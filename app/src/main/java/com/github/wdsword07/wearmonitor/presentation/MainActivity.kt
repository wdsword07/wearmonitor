package com.github.wdsword07.wearmonitor.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.github.wdsword07.wearmonitor.data.model.BatteryInfo
import com.github.wdsword07.wearmonitor.data.model.CpuCoreInfo
import com.github.wdsword07.wearmonitor.data.model.CpuInfo
import com.github.wdsword07.wearmonitor.data.model.MemoryInfo
import com.github.wdsword07.wearmonitor.data.model.MetricDetailType
import com.github.wdsword07.wearmonitor.data.model.StorageInfo
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.model.ThermalInfo
import com.github.wdsword07.wearmonitor.presentation.theme.WearmonitorTheme
import com.github.wdsword07.wearmonitor.presentation.ui.BatteryCard
import com.github.wdsword07.wearmonitor.presentation.ui.CpuCard
import com.github.wdsword07.wearmonitor.presentation.ui.MemoryCard
import com.github.wdsword07.wearmonitor.presentation.ui.MetricDetailScreen
import com.github.wdsword07.wearmonitor.presentation.ui.StatusHeaderCard
import com.github.wdsword07.wearmonitor.presentation.ui.StorageCard
import com.github.wdsword07.wearmonitor.presentation.ui.ThermalCard

class MainActivity : ComponentActivity() {
    private val viewModel: SystemMonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by viewModel.snapshotState.collectAsStateWithLifecycle()
            val history by viewModel.history.collectAsStateWithLifecycle()
            val currentDetailType by viewModel.currentDetailType.collectAsStateWithLifecycle()

            // 拦截系统返回键，如果在二级页面则返回主面板
            BackHandler(enabled = currentDetailType != null) {
                viewModel.closeDetail()
            }

            WearmonitorApp(
                snapshot = snapshot,
                history = history,
                detailType = currentDetailType,
                onOpenDetail = { type -> viewModel.openDetail(type) },
                onBack = { viewModel.closeDetail() },
                onScrollInProgressChanged = { isScrolling ->
                    viewModel.setScrolling(isScrolling)
                }
            )
        }
    }
}

@Composable
fun WearmonitorApp(
    snapshot: SystemSnapshot,
    history: List<SystemSnapshot> = emptyList(),
    detailType: MetricDetailType? = null,
    onOpenDetail: (MetricDetailType) -> Unit = {},
    onBack: () -> Unit = {},
    onScrollInProgressChanged: (Boolean) -> Unit = {}
) {
    WearmonitorTheme {
        AppScaffold {
            val swipeDismissState = rememberSwipeToDismissBoxState()
            LaunchedEffect(swipeDismissState.currentValue) {
                if (swipeDismissState.currentValue == SwipeToDismissValue.Dismissed) {
                    swipeDismissState.snapTo(SwipeToDismissValue.Default)
                    onBack()
                }
            }

            SwipeToDismissBox(
                state = swipeDismissState,
                userSwipeEnabled = detailType != null,
                backgroundKey = "dashboard",
                contentKey = detailType ?: "dashboard",
                onDismissed = onBack
            ) { isBackground ->
                if (isBackground || detailType == null) {
                    val listState = rememberTransformingLazyColumnState()

                    LaunchedEffect(listState) {
                        snapshotFlow { listState.isScrollInProgress }
                            .collect { isScrolling ->
                                onScrollInProgressChanged(isScrolling)
                            }
                    }

                    ScreenScaffold(
                        scrollState = listState
                    ) { contentPadding ->
                        TransformingLazyColumn(
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding() + 8.dp,
                                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                                start = 10.dp,
                                end = 10.dp
                            ),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(key = "header") {
                                StatusHeaderCard(
                                    isRootGranted = snapshot.isRootGranted,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item(key = "cpu") {
                                CpuCard(
                                    cpu = snapshot.cpu,
                                    onClick = { onOpenDetail(MetricDetailType.CPU) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item(key = "thermal") {
                                ThermalCard(
                                    thermal = snapshot.thermal,
                                    onClick = { onOpenDetail(MetricDetailType.THERMAL) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item(key = "battery") {
                                BatteryCard(
                                    battery = snapshot.battery,
                                    onClick = { onOpenDetail(MetricDetailType.BATTERY) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item(key = "memory") {
                                MemoryCard(
                                    memory = snapshot.memory,
                                    onClick = { onOpenDetail(MetricDetailType.MEMORY) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item(key = "storage") {
                                StorageCard(
                                    storage = snapshot.storage,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    MetricDetailScreen(
                        type = detailType,
                        history = history,
                        latest = snapshot,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun WearmonitorPreview() {
    val sampleSnapshot = SystemSnapshot(
        isRootGranted = true,
        cpu = CpuInfo(
            overallUsagePercent = 42.5f,
            cores = listOf(
                CpuCoreInfo(0, 1400, isOnline = true),
                CpuCoreInfo(1, 1400, isOnline = true),
                CpuCoreInfo(2, 800, isOnline = true),
                CpuCoreInfo(3, 800, isOnline = true)
            ),
            governor = "schedutil"
        ),
        thermal = ThermalInfo(
            batteryTempC = 36.2f,
            socTempC = 44.8f
        ),
        battery = BatteryInfo(
            levelPercent = 78,
            isCharging = true,
            currentMa = 420,
            voltageMv = 4120,
            powerWatts = 1.73f
        ),
        memory = MemoryInfo(
            totalMb = 1850,
            availMb = 720,
            usedMb = 1130,
            usedPercent = 61.1f,
            swapTotalMb = 1024,
            swapFreeMb = 204,
            swapUsedMb = 820,
            swapUsedPercent = 80.1f,
            zramOrigMb = 1850.5f,
            zramComprMb = 650.2f,
            zramRatio = 2.85f,
            zramAlgorithm = "lz4"
        ),
        storage = StorageInfo(
            totalBytes = 32L * 1024 * 1024 * 1024,
            freeBytes = 18L * 1024 * 1024 * 1024,
            usedBytes = 14L * 1024 * 1024 * 1024,
            usedPercent = 43.7f
        )
    )
    WearmonitorApp(snapshot = sampleSnapshot)
}
