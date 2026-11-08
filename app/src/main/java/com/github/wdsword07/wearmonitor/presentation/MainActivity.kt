package com.github.wdsword07.wearmonitor.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.github.wdsword07.wearmonitor.data.model.BatteryInfo
import com.github.wdsword07.wearmonitor.data.model.CpuCoreInfo
import com.github.wdsword07.wearmonitor.data.model.CpuInfo
import com.github.wdsword07.wearmonitor.data.model.MemoryInfo
import com.github.wdsword07.wearmonitor.data.model.StorageInfo
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.model.ThermalInfo
import com.github.wdsword07.wearmonitor.presentation.theme.WearmonitorTheme
import com.github.wdsword07.wearmonitor.presentation.ui.BatteryCard
import com.github.wdsword07.wearmonitor.presentation.ui.CpuCard
import com.github.wdsword07.wearmonitor.presentation.ui.MemoryStorageCard
import com.github.wdsword07.wearmonitor.presentation.ui.StatusHeaderCard
import com.github.wdsword07.wearmonitor.presentation.ui.ThermalCard

class MainActivity : ComponentActivity() {
    private val viewModel: SystemMonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by viewModel.snapshotState.collectAsStateWithLifecycle()
            WearmonitorApp(
                snapshot = snapshot,
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
    onScrollInProgressChanged: (Boolean) -> Unit = {}
) {
    WearmonitorTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()

            // 监听列表滚动状态，在用户滑动时平息数据刷新，消除掉帧
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
                    contentPadding = contentPadding,
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header") {
                        StatusHeaderCard(
                            isRootGranted = snapshot.isRootGranted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }

                    item(key = "cpu") {
                        CpuCard(
                            cpu = snapshot.cpu,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }

                    item(key = "thermal") {
                        ThermalCard(
                            thermal = snapshot.thermal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }

                    item(key = "battery") {
                        BatteryCard(
                            battery = snapshot.battery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }

                    item(key = "memory") {
                        MemoryStorageCard(
                            memory = snapshot.memory,
                            storage = snapshot.storage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
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
            swapFreeMb = 820
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
