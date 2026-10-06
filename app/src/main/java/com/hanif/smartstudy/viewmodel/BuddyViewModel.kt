package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.BuddyRepository
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
//  BuddyViewModel — Study Buddy ফিচারের state ও action
// ─────────────────────────────────────────────────────────

class BuddyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = BuddyRepository()
    private val session = SessionManager(app)
    private val cache   = ContentCache(app)

    private val _state = MutableStateFlow(BuddyState())
    val state: StateFlow<BuddyState> = _state.asStateFlow()

    private val _phoneInput = MutableStateFlow("")
    val phoneInput: StateFlow<String> = _phoneInput.asStateFlow()

    private var buddyWatchJob   : Job? = null
    private var progressWatchJob: Job? = null
    private var requestWatchJob : Job? = null
    private var studyingWatchJob: Job? = null
    private var knockWatchJob   : Job? = null
    private var goalWatchJob    : Job? = null
    private var focusWatchJob   : Job? = null
    private var latestGoal      : BuddyGoal? = null
    private var sentWatchJob    : Job? = null
    private var myDaysJob       : Job? = null
    private var buddyDaysJob    : Job? = null
    private var myDays    : Map<String, Int> = emptyMap()
    private var buddyDays : Map<String, Int> = emptyMap()

    // ── Phase B2: share অনুমতি (লোকাল SharedPreferences) ──
    private val sharePrefs = app.getSharedPreferences("buddy_share", android.content.Context.MODE_PRIVATE)

    fun setShare(s: BuddyShareSettings) {
        sharePrefs.edit().putBoolean("studyTime", s.studyTime).putBoolean("progress", s.progress)
            .putBoolean("streak", s.streak).putBoolean("routine", s.routine)
            .putBoolean("quizScore", s.quizScore).apply()
        _state.update { it.copy(share = s) }
        refreshMyProgress()
    }

    init {
        _state.update {
            it.copy(share = BuddyShareSettings(
                studyTime = sharePrefs.getBoolean("studyTime", true),
                progress  = sharePrefs.getBoolean("progress", true),
                streak    = sharePrefs.getBoolean("streak", true),
                routine   = sharePrefs.getBoolean("routine", true),
                quizScore = sharePrefs.getBoolean("quizScore", true)
            ))
        }
        val me = session.getCurrentUser()
        if (!me?.phone.isNullOrEmpty()) {
            loadAll()
        }
    }

    fun loadAll() {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            // আজকের progress আপলোড করো
            uploadMyProgress(me, myPhone)

            // বর্তমান buddy আছে কিনা চেক করো
            val buddy = repo.getMyBuddy(myPhone)
            _state.update { it.copy(hasBuddy = buddy != null, buddy = buddy, isLoading = false) }

            // Buddy link observe করো (realtime)
            buddyWatchJob?.cancel()
            buddyWatchJob = viewModelScope.launch {
                repo.observeMyBuddy(myPhone).collect { link ->
                    _state.update { it.copy(hasBuddy = link != null, buddy = link) }
                    progressWatchJob?.cancel()
                    // ── Study Nav Phase 6: বন্ধু এখন কোন Topic পড়ছে (Study Together) ──
                    studyingWatchJob?.cancel()
                    sentWatchJob?.cancel(); buddyDaysJob?.cancel(); goalWatchJob?.cancel(); focusWatchJob?.cancel()
                    if (link != null) {
                        // ── Phase B1: আমার পাঠানো knock-এর উত্তর + Buddy Streak ──
                        sentWatchJob = viewModelScope.launch {
                            repo.observeKnocks(link.buddyPhone).collect { list ->
                                val cutoff = System.currentTimeMillis() - 2 * 24 * 3600_000L
                                _state.update { st ->
                                    st.copy(sentKnocks = list.filter {
                                        it.fromPhone == myPhone && it.status != "PENDING" && it.at >= cutoff
                                    }.sortedByDescending { it.replyAt })
                                }
                            }
                        }
                        val pk = repo.pairKey(myPhone, link.buddyPhone)
                        goalWatchJob = viewModelScope.launch {
                            repo.observeGoal(pk).collect { node ->
                                latestGoal = node.goal
                                val mine  = node.progress[myPhone.firebaseKey()]
                                val theirs = node.progress[link.buddyPhone.firebaseKey()]
                                _state.update {
                                    it.copy(goalState = BuddyGoalState(node.goal, mine?.first ?: 0, theirs?.first ?: 0,
                                        mine?.second ?: 0L, theirs?.second ?: 0L))
                                }
                                if (node.goal != null) syncGoalProgress(myPhone)
                            }
                        }
                        focusWatchJob = viewModelScope.launch {
                            repo.observeFocus(pk).collect { f ->
                                _state.update { it.copy(focus = f) }
                                // আমি যদি এই session-এ থাকি, আমার টাইমার একই শেষ-সময়ে সিঙ্ক রাখো
                                val myMember = f?.members?.firstOrNull { it.phoneKey == myPhone.firebaseKey() }
                                if (f != null && f.active() && myMember?.inSession == true) {
                                    if (com.hanif.smartstudy.ui.quiz.StudyFocusTimer.endAtMs != f.endMs()) {
                                        com.hanif.smartstudy.ui.quiz.StudyFocusTimer.startUntil("Buddy Focus", f.endMs())
                                    }
                                }
                            }
                        }
                        buddyDaysJob = viewModelScope.launch {
                            repo.observeDays(link.buddyPhone).collect { buddyDays = it; recomputeStreak() }
                        }
                    } else {
                        latestGoal = null
                        _state.update { it.copy(focus = null) }
                        _state.update { it.copy(sentKnocks = emptyList(), buddyStreak = 0, streakRisk = null, goalState = BuddyGoalState()) }
                    }
                    if (link != null) {
                        studyingWatchJob = viewModelScope.launch {
                            repo.observeBuddyStudying(link.buddyPhone).collect { st ->
                                _state.update { it.copy(buddyStudying = st) }
                            }
                        }
                    } else {
                        _state.update { it.copy(buddyStudying = null) }
                    }
                    if (link != null) {
                        progressWatchJob = viewModelScope.launch {
                            repo.observeBuddyProgress(link.buddyPhone).collect { prog ->
                                if (prog != null) {
                                    _state.update { it.copy(buddyProgress = prog) }
                                }
                            }
                        }
                    }
                }
            }

            // ── Phase B1: আমার inbox-এর knock + আমার দিনভিত্তিক রেকর্ড ──
            knockWatchJob?.cancel()
            knockWatchJob = viewModelScope.launch {
                repo.observeKnocks(myPhone).collect { list ->
                    val cutoff = System.currentTimeMillis() - 2 * 24 * 3600_000L
                    _state.update { st ->
                        st.copy(knocks = list.filter { it.status == "PENDING" && it.at >= cutoff }
                            .sortedByDescending { it.at })
                    }
                }
            }
            myDaysJob?.cancel()
            myDaysJob = viewModelScope.launch {
                repo.observeDays(myPhone).collect { myDays = it; recomputeStreak() }
            }

            // পেন্ডিং রিকোয়েস্ট observe করো
            requestWatchJob?.cancel()
            requestWatchJob = viewModelScope.launch {
                repo.observeMyRequests(myPhone).collect { requests ->
                    _state.update { it.copy(incomingRequest = requests.firstOrNull()) }
                }
            }
        }
    }

    // ── আজকের progress গণনা করে Firebase এ আপলোড করো ──────

    private suspend fun uploadMyProgress(me: com.hanif.smartstudy.data.model.User, myPhone: String) {
        val goal = session.getDailyGoal().coerceAtLeast(1)
        val done = cache.getTodayStudyMinutes()
        val pct  = ((done * 100) / goal).coerceIn(0, 100)
        val routine = com.hanif.smartstudy.data.local.RoutineCache(getApplication()).getTodayRoutine()
        // নিজের স্ক্রিনের জন্য পূর্ণ ডেটা; Firebase-এ শুধু অনুমতি দেওয়া অংশ (বাকি -1)
        val full = BuddyProgress(
            phone       = myPhone,
            name        = me.displayName(),
            date        = todayString(),
            doneMinutes = done,
            goalMinutes = goal,
            progressPct = pct,
            streak       = session.getStreak(),
            routineDone  = routine.doneCount,
            routineTotal = routine.totalCount,
            quizAcc      = quizAccuracy()
        )
        val sh = _state.value.share
        val shared = full.copy(
            doneMinutes  = if (sh.studyTime) done else -1,
            goalMinutes  = if (sh.studyTime) goal else -1,
            progressPct  = if (sh.progress) pct else -1,
            streak       = if (sh.streak) full.streak else -1,
            routineDone  = if (sh.routine) full.routineDone else -1,
            routineTotal = if (sh.routine) full.routineTotal else -1,
            quizAcc      = if (sh.quizScore) full.quizAcc else -1
        )
        repo.updateMyProgress(shared)
        if (done > 0) repo.recordDay(myPhone, dayKey(0), done)
        _state.update { it.copy(myProgress = full) }
        syncGoalProgress(myPhone)
    }

    /** HomeScreen/QuizViewModel থেকে কুইজ শেষ করার পর কল করা যেতে পারে progress sync করতে */
    fun refreshMyProgress() {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        viewModelScope.launch { uploadMyProgress(me, myPhone) }
    }

    // ── Search & Request ──────────────────────────────────

    fun onPhoneInputChange(value: String) {
        _state.update { it.copy(error = null) }
        _phoneInput.value = value
    }

    fun sendRequest(toPhone: String) {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        val cleaned = toPhone.trim()

        if (cleaned.isBlank()) return
        if (cleaned == myPhone) {
            _state.update { it.copy(error = "নিজেকে Study Buddy বানানো যাবে না") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val user = repo.findUserByPhone(cleaned)
            if (user == null) {
                _state.update { it.copy(isLoading = false, error = "এই নম্বরে কোনো অ্যাকাউন্ট নেই") }
                return@launch
            }
            val ok = repo.sendBuddyRequest(me, cleaned)
            _state.update {
                it.copy(
                    isLoading = false,
                    toast = if (ok) "✅ রিকোয়েস্ট পাঠানো হয়েছে! ${user.displayName()} accept করলে জানতে পারবে।"
                            else "❌ রিকোয়েস্ট পাঠাতে সমস্যা হয়েছে, আবার চেষ্টা করো"
                )
            }
        }
    }

    fun acceptRequest(request: BuddyRequest) {
        val me = session.getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val ok = repo.acceptBuddyRequest(me, request)
            _state.update {
                it.copy(
                    isLoading = false,
                    incomingRequest = null,
                    toast = if (ok) "🎉 ${request.fromName} এখন তোমার Study Buddy!" else "❌ সমস্যা হয়েছে, আবার চেষ্টা করো"
                )
            }
            if (ok) loadAll()
        }
    }

    fun declineRequest(request: BuddyRequest) {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        viewModelScope.launch {
            repo.declineBuddyRequest(myPhone, request)
            _state.update { it.copy(incomingRequest = null) }
        }
    }

    // ── Nudge পাঠাও ────────────────────────────────────────

    fun sendNudge() {
        val me     = session.getCurrentUser() ?: return
        val buddy  = _state.value.buddy ?: return
        viewModelScope.launch {
            val ok = repo.sendNudge(me.displayName(), buddy.buddyPhone)
            _state.update {
                it.copy(toast = if (ok) "👀 ${buddy.buddyName} কে তাগাদা পাঠানো হয়েছে!"
                                 else "❌ পাঠাতে সমস্যা হয়েছে")
            }
        }
    }

    // ── Study Nav Phase 6: Study Together ───────────────────

    /** Study-তে Topic খুললে — বন্ধু থাকলে "এখন পড়ছে" জানিয়ে রাখা (বন্ধু না থাকলে কিছুই করে না) */
    fun setStudying(subject: String, topic: String) {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        if (!_state.value.hasBuddy) return
        viewModelScope.launch { repo.setStudying(myPhone, me.displayName(), subject, topic) }
    }

    fun clearStudying() {
        val myPhone = session.getCurrentUser()?.phone ?: return
        if (!_state.value.hasBuddy) return
        viewModelScope.launch { repo.clearStudying(myPhone) }
    }

    /** 🤝 "এই lessonটা একসাথে পড়বি?" */
    fun inviteToStudy(subject: String, topic: String) {
        val me    = session.getCurrentUser() ?: return
        val buddy = _state.value.buddy ?: return
        viewModelScope.launch {
            val ok = repo.sendStudyInvite(me.displayName(), buddy.buddyPhone, subject, topic)
            _state.update {
                it.copy(toast = if (ok) "🤝 ${buddy.buddyName} কে আমন্ত্রণ পাঠানো হয়েছে!" else "❌ আমন্ত্রণ পাঠাতে সমস্যা হয়েছে")
            }
        }
    }

    // ── Phase B4: Study Together (shared Focus) ─────────────

    /** চলমান session থাকলে Join, না থাকলে নতুন শুরু (২৫ মিনিট) */
    fun startOrJoinFocus() {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        val buddy = _state.value.buddy ?: return
        val pk = repo.pairKey(myPhone, buddy.buddyPhone)
        val cur = _state.value.focus
        viewModelScope.launch {
            if (cur != null && cur.active()) {
                if (repo.joinFocus(pk, me)) {
                    com.hanif.smartstudy.ui.quiz.StudyFocusTimer.startUntil("Buddy Focus", cur.endMs())
                } else _state.update { it.copy(toast = "❌ Join করা যায়নি") }
            } else {
                val minutes = com.hanif.smartstudy.ui.quiz.StudyFocusTimer.DEFAULT_MINUTES
                val start = repo.startFocus(pk, me, buddy.buddyPhone, minutes)
                if (start != null) {
                    com.hanif.smartstudy.ui.quiz.StudyFocusTimer.startUntil("Buddy Focus", start + minutes * 60_000L)
                    _state.update { it.copy(toast = "🟢 Focus শুরু — ${buddy.buddyName}-কে জানানো হয়েছে") }
                } else _state.update { it.copy(toast = "❌ শুরু করা যায়নি") }
            }
        }
    }

    fun leaveFocus() {
        val myPhone = session.getCurrentUser()?.phone ?: return
        val buddy = _state.value.buddy ?: return
        com.hanif.smartstudy.ui.quiz.StudyFocusTimer.stop()
        viewModelScope.launch { repo.leaveFocus(repo.pairKey(myPhone, buddy.buddyPhone), myPhone) }
    }

    // ── Phase B3: Shared Goal ───────────────────────────────

    private val goalPrefs = app.getSharedPreferences("buddy_goal_base", android.content.Context.MODE_PRIVATE)

    private suspend fun quizAccuracy(): Int {
        val c = cache.getCorrectCount(); val w = cache.getWrongCount()
        return if (c + w > 0) c * 100 / (c + w) else -1
    }

    /** সব মিলিয়ে মোট (cumulative) কাউন্টার — goal-এর মান = এখনকার মোট − goal শুরুর baseline */
    private suspend fun cumulative(metric: String): Int =
        if (metric == GoalMetric.MINUTES) cache.getStudyStats().third
        else cache.getCorrectCount() + cache.getWrongCount()

    private fun syncGoalProgress(myPhone: String) {
        val g = latestGoal ?: return
        if (g.id.isBlank()) return
        val buddy = _state.value.buddy ?: return
        viewModelScope.launch {
            val now = cumulative(g.metric)
            val baseKey = "${g.id}_${g.metric}"
            if (!goalPrefs.contains(baseKey)) goalPrefs.edit().putInt(baseKey, now).apply()
            val value = (now - goalPrefs.getInt(baseKey, now)).coerceAtLeast(0)
            val mine = _state.value.goalState
            val reached = if (value >= g.target) (if (mine.myReachedAt > 0L) mine.myReachedAt else System.currentTimeMillis()) else 0L
            if (value != mine.myValue || reached != mine.myReachedAt) {
                repo.updateGoalProgress(repo.pairKey(myPhone, buddy.buddyPhone), myPhone, value, reached)
            }
        }
    }

    fun createGoal(title: String, metric: String, target: Int, mode: String, days: Int) {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        val buddy = _state.value.buddy ?: return
        val g = BuddyGoal(
            id = "g${System.currentTimeMillis()}", title = title.ifBlank { "আমাদের লক্ষ্য" }, metric = metric,
            target = target.coerceAtLeast(1), mode = mode, days = days.coerceIn(1, 60),
            startMs = System.currentTimeMillis(), createdBy = myPhone, createdByName = me.displayName()
        )
        viewModelScope.launch {
            val ok = repo.saveGoal(repo.pairKey(myPhone, buddy.buddyPhone), g, buddy.buddyPhone, me.displayName())
            _state.update { it.copy(toast = if (ok) "🎯 লক্ষ্য তৈরি হয়েছে!" else "❌ লক্ষ্য তৈরি করা যায়নি") }
        }
    }

    fun clearGoal() {
        val myPhone = session.getCurrentUser()?.phone ?: return
        val buddy = _state.value.buddy ?: return
        viewModelScope.launch { repo.clearGoal(repo.pairKey(myPhone, buddy.buddyPhone)) }
    }

    // ── Phase B1: Knock ─────────────────────────────────────

    fun sendKnock(kind: String, message: String) {
        val me    = session.getCurrentUser() ?: return
        val buddy = _state.value.buddy ?: return
        viewModelScope.launch {
            val ok = repo.sendKnock(me, buddy.buddyPhone, kind, message)
            _state.update { it.copy(toast = if (ok) "👋 ${buddy.buddyName} কে knock পাঠানো হয়েছে!" else "❌ পাঠাতে সমস্যা হয়েছে") }
        }
    }

    /** status: ACCEPTED | LATER | REPLIED (reply সহ) */
    fun respondKnock(knock: BuddyKnock, status: String, reply: String = "") {
        val me = session.getCurrentUser() ?: return
        viewModelScope.launch {
            val ok = repo.respondKnock(me, knock, status, reply)
            if (ok) _state.update { st -> st.copy(knocks = st.knocks.filter { it.id != knock.id }) }
            else _state.update { it.copy(toast = "❌ উত্তর পাঠাতে সমস্যা হয়েছে") }
        }
    }

    fun postToast(msg: String) { _state.update { it.copy(toast = msg) } }

    // ── Phase B1: Buddy Streak — দুজনেই সেদিন কিছু পড়েছে এমন টানা দিন ──

    private fun dayKey(offset: Int): String {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
    }

    private fun recomputeStreak() {
        fun both(i: Int) = (myDays[dayKey(i)] ?: 0) > 0 && (buddyDays[dayKey(i)] ?: 0) > 0
        val todayBoth = both(0)
        var streak = 0
        var i = if (todayBoth) 0 else 1
        while (i < 34 && both(i)) { streak++; i++ }
        val buddyName = _state.value.buddy?.buddyName ?: "Buddy"
        val risk = when {
            streak == 0 || todayBoth -> null
            (buddyDays[dayKey(0)] ?: 0) <= 0 && (myDays[dayKey(0)] ?: 0) <= 0 -> "আজ তোমরা দুজনের কেউই এখনো পড়োনি"
            (buddyDays[dayKey(0)] ?: 0) <= 0 -> "$buddyName আজ এখনো study করেনি"
            else -> "তুমি আজ এখনো study করোনি"
        }
        _state.update { it.copy(buddyStreak = streak, streakRisk = risk) }
    }

    // ── Buddy ব্রেক করো ─────────────────────────────────────

    fun removeBuddy() {
        val me    = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        val buddy = _state.value.buddy ?: return
        viewModelScope.launch {
            repo.removeBuddy(myPhone, buddy.buddyPhone)
            _state.update {
                it.copy(hasBuddy = false, buddy = null, buddyProgress = BuddyProgress(),
                        toast = "Study Buddy সম্পর্ক শেষ করা হয়েছে")
            }
        }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun todayString(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }
}
