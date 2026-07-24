package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.hanif.smartstudy.data.model.CaseInsensitiveGson
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first

class ContentCache(private val context: Context) {

    // CaseInsensitiveGson — Firebase field names (correct, explanation, Question Type) ঠিকমতো handle করে
    private val gson = CaseInsensitiveGson.instance

    companion object {
        // version bump করলে পুরানো cache auto-invalidate হয়
        private const val CACHE_VERSION = 5  // Firebase path key as id fix — পুরনো numeric id cache clear করো
        val KEY_CACHE_VERSION = intPreferencesKey("cache_version")
        val KEY_STUDY_JSON    = stringPreferencesKey("cache_study_json")
        val KEY_QUIZ_JSON     = stringPreferencesKey("cache_quiz_json")
        val KEY_QBANK_JSON    = stringPreferencesKey("cache_qbank_json")
        // Model Test — subject অনুযায়ী ফিক্সড টেস্টগুলো ফোনের লোকাল স্টোরেজে থাকে,
        // যাতে বারবার Firebase থেকে আনতে না হয় (offline-এও Model Test list দেখা যায়)
        val KEY_MODELTESTS_JSON = stringPreferencesKey("cache_modeltests_json")
        val KEY_CACHE_TIME    = longPreferencesKey("cache_fetched_at")
        // Firebase "/meta/updatedAt" থেকে আসা সার্ভার টাইমস্ট্যাম্প — লাস্ট সেভ করা কনটেন্টের সাথে মেলানো হয়
        val KEY_REMOTE_UPDATED_AT = longPreferencesKey("cache_remote_updated_at")

        // ── Delta/incremental sync per-sheet checkpoint ──
        // প্রতিটা sheet-এ সর্বশেষ কবে পর্যন্ত (device epoch millis) sync করা হয়েছে — পরের বার
        // শুধু এর চেয়ে নতুন (updatedAt বেশি) row গুলোই আনা হবে, পুরো sheet না।
        val KEY_QUIZ_LAST_SYNC   = longPreferencesKey("quiz_last_sync")
        val KEY_QBANK_LAST_SYNC  = longPreferencesKey("qbank_last_sync")
        val KEY_STUDY_LAST_SYNC  = longPreferencesKey("study_last_sync")
        // সব sheet সবশেষ কবে "পুরোপুরি" (full) sync হয়েছিল — deletion/edge-case reconcile
        // করার জন্য মাঝে মাঝে (নিচে FULL_RESYNC_INTERVAL_MS) পুরো refetch করা হবে,
        // কারণ delta sync শুধু edit/add ধরে, কেউ প্রশ্ন মুছে ফেললে সেটা delta তে বোঝা যায় না।
        val KEY_LAST_FULL_SYNC   = longPreferencesKey("last_full_sync")
        // clock-skew buffer — admin আর syncing ডিভাইসের ঘড়িতে সামান্য পার্থক্য থাকতে পারে,
        // তাই delta query-তে since থেকে এই বাফারটা বিয়োগ করে একটু আগে থেকে চাওয়া হয়,
        // যাতে সীমানার কাছাকাছি সময়ের কোনো edit বাদ না পড়ে যায়।
        const val CLOCK_SKEW_BUFFER_MS = 3 * 60_000L
        // প্রতি ৩ দিনে একবার পুরো sheet পুনরায় refetch (safety net) — deletion ধরার জন্য
        const val FULL_RESYNC_INTERVAL_MS = 3 * 24 * 60 * 60_000L

        val KEY_TODAY_STUDY   = intPreferencesKey("today_study_min")
        val KEY_WEEK_STUDY    = intPreferencesKey("week_study_min")
        val KEY_TOTAL_STUDY   = intPreferencesKey("total_study_min")
        val KEY_CORRECT_COUNT = intPreferencesKey("correct_count")
        val KEY_WRONG_COUNT   = intPreferencesKey("wrong_count")
        val KEY_STREAK        = intPreferencesKey("streak_days")
        val KEY_STREAK_DATE   = stringPreferencesKey("streak_last_date")
        val KEY_STUDY_DATE    = stringPreferencesKey("today_study_date")
    }

    suspend fun saveContent(content: AppContent) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHE_VERSION] = CACHE_VERSION
            prefs[KEY_STUDY_JSON] = gson.toJson(content.study)
            prefs[KEY_QUIZ_JSON]  = gson.toJson(content.quiz)
            prefs[KEY_QBANK_JSON] = gson.toJson(content.qbank)
            prefs[KEY_MODELTESTS_JSON] = gson.toJson(content.modelTests)
            prefs[KEY_CACHE_TIME] = content.fetchedAt
            prefs[KEY_REMOTE_UPDATED_AT] = content.remoteUpdatedAt
        }
    }

    // ── Delta sync checkpoints — getter/setter ──────────────────────────────
    suspend fun getQuizLastSync(): Long  = context.dataStore.data.first()[KEY_QUIZ_LAST_SYNC]  ?: 0L
    suspend fun getQBankLastSync(): Long = context.dataStore.data.first()[KEY_QBANK_LAST_SYNC] ?: 0L
    suspend fun getStudyLastSync(): Long = context.dataStore.data.first()[KEY_STUDY_LAST_SYNC] ?: 0L
    suspend fun getLastFullSync(): Long  = context.dataStore.data.first()[KEY_LAST_FULL_SYNC]  ?: 0L
    // ── Google Sheet মোডে ব্যাকগ্রাউন্ড refresh চেক-এর জন্য হালকা getter — পুরো
    // cached content (হাজার হাজার row) deserialize না করেই শুধু এই একটা ছোট
    // timestamp key পড়া যায় (দেখো ContentFetchService.fetchMetaUpdatedAt) ──
    suspend fun getRemoteUpdatedAt(): Long = context.dataStore.data.first()[KEY_REMOTE_UPDATED_AT] ?: 0L

    /** delta sync সফল হওয়ার পর প্রতিটা sheet এর checkpoint আলাদাভাবে আপডেট করো */
    suspend fun setSyncCheckpoints(quizAt: Long, qbankAt: Long, studyAt: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUIZ_LAST_SYNC]  = quizAt
            prefs[KEY_QBANK_LAST_SYNC] = qbankAt
            prefs[KEY_STUDY_LAST_SYNC] = studyAt
        }
    }

    /** পুরো (full) fetchAllContent() সফল হলে — সব checkpoint একসাথে "এখন" এ সেট করো */
    suspend fun markFullSyncDone(at: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUIZ_LAST_SYNC]  = at
            prefs[KEY_QBANK_LAST_SYNC] = at
            prefs[KEY_STUDY_LAST_SYNC] = at
            prefs[KEY_LAST_FULL_SYNC]  = at
        }
    }

    // FIX: version mismatch হলে cache invalid — নতুন data fetch হবে
    suspend fun loadContent(): AppContent? {
        return try {
            val prefs = context.dataStore.data.first()
            val fetchedAt = prefs[KEY_CACHE_TIME] ?: 0L
            val savedVersion = prefs[KEY_CACHE_VERSION] ?: 0

            // কোনো cache নেই বা পুরানো version
            if (fetchedAt == 0L || savedVersion < CACHE_VERSION) return null

            val studyJson  = prefs[KEY_STUDY_JSON] ?: "[]"
            val quizJson   = prefs[KEY_QUIZ_JSON]  ?: "[]"
            val qbankJson  = prefs[KEY_QBANK_JSON] ?: "[]"
            val modelTestsJson = prefs[KEY_MODELTESTS_JSON] ?: "{}"

            val studyType = object : com.google.gson.reflect.TypeToken<List<com.hanif.smartstudy.data.model.StudyItem?>>() {}.type
            val quizType  = object : com.google.gson.reflect.TypeToken<List<com.hanif.smartstudy.data.model.QuizItem?>>() {}.type
            val qbankType = object : com.google.gson.reflect.TypeToken<List<com.hanif.smartstudy.data.model.QBankItem?>>() {}.type
            val modelTestsType = object : com.google.gson.reflect.TypeToken<Map<String, List<com.hanif.smartstudy.data.model.ModelTestMeta>>>() {}.type

            val modelTests: Map<String, List<com.hanif.smartstudy.data.model.ModelTestMeta>> = try {
                gson.fromJson(modelTestsJson, modelTestsType) ?: emptyMap()
            } catch (e: Exception) { emptyMap() }

            AppContent(
                study     = (gson.fromJson<List<com.hanif.smartstudy.data.model.StudyItem?>>(studyJson, studyType) ?: emptyList()).filterNotNull(),
                quiz      = (gson.fromJson<List<com.hanif.smartstudy.data.model.QuizItem?>>(quizJson,  quizType)  ?: emptyList()).filterNotNull(),
                qbank     = (gson.fromJson<List<com.hanif.smartstudy.data.model.QBankItem?>>(qbankJson, qbankType) ?: emptyList()).filterNotNull(),
                modelTests = modelTests,
                fetchedAt = fetchedAt,
                remoteUpdatedAt = prefs[KEY_REMOTE_UPDATED_AT] ?: 0L
            )
        } catch (e: Exception) { null }
    }

    suspend fun hasCachedContent(): Boolean {
        val prefs = context.dataStore.data.first()
        return (prefs[KEY_CACHE_TIME] ?: 0L) > 0L
    }

    /** Admin edit এর পরে cache invalidate করো */
    suspend fun clearCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_STUDY_JSON)
            prefs.remove(KEY_QUIZ_JSON)
            prefs.remove(KEY_QBANK_JSON)
            prefs.remove(KEY_MODELTESTS_JSON)
            prefs[KEY_CACHE_TIME] = 0L
            prefs[KEY_REMOTE_UPDATED_AT] = 0L
        }
    }

    suspend fun addStudyMinutes(minutes: Int) {
        val today = todayString()
        context.dataStore.edit { prefs ->
            val lastDate = prefs[KEY_STUDY_DATE] ?: ""
            val todayMin = if (lastDate == today) prefs[KEY_TODAY_STUDY] ?: 0 else 0
            prefs[KEY_TODAY_STUDY]  = todayMin + minutes
            prefs[KEY_STUDY_DATE]   = today
            prefs[KEY_WEEK_STUDY]   = (prefs[KEY_WEEK_STUDY]  ?: 0) + minutes
            prefs[KEY_TOTAL_STUDY]  = (prefs[KEY_TOTAL_STUDY] ?: 0) + minutes
        }
    }

    suspend fun getTodayStudyMinutes(): Int {
        val prefs   = context.dataStore.data.first()
        val today   = todayString()
        val lastDay = prefs[KEY_STUDY_DATE] ?: ""
        return if (lastDay == today) prefs[KEY_TODAY_STUDY] ?: 0 else 0
    }

    suspend fun getStudyStats(): Triple<Int, Int, Int> {
        val prefs = context.dataStore.data.first()
        val today = todayString()
        val lastDay = prefs[KEY_STUDY_DATE] ?: ""
        val todayMin = if (lastDay == today) prefs[KEY_TODAY_STUDY] ?: 0 else 0
        return Triple(
            todayMin,
            prefs[KEY_WEEK_STUDY]  ?: 0,
            prefs[KEY_TOTAL_STUDY] ?: 0
        )
    }

    suspend fun updateStreak(): Int {
        val today   = todayString()
        val prefs   = context.dataStore.data.first()
        val lastDate= prefs[KEY_STREAK_DATE] ?: ""
        val current = prefs[KEY_STREAK] ?: 0

        val newStreak = when {
            lastDate == today     -> current
            isYesterday(lastDate) -> current + 1
            lastDate.isEmpty()    -> 1
            else                  -> 1
        }

        context.dataStore.edit { p ->
            p[KEY_STREAK]      = newStreak
            p[KEY_STREAK_DATE] = today
        }
        return newStreak
    }

    suspend fun getStreak(): Int {
        val prefs    = context.dataStore.data.first()
        val lastDate = prefs[KEY_STREAK_DATE] ?: ""
        val current  = prefs[KEY_STREAK] ?: 0
        return if (isStreakAlive(lastDate)) current else 0
    }

    suspend fun incrementCorrect() {
        context.dataStore.edit { it[KEY_CORRECT_COUNT] = (it[KEY_CORRECT_COUNT] ?: 0) + 1 }
    }

    suspend fun incrementWrong() {
        context.dataStore.edit { it[KEY_WRONG_COUNT] = (it[KEY_WRONG_COUNT] ?: 0) + 1 }
    }

    suspend fun getCorrectCount() = context.dataStore.data.first()[KEY_CORRECT_COUNT] ?: 0
    suspend fun getWrongCount()   = context.dataStore.data.first()[KEY_WRONG_COUNT]   ?: 0

    private fun todayString(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)+1}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    private fun isYesterday(dateStr: String): Boolean {
        if (dateStr.isEmpty()) return false
        return try {
            val parts = dateStr.split("-").map { it.toInt() }
            val cal   = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            val y = cal.get(java.util.Calendar.YEAR)
            val m = cal.get(java.util.Calendar.MONTH) + 1
            val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
            parts.size == 3 && parts[0] == y && parts[1] == m && parts[2] == d
        } catch (e: Exception) { false }
    }

    private fun isStreakAlive(lastDate: String): Boolean {
        if (lastDate.isEmpty()) return false
        val today = todayString()
        return lastDate == today || isYesterday(lastDate)
    }
}
