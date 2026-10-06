package com.hanif.smartstudy.ui.quiz

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.ui.shared.StudyReaderStore
import com.hanif.smartstudy.ui.shared.StudyRichBlocks
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.BuddyViewModel
import com.hanif.smartstudy.viewmodel.QuizViewModel
import com.hanif.smartstudy.viewmodel.RoutineViewModel
import kotlinx.coroutines.delay

/**
 * ── Study Nav "Phase 4 — Smart Learning" ──
 *
 * Study Topic-এর ভেতরে (প্রশ্ন-তালিকার ঠিক ওপরে) একটা স্ক্রল-যোগ্য টুলবার:
 *   🎯 Exam Focus   — এই পাতার 💡 Important / ⚠️ Mistake / 🎯 Trick / ⭐ Saved
 *   ⚡ Quick Notes  — ৩০ সেকেন্ডের রিভিশন (প্রশ্ন → সংক্ষিপ্ত উত্তর)
 *   📝 Practice     — এই Topic থেকে ৫ / ১০ প্রশ্ন বা পুরো Topic Quiz (Quiz ট্যাবে)
 *   🔴 Wrong Review — এই Topic-এ N টি ভুল থাকলে (Home-এর Wrong Review-তে নিয়ে যায়)
 *   🗓 Routine      — এই Topic আজকের Routine-এ যোগ
 *   ⏱ Focus 25m    — ২৫ মিনিটের ফোকাস টাইমার (টপ-বারে কাউন্টডাউন)
 *
 * কনটেন্ট-মার্কআপ (Phase 2-এর সাথে): `!! লেখা` = Important, `?? লেখা` = Common Mistake।
 * Exam Focus/Quick Notes বর্তমানে লোড হওয়া পাতার প্রশ্ন থেকেই তৈরি হয় (নতুন নেটওয়ার্ক কল নেই)।
 */

/** Study থেকে অন্য ট্যাবে যাওয়ার অ্যাকশন — MainScreen হ্যান্ডেল করে */
sealed class StudyAction {
    /** count = 0 মানে পুরো Topic (Quiz ট্যাবে Topic খোলে), নাহলে ওই সংখ্যার ইনস্ট্যান্ট টেস্ট */
    data class Practice(val subject: String, val topic: String, val count: Int) : StudyAction()
    object OpenWrongReview : StudyAction()
    /** Phase 6: Study Buddy-কে আগে থেকে invite-এ বসিয়ে Challenge Create স্ক্রিন খোলা */
    data class BuddyChallenge(val subject: String, val topic: String, val buddyPhone: String) : StudyAction()
    /** Phase 6: বন্ধু যে Topic পড়ছে সেখানে Join */
    data class OpenTopic(val subject: String, val topic: String) : StudyAction()
}

/** 📤 Lesson শেয়ার — সাধারণ Android শেয়ার-শীট; লিংক অ্যাপ ইন্সটল থাকলে সরাসরি Study-তে খোলে */
private fun shareStudyTopic(ctx: Context, subject: String, topic: String) {
    val link = "smartstudy://study/" + Uri.encode(subject)
    val text = "📖 \"$topic\" ($subject) — Smart Study অ্যাপে পড়ছি! একসাথে পড়বি?\n$link"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        ctx.startActivity(Intent.createChooser(send, "Lesson শেয়ার করুন").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus timer (সিঙ্গেলটন — টপিক-লিস্টে/ফিরে এলেও চলতে থাকে)
// ─────────────────────────────────────────────────────────────────────────────
object StudyFocusTimer {
    const val DEFAULT_MINUTES = 25
    var endAtMs by mutableStateOf(0L)
        private set
    var topic by mutableStateOf("")
        private set

    val isActive: Boolean get() = endAtMs > 0L

    fun start(topicName: String, minutes: Int = DEFAULT_MINUTES) {
        topic = topicName
        endAtMs = System.currentTimeMillis() + minutes * 60_000L
    }

    /** Buddy shared session-এর জন্য — দুজনের শেষ-সময় একই (সিঙ্ক) */
    fun startUntil(topicName: String, endMs: Long) {
        topic = topicName
        endAtMs = endMs
    }

    fun stop() { endAtMs = 0L; topic = "" }
}

/** টপ-বারে বসে: চালু থাকলে "⏱ 24:10", শেষ হলে টোস্ট দিয়ে বন্ধ হয় */
@Composable
fun StudyFocusTicker() {
    if (!StudyFocusTimer.isActive) return
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(StudyFocusTimer.endAtMs) {
        while (StudyFocusTimer.isActive) {
            now = System.currentTimeMillis()
            if (now >= StudyFocusTimer.endAtMs) {
                val t = StudyFocusTimer.topic
                StudyFocusTimer.stop()
                Toast.makeText(ctx, "🎉 ফোকাস সেশন শেষ! \"$t\" — এবার একটা Quick Quiz দিয়ে যাচাই করো", Toast.LENGTH_LONG).show()
                break
            }
            delay(1000)
        }
    }
    val remain = ((StudyFocusTimer.endAtMs - now) / 1000).coerceAtLeast(0)
    Text(
        "⏱ %d:%02d".format(remain / 60, remain % 60),
        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669),
        fontFamily = NotoSansBengali,
        modifier = Modifier.padding(end = 4.dp).clickable { StudyFocusTimer.stop() }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Content extraction (Exam Focus / Quick Notes)
// ─────────────────────────────────────────────────────────────────────────────
private data class FocusEntry(val title: String, val text: String)

private val tagRegex = Regex("<[^>]*>")

private fun plain(s: String): String =
    s.replace(tagRegex, "").replace("**", "").replace("__", "").replace("++", "")
        .replace(Regex("^[#>\\-•]+\\s*", RegexOption.MULTILINE), "").trim()

private fun firstLine(s: String): String = plain(s).lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""

private fun titleOf(q: QuestionItem): String =
    firstLine(q.question.ifBlank { q.explanation.ifBlank { q.answer } })

private fun linesWith(q: QuestionItem, prefix: String): List<String> =
    listOf(q.question, q.answer, q.explanation)
        .flatMap { it.replace("\r\n", "\n").split('\n') }
        .map { it.trim() }
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix).trim() }
        .filter { it.isNotBlank() }

private class FocusData(val important: List<FocusEntry>, val mistake: List<FocusEntry>,
                        val trick: List<FocusEntry>, val saved: List<FocusEntry>)

private fun buildFocusData(qs: List<QuestionItem>): FocusData {
    val imp = ArrayList<FocusEntry>(); val mis = ArrayList<FocusEntry>()
    val trk = ArrayList<FocusEntry>(); val sav = ArrayList<FocusEntry>()
    for (q in qs) {
        val t = titleOf(q)
        linesWith(q, "!!").forEach { imp.add(FocusEntry(t, it)) }
        linesWith(q, "??").forEach { mis.add(FocusEntry(t, it)) }
        if (q.technique.isNotBlank()) trk.add(FocusEntry(t, q.technique))
        if (q.isBookmarked) sav.add(FocusEntry(t, plain(q.answer.ifBlank { q.explanation }).take(220)))
    }
    return FocusData(imp, mis, trk, sav)
}

// ─────────────────────────────────────────────────────────────────────────────
// Toolbar
// ─────────────────────────────────────────────────────────────────────────────
private val Accent = Color(0xFF059669)

@Composable
fun StudyTopicToolbar(
    viewModel     : QuizViewModel,
    subject       : String,
    topic         : String,
    questions     : List<QuestionItem>,
    onStudyAction : ((StudyAction) -> Unit)?
) {
    val ctx = LocalContext.current
    val routineVm: RoutineViewModel = viewModel()

    var showFocus    by remember { mutableStateOf(false) }
    var showNotes    by remember { mutableStateOf(false) }
    var showPractice by remember { mutableStateOf(false) }
    var showRoutine  by remember { mutableStateOf(false) }
    var showBuddy    by remember { mutableStateOf(false) }
    var showAi       by remember { mutableStateOf(false) }

    // ── Phase 6: Study Buddy / Study Together ──
    val buddyVm: BuddyViewModel = viewModel()
    val buddyState by buddyVm.state.collectAsState()
    val hasBuddy = buddyState.hasBuddy
    LaunchedEffect(subject, topic, hasBuddy) { if (hasBuddy) buddyVm.setStudying(subject, topic) }
    DisposableEffect(subject, topic) { onDispose { buddyVm.clearStudying() } }

    var wrongCount by remember(subject, topic) { mutableStateOf(0) }
    LaunchedEffect(subject, topic) { wrongCount = viewModel.topicWrongCount(subject, topic) }

    // ── Phase 5: Quick Revision সেশন — শুরু হলে Quick Note নিজে থেকেই খোলে ──
    val inRevision = StudyRevisionSession.isFor(subject, topic)
    LaunchedEffect(inRevision, questions.isNotEmpty()) {
        if (inRevision && !StudyRevisionSession.notesDone && questions.isNotEmpty()) {
            showNotes = true
            StudyRevisionSession.notesDone = true
        }
    }
    if (inRevision) {
        RevisionBanner(
            notesDone = StudyRevisionSession.notesDone,
            quizDone  = StudyRevisionSession.quizDone,
            wrongCount = wrongCount,
            onNotes = { showNotes = true; StudyRevisionSession.notesDone = true },
            onQuiz  = {
                StudyRevisionSession.quizDone = true
                onStudyAction?.invoke(StudyAction.Practice(subject, topic, 3))
            },
            onWrong = { onStudyAction?.invoke(StudyAction.OpenWrongReview) },
            canNavigate = onStudyAction != null,
            onFinish = {
                StudyRevisionStore(ctx).markRevised(subject, topic)
                StudyRevisionSession.finish()
                Toast.makeText(ctx, "✅ \"$topic\" — Revision শেষ! পরের রিভিশন নির্ধারিত হয়েছে", Toast.LENGTH_LONG).show()
            },
            onCancel = { StudyRevisionSession.finish() }
        )
    }

    // ── বন্ধু এখন পড়ছে? (Study Together) ──
    val studying = buddyState.buddyStudying
    if (hasBuddy && studying != null && studying.isFresh()) {
        val who  = studying.name.ifBlank { buddyState.buddy?.buddyName ?: "Buddy" }
        val same = studying.subject == subject && studying.topic == topic
        Surface(
            shape = RoundedCornerShape(12.dp), color = Accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (same) "🤝 $who এখন এই Topic-এই পড়ছে — তোমরা একসাথে পড়ছো!"
                    else "🤝 $who এখন পড়ছে: ${studying.topic}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent,
                    fontFamily = NotoSansBengali, modifier = Modifier.weight(1f)
                )
                if (!same && onStudyAction != null) {
                    Text("Join", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        fontFamily = NotoSansBengali,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Accent)
                            .clickable { onStudyAction(StudyAction.OpenTopic(studying.subject, studying.topic)) }
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolChip("🤖 AI") { showAi = true }
        ToolChip("🎯 Exam Focus") { showFocus = true }
        ToolChip("⚡ Quick Notes") { showNotes = true }
        if (onStudyAction != null) ToolChip("📝 Practice") { showPractice = true }
        if (wrongCount > 0 && onStudyAction != null) {
            ToolChip("🔴 Wrong Review ($wrongCount)", danger = true) { onStudyAction(StudyAction.OpenWrongReview) }
        }
        ToolChip("🗓 Routine") { showRoutine = true }
        if (hasBuddy) ToolChip("🤝 Buddy") { showBuddy = true }
        ToolChip("📤 Share") { shareStudyTopic(ctx, subject, topic) }
        if (StudyFocusTimer.isActive) {
            ToolChip("⏱ Focus চলছে — বন্ধ", active = true) { StudyFocusTimer.stop() }
        } else {
            ToolChip("⏱ Focus ${StudyFocusTimer.DEFAULT_MINUTES}m") {
                StudyFocusTimer.start(topic)
                Toast.makeText(ctx, "⏱ ${StudyFocusTimer.DEFAULT_MINUTES} মিনিটের ফোকাস শুরু — \"$topic\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showBuddy) {
        val buddy = buddyState.buddy
        BuddySheet(
            buddyName = buddy?.buddyName ?: "Buddy",
            topic = topic,
            onInvite = { showBuddy = false; buddyVm.inviteToStudy(subject, topic) },
            onChallenge = if (onStudyAction != null && buddy != null) ({
                showBuddy = false
                onStudyAction(StudyAction.BuddyChallenge(subject, topic, buddy.buddyPhone))
            }) else null,
            onDismiss = { showBuddy = false }
        )
    }
    if (showAi)       StudyAiSheet(subject, topic, questions) { showAi = false }
    if (showFocus)    ExamFocusSheet(topic, questions) { showFocus = false }
    if (showNotes)    QuickNotesSheet(topic, questions) { showNotes = false }
    if (showPractice && onStudyAction != null) {
        PracticeSheet(
            onPick = { count ->
                showPractice = false
                onStudyAction(StudyAction.Practice(subject, topic, count))
            },
            onDismiss = { showPractice = false }
        )
    }
    if (showRoutine) {
        RoutineAddDialog(
            topic = topic,
            onConfirm = { minutes ->
                showRoutine = false
                routineVm.addItem(title = topic, subject = subject, subTopic = topic, minutes = minutes)
                Toast.makeText(ctx, "🗓 \"$topic\" — $minutes মিনিট, আজকের Routine-এ যোগ হয়েছে", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showRoutine = false }
        )
    }
}

@Composable
private fun RevisionBanner(
    notesDone: Boolean, quizDone: Boolean, wrongCount: Int, canNavigate: Boolean,
    onNotes: () -> Unit, onQuiz: () -> Unit, onWrong: () -> Unit,
    onFinish: () -> Unit, onCancel: () -> Unit
) {
    val purple = StudyTopicStatus.NEEDS_REVISION.color
    Surface(
        shape = RoundedCornerShape(12.dp), color = purple.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, purple.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔄 Quick Revision (৫–১০ মিনিট)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    color = purple, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                Text("✕", fontSize = 14.sp, color = purple, modifier = Modifier.clickable(onClick = onCancel).padding(4.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolChip((if (notesDone) "✓ " else "① ") + "Quick Note", active = notesDone, onClick = onNotes)
                if (canNavigate) {
                    ToolChip((if (quizDone) "✓ " else "② ") + "৩টি প্রশ্ন", active = quizDone, onClick = onQuiz)
                    if (wrongCount > 0) ToolChip("③ Wrong Review ($wrongCount)", danger = true, onClick = onWrong)
                }
                ToolChip("✅ Revision শেষ", onClick = onFinish)
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, danger: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    val color = when { danger -> Color(0xFFDC2626); else -> Accent }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) color else color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
            color = if (active) Color.White else color, maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheets
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamFocusSheet(topic: String, questions: List<QuestionItem>, onDismiss: () -> Unit) {
    val data = remember(questions) { buildFocusData(questions) }
    val tabs = listOf(
        "💡 Important" to data.important, "⚠️ Mistake" to data.mistake,
        "🎯 Trick" to data.trick, "⭐ Saved" to data.saved
    )
    var tab by remember { mutableStateOf(tabs.indexOfFirst { it.second.isNotEmpty() }.coerceAtLeast(0)) }
    val settings = StudyReaderStore.settings
    val fg = MaterialTheme.colorScheme.onSurface

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("🎯 পরীক্ষায় গুরুত্বপূর্ণ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali)
            Text(topic, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, (label, list) ->
                    val sel = tab == i
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) Accent else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { tab = i }
                    ) {
                        Text("$label (${list.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            fontFamily = NotoSansBengali,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val current = tabs[tab].second
            if (current.isEmpty()) {
                Text(
                    "এই পাতায় এই ধরনের কিছু নেই।\n(কনটেন্টে `!! লেখা` = Important, `?? লেখা` = Mistake, টেকনিক ঘর = Trick, ⭐ = Saved)",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(current) { e ->
                        Surface(shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                if (e.title.isNotBlank()) {
                                    Text(e.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent,
                                        fontFamily = NotoSansBengali, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                }
                                StudyRichBlocks(e.text, settings, 14, fg)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNotesSheet(topic: String, questions: List<QuestionItem>, onDismiss: () -> Unit) {
    val important = remember(questions) { questions.flatMap { linesWith(it, "!!") } }
    val cards = remember(questions) {
        questions.mapNotNull { q ->
            val t = titleOf(q)
            val body = firstLine(q.answer.ifBlank { q.explanation }).take(160)
            if (t.isBlank()) null else FocusEntry(t, if (body == t) "" else body)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("⚡ Quick Notes", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            Text("$topic — এই পাতার ${cards.size}টি, ৩০ সেকেন্ডের রিভিশন", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (important.isNotEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3C4),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("🔥 পরীক্ষার জন্য মনে রাখুন", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF92400E), fontFamily = NotoSansBengali)
                                important.forEach {
                                    Text("• ${plain(it)}", fontSize = 13.sp, color = Color(0xFF3B2A00),
                                        fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
                items(cards) { c ->
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                            if (c.text.isNotBlank()) {
                                Text("➜ ${c.text}", fontSize = 13.sp, color = Accent, fontFamily = NotoSansBengali,
                                    modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuddySheet(
    buddyName: String, topic: String,
    onInvite: () -> Unit, onChallenge: (() -> Unit)?, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🤝 $buddyName-এর সাথে", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            Text(topic, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BuddyOption("🤝 এই lessonটা একসাথে পড়বি?", "বন্ধুর কাছে আমন্ত্রণ যাবে; ট্যাপ করলেই এই Topic-এ খুলবে", onInvite)
            if (onChallenge != null) {
                BuddyOption("🎯 এই Topic থেকে ১০-প্রশ্ন Challenge", "Challenge তৈরির পেজ খুলবে — বন্ধু আগে থেকেই যোগ করা", onChallenge)
            }
        }
    }
}

@Composable
private fun BuddyOption(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = Accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Accent, fontFamily = NotoSansBengali)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📝 এই Topic থেকে Practice করুন", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali)
            Text("Quiz ট্যাবে খুলবে — ভুলগুলো Wrong Review-তে জমা হবে", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "৫ প্রশ্ন", 10 to "১০ প্রশ্ন", 0 to "পুরো Topic").forEach { (n, label) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp), color = Accent,
                        modifier = Modifier.weight(1f).clickable { onPick(n) }
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            fontFamily = NotoSansBengali,
                            modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineAddDialog(topic: String, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf(20) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🗓 Routine-এ যোগ করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("\"$topic\" — কত মিনিট পড়বে?", fontFamily = NotoSansBengali, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 20, 30, 45).forEach { m ->
                        val sel = minutes == m
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (sel) Accent else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { minutes = m }
                        ) {
                            Text("$m", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes) }) { Text("যোগ করুন", fontFamily = NotoSansBengali) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
    )
}
