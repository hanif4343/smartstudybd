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
import com.hanif.smartstudy.util.TypingAdaptiveContentProvider
import com.hanif.smartstudy.util.TypingErrorAnalyzer
import com.hanif.smartstudy.util.TypingHistoryEntry
import com.hanif.smartstudy.util.TypingMistakeLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── AI Adaptive Session কনফিগ — দেখো SmartStudyBD-টাইপিং-অডিট-ও-রোডম্যাপ.md সেকশন ৮ ──
// প্রথম ধাপ (random, diagnostic) কত সেকেন্ড চলবে
private const val ADAPTIVE_PHASE1_SECONDS = 180
// এই সেকেন্ডে পৌঁছালে phase-2 এর AI/blended প্যাসেজ ব্যাকগ্রাউন্ডে ফেচ শুরু হবে
// (৬০ সেকেন্ড বাফার রেখে, যাতে phase ১ শেষ হওয়ার আগেই রেডি থাকে)
private const val ADAPTIVE_PHASE2_FETCH_TRIGGER_SECONDS = ADAPTIVE_PHASE1_SECONDS - 60

// ── BCC Exam Simulation Mode — বাস্তব বাংলাদেশ কম্পিউটার কাউন্সিল পরীক্ষার নিয়ম অনুযায়ী
// প্রতিটা ভাষায় ঠিক ১০ মিনিট (৬০০ সেকেন্ড) — দেখো রোডম্যাপ সেকশন ৮ ──
private const val EXAM_PHASE_SECONDS = 600

// ── Free/সাধারণ প্র্যাকটিস মোড — একটা প্যাসেজ (৭০-৮০ অক্ষর) সাধারণ স্পিডে
// মাত্র ১৫-২০ সেকেন্ডে শেষ হয়ে যায়, তাতে একটানা লেখার অনুশীলন হয় না। তাই এখন
// এই মোডেও adaptive/exam-এর মতোই — এক প্যাসেজ শেষ হলে পরেরটায় লুপ করে, যতক্ষণ
// না কমপক্ষে এই সময় (৫ মিনিট) পার হয়। এরপর যে প্যাসেজ চলছিল সেটা শেষ হলেই সেশন থামে ──
private const val FREE_MODE_MIN_SECONDS = 300

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

/** বাংলা নুক্তা-অক্ষর (ড়/ঢ়/য়) দুই রকম Unicode ফর্মে আসতে পারে — একক কোডপয়েন্ট, অথবা
 *  base+nukta দুই কোডপয়েন্টের যোগফল। দেখতে হুবহু একইরকম হলেও char-by-char তুলনায়
 *  এই দুই ফর্ম মিলত না, তাই কিবোর্ড আর প্যাসেজ টেক্সট আলাদা ফর্ম ব্যবহার করলে সঠিক
 *  টাইপ করা সত্ত্বেও ভুল ধরত। প্যাসেজ ও ইউজার-ইনপুট দুটোকেই NFC-তে normalize করে
 *  একই ফর্মে আনা হলো, যাতে তুলনাটা নির্ভরযোগ্য হয়। */
private fun normalizeBn(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

/** ড়/ঢ়/য়-এর মতো নুক্তা-অক্ষর Unicode-এর নিয়মেই composition-exclusion তালিকায় আছে —
 *  মানে NFC normalize করলেও এগুলো ভেঙে থাকে (base + ় দুই আলাদা ক্যারেক্টার হিসেবে)।
 *  এতে মান (value) মেলাতে সমস্যা নেই, কিন্তু রঙ করার সময় যদি base আর নুক্তাকে আলাদা
 *  স্টাইল-স্প্যানে ফেলা হয়, তাহলে টেক্সট শেপিং ভেঙে যায় আর নুক্তাটা বেস থেকে বিচ্ছিন্ন
 *  হয়ে একলা ডটেড-সার্কেল হিসেবে রেন্ডার হয়। তাই একটা visual অক্ষর (base + তার সাথে লেগে
 *  থাকা combining/nukta মার্ক) — পুরোটাকে একসাথে একটাই ইউনিট (grapheme cluster) ধরে
 *  একটাই স্টাইল দেওয়া হয়, যাতে শেপিং কখনো না ভাঙে। */
private fun graphemeClusters(s: String): List<IntRange> {
    if (s.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var start = 0
    for (i in 1..s.length) {
        val atEnd = i == s.length
        val isCombining = !atEnd && (
            Character.getType(s[i]) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(s[i]) == Character.COMBINING_SPACING_MARK.toInt()
        )
        if (!isCombining) {
            ranges.add(start until i)
            start = i
        }
    }
    return ranges
}

/** Adaptive Session-এ ভাষা মিশে না যাওয়ার জন্য — শুধু একটা ভাষার প্যাসেজ পুল */
private fun poolForLanguage(language: String): List<PassageInfo> =
    PASSAGES.filter { TypingErrorAnalyzer.detectLanguage(it.text) == language }.ifEmpty { PASSAGES }

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
    onResult  : (TypingResult) -> Unit = {},
    onOpenRace: () -> Unit = {},
    // ── Focus Mode কার্ড এখন এই স্ক্রিন থেকেও চালু করা যায় (আগে শুধু Study ট্যাব
    // থেকে করা যেত, যেটা অসামঞ্জস্যপূর্ণ ছিল)। MainScreen থেকে আসল Study
    // সাবজেক্টের তালিকা পাস করা হয় — টাইপিং নিজেই সবসময় প্রথম এন্ট্রি হিসেবে
    // যোগ হয় (SubjectListScreen.kt-এর একই প্যাটার্নে) ──
    focusStudySubjects: List<String> = emptyList()
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
    var weakWordDashboard by remember { mutableStateOf(listOf<String>()) }  // পুরনো/lifetime দুর্বল শব্দ, শুরুর আগে দেখানোর জন্য
    var lifetimeHandSummary by remember { mutableStateOf<Pair<Int, Int>?>(null) }  // (leftErr%, rightErr%) — যথেষ্ট ডেটা থাকলেই non-null
    LaunchedEffect(Unit) {
        bestWpm = session.getTypingBestWpm()
        history = session.getTypingHistory()
        val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
        weakWordDashboard = AppDatabase.getInstance(ctx).typingMistakeDao()
            .getTopWeakWords(userId, "bn", limit = 10).map { it.targetWord } +
            AppDatabase.getInstance(ctx).typingMistakeDao()
            .getTopWeakWords(userId, "en", limit = 5).map { it.targetWord }

        val hs = AppDatabase.getInstance(ctx).typingHandStatsDao().get(userId)
        lifetimeHandSummary = hs?.let {
            val leftTotal = it.leftCorrectChars + it.leftWrongChars
            val rightTotal = it.rightCorrectChars + it.rightWrongChars
            if (leftTotal < 100 || rightTotal < 100) null
            else (it.leftErrorRate() * 100).toInt() to (it.rightErrorRate() * 100).toInt()
        }
    }

    var selectedDifficulty by remember { mutableStateOf("all") }
    var passageIndex by remember { mutableStateOf(0) }
    var passage      by remember { mutableStateOf(normalizeBn(PASSAGES[0].text)) }
    var userInput    by remember { mutableStateOf("") }
    // ── প্রতিটা টাইপ করা ক্যারেক্টার প্যাসেজের কোন পজিশনের সাথে "মিলেছে" তার ম্যাপ —
    // (userInput-এর সমান সাইজ, প্যারালাল লিস্ট)। -1 মানে এই ক্যারেক্টারটা এক্সট্রা/বাড়তি
    // (যেমন ভুলবশত ডাবল স্পেস) — এটা প্যাসেজের কোনো পজিশন "খরচ" করে না, তাই এর পরের
    // টাইপিং আবার ঠিকভাবেই মিলবে, পুরো বাকি অংশ লাল হয়ে যাবে না (auto-resync) ──
    var typedTargetIndex by remember { mutableStateOf(listOf<Int>()) }
    var isStarted    by remember { mutableStateOf(false) }
    var isFinished   by remember { mutableStateOf(false) }
    var elapsedSec   by remember { mutableStateOf(0) }
    var result       by remember { mutableStateOf<TypingResult?>(null) }

    // ── AI Adaptive Session — "free" (স্বাভাবিক প্র্যাকটিস) বনাম "adaptive" (দুই-ধাপ) ──
    var sessionMode      by remember { mutableStateOf("free") }   // "free" | "adaptive"
    var sessionLanguage  by remember { mutableStateOf("bn") }     // adaptive মোডে ভাষা মিশবে না
    var adaptivePhase    by remember { mutableStateOf(1) }        // 1 | 2
    var sessionMistakeWords by remember { mutableStateOf(listOf<String>()) } // phase-১-এ session-local ভুল শব্দ
    var phase2Passage    by remember { mutableStateOf<String?>(null) }
    var phase2Source     by remember { mutableStateOf<String?>(null) }  // "cache" | "live_ai" | "fallback"
    var phase2Fetching   by remember { mutableStateOf(false) }
    var showPhaseTransition by remember { mutableStateOf(false) }

    // ── BCC Exam Simulation Mode ──
    var examPhase          by remember { mutableStateOf("en") }   // "en" | "bn"
    var examEnglishResult  by remember { mutableStateOf<TypingResult?>(null) }
    var examBanglaResult   by remember { mutableStateOf<TypingResult?>(null) }
    var showExamPhaseTransition by remember { mutableStateOf(false) }

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
        // ── এক্সট্রা/বাড়তি ক্যারেক্টার (যেমন ডাবল স্পেস) প্যাসেজের পজিশন খরচ করে না, তাই
        // "শেষ হয়েছে কিনা" এখন raw userInput.length দিয়ে না বরং কতটুকু প্যাসেজ আসলে
        // resolve হয়েছে তা দিয়ে ঠিক হয় ──
        val targetPos = (typedTargetIndex.lastOrNull { it >= 0 } ?: -1) + 1
        if (isStarted && targetPos >= passage.length && !isFinished) {

            // ── Adaptive Session — phase ১: পুরো প্যাসেজ শেষ হলেও সেশন শেষ না, পরের
            // random প্যাসেজে লুপ করবে (টাইমার/স্ট্যাটস চলতেই থাকবে), যতক্ষণ না
            // ADAPTIVE_PHASE1_SECONDS সময় পেরিয়ে যায় (সেটা নিচের আলাদা effect-এ চেক হয়) ──
            if (sessionMode == "adaptive" && adaptivePhase == 1) {
                val pool = poolForLanguage(sessionLanguage)
                val nextIdx = (passageIndex + 1).mod(pool.size.coerceAtLeast(1))
                passageIndex = nextIdx
                passage      = normalizeBn(pool.getOrNull(nextIdx)?.text ?: passage)
                userInput    = ""
                typedTargetIndex = emptyList()
                loggedWordEnds = emptySet()
                return@LaunchedEffect
            }

            // ── Exam Simulation — পুরো প্যাসেজ শেষ হলেও সময়-বাজেট (EXAM_PHASE_SECONDS)
            // শেষ না হওয়া পর্যন্ত একই ভাষার পরের random প্যাসেজে লুপ করে (সময়-ভিত্তিক
            // ইতি নিচের আলাদা effect-এ হ্যান্ডল হয়) ──
            if (sessionMode == "exam") {
                val pool = poolForLanguage(examPhase)
                val nextIdx = (passageIndex + 1).mod(pool.size.coerceAtLeast(1))
                passageIndex = nextIdx
                passage      = normalizeBn(pool.getOrNull(nextIdx)?.text ?: passage)
                userInput    = ""
                typedTargetIndex = emptyList()
                loggedWordEnds = emptySet()
                return@LaunchedEffect
            }

            // ── Free/সাধারণ প্র্যাকটিস মোড — একটা প্যাসেজ শেষ হলেও কমপক্ষে
            // FREE_MODE_MIN_SECONDS (৫ মিনিট) পার না হওয়া পর্যন্ত একই ডিফিকাল্টির
            // পরের প্যাসেজে লুপ করে, যাতে একটানা লেখার প্র্যাকটিস হয় ──
            if (sessionMode == "free" && elapsedSec < FREE_MODE_MIN_SECONDS) {
                val pool = poolFor(selectedDifficulty)
                val nextIdx = (passageIndex + 1).mod(pool.size.coerceAtLeast(1))
                passageIndex = nextIdx
                passage      = normalizeBn(pool.getOrNull(nextIdx)?.text ?: passage)
                userInput    = ""
                typedTargetIndex = emptyList()
                loggedWordEnds = emptySet()
                return@LaunchedEffect
            }

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

                // ── দুর্বল-শব্দ ড্যাশবোর্ড রিফ্রেশ (পরের সেশনের আগে আপডেটেড দেখাতে) ──
                weakWordDashboard = AppDatabase.getInstance(ctx).typingMistakeDao()
                    .getTopWeakWords(userId, "bn", limit = 10).map { it.targetWord } +
                    AppDatabase.getInstance(ctx).typingMistakeDao()
                    .getTopWeakWords(userId, "en", limit = 5).map { it.targetWord }
            }
        }
    }

    // ── Adaptive Session — phase ১-এর সময়-বাজেট নিয়ন্ত্রণ: নির্দিষ্ট সময়ে phase-২ এর
    // প্যাসেজ ব্যাকগ্রাউন্ডে ফেচ শুরু, এবং পুরো বাজেট শেষ হলে জোর করেই (মাঝ-প্যাসেজেও)
    // transition-এ পাঠানো — দেখো রোডম্যাপ সেকশন ৮.১ ──
    LaunchedEffect(elapsedSec, sessionMode, adaptivePhase, isStarted, isFinished) {
        if (sessionMode != "adaptive" || adaptivePhase != 1 || !isStarted || isFinished) return@LaunchedEffect

        if (elapsedSec >= ADAPTIVE_PHASE2_FETCH_TRIGGER_SECONDS && phase2Passage == null && !phase2Fetching) {
            phase2Fetching = true
            scope.launch {
                val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
                val weak = sessionMistakeWords.distinct().take(10).ifEmpty {
                    AppDatabase.getInstance(ctx).typingMistakeDao()
                        .getTopWeakWords(userId, sessionLanguage, limit = 10).map { it.targetWord }
                }
                // ── lifetime হাত-ভিত্তিক error-rate থেকে বের করা কোন হাত (থাকলে) দুর্বল —
                // পার্থক্য কম হলে (< ৫%) কোনো bias না দেওয়াই ভালো, ResultCard-এর threshold-এর
                // সাথে সামঞ্জস্যপূর্ণ ──
                val handStats = AppDatabase.getInstance(ctx).typingHandStatsDao().get(userId)
                val weakHand = handStats?.let { hs ->
                    val leftTotal = hs.leftCorrectChars + hs.leftWrongChars
                    val rightTotal = hs.rightCorrectChars + hs.rightWrongChars
                    if (leftTotal < 100 || rightTotal < 100) return@let null  // যথেষ্ট ডেটা নেই
                    val leftErr = hs.leftErrorRate(); val rightErr = hs.rightErrorRate()
                    when {
                        rightErr > leftErr + 0.05f -> "right"
                        leftErr > rightErr + 0.05f -> "left"
                        else -> null
                    }
                }
                val res = TypingAdaptiveContentProvider.getBlendedPassage(ctx, weak, sessionLanguage, "medium", weakHand)
                phase2Passage = res.passage
                phase2Source = when (res.source) {
                    TypingAdaptiveContentProvider.Source.Cache    -> "cache"
                    TypingAdaptiveContentProvider.Source.LiveAi   -> "live_ai"
                    TypingAdaptiveContentProvider.Source.Fallback -> "fallback"
                }
                phase2Fetching = false
            }
        }

        if (elapsedSec >= ADAPTIVE_PHASE1_SECONDS) {
            isFinished = true
            showPhaseTransition = true
        }
    }

    // ── Exam Simulation — সময়-বাজেট নিয়ন্ত্রণ: EXAM_PHASE_SECONDS (১০ মিনিট) শেষ হলে
    // মাঝ-প্যাসেজেও জোর করে থামিয়ে দেয় — ঠিক বাস্তব পরীক্ষার মতো (দেখো রোডম্যাপ সেকশন ৪ —
    // "১০ মিনিট শেষ হওয়ার পর সফটওয়্যার স্বয়ংক্রিয়ভাবে বন্ধ হয়ে যায়") ──
    LaunchedEffect(elapsedSec, sessionMode, examPhase, isStarted, isFinished) {
        if (sessionMode != "exam" || !isStarted || isFinished) return@LaunchedEffect
        if (elapsedSec < EXAM_PHASE_SECONDS) return@LaunchedEffect

        isFinished = true
        val timeSec = elapsedSec.coerceAtLeast(1)
        val minutes = timeSec / 60.0
        val rawWpm = if (minutes > 0) (totalKeystrokes / 5.0 / minutes).toInt() else 0
        val netWpm = if (minutes > 0) (correctKeystrokes / 5.0 / minutes).toInt().coerceAtLeast(0) else 0
        val acc = if (totalKeystrokes > 0) (correctKeystrokes * 100 / totalKeystrokes) else 100
        val phaseResult = TypingResult(
            wpm = netWpm, rawWpm = rawWpm, accuracy = acc, timeSec = timeSec,
            correctChars = correctKeystrokes, totalChars = totalKeystrokes,
            leftCorrect = leftCorrectChars, leftWrong = leftWrongChars,
            rightCorrect = rightCorrectChars, rightWrong = rightWrongChars,
            syncLossCount = syncLossCount
        )

        if (examPhase == "en") {
            examEnglishResult = phaseResult
            showExamPhaseTransition = true
        } else {
            examBanglaResult = phaseResult
            scope.launch {
                val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
                // ── দুই ভাষা মিলিয়ে হাত-ভিত্তিক ডেটা persist — এই ফেজেরটুকু (বাংলা) যোগ হচ্ছে,
                // ইংরেজি ফেজেরটা আগেই স্বাভাবিক char-লুপ দিয়ে গণনা হয়েছিল কিন্তু persist হয়নি,
                // তাই এখানে দুটো ফেজের সম্মিলিত হাত-ডেটা একসাথে যোগ করা হলো ──
                AppDatabase.getInstance(ctx).typingHandStatsDao().addSessionDelta(
                    userId       = userId,
                    leftCorrect  = leftCorrectChars.toLong(),
                    leftWrong    = leftWrongChars.toLong(),
                    rightCorrect = rightCorrectChars.toLong(),
                    rightWrong   = rightWrongChars.toLong()
                )
                session.addTypingSecondsToday(timeSec)
                todaySecondsBefore = session.getTypingTodaySeconds()
            }
        }
    }

    // ── প্রতিটা কী-প্রেস কেপচার করে target অক্ষরের সাথে সাথেই মিলিয়ে ঠিক/ভুল গণনা ──
    // (আগে টাইপ করা ইনডেক্স আর প্যাসেজ ইনডেক্স সবসময় সমান ধরে নেওয়া হতো, তাই ভুলবশত
    // একটা এক্সট্রা ক্যারেক্টার — যেমন ডাবল স্পেস — ঢুকে গেলে তার পরের পুরো অংশ শিফট
    // হয়ে গিয়ে ভুলভাবে লাল দেখাত। এখন প্রতিটা টাইপ করা ক্যারেক্টার কোন প্যাসেজ-পজিশনের
    // সাথে মিলল তা আলাদাভাবে ট্র্যাক করা হয় (typedTargetIndex), তাই একটা এক্সট্রা
    // স্পেস শুধু ওই একটা কী-প্রেসকেই ভুল ধরে, প্যাসেজ-পয়েন্টার এগোয় না, ফলে পরের
    // টাইপিং আবার ঠিক পজিশনের সাথেই মিলে যায় — auto-resync) ──
    fun onInputChange(new: String) {
        if (isFinished) return
        if (!isStarted && new.isNotEmpty()) isStarted = true
        val normalized = normalizeBn(new)

        var target = (typedTargetIndex.lastOrNull { it >= 0 } ?: -1) + 1
        val oldTargetPos = target
        var newTypedTargetIndex = typedTargetIndex
        var consumedUpto = userInput.length

        if (normalized.length > userInput.length) {
            for (i in userInput.length until normalized.length) {
                if (target >= passage.length) break   // প্যাসেজ শেষ — বাড়তি ক্যারেক্টার আর গোনা হবে না
                val ch = normalized[i]
                totalKeystrokes++
                when {
                    ch == passage[target] -> {
                        correctKeystrokes++
                        if (HandKeyMap.isTrackable(passage[target])) {
                            when (HandKeyMap.handOf(passage[target])) {
                                Hand.LEFT  -> leftCorrectChars++
                                Hand.RIGHT -> rightCorrectChars++
                            }
                        }
                        newTypedTargetIndex = newTypedTargetIndex + target
                        target++
                    }
                    ch == ' ' && passage[target] != ' ' -> {
                        // ── এক্সট্রা/বাড়তি স্পেস (যেমন ডাবল স্পেস) — শুধু এই কী-প্রেসটাই
                        // ভুল ধরবে, target পজিশন এগোবে না, তাই পরের ক্যারেক্টার আবার
                        // সঠিক পজিশনের সাথেই তুলনা হবে ──
                        incorrectKeystrokes++
                        newTypedTargetIndex = newTypedTargetIndex + -1
                    }
                    else -> {
                        incorrectKeystrokes++
                        if (HandKeyMap.isTrackable(passage[target])) {
                            when (HandKeyMap.handOf(passage[target])) {
                                Hand.LEFT  -> leftWrongChars++
                                Hand.RIGHT -> rightWrongChars++
                            }
                        }
                        newTypedTargetIndex = newTypedTargetIndex + target
                        target++
                    }
                }
                consumedUpto = i + 1
            }

            val capped = normalized.take(consumedUpto)

            // ── এই কী-প্রেসে কোনো শব্দ-বাউন্ডারি (স্পেস বা প্যাসেজের শেষ) পার হলে,
            // সেই শব্দটা target-এর সাথে মিলিয়ে mistake/correct হিসেবে Room-এ লগ হবে।
            // এটাই AI adaptive practice ও hand-balance analysis-এর ভিত্তি ডেটা তৈরি করে।
            // (typedWord এখন typedTargetIndex ম্যাপ থেকে বের করা হয়, কারণ এক্সট্রা
            // ক্যারেক্টার থাকলে capped-এর ইনডেক্স আর span-এর প্যাসেজ-ইনডেক্স আর সমান না) ──
            for (span in wordSpans) {
                if (span.end > oldTargetPos && span.end <= target && span.end !in loggedWordEnds) {
                    loggedWordEnds = loggedWordEnds + span.end
                    val typedWord = buildString {
                        for (idx in newTypedTargetIndex.indices) {
                            val t = newTypedTargetIndex[idx]
                            if (t in span.start until span.end) append(capped[idx])
                        }
                    }
                    if (typedWord != span.word) {
                        // ── Adaptive Session phase-১: এই সেশনের ভুল শব্দ জমা রাখা (দুর্বল-শব্দ
                        // ভিত্তিক AI প্যাসেজ বানানোর ইনপুট — sync-loss বাদে, কারণ সেটা বানান-ভুল না) ──
                        val errType = TypingErrorAnalyzer.classify(span.word, typedWord)
                        if (sessionMode == "adaptive" && adaptivePhase == 1 && errType != MistakeErrorType.SYNC_LOSS) {
                            sessionMistakeWords = sessionMistakeWords + span.word
                        }
                        // ── ধরন B (sync-loss) real-time ধরা পড়লে হালকা ভাইব্রেশন —
                        // মাঝপথে ভয়েস না দেওয়ার কারণ: রোডম্যাপ সেকশন ৫.১ ──
                        if (errType == MistakeErrorType.SYNC_LOSS) {
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

            typedTargetIndex = newTypedTargetIndex
            userInput = capped
        } else {
            // ── ব্যাকস্পেস/ডিলিট — typedTargetIndex-ও নতুন দৈর্ঘ্যে ছেঁটে সিঙ্কে রাখা হচ্ছে ──
            typedTargetIndex = typedTargetIndex.take(normalized.length)
            userInput = normalized
        }
    }

    fun reset(newIndex: Int = passageIndex, pool: List<PassageInfo> = poolFor(selectedDifficulty)) {
        val idx = if (pool.isNotEmpty()) newIndex.mod(pool.size) else 0
        passageIndex = idx
        passage      = normalizeBn(pool.getOrNull(idx)?.text ?: PASSAGES[0].text)
        userInput    = ""
        typedTargetIndex = emptyList()
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
        sessionMode   = "free"
        adaptivePhase = 1
        showPhaseTransition = false
        examPhase = "en"
        examEnglishResult = null
        examBanglaResult  = null
        showExamPhaseTransition = false
        // adaptive সেশন-স্টেট এখানে ইচ্ছাকৃতভাবে রিসেট করা হয়নি — free practice-এ ফিরে
        // গেলে sessionMode="free" আলাদাভাবে সেট করে দিলেই যথেষ্ট (নিচের startAdaptiveSession দেখো)
    }

    /** "🎯 AI Adaptive Session" বাটনে ট্যাপ করলে কল হয় — ভাষা অনুযায়ী পুল থেকে
     *  একটা random প্যাসেজ দিয়ে phase-১ শুরু করে সব adaptive-state রিসেট করে */
    fun startAdaptiveSession(language: String) {
        sessionMode  = "adaptive"
        adaptivePhase = 1
        sessionLanguage = language
        sessionMistakeWords = emptyList()
        phase2Passage  = null
        phase2Source   = null
        phase2Fetching = false
        showPhaseTransition = false
        val pool = poolForLanguage(language)
        passageIndex = 0
        passage      = normalizeBn(pool.firstOrNull()?.text ?: PASSAGES[0].text)
        userInput = ""; typedTargetIndex = emptyList(); isStarted = false; isFinished = false; elapsedSec = 0; result = null
        correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0; loggedWordEnds = emptySet()
        leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0; syncLossCount = 0
    }

    /** "🏛️ BCC Exam Simulation" বাটনে ট্যাপ করলে কল হয় — ইংরেজি ফেজ দিয়ে শুরু */
    fun startExamSimulation() {
        sessionMode = "exam"
        examPhase = "en"
        examEnglishResult = null
        examBanglaResult  = null
        showExamPhaseTransition = false
        val pool = poolForLanguage("en")
        passageIndex = 0
        passage      = normalizeBn(pool.firstOrNull()?.text ?: PASSAGES[0].text)
        userInput = ""; typedTargetIndex = emptyList(); isStarted = false; isFinished = false; elapsedSec = 0; result = null
        correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0; loggedWordEnds = emptySet()
        leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0; syncLossCount = 0
    }

    /** ইংরেজি ফেজ শেষে ট্রানজিশন কার্ডের বাটনে ট্যাপ করলে কল হয় — বাংলা ফেজ শুরু,
     *  সব কাউন্টার ফ্রেশ (প্রতিটা ফেজের নিজের আলাদা, স্বাধীন WPM/accuracy হবে) */
    fun startExamBanglaPhase() {
        examPhase = "bn"
        showExamPhaseTransition = false
        val pool = poolForLanguage("bn")
        passageIndex = 0
        passage      = normalizeBn(pool.firstOrNull()?.text ?: PASSAGES[0].text)
        userInput = ""; typedTargetIndex = emptyList(); isStarted = false; isFinished = false; elapsedSec = 0
        correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0; loggedWordEnds = emptySet()
        leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0; syncLossCount = 0
    }


    /** ট্রানজিশন কার্ডের CTA বাটনে ট্যাপ করলে কল হয় — phase-২ শুরু, টাইমার/স্ট্যাটস
     *  চলতেই থাকে (আলাদা রিসেট হয় না) যাতে ফাইনাল রেজাল্ট পুরো সেশনের সমন্বিত হয় */
    fun startPhase2() {
        adaptivePhase = 2
        passage = normalizeBn(phase2Passage ?: fallbackPassageFor(sessionLanguage))
        userInput = ""; typedTargetIndex = emptyList()
        loggedWordEnds = emptySet()
        isFinished = false
        showPhaseTransition = false
        // isStarted/elapsedSec/correctKeystrokes ইত্যাদি ইচ্ছাকৃতভাবে অপরিবর্তিত —
        // পুরো adaptive session-এর একটাই সমন্বিত ফাইনাল রেজাল্ট হবে
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

            // ── ফোকাস মোড: SubjectListScreen.kt-এর "🎯 আজ ফোকাস" কার্ডের সেম —
            // এখন Study ট্যাবের পাশাপাশি টাইপিং স্ক্রিন থেকেও চালু করা যায় ──
            if (com.hanif.smartstudy.focus.FocusModeConfig.ENABLED) {
                com.hanif.smartstudy.focus.FocusTodayCard(
                    subjects = listOf(com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT) + focusStudySubjects
                )
            }

            // ── ধাপ ৪: Daily Discipline Mode ব্যানার — শুধু মোড অন থাকলেই দেখা যায়,
            // কিছু আটকায় না, শুধু আজকের progress দেখায় (non-coercive) ──
            if (disciplineOn) {
                DailyGoalBanner(todaySeconds = todaySecondsBefore, goalMinutes = dailyGoalMin)
            }

            // Stats row
            StatsRow(
                elapsedSec        = elapsedSec,
                resolvedCount     = (typedTargetIndex.lastOrNull { it >= 0 } ?: -1) + 1,
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
                        Text(
                            if (sessionMode == "adaptive" && adaptivePhase == 2) "🎯 তোমার জন্য বিশেষভাবে তৈরি" else "📖 Passage",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (sessionMode == "adaptive" && adaptivePhase == 2 && phase2Source != null) {
                                val srcLabel = when (phase2Source) {
                                    "live_ai" -> "✨ AI"
                                    "cache"   -> "♻️ Cache"
                                    else      -> "📚 Fallback"
                                }
                                Text(srcLabel, fontSize = 9.sp, fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                    }
                    Spacer(Modifier.height(8.dp))

                    // Colored passage showing correct/wrong chars + current-word হাইলাইট
                    // (selftyping.com-এর মতো — এখন কোন শব্দে আছি সেটা স্পষ্ট বোঝা যায়,
                    // দেখো রোডম্যাপ সেকশন ৪-এর "sync-loss/ধরন B" আলোচনা)
                    // ── প্যাসেজের প্রতিটা পজিশন আসলে "resolve" হয়েছে কিনা এবং ঠিক/ভুল —
                    // এটা এখন raw userInput index দিয়ে না, typedTargetIndex ম্যাপ দিয়ে বের হয়,
                    // তাই এক্সট্রা ক্যারেক্টার (যেমন ডাবল স্পেস) থাকলেও পরের অংশ ভুলভাবে
                    // লাল দেখায় না ──
                    val targetPos = remember(typedTargetIndex) {
                        (typedTargetIndex.lastOrNull { it >= 0 } ?: -1) + 1
                    }
                    val resolvedCorrect = remember(typedTargetIndex, userInput, passage) {
                        val map = HashMap<Int, Boolean>()
                        for (idx in typedTargetIndex.indices) {
                            val t = typedTargetIndex[idx]
                            if (t >= 0 && idx < userInput.length && t < passage.length) {
                                map[t] = userInput[idx] == passage[t]
                            }
                        }
                        map
                    }
                    val currentWordSpan = remember(wordSpans, targetPos) {
                        wordSpans.firstOrNull { targetPos in it.start..it.end }
                    }
                    val passageClusters = remember(passage) { graphemeClusters(passage) }
                    Text(
                        buildAnnotatedString {
                            for (cluster in passageClusters) {
                                val cStart = cluster.first
                                val cEndExclusive = cluster.last + 1
                                val inCurrentWord = currentWordSpan != null && cStart >= currentWordSpan.start && cStart < currentWordSpan.end
                                val style = when {
                                    // পুরো cluster এখনো টাইপ হয়নি (আংশিক টাইপ হলেও পুরোটাই
                                    // "pending" রাখা হচ্ছে, যাতে base + নুক্তা কখনো আলাদা
                                    // স্টাইলে না পড়ে আর ডটেড-সার্কেল না দেখায়)
                                    targetPos < cEndExclusive -> SpanStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        background = if (inCurrentWord) Color(0xFFDBEAFE) else Color.Unspecified
                                    )
                                    cluster.all { resolvedCorrect[it] == true } -> SpanStyle(color = GreenOk, background = Color(0xFFDCFCE7))
                                    else -> SpanStyle(color = RedWrong, background = Color(0xFFFEE2E2))
                                }
                                withStyle(style) { append(passage.substring(cStart, cEndExclusive)) }
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
                    imeAction = ImeAction.None,
                    autoCorrect = false
                ),
                minLines = 3
            )

            // Hint: timer starts on typing
            if (!isStarted) {
                Text("💡 Type করলেই Timer শুরু হবে",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            // ── Adaptive Session — phase ১→২ ট্রানজিশন সামারি ──
            AnimatedVisibility(visible = showPhaseTransition) {
                val weakNow = sessionMistakeWords.distinct().take(8)
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Indigo600.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊 প্রথম ধাপ শেষ!", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali, color = Indigo600)
                        if (weakNow.isNotEmpty()) {
                            Text("এই শব্দগুলোয় ভুল হয়েছে:", fontSize = 12.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(weakNow.joinToString("  •  "), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        } else {
                            Text("এই ধাপে তেমন ভুল হয়নি — চমৎকার! এবার একটু কঠিন কন্টেন্টে যাই।",
                                fontSize = 12.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        if (phase2Fetching && phase2Passage == null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Indigo600)
                                Text("তোমার জন্য প্যাসেজ তৈরি হচ্ছে...", fontSize = 11.sp,
                                    fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            onClick = { startPhase2() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                        ) {
                            Text(
                                if (weakNow.isNotEmpty()) "🎯 এই ভুলগুলো ঠিক করতে পরের রাউন্ডে যাই"
                                else "➡️ পরের রাউন্ডে যাই",
                                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, fontFamily = NotoSansBengali
                            )
                        }
                    }
                }
            }

            // ── Exam Simulation — ইংরেজি ফেজ শেষে ট্রানজিশন ──
            AnimatedVisibility(visible = showExamPhaseTransition) {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7C3AED).copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅ ইংরেজি ধাপ শেষ!", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali, color = Color(0xFF6D28D9))
                        examEnglishResult?.let { er ->
                            Text("${er.wpm} WPM  •  ${er.accuracy}% নির্ভুলতা", fontSize = 13.sp,
                                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("এবার বাংলা ধাপ — বিজয় লেআউটে আছ কিনা আরেকবার দেখে নাও।", fontSize = 12.sp,
                            fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Button(
                            onClick = { startExamBanglaPhase() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            Text("🔤 বাংলা ধাপ শুরু করি", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, fontFamily = NotoSansBengali)
                        }
                    }
                }
            }

            // ── Exam Simulation — ফাইনাল রেজাল্ট (দুই ভাষার আলাদা ব্লক) ──
            AnimatedVisibility(visible = sessionMode == "exam" && examBanglaResult != null) {
                examEnglishResult?.let { er -> examBanglaResult?.let { br ->
                    ExamResultCard(englishResult = er, banglaResult = br, onRestart = { reset() })
                } }
            }

            // Result card
            AnimatedVisibility(visible = isFinished && result != null && !showPhaseTransition && sessionMode != "exam") {
                result?.let { r ->
                    ResultCard(r, bestWpm,
                        sessionMistakeWords = sessionMistakeWords.distinct(),
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
                // ── AI Adaptive Session মোড-সিলেক্টর ──
                Text("🎯 অনুশীলনের ধরন:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "free") Indigo600 else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { sessionMode = "free"; adaptivePhase = 1 }
                    ) {
                        Text("✍️ ফ্রি প্র্যাকটিস", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "free") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "adaptive") Indigo600 else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { startAdaptiveSession(sessionLanguage) }
                    ) {
                        Text("🎯 AI Adaptive", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "adaptive") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "exam") Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { startExamSimulation() }
                    ) {
                        Text("🏛️ BCC Exam", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "exam") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                    }
                }

                if (sessionMode == "exam") {
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🏛️ বাস্তব BCC পরীক্ষার নিয়ম অনুযায়ী", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                fontFamily = NotoSansBengali, color = Color(0xFF6D28D9))
                            Text(
                                "⌨️ কীবোর্ড বিজয় লেআউটে বদলে নাও (পরীক্ষায় অভ্র চলে না)\n" +
                                "🔤 প্রথমে ইংরেজি ১০ মিনিট, তারপর বাংলা ১০ মিনিট — কোনো spell-check সাহায্য নেই\n" +
                                "🎯 গতির চেয়ে নির্ভুলতা বেশি গুরুত্বপূর্ণ",
                                fontSize = 10.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp
                            )
                        }
                    }
                }

                if (sessionMode == "adaptive") {
                    Text(
                        "প্রথম ৩ মিনিট random প্যাসেজ, তারপর তোমার ভুল-শব্দ দিয়ে AI বানানো প্যাসেজ — একটাই সমন্বিত ফলাফল।",
                        fontSize = 10.sp, fontFamily = NotoSansBengali,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("bn" to "বাংলা", "en" to "English").forEach { (code, label) ->
                            OutlinedButton(
                                onClick = { startAdaptiveSession(code) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (sessionLanguage == code) Color(0xFFEEF2FF) else Color.Transparent
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, if (sessionLanguage == code) Indigo600 else Color(0xFFE2E8F0)
                                )
                            ) { Text(label, fontFamily = NotoSansBengali, fontSize = 12.sp) }
                        }
                    }
                }

                // ── দুর্বল-শব্দ ড্যাশবোর্ড — আগের সেশনগুলো থেকে জমা হওয়া সবচেয়ে বেশি ভুল হওয়া শব্দ ──
                if (weakWordDashboard.isNotEmpty()) {
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberMid.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📌 তোমার দুর্বল শব্দ", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                fontFamily = NotoSansBengali, color = Color(0xFFB45309))
                            Text(weakWordDashboard.take(10).joinToString("  •  "), fontSize = 12.sp,
                                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // ── লাইফটাইম হাত-ভিত্তিক দুর্বলতা — যথেষ্ট ডেটা জমলেই দেখা যাবে ──
                lifetimeHandSummary?.let { (leftErr, rightErr) ->
                    val insight = when {
                        rightErr > leftErr + 5 -> "✋ তোমার সবসময়ই ডান হাতে ভুলের হার বেশি ($rightErr% বনাম $leftErr%) — AI প্র্যাকটিসে এখন এটা মাথায় রেখে শব্দ বাছা হবে।"
                        leftErr > rightErr + 5 -> "✋ তোমার সবসময়ই বাম হাতে ভুলের হার বেশি ($leftErr% বনাম $rightErr%) — AI প্র্যাকটিসে এখন এটা মাথায় রেখে শব্দ বাছা হবে।"
                        else -> null
                    }
                    insight?.let {
                        Card(
                            Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Indigo600.copy(alpha = 0.25f))
                        ) {
                            Text(it, Modifier.padding(12.dp), fontSize = 12.sp,
                                fontFamily = NotoSansBengali, color = Indigo600)
                        }
                    }
                }

                TextButton(onClick = onOpenRace, modifier = Modifier.fillMaxWidth()) {
                    Text("🏁 বন্ধুর সাথে টাইপিং রেস দাও →", fontFamily = NotoSansBengali,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                }

                if (sessionMode == "free") {
                    Spacer(Modifier.height(2.dp))
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
private fun StatsRow(elapsedSec: Int, resolvedCount: Int, passage: String, isStarted: Boolean, correctKeystrokes: Int) {
    val mins = elapsedSec / 60
    val secs = elapsedSec % 60
    val progress = if (passage.isNotEmpty()) resolvedCount.toFloat() / passage.length else 0f
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
                StatBox("📝", "$resolvedCount/${passage.length}", "অক্ষর", Color(0xFFFBBF24))
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
    sessionMistakeWords: List<String> = emptyList(),
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

            // ── এই সেশনে ভুল হওয়া শব্দের তালিকা — আগে ডেটা জমলেও কোথাও দেখানো হতো না,
            // এটাই সেই মিসিং পিস (দেখো রোডম্যাপ সেকশন ১০) ──
            if (sessionMistakeWords.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📌 এই সেশনে ভুল হওয়া শব্দ", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            fontFamily = NotoSansBengali, color = Color(0xFFB45309))
                        Text(sessionMistakeWords.take(10).joinToString("  •  "), fontSize = 12.sp,
                            fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            val shareCtx = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("🔄 আবার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = { com.hanif.smartstudy.util.ResultShareUtil.shareTyping(shareCtx, result, isNewBest) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📤 শেয়ার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
            Button(onClick = onNextPassage, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)) {
                Text("➡️ পরের Passage", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ExamResultCard(englishResult: TypingResult, banglaResult: TypingResult, onRestart: () -> Unit) {
    val shareCtx = androidx.compose.ui.platform.LocalContext.current
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7C3AED).copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("🏛️ BCC Exam Simulation — ফলাফল", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali, color = Color(0xFF6D28D9),
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("🔤 ইংরেজি" to englishResult, "🅱️ বাংলা" to banglaResult).forEach { (label, r) ->
                    Card(
                        Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                            Text("${r.wpm}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C3AED))
                            Text("WPM", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${r.accuracy}% নির্ভুলতা", fontSize = 11.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            val overallPass = englishResult.wpm >= 20 && banglaResult.wpm >= 20 &&
                englishResult.accuracy >= 80 && banglaResult.accuracy >= 80
            Text(
                if (overallPass) "✅ দুটো ধাপেই সাধারণ BCC বেঞ্চমার্ক (~২০ WPM, ৮০%+ নির্ভুলতা) পূরণ হয়েছে"
                else "💪 আরও প্র্যাকটিস দরকার — টার্গেট: প্রতিটা ভাষায় অন্তত ২০ WPM ও ৮০%+ নির্ভুলতা",
                fontSize = 11.sp, fontFamily = NotoSansBengali,
                color = if (overallPass) GreenOk else AmberMid,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("🔄 আবার দাও", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = {
                        // ── শেয়ার কার্ড বাংলা ফেজের ফলাফল দিয়ে বানানো হচ্ছে (একটাই কার্ড লাগে) ──
                        com.hanif.smartstudy.util.ResultShareUtil.shareTyping(shareCtx, banglaResult, false)
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📤 শেয়ার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
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
