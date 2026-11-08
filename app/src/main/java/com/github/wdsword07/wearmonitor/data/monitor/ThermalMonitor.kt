package com.github.wdsword07.wearmonitor.data.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.github.wdsword07.wearmonitor.data.model.ThermalInfo
import com.github.wdsword07.wearmonitor.data.model.ThermalZone
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ThermalMonitor(private val context: Context) {
    // 缓存探测到的 SoC 核心温区 ID，避免每次采样扫描整个文件系统
    @Volatile
    private var cachedSocZoneId: Int? = null
    @Volatile
    private var hasProbedSocZone = false

    suspend fun sampleThermalInfo(): ThermalInfo = withContext(Dispatchers.IO) {
        val batteryTemp = readBatteryTemp()
        val socTemp = readSocTemp()
        ThermalInfo(
            batteryTempC = batteryTemp,
            socTempC = socTemp,
            zones = emptyList()
        )
    }

    private suspend fun readBatteryTemp(): Float {
        // 1. 首选 BatteryManager 系统广播（零 I/O 成本，系统服务常驻维护）
        try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (tempTenths > 0) {
                return tempTenths / 10f
            }
        } catch (_: Exception) {}

        // 2. 备用读取 sysfs
        val battTempSys = RootShell.readFile("/sys/class/power_supply/battery/temp")
        battTempSys?.toIntOrNull()?.let { raw ->
            return normalizeTemp(raw)
        }
        return 0f
    }

    private suspend fun readSocTemp(): Float? {
        if (!hasProbedSocZone) {
            probeSocZone()
            hasProbedSocZone = true
        }

        val zoneId = cachedSocZoneId
        if (zoneId != null) {
            val tempRawStr = RootShell.readFile("/sys/class/thermal/thermal_zone$zoneId/temp")
            val tempRaw = tempRawStr?.toIntOrNull()
            if (tempRaw != null) {
                return normalizeTemp(tempRaw)
            }
        }
        return null
    }

    private suspend fun probeSocZone() {
        val thermalDir = File("/sys/class/thermal")
        val zoneDirs = thermalDir.listFiles { _, name -> name.startsWith("thermal_zone") }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }
            ?: return

        for (dir in zoneDirs.take(12)) {
            val id = dir.name.removePrefix("thermal_zone").toIntOrNull() ?: continue
            val type = RootShell.readFile("/sys/class/thermal/thermal_zone$id/type")?.lowercase() ?: continue
            if (type.contains("soc") || type.contains("cpu") || type.contains("tsens") || type.contains("mtktscpu")) {
                cachedSocZoneId = id
                return
            }
        }

        // 若没找到明确标注 soc/cpu 的温区，使用 zone0 作为基准
        if (zoneDirs.isNotEmpty()) {
            cachedSocZoneId = 0
        }
    }

    private fun normalizeTemp(raw: Int): Float {
        return if (raw > 1000) {
            raw / 1000f
        } else if (raw > 200) {
            raw / 10f
        } else {
            raw.toFloat()
        }
    }
}
