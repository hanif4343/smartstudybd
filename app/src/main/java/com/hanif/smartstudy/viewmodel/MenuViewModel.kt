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
    val isReminderOn    : Boolean            = false,
    val reminderHour    : Int                = 20,
    val reminderMinute  : Int                = 0,
    val isMorningOn     : Boolean            = false,
    val morningHour     : Int                = 7,
    val morningMinute   : Int                = 0,
    val isNightOn       : Boolean            = false,
    val nightHour       : Int                = 21,
    val nightMinute     : Int                = 0,
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
    // Report Queue
    val reportedQuestions : List<com.hanif.smartstudy.data.remote.ReportedQuestion> = emptyList(),
    val isLoadingReports  : Boolean          = false,
    // Add Question
    val isAddingQuestion  : Boolean          = false,
    val addQuestionMsg    : String?          = null,
    // Bulk Audience
    val isBulkUpdating    : Boolean          = false,
    val bulkUpdateMsg     : String?          = null,
    // Offline admin edits
    val pendingEdits      : List<com.hanif.smartstudy.data.local.PendingAction> = emptyList(),
    val isSyncingEdits    : Boolean          = false,
    val syncEditsMsg      : String?          = null,
)

class MenuViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val cache   = ContentCache(app)
    private val ctx     = app.applicationContext

    // ── Firebase REST helpers ─────────────────────────────────
    private val http    = OkHttpClient()
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()
    private val fbUrl   get() = BuildConfig.FIREBASE_URL.trimEnd('/')
    private val fbAuth  get() = BuildConfig.FIREBASE_DB_SECRET

    private suspend fun fbPatch(path: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = JSONObject(data.mapValues { it.value ?: JSONObject.NULL }).toString()
            .toRequestBody(JSON_MT)
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=$fbAuth")
            .patch(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("fbPatch $path failed: ${r.code}")
        }
    }

    private suspend fun fbSet(path: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = JSONObject(data.mapValues { it.value ?: JSONObject.NULL }).toString()
            .toRequestBody(JSON_MT)
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=$fbAuth")
            .put(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("fbSet $path failed: ${r.code}")
        }
    }

    private suspend fun fbPost(path: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val body = JSONObject(data.mapValues { it.value ?: JSONObject.NULL }).toString()
            .toRequestBody(JSON_MT)
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=$fbAuth")
            .post(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("fbPost $path failed: ${r.code}")
        }
    }

    private suspend fun fbGet(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$fbUrl/$path.json?auth=$fbAuth")
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
            val remOn      = session.isReminderOn()
            val remH       = session.getReminderHour()
            val remM       = session.getReminderMinute()
            val morningOn  = session.isMorningReminderOn()
            val morningH   = session.getMorningHour()
            val morningM   = session.getMorningMinute()
            val nightOn    = session.isNightReminderOn()
            val nightH     = session.getNightHour()
            val nightM     = session.getNightMinute()
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
                    isReminderOn   = remOn,
                    reminderHour   = remH,
                    reminderMinute = remM,
                    isMorningOn    = morningOn,
                    morningHour    = morningH,
                    morningMinute  = morningM,
                    isNightOn      = nightOn,
                    nightHour      = nightH,
                    nightMinute    = nightM,
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
                    adminViewingTag = adminTag
                )
            }

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

    // ── Reminder ─────────────────────────────────────────────

    fun setReminder(on: Boolean, hour: Int = _state.value.reminderHour, minute: Int = _state.value.reminderMinute) {
        viewModelScope.launch {
            session.setReminder(on, hour, minute)
            _state.update { it.copy(isReminderOn = on, reminderHour = hour, reminderMinute = minute) }
            if (on) ReminderReceiver.scheduleMorning(ctx, hour, minute)
            else    ReminderReceiver.cancelMorning(ctx)
        }
    }

    fun setMorningReminder(on: Boolean, hour: Int = _state.value.morningHour, minute: Int = _state.value.morningMinute) {
        viewModelScope.launch {
            _state.update { it.copy(isMorningOn = on, morningHour = hour, morningMinute = minute) }
            if (on) ReminderReceiver.scheduleMorning(ctx, hour, minute)
            else    ReminderReceiver.cancelMorning(ctx)
        }
    }

    fun setNightReminder(on: Boolean, hour: Int = _state.value.nightHour, minute: Int = _state.value.nightMinute) {
        viewModelScope.launch {
            _state.update { it.copy(isNightOn = on, nightHour = hour, nightMinute = minute) }
            if (on) ReminderReceiver.scheduleNight(ctx, hour, minute)
            else    ReminderReceiver.cancelNight(ctx)
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
                _state.update { it.copy(isLoading = false, successMsg = "নোটিফিকেশন পাঠানো হয়েছে") }
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
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isEditingQuestion = true, editSuccessMsg = null) }
            val cm = getApplication<android.app.Application>()
                .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val isOnline = cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (isOnline) {
                // Online — সরাসরি Firebase এ save
                when (val r = com.hanif.smartstudy.data.remote.GasApiService
                        .adminUpdateQuestionField(sheet, rowKey, fields)) {
                    is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                        cache.clearCache()
                        _state.update { it.copy(isEditingQuestion = false,
                            editSuccessMsg = "✅ আপডেট হয়েছে!", toast = "✅ প্রশ্ন সংরক্ষিত") }
                    }
                    is com.hanif.smartstudy.data.remote.GasResult.Error -> {
                        // Online কিন্তু fail — queue এ রাখো
                        val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                        q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                        loadPendingEdits()
                        _state.update { it.copy(isEditingQuestion = false,
                            editSuccessMsg = "⚠️ সংরক্ষিত — sync হবে", error = "❌ ${r.message}") }
                    }
                }
            } else {
                // Offline — queue এ রাখো, net আসলে auto sync হবে
                val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
                q.enqueueAdminEdit(sheet, rowKey, fields, questionPreview)
                loadPendingEdits()
                _state.update { it.copy(isEditingQuestion = false,
                    editSuccessMsg = "📴 Offline এ সংরক্ষিত — net আসলে auto sync হবে") }
            }
        }
    }

    // ── Pending admin edits লোড করো ──────────────────────────
    fun loadPendingEdits() {
        viewModelScope.launch {
            val q = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
            _state.update { it.copy(pendingEdits = q.getPendingAdminEdits()) }
        }
    }

    // ── Manual sync now ───────────────────────────────────────
    fun syncPendingEditsNow() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isSyncingEdits = true, syncEditsMsg = null) }
            val q       = com.hanif.smartstudy.data.local.PendingQueue(getApplication())
            val pending = q.getPendingAdminEdits()
            if (pending.isEmpty()) {
                _state.update { it.copy(isSyncingEdits = false, syncEditsMsg = "✅ কোনো pending edit নেই") }
                return@launch
            }
            var successCount = 0
            var failCount    = 0
            val gson = com.google.gson.Gson()
            for (action in pending) {
                try {
                    val payload    = gson.fromJson(action.payload, Map::class.java)
                    val sheet      = payload["sheet"]?.toString() ?: continue
                    val questionId = payload["questionId"]?.toString() ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val fields     = payload["fields"] as? Map<String, String> ?: continue
                    when (com.hanif.smartstudy.data.remote.GasApiService
                            .adminUpdateQuestionField(sheet, questionId, fields)) {
                        is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                            q.remove(action.id); successCount++
                        }
                        is com.hanif.smartstudy.data.remote.GasResult.Error -> {
                            q.incrementRetry(action.id); failCount++
                        }
                    }
                } catch (e: Exception) { failCount++ }
            }
            if (successCount > 0) cache.clearCache()
            loadPendingEdits()
            val msg = when {
                failCount == 0 -> "✅ $successCount টি edit sync সফল!"
                successCount == 0 -> "❌ সব ($failCount টি) fail হয়েছে"
                else -> "⚠️ $successCount টি সফল, $failCount টি fail"
            }
            _state.update { it.copy(isSyncingEdits = false, syncEditsMsg = msg) }
        }
    }

    fun clearSyncEditsMsg() { _state.update { it.copy(syncEditsMsg = null) } }

    fun adminSwapOptions(sheet: String, rowKey: String, options: Map<String, String>, newAnswer: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isEditingQuestion = true) }
            when (val r = com.hanif.smartstudy.data.remote.GasApiService
                    .adminSwapOptions(sheet, rowKey, options, newAnswer)) {
                is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                    cache.clearCache()
                    _state.update { it.copy(isEditingQuestion = false, toast = "✅ Options আপডেট") }
                }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update { it.copy(isEditingQuestion = false, error = "❌ ${r.message}") }
            }
        }
    }

    fun clearEditMsg() { _state.update { it.copy(editSuccessMsg = null) } }

    // ── Admin: Report Queue ───────────────────────────────────
    fun loadPendingReports() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingReports = true) }
            when (val r = com.hanif.smartstudy.data.remote.GasApiService.fetchPendingReports()) {
                is com.hanif.smartstudy.data.remote.GasResult.Success ->
                    _state.update { it.copy(reportedQuestions = r.data, isLoadingReports = false) }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
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
            when (com.hanif.smartstudy.data.remote.GasApiService
                    .resolveReportAndNotify(reportKey, status, userPhone, questionSnippet, userName, questionId, tab)) {
                is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                    _state.update {
                        it.copy(
                            reportedQuestions = it.reportedQuestions.filter { r -> r.reportKey != reportKey },
                            toast = if (status == "resolved") "✅ Resolved — ইউজারকে নোটিফিকেশন গেছে" else "🗑 Dismissed"
                        )
                    }
                }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update { it.copy(toast = "❌ Update ব্যর্থ") }
            }
        }
    }

    // ── Admin: Add New Question ───────────────────────────────
    fun adminAddQuestion(sheet: String, fields: Map<String, String>) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isAddingQuestion = true, addQuestionMsg = null) }
            when (val r = com.hanif.smartstudy.data.remote.GasApiService.adminAddQuestion(sheet, fields)) {
                is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                    cache.clearCache()
                    _state.update { it.copy(isAddingQuestion = false,
                        addQuestionMsg = "✅ প্রশ্ন যোগ হয়েছে! Key: ${r.data.take(15)}") }
                }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update { it.copy(isAddingQuestion = false,
                        addQuestionMsg = "❌ ব্যর্থ: ${r.message}") }
            }
        }
    }

    fun clearAddQuestionMsg() { _state.update { it.copy(addQuestionMsg = null) } }

    // ── Admin: Bulk Audience Update ───────────────────────────
    fun adminBulkAudienceUpdate(sheet: String, subject: String, subTopic: String, newTag: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isBulkUpdating = true, bulkUpdateMsg = null) }
            when (val r = com.hanif.smartstudy.data.remote.GasApiService
                    .adminBulkAudienceUpdate(sheet, subject, subTopic, newTag)) {
                is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                    cache.clearCache()
                    _state.update { it.copy(isBulkUpdating = false,
                        bulkUpdateMsg = "✅ ${r.data}টি প্রশ্ন → \"$newTag\"") }
                }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update { it.copy(isBulkUpdating = false,
                        bulkUpdateMsg = "❌ ${r.message}") }
            }
        }
    }

    fun clearBulkMsg() { _state.update { it.copy(bulkUpdateMsg = null) } }

    // ── Toast clear ───────────────────────────────────────────

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    // ── Firebase user save ────────────────────────────────────

    private fun saveUserToFirebase(user: User) {
        val phone = user.phone?.replace("+", "").orEmpty().ifEmpty { return }
        viewModelScope.launch {
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
