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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TypingErrorAnalyzer
import com.hanif.smartstudy.util.TypingPassageProvider
import com.hanif.smartstudy.viewmodel.TypingSessionViewModel

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  NormalTypingScreen — পর্ব ৩/৫.৩ (মোড-সেপারেশন) — ধাপ ২ (প্রথম নতুন স্ক্রিন)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * এটা `TypingPracticeScreen.kt`-এর একটা **সম্পূর্ণ নতুন, স্বতন্ত্র** ফাইল —
 * পুরনো ফাইলে **কোনো পরিবর্তন করা হয়নি** (Smart/Exam/curriculum ইত্যাদি সব
 * মোড আগের মতোই TypingPracticeScreen.kt দিয়ে চলবে, কোনো regression-ঝুঁকি নেই)।
 *
 * এই স্ক্রিন শুধু Normal Typing-এর (free/সাধারণ প্র্যাকটিস) জন্য, নতুন
 * `TypingSessionViewModel`-এর ওপর ভিত্তি করে বানানো — পর্ব ৫.২-এর স্কোপ-
 * ম্যাপিং অনুযায়ী। কিছু UI কম্পোনেন্ট (StatsRow/ResultCard/ResultStat/
 * StatBox/CompactToggleChip + রঙের প্যালেট) পুরনো ফাইল থেকেই পুনর্ব্যবহার
 * করা হয়েছে (visibility `private` → `internal` করে, কোনো ডুপ্লিকেশন ছাড়া)।
 *
 * ⚠️ এখনো বাকি (পরের ধাপ): MainScreen.kt-তে এই স্ক্রিনে নেভিগেট করার এন্ট্রি-
 * পয়েন্ট যোগ করা (এই ফাইল এখনো কোথাও থেকে কল হচ্ছে না) — ইচ্ছাকৃতভাবে আলাদা
 * রাখা হয়েছে, যাতে এই ফাইলটা আগে এককভাবে কম্পাইল-রিভিউ করা যায়।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalTypingScreen(
    onBack: () -> Unit,
    onResult: (TypingResult) -> Unit = {},
    vm: TypingSessionViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()

    // ── পুরনো TypingPracticeScreen-এর মতোই — ফলাফল এলেই onResult() কল হয়
    // (MainScreen-এ achievement-unlock এই কলব্যাকের ওপর নির্ভর করে, দেখো
    // onResult = { r -> if (r.wpm >= 40) unlockAchievement("typing_40wpm") }) ──
    LaunchedEffect(state.result) {
        state.result?.let { onResult(it) }
    }

    var difficulty by remember { mutableStateOf("all") }   // all/easy/medium/hard
    var language   by remember { mutableStateOf("bn") }     // bn/en
    var allPassages by remember { mutableStateOf(listOf<PassageInfo>()) }

    // ── FIX: আগে এখানে শুধু difficulty দিয়ে ফিল্টার হতো, ভাষা (bn/en) একদম উপেক্ষা
    // করা হতো — ফলে "English" সিলেক্ট করলেও পুল-এ বাংলা প্যাসেজ থেকে যেত, আর প্যাসেজ
    // শেষে/সাবমিটে পরের প্যাসেজ (advanceToNextPassage) সেই একই না-ফিল্টার-করা পুল
    // থেকে বাছাই করত বলে মাঝে মাঝে বাংলা চলে আসত। এখন difficulty-এর পাশাপাশি
    // TypingErrorAnalyzer.detectLanguage() দিয়ে প্রতিটা প্যাসেজের ভাষা যাচাই করে
    // সিলেক্ট-করা language-এর সাথে না মিললে বাদ দেওয়া হয় — তাই "English" সিলেক্ট
    // থাকলে সবসময় শুধু ইংরেজি, "বাংলা" সিলেক্ট থাকলে সবসময় শুধু বাংলা প্যাসেজ আসবে ──
    fun currentPool(): List<PassageInfo> =
        allPassages.filter {
            (difficulty == "all" || it.difficulty == difficulty) &&
                TypingErrorAnalyzer.detectLanguage(it.text) == language
        }

    // ── প্রথমবার স্ক্রিন খোলার সময় Timer On/Off পছন্দ (persisted) লোড করে, তারপর
    // প্যাসেজ-পুল লোড করে সেশন শুরু + cloud sync পুল ──
    LaunchedEffect(Unit) {
        vm.setTimerEnabled(session.getTypingTimerEnabled())
        vm.syncFromCloud()
        allPassages = TypingPassageProvider.getPassages(ctx)
        vm.startSession("free", currentPool(), budgetSec = 300)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🌿 ফ্রি টাইপিং", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── ডিফিকাল্টি + ভাষা সিলেক্টর ──
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "সব", "easy" to "সহজ", "medium" to "মাঝারি", "hard" to "কঠিন").forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (difficulty == key) Indigo600 else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            difficulty = key
                            vm.startSession("free", currentPool(), budgetSec = 300)
                        }
                    ) {
                        Text(label, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            color = if (difficulty == key) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                listOf("bn" to "🌐 বাংলা", "en" to "🌐 English").forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (language == key) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            language = key
                            vm.startSession("free", currentPool(), budgetSec = 300)
                        }
                    ) {
                        Text(label, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            color = if (language == key) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }

            // ── Timer On/Off — যারা নতুন টাইপিং শিখছে, তারা সময়ের চাপ ছাড়া নিজের
            // গতিতে টাইপ করতে পারবে (Timer বন্ধ থাকলে সময়সীমা শেষ হলেও সেশন জোর
            // করে শেষ হবে না)। দক্ষ ইউজাররা Timer চালু রেখে আগের মতোই টাইমড
            // প্র্যাকটিস করতে পারবে। পছন্দটা সেভ থাকে, পরের বার স্ক্রিন খুললেও মনে থাকে ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.timerEnabled) "⏱️ টাইমার চালু আছে" else "⏱️ টাইমার বন্ধ — যতক্ষণ ইচ্ছা টাইপ করুন",
                    fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompactToggleChip(icon = "⏱️", label = "Timer", checked = state.timerEnabled) {
                    val next = !state.timerEnabled
                    vm.setTimerEnabled(next)
                    scope.launch { session.setTypingTimerEnabled(next) }
                }
            }

            // ── Practice / Quick-3 বাটন ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    color = if (state.freeModeBudgetSec > 180) Indigo600 else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { vm.startSession("free", currentPool(), budgetSec = 300) }
                ) {
                    Text("✍️ প্র্যাকটিস (৫ মিনিট)", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = if (state.freeModeBudgetSec > 180) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                }
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    color = if (state.freeModeBudgetSec <= 180) Color(0xFFCA8A04) else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { vm.startSession("free", currentPool(), budgetSec = 180) }
                ) {
                    Text("⚡ Quick 3", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = if (state.freeModeBudgetSec <= 180) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                }
            }

            // ── লাইভ স্ট্যাটস ──
            val passageWords = remember(state.passage) { state.passage.split(' ') }
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
                totalKeystrokes = state.totalKeystrokes, showAccuracy = false, heroStyle = true
            )

            // ── প্যাসেজ প্রদর্শন (সঠিক=সবুজ, ভুল=লাল, বর্তমান শব্দ=নীল ব্যাকগ্রাউন্ড) ──
            // ── Card-টা একটা Box-এ মোড়ানো, যাতে ওপর-ডানে "⏭️ পরের প্যাসেজ" বাটন
            // ভাসিয়ে রাখা যায় — সাবমিট/সময়-শেষ ছাড়াই, বর্তমান প্যাসেজ পছন্দ না
            // হলে বা কঠিন লাগলে সরাসরি এক-ট্যাপে অন্য একটা প্যাসেজে চলে যাওয়া যায়
            // (cumulative WPM/Accuracy স্ট্যাট অক্ষত থাকে, restartCurrentPassage()-এর
            // মতোই — শুধু প্যাসেজটাই পাল্টায়) ──
            Box(Modifier.fillMaxWidth()) {
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
                // ── ফিক্সড-হাইট + ভেতরে স্ক্রল — প্যাসেজ যত লম্বাই হোক, কার্ডের উচ্চতা
                // পাল্টায় না, তাই নিচের ইনপুট-বক্স সবসময় একই জায়গায় থাকে (আগে লম্বা
                // প্যাসেজে টেক্সটবক্স নিচে সরে যেত)। টাইপ করার সাথে সাথে বর্তমান লাইন
                // পর্যন্ত অটো-স্ক্রল হয় (getLineForOffset দিয়ে বের করা হয়) ──
                val passageScrollState = rememberScrollState()
                var passageLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                Text(
                    annotated, fontSize = 18.sp, fontFamily = NotoSansBengali, lineHeight = 30.sp,
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .verticalScroll(passageScrollState)
                        .padding(16.dp)
                        // ── উপরে-ডানে ভাসমান "পরের" বাটনের সাথে টেক্সট যেন না মিশে যায় ──
                        .padding(end = 46.dp),
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

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { vm.advanceToNextPassage(currentPool()) }
                ) {
                    Text(
                        "⏭️ পরের", fontSize = 10.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }

            // ── ব্যাকস্পেস-লক (কমপ্যাক্ট চিপ) ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = state.showBackspaceWarning) {
                    Text("🔒 লকড — সামনে এগিয়ে যান", color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = NotoSansBengali)
                }
                if (!state.showBackspaceWarning) Spacer(Modifier.weight(1f))
                CompactToggleChip(icon = "🔒", label = "Backspace", checked = state.backspaceLocked) {
                    vm.setBackspaceLocked(!state.backspaceLocked)
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

            // ── ম্যানুয়াল Submit ──
            if (state.isStarted && !state.isFinished) {
                Button(
                    onClick = { vm.finishSession() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Text("📤 Submit Now", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    // ── ফুল-স্ক্রিন রেজাল্ট পপ-আপ (পর্ব ৪.৪-এর সমতুল্য) ──
    if (state.isFinished && state.result != null) {
        var bestWpm by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { bestWpm = session.getTypingBestWpm() }
        Dialog(
            onDismissRequest = { /* ইচ্ছাকৃতভাবে খালি — ব্যাকগ্রাউন্ড লকড */ },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.result?.let { r ->
                        ResultCard(
                            result = r, bestWpm = bestWpm, showSmartFeatures = false,
                            onRetry = { vm.restartCurrentPassage() },
                            onNextPassage = { vm.startSession("free", currentPool(), budgetSec = state.freeModeBudgetSec) }
                        )
                    }
                }
            }
        }
    }
}
