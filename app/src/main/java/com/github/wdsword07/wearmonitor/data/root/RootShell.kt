package com.github.wdsword07.wearmonitor.data.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object RootShell {
    private const val TAG = "WearMonitor_Root"
    private val mutex = Mutex()

    @Volatile
    private var rootAvailable: Boolean? = null

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
            val isRoot = exitCode == 0 && (line != null && line.contains("uid=0"))
            Log.d(TAG, "Root check result: $isRoot (out=$line)")
            isRoot
        } catch (e: Exception) {
            Log.w(TAG, "su command not available: ${e.message}")
            false
        }
    }

    /**
     * 读取文本文件内容：
     * 1. 优先普通文件 IO 直接读取（速度最高、无进程额外开销）
     * 2. 若普通权限无法读取且设备有 Root 权限，则通过 su 执行读取
     */
    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists() && file.canRead()) {
            try {
                return@withContext file.readText().trim()
            } catch (_: Exception) {
                // fall through to root fallback
            }
        }

        if (isRootAvailable()) {
            return@withContext execSu("cat $path")?.trim()
        }
        null
    }

    /**
     * 执行简单的只读 Shell 命令并返回单行或输出内容
     */
    suspend fun execSu(command: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = OutputStreamWriter(process.outputStream)
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                os.write("$command\nexit\n")
                os.flush()

                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                process.waitFor()
                output.toString().trim()
            } catch (e: Exception) {
                Log.w(TAG, "execSu failed for '$command': ${e.message}")
                null
            }
        }
    }
}
