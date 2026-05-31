package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first

class ContentCache(private val context: Context) {

    private val gson = Gson()

    companion object {
        val KEY_STUDY_JSON    = stringPreferencesKey("cache_study_json")
        val KEY_QUIZ_JSON     = stringPreferencesKey("cache_quiz_json")
        val KEY_QBANK_JSON    = stringPreferencesKey("cache_qbank_json")
        val KEY_CACHE_TIME    = longPreferencesKey("cache_fetched_at")

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
            prefs[KEY_STUDY_JSON] = gson.toJson(content.study)
            prefs[KEY_QUIZ_JSON]  = gson.toJson(content.quiz)
            prefs[KEY_QBANK_JSON] = gson.toJson(content.qbank)
            prefs[KEY_CACHE_TIME] = content.fetchedAt
        }
    }

    // FIX: যেকোনো একটা key null হলেও বাকিগুলো দিয়ে AppContent বানাও
    suspend fun loadContent(): AppContent? {
        return try {
            val prefs = context.dataStore.data.first()
            val fetchedAt = prefs[KEY_CACHE_TIME] ?: 0L

            // কোনো cache নেই
            if (fetchedAt == 0L) return null

            val studyJson = prefs[KEY_STUDY_JSON] ?: "[]"
            val quizJson  = prefs[KEY_QUIZ_JSON]  ?: "[]"
            val qbankJson = prefs[KEY_QBANK_JSON] ?: "[]"

            val studyType = object : com.google.gson.reflect.TypeToken<List<com.hanif.smartstudy.data.model.StudyItem>>() {}.type
            val quizType  = object : com.google.gson.reflect.TypeToken<List<com.hanif.smartstudy.data.model.QuizItem>>() {}.type
            val qbankType = object : com.google.gson.reflect.TypeToken<List<com.hanif.smartstudy.data.model.QBankItem>>() {}.type

            AppContent(
                study     = gson.fromJson(studyJson, studyType) ?: emptyList(),
                quiz      = gson.fromJson(quizJson,  quizType)  ?: emptyList(),
                qbank     = gson.fromJson(qbankJson, qbankType) ?: emptyList(),
                fetchedAt = fetchedAt
            )
        } catch (e: Exception) { null }
    }

    suspend fun hasCachedContent(): Boolean {
        val prefs = context.dataStore.data.first()
        return (prefs[KEY_CACHE_TIME] ?: 0L) > 0L
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
