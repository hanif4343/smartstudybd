package com.hanif.smartstudy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontFamily
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

// ── Nordic Pastel palette ───────────────────────────────────────
// Scandinavian-inspired: মিউটেড sage green, dusty blue, warm cream,
// soft terracotta accent — কোনো harsh/vivid রং নেই, সব "washed out" পেস্টেল টোন।
val NordicSage      = Color(0xFF7C9885)   // primary — dusty sage green
val NordicSageDeep  = Color(0xFF5F7A68)   // darker sage (dark-mode primary base)
val NordicSageTint  = Color(0xFFE4EDE6)   // primaryContainer — pale sage wash
val NordicBlue       = Color(0xFF7E97B3)  // secondary — dusty/slate blue
val NordicBlueTint   = Color(0xFFE9EEF4)  // ব্যাখ্যা বক্সের ফিল
val NordicClay       = Color(0xFFCE8E63)  // tertiary — warm terracotta/clay
val NordicClayTint   = Color(0xFFF7E9DA)  // মনে রাখার টেকনিক বক্সের ফিল
val NordicCream      = Color(0xFFF7F5F0)  // background — warm off-white
val NordicSurface    = Color(0xFFFFFFFF)
val NordicInk        = Color(0xFF3B3F3A)  // onBackground/onSurface — সফট চারকোল (pure black নয়)
val NordicMuted      = Color(0xFF7A8177)  // secondary text
val NordicRose       = Color(0xFFC97B7B)  // মিউটেড error/wrong (পেস্টেল, harsh red নয়)
val NordicDarkBg     = Color(0xFF23272A)  // dark-mode background — cool charcoal
val NordicDarkSurf   = Color(0xFF2C3130)  // dark-mode surface

// ── Font ──────────────────────────────────────────────────────
val NotoSansBengali = FontFamily.Default

// ── 5 theme definitions ───────────────────────────────────────
enum class AppTheme(val label: String, val emoji: String) {
    INDIGO("ইন্ডিগো", "💜"),
    TEAL("টিল", "🩵"),
    ROSE("রোজ", "❤️"),
    AMBER("অ্যাম্বার", "🧡"),
    NORDIC("নর্ডিক প্যাস্টেল", "🌿")
}

fun themeFromString(s: String): AppTheme = when (s.lowercase()) {
    "teal"   -> AppTheme.TEAL
    "rose"   -> AppTheme.ROSE
    "amber"  -> AppTheme.AMBER
    "nordic" -> AppTheme.NORDIC
    else     -> AppTheme.INDIGO
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
    // ── Nordic Pastel — কোমল, ওয়াশড-আউট Scandinavian টোন ──
    AppTheme.NORDIC -> lightColorScheme(
        primary = NordicSage, onPrimary = White, primaryContainer = NordicSageTint,
        secondary = NordicBlue, onSecondary = White, tertiary = NordicClay,
        background = NordicCream, onBackground = NordicInk,
        surface = NordicSurface, onSurface = NordicInk,
        surfaceVariant = NordicSageTint, onSurfaceVariant = NordicMuted,
        error = NordicRose
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
    // ── Nordic Pastel (dark) — sage কিছুটা হালকা করা হয়েছে যাতে ডার্ক ব্যাকগ্রাউন্ডে ভেসে থাকে ──
    AppTheme.NORDIC -> darkColorScheme(
        primary = Color(0xFF9DBBA5), onPrimary = NordicDarkBg, primaryContainer = NordicSageDeep,
        secondary = Color(0xFFA9BDD3), onSecondary = NordicDarkBg, tertiary = Color(0xFFDFA679),
        background = NordicDarkBg, onBackground = Color(0xFFE7E7E2),
        surface = NordicDarkSurf, onSurface = Color(0xFFE7E7E2),
        surfaceVariant = Color(0xFF343A37), onSurfaceVariant = Color(0xFFB7BEB4),
        error = Color(0xFFD99B9B)
    )
}

// ── CompositionLocals ─────────────────────────────────────────
val LocalDarkMode  = compositionLocalOf { mutableStateOf(false) }
val LocalAppTheme  = compositionLocalOf { mutableStateOf(AppTheme.INDIGO) }

// uiScale: 1.0f = normal সাইজ।
// বাবার phone এ যদি system font/display বড় করা থাকে,
// তাহলে এই value 1.0f এর নিচে দিলে পুরো app ছোট হয়ে যাবে।
// (যেমন 0.75f মানে তিন-ভাগের দুই ভাগ, 0.6f মানে আরো ছোট)
val LocalUiScale = compositionLocalOf { mutableStateOf(1.0f) }

@Composable
fun SmartStudyTheme(
    darkTheme : Boolean  = isSystemInDarkTheme(),
    appTheme  : AppTheme = AppTheme.INDIGO,
    uiScale   : Float    = 1.0f,   // <-- এটাই পুরো app ছোট/বড় করে
    content   : @Composable () -> Unit
) {
    val darkMode     = remember { mutableStateOf(darkTheme) }
    val themeState   = remember { mutableStateOf(appTheme) }
    val uiScaleState = remember { mutableStateOf(uiScale) }
    val colorScheme  = if (darkMode.value) darkColors(themeState.value) else lightColors(themeState.value)

    LaunchedEffect(darkTheme) { darkMode.value = darkTheme }
    LaunchedEffect(uiScale)   { uiScaleState.value = uiScale }

    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkMode.value
        }
    }

    // ── KEY: LocalDensity override ───────────────────────────
    // এটাই পুরো UI কে scale করে — dp, sp, padding, icon, button সব।
    // System এর original density নিয়ে সেটাকে uiScale দিয়ে multiply করো।
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(uiScaleState.value, baseDensity) {
        Density(
            density    = baseDensity.density    * uiScaleState.value,
            fontScale  = baseDensity.fontScale  * uiScaleState.value
        )
    }

    CompositionLocalProvider(
        LocalDarkMode  provides darkMode,
        LocalAppTheme  provides themeState,
        LocalUiScale   provides uiScaleState,
        LocalDensity   provides scaledDensity        // <-- পুরো app scale হয়
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = SmartStudyTypography,
            content     = content
        )
    }
}
