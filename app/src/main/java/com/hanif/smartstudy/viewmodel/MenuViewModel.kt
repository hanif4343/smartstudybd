package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.data.remote.ImgBBService
import com.hanif.smartstudy.data.remote.UserSyncService
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── App theme ──
enum class AppTheme(val label: String, val key: String) {
    INDIGO("নীল-বেগুনি", "indigo"),
    TEAL("সবুজ-নীল",   "teal"),
    ROSE("গোলাপি",     "rose"),
    AMBER("হলুদ",      "amber")
}

data class MenuUiState(
    val user           : User?      = null,
    val isDarkMode     : Boolean    = false,
    val appTheme       : AppTheme   = AppTheme.INDIGO,
    val isSoundOn      : Boolean    = true,
    val isLoading      : Boolean    = false,
    val uploadProgress : Boolean    = false,
    val error          : String?    = null,
    val successMsg     : String?    = null,
    // Stats
    val totalCorrect   : Int        = 0,
    val totalWrong     : Int        = 0,
    val accuracyPct    : Int        = 0,
    val totalStudyMin  : Int        = 0,
    val xpHistory      : List<Pair<String, Int>> = emptyList(),
    // Subject breakdown
    val subjectStats   : Map<String, Pair<Int,Int>> = emptyMap(),  // subject → (correct, total)
    // Bookmarks
    val bookmarkedIds  : Set<String> = emptySet(),
    // Admin
    val isAdminView    : Boolean    = false,
    val allUsers       : List<User> = emptyList(),
    val activeUsers    : List<ActiveUser> = emptyList()
)

data class ActiveUser(
    val phone     : String,
    val name      : String,
    val lastSeen  : Long,
    val isOnline  : Boolean,
    val fcmToken  : String = ""
)

class MenuViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val cache   = ContentCache(app)
    private val prefs   = app.getSharedPreferences("quiz_prefs", android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            val user      = session.getCurrentUser()
            val darkMode  = session.isDarkMode()
            val soundOn   = !prefs.getBoolean("sound_off", false)
            val themeKey  = prefs.getString("app_theme", "indigo") ?: "indigo"
            val theme     = AppTheme.entries.firstOrNull { it.key == themeKey } ?: AppTheme.INDIGO
            val bookmarks = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
            val (today, week, total) = cache.getStudyStats()
            val correct   = cache.getCorrectCount()
            val wrong     = cache.getWrongCount()
            val t         = correct + wrong
            val acc       = if (t > 0) (correct * 100) / t else 0

            _state.update {
                it.copy(
                    user           = user,
                    isDarkMode     = darkMode,
                    appTheme       = theme,
                    isSoundOn      = soundOn,
                    bookmarkedIds  = bookmarks,
                    totalCorrect   = correct,
                    totalWrong     = wrong,
                    accuracyPct    = acc,
                    totalStudyMin  = total,
                    isAdminView    = user?.isAdmin() == true
                )
            }

            // Admin: load active users
            if (user?.isAdmin() == true) loadActiveUsers()
        }
    }

    // ── Dark mode ──
    fun toggleDarkMode() {
        viewModelScope.launch {
            val newVal = !_state.value.isDarkMode
            session.setDarkMode(newVal)
            _state.update { it.copy(isDarkMode = newVal) }
        }
    }

    // ── Theme ──
    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.key).apply()
        _state.update { it.copy(appTheme = theme) }
    }

    // ── Sound ──
    fun toggleSound() {
        val newVal = !_state.value.isSoundOn
        prefs.edit().putBoolean("sound_off", !newVal).apply()
        _state.update { it.copy(isSoundOn = newVal) }
    }

    // ── Profile photo upload (ImgBB → Firebase) ──
    fun uploadPhoto(imageBytes: ByteArray) {
        viewModelScope.launch {
            _state.update { it.copy(uploadProgress = true, error = null) }
            try {
                val url = ImgBBService.upload(imageBytes)
                if (url != null) {
                    val user = session.getCurrentUser() ?: return@launch
                    val updated = user.copy(picture = url)
                    session.saveUser(updated)
                    UserSyncService.updatePicture(user.phone ?: "", url)
                    _state.update { it.copy(user = updated, uploadProgress = false,
                        successMsg = "ছবি আপলোড সফল হয়েছে ✅") }
                } else {
                    _state.update { it.copy(uploadProgress = false, error = "আপলোড ব্যর্থ হয়েছে") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(uploadProgress = false, error = e.message) }
            }
        }
    }

    // ── Name update ──
    fun updateName(name: String) {
        viewModelScope.launch {
            val user = session.getCurrentUser() ?: return@launch
            val updated = user.copy(name = name)
            session.saveUser(updated)
            UserSyncService.updateName(user.phone ?: "", name)
            _state.update { it.copy(user = updated, successMsg = "নাম আপডেট হয়েছে ✅") }
        }
    }

    // ── Cache clear ──
    fun clearCache() {
        viewModelScope.launch {
            prefs.edit().remove("progress").remove("correct_count")
                .remove("wrong_count").apply()
            _state.update { it.copy(totalCorrect = 0, totalWrong = 0, accuracyPct = 0,
                successMsg = "Cache মুছে ফেলা হয়েছে") }
        }
    }

    // ── Logout ──
    fun logout() {
        viewModelScope.launch { session.clearUser() }
    }

    // ── Admin: load active users from Firebase ──
    private fun loadActiveUsers() {
        viewModelScope.launch {
            try {
                val users = UserSyncService.fetchActiveUsers()
                _state.update { it.copy(activeUsers = users) }
            } catch (e: Exception) {
                Log.e("MenuVM", "loadActiveUsers: ${e.message}")
            }
        }
    }

    // ── Admin: send FCM notification ──
    fun adminSendNotification(title: String, body: String, targetPhone: String?) {
        viewModelScope.launch {
            try {
                UserSyncService.sendAdminNotification(title, body, targetPhone)
                _state.update { it.copy(successMsg = "নোটিফিকেশন পাঠানো হয়েছে ✅") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Admin: switch to user view ──
    fun adminViewAs(phone: String) {
        viewModelScope.launch {
            try {
                val user = UserSyncService.fetchUser(phone)
                if (user != null) _state.update { it.copy(user = user) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearMsg() { _state.update { it.copy(error = null, successMsg = null) } }
}
