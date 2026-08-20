package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  TypingModeSelectScreen — পর্ব ৩/৫.৩ — ধাপ ৩ (মোড-সেপারেশনের চূড়ান্ত ধাপ)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * এখন পর্যন্ত ইউজারকে টাইপিং-এ ঢুকতে হলে প্রথমে পুরনো `TypingPracticeScreen.kt`
 * (মোড-সিলেক্টর এক্সপ্যান্ড করে ভেতরের বাটন থেকে Normal/Smart-এ যাওয়া) দিয়েই
 * ঘুরে যেতে হতো — এটাই ছিল "ঘুরিয়ে নেওয়া" পথ, সরাসরি না। এই স্ক্রিনটা এখন
 * থেকে **সরাসরি এন্ট্রি-পয়েন্ট** — মোড-সিলেক্টর প্রথমেই দেখাবে।
 *
 * চতুর্থ, ছোট বাটন "⚙️ সম্পূর্ণ ফিচার (পুরনো) স্ক্রিন" ইচ্ছাকৃতভাবে রাখা হয়েছে —
 * Exam Mode-এ AI Adaptive Session/curriculum-এর কিছু niche ফিচার (যেমন Study
 * Typing/Focus-Mode ইন্টিগ্রেশন) এখনো শুধু পুরনো ফাইলেই আছে, তাই এটা এখনই পুরোপুরি
 * সরিয়ে দেওয়া হয়নি — একটা নিরাপদ fallback হিসেবে রাখা হলো।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingModeSelectScreen(
    onBack: () -> Unit,
    onSelectNormal: () -> Unit,
    onSelectSmart: () -> Unit,
    onSelectExam: () -> Unit,
    onSelectLegacy: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⌨️ Typing Practice", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "কোন মোডে প্র্যাকটিস করবে?", fontSize = 15.sp, fontFamily = NotoSansBengali,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ModeCard(
                icon = "⌨️", title = "Normal Typing", subtitle = "সহজ, পরিষ্কার — সাধারণ প্র্যাকটিস, Quick-3",
                color = Color(0xFF059669), onClick = onSelectNormal
            )
            ModeCard(
                icon = "🧠", title = "Smart Typing", subtitle = "Adaptive Key-Unlock, দুর্বল-কী/জুটি ড্রিল, AI Adaptive Session",
                color = Color(0xFF7C3AED), onClick = onSelectSmart
            )
            ModeCard(
                icon = "🏛️", title = "Exam / Govt Mock", subtitle = "BCC Exam Simulation, Govt Job মক টেস্ট",
                color = Color(0xFF1D4ED8), onClick = onSelectExam
            )

            Spacer(Modifier.height(6.dp))
            Text(
                "নিচের অপশনটাতে কিছু পুরনো/অতিরিক্ত ফিচার (Study Typing, Focus-Mode ইন্টিগ্রেশন) এখনো আছে —",
                fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onSelectLegacy
            ) {
                Text(
                    "⚙️ সম্পূর্ণ ফিচার (পুরনো) স্ক্রিন", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeCard(icon: String, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        color = color,
        onClick = onClick
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$icon $title", fontSize = 17.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, fontFamily = NotoSansBengali, color = Color.White.copy(alpha = 0.85f))
        }
    }
}
