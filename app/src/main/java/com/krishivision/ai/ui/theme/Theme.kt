package com.krishivision.ai.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val KrishiDarkColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = PrimaryText,
    primaryContainer = SecondarySurface,
    onPrimaryContainer = PrimaryText,
    secondary = GoldAccent,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = PrimaryText,
    surface = CardSurface,
    onSurface = PrimaryText,
    surfaceVariant = SecondarySurface,
    onSurfaceVariant = SecondaryText,
    outline = BorderOutline
)

@Composable
fun KrishiVisionAITheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = KrishiDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}