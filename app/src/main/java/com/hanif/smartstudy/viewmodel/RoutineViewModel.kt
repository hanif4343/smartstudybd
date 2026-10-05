package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.RoutineCache
import com.hanif.smartstudy.data.local.RoutineSmartPlanner
import com.hanif.smartstudy.data.local.RoutineGoal
import com.hanif.smartstudy.data.local.RoutineGoalStore
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.DailyRoutine
import com.hanif.smartstudy.data.model.RoutineSubjectOption
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.data.repository.DataState
import com.hanif.smartstudy.receiver.RoutineItemReminderReceiver
import com.hanif.smartstudy.util.AudienceFilter.forUser
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.SoundManager
import com.hanif.smartstudy.widget.RoutineWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
//  RoutineViewModel — হোম স্ক্রিন চেকলিস্ট (আজকের পড়ার রুটিন)
// ─────────────────────────────────────────────────────────

class RoutineViewModel(app: Application) : AndroidViewModel(app) {

    private val cache   = RoutineCache(app)
    private val repo    = ContentRepository(app)
    private val session = SessionManager(app)

    private val _state = MutableStateFlow(DailyRoutine())
    val state: StateFlow<DailyRoutine> = _state.asStateFlow()

    // ── "বিষয় (ঐচ্ছিক)" ও "SubTopic" dropdown — ইউজারের audience
    // (classLevel/userType) অনুযায়ী ফিল্টার করা Subject/SubTopic লিস্ট ──
    private val _subjectOptions = MutableStateFlow<List<RoutineSubjectOption>>(emptyList())
    val subjectOptions: StateFlow<List<RoutineSubjectOption>> = _subjectOptions.asStateFlow()

    // ── স্মার্ট প্ল্যান তৈরির ফলাফল-বার্তা (UI একবার দেখিয়ে clear করে) ──
    // ── লক্ষ্য (পরীক্ষা/তারিখ/দৈনিক সময়/ছুটি) ──
    private val goalStore = RoutineGoalStore(app)
    private val _goal = MutableStateFlow(goalStore.load())
    val goal: StateFlow<RoutineGoal> = _goal.asStateFlow()
    fun saveGoal(g: RoutineGoal) { goalStore.save(g); _goal.value = goalStore.load() }

    private val _planMessage = MutableStateFlow<String?>(null)
    val planMessage: StateFlow<String?> = _planMessage.asStateFlow()
    fun clearPlanMessage() { _planMessage.value = null }

    init {
        load()
        loadSubjectOptions()
        // DataStore-এর পরিবর্তন সরাসরি শোনা — নিজে-নিজে-টিক (RoutineAutoTracker) হলে স্ক্রিন সাথে সাথে আপডেট
        viewModelScope.launch { cache.todayRoutineFlow().collect { _state.value = it } }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = cache.getTodayRoutine()
            // App চালু/resume হওয়ার সময় আজকের active reminder-গুলো আবার schedule করো
            // (date rollover, app kill, ইত্যাদির পরও alarm ঠিকভাবে চলতে থাকুক)
            RoutineItemReminderReceiver.rescheduleAll(getApplication())
            syncWidget()
        }
    }

    /**
     * AppContent (quiz + qbank + study) থেকে — লগইন করা ইউজারের
     * audience (classLevel/userType) অনুযায়ী ফিল্টার করে — Subject ও
     * তার অধীনে SubTopic-গুলোর তালিকা তৈরি করো। এটাই "বিষয় (ঐচ্ছিক)"
     * dropdown-এ দেখানো হবে — ফ্রি-টেক্সট নয়।
     */
    fun loadSubjectOptions() {
        viewModelScope.launch {
            val list = buildSubjectOptions()
            if (list.isNotEmpty()) _subjectOptions.value = list
        }
    }

    private suspend fun buildSubjectOptions(): List<RoutineSubjectOption> {
        val content: AppContent = when (val result = repo.getContent()) {
            is DataState.Success -> result.data
            else -> ContentRepository.getMemCache() ?: AppContent()
        }
        if (content.isEmpty()) return emptyList()

        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered = content.forUser(user, adminTag)

        val map = linkedMapOf<String, LinkedHashSet<String>>()
        fun collect(subject: String?, subTopic: String?) {
            val subj = subject?.trim().orEmpty()
            if (subj.isEmpty()) return
            val set = map.getOrPut(subj) { linkedSetOf() }
            val st = subTopic?.trim().orEmpty()
            if (st.isNotEmpty()) set.add(st)
        }
        filtered.quiz.forEach  { collect(it.subject, it.subTopic) }
        filtered.qbank.forEach { collect(it.subject, it.subTopic) }
        filtered.study.forEach { collect(it.subject, it.subTopic) }

        return map
            .map { (subject, subTopics) -> RoutineSubjectOption(subject, subTopics.sorted()) }
            .sortedBy { it.subject }
    }

    /**
     * ✨ আজকের স্মার্ট প্ল্যান — এক চাপে ৪টা আইটেম: অর্ধেক দুর্বল টপিক (ভুল প্রশ্ন জমে আছে),
     * অর্ধেক নতুন টপিক (গত ১৪ দিনে আসেনি)। আগের স্মার্ট আইটেম বদলে যায়; নিজে যোগ করা আইটেম অক্ষত।
     */
    fun generateSmartPlan() {
        viewModelScope.launch {
            // ── লক্ষ্য অনুযায়ী: ছুটির দিনে প্ল্যান নয়; আইটেম সংখ্যা ও প্রতিটার সময় দৈনিক লক্ষ্য থেকে ──
            val g = _goal.value
            if (g.isOffToday()) {
                _planMessage.value = "আজ আপনার সাপ্তাহিক ছুটির দিন — বিশ্রাম নিন 😌 (লক্ষ্য বদলে ছুটি সরাতে পারেন)"
                return@launch
            }
            val count = (g.dailyMinutes / 30).coerceIn(2, 8)
            val perItem = (((g.dailyMinutes.toFloat() / count) / 5f).toInt() * 5).coerceAtLeast(15)
            val perWeak = (((perItem * 0.75f) / 5f).toInt() * 5).coerceAtLeast(10)
            val options = _subjectOptions.value.ifEmpty { buildSubjectOptions().also { if (it.isNotEmpty()) _subjectOptions.value = it } }
            val allTopics = options.flatMap { o -> o.subTopics.map { RoutineSmartPlanner.Topic(o.subject, it) } }
            if (allTopics.isEmpty()) {
                _planMessage.value = "প্ল্যান বানানো যায়নি — আগে কনটেন্ট ডাউনলোড হতে দিন (ইন্টারনেট চালু করে একবার খুলুন)"
                return@launch
            }

            // ── দুর্বল টপিক: এখনো ভুল-তালিকায় থাকা প্রশ্ন → তাদের বিষয়/টপিক ──
            val wrong = HashMap<RoutineSmartPlanner.Topic, Int>()
            val content = ContentRepository.getMemCache()
            if (content != null) {
                val qp = getApplication<Application>().getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)
                val ids = qp.getStringSet("wrong_q_ids", emptySet()) ?: emptySet()
                val quizIdx  = content.quiz.associateBy  { it.id ?: "" }
                val qbankIdx = content.qbank.associateBy { it.id ?: "" }
                val studyIdx = content.study.associateBy { it.id ?: "" }
                for (entry in ids) {
                    val sheet = entry.substringBefore(":"); val qid = entry.substringAfter(":", "")
                    val (sub, top) = when (sheet) {
                        "qbank" -> qbankIdx[qid]?.let { it.subject to it.subTopic }
                        "study" -> studyIdx[qid]?.let { it.subject to it.subTopic }
                        else    -> quizIdx[qid]?.let  { it.subject to it.subTopic }
                    } ?: continue
                    val t = RoutineSmartPlanner.Topic(sub?.trim().orEmpty(), top?.trim().orEmpty())
                    if (t.subject.isNotEmpty() && t.subTopic.isNotEmpty()) wrong[t] = (wrong[t] ?: 0) + 1
                }
            }

            // ── সাম্প্রতিক (১৪ দিন) প্ল্যান করা টপিক ──
            val rp = getApplication<Application>().getSharedPreferences("routine_smart_prefs", android.content.Context.MODE_PRIVATE)
            val today = cache.getTodayRoutine()
            val cutoff = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -14) }
            val cutoffStr = "%04d-%02d-%02d".format(cutoff.get(java.util.Calendar.YEAR), cutoff.get(java.util.Calendar.MONTH) + 1, cutoff.get(java.util.Calendar.DAY_OF_MONTH))
            val recentRaw = (rp.getStringSet("recent", emptySet()) ?: emptySet()).filter { it.substringBefore("|") >= cutoffStr }
            val recent = recentRaw.mapNotNull {
                val p = it.split("|"); if (p.size >= 3) RoutineSmartPlanner.Topic(p[1], p[2]) else null
            }.toSet()

            // নিজে যোগ করা আইটেমের টপিক বাদ (ডুপ্লিকেট এড়াতে)
            val existing = today.items.filter { !it.auto && it.subTopic.isNotBlank() }
                .map { RoutineSmartPlanner.Topic(it.subject.trim(), it.subTopic.trim()) }.toSet()

            val picks = RoutineSmartPlanner.plan(
                allTopics = allTopics, wrongCounts = wrong, recent = recent,
                existing = existing, count = count, seed = today.date.hashCode().toLong()
            )
            if (picks.isEmpty()) {
                _planMessage.value = "আজকের জন্য নতুন কোনো টপিক পাওয়া যায়নি"
                return@launch
            }

            val now = System.currentTimeMillis()
            val items = picks.mapIndexed { i, p ->
                com.hanif.smartstudy.data.model.RoutineItem(
                    id = "r_${now}_$i",
                    title = if (p.weak) "🔁 ${p.topic.subTopic} — ভুলগুলো ঝালাই (${p.wrongCount}টা)" else "📘 ${p.topic.subTopic} — নতুন পড়া",
                    subject = p.topic.subject, subTopic = p.topic.subTopic,
                    minutes = if (p.weak) perWeak else perItem, done = false, auto = true
                )
            }
            cache.replaceAutoItems(items)
            _state.value = cache.getTodayRoutine()
            syncWidget()

            // সাম্প্রতিক তালিকা হালনাগাদ (শুধু আজকের নতুন টপিকগুলো)
            val fresh = picks.filter { !it.weak }.map { "${today.date}|${it.topic.subject}|${it.topic.subTopic}" }
            rp.edit().putStringSet("recent", (recentRaw + fresh).toSet()).apply()

            val w = picks.count { it.weak }; val f = picks.size - w
            _planMessage.value = "✨ আজকের প্ল্যান তৈরি: $w টা দুর্বল + $f টা নতুন টপিক (মোট ${items.sumOf { it.minutes }} মিনিট)"
        }
    }

    fun addItem(
        title: String,
        subject: String = "",
        subTopic: String = "",
        minutes: Int = 20,
        reminderEnabled: Boolean = false,
        reminderHour: Int = -1,
        reminderMinute: Int = -1
    ) {
        val cleaned = title.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val added = cache.addItem(cleaned, subject.trim(), subTopic.trim(), minutes, reminderEnabled, reminderHour, reminderMinute)
            _state.value = cache.getTodayRoutine()

            if (added.hasReminder) {
                RoutineItemReminderReceiver.schedule(
                    getApplication(), added.id, added.title, added.subject,
                    added.reminderHour, added.reminderMinute
                )
            }
            syncWidget()
        }
    }

    fun toggleItem(id: String) {
        viewModelScope.launch {
            cache.toggleItem(id)
            val updated = cache.getTodayRoutine()
            _state.value = updated

            // টিক দেওয়ার সময় সাউন্ড ফিডব্যাক
            val item = updated.items.find { it.id == id }
            if (item?.done == true) SoundManager.playCorrect()

            // আইটেম সম্পন্ন হলে আজকের আর রিমাইন্ডার দরকার নেই; আনচেক করলে আবার চালু করো
            if (item != null && item.hasReminder) {
                if (item.done) {
                    RoutineItemReminderReceiver.cancel(getApplication(), id)
                } else {
                    RoutineItemReminderReceiver.schedule(
                        getApplication(), item.id, item.title, item.subject,
                        item.reminderHour, item.reminderMinute
                    )
                }
            }

            syncWidget()
        }
    }

    // ── Bottom sheet থেকে "পড়া শেষ করেছি" বাটনে — idempotent, শুধু done=true করে,
    //    আগে থেকে done থাকলে toggle করে আবার false করে না ──
    fun markDone(id: String) {
        viewModelScope.launch {
            val current = _state.value.items.find { it.id == id }
            if (current?.done == true) return@launch
            cache.toggleItem(id)
            val updated = cache.getTodayRoutine()
            _state.value = updated
            SoundManager.playCorrect()

            if (current?.hasReminder == true) {
                RoutineItemReminderReceiver.cancel(getApplication(), id)
            }

            syncWidget()
        }
    }

    fun removeItem(id: String) {
        viewModelScope.launch {
            cache.removeItem(id)
            _state.value = cache.getTodayRoutine()
            RoutineItemReminderReceiver.cancel(getApplication(), id)
            syncWidget()
        }
    }

    // ── বিদ্যমান আইটেমের রিমাইন্ডার সময় সেট/পরিবর্তন/বন্ধ করো ──
    fun setItemReminder(id: String, enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            val updatedItem = cache.setItemReminder(id, enabled, hour, minute)
            _state.value = cache.getTodayRoutine()

            if (updatedItem != null) {
                if (updatedItem.hasReminder && !updatedItem.done) {
                    RoutineItemReminderReceiver.schedule(
                        getApplication(), updatedItem.id, updatedItem.title, updatedItem.subject,
                        updatedItem.reminderHour, updatedItem.reminderMinute
                    )
                } else {
                    RoutineItemReminderReceiver.cancel(getApplication(), id)
                }
            }
            syncWidget()
        }
    }

    // ── Home screen widget আপডেট করো ──
    private fun syncWidget() {
        RoutineWidgetProvider.updateAll(getApplication())
    }
}
