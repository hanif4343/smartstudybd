package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.local.TestHistoryCache
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.data.repository.DataState
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.AudienceFilter.filterForUser
import com.hanif.smartstudy.util.AudienceFilter.forUser
import kotlinx.coroutines.*
import kotlinx.coroutines.withTimeoutOrNull
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
    val contentLoaded : Boolean          = false,
    val highlightQuestionId : String?    = null,
    // ── Admin: ইনলাইন ক্রম সাজানো (Subject/SubTopic list screen-এই ▲▼ বাটন) ──
    val isAdmin        : Boolean         = false,
    val isReorderMode  : Boolean         = false,   // ▲▼ বাটন দেখানো হবে কিনা (admin টগল করে)
    val isSavingOrder  : Boolean         = false,
    val orderSavedMsg  : String?         = null
)

class QuizViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        // সঠিক উত্তরে XP — written এ একটু বেশি (বেশি effort লাগে)
        private const val XP_PER_CORRECT_MCQ     = 2
        private const val XP_PER_CORRECT_WRITTEN = 3
    }


    private val repo    = ContentRepository(app)
    private val cache   = ContentCache(app)
    private val session = SessionManager(app)
    private val historyCache = TestHistoryCache(app)

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private val _pendingAchievement = MutableStateFlow<Achievement?>(null)
    val pendingAchievement: StateFlow<Achievement?> = _pendingAchievement.asStateFlow()

    private val _pendingStreak = MutableStateFlow(0)
    val pendingStreak: StateFlow<Int> = _pendingStreak.asStateFlow()

    // Sound + Vibration এর জন্য — true=সঠিক, false=ভুল, null=কোনো event নেই
    private val _feedbackEvent = MutableStateFlow<Boolean?>(null)
    val feedbackEvent: StateFlow<Boolean?> = _feedbackEvent.asStateFlow()
    fun clearFeedback() { _feedbackEvent.value = null }

    fun consumeAchievement() { _pendingAchievement.value = null }
    fun consumeStreak()      { _pendingStreak.value = 0 }

    private var timerJob: Job? = null
    private var loadJob: Job? = null   // cancellable load job
    private val prefs = app.getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)

    // init এ কিছু করি না — setMode() call আসার জন্য অপেক্ষা
    // MainScreen থেকে LaunchedEffect(Unit) { vm.setMode(...) } call হবে

    // ── setMode: সব কিছুর শুরু ──
    fun setMode(newMode: StudyMode) {
        Log.d("QuizVM", "setMode($newMode) called, current=${_state.value.mode}")

        // আগের load cancel করো
        loadJob?.cancel()

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
                subTopics    = emptyList(),
                isLoading    = true,
                error        = null
            )
        }

        loadJob = viewModelScope.launch {
            val bookmarks  = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
            val weakTopics = loadWeakTopics()
            val isAdmin    = session.getCurrentUser()?.isAdmin() == true
            _state.update { it.copy(bookmarkedIds = bookmarks, weakTopics = weakTopics, isAdmin = isAdmin) }

            // 30 সেকেন্ড timeout
            val result = withTimeoutOrNull(30_000L) { repo.getContent() }
                ?: DataState.Error("টাইমআউট — ৩০ সেকেন্ডে data আসেনি। Internet ও Secrets চেক করো।")

            val content = (result as? DataState.Success)?.data ?: AppContent()
            val errMsg  = (result as? DataState.Error)?.message

            Log.d("QuizVM", "Content loaded for $newMode: quiz=${content.quiz.size} study=${content.study.size} qbank=${content.qbank.size} err=$errMsg")

            val debugMsg = when {
                content.isEmpty() -> "❌ Data empty! err=$errMsg quiz=${content.quiz.size} study=${content.study.size} qbank=${content.qbank.size}"
                else -> "✅ quiz=${content.quiz.size} study=${content.study.size} qbank=${content.qbank.size}"
            }
            Log.d("QuizVM_DEBUG", debugMsg)

            _state.update {
                it.copy(
                    contentLoaded = !content.isEmpty(),
                    error         = if (content.isEmpty()) (errMsg ?: "Data empty") else null
                )
            }
            rebuildSubjects(content, newMode)
        }
    }

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

    /** নোটিফিকেশন থেকে এসে নির্দিষ্ট প্রশ্নে সরাসরি navigate করো */
    fun navigateToQuestion(questionId: String) {
        if (questionId.isBlank()) return
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            val pool = when (_state.value.mode) {
                StudyMode.QUIZ  -> content.quiz.map  { QuestionItem.fromQuizItem(it) }
                StudyMode.QBANK -> content.qbank.map { QuestionItem.fromQBankItem(it) }
                StudyMode.STUDY -> content.study.map { QuestionItem.fromStudyItem(it) }
            }
            val target = pool.find { it.id == questionId } ?: return@launch
            _state.update {
                it.copy(navPath = NavPath(target.subject, target.subTopic), highlightQuestionId = questionId)
            }
            loadQuestions(content, target.subject, target.subTopic, _state.value.mode)
        }
    }

    fun consumeHighlight() {
        _state.update { it.copy(highlightQuestionId = null) }
    }

    /**
     * Admin edit করার পর এই function call হয়। আগে এটা পুরো cache clear করে
     * setMode() call করত — যেটা navPath রিসেট করে দিত, ফলে admin যেই
     * subject/subTopic/question স্ক্রিনে ছিল সেখান থেকে subject list এ
     * ছিটকে যেত।
     *
     * এখন: in-memory content (যেটা ইতিমধ্যে ViewModel এর সাথে patch হয়ে গেছে)
     * থেকে navPath অপরিবর্তিত রেখেই শুধু বর্তমান স্ক্রিনের ডাটা rebuild করা হয়।
     * Admin ঠিক যেখানে ছিল সেখানেই থাকে, আর edit করা কনটেন্ট সাথে সাথে
     * স্ক্রিনে দেখা যায় — কোনো reload/navigation jump ছাড়াই।
     */
    fun adminRefreshContent() {
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            val path    = _state.value.navPath
            when {
                path.subTopic != null && path.subject != null ->
                    refreshQuestionsInPlace(content, path.subject, path.subTopic, _state.value.mode)
                path.subject != null ->
                    rebuildSubTopics(content, path.subject, _state.value.mode)
                else ->
                    rebuildSubjects(content, _state.value.mode)
            }
        }
    }

    /**
     * loadQuestions() এর মতো পুরো question list reset করে না — timer, answered
     * count, result কিছুই ছোঁয় না। শুধু প্রতিটি প্রশ্নের টেক্সট/অপশন/উত্তর
     * নতুন content দিয়ে আপডেট করে দেয় (id ধরে ধরে), answerState/bookmark এর
     * মতো runtime state অপরিবর্তিত রাখে। তাই admin চলমান quiz/qbank/study
     * স্ক্রিনে কোনো প্রশ্ন এডিট করলে মাঝপথে timer রিস্টার্ট হয় না বা উত্তর
     * দেওয়া প্রশ্নগুলো আনআনসারড হয়ে যায় না।
     */
    private suspend fun refreshQuestionsInPlace(content: AppContent, subject: String, subTopic: String, mode: StudyMode) {
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered = content.forUser(user, adminTag)
        val fresh = when (mode) {
            StudyMode.QUIZ  -> filtered.quiz.filter  { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> filtered.qbank.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> filtered.study.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromStudyItem(it) }
        }.associateBy { it.id }

        _state.update { st ->
            st.copy(questions = st.questions.map { existing ->
                fresh[existing.id]?.copy(
                    answerState  = existing.answerState,
                    isBookmarked = existing.isBookmarked,
                    isWeakTopic  = existing.isWeakTopic
                ) ?: existing
            })
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
            _state.value.showResult -> {
                _state.update {
                    it.copy(showResult = false, isQuizActive = false, result = null,
                            navPath = NavPath(path.subject), timerSec = 0)
                }
                // উত্তর দেওয়ার পর progress আপডেট হয়েছে — subTopic list রিফ্রেশ করো
                if (path.subject != null) {
                    viewModelScope.launch {
                        val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
                        rebuildSubTopics(content, path.subject, _state.value.mode)
                    }
                }
            }
            path.subTopic != null -> {
                _state.update { it.copy(navPath = NavPath(path.subject)) }
                if (path.subject != null) {
                    viewModelScope.launch {
                        val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
                        rebuildSubTopics(content, path.subject, _state.value.mode)
                    }
                }
            }
            path.subject  != null -> {
                _state.update { it.copy(navPath = NavPath()) }
                viewModelScope.launch {
                    val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
                    rebuildSubjects(content, _state.value.mode)
                }
            }
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

    /**
     * রুটিন থেকে "এখন টেস্ট দাও" — নির্দিষ্ট subject/subTopic এর উপর সরাসরি
     * Mock Test শুরু করো। subTopic ফাঁকা থাকলে ওই subject এর সব subTopic
     * সিলেক্ট হবে।
     */
    fun startInstantTestFor(subject: String, subTopic: String, limit: Int = 10) {
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            rebuildSubjects(content, _state.value.mode, forMock = true)

            val subjectEntry = _state.value.subjects.find { it.name == subject }
            val keys = if (subTopic.isNotBlank()) {
                listOf("$subject||$subTopic")
            } else if (subjectEntry?.subTopics?.isNotEmpty() == true) {
                subjectEntry.subTopics.map { "$subject||${it.name}" }
            } else {
                // এই বিষয়ে কোনো SubTopic নেই — সরাসরি subject-ভিত্তিক প্রশ্ন (subTopic ফাঁকা)
                listOf("$subject||")
            }

            _state.update {
                it.copy(
                    isMockZone = true,
                    navPath    = NavPath(),
                    mockConfig = it.mockConfig.copy(
                        selectedTopics = keys,
                        questionLimit  = limit
                    )
                )
            }
            startMock()
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
                it.copy(isMockZone = false, questions = questions, isQuizActive = true,
                        showResult = false, result = null, answeredCount = 0,
                        navPath = NavPath("Mock Test"))
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
        val isCorrect = selectedText.trim().equals(resolveCorrectText(q).trim(), ignoreCase = true)
        questions[questionIndex] = q.copy(answerState = AnswerState.McqSelected(selectedOption, isCorrect))
        _state.update { it.copy(questions = questions, answeredCount = it.answeredCount + 1) }
        _feedbackEvent.value = isCorrect
        markProgress(q.id, _state.value.mode)
        viewModelScope.launch {
            if (isCorrect) {
                cache.incrementCorrect()
                removeWrongQIdByMode(q.id, _state.value.mode)   // সঠিক হলে remove
                // STUDY mode এ per-answer XP award — QUIZ mode এ submitQuiz() এ bulk award হয় (double নয়)
                if (_state.value.mode == StudyMode.STUDY) {
                    session.getCurrentUser()?.phone?.let { phone ->
                        repo.awardXp(phone, XP_PER_CORRECT_MCQ)
                    }
                }
            } else {
                cache.incrementWrong()
                saveWeakTopic(q.subject, q.subTopic)
                saveWrongQId(q.id, _state.value.mode)     // ভুল হলে save
            }
            repo.submitQuizAnswer(q.id, isCorrect)
        }
    }

    fun answerWritten(questionIndex: Int, userText: String): Int {
        val questions = _state.value.questions.toMutableList()
        val q = questions.getOrNull(questionIndex) ?: return 0
        val matchPct = fuzzyMatch(userText, q.answer)
        val isCorrect = matchPct >= 70
        questions[questionIndex] = q.copy(answerState = AnswerState.WrittenSubmitted(userText, matchPct, isCorrect))
        _state.update { it.copy(questions = questions, answeredCount = it.answeredCount + 1) }
        _feedbackEvent.value = isCorrect
        markProgress(q.id, _state.value.mode)
        viewModelScope.launch {
            if (isCorrect) {
                cache.incrementCorrect()
                removeWrongQIdByMode(q.id, _state.value.mode)
                // STUDY mode এ per-answer XP award — QUIZ mode এ submitQuiz() এ bulk award হয়
                if (_state.value.mode == StudyMode.STUDY) {
                    session.getCurrentUser()?.phone?.let { phone ->
                        repo.awardXp(phone, XP_PER_CORRECT_WRITTEN)
                    }
                }
            } else {
                cache.incrementWrong()
                saveWeakTopic(q.subject, q.subTopic)
                saveWrongQId(q.id, _state.value.mode)
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
            val isC  = q.answerState.let { it is AnswerState.McqSelected && it.isCorrect || it is AnswerState.WrittenSubmitted && it.isCorrect }
            subjectMap[subj] = prev.copy(correct = prev.correct + (if (isC) 1 else 0), total = prev.total + 1)
        }

        val xp = correct * 5 + (correct - wrong).coerceAtLeast(0) * 2
        val result = QuizResult(questions.size, correct, wrong, skipped, elapsed, xp, subjectMap)
        _state.update { it.copy(result = result, showResult = true, isQuizActive = false, timerSec = 0) }

        // ── "এখন টেস্ট দাও" (Mock Test) রেজাল্ট হিস্ট্রিতে জমা রাখো ──
        if (_state.value.navPath.subject == "Mock Test" && result.total > 0) {
            viewModelScope.launch {
                val cfg = _state.value.mockConfig
                val topicLabels = if (cfg.selectedTopics.isEmpty()) {
                    listOf("সব বিষয় (র‍্যান্ডম)")
                } else {
                    cfg.selectedTopics.map { key ->
                        val parts = key.split("||")
                        val subj  = parts.getOrNull(0) ?: ""
                        val sub   = parts.getOrNull(1)
                        if (!sub.isNullOrBlank()) "$subj - $sub" else subj
                    }
                }
                historyCache.addEntry(result.toHistoryEntry(_state.value.mode.name, topicLabels))
            }
        }

        viewModelScope.launch {
            cache.markTodayActivity()
            val user = session.getCurrentUser()
            session.recordDailyXp(xp)
            // awardXp() local session + Firebase RTDB দুটোই update করে (atomic transaction)
            user?.phone?.let { phone -> repo.awardXp(phone, xp) }
            // cache.markTodayActivity() ইতিমধ্যে streak update করেছে (streak_days key) —
            // session.updateStreak() shared streak_last_date দেখে "already today" ভেবে increment করে না,
            // তাই সরাসরি cache থেকে পড়ো
            val streak = cache.getStreak()
            _pendingStreak.value = streak

            checkAndUnlock("first_quiz")
            if (result.wrong == 0 && result.skipped == 0 && result.total > 0) checkAndUnlock("perfect_score")
            listOf(3 to "streak_3", 7 to "streak_7", 30 to "streak_30").forEach { (days, id) -> if (streak >= days) checkAndUnlock(id) }
            val bCount = _state.value.bookmarkedIds.size
            if (bCount >= 10) checkAndUnlock("bookmarked_10")
            val totalXp = (user?.xp ?: 0) + xp
            listOf(100 to "xp_100", 500 to "xp_500", 1000 to "xp_1000").forEach { (t, id) -> if (totalXp >= t) checkAndUnlock(id) }
        }
    }

    private suspend fun checkAndUnlock(id: String) {
        if (!session.hasAchievement(id)) {
            session.unlockAchievement(id)
            _pendingAchievement.value = Achievements.findById(id)
        }
    }

    fun reportQuestion(questionIndex: Int, issue: String) {
        val q    = _state.value.questions.getOrNull(questionIndex) ?: return
        val user = session.getCurrentUser()
        val tab  = when (_state.value.mode) {
            StudyMode.QUIZ  -> "quiz"
            StudyMode.QBANK -> "qbank"
            StudyMode.STUDY -> "study"
        }
        val qsheet = when (_state.value.mode) {
            StudyMode.QUIZ  -> "Quiz"
            StudyMode.QBANK -> "QBank"
            StudyMode.STUDY -> "Study"
        }
        viewModelScope.launch {
            try {
                com.hanif.smartstudy.data.remote.FirebaseDataService.reportQuestion(
                    questionId = q.id,
                    question   = q.question,
                    issue      = issue,
                    userName   = user?.displayName() ?: "",
                    userPhone  = user?.phone ?: "",
                    tab        = tab,
                    qsheet     = qsheet
                )
                Log.d("QuizVM", "Reported question ${q.id} [$tab/$qsheet]: $issue")
            } catch (e: Exception) {
                Log.e("QuizVM", "Report failed: ${e.message}")
            }
        }
    }

    fun toggleBookmark(questionId: String) {
        val current = _state.value.bookmarkedIds.toMutableSet()
        if (current.contains(questionId)) current.remove(questionId) else current.add(questionId)
        prefs.edit().putStringSet("bookmarks", current).apply()
        // questions list এও isBookmarked update করো — UI immediately reflect করবে
        val updatedQuestions = _state.value.questions.map { q ->
            if (q.id == questionId) q.copy(isBookmarked = current.contains(questionId)) else q
        }
        _state.update { it.copy(bookmarkedIds = current, questions = updatedQuestions) }
    }

    fun updateReadingIndex(index: Int) {
        _state.update { it.copy(readingIndex = index) }
    }

    fun startTimer(questionCount: Int) {
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
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered = content.forUser(user, adminTag)
        val items = when (mode) {
            StudyMode.QUIZ  -> filtered.quiz.map  { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> filtered.qbank.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> filtered.study.map { QuestionItem.fromStudyItem(it) }
        }
        Log.d("QuizVM", "rebuildSubjects mode=$mode items=${items.size}")

        // Canonical tag for order lookup — same logic as audienceGroupOf() / forUser() filter
        val effectiveTag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
            .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
        val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(effectiveTag)

        val progressMap = loadProgressMap()
        val order = content.subjectOrder[mode.name]?.get(encodedTag) ?: emptyMap()
        val subjects = items
            .filter { it.subject.isNotBlank() }
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
            // Admin সেট করা সিরিয়াল অনুযায়ী সাজাও (ছোট নাম্বার আগে) — এই mode+tag এর জন্য আলাদা ক্রম।
            // যে সাবজেক্টের serial সেট করা নেই, সেগুলো সবসময় শেষে — নাম অনুযায়ী sort হয়ে।
            .sortedWith(compareBy({ order[it.name] ?: Int.MAX_VALUE }, { it.name }))
        Log.d("QuizVM", "Subjects built: ${subjects.size} for mode=$mode tag=$effectiveTag")
        _state.update { it.copy(subjects = subjects, isLoading = false) }
    }

    private suspend fun rebuildSubTopics(content: AppContent, subject: String, mode: StudyMode) {
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered = content.forUser(user, adminTag)
        val items = when (mode) {
            StudyMode.QUIZ  -> filtered.quiz.filter  { it.subject == subject }.map { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> filtered.qbank.filter { it.subject == subject }.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> filtered.study.filter { it.subject == subject }.map { QuestionItem.fromStudyItem(it) }
        }
        val progressMap = loadProgressMap()

        val effectiveTag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
            .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
        val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(effectiveTag)

        val order = content.subTopicOrder[mode.name]?.get(encodedTag)?.get(subject) ?: emptyMap()
        val subTopics = items.filter { it.subTopic.isNotBlank() }.groupBy { it.subTopic }.map { (st, qs) ->
            SubTopicEntry(name = st, subject = subject, totalQ = qs.size,
                          doneQ = qs.count { progressMap.contains("${mode.name}:${it.id}") }, isWeak = isWeak(st))
        }
            .sortedWith(compareBy({ order[it.name] ?: Int.MAX_VALUE }, { it.name }))
        _state.update { it.copy(subTopics = subTopics) }
    }

    // ═════════════════════════════════════════════════════════
    // Admin: ইনলাইন সাবজেক্ট/সাবটপিক ক্রম সাজানো
    // (Subject List / SubTopic List screen-এই ▲▼ বাটন চেপে — Admin Panel এ আলাদা
    //  করে যেতে হয় না। যেহেতু এই স্ক্রিন আগে থেকেই mode (Quiz/QBank/Study) আর
    //  audience tag অনুযায়ী filter হয়ে subjects/subTopics দেখায়, এখানে সরাসরি
    //  ক্রম বদলালে সেটা ঠিক ওই mode এর জন্যই সংরক্ষিত হয় — অন্য mode প্রভাবিত হয় না।)
    // ═════════════════════════════════════════════════════════

    /** Admin "ক্রম সাজান" বাটনে চাপলে ▲▼ controls toggle হয় */
    fun toggleReorderMode() {
        if (!_state.value.isAdmin) return
        _state.update { it.copy(isReorderMode = !it.isReorderMode, orderSavedMsg = null) }
    }

    /** Subject list এ একটা subject উপরে/নিচে সরানো — শুধু local state, সাথে সাথেই Firebase এ সংরক্ষণ হয় */
    fun moveSubject(fromIndex: Int, toIndex: Int) {
        if (!_state.value.isAdmin) return
        val list = _state.value.subjects.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _state.update { it.copy(subjects = list) }
        persistSubjectOrder(list.map { it.name })
    }

    /** SubTopic list এ একটা subTopic উপরে/নিচে সরানো — শুধু local state, সাথে সাথেই Firebase এ সংরক্ষণ হয় */
    fun moveSubTopic(fromIndex: Int, toIndex: Int) {
        if (!_state.value.isAdmin) return
        val subject = _state.value.navPath.subject ?: return
        val list = _state.value.subTopics.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _state.update { it.copy(subTopics = list) }
        persistSubTopicOrder(subject, list.map { it.name })
    }

    private var orderSaveJob: Job? = null

    /** বর্তমান subjects ক্রম অনুযায়ী ১,২,৩... সিরিয়াল বানিয়ে এই mode+tag এর জন্য Firebase এ সেভ করো */
    private fun persistSubjectOrder(orderedNames: List<String>) {
        val mode = _state.value.mode
        val user = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val effectiveTag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
            .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
        orderSaveJob?.cancel()
        orderSaveJob = viewModelScope.launch {
            _state.update { it.copy(isSavingOrder = true, orderSavedMsg = null) }
            val order = orderedNames.mapIndexed { idx, name -> name to (idx + 1) }.toMap()
            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService.adminSetSubjectOrderBulk(mode.name, effectiveTag, order)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(effectiveTag)
                    repo.patchSubjectOrderAndPersist(mode.name, encodedTag, order)
                    _state.update { it.copy(isSavingOrder = false, orderSavedMsg = "✅ ক্রম সংরক্ষিত হয়েছে") }
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    _state.update { it.copy(isSavingOrder = false, orderSavedMsg = "❌ সংরক্ষণ ব্যর্থ: ${r.message}") }
                }
            }
        }
    }

    /** বর্তমান subTopics ক্রম অনুযায়ী ১,২,৩... সিরিয়াল বানিয়ে এই mode+tag+subject এর জন্য Firebase এ সেভ করো */
    private fun persistSubTopicOrder(subject: String, orderedNames: List<String>) {
        val mode = _state.value.mode
        val user = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val effectiveTag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
            .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
        orderSaveJob?.cancel()
        orderSaveJob = viewModelScope.launch {
            _state.update { it.copy(isSavingOrder = true, orderSavedMsg = null) }
            val order = orderedNames.mapIndexed { idx, name -> name to (idx + 1) }.toMap()
            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService.adminSetSubTopicOrderBulk(mode.name, effectiveTag, subject, order)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(effectiveTag)
                    repo.patchSubTopicOrderAndPersist(mode.name, encodedTag, subject, order)
                    _state.update { it.copy(isSavingOrder = false, orderSavedMsg = "✅ ক্রম সংরক্ষিত হয়েছে") }
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    _state.update { it.copy(isSavingOrder = false, orderSavedMsg = "❌ সংরক্ষণ ব্যর্থ: ${r.message}") }
                }
            }
        }
    }

    fun clearOrderSavedMsg() { _state.update { it.copy(orderSavedMsg = null) } }

    private suspend fun loadQuestions(content: AppContent, subject: String, subTopic: String, mode: StudyMode) {
        val bookmarks = _state.value.bookmarkedIds
        val user      = session.getCurrentUser()
        val adminTag  = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered  = content.forUser(user, adminTag)
        val items = when (mode) {
            StudyMode.QUIZ  -> filtered.quiz.filter  { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> filtered.qbank.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> filtered.study.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromStudyItem(it) }
        }.map { it.copy(isBookmarked = bookmarks.contains(it.id), isWeakTopic = isWeak(it.subTopic)) }
            // আগে সঠিক হয়েছে এমন (mastered) প্রশ্নগুলো এই সাবটপিকে সবার নিচে শুরু হবে —
            // নতুন/ভুল করা প্রশ্নগুলো উপরে থাকবে, যাতে আগে সেগুলোর দিকেই নজর যায়
            .sortedBy { isMastered(it.id, mode) }

        _state.update {
            it.copy(questions = items, isQuizActive = mode != StudyMode.STUDY,
                    showResult = false, result = null, answeredCount = 0, timerSec = 0)
        }
        if (mode != StudyMode.STUDY) startTimer(items.size)
    }

    private fun loadProgressMap(): Set<String> = prefs.getStringSet("progress", emptySet()) ?: emptySet()

    // প্রশ্ন উত্তর দেওয়া হলে "done" সেট এ যোগ করো — নইলে progressPct সবসময় ০% থেকে যায়
    private fun markProgress(qId: String, mode: StudyMode) {
        if (qId.isBlank()) return
        val key   = "${mode.name}:$qId"
        val saved = prefs.getStringSet("progress", mutableSetOf())!!.toMutableSet()
        if (saved.add(key)) prefs.edit().putStringSet("progress", saved).apply()
    }

    private fun saveWeakTopic(subject: String, subTopic: String) {
        if (subTopic.isBlank()) return
        val key = "weak_$subTopic"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun saveWrongQId(qId: String, mode: StudyMode) {
        val sheet  = when (mode) { StudyMode.QUIZ -> "quiz"; StudyMode.QBANK -> "qbank"; StudyMode.STUDY -> "study" }
        val entry  = "$sheet:$qId"
        val ids    = prefs.getStringSet("wrong_q_ids", mutableSetOf())!!.toMutableSet()
        ids.add(entry)
        val counts = prefs.getStringSet("wrong_q_count", mutableSetOf())!!.toMutableSet()
        // count format: "sheet:id=N"
        val existing = counts.find { it.startsWith("$entry=") }
        val newCount = (existing?.split("=")?.getOrNull(1)?.toIntOrNull() ?: 0) + 1
        if (existing != null) counts.remove(existing)
        counts.add("$entry=$newCount")
        prefs.edit().putStringSet("wrong_q_ids", ids).putStringSet("wrong_q_count", counts).apply()
    }

    /** HomeScreen WrongReviewSection থেকে call হয় — সঠিক হলে সব sheet এর entry remove */
    fun removeWrongQId(qId: String) {
        if (qId.isBlank()) return
        val suffix = ":$qId"
        val ids    = prefs.getStringSet("wrong_q_ids", mutableSetOf())!!.toMutableSet()
        val counts = prefs.getStringSet("wrong_q_count", mutableSetOf())!!.toMutableSet()
        val removedIds    = ids.removeAll    { it.endsWith(suffix) }
        val removedCounts = counts.removeAll { it.substringBefore("=").endsWith(suffix) }
        if (removedIds || removedCounts) {
            prefs.edit()
                .putStringSet("wrong_q_ids",   ids)
                .putStringSet("wrong_q_count", counts)
                .apply()
        }
    }


    private fun removeWrongQIdByMode(qId: String, mode: StudyMode) {
        val sheet = when (mode) { StudyMode.QUIZ -> "quiz"; StudyMode.QBANK -> "qbank"; StudyMode.STUDY -> "study" }
        val entry = "$sheet:$qId"
        val ids    = prefs.getStringSet("wrong_q_ids",   mutableSetOf())!!.toMutableSet()
        val counts = prefs.getStringSet("wrong_q_count", mutableSetOf())!!.toMutableSet()
        ids.remove(entry)
        counts.removeAll { it.startsWith("$entry=") }
        prefs.edit()
            .putStringSet("wrong_q_ids",   ids)
            .putStringSet("wrong_q_count", counts)
            .apply()
    }

    // ── Routine bottom sheet এর জন্য — ইতিমধ্যে লোড হওয়া study content snapshot ──
    fun getStudyContentSnapshot(): List<StudyItem> {
        return com.hanif.smartstudy.data.repository.ContentRepository.getMemCache()?.study ?: emptyList()
    }

    // ── Routine bottom sheet এর জন্য — matching quiz snapshot (in-place mini-quiz এর জন্য) ──
    fun getQuizContentSnapshot(): List<QuizItem> {
        return com.hanif.smartstudy.data.repository.ContentRepository.getMemCache()?.quiz ?: emptyList()
    }

    // ── একটা প্রশ্নের উত্তরের log রাখা (routine mini-quiz থেকে — শেয়ার্ড _state ছোঁয় না) ──
    fun logRoutineQuizAnswer(questionId: String, isCorrect: Boolean) {
        viewModelScope.launch { repo.submitQuizAnswer(questionId, isCorrect) }
    }

    fun getWrongQuestions(): List<Pair<QuestionItem, Int>> {
        val content   = com.hanif.smartstudy.data.repository.ContentRepository.getMemCache() ?: return emptyList()
        val ids       = prefs.getStringSet("wrong_q_ids", emptySet()) ?: return emptyList()
        val counts    = prefs.getStringSet("wrong_q_count", emptySet()) ?: emptySet()
        val countMap  = counts.associate {
            val p = it.split("="); p.getOrElse(0){""} to (p.getOrElse(1){"1"}.toIntOrNull() ?: 1)
        }
        return ids.mapNotNull { entry ->
            val parts  = entry.split(":", limit = 2)
            val sheet  = parts.getOrElse(0) { "quiz" }
            val qId    = parts.getOrElse(1) { "" }
            val pool   = when (sheet) {
                "qbank" -> content.qbank.map { QuestionItem.fromQBankItem(it) }
                "study" -> content.study.map { QuestionItem.fromStudyItem(it) }
                else    -> content.quiz.map  { QuestionItem.fromQuizItem(it)  }
            }
            val q = pool.find { it.id == qId } ?: return@mapNotNull null
            q to (countMap[entry] ?: 1)
        }.sortedByDescending { it.second }
    }

    fun startWrongReview() {
        val wrongItems = getWrongQuestions().map { (q, _) -> q }
        if (wrongItems.isEmpty()) return
        _state.update {
            it.copy(
                questions     = wrongItems,
                isQuizActive  = true,
                showResult    = false,
                result        = null,
                answeredCount = 0,
                navPath       = NavPath("ভুল প্রশ্ন Review")
            )
        }
        startTimer(wrongItems.size)
    }

    private fun isWeak(subTopic: String) = prefs.getInt("weak_$subTopic", 0) >= 2

    // ── একটা প্রশ্ন আগে সঠিকভাবে উত্তর দেওয়া (mastered) কিনা — অর্থাৎ আগে অন্তত
    // একবার উত্তর দেওয়া হয়েছে (progress এ আছে) এবং এখন আর "ভুল" তালিকায় নেই ──
    private fun sheetNameFor(mode: StudyMode) = when (mode) {
        StudyMode.QUIZ  -> "quiz"
        StudyMode.QBANK -> "qbank"
        StudyMode.STUDY -> "study"
    }

    private fun isMastered(qId: String, mode: StudyMode): Boolean {
        if (qId.isBlank()) return false
        val doneKey  = "${mode.name}:$qId"
        val wrongKey = "${sheetNameFor(mode)}:$qId"
        val progressSet = prefs.getStringSet("progress", emptySet()) ?: emptySet()
        val wrongSet     = prefs.getStringSet("wrong_q_ids", emptySet()) ?: emptySet()
        return progressSet.contains(doneKey) && !wrongSet.contains(wrongKey)
    }

    private fun loadWeakTopics(): List<WeakTopic> =
        prefs.all.entries
            .filter { it.key.startsWith("weak_") && (it.value as? Int ?: 0) >= 2 }
            .map { WeakTopic(it.key.removePrefix("weak_"), "", it.value as Int) }
            .sortedByDescending { it.wrongCount }

    /**
     * answer field এ যদি শুধু ক/খ/গ/ঘ অথবা a/b/c/d থাকে,
     * তাহলে সেই position এর option text return করে।
     * অন্যথায় original answer text ই return করে।
     */
    private fun resolveCorrectText(q: QuestionItem): String {
        val raw = q.answer.trim()
        val optionByIndex = when (raw.lowercase()) {
            "ক", "a", "1" -> q.optionA
            "খ", "b", "2" -> q.optionB
            "গ", "c", "3" -> q.optionC
            "ঘ", "d", "4" -> q.optionD
            else           -> null
        }
        return if (optionByIndex != null && optionByIndex.isNotBlank()) optionByIndex else raw
    }

    private fun fuzzyMatch(userText: String, correctText: String): Int {
        if (userText.isBlank()) return 0
        val uWords = userText.lowercase().split(Regex("[\\s,।.]+")).filter { it.length > 1 }.toSet()
        val cWords = correctText.lowercase().split(Regex("[\\s,।.]+")).filter { it.length > 1 }.toSet()
        if (cWords.isEmpty()) return 0
        return minOf(100, (uWords.intersect(cWords).size * 100) / cWords.size)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        loadJob?.cancel()
    }
}

private suspend fun ContentCache.markTodayActivity() { updateStreak() }
