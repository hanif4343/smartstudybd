package com.hanif.smartstudy.ui.typing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.CustomPassageEntity
import com.hanif.smartstudy.data.local.MistakeErrorType
import com.hanif.smartstudy.data.local.TypingKeyStatEntity
import com.hanif.smartstudy.data.local.toEntity
import com.hanif.smartstudy.data.model.BijoyCurriculum
import com.hanif.smartstudy.data.remote.TypingCloudSyncService
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.CurriculumProvider
import com.hanif.smartstudy.util.Hand
import com.hanif.smartstudy.util.HandKeyMap
import com.hanif.smartstudy.util.RoadmapPlan
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.SpeedRankUtil
import com.hanif.smartstudy.util.TtsManager
import com.hanif.smartstudy.util.TypingAdaptiveContentProvider
import com.hanif.smartstudy.util.TypingErrorAnalyzer
import com.hanif.smartstudy.util.TypingHistoryEntry
import com.hanif.smartstudy.util.TypingKeySound
import com.hanif.smartstudy.util.TypingKeyStatStore
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
private val AmberWarn = Color(0xFFB45309)  // স্পেস-মিস হওয়া শব্দ/অক্ষরের জন্য — লাল থেকে আলাদা রঙ, যাতে বানান-ভুল আর স্পেস-ভুল গুলিয়ে না যায়
private val AmberMid  = Color(0xFFF59E0B)
// SlateText -> MaterialTheme.colorScheme.onSurface
// MutedText -> MaterialTheme.colorScheme.onSurfaceVariant
// CardBg -> MaterialTheme.colorScheme.surface

// ── Passage + difficulty ট্যাগ — "easy" | "medium" | "hard" | "custom" | "all" ──
data class PassageInfo(val text: String, val difficulty: String)

// ── আগে এখানে হার্ডকোডেড প্যাসেজের একটা fixed তালিকা ছিল। এখন সেটা বাদ —
// Google Sheet-এর "Typing" ট্যাব (headers: id, language, content, updatedAt, Firebase
// হয়ে সিঙ্ক হয়) থেকে রানটাইমে লোড হয় (দেখো util/TypingPassageProvider.kt), Admin App
// থেকে যোগ করা কনটেন্টও এখান দিয়েই আসবে। বাংলা সিলেক্ট থাকলে বাংলা প্যাসেজ, English
// সিলেক্ট থাকলে English প্যাসেজ — sessionLanguage অনুযায়ী filter হয় (poolForLanguage/
// currentPool()-এ), sheet-এর "language" কলাম অনুযায়ী না মিললেও detectLanguage() দিয়ে
// টেক্সট থেকে ভাষা যাচাই হয়, তাই sheet-এ language ফাঁকা থাকলেও কাজ করবে।
// লোড শেষ না হওয়া পর্যন্ত/লোড ব্যর্থ হলে এই তালিকা খালি থাকে — সব pool-getter ও
// fallback ফাংশন খালি তালিকা নিরাপদে হ্যান্ডেল করে (ক্র্যাশ করে না, খালি স্ট্রিং রিটার্ন করে)।
private var PASSAGES: List<PassageInfo> = emptyList()

// ── Phase ২: চিহ্ন ও Backspace ড্রিলের জন্য একটা ছোট curated ব্যাংক — সাধারণ Study/Sheet
// কনটেন্টে বাস্তব-জীবনের চিহ্ন (@, %, &, ., , ইত্যাদি) খুব কম/অসম-ভাবে থাকে, তাই এই
// নির্দিষ্ট মাইক্রো-ড্রিলের জন্য ইমেইল/দাম/ফর্ম-স্টাইল কয়েকটা বাস্তবসম্মত বাক্য —
// PASSAGES-এর মতো এটা "সাধারণ টাইপিং কনটেন্ট" না, তাই hardcode রাখা এখানে সমস্যা না ──
private val SYMBOL_DRILL_BANK = listOf(
    "rahat.hasan@gmail.com এই ইমেইলে সিভি পাঠান, বিষয়: চাকরির আবেদন (Job Application)।",
    "পণ্যের দাম: ৫৫০.৫০ টাকা, ভ্যাট ১৫% যোগ হবে। মোট = ৬৩২.৮৫ টাকা (৳)।",
    "নাম: ______, বয়স: ___, ফোন: +৮৮০১৭xxxxxxxx, ইমেইল: example@mail.com।",
    "প্রশ্ন: ১) সঠিক উত্তর কোনটি? ক) ২০% খ) ৩৫% গ) ৫০% ঘ) কোনোটিই না।",
    "Please confirm by 5:00 PM; otherwise, the slot (Ref#: 2025-07) will be cancelled!",
    "মূল্য-তালিকা: ১০০/-, ২৫০/-, ৫০০/- ও ১,০০০/- টাকার প্যাকেজ পাওয়া যাচ্ছে।",
    "Subject: Re: Application (Urgent) — attachments: CV.pdf, NID.jpg, Photo.png.",
    "টেলিফোন: (০২)-৫৫০০-১১২২, মোবাইল: ০১৭xx-xxxxxx; সময়: সকাল ৯টা-বিকাল ৫টা।",
)

/** TypingPracticeScreen ও TypingRaceScreen — দুটোই স্ক্রিন খোলার সাথে সাথে এটা কল করে,
 *  Sheet থেকে প্যাসেজ পুল একবার লোড করে নেয় (RAM cache থাকলে আবার নেটওয়ার্ক কল হয় না)। */
suspend fun ensureTypingPassagesLoaded(context: android.content.Context) {
    if (PASSAGES.isEmpty()) {
        PASSAGES = com.hanif.smartstudy.util.TypingPassageProvider.getPassages(context)
    }
}

private fun difficultyLabel(d: String) = when (d) {
    "easy"   -> "সহজ"
    "medium" -> "মাঝারি"
    "hard"   -> "কঠিন"
    "custom" -> "আমার প্যাসেজ"
    else     -> "সব"
}

private fun difficultyColor(d: String) = when (d) {
    "easy"   -> GreenOk
    "medium" -> AmberMid
    "hard"   -> RedWrong
    "custom" -> Color(0xFF7C3AED)
    else     -> Indigo600
}

private fun poolFor(difficulty: String): List<PassageInfo> =
    if (difficulty == "all") PASSAGES else PASSAGES.filter { it.difficulty == difficulty }

/** বাংলা নুক্তা-অক্ষর (ড়/ঢ়/য়) দুই রকম Unicode ফর্মে আসতে পারে — একক কোডপয়েন্ট
 *  (precomposed), অথবা base+nukta দুই কোডপয়েন্টের যোগফল (decomposed)। Java-র
 *  Normalizer.NFC ব্যবহার করে প্রথমে এই ফাংশনটা টেক্সটকে "ভেঙে" (decomposed) রাখতো,
 *  কারণ Unicode-এর composition-exclusion নিয়মেই এই তিনটা অক্ষর NFC দিয়ে আর জোড়া
 *  লাগে না। মান (value) মেলাতে এটা ঠিকই কাজ করতো, কিন্তু ডিভাইসের বাংলা ফন্ট এই
 *  ভাঙা (২-কোডপয়েন্ট) সিকোয়েন্সটা ঠিকভাবে জোড়া লাগিয়ে দেখাতে পারছিল না — ফলে
 *  নুক্তাটা বেস অক্ষর থেকে বিচ্ছিন্ন হয়ে একলা ডটেড-সার্কেল হিসেবে রেন্ডার হচ্ছিল
 *  (রং করার কোডে কোনো সমস্যা ছিল না — এটা আসলে raw string-এই ভাঙা থাকার কারণে,
 *  এমনকি প্লেইন টেক্সটফিল্ডেও দেখা যাচ্ছিল)।
 *
 *  তাই এখন উল্টো পথে যাওয়া হলো: base+nukta পেলেই নিজে থেকে জোড়া লাগিয়ে single
 *  precomposed কোডপয়েন্টে নিয়ে আসা হয় (Java Normalizer ব্যবহার না করে, ম্যানুয়ালি) —
 *  এতে সবসময় ফন্টের রেডি-মেড গ্লিফ ব্যবহার হয়, ভাঙা-জোড়া লাগানোর কোনো ঝুঁকি থাকে
 *  না, আর প্যাসেজ ও টাইপ করা টেক্সট — দুটোই একই ফর্মে থাকায় মান-তুলনাও নির্ভরযোগ্য। */
internal fun normalizeBn(s: String): String {
    if (s.indexOf('\u09BC') < 0) return s   // নুক্তা নেই — কিছু করার দরকার নেই (দ্রুত পথ)
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        val next = if (i + 1 < s.length) s[i + 1] else null
        val composed = if (next == '\u09BC') when (c) {
            '\u09A1' -> '\u09DC' // ড + ় → ড়
            '\u09A2' -> '\u09DD' // ঢ + ় → ঢ়
            '\u09AF' -> '\u09DF' // য + ় → য়
            else -> null
        } else null
        if (composed != null) {
            sb.append(composed)
            i += 2
        } else {
            sb.append(c)
            i += 1
        }
    }
    return sb.toString()
}

/** টাইপ করা টেক্সটকে "সম্পূর্ণ (locked)" শব্দ আর "চলমান" শব্দে ভাগ করে — একাধিক
 *  পরপর স্পেস (ডাবল স্পেস টাইপো) একটাই বিভাজক হিসেবে গণ্য হয় (regex " +"), তাই
 *  ভুলবশত এক্সট্রা স্পেসে কোনো ফাঁকা "শব্দ" ঢুকে বাকি সব শব্দের ইনডেক্স শিফট হয়ে
 *  যায় না — এটাই মূল কারণ যে আগে ডাবল-স্পেসে পুরো বাকি অংশ ভুল দেখাতো। */
internal data class TypedWordSplit(val completed: List<String>, val current: String)
internal fun splitTypedWords(normalized: String): TypedWordSplit {
    val trimmed = normalized.trimEnd(' ')
    val raw = if (trimmed.isEmpty()) emptyList() else trimmed.split(Regex(" +"))
    val wordBoundaryJustCrossed = normalized.endsWith(' ') && trimmed.isNotEmpty()
    val completed = if (wordBoundaryJustCrossed) raw else raw.dropLast(1)
    val current   = if (wordBoundaryJustCrossed) "" else raw.lastOrNull() ?: ""
    return TypedWordSplit(completed, current)
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
    // PASSAGES এখনো লোড না হলে/লোড ব্যর্থ হলে (নেট নেই) pool খালি থাকতে পারে —
    // আগে এখানে .random() ক্র্যাশ করত (NoSuchElementException)
    return pool.randomOrNull()?.text ?: ""
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
    // ── Phase ১: প্রতিটা কী-এর accuracy — লাইভ হিটম্যাপ (KeyHeatmapCard) এখান থেকেই আঁকা হয়,
    // দুর্বল-কী ড্রিলও (startKeyDrillSession()) এই একই DB থেকে ডেটা নেয় ──
    var keyHeatmap by remember { mutableStateOf(listOf<TypingKeyStatEntity>()) }
    // ── চলতি সেশনে প্রতিটা কী কতবার সঠিক/ভুল হয়েছে — RAM-এ জমা হয়, সেশন শেষে
    // (finishSession()/finishExamPhase()) একবারে persist হয়, বারবার DB-write এড়াতে ──
    val keyStatsDelta = remember { mutableMapOf<Char, IntArray>() }
    // ── Phase ৩: প্রোফাইল/Roadmap/আঙুল-পজিশন — তিনটাই Dialog হিসেবে, নতুন কোনো
    // নেভিগেশন রুট লাগেনি। এই ডিক্লেয়ারেশনগুলো এখানেই থাকা জরুরি — নিচের
    // LaunchedEffect(Unit)-এই এগুলো ব্যবহার হয়, আর Kotlin-এ local var/state
    // ব্যবহারের আগেই declare করতে হয় (আগে এই ব্লকটা অনেক নিচে ছিল, তাতে
    // "Unresolved reference" কম্পাইল এরর হচ্ছিল) ──
    var showProfileDialog by remember { mutableStateOf(false) }
    var showRoadmapWizard by remember { mutableStateOf(false) }
    var showFingerDialog  by remember { mutableStateOf(false) }
    var roadmapPlan       by remember { mutableStateOf<RoadmapPlan?>(null) }
    // ── Phase ৩ (#1+#2): Key-unlock কারিকুলাম — ট্র্যাক, বর্তমান স্টেজ, স্টেজের
    // নতুন ক্যারেক্টারগুলোর unlock-প্রগ্রেস (String হিসেবে রাখা হয়, কারণ ড়/ঢ়/য়-এর
    // মতো কিছু বাংলা অক্ষর একাধিক Unicode codepoint-এর সমন্বয়ে গঠিত — এগুলো একটা
    // Kotlin Char-এ ধরানো যায় না, তাই BijoyCurriculum.kt-ও String ব্যবহার করে),
    // আর সদ্য-আনলক সেলিব্রেশন ──
    var curriculumTrack    by remember { mutableStateOf("bn") }
    var curriculumStage    by remember { mutableStateOf(1) }
    var curriculumProgress by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var justUnlockedStage  by remember { mutableStateOf<Int?>(null) }
    // ── Neonlipi-স্টাইল সব নতুন ফিচার (heatmap, দুর্বল-কী/চিহ্ন ড্রিল, Govt Mock,
    // BCC, Key-unlock কারিকুলাম, Roadmap, প্রোফাইল/Cloud Sync, আঙুল-পজিশন) এই একটা
    // ফ্ল্যাগের পেছনে — Settings-এ "🧪 Smart Typing" টগল বন্ধ থাকলে (ডিফল্ট) নিচের
    // এই সব UI ব্লক সম্পূর্ণ hide থাকবে, আগের পরিচিত UI-ই দেখা যাবে ──
    var smartTypingEnabled by remember { mutableStateOf(session.getSmartTypingEnabled()) }
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

        // ── Phase ৩: Roadmap প্ল্যান লোকাল থেকে লোড ──
        roadmapPlan = session.getRoadmapPlan()

        // ── Phase ৩ (#1+#2): কারিকুলামের বর্তমান স্টেজ ও প্রগ্রেস লোড ──
        curriculumStage = CurriculumProvider.getCurrentStage(ctx, curriculumTrack)
        curriculumProgress = CurriculumProvider.stageProgress(ctx, curriculumTrack, curriculumStage)

        // ── Phase ৩: Cloud Sync — স্ক্রিন খোলার সাথে সাথে নীরবে pull করে merge করে
        // নেয় (guest হলে/ইন্টারনেট না থাকলে silently স্কিপ হয়ে যায়, UI ব্লক করে না) ──
        session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            val cloud = TypingCloudSyncService.pull(phone)
            if (cloud != null) {
                session.mergeTypingCloudSnapshot(cloud.bestWpm, cloud.history)
                bestWpm = session.getTypingBestWpm()
                history = session.getTypingHistory()
            }
        }
    }

    var selectedDifficulty by remember { mutableStateOf("all") }
    // ── নিজের যোগ করা প্যাসেজ (লোকাল-অনলি) — "আমার প্যাসেজ" ফিল্টার বেছে নিলে
    // এখান থেকেই রান হয়। নিজের সব প্যাসেজ একবার শেষ হয়ে গেলে, এই সেশনে যে ভুলগুলো
    // হয়েছে তা দিয়ে AI পরের ৫ মিনিটের জন্য নতুন প্যাসেজ বানিয়ে দেয় (adaptive
    // মোডের phase-২ AI-ইঞ্জিনই পুনরায় ব্যবহার করা হয়েছে, দেখো নিচে) ──
    var customPassages    by remember { mutableStateOf(listOf<CustomPassageEntity>()) }
    var showAddPassageDialog by remember { mutableStateOf(false) }
    // ── "📖 Passage" কার্ডের + আইকনে চাপলে খোলে — নিজের প্যাসেজ বাছাই/যোগ করার ডায়ালগ ──
    var showCustomPassageManager by remember { mutableStateOf(false) }
    var customPassageInput   by remember { mutableStateOf("") }
    var customCyclesDone     by remember { mutableStateOf(0) }   // নিজের পুরো তালিকা কতবার শেষ হয়েছে
    var customAiFetching     by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        customPassages = AppDatabase.getInstance(ctx).customPassageDao().getAll()
    }
    var passageIndex by remember { mutableStateOf(0) }
    // ── PASSAGES এখন Sheet থেকে asynchronously লোড হয় (দেখো ensureTypingPassagesLoaded()),
    // তাই শুরুতে খালি — নিচের LaunchedEffect(Unit) লোড শেষে reset() দিয়ে বৈধ প্যাসেজ বসায় ──
    var passage      by remember { mutableStateOf("") }
    var userInput    by remember { mutableStateOf("") }
    // ── Word-by-word matching (selftyping.com/10fastfingers-এর স্ট্যান্ডার্ড পদ্ধতি) —
    // প্যাসেজকে স্পেস দিয়ে শব্দে ভাগ করা হয়। একটা শব্দ পুরোপুরি টাইপ করে স্পেস চাপলেই
    // (বা প্যাসেজের একদম শেষ শব্দ হলে) সেটা "লক" হয়ে যায় — ঠিক/ভুল চিরস্থায়ীভাবে ফিক্স
    // হয়ে যায়, পরে যাই হোক না কেন আর বদলায় না। এতে ১টা শব্দে ভুল হলেও আগে/পরের অন্য
    // কোনো শব্দ প্রভাবিত হয় না — char-by-char index-তুলনার cascading সমস্যা পুরোপুরি
    // এড়ানো যায়। ডাবল-স্পেসও এমনিতেই সমাধান হয়ে যায়, কারণ একাধিক স্পেসকে একটাই
    // শব্দ-বিভাজক ধরা হয় (দেখো splitTypedWords())। ──
    var frozenWordResults by remember { mutableStateOf(listOf<Boolean>()) }
    // ── frozenWordResults-এর সমান্তরাল লিস্ট — কোন কোন লক-হওয়া শব্দে "স্পেস মিস" অটো-ফিক্স
    // হয়েছিল (অক্ষর হয়তো ঠিক ছিল, কিন্তু মাঝের স্পেসটা ইউজার চাপেনি — অ্যাপ নিজে থেকে বসিয়ে
    // দিয়েছে যাতে বাকি প্যাসেজ sync না হারায়)। এই শব্দগুলো ঠিক-এর মতো দেখতে (green) না, বরং
    // আলাদা রঙে (amber) রেন্ডার হবে, এবং সবসময় "ভুল" হিসেবেই গোনা হবে — ভুলটা মাফ হয় না ──
    var autoFixedWordFlags by remember { mutableStateOf(listOf<Boolean>()) }
    var isStarted    by remember { mutableStateOf(false) }
    var isFinished   by remember { mutableStateOf(false) }
    var elapsedSec   by remember { mutableStateOf(0) }
    var result       by remember { mutableStateOf<TypingResult?>(null) }

    // ── AI Adaptive Session — "free" (স্বাভাবিক প্র্যাকটিস) বনাম "adaptive" (দুই-ধাপ) ──
    var sessionMode      by remember { mutableStateOf("free") }   // "free" | "adaptive"
    // ── Phase ২: Govt Job মক টেস্ট — ইউজার সিলেক্ট করা সময়সীমা (মিনিট) ও শেষে
    // দেখানোর জন্য পেনাল্টি WPM (দেখো startGovtMockTest()/finishSession()) ──
    var govtMockMinutes    by remember { mutableStateOf(10) }
    var govtMockPenaltyWpm by remember { mutableStateOf(0) }
    var sessionLanguage  by remember { mutableStateOf("bn") }     // adaptive মোডে ভাষা মিশবে না

    // ── Phase ১: ভাষা বদলালে (🌐 সিলেক্টর) হিটম্যাপও সেই ভাষার কী-স্ট্যাট দিয়ে রিলোড হয় ──
    LaunchedEffect(sessionLanguage) {
        keyHeatmap = TypingKeyStatStore.getHeatmap(ctx, sessionLanguage)
    }

    /** "প্র্যাকটিস" মোডের বর্তমান পুল — difficulty অনুযায়ী (বা "custom" হলে নিজের
     *  প্যাসেজ), এবং টপ বারের 🌐 ভাষা সিলেক্টর অনুযায়ী ফিল্টার করা। ভাষা-ফিল্টারে
     *  পুল খালি হয়ে গেলে (যেমন কোনো ভাষায় passage না থাকলে) আনফিল্টার্ড পুলে ফিরে যায় */
    fun currentPool(): List<PassageInfo> {
        val base = if (selectedDifficulty == "custom") customPassages.map { PassageInfo(it.text, "custom") }
                   else poolFor(selectedDifficulty)
        if (selectedDifficulty == "custom") return base   // নিজের প্যাসেজ ভাষা-ফিল্টার হয় না
        return base.filter { TypingErrorAnalyzer.detectLanguage(it.text) == sessionLanguage }.ifEmpty { base }
    }
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

    // ── স্টাডি টাইপিং মোড — Study sheet-এর subject/sub_topic থেকে কনটেন্ট পুল করে
    // প্যাসেজ হিসেবে দেখায়, একটা আইটেম একবার টাইপ হলে আর ফেরত আসে না ──
    var studySubjectList  by remember { mutableStateOf<List<String>>(emptyList()) }
    var studySubTopicList by remember { mutableStateOf<List<String>>(emptyList()) }
    var studySubject      by remember { mutableStateOf<String?>(null) }
    var studySubTopic     by remember { mutableStateOf<String?>(null) }
    var studyCurrentId    by remember { mutableStateOf<String?>(null) }
    var studyPoolTotal    by remember { mutableStateOf(0) }
    var studyPoolUsed     by remember { mutableStateOf(0) }
    var studyExhausted    by remember { mutableStateOf(false) }
    var studyLoading      by remember { mutableStateOf(false) }
    // ── আগে শুধু "list খালি কিনা" দেখে "লোড হচ্ছে" টেক্সট দেখানো হতো — তাই যদি
    // ক্যাশে সত্যিই কোনো ডেটা না থাকে (এখনো সিঙ্ক হয়নি/ইন্টারনেট নেই), লোডিং শেষ
    // হয়ে যাওয়ার পরও লিস্ট খালিই থেকে যেত, ফলে UI চিরকাল "⏳ লোড হচ্ছে..." দেখাতো
    // যদিও আসলে লোডিং অনেক আগেই শেষ। এখন আলাদা loading-flag রাখা হলো, যাতে
    // "এখনো লোড হচ্ছে" আর "লোড শেষ কিন্তু কোনো ডেটা নেই" — এই দুটো আলাদা করা যায় ──
    var studySubjectsLoading  by remember { mutableStateOf(false) }
    var studySubTopicsLoading by remember { mutableStateOf(false) }

    // ── কীস্ট্রোক-ভিত্তিক accuracy ট্র্যাকিং — WPM/accuracy আন্তর্জাতিক ক্যারেক্টার-ভিত্তিক
    // সূত্র মেনেই হিসেব হয় (৫ ক্যারেক্টার = ১ শব্দ), শুধু গণনাটা এখন প্রতিটা শব্দ "লক" হওয়ার
    // মুহূর্তে (target শব্দ vs typed শব্দ) হয়, char-by-char resync-pointer দিয়ে না ──
    var correctKeystrokes   by remember { mutableStateOf(0) }
    var incorrectKeystrokes by remember { mutableStateOf(0) }
    var totalKeystrokes     by remember { mutableStateOf(0) }

    // ── ধাপ ৪: বাম/ডান হাতের সঠিক-ভুল অক্ষর গণনা (session-local, শেষে Room-এ flush হবে) ──
    var leftCorrectChars  by remember { mutableStateOf(0) }
    var leftWrongChars    by remember { mutableStateOf(0) }
    var rightCorrectChars by remember { mutableStateOf(0) }
    var rightWrongChars   by remember { mutableStateOf(0) }
    var syncLossCount     by remember { mutableStateOf(0) }

    /** কোনো টেক্সট আসলে টাইপ করার মতো অর্থবহ কনটেন্ট কিনা — শুধু একটা raw
     *  ছবি/লিংক (যেমন https://i.ibb.co/...) হলে সেটা বাদ দেওয়া হয়, কারণ সেটা
     *  টাইপ করার মতো কোনো বাক্য/ব্যাখ্যা না, স্রেফ একটা URL */
    fun isTypableText(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isBlank()) return false
        val urlOnly = Regex("""^(https?://\S+|www\.\S+)[.\s]*$""", RegexOption.IGNORE_CASE)
        return !urlOnly.matches(t)
    }

    /** Study sheet-এর একটা আইটেম (question/explanation/technique/answer) জোড়া দিয়ে
     *  একটাই টাইপিং প্যাসেজ বানায় — "কারক কি? কত প্রকার? কোনটা কি উপায়ে চেনা যায়"
     *  গোছের পুরো ব্যাখ্যাটাই একসাথে টাইপ করা যাবে।
     *  — explanationIsPublic = false (এডমিন-শুধু/প্রাইভেট ব্যাখ্যা) হলে সাধারণ
     *    ইউজারের জন্য সেটা বাদ দেওয়া হয় — Study রিডিং স্ক্রিনে যেটা লুকানো থাকে,
     *    Study Typing-এও সেটা "অন্য জায়গায়" ফাঁস হয়ে যাওয়া ঠিক না।
     *  — explanation/technique যদি স্রেফ একটা raw ছবির লিংক হয়, সেটাও বাদ।
     *  — ব্যাখ্যা ফাঁকা/অদেখাযোগ্য/শুধু-লিংক হলে (কিন্তু উত্তরে টাইপ করার মতো আসল
     *    টেক্সট থাকলে) — ব্যাখ্যার বদলে সেই উত্তরটাই প্যাসেজে যোগ হয়, যাতে আইটেমটা
     *    পুরো বাদ না পড়ে যায় ──
     */
    fun buildStudyPassageText(item: com.hanif.smartstudy.data.local.QuestionEntity): String {
        val isAdmin = session.getCurrentUser()?.isAdmin() == true
        val canSeeExplanation = item.explanationIsPublic || isAdmin
        val explanationUsable = canSeeExplanation && isTypableText(item.explanation)
        val explanationOrAnswer = if (explanationUsable) item.explanation
                                   else item.answer.takeIf { isTypableText(it) }
        return listOfNotNull(
            item.question.takeIf { isTypableText(it) },
            explanationOrAnswer,
            item.technique.takeIf { isTypableText(it) }
        ).joinToString(" ").trim()
    }

    /** Room-এর "STUDY" শীটে ডেটা না থাকলে (বা এই সাবজেক্ট/সাব-টপিকের ডেটা এখনো Room-এ
     *  লেখা হয়নি) — ContentRepository-র in-memory cache (যেটা 📚 Study সেকশন খুললেই
     *  ভরে যায়) থেকে সরাসরি নিয়ে Room-এ upsert করে দেয়, যাতে সাথে সাথেই আবার Room
     *  query চালালে ডেটা পাওয়া যায়। এটাই মূল সমাধান: আগে Room-sync শুধু ব্যাকগ্রাউন্ডে
     *  (fire-and-forget GlobalScope) হতো — Study Typing খোলার সময় সেটা শেষ না হলে
     *  "কোনো কনটেন্ট পাওয়া যায়নি" দেখাত, যদিও memCache/ডিস্ক-ক্যাশে ডেটা আসলে ছিল। */
    suspend fun syncStudyFromRepoIfNeeded(): Boolean {
        return try {
            val dao = AppDatabase.getInstance(ctx).questionDao()
            val repo = com.hanif.smartstudy.data.repository.ContentRepository(ctx)
            val content = com.hanif.smartstudy.data.repository.ContentRepository.getMemCache()
                ?: (repo.getContent() as? com.hanif.smartstudy.data.repository.DataState.Success)?.data
            if (content != null && content.study.isNotEmpty()) {
                dao.upsertAll(content.study.map { it.toEntity() })
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /** একটা sub_topic-এর জন্য used-id বাদ দিয়ে নতুন একটা আইটেম তুলে প্যাসেজ বানায়।
     *  পুল খালি হয়ে গেলে (সব টাইপ হয়ে গেছে) studyExhausted = true হয়ে UI-তে
     *  "✅ সব প্যাসেজ টাইপ করা হয়ে গেছে" বার্তা + রিসেট বাটন দেখায় */
    fun loadStudyPool(subject: String, subTopic: String) {
        studyLoading = true
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(ctx).questionDao()
                val progressDao = AppDatabase.getInstance(ctx).studyTypingProgressDao()
                val user = session.getCurrentUser()
                val userId = user?.phone?.takeIf { it.isNotBlank() } ?: "guest"

                var all = dao.getAllForSubTopic("STUDY", subject, subTopic)
                if (all.isEmpty() && syncStudyFromRepoIfNeeded()) {
                    all = dao.getAllForSubTopic("STUDY", subject, subTopic)
                }
                val visible = all.filter { com.hanif.smartstudy.util.AudienceFilter.userCanSee(it.audienceTags, user) }
                    .filter { buildStudyPassageText(it).isNotBlank() }
                val usedIds = progressDao.getUsedIds(userId, subject, subTopic).toSet()
                val available = visible.filter { it.fbKey !in usedIds }

                studyPoolTotal = visible.size
                studyPoolUsed  = visible.count { it.fbKey in usedIds }
                studyExhausted = visible.isNotEmpty() && available.isEmpty()

                val pick = available.randomOrNull()
                if (pick != null) {
                    studyCurrentId = pick.fbKey
                    passage = normalizeBn(buildStudyPassageText(pick))
                } else {
                    studyCurrentId = null
                    passage = ""
                }
                userInput = ""; frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
                isStarted = false; isFinished = false; elapsedSec = 0; result = null
                correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0
                leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0; syncLossCount = 0
            } catch (e: Exception) {
                // ── আগে এখানে exception হলে studyLoading আর কখনো false হতো না —
                // UI চিরকাল "⏳ প্যাসেজ লোড হচ্ছে..." দেখিয়ে যেত। এখন try/finally
                // দিয়ে নিশ্চিত করা হলো এটা যেকোনো অবস্থাতেই শেষ হবে ──
                passage = ""
                studyCurrentId = null
            } finally {
                studyLoading = false
            }
        }
    }

    /** সাবজেক্ট বেছে নেওয়ার সাথে সাথে সেই সাবজেক্টের sub_topic লিস্ট লোড করে */
    fun loadStudySubTopics(subject: String) {
        studySubject = subject
        studySubTopic = null
        studySubTopicList = emptyList()
        studyCurrentId = null
        passage = ""
        studyExhausted = false
        studySubTopicsLoading = true
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(ctx).questionDao()
                var subTopics = dao.getSubTopics("STUDY", subject)
                if (subTopics.isEmpty() && syncStudyFromRepoIfNeeded()) {
                    subTopics = dao.getSubTopics("STUDY", subject)
                }
                studySubTopicList = subTopics
            } catch (e: Exception) {
                studySubTopicList = emptyList()
            } finally {
                studySubTopicsLoading = false
            }
        }
    }

    /** "📚 Study Typing" ট্যাবে ট্যাপ করলে কল হয় — প্রথমে সাবজেক্ট লিস্ট লোড হয়,
     *  ইউজার সাবজেক্ট → সাব-টপিক বেছে নিলে তবেই প্যাসেজ পুল তৈরি হয় */
    fun startStudyMode() {
        sessionMode = "study"
        studySubject = null
        studySubTopic = null
        studyCurrentId = null
        studySubTopicList = emptyList()
        studyExhausted = false
        passage = ""
        userInput = ""; frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
        isStarted = false; isFinished = false; elapsedSec = 0; result = null
        correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0
        leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0; syncLossCount = 0
        studySubjectsLoading = true
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(ctx).questionDao()
                var subjects = dao.getSubjects("STUDY")
                if (subjects.isEmpty() && syncStudyFromRepoIfNeeded()) {
                    subjects = dao.getSubjects("STUDY")
                }
                studySubjectList = subjects
            } catch (e: Exception) {
                studySubjectList = emptyList()
            } finally {
                studySubjectsLoading = false
            }
        }
    }

    /** টাইপিং শেষ হলে (finishStudyItem হুকের ভেতর থেকে) আইটেমটাকে "used" হিসেবে সেভ করে,
     *  যাতে এই sub_topic-এ আবার এলে এই আইটেমটা দ্বিতীয়বার না আসে */
    fun markCurrentStudyItemUsed() {
        val subject = studySubject ?: return
        val subTopic = studySubTopic ?: return
        val id = studyCurrentId ?: return
        scope.launch {
            val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
            AppDatabase.getInstance(ctx).studyTypingProgressDao().markUsed(
                com.hanif.smartstudy.data.local.StudyTypingProgressEntity(
                    userId = userId, subject = subject, subTopic = subTopic,
                    contentId = id, typedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** "🔄 এই টপিক রিসেট করো" বাটনে ট্যাপ করলে কল হয় — শুধু এই sub_topic-এর used
     *  id গুলোই মোছে (পুরো ট্র্যাকিং টেবিল না), তারপর পুল আবার লোড হয় */
    fun resetCurrentStudySubTopic() {
        val subject = studySubject ?: return
        val subTopic = studySubTopic ?: return
        scope.launch {
            val userId = session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "guest"
            AppDatabase.getInstance(ctx).studyTypingProgressDao().resetSubTopic(userId, subject, subTopic)
            loadStudyPool(subject, subTopic)
        }
    }


    // ── Word-level mistake tracking (Phase ১) ──
    val passageWords = remember(passage) { passage.split(' ') }
    val passageLang  = remember(passage) { TypingErrorAnalyzer.detectLanguage(passage) }

    // ── প্যাসেজ বক্স এখন ফিক্সড-হাইট + অটো-স্ক্রল — লম্বা প্যাসেজ (Study Typing-এ
    // প্রায়ই ৫০০+ ক্যারেক্টার) থাকলে আগে পুরো Card unbounded বেড়ে যেত, ফলে টাইপিং
    // ইনপুট বক্স স্ক্রিনের অনেক নিচে চলে যেত। এখন প্যাসেজের নিজস্ব ছোট viewport-এর
    // ভেতরেই টাইপ করতে করতে বর্তমান লাইন লক্ষ্য করে স্মুথলি অটো-স্ক্রল হয় (আগের
    // লাইন উপরে সরে যায়, নতুন লাইন নিচ থেকে উঠে আসে) — টার্গেট প্যাসেজ ও ইনপুট বক্স
    // সবসময় একসাথে ফিক্সড স্ক্রিনে দেখা যায় ──
    var passageTextLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val passageScrollState = rememberScrollState()
    val currentWordCharOffset = remember(frozenWordResults.size, passageWords) {
        var off = 0
        for (i in 0 until minOf(frozenWordResults.size, passageWords.size)) {
            off += passageWords[i].length + 1  // শব্দ + তার পরের স্পেস
        }
        off
    }
    // নতুন প্যাসেজ লোড হলে (আগের প্যাসেজ থেকে সম্পূর্ণ ভিন্ন টেক্সট) স্ক্রল সাথে সাথেই
    // টপে রিসেট হয় — অ্যানিমেট করে না, কারণ এটা নতুন কনটেন্ট, "চলমান" স্ক্রল না
    LaunchedEffect(passage) { passageScrollState.scrollTo(0) }
    // টাইপ করতে করতে বর্তমান শব্দ যে লাইনে আছে, সেই লাইন (এক লাইন আগে থেকে, যাতে
    // প্রসঙ্গ বোঝা যায়) viewport-এর টপে আনার জন্য স্মুথ অ্যানিমেটেড স্ক্রল
    LaunchedEffect(currentWordCharOffset, passageTextLayout) {
        val layout = passageTextLayout ?: return@LaunchedEffect
        val textLen = layout.layoutInput.text.text.length
        if (textLen == 0) return@LaunchedEffect
        val safeOffset = currentWordCharOffset.coerceIn(0, textLen - 1)
        val line = layout.getLineForOffset(safeOffset)
        val topLine = (line - 1).coerceAtLeast(0)
        val targetY = layout.getLineTop(topLine).toInt().coerceAtLeast(0)
        passageScrollState.animateScrollTo(targetY)
    }

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

    // ── টপ বার রিডিজাইন — "অনুশীলনের ধরন" রো কোলাপসিবল (ডিফল্ট খোলা), History এখন
    // popup/dialog, Focus mode-এর নিজস্ব আইকন এন্ট্রি পয়েন্ট ──
    var modeTypeExpanded by remember { mutableStateOf(true) }
    // ── টাইপ করার জন্য একটা বৈধ টার্গেট প্যাসেজ রেডি হয়ে গেলে "অনুশীলনের ধরন" প্যানেল
    // (মোড বাটন + সাবজেক্ট/সাব-টপিক চিপ ইত্যাদি) অটোমেটিক কোলাপ্স হয়ে যায় — যাতে
    // প্যাসেজ ও ইনপুট বক্সের জন্য বেশি জায়গা ফাঁকা থাকে (ইউজার চাইলে টগল বাটনে চেপে
    // আবার খুলতে পারবে) ──
    LaunchedEffect(passage) {
        if (passage.isNotBlank()) modeTypeExpanded = false
    }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }   // 🌐 বাংলা/English সিলেক্টর

    // ── 🎯 Focus mode — টাইপিং স্ক্রিন থেকে ট্যাপ করলেই সরাসরি অন/অফ হয়ে যায়, কোনো
    // সাবজেক্ট/পরীক্ষার-তারিখ বাছাইয়ের প্রয়োজন নেই — টাইপিং-এ থাকা মানেই ফোকাস
    // টাইপিং-এই থাকবে, এটাই স্বাভাবিক (দেখো TopAppBar-এর 🎯 আইকন) ──
    val focusStore = remember { com.hanif.smartstudy.focus.FocusModeStore(ctx) }
    val focusState by focusStore.stateFlow.collectAsState(initial = com.hanif.smartstudy.focus.FocusModeState())
    val focusActive = focusState.isEffectivelyActive()
    fun toggleFocusMode() {
        scope.launch {
            if (focusActive) focusStore.deactivate()
            else focusStore.activate(
                com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT,
                com.hanif.smartstudy.focus.FocusModeStore.todayStartMillis()
            )
        }
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

    /** এখন পর্যন্ত যা টাইপ হয়েছে তা দিয়েই সেশন চূড়ান্তভাবে শেষ করে, WPM/accuracy হিসাব
     *  করে, history/best-WPM/হাত-ভিত্তিক স্ট্যাটে সেভ করে, আর ResultCard দেখায় — পুরো
     *  প্যাসেজ শেষ হলে (বা adaptive/exam-এ সময়-বাজেট শেষ হলে) স্বয়ংক্রিয়ভাবে যা হতো,
     *  ঠিক সেই একই লজিক। "📤 Submit Now" বাটনে ট্যাপ করলেও এটাই কল হয় — তাই যেকোনো
     *  সময় চাপলে স্বাভাবিক ফলাফলই (as usual) দেখা যায়, আলাদা কোনো পথ নেই। */
    fun finishSession() {
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
        // ── Phase ২: Govt Job মক টেস্টে প্রতিটা ভুল কী-প্রেসের জন্য WPM থেকে পেনাল্টি
        // কাটা হয় (বাস্তব সরকারি ডেটা এন্ট্রি পরীক্ষার নিয়ম অনুযায়ী) — result.wpm
        // অপরিবর্তিত থাকে (bestWPM/history-এর সাথে তুলনা সঠিক রাখতে), শুধু আলাদা
        // state-এ পেনাল্টি রাখা হয়, ResultCard-এর পাশে আলাদা কার্ডে দেখানো হয় ──
        govtMockPenaltyWpm = if (sessionMode == "govtmock")
            (incorrectKeystrokes * 0.5).toInt() else 0
        result = r
        onResult(r)
        if (sessionMode == "study") markCurrentStudyItemUsed()
        scope.launch {
            session.recordTypingResult(r.wpm, r.rawWpm, r.accuracy, r.timeSec)
            bestWpm = maxOf(bestWpm, r.wpm)
            history = session.getTypingHistory()

            // ── Phase ৩: Cloud Sync — লোকাল persist-এর পরই নীরবে cloud-এ push (guest
            // হলে/নেট না থাকলে ভেতরেই silently স্কিপ/fail হয়, মূল ফলাফল প্রদর্শনে
            // কোনো প্রভাব পড়ে না) ──
            session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                TypingCloudSyncService.push(phone, session.getTypingBestWpm(), session.getRawTypingHistory())
            }

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

            // ── Phase ১/২: এই সেশনে জমা হওয়া প্রতিটা কী-এর সঠিক/ভুল কাউন্ট একবারে
            // persist — প্রতিটা char নিজের ধরন (বাংলা অক্ষর/ইংরেজি অক্ষর/চিহ্ন) অনুযায়ী
            // আলাদা bucket-এ ভাগ হয়ে যায় ("sym" bucket-টাই Phase ২-এর চিহ্ন-ড্রিলের ভিত্তি) —
            // তারপর হিটম্যাপ রিফ্রেশ (নতুন সংখ্যা সাথে সাথে দেখাতে) ──
            if (keyStatsDelta.isNotEmpty()) {
                val bnDelta  = keyStatsDelta.filterKeys { it.code in 0x0980..0x09FF }
                val symDelta = keyStatsDelta.filterKeys { it.code !in 0x0980..0x09FF && !it.isLetterOrDigit() }
                val enDelta  = keyStatsDelta.filterKeys { it.code !in 0x0980..0x09FF && it.isLetterOrDigit() }
                if (bnDelta.isNotEmpty())  TypingKeyStatStore.addDeltas(ctx, "bn", bnDelta)
                if (enDelta.isNotEmpty())  TypingKeyStatStore.addDeltas(ctx, "en", enDelta)
                if (symDelta.isNotEmpty()) TypingKeyStatStore.addDeltas(ctx, "sym", symDelta)
                keyStatsDelta.clear()
                keyHeatmap = TypingKeyStatStore.getHeatmap(ctx, sessionLanguage)
            }

            // ── Phase ৩ (#1+#2): কারিকুলাম-মোডে সেশন শেষ হলে unlock-শর্ত চেক করা হয় —
            // পূরণ হলে পরের স্টেজে এগিয়ে যায় (celebration UI দেখায়), নাহলে চুপচাপ
            // বর্তমান স্টেজেরই প্রগ্রেস (progress bar) আপডেট হয় ──
            if (sessionMode == "curriculum") {
                val targetWpm = session.getTypingTargetWpm()
                val advanced = CurriculumProvider.checkAndAdvance(ctx, curriculumTrack, targetWpm, r.wpm)
                if (advanced != null) {
                    curriculumStage = advanced
                    justUnlockedStage = advanced
                } 
                curriculumProgress = CurriculumProvider.stageProgress(ctx, curriculumTrack, curriculumStage)
            }
        }
    }

    // Check completion
    LaunchedEffect(userInput) {
        // ── "ফ্রি টাইপিং" মোডে কোনো নির্দিষ্ট target passage নেই (হার্ড কপি বই দেখে
        // নিজের ইচ্ছামতো টাইপ করা হয়), তাই word-matching/completion লজিকের কোনো মানে
        // নেই — এই মোডে ইউজার নিজেই "শেষ করুন" বাটনে চাপলে সেশন শেষ হবে (নিচে দেখো) ──
        if (sessionMode == "freetyping") return@LaunchedEffect

        // ── "শেষ হয়েছে কিনা" — সব শব্দ লক হয়ে গেছে, অথবা শেষ শব্দটাই এখন ঠিক
        // টাইপ হয়ে গেছে (শেষ শব্দের পর সাধারণত স্পেস চাপা হয় না) ──
        val split = splitTypedWords(userInput)
        val allDone = split.completed.size >= passageWords.size ||
            (split.completed.size == passageWords.size - 1 && split.current == passageWords.lastOrNull())
        if (isStarted && allDone && !isFinished) {

            // ── Adaptive Session — phase ১: পুরো প্যাসেজ শেষ হলেও সেশন শেষ না, পরের
            // random প্যাসেজে লুপ করবে (টাইমার/স্ট্যাটস চলতেই থাকবে), যতক্ষণ না
            // ADAPTIVE_PHASE1_SECONDS সময় পেরিয়ে যায় (সেটা নিচের আলাদা effect-এ চেক হয়) ──
            if (sessionMode == "adaptive" && adaptivePhase == 1) {
                val pool = poolForLanguage(sessionLanguage)
                val nextIdx = (passageIndex + 1).mod(pool.size.coerceAtLeast(1))
                passageIndex = nextIdx
                passage      = normalizeBn(pool.getOrNull(nextIdx)?.text ?: passage)
                userInput    = ""
                frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
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
                frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
                return@LaunchedEffect
            }

            // ── Phase ২: Govt Job মক টেস্ট — একটা প্যাসেজ শেষ হলেও নির্বাচিত সময়সীমা
            // (govtMockMinutes) শেষ না হওয়া পর্যন্ত পরের random প্যাসেজে লুপ করে (সময়-ভিত্তিক
            // জোরপূর্বক-ইতি নিচের আলাদা effect-এ হ্যান্ডল হয়, ঠিক Exam Simulation-এর মতোই) ──
            if (sessionMode == "govtmock") {
                val pool = currentPool()
                val nextIdx = (passageIndex + 1).mod(pool.size.coerceAtLeast(1))
                passageIndex = nextIdx
                passage      = normalizeBn(pool.getOrNull(nextIdx)?.text ?: passage)
                userInput    = ""
                frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
                return@LaunchedEffect
            }

            // ── Free/সাধারণ প্র্যাকটিস মোড — একটা প্যাসেজ শেষ হলেও কমপক্ষে
            // FREE_MODE_MIN_SECONDS (৫ মিনিট) পার না হওয়া পর্যন্ত একই ডিফিকাল্টির
            // পরের প্যাসেজে লুপ করে, যাতে একটানা লেখার প্র্যাকটিস হয়। প্রতিটা
            // ডিফিকাল্টি পুলে বাংলা ও ইংরেজি প্যাসেজ মিশানো আছে — তাই লুপ করার সময়
            // শুধু বর্তমান প্যাসেজের ভাষার মধ্যেই থাকা হয়, নাহলে মাঝপথে হঠাৎ
            // বাংলা থেকে ইংরেজিতে (বা উল্টো) বদলে গিয়ে কিবোর্ড অ্যাপই বদলাতে হতো ──
            if (sessionMode == "free" && elapsedSec < FREE_MODE_MIN_SECONDS) {
                val pool = currentPool()
                if (pool.isEmpty()) {
                    // "আমার প্যাসেজ" বেছে নেওয়া কিন্তু এখনো কিছু যোগ করা হয়নি
                    isFinished = true
                    return@LaunchedEffect
                }

                if (selectedDifficulty == "custom") {
                    // ── নিজের সেভ করা প্যাসেজ — একটা শেষ হলে পরেরটায় লুপ করে। পুরো
                    // তালিকা একবার শেষ হয়ে গেলে (index আবার ০-তে ফিরলে), এই সেশনে
                    // যে শব্দগুলোয় ভুল হয়েছে সেগুলো দিয়ে AI একটা নতুন প্যাসেজ বানিয়ে
                    // দেয় — বাকি সময়টা সেই টার্গেটেড প্যাসেজেই লুপ চলতে থাকে ──
                    val nextIdx = (passageIndex + 1).mod(pool.size)
                    if (nextIdx == 0 && !customAiFetching) {
                        customCyclesDone++
                        customAiFetching = true
                        scope.launch {
                            val weak = sessionMistakeWords.distinct().take(10)
                            val res = TypingAdaptiveContentProvider.getBlendedPassage(
                                ctx, weak, passageLang, "medium", null
                            )
                            passage = normalizeBn(res.passage)
                            passageIndex = 0
                            userInput = ""
                            frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
                            customAiFetching = false
                        }
                        return@LaunchedEffect
                    }
                    passageIndex = nextIdx
                    passage      = normalizeBn(pool.getOrNull(nextIdx)?.text ?: passage)
                    userInput    = ""
                    frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
                    return@LaunchedEffect
                }

                val currentLang = TypingErrorAnalyzer.detectLanguage(passage)
                val sameLangIdx = pool.indices.filter {
                    TypingErrorAnalyzer.detectLanguage(pool[it].text) == currentLang
                }
                val candidates = if (sameLangIdx.size > 1) sameLangIdx else pool.indices.toList()
                val curPos  = candidates.indexOf(passageIndex).let { if (it >= 0) it else 0 }
                val nextPos = (curPos + 1).mod(candidates.size.coerceAtLeast(1))
                val nextIdx = candidates.getOrElse(nextPos) { 0 }
                passageIndex = nextIdx
                passage      = normalizeBn(pool.getOrNull(nextIdx)?.text ?: passage)
                userInput    = ""
                frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
                return@LaunchedEffect
            }

            finishSession()
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

    /** Exam মোডের বর্তমান ফেজ (English/Bangla) এখনই শেষ করে — সময়-বাজেট (EXAM_PHASE_SECONDS)
     *  শেষ হলে যেটা স্বয়ংক্রিয়ভাবে হতো, ঠিক সেই একই লজিক। ইংরেজি ফেজে থাকলে বাংলা ফেজে
     *  transition কার্ড দেখায় (আগের মতোই), বাংলা ফেজে থাকলে দুই ফেজ মিলিয়ে চূড়ান্ত
     *  ExamResultCard দেখায়। "📤 Submit Now"-এ ট্যাপ করলে এটাই কল হয়। */
    fun finishExamPhase() {
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

                // ── Phase ১/২: দুই ফেজ মিলিয়ে জমা হওয়া কী-স্ট্যাট একবারে persist —
                // বাংলা/ইংরেজি/চিহ্ন — তিন bucket-এ ভাগ হয়ে যায় ──
                if (keyStatsDelta.isNotEmpty()) {
                    val bnDelta  = keyStatsDelta.filterKeys { it.code in 0x0980..0x09FF }
                    val symDelta = keyStatsDelta.filterKeys { it.code !in 0x0980..0x09FF && !it.isLetterOrDigit() }
                    val enDelta  = keyStatsDelta.filterKeys { it.code !in 0x0980..0x09FF && it.isLetterOrDigit() }
                    if (bnDelta.isNotEmpty())  TypingKeyStatStore.addDeltas(ctx, "bn", bnDelta)
                    if (enDelta.isNotEmpty())  TypingKeyStatStore.addDeltas(ctx, "en", enDelta)
                    if (symDelta.isNotEmpty()) TypingKeyStatStore.addDeltas(ctx, "sym", symDelta)
                    keyStatsDelta.clear()
                    keyHeatmap = TypingKeyStatStore.getHeatmap(ctx, sessionLanguage)
                }
            }
        }
    }

    // ── Exam Simulation — সময়-বাজেট নিয়ন্ত্রণ: EXAM_PHASE_SECONDS (১০ মিনিট) শেষ হলে
    // মাঝ-প্যাসেজেও জোর করে থামিয়ে দেয় — ঠিক বাস্তব পরীক্ষার মতো (দেখো রোডম্যাপ সেকশন ৪ —
    // "১০ মিনিট শেষ হওয়ার পর সফটওয়্যার স্বয়ংক্রিয়ভাবে বন্ধ হয়ে যায়") ──
    LaunchedEffect(elapsedSec, sessionMode, examPhase, isStarted, isFinished) {
        if (sessionMode != "exam" || !isStarted || isFinished) return@LaunchedEffect
        if (elapsedSec < EXAM_PHASE_SECONDS) return@LaunchedEffect

        finishExamPhase()
    }

    // ── Phase ২: Govt Job মক টেস্ট — নির্বাচিত সময়সীমা (govtMockMinutes) শেষ হলে
    // মাঝ-প্যাসেজেও জোর করে থামিয়ে দেয়, ঠিক Exam Simulation-এর মতোই ──
    LaunchedEffect(elapsedSec, sessionMode, govtMockMinutes, isStarted, isFinished) {
        if (sessionMode != "govtmock" || !isStarted || isFinished) return@LaunchedEffect
        if (elapsedSec < govtMockMinutes * 60) return@LaunchedEffect

        finishSession()
    }

    // ── Word-by-word matching — একটা শব্দ পুরো টাইপ করে স্পেস চাপলেই (বা প্যাসেজের
    // শেষ শব্দ হলে) সেটা "লক" হয়ে যায়, চিরস্থায়ীভাবে ঠিক/ভুল ফিক্স হয়ে যায়। এতে
    // একটা শব্দে ভুল হলে শুধু সেই শব্দটাই প্রভাবিত হয়, বাকি সব শব্দ (আগে/পরে)
    // সম্পূর্ণ স্বাধীন থাকে — char-by-char index-তুলনার cascading সমস্যা এখানে
    // আর নেইই (কোনো resync-heuristic লাগে না, কাঠামোগতভাবেই এড়ানো)। ──
    fun onInputChange(new: String) {
        if (isFinished) return
        // ── Phase ২: Govt Job মক টেস্টে Backspace/মুছে ফেলা নিষিদ্ধ — বাস্তব সরকারি
        // ডেটা এন্ট্রি পরীক্ষার নিয়ম অনুযায়ী, ভুল হলেই এগিয়ে যেতে হয়, পেছনে ফেরা যায় না ──
        if (sessionMode == "govtmock" && new.length < userInput.length) return
        if (!isStarted && new.isNotEmpty()) isStarted = true
        // ── Phase ১: কী-সাউন্ড — শুধু ক্যারেক্টার যোগ হলে বাজে (ব্যাকস্পেস/মুছে ফেলায় না,
        // নাহলে ব্যাকস্পেস দেওয়াও "পুরস্কৃত" মনে হতে পারে) ──
        if (smartTypingEnabled && new.length > userInput.length) TypingKeySound.playForCurrentPreset(ctx)
        var normalized = normalizeBn(new)

        // ── স্মার্ট অটো-রিসিঙ্ক (স্পেস মিস হ্যান্ডলিং) ──
        // ইউজার দুটো শব্দের মাঝে স্পেস দিতে ভুলে গেলে (যেমন "স্কুলেযাই"), বর্তমান শব্দের
        // "বাড়তি" অংশটা যদি ঠিক পরের টার্গেট-শব্দের শুরুর সাথে মিলে যায়, তাহলে এটা নিশ্চিতভাবেই
        // স্পেস-মিস, বানান-ভুল না। ইউজারকে থামতে/ব্যাকস্পেস দিতে বলার বদলে অ্যাপ নিজেই ঠিক
        // জায়গায় একটা স্পেস বসিয়ে দেয়, যাতে টাইপিং ফ্লো/গতি একদম অক্ষুণ্ণ থাকে আর বাকি
        // প্যাসেজের sync নষ্ট না হয়। কিন্তু এই ভুলটা মাফ করে দেওয়া হয় না — নিচের finalize
        // লুপে এই শব্দটা পরিষ্কারভাবে "ভুল" হিসেবেই গোনা হবে, accuracy-তে প্রভাব ফেলবে,
        // মিসটেক DB-তে লগ হবে, আর সেশন-শেষের রেজাল্টে (🔄 sync-loss কাউন্ট) দেখানো হবে —
        // practice-এর সততা অক্ষুণ্ণ থাকে, শুধু হাতে ব্যাকস্পেস দিতে হয় না। ন্যূনতম ২ অক্ষর
        // মিল লাগবে যাতে কাকতালীয়ভাবে ১ অক্ষর মিলে গিয়ে ভুল-পজিটিভ না হয়। ──
        var autoFixedIndex = -1
        var autoFixedRawTyped = ""
        run {
            val liveSplit = splitTypedWords(normalized)
            val wIdx = liveSplit.completed.size
            val target = passageWords.getOrNull(wIdx)
            val cur = liveSplit.current
            if (target != null && cur.length > target.length) {
                val overflow = cur.substring(target.length)
                val nextWord = passageWords.getOrNull(wIdx + 1)
                if (overflow.length >= 2 && nextWord != null && nextWord.startsWith(overflow)) {
                    autoFixedRawTyped = cur   // যা আসলে টাইপ হয়েছিল (স্পেস ছাড়া) — মিসটেক লগে এটাই যাবে
                    normalized = normalized.dropLast(cur.length) + cur.substring(0, target.length) + " " + overflow
                    autoFixedIndex = wIdx
                }
            }
        }

        val oldSplit = splitTypedWords(userInput)
        val newSplit = splitTypedWords(normalized)

        // নতুন করে "লক" হওয়া শব্দ থাকলে (আগের চেয়ে বেশি সম্পূর্ণ শব্দ) — সেগুলোই
        // একবার করে গণনা/লগ হবে, ইতিমধ্যে লক হওয়া শব্দ আর ছোঁয়া হবে না।
        if (newSplit.completed.size > frozenWordResults.size) {
            var results = frozenWordResults
            var fixedFlags = autoFixedWordFlags
            for (i in frozenWordResults.size until newSplit.completed.size) {
                val target    = passageWords.getOrNull(i) ?: break
                val typedWord = newSplit.completed[i]
                val wasAutoFixed = (i == autoFixedIndex)
                // ── স্পেস অটো-ফিক্স হলে এই শব্দটাকে সবসময় "ভুল" গোনা হয় — অক্ষর ঠিক থাকলেও
                // ইউজার স্পেসটা আসলে চাপেনি, তাই এই ভুলটা লুকানো/মাফ করা হয় না ──
                val isCorrect = !wasAutoFixed && typedWord == target

                // ── ক্যারেক্টার-ভিত্তিক গণনা (WPM/accuracy আন্তর্জাতিক সূত্র + হাত-ব্যালান্স) —
                // যা আসলে টাইপ হয়েছে (typedWord) তার ভিত্তিতেই, যাতে সংখ্যা কৃত্রিমভাবে না বাড়ে ──
                val len = maxOf(target.length, typedWord.length)
                for (j in 0 until len) {
                    totalKeystrokes++
                    val tc = target.getOrNull(j)
                    val yc = typedWord.getOrNull(j)
                    if (tc != null && tc == yc) {
                        correctKeystrokes++
                        if (HandKeyMap.isTrackable(tc)) {
                            when (HandKeyMap.handOf(tc)) {
                                Hand.LEFT  -> leftCorrectChars++
                                Hand.RIGHT -> rightCorrectChars++
                            }
                            // ── Phase ১: এই নির্দিষ্ট কী-এর জন্য "সঠিক" গণনা বাড়লো ──
                            keyStatsDelta.getOrPut(tc) { intArrayOf(0, 0) }[0]++
                        }
                    } else {
                        incorrectKeystrokes++
                        if (tc != null && HandKeyMap.isTrackable(tc)) {
                            when (HandKeyMap.handOf(tc)) {
                                Hand.LEFT  -> leftWrongChars++
                                Hand.RIGHT -> rightWrongChars++
                            }
                            // ── Phase ১: এই নির্দিষ্ট কী-এর জন্য "ভুল" গণনা বাড়লো ──
                            keyStatsDelta.getOrPut(tc) { intArrayOf(0, 0) }[1]++
                        }
                    }
                }
                if (i < passageWords.size - 1) {
                    // শব্দের পরের স্পেসটাও ১টা কী-প্রেস হিসেবে গোনা (সঠিক শব্দ মানেই স্পেসও ঠিক;
                    // অটো-ফিক্স হলে isCorrect এমনিতেই false, তাই স্পেসটাও ভুল হিসেবেই যোগ হবে)
                    totalKeystrokes++
                    if (isCorrect) correctKeystrokes++ else incorrectKeystrokes++
                }

                // ── mistake/correct লগিং — AI adaptive practice ও sync-loss ইনসাইটের ভিত্তি ──
                if (!isCorrect) {
                    // অটো-ফিক্স হওয়া শব্দের জন্য প্রকৃত (স্পেসবিহীন) টাইপড টেক্সট পাঠানো হয়,
                    // যাতে classify() ঠিকভাবে SYNC_LOSS (স্পেস-মিস) হিসেবে ধরে, আর মিসটেক DB-তেও
                    // সঠিক প্রমাণ (তুমি আসলে কী টাইপ করেছিলে) সেভ থাকে
                    val typedForAnalysis = if (wasAutoFixed) autoFixedRawTyped else typedWord
                    val errType = if (wasAutoFixed) MistakeErrorType.SYNC_LOSS
                                  else TypingErrorAnalyzer.classify(target, typedWord)
                    val collectMistakes = (sessionMode == "adaptive" && adaptivePhase == 1) ||
                        (sessionMode == "free" && selectedDifficulty == "custom")
                    if (collectMistakes && errType != MistakeErrorType.SYNC_LOSS) {
                        sessionMistakeWords = sessionMistakeWords + target
                    }
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
                    scope.launch { TypingMistakeLogger.logMistake(ctx, target, typedForAnalysis, passageLang) }
                } else {
                    scope.launch { TypingMistakeLogger.logCorrect(ctx, target, passageLang) }
                }

                results = results + isCorrect
                fixedFlags = fixedFlags + wasAutoFixed
            }
            frozenWordResults = results
            autoFixedWordFlags = fixedFlags
        } else if (newSplit.completed.size < oldSplit.completed.size) {
            // ── ব্যাকস্পেস দিয়ে আগের শব্দে ফিরে গেলে — সেই শব্দ(গুলো)-র লক তুলে নেওয়া ──
            frozenWordResults = frozenWordResults.take(newSplit.completed.size)
            autoFixedWordFlags = autoFixedWordFlags.take(newSplit.completed.size)
        }

        userInput = normalized
    }

    fun reset(newIndex: Int = passageIndex, pool: List<PassageInfo> = currentPool()) {
        val idx = if (pool.isNotEmpty()) newIndex.mod(pool.size) else 0
        passageIndex = idx
        passage      = normalizeBn(pool.getOrNull(idx)?.text ?: PASSAGES.firstOrNull()?.text ?: "")
        userInput    = ""
        frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
        isStarted    = false
        isFinished   = false
        elapsedSec   = 0
        result       = null
        correctKeystrokes   = 0
        incorrectKeystrokes = 0
        totalKeystrokes     = 0
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

    /** "🎯 দুর্বল-কী ড্রিল" বাটনে ট্যাপ করলে কল হয় — Phase ১-এর key-stat DB থেকে
     *  সবচেয়ে কম accuracy-র কী-গুলো বের করে, তারপর ইতিমধ্যে লোড হওয়া (Sheet-সোর্সড)
     *  পুল থেকে সেই কী-গুলো সবচেয়ে বেশি আছে এমন প্যাসেজটা বেছে নেয় — কোনো নতুন AI কল
     *  লাগে না, শুধু বিদ্যমান পুল থেকেই সবচেয়ে প্রাসঙ্গিক প্যাসেজ খোঁজা হয়। যথেষ্ট
     *  দুর্বল-কী ডেটা এখনো জমেনি (নতুন ইউজার) হলে সাধারণ পুল থেকেই random একটা প্যাসেজ। */
    fun startKeyDrillSession() {
        scope.launch {
            val weakChars = TypingKeyStatStore.getWeakest(ctx, sessionLanguage, minSamples = 10, limit = 6)
                .mapNotNull { it.keyChar.firstOrNull() }
            val pool = currentPool()
            val drillText = if (weakChars.isNotEmpty() && pool.isNotEmpty()) {
                pool.maxByOrNull { p -> weakChars.sumOf { c -> p.text.count { ch -> ch == c } } }?.text
            } else null

            val finalText = drillText ?: pool.randomOrNull()?.text
            if (!finalText.isNullOrBlank()) {
                reset(0, listOf(PassageInfo(finalText, "all")))
                sessionMode = "keydrill"
            }
        }
    }

    /** "🔣 চিহ্ন ও Backspace প্র্যাকটিস" বাটনে ট্যাপ করলে কল হয় — একই দুর্বল-কী লজিক,
     *  শুধু "sym" bucket (Phase ১-এর তিন-ভাগ script split থেকে) থেকে দুর্বল চিহ্ন খোঁজে,
     *  আর সাধারণ Sheet-পুলের বদলে SYMBOL_DRILL_BANK থেকে প্যাসেজ বেছে নেয় (কারণ সাধারণ
     *  কনটেন্টে চিহ্নের ঘনত্ব যথেষ্ট না) */
    fun startSymbolDrillSession() {
        scope.launch {
            val weakSymbols = TypingKeyStatStore.getWeakest(ctx, "sym", minSamples = 5, limit = 8)
                .mapNotNull { it.keyChar.firstOrNull() }
            val drillText = if (weakSymbols.isNotEmpty()) {
                SYMBOL_DRILL_BANK.maxByOrNull { s -> weakSymbols.sumOf { c -> s.count { ch -> ch == c } } }
            } else null

            val finalText = drillText ?: SYMBOL_DRILL_BANK.random()
            reset(0, listOf(PassageInfo(finalText, "all")))
            sessionMode = "symboldrill"
        }
    }

    /** "🏛️ Govt Job মক টেস্ট" শুরু বাটনে (নির্বাচিত সময়সীমাসহ) ট্যাপ করলে কল হয় —
     *  Backspace বন্ধ থাকবে (দেখো onInputChange()), ভুল হলে WPM থেকে পেনাল্টি কাটা হবে
     *  (দেখো finishSession()), নির্বাচিত সময় (৫/১০/১৫ মিনিট) শেষ না হওয়া পর্যন্ত
     *  প্যাসেজে প্যাসেজে লুপ চলতে থাকবে (দেখো "Check completion" effect)। */
    fun startGovtMockTest(minutes: Int) {
        govtMockMinutes = minutes
        govtMockPenaltyWpm = 0
        val pool = currentPool()
        reset(0, pool)
        sessionMode = "govtmock"
    }

    /** "🗝️ Adaptive Key-Unlock" বাটনে ট্যাপ করলে কল হয় — বর্তমান স্টেজ পর্যন্ত আনলক
     *  হওয়া ক্যারেক্টার দিয়ে একটা সিন্থেটিক ড্রিল-টেক্সট বানায় (দেখো
     *  CurriculumProvider.buildDrillPassage())। সেশন শেষে (finishSession()) unlock-শর্ত
     *  চেক হয়ে পরের স্টেজে এগোনো যায় কিনা দেখা হয়। */
    fun startCurriculumSession(track: String) {
        curriculumTrack = track
        scope.launch {
            curriculumStage = CurriculumProvider.getCurrentStage(ctx, track)
            curriculumProgress = CurriculumProvider.stageProgress(ctx, track, curriculumStage)
            val drillText = CurriculumProvider.buildDrillPassage(track, curriculumStage)
            if (drillText.isNotBlank()) {
                reset(0, listOf(PassageInfo(drillText, "all")))
                sessionMode = "curriculum"
            }
        }
    }

    // ── আগে হার্ডকোডেড PASSAGES তালিকা এখান থেকেই সরাসরি পড়া হতো। এখন Google Sheet-এর
    // "Typing" ট্যাব (Firebase হয়ে) থেকে asynchronously লোড হয় — এই effect স্ক্রিন খোলার
    // সাথে সাথে একবার লোড করে (RAM cache থাকলে আবার নেটওয়ার্ক কল হয় না), লোড শেষে
    // "প্র্যাকটিস" মোডে থেকে passage এখনো খালি থাকলে reset() দিয়ে বৈধ প্যাসেজ বসিয়ে দেয় ──
    LaunchedEffect(Unit) {
        ensureTypingPassagesLoaded(ctx)
        if (passage.isBlank() && sessionMode == "free" && selectedDifficulty != "custom") {
            reset(0, currentPool())
        }
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
        passage      = normalizeBn(pool.firstOrNull()?.text ?: PASSAGES.firstOrNull()?.text ?: "")
        userInput = ""; frozenWordResults = emptyList(); autoFixedWordFlags = emptyList(); isStarted = false; isFinished = false; elapsedSec = 0; result = null
        correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0
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
        passage      = normalizeBn(pool.firstOrNull()?.text ?: PASSAGES.firstOrNull()?.text ?: "")
        userInput = ""; frozenWordResults = emptyList(); autoFixedWordFlags = emptyList(); isStarted = false; isFinished = false; elapsedSec = 0; result = null
        correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0
        leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0; syncLossCount = 0
    }

    /** "⌨️ ফ্রি টাইপিং" বাটনে ট্যাপ করলে কল হয় — কোনো passage/target টেক্সট থাকে না,
     *  হার্ড কপি বই দেখে ইউজার নিজের ইচ্ছামতো টাইপ করে; শুধু স্পিড লাইভ কাউন্ট হয়,
     *  আর ইউজার নিজে "✅ শেষ করুন" চাপলে সেশন শেষ হয় (দেখো finishFreeTyping()) */
    fun startFreeTyping() {
        sessionMode = "freetyping"
        userInput = ""
        frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
        isStarted = false
        isFinished = false
        elapsedSec = 0
        result = null
        correctKeystrokes = 0
        incorrectKeystrokes = 0
        totalKeystrokes = 0
        leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0
        syncLossCount = 0
    }

    /** ফ্রি টাইপিং মোডে টেক্সট বদলালে কল হয় — কোনো target passage-এর সাথে তুলনা করা হয় না,
     *  শুধু মোট অক্ষর গোনা হয় (WPM হিসেবের জন্য) */
    fun onFreeTypingInputChange(new: String) {
        if (isFinished) return
        if (!isStarted && new.isNotEmpty()) isStarted = true
        userInput = new
        totalKeystrokes = new.length
        correctKeystrokes = new.length
    }

    /** ফ্রি টাইপিং সেশন — ইউজার নিজে "✅ শেষ করুন" চাপলে কল হয়, যেহেতু নির্দিষ্ট কোনো
     *  শেষ-বিন্দু (passage length) নেই। ফলাফল অন্য মোডগুলোর মতোই history/best-WPM-এ জমা হয় */
    fun finishFreeTyping() {
        isFinished = true
        val timeSec = elapsedSec.coerceAtLeast(1)
        val minutes = timeSec / 60.0
        val len = userInput.length
        val wpm = if (minutes > 0) (len / 5.0 / minutes).toInt() else 0
        val r = TypingResult(
            wpm = wpm, rawWpm = wpm, accuracy = 100, timeSec = timeSec,
            correctChars = len, totalChars = len
        )
        result = r
        onResult(r)
        scope.launch {
            session.recordTypingResult(r.wpm, r.rawWpm, r.accuracy, r.timeSec)
            bestWpm = maxOf(bestWpm, r.wpm)
            history = session.getTypingHistory()
            session.addTypingSecondsToday(timeSec)
            todaySecondsBefore = session.getTypingTodaySeconds()

            // ── Phase ৩: Cloud Sync ──
            session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                TypingCloudSyncService.push(phone, session.getTypingBestWpm(), session.getRawTypingHistory())
            }
        }
    }

    /** ইংরেজি ফেজ শেষে ট্রানজিশন কার্ডের বাটনে ট্যাপ করলে কল হয় — বাংলা ফেজ শুরু,
     *  সব কাউন্টার ফ্রেশ (প্রতিটা ফেজের নিজের আলাদা, স্বাধীন WPM/accuracy হবে) */
    fun startExamBanglaPhase() {
        examPhase = "bn"
        showExamPhaseTransition = false
        val pool = poolForLanguage("bn")
        passageIndex = 0
        passage      = normalizeBn(pool.firstOrNull()?.text ?: PASSAGES.firstOrNull()?.text ?: "")
        userInput = ""; frozenWordResults = emptyList(); autoFixedWordFlags = emptyList(); isStarted = false; isFinished = false; elapsedSec = 0
        correctKeystrokes = 0; incorrectKeystrokes = 0; totalKeystrokes = 0
        leftCorrectChars = 0; leftWrongChars = 0; rightCorrectChars = 0; rightWrongChars = 0; syncLossCount = 0
    }


    /** ট্রানজিশন কার্ডের CTA বাটনে ট্যাপ করলে কল হয় — phase-২ শুরু, টাইমার/স্ট্যাটস
     *  চলতেই থাকে (আলাদা রিসেট হয় না) যাতে ফাইনাল রেজাল্ট পুরো সেশনের সমন্বিত হয় */
    fun startPhase2() {
        adaptivePhase = 2
        passage = normalizeBn(phase2Passage ?: fallbackPassageFor(sessionLanguage))
        userInput = ""; frozenWordResults = emptyList(); autoFixedWordFlags = emptyList()
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
                    // ── 🎯 Focus mode — টাইপিং স্ক্রিনে থাকা মানেই ফোকাস টাইপিং-এই থাকবে,
                    // তাই আলাদা সাবজেক্ট/তারিখ বাছাইয়ের দরকার নেই — ট্যাপ করলেই সরাসরি
                    // অন/অফ টগল হয়ে যায় (কোনো কার্ড/শিট দেখানো হয় না) ──
                    if (com.hanif.smartstudy.focus.FocusModeConfig.ENABLED) {
                        IconButton(onClick = { toggleFocusMode() }) {
                            Icon(
                                Icons.Default.GpsFixed, contentDescription = "Focus mode",
                                tint = if (focusActive) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

            // ── 📜 History popup — পেছনের স্ক্রিন লক থাকে (Dialog), ভেতরে স্ক্রল করা যায়,
            // নতুন-থেকে-পুরনো (newest-first, session.getTypingHistory() নিজেই এভাবে দেয়),
            // উপরে ও নিচে Close বাটন ──
            if (showHistoryDialog) {
                Dialog(onDismissRequest = { showHistoryDialog = false }) {
                    Card(
                        Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📜 সাম্প্রতিক ফলাফল", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                                    fontFamily = NotoSansBengali)
                                TextButton(onClick = { showHistoryDialog = false }) {
                                    Text("✕ Close", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (history.isEmpty()) {
                                Text("এখনো কোনো ফলাফল নেই।", fontSize = 12.sp, fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp))
                            } else {
                                Column(
                                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    history.forEach { h ->
                                        Row(
                                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(h.date, fontSize = 12.sp, fontFamily = NotoSansBengali,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text("${h.wpm} WPM", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                                    color = Indigo600, fontFamily = NotoSansBengali)
                                                Text("${h.accuracy}%", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                    color = if (h.accuracy >= 90) GreenOk else if (h.accuracy >= 70) AmberMid else RedWrong,
                                                    fontFamily = NotoSansBengali)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showHistoryDialog = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── মোড-সিলেক্টর হেডার — টপ বারের ঠিক নিচে (কোলাপসিবল, ডিফল্ট খোলা)।
            // "History" এখন আইকন না, সরাসরি লেখা হিসেবে ভাষা সিলেক্টরের পাশে ──
            if (!isStarted) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎯 অনুশীলনের ধরন:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                        // ── 🌐 ভাষা সিলেক্টর — "প্র্যাকটিস" মোডে passage পুল এই ভাষা
                        // অনুযায়ী ফিল্টার হয় (দেখো currentPool()) ──
                        Box {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { showLangMenu = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        if (sessionLanguage == "en") "🌐 English" else "🌐 বাংলা",
                                        fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(expanded = showLangMenu, onDismissRequest = { showLangMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("🌐 বাংলা", fontFamily = NotoSansBengali) },
                                    onClick = {
                                        sessionLanguage = "bn"; showLangMenu = false
                                        if (sessionMode == "free" && selectedDifficulty != "custom") reset(0, currentPool())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🌐 English", fontFamily = NotoSansBengali) },
                                    onClick = {
                                        sessionLanguage = "en"; showLangMenu = false
                                        if (sessionMode == "free" && selectedDifficulty != "custom") reset(0, currentPool())
                                    }
                                )
                            }
                        }
                        // ── 📜 History — এখন আইকন না, সরাসরি লেখা, ভাষা সিলেক্টরের পাশে,
                        // চাপলে আগের মতোই popup/dialog খোলে ──
                        Text(
                            "History", fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { showHistoryDialog = true }
                        )
                    }
                    IconButton(
                        onClick = { modeTypeExpanded = !modeTypeExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (modeTypeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "টগল", tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AnimatedVisibility(visible = modeTypeExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "free") Indigo600 else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = {
                            sessionMode = "free"; adaptivePhase = 1
                            // ── Study Typing মোড থেকে এলে passage খালি থাকতে পারে —
                            // তখন একটা ভ্যালিড প্যাসেজ লোড করে দেওয়া নিশ্চিত করা হলো ──
                            if (passage.isBlank()) reset(0, currentPool())
                        }
                    ) {
                        Text("✍️ প্র্যাকটিস", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "free") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "freetyping") Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { startFreeTyping() }
                    ) {
                        Text("⌨️ ফ্রি টাইপিং", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "freetyping") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "adaptive") Indigo600 else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { startAdaptiveSession(sessionLanguage) }
                    ) {
                        Text("🎯 AI Adaptive", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "adaptive") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "study") Color(0xFF0891B2) else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { startStudyMode() }
                    ) {
                        Text("📚 Study Typing", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "study") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    }
                }
                // ── Neonlipi-স্টাইল নতুন মোড-বাটনগুলো (দুর্বল-কী ড্রিল, চিহ্ন ড্রিল,
                // BCC পরীক্ষা, Govt Mock, Key-unlock কারিকুলাম) — সবগুলো "🧪 Smart
                // Typing" টগলের পেছনে, Settings থেকে অন করলেই দেখা যাবে ──
                if (smartTypingEnabled) {
                // ── Phase ১: দুর্বল-কী ড্রিল — Neonlipi-এর "দুর্বলতা ধরে ধরে সারানো"
                // ফিচারের সমতুল্য, একটা আলাদা full-width রো হিসেবে (যথেষ্ট গুরুত্বপূর্ণ,
                // 2x2 গ্রিডে গুঁজে না দিয়ে) ──
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    color    = if (sessionMode == "keydrill") Color(0xFFB91C1C) else MaterialTheme.colorScheme.surfaceVariant,
                    onClick  = { startKeyDrillSession() }
                ) {
                    Text("🎯 দুর্বল-কী ড্রিল", fontSize = 12.sp, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold,
                        color = if (sessionMode == "keydrill") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                }

                // ── Phase ২: চিহ্ন ও Backspace ড্রিল + BCC পরীক্ষা — একই রো-তে দুটো বাটন ──
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "symboldrill") Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { startSymbolDrillSession() }
                    ) {
                        Text("🔣 চিহ্ন ও Backspace", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "symboldrill") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                        color    = if (sessionMode == "exam") Color(0xFF0F766E) else MaterialTheme.colorScheme.surfaceVariant,
                        onClick  = { startExamSimulation() }
                    ) {
                        Text("🏛️ BCC পরীক্ষা", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (sessionMode == "exam") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                    }
                }

                // ── Phase ২: Govt Job মক টেস্ট — শুরু করার আগেই সময়সীমা বেছে নিতে হয়
                // (Backspace বন্ধ + পেনাল্টি সিস্টেম — দেখো onInputChange()/finishSession()) ──
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C2D12).copy(alpha = 0.15f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🏛️ Govt Job মক টেস্ট (Backspace বন্ধ, ভুলে পেনাল্টি)",
                        fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { m ->
                            Surface(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                                color = if (sessionMode == "govtmock" && govtMockMinutes == m)
                                    Color(0xFF9A3412) else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { startGovtMockTest(m) }
                            ) {
                                Text("$m মিনিট", fontSize = 12.sp, fontFamily = NotoSansBengali,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sessionMode == "govtmock" && govtMockMinutes == m)
                                        Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                        }
                    }
                }

                // ── Phase ৩ (#1+#2): Adaptive Key-Unlock কারিকুলাম — ট্র্যাক বেছে নিয়ে
                // শুরু করা যায়, বর্তমান স্টেজ + নতুন ক্যারেক্টারগুলোর unlock-প্রগ্রেস
                // বার সবসময় দেখায় (Neonlipi-এর স্টেজ প্রগ্রেস বার-এর সমতুল্য) ──
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("🗝️ Adaptive Key-Unlock", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("স্টেজ $curriculumStage/${BijoyCurriculum.totalStages(curriculumTrack)}",
                            fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("bn" to "বাংলা", "en" to "ইংরেজি").forEach { (trk, label) ->
                            Surface(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                                color = if (curriculumTrack == trk) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { startCurriculumSession(trk) }
                            ) {
                                Text(label, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                                    color = if (curriculumTrack == trk) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                        }
                    }
                    if (curriculumProgress.isNotEmpty()) {
                        Text(
                            "এই স্টেজের নতুন কী-গুলো:",
                            fontSize = 10.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        curriculumProgress.forEach { (ch, progress) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(ch.toString(), fontSize = 13.sp, fontFamily = NotoSansBengali,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                LinearProgressIndicator(
                                    progress = { progress.toFloat() / CurriculumProvider.UNLOCK_MIN_CORRECT },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                                Text("$progress/${CurriculumProvider.UNLOCK_MIN_CORRECT}", fontSize = 9.sp,
                                    fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                } // ← if (smartTypingEnabled)

                if (sessionMode == "study") {
                    // ── সাবজেক্ট চিপ ──
                    if (studySubjectsLoading) {
                        Text("⏳ বিষয় লোড হচ্ছে...", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (studySubjectList.isNotEmpty()) {
                        Text("বিষয় বেছে নাও:", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            studySubjectList.forEach { subj ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (studySubject == subj) Color(0xFF0891B2) else MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = { loadStudySubTopics(subj) }
                                ) {
                                    Text(subj, fontSize = 12.sp, fontFamily = NotoSansBengali,
                                        color = if (studySubject == subj) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                    } else {
                        // ── আগে এই অবস্থায়ও "⏳ বিষয় লোড হচ্ছে..." দেখাতো — লোডিং আসলে
                        // শেষ হয়ে গেলেও, কারণ "খালি লিস্ট" আর "এখনো লোড হচ্ছে" আলাদা করা
                        // যেত না। এখন লোড শেষেও ক্যাশে কিছু না পেলে স্পষ্ট বার্তা + রিট্রাই ──
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "📭 ক্যাশে কোনো Study কনটেন্ট পাওয়া যায়নি — ইন্টারনেট কানেকশন চেক করে আবার চেষ্টা করো, অথবা আগে একবার 📚 Study সেকশন খুলে কনটেন্ট সিঙ্ক করে নাও।",
                                fontSize = 11.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp
                            )
                            OutlinedButton(onClick = { startStudyMode() }, shape = RoundedCornerShape(10.dp)) {
                                Text("🔄 আবার চেষ্টা করো", fontFamily = NotoSansBengali, fontSize = 12.sp)
                            }
                        }
                    }

                    // ── সাব-টপিক চিপ ──
                    if (studySubject != null && studySubTopicsLoading) {
                        Text("⏳ সাব-টপিক লোড হচ্ছে...", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (studySubject != null && studySubTopicList.isNotEmpty()) {
                        Text("সাব-টপিক বেছে নাও:", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            studySubTopicList.forEach { st ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (studySubTopic == st) Color(0xFF0E7490) else MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = { studySubTopic = st; loadStudyPool(studySubject!!, st) }
                                ) {
                                    Text(st, fontSize = 12.sp, fontFamily = NotoSansBengali,
                                        color = if (studySubTopic == st) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                    } else if (studySubject != null) {
                        Text("এই বিষয়ে কোনো সাব-টপিক পাওয়া যায়নি।", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // ── প্রগ্রেস লেবেল, যেমন "কারক: ৮/১০ শেষ" ──
                    if (studySubTopic != null && studyPoolTotal > 0) {
                        Text("$studySubTopic: $studyPoolUsed/$studyPoolTotal শেষ",
                            fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            color = Color(0xFF0891B2))
                    }

                    // ── পুরো টপিক শেষ হয়ে গেলে — রিসেট বাটন ──
                    if (studyExhausted && studySubTopic != null) {
                        Card(
                            Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "✅ \"$studySubTopic\"-এর সব প্যাসেজ টাইপ করা হয়ে গেছে — অন্য টপিক বেছে নাও, অথবা এই টপিক রিসেট করো।",
                                    fontSize = 12.sp, fontFamily = NotoSansBengali, color = GreenOk, fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp), color = GreenOk,
                                    onClick = { resetCurrentStudySubTopic() }
                                ) {
                                    Text("🔄 এই টপিক রিসেট করো", fontSize = 12.sp, fontFamily = NotoSansBengali,
                                        fontWeight = FontWeight.Bold, color = Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                    } else if (studyLoading) {
                        Text("⏳ প্যাসেজ লোড হচ্ছে...", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (studySubTopic != null && !studyExhausted && studyPoolTotal == 0) {
                        // ── লোড শেষ, কিন্তু এই সাব-টপিকে টাইপযোগ্য কোনো কনটেন্টই নেই
                        // (audience filter-এ বাদ পড়েছে বা প্রশ্ন/ব্যাখ্যা/টেকনিক সব খালি) —
                        // আগে এখানে কিছুই দেখাতো না, খালি স্ক্রিন দেখে "আটকে আছে" মনে হতো ──
                        Text(
                            "😕 এই সাব-টপিকে টাইপ করার মতো কোনো কনটেন্ট পাওয়া যায়নি — অন্য একটা সাব-টপিক বেছে নাও।",
                            fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                } // Column
                } // AnimatedVisibility(modeTypeExpanded)
            }

            // ── ধাপ ৪: Daily Discipline Mode ব্যানার — ইউজারের অনুরোধে স্ক্রিনে
            // দেখানো বন্ধ করা হলো (হাইড)। todaySecondsBefore/dailyGoalMin ট্র্যাকিং
            // লজিক অপরিবর্তিত রইলো (অন্য জায়গায় ব্যবহার হয়), শুধু এই ব্যানারটাই আর
            // রেন্ডার হয় না — ফলে এর জায়গায় নিচের অংশ (AI-fetching/Stats/প্যাসেজ/
            // রেস বাটন ইত্যাদি) স্বয়ংক্রিয়ভাবে উপরে উঠে আসে ──
            // if (disciplineOn) {
            //     DailyGoalBanner(todaySeconds = todaySecondsBefore, goalMinutes = dailyGoalMin)
            // }

            // ── নিজের প্যাসেজ একবার শেষ, এখন এই সেশনের ভুল থেকে AI দিয়ে পরের
            // প্যাসেজ তৈরি হচ্ছে — সংক্ষিপ্ত সময়ের জন্য দেখানো হয় ──
            if (customAiFetching) {
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF3E8FF)
                ) {
                    Text(
                        "🤖 তোমার ভুল থেকে AI নতুন প্যাসেজ বানাচ্ছে...",
                        Modifier.padding(12.dp), fontSize = 12.sp, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED)
                    )
                }
            }

            // ── Study Typing মোডে যতক্ষণ না subject/sub_topic বেছে একটা প্যাসেজ লোড
            // হয় (বা টপিক exhausted), ততক্ষণ Stats/Passage/Input বক্স দেখানোর দরকার নেই ──
            if (sessionMode != "study" || passage.isNotBlank()) {
            // Stats row
            // ── প্রগ্রেস বার/স্ট্যাট দেখানোর জন্য কতটুকু প্যাসেজ char হিসেবে "রিজলভড" —
            // লক হওয়া শব্দগুলোর দৈর্ঘ্য (+ মাঝের স্পেস) + এখন চলমান শব্দের দৈর্ঘ্য ──
            val resolvedCount = remember(userInput, passageWords, sessionMode) {
                if (sessionMode == "freetyping") {
                    userInput.length
                } else {
                    val split = splitTypedWords(userInput)
                    var total = 0
                    for (i in split.completed.indices) {
                        total += (passageWords.getOrNull(i)?.length ?: 0)
                        if (i < passageWords.size - 1) total += 1
                    }
                    total + split.current.length
                }
            }
            StatsRow(
                elapsedSec        = elapsedSec,
                resolvedCount     = resolvedCount,
                passage           = passage,
                isStarted         = isStarted,
                correctKeystrokes = correctKeystrokes,
                freeTypingMode    = sessionMode == "freetyping"
            )

            // ── Live next-key হাইলাইট কীবোর্ডের জন্য — এখন ঠিক কোন ক্যারেক্টার টাইপ
            // করার কথা সেটা বের করা হয় (দেখো FingerKeyboardDiagram.kt-এর
            // LiveKeyHighlightKeyboard)। ফ্রি-টাইপিং মোডে কোনো target passage
            // থাকে না, তাই সেখানে "পরের কী" বলে কিছু নেই ──
            val nextTypeChar: Char? = remember(userInput, passageWords, frozenWordResults, sessionMode) {
                if (sessionMode == "freetyping" || passageWords.isEmpty()) null
                else {
                    val liveSplit = splitTypedWords(userInput)
                    val wIdx = frozenWordResults.size
                    val word = passageWords.getOrNull(wIdx)
                    when {
                        word == null -> null
                        liveSplit.current.length < word.length -> word[liveSplit.current.length]
                        wIdx < passageWords.size - 1 -> ' '   // শব্দ শেষ — এখন স্পেস চাপার পালা
                        else -> null                           // পুরো প্যাসেজ শেষ
                    }
                }
            }

            // ── Sheet থেকে প্যাসেজ পুল এখনো লোড না হলে (নেট নেই/প্রথমবার) — ব্যবহারকারীকে
            // জানানো, নাহলে খালি স্ক্রিন দেখে "আটকে আছে" মনে হতে পারে ──
            if (passage.isBlank() && sessionMode !in listOf("freetyping", "study")) {
                Text(
                    "⏳ প্যাসেজ লোড হচ্ছে... (না এলে ইন্টারনেট সংযোগ চেক করুন)",
                    fontSize = 12.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // ── Passage display — "ফ্রি টাইপিং" মোডে কোনো passage/target টেক্সট দেখানো হয় না,
            // ইউজার হার্ড কপি বই দেখে নিজের ইচ্ছামতো নিচের ফাঁকা বক্সে টাইপ করে ──
            if (sessionMode != "freetyping") {
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
                            when {
                                sessionMode == "study" -> "📚 ${studySubject ?: ""} · ${studySubTopic ?: ""}"
                                sessionMode == "adaptive" && adaptivePhase == 2 -> "🎯 তোমার জন্য বিশেষভাবে তৈরি"
                                else -> "📖 Passage"
                            },
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
                        // ── "আমার প্যাসেজ" ফিচার — নিজের যোগ করা প্যাসেজ এখান থেকেই
                        // ম্যানেজ/বাছাই করা যায় (+ আইকনে চাপুন) ──
                        if (sessionMode == "free") {
                            IconButton(
                                onClick = { showCustomPassageManager = true },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "আমার প্যাসেজ",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                        // ── কঠিনতার স্তর বাজ (সহজ/মাঝারি/কঠিন) এখন আর দেখানো হয় না —
                        // পুরোপুরি রিমুভ করা হলো, ব্যবহারকারীর ইনপুট/সিলেকশনের দরকার নেই ──
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // ── Colored passage — word-by-word (selftyping.com/10fastfingers-এর
                    // স্ট্যান্ডার্ড পদ্ধতি): প্রতিটা "লক" হওয়া শব্দ একটাই রঙ পায় (পুরো
                    // শব্দ সঠিক হলে সবুজ, নাহলে লাল) — একটা শব্দে ভুল হলে আগে/পরের অন্য
                    // কোনো শব্দ প্রভাবিত হয় না। বর্তমান (এখনো টাইপ করা হচ্ছে) শব্দে
                    // লাইভ ক্যারেক্টার-বাই-ক্যারেক্টার ফিডব্যাক দেখানো হয়। ──
                    val split = remember(userInput) { splitTypedWords(userInput) }
                    // ── ফিক্সড-হাইট viewport (~৪ লাইন) — লম্বা প্যাসেজেও Card unbounded
                    // বাড়ে না, ফলে নিচের ইনপুট বক্স স্ক্রিনের বাইরে চলে যায় না ──
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(passageScrollState)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                passageWords.forEachIndexed { i, word ->
                                    if (i < frozenWordResults.size) {
                                        // ── locked word — পুরো শব্দ সবুজ/লাল/অ্যাম্বার (অটো-ফিক্সড স্পেস) ──
                                        val wasAutoFixed = autoFixedWordFlags.getOrNull(i) == true
                                        val isOk = frozenWordResults[i]
                                        val wordColor = when {
                                            wasAutoFixed -> AmberWarn  // স্পেস মিস — অ্যাম্বার রঙ
                                            isOk -> GreenOk
                                            else -> RedWrong
                                        }
                                        withStyle(SpanStyle(color = wordColor, fontWeight = FontWeight.Bold)) {
                                            append(word)
                                        }
                                    } else if (i == frozenWordResults.size) {
                                        // ── current word — লাইভ টাইপ করার সময় char-by-char ফিডব্যাক + নীল ব্যাকগ্রাউন্ড ──
                                        val curTyped = split.current
                                        val maxLen = maxOf(word.length, curTyped.length)
                                        for (j in 0 until maxLen) {
                                            val tc = word.getOrNull(j)
                                            val yc = curTyped.getOrNull(j)
                                            when {
                                                yc == null -> {
                                                    // এখনো টাইপ করা হয়নি — মূল অক্ষর
                                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) {
                                                        append(tc.toString())
                                                    }
                                                }
                                                tc == null -> {
                                                    // বাড়তি টাইপড অক্ষর — লাল ব্যাকগ্রাউন্ড সহ
                                                    withStyle(SpanStyle(color = RedWrong, fontWeight = FontWeight.Bold, background = Color(0xFFFEE2E2))) {
                                                        append(yc.toString())
                                                    }
                                                }
                                                tc == yc -> {
                                                    // সঠিক টাইপড অক্ষর — সবুজ
                                                    withStyle(SpanStyle(color = GreenOk, fontWeight = FontWeight.Bold)) {
                                                        append(tc.toString())
                                                    }
                                                }
                                                else -> {
                                                    // ভুল টাইপড অক্ষর — লাল
                                                    withStyle(SpanStyle(color = RedWrong, fontWeight = FontWeight.Bold, background = Color(0xFFFEE2E2))) {
                                                        append(tc.toString())
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // ── upcoming word — এখনো পর্যন্ত পৌঁছায়নি ──
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                            append(word)
                                        }
                                    }
                                    if (i < passageWords.size - 1) append(" ")
                                }
                            },
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            fontFamily = NotoSansBengali,
                            onTextLayout = { passageTextLayout = it }
                        )
                    }
                }
            }
            } // if (sessionMode != "freetyping")

            // Input field
            OutlinedTextField(
                value         = userInput,
                onValueChange = { if (sessionMode == "freetyping") onFreeTypingInputChange(it) else onInputChange(it) },
                enabled       = !isFinished,
                placeholder   = {
                    Text(
                        if (sessionMode == "freetyping") "বই দেখে এখানে নিজের ইচ্ছামতো টাইপ করুন..."
                        else "টাইপ শুরু করুন...",
                        fontFamily = NotoSansBengali
                    )
                },
                modifier      = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                shape         = RoundedCornerShape(12.dp),
                textStyle     = LocalTextStyle.current.copy(fontSize = 16.sp, fontFamily = NotoSansBengali),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Indigo600,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction      = ImeAction.Done
                )
            )

            // ── Phase ২: Action buttons row — Reset, Submit Now, Race (যদি উপলব্ধ থাকে) ──
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reset button — এখন ফ্রি টাইপিং সহ যেকোনো মোডেই কাজ করে
                OutlinedButton(
                    onClick  = {
                        if (sessionMode == "freetyping") startFreeTyping()
                        else reset((passageIndex + 1).mod(currentPool().size.coerceAtLeast(1)))
                    },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("রিসেট", fontFamily = NotoSansBengali, fontSize = 12.sp)
                }

                // ── "📤 Submit Now" বাটন — ইউজারের টাইপ করা শেষ মনে হলে টাইমার শেষ হওয়ার
                // আগেই সেশন জমাদান। এখন ফ্রি টাইপিং, Exam, Adaptive ও ফ্রি সেশন —
                // সব মোডেই যথাযথ হুক কল করে সঙ্গে সঙ্গে জমা নেয় (as usual results) ──
                if (isStarted && !isFinished) {
                    Button(
                        onClick = {
                            when (sessionMode) {
                                "freetyping" -> finishFreeTyping()
                                "exam"       -> finishExamPhase()
                                else         -> finishSession()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("জমা দিন", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color.White)
                    }
                }

                // ── 🏎️ Typing Race বাটন — TypingPracticeScreen-এর মধ্যেও সহজ একসেস ──
                OutlinedButton(
                    onClick  = onOpenRace,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Indigo600)
                ) {
                    Text("🏎️ রেস", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Phase ৩ (#3): প্রোফাইল/Roadmap/আঙুল-পজিশন বোতাম row — Neonlipi-স্টাইল
            // (সবগুলো "🧪 Smart Typing" টগলের পেছনে, Settings থেকে অন করলেই দেখা যাবে) ──
            if (smartTypingEnabled) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    onClick  = { showRoadmapWizard = true }
                ) {
                    Text("🗺️ রোডম্যাপ", fontSize = 11.sp, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp))
                }
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    onClick  = { showProfileDialog = true }
                ) {
                    Text("👤 প্রোফাইল & সিঙ্ক", fontSize = 11.sp, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp))
                }
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    onClick  = { showFingerDialog = true }
                ) {
                    Text("🖐️ আঙুল গাইড", fontSize = 11.sp, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp))
                }
            }
            } // ← if (smartTypingEnabled)

            // ── Phase ৩: Roadmap Wizard Dialog ──
            if (showRoadmapWizard) {
                RoadmapWizardDialog(
                    onDismiss = { showRoadmapWizard = false },
                    onComplete = { plan ->
                        roadmapPlan = plan
                        showRoadmapWizard = false
                    }
                )
            }

            // ── Phase ৩: Profile & Sync Dialog ──
            if (showProfileDialog) {
                TypingProfileDialog(onDismiss = { showProfileDialog = false })
            }

            // ── Phase ৩: Finger Placement Guide Dialog ──
            if (showFingerDialog) {
                FingerPlacementDialog(onDismiss = { showFingerDialog = false })
            }

            // ── Phase ৩ (#1+#2): Key-unlock কারিকুলাম — সদ্য আনলক হওয়া স্টেজের সেলিব্রেশন UI ──
            justUnlockedStage?.let { stage ->
                AlertDialog(
                    onDismissRequest = { justUnlockedStage = null },
                    confirmButton = {
                        Button(onClick = { justUnlockedStage = null }) {
                            Text("চালিয়ে যান", fontFamily = NotoSansBengali)
                        }
                    },
                    title = { Text("🎉 অভিনন্দন! নতুন স্টেজ আনলক!", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    text  = { Text("তুমি সফলভাবে স্টেজ ${stage - 1} শেষ করে স্টেজ $stage-এ পৌঁছেছ। নতুন ক্যারেক্টার অনুশীলনের জন্য প্রস্তুত!", fontFamily = NotoSansBengali) }
                )
            }

            // ── Phase ১: Key Accuracy Heatmap Card — ইউজারের টাইপিং দুর্বলতা স্পষ্ট
            // করার জন্য live visual feedback (সব "🧪 Smart Typing" টগলের পেছনে) ──
            if (smartTypingEnabled && keyHeatmap.isNotEmpty()) {
                KeyHeatmapCard(stats = keyHeatmap, language = sessionLanguage)
            }

            // ── Transition dialogs ──
            if (showPhaseTransition && phase2Passage != null) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {
                        Button(onClick = { startPhase2() }) {
                            Text("Phase ২ শুরু করুন (AI Targeted)", fontFamily = NotoSansBengali)
                        }
                    },
                    title = { Text("🎯 Phase ১ শেষ!", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    text  = {
                        Text(
                            "তোমার প্রথম ৩ মিনিটের পারফরম্যান্স বিশ্লেষণ করে AI একটি বিশেষ প্যাসেজ তৈরি করেছে। এটি টাইপ করলে তোমার দুর্বল অক্ষর ও শব্দগুলোতে গতি বাড়বে।",
                            fontFamily = NotoSansBengali
                        )
                    }
                )
            }

            if (showExamPhaseTransition) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {
                        Button(onClick = { startExamBanglaPhase() }) {
                            Text("বাংলা অংশ শুরু করুন (১০ মিনিট)", fontFamily = NotoSansBengali)
                        }
                    },
                    title = { Text("🏛️ ইংরেজি পরীক্ষা সম্পূর্ণ!", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    text  = {
                        val enWpm = examEnglishResult?.wpm ?: 0
                        val enAcc = examEnglishResult?.accuracy ?: 0
                        Text(
                            "ইংরেজি টাইপিং ফলাফল: $enWpm WPM (Accuracy: $enAcc%)\n\nএখন বাংলা অংশ শুরু হবে। প্রস্তুত হয়ে বোতামে চাপ দিন।",
                            fontFamily = NotoSansBengali
                        )
                    }
                )
            }

            // ── Result cards ──
            if (sessionMode == "exam" && examEnglishResult != null && examBanglaResult != null) {
                ExamResultCard(
                    englishResult = examEnglishResult!!,
                    banglaResult  = examBanglaResult!!,
                    onRestart     = { startExamSimulation() }
                )
            } else if (result != null) {
                ResultCard(
                    result     = result!!,
                    bestWpm    = bestWpm,
                    onNext     = { reset((passageIndex + 1).mod(currentPool().size.coerceAtLeast(1))) },
                    sessionMode = sessionMode,
                    govtMockPenaltyWpm = govtMockPenaltyWpm
                )
            }
            } // if (sessionMode != "study" || passage.isNotBlank())
        }
    }
}

// ── Daily Discipline Banner UI Component ──
@Composable
private fun DailyGoalBanner(todaySeconds: Int, goalMinutes: Int) {
    val goalSeconds = (goalMinutes * 60).coerceAtLeast(1)
    val progress = (todaySeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
    val todayMinutes = todaySeconds / 60
    val isGoalMet = todaySeconds >= goalSeconds

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoalMet) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isGoalMet) "🎉 আজকের টাইপিং লক্ষ্য পূর্ণ হয়েছে!" else "⏱️ আজকের টাইপিং সময়",
                    fontSize = 11.sp,
                    fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold,
                    color = if (isGoalMet) GreenOk else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$todayMinutes / $goalMinutes মি.",
                    fontSize = 11.sp,
                    fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold,
                    color = Indigo600
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (isGoalMet) GreenOk else Indigo600,
                trackColor = Color(0xFFE2E8F0)
            )
        }
    }
}

// ── Key Heatmap Visualizer ──
@Composable
private fun KeyHeatmapCard(stats: List<TypingKeyStatEntity>, language: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔥 কী-নিখুঁততা হিটম্যাপ (${if (language == "bn") "বাংলা" else "English"})",
                    fontSize = 11.sp,
                    fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "সবুজ = ৯%+, লাল = দুর্বল",
                    fontSize = 9.sp,
                    fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top 16 keys sorted by usage
            val topKeys = remember(stats) { stats.sortedByDescending { it.correctCount + it.wrongCount }.take(16) }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                topKeys.forEach { stat ->
                    val total = stat.correctCount + stat.wrongCount
                    val acc = if (total > 0) stat.correctCount * 100 / total else 0
                    val bgColor = when {
                        total < 5 -> Color(0xFFE2E8F0)
                        acc >= 90 -> Color(0xFFDCFCE7)
                        acc >= 75 -> Color(0xFFFEF3C7)
                        else      -> Color(0xFFFEE2E2)
                    }
                    val textColor = when {
                        total < 5 -> Color(0xFF64748B)
                        acc >= 90 -> Color(0xFF166534)
                        acc >= 75 -> Color(0xFF92400E)
                        else      -> Color(0xFF991B1B)
                    }

                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stat.keyChar,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontFamily = NotoSansBengali
                            )
                            if (total >= 5) {
                                Text(
                                    text = "$acc%",
                                    fontSize = 8.sp,
                                    color = textColor,
                                    fontFamily = NotoSansBengali
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Exam Dual Result Display ──
@Composable
private fun ExamResultCard(
    englishResult: TypingResult,
    banglaResult : TypingResult,
    onRestart    : () -> Unit
) {
    val totalWpm = (englishResult.wpm + banglaResult.wpm) / 2
    val totalAcc = (englishResult.accuracy + banglaResult.accuracy) / 2
    val passed   = englishResult.wpm >= 30 && banglaResult.wpm >= 20 && totalAcc >= 85

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (passed) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (passed) GreenOk else RedWrong)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (passed) "🎉 BCC পরীক্ষায় উত্তীর্ণ!" else "❌ আরও অনুশীলন প্রয়োজন",
                    fontSize = 16.sp,
                    fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (passed) GreenOk else RedWrong
                )
                Text(
                    text = "গড়: $totalWpm WPM",
                    fontSize = 14.sp,
                    fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold,
                    color = Indigo600
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // English Box
                Card(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🌐 English", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                        Text("${englishResult.wpm} WPM", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Indigo600)
                        Text("নিখুঁততা: ${englishResult.accuracy}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        Text("লক্ষ্য: ৩০ WPM", fontSize = 9.sp, color = if (englishResult.wpm >= 30) GreenOk else RedWrong, fontFamily = NotoSansBengali)
                    }
                }

                // Bangla Box
                Card(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🇧🇩 বাংলা", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                        Text("${banglaResult.wpm} WPM", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Indigo600)
                        Text("নিখুঁততা: ${banglaResult.accuracy}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        Text("লক্ষ্য: ২০ WPM", fontSize = 9.sp, color = if (banglaResult.wpm >= 20) GreenOk else RedWrong, fontFamily = NotoSansBengali)
                    }
                }
            }

            Text(
                text = "BCC স্ট্যান্ডার্ড: ইংরেজিতে ৩০ WPM, বাংলায় ২০ WPM এবং ৮৫%+ নিখুঁততা আবশ্যক।",
                fontSize = 10.sp,
                fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                Text("পুনরায় পরীক্ষা দিন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Phase ৩: Profile & Sync Dialog ──
@Composable
private fun TypingProfileDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionManager(ctx) }
    val scope = rememberCoroutineScope()

    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("👤 টাইপিং প্রোফাইল & ক্লাউড সিঙ্ক", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)

                val user = session.getCurrentUser()
                Text("ব্যবহারকারী: ${user?.phone?.ifBlank { "Guest User" } ?: "Guest User"}", fontSize = 12.sp, fontFamily = NotoSansBengali)
                Text("সর্বোচ্চ গতি (Best WPM): ${session.getTypingBestWpm()}", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Indigo600)
                Text("মোট সেশন সম্পন্ন: ${session.getTypingHistory().size}", fontSize = 12.sp, fontFamily = NotoSansBengali)

                syncMessage?.let {
                    Text(it, fontSize = 11.sp, fontFamily = NotoSansBengali, color = GreenOk)
                }

                Button(
                    onClick = {
                        val phone = user?.phone?.takeIf { it.isNotBlank() }
                        if (phone == null) {
                            syncMessage = "❌ লগইন করা নেই। অতিথিদের ক্লাউড সিঙ্ক উপলব্ধ নয়।"
                            return@Button
                        }
                        isSyncing = true
                        scope.launch {
                            TypingCloudSyncService.push(phone, session.getTypingBestWpm(), session.getRawTypingHistory())
                            val pullResult = TypingCloudSyncService.pull(phone)
                            if (pullResult != null) {
                                session.mergeTypingCloudSnapshot(pullResult.bestWpm, pullResult.history)
                            }
                            isSyncing = false
                            syncMessage = if (pullResult != null) "✅ ক্লাউড সিঙ্ক সফল হয়েছে!" else "❌ সিঙ্ক করতে ব্যর্থ হয়েছে।"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("☁️ এখনই সিঙ্ক করুন (Manual Sync)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("বন্ধ করুন", fontFamily = NotoSansBengali)
                }
            }
        }
    }
}

// ── Phase ৩: Finger Placement Guide Dialog ──
@Composable
private fun FingerPlacementDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🖐️ আদর্শ আঙুল পজিশনিং গাইড", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("১. Home Row Position:", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = NotoSansBengali)
                    Text("• বাম হাত: A, S, D, F (কানি আঙুল থেকে তর্জনী)", fontSize = 11.sp, fontFamily = NotoSansBengali)
                    Text("• ডান হাত: J, K, L, ; (তর্জনী থেকে কানি আঙুল)", fontSize = 11.sp, fontFamily = NotoSansBengali)
                    Text("• বৃদ্ধা আঙুল: Spacebar-এর জন্য নির্ধারিত।", fontSize = 11.sp, fontFamily = NotoSansBengali)

                    Spacer(Modifier.height(4.dp))
                    Text("২. বিজয় বাংলা কীবোর্ড হোম-কী:", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = NotoSansBengali)
                    Text("• F কী = ি / ী (Shift)", fontSize = 11.sp, fontFamily = NotoSansBengali)
                    Text("• J কী = হ / ঃ (Shift)", fontSize = 11.sp, fontFamily = NotoSansBengali)
                    Text("• G কী (যুক্তবর্ণ লিঙ্কার) = বাম তর্জনী প্রসারিত করে চাপতে হয়।", fontSize = 11.sp, fontFamily = NotoSansBengali)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("বুঝতে পেরেছি", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── StatsRow Component ──
@Composable
private fun StatsRow(
    elapsedSec: Int,
    resolvedCount: Int,
    passage: String,
    isStarted: Boolean,
    correctKeystrokes: Int,
    freeTypingMode: Boolean
) {
    val timeMin = elapsedSec / 60.0
    val liveWpm = if (timeMin > 0) (correctKeystrokes / 5.0 / timeMin).toInt() else 0
    val progress = if (freeTypingMode || passage.isEmpty()) 0f else (resolvedCount.toFloat() / passage.length).coerceIn(0f, 1f)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Live WPM Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Indigo600.copy(alpha = 0.1f))
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ ", fontSize = 14.sp)
                Text("$liveWpm ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Indigo600, fontFamily = NotoSansBengali)
                Text("WPM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Indigo600, fontFamily = NotoSansBengali)
            }
        }

        // Timer Display
        val mins = elapsedSec / 60
        val secs = elapsedSec % 60
        Text(
            text = String.format("%02d:%02d", mins, secs),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = NotoSansBengali
        )
    }

    if (!freeTypingMode && passage.isNotEmpty()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Indigo600,
            trackColor = Color(0xFFE2E8F0)
        )
    }
}

// ── ResultCard Component ──
@Composable
private fun ResultCard(
    result: TypingResult,
    bestWpm: Int,
    onNext: () -> Unit,
    sessionMode: String,
    govtMockPenaltyWpm: Int = 0
) {
    val isNewRecord = result.wpm > bestWpm && bestWpm > 0

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📊 সেশন ফলাফল", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                if (isNewRecord) {
                    Text("🎉 নতুন রেকর্ড!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenOk, fontFamily = NotoSansBengali)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Net WPM
                Card(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Indigo600.copy(alpha = 0.05f))
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Net Speed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        Text("${result.wpm}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Indigo600)
                        Text("WPM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Indigo600)
                    }
                }

                // Accuracy
                Card(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenOk.copy(alpha = 0.05f))
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Accuracy", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        Text("${result.accuracy}%", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = GreenOk)
                        Text("নিখুঁততা", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenOk, fontFamily = NotoSansBengali)
                    }
                }

                // Time
                Card(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Time", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        Text("${result.timeSec}s", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("সময়", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    }
                }
            }

            if (sessionMode == "govtmock" && govtMockPenaltyWpm > 0) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏛️ Govt Mock Penalty Applied:", fontSize = 11.sp, fontFamily = NotoSansBengali, color = RedWrong)
                        Text("-$govtMockPenaltyWpm WPM (ভুল কী-প্রেসের কারণে)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedWrong, fontFamily = NotoSansBengali)
                    }
                }
            }

            if (result.syncLossCount > 0) {
                Text(
                    "🔄 এই সেশনে $result.syncLossCount বার স্পেস বা ট্র্যাকিং মিস হয়েছে।",
                    fontSize = 11.sp,
                    fontFamily = NotoSansBengali,
                    color = AmberWarn
                )
            }

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                Text("পরবর্তী প্যাসেজ ➔", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        }
    }
}
