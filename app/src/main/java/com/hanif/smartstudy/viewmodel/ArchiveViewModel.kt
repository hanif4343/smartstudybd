package com.hanif.smartstudy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.ArchiveQuestion
import com.hanif.smartstudy.data.model.ArchiveSheet
import com.hanif.smartstudy.data.model.ArchiveTopicRef
import com.hanif.smartstudy.data.model.SubjectRef
import com.hanif.smartstudy.data.model.TopicRef
import com.hanif.smartstudy.data.remote.ArchiveGasService
import com.hanif.smartstudy.data.remote.GasContentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ── Archive সেকশনের ViewModel — সম্পূর্ণ নতুন, QuizViewModel.kt-কে স্পর্শ করা হয়নি ──
 *
 * সেফটি রিমাইন্ডার: এই ViewModel কখনো কোনো row delete করার কল করে না। শুধু
 * markDuplicate (ট্যাগ) আর moveToActive (কপি+ট্যাগ) — দুটোই non-destructive।
 */

data class ArchiveUiState(
    val sheet             : ArchiveSheet = ArchiveSheet.QUIZ_ARCHIVE,
    val isLoadingTopics    : Boolean = false,
    val archiveTopics      : List<ArchiveTopicRef> = emptyList(),
    val subjects           : List<SubjectRef> = emptyList(),   // Move-to dropdown-এর জন্য (Active Subjects, Archive-এর সাথে ১০০% মেলে)
    val activeTopics       : List<TopicRef> = emptyList(),      // Move-to dropdown-এর জন্য (Active Topics — existing হলে dropdown, না হলে নতুন লেখা যাবে)
    val selectedSubjectId  : String? = null,     // Quiz-এর মতো ৩-লেভেল ড্রিল-ডাউন: Subject → Topic → Questions
    val selectedTopic      : ArchiveTopicRef? = null,
    val isLoadingQuestions : Boolean = false,
    val questions          : List<ArchiveQuestion> = emptyList(),
    val duplicateIds       : Set<String> = emptySet(),   // per-question "ডুপ্লিকেট" বাটন (পুরো পেজ একসাথে "Move to Active"-এর সময় ব্যবহৃত হয়)
    val selectMode         : Boolean = false,             // "সিলেক্ট করে Move" মোড — ভুল Subject/Topic-এ থাকা বিচ্ছিন্ন প্রশ্নের জন্য (original Quiz-এর isSelectMode-এর মতো)
    val selectedForMove    : Set<String> = emptySet(),    // selectMode-এ যেগুলো টিক দেওয়া, আলাদা destination-এ move হবে
    val cursor             : Int = 0,
    val hasMore            : Boolean = false,
    val total              : Int = 0,
    val isSorted           : Boolean = false,
    val isBusy             : Boolean = false,            // mark/move ইন-ফ্লাইট
    val error              : String? = null,
    val message            : String? = null
)

class ArchiveViewModel : ViewModel() {

    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    /** Archive হোম-এ ঢোকার সময় একবার — subject/topic দুই-ধরনের রেফারেন্স লিস্ট লোড করে */
    fun loadInitial(sheet: ArchiveSheet) {
        _state.update { it.copy(sheet = sheet, isLoadingTopics = true, error = null) }
        viewModelScope.launch {
            val archiveTopicsDeferred = ArchiveGasService.fetchArchiveTopics()
            val refData = GasContentService.fetchReferenceData() // existing, read-only, সেফ
            _state.update {
                it.copy(
                    isLoadingTopics = false,
                    archiveTopics   = archiveTopicsDeferred,
                    subjects        = refData?.subjects ?: emptyList(),
                    activeTopics    = refData?.topics ?: emptyList(),
                    error           = if (archiveTopicsDeferred.isEmpty()) "Archive topic লোড করা যায়নি — নেটওয়ার্ক/GAS চেক করুন" else null
                )
            }
        }
    }

    fun switchSheet(sheet: ArchiveSheet) {
        if (_state.value.sheet == sheet) return
        _state.update {
            ArchiveUiState(sheet = sheet, isLoadingTopics = true,
                subjects = it.subjects, activeTopics = it.activeTopics) // subject list দুই sheet-এই এক, রিফেচ লাগবে না
        }
        viewModelScope.launch {
            val topics = ArchiveGasService.fetchArchiveTopics()
            _state.update { it.copy(isLoadingTopics = false, archiveTopics = topics) }
        }
    }

    /** Subject কার্ডে ট্যাপ — Topic লিস্টে যাওয়া (Quiz-এর মতোই ৩-লেভেল ড্রিল-ডাউন) */
    fun selectSubject(subjectId: String) {
        _state.update { it.copy(selectedSubjectId = subjectId) }
    }

    /** Topic লিস্ট থেকে Subject লিস্টে ফেরত */
    fun backToSubjects() {
        _state.update { it.copy(selectedSubjectId = null) }
    }

    /** একটা টপিক বাছাই করলে — প্রথম পেজ লোড */
    fun selectTopic(topic: ArchiveTopicRef) {
        _state.update {
            it.copy(
                selectedTopic = topic, questions = emptyList(), duplicateIds = emptySet(),
                selectMode = false, selectedForMove = emptySet(),
                cursor = 0, hasMore = false, total = 0, isSorted = false, error = null, message = null
            )
        }
        loadNextPage()
    }

    /** Question লিস্ট থেকে Topic লিস্টে ফেরত — selectedSubjectId ঠিক থাকে, তাই একই
     * Subject-এর Topic কার্ডগুলোতেই ফেরত যায়, Subject লিস্টে না */
    fun backToTopics() {
        _state.update {
            it.copy(selectedTopic = null, questions = emptyList(), duplicateIds = emptySet(),
                selectMode = false, selectedForMove = emptySet())
        }
    }

    /** পরের ৫০টা (বা প্রথমবার) — resume-safe cursor দিয়ে, review_status ট্যাগ হওয়া
     * প্রশ্নগুলো এমনিতেই ব্যাকএন্ডে স্কিপ হয়ে যায়, তাই page ভাঙে না */
    fun loadNextPage() {
        val topic = _state.value.selectedTopic ?: return
        if (_state.value.isLoadingQuestions) return
        _state.update { it.copy(isLoadingQuestions = true, error = null) }
        viewModelScope.launch {
            val result = ArchiveGasService.fetchQuestionsPage(
                sheet = _state.value.sheet, topicId = topic.topicId ?: "", cursor = _state.value.cursor
            )
            if (result.error != null) {
                _state.update { it.copy(isLoadingQuestions = false, error = result.error) }
            } else {
                _state.update {
                    it.copy(
                        isLoadingQuestions = false,
                        questions   = it.questions + result.rows,   // আগের যা loaded ছিল তার সাথে জোড়া লাগে (একই পেজ-সেশনে "আরও দেখাও" করলে)
                        duplicateIds = emptySet(),
                        cursor      = result.nextCursor,
                        hasMore     = result.hasMore,
                        total       = result.total,
                        isSorted    = false
                    )
                }
            }
        }
    }

    /** A-Z Sort টগল — পুরো টপিকের unreviewed সেট একসাথে এনে question-টেক্সট দিয়ে সাজায়।
     * এটা display-only, sheet-এর কোনো row নড়ে না। */
    fun toggleSort() {
        val topic = _state.value.selectedTopic ?: return
        if (_state.value.isSorted) {
            // ── আবার পেজিনেটেড মোডে ফিরে যাওয়া — শুরু থেকে রিলোড ──
            _state.update { it.copy(questions = emptyList(), duplicateIds = emptySet(), cursor = 0, hasMore = false, isSorted = false) }
            loadNextPage()
            return
        }
        _state.update { it.copy(isLoadingQuestions = true, error = null) }
        viewModelScope.launch {
            val result = ArchiveGasService.fetchQuestionsSorted(_state.value.sheet, topic.topicId ?: "")
            if (result.error != null) {
                _state.update { it.copy(isLoadingQuestions = false, error = result.error) }
            } else {
                _state.update {
                    it.copy(
                        isLoadingQuestions = false, questions = result.rows, duplicateIds = emptySet(),
                        hasMore = false, total = result.total, isSorted = true
                    )
                }
            }
        }
    }

    fun toggleDuplicate(id: String) {
        _state.update {
            val cur = it.duplicateIds
            it.copy(duplicateIds = if (id in cur) cur - id else cur + id)
        }
    }

    /** Mark All — চলতি লিস্টের সবকটা "ডুপ্লিকেট" হিসেবে সিলেক্ট/আনসিলেক্ট (টগল) */
    fun toggleMarkAll() {
        _state.update {
            val allIds = it.questions.map { q -> q.id }.toSet()
            it.copy(duplicateIds = if (it.duplicateIds.containsAll(allIds) && allIds.isNotEmpty()) emptySet() else allIds)
        }
    }

    fun clearMessages() { _state.update { it.copy(error = null, message = null) } }

    // ── "সিলেক্ট করে Move" — যে প্রশ্নগুলো এই Archive টপিকে থাকলেও আসলে ভুল
    // Subject/Topic-এ পড়ে আছে, সেগুলো আলাদাভাবে বেছে ভিন্ন destination-এ পাঠানোর
    // জন্য (original Quiz-এর isSelectMode + AdminMoveQuestionsPickerDialog-এর
    // প্যাটার্ন)। এটা duplicateIds থেকে সম্পূর্ণ আলাদা — duplicateIds পুরো পেজ
    // একসাথে "Move to Active" করার সময় ডুপ্লিকেট বাদ দিতে ব্যবহার হয়, এখানে বরং
    // নির্দিষ্ট কয়েকটা প্রশ্ন এখনই, ভিন্ন subject/topic-এ সরাসরি সরানো হয়। ──
    fun toggleSelectMode() {
        _state.update {
            if (it.selectMode) it.copy(selectMode = false, selectedForMove = emptySet())
            else it.copy(selectMode = true)
        }
    }

    fun toggleSelectForMove(id: String) {
        _state.update {
            val cur = it.selectedForMove
            it.copy(selectedForMove = if (id in cur) cur - id else cur + id)
        }
    }

    /**
     * সিলেক্ট করা (ভুল Subject/Topic-এ থাকা) প্রশ্নগুলো নির্দিষ্ট একটা
     * newSubject/newSubTopic-এ সরাসরি move করে — duplicateIds/finishPage flow-কে
     * প্রভাবিত করে না। সফল হলে ওই প্রশ্নগুলো লোকাল লিস্ট থেকে সরাসরি বাদ দেওয়া হয়
     * (আবার fetch করার দরকার নেই, ঠিক কোন id-গুলো সরেছে সেটা জানাই আছে)।
     */
    fun moveSelected(newSubject: String, newSubTopic: String) {
        val s = _state.value
        val ids = s.selectedForMove.toList()
        if (ids.isEmpty()) return
        if (newSubject.isBlank() || newSubTopic.isBlank()) {
            _state.update { it.copy(error = "Subject ও Topic দুটোই লাগবে") }
            return
        }
        _state.update { it.copy(isBusy = true, error = null, message = null) }
        viewModelScope.launch {
            val res = ArchiveGasService.moveToActive(s.sheet, ids, newSubject.trim(), newSubTopic.trim())
            if (res.error != null) {
                _state.update { it.copy(isBusy = false, error = res.error) }
            } else {
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "✅ ${res.moved} টা প্রশ্ন \"$newSubject / $newSubTopic\"-এ মুভ হয়েছে",
                        questions       = it.questions.filterNot { q -> q.id in ids },
                        duplicateIds    = it.duplicateIds - ids.toSet(),
                        selectedForMove = emptySet(),
                        selectMode      = false
                    )
                }
            }
        }
    }

    /**
     * "Move to Active" কনফার্ম — একসাথে দুটো কাজ:
     *  ১) duplicateIds (চেক করা) গুলোতে review_status="duplicate"
     *  ২) বাকি (চেক-না-করা, ভালো) গুলো Active শিটে newSubject/newSubTopic-এর আন্ডারে কপি
     * শেষে সফল হলে current page থেকে সব বাদ দিয়ে stored cursor দিয়ে পরের পেজ লোড হয় —
     * তাই "৫০টা মুভ করলে পরের পেজে যাওয়া যায় না" সমস্যাটা structurally থাকে না,
     * কারণ কোনো row shift হয়নি, শুধু ট্যাগ বসেছে। ──
     */
    fun finishPage(newSubject: String, newSubTopic: String) {
        val s = _state.value
        if (newSubject.isBlank() || newSubTopic.isBlank()) {
            _state.update { it.copy(error = "Subject ও Topic দুটোই লাগবে") }
            return
        }
        val duplicateIds = s.duplicateIds.toList()
        val moveIds      = s.questions.map { it.id }.filter { it !in s.duplicateIds }
        if (duplicateIds.isEmpty() && moveIds.isEmpty()) return

        _state.update { it.copy(isBusy = true, error = null, message = null) }
        viewModelScope.launch {
            var errMsg: String? = null
            var movedCount = 0

            if (duplicateIds.isNotEmpty()) {
                val ok = ArchiveGasService.markDuplicate(s.sheet, duplicateIds)
                if (!ok) errMsg = "কিছু প্রশ্ন duplicate মার্ক করা যায়নি"
            }
            if (moveIds.isNotEmpty()) {
                val res = ArchiveGasService.moveToActive(s.sheet, moveIds, newSubject.trim(), newSubTopic.trim())
                if (res.error != null) {
                    errMsg = (errMsg?.plus(" | ") ?: "") + res.error
                } else {
                    movedCount = res.moved
                }
            }

            if (errMsg != null) {
                _state.update { it.copy(isBusy = false, error = errMsg) }
            } else {
                // ── সফল — এই পেজ শেষ, stored cursor দিয়েই পরের ৫০টা আনা হবে ──
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "✅ $movedCount টা Active-এ মুভ হয়েছে" +
                                if (duplicateIds.isNotEmpty()) ", ${duplicateIds.size} টা duplicate মার্ক হয়েছে" else "",
                        questions = emptyList(), duplicateIds = emptySet()
                    )
                }
                if (s.isSorted) {
                    // sorted মোডে পুরো টপিক একসাথে হ্যান্ডল হয়ে গেছে — রিফ্রেশ করে দেখাই বাকি আছে কিনা
                    toggleSort() // sorted বন্ধ করে পেজিনেটেড মোডে ফিরে যাবে (cursor=0 থেকে, যা এখনো unreviewed সেটাই আসবে)
                } else {
                    loadNextPage()
                }
            }
        }
    }
}
