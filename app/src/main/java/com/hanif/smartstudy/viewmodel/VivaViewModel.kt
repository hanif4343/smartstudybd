package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.model.TestHistoryEntry
import com.hanif.smartstudy.data.local.TestHistoryCache
import com.hanif.smartstudy.data.remote.WrittenAnswerAiService
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VivaStage { ASK_SUBJECT, ASK_SUBTOPIC, ASKING, GRADING, ENDED }

data class VivaLogEntry(
    val question      : String,
    val studentAnswer : String,
    val verdict        : String,   // CORRECT | PARTIAL | WRONG | SKIPPED
    val correctAnswer  : String
)

data class VivaUiState(
    val stage           : VivaStage = VivaStage.ASK_SUBJECT,
    val studentName      : String = "",
    val subject          : String = "",
    val subTopic         : String = "",
    val currentQuestion  : QuestionItem? = null,
    val lastVerdict      : String? = null,
    val lastFeedback     : String? = null,
    val promptText       : String = "",
    val log              : List<VivaLogEntry> = emptyList(),
    val correctCount     : Int = 0,
    val wrongCount       : Int = 0,
    val skippedCount     : Int = 0,
    val isBusy           : Boolean = false,
    val error            : String? = null,
    val hasAnyKey        : Boolean = true,
    val xpEarned         : Int = 0
)

/**
 * ══════════════════════════════════════════════════════════════════
 *  VivaViewModel — কথোপকথনমূলক ভয়েস "মৌখিক পরীক্ষা" (Viva Mode)।
 *
 *  ফ্লো: AI ছাত্রের নাম ধরে জিজ্ঞেস করে কোন বিষয়/টপিক চায় (ভয়েসে) → AI সেই
 *  বিষয়ের কথা বুঝে (স্পোকেন টেক্সট ↔ আসল Subject/SubTopic list AI দিয়ে মিলিয়ে,
 *  exact string-match না — colloquial উচ্চারণ সহনশীল) → Quiz sheet থেকে random
 *  প্রশ্ন → ছাত্র ভয়েসে উত্তর দেয় → voice-aware grading (verdict+feedback একসাথে)
 *  → পরের প্রশ্ন — এভাবে চলতেই থাকে যতক্ষণ না ছাত্র "বিষয় পরিবর্তন করুন"/
 *  "টপিক পরিবর্তন করুন"/"সমাপ্ত করো" চাপে।
 *
 *  আগের voice-AI ফিচারে যেসব বাগ হয়েছিল (TTS key কনফ্লিক্ট, দেরিতে-আসা
 *  রেসপন্স ভুল স্ক্রিনে বসে যাওয়া) — এই ViewModel শুরু থেকেই সেই ফিক্সগুলো
 *  মাথায় রেখে বানানো: প্রতিটা speak()-এ ইউনিক key, প্রতিটা stage-পরিবর্তনে
 *  activeJob cancel, sessionActive flag দিয়ে দেরিতে-আসা রেসপন্স ব্লক করা।
 * ══════════════════════════════════════════════════════════════════
 */
class VivaViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val XP_PER_CORRECT = 3
        private const val SHEET = "Quiz"
    }

    private val session = SessionManager(app)
    private val repo    = ContentRepository(app)

    private val _state = MutableStateFlow(VivaUiState())
    val state: StateFlow<VivaUiState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var sessionActive = true
    private val askedIds = mutableSetOf<String>()
    private var subjectOptions: List<String> = emptyList()
    private var subTopicOptions: List<String> = emptyList()

    fun start() {
        sessionActive = true
        val name = session.getCurrentUser()?.name?.trim().takeUnless { it.isNullOrBlank() } ?: "ছাত্র"
        _state.update { it.copy(studentName = name, hasAnyKey = session.getAiApiKeys().hasAnyKey()) }
        askSubject()
    }

    // ── ধাপ ১: বিষয় জিজ্ঞাসা ──
    private fun askSubject() {
        activeJob?.cancel()
        val name = _state.value.studentName
        _state.update {
            it.copy(
                stage = VivaStage.ASK_SUBJECT, subject = "", subTopic = "",
                currentQuestion = null, lastVerdict = null, lastFeedback = null,
                promptText = "$name, তুমি কোন বিষয়ের ওপর প্র্যাকটিস করতে চাও?",
                isBusy = false
            )
        }
        speakCurrentPrompt()
        activeJob = viewModelScope.launch {
            subjectOptions = repo.getRoomSubjectCounts(SHEET).map { it.subject }
        }
    }

    // ── ধাপ ২: টপিক জিজ্ঞাসা (subject আগে থেকেই বেছে নেওয়া আছে) ──
    private fun askSubTopic() {
        activeJob?.cancel()
        val subject = _state.value.subject
        _state.update {
            it.copy(
                stage = VivaStage.ASK_SUBTOPIC, subTopic = "",
                promptText = "\"$subject\" থেকে কোন টপিকের ওপর প্র্যাকটিস করতে চাও?",
                isBusy = false
            )
        }
        speakCurrentPrompt()
        activeJob = viewModelScope.launch {
            subTopicOptions = repo.getRoomSubTopicCounts(SHEET, subject).map { it.subTopic }
        }
    }

    /** মাইক/টাইপ — যেকোনো একটা টেক্সট ইনপুট, বর্তমান stage অনুযায়ী রুট হয়। প্রশ্ন
     * চলাকালীন কিছু ভয়েস-কমান্ডও ধরা হয় (বাটনের পাশাপাশি, ঐচ্ছিক শর্টকাট হিসেবে)। */
    fun onVoiceInput(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        if (_state.value.stage == VivaStage.ASKING) {
            val lower = trimmed.lowercase()
            when {
                lower.contains("বিষয় পরিবর্তন") || lower.contains("change subject") -> { changeSubject(); return }
                lower.contains("টপিক পরিবর্তন") || lower.contains("change topic")   -> { changeTopic(); return }
                lower.contains("সমাপ্ত") || lower.contains("শেষ কর") || lower.contains("বন্ধ কর") || lower == "stop" -> { endSession(); return }
                lower.contains("পাস") || lower == "pass" || lower.contains("জানি না") || lower.contains("skip")     -> { skipQuestion(); return }
            }
        }

        when (_state.value.stage) {
            VivaStage.ASK_SUBJECT  -> resolveSubject(trimmed)
            VivaStage.ASK_SUBTOPIC -> resolveSubTopic(trimmed)
            VivaStage.ASKING       -> gradeAnswer(trimmed)
            else -> {}
        }
    }

    private fun resolveSubject(spokenText: String) {
        val keys = session.getAiApiKeys()
        if (!keys.hasAnyKey()) {
            _state.update { it.copy(hasAnyKey = false, error = "Viva চালাতে Settings-এ অন্তত একটা AI key (Groq/Mistral/Cerebras/Gemini) যোগ করো।") }
            return
        }
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, error = null) }
        activeJob = viewModelScope.launch {
            val options = subjectOptions.ifEmpty {
                repo.getRoomSubjectCounts(SHEET).map { it.subject }.also { subjectOptions = it }
            }
            val matched = WrittenAnswerAiService.resolveFromList(spokenText, options, keys)
            if (!sessionActive || _state.value.stage != VivaStage.ASK_SUBJECT) return@launch
            if (matched != null) {
                _state.update { it.copy(subject = matched, isBusy = false) }
                askSubTopic()
            } else {
                _state.update {
                    it.copy(isBusy = false, promptText = "বুঝতে পারিনি — আবার বলো, কোন বিষয়ে পড়তে চাও?")
                }
                speakCurrentPrompt()
            }
        }
    }

    private fun resolveSubTopic(spokenText: String) {
        val keys = session.getAiApiKeys()
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, error = null) }
        activeJob = viewModelScope.launch {
            val subject = _state.value.subject
            val options = subTopicOptions.ifEmpty {
                repo.getRoomSubTopicCounts(SHEET, subject).map { it.subTopic }.also { subTopicOptions = it }
            }
            val matched = WrittenAnswerAiService.resolveFromList(spokenText, options, keys)
            if (!sessionActive || _state.value.stage != VivaStage.ASK_SUBTOPIC) return@launch
            if (matched != null) {
                _state.update { it.copy(subTopic = matched, isBusy = false) }
                askNextQuestion()
            } else {
                _state.update {
                    it.copy(isBusy = false, promptText = "বুঝতে পারিনি — আবার বলো, কোন টপিকে পড়তে চাও?")
                }
                speakCurrentPrompt()
            }
        }
    }

    // ── ধাপ ৩: Quiz sheet থেকে random প্রশ্ন — এই সেশনে আগে-জিজ্ঞাসা-করা প্রশ্ন এড়িয়ে ──
    private fun askNextQuestion() {
        activeJob?.cancel()
        _state.update {
            it.copy(stage = VivaStage.ASKING, isBusy = true, lastVerdict = null, lastFeedback = null)
        }
        activeJob = viewModelScope.launch {
            val q = pickRandomQuestion()
            if (!sessionActive) return@launch
            if (q == null) {
                _state.update {
                    it.copy(isBusy = false, promptText = "এই টপিকে প্রশ্ন পাওয়া যায়নি — অন্য বিষয়/টপিক বেছে নাও।")
                }
                speakCurrentPrompt()
                return@launch
            }
            askedIds += q.id
            val qText = q.question.ifBlank { q.explanation }
            val optionsText = if (q.isMcq())
                " অপশন: ক) ${q.optionA} খ) ${q.optionB} গ) ${q.optionC} ঘ) ${q.optionD}।"
            else ""
            _state.update { it.copy(currentQuestion = q, isBusy = false, promptText = "$qText$optionsText") }
            speakCurrentPrompt()
        }
    }

    private fun gradeAnswer(spokenAnswer: String) {
        val q = _state.value.currentQuestion ?: return
        val keys = session.getAiApiKeys()
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, stage = VivaStage.GRADING) }
        activeJob = viewModelScope.launch {
            val verdict = WrittenAnswerAiService.gradeVivaAnswer(
                question      = q.question.ifBlank { q.explanation },
                correctAnswer = q.answer.ifBlank { q.explanation },
                explanation   = q.explanation,
                studentAnswer = spokenAnswer,
                keys          = keys
            )
            if (!sessionActive) return@launch
            if (verdict == null) {
                _state.update {
                    it.copy(isBusy = false, stage = VivaStage.ASKING, error = "AI থেকে ফলাফল পাওয়া যায়নি — আবার চেষ্টা করো।")
                }
                return@launch
            }
            recordResult(q, spokenAnswer, verdict.verdict)
            _state.update {
                it.copy(isBusy = false, lastVerdict = verdict.verdict, lastFeedback = verdict.feedback, promptText = verdict.feedback)
            }
            speakCurrentPrompt()
            delay(2600)   // ফিডব্যাক শোনার/পড়ার সময় দেওয়া, তারপর পরের প্রশ্নে
            if (!sessionActive) return@launch
            askNextQuestion()
        }
    }

    /** "পাস"/স্কিপ — না জানলে সঠিক উত্তর দেখিয়ে পরের প্রশ্নে চলে যাওয়া (real viva-র মতোই) */
    fun skipQuestion() {
        val q = _state.value.currentQuestion ?: return
        activeJob?.cancel()
        TtsManager.stop()
        recordResult(q, "", "SKIPPED")
        askNextQuestion()
    }

    private fun recordResult(q: QuestionItem, studentAnswer: String, verdict: String) {
        val entry = VivaLogEntry(
            question      = q.question.ifBlank { q.explanation },
            studentAnswer = studentAnswer,
            verdict       = verdict,
            correctAnswer = q.answer.ifBlank { q.explanation }
        )
        _state.update {
            it.copy(
                log          = it.log + entry,
                correctCount = it.correctCount + if (verdict == "CORRECT") 1 else 0,
                wrongCount   = it.wrongCount   + if (verdict == "WRONG" || verdict == "PARTIAL") 1 else 0,
                skippedCount = it.skippedCount + if (verdict == "SKIPPED") 1 else 0
            )
        }
    }

    /** "বিষয় পরিবর্তন করুন" — এখনকার কথোপকথন সাথে সাথে বন্ধ, নতুন করে বিষয় জিজ্ঞাসা */
    fun changeSubject() {
        activeJob?.cancel()
        TtsManager.stop()
        askSubject()
    }

    /** "টপিক পরিবর্তন করুন" — subject অক্ষত, শুধু নতুন করে টপিক জিজ্ঞাসা */
    fun changeTopic() {
        activeJob?.cancel()
        TtsManager.stop()
        askSubTopic()
    }

    /** "সমাপ্ত করো" — ফলাফল সংরক্ষণ (History + XP), সামারি স্ক্রিন দেখানো */
    fun endSession() {
        activeJob?.cancel()
        TtsManager.stop()
        sessionActive = false
        _state.update { it.copy(stage = VivaStage.ENDED, isBusy = true) }
        viewModelScope.launch {
            saveHistoryAndXp()
            _state.update { it.copy(isBusy = false) }
        }
    }

    private suspend fun saveHistoryAndXp() {
        val st = _state.value
        val total = st.correctCount + st.wrongCount + st.skippedCount
        if (total == 0) return

        val xp = st.correctCount * XP_PER_CORRECT
        val phone = session.getCurrentUser()?.phone
        if (!phone.isNullOrBlank() && xp > 0) repo.awardXp(phone, xp)
        repo.markTodayActivity()

        val topicLabel = if (st.subTopic.isNotBlank()) "${st.subject} - ${st.subTopic}" else st.subject
        val entry = TestHistoryEntry(
            id           = "viva_${System.currentTimeMillis()}",
            timestamp    = System.currentTimeMillis(),
            mode         = "VIVA",
            topics       = listOf(topicLabel),
            total        = total,
            correct      = st.correctCount,
            wrong        = st.wrongCount,
            skipped      = st.skippedCount,
            timeTakenSec = 0,
            xpEarned     = xp,
            source       = "viva"
        )
        TestHistoryCache(getApplication()).addEntry(entry)
        _state.update { it.copy(xpEarned = xp) }
    }

    private fun speakCurrentPrompt() {
        val text = _state.value.promptText
        if (text.isBlank()) return
        // ── প্রতিটা utterance-এর জন্য ইউনিক key — আগের voice-AI ফিচারে "একই key
        // দিলে pause/resume হয়ে যায়" বাগ থেকে শেখা শিক্ষা ──
        TtsManager.speak(text, "viva_${System.nanoTime()}")
    }

    private suspend fun pickRandomQuestion(): QuestionItem? {
        val subject  = _state.value.subject
        val subTopic = _state.value.subTopic
        val total = repo.getRoomTotalCount(SHEET, subject, subTopic, "all")
        if (total <= 0) return null
        repeat(8) {
            val offset = (0 until total).random()
            val q = repo.getRoomPagedQuestions(SHEET, subject, subTopic, "all", offset, 1).firstOrNull()
            if (q != null && q.id !in askedIds) return q
        }
        // ছোট subTopic হলে সব চেষ্টাই আগে-করা প্রশ্নে পড়ে যেতে পারে — তখন repeat মানা যায়
        val offset = (0 until total).random()
        return repo.getRoomPagedQuestions(SHEET, subject, subTopic, "all", offset, 1).firstOrNull()
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    /** স্ক্রিন বন্ধ/back করার সময় কল হয় — চলমান কল/TTS বন্ধ, ভবিষ্যতের দেরিতে-আসা
     * কোনো রেসপন্সও (sessionActive=false থাকা পর্যন্ত) প্রয়োগ হবে না। */
    fun close() {
        sessionActive = false
        activeJob?.cancel()
        TtsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        close()
    }
}
