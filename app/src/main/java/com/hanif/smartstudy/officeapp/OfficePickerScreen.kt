@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hanif.smartstudy.officeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.officeapp.common.OfficeModule
import com.hanif.smartstudy.ui.theme.NotoSansBengali

/**
 * ══════════════════════════════════════════════════════════════════
 *  OfficePickerScreen — Home-এর "MS Office" টাইলে ট্যাপ করলে এই স্ক্রিন
 *  খোলে। এখান থেকে Word / Excel / PowerPoint বাছাই করলে সংশ্লিষ্ট
 *  module-এর File List স্ক্রিনে (New/Open) চলে যাবে।
 *
 *  Phase 2: শুধু Word সম্পূর্ণ কাজ করবে। Excel/PowerPoint কার্ড দেখা
 *  যাবে কিন্তু "শীঘ্রই আসছে" ব্যাজ দেখাবে (Phase 4/5 এ চালু হবে)।
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun OfficePickerScreen(
    onBack: () -> Unit,
    onSelectModule: (OfficeModule) -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MS Office", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "সরকারি চাকরি পরীক্ষার Office প্র্যাকটিসের জন্য একটি অ্যাপ বাছাই করুন",
                fontFamily = NotoSansBengali,
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )

            OfficeAppCard(
                title = "Word",
                subtitle = "স্মারকপত্র, দরখাস্ত, CV, প্রতিবেদন",
                emoji = "📝",
                gradient = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
                enabled = true,
                onClick = { onSelectModule(OfficeModule.WORD) }
            )
            OfficeAppCard(
                title = "Excel",
                subtitle = "বেতন শীট, ফলাফল শীট, SUM/IF/VLOOKUP",
                emoji = "📊",
                gradient = listOf(Color(0xFF16A34A), Color(0xFF15803D)),
                enabled = true,
                onClick = { onSelectModule(OfficeModule.EXCEL) }
            )
            OfficeAppCard(
                title = "PowerPoint",
                subtitle = "স্লাইড, শেপ, ট্রানজিশন",
                emoji = "📽️",
                gradient = listOf(Color(0xFFEA580C), Color(0xFFC2410C)),
                enabled = true,
                onClick = { onSelectModule(OfficeModule.PPT) }
            )
        }
    }
}

@Composable
private fun OfficeAppCard(
    title: String,
    subtitle: String,
    emoji: String,
    gradient: List<Color>,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled) gradient else listOf(Color(0xFFCBD5E1), Color(0xFFE2E8F0))
                )
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 34.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = NotoSansBengali)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Color.White.copy(alpha = .9f), fontSize = 12.sp, fontFamily = NotoSansBengali)
            }
            if (!enabled) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = .35f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("শীঘ্রই আসছে", color = Color.White, fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
