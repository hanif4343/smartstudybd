package com.hanif.smartstudy.ui.home

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.local.RoutineGoal
import com.hanif.smartstudy.data.model.DailyRoutine
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import java.util.Calendar

// ── Smart Routine-এর মাথা: লক্ষ্য + পরীক্ষার কাউন্টডাউন + আজকের গোল রিং + আজকের পড়ার সময় ──

private val Teal    = Color(0xFF0F766E)
private val TealBg  = Color(0xFFE6F6F3)
private val Indigo  = Color(0xFF4F46E5)

private val EXAMS = listOf(
    "BCS (প্রিলি + লিখিত)", "প্রাথমিক শিক্ষক নিয়োগ", "ব্যাংক নিয়োগ",
    "NTRCA / শিক্ষক নিবন্ধন", "অফিস সহায়ক / ১০ম-১৪তম গ্রেড", "অন্যান্য সরকারি চাকরি"
)
private val DAILY_OPTIONS = listOf(60, 90, 120, 180, 240, 300, 360)
// (Calendar.DAY_OF_WEEK, নাম)
private val OFF_DAYS = listOf(0 to "ছুটি নেই", 6 to "শুক্রবার", 7 to "শনিবার", 1 to "রবিবার")

fun minutesLabel(m: Int): String = when {
    m % 60 == 0 -> "${m / 60} ঘণ্টা"
    m > 60      -> "${m / 60} ঘণ্টা ${m % 60} মিনিট"
    else        -> "$m মিনিট"
}

@Composable
fun SmartRoutineHeader(
    goal: RoutineGoal,
    routine: DailyRoutine,
    onSaveGoal: (RoutineGoal) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val daysLeft = remember(goal.examDate) { goal.daysLeft() }
    val doneMinutes = routine.items.filter { it.done }.sumOf { it.minutes }
    val offToday = goal.isOffToday()

    if (showDialog) GoalDialog(goal, onDismiss = { showDialog = false }, onSave = { onSaveGoal(it); showDialog = false })

    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TealBg)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(done = routine.doneCount, total = routine.totalCount, modifier = Modifier.size(86.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (goal.isSet) {
                        Text("🎯 ${goal.exam}", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp, color = Color(0xFF134E4A))
                        Text(
                            when {
                                daysLeft == null && goal.examDate.isBlank() -> "পরীক্ষার তারিখ সেট করা হয়নি"
                                daysLeft == null -> "পরীক্ষার তারিখ পেরিয়ে গেছে — নতুন তারিখ দিন"
                                daysLeft == 0 -> "⏰ পরীক্ষা আজ!"
                                else -> "⏳ পরীক্ষার আর $daysLeft দিন বাকি"
                            },
                            fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (daysLeft != null && daysLeft <= 30) Color(0xFFB91C1C) else Teal,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            if (offToday) "😌 আজ আপনার সাপ্তাহিক ছুটির দিন"
                            else "আজ পড়া: ${minutesLabel(doneMinutes)} / ${minutesLabel(goal.dailyMinutes)}",
                            fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF334155),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text("লক্ষ্য সেট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp, color = Color(0xFF134E4A))
                        Text("পরীক্ষা, তারিখ আর দৈনিক পড়ার সময় দিলে প্ল্যান আপনার মতো করে তৈরি হবে",
                            fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF334155),
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            if (goal.isSet && !offToday && goal.dailyMinutes > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress   = { (doneMinutes.toFloat() / goal.dailyMinutes).coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                    color      = Teal, trackColor = Color.White
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick  = { showDialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(if (goal.isSet) "🎯 লক্ষ্য বদলান" else "🎯 লক্ষ্য সেট করুন",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Teal)
            }
        }
    }
}

@Composable
private fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier) {
    val frac = if (total == 0) 0f else done.toFloat() / total
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            drawArc(Teal, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$total", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp, color = Color(0xFF134E4A))
            Text("সম্পন্ন", fontFamily = NotoSansBengali, fontSize = 10.sp, color = Color(0xFF475569))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(goal: RoutineGoal, onDismiss: () -> Unit, onSave: (RoutineGoal) -> Unit) {
    val context = LocalContext.current
    var exam    by remember { mutableStateOf(goal.exam) }
    var date    by remember { mutableStateOf(goal.examDate) }
    var minutes by remember { mutableStateOf(goal.dailyMinutes) }
    var off     by remember { mutableStateOf(goal.offDay) }

    fun pickDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d -> date = "%04d-%02d-%02d".format(y, m + 1, d) },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    @Composable
    fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                .background(if (selected) Teal else Color(0xFFF1F5F9))
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color(0xFF334155))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎯 আমার লক্ষ্য", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("কোন পরীক্ষার প্রস্তুতি?", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXAMS.forEach { e -> Chip(e, exam == e) { exam = e } }
                }
                Text("পরীক্ষার তারিখ", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { pickDate() }, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    Text(if (date.isBlank()) "📅 তারিখ বাছুন (ঐচ্ছিক)" else "📅 $date",
                        fontFamily = NotoSansBengali, fontSize = 13.sp)
                }
                Text("দৈনিক পড়ার সময়", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAILY_OPTIONS.forEach { m -> Chip(minutesLabel(m), minutes == m) { minutes = m } }
                }
                Text("সাপ্তাহিক ছুটি", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OFF_DAYS.forEach { (v, n) -> Chip(n, off == v) { off = v } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(RoutineGoal(exam, date, minutes, off)) }, enabled = exam.isNotBlank()) {
                Text("সেভ করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
    )
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))
