package com.hanif.smartstudy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Indigo600  = Color(0xFF4F46E5)
val Indigo700  = Color(0xFF4338CA)
val Indigo100  = Color(0xFFE0E7FF)
val Green500   = Color(0xFF10B981)
val Teal600    = Color(0xFF0D9488)
val Amber500   = Color(0xFFF59E0B)
val Red500     = Color(0xFFEF4444)
val Slate100   = Color(0xFFF1F5F9)
val Slate800   = Color(0xFF1E293B)
val Slate900   = Color(0xFF0F172A)
val White      = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary          = Indigo600,
    onPrimary        = White,
    primaryContainer = Indigo100,
    secondary        = Green500,
    onSecondary      = White,
    tertiary         = Teal600,
    background       = Slate100,
    onBackground     = Slate800,
    surface          = White,
    onSurface        = Slate800,
    error            = Red500,
)

private val DarkColors = darkColorScheme(
    primary          = Indigo600,
    onPrimary        = White,
    primaryContainer = Color(0xFF3730A3),
    secondary        = Green500,
    onSecondary      = White,
    tertiary         = Teal600,
    background       = Slate900,
    onBackground     = Slate100,
    surface          = Slate800,
    onSurface        = Slate100,
    error            = Red500,
)

val LocalDarkMode = compositionLocalOf { mutableStateOf(false) }

@Composable
fun SmartStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val darkMode = remember { mutableStateOf(darkTheme) }
    val colorScheme = if (darkMode.value) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !darkMode.value
        }
    }

    CompositionLocalProvider(LocalDarkMode provides darkMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = SmartStudyTypography,
            content     = content
        )
    }
}
