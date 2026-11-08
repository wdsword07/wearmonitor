package com.github.wdsword07.wearmonitor.data.monitor

import android.os.Environment
import android.os.StatFs
import com.github.wdsword07.wearmonitor.data.model.MemoryInfo
import com.github.wdsword07.wearmonitor.data.model.StorageInfo
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryStorageMonitor {

    suspend fun sampleMemoryInfo(): MemoryInfo = withContext(Dispatchers.IO) {
        val memInfoContent = RootShell.readFile("/proc/meminfo") ?: return@withContext MemoryInfo(0, 0, 0, 0f)
        var totalKb = 0L
        var availKb = 0L
        var freeKb = 0L
        var swapTotalKb = 0L
        var swapFreeKb = 0L

        memInfoContent.lineSequence().forEach { line ->
            val parts = line.split(":")
            if (parts.size >= 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L
                when (key) {
                    "MemTotal" -> totalKb = value
                    "MemAvailable" -> availKb = value
                    "MemFree" -> freeKb = value
                    "SwapTotal" -> swapTotalKb = value
                    "SwapFree" -> swapFreeKb = value
                }
            }
        }

        val effectiveAvailKb = if (availKb > 0) availKb else freeKb
        val usedKb = (totalKb - effectiveAvailKb).coerceAtLeast(0L)
        val usedPercent = if (totalKb > 0) {
            (usedKb.toFloat() / totalKb.toFloat()) * 100f
        } else {
            0f
        }

        MemoryInfo(
            totalMb = totalKb / 1024,
            availMb = effectiveAvailKb / 1024,
            usedMb = usedKb / 1024,
            usedPercent = usedPercent.coerceIn(0f, 100f),
            swapTotalMb = swapTotalKb / 1024,
            swapFreeMb = swapFreeKb / 1024
        )
    }

    suspend fun sampleStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
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

            StorageInfo(
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                usedBytes = usedBytes,
                usedPercent = usedPercent.coerceIn(0f, 100f)
            )
        } catch (_: Exception) {
            StorageInfo(0, 0, 0, 0f)
        }
    }
}
