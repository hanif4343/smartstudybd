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
    // Settings → "Data Source" ড্রপডাউন — Firebase | Google Sheet
    val dataSourceMode  : com.hanif.smartstudy.data.model.DataSourceMode =
        com.hanif.smartstudy.data.model.DataSourceMode.FIREBASE,
    // ── Google Sheet সিলেক্ট করার পর সাথে সাথেই একটা test fetch চলে — এই
    // ৩টা field দিয়ে Settings স্ক্রিনে real-time প্রোগ্রেস (elapsed সেকেন্ড) ও
    // ফলাফল (সফল/ব্যর্থ + আসল কারণ) দেখানো হয় ──
    val isTestingDataSource      : Boolean = false,
    val dataSourceTestElapsedSec : Int     = 0,
    val dataSourceTestResultMsg  : String? = null,
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
    val aiKeysSavedMsg    : String?          = null,
)

class MenuViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val cache   = ContentCache(app)
    private val ctx     = app.applicationContext

    // ── Settings-এ "Data Source" ড্রপডাউন থেকে "Google Sheet" সিলেক্ট করা থাকলে
    // admin এর প্রশ্ন এডিট/যোগ/ডিলিট/রিনেম — সবকিছু Firebase বাইপাস করে GasContentService
    // (GAS Web App প্রক্সি) দিয়ে যায়। দুই ব্যাকএন্ডেরই ApiResult<T> রিটার্ন টাইপ এক,
    // তাই কল-সাইটের বাকি লজিক (cache patch, pending-queue fallback ইত্যাদি) অপরিবর্তিত থাকে। ──
    private fun useGoogleSheetBackend(): Boolean =
        session.getDataSourceMode() == com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET

    private suspend fun adminUpdateField(
        sheet: String, rowKey: String, fields: Map<String, String>
    ): com.hanif.smartstudy.data.remote.ApiResult<Unit> =
        if (useGoogleSheetBackend())
            com.hanif.smartstudy.data.remote.GasContentService.updateFields(sheet, rowKey, fields)
        else
            com.hanif.smartstudy.data.remote.FirebaseDataService.adminUpdateQuestionField(sheet, rowKey, fields)

    private suspend fun adminDeleteRow(sheet: String, rowKey: String): com.hanif.smartstudy.data.remote.ApiResult<Unit> =
        if (useGoogleSheetBackend())
            com.hanif.smartstudy.data.remote.GasContentService.deleteQuestion(sheet, rowKey)
        else
            com.hanif.smartstudy.data.remote.FirebaseDataService.adminDeleteQuestion(sheet, rowKey)

    private suspend fun adminAddRow(sheet: String, fields: Map<String, String>): com.hanif.smartstudy.data.remote.ApiResult<String> =
        if (useGoogleSheetBackend())
            com.hanif.smartstudy.data.remote.GasContentService.addQuestion(sheet, fields)
        else
            com.hanif.smartstudy.data.remote.FirebaseDataService.adminAddQuestion(sheet, fields)

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

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            val localUser  = session.getCurrentUser()
            val isDark     = session.isDarkMode()
            val theme      = themeFromString(session.getThemeColor())
            val soundOff   = session.isSoundOff()
            val offlineOn  = session.isOfflineMode()
            val dataSrcMode = session.getDataSourceMode()
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
                    dataSourceMode = dataSrcMode,
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
                    geminiApiKey   = aiKeys.gemini
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

    // ── Data Source (Firebase / Google Sheet) — Settings-এ ড্রপডাউন থেকে বদলায় ──
    // বদলানোর সাথে সাথে content cache (Room + DataStore + in-memory) clear করে দেওয়া
    // হয় — যাতে পুরনো ব্যাকএন্ডের ডেটা নতুন মোডের সাথে গুলিয়ে না যায় (getContent() পরের
    // বার কল হলে নতুন মোড অনুযায়ী fresh fetch শুরু হবে, দেখো ContentFetchService.kt)।
    fun setDataSourceMode(mode: com.hanif.smartstudy.data.model.DataSourceMode) {
        viewModelScope.launch {
            if (mode == com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET &&
                !com.hanif.smartstudy.data.remote.GasContentService.isConfigured()
            ) {
                _state.update { it.copy(
                    toast = "❌ GAS_URL/GAS_SECRET বিল্ডে সেট করা নেই — Google Sheet মোড চালু করা যাবে না"
                )}
                return@launch
            }
            session.setDataSourceMode(mode)
            cache.clearCache()
            com.hanif.smartstudy.data.repository.ContentRepository.clearMemCache()
            _state.update { it.copy(
                dataSourceMode = mode,
                dataSourceTestResultMsg = null,
                toast = if (mode == com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET)
                    "📊 Data Source: Google Sheet — এখন থেকে সব প্রশ্ন/সাবজেক্ট Sheet থেকে আসবে (প্রথমবার একটু সময় লাগতে পারে)"
                else
                    "🔥 Data Source: Firebase — আগের মতোই দ্রুত sync",
                contentEditVersion = it.contentEditVersion + 1
            )}

            // ── Google Sheet সিলেক্ট করার সাথে সাথেই একটা রিয়েল test fetch চালাই —
            // "সিলেক্ট করলাম কিন্তু ডেটা আসছে না" এই অবস্থায় ইউজারকে অন্ধকারে
            // রাখার বদলে সাথে সাথেই real progress (elapsed সেকেন্ড, ticking) ও
            // ফলাফল (কতগুলো প্রশ্ন এলো, বা আসল error কারণ) দেখানো হয়। ──
            if (mode == com.hanif.smartstudy.data.model.DataSourceMode.GOOGLE_SHEET) {
                _state.update { it.copy(isTestingDataSource = true, dataSourceTestElapsedSec = 0) }
                val tickerJob = launch {
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        _state.update { it.copy(dataSourceTestElapsedSec = it.dataSourceTestElapsedSec + 1) }
                    }
                }
                val result = com.hanif.smartstudy.data.remote.GasContentService.fetchAllContent()
                tickerJob.cancel()
                when (result) {
                    is com.hanif.smartstudy.data.remote.ContentResult.Success -> {
                        val d = result.data
                        // test fetch-এই যে ডেটা পেলাম সেটা সরাসরি cache-এ বসিয়ে দিলাম —
                        // ইউজারকে আলাদা করে Home/Quiz reload করে আবার অপেক্ষা করতে হবে না
                        cache.saveContent(d)
                        cache.markFullSyncDone(d.fetchedAt)
                        com.hanif.smartstudy.data.repository.ContentRepository.clearMemCache()
                        _state.update { it.copy(
                            isTestingDataSource = false,
                            dataSourceTestResultMsg = "✅ সফল — Quiz ${d.quiz.size}টি, QBank ${d.qbank.size}টি, Study ${d.study.size}টি প্রশ্ন এসেছে",
                            contentEditVersion = it.contentEditVersion + 1
                        )}
                    }
                    is com.hanif.smartstudy.data.remote.ContentResult.Error -> {
                        _state.update { it.copy(
                            isTestingDataSource = false,
                            dataSourceTestResultMsg = "❌ ব্যর্থ — ${result.message}"
                        )}
                    }
                }
            }
        }
    }

    fun clearDataSourceTestResult() { _state.update { it.copy(dataSourceTestResultMsg = null) } }

    // ── Written উত্তর AI-অটো-চেক: ৪টা প্রোভাইডারের API key সেভ ──
    // একবার সেভ করলে DataStore-এ থেকে যায়, পরের বার আবার বসাতে হয় না।
    // চেষ্টার ক্রম Study/QBank উভয় জায়গাতেই: Groq → Mistral → Cerebras → Gemini।
    fun saveAiApiKeys(groq: String, mistral: String, cerebras: String, gemini: String) {
        viewModelScope.launch {
            val keys = com.hanif.smartstudy.data.model.AiApiKeys(
                groq     = groq.trim(),
                mistral  = mistral.trim(),
                cerebras = cerebras.trim(),
                gemini   = gemini.trim()
            )
            session.setAiApiKeys(keys)
            _state.update {
                it.copy(
                    groqApiKey     = keys.groq,
                    mistralApiKey  = keys.mistral,
                    cerebrasApiKey = keys.cerebras,
                    geminiApiKey   = keys.gemini,
                    aiKeysSavedMsg = "✅ API key সংরক্ষণ করা হয়েছে"
                )
            }
        }
    }

    fun clearAiKeysSavedMsg() {
        _state.update { it.copy(aiKeysSavedMsg = null) }
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
            // ── পুরো ব্লকটা try/catch দিয়ে ঘেরা — connectivity check, network কল,
            //    বা অন্য যেকোনো অপ্রত্যাশিত exception হলেও edit-টা হারিয়ে যাবে না।
            //    exception হলেও সবসময় শেষ ভরসা হিসেবে queue এ ফেলে দেওয়া হবে,
            //    যাতে Pending Sync ট্যাবে অন্তত entry-টা দেখা যায় আর পরে sync করা যায়। ──
            try {
                val cm = getApplication<android.app.Application>()
                    .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                        as android.net.ConnectivityManager
                val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                    ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                android.util.Log.d("AdminEdit", "connectivity check: isOnline=$isOnline")

                if (isOnline) {
                    // Online — সরাসরি Firebase/Google Sheet এ save (Settings-এ যেটা সিলেক্ট করা আছে)
                    when (val r = adminUpdateField(sheet, rowKey, fields)) {
                        is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                            // পুরো cache clear করে নতুন fetch করানোর বদলে — শুধু এই
                            // row টাই in-memory + disk cache এ সরাসরি patch করো।
                            // এতে স্ক্রিন reload হয় না, admin যেখানে ছিল সেখানেই
                            // থাকে আর পরিবর্তনও সাথে সাথে স্ক্রিনে দেখা যায়।
                            // TTL (১ ঘণ্টা) শেষ হলে স্বাভাবিক নিয়মেই fresh fetch হবে
                            // এবং অন্য সব ইউজারের কাছেও আপডেট পৌঁছাবে।
                            com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
                                .patchContentAndPersist(sheet, rowKey, fields)
                            android.util.Log.i("AdminEdit", "SUCCESS: $sheet/$rowKey updated. In-place patched.")
                            _state.update { it.copy(isEditingQuestion = false,
                                editSuccessMsg = "✅ আপডেট হয়েছে!", toast = "✅ প্রশ্ন সংরক্ষিত",
                                contentEditVersion = _state.value.contentEditVersion + 1) }
                        }
                        is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                            // Online কিন্তু fail — queue এ রাখো + লোকাল cache-এও সাথে সাথে
                            // patch করে দাও, যাতে sync না হওয়া পর্যন্ত এই edit-টাই অ্যাপে
                            // দেখা যায় (Firebase-এ সিঙ্ক না হলেও UI নিজের local state দেখাবে)।
                            android.util.Log.e("AdminEdit", "Online but FAILED: ${r.message} — queueing")
                            com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
                                .patchContentAndPersist(sheet, rowKey, fields)
                            val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                            q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                            loadPendingEdits()
                            _state.update { it.copy(isEditingQuestion = false,
                                editSuccessMsg = "⚠️ সংরক্ষিত — sync হবে", error = "❌ ${r.message}",
                                contentEditVersion = _state.value.contentEditVersion + 1) }
                        }
                    }
                } else {
                    // Offline — লোকাল cache-এ সাথে সাথে patch করো (তাই edit-টা সাথে সাথেই
                    // অ্যাপে দেখা যাবে), আর queue এ রাখো — net আসলে auto sync হবে
                    android.util.Log.d("AdminEdit", "OFFLINE branch — enqueueing")
                    com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
                        .patchContentAndPersist(sheet, rowKey, fields)
                    val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                    q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                    val countAfter = q.getPendingAdminEdits().size
                    android.util.Log.d("AdminEdit", "enqueued OK — pending count now = $countAfter")
                    loadPendingEdits()
                    _state.update { it.copy(isEditingQuestion = false,
                        editSuccessMsg = "📴 Offline এ সংরক্ষিত — net আসলে auto sync হবে",
                        contentEditVersion = _state.value.contentEditVersion + 1) }
                }
            } catch (e: Exception) {
                // ── connectivity check / network কল / patchContentAndPersist —
                //    যেখানেই exception হোক না কেন, শেষ চেষ্টা হিসেবে queue এ ফেলার
                //    চেষ্টা করি, যাতে edit-টা একদম হারিয়ে না যায় ──
                android.util.Log.e("AdminEdit", "EXCEPTION in adminEditQuestion: ${e.message}", e)
                try {
                    com.hanif.smartstudy.data.repository.ContentRepository(getApplication())
                        .patchContentAndPersist(sheet, rowKey, fields)
                    val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                    q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                    loadPendingEdits()
                    _state.update { it.copy(isEditingQuestion = false,
                        editSuccessMsg = "📴 সংরক্ষিত — net আসলে auto sync হবে",
                        contentEditVersion = _state.value.contentEditVersion + 1) }
                } catch (e2: Exception) {
                    android.util.Log.e("AdminEdit", "QUEUE ALSO FAILED: ${e2.message}", e2)
                    _state.update { it.copy(isEditingQuestion = false,
                        error = "❌ সংরক্ষণ ব্যর্থ হয়েছে: ${e2.message ?: "unknown error"}") }
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
                    val sheet   = payload["sheet"]?.toString() ?: continue

                    when (action.type) {
                        "admin_edit_question" -> {
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
            // অস্থায়ী লোকাল id — অনলাইন হলে Firebase push-key দিয়ে replace হয়ে যাবে,
            // অফলাইন/fail হলে এটাই থেকে যাবে যতক্ষণ না পরে sync হয়
            val localId = "-local" + System.currentTimeMillis().toString(36) +
                    (0..5).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
            try {
                val cm = getApplication<android.app.Application>()
                    .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                        as android.net.ConnectivityManager
                val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                    ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                if (isOnline) {
                    when (val r = adminAddRow(sheet, fields)) {
                        is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                            // পুরো cache clear করার বদলে সরাসরি নতুন row cache-এ যোগ করো —
                            // তাতে স্ক্রিন reload ছাড়াই সাথে সাথে নতুন প্রশ্নটা দেখা যাবে।
                            repo.addContentAndPersist(sheet, r.data, fields)
                            _state.update { it.copy(isAddingQuestion = false,
                                addQuestionMsg = "✅ প্রশ্ন যোগ হয়েছে! Key: ${r.data.take(15)}",
                                contentEditVersion = it.contentEditVersion + 1) }
                        }
                        is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                            // Online কিন্তু fail — লোকাল id দিয়ে cache-এ দেখাও + queue করো
                            repo.addContentAndPersist(sheet, localId, fields)
                            val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                            q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                            loadPendingEdits()
                            _state.update { it.copy(isAddingQuestion = false,
                                addQuestionMsg = "⚠️ সংরক্ষিত — sync হবে (❌ ${r.message})",
                                contentEditVersion = it.contentEditVersion + 1) }
                        }
                    }
                } else {
                    // Offline — লোকাল id দিয়ে cache-এ সাথে সাথে যোগ করো, queue এ রাখো
                    repo.addContentAndPersist(sheet, localId, fields)
                    val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                    q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                    loadPendingEdits()
                    _state.update { it.copy(isAddingQuestion = false,
                        addQuestionMsg = "📴 Offline এ সংরক্ষিত — net আসলে auto sync হবে",
                        contentEditVersion = it.contentEditVersion + 1) }
                }
            } catch (e: Exception) {
                try {
                    repo.addContentAndPersist(sheet, localId, fields)
                    val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                    q.enqueueAdminAdd(sheet, localId, fields, questionPreview)
                    loadPendingEdits()
                    _state.update { it.copy(isAddingQuestion = false,
                        addQuestionMsg = "📴 সংরক্ষিত — net আসলে auto sync হবে",
                        contentEditVersion = it.contentEditVersion + 1) }
                } catch (e2: Exception) {
                    _state.update { it.copy(isAddingQuestion = false,
                        addQuestionMsg = "❌ সংরক্ষণ ব্যর্থ হয়েছে: ${e2.message ?: "unknown error"}") }
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
            when (val r = if (useGoogleSheetBackend())
                    com.hanif.smartstudy.data.remote.GasContentService
                        .renameSubjectOrTopic(sheets, oldSubject, oldSubTopic, newName, renameSubTopic)
                else
                    com.hanif.smartstudy.data.remote.FirebaseDataService
                        .adminRenameSubjectOrTopic(sheets, oldSubject, oldSubTopic, newName, renameSubTopic)) {
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
