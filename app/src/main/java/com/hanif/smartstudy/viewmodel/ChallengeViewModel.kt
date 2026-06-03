package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.ChallengeRepository
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
// UI States
// ─────────────────────────────────────────────────────────

sealed class ChallengeScreen {
    object Home        : ChallengeScreen()   // Challenge hub
    object CreateSetup : ChallengeScreen()   // Step 1: invite + config
    data class Lobby(val challengeId: String) : ChallengeScreen()
    data class Exam  (val challengeId: String) : ChallengeScreen()
    data class Result(val challengeId: String) : ChallengeScreen()
}

data class ChallengeUiState(
    val screen          : ChallengeScreen       = ChallengeScreen.Home,
    // Create flow
    val phoneInput      : String                = "",
    val searchResult    : User?                 = null,
    val searchError     : String?               = null,
    val isSearching     : Boolean               = false,
    val invitedUsers    : List<User>            = emptyList(),
    val selectedSubject : String                = "",
    val selectedSubTopic: String                = "",
    val questionCount   : Int                   = 10,
    val timeLimitSec    : Int                   = 600,
    val subjects        : List<String>          = emptyList(),
    val subTopics       : List<String>          = emptyList(),
    // Lobby/Exam
    val challenge       : Challenge?            = null,
    val myPhone         : String                = "",
    val questions       : List<QuestionItem>    = emptyList(),
    val answers         : MutableMap<String, Int> = mutableMapOf(),  // questionId → selectedOption
    val currentQIndex   : Int                   = 0,
    val timerSec        : Int                   = 0,
    val isSubmitting    : Boolean               = false,
    // Invites inbox
    val pendingInvites  : List<ChallengeInvite> = emptyList(),
    // Loading / error
    val isLoading       : Boolean               = false,
    val error           : String?               = null,
    val toast           : String?               = null
)

class ChallengeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = ChallengeRepository()
    private val session = SessionManager(app)
    private val content = ContentRepository(app)

    private val _state = MutableStateFlow(ChallengeUiState())
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()

    private var challengeObserveJob : Job? = null
    private var inviteObserveJob    : Job? = null
    private var timerJob            : Job? = null

    init {
        val me = session.getCurrentUser()
        _state.update { it.copy(myPhone = me?.phone ?: "") }
        loadSubjects()
        observeInvites()
    }

    // ── Subjects from content ────────────────────────────

    private fun loadSubjects() {
        viewModelScope.launch {
            val c = ContentRepository.getMemCache() ?: content.getContent()
                .let { (it as? com.hanif.smartstudy.data.repository.DataState.Success)?.data }
            if (c == null) return@launch
            val subjects = (c.quiz.map { it.subject ?: "" } +
                            c.qbank.map { it.subject ?: "" })
                .filter { it.isNotBlank() }.distinct().sorted()
            _state.update { it.copy(subjects = subjects) }
        }
    }

    fun onSubjectSelect(subject: String) {
        viewModelScope.launch {
            val c = ContentRepository.getMemCache() ?: return@launch
            val subTopics = (c.quiz.filter { it.subject == subject }.map { it.subTopic ?: "" } +
                             c.qbank.filter { it.subject == subject }.map { it.subTopic ?: "" })
                .filter { it.isNotBlank() }.distinct().sorted()
            _state.update { it.copy(selectedSubject = subject, selectedSubTopic = "", subTopics = subTopics) }
        }
    }

    fun onSubTopicSelect(sub: String) = _state.update { it.copy(selectedSubTopic = sub) }
    fun onQuestionCountChange(n: Int) = _state.update { it.copy(questionCount = n.coerceIn(5, 30)) }
    fun onTimeLimitChange(sec: Int)   = _state.update { it.copy(timeLimitSec = sec) }
    fun onPhoneInput(phone: String)   = _state.update { it.copy(phoneInput = phone, searchResult = null, searchError = null) }

    // ── Search user ──────────────────────────────────────

    fun searchUser() {
        val phone = _state.value.phoneInput.trim()
        if (phone.isBlank()) return
        val myPhone = _state.value.myPhone
        if (phone == myPhone) {
            _state.update { it.copy(searchError = "নিজেকে invite করা যাবে না") }
            return
        }
        if (_state.value.invitedUsers.any { it.phone == phone }) {
            _state.update { it.copy(searchError = "এই ব্যবহারকারীকে ইতিমধ্যে add করা হয়েছে") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, searchError = null) }
            val user = repo.findUserByPhone(phone)
            _state.update {
                it.copy(
                    isSearching  = false,
                    searchResult = user,
                    searchError  = if (user == null) "এই নম্বরে কোনো account নেই" else null
                )
            }
        }
    }

    fun addInvitee() {
        val user = _state.value.searchResult ?: return
        _state.update { it.copy(
            invitedUsers = it.invitedUsers + user,
            searchResult = null,
            phoneInput   = "",
            toast        = "${user.displayName()} যোগ করা হয়েছে"
        )}
    }

    fun removeInvitee(phone: String) {
        _state.update { it.copy(invitedUsers = it.invitedUsers.filter { u -> u.phone != phone }) }
    }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    // ── Create challenge ─────────────────────────────────

    fun createChallenge() {
        val s = _state.value
        if (s.invitedUsers.isEmpty()) {
            _state.update { it.copy(error = "অন্তত একজনকে invite করো") }
            return
        }
        if (s.selectedSubject.isBlank()) {
            _state.update { it.copy(error = "একটি বিষয় সেলেক্ট করো") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val me      = session.getCurrentUser() ?: return@launch
            val c       = ContentRepository.getMemCache() ?: return@launch

            // Question pool থেকে random questions নাও
            val pool    = (c.quiz.filter { it.subject == s.selectedSubject &&
                            (s.selectedSubTopic.isBlank() || it.subTopic == s.selectedSubTopic) }
                            .map { QuestionItem.fromQuizItem(it) } +
                           c.qbank.filter { it.subject == s.selectedSubject &&
                            (s.selectedSubTopic.isBlank() || it.subTopic == s.selectedSubTopic) }
                            .map { QuestionItem.fromQBankItem(it) })
                .filter { it.optionA.isNotBlank() } // শুধু MCQ
                .shuffled()
                .take(s.questionCount)

            if (pool.size < s.questionCount) {
                _state.update { it.copy(isLoading = false, error = "এই বিষয়ে মাত্র ${pool.size}টি MCQ পাওয়া গেছে। প্রশ্ন সংখ্যা কমাও।") }
                return@launch
            }

            val challengeId = repo.createChallenge(
                creator       = me,
                invitees      = s.invitedUsers,
                subject       = s.selectedSubject,
                subTopic      = s.selectedSubTopic,
                questionCount = s.questionCount,
                timeLimitSec  = s.timeLimitSec,
                questionIds   = pool.map { it.id }
            )

            if (challengeId != null) {
                _state.update { it.copy(isLoading = false, screen = ChallengeScreen.Lobby(challengeId)) }
                observeChallenge(challengeId)
            } else {
                _state.update { it.copy(isLoading = false, error = "Challenge তৈরি করতে সমস্যা হয়েছে") }
            }
        }
    }

    // ── Respond to invite ────────────────────────────────

    fun respondToInvite(challengeId: String, accept: Boolean) {
        viewModelScope.launch {
            val myPhone = _state.value.myPhone
            repo.deleteInvite(myPhone, challengeId)
            val success = repo.respondToChallenge(challengeId, myPhone, accept)
            if (success && accept) {
                _state.update { it.copy(screen = ChallengeScreen.Lobby(challengeId)) }
                observeChallenge(challengeId)
            } else if (!accept) {
                _state.update { it.copy(toast = "Challenge decline করা হয়েছে") }
            }
        }
    }

    // ── Observe challenge realtime ────────────────────────

    fun observeChallenge(challengeId: String) {
        challengeObserveJob?.cancel()
        challengeObserveJob = viewModelScope.launch {
            repo.observeChallenge(challengeId).collect { challenge ->
                if (challenge == null) return@collect
                val prev = _state.value.challenge

                _state.update { it.copy(challenge = challenge) }

                // WAITING → ACTIVE: সবাই accept হলে creator start করবে
                if (challenge.getStatus() == ChallengeStatus.WAITING &&
                    challenge.allAccepted() &&
                    challenge.creatorPhone == _state.value.myPhone) {
                    repo.startChallenge(challengeId)
                }

                // ACTIVE → Exam screen
                if (challenge.getStatus() == ChallengeStatus.ACTIVE &&
                    prev?.getStatus() != ChallengeStatus.ACTIVE &&
                    _state.value.screen !is ChallengeScreen.Exam) {
                    loadExamQuestions(challenge)
                }

                // All submitted → Result screen
                if (challenge.allSubmitted() &&
                    _state.value.screen !is ChallengeScreen.Result) {
                    _state.update { it.copy(screen = ChallengeScreen.Result(challengeId)) }
                    timerJob?.cancel()
                }
            }
        }
    }

    private fun loadExamQuestions(challenge: Challenge) {
        viewModelScope.launch {
            val c = ContentRepository.getMemCache() ?: return@launch
            val allQ = (c.quiz.map { QuestionItem.fromQuizItem(it) } +
                        c.qbank.map { QuestionItem.fromQBankItem(it) })
            val questions = challenge.questionIds.mapNotNull { id ->
                allQ.find { it.id == id }
            }
            _state.update {
                it.copy(
                    questions    = questions,
                    currentQIndex = 0,
                    timerSec     = challenge.timeLimitSec,
                    screen       = ChallengeScreen.Exam(challenge.id)
                )
            }
            startExamTimer(challenge.id, challenge.timeLimitSec)
        }
    }

    // ── Exam actions ──────────────────────────────────────

    fun answerQuestion(questionId: String, selectedOption: Int) {
        val answers = _state.value.answers.toMutableMap()
        answers[questionId] = selectedOption
        _state.update { it.copy(answers = answers) }

        // Firebase 에 live progress 업데이트
        viewModelScope.launch {
            val idx = _state.value.currentQIndex
            repo.updateProgress(_state.value.challenge?.id ?: return@launch, _state.value.myPhone, idx)
        }
    }

    fun goToQuestion(index: Int) {
        _state.update { it.copy(currentQIndex = index.coerceIn(0, _state.value.questions.size - 1)) }
    }

    fun submitExam() {
        val s = _state.value
        val challenge = s.challenge ?: return
        timerJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            var score = 0
            val correctIds = mutableListOf<String>()
            s.questions.forEach { q ->
                val selected = s.answers[q.id] ?: return@forEach
                val selectedText = when (selected) {
                    1 -> q.optionA; 2 -> q.optionB; 3 -> q.optionC; 4 -> q.optionD; else -> ""
                }
                if (selectedText.trim().equals(q.answer.trim(), ignoreCase = true)) {
                    score++
                    correctIds.add(q.id)
                }
            }
            repo.submitChallenge(challenge.id, s.myPhone, score, correctIds)
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun startExamTimer(challengeId: String, totalSec: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                delay(1000)
                remaining--
                _state.update { it.copy(timerSec = remaining) }
            }
            // Time up — auto submit
            if (_state.value.screen is ChallengeScreen.Exam) submitExam()
        }
    }

    // ── Observe invites ───────────────────────────────────

    private fun observeInvites() {
        val phone = session.getCurrentUser()?.phone ?: return
        inviteObserveJob?.cancel()
        inviteObserveJob = viewModelScope.launch {
            repo.observeMyInvites(phone).collect { invites ->
                _state.update { it.copy(pendingInvites = invites) }
            }
        }
    }

    // ── Navigation ────────────────────────────────────────

    fun openCreateSetup() = _state.update { it.copy(screen = ChallengeScreen.CreateSetup, error = null) }
    fun goHome()          { challengeObserveJob?.cancel(); timerJob?.cancel()
        _state.update { it.copy(screen = ChallengeScreen.Home, challenge = null, questions = emptyList(), answers = mutableMapOf()) } }

    override fun onCleared() {
        super.onCleared()
        challengeObserveJob?.cancel()
        inviteObserveJob?.cancel()
        timerJob?.cancel()
    }
}
