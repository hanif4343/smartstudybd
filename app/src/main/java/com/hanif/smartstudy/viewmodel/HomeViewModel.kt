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
    val pendingSync     : Int                = 0
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
            val contentState = repo.getContent(forceRefresh)
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
                isFromCache   = fromCache
            )

            // Exam countdown live tick
            if (examCd.isSet) startCountdown()
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
