package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.data.repository.DataState
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── Home Screen সম্পূর্ণ UI State ──
data class HomeUiState(
    val isLoading       : Boolean            = true,
    val error           : String?            = null,
    val user            : User?              = null,
    val xpInfo          : XpInfo             = XpInfo(),
    val streakInfo      : StreakInfo         = StreakInfo(),
    val goalProgress    : GoalProgress       = GoalProgress(),
    val studyStats      : StudyStats         = StudyStats(),
    val examCountdown   : ExamCountdown      = ExamCountdown(),
    val dailyQuote      : MotivationalQuote  = MotivationalQuote.ofDay(),
    val content         : AppContent         = AppContent(),
    val isOffline       : Boolean            = false,
    val isFromCache     : Boolean            = false,
    val pendingSync     : Int                = 0,
    // 🔔 Notification inbox
    val notifications      : List<AppNotification> = emptyList(),
    val unreadNotifCount   : Int                    = 0,
    val isLoadingNotifs    : Boolean                = false,
    // ── App feature (এডমিন-অনলি): Home-এ "কোথায় কমতি হয়েছে" ড্যাশবোর্ড —
    // CDN (manifest.json-এ publish হওয়া count) বনাম App (এই মুহূর্তে ডিভাইসে
    // cache/lazy-load হওয়া count) — key = "Quiz"|"QBank"|"Study"।
    // 🐛 ফিক্স: আগে এখানে "Sheet" (আসল Google Sheet raw row count, GAS দিয়ে) নামে
    // আরেকটা সোর্সও ছিল — বড় শিটে (~১৪,০০০+ রো) সেই GAS কল-ই ছিল app hang-এর মূল
    // কারণ, তাই সম্পূর্ণ সরিয়ে দেওয়া হলো (ব্যবহারকারীর সিদ্ধান্তে) — শুধু CDN/App থাকছে। ──
    val cdnCounts           : Map<String, Int>  = emptyMap(),
    val cdnConfigured      : Boolean           = true,   // false হলে UI-তে "CDN কনফিগার নেই" দেখাবে
    val isLoadingAdminCounts: Boolean          = false,
    // ── Admin-only "🔄 Force Full Resync" (Home হেডারের top-right) — দেখো
    // ContentRepository.forceFullResync()-এর কমেন্ট। isRefreshingAll পুরো বাটনে
    // স্পিনার দেখায়, forceResyncMsg শেষ হলে ছোট ফিডব্যাক (কয়েক সেকেন্ড পর/ট্যাপে বন্ধ)। ──
    val isRefreshingAll    : Boolean           = false,
    val forceResyncMsg     : String?           = null
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = ContentRepository(app)
    private val session = SessionManager(app)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Countdown timer
    private var countdownJob: Job? = null

    init {
        loadHomeData()
    }

    // ── Home data সব একসাথে load ──
    fun loadHomeData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // User session
            val user = session.getCurrentUser()

            // Parallel data load
            val xpInfo       = repo.getXpInfo()
            val streakInfo   = repo.getStreakInfo()
            val goalProgress = repo.getGoalProgress()
            val studyStats   = repo.getStudyStats()
            val examCd       = repo.getExamCountdown()
            val quote        = MotivationalQuote.ofDay()

            // Content fetch
            val contentState = repo.getContent(
                forceRefresh = forceRefresh,
                onBackgroundUpdate = { freshData ->
                    // Background এ নতুন data এলে home screen silently update
                    viewModelScope.launch { loadHomeData(forceRefresh = false) }
                }
            )
            val content      = (contentState as? DataState.Success)?.data ?: AppContent()
            val isOffline    = (contentState as? DataState.Success)?.isOffline ?: false
            val fromCache    = (contentState as? DataState.Success)?.fromCache ?: false
            val error        = (contentState as? DataState.Error)?.message

            _uiState.value = HomeUiState(
                isLoading     = false,
                error         = error,
                user          = user,
                xpInfo        = xpInfo,
                streakInfo    = streakInfo,
                goalProgress  = goalProgress,
                studyStats    = studyStats,
                examCountdown = examCd,
                dailyQuote    = quote,
                content       = content,
                isOffline     = isOffline,
                isFromCache   = fromCache,
                // notifications পুরনো state থেকেই রাখা হলো — নইলে প্রতিবার
                // refresh এ badge count ও লিস্ট মুহূর্তের জন্য উবে যায়
                notifications    = _uiState.value.notifications,
                unreadNotifCount = _uiState.value.unreadNotifCount
            )

            // Exam countdown live tick
            if (examCd.isSet) startCountdown()

            // 🔔 badge count-এর জন্য ব্যাকগ্রাউন্ডে notification ও রিফ্রেশ করো
            loadNotifications()

            // ── App feature (এডমিন-অনলি): Sheet/CDN/App count ড্যাশবোর্ড —
            // আলাদা coroutine-এ fire-and-forget (loadHomeData()-কে ব্লক করে
            // না, তাই issue #২-এর মতো ধীরগতির ঝুঁকি নেই — এডমিন-অনলি এক্সট্রা
            // নেটওয়ার্ক কল, বাকি সবার জন্য কোনো প্রভাব নেই) ──
            if (user?.isAdmin() == true) loadAdminContentCounts()
        }
    }

    /**
     * App feature (এডমিন-অনলি Home ড্যাশবোর্ড, "কোন জায়গায় কমতি হয়েছে"):
     * ১) CDN — CDN Worker-এর manifest.json (topic_id প্রিফিক্স QZ/QB/ST দিয়ে
     *          গ্রুপ করে count যোগ করা হয়) — কনফিগার না থাকলে cdnConfigured=false
     * ২) App — এই মুহূর্তে ডিভাইসে cache/lazy-load হয়ে থাকা content (HomeUiState.content)
     *
     * 🐛 ফিক্স (App hang/crash — "Sheet, CDN, App question count এর জন্য"):
     * আগে এখানে GAS-এর `countOrphanQuestions` অ্যাকশনও কল হতো, যেটা Quiz/QBank/Study
     * তিনটা শিটেরই **পুরো ডেটা** (getDataRange().getValues()) পড়ত — শিট এখন ~১৪,০০০+
     * রো-তে পৌঁছে যাওয়ায় এই একটা কলই GAS-সাইডে অনেক সময় নিতো (readTimeout ২৮০ সেকেন্ড
     * পর্যন্ত সেট করা ছিল ঠিক এই কারণেই), আর এটা Home-এ যতবার আসা হতো ততবার অটো-চলত —
     * এটাই মূল hang-এর উৎস ছিল। ব্যবহারকারীর সিদ্ধান্তে Sheet count সম্পূর্ণ সরিয়ে দেওয়া
     * হলো — শুধু CDN (হালকা manifest.json) আর App (লোকাল, নেটওয়ার্কই লাগে না) রইলো, তাই
     * এখন এই ড্যাশবোর্ড সবসময় দ্রুত। ── */
    fun loadAdminContentCounts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAdminCounts = true)

            val cdnConfigured = com.hanif.smartstudy.data.remote.CdnService.isConfigured()
            val cdnCounts = if (!cdnConfigured) emptyMap() else {
                val manifest = com.hanif.smartstudy.data.remote.CdnService.fetchManifest()
                if (manifest == null) emptyMap() else {
                    val out = mutableMapOf("Quiz" to 0, "QBank" to 0, "Study" to 0)
                    for ((topicId, entry) in manifest.topics) {
                        val sheetName = when {
                            topicId.startsWith("QZ") -> "Quiz"
                            topicId.startsWith("QB") -> "QBank"
                            topicId.startsWith("ST") -> "Study"
                            else -> null
                        } ?: continue
                        out[sheetName] = (out[sheetName] ?: 0) + entry.count
                    }
                    out
                }
            }

            _uiState.value = _uiState.value.copy(
                cdnCounts            = cdnCounts,
                cdnConfigured        = cdnConfigured,
                isLoadingAdminCounts = false
            )
        }
    }

    /**
     * ── Admin-only "🔄 Force Full Resync" (Home হেডারের top-right বাটন): Room-এর
     * `questions` টেবিল + `topic_sync` কার্সার + সব reference টেবিল খালি করে CDN থেকে
     * পুরোপুরি টাটকা টেনে আনে। দেখো ContentRepository.forceFullResync()-এর বিস্তারিত
     * কমেন্ট। AppDatabase singleton (getInstance) বলে এই HomeViewModel-এর নিজের
     * ContentRepository ইনস্ট্যান্স দিয়ে কল করলেও effect পুরো অ্যাপ-জুড়েই প্রযোজ্য হয়
     * (Quiz/QBank/Study ভিউমডেল অন্য instance হলেও একই Room DB শেয়ার করে) — সেই সাথে
     * loadHomeData(forceRefresh=true) দিয়ে Home-এর নিজের ক্যাশও রিফ্রেশ হয়। ──
     */
    fun forceFullResync() {
        if (_uiState.value.isRefreshingAll) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingAll = true, forceResyncMsg = null)
            val ok = repo.forceFullResync()
            loadHomeData(forceRefresh = true)
            loadAdminContentCounts()
            _uiState.value = _uiState.value.copy(
                isRefreshingAll = false,
                forceResyncMsg  = if (ok) "✅ ক্যাশ মুছে CDN থেকে টাটকা ডেটা আনা হয়েছে"
                                  else "❌ রিফ্রেশ ব্যর্থ হয়েছে — ইন্টারনেট চেক করো"
            )
        }
    }

    /** forceResyncMsg ব্যানার dismiss/auto-hide করার জন্য */
    fun clearForceResyncMsg() {
        _uiState.value = _uiState.value.copy(forceResyncMsg = null)
    }

    // ── 🔔 Notification inbox লোড করো — home load-এর সময়েও (শুধু badge count এর
    // জন্য) আর 🔔 আইকনে চাপলেও (পুরো লিস্ট দেখানোর জন্য) ──
    fun loadNotifications() {
        val phone = _uiState.value.user?.phone ?: session.getCurrentUser()?.phone
        if (phone.isNullOrBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingNotifs = true)
            val list = com.hanif.smartstudy.data.remote.FirebaseDataService.getNotifications(phone)
            _uiState.value = _uiState.value.copy(
                notifications    = list,
                unreadNotifCount = list.count { !it.read },
                isLoadingNotifs  = false
            )
        }
    }

    // ── একটা notification "পড়া হয়েছে" মার্ক করো (ট্যাপ করলে) — optimistic
    // UI আপডেট আগে করে দেয়, তারপর ব্যাকগ্রাউন্ডে Firebase এ পাঠায় ──
    fun markNotificationRead(key: String) {
        val phone = _uiState.value.user?.phone ?: return
        val current = _uiState.value.notifications
        val already = current.find { it.key == key }?.read == true
        if (already) return
        val updated = current.map { if (it.key == key) it.copy(read = true) else it }
        _uiState.value = _uiState.value.copy(
            notifications    = updated,
            unreadNotifCount = updated.count { !it.read }
        )
        viewModelScope.launch {
            com.hanif.smartstudy.data.remote.FirebaseDataService.markNotificationRead(phone, key)
        }
    }

    // ── সব কটাকে একসাথে "পড়া হয়েছে" মার্ক করো ──
    fun markAllNotificationsRead() {
        val phone = _uiState.value.user?.phone ?: return
        val current = _uiState.value.notifications
        val unreadKeys = current.filter { !it.read }.map { it.key }
        if (unreadKeys.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            notifications    = current.map { it.copy(read = true) },
            unreadNotifCount = 0
        )
        viewModelScope.launch {
            com.hanif.smartstudy.data.remote.FirebaseDataService.markAllNotificationsRead(phone, unreadKeys)
        }
    }

    // ── User নাম সম্পাদনা ──
    fun updateUserName(name: String) {
        viewModelScope.launch {
            val user = session.getCurrentUser() ?: return@launch
            val updated = user.copy(name = name)
            session.saveUser(updated)
            _uiState.value = _uiState.value.copy(user = updated)
        }
    }

    // ── Daily Goal set ──
    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch {
            session.setDailyGoal(minutes)
            val goal = repo.getGoalProgress()
            _uiState.value = _uiState.value.copy(goalProgress = goal)
        }
    }

    // ── Exam date set ──
    fun setExamDate(date: String, name: String) {
        repo.saveExamDate(date, name)
        val cd = repo.getExamCountdown()
        _uiState.value = _uiState.value.copy(examCountdown = cd)
        if (cd.isSet) startCountdown()
    }

    // ── Exam date clear (টাইমার শেষ বা user cancel) ──
    fun clearExamDate() {
        repo.clearExamDate()
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(examCountdown = ExamCountdown())
    }

    // ── Study session complete ──
    fun onStudySessionComplete(minutes: Int, topic: String) {
        viewModelScope.launch {
            repo.submitStudyProgress(minutes, topic)
            // stats refresh
            val stats = repo.getStudyStats()
            val goal  = repo.getGoalProgress()
            val streak= repo.getStreakInfo()
            _uiState.value = _uiState.value.copy(
                studyStats   = stats,
                goalProgress = goal,
                streakInfo   = streak
            )
        }
    }

    // ── Quiz answer ──
    fun onQuizAnswer(questionId: String, isCorrect: Boolean) {
        viewModelScope.launch {
            repo.submitQuizAnswer(questionId, isCorrect)
            val stats = repo.getStudyStats()
            _uiState.value = _uiState.value.copy(studyStats = stats)
        }
    }

    // ── Refresh ──
    fun refresh() = loadHomeData(forceRefresh = true)

    // ── Exam countdown live tick ──
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val cd = repo.getExamCountdown()
                _uiState.value = _uiState.value.copy(examCountdown = cd)
                if (!cd.isSet || (cd.days == 0L && cd.hours == 0L && cd.minutes == 0L && cd.seconds == 0L)) break
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
