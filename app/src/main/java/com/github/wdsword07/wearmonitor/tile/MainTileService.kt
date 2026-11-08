package com.github.wdsword07.wearmonitor.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.card
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.monitor.SystemMonitorRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

private const val RESOURCES_VERSION = "1"

class MainTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        serviceScope.launch {
            try {
                val repository = SystemMonitorRepository.getInstance(applicationContext)
                val snapshot = repository.getSnapshot()
                future.set(createTile(requestParams, this@MainTileService, snapshot))
            } catch (_: Exception) {
                future.set(createTile(requestParams, this@MainTileService, SystemSnapshot()))
            }
        }
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(resources(requestParams))
}

private fun resources(requestParams: RequestBuilders.ResourcesRequest): Resources {
    return Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}

private fun createTile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    snapshot: SystemSnapshot,
): TileBuilders.Tile {
    val launchIntentAction = ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(context.packageName)
                .setClassName("com.github.wdsword07.wearmonitor.presentation.MainActivity")
                .build()
        )
        .build()

    val clickable = ModifiersBuilders.Clickable.Builder()
        .setOnClick(launchIntentAction)
        .build()

    val cpuText = String.format(Locale.US, "CPU: %.0f%%", snapshot.cpu.overallUsagePercent)
    val ramText = String.format(Locale.US, "RAM: %.0f%%", snapshot.memory.usedPercent)
    val battTempText = String.format(Locale.US, "%.1f°C", snapshot.thermal.batteryTempC)
    val batteryText = "电量: ${snapshot.battery.levelPercent}% ($battTempText)"

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                materialScope(context, requestParams.deviceConfiguration) {
                    primaryLayout(
                        titleSlot = {
                            text(
                                "WearMonitor".layoutString,
                                typography = Typography.TITLE_SMALL
                            )
                        },
                        mainSlot = {
                            LayoutElementBuilders.Column.Builder()
                                .setWidth(expand())
                                .setModifiers(
                                    ModifiersBuilders.Modifiers.Builder()
                                        .setClickable(clickable)
                                        .build()
                                )
                                .addContent(
                                    card(
                                        onClick = clickable
                                    ) {
                                        LayoutElementBuilders.Column.Builder()
                                            .setWidth(expand())
                                            .addContent(
                                                text(
                                                    "$cpuText | $ramText".layoutString,
                                                    typography = Typography.BODY_MEDIUM
                                                )
                                            )
                                            .addContent(
                                                text(
                                                    batteryText.layoutString,
                                                    typography = Typography.BODY_SMALL
                                                )
                                            )
                                            .build()
                                    }
                                )
                                .build()
                        },
                        bottomSlot = {
                            text(
                                "点击查看详细".layoutString,
                                typography = Typography.LABEL_SMALL
                            )
                        }
                    )
                }
            )
        )
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    createTile(it, context, SystemSnapshot())
}
