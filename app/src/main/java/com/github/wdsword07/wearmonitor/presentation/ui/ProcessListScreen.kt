package com.github.wdsword07.wearmonitor.presentation.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.github.wdsword07.wearmonitor.data.model.ProcessEntry
import com.github.wdsword07.wearmonitor.data.model.ProcessSort
import java.util.Locale

/**
 * 进程管理二级页。
 *
 * 每个进程一张小卡片：包名 / 版本 / 内存 / CPU，按所选指标降序排列。
 * 点击卡片弹出操作菜单：结束进程（需二次确认）或打开系统应用信息页。
 */
@Composable
fun ProcessListScreen(
    processes: List<ProcessEntry>,
    sort: ProcessSort,
    onToggleSort: () -> Unit,
    onKill: (ProcessEntry) -> Unit,
    onScrollInProgressChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()

    var actionTarget by remember { mutableStateOf<ProcessEntry?>(null) }
    var confirmTarget by remember { mutableStateOf<ProcessEntry?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { onScrollInProgressChanged(it) }
    }
    // 离开页面时复位滚动状态，避免主面板数据流被永久静默
    DisposableEffect(Unit) {
        onDispose { onScrollInProgressChanged(false) }
    }

    val sorted = remember(processes, sort) {
        when (sort) {
            ProcessSort.MEMORY -> processes.sortedByDescending { it.memoryMb }
            ProcessSort.CPU -> processes.sortedByDescending { it.cpuPercent }
        }
    }

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
            item(key = "process_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListHeader(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "进程管理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (processes.isEmpty()) "读取中…" else "共 ${processes.size} 个进程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompactButton(onClick = onToggleSort) {
                            Text(
                                text = when (sort) {
                                    ProcessSort.MEMORY -> "内存 ↓"
                                    ProcessSort.CPU -> "CPU ↓"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (sorted.isEmpty()) {
                item(key = "process_empty") {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Text(
                            text = "无法读取进程列表。\n请确认已授予 Root 权限。",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                    }
                }
            } else {
                items(sorted, key = { it.pid }) { entry ->
                    ProcessItemCard(
                        entry = entry,
                        onClick = { actionTarget = entry },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 操作菜单
    actionTarget?.let { entry ->
        AlertDialog(
            visible = true,
            onDismissRequest = { actionTarget = null },
            title = {
                Text(
                    text = entry.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    Text(
                        text = when {
                            entry.versionName.isNotBlank() -> "版本 ${entry.versionName}"
                            entry.isSystem -> "系统进程"
                            else -> "版本未知"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "内存 ${formatMemory(entry.memoryMb)} · CPU " +
                            String.format(Locale.US, "%.1f%%", entry.cpuPercent),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionTarget = null
                        if (entry.canKill) confirmTarget = entry
                    },
                    enabled = entry.canKill
                ) {
                    Text(if (entry.canKill) "结束进程" else "不可结束")
                }
            },
            dismissButton = {
                if (entry.packageName != null) {
                    TextButton(
                        onClick = {
                            actionTarget = null
                            openAppInfo(context, entry.packageName)
                        }
                    ) {
                        Text("应用信息")
                    }
                } else {
                    TextButton(onClick = { actionTarget = null }) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    // 结束进程二次确认
    confirmTarget?.let { entry ->
        AlertDialog(
            visible = true,
            onDismissRequest = { confirmTarget = null },
            title = { Text("结束进程?") },
            text = {
                Text(
                    text = "将强制停止 ${entry.displayName}。\n未保存的数据可能丢失。",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTarget = null
                        onKill(entry)
                    }
                ) {
                    Text("确认结束")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ProcessItemCard(
    entry: ProcessEntry,
    onClick: () -> Unit,
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
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when {
                        entry.versionName.isNotBlank() -> "v${entry.versionName}"
                        entry.isSystem -> "系统"
                        else -> "—"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "内存 ${formatMemory(entry.memoryMb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.US, "CPU %.1f%%", entry.cpuPercent),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatMemory(memoryMb: Float): String = when {
    memoryMb >= 1024f -> String.format(Locale.US, "%.2f GB", memoryMb / 1024f)
    memoryMb >= 100f -> String.format(Locale.US, "%.0f MB", memoryMb)
    else -> String.format(Locale.US, "%.1f MB", memoryMb)
}

private fun openAppInfo(context: Context, packageName: String) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(details) }.isSuccess) return

    // Wear OS 部分版本没有应用详情页，退回系统设置首页
    val fallback = Intent(Settings.ACTION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(fallback) }
}
