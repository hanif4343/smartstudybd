package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.RoutineCache
import com.hanif.smartstudy.data.model.DailyRoutine
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

    private val cache = RoutineCache(app)

    private val _state = MutableStateFlow(DailyRoutine())
    val state: StateFlow<DailyRoutine> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = cache.getTodayRoutine()
            syncWidget()
        }
    }

    fun addItem(title: String, subject: String = "", minutes: Int = 20) {
        val cleaned = title.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            cache.addItem(cleaned, subject.trim(), minutes)
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
