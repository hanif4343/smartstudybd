package com.hanif.smartstudy.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.local.PendingQueue
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.toEntity
import com.hanif.smartstudy.data.local.toQuestionItem
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

    // ── In-memory cache — একবার fetch হলে সব VM শেয়ার করে ──
    companion object {
        @Volatile private var _memCache: AppContent? = null
        @Volatile private var _lastBgRefreshAt: Long = 0L
        private val mutex = Mutex()
        private const val BG_REFRESH_MIN_GAP_MS = 60_000L // একই সেশনে বারবার getContent() কল হলেও ৬০ সেকেন্ডে একবারের বেশি Firebase hit না করার জন্য
        fun getMemCache(): AppContent? = _memCache
        fun clearMemCache() { _memCache = null }
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
        return when (val result = ContentFetchService.fetchSubjectsOnly()) {
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

    // ── Stale-While-Revalidate ─────────────────────────────────────────────────
    // Cache থাকলে সাথে সাথে return, background এ Firebase থেকে fresh data আনো।
    // Callback দিয়ে নতুন data এলে ViewModel update করতে পারবে।
    suspend fun getContent(
        forceRefresh: Boolean = false,
        onBackgroundUpdate: ((AppContent) -> Unit)? = null
    ): DataState<AppContent> {

        // ── Step 1: Cache থেকে instant data ──────────────────────────────
        val cached = _memCache?.takeIf { !it.isEmpty() }
            ?: cache.loadContent()?.takeIf { !it.isEmpty() }

        if (cached != null && !forceRefresh) {
            _memCache = cached
            Log.d("Repo", "Cache hit: quiz=${cached.quiz.size} — background refresh শুরু")

            // ── Step 2: Background এ Firebase check ───────────────────────
            // আগে এটা শুধু onBackgroundUpdate callback দিলেই চলত — ফলে Quiz/QBank/Study/
            // Routine/Menu থেকে getContent() কল হলে (কোনো callback ছাড়াই) কখনো refresh-ই
            // হতো না, শুধু Home screen খুললেই হতো। তাই admin কোনো প্রশ্নে explanation
            // যোগ/এডিট করলে সেটা student app-এ আসতে Home screen খোলা লাগত। এখন
            // অনলাইন থাকলেই সবসময় background refresh হবে, callback থাকুক বা না থাকুক।
            val now = System.currentTimeMillis()
            if (isOnline() && (onBackgroundUpdate != null || now - _lastBgRefreshAt > BG_REFRESH_MIN_GAP_MS)) {
                _lastBgRefreshAt = now
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        when (val fresh = ContentFetchService.fetchAllContent()) {
                            is ContentResult.Success -> {
                                if (fresh.data.fetchedAt > cached.fetchedAt || fresh.data.quiz.size != cached.quiz.size) {
                                    Log.d("Repo", "Background update: new data found, notifying UI")
                                    _memCache = fresh.data
                                    cache.saveContent(fresh.data)
                                    syncToRoom(fresh.data)
                                    onBackgroundUpdate?.invoke(fresh.data)
                                } else {
                                    Log.d("Repo", "Background: data same, no update needed")
                                }
                            }
                            is ContentResult.Error -> Log.w("Repo", "Background refresh failed: ${fresh.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("Repo", "Background refresh error: ${e.message}")
                    }
                }
            }

            return DataState.Success(cached, fromCache = true)
        }

        // ── Step 3: Cache নেই — Offline check ────────────────────────────
        if (!isOnline()) {
            return DataState.Error("ইন্টারনেট সংযোগ নেই এবং কোনো cache নেই")
        }

        // ── Step 4: প্রথমবার — Firebase থেকে fetch (mutex দিয়ে একবারই) ──
        return mutex.withLock {
            // Double check — অন্য coroutine এর মধ্যে fetch হয়ে গেছে কিনা
            _memCache?.takeIf { !it.isEmpty() }?.let {
                return@withLock DataState.Success(it, fromCache = true)
            }

            Log.d("Repo", "First load: Firebase থেকে fetch করছি...")
            when (val result = ContentFetchService.fetchAllContent()) {
                is ContentResult.Success -> {
                    Log.d("Repo", "Firebase OK: quiz=${result.data.quiz.size} study=${result.data.study.size} qbank=${result.data.qbank.size}")
                    _memCache = result.data
                    cache.saveContent(result.data)
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        syncToRoom(result.data)
                    }
                    DataState.Success(result.data)
                }
                is ContentResult.Error -> {
                    Log.e("Repo", "Firebase error: ${result.message}")
                    DataState.Error(result.message)
                }
            }
        }
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

    /**
     * Admin subject order সেভ করার পর in-memory + disk cache দুটোতেই সরাসরি নতুন
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
