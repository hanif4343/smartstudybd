package com.hanif.smartstudy.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.flow.first

class ContentRepository(private val context: Context) {

    private val cache   = ContentCache(context)
    private val queue   = PendingQueue(context)
    private val session = SessionManager(context)

    // ── Content: cache-first, then network ──
    suspend fun getContent(forceRefresh: Boolean = false): DataState<AppContent> {
        // Cache check
        if (!forceRefresh) {
            val cached = cache.loadContent()
            if (cached != null && !cached.isEmpty() && !cached.isStale()) {
                return DataState.Success(cached, fromCache = true)
            }
        }

        // Offline হলে cache থেকে দাও
        if (!isOnline()) {
            val cached = cache.loadContent()
            return if (cached != null && !cached.isEmpty()) {
                DataState.Success(cached, fromCache = true, isOffline = true)
            } else {
                DataState.Error("ইন্টারনেট সংযোগ নেই এবং কোনো cache নেই")
            }
        }

        // Network fetch
        return when (val result = ContentFetchService.fetchAllContent()) {
            is ContentResult.Success -> {
                cache.saveContent(result.data)
                DataState.Success(result.data)
            }
            is ContentResult.Error -> {
                // Fallback to stale cache
                val stale = cache.loadContent()
                if (stale != null && !stale.isEmpty()) {
                    DataState.Success(stale, fromCache = true, isOffline = false)
                } else {
                    DataState.Error(result.message)
                }
            }
        }
    }

    // ── XP Info ──
    fun getXpInfo(): XpInfo {
        val user = session.getCurrentUser()
        return XpInfo.fromXp(user?.xp ?: 0)
    }

    // ── Streak ──
    suspend fun getStreakInfo(): StreakInfo {
        val streakDays = cache.getStreak()
        val daysBn     = listOf("শনি","রবি","সোম","মঙ্গল","বুধ","বৃহস্পতি","শুক্র")

        // JS style: 0=Sun, 1=Mon... আমাদের শনি থেকে শুরু
        val todayJS     = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0-6
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

    // ── Mark today's activity (streak update) ──
    suspend fun markTodayActivity() {
        cache.updateStreak()
    }

    // ── Goal Progress ──
    suspend fun getGoalProgress(): GoalProgress {
        val goalMin = session.getDailyGoal()
        val doneMin = cache.getTodayStudyMinutes()
        val pct     = if (goalMin > 0) minOf(100f, doneMin * 100f / goalMin) else 0f
        return GoalProgress(goalMin, doneMin, pct)
    }

    // ── Study Stats ──
    suspend fun getStudyStats(): StudyStats {
        val (today, week, total) = cache.getStudyStats()
        val correct = cache.getCorrectCount()
        val wrong   = cache.getWrongCount()
        val total2  = correct + wrong
        val acc     = if (total2 > 0) (correct * 100) / total2 else 0
        return StudyStats(today, week, total, correct, wrong, acc)
    }

    // ── Exam Countdown ──
    fun getExamCountdown(): ExamCountdown {
        val prefs = android.content.Context::class.java
        // SessionManager থেকে exam date পড়ো
        return try {
            val sharedPrefs = context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)
            val examDateStr = sharedPrefs.getString("exam_date", null) ?: return ExamCountdown()
            val examName    = sharedPrefs.getString("exam_name", "পরীক্ষা") ?: "পরীক্ষা"
            val sdf         = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val examDate    = sdf.parse(examDateStr) ?: return ExamCountdown()
            val diff        = examDate.time - System.currentTimeMillis()
            if (diff <= 0) return ExamCountdown(examName, 0, 0, 0, 0, true)
            val days    = diff / (1000 * 60 * 60 * 24)
            val hours   = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
            val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (diff % (1000 * 60)) / 1000
            ExamCountdown(examName, days, hours, minutes, seconds, true)
        } catch (e: Exception) { ExamCountdown() }
    }

    // ── Save Exam Date ──
    fun saveExamDate(date: String, name: String) {
        context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE).edit()
            .putString("exam_date", date)
            .putString("exam_name", name)
            .apply()
    }

    // ── Offline action queue ──
    suspend fun submitQuizAnswer(questionId: String, isCorrect: Boolean) {
        val phone = session.getCurrentUser()?.phone ?: return
        if (isCorrect) cache.incrementCorrect() else cache.incrementWrong()
        if (isOnline()) {
            // Direct sync — WorkManager এর মাধ্যমে
            SyncWorker.scheduleOneTime(context)
        } else {
            queue.enqueueQuizAnswer(questionId, isCorrect, phone)
        }
    }

    suspend fun submitStudyProgress(minutes: Int, topic: String) {
        val phone = session.getCurrentUser()?.phone ?: return
        cache.addStudyMinutes(minutes)
        markTodayActivity()
        if (!isOnline()) {
            queue.enqueueStudyProgress(phone, minutes, topic)
        }
        SyncWorker.scheduleOneTime(context)
    }

    fun getPendingQueueCount(): kotlinx.coroutines.flow.Flow<Int> =
        kotlinx.coroutines.flow.flow { emit(queue.count()) }

    // ── Network check ──
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// ── DataState wrapper ──
sealed class DataState<out T> {
    data class Success<T>(
        val data      : T,
        val fromCache : Boolean = false,
        val isOffline : Boolean = false
    ) : DataState<T>()
    data class Error(val message: String) : DataState<Nothing>()
    object Loading : DataState<Nothing>()
}
