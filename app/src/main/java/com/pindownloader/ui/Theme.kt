package com.pindownloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PinColorScheme = lightColorScheme(
    primary = Color(0xFFE60023),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCBBC3),
    secondary = Color(0xFF767676),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    error = Color(0xFFB00020)
)

@Composable
fun PinDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PinColorScheme,
        content = content
    )
}
