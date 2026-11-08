package com.github.wdsword07.wearmonitor.data.model

data class CpuCoreInfo(
    val index: Int,
    val curFreqMhz: Int,
    val minFreqMhz: Int = 0,
    val maxFreqMhz: Int = 0,
    val isOnline: Boolean = true
)

data class CpuInfo(
    val overallUsagePercent: Float, // 0..100
    val cores: List<CpuCoreInfo>,
    val governor: String = "",
    val socPlatform: String = "",
    val socHardware: String = "",
    val cpuArch: String = "",
    val supportedAbis: String = ""
)

data class ThermalZone(
    val id: Int,
    val type: String,
    val tempC: Float
)

data class ThermalInfo(
    val batteryTempC: Float,
    val socTempC: Float?,
    val zones: List<ThermalZone> = emptyList()
)

data class BatteryInfo(
    val levelPercent: Int,
    val isCharging: Boolean,
    val currentMa: Int?,       // 正数为充电流入，负数为放电
    val voltageMv: Int?,
    val powerWatts: Float?     // 充放电实时功率 (W)
)

data class MemoryInfo(
    val totalMb: Long,
    val availMb: Long,
    val usedMb: Long,
    val usedPercent: Float,
    val swapTotalMb: Long = 0,
    val swapFreeMb: Long = 0,
    val swapUsedMb: Long = (swapTotalMb - swapFreeMb).coerceAtLeast(0L),
    val swapUsedPercent: Float = if (swapTotalMb > 0) {
        (((swapTotalMb - swapFreeMb).coerceAtLeast(0L).toFloat() / swapTotalMb.toFloat()) * 100f).coerceIn(0f, 100f)
    } else {
        0f
    },
    val zramOrigMb: Float = 0f,
    val zramComprMb: Float = 0f,
    val zramRatio: Float = 0f,
    val zramAlgorithm: String = ""
)

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercent: Float
) {
    val totalGb: Float get() = totalBytes / (1024f * 1024f * 1024f)
    val freeGb: Float get() = freeBytes / (1024f * 1024f * 1024f)
    val usedGb: Float get() = usedBytes / (1024f * 1024f * 1024f)
}

data class SystemDeviceInfo(
    val model: String = "",
    val deviceCodename: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val buildDisplay: String = "",
    val securityPatch: String = "",
    val uptimeFormatted: String = "",
    val kernelVersion: String = "",
    val rootProvider: String = "",
    val suPath: String = "",
    val selinuxMode: String = "",
    val bootloaderState: String = "",
    val screenResolution: String = "",
    val isRoundScreen: Boolean = true,
    val screenDpi: Int = 0,
    val refreshRate: Float = 60f,
    val radioVersion: String = ""
)

enum class MetricDetailType(val title: String) {
    SYSTEM_INFO("系统环境"),
    CPU("CPU 趋势"),
    THERMAL("温度趋势"),
    BATTERY("供电趋势"),
    MEMORY("RAM & Swap")
}

data class SystemSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val isRootGranted: Boolean = false,
    val cpu: CpuInfo = CpuInfo(0f, emptyList()),
    val thermal: ThermalInfo = ThermalInfo(0f, null),
    val battery: BatteryInfo = BatteryInfo(0, false, null, null, null),
    val memory: MemoryInfo = MemoryInfo(0, 0, 0, 0f),
    val storage: StorageInfo = StorageInfo(0, 0, 0, 0f),
    val deviceInfo: SystemDeviceInfo = SystemDeviceInfo()
)
