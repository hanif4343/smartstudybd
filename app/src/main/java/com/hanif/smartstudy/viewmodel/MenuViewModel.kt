package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.data.remote.ImgBbResult
import com.hanif.smartstudy.data.remote.ImgBbService
import com.hanif.smartstudy.receiver.ReminderReceiver
import com.hanif.smartstudy.service.SmartStudyFirebaseService
import com.hanif.smartstudy.ui.theme.AppTheme
import com.hanif.smartstudy.ui.theme.themeFromString
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────
//  MenuViewModel — all Menu tab state
// ─────────────────────────────────────────────────────────────

data class ActiveUser(
    val phone    : String = "",
    val name     : String = "",
    val lastSeen : Long   = 0L,
    val isOnline : Boolean = false,
    val fcmToken : String = ""
)

data class DebugLogEntry(
    val ts    : Long   = 0L,
    val level : String = "D",
    val tag   : String = "",
    val msg   : String = "",
    val phone : String = ""
)

data class MenuUiState(
    val user            : User?              = null,
    val isAdmin         : Boolean            = false,
    val isDarkMode      : Boolean            = false,
    val appTheme        : AppTheme           = AppTheme.INDIGO,
    val isSoundOff      : Boolean            = false,
    val isOfflineMode   : Boolean            = false,
    val isReminderOn    : Boolean            = false,
    val reminderHour    : Int                = 20,
    val reminderMinute  : Int                = 0,
    val isMorningOn     : Boolean            = false,
    val morningHour     : Int                = 7,
    val morningMinute   : Int                = 0,
    val isMorningRepeat : Boolean            = true,
    val isNightOn       : Boolean            = false,
    val nightHour       : Int                = 21,
    val nightMinute     : Int                = 0,
    val isNightRepeat   : Boolean            = true,
    val isMiddayOn      : Boolean            = false,
    val middayHour      : Int                = 14,
    val middayMinute    : Int                = 0,
    val isMiddayRepeat  : Boolean            = true,
    val isEveningOn     : Boolean            = false,
    val eveningHour     : Int                = 19,
    val eveningMinute   : Int                = 0,
    val isEveningRepeat : Boolean            = true,
    val correctCount    : Int                = 0,
    val wrongCount      : Int                = 0,
    val accuracyPct     : Int                = 0,
    val totalStudyMin   : Int                = 0,
    val totalAppMin     : Int                = 0,
    val xpHistory       : List<Pair<String,Int>> = emptyList(),
    val fcmToken        : String             = "",
    val isUploadingPhoto: Boolean            = false,
    val uploadProgress  : Boolean            = false,
    val photoUploadError: String?            = null,
    val isLoading       : Boolean            = false,
    val toast           : String?            = null,
    val successMsg      : String?            = null,
    val error           : String?            = null,
    // Stats
    val totalCorrect    : Int                = 0,
    val totalWrong      : Int                = 0,
    val subjectStats    : Map<String, Pair<Int,Int>> = emptyMap(),
    // Bookmarks
    val bookmarkedIds   : Set<String>        = emptySet(),
    // Weak topics (Profile/Stats only)
    val weakTopics      : List<com.hanif.smartstudy.data.model.WeakTopic> = emptyList(),
    // Study time breakdown
    val todayStudyMin   : Int                = 0,
    val weekStudyMin    : Int                = 0,
    // Active users (Admin)
    val activeUsers     : List<ActiveUser>   = emptyList(),
    val allUsers        : List<Map<String,String>> = emptyList(),
    val viewingAsUser   : User?              = null,
    // Remote debug logs (Admin)
    val debugLogPhones  : List<String>       = emptyList(),
    val debugLogs       : List<DebugLogEntry> = emptyList(),
    val isLoadingLogs   : Boolean            = false,

    // ── Admin Power features ──────────────────────────────────
    val adminViewingTag   : String           = "",   // audience switch
    val isEditingQuestion : Boolean          = false,
    val editSuccessMsg    : String?          = null,
    // Delete Question (পুরো কার্ড — প্রশ্ন+অপশন+উত্তর+ব্যাখ্যা)
    val isDeletingQuestion: Boolean          = false,
    val deleteSuccessMsg  : String?          = null,
    // Report Queue
    val reportedQuestions : List<com.hanif.smartstudy.data.remote.ReportedQuestion> = emptyList(),
    val isLoadingReports  : Boolean          = false,
    // Add Question
    val isAddingQuestion  : Boolean          = false,
    val addQuestionMsg    : String?          = null,
    // Bulk Question Uploader (admin app এর মতো — local-first, sync হবে পরে)
    val isBulkUploading   : Boolean          = false,
    val bulkUploadTotal   : Int              = 0,
    val bulkUploadDone    : Int              = 0,
    val bulkUploadSent    : Int              = 0,
    val bulkUploadFailed  : Int              = 0,
    val bulkUploadLog     : List<String>     = emptyList(),
    val bulkUploadResultMsg: String?         = null,
    // Bulk Audience
    val isBulkUpdating    : Boolean          = false,
    val bulkUpdateMsg     : String?          = null,
    // Subject/SubTopic Rename
    val isRenaming        : Boolean          = false,
    val renameMsg         : String?          = null,
    val isDeletingSubject : Boolean          = false,
    val deleteSubjectMsg  : String?          = null,
    // Admin "Move" (ফাইল ম্যানেজারের মতো — প্রশ্ন/টপিক অন্য Subject/Topic-এ move)
    val isMovingContent   : Boolean          = false,
    val moveContentMsg    : String?          = null,
    // Model Test bulk-generate (Admin)
    // ── Subject/SubTopic taxonomy (dropdown suggestions এর জন্য) ──
    // key: sheet ("Quiz"/"QBank"/"Study") → distinct subject list
    val adminSubjectsBySheet  : Map<String, List<String>> = emptyMap(),
    // key: "sheet|subject" → distinct subTopic list
    val adminSubTopicsByKey   : Map<String, List<String>> = emptyMap(),
    val isLoadingTaxonomy     : Boolean      = false,
    // Offline admin edits
    val pendingEdits      : List<com.hanif.smartstudy.data.local.PendingAction> = emptyList(),
    val isSyncingEdits    : Boolean          = false,
    val syncEditsMsg      : String?          = null,
    // edit হলে increment হয় — MainScreen এ observe করে quiz/study/qbank refresh হয়
    val contentEditVersion: Int              = 0,

    // ── Written উত্তর AI-অটো-চেক (স্টাডি ⌨️ রিকল-টাইপিং মোড) — ৪টা প্রোভাইডারের API key ──
    val groqApiKey        : String           = "",
    val mistralApiKey     : String           = "",
    val cerebrasApiKey    : String           = "",
    val geminiApiKey      : String           = "",
    // ── প্রতিটা প্রোভাইডারের নির্বাচিত মডেল — Settings-এ key-এর পাশের ড্রপডাউনে দেখানো হয় ──
    val groqModel         : String           = com.hanif.smartstudy.data.model.AiApiKeys.DEFAULT_GROQ_MODEL,
    val mistralModel      : String           = com.hanif.smartstudy.data.model.AiApiKeys.DEFAULT_MISTRAL_MODEL,
    val cerebrasModel     : String           = com.hanif.smartstudy.data.model.AiApiKeys.DEFAULT_CEREBRAS_MODEL,
    val geminiModel       : String           = com.hanif.smartstudy.data.model.AiApiKeys.DEFAULT_GEMINI_MODEL,
    val aiKeysSavedMsg    : String?          = null,

    // ── Typing Settings (SettingsScreen "⌨️ টাইপিং সেটিংস" কার্ড) ──
    val smartTypingEnabled : Boolean         = false,
    val typingTargetWpm    : Int             = 40,
    val typingSoundPreset  : String          = "off",   // "off" | "soft" | "mechanical"

    // ── লাইভ ফিচার হোল্ড/আনহোল্ড (SettingsScreen "🎮 লাইভ ফিচার") — Speed Plan Task 4 ──
    val challengesEnabled  : Boolean         = false,
    val buddyEnabled       : Boolean         = false,
    val typingRaceEnabled  : Boolean         = false,
    val typingLeaderboardEnabled : Boolean   = false,
)

class MenuViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val cache   = ContentCache(app)
    private val ctx     = app.applicationContext

    // ── Data Source ফিচার সম্পূর্ণ সরানো হয়েছে — Content READ (Quiz/QBank/Study)
    // সবসময় কোড-লেভেলে ফিক্সড পথে যায় (CDN প্রাইমারি, GAS শুধু targeted fallback/
    // write-এ, দেখো ContentFetchService.kt/CdnService.kt), কোনো user-facing টগল
    // নেই। admin WRITE (edit/delete/add/rename) GAS_URL/GAS_SECRET কনফিগার করা
    // থাকলে সবসময় Sheet-ই প্রাইমারি/নির্ভরযোগ্য write টার্গেট, Firebase শুধু
    // best-effort মিরর (ব্যর্থ হলেও Sheet write আটকায় না)। ──

    /** Firebase অ্যাকশন-টাকে try/catch এ মুড়ে দেয় — exception হলেও ApiResult.Error রিটার্ন করে, throw করে না */
    // ── Phase 6 পূর্ণ কাটওভার (single-user account) — আগে এখানে Firebase RTDB-তেও
    // "best-effort mirror" হিসেবে একসাথে লেখা হতো (dual-write), Sheet primary + Firebase
    // backup। যেহেতু RTDB-র Quiz/QBank/Study node ডিলিটের পরিকল্পনা করা হচ্ছে, এখন থেকে
    // এই তিনটে ফাংশন শুধুই Google Sheet/GAS-এ লেখে — fbBestEffort/Firebase mirror সরানো
    // হয়েছে। GAS কনফিগার করা না থাকলে এখন সরাসরি error রিটার্ন করে (Firebase fallback নেই)।

    private suspend fun adminUpdateField(
        sheet: String, rowKey: String, fields: Map<String, String>
    ): com.hanif.smartstudy.data.remote.ApiResult<Unit> {
        if (!com.hanif.smartstudy.data.remote.GasContentService.isConfigured()) {
            return com.hanif.smartstudy.data.remote.ApiResult.Error("Google Sheet কনফিগার করা নেই")
        }
        return when (val sheetResult = com.hanif.smartstudy.data.remote.GasContentService.updateFields(sheet, rowKey, fields)) {
            is com.hanif.smartstudy.data.remote.ApiResult.Success -> com.hanif.smartstudy.data.remote.ApiResult.Success(Unit)
            is com.hanif.smartstudy.data.remote.ApiResult.Error -> com.hanif.smartstudy.data.remote.ApiResult.Error("Sheet: ${sheetResult.message}")
        }
    }

    private suspend fun adminDeleteRow(sheet: String, rowKey: String): com.hanif.smartstudy.data.remote.ApiResult<Unit> {
        if (!com.hanif.smartstudy.data.remote.GasContentService.isConfigured()) {
            return com.hanif.smartstudy.data.remote.ApiResult.Error("Google Sheet কনফিগার করা নেই")
        }
        return when (val sheetResult = com.hanif.smartstudy.data.remote.GasContentService.deleteQuestion(sheet, rowKey)) {
            is com.hanif.smartstudy.data.remote.ApiResult.Success -> com.hanif.smartstudy.data.remote.ApiResult.Success(Unit)
            is com.hanif.smartstudy.data.remote.ApiResult.Error -> com.hanif.smartstudy.data.remote.ApiResult.Error("Sheet: ${sheetResult.message}")
        }
    }

    private suspend fun adminAddRow(sheet: String, fields: Map<String, String>): com.hanif.smartstudy.data.remote.ApiResult<String> {
        if (!com.hanif.smartstudy.data.remote.GasContentService.isConfigured()) {
            return com.hanif.smartstudy.data.remote.ApiResult.Error("Google Sheet কনফিগার করা নেই")
        }
        return when (val sheetResult = com.hanif.smartstudy.data.remote.GasContentService.addQuestion(sheet, fields)) {
            is com.hanif.smartstudy.data.remote.ApiResult.Success -> sheetResult
            is com.hanif.smartstudy.data.remote.ApiResult.Error -> com.hanif.smartstudy.data.remote.ApiResult.Error("Sheet: ${sheetResult.message}")
        }
    }

    // ── Firebase REST helpers ─────────────────────────────────
    private val http    = OkHttpClient()
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()
    private val fbUrl   get() = BuildConfig.FIREBASE_URL.trimEnd('/')
    private suspend fun fbAuth(): String = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()

    private suspend fun fbPatch(path: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = JSONObject(data.mapValues { it.value ?: JSONObject.NULL }).toString()
            .toRequestBody(JSON_MT)
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=${fbAuth()}")
            .patch(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("fbPatch $path failed: ${r.code}")
        }
    }

    private suspend fun fbSet(path: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = JSONObject(data.mapValues { it.value ?: JSONObject.NULL }).toString()
            .toRequestBody(JSON_MT)
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=${fbAuth()}")
            .put(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("fbSet $path failed: ${r.code}")
        }
    }

    private suspend fun fbPost(path: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = JSONObject(data.mapValues { it.value ?: JSONObject.NULL }).toString()
            .toRequestBody(JSON_MT)
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=${fbAuth()}")
            .post(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("fbPost $path failed: ${r.code}")
        }
    }

    private suspend fun fbGet(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=${fbAuth()}")
            .get().build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string() ?: return@withContext null
            if (txt == "null") return@withContext null
            JSONObject(txt)
        }
    }

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    init {
        loadAll()
        // ── আগে isAdmin শুধু loadAll()-এ (app চালু হওয়ার সময় একবারই) সেট হতো।
        // App বন্ধ না করে logout করে অন্য account দিয়ে login করলে এই
        // MenuViewModel instance-টা recreate হতো না (Activity-scoped), ফলে
        // পুরনো session-এর isAdmin=true state থেকেই যেত — নতুন (non-admin)
        // account-ও ভুলভাবে Admin Menu দেখতো। এখন session-এর user বদলালেই
        // (logout-এ null, login-এ নতুন user) সাথে সাথে isAdmin recompute হয়। ──
        viewModelScope.launch {
            session.currentUserFlow().collect { u ->
                _state.update { it.copy(user = u, isAdmin = u?.isAdmin() ?: false) }
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            val localUser  = session.getCurrentUser()
            val isDark     = session.isDarkMode()
            val theme      = themeFromString(session.getThemeColor())
            val soundOff   = session.isSoundOff()
            val offlineOn  = session.isOfflineMode()
            val remOn      = session.isReminderOn()
            val remH       = session.getReminderHour()
            val remM       = session.getReminderMinute()
            val morningOn  = session.isMorningReminderOn()
            val morningH   = session.getMorningHour()
            val morningM   = session.getMorningMinute()
            val morningRep = session.isMorningRepeatDaily()
            val nightOn    = session.isNightReminderOn()
            val nightH     = session.getNightHour()
            val nightM     = session.getNightMinute()
            val nightRep   = session.isNightRepeatDaily()
            val middayOn   = session.isMiddayReminderOn()
            val middayH    = session.getMiddayHour()
            val middayM    = session.getMiddayMinute()
            val middayRep  = session.isMiddayRepeatDaily()
            val eveningOn  = session.isEveningReminderOn()
            val eveningH   = session.getEveningHour()
            val eveningM   = session.getEveningMinute()
            val eveningRep = session.isEveningRepeatDaily()
            val correct    = cache.getCorrectCount()
            val wrong      = cache.getWrongCount()
            val total      = correct + wrong
            val acc        = if (total > 0) (correct * 100) / total else 0
            val stats      = cache.getStudyStats()
            val xpHist     = session.getXpHistory()
            val totalApp   = session.getTotalAppMinutes()
            val fcm        = localUser?.fcmToken ?: ""

            val prefs = ctx.getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)
            val bookmarks = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
            val adminTag  = if (localUser?.isAdmin() == true) session.getAdminAudienceTag() else ""

            // ── Typing Settings — SessionManager-এর DataStore থেকে পড়া হয় (আগে এখানে
            // "quiz_prefs" SharedPreferences থেকে পড়া হতো, কিন্তু TypingPracticeScreen.kt
            // আসলে session.getSmartTypingEnabled() (DataStore) থেকে ফ্ল্যাগ পড়ে — দুই
            // জায়গায় দুই storage থাকায় Settings-এ টগল অন করলেও TypingPracticeScreen
            // কখনো সেটা দেখতেই পেত না। এখন দুই পাশই একই source ব্যবহার করছে ──
            val smartTypingOn   = session.getSmartTypingEnabled()
            val typingTargetWpm = session.getTypingTargetWpm()
            val typingSoundPr   = session.getTypingSoundPreset()

            // ── Speed Plan Task 4: লাইভ ফিচার টগল ──
            val challengesOn  = session.getChallengesEnabled()
            val buddyOn       = session.getBuddyEnabled()
            val typingRaceOn  = session.getTypingRaceEnabled()
            val typingLeaderboardOn = session.getTypingLeaderboardEnabled()

            val aiKeys = session.getAiApiKeys()

            val weakTopics = prefs.all.entries
                .filter { it.key.startsWith("weak_") && (it.value as? Int ?: 0) >= 2 }
                .map { com.hanif.smartstudy.data.model.WeakTopic(
                    subTopic   = it.key.removePrefix("weak_"),
                    subject    = "",
                    wrongCount = it.value as Int
                )}
                .sortedByDescending { it.wrongCount }

            // প্রথমে local user দিয়ে UI দেখাও (fast)
            _state.update {
                it.copy(
                    user           = localUser,
                    isAdmin        = localUser?.isAdmin() ?: false,
                    isDarkMode     = isDark,
                    appTheme       = theme,
                    isSoundOff     = soundOff,
                    isOfflineMode  = offlineOn,
                    isReminderOn   = remOn,
                    reminderHour   = remH,
                    reminderMinute = remM,
                    isMorningOn    = morningOn,
                    morningHour    = morningH,
                    morningMinute  = morningM,
                    isMorningRepeat = morningRep,
                    isNightOn      = nightOn,
                    nightHour      = nightH,
                    nightMinute    = nightM,
                    isNightRepeat  = nightRep,
                    isMiddayOn     = middayOn,
                    middayHour     = middayH,
                    middayMinute   = middayM,
                    isMiddayRepeat = middayRep,
                    isEveningOn    = eveningOn,
                    eveningHour    = eveningH,
                    eveningMinute  = eveningM,
                    isEveningRepeat = eveningRep,
                    correctCount   = correct,
                    wrongCount     = wrong,
                    totalCorrect   = correct,
                    totalWrong     = wrong,
                    accuracyPct    = acc,
                    todayStudyMin  = stats.first,
                    weekStudyMin   = stats.second,
                    totalStudyMin  = stats.third,
                    totalAppMin    = totalApp,
                    xpHistory      = xpHist,
                    fcmToken       = fcm,
                    bookmarkedIds  = bookmarks,
                    weakTopics     = weakTopics,
                    adminViewingTag = adminTag,
                    groqApiKey     = aiKeys.groq,
                    mistralApiKey  = aiKeys.mistral,
                    cerebrasApiKey = aiKeys.cerebras,
                    geminiApiKey   = aiKeys.gemini,
                    groqModel      = aiKeys.groqModel,
                    mistralModel   = aiKeys.mistralModel,
                    cerebrasModel  = aiKeys.cerebrasModel,
                    geminiModel    = aiKeys.geminiModel,
                    smartTypingEnabled = smartTypingOn,
                    typingTargetWpm    = typingTargetWpm,
                    typingSoundPreset  = typingSoundPr,
                    challengesEnabled  = challengesOn,
                    buddyEnabled       = buddyOn,
                    typingRaceEnabled  = typingRaceOn,
                    typingLeaderboardEnabled = typingLeaderboardOn
                )
            }

            // অফলাইন মোড অন থাকলে এখান থেকে আর কোনো Firebase কল হবে না —
            // localUser দিয়েই UI চলবে, উপরের state.update এতেই যথেষ্ট।
            if (offlineOn) return@launch

            // Firebase থেকে fresh user fetch করো (reducedUi সহ সব latest data)
            if (!localUser?.phone.isNullOrEmpty()) {
                try {
                    val freshUser = com.hanif.smartstudy.data.remote.UserSyncService
                        .fetchUser(localUser!!.phone!!)
                        ?.copy(phone = localUser.phone, fcmToken = localUser.fcmToken)
                    if (freshUser != null) {
                        session.saveUser(freshUser)
                        _state.update { it.copy(
                            user    = freshUser,
                            isAdmin = freshUser.isAdmin()
                        )}
                        Log.d("Menu", "Fresh user loaded: reducedUi=${freshUser.reducedUi}")
                    }
                } catch (e: Exception) {
                    Log.e("Menu", "Firebase refresh failed: ${e.message}")
                }
            }

            // FCM token fresh fetch
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                _state.update { it.copy(fcmToken = token) }
                SmartStudyFirebaseService.saveFcmTokenToFirebase(ctx, token)
            }
        }
    }

    // ── Profile photo upload ──────────────────────────────────

    fun uploadProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingPhoto = true, photoUploadError = null) }
            when (val result = ImgBbService.uploadImage(ctx, uri)) {
                is ImgBbResult.Success -> {
                    val user = _state.value.user ?: return@launch
                    val updated = user.copy(picture = result.url)
                    session.saveUser(updated)
                    // Save to Firebase RTDB
                    saveUserToFirebase(updated)
                    _state.update { it.copy(user = updated, isUploadingPhoto = false, toast = "✅ প্রোফাইল ছবি আপডেট হয়েছে") }
                }
                is ImgBbResult.Error -> {
                    _state.update { it.copy(isUploadingPhoto = false, photoUploadError = result.message) }
                }
            }
        }
    }

    // ── Update name ───────────────────────────────────────────

    fun updateName(name: String) {
        viewModelScope.launch {
            val user = _state.value.user ?: return@launch
            val updated = user.copy(name = name)
            session.saveUser(updated)
            saveUserToFirebase(updated)
            _state.update { it.copy(user = updated, toast = "✅ নাম আপডেট হয়েছে") }
        }
    }

    // ── Update profile (name + userType + classLevel) ─────────
    fun updateProfile(name: String, userType: String, classLevel: String) {
        viewModelScope.launch {
            val user = _state.value.user ?: return@launch
            val updated = user.copy(
                name       = name.trim().ifBlank { user.name },
                userType   = userType.trim().ifBlank { user.userType },
                classLevel = classLevel.trim()
            )
            session.saveUser(updated)
            saveProfileToFirebase(updated)
            _state.update { it.copy(user = updated, successMsg = "প্রোফাইল আপডেট হয়েছে") }
        }
    }

    // ── Dark mode ─────────────────────────────────────────────

    fun setDarkMode(on: Boolean) {
        viewModelScope.launch {
            session.setDarkMode(on)
            _state.update { it.copy(isDarkMode = on) }
        }
    }

    // ── Theme color ───────────────────────────────────────────

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            session.setThemeColor(theme.name.lowercase())
            _state.update { it.copy(appTheme = theme) }
        }
    }

    // ── Sound ─────────────────────────────────────────────────

    fun setSoundOff(off: Boolean) {
        viewModelScope.launch {
            session.setSoundOff(off)
            _state.update { it.copy(isSoundOff = off) }
        }
    }

    // ── Typing Settings (SettingsScreen "⌨️ টাইপিং সেটিংস") ────
    // FIX: আগে এখানে "quiz_prefs" SharedPreferences-এ লেখা হতো, কিন্তু
    // TypingPracticeScreen.kt আসলে session.getSmartTypingEnabled() (SessionManager-এর
    // DataStore) থেকে ফ্ল্যাগ পড়ে — ফলে Settings-এ টগল অন করলে state.smartTypingEnabled
    // সাথে সাথে বদলালেও (এবং সেটিংস স্ক্রিনে "চালু আছে" দেখালেও), TypingPracticeScreen
    // কখনো নতুন ফিচারগুলো (heatmap, Roadmap, Govt Mock, BCC, ইত্যাদি) দেখাতোই না, কারণ
    // সে যেই DataStore key পড়ছে সেটাতে কিছুই লেখা হচ্ছিল না। এখন session (DataStore)-এই
    // লেখা হচ্ছে, যাতে দুই পাশ একই সোর্স শেয়ার করে।
    fun setSmartTypingEnabled(on: Boolean) {
        viewModelScope.launch {
            session.setSmartTypingEnabled(on)
            _state.update { it.copy(smartTypingEnabled = on) }
        }
    }

    // ── লাইভ ফিচার হোল্ড/আনহোল্ড (Speed Plan Task 4) — off করলে সংশ্লিষ্ট নেভিগেশন
    // entry point (bottom-tab/MenuRow/বাটন) লুকানো থাকে, ফলে সেই স্ক্রিনের
    // ViewModel/Firebase listener কখনো তৈরিই হয় না (Compose viewModel() lazy) ──
    fun setChallengesEnabled(on: Boolean) {
        viewModelScope.launch {
            session.setChallengesEnabled(on)
            _state.update { it.copy(challengesEnabled = on) }
        }
    }

    fun setBuddyEnabled(on: Boolean) {
        viewModelScope.launch {
            session.setBuddyEnabled(on)
            _state.update { it.copy(buddyEnabled = on) }
        }
    }

    fun setTypingRaceEnabled(on: Boolean) {
        viewModelScope.launch {
            session.setTypingRaceEnabled(on)
            _state.update { it.copy(typingRaceEnabled = on) }
        }
    }

    fun setTypingLeaderboardEnabled(on: Boolean) {
        viewModelScope.launch {
            session.setTypingLeaderboardEnabled(on)
            _state.update { it.copy(typingLeaderboardEnabled = on) }
        }
    }

    fun setTypingTargetWpm(wpm: Int) {
        viewModelScope.launch {
            session.setTypingTargetWpm(wpm)
            _state.update { it.copy(typingTargetWpm = wpm.coerceIn(5, 200)) }
        }
    }

    fun setTypingSoundPreset(preset: String) {
        viewModelScope.launch {
            session.setTypingSoundPreset(preset)
            _state.update { it.copy(typingSoundPreset = preset) }
        }
    }

    // ── Offline mode (Firebase disconnect বাটন) ───────────────
    // অন করলে: কোনো নতুন Firebase read/write হবে না, সব লোকাল Room/DataStore
    // cache থেকে সার্ভ হবে, pending changes queue-তেই জমা থাকবে।
    // বন্ধ করলে: পরের সুবিধাজনক মুহূর্তে (app খোলা/reopen বা periodic sync-এ)
    // সব pending change আবার Firebase-এ sync হয়ে যাবে — কিছু হারাবে না।
    fun setOfflineMode(on: Boolean) {
        viewModelScope.launch {
            session.setOfflineMode(on)
            _state.update { it.copy(isOfflineMode = on, toast = if (on)
                "📴 অফলাইন মোড চালু — Firebase-এ কোনো ডাটা যাবে না, সব লোকালি সেভ হবে"
            else
                "☁️ অফলাইন মোড বন্ধ — Firebase সিঙ্ক আবার চালু হচ্ছে") }
            if (!on) {
                // অফলাইন মোড বন্ধ হওয়া মাত্র pending queue sync চালু করে দাও
                com.hanif.smartstudy.worker.SyncWorker.scheduleOneTime(getApplication())
            }
        }
    }

    // ── Written উত্তর AI-অটো-চেক: ৪টা প্রোভাইডারের API key + মডেল সেভ ──
    // একবার সেভ করলে DataStore-এ থেকে যায়, পরের বার আবার বসাতে হয় না।
    // চেষ্টার ক্রম Study/QBank উভয় জায়গাতেই: Groq → Mistral → Cerebras → Gemini।
    fun saveAiApiKeys(
        groq: String, mistral: String, cerebras: String, gemini: String,
        groqModel: String, mistralModel: String, cerebrasModel: String, geminiModel: String
    ) {
        viewModelScope.launch {
            val defaults = com.hanif.smartstudy.data.model.AiApiKeys()
            val keys = com.hanif.smartstudy.data.model.AiApiKeys(
                groq     = groq.trim(),
                mistral  = mistral.trim(),
                cerebras = cerebras.trim(),
                gemini   = gemini.trim(),
                groqModel     = groqModel.trim().ifBlank { defaults.groqModel },
                mistralModel  = mistralModel.trim().ifBlank { defaults.mistralModel },
                cerebrasModel = cerebrasModel.trim().ifBlank { defaults.cerebrasModel },
                geminiModel   = geminiModel.trim().ifBlank { defaults.geminiModel }
            )
            session.setAiApiKeys(keys)
            _state.update {
                it.copy(
                    groqApiKey     = keys.groq,
                    mistralApiKey  = keys.mistral,
                    cerebrasApiKey = keys.cerebras,
                    geminiApiKey   = keys.gemini,
                    groqModel      = keys.groqModel,
                    mistralModel   = keys.mistralModel,
                    cerebrasModel  = keys.cerebrasModel,
                    geminiModel    = keys.geminiModel,
                    aiKeysSavedMsg = "✅ API key ও মডেল সংরক্ষণ করা হয়েছে"
                )
            }
        }
    }

    fun clearAiKeysSavedMsg() {
        _state.update { it.copy(aiKeysSavedMsg = null) }
    }

    /** ── 🔍 Settings-এ "টেস্ট করুন" বাটন — সেভ করার আগেই key+মডেল সত্যিকারের
     * একটা রিকোয়েস্ট পাঠিয়ে যাচাই করে দেখে। এখনো সেভ না করা মান দিয়েও টেস্ট করা
     * যায় (তাই সরাসরি text field-এর মান পাঠানো হয়, session থেকে না)। ── */
    suspend fun testAiModel(provider: String, apiKey: String, model: String):
            com.hanif.smartstudy.data.remote.WrittenAnswerAiService.ModelTestResult {
        return com.hanif.smartstudy.data.remote.WrittenAnswerAiService.testProviderModel(provider, apiKey, model)
    }

    // ── Reminder ─────────────────────────────────────────────

    fun setReminder(on: Boolean, hour: Int = _state.value.reminderHour, minute: Int = _state.value.reminderMinute) {
        viewModelScope.launch {
            session.setReminder(on, hour, minute)
            _state.update { it.copy(isReminderOn = on, reminderHour = hour, reminderMinute = minute) }
            if (on) ReminderReceiver.scheduleMorning(ctx, hour, minute)
            else    ReminderReceiver.cancelMorning(ctx)
        }
    }

    /** Android 12+ এ exact alarm permission আছে কিনা — UI থেকে save করার আগে চেক করার জন্য */
    fun hasExactAlarmPermission(): Boolean = ReminderReceiver.canScheduleExactAlarms(ctx)

    fun setMorningReminder(on: Boolean, hour: Int = _state.value.morningHour, minute: Int = _state.value.morningMinute, repeatDaily: Boolean = _state.value.isMorningRepeat) {
        viewModelScope.launch {
            _state.update { it.copy(isMorningOn = on, morningHour = hour, morningMinute = minute, isMorningRepeat = repeatDaily) }
            if (on) ReminderReceiver.scheduleMorning(ctx, hour, minute, repeatDaily)
            else    ReminderReceiver.cancelMorning(ctx)
        }
    }

    fun setNightReminder(on: Boolean, hour: Int = _state.value.nightHour, minute: Int = _state.value.nightMinute, repeatDaily: Boolean = _state.value.isNightRepeat) {
        viewModelScope.launch {
            _state.update { it.copy(isNightOn = on, nightHour = hour, nightMinute = minute, isNightRepeat = repeatDaily) }
            if (on) ReminderReceiver.scheduleNight(ctx, hour, minute, repeatDaily)
            else    ReminderReceiver.cancelNight(ctx)
        }
    }

    fun setMiddayReminder(on: Boolean, hour: Int = _state.value.middayHour, minute: Int = _state.value.middayMinute, repeatDaily: Boolean = _state.value.isMiddayRepeat) {
        viewModelScope.launch {
            _state.update { it.copy(isMiddayOn = on, middayHour = hour, middayMinute = minute, isMiddayRepeat = repeatDaily) }
            if (on) ReminderReceiver.scheduleMidday(ctx, hour, minute, repeatDaily)
            else    ReminderReceiver.cancelMidday(ctx)
        }
    }

    fun setEveningReminder(on: Boolean, hour: Int = _state.value.eveningHour, minute: Int = _state.value.eveningMinute, repeatDaily: Boolean = _state.value.isEveningRepeat) {
        viewModelScope.launch {
            _state.update { it.copy(isEveningOn = on, eveningHour = hour, eveningMinute = minute, isEveningRepeat = repeatDaily) }
            if (on) ReminderReceiver.scheduleEvening(ctx, hour, minute, repeatDaily)
            else    ReminderReceiver.cancelEvening(ctx)
        }
    }

    // ── Data reset ────────────────────────────────────────────

    fun resetData() {
        viewModelScope.launch {
            // Clear quiz stats from cache
            // We don't clear user session, just stats
            ctx.getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
            loadAll()
            _state.update { it.copy(toast = "✅ ডেটা রিসেট হয়েছে") }
        }
    }

    // ── Logout ────────────────────────────────────────────────

    fun logout() {
        viewModelScope.launch {
            SmartStudyFirebaseService.updatePresence(ctx, false)
            session.clearUser()
            // FIX: content cache (disk + in-memory) ক্লিয়ার না করলে edit করা প্রশ্ন/তথ্য
            // logout-login করার পরেও পুরনো (stale) cache থেকেই দেখানো হতো।
            cache.clearCache()
            com.hanif.smartstudy.data.repository.ContentRepository.clearMemCache()
            _state.update { it.copy(user = null) }
        }
    }

    // ── Admin: load all users ─────────────────────────────────

    fun loadAllUsers() {
        if (!(_state.value.isAdmin)) return
        viewModelScope.launch {
            try {
                val json = fbGet("users") ?: return@launch
                val list = mutableListOf<Map<String, String>>()
                json.keys().forEach { key ->
                    val child = json.optJSONObject(key) ?: return@forEach
                    val map = mutableMapOf<String, String>()
                    child.keys().forEach { field -> map[field] = child.optString(field) }
                    list.add(map)
                }
                _state.update { it.copy(allUsers = list) }
            } catch (e: Exception) {
                Log.e("Admin", "loadAllUsers: ${e.message}")
            }
        }
    }

    // ── Profile photo upload (Uri version) ────────────────────
    fun uploadPhoto(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingPhoto = true, uploadProgress = true, photoUploadError = null) }
            try {
                val result = com.hanif.smartstudy.data.remote.ImgBbService.uploadImage(getApplication(), uri)
                when (result) {
                    is com.hanif.smartstudy.data.remote.ImgBbResult.Success -> {
                        val user = _state.value.user ?: return@launch
                        val updated = user.copy(picture = result.url)
                        session.saveUser(updated)
                        saveUserToFirebase(updated)
                        _state.update { it.copy(user = updated, isUploadingPhoto = false, uploadProgress = false, successMsg = "প্রোফাইল ছবি আপডেট হয়েছে") }
                    }
                    is com.hanif.smartstudy.data.remote.ImgBbResult.Error -> {
                        _state.update { it.copy(isUploadingPhoto = false, uploadProgress = false, error = result.message) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isUploadingPhoto = false, uploadProgress = false, error = e.message) }
            }
        }
    }

    // ── Admin: load list of phones that have debug logs ──
    // ⚠️ Phase 6 item 13 — AdminPage.kt-এর 📋 Logs ট্যাব সম্পূর্ণ সরানো হয়েছে (Admin Web
    // App-এ ডুপ্লিকেট ছিল), তাই এই ফাংশনের এখন কোনো caller নেই। ডিলিট না করে শুধু
    // @Deprecated রাখা হলো (safe cleanup পরে, MenuUiState.debugLogPhones/debugLogs field
    // দুটোও একইসাথে সরানো যাবে যখন নিশ্চিত হওয়া যাবে অন্য কোথাও লাগছে না)।
    @Deprecated("AdminPage.kt-এর 📋 Logs ট্যাব সরানো হয়েছে (Phase 6 item 13) — এখন unused")
    fun loadDebugLogPhones() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            try {
                val phones = com.hanif.smartstudy.data.remote.UserSyncService.fetchDebugLogPhones()
                _state.update { it.copy(debugLogPhones = phones) }
            } catch (e: Exception) {
                Log.e("Admin", "loadDebugLogPhones: ${e.message}")
            }
        }
    }

    // ── Admin: load logs for a phone (or "" = own phone) ──
    @Deprecated("AdminPage.kt-এর 📋 Logs ট্যাব সরানো হয়েছে (Phase 6 item 13) — এখন unused")
    fun loadDebugLogs(phone: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingLogs = true) }
            try {
                val targetPhone = phone.ifBlank { _state.value.user?.phone ?: "" }
                val logs = com.hanif.smartstudy.data.remote.UserSyncService.fetchDebugLogs(targetPhone)
                _state.update { it.copy(debugLogs = logs, isLoadingLogs = false) }
            } catch (e: Exception) {
                Log.e("Admin", "loadDebugLogs: ${e.message}")
                _state.update { it.copy(isLoadingLogs = false, error = "loadDebugLogs error: ${e.message}") }
            }
        }
    }

    // ── Clear success/error messages ─────────────────────────
    fun clearMsg() {
        _state.update { it.copy(successMsg = null, error = null, toast = null) }
    }

    // ── Load active users (Admin) ─────────────────────────────
    fun loadActiveUsers() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            try {
                val users = com.hanif.smartstudy.data.remote.UserSyncService.fetchActiveUsers()
                _state.update { it.copy(activeUsers = users, error = if (users.isEmpty()) "ইউজার লিস্ট খালি (${users.size})" else null) }
            } catch (e: Exception) {
                Log.e("Admin", "loadActiveUsers: ${e.message}")
                _state.update { it.copy(error = "loadActiveUsers error: ${e.message}") }
            }
        }
    }

    // ── Admin: send notification (title, body, targetPhone) ───
    fun adminSendNotification(title: String, body: String, targetPhone: String?) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                fbPost("broadcasts", mapOf(
                    "title"       to title,
                    "body"        to body,
                    "targetPhone" to (targetPhone ?: "ALL"),
                    "sentAt"      to System.currentTimeMillis(),
                    "sentBy"      to (_state.value.user?.phone ?: "admin")
                ))

                // আসল push — সরাসরি FCM v1 (GAS নেই)
                val cleanTarget = targetPhone?.trim().orEmpty()
                val pushOk = if (cleanTarget.isBlank() || cleanTarget.equals("ALL", ignoreCase = true)) {
                    // সবাইকে — "all_users" topic এ এক কলেই broadcast
                    com.hanif.smartstudy.data.remote.FcmAdminService.sendToTopic(
                        topic = "all_users", title = title, body = body,
                        data  = mapOf("type" to "admin_broadcast", "url" to "home")
                    )
                } else {
                    // নির্দিষ্ট একজন — তার token lookup করে সরাসরি পাঠাও
                    val token = com.hanif.smartstudy.data.remote.FcmAdminService.fetchTokenForPhone(cleanTarget)
                    if (token.isNullOrBlank()) false
                    else com.hanif.smartstudy.data.remote.FcmAdminService.sendToToken(
                        token = token, title = title, body = body,
                        data  = mapOf("type" to "admin_notify", "url" to "home")
                    )
                }

                _state.update {
                    it.copy(
                        isLoading  = false,
                        successMsg = if (pushOk) "নোটিফিকেশন পাঠানো হয়েছে" else "সেভ হয়েছে, কিন্তু push পাঠানো যায়নি (token পাওয়া যায়নি)"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "পাঠানো যায়নি: ${e.message}") }
            }
        }
    }

    // ── Admin: switch view to a user ──────────────────────────

    fun adminViewAs(phone: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            try {
                val cleanPhone = phone.replace("+", "")
                val json = fbGet("users/$cleanPhone")
                if (json != null) {
                    val map = mutableMapOf<String, Any>()
                    json.keys().forEach { map[it] = json.get(it) }
                    val user = User.fromFirebaseMap(map)
                    _state.update { it.copy(viewingAsUser = user, toast = "👁 ${user.name} হিসেবে দেখছেন") }
                } else {
                    _state.update { it.copy(toast = "❌ ইউজার পাওয়া যায়নি") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(toast = "❌ ইউজার লোড হয়নি") }
            }
        }
    }

    fun adminExitViewAs() {
        _state.update { it.copy(viewingAsUser = null) }
    }

    // ── Admin: Audience Tag Switch ────────────────────────────
    fun adminSwitchAudienceTag(tag: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            session.setAdminAudienceTag(tag)
            val label = if (tag.isBlank()) "Job Seeker (default)" else tag
            _state.update { it.copy(adminViewingTag = tag, toast = "🔄 দেখছেন: $label") }
        }
    }

    // ── Admin: Edit Question (offline-aware) ──────────────────
    fun adminEditQuestion(sheet: String, rowKey: String, fields: Map<String, String>, questionPreview: String = "") {
        android.util.Log.d("AdminEdit", "adminEditQuestion called: sheet=$sheet rowKey='$rowKey' fields=$fields isAdmin=${_state.value.isAdmin}")
        if (!_state.value.isAdmin) {
            android.util.Log.e("AdminEdit", "BLOCKED: user is not admin!")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isEditingQuestion = true, editSuccessMsg = null) }
            // ── FIX (মূল "instant না দেখানো" বাগ): আগে এখানে প্রথমে Firebase PATCH,
            // তারপর (sequentially) Google Sheet PATCH — এই দুইটা network কল শেষ
            // হওয়া পর্যন্ত অপেক্ষা করে, তারপর local cache patch + UI update হতো।
            // GAS/Apps Script কোল্ড-স্টার্টে কয়েক সেকেন্ড সহজেই লাগে, ফলে "instant"
            // এডিট আসলে ৫-১০ সেকেন্ড পর দেখা যেত। এখন adminDeleteQuestion-এর মতোই
            // প্যাটার্ন: local cache + UI সাথে সাথেই (network কলের আগে) patch হয়ে
            // যায়, আর Firebase/Sheet-এ save হওয়াটা সম্পূর্ণ ব্যাকগ্রাউন্ডে/silently
            // চলতে থাকে — ব্যর্থ হলে pending queue-তে auto ঢুকে যায়, UI আবার ছোঁয়া
            // লাগে না (যেহেতু ইউজার এমনিতেই edited ভ্যালুটা দেখছে)। ──
            try {
                val contentRepo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
                contentRepo.patchContentAndPersist(sheet, rowKey, fields)
                _state.update { it.copy(isEditingQuestion = false,
                    editSuccessMsg = "✅ আপডেট হয়েছে!", toast = "✅ প্রশ্ন সংরক্ষিত",
                    contentEditVersion = _state.value.contentEditVersion + 1) }
                android.util.Log.i("AdminEdit", "Instant local patch done: $sheet/$rowKey")
            } catch (e: Exception) {
                android.util.Log.e("AdminEdit", "Instant local patch FAILED: ${e.message}", e)
                _state.update { it.copy(isEditingQuestion = false,
                    error = "❌ সংরক্ষণ ব্যর্থ হয়েছে: ${e.message ?: "unknown error"}") }
                return@launch
            }

            // ── FIX ("এডিট/ডিলিট সব জায়গায় বন্ধ" বাগ, root cause): Room-এর টপিক-ক্যাশ
            // প্যাচ (patchRoomQuestion) আগে উপরের try ব্লকেই ছিল — এখানে কোনো কারণে exception
            // হলে (যেমন কোনো id Room-এ এখনো cache-ই হয়নি) পুরো catch ব্লক ট্রিগার হয়ে
            // `return@launch` চলে যেত, ফলে নিচের আসল ব্যাকগ্রাউন্ড GAS sync (যেটা সত্যিকারের
            // Sheet-এ লেখে) কখনোই রান হতো না — এডিট শুধু কখনো instant-ও দেখাতো না, আবার
            // Sheet-এও সেভ হতো না। এখন এটা সম্পূর্ণ আলাদা, নিজের try/catch-এ — ব্যর্থ হলেও
            // (শুধু ক্যাশ-প্যাচ মিস হবে, সেটা পরের রিফ্রেশে এমনিতেই ঠিক হয়ে যায়) নিচের
            // ব্যাকগ্রাউন্ড sync সবসময় চলবে। ──
            try {
                com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
                    .patchRoomQuestion(sheet, rowKey, fields)
            } catch (e: Exception) {
                android.util.Log.w("AdminEdit", "Room cache patch failed (non-fatal, sync continues): ${e.message}")
            }

            // ── Background sync (silent) — UI ইতিমধ্যে আপডেট দেখিয়ে দিয়েছে, তাই
            // এখানে exception হলেও শুধু queue-তে ফেলে রাখাই যথেষ্ট, UI ব্লক করার
            // দরকার নেই। ──
            launch {
                val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                try {
                    val cm = getApplication<android.app.Application>()
                        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                            as android.net.ConnectivityManager
                    val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    android.util.Log.d("AdminEdit", "background sync: isOnline=$isOnline")

                    if (isOnline) {
                        when (val r = adminUpdateField(sheet, rowKey, fields)) {
                            is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                android.util.Log.i("AdminEdit", "Background sync SUCCESS: $sheet/$rowKey")
                            }
                            is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                android.util.Log.e("AdminEdit", "Background sync FAILED: ${r.message} — queueing")
                                q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                                loadPendingEdits()
                            }
                        }
                    } else {
                        q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                        loadPendingEdits()
                        android.util.Log.d("AdminEdit", "OFFLINE — enqueued for later sync")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdminEdit", "EXCEPTION in background sync: ${e.message}", e)
                    try {
                        q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                        loadPendingEdits()
                    } catch (e2: Exception) {
                        android.util.Log.e("AdminEdit", "QUEUE ALSO FAILED: ${e2.message}", e2)
                    }
                }
            }
        }
    }

    // ── Admin: পুরো প্রশ্ন কার্ড ডিলিট করো (প্রশ্ন+অপশন+উত্তর+ব্যাখ্যা সবসহ) ──
    // adminEditQuestion এর মতোই প্যাটার্ন — লোকাল cache থেকে সাথে সাথেই সরিয়ে
    // দেওয়া হয় (তাই ইউজার/এডমিন সাথে সাথেই ফলাফল দেখে), আর Firebase সেভ
    // ব্যর্থ/অফলাইন হলে queue-তে রাখা হয় — নেট ফিরলে auto sync হয়ে Firebase
    // থেকেও ডিলিট হয়ে যাবে। এখনো কখনো Firebase-এ sync-ই হয়নি এমন লোকাল
    // প্রশ্ন (id "-local..." দিয়ে শুরু) হলে Firebase-এ কিছু পাঠানোর দরকারই নেই।
    fun adminDeleteQuestion(sheet: String, rowKey: String, questionPreview: String = "") {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isDeletingQuestion = true, deleteSuccessMsg = null) }
            val repo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
            val q    = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
            val isLocalOnly = rowKey.startsWith("-local")
            try {
                // যেভাবেই sync হোক না কেন — অ্যাপ থেকে সাথে সাথেই সরিয়ে দাও, আর এই
                // প্রশ্নের জন্য আগে থেকে থাকা কোনো pending edit/add থাকলে সেটাও বাতিল করো
                repo.removeContentAndPersist(sheet, rowKey)
                q.removePendingForQuestion(rowKey)

                // ── FIX ("ডিলিট করলে অ্যাপে সাথে সাথে হারিয়ে যায় না" বাগ, ঠিক এডিটের
                // মতোই root cause): Room-এর topicId-ভিত্তিক ক্যাশও (আসল টপিক-স্ক্রিন
                // যেটা পড়ে) সাথে সাথে মুছে ফেলা দরকার — removeContentAndPersist() শুধু
                // পুরনো bulk cache প্যাচ করে, Room অস্পর্শিত থাকতো, তাই Sheet থেকে
                // সত্যিই ডিলিট হয়ে গেলেও অ্যাপে প্রশ্নটা দেখা যেতেই থাকতো। এই কল
                // ব্যর্থ হলেও (নিজস্ব try-catch, নিচের catch-এ পড়বে না) যেন pending-queue/
                // background sync থেমে না যায়, তাই এখানেই আলাদা try-catch দিয়ে সামলানো। ──
                try {
                    repo.removeRoomQuestion(sheet, rowKey)
                } catch (e: Exception) {
                    android.util.Log.w("AdminDelete", "Room cache delete failed (non-fatal): ${e.message}")
                }

                if (isLocalOnly) {
                    // এই প্রশ্নটা কখনো Firebase-এ পাঠানোই হয়নি, তাই ডিলিট sync করারও দরকার নেই
                    loadPendingEdits()
                    _state.update { it.copy(isDeletingQuestion = false,
                        deleteSuccessMsg = "🗑️ প্রশ্ন কার্ডটি মুছে ফেলা হয়েছে",
                        contentEditVersion = it.contentEditVersion + 1) }
                    return@launch
                }

                val cm = getApplication<android.app.Application>()
                    .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                        as android.net.ConnectivityManager
                val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                    ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                if (isOnline) {
                    when (val r = adminDeleteRow(sheet, rowKey)) {
                        is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                            _state.update { it.copy(isDeletingQuestion = false,
                                deleteSuccessMsg = "✅ প্রশ্ন কার্ডটি ডিলিট হয়েছে!", toast = "🗑️ প্রশ্ন ডিলিট হয়েছে",
                                contentEditVersion = it.contentEditVersion + 1) }
                        }
                        is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                            // Online কিন্তু fail (যেমন Firebase quota শেষ) — queue এ রাখো,
                            // নেট/quota ঠিক হলে auto sync হয়ে Firebase থেকেও ডিলিট হয়ে যাবে
                            q.enqueueAdminDelete(sheet, rowKey, questionPreview)
                            loadPendingEdits()
                            _state.update { it.copy(isDeletingQuestion = false,
                                deleteSuccessMsg = "⚠️ অ্যাপ থেকে মুছে ফেলা হয়েছে — Firebase-এ sync বাকি",
                                error = "❌ ${r.message}",
                                contentEditVersion = it.contentEditVersion + 1) }
                        }
                    }
                } else {
                    q.enqueueAdminDelete(sheet, rowKey, questionPreview)
                    loadPendingEdits()
                    _state.update { it.copy(isDeletingQuestion = false,
                        deleteSuccessMsg = "📴 Offline এ মুছে ফেলা হয়েছে — net আসলে Firebase থেকেও auto ডিলিট হবে",
                        contentEditVersion = it.contentEditVersion + 1) }
                }
            } catch (e: Exception) {
                try {
                    repo.removeContentAndPersist(sheet, rowKey)
                    q.removePendingForQuestion(rowKey)
                    if (!isLocalOnly) q.enqueueAdminDelete(sheet, rowKey, questionPreview)
                    loadPendingEdits()
                    _state.update { it.copy(isDeletingQuestion = false,
                        deleteSuccessMsg = "📴 মুছে ফেলা হয়েছে — net আসলে auto sync হবে",
                        contentEditVersion = it.contentEditVersion + 1) }
                } catch (e2: Exception) {
                    _state.update { it.copy(isDeletingQuestion = false,
                        error = "❌ ডিলিট ব্যর্থ হয়েছে: ${e2.message ?: "unknown error"}") }
                }
            }
        }
    }

    // ── Pending admin edits লোড করো ──────────────────────────
    fun loadPendingEdits() {
        viewModelScope.launch {
            val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
            _state.update { it.copy(pendingEdits = q.getPendingAdminActions()) }
        }
    }

    // ── Manual sync now ───────────────────────────────────────
    fun syncPendingEditsNow() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isSyncingEdits = true, syncEditsMsg = null) }
            val q       = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
            val pending = q.getPendingAdminActions()
            if (pending.isEmpty()) {
                _state.update { it.copy(isSyncingEdits = false, syncEditsMsg = "✅ কোনো pending edit নেই") }
                return@launch
            }
            var successCount = 0
            var failCount    = 0
            val gson = com.google.gson.Gson()
            val repo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
            for (action in pending) {
                try {
                    val payload = gson.fromJson(action.payload, Map::class.java)
                    // ── sheet এখন প্রতিটা case-এর ভিতরেই আলাদাভাবে বের করা হয় — আগে এখানে
                    // একবারে বের করে পুরো block-এর জন্য গেট করা হতো, কিন্তু
                    // admin_delete_subject_topic-এ "sheet" না "sheets" (লিস্ট) থাকে, আর
                    // admin_move_topic-এ কোনো sheet ফিল্ডই নেই (topicId দিয়ে GAS নিজেই
                    // ঠিক sheet বের করে) — তাই আগের ব্লকেট extraction এই দুই টাইপকেই
                    // silently skip করে দিত (sync হতোই না) ──
                    when (action.type) {
                        "admin_edit_question" -> {
                            val sheet = payload["sheet"]?.toString() ?: continue
                            @Suppress("UNCHECKED_CAST")
                            val fields  = payload["fields"] as? Map<String, String> ?: continue
                            val questionId = payload["questionId"]?.toString() ?: continue
                            when (adminUpdateField(sheet, questionId, fields)) {
                                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                    repo.patchContentAndPersist(sheet, questionId, fields)
                                    q.remove(action.id); successCount++
                                }
                                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                    q.incrementRetry(action.id); failCount++
                                }
                            }
                        }
                        "admin_add_question" -> {
                            val sheet = payload["sheet"]?.toString() ?: continue
                            @Suppress("UNCHECKED_CAST")
                            val fields  = payload["fields"] as? Map<String, String> ?: continue
                            val localId = payload["localId"]?.toString() ?: continue
                            when (val r = adminAddRow(sheet, fields)) {
                                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                    // temp local id → আসল Firebase push key দিয়ে replace
                                    repo.replaceLocalIdAndPersist(sheet, localId, r.data)
                                    q.remove(action.id); successCount++
                                }
                                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                    q.incrementRetry(action.id); failCount++
                                }
                            }
                        }
                        "admin_delete_question" -> {
                            val sheet = payload["sheet"]?.toString() ?: continue
                            val questionId = payload["questionId"]?.toString() ?: continue
                            when (adminDeleteRow(sheet, questionId)) {
                                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                    // লোকাল cache থেকে তো ডিলিটের সময়ই সরানো হয়ে গেছে,
                                    // এখানে শুধু Firebase-এ পাঠানো সফল হলো এটাই নিশ্চিত করা
                                    q.remove(action.id); successCount++
                                }
                                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                    q.incrementRetry(action.id); failCount++
                                }
                            }
                        }
                        "admin_delete_subject_topic" -> {
                            @Suppress("UNCHECKED_CAST")
                            val sheets = (payload["sheets"] as? List<*>)?.map { it.toString() } ?: continue
                            val subject = payload["subject"]?.toString() ?: continue
                            val subTopic = payload["subTopic"]?.toString() ?: ""
                            val deleteSubTopic = payload["deleteSubTopic"]?.toString()?.toBoolean() ?: false
                            @Suppress("UNCHECKED_CAST")
                            val referenceIds = (payload["referenceIds"] as? Map<*, *>)
                                ?.entries?.associate { (k, v) -> k.toString() to v.toString() } ?: emptyMap()
                            when (val r = adminDeleteBySubjectBoth(sheets, subject, subTopic, deleteSubTopic)) {
                                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                    val refType = if (deleteSubTopic) "topics" else "subjects"
                                    referenceIds.values.toSet().forEach { rid ->
                                        if (rid.isNotBlank()) com.hanif.smartstudy.data.remote.GasContentService.deleteReferenceItem(refType, rid)
                                    }
                                    q.remove(action.id); successCount++
                                }
                                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                    q.incrementRetry(action.id); failCount++
                                }
                            }
                        }
                        "admin_move_questions" -> {
                            val sheet = payload["sheet"]?.toString() ?: continue
                            @Suppress("UNCHECKED_CAST")
                            val ids = (payload["ids"] as? List<*>)?.map { it.toString() } ?: continue
                            val newSubject = payload["newSubject"]?.toString() ?: continue
                            val newSubjectId = payload["newSubjectId"]?.toString() ?: continue
                            val newSubTopic = payload["newSubTopic"]?.toString() ?: continue
                            var newTopicId = payload["newTopicId"]?.toString() ?: ""
                            val createIfMissing = payload["createIfMissing"]?.toString()?.toBoolean() ?: false
                            var moveOk = true
                            if (createIfMissing || newTopicId.isBlank() || newTopicId.startsWith("-local")) {
                                when (val cr = com.hanif.smartstudy.data.remote.GasContentService
                                    .addReferenceItem("topics", newSubTopic, newSubjectId)) {
                                    is com.hanif.smartstudy.data.remote.ApiResult.Success -> newTopicId = cr.data
                                    is com.hanif.smartstudy.data.remote.ApiResult.Error -> moveOk = false
                                }
                            }
                            if (moveOk) {
                                when (com.hanif.smartstudy.data.remote.GasContentService
                                    .moveQuestions(sheet, ids, newSubject, newSubjectId, newSubTopic, newTopicId)) {
                                    is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                        q.remove(action.id); successCount++
                                    }
                                    is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                        q.incrementRetry(action.id); failCount++
                                    }
                                }
                            } else {
                                q.incrementRetry(action.id); failCount++
                            }
                        }
                        "admin_move_topic" -> {
                            val topicId = payload["topicId"]?.toString() ?: continue
                            val newSubjectId = payload["newSubjectId"]?.toString() ?: continue
                            val newSubjectName = payload["newSubjectName"]?.toString() ?: continue
                            val newSubTopicName = payload["newSubTopicName"]?.toString() ?: continue
                            val mergeTopicId = payload["mergeTopicId"]?.toString()?.ifBlank { null }
                            when (com.hanif.smartstudy.data.remote.GasContentService
                                .moveTopic(topicId, newSubjectId, newSubjectName, newSubTopicName, mergeTopicId)) {
                                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                    q.remove(action.id); successCount++
                                }
                                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                    q.incrementRetry(action.id); failCount++
                                }
                            }
                        }
                        else -> continue
                    }
                } catch (e: Exception) { failCount++ }
            }
            loadPendingEdits()
            val msg = when {
                failCount == 0 -> "✅ $successCount টি edit sync সফল!"
                successCount == 0 -> "❌ সব ($failCount টি) fail হয়েছে"
                else -> "⚠️ $successCount টি সফল, $failCount টি fail"
            }
            _state.update { it.copy(isSyncingEdits = false, syncEditsMsg = msg,
                contentEditVersion = if (successCount > 0) it.contentEditVersion + 1 else it.contentEditVersion) }
        }
    }

    fun clearSyncEditsMsg() { _state.update { it.copy(syncEditsMsg = null) } }

    // ── Admin: Options swap (offline-aware — adminEditQuestion এরই একটা shortcut,
    //    যেহেতু এটাও শুধু কিছু ফিল্ড patch করা, তাই একই offline/queue লজিক পায়) ──
    fun adminSwapOptions(sheet: String, rowKey: String, options: Map<String, String>, newAnswer: String, questionPreview: String = "") {
        if (!_state.value.isAdmin) return
        val fields = options.toMutableMap().apply { put("correct", newAnswer) }
        adminEditQuestion(sheet, rowKey, fields, questionPreview)
    }

    fun clearEditMsg() { _state.update { it.copy(editSuccessMsg = null) } }

    // ── Admin: Report Queue ───────────────────────────────────
    // ⚠️ Phase 6 item 13 — AdminPage.kt-এর 🚩 Reports ট্যাব সম্পূর্ণ সরানো হয়েছে (Admin
    // Web App-এ ডুপ্লিকেট ছিল), তাই এই দুটো ফাংশনের এখন কোনো caller নেই। ডিলিট না করে
    // শুধু @Deprecated রাখা হলো।
    @Deprecated("AdminPage.kt-এর 🚩 Reports ট্যাব সরানো হয়েছে (Phase 6 item 13) — এখন unused")
    fun loadPendingReports() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingReports = true) }
            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService.fetchPendingReports()) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success ->
                    _state.update { it.copy(reportedQuestions = r.data, isLoadingReports = false) }
                is com.hanif.smartstudy.data.remote.ApiResult.Error ->
                    _state.update { it.copy(isLoadingReports = false, error = "❌ ${r.message}") }
            }
        }
    }

    /** Report resolve + reporter কে notification পাঠাও */
    @Deprecated("AdminPage.kt-এর 🚩 Reports ট্যাব সরানো হয়েছে (Phase 6 item 13) — এখন unused")
    fun resolveReport(
        reportKey      : String,
        status         : String,
        userPhone      : String,
        questionSnippet: String = "",
        userName       : String = "",
        questionId     : String = "",
        tab            : String = ""
    ) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            when (com.hanif.smartstudy.data.remote.FirebaseDataService
                    .resolveReportAndNotify(reportKey, status, userPhone, questionSnippet, userName, questionId, tab)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            reportedQuestions = it.reportedQuestions.filter { r -> r.reportKey != reportKey },
                            toast = if (status == "resolved") "✅ Resolved — ইউজারকে নোটিফিকেশন গেছে" else "🗑 Dismissed"
                        )
                    }
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error ->
                    _state.update { it.copy(toast = "❌ Update ব্যর্থ") }
            }
        }
    }

    // ── Admin: Add New Question (offline-aware) ───────────────
    fun adminAddQuestion(sheet: String, fields: Map<String, String>) {
        if (!_state.value.isAdmin) return
        val questionPreview = fields["question"] ?: ""
        viewModelScope.launch {
            _state.update { it.copy(isAddingQuestion = true, addQuestionMsg = null) }
            val repo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
            // অস্থায়ী লোকাল id — background sync সফল হলে আসল Firebase/Sheet id
            // দিয়ে replace হয়ে যাবে (replaceLocalIdAndPersist দিয়ে), fail/offline
            // হলে এটাই থেকে যাবে যতক্ষণ না পরে sync হয়
            val localId = "-local" + System.currentTimeMillis().toString(36) +
                    (0..5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
            // ── FIX: adminEditQuestion-এর মতোই — আগে network কল (Firebase/Sheet)
            // শেষ হওয়া পর্যন্ত অপেক্ষা করে তারপর local cache-এ যোগ হতো, তাই "নতুন
            // প্রশ্ন যোগ" করাও কয়েক সেকেন্ড দেরি করে দেখাতো। এখন প্রথমে localId
            // দিয়ে সাথে সাথেই local cache + UI তে যোগ হয়ে যায়, network sync
            // সম্পূর্ণ ব্যাকগ্রাউন্ডে/silently চলে। ──
            try {
                repo.addContentAndPersist(sheet, localId, fields)
                _state.update { it.copy(isAddingQuestion = false,
                    addQuestionMsg = "✅ প্রশ্ন যোগ হয়েছে!",
                    contentEditVersion = it.contentEditVersion + 1) }
            } catch (e: Exception) {
                _state.update { it.copy(isAddingQuestion = false,
                    addQuestionMsg = "❌ সংরক্ষণ ব্যর্থ হয়েছে: ${e.message ?: "unknown error"}") }
                return@launch
            }

            launch {
                val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                try {
                    val cm = getApplication<android.app.Application>()
                        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                            as android.net.ConnectivityManager
                    val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                    if (isOnline) {
                        when (val r = adminAddRow(sheet, fields)) {
                            is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                // আসল server id দিয়ে অস্থায়ী localId replace করো —
                                // UI-তে প্রশ্নটা যেখানে ছিল সেখানেই থাকবে, শুধু id বদলাবে
                                repo.replaceLocalIdAndPersist(sheet, localId, r.data)
                            }
                            is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                                loadPendingEdits()
                            }
                        }
                    } else {
                        q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                        loadPendingEdits()
                    }
                } catch (e: Exception) {
                    try {
                        q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                        loadPendingEdits()
                    } catch (_: Exception) { }
                }
            }
        }
    }

    fun clearAddQuestionMsg() { _state.update { it.copy(addQuestionMsg = null) } }

    // ── Admin: Bulk Question Upload (offline-aware, local-first) ───────────────
    // admin-app এর BulkUploaderPage এর মতোই কাজ করে: একসাথে অনেক প্রশ্ন { } ব্লক বা
    // লাইন-বাই-লাইন পার্স করে একটার পর একটা adminAddQuestion-এর মতোই সেভ করে।
    // প্রতিটি আইটেম আগে সাথে সাথে লোকাল cache-এ (in-memory + disk) দেখানো হয়,
    // তারপর অনলাইন থাকলে Firebase-এ push করার চেষ্টা হয়; fail/offline হলে
    // PendingQueue-তে জমা থাকে এবং নেট/quota ঠিক হলে SyncWorker স্বয়ংক্রিয়ভাবে sync করে দেয়।
    private var bulkUploadJob: kotlinx.coroutines.Job? = null

    fun adminStopBulkUpload() { bulkUploadJob?.cancel() }

    fun adminClearBulkUploadResult() { _state.update { it.copy(bulkUploadResultMsg = null, bulkUploadLog = emptyList()) } }

    fun adminBulkAddQuestions(sheet: String, entries: List<Map<String, String>>) {
        if (!_state.value.isAdmin) return
        if (entries.isEmpty()) return
        bulkUploadJob?.cancel()
        bulkUploadJob = viewModelScope.launch {
            _state.update { it.copy(
                isBulkUploading = true, bulkUploadTotal = entries.size, bulkUploadDone = 0,
                bulkUploadSent = 0, bulkUploadFailed = 0, bulkUploadLog = emptyList(), bulkUploadResultMsg = null
            ) }
            val repo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
            val q    = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
            val cm = getApplication<android.app.Application>()
                .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

            var sent = 0
            var failed = 0
            val BATCH = 6
            var i = 0
            while (i < entries.size) {
                ensureActive()
                val batch = entries.subList(i, minOf(i + BATCH, entries.size))

                // ধাপ ১: নেটওয়ার্ক কল (Firebase push) গুলো একসাথে সমান্তরালে চালাও — দ্রুত হওয়ার জন্য
                val netResults = batch.map { fields ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        val isOnline = try {
                            cm.getNetworkCapabilities(cm.activeNetwork)
                                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                        } catch (e: Exception) { false }
                        if (!isOnline) {
                            fields to null
                        } else {
                            val r = try {
                                adminAddRow(sheet, fields)
                            } catch (e: Exception) {
                                com.hanif.smartstudy.data.remote.ApiResult.Error(e.message ?: "unknown")
                            }
                            fields to r
                        }
                    }
                }.map { it.await() }

                // ধাপ ২: লোকাল cache (in-memory + disk) এ লেখা — একটার পর একটা (সমান্তরাল লিখলে
                // ContentRepository-র in-memory cache race-condition-এ পড়তে পারে বলে সিরিয়ালি করা হলো)
                netResults.forEach { (fields, r) ->
                    val questionPreview = fields["question"] ?: ""
                    val localId = "-local" + System.currentTimeMillis().toString(36) +
                            (0..5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
                    val (ok, logLine) = try {
                        when (r) {
                            is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                repo.addContentAndPersist(sheet, r.data, fields)
                                true to "✔ ${questionPreview.take(45)}"
                            }
                            is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                repo.addContentAndPersist(sheet, localId, fields)
                                q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                                false to "⚠ সংরক্ষিত (sync বাকি): ${questionPreview.take(35)} [${r.message}]"
                            }
                            null -> {
                                repo.addContentAndPersist(sheet, localId, fields)
                                q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                                false to "📴 অফলাইনে সংরক্ষিত: ${questionPreview.take(40)}"
                            }
                        }
                    } catch (e: Exception) {
                        false to "❌ ব্যর্থ: ${questionPreview.take(35)} [${e.message ?: "unknown"}]"
                    }
                    if (ok) sent++ else failed++
                    _state.update {
                        it.copy(
                            bulkUploadDone   = it.bulkUploadDone + 1,
                            bulkUploadSent   = sent,
                            bulkUploadFailed = failed,
                            bulkUploadLog    = (it.bulkUploadLog + logLine).takeLast(100),
                            contentEditVersion = it.contentEditVersion + 1
                        )
                    }
                }
                i += BATCH
            }
            loadPendingEdits()
            _state.update { it.copy(
                isBulkUploading = false,
                bulkUploadResultMsg = "✅ সম্পন্ন — মোট ${entries.size}টি, সফল $sent টি" +
                    (if (failed > 0) ", অফলাইন/pending $failed টি (auto sync হবে)" else "")
            ) }
        }
    }

    // ── Admin: Subject/SubTopic taxonomy লোড করো (dropdown suggestion এর জন্য) ──
    // Rename/Bulk/AddQuestion — এই তিনটা tab এই একই taxonomy share করে, তাই
    // একবার লোড করে state এ cache রাখা হয় (পুরো content fetch করা লাগে,
    // তাই বারবার না করাই ভালো — admin চাইলে refresh icon দিয়ে আবার লোড করবে)।
    fun loadAdminTaxonomy(forceRefresh: Boolean = false) {
        if (!_state.value.isAdmin) return
        if (_state.value.adminSubjectsBySheet.isNotEmpty() && !forceRefresh) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingTaxonomy = true) }
            val repo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
            when (val r = repo.getContent(forceRefresh)) {
                is com.hanif.smartstudy.data.repository.DataState.Success -> {
                    val content = r.data
                    val subjectsBySheet = mutableMapOf<String, List<String>>()
                    val subTopicsByKey  = mutableMapOf<String, MutableSet<String>>()

                    fun <T> index(sheet: String, items: List<T>, subjectOf: (T) -> String?, subTopicOf: (T) -> String?) {
                        val subjects = sortedSetOf<String>()
                        items.forEach { item ->
                            val subj = subjectOf(item)?.trim().orEmpty()
                            if (subj.isBlank()) return@forEach
                            subjects.add(subj)
                            val sub = subTopicOf(item)?.trim().orEmpty()
                            if (sub.isNotBlank()) {
                                subTopicsByKey.getOrPut("$sheet|$subj") { sortedSetOf() }.add(sub)
                            }
                        }
                        subjectsBySheet[sheet] = subjects.toList()
                    }

                    index("Quiz",  content.quiz,  { it.subject }, { it.subTopic })
                    index("QBank", content.qbank, { it.subject }, { it.subTopic })
                    index("Study", content.study, { it.subject }, { it.subTopic })

                    _state.update { it.copy(
                        isLoadingTaxonomy    = false,
                        adminSubjectsBySheet = subjectsBySheet,
                        adminSubTopicsByKey  = subTopicsByKey.mapValues { (_, v) -> v.toList() }
                    )}
                }
                is com.hanif.smartstudy.data.repository.DataState.Error -> {
                    _state.update { it.copy(isLoadingTaxonomy = false) }
                }
                else -> _state.update { it.copy(isLoadingTaxonomy = false) }
            }
        }
    }

    // ── Admin: Bulk Audience Update ───────────────────────────
    // ⚠️ Phase 6 item 13 — AdminPage.kt-এর 🌐 Bulk Tag ট্যাব সম্পূর্ণ সরানো হয়েছে (Admin
    // Web App-এ ডুপ্লিকেট + পুরনো raw-text subject/sub_topic matching-এর ওপর নির্ভরশীল
    // ছিল, দেখো FirebaseDataService.adminBulkAudienceUpdate-এর @Deprecated নোট) — তাই এই
    // wrapper-এর এখন কোনো caller নেই।
    @Deprecated("AdminPage.kt-এর 🌐 Bulk Tag ট্যাব সরানো হয়েছে (Phase 6 item 13) — এখন unused")
    fun adminBulkAudienceUpdate(sheet: String, subject: String, subTopic: String, newTag: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isBulkUpdating = true, bulkUpdateMsg = null) }
            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService
                    .adminBulkAudienceUpdate(sheet, subject, subTopic, newTag)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    cache.clearCache()
                    com.hanif.smartstudy.data.repository.ContentRepository.clearMemCache()
                    _state.update { it.copy(isBulkUpdating = false,
                        bulkUpdateMsg = "✅ ${r.data}টি প্রশ্ন → \"$newTag\"",
                        contentEditVersion = it.contentEditVersion + 1) }
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error ->
                    _state.update { it.copy(isBulkUpdating = false,
                        bulkUpdateMsg = "❌ ${r.message}") }
            }
        }
    }

    fun clearBulkMsg() { _state.update { it.copy(bulkUpdateMsg = null) } }

    // ── Rename-ও এখন adminUpdateField/adminDeleteRow/adminAddRow-এর মতোই dual-write:
    // Sheet কনফিগার থাকলে সেটাই প্রাইমারি ফলাফল, Firebase শুধু best-effort মিরর ──
    // ── Phase 6 পূর্ণ কাটওভার — দেখো adminUpdateField-এর ওপরের নোট, একই কারণ প্রযোজ্য ──
    private suspend fun adminRenameBoth(
        sheets: List<String>, oldSubject: String, oldSubTopic: String,
        newName: String, renameSubTopic: Boolean
    ): com.hanif.smartstudy.data.remote.ApiResult<Int> {
        if (!com.hanif.smartstudy.data.remote.GasContentService.isConfigured()) {
            return com.hanif.smartstudy.data.remote.ApiResult.Error("Google Sheet কনফিগার করা নেই")
        }
        return when (val sheetResult = com.hanif.smartstudy.data.remote.GasContentService
                .renameSubjectOrTopic(sheets, oldSubject, oldSubTopic, newName, renameSubTopic)) {
            is com.hanif.smartstudy.data.remote.ApiResult.Success -> sheetResult
            is com.hanif.smartstudy.data.remote.ApiResult.Error -> com.hanif.smartstudy.data.remote.ApiResult.Error("Sheet: ${sheetResult.message}")
        }
    }

    // ── Admin: Rename Subject/SubTopic ────────────────────────
    fun adminRenameSubjectOrTopic(
        sheets         : List<String>,
        oldSubject     : String,
        oldSubTopic    : String,
        newName        : String,
        renameSubTopic : Boolean
    ) {
        if (!_state.value.isAdmin) return
        if (sheets.isEmpty() || oldSubject.isBlank() || newName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isRenaming = true, renameMsg = null) }
            when (val r = adminRenameBoth(sheets, oldSubject, oldSubTopic, newName, renameSubTopic)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    cache.clearCache()
                    com.hanif.smartstudy.data.repository.ContentRepository.clearMemCache()
                    val what = if (renameSubTopic) "অধ্যায়" else "বিষয়"
                    _state.update { it.copy(isRenaming = false,
                        renameMsg = "✅ ${r.data}টি প্রশ্নে $what \"$newName\" এ পরিবর্তিত হয়েছে",
                        contentEditVersion = it.contentEditVersion + 1) }
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error ->
                    _state.update { it.copy(isRenaming = false, renameMsg = "❌ ${r.message}") }
            }
        }
    }

    fun clearRenameMsg() { _state.update { it.copy(renameMsg = null) } }

    // ── Delete-ও rename এর মতোই dual-write: Sheet কনফিগার থাকলে প্রাইমারি ফলাফল,
    // Firebase শুধু best-effort মিরর ──
    // ── Phase 6 পূর্ণ কাটওভার — দেখো adminUpdateField-এর ওপরের নোট, একই কারণ প্রযোজ্য ──
    private suspend fun adminDeleteBySubjectBoth(
        sheets: List<String>, subject: String, subTopic: String, deleteSubTopic: Boolean
    ): com.hanif.smartstudy.data.remote.ApiResult<Int> {
        if (!com.hanif.smartstudy.data.remote.GasContentService.isConfigured()) {
            return com.hanif.smartstudy.data.remote.ApiResult.Error("Google Sheet কনফিগার করা নেই")
        }
        return when (val sheetResult = com.hanif.smartstudy.data.remote.GasContentService
                .deleteBySubjectOrTopic(sheets, subject, subTopic, deleteSubTopic)) {
            is com.hanif.smartstudy.data.remote.ApiResult.Success -> sheetResult
            is com.hanif.smartstudy.data.remote.ApiResult.Error -> com.hanif.smartstudy.data.remote.ApiResult.Error("Sheet: ${sheetResult.message}")
        }
    }

    // ── Admin: Subject/SubTopic-এর সব প্রশ্ন + নিজেই একসাথে ডিলিট (destructive — নিশ্চিত
    //    হয়ে কল করবে) ──
    // ── FIX ("সাবজেক্ট/টপিক Delete হচ্ছে না" বাগ): আগে এখানে সরাসরি
    // adminDeleteBySubjectBoth() (পুরো Sheet fetch + deleteByIds, network-heavy) কল করে
    // অপেক্ষা করা হতো, তারপর সফল হলে তবেই UI "ডিলিট হয়েছে" দেখাতো — GAS cold-start/বড়
    // Subject-এ এটা কয়েক সেকেন্ড-মিনিট লাগতে পারত বলে মনে হতো ডিলিট কাজই করছে না। আর
    // সফল হলেও শুধু প্রশ্ন-রো মুছত, Subject/Topic নিজেই (SubjectListScreen যেই Room
    // reference-টেবিল থেকে পড়ে) খালি অবস্থায় তালিকায় থেকে যেত। এখন adminEditQuestion/
    // adminDeleteQuestion-এর মতোই প্যাটার্ন: প্রথমে (network-এর আগেই) লোকাল সব জায়গা
    // (bulk cache + Room questions + Room reference টেবিল, তার আন্ডারের সব প্রশ্নসহ)
    // থেকে সরিয়ে সাথে সাথেই UI আপডেট দেখানো হয়, আসল Sheet delete সম্পূর্ণ ব্যাকগ্রাউন্ডে
    // চলে — ব্যর্থ/অফলাইন হলে pending queue-তে ঢুকে নেট ফিরলে auto sync হয়ে যাবে। ──
    fun adminDeleteSubjectOrTopic(
        sheets         : List<String>,
        subject        : String,
        subTopic       : String,
        deleteSubTopic : Boolean
    ) {
        if (!_state.value.isAdmin) return
        if (sheets.isEmpty() || subject.isBlank() || (deleteSubTopic && subTopic.isBlank())) return
        viewModelScope.launch {
            _state.update { it.copy(isDeletingSubject = true, deleteSubjectMsg = null) }
            val contentRepo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
            val what = if (deleteSubTopic) "\"$subTopic\" অধ্যায়ের" else "\"$subject\" বিষয়ের"

            // sheet -> resolved subjectId/topicId (Room reference-টেবিলে পাওয়া গেলে) —
            // ব্যাকগ্রাউন্ড sync-এ Sheet-এর Subjects/Topics ট্যাব থেকেও একইভাবে ডিলিট করতে লাগবে
            val resolvedIds = mutableMapOf<String, String>()
            try {
                for (sheet in sheets) {
                    contentRepo.removeContentBySubjectAndPersist(sheet, subject, subTopic, deleteSubTopic)
                    try {
                        contentRepo.removeRoomQuestionsBySubject(sheet, subject, subTopic, deleteSubTopic)
                    } catch (e: Exception) {
                        android.util.Log.w("AdminDeleteSubject", "Room questions purge failed for $sheet (non-fatal): ${e.message}")
                    }
                    try {
                        contentRepo.removeRoomReferenceForSubjectOrTopic(sheet, subject, subTopic, deleteSubTopic)
                            ?.let { resolvedIds[sheet] = it }
                    } catch (e: Exception) {
                        android.util.Log.w("AdminDeleteSubject", "Room reference purge failed for $sheet (non-fatal): ${e.message}")
                    }
                }
                _state.update { it.copy(isDeletingSubject = false,
                    deleteSubjectMsg = "✅ $what সব প্রশ্ন মুছে ফেলা হয়েছে",
                    toast = "🗑️ $what সব প্রশ্ন মুছে ফেলা হয়েছে",
                    contentEditVersion = it.contentEditVersion + 1) }
                android.util.Log.i("AdminDeleteSubject", "Instant local delete done: $sheets/$subject/$subTopic")
            } catch (e: Exception) {
                android.util.Log.e("AdminDeleteSubject", "Instant local delete FAILED: ${e.message}", e)
                _state.update { it.copy(isDeletingSubject = false,
                    deleteSubjectMsg = "❌ ডিলিট ব্যর্থ হয়েছে: ${e.message ?: "unknown error"}") }
                return@launch
            }

            // ── Background sync (silent) — UI ইতিমধ্যে আপডেট দেখিয়ে দিয়েছে, তাই এখানে
            // ব্যর্থ হলেও শুধু pending queue-তে ফেলে রাখাই যথেষ্ট, UI ব্লক করার দরকার নেই। ──
            launch {
                val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                try {
                    val cm = getApplication<android.app.Application>()
                        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                            as android.net.ConnectivityManager
                    val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    android.util.Log.d("AdminDeleteSubject", "background sync: isOnline=$isOnline")

                    if (isOnline) {
                        when (val r = adminDeleteBySubjectBoth(sheets, subject, subTopic, deleteSubTopic)) {
                            is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                android.util.Log.i("AdminDeleteSubject", "Background sheet delete SUCCESS: $sheets/$subject/$subTopic (${r.data} প্রশ্ন)")
                                // deleteByIds শুধু প্রশ্ন-রো মোছে, Subjects/Topics ট্যাব স্পর্শ করে
                                // না — তাই আলাদা করে (id-ম্যাচ, নিরাপদ) সেই এন্ট্রিও ডিলিট করা হলো
                                val refType = if (deleteSubTopic) "topics" else "subjects"
                                resolvedIds.values.toSet().forEach { rid ->
                                    when (val rr = com.hanif.smartstudy.data.remote.GasContentService.deleteReferenceItem(refType, rid)) {
                                        is com.hanif.smartstudy.data.remote.ApiResult.Success ->
                                            android.util.Log.i("AdminDeleteSubject", "Reference item deleted: $refType/$rid")
                                        is com.hanif.smartstudy.data.remote.ApiResult.Error ->
                                            android.util.Log.w("AdminDeleteSubject", "Reference item delete failed: ${rr.message}")
                                    }
                                }
                                cache.clearCache()
                                com.hanif.smartstudy.data.repository.ContentRepository.clearMemCache()
                                // Room reference টেবিল (Subjects/Topics/SubTopics) জোর করে আবার
                                // sync — যাতে অন্য কোনো ডিভাইস/সেশনেও নিশ্চিতভাবে হালনাগাদ দেখায়
                                contentRepo.syncReferenceData(force = true)
                            }
                            is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                android.util.Log.e("AdminDeleteSubject", "Background sheet delete FAILED: ${r.message} — queueing")
                                q.enqueueAdminDeleteSubjectTopic(sheets, subject, subTopic, deleteSubTopic, resolvedIds)
                                loadPendingEdits()
                            }
                        }
                    } else {
                        q.enqueueAdminDeleteSubjectTopic(sheets, subject, subTopic, deleteSubTopic, resolvedIds)
                        loadPendingEdits()
                        android.util.Log.d("AdminDeleteSubject", "OFFLINE — enqueued for later sync")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdminDeleteSubject", "EXCEPTION in background sync: ${e.message}", e)
                    try {
                        q.enqueueAdminDeleteSubjectTopic(sheets, subject, subTopic, deleteSubTopic, resolvedIds)
                        loadPendingEdits()
                    } catch (e2: Exception) {
                        android.util.Log.e("AdminDeleteSubject", "QUEUE ALSO FAILED: ${e2.message}", e2)
                    }
                }
            }
        }
    }

    fun clearDeleteSubjectMsg() { _state.update { it.copy(deleteSubjectMsg = null) } }

    fun clearMoveContentMsg() { _state.update { it.copy(moveContentMsg = null) } }

    // ═════════════════════════════════════════════════════════════════════════
    // Admin "Move" (ফাইল ম্যানেজারের মতো) — adminDeleteSubjectOrTopic-এর মতোই instant-
    // then-background প্যাটার্ন: নেটওয়ার্কের আগেই লোকাল সব জায়গা (bulk cache + Room
    // questions + Room reference টেবিল) থেকে move হয়ে যায়, আসল Sheet sync ব্যাকগ্রাউন্ডে
    // চলে — ব্যর্থ/অফলাইন হলে pending queue-তে ঢুকে নেট ফিরলে auto sync হয়ে যাবে।
    // ═════════════════════════════════════════════════════════════════════════

    /** এক বা একাধিক প্রশ্ন (ids) অন্য Subject/Topic-এ move করে। destination Topic
     *  আগে থেকে থাকতেই হবে (নতুন Topic বানাতে হলে আগে সেটা বানাতে হবে)। প্রশ্নের
     *  নিজের id অপরিবর্তিত থাকে (তাই bookmark/quiz-history/Exam_Appearances ভাঙে না)। */
    fun adminMoveQuestions(
        sheet          : String,
        ids            : List<String>,
        newSubjectName : String,
        newSubTopicName: String
    ) {
        if (!_state.value.isAdmin) return
        if (ids.isEmpty() || newSubjectName.isBlank() || newSubTopicName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isMovingContent = true, moveContentMsg = null) }
            val contentRepo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())

            // ── destination নাম থেকে আসল subjectId রিজলভ (GAS action id-ভিত্তিক) ──
            val newSubjectId = contentRepo.resolveSubjectId(sheet, newSubjectName)
            if (newSubjectId == null) {
                _state.update { it.copy(isMovingContent = false, moveContentMsg = "❌ \"$newSubjectName\" নামে কোনো Subject পাওয়া যায়নি") }
                return@launch
            }

            // ── destination Topic না থাকলে — এরর না দিয়ে সাথে সাথেই নতুন বানানো হয়
            // (adminAddQuestion()-এর অস্থায়ী localId প্যাটার্নের মতোই: প্রথমে
            // "-localT..." দিয়ে instant local reference-এ যোগ, ব্যাকগ্রাউন্ডে GAS-এর
            // addReferenceItem দেওয়া আসল id দিয়ে replace) ──
            var newTopicId = contentRepo.resolveTopicId(newSubjectId, newSubTopicName)
            var isNewTopic = false
            var localTempTopicId: String? = null
            if (newTopicId == null) {
                isNewTopic = true
                localTempTopicId = "-localT" + System.currentTimeMillis().toString(36) +
                        (0..5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
                try {
                    contentRepo.addRoomTopicLocal(localTempTopicId, newSubjectId, newSubTopicName)
                } catch (e: Exception) {
                    android.util.Log.w("AdminMove", "Local temp topic insert failed (non-fatal): ${e.message}")
                }
                newTopicId = localTempTopicId
            }
            val finalNewTopicId = newTopicId

            // ── FIX ("move করার পর সোর্স টপিকের কাউন্ট রিয়েল-টাইম আপডেট হচ্ছিল না"):
            // moveRoomQuestionsByIds()-কে সোর্স টপিকের rowCount সাথে সাথে ঠিক করতে হলে
            // ওই ids গুলো move হওয়ার *আগে* কোন topicId-তে ছিল সেটা জানা লাগে — এখানে
            // আগে কখনো resolve করা হতো না। এখন patch/move শুরু করার ঠিক আগে (তখনো
            // পুরনো subject/topic-ই আছে) Room থেকে ওই ids-এর আসল প্রশ্ন এনে তাদের
            // subject/subTopic দিয়ে oldTopicId বের করে নেওয়া হচ্ছে — audience-filter
            // ছাড়াই (admin-only ফাংশন, getAdminAudienceTag() দিয়ে সব দেখা যায়)। ──
            val oldTopicId = try {
                val adminTag = session.getAdminAudienceTag()
                val sourceItems = contentRepo.getRoomQuestionsByIds(sheet, ids, adminTag)
                val firstSource = sourceItems.firstOrNull()
                if (firstSource != null) {
                    val oldSubjectId = contentRepo.resolveSubjectId(sheet, firstSource.subject)
                    oldSubjectId?.let { contentRepo.resolveTopicId(it, firstSource.subTopic) }
                } else null
            } catch (e: Exception) {
                android.util.Log.w("AdminMove", "oldTopicId resolve failed (non-fatal, source rowCount won't live-refresh): ${e.message}")
                null
            }

            try {
                contentRepo.patchContentBulkAndPersist(sheet, ids.toSet(), mapOf("subject" to newSubjectName, "sub_topic" to newSubTopicName))
                try {
                    contentRepo.moveRoomQuestionsByIds(sheet, ids, newSubjectName, newSubTopicName, newSubjectId, finalNewTopicId, oldTopicId)
                } catch (e: Exception) {
                    android.util.Log.w("AdminMove", "Room questions move failed (non-fatal): ${e.message}")
                }
                val newTopicNote = if (isNewTopic) " (নতুন Topic তৈরি হয়েছে)" else ""
                _state.update { it.copy(isMovingContent = false,
                    moveContentMsg = "✅ ${ids.size}টি প্রশ্ন \"$newSubjectName\" › \"$newSubTopicName\"-এ সরানো হয়েছে$newTopicNote",
                    toast = "📦 ${ids.size}টি প্রশ্ন সরানো হয়েছে",
                    contentEditVersion = it.contentEditVersion + 1) }
                android.util.Log.i("AdminMove", "Instant local move done: $sheet/${ids.size} → $newSubjectName/$newSubTopicName")
            } catch (e: Exception) {
                android.util.Log.e("AdminMove", "Instant local move FAILED: ${e.message}", e)
                _state.update { it.copy(isMovingContent = false, moveContentMsg = "❌ Move ব্যর্থ হয়েছে: ${e.message ?: "unknown error"}") }
                return@launch
            }

            launch {
                val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                try {
                    val cm = getApplication<android.app.Application>()
                        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                            as android.net.ConnectivityManager
                    val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                    if (isOnline) {
                        var realTopicId = finalNewTopicId
                        if (isNewTopic) {
                            when (val cr = com.hanif.smartstudy.data.remote.GasContentService
                                .addReferenceItem("topics", newSubTopicName, newSubjectId)) {
                                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                                    realTopicId = cr.data
                                    // ── লোকাল অস্থায়ী topicId আসল id দিয়ে replace —
                                    // adminAddQuestion()-এর replaceLocalIdAndPersist()-এর মতোই ──
                                    try {
                                        contentRepo.replaceRoomTopicId(localTempTopicId!!, realTopicId)
                                        contentRepo.replaceRoomQuestionsTopicId(sheet, localTempTopicId, realTopicId)
                                    } catch (e: Exception) {
                                        android.util.Log.w("AdminMove", "Local temp topic id replace failed (non-fatal): ${e.message}")
                                    }
                                }
                                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                    android.util.Log.e("AdminMove", "addReferenceItem FAILED: ${cr.message} — queueing")
                                    q.enqueueAdminMoveQuestions(sheet, ids, newSubjectName, newSubjectId, newSubTopicName, "", createIfMissing = true)
                                    loadPendingEdits()
                                    return@launch
                                }
                            }
                        }
                        when (val r = com.hanif.smartstudy.data.remote.GasContentService
                            .moveQuestions(sheet, ids, newSubjectName, newSubjectId, newSubTopicName, realTopicId)) {
                            is com.hanif.smartstudy.data.remote.ApiResult.Success ->
                                android.util.Log.i("AdminMove", "Background sheet move SUCCESS: ${r.data}টি প্রশ্ন")
                            is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                android.util.Log.e("AdminMove", "Background sheet move FAILED: ${r.message} — queueing")
                                q.enqueueAdminMoveQuestions(sheet, ids, newSubjectName, newSubjectId, newSubTopicName, realTopicId)
                                loadPendingEdits()
                            }
                        }
                    } else {
                        q.enqueueAdminMoveQuestions(sheet, ids, newSubjectName, newSubjectId, newSubTopicName, finalNewTopicId, createIfMissing = isNewTopic)
                        loadPendingEdits()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdminMove", "EXCEPTION in background sync: ${e.message}", e)
                    try {
                        q.enqueueAdminMoveQuestions(sheet, ids, newSubjectName, newSubjectId, newSubTopicName, finalNewTopicId, createIfMissing = isNewTopic)
                        loadPendingEdits()
                    } catch (e2: Exception) {
                        android.util.Log.e("AdminMove", "QUEUE ALSO FAILED: ${e2.message}", e2)
                    }
                }
            }
        }
    }

    /** একটা পুরো Topic (তার আন্ডারের সব প্রশ্নসহ) অন্য Subject-এ move করে। destination
     *  Subject-এ same নামের (newSubTopicName) Topic আগে থেকে থাকলে auto-merge হয়ে যায়
     *  (topic_id-ও সেই existing id-তে বদলে যায়) — নাহলে topic_id অপরিবর্তিত রেখে শুধু
     *  reparent হয়। */
    fun adminMoveTopic(
        sheet          : String,
        oldSubject     : String,
        oldSubTopic    : String,
        newSubjectName : String,
        newSubTopicName: String = oldSubTopic
    ) {
        if (!_state.value.isAdmin) return
        if (oldSubject.isBlank() || oldSubTopic.isBlank() || newSubjectName.isBlank() || newSubTopicName.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isMovingContent = true, moveContentMsg = null) }
            val contentRepo = com.hanif.smartstudy.data.repository.ContentRepository(getApplication())

            // ── সোর্স Topic-এর topicId রিজলভ (Room reference-টেবিল থেকে) ──
            val oldSubjectId = contentRepo.resolveSubjectId(sheet, oldSubject)
            val topicId = oldSubjectId?.let { contentRepo.resolveTopicId(it, oldSubTopic) }
            if (topicId == null) {
                _state.update { it.copy(isMovingContent = false, moveContentMsg = "❌ \"$oldSubject\" › \"$oldSubTopic\" রিজলভ করা যায়নি — একবার রিফ্রেশ করে আবার চেষ্টা করুন") }
                return@launch
            }
            // ── destination Subject রিজলভ, আর same নামের Topic থাকলে auto-merge target ──
            val newSubjectId = contentRepo.resolveSubjectId(sheet, newSubjectName)
            if (newSubjectId == null) {
                _state.update { it.copy(isMovingContent = false, moveContentMsg = "❌ \"$newSubjectName\" নামে কোনো Subject পাওয়া যায়নি") }
                return@launch
            }
            if (newSubjectId == oldSubjectId && newSubTopicName.trim().equals(oldSubTopic.trim(), ignoreCase = true)) {
                _state.update { it.copy(isMovingContent = false, moveContentMsg = "ℹ️ এটা এখন যেখানে আছে, সেখানেই আছে — কিছু বদলায়নি") }
                return@launch
            }
            val mergeTopicId = contentRepo.resolveTopicId(newSubjectId, newSubTopicName)
                ?.takeIf { it != topicId }   // নিজের সাথে merge না — নিরাপত্তা check

            val effectiveTopicId = mergeTopicId ?: topicId
            try {
                contentRepo.moveContentByTopicAndPersist(sheet, oldSubject, oldSubTopic, newSubjectName, newSubTopicName)
                try {
                    contentRepo.moveRoomQuestionsByTopic(sheet, topicId, newSubjectName, newSubTopicName, newSubjectId, effectiveTopicId)
                } catch (e: Exception) {
                    android.util.Log.w("AdminMove", "Room questions move (topic) failed (non-fatal): ${e.message}")
                }
                try {
                    contentRepo.moveRoomTopicReference(topicId, newSubjectId, mergeTopicId, sheet)
                } catch (e: Exception) {
                    android.util.Log.w("AdminMove", "Room topic reference move failed (non-fatal): ${e.message}")
                }
                val mergeNote = if (mergeTopicId != null) " (একই নামের Topic-এর সাথে merge)" else ""
                _state.update { it.copy(isMovingContent = false,
                    moveContentMsg = "✅ \"$oldSubTopic\" অধ্যায় \"$newSubjectName\"-এ সরানো হয়েছে$mergeNote",
                    toast = "📦 \"$oldSubTopic\" অধ্যায় সরানো হয়েছে",
                    contentEditVersion = it.contentEditVersion + 1) }
                android.util.Log.i("AdminMove", "Instant local topic move done: $topicId → $newSubjectName/$newSubTopicName")
            } catch (e: Exception) {
                android.util.Log.e("AdminMove", "Instant local topic move FAILED: ${e.message}", e)
                _state.update { it.copy(isMovingContent = false, moveContentMsg = "❌ Move ব্যর্থ হয়েছে: ${e.message ?: "unknown error"}") }
                return@launch
            }

            launch {
                val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                try {
                    val cm = getApplication<android.app.Application>()
                        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                            as android.net.ConnectivityManager
                    val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                    if (isOnline) {
                        when (val r = com.hanif.smartstudy.data.remote.GasContentService
                            .moveTopic(topicId, newSubjectId, newSubjectName, newSubTopicName, mergeTopicId)) {
                            is com.hanif.smartstudy.data.remote.ApiResult.Success ->
                                android.util.Log.i("AdminMove", "Background sheet topic-move SUCCESS: ${r.data}টি প্রশ্ন")
                            is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                                android.util.Log.e("AdminMove", "Background sheet topic-move FAILED: ${r.message} — queueing")
                                q.enqueueAdminMoveTopic(topicId, newSubjectId, newSubjectName, newSubTopicName, mergeTopicId)
                                loadPendingEdits()
                            }
                        }
                    } else {
                        q.enqueueAdminMoveTopic(topicId, newSubjectId, newSubjectName, newSubTopicName, mergeTopicId)
                        loadPendingEdits()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdminMove", "EXCEPTION in background sync: ${e.message}", e)
                    try {
                        q.enqueueAdminMoveTopic(topicId, newSubjectId, newSubjectName, newSubTopicName, mergeTopicId)
                        loadPendingEdits()
                    } catch (e2: Exception) {
                        android.util.Log.e("AdminMove", "QUEUE ALSO FAILED: ${e2.message}", e2)
                    }
                }
            }
        }
    }

    // ── Toast clear ───────────────────────────────────────────

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    // ── Firebase user save ────────────────────────────────────

    private fun saveUserToFirebase(user: User) {
        val phone = user.phone?.replace("+", "").orEmpty().ifEmpty { return }
        viewModelScope.launch {
            if (session.isOfflineMode()) return@launch
            try {
                val update = mutableMapOf<String, Any?>()
                user.name?.let    { update["Name"]    = it }
                user.picture?.let { update["Picture"] = it }
                update["XP"] = user.xp
                fbPatch("users/$phone", update)
            } catch (e: Exception) {
                Log.e("Firebase", "saveUser: ${e.message}")
            }
        }
    }

    private fun saveProfileToFirebase(user: User) {
        val phone = user.phone?.replace("+", "").orEmpty().ifEmpty { return }
        viewModelScope.launch {
            if (session.isOfflineMode()) return@launch
            try {
                val update = mutableMapOf<String, Any?>()
                user.name?.let      { if (it.isNotBlank()) update["Name"]       = it }
                user.userType?.let  { if (it.isNotBlank()) update["UserType"]   = it }
                // classLevel খালি হলেও save করতে হবে (Job seeker = classLevel ফাঁকা)
                update["ClassLevel"] = user.classLevel ?: ""
                user.picture?.let   { update["Picture"] = it }
                update["XP"] = user.xp
                fbPatch("users/$phone", update)
            } catch (e: Exception) {
                Log.e("Firebase", "saveProfile: ${e.message}")
            }
        }
    }
}
