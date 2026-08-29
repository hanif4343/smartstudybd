package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.BijoyCurriculum
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.CurriculumProvider
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TypingHistoryEntry
import java.util.Calendar

/**
 * প্রোগ্রেস ড্যাশবোর্ড — "typing-redesign-demo.html"-এর ৪ নম্বর স্ক্রিন।
 * সম্পূর্ণ real, আগে থেকেই persist হওয়া ডেটা দিয়ে বানানো, কোনো নতুন
 * টেবিল/এন্টিটি লাগেনি:
 *   • WPM ট্রেন্ড      → SessionManager.getTypingHistory() (শেষ ১৫ সেশন)
 *   • স্ট্রিক ক্যালেন্ডার → SessionManager.getDailyPracticeMinutes()
 *   • লেভেল রিং        → CurriculumProvider.getCurrentStage("bn") / totalStages
 *   • ব্যাজ            → bestWpm + streak থ্রেশহোল্ড (TypingProfileDialog-এর
 *                        অ্যাচিভমেন্ট-লজিকের সাথে সঙ্গতিপূর্ণ রাখা হয়েছে)
 */
@Composable
fun TypingProgressScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }

    var stage by remember { mutableStateOf(1) }
    val totalStages = remember { BijoyCurriculum.totalStages("bn") }
    var bestWpm by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf(listOf<TypingHistoryEntry>()) }
    var dailyMinutes by remember { mutableStateOf(mapOf<String, Int>()) }

    LaunchedEffect(Unit) {
        stage = CurriculumProvider.getCurrentStage(ctx, "bn")
        bestWpm = session.getTypingBestWpm()
        streak = session.getStreak()
        history = session.getTypingHistory()          // newest-first
        dailyMinutes = session.getDailyPracticeMinutes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📈 প্রোগ্রেস", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ══════════ লেভেল কার্ড ══════════
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LevelRing(progress = stage.toFloat() / totalStages.toFloat(), label = "$stage")
                    Column {
                        Text("স্টেজ $stage / $totalStages", fontSize = 14.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                        val remain = (totalStages - stage).coerceAtLeast(0)
                        Text(
                            if (remain > 0) "সর্বশেষ স্টেজে যেতে আর $remain টা বাকি" else "সব স্টেজ সম্পূর্ণ! 🎉",
                            fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ══════════ WPM ট্রেন্ড ══════════
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("WPM ট্রেন্ড (সাম্প্রতিক সেশন)", fontSize = 12.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                        Text("সেরা $bestWpm", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                    Spacer(Modifier.height(10.dp))
                    if (history.size < 2) {
                        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "অন্তত ২টা সেশন শেষ হলে ট্রেন্ড দেখাবে — আরেকটু প্র্যাকটিস করো!",
                                fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        WpmTrendChart(history.reversed())   // chronological (পুরনো → নতুন)
                    }
                }
            }

            // ══════════ ব্যাজ ══════════
            SectionLabel2("ব্যাজ")
            val badges = listOf(
                Triple("🥉", "১৫+ WPM", bestWpm >= 15),
                Triple("🥈", "৩০+ WPM", bestWpm >= 30),
                Triple("🥇", "৫০+ WPM", bestWpm >= 50),
                Triple("🔥", "৭ দিন স্ট্রিক", streak >= 7)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                badges.take(2).forEach { (emoji, label, unlocked) ->
                    BadgePill(Modifier.weight(1f), emoji, label, unlocked)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                badges.drop(2).forEach { (emoji, label, unlocked) ->
                    BadgePill(Modifier.weight(1f), emoji, label, unlocked)
                }
            }

            // ══════════ প্র্যাকটিস ক্যালেন্ডার ══════════
            SectionLabel2("এই মাসের প্র্যাকটিস")
            PracticeCalendar(dailyMinutes)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel2(text: String) {
    Text(text, fontSize = 11.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LevelRing(progress: Float, label: String) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            drawArc(
                color = Color(0xFF6366F1).copy(alpha = 0.2f), startAngle = -90f, sweepAngle = 360f,
                useCenter = false, style = Stroke(stroke)
            )
            drawArc(
                color = Color(0xFF6366F1), startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false, style = Stroke(stroke)
            )
        }
        Text(label, fontSize = 14.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun BadgePill(modifier: Modifier = Modifier, emoji: String, label: String, unlocked: Boolean) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        color = if (unlocked) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (unlocked) emoji else "🔒", fontSize = 20.sp)
            Text(
                label, fontSize = 10.sp, fontFamily = NotoSansBengali, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun WpmTrendChart(chronological: List<TypingHistoryEntry>) {
    val maxWpm = (chronological.maxOfOrNull { it.wpm } ?: 1).coerceAtLeast(1)
    val lineColor = Color(0xFF6366F1)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val w = size.width
        val h = size.height
        val stepX = if (chronological.size > 1) w / (chronological.size - 1) else w
        val path = Path()
        chronological.forEachIndexed { i, entry ->
            val x = stepX * i
            val y = h - (entry.wpm.toFloat() / maxWpm) * h * 0.85f - 6f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        chronological.forEachIndexed { i, entry ->
            val x = stepX * i
            val y = h - (entry.wpm.toFloat() / maxWpm) * h * 0.85f - 6f
            drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
        }
    }
}

@Composable
private fun PracticeCalendar(dailyMinutes: Map<String, Int>) {
    val cal = remember { Calendar.getInstance() }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOffset = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.get(Calendar.DAY_OF_WEEK) - 1   // ০ = রবিবার
    }
    val today = cal.get(Calendar.DAY_OF_MONTH)

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(14.dp)) {
            val cells = (0 until firstDayOffset).map { -1 } + (1..daysInMonth).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { day ->
                        val minutes = if (day > 0) dailyMinutes["$year-$month-$day"] ?: 0 else 0
                        val bg = when {
                            day <= 0 -> Color.Transparent
                            minutes >= 20 -> Color(0xFF10B981)
                            minutes >= 5 -> Color(0xFF10B981).copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day > 0) {
                                Text(
                                    "$day", fontSize = 9.sp, fontFamily = NotoSansBengali,
                                    fontWeight = if (day == today) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (minutes >= 5) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}
