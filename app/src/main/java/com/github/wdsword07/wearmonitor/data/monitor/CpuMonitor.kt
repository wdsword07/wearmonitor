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

    // 静态频率范围（min/max），每核只读一次
    private val freqRangeCache = HashMap<Int, Pair<Int, Int>>()

    /**
     * 上一次采样的 time_in_state（频率 -> 累计 tick）。
     * 差分后按驻留时间加权，得到该采样窗口内真实运行频率。
     */
    private var lastTimeInState: Map<Int, Map<Int, Long>> = emptyMap()

    suspend fun sampleCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val usage = calculateOverallUsage()
        // 先取频率驻留统计，再换算成每核有效频率
        val effectiveFreqs = computeEffectiveFreqMhz(readTimeInStateStats())
        val cores = readCoresInfo(effectiveFreqs)
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

    /**
     * 读取全部核心的 cpufreq 驻留统计。
     *
     * 注意：`scaling_cur_freq` 在部分平台只反映 governor 的“请求值”，
     * 可能长期固定在 policy 上限（表现为恒为 1708MHz），不能代表实际运行频率。
     * 因此优先使用 `stats/time_in_state` 的区间驻留加权值。
     */
    private suspend fun readTimeInStateStats(): Map<Int, Map<Int, Long>> {
        val indices = detectedCoreIndices ?: probeCoreIndices()
        val result = HashMap<Int, Map<Int, Long>>()

        // 1. 普通 IO 优先（部分内核将该节点设为 0444）
        for (index in indices) {
            val text = runCatching {
                val file = timeInStateCandidates(index).firstOrNull { File(it).canRead() }
                if (file != null) File(file).readText() else null
            }.getOrNull()
            if (text != null) {
                parseTimeInState(text)?.let { result[index] = it }
            }
        }
        if (result.size == indices.size) return result

        // 2. 多数内核仅 root 可读：合并成一条命令批量读取，避免逐核 fork
        if (RootShell.isRootAvailable()) {
            val command = buildString {
                for (index in indices) {
                    append("echo @").append(index).append('\n')
                    for (path in timeInStateCandidates(index)) {
                        append("cat ").append(path).append(" 2>/dev/null\n")
                    }
                }
            }
            val output = RootShell.execCommand(command, timeoutMs = 2_000L)
            if (output != null) {
                var currentIndex = -1
                val buffer = StringBuilder()
                fun flush() {
                    if (currentIndex >= 0) {
                        parseTimeInState(buffer.toString())?.let { result[currentIndex] = it }
                    }
                    buffer.setLength(0)
                }
                for (line in output.lineSequence()) {
                    if (line.startsWith("@")) {
                        flush()
                        currentIndex = line.removePrefix("@").trim().toIntOrNull() ?: -1
                    } else if (currentIndex >= 0) {
                        buffer.append(line).append('\n')
                    }
                }
                flush()
            }
        }
        return result
    }

    /**
     * 兼容不同内核的驻留统计节点位置：
     * per-cpu / per-policy / 单 policy（所有核心共享）。
     */
    private fun timeInStateCandidates(index: Int): List<String> = listOf(
        "/sys/devices/system/cpu/cpu$index/cpufreq/stats/time_in_state",
        "/sys/devices/system/cpu/cpufreq/policy$index/stats/time_in_state",
        "/sys/devices/system/cpu/cpufreq/policy0/stats/time_in_state"
    )

    /** 解析 `time_in_state`：每行 `频率(kHz) 累计tick` */
    private fun parseTimeInState(text: String): Map<Int, Long>? {
        val states = HashMap<Int, Long>()
        for (line in text.lineSequence()) {
            val parts = line.trim().split(WHITESPACE_REGEX)
            if (parts.size < 2) continue
            val freqKhz = parts[0].toIntOrNull() ?: continue
            val ticks = parts[1].toLongOrNull() ?: continue
            if (freqKhz > 0) states[freqKhz] = ticks
        }
        return states.ifEmpty { null }
    }

    /**
     * 用两次采样的 tick 增量做驻留加权，得到真实平均频率（MHz）。
     * 首次采样没有基准，返回空表由瞬时值兜底。
     */
    private fun computeEffectiveFreqMhz(stats: Map<Int, Map<Int, Long>>): Map<Int, Int> {
        if (stats.isEmpty()) return emptyMap()
        val result = HashMap<Int, Int>(stats.size)
        for ((index, states) in stats) {
            val previous = lastTimeInState[index]
            if (previous != null) {
                var totalTicks = 0L
                var weightedKhz = 0.0
                for ((freqKhz, ticks) in states) {
                    val delta = ticks - (previous[freqKhz] ?: 0L)
                    if (delta > 0) {
                        totalTicks += delta
                        weightedKhz += delta.toDouble() * freqKhz
                    }
                }
                if (totalTicks > 0) {
                    result[index] = (weightedKhz / totalTicks / 1000.0).toInt()
                }
            }
        }
        lastTimeInState = stats
        return result
    }

    private suspend fun readCoresInfo(effectiveFreqs: Map<Int, Int>): List<CpuCoreInfo> {
        var indices = detectedCoreIndices
        if (indices == null) {
            indices = probeCoreIndices()
            detectedCoreIndices = indices
        }

        val cores = ArrayList<CpuCoreInfo>(indices.size)
        for (i in indices) {
            cores.add(readSingleCore(i, effectiveFreqs[i]))
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

    private suspend fun readSingleCore(index: Int, effectiveFreqMhz: Int?): CpuCoreInfo {
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

        val (minFreq, maxFreq) = readFreqRange(index)
        // 优先：区间驻留加权（实际运行）→ 硬件瞬时 → governor 请求值
        val curFreqMhz = effectiveFreqMhz?.takeIf { it > 0 }
            ?: readFreqMhz("$basePath/cpufreq/cpuinfo_cur_freq")
            ?: readFreqMhz("$basePath/cpufreq/scaling_cur_freq")
            ?: 0

        return CpuCoreInfo(
            index = index,
            curFreqMhz = curFreqMhz,
            minFreqMhz = minFreq,
            maxFreqMhz = maxFreq,
            isOnline = true
        )
    }

    private suspend fun readFreqRange(index: Int): Pair<Int, Int> {
        freqRangeCache[index]?.let { return it }
        val basePath = "/sys/devices/system/cpu/cpu$index/cpufreq"
        val minMhz = RootShell.readFile("$basePath/cpuinfo_min_freq", singleLineOnly = true)
            ?.toIntOrNull()?.div(1000) ?: 0
        val maxMhz = RootShell.readFile("$basePath/cpuinfo_max_freq", singleLineOnly = true)
            ?.toIntOrNull()?.div(1000) ?: 0
        val range = minMhz to maxMhz
        freqRangeCache[index] = range
        return range
    }

    private suspend fun readFreqMhz(path: String): Int? {
        val raw = RootShell.readFile(path, singleLineOnly = true)?.toIntOrNull() ?: return null
        return (raw / 1000).takeIf { it > 0 }
    }

    private suspend fun readGovernor(): String {
        return RootShell.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", singleLineOnly = true) ?: ""
    }

    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
