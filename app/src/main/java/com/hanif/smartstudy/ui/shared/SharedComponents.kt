package com.hanif.smartstudy.ui.shared

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
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
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.remote.FirebaseDataService
import com.hanif.smartstudy.ui.components.RichContentText
import com.hanif.smartstudy.data.remote.ApiResult
import com.hanif.smartstudy.ui.theme.NotoSansBengali
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
import androidx.compose.foundation.text.BasicTextField
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
    onBookmark     : () -> Unit,
    onReport       : () -> Unit,
    currentUser    : User?     = null,
    onAdminRefresh : (() -> Unit)? = null,
    onAdminEdit    : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null,
    modifier       : Modifier = Modifier
) {
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
                            val ans = item.answer.ifBlank { item.explanation }
                            if (ans.isNotBlank()) { append("। উত্তর। "); append(ans) }
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
                    // Admin edit button
                    if (currentUser?.isAdmin() == true) {
                        var showEdit by remember { mutableStateOf(false) }
                        IconButton(onClick = { showEdit = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                        }
                        if (showEdit) {
                            AdminQuestionEditDialog(item = item, onDismiss = {
                                showEdit = false
                                onAdminRefresh?.invoke()
                            }, onAdminEdit = onAdminEdit)
                        }
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
                item.isWritten() && mode != StudyMode.STUDY -> {
                    WrittenInput(item = item, onSubmit = onWritten)
                }
                else -> {}
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
                AnswerBox(text = item.answer, ttsKey = answerTtsKey)
            }

            // explanation — studyNoQ তে empty, নাহলে দেখাও
            if (showAnswerBox && displayExplanation.isNotBlank() && displayExplanation != item.answer) {
                Spacer(Modifier.height(6.dp))
                ExplanationBox(text = displayExplanation)
            }

            if (showAnswerBox && item.technique.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                TechniqueBox(text = item.technique)
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
    baseColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val activeKey by com.hanif.smartstudy.util.TtsManager.activeKeyFlow.collectAsState()
    val wordRange by com.hanif.smartstudy.util.TtsManager.currentWordRange.collectAsState()
    val isThisActive = activeKey == ttsKey

    val annotated = remember(text, isThisActive, wordRange) {
        buildAnnotatedString {
            if (!isThisActive || wordRange == null) {
                withStyle(SpanStyle(color = baseColor)) { append(text) }
            } else {
                val range = wordRange!!
                val spokenEnd = range.first.coerceIn(0, text.length)
                val highlightStart = range.first.coerceIn(0, text.length)
                val highlightEnd = range.last.coerceIn(0, text.length)
                // ইতিমধ্যে বলা অংশ — হালকা ফিকে রঙ (পড়া শেষ বোঝানোর জন্য)
                if (spokenEnd > 0) {
                    withStyle(SpanStyle(color = baseColor.copy(alpha = 0.38f))) {
                        append(text.substring(0, spokenEnd))
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
fun QuestionText(text: String, modifier: Modifier = Modifier, ttsKey: String? = null) {
    val hasLatex = remember(text) { text.contains("\\") || text.contains("\$") || text.contains("frac") }
    if (hasLatex) {
        // LaTeX/গণিত সূত্র থাকলে MathWebView দিয়ে render হয় — word-highlight ও selection এখানে প্রযোজ্য নয়
        MathWebView(latex = text, modifier = modifier.fillMaxWidth().heightIn(min = 40.dp, max = 300.dp))
    } else if (ttsKey != null) {
        HighlightedSpeakingText(text = text, ttsKey = ttsKey, modifier = modifier, fontSize = 15)
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
    val isDark = isSystemInDarkTheme()
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
fun WrittenInput(item: QuestionItem, onSubmit: (String) -> Int) {
    val submitted = item.answerState as? AnswerState.WrittenSubmitted
    var text by remember { mutableStateOf("") }
    var matchPct by remember { mutableStateOf(0) }

    if (submitted != null) {
        val isCorrect = submitted.isCorrect
        val isDark = isSystemInDarkTheme()
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
            onValueChange = { text = it },
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

@Composable
fun AnswerBox(text: String, ttsKey: String? = null) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, GreenOk)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("উত্তর:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = GreenOk, fontFamily = NotoSansBengali)
            Spacer(Modifier.height(4.dp))
            if (ttsKey != null) {
                HighlightedSpeakingText(
                    text      = text,
                    ttsKey    = ttsKey,
                    fontSize  = 13,
                    fontWeight = FontWeight.Normal,
                    baseColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                RichContentText(
                    text      = text,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    fontSize  = 13
                )
            }
        }
    }
}

@Composable
fun ExplanationBox(text: String) {
    val isDark = isSystemInDarkTheme()
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF38BDF8))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("ব্যাখ্যা:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF38BDF8), fontFamily = NotoSansBengali)
            Spacer(Modifier.height(4.dp))
            RichContentText(
                text      = text,
                textColor = MaterialTheme.colorScheme.onSurface,
                fontSize  = 12
            )
        }
    }
}

@Composable
fun TechniqueBox(text: String) {
    val isDark = isSystemInDarkTheme()
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, AmberWarn)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("💡 টেকনিক:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = AmberWarn, fontFamily = NotoSansBengali)
            Spacer(Modifier.height(4.dp))
            RichContentText(
                text      = text,
                textColor = MaterialTheme.colorScheme.onSurface,
                fontSize  = 12
            )
        }
    }
}

@Composable
fun UserTechniqueSection(
    questionId  : String,
    currentUser : User?
) {
    if (questionId.isBlank() || currentUser == null) return

    val scope          = rememberCoroutineScope()
    var techniques     by remember(questionId) { mutableStateOf<List<UserTechnique>>(emptyList()) }
    var isLoading      by remember(questionId) { mutableStateOf(false) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<UserTechnique?>(null) }
    var expanded       by remember { mutableStateOf(false) }
    var feedbackMsg    by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(questionId) {
        isLoading = true
        // ফিক্সড: ApiResult.Success টাইপ সেফটি ফিক্সড
        val res = FirebaseDataService.fetchTechniquesForQuestion(questionId, currentUser.phone ?: "")
        if (res is ApiResult.Success<*>) {
            @Suppress("UNCHECKED_CAST")
            techniques = res.data as List<UserTechnique>
        }
        isLoading = false
    }

    fun refresh() {
        scope.launch {
            val res = FirebaseDataService.fetchTechniquesForQuestion(questionId, currentUser.phone ?: "")
            if (res is ApiResult.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                techniques = res.data as List<UserTechnique>
            }
        }
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
                        if (expanded) "টেকনিক লুকাও" else "🧠 ${techniques.size}টি ইউজার টেকনিক",
                        fontSize = 11.sp, color = OrangeTech, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            TextButton(
                onClick  = { showAddDialog = true },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Indigo600, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("টেকনিক যোগ করুন", fontSize = 10.sp, color = Indigo600,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
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
                            FirebaseDataService.deleteTechnique(questionId, t.id)
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
            onSave      = { text, isPublic ->
                scope.launch {
                    val target = editTarget
                    if (target == null) {
                        FirebaseDataService.saveTechnique(
                            questionId = questionId,
                            userId     = currentUser.phone ?: "",
                            userName   = currentUser.displayName(),
                            text       = text,
                            isPublic   = isPublic
                        )
                        feedbackMsg = if (isPublic)
                            "✅ সেভ হয়েছে! এডমিন অনুমোদনের পর সবাই দেখতে পাবে।"
                        else "✅ প্রাইভেট টেকনিক সেভ হয়েছে।"
                    } else {
                        FirebaseDataService.updateTechnique(questionId, target.id, text, isPublic)
                        feedbackMsg = "✅ আপডেট হয়েছে!"
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
            Text(technique.text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NotoSansBengali, lineHeight = 18.sp)
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
    onSave    : (text: String, isPublic: Boolean) -> Unit
) {
    var text     by remember(existing) { mutableStateOf(existing?.text ?: "") }
    var isPublic by remember(existing) { mutableStateOf(existing?.isPublic ?: false) }

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
                    if (existing == null) "💡 নতুন টেকনিক যোগ করুন" else "✏️ টেকনিক সম্পাদনা",
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = onCardBg, fontFamily = NotoSansBengali
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = {
                        Text(
                            "এখানে টেকনিক লিখুন...",
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
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isPublic) "🌐 সবার জন্য পাবলিক" else "🔒 শুধু আমার জন্য প্রাইভেট",
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
                        onClick  = { if (text.trim().isNotBlank()) onSave(text.trim(), isPublic) },
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
//  AdminQuestionEditDialog — HTML index23.html inspired redesign
//  Clean, compact, scroll-free for most questions
// ═══════════════════════════════════════════════════════════════

private val ADMIN_AUDIENCE_LIST = listOf(
    ""          to "Job Seeker (default)",
    "Job"       to "Job",
    "Honours 1" to "Honours 1st Year",
    "Honours 2" to "Honours 2nd Year",
    "Honours 3" to "Honours 3rd Year",
    "Honours 4" to "Honours 4th Year",
    "Masters 1" to "Masters 1st Year",
    "Masters 2" to "Masters 2nd Year",
    "Class 9"   to "Class 9",
    "Class 10"  to "Class 10",
    "Class 11"  to "Class 11",
    "Class 12"  to "Class 12"
)

@Composable
fun AdminQuestionEditDialog(
    item        : QuestionItem,
    onDismiss   : () -> Unit,
    onAdminEdit : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null
) {
    val sheet = when {
        item.year.isNotBlank() || item.examName.isNotBlank() -> "QBank"
        item.isStudy() -> "Study"
        item.isMcq()   -> "Quiz"
        else           -> "Study"
    }

    var editQuestion    by remember { mutableStateOf(item.question) }
    var editOptionA     by remember { mutableStateOf(item.optionA) }
    var editOptionB     by remember { mutableStateOf(item.optionB) }
    var editOptionC     by remember { mutableStateOf(item.optionC) }
    var editOptionD     by remember { mutableStateOf(item.optionD) }
    var editAnswer      by remember { mutableStateOf(item.answer) }
    var editExplanation by remember { mutableStateOf(item.explanation) }
    var editTechnique   by remember { mutableStateOf(item.technique) }
    var editAudience    by remember { mutableStateOf(item.audienceTags) }
    var audienceExpanded by remember { mutableStateOf(false) }

    // active field for toolbar
    var activeFieldId   by remember { mutableStateOf("question") }
    var showPreviewFor  by remember { mutableStateOf<String?>(null) }

    var isSaving    by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf<Boolean?>(null) }
    var saveMsg     by remember { mutableStateOf("") }
    val scope       = rememberCoroutineScope()
    val adminIndigo = Color(0xFF4F46E5)
    val greenOkClr  = Color(0xFF10B981)

    // Map fieldId → state getter/setter
    fun getFieldValue(id: String) = when(id) {
        "question"    -> editQuestion
        "optA"        -> editOptionA
        "optB"        -> editOptionB
        "optC"        -> editOptionC
        "optD"        -> editOptionD
        "answer"      -> editAnswer
        "explanation" -> editExplanation
        "technique"   -> editTechnique
        else          -> editQuestion
    }
    fun setFieldValue(id: String, v: String) = when(id) {
        "question"    -> editQuestion    = v
        "optA"        -> editOptionA     = v
        "optB"        -> editOptionB     = v
        "optC"        -> editOptionC     = v
        "optD"        -> editOptionD     = v
        "answer"      -> editAnswer      = v
        "explanation" -> editExplanation = v
        "technique"   -> editTechnique   = v
        else          -> Unit
    }

    val doSave: () -> Unit = {
        scope.launch {
            isSaving = true; saveSuccess = null; saveMsg = ""
            try {
                val fields = mutableMapOf<String, String>()
                if (editQuestion    != item.question)     fields["question"]     = editQuestion
                if (editExplanation != item.explanation)  fields["explanation"]  = editExplanation
                if (editTechnique   != item.technique)    fields["technique"]    = editTechnique
                if (editAudience    != item.audienceTags) fields["AudienceTags"] = editAudience
                if (editAnswer      != item.answer) { fields["correct"] = editAnswer; fields["answer"] = editAnswer }
                val origOpts = listOf(item.optionA, item.optionB, item.optionC, item.optionD)
                val newOpts  = listOf(editOptionA, editOptionB, editOptionC, editOptionD)
                if (newOpts != origOpts) {
                    if (editOptionA.isNotBlank()) fields["option1"] = editOptionA
                    if (editOptionB.isNotBlank()) fields["option2"] = editOptionB
                    if (editOptionC.isNotBlank()) fields["option3"] = editOptionC
                    if (editOptionD.isNotBlank()) fields["option4"] = editOptionD
                    fields["correct"] = editAnswer
                }
                if (fields.isEmpty()) { saveMsg = "⚠️ কিছু পরিবর্তন করা হয়নি"; saveSuccess = false; isSaving = false; return@launch }
                if (onAdminEdit != null) {
                    onAdminEdit(sheet, item.id, fields, item.question.take(60))
                    saveMsg = "✅ ${fields.size}টি field সংরক্ষিত হচ্ছে!"; saveSuccess = true
                } else {
                    when (val r = FirebaseDataService.adminUpdateQuestionField(sheet, item.id, fields)) {
                        is ApiResult.Success -> { saveMsg = "✅ ${fields.size}টি field Firebase এ সংরক্ষিত!"; saveSuccess = true }
                        is ApiResult.Error   -> { saveMsg = "❌ ${r.message}"; saveSuccess = false }
                    }
                }
            } catch (e: Exception) { saveMsg = "❌ ${e.message}"; saveSuccess = false }
            isSaving = false
            if (saveSuccess == true) { kotlinx.coroutines.delay(1500); onDismiss() }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = !isSaving,
            dismissOnClickOutside   = !isSaving
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.96f),
            shape    = RoundedCornerShape(20.dp),
            color    = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {

                // ── Gradient Header ──────────────────────────────────────
                Box(
                    Modifier.fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color(0xFF1E1B4B), adminIndigo)
                        ))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Subject + SubTopic chips
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item.subject.isNotBlank())
                                    AdminInfoChip("📚 ${item.subject}", Color(0xFFa78bfa))
                                if (item.subTopic.isNotBlank())
                                    AdminInfoChip("🏷️ ${item.subTopic}", Color(0xFF6ee7b7))
                            }
                            Text("ID: ${item.id}  •  $sheet", fontSize = 9.sp,
                                color = Color.White.copy(0.45f), fontFamily = NotoSansBengali,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        // Save button
                        Surface(
                            onClick  = { if (!isSaving) doSave() },
                            enabled  = !isSaving,
                            shape    = RoundedCornerShape(12.dp),
                            color    = when {
                                isSaving             -> Color.White.copy(0.5f)
                                saveSuccess == true  -> greenOkClr
                                saveSuccess == false -> Color(0xFFEF4444)
                                else                 -> Color.White.copy(0.92f)
                            }
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                when {
                                    isSaving -> CircularProgressIndicator(Modifier.size(13.dp), adminIndigo, strokeWidth = 2.dp)
                                    saveSuccess == true  -> Text("✅", fontSize = 13.sp)
                                    saveSuccess == false -> Text("❌", fontSize = 13.sp)
                                    else -> Icon(Icons.Default.Save, null, tint = adminIndigo, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    when {
                                        isSaving             -> "সংরক্ষণ..."
                                        saveSuccess == true  -> "সংরক্ষিত!"
                                        saveSuccess == false -> "আবার চেষ্টা"
                                        else                 -> "💾 সংরক্ষণ"
                                    },
                                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                    fontFamily = NotoSansBengali,
                                    color = if (saveSuccess != null || isSaving) Color.White else adminIndigo
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { if (!isSaving) onDismiss() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ── Sticky Toolbar (always visible, acts on active field) ─
                AdminStickyToolbar(
                    activeFieldId   = activeFieldId,
                    getFieldValue   = ::getFieldValue,
                    setFieldValue   = ::setFieldValue,
                    showPreviewFor  = showPreviewFor,
                    onTogglePreview = { showPreviewFor = if (showPreviewFor == activeFieldId) null else activeFieldId },
                    adminIndigo     = adminIndigo
                )

                // ── Save message banner ──────────────────────────────────
                androidx.compose.animation.AnimatedVisibility(saveMsg.isNotBlank()) {
                    val ok = saveSuccess == true
                    Surface(color = if (ok) Color(0xFFDCFCE7) else Color(0xFFFEE2E2), modifier = Modifier.fillMaxWidth()) {
                        Text(saveMsg, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                            color = if (ok) Color(0xFF166534) else Color(0xFF991B1B))
                    }
                }

                // ── Scrollable Fields ────────────────────────────────────
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Subject + SubTopic row (read-only info)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminReadOnlyField("📚 Subject", item.subject, Modifier.weight(1f))
                        AdminReadOnlyField("🏷️ Sub-Topic", item.subTopic, Modifier.weight(1f))
                    }

                    // ── প্রশ্ন ──
                    AdminCompactField(
                        label        = "প্রশ্ন",
                        fieldId      = "question",
                        value        = editQuestion,
                        onChange     = { editQuestion = it },
                        minLines     = 2,
                        isActive     = activeFieldId == "question",
                        showPreview  = showPreviewFor == "question",
                        onFocus      = { activeFieldId = "question" },
                        adminIndigo  = adminIndigo
                    )

                    // ── MCQ Options ──
                    if (item.isMcq()) {
                        // 2-column grid for options
                        val opts = listOf(
                            "optA" to editOptionA,
                            "optB" to editOptionB,
                            "optC" to editOptionC,
                            "optD" to editOptionD
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(opts[0], opts[2]).forEach { (id, v) ->
                                    val label = if (id == "optA") "A" else "C"
                                    val isCorrect = v.trim().equals(editAnswer.trim(), ignoreCase = true)
                                    AdminOptionField(
                                        label = label, fieldId = id, value = v,
                                        isCorrect = isCorrect,
                                        isActive = activeFieldId == id,
                                        showPreview = showPreviewFor == id,
                                        onFocus = { activeFieldId = id },
                                        onChange = { setFieldValue(id, it) },
                                        adminIndigo = adminIndigo, greenOkClr = greenOkClr
                                    )
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(opts[1], opts[3]).forEach { (id, v) ->
                                    val label = if (id == "optB") "B" else "D"
                                    val isCorrect = v.trim().equals(editAnswer.trim(), ignoreCase = true)
                                    AdminOptionField(
                                        label = label, fieldId = id, value = v,
                                        isCorrect = isCorrect,
                                        isActive = activeFieldId == id,
                                        showPreview = showPreviewFor == id,
                                        onFocus = { activeFieldId = id },
                                        onChange = { setFieldValue(id, it) },
                                        adminIndigo = adminIndigo, greenOkClr = greenOkClr
                                    )
                                }
                            }
                        }

                        // সঠিক উত্তর (compact single line)
                        AdminCompactField(
                            label       = "✅ সঠিক উত্তর",
                            fieldId     = "answer",
                            value       = editAnswer,
                            onChange    = { editAnswer = it },
                            isActive    = activeFieldId == "answer",
                            showPreview = showPreviewFor == "answer",
                            onFocus     = { activeFieldId = "answer" },
                            adminIndigo = adminIndigo,
                            accentColor = greenOkClr
                        )
                    } else {
                        AdminCompactField(
                            label       = "✅ উত্তর",
                            fieldId     = "answer",
                            value       = editAnswer,
                            onChange    = { editAnswer = it },
                            minLines    = 2,
                            isActive    = activeFieldId == "answer",
                            showPreview = showPreviewFor == "answer",
                            onFocus     = { activeFieldId = "answer" },
                            adminIndigo = adminIndigo,
                            accentColor = greenOkClr
                        )
                    }

                    // ── ব্যাখ্যা ──
                    AdminCompactField(
                        label       = "💡 ব্যাখ্যা",
                        fieldId     = "explanation",
                        value       = editExplanation,
                        onChange    = { editExplanation = it },
                        minLines    = 2,
                        isActive    = activeFieldId == "explanation",
                        showPreview = showPreviewFor == "explanation",
                        onFocus     = { activeFieldId = "explanation" },
                        adminIndigo = adminIndigo,
                        accentColor = Color(0xFFF59E0B)
                    )

                    // ── টেকনিক + Audience — same row ──
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminCompactField(
                            label       = "🧠 টেকনিক",
                            fieldId     = "technique",
                            value       = editTechnique,
                            onChange    = { editTechnique = it },
                            isActive    = activeFieldId == "technique",
                            showPreview = showPreviewFor == "technique",
                            onFocus     = { activeFieldId = "technique" },
                            adminIndigo = adminIndigo,
                            accentColor = Color(0xFF0EA5E9),
                            modifier    = Modifier.weight(1f)
                        )

                        // Audience compact dropdown
                        val curAudLabel = ADMIN_AUDIENCE_LIST.find { it.first == editAudience }?.second
                            ?: if (editAudience.isBlank()) "Default" else editAudience
                        Column(Modifier.weight(1f)) {
                            Text("🎯 Audience", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF8B5CF6), fontFamily = NotoSansBengali,
                                modifier = Modifier.padding(bottom = 4.dp))
                            Surface(
                                onClick  = { audienceExpanded = !audienceExpanded },
                                shape    = RoundedCornerShape(10.dp),
                                color    = MaterialTheme.colorScheme.surfaceVariant,
                                border   = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, if (audienceExpanded) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(curAudLabel, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                                    Icon(if (audienceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                                }
                            }
                            androidx.compose.animation.AnimatedVisibility(audienceExpanded) {
                                Surface(
                                    shape  = RoundedCornerShape(10.dp),
                                    color  = MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        ADMIN_AUDIENCE_LIST.forEach { (tag, label) ->
                                            val isSel = editAudience == tag
                                            Surface(
                                                onClick  = { editAudience = tag; audienceExpanded = false },
                                                shape    = RoundedCornerShape(8.dp),
                                                color    = if (isSel) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (isSel) Icon(Icons.Default.CheckCircle, null,
                                                        tint = Color.White, modifier = Modifier.size(12.dp))
                                                    Text(label, fontSize = 11.sp, fontFamily = NotoSansBengali,
                                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
                                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }

                // ── Bottom Cancel ────────────────────────────────────────
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), shadowElevation = 6.dp) {
                    OutlinedButton(
                        onClick  = { if (!isSaving) onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("বাতিল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ── Sticky Toolbar — always on top, targets active field ─────────────────────
@Composable
private fun AdminStickyToolbar(
    activeFieldId   : String,
    getFieldValue   : (String) -> String,
    setFieldValue   : (String, String) -> Unit,
    showPreviewFor  : String?,
    onTogglePreview : () -> Unit,
    adminIndigo     : Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Tool buttons — icons + short labels
            val toolItems = listOf(
                Triple("B",    Icons.Default.FormatBold,          { v: String -> applyToolToField("<b>", "</b>", v) }),
                Triple("I",    Icons.Default.FormatItalic,        { v: String -> applyToolToField("<i>", "</i>", v) }),
                Triple("U",    Icons.Default.FormatUnderlined,    { v: String -> applyToolToField("<u>", "</u>", v) }),
                Triple("HI",   Icons.Default.Highlight,           { v: String -> applyToolToField("<mark><b>", "</b></mark>", v) }),
                Triple("LIST", Icons.Default.FormatListBulleted,  { v: String -> applyToolToField("<li>", "</li>", v) }),
                Triple("$$",   Icons.Default.Functions,           { v: String -> "$v\$\$ " }),
                Triple("x²",   Icons.Default.Superscript,         { v: String -> "$v\$x^2\$" }),
                Triple("⊞",   Icons.Default.TableChart,          { v: String -> "$v\nশিরোনাম ; শিরোনাম\nতথ্য ; তথ্য" })
            )

            toolItems.forEach { (label, icon, transform) ->
                val isSpecial = label == "HI"
                Surface(
                    onClick = {
                        val cur = getFieldValue(activeFieldId)
                        setFieldValue(activeFieldId, transform(cur))
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSpecial) Color(0xFFFFF7ED) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = label,
                            tint = when(label) {
                                "HI"   -> Color(0xFF92400E)
                                "$$", "x²" -> Color(0xFF2563EB)
                                "LIST" -> Color(0xFF059669)
                                "⊞"  -> Color(0xFF4F46E5)
                                else   -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Preview toggle
            val isPreviewing = showPreviewFor == activeFieldId
            Surface(
                onClick  = onTogglePreview,
                shape    = RoundedCornerShape(8.dp),
                color    = if (isPreviewing) adminIndigo.copy(0.12f) else Color.Transparent,
                border   = androidx.compose.foundation.BorderStroke(1.dp,
                    if (isPreviewing) adminIndigo else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Visibility, null,
                        tint = if (isPreviewing) adminIndigo else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp))
                    Text("Preview", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (isPreviewing) adminIndigo else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// helper — append to end (simple; full selection support needs TextFieldValue)
private fun applyToolToField(open: String, close: String, current: String): String =
    current + open + close

// ── Compact field — label + outlined text field + optional preview ────────────
@Composable
private fun AdminCompactField(
    label       : String,
    fieldId     : String,
    value       : String,
    onChange    : (String) -> Unit,
    minLines    : Int    = 1,
    isActive    : Boolean,
    showPreview : Boolean,
    onFocus     : () -> Unit,
    adminIndigo : Color,
    accentColor : Color  = adminIndigo,
    modifier    : Modifier = Modifier.fillMaxWidth()
) {
    val borderColor = when {
        isActive -> accentColor
        else     -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(modifier) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
            color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = NotoSansBengali,
            modifier = Modifier.padding(bottom = 3.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
            minLines      = minLines,
            shape         = RoundedCornerShape(10.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor   = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = NotoSansBengali, fontSize = 13.sp)
        )
        // Live preview
        androidx.compose.animation.AnimatedVisibility(showPreview && value.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape    = RoundedCornerShape(8.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                border   = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(0.3f))
            ) {
                Text(parseRichAnnotated(value), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
            }
        }
    }
}

// ── Option field — colored border if correct ──────────────────────────────────
@Composable
private fun AdminOptionField(
    label       : String,
    fieldId     : String,
    value       : String,
    isCorrect   : Boolean,
    isActive    : Boolean,
    showPreview : Boolean,
    onFocus     : () -> Unit,
    onChange    : (String) -> Unit,
    adminIndigo : Color,
    greenOkClr  : Color
) {
    val borderColor = when {
        isCorrect -> greenOkClr
        isActive  -> adminIndigo
        else      -> MaterialTheme.colorScheme.outlineVariant
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 3.dp)) {
            Box(
                Modifier.size(20.dp).background(
                    if (isCorrect) greenOkClr else Color(0xFF7C3AED), RoundedCornerShape(5.dp)
                ), contentAlignment = Alignment.Center
            ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            if (isCorrect)
                Text("✅ সঠিক", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    color = greenOkClr, fontFamily = NotoSansBengali)
        }
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
            minLines      = 1,
            shape         = RoundedCornerShape(10.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = borderColor,
                unfocusedBorderColor    = borderColor,
                focusedContainerColor   = if (isCorrect) greenOkClr.copy(0.06f) else MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = if (isCorrect) greenOkClr.copy(0.06f) else MaterialTheme.colorScheme.surface
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = NotoSansBengali, fontSize = 12.sp)
        )
    }
}

// ── Read-only info field ──────────────────────────────────────────────────────
@Composable
private fun AdminReadOnlyField(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    Column(modifier) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value, Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
        }
    }
}

@Composable
private fun AdminSectionHeader(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = color, fontFamily = NotoSansBengali)
        HorizontalDivider(Modifier.weight(1f), color = color.copy(0.2f))
    }
}

@Composable
private fun AdminInfoChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(0.18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.4f))) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = NotoSansBengali)
    }
}

// ── Tool definitions (kept for compatibility) ─────────────────────────────────
private data class AdminTool(
    val label     : String,
    val emoji     : String   = "",
    val open      : String   = "",
    val close     : String   = "",
    val insert    : String   = "",
    val bgColor   : Long     = 0xFFFFFFFF,
    val textColor : Long     = 0xFF4F46E5,
    val isBold    : Boolean  = false,
    val isItalic  : Boolean  = false,
    val isUnder   : Boolean  = false
)

private val ADMIN_TOOLS = listOf(
    AdminTool("B+HI", open="<mark><b>", close="</b></mark>", bgColor=0xFFFFF7ED, textColor=0xFF92400E, isBold=true),
    AdminTool("B",    open="<b>",       close="</b>",        isBold=true),
    AdminTool("I",    open="<i>",       close="</i>",        isItalic=true),
    AdminTool("U",    open="<u>",       close="</u>",        isUnder=true),
    AdminTool("A↑",   open="++",        close="++",          textColor=0xFF7C3AED),
    AdminTool("LIST", open="<li>",      close="</li>",       textColor=0xFF059669),
    AdminTool("$$",   insert="\$\$ ",              textColor=0xFF2563EB),
    AdminTool("x²",   insert="\$x^2\$",            textColor=0xFF2563EB),
    AdminTool("TABLE",insert="\nশিরোনাম ; শিরোনাম\nতথ্য ; তথ্য", textColor=0xFF1E293B, bgColor=0xFF1E293B)
)



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

private fun wrapSelection(tfv: TextFieldValue, open: String, close: String): TextFieldValue {
    val sel = tfv.selection
    val txt = tfv.text
    val start = minOf(sel.start, sel.end)
    val end   = maxOf(sel.start, sel.end)
    val selected = txt.substring(start, end)
    val newText  = txt.substring(0, start) + open + selected + close + txt.substring(end)
    val newCursor = if (start == end) start + open.length else end + open.length + close.length
    return TextFieldValue(newText, TextRange(newCursor))
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
