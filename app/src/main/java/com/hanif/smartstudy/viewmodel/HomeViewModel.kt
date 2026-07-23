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
    val isLoadingNotifs    : Boolean                = false
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
        }
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
