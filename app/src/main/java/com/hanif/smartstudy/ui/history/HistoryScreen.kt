package com.hanif.smartstudy.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.AlmostTopic
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.model.ResultReviewItem
import com.hanif.smartstudy.data.model.TestHistoryEntry
import com.hanif.smartstudy.ui.home.WrongReviewSection
import com.hanif.smartstudy.ui.home.hdFmtMin
import com.hanif.smartstudy.ui.shared.MathText
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.HistoryViewModel
import com.hanif.smartstudy.viewmodel.QuizViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val HIndigo = Color(0xFF4F46E5)
private val HGreen  = Color(0xFF10B981)
private val HRed    = Color(0xFFEF4444)
private val HAmber  = Color(0xFFF59E0B)
private val HGray   = Color(0xFF94A3B8)

private fun pctColor(p: Int) = when { p >= 60 -> HGreen; p >= 40 -> HAmber; else -> HRed }
private fun dateLabel(ts: Long) = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ts))

private fun entryTitle(e: TestHistoryEntry): String {
    if (e.isModelTest) return e.topics.firstOrNull() ?: "মডেল টেস্ট"
    val base = e.topics.take(2).joinToString(", ").ifBlank { e.modeLabel }
    return if (e.topics.size > 2) "$base +${e.topics.size - 2}" else base
}

@Composable
fun HistoryScreen(
    quizViewModel: QuizViewModel?,
    onOpenMenuPage: (String) -> Unit = {},
    vm: HistoryViewModel = viewModel()
) {
    val history by vm.history.collectAsState()
    val reviewIds by vm.reviewIds.collectAsState()
    val extra by vm.extra.collectAsState()

    var tab by rememberSaveable { mutableStateOf(0) }
    var selected by remember { mutableStateOf<TestHistoryEntry?>(null) }

    // ট্যাবে ঢুকলেই সর্বশেষ ডেটা
    LaunchedEffect(Unit) { vm.refresh() }

    selected?.let { entry ->
        BackHandler { selected = null }
        HistoryReviewDetail(entry, vm) { selected = null }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text("🕘 History", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = NotoSansBengali, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))

        val tabs = listOf("সারসংক্ষেপ", "মক টেস্ট", "ভুল রিভিউ", "সব কার্যক্রম")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { i, t ->
                FilterChip(selected = tab == i, onClick = { tab = i },
                    label = { Text(t, fontFamily = NotoSansBengali, fontSize = 13.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (tab) {
                0 -> OverviewTab(history, extra.studyStats.todayMinutes, extra.studyStats.weekMinutes,
                        extra.studyStats.totalMinutes, extra.goal.doneMinutes, extra.goal.goalMinutes,
                        extra.streakDays, extra.almost)
                1 -> MockTab(history, reviewIds) { selected = it }
                2 -> WrongTab(quizViewModel)
                else -> AllTab(history, reviewIds) { selected = it }
            }
        }
    }
}

// ══════════ সারসংক্ষেপ: Study Time + প্রায় শেষ টপিক + টোটাল ══════════
@Composable
private fun HCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

@Composable
private fun StatBox(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.09f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = NotoSansBengali, maxLines = 1)
        Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, maxLines = 1)
    }
}

@Composable
private fun OverviewTab(
    history: List<TestHistoryEntry>,
    today: Int, week: Int, total: Int, goalDone: Int, goalTotal: Int, streak: Int,
    almost: List<AlmostTopic>
) {
    HCard {
        Text("⏱️ Study Time", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(hdFmtMin(today), "আজ", HGreen, Modifier.weight(1f))
            StatBox(hdFmtMin(week), "এই সপ্তাহ", HIndigo, Modifier.weight(1f))
            StatBox(hdFmtMin(total), "মোট", HAmber, Modifier.weight(1f))
        }
        val frac = if (goalTotal > 0) (goalDone.toFloat() / goalTotal).coerceIn(0f, 1f) else 0f
        Text("আজকের লক্ষ্য: ${hdFmtMin(goalDone)} / ${hdFmtMin(goalTotal)} · 🔥 $streak দিনের স্ট্রিক",
            fontSize = 12.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = HGreen, trackColor = HGreen.copy(alpha = 0.18f))
    }

    // গত ৭ দিন — টেস্টে ব্যয়িত সময় (মিনিট), TestHistory থেকে
    HCard {
        Text("📅 গত ৭ দিন (টেস্টে ব্যয়িত সময়)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
        val days = remember(history) {
            val dayMs = 86_400_000L
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            (6 downTo 0).map { back ->
                val from = start - back * dayMs
                val mins = history.filter { it.timestamp >= from && it.timestamp < from + dayMs }.sumOf { it.timeTakenSec } / 60
                val label = SimpleDateFormat("E", Locale.getDefault()).format(Date(from))
                label to mins
            }
        }
        val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { (label, m) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("${m}m", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    Box(Modifier.fillMaxWidth().height((52f * m / max).coerceAtLeast(3f).dp).clip(RoundedCornerShape(6.dp))
                        .background(if (m > 0) HIndigo else HGray.copy(alpha = 0.3f)))
                    Text(label, fontSize = 9.sp, fontFamily = NotoSansBengali, maxLines = 1)
                }
            }
        }
    }

    // টোটাল / গড় / সেরা
    val graded = history.filter { !it.isUngraded && it.total > 0 }
    HCard {
        Text("📊 টেস্ট পরিসংখ্যান", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("${history.size}", "মোট টেস্ট", HIndigo, Modifier.weight(1f))
            StatBox(if (graded.isEmpty()) "—" else "${graded.sumOf { it.pct } / graded.size}%", "গড় স্কোর", HGreen, Modifier.weight(1f))
            StatBox(if (graded.isEmpty()) "—" else "${graded.maxOf { it.pct }}%", "সেরা", HAmber, Modifier.weight(1f))
        }
    }

    HCard {
        Text("🎯 প্রায় শেষ টপিক", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
        if (almost.isEmpty()) {
            Text("৫০%-এর বেশি শেষ করেছ কিন্তু বাকি আছে — এমন টপিক এখনো নেই। পড়তে থাকো!",
                fontSize = 12.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            almost.forEach { t ->
                val modeLabel = when (t.mode) { "QBANK" -> "QBank"; "STUDY" -> "Study"; else -> "Quiz" }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(modeLabel, fontSize = 10.sp, color = HIndigo, fontFamily = NotoSansBengali,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(HIndigo.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    LinearProgressIndicator(progress = { t.progressPct / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = HGreen, trackColor = HGreen.copy(alpha = 0.18f))
                    Text("${t.attempted}/${t.total} প্রশ্ন (${t.progressPct}%) · আর ${t.remaining}টা বাকি · সঠিক ${t.accuracyPct}%",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                }
            }
        }
    }
}

// ══════════ মক টেস্ট ══════════
@Composable
private fun MockTab(history: List<TestHistoryEntry>, reviewIds: Set<String>, onOpen: (TestHistoryEntry) -> Unit) {
    val list = history.filter { it.isMockTest || it.isModelTest }
    if (list.isEmpty()) {
        EmptyHint("🧪", "এখনো কোনো মক টেস্ট দাওনি", "Home থেকে Model Test চালিয়ে দাও — প্রশ্ন ও রেজাল্টসহ এখানে জমা থাকবে।")
    } else {
        Text("ট্যাপ করলে প্রশ্ন, তোমার উত্তর ও সঠিক উত্তর দেখা যাবে (সর্বশেষ ৩০টা টেস্টের প্রশ্ন সংরক্ষিত থাকে)।",
            fontSize = 11.5.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { HistoryRow(it, it.id in reviewIds, onOpen) }
    }
}

@Composable
private fun AllTab(history: List<TestHistoryEntry>, reviewIds: Set<String>, onOpen: (TestHistoryEntry) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val filters = listOf("ALL" to "সব", "QUIZ" to "কুইজ", "QBANK" to "QBank", "STUDY" to "পড়া", "VIVA" to "ভাইভা")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        filters.forEach { (k, l) -> FilterChip(selected = filter == k, onClick = { filter = k },
            label = { Text(l, fontFamily = NotoSansBengali, fontSize = 12.sp) }) }
    }
    val list = history.filter { filter == "ALL" || it.mode == filter }
    if (list.isEmpty()) EmptyHint("📭", "কিছু পাওয়া যায়নি", "এই ধরনের কোনো কার্যক্রম এখনো নেই।")
    else list.forEach { HistoryRow(it, it.id in reviewIds, onOpen) }
}

@Composable
private fun EmptyHint(emoji: String, title: String, sub: String) {
    HCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 40.sp)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            Text(sub, fontSize = 12.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(e: TestHistoryEntry, hasReview: Boolean, onOpen: (TestHistoryEntry) -> Unit) {
    val col = if (e.isUngraded) HIndigo else pctColor(e.pct)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .then(if (hasReview) Modifier.clickable { onOpen(e) } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.gradeEmoji, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entryTitle(e), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val tag = if (e.isModelTest) "মডেল টেস্ট" else if (e.isMockTest) "মক টেস্ট · ${e.modeLabel}" else e.modeLabel
                Text("$tag · ${dateLabel(e.timestamp)}", fontSize = 10.5.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.isUngraded) Text("জমা ${e.recorded}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = col, fontFamily = NotoSansBengali)
            else Text("${e.pct}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = col, fontFamily = NotoSansBengali)
            if (hasReview) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("✓ ${e.correct}", HGreen); Pill("✗ ${e.wrong}", HRed); Pill("⏭ ${e.skipped}", HGray)
            Pill("⏱ ${hdFmtMin((e.timeTakenSec / 60).coerceAtLeast(if (e.timeTakenSec > 0) 1 else 0))}", HIndigo)
            if (!hasReview && (e.isMockTest || e.isModelTest)) Pill("রিভিউ নেই", HGray)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, fontFamily = NotoSansBengali, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

// ══════════ ভুল রিভিউ (History-র ভেতরেই) ══════════
@Composable
private fun WrongTab(quizViewModel: QuizViewModel?) {
    var wrongItems by remember { mutableStateOf<List<Pair<QuestionItem, Int>>>(emptyList()) }
    LaunchedEffect(quizViewModel) { wrongItems = quizViewModel?.getWrongQuestions() ?: emptyList() }
    if (wrongItems.isEmpty()) {
        EmptyHint("🎉", "কোনো ভুল প্রশ্ন নেই", "কুইজ বা QBank-এ ভুল উত্তর দিলে এখানে জমা হবে, তারপর আবার অনুশীলন করতে পারবে।")
    } else {
        WrongReviewSection(
            wrongItems = wrongItems,
            onAnswerMcq = { qId, opt ->
                val idx = wrongItems.indexOfFirst { it.first.id == qId }
                if (idx != -1) quizViewModel?.answerMcq(idx, opt)
            },
            onAnswerWritten = { qId, text ->
                val idx = wrongItems.indexOfFirst { it.first.id == qId }
                if (idx != -1) quizViewModel?.answerWritten(idx, text) ?: 0 else 0
            },
            onRemoveCorrect = { qId ->
                quizViewModel?.removeWrongQId(qId)
                wrongItems = wrongItems.filter { it.first.id != qId }
            }
        )
    }
}

// ══════════ একটা মক টেস্টের প্রশ্ন-রিভিউ ══════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryReviewDetail(entry: TestHistoryEntry, vm: HistoryViewModel, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<ResultReviewItem>?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    LaunchedEffect(entry.id) { items = vm.loadReview(entry.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryTitle(entry), fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(if (entry.isUngraded) "${entry.recorded}" else "${entry.pct}%", "স্কোর", if (entry.isUngraded) HIndigo else pctColor(entry.pct), Modifier.weight(1f))
                StatBox("${entry.correct}", "সঠিক", HGreen, Modifier.weight(1f))
                StatBox("${entry.wrong}", "ভুল", HRed, Modifier.weight(1f))
                StatBox("${entry.skipped}", "স্কিপ", HGray, Modifier.weight(1f))
            }
            val filters = listOf("all" to "সব", "correct" to "সঠিক", "wrong" to "ভুল", "skipped" to "স্কিপ")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEach { (k, l) -> FilterChip(selected = filter == k, onClick = { filter = k },
                    label = { Text(l, fontFamily = NotoSansBengali, fontSize = 12.sp) }) }
            }
            val all = items
            when {
                all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                all.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("এই টেস্টের প্রশ্ন সংরক্ষিত নেই।", fontFamily = NotoSansBengali)
                }
                else -> {
                    val shown = all.filter { filter == "all" || it.status == filter }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (shown.isEmpty()) Text("এই ফিল্টারে কোনো প্রশ্ন নেই।", fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        shown.forEach { QuestionReviewCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionReviewCard(r: ResultReviewItem) {
    val (label, col) = when (r.status) {
        "correct"  -> "✓ সঠিক" to HGreen
        "wrong"    -> "✗ ভুল" to HRed
        "recorded" -> "✍️ জমা" to HIndigo
        else       -> "⏭ স্কিপ" to HGray
    }
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("প্রশ্ন ${r.number}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = HIndigo, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
            Pill(label, col)
        }
        MathText(r.question, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
        if (r.options.isNotEmpty()) {
            val letters = listOf("A", "B", "C", "D")
            r.options.forEachIndexed { i, opt ->
                if (opt.isBlank()) return@forEachIndexed
                val isCorrect = opt == r.correctAnswer
                val isYours = r.yourAnswer.isNotBlank() && opt == r.yourAnswer
                val c = when { isCorrect -> HGreen; isYours -> HRed; else -> Color.Transparent }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (c == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else c.copy(alpha = 0.12f))
                        .then(if (c != Color.Transparent) Modifier.border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(12.dp)) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(letters.getOrElse(i) { "" }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (c == Color.Transparent) MaterialTheme.colorScheme.onSurfaceVariant else c, modifier = Modifier.width(20.dp))
                    MathText(opt, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (isCorrect) Text("✓", color = HGreen, fontWeight = FontWeight.ExtraBold)
                    else if (isYours) Text("✗", color = HRed, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else {
            if (r.yourAnswer.isNotBlank()) {
                Text("তোমার উত্তর:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                MathText(r.yourAnswer, fontSize = 13.sp)
            }
            if (r.correctAnswer.isNotBlank()) {
                Text("সঠিক উত্তর:", fontSize = 11.sp, color = HGreen, fontFamily = NotoSansBengali)
                MathText(r.correctAnswer, fontSize = 13.sp)
            }
        }
        if (r.options.isNotEmpty() && r.status == "skipped" && r.correctAnswer.isNotBlank()) {
            Text("তুমি স্কিপ করেছিলে", fontSize = 11.sp, color = HGray, fontFamily = NotoSansBengali)
        }
        if (r.explanation.isNotBlank()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HAmber.copy(alpha = 0.10f)).padding(10.dp)) {
                Text("💡 ব্যাখ্যা", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HAmber, fontFamily = NotoSansBengali)
                MathText(r.explanation, fontSize = 12.5.sp)
            }
        }
    }
}
