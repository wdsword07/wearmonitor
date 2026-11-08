package com.github.wdsword07.wearmonitor.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun WearmonitorTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColors = dynamicColorScheme(context)

    MaterialTheme(
        colorScheme = dynamicColors ?: ColorScheme(),
        content = content
    )
}