package com.hanif.smartstudy.ui.quiz

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
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
import com.hanif.smartstudy.data.model.SubjectEntry
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.viewmodel.BuddyViewModel
import com.hanif.smartstudy.viewmodel.QuizViewModel

/**
 * ── Study Nav "Phase 1 — Study Foundation" ──
 *
 * Study ট্যাবের Subject লিস্টের ঠিক ওপরে বসে (SubjectListScreen-এর `headerSlot` দিয়ে):
 *   📚 আমার পড়াশোনা
 *     • Continue Learning — শেষ যে Topic পড়ছিলে, % সহ [Continue] বাটন
 *     • Overall Progress — সব Subject মিলিয়ে কতটা এগিয়েছ
 *     • Recent — শেষ ৫টা Topic (ট্যাপ করলে সরাসরি খোলে)
 *
 * কোনো নতুন নেটওয়ার্ক/Sheet কলাম লাগে না — "Recent" লোকাল SharedPreferences-এ থাকে
 * (StudyRecentStore), Progress আসে আগে থেকেই থাকা SubjectEntry/SubTopicEntry থেকে।
 */

// ─────────────────────────────────────────────────────────────────────────────
// Recent / Continue store (লোকাল, নতুন schema লাগে না)
// ─────────────────────────────────────────────────────────────────────────────
data class StudyRecentTopic(
    val subject  : String,
    val topic    : String,
    val progress : Int,     // 0..100 — সর্বশেষ জানা অগ্রগতি
    val timeMs   : Long
)

class StudyRecentStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("study_home_prefs", Context.MODE_PRIVATE)

    private val sep = '\u001F'   // field separator (ইউজারের টেক্সটে আসার কথা না)

    fun load(): List<StudyRecentTopic> {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val p = line.split(sep)
            if (p.size < 4) return@mapNotNull null
            StudyRecentTopic(
                subject  = p[0],
                topic    = p[1],
                progress = p[2].toIntOrNull()?.coerceIn(0, 100) ?: 0,
                timeMs   = p[3].toLongOrNull() ?: 0L
            )
        }
    }

    /** সর্বশেষ পড়া Topic সবার ওপরে; একই Topic দুইবার থাকে না; সর্বোচ্চ ৫টা। */
    fun record(subject: String, topic: String, progress: Int) {
        if (subject.isBlank() || topic.isBlank()) return
        val updated = buildList {
            add(StudyRecentTopic(subject, topic, progress.coerceIn(0, 100), System.currentTimeMillis()))
            addAll(load().filterNot { it.subject == subject && it.topic == topic })
        }.take(MAX_ITEMS)
        prefs.edit().putString(
            KEY,
            updated.joinToString("\n") { "${it.subject}$sep${it.topic}$sep${it.progress}$sep${it.timeMs}" }
        ).apply()
    }

    companion object {
        private const val KEY = "recent_topics_v1"
        private const val MAX_ITEMS = 5
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI
// ─────────────────────────────────────────────────────────────────────────────
private val StudyGreen     = Color(0xFF059669)
private val StudyGreenDark = Color(0xFF047857)

@Composable
fun StudyHomeSection(
    subjects    : List<SubjectEntry>,
    viewModel   : QuizViewModel,
    onOpenTopic : (subject: String, topic: String) -> Unit
) {
    val ctx    = LocalContext.current
    // ── Phase 5: Smart Revision — পড়া Topic-গুলোর স্ট্যাটাস + আজকের Revision queue ──
    val revStore = remember { StudyRevisionStore(ctx) }
    var overview by remember { mutableStateOf<RevisionOverview?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { overview = buildRevisionOverview(revStore, viewModel) }
    val store  = remember { StudyRecentStore(ctx) }
    // Subject লিস্টে ফিরে এলে এই composable নতুন করে তৈরি হয়, তাই এখানেই সর্বশেষ ডেটা পড়া হয়
    val recent = remember { store.load() }
    val last   = recent.firstOrNull()

    val totalQ = subjects.sumOf { it.totalQ }
    val doneQ  = subjects.sumOf { it.doneQ }
    val overallPct = if (totalQ > 0) ((doneQ * 100) / totalQ).coerceIn(0, 100) else 0

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "📚 আমার পড়াশোনা",
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onBackground
        )

        // ── Continue Learning ──
        if (last != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = StudyGreen,
                modifier = Modifier.fillMaxWidth().clickable { onOpenTopic(last.subject, last.topic) }
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("▶ Continue Learning", fontSize = 11.sp, color = Color.White.copy(0.8f),
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(last.topic, fontSize = 17.sp, color = Color.White,
                            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(last.subject, fontSize = 12.sp, color = Color.White.copy(0.85f),
                            fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { last.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(0.28f)
                        )
                        Text("${last.progress}% পড়া হয়েছে", fontSize = 11.sp,
                            color = Color.White.copy(0.9f), fontFamily = NotoSansBengali,
                            modifier = Modifier.padding(top = 3.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .clickable { onOpenTopic(last.subject, last.topic) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("Continue", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = StudyGreenDark, fontFamily = NotoSansBengali)
                    }
                }
            }
        }

        // ── Phase 6: 🤝 Study Buddy — আজকের অগ্রগতি + এখন কোন Topic পড়ছে (Join) ──
        val buddyVm: BuddyViewModel = viewModel()
        val buddyState by buddyVm.state.collectAsState()
        if (buddyState.hasBuddy) {
            val bName = buddyState.buddy?.buddyName ?: "Buddy"
            val st    = buddyState.buddyStudying
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudyGreen.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, StudyGreen.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🤝 $bName", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali, color = StudyGreenDark)
                        Text(
                            if (st != null && st.isFresh()) "এখন পড়ছে: ${st.topic}"
                            else "আজ ${buddyState.buddyProgress.progressPct}% সম্পন্ন",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (st != null && st.isFresh()) {
                        Text("Join", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            fontFamily = NotoSansBengali,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(StudyGreen)
                                .clickable { onOpenTopic(st.subject, st.topic) }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }

        // ── Overall progress ──
        if (totalQ > 0) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📈 আমার অগ্রগতি", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                        Text("$overallPct%", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            color = StudyGreen, fontFamily = NotoSansBengali)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { overallPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = StudyGreen,
                        trackColor = StudyGreen.copy(alpha = 0.15f)
                    )
                    Text("$doneQ / $totalQ টি পড়া হয়েছে", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // ── Phase 5: Topic-status সারাংশ (🟢 Strong n · 🔴 Weak n · 🔄 Needs Revision n ...) ──
        val ov = overview
        if (ov != null && ov.counts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudyTopicStatus.values().filter { (ov.counts[it] ?: 0) > 0 }.forEach { st ->
                    Text("${st.emoji} ${st.label} ${ov.counts[st]}", fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, color = st.color, fontFamily = NotoSansBengali,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(st.color.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        // ── Phase 5: 🔄 আজ Revision দরকার ──
        if (ov != null && ov.queue.isNotEmpty()) {
            val purple = StudyTopicStatus.NEEDS_REVISION.color
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = purple.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, purple.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔄 আজ Revision দরকার", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                        color = purple, fontFamily = NotoSansBengali)
                    ov.queue.take(3).forEach { r ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenTopic(r.subject, r.topic) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(r.status.emoji, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.topic, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (ov.queue.size > 3) {
                        Text("আরও ${ov.queue.size - 3}টি", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    }
                    val first = ov.queue.first()
                    // ── Phase 7: 🤖 AI Study Plan (ব্যক্তিগত সাজেশন) ──
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF7C3AED), RoundedCornerShape(12.dp))
                            .clickable { showPlan = true }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🤖 AI Study Plan", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED), fontFamily = NotoSansBengali)
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(purple)
                            .clickable {
                                StudyRevisionSession.start(first.subject, first.topic)
                                onOpenTopic(first.subject, first.topic)
                            }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Start Revision (৫–১০ মিনিট)", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, fontFamily = NotoSansBengali)
                    }
                }
            }
        }

        if (showPlan && ov != null && ov.queue.isNotEmpty()) {
            StudyPlanSheet(ov.queue, onOpenTopic) { showPlan = false }
        }

        // ── Recent (শেষ ৫টা Topic; প্রথমটা Continue কার্ডেই আছে, তাই বাকিগুলো) ──
        val others = if (recent.size > 1) recent.drop(1) else emptyList()
        if (others.isNotEmpty()) {
            Text("🕘 Recent", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onBackground)
            others.forEach { r ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenTopic(r.subject, r.topic) }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.topic, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.subject, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${r.progress}%", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = StudyGreen, fontFamily = NotoSansBengali)
                    }
                }
            }
        }

        Text("📘 Subjects", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp))
    }
}
