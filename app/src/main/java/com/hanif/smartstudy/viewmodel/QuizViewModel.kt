package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.local.TestHistoryCache
import com.hanif.smartstudy.data.local.LocalModelTestStore
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.Dispatchers
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
    // ── Room pagination ──
    val totalQuestions: Int              = 0,    // এই subTopic-এ মোট কতটা প্রশ্ন (Room count)
    val questionsLoading: Boolean        = false, // প্রশ্ন লোড হচ্ছে কিনা (page change এ)
    val timerSec      : Int              = 0,
    val totalTimeSec  : Int              = 0,
    val answeredCount : Int              = 0,
    val result        : QuizResult?      = null,
    val showResult    : Boolean          = false,
    val isMockZone    : Boolean          = false,
    val mockConfig    : MockTestConfig   = MockTestConfig(),
    // টাইমার শেষ হয়ে অটো-সাবমিট হওয়ার সময় written প্রশ্নে টাইপ-করা-কিন্তু-submit-না-করা
    // ড্রাফট টেক্সট হারিয়ে না যাওয়ার জন্য — key: QuestionItem.sourceKey()
    val writtenDrafts : Map<String, String> = emptyMap(),
    // ── Model Test (এডমিন-কিউরেটেড, ফিক্সড) ──
    // ── Model Test (এডমিন-কিউরেটেড, ফিক্সড) ──
    // Mock Test-এর মতোই একটা গ্লোবাল এন্ট্রি — Study/Quiz/QBank subject list-এর নিচে বাটন,
    // ট্যাপ করলে প্রথমে subject picker, তারপর সেই subject-এর test list
    val isModelTestSubjectPicker : Boolean              = false,
    val modelTestSubjectList     : List<Pair<String,Int>> = emptyList(),  // subject -> কতগুলো টেস্ট আছে
    val isModelTestZone      : Boolean            = false,   // Model Test list (test 1,2,3...) দেখানো হচ্ছে
    val modelTestSubject     : String             = "",
    val modelTests           : List<ModelTestMeta> = emptyList(),
    val pendingModelTestType : ModelTestMeta?      = null,    // type=="both" হলে MCQ/Written bottom sheet এর জন্য
    val activeModelTest      : ModelTestMeta?      = null,    // বর্তমানে চলমান/সদ্য-শেষ Model Test — back/retry নেভিগেশনের জন্য
    val activeModelTestType  : String?             = null,
    // ── Model Test — ইউজার নিজে জেনারেট করে (QBank-only entry, Quiz sheet থেকে পুল, লোকাল স্টোরেজ) ──
    val isModelTestJobUser       : Boolean         = false,   // true হলে subject picker স্কিপ হয়ে সরাসরি "সকল বিষয়" ব্যাচ খোলে
    val showModelTestGenerateSheet : Boolean       = false,   // "+ নতুন মডেল টেস্ট বানান" ফর্ম
    val isGeneratingModelTest    : Boolean         = false,
    val modelTestGenWarning      : String?         = null,    // জেনারেটরের warning (যেমন প্রশ্ন কম থাকলে)
    val readingIndex  : Int              = 0,
    val bookmarkedIds : Set<String>      = emptySet(),
    val weakTopics    : List<WeakTopic>  = emptyList(),
    val contentLoaded : Boolean          = false,
    val highlightQuestionId : String?    = null,
    // ── Admin: ইনলাইন ক্রম সাজানো (Subject/SubTopic list screen-এই ▲▼ বাটন) ──
    val isAdmin        : Boolean         = false,
    val isReorderMode  : Boolean         = false,   // ▲▼ বাটন দেখানো হবে কিনা (admin টগল করে)
    val isSavingOrder  : Boolean         = false,
    val orderSavedMsg  : String?         = null,
    // ── Pagination ──
    val currentPage    : Int             = 0,        // 0-based page index
    // ═══ QBank-only ফিল্টার — শুধু QBank মোডে ব্যবহার হয় ═══
    // ডিফল্ট পদবী(Designation)-ভিত্তিক — এখনকার Subject→SubTopic হায়ারার্কি অপরিবর্তিত।
    // প্রতিষ্ঠান(Institution)-ভিত্তিক হলে হায়ারার্কি উল্টে যায় (আগে প্রতিষ্ঠান, তার
    // আন্ডারে পদবী)। সাল(Year)-ভিত্তিক হলে flat প্রশ্ন-লিস্ট (subject/subTopic নির্বিশেষে)।
    val qbankFilterMode : QBankFilterMode = QBankFilterMode.DESIGNATION,
    // প্রতিষ্ঠান-মোড: depth0-এ প্রতিষ্ঠানের লিস্ট (প্রতিটার subTopics ফিল্ডে সেই
    // প্রতিষ্ঠানের আন্ডারে যত পদবী আছে সেটাই নেস্টেড থাকে — rebuildSubjects()-এর
    // মতোই একই প্যাটার্নে একবারেই বিল্ড হয়)। একটা প্রতিষ্ঠান বাছাই করলে
    // qbankSelectedInstitution সেট হয় ও ওই নেস্টেড লিস্টই qbankDesignationsUnderInstitution-এ যায়।
    val qbankInstitutions : List<SubjectEntry> = emptyList(),
    val qbankSelectedInstitution : String? = null,
    val qbankDesignationsUnderInstitution : List<SubTopicEntry> = emptyList(),
    // সাল-মোড: depth0-এ সালের লিস্ট, একটা সাল বাছাই করলে flat প্রশ্ন-লিস্ট (Room-first pagination)
    val qbankYears : List<SubjectEntry> = emptyList(),
    val qbankSelectedYear : String? = null,
    // পদ-মোড (Phase 6, নতুন schema — Posts/Institutions/Exam_Appearances reference-টেবিল
    // থেকে): depth0-এ পদের লিস্ট, একটা পদ বাছাই করলে ওই পদের আন্ডারে যত প্রতিষ্ঠান আছে তার
    // লিস্ট (qbankInstitutionsUnderPost), প্রতিষ্ঠান বাছাই করলে flat প্রশ্ন-লিস্ট —
    // appearance-linked questionId দিয়ে সরাসরি Room থেকে (দেখো SubTopicEntry.linkedQuestionIds)।
    val qbankPosts : List<SubjectEntry> = emptyList(),
    val qbankSelectedPost : String? = null,
    val qbankInstitutionsUnderPost : List<SubTopicEntry> = emptyList(),
    // ── Review System (Admin-only) — student-দের কাছে সম্পূর্ণ অদৃশ্য, isAdmin==true
    // ছাড়া toggleReviewMode()/markReviewed() কিছুই করে না। ──
    val isReviewMode          : Boolean = false,
    val reviewProgressSubjects: Map<String, com.hanif.smartstudy.data.remote.GasContentService.ReviewCount> = emptyMap(),
    val reviewProgressTopics  : Map<String, com.hanif.smartstudy.data.remote.GasContentService.ReviewCount> = emptyMap(),
    // QBank-only সার্চ — শুধু depth0-এর নাম-লিস্ট (Designation/Institution/Year)
    // ক্লায়েন্ট-সাইড ফিল্টার করে, প্রশ্নের কনটেন্টে সার্চ করে না
    val qbankSearchQuery : String = ""
)

class QuizViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        // সঠিক উত্তরে XP — written এ একটু বেশি (বেশি effort লাগে)
        private const val XP_PER_CORRECT_MCQ     = 2
        private const val XP_PER_CORRECT_WRITTEN = 3
        const val PAGE_SIZE = 50   // প্রতি পৃষ্ঠায় প্রশ্নের সংখ্যা
    }


    private val repo    = ContentRepository(app)
    private val cache   = ContentCache(app)
    private val session = SessionManager(app)
    private val historyCache = TestHistoryCache(app)
    private val localModelTestStore = LocalModelTestStore(app)

    // ── Admin "Move Question(s)" ডায়ালগের Subject-এর পাশে Expand বাটনে ট্যাপ করলে
    // ওই Subject-এর Topic লিস্ট Room থেকে লাইভ আনতে (নাম দিয়ে subjectId রিজলভ করে) ──
    suspend fun adminTopicsForSubject(sheet: String, subject: String): List<String> {
        val subjectId = repo.resolveSubjectId(sheet, subject) ?: return emptyList()
        return repo.getRoomTopicsForSubject(subjectId).map { it.name }
    }

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

    // ── FIX: টপিক পরিবর্তনের রেস-কন্ডিশন বাগ — আগে দ্রুত একের পর এক টপিক পাল্টালে
    // ধীরগতির আগের টপিকের রেসপন্স পরে এসে নতুন টপিকের প্রশ্ন ওভাররাইট করে ফেলতো।
    // এখন (ক) আগের টপিক-লোড job সবসময় cancel করা হয়, এবং (খ) প্রতিটা রিকোয়েস্টের
    // নিজস্ব token থাকে — রেসপন্স ফিরলে token না মিললে (মানে ততক্ষণে অন্য টপিকে
    // চলে যাওয়া হয়েছে) সেই stale রেসপন্স চুপচাপ ফেলে দেওয়া হয়, state আপডেট হয় না। ──
    private var subTopicLoadJob: Job? = null
    private var subTopicLoadToken: Long = 0L
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
                error        = null,
                // ── অন্য মোডে গেলে/অন্য মোড থেকে QBank-এ এলে ফিল্টার-সিলেকশন রিসেট ──
                qbankFilterMode = QBankFilterMode.DESIGNATION,
                qbankInstitutions = emptyList(),
                qbankSelectedInstitution = null,
                qbankDesignationsUnderInstitution = emptyList(),
                qbankYears = emptyList(),
                qbankSelectedYear = null,
                qbankPosts = emptyList(),
                qbankSelectedPost = null,
                qbankInstitutionsUnderPost = emptyList(),
                qbankSearchQuery = ""
            )
        }

        loadJob = viewModelScope.launch {
            val bookmarks  = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
            val weakTopics = loadWeakTopics()
            val isAdmin    = session.getCurrentUser()?.isAdmin() == true
            _state.update { it.copy(bookmarkedIds = bookmarks, weakTopics = weakTopics, isAdmin = isAdmin) }

            // ── Phase 6 লেজি-লোডিং ফিক্স (db-migration-v2) ────────────────────
            // আগে এখানে repo.getContent() দিয়ে পুরো ~১৪,০০০ row Quiz+QBank+Study
            // একসাথে টেনে তারপর subject বের করা হতো — Subject লিস্ট দেখানোর আগেই
            // পুরো fetch শেষ হওয়া লাগতো (৩০/৯০ সেকেন্ড টাইমআউট)। এখন Subject লিস্ট
            // সরাসরি Room-এর reference-টেবিল (Subjects, GAS getReferenceData দিয়ে
            // populate) থেকে আসে — ছোট, দ্রুত, প্রশ্ন ডাউনলোড করা লাগে না। Topic লিস্ট
            // ও প্রশ্ন — নিচে navigateToSubjectLazy()/navigateToSubTopicLazy() দেখো।
            rebuildSubjectsLazy(newMode)
            // ── FIX: "পদবী" (Designation/Post) মোড এখন QBank-এর ডিফল্ট QBank-filter,
            // আর এটা এখন Exam_Appearances-ভিত্তিক নতুন সিস্টেম (rebuildQBankPosts) দিয়ে
            // চলে (subject/subTopic টেক্সট-কলাম-নির্ভর পুরনো ভাঙা সিস্টেম না) — তাই QBank
            // মোডে ঢোকার সাথে সাথেই এটাও প্রি-লোড করে রাখা, নাহলে প্রথমবার "পদবী" চিপ
            // দেখানোর সময় তালিকা খালি থাকতো যতক্ষণ না ইউজার চিপে আলাদাভাবে ট্যাপ করতো ──
            if (newMode == StudyMode.QBANK) rebuildQBankPosts()
            _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Phase 6 — Subject লিস্ট Room-এর reference-টেবিল (Subjects, GAS getReferenceData
     * দিয়ে populate) থেকে সরাসরি লোড করে — কোনো প্রশ্ন ডাউনলোড করা লাগে না, তাই
     * তাৎক্ষণিক। পুরনো rebuildSubjects(content, mode) (পুরো sheet স্ক্যান করে) এখনো
     * আছে (search/model-test-generation ইত্যাদির জন্য), শুধু এখানে আর ব্যবহার হয় না।
     */
    private suspend fun rebuildSubjectsLazy(mode: StudyMode) {
        val sheet = when (mode) {
            StudyMode.QUIZ  -> "Quiz"
            StudyMode.QBANK -> "QBank"
            StudyMode.STUDY -> "Study"
        }

        // ── FIX ("সাবজেক্ট/টপিক ঠিকমতো দেখালেও প্রশ্ন দেখাচ্ছে না" মূল সমস্যা):
        // Subjects রেফারেন্স-টেবিলের "tag_id" কলাম (SubjectEntity.tagId) আর
        // AudienceFilter.subjectVisibleForUser() — দুটোই আগে থেকেই কোডে ছিল, কিন্তু
        // এখানে কখনো ব্যবহারই হতো না! ফলে সব সাবজেক্ট/টপিক সব ইউজারকে দেখানো হতো
        // (audience-নিরপেক্ষ), আর তারপর টপিক খুলে আসল প্রশ্ন আনার সময় সেটা সঠিকভাবে
        // audience অনুযায়ী ফিল্টার হতো — এই দুই ধাপের অসামঞ্জস্যেই "সাবজেক্ট/টপিক
        // দেখাচ্ছে, প্রশ্ন দেখাচ্ছে না" বিভ্রান্তি তৈরি হতো। এখন বর্তমান ইউজারের
        // audience-এর সাথে না মেলা tag_id-ওয়ালা সাবজেক্ট শুরুতেই বাদ পড়বে (এবং তার
        // আন্ডারের টপিকও, যেহেতু সাবজেক্টই "মাদার")। tag_id ফাঁকা থাকা সাবজেক্ট
        // (পুরনো/আনরেস্ট্রিক্টেড) সবার জন্যই দেখাবে, ব্যাকওয়ার্ড-কম্প্যাটিবল। ──
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val tagsById = repo.getRoomTags().associateBy({ it.tagId }, { it.name })

        fun toSubjects(rows: List<com.hanif.smartstudy.data.local.SubjectEntity>) =
            rows
                .filter { s ->
                    com.hanif.smartstudy.util.AudienceFilter.subjectVisibleForUser(s.tagId, tagsById, user, adminTag)
                }
                .map { s ->
                    // totalQ/doneQ এখানে ইচ্ছাকৃতভাবে ০ — গণনা করতে হলে প্রশ্ন ডাউনলোড
                    // করা লাগতো, যেটা ঠিক যেই সমস্যা এড়াতে চাইছি সেটাই আবার তৈরি করত।
                    SubjectEntry(name = s.name, totalQ = 0, doneQ = 0, subTopics = emptyList(), subjectId = s.subjectId)
                }.sortedBy { it.name }

        // ⚠️ BUG FIX ("subject list ashte onek slow"): আগে এখানে repo.syncReferenceData()
        // কে সবসময় await করা হতো, তারপরই Room থেকে subjects পড়ে দেখানো হতো — মানে
        // Subject list দেখা যাওয়ার আগেই প্রতিবার একটা GAS নেটওয়ার্ক রাউন্ড-ট্রিপ শেষ
        // হওয়া লাগতো (Apps Script cold-start সহ কয়েক সেকেন্ড)। এখন Room-এ যা আগে থেকেই
        // আছে সেটা প্রথমে সাথে সাথে দেখানো হয় (instant, ননব্লকিং), তারপর ব্যাকগ্রাউন্ডে
        // syncReferenceData() (নিজের ১০-মিনিট cache-gate সহ) চলে ও নতুন ডেটা এলে আবার
        // আপডেট করে — প্রথমবার/Room খালি থাকলে এখনো ব্লকিং fetch হবে (কিছু দেখানোর
        // মতো ডেটাই নেই বলে), কিন্তু বারবার ভিজিটে আর অপেক্ষা করা লাগবে না।
        val cachedRows = repo.getRoomSubjectsRefBySheet(sheet)
        if (cachedRows.isNotEmpty()) {
            val cachedSubjects = toSubjects(cachedRows)
            Log.d("QuizVM", "rebuildSubjectsLazy mode=$mode subjects=${cachedSubjects.size} (from Room cache, instant)")
            _state.update { it.copy(subjects = cachedSubjects, contentLoaded = true, error = null) }
            // ব্যাকগ্রাউন্ডে ফ্রেশ করো — cache-gate-এর কারণে বেশিরভাগ সময় এটা নেটওয়ার্ক
            // কলই করবে না, gap পার হয়ে গেলে চুপচাপ রিফ্রেশ করবে
            viewModelScope.launch {
                if (repo.syncReferenceData()) {
                    val freshRows = repo.getRoomSubjectsRefBySheet(sheet)
                    if (freshRows.isNotEmpty()) {
                        val freshSubjects = toSubjects(freshRows)
                        _state.update { it.copy(subjects = freshSubjects) }
                    }
                }
            }
            return
        }

        // Room-এ এখনো কিছু নেই (প্রথমবার/ফ্রেশ ইনস্টল) — এবারই একমাত্র সময় যখন
        // Subject list দেখানোর আগে সত্যিই GAS fetch শেষ হওয়া লাগবে
        repo.syncReferenceData()   // idempotent — ব্যর্থ হলে Room-এর পুরনো/খালি ডেটাই থাকবে
        val subjects = toSubjects(repo.getRoomSubjectsRefBySheet(sheet))
        Log.d("QuizVM", "rebuildSubjectsLazy mode=$mode subjects=${subjects.size} (first load)")
        _state.update {
            it.copy(subjects = subjects, contentLoaded = true, error = if (subjects.isEmpty()) "কোনো Subject পাওয়া যায়নি" else null)
        }
        // ⚠️ FIX: আগে এখানে "if (isAdmin) loadReviewProgress()" ছিল — প্রতিবার Subject
        // লিস্ট লোড হওয়ার সময় এটা GAS-এ getReviewProgress কল করতো, যেটা পুরো sheet
        // স্ক্যান করে (ঠিক যেই ভারী-fetch সমস্যা এড়াতে চেয়েছিলাম, সেটাই আবার তৈরি করছিল,
        // Review Mode ব্যবহার না করলেও!)। এখন loadReviewProgress() শুধু toggleReviewMode()
        // থেকেই চলে — যখন Admin সত্যিই Review Mode অন করে, তখনই একবার।
    }

    /**
     * Phase 6 — Subject-এ ঢুকলে Topic লিস্ট Room-এর reference-টেবিল (Topics) থেকে
     * সরাসরি (fast, প্রশ্ন ডাউনলোড ছাড়াই)। subjectId রিজলভ হয় ইতিমধ্যে-লোড হওয়া
     * state.subjects থেকে (নাম মিলিয়ে) — SubjectListScreen-এর onSubject callback
     * এখনো নাম-ভিত্তিক (String) বলে callback-signature বদলাতে হয়নি।
     */
    fun navigateToSubjectLazy(subjectName: String) {
        _state.update { it.copy(navPath = NavPath(subjectName), subTopics = emptyList(), isLoading = true) }
        val subjectId = _state.value.subjects.find { it.name == subjectName }?.subjectId.orEmpty()
        if (subjectId.isBlank()) {
            _state.update { it.copy(isLoading = false, error = "এই Subject-এর ID পাওয়া যায়নি — Admin App-এ Reference ঠিক আছে কিনা দেখো") }
            return
        }
        val mode = _state.value.mode
        viewModelScope.launch {
            val topicRows = repo.getRoomTopicsForSubject(subjectId)
            val subTopics = topicRows.map { t ->
                // ── FIX ("Article: 74 প্রশ্ন" দেখাতো, Quiz-এ ঢুকলে ভিতরে ২৩টা): t.rowCount
                // (generic legacy কলাম) সবসময় Study sheet-এর কাউন্ট বহন করতো, মোড যাই হোক
                // না কেন। এখন বর্তমান StudyMode অনুযায়ী সঠিক per-sheet কলাম বেছে নেওয়া হচ্ছে
                // — নতুন কলাম এখনো ০ থাকলে (rebuildIndex পুরনো ভার্সনে চলেছিল/এখনো চলেনি,
                // per-sheet কলাম ফাঁকা) legacy rowCount-এ fallback করে, যাতে rebuildIndex
                // নতুন করে না চালানো পর্যন্ত পুরোপুরি ০ না দেখায়। ──
                val perSheetCount = when (mode) {
                    StudyMode.QUIZ  -> t.rowCountQuiz
                    StudyMode.QBANK -> t.rowCountQbank
                    StudyMode.STUDY -> t.rowCountStudy
                }
                SubTopicEntry(
                    name      = t.name,
                    subject   = subjectName,
                    totalQ    = if (perSheetCount > 0) perSheetCount else t.rowCount,
                    doneQ     = 0,
                    subjectId = t.subjectId,
                    topicId   = t.topicId
                )
            }.sortedBy { it.name }
            Log.d("QuizVM", "navigateToSubjectLazy: $subjectName ($subjectId) topics=${subTopics.size}")
            _state.update { it.copy(subTopics = subTopics, isLoading = false) }
        }
    }

    /**
     * Phase 6 — Topic-এ ঢুকলে প্রশ্ন Room-cache (progressive-fill) থেকে, নতুন ব্যাচ
     * অগ্রাধিকার সহ। topicId রিজলভ হয় state.subTopics থেকে (নাম মিলিয়ে)।
     *
     * আচরণ:
     * - অনলাইন + এই Topic এখনো সম্পূর্ণ লোকালে আসেনি → GAS `getQuestionsPage` থেকে
     *   পরের **নতুন** ৫০-ব্যাচ (আগের cursor থেকে, কখনো একই ব্যাচ দুইবার না) Room-এ
     *   যোগ হয়, তারপর Room-এ যা যা জমেছে (পুরনো+নতুন) সব একসাথে দেখানো হয়।
     * - অনলাইন + Topic সম্পূর্ণ লোকালে (hasMore=false) → নেটওয়ার্ক কল-ই হয় না, সরাসরি
     *   Room থেকে instant।
     * - অফলাইন (নেটওয়ার্ক এক্সসেপশন) → চুপচাপ ধরে Room-এ যা আছে তাই দেখানো হয়।
     */
    fun navigateToSubTopicLazy(topicName: String) {
        val subject = _state.value.navPath.subject ?: return
        var topicId = _state.value.subTopics.find { it.name == topicName }?.topicId.orEmpty()
        timerJob?.cancel()

        // আগের টপিকের জন্য চলমান লোড থাকলে সাথে সাথে বাতিল করো, আর এই রিকোয়েস্টের
        // নিজস্ব token নাও — নিচে রেসপন্স ফেরার পর এই token দিয়ে স্টেল-চেক হবে।
        subTopicLoadJob?.cancel()
        val myToken = ++subTopicLoadToken

        // ── FIX: টাইটেল/হেডার সাথে সাথে নতুন টপিকের নাম দেখায়, কিন্তু questions লিস্ট
        // খালি না করলে যতক্ষণ নতুন ডেটা না আসে ততক্ষণ পুরনো টপিকের প্রশ্নই নিচে দেখা
        // যেত (title=নতুন টপিক, content=পুরনো টপিক — এটাই বিভ্রান্তির আসল কারণ)।
        // এখন টপিক পাল্টানোর মুহূর্তেই লিস্ট খালি করে দেওয়া হচ্ছে, তাই নতুন ডেটা না
        // আসা পর্যন্ত স্ক্রিন লোডিং/খালি দেখাবে, কখনো ভুল টপিকের প্রশ্ন দেখাবে না। ──
        _state.update {
            it.copy(
                navPath        = NavPath(subject, topicName),
                currentPage    = 0,
                isLoading      = true,
                questions      = emptyList(),
                totalQuestions = 0,
                answeredCount  = 0,
                showResult     = false,
                result         = null,
                error          = null
            )
        }
        subTopicLoadJob = viewModelScope.launch {
            val sheet = when (_state.value.mode) {
                StudyMode.QUIZ  -> "Quiz"
                StudyMode.QBANK -> "QBank"
                StudyMode.STUDY -> "Study"
            }

            // ── FIX ("Submit → ad দেখানোর পর টপিকে ঢুকলে প্রশ্ন ফাঁকা দেখায়, Back-এও
            // একই সমস্যা"): আগে topicId শুধু state.subTopics থেকেই খোঁজা হতো, সরাসরি
            // এই ফাংশনের শুরুতেই (কোনো coroutine ছাড়া)। কিন্তু Result থেকে ফেরার সময়
            // navigateBack() নিজেই আগে navPath আপডেট করে তারপর rebuildSubTopics() একটা
            // আলাদা coroutine-এ async ভাবে চালায় — সেই coroutine শেষ হওয়ার আগেই যদি এই
            // ফাংশন কল হয় (যেমন Ad dismiss হওয়ার সাথে সাথেই বা onRetry-তে) তাহলে
            // state.subTopics তখনো পুরনো/খালি থাকতে পারে, topicId ফাঁকা থেকে যেত, আর
            // "এই Topic-এর ID পাওয়া যায়নি" এরর সেট হয়ে সাথে সাথেই রিটার্ন করে ফেলত —
            // ফলাফলে প্রশ্ন-লিস্ট চিরকাল ফাঁকাই থেকে যেত। এখন state.subTopics-এ না
            // পেলে সরাসরি Room থেকে (subjectId + topicName দিয়ে) topicId রিজলভ করার
            // চেষ্টা করা হয় — এটা coroutine-এর ভেতরে হওয়ায় rebuildSubTopics()-এর race
            // থাকে না। ──
            if (topicId.isBlank()) {
                val subjectId = _state.value.subjects.find { it.name == subject }?.subjectId
                    ?.takeIf { it.isNotBlank() }
                    ?: repo.resolveSubjectId(sheet, subject)
                if (!subjectId.isNullOrBlank()) {
                    topicId = repo.resolveTopicId(subjectId, topicName).orEmpty()
                }
            }

            if (myToken != subTopicLoadToken) return@launch

            if (topicId.isBlank()) {
                _state.update { it.copy(isLoading = false, error = "এই Topic-এর ID পাওয়া যায়নি — আবার চেষ্টা করুন") }
                return@launch
            }

            val user     = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val tag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
                .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }

            // অনলাইন + অসম্পূর্ণ হলে নতুন ব্যাচ যোগ করার চেষ্টা — ব্যর্থ হলে (অফলাইন/নেটওয়ার্ক
            // এরর) চুপচাপ ধরে নিয়ে Room-এ যা আছে তাই দেখানো হবে, ক্র্যাশ করবে না
            try {
                repo.cacheNextTopicBatch(sheet, topicId)
            } catch (e: Exception) {
                Log.w("QuizVM", "cacheNextTopicBatch failed (offline?): ${e.message}")
            }

            // ── FIX: নেটওয়ার্ক কল চলাকালীন ইউজার অন্য টপিকে চলে গেলে (myToken আর
            // বর্তমান subTopicLoadToken এক থাকবে না) — এই stale রেসপন্স দিয়ে state
            // update করা চলবে না, নাহলে এটাই ছিল বাগ: পুরনো টপিকের প্রশ্ন পরে এসে
            // নতুন টপিকের প্রশ্ন ওভাররাইট করে ফেলতো। ──
            if (myToken != subTopicLoadToken) {
                Log.d("QuizVM", "navigateToSubTopicLazy: stale response for $topicName ($topicId) ignored")
                return@launch
            }

            val bookmarks = _state.value.bookmarkedIds
            // ── FIX ("এক পেজে ৫০ না, সব একসাথে আসছে" সমস্যা): আগে এখানে
            // getRoomQuestionsForTopic() দিয়ে Room-এ ক্যাশ হওয়া টপিকের ALL প্রশ্ন
            // একসাথে state.questions-এ বসানো হতো (পেজিনেশন ছাড়াই) — তাই ১ম পাতাতেই
            // সব দেখা যেত, আর "পরবর্তী" চাপলে (goToPage) সম্পূর্ণ ভিন্ন subject/subTopic
            // টেক্সট-ভিত্তিক query চলতো যেটা কিছুই খুঁজে পেত না (ফাঁকা স্ক্রিন)। এখন
            // শুরুতেই শুধু প্রথম ৫০টা (PAGE_SIZE) topicId দিয়ে paginate করে আনা হয়,
            // goToPage()-ও এখন একই topicId-ভিত্তিক পাথ ব্যবহার করে (দেখো
            // loadQuestionsFromRoomByTopic) — দুটো ধাপ একই, সামঞ্জস্যপূর্ণ ডেটা-পাথ। ──
            val total = repo.getRoomTotalCountByTopic(sheet, topicId, tag)
            val items = repo.getRoomPagedQuestionsByTopic(sheet, topicId, tag, 0, PAGE_SIZE).map { q ->
                q.copy(
                    isBookmarked = bookmarks.contains(q.id),
                    isWeakTopic  = isWeak(q.subTopic),
                    isStudyDone  = isStudyDone(q.id)
                )
            }
            Log.d("QuizVM", "navigateToSubTopicLazy: $topicName ($topicId) cached=$total loaded_page1=${items.size}")

            // দ্বিতীয়বার চেক — Room থেকে items বের করতেও কিছুটা সময় লাগে, ততক্ষণে
            // আবার টপিক পাল্টে যেতে পারে, তাই state বসানোর ঠিক আগে আবার token যাচাই
            if (myToken != subTopicLoadToken) {
                Log.d("QuizVM", "navigateToSubTopicLazy: stale response (post-fetch) for $topicName ($topicId) ignored")
                return@launch
            }

            if (total == 0) {
                _state.update { it.copy(isLoading = false, error = "কোনো প্রশ্ন পাওয়া যায়নি — ইন্টারনেট চেক করো") }
                return@launch
            }
            _state.update {
                it.copy(
                    questions      = items,
                    totalQuestions = total,
                    currentPage    = 0,
                    isQuizActive   = true,
                    showResult     = false,
                    result         = null,
                    answeredCount  = 0,
                    timerSec       = 0,
                    isLoading      = false
                )
            }
            startTimer(total)
        }
    }

    // ═════════════════════════════════════════════════════════
    // Review System (Admin-only) — student-দের UI/behavior-এ কোনো প্রভাব নেই, সব
    // ফাংশন শুরুতেই isAdmin চেক করে চুপচাপ রিটার্ন করে যদি admin না হয়।
    // ═════════════════════════════════════════════════════════

    /** Review Mode টগল — চালু থাকলে QuestionListScreen-এ প্রতিটা প্রশ্নে বড় ✓ বাটন দেখা যায়। */
    fun toggleReviewMode() {
        if (!_state.value.isAdmin) return
        _state.update { it.copy(isReviewMode = !it.isReviewMode) }
        if (_state.value.isReviewMode) loadReviewProgress()
    }

    /**
     * একটা প্রশ্নকে reviewed মার্ক/আনমার্ক করে — GAS-এ লেখে (source of truth) + Room +
     * local state সব সিঙ্ক রাখে। Optimistic update: UI সাথে সাথে বদলায়, নেটওয়ার্ক ব্যর্থ
     * হলে চুপচাপ রিভার্ট হয়ে যায়।
     */
    fun markReviewed(questionId: String, reviewed: Boolean = true) {
        if (!_state.value.isAdmin) return
        val q = _state.value.questions.find { it.id == questionId } ?: return
        val sheet = when (_state.value.mode) {
            StudyMode.QUIZ  -> "Quiz"
            StudyMode.QBANK -> "QBank"
            StudyMode.STUDY -> "Study"
        }
        val nowMs = System.currentTimeMillis()
        _state.update {
            it.copy(questions = it.questions.map { item ->
                if (item.id == questionId) item.copy(reviewed = reviewed, reviewedAt = if (reviewed) nowMs else 0L) else item
            })
        }
        viewModelScope.launch {
            val ok = repo.markQuestionReviewed(sheet, q.id, reviewed)
            if (!ok) {
                _state.update {
                    it.copy(
                        questions = it.questions.map { item ->
                            if (item.id == questionId) item.copy(reviewed = !reviewed, reviewedAt = 0L) else item
                        },
                        error = "রিভিউ মার্ক করা যায়নি — নেটওয়ার্ক চেক করো"
                    )
                }
            } else {
                loadReviewProgress()
            }
        }
    }

    /** Review progress (subject+topic %) রিফ্রেশ — হালকা GAS কল, পুরো প্রশ্ন ডাউনলোড হয় না। */
    fun loadReviewProgress() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            val sheet = when (_state.value.mode) {
                StudyMode.QUIZ  -> "Quiz"
                StudyMode.QBANK -> "QBank"
                StudyMode.STUDY -> "Study"
            }
            val progress = repo.getReviewProgress(sheet)
            _state.update { it.copy(reviewProgressSubjects = progress.subjects, reviewProgressTopics = progress.topics) }
        }
    }

    // ── navigateToSubject/navigateToSubTopic — এখনো রাখা হয়েছে কারণ MainScreen.kt-এর
    // deep-link/focus-navigation (নোটিফিকেশন থেকে বা search থেকে এসে সরাসরি একটা
    // subject/subTopic-এ ঢোকা) এই ঠিক এই নামেই কল করে। CoreScreen.kt-এর সাধারণ
    // ব্রাউজিং ফ্লো এখন ওপরের navigateToSubjectLazy()/navigateToSubTopicLazy()
    // ব্যবহার করে — এই দুটো এখন শুধু deep-link/legacy call site গুলোর জন্য। ──
    fun navigateToSubject(subject: String) {
        _state.update { it.copy(navPath = NavPath(subject)) }
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            rebuildSubTopics(content, subject, _state.value.mode)
        }
    }

    fun navigateToSubTopic(subTopic: String) {
        val subject = _state.value.navPath.subject ?: return
        _state.update { it.copy(navPath = NavPath(subject, subTopic), currentPage = 0) }
        viewModelScope.launch {
            // Room-এ data আছে কিনা চেক করো
            val sheet = _state.value.mode.name
            val user  = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val tag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
                .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }

            val roomCount = repo.getRoomTotalCount(sheet, subject, subTopic, tag)

            if (roomCount > 0) {
                // ── Room-first: instant load ──────────────────────────────────
                Log.d("QuizVM", "Room hit: $roomCount questions for $subject/$subTopic")
                loadQuestionsFromRoom(sheet, subject, subTopic, tag, page = 0)

                // Background-এ Firebase sync (REALTIME_DATA=true হলে)
                if (BuildConfig.REALTIME_DATA) {
                    launch(Dispatchers.IO) {
                        val content = (repo.getContent() as? DataState.Success)?.data
                        if (content != null) loadQuestions(content, subject, subTopic, _state.value.mode)
                    }
                }
            } else {
                // ── Room-এ নেই → Firebase থেকে আনো ─────────────────────────
                Log.d("QuizVM", "Room miss: fetching from Firebase for $subject/$subTopic")
                val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
                loadQuestions(content, subject, subTopic, _state.value.mode)
            }
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
    /**
     * ── FIX ("Admin delete করলে প্রশ্ন কার্ড সাথে সাথে হারায় না, উপরের জায়গা খালি
     * থাকে যতক্ষণ না full refresh আসে"): MenuViewModel.adminDeleteQuestion() ডিলিট
     * নিজে async ভাবে করে আর শুধু contentEditVersion বাড়ায় — MainScreen-এর
     * LaunchedEffect(contentEditVersion) তখন adminRefreshContent() কল করে, যেটা
     * Room থেকে পুরো টপিক আবার fetch করে (নেটওয়ার্ক/DB রাউন্ড-ট্রিপ, তাই ইনস্ট্যান্ট
     * না)। এই ফাংশনটা admin delete-এর ঠিক পরপরই MainScreen থেকে সরাসরি কল হয়
     * (adminRefreshContent()-এর আগেই) — state.questions থেকে আইটেমটা সাথে সাথেই
     * ফিল্টার করে বাদ দেয়, তাই নিচের প্রশ্নগুলো নিজে থেকেই ওপরে উঠে জায়গা পূরণ করে,
     * কোনো ফাঁকা গ্যাপ দেখা যায় না। totalQuestions/subTopics/subjects-এর গণনাও
     * সাথে সাথেই ১ কমিয়ে দেওয়া হয় যাতে প্রগ্রেস % ভুল না দেখায়। পরে
     * adminRefreshContent() এসে Room থেকে আসল/চূড়ান্ত ডেটা দিয়ে সবকিছু ঠিক করে দেবে।
     */
    fun removeQuestionLocally(rowKey: String) {
        val current = _state.value
        val target  = current.questions.find { it.id == rowKey } ?: return
        _state.update {
            it.copy(
                questions      = it.questions.filterNot { q -> q.id == rowKey },
                totalQuestions = (it.totalQuestions - 1).coerceAtLeast(0),
                subTopics      = it.subTopics.map { st ->
                    if (st.name == target.subTopic || st.name == current.navPath.subTopic)
                        st.copy(totalQ = (st.totalQ - 1).coerceAtLeast(0))
                    else st
                },
                subjects       = it.subjects.map { subj ->
                    if (subj.name == target.subject || subj.name == current.navPath.subject)
                        subj.copy(totalQ = (subj.totalQ - 1).coerceAtLeast(0))
                    else subj
                }
            )
        }
    }

    /**
     * ── Admin "Move Question(s)" — removeQuestionLocally()-এরই বাল্ক ভার্সন। Move
     * confirm হওয়ার সাথে সাথেই (menuViewModel.adminMoveQuestions() এর network/Room কল
     * শেষ হওয়ার অপেক্ষা না করে) MainScreen থেকে এটা কল হয় — সরানো প্রশ্নগুলো
     * state.questions থেকে সাথে সাথে বাদ পড়ে, LazyColumn-এর animateItemPlacement()
     * (দেখো QuestionCard-এর modifier) বাকি প্রশ্নগুলোকে নিজে থেকেই স্মূথলি ওপরে তুলে
     * আনে — কোনো ফাঁকা গ্যাপ বা রিফ্রেশের অপেক্ষা ছাড়াই "সরানো হয়েছে" তাৎক্ষণিক অনুভূত হয়।
     *
     * এখনকার Topic-এর subTopics এন্ট্রির totalQ-ও সাথে সাথেই কমে যায় — এটা ০-তে নেমে
     * গেলে (এই Topic-এর সব প্রশ্নই move হয়ে গেছে) সেই Topic subTopics লিস্ট থেকে সাথে
     * সাথেই সরে যায় (নেভিগেট করে subject-এ আবার ঢোকার অপেক্ষা করা লাগে না) —
     * navigateToSubjectLazy()-এর লাইভ-কাউন্ট ফিল্টারের সাথে সামঞ্জস্যপূর্ণ আচরণ।
     * পরে adminRefreshContent() এসে Room থেকে আসল/চূড়ান্ত ডেটা দিয়ে সবকিছু নিশ্চিত করে দেবে।
     */
    fun removeQuestionsLocally(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet   = ids.toSet()
        val current = _state.value
        val removed = current.questions.filter { it.id in idSet }
        if (removed.isEmpty()) return
        val countBySubTopic = removed.groupingBy { it.subTopic.ifBlank { current.navPath.subTopic.orEmpty() } }.eachCount()
        val countBySubject  = removed.groupingBy { it.subject.ifBlank { current.navPath.subject.orEmpty() } }.eachCount()
        _state.update {
            it.copy(
                questions      = it.questions.filterNot { q -> q.id in idSet },
                totalQuestions = (it.totalQuestions - removed.size).coerceAtLeast(0),
                subTopics      = it.subTopics.mapNotNull { st ->
                    val dec = countBySubTopic[st.name] ?: 0
                    if (dec <= 0) return@mapNotNull st
                    val newTotal = (st.totalQ - dec).coerceAtLeast(0)
                    if (newTotal <= 0) null else st.copy(totalQ = newTotal)   // শূন্য হলে সাথে সাথেই লিস্ট থেকে হাইড
                },
                subjects       = it.subjects.map { subj ->
                    val dec = countBySubject[subj.name] ?: 0
                    if (dec <= 0) subj else subj.copy(totalQ = (subj.totalQ - dec).coerceAtLeast(0))
                }
            )
        }
    }

    fun adminRefreshContent() {
        viewModelScope.launch {
            val path = _state.value.navPath

            // ── FIX: টপিক-প্রশ্ন-স্ক্রিনে থাকা অবস্থায় (path.subTopic সেট) Room-এর
            // topicId দিয়ে রিফ্রেশ করো — এটাই আসলে স্ক্রিনে যা দেখানো হয় তার
            // উৎস (cacheNextTopicBatch/getRoomQuestionsForTopic, Phase 6)। আগে এখানে
            // পুরনো subject/subTopic নাম-ভিত্তিক bulk content ব্যবহার হতো, যেটা
            // QBank-এ কখনো মেলেনি (QBank শীটে subject/subTopic নামের কলামই নেই,
            // শুধু subject_id/topic_id) — ফলে QBank-এ এডিট করলে GAS-এ সেভ হতো
            // ঠিকই, স্ক্রিনে সাথে সাথে দেখা যেত না। ──
            if (path.subTopic != null && path.subject != null) {
                val topicId = _state.value.subTopics.find { it.name == path.subTopic }?.topicId
                if (!topicId.isNullOrBlank()) {
                    val sheet = when (_state.value.mode) {
                        StudyMode.QUIZ  -> "Quiz"
                        StudyMode.QBANK -> "QBank"
                        StudyMode.STUDY -> "Study"
                    }
                    refreshQuestionsInPlaceFromRoom(sheet, topicId)
                    return@launch
                }
                // topicId পাওয়া না গেলে (পুরনো/legacy টপিক) নিচের পুরনো path-এ fallback
            }

            // ── আগে fetch ব্যর্থ হলে খালি AppContent() দিয়ে বিদ্যমান লিস্ট
            // replace হয়ে যেত (কারণ ঠিক এর আগে cache.clearCache()/clearMemCache()
            // চলে বলে fallback করার মতো কোনো cache-ও থাকতো না) — ফলে edit/delete/
            // rename করার পরপরই একটা transient network/Sheet ব্যর্থতায় পুরো
            // subject/QBank/Study list "উধাও" হয়ে যেত, যদিও আসল ডেটা অক্ষত ছিল।
            // এখন fetch ব্যর্থ হলে screen-এর বিদ্যমান state-ই অক্ষত থাকবে,
            // পরের successful refresh না আসা পর্যন্ত কিছু bদলাবে না। ──
            val content = (repo.getContent() as? DataState.Success)?.data
            if (content == null) {
                Log.w("QuizVM", "adminRefreshContent: fetch failed, বিদ্যমান content অক্ষত রাখা হলো")
                return@launch
            }
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
     * Room থেকে বর্তমান টপিকের সদ্য-আপডেট প্রশ্নগুলো এনে in-place বসিয়ে দেয় —
     * refreshQuestionsInPlace()-এর মতোই (timer/answered/result কিছু ছোঁয় না),
     * শুধু matching নাম দিয়ে না, topicId দিয়ে — তাই QBank-সহ সব মোডেই নির্ভরযোগ্য।
     */
    private suspend fun refreshQuestionsInPlaceFromRoom(sheet: String, topicId: String) {
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val tag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
            .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
        val fresh = repo.getRoomQuestionsForTopic(sheet, topicId, tag).associateBy { it.id }

        _state.update { st ->
            st.copy(questions = st.questions.map { existing ->
                fresh[existing.id]?.copy(
                    answerState  = existing.answerState,
                    isBookmarked = existing.isBookmarked,
                    isWeakTopic  = existing.isWeakTopic,
                    isStudyDone  = existing.isStudyDone
                ) ?: existing
            })
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
            // Model Test list খোলা ছিল (এখনো কোনো টেস্ট শুরু হয়নি) → subject picker এ ফিরে যাও
            // (Job ইউজারের ক্ষেত্রে subject picker ছিলই না — সরাসরি বন্ধ করে বেস লিস্টে ফিরে যাও)
            _state.value.isModelTestZone -> _state.update {
                it.copy(isModelTestZone = false, modelTests = emptyList(),
                         modelTestSubject = "", pendingModelTestType = null,
                         isModelTestSubjectPicker = !it.isModelTestJobUser)
            }
            // Model Test subject picker খোলা ছিল → পুরোপুরি বন্ধ, বেস subject list এ ফিরে যাও
            _state.value.isModelTestSubjectPicker -> _state.update {
                it.copy(isModelTestSubjectPicker = false, modelTestSubjectList = emptyList())
            }
            _state.value.isMockZone -> _state.update {
                it.copy(isMockZone = false, navPath = NavPath(), isQuizActive = false,
                        result = null, showResult = false, timerSec = 0)
            }
            _state.value.showResult -> {
                val fromModelTest = _state.value.activeModelTest != null
                val fromMock      = path.subject == "Mock Test"
                // মডেল টেস্ট হলে NavPath-এ যে "subject" থাকে সেটা display label (যেমন "সকল বিষয় (মিশ্র)"),
                // কিন্তু স্টোরেজ/জোন খুলতে আসল subjectKey লাগে — সেটা modelTestSubject-এ এখনো ধরা আছে
                val modelSubjectKey = _state.value.modelTestSubject
                _state.update {
                    it.copy(showResult = false, isQuizActive = false, result = null,
                            navPath = if (fromMock) NavPath() else NavPath(path.subject), timerSec = 0,
                            activeModelTest = null, activeModelTestType = null)
                }
                // উত্তর দেওয়ার পর progress আপডেট হয়েছে — সঠিক লিস্টে ফিরে যাও
                if (fromModelTest) {
                    if (modelSubjectKey.isNotBlank()) openModelTestZone(modelSubjectKey)
                } else if (fromMock) {
                    // Mock Test — "Mock Test" কোনো আসল subject না, তাই subTopic rebuild না করে
                    // সরাসরি বেস subject list এ ফিরে যাও (subjects আগে থেকেই লোড করা আছে)।
                    viewModelScope.launch {
                        val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
                        rebuildSubjects(content, _state.value.mode)
                    }
                } else if (path.subject != null) {
                    viewModelScope.launch {
                        val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
                        rebuildSubTopics(content, path.subject, _state.value.mode)
                    }
                }
            }
            // Model Test চলাকালীন (submit না করে) back চাপলে → subTopic list না, Model Test list এ ফিরে যাও
            _state.value.activeModelTest != null && path.subTopic != null -> {
                val modelSubjectKey = _state.value.modelTestSubject
                _state.update {
                    it.copy(navPath = NavPath(path.subject), isQuizActive = false,
                             activeModelTest = null, activeModelTestType = null)
                }
                if (modelSubjectKey.isNotBlank()) openModelTestZone(modelSubjectKey)
            }
            // Mock Test চলাকালীন (submit না করে) back চাপলে → "Mock Test" নামের ভুয়া subTopic
            // list খুঁজতে যাওয়া যাবে না, সরাসরি বেস subject list এ ফিরে যাও।
            path.subject == "Mock Test" -> {
                _state.update {
                    it.copy(navPath = NavPath(), isQuizActive = false, questions = emptyList(), timerSec = 0)
                }
            }
            // ⚠️ ফিক্স ("টপিকে ক্লিক করলে ফাঁকা দেখাচ্ছে, Back চাপলে টপিক লিস্টও হারিয়ে
            // যাচ্ছে"): আগে এখানে পুরনো rebuildSubTopics(repo.getContent(), ...) কল হতো —
            // কিন্তু Phase 6 লেজি-লোডিং আর্কিটেকচারে (দেখো rebuildSubjectsLazy/
            // navigateToSubjectLazy-এর ওপরের কমেন্ট) repo.getContent()-এর পুরনো
            // full-content cache আর সরাসরি populate হয় না, তাই সেটা প্রায়ই খালি
            // AppContent() ফেরত দিত আর subTopics খালি হয়ে যেত। এখন forward-navigation-এর
            // মতোই navigateToSubjectLazy() ব্যবহার করছি (Room-এর Topics reference-টেবিল
            // থেকে, দ্রুত) — যাতে Back-এ ঠিক একই টপিক লিস্ট আবার দেখা যায়।
            path.subTopic != null -> {
                if (path.subject != null) {
                    navigateToSubjectLazy(path.subject)
                } else {
                    _state.update { it.copy(navPath = NavPath()) }
                }
            }
            path.subject  != null -> {
                _state.update { it.copy(navPath = NavPath()) }
                viewModelScope.launch { rebuildSubjectsLazy(_state.value.mode) }
            }
            else -> {}
        }
    }

    /**
     * QBank subject list-এর নিচে "🏆 মডেল টেস্ট" বাটনে ট্যাপ করলে কল হয়।
     * সোর্স সবসময় **Quiz sheet** (QBank sheet না) — এন্ট্রি পয়েন্টটা শুধু QBank স্ক্রিনে থাকে,
     * কিন্তু প্রশ্ন আসে Quiz থেকে।
     *   - Job seeker  → subject picker স্কিপ, Quiz-এর সব সাবজেক্ট মিশিয়ে একটাই ব্যাচ (JOB_ALL_KEY)
     *   - Student     → নিজের classLevel-এ Quiz-এ যেসব সাবজেক্ট আছে তার একটা বাছাই করতে হয়
     */
    fun openModelTestPicker() {
        viewModelScope.launch {
            val user = session.getCurrentUser()
            val isJob = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
                .equals("Job", ignoreCase = true)

            if (isJob) {
                _state.update { it.copy(isModelTestJobUser = true) }
                openModelTestZone(LocalModelTestStore.JOB_ALL_KEY)
                return@launch
            }

            _state.update { it.copy(isModelTestJobUser = false, isModelTestSubjectPicker = true,
                modelTestSubjectList = emptyList()) }

            val content  = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val filtered = content.forUser(user, adminTag)
            // ইউজারের classLevel অনুযায়ী Quiz sheet-এ যেসব সাবজেক্ট আছে (audience-filtered)
            val subjects = filtered.quiz.mapNotNull { it.subject }.filter { it.isNotBlank() }.toSet().sorted()

            val list = subjects.map { subj -> subj to localModelTestStore.countFor(subj) }

            _state.update { it.copy(modelTestSubjectList = list) }
        }
    }

    // ═════════════════════════════════════════════════════════
    // Model Test — ইউজার নিজে জেনারেট করে, শুধু এই ডিভাইসে সংরক্ষিত (LocalModelTestStore)।
    // প্রশ্নের পুল সবসময় Quiz sheet থেকে আসে।
    // ═════════════════════════════════════════════════════════

    /** Subject picker থেকে একটা subject বাছাই করলে (বা Job ইউজারের জন্য সরাসরি) কল হয় */
    fun openModelTestZone(subjectKey: String) {
        _state.update {
            it.copy(isModelTestZone = true, isModelTestSubjectPicker = false,
                     modelTestSubject = subjectKey, pendingModelTestType = null, modelTestGenWarning = null)
        }
        viewModelScope.launch {
            _state.update { it.copy(modelTests = localModelTestStore.getTests(subjectKey)) }
        }
    }

    /** "+ নতুন মডেল টেস্ট বানান" বাটনে ট্যাপ করলে ফর্ম খোলে */
    fun openModelTestGenerateSheet() {
        _state.update { it.copy(showModelTestGenerateSheet = true) }
    }

    fun dismissModelTestGenerateSheet() {
        _state.update { it.copy(showModelTestGenerateSheet = false) }
    }

    /**
     * ফর্ম সাবমিট করলে কল হয় — Quiz sheet থেকে পুল বানিয়ে ModelTestGenerator দিয়ে জেনারেট করে
     * LocalModelTestStore-এ সেভ করে (আগের ব্যাচ থাকলে রিপ্লেস হয়ে যায়)।
     *
     * subjectKey == JOB_ALL_KEY হলে Quiz-এর সব সাবজেক্ট মিশিয়ে পুল বানানো হয় (Job ইউজার),
     * নাহলে শুধু ওই একটা সাবজেক্টের সব সাবটপিক মিলিয়ে পুল বানানো হয় (Student ইউজার)।
     */
    fun generateLocalModelTests(type: String, perTest: Int, count: Int) {
        val subjectKey = _state.value.modelTestSubject
        if (subjectKey.isBlank()) return

        _state.update { it.copy(isGeneratingModelTest = true, modelTestGenWarning = null) }
        viewModelScope.launch {
            val content  = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            val user     = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val filtered = content.forUser(user, adminTag)

            val quizItems = if (subjectKey == LocalModelTestStore.JOB_ALL_KEY)
                filtered.quiz
            else
                filtered.quiz.filter { it.subject == subjectKey }

            var pool = quizItems.map { QuestionItem.fromQuizItem(it) }
            if (type != "both") {
                pool = pool.filter { if (type == "written") it.isWritten() else it.isMcq() }
            }

            val result = com.hanif.smartstudy.util.ModelTestGenerator.generate(pool, count, perTest)

            val displaySubject = if (subjectKey == LocalModelTestStore.JOB_ALL_KEY)
                LocalModelTestStore.JOB_ALL_LABEL else subjectKey

            val now = System.currentTimeMillis()
            val tests = result.tests.map { g ->
                ModelTestMeta(
                    subject    = displaySubject,
                    testNumber = g.testNumber,
                    title      = "মডেল টেস্ট ${g.testNumber}",
                    type       = type,
                    totalMarks = g.questionKeys.size,
                    questionIds = g.questionKeys,
                    createdAt  = now
                )
            }

            localModelTestStore.saveTests(subjectKey, tests)

            _state.update {
                it.copy(
                    modelTests = tests,
                    isGeneratingModelTest = false,
                    showModelTestGenerateSheet = false,
                    modelTestGenWarning = result.warning
                )
            }
        }
    }

    /** Model Test list থেকে একটা টেস্টে ট্যাপ — type "both" হলে MCQ/Written bottom sheet দেখাও */
    fun selectModelTest(test: ModelTestMeta) {
        if (test.type == "both") {
            _state.update { it.copy(pendingModelTestType = test) }
        } else {
            startModelTest(test, test.type)
        }
    }

    fun dismissModelTestTypePicker() {
        _state.update { it.copy(pendingModelTestType = null) }
    }

    /** Model Test শুরু করো — questionIds ("Quiz|id") resolve করে audience-filtered Quiz pool থেকে প্রশ্ন বসাও */
    fun startModelTest(test: ModelTestMeta, chosenType: String) {
        viewModelScope.launch {
            val content  = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            val user     = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val filtered = content.forUser(user, adminTag)

            val quizPool = filtered.quiz.map { QuestionItem.fromQuizItem(it) }.associateBy { it.sourceKey() }

            val resolved = test.questionIds.mapNotNull { key -> quizPool[key] }
            val typeFiltered = if (test.type == "both") {
                resolved.filter { if (chosenType == "written") it.isWritten() else it.isMcq() }
            } else resolved

            val questions = typeFiltered.map { it.copy(isWeakTopic = isWeak(it.subTopic)) }

            _state.update {
                it.copy(
                    isModelTestZone      = false,
                    pendingModelTestType = null,
                    questions            = questions,
                    isQuizActive         = true,
                    showResult           = false,
                    result               = null,
                    answeredCount        = 0,
                    navPath              = NavPath(test.subject, test.displayTitle()),
                    activeModelTest      = test,
                    activeModelTestType  = chosenType
                )
            }
            startTimer(questions.size)
        }
    }

    /** ResultModal-এর "আবার চেষ্টা" — Model Test হলে একই টেস্ট আবার শুরু করে */
    fun retryModelTest() {
        val mt = _state.value.activeModelTest ?: return
        val type = _state.value.activeModelTestType ?: (if (mt.type == "both") "mcq" else mt.type)
        startModelTest(mt, type)
    }

    /** Mock Test রেজাল্ট স্ক্রিন থেকে "আবার দাও" — আগের mockConfig (selectedTopics/limit)
     *  দিয়েই নতুন করে র‍্যান্ডম প্রশ্ন সেট শুরু হবে। */
    fun retryMock() {
        startMock()
    }

    /**
     * @param mode Home থেকে Quiz/QBank ট্যাব প্রথমবার ভিজিট না করেই সরাসরি Mock Test
     * খোলা হলে এই ViewModel এর state.mode তখনো ডিফল্ট (QUIZ) থাকতে পারে। সেক্ষেত্রে
     * CoreScreen এর mode-sync LaunchedEffect (state.mode != mode হলে setMode() কল করে)
     * পরে গিয়ে এই isMockZone=true ফ্ল্যাগটা রিসেট করে ফেলত (setMode সবসময় isMockZone=false
     * করে দেয়)। তাই এখানেই mode সেট করে দেওয়া হচ্ছে যাতে LaunchedEffect এর শর্ত মিলে যায়
     * এবং setMode() আর কল না হয়।
     */
    fun openMockZone(mode: StudyMode = _state.value.mode) {
        _state.update { it.copy(mode = mode, isMockZone = true, navPath = NavPath()) }
        viewModelScope.launch {
            val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
            rebuildSubjects(content, mode, forMock = true)
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
            // ── লোকাল থেকে প্রশ্ন নেওয়া নিশ্চিত করা ──
            // repo.getContent() নিজে আগে memory cache / disk cache চেক করে, কিন্তু Home
            // থেকে সরাসরি Mock Test শুরু করলে (Quiz/QBank ট্যাব কখনো ভিজিট না করেই) কোনো
            // মুহূর্তে cache miss হলে খালি AppContent() পড়ে যেতে পারত। তাই এখানে আগে
            // shared memory cache (ContentRepository.getMemCache()) সরাসরি চেক করি —
            // সেটা থাকলে নেটওয়ার্ক/সাসপেন্ড কল ছাড়াই তাৎক্ষণিক লোকাল ডেটা পাওয়া যায়।
            val content = com.hanif.smartstudy.data.repository.ContentRepository.getMemCache()
                ?: (repo.getContent() as? DataState.Success)?.data
                ?: AppContent()
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
                        // NOTE: আগে এখানে NavPath("Mock Test") (শুধু subject, subTopic=null)
                        // ব্যবহার হতো — NavPath.depth() তখন 1 হতো, ফলে CoreScreen ভুলবশত
                        // SubTopicListScreen দেখাত (প্রশ্ন লিস্ট না দেখিয়ে "0 টি অধ্যায়" খালি স্ক্রিন)।
                        // subject+subTopic দুটোই সেট করে depth()==2 নিশ্চিত করা হলো যাতে
                        // QuestionListScreen সঠিকভাবে রেন্ডার হয়।
                        navPath = NavPath("Mock Test", "প্রশ্ন"))
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

    /**
     * Model Test-এর Written প্রশ্নের জন্য — টাইপ করে উত্তর মেলানোর বদলে "উত্তর দেখুন"
     * বাটনে আসল উত্তর দেখানো হয়, তারপর ইউজার নিজেই "ঠিক পেরেছি"/"ভুল হয়েছে" বেছে নেয়।
     * এখানে সরাসরি AnswerState.WrittenSubmitted ব্যবহার করা হচ্ছে (userText খালি, matchPct
     * 100/0) — তাতে submitQuiz() এ MCQ-র মতোই correct/wrong গণনা হয়ে যায়, ফলে একই মডেল
     * টেস্টে MCQ ও Written প্রশ্ন মিশিয়ে দিলেও রেজাল্ট ঠিকভাবে হিসাব হয়।
     */
    fun answerWrittenSelfGrade(questionIndex: Int, isCorrect: Boolean) {
        val questions = _state.value.questions.toMutableList()
        val q = questions.getOrNull(questionIndex) ?: return
        if (q.answerState !is AnswerState.Unanswered) return
        questions[questionIndex] = q.copy(
            answerState = AnswerState.WrittenSubmitted(userText = "", matchPct = if (isCorrect) 100 else 0, isCorrect = isCorrect)
        )
        _state.update { it.copy(questions = questions, answeredCount = it.answeredCount + 1) }
        _feedbackEvent.value = isCorrect
        markProgress(q.id, _state.value.mode)
        viewModelScope.launch {
            if (isCorrect) {
                cache.incrementCorrect()
                removeWrongQIdByMode(q.id, _state.value.mode)
            } else {
                cache.incrementWrong()
                saveWeakTopic(q.subject, q.subTopic)
                saveWrongQId(q.id, _state.value.mode)
            }
        }
    }

    fun answerWritten(questionIndex: Int, userText: String): Int {
        val questions = _state.value.questions.toMutableList()
        val q = questions.getOrNull(questionIndex) ?: return 0

        // ── Model Test-এর written প্রশ্নে auto-match হয় না — শুধু রেকর্ড করে রাখা হয় ──
        val activeModelTest = _state.value.activeModelTest
        if (activeModelTest != null) {
            questions[questionIndex] = q.copy(answerState = AnswerState.WrittenRecorded(userText))
            _state.update { it.copy(questions = questions, answeredCount = it.answeredCount + 1) }
            markProgress(q.id, _state.value.mode)
            viewModelScope.launch {
                repo.saveModelTestWrittenAnswer(
                    subject      = activeModelTest.subject,
                    testNumber   = activeModelTest.testNumber,
                    questionKey  = q.sourceKey(),
                    questionText = q.question,
                    userText     = userText
                )
            }
            return 100  // UI-কে "সংরক্ষিত হয়েছে" দেখানোর সিগন্যাল — সঠিক/ভুল বিচার নয়
        }

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

    fun updateWrittenDraft(key: String, text: String) {
        _state.update { it.copy(writtenDrafts = it.writtenDrafts + (key to text)) }
    }

    /**
     * ── Study রিকল-টাইপিং (⌨️) মোডে Written উত্তর AI দিয়ে অটো-চেক ──
     * Settings-এ সেভ করা key দিয়ে Groq → Mistral → Cerebras → Gemini ক্রমে চেষ্টা হয়।
     * কোনো key সেভ করা না থাকলে বা সবগুলো ব্যর্থ হলে null রিটার্ন করে — তখন
     * SharedComponents-এর QuestionCard সাথে সাথেই ম্যানুয়াল ঠিক/ভুল বাটনে ফলব্যাক করে।
     */
    suspend fun gradeWrittenWithAi(question: String, correctAnswer: String, userAnswer: String): Boolean? {
        val keys = session.getAiApiKeys()
        if (!keys.hasAnyKey()) return null
        return com.hanif.smartstudy.data.remote.WrittenAnswerAiService.gradeWrittenAnswer(
            question      = question,
            correctAnswer = correctAnswer,
            userAnswer    = userAnswer,
            keys          = keys
        )
    }

    /**
     * ── Study রিকল-টাইপিং মোডের নিচের ফ্লোটিং "সাবমিট" বাটনে ফলাফলের
     * "বিস্তারিত" চাপলে — ভুলটা ঠিক কোথায় হয়েছে তার সংক্ষিপ্ত ব্যাখ্যা আনে। ──
     */
    suspend fun explainWrittenMistake(question: String, correctAnswer: String, userAnswer: String): String? {
        val keys = session.getAiApiKeys()
        if (!keys.hasAnyKey()) return null
        return com.hanif.smartstudy.data.remote.WrittenAnswerAiService.explainMistake(
            question      = question,
            correctAnswer = correctAnswer,
            userAnswer    = userAnswer,
            keys          = keys
        )
    }

    /**
     * ── "প্রশ্ন এডিট করুন" ডায়ালগে "🔄 Regenerate" বাটনে ব্যবহারের জন্য — প্রশ্ন
     * এডিট/পুনর্লিখন করার পর তার সাথে মিলিয়ে ৪টা অপশন ও সঠিক উত্তর AI দিয়ে আবার
     * তৈরি করে দেয় (SharedComponents.kt-এর AdminFieldEditDialog থেকে কল হয়)।
     * বাল্ক-আপলোড করা প্রশ্নে ভুল ধরা পড়লে প্রশ্ন ঠিক করার সাথে সাথেই অপশন/উত্তরও
     * মিলিয়ে নেওয়া যায় — আলাদাভাবে ম্যানুয়ালি ৪টা অপশন টাইপ করতে হয় না।
     */
    suspend fun regenerateMcqOptions(question: String): com.hanif.smartstudy.data.remote.RegeneratedMcq? {
        val keys = session.getAiApiKeys()
        if (!keys.hasAnyKey()) return null
        return com.hanif.smartstudy.data.remote.WrittenAnswerAiService.regenerateMcqOptions(
            question = question,
            keys     = keys
        )
    }


    fun submitQuiz() {
        timerJob?.cancel()

        // ── স্ট্রিক্ট টাইমার: সময় শেষ হয়ে অটো-সাবমিট হয়ে গেলেও যেসব written প্রশ্নে
        // ইউজার টাইপ করছিল কিন্তু এখনো "উত্তর জমা দিন" চাপেনি, তাদের ড্রাফট টেক্সট
        // এখানে finalize করে ফেলা হয় — কোনো লেখা হারিয়ে যায় না ──
        val drafts = _state.value.writtenDrafts
        if (drafts.isNotEmpty()) {
            _state.value.questions.forEachIndexed { idx, q ->
                if (q.answerState is AnswerState.Unanswered && q.isWritten()) {
                    drafts[q.sourceKey()]?.takeIf { it.isNotBlank() }?.let { draftText ->
                        answerWritten(idx, draftText)
                    }
                }
            }
        }

        val questions  = _state.value.questions   // draft-finalize এর পর আপডেটেড লিস্ট রি-রিড
        val totalTime  = _state.value.totalTimeSec
        val elapsed    = totalTime - _state.value.timerSec
        var correct = 0; var wrong = 0; var skipped = 0; var recorded = 0
        val subjectMap = mutableMapOf<String, SubjectScore>()

        questions.forEach { q ->
            when (val a = q.answerState) {
                is AnswerState.McqSelected      -> { if (a.isCorrect) correct++ else wrong++ }
                is AnswerState.WrittenSubmitted -> { if (a.isCorrect) correct++ else wrong++ }
                is AnswerState.WrittenRecorded  -> { recorded++ }   // Model Test written — গ্রেডিং হয়নি, শুধু জমা হয়েছে
                else -> skipped++
            }
            val subj = q.subject.ifBlank { "অন্যান্য" }
            val prev = subjectMap[subj] ?: SubjectScore(subj, 0, 0)
            val isC  = q.answerState.let { it is AnswerState.McqSelected && it.isCorrect || it is AnswerState.WrittenSubmitted && it.isCorrect }
            subjectMap[subj] = prev.copy(correct = prev.correct + (if (isC) 1 else 0), total = prev.total + 1)
        }

        val xp = correct * 5 + (correct - wrong).coerceAtLeast(0) * 2
        val result = QuizResult(questions.size, correct, wrong, skipped, elapsed, xp, subjectMap, recorded)
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

        // ── Model Test রেজাল্ট হিস্ট্রিতে জমা রাখো — কবে দিয়েছিল, কত স্কোর, progress ট্র্যাক করার জন্য ──
        val mt = _state.value.activeModelTest
        if (mt != null && result.total > 0) {
            viewModelScope.launch {
                historyCache.addEntry(
                    result.toHistoryEntry(
                        mode   = _state.value.mode.name,
                        topics = listOf("${mt.subject} — ${mt.displayTitle()}"),
                        source = "model_test"
                    )
                )
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
            // ── FIX: আগে প্রতিটা সাবমিটেই Streak popup দেখাতো (বিরক্তিকর) — এখন
            // দিনে একবারই দেখাবে ──
            if (session.shouldShowStreakPopupToday()) {
                _pendingStreak.value = streak
                session.markStreakPopupShownToday()
            }

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

    private fun isStudyDone(qId: String): Boolean {
        if (qId.isBlank()) return false
        return prefs.getStringSet("study_done_ids", emptySet())?.contains(qId) == true
    }

    /**
     * Study মোডের টিকমার্ক বাটন — ক্লিক করলে "পড়া হয়েছে" হিসেবে সেভ হয় এবং
     * একই লিস্টের নিচে চলে যায় (quiz এর mastered question sink করার মতো)।
     * হাইড হয় না, শুধু নিচে সরে যায় — আবার ক্লিক করলে টিক উঠে যাবে (toggle)।
     */
    /**
     * Study মোডের টিকমার্ক বাটন — ক্লিক করলে "পড়া হয়েছে" হিসেবে সেভ হয়।
     * এখনই লিস্টে নিচে সরানো হয় না (তাহলে স্ক্রিন স্ক্রল করে নিচে চলে যায় এবং
     * ইউজারকে আবার উপরে স্ক্রল করে আসতে হয়) — শুধু চেকমার্কটা টিক হয়ে যাবে,
     * এই সাবটপিক পরের বার আবার ওপেন করলে (loadQuestions/loadQuestionsFromRoom)
     * তখন done আইটেমগুলো স্বয়ংক্রিয়ভাবে লিস্টের নিচে চলে যাবে।
     */
    fun toggleStudyDone(qId: String) {
        if (qId.isBlank()) return
        val current = prefs.getStringSet("study_done_ids", mutableSetOf())!!.toMutableSet()
        if (current.contains(qId)) current.remove(qId) else current.add(qId)
        prefs.edit().putStringSet("study_done_ids", current).apply()

        val updated = _state.value.questions
            .map { q -> if (q.id == qId) q.copy(isStudyDone = current.contains(qId)) else q }
            // এখানে reorder করা হচ্ছে না ইচ্ছাকৃতভাবে — position অপরিবর্তিত থাকবে
        _state.update { it.copy(questions = updated) }
    }

    /**
     * ── toggleStudyDone-এর মতোই, কিন্তু টগল না করে সরাসরি true/false সেট করে।
     * Study রিকল-টাইপিং বাল্ক-সাবমিটে ব্যবহৃত — AI দিয়ে গ্রেড হওয়া প্রতিটা
     * প্রশ্নকে "পড়া হয়েছে" মার্ক করার জন্য, আগে থেকে done থাকলে যাতে ভুলবশত
     * আনডান না হয়ে যায়। ──
     */
    fun markStudyDone(qId: String, done: Boolean) {
        if (qId.isBlank()) return
        val current = prefs.getStringSet("study_done_ids", mutableSetOf())!!.toMutableSet()
        val changed = if (done) current.add(qId) else current.remove(qId)
        if (!changed) return
        prefs.edit().putStringSet("study_done_ids", current).apply()

        val updated = _state.value.questions
            .map { q -> if (q.id == qId) q.copy(isStudyDone = current.contains(qId)) else q }
        _state.update { it.copy(questions = updated) }
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
                                          name         = st,
                                          subject      = subj,
                                          totalQ       = stQs.size,
                                          doneQ        = stQs.count { progressMap.contains("${mode.name}:${it.id}") },
                                          isWeak       = isWeak(st),
                                          mcqCount     = stQs.count { it.isMcq() },
                                          writtenCount = stQs.count { it.isWritten() }
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
            SubTopicEntry(
                name         = st,
                subject      = subject,
                totalQ       = qs.size,
                doneQ        = qs.count { progressMap.contains("${mode.name}:${it.id}") },
                isWeak       = isWeak(st),
                mcqCount     = qs.count { it.isMcq() },
                writtenCount = qs.count { it.isWritten() }
            )
        }
            .sortedWith(compareBy({ order[it.name] ?: Int.MAX_VALUE }, { it.name }))

        // Model Test আগে এখানে subtopic list-এর ভেতর virtual card হিসেবে বসতো — এখন Mock Test-এর
        // মতোই subject list-এর নিচে একটা গ্লোবাল বাটন থেকে (openModelTestPicker) অ্যাক্সেস হয়,
        // তাই এখানে আর ইনজেক্ট করা হয় না।
        _state.update { it.copy(subTopics = subTopics) }
    }

    // ═════════════════════════════════════════════════════════
    // QBank-only ফিল্টার: পদবী(Designation, ডিফল্ট) / প্রতিষ্ঠান(Institution) / সাল(Year)
    //
    // পদবী-মোড: বিদ্যমান Subject→SubTopic হায়ারার্কি অপরিবর্তিত (rebuildSubjects/
    // rebuildSubTopics/navigateToSubject/navigateToSubTopic — কিছুই বদলায়নি)।
    //
    // প্রতিষ্ঠান-মোড: হায়ারার্কি উল্টে যায় — আগে প্রতিষ্ঠান(=আসল subTopic কলাম) বাছাই,
    // তারপর তার আন্ডারে পদবী(=আসল subject কলাম)। শেষ ধাপে navPath = (designation,
    // institution) — এটাই আসল Room/Firebase pair, তাই navigateToSubTopic()-এর
    // বিদ্যমান পুরো Room-first pagination/report/admin-edit পাইপলাইন অপরিবর্তিত রিইউজ হয়।
    //
    // সাল-মোড: subject/subTopic নির্বিশেষে flat প্রশ্ন-লিস্ট, তাই আলাদা Room query লাগে
    // (getRoomYearTotalCount/getRoomPagedQuestionsByYear — ContentRepository/QuestionDao)।
    // ═════════════════════════════════════════════════════════

    /** ফিল্টার চিপ ট্যাপ করলে কল হয় — শুধু QBank মোডে effective */
    fun setQBankFilterMode(newMode: QBankFilterMode) {
        if (_state.value.mode != StudyMode.QBANK) return
        if (_state.value.qbankFilterMode == newMode &&
            _state.value.qbankSelectedInstitution == null &&
            _state.value.qbankSelectedYear == null &&
            _state.value.navPath.depth() == 0) return

        timerJob?.cancel()
        _state.update {
            it.copy(
                qbankFilterMode = newMode,
                navPath = NavPath(),
                qbankSelectedInstitution = null,
                qbankDesignationsUnderInstitution = emptyList(),
                qbankSelectedYear = null,
                qbankSelectedPost = null,
                qbankInstitutionsUnderPost = emptyList(),
                qbankSearchQuery = "",
                isQuizActive = false,
                questions = emptyList(),
                showResult = false,
                result = null,
                isLoading = true
            )
        }
        viewModelScope.launch {
            when (newMode) {
                QBankFilterMode.DESIGNATION -> rebuildQBankPosts()
                QBankFilterMode.INSTITUTION -> rebuildQBankInstitutions()
                QBankFilterMode.YEAR        -> rebuildQBankYears((repo.getContent() as? DataState.Success)?.data ?: AppContent())
                QBankFilterMode.POST        -> rebuildQBankPosts()
            }
        }
    }

    /** QBank-only সার্চ বক্সে টাইপ করলে কল হয় — শুধু নাম-লিস্ট ক্লায়েন্ট-সাইড ফিল্টার করে */
    fun setQBankSearchQuery(query: String) {
        _state.update { it.copy(qbankSearchQuery = query) }
    }

    /**
     * প্রতিষ্ঠান-মোড — Room-এর exam_appearances (Institution+Post ধরে group করা)
     * থেকে তালিকা, ঠিক rebuildQBankPosts()-এর প্যাটার্নেই শুধু Institution↔Post উল্টে।
     * ── FIX: আগে এই ফাংশন QuestionEntity-র পুরনো `subTopic` টেক্সট-কলাম (Institution
     * নাম হিসেবে ব্যবহৃত হতো) ধরে group করতো (নিচে দেখো cite নিচে) — কিন্তু QBank
     * শীটে এখন আর সেই plain subject/sub_topic কলামই নেই (subject_id/topic_id দিয়ে
     * রিপ্লেস হয়েছে), তাই এই তালিকা সবসময় ফাঁকা আসতো ("ডেটা আসেনি")। এখন এটা
     * সঠিক Institutions/Exam_Appearances রেফারেন্স-টেবিল থেকে তৈরি হয়, ঠিক "পদ"-মোডের
     * (এখন পদবী-তে merge হওয়া) মতোই নির্ভরযোগ্য। ──
     */
    private suspend fun rebuildQBankInstitutions() {
        repo.syncExamAppearances()
        val institutions = repo.getRoomInstitutions()
        val posts        = repo.getRoomPosts().associateBy { it.postId }
        val progressMap  = loadProgressMap()
        val mode = StudyMode.QBANK

        val entries = institutions.map { inst ->
            val appearances = repo.getRoomAppearancesForInstitution(inst.institutionId)
            val byPost = appearances.groupBy { it.postId }
            val subTopics = byPost.mapNotNull { (postId, apps) ->
                val postName = posts[postId]?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val qIds = apps.map { it.questionId }.distinct()
                SubTopicEntry(
                    name              = postName,
                    subject           = inst.name,
                    totalQ            = qIds.size,
                    doneQ             = qIds.count { progressMap.contains("${mode.name}:$it") },
                    subjectId         = inst.institutionId,
                    topicId           = postId,
                    linkedQuestionIds = qIds
                )
            }.sortedBy { it.name }
            SubjectEntry(
                name      = inst.name,
                totalQ    = subTopics.sumOf { it.totalQ },
                doneQ     = subTopics.sumOf { it.doneQ },
                subTopics = subTopics,
                subjectId = inst.institutionId
            )
        }.filter { it.subTopics.isNotEmpty() }.sortedBy { it.name }

        Log.d("QuizVM", "rebuildQBankInstitutions: ${entries.size}")
        _state.update { it.copy(qbankInstitutions = entries, isLoading = false) }
    }

    /** প্রতিষ্ঠান-মোডের depth0 → একটা প্রতিষ্ঠান বাছাই — নেস্টেড ডেটা আগে থেকেই
     *  qbankInstitutions-এ আছে, তাই নতুন করে fetch লাগে না। */
    fun selectQBankInstitution(institution: String) {
        val designations = _state.value.qbankInstitutions
            .find { it.name == institution }?.subTopics ?: emptyList()
        _state.update {
            it.copy(
                qbankSelectedInstitution = institution,
                qbankDesignationsUnderInstitution = designations,
                qbankSearchQuery = ""
            )
        }
    }

    /**
     * প্রতিষ্ঠান-মোডের depth1 → একটা পদবী বাছাই — appearance-linked questionId
     * দিয়ে সরাসরি ফ্ল্যাট প্রশ্ন-লিস্ট (ঠিক selectQBankInstitutionUnderPost()-এর
     * প্যাটার্নেই)। আগে এখানে raw subject/subTopic টেক্সট-ম্যাচ (navigateToSubTopic)
     * রিইউজ হতো, যেটা এখন আর সেই কলামগুলো না থাকায় কখনো মেলে না — তাই "প্রশ্ন ০"।
     */
    fun selectQBankDesignationUnderInstitution(designation: String) {
        val entry = _state.value.qbankDesignationsUnderInstitution.find { it.name == designation } ?: return
        val institution = _state.value.qbankSelectedInstitution ?: return
        timerJob?.cancel()
        _state.update {
            it.copy(
                navPath = NavPath(institution, designation),
                currentPage = 0,
                qbankSearchQuery = ""
            )
        }
        viewModelScope.launch {
            val user     = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val tag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
                .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
            val bookmarks = _state.value.bookmarkedIds

            // ── FIX ("০/০ প্রশ্ন" বাগ): Room-এ না-থাকা linkedQuestionId গুলো
            // আগে GAS থেকে টার্গেটেড এনে ক্যাশ করে নাও, তারপর Room থেকে পড়ো ──
            repo.ensureRoomQuestionsByIds(StudyMode.QBANK.name, entry.linkedQuestionIds)

            val items = repo.getRoomQuestionsByIds(StudyMode.QBANK.name, entry.linkedQuestionIds, tag)
                .map { q ->
                    q.copy(
                        isBookmarked = bookmarks.contains(q.id),
                        isWeakTopic  = isWeak(q.subTopic),
                        isStudyDone  = isStudyDone(q.id)
                    )
                }.sortedBy { isMastered(it.id, StudyMode.QBANK) || it.isStudyDone }

            _state.update {
                it.copy(
                    questions      = items,
                    totalQuestions = items.size,
                    currentPage    = 0,
                    isQuizActive   = true,
                    showResult     = false,
                    result         = null,
                    answeredCount  = 0,
                    timerSec       = 0,
                    isLoading      = false
                )
            }
            startTimer(items.size)
        }
    }

    /** সাল-মোড: year কলাম ধরে group করা তালিকা — subject/subTopic নির্বিশেষে */
    private suspend fun rebuildQBankYears(content: AppContent) {
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered = content.forUser(user, adminTag)
        val items    = filtered.qbank.map { QuestionItem.fromQBankItem(it) }
        val progressMap = loadProgressMap()
        val mode = StudyMode.QBANK

        val years = items
            .filter { it.year.isNotBlank() }
            .groupBy { it.year }
            .map { (yr, qs) ->
                SubjectEntry(
                    name   = yr,
                    totalQ = qs.size,
                    doneQ  = qs.count { progressMap.contains("${mode.name}:${it.id}") }
                )
            }
            .sortedByDescending { it.name }   // সাম্প্রতিক সাল আগে
        Log.d("QuizVM", "rebuildQBankYears: ${years.size}")
        _state.update { it.copy(qbankYears = years, isLoading = false) }
    }

    /** সাল-মোডের depth0 → একটা সাল বাছাই — flat প্রশ্ন-লিস্ট, Room-first pagination
     *  (ঠিক navigateToSubTopic()-এর মতোই প্যাটার্ন, শুধু subject/subTopic pair এর
     *  বদলে শুধু year দিয়ে cross-subject ফিল্টার হয়) */
    fun selectQBankYear(year: String) {
        timerJob?.cancel()
        _state.update {
            it.copy(
                qbankSelectedYear = year,
                // navPath কে শুধু ডিসপ্লে/ডেপথ-ট্র্যাকিং এর জন্য একটা placeholder pair
                // দেওয়া হলো ("সাল", year) — এটা আসল কোনো subject/subTopic না, তাই
                // এখান থেকে Room query হয় না (নিচে আলাদাভাবে year দিয়েই হয়)
                navPath = NavPath("সাল", year),
                currentPage = 0,
                qbankSearchQuery = ""
            )
        }
        viewModelScope.launch {
            val sheet = StudyMode.QBANK.name
            val user  = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val tag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
                .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }

            val roomCount = repo.getRoomYearTotalCount(sheet, year, tag)
            if (roomCount > 0) {
                loadQBankYearQuestionsFromRoom(year, tag, page = 0)
                if (BuildConfig.REALTIME_DATA) {
                    launch(Dispatchers.IO) {
                        val content = (repo.getContent() as? DataState.Success)?.data
                        if (content != null) loadQBankYearQuestionsFallback(content, year)
                    }
                }
            } else {
                val content = (repo.getContent() as? DataState.Success)?.data ?: AppContent()
                loadQBankYearQuestionsFallback(content, year)
            }
        }
    }

    /** Room DB থেকে সাল-ভিত্তিক পেজিনেটেড প্রশ্ন — goToPage() থেকেও কল হয় */
    private suspend fun loadQBankYearQuestionsFromRoom(year: String, tag: String, page: Int) {
        _state.update { it.copy(questionsLoading = true) }
        val sheet = StudyMode.QBANK.name
        val total = repo.getRoomYearTotalCount(sheet, year, tag)
        val items = repo.getRoomPagedQuestionsByYear(sheet, year, tag, page, PAGE_SIZE)
        val bookmarks = _state.value.bookmarkedIds

        val questions = items.map { q ->
            q.copy(
                isBookmarked = bookmarks.contains(q.id),
                isWeakTopic  = isWeak(q.subTopic),
                isStudyDone  = isStudyDone(q.id)
            )
        }.sortedBy { isMastered(it.id, StudyMode.QBANK) || it.isStudyDone }

        _state.update {
            it.copy(
                questions        = questions,
                totalQuestions   = total,
                currentPage      = page,
                questionsLoading = false,
                isQuizActive     = true,
                showResult       = false,
                result           = null,
                answeredCount    = 0,
                timerSec         = 0
            )
        }
        startTimer(total)
    }

    /** Room-এ QBank সিঙ্ক না হয়ে থাকলে Firebase/AppContent থেকে সরাসরি ফলব্যাক (পেজিনেশন ছাড়া) */
    private suspend fun loadQBankYearQuestionsFallback(content: AppContent, year: String) {
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered = content.forUser(user, adminTag)
        val bookmarks = _state.value.bookmarkedIds
        val items = filtered.qbank.filter { it.year == year }
            .map { QuestionItem.fromQBankItem(it) }
            .map {
                it.copy(
                    isBookmarked = bookmarks.contains(it.id),
                    isWeakTopic  = isWeak(it.subTopic),
                    isStudyDone  = isStudyDone(it.id)
                )
            }
            .sortedBy { isMastered(it.id, StudyMode.QBANK) || it.isStudyDone }
        _state.update {
            it.copy(
                questions      = items,
                totalQuestions = items.size,
                currentPage    = 0,
                isQuizActive   = true,
                showResult     = false,
                result         = null,
                answeredCount  = 0,
                timerSec       = 0
            )
        }
        startTimer(items.size)
    }

    // ═════════════════════════════════════════════════════════
    // Phase 6 (db-migration-v2): "পদ অনুযায়ী ব্রাউজ" — নতুন Posts/Institutions/
    // Exam_Appearances reference-টেবিল থেকে (Room, দেখো data/local/ReferenceDao.kt)।
    // DESIGNATION/INSTITUTION মোডের মতো raw sheet subject/sub_topic টেক্সট থেকে না —
    // এখানে একই প্রশ্ন একাধিক পরীক্ষায় (ভিন্ন Institution/Year) আলাদা appearance-row
    // হিসেবে থাকতে পারে, তাই একই প্রশ্ন একাধিক Post/Institution-এ দেখা যেতে পারে।
    //
    // ⚠️ GAS-সাইডে (`code_updated.gs`) এখনো `getAllExamAppearances` action নেই (দেখো
    // GasContentService.fetchAllExamAppearances-এর কমেন্ট) — সেটা যোগ না হওয়া পর্যন্ত
    // Room-এর exam_appearances টেবিল খালিই থাকবে আর এই মোডে "কোনো পদ নেই" দেখাবে,
    // কিন্তু অ্যাপ ভাঙবে না।
    // ═════════════════════════════════════════════════════════

    /** পদ-মোড: Room-এর exam_appearances (Post+Institution ধরে group করা) থেকে তালিকা —
     *  প্রতিটা পদের ভিতরে (subTopics ফিল্ডে) সেই পদের আন্ডারে যত প্রতিষ্ঠান আছে তার নেস্টেড
     *  লিস্ট, ঠিক rebuildQBankInstitutions()-এর প্যাটার্নেই। */
    private suspend fun rebuildQBankPosts() {
        repo.syncExamAppearances()   // idempotent — ব্যর্থ হলে Room-এর পুরনো/খালি ডেটাই থাকবে, exception ছোঁড়ে না
        val posts        = repo.getRoomPosts()
        val institutions = repo.getRoomInstitutions().associateBy { it.institutionId }
        val progressMap  = loadProgressMap()
        val mode = StudyMode.QBANK

        val entries = posts.map { post ->
            val appearances   = repo.getRoomAppearancesForPost(post.postId)
            val byInstitution = appearances.groupBy { it.institutionId }
            val subTopics = byInstitution.mapNotNull { (instId, apps) ->
                val instName = institutions[instId]?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val qIds = apps.map { it.questionId }.distinct()
                SubTopicEntry(
                    name              = instName,
                    subject           = post.name,
                    totalQ            = qIds.size,
                    doneQ             = qIds.count { progressMap.contains("${mode.name}:$it") },
                    subjectId         = post.postId,
                    topicId           = instId,
                    linkedQuestionIds = qIds
                )
            }.sortedBy { it.name }
            SubjectEntry(
                name      = post.name,
                totalQ    = subTopics.sumOf { it.totalQ },
                doneQ     = subTopics.sumOf { it.doneQ },
                subTopics = subTopics,
                subjectId = post.postId
            )
        }.filter { it.subTopics.isNotEmpty() }.sortedBy { it.name }

        Log.d("QuizVM", "rebuildQBankPosts: ${entries.size}")
        _state.update { it.copy(qbankPosts = entries, isLoading = false) }
    }

    /** পদ-মোডের depth0 → একটা পদ বাছাই — নেস্টেড ডেটা আগে থেকেই qbankPosts-এ আছে,
     *  তাই selectQBankInstitution()-এর মতোই নতুন করে fetch লাগে না। */
    fun selectQBankPost(postName: String) {
        val entry = _state.value.qbankPosts.find { it.name == postName }
        _state.update {
            it.copy(
                qbankSelectedPost = postName,
                qbankInstitutionsUnderPost = entry?.subTopics ?: emptyList(),
                qbankSearchQuery = ""
            )
        }
    }

    /** পদ-মোডের depth1 → একটা প্রতিষ্ঠান বাছাই — appearance-linked questionId গুলো দিয়ে
     *  সরাসরি Room থেকে ফ্ল্যাট প্রশ্ন-লিস্ট। navigateToSubTopic() রিইউজ করা যায়নি কারণ
     *  এই ডেটার জন্য কোনো raw subject/sub_topic টেক্সট ম্যাচ নেই, শুধু Exam_Appearances-এর
     *  questionId লিংক আছে — ঠিক selectQBankYear()-এর মতোই সরাসরি ফ্ল্যাট-লোড প্যাটার্ন। */
    fun selectQBankInstitutionUnderPost(institutionName: String) {
        val entry = _state.value.qbankInstitutionsUnderPost.find { it.name == institutionName } ?: return
        timerJob?.cancel()
        _state.update {
            it.copy(
                // শুধু ডেপথ/ডিসপ্লে প্লেসহোল্ডার (YEAR মোডের "সাল" প্যাটার্নেই) — আসল
                // subject/subTopic pair না, তাই এখান থেকে কোনো raw Room subject/subTopic
                // query হয় না (নিচে entry.linkedQuestionIds দিয়েই সরাসরি হয়)
                navPath = NavPath("পদ", institutionName),
                currentPage = 0,
                qbankSearchQuery = ""
            )
        }
        viewModelScope.launch {
            val user     = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val tag = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
                .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
            val bookmarks = _state.value.bookmarkedIds

            // ── FIX ("০/০ প্রশ্ন" বাগ): Room-এ না-থাকা linkedQuestionId গুলো আগে
            // GAS থেকে টার্গেটেড এনে ক্যাশ করে নাও, তারপর Room থেকে পড়ো ──
            repo.ensureRoomQuestionsByIds(StudyMode.QBANK.name, entry.linkedQuestionIds)

            val items = repo.getRoomQuestionsByIds(StudyMode.QBANK.name, entry.linkedQuestionIds, tag)
                .map { q ->
                    q.copy(
                        isBookmarked = bookmarks.contains(q.id),
                        isWeakTopic  = isWeak(q.subTopic),
                        isStudyDone  = isStudyDone(q.id)
                    )
                }.sortedBy { isMastered(it.id, StudyMode.QBANK) || it.isStudyDone }

            _state.update {
                it.copy(
                    questions      = items,
                    totalQuestions = items.size,
                    currentPage    = 0,
                    isQuizActive   = true,
                    showResult     = false,
                    result         = null,
                    answeredCount  = 0,
                    timerSec       = 0,
                    isLoading      = false
                )
            }
            startTimer(items.size)
        }
    }

    /**
     * প্রতিষ্ঠান/সাল ফিল্টার মোডে থাকা অবস্থায় CoreScreen এই ফাংশন কল করে (generic
     * navigateBack()-এর বদলে) — কারণ generic navigateBack() শুধু পদবী(Designation)-মোডের
     * সাধারণ Subject→SubTopic হায়ারার্কি বোঝে, এই দুই বিশেষ মোডের কাস্টম ধাপ বোঝে না।
     */
    fun qbankFilterBack() {
        when (_state.value.qbankFilterMode) {
            // ── FIX: "পদ" রিপ্লেস হয়ে "পদবী" হয়েছে, একই qbankPosts/qbankSelectedPost
            // state পুনর্ব্যবহার করে — তাই back-লজিকও POST-এর মতোই হবে ──
            QBankFilterMode.DESIGNATION -> when {
                _state.value.navPath.depth() == 2 -> {
                    // প্রশ্ন-লিস্ট থেকে এই পদবীর আন্ডারে প্রতিষ্ঠান-লিস্টে ফিরে যাও
                    timerJob?.cancel()
                    _state.update {
                        it.copy(
                            navPath = NavPath(), isQuizActive = false, questions = emptyList(),
                            timerSec = 0, showResult = false, result = null
                        )
                    }
                }
                _state.value.qbankSelectedPost != null -> {
                    // প্রতিষ্ঠান-লিস্ট থেকে পদবী-লিস্টে ফিরে যাও
                    _state.update {
                        it.copy(
                            qbankSelectedPost = null,
                            qbankInstitutionsUnderPost = emptyList(),
                            qbankSearchQuery = ""
                        )
                    }
                }
                else -> {}
            }
            QBankFilterMode.INSTITUTION -> when {
                _state.value.navPath.depth() == 2 -> {
                    // প্রশ্ন-লিস্ট থেকে এই প্রতিষ্ঠানের আন্ডারে পদবী-লিস্টে ফিরে যাও
                    timerJob?.cancel()
                    _state.update {
                        it.copy(
                            navPath = NavPath(), isQuizActive = false, questions = emptyList(),
                            timerSec = 0, showResult = false, result = null
                        )
                    }
                }
                _state.value.qbankSelectedInstitution != null -> {
                    // পদবী-লিস্ট থেকে প্রতিষ্ঠান-লিস্টে ফিরে যাও
                    _state.update {
                        it.copy(
                            qbankSelectedInstitution = null,
                            qbankDesignationsUnderInstitution = emptyList(),
                            qbankSearchQuery = ""
                        )
                    }
                }
                else -> {}
            }
            QBankFilterMode.YEAR -> if (_state.value.qbankSelectedYear != null) {
                // ফ্ল্যাট প্রশ্ন-লিস্ট থেকে সালের লিস্টে ফিরে যাও
                timerJob?.cancel()
                _state.update {
                    it.copy(
                        navPath = NavPath(), qbankSelectedYear = null, isQuizActive = false,
                        questions = emptyList(), timerSec = 0, showResult = false, result = null
                    )
                }
            }
            QBankFilterMode.POST -> when {
                _state.value.navPath.depth() == 2 -> {
                    // প্রশ্ন-লিস্ট থেকে এই পদের আন্ডারে প্রতিষ্ঠান-লিস্টে ফিরে যাও
                    timerJob?.cancel()
                    _state.update {
                        it.copy(
                            navPath = NavPath(), isQuizActive = false, questions = emptyList(),
                            timerSec = 0, showResult = false, result = null
                        )
                    }
                }
                _state.value.qbankSelectedPost != null -> {
                    // প্রতিষ্ঠান-লিস্ট থেকে পদ-লিস্টে ফিরে যাও
                    _state.update {
                        it.copy(
                            qbankSelectedPost = null,
                            qbankInstitutionsUnderPost = emptyList(),
                            qbankSearchQuery = ""
                        )
                    }
                }
                else -> {}
            }
            else -> {}
        }
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

    /**
     * বর্তমান subjects ক্রম অনুযায়ী ১,২,৩... সিরিয়াল বানিয়ে এই mode+tag এর জন্য Firebase এ সেভ করো।
     *
     * ⚠️ আগে এখানে শুধু Firebase কল success হলেই লোকাল cache patch হতো — মানে নেট/quota
     * সমস্যায় কল fail করলে reorder-টা কোথাও সংরক্ষিতই হতো না (admin অ্যাপ বন্ধ করে খুললেই
     * আগের ক্রম ফিরে আসত), আর সফল হলেও meta touch না হওয়ায় বাকি ইউজাররা নতুন ক্রম দেখতে
     * পেত না periodic full-resync-এর আগ পর্যন্ত।
     *
     * এখন: (adminEditQuestion-এর মতোই প্যাটার্নে)
     * ১) লোকাল cache-এ সবসময় সাথে সাথে patch হয় — Firebase সফল হোক বা না হোক, admin
     *    এই মুহূর্তেই আর অ্যাপ রিস্টার্টের পরও ঠিক ক্রম দেখবে।
     * ২) অফলাইন হলে সরাসরি Firebase কল না করে PendingQueue-তে রাখা হয়।
     * ৩) অনলাইনে কল fail করলে (quota/network) — PendingQueue-তে রাখা হয় + SyncWorker
     *    ব্যাকগ্রাউন্ডে auto-retry করবে নেট/quota ঠিক হলে।
     * ৪) সফল হলে FirebaseDataService.adminSetSubjectOrderBulk এখন নিজেই touchMetaUpdatedAt()
     *    কল করে, তাই বাকি সব ইউজারও পরের sync-এ নতুন ক্রম পেয়ে যাবে।
     */
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
            val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(effectiveTag)

            // ── Step 1: লোকাল cache-এ সাথে সাথেই patch — ফলাফল যাই হোক ──
            repo.patchSubjectOrderAndPersist(mode.name, encodedTag, order)

            val pendingQueue = com.hanif.smartstudy.data.local.PendingQueue(getApplication<Application>())

            if (!repo.isOnline()) {
                pendingQueue.enqueueAdminReorderSubject(mode.name, effectiveTag, order)
                _state.update { it.copy(isSavingOrder = false,
                    orderSavedMsg = "📴 অফলাইনে সংরক্ষিত — net আসলে auto sync হবে") }
                return@launch
            }

            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService.adminSetSubjectOrderBulk(mode.name, effectiveTag, order)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    _state.update { it.copy(isSavingOrder = false, orderSavedMsg = "✅ ক্রম সংরক্ষিত হয়েছে — সব ইউজার দেখতে পাবে") }
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    // Online কিন্তু fail (যেমন Firebase quota শেষ) — queue এ রাখো, লোকাল
                    // cache তো আগেই patch হয়ে গেছে, তাই admin ঠিক ক্রমই দেখবে।
                    pendingQueue.enqueueAdminReorderSubject(mode.name, effectiveTag, order)
                    com.hanif.smartstudy.worker.SyncWorker.scheduleOneTime(getApplication<Application>())
                    _state.update { it.copy(isSavingOrder = false,
                        orderSavedMsg = "⚠️ এখনই sync হয়নি (${r.message}) — queue-তে রাখা হয়েছে, নেট/quota ঠিক হলে auto sync হবে") }
                }
            }
        }
    }

    /**
     * বর্তমান subTopics ক্রম অনুযায়ী ১,২,৩... সিরিয়াল বানিয়ে এই mode+tag+subject এর জন্য Firebase এ সেভ করো।
     * persistSubjectOrder-এর মতোই প্যাটার্ন — লোকাল cache সবসময় সাথে সাথে patch হয়, আর
     * offline/fail হলে PendingQueue-তে রাখা হয় (SyncWorker ব্যাকগ্রাউন্ডে auto-retry করবে)।
     */
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
            val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(effectiveTag)

            // ── Step 1: লোকাল cache-এ সাথে সাথেই patch — ফলাফল যাই হোক ──
            repo.patchSubTopicOrderAndPersist(mode.name, encodedTag, subject, order)

            val pendingQueue = com.hanif.smartstudy.data.local.PendingQueue(getApplication<Application>())

            if (!repo.isOnline()) {
                pendingQueue.enqueueAdminReorderSubTopic(mode.name, effectiveTag, subject, order)
                _state.update { it.copy(isSavingOrder = false,
                    orderSavedMsg = "📴 অফলাইনে সংরক্ষিত — net আসলে auto sync হবে") }
                return@launch
            }

            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService.adminSetSubTopicOrderBulk(mode.name, effectiveTag, subject, order)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    _state.update { it.copy(isSavingOrder = false, orderSavedMsg = "✅ ক্রম সংরক্ষিত হয়েছে — সব ইউজার দেখতে পাবে") }
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    pendingQueue.enqueueAdminReorderSubTopic(mode.name, effectiveTag, subject, order)
                    com.hanif.smartstudy.worker.SyncWorker.scheduleOneTime(getApplication<Application>())
                    _state.update { it.copy(isSavingOrder = false,
                        orderSavedMsg = "⚠️ এখনই sync হয়নি (${r.message}) — queue-তে রাখা হয়েছে, নেট/quota ঠিক হলে auto sync হবে") }
                }
            }
        }
    }

    fun clearOrderSavedMsg() { _state.update { it.copy(orderSavedMsg = null) } }

    /** Pagination: নির্দিষ্ট page-এ যাও — Room থেকে instant load */
    fun goToPage(page: Int) {
        val totalPages = (_state.value.totalQuestions + PAGE_SIZE - 1) / PAGE_SIZE
        val safePage = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        if (safePage == _state.value.currentPage) return

        // ── QBank সাল-মোড: subject/subTopic pair নেই, শুধু year দিয়ে cross-subject পেজিনেশন ──
        val year = _state.value.qbankSelectedYear
        if (year != null) {
            val user     = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val tag      = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
                .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }
            viewModelScope.launch { loadQBankYearQuestionsFromRoom(year, tag, safePage) }
            return
        }

        // ── Phase 6: QBank পদ-মোড — navPath = NavPath("পদ", institutionName) একটা
        // placeholder (দেখো selectQBankInstitutionUnderPost), আসল subject/subTopic pair
        // না। এই লিস্ট Exam_Appearances-লিংকড questionId দিয়ে একবারেই সম্পূর্ণ লোড হয়
        // (Room subject/subTopic pagination না), তাই এখানে goToPage() করার কিছু নেই —
        // এটা ছাড়া নিচের কোড ভুল subject="পদ" দিয়ে Room query চালিয়ে ফেলত (খালি ফল দিত)।
        if (_state.value.qbankFilterMode == QBankFilterMode.POST && _state.value.navPath.subject == "পদ") {
            return
        }

        val navPath  = _state.value.navPath
        val subject  = navPath.subject ?: return
        val subTopic = navPath.subTopic ?: return
        val sheet    = _state.value.mode.name
        val user     = session.getCurrentUser()
        val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val tag      = com.hanif.smartstudy.util.AudienceFilter.audienceGroupOf(user)
            .let { if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else it }

        // ── FIX ("পরবর্তী বাটনে ফাঁকা স্ক্রিন" বাগ, root cause): এই টপিক Phase 6-এর
        // লেজি topicId সিস্টেম দিয়ে খোলা হয়েছিল কিনা (navigateToSubTopicLazy) চেক করো —
        // থাকলে topicId দিয়েই paginate করো (getRoomQuestionsForTopic-এর মতো একই
        // ডেটা-পাথ), নাহলে আগের subject/subTopic টেক্সট-ভিত্তিক পাথে fallback করো
        // (QBank প্রতিষ্ঠান-মোডের মতো টেক্সট-pair-নির্ভর পুরনো flow-এর জন্য, যদি কোথাও
        // এখনো ব্যবহৃত হয়)। আগে সবসময় টেক্সট-ভিত্তিক পাথ ব্যবহার হতো, যেটা topicId-based
        // ক্যাশের সাথে বেমানান হওয়ায় ২য় পাতা থেকে সবসময় ফাঁকা ফলাফল দিত। ──
        val topicId = _state.value.subTopics.find { it.name == subTopic }?.topicId

        viewModelScope.launch {
            if (!topicId.isNullOrBlank()) {
                loadQuestionsFromRoomByTopic(sheet, topicId, tag, safePage)
            } else {
                loadQuestionsFromRoom(sheet, subject, subTopic, tag, safePage)
            }
        }
    }

    /**
     * Room DB থেকে topicId দিয়ে paginated questions load করো — navigateToSubTopicLazy()-এর
     * প্রথম-পেজ-লোডের মতোই একই ডেটা-পাথ, শুধু এখানে LIMIT/OFFSET দিয়ে একটা page-ই আনা হয়।
     */
    private suspend fun loadQuestionsFromRoomByTopic(
        sheet   : String,
        topicId : String,
        tag     : String,
        page    : Int
    ) {
        _state.update { it.copy(questionsLoading = true) }

        val total     = repo.getRoomTotalCountByTopic(sheet, topicId, tag)
        val items     = repo.getRoomPagedQuestionsByTopic(sheet, topicId, tag, page, PAGE_SIZE)
        val bookmarks = _state.value.bookmarkedIds

        val questions = items.map { q ->
            q.copy(
                isBookmarked = bookmarks.contains(q.id),
                isWeakTopic  = isWeak(q.subTopic),
                isStudyDone  = isStudyDone(q.id)
            )
        }.sortedBy { isMastered(it.id, _state.value.mode) || it.isStudyDone }

        Log.d("QuizVM", "loadQuestionsFromRoomByTopic: page=$page total=$total loaded=${questions.size}")

        val mode = _state.value.mode
        _state.update {
            it.copy(
                questions       = questions,
                totalQuestions  = total,
                currentPage     = page,
                questionsLoading = false,
                isQuizActive    = mode != StudyMode.STUDY,
                showResult      = false,
                result          = null,
                answeredCount   = 0,
                timerSec        = 0
            )
        }
        if (mode != StudyMode.STUDY) startTimer(total)  // timer total প্রশ্ন দিয়ে
    }

    /**
     * Room DB থেকে paginated questions load করো — instant, Firebase call নেই।
     * goToPage() থেকেও এটা call হয়।
     */
    private suspend fun loadQuestionsFromRoom(
        sheet    : String,
        subject  : String,
        subTopic : String,
        tag      : String,
        page     : Int
    ) {
        _state.update { it.copy(questionsLoading = true) }

        val total    = repo.getRoomTotalCount(sheet, subject, subTopic, tag)
        val items    = repo.getRoomPagedQuestions(sheet, subject, subTopic, tag, page, PAGE_SIZE)
        val bookmarks = _state.value.bookmarkedIds

        val questions = items.map { q ->
            q.copy(
                isBookmarked = bookmarks.contains(q.id),
                isWeakTopic  = isWeak(q.subTopic),
                isStudyDone  = isStudyDone(q.id)
            )
        }.sortedBy { isMastered(it.id, _state.value.mode) || it.isStudyDone }

        Log.d("QuizVM", "loadQuestionsFromRoom: page=$page total=$total loaded=${questions.size}")

        val mode = _state.value.mode
        _state.update {
            it.copy(
                questions       = questions,
                totalQuestions  = total,
                currentPage     = page,
                questionsLoading = false,
                isQuizActive    = mode != StudyMode.STUDY,
                showResult      = false,
                result          = null,
                answeredCount   = 0,
                timerSec        = 0
            )
        }
        if (mode != StudyMode.STUDY) startTimer(total)  // timer total প্রশ্ন দিয়ে
    }

    private suspend fun loadQuestions(content: AppContent, subject: String, subTopic: String, mode: StudyMode) {
        val bookmarks = _state.value.bookmarkedIds
        val user      = session.getCurrentUser()
        val adminTag  = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
        val filtered  = content.forUser(user, adminTag)
        val items = when (mode) {
            StudyMode.QUIZ  -> filtered.quiz.filter  { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQuizItem(it)  }
            StudyMode.QBANK -> filtered.qbank.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromQBankItem(it) }
            StudyMode.STUDY -> filtered.study.filter { it.subject == subject && it.subTopic == subTopic }.map { QuestionItem.fromStudyItem(it) }
        }.map {
            it.copy(
                isBookmarked = bookmarks.contains(it.id),
                isWeakTopic  = isWeak(it.subTopic),
                isStudyDone  = isStudyDone(it.id)
            )
        }
            // আগে সঠিক হয়েছে এমন (mastered) প্রশ্নগুলো এই সাবটপিকে সবার নিচে শুরু হবে —
            // নতুন/ভুল করা প্রশ্নগুলো উপরে থাকবে, যাতে আগে সেগুলোর দিকেই নজর যায়
            // Study mode এ টিকমার্ক দেওয়া (পড়া হয়ে গেছে) আইটেমও একইভাবে নিচে যাবে
            .sortedBy { isMastered(it.id, mode) || it.isStudyDone }

        _state.update {
            it.copy(questions = items, isQuizActive = mode != StudyMode.STUDY,
                    showResult = false, result = null, answeredCount = 0, timerSec = 0,
                    currentPage = 0)
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
