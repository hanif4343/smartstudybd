package com.hanif.smartstudy.ui.typing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.MistakeErrorType
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.Hand
import com.hanif.smartstudy.util.HandKeyMap
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TtsManager
import com.hanif.smartstudy.util.TypingErrorAnalyzer
import com.hanif.smartstudy.util.TypingHistoryEntry
import com.hanif.smartstudy.util.TypingMistakeLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Indigo600 = Color(0xFF4F46E5)
private val GreenOk   = Color(0xFF10B981)
private val RedWrong  = Color(0xFFEF4444)
private val AmberMid  = Color(0xFFF59E0B)
// SlateText -> MaterialTheme.colorScheme.onSurface
// MutedText -> MaterialTheme.colorScheme.onSurfaceVariant
// CardBg -> MaterialTheme.colorScheme.surface

// ── Passage + difficulty ট্যাগ — "easy" | "medium" | "hard" ──
data class PassageInfo(val text: String, val difficulty: String)

private val PASSAGES = listOf(
    // সহজ
    PassageInfo("আমি বই পড়ি। বই পড়তে আমার ভালো লাগে। প্রতিদিন সকালে আমি পড়াশোনা করি।", "easy"),
    PassageInfo("সূর্য পূর্ব দিকে ওঠে। পাখিরা সকালে গান গায়। বাতাস ঠান্ডা ও সতেজ।", "easy"),
    PassageInfo("আমার নাম রাহাত। আমি স্কুলে যাই। আমার প্রিয় বিষয় বিজ্ঞান।", "easy"),
    PassageInfo("Cats and dogs are common pets. They live with people. Many families love them.", "easy"),
    PassageInfo("Water is essential for life. We drink water every day. It keeps us healthy.", "easy"),
    // মাঝারি
    PassageInfo("বাংলাদেশ একটি সুন্দর দেশ। এখানে অনেক নদী আছে। পদ্মা, মেঘনা, যমুনা প্রধান নদী।", "medium"),
    PassageInfo("বিজ্ঞান মানুষের জীবনকে সহজ করেছে। প্রযুক্তির উন্নয়নে পৃথিবী পরিবর্তন হচ্ছে দিন দিন।", "medium"),
    PassageInfo("শিক্ষা জাতির মেরুদণ্ড। একটি শিক্ষিত জাতি সকল প্রতিকূলতা অতিক্রম করতে পারে।", "medium"),
    PassageInfo("The quick brown fox jumps over the lazy dog. Practice typing every day to improve your speed.", "medium"),
    PassageInfo("Reading books regularly improves vocabulary, concentration, and overall mental development.", "medium"),
    // কঠিন
    PassageInfo("Science and technology have transformed human civilization in remarkable ways over centuries.", "hard"),
    PassageInfo("স্বাধীনতা মানুষের জন্মগত অধিকার, যা কোনো জাতি বা রাষ্ট্র কেড়ে নিতে পারে না; এটি রক্ত দিয়ে অর্জিত এক মহান চেতনা।", "hard"),
    PassageInfo("পরিবেশ দূষণ, জলবায়ু পরিবর্তন এবং প্রাকৃতিক সম্পদের অপব্যবহার—এই তিনটি সমস্যা আজ সারা পৃথিবীর জন্য এক বিরাট চ্যালেঞ্জ হয়ে দাঁড়িয়েছে।", "hard"),
    PassageInfo("Artificial intelligence, machine learning, and data science are rapidly reshaping industries, economies, and the very nature of human employment worldwide.", "hard"),
    PassageInfo("কোয়ান্টাম পদার্থবিজ্ঞান এমন একটি ক্ষেত্র যেখানে ক্ষুদ্রাতিক্ষুদ্র কণার আচরণ সাধারণ বুদ্ধি দিয়ে ব্যাখ্যা করা প্রায়ই অসম্ভব হয়ে পড়ে।", "hard"),
)

private fun difficultyLabel(d: String) = when (d) {
    "easy"   -> "সহজ"
    "medium" -> "মাঝারি"
    "hard"   -> "কঠিন"
    else     -> "সব"
}

private fun difficultyColor(d: String) = when (d) {
    "easy"   -> GreenOk
    "medium" -> AmberMid
    "hard"   -> RedWrong
    else     -> Indigo600
}

private fun poolFor(difficulty: String): List<PassageInfo> =
    if (difficulty == "all") PASSAGES else PASSAGES.filter { it.difficulty == difficulty }

/** AI Adaptive Session-এ live generation ব্যর্থ হলে এখান থেকে ভাষা-মিলিয়ে একটা random fallback প্যাসেজ —
 *  দেখো TypingAdaptiveContentProvider.kt */
fun fallbackPassageFor(language: String): String {
    val pool = PASSAGES.filter {
        val isBn = it.text.any { c -> c.code in 0x0980..0x09FF }
        if (language == "bn") isBn else !isBn
    }.ifEmpty { PASSAGES }
    return pool.random().text
}

data class TypingResult(
    val wpm         : Int,   // Net WPM — সঠিকভাবে টাইপ করা অক্ষরের ভিত্তিতে, এটাই মূল ফলাফল
    val rawWpm      : Int,   // Raw/Gross WPM — ভুলসহ মোট টাইপ করা অক্ষরের ভিত্তিতে
    val accuracy    : Int,
    val timeSec     : Int,
    val correctChars: Int,
    val totalChars  : Int,
    // ── ধাপ ৪: হাত-ভিত্তিক ও sync-loss ইনসাইট (সব ডিফল্ট ০, পুরনো caller ভাঙবে না) ──
    val leftCorrect : Int = 0,
    val leftWrong   : Int = 0,
    val rightCorrect: Int = 0,
    val rightWrong  : Int = 0,
    val syncLossCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingPracticeScreen(
    onBack    : () -> Unit,
    onResult  : (TypingResult) -> Unit = {}
) {
    // ── Persistence — Best WPM ও সাম্প্রতিক হিস্ট্রি এখন সরাসরি এই স্ক্রিনই লোড/সেভ
    // করে (SessionManager দিয়ে) — আগে bestWpm বাইরে থেকে প্যারামিটার হিসেবে আসার কথা
    // ছিল কিন্তু MainScreen কখনো সেটা পাস করত না, তাই "Best WPM"/"নতুন Record!"
    // ফিচারটা বাস্তবে কখনোই কাজ করত না। এখন স্ক্রিন নিজেই স্বয়ংসম্পূর্ণ। ──
    val ctx     = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionManager(ctx) }
    val scope   = rememberCoroutineScope()
    var bestWpm by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf<List<TypingHistoryEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        bestWpm = session.getTypingBestWpm()
        history = session.getTypingHistory()
    }

    var selectedDifficulty by remember { mutableStateOf("all") }
    var passageIndex by remember { mutableStateOf(0) }
    var passage      by remember { mutableStateOf(PASSAGES[0].text) }
    var userInput    by remember { mutableStateOf("") }
    var isStarted    by remember { mutableStateOf(false) }
    var isFinished   by remember { mutableStateOf(false) }
    var elapsedSec   by remember { mutableStateOf(0) }
    var result       by remember { mutableStateOf<TypingResult?>(null) }

    // ── কীস্ট্রোক-ভিত্তিক accuracy ট্র্যাকিং ──
    // আগে accuracy বের হতো ফাইনাল স্ট্রিং-কে প্যাসেজের সাথে পজিশন-বাই-পজিশন মিলিয়ে —
    // মাঝপথে ১টা অক্ষর মিস/এক্সট্রা হয়ে গেলে তারপরের পুরো অংশ ভুলভাবে "লাল" দেখাত
    // (cascading mismatch), যদিও ইউজার আসলে ঠিকই টাইপ করছিল। এখন প্রতিটা কী-প্রেসের
    // মুহূর্তেই (target অক্ষরের সাথে মিলিয়ে) ঠিক/ভুল গণনা হয়, তাই ফলাফল নির্ভরযোগ্য।
    var correctKeystrokes   by remember { mutableStateOf(0) }
    var incorrectKeystrokes by remember { mutableStateOf(0) }
    var totalKeystrokes     by remember { mutableStateOf(0) }

    // ── Word-level mistake tracking (Phase ১) — প্যাসেজ বদলালে word-span/ভাষা recompute হয়,
    // loggedWordEnds দিয়ে প্রতিটা শব্দ ঠিক একবারই লগ হয় (বাউন্ডারি একাধিকবার পার হলেও না) ──
    val wordSpans   = remember(passage) { TypingErrorAnalyzer.wordSpans(passage) }
    val passageLang = remember(passage) { TypingErrorAnalyzer.detectLanguage(passage) }
    var loggedWordEnds by remember { mutableStateOf(setOf<Int>()) }

    // ── ধাপ ৪: বাম/ডান হাতের সঠিক-ভুল অক্ষর গণনা (session-local, শেষে Room-এ flush হবে) ──
    var leftCorrectChars  by remember { mutableStateOf(0) }
    var leftWrongChars    by remember { mutableStateOf(0) }
    var rightCorrectChars by remember { mutableStateOf(0) }
    var rightWrongChars   by remember { mutableStateOf(0) }
    var syncLossCount     by remember { mutableStateOf(0) }

    // ── ধাপ ৪: Daily Discipline Mode — non-coercive, শুধু progress track/দেখানো হয় ──
    var disciplineOn      by remember { mutableStateOf(false) }
    var dailyGoalMin      by remember { mutableStateOf(60) }
    var todaySecondsBefore by remember { mutableStateOf(0) }   // এই সেশন শুরুর আগে আজকে যত সেকেন্ড হয়েছিল
    LaunchedEffect(Unit) {
        val rawPref = session.getTypingDisciplineRaw()
        disciplineOn = rawPref ?: (session.getCurrentUser()?.isAdmin() == true).also {
            // প্রথমবার — কখনো explicit সেট করা হয়নি, তাই admin হলে default অন করে persist করা হলো
            if (it) session.setTypingDisciplineOn(true)
        }
        dailyGoalMin = session.getTypingDailyGoalMinutes()
        todaySecondsBefore = session.getTypingTodaySeconds()
        TtsManager.init(ctx)   // একাধিকবার কল করলেও সমস্যা নেই — ইতিমধ্যে init হলে কিছুই করে না
    }
    val vibrator = remember {
        ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    }

    // Timer
    LaunchedEffect(isStarted, isFinished) {
        if (isStarted && !isFinished) {
            while (true) {
                delay(1000)
                elapsedSec++
            }
        }
    }

    // Check completion
    LaunchedEffect(userInput) {
        if (isStarted && userInput.length >= passage.length && !isFinished) {
            isFinished = true
            val timeSec = elapsedSec.coerceAtLeast(1)
            val minutes = timeSec / 60.0
            // ── ইন্ডাস্ট্রি স্ট্যান্ডার্ড: ৫টা ক্যারেক্টার = ১টা "word" ──
            val rawWpm = if (minutes > 0) (totalKeystrokes / 5.0 / minutes).toInt() else 0
            val netWpm = if (minutes > 0) (correctKeystrokes / 5.0 / minutes).toInt().coerceAtLeast(0) else 0
            val acc = if (totalKeystrokes > 0) (correctKeystrokes * 100 / totalKeystrokes) else 100
            val r = TypingResult(
                wpm = netWpm, rawWpm = rawWpm, accuracy = acc, timeSec = timeSec,
                correctChars = correctKeystrokes, totalChars = totalKeystrokes,
                leftCorrect = leftCorrectChars, leftWrong = leftWrongChars,
                rightCorrect = rightCorrectChars, rightWrong = rightWrongChars,
                syncLossCount = syncLossCount
            )
            result = r
            onResult(r)
            scope.launch {
                session.recordTypingResult(r.wpm, r.rawWpm, r.accuracy, r.timeSec)
                bestWpm = maxOf(bestWpm, r.wpm)
                history = session.getTypingHistory()

                // ── ধাপ ৪: এই সেশনের হাত-ভিত্তিক ডেটা Room-এ cumulative করে যোগ ──
                val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
                AppDatabase.getInstance(ctx).typingHandStatsDao().addSessionDelta(
                    userId       = userId,
                    leftCorrect  = leftCorrectChars.toLong(),
                    leftWrong    = leftWrongChars.toLong(),
                    rightCorrect = rightCorrectChars.toLong(),
                    rightWrong   = rightWrongChars.toLong()
                )

                // ── ধাপ ৪: Daily Discipline — আজকের মোট টাইপিং-সময়ে যোগ (মোড অফ থাকলেও
                // ট্র্যাক করা হয়, যাতে পরে অন করলে আজকের ডেটা মিস না হয়; শুধু ব্যানারটা
                // অফ থাকলে দেখানো হয় না — কিছুই জোর করে আটকানো হয় না) ──
                session.addTypingSecondsToday(timeSec)
                todaySecondsBefore = session.getTypingTodaySeconds()

                // ── ধাপ ৪: sync-loss (ধরন B) ধরা পড়লে সেশন-শেষে ছোট ভয়েস-টিপ (মাঝপথে না,
                // কারণ মাঝপথে ভয়েস মনোযোগ আরও ভাঙতে পারে — রোডম্যাপ সেকশন ৫.১) ──
                if (syncLossCount > 0) {
                    TtsManager.speak(
                        "তুমি এই সেশনে $syncLossCount বার টেক্সট ট্র্যাক হারিয়েছ। ধীরে টাইপ করো, একবারে কয়েকটা শব্দ পড়ে তারপর টাইপ করো।",
                        key = "typing_sync_tip"
                    )
                }
            }
        }
    }

    // ── প্রতিটা কী-প্রেস কেপচার করে target অক্ষরের সাথে সাথেই মিলিয়ে ঠিক/ভুল গণনা ──
    fun onInputChange(new: String) {
        if (isFinished) return
        if (!isStarted && new.isNotEmpty()) isStarted = true
        val capped = new.take(passage.length)
        if (capped.length > userInput.length) {
            for (i in userInput.length until capped.length) {
                totalKeystrokes++
                val isCorrect = i < passage.length && capped[i] == passage[i]
                if (isCorrect) correctKeystrokes++ else incorrectKeystrokes++
                // ── ধাপ ৪: এই ক্যারেক্টারটা target প্যাসেজে কোন হাতে পড়ে সেই অনুযায়ী গণনা ──
                if (i < passage.length && HandKeyMap.isTrackable(passage[i])) {
                    when (HandKeyMap.handOf(passage[i])) {
                        Hand.LEFT  -> if (isCorrect) leftCorrectChars++  else leftWrongChars++
                        Hand.RIGHT -> if (isCorrect) rightCorrectChars++ else rightWrongChars++
                    }
                }
            }
            // ── এই কী-প্রেসে কোনো শব্দ-বাউন্ডারি (স্পেস বা প্যাসেজের শেষ) পার হলে,
            // সেই শব্দটা target-এর সাথে মিলিয়ে mistake/correct হিসেবে Room-এ লগ হবে।
            // এটাই AI adaptive practice ও hand-balance analysis-এর ভিত্তি ডেটা তৈরি করে। ──
            for (span in wordSpans) {
                if (span.end > userInput.length && span.end <= capped.length && span.end !in loggedWordEnds) {
                    loggedWordEnds = loggedWordEnds + span.end
                    val typedWord = capped.substring(span.start, span.end)
                    if (typedWord != span.word) {
                        // ── ধরন B (sync-loss) real-time ধরা পড়লে হালকা ভাইব্রেশন —
                        // মাঝপথে ভয়েস না দেওয়ার কারণ: রোডম্যাপ সেকশন ৫.১ ──
                        if (TypingErrorAnalyzer.classify(span.word, typedWord) == MistakeErrorType.SYNC_LOSS) {
                            syncLossCount++
                            vibrator?.let { v ->
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    v.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION") v.vibrate(60)
                                }
                            }
                        }
                        scope.launch { TypingMistakeLogger.logMistake(ctx, span.word, typedWord, passageLang) }
                    } else {
                        scope.launch { TypingMistakeLogger.logCorrect(ctx, span.word, passageLang) }
                    }
                }
            }
        }
        userInput = capped
    }

    fun reset(newIndex: Int = passageIndex, pool: List<PassageInfo> = poolFor(selectedDifficulty)) {
        val idx = if (pool.isNotEmpty()) newIndex.mod(pool.size) else 0
        passageIndex = idx
        passage      = pool.getOrNull(idx)?.text ?: PASSAGES[0].text
        userInput    = ""
        isStarted    = false
        isFinished   = false
        elapsedSec   = 0
        result       = null
        correctKeystrokes   = 0
        incorrectKeystrokes = 0
        totalKeystrokes     = 0
        loggedWordEnds      = emptySet()
        leftCorrectChars  = 0
        leftWrongChars    = 0
        rightCorrectChars = 0
        rightWrongChars   = 0
        syncLossCount     = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⌨️ Typing Practice", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    // Best WPM badge — এখন আসলেই persist হয়ে সঠিক মান দেখায়
                    if (bestWpm > 0) {
                        Box(
                            Modifier.padding(end = 12.dp)
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("🏆 $bestWpm WPM", fontSize = 11.sp, color = GreenOk,
                                fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── ধাপ ৪: Daily Discipline Mode ব্যানার — শুধু মোড অন থাকলেই দেখা যায়,
            // কিছু আটকায় না, শুধু আজকের progress দেখায় (non-coercive) ──
            if (disciplineOn) {
                DailyGoalBanner(todaySeconds = todaySecondsBefore, goalMinutes = dailyGoalMin)
            }

            // Stats row
            StatsRow(
                elapsedSec        = elapsedSec,
                userInput         = userInput,
                passage           = passage,
                isStarted         = isStarted,
                correctKeystrokes = correctKeystrokes
            )

            // Passage display
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(Color(0xFFFAFAFF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E7FF))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📖 Passage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali)
                        val diff = poolFor(selectedDifficulty).getOrNull(passageIndex)?.difficulty
                            ?: PASSAGES.getOrNull(passageIndex)?.difficulty ?: "medium"
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(difficultyColor(diff).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(difficultyLabel(diff), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                color = difficultyColor(diff), fontFamily = NotoSansBengali)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Colored passage showing correct/wrong chars
                    Text(
                        buildAnnotatedString {
                            passage.forEachIndexed { i, ch ->
                                val style = when {
                                    i >= userInput.length -> SpanStyle(color = MaterialTheme.colorScheme.onSurface)
                                    userInput[i] == ch   -> SpanStyle(color = GreenOk, background = Color(0xFFDCFCE7))
                                    else                 -> SpanStyle(color = RedWrong, background = Color(0xFFFEE2E2))
                                }
                                withStyle(style) { append(ch) }
                            }
                        },
                        fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        lineHeight = 26.sp, letterSpacing = 0.3.sp
                    )
                }
            }

            // Input field
            OutlinedTextField(
                value         = userInput,
                onValueChange = { if (!isFinished) onInputChange(it) },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text(
                    if (!isStarted) "এখানে type করা শুরু করুন..." else "টাইপ চলছে...",
                    fontFamily = NotoSansBengali
                )},
                shape         = RoundedCornerShape(14.dp),
                enabled       = !isFinished,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo600,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.None
                ),
                minLines = 3
            )

            // Hint: timer starts on typing
            if (!isStarted) {
                Text("💡 Type করলেই Timer শুরু হবে",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            // Result card
            AnimatedVisibility(visible = isFinished && result != null) {
                result?.let { r ->
                    ResultCard(r, bestWpm,
                        onRetry = { reset() },
                        onNextPassage = {
                            val pool = poolFor(selectedDifficulty)
                            reset(passageIndex + 1, pool)
                        }
                    )
                }
            }

            // ── সাম্প্রতিক ফলাফল হিস্ট্রি — শুরু করার আগে দেখা যাবে ──
            if (!isStarted && history.isNotEmpty()) {
                TypingHistoryCard(history = history.take(5))
            }

            // Difficulty filter + Passage selector
            if (!isStarted) {
                Text("🎚️ কঠিনতার স্তর:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all", "easy", "medium", "hard").forEach { d ->
                        val selected = selectedDifficulty == d
                        Surface(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                            color    = if (selected) difficultyColor(d) else MaterialTheme.colorScheme.surfaceVariant,
                            onClick  = {
                                selectedDifficulty = d
                                reset(0, poolFor(d))
                            }
                        ) {
                            Text(
                                difficultyLabel(d), fontSize = 11.sp, fontFamily = NotoSansBengali,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("📝 Passage বেছে নিন:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                val pool = poolFor(selectedDifficulty)
                pool.forEachIndexed { i, p ->
                    OutlinedButton(
                        onClick = { reset(i, pool) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (passageIndex == i) Color(0xFFEEF2FF) else Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (passageIndex == i) Indigo600 else Color(0xFFE2E8F0)
                        )
                    ) {
                        Text(
                            "${i + 1}. ${p.text.take(40)}...",
                            fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = if (passageIndex == i) Indigo600 else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyGoalBanner(todaySeconds: Int, goalMinutes: Int) {
    val goalSeconds = (goalMinutes * 60).coerceAtLeast(1)
    val progress = (todaySeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
    val doneMin = todaySeconds / 60
    val reached = todaySeconds >= goalSeconds
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            if (reached) Color(0xFFECFDF5) else Color(0xFFFFFBEB)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (reached) GreenOk.copy(alpha = 0.4f) else AmberMid.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (reached) "🎯 আজকের লক্ষ্য পূরণ হয়েছে! ($doneMin/$goalMinutes মিনিট)"
                else "🎯 আজকে $doneMin/$goalMinutes মিনিট টাইপ করেছ",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                color = if (reached) GreenOk else Color(0xFFB45309)
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (reached) GreenOk else AmberMid,
                trackColor = (if (reached) GreenOk else AmberMid).copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun StatsRow(elapsedSec: Int, userInput: String, passage: String, isStarted: Boolean, correctKeystrokes: Int) {
    val mins = elapsedSec / 60
    val secs = elapsedSec % 60
    val progress = if (passage.isNotEmpty()) userInput.length.toFloat() / passage.length else 0f
    // লাইভ WPM এখন correct keystroke ভিত্তিক (৫-ক্যারেক্টার/word স্ট্যান্ডার্ড) — final হিসাবের
    // সাথে সামঞ্জস্যপূর্ণ, স্পেস-স্প্লিট word count-এর চেয়ে বেশি সঠিক অনুমান দেয়
    val liveWpm = if (elapsedSec > 0 && isStarted) {
        (correctKeystrokes / 5.0 / (elapsedSec / 60.0)).toInt()
    } else 0

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color(0xFF1E1B4B))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox("⏱️", "%02d:%02d".format(mins, secs), "সময়", Color(0xFF60A5FA))
                StatBox("⌨️", "$liveWpm", "WPM", Color(0xFF4ADE80))
                StatBox("📊", "${(progress * 100).toInt()}%", "Progress", Color(0xFFA78BFA))
                StatBox("📝", "${userInput.length}/${passage.length}", "অক্ষর", Color(0xFFFBBF24))
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Indigo600, trackColor = Color.White.copy(0.2f)
            )
        }
    }
}

@Composable
private fun StatBox(icon: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 14.sp)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

@Composable
private fun ResultCard(
    result       : TypingResult,
    bestWpm      : Int,
    onRetry      : () -> Unit,
    onNextPassage: () -> Unit
) {
    val isNewBest = result.wpm > bestWpm
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            if (isNewBest) Color(0xFF1E1B4B) else MaterialTheme.colorScheme.surface
        ),
        border = if (isNewBest) androidx.compose.foundation.BorderStroke(
            2.dp, Brush.horizontalGradient(listOf(Color(0xFFFBBF24), Color(0xFFF59E0B)))
        ) else null,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            if (isNewBest) {
                Text("🏆 নতুন Record!", fontSize = 14.sp, color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            }

            Text("${result.wpm} WPM",
                fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
                color = if (isNewBest) Color.White else Indigo600, fontFamily = NotoSansBengali)
            // Raw (ভুলসহ) WPM ছোট করে সাব-টেক্সটে — ইন্ডাস্ট্রি-স্ট্যান্ডার্ড টাইপিং সাইটগুলোর
            // মতোই Net (চূড়ান্ত) আর Raw (অপরিশোধিত) দুটোই দেখানো হয়
            Text("Raw ${result.rawWpm} WPM",
                fontSize = 11.sp, color = if (isNewBest) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotoSansBengali)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ResultStat("✅ Accuracy", "${result.accuracy}%",
                    if (result.accuracy >= 90) GreenOk else if (result.accuracy >= 70) Color(0xFFF59E0B) else RedWrong,
                    isNewBest)
                ResultStat("⏱️ সময়", "${result.timeSec}s", Color(0xFF60A5FA), isNewBest)
                ResultStat("📝 সঠিক", "${result.correctChars}/${result.totalChars}", GreenOk, isNewBest)
            }

            val grade = when {
                result.wpm >= 60 && result.accuracy >= 95 -> "S" to "⚡ Excellent!"
                result.wpm >= 40 && result.accuracy >= 90 -> "A" to "👍 Very Good!"
                result.wpm >= 25 && result.accuracy >= 80 -> "B" to "📈 Good!"
                result.wpm >= 15 -> "C" to "💪 Keep Practicing!"
                else -> "D" to "🔄 Try Again!"
            }
            Text("${grade.first} Grade — ${grade.second}", fontSize = 13.sp,
                color = if (isNewBest) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)

            // ── ধাপ ৪: হাত-ভিত্তিক ইনসাইট — যথেষ্ট ডেটা থাকলেই দেখানো হয় (নাহলে বিভ্রান্তিকর) ──
            val leftTotal  = result.leftCorrect + result.leftWrong
            val rightTotal = result.rightCorrect + result.rightWrong
            if (leftTotal >= 15 && rightTotal >= 15) {
                val leftErr  = result.leftWrong * 100 / leftTotal
                val rightErr = result.rightWrong * 100 / rightTotal
                val insight = when {
                    rightErr > leftErr + 5 -> "✋ এই সেশনে ডান হাতে ভুলের হার বেশি ($rightErr% বনাম $leftErr%)"
                    leftErr > rightErr + 5 -> "✋ এই সেশনে বাম হাতে ভুলের হার বেশি ($leftErr% বনাম $rightErr%)"
                    else -> null
                }
                insight?.let {
                    Text(it, fontSize = 11.sp, fontFamily = NotoSansBengali,
                        color = if (isNewBest) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.syncLossCount > 0) {
                Text("🔄 ${result.syncLossCount} বার টেক্সট ট্র্যাক হারিয়েছ — ধীরে টাইপ করার চেষ্টা করো",
                    fontSize = 11.sp, fontFamily = NotoSansBengali, color = AmberMid)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("🔄 আবার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
                Button(onClick = onNextPassage, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)) {
                    Text("➡️ পরের Passage", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, color: Color, dark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = if (dark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
    }
}

// ── সাম্প্রতিক সেশন হিস্ট্রি — শেষ ৫টা রেজাল্ট ছোট লিস্টে দেখায় (আগে এই ডেটা
// কোথাও সেভই হতো না, তাই কোনো হিস্ট্রি ছিল না) ──
@Composable
private fun TypingHistoryCard(history: List<TypingHistoryEntry>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E7FF))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📜 সাম্প্রতিক ফলাফল", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
            history.forEach { h ->
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(h.date, fontSize = 11.sp, fontFamily = NotoSansBengali,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${h.wpm} WPM", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                            color = Indigo600, fontFamily = NotoSansBengali)
                        Text("${h.accuracy}%", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (h.accuracy >= 90) GreenOk else if (h.accuracy >= 70) AmberMid else RedWrong,
                            fontFamily = NotoSansBengali)
                    }
                }
            }
        }
    }
}
