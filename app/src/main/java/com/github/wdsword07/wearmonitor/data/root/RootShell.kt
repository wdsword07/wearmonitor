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
import java.util.concurrent.ConcurrentHashMap

object RootShell {
    private const val TAG = "WearMonitor_Root"
    private val mutex = Mutex()

    @Volatile
    private var rootAvailable: Boolean? = null

    // 仅针对非关键的可选 sysfs 节点记录不存在的黑名单，核心 proc 节点绝不加入长期黑名单
    private val unreadableSysfsPaths = ConcurrentHashMap<String, Long>()

    private var persistentSuProcess: Process? = null
    private var suWriter: OutputStreamWriter? = null
    private var suReader: BufferedReader? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        rootAvailable?.let { return@withContext it }
        val available = testRoot()
        rootAvailable = available
        available
    }

    private fun testRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
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
     * 在持久化 su 会话中执行指定 Shell 命令并返回结果
     */
    suspend fun execCommand(command: String): String? = mutex.withLock {
        if (!testRootCached()) return null
        withContext(Dispatchers.IO) {
            try {
                ensureSuSession()
                val writer = suWriter ?: return@withContext null
                val reader = suReader ?: return@withContext null

                val token = "__END_${System.nanoTime()}__"
                writer.write("$command 2>/dev/null; echo $token\n")
                writer.flush()

                val sb = java.lang.StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == token) break
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(line)
                }
                val text = sb.toString().trim()
                if (text.isNotEmpty()) text else null
            } catch (e: Exception) {
                Log.w(TAG, "Persistent su exec failed for $command: ${e.message}")
                closeSuSession()
                null
            }
        }
    }

    private fun testRootCached(): Boolean {
        return rootAvailable ?: testRoot().also { rootAvailable = it }
    }

    private fun ensureSuSession() {
        if (persistentSuProcess == null) {
            val process = Runtime.getRuntime().exec("su")
            persistentSuProcess = process
            suWriter = OutputStreamWriter(process.outputStream)
            suReader = BufferedReader(InputStreamReader(process.inputStream))
        }
    }

    private fun closeSuSession() {
        try {
            suWriter?.write("exit\n")
            suWriter?.flush()
        } catch (_: Exception) {}
        try {
            persistentSuProcess?.destroy()
        } catch (_: Exception) {}
        persistentSuProcess = null
        suWriter = null
        suReader = null
    }
}
