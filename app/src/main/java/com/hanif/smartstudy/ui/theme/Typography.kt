package com.hanif.smartstudy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// NotoSansBengali is defined in Theme.kt — imported from same package, no re-declaration needed

val SmartStudyTypography = Typography(
    displayLarge   = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,      fontSize = 20.sp),
    titleLarge     = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,      fontSize = 18.sp),
    titleMedium    = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp),
    bodyLarge      = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium,    fontSize = 12.sp),
    labelLarge     = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,      fontSize = 14.sp),
    labelSmall     = TextStyle(fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
)
