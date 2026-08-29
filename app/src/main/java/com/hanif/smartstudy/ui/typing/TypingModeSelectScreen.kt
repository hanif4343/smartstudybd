package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.BijoyCurriculum
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.CurriculumProvider
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TypingHistoryEntry
import com.hanif.smartstudy.util.TypingKeyStatStore

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  TypingModeSelectScreen — রিডিজাইন (দেখো "typing-redesign-demo.html")
 * ═══════════════════════════════════════════════════════════════════════
 *
 * এটাই এখন টাইপিং ফিচারের **হোম স্ক্রিন** — আগে এখানে শুধু ৩টা মোড-কার্ড ছিল,
 * ইউজারকে নিজে বেছে নিতে হতো কোথা থেকে শুরু করবে। এখন Keybr/Duolingo-প্যাটার্নে
 * সবচেয়ে দরকারি কাজটা (Continue) উপরেই বসানো — real কারিকুলাম-স্টেজ, দুর্বল-কী,
 * আর এই-সপ্তাহের গড় পারফরম্যান্স দিয়ে, কোনো প্লেসহোল্ডার/ফেক ডেটা ছাড়াই।
 *
 * ডেটার সোর্স (কোনো নতুন টেবিল/এন্টিটি লাগেনি, সবই আগে থেকে ট্র্যাক হচ্ছিল):
 *   • CurriculumProvider.getCurrentStage/stageFor  → বর্তমান স্টেজ
 *   • TypingKeyStatStore.getWeakest                → সবচেয়ে দুর্বল কী
 *   • SessionManager.getTypingHistory/getStreak/getTypingBestWpm → সাপ্তাহিক স্ট্যাট
 *
 * ফাংশন-সিগনেচার অপরিবর্তিত রাখা হয়েছে (onBack/onSelectNormal/onSelectSmart/
 * onSelectExam/onSelectLegacy) — তাই MainScreen.kt-এ কোনো পরিবর্তন লাগেনি।
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
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }
    var showProgress by remember { mutableStateOf(false) }

    // ── Continue কার্ড + সাপ্তাহিক স্ট্যাটের জন্য real ডেটা ──
    var stage by remember { mutableStateOf(1) }
    val totalStages = remember { BijoyCurriculum.totalStages("bn") }
    var unlockedKeyCount by remember { mutableStateOf(0) }
    var weakestKey by remember { mutableStateOf<String?>(null) }
    var streak by remember { mutableStateOf(0) }
    var bestWpm by remember { mutableStateOf(0) }
    var weekAvgWpm by remember { mutableStateOf(0) }
    var weekAvgAcc by remember { mutableStateOf(0) }
    var weekMinutes by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        stage = CurriculumProvider.getCurrentStage(ctx, "bn")
        unlockedKeyCount = BijoyCurriculum.stagesFor("bn").take(stage).flatten().size
        weakestKey = TypingKeyStatStore.getWeakest(ctx, "bn", minSamples = 10, limit = 1)
            .firstOrNull()?.keyChar
        streak = session.getStreak()
        bestWpm = session.getTypingBestWpm()
        val recent: List<TypingHistoryEntry> = session.getTypingHistory().take(7)
        if (recent.isNotEmpty()) {
            weekAvgWpm = recent.sumOf { it.wpm } / recent.size
            weekAvgAcc = recent.sumOf { it.accuracy } / recent.size
            weekMinutes = recent.sumOf { it.timeSec } / 60
        }
    }

    if (showProgress) {
        TypingProgressScreen(onBack = { showProgress = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🧠 Smart Typing", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text(
                            "🔥 $streak দিন", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            // ══════════ CONTINUE কার্ড ══════════
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF1E1B4B)))
                    )
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "CONTINUE", fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp, color = Color(0xFFC7D2FE)
                        )
                        Text(
                            "চালিয়ে যান", fontSize = 22.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            "স্টেজ $stage / $totalStages", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            color = Color(0xFFC7D2FE), modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            ContinueMeta("গড় WPM", if (weekAvgWpm > 0) "$weekAvgWpm" else "—")
                            ContinueMeta("Accuracy", if (weekAvgAcc > 0) "$weekAvgAcc%" else "—")
                            ContinueMeta("এ সপ্তাহে", if (weekMinutes > 0) "${weekMinutes}m" else "—")
                        }

                        Spacer(Modifier.height(12.dp))

                        weakestKey?.let { wk ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEC4899).copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Box(
                                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEC4899)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(practiceKeyGlyph(wk) ?: wk, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        fontFamily = NotoSansBengali, color = Color.White)
                                }
                                Text(
                                    "“${practiceKeyGlyph(wk) ?: wk}”-তে সবচেয়ে বেশি ভুল হচ্ছে",
                                    fontSize = 11.5.sp, fontFamily = NotoSansBengali, color = Color(0xFFFBCFE8)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        Button(
                            onClick = onSelectSmart,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text("▶ প্র্যাকটিস শুরু করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // ══════════ প্র্যাকটিস মোড গ্রিড ══════════
            SectionLabel("প্র্যাকটিস মোড")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeTile(
                        Modifier.weight(1f), icon = "🎯", title = "কারিকুলাম",
                        desc = "ধাপে ধাপে নতুন কী আনলক করুন", onClick = onSelectSmart
                    )
                    ModeTile(
                        Modifier.weight(1f), icon = "🩹", title = "দুর্বল-কী ড্রিল",
                        desc = "যেসব কী-তে ভুল বেশি, শুধু সেগুলো", onClick = onSelectSmart
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeTile(
                        Modifier.weight(1f), icon = "🌿", title = "ফ্রি টাইপিং",
                        desc = "চাপ ছাড়া, নিজের গতিতে", onClick = onSelectNormal
                    )
                    ModeTile(
                        Modifier.weight(1f), icon = "🏁", title = "এক্সাম সিমুলেশন",
                        desc = "সরকারি পরীক্ষার মতো টাইমড টেস্ট", onClick = onSelectExam
                    )
                }
            }

            // ══════════ এই সপ্তাহ ══════════
            SectionLabel("এই সপ্তাহ")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatMini(Modifier.weight(1f), value = if (bestWpm > 0) "$bestWpm" else "—", label = "সেরা WPM")
                StatMini(Modifier.weight(1f), value = if (weekAvgAcc > 0) "$weekAvgAcc%" else "—", label = "গড় Accuracy")
                StatMini(Modifier.weight(1f), value = "$unlockedKeyCount", label = "কী আনলক")
            }

            // ══════════ প্রোগ্রেস লিংক ══════════
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { showProgress = true }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("📈 বিস্তারিত প্রোগ্রেস দেখুন", fontSize = 13.5.sp, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("→", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                onClick = onSelectLegacy
            ) {
                Text(
                    "⚙️ সম্পূর্ণ ফিচার (পুরনো) স্ক্রিন", fontSize = 11.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ContinueMeta(label: String, value: String) {
    Column {
        Text(value, fontSize = 16.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, fontSize = 10.5.sp, fontFamily = NotoSansBengali, color = Color(0xFFA5B4FC))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 11.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ModeTile(modifier: Modifier = Modifier, icon: String, title: String, desc: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, fontSize = 20.sp)
            Text(title, fontSize = 13.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 10.5.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun StatMini(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
            Text(label, fontSize = 10.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
