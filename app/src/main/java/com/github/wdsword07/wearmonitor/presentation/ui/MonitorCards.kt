package com.github.wdsword07.wearmonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.github.wdsword07.wearmonitor.data.model.BatteryInfo
import com.github.wdsword07.wearmonitor.data.model.CpuInfo
import com.github.wdsword07.wearmonitor.data.model.MemoryInfo
import com.github.wdsword07.wearmonitor.data.model.StorageInfo
import com.github.wdsword07.wearmonitor.data.model.ThermalInfo
import java.util.Locale

@Composable
fun StatusHeaderCard(
    isRootGranted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRootGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WearMonitor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isRootGranted) "ROOTED" else "NO ROOT",
                style = MaterialTheme.typography.labelSmall,
                color = if (isRootGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CpuCard(
    cpu: CpuInfo,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CPU 负载",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "  ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f%%", cpu.overallUsagePercent),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            // 确保即使负载低也有微弱的可视最小进度，且正确响应 0f..1f
            val cpuProgress = if (cpu.overallUsagePercent > 0f) {
                (cpu.overallUsagePercent / 100f).coerceIn(0.02f, 1f)
            } else {
                0.02f
            }
            LinearProgressIndicator(
                progress = { cpuProgress },
                modifier = Modifier.fillMaxWidth()
            )

            if (cpu.cores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val onlineCores = cpu.cores.filter { it.isOnline }
                Text(
                    text = "核心: ${onlineCores.size}/${cpu.cores.size} 在线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val freqsText = onlineCores.take(4).joinToString(" | ") { core ->
                    "${core.curFreqMhz}M"
                }
                if (freqsText.isNotBlank()) {
                    Text(
                        text = freqsText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (cpu.governor.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "调速: ${cpu.governor}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun ThermalCard(
    thermal: ThermalInfo,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "温度状态",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "  ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "电池温度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f °C", thermal.batteryTempC),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = getTempColor(thermal.batteryTempC)
                    )
                }

                if (thermal.socTempC != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "芯片 SoC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f °C", thermal.socTempC),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = getTempColor(thermal.socTempC)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryCard(
    battery: BatteryInfo,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "电池与供电",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "  ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "${battery.levelPercent}%" + if (battery.isCharging) " ⚡" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (battery.levelPercent <= 20) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (battery.levelPercent / 100f).coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                battery.powerWatts?.let { watts ->
                    val sign = if (watts > 0) "+" else ""
                    Text(
                        text = "功率: $sign${String.format(Locale.US, "%.2f", watts)} W",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (watts > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                battery.currentMa?.let { current ->
                    val sign = if (current > 0) "+" else ""
                    Text(
                        text = "电流: $sign$current mA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            battery.voltageMv?.let { voltage ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "电压: ${voltage} mV",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun MemoryStorageCard(
    memory: MemoryInfo,
    storage: StorageInfo,
    onMemoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onMemoryClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "内存与存储",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "  ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RAM (${memory.usedMb}/${memory.totalMb} MB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.US, "%.0f%%", memory.usedPercent),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LinearProgressIndicator(
                progress = { (memory.usedPercent / 100f).coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            if (memory.swapTotalMb > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                val swapUsed = (memory.swapTotalMb - memory.swapFreeMb).coerceAtLeast(0)
                Text(
                    text = "Swap: $swapUsed / ${memory.swapTotalMb} MB",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (storage.totalBytes > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val usedGb = storage.usedBytes / (1024f * 1024 * 1024)
                val totalGb = storage.totalBytes / (1024f * 1024 * 1024)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "存储 (%.1f/%.1f GB)", usedGb, totalGb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", storage.usedPercent),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { (storage.usedPercent / 100f).coerceIn(0.02f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun getTempColor(tempC: Float): Color {
    return when {
        tempC >= 55f -> Color(0xFFFF5252) // 严重发热 红
        tempC >= 42f -> Color(0xFFFFB74D) // 温热 橙黄
        else -> Color(0xFF81C784)        // 舒适 绿
    }
}
