package com.hanif.smartstudy.ui.typing

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
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TypingErrorAnalyzer
import com.hanif.smartstudy.util.TypingPassageProvider
import com.hanif.smartstudy.viewmodel.TypingSessionViewModel

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  ExamTypingScreen — পর্ব ৩/৫.৩ — ধাপ ৪ (তৃতীয়/শেষ নতুন মোড-স্ক্রিন)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Normal/Smart-এর মতোই — সম্পূর্ণ নতুন, স্বতন্ত্র ফাইল, `TypingPracticeScreen.kt`-এ
 * কোনো লজিক পরিবর্তন নেই (শুধু `ExamResultCard`/`EXAM_PHASE_SECONDS`-এর visibility
 * `internal` করা হয়েছে, পুনর্ব্যবহারের জন্য)।
 *
 * দুইটা সাব-মোড এক স্ক্রিনে:
 *  ১. **BCC Exam Simulation** — দুই-ফেজ (ইংরেজি ১০ মিনিট → বাংলা ১০ মিনিট),
 *     প্রতিটা ফেজের আলাদা, স্বাধীন WPM — কোনোটাই "প্র্যাকটিস বেস্ট WPM"/হিস্ট্রিতে
 *     যোগ হয় না (দেখো ViewModel-এর `shouldRecordAsNormalResult` চেক)।
 *  ২. **Govt Job Mock Test** — এক-ফেজ, ব্যাকস্পেস বাধ্যতামূলক লকড, ভুল-কী-প্রেসে
 *     WPM পেনাল্টি (-0.5/ভুল), ইউজার-নির্বাচিত সময়সীমা (৫/১০/১৫ মিনিট)।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamTypingScreen(
    onBack: () -> Unit,
    onResult: (TypingResult) -> Unit = {},
    vm: TypingSessionViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }
    val state by vm.state.collectAsState()

    var allPassages by remember { mutableStateOf(listOf<PassageInfo>()) }
    fun poolForLanguage(language: String): List<PassageInfo> =
        allPassages.filter { TypingErrorAnalyzer.detectLanguage(it.text) == language }.ifEmpty { allPassages }

    // ── Exam Simulation-এর দুই-ফেজ স্টেট (স্ক্রিন-লোকাল — ViewModel প্রতিটা ফেজের
    // জন্য আলাদাভাবে কল হয়, তাই দুই ফেজের ফলাফল এখানেই আলাদা করে জমা রাখা হয়) ──
    var examMode by remember { mutableStateOf<String?>(null) }   // null | "exam" | "govtmock"
    var examPhase by remember { mutableStateOf("en") }
    var examEnglishResult by remember { mutableStateOf<TypingResult?>(null) }
    var examBanglaResult by remember { mutableStateOf<TypingResult?>(null) }
    var showPhaseTransition by remember { mutableStateOf(false) }
    var govtMockMinutes by remember { mutableStateOf(10) }

    LaunchedEffect(Unit) { allPassages = TypingPassageProvider.getPassages(ctx) }

    fun startExamSimulation() {
        examMode = "exam"; examPhase = "en"; examEnglishResult = null; examBanglaResult = null; showPhaseTransition = false
        vm.startSession("exam", poolForLanguage("en"), budgetSec = EXAM_PHASE_SECONDS, language = "en")
    }

    fun startGovtMockTest(minutes: Int) {
        examMode = "govtmock"; govtMockMinutes = minutes
        vm.startSession("govtmock", allPassages, budgetSec = minutes * 60, language = "bn")
    }

    // ── ফেজ-১ (ইংরেজি) শেষ হলে ট্রানজিশন কার্ড দেখানো; ফেজ-২ (বাংলা) শেষ হলে
    // চূড়ান্ত ExamResultCard-এর জন্য ফলাফল জমা রাখা ──
    LaunchedEffect(state.isFinished, state.result) {
        val r = state.result ?: return@LaunchedEffect
        if (!state.isFinished) return@LaunchedEffect
        // ── মূল TypingPracticeScreen.kt-এ শুধু govtmock (সাধারণ finishSession() পথ)
        // onResult() কল করত, exam phase-এর finishExamPhase() কখনো করত না — এখানেও
        // সেই একই আচরণ বজায় রাখা হলো ──
        if (examMode == "govtmock") onResult(r)
        if (examMode != "exam") return@LaunchedEffect
        if (examPhase == "en" && examEnglishResult == null) {
            examEnglishResult = r
            showPhaseTransition = true
        } else if (examPhase == "bn" && examBanglaResult == null) {
            examBanglaResult = r
        }
    }

    fun startExamBanglaPhase() {
        examPhase = "bn"; showPhaseTransition = false
        vm.startSession("exam", poolForLanguage("bn"), budgetSec = EXAM_PHASE_SECONDS, language = "bn")
    }

    val passageWords = remember(state.passage) { state.passage.split(' ') }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏛️ Exam / Govt Mock", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
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
            if (examMode == null) {
                // ── মোড-বাছাই স্ক্রিন ──
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF1D4ED8),
                    onClick = { startExamSimulation() }
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("🏛️ BCC Exam Simulation", fontSize = 15.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("দুই-ফেজ: ইংরেজি ১০ মিনিট → বাংলা ১০ মিনিট", fontSize = 11.sp,
                            fontFamily = NotoSansBengali, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Text("🏛️ Govt Job মক টেস্ট (Backspace বন্ধ, ভুলে পেনাল্টি)", fontSize = 13.sp,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { m ->
                        Surface(
                            shape = RoundedCornerShape(10.dp), color = Color(0xFF7C2D12),
                            modifier = Modifier.clickable { startGovtMockTest(m) }
                        ) {
                            Text("$m মিনিট", fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                                color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }
            } else if (showPhaseTransition) {
                // ── ইংরেজি ফেজ শেষ, বাংলা ফেজ শুরুর ট্রানজিশন ──
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("✅ ইংরেজি ফেজ সম্পন্ন!", fontSize = 16.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                        examEnglishResult?.let { r ->
                            Text("${r.wpm} WPM · ${r.accuracy}% নির্ভুলতা", fontSize = 13.sp, fontFamily = NotoSansBengali)
                        }
                        Text("এখন বাংলা ফেজ (১০ মিনিট) শুরু হবে।", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { startExamBanglaPhase() }, modifier = Modifier.fillMaxWidth()) {
                            Text("বাংলা ফেজ শুরু করো →", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            } else if (examBanglaResult != null && examEnglishResult != null) {
                // ── উভয় ফেজ শেষ — চূড়ান্ত রেজাল্ট ──
                ExamResultCard(
                    englishResult = examEnglishResult!!, banglaResult = examBanglaResult!!,
                    onRestart = { examMode = null; examEnglishResult = null; examBanglaResult = null }
                )
            } else {
                // ── সক্রিয় টাইপিং সেশন (exam ফেজ-১/২ অথবা govtmock) ──
                Text(
                    if (examMode == "exam") "ফেজ: ${if (examPhase == "en") "ইংরেজি" else "বাংলা"} (১০ মিনিট)"
                    else "Govt Mock — $govtMockMinutes মিনিট (Backspace বন্ধ)",
                    fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    val liveSplit = remember(state.userInput) { splitTypedWords(state.userInput) }
                    val annotated = remember(state.passage, state.frozenWordResults, liveSplit) {
                        buildAnnotatedString {
                            passageWords.forEachIndexed { wIdx, word ->
                                when {
                                    wIdx < state.frozenWordResults.size -> {
                                        val ok = state.frozenWordResults[wIdx]
                                        val wasAutoFixed = state.autoFixedWordFlags.getOrNull(wIdx) == true
                                        val style = when {
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

                if (examMode == "govtmock") {
                    Text("🔒 Backspace বন্ধ — ভুল হলেও সামনে এগিয়ে যেতে হবে (বাস্তব সরকারি পরীক্ষার নিয়ম)",
                        fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.error)
                }

                OutlinedTextField(
                    value = state.userInput,
                    onValueChange = { if (!state.isFinished) vm.onInputChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("এখানে type করা শুরু করুন...", fontFamily = NotoSansBengali) },
                    enabled = !state.isFinished,
                    keyboardOptions = KeyboardOptions.Default,
                    minLines = 4
                )

                if (state.isStarted && !state.isFinished) {
                    Button(
                        onClick = { vm.finishSession() }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                    ) {
                        Text("📤 Submit Now", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

    // ── Govt Mock-এর ফুল-স্ক্রিন রেজাল্ট (পেনাল্টি-সহ) ──
    if (examMode == "govtmock" && state.isFinished && state.result != null) {
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.result?.let { r ->
                        ResultCard(
                            result = r, bestWpm = bestWpm, showSmartFeatures = false,
                            onRetry = { startGovtMockTest(govtMockMinutes) },
                            onNextPassage = { examMode = null }
                        )
                        // ── Phase ২: পেনাল্টি হিসাব — r.wpm অপরিবর্তিত (bestWpm/history-এর
                        // সাথে মেলাতে), শুধু আলাদা কার্ডে "কার্যকর WPM" দেখানো হয় ──
                        val penaltyWpm = ((r.totalChars - r.correctChars) * 0.5).toInt()
                        Card(
                            Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7C2D12).copy(alpha = 0.12f))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🏛️ Govt Job মক টেস্ট — পেনাল্টি হিসাব", fontSize = 12.sp,
                                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                                Text(
                                    "ভুল কীপ্রেস: ${r.totalChars - r.correctChars}টা → পেনাল্টি: -$penaltyWpm WPM",
                                    fontSize = 12.sp, fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "কার্যকর WPM (পেনাল্টির পর): ${(r.wpm - penaltyWpm).coerceAtLeast(0)}",
                                    fontSize = 14.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF9A3412)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
