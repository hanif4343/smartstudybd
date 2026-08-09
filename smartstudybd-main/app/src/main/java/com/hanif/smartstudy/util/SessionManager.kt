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

// ── Phase ৩: Roadmap Wizard-এর ফলাফল — ৫-ধাপ প্রশ্নমালার উত্তর থেকে বানানো
// personalized প্ল্যান, "তোমার Roadmap" কার্ডে সবসময় দেখানো হয় (দেখো RoadmapWizard.kt) ──
data class RoadmapPlan(
    val tracks        : List<String> = listOf("bn"),  // "bn" | "en", multi-select
    val experience    : String       = "new",          // "new" | "some"
    val targetWpm     : Int          = 20,
    val planMode      : String       = "daily",        // "daily" | "deadline"
    val dailyMinutes  : Int          = 30,
    val deadlineMillis: Long         = 0L,
    val createdAt     : Long         = 0L,
    val estimatedDoneMillis: Long    = 0L
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
        // ── Study রিকল-টাইপিং (⌨️) মোডে Written উত্তর AI দিয়ে অটো-চেক করার জন্য
        // ইউজারের নিজের API key — Settings থেকে একবার সেভ করলে DataStore-এ থেকে যায়,
        // পরের বার আবার বসাতে হয় না। fallback order: Groq → Mistral → Cerebras → Gemini
        // (Gemini সবার শেষে, কারণ এটা প্রায়ই ফেইল করে)।
        val KEY_AI_GROQ_KEY      = stringPreferencesKey("ai_groq_api_key")
        val KEY_AI_MISTRAL_KEY   = stringPreferencesKey("ai_mistral_api_key")
        val KEY_AI_CEREBRAS_KEY  = stringPreferencesKey("ai_cerebras_api_key")
        val KEY_AI_GEMINI_KEY    = stringPreferencesKey("ai_gemini_api_key")
        // ইউজার ম্যানুয়ালি "অফলাইন মোড" অন করলে — Firebase-এ কোনো read/write
        // হবে না, শুধু লোকাল ক্যাশ (Room + DataStore) থেকেই সব চলবে।
        val KEY_OFFLINE_MODE     = booleanPreferencesKey("offline_mode_on")
        // Settings → "Data Source" ড্রপডাউন — "firebase" | "google_sheet" (দেখুন DataSourceMode.kt)
        val KEY_DATA_SOURCE_MODE = stringPreferencesKey("data_source_mode")
        val KEY_EXAM_DATE        = stringPreferencesKey("exam_date")
        val KEY_DAILY_GOAL       = intPreferencesKey("daily_goal")
        // ── FIX: Streak popup আগে প্রতিবার সাবমিটেই দেখাতো, বিরক্তিকর লাগছিল —
        // এখন দিনে একবারই দেখাবে, শেষ কবে দেখানো হয়েছিল সেই তারিখ এখানে রাখা হয় ──
        val KEY_LAST_STREAK_POPUP_DATE = stringPreferencesKey("last_streak_popup_date")
        val KEY_USER_NAME        = stringPreferencesKey("home_user_name")
        val KEY_USER_PIC         = stringPreferencesKey("home_user_pic")
        // ── Phase ১ (Neonlipi-স্টাইল কাস্টমাইজেশন): Typing Settings ──
        // ইউজার নিজের লক্ষ্য WPM সেট করতে পারে (Settings-এ) — key-unlock/rank সিস্টেম
        // ছাড়াও এখন থেকে ResultCard-এ target-এর সাপেক্ষে অগ্রগতি দেখানো যাবে।
        val KEY_TYPING_TARGET_WPM   = intPreferencesKey("typing_target_wpm")
        // কীবোর্ড ক্লিক-সাউন্ড প্রিসেট — "off" | "soft" | "mechanical" (দেখো
        // util/TypingKeySound.kt) — গ্লোবাল KEY_SOUND_OFF অন থাকলে এটা যাই হোক না কেন বাজবে না।
        val KEY_TYPING_SOUND_PRESET = stringPreferencesKey("typing_sound_preset")
        // ── Phase ৩: Roadmap wizard — একবার জেনারেট করা প্ল্যান JSON আকারে রাখা হয়,
        // দেখো getRoadmapPlan()/saveRoadmapPlan() ও ui/typing/RoadmapWizard.kt ──
        val KEY_ROADMAP_PLAN_JSON = stringPreferencesKey("typing_roadmap_plan")
        // ── Neonlipi-স্টাইল নতুন ফিচারগুলো (heatmap, দুর্বল-কী/চিহ্ন ড্রিল, Govt Mock,
        // BCC, Key-unlock কারিকুলাম, Roadmap, প্রোফাইল/Cloud Sync, আঙুল-পজিশন) একটা
        // মাস্টার টগলের পেছনে — ডিফল্ট বন্ধ (আগের UI-ই দেখা যাবে), Settings থেকে অন
        // করলে সব একসাথে চালু হয়। দেখো getSmartTypingEnabled()/setSmartTypingEnabled()।
        val KEY_SMART_TYPING_ON = booleanPreferencesKey("smart_typing_enabled")
        
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
        // ── পর্ব ২.৩ ফিচার #৬: স্ট্রিক-ক্যালেন্ডার হিটম্যাপের ডেটা-সোর্স — date -> seconds
        // (JSON ম্যাপ, ছোট থাকে বলে ইচ্ছাকৃতভাবে trim করা হয়নি — বছরে ~৩৬৫ এন্ট্রি, নগণ্য সাইজ) ──
        val KEY_TYPING_DAILY_MAP       = stringPreferencesKey("typing_daily_minutes_map")
        // ── প্যাসেজ-পুনরাবৃত্তি এড়ানো (অ্যাপ-রিস্টার্ট/স্ক্রিন-পুনঃপ্রবেশের পরও) — শেষ কয়েকটা
        // দেখানো প্যাসেজের hash — Normal Typing স্ক্রিনে ঢোকার সময় এগুলো বাদ দিয়ে বাছাই হয় ──
        val KEY_TYPING_RECENT_PASSAGES = stringPreferencesKey("typing_recent_passage_hashes")
        // ── পর্ব-১ #১৫ (মাল্টি-লেআউট সিলেক্টর) — ইউজার কোন কীবোর্ড-লেআউটে টাইপ করছে তা
        // মনে রাখা (bijoy/national/phonetic/probhat/unibijoy/software) — শুধু UI প্রেফারেন্স,
        // কোর টাইপিং-ইঞ্জিনে কোনো প্রভাব নেই (আউটপুট-ক্যারেক্টার-ভিত্তিক ট্র্যাকিং, তাই
        // ফলাফল যেকোনো লেআউটেই সঠিক) — শুধু Live Key Highlight ভিজ্যুয়াল ফিচার এটার ওপর
        // নির্ভর করে (দেখো TypingPracticeScreen.kt) ──
        val KEY_TYPING_KEYBOARD_LAYOUT = stringPreferencesKey("typing_keyboard_layout")
        
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

        // ── Phase 6 (db-migration-v2): Admin App-এর Sheet schema মাইগ্রেশনে (Phase 5)
        // সব প্রশ্নের ID রিজেনারেট হয়েছে — পুরনো bookmarks/wrong-answer/progress/
        // study-done ডেটা (নিচের clearStaleContentIdCacheIfNeeded() দেখো) পুরনো ID দিয়ে
        // সেভ ছিল, নতুন ID-র সাথে আর মিলবে না। এই ফ্ল্যাগ দিয়ে per-install একবারই সেই
        // পুরনো cache ক্লিয়ার করা হয়।
        val KEY_CONTENT_SCHEMA_V2_MIGRATED = booleanPreferencesKey("content_schema_v2_migrated")
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

    // ── Phase ১: Typing Settings — Target WPM ও Sound Preset ─────

    /** ইউজারের নিজের সেট করা লক্ষ্য WPM — ডিফল্ট ২০ (সরকারি চাকরির সাধারণ মান) */
    fun getTypingTargetWpm(): Int = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_TARGET_WPM] ?: 20
    }

    suspend fun setTypingTargetWpm(wpm: Int) {
        context.dataStore.edit { it[KEY_TYPING_TARGET_WPM] = wpm.coerceIn(5, 200) }
    }

    /** "off" | "soft" | "mechanical" — ডিফল্ট "soft" */
    fun getTypingSoundPreset(): String = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_SOUND_PRESET] ?: "soft"
    }

    suspend fun setTypingSoundPreset(preset: String) {
        context.dataStore.edit { it[KEY_TYPING_SOUND_PRESET] = preset }
    }

    /** Neonlipi-স্টাইল নতুন টাইপিং ফিচারগুলোর মাস্টার সুইচ — ডিফল্ট **false** (আগের,
     *  পরিচিত UI-ই দেখাবে)। Settings থেকে "Smart Typing" টগল অন করলে heatmap,
     *  দুর্বল-কী/চিহ্ন ড্রিল, Govt Mock, BCC, Key-unlock কারিকুলাম, Roadmap,
     *  প্রোফাইল/Cloud Sync, আঙুল-পজিশন — সবগুলো একসাথে দেখা যাবে (TypingPracticeScreen.kt)। */
    fun getSmartTypingEnabled(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_SMART_TYPING_ON] ?: false
    }

    suspend fun setSmartTypingEnabled(on: Boolean) {
        context.dataStore.edit { it[KEY_SMART_TYPING_ON] = on }
    }

    // ── Phase ৩: Roadmap Wizard ──

    fun getRoadmapPlan(): RoadmapPlan? = runBlocking {
        val json = context.dataStore.data.first()[KEY_ROADMAP_PLAN_JSON] ?: return@runBlocking null
        try { gson.fromJson(json, RoadmapPlan::class.java) } catch (e: Exception) { null }
    }

    suspend fun saveRoadmapPlan(plan: RoadmapPlan) {
        context.dataStore.edit { it[KEY_ROADMAP_PLAN_JSON] = gson.toJson(plan) }
    }

    suspend fun clearRoadmapPlan() {
        context.dataStore.edit { it.remove(KEY_ROADMAP_PLAN_JSON) }
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

    // ── Written উত্তর AI-অটো-চেক: ৪টা প্রোভাইডারের API key সেভ/লোড ────

    fun getAiApiKeys(): com.hanif.smartstudy.data.model.AiApiKeys = runBlocking {
        val prefs = context.dataStore.data.first()
        com.hanif.smartstudy.data.model.AiApiKeys(
            groq     = prefs[KEY_AI_GROQ_KEY] ?: "",
            mistral  = prefs[KEY_AI_MISTRAL_KEY] ?: "",
            cerebras = prefs[KEY_AI_CEREBRAS_KEY] ?: "",
            gemini   = prefs[KEY_AI_GEMINI_KEY] ?: ""
        )
    }

    suspend fun setAiApiKeys(keys: com.hanif.smartstudy.data.model.AiApiKeys) {
        context.dataStore.edit {
            it[KEY_AI_GROQ_KEY]     = keys.groq.trim()
            it[KEY_AI_MISTRAL_KEY]  = keys.mistral.trim()
            it[KEY_AI_CEREBRAS_KEY] = keys.cerebras.trim()
            it[KEY_AI_GEMINI_KEY]   = keys.gemini.trim()
        }
    }

    // ── Offline mode (ম্যানুয়াল বাটন — Firebase সম্পূর্ণ বন্ধ) ───

    fun isOfflineMode(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_OFFLINE_MODE] ?: false
    }

    fun offlineModeFlow(): Flow<Boolean> = context.dataStore.data.map { it[KEY_OFFLINE_MODE] ?: false }

    suspend fun setOfflineMode(on: Boolean) {
        context.dataStore.edit { it[KEY_OFFLINE_MODE] = on }
    }

    // ── Data Source (Firebase / Google Sheet) ─────────────────
    // Settings-এ একবার সিলেক্ট করলে এখানে সেভ থাকে — Quiz/QBank/Study কনটেন্টের
    // read + admin edit/update + subject তালিকা এই মোড অনুযায়ী রুট হয়
    // (দেখুন ContentFetchService.kt ও MenuViewModel-এর admin ফাংশনগুলো)।

    fun getDataSourceMode(): com.hanif.smartstudy.data.model.DataSourceMode = runBlocking {
        val raw = context.dataStore.data.first()[KEY_DATA_SOURCE_MODE]
        com.hanif.smartstudy.data.model.DataSourceMode.fromStorageOrDefault(raw)
    }

    fun dataSourceModeFlow(): Flow<com.hanif.smartstudy.data.model.DataSourceMode> =
        context.dataStore.data.map { com.hanif.smartstudy.data.model.DataSourceMode.fromStorageOrDefault(it[KEY_DATA_SOURCE_MODE]) }

    suspend fun setDataSourceMode(mode: com.hanif.smartstudy.data.model.DataSourceMode) {
        context.dataStore.edit { it[KEY_DATA_SOURCE_MODE] = mode.storageKey }
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

    // ── Streak popup — দিনে একবারই দেখানোর জন্য ──
    /** আজকে (ডিভাইসের লোকাল তারিখ অনুযায়ী) Streak popup এখনো দেখানো হয়নি কিনা */
    fun shouldShowStreakPopupToday(): Boolean = runBlocking {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val last  = context.dataStore.data.first()[KEY_LAST_STREAK_POPUP_DATE]
        last != today
    }

    /** Streak popup দেখানোর পর আজকের তারিখ সেভ করে রাখো — আবার দেখাবে না */
    suspend fun markStreakPopupShownToday() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        context.dataStore.edit { it[KEY_LAST_STREAK_POPUP_DATE] = today }
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

    /** Cloud Sync-এর জন্য raw (chronological, না-reversed) history — TypingCloudSyncService.push()
     *  এই ফরম্যাটই cloud-এ পাঠায়, pull()-ও একই ফরম্যাটে ফেরত দেয় ──*/
    fun getRawTypingHistory(): List<Map<String, Any>> = runBlocking {
        val json = context.dataStore.data.first()[KEY_TYPING_HISTORY] ?: return@runBlocking emptyList()
        try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    /** Cloud থেকে আসা স্ন্যাপশট লোকালের সাথে মিলিয়ে নেয় — bestWpm-এ যেটা বড় সেটা থাকে,
     *  history দুটোই মিলিয়ে (date+wpm দিয়ে ডুপ্লিকেট বাদ) সাম্প্রতিক ১৫টা রাখা হয়। এভাবে
     *  একটা ডিভাইসে অফলাইন প্র্যাকটিস করা ডেটা আরেকটা ডিভাইসে sync করলে হারায় না। */
    suspend fun mergeTypingCloudSnapshot(cloudBestWpm: Int, cloudHistory: List<Map<String, Any>>) {
        val localBest = getTypingBestWpm()
        val localHistory = getRawTypingHistory()

        val merged = (localHistory + cloudHistory)
            .distinctBy { "${it["date"]}_${it["wpm"]}_${it["timeSec"]}" }
            .sortedBy { (it["date"] as? String) ?: "" }
            .takeLast(15)

        context.dataStore.edit {
            it[KEY_TYPING_HISTORY] = gson.toJson(merged)
            if (cloudBestWpm > localBest) it[KEY_TYPING_BEST_WPM] = cloudBestWpm
        }
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
        addTypingSecondsToDailyMap(seconds)
    }

    /** পর্ব ২.৩ ফিচার #৬: প্রতিদিনের মোট টাইপিং-সেকেন্ড আলাদাভাবে জমা রাখে (date -> seconds),
     *  স্ট্রিক-ক্যালেন্ডার হিটম্যাপের জন্য — addTypingSecondsToday()-এর ভেতর থেকেই কল হয়,
     *  আলাদা করে কল করার দরকার নেই। */
    private suspend fun addTypingSecondsToDailyMap(seconds: Int) {
        val prefs = context.dataStore.data.first()
        val json  = prefs[KEY_TYPING_DAILY_MAP] ?: "{}"
        val type  = object : TypeToken<MutableMap<String, Double>>() {}.type
        val map: MutableMap<String, Double> = try { gson.fromJson(json, type) ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
        val today = todayString()
        map[today] = (map[today] ?: 0.0) + seconds
        context.dataStore.edit { it[KEY_TYPING_DAILY_MAP] = gson.toJson(map) }
    }

    /** স্ট্রিক-ক্যালেন্ডার হিটম্যাপের জন্য — date string ("YYYY-M-D") -> মিনিট */
    fun getDailyPracticeMinutes(): Map<String, Int> = runBlocking {
        val json = context.dataStore.data.first()[KEY_TYPING_DAILY_MAP] ?: "{}"
        val type = object : TypeToken<Map<String, Double>>() {}.type
        val map: Map<String, Double> = try { gson.fromJson(json, type) ?: emptyMap() } catch (e: Exception) { emptyMap() }
        map.mapValues { (it.value / 60).toInt() }
    }

    /** প্যাসেজ-পুনরাবৃত্তি এড়ানো — Normal Typing-এ কোনো প্যাসেজ ইউজারকে দেখানো হলেই (সম্পূর্ণ
     *  করুক বা না করুক) এটা কল করে জমা রাখা হয়, যাতে অ্যাপ রিস্টার্ট করলেও ঠিক ওই একই
     *  প্যাসেজটাই আবার প্রথমে ফিরে না আসে (শেষ ৮টার hash জমা থাকে, most-recent-last)। */
    suspend fun recordShownPassage(text: String) {
        val prefs = context.dataStore.data.first()
        val json  = prefs[KEY_TYPING_RECENT_PASSAGES] ?: "[]"
        val type  = object : TypeToken<MutableList<Int>>() {}.type
        val list: MutableList<Int> = try { gson.fromJson(json, type) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
        val h = text.hashCode()
        list.remove(h)   // ডুপ্লিকেট থাকলে সরিয়ে শেষে যোগ (most-recent-last অর্ডার বজায় রাখতে)
        list.add(h)
        val trimmed = if (list.size > 8) list.takeLast(8) else list
        context.dataStore.edit { it[KEY_TYPING_RECENT_PASSAGES] = gson.toJson(trimmed) }
    }

    /** getRecentPassageHashes() — এই hash-গুলো বাদ দিয়ে পরের প্যাসেজ বাছাই করা উচিত */
    fun getRecentPassageHashes(): Set<Int> = runBlocking {
        val json = context.dataStore.data.first()[KEY_TYPING_RECENT_PASSAGES] ?: "[]"
        val type = object : TypeToken<List<Int>>() {}.type
        try { (gson.fromJson(json, type) ?: emptyList<Int>()).toSet() } catch (e: Exception) { emptySet() }
    }

    /** পর্ব-১ #১৫: ইউজারের বেছে নেওয়া কীবোর্ড-লেআউট — ডিফল্ট "bijoy" */
    fun getKeyboardLayout(): String = runBlocking {
        context.dataStore.data.first()[KEY_TYPING_KEYBOARD_LAYOUT] ?: "bijoy"
    }
    suspend fun setKeyboardLayout(layout: String) {
        context.dataStore.edit { it[KEY_TYPING_KEYBOARD_LAYOUT] = layout }
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

    // ── Phase 6 (db-migration-v2): পুরনো content-ID cache ক্লিয়ার ──
    //
    // QuizViewModel-এ bookmarks/wrong-answer/progress/study-done সবকিছুই একটা প্লেইন
    // SharedPreferences ফাইলে ("quiz_prefs", SessionManager-এর DataStore থেকে আলাদা)
    // প্রশ্নের sourceKey() (উদাহরণ: "QUIZ:-Nx7abc...") দিয়ে সেভ থাকে। Admin App-এর Sheet
    // schema মাইগ্রেশনে (Phase 5) সব প্রশ্নের ID রিজেনারেট হয়েছে — তাই এই পুরনো ID-গুলো
    // এখন orphaned: নতুন প্রশ্নের সাথে আর কখনো মিলবে না। এতে crash হয় না, কিন্তু —
    //   ১) bookmark/progress/wrong-answer ইউজারের কাছে "হারিয়ে গেছে" মনে হবে
    //   ২) মৃত এন্ট্রিগুলো SharedPreferences-এ চিরকাল জমে থাকবে
    // ম্যানুয়ালি app data clear করতে বলার বদলে এই ফাংশন per-install একবারই (DataStore
    // ফ্ল্যাগ KEY_CONTENT_SCHEMA_V2_MIGRATED দিয়ে গার্ড করা) সেই পুরনো কী-গুলো মুছে দেয়।
    // MainActivity.onCreate()-এ একবার কল হয়।
    //
    // "weak_<subTopic>" কী-গুলো ইচ্ছাকৃতভাবে বাদ রাখা হয়েছে — এগুলো subTopic নামের ওপর
    // (ID-র ওপর না) নির্ভর করে, আর মাইগ্রেশনে subTopic-এর টেক্সট নাম বদলায়নি (শুধু নতুন
    // subject_id/topic_id কলাম যোগ হয়েছে) — তাই এগুলো নিরাপদ, মুছে দেওয়ার দরকার নেই।
    fun clearStaleContentIdCacheIfNeeded() = runBlocking {
        val already = context.dataStore.data.first()[KEY_CONTENT_SCHEMA_V2_MIGRATED] ?: false
        if (already) return@runBlocking

        val quizPrefs = context.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE)
        quizPrefs.edit()
            .remove("bookmarks")
            .remove("study_done_ids")
            .remove("progress")
            .remove("wrong_q_ids")
            .remove("wrong_q_count")
            .apply()

        context.dataStore.edit { it[KEY_CONTENT_SCHEMA_V2_MIGRATED] = true }
    }
}
