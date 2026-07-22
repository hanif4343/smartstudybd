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

// ── Typing Practice: একটা সেশনের সংক্ষিপ্ত রেকর্ড (হিস্ট্রি লিস্টে দেখানোর জন্য) ──
data class TypingHistoryEntry(
    val date     : String,
    val wpm      : Int,
    val rawWpm   : Int,
    val accuracy : Int,
    val timeSec  : Int
)

class SessionManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        val KEY_USER_JSON        = stringPreferencesKey("ss_user")
        val KEY_DARK_MODE        = booleanPreferencesKey("dark_mode")
        val KEY_FONT_SIZE        = floatPreferencesKey("font_size")
        val KEY_THEME_COLOR      = stringPreferencesKey("theme_color")   // "indigo"|"teal"|"rose"|"amber"
        val KEY_OB_DONE          = booleanPreferencesKey("ob_done")
        val KEY_SOUND_OFF        = booleanPreferencesKey("sound_off")
        // Study মোডে "শুধু প্রশ্ন দেখ" ফিচার — চালু থাকলে উত্তর/ব্যাখ্যা/টেকনিক
        // ডিফল্টভাবে লুকানো থাকে, "উত্তর দেখুন" বাটনে চাপলে তবেই দেখা যায়।
        // টগল বাটনটা Study screen-এর নিজের টপবারেই থাকে (Settings/Menu-তে নয়),
        // কিন্তু পছন্দটা এখানে persist করা থাকে যাতে পরের বার Study খুললেও মনে থাকে।
        val KEY_STUDY_REVEAL_MODE = booleanPreferencesKey("study_reveal_mode")
        // Study: টাইপ করে উত্তর মেলানোর রিকল-প্র্যাকটিস মোড (কীবোর্ড আইকন টগল)
        val KEY_STUDY_RECALL_MODE = booleanPreferencesKey("study_recall_mode")
        // ইউজার ম্যানুয়ালি "অফলাইন মোড" অন করলে — Firebase-এ কোনো read/write
        // হবে না, শুধু লোকাল ক্যাশ (Room + DataStore) থেকেই সব চলবে।
        val KEY_OFFLINE_MODE     = booleanPreferencesKey("offline_mode_on")
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

        // ── Typing Practice: বেস্ট WPM + সাম্প্রতিক সেশনগুলোর হিস্ট্রি ──
        val KEY_TYPING_BEST_WPM  = intPreferencesKey("typing_best_wpm")
        val KEY_TYPING_HISTORY   = stringPreferencesKey("typing_history")   // JSON: [{date,wpm,rawWpm,accuracy,timeSec}]

        // ── Typing Practice: Daily Discipline Mode (optional, non-coercive) —
        // চালু থাকলে প্রতিদিনের টাইপিং-সময় ট্র্যাক হয় ও লক্ষ্যের সাপেক্ষে progress দেখানো হয়।
        // hard-lock করা হয় না (দেখো রোডম্যাপ সেকশন ৫.৩ — Focus Mode-এর non-coercive philosophy অনুসরণ) ──
        val KEY_TYPING_DISCIPLINE_ON   = booleanPreferencesKey("typing_discipline_on")
        val KEY_TYPING_DAILY_GOAL_MIN  = intPreferencesKey("typing_daily_goal_min")   // ডিফল্ট ৬০
        val KEY_TYPING_TODAY_SECONDS   = intPreferencesKey("typing_today_seconds")
        val KEY_TYPING_TODAY_DATE      = stringPreferencesKey("typing_today_date")
        
        val KEY_NIGHT_ON         = booleanPreferencesKey("night_on")
        val KEY_NIGHT_HOUR       = intPreferencesKey("night_hour")
        val KEY_NIGHT_MIN        = intPreferencesKey("night_min")

        val KEY_MIDDAY_ON        = booleanPreferencesKey("midday_on")
        val KEY_MIDDAY_HOUR      = intPreferencesKey("midday_hour")
        val KEY_MIDDAY_MIN       = intPreferencesKey("midday_min")

        val KEY_EVENING_ON       = booleanPreferencesKey("evening_on")
        val KEY_EVENING_HOUR     = intPreferencesKey("evening_hour")
        val KEY_EVENING_MIN      = intPreferencesKey("evening_min")

        // Repeat mode: true = Daily (প্রতিদিন), false = Once (একবার)
        val KEY_MORNING_REPEAT   = booleanPreferencesKey("morning_repeat")
        val KEY_NIGHT_REPEAT     = booleanPreferencesKey("night_repeat")
        val KEY_MIDDAY_REPEAT    = booleanPreferencesKey("midday_repeat")
        val KEY_EVENING_REPEAT   = booleanPreferencesKey("evening_repeat")

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
        // Pending sync count
        val KEY_PENDING_SYNC     = intPreferencesKey("pending_sync_count")

        // Admin: audience tag switch
        val KEY_ADMIN_AUDIENCE_TAG = stringPreferencesKey("admin_audience_tag")

        // App-open এ Settings-redirect শুধু একবারই দেখানোর জন্য — বারবার app
        // খুললেই exact-alarm/battery-optimization এর Settings পেজে চলে যাওয়া
        // "app opening slow" মনে হওয়ার একটা বড় কারণ ছিল।
        val KEY_ASKED_EXACT_ALARM  = booleanPreferencesKey("asked_exact_alarm")
        val KEY_ASKED_BATTERY_OPT  = booleanPreferencesKey("asked_battery_opt")
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
        user.phone?.let { com.hanif.smartstudy.util.RemoteLogger.setUserPhone(it) }
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

    // ── Study: "শুধু প্রশ্ন দেখ" মোড ──────────────────────────

    fun isStudyRevealMode(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_STUDY_REVEAL_MODE] ?: false
    }

    suspend fun setStudyRevealMode(on: Boolean) {
        context.dataStore.edit { it[KEY_STUDY_REVEAL_MODE] = on }
    }

    // ── Study: টাইপ করে উত্তর রিকল-প্র্যাকটিস মোড (⌨️ আইকন) ────

    fun isStudyRecallMode(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_STUDY_RECALL_MODE] ?: false
    }

    suspend fun setStudyRecallMode(on: Boolean) {
        context.dataStore.edit { it[KEY_STUDY_RECALL_MODE] = on }
    }

    // ── Offline mode (ম্যানুয়াল বাটন — Firebase সম্পূর্ণ বন্ধ) ───

    fun isOfflineMode(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_OFFLINE_MODE] ?: false
    }

    fun offlineModeFlow(): Flow<Boolean> = context.dataStore.data.map { it[KEY_OFFLINE_MODE] ?: false }

    suspend fun setOfflineMode(on: Boolean) {
        context.dataStore.edit { it[KEY_OFFLINE_MODE] = on }
    }

    // ── Onboarding ────────────────────────────────────────────

    fun isOnboardingDone(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_OB_DONE] ?: false
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_OB_DONE] = true }
    }

    // ── App-open permission prompts (শুধু একবার দেখানোর জন্য) ────

    fun hasAskedExactAlarmPermission(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_ASKED_EXACT_ALARM] ?: false
    }

    fun setAskedExactAlarmPermission() = runBlocking {
        context.dataStore.edit { it[KEY_ASKED_EXACT_ALARM] = true }
    }

    fun hasAskedBatteryOptPermission(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_ASKED_BATTERY_OPT] ?: false
    }

    fun setAskedBatteryOptPermission() = runBlocking {
        context.dataStore.edit { it[KEY_ASKED_BATTERY_OPT] = true }
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
    fun setReminderMorning(on: Boolean, hour: Int, minute: Int, repeatDaily: Boolean = true) = runBlocking {
        context.dataStore.edit {
            it[KEY_MORNING_ON] = on
            it[KEY_MORNING_HOUR] = hour
            it[KEY_MORNING_MIN] = minute
            it[KEY_MORNING_REPEAT] = repeatDaily
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
    fun isMorningRepeatDaily(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_MORNING_REPEAT] ?: true
    }

    // ── Night reminder ──
    fun setReminderNight(on: Boolean, hour: Int, minute: Int, repeatDaily: Boolean = true) = runBlocking {
        context.dataStore.edit {
            it[KEY_NIGHT_ON] = on
            it[KEY_NIGHT_HOUR] = hour
            it[KEY_NIGHT_MIN] = minute
            it[KEY_NIGHT_REPEAT] = repeatDaily
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
    fun isNightRepeatDaily(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_NIGHT_REPEAT] ?: true
    }

    // ── Midday progress check ──
    fun setReminderMidday(on: Boolean, hour: Int, minute: Int, repeatDaily: Boolean = true) = runBlocking {
        context.dataStore.edit {
            it[KEY_MIDDAY_ON] = on
            it[KEY_MIDDAY_HOUR] = hour
            it[KEY_MIDDAY_MIN] = minute
            it[KEY_MIDDAY_REPEAT] = repeatDaily
        }
    }
    fun isMiddayReminderOn(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_MIDDAY_ON] ?: false
    }
    fun getMiddayHour(): Int = runBlocking {
        context.dataStore.data.first()[KEY_MIDDAY_HOUR] ?: 14
    }
    fun getMiddayMinute(): Int = runBlocking {
        context.dataStore.data.first()[KEY_MIDDAY_MIN] ?: 0
    }
    fun isMiddayRepeatDaily(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_MIDDAY_REPEAT] ?: true
    }

    // ── Evening urgency check ──
    fun setReminderEvening(on: Boolean, hour: Int, minute: Int, repeatDaily: Boolean = true) = runBlocking {
        context.dataStore.edit {
            it[KEY_EVENING_ON] = on
            it[KEY_EVENING_HOUR] = hour
            it[KEY_EVENING_MIN] = minute
            it[KEY_EVENING_REPEAT] = repeatDaily
        }
    }
    fun isEveningReminderOn(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_EVENING_ON] ?: false
    }
    fun getEveningHour(): Int = runBlocking {
        context.dataStore.data.first()[KEY_EVENING_HOUR] ?: 19
    }
    fun getEveningMinute(): Int = runBlocking {
        context.dataStore.data.first()[KEY_EVENING_MIN] ?: 0
    }
    fun isEveningRepeatDaily(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_EVENING_REPEAT] ?: true
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

    // ── Typing Practice: বেস্ট WPM (persist) + সাম্প্রতিক সেশন হিস্ট্রি ──
    // আগে এই ডেটা কোথাও সেভ হতো না — TypingPracticeScreen প্রতিবার bestWpm=0
    // দিয়ে খুলত, ফলে "🏆 Best WPM"/"নতুন Record!" ফিচারটা আসলে কখনো কাজ করত না।

    fun getTypingBestWpm(): Int = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_BEST_WPM] ?: 0
    }

    /** একটা সেশন শেষ হলে কল করো — বেস্ট WPM আপডেট (দরকার হলে) + হিস্ট্রিতে যোগ (সর্বশেষ ১৫টা রাখা হয়) */
    suspend fun recordTypingResult(wpm: Int, rawWpm: Int, accuracy: Int, timeSec: Int) {
        val prefs = context.dataStore.data.first()
        val bestSoFar = prefs[KEY_TYPING_BEST_WPM] ?: 0
        val json  = prefs[KEY_TYPING_HISTORY] ?: "[]"
        val type  = object : TypeToken<MutableList<Map<String, Any>>>() {}.type
        val list: MutableList<Map<String, Any>> = try { gson.fromJson(json, type) } catch (e: Exception) { mutableListOf() }
        list.add(mapOf(
            "date" to todayString(), "wpm" to wpm, "rawWpm" to rawWpm,
            "accuracy" to accuracy, "timeSec" to timeSec
        ))
        val trimmed = if (list.size > 15) list.takeLast(15) else list
        context.dataStore.edit {
            it[KEY_TYPING_HISTORY] = gson.toJson(trimmed)
            if (wpm > bestSoFar) it[KEY_TYPING_BEST_WPM] = wpm
        }
    }

    fun getTypingHistory(): List<TypingHistoryEntry> = runBlocking {
        val json = context.dataStore.data.first()[KEY_TYPING_HISTORY] ?: return@runBlocking emptyList()
        return@runBlocking try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>> = gson.fromJson(json, type) ?: emptyList()
            list.map {
                TypingHistoryEntry(
                    date     = it["date"] as? String ?: "",
                    wpm      = (it["wpm"] as? Double)?.toInt() ?: 0,
                    rawWpm   = (it["rawWpm"] as? Double)?.toInt() ?: 0,
                    accuracy = (it["accuracy"] as? Double)?.toInt() ?: 0,
                    timeSec  = (it["timeSec"] as? Double)?.toInt() ?: 0
                )
            }.reversed()   // সর্বশেষটা আগে
        } catch (e: Exception) { emptyList() }
    }

    // ── Typing Practice: Daily Discipline Mode ──
    // non-coercive — hard-lock করা হয় না, শুধু progress track ও দেখানো হয়

    fun isTypingDisciplineOn(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_DISCIPLINE_ON] ?: false
    }

    /** কখনো explicit সেট করা না থাকলে null ফেরত দেয় — caller admin-কিনা দেখে ডিফল্ট ঠিক করতে পারে */
    fun getTypingDisciplineRaw(): Boolean? = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_DISCIPLINE_ON]
    }

    suspend fun setTypingDisciplineOn(on: Boolean) {
        context.dataStore.edit { it[KEY_TYPING_DISCIPLINE_ON] = on }
    }

    fun getTypingDailyGoalMinutes(): Int = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_DAILY_GOAL_MIN] ?: 60
    }

    suspend fun setTypingDailyGoalMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_TYPING_DAILY_GOAL_MIN] = minutes }
    }

    /** আজকে এখন পর্যন্ত মোট কত সেকেন্ড টাইপ করা হয়েছে — তারিখ বদলালে স্বয়ংক্রিয়ভাবে ০ থেকে শুরু হয় */
    fun getTypingTodaySeconds(): Int = runBlocking {
        val prefs = context.dataStore.data.first()
        val savedDate = prefs[KEY_TYPING_TODAY_DATE] ?: ""
        if (savedDate != todayString()) 0 else (prefs[KEY_TYPING_TODAY_SECONDS] ?: 0)
    }

    /** একটা টাইপিং সেশন শেষ হলে কল করো — আজকের মোট সময়ে যোগ হবে (তারিখ বদলালে আগে রিসেট হয়) */
    suspend fun addTypingSecondsToday(seconds: Int) {
        val prefs = context.dataStore.data.first()
        val savedDate = prefs[KEY_TYPING_TODAY_DATE] ?: ""
        val today = todayString()
        val base = if (savedDate == today) (prefs[KEY_TYPING_TODAY_SECONDS] ?: 0) else 0
        context.dataStore.edit {
            it[KEY_TYPING_TODAY_DATE]    = today
            it[KEY_TYPING_TODAY_SECONDS] = base + seconds
        }
    }



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

    // ── Pending sync ──────────────────────────────────────────

    fun getPendingSyncCount(): Int = runBlocking {
        context.dataStore.data.first()[KEY_PENDING_SYNC] ?: 0
    }

    suspend fun setPendingSyncCount(count: Int) {
        context.dataStore.edit { it[KEY_PENDING_SYNC] = count }
    }

    // ── Font Scale (for accessibility user override) ──────────
    // Default 1.0f = normal size, larger = bigger text

    fun getFontScale(): Float = runBlocking {
        context.dataStore.data.first()[KEY_FONT_SIZE] ?: 1.0f
    }

    fun fontScaleFlow(): Flow<Float> = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: 1.0f }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = scale }
    }

    // ── Admin Audience Tag ────────────────────────────────────
    fun getAdminAudienceTag(): String = runBlocking {
        context.dataStore.data.first()[KEY_ADMIN_AUDIENCE_TAG] ?: ""
    }
    suspend fun setAdminAudienceTag(tag: String) {
        context.dataStore.edit { it[KEY_ADMIN_AUDIENCE_TAG] = tag }
    }
}
