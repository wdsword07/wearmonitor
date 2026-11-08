package com.github.wdsword07.wearmonitor.data.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object RootShell {
    private const val TAG = "WearMonitor_Root"
    private val mutex = Mutex()

    // 单条 root 命令的硬超时。手表上 su 偶发握手/策略回调会阻塞，
    // 一旦阻塞必须快速失败并销毁会话，绝不能把整条数据流卡死。
    private const val DEFAULT_TIMEOUT_MS = 1500L
    // 新建 su 会话的首条命令：Magisk 首次策略通知会 fork 完整 JVM，必须给足时间
    private const val COLD_START_TIMEOUT_MS = 6000L

    @Volatile
    private var rootAvailable: Boolean? = null

    // 仅针对非关键的可选 sysfs 节点记录不存在的黑名单，核心 proc 节点绝不加入长期黑名单
    private val unreadableSysfsPaths = ConcurrentHashMap<String, Long>()

    private var persistentSuProcess: Process? = null
    private var suWriter: OutputStreamWriter? = null
    private var suReader: BufferedReader? = null

    private val rootCheckLock = Any()

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        rootAvailable?.let { return@withContext it }
        synchronized(rootCheckLock) {
            // 双重检查，避免启动时多个并发协程各自 fork 一个 su
            rootAvailable?.let { return@withContext it }
            val available = testRoot()
            rootAvailable = available
            available
        }
    }

    private fun testRoot(): Boolean {
        return try {
            // 一次性进程（独立管道），不会影响持久化会话的 stdin
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).apply {
                outputStream.close()
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val exitCode = process.waitFor()
            val isRoot = (exitCode == 0) && (line != null && line.contains("uid=0"))
            Log.d(TAG, "Root check result: $isRoot (out=$line)")
            isRoot
        } catch (e: Exception) {
            Log.w(TAG, "su command not available: ${e.message}")
            false
        }
    }

    /**
     * 高性能读取文本文件内容：
     * 1. 优先普通文件 IO 直接读取（微秒级）
     * 2. 若普通权限失败且设备有 Root，使用持久化 su 会话
     */
    suspend fun readFile(path: String, singleLineOnly: Boolean = false): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (path.startsWith("/sys/")) {
            val lastFailed = unreadableSysfsPaths[path]
            if (lastFailed != null && (now - lastFailed < 45_000L)) {
                return@withContext null
            }
        }

        // 尝试普通 Java IO
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                FileInputStream(file).bufferedReader().use { reader ->
                    return@withContext if (singleLineOnly) {
                        reader.readLine()?.trim()
                    } else {
                        reader.readText().trim()
                    }
                }
            }
        } catch (_: Exception) {
            // 普通权限被拒绝，继续尝试 root
        }

        // 尝试 Root 读取
        if (isRootAvailable()) {
            val result = execCommand("cat $path")
            if (result != null) {
                return@withContext if (singleLineOnly) result.lineSequence().firstOrNull()?.trim() else result
            }
        }

        if (path.startsWith("/sys/")) {
            unreadableSysfsPaths[path] = now
        }
        null
    }

    /**
     * 在持久化 su 会话中执行指定 Shell 命令并返回结果。
     *
     * 安全约束：
     * - 命令强制 `</dev/null`，避免命令（尤其是嵌套 `su`）抢占会话 stdin 造成 token 丢失；
     * - 读取带硬超时，超时立即销毁会话解除阻塞，返回 null 由调用方降级。
     */
    suspend fun execCommand(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? = withContext(Dispatchers.IO) {
        // 缓存判断放在锁外，避免握着锁再去 fork su
        if (!isRootAvailable()) return@withContext null

        mutex.withLock {
            try {
                val isNewSession = ensureSuSession()
                val writer = suWriter ?: return@withLock null
                val reader = suReader ?: return@withLock null

                val token = "__END_${System.nanoTime()}__"
                // 关键：sysfs/proc 很多文件末尾没有换行符（如 selinux/enforce、zram0/mm_stat），
                // 直接 echo token 会与文件内容粘成同一行（例如 "1__END__..."），
                // 导致读取端永远匹配不到 token 而无限阻塞。故先输出一个换行。
                writer.write("$command </dev/null 2>/dev/null; echo; echo $token\n")
                writer.flush()

                val effectiveTimeout = if (isNewSession) maxOf(timeoutMs, COLD_START_TIMEOUT_MS) else timeoutMs
                val text = readUntilToken(reader, token, effectiveTimeout)
                if (text.isNullOrEmpty()) null else text
            } catch (e: TimeoutException) {
                Log.w(TAG, "su exec timeout, dropping session: $command")
                closeSuSession()
                null
            } catch (e: Exception) {
                Log.w(TAG, "Persistent su exec failed for $command: ${e.message}")
                closeSuSession()
                null
            }
        }
    }

    /**
     * 阻塞式逐行读取无法被协程取消，因此放到独立线程并在 future 上做硬超时。
     * 超时后由调用方 [closeSuSession] 销毁进程，使底层 fd 关闭从而解除阻塞。
     */
    private fun readUntilToken(reader: BufferedReader, token: String, timeoutMs: Long): String? {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wm-su-reader").apply { isDaemon = true }
        }
        try {
            val future = executor.submit(Callable {
                val sb = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.trim() == token) break
                    if (line.endsWith(token)) {
                        // 双保险：万一 token 仍与内容粘连，剥离后收尾
                        val head = line.removeSuffix(token)
                        if (head.isNotEmpty()) {
                            if (sb.isNotEmpty()) sb.append('\n')
                            sb.append(head)
                        }
                        break
                    }
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line)
                }
                sb.toString().trim()
            })
            return future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw e
        } finally {
            executor.shutdownNow()
        }
    }

    /** @return true 表示本次新建了 su 会话（冷启动，首条命令需要更长超时） */
    private fun ensureSuSession(): Boolean {
        if (persistentSuProcess == null) {
            val process = Runtime.getRuntime().exec("su")
            persistentSuProcess = process
            suWriter = OutputStreamWriter(process.outputStream)
            suReader = BufferedReader(InputStreamReader(process.inputStream))
            return true
        }
        return false
    }

    private fun closeSuSession() {
        try {
            suWriter?.write("exit\n")
            suWriter?.flush()
        } catch (_: Exception) {}
        try {
            persistentSuProcess?.destroy()
        } catch (_: Exception) {}
        try {
            suReader?.close()
        } catch (_: Exception) {}
        try {
            suWriter?.close()
        } catch (_: Exception) {}
        persistentSuProcess = null
        suWriter = null
        suReader = null
    }
}