package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.ChallengeRepository
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.AudienceFilter.forUser
import com.hanif.smartstudy.util.AudienceFilter.canChallenge
import com.hanif.smartstudy.util.AudienceFilter.audienceGroupLabel
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
    val canAddOpponent  : Boolean               = false,   // group match হলে true
    val isSearching     : Boolean               = false,
    val invitedUsers    : List<User>            = emptyList(),
    val selectedSource  : String                = "",   // "Quiz" | "QBank" | ""
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
    val toast           : String?               = null,
    // Ghost Mode
    val isGhostMode       : Boolean               = false,
    // XP Wager (বাজি)
    val wagerXp           : Int                   = 0,       // 0, 10, 25, 50, 100
    val myCurrentXp       : Int                   = 0,       // বাজি ধরার আগে check
    // Lifelines — প্রতিটা একবার মাত্র
    val usedFiftyFifty    : Boolean               = false,
    val usedTimeFreeze    : Boolean               = false,
    val hiddenOptions     : Set<Int>              = emptySet(), // 50-50 এ লুকানো wrong options
    val timeFrozenSec     : Int                   = 0,          // freeze চলাকালীন বাকি সেকেন্ড
    val isFreezing        : Boolean               = false,
    // Accuracy tip — দ্রুত কিন্তু কম accurate হলে পরামর্শ
    val accuracyTip       : String?               = null,
    // All Users dropdown
    val allUsers          : List<User>            = emptyList(),
    val isLoadingUsers    : Boolean               = false,
    val showUserDropdown  : Boolean               = false,
    val selectedDropUser  : User?                 = null,
    // Match History (current opponent)
    val matchHistory      : List<MatchRecord>     = emptyList(),
    val winLossSummary    : Map<String, Pair<Int,Int>> = emptyMap()
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
        _state.update { it.copy(
            myPhone     = me?.phone ?: "",
            myCurrentXp = me?.xp ?: 0
        )}
        loadSubjects()
        observeInvites()
    }

    // ── Subjects from content ────────────────────────────

    // ── Source + Subjects from content ──────────────────

    private fun loadSubjects() {
        viewModelScope.launch {
            val c = ContentRepository.getMemCache() ?: content.getContent()
                .let { (it as? com.hanif.smartstudy.data.repository.DataState.Success)?.data }
            if (c == null) return@launch
            val me       = session.getCurrentUser()
            val filtered = c.forUser(me)
            val subjects = (filtered.quiz.map { it.subject ?: "" } +
                            filtered.qbank.map { it.subject ?: "" })
                .filter { it.isNotBlank() }.distinct().sorted()
            _state.update { it.copy(subjects = subjects) }
        }
    }

    fun onSourceSelect(source: String) {
        viewModelScope.launch {
            val c = ContentRepository.getMemCache() ?: content.getContent()
                .let { (it as? com.hanif.smartstudy.data.repository.DataState.Success)?.data }
            if (c == null) return@launch
            val me       = session.getCurrentUser()
            val filtered = c.forUser(me)
            val subjects = when (source) {
                "Quiz"  -> filtered.quiz.map { it.subject ?: "" }
                "QBank" -> filtered.qbank.map { it.subject ?: "" }
                else    -> filtered.quiz.map { it.subject ?: "" } + filtered.qbank.map { it.subject ?: "" }
            }.filter { it.isNotBlank() }.distinct().sorted()
            _state.update { it.copy(
                selectedSource   = source,
                subjects         = subjects,
                selectedSubject  = "",
                selectedSubTopic = "",
                subTopics        = emptyList()
            )}
        }
    }

    fun onSubjectSelect(subject: String) {
        viewModelScope.launch {
            val c = ContentRepository.getMemCache() ?: return@launch
            val me       = session.getCurrentUser()
            val filtered = c.forUser(me)
            val source   = _state.value.selectedSource
            val subTopics = when (source) {
                "Quiz"  -> filtered.quiz.filter { it.subject == subject }.map { it.subTopic ?: "" }
                "QBank" -> filtered.qbank.filter { it.subject == subject }.map { it.subTopic ?: "" }
                else    -> (filtered.quiz.filter { it.subject == subject }.map { it.subTopic ?: "" } +
                            filtered.qbank.filter { it.subject == subject }.map { it.subTopic ?: "" })
            }.filter { it.isNotBlank() }.distinct().sorted()
            _state.update { it.copy(selectedSubject = subject, selectedSubTopic = "", subTopics = subTopics) }
        }
    }

    fun onSubTopicSelect(sub: String) = _state.update { it.copy(selectedSubTopic = sub) }
    fun onQuestionCountChange(n: Int) {
        val count = n.coerceIn(5, 30)
        // ১ প্রশ্ন = ১ মিনিট, auto-set time
        _state.update { it.copy(questionCount = count, timeLimitSec = count * 60) }
    }
    fun onTimeLimitChange(sec: Int)   = _state.update { it.copy(timeLimitSec = sec) }
    fun onPhoneInput(phone: String)   = _state.update { it.copy(phoneInput = phone, searchResult = null, searchError = null, canAddOpponent = false) }
    fun onWagerChange(xp: Int)        = _state.update { it.copy(wagerXp = xp) }

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

            if (user == null) {
                _state.update {
                    it.copy(isSearching = false, searchResult = null,
                            canAddOpponent = false,
                            searchError = "এই নম্বরে কোনো account নেই")
                }
                return@launch
            }

            // ── Audience group compatibility check ──
            val me           = session.getCurrentUser()
            val compatible   = canChallenge(me, user)
            val myGroup      = audienceGroupLabel(me)
            val oppGroup     = audienceGroupLabel(user)
            val groupErrMsg  = if (!compatible)
                "❌ তুমি ($myGroup) এই ব্যবহারকারীকে ($oppGroup) challenge করতে পারবে না। শুধুমাত্র একই শ্রেণির শিক্ষার্থীরা পরস্পরকে challenge করতে পারবে।"
            else null

            _state.update {
                it.copy(
                    isSearching    = false,
                    searchResult   = user,          // সব ক্ষেত্রেই card দেখাও
                    canAddOpponent = compatible,    // group মিললে true → বাটন সবুজ
                    searchError    = groupErrMsg    // মিলে গেলে null, না মিললে error
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

    fun addInviteeFromDropdown(user: User) {
        val s = _state.value
        if (s.invitedUsers.any { it.phone == user.phone }) {
            _state.update { it.copy(
                showUserDropdown = false,
                selectedDropUser = null,
                toast = "${user.displayName()} ইতিমধ্যে যোগ করা হয়েছে"
            )}
            return
        }
        _state.update { it.copy(
            invitedUsers     = it.invitedUsers + user,
            showUserDropdown = false,
            selectedDropUser = null,
            toast            = "${user.displayName()} যোগ করা হয়েছে"
        )}
    }

    fun loadAllUsers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingUsers = true, showUserDropdown = true) }
            val users = repo.getAllUsers(_state.value.myPhone)
            _state.update { it.copy(isLoadingUsers = false, allUsers = users) }
        }
    }

    fun selectDropUser(user: User) {
        _state.update { it.copy(selectedDropUser = if (it.selectedDropUser?.phone == user.phone) null else user) }
    }

    fun dismissUserDropdown() {
        _state.update { it.copy(showUserDropdown = false, selectedDropUser = null) }
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
            val filtered = c.forUser(me)

            // Question pool থেকে random questions নাও (audience filtered)
            val pool    = (filtered.quiz.filter { it.subject == s.selectedSubject &&
                            (s.selectedSubTopic.isBlank() || it.subTopic == s.selectedSubTopic) }
                            .map { QuestionItem.fromQuizItem(it) } +
                           filtered.qbank.filter { it.subject == s.selectedSubject &&
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
                questionIds   = pool.map { it.id },
                wagerXp       = s.wagerXp
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
                if (challenge.getStatus() == ChallengeStatus.PENDING ||
                   (challenge.getStatus() == ChallengeStatus.WAITING &&
                    challenge.allAccepted() &&
                    challenge.creatorPhone == _state.value.myPhone)) {
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
            // MemCache null হলে content fetch করো
            val c = ContentRepository.getMemCache()
                ?: run {
                    val fetched = content.getContent()
                    (fetched as? com.hanif.smartstudy.data.repository.DataState.Success)?.data
                }
            if (c == null) {
                delay(2000)
                val retried = ContentRepository.getMemCache()
                    ?: (content.getContent() as? com.hanif.smartstudy.data.repository.DataState.Success)?.data
                if (retried == null) {
                    _state.update { it.copy(error = "প্রশ্ন লোড হয়নি। আবার চেষ্টা করুন।") }
                    return@launch
                }
                loadExamQuestionsFromContent(challenge, retried)
                return@launch
            }
            loadExamQuestionsFromContent(challenge, c)
        }
    }

    private fun loadExamQuestionsFromContent(
        challenge : Challenge,
        c         : com.hanif.smartstudy.data.model.AppContent
    ) {
        val me       = session.getCurrentUser()
        val filtered = c.forUser(me)
        val allQ     = (filtered.quiz.map { QuestionItem.fromQuizItem(it) } +
                        filtered.qbank.map { QuestionItem.fromQBankItem(it) })
        val questions = challenge.questionIds.mapNotNull { id ->
            allQ.find { it.id == id }
        }.ifEmpty {
            allQ.filter {
                challenge.subject.isBlank() || it.subject == challenge.subject
            }.shuffled().take(challenge.questionCount)
        }
        if (questions.isEmpty()) {
            _state.update { it.copy(error = "এই বিষয়ে কোনো প্রশ্ন পাওয়া যায়নি।") }
            return
        }
        _state.update {
            it.copy(
                questions     = questions,
                currentQIndex = 0,
                timerSec      = challenge.timeLimitSec,
                screen        = ChallengeScreen.Exam(challenge.id)
            )
        }
        startExamTimer(challenge.id, challenge.timeLimitSec)
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
        _state.update { it.copy(
            currentQIndex = index.coerceIn(0, _state.value.questions.size - 1),
            hiddenOptions = emptySet()   // নতুন প্রশ্নে 50-50 এফেক্ট reset
        )}
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
            // Ghost Mode: lockGhostChallenge, তারপর invite পাঠাও
            if (challenge.isGhostMode && challenge.creatorPhone == s.myPhone) {
                repo.lockGhostChallenge(challenge.id, s.myPhone, score, correctIds)
                // Ghost invite পাঠাও সব invitees দের
                challenge.participants.values
                    .filter { it.phone != s.myPhone && it.status == "INVITED" }
                    .forEach { p ->
                        repo.sendGhostInvite(p.phone, ChallengeInvite(
                            challengeId   = challenge.id,
                            creatorName   = challenge.creatorName,
                            creatorPhone  = challenge.creatorPhone,
                            subject       = challenge.subject,
                            questionCount = challenge.questionCount,
                            timeLimitSec  = challenge.timeLimitSec,
                            createdAt     = challenge.createdAt,
                            isGhostMode   = true
                        ))
                    }
            } else {
                repo.submitChallenge(challenge.id, s.myPhone, score, correctIds)
            }

            // Match History save + XP wager apply
            val wager       = challenge.wagerXp
            val accuracy    = if (s.questions.isNotEmpty()) score * 100f / s.questions.size else 0f
            val timeUsedSec = challenge.timeLimitSec - s.timerSec   // কত সেকেন্ড লেগেছে
            val totalSec    = challenge.timeLimitSec.coerceAtLeast(1)
            val timeUsedPct = timeUsedSec * 100f / totalSec           // % সময় ব্যবহার

            // Accuracy tip: দ্রুত শেষ করেছে কিন্তু accuracy কম
            val tip = when {
                accuracy < 50f && timeUsedPct < 50f ->
                    "⚠️ তুমি অনেক দ্রুত শেষ করেছ কিন্তু মাত্র ${accuracy.toInt()}% সঠিক! পরের বার প্রতিটা প্রশ্ন ভালো করে পড়ো।"
                accuracy < 60f && timeUsedPct < 60f ->
                    "💡 Speed এর চেয়ে Accuracy বেশি গুরুত্বপূর্ণ! একটু ধীরে হলেও সঠিক উত্তর দাও।"
                accuracy >= 90f ->
                    "🌟 অসাধারণ! ${accuracy.toInt()}% accuracy — এভাবেই চালিয়ে যাও!"
                else -> null
            }
            challenge.participants.values
                .filter { it.phone != s.myPhone && it.score >= 0 }
                .forEach { opponent ->
                    repo.saveMatchHistory(
                        myPhone     = s.myPhone,
                        myName      = challenge.myParticipant(s.myPhone)?.name ?: "",
                        opponent    = opponent,
                        challengeId = challenge.id,
                        subject     = challenge.subject,
                        myScore     = score,
                        total       = s.questions.size,
                        isGhostMode = challenge.isGhostMode,
                        wagerXp     = wager
                    )
                    // XP Wager apply করো
                    if (wager > 0) {
                        val iWon  = score > opponent.score
                        val delta = if (iWon) wager else -wager
                        repo.applyWagerXp(s.myPhone, delta)
                        val msg = if (iWon) "🏆 জিতেছ! +$wager XP পেয়েছ" else "😞 হেরেছ। -$wager XP কাটা গেছে"
                        _state.update { it.copy(toast = msg) }
                    }
                }

            _state.update { it.copy(isSubmitting = false, accuracyTip = tip) }
        }
    }

    private fun startExamTimer(challengeId: String, totalSec: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                delay(1000)
                // Time Freeze চললে tick skip করো
                if (_state.value.isFreezing) continue
                remaining--
                _state.update { it.copy(timerSec = remaining) }
            }
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

    // ── Rewarded Ad: Earn 20 XP (Wager section) ──────────
    fun earnXpFromAd(earnAmount: Int = 20) {
        viewModelScope.launch {
            try {
                val me = session.getCurrentUser() ?: return@launch
                val newXp = me.xp + earnAmount
                val updated = me.copy(xp = newXp)
                session.saveUser(updated)
                // Firebase update — try both path casings
                val phones = listOf(
                    me.phone?.replace("+", "").orEmpty(),
                    me.phone.orEmpty()
                ).filter { it.isNotBlank() }.distinct()
                for (node in listOf("Users", "users")) {
                    for (phone in phones) {
                        try {
                            com.google.firebase.database.FirebaseDatabase.getInstance()
                                .getReference("$node/$phone")
                                .updateChildren(mapOf("XP" to newXp))
                        } catch (_: Exception) {}
                    }
                }
                _state.update { it.copy(
                    myCurrentXp = newXp,
                    toast       = "🎉 বিজ্ঞাপন দেখার জন্য +$earnAmount XP পেয়েছ!"
                )}
            } catch (e: Exception) {
                Log.e("ChallengeVM", "earnXpFromAd error: ${e.message}")
            }
        }
    }

    // ── Rewarded Ad: XP double ────────────────────────────
    fun doubleXP() {
        viewModelScope.launch {
            try {
                val me    = session.getCurrentUser() ?: return@launch
                val total = _state.value.questions.size
                val myPhone = _state.value.myPhone
                val myScore = _state.value.challenge?.participants
                    ?.get(myPhone.replace(".", "_"))?.score ?: 0
                // XP bonus = score পয়েন্ট (যত প্রশ্ন সঠিক)
                val earnedXp = myScore.coerceAtLeast(5)
                val phone  = me.phone?.replace("+", "").orEmpty().ifEmpty { return@launch }
                val newXp  = me.xp + earnedXp
                val updated = me.copy(xp = newXp)
                session.saveUser(updated)
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("users/$phone")
                    .updateChildren(mapOf("XP" to newXp))
                _state.update { it.copy(toast = "🎉 XP দ্বিগুণ হয়েছে! +$earnedXp XP") }
            } catch (e: Exception) {
                Log.e("ChallengeVM", "doubleXP error: ${e.message}")
            }
        }
    }

    // ── Ghost Mode toggle ─────────────────────────────────
    fun toggleGhostMode() = _state.update { it.copy(isGhostMode = !it.isGhostMode) }

    // ── Lifelines ─────────────────────────────────────────

    /** 50-50: সঠিক উত্তর ছাড়া দুটো ভুল option লুকিয়ে দাও */
    fun useFiftyFifty() {
        val s = _state.value
        if (s.usedFiftyFifty) return
        val currentQ = s.questions.getOrNull(s.currentQIndex) ?: return

        // সঠিক option index বের করো (1-indexed)
        val correctIdx = when {
            currentQ.optionA.trim().equals(currentQ.answer.trim(), ignoreCase = true) -> 1
            currentQ.optionB.trim().equals(currentQ.answer.trim(), ignoreCase = true) -> 2
            currentQ.optionC.trim().equals(currentQ.answer.trim(), ignoreCase = true) -> 3
            currentQ.optionD.trim().equals(currentQ.answer.trim(), ignoreCase = true) -> 4
            else -> 1
        }
        // বাকি তিনটা থেকে দুটো random বেছে লুকাও
        val wrongOptions = (1..4).filter { it != correctIdx }.shuffled().take(2).toSet()
        _state.update { it.copy(usedFiftyFifty = true, hiddenOptions = wrongOptions) }

        // Firebase এ mark করো
        viewModelScope.launch {
            repo.markLifelineUsed(
                s.challenge?.id ?: return@launch,
                s.myPhone,
                Lifeline.FIFTY_FIFTY
            )
        }
    }

    /** Time Freeze: ৩০ সেকেন্ড timer থামিয়ে রাখো */
    fun useTimeFreeze() {
        val s = _state.value
        if (s.usedTimeFreeze || s.isFreezing) return
        val frozenAt = s.timerSec
        _state.update { it.copy(usedTimeFreeze = true, isFreezing = true, timeFrozenSec = frozenAt) }

        // Timer job pause করো — ৩০ সেকেন্ড পরে resume
        viewModelScope.launch {
            delay(30_000L)
            _state.update { it.copy(isFreezing = false) }
        }

        // Firebase এ mark করো
        viewModelScope.launch {
            repo.markLifelineUsed(
                s.challenge?.id ?: return@launch,
                s.myPhone,
                Lifeline.TIME_FREEZE
            )
        }
    }

    // ── Ghost Challenge create — creator একাই ACTIVE পরীক্ষা শুরু করে ──
    fun createGhostChallenge() {
        val s = _state.value
        if (s.selectedSubject.isBlank()) { _state.update { it.copy(error = "বিষয় বেছে নিন") }; return }
        if (s.invitedUsers.isEmpty())    { _state.update { it.copy(error = "অন্তত একজনকে invite করুন") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val me = session.getCurrentUser() ?: return@launch
            val c  = ContentRepository.getMemCache()
                ?: (content.getContent() as? com.hanif.smartstudy.data.repository.DataState.Success)?.data
                ?: run { _state.update { it.copy(isLoading = false, error = "Content load হয়নি") }; return@launch }
            val filtered = c.forUser(me)
            val pool = (filtered.quiz.filter { it.subject == s.selectedSubject &&
                (s.selectedSubTopic.isBlank() || it.subTopic == s.selectedSubTopic) })
                .shuffled().take(s.questionCount)
            val questionIds = pool.map { it.id ?: "" }.filter { it.isNotBlank() }
            if (questionIds.size < 3) {
                _state.update { it.copy(isLoading = false, error = "পর্যাপ্ত প্রশ্ন নেই") }; return@launch
            }
            val id = repo.createGhostChallenge(
                creator = me, invitees = s.invitedUsers,
                subject = s.selectedSubject, subTopic = s.selectedSubTopic,
                questionCount = questionIds.size, timeLimitSec = s.timeLimitSec,
                questionIds = questionIds
            )
            if (id == null) {
                _state.update { it.copy(isLoading = false, error = "চ্যালেঞ্জ তৈরি ব্যর্থ হয়েছে") }
                return@launch
            }
            // সরাসরি Exam 에 যাও — creator একাই পরীক্ষা দেবে
            val ghostChallenge = repo.getChallenge(id)
            val questions = c.quiz.filter { it.id in questionIds }.map { QuestionItem.fromQuizItem(it) }
            _state.update { it.copy(
                isLoading     = false,
                challenge     = ghostChallenge,
                questions     = questions,
                answers       = mutableMapOf(),
                currentQIndex = 0,
                timerSec      = s.timeLimitSec,
                screen        = ChallengeScreen.Exam(id)
            )}
            startExamTimer(id, s.timeLimitSec)
        }
    }

    // ── Rematch — same subject, same invitees, নতুন চ্যালেঞ্জ ──
    fun rematch() {
        val s = _state.value
        val challenge = s.challenge ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val me = session.getCurrentUser() ?: return@launch
            val c  = ContentRepository.getMemCache()
                ?: (content.getContent() as? com.hanif.smartstudy.data.repository.DataState.Success)?.data
                ?: run { _state.update { it.copy(isLoading = false, error = "Content load হয়নি") }; return@launch }
            val filtered = c.forUser(me)

            // opponents list
            val opponents = challenge.participants.values
                .filter { it.phone != s.myPhone }
                .mapNotNull { p ->
                    // User object বানাও opponent থেকে
                    com.hanif.smartstudy.data.model.User(
                        phone    = p.phone,
                        name     = p.name,
                        role     = "User",
                        status   = "Active"
                    )
                }

            val pool = filtered.quiz.filter { it.subject == challenge.subject &&
                (challenge.subTopic.isBlank() || it.subTopic == challenge.subTopic) }
                .shuffled().take(challenge.questionCount)
            val questionIds = pool.map { it.id ?: "" }.filter { it.isNotBlank() }

            if (questionIds.size < 3) {
                _state.update { it.copy(isLoading = false, error = "পর্যাপ্ত প্রশ্ন নেই") }
                return@launch
            }

            val id = repo.createChallenge(
                creator = me, invitees = opponents,
                subject = challenge.subject, subTopic = challenge.subTopic,
                questionCount = questionIds.size, timeLimitSec = challenge.timeLimitSec,
                questionIds = questionIds
            )
            _state.update { it.copy(isLoading = false) }
            if (id != null) {
                observeChallenge(id)
                _state.update { it.copy(screen = ChallengeScreen.Lobby(id)) }
                _state.update { it.copy(toast = "♻️ Rematch পাঠানো হয়েছে!") }
            } else {
                _state.update { it.copy(error = "Rematch তৈরি ব্যর্থ") }
            }
        }
    }

    // ── Match History load ────────────────────────────────
    fun loadMatchHistory(opponentPhone: String) {
        val myPhone = _state.value.myPhone.ifEmpty { return }
        viewModelScope.launch {
            val history = repo.getMatchHistory(myPhone, opponentPhone)
            _state.update { it.copy(matchHistory = history) }
        }
    }

    fun loadWinLossSummary() {
        val myPhone = _state.value.myPhone.ifEmpty { return }
        viewModelScope.launch {
            val summary = repo.getWinLossSummary(myPhone)
            _state.update { it.copy(winLossSummary = summary) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        challengeObserveJob?.cancel()
        inviteObserveJob?.cancel()
        timerJob?.cancel()
    }
}
