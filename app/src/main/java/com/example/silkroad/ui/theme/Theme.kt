package com.example.silkroad.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryNavy,
    secondary = SecondaryEarth,
    primaryContainer = ImageBackground,
    onPrimaryContainer = PrimaryNavy,
    secondaryContainer = ImageBackground,
    onSecondaryContainer = TextPrimary,
    surfaceVariant = ImageBackground,
    onSurfaceVariant = TextSecondary,
    background = BackgroundBeige,
    surface = CardWhite,
    onPrimary = CardWhite,
    onSecondary = CardWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed
)

@Composable
fun SilkRoadTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
