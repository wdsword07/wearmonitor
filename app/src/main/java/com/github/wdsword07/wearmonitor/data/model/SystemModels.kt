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
    val governor: String = ""
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
    val swapFreeMb: Long = 0
)

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercent: Float
)

data class SystemSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val isRootGranted: Boolean = false,
    val cpu: CpuInfo = CpuInfo(0f, emptyList()),
    val thermal: ThermalInfo = ThermalInfo(0f, null),
    val battery: BatteryInfo = BatteryInfo(0, false, null, null, null),
    val memory: MemoryInfo = MemoryInfo(0, 0, 0, 0f),
    val storage: StorageInfo = StorageInfo(0, 0, 0, 0f)
)
