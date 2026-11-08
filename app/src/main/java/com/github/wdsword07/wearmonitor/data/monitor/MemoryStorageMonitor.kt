package com.github.wdsword07.wearmonitor.data.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.github.wdsword07.wearmonitor.data.model.MemoryInfo
import com.github.wdsword07.wearmonitor.data.model.StorageInfo
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryStorageMonitor(private val context: Context) {
    @Volatile
    private var cachedStorage: StorageInfo = StorageInfo(0, 0, 0, 0f)
    @Volatile
    private var lastStorageCheckTime: Long = 0

    // 缓存 Swap 总量与压缩算法，因为手表 ZRAM 启动后几乎不变
    @Volatile
    private var cachedSwapTotalMb: Long = 0
    @Volatile
    private var cachedZramAlgorithm: String = ""

    suspend fun sampleMemoryInfo(): MemoryInfo = withContext(Dispatchers.IO) {
        // 1. 使用 Android 原生 ActivityManager（微秒级 JNI，准确扣除系统 PageCache，反映真实可用 RAM）
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val amMemInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(amMemInfo)

        val totalMb = amMemInfo.totalMem / (1024 * 1024)
        val availMb = amMemInfo.availMem / (1024 * 1024)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)
        val usedPercent = if (totalMb > 0) {
            ((usedMb.toFloat() / totalMb.toFloat()) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }

        // 2. 动态读取 /proc/meminfo（微秒级内核内存映射，提取 Swap/ZRAM 实时剩余量）
        var swapFreeMb = 0L
        val memInfoContent = RootShell.readFile("/proc/meminfo")
        if (memInfoContent != null) {
            memInfoContent.lineSequence().forEach { line ->
                if (line.startsWith("SwapTotal:")) {
                    if (cachedSwapTotalMb == 0L) {
                        cachedSwapTotalMb = extractKb(line) / 1024
                    }
                } else if (line.startsWith("SwapFree:")) {
                    swapFreeMb = extractKb(line) / 1024
                }
            }
        }

        val swapTotal = cachedSwapTotalMb
        val swapUsed = (swapTotal - swapFreeMb).coerceAtLeast(0L)
        val swapUsedPercent = if (swapTotal > 0) {
            ((swapUsed.toFloat() / swapTotal.toFloat()) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }

        // 3. 读取 ZRAM 压缩比与详细参数 (/sys/block/zram0)
        var zramOrigMb = 0f
        var zramComprMb = 0f
        var zramRatio = 0f
        if (swapTotal > 0) {
            val origBytes = RootShell.readFile("/sys/block/zram0/orig_data_size", singleLineOnly = true)?.toLongOrNull() ?: 0L
            val comprBytes = RootShell.readFile("/sys/block/zram0/compr_data_size", singleLineOnly = true)?.toLongOrNull() ?: 0L
            if (origBytes > 0L) {
                zramOrigMb = origBytes.toFloat() / (1024f * 1024f)
            }
            if (comprBytes > 0L) {
                zramComprMb = comprBytes.toFloat() / (1024f * 1024f)
            }
            if (comprBytes > 0L && origBytes > 0L) {
                zramRatio = origBytes.toFloat() / comprBytes.toFloat()
            }

            if (cachedZramAlgorithm.isEmpty()) {
                val rawAlgo = RootShell.readFile("/sys/block/zram0/comp_algorithm", singleLineOnly = true)
                if (!rawAlgo.isNullOrBlank()) {
                    cachedZramAlgorithm = if (rawAlgo.contains("[")) {
                        rawAlgo.substringAfter("[").substringBefore("]").trim()
                    } else {
                        rawAlgo.trim()
                    }
                }
            }
        }

        MemoryInfo(
            totalMb = totalMb,
            availMb = availMb,
            usedMb = usedMb,
            usedPercent = usedPercent,
            swapTotalMb = swapTotal,
            swapFreeMb = swapFreeMb,
            swapUsedMb = swapUsed,
            swapUsedPercent = swapUsedPercent,
            zramOrigMb = zramOrigMb,
            zramComprMb = zramComprMb,
            zramRatio = zramRatio,
            zramAlgorithm = cachedZramAlgorithm
        )
    }

    private fun extractKb(line: String): Long {
        return line.substringAfter(":").trim().substringBefore(" ").toLongOrNull() ?: 0L
    }

    suspend fun sampleStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastStorageCheckTime < 20_000L && cachedStorage.totalBytes > 0) {
            return@withContext cachedStorage
        }

        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            val usedPercent = if (totalBytes > 0) {
                (usedBytes.toFloat() / totalBytes.toFloat()) * 100f
            } else {
                0f
            }

            val storage = StorageInfo(
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                usedBytes = usedBytes,
                usedPercent = usedPercent.coerceIn(0f, 100f)
            )
            cachedStorage = storage
            lastStorageCheckTime = now
            storage
        } catch (_: Exception) {
            cachedStorage
        }
    }
}
