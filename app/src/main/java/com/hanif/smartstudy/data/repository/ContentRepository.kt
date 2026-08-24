package com.hanif.smartstudy.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.local.PendingQueue
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.TopicSyncEntity
import com.hanif.smartstudy.data.local.toEntity
import com.hanif.smartstudy.data.local.toQuestionItem
import com.hanif.smartstudy.data.local.toQuizItem
import com.hanif.smartstudy.data.local.toQBankItem
import com.hanif.smartstudy.data.local.toStudyItem
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.ExamCountdown
import com.hanif.smartstudy.data.model.GoalProgress
import com.hanif.smartstudy.data.model.StreakDay
import com.hanif.smartstudy.data.model.StreakInfo
import com.hanif.smartstudy.data.model.StudyStats
import com.hanif.smartstudy.data.model.XpInfo
import com.hanif.smartstudy.data.remote.ContentFetchService
import com.hanif.smartstudy.data.remote.ContentResult
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.worker.SyncWorker
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ContentRepository(private val context: Context) {

    private val cache   = ContentCache(context)
    private val queue   = PendingQueue(context)
    private val session = SessionManager(context)
    private val db      = AppDatabase.getInstance(context)
    private val dao     = db.questionDao()
    private val refDao  = db.referenceDao()   // Phase 6 — Subjects/Topics/SubTopics/Tags/Posts/Institutions
    private val topicSyncDao = db.topicSyncDao()  // প্রতিটা Topic-এ কতদূর আনা হয়েছে (progressive fill)

    // ── In-memory cache — একবার fetch হলে সব VM শেয়ার করে ──
    companion object {
        @Volatile private var _memCache: AppContent? = null
        fun getMemCache(): AppContent? = _memCache
        fun clearMemCache() { _memCache = null }

        // ⚠️ BUG FIX ("subject list ashte onek slow"): syncReferenceData() আগে
        // rebuildSubjectsLazy() (মানে setMode()/প্রতিবার Quiz-QBank-Study খোলার সময়)
        // থেকে unconditionally কল হতো — মানে প্রতিটা visit-এই GAS-এর getReferenceData
        // action-এ একটা লাইভ নেটওয়ার্ক রাউন্ড-ট্রিপ হতো, Room-এ আগে থেকেই ডেটা থাকলেও।
        // Apps Script Web App-এর নিজস্ব ল্যাটেন্সি (cold start ইত্যাদি) থাকায় এটাই
        // "সাবজেক্ট লিস্ট প্রতিবার স্লো" সমস্যার মূল কারণ ছিল — content sync-এর জন্য
        // আগে থেকেই থাকা BG_REFRESH_MIN_GAP_MS প্যাটার্নটাই এখানে প্রয়োগ করা হলো।
        @Volatile private var _lastRefSyncAt: Long = 0L
        private const val REF_SYNC_MIN_GAP_MS = 10 * 60_000L // ১০ মিনিটে একবারের বেশি না

        // ── FIX (Speed Plan Task 3): CDN manifest.json-এর জন্য ৫-মিনিট TTL
        // in-memory cache — একই ধরনের প্যাটার্ন যেভাবে উপরে reference sync-এর
        // জন্য REF_SYNC_MIN_GAP_MS ব্যবহার হয়েছে, শুধু ছোট TTL (CDN সস্তা/দ্রুত,
        // GAS-এর মতো cold-start নেই) ──
        @Volatile private var _manifestCache: com.hanif.smartstudy.data.remote.CdnService.Manifest? = null
        @Volatile private var _manifestCachedAt: Long = 0L
        private const val MANIFEST_TTL_MS = 5 * 60_000L
    }

    /**
     * FAST PATH — Subject list দেখানোর জন্য।
     * শুধু SubjectOrder + SubTopicOrder fetch করে — questions আসে না।
     * Questions background-এ আলাদাভাবে আসবে।
     */
    suspend fun getSubjectsQuick(): DataState<AppContent> {
        // Cache-এ subjects থাকলে সেটাই দাও (questions থাকুক বা না থাকুক)
        _memCache?.let { mem ->
            if (mem.subjectOrder.isNotEmpty() || mem.subTopicOrder.isNotEmpty()) {
                Log.d("Repo", "getSubjectsQuick: cache hit")
                return DataState.Success(mem, fromCache = true)
            }
        }
        if (!isOnline()) {
            val cached = cache.loadContent() ?: _memCache
            return if (cached != null) DataState.Success(cached, fromCache = true, isOffline = true)
            else DataState.Error("ইন্টারনেট সংযোগ নেই")
        }
        return when (val result = ContentFetchService.fetchSubjectsOnly(context)) {
            is ContentResult.Success -> {
                // শুধু subjectOrder/subTopicOrder মেমরিতে রাখি
                // questions না আসা পর্যন্ত memCache এ questions empty থাকবে
                val partial = result.data
                _memCache = partial
                Log.d("Repo", "getSubjectsQuick: Firebase OK")
                DataState.Success(partial)
            }
            is ContentResult.Error -> DataState.Error(result.message)
        }
    }

    // ── Room-based fast methods ──────────────────────────────────────────────

    /** Room DB তে কোনো প্রশ্ন আছে কিনা */
    suspend fun hasRoomData(): Boolean =
        dao.countAll("QUIZ") > 0 || dao.countAll("STUDY") > 0 || dao.countAll("QBANK") > 0

    /** Room থেকে subject count list — instant */
    suspend fun getRoomSubjectCounts(sheet: String) = dao.getSubjectCounts(sheet)

    /** Room থেকে subTopic count list — instant */
    suspend fun getRoomSubTopicCounts(sheet: String, subject: String) =
        dao.getSubTopicCounts(sheet, subject)

    // ── QBank "প্রতিষ্ঠান-ভিত্তিক" ও "সাল-ভিত্তিক" ফিল্টার — subject/subTopic
    // হায়ারার্কি উল্টে/পাশ কাটিয়ে দেখার জন্য নতুন Room helper গুলো ──

    /** Room থেকে প্রতিষ্ঠান (Institution) তালিকা — subject-নিরপেক্ষ, সব ডিজিগনেশন মিলিয়ে */
    suspend fun getRoomInstitutionCounts(sheet: String) = dao.getInstitutionCounts(sheet)

    /** Room থেকে একটা প্রতিষ্ঠানের আন্ডারে যত পদবী (Designation) আছে */
    suspend fun getRoomDesignationsUnderInstitution(sheet: String, institution: String) =
        dao.getDesignationsUnderInstitution(sheet, institution)

    /** Room থেকে সালের তালিকা — subject/subTopic নির্বিশেষে */
    suspend fun getRoomYearCounts(sheet: String) = dao.getYearCounts(sheet)

    /** Room থেকে একটা নির্দিষ্ট সালের সব প্রশ্ন — পেজিনেটেড, subject/subTopic নির্বিশেষে */
    suspend fun getRoomPagedQuestionsByYear(
        sheet: String, year: String, tag: String, page: Int, pageSize: Int
    ): List<com.hanif.smartstudy.data.model.QuestionItem> {
        val offset = page * pageSize
        return if (tag.isBlank() || tag == "all") {
            dao.getPagedByYear(sheet, year, pageSize, offset)
        } else {
            dao.getPagedByYearFiltered(sheet, year, tag, pageSize, offset)
        }.map { it.toQuestionItem() }
    }

    /** Room থেকে একটা সালের মোট প্রশ্ন সংখ্যা */
    suspend fun getRoomYearTotalCount(sheet: String, year: String, tag: String): Int =
        if (tag.isBlank() || tag == "all") {
            dao.countByYear(sheet, year)
        } else {
            dao.countByYearFiltered(sheet, year, tag)
        }

    /**
     * Room থেকে paginated questions — instant, Firebase লাগে না।
     * audienceTag="" হলে সব দেখাবে, নইলে filter হবে।
     */
    suspend fun getRoomPagedQuestions(
        sheet    : String,
        subject  : String,
        subTopic : String,
        tag      : String,
        page     : Int,
        pageSize : Int
    ): List<com.hanif.smartstudy.data.model.QuestionItem> {
        val offset = page * pageSize
        return if (tag.isBlank() || tag == "all") {
            dao.getPagedQuestions(sheet, subject, subTopic, pageSize, offset)
        } else {
            dao.getPagedQuestionsFiltered(sheet, subject, subTopic, tag, pageSize, offset)
        }.map { it.toQuestionItem() }
    }

    /** Room থেকে একটা subTopic-এর মোট প্রশ্ন সংখ্যা */
    suspend fun getRoomTotalCount(
        sheet    : String,
        subject  : String,
        subTopic : String,
        tag      : String
    ): Int = if (tag.isBlank() || tag == "all") {
        dao.countBySubTopic(sheet, subject, subTopic)
    } else {
        dao.countFiltered(sheet, subject, subTopic, tag)
    }

    // ── FIX ("পরবর্তী বাটনে ফাঁকা স্ক্রিন" বাগ) — Phase 6 লেজি টপিক সিস্টেমের সাথে
    // সামঞ্জস্যপূর্ণ পেজিনেশন: topicId দিয়ে (subject/subTopic টেক্সট না) ──
    // ⚠️⚠️ CRITICAL FIX ("প্রশ্নই পাওয়া যাচ্ছে না" — সব টপিকে হঠাৎ ০ প্রশ্ন হয়ে যাওয়ার
    // আসল কারণ): এখানে sheet প্যারামিটার ".uppercase()" ছাড়াই সরাসরি dao-তে পাঠানো
    // হচ্ছিল ("Quiz"/"QBank"/"Study" — মিশ্র-কেস), কিন্তু Room-এ প্রতিটা রো সবসময়
    // uppercase sheet ("QUIZ"/"QBANK"/"STUDY") দিয়ে সেভ হয় (দেখো cacheNextTopicBatch,
    // getRoomQuestionsForTopic — ওখানে ঠিকই .uppercase() করা হয়)। SQLite-এর টেক্সট
    // তুলনা case-sensitive, তাই "QBank" ≠ "QBANK" — ফলে এই দুটো নতুন ফাংশন *সবসময়*
    // ০ রো রিটার্ন করছিল, যেকোনো tag/audience-নির্বিশেষে। এটাই ছিল সেই ভয়াবহ
    // রিগ্রেশন যেখানে হঠাৎ কোনো টপিকেই প্রশ্ন পাওয়া যাচ্ছিল না। এখন ঠিক হলো। ──
    suspend fun getRoomPagedQuestionsByTopic(
        sheet    : String,
        topicId  : String,
        tag      : String,
        page     : Int,
        pageSize : Int
    ): List<com.hanif.smartstudy.data.model.QuestionItem> {
        val offset = page * pageSize
        return dao.getByTopicIdPaged(sheet.uppercase(), topicId, tag, pageSize, offset).map { it.toQuestionItem() }
    }

    /** Room থেকে একটা topicId-এর (audience-filtered) মোট প্রশ্ন সংখ্যা */
    suspend fun getRoomTotalCountByTopic(sheet: String, topicId: String, tag: String): Int =
        dao.countByTopicIdFiltered(sheet.uppercase(), topicId, tag)

    /**
     * Firebase থেকে fetch করে Room-এ save করো।
     * Online sync — background-এ চলে।
     */
    suspend fun syncToRoom(content: AppContent) {
        val now = System.currentTimeMillis()
        Log.d("Repo", "syncToRoom: quiz=${content.quiz.size} study=${content.study.size} qbank=${content.qbank.size}")
        if (content.quiz.isNotEmpty())  dao.upsertAll(content.quiz.map  { it.toEntity(now) })
        if (content.qbank.isNotEmpty()) dao.upsertAll(content.qbank.map { it.toEntity(now) })
        if (content.study.isNotEmpty()) dao.upsertAll(content.study.map { it.toEntity(now) })
        Log.d("Repo", "syncToRoom: done")
    }

    // ── Phase 6: Reference data (Subjects/Topics/SubTopics/Tags/Posts/Institutions) ──

    /**
     * GAS `getReferenceData` থেকে Subjects/Topics/SubTopics/Tags/Posts/Institutions টেনে
     * Room-এ পুরো replace করে (ছোট টেবিল বলে delete-then-insert নিরাপদ, delta লাগে না)।
     * শুধু Google Sheet ডেটা-সোর্স মোডে কাজ করে — Firebase মোডে Phase 4 এখনো deferred, তাই
     * ওই মোডে এই reference টেবিলগুলো ফাঁকাই থাকবে (কল করলে false রিটার্ন করে, কিছু ভাঙে না)।
     * ব্যর্থ হলে চুপচাপ (Room-এর পুরনো ডেটা অপরিবর্তিত থাকে) — non-critical background sync,
     * এখনো কোনো ViewModel থেকে auto-call হয় না, প্রয়োজনমতো explicitly call করতে হবে।
     */
    /**
     * force=false (ডিফল্ট) হলে — Room-এ ইতিমধ্যে subjects/topics ডেটা থাকলে ও শেষ সফল
     * sync REF_SYNC_MIN_GAP_MS-এর মধ্যে হয়ে থাকলে নতুন GAS কল স্কিপ করে সরাসরি true
     * রিটার্ন করে (Room-এর ডেটাই "যথেষ্ট ফ্রেশ" ধরা হয়) — এটাই মূল ফিক্স যেটা প্রতিটা
     * Subject-list visit-কে ব্লকিং নেটওয়ার্ক কল থেকে বাঁচায়। force=true দিলে (যেমন
     * pull-to-refresh) গ্যাপ উপেক্ষা করে সবসময় GAS থেকে টাটকা ডেটা আনবে।
     */
    /**
     * force=false (ডিফল্ট) হলে — Room-এ ইতিমধ্যে subjects/topics ডেটা থাকলে ও শেষ সফল
     * sync REF_SYNC_MIN_GAP_MS-এর মধ্যে হয়ে থাকলে নতুন CDN কল স্কিপ করে সরাসরি true
     * রিটার্ন করে (Room-এর ডেটাই "যথেষ্ট ফ্রেশ" ধরা হয়)। force=true দিলে (যেমন
     * pull-to-refresh) গ্যাপ উপেক্ষা করে সবসময় CDN থেকে টাটকা ডেটা আনবে।
     *
     * FIX (Speed Plan Task 3, "Gas diye kuno read noy — never"): আগে GAS
     * `getReferenceData` কল হতো (cold-start/latency-প্রবণ)। এখন সম্পূর্ণ CDN-only —
     * ব্যর্থ হলে (network/CDN down) কোনো GAS fallback হয় না, Room-এর পুরনো ডেটা
     * অপরিবর্তিত থাকে + local notification দেখানো হয়।
     */
    suspend fun syncReferenceData(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (session.getDataSourceMode() != com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET) {
            return@withContext false
        }
        val now = System.currentTimeMillis()
        if (!force && (now - _lastRefSyncAt) < REF_SYNC_MIN_GAP_MS) {
            val hasCached = refDao.getAllSubjects().isNotEmpty()
            if (hasCached) {
                Log.d("Repo", "syncReferenceData: skip (fresh within ${REF_SYNC_MIN_GAP_MS}ms), using Room cache")
                return@withContext true
            }
            // Room এখনো খালি — গ্যাপের মধ্যে থাকলেও প্রথমবারের জন্য fetch করতেই হবে
        }
        if (!isOnline()) return@withContext false

        val subjects = com.hanif.smartstudy.data.remote.CdnService
            .fetchReferenceJson<com.hanif.smartstudy.data.model.SubjectRef>("reference/subjects.json")
        if (subjects == null) {
            com.hanif.smartstudy.util.CdnFailureNotifier.notify(context, "Subjects তালিকা আনা যায়নি")
            return@withContext false
        }
        val topics = com.hanif.smartstudy.data.remote.CdnService
            .fetchReferenceJson<com.hanif.smartstudy.data.model.TopicRef>("reference/topics.json")
            ?: run {
                com.hanif.smartstudy.util.CdnFailureNotifier.notify(context, "Topics তালিকা আনা যায়নি")
                return@withContext false
            }
        // ── tags/posts/institutions ছোট, non-critical reference — একটার fetch
        // ব্যর্থ হলেও পুরো sync আটকে দেওয়ার দরকার নেই, শুধু সেই অংশটা Room-এ
        // আগের মতোই থেকে যাবে (খালি লিস্টে ওভাররাইট না করে) ──
        val tags = com.hanif.smartstudy.data.remote.CdnService
            .fetchReferenceJson<com.hanif.smartstudy.data.model.TagRef>("reference/tags.json")
        val posts = com.hanif.smartstudy.data.remote.CdnService
            .fetchReferenceJson<com.hanif.smartstudy.data.model.PostRef>("reference/posts.json")
        val institutions = com.hanif.smartstudy.data.remote.CdnService
            .fetchReferenceJson<com.hanif.smartstudy.data.model.InstitutionRef>("reference/institutions.json")
        if (tags == null || posts == null || institutions == null) {
            com.hanif.smartstudy.util.CdnFailureNotifier.notify(context, "কিছু reference তালিকা (tags/posts/institutions) আনা যায়নি")
        }

        refDao.replaceAll(
            subjects     = subjects.map { it.toEntity() },
            topics       = topics.map { it.toEntity() },
            subtopics    = emptyList(), // GAS-এ কখনো আলাদা "Subtopics" শিট ছিলই না (REF_TABS দেখো) — topics-ই সাব-টপিক লেভেল
            tags         = tags?.map { it.toEntity() } ?: refDao.getAllTags(),
            posts        = posts?.map { it.toEntity() } ?: refDao.getAllPosts(),
            institutions = institutions?.map { it.toEntity() } ?: refDao.getAllInstitutions()
        )
        _lastRefSyncAt = now
        Log.d("Repo", "syncReferenceData (CDN): subjects=${subjects.size} topics=${topics.size}")
        true
    }

    // ── Room-cached reference data — instant, কোনো নেটওয়ার্ক কল ছাড়াই ──
    suspend fun getRoomSubjectsRef()                     = refDao.getAllSubjects()
    suspend fun getRoomSubjectsRefBySheet(sheet: String)  = refDao.getSubjectsBySheet(sheet)
    suspend fun getRoomTopicsForSubject(subjectId: String) = refDao.getTopicsForSubject(subjectId)
    suspend fun getRoomSubTopicsForTopic(topicId: String)  = refDao.getSubTopicsForTopic(topicId)
    suspend fun getRoomTags()                             = refDao.getAllTags()
    suspend fun getRoomPosts()                            = refDao.getAllPosts()
    suspend fun getRoomInstitutions()                     = refDao.getAllInstitutions()
    suspend fun getRoomAppearancesForQuestion(questionId: String) = refDao.getAppearancesForQuestion(questionId)
    suspend fun getRoomAppearancesForPost(postId: String)          = refDao.getAppearancesForPost(postId)
    suspend fun getRoomAppearancesForInstitution(institutionId: String) = refDao.getAppearancesForInstitution(institutionId)

    // ── Admin "Move" ডায়ালগে Subject/Topic নাম বেছে/টাইপ করলে, ব্যাকগ্রাউন্ড sync কলের
    // আগে আসল id বের করতে লাগে (GAS action-গুলো id-ভিত্তিক, নাম-ভিত্তিক না) ──
    suspend fun resolveSubjectId(sheet: String, subjectName: String): String? =
        refDao.getSubjectByName(sheet, subjectName)?.subjectId

    suspend fun resolveTopicId(subjectId: String, topicName: String): String? =
        refDao.getTopicByName(subjectId, topicName)?.topicId

    /**
     * "পদ অনুযায়ী ব্রাউজ" ফ্লো-র ডেটা: CDN-এর বাল্ক `exam-appearances.json`
     * থেকে পুরো Exam_Appearances টেবিল টেনে Room-এ replace করে।
     *
     * FIX (Speed Plan Task 3): আগে GAS `getAllExamAppearances` ব্যবহার হতো —
     * এখন CDN-only, কোনো GAS fallback নেই। ব্যর্থ হলে Room-এর পুরনো টেবিল
     * অপরিবর্তিত থাকে + local notification।
     */
    suspend fun syncExamAppearances(): Boolean = withContext(Dispatchers.IO) {
        if (session.getDataSourceMode() != com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET) {
            return@withContext false
        }
        if (!isOnline()) return@withContext false
        val appearances = com.hanif.smartstudy.data.remote.CdnService
            .fetchReferenceJson<com.hanif.smartstudy.data.model.ExamAppearanceRef>("exam-appearances.json")
        if (appearances == null) {
            com.hanif.smartstudy.util.CdnFailureNotifier.notify(context, "Exam Appearances তালিকা আনা যায়নি")
            return@withContext false
        }
        refDao.deleteAllExamAppearances()
        if (appearances.isNotEmpty()) {
            refDao.upsertExamAppearances(appearances.map { it.toEntity() })
        }
        Log.d("Repo", "syncExamAppearances (CDN): ${appearances.size}")
        true
    }

    /**
     * একগুচ্ছ questionId (Exam_Appearances থেকে পাওয়া) দিয়ে সরাসরি সেই নির্দিষ্ট প্রশ্নগুলো
     * Room থেকে টেনে আনে (audience-filtered) — "পদ অনুযায়ী ব্রাউজ"-এ Post+Institution
     * বাছাইয়ের পর ফ্ল্যাট প্রশ্ন-লিস্ট দেখানোর জন্য।
     */
    suspend fun getRoomQuestionsByIds(sheet: String, ids: List<String>, tag: String): List<com.hanif.smartstudy.data.model.QuestionItem> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            val entities = dao.getByFbKeysFiltered(sheet, ids, tag)
            entities.map { it.toQuestionItem() }
        }

    /**
     * ── FIX ("পদবী/প্রতিষ্ঠান-মোডে প্রশ্ন ০/০" বাগ) ──
     * getRoomQuestionsByIds() শুধু Room-এ যা আছে তাই ফেরত দেয় — Exam_Appearances
     * যেই questionId-গুলো লিংক করে সেগুলো যদি কখনো স্বাভাবিক Subject→Topic পথে
     * ব্রাউজ করে ডাউনলোড না হয়ে থাকে, Room-এ তারা থাকেই না, ফলে "০/০ প্রশ্ন" দেখাতো।
     * এই ফাংশন আগে চেক করে কোন id-গুলো Room-এ নেই, শুধু সেগুলো GAS-এর নতুন
     * getQuestionsByIds action দিয়ে টার্গেটেড এনে upsert করে দেয় — তারপর
     * getRoomQuestionsByIds() ঠিকঠাক সব প্রশ্ন খুঁজে পাবে। selectQBankInstitution
     * UnderPost()/selectQBankDesignationUnderInstitution()-এর ঠিক আগে কল করা হয়।
     */
    suspend fun ensureRoomQuestionsByIds(sheet: String, ids: List<String>): Unit =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            val roomSheet = sheet.uppercase()
            val existing  = dao.getExistingFbKeys(roomSheet, ids).toSet()
            val missing   = ids.filterNot { existing.contains(it) }
            if (missing.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            try {
                // ── FIX ("Exam_Appearances-এর question_id মেলে না" বাগ, root cause):
                // Exam_Appearances শীটের question_id আসলে "new_id" ফরম্যাট (QB-00002),
                // কিন্তু .toEntity() সবসময় fbKey = plain "id" (যেমন "2") বসায়। ফলে
                // GAS থেকে সঠিক প্রশ্ন এনে upsert হলেও, Room-এ সেটা fbKey="2" দিয়ে সেভ
                // হতো — অথচ পরের getRoomQuestionsByIds() কল করা হতো fbKey="QB-00002"
                // দিয়ে খুঁজতে (linkedQuestionIds যা Exam_Appearances থেকে এসেছে) —
                // কখনো মেলেনি, তাই "কোনো প্রশ্ন পাওয়া যায়নি" + খালি রেজাল্ট-কার্ড।
                // এখন যেই key (id বা new_id) দিয়ে আসলে রিকোয়েস্ট করা হয়েছিল (missing
                // লিস্টে যা ছিল), ঠিক সেই ভ্যালুটাকেই fbKey হিসেবে বসানো হচ্ছে — তাই
                // পরের read ঠিক একই key দিয়ে মিলে যাবে। ──
                suspend fun <T> upsertMatched(list: List<T>?, idOf: (T) -> String?, newIdOf: (T) -> String?, toEnt: (T) -> com.hanif.smartstudy.data.local.QuestionEntity) {
                    if (list.isNullOrEmpty()) return
                    val entities = list.mapNotNull { item ->
                        val requestedKey = missing.firstOrNull { it == idOf(item) || it == newIdOf(item) }
                            ?: idOf(item) ?: return@mapNotNull null
                        toEnt(item).copy(fbKey = requestedKey)
                    }
                    if (entities.isNotEmpty()) dao.upsertAll(entities)
                }
                when (sheet.uppercase()) {
                    "QUIZ" -> upsertMatched(
                        com.hanif.smartstudy.data.remote.GasContentService.fetchQuizByIds(missing),
                        { it.id }, { it.newId }, { it.toEntity(now) }
                    )
                    "QBANK" -> upsertMatched(
                        com.hanif.smartstudy.data.remote.GasContentService.fetchQBankByIds(missing),
                        { it.id }, { it.newId }, { it.toEntity(now) }
                    )
                    "STUDY" -> upsertMatched(
                        com.hanif.smartstudy.data.remote.GasContentService.fetchStudyByIds(missing),
                        { it.id }, { it.newId }, { it.toEntity(now) }
                    )
                }
            } catch (e: Exception) {
                Log.w("ContentRepository", "ensureRoomQuestionsByIds($sheet) failed: ${e.message}")
            }
        }

    // ── Review System (Admin-only) ────────────────────────────────────────

    /**
     * GAS `getReviewProgress` থেকে একটা sheet-এর subject/topic-ভিত্তিক reviewed-percentage
     * — SubjectListScreen/SubTopicListScreen-এ progress bar দেখানোর জন্য। শুধু Google Sheet
     * মোডে কাজ করে, ব্যর্থ হলে খালি map রিটার্ন করে (progress bar দেখাবে না, crash করবে না)।
     */
    suspend fun getReviewProgress(sheet: String): com.hanif.smartstudy.data.remote.GasContentService.ReviewProgress {
        if (session.getDataSourceMode() != com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET) {
            return com.hanif.smartstudy.data.remote.GasContentService.ReviewProgress()
        }
        return com.hanif.smartstudy.data.remote.GasContentService.fetchReviewProgress(sheet)
    }

    /**
     * একটা প্রশ্নকে "রিভিউ করা হয়েছে" মার্ক করে — GAS-এ লেখে (source of truth) এবং সফল
     * হলে Room cache-ও সাথে সাথে আপডেট করে (fresh fetch ছাড়াই local state নির্ভুল থাকে)।
     * reviewed=false পাঠিয়ে আনমার্কও করা যায় (ভুলে টিক পড়লে undo করার জন্য)।
     */
    suspend fun markQuestionReviewed(sheet: String, rowKey: String, reviewed: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!com.hanif.smartstudy.data.remote.GasContentService.isConfigured()) return@withContext false
        val now = System.currentTimeMillis()
        val fields = mapOf(
            "reviewed"   to reviewed.toString(),
            "reviewedAt" to if (reviewed) now.toString() else "0"
        )
        val result = com.hanif.smartstudy.data.remote.GasContentService.updateFields(sheet, rowKey, fields)
        if (result is com.hanif.smartstudy.data.remote.ApiResult.Success) {
            dao.updateReviewed(sheet.uppercase(), rowKey, reviewed, if (reviewed) now else 0L)
            true
        } else false
    }

    // ── Progressive topic-fill (অফলাইন-সক্ষম, ব্যাচ-ব্যাচ ক্যাশিং) ────────────────

    /**
     * একটা Topic-এর পরের ৫০-প্রশ্নের ব্যাচ (আগেরটা না, নতুনটা — TopicSyncEntity-তে
     * সেভ করা cursor থেকে) GAS `getQuestionsPage` দিয়ে এনে Room-এ যোগ করে। ইতিমধ্যে
     * পুরো Topic লোকালি থাকলে (hasMore==false) নেটওয়ার্ক কলই করে না — instant false।
     * অফলাইনে exception ছুঁড়তে পারে — caller-কে try/catch করে চুপচাপ উপেক্ষা করতে হবে
     * (Room-এ যা আছে তাই দেখানো হবে)।
     *
     * @return নতুন প্রশ্ন যোগ হয়েছে কিনা
     */
    /**
     * একটা Topic-এর পুরো প্রশ্ন-সেট CDN থেকে এনে Room-এ upsert করে।
     *
     * FIX (Speed Plan Task 3, "Gas diye kuno read noy — never"): আগে GAS
     * `getQuestionsPage` দিয়ে ৫০-৫০ ব্যাচে (cursor-ভিত্তিক pagination) আনা হতো —
     * এখন CDN-এ প্রতিটা topic-এর পুরো JSON একটাই ফাইলে থাকে বলে পুরোটা একবারেই
     * আসে, pagination আর দরকার নেই। manifest-এর hash Room-এ (`lastHash`) সেভ করা
     * hash-এর সাথে মিললে network call-ই স্কিপ হয় (CDN ফাইল immutable-per-hash)।
     *
     * CDN fetch ব্যর্থ হলে (network/timeout/misconfigured) — **কোনো GAS fallback
     * নেই** — Room-এ যা আছে তাই থেকে যায় (caller সেটাই দেখাবে), শুধু local
     * notification দেখানো হয়।
     *
     * @return নতুন প্রশ্ন যোগ হয়েছে কিনা
     */
    suspend fun cacheNextTopicBatch(sheet: String, topicId: String): Boolean = withContext(Dispatchers.IO) {
        val sheetPath = when (sheet) {
            "Quiz" -> "quiz"; "QBank" -> "qbank"; "Study" -> "study"
            else -> return@withContext false
        }
        val sync = topicSyncDao.get(topicId)
        val cachedCount = dao.countByTopicId(sheet.uppercase(), topicId)
        if (sync != null && !sync.hasMore && cachedCount > 0) return@withContext false
        if (!isOnline()) return@withContext false

        val manifest = getCachedManifest()
        if (manifest == null) {
            com.hanif.smartstudy.util.CdnFailureNotifier.notify(context, "Manifest আনা যায়নি")
            return@withContext false
        }
        val entry = manifest.topics[topicId]
        if (entry == null) {
            // manifest-এ এই topicId নেই — হয় সত্যিই কোনো প্রশ্ন নেই (delete/move হয়ে
            // গেছে), অথবা এখনো publish হয়নি। কোনো error না — শুধু কিছু cache হয়নি।
            return@withContext false
        }
        val hash = entry.hash ?: ""
        // ── hash অপরিবর্তিত + Room-এ ইতিমধ্যে প্রশ্ন আছে মানে এই ভার্সন আগেই
        // cache করা — network call পুরোপুরি স্কিপ ──
        if (hash.isNotBlank() && sync?.lastHash == hash && cachedCount > 0) {
            if (sync.hasMore) topicSyncDao.upsert(sync.copy(hasMore = false))
            return@withContext false
        }

        val now = System.currentTimeMillis()
        val items: List<*>? = when (sheet) {
            "Quiz"  -> com.hanif.smartstudy.data.remote.CdnService.fetchTopicJson<com.hanif.smartstudy.data.model.QuizItem>(sheetPath, topicId, hash)
            "QBank" -> com.hanif.smartstudy.data.remote.CdnService.fetchTopicJson<com.hanif.smartstudy.data.model.QBankItem>(sheetPath, topicId, hash)
            "Study" -> com.hanif.smartstudy.data.remote.CdnService.fetchTopicJson<com.hanif.smartstudy.data.model.StudyItem>(sheetPath, topicId, hash)
            else    -> null
        }
        if (items == null) {
            com.hanif.smartstudy.util.CdnFailureNotifier.notify(context, "$topicId আনা যায়নি")
            return@withContext false
        }
        val entities = when (sheet) {
            "Quiz"  -> (items as List<com.hanif.smartstudy.data.model.QuizItem>).map { it.toEntity(now) }
            "QBank" -> (items as List<com.hanif.smartstudy.data.model.QBankItem>).map { it.toEntity(now) }
            "Study" -> (items as List<com.hanif.smartstudy.data.model.StudyItem>).map { it.toEntity(now) }
            else    -> emptyList()
        }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
        topicSyncDao.upsert(TopicSyncEntity(topicId, null, false, now, hash))
        entities.isNotEmpty()
    }

    // ── CDN manifest — ৫-মিনিট TTL in-memory cache, একাধিক topic-এর জন্য বারবার
    // নেটওয়ার্ক কল না করে একবারই আনা হয় (App খোলা/pull-to-refresh/periodic নীতি
    // অনুযায়ী)। ব্যর্থ হলে null রিটার্ন করে, পুরনো cached manifest থাকলেও সেটা
    // stale হতে পারে বলে reuse না করে caller-কে জানানো হয় (caller Room cache
    // থেকে দেখাবে) ──
    private suspend fun getCachedManifest(): com.hanif.smartstudy.data.remote.CdnService.Manifest? {
        val now = System.currentTimeMillis()
        _manifestCache?.let { if (now - _manifestCachedAt < MANIFEST_TTL_MS) return it }
        val fresh = com.hanif.smartstudy.data.remote.CdnService.fetchManifest() ?: return _manifestCache
        _manifestCache = fresh
        _manifestCachedAt = now
        return fresh
    }

    /** এই মুহূর্তে Topic-টা লোকালি ১০০% আছে কিনা (hasMore==false মানে আর ফেচ করার কিছু নেই) */
    suspend fun isTopicFullySynced(topicId: String): Boolean = withContext(Dispatchers.IO) {
        topicSyncDao.get(topicId)?.hasMore == false
    }

    /** একটা Topic-এ Room-এ এখন পর্যন্ত যতটুকু cache হয়েছে সব (audience-filtered) — cacheNextTopicBatch()-এর পরে কল করো */
    suspend fun getRoomQuestionsForTopic(sheet: String, topicId: String, tag: String): List<com.hanif.smartstudy.data.model.QuestionItem> =
        withContext(Dispatchers.IO) {
            dao.getByTopicId(sheet.uppercase(), topicId, tag).map { it.toQuestionItem() }
        }


    /** GAS getQuestionsPage-এর ফলাফল — এই page-এর প্রশ্নগুলো + পরের page-এর cursor */
    data class QuestionsPage(
        val items      : List<com.hanif.smartstudy.data.model.QuestionItem>,
        val nextCursor : String?,
        val hasMore    : Boolean
    )

    /**
     * Phase 6 — GAS-এর নতুন paginated `getQuestionsPage` endpoint কল করে (শুধু Google Sheet
     * মোডে; Firebase মোডে Phase 4 deferred বলে সমতুল্য pagination নেই)। topicId Room-এর
     * TopicEntity থেকে আসে (subject/topic-এর নাম দিয়ে না, topic_id দিয়ে) — caller আগে
     * getRoomTopicsForSubject() দিয়ে topicId বের করে নেবে।
     *
     * ⚠️ এটা এখনো UI (QuestionListScreen/QuizViewModel) থেকে call হয় না — সেই wiring পরের
     * ধাপে ("Infinite-scroll wiring") হবে। এই মেথড শুধু নেটওয়ার্ক-লেয়ার প্রস্তুত রাখে,
     * আর UI wire করার আগে GAS response-এর ঠিক ফিল্ড-নাম verify করে নেওয়া উচিত
     * (দেখো GasContentService.fetchQuestionsPage-এর কমেন্ট)।
     */
    suspend fun getQuestionsPage(
        sheet      : String,             // "Quiz" | "QBank" | "Study"
        topicId    : String,
        subtopicId : String? = null,     // শুধু QBank-এ ব্যবহৃত (ঐচ্ছিক)
        cursor     : String? = null,
        limit      : Int = 50
    ): DataState<QuestionsPage> {
        if (session.getDataSourceMode() != com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET) {
            return DataState.Error("getQuestionsPage শুধু Google Sheet ডেটা-সোর্স মোডে কাজ করে")
        }
        return withContext(Dispatchers.IO) {
            try {
                when (sheet) {
                    "Quiz" -> {
                        val page = com.hanif.smartstudy.data.remote.GasContentService.fetchQuizPage(topicId, cursor, limit)
                        DataState.Success(QuestionsPage(
                            items = page.items.map { com.hanif.smartstudy.data.model.QuestionItem.fromQuizItem(it) },
                            nextCursor = page.nextCursor, hasMore = page.hasMore
                        ))
                    }
                    "QBank" -> {
                        val page = com.hanif.smartstudy.data.remote.GasContentService.fetchQBankPage(topicId, subtopicId, cursor, limit)
                        DataState.Success(QuestionsPage(
                            items = page.items.map { com.hanif.smartstudy.data.model.QuestionItem.fromQBankItem(it) },
                            nextCursor = page.nextCursor, hasMore = page.hasMore
                        ))
                    }
                    "Study" -> {
                        val page = com.hanif.smartstudy.data.remote.GasContentService.fetchStudyPage(topicId, cursor, limit)
                        DataState.Success(QuestionsPage(
                            items = page.items.map { com.hanif.smartstudy.data.model.QuestionItem.fromStudyItem(it) },
                            nextCursor = page.nextCursor, hasMore = page.hasMore
                        ))
                    }
                    else -> DataState.Error("Unknown sheet: $sheet")
                }
            } catch (e: Exception) {
                DataState.Error(e.message ?: "getQuestionsPage failed")
            }
        }
    }

    // ── FIX (Speed Plan Task 3.5): আগে এখানে applyIncrementalOrFullSync()/mergeById()
    // ফাংশন ছিল — GAS delta/full-resync লজিক, শুধু getContent()-এর পুরনো
    // background-refresh পাথ থেকেই কল হতো। getContent() এখন Room-only (উপরে দেখো),
    // এই ফাংশনগুলোর আর কোনো caller নেই বলে সরিয়ে ফেলা হলো (dead code, GAS
    // fetchAllContent/fetchIncrementalContent রেফারেন্স করত যেটা "কখনো GAS read
    // না" নিয়মের সাথে সাংঘর্ষিক ছিল)।

    // ── Stale-While-Revalidate ─────────────────────────────────────────────────
    // FIX (Speed Plan Task 3.5, "Room a o same korbe — topic by topic"):
    // আগে এই ফাংশন প্রথমবার GAS দিয়ে ~১৪,০০০ রো (Quiz+QBank+Study পুরো sheet)
    // একবারে টেনে আনত, তারপর ডিস্ক/মেমরি-cache করত। এখন সম্পূর্ণ ভিন্ন মডেল —
    // GAS/CDN-এ কোনো "bulk fetch" নেই, শুধু Room-এ **এখন পর্যন্ত যতটুকু cache
    // হয়েছে** (ইউজার যেসব topic ভিজিট করেছে, cacheNextTopicBatch() দিয়ে CDN
    // থেকে ধীরে ধীরে জমা হয়েছে) সেটাই রিটার্ন করে। ব্লকিং নেই, নেটওয়ার্ক কল নেই —
    // Room read সবসময় instant।
    //
    // ⚠️ Trade-off (ইচ্ছাকৃত, ব্যবহারকারীর সিদ্ধান্ত অনুযায়ী): Search/Weak-topics/
    // Random-quiz-এর মতো ফিচার যেগুলো "সব প্রশ্ন" আশা করে, সেগুলো এখন শুধু
    // এখনো-cache-হওয়া topic-গুলোর প্রশ্নই দেখবে — নতুন install-এ শুরুতে খালি/কম
    // থাকবে, ইউজার যত বেশি টপিক ব্রাউজ করবে তত সম্পূর্ণ হতে থাকবে। এটা প্রথম
    // দেখায় "silent incomplete result" মনে হতে পারে বলেই এই ট্রেড-অফটা এখানে
    // স্পষ্ট করে লেখা হলো — কিন্তু এটাই ইচ্ছাকৃত সিদ্ধান্ত: আগেভাগে সব একসাথে
    // আনলে app স্লো হয়ে যেত, যেটা এড়ানোর জন্যই পুরো এই মাইগ্রেশন।
    //
    // `forceRefresh`/`onBackgroundUpdate` প্যারামিটার দুটো signature-compatibility-র
    // জন্য রাখা হয়েছে (caller-দের কোড না বদলাতে) — কিন্তু এখন আর কোনো effect নেই,
    // কারণ background network refresh-এর ধারণাটাই আর নেই (Room read synchronous)।
    suspend fun getContent(
        forceRefresh: Boolean = false,
        onBackgroundUpdate: ((AppContent) -> Unit)? = null
    ): DataState<AppContent> = withContext(Dispatchers.IO) {
        val quiz  = dao.getAll("QUIZ").map  { it.toQuizItem() }
        val qbank = dao.getAll("QBANK").map { it.toQBankItem() }
        val study = dao.getAll("STUDY").map { it.toStudyItem() }
        val now = System.currentTimeMillis()
        val content = AppContent(quiz = quiz, qbank = qbank, study = study, fetchedAt = now, remoteUpdatedAt = now)
        _memCache = content
        DataState.Success(content, fromCache = true)
    }

    fun getXpInfo(): XpInfo {
        val user = session.getCurrentUser()
        return XpInfo.fromXp(user?.xp ?: 0)
    }

    // ── XP award করা — Firebase Users REST API দিয়ে (SDK transaction নয়)
    //    REST API consistent — বাকি সব Firebase write এভাবেই হয়।
    //    Local session এও তাৎক্ষণিক update — Home screen সাথে সাথেই নতুন XP দেখায়।
    suspend fun awardXp(phone: String, delta: Int) {
        if (delta == 0 || phone.isBlank()) return

        // ── Step 1: Local session তাৎক্ষণিক update ──
        try {
            val current = session.getCurrentUser()
            if (current != null) {
                val newXp = (current.xp + delta).coerceAtMost(999999)
                session.saveUser(current.copy(xp = newXp))
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "awardXp local update failed: ${e.message}")
        }

        // ── Step 2: Firebase REST API দিয়ে XP update ──
        // SDK transaction এর বদলে REST ব্যবহার — consistent, reliable
        try {
            val token = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            if (token.isBlank()) {
                Log.w("ContentRepository", "awardXp: no auth token, skipping Firebase write")
                return
            }
            val base  = BuildConfig.FIREBASE_URL.trimEnd('/')
            val auth  = "?auth=$token"
            val httpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val gson2 = com.google.gson.Gson()

            // ── Step 2a: User এর Firebase key খুঁজে বের করো ──
            val queryUrl = "$base/Users.json?orderBy=%22Phone%22&equalTo=%22${phone.trim()}%22$auth"
            val queryResp = httpClient.newCall(
                okhttp3.Request.Builder().url(queryUrl).get().build()
            ).execute()
            val queryBody = queryResp.body?.string() ?: ""
            queryResp.close()

            if (queryBody.isBlank() || queryBody == "null" || queryBody == "{}") {
                Log.w("ContentRepository", "awardXp: user not found in Firebase for $phone")
                return
            }

            val rootMap = gson2.fromJson(queryBody, Map::class.java) as? Map<String, Any> ?: return
            val (userKey, userMap) = rootMap.entries.firstOrNull()
                ?.let { it.key to (it.value as? Map<String, Any>) }
                ?: run { Log.w("ContentRepository", "awardXp: malformed user data"); return }
            if (userMap == null) return

            // ── Step 2b: Current Firebase XP + delta ──
            val firebaseXp = userMap["XP"]?.toString()?.toIntOrNull()
                ?: userMap["xp"]?.toString()?.toIntOrNull() ?: 0
            val newXp = (firebaseXp + delta).coerceAtMost(999999)

            // ── Step 2c: PATCH করো — শুধু XP field, বাকি সব অপরিবর্তিত ──
            val patchUrl  = "$base/Users/$userKey.json$auth"
            val patchBody = com.google.gson.JsonObject().apply { addProperty("XP", newXp) }
                .toString().toRequestBody("application/json".toMediaType())
            val patchResp = httpClient.newCall(
                okhttp3.Request.Builder().url(patchUrl).patch(patchBody).build()
            ).execute()
            val patchCode = patchResp.code
            patchResp.close()

            Log.d("ContentRepository", "awardXp: $phone +$delta → newXp=$newXp (Firebase HTTP $patchCode)")
        } catch (e: Exception) {
            Log.e("ContentRepository", "awardXp Firebase write failed: ${e.message}")
        }
    }

    suspend fun getStreakInfo(): StreakInfo {
        val streakDays = cache.getStreak()
        val daysBn     = listOf("শনি","রবি","সোম","মঙ্গল","বুধ","বৃহস্পতি","শুক্র")
        val todayJS     = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
        val jsToOurIdx  = intArrayOf(1,2,3,4,5,6,0)
        val todayOurIdx = jsToOurIdx[todayJS]
        val weekDays = daysBn.mapIndexed { i, label ->
            val daysAgo = (todayOurIdx - i + 7) % 7
            val isToday = i == todayOurIdx
            val isDone  = !isToday && daysAgo in 1..streakDays
            StreakDay(label, isToday, isDone)
        }
        return StreakInfo(streakDays, weekDays)
    }

    suspend fun markTodayActivity() { cache.updateStreak() }

    suspend fun getGoalProgress(): GoalProgress {
        val goalMin = session.getDailyGoal()
        val doneMin = cache.getTodayStudyMinutes()
        val pct     = if (goalMin > 0) minOf(100f, doneMin * 100f / goalMin) else 0f
        return GoalProgress(goalMin, doneMin, pct)
    }

    suspend fun getStudyStats(): StudyStats {
        val (today, week, total) = cache.getStudyStats()
        val correct = cache.getCorrectCount()
        val wrong   = cache.getWrongCount()
        val total2  = correct + wrong
        val acc     = if (total2 > 0) (correct * 100) / total2 else 0
        return StudyStats(today, week, total, correct, wrong, acc)
    }

    fun getExamCountdown(): ExamCountdown {
        return try {
            val sharedPrefs = context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)
            val examDateStr = sharedPrefs.getString("exam_date", null) ?: return ExamCountdown()
            val examName    = sharedPrefs.getString("exam_name", "পরীক্ষা") ?: "পরীক্ষা"
            val sdf         = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val examDate    = sdf.parse(examDateStr) ?: return ExamCountdown()
            val diff        = examDate.time - System.currentTimeMillis()
            if (diff <= 0) return ExamCountdown(examName, 0, 0, 0, 0, true)
            ExamCountdown(examName,
                diff / (1000*60*60*24),
                (diff % (1000*60*60*24)) / (1000*60*60),
                (diff % (1000*60*60)) / (1000*60),
                (diff % (1000*60)) / 1000, true)
        } catch (e: Exception) { ExamCountdown() }
    }

    fun saveExamDate(date: String, name: String) {
        context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE).edit()
            .putString("exam_date", date)
            .putString("exam_name", name)
            .apply()
    }

    fun clearExamDate() {
        context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE).edit()
            .remove("exam_date")
            .remove("exam_name")
            .apply()
    }

    suspend fun submitQuizAnswer(questionId: String, isCorrect: Boolean) {
        val phone = session.getCurrentUser()?.phone ?: return
        if (isCorrect) cache.incrementCorrect() else cache.incrementWrong()
        if (isOnline()) SyncWorker.scheduleOneTime(context)
        else queue.enqueueQuizAnswer(questionId, isCorrect, phone)
    }

    suspend fun submitStudyProgress(minutes: Int, topic: String) {
        val phone = session.getCurrentUser()?.phone ?: return
        cache.addStudyMinutes(minutes)
        markTodayActivity()
        if (!isOnline()) queue.enqueueStudyProgress(phone, minutes, topic)
        SyncWorker.scheduleOneTime(context)
    }

    /**
     * Model Test-এ written উত্তর অটো-চেক (matchPct) হয় না — ইউজার যেভাবে লিখেছে ঠিক সেভাবেই
     * Firebase-এ সংরক্ষণ করা হয়, যাতে এডমিন "পরে" (আলাদা রিভিউ স্ক্রিন থেকে) নিজে যাচাই করে দেখতে পারে।
     * best-effort write (offline queue নেই) — নেট না থাকলে/fail হলে silently skip করে, quiz flow ব্লক হয় না।
     */
    suspend fun saveModelTestWrittenAnswer(
        subject: String, testNumber: Int, questionKey: String,
        questionText: String, userText: String
    ) {
        val phone = session.getCurrentUser()?.phone ?: return
        try {
            withContext(Dispatchers.IO) {
                val token = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
                if (token.isBlank()) return@withContext
                val base = BuildConfig.FIREBASE_URL.trimEnd('/')
                val safeSubject = android.net.Uri.encode(subject)
                val safePhone   = android.net.Uri.encode(phone)
                val safeQKey    = android.net.Uri.encode(questionKey)
                val url = "$base/ModelTestSubmissions/$safeSubject/$testNumber/$safePhone/$safeQKey.json?auth=$token"
                val gson2 = com.google.gson.Gson()
                val body = gson2.toJson(mapOf(
                    "question"    to questionText,
                    "userAnswer"  to userText,
                    "submittedAt" to System.currentTimeMillis()
                ))
                val httpClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                httpClient.newCall(
                    okhttp3.Request.Builder().url(url)
                        .put(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().close()
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "saveModelTestWrittenAnswer failed: ${e.message}")
        }
    }

    // ── Admin edit এর পরে in-memory + disk cache সরাসরি patch করো ──
    // পুরো cache invalidate/refetch করার বদলে শুধু matching row টাই বদলে দেয়,
    // যাতে স্ক্রিন reload না হয়ে সাথে সাথে আপডেট দেখা যায় (RoutineFocusSheet/
    // MenuViewModel.adminEditQuestion ও adminSwapOptions থেকে call হয়)।
    suspend fun patchContentAndPersist(sheet: String, rowKey: String, fields: Map<String, String>) {
        val base = _memCache ?: cache.loadContent() ?: return
        val gson = com.hanif.smartstudy.data.model.CaseInsensitiveGson.instance

        fun <T : Any> patchItem(item: T, idOf: (T) -> String?): T {
            if (idOf(item) != rowKey) return item
            val cls = item::class.java
            val obj = gson.toJsonTree(item, cls).asJsonObject
            fields.forEach { (k, v) -> obj.addProperty(k, v) }
            return gson.fromJson(obj, cls)
        }

        val patched = when (sheet) {
            "Study" -> base.copy(study = base.study.map { patchItem(it) { s -> s.id } })
            "Quiz"  -> base.copy(quiz  = base.quiz.map  { patchItem(it) { q -> q.id } })
            "QBank" -> base.copy(qbank = base.qbank.map { patchItem(it) { q -> q.id } })
            else    -> base
        }

        _memCache = patched
        cache.saveContent(patched)
    }

    // ── FIX ("QBank-এ এডিট করলে সাথে সাথে স্ক্রিনে দেখা যায় না" বাগ):
    // patchContentAndPersist() উপরের পুরনো bulk "_memCache"/disk-cache প্যাচ করে —
    // কিন্তু টপিক-স্ক্রিনে যা দেখানো হয় তা আসে Room-এর QuestionEntity টেবিল থেকে
    // (cacheNextTopicBatch/getRoomQuestionsForTopic, Phase 6 lazy topic system)।
    // এই দুটো আলাদা ক্যাশ — তাই পুরনোটা প্যাচ করলেও Room অস্পর্শিত থেকে যেত।
    // QBank-এ তো plain "subject"/"subTopic" টেক্সট কলামই নেই, তাই ওই পুরনো
    // নাম-ভিত্তিক refresh (refreshQuestionsInPlace) QBank-এ কখনোই কিছু মেলাতে
    // পারতো না — ফলে QBank এডিট GAS-এ সেভ হতো ঠিকই, কিন্তু স্ক্রিনে কখনো
    // সাথে সাথে দেখা যেত না। এই ফাংশন এখন সরাসরি Room-এর row-টা (fbKey দিয়ে,
    // নাম না) প্যাচ করে দেয় — Quiz/QBank/Study সবগুলোতেই নির্ভরযোগ্যভাবে কাজ করে। ──
    suspend fun patchRoomQuestion(sheet: String, rowKey: String, fields: Map<String, String>) =
        withContext(Dispatchers.IO) {
            val roomSheet = sheet.uppercase()
            val existing  = dao.getById(roomSheet, rowKey) ?: return@withContext
            val updated = existing.copy(
                question    = fields["question"]    ?: existing.question,
                optionA     = fields["option1"]      ?: existing.optionA,
                optionB     = fields["option2"]      ?: existing.optionB,
                optionC     = fields["option3"]      ?: existing.optionC,
                optionD     = fields["option4"]      ?: existing.optionD,
                // ── GAS-এ "correct" আর অ্যাপে "answer" — এডিট ডায়ালগ দুটোই পাঠায়,
                // তাই যেকোনো একটা এলেই যথেষ্ট (correct-কে অগ্রাধিকার, GAS-এর সোর্স-অফ-ট্রুথ) ──
                answer      = fields["correct"] ?: fields["answer"] ?: existing.answer,
                explanation = fields["explanation"] ?: existing.explanation,
                technique   = fields["technique"]    ?: existing.technique
            )
            dao.upsert(updated)
        }

    // ── Admin নতুন প্রশ্ন যোগ করার পর (বা offline/fail হলেও) in-memory +
    //    disk cache এ সরাসরি নতুন item যোগ করে দেয় — patchContentAndPersist এর
    //    মতোই, কিন্তু existing row খোঁজার বদলে সম্পূর্ণ নতুন row append করে।
    //    rowKey এখানে Firebase push-key (অনলাইন সফল হলে) অথবা একটা টেম্পোরারি
    //    লোকাল আইডি (offline/fail হলে, পরে sync হওয়ার সময় আসল key দিয়ে বদলে যাবে)।
    suspend fun addContentAndPersist(sheet: String, rowKey: String, fields: Map<String, String>) {
        val base = _memCache ?: cache.loadContent() ?: return
        val gson = com.hanif.smartstudy.data.model.CaseInsensitiveGson.instance
        val obj  = com.google.gson.JsonObject()
        fields.forEach { (k, v) -> obj.addProperty(k, v) }
        obj.addProperty("id", rowKey)

        val patched = when (sheet) {
            "Study" -> base.copy(study = base.study + gson.fromJson(obj, com.hanif.smartstudy.data.model.StudyItem::class.java))
            "Quiz"  -> base.copy(quiz  = base.quiz  + gson.fromJson(obj, com.hanif.smartstudy.data.model.QuizItem::class.java))
            "QBank" -> base.copy(qbank = base.qbank + gson.fromJson(obj, com.hanif.smartstudy.data.model.QBankItem::class.java))
            else    -> base
        }

        _memCache = patched
        cache.saveContent(patched)
    }

    // ── Admin প্রশ্ন কার্ড ডিলিট করার পর in-memory + disk cache থেকে সেই
    //    আইটেমটাই সরিয়ে দেয় — patchContentAndPersist এর মতোই প্যাটার্ন, কিন্তু
    //    row বদলানোর বদলে পুরোপুরি বাদ দিয়ে দেয় (প্রশ্ন + অপশন + উত্তর + ব্যাখ্যা,
    //    পুরো কার্ডটাই)।
    suspend fun removeContentAndPersist(sheet: String, rowKey: String) {
        val base = _memCache ?: cache.loadContent() ?: return

        val patched = when (sheet) {
            "Study" -> base.copy(study = base.study.filter { it.id != rowKey })
            "Quiz"  -> base.copy(quiz  = base.quiz.filter  { it.id != rowKey })
            "QBank" -> base.copy(qbank = base.qbank.filter { it.id != rowKey })
            else    -> base
        }

        _memCache = patched
        cache.saveContent(patched)
    }

    // ── FIX ("ডিলিট করলে অ্যাপে সাথে সাথে হারিয়ে যায় না" বাগ): Room-এর topicId-ভিত্তিক
    // ক্যাশ থেকেও (আসল টপিক-স্ক্রিন যেটা পড়ে) সরাসরি মুছে দেয় — removeContentAndPersist()
    // শুধু পুরনো bulk cache প্যাচ করে, এটা আলাদা এবং দুটোই দরকার। ──
    suspend fun removeRoomQuestion(sheet: String, rowKey: String) = withContext(Dispatchers.IO) {
        dao.deleteByFbKey(sheet.uppercase(), rowKey)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FIX ("সাবজেক্ট/টপিক Delete হচ্ছে না" বাগ): adminDeleteSubjectOrTopic() আগে
    // প্রথমে (network-এর আগেই) কোনো লোকাল ক্যাশ/Room touch করতো না — সরাসরি Sheet-এ
    // পুরো sheet fetch + deleteByIds কল করে অপেক্ষা করত, তারপর তবেই UI বলত "ডিলিট
    // হয়েছে"। GAS cold-start/বড় Sheet-এ এটা সহজেই কয়েক সেকেন্ড-মিনিট লাগতে পারত,
    // ফলে ইউজার/এডমিনের মনে হতো ডিলিট "হচ্ছেই না"। এখন adminEditQuestion/
    // adminDeleteQuestion-এর মতোই প্যাটার্ন: এই তিনটে ফাংশন দিয়ে সাথে সাথেই (network
    // কলের আগে) লোকাল সব জায়গা (bulk cache + Room questions + Room reference টেবিল)
    // থেকে subject/subTopic-এর সব প্রশ্ন ও নিজেই সরিয়ে দেওয়া হয়, আসল Sheet delete
    // ব্যাকগ্রাউন্ডে (দেখো MenuViewModel.adminDeleteSubjectOrTopic) চলে।
    // ═════════════════════════════════════════════════════════════════════════

    /** Admin Subject/SubTopic ডিলিট করার পর in-memory + disk cache থেকে সাথে সাথেই
     *  matching সব প্রশ্ন বাদ দেয় — removeContentAndPersist()-এর মতোই প্যাটার্ন, কিন্তু
     *  একটা id-এর বদলে subject(+subTopic) মিলিয়ে বাল্ক ফিল্টার করে। */
    suspend fun removeContentBySubjectAndPersist(
        sheet: String, subject: String, subTopic: String, deleteSubTopic: Boolean
    ) {
        val base = _memCache ?: cache.loadContent() ?: return
        fun norm(s: String?) = s?.trim()?.lowercase().orEmpty()
        val subjN  = norm(subject)
        val subTpN = norm(subTopic)
        fun matches(s: String?, st: String?): Boolean =
            if (deleteSubTopic) norm(s) == subjN && norm(st) == subTpN else norm(s) == subjN

        val patched = when (sheet) {
            "Study" -> base.copy(study = base.study.filterNot { matches(it.subject, it.subTopic) })
            "Quiz"  -> base.copy(quiz  = base.quiz.filterNot  { matches(it.subject, it.subTopic) })
            "QBank" -> base.copy(qbank = base.qbank.filterNot { matches(it.subject, it.subTopic) })
            else    -> base
        }

        _memCache = patched
        cache.saveContent(patched)
    }

    /** removeRoomQuestion()-এর বাল্ক সংস্করণ — Subject/SubTopic-এর সব প্রশ্ন Room থেকে
     *  একসাথে সরিয়ে দেয়, যাতে টপিক-স্ক্রিন (Room-নির্ভর) সাথে সাথে খালি দেখায়। */
    suspend fun removeRoomQuestionsBySubject(
        sheet: String, subject: String, subTopic: String, deleteSubTopic: Boolean
    ) = withContext(Dispatchers.IO) {
        val roomSheet = sheet.uppercase()
        if (deleteSubTopic) dao.deleteBySubjectAndSubTopic(roomSheet, subject, subTopic)
        else dao.deleteBySubject(roomSheet, subject)
    }

    /** Room-এর reference টেবিল (Subjects/Topics/SubTopics — SubjectListScreen/
     *  SubTopicListScreen এখান থেকেই পড়ে) থেকে নিজে Subject/Topic এন্ট্রিটাও সাথে সাথে
     *  সরিয়ে দেয়, নাহলে ভিতরের সব প্রশ্ন মুছে গেলেও Subject/Topic নিজেই (খালি অবস্থায়)
     *  তালিকায় থেকে যেত। নাম দিয়ে subjectId/topicId রিজলভ করে রিটার্ন করে — পাওয়া গেলে
     *  ব্যাকগ্রাউন্ডে Sheet-এর Subjects/Topics ট্যাব থেকেও (GasContentService.deleteReferenceItem)
     *  একইভাবে ডিলিট করতে caller (MenuViewModel) এই id ব্যবহার করে। পুরনো/আগে থেকে
     *  reference-টেবিলে sync না-হওয়া ডেটার জন্য null রিটার্ন করে (তখনও প্রশ্ন ঠিকই ডিলিট
     *  হয়ে গেছে, শুধু id-ভিত্তিক reference cleanup skip হয়)। */
    suspend fun removeRoomReferenceForSubjectOrTopic(
        sheet: String, subject: String, subTopic: String, deleteSubTopic: Boolean
    ): String? = withContext(Dispatchers.IO) {
        val subjectEntity = refDao.getSubjectByName(sheet, subject) ?: return@withContext null
        if (!deleteSubTopic) {
            refDao.deleteSubjectCascade(subjectEntity.subjectId)
            subjectEntity.subjectId
        } else {
            val topicEntity = refDao.getTopicByName(subjectEntity.subjectId, subTopic) ?: return@withContext null
            refDao.deleteTopicCascade(topicEntity.topicId)
            topicEntity.topicId
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Admin "Move" (ফাইল ম্যানেজারের মতো) — এক/একাধিক প্রশ্ন অথবা একটা গোটা Topic অন্য
    // Subject/Topic-এ move করার instant-local helper। প্রশ্ন/টপিকের নিজের id
    // (merge ছাড়া) অপরিবর্তিত থাকে — শুধু subject/subTopic/subjectId/topicId বদলায়,
    // তাই bookmark/quiz-history/Exam_Appearances কিছুই ভাঙে না।
    // ═════════════════════════════════════════════════════════════════════════

    /** নির্দিষ্ট কয়েকটা প্রশ্ন (id list) — bulk cache-এ একসাথে subject/subTopic বদলে দেয়
     *  (patchContentAndPersist()-এর একই-fields-বহু-id সংস্করণ, single-pass)। */
    suspend fun patchContentBulkAndPersist(sheet: String, rowKeys: Set<String>, fields: Map<String, String>) {
        val base = _memCache ?: cache.loadContent() ?: return
        val gson = com.hanif.smartstudy.data.model.CaseInsensitiveGson.instance

        fun <T : Any> patchItem(item: T, idOf: (T) -> String?): T {
            if (idOf(item) !in rowKeys) return item
            val cls = item::class.java
            val obj = gson.toJsonTree(item, cls).asJsonObject
            fields.forEach { (k, v) -> obj.addProperty(k, v) }
            return gson.fromJson(obj, cls)
        }

        val patched = when (sheet) {
            "Study" -> base.copy(study = base.study.map { patchItem(it) { s -> s.id } })
            "Quiz"  -> base.copy(quiz  = base.quiz.map  { patchItem(it) { q -> q.id } })
            "QBank" -> base.copy(qbank = base.qbank.map { patchItem(it) { q -> q.id } })
            else    -> base
        }

        _memCache = patched
        cache.saveContent(patched)
    }

    /** পুরো Topic-এর (subject+subTopic নাম মিলিয়ে) সব প্রশ্ন — bulk cache-এ
     *  subject/subTopic বদলে দেয়। */
    suspend fun moveContentByTopicAndPersist(
        sheet: String, oldSubject: String, oldSubTopic: String, newSubject: String, newSubTopic: String
    ) {
        val base = _memCache ?: cache.loadContent() ?: return
        fun norm(s: String?) = s?.trim()?.lowercase().orEmpty()
        val oSubjN = norm(oldSubject); val oSubTN = norm(oldSubTopic)
        val gson = com.hanif.smartstudy.data.model.CaseInsensitiveGson.instance

        fun <T : Any> patchItem(item: T, subjOf: (T) -> String?, subTOf: (T) -> String?): T {
            if (norm(subjOf(item)) != oSubjN || norm(subTOf(item)) != oSubTN) return item
            val cls = item::class.java
            val obj = gson.toJsonTree(item, cls).asJsonObject
            obj.addProperty("subject", newSubject)
            obj.addProperty("sub_topic", newSubTopic)
            return gson.fromJson(obj, cls)
        }

        val patched = when (sheet) {
            "Study" -> base.copy(study = base.study.map { patchItem(it, { s -> s.subject }, { s -> s.subTopic }) })
            "Quiz"  -> base.copy(quiz  = base.quiz.map  { patchItem(it, { q -> q.subject }, { q -> q.subTopic }) })
            "QBank" -> base.copy(qbank = base.qbank.map { patchItem(it, { q -> q.subject }, { q -> q.subTopic }) })
            else    -> base
        }

        _memCache = patched
        cache.saveContent(patched)
    }

    // ── FIX ("সাবজেক্টে টপিক-সংখ্যা / টপিকে প্রশ্ন-সংখ্যা বেমিল দেখাচ্ছে", "move করার পর
    // কাউন্ট রিয়েল-টাইম আপডেট হয় না"): Topics reference-টেবিলের rowCount কলাম শুধু
    // syncReferenceData() (GAS থেকে, পর্যায়ক্রমে/cache-gate সহ) দিয়ে বসে — কোনো move/
    // delete-এর সময় local ভাবে এটা কখনো ছোঁয়াই হতো না। ফলে প্রশ্ন move হওয়ার পরও
    // সোর্স/ডেস্টিনেশন দুই টপিকেরই দেখানো সংখ্যা পুরনো/ভুল থেকে যেত যতক্ষণ না পরের
    // পর্যায়ক্রমিক reference sync আসত (মিনিট কয়েক লাগতে পারত)। এখন প্রতিটা move-এর
    // সাথে সাথেই dao.countByTopicId() দিয়ে Room-এর প্রশ্ন-টেবিল থেকে আসল/লাইভ কাউন্ট
    // গুনে সরাসরি Topics.rowCount আপডেট করে দেওয়া হয় — GAS sync-এর অপেক্ষা ছাড়াই,
    // Room-ই একমাত্র সোর্স-অফ-ট্রুথ (local ও DB সবসময় সেম থাকে)। ──
    // ── FIX ("Article: 74 প্রশ্ন" দেখাতো Quiz-এ ঢুকলে ভিতরে ২৩টা — মূল কারণ): rowCount
    // মোড-নিরপেক্ষ একটাই generic কলাম ছিল, কিন্তু একই topic_id Quiz/QBank/Study তিন
    // sheet-এই আলাদা প্রশ্ন-সংখ্যা রাখতে পারে। এখন live count যেই sheet-এ move হলো
    // ঠিক তার নিজের per-sheet কলামেই বসে (+ legacy generic কলামও sync রাখা হয়, পুরনো
    // কোনো UI path এখনো সেটা পড়লে অন্তত এই sheet-এর সঠিক সংখ্যাই পাবে)। ──
    private suspend fun refreshTopicRowCount(sheet: String, topicId: String) {
        if (topicId.isBlank()) return
        val live = dao.countByTopicId(sheet.uppercase(), topicId)
        refDao.updateTopicRowCount(topicId, live)   // legacy fallback column
        when (sheet.uppercase()) {
            "QUIZ"  -> refDao.updateTopicRowCountQuiz(topicId, live)
            "QBANK" -> refDao.updateTopicRowCountQbank(topicId, live)
            "STUDY" -> refDao.updateTopicRowCountStudy(topicId, live)
        }
    }

    /** removeRoomQuestionsBySubject()-এর মতোই — নির্দিষ্ট কয়েকটা প্রশ্ন (fbKey list) Room-এ
     *  move করে (subject/subTopic/subjectId/topicId আপডেট, fbKey/id অপরিবর্তিত)। move-এর
     *  পরপরই সোর্স ও ডেস্টিনেশন — দুই Topic-এরই rowCount Room থেকে লাইভ গুনে আপডেট হয়।
     *  oldTopicId ঐচ্ছিক (caller-এর কাছে সবসময় নাও থাকতে পারে) — দিলে সোর্স টপিকের কাউন্টও
     *  সাথে সাথে ঠিক হয়ে যায়, না দিলে শুধু ডেস্টিনেশন টপিকের কাউন্ট আপডেট হবে। */
    suspend fun moveRoomQuestionsByIds(
        sheet: String, ids: List<String>, newSubject: String, newSubTopic: String, newSubjectId: String, newTopicId: String,
        oldTopicId: String? = null
    ) = withContext(Dispatchers.IO) {
        dao.moveQuestionsByIds(sheet.uppercase(), ids, newSubject, newSubTopic, newSubjectId, newTopicId)
        if (!oldTopicId.isNullOrBlank()) refreshTopicRowCount(sheet, oldTopicId)
        refreshTopicRowCount(sheet, newTopicId)
    }

    /** পুরো Topic-এর (topicId মিলিয়ে) সব প্রশ্ন Room-এ move করে। move-এর পর সোর্স Topic-এর
     *  rowCount ০-তে নেমে যায় (তাই লিস্টে আর দেখাবে না) আর ডেস্টিনেশন Topic-এর rowCount
     *  Room থেকে লাইভ গুনে ঠিক হয়ে যায়। */
    suspend fun moveRoomQuestionsByTopic(
        sheet: String, oldTopicId: String, newSubject: String, newSubTopic: String, newSubjectId: String, newTopicId: String
    ) = withContext(Dispatchers.IO) {
        dao.moveQuestionsByTopicId(sheet.uppercase(), oldTopicId, newSubject, newSubTopic, newSubjectId, newTopicId)
        refreshTopicRowCount(sheet, oldTopicId)
        refreshTopicRowCount(sheet, newTopicId)
    }

    /** Room reference-টেবিলে (Topics) Topic-টা reparent করে — mergeTopicId দেওয়া থাকলে
     *  destination-এর existing Topic-এর সাথে merge (সোর্স Topic-রো ডিলিট), নাহলে শুধু
     *  subjectId কলাম বদলে reparent। merge হলে সব প্রশ্ন mergeTopicId-এর আন্ডারে চলে যায়,
     *  তাই merge target-এর rowCount-ও সাথে সাথে লাইভ রিফ্রেশ হয়ে যায় — একটা sheet
     *  প্যারামিটার লাগে (প্রশ্ন কোন Room টেবিলে আছে সেটা বলার জন্য); না দিলে (পুরনো
     *  call site ভাঙবে না বলে ডিফল্ট null) rowCount রিফ্রেশ স্কিপ হবে, পরের পর্যায়ক্রমিক
     *  reference sync-এই ঠিক হবে। */
    suspend fun moveRoomTopicReference(
        topicId: String, newSubjectId: String, mergeTopicId: String?, sheet: String? = null
    ) = withContext(Dispatchers.IO) {
        if (!mergeTopicId.isNullOrBlank()) {
            refDao.mergeTopicCascade(topicId, mergeTopicId)
            if (!sheet.isNullOrBlank()) refreshTopicRowCount(sheet, mergeTopicId)
        } else {
            refDao.reparentTopic(topicId, newSubjectId)
        }
    }

    // ── "নতুন Topic যোগ করে Move" — adminAddQuestion()-এর localId প্যাটার্নের মতোই,
    // এখানে নতুন Topic-এর জন্য। ──

    /** অস্থায়ী লোকাল topicId দিয়ে সাথে সাথে Room reference-এ (Topics টেবিলে) নতুন
     *  Topic-এন্ট্রি যোগ করে — যাতে UI-তে সাথে সাথেই দেখা যায়, ব্যাকগ্রাউন্ডে GAS আসল
     *  id দিলে replaceRoomTopicId() দিয়ে বদলে নিতে হবে। */
    suspend fun addRoomTopicLocal(topicId: String, subjectId: String, name: String) = withContext(Dispatchers.IO) {
        refDao.upsertTopics(listOf(com.hanif.smartstudy.data.local.TopicEntity(
            topicId = topicId, subjectId = subjectId, name = name
        )))
    }

    /** অস্থায়ী লোকাল topicId-কে GAS-এর দেওয়া আসল topicId দিয়ে বদলে দেয় (Topics
     *  reference টেবিল + questions টেবিল দুই জায়গাতেই) — replaceLocalIdAndPersist()-এর
     *  মতোই কনসেপ্ট, শুধু bulk-cache patch এখানে দরকার নেই (Topic নিজে bulk cache-এ
     *  কোনো id হিসেবে সংরক্ষিত থাকে না, শুধু নাম হিসেবে)। */
    suspend fun replaceRoomTopicId(oldTopicId: String, newTopicId: String) = withContext(Dispatchers.IO) {
        refDao.replaceTopicId(oldTopicId, newTopicId)
    }

    suspend fun replaceRoomQuestionsTopicId(sheet: String, oldTopicId: String, newTopicId: String) = withContext(Dispatchers.IO) {
        dao.replaceTopicId(sheet.uppercase(), oldTopicId, newTopicId)
    }

    // ── offline/fail অবস্থায় temp id দিয়ে যোগ করা row, sync সফল হয়ে আসল
    //    Firebase key পেলে সেটা দিয়ে replace করে দেয় (id বদলে যায়, বাকি ফিল্ড অপরিবর্তিত)।
    suspend fun replaceLocalIdAndPersist(sheet: String, oldId: String, newId: String) {
        val base = _memCache ?: cache.loadContent() ?: return
        val gson = com.hanif.smartstudy.data.model.CaseInsensitiveGson.instance

        fun <T : Any> swapId(item: T, idOf: (T) -> String?): T {
            if (idOf(item) != oldId) return item
            val cls = item::class.java
            val obj = gson.toJsonTree(item, cls).asJsonObject
            obj.addProperty("id", newId)
            return gson.fromJson(obj, cls)
        }

        val patched = when (sheet) {
            "Study" -> base.copy(study = base.study.map { swapId(it) { s -> s.id } })
            "Quiz"  -> base.copy(quiz  = base.quiz.map  { swapId(it) { q -> q.id } })
            "QBank" -> base.copy(qbank = base.qbank.map { swapId(it) { q -> q.id } })
            else    -> base
        }

        _memCache = patched
        cache.saveContent(patched)
    }

    /**
     * Admin একটা mode + tag এর subject order সেভ করার পর in-memory + disk cache এ সরাসরি নতুন subject
     * order বসিয়ে দেয় — পুরো content নতুন করে fetch না করেই।
     * mode + tag উভয়ভিত্তিক — শুধু সেই mode+tag এর subject order replace হয়।
     */
    suspend fun patchSubjectOrderAndPersist(mode: String, tag: String, order: Map<String, Int>) {
        val base = _memCache ?: cache.loadContent() ?: return
        val modeMap = (base.subjectOrder[mode] ?: emptyMap()).toMutableMap().apply { put(tag, order) }
        val patched = base.copy(subjectOrder = base.subjectOrder.toMutableMap().apply { put(mode, modeMap) })
        _memCache = patched
        cache.saveContent(patched)
    }

    /**
     * Admin একটা mode + tag + subject এর subTopic order সেভ করার পর in-memory + disk cache এ
     * সরাসরি প্যাচ করে দেয় — শুধু সেই mode+tag+subject এর entry replace হয়, বাকি সব অপরিবর্তিত।
     */
    suspend fun patchSubTopicOrderAndPersist(mode: String, tag: String, subject: String, order: Map<String, Int>) {
        val base = _memCache ?: cache.loadContent() ?: return
        val tagMap    = (base.subTopicOrder[mode] ?: emptyMap()).toMutableMap()
        val subjMap   = (tagMap[tag] ?: emptyMap()).toMutableMap().apply { put(subject, order) }
        tagMap[tag]   = subjMap
        val patched   = base.copy(subTopicOrder = base.subTopicOrder.toMutableMap().apply { put(mode, tagMap) })
        _memCache = patched
        cache.saveContent(patched)
    }

    fun getPendingQueueCount() = kotlinx.coroutines.flow.flow { emit(queue.count()) }

    fun isOnline(): Boolean {
        // ইউজার ম্যানুয়ালি "অফলাইন মোড" অন করলে — নেট থাকলেও Firebase-কে
        // "অনলাইন নেই" হিসেবে ট্রিট করা হয়, ফলে সব read/write লোকাল
        // cache/queue দিয়েই সার্ভ হয় (bandwidth/quota একদম বাঁচে)।
        if (session.isOfflineMode()) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

sealed class DataState<out T> {
    data class Success<T>(
        val data      : T,
        val fromCache : Boolean = false,
        val isOffline : Boolean = false
    ) : DataState<T>()
    data class Error(val message: String) : DataState<Nothing>()
    object Loading : DataState<Nothing>()
}
