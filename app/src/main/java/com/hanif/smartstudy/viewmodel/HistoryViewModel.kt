package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.local.TestHistoryCache
import com.hanif.smartstudy.data.model.AlmostTopic
import com.hanif.smartstudy.data.model.GoalProgress
import com.hanif.smartstudy.data.model.ResultReviewItem
import com.hanif.smartstudy.data.model.StudyStats
import com.hanif.smartstudy.data.model.TestHistoryEntry
import com.hanif.smartstudy.data.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
//  HistoryViewModel — নিচের "History" ট্যাব
//  • মক/মডেল টেস্ট (প্রশ্নসহ লোকালি সংরক্ষিত) • Study Time • প্রায় শেষ টপিক • সব কার্যক্রম
// ─────────────────────────────────────────────────────────

data class HistoryExtra(
    val studyStats : StudyStats      = StudyStats(),
    val goal       : GoalProgress    = GoalProgress(),
    val streakDays : Int             = 0,
    val almost     : List<AlmostTopic> = emptyList(),
    val loaded     : Boolean         = false
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val cache = TestHistoryCache(app)
    private val repo  = ContentRepository(app)

    val history: StateFlow<List<TestHistoryEntry>> = cache.historyFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** যেসব টেস্টের প্রশ্ন-রিভিউ লোকালি সংরক্ষিত আছে */
    val reviewIds: StateFlow<Set<String>> = cache.reviewIdsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _extra = MutableStateFlow(HistoryExtra())
    val extra: StateFlow<HistoryExtra> = _extra

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val stats  = repo.getStudyStats()
            val goal   = repo.getGoalProgress()
            val streak = repo.getStreakInfo().streakDays
            val almost = repo.getAlmostCompletedTopics()
            _extra.value = HistoryExtra(stats, goal, streak, almost, true)
        }
    }

    suspend fun loadReview(entryId: String): List<ResultReviewItem> = cache.getReview(entryId)
}
