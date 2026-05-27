package com.pindownloader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Grey100,
    primaryContainer = Color(0xFFEDE7F6),
    onPrimaryContainer = Purple10,
    secondary = Blue40,
    onSecondary = Grey100,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Blue10,
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Grey100,
    tertiaryContainer = Color(0xFFF3E5F5),
    onTertiaryContainer = Color(0xFF311B92),
    background = Grey98,
    onBackground = Grey10,
    surface = Grey100,
    onSurface = Grey10,
    surfaceVariant = Grey95,
    onSurfaceVariant = Grey50,
    surfaceTint = Purple40,
    outline = Grey70,
    outlineVariant = Grey90,
    error = Error,
    onError = Grey100,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),
    inverseSurface = Grey20,
    inverseOnSurface = Grey95,
    inversePrimary = Purple60,
    scrim = Color.Black.copy(alpha = 0.32f)
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple60,
    onPrimary = Purple10,
    primaryContainer = Color(0xFF381E72),
    onPrimaryContainer = Color(0xFFD0BCFF),
    secondary = Blue60,
    onSecondary = Blue10,
    secondaryContainer = Color(0xFF0D47A1),
    onSecondaryContainer = Blue80,
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF311B92),
    tertiaryContainer = Color(0xFF4A148C),
    onTertiaryContainer = Color(0xFFF3E5F5),
    background = Grey10,
    onBackground = Grey90,
    surface = Grey15,
    onSurface = Grey90,
    surfaceVariant = Grey20,
    onSurfaceVariant = Grey70,
    surfaceTint = Purple60,
    outline = Grey50,
    outlineVariant = Grey30,
    error = Error,
    onError = Grey10,
    errorContainer = Color(0xFF4A0000),
    onErrorContainer = Color(0xFFFFCDD2),
    inverseSurface = Grey90,
    inverseOnSurface = Grey10,
    inversePrimary = Purple40,
    scrim = Color.Black.copy(alpha = 0.48f)
)

@Composable
fun PinDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
