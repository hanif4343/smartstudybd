package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
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
import kotlinx.coroutines.launch

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
    // Active users (Admin)
    val activeUsers     : List<ActiveUser>   = emptyList(),
    // Admin: list of all users from Firebase
    val allUsers        : List<Map<String,String>> = emptyList(),
    val viewingAsUser   : User?              = null,  // admin impersonation
)

class MenuViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val cache   = ContentCache(app)
    private val ctx     = app.applicationContext

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            val user       = session.getCurrentUser()
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
            val fcm        = user?.fcmToken ?: ""

            val prefs = ctx.getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)
            val bookmarks = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()

            _state.update {
                it.copy(
                    user           = user,
                    isAdmin        = user?.isAdmin() ?: false,
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
                    totalStudyMin  = stats.third,
                    totalAppMin    = totalApp,
                    xpHistory      = xpHist,
                    fcmToken       = fcm,
                    bookmarkedIds  = bookmarks
                )
            }

            // Fetch FCM token fresh
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
        try {
            val ref = FirebaseDatabase.getInstance().getReference("users")
            ref.get().addOnSuccessListener { snapshot ->
                val list = mutableListOf<Map<String, String>>()
                snapshot.children.forEach { child ->
                    val map = mutableMapOf<String, String>()
                    child.children.forEach { field ->
                        map[field.key ?: ""] = field.value?.toString() ?: ""
                    }
                    list.add(map)
                }
                _state.update { it.copy(allUsers = list) }
            }
        } catch (e: Exception) {
            Log.e("Admin", "loadAllUsers: ${e.message}")
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
                _state.update { it.copy(activeUsers = users) }
            } catch (e: Exception) {
                Log.e("Admin", "loadActiveUsers: ${e.message}")
            }
        }
    }

    // ── Admin: send notification (title, body, targetPhone) ───
    fun adminSendNotification(title: String, body: String, targetPhone: String?) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val ref = FirebaseDatabase.getInstance().getReference("broadcasts").push()
                ref.setValue(mapOf(
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
        try {
            val ref = FirebaseDatabase.getInstance().getReference("users/${phone.replace("+", "")}")
            ref.get().addOnSuccessListener { snap ->
                val map = mutableMapOf<String, Any>()
                snap.children.forEach { map[it.key ?: ""] = it.value ?: "" }
                val user = User.fromFirebaseMap(map)
                _state.update { it.copy(viewingAsUser = user, toast = "👁 ${user.name} হিসেবে দেখছেন") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "❌ ইউজার লোড হয়নি") }
        }
    }

    fun adminExitViewAs() {
        _state.update { it.copy(viewingAsUser = null) }
    }

    // ── Toast clear ───────────────────────────────────────────

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    // ── Firebase user save ────────────────────────────────────

    private fun saveUserToFirebase(user: User) {
        val phone = user.phone?.replace("+", "").orEmpty().ifEmpty { return }
        try {
            val ref = FirebaseDatabase.getInstance().getReference("users/$phone")
            val update = mutableMapOf<String, Any>()
            user.name?.let    { update["Name"]    = it }
            user.picture?.let { update["Picture"] = it }
            update["XP"] = user.xp
            ref.updateChildren(update)
        } catch (e: Exception) {
            Log.e("Firebase", "saveUser: ${e.message}")
        }
    }
}
