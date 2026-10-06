package com.hanif.smartstudy.ui.quiz

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.hanif.smartstudy.viewmodel.QuizViewModel

/**
 * ── Study Nav "Phase 5 — Smart Revision" ──
 *
 *  • StudyTopicStatus — ⚪ Not Started / 🔵 Learning / 🟡 Practicing / 🟢 Strong / 🔴 Weak / 🔄 Needs Revision
 *    (ইউজারের বাটন চাপার ওপর নির্ভর করে না — Quiz/QBank/Study উত্তরের ফলাফল + Study activity + সময় থেকে হিসাব)
 *  • StudyRevisionStore — কোন Topic কবে প্রথম/শেষ পড়া হয়েছে, কতবার রিভিশন হয়েছে, পরের রিভিশন কবে
 *    (Spaced revision: ১ → ৩ → ৭ → ১৪ → ৩০ → ৬০ দিন)। লোকাল SharedPreferences, নতুন Sheet কলাম লাগে না।
 *  • StudyRevisionSession — "5–10 মিনিটের Quick Revision": Quick Note → ৩টি প্রশ্ন → Wrong Review → ✅ শেষ
 */
enum class StudyTopicStatus(val emoji: String, val label: String, val color: Color) {
    NOT_STARTED("⚪", "Not Started", Color(0xFF9CA3AF)),
    LEARNING("🔵", "Learning", Color(0xFF3B82F6)),
    PRACTICING("🟡", "Practicing", Color(0xFFD97706)),
    STRONG("🟢", "Strong", Color(0xFF16A34A)),
    WEAK("🔴", "Weak", Color(0xFFDC2626)),
    NEEDS_REVISION("🔄", "Needs Revision", Color(0xFF7C3AED))
}

data class RevEntry(
    val subject   : String,
    val topic     : String,
    val firstMs   : Long,
    val lastMs    : Long,
    val stage     : Int,      // কতবার রিভিশন শেষ হয়েছে
    val nextDueMs : Long
)

class StudyRevisionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("study_revision_prefs", Context.MODE_PRIVATE)
    private val sep = '\u001F'

    fun load(): List<RevEntry> {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val p = line.split(sep)
            if (p.size < 6) return@mapNotNull null
            RevEntry(p[0], p[1], p[2].toLongOrNull() ?: 0L, p[3].toLongOrNull() ?: 0L,
                p[4].toIntOrNull() ?: 0, p[5].toLongOrNull() ?: 0L)
        }
    }

    fun get(subject: String, topic: String): RevEntry? =
        load().firstOrNull { it.subject == subject && it.topic == topic }

    private fun save(list: List<RevEntry>) {
        val trimmed = list.sortedByDescending { it.lastMs }.take(MAX_ENTRIES)
        prefs.edit().putString(KEY, trimmed.joinToString("\n") {
            "${it.subject}$sep${it.topic}$sep${it.firstMs}$sep${it.lastMs}$sep${it.stage}$sep${it.nextDueMs}"
        }).apply()
    }

    /** Study-তে Topic খুললে — প্রথমবার হলে ১ দিন পরের রিভিশন-ডেট বসে, পরেরবার শুধু lastMs বদলায় */
    fun recordVisit(subject: String, topic: String) {
        if (subject.isBlank() || topic.isBlank()) return
        val now = System.currentTimeMillis()
        val all = load()
        val cur = all.firstOrNull { it.subject == subject && it.topic == topic }
        val updated = if (cur == null) {
            RevEntry(subject, topic, now, now, 0, now + DAY_MS * INTERVALS_DAYS[0])
        } else cur.copy(lastMs = now)
        save(listOf(updated) + all.filterNot { it.subject == subject && it.topic == topic })
    }

    /** ✅ Revision শেষ — পরের ব্যবধান বাড়ে (১→৩→৭→১৪→৩০→৬০ দিন) */
    fun markRevised(subject: String, topic: String) {
        val now = System.currentTimeMillis()
        val all = load()
        val cur = all.firstOrNull { it.subject == subject && it.topic == topic }
            ?: RevEntry(subject, topic, now, now, 0, now)
        val stage = cur.stage + 1
        val updated = cur.copy(
            lastMs = now, stage = stage,
            nextDueMs = now + DAY_MS * INTERVALS_DAYS[stage.coerceAtMost(INTERVALS_DAYS.lastIndex)]
        )
        save(listOf(updated) + all.filterNot { it.subject == subject && it.topic == topic })
    }

    companion object {
        private const val KEY = "entries_v1"
        private const val MAX_ENTRIES = 80
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val INTERVALS_DAYS = intArrayOf(1, 3, 7, 14, 30, 60)
    }
}

/** Topic-status হিসাব — অগ্রাধিকার: Weak > Needs Revision > Strong > Practicing > Learning > Not Started */
fun computeTopicStatus(entry: RevEntry?, attempted: Int, correct: Int, now: Long = System.currentTimeMillis()): StudyTopicStatus {
    val wrong = (attempted - correct).coerceAtLeast(0)
    val acc = if (attempted > 0) correct * 100 / attempted else -1
    return when {
        entry == null && attempted == 0        -> StudyTopicStatus.NOT_STARTED
        (attempted >= 3 && acc < 50) || wrong >= 3 -> StudyTopicStatus.WEAK
        entry != null && entry.nextDueMs <= now -> StudyTopicStatus.NEEDS_REVISION
        attempted >= 5 && acc >= 80            -> StudyTopicStatus.STRONG
        attempted > 0                          -> StudyTopicStatus.PRACTICING
        else                                   -> StudyTopicStatus.LEARNING
    }
}

data class RevisionRow(val subject: String, val topic: String, val status: StudyTopicStatus, val reason: String)
data class RevisionOverview(val queue: List<RevisionRow>, val counts: Map<StudyTopicStatus, Int>)

/** পড়া Topic-গুলোর (সর্বশেষ ৩০টি) স্ট্যাটাস + আজকের Revision queue */
suspend fun buildRevisionOverview(store: StudyRevisionStore, vm: QuizViewModel): RevisionOverview {
    val now = System.currentTimeMillis()
    val rows = ArrayList<Pair<RevisionRow, Long>>()
    val counts = HashMap<StudyTopicStatus, Int>()
    for (e in store.load().sortedByDescending { it.lastMs }.take(30)) {
        val (att, cor) = vm.topicStats(e.subject, e.topic)
        val st = computeTopicStatus(e, att, cor, now)
        counts[st] = (counts[st] ?: 0) + 1
        if (st == StudyTopicStatus.WEAK || st == StudyTopicStatus.NEEDS_REVISION) {
            val reason = if (st == StudyTopicStatus.WEAK) {
                "${(att - cor).coerceAtLeast(0)}টি ভুল · ${if (att > 0) cor * 100 / att else 0}% সঠিক"
            } else "রিভিশনের সময় হয়েছে"
            rows.add(RevisionRow(e.subject, e.topic, st, reason) to e.nextDueMs)
        }
    }
    val queue = rows.sortedWith(compareBy({ if (it.first.status == StudyTopicStatus.WEAK) 0 else 1 }, { it.second }))
        .map { it.first }
    return RevisionOverview(queue, counts)
}

/** চলমান Quick Revision সেশন (ইন-মেমরি; অ্যাপ বন্ধ হলে শেষ) */
object StudyRevisionSession {
    var active by mutableStateOf(false)
        private set
    var subject by mutableStateOf("")
        private set
    var topic by mutableStateOf("")
        private set
    var notesDone by mutableStateOf(false)
    var quizDone  by mutableStateOf(false)

    fun start(s: String, t: String) {
        subject = s; topic = t; notesDone = false; quizDone = false; active = true
    }

    fun isFor(s: String, t: String) = active && subject == s && topic == t

    fun finish() { active = false; subject = ""; topic = ""; notesDone = false; quizDone = false }
}
