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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

// ── QBank "পদবী → প্রতিষ্ঠান → প্রশ্নপত্র" (৩য় লেয়ার) স্ক্রিন — এক্সাম-পেপার স্টাইল।
// ব্যবহারকারীর দেওয়া HTML মকআপ অনুযায়ী তৈরি: ক্রিম রঙের পেপার ব্যাকগ্রাউন্ড, গাঢ়
// হেডার (প্রতিষ্ঠান + পদবী নাম), সাবজেক্ট ট্যাব (টপিক দেখানো হয় না — শুধু সাবজেক্ট),
// প্রতিটা প্রশ্ন সরাসরি উত্তর-সহ (এটা একটা রেফারেন্স/আনসার-কী রিভিউ স্ক্রিন, লাইভ
// কুইজ-খেলা স্ক্রিন না — তাই MCQ অপশন বাটন না দেখিয়ে সরাসরি সঠিক উত্তর দেখানো হয়)।
// একই groupId-এর multi-part প্রশ্ন (ক/খ/গ/ঘ/ঙ) একটা কার্ডে একসাথে দেখানো হয়। ──

private val PaperBg      = Color(0xFFF7F3E9)
private val HeaderTop    = Color(0xFF0891B2)   // অ্যাপের QBank থিম-রঙ (SubjectListScreen-এর গ্র্যাডিয়েন্টের সাথে ম্যাচ)
private val HeaderBottom = Color(0xFF0E7490)
private val HeaderBg     = HeaderTop           // solid fallback যেখানে gradient দরকার নেই
private val HeaderCream  = Color(0xFFF7F3E9)
private val HeaderSub    = Color(0xFFCFEBF0)
private val GoldAccent   = Color(0xFFC9A24B)
private val TabInactiveBg= Color(0xFFEFE9D8)
private val TabBorder    = Color(0xFFD8D0B8)
private val TextMain     = Color(0xFF221F1A)
private val TextMuted    = Color(0xFF6B6552)
private val AnswerBg     = Color(0xFFEAF1E4)
private val AnswerText   = Color(0xFF2C4728)
private val DashedLine   = Color(0xFFCFC6AC)

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

// "fillblank" ফরম্যাটে (ইংরেজি) question-এ blank-এর জায়গায় ___/…../.... থাকলে সেখানে
// answer সরাসরি ইনলাইন (বোল্ড+আন্ডারলাইন) বসিয়ে দেখানো হয় — আলাদা "উত্তর:" বক্স লাগে না।
// Blank marker না পাওয়া গেলে null ফেরত দেয়, তখন caller স্বাভাবিক Q+A-বক্স ফলব্যাক করবে।
private val BLANK_REGEX = Regex("_{2,}|\\.{4,}|…{2,}")
private fun buildFillBlankText(question: String, answer: String): AnnotatedString? {
    val m = BLANK_REGEX.find(question) ?: return null
    return buildAnnotatedString {
        append(question.substring(0, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AnswerText, textDecoration = TextDecoration.Underline)) {
            append(answer)
        }
        append(question.substring(m.range.last + 1))
    }
}

private val ExplainPanel = Color(0xFFEFE9D8)
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
private val WRITTEN_SUBJECT_BUCKET = mapOf(
    "QB01" to "ইংরেজি",       // English Grammar
    "QB02" to "ইংরেজি",       // English Literature
    "QB03" to "সাধারণ জ্ঞান", // আন্তর্জাতিক
    "QB04" to "সাধারণ জ্ঞান", // কম্পিউটার
    "QB05" to "গণিত",         // গণিত
    "QB06" to "বাংলা",        // বাংলা ব্যাকরণ
    "QB07" to "বাংলা",        // বাংলা সাহিত্য
    "QB08" to "সাধারণ জ্ঞান", // বাংলাদেশ বিষয়াবলী
    "QB09" to "সাধারণ জ্ঞান"  // সাধারণ বিজ্ঞান
)
private val WRITTEN_SUBJECT_ORDER = listOf("বাংলা", "ইংরেজি", "গণিত", "সাধারণ জ্ঞান")

// ── top-level করা হলো (আগে QBankExamPaperScreen()-এর ভিতরে local fun ছিল) — এখন
// ExamSerialCard/ExamQAItem থেকেও সরাসরি কল করা যায়, selectedSubject থ্রেড করে
// পাঠাতে হয় না (q.subjectId থেকেই সবসময় নির্ভুলভাবে বের করা যায়) ──
private fun subjectLabelOf(q: QuestionItem): String =
    WRITTEN_SUBJECT_BUCKET[q.subjectId] ?: "অন্যান্য"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QBankExamPaperScreen(
    institutionName : String,
    postName        : String,
    questions       : List<QuestionItem>,
    onBack          : () -> Unit,
    onBookmark      : (String) -> Unit,
    onReport        : (globalIndex: Int, issue: String) -> Unit
) {
    // ── FIX: এখন subjectId-কে সরাসরি (Room reference-নাম না) ৪-বাকেট কনসোলিডেশন
    // ম্যাপ দিয়ে রেজল্ভ করা হয় — written অংশে সবসময় ঠিক ৪টা সাবজেক্ট-ট্যাবই দেখাবে
    // (top-level subjectLabelOf() ব্যবহার হচ্ছে, দেখো ফাইলের ওপরে) ──
    fun subjectLabel(q: QuestionItem): String = subjectLabelOf(q)

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
    val serials = remember(subjectQuestions) {
        val out = mutableListOf<Serial>()
        var i = 0
        while (i < subjectQuestions.size) {
            val q = subjectQuestions[i]
            if (q.isGrouped()) {
                val mates = subjectQuestions.filter { it.groupId == q.groupId }
                out.add(Serial(q.groupId, mates.sortedBy { it.subIndex }))
                i += mates.size
            } else {
                out.add(Serial(q.id, listOf(q)))
                i += 1
            }
        }
        out
    }

    var reportTarget by remember { mutableStateOf<QuestionItem?>(null) }

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
                    Text(
                        text = institutionName,
                        color = Color(0xFFFFE9A8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(text = postName, color = HeaderCream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(text = "প্রশ্নব্যাংক · ${questions.size} প্রশ্ন", color = HeaderSub, fontSize = 12.sp)
                }
            }
        }

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
            itemsIndexed(serials, key = { _, s -> s.key }) { idx, serial ->
                ExamSerialCard(
                    serialNo     = idx + 1,
                    serial       = serial.items,
                    onBookmark   = onBookmark,
                    onSpeak      = { text, key -> TtsManager.speak(text, key) },
                    onReportTap  = { q -> reportTarget = q }
                )
                Divider(color = DashedLine, thickness = 1.dp)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
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

/** একটা "সিরিয়াল" — হয় একটা একক প্রশ্ন, অথবা একই groupId-এর একগুচ্ছ sub-part (ক/খ/গ...) */
@Composable
private fun ExamSerialCard(
    serialNo    : Int,
    serial      : List<QuestionItem>,
    onBookmark  : (String) -> Unit,
    onSpeak     : (String, String) -> Unit,
    onReportTap : (QuestionItem) -> Unit
) {
    val labels = listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // ── সিরিয়াল-নম্বর ব্যাজ — একক প্রশ্ন হোক বা মাল্টি-পার্ট গ্রুপ, সবসময় থাকে
        // (গণিত/সাধারণ জ্ঞানে হেডিং না থাকলেও অন্তত এই নম্বরটা দিয়ে প্রশ্ন শুরু হয়) ──
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HeaderBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$serialNo", color = HeaderCream, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        if (serial.size == 1) {
            // ── একক প্রশ্ন — নাম্বার + প্রশ্ন + উত্তর সরাসরি ──
            val q = serial[0]
            Column(Modifier.weight(1f)) {
                ExamQAItem(prefixLabel = null, q = q, onBookmark = onBookmark, onSpeak = onSpeak, onReportTap = onReportTap)
            }
        } else if (serial.first().formatStyle == "table") {
            // ── PAPER COMPOSER "Table" ফরম্যাট (যেমন সন্ধি বিচ্ছেদ) — ক/খ/গ স্ট্যাক না
            // করে সত্যিকারের দুই-কলাম টেবিল: শব্দ | ব্যাখ্যা/বিচ্ছেদ ──
            Column(Modifier.weight(1f)) {
                if (serial.first().groupHeading.isNotBlank()) {
                    Text(
                        text = serial.first().groupHeading,
                        fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextMain
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("শব্দ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(0.38f))
                    Text("ব্যাখ্যা / বিচ্ছেদ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(0.62f))
                }
                serial.forEachIndexed { idx, sub ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(text = sub.question, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMain, modifier = Modifier.weight(0.38f))
                        Text(text = sub.answer, fontSize = 12.5.sp, color = AnswerText, modifier = Modifier.weight(0.62f))
                    }
                    if (idx < serial.lastIndex) Divider(color = DashedLine, thickness = 0.7.dp)
                }
            }
        } else {
            // ── multi-part গ্রুপ — একটা গ্রুপ-হেডিং লাইনের নিচে ক/খ/গ... ──
            Column(Modifier.weight(1f)) {
                // ── SIMPLIFIED ("হেডিং অন করে টেক্সট বসালেই তো হবে"): আগে প্রথম sub-
                // question-এর নিজের question টেক্সট হেডিং হিসেবে (ভুলভাবে) রিইউজ করা
                // হতো, প্লাস একটা আলাদা showGroupHeading বুলিয়ান থাকতো — এখন Admin
                // App-এ যা টাইপ করা হয়েছে (groupHeading) সেটাই সরাসরি এখানে বসে, খালি
                // থাকলে (থিওরিটে হওয়ার কথা না — গ্রুপ থাকা মানেই হেডিং টাইপ করা হয়েছে,
                // কারণ group_id নিজেই Admin App-এ groupHeading টাইপ করলে তবেই বসে) কোনো
                // হেডিং-লাইন দেখাবে না, নিরাপত্তার জন্য fallback হিসেবে। ──
                if (serial.first().groupHeading.isNotBlank()) {
                    Text(
                        text = serial.first().groupHeading,
                        fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextMain
                    )
                    Spacer(Modifier.height(8.dp))
                }
                serial.forEachIndexed { idx, sub ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(
                            text = (labels.getOrNull(idx) ?: "${idx + 1}") + ")",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8A6D1D),
                            modifier = Modifier.width(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            ExamQAItem(prefixLabel = null, q = sub, compact = true, onBookmark = onBookmark, onSpeak = onSpeak, onReportTap = onReportTap)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamQAItem(
    prefixLabel : String?,
    q           : QuestionItem,
    compact     : Boolean = false,
    onBookmark  : (String) -> Unit,
    onSpeak     : (String, String) -> Unit,
    onReportTap : (QuestionItem) -> Unit
) {
    var detailOpen by remember(q.id) { mutableStateOf(false) }
    var detailKind by remember(q.id) { mutableStateOf("explain") } // "explain" | "technique"
    var bookmarked by remember(q.id, q.isBookmarked) { mutableStateOf(q.isBookmarked) }

    Column {
        // ── PAPER COMPOSER: formatStyle অনুযায়ী প্রশ্ন-টেক্সট রেন্ডার আলাদা হয় ──
        val questionFontSize = if (compact) 13.8.sp else 14.5.sp
        val questionLineHeight = if (compact) 19.sp else 21.sp
        val inlineFillBlank = if (q.formatStyle == "fillblank" && q.answer.isNotBlank())
            buildFillBlankText(q.question, q.answer) else null

        when {
            inlineFillBlank != null -> Text(text = inlineFillBlank, fontSize = questionFontSize, lineHeight = questionLineHeight, color = TextMain)
            q.formatStyle == "highlight" -> Text(text = buildHighlightedText(q.question), fontSize = questionFontSize, lineHeight = questionLineHeight, color = TextMain)
            else -> Text(text = q.question, fontSize = questionFontSize, lineHeight = questionLineHeight, color = TextMain)
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
        if (q.answer.isNotBlank() && inlineFillBlank == null) {
            val labelPrefix = if (subjectLabelOf(q) == "ইংরেজি") "" else "উত্তর: "
            Box(
                Modifier.clip(RoundedCornerShape(3.dp)).background(AnswerBg).padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
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
            Box(
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
                ExamIconButton(emoji = "🔊") { onSpeak(q.question + ". উত্তর: " + q.answer, q.id) }
                ExamIconButton(emoji = if (bookmarked) "★" else "☆", active = bookmarked) {
                    bookmarked = !bookmarked
                    onBookmark(q.id)
                }
                ExamIconButton(emoji = "⚑") { onReportTap(q) }
            }
        }

        if (detailOpen) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                DetailTab("ব্যাখ্যা", detailKind == "explain") { detailKind = "explain" }
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
                Text(
                    text = panelText.ifBlank { "এখনো যোগ করা হয়নি।" },
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = if (panelText.isBlank()) TextMuted else TextMain,
                    fontStyle = if (panelText.isBlank()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                )
            }
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
