package com.github.wdsword07.wearmonitor.data.monitor

import android.content.Context
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.github.wdsword07.wearmonitor.data.model.ProcessEntry
import com.github.wdsword07.wearmonitor.data.model.ProcessSummary
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 进程枚举与采样。
 *
 * 设计要点：
 * 1. 跨应用读取 /proc 在 Android 9+ 被 hidepid 限制，必须走 root 会话；
 * 2. 整张进程表只用 **一条** su 命令采集，避免逐 pid fork（手表上 fork 成本极高）：
 *    - `ps -A -o pid,uid,rss,args` 一次拿到 pid / uid / 常驻内存 / 进程名；
 *    - 再用 mksh 内建 `read`（零 fork）批量读取 `/proc/<pid>/stat` 取 utime+stime，
 *      与上一次采样做差得到真实 CPU 占用；
 * 3. 结果带短时缓存，主页入口卡片 10s 刷新、详情页 2~3s 刷新共用同一份数据，
 *    保证同一时刻 CPU 差值的采样窗口一致。
 */
class ProcessMonitor(private val context: Context) {

    private val mutex = Mutex()

    // 包名/版本查询结果缓存（进程存活期间不会变化）
    private val packageCache = HashMap<String, PackageMeta>()

    private var cachedProcesses: List<ProcessEntry> = emptyList()
    private var cachedAtMs: Long = 0L

    // 上一次采样的累计 CPU tick，用于计算区间占用率
    private var lastCpuTicks: Map<Int, Long> = emptyMap()
    private var lastCpuSampleMs: Long = 0L

    private val clockTicks: Float by lazy {
        runCatching { Os.sysconf(OsConstants._SC_CLK_TCK).toFloat() }
            .getOrDefault(100f)
            .coerceAtLeast(1f)
    }

    /** 详情页使用：默认 2.5s 缓存，基本每次轮询都会真正刷新。 */
    suspend fun getProcesses(forceRefresh: Boolean = false, maxAgeMs: Long = 2_500L): List<ProcessEntry> =
        withContext(Dispatchers.IO) {
            mutex.withLock { snapshotLocked(forceRefresh, maxAgeMs) }
        }

    /** 主页入口卡片使用：默认 10s 缓存，避免高频枚举拖慢主面板。 */
    suspend fun getSummary(forceRefresh: Boolean = false, maxAgeMs: Long = 10_000L): ProcessSummary =
        withContext(Dispatchers.IO) {
            val list = mutex.withLock { snapshotLocked(forceRefresh, maxAgeMs) }
            ProcessSummary(
                totalCount = list.size,
                appCount = list.count { !it.isSystem },
                systemCount = list.count { it.isSystem },
                totalMemoryMb = list.sumOf { it.memoryMb.toDouble() }.toFloat()
            )
        }

    /**
     * 结束指定应用进程。
     *
     * 使用 `am force-stop`（系统级停用语义）而非 `kill -9`：
     * 后者会绕过 AMS，可能损坏应用数据甚至拖垮共享进程。
     */
    suspend fun killProcess(entry: ProcessEntry): Boolean = withContext(Dispatchers.IO) {
        if (!entry.canKill) return@withContext false
        val pkg = entry.packageName ?: return@withContext false
        // 包名会拼进 root 命令，必须严格白名单校验，杜绝命令注入
        if (!PACKAGE_NAME_REGEX.matches(pkg)) return@withContext false
        // 无输出的命令在 RootShell 中会返回 null，故追加标记用于判定成功；
        // am 显式重定向 stdin，遵守持久化 su 会话“禁止任何进程抢占 stdin”的约束
        val result = RootShell.execCommand(
            "am force-stop $pkg </dev/null 2>/dev/null; echo __WM_KILLED__",
            timeoutMs = 4_000L
        )
        val success = result?.contains("__WM_KILLED__") == true
        mutex.withLock { cachedAtMs = 0L }
        success
    }

    private suspend fun snapshotLocked(forceRefresh: Boolean, maxAgeMs: Long): List<ProcessEntry> {
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh && cachedProcesses.isNotEmpty() && now - cachedAtMs <= maxAgeMs) {
            return cachedProcesses
        }
        val parsed = enumerateLocked()
        if (parsed.isEmpty()) return cachedProcesses
        cachedProcesses = parsed
        cachedAtMs = now
        return parsed
    }

    private suspend fun enumerateLocked(): List<ProcessEntry> {
        val output = RootShell.execCommand(COLLECT_COMMAND, timeoutMs = 4_000L) ?: return emptyList()

        val rawProcesses = ArrayList<RawProcess>(256)
        val statTicks = HashMap<Int, Long>(256)
        var section = 0

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            when {
                line == PS_MARKER -> section = 1
                line == STAT_MARKER -> section = 2
                section == 1 -> parsePsLine(line)?.let(rawProcesses::add)
                section == 2 -> parseStatLine(line, statTicks)
            }
        }

        if (rawProcesses.isEmpty()) {
            Log.w(TAG, "ps output not parsable, keep previous process cache")
            return emptyList()
        }

        val now = SystemClock.elapsedRealtime()
        val deltaSec = if (lastCpuSampleMs > 0L) (now - lastCpuSampleMs) / 1000f else 0f
        val ticksPerSecond = clockTicks
        // 采样窗口过短（例如摘要与详情页同时刷新）时沿用上次占用率，避免整屏闪 0
        val previousByPid = if (deltaSec <= 0.2f) cachedProcesses.associateBy { it.pid } else emptyMap()

        val entries = ArrayList<ProcessEntry>(rawProcesses.size)
        for (raw in rawProcesses) {
            val prevTicks = lastCpuTicks[raw.pid]
            val currentTicks = statTicks[raw.pid]
            val cpuPercent = if (prevTicks != null && currentTicks != null &&
                currentTicks >= prevTicks && deltaSec > 0.2f
            ) {
                (((currentTicks - prevTicks) / ticksPerSecond) / deltaSec * 100f).coerceIn(0f, 100f)
            } else {
                previousByPid[raw.pid]?.cpuPercent ?: 0f
            }

            // 用 appId 判断可兼容多用户 / 工作资料（uid = userId * 100000 + appId）
            val isAppUid = (raw.uid % PER_USER_RANGE) in APP_ID_RANGE
            val meta = if (isAppUid) resolvePackageMeta(raw.uid, raw.processName) else null

            entries += ProcessEntry(
                pid = raw.pid,
                uid = raw.uid,
                processName = raw.processName,
                packageName = meta?.packageName,
                versionName = meta?.versionName.orEmpty(),
                memoryMb = raw.rssKb / 1024f,
                cpuPercent = cpuPercent,
                isSystem = !isAppUid,
                isSelf = meta?.packageName == context.packageName
            )
        }

        lastCpuTicks = statTicks
        lastCpuSampleMs = now
        return entries
    }

    /**
     * 解析 `ps -A -o PID,UID,RSS,ARGS` 的一行。
     * 内核线程的 cmdline 为空，toybox 会渲染成 `[kworker/...]`，直接过滤掉。
     */
    private fun parsePsLine(line: String): RawProcess? {
        val match = PS_LINE_REGEX.matchEntire(line) ?: return null
        val pid = match.groupValues[1].toIntOrNull() ?: return null
        val uid = match.groupValues[2].toIntOrNull() ?: return null
        val rssKb = match.groupValues[3].toLongOrNull() ?: 0L
        val args = match.groupValues[4].trim()
        if (pid <= 0 || args.isEmpty() || args.startsWith("[")) return null
        // ARGS 可能带命令行参数，进程名取第一个 token
        val name = args.substringBefore(' ')
        // 过滤采集命令自身产生的短命 ps 进程，避免进程数每次刷新抖动
        if (name == "ps") return null
        return RawProcess(pid, uid, rssKb, name)
    }

    /**
     * 解析 `<pid> <stat...>`，取 utime(14) / stime(15)。
     * comm 字段可能包含空格与括号，必须从最后一个 ')' 之后开始切分。
     */
    private fun parseStatLine(line: String, out: MutableMap<Int, Long>) {
        val spaceIndex = line.indexOf(' ')
        if (spaceIndex <= 0) return
        val pid = line.substring(0, spaceIndex).toIntOrNull() ?: return
        val closeParen = line.lastIndexOf(')')
        if (closeParen < 0 || closeParen + 2 >= line.length) return
        val fields = line.substring(closeParen + 2).trim().split(WHITESPACE_REGEX)
        if (fields.size < 13) return
        val utime = fields[11].toLongOrNull() ?: return
        val stime = fields[12].toLongOrNull() ?: return
        out[pid] = utime + stime
    }

    private fun resolvePackageMeta(uid: Int, processName: String): PackageMeta? {
        val cacheKey = "$uid:${processName.substringBefore(':')}"
        packageCache[cacheKey]?.let { return it }

        val pm = context.packageManager
        val candidate = processName.substringBefore(':')
        val packages = runCatching { pm.getPackagesForUid(uid) }.getOrNull()

        val packageName = when {
            packages.isNullOrEmpty() -> candidate
            packages.contains(candidate) -> candidate
            packages.size == 1 -> packages[0]
            else -> packages.firstOrNull { candidate.startsWith(it) } ?: packages[0]
        }

        // 进程名来自 /proc cmdline，可能被恶意进程构造，必须校验后才能用于 root 命令
        if (!PACKAGE_NAME_REGEX.matches(packageName)) return null

        val versionName = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()

        val meta = PackageMeta(packageName, versionName)
        packageCache[cacheKey] = meta
        return meta
    }

    private data class RawProcess(
        val pid: Int,
        val uid: Int,
        val rssKb: Long,
        val processName: String
    )

    private data class PackageMeta(
        val packageName: String,
        val versionName: String
    )

    private companion object {
        const val TAG = "WearMonitor_Process"
        const val PS_MARKER = "__WM_PS__"
        const val STAT_MARKER = "__WM_STAT__"
        const val PER_USER_RANGE = 100_000
        val APP_ID_RANGE = 10_000..19_999
        val PS_LINE_REGEX = Regex("""^(\d+)\s+(\d+)\s+(\d+)\s+(.*)$""")
        val WHITESPACE_REGEX = Regex("\\s+")
        val PACKAGE_NAME_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

        /**
         * 一条命令完成两次枚举：
         * 1. ps 取 pid/uid/rss/args；
         * 2. 仅用 shell 内建 read 批量读取 /proc/<pid>/stat（零 fork）。
         */
        val COLLECT_COMMAND: String = buildString {
            append("echo ").append(PS_MARKER).append('\n')
            // 字段名用小写：toybox 与 procps 均接受，避免大小写差异导致整条命令失败
            append("ps -A -o pid,uid,rss,args 2>/dev/null\n")
            append("echo ").append(STAT_MARKER).append('\n')
            append("for p in \$(ps -A -o pid 2>/dev/null); do ")
            append("s=; IFS= read -r s < /proc/\$p/stat 2>/dev/null; ")
            append("[ -n \"\$s\" ] && echo \"\$p \$s\"; done\n")
        }
    }
}
