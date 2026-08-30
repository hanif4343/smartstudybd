package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.remote.TypingCloudSyncService
import com.hanif.smartstudy.ui.typing.PassageInfo
import com.hanif.smartstudy.ui.typing.TypingResult
import com.hanif.smartstudy.ui.typing.normalizeBn
import com.hanif.smartstudy.ui.typing.splitTypedWords
import com.hanif.smartstudy.util.Hand
import com.hanif.smartstudy.util.HandKeyMap
import com.hanif.smartstudy.util.PassageRepeatGuard
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TypingKeyStatStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  TypingSessionViewModel — পর্ব ৩/৫.৩ (মোড-সেপারেশন আর্কিটেকচার)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * ⚠️ স্কোপ-নোট (আপ-টু-ডেট — ধাপ ৩ পর্যন্ত):
 *
 *   ✅ আছে: প্যাসেজ/সেশন স্টেট, টাইমার, কীস্ট্রোক-কাউন্টিং (correct/incorrect/
 *      total + হাত-ভিত্তিক), ব্যাকস্পেস-লক, অটো-রিসিঙ্ক (স্পেস-মিস হ্যান্ডলিং),
 *      no-repeat প্যাসেজ-গার্ড (in-session + persisted-across-restart),
 *      হার্ড টাইম-কাটঅফ, WPM/Accuracy ক্যালকুলেশন, বেসিক persist (history/
 *      bestWpm/daily-seconds), **প্রতি-ক্যারেক্টার সঠিক/ভুল + latency ট্র্যাকিং
 *      (TypingKeyStatStore-এ persist), লাইভ রিদম-স্কোর।**
 *
 *   ❌ এখনো নেই (curriculum-স্তরের UI স্টেট, exam/govtmock-স্পেসিফিক লজিক,
 *      cloud sync, mistake-DB লগিং, TTS টিপস, সাউন্ড/ভাইব্রেশন, weak-word
 *      ড্যাশবোর্ড) — এগুলো `SmartTypingScreen.kt`/`TypingPracticeScreen.kt`-এ
 *      লোকাল স্টেট হিসেবে থেকে যাচ্ছে, কারণ এগুলো curriculum-নির্দিষ্ট ব্যবসায়িক
 *      লজিক (CurriculumProvider ইত্যাদি), কোর টাইপিং-ইঞ্জিনের অংশ না।
 */

data class TypingSessionUiState(
    val sessionMode: String = "free",
    val sessionLanguage: String = "bn",   // key-stat persist বাকেট (bn/en) — curriculum track-এর সাথে মেলে

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

    // ── পর্ব ৩/৫.৩ ধাপ ৩ (Smart Typing সম্প্রসারণ): লাইভ রিদম স্কোর (০-১০০),
    // সাম্প্রতিক কী-প্রেস ল্যাটেন্সির consistency থেকে — null মানে যথেষ্ট নমুনা এখনো হয়নি ──
    val rhythmScore: Int? = null,

    // ── পর্ব ৩/৫.৩ ধাপ ২: সর্বশেষ "লক" হওয়া শব্দের তথ্য — SmartTypingScreen এটা
    // পর্যবেক্ষণ করে TypingMistakeLogger-এ লগ করে (দুর্বল-শব্দ ড্যাশবোর্ডের জন্য)।
    // lastLockedWordIndex পাল্টালেই নতুন ইভেন্ট ধরা হয় (LaunchedEffect key হিসেবে) ──
    val lastLockedWordIndex: Int = -1,
    val lastLockedWordTarget: String = "",
    val lastLockedWordTyped: String = "",
    val lastLockedWordCorrect: Boolean = false,

    // ── স্পেস-মিস অটো-রিসিঙ্ক কতবার ট্রিগার হয়েছে — সেশন-শেষে TTS টিপের জন্য ──
    val syncLossCount: Int = 0,

    // ── ফলাফল ──
    val result: TypingResult? = null
)

class TypingSessionViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val passageGuard = PassageRepeatGuard()

    /** Normal (free) মোডে সময়-বাজেটের মধ্যে একের-পর-এক প্যাসেজ লুপ করার জন্য বর্তমান
     *  পুল মনে রাখা হয় — startSession()/advanceToNextPassage() সেট করে, onInputChange()-
     *  এর ভেতরের auto-advance লজিক এটাই ব্যবহার করে (UI থেকে বারবার পাস করতে হয় না)। */
    private var currentPool: List<PassageInfo> = emptyList()

    // ── পর্ব ৩/৫.৩ ধাপ ৩ (Smart Typing সম্প্রসারণ): প্রতি-ক্যারেক্টার সঠিক/ভুল ও
    // ল্যাটেন্সি — সেশন চলাকালীন RAM-এ জমে, finishSession()-এ একবারে TypingKeyStatStore-এ
    // flush হয় (ঠিক মূল TypingPracticeScreen.kt-এর প্যাটার্নেই — batch persist, বারবার
    // DB-write এড়াতে)। শুধু smartTypingEnabled সেশনেই এটা দরকার — Normal Typing-এ এই
    // বাফারগুলো সবসময় খালি থাকবে (কেউ populate করবে না), তাই flush করলেও কিছু হবে না ──
    private val keyCorrectWrongBuffer = mutableMapOf<Char, IntArray>()      // char -> [correctDelta, wrongDelta]
    private val keyLatencyBuffer = mutableMapOf<Char, TypingKeyStatStore.LatencyAgg>()
    private var lastCharAtMs: Long = 0L
    private val recentLatenciesMs = ArrayDeque<Long>(8)   // রিদম-স্কোরের জন্য সর্বশেষ ৮টা

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
    fun startSession(mode: String, pool: List<PassageInfo>, budgetSec: Int = 300, language: String = "bn") {
        stopTimer()
        currentPool = pool
        keyCorrectWrongBuffer.clear(); keyLatencyBuffer.clear(); recentLatenciesMs.clear(); lastCharAtMs = 0L
        val recentHashes = session.getRecentPassageHashes()
        val candidates = pool.indices.filter { pool[it].text.hashCode() !in recentHashes }
        val idx = candidates.randomOrNull() ?: pool.indices.randomOrNull() ?: 0
        val chosen = pool.getOrNull(idx)
        _state.value = TypingSessionUiState(
            sessionMode = mode,
            sessionLanguage = language,
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
                showBackspaceWarning = false, syncLossCount = 0
            )
        }
    }

    /** পরের প্যাসেজে যাও — শাফল-ব্যাগ-ভিত্তিক no-repeat গার্ড (পর্ব ৫.১) দিয়ে বাছাই।
     *  ⚠️ গুরুত্বপূর্ণ: cumulative কীস্ট্রোক-কাউন্টার/elapsedSec/isStarted **সংরক্ষিত
     *  থাকে** (রিসেট হয় না) — কারণ Normal মোডে সময়-বাজেটের মধ্যে একাধিক প্যাসেজ
     *  লুপ করা হয়, চূড়ান্ত WPM/Accuracy পুরো সেশনের (একাধিক প্যাসেজ মিলিয়ে) হওয়া
     *  উচিত, শুধু শেষ প্যাসেজেরটা না। শুধু প্যাসেজ-স্পেসিফিক ফিল্ড (userInput,
     *  frozenWordResults ইত্যাদি) রিসেট হয়। */
    fun advanceToNextPassage(pool: List<PassageInfo>) {
        if (pool.isEmpty()) return
        currentPool = pool
        val cur = _state.value
        val nextIdx = passageGuard.next(pool.size, cur.passageIndex)
        val chosen = pool[nextIdx]
        _state.update {
            it.copy(
                passageIndex = nextIdx, passage = chosen.text, passageDifficulty = chosen.difficulty,
                userInput = "", frozenWordResults = emptyList(), autoFixedWordFlags = emptyList(),
                showBackspaceWarning = false
                // ── ইচ্ছাকৃতভাবে বাদ: correctKeystrokes/incorrectKeystrokes/totalKeystrokes/
                // elapsedSec/isStarted/leftCorrectChars/... — এগুলো cumulative, প্যাসেজ
                // পাল্টালেও অক্ষত থাকা উচিত ──
            )
        }
        if (cur.sessionMode == "free" && chosen.text.isNotBlank()) {
            viewModelScope.launch { session.recordShownPassage(chosen.text) }
        }
    }

    fun setBackspaceLocked(locked: Boolean) {
        _state.update { it.copy(backspaceLocked = locked) }
    }

    /** স্ক্রিন খোলার সময় কল করার জন্য — cloud থেকে টেনে local-এর সাথে merge করে
     *  (মূল ফাইলের LaunchedEffect(Unit)-এর প্যাটার্নে, silent fail যদি লগইন করা না থাকে) */
    fun syncFromCloud() {
        viewModelScope.launch {
            val phone = session.getCurrentUser()?.phone.orEmpty()
            if (phone.isBlank()) return@launch
            val cloud = TypingCloudSyncService.pull(phone) ?: return@launch
            session.mergeTypingCloudSnapshot(cloud.bestWpm, cloud.history)
        }
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
                // ── হার্ড টাইম-কাটঅফ (পর্ব ৪.২/৫.৪-এর সমতুল্য) — মাঝ-প্যাসেজেও থামায়।
                // "free"-এর পাশাপাশি exam/govtmock-ও টাইমড মোড (দেখো পর্ব ৫.৮ ধাপ-১
                // — Exam স্প্লিট) — curriculum/keydrill ইচ্ছাকৃতভাবে বাদ, ওগুলোতে কোনো
                // হার্ড টাইম-বাজেট নেই (মূল অ্যাপেও ছিল না, শুধু প্যাসেজ শেষ হলে থামে) ──
                val s = _state.value
                val isTimedMode = s.sessionMode == "free" || s.sessionMode == "exam" || s.sessionMode == "govtmock" || s.sessionMode == "adaptive"
                if (isTimedMode && !s.isFinished && s.elapsedSec >= s.freeModeBudgetSec) {
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

        // ── পর্ব ৩/৫.৩ ধাপ ৩: রিদম-স্কোরের জন্য ল্যাটেন্সি ক্যাপচার — শুধু "পরিষ্কার"
        // এক-অক্ষর ফরওয়ার্ড কী-প্রেসেই মাপা হয় (paste/backspace/auto-resync-এ না,
        // কারণ তখন সময়ের হিসাব অর্থহীন হয়ে যায়) ──
        val nowMs = System.currentTimeMillis()
        if (new.length == s.userInput.length + 1 && lastCharAtMs > 0L) {
            val latency = nowMs - lastCharAtMs
            if (latency in 1..10_000) {   // অস্বাভাবিক (অ্যাপ ব্যাকগ্রাউন্ডে ছিল ইত্যাদি) মান বাদ
                recentLatenciesMs.addLast(latency)
                if (recentLatenciesMs.size > 8) recentLatenciesMs.removeFirst()
                val typedChar = new.lastOrNull()
                if (typedChar != null) {
                    val agg = keyLatencyBuffer.getOrPut(typedChar) { TypingKeyStatStore.LatencyAgg() }
                    agg.sumMs += latency; agg.sumSqMs += (latency.toDouble() * latency); agg.count++
                }
            }
        }
        lastCharAtMs = nowMs
        if (recentLatenciesMs.size >= 3) {
            val mean = recentLatenciesMs.average()
            val variance = recentLatenciesMs.map { (it - mean) * (it - mean) }.average()
            val cv = if (mean > 0) kotlin.math.sqrt(variance) / mean else 0.0
            val score = (100 - (cv * 100)).toInt().coerceIn(0, 100)
            _state.update { it.copy(rhythmScore = score) }
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
                    _state.update { it.copy(syncLossCount = it.syncLossCount + 1) }
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
            var lastLockedIdx = s.lastLockedWordIndex
            var lastLockedTarget = s.lastLockedWordTarget
            var lastLockedTyped = s.lastLockedWordTyped
            var lastLockedCorrect = s.lastLockedWordCorrect

            for (i in s.frozenWordResults.size until newSplit.completed.size) {
                val target    = passageWords.getOrNull(i) ?: break
                val typedWord = newSplit.completed[i]
                val wasAutoFixed = (i == autoFixedIndex)
                val isCorrect = !wasAutoFixed && typedWord == target
                lastLockedIdx = i; lastLockedTarget = target; lastLockedTyped = typedWord; lastLockedCorrect = isCorrect

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
                        // ── পর্ব ৩/৫.৩ ধাপ ৩: প্রতি-ক্যারেক্টার সঠিক-ডেল্টা — curriculum
                        // unlock, KeyHeatmapCard, দুর্বল-কী ড্রিল সবকিছুরই ডেটা-সোর্স ──
                        keyCorrectWrongBuffer.getOrPut(tc) { intArrayOf(0, 0) }[0]++
                    } else {
                        incorrectKs++
                        if (tc != null && HandKeyMap.isTrackable(tc)) {
                            when (HandKeyMap.handOf(tc)) { Hand.LEFT -> leftW++; Hand.RIGHT -> rightW++ }
                        }
                        if (tc != null) keyCorrectWrongBuffer.getOrPut(tc) { intArrayOf(0, 0) }[1]++
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
                    rightCorrectChars = rightC, rightWrongChars = rightW,
                    lastLockedWordIndex = lastLockedIdx, lastLockedWordTarget = lastLockedTarget,
                    lastLockedWordTyped = lastLockedTyped, lastLockedWordCorrect = lastLockedCorrect
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

        // ── প্যাসেজ সম্পূর্ণ হলে (allDone): Normal (free) মোডে সময়-বাজেট এখনো বাকি
        // থাকলে পরের প্যাসেজে লুপ করে (cumulative স্ট্যাট সংরক্ষিত থাকে, দেখো
        // advanceToNextPassage()) — সময় শেষ বা free-না-হলে তবেই সেশন সত্যিকারভাবে
        // finishSession() দিয়ে শেষ হয় ──
        val finalSplit = splitTypedWords(normalized)
        val allDone = finalSplit.completed.size >= passageWords.size ||
            (finalSplit.completed.size == passageWords.size - 1 &&
                finalSplit.current == passageWords.lastOrNull())
        if (allDone && !_state.value.isFinished) {
            val latest = _state.value
            // ── free/exam/govtmock — তিনটাই সময়-বাজেটের মধ্যে একাধিক প্যাসেজে লুপ করে
            // (মূল TypingPracticeScreen.kt-এর আচরণের সাথে হুবহু মিলিয়ে) — curriculum/
            // keydrill ইত্যাদিতে লুপ হয় না, একটা প্যাসেজ শেষ = সেশন শেষ ──
            val loopsWithinBudget = latest.sessionMode == "free" || latest.sessionMode == "exam" || latest.sessionMode == "govtmock"
            if (loopsWithinBudget && latest.elapsedSec < latest.freeModeBudgetSec && currentPool.isNotEmpty()) {
                advanceToNextPassage(currentPool)
            } else {
                finishSession()
            }
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
            rightCorrect = s.rightCorrectChars, rightWrong = s.rightWrongChars,
            syncLossCount = s.syncLossCount
        )

        _state.update { it.copy(isFinished = true, result = result) }

        // ── পর্ব ৩/৫.৩ ধাপ ৩: Smart Typing-এর কী-স্ট্যাট বাফার (correct/wrong + latency)
        // এখানে batch-flush হয় — Normal Typing-এ বাফার খালি থাকে বলে addDeltas()/
        // addLatencyDeltas() no-op হয়ে যায় (কিছুই করার নেই), তাই এটা সব মোডেই নিরাপদ ──
        val lang = s.sessionLanguage
        val correctWrongSnapshot = keyCorrectWrongBuffer.toMap()
        val latencySnapshot = keyLatencyBuffer.toMap()
        keyCorrectWrongBuffer.clear(); keyLatencyBuffer.clear()
        // ── ⚠️ Exam Simulation-এর ফলাফল সাধারণ "প্র্যাকটিস বেস্ট WPM"/হিস্ট্রিতে যোগ
        // হয় না — মূল TypingPracticeScreen.kt-এর finishExamPhase()-ও এটাই করত (আলাদা
        // ExamResultCard-এ দেখানো হয়, bestWpm/history দূষিত হয় না) ──
        val shouldRecordAsNormalResult = s.sessionMode != "exam"
        viewModelScope.launch {
            if (shouldRecordAsNormalResult) {
                session.recordTypingResult(result.wpm, result.rawWpm, result.accuracy, result.timeSec)
            }
            session.addTypingSecondsToday(timeSec)
            if (correctWrongSnapshot.isNotEmpty()) {
                TypingKeyStatStore.addDeltas(getApplication(), lang, correctWrongSnapshot)
            }
            if (latencySnapshot.isNotEmpty()) {
                TypingKeyStatStore.addLatencyDeltas(getApplication(), lang, latencySnapshot)
            }
            // ── পর্ব ৩/৫.৩ ধাপ ২: cloud sync — মূল ফাইলের প্যাটার্নেই, ব্যর্থ হলেও
            // silent fail (local persist প্রভাবিত হয় না), Google/ফোন-লগইন করা না
            // থাকলে phone খালি থাকবে আর push()-ই কিছু করবে না ──
            val phone = session.getCurrentUser()?.phone.orEmpty()
            if (phone.isNotBlank()) {
                TypingCloudSyncService.push(phone, session.getTypingBestWpm(), session.getRawTypingHistory())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}
