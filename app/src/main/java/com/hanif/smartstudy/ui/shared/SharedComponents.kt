package com.hanif.smartstudy.ui.shared

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.local.LocalTechniqueStore
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.remote.FirebaseDataService
import com.hanif.smartstudy.data.remote.ImgBbResult
import com.hanif.smartstudy.data.remote.ImgBbService
import com.hanif.smartstudy.ui.components.RichContentText
import com.hanif.smartstudy.data.remote.ApiResult
import com.hanif.smartstudy.ui.theme.LocalDarkMode
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.ui.theme.NordicSage
import com.hanif.smartstudy.ui.theme.NordicSageTint
import com.hanif.smartstudy.ui.theme.NordicBlue
import com.hanif.smartstudy.ui.theme.NordicBlueTint
import com.hanif.smartstudy.ui.theme.NordicClay
import com.hanif.smartstudy.ui.theme.NordicClayTint
import com.hanif.smartstudy.ui.theme.NordicInk
import com.hanif.smartstudy.ui.theme.NordicMuted
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

val Indigo600   = Color(0xFF4F46E5)
val DeepIndigo  = Color(0xFF1E1B4B)
val GreenOk     = Color(0xFF10B981)
val RedWrong    = Color(0xFFEF4444)
val AmberWarn   = Color(0xFFF59E0B)
val OrangeTech  = Color(0xFFF97316)
// Legacy light-only constants — use AppColors composable for theme-aware colors
val SlateText   = Color(0xFF1E293B)
val MutedText   = Color(0xFF64748B)
val CardBg      = Color(0xFFFFFFFF)
val SlateLight  = Color(0xFFF8FAFC)

// ── Theme-aware color helpers (dark mode safe) ─────────────────
object AppColors {
    val slateText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val mutedText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg: Color    @Composable get() = MaterialTheme.colorScheme.surface
}

// ── ছোট পিল বাটন — প্রশ্ন/অপশন/উত্তর এর পাশে admin edit bar-এ ব্যবহৃত ──
@Composable
fun AdminEditPillButton(label: String, bg: Color, fg: Color, border: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(6.dp),
        color   = bg,
        border  = BorderStroke(1.dp, border)
    ) {
        Text(
            label,
            modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold,
            color      = fg,
            fontFamily = NotoSansBengali
        )
    }
}

// ── উত্তর/ব্যাখ্যা/টেকনিক বক্সের ভেতরে ছোট ✎ আইকন (top-right) ──
@Composable
fun AdminBoxEditIcon(tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(20.dp)) {
        Icon(Icons.Default.Edit, null, tint = tint, modifier = Modifier.size(13.dp))
    }
}

// ── "পড়া হয়েছে" চেকমার্ক — এখন উপরের আইকন-রো তে না থেকে, উত্তর বক্সের
//    হেডারে এডিট বাটনের পাশে বসে (Admin edit icon-এর চেয়ে একটু বড়, যাতে
//    সহজে চোখে পড়ে ও চাপা যায়)। ক্লিক করলে টিক টগল হয়, তালিকার নিচে চলে যাবে ──
@Composable
fun StudyDoneCheckIcon(done: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = if (done) "পড়া হয়েছে" else "পড়া হয়েছে চিহ্নিত করো",
            tint     = if (done) GreenOk else Color(0xFFCBD5E1),
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun TimerBar(
    timerSec  : Int,
    totalSec  : Int,
    modifier  : Modifier = Modifier
) {
    val pct     = if (totalSec > 0) timerSec.toFloat() / totalSec else 0f
    val animPct by animateFloatAsState(pct, tween(800), label = "timer")
    val color   = when {
        pct > 0.5f -> GreenOk
        pct > 0.2f -> AmberWarn
        else       -> RedWrong
    }
    val m = timerSec / 60
    val s = timerSec % 60

    Column(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⏱", fontSize = 14.sp)
            Text(
                text       = "%d:%02d".format(m, s),
                fontSize   = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = color,
                fontFamily = NotoSansBengali
            )
        }
        Box(
            Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animPct)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f))))
            )
        }
    }
}

@Composable
fun ReadingProgressBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    val pct     = if (total > 0) current.toFloat() / total else 0f
    val animPct by animateFloatAsState(pct, tween(300), label = "readProg")
    Box(modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            Modifier.fillMaxWidth(animPct).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Indigo600, Color(0xFF818CF8))))
        )
    }
}

@Composable
fun QuestionCard(
    index          : Int,
    item           : QuestionItem,
    mode           : StudyMode,
    totalCount     : Int       = 0,
    onMcqAnswer    : (Int) -> Unit,
    onWritten      : (String) -> Int,
    onWrittenDraft : (String) -> Unit = {},
    // ── Model Test-এর Written প্রশ্নে: টাইপ করার বদলে "উত্তর দেখুন" + ঠিক/ভুল বাটন ──
    isModelTest        : Boolean = false,
    onWrittenSelfGrade : (Boolean) -> Unit = {},
    onBookmark     : () -> Unit,
    onStudyDone    : () -> Unit = {},
    onReport       : () -> Unit,
    // ── Study রিকল-টাইপিং মোড: ⌨️ আইকন টগল চালু থাকলে টাইপ-বক্সে উত্তর
    //    লিখে Enter চাপলে আসল উত্তর দেখা যায়, তারপর ঠিক/ভুল বেছে Enter
    //    চাপলেই পরের প্রশ্নের টাইপ-বক্সে কার্সর চলে যায়। টগল বন্ধ থাকলে
    //    সবকিছু আগের মতোই (normal) কাজ করবে। ──
    answerFocusRequester : FocusRequester? = null,
    onRecallGraded       : (Boolean) -> Unit = {},
    studyRecallMode      : Boolean = false,
    // ── ⌨️ রিকল-টাইপিং মোডে Written উত্তর AI দিয়ে অটো-চেক করার জন্য —
    //    Settings-এ সেভ করা key দিয়ে Groq→Mistral→Cerebras→Gemini ক্রমে চেষ্টা হয়।
    //    null রিটার্ন করলে (কোনো key নেই / সব ব্যর্থ) সাথে সাথেই ম্যানুয়াল
    //    ঠিক/ভুল বাটনে ফলব্যাক করে — parameter না দিলে আচরণ আগের মতোই থাকবে।
    onAiGradeWritten     : (suspend (question: String, correctAnswer: String, userAnswer: String) -> Boolean?)? = null,
    currentUser    : User?     = null,
    onAdminRefresh : (() -> Unit)? = null,
    onAdminEdit    : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null,
    onAdminDelete  : ((sheet: String, rowKey: String, preview: String) -> Unit)? = null,
    studyRevealMode: Boolean = false,
    modifier       : Modifier = Modifier
) {
    val isAdminUser = currentUser?.isAdmin() == true
    var activeEditField by remember { mutableStateOf<String?>(null) }

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // ── _studyNoQ logic — index.html এর মতো ──
            // Study mode এ প্রশ্ন না থাকলে explanation/answer কেই প্রশ্ন হিসেবে দেখাবে
            // (links সহ — PDF/image/video সব render হবে)
            // (TTS speak বাটনের জন্য আইকন রো-এর আগেই হিসেব করে নেওয়া হলো)
            val studyNoQ = mode == StudyMode.STUDY && item.question.isBlank()
            val displayQuestion = if (studyNoQ) (item.explanation.ifBlank { item.answer }) else item.question
            val displayExplanation = if (studyNoQ) "" else item.explanation

            // TTS তে প্রশ্ন + উত্তর একটাই স্পিচে জোড়া লাগানো হয় (combinedText, নিচে দেখুন),
            // তাই AnswerBox কে জানাতে হবে সে ওই মিলিত টেক্সটের কোন char-position থেকে শুরু —
            // নাহলে highlight ভুল জায়গায় (এলোমেলোভাবে) হয়
            val ttsSeparator = "। উত্তর। "
            val ttsAnswerText = item.answer.ifBlank { item.explanation }
            val ttsAnswerOffset = displayQuestion.length + ttsSeparator.length

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Indigo600.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("#${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                            color = Indigo600, fontFamily = NotoSansBengali)
                    }
                    if (item.isWritten()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(OrangeTech.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Written", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = OrangeTech)
                        }
                    }
                    if (item.year.isNotBlank()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(GreenOk.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(item.year, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenOk)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ── 🔊 প্রশ্ন + উত্তর একসাথে শুনো — শুধু Study mode এ ──
                    if (mode == StudyMode.STUDY && displayQuestion.isNotBlank()) {
                        val ttsKey = "${item.id}_qa"
                        val speakingKey by com.hanif.smartstudy.util.TtsManager.speakingKey.collectAsState()
                        val pausedKey by com.hanif.smartstudy.util.TtsManager.pausedKey.collectAsState()
                        val isThisSpeaking = speakingKey == ttsKey
                        val isThisPaused = pausedKey == ttsKey
                        val combinedText = buildString {
                            append(displayQuestion)
                            if (ttsAnswerText.isNotBlank()) { append(ttsSeparator); append(ttsAnswerText) }
                        }
                        IconButton(
                            onClick = { com.hanif.smartstudy.util.TtsManager.speak(combinedText, ttsKey) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                when {
                                    isThisSpeaking -> Icons.Default.Pause
                                    isThisPaused   -> Icons.Default.PlayArrow
                                    else           -> Icons.Default.VolumeUp
                                },
                                contentDescription = when {
                                    isThisSpeaking -> "পজ করো"
                                    isThisPaused   -> "আবার চালু করো"
                                    else           -> "প্রশ্ন ও উত্তর শুনো"
                                },
                                tint = if (isThisSpeaking || isThisPaused) Indigo600 else Color(0xFFCBD5E1),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = onBookmark, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (item.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            null,
                            tint     = if (item.isBookmarked) AmberWarn else Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onReport, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Flag, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // প্রশ্ন — QuestionText (LaTeX support আছে) + RichContentText (link support)
            // studyNoQ তে explanation-ই প্রশ্ন, সেটায় link থাকতে পারে
            if (studyNoQ) {
                // Explanation as question — RichContentText (PDF/image/video লিংক render করবে)
                RichContentText(
                    text      = displayQuestion,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    fontSize  = 15
                )
            } else {
                // সাধারণ প্রশ্ন — QuestionText (LaTeX support + Study mode এ word-highlight TTS)
                QuestionText(
                    text   = displayQuestion,
                    ttsKey = if (mode == StudyMode.STUDY) "${item.id}_qa" else null
                )
            }

            if (isAdminUser) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AdminEditPillButton("✎ প্রশ্ন", Color(0xFFEFF6FF), Color(0xFF1D4ED8), Color(0xFFBFDBFE)) {
                        activeEditField = "question"
                    }
                    if (item.isMcq()) {
                        AdminEditPillButton("✎ ক", Color(0xFFF0FDF4), Color(0xFF15803D), Color(0xFFBBF7D0)) { activeEditField = "optA" }
                        AdminEditPillButton("✎ খ", Color(0xFFF0FDF4), Color(0xFF15803D), Color(0xFFBBF7D0)) { activeEditField = "optB" }
                        AdminEditPillButton("✎ গ", Color(0xFFF0FDF4), Color(0xFF15803D), Color(0xFFBBF7D0)) { activeEditField = "optC" }
                        AdminEditPillButton("✎ ঘ", Color(0xFFF0FDF4), Color(0xFF15803D), Color(0xFFBBF7D0)) { activeEditField = "optD" }
                    }
                    if (item.isMcq() || item.isWritten()) {
                        AdminEditPillButton("✎ উত্তর", Color(0xFFFEF2F2), Color(0xFFDC2626), Color(0xFFFECACA)) { activeEditField = "answer" }
                    }
                }
            }

            // imageUrl field — comma separated multiple images support
            if (item.imageUrl.isNotBlank()) {
                val imgUrls = item.imageUrl.split(",").map { it.trim() }.filter { it.isNotBlank() }
                imgUrls.forEach { singleUrl ->
                    Spacer(Modifier.height(6.dp))
                    ZoomableImage(url = singleUrl)
                }
            }

            // VisualURL field — আলাদাভাবে থাকলে (image/video/pdf লিংক)
            if (item.visualUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                RichContentText(text = item.visualUrl)
            }

            Spacer(Modifier.height(10.dp))

            when {
                item.isMcq() && mode != StudyMode.STUDY -> {
                    McqOptions(item = item, onAnswer = onMcqAnswer)
                }
                item.isWritten() && mode == StudyMode.QBANK && !isModelTest -> {
                    // ── QBank-এর Written প্রশ্নে সরাসরি টাইপ-বক্স দেখা যাবে (Study-র মতো
                    // আলাদা কীবোর্ড আইকন টগলের দরকার নেই) — উত্তর লেখার পর AI দিয়ে অটো-চেক
                    // হয় (Study রিকল-টাইপিং মোডের একই নিয়মে), AI ব্যর্থ হলে সাথে সাথেই
                    // ম্যানুয়াল ঠিক/ভুল বাটনে ফলব্যাক করে। ──
                    WrittenAiRecallCheck(
                        item             = item,
                        onGrade          = onWrittenSelfGrade,
                        onAiGradeWritten = onAiGradeWritten
                    )
                }
                item.isWritten() && mode != StudyMode.STUDY -> {
                    // ── Model Test ও Quiz-এর Written প্রশ্নে এখনো টাইপ-করে-মেলানোর বদলে
                    // "উত্তর দেখুন" + ঠিক/ভুল সেলফ-গ্রেড UI ব্যবহার হয় — এতে ফলাফল গণনা
                    // MCQ-এর মতোই নির্ভুল ও সামঞ্জস্যপূর্ণ হয়। ──
                    WrittenRevealSelfGrade(item = item, onGrade = onWrittenSelfGrade)
                }
                else -> {}
            }

            // ── Study: "শুধু প্রশ্ন দেখ" (👁️) মোড — উত্তর/ব্যাখ্যা/টেকনিক লুকিয়ে
            //    শুধু "উত্তর দেখুন" বাটন দেখায়, ট্যাপ করলেই প্রকাশ হয়ে যায়।
            // ── Study: রিকল-টাইপিং (⌨️) মোড — এটা আলাদা, স্বতন্ত্র টগল। চালু
            //    থাকলে উত্তর দেখার আগে একটা টাইপ-বক্সে নিজের উত্তর লিখতে হবে,
            //    Enter চাপলে আসল উত্তর দেখা যায়, তারপর ঠিক/ভুল বেছে Enter
            //    চাপলেই পরের প্রশ্নের টাইপ-বক্সে কার্সর চলে যায়। বন্ধ থাকলে
            //    সবকিছু আগের মতোই (normal) আচরণ করবে। ──
            val hasAnswerContent  = !studyNoQ && item.answer.isNotBlank()
            val studyHideAnswer   = mode == StudyMode.STUDY && studyRevealMode && hasAnswerContent
            val studyRecallActive = mode == StudyMode.STUDY && studyRecallMode && hasAnswerContent
            var isRevealed by remember(item.id) { mutableStateOf(false) }
            // ── রিকল-টাইপিং স্টেট: টাইপ করা উত্তর ও গ্রেড হয়েছে কিনা ──
            var recallTypedAnswer by remember(item.id) { mutableStateOf("") }
            var recallGraded by remember(item.id) { mutableStateOf(false) }
            // ── AI অটো-চেক স্টেট: চেক চলছে কিনা, আর চেষ্টা করে ব্যর্থ হয়েছে কিনা
            // (ব্যর্থ হলে সাথে সাথেই নিচের ম্যানুয়াল ঠিক/ভুল বাটনে ফলব্যাক হয়) ──
            var aiChecking by remember(item.id) { mutableStateOf(false) }
            var aiFailed by remember(item.id) { mutableStateOf(false) }
            // ── AI ফলাফল (ঠিক/ভুল) সাথে সাথেই পরের প্রশ্নে না গিয়ে কিছুক্ষণ
            // দেখানোর জন্য — যতক্ষণ না ৫ সেকেন্ডের বিরতি শেষ হয়ে recallGraded
            // হয়, ততক্ষণ এই ভ্যারিয়েবলে ফলাফল ধরে রাখা হয় ──
            var aiVerdict by remember(item.id) { mutableStateOf<Boolean?>(null) }

            if (studyRecallActive && !isRevealed) {
                // ── ⌨️ রিকল-টাইপিং মোড: টাইপ-বক্স, Enter চাপলে উত্তর দেখাবে ──
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = recallTypedAnswer,
                    onValueChange = { recallTypedAnswer = it },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .then(
                            if (answerFocusRequester != null) Modifier.focusRequester(answerFocusRequester)
                            else Modifier
                        ),
                    singleLine    = true,
                    placeholder   = {
                        Text(
                            "এখানে তোমার উত্তর লিখো, এন্টার চাপলে আসল উত্তর দেখাবে…",
                            fontFamily = NotoSansBengali, color = Color(0xFFCBD5E1), fontSize = 13.sp
                        )
                    },
                    shape           = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { isRevealed = true }),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Indigo600,
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )
            } else if (studyHideAnswer && !isRevealed) {
                // ── 👁️ শুধু-প্রশ্ন-দেখ মোড (রিকল-টাইপিং বন্ধ থাকলে): আগের মতোই সাধারণ বাটন ──
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick  = { isRevealed = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("উত্তর দেখুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {

            // ── ⌨️ রিকল-টাইপিং মোডে উত্তর প্রকাশ হওয়ার সাথে সাথেই (Settings-এ key
            // সেভ করা থাকলে) AI দিয়ে অটো-চেক শুরু হয়। AI নির্দিষ্ট সঠিক/ভুল বলতে
            // পারলে recallGraded=true হয়ে সরাসরি পরের প্রশ্নে চলে যায় — ম্যানুয়াল
            // বাটনে চাপ দিতে হয় না। AI না থাকলে বা ব্যর্থ হলে (aiFailed=true) নিচের
            // "ঠিক হয়েছে/ভুল হয়েছে" বাটন সাথে সাথেই স্বাভাবিকভাবে দেখা যাবে ──
            if (studyRecallActive && onAiGradeWritten != null) {
                LaunchedEffect(item.id, isRevealed) {
                    if (isRevealed && !recallGraded && !aiFailed && !aiChecking && aiVerdict == null) {
                        aiChecking = true
                        val verdict = runCatching {
                            onAiGradeWritten(displayQuestion, item.answer, recallTypedAnswer)
                        }.getOrNull()
                        aiChecking = false
                        if (verdict != null) {
                            // ── ফলাফল (ঠিক/ভুল) আগে স্ক্রিনে দেখাও, ৫ সেকেন্ড
                            // অপেক্ষা করো, তারপরই পরের প্রশ্নে অটো-এগিয়ে যাও —
                            // যাতে ইউজার ফলাফল দেখার আগেই পরের প্রশ্নে চলে না যায় ──
                            aiVerdict = verdict
                            kotlinx.coroutines.delay(5000)
                            recallGraded = true
                            onRecallGraded(verdict)
                        } else {
                            aiFailed = true
                        }
                    }
                }
            }

            val showAnswerBox = when (mode) {
                StudyMode.STUDY -> true
                else -> item.answerState !is AnswerState.Unanswered
            }
            // MCQ তে সবুজ/লাল রঙে অপশনেই উত্তর বোঝা যায় — আলাদা AnswerBox দরকার নেই
            val showAnswerText = showAnswerBox && (!item.isMcq() || item.isStudy())
            // studyNoQ হলে answer already question হিসেবে দেখানো হয়েছে — আবার দেখানো দরকার নেই
            if (showAnswerText && item.answer.isNotBlank() && !studyNoQ) {
                Spacer(Modifier.height(8.dp))
                val answerTtsKey = if (mode == StudyMode.STUDY) "${item.id}_qa" else null
                if (studyRecallActive && recallTypedAnswer.isNotBlank()) {
                    Text(
                        "তোমার উত্তর: $recallTypedAnswer",
                        fontSize   = 11.sp,
                        fontFamily = NotoSansBengali,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier   = Modifier.padding(bottom = 6.dp)
                    )
                }
                AnswerBox(
                    text        = item.answer,
                    ttsKey      = answerTtsKey,
                    ttsOffset   = ttsAnswerOffset,
                    onEdit      = if (isAdminUser) ({ activeEditField = "answer" }) else null,
                    isStudyDone = item.isStudyDone,
                    onStudyDone = if (mode == StudyMode.STUDY) onStudyDone else null
                )
            } else if (mode == StudyMode.STUDY) {
                // ── উত্তর বক্স না থাকলেও (যেমন studyNoQ বা খালি answer) "পড়া হয়েছে"
                // চেকমার্কটা যেন হারিয়ে না যায়, তাই একই জায়গায় আলাদাভাবে দেখানো হলো ──
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StudyDoneCheckIcon(done = item.isStudyDone, onClick = onStudyDone)
                }
            }

            // ── AI ফলাফল (ঠিক/ভুল) নির্ধারণ হয়ে গেলে সাথে সাথেই পরের প্রশ্নে
            // না গিয়ে ৫ সেকেন্ড এই ব্যানারে ফলাফল দেখানো হয় (উপরের LaunchedEffect-এ
            // delay(5000) চলাকালীন) — তারপর অটো পরের প্রশ্নে চলে যায়। ──
            if (studyRecallActive && !recallGraded && aiVerdict != null) {
                Spacer(Modifier.height(10.dp))
                val verdictIsCorrect = aiVerdict == true
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = (if (verdictIsCorrect) GreenOk else RedWrong).copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, if (verdictIsCorrect) GreenOk else RedWrong)
                ) {
                    Text(
                        if (verdictIsCorrect) "✅ ঠিক হয়েছে (AI)" else "❌ ভুল হয়েছে (AI)",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = if (verdictIsCorrect) GreenOk else RedWrong,
                        fontFamily = NotoSansBengali,
                        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier   = Modifier.padding(12.dp).fillMaxWidth()
                    )
                }
            }

            // ── AI অটো-চেক চলাকালীন লোডিং ইন্ডিকেটর — এই সময় নিচের ম্যানুয়াল
            // বাটন দেখানো হয় না, AI ব্যর্থ হলেই (aiFailed) সাথে সাথে দেখা যাবে ──
            if (studyRecallActive && !recallGraded && aiChecking) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Indigo600)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🤖 AI দিয়ে যাচাই করা হচ্ছে…",
                        fontFamily = NotoSansBengali, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── ⌨️ রিকল-টাইপিং: উত্তর দেখানোর পর "ঠিক হয়েছে"/"ভুল হয়েছে" বাছাই —
            //    ডিফল্ট ফোকাস "ঠিক হয়েছে"-তে থাকে, হার্ডওয়্যার কীবোর্ডে Tab দিয়ে
            //    দুই বাটনের মধ্যে টগল করা যায়, আর Enter চাপলেই সেই গ্রেড কনফার্ম
            //    হয়ে পরের প্রশ্নের টাইপ-বক্সে ফোকাস চলে যায় — পুরোটা কীবোর্ড
            //    দিয়েই দ্রুত করা যায়, টাচ লাগে না।
            //    ── AI চালু থাকলে (onAiGradeWritten != null) এই ম্যানুয়াল বাটন তখনই
            //    দেখা যাবে যখন AI চেক শেষে ব্যর্থ হয়েছে (aiFailed) — অন্যথায় AI নিজেই
            //    অটো-গ্রেড করে দেবে, ইউজারকে বাটনে চাপ দিতে হবে না। ──
            val showManualGradeButtons = studyRecallActive && !recallGraded && !aiChecking &&
                (onAiGradeWritten == null || aiFailed)
            if (showManualGradeButtons) {
                val correctFocusRequester = remember(item.id) { FocusRequester() }
                var correctFocused by remember(item.id) { mutableStateOf(false) }
                var wrongFocused by remember(item.id) { mutableStateOf(false) }
                LaunchedEffect(item.id) {
                    kotlinx.coroutines.delay(80)
                    runCatching { correctFocusRequester.requestFocus() }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick  = { recallGraded = true; onRecallGraded(true) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(correctFocusRequester)
                            .onFocusChanged { correctFocused = it.isFocused },
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenOk),
                        border   = if (correctFocused) BorderStroke(2.dp, Color.White) else null
                    ) {
                        Text("✅ ঠিক হয়েছে", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Button(
                        onClick  = { recallGraded = true; onRecallGraded(false) },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { wrongFocused = it.isFocused },
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = RedWrong),
                        border   = if (wrongFocused) BorderStroke(2.dp, Color.White) else null
                    ) {
                        Text("❌ ভুল হয়েছে", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }

            // explanation — studyNoQ তে empty, নাহলে দেখাও।
            // Private ব্যাখ্যা শুধু Admin দেখতে পাবে — সাধারণ ইউজারের কাছে হাইড থাকবে।
            val canSeeExplanation = item.explanationIsPublic || currentUser?.isAdmin() == true
            if (showAnswerBox && canSeeExplanation && displayExplanation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                ExplanationBox(
                    text    = displayExplanation,
                    onEdit  = if (isAdminUser) ({ activeEditField = "explanation" }) else null,
                    isAdmin = isAdminUser
                )
                if (!item.explanationIsPublic) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Private — শুধু আপনি (Admin) দেখছেন",
                            fontSize = 9.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B)
                        )
                    }
                }
            }

            // টেকনিক — কোনো প্রাইভেসি কনসেপ্ট নেই, তাই থাকলেই সবার জন্য
            // সাথে সাথে (কোনো ট্যাপ ছাড়াই) খোলা অবস্থায় দেখা যাবে —
            // অ্যাডমিন ব্যাখ্যার জন্য যেভাবে সবসময় auto-expanded থাকে,
            // এখানে isAdmin=true দিয়ে সবার জন্যই সেই একই আচরণ করা হলো।
            // এডিট বাটন অবশ্য এখনো শুধু admin-ই পাবে (onEdit চেক অপরিবর্তিত)।
            // ── এই আচরণ ইচ্ছাকৃতভাবেই অপরিবর্তিত — টেকনিক সবসময় সবার জন্য খোলা থাকবে ──
            if (showAnswerBox && item.technique.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                TechniqueBox(
                    text    = item.technique,
                    onEdit  = if (isAdminUser) ({ activeEditField = "technique" }) else null,
                    isAdmin = true
                )
            }

            if (showAnswerBox) {
                Spacer(Modifier.height(6.dp))
                UserTechniqueSection(
                    questionId  = item.id,
                    currentUser = currentUser
                )
            }
            }
        }
    }

    // ── Admin per-field এডিট পপআপ — activeEditField সেট হলে খুলে যায় ──
    val editField = activeEditField
    if (isAdminUser && editField != null) {
        val initialValue = when (editField) {
            "question"    -> item.question
            "optA"        -> item.optionA
            "optB"        -> item.optionB
            "optC"        -> item.optionC
            "optD"        -> item.optionD
            "answer"      -> item.answer
            "explanation" -> item.explanation
            "technique"   -> item.technique
            else          -> ""
        }
        AdminFieldEditDialog(
            item         = item,
            fieldId      = editField,
            initialValue = initialValue,
            onDismiss    = {
                activeEditField = null
                onAdminRefresh?.invoke()
            },
            onAdminEdit   = onAdminEdit,
            onAdminDelete = onAdminDelete
        )
    }
}

/**
 * ── ইংরেজি শব্দ চিনে আলাদা annotation tag বসিয়ে দেয় ──
 * AnnotatedString এর উপর "word" tag বসানো হয় শুধু ইংরেজি (A-Z/a-z ভিত্তিক) অংশে,
 * যাতে ক্লিক করলে বোঝা যায় কোন শব্দে ট্যাপ হয়েছে এবং সেটার শুদ্ধ ইংরেজি
 * উচ্চারণ আলাদাভাবে চালানো যায়।
 */
private val ENGLISH_WORD_REGEX = Regex("[A-Za-z][A-Za-z0-9.\\-]*")

private fun annotateEnglishWords(builder: androidx.compose.ui.text.AnnotatedString.Builder, text: String) {
    for (match in ENGLISH_WORD_REGEX.findAll(text)) {
        builder.addStringAnnotation(
            tag = "EN_WORD",
            annotation = match.value,
            start = match.range.first,
            end = match.range.last + 1
        )
    }
}

/**
 * ── সাধারণ Selectable + Tap-to-pronounce টেক্সট ──
 * SelectionContainer দিয়ে wrap করা থাকে — তাই আঙুল চেপে ধরলে টেক্সট সিলেক্ট করা যায়,
 * Android এর built-in selection toolbar এ Copy/Web Search/Share সব এমনিতেই আসে।
 * এছাড়া কোনো ইংরেজি শব্দে ট্যাপ করলে সেই শব্দের শুদ্ধ ইংরেজি উচ্চারণ তাৎক্ষণিকভাবে শোনায়।
 */
@Composable
fun SelectableSmartText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    lineHeight: Int = fontSize + 7
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            append(text)
            annotateEnglishWords(this, text)
        }
    }
    SelectionContainer(modifier = modifier) {
        ClickableText(
            text = annotated,
            style = androidx.compose.ui.text.TextStyle(
                fontSize   = fontSize.sp,
                fontWeight = fontWeight,
                fontFamily = NotoSansBengali,
                color      = color,
                lineHeight = lineHeight.sp
            ),
            onClick = { offset ->
                annotated.getStringAnnotations("EN_WORD", offset, offset).firstOrNull()?.let { ann ->
                    com.hanif.smartstudy.util.TtsManager.speakWord(ann.item)
                }
            }
        )
    }
}

/**
 * ── Word-highlighting TTS Text ──
 * TtsManager এর currentWordRange দেখে যেই word বলা হচ্ছে সেটা highlight করে।
 * ইতিমধ্যে বলা অংশ হালকা রঙে (already spoken), বর্তমান word বোল্ড+রঙিন ভাবে
 * (currently speaking), আর বাকি অংশ স্বাভাবিক রঙে দেখায়।
 * key মিলে গেলে তবেই highlight করে — অন্য card এর speech এ এটা react করে না।
 * SelectionContainer দিয়ে wrap করা — copy/web-search toolbar পাওয়া যায়, আর
 * ইংরেজি শব্দে ট্যাপ করলে তার শুদ্ধ ইংরেজি উচ্চারণ আলাদাভাবে শোনায়।
 */
@Composable
fun HighlightedSpeakingText(
    text: String,
    ttsKey: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
    fontWeight: FontWeight = FontWeight.Bold,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    // এই text টুকরাটা সম্পূর্ণ spoken (combined question+answer) টেক্সটের কোন
    // চরিত্র-অবস্থান থেকে শুরু হয়েছে — যাতে global word-range কে সঠিকভাবে
    // এই local text এর সাপেক্ষে বসানো যায় (নাহলে answer box এ এলোমেলো হাইলাইট হয়)
    spokenOffset: Int = 0
) {
    val activeKey by com.hanif.smartstudy.util.TtsManager.activeKeyFlow.collectAsState()
    val wordRange by com.hanif.smartstudy.util.TtsManager.currentWordRange.collectAsState()
    val isThisActive = activeKey == ttsKey

    val annotated = remember(text, isThisActive, wordRange, spokenOffset) {
        buildAnnotatedString {
            val range = wordRange
            if (!isThisActive || range == null) {
                withStyle(SpanStyle(color = baseColor)) { append(text) }
            } else {
                // global word-range কে এই box এর local coordinate এ আনা হলো
                val localStart = range.first - spokenOffset
                val localEnd   = range.last  - spokenOffset

                when {
                    // এখনো এই box এর অংশ বলা শুরু হয়নি (আগে অন্য অংশ — যেমন প্রশ্ন — বলা হচ্ছে)
                    localEnd <= 0 -> {
                        withStyle(SpanStyle(color = baseColor)) { append(text) }
                    }
                    // এই box এর অংশ বলা ইতিমধ্যে শেষ (এখন পরের অংশ — যেমন উত্তর — বলা হচ্ছে)
                    localStart >= text.length -> {
                        withStyle(SpanStyle(color = baseColor.copy(alpha = 0.38f))) { append(text) }
                    }
                    // এই box এর ভেতরেই এখন বলা হচ্ছে — সঠিক word টুকু হাইলাইট করো
                    else -> {
                        val highlightStart = localStart.coerceIn(0, text.length)
                        val highlightEnd   = localEnd.coerceIn(0, text.length)
                        // ইতিমধ্যে বলা অংশ — হালকা ফিকে রঙ (পড়া শেষ বোঝানোর জন্য)
                        if (highlightStart > 0) {
                            withStyle(SpanStyle(color = baseColor.copy(alpha = 0.38f))) {
                                append(text.substring(0, highlightStart))
                            }
                        }
                        // বর্তমান word — হাইলাইট রঙ + bold + সামান্য background tint
                        if (highlightEnd > highlightStart) {
                            withStyle(
                                SpanStyle(
                                    color = Indigo600,
                                    fontWeight = FontWeight.ExtraBold,
                                    background = Indigo600.copy(alpha = 0.16f)
                                )
                            ) {
                                append(text.substring(highlightStart, highlightEnd))
                            }
                        }
                        // বাকি অংশ — যা এখনো বলা হয়নি, স্বাভাবিক রঙ
                        if (highlightEnd < text.length) {
                            withStyle(SpanStyle(color = baseColor)) {
                                append(text.substring(highlightEnd))
                            }
                        }
                    }
                }
            }
            annotateEnglishWords(this, text)
        }
    }

    SelectionContainer(modifier = modifier) {
        ClickableText(
            text = annotated,
            style = androidx.compose.ui.text.TextStyle(
                fontSize   = fontSize.sp,
                fontWeight = fontWeight,
                fontFamily = NotoSansBengali,
                lineHeight = (fontSize + 7).sp
            ),
            onClick = { offset ->
                annotated.getStringAnnotations("EN_WORD", offset, offset).firstOrNull()?.let { ann ->
                    com.hanif.smartstudy.util.TtsManager.speakWord(ann.item)
                }
            }
        )
    }
}

@Composable
fun QuestionText(text: String, modifier: Modifier = Modifier, ttsKey: String? = null, ttsOffset: Int = 0) {
    val hasLatex = remember(text) { text.contains("\\") || text.contains("\$") || text.contains("frac") }
    if (hasLatex) {
        // LaTeX/গণিত সূত্র থাকলে MathWebView দিয়ে render হয় — word-highlight ও selection এখানে প্রযোজ্য নয়
        MathWebView(latex = text, modifier = modifier.fillMaxWidth().heightIn(min = 40.dp, max = 300.dp))
    } else if (ttsKey != null) {
        HighlightedSpeakingText(text = text, ttsKey = ttsKey, modifier = modifier, fontSize = 15, spokenOffset = ttsOffset)
    } else {
        SelectableSmartText(
            text       = text,
            fontSize   = 15,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22,
            modifier   = modifier
        )
    }
}

@Composable
fun MathWebView(latex: String, modifier: Modifier = Modifier) {
    val escaped = remember(latex) {
        latex.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
    val html = """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
        <style>
          body { font-family: 'Noto Sans Bengali', sans-serif; font-size: 15px;
                 color: #1e293b; margin: 0; padding: 4px 0; word-break: break-word; }
          .MJX-TEX { font-size: 100% !important; }
        </style></head>
        <body>$escaped</body></html>
    """.trimIndent()

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { it.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) }
    )
}

@Composable
fun McqOptions(item: QuestionItem, onAnswer: (Int) -> Unit) {
    val answered = item.answerState as? AnswerState.McqSelected
    val options  = listOf(1 to item.optionA, 2 to item.optionB, 3 to item.optionC, 4 to item.optionD)
        .filter { it.second.isNotBlank() }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isDark = LocalDarkMode.current.value
    val correctBg = if (isDark) Color(0xFF052E16) else Color(0xFFF0FDF4)
    val wrongBg   = if (isDark) Color(0xFF3D1010) else Color(0xFFFFF1F2)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (n, text) ->
            val isSelected  = answered?.option == n
            val isCorrectOpt = text.trim().equals(item.answer.trim(), ignoreCase = true)
            val bg = when {
                answered == null        -> surfaceColor
                isSelected && answered.isCorrect  -> correctBg
                isSelected && !answered.isCorrect -> wrongBg
                !isSelected && isCorrectOpt && answered != null -> correctBg
                else -> surfaceVariantColor
            }
            val border = when {
                answered == null        -> onSurfaceColor.copy(alpha = 0.2f)
                isSelected && answered.isCorrect  -> GreenOk
                isSelected && !answered.isCorrect -> RedWrong
                !isSelected && isCorrectOpt && answered != null -> GreenOk
                else -> onSurfaceColor.copy(alpha = 0.2f)
            }
            val textColor = when {
                answered == null -> onSurfaceColor
                isSelected && answered.isCorrect  -> Color(0xFF166534)
                isSelected && !answered.isCorrect -> Color(0xFF9F1239)
                !isSelected && isCorrectOpt && answered != null -> Color(0xFF166534)
                else -> onSurfaceVariantColor
            }
            val icon = when {
                answered == null -> listOf("A","B","C","D").getOrNull(n - 1) ?: "?"
                isSelected && answered.isCorrect  -> "✓"
                isSelected && !answered.isCorrect -> "✗"
                !isSelected && isCorrectOpt && answered != null -> "✓"
                else -> listOf("A","B","C","D").getOrNull(n - 1) ?: "?"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.5.dp, border, RoundedCornerShape(12.dp))
                    .then(if (answered == null) Modifier.clickable { onAnswer(n) } else Modifier)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(border.copy(alpha = 0.15f))
                        .border(1.dp, border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = border)
                }
                Spacer(Modifier.width(10.dp))
                Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = textColor, fontFamily = NotoSansBengali, lineHeight = 18.sp,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun WrittenInput(item: QuestionItem, onSubmit: (String) -> Int, onDraftChange: (String) -> Unit = {}) {
    val submitted = item.answerState as? AnswerState.WrittenSubmitted
    val recorded  = item.answerState as? AnswerState.WrittenRecorded
    var text by remember { mutableStateOf("") }
    var matchPct by remember { mutableStateOf(0) }

    if (recorded != null) {
        // Model Test-এর written প্রশ্ন — অটো-চেক নেই, শুধু "সংরক্ষিত হয়েছে" দেখাও
        val isDark = LocalDarkMode.current.value
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF0C2A24) else Color(0xFFF0FDF4)
            ),
            border = BorderStroke(1.dp, Color(0xFF059669))
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("✅ উত্তর সংরক্ষিত হয়েছে", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF059669), fontFamily = NotoSansBengali)
                if (recorded.userText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("তোমার উত্তর: ${recorded.userText}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, lineHeight = 16.sp)
                }
            }
        }
    } else if (submitted != null) {
        val isCorrect = submitted.isCorrect
        val isDark = LocalDarkMode.current.value
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCorrect && isDark  -> Color(0xFF052E16)
                    isCorrect            -> Color(0xFFF0FDF4)
                    !isCorrect && isDark -> Color(0xFF3D1010)
                    else                 -> Color(0xFFFFF1F2)
                }
            ),
            border = BorderStroke(1.dp, if (isCorrect) GreenOk else RedWrong)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "${if (isCorrect) "✅" else "❌"} : ${submitted.matchPct}%",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isCorrect) Color(0xFF166534) else Color(0xFF9F1239),
                    fontFamily = NotoSansBengali
                )
                if (submitted.userText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("তোমার উত্তর: ${submitted.userText}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, lineHeight = 16.sp)
                }
            }
        }
    } else {
        OutlinedTextField(
            value         = text,
            // টাইমার শেষ হয়ে অটো-সাবমিট হয়ে গেলেও এই ড্রাফট টেক্সট হারিয়ে না যাক —
            // প্রতি কি-স্ট্রোকে ViewModel-এ জমা রাখা হয় (submit বাটন চাপার আগেই)
            onValueChange = { text = it; onDraftChange(it) },
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 2,
            maxLines      = 5,
            placeholder   = { Text("এখানে তোমার উত্তর লিখো...", fontFamily = NotoSansBengali,
                color = Color(0xFFCBD5E1)) },
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Indigo600,
                unfocusedBorderColor = Color(0xFFE2E8F0)
            )
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick  = { if (text.isNotBlank()) { matchPct = onSubmit(text) } },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
        ) {
            Text("🔍 উত্তর মিলিয়ে দেখো", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ────────────────────────────────────────────────────────────────
// Model Test — Written প্রশ্ন: টাইপ করে মেলানোর বদলে "উত্তর দেখুন" বাটনে
// আসল উত্তর দেখানো হয়, তারপর ইউজার নিজেই "ঠিক পেরেছি" / "ভুল হয়েছে" ট্যাপ করে —
// এতে MCQ-এর মতোই সরাসরি correct/wrong ধরা পড়ে, ফলে একই টেস্টে MCQ+Written মিশিয়ে
// দিলেও রেজাল্ট সঠিকভাবে হিসাব করা যায়।
// ────────────────────────────────────────────────────────────────
@Composable
fun WrittenRevealSelfGrade(item: QuestionItem, onGrade: (Boolean) -> Unit) {
    val submitted = item.answerState as? AnswerState.WrittenSubmitted
    var isRevealed by remember(item.id) { mutableStateOf(false) }
    val isDark = LocalDarkMode.current.value

    when {
        submitted != null -> {
            // ── আগেই গ্রেড হয়ে গেছে — ফলাফল দেখাও ──
            val isCorrect = submitted.isCorrect
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isCorrect && isDark  -> Color(0xFF052E16)
                        isCorrect            -> Color(0xFFF0FDF4)
                        !isCorrect && isDark -> Color(0xFF3D1010)
                        else                 -> Color(0xFFFFF1F2)
                    }
                ),
                border = BorderStroke(1.dp, if (isCorrect) GreenOk else RedWrong)
            ) {
                Text(
                    if (isCorrect) "✅ ঠিক পেরেছেন" else "❌ ভুল হয়েছে",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (isCorrect) Color(0xFF166534) else Color(0xFF9F1239),
                    fontFamily = NotoSansBengali,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        !isRevealed -> {
            // ── প্রথম ধাপ: শুধু "উত্তর দেখুন" বাটন — অপশন/টাইপিং কিছুই নেই ──
            Button(
                onClick  = { isRevealed = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("উত্তর দেখুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        else -> {
            // ── দ্বিতীয় ধাপ: উত্তর দেখানো হলো, এবার ইউজার নিজেই বিচার করবে ──
            Column {
                if (item.answer.isNotBlank()) {
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                        )
                    ) {
                        Text(item.answer, fontSize = 13.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick  = { onGrade(true) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenOk)
                    ) {
                        Text("✅ ঠিক পেরেছি", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Button(
                        onClick  = { onGrade(false) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = RedWrong)
                    ) {
                        Text("❌ ভুল হয়েছে", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// QBank — Written প্রশ্ন: Study-র রিকল-টাইপিং মোডের মতোই সরাসরি টাইপ-বক্স
// দেখা যায় (আলাদা কীবোর্ড আইকন টগল ছাড়াই), টাইপ করে Enter/বাটনে জমা দিলে
// আসল উত্তর দেখা যায় এবং সাথে সাথে AI দিয়ে অটো-চেক শুরু হয় (Settings-এ সেভ
// করা key থাকলে)। AI ঠিক/ভুল ধরতে পারলে সেই ফলাফল ৫ সেকেন্ড দেখানো হয়,
// তারপর অটো এগিয়ে যায় (পরের প্রশ্নে স্ক্রল — QuestionListScreen-এ)। AI key
// না থাকলে বা চেষ্টা ব্যর্থ হলে সাথে সাথেই ম্যানুয়াল ঠিক/ভুল বাটনে ফলব্যাক
// করে। সবশেষে ফলাফল আগের মতোই QBank-এর রেজাল্ট স্ক্রিনে গণনা হয়ে যায়
// (AnswerState.WrittenSubmitted-ই ব্যবহার হয়, তাই submitQuiz() এ MCQ-র
// মতোই সঠিক/ভুল হিসাব হয়)।
// ────────────────────────────────────────────────────────────────
@Composable
fun WrittenAiRecallCheck(
    item             : QuestionItem,
    onGrade          : (Boolean) -> Unit,
    onAiGradeWritten : (suspend (question: String, correctAnswer: String, userAnswer: String) -> Boolean?)? = null
) {
    val submitted = item.answerState as? AnswerState.WrittenSubmitted
    val isDark = LocalDarkMode.current.value

    var typedAnswer by remember(item.id) { mutableStateOf("") }
    var isRevealed   by remember(item.id) { mutableStateOf(false) }
    var aiChecking   by remember(item.id) { mutableStateOf(false) }
    var aiFailed     by remember(item.id) { mutableStateOf(false) }
    var aiVerdict    by remember(item.id) { mutableStateOf<Boolean?>(null) }
    var graded       by remember(item.id) { mutableStateOf(false) }

    // ── উত্তর প্রকাশ হওয়ার সাথে সাথেই AI দিয়ে অটো-চেক শুরু হয়। key না থাকলে
    // সরাসরি ম্যানুয়াল বাটনে ফলব্যাক করে। AI ঠিক/ভুল বলতে পারলে সেই ফলাফল
    // ৫ সেকেন্ড দেখিয়ে তারপর onGrade() কল করে চূড়ান্ত করা হয় ──
    if (submitted == null && isRevealed) {
        LaunchedEffect(item.id, isRevealed) {
            if (!graded && !aiFailed && aiVerdict == null) {
                if (onAiGradeWritten == null) {
                    aiFailed = true
                } else {
                    aiChecking = true
                    val verdict = runCatching {
                        onAiGradeWritten(item.question, item.answer, typedAnswer)
                    }.getOrNull()
                    aiChecking = false
                    if (verdict != null) {
                        aiVerdict = verdict
                        kotlinx.coroutines.delay(5000)
                        graded = true
                        onGrade(verdict)
                    } else {
                        aiFailed = true
                    }
                }
            }
        }
    }

    when {
        submitted != null -> {
            // ── আগেই গ্রেড হয়ে গেছে — ফলাফল দেখাও ──
            val isCorrect = submitted.isCorrect
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isCorrect && isDark  -> Color(0xFF052E16)
                        isCorrect            -> Color(0xFFF0FDF4)
                        !isCorrect && isDark -> Color(0xFF3D1010)
                        else                 -> Color(0xFFFFF1F2)
                    }
                ),
                border = BorderStroke(1.dp, if (isCorrect) GreenOk else RedWrong)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        if (isCorrect) "✅ ঠিক হয়েছে" else "❌ ভুল হয়েছে",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = if (isCorrect) Color(0xFF166534) else Color(0xFF9F1239),
                        fontFamily = NotoSansBengali
                    )
                    if (submitted.userText.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "তোমার উত্তর: ${submitted.userText}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali, lineHeight = 16.sp
                        )
                    }
                }
            }
        }
        !isRevealed -> {
            // ── সরাসরি টাইপ-বক্স — কীবোর্ড আইকন টগলের দরকার নেই ──
            OutlinedTextField(
                value         = typedAnswer,
                onValueChange = { typedAnswer = it },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 2,
                maxLines      = 5,
                placeholder   = {
                    Text(
                        "এখানে তোমার উত্তর লিখো...", fontFamily = NotoSansBengali,
                        color = Color(0xFFCBD5E1)
                    )
                },
                shape           = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { isRevealed = true }),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Indigo600,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = { isRevealed = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                Text("🔍 উত্তর মিলিয়ে দেখো", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
            }
        }
        else -> {
            Column {
                if (item.answer.isNotBlank()) {
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                item.answer, fontSize = 13.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp
                            )
                            if (typedAnswer.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "তোমার উত্তর: $typedAnswer", fontSize = 12.sp,
                                    fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── AI অটো-চেক চলাকালীন লোডিং ইন্ডিকেটর ──
                if (aiChecking) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Indigo600)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "🤖 AI দিয়ে যাচাই করা হচ্ছে…",
                            fontFamily = NotoSansBengali, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── AI ফলাফল (ঠিক/ভুল) — ৫ সেকেন্ড দেখানো হয়, তারপর onGrade()
                // কল হয়ে অটো পরের প্রশ্নে চলে যায় (QuestionListScreen-এ স্ক্রল) ──
                if (aiVerdict != null && !graded) {
                    val verdictIsCorrect = aiVerdict == true
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = (if (verdictIsCorrect) GreenOk else RedWrong).copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, if (verdictIsCorrect) GreenOk else RedWrong)
                    ) {
                        Text(
                            if (verdictIsCorrect) "✅ ঠিক হয়েছে (AI)" else "❌ ভুল হয়েছে (AI)",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = if (verdictIsCorrect) GreenOk else RedWrong,
                            fontFamily = NotoSansBengali,
                            textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier   = Modifier.padding(12.dp).fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ── AI ব্যর্থ হলে (key নেই / চেষ্টা ব্যর্থ) সাথে সাথেই ম্যানুয়াল
                // ঠিক/ভুল বাটন — ইউজার নিজেই বেছে নেবে ──
                if (aiFailed && !graded) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick  = { graded = true; onGrade(true) },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = GreenOk)
                        ) {
                            Text("✅ ঠিক হয়েছে", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Button(
                            onClick  = { graded = true; onGrade(false) },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = RedWrong)
                        ) {
                            Text("❌ ভুল হয়েছে", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// "Nordic Pastel" ইনফো-বক্স টেমপ্লেট — উত্তর / ব্যাখ্যা / টেকনিক
// তিনটাই একই কাঠামো শেয়ার করে, শুধু রং আর আইকন বদলায়:
//   ● বর্ডার-বিহীন, সফট-ফিল করা পেস্টেল ব্যাকগ্রাউন্ড (border দিয়ে "আলাদা" করার
//     বদলে হালকা রঙের container ব্যবহার করা হয়েছে — এটাই Nordic/Scandinavian
//     ডিজাইনের মূল কৌশল: contrast না বাড়িয়ে soft tonal separation)
//   ● বাম পাশে ছোট circular icon-badge (রঙের ভরাট বৃত্তে সাদা আইকন) —
//     টেক্সট-লেবেলের বদলে আইকন হওয়ায় স্ক্যান করা সহজ হয়
//   ● 16dp corner radius — 12dp এর চেয়ে বেশি বৃত্তাকার, নরম/friendly অনুভূতি দেয়
//   ● heading + body এর মধ্যে alpha-tuned রঙ — heading পূর্ণ-স্যাচুরেশন accent,
//     body টেক্সট থিমের onSurface (soft charcoal, pure black নয়)
//
// ── collapsible support ──
//   collapsible = true হলে পুরো হেডার রো-টা একটা বাটন হয়ে যায়:
//   ট্যাপ করলে expanded টগল হয়। ডিফল্ট অবস্থায় (startExpanded = false)
//   শুধু হেডার (আইকন-ব্যাজ + লেখা + নিচমুখী ▾ chevron) দেখা যায়,
//   ভেতরের content() হাইড থাকে — যতক্ষণ না ইউজার ট্যাপ করছে।
//   এই আচরণ Admin/সাধারণ — সব ইউজারের জন্য সমান।
// ────────────────────────────────────────────────────────────────
@Composable
private fun NordicInfoBox(
    heading       : String,
    icon          : androidx.compose.ui.graphics.vector.ImageVector,
    accent        : Color,
    tint          : Color,
    onEdit        : (() -> Unit)?,
    collapsible   : Boolean = false,
    startExpanded : Boolean = true,
    extraAction   : (@Composable () -> Unit)? = null,
    content       : @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "explanationChevron"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tint
    ) {
        Column(
            Modifier
                .then(
                    if (collapsible)
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { expanded = !expanded }
                    else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        heading, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = accent, fontFamily = NotoSansBengali
                    )
                    if (collapsible) {
                        Spacer(Modifier.width(4.dp))
                        // ── এই ▾ চিহ্নটাই বাটন — নিচের দিকে মুখ করা থাকে যাতে বোঝা
                        //    যায় চাপলে এর নিচে ব্যাখ্যা খুলবে; খোলা থাকলে উপরে ঘুরে যায় ──
                        Icon(
                            Icons.Default.KeyboardArrowDown, null,
                            tint = accent,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = chevronRotation }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEdit != null) AdminBoxEditIcon(accent, onEdit)
                    if (extraAction != null) extraAction()
                }
            }
            AnimatedVisibility(visible = !collapsible || expanded) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun AnswerBox(
    text        : String,
    ttsKey      : String? = null,
    ttsOffset   : Int = 0,
    onEdit      : (() -> Unit)? = null,
    isStudyDone : Boolean = false,
    onStudyDone : (() -> Unit)? = null
) {
    NordicInfoBox(
        heading = "উত্তর",
        icon    = Icons.Default.CheckCircle,
        accent  = NordicSage,
        tint    = NordicSageTint,
        onEdit  = onEdit,
        extraAction = if (onStudyDone != null) ({ StudyDoneCheckIcon(isStudyDone, onStudyDone) }) else null
    ) {
        if (ttsKey != null) {
            HighlightedSpeakingText(
                text      = text,
                ttsKey    = ttsKey,
                fontSize  = 13,
                fontWeight = FontWeight.Medium,
                baseColor = NordicInk,
                spokenOffset = ttsOffset
            )
        } else {
            RichContentText(
                text      = text,
                textColor = NordicInk,
                fontSize  = 13
            )
        }
    }
}

// ── ব্যাখ্যা বক্স — সাধারণ ইউজারের জন্য ডিফল্টভাবে বন্ধ/হাইড থাকে, বাটনে
//    চাপলে খোলে। এডমিনের জন্য সবসময় খোলা থাকবে — বাটনের দরকার নেই। ──
@Composable
fun ExplanationBox(text: String, onEdit: (() -> Unit)? = null, isAdmin: Boolean = false) {
    NordicInfoBox(
        heading       = "ব্যাখ্যা",
        icon          = Icons.Default.MenuBook,
        accent        = NordicBlue,
        tint          = NordicBlueTint,
        onEdit        = onEdit,
        collapsible   = !isAdmin,
        startExpanded = isAdmin
    ) {
        RichContentText(
            text      = text,
            textColor = NordicMuted,
            fontSize  = 12
        )
    }
}

// ── টেকনিক বক্স — সাধারণ ইউজারের জন্য ব্যাখ্যার মতোই কোলাপসিবল, ডিফল্ট বন্ধ।
//    এডমিনের জন্য সবসময় খোলা থাকবে — বাটনের দরকার নেই।
//    খালি/ফাঁকা টেকনিক থাকলে এই ফাংশনই কল হয় না — কল-সাইটে আগে থেকেই
//    `item.technique.isNotBlank()` চেক করা আছে, তাই টেকনিক না থাকলে
//    বাটনও দেখা যাবে না (স্বয়ংক্রিয়ভাবেই)। ──
@Composable
fun TechniqueBox(text: String, onEdit: (() -> Unit)? = null, isAdmin: Boolean = false) {
    if (text.isBlank()) return
    NordicInfoBox(
        heading       = "মনে রাখার টেকনিক",
        icon          = Icons.Default.Lightbulb,
        accent        = NordicClay,
        tint          = NordicClayTint,
        onEdit        = onEdit,
        collapsible   = !isAdmin,
        startExpanded = isAdmin
    ) {
        RichContentText(
            text      = text,
            textColor = NordicClay,
            fontSize  = 12
        )
    }
}

@Composable
fun UserTechniqueSection(
    questionId  : String,
    currentUser : User?
) {
    if (questionId.isBlank() || currentUser == null) return

    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val myPhone        = currentUser.phone ?: ""
    var techniques     by remember(questionId) { mutableStateOf<List<UserTechnique>>(emptyList()) }
    var isLoading      by remember(questionId) { mutableStateOf(false) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<UserTechnique?>(null) }
    var expanded       by remember { mutableStateOf(false) }
    var feedbackMsg    by remember { mutableStateOf<String?>(null) }

    // ── remote (পাবলিক/অনুমোদিত) + লোকাল (নিজের প্রাইভেট, ফোনে সেভ থাকা) টেকনিক একসাথে মার্জ করো ──
    suspend fun loadAll(): List<UserTechnique> {
        val local  = LocalTechniqueStore.getForQuestion(context, questionId, myPhone)
        val res    = FirebaseDataService.fetchTechniquesForQuestion(questionId, myPhone)
        val remote = if (res is ApiResult.Success<*>) {
            @Suppress("UNCHECKED_CAST")
            (res.data as List<UserTechnique>).filter { it.userId != myPhone || it.isPublic }
        } else emptyList() // অফলাইনে/নেটওয়ার্ক এরর হলেও লোকাল প্রাইভেট টেকনিক দেখাতে বাধা নেই
        return (local + remote).sortedByDescending { it.timestamp }
    }

    LaunchedEffect(questionId) {
        isLoading = true
        techniques = loadAll()
        isLoading = false
    }

    fun refresh() {
        scope.launch { techniques = loadAll() }
    }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (techniques.isNotEmpty()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = OrangeTech, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        if (expanded) "লুকাও" else "🧠 ${techniques.size}টি ইউজার কনটেন্ট",
                        fontSize = 11.sp, color = OrangeTech, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            // ── "টেকনিক যোগ করুন" টেক্সট বাটনের জায়গায় ছোট "+" আইকন —
            // ট্যাপ করলে পপআপে ব্যাখ্যা/টেকনিক দুইটাই যোগ করার সুযোগ থাকে ──
            Surface(
                onClick  = { showAddDialog = true },
                shape    = CircleShape,
                color    = Indigo600.copy(alpha = 0.12f),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "ব্যাখ্যা/টেকনিক যোগ করুন",
                        tint = Indigo600, modifier = Modifier.size(16.dp))
                }
            }
        }

        feedbackMsg?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2500)
                feedbackMsg = null
            }
            Surface(
                shape  = RoundedCornerShape(8.dp),
                color  = GreenOk.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(msg, fontSize = 11.sp, color = GreenOk, fontFamily = NotoSansBengali,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
            Spacer(Modifier.height(4.dp))
        }

        if (expanded && techniques.isNotEmpty()) {
            techniques.forEach { t ->
                val isOwn = t.userId == currentUser.phone
                Spacer(Modifier.height(4.dp))
                UserTechniqueCard(
                    technique   = t,
                    isOwn       = isOwn,
                    onEdit      = { editTarget = t; showAddDialog = true },
                    onDelete    = {
                        scope.launch {
                            if (LocalTechniqueStore.isLocalId(t.id)) {
                                LocalTechniqueStore.delete(context, t.id)
                            } else {
                                FirebaseDataService.deleteTechnique(questionId, t.id)
                            }
                            refresh()
                            feedbackMsg = "টেকনিক মুছে ফেলা হয়েছে"
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddTechniqueDialog(
            existing    = editTarget,
            onDismiss   = { showAddDialog = false; editTarget = null },
            onSave      = { text, isPublic, type ->
                scope.launch {
                    val target  = editTarget
                    val typeLbl = if (type == "explanation") "ব্যাখ্যা" else "টেকনিক"

                    if (!isPublic) {
                        // ── প্রাইভেট: সরাসরি ফোনেই সেভ, ইন্টারনেট লাগে না ──
                        if (target == null) {
                            LocalTechniqueStore.add(
                                context    = context,
                                questionId = questionId,
                                userId     = myPhone,
                                userName   = currentUser.displayName(),
                                text       = text,
                                type       = type
                            )
                        } else if (LocalTechniqueStore.isLocalId(target.id)) {
                            LocalTechniqueStore.update(context, target.id, text)
                        } else {
                            // আগে পাবলিক ছিল, এখন প্রাইভেট করা হচ্ছে: রিমোট থেকে সরিয়ে ফোনে নিয়ে আসো
                            FirebaseDataService.deleteTechnique(questionId, target.id)
                            LocalTechniqueStore.add(
                                context    = context,
                                questionId = questionId,
                                userId     = myPhone,
                                userName   = currentUser.displayName(),
                                text       = text,
                                type       = type
                            )
                        }
                        feedbackMsg = "✅ প্রাইভেট $typeLbl আপনার ফোনে সেভ হয়েছে।"
                    } else {
                        // ── পাবলিক: এডমিন অনুমোদনের জন্য সার্ভারে পাঠাতে হয়, তাই ইন্টারনেট লাগবে ──
                        val res = if (target == null || LocalTechniqueStore.isLocalId(target.id)) {
                            if (target != null) LocalTechniqueStore.delete(context, target.id)
                            FirebaseDataService.saveTechnique(
                                questionId = questionId,
                                userId     = myPhone,
                                userName   = currentUser.displayName(),
                                text       = text,
                                isPublic   = true,
                                type       = type
                            )
                        } else {
                            FirebaseDataService.updateTechnique(questionId, target.id, text, true, type)
                        }

                        feedbackMsg = if (res is ApiResult.Success<*>) {
                            "✅ সেভ হয়েছে! এডমিন অনুমোদনের পর সবাই দেখতে পাবে।"
                        } else {
                            // নেটওয়ার্ক না থাকলে হারিয়ে না যায় — সাময়িকভাবে ফোনে প্রাইভেট হিসেবে রেখে দাও
                            LocalTechniqueStore.add(
                                context    = context,
                                questionId = questionId,
                                userId     = myPhone,
                                userName   = currentUser.displayName(),
                                text       = text,
                                type       = type
                            )
                            "⚠️ ইন্টারনেট সংযোগ নেই, তাই আপাতত প্রাইভেট হিসেবে ফোনে সেভ হয়েছে। " +
                                "নেট আসলে আবার এডিট করে 'পাবলিক' করে দিন।"
                        }
                    }

                    refresh()
                    showAddDialog = false
                    editTarget = null
                    expanded = true
                }
            }
        )
    }
}

@Composable
private fun UserTechniqueCard(
    technique : UserTechnique,
    isOwn     : Boolean,
    onEdit    : () -> Unit,
    onDelete  : () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOwn) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
        ),
        border = BorderStroke(
            1.dp,
            if (isOwn) GreenOk.copy(alpha = 0.4f) else AmberWarn.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // টাইপ ব্যাজ — ব্যাখ্যা না টেকনিক
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(
                                if (technique.isExplanation()) Color(0xFF0EA5E9).copy(alpha = 0.14f)
                                else AmberWarn.copy(alpha = 0.14f)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (technique.isExplanation()) "💡 ব্যাখ্যা" else "🧠 টেকনিক",
                            fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali,
                            color = if (technique.isExplanation()) Color(0xFF0369A1) else Color(0xFF92400E)
                        )
                    }
                    Text(
                        if (isOwn) "🙋 আমার" else "👤 ${technique.userName}",
                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (isOwn) GreenOk else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali
                    )
                    if (isOwn) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (technique.isPublic) Indigo600.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (technique.isPublic) "🌐 পাবলিক" else "🔒 প্রাইভেট",
                                fontSize = 8.sp, color = if (technique.isPublic) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold
                            )
                        }
                        if (technique.isPublic && technique.isPending()) {
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(AmberWarn.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("⏳ অনুমোদন পেন্ডিং", fontSize = 8.sp, color = Color(0xFF92400E),
                                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (isOwn) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, null, tint = Indigo600, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null, tint = RedWrong, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            RichContentText(
                text      = technique.text,
                textColor = MaterialTheme.colorScheme.onSurface,
                fontSize  = 12
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("টেকনিক মুছবেন?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
            text  = { Text("এই টেকনিকটি স্থায়ীভাবে মুছে যাবে।", fontFamily = NotoSansBengali) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("হ্যাঁ, মুছুন", color = RedWrong, fontFamily = NotoSansBengali)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
        )
    }
}

@Composable
private fun AddTechniqueDialog(
    existing  : UserTechnique?,
    onDismiss : () -> Unit,
    onSave    : (text: String, isPublic: Boolean, type: String) -> Unit
) {
    var text     by remember(existing) { mutableStateOf(existing?.text ?: "") }
    var isPublic by remember(existing) { mutableStateOf(existing?.isPublic ?: false) }
    // ── ব্যাখ্যা না টেকনিক — কোনটা যোগ/এডিট করা হচ্ছে ──
    var type     by remember(existing) { mutableStateOf(existing?.type ?: "technique") }
    val isEditingExisting = existing != null

    // ── ছবি আপলোড: গ্যালারি থেকে ছবি নিয়ে imgbb-তে আপলোড, তারপর লিংকটা টেক্সটের
    // মধ্যেই বসিয়ে দেওয়া হয় — ছবি দেখানোর সিস্টেম (RichContentText) আগে থেকেই
    // টেক্সটের মধ্যে থাকা imgbb লিংক থেকে ছবি অটো-রেন্ডার করে, তাই আলাদা ফিল্ড লাগে না ──
    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()
    var isUploadingImage  by remember { mutableStateOf(false) }
    var imageUploadError  by remember { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isUploadingImage = true
            imageUploadError = null
            when (val result = ImgBbService.uploadImage(context, uri)) {
                is ImgBbResult.Success -> {
                    text = if (text.isBlank()) result.url else text.trimEnd() + "\n" + result.url
                }
                is ImgBbResult.Error -> {
                    imageUploadError = result.message
                }
            }
            isUploadingImage = false
        }
    }

    // Dark mode aware colors
    val cardBg    = MaterialTheme.colorScheme.surface
    val onCardBg  = MaterialTheme.colorScheme.onSurface
    val subBg     = MaterialTheme.colorScheme.surfaceVariant
    val subBorder = MaterialTheme.colorScheme.outline

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (existing == null) "✍️ নতুন কনটেন্ট যোগ করুন" else "✏️ সম্পাদনা",
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = onCardBg, fontFamily = NotoSansBengali
                )
                Spacer(Modifier.height(12.dp))

                // ── ব্যাখ্যা | টেকনিক — দুই পাশে টাইপ বাছাইয়ের ট্যাব ──
                // নতুন যোগ করার সময় বাছাই করা যায়; এডিটের সময় টাইপ পরিবর্তন করা হয় না
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(subBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf("explanation" to "💡 ব্যাখ্যা", "technique" to "🧠 টেকনিক").forEach { (tId, label) ->
                        val selected = type == tId
                        Surface(
                            onClick  = { if (!isEditingExisting) type = tId },
                            shape    = RoundedCornerShape(8.dp),
                            color    = if (selected) Indigo600 else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                label,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = NotoSansBengali,
                                color      = if (selected) Color.White else onCardBg,
                                textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier   = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = {
                        Text(
                            if (type == "explanation") "এখানে আপনার ব্যাখ্যা লিখুন..." else "এখানে টেকনিক লিখুন...",
                            fontFamily = NotoSansBengali,
                            fontSize   = 13.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier   = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    minLines   = 3,
                    maxLines   = 6,
                    shape      = RoundedCornerShape(10.dp),
                    textStyle  = LocalTextStyle.current.copy(
                        fontFamily = NotoSansBengali,
                        fontSize   = 13.sp,
                        color      = onCardBg   // ← এটাই আগে ছিল না, তাই dark mode এ দেখা যেত না
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Indigo600,
                        unfocusedBorderColor = subBorder,
                        focusedTextColor     = onCardBg,
                        unfocusedTextColor   = onCardBg,
                        cursorColor          = Indigo600
                    )
                )

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape    = RoundedCornerShape(10.dp),
                    color    = subBg,
                    border   = BorderStroke(1.dp, subBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── ছবি যুক্ত করার বাটন — গ্যালারি খুলে, imgbb-তে আপলোড হয়ে
                        // লিংক টেক্সটে বসে যায়। আপলোড চলাকালীন ছোট লোডার দেখায় ──
                        Surface(
                            onClick  = { if (!isUploadingImage) imageLauncher.launch("image/*") },
                            shape    = RoundedCornerShape(9.dp),
                            color    = Indigo600.copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUploadingImage) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color       = Indigo600
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = "ছবি যুক্ত করুন",
                                        tint = Indigo600,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isPublic) "🌐 পাবলিক" else "🔒 প্রাইভেট",
                                fontSize   = 12.sp, fontWeight = FontWeight.Bold,
                                color      = onCardBg, fontFamily = NotoSansBengali
                            )
                            Text(
                                if (isPublic) "এডমিন অনুমোদনের পর সবাই দেখতে পাবে"
                                else "শুধু আপনি দেখতে পাবেন",
                                fontSize = 10.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali
                            )
                        }
                        Switch(
                            checked         = isPublic,
                            onCheckedChange = { isPublic = it },
                            colors          = SwitchDefaults.colors(checkedThumbColor = Indigo600)
                        )
                    }
                }

                imageUploadError?.let { err ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "⚠️ $err",
                        fontSize = 10.sp,
                        color = RedWrong,
                        fontFamily = NotoSansBengali
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Text("বাতিল", fontFamily = NotoSansBengali)
                    }
                    Button(
                        onClick  = { if (text.trim().isNotBlank()) onSave(text.trim(), isPublic, type) },
                        enabled  = text.trim().isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
                    ) {
                        Text("সেভ করুন", fontFamily = NotoSansBengali)
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomableImage(url: String) {
    var zoomed by remember { mutableStateOf(false) }

    AsyncImage(
        model              = url,
        contentDescription = null,
        modifier           = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { zoomed = true }
            .padding(vertical = 6.dp),
        contentScale       = ContentScale.Fit
    )

    if (zoomed) {
        com.hanif.smartstudy.ui.shared.ImageZoomOverlay(
            imageUrl  = url,
            onDismiss = { zoomed = false }
        )
    }
}

@Composable
fun ReportDialog(
    questionId   : String = "",
    questionText : String = "",
    onReport     : (String) -> Unit,
    onDismiss    : () -> Unit
) {
    val issues = listOf("ভুল উত্তর", "ভুল বানান", "ব্যাখ্যা নেই", "ছবি দেখা যাচ্ছে না", "অন্য সমস্যা")
    var selected by remember { mutableStateOf("") }
    var extraNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFFF1F2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🚩", fontSize = 18.sp)
                }
                Text("সমস্যা রিপোর্ট করুন", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (questionText.isNotBlank()) {
                    Text(
                        questionText.take(80) + if (questionText.length > 80) "..." else "",
                        fontFamily = NotoSansBengali, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    )
                }
                Text("সমস্যার ধরন:", fontFamily = NotoSansBengali,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                issues.forEach { issue ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected == issue) Indigo600.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                if (selected == issue) Indigo600 else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selected = issue }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == issue,
                            onClick  = { selected = issue },
                            colors   = RadioButtonDefaults.colors(selectedColor = Indigo600)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(issue, fontFamily = NotoSansBengali, fontSize = 13.sp,
                            color = if (selected == issue) Indigo600 else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected == issue) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                OutlinedTextField(
                    value         = extraNote,
                    onValueChange = { extraNote = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("বাড়তি তথ্য (ঐচ্ছিক)...", fontFamily = NotoSansBengali,
                        fontSize = 12.sp, color = Color(0xFFCBD5E1)) },
                    maxLines      = 3,
                    shape         = RoundedCornerShape(10.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Indigo600,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    val msg = selected + if (extraNote.isNotBlank()) ": $extraNote" else ""
                    if (msg.isNotBlank()) onReport(msg)
                },
                enabled  = selected.isNotBlank(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("🚩 রিপোর্ট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// ═══════════════════════════════════════════════════════════════
//  AdminFieldEditDialog — পুরনো index.html স্টাইলের ছোট per-field
//  এডিট পপআপ। প্রতিটা ফিল্ড (প্রশ্ন/অপশন/উত্তর/ব্যাখ্যা/টেকনিক) এর
//  পাশে আলাদা ✎ আইকনে ক্লিক করলে এই ছোট মোডালটা খোলে — শুধু ওই
//  ফিল্ডের এক্সিস্টিং টেক্সট সহ, Close/Update বাটন সহ।
// ═══════════════════════════════════════════════════════════════

private val ADMIN_FIELD_LABELS = mapOf(
    "question"    to "প্রশ্ন",
    "optA"        to "অপশন ক",
    "optB"        to "অপশন খ",
    "optC"        to "অপশন গ",
    "optD"        to "অপশন ঘ",
    "answer"      to "সঠিক উত্তর",
    "explanation" to "ব্যাখ্যা",
    "technique"   to "টেকনিক"
)

@Composable
fun AdminFieldEditDialog(
    item        : QuestionItem,
    fieldId     : String,
    initialValue: String,
    onDismiss   : () -> Unit,
    onAdminEdit   : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null,
    onAdminDelete : ((sheet: String, rowKey: String, preview: String) -> Unit)? = null
) {
    val sheet = when {
        item.year.isNotBlank() || item.examName.isNotBlank() -> "QBank"
        item.isStudy() -> "Study"
        item.isMcq()   -> "Quiz"
        else           -> "Study"
    }
    val fieldLabel = ADMIN_FIELD_LABELS[fieldId] ?: fieldId
    val adminIndigo = Color(0xFF4F46E5)

    var text        by remember { mutableStateOf(initialValue) }
    var isSaving    by remember { mutableStateOf(false) }
    // ── পুরো প্রশ্ন কার্ড ডিলিট — শুধু এই ফিল্ড না, পুরো row (প্রশ্ন+অপশন+
    // উত্তর+ব্যাখ্যা+টেকনিক সব) ডিলিট হয়ে যায়, তাই confirm করার জন্য
    // আলাদা রেড ওয়ার্নিং ডায়ালগ দেখানো হয় ──
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting        by remember { mutableStateOf(false) }
    val scope       = rememberCoroutineScope()

    val doSave: () -> Unit = {
        scope.launch {
            isSaving = true
            val value = text.trim()
            val fields = mutableMapOf<String, String>()
            when (fieldId) {
                "question"    -> fields["question"] = value
                "optA"        -> fields["option1"] = value
                "optB"        -> fields["option2"] = value
                "optC"        -> fields["option3"] = value
                "optD"        -> fields["option4"] = value
                "answer"      -> { fields["correct"] = value; fields["answer"] = value }
                "explanation" -> fields["explanation"] = value
                "technique"   -> fields["technique"] = value
            }
            try {
                if (onAdminEdit != null) {
                    onAdminEdit(sheet, item.id, fields, item.question.take(60))
                } else {
                    FirebaseDataService.adminUpdateQuestionField(sheet, item.id, fields)
                }
            } catch (_: Exception) { }
            isSaving = false
            onDismiss()
        }
    }

    val doDelete: () -> Unit = {
        scope.launch {
            isDeleting = true
            try {
                if (onAdminDelete != null) {
                    onAdminDelete(sheet, item.id, item.question.take(60))
                } else {
                    FirebaseDataService.adminDeleteQuestion(sheet, item.id)
                }
            } catch (_: Exception) { }
            isDeleting = false
            showDeleteConfirm = false
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape    = RoundedCornerShape(20.dp),
            color    = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "তথ্য ইডিট করুন",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = adminIndigo,
                        fontFamily = NotoSansBengali
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── পুরো প্রশ্ন কার্ড ডিলিট বাটন — শুধু "প্রশ্ন" (question) ফিল্ড
                        // ইডিটে দেখাবে। উত্তর/ব্যাখ্যা/টেকনিক/অপশন ইডিটে এই বাটন থাকবে না,
                        // যাতে ভুলবশত অন্য ফিল্ড ইডিট করার সময় পুরো প্রশ্ন কার্ড ডিলিট না হয়ে যায় ──
                        if (fieldId == "question") {
                            Surface(
                                onClick  = { if (!isSaving && !isDeleting) showDeleteConfirm = true },
                                shape    = RoundedCornerShape(20.dp),
                                color    = RedWrong.copy(alpha = 0.12f)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "পুরো প্রশ্ন কার্ড ডিলিট করুন",
                                    tint = RedWrong,
                                    modifier = Modifier.padding(6.dp).size(18.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                fieldLabel,
                                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "ID: ${item.id}",
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = NotoSansBengali
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    modifier      = Modifier.fillMaxWidth().height(280.dp),
                    placeholder   = { Text("এখানে লিখুন...", fontFamily = NotoSansBengali, color = Color(0xFFCBD5E1)) },
                    textStyle     = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        fontFamily = NotoSansBengali, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape         = RoundedCornerShape(16.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = adminIndigo,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick  = { if (!isSaving) onDismiss() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Close", fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick  = { if (!isSaving) doSave() },
                        enabled  = !isSaving,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = adminIndigo)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Update", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ── রেড ওয়ার্নিং: পুরো প্রশ্ন কার্ড ডিলিট কনফার্মেশন ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            icon = { Text("⚠️", fontSize = 28.sp) },
            title = {
                Text(
                    "পুরো প্রশ্ন কার্ড ডিলিট করবেন?",
                    fontWeight = FontWeight.ExtraBold,
                    color = RedWrong,
                    fontFamily = NotoSansBengali
                )
            },
            text = {
                Text(
                    "শুধু এই ফিল্ড না — প্রশ্ন, চারটা অপশন, সঠিক উত্তর, ব্যাখ্যা, টেকনিক " +
                        "সহ পুরো প্রশ্ন কার্ডটাই স্থায়ীভাবে মুছে যাবে। এই কাজটি ফিরিয়ে আনা যাবে না।",
                    fontFamily = NotoSansBengali,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick  = doDelete,
                    enabled  = !isDeleting,
                    colors   = ButtonDefaults.buttonColors(containerColor = RedWrong)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(Modifier.size(16.dp), Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("হ্যাঁ, ডিলিট করুন", fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isDeleting) showDeleteConfirm = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
        )
    }
}

// ── Rich text parser (used by preview) ───────────────────────────────────────
internal fun parseRichAnnotated(raw: String, baseSizeSp: Float = 13f): AnnotatedString {
    val cleaned = raw
        .replace("<mark><b>", "<hb>").replace("</b></mark>", "</hb>")
        .replace("<mark>",    "<hb>").replace("</mark>",     "</hb>")

    return buildAnnotatedString {
        var i = 0
        while (i < cleaned.length) {
            when {
                cleaned.startsWith("**", i) -> {
                    val end = cleaned.indexOf("**", i + 2)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(cleaned.substring(i+2,end)) }; i=end+2 }
                }
                cleaned.startsWith("__", i) -> {
                    val end = cleaned.indexOf("__", i + 2)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(cleaned.substring(i+2,end)) }; i=end+2 }
                }
                cleaned.startsWith("++", i) -> {
                    val end = cleaned.indexOf("++", i + 2)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { withStyle(SpanStyle(fontSize = (baseSizeSp+3).sp)) { append(cleaned.substring(i+2,end)) }; i=end+2 }
                }
                cleaned[i] == '*' && !cleaned.startsWith("**", i) -> {
                    val end = cleaned.indexOf('*', i+1).let { if (it != -1 && !cleaned.startsWith("**", it)) it else -1 }
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(cleaned.substring(i+1,end)) }; i=end+1 }
                }
                cleaned.startsWith("<hb>", i) -> {
                    val end = cleaned.indexOf("</hb>", i+4)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, background = Color(0x33F59E0B))) {
                            append(cleaned.substring(i+4, end))
                        }
                        i = end + 5
                    }
                }
                cleaned.startsWith("<b>", i) -> {
                    val end = cleaned.indexOf("</b>", i+3)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(cleaned.substring(i+3,end)) }; i=end+4 }
                }
                cleaned.startsWith("<i>", i) -> {
                    val end = cleaned.indexOf("</i>", i+3)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(cleaned.substring(i+3,end)) }; i=end+4 }
                }
                cleaned.startsWith("<u>", i) -> {
                    val end = cleaned.indexOf("</u>", i+3)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(cleaned.substring(i+3,end)) }; i=end+4 }
                }
                cleaned.startsWith("<li>", i) -> {
                    val end = cleaned.indexOf("</li>", i+4)
                    if (end == -1) { append(cleaned[i]); i++ }
                    else { append("• "); append(cleaned.substring(i+4, end)); append("\n"); i = end + 5 }
                }
                cleaned.startsWith("</", i) -> {
                    val tagEnd = cleaned.indexOf('>', i)
                    i = if (tagEnd == -1) i+1 else tagEnd+1
                }
                else -> { append(cleaned[i]); i++ }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ADMIN DRAG-TO-REORDER — On-the-spot position change
//  Long-press করে ধরো → drag করো → ছেড়ে দাও → Firebase এ save
// ═══════════════════════════════════════════════════════════════

/**
 * AdminDragHandle — Admin mode এ প্রতিটা item এর পাশে দেখাবে।
 * এটা দেখলে বুঝবে এটা drag করা যাবে।
 */
@Composable
fun AdminDragHandle(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.DragHandle,
        contentDescription = "Drag to reorder",
        tint = Color(0xFF94A3B8),
        modifier = modifier.size(20.dp)
    )
}

/**
 * AdminDraggableItem — যেকোনো admin-draggable item এর wrapper।
 * Long-press করলে drag শুরু হয়, ছেড়ে দিলে onDrop(fromIdx, toIdx) call হয়।
 *
 * isDragging = এই item টা এখন drag হচ্ছে কিনা
 * dragOffsetY = drag করার সময় Y offset (px)
 */
@Composable
fun AdminDraggableItem(
    index      : Int,
    isDragging : Boolean,
    dragOffsetY: Float,
    modifier   : Modifier = Modifier,
    content    : @Composable () -> Unit
) {
    val elevation by animateFloatAsState(if (isDragging) 16f else 2f, label = "dragElev")
    val scale     by animateFloatAsState(if (isDragging) 1.03f else 1f, label = "dragScale")
    val alpha     by animateFloatAsState(if (isDragging) 0.92f else 1f, label = "dragAlpha")

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX       = scale
                scaleY       = scale
                this.alpha   = alpha
                shadowElevation = elevation
            }
            .zIndex(if (isDragging) 10f else 0f)
    ) {
        content()
    }
}

/**
 * AdminReorderableQuestionList — Admin mode এ question drag-reorder।
 * Long-press on handle → drag → release → Firebase save।
 *
 * FIX: real-time swap + scroll conflict নেই।
 */
@Composable
fun AdminReorderableQuestionList(
    questions  : List<QuestionItem>,
    isAdmin    : Boolean,
    onReorder  : (from: Int, to: Int) -> Unit,
    modifier   : Modifier = Modifier,
    itemContent: @Composable (index: Int, item: QuestionItem, dragModifier: Modifier) -> Unit
) {
    if (!isAdmin) {
        questions.forEachIndexed { idx, q -> itemContent(idx, q, Modifier) }
        return
    }

    val localItems = remember(questions) { questions.toMutableStateList() }
    var draggingIdx  by remember { mutableStateOf(-1) }
    var dragOffsetY  by remember { mutableStateOf(0f) }
    val itemHeights  = remember { mutableStateMapOf<Int, Float>() }

    Column(modifier = modifier) {
        localItems.forEachIndexed { idx, q ->
            val isDragging = draggingIdx == idx
            val steps = if (isDragging && itemHeights.isNotEmpty()) {
                val avgH = itemHeights.values.average().toFloat().takeIf { it > 0f } ?: 1f
                (dragOffsetY / avgH).toInt()
            } else 0
            val targetIdx = if (draggingIdx >= 0)
                (draggingIdx + steps).coerceIn(0, localItems.lastIndex) else -1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isDragging) {
                            translationY    = dragOffsetY
                            scaleX          = 1.02f
                            scaleY          = 1.02f
                            alpha           = 0.94f
                            shadowElevation = 20f
                        }
                    }
                    .zIndex(if (isDragging) 10f else 0f),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .padding(top = 16.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { _ ->
                                    draggingIdx = idx
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val avgH = itemHeights.values.average()
                                        .toFloat().takeIf { it > 0f } ?: return@detectDragGesturesAfterLongPress
                                    val s    = (dragOffsetY / avgH).toInt()
                                    val newTo = (draggingIdx + s).coerceIn(0, localItems.lastIndex)
                                    if (newTo != draggingIdx) {
                                        val moved = localItems.removeAt(draggingIdx)
                                        localItems.add(newTo, moved)
                                        dragOffsetY -= s * avgH
                                        draggingIdx = newTo
                                    }
                                },
                                onDragEnd = {
                                    val finalIdx = draggingIdx
                                    val originalFrom = questions.indexOfFirst { it.id == localItems.getOrNull(finalIdx)?.id }
                                    if (originalFrom >= 0 && originalFrom != finalIdx)
                                        onReorder(originalFrom, finalIdx)
                                    draggingIdx = -1; dragOffsetY = 0f
                                },
                                onDragCancel = { draggingIdx = -1; dragOffsetY = 0f }
                            )
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(3) {
                            Box(
                                Modifier.width(16.dp).height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isDragging) Color(0xFF4F46E5) else Color(0xFFB0BEC5))
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { c -> itemHeights[idx] = c.size.height.toFloat() }
                ) {
                    itemContent(idx, q, Modifier)
                }
            }

            if (draggingIdx >= 0 && draggingIdx != idx && targetIdx == idx) {
                Box(
                    Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color(0xFF4F46E5))
                )
            }
        }
    }
}


/**
 * AdminReorderableList<T> — Generic drag-reorder।
 * Subject / SubTopic list এ use হয়।
 *
 * FIX: আগের version এ LazyColumn scroll এর সাথে drag conflict করত।
 * এখন item এর position track করে itemOffsets দিয়ে — scroll এর বাইরে।
 * Handle টা পুরো Row এর বাম পাশে, long-press এ drag শুরু।
 */
@Composable
fun <T> AdminReorderableList(
    items      : List<T>,
    isAdmin    : Boolean,
    keyOf      : (T) -> String,
    onReorder  : (from: Int, to: Int) -> Unit,
    modifier   : Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    if (!isAdmin) {
        Column(modifier) {
            items.forEachIndexed { idx, item -> itemContent(idx, item) }
        }
        return
    }

    // mutable list — drag হলে immediately reorder করি (visual feedback)
    val localItems = remember(items) { items.toMutableStateList() }

    // drag state
    var draggingIdx  by remember { mutableStateOf(-1) }
    var dragOffsetY  by remember { mutableStateOf(0f) }
    // প্রতিটা item এর height track করি
    val itemHeights  = remember { mutableStateMapOf<Int, Float>() }

    Column(modifier = modifier) {
        localItems.forEachIndexed { idx, item ->
            val isDragging = draggingIdx == idx

            // dragging item কত steps move হয়েছে
            val steps = if (isDragging && itemHeights.isNotEmpty()) {
                val avgH = itemHeights.values.average().toFloat().takeIf { it > 0f } ?: 1f
                (dragOffsetY / avgH).toInt()
            } else 0
            val targetIdx = if (draggingIdx >= 0)
                (draggingIdx + steps).coerceIn(0, localItems.lastIndex) else -1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isDragging) {
                            translationY    = dragOffsetY
                            scaleX          = 1.02f
                            scaleY          = 1.02f
                            alpha           = 0.94f
                            shadowElevation = 20f
                        }
                    }
                    .zIndex(if (isDragging) 10f else 0f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Drag Handle ──
                // pointerInput এখানে — handle touch করলেই drag, scroll এ যাবে না
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { _ ->
                                    draggingIdx = idx
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    // Real-time swap: threshold পার হলে swap করো
                                    val avgH = itemHeights.values.average()
                                        .toFloat().takeIf { it > 0f } ?: return@detectDragGesturesAfterLongPress
                                    val s    = (dragOffsetY / avgH).toInt()
                                    val newTo = (draggingIdx + s).coerceIn(0, localItems.lastIndex)
                                    if (newTo != draggingIdx) {
                                        val moved = localItems.removeAt(draggingIdx)
                                        localItems.add(newTo, moved)
                                        // offset reset — নতুন position থেকে শুরু
                                        dragOffsetY -= s * avgH
                                        draggingIdx = newTo
                                    }
                                },
                                onDragEnd = {
                                    val finalIdx = draggingIdx
                                    // original list এর index খুঁজে বের করো
                                    val originalFrom = items.indexOfFirst { keyOf(it) == keyOf(localItems[finalIdx]) }
                                    if (originalFrom >= 0 && originalFrom != finalIdx) {
                                        onReorder(originalFrom, finalIdx)
                                    } else if (finalIdx >= 0) {
                                        // local reorder ই হয়েছে — ViewModel কে জানাও
                                        onReorder(finalIdx, finalIdx)
                                    }
                                    draggingIdx = -1
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIdx = -1
                                    dragOffsetY = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // ≡ হ্যান্ডেল আইকন
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isDragging) Color(0xFF4F46E5)
                                        else Color(0xFFB0BEC5)
                                    )
                            )
                        }
                    }
                }

                // ── Item content ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            itemHeights[idx] = coords.size.height.toFloat()
                        }
                ) {
                    itemContent(idx, item)
                }
            }

            // Drop indicator — target position এ নীল রেখা
            if (draggingIdx >= 0 && draggingIdx != idx && targetIdx == idx) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF4F46E5))
                )
            }
        }
    }
}


/**
 * AdminOptionReorderRow — MCQ options drag-reorder।
 * Admin শুধু long-press করে A/B/C/D option drag করে position বদলাতে পারবে।
 * Firebase এ correct answer automatically update হবে।
 *
 * onReorder(fromOptIdx, toOptIdx): 0=A, 1=B, 2=C, 3=D
 */
@Composable
fun AdminOptionReorderRow(
    optionText  : String,
    optionLabel : String,        // "A", "B", "C", "D"
    isCorrect   : Boolean,
    isAdmin     : Boolean,
    optionIdx   : Int,
    onReorder   : (from: Int, to: Int) -> Unit,
    modifier    : Modifier = Modifier,
    content     : @Composable () -> Unit
) {
    if (!isAdmin) {
        content()
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini drag handle (option এর বাম পাশে)
        Box(
            Modifier
                .width(24.dp)
                .pointerInput(optionIdx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { /* handled in parent */ },
                        onDrag      = { _, _ -> },
                        onDragEnd   = { },
                        onDragCancel = { }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(2) {
                    Box(Modifier.width(10.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFFCBD5E1)))
                }
            }
        }

        Box(Modifier.weight(1f)) { content() }
    }
}
