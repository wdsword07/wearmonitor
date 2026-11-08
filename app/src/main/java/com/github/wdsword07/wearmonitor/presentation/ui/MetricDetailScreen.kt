package com.github.wdsword07.wearmonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.github.wdsword07.wearmonitor.data.model.MetricDetailType
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import java.util.Locale

@Composable
fun MetricDetailScreen(
    type: MetricDetailType,
    history: List<SystemSnapshot>,
    latest: SystemSnapshot,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(
        scrollState = listState,
        modifier = modifier.fillMaxSize()
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 6.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                start = 10.dp,
                end = 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部返回栏
            item(key = "nav_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = type.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 根据类型展示对应的趋势图和详细卡片
            when (type) {
                MetricDetailType.CPU -> {
                    item(key = "cpu_chart") {
                        val points = history.map { it.cpu.overallUsagePercent }
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = String.format(Locale.US, "实时: %.1f%%", latest.cpu.overallUsagePercent),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MetricTrendChart(
                                    points = points,
                                    fixedMinY = 0f,
                                    fixedMaxY = 100f,
                                    unit = "%",
                                    lineColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    item(key = "cpu_cores_detail") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "核心频率明细",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                latest.cpu.cores.forEach { core ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Core ${core.index}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (core.isOnline) "${core.curFreqMhz} MHz" else "Offline",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (core.isOnline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                if (latest.cpu.governor.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "调速策略: ${latest.cpu.governor}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                MetricDetailType.THERMAL -> {
                    item(key = "thermal_chart") {
                        val points = history.map { it.thermal.batteryTempC }
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "电池: %.1f °C", latest.thermal.batteryTempC),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB74D)
                                    )
                                    latest.thermal.socTempC?.let { soc ->
                                        Text(
                                            text = String.format(Locale.US, "SoC: %.1f °C", soc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF5252)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                MetricTrendChart(
                                    points = points,
                                    unit = "°C",
                                    lineColor = Color(0xFFFFB74D)
                                )
                            }
                        }
                    }
                }

                MetricDetailType.BATTERY -> {
                    item(key = "battery_chart") {
                        val points = history.mapNotNull { it.battery.powerWatts }
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                latest.battery.powerWatts?.let { watts ->
                                    val sign = if (watts > 0) "+" else ""
                                    Text(
                                        text = "功率: $sign${String.format(Locale.US, "%.2f", watts)} W",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (watts > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                MetricTrendChart(
                                    points = if (points.isNotEmpty()) points else listOf(0f),
                                    unit = "W",
                                    lineColor = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }

                    item(key = "battery_details") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "电池指标明细",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("电量百分比", style = MaterialTheme.typography.bodySmall)
                                    Text("${latest.battery.levelPercent}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                latest.battery.currentMa?.let { current ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("即时电流", style = MaterialTheme.typography.bodySmall)
                                        Text("${current} mA", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                latest.battery.voltageMv?.let { volt ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("端电压", style = MaterialTheme.typography.bodySmall)
                                        Text("${volt} mV", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                MetricDetailType.MEMORY -> {
                    item(key = "memory_chart") {
                        val points = history.map { it.memory.usedPercent }
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = String.format(Locale.US, "RAM 占用: %.0f%% (%d MB)", latest.memory.usedPercent, latest.memory.usedMb),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MetricTrendChart(
                                    points = points,
                                    fixedMinY = 0f,
                                    fixedMaxY = 100f,
                                    unit = "%",
                                    lineColor = Color(0xFF64B5F6)
                                )
                            }
                        }
                    }

                    item(key = "memory_details") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "内存划分明细",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("可用空闲", style = MaterialTheme.typography.bodySmall)
                                    Text("${latest.memory.availMb} MB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("总物理 RAM", style = MaterialTheme.typography.bodySmall)
                                    Text("${latest.memory.totalMb} MB", style = MaterialTheme.typography.bodySmall)
                                }
                                if (latest.memory.swapTotalMb > 0) {
                                    val swapUsed = (latest.memory.swapTotalMb - latest.memory.swapFreeMb).coerceAtLeast(0)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ZRAM / Swap 已用", style = MaterialTheme.typography.bodySmall)
                                        Text("$swapUsed / ${latest.memory.swapTotalMb} MB", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
