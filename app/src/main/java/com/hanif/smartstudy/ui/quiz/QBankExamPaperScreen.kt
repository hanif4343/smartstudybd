package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.ui.shared.ReportDialog
import com.hanif.smartstudy.util.TtsManager
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

// ── QBank "পদবী → প্রতিষ্ঠান → প্রশ্নপত্র" (৩য় লেয়ার) স্ক্রিন — এক্সাম-পেপার স্টাইল।
// ব্যবহারকারীর দেওয়া HTML মকআপ অনুযায়ী তৈরি: সাদা পেপার ব্যাকগ্রাউন্ড, গাঢ়
// হেডার (প্রতিষ্ঠান + পদবী নাম), সাবজেক্ট ট্যাব (টপিক দেখানো হয় না — শুধু সাবজেক্ট),
// প্রতিটা প্রশ্ন সরাসরি উত্তর-সহ (এটা একটা রেফারেন্স/আনসার-কী রিভিউ স্ক্রিন, লাইভ
// কুইজ-খেলা স্ক্রিন না — তাই MCQ অপশন বাটন না দেখিয়ে সরাসরি সঠিক উত্তর দেখানো হয়)।
// একই groupId-এর multi-part প্রশ্ন (ক/খ/গ/ঘ/ঙ) একটা কার্ডে একসাথে দেখানো হয়। ──

private val PaperBg      = Color.White                // লিখিত প্রশ্নের স্ক্রিন — সাদা ব্যাকগ্রাউন্ড
private val HeaderTop    = Color(0xFF0891B2)   // অ্যাপের QBank থিম-রঙ (SubjectListScreen-এর গ্র্যাডিয়েন্টের সাথে ম্যাচ)
private val HeaderBottom = Color(0xFF0E7490)
private val HeaderBg     = HeaderTop           // solid fallback যেখানে gradient দরকার নেই
private val HeaderCream  = Color(0xFFF7F3E9)
private val HeaderSub    = Color(0xFFCFEBF0)
private val GoldAccent   = Color(0xFFC9A24B)
private val TabInactiveBg= Color(0xFFF1F5F9)
private val TabBorder    = Color(0xFFE2E8F0)
private val TextMain     = Color(0xFF221F1A)
private val TextMuted    = Color(0xFF6B6552)
private val AnswerBg     = Color(0xFFEAF1E4)
private val AnswerText   = Color(0xFF2C4728)
private val DashedLine   = Color(0xFFE2E8F0)

// ── PAPER COMPOSER রেন্ডারিং হেল্পার ──
// "highlight" ফরম্যাটে Admin App-এ টেক্সট সিলেক্ট করে "🖍 হাইলাইট করো" চাপলে question-এ
// __word__ মার্কআপ বসে (দেখো QuizModels.kt-এর formatStyle কমেন্ট) — এখানে সেটা পার্স
// করে বোল্ড+আন্ডারলাইন+রঙিন করে দেখানো হয় (যেমন কারক নির্ণয়ে নির্দিষ্ট শব্দ চিহ্নিত করা)।
private val HIGHLIGHT_REGEX = Regex("__(.+?)__")
private fun buildHighlightedText(raw: String): AnnotatedString = buildAnnotatedString {
    var lastEnd = 0
    for (m in HIGHLIGHT_REGEX.findAll(raw)) {
        append(raw.substring(lastEnd, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = HeaderTop, textDecoration = TextDecoration.Underline)) {
            append(m.groupValues[1])
        }
        lastEnd = m.range.last + 1
    }
    append(raw.substring(lastEnd))
}

// "fillblank" ফরম্যাটে (ইংরেজি) — Admin App-এর PaperComposer-এ 🖍 "Blank মার্ক করো" বাটনে
// যে শব্দটা সিলেক্ট করা হয় সেটাই __word__ মার্কআপ হয়ে question-এর ভিতরে থেকে যায় (মুছে
// যায় না — দেখো uploaderUtils.js buildSheetRow-এর কমেন্ট), আর সেই একই টেক্সট আলাদাভাবে
// answer/correct কলামেও (extract করে) সেভ হয়। তাই এখানে রেন্ডার করার সময় প্রশ্নের ভিতরের
// __..__ মার্ক করা অংশটাই সরিয়ে সেই জায়গায় answer টা বোল্ড+আন্ডারলাইন করে বসানো হয়।
// 🐛 ফিক্স: আগে এখানে শুধু "____"/"...."/"……" style literal blank placeholder খোঁজা হতো
// (BLANK_REGEX) — কিন্তু Admin App আসলে __word__ মার্কআপ পাঠায়, যেটাতেও প্রথম "__" নিজেই
// BLANK_REGEX-এর "২+ underscore" শর্তে ম্যাচ করে যেত। ফলে answer টা বসতো ঠিকই, কিন্তু তার
// পরে বাকি "word__ ..." অংশ (মার্ক করা শব্দ + বন্ধনী "__") অপরিবর্তিত/অতিরিক্ত টেক্সট হিসেবে
// থেকে যেত — একই উত্তর বাক্যে দুইবার দেখাতো। এখন আগে __..__ মার্ক খোঁজা হয় (নতুন Admin App
// convention), সেটা না পেলে পুরনো literal blank placeholder-এ (আগে ম্যানুয়ালি টাইপ করা কোনো
// প্রশ্নে থাকলে) fallback করে — দুই ধরনের প্রশ্নই সঠিকভাবে রেন্ডার হবে।
private val BLANK_REGEX = Regex("_{2,}|\\.{4,}|…{2,}")
private fun buildFillBlankText(question: String, answer: String): AnnotatedString? {
    val markMatch = HIGHLIGHT_REGEX.find(question)
    val blankRange = markMatch?.range ?: BLANK_REGEX.find(question)?.range ?: return null
    return buildAnnotatedString {
        append(question.substring(0, blankRange.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AnswerText, textDecoration = TextDecoration.Underline)) {
            append(answer)
        }
        append(question.substring(blankRange.last + 1))
    }
}

private val ExplainPanel = Color(0xFFF1F5F9)
/** লিখিত মডেল টেস্টে উত্তর/ব্যাখ্যা প্রথমে লুকানো থাকে (QBankExamPaperScreen-এর hideAnswers) */
private val LocalPaperHideAnswers = androidx.compose.runtime.compositionLocalOf { false }
private val TechPanel    = Color(0xFFEAF1E4)

// ── FIX ("সাবজেক্ট ঠিকমতো Define না থাকা" সমস্যা): written প্রশ্নে subject_id
// এখনো ৯টা QBank সাবজেক্টের (English Grammar, English Literature, আন্তর্জাতিক,
// কম্পিউটার, গণিত, বাংলা ব্যাকরণ, বাংলা সাহিত্য, বাংলাদেশ বিষয়াবলী, সাধারণ বিজ্ঞান)
// যেকোনো একটা ধরে আসে (Sheet-এ ওগুলোই আছে) — কিন্তু written অংশে ঠিক ৪টা সাবজেক্টে
// (বাংলা/ইংরেজি/গণিত/সাধারণ জ্ঞান) কনসোলিডেট করে দেখানো দরকার। Sheet-এর subject_id
// সরাসরি বদলানো (হাজারো রো এডিট) ঝুঁকিপূর্ণ ও কষ্টসাধ্য — তাই এখানে subjectId-ভিত্তিক
// একটা কনসোলিডেশন ম্যাপ রাখা হলো, ডেটা অপরিবর্তিত থেকেও ঠিক ৪টা ট্যাবে ভাগ হয়ে যাবে।
// যেই subjectId এই ম্যাপে নেই (বা ফাঁকা) সেটাই আসল ডেটা-গ্যাপ — "অন্যান্য"-তে থেকে যাবে,
// ওগুলোর subject_id Sheet-এ বসানো দরকার। ──
internal val WRITTEN_SUBJECT_BUCKET = mapOf(
    "QB01" to "ইংরেজি",       // English Grammar
    "QB02" to "ইংরেজি",       // English Literature
    "QB03" to "সাধারণ জ্ঞান", // আন্তর্জাতিক
    "QB04" to "সাধারণ জ্ঞান", // কম্পিউটার
    "QB05" to "গণিত",         // গণিত
    "QB06" to "বাংলা",        // বাংলা ব্যাকরণ
    "QB07" to "বাংলা",        // বাংলা সাহিত্য
    "QB08" to "সাধারণ জ্ঞান", // বাংলাদেশ বিষয়াবলী
    "QB09" to "সাধারণ জ্ঞান", // সাধারণ বিজ্ঞান
    // 🐛 ফিক্স ("বাংলা/English Others-এ দেখানো" বাগ): এই ম্যাপটা তৈরি হয়েছিল
    // PaperComposer ফিচার আসার আগে — QB10 থেকে যা কিছু নতুন Subject হিসেবে
    // resolveOrCreateReference দিয়ে তৈরি হয়েছে (একীভূত "বাংলা"/"English" বাকেট,
    // পুরনো typo-ডুপ্লিকেট এন্ট্রি সহ) কোনোটাই এই ম্যাপে ছিল না — তাই সবই
    // "অন্যান্য"-তে পড়ে যাচ্ছিল। Study_Database_-_Subjects শিট দেখে (QB10-QB23)
    // সবগুলো যোগ করা হলো — নতুন Subject তৈরি হলে ভবিষ্যতেও এখানে যোগ করা লাগবে। ──
    "QB10" to "গণিত",         // পাটিগণিত
    "QB11" to "গণিত",         // বীজগণিত
    "QB12" to "সাধারণ জ্ঞান", // আন্তর্জাতিক বিষয়াবলী
    "QB13" to "সাধারণ জ্ঞান", // সাধারন জ্ঞান
    "QB14" to "বাংলা",        // বাংলা ব্যাকররণ (টাইপো-ডুপ্লিকেট)
    "QB15" to "ইংরেজি",       // English Grammmar (টাইপো-ডুপ্লিকেট)
    "QB16" to "সাধারণ জ্ঞান", // বাংলাদেশ বিষয়াবলী (ডুপ্লিকেট)
    "QB17" to "বাংলা",        // বাংলা ব্যকরণ (টাইপো-ডুপ্লিকেট)
    "QB18" to "বাংলা",        // বাংলা (PaperComposer — একীভূত)
    "QB19" to "ইংরেজি",       // English (PaperComposer — একীভূত)
    "QB20" to "বাংলা",        // বাংলা (ডুপ্লিকেট — refData রেসের জন্য তৈরি হয়েছিল)
    "QB21" to "ইংরেজি",       // English (ডুপ্লিকেট)
    "QB22" to "বাংলা",        // বাংলা (ডুপ্লিকেট)
    "QB23" to "ইংরেজি",       // English (ডুপ্লিকেট)
    // 🆕 Unified Subject ID (S01, S02 ...) — Quiz/QBank/Study এখন একই subject তালিকা শেয়ার করে।
    // উপরের পুরনো QBxx id-গুলো legacy ডেটার জন্য রাখা হলো; নতুন id-র বাকেট CDN ডেটা দেখে
    // ঠিক করা (পুরনো QBxx → নতুন Sxx মাইগ্রেশনের সাথে হুবহু আগের বাকেটই বজায় রাখা হয়েছে)।
    // নতুন Subject তৈরি হলে এখানে যোগ না করলে সেটা "অন্যান্য"-তে যাবে।
    "S01" to "বাংলা",         // বাংলা সাহিত্য
    "S02" to "বাংলা",         // বাংলা ব্যাকরণ (আগের QB06/QB14/QB17)
    "S03" to "ইংরেজি",        // English Grammar (আগের QB01/QB15)
    "S04" to "গণিত",          // পাটিগণিত
    "S05" to "গণিত",          // জ্যামিতি
    "S06" to "সাধারণ জ্ঞান",  // আন্তর্জাতিক বিষয়াবলি (আগের QB03/QB12)
    "S07" to "সাধারণ জ্ঞান",  // বাংলাদেশ বিষয়াবলি (আগের QB08/QB16)
    "S08" to "সাধারণ জ্ঞান",  // কম্পিউটার (আগের QB04)
    "S12" to "ইংরেজি",        // English Literature (আগের QB02)
    "S14" to "সাধারণ জ্ঞান",  // সাধারণ জ্ঞান (আগের QB13)
    "S15" to "ইংরেজি",        // English for Today
    "S16" to "বাংলা",         // BCS — আগের QB14 যে বাকেটে ছিল (বাংলা) সেটাই বজায় রাখা হলো
    "S18" to "গণিত",          // বীজগণিত (আগের QB11)
    "S20" to "গণিত",          // গণিত (আগের QB05)
    "S21" to "সাধারণ জ্ঞান"   // সাধারণ বিজ্ঞান (আগের QB09)
)
private val WRITTEN_SUBJECT_ORDER = listOf("বাংলা", "ইংরেজি", "গণিত", "সাধারণ জ্ঞান")

// ── top-level করা হলো (আগে QBankExamPaperScreen()-এর ভিতরে local fun ছিল) — এখন
// ExamSerialCard/ExamQAItem থেকেও সরাসরি কল করা যায়, selectedSubject থ্রেড করে
// পাঠাতে হয় না (q.subjectId থেকেই সবসময় নির্ভুলভাবে বের করা যায়) ──
private fun subjectLabelOf(q: QuestionItem): String =
    WRITTEN_SUBJECT_BUCKET[q.subjectId] ?: "অন্যান্য"


/**
 * লিখিত পেপার স্ক্রিন। hideAnswers = true (লিখিত মডেল টেস্ট) হলে উত্তর/ব্যাখ্যা/কৌশল প্রথমে লুকানো থাকে;
 * নিচে ডানের ভাসমান বাটনে ট্যাপ করলে দেখা/লুকানো যায়। ডিফল্ট false — বাকি সব জায়গায় আগের আচরণ।
 */
@Composable
fun QBankExamPaperScreen(
    institutionName : String,
    postName        : String,
    showInstitution : Boolean = true,
    questions       : List<QuestionItem>,
    isAdmin         : Boolean = false,
    onBack          : () -> Unit,
    onBookmark      : (String) -> Unit,
    onReport        : (globalIndex: Int, issue: String) -> Unit,
    hideAnswers     : Boolean = false
) {
    var hidden by remember(hideAnswers) { mutableStateOf(hideAnswers) }
    androidx.compose.runtime.CompositionLocalProvider(LocalPaperHideAnswers provides hidden) {
        Box(Modifier.fillMaxSize()) {
            QBankExamPaperScreenImpl(
                institutionName = institutionName, postName = postName, showInstitution = showInstitution,
                questions = questions, isAdmin = isAdmin, onBack = onBack, onBookmark = onBookmark, onReport = onReport
            )
            if (hideAnswers) {
                Text(
                    text = if (hidden) "👁 উত্তর দেখান" else "🙈 উত্তর লুকান",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd).padding(16.dp)
                        .clip(RoundedCornerShape(24.dp)).background(Color(0xFF0E7490))
                        .clickable { hidden = !hidden }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QBankExamPaperScreenImpl(
    institutionName : String,
    postName        : String,
    showInstitution : Boolean = true,   // false হলে (বিসিএস/প্রাথমিক/নিবন্ধন/ব্যাংক) শুধু পদবী
    questions       : List<QuestionItem>,
    isAdmin         : Boolean = false,
    onBack          : () -> Unit,
    onBookmark      : (String) -> Unit,
    onReport        : (globalIndex: Int, issue: String) -> Unit
) {
    // ── FIX: এখন subjectId-কে সরাসরি (Room reference-নাম না) ৪-বাকেট কনসোলিডেশন
    // ম্যাপ দিয়ে রেজল্ভ করা হয় — written অংশে সবসময় ঠিক ৪টা সাবজেক্ট-ট্যাবই দেখাবে
    // (top-level subjectLabelOf() ব্যবহার হচ্ছে, দেখো ফাইলের ওপরে) ──
    fun subjectLabel(q: QuestionItem): String = subjectLabelOf(q)

    // ── শিট-ভিউ (ছাপা সল্যুশন-শিটের মতো একটানা পেপার) | কার্ড-ভিউ (আগের ট্যাব + কার্ড) — হেডারের 📄|🗂 টগলে বদলায় ──
    val viewCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { com.hanif.smartstudy.util.QBankPaperViewStore.init(viewCtx) }
    val sheet = com.hanif.smartstudy.util.QBankPaperViewStore.sheet

    // ── সাবজেক্ট ট্যাবের ক্রম — সবসময় বাংলা→ইংরেজি→গণিত→সাধারণ জ্ঞান (যেগুলোর
    // প্রশ্ন আছে শুধু সেগুলোই দেখাবে), "অন্যান্য" থাকলে সবার শেষে (ডেটা-গ্যাপ নির্দেশক) ──
    val subjectOrder = remember(questions) {
        val present = questions.map { subjectLabel(it) }.toSet()
        WRITTEN_SUBJECT_ORDER.filter { it in present } +
            (if ("অন্যান্য" in present) listOf("অন্যান্য") else emptyList())
    }
    var selectedSubject by remember(subjectOrder) { mutableStateOf(subjectOrder.firstOrNull().orEmpty()) }

    val subjectQuestions = remember(questions, selectedSubject) {
        questions.filter { subjectLabel(it) == selectedSubject }
    }

    // ── এই সাবজেক্টের প্রশ্নগুলোকে groupId অনুযায়ী "সিরিয়াল" (একটা করে কার্ড) এ ভাগ করা —
    // একই groupId হলে সবগুলো sub-part একটা কার্ডে, না হলে প্রতিটা নিজের একটা কার্ড ──
    data class Serial(val key: String, val items: List<QuestionItem>)
    // ── FIX (লিখিত প্রশ্ন কম/ডুপ্লিকেট দেখানোর বাগ): আগের লুপ `i += mates.size` ধরে নিত একই গ্রুপের
    // প্রশ্নগুলো লিস্টে পাশাপাশি আছে। কিন্তু ViewModel মাস্টার্ড/স্টাডি-ডান প্রশ্ন শেষে সরিয়ে দেয়, তাই
    // গ্রুপের সদস্য ছড়িয়ে গেলে কিছু প্রশ্ন বাদ পড়ত আর কিছু গ্রুপ দু'বার দেখাত। এখন প্রতিটা গ্রুপ
    // ঠিক একবার (প্রথম সদস্য যেখানে আছে সেই জায়গায়) বসে, আর কোনো প্রশ্নই বাদ পড়ে না। ──
    val serials = remember(subjectQuestions) {
        val groups = subjectQuestions.filter { it.isGrouped() }.groupBy { it.groupId }
        val seen = HashSet<String>()
        val out = mutableListOf<Serial>()
        for (q in subjectQuestions) {
            if (q.isGrouped()) {
                if (seen.add(q.groupId)) {
                    out.add(Serial(q.groupId, (groups[q.groupId] ?: listOf(q)).sortedBy { it.subIndex }))
                }
            } else {
                out.add(Serial(q.id, listOf(q)))
            }
        }
        out
    }

    var reportTarget by remember { mutableStateOf<QuestionItem?>(null) }
    var sortReversed by remember { mutableStateOf(false) }
    // ── "মূল প্রশ্নপত্র" — হেডারে বাটন, চাপলে হেডারের নিচে ছবি খোলে ──
    var paperExpanded by remember { mutableStateOf(false) }
    val paperImages = remember(questions) { questions.flatMap { it.questionPaperImageList() }.distinct() }
    val shownSerials = remember(serials, sortReversed) { if (sortReversed) serials.reversed() else serials }

    Column(Modifier.fillMaxSize().background(PaperBg)) {
        // ── হেডার: প্রতিষ্ঠান (eyebrow) + পদবী (title) + মোট প্রশ্ন — অ্যাপের QBank
        // থিম-গ্র্যাডিয়েন্ট (#0891B2 → #0E7490), আগের কালো ব্যাকগ্রাউন্ডের বদলে ──
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HeaderTop, HeaderBottom)))
                .padding(start = 8.dp, end = 18.dp, top = 10.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "ব্যাক", tint = HeaderCream)
                }
                Column {
                    // ── ওপরে পদবী (মূল শিরোনাম); পদবী না থাকলে প্রতিষ্ঠানই শিরোনাম ──
                    Text(
                        text = postName.ifBlank { institutionName },
                        color = HeaderCream, fontSize = 19.sp, fontWeight = FontWeight.Bold
                    )
                    // ── তার নিচে সুন্দর করে প্রতিষ্ঠান (pill) — শুধু ১৬-২০ গ্রেডে ──
                    if (showInstitution && postName.isNotBlank() && institutionName.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color(0xFFFFE9A8).copy(alpha = 0.55f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🏛 $institutionName",
                                color = Color(0xFFFFE9A8), fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = "প্রশ্নব্যাংক · ${questions.size} প্রশ্ন", color = HeaderSub, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                // ── হেডারের ডানে: "মূল প্রশ্নপত্র" বাটন + ↕ সাজান (ট্যাপে সিরিয়াল ক্রম উল্টে যায়) ──
                com.hanif.smartstudy.ui.shared.QuestionPaperHeaderButton(
                    enabled  = paperImages.isNotEmpty(),
                    expanded = paperExpanded,
                    onClick  = { paperExpanded = !paperExpanded },
                    onDark   = true,
                    count    = paperImages.size
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                ) {
                    listOf(true to "📄", false to "🗂").forEach { (isSheet, icon) ->
                        Box(
                            Modifier.background(if (sheet == isSheet) Color.White.copy(alpha = 0.30f) else Color.Transparent)
                                .clickable { com.hanif.smartstudy.util.QBankPaperViewStore.set(viewCtx, isSheet) }
                                .size(width = 36.dp, height = 34.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(icon, fontSize = 15.sp) }
                    }
                }
                if (!sheet) Spacer(Modifier.width(8.dp))
                if (!sheet) Box(
                    Modifier.size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = if (sortReversed) 0.28f else 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { sortReversed = !sortReversed },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "সাজান",
                        tint = HeaderCream, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── "মূল প্রশ্নপত্র" বাটন হেডারে সরানো হয়েছে (ওপরে)। চাপলে ছবিগুলো এখানে খোলে। ──
        if (paperExpanded && paperImages.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                com.hanif.smartstudy.ui.shared.QuestionPaperImagesPanel(
                    urls     = paperImages,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (sheet) {
            PaperSheetView(
                questions       = questions,
                onBookmark      = onBookmark,
                onSpeak         = { text, key -> TtsManager.speak(text, key) },
                onReportTap     = { q -> reportTarget = q },
                modifier        = Modifier.weight(1f)
            )
        } else {
        // ── সাবজেক্ট ট্যাব — টপিক এখানে দেখানো হয় না, শুধু সাবজেক্ট ──
        Row(
            Modifier
                .fillMaxWidth()
                .background(TabInactiveBg)
                .border(BorderStroke2(2.dp, HeaderBg))
                .horizontalScroll(rememberScrollState())
        ) {
            subjectOrder.forEach { subj ->
                val selected = subj == selectedSubject
                Box(
                    Modifier
                        .background(if (selected) HeaderBg else Color.Transparent)
                        .border(androidx.compose.foundation.BorderStroke(0.5.dp, TabBorder))
                        .clickable { selectedSubject = subj }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = subj,
                        color = if (selected) HeaderCream else TextMuted,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── সাবজেক্ট সেকশন টাইটেল ──
        Row(
            Modifier.fillMaxWidth()
                .background(PaperBg)
                .border(androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$selectedSubject (${subjectQuestions.size})",
                color = Color(0xFF8A6D1D),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Divider(color = GoldAccent, thickness = 1.dp)

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            // ── ছাপা পেপারের শীর্ষ ব্যানার (হলুদ/কমলা/নেভি) ──
            item {
                val bannerTop = if (showInstitution && institutionName.isNotBlank() && postName.isNotBlank()) institutionName
                                else postName.ifBlank { institutionName }
                val bannerMid = if (showInstitution && institutionName.isNotBlank() && postName.isNotBlank()) postName else ""
                PaperBanner(
                    top = bannerTop, mid = bannerMid, count = serials.size,
                    year = subjectQuestions.firstOrNull { it.year.isNotBlank() }?.year.orEmpty()
                )
            }
            itemsIndexed(shownSerials, key = { _, s -> s.key }) { idx, serial ->
                ExamSerialCard(
                    serialNo     = if (sortReversed) serials.size - idx else idx + 1,
                    serial       = serial.items,
                    isAdmin      = isAdmin,
                    onBookmark   = onBookmark,
                    onSpeak      = { text, key -> TtsManager.speak(text, key) },
                    onReportTap  = { q -> reportTarget = q }
                )
                Divider(color = DashedLine, thickness = 1.dp)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }  // else (কার্ড-ভিউ)
    }

    reportTarget?.let { q ->
        val globalIdx = questions.indexOfFirst { it.id == q.id }
        ReportDialog(
            questionId   = q.id,
            questionText = q.question,
            onReport     = { issue -> if (globalIdx >= 0) onReport(globalIdx, issue); reportTarget = null },
            onDismiss    = { reportTarget = null }
        )
    }
}

/** একটা "সিরিয়াল" — হয় একটা একক প্রশ্ন, অথবা একই groupId-এর একগুচ্ছ sub-part (ক/খ/গ...)
 *  ছাপা পেপারের মতো: "০১।" ক্রমিক + শিরোনাম, তারপর শিরোনামের টেমপ্লেট অনুযায়ী লেআউট (PaperLayouts.kt) */
@Composable
private fun ExamSerialCard(
    serialNo    : Int,
    serial      : List<QuestionItem>,
    isAdmin     : Boolean = false,
    onBookmark  : (String) -> Unit,
    onSpeak     : (String, String) -> Unit,
    onReportTap : (QuestionItem) -> Unit
) {
    val hide = LocalPaperHideAnswers.current
    val labels = listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ")
    val first = serial.first()
    val heading = first.groupHeading
    val hasHeading = serial.size > 1 || heading.isNotBlank()
    val isEnglish = subjectLabelOf(first) == "ইংরেজি"
    val isMath = subjectLabelOf(first) == "গণিত"
    val layout = remember(heading, first.formatStyle, first.id) {
        resolvePaperLayout(heading, first.formatStyle, first.optionA.isNotBlank(), isEnglish)
    }
    val serialText = paperBn(serialNo) + "।"

    if (isAdmin && serial.size > 1) {
        Text(
            "▸ DIAG groupId=${first.groupId} groupHeading=" +
                (heading.ifBlank { "<EMPTY>" }) +
                " formatStyle=${first.formatStyle.ifBlank { "<none>" }} layout=${layout.kind}",
            fontSize = 8.sp, color = Color(0xFFB00020),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (!hasHeading) {
            // ── একক প্রশ্ন: "০১।  প্রশ্ন … উত্তর"
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = serialText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextMain,
                    modifier = Modifier.width(36.dp)
                )
                Column(Modifier.weight(1f)) {
                    ExamQAItem(
                        prefixLabel = null, q = first, inlineSolution = isMath, isAdmin = isAdmin,
                        onBookmark = onBookmark, onSpeak = onSpeak, onReportTap = onReportTap
                    )
                }
            }
            return@Column
        }

        // ── শিরোনাম লাইন: "০১।  সন্ধি বিচ্ছেদ করুন:"
        val title = paperHeadingBase(heading)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = serialText, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TextMain,
                modifier = Modifier.width(36.dp)
            )
            com.hanif.smartstudy.ui.shared.MathText(
                text = title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TextMain,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth().padding(start = 4.dp)) {
            when (layout.kind) {

                // ── শব্দ-বাক্স + অনুচ্ছেদ + "Answer: (i) … (ii) …"
                "cloze" -> {
                    val parts = paperClozeParts(heading)
                    if (parts.words.isNotEmpty()) PaperWordBox(parts.words)
                    if (parts.passage.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        com.hanif.smartstudy.ui.shared.MathText(
                            text = parts.passage, fontSize = 13.8.sp, lineHeight = 22.sp, color = TextMain
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val answerLine = "Answer: " + serial.joinToString("   ") { "${it.question.trim()} ${it.answer.trim()}" }
                    if (!hide) Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(AnswerBg)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        com.hanif.smartstudy.ui.shared.MathText(text = answerLine, fontSize = 13.sp, color = AnswerText, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                    }
                }

                // ── দুই কলামের টেবিল (অশুদ্ধ | শুদ্ধ ইত্যাদি)
                "tbl" -> {
                    val c0 = layout.cols.getOrNull(0) ?: ""
                    val c1 = layout.cols.getOrNull(1) ?: ""
                    Column(Modifier.fillMaxWidth().border(androidx.compose.foundation.BorderStroke(1.dp, TextMain))) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(TabInactiveBg)) {
                            PaperCell(c0, bold = true, modifier = Modifier.weight(1f).fillMaxHeight())
                            PaperCell(c1, bold = true, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        serial.forEach { sub ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                PaperCell(sub.question, modifier = Modifier.weight(1f).fillMaxHeight())
                                PaperCell(if (hide) "—" else sub.answer, color = AnswerText, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }

                // ── "ক) বাম  =  ডান" (সন্ধি/এক কথায়/বাগধারা/সমাস…)
                "pair" -> {
                    serial.forEachIndexed { idx, sub ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(
                                    text = (labels.getOrNull(idx) ?: "${idx + 1}") + ")",
                                    fontWeight = FontWeight.Bold, color = Color(0xFF8A6D1D),
                                    modifier = Modifier.width(26.dp)
                                )
                                if (layout.highlight) {
                                    Text(
                                        text = buildHighlightedText(sub.question),
                                        fontSize = 13.8.sp, lineHeight = 20.sp, color = TextMain,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    com.hanif.smartstudy.ui.shared.MathText(
                                        text = sub.question,
                                        fontSize = 13.8.sp, lineHeight = 20.sp, color = TextMain,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    text = "  ${layout.sep}  ", fontSize = 13.8.sp, fontWeight = FontWeight.Bold, color = TextMain
                                )
                                com.hanif.smartstudy.ui.shared.MathText(
                                    text = if (hide) "＿＿＿" else sub.answer, fontSize = 13.5.sp, lineHeight = 20.sp,
                                    color = AnswerText, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            val third = layout.thirdPre
                            if (third != null && sub.explanation.isNotBlank() && !hide) {
                                com.hanif.smartstudy.ui.shared.MathText(
                                    text = "$third${sub.explanation}${layout.thirdSuf}",
                                    fontSize = 12.sp, color = TextMuted, lineHeight = 18.sp,
                                    modifier = Modifier.padding(start = 26.dp, top = 1.dp)
                                )
                            }
                        }
                    }
                }

                // ── প্রশ্ন + "Ans:/উত্তর:" লাইন (stack), MCQ-লেআউট (Options + Answer), ফাঁকা-পূরণ, অথবা সাধারণ ক) খ) তালিকা
                else -> {
                    serial.forEachIndexed { idx, sub ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(
                                text = (labels.getOrNull(idx) ?: "${idx + 1}") + ")",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8A6D1D),
                                modifier = Modifier.width(26.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                ExamQAItem(
                                    prefixLabel = null, q = sub, compact = true, isAdmin = isAdmin,
                                    answerLabel = when {
                                        layout.kind == "mcq" -> "Answer: "
                                        layout.kind == "stack" && layout.pre.isNotBlank() -> layout.pre
                                        else -> null
                                    },
                                    optionsLine = if (layout.kind == "mcq") paperOptionsLine(sub) else null,
                                    inlineSolution = isMath,
                                    onBookmark = onBookmark, onSpeak = onSpeak, onReportTap = onReportTap
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** টেবিলের একটা ঘর (বর্ডারসহ) */
@Composable
private fun PaperCell(text: String, bold: Boolean = false, color: Color = TextMain, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(androidx.compose.foundation.BorderStroke(0.6.dp, TextMain))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        com.hanif.smartstudy.ui.shared.MathText(
            text = text, fontSize = 13.sp, lineHeight = 19.sp, color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** cloze-এর শব্দ-বাক্স: প্রতি সারিতে ৩ ঘর, বর্ডারসহ (ছাপা পেপারের মতো) */
@Composable
private fun PaperWordBox(words: List<String>) {
    Column(Modifier.fillMaxWidth().border(androidx.compose.foundation.BorderStroke(1.dp, TextMain))) {
        words.chunked(3).forEach { rowWords ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                rowWords.forEach { w ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .border(androidx.compose.foundation.BorderStroke(0.6.dp, TextMain))
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        contentAlignment = Alignment.CenterStart
                    ) { Text(text = w, fontSize = 13.sp, color = TextMain) }
                }
                repeat(3 - rowWords.size) {
                    Box(Modifier.weight(1f).fillMaxHeight().border(androidx.compose.foundation.BorderStroke(0.6.dp, TextMain))) {}
                }
            }
        }
    }
}

/** ছাপা পেপারের শীর্ষ ব্যানার: হলুদ (প্রতিষ্ঠান) / কমলা (পদ) / নেভি (প্রশ্নসংখ্যা · সাল) */
@Composable
private fun PaperBanner(top: String, mid: String, count: Int, year: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (top.isNotBlank()) {
            Box(Modifier.fillMaxWidth().background(Color(0xFFFFEB00)).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                Text(text = top, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
        }
        if (mid.isNotBlank()) {
            Box(Modifier.fillMaxWidth().background(Color(0xFFE8710A)).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                Text(text = mid, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1F2A6B)).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "প্রশ্ন: ${paperBn(count, 1)}টি", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            if (year.isNotBlank()) {
                Text(text = "সাল: $year", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ExamQAItem(
    prefixLabel : String?,
    q           : QuestionItem,
    compact     : Boolean = false,
    // ── পেপার-লেআউট (PaperLayouts.kt): null হলে আগের আচরণ ──
    answerLabel : String? = null,      // যেমন "Ans: ", "উত্তর: ", "Answer: "
    optionsLine : String? = null,      // "are/is/were/be" → "Options: …" লাইন
    inlineSolution : Boolean = false,  // গণিত: "সমাধান" বাক্স সরাসরি দেখানো (ছাপা পেপারের মতো)
    isAdmin     : Boolean = false,     // অ্যাডমিনের জন্য ✍ হাতে লেখার বাটন
    onBookmark  : (String) -> Unit,
    onSpeak     : (String, String) -> Unit,
    onReportTap : (QuestionItem) -> Unit
) {
    val hide = LocalPaperHideAnswers.current
    val showSolutionInline = inlineSolution && q.explanation.isNotBlank() && !LocalPaperHideAnswers.current
    var detailOpen by remember(q.id) { mutableStateOf(false) }
    var detailKind by remember(q.id) { mutableStateOf(if (showSolutionInline) "technique" else "explain") } // "explain" | "technique"
    var bookmarked by remember(q.id, q.isBookmarked) { mutableStateOf(q.isBookmarked) }

    Column {
        // ── PAPER COMPOSER: formatStyle অনুযায়ী প্রশ্ন-টেক্সট রেন্ডার আলাদা হয় ──
        val questionFontSize = if (compact) 13.8.sp else 14.5.sp
        val questionLineHeight = if (compact) 19.sp else 21.sp
        val inlineFillBlank = if (!hide && q.formatStyle == "fillblank" && q.answer.isNotBlank())
            buildFillBlankText(q.question, q.answer) else null

        when {
            inlineFillBlank != null -> Text(text = inlineFillBlank, fontSize = questionFontSize, lineHeight = questionLineHeight, color = TextMain)
            q.formatStyle == "highlight" -> Text(text = buildHighlightedText(q.question), fontSize = questionFontSize, lineHeight = questionLineHeight, color = TextMain)
            else -> com.hanif.smartstudy.ui.shared.MathText(text = q.question, fontSize = questionFontSize, lineHeight = questionLineHeight, color = TextMain)
        }
        if (optionsLine != null) {
            Spacer(Modifier.height(3.dp))
            com.hanif.smartstudy.ui.shared.MathText(
                text = "Options: $optionsLine",
                fontSize = if (compact) 12.5.sp else 13.sp, color = TextMuted, lineHeight = 18.sp
            )
        }
        if (showSolutionInline) {
            Spacer(Modifier.height(5.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(ExplainPanel)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(text = "সমাধান", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = HeaderTop)
                Spacer(Modifier.height(2.dp))
                com.hanif.smartstudy.ui.shared.MathText(
                    text = q.explanation, fontSize = 13.sp, lineHeight = 20.sp, color = TextMain
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // ── উত্তর — সরাসরি সঠিক উত্তরের টেক্সট (MCQ হলে answer ফিল্ডে টেক্সট থাকে,
        // Written হলেও answer ফিল্ডই মডেল-উত্তর ধরে নেওয়া হচ্ছে)।
        // ── FIX ("English এ Ans দরকার নাই"): "উত্তর:" প্রিফিক্স-লেবেল শুধু ইংরেজি
        // সাবজেক্ট ছাড়া বাকি সব জায়গায় (বাংলা/গণিত/সাধারণ জ্ঞান) দেখায় — ইংরেজিতে
        // (সাধারণত fill-in-the-blank টাইপ, answer এমনিতেই বাক্যের ফাঁকে বোঝা যায়)
        // শুধু উত্তরের টেক্সটটাই থাকে, কোনো লেবেল ছাড়া।
        // ── inlineFillBlank != null হলে উত্তর ইতিমধ্যে বাক্যের ভিতরেই দেখানো হয়ে গেছে,
        // তাই নিচে আবার আলাদা বক্সে দেখানো হয় না (ডুপ্লিকেট এড়াতে)। ──
        if (q.answer.isNotBlank() && inlineFillBlank == null && !hide) {
            val labelPrefix = answerLabel ?: (if (subjectLabelOf(q) == "ইংরেজি") "" else "উত্তর: ")
            Box(
                Modifier.clip(RoundedCornerShape(3.dp)).background(AnswerBg).padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                com.hanif.smartstudy.ui.shared.MathText(
                    text = "$labelPrefix${q.answer}",
                    fontSize = if (compact) 13.sp else 13.5.sp,
                    color = AnswerText,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        // ── ফুটার: বিস্তারিত টগল + আইকন রো ──
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hide) Spacer(Modifier) else Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (detailOpen) HeaderBg else Color(0xFFFDF6E3))
                    .border(1.dp, if (detailOpen) HeaderBg else GoldAccent, RoundedCornerShape(7.dp))
                    .clickable { detailOpen = !detailOpen }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = (if (detailOpen) "▴ " else "▾ ") + "বিস্তারিত",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (detailOpen) HeaderCream else Color(0xFF7A5F16)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ExamIconButton(emoji = "🔊") { onSpeak(if (hide) q.question else (q.question + ". উত্তর: " + q.answer), q.id) }
                ExamIconButton(emoji = if (bookmarked) "★" else "☆", active = bookmarked) {
                    bookmarked = !bookmarked
                    onBookmark(q.id)
                }
                ExamIconButton(emoji = "⚑") { onReportTap(q) }
            }
        }

        if (detailOpen && !hide) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                if (!showSolutionInline) DetailTab("ব্যাখ্যা", detailKind == "explain") { detailKind = "explain" }
                DetailTab("কৌশল", detailKind == "technique") { detailKind = "technique" }
            }
            Spacer(Modifier.height(4.dp))
            val panelText = if (detailKind == "explain") q.explanation else q.technique
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (detailKind == "explain") ExplainPanel else TechPanel)
                    .border(
                        androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent)
                    )
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 10.dp)
            ) {
                if (panelText.isBlank()) {
                    Text(
                        text = "এখনো যোগ করা হয়নি।",
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = TextMuted,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    com.hanif.smartstudy.ui.shared.MathText(text = panelText, fontSize = 12.5.sp, lineHeight = 20.sp, color = TextMain)
                }
            }
            // ✍ হাতে লেখা (টাচপেন) — ব্যাখ্যা/কৌশল ট্যাব অনুযায়ী
            com.hanif.smartstudy.ui.ink.InkSection(q.id, if (detailKind == "explain") "explanation" else "technique", isAdmin)
        }
    }
}

@Composable
private fun DetailTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) HeaderBg else Color.White)
            .border(androidx.compose.foundation.BorderStroke(1.dp, DashedLine))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) HeaderCream else TextMuted
        )
    }
}

@Composable
private fun ExamIconButton(emoji: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(25.dp)
            .clip(CircleShape)
            .background(if (active) GoldAccent else Color.White)
            .border(1.dp, if (active) GoldAccent else DashedLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 12.sp)
    }
}

// ── ছোট হেল্পার: BorderStroke সহজে লেখার জন্য (import শর্টকাট) ──
private fun BorderStroke2(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)


/* ══════════════════════════════════════════════════════════════════════════
   📄 শিট-ভিউ — ছাপা "লিখিত জব সল্যুশন"-এর মতো একটানা পেপার (হালকা রঙে)
   • প্রতিষ্ঠান/পদ/সংখ্যা উপরের অ্যাপ-হেডারেই একবার আছে — এখানে আর দ্বিতীয়বার নয়
   • বিষয়ের শিরোনাম (বাংলা (১৬)); সাবজেক্ট-চিপে ট্যাপ করলে সেখানে স্ক্রল
   • পুরো পেপারে একটানা নম্বর; গ্রুপ হলে হেডিং একবারই (১. সন্ধি বিচ্ছেদ করুন:)
   • কার্ড-ভিউর সব লেআউট-সিস্টেম আছে:
       tbl   → বর্ডার-টেবিল (প্রদত্ত শব্দ | সন্ধি বিচ্ছেদ …)
       pair  → "ক. সদেব = সদা+এব" (+ বাক্য/ব্যাখ্যা লাইন)
       cloze → শব্দ-বাক্স + অনুচ্ছেদ + উত্তর
       stack/mcq/fill/plain → প্রশ্ন + নিচে "উ. উত্তর" (গণিতে বক্সে "সমাধান:")
   • প্রতিটা প্রশ্নের ডানে ⋮ (শুনুন / বুকমার্ক / রিপোর্ট); উত্তর-লুকানো মোডে উত্তর দেখায় না
   ══════════════════════════════════════════════════════════════════════════ */
// হালকা রঙ-সেট (TextMain-এর মতো গাঢ় কালো নয়)
private val SheetInk     = Color(0xFF475467)
private val SheetInkSoft = Color(0xFF7B8794)
private val SheetTblLine = Color(0xFFD5DCE6)
private val SheetTblHead = Color(0xFFF3F6FA)

private val SheetLetters = listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ", "ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন")
private fun sheetLabel(i: Int): String = SheetLetters.getOrNull(i) ?: paperBn(i + 1, 1)

private sealed class SheetRow {
    class Subject(val label: String, val count: Int) : SheetRow()
    class Item(val no: Int, val items: List<QuestionItem>, val subject: String) : SheetRow()
}

private fun buildSheetRows(questions: List<QuestionItem>): List<SheetRow> {
    val rows = ArrayList<SheetRow>()
    val present = questions.map { subjectLabelOf(it) }.toSet()
    val order = WRITTEN_SUBJECT_ORDER.filter { it in present } + (if ("অন্যান্য" in present) listOf("অন্যান্য") else emptyList())
    var no = 0
    for (label in order) {
        val qs = questions.filter { subjectLabelOf(it) == label }
        rows.add(SheetRow.Subject(label, qs.size))
        val groups = qs.filter { it.isGrouped() }.groupBy { it.groupId }
        val seen = HashSet<String>()
        for (q in qs) {
            if (q.isGrouped()) {
                if (seen.add(q.groupId)) {
                    no++
                    rows.add(SheetRow.Item(no, (groups[q.groupId] ?: listOf(q)).sortedBy { it.subIndex }, label))
                }
            } else {
                no++
                rows.add(SheetRow.Item(no, listOf(q), label))
            }
        }
    }
    return rows
}

@Composable
private fun SheetText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) = com.hanif.smartstudy.ui.shared.MathText(
    text = text, modifier = modifier, fontSize = fontSize, fontWeight = fontWeight, color = color, lineHeight = lineHeight
)

@Composable
private fun PaperSheetView(
    questions   : List<QuestionItem>,
    onBookmark  : (String) -> Unit,
    onSpeak     : (String, String) -> Unit,
    onReportTap : (QuestionItem) -> Unit,
    modifier    : Modifier = Modifier
) {
    val rows = remember(questions) { buildSheetRows(questions) }
    val hide = LocalPaperHideAnswers.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val subjects = remember(rows) { rows.filterIsInstance<SheetRow.Subject>() }

    Column(modifier.fillMaxWidth().background(PaperBg)) {
        // সাবজেক্ট-চিপ — ট্যাপে সেই বিষয়ের শুরুতে স্ক্রল
        if (subjects.size > 1) {
            Row(
                Modifier.fillMaxWidth().background(TabInactiveBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subjects.forEach { sub ->
                    Text(
                        text = sub.label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = HeaderBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50)).background(Color.White)
                            .border(1.dp, HeaderBg.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .clickable {
                                val at = rows.indexOf(sub)
                                if (at >= 0) scope.launch { listState.animateScrollToItem(at) }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(rows.size) { i ->
                when (val r = rows[i]) {
                    is SheetRow.Subject -> Column(
                        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${r.label} (${paperBn(r.count, 1)})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeaderBg)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.width(48.dp).height(2.dp).background(HeaderBg.copy(alpha = 0.55f)))
                    }
                    is SheetRow.Item -> SheetItem(r.no, r.items, r.subject, hide, onBookmark, onSpeak, onReportTap)
                }
            }
        }
    }
}

@Composable
private fun SheetItem(
    no: Int, items: List<QuestionItem>, subject: String, hide: Boolean,
    onBookmark: (String) -> Unit, onSpeak: (String, String) -> Unit, onReportTap: (QuestionItem) -> Unit
) {
    val first = items.first()
    val math = subject == "গণিত"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (first.isGrouped()) {
            val heading = first.groupHeading
            val layout = resolvePaperLayout(heading, first.formatStyle, paperOptionsLine(first) != null, subject == "ইংরেজি")
            // হেডিং — একবারই
            Row(verticalAlignment = Alignment.Top) {
                Text("${paperBn(no, 1)}.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SheetInk,
                    modifier = Modifier.widthIn(min = 28.dp))
                SheetText(
                    text = paperHeadingBase(heading), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = SheetInk, lineHeight = 22.sp, modifier = Modifier.weight(1f)
                )
            }
            when (layout.kind) {
                // ── শব্দ-বাক্স + অনুচ্ছেদ + উত্তর ──
                "cloze" -> {
                    val cp = paperClozeParts(heading)
                    Spacer(Modifier.height(6.dp))
                    if (cp.words.isNotEmpty()) PaperWordBox(cp.words)
                    if (cp.passage.isNotBlank()) {
                        SheetText(text = cp.passage, fontSize = 14.sp, lineHeight = 22.sp, color = SheetInk,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    if (!hide) {
                        val answerLine = "উ. " + items.joinToString("   ") { "${it.question.trim()} ${it.answer.trim()}" }
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(4.dp))
                                .background(AnswerBg).padding(horizontal = 8.dp, vertical = 5.dp)
                        ) { SheetText(text = answerLine, fontSize = 13.sp, lineHeight = 20.sp, color = AnswerText) }
                    }
                }
                // ── দুই কলামের টেবিল (প্রদত্ত শব্দ | সন্ধি বিচ্ছেদ ইত্যাদি) ──
                "tbl" -> {
                    val c0 = layout.cols.getOrNull(0) ?: ""
                    val c1 = layout.cols.getOrNull(1) ?: ""
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, SheetTblLine), RoundedCornerShape(6.dp))
                    ) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(SheetTblHead)) {
                            SheetCell(c0, bold = true, modifier = Modifier.weight(1f).fillMaxHeight())
                            SheetCell(c1, bold = true, modifier = Modifier.weight(1f).fillMaxHeight())
                            Box(Modifier.width(34.dp).fillMaxHeight().border(androidx.compose.foundation.BorderStroke(0.6.dp, SheetTblLine))) {}
                        }
                        items.forEach { sub ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                SheetCell(sub.question, modifier = Modifier.weight(1f).fillMaxHeight())
                                SheetCell(if (hide) "—" else sub.answer, color = AnswerText, modifier = Modifier.weight(1f).fillMaxHeight())
                                Box(
                                    Modifier.width(34.dp).fillMaxHeight()
                                        .border(androidx.compose.foundation.BorderStroke(0.6.dp, SheetTblLine)),
                                    contentAlignment = Alignment.Center
                                ) { SheetMenu(sub, hide, onBookmark, onSpeak, onReportTap) }
                            }
                        }
                    }
                }
                // ── pair / stack / mcq / fill / plain: ক. খ. গ. উপ-প্রশ্ন ──
                else -> items.forEachIndexed { i, q ->
                    SheetSub("${sheetLabel(i)}.", q, layout, math, hide, onBookmark, onSpeak, onReportTap)
                }
            }
        } else {
            // একক প্রশ্ন: "২০.  প্রশ্ন … উ. উত্তর"
            SheetSub("${paperBn(no, 1)}.", first, PaperLayout("plain"), math, hide, onBookmark, onSpeak, onReportTap)
        }
    }
    Divider(color = DashedLine, thickness = 1.dp)
}

/** টেবিলের ঘর (হালকা বর্ডার) */
@Composable
private fun SheetCell(text: String, bold: Boolean = false, color: Color = SheetInk, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(androidx.compose.foundation.BorderStroke(0.6.dp, SheetTblLine))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        SheetText(
            text = text, fontSize = 13.5.sp, lineHeight = 20.sp, color = color,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** ⋮ — শুনুন / বুকমার্ক / রিপোর্ট */
@Composable
private fun SheetMenu(
    q: QuestionItem, hide: Boolean,
    onBookmark: (String) -> Unit, onSpeak: (String, String) -> Unit, onReportTap: (QuestionItem) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Text("⋮", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SheetInkSoft,
            modifier = Modifier.clickable { menu = true }.padding(horizontal = 8.dp, vertical = 2.dp))
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("🔊 শুনুন") },
                onClick = { menu = false; onSpeak(if (hide) q.question else (q.question + ". উত্তর: " + q.answer), q.id) }
            )
            DropdownMenuItem(
                text = { Text(if (q.isBookmarked) "★ বুকমার্ক সরান" else "☆ বুকমার্ক") },
                onClick = { menu = false; onBookmark(q.id) }
            )
            DropdownMenuItem(
                text = { Text("🚩 রিপোর্ট") },
                onClick = { menu = false; onReportTap(q) }
            )
        }
    }
}

@Composable
private fun SheetSub(
    label: String, q: QuestionItem, layout: PaperLayout, math: Boolean, hide: Boolean,
    onBookmark: (String) -> Unit, onSpeak: (String, String) -> Unit, onReportTap: (QuestionItem) -> Unit
) {
    val sep = layout.sep.ifBlank { "=" }
    val inlineThird = layout.thirdPre == "("                       // সমাস: "প্রশ্ন = উত্তর (ব্যাখ্যা)"
    val oneLine = layout.kind == "pair" && (!layout.highlight || inlineThird)
    val ans = if (hide) "＿＿＿" else q.answer
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = SheetInkSoft,
            modifier = Modifier.widthIn(min = 28.dp).padding(top = 1.dp))
        Column(Modifier.weight(1f)) {
            if (oneLine) {
                if (layout.highlight) {
                    val third = if (inlineThird && !hide && q.explanation.isNotBlank()) " (${q.explanation})" else ""
                    Text(
                        text = buildAnnotatedString { append(buildHighlightedText(q.question)); append("  $sep  $ans$third") },
                        fontSize = 14.5.sp, lineHeight = 22.sp, color = SheetInk
                    )
                } else {
                    SheetText(text = "${q.question}  $sep  $ans", fontSize = 14.5.sp, lineHeight = 22.sp, color = SheetInk)
                }
                if (!inlineThird && layout.thirdPre != null && !hide && q.explanation.isNotBlank()) {
                    SheetText(text = "${layout.thirdPre} ${q.explanation}${layout.thirdSuf}", fontSize = 13.sp, lineHeight = 20.sp, color = SheetInkSoft)
                }
            } else {
                if (layout.highlight) {
                    Text(buildHighlightedText(q.question), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, color = SheetInk)
                } else {
                    SheetText(text = q.question, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, color = SheetInk)
                }
                if (layout.kind == "mcq") {
                    paperOptionsLine(q)?.let { SheetText(text = "অপশন: $it", fontSize = 13.sp, lineHeight = 20.sp, color = SheetInkSoft) }
                }
                if (!hide && q.answer.isNotBlank()) {
                    SheetText(text = "উ. ${q.answer}", fontSize = 14.5.sp, lineHeight = 22.sp, color = AnswerText.copy(alpha = 0.9f))
                }
                if (!hide && q.explanation.isNotBlank()) {
                    if (math) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "সমাধান:", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = HeaderBg,
                            modifier = Modifier.border(1.dp, HeaderBg.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                        SheetText(text = q.explanation, fontSize = 14.sp, lineHeight = 22.sp, color = SheetInk, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        SheetText(text = "ব্যাখ্যা: ${q.explanation}", fontSize = 12.5.sp, lineHeight = 19.sp, color = SheetInkSoft)
                    }
                }
            }
            if (!hide && q.technique.isNotBlank()) {
                SheetText(text = "কৌশল: ${q.technique}", fontSize = 12.5.sp, lineHeight = 19.sp, color = AnswerText.copy(alpha = 0.8f))
            }
        }
        SheetMenu(q, hide, onBookmark, onSpeak, onReportTap)
    }
}
