package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.RoutineCache
import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.DailyRoutine
import com.hanif.smartstudy.data.model.RoutineSubjectOption
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.data.repository.DataState
import com.hanif.smartstudy.util.AudienceFilter.forUser
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.SoundManager
import com.hanif.smartstudy.widget.RoutineWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
//  RoutineViewModel — হোম স্ক্রিন চেকলিস্ট (আজকের পড়ার রুটিন)
// ─────────────────────────────────────────────────────────

class RoutineViewModel(app: Application) : AndroidViewModel(app) {

    private val cache   = RoutineCache(app)
    private val repo    = ContentRepository(app)
    private val session = SessionManager(app)

    private val _state = MutableStateFlow(DailyRoutine())
    val state: StateFlow<DailyRoutine> = _state.asStateFlow()

    // ── "বিষয় (ঐচ্ছিক)" ও "SubTopic" dropdown — ইউজারের audience
    // (classLevel/userType) অনুযায়ী ফিল্টার করা Subject/SubTopic লিস্ট ──
    private val _subjectOptions = MutableStateFlow<List<RoutineSubjectOption>>(emptyList())
    val subjectOptions: StateFlow<List<RoutineSubjectOption>> = _subjectOptions.asStateFlow()

    init {
        load()
        loadSubjectOptions()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = cache.getTodayRoutine()
            syncWidget()
        }
    }

    /**
     * AppContent (quiz + qbank + study) থেকে — লগইন করা ইউজারের
     * audience (classLevel/userType) অনুযায়ী ফিল্টার করে — Subject ও
     * তার অধীনে SubTopic-গুলোর তালিকা তৈরি করো। এটাই "বিষয় (ঐচ্ছিক)"
     * dropdown-এ দেখানো হবে — ফ্রি-টেক্সট নয়।
     */
    fun loadSubjectOptions() {
        viewModelScope.launch {
            val content: AppContent = when (val result = repo.getContent()) {
                is DataState.Success -> result.data
                else -> ContentRepository.getMemCache() ?: AppContent()
            }
            if (content.isEmpty()) return@launch

            val user     = session.getCurrentUser()
            val adminTag = if (user?.isAdmin() == true) session.getAdminAudienceTag() else ""
            val filtered = content.forUser(user, adminTag)

            // subject -> distinct subTopic সেট (insertion order বজায় রেখে)
            val map = linkedMapOf<String, LinkedHashSet<String>>()
            fun collect(subject: String?, subTopic: String?) {
                val subj = subject?.trim().orEmpty()
                if (subj.isEmpty()) return
                val set = map.getOrPut(subj) { linkedSetOf() }
                val st = subTopic?.trim().orEmpty()
                if (st.isNotEmpty()) set.add(st)
            }
            filtered.quiz.forEach  { collect(it.subject, it.subTopic) }
            filtered.qbank.forEach { collect(it.subject, it.subTopic) }
            filtered.study.forEach { collect(it.subject, it.subTopic) }

            _subjectOptions.value = map
                .map { (subject, subTopics) -> RoutineSubjectOption(subject, subTopics.sorted()) }
                .sortedBy { it.subject }
        }
    }

    fun addItem(title: String, subject: String = "", subTopic: String = "", minutes: Int = 20) {
        val cleaned = title.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            cache.addItem(cleaned, subject.trim(), subTopic.trim(), minutes)
            _state.value = cache.getTodayRoutine()
            syncWidget()
        }
    }

    fun toggleItem(id: String) {
        viewModelScope.launch {
            cache.toggleItem(id)
            val updated = cache.getTodayRoutine()
            _state.value = updated

            // টিক দেওয়ার সময় সাউন্ড ফিডব্যাক
            val item = updated.items.find { it.id == id }
            if (item?.done == true) SoundManager.playCorrect()

            syncWidget()
        }
    }

    // ── Bottom sheet থেকে "পড়া শেষ করেছি" বাটনে — idempotent, শুধু done=true করে,
    //    আগে থেকে done থাকলে toggle করে আবার false করে না ──
    fun markDone(id: String) {
        viewModelScope.launch {
            val current = _state.value.items.find { it.id == id }
            if (current?.done == true) return@launch
            cache.toggleItem(id)
            val updated = cache.getTodayRoutine()
            _state.value = updated
            SoundManager.playCorrect()
            syncWidget()
        }
    }

    fun removeItem(id: String) {
        viewModelScope.launch {
            cache.removeItem(id)
            _state.value = cache.getTodayRoutine()
            syncWidget()
        }
    }

    // ── Home screen widget আপডেট করো ──
    private fun syncWidget() {
        RoutineWidgetProvider.updateAll(getApplication())
    }
}
