package com.hanif.smartstudy.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.local.PendingQueue
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class ContentRepository(private val context: Context) {

    private val cache   = ContentCache(context)
    private val queue   = PendingQueue(context)
    private val session = SessionManager(context)

    // ── In-memory cache — একবার fetch হলে সব VM শেয়ার করে ──
    companion object {
        @Volatile private var _memCache: AppContent? = null
        private val mutex = Mutex()
        fun getMemCache(): AppContent? = _memCache
        fun clearMemCache() { _memCache = null }
    }

    suspend fun getContent(forceRefresh: Boolean = false): DataState<AppContent> {
        // In-memory cache hit — সবচেয়ে fast
        if (!forceRefresh) {
            _memCache?.let { mem ->
                if (!mem.isEmpty() && !mem.isStale()) {
                    Log.d("Repo", "Memory cache hit: quiz=${mem.quiz.size}")
                    return DataState.Success(mem, fromCache = true)
                }
            }
        }

        // mutex দিয়ে protect — concurrent calls এ একবারই fetch হবে
        return mutex.withLock {
            // Double-check after lock
            if (!forceRefresh) {
                _memCache?.let { mem ->
                    if (!mem.isEmpty() && !mem.isStale()) {
                        return@withLock DataState.Success(mem, fromCache = true)
                    }
                }
            }

            // DataStore cache check
            if (!forceRefresh) {
                val cached = cache.loadContent()
                if (cached != null && !cached.isEmpty() && !cached.isStale()) {
                    _memCache = cached
                    Log.d("Repo", "DataStore cache hit: quiz=${cached.quiz.size}")
                    return@withLock DataState.Success(cached, fromCache = true)
                }
            }

            // Offline হলে যা আছে দাও
            if (!isOnline()) {
                val cached = cache.loadContent() ?: _memCache
                return@withLock if (cached != null && !cached.isEmpty()) {
                    _memCache = cached
                    DataState.Success(cached, fromCache = true, isOffline = true)
                } else {
                    DataState.Error("ইন্টারনেট সংযোগ নেই")
                }
            }

            // Network fetch
            Log.d("Repo", "Fetching from Firebase...")
            when (val result = ContentFetchService.fetchAllContent()) {
                is ContentResult.Success -> {
                    Log.d("Repo", "Firebase OK: quiz=${result.data.quiz.size} study=${result.data.study.size} qbank=${result.data.qbank.size}")
                    _memCache = result.data
                    cache.saveContent(result.data)
                    DataState.Success(result.data)
                }
                is ContentResult.Error -> {
                    Log.e("Repo", "Firebase error: ${result.message}")
                    val stale = cache.loadContent() ?: _memCache
                    if (stale != null && !stale.isEmpty()) {
                        _memCache = stale
                        DataState.Success(stale, fromCache = true)
                    } else {
                        DataState.Error(result.message)
                    }
                }
            }
        }
    }

    fun getXpInfo(): XpInfo {
        val user = session.getCurrentUser()
        return XpInfo.fromXp(user?.xp ?: 0)
    }

    // ── XP award করা — Firebase Users/{phone}/XP ফিল্ডে atomic transaction দিয়ে
    //    বাড়ায় (race condition-safe), আর সাথে সাথে local session-এর cached XP-ও
    //    আপডেট করে দেয় যাতে Home screen তৎক্ষণাৎ নতুন XP দেখায়, পরের login এর
    //    জন্য অপেক্ষা করতে না হয়।
    //
    //    [মূল কারণ — আগে XP সবসময় ০ দেখাতো]: quiz এ সঠিক উত্তর দিলে কোনো XP-ই
    //    award হতো না — পুরো pipeline (PendingQueue.enqueueXpUpdate ইত্যাদি)
    //    কোডে ছিল কিন্তু কোথাও call হতো না। এই ফাংশনটাই সেই gap পূরণ করে।
    private val xpDb: FirebaseDatabase by lazy {
        try {
            val url = BuildConfig.FIREBASE_URL
            if (url.isNullOrBlank() || url.contains("%%") || !url.startsWith("https://")) {
                FirebaseDatabase.getInstance()
            } else {
                FirebaseDatabase.getInstance(url)
            }
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    suspend fun awardXp(phone: String, delta: Int) {
        if (delta == 0 || phone.isBlank()) return

        // - Local cache সাথে সাথে আপডেট — UI তে তাৎক্ষণিক প্রতিফলন
        try {
            val current = session.getCurrentUser()
            if (current != null) {
                session.saveUser(current.copy(xp = maxOf(0, current.xp + delta)))
            }
        } catch (e: Exception) {
            Log.e("ContentRepository", "awardXp local update failed: ${e.message}")
        }

        // - Firebase এ আসল মান — atomic transaction (একসাথে অনেক answer দিলেও XP হারিয়ে যাবে না)
        try {
            val usersRef = xpDb.getReference("Users")
            val snap = usersRef.orderByChild("Phone").equalTo(phone).get().await()
            val userRef = snap.children.firstOrNull()?.ref ?: run {
                Log.w("ContentRepository", "awardXp: user not found for $phone")
                return
            }
            userRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(d: MutableData): Transaction.Result {
                    val cur = d.child("XP").getValue(Int::class.java) ?: 0
                    d.child("XP").value = maxOf(0, cur + delta)
                    return Transaction.success(d)
                }
                override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {
                    if (e != null) Log.e("ContentRepository", "awardXp transaction failed: ${e.message}")
                }
            })
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
