package com.hanif.smartstudy.ui.shared

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.hanif.smartstudy.ui.shared.HapticUtil
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// ── Brand colors ──
val Indigo600   = Color(0xFF4F46E5)
val DeepIndigo  = Color(0xFF1E1B4B)
val GreenOk     = Color(0xFF10B981)
val RedWrong    = Color(0xFFEF4444)
val AmberWarn   = Color(0xFFF59E0B)
val OrangeTech  = Color(0xFFF97316)
val SlateText   = Color(0xFF1E293B)
val MutedText   = Color(0xFF64748B)
val CardBg      = Color(0xFFFFFFFF)
val SlateLight  = Color(0xFFF8FAFC)

// ─────────────────────────────────────────────────────────
// Timer Bar
// ─────────────────────────────────────────────────────────
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
            Modifier.fillMaxWidth().height(5.dp).background(Color(0xFFE2E8F0))
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

// ─────────────────────────────────────────────────────────
// Reading Progress Bar (scroll-based)
// ─────────────────────────────────────────────────────────
@Composable
fun ReadingProgressBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    val pct     = if (total > 0) current.toFloat() / total else 0f
    val animPct by animateFloatAsState(pct, tween(300), label = "readProg")
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0xFFE2E8F0))) {
        Box(
            Modifier.fillMaxWidth(animPct).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Indigo600, Color(0xFF818CF8))))
        )
    }
}

// ─────────────────────────────────────────────────────────
// Question Card
// ─────────────────────────────────────────────────────────
@Composable
fun QuestionCard(
    index       : Int,
    item        : QuestionItem,
    mode        : StudyMode,
    totalCount  : Int       = 0,
    onMcqAnswer : (Int) -> Unit,
    onWritten   : (String) -> Int,
    onBookmark  : () -> Unit,
    onReport    : () -> Unit,
    modifier    : Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // ── Header row ──
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Number badge
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFEEF2FF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("#${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                            color = Indigo600, fontFamily = NotoSansBengali)
                    }
                    if (item.isWritten()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFFFF7ED))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Written", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = OrangeTech)
                        }
                    }
                    if (item.isWeakTopic) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFFFF1F2))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("🔁 Review", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = RedWrong)
                        }
                    }
                    if (item.year.isNotBlank()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF0FDF4))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(item.year, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenOk)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // ── Question text (LaTeX/Math or plain) ──
            QuestionText(text = item.question)

            // ── Image (if any) ──
            if (item.imageUrl.isNotBlank()) {
                ZoomableImage(url = item.imageUrl)
            }

            Spacer(Modifier.height(10.dp))

            // ── MCQ Options / Written Input ──
            when {
                item.isMcq() && mode != StudyMode.STUDY -> {
                    McqOptions(item = item, onAnswer = onMcqAnswer)
                }
                item.isWritten() && mode != StudyMode.STUDY -> {
                    WrittenInput(item = item, onSubmit = onWritten)
                }
                else -> {
                    // Study mode — always show answer
                }
            }

            // ── Answer box (Study mode always; others after answer) ──
            val showAnswerBox = when (mode) {
                StudyMode.STUDY -> true
                else -> item.answerState !is AnswerState.Unanswered
            }
            if (showAnswerBox && item.answer.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                AnswerBox(text = item.answer)
            }

            // ── Explanation box ──
            if (showAnswerBox && item.explanation.isNotBlank() && item.explanation != item.answer) {
                Spacer(Modifier.height(6.dp))
                ExplanationBox(text = item.explanation)
            }

            // ── Technique box ──
            if (showAnswerBox && item.technique.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                TechniqueBox(text = item.technique)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Question Text — LaTeX detect করে WebView দিয়ে render
// ─────────────────────────────────────────────────────────
@Composable
fun QuestionText(text: String, modifier: Modifier = Modifier) {
    val hasLatex = remember(text) { text.contains("\\") || text.contains("\$") || text.contains("frac") }
    if (hasLatex) {
        MathWebView(latex = text, modifier = modifier.fillMaxWidth().heightIn(min = 40.dp, max = 300.dp))
    } else {
        Text(
            text       = text,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Bold,
            color      = SlateText,
            fontFamily = NotoSansBengali,
            lineHeight = 22.sp,
            modifier   = modifier
        )
    }
}

// ─────────────────────────────────────────────────────────
// MathJax WebView
// ─────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────
// MCQ Options
// ─────────────────────────────────────────────────────────
@Composable
fun McqOptions(item: QuestionItem, onAnswer: (Int) -> Unit) {
    val ctx = LocalContext.current
    val answered = item.answerState as? AnswerState.McqSelected
    val options  = listOf(1 to item.optionA, 2 to item.optionB, 3 to item.optionC, 4 to item.optionD)
        .filter { it.second.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (n, text) ->
            val isSelected  = answered?.option == n
            val isCorrectOpt = text.trim().equals(item.answer.trim(), ignoreCase = true)
            val bg = when {
                answered == null        -> CardBg
                isSelected && answered.isCorrect  -> Color(0xFFF0FDF4)
                isSelected && !answered.isCorrect -> Color(0xFFFFF1F2)
                !isSelected && isCorrectOpt && answered != null -> Color(0xFFF0FDF4)
                else -> SlateLight
            }
            val border = when {
                answered == null        -> Color(0xFFE2E8F0)
                isSelected && answered.isCorrect  -> GreenOk
                isSelected && !answered.isCorrect -> RedWrong
                !isSelected && isCorrectOpt && answered != null -> GreenOk
                else -> Color(0xFFE2E8F0)
            }
            val textColor = when {
                answered == null -> SlateText
                isSelected && answered.isCorrect  -> Color(0xFF166534)
                isSelected && !answered.isCorrect -> Color(0xFF9F1239)
                !isSelected && isCorrectOpt && answered != null -> Color(0xFF166534)
                else -> MutedText
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

// ─────────────────────────────────────────────────────────
// Written Input
// ─────────────────────────────────────────────────────────
@Composable
fun WrittenInput(item: QuestionItem, onSubmit: (String) -> Int) {
    val submitted = item.answerState as? AnswerState.WrittenSubmitted
    var text by remember { mutableStateOf("") }
    var matchPct by remember { mutableStateOf(0) }

    if (submitted != null) {
        // Show match result
        val isCorrect = submitted.isCorrect
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCorrect) Color(0xFFF0FDF4) else Color(0xFFFFF1F2)
            ),
            border = BorderStroke(1.dp, if (isCorrect) GreenOk else RedWrong)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "${if (isCorrect) "✅" else "❌"} মিল: ${submitted.matchPct}%",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isCorrect) Color(0xFF166534) else Color(0xFF9F1239),
                    fontFamily = NotoSansBengali
                )
                if (submitted.userText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("তোমার উত্তর: ${submitted.userText}", fontSize = 12.sp,
                        color = MutedText, fontFamily = NotoSansBengali, lineHeight = 16.sp)
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

// ─────────────────────────────────────────────────────────
// Answer Box (green)
// ─────────────────────────────────────────────────────────
@Composable
fun AnswerBox(text: String) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.5.dp, GreenOk)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("উত্তর:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF166534), fontFamily = NotoSansBengali)
            Spacer(Modifier.height(4.dp))
            Text(text, fontSize = 13.sp, color = Color(0xFF14532D),
                fontFamily = NotoSansBengali, lineHeight = 18.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────
// Explanation Box (blue)
// ─────────────────────────────────────────────────────────
@Composable
fun ExplanationBox(text: String) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = BorderStroke(1.dp, Color(0xFF38BDF8))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("ব্যাখ্যা:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0369A1), fontFamily = NotoSansBengali)
            Spacer(Modifier.height(4.dp))
            Text(text, fontSize = 12.sp, color = Color(0xFF0C4A6E),
                fontFamily = NotoSansBengali, lineHeight = 18.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────
// Technique Box (orange)
// ─────────────────────────────────────────────────────────
@Composable
fun TechniqueBox(text: String) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.5.dp, AmberWarn)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("💡 টেকনিক:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF92400E), fontFamily = NotoSansBengali)
            Spacer(Modifier.height(4.dp))
            Text(text, fontSize = 12.sp, color = Color(0xFF78350F),
                fontFamily = NotoSansBengali, lineHeight = 18.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────
// Zoomable Image
// ─────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────
// Report Dialog
// ─────────────────────────────────────────────────────────
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
                        color = MutedText, lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateLight)
                            .padding(8.dp)
                    )
                }
                Text("সমস্যার ধরন:", fontFamily = NotoSansBengali,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateText)
                issues.forEach { issue ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected == issue) Color(0xFFEEF2FF) else SlateLight)
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
                            color = if (selected == issue) Indigo600 else SlateText,
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
                        unfocusedBorderColor = Color(0xFFE2E8F0)
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
                Text("বাতিল", fontFamily = NotoSansBengali, color = MutedText)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
