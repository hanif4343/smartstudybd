package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.ui.typing.PassageInfo
import com.hanif.smartstudy.ui.typing.TypingResult
import com.hanif.smartstudy.ui.typing.normalizeBn
import com.hanif.smartstudy.ui.typing.splitTypedWords
import com.hanif.smartstudy.util.Hand
import com.hanif.smartstudy.util.HandKeyMap
import com.hanif.smartstudy.util.PassageRepeatGuard
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  TypingSessionViewModel — পর্ব ৩/৫.৩ (মোড-সেপারেশন আর্কিটেকচার) — ধাপ ১
 * ═══════════════════════════════════════════════════════════════════════
 *
 * ⚠️ গুরুত্বপূর্ণ স্কোপ-নোট (প্রতারণা এড়াতে স্পষ্ট করে লেখা হলো):
 * এটা `TypingPracticeScreen.kt`-এর ~৪০০০ লাইনের **সম্পূর্ণ প্রতিস্থাপন না** —
 * সেটা একবারে (কম্পাইলার ছাড়া) করা অত্যন্ত ঝুঁকিপূর্ণ হতো। এখানে শুধু
 * **কোর ইঞ্জিন** অংশটুকু আনা হয়েছে, যেটা Normal ও Smart Typing দুই মোডেই
 * অভিন্নভাবে দরকার হবে:
 *
 *   ✅ আছে: প্যাসেজ/সেশন স্টেট, টাইমার, কীস্ট্রোক-কাউন্টিং (correct/incorrect/
 *      total + হাত-ভিত্তিক), ব্যাকস্পেস-লক, অটো-রিসিঙ্ক (স্পেস-মিস হ্যান্ডলিং),
 *      no-repeat প্যাসেজ-গার্ড (in-session + persisted-across-restart),
 *      হার্ড টাইম-কাটঅফ, WPM/Accuracy ক্যালকুলেশন, বেসিক persist (history/
 *      bestWpm/daily-seconds)।
 *
 *   ❌ এখনো নেই (পরের ফেজে যোগ হবে, আপাতত TypingPracticeScreen.kt-এই থেকে
 *      যাচ্ছে): curriculum/adaptive/exam/govtmock-স্পেসিফিক লজিক, cloud sync,
 *      key-latency/rhythm অ্যানালিটিক্স, mistake-DB লগিং, TTS টিপস, সাউন্ড/
 *      ভাইব্রেশন, hand-stats DB persist, weak-word ড্যাশবোর্ড।
 *
 * `TypingPracticeScreen.kt`-এর বিদ্যমান কোড **এখনো এই ViewModel ব্যবহার করছে
 * না** — সেটা একটা পরবর্তী, ইচ্ছাকৃতভাবে আলাদা ধাপ, যাতে এই ফাইলটা প্রথমে
 * এককভাবে কম্পাইল/রিভিউ করা যায়, তারপর ধাপে ধাপে existing state-কে এখানে
 * migrate করে reference করানো হবে।
 */

data class TypingSessionUiState(
    val sessionMode: String = "free",

    // ── প্যাসেজ/সেশন ──
    val passageIndex: Int = 0,
    val passage: String = "",
    val passageDifficulty: String = "",
    val userInput: String = "",
    val frozenWordResults: List<Boolean> = emptyList(),
    val autoFixedWordFlags: List<Boolean> = emptyList(),

    // ── টাইমার ──
    val isStarted: Boolean = false,
    val isFinished: Boolean = false,
    val elapsedSec: Int = 0,
    val freeModeBudgetSec: Int = 300,

    // ── কীস্ট্রোক কাউন্টার ──
    val correctKeystrokes: Int = 0,
    val incorrectKeystrokes: Int = 0,
    val totalKeystrokes: Int = 0,
    val leftCorrectChars: Int = 0,
    val leftWrongChars: Int = 0,
    val rightCorrectChars: Int = 0,
    val rightWrongChars: Int = 0,

    // ── ব্যাকস্পেস-লক ──
    val backspaceLocked: Boolean = false,
    val showBackspaceWarning: Boolean = false,

    // ── ফলাফল ──
    val result: TypingResult? = null
)

class TypingSessionViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val passageGuard = PassageRepeatGuard()

    private val _state = MutableStateFlow(TypingSessionUiState())
    val state: StateFlow<TypingSessionUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    /** বর্তমানে যে প্যাসেজের ওপর ভিত্তি করে userInput যাচাই হচ্ছে — UI থেকেই
     *  পাস করা হয় (word-split লজিক পুরোনো ফাইলেরটাই পুনর্ব্যবহার করা হচ্ছে,
     *  তাই passageWords আলাদা রাখা প্রয়োজন)। */
    private fun passageWords(): List<String> = _state.value.passage.split(' ')

    // ─────────────────────────────────────────────────────────────────
    //  সেশন শুরু/রিসেট
    // ─────────────────────────────────────────────────────────────────

    /** নতুন সেশন শুরু করে — সাধারণত মোড-সিলেক্ট বা "Practice"/"Quick 3" বাটনে।
     *  no-repeat গার্ড (persisted, দেখো পর্ব ৫.১-এর ক্রিটিক্যাল ফলো-আপ ফিক্স)
     *  ব্যবহার করে সাম্প্রতিক-দেখানো প্যাসেজ এড়িয়ে বাছাই করে। */
    fun startSession(mode: String, pool: List<PassageInfo>, budgetSec: Int = 300) {
        stopTimer()
        val recentHashes = session.getRecentPassageHashes()
        val candidates = pool.indices.filter { pool[it].text.hashCode() !in recentHashes }
        val idx = candidates.randomOrNull() ?: pool.indices.randomOrNull() ?: 0
        val chosen = pool.getOrNull(idx)
        _state.value = TypingSessionUiState(
            sessionMode = mode,
            passageIndex = idx,
            passage = chosen?.text ?: "",
            passageDifficulty = chosen?.difficulty ?: "",
            freeModeBudgetSec = budgetSec
        )
        chosen?.text?.let { txt ->
            if (mode == "free" && txt.isNotBlank()) {
                viewModelScope.launch { session.recordShownPassage(txt) }
            }
        }
    }

    /** বর্তমান প্যাসেজ অপরিবর্তিত রেখে টাইপিং-স্টেট রিসেট (Ctrl+R / "আবার" বাটন)। */
    fun restartCurrentPassage() {
        stopTimer()
        _state.update {
            it.copy(
                userInput = "", frozenWordResults = emptyList(), autoFixedWordFlags = emptyList(),
                isStarted = false, isFinished = false, elapsedSec = 0, result = null,
                correctKeystrokes = 0, incorrectKeystrokes = 0, totalKeystrokes = 0,
                leftCorrectChars = 0, leftWrongChars = 0, rightCorrectChars = 0, rightWrongChars = 0,
                showBackspaceWarning = false
            )
        }
    }

    /** পরের প্যাসেজে যাও — শাফল-ব্যাগ-ভিত্তিক no-repeat গার্ড (পর্ব ৫.১) দিয়ে বাছাই। */
    fun advanceToNextPassage(pool: List<PassageInfo>) {
        stopTimer()
        if (pool.isEmpty()) return
        val curIdx = _state.value.passageIndex
        val nextIdx = passageGuard.next(pool.size, curIdx)
        val chosen = pool[nextIdx]
        _state.value = TypingSessionUiState(
            sessionMode = _state.value.sessionMode,
            passageIndex = nextIdx,
            passage = chosen.text,
            passageDifficulty = chosen.difficulty,
            freeModeBudgetSec = _state.value.freeModeBudgetSec
        )
        if (_state.value.sessionMode == "free" && chosen.text.isNotBlank()) {
            viewModelScope.launch { session.recordShownPassage(chosen.text) }
        }
    }

    fun setBackspaceLocked(locked: Boolean) {
        _state.update { it.copy(backspaceLocked = locked) }
    }

    // ─────────────────────────────────────────────────────────────────
    //  টাইমার
    // ─────────────────────────────────────────────────────────────────

    private fun ensureTimerRunning() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
                // ── হার্ড টাইম-কাটঅফ (পর্ব ৪.২/৫.৪-এর সমতুল্য) — মাঝ-প্যাসেজেও থামায় ──
                val s = _state.value
                if (s.sessionMode == "free" && !s.isFinished && s.elapsedSec >= s.freeModeBudgetSec) {
                    finishSession()
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // ─────────────────────────────────────────────────────────────────
    //  ইনপুট হ্যান্ডলিং — বিদ্যমান TypingPracticeScreen.onInputChange()-এর
    //  কোর অংশ (backspace-lock + auto-resync + char-by-char কাউন্টিং) থেকে
    //  বিশ্বস্তভাবে পোর্ট করা হয়েছে; smart-typing-specific অংশ (latency/
    //  rhythm/mistake-DB/সাউন্ড) বাদ দেওয়া হয়েছে — সেগুলো UI লেয়ারে থেকে যাবে।
    // ─────────────────────────────────────────────────────────────────

    fun onInputChange(new: String) {
        val s = _state.value
        if (s.isFinished) return

        val backspaceIsBlocked = s.sessionMode == "govtmock" || s.backspaceLocked
        if (backspaceIsBlocked && new.length < s.userInput.length) {
            _state.update { it.copy(showBackspaceWarning = true) }
            return
        }
        if (s.showBackspaceWarning) _state.update { it.copy(showBackspaceWarning = false) }

        if (!s.isStarted && new.isNotEmpty()) {
            _state.update { it.copy(isStarted = true) }
            ensureTimerRunning()
        }

        val passageWords = passageWords()
        var normalized = normalizeBn(new)

        // ── স্মার্ট অটো-রিসিঙ্ক (স্পেস মিস হ্যান্ডলিং) — বিস্তারিত ব্যাখ্যা মূল
        // TypingPracticeScreen.kt-এর onInputChange()-এ দেখো, একই লজিক এখানে ──
        var autoFixedIndex = -1
        run {
            val liveSplit = splitTypedWords(normalized)
            val wIdx = liveSplit.completed.size
            val target = passageWords.getOrNull(wIdx)
            val cur = liveSplit.current
            if (target != null && cur.length > target.length) {
                val overflow = cur.substring(target.length)
                val nextWord = passageWords.getOrNull(wIdx + 1)
                if (overflow.length >= 2 && nextWord != null && nextWord.startsWith(overflow)) {
                    normalized = normalized.dropLast(cur.length) + cur.substring(0, target.length) + " " + overflow
                    autoFixedIndex = wIdx
                }
            }
        }

        val newSplit = splitTypedWords(normalized)

        if (newSplit.completed.size > s.frozenWordResults.size) {
            var results = s.frozenWordResults
            var fixedFlags = s.autoFixedWordFlags
            var totalKs = s.totalKeystrokes
            var correctKs = s.correctKeystrokes
            var incorrectKs = s.incorrectKeystrokes
            var leftC = s.leftCorrectChars; var leftW = s.leftWrongChars
            var rightC = s.rightCorrectChars; var rightW = s.rightWrongChars

            for (i in s.frozenWordResults.size until newSplit.completed.size) {
                val target    = passageWords.getOrNull(i) ?: break
                val typedWord = newSplit.completed[i]
                val wasAutoFixed = (i == autoFixedIndex)
                val isCorrect = !wasAutoFixed && typedWord == target

                val len = maxOf(target.length, typedWord.length)
                for (j in 0 until len) {
                    totalKs++
                    val tc = target.getOrNull(j)
                    val yc = typedWord.getOrNull(j)
                    if (tc != null && tc == yc) {
                        correctKs++
                        if (HandKeyMap.isTrackable(tc)) {
                            when (HandKeyMap.handOf(tc)) { Hand.LEFT -> leftC++; Hand.RIGHT -> rightC++ }
                        }
                    } else {
                        incorrectKs++
                        if (tc != null && HandKeyMap.isTrackable(tc)) {
                            when (HandKeyMap.handOf(tc)) { Hand.LEFT -> leftW++; Hand.RIGHT -> rightW++ }
                        }
                    }
                }
                if (i < passageWords.size - 1) {
                    totalKs++
                    if (isCorrect) correctKs++ else incorrectKs++
                }

                results = results + isCorrect
                fixedFlags = fixedFlags + wasAutoFixed
            }

            _state.update {
                it.copy(
                    frozenWordResults = results, autoFixedWordFlags = fixedFlags,
                    totalKeystrokes = totalKs, correctKeystrokes = correctKs, incorrectKeystrokes = incorrectKs,
                    leftCorrectChars = leftC, leftWrongChars = leftW,
                    rightCorrectChars = rightC, rightWrongChars = rightW
                )
            }
        } else {
            // ── লক-হওয়া শব্দ-সংখ্যা না বাড়লেও frozenWordResults-এর বেশি হয়ে গেলে
            // (ব্যাকস্পেস দিয়ে আংশিক শব্দ মুছে ফেলা) ছাঁটাই করা — মূল ফাইলের মতোই ──
            if (newSplit.completed.size < s.frozenWordResults.size) {
                _state.update {
                    it.copy(
                        frozenWordResults = it.frozenWordResults.take(newSplit.completed.size),
                        autoFixedWordFlags = it.autoFixedWordFlags.take(newSplit.completed.size)
                    )
                }
            }
        }

        _state.update { it.copy(userInput = normalized) }

        // ── প্যাসেজ সম্পূর্ণ হলে (allDone) স্বয়ংক্রিয়ভাবে finishSession() —
        // Normal মোডে (multi-passage loop UI-স্তরে হ্যান্ডল হয়, এখানে শুধু
        // single-passage completion detect করা হচ্ছে) ──
        val finalSplit = splitTypedWords(normalized)
        val allDone = finalSplit.completed.size >= passageWords.size ||
            (finalSplit.completed.size == passageWords.size - 1 &&
                finalSplit.current == passageWords.lastOrNull())
        if (allDone && !_state.value.isFinished) {
            finishSession()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  সেশন সমাপ্তি
    // ─────────────────────────────────────────────────────────────────

    /** সময়-সীমা শেষে (মাঝ-প্যাসেজেও) জোর করে থামানো হলে ঠিক finishSession()-এর
     *  আগে কল হয় — চলমান (আন-লকড) শব্দকে ফাইনাল কাউন্টে যোগ করে, আর একদমই
     *  টাইপ-না-করা বাকি অংশকে সম্পূর্ণ "ভুল" হিসেবে যোগ করে দেয় (পর্ব ৪.২/৫.৪)। */
    private fun finalizeTimedCutoff() {
        val s = _state.value
        val passageWords = passageWords()
        val split = splitTypedWords(s.userInput)
        val wIdx = split.completed.size
        val typedWord = split.current
        val targetWord = passageWords.getOrNull(wIdx)

        var totalKs = s.totalKeystrokes
        var correctKs = s.correctKeystrokes
        var incorrectKs = s.incorrectKeystrokes

        if (targetWord != null && typedWord.isNotEmpty()) {
            val len = maxOf(targetWord.length, typedWord.length)
            for (i in 0 until len) {
                val t = targetWord.getOrNull(i)
                val u = typedWord.getOrNull(i)
                totalKs++
                if (t != null && u == t) correctKs++ else incorrectKs++
            }
        }

        val untouchedWords = passageWords.drop(wIdx + 1)
        if (untouchedWords.isNotEmpty()) {
            val missingChars = untouchedWords.sumOf { it.length } + untouchedWords.size
            totalKs += missingChars
            incorrectKs += missingChars
        }

        _state.update { it.copy(totalKeystrokes = totalKs, correctKeystrokes = correctKs, incorrectKeystrokes = incorrectKs) }
    }

    /** সেশন শেষ করে — "Submit Now"/হার্ড-কাটঅফ/প্যাসেজ-সম্পূর্ণ, সব পথ থেকেই এটাই
     *  কল হয়। কোর WPM/Accuracy ক্যালকুলেশন + বেসিক persist (history/bestWpm/
     *  daily-seconds) — curriculum/cloud-sync/mistake-DB ইত্যাদি এখনো UI লেয়ারে। */
    fun finishSession() {
        val cur = _state.value
        if (cur.isFinished) return

        // মাঝ-প্যাসেজে টাইম-কাটঅফ হলে (পুরো প্যাসেজ শেষ হয়নি) finalizeTimedCutoff চালানো
        val passageWords = passageWords()
        val split = splitTypedWords(cur.userInput)
        val fullyDone = split.completed.size >= passageWords.size
        if (!fullyDone) finalizeTimedCutoff()

        stopTimer()

        val s = _state.value
        val timeSec = s.elapsedSec.coerceAtLeast(1)
        val minutes = timeSec / 60.0
        val rawWpm = if (minutes > 0) (s.totalKeystrokes / 5.0 / minutes).toInt() else 0
        val netWpm = if (minutes > 0) (s.correctKeystrokes / 5.0 / minutes).toInt().coerceAtLeast(0) else 0
        val acc = if (s.totalKeystrokes > 0) (s.correctKeystrokes * 100 / s.totalKeystrokes) else 100

        val result = TypingResult(
            wpm = netWpm, rawWpm = rawWpm, accuracy = acc, timeSec = timeSec,
            correctChars = s.correctKeystrokes, totalChars = s.totalKeystrokes,
            leftCorrect = s.leftCorrectChars, leftWrong = s.leftWrongChars,
            rightCorrect = s.rightCorrectChars, rightWrong = s.rightWrongChars
        )

        _state.update { it.copy(isFinished = true, result = result) }

        viewModelScope.launch {
            session.recordTypingResult(result.wpm, result.rawWpm, result.accuracy, result.timeSec)
            session.addTypingSecondsToday(timeSec)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}
