package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.repository.BuddyRepository
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
//  BuddyViewModel — Study Buddy ফিচারের state ও action
// ─────────────────────────────────────────────────────────

class BuddyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = BuddyRepository()
    private val session = SessionManager(app)
    private val cache   = ContentCache(app)

    private val _state = MutableStateFlow(BuddyState())
    val state: StateFlow<BuddyState> = _state.asStateFlow()

    private val _phoneInput = MutableStateFlow("")
    val phoneInput: StateFlow<String> = _phoneInput.asStateFlow()

    private var buddyWatchJob   : Job? = null
    private var progressWatchJob: Job? = null
    private var requestWatchJob : Job? = null
    private var studyingWatchJob: Job? = null

    init {
        val me = session.getCurrentUser()
        if (!me?.phone.isNullOrEmpty()) {
            loadAll()
        }
    }

    fun loadAll() {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            // আজকের progress আপলোড করো
            uploadMyProgress(me, myPhone)

            // বর্তমান buddy আছে কিনা চেক করো
            val buddy = repo.getMyBuddy(myPhone)
            _state.update { it.copy(hasBuddy = buddy != null, buddy = buddy, isLoading = false) }

            // Buddy link observe করো (realtime)
            buddyWatchJob?.cancel()
            buddyWatchJob = viewModelScope.launch {
                repo.observeMyBuddy(myPhone).collect { link ->
                    _state.update { it.copy(hasBuddy = link != null, buddy = link) }
                    progressWatchJob?.cancel()
                    // ── Study Nav Phase 6: বন্ধু এখন কোন Topic পড়ছে (Study Together) ──
                    studyingWatchJob?.cancel()
                    if (link != null) {
                        studyingWatchJob = viewModelScope.launch {
                            repo.observeBuddyStudying(link.buddyPhone).collect { st ->
                                _state.update { it.copy(buddyStudying = st) }
                            }
                        }
                    } else {
                        _state.update { it.copy(buddyStudying = null) }
                    }
                    if (link != null) {
                        progressWatchJob = viewModelScope.launch {
                            repo.observeBuddyProgress(link.buddyPhone).collect { prog ->
                                if (prog != null) {
                                    _state.update { it.copy(buddyProgress = prog) }
                                }
                            }
                        }
                    }
                }
            }

            // পেন্ডিং রিকোয়েস্ট observe করো
            requestWatchJob?.cancel()
            requestWatchJob = viewModelScope.launch {
                repo.observeMyRequests(myPhone).collect { requests ->
                    _state.update { it.copy(incomingRequest = requests.firstOrNull()) }
                }
            }
        }
    }

    // ── আজকের progress গণনা করে Firebase এ আপলোড করো ──────

    private suspend fun uploadMyProgress(me: com.hanif.smartstudy.data.model.User, myPhone: String) {
        val goal = session.getDailyGoal().coerceAtLeast(1)
        val done = cache.getTodayStudyMinutes()
        val pct  = ((done * 100) / goal).coerceIn(0, 100)
        val progress = BuddyProgress(
            phone       = myPhone,
            name        = me.displayName(),
            date        = todayString(),
            doneMinutes = done,
            goalMinutes = goal,
            progressPct = pct
        )
        repo.updateMyProgress(progress)
        _state.update { it.copy(myProgress = progress) }
    }

    /** HomeScreen/QuizViewModel থেকে কুইজ শেষ করার পর কল করা যেতে পারে progress sync করতে */
    fun refreshMyProgress() {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        viewModelScope.launch { uploadMyProgress(me, myPhone) }
    }

    // ── Search & Request ──────────────────────────────────

    fun onPhoneInputChange(value: String) {
        _state.update { it.copy(error = null) }
        _phoneInput.value = value
    }

    fun sendRequest(toPhone: String) {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        val cleaned = toPhone.trim()

        if (cleaned.isBlank()) return
        if (cleaned == myPhone) {
            _state.update { it.copy(error = "নিজেকে Study Buddy বানানো যাবে না") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val user = repo.findUserByPhone(cleaned)
            if (user == null) {
                _state.update { it.copy(isLoading = false, error = "এই নম্বরে কোনো অ্যাকাউন্ট নেই") }
                return@launch
            }
            val ok = repo.sendBuddyRequest(me, cleaned)
            _state.update {
                it.copy(
                    isLoading = false,
                    toast = if (ok) "✅ রিকোয়েস্ট পাঠানো হয়েছে! ${user.displayName()} accept করলে জানতে পারবে।"
                            else "❌ রিকোয়েস্ট পাঠাতে সমস্যা হয়েছে, আবার চেষ্টা করো"
                )
            }
        }
    }

    fun acceptRequest(request: BuddyRequest) {
        val me = session.getCurrentUser() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val ok = repo.acceptBuddyRequest(me, request)
            _state.update {
                it.copy(
                    isLoading = false,
                    incomingRequest = null,
                    toast = if (ok) "🎉 ${request.fromName} এখন তোমার Study Buddy!" else "❌ সমস্যা হয়েছে, আবার চেষ্টা করো"
                )
            }
            if (ok) loadAll()
        }
    }

    fun declineRequest(request: BuddyRequest) {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        viewModelScope.launch {
            repo.declineBuddyRequest(myPhone, request)
            _state.update { it.copy(incomingRequest = null) }
        }
    }

    // ── Nudge পাঠাও ────────────────────────────────────────

    fun sendNudge() {
        val me     = session.getCurrentUser() ?: return
        val buddy  = _state.value.buddy ?: return
        viewModelScope.launch {
            val ok = repo.sendNudge(me.displayName(), buddy.buddyPhone)
            _state.update {
                it.copy(toast = if (ok) "👀 ${buddy.buddyName} কে তাগাদা পাঠানো হয়েছে!"
                                 else "❌ পাঠাতে সমস্যা হয়েছে")
            }
        }
    }

    // ── Study Nav Phase 6: Study Together ───────────────────

    /** Study-তে Topic খুললে — বন্ধু থাকলে "এখন পড়ছে" জানিয়ে রাখা (বন্ধু না থাকলে কিছুই করে না) */
    fun setStudying(subject: String, topic: String) {
        val me = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        if (!_state.value.hasBuddy) return
        viewModelScope.launch { repo.setStudying(myPhone, me.displayName(), subject, topic) }
    }

    fun clearStudying() {
        val myPhone = session.getCurrentUser()?.phone ?: return
        if (!_state.value.hasBuddy) return
        viewModelScope.launch { repo.clearStudying(myPhone) }
    }

    /** 🤝 "এই lessonটা একসাথে পড়বি?" */
    fun inviteToStudy(subject: String, topic: String) {
        val me    = session.getCurrentUser() ?: return
        val buddy = _state.value.buddy ?: return
        viewModelScope.launch {
            val ok = repo.sendStudyInvite(me.displayName(), buddy.buddyPhone, subject, topic)
            _state.update {
                it.copy(toast = if (ok) "🤝 ${buddy.buddyName} কে আমন্ত্রণ পাঠানো হয়েছে!" else "❌ আমন্ত্রণ পাঠাতে সমস্যা হয়েছে")
            }
        }
    }

    // ── Buddy ব্রেক করো ─────────────────────────────────────

    fun removeBuddy() {
        val me    = session.getCurrentUser() ?: return
        val myPhone = me.phone ?: return
        val buddy = _state.value.buddy ?: return
        viewModelScope.launch {
            repo.removeBuddy(myPhone, buddy.buddyPhone)
            _state.update {
                it.copy(hasBuddy = false, buddy = null, buddyProgress = BuddyProgress(),
                        toast = "Study Buddy সম্পর্ক শেষ করা হয়েছে")
            }
        }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun todayString(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }
}
