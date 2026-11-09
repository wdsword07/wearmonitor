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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
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
            // 顶部标题栏（移除显式返回按钮以避免圆屏边缘遮挡，手表端可直接侧滑返回）
            item(key = "nav_header") {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                ) {
                    Text(
                        text = type.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 根据类型展示对应的趋势图和详细卡片
            when (type) {
                MetricDetailType.SYSTEM_INFO -> {
                    val device = latest.deviceInfo

                    item(key = "sys_device_card") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "设备与系统固件",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("设备型号", style = MaterialTheme.typography.bodySmall)
                                    Text(device.model.ifBlank { "未知" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("内部代号", style = MaterialTheme.typography.bodySmall)
                                    Text(device.deviceCodename, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Android 版本", style = MaterialTheme.typography.bodySmall)
                                    Text("Android ${device.androidVersion} (API ${device.apiLevel})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                if (device.buildDisplay.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("固件版本号", style = MaterialTheme.typography.bodySmall)
                                        Text(device.buildDisplay.take(16), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("安全补丁", style = MaterialTheme.typography.bodySmall)
                                    Text(device.securityPatch, style = MaterialTheme.typography.bodySmall)
                                }
                                if (device.uptimeFormatted.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("运行时间", style = MaterialTheme.typography.bodySmall)
                                        Text(device.uptimeFormatted, style = MaterialTheme.typography.bodySmall, color = Color(0xFF81C784))
                                    }
                                }
                            }
                        }
                    }

                    item(key = "sys_root_card") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "特权与安全环境 (Root)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (latest.isRootGranted) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Root 状态", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = device.rootProvider,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (latest.isRootGranted) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                }
                                if (device.suPath.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("SU 路径", style = MaterialTheme.typography.bodySmall)
                                        Text(device.suPath, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("SELinux 模式", style = MaterialTheme.typography.bodySmall)
                                    Text(device.selinuxMode, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Bootloader 状态", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = device.bootloaderState,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (device.bootloaderState.contains("解锁", ignoreCase = true) || device.bootloaderState.contains("Unlocked", ignoreCase = true)) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (device.kernelVersion.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "内核版本:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = device.kernelVersion,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item(key = "sys_screen_card") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "屏幕与显示规格",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("物理分辨率", style = MaterialTheme.typography.bodySmall)
                                    Text(device.screenResolution, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("表盘形态", style = MaterialTheme.typography.bodySmall)
                                    Text(if (device.isRoundScreen) "圆形表盘 (Round)" else "矩形表盘 (Square)", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("像素密度 (DPI)", style = MaterialTheme.typography.bodySmall)
                                    Text("${device.screenDpi} DPI", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("屏幕刷新率", style = MaterialTheme.typography.bodySmall)
                                    Text("${device.refreshRate.toInt()} Hz", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    item(key = "sys_radio_card") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "基带与网络固件",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("基带版本", style = MaterialTheme.typography.bodySmall)
                                    Text(device.radioVersion.take(20), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                }
                            }
                        }
                    }
                }

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
                                    unit = "%",
                                    lineColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    item(key = "soc_hardware_details") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "SoC 芯片硬件规格",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (latest.cpu.socPlatform.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("SoC 平台 / 芯片", style = MaterialTheme.typography.bodySmall)
                                        Text(latest.cpu.socPlatform, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (latest.cpu.socHardware.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("主板硬件代号", style = MaterialTheme.typography.bodySmall)
                                        Text(latest.cpu.socHardware, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("指令集架构", style = MaterialTheme.typography.bodySmall)
                                    Text(latest.cpu.cpuArch, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                if (latest.cpu.supportedAbis.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("支持 ABI", style = MaterialTheme.typography.bodySmall)
                                        Text(latest.cpu.supportedAbis, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("物理核心总数", style = MaterialTheme.typography.bodySmall)
                                    Text("${latest.cpu.cores.size} 核物理核心", style = MaterialTheme.typography.bodySmall)
                                }
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
                    item(key = "ram_chart") {
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
                                    text = String.format(Locale.US, "RAM 占用: %.0f%% (%d/%d MB)", latest.memory.usedPercent, latest.memory.usedMb, latest.memory.totalMb),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64B5F6)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MetricTrendChart(
                                    points = points,
                                    unit = "%",
                                    lineColor = Color(0xFF64B5F6)
                                )
                            }
                        }
                    }

                    if (latest.memory.swapTotalMb > 0) {
                        item(key = "swap_chart") {
                            val swapPoints = history.map { it.memory.swapUsedPercent }
                            Card(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                    Text(
                                        text = String.format(Locale.US, "Swap 占用: %.0f%% (%d/%d MB)", latest.memory.swapUsedPercent, latest.memory.swapUsedMb, latest.memory.swapTotalMb),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBA68C8)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    MetricTrendChart(
                                        points = swapPoints,
                                        unit = "%",
                                        lineColor = Color(0xFFBA68C8)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "ram_details") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "RAM 物理内存明细",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF64B5F6)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("已用物理 RAM", style = MaterialTheme.typography.bodySmall)
                                    Text("${latest.memory.usedMb} MB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("系统可用空闲", style = MaterialTheme.typography.bodySmall)
                                    Text("${latest.memory.availMb} MB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("总物理 RAM", style = MaterialTheme.typography.bodySmall)
                                    Text("${latest.memory.totalMb} MB", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    item(key = "swap_details") {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                Text(
                                    text = "Swap & ZRAM 深度明细",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFFBA68C8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (latest.memory.swapTotalMb > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Swap 已用容量", style = MaterialTheme.typography.bodySmall)
                                        Text("${latest.memory.swapUsedMb} MB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Swap 剩余空闲", style = MaterialTheme.typography.bodySmall)
                                        Text("${latest.memory.swapFreeMb} MB", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Swap 配置总计", style = MaterialTheme.typography.bodySmall)
                                        Text("${latest.memory.swapTotalMb} MB", style = MaterialTheme.typography.bodySmall)
                                    }

                                    if (latest.memory.zramRatio > 0f) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("ZRAM 实时压缩比", style = MaterialTheme.typography.bodySmall)
                                            Text(String.format(Locale.US, "%.2f : 1", latest.memory.zramRatio), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("未压缩原始数据", style = MaterialTheme.typography.bodySmall)
                                            Text(String.format(Locale.US, "%.1f MB", latest.memory.zramOrigMb), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("压缩后物理占用", style = MaterialTheme.typography.bodySmall)
                                            Text(String.format(Locale.US, "%.1f MB", latest.memory.zramComprMb), style = MaterialTheme.typography.bodySmall)
                                        }
                                        val savedMb = (latest.memory.zramOrigMb - latest.memory.zramComprMb).coerceAtLeast(0f)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("节省物理内存", style = MaterialTheme.typography.bodySmall)
                                            Text(String.format(Locale.US, "%.1f MB", savedMb), style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                                        }
                                    }

                                    if (latest.memory.zramAlgorithm.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("压缩算法", style = MaterialTheme.typography.bodySmall)
                                            Text(latest.memory.zramAlgorithm.uppercase(Locale.US), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "提示: Android 采用 ZRAM 内存压缩盘，由 CPU 快速解压。内核 swappiness 会积极将冷数据压缩以释放大块物理 RAM，高 Swap 占用率属正常现象。",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Text(
                                        text = "设备未启用 Swap / ZRAM 分区",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 进程管理使用独立的 ProcessListScreen，不会进入本页面
                MetricDetailType.PROCESSES -> Unit
            }
        }
    }
}
