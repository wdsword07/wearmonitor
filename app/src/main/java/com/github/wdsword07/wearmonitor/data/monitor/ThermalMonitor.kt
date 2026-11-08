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

    suspend fun sampleThermalInfo(): ThermalInfo = withContext(Dispatchers.IO) {
        val batteryTemp = readBatteryTemp()
        val zones = readThermalZones()
        val socTemp = findSocTemp(zones)
        ThermalInfo(
            batteryTempC = batteryTemp,
            socTempC = socTemp,
            zones = zones
        )
    }

    private suspend fun readBatteryTemp(): Float {
        // 1. 尝试从 sysfs 读取
        val battTempSys = RootShell.readFile("/sys/class/power_supply/battery/temp")
        battTempSys?.toIntOrNull()?.let { raw ->
            return normalizeTemp(raw)
        }

        // 2. 尝试从 Android BatteryManager 广播中获取
        try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (tempTenths > 0) {
                return tempTenths / 10f
            }
        } catch (_: Exception) {
        }
        return 0f
    }

    private suspend fun readThermalZones(): List<ThermalZone> {
        val result = mutableListOf<ThermalZone>()
        val thermalDir = File("/sys/class/thermal")
        val zoneDirs = thermalDir.listFiles { _, name -> name.startsWith("thermal_zone") }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }

        if (zoneDirs.isNullOrEmpty()) {
            for (i in 0..15) {
                val zone = readSingleThermalZone(i) ?: break
                result.add(zone)
            }
        } else {
            for (dir in zoneDirs.take(16)) {
                val id = dir.name.removePrefix("thermal_zone").toIntOrNull() ?: continue
                readSingleThermalZone(id)?.let { result.add(it) }
            }
        }
        return result
    }

    private suspend fun readSingleThermalZone(id: Int): ThermalZone? {
        val type = RootShell.readFile("/sys/class/thermal/thermal_zone$id/type") ?: return null
        val tempRawStr = RootShell.readFile("/sys/class/thermal/thermal_zone$id/temp") ?: return null
        val tempRaw = tempRawStr.toIntOrNull() ?: return null
        val tempC = normalizeTemp(tempRaw)
        return ThermalZone(id = id, type = type, tempC = tempC)
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

    private fun findSocTemp(zones: List<ThermalZone>): Float? {
        val socCandidates = zones.filter { zone ->
            val name = zone.type.lowercase()
            name.contains("soc") || name.contains("cpu") || name.contains("tsens") || name.contains("mtktscpu")
        }
        return if (socCandidates.isNotEmpty()) {
            socCandidates.map { it.tempC }.filter { it in 10f..115f }.maxOrNull()
        } else {
            zones.map { it.tempC }.filter { it in 15f..115f }.maxOrNull()
        }
    }
}
