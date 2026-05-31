package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.data.repository.DataState
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class QuizUiState(
    val mode          : StudyMode        = StudyMode.QUIZ,
    val navPath       : NavPath          = NavPath(),
    val subjects      : List<SubjectEntry>   = emptyList(),
    val subTopics     : List<SubTopicEntry>  = emptyList(),
    val questions     : List<QuestionItem>   = emptyList(),
    val isLoading     : Boolean          = true,
    val error         : String?          = null,
    val isQuizActive  : Boolean          = false,
    val timerSec      : Int              = 0,
    val totalTimeSec  : Int              = 0,
    val answeredCount : Int              = 0,
    val result        : QuizResult?      = null,
    val showResult    : Boolean          = false,
    val isMockZone    : Boolean          = false,
    val mockConfig    : MockTestConfig   = MockTestConfig(),
    val readingIndex  : Int              = 0,
    val bookmarkedIds : Set<String>      = emptySet(),
    val weakTopics    : List<WeakTopic>  = emptyList(),
    val contentLoaded : Boolean          = false
)

class QuizViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = ContentRepository(app)
    private val cache   = ContentCache(app)
    private val session = SessionManager(app)

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private val _pendingAchievement = MutableStateFlow<Achievement?>(null)
    val pendingAchievement: StateFlow<Achievement?> = _pendingAchievement.asStateFlow()

    private val _pendingStreak = MutableStateFlow(0)
    val pendingStreak: StateFlow<Int> = _pendingStreak.asStateFlow()

    fun consumeAchievement() { _pendingAchievement.value = null }
    fun consumeStreak()      { _pendingStreak.value = 0 }

    private var timerJob: Job? = null
    private val prefs = app.getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)

    // ── Init: content load, BUT subjects build হবে setMode() call এর পরে ──
    // তাই এখানে rebuildSubjects call করি না — setMode() সেটা করবে
    init {
        viewModelScope.launch {
            val result    = repo.getContent()
            val content   = (result as? DataState.Success)?.data ?: AppContent()
            val bookmarks = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
            val weakTopics = loadWeakTopics()
            _state.update {
                it.copy(
                    contentLoaded = !content.isEmpty(),
                    bookmarkedIds = bookmarks,
                    weakTopics    = weakTopics,
                    error         = if (content.isEmpty()) (result as? DataState.Error)?.message else null
                )
            }
            // content load হলে current mode এর subjects build করো
            rebuildSubjects(content, _state.value.mode)
        }
    }

    // ── Mode set — সবসময় subjects rebuild করো ──
    fun setMode(newMode: StudyMode) {
        if (_state.value.mode == newMode && _state.value.subjects.isNotEmpty()) return
        timerJob?.cancel()
        _state.update {
            it.copy(
                mode         = newMode,
                navPath      = NavPath(),
                isQuizActive = false,
                result       = null,
                showResult   = false,
                isMockZone   = false,
                timerSec     = 0,
                subjects     = emptyList(),
                isLoading    = true
            )
        }
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            rebuildSubjects(content, newMode)
        }
    }

    // ── Navigation ──
    fun navigateToSubject(subject: String) {
        _state.update { it.copy(navPath = NavPath(subject)) }
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            rebuildSubTopics(content, subject, _state.value.mode)
        }
    }

    fun navigateToSubTopic(subTopic: String) {
        val subject = _state.value.navPath.subject ?: return
        _state.update { it.copy(navPath = NavPath(subject, subTopic)) }
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            loadQuestions(content, subject, subTopic, _state.value.mode)
        }
    }

    fun navigateBack() {
        val path = _state.value.navPath
        timerJob?.cancel()
        when {
            _state.value.isMockZone -> _state.update {
                it.copy(isMockZone = false, navPath = NavPath(), isQuizActive = false,
                        result = null, showResult = false, timerSec = 0)
            }
            _state.value.showResult -> _state.update {
                it.copy(showResult = false, isQuizActive = false, result = null,
                        navPath = NavPath(path.subject), timerSec = 0)
            }
            path.subTopic != null -> _state.update { it.copy(navPath = NavPath(path.subject)) }
            path.subject  != null -> _state.update { it.copy(navPath = NavPath()) }
            else -> {}
        }
    }

    fun openMockZone() {
        _state.update { it.copy(isMockZone = true, navPath = NavPath()) }
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            rebuildSubjects(content, _state.value.mode, forMock = true)
        }
    }

    fun toggleMockTopic(key: String) {
        val current = _state.value.mockConfig.selectedTopics.toMutableList()
        if (current.contains(key)) current.remove(key) else current.add(key)
        _state.update { it.copy(mockConfig = it.mockConfig.copy(selectedTopics = current)) }
    }

    fun setMockLimit(n: Int) {
        _state.update { it.copy(mockConfig = it.mockConfig.copy(questionLimit = n)) }
    }

    fun startMock() {
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            val cfg     = _state.value.mockConfig
            val mode    = _state.value.mode
            val pool    = when (mode) {
                StudyMode.QUIZ  -> content.quiz.map  { QuestionItem.fromQuizItem(it) }
                StudyMode.QBANK -> content.qbank.map { QuestionItem.fromQBankItem(it) }
                StudyMode.STUDY -> content.study.map { QuestionItem.fromStudyItem(it) }
            }
            val filtered = if (cfg.selectedTopics.isEmpty()) pool
            else pool.filter { q ->
                cfg.selectedTopics.any { key ->
                    val parts = key.split("||")
                    q.subject == parts.getOrNull(0) && q.subTopic == parts.getOrNull(1)
                }
            }
            val questions = filtered.shuffled().take(cfg.questionLimit)
                .map { it.copy(isWeakTopic = isWeak(it.subTopic)) }
            _state.update {
                it.copy(
                    isMockZone    = false,
                    questions     = questions,
                    isQuizActive  = true,
                    showResult    = false,
                    result        = null,
                    answeredCount = 0,
                    navPath       = NavPath("Mock Test")
                )
            }
            startTimer(questions.size)
        }
    }

    fun answerMcq(questionIndex: Int, selectedOption: Int) {
        val questions = _state.value.questions.toMutableList()
        val q = questions.getOrNull(questionIndex) ?: return
        if (q.answerState !is AnswerState.Unanswered) return

        val selectedText = when (selectedOption) {
            1 -> q.optionA; 2 -> q.optionB; 3 -> q.optionC; 4 -> q.optionD; else -> ""
        }
        val isCorrect = selectedText.trim().equals(q.answer.trim(), ignoreCase = true)
        questions[questionIndex] = q.copy(
            answerState = AnswerState.McqSelected(selectedOption, isCorrect)
        )
        _state.update { it.copy(questions = questions, answeredCount = it.answeredCount + 1) }
        viewModelScope.launch {
            if (isCorrect) cache.incrementCorrect() else {
                cache.incrementWrong()
                saveWeakTopic(q.subject, q.subTopic)
            }
            repo.submitQuizAnswer(q.id, isCorrect)
        }
    }

    fun answerWritten(questionIndex: Int, userText: String): Int {
        val questions = _state.value.questions.toMutableList()
        val q = questions.getOrNull(questionIndex) ?: return 0
        val matchPct = fuzzyMatch(userText, q.answer)
        val isCorrect = matchPct >= 70
        questions[questionIndex] = q.copy(
            answerState = AnswerState.WrittenSubmitted(userText, matchPct, isCorrect)
        )
        _state.update { it.copy(questions = questions, answeredCount = it.answeredCount + 1) }
        viewModelScope.launch {
            if (isCorrect) cache.incrementCorrect() else {
                cache.incrementWrong()
                saveWeakTopic(q.subject, q.subTopic)
            }
        }
        return matchPct
    }

    fun submitQuiz() {
        timerJob?.cancel()
        val questions  = _state.value.questions
        val totalTime  = _state.value.totalTimeSec
        val elapsed    = totalTime - _state.value.timerSec
        var correct = 0; var wrong = 0; var skipped = 0
        val subjectMap = mutableMapOf<String, SubjectScore>()

        questions.forEach { q ->
            when (val a = q.answerState) {
                is AnswerState.McqSelected      -> { if (a.isCorrect) correct++ else wrong++ }
                is AnswerState.WrittenSubmitted -> { if (a.isCorrect) correct++ else wrong++ }
                else -> skipped++
            }
            val subj = q.subject.ifBlank { "অন্যান্য" }
            val prev = subjectMap[subj] ?: SubjectScore(subj, 0, 0)
            val isC  = (q.answerState.let {
                it is AnswerState.McqSelected && it.isCorrect ||
                it is AnswerState.WrittenSubmitted && it.isCorrect
            })
            subjectMap[subj] = prev.copy(correct = prev.correct + (if (isC) 1 else 0), total = prev.total + 1)
        }

        val xp = correct * 5 + (correct - wrong).coerceAtLeast(0) * 2
        val result = QuizResult(questions.size, correct, wrong, skipped, elapsed, xp, subjectMap)
        _state.update { it.copy(result = result, showResult = true, isQuizActive = false, timerSec = 0) }

        viewModelScope.launch {
            cache.markTodayActivity()
            val user = session.getCurrentUser()
            if (user != null) {
                val updated = user.copy(xp = (user.xp + xp).coerceAtMost(999999))
                session.saveUser(updated)
            }
            session.recordDailyXp(xp)
            val streak = session.updateStreak()
            _pendingStreak.value = streak

            if (!session.hasAchievement("first_quiz")) {
                session.unlockAchievement("first_quiz")
                _pendingAchievement.value = Achievements.findById("first_quiz")
            }
            if (result.wrong == 0 && result.skipped == 0 && result.total > 0) {
                if (!session.hasAchievement("perfect_score")) {
                    session.unlockAchievement("perfect_score")
                    _pendingAchievement.value = Achievements.findById("perfect_score")
                }
            }
            listOf(3 to "streak_3", 7 to "streak_7", 30 to "streak_30").forEach { (days, id) ->
                if (streak >= days && !session.hasAchievement(id)) {
                    session.unlockAchievement(id)
                    _pendingAchievement.value = Achievements.findById(id)
                }
            }
            val bookmarkCount = _state.value.bookmarkedIds.size
            if (bookmarkCount >= 10 && !session.hasAchievement("bookmarked_10")) {
                session.unlockAchievement("bookmarked_10")
                _pendingAchievement.value = Achievements.findById("bookmarked_10")
            }
            val totalXp = (user?.xp ?: 0) + xp
            listOf(100 to "xp_100", 500 to "xp_500", 1000 to "xp_1000").forEach { (threshold, id) ->
                if (totalXp >= threshold && !session.hasAchievement(id)) {
                    session.unlockAchievement(id)
                    _pendingAchievement.value = Achievements.findById(id)
                }
            }
        }
    }

    fun toggleBookmark(questionId: String) {
        val current = _state.value.bookmarkedIds.toMutableSet()
        if (current.contains(questionId)) current.remove(questionId) else current.add(questionId)
        prefs.edit().putStringSet("bookmarks", current).apply()
        _state.update { it.copy(bookmarkedIds = current) }
    }

    fun updateReadingIndex(index: Int) {
        _state.update { it.copy(readingIndex = index) }
    }

    private fun startTimer(questionCount: Int) {
        val totalSec = questionCount * 60
        timerJob?.cancel()
        _state.update { it.copy(timerSec = totalSec, totalTimeSec = totalSec, isQuizActive = true) }
        timerJob = viewModelScope.launch {
            while (isActive && _state.value.timerSec > 0) {
                delay(1000)
                _state.update { it.copy(timerSec = (it.timerSec - 1).coerceAtLeast(0)) }
            }
            if (_state.value.timerSec <= 0 && _state.value.isQuizActive) submitQuiz()
        }
    }

    private suspend fun rebuildSubjects(content: AppContent, mode: StudyMode, forMock: Boolean = false) {
        val items = when (mode) {
            StudyMode.QUIZ  -> content.quiz.map  { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> content.qbank.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> content.study.map { QuestionItem.fromStudyItem(it) }
        }
        val progressMap = loadProgressMap()
        val subjects = items
            .filter { it.subject.isNotBlank() }   // blank subject গুলো বাদ দাও
            .groupBy { it.subject }
            .map { (subj, qs) ->
                SubjectEntry(
                    name      = subj,
                    totalQ    = qs.size,
                    doneQ     = qs.count { progressMap.contains("${mode.name}:${it.id}") },
                    subTopics = qs.filter { it.subTopic.isNotBlank() }
                                  .groupBy { it.subTopic }
                                  .map { (st, stQs) ->
                                      SubTopicEntry(
                                          name    = st,
                                          subject = subj,
                                          totalQ  = stQs.size,
                                          doneQ   = stQs.count { progressMap.contains("${mode.name}:${it.id}") },
                                          isWeak  = isWeak(st)
                                      )
                                  }
                )
            }
        _state.update { it.copy(subjects = subjects, isLoading = false) }
    }

    private suspend fun rebuildSubTopics(content: AppContent, subject: String, mode: StudyMode) {
        val items = when (mode) {
            StudyMode.QUIZ  -> content.quiz.filter  { it.subject == subject }.map { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> content.qbank.filter { it.subject == subject }.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> content.study.filter { it.subject == subject }.map { QuestionItem.fromStudyItem(it) }
        }
        val progressMap = loadProgressMap()
        val subTopics = items
            .filter { it.subTopic.isNotBlank() }
            .groupBy { it.subTopic }
            .map { (st, qs) ->
                SubTopicEntry(
                    name    = st,
                    subject = subject,
                    totalQ  = qs.size,
                    doneQ   = qs.count { progressMap.contains("${mode.name}:${it.id}") },
                    isWeak  = isWeak(st)
                )
            }
        _state.update { it.copy(subTopics = subTopics) }
    }

    private suspend fun loadQuestions(content: AppContent, subject: String, subTopic: String, mode: StudyMode) {
        val bookmarks = _state.value.bookmarkedIds
        val items = when (mode) {
            StudyMode.QUIZ  -> content.quiz.filter  { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> content.qbank.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> content.study.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromStudyItem(it) }
        }.map { it.copy(isBookmarked = bookmarks.contains(it.id), isWeakTopic = isWeak(it.subTopic)) }

        _state.update {
            it.copy(
                questions     = items,
                isQuizActive  = mode != StudyMode.STUDY,
                showResult    = false,
                result        = null,
                answeredCount = 0,
                timerSec      = 0
            )
        }
        if (mode != StudyMode.STUDY) startTimer(items.size)
    }

    private fun loadProgressMap(): Set<String> =
        prefs.getStringSet("progress", emptySet()) ?: emptySet()

    private fun saveWeakTopic(subject: String, subTopic: String) {
        if (subTopic.isBlank()) return
        val key   = "weak_$subTopic"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
    }

    private fun isWeak(subTopic: String): Boolean =
        prefs.getInt("weak_$subTopic", 0) >= 2

    private fun loadWeakTopics(): List<WeakTopic> =
        prefs.all.entries
            .filter { it.key.startsWith("weak_") && (it.value as? Int ?: 0) >= 2 }
            .map { WeakTopic(it.key.removePrefix("weak_"), "", it.value as Int) }
            .sortedByDescending { it.wrongCount }

    private fun fuzzyMatch(userText: String, correctText: String): Int {
        if (userText.isBlank()) return 0
        val uWords = userText.lowercase().split(Regex("[\\s,।.]+")).filter { it.length > 1 }.toSet()
        val cWords = correctText.lowercase().split(Regex("[\\s,।.]+")).filter { it.length > 1 }.toSet()
        if (cWords.isEmpty()) return 0
        val overlap = uWords.intersect(cWords).size
        return minOf(100, (overlap * 100) / cWords.size)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

private suspend fun ContentCache.markTodayActivity() { updateStreak() }
