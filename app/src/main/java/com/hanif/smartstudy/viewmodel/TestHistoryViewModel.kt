package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.TestHistoryCache
import com.hanif.smartstudy.data.model.TestHistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
//  TestHistoryViewModel — Profile মেনুর টেস্ট হিস্ট্রি
// ─────────────────────────────────────────────────────────

class TestHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val cache = TestHistoryCache(app)

    val history: StateFlow<List<TestHistoryEntry>> = cache.historyFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearHistory() {
        viewModelScope.launch { cache.clearHistory() }
    }
}
