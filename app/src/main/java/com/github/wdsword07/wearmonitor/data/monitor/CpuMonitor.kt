package com.github.wdsword07.wearmonitor.data.monitor

import com.github.wdsword07.wearmonitor.data.model.CpuCoreInfo
import com.github.wdsword07.wearmonitor.data.model.CpuInfo
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CpuMonitor {
    private var lastTotalTime = 0L
    private var lastIdleTime = 0L

    suspend fun sampleCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val usage = calculateOverallUsage()
        val cores = readCoresInfo()
        val governor = readGovernor()
        CpuInfo(
            overallUsagePercent = usage,
            cores = cores,
            governor = governor
        )
    }

    private suspend fun calculateOverallUsage(): Float {
        val statContent = RootShell.readFile("/proc/stat") ?: return 0f
        val firstLine = statContent.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return 0f
        val parts = firstLine.trim().split("\\s+".toRegex())
        if (parts.size < 5) return 0f

        try {
            // cpu user nice system idle iowait irq softirq steal
            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L

            val currentIdleTime = idle + iowait
            val currentTotalTime = user + nice + system + currentIdleTime + irq + softirq + steal

            if (lastTotalTime == 0L) {
                lastTotalTime = currentTotalTime
                lastIdleTime = currentIdleTime
                return 0f
            }

            val deltaTotal = currentTotalTime - lastTotalTime
            val deltaIdle = currentIdleTime - lastIdleTime

            lastTotalTime = currentTotalTime
            lastIdleTime = currentIdleTime

            if (deltaTotal <= 0) return 0f
            val usage = ((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat()) * 100f
            return usage.coerceIn(0f, 100f)
        } catch (_: Exception) {
            return 0f
        }
    }

    private suspend fun readCoresInfo(): List<CpuCoreInfo> {
        val cores = mutableListOf<CpuCoreInfo>()
        val cpuDir = File("/sys/devices/system/cpu")
        val cpuFiles = cpuDir.listFiles { _, name -> name.matches(Regex("cpu[0-9]+")) }
            ?.sortedBy { it.name.substring(3).toIntOrNull() ?: 0 }

        if (cpuFiles.isNullOrEmpty()) {
            // 兜底尝试 cpu0..cpu7
            for (i in 0..7) {
                val core = readSingleCore(i) ?: break
                cores.add(core)
            }
        } else {
            for (file in cpuFiles) {
                val index = file.name.substring(3).toIntOrNull() ?: continue
                val core = readSingleCore(index)
                if (core != null) {
                    cores.add(core)
                }
            }
        }
        return cores
    }

    private suspend fun readSingleCore(index: Int): CpuCoreInfo? {
        val basePath = "/sys/devices/system/cpu/cpu$index"
        val onlineFile = "$basePath/online"
        val isOnline = if (index == 0) {
            true
        } else {
            val onlineStr = RootShell.readFile(onlineFile)
            onlineStr == null || onlineStr == "1"
        }

        if (!isOnline) {
            return CpuCoreInfo(
                index = index,
                curFreqMhz = 0,
                isOnline = false
            )
        }

        val curFreqStr = RootShell.readFile("$basePath/cpufreq/scaling_cur_freq")
            ?: RootShell.readFile("$basePath/cpufreq/cpuinfo_cur_freq")
        val minFreqStr = RootShell.readFile("$basePath/cpufreq/scaling_min_freq")
        val maxFreqStr = RootShell.readFile("$basePath/cpufreq/scaling_max_freq")

        val curFreqKhz = curFreqStr?.toIntOrNull() ?: 0
        val minFreqKhz = minFreqStr?.toIntOrNull() ?: 0
        val maxFreqKhz = maxFreqStr?.toIntOrNull() ?: 0

        return CpuCoreInfo(
            index = index,
            curFreqMhz = curFreqKhz / 1000,
            minFreqMhz = minFreqKhz / 1000,
            maxFreqMhz = maxFreqKhz / 1000,
            isOnline = true
        )
    }

    private suspend fun readGovernor(): String {
        return RootShell.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: ""
    }
}
