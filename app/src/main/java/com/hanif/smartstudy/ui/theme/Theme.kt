package com.hanif.smartstudy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hanif.smartstudy.R
import com.hanif.smartstudy.viewmodel.AppTheme

val NotoSansBengali = FontFamily(
    Font(R.font.noto_sans_bengali_regular, FontWeight.Normal),
    Font(R.font.noto_sans_bengali_bold,    FontWeight.Bold),
    Font(R.font.noto_sans_bengali_black,   FontWeight.ExtraBold)
)

// ── Theme color palettes ──
private fun indigoScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary   = Color(0xFF818CF8), onPrimary = Color(0xFF1E1B4B),
    background = Color(0xFF0F0F1A), surface = Color(0xFF1A1A2E),
    onBackground = Color(0xFFE2E8F0), onSurface = Color(0xFFE2E8F0)
) else lightColorScheme(
    primary   = Color(0xFF4F46E5), onPrimary = Color.White,
    background = Color(0xFFF8FAFC), surface = Color.White,
    onBackground = Color(0xFF1E293B), onSurface = Color(0xFF1E293B)
)

private fun tealScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary   = Color(0xFF2DD4BF), onPrimary = Color(0xFF003733),
    background = Color(0xFF0F1A18), surface = Color(0xFF1A2E2B)
) else lightColorScheme(
    primary   = Color(0xFF0D9488), onPrimary = Color.White,
    background = Color(0xFFF0FDFA), surface = Color.White
)

private fun roseScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary   = Color(0xFFFB7185), onPrimary = Color(0xFF4C0519),
    background = Color(0xFF1A0F12), surface = Color(0xFF2E1A1E)
) else lightColorScheme(
    primary   = Color(0xFFE11D48), onPrimary = Color.White,
    background = Color(0xFFFFF1F2), surface = Color.White
)

private fun amberScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary   = Color(0xFFFBBF24), onPrimary = Color(0xFF451A03),
    background = Color(0xFF1A1408), surface = Color(0xFF2E2410)
) else lightColorScheme(
    primary   = Color(0xFFD97706), onPrimary = Color.White,
    background = Color(0xFFFFFBEB), surface = Color.White
)

@Composable
fun SmartStudyTheme(
    darkTheme : Boolean  = isSystemInDarkTheme(),
    appTheme  : AppTheme = AppTheme.INDIGO,
    content   : @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.INDIGO -> indigoScheme(darkTheme)
        AppTheme.TEAL   -> tealScheme(darkTheme)
        AppTheme.ROSE   -> roseScheme(darkTheme)
        AppTheme.AMBER  -> amberScheme(darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(
            bodyLarge  = MaterialTheme.typography.bodyLarge.copy(fontFamily  = NotoSansBengali),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = NotoSansBengali),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = NotoSansBengali),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = NotoSansBengali)
        ),
        content = content
    )
}
