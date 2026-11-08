package com.github.wdsword07.wearmonitor.data.monitor

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import com.github.wdsword07.wearmonitor.data.model.SystemDeviceInfo
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemInfoMonitor(private val context: Context) {
    @Volatile
    private var cachedStaticInfo: SystemDeviceInfo? = null

    suspend fun getDeviceInfo(isRootGranted: Boolean): SystemDeviceInfo = withContext(Dispatchers.IO) {
        val base = cachedStaticInfo ?: probeStaticInfo(isRootGranted).also { cachedStaticInfo = it }
        base.copy(uptimeFormatted = formatUptime())
    }

    private suspend fun probeStaticInfo(isRootGranted: Boolean): SystemDeviceInfo {
        // 1. 设备与构建系统
        val model = Build.MODEL ?: "Unknown"
        val deviceCodename = Build.DEVICE ?: "Unknown"
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
        val apiLevel = Build.VERSION.SDK_INT
        val buildDisplay = Build.DISPLAY ?: ""
        val securityPatch = Build.VERSION.SECURITY_PATCH ?: "未知"
        val radioVersion = Build.getRadioVersion()?.ifBlank { null } ?: "无基带 / Wi-Fi版"

        // 2. 屏幕与显示
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display
            } catch (_: Exception) {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi
        val isRound = context.resources.configuration.isScreenRound
        val refreshRate = display?.refreshRate ?: 60f

        // 3. Linux 内核信息
        val rawKernel = RootShell.readFile("/proc/version", singleLineOnly = true) ?: "未知"
        val kernelVersion = if (rawKernel.startsWith("Linux version ")) {
            rawKernel.removePrefix("Linux version ")
        } else {
            rawKernel
        }

        // 4. SELinux 状态
        val enforceVal = RootShell.readFile("/sys/fs/selinux/enforce", singleLineOnly = true)
        val selinuxMode = when (enforceVal) {
            "1" -> "Enforcing (严格强制)"
            "0" -> "Permissive (宽容模式)"
            else -> "未知 / 隐藏"
        }

        // 5. 深入 Root 特权与环境
        var rootProvider = if (isRootGranted) "已授权 (Rooted)" else "未授权 / 无 Root"
        var suPath = ""
        var bootloaderState = "未知"

        if (isRootGranted) {
            // su 路径
            val whichSu = RootShell.execCommand("which su")
            if (!whichSu.isNullOrBlank()) {
                suPath = whichSu.lines().firstOrNull()?.trim() ?: ""
            }

            // 检测 Root 管理器类型及版本 (Magisk, KernelSU, APatch 等)
            val suVersion = RootShell.execCommand("su -v")?.trim() ?: ""
            rootProvider = when {
                suVersion.contains("MAGISK", ignoreCase = true) -> "Magisk ($suVersion)"
                suVersion.contains("KernelSU", ignoreCase = true) || rawKernel.contains("KernelSU", ignoreCase = true) -> "KernelSU ($suVersion)"
                suVersion.contains("APatch", ignoreCase = true) -> "APatch ($suVersion)"
                suVersion.isNotBlank() -> "SU ($suVersion)"
                else -> "通用 SU (UID 0)"
            }

            // Bootloader 解锁状态
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

        return SystemDeviceInfo(
            model = model,
            deviceCodename = deviceCodename,
            manufacturer = manufacturer,
            androidVersion = androidVersion,
            apiLevel = apiLevel,
            buildDisplay = buildDisplay,
            securityPatch = securityPatch,
            uptimeFormatted = "",
            kernelVersion = kernelVersion,
            rootProvider = rootProvider,
            suPath = suPath,
            selinuxMode = selinuxMode,
            bootloaderState = bootloaderState,
            screenResolution = "${width} × ${height} px",
            isRoundScreen = isRound,
            screenDpi = dpi,
            refreshRate = refreshRate,
            radioVersion = radioVersion
        )
    }

    private fun formatUptime(): String {
        val millis = SystemClock.elapsedRealtime()
        val totalSecs = millis / 1000
        val days = totalSecs / (24 * 3600)
        val hours = (totalSecs % (24 * 3600)) / 3600
        val mins = (totalSecs % 3600) / 60
        return if (days > 0) {
            "${days}天 ${hours}小时 ${mins}分"
        } else if (hours > 0) {
            "${hours}小时 ${mins}分"
        } else {
            "${mins}分钟"
        }
    }
}
