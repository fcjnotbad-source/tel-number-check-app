package com.fcj.telnumbercheck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = lightColorScheme(
    primary = Color(0xFF176B61),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F4EC),
    onPrimaryContainer = Color(0xFF082F2A),
    secondary = Color(0xFF506561),
    background = Color(0xFFF6F8F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EFEC),
    error = Color(0xFFB42318),
)

@Composable
fun TelNumberCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content,
    )
}
