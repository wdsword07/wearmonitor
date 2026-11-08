package com.github.wdsword07.wearmonitor.data.monitor

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.github.wdsword07.wearmonitor.data.model.SystemDeviceInfo
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 系统静态信息探测。
 *
 * 重要：所有静态字段都在后台协程内探测并缓存，[getDeviceInfo] 永远立即返回，
 * 绝不阻塞主数据流（历史教训：root 探测一旦卡住会让整块面板归零）。
 */
class SystemInfoMonitor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probing = AtomicBoolean(false)

    @Volatile
    private var hasProbed = false

    @Volatile
    private var cachedStaticInfo: SystemDeviceInfo? = null

    fun getDeviceInfo(isRootGranted: Boolean): SystemDeviceInfo {
        val cached = cachedStaticInfo
        if (cached == null) {
            if (hasProbed) {
                // 探测已完成但完全失败，至少返回可立即获得的构建信息
                return baseInfo().copy(uptimeFormatted = formatUptime())
            }
            startProbe(isRootGranted)
            return baseInfo().copy(uptimeFormatted = formatUptime())
        }
        return cached.copy(uptimeFormatted = formatUptime())
    }

    private fun startProbe(isRootGranted: Boolean) {
        if (!probing.compareAndSet(false, true)) return
        scope.launch {
            try {
                cachedStaticInfo = probeStaticInfo(isRootGranted)
                hasProbed = true
            } catch (e: Exception) {
                Log.w(TAG, "System info probe failed: ${e.message}")
                hasProbed = true
            } finally {
                probing.set(false)
            }
        }
    }

    /** 零 I/O 的基础信息，任何时刻都能立即返回 */
    private fun baseInfo(): SystemDeviceInfo {
        val metrics = context.resources.displayMetrics
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val modelName = Build.MODEL?.trim().orEmpty()
        val displayModel = if (modelName.startsWith(manufacturer, ignoreCase = true) || manufacturer.isEmpty()) {
            modelName
        } else {
            "$manufacturer $modelName"
        }
        return SystemDeviceInfo(
            model = displayModel.ifBlank { "Unknown" },
            deviceCodename = Build.DEVICE ?: "Unknown",
            manufacturer = manufacturer.ifBlank { "Unknown" },
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            buildDisplay = Build.DISPLAY ?: "",
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "未知",
            uptimeFormatted = "",
            screenResolution = "${metrics.widthPixels} × ${metrics.heightPixels} px",
            isRoundScreen = context.resources.configuration.isScreenRound,
            screenDpi = metrics.densityDpi,
            refreshRate = 60f
        )
    }

    private suspend fun probeStaticInfo(isRootGranted: Boolean): SystemDeviceInfo {
        val base = baseInfo()

        // 1. Linux 内核信息（应用可直接读取，无需 root）
        val rawKernel = RootShell.readFile("/proc/version", singleLineOnly = true) ?: "未知"
        val kernelVersion = rawKernel.removePrefix("Linux version ")

        // 2. Root 环境探测：禁止使用 `su -v`！
        //    在持久化 su shell 内再次执行 su 会继承并抢占会话 stdin，
        //    导致 token 丢失、读取永久阻塞（本次重大 bug 的直接原因）。
        //    改用 root 管理器自身的 applet / 二进制，它们不会重新申请 su 会话。
        //    注意：这一步也会预热 su 会话，后续 sysfs 读取可以命中热会话。
        var rootProvider = if (isRootGranted) "已授权 (Rooted)" else "未授权 / 无 Root"
        var suPath = ""
        var bootloaderState = "未知"

        if (isRootGranted) {
            val whichSu = RootShell.execCommand("which su")
            if (!whichSu.isNullOrBlank()) {
                suPath = whichSu.lineSequence().firstOrNull()?.trim().orEmpty()
            }

            if (suPath.contains("apd", ignoreCase = true)) {
                rootProvider = "APatch (${RootShell.execCommand("apd -V")?.trim().orEmpty()})"
            } else {
                val magiskVersion = RootShell.execCommand("magisk -v")
                val ksudVersion = if (magiskVersion.isNullOrBlank()) RootShell.execCommand("ksud -V") else null
                val apdVersion = if (magiskVersion.isNullOrBlank() && ksudVersion.isNullOrBlank()) {
                    RootShell.execCommand("apd -V")
                } else {
                    null
                }
                rootProvider = when {
                    !magiskVersion.isNullOrBlank() -> "Magisk (${magiskVersion.trim()})"
                    !ksudVersion.isNullOrBlank() -> "KernelSU (${ksudVersion.trim()})"
                    !apdVersion.isNullOrBlank() -> "APatch (${apdVersion.trim()})"
                    else -> "通用 SU (UID 0)"
                }
            }

            val bootState = RootShell.execCommand("getprop ro.boot.verifiedbootstate")?.trim()
            val flashLocked = RootShell.execCommand("getprop ro.boot.flash.locked")?.trim()
            bootloaderState = when {
                bootState.equals("orange", ignoreCase = true) || flashLocked == "0" -> "已解锁 (Unlocked / Orange)"
                bootState.equals("green", ignoreCase = true) || flashLocked == "1" -> "已锁定 (Locked / Green)"
                bootState.equals("yellow", ignoreCase = true) -> "自签名 (Custom Key / Yellow)"
                !bootState.isNullOrBlank() -> bootState.uppercase()
                else -> "未知"
            }
        }

        // 3. SELinux 状态（普通 IO 会被 SELinux 拦截，走带超时保护的 root 读取；
        //    此时 su 会话已被预热，失败时再重试一次以避免黑名单误伤）
        var enforceVal = RootShell.readFile("/sys/fs/selinux/enforce", singleLineOnly = true)?.trim()
        if (enforceVal.isNullOrBlank() && isRootGranted) {
            enforceVal = RootShell.execCommand("cat /sys/fs/selinux/enforce", timeoutMs = 3000L)?.trim()
        }
        val selinuxMode = when (enforceVal?.trim()) {
            "1" -> "Enforcing (严格强制)"
            "0" -> "Permissive (宽容模式)"
            else -> "未知 / 隐藏"
        }

        // 4. 显示刷新率
        val refreshRate = runCatching {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.defaultDisplay?.refreshRate
        }.getOrNull() ?: 60f

        return base.copy(
            kernelVersion = kernelVersion,
            rootProvider = rootProvider,
            suPath = suPath,
            selinuxMode = selinuxMode,
            bootloaderState = bootloaderState,
            refreshRate = refreshRate,
            radioVersion = Build.getRadioVersion()?.ifBlank { "无基带 / Wi-Fi版" } ?: "无基带 / Wi-Fi版"
        )
    }

    private fun formatUptime(): String {
        val totalSecs = SystemClock.elapsedRealtime() / 1000
        val days = totalSecs / (24 * 3600)
        val hours = (totalSecs % (24 * 3600)) / 3600
        val mins = (totalSecs % 3600) / 60
        return when {
            days > 0 -> "${days}天 ${hours}小时 ${mins}分"
            hours > 0 -> "${hours}小时 ${mins}分"
            else -> "${mins}分钟"
        }
    }

    private companion object {
        const val TAG = "WearMonitor_SysInfo"
    }
}