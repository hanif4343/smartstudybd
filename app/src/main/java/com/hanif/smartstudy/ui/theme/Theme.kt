package com.hanif.smartstudy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Base palette ──────────────────────────────────────────────
val Indigo600  = Color(0xFF4F46E5)
val Indigo700  = Color(0xFF4338CA)
val Indigo100  = Color(0xFFE0E7FF)
val Green500   = Color(0xFF10B981)
val Teal600    = Color(0xFF0D9488)
val Teal100    = Color(0xFFCCFBF1)
val Amber500   = Color(0xFFF59E0B)
val Amber100   = Color(0xFFFEF3C7)
val Red500     = Color(0xFFEF4444)
val Rose600    = Color(0xFFE11D48)
val Rose100    = Color(0xFFFFE4E6)
val Slate100   = Color(0xFFF1F5F9)
val Slate800   = Color(0xFF1E293B)
val Slate900   = Color(0xFF0F172A)
val White      = Color(0xFFFFFFFF)

// ── Font ────────────────────────────────────────────────────────
val NotoSansBengali = FontFamily.Default

// ── 4 theme definitions ───────────────────────────────────────
enum class AppTheme(val label: String, val emoji: String) {
    INDIGO("ইন্ডিগো", "💜"),
    TEAL("টিল", "🩵"),
    ROSE("রোজ", "❤️"),
    AMBER("অ্যাম্বার", "🧡")
}

fun themeFromString(s: String): AppTheme = when (s.lowercase()) {
    "teal"  -> AppTheme.TEAL
    "rose"  -> AppTheme.ROSE
    "amber" -> AppTheme.AMBER
    else    -> AppTheme.INDIGO
}

private fun lightColors(theme: AppTheme) = when (theme) {
    AppTheme.INDIGO -> lightColorScheme(
        primary = Indigo600, onPrimary = White, primaryContainer = Indigo100,
        secondary = Green500, onSecondary = White, tertiary = Teal600,
        background = Slate100, onBackground = Slate800, surface = White, onSurface = Slate800, error = Red500
    )
    AppTheme.TEAL -> lightColorScheme(
        primary = Teal600, onPrimary = White, primaryContainer = Teal100,
        secondary = Indigo600, onSecondary = White, tertiary = Green500,
        background = Slate100, onBackground = Slate800, surface = White, onSurface = Slate800, error = Red500
    )
    AppTheme.ROSE -> lightColorScheme(
        primary = Rose600, onPrimary = White, primaryContainer = Rose100,
        secondary = Amber500, onSecondary = White, tertiary = Indigo600,
        background = Slate100, onBackground = Slate800, surface = White, onSurface = Slate800, error = Red500
    )
    AppTheme.AMBER -> lightColorScheme(
        primary = Amber500, onPrimary = White, primaryContainer = Amber100,
        secondary = Rose600, onSecondary = White, tertiary = Teal600,
        background = Slate100, onBackground = Slate800, surface = White, onSurface = Slate800, error = Red500
    )
}

private fun darkColors(theme: AppTheme) = when (theme) {
    AppTheme.INDIGO -> darkColorScheme(
        primary = Color(0xFF818CF8), onPrimary = Slate900, primaryContainer = Color(0xFF3730A3),
        secondary = Green500, onSecondary = Slate900, tertiary = Teal600,
        background = Slate900, onBackground = Slate100, surface = Slate800, onSurface = Slate100, error = Red500
    )
    AppTheme.TEAL -> darkColorScheme(
        primary = Color(0xFF2DD4BF), onPrimary = Slate900, primaryContainer = Color(0xFF0F766E),
        secondary = Color(0xFF818CF8), onSecondary = Slate900, tertiary = Green500,
        background = Slate900, onBackground = Slate100, surface = Slate800, onSurface = Slate100, error = Red500
    )
    AppTheme.ROSE -> darkColorScheme(
        primary = Color(0xFFFB7185), onPrimary = Slate900, primaryContainer = Color(0xFF9F1239),
        secondary = Amber500, onSecondary = Slate900, tertiary = Color(0xFF818CF8),
        background = Slate900, onBackground = Slate100, surface = Slate800, onSurface = Slate100, error = Red500
    )
    AppTheme.AMBER -> darkColorScheme(
        primary = Color(0xFFFBBF24), onPrimary = Slate900, primaryContainer = Color(0xFF92400E),
        secondary = Color(0xFFFB7185), onSecondary = Slate900, tertiary = Teal600,
        background = Slate900, onBackground = Slate100, surface = Slate800, onSurface = Slate100, error = Red500
    )
}

// ── CompositionLocals ─────────────────────────────────────────
val LocalDarkMode  = compositionLocalOf { mutableStateOf(false) }
val LocalAppTheme  = compositionLocalOf { mutableStateOf(AppTheme.INDIGO) }

@Composable
fun SmartStudyTheme(
    darkTheme : Boolean  = isSystemInDarkTheme(),
    appTheme  : AppTheme = AppTheme.INDIGO,
    content   : @Composable () -> Unit
) {
    val darkMode     = remember { mutableStateOf(darkTheme) }
    val themeState   = remember { mutableStateOf(appTheme) }
    val colorScheme  = if (darkMode.value) darkColors(themeState.value) else lightColors(themeState.value)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: status bar transparent, content draws behind
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkMode.value
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkMode,
        LocalAppTheme provides themeState
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = SmartStudyTypography,
            content     = content
        )
    }
}
