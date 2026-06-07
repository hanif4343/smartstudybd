package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.data.repository.WeekendBattleRepository
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.AudienceFilter.forUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WeekendBattleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = WeekendBattleRepository()
    private val session = SessionManager(app)
    private val content = ContentRepository(app)

    private val _state = MutableStateFlow(WeekendBattleUiState())
    val state: StateFlow<WeekendBattleUiState> = _state.asStateFlow()

    private var timerJob        : Job? = null
    private var leaderboardJob  : Job? = null

    init { loadBattleInfo() }

    // ── Battle info + leaderboard লোড করো ───────────────

    fun loadBattleInfo() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                val active   = tryOrNull { repo.getActiveBattle() }
                val upcoming = if (active == null) tryOrNull { repo.getUpcomingBattle() } else null

                val battle = active ?: upcoming
                if (battle == null) {
                    _state.update { it.copy(isLoading = false, activeBattle = null, upcomingBattle = null) }
                    return@launch
                }

                val myPhone      = session.getCurrentUser()?.phone ?: ""
                val myEntry      = if (active != null) tryOrNull { repo.getMyEntry(battle.id, myPhone) } else null
                val hasSubmitted = myEntry != null

                _state.update { it.copy(
                    isLoading      = false,
                    activeBattle   = active,
                    upcomingBattle = upcoming,
                    myEntry        = myEntry,
                    hasSubmitted   = hasSubmitted
                )}

                if (active != null) {
                    observeLeaderboard(active.id, myPhone)
                }
            } catch (e: Exception) {
                Log.e("WeekendBattleVM", "loadBattleInfo error: ${e.message}", e)
                _state.update { it.copy(isLoading = false, error = "চ্যাম্পিয়নশিপ তথ্য লোড হয়নি। পুনরায় চেষ্টা করুন।") }
            }
        }
    }

    private fun observeLeaderboard(battleId: String, myPhone: String) {
        leaderboardJob?.cancel()
        leaderboardJob = viewModelScope.launch {
            try {
                repo.observeLeaderboard(battleId).collect { entries ->
                    val ranked = entries.mapIndexed { idx, e ->
                        BattleRankEntry(rank = idx + 1, entry = e, isMe = e.phone == myPhone)
                    }
                    val mine = ranked.find { it.isMe }?.entry
                    _state.update { it.copy(leaderboard = ranked, myEntry = mine ?: it.myEntry) }
                }
            } catch (e: Exception) {
                Log.e("WeekendBattleVM", "observeLeaderboard error: ${e.message}")
            }
        }
    }

    // ── Null-safe suspend helper ─────────────────────────────
    private suspend fun <T> tryOrNull(block: suspend () -> T?): T? = try { block() }
    catch (e: Exception) { Log.e("WeekendBattleVM", "tryOrNull: ${e.message}"); null }

    // ── পরীক্ষা শুরু করো ───────────────────────────────

    fun startExam() {
        val battle = _state.value.activeBattle ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                val c = ContentRepository.getMemCache()
                    ?: (content.getContent() as? com.hanif.smartstudy.data.repository.DataState.Success)?.data
                    ?: run {
                        _state.update { it.copy(isLoading = false, error = "প্রশ্ন লোড হয়নি। ইন্টারনেট চেক করুন।") }
                        return@launch
                    }

                val allQ      = c.forUser(session.getCurrentUser()).let { filtered ->
                    filtered.quiz.map { QuestionItem.fromQuizItem(it) } +
                    filtered.qbank.map { QuestionItem.fromQBankItem(it) }
                }
                val questions = tryOrNull { repo.getBattleQuestions(battle.id, allQ) }
                    ?.ifEmpty {
                        // questionIds empty হলে subject-filtered random questions নাও
                        allQ.filter {
                            battle.subject.isBlank() || it.subject == battle.subject
                        }.shuffled().take(battle.questionCount)
                    } ?: emptyList()

                if (questions.isEmpty()) {
                    _state.update { it.copy(isLoading = false, error = "এই বিষয়ে কোনো প্রশ্ন পাওয়া যায়নি।") }
                    return@launch
                }

                _state.update { it.copy(
                    isLoading     = false,
                    questions     = questions,
                    answers       = mutableMapOf(),
                    currentQIndex = 0,
                    timerSec      = battle.timeLimitSec,
                    isExamMode    = true
                )}
                startTimer(battle.timeLimitSec)
            } catch (e: Exception) {
                Log.e("WeekendBattleVM", "startExam error: ${e.message}", e)
                _state.update { it.copy(isLoading = false, error = "পরীক্ষা শুরু করতে সমস্যা হয়েছে।") }
            }
        }
    }

    fun answerQuestion(questionId: String, option: Int) {
        val answers = _state.value.answers.toMutableMap()
        answers[questionId] = option
        _state.update { it.copy(answers = answers) }
    }

    fun goToQuestion(index: Int) {
        _state.update { it.copy(currentQIndex = index.coerceIn(0, _state.value.questions.size - 1)) }
    }

    fun submitExam() {
        val s      = _state.value
        val battle = s.activeBattle ?: return
        timerJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            var score = 0
            s.questions.forEach { q ->
                val sel  = s.answers[q.id] ?: return@forEach
                val text = when (sel) { 1 -> q.optionA; 2 -> q.optionB; 3 -> q.optionC; 4 -> q.optionD; else -> "" }
                if (text.trim().equals(q.answer.trim(), ignoreCase = true)) score++
            }

            val total        = s.questions.size
            val timeTakenSec = battle.timeLimitSec - s.timerSec
            val accuracy     = if (total > 0) score * 100f / total else 0f

            // XP: score অনুযায়ী (প্রতি সঠিক = 5 XP, top 3 extra reward)
            val baseXp  = score * 5
            val xpEarned = baseXp

            val myPhone = session.getCurrentUser()?.phone ?: ""
            val myName  = session.getCurrentUser()?.displayName() ?: ""

            val ok = repo.submitBattleEntry(
                battleId     = battle.id,
                phone        = myPhone,
                name         = myName,
                score        = score,
                total        = total,
                timeTakenSec = timeTakenSec,
                xpEarned     = xpEarned
            )

            if (ok) {
                repo.awardBattleXp(myPhone, xpEarned)
                val toast = "✅ জমা দেওয়া হয়েছে! তুমি $score/$total সঠিক করেছ (+$xpEarned XP)"
                _state.update { it.copy(
                    isSubmitting = false,
                    hasSubmitted = true,
                    isExamMode   = false,
                    toast        = toast
                )}
            } else {
                _state.update { it.copy(isSubmitting = false, error = "জমা দিতে সমস্যা হয়েছে") }
            }
        }
    }

    private fun startTimer(totalSec: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                delay(1000)
                remaining--
                _state.update { it.copy(timerSec = remaining) }
            }
            if (_state.value.isExamMode) submitExam()
        }
    }

    fun dismissToast()  = _state.update { it.copy(toast = null) }
    fun dismissError()  = _state.update { it.copy(error = null) }
    fun exitExam()      { timerJob?.cancel(); _state.update { it.copy(isExamMode = false) } }

    override fun onCleared() {
        super.onCleared()
        leaderboardJob?.cancel()
        timerJob?.cancel()
    }
}
