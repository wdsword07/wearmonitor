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

    // 记录不可读/不存在的路径，避免在低功耗手表上高频轮询重试造成 CPU 负载
    private val unreadablePaths = ConcurrentHashMap<String, Long>()

    // 持久化 su 进程与管道
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
     * 高性能读取小文本文件：
     * 1. 优先普通文件 IO 直接读取（微秒级、零进程开销）
     * 2. 若普通权限失败且设备有 Root，使用持久化 su 会话，避免反复 fork 进程引起严重掉帧
     */
    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastFailed = unreadablePaths[path]
        if (lastFailed != null && (now - lastFailed < 60_000L)) {
            // 60秒内曾失败过的路径直接跳过，保护 CPU 性能
            return@withContext null
        }

        // 尝试普通 Java IO
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                FileInputStream(file).bufferedReader().use {
                    val line = it.readLine()?.trim()
                    if (line != null) return@withContext line
                }
            }
        } catch (_: Exception) {
            // 普通权限无法读取
        }

        // 仅在确认有 Root 权限时才通过 root 会话读取
        if (isRootAvailable()) {
            val result = readViaRootSession(path)
            if (result != null) {
                return@withContext result
            }
        }

        unreadablePaths[path] = now
        null
    }

    private suspend fun readViaRootSession(path: String): String? = mutex.withLock {
        try {
            ensureSuSession()
            val writer = suWriter ?: return null
            val reader = suReader ?: return null

            val token = "__END_${System.nanoTime()}__"
            writer.write("cat $path 2>/dev/null; echo $token\n")
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
            Log.w(TAG, "Persistent su read failed for $path: ${e.message}")
            closeSuSession()
            null
        }
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
