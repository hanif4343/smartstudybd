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
    // Weak topics (Profile/Stats only)
    val weakTopics      : List<com.hanif.smartstudy.data.model.WeakTopic> = emptyList(),
    // Study time breakdown
    val todayStudyMin   : Int                = 0,
    val weekStudyMin    : Int                = 0,
    // Active users (Admin)
    val activeUsers     : List<ActiveUser>   = emptyList(),
    // Admin: list of all users from Firebase
    val allUsers        : List<Map<String,String>> = emptyList(),
    val viewingAsUser   : User?              = null,  // admin impersonation
    // Admin: audience tag switch — "" = নিজের, অন্যথায় override করে দেখছে
    val adminViewingTag : String             = "",
    // Admin: question edit processing
    val isEditingQuestion : Boolean          = false,
    val editSuccessMsg    : String?          = null,

    // ── Feature 1: Report Queue ──
    val reportedQuestions : List<com.hanif.smartstudy.data.remote.ReportedQuestion> = emptyList(),
    val isLoadingReports  : Boolean          = false,

    // ── Feature 2: Add New Question ──
    val isAddingQuestion  : Boolean          = false,
    val addQuestionMsg    : String?          = null,

    // ── Feature 3: Bulk Audience Change ──
    val isBulkUpdating    : Boolean          = false,
    val bulkUpdateMsg     : String?          = null,
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

    // ── Admin: Audience Tag Switch ─────────────────────────────

    /**
     * Admin এর জন্য audience tag switch করো।
     * [tag] = "" হলে নিজের actual audience তে ফিরে যাবে।
     * এই tag টা SessionManager এও save হয় যাতে restart এর পরেও থাকে।
     */
    fun adminSwitchAudienceTag(tag: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            session.setAdminAudienceTag(tag)
            _state.update {
                val label = if (tag.isBlank()) "নিজের audience" else tag
                it.copy(
                    adminViewingTag = tag,
                    toast = "🔄 এখন দেখছেন: $label"
                )
            }
        }
    }

    // ── Admin: Edit Question Field ────────────────────────────

    /**
     * Admin যেকোনো প্রশ্নের যেকোনো field Firebase এ সরাসরি update করতে পারবে।
     *
     * @param sheet   "Quiz" | "QBank" | "Study"
     * @param rowKey  Firebase row key (question id)
     * @param fields  পরিবর্তিত fields map
     */
    fun adminEditQuestion(
        sheet  : String,
        rowKey : String,
        fields : Map<String, String>
    ) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isEditingQuestion = true, editSuccessMsg = null) }
            try {
                val result = com.hanif.smartstudy.data.remote.GasApiService
                    .adminUpdateQuestionField(sheet, rowKey, fields)
                when (result) {
                    is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                        // Cache invalidate করো যাতে পরবর্তী fetch এ নতুন data আসে
                        cache.clearCache()
                        _state.update {
                            it.copy(
                                isEditingQuestion = false,
                                editSuccessMsg = "✅ সংরক্ষিত হয়েছে! Cache clear করা হয়েছে।",
                                toast = "✅ প্রশ্ন আপডেট হয়েছে"
                            )
                        }
                    }
                    is com.hanif.smartstudy.data.remote.GasResult.Error -> {
                        _state.update {
                            it.copy(
                                isEditingQuestion = false,
                                error = "❌ আপডেট ব্যর্থ: ${result.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isEditingQuestion = false, error = "❌ Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Admin: MCQ options position swap করো।
     * Firebase এ optionA/B/C/D এবং answer একসাথে update হবে।
     */
    fun adminSwapOptions(
        sheet    : String,
        rowKey   : String,
        options  : Map<String, String>,
        newAnswer: String
    ) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isEditingQuestion = true) }
            try {
                val result = com.hanif.smartstudy.data.remote.GasApiService
                    .adminSwapOptions(sheet, rowKey, options, newAnswer)
                when (result) {
                    is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                        cache.clearCache()
                        _state.update {
                            it.copy(isEditingQuestion = false, toast = "✅ Options position আপডেট হয়েছে")
                        }
                    }
                    is com.hanif.smartstudy.data.remote.GasResult.Error -> {
                        _state.update {
                            it.copy(isEditingQuestion = false, error = "❌ Swap ব্যর্থ: ${result.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isEditingQuestion = false, error = "❌ ${e.message}") }
            }
        }
    }

    fun clearEditMsg() {
        _state.update { it.copy(editSuccessMsg = null) }
    }

    // ── Feature 1: Report Queue ───────────────────────────────

    fun loadPendingReports() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingReports = true) }
            when (val r = com.hanif.smartstudy.data.remote.GasApiService.fetchPendingReports()) {
                is com.hanif.smartstudy.data.remote.GasResult.Success ->
                    _state.update { it.copy(reportedQuestions = r.data, isLoadingReports = false) }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update { it.copy(isLoadingReports = false, error = "Report লোড ব্যর্থ: ${r.message}") }
            }
        }
    }

    fun resolveReport(reportKey: String, status: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            when (com.hanif.smartstudy.data.remote.GasApiService.updateReportStatus(reportKey, status)) {
                is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                    // Local list থেকে সরাও
                    _state.update {
                        it.copy(
                            reportedQuestions = it.reportedQuestions.filter { r -> r.reportKey != reportKey },
                            toast = if (status == "resolved") "✅ Report resolved" else "🗑 Report dismissed"
                        )
                    }
                }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update { it.copy(toast = "❌ Update ব্যর্থ হয়েছে") }
            }
        }
    }

    // ── Feature 2: Add New Question ──────────────────────────

    fun adminAddQuestion(sheet: String, fields: Map<String, String>) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isAddingQuestion = true, addQuestionMsg = null) }
            when (val r = com.hanif.smartstudy.data.remote.GasApiService.adminAddQuestion(sheet, fields)) {
                is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                    cache.clearCache()
                    _state.update {
                        it.copy(
                            isAddingQuestion = false,
                            addQuestionMsg   = "✅ প্রশ্ন যোগ হয়েছে! Key: ${r.data.take(15)}"
                        )
                    }
                }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update {
                        it.copy(
                            isAddingQuestion = false,
                            addQuestionMsg   = "❌ ব্যর্থ: ${r.message}"
                        )
                    }
            }
        }
    }

    fun clearAddQuestionMsg() { _state.update { it.copy(addQuestionMsg = null) } }

    // ── Feature 3: Bulk Audience Change ──────────────────────

    fun adminBulkAudienceUpdate(
        sheet    : String,
        subject  : String,
        subTopic : String,
        newTag   : String
    ) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            _state.update { it.copy(isBulkUpdating = true, bulkUpdateMsg = null) }
            when (val r = com.hanif.smartstudy.data.remote.GasApiService.adminBulkAudienceUpdate(
                sheet, subject, subTopic, newTag
            )) {
                is com.hanif.smartstudy.data.remote.GasResult.Success -> {
                    cache.clearCache()
                    _state.update {
                        it.copy(
                            isBulkUpdating = false,
                            bulkUpdateMsg  = "✅ ${r.data}টি প্রশ্নের AudienceTag আপডেট হয়েছে → \"$newTag\""
                        )
                    }
                }
                is com.hanif.smartstudy.data.remote.GasResult.Error ->
                    _state.update {
                        it.copy(
                            isBulkUpdating = false,
                            bulkUpdateMsg  = "❌ ব্যর্থ: ${r.message}"
                        )
                    }
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

    private fun saveProfileToFirebase(user: User) {
        val phone = user.phone?.replace("+", "").orEmpty().ifEmpty { return }
        try {
            val ref = FirebaseDatabase.getInstance().getReference("users/$phone")
            val update = mutableMapOf<String, Any>()
            user.name?.let      { if (it.isNotBlank()) update["Name"]       = it }
            user.userType?.let  { if (it.isNotBlank()) update["UserType"]   = it }
            // classLevel খালি হলেও save করতে হবে (Job seeker = classLevel ফাঁকা)
            update["ClassLevel"] = user.classLevel ?: ""
            user.picture?.let   { update["Picture"] = it }
            update["XP"] = user.xp
            ref.updateChildren(update)
        } catch (e: Exception) {
            Log.e("Firebase", "saveProfile: ${e.message}")
        }
    }
}
