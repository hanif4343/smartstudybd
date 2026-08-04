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
import com.hanif.smartstudy.data.model.BijoyCurriculum
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.CurriculumProvider
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TypingKeyStatStore
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
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()

    var curriculumTrack by remember { mutableStateOf("bn") }
    var curriculumStage by remember { mutableStateOf(1) }
    var curriculumProgress by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var keyStatSnapshot by remember { mutableStateOf(mapOf<String, Pair<Int, Int>>()) }
    var blindMode by remember { mutableStateOf(false) }
    var showKeyAnalysis by remember { mutableStateOf(false) }
    var keyAnalysisList by remember { mutableStateOf(listOf<TypingKeyStatStore.KeyAnalysis>()) }

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
            val drillText = CurriculumProvider.buildDrillPassage(track, curriculumStage)
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

    LaunchedEffect(Unit) { startCurriculumSession("bn") }

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
    val currentKeyForBox: String? = remember(nextTypeChar, state.frozenWordResults, passageWords) {
        if (nextTypeChar != null && nextTypeChar != ' ') nextTypeChar.toString()
        else passageWords.getOrNull(state.frozenWordResults.size + 1)?.firstOrNull()?.toString()
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
            }

            Text("স্টেজ $curriculumStage / ${BijoyCurriculum.totalStages(curriculumTrack)}",
                fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                totalKeystrokes = state.totalKeystrokes, showAccuracy = true
            )

            // ── CURRENT KEY + ALL KEYS ──
            if (allUnlockedKeys.isNotEmpty()) {
                CurrentKeyAndAllKeysBox(allKeys = allUnlockedKeys, currentKey = currentKeyForBox, statSnapshot = keyStatSnapshot)
            }

            // ── প্যাসেজ প্রদর্শন ──
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
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
                Text(annotated, fontSize = 18.sp, fontFamily = NotoSansBengali, lineHeight = 30.sp, modifier = Modifier.padding(16.dp))
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
            OutlinedTextField(
                value = state.userInput,
                onValueChange = { if (!state.isFinished) vm.onInputChange(it) },
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        keyEvent.key == Key.Escape && state.isStarted && !state.isFinished -> { vm.finishSession(); true }
                        keyEvent.key == Key.R && keyEvent.isCtrlPressed -> { vm.restartCurrentPassage(); true }
                        else -> false
                    }
                },
                placeholder = { Text("এখানে type করা শুরু করুন...", fontFamily = NotoSansBengali) },
                enabled = !state.isFinished,
                keyboardOptions = KeyboardOptions.Default,
                minLines = 4
            )

            if (state.isStarted && !state.isFinished) {
                RhythmMeter(score = state.rhythmScore)
                LessonProgressBar(resolvedCount = resolvedCount, totalCount = state.passage.length)
                ProTipBanner(accuracyPct = if (state.totalKeystrokes > 0) state.correctKeystrokes * 100 / state.totalKeystrokes else 100)
                if (state.sessionMode == "curriculum" && curriculumProgress.isNotEmpty()) {
                    PerCharacterCoachCards(curriculumProgress)
                }
                Button(
                    onClick = { vm.finishSession() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("📤 Submit Now", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }

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
    if (state.isFinished && state.result != null) {
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
                            onRetry = { vm.restartCurrentPassage() },
                            onNextPassage = {
                                when (state.sessionMode) {
                                    "curriculum" -> startCurriculumSession(curriculumTrack)
                                    "keydrill" -> startKeyDrillSession()
                                    else -> startCurriculumSession(curriculumTrack)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
