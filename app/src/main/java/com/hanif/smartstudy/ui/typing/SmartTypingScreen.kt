package com.hanif.smartstudy.ui.typing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.model.BijoyCurriculum
import com.hanif.smartstudy.data.repository.TypingLeaderboardRepository
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.CurriculumProvider
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TypingAdaptiveContentProvider
import com.hanif.smartstudy.util.TypingErrorAnalyzer
import com.hanif.smartstudy.util.TypingKeyStatStore
import com.hanif.smartstudy.util.TypingMistakeLogger
import com.hanif.smartstudy.util.TypingPassageProvider
import com.hanif.smartstudy.util.TtsManager
import com.hanif.smartstudy.viewmodel.TypingSessionViewModel
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  SmartTypingScreen — পর্ব ৩/৫.৩ (মোড-সেপারেশন) — ধাপ ৩ (দ্বিতীয় নতুন স্ক্রিন)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * `NormalTypingScreen.kt`-এর মতোই — সম্পূর্ণ নতুন, স্বতন্ত্র ফাইল, পুরনো
 * `TypingPracticeScreen.kt`-এ কোনো লজিক পরিবর্তন নেই (Exam/govtmock এখনো
 * ওখানেই)। `TypingSessionViewModel` কোর ইঞ্জিন (এখন প্রতি-ক্যারেক্টার
 * সঠিক/ভুল + latency + রিদম-স্কোরও ট্র্যাক করে, দেখো ViewModel-এর ধাপ ৩)
 * ব্যবহার করে, আর curriculum-স্তরের স্টেট (ট্র্যাক/স্টেজ/প্রগ্রেস) এই স্ক্রিনেই
 * লোকাল — কারণ এটা CurriculumProvider-নির্ভর ব্যবসায়িক লজিক, কোর ইঞ্জিনের
 * অংশ না।
 *
 * ⚠️ স্কোপ: শুধু Adaptive Key-Unlock (curriculum) + দুর্বল-কী/জুটি ড্রিল।
 * AI Adaptive Session, Exam Simulation, Govt Job Mock Test, cloud sync,
 * mistake-DB, TTS — এখনো পুরনো `TypingPracticeScreen.kt`-এই আছে (আলাদা,
 * পরবর্তী ধাপ)।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTypingScreen(
    onBack: () -> Unit,
    onResult: (TypingResult) -> Unit = {},
    vm: TypingSessionViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }
    val leaderboardRepo = remember { TypingLeaderboardRepository() }
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val isAdmin = remember { session.getCurrentUser()?.isAdmin() == true }
    var showStageAdmin by remember { mutableStateOf(false) }

    var curriculumTrack by remember { mutableStateOf("bn") }
    var curriculumStage by remember { mutableStateOf(1) }
    var curriculumProgress by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var keyStatSnapshot by remember { mutableStateOf(mapOf<String, Pair<Int, Int>>()) }
    // ── XP/লেভেল-আপ সেলিব্রেশন — সেশন শেষে লেভেল বাড়লে এখানে সেট হয়, একটা ছোট
    // Dialog দেখানো হয় (দেখো নিচের finish-session LaunchedEffect) ──
    var levelUpTo by remember { mutableStateOf<Int?>(null) }
    var blindMode by remember { mutableStateOf(false) }
    var showKeyAnalysis by remember { mutableStateOf(false) }
    var keyAnalysisList by remember { mutableStateOf(listOf<TypingKeyStatStore.KeyAnalysis>()) }
    // ── পর্ব ৩/৫.৩ ধাপ ২: দুর্বল-শব্দ ড্যাশবোর্ড + এই-সেশনের ভুল-শব্দ ট্র্যাকিং ──
    var weakWordDashboard by remember { mutableStateOf(listOf<String>()) }
    var sessionMistakeWords by remember { mutableStateOf(listOf<String>()) }
    // ── পর্ব ৩/৫.৩ ধাপ ২ (বাকি অংশ): AI Adaptive Session — দুই-ফেজ, ফেজ ১ (৩ মিনিট,
    // pool-প্যাসেজ) চলাকালীন ব্যাকগ্রাউন্ডে ফেজ-২-এর AI-blended (দুর্বল-শব্দ-ভিত্তিক)
    // প্যাসেজ ফেচ হয় ──
    var allPassages by remember { mutableStateOf(listOf<PassageInfo>()) }
    var adaptivePhase by remember { mutableStateOf(1) }
    var phase2Passage by remember { mutableStateOf<String?>(null) }
    var phase2Source by remember { mutableStateOf<String?>(null) }
    var phase2Fetching by remember { mutableStateOf(false) }
    var showAdaptiveTransition by remember { mutableStateOf(false) }
    var adaptivePhase1Result by remember { mutableStateOf<TypingResult?>(null) }

    val allUnlockedKeys = remember(curriculumTrack, curriculumStage) {
        BijoyCurriculum.stagesFor(curriculumTrack).take(curriculumStage).flatten()
    }

    suspend fun refreshCurriculum(track: String) {
        curriculumStage = CurriculumProvider.getCurrentStage(ctx, track)
        curriculumProgress = CurriculumProvider.stageProgress(ctx, track, curriculumStage)
        keyStatSnapshot = loadKeyStatSnapshot(ctx, track)
    }

    fun startCurriculumSession(track: String) {
        curriculumTrack = track
        scope.launch {
            refreshCurriculum(track)
            val drillText = CurriculumProvider.buildDrillPassageSmart(ctx, track, curriculumStage)
            if (drillText.isNotBlank()) {
                vm.startSession("curriculum", listOf(PassageInfo(drillText, "all")), budgetSec = 300, language = track)
            }
        }
    }

    fun startKeyDrillSession() {
        scope.launch {
            val weakChars = TypingKeyStatStore.getWeakest(ctx, curriculumTrack, minSamples = 10, limit = 6)
                .mapNotNull { it.keyChar.firstOrNull() }
            val fillerChars = allUnlockedKeys.ifEmpty { listOf("ক", "া", "র") }
            val words = (1..12).map {
                val useWeak = weakChars.isNotEmpty() && (0..1).random() == 0
                val core = if (useWeak) weakChars.random().toString() else fillerChars.random()
                val len = (2..4).random()
                (1..len).map { if ((0..1).random() == 0 && useWeak) core else fillerChars.random() }.joinToString("")
            }
            vm.startSession("keydrill", listOf(PassageInfo(words.joinToString(" "), "all")), budgetSec = 300, language = curriculumTrack)
        }
    }

    // ── 🤖 ভুল-শব্দ দিয়ে AI পরের প্যাসেজ — এই সেশনে যে শব্দগুলোতে ভুল হয়েছিল
    // (sessionMistakeWords), সেগুলো AI-কে দিয়ে একটা নতুন প্র্যাকটিস-প্যাসেজ বানানো
    // হয় (TypingAdaptiveContentProvider — আগে শুধু "AI Adaptive Session" মোডে
    // ব্যবহার হতো, এখন যেকোনো মোডের ResultCard থেকেও, opt-in বাটনে)। "keydrill"
    // ট্যাগে চালু করা হয় (curriculum না) — কারণ curriculum মোডের কী-আনলক লজিক
    // ধরে নেয় প্যাসেজে শুধু এখন-পর্যন্ত-আনলক-করা কী থাকবে, AI-টেক্সটে অন্য কী
    // চলে আসতে পারে, যেটা curriculum-এর ধাপ-ভিত্তিক ডিজাইন ভেঙে ফেলবে ──
    fun startAiMistakeDrillSession() {
        val mistakes = sessionMistakeWords.distinct()
        sessionMistakeWords = emptyList()
        if (mistakes.isEmpty()) { startCurriculumSession(curriculumTrack); return }
        scope.launch {
            val res = TypingAdaptiveContentProvider.getBlendedPassage(ctx, mistakes, curriculumTrack, "medium", null)
            vm.startSession("keydrill", listOf(PassageInfo(res.passage, "ai")), budgetSec = 300, language = curriculumTrack)
        }
    }

    fun startBigramDrillSession() {
        scope.launch {
            val slowPairs = TypingKeyStatStore.getSlowestPairsGlobal(ctx, curriculumTrack, minCount = 3, limit = 6)
            if (slowPairs.isEmpty()) {
                startKeyDrillSession()
                return@launch
            }
            val bigramStrings = slowPairs.map { it.first + it.second }
            val fillerChars = allUnlockedKeys.ifEmpty { bigramStrings.flatMap { it.map(Char::toString) }.distinct() }
            val words = (1..12).map {
                val includeBigram = (0..2).random() > 0
                val core = if (includeBigram) bigramStrings.random() else ""
                val prefix = if ((0..1).random() == 0) fillerChars.randomOrNull() ?: "" else ""
                (prefix + core).ifBlank { fillerChars.randomOrNull() ?: "ক" }
            }
            vm.startSession("keydrill", listOf(PassageInfo(words.joinToString(" "), "all")), budgetSec = 300, language = curriculumTrack)
        }
    }

    /** "🎯 AI Adaptive Session" বাটনে ট্যাপ করলে কল হয় — ফেজ ১ (৩ মিনিট, সাধারণ পুল-
     *  প্যাসেজ) দিয়ে শুরু, ব্যাকগ্রাউন্ডে ফেজ-২-এর AI-blended প্যাসেজ তৈরি হতে থাকে। */
    fun startAdaptiveSession(language: String) {
        adaptivePhase = 1
        phase2Passage = null; phase2Source = null; phase2Fetching = false
        showAdaptiveTransition = false; adaptivePhase1Result = null
        sessionMistakeWords = emptyList()
        val pool = allPassages.filter { TypingErrorAnalyzer.detectLanguage(it.text) == language }.ifEmpty { allPassages }
        vm.startSession("adaptive", pool, budgetSec = ADAPTIVE_PHASE1_SECONDS, language = language)
    }

    fun startAdaptivePhase2() {
        adaptivePhase = 2; showAdaptiveTransition = false
        val text = phase2Passage ?: return
        vm.startSession("adaptive", listOf(PassageInfo(text, "ai")), budgetSec = ADAPTIVE_PHASE1_SECONDS, language = curriculumTrack)
    }

    // ── ফেজ ১ চলাকালীন — নির্দিষ্ট সময়ে (৩ মিনিটের ১ মিনিট আগে) ব্যাকগ্রাউন্ডে
    // ফেজ-২-এর AI-blended প্যাসেজ ফেচ শুরু হয় (এই সেশনের ভুল-শব্দ, না থাকলে DB-র
    // পুরনো দুর্বল-শব্দ ব্যবহার করে) ──
    LaunchedEffect(state.elapsedSec, state.sessionMode, adaptivePhase, state.isStarted, state.isFinished) {
        if (state.sessionMode != "adaptive" || adaptivePhase != 1 || !state.isStarted || state.isFinished) return@LaunchedEffect
        if (state.elapsedSec < ADAPTIVE_PHASE2_FETCH_TRIGGER_SECONDS || phase2Passage != null || phase2Fetching) return@LaunchedEffect
        phase2Fetching = true
        val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
        val weak = sessionMistakeWords.distinct().take(10).ifEmpty {
            AppDatabase.getInstance(ctx).typingMistakeDao().getTopWeakWords(userId, curriculumTrack, limit = 10).map { it.targetWord }
        }
        val res = TypingAdaptiveContentProvider.getBlendedPassage(ctx, weak, curriculumTrack, "medium", null)
        phase2Passage = res.passage
        phase2Source = when (res.source) {
            TypingAdaptiveContentProvider.Source.Cache -> "cache"
            TypingAdaptiveContentProvider.Source.LiveAi -> "live_ai"
            TypingAdaptiveContentProvider.Source.Fallback -> "fallback"
        }
        phase2Fetching = false
    }

    // ── ফেজ ১ শেষ হলে (সময়/প্যাসেজ-সম্পূর্ণ, দুটোতেই) — ফলাফল জমা রেখে ট্রানজিশন
    // কার্ড দেখানো (ফেজ-২ প্যাসেজ রেডি থাকলে "শুরু করো" বাটন সক্রিয় থাকবে) ──
    LaunchedEffect(state.isFinished, state.result) {
        val r = state.result ?: return@LaunchedEffect
        if (!state.isFinished || state.sessionMode != "adaptive") return@LaunchedEffect
        if (adaptivePhase == 1 && adaptivePhase1Result == null) {
            adaptivePhase1Result = r
            showAdaptiveTransition = true
        }
    }

    LaunchedEffect(Unit) {
        vm.syncFromCloud()
        val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
        weakWordDashboard = AppDatabase.getInstance(ctx).typingMistakeDao()
            .getTopWeakWords(userId, "bn", limit = 10).map { it.targetWord } +
            AppDatabase.getInstance(ctx).typingMistakeDao()
                .getTopWeakWords(userId, "en", limit = 5).map { it.targetWord }
        startCurriculumSession("bn")
        allPassages = TypingPassageProvider.getPassages(ctx)
    }

    // ── পর্ব ৩/৫.৩ ধাপ ২: প্রতিটা "লক" হওয়া শব্দ TypingMistakeLogger-এ লগ হয় (spaced-
    // repetition দুর্বল-শব্দ ট্র্যাকিং), আর এই সেশনের ভুল শব্দগুলো লোকাল লিস্টে জমা হয় ──
    LaunchedEffect(state.lastLockedWordIndex) {
        if (state.lastLockedWordIndex < 0) return@LaunchedEffect
        val target = state.lastLockedWordTarget
        val typed = state.lastLockedWordTyped
        if (target.isBlank()) return@LaunchedEffect
        val lang = TypingErrorAnalyzer.detectLanguage(target)
        if (state.lastLockedWordCorrect) {
            TypingMistakeLogger.logCorrect(ctx, target, lang)
        } else {
            TypingMistakeLogger.logMistake(ctx, target, typed, lang)
            sessionMistakeWords = (sessionMistakeWords + target).distinct().takeLast(10)
        }
    }

    // ── সেশন শেষ হলে: curriculum unlock-চেক + key-stat স্ন্যাপশট রিফ্রেশ + onResult কল ──
    LaunchedEffect(state.isFinished, state.result) {
        val r = state.result ?: return@LaunchedEffect
        if (!state.isFinished) return@LaunchedEffect
        onResult(r)
        if (state.sessionMode == "curriculum") {
            val targetWpm = 20
            val advanced = CurriculumProvider.checkAndAdvance(ctx, curriculumTrack, targetWpm, r.wpm)
            if (advanced != null) curriculumStage = advanced
            curriculumProgress = CurriculumProvider.stageProgress(ctx, curriculumTrack, curriculumStage)
        }
        keyStatSnapshot = loadKeyStatSnapshot(ctx, curriculumTrack)
        keyAnalysisList = TypingKeyStatStore.getKeyAnalysis(ctx, curriculumTrack)
        // ── XP/লেভেল — সরল ফর্মুলা: সঠিক অক্ষর + WPM বোনাস + উচ্চ-Accuracy বোনাস।
        // (আগে এখানে বলা ছিল লিডারবোর্ড ব্যাকএন্ড লাগবে বলে বাদ, কিন্তু আবিষ্কার হলো
        // TypingRaceRepository আগে থেকেই RTDB ব্যবহার করছে — তাই এখন লিডারবোর্ডও যোগ
        // করা হলো, নতুন কোনো ইনফ্রা লাগেনি) ──
        val xpEarned = r.correctChars + (r.wpm * 2) + (if (r.accuracy >= 95) 30 else 0)
        val (lvlBefore, lvlAfter) = session.addTypingXp(xpEarned)
        if (lvlAfter > lvlBefore) levelUpTo = lvlAfter
        // ── নতুন personal-best হলেই লিডারবোর্ডে জমা দেওয়া হয় (repo নিজেই ডুপ্লিকেট/
        // পুরনো-স্কোর write এড়ায়, দেখো TypingLeaderboardRepository.submitScore) —
        // এখানে session.getTypingBestWpm() সরাসরি কল করা হয়েছে, নিচের `bestWpm`
        // state var (UI-তে দেখানোর জন্য) না, কারণ এই ব্লকটা সেই var-এর declaration-এর
        // আগে চলে (Kotlin-এ লোকাল ভ্যারিয়েবল forward-reference করা যায় না) ──
        val myPhone = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() }
        if (myPhone != null && r.wpm > session.getTypingBestWpm()) {
            val myName = session.getCurrentUser()?.displayName() ?: "ব্যবহারকারী"
            leaderboardRepo.submitScore(curriculumTrack, myPhone, myName, r.wpm, r.accuracy)
        }
        weakWordDashboard = AppDatabase.getInstance(ctx).typingMistakeDao()
            .getTopWeakWords(session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest", "bn", limit = 10)
            .map { it.targetWord }
        // ── পর্ব ৩/৫.৩ ধাপ ২ (শেষ অংশ): sync-loss (স্পেস-মিস অটো-রিসিঙ্ক) হয়ে থাকলে
        // সেশন-শেষে ছোট ভয়েস-টিপ — মাঝপথে না দেওয়ার কারণ মূল ফাইলেও একই ছিল
        // (তখন ভয়েস মনোযোগ আরও ভাঙতে পারে) ──
        if (r.syncLossCount > 0) {
            TtsManager.speak(
                "তুমি এই সেশনে ${r.syncLossCount} বার টেক্সট ট্র্যাক হারিয়েছ। ধীরে টাইপ করো, একবারে কয়েকটা শব্দ পড়ে তারপর টাইপ করো।",
                key = "typing_sync_tip"
            )
        }
    }

    val passageWords = remember(state.passage) { state.passage.split(' ') }
    val nextTypeChar: Char? = remember(state.userInput, passageWords) {
        val liveSplit = splitTypedWords(state.userInput)
        val wIdx = state.frozenWordResults.size
        val word = passageWords.getOrNull(wIdx)
        when {
            word == null -> null
            liveSplit.current.length < word.length -> word[liveSplit.current.length]
            wIdx < passageWords.size - 1 -> ' '
            else -> null
        }
    }
    // ── raw পরের কী — স্পেস/চন্দ্রবিন্দুর ডিসপ্লে-গ্লিফ CurrentKeyAndAllKeysBox-এর
    // ভেতরে practiceKeyGlyph() দিয়ে হয়, এখানে raw মান রাখাই ঠিক (highlighting/
    // stat-lookup-এর জন্য) ──
    val currentKeyForBox: String? = remember(nextTypeChar) { nextTypeChar?.toString() }

    // ── এডমিন স্টেজ-কনটেন্ট এডিটর — শুধু এডমিনদের জন্য, ALL KEYS বক্সের ধারে-কাছে
    // একটা ✏️ বাটন থেকে খোলে (নিচে দেখো) ──
    if (showStageAdmin) {
        CurriculumStageAdminScreen(onBack = { showStageAdmin = false }, initialTrack = curriculumTrack, initialStage = curriculumStage)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🧠 Smart Typing", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── ট্র্যাক + মোড বাটন ──
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("bn" to "বাংলা", "en" to "ইংরেজি").forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (curriculumTrack == key && state.sessionMode == "curriculum") Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { startCurriculumSession(key) }
                    ) {
                        Text(label, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            color = if (curriculumTrack == key && state.sessionMode == "curriculum") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (state.sessionMode == "keydrill") Color(0xFFB91C1C) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { startKeyDrillSession() }
                ) {
                    Text("🎯 দুর্বল-কী", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = if (state.sessionMode == "keydrill") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                Surface(
                    shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { startBigramDrillSession() }
                ) {
                    Text("🔗 ধীর জুটি", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (state.sessionMode == "adaptive") Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { startAdaptiveSession(curriculumTrack) }
                ) {
                    Text("🎯 AI Adaptive", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = if (state.sessionMode == "adaptive") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }

            Text("স্টেজ $curriculumStage / ${BijoyCurriculum.totalStages(curriculumTrack)}",
                fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ── পর্ব ৩/৫.৩ ধাপ ২: দুর্বল-শব্দ ড্যাশবোর্ড — পুরনো সেশনগুলো থেকে যেসব
            // শব্দে বারবার ভুল হয়েছে, সেগুলো এক নজরে (spaced-repetition সচেতনতা) ──
            if (weakWordDashboard.isNotEmpty() && !state.isStarted) {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB45309).copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📌 তোমার দুর্বল শব্দগুলো (আগের সেশন থেকে)", fontSize = 11.sp,
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                        Text(weakWordDashboard.joinToString("   "), fontSize = 13.sp, fontFamily = NotoSansBengali)
                    }
                }
            }

            // ── লাইভ স্ট্যাটস (Accuracy% সহ) ──
            val resolvedCount = remember(state.userInput, state.passage) {
                val split = splitTypedWords(state.userInput)
                var total = 0
                for (i in split.completed.indices) {
                    total += (passageWords.getOrNull(i)?.length ?: 0)
                    if (i < passageWords.size - 1) total += 1
                }
                total + split.current.length
            }
            StatsRow(
                elapsedSec = state.elapsedSec, resolvedCount = resolvedCount, passage = state.passage,
                isStarted = state.isStarted, correctKeystrokes = state.correctKeystrokes,
                totalKeystrokes = state.totalKeystrokes, showAccuracy = true, heroStyle = true
            )

            // ── CURRENT KEY + ALL KEYS ──
            if (allUnlockedKeys.isNotEmpty()) {
                CurrentKeyAndAllKeysBox(
                    allKeys = allUnlockedKeys, currentKey = currentKeyForBox, statSnapshot = keyStatSnapshot,
                    keyProgress = if (state.sessionMode == "curriculum") curriculumProgress else emptyList()
                )
            }

            // ── AI Adaptive Session — ফেজ ১ শেষে ট্রানজিশন কার্ড ──
            if (showAdaptiveTransition) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("✅ ফেজ ১ সম্পন্ন!", fontSize = 16.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                        adaptivePhase1Result?.let { r ->
                            Text("${r.wpm} WPM · ${r.accuracy}% নির্ভুলতা", fontSize = 13.sp, fontFamily = NotoSansBengali)
                        }
                        if (phase2Passage == null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("তোমার দুর্বল শব্দ দিয়ে AI প্যাসেজ তৈরি হচ্ছে...", fontSize = 12.sp, fontFamily = NotoSansBengali)
                            }
                        } else {
                            Text(
                                "ফেজ ২: তোমার দুর্বল শব্দ দিয়ে বানানো প্যাসেজ" +
                                    (if (phase2Source == "live_ai") " (AI-জেনারেটেড)" else ""),
                                fontSize = 12.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { startAdaptivePhase2() }, modifier = Modifier.fillMaxWidth()) {
                                Text("ফেজ ২ শুরু করো →", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            } else {

            // ── প্যাসেজ প্রদর্শন ──
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                // ── এডমিন-অনলি ✏️ এডিট বাটন — এই স্টেজের প্র্যাকটিস-কনটেন্ট সরাসরি
                // এডিট করে Google Sheet-এ সেভ করা যায় (দেখো CurriculumStageAdminScreen.kt) ──
                if (isAdmin && state.sessionMode == "curriculum") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.End) {
                        Surface(
                            shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { showStageAdmin = true }
                        ) {
                            Text(
                                "✏️ এডিট", fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                val liveSplit = remember(state.userInput) { splitTypedWords(state.userInput) }
                val annotated = remember(state.passage, state.frozenWordResults, liveSplit, blindMode) {
                    buildAnnotatedString {
                        passageWords.forEachIndexed { wIdx, word ->
                            when {
                                wIdx < state.frozenWordResults.size -> {
                                    val ok = state.frozenWordResults[wIdx]
                                    val wasAutoFixed = state.autoFixedWordFlags.getOrNull(wIdx) == true
                                    val style = if (blindMode) SpanStyle(color = onSurfaceColor) else when {
                                        wasAutoFixed -> SpanStyle(color = AmberWarn)
                                        ok -> SpanStyle(color = GreenOk)
                                        else -> SpanStyle(color = RedWrong)
                                    }
                                    withStyle(style) { append(word) }
                                }
                                wIdx == state.frozenWordResults.size -> {
                                    for (ci in word.indices) {
                                        val typedChar = liveSplit.current.getOrNull(ci)
                                        val style = when {
                                            typedChar == null -> SpanStyle(background = Color(0xFFDBEAFE))
                                            blindMode -> SpanStyle(background = Color(0xFFDBEAFE))
                                            typedChar == word[ci] -> SpanStyle(color = GreenOk, background = Color(0xFFDCFCE7))
                                            else -> SpanStyle(color = RedWrong, background = Color(0xFFFEE2E2))
                                        }
                                        withStyle(style) { append(word[ci]) }
                                    }
                                }
                                else -> append(word)
                            }
                            if (wIdx < passageWords.size - 1) append(" ")
                        }
                    }
                }
                // ── ফিক্সড-হাইট + অটো-স্ক্রল (NormalTypingScreen-এর মতোই) — টেক্সটবক্স
                // যাতে সবসময় একই জায়গায় থাকে, লম্বা প্যাসেজে নিচে না সরে যায় ──
                val passageScrollState = rememberScrollState()
                var passageLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                Text(
                    annotated, fontSize = 18.sp, fontFamily = NotoSansBengali, lineHeight = 30.sp,
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .verticalScroll(passageScrollState)
                        .padding(16.dp),
                    onTextLayout = { passageLayout = it }
                )
                LaunchedEffect(resolvedCount, passageLayout) {
                    val layout = passageLayout ?: return@LaunchedEffect
                    val textLen = layout.layoutInput.text.length
                    if (textLen == 0) return@LaunchedEffect
                    val offset = resolvedCount.coerceIn(0, textLen - 1)
                    val line = layout.getLineForOffset(offset)
                    val lineTop = layout.getLineTop(line).toInt()
                    passageScrollState.animateScrollTo(lineTop.coerceAtLeast(0))
                }
            }

            // ── ব্যাকস্পেস-লক + Blind Mode চিপ ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = state.showBackspaceWarning) {
                    Text("🔒 লকড — সামনে এগিয়ে যান", color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = NotoSansBengali)
                }
                if (!state.showBackspaceWarning) Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactToggleChip(icon = "🔒", label = "Backspace", checked = state.backspaceLocked) {
                        vm.setBackspaceLocked(!state.backspaceLocked)
                    }
                    CompactToggleChip(icon = "🙈", label = "Blind", checked = blindMode) { blindMode = !blindMode }
                }
            }

            // ── ইনপুট ফিল্ড ──
            TypingInputField(
                value = state.userInput,
                isFinished = state.isFinished,
                isBackspaceBlocked = state.backspaceLocked,
                onValueChange = { vm.onInputChange(it) },
                onEscape = if (state.isStarted && !state.isFinished) { { vm.finishSession() } } else null,
                onRestart = { vm.restartCurrentPassage() }
            )

            if (state.isStarted && !state.isFinished) {
                RhythmMeter(score = state.rhythmScore)
                LessonProgressBar(resolvedCount = resolvedCount, totalCount = state.passage.length)
                ProTipBanner(accuracyPct = if (state.totalKeystrokes > 0) state.correctKeystrokes * 100 / state.totalKeystrokes else 100)
                // ── "🎯 এগুলোতে ফোকাস করো" এখন ALL KEYS-এর পাশের (i) বাটনে (দেখো
                // CurrentKeyAndAllKeysBox/KeyBoxInfoDialog) — এখানে আলাদা বড় সেকশন
                // হিসেবে আর দেখানো হয় না, মূল স্ক্রিনে জায়গা বাঁচাতে ──
                Button(
                    onClick = { vm.finishSession() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("📤 Submit Now", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
            }   // ← showAdaptiveTransition-এর else ব্লক শেষ

            // ── Key Analysis (সেশন শেষে) ──
            if (state.isFinished && keyAnalysisList.isNotEmpty()) {
                TextButton(onClick = { showKeyAnalysis = !showKeyAnalysis }) {
                    Text(if (showKeyAnalysis) "🔬 Key Analysis লুকাও" else "🔬 Key Analysis দেখাও",
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                }
                if (showKeyAnalysis) {
                    KeyAnalysisSection(analysis = keyAnalysisList, targetWpm = 20)
                }
            }
        }
    }

    // ── ফুল-স্ক্রিন রেজাল্ট পপ-আপ ──
    // ── AI Adaptive Session ফেজ ১ শেষ হলে ফুল-স্ক্রিন পপ-আপ না দেখিয়ে শুধু ট্রানজিশন
    // কার্ড দেখানো হয় (ওপরে) — ফেজ ২ শেষ হলেই (বা curriculum/keydrill/অন্যান্য মোডে)
    // এই সাধারণ পপ-আপ দেখানো হবে ──
    val suppressResultPopup = state.sessionMode == "adaptive" && adaptivePhase == 1
    if (state.isFinished && state.result != null && !suppressResultPopup) {
        var bestWpm by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { bestWpm = session.getTypingBestWpm() }
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.result?.let { r ->
                        ResultCard(
                            result = r, bestWpm = bestWpm, showSmartFeatures = true,
                            sessionMistakeWords = sessionMistakeWords,
                            weakKeyProgress = if (state.sessionMode == "curriculum") curriculumProgress else emptyList(),
                            heatmapKeys = allUnlockedKeys, heatmapStats = keyStatSnapshot,
                            onAiMistakeDrill = { startAiMistakeDrillSession() },
                            onRetry = { vm.restartCurrentPassage() },
                            onNextPassage = {
                                sessionMistakeWords = emptyList()
                                when (state.sessionMode) {
                                    "curriculum" -> startCurriculumSession(curriculumTrack)
                                    "keydrill" -> startKeyDrillSession()
                                    "adaptive" -> startAdaptiveSession(curriculumTrack)
                                    else -> startCurriculumSession(curriculumTrack)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ── 🎉 Level Up! — সেশন শেষে XP-লেভেল বাড়লে ফুল-রেজাল্ট পপ-আপের উপরে এই ছোট
    // সেলিব্রেশন ডায়ালগ দেখানো হয় (রিটেনশন গেমিফিকেশন লেয়ার, লিডারবোর্ড না —
    // এটার জন্য ব্যাকএন্ড লাগবে, আপাতত সম্পূর্ণ লোকাল/ব্যক্তিগত) ──
    levelUpTo?.let { lvl ->
        Dialog(onDismissRequest = { levelUpTo = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉", fontSize = 44.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Level Up!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                    Text(
                        "এখন আপনি লেভেল $lvl", fontSize = 14.sp, fontFamily = NotoSansBengali,
                        color = Color(0xFF6366F1), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { levelUpTo = null }, shape = RoundedCornerShape(14.dp)) {
                        Text("দারুণ! 🎊", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
