package com.hanif.smartstudy.vision

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Mission counts for one date, stored in SharedPreferences as a JSON object
// (not an array), so the shape stays readable and forward-compatible.
internal data class VisionMissionCounts(
    val newCount: Int = 0,
    val revisionCount: Int = 0,
    val mockCount: Int = 0
)

class VisionViewModel : ViewModel() {

    var loaded by mutableStateOf(false)
        private set

    var profile by mutableStateOf<VisionProfile?>(null)
        private set

    var lastMock by mutableStateOf<VisionMockAttempt?>(null)
        private set

    private val gson = Gson()
    private var prefs: android.content.SharedPreferences? = null
    private var initialized = false

    private var scores = mutableMapOf<String, VisionTopicScore>()
    private var streaks = mutableMapOf<String, Int>()
    private var dueDates = mutableMapOf<String, String>()
    private var activeDates = mutableSetOf<String>()
    private var missions = mutableMapOf<String, VisionMissionCounts>()
    private var mockHistoryList = mutableListOf<VisionMockAttempt>()
    private var writtenChecksList = mutableListOf<VisionWrittenCheck>()

    // ---------- Dates (yyyy-MM-dd, default locale) ----------

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }

    private fun todayString(): String {
        return formatDate(Date())
    }

    private fun addDays(dateString: String, days: Int): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
                ?: return dateString
            val calendar = Calendar.getInstance()
            calendar.time = parsed
            calendar.add(Calendar.DAY_OF_YEAR, days)
            formatDate(calendar.time)
        } catch (e: Exception) {
            dateString
        }
    }

    // ---------- Persistence ----------

    private fun persist() {
        val editor = prefs?.edit() ?: return
        editor.putString("profile", gson.toJson(profile))
        editor.putString("scores", gson.toJson(scores))
        editor.putString("streaks", gson.toJson(streaks))
        editor.putString("due", gson.toJson(dueDates))
        editor.putString("activeDates", gson.toJson(activeDates))
        editor.putString("missions", gson.toJson(missions))
        editor.putString("mockHistory", gson.toJson(mockHistoryList))
        editor.putString("writtenChecks", gson.toJson(writtenChecksList))
        editor.putString("lastMock", gson.toJson(lastMock))
        editor.apply()
    }

    fun init(context: android.content.Context) {
        // Idempotent: a second call must never wipe already-loaded state.
        if (initialized) {
            return
        }
        initialized = true
        prefs = context.getSharedPreferences("vision_prefs", android.content.Context.MODE_PRIVATE)

        val stored = prefs ?: run {
            loaded = true
            return
        }

        try {
            stored.getString("profile", null)?.takeIf { it.isNotBlank() }?.let {
                profile = gson.fromJson(it, VisionProfile::class.java)
            }
        } catch (e: Exception) {
            profile = null
        }

        try {
            stored.getString("scores", null)?.takeIf { it.isNotBlank() }?.let {
                val type = object : TypeToken<Map<String, VisionTopicScore>>() {}.type
                val loadedScores: Map<String, VisionTopicScore>? = gson.fromJson(it, type)
                if (loadedScores != null) {
                    scores = loadedScores.toMutableMap()
                }
            }
        } catch (e: Exception) {
            scores = mutableMapOf()
        }

        try {
            stored.getString("streaks", null)?.takeIf { it.isNotBlank() }?.let {
                val type = object : TypeToken<Map<String, Int>>() {}.type
                val loadedStreaks: Map<String, Int>? = gson.fromJson(it, type)
                if (loadedStreaks != null) {
                    streaks = loadedStreaks.toMutableMap()
                }
            }
        } catch (e: Exception) {
            streaks = mutableMapOf()
        }

        try {
            stored.getString("due", null)?.takeIf { it.isNotBlank() }?.let {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val loadedDue: Map<String, String>? = gson.fromJson(it, type)
                if (loadedDue != null) {
                    dueDates = loadedDue.toMutableMap()
                }
            }
        } catch (e: Exception) {
            dueDates = mutableMapOf()
        }

        try {
            stored.getString("activeDates", null)?.takeIf { it.isNotBlank() }?.let {
                val type = object : TypeToken<Set<String>>() {}.type
                val loadedDates: Set<String>? = gson.fromJson(it, type)
                if (loadedDates != null) {
                    activeDates = loadedDates.toMutableSet()
                }
            }
        } catch (e: Exception) {
            activeDates = mutableSetOf()
        }

        try {
            stored.getString("missions", null)?.takeIf { it.isNotBlank() }?.let {
                val type = object : TypeToken<Map<String, VisionMissionCounts>>() {}.type
                val loadedMissions: Map<String, VisionMissionCounts>? = gson.fromJson(it, type)
                if (loadedMissions != null) {
                    missions = loadedMissions.toMutableMap()
                }
            }
        } catch (e: Exception) {
            missions = mutableMapOf()
        }

        try {
            stored.getString("mockHistory", null)?.takeIf { it.isNotBlank() }?.let {
                val type = object : TypeToken<List<VisionMockAttempt>>() {}.type
                val loadedHistory: List<VisionMockAttempt>? = gson.fromJson(it, type)
                if (loadedHistory != null) {
                    mockHistoryList = loadedHistory.toMutableList()
                }
            }
        } catch (e: Exception) {
            mockHistoryList = mutableListOf()
        }

        try {
            stored.getString("writtenChecks", null)?.takeIf { it.isNotBlank() }?.let {
                val type = object : TypeToken<List<VisionWrittenCheck>>() {}.type
                val loadedChecks: List<VisionWrittenCheck>? = gson.fromJson(it, type)
                if (loadedChecks != null) {
                    writtenChecksList = loadedChecks.toMutableList()
                }
            }
        } catch (e: Exception) {
            writtenChecksList = mutableListOf()
        }

        try {
            stored.getString("lastMock", null)?.takeIf { it.isNotBlank() }?.let {
                lastMock = gson.fromJson(it, VisionMockAttempt::class.java)
            }
        } catch (e: Exception) {
            lastMock = null
        }

        loaded = true
    }

    fun saveProfile(p: VisionProfile) {
        profile = p
        persist()
    }

    // ---------- Topic mastery ----------

    fun mastery(topicId: String): Float {
        val score = scores[topicId] ?: return 0f
        if (score.total <= 0) {
            return 0f
        }
        return score.correct.toFloat() / score.total.toFloat()
    }

    fun attempted(topicId: String): Boolean {
        return (scores[topicId]?.total ?: 0) > 0
    }

    // ---------- Practice + spaced repetition ----------

    fun recordPracticeAnswer(q: VisionMcq, correct: Boolean, confident: Boolean) {
        val previous = scores[q.topicId] ?: VisionTopicScore(q.topicId, 0, 0)
        scores[q.topicId] = VisionTopicScore(
            q.topicId,
            previous.correct + if (correct) 1 else 0,
            previous.total + 1
        )

        val today = todayString()
        if (correct) {
            val newStreak = (streaks[q.id] ?: 0) + 1
            streaks[q.id] = newStreak
            val intervalDays = when (newStreak) {
                1 -> 1
                2 -> 3
                3 -> 7
                else -> 14
            }
            dueDates[q.id] = addDays(today, intervalDays)
        } else {
            streaks[q.id] = 0
            dueDates[q.id] = addDays(today, 1)
        }
        persist()
    }

    private fun isNeverAttempted(q: VisionMcq): Boolean {
        return !streaks.containsKey(q.id) && !dueDates.containsKey(q.id)
    }

    fun revisionQueue(): List<VisionMcq> {
        val today = todayString()
        return VisionData.mcqs
            .filter { q ->
                val due = dueDates[q.id] ?: return@filter false
                due <= today
            }
            .sortedWith(
                compareBy<VisionMcq> { mastery(it.topicId) }
                    .thenBy { it.id }
            )
            .take(10)
    }

    fun newQueue(topicId: String?): List<VisionMcq> {
        val pool = if (topicId == null) {
            VisionData.mcqs
        } else {
            VisionData.mcqs.filter { it.topicId == topicId }
        }
        if (topicId == null) {
            // Never-attempted first, then lowest-mastery topics.
            val neverAttempted = pool.filter { isNeverAttempted(it) }
                .sortedWith(
                    compareBy<VisionMcq> { mastery(it.topicId) }
                        .thenBy { it.id }
                )
            val attemptedPool = pool.filter { !isNeverAttempted(it) }
                .sortedWith(
                    compareBy<VisionMcq> { mastery(it.topicId) }
                        .thenBy { it.id }
                )
            return (neverAttempted + attemptedPool).take(10)
        } else {
            val neverAttempted = pool.filter { isNeverAttempted(it) }
            val rest = pool.filter { !isNeverAttempted(it) }
            return (neverAttempted + rest).take(10)
        }
    }

    // ---------- Daily mission ----------

    fun todayMission(): Triple<Int, Int, Int> {
        val counts = missions[todayString()] ?: VisionMissionCounts()
        val mockDone = if (counts.mockCount > 0) 1 else 0
        return Triple(counts.newCount, counts.revisionCount, mockDone)
    }

    private fun incrementMission(newDelta: Int, revisionDelta: Int, mockDelta: Int) {
        val today = todayString()
        val previous = missions[today] ?: VisionMissionCounts()
        missions[today] = VisionMissionCounts(
            newCount = previous.newCount + newDelta,
            revisionCount = previous.revisionCount + revisionDelta,
            mockCount = previous.mockCount + mockDelta
        )
        persist()
    }

    fun markMissionNew() {
        incrementMission(newDelta = 1, revisionDelta = 0, mockDelta = 0)
    }

    fun markMissionRevision() {
        incrementMission(newDelta = 0, revisionDelta = 1, mockDelta = 0)
    }

    // ---------- Mock tests ----------

    fun startMock(): List<VisionMcq> {
        val seed = todayString().hashCode().toLong()
        // Deterministic shuffle for the day, then weakest topics first.
        // The sort is stable, so questions sharing a mastery value keep
        // their shuffled (but day-stable) order.
        val shuffled = VisionData.mcqs.shuffled(Random(seed))
        return shuffled
            .sortedBy { mastery(it.topicId) }
            .take(25)
    }

    fun submitMock(
        questions: List<VisionMcq>,
        answers: Map<String, Int>,
        guessed: Set<String>,
        secondsPerQ: Map<String, Int>
    ): VisionMockAttempt {
        var correctCount = 0
        val perTopicCorrect = mutableMapOf<String, Int>()
        val perTopicTotal = mutableMapOf<String, Int>()
        for (q in questions) {
            perTopicTotal[q.topicId] = (perTopicTotal[q.topicId] ?: 0) + 1
            if (answers[q.id] == q.answerIndex) {
                correctCount++
                perTopicCorrect[q.topicId] = (perTopicCorrect[q.topicId] ?: 0) + 1
            }
        }
        val perTopic = perTopicTotal.keys.sorted().map { topicId ->
            VisionTopicScore(
                topicId,
                perTopicCorrect[topicId] ?: 0,
                perTopicTotal[topicId] ?: 0
            )
        }
        val questionIds = questions.map { it.id }.toSet()
        val guessedCount = guessed.intersect(questionIds).size
        val seconds = questions.mapNotNull { secondsPerQ[it.id] }
        val avgSeconds = if (seconds.isEmpty()) 0 else seconds.sum() / seconds.size

        val attempt = VisionMockAttempt(
            dateMs = System.currentTimeMillis(),
            total = questions.size,
            correct = correctCount,
            perTopic = perTopic,
            guessedCount = guessedCount,
            avgSeconds = avgSeconds
        )
        mockHistoryList.add(attempt)
        lastMock = attempt
        incrementMission(newDelta = 0, revisionDelta = 0, mockDelta = 1)
        persist()
        return attempt
    }

    fun mockHistory(): List<VisionMockAttempt> {
        return mockHistoryList.toList()
    }

    // ---------- Weak topics / readiness / streak ----------

    fun weakTopics(): List<VisionTopic> {
        return scores.values
            .filter { it.total > 0 && mastery(it.topicId) < 0.6f }
            .sortedBy { mastery(it.topicId) }
            .mapNotNull { score -> VisionData.topics.find { it.id == score.topicId } }
    }

    fun readiness(): Float {
        val attemptedScores = scores.values.filter { it.total > 0 }
        if (attemptedScores.isEmpty()) {
            return 0f
        }
        return attemptedScores.map { mastery(it.topicId) }.average().toFloat()
    }

    fun streakDays(): Int {
        var count = 0
        var date = todayString()
        while (count < 10000 && activeDates.contains(date)) {
            count++
            val previous = addDays(date, -1)
            if (previous == date) {
                break
            }
            date = previous
        }
        return count
    }

    fun markDayActive() {
        if (activeDates.add(todayString())) {
            persist()
        }
    }

    // ---------- Written checks ----------

    fun saveWrittenCheck(c: VisionWrittenCheck) {
        writtenChecksList.add(c)
        persist()
    }

    fun writtenChecks(): List<VisionWrittenCheck> {
        return writtenChecksList.toList()
    }

    // ---------- Reset ----------

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        scores = mutableMapOf()
        streaks = mutableMapOf()
        dueDates = mutableMapOf()
        activeDates = mutableSetOf()
        missions = mutableMapOf()
        mockHistoryList = mutableListOf()
        writtenChecksList = mutableListOf()
        profile = null
        lastMock = null
        loaded = true
    }
}
