package com.hanif.smartstudy.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_study_prefs")

class SessionManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        val KEY_USER_JSON        = stringPreferencesKey("ss_user")
        val KEY_DARK_MODE        = booleanPreferencesKey("dark_mode")
        val KEY_FONT_SIZE        = floatPreferencesKey("font_size")
        val KEY_THEME_COLOR      = stringPreferencesKey("theme_color")   // "indigo"|"teal"|"rose"|"amber"
        val KEY_OB_DONE          = booleanPreferencesKey("ob_done")
        val KEY_SOUND_OFF        = booleanPreferencesKey("sound_off")
        val KEY_EXAM_DATE        = stringPreferencesKey("exam_date")
        val KEY_DAILY_GOAL       = intPreferencesKey("daily_goal")
        val KEY_USER_NAME        = stringPreferencesKey("home_user_name")
        val KEY_USER_PIC         = stringPreferencesKey("home_user_pic")
        
        // Reminder Keys (Updated for DataStore Consistency)
        val KEY_REMINDER_ON      = booleanPreferencesKey("reminder_on")
        val KEY_REMINDER_HOUR    = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE  = intPreferencesKey("reminder_minute")
        
        val KEY_MORNING_ON       = booleanPreferencesKey("morning_on")
        val KEY_MORNING_HOUR     = intPreferencesKey("morning_hour")
        val KEY_MORNING_MIN      = intPreferencesKey("morning_min")
        
        val KEY_NIGHT_ON         = booleanPreferencesKey("night_on")
        val KEY_NIGHT_HOUR       = intPreferencesKey("night_hour")
        val KEY_NIGHT_MIN        = intPreferencesKey("night_min")
        
        val KEY_LAST_NOTIF_CHECK = longPreferencesKey("last_notif_check")

        // XP history (JSON list of daily XP)
        val KEY_XP_HISTORY       = stringPreferencesKey("xp_history")
        // App time tracking
        val KEY_APP_SESSION_START = longPreferencesKey("app_session_start")
        val KEY_TOTAL_APP_MIN    = intPreferencesKey("total_app_min")
        // Streak
        val KEY_STREAK_COUNT     = intPreferencesKey("streak_count")
        val KEY_STREAK_LAST_DATE = stringPreferencesKey("streak_last_date")
        // Achievements (JSON set of earned ids)
        val KEY_ACHIEVEMENTS     = stringPreferencesKey("achievements")
        // Typing practice best WPM
        val KEY_TYPING_BEST_WPM  = intPreferencesKey("typing_best_wpm")
        // Pending sync count
        val KEY_PENDING_SYNC     = intPreferencesKey("pending_sync_count")
    }

    // ── User ──────────────────────────────────────────────────

    fun isLoggedIn(): Boolean = runBlocking {
        val prefs = context.dataStore.data.first()
        !prefs[KEY_USER_JSON].isNullOrEmpty()
    }

    fun getCurrentUser(): User? = runBlocking {
        try {
            val json = context.dataStore.data.first()[KEY_USER_JSON] ?: return@runBlocking null
            gson.fromJson(json, User::class.java)
        } catch (e: Exception) { null }
    }

    fun currentUserFlow(): Flow<User?> = context.dataStore.data.map { prefs ->
        try { prefs[KEY_USER_JSON]?.let { gson.fromJson(it, User::class.java) } }
        catch (e: Exception) { null }
    }

    suspend fun saveUser(user: User) {
        context.dataStore.edit { p ->
            p[KEY_USER_JSON] = gson.toJson(user)
            p[KEY_USER_NAME] = user.name ?: ""
            user.picture?.let { p[KEY_USER_PIC] = it }
        }
    }

    suspend fun clearUser() {
        context.dataStore.edit { it.remove(KEY_USER_JSON) }
    }

    // ── Theme ─────────────────────────────────────────────────

    fun isDarkMode(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_DARK_MODE] ?: false
    }

    fun darkModeFlow(): Flow<Boolean> = context.dataStore.data.map { it[KEY_DARK_MODE] ?: false }

    suspend fun setDarkMode(on: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = on }
    }

    fun getThemeColor(): String = runBlocking {
        context.dataStore.data.first()[KEY_THEME_COLOR] ?: "indigo"
    }

    fun themeColorFlow(): Flow<String> = context.dataStore.data.map { it[KEY_THEME_COLOR] ?: "indigo" }

    suspend fun setThemeColor(color: String) {
        context.dataStore.edit { it[KEY_THEME_COLOR] = color }
    }

    // ── Sound ─────────────────────────────────────────────────

    fun isSoundOff(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_SOUND_OFF] ?: false
    }

    suspend fun setSoundOff(off: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_OFF] = off }
    }

    // ── Onboarding ────────────────────────────────────────────

    fun isOnboardingDone(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_OB_DONE] ?: false
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_OB_DONE] = true }
    }

    // ── Daily Goal ────────────────────────────────────────────

    fun getDailyGoal(): Int = runBlocking {
        context.dataStore.data.first()[KEY_DAILY_GOAL] ?: 20
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { it[KEY_DAILY_GOAL] = goal }
    }

    // ── Reminder ─────────────────────────────────────────────

    // ── Morning reminder ──
    fun setReminderMorning(on: Boolean, hour: Int, minute: Int) = runBlocking {
        context.dataStore.edit {
            it[KEY_MORNING_ON] = on
            it[KEY_MORNING_HOUR] = hour
            it[KEY_MORNING_MIN] = minute
        }
    }
    fun isMorningReminderOn(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_MORNING_ON] ?: false
    }
    fun getMorningHour(): Int = runBlocking {
        context.dataStore.data.first()[KEY_MORNING_HOUR] ?: 7
    }
    fun getMorningMinute(): Int = runBlocking {
        context.dataStore.data.first()[KEY_MORNING_MIN] ?: 0
    }

    // ── Night reminder ──
    fun setReminderNight(on: Boolean, hour: Int, minute: Int) = runBlocking {
        context.dataStore.edit {
            it[KEY_NIGHT_ON] = on
            it[KEY_NIGHT_HOUR] = hour
            it[KEY_NIGHT_MIN] = minute
        }
    }
    fun isNightReminderOn(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_NIGHT_ON] ?: false
    }
    fun getNightHour(): Int = runBlocking {
        context.dataStore.data.first()[KEY_NIGHT_HOUR] ?: 21
    }
    fun getNightMinute(): Int = runBlocking {
        context.dataStore.data.first()[KEY_NIGHT_MIN] ?: 0
    }

    // ── Notification polling ──
    fun getLastNotifCheck(): Long = runBlocking {
        context.dataStore.data.first()[KEY_LAST_NOTIF_CHECK] ?: 0L
    }
    fun setLastNotifCheck(t: Long) = runBlocking {
        context.dataStore.edit { it[KEY_LAST_NOTIF_CHECK] = t }
    }

    fun isReminderOn(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_REMINDER_ON] ?: false
    }

    fun getReminderHour(): Int = runBlocking {
        context.dataStore.data.first()[KEY_REMINDER_HOUR] ?: 20
    }

    fun getReminderMinute(): Int = runBlocking {
        context.dataStore.data.first()[KEY_REMINDER_MINUTE] ?: 0
    }

    suspend fun setReminder(on: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[KEY_REMINDER_ON]     = on
            it[KEY_REMINDER_HOUR]   = hour
            it[KEY_REMINDER_MINUTE] = minute
        }
    }

    // ── XP History ────────────────────────────────────────────

    suspend fun recordDailyXp(xp: Int) {
        val today  = todayString()
        val prefs  = context.dataStore.data.first()
        val json   = prefs[KEY_XP_HISTORY] ?: "[]"
        val type   = object : com.google.gson.reflect.TypeToken<MutableList<Map<String, Any>>>() {}.type
        val list: MutableList<Map<String, Any>> = try { gson.fromJson(json, type) } catch (e: Exception) { mutableListOf() }
        // Update or add today
        val idx = list.indexOfFirst { it["date"] == today }
        val entry = mapOf("date" to today, "xp" to xp)
        if (idx >= 0) list[idx] = entry else list.add(entry)
        // Keep last 30 days
        val trimmed = if (list.size > 30) list.takeLast(30) else list
        context.dataStore.edit { it[KEY_XP_HISTORY] = gson.toJson(trimmed) }
    }

    fun getXpHistory(): List<Pair<String, Int>> = runBlocking {
        val json  = context.dataStore.data.first()[KEY_XP_HISTORY] ?: return@runBlocking emptyList()
        return@runBlocking try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>> = gson.fromJson(json, type) ?: emptyList()
            list.map { (it["date"] as? String ?: "") to ((it["xp"] as? Double)?.toInt() ?: 0) }
        } catch (e: Exception) { emptyList() }
    }

    // ── App time ─────────────────────────────────────────────

    suspend fun recordSessionMinutes(minutes: Int) {
        context.dataStore.edit {
            it[KEY_TOTAL_APP_MIN] = (it[KEY_TOTAL_APP_MIN] ?: 0) + minutes
        }
    }

    fun getTotalAppMinutes(): Int = runBlocking {
        context.dataStore.data.first()[KEY_TOTAL_APP_MIN] ?: 0
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun todayString(): String {
        val c = java.util.Calendar.getInstance()
        return "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.MONTH)+1}-${c.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    // ── Streak ────────────────────────────────────────────────

    /** Call after each study session. Returns new streak count. */
    fun updateStreak(): Int = runBlocking {
        val today = todayString()
        val prefs = context.dataStore.data.first()
        val lastDate = prefs[KEY_STREAK_LAST_DATE] ?: ""
        val current  = prefs[KEY_STREAK_COUNT] ?: 0
        if (lastDate == today) return@runBlocking current

        val yesterday = run {
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.DAY_OF_MONTH, -1)
            "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.MONTH)+1}-${c.get(java.util.Calendar.DAY_OF_MONTH)}"
        }
        val newStreak = if (lastDate == yesterday) current + 1 else 1
        context.dataStore.edit {
            it[KEY_STREAK_COUNT]     = newStreak
            it[KEY_STREAK_LAST_DATE] = today
        }
        newStreak
    }

    fun getStreak(): Int = runBlocking {
        context.dataStore.data.first()[KEY_STREAK_COUNT] ?: 0
    }

    // ── Achievements ──────────────────────────────────────────

    fun getAchievements(): Set<String> = runBlocking {
        val json = context.dataStore.data.first()[KEY_ACHIEVEMENTS] ?: return@runBlocking emptySet()
        try {
            val type = object : com.google.gson.reflect.TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) { emptySet() }
    }

    suspend fun unlockAchievement(id: String) {
        val current = getAchievements().toMutableSet()
        if (current.contains(id)) return
        current.add(id)
        context.dataStore.edit { it[KEY_ACHIEVEMENTS] = gson.toJson(current) }
    }

    fun hasAchievement(id: String): Boolean = getAchievements().contains(id)

    // ── Typing practice ───────────────────────────────────────

    fun getTypingBestWpm(): Int = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_BEST_WPM] ?: 0
    }

    suspend fun saveTypingWpm(wpm: Int) {
        val best = getTypingBestWpm()
        if (wpm > best) context.dataStore.edit { it[KEY_TYPING_BEST_WPM] = wpm }
    }

    // ── Pending sync ──────────────────────────────────────────

    fun getPendingSyncCount(): Int = runBlocking {
        context.dataStore.data.first()[KEY_PENDING_SYNC] ?: 0
    }

    suspend fun setPendingSyncCount(count: Int) {
        context.dataStore.edit { it[KEY_PENDING_SYNC] = count }
    }
}
