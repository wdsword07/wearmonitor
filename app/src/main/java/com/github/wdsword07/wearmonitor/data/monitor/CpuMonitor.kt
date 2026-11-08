package com.github.wdsword07.wearmonitor.data.monitor

import android.os.Build
import com.github.wdsword07.wearmonitor.data.model.CpuCoreInfo
import com.github.wdsword07.wearmonitor.data.model.CpuInfo
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CpuMonitor {
    private var lastTotalTime = 0L
    private var lastIdleTime = 0L
    private var lastValidUsage = 15f // 初始默认预估基准

    @Volatile
    private var detectedCoreIndices: List<Int>? = null
    @Volatile
    private var cachedGovernor: String = ""
    @Volatile
    private var cachedSocPlatform: String = ""
    @Volatile
    private var cachedSocHardware: String = ""
    @Volatile
    private var cachedCpuArch: String = ""
    @Volatile
    private var cachedSupportedAbis: String = ""

    suspend fun sampleCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val usage = calculateOverallUsage()
        val cores = readCoresInfo()
        if (cachedGovernor.isEmpty()) {
            cachedGovernor = readGovernor()
        }
        if (cachedSocPlatform.isEmpty()) {
            val board = Build.BOARD ?: ""
            val hardware = Build.HARDWARE ?: ""
            cachedSocHardware = hardware
            cachedSocPlatform = if (board.isNotBlank()) board else hardware
            cachedCpuArch = System.getProperty("os.arch") ?: "arm"
            cachedSupportedAbis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: ""
        }
        CpuInfo(
            overallUsagePercent = usage,
            cores = cores,
            governor = cachedGovernor,
            socPlatform = cachedSocPlatform,
            socHardware = cachedSocHardware,
            cpuArch = cachedCpuArch,
            supportedAbis = cachedSupportedAbis
        )
    }

    private suspend fun calculateOverallUsage(): Float {
        val statContent = RootShell.readFile("/proc/stat")
        if (statContent != null) {
            val firstLine = statContent.lineSequence().firstOrNull { it.trim().startsWith("cpu") }
            if (firstLine != null) {
                val parts = firstLine.trim().split("\\s+".toRegex())
                if (parts.size >= 5) {
                    try {
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
                            return lastValidUsage
                        }

                        val deltaTotal = currentTotalTime - lastTotalTime
                        val deltaIdle = currentIdleTime - lastIdleTime

                        lastTotalTime = currentTotalTime
                        lastIdleTime = currentIdleTime

                        if (deltaTotal > 0) {
                            val usage = ((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat()) * 100f
                            val clamped = usage.coerceIn(0f, 100f)
                            lastValidUsage = clamped
                            return clamped
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 兜底方案：读取 /proc/loadavg 获取 1 分钟系统负载
        val loadavg = RootShell.readFile("/proc/loadavg", singleLineOnly = true)
        if (loadavg != null) {
            val oneMinLoad = loadavg.split("\\s+".toRegex()).firstOrNull()?.toFloatOrNull()
            if (oneMinLoad != null) {
                val coreCount = (detectedCoreIndices?.size ?: Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
                val loadPercent = (oneMinLoad / coreCount.toFloat() * 100f).coerceIn(0f, 100f)
                lastValidUsage = loadPercent
                return loadPercent
            }
        }

        return lastValidUsage
    }

    private suspend fun readCoresInfo(): List<CpuCoreInfo> {
        var indices = detectedCoreIndices
        if (indices == null) {
            indices = probeCoreIndices()
            detectedCoreIndices = indices
        }

        val cores = ArrayList<CpuCoreInfo>(indices.size)
        for (i in indices) {
            cores.add(readSingleCore(i))
        }
        return cores
    }

    private fun probeCoreIndices(): List<Int> {
        val cpuDir = File("/sys/devices/system/cpu")
        val cpuFiles = cpuDir.listFiles { _, name -> name.matches(Regex("cpu[0-9]+")) }
            ?.mapNotNull { it.name.substring(3).toIntOrNull() }
            ?.sorted()

        return if (!cpuFiles.isNullOrEmpty()) {
            cpuFiles
        } else {
            val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            (0 until availableProcessors).toList()
        }
    }

    private suspend fun readSingleCore(index: Int): CpuCoreInfo {
        val basePath = "/sys/devices/system/cpu/cpu$index"
        val isOnline = if (index == 0) {
            true
        } else {
            val onlineStr = RootShell.readFile("$basePath/online", singleLineOnly = true)
            onlineStr == null || onlineStr == "1"
        }

        if (!isOnline) {
            return CpuCoreInfo(
                index = index,
                curFreqMhz = 0,
                isOnline = false
            )
        }

        val curFreqStr = RootShell.readFile("$basePath/cpufreq/scaling_cur_freq", singleLineOnly = true)
            ?: RootShell.readFile("$basePath/cpufreq/cpuinfo_cur_freq", singleLineOnly = true)

        val curFreqKhz = curFreqStr?.toIntOrNull() ?: 0

        return CpuCoreInfo(
            index = index,
            curFreqMhz = curFreqKhz / 1000,
            isOnline = true
        )
    }

    private suspend fun readGovernor(): String {
        return RootShell.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", singleLineOnly = true) ?: ""
    }
}
