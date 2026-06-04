package com.hanif.smartstudy.ui.menu.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.WeakTopic
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.MenuUiState

private val Indigo600 = Color(0xFF4F46E5)
private val GreenOk   = Color(0xFF10B981)
private val RedWrong  = Color(0xFFEF4444)
private val AmberWarn = Color(0xFFF59E0B)
private val SlateText = Color(0xFF1E293B)
private val MutedText = Color(0xFF64748B)
private val CardBg    = Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPage(state: MenuUiState, onBack: () -> Unit) {
    val total    = state.totalCorrect + state.totalWrong
    val accPct   = if (total > 0) (state.totalCorrect * 100) / total else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("পরিসংখ্যান", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Accuracy ring ──
            AccuracyRingCard(
                correct  = state.totalCorrect,
                wrong    = state.totalWrong,
                accPct   = accPct
            )

            // ── Study time card ──
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(Color(0xFF1E1B4B))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⏱️ পড়ার সময়", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ফিক্সড: MenuUiState এর প্রোপার্টির সাথে ম্যাচিং ফিক্স (প্রয়োজনে আপনার স্টেট অনুযায়ী variable মডিফাই করতে পারবেন)
                        val todayMin = state.todayStudyMin
                        val weekMin = state.weekStudyMin
                        val totalMin = state.totalStudyMin

                        val todayStr = if (todayMin < 60) "$todayMinমি" else "${todayMin / 60}ঘ ${todayMin % 60}মি"
                        val weekStr = if (weekMin < 60) "$weekMinমি" else "${weekMin / 60}ঘ ${weekMin % 60}মি"
                        val totalStr = if (totalMin < 60) "$totalMinমি" else "${totalMin / 60}ঘ ${totalMin % 60}মি"

                        TimeStatBox("আজ",    todayStr, Color(0xFF4ADE80), Modifier.weight(1f))
                        TimeStatBox("সপ্তাহ", weekStr,  Color(0xFFA78BFA), Modifier.weight(1f))
                        TimeStatBox("মোট",   totalStr, Color(0xFFFBBF24), Modifier.weight(1f))
                    }
                }
            }

            // ── XP history chart ──
            if (state.xpHistory.isNotEmpty()) {
                XpChartCard(history = state.xpHistory)
            } else {
                XpPlaceholderCard(xp = state.user?.xp ?: 0)
            }

            // ── Subject breakdown ──
            if (state.subjectStats.isNotEmpty()) {
                SubjectBreakdownCard(stats = state.subjectStats)
            }

            // ── দুর্বল টপিক ──
            if (state.weakTopics.isNotEmpty()) {
                WeakTopicsCard(weakTopics = state.weakTopics)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AccuracyRingCard(correct: Int, wrong: Int, accPct: Int) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val animPct by animateFloatAsState(
                accPct / 100f,
                tween(1000),
                label = "acc"
            )
            Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(12f, cap = StrokeCap.Round)
                    drawArc(Color(0xFFE2E8F0), -90f, 360f, false, style = stroke)
                    drawArc(
                        Brush.sweepGradient(listOf(Indigo600, Color(0xFF818CF8))),
                        -90f, 360f * animPct, false, style = stroke
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$accPct%", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = SlateText, fontFamily = NotoSansBengali)
                    Text("নির্ভুল", fontSize = 8.sp, color = MutedText, fontFamily = NotoSansBengali)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccRow("✅ সঠিক", correct, GreenOk)
                AccRow("❌ ভুল",  wrong,   RedWrong)
                AccRow("📝 মোট",  correct + wrong, Indigo600)
            }
        }
    }
}

@Composable
private fun AccRow(label: String, value: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = SlateText, fontFamily = NotoSansBengali)
        Text(value.toString(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            color = color, fontFamily = NotoSansBengali)
    }
}

@Composable
private fun TimeStatBox(label: String, value: String, vc: Color, mod: Modifier) {
    Column(
        mod.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            color = vc, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

@Composable
private fun XpChartCard(history: List<Pair<String, Int>>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("⭐ XP ইতিহাস", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = SlateText, fontFamily = NotoSansBengali, modifier = Modifier.padding(bottom = 12.dp))
            val max = history.maxOf { it.second }.coerceAtLeast(1).toFloat()
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val pts = history.mapIndexed { i, (_, xp) ->
                        Offset(
                            x = i * (size.width / (history.size - 1).coerceAtLeast(1)),
                            y = size.height - (xp / max) * size.height
                        )
                    }
                    for (i in 0 until pts.size - 1) {
                        drawLine(Indigo600, pts[i], pts[i + 1], strokeWidth = 3f)
                    }
                    pts.forEach { pt ->
                        drawCircle(Indigo600, 5f, pt)
                        drawCircle(Color.White, 3f, pt)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                history.takeLast(7).forEach { (label, _) ->
                    Text(label, fontSize = 8.sp, color = MutedText, fontFamily = NotoSansBengali)
                }
            }
        }
    }
}

@Composable
private fun XpPlaceholderCard(xp: Int) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color(0xFFF5F3FF)),
        border = BorderStroke(1.dp, Color(0xFFDDD6FE))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⭐", fontSize = 32.sp)
            Column {
                Text("মোট XP: $xp", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    color = Indigo600, fontFamily = NotoSansBengali)
                Text("আরো Quiz দিলে XP ইতিহাস চার্ট দেখাবে",
                    fontSize = 11.sp, color = MutedText, fontFamily = NotoSansBengali)
            }
        }
    }
}

@Composable
private fun SubjectBreakdownCard(stats: Map<String, Pair<Int, Int>>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📊 বিষয় ভিত্তিক ফলাফল", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = SlateText, fontFamily = NotoSansBengali)
            stats.entries.forEach { (subject, pair) ->
                val (correct, total) = pair
                val pct = if (total > 0) correct * 100 / total else 0
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(subject, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = SlateText, fontFamily = NotoSansBengali)
                        Text("$correct/$total ($pct%)", fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold, color = Indigo600,
                            fontFamily = NotoSansBengali)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(20.dp)).background(Color(0xFFE2E8F0))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(pct / 100f).fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(
                                        if (pct >= 60) GreenOk else if (pct >= 40) AmberWarn else RedWrong,
                                        Indigo600
                                    ))
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeakTopicsCard(weakTopics: List<WeakTopic>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color(0xFFFFF1F2)),
        border = BorderStroke(1.5.dp, Color(0xFFFCA5A5))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🔁", fontSize = 16.sp)
                Text("দুর্বল টপিক", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF9F1239), fontFamily = NotoSansBengali)
                Text("(${weakTopics.size}টি)", fontSize = 11.sp, color = RedWrong,
                    fontFamily = NotoSansBengali)
            }
            Text(
                "এই টপিকগুলোতে বারবার ভুল হয়েছে — আরো Practice করো",
                fontSize = 11.sp, color = Color(0xFFBE123C), fontFamily = NotoSansBengali
            )
            weakTopics.forEach { w ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFE4E6), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                                .background(RedWrong.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("❌", fontSize = 13.sp) }
                        Text(
                            w.subTopic, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF9F1239), fontFamily = NotoSansBengali
                        )
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(RedWrong.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${w.wrongCount}× ভুল", fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold, color = RedWrong,
                            fontFamily = NotoSansBengali
                        )
                    }
                }
            }
        }
    }
}
