package com.github.wdsword07.wearmonitor.data.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.github.wdsword07.wearmonitor.data.model.BatteryInfo
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class BatteryMonitor(private val context: Context) {

    suspend fun sampleBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.let {
            val rawLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) ((rawLevel * 100) / scale) else -1
        } ?: -1

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val currentMa = readCurrentMa(isCharging)
        val voltageMv = readVoltageMv(batteryIntent)

        val powerWatts = if (voltageMv != null && currentMa != null) {
            val watts = (voltageMv.toFloat() * abs(currentMa).toFloat()) / 1_000_000f
            if (currentMa < 0) -watts else watts
        } else {
            null
        }

        BatteryInfo(
            levelPercent = if (level >= 0) level else 0,
            isCharging = isCharging,
            currentMa = currentMa,
            voltageMv = voltageMv,
            powerWatts = powerWatts
        )
    }

    private suspend fun readCurrentMa(isCharging: Boolean): Int? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        var rawCurrent: Long? = null

        // 优先 BatteryManager property
        val currentNow = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentNow != null && currentNow != Long.MIN_VALUE && currentNow != 0L) {
            rawCurrent = currentNow
        }

        // 尝试从 sysfs 读取
        if (rawCurrent == null) {
            val currentSys = RootShell.readFile("/sys/class/power_supply/battery/current_now")
            rawCurrent = currentSys?.toLongOrNull()
        }

        if (rawCurrent == null) return null

        // 归一化为 mA（微安 vs 毫安）
        var ma = if (abs(rawCurrent) > 10_000) {
            (rawCurrent / 1000).toInt()
        } else {
            rawCurrent.toInt()
        }

        // 规范化符号：充电为正，放电为负
        if (isCharging && ma < 0) {
            ma = -ma
        } else if (!isCharging && ma > 0) {
            ma = -ma
        }

        return ma
    }

    private suspend fun readVoltageMv(batteryIntent: Intent?): Int? {
        val voltSys = RootShell.readFile("/sys/class/power_supply/battery/voltage_now")
        voltSys?.toLongOrNull()?.let { raw ->
            return if (raw > 100_000) (raw / 1000).toInt() else raw.toInt()
        }

        val voltIntent = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (voltIntent != null && voltIntent > 0) {
            return if (voltIntent > 100_000) voltIntent / 1000 else voltIntent
        }

        return null
    }
}
