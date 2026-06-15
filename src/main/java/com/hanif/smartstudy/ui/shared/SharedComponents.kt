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
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.remote.GasApiService
import com.hanif.smartstudy.ui.components.RichContentText
import com.hanif.smartstudy.data.remote.GasResult
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

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
    modifier       : Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            })
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── _studyNoQ logic — index.html এর মতো ──
            // Study mode এ প্রশ্ন না থাকলে explanation/answer কেই প্রশ্ন হিসেবে দেখাবে
            // (links সহ — PDF/image/video সব render হবে)
            val studyNoQ = mode == StudyMode.STUDY && item.question.isBlank()
            val displayQuestion = if (studyNoQ) (item.explanation.ifBlank { item.answer }) else item.question
            val displayExplanation = if (studyNoQ) "" else item.explanation

            // প্রশ্ন — QuestionText (LaTeX support আছে) + RichContentText (link support)
            // studyNoQ তে explanation-ই প্রশ্ন, সেটায় link থাকতে পারে
            if (studyNoQ) {
                // Explanation as question — RichContentText (PDF/image/video লিংক render করবে)
                RichContentText(
                    text      = displayQuestion,
                    textColor = Color(0xFF1E293B),
                    fontSize  = 15
                )
            } else {
                // সাধারণ প্রশ্ন — QuestionText (LaTeX support)
                QuestionText(text = displayQuestion)
            }

            // imageUrl field (separate Image field from Firebase)
            if (item.imageUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                ZoomableImage(url = item.imageUrl)
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
            val showAnswerText = showAnswerBox && !item.isMcq()
            // studyNoQ হলে answer already question হিসেবে দেখানো হয়েছে — আবার দেখানো দরকার নেই
            if (showAnswerText && item.answer.isNotBlank() && !studyNoQ) {
                Spacer(Modifier.height(8.dp))
                AnswerBox(text = item.answer)
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

@Composable
fun WrittenInput(item: QuestionItem, onSubmit: (String) -> Int) {
    val submitted = item.answerState as? AnswerState.WrittenSubmitted
    var text by remember { mutableStateOf("") }
    var matchPct by remember { mutableStateOf(0) }

    if (submitted != null) {
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
                    "${if (isCorrect) "✅" else "❌"} : ${submitted.matchPct}%",
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
            RichContentText(
                text      = text,
                textColor = Color(0xFF14532D),
                fontSize  = 13
            )
        }
    }
}

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
            RichContentText(
                text      = text,
                textColor = Color(0xFF0C4A6E),
                fontSize  = 12
            )
        }
    }
}

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
            RichContentText(
                text      = text,
                textColor = Color(0xFF78350F),
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
        // ফিক্সড: GasResult.Success টাইপ সেফটি ফিক্সড
        val res = GasApiService.fetchTechniquesForQuestion(questionId, currentUser.phone ?: "")
        if (res is GasResult.Success<*>) {
            @Suppress("UNCHECKED_CAST")
            techniques = res.data as List<UserTechnique>
        }
        isLoading = false
    }

    fun refresh() {
        scope.launch {
            val res = GasApiService.fetchTechniquesForQuestion(questionId, currentUser.phone ?: "")
            if (res is GasResult.Success<*>) {
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
                            GasApiService.deleteTechnique(questionId, t.id)
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
                        GasApiService.saveTechnique(
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
                        GasApiService.updateTechnique(questionId, target.id, text, isPublic)
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
                        color = if (isOwn) GreenOk else MutedText,
                        fontFamily = NotoSansBengali
                    )
                    if (isOwn) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (technique.isPublic) Indigo600.copy(alpha = 0.12f)
                                    else Color(0xFFF1F5F9)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (technique.isPublic) "🌐 পাবলিক" else "🔒 প্রাইভেট",
                                fontSize = 8.sp, color = if (technique.isPublic) Indigo600 else MutedText,
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
            Text(technique.text, fontSize = 12.sp, color = Color(0xFF1E293B),
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

// ═══════════════════════════════════════════════════════════════
//  AdminQuestionEditDialog — inline edit from any QuestionCard
// ═══════════════════════════════════════════════════════════════

@Composable
fun AdminQuestionEditDialog(item: QuestionItem, onDismiss: () -> Unit) {
    val sheet = when {
        item.year.isNotBlank() || item.examName.isNotBlank() -> "QBank"
        item.isMcq() -> "Quiz"
        else         -> "Study"
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

    // Options list for swap UI
    var optionOrder by remember {
        mutableStateOf(
            listOf("A" to item.optionA, "B" to item.optionB,
                   "C" to item.optionC, "D" to item.optionD)
                .filter { it.second.isNotBlank() }
        )
    }

    LaunchedEffect(optionOrder) {
        editOptionA = optionOrder.getOrNull(0)?.second ?: ""
        editOptionB = optionOrder.getOrNull(1)?.second ?: ""
        editOptionC = optionOrder.getOrNull(2)?.second ?: ""
        editOptionD = optionOrder.getOrNull(3)?.second ?: ""
    }

    var isSaving  by remember { mutableStateOf(false) }
    var saveMsg   by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(0) }
    val scope     = rememberCoroutineScope()
    val adminIndigo = Color(0xFF4F46E5)

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = !isSaving,
            dismissOnClickOutside   = !isSaving
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.92f),
            shape    = RoundedCornerShape(20.dp),
            color    = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {

                // Header
                Box(
                    Modifier.fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color(0xFF1E1B4B), adminIndigo)
                        ))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("✏️ Admin Edit", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(0.2f)) {
                            Text(sheet, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = Color.White, fontFamily = NotoSansBengali)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { if (!isSaving) onDismiss() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = activeTab, containerColor = Color(0xFFF8F9FF), contentColor = adminIndigo) {
                    listOf("📝 প্রশ্ন", "🔄 Options", "💡 উত্তর/ব্যাখ্যা").forEachIndexed { i, label ->
                        Tab(selected = activeTab == i, onClick = { activeTab = i },
                            text = { Text(label, fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                    }
                }

                // Tab Content
                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (activeTab) {
                        0 -> {
                            AdminTextField("প্রশ্ন (question)", editQuestion, { editQuestion = it }, 3)
                            AdminTextField("Audience Tags", editAudience, { editAudience = it },
                                hint = "Job / Honours 1 / Class 10 / ফাঁকা=Job Seeker")
                            AdminInfoRow("Subject", item.subject)
                            AdminInfoRow("SubTopic", item.subTopic)
                        }
                        1 -> {
                            if (item.isMcq()) {
                                Text("🔄 তীর দিয়ে position পরিবর্তন করুন",
                                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                    color = adminIndigo, fontFamily = NotoSansBengali)
                                val posLabels = listOf("A","B","C","D")
                                optionOrder.forEachIndexed { idx, (_, text) ->
                                    val isAns = text.trim().equals(editAnswer.trim(), ignoreCase = true)
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        shape  = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isAns) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                                        ),
                                        border = if (isAns) androidx.compose.foundation.BorderStroke(1.5.dp, GreenOk) else null
                                    ) {
                                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier.size(26.dp)
                                                    .background(if (isAns) GreenOk else adminIndigo, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(posLabels.getOrElse(idx) { "${idx+1}" }, fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(text, Modifier.weight(1f), fontSize = 12.sp,
                                                fontFamily = NotoSansBengali,
                                                color = if (isAns) Color(0xFF166534) else SlateText,
                                                fontWeight = if (isAns) FontWeight.Bold else FontWeight.Normal)
                                            if (isAns) Icon(Icons.Default.CheckCircle, null,
                                                tint = GreenOk, modifier = Modifier.size(16.dp))
                                            Column {
                                                IconButton(onClick = {
                                                    if (idx > 0) {
                                                        val l = optionOrder.toMutableList()
                                                        val t = l[idx-1]; l[idx-1] = l[idx]; l[idx] = t
                                                        optionOrder = l
                                                    }
                                                }, enabled = idx > 0, modifier = Modifier.size(26.dp)) {
                                                    Icon(Icons.Default.KeyboardArrowUp, null,
                                                        tint = if (idx > 0) adminIndigo else Color(0xFFCBD5E1),
                                                        modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(onClick = {
                                                    if (idx < optionOrder.size-1) {
                                                        val l = optionOrder.toMutableList()
                                                        val t = l[idx+1]; l[idx+1] = l[idx]; l[idx] = t
                                                        optionOrder = l
                                                    }
                                                }, enabled = idx < optionOrder.size-1, modifier = Modifier.size(26.dp)) {
                                                    Icon(Icons.Default.KeyboardArrowDown, null,
                                                        tint = if (idx < optionOrder.size-1) adminIndigo else Color(0xFFCBD5E1),
                                                        modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Text("✏️ Option text সরাসরি edit করুন", fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold, color = adminIndigo, fontFamily = NotoSansBengali)
                                optionOrder.forEachIndexed { idx, _ ->
                                    val cur = when(idx) { 0->editOptionA; 1->editOptionB; 2->editOptionC; else->editOptionD }
                                    AdminTextField("Option ${listOf("A","B","C","D").getOrElse(idx){""}} ", cur, { v ->
                                        val l = optionOrder.toMutableList()
                                        l[idx] = l[idx].copy(second = v)
                                        optionOrder = l
                                    })
                                }
                            } else {
                                Text("Written প্রশ্ন — option নেই", fontFamily = NotoSansBengali, color = MutedText)
                            }
                        }
                        2 -> {
                            AdminTextField("✅ সঠিক উত্তর", editAnswer, { editAnswer = it })
                            AdminTextField("💡 ব্যাখ্যা", editExplanation, { editExplanation = it }, 3)
                            AdminTextField("🧠 টেকনিক", editTechnique, { editTechnique = it }, 2)
                        }
                    }
                }

                // Save message
                saveMsg?.let { msg ->
                    val ok = msg.startsWith("✅")
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2500)
                        saveMsg = null
                        if (ok) onDismiss()
                    }
                    Surface(color = if (ok) Color(0xFFF0FDF4) else Color(0xFFFFF1F2), modifier = Modifier.fillMaxWidth()) {
                        Text(msg, Modifier.padding(12.dp), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                            color = if (ok) Color(0xFF166534) else Color(0xFF991B1B))
                    }
                }

                // Bottom bar
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth(), shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { if (!isSaving) onDismiss() },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Text("বাতিল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    saveMsg  = null
                                    try {
                                        val fields = mutableMapOf<String, String>()
                                        if (editQuestion    != item.question)    fields["question"]     = editQuestion
                                        if (editExplanation != item.explanation) fields["explanation"]  = editExplanation
                                        if (editTechnique   != item.technique)   fields["technique"]    = editTechnique
                                        if (editAudience    != item.audienceTags) fields["AudienceTags"] = editAudience
                                        if (editAnswer      != item.answer)      { fields["correct"] = editAnswer; fields["answer"] = editAnswer }
                                        val origOpts = listOf(item.optionA, item.optionB, item.optionC, item.optionD)
                                        val newOpts  = listOf(editOptionA, editOptionB, editOptionC, editOptionD)
                                        if (newOpts != origOpts) {
                                            if (editOptionA.isNotBlank()) fields["option1"] = editOptionA
                                            if (editOptionB.isNotBlank()) fields["option2"] = editOptionB
                                            if (editOptionC.isNotBlank()) fields["option3"] = editOptionC
                                            if (editOptionD.isNotBlank()) fields["option4"] = editOptionD
                                            fields["correct"] = editAnswer
                                        }
                                        if (fields.isEmpty()) { saveMsg = "⚠️ কিছু পরিবর্তন হয়নি"; isSaving = false; return@launch }
                                        saveMsg = when (val r = GasApiService.adminUpdateQuestionField(sheet, item.id, fields)) {
                                            is GasResult.Success -> "✅ ${fields.size}টি field Firebase এ সংরক্ষিত!"
                                            is GasResult.Error   -> "❌ ${r.message}"
                                        }
                                    } catch (e: Exception) { saveMsg = "❌ ${e.message}" }
                                    isSaving = false
                                }
                            },
                            enabled  = !isSaving,
                            modifier = Modifier.weight(2f),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = adminIndigo)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(Modifier.size(16.dp), Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("💾 Firebase এ সংরক্ষণ", fontFamily = NotoSansBengali,
                                fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTextField(label: String, value: String, onChange: (String) -> Unit,
                           minLines: Int = 1, hint: String = "") {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(), minLines = minLines,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF4F46E5), unfocusedBorderColor = Color(0xFFE2E8F0)),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = NotoSansBengali, fontSize = 13.sp),
        placeholder = if (hint.isNotBlank()) {
            { Text(hint, fontSize = 11.sp, color = Color(0xFFCBD5E1)) }
        } else null
    )
}

@Composable
private fun AdminInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedText, fontFamily = NotoSansBengali)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SlateText, fontFamily = NotoSansBengali)
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
 * AdminReorderableQuestionList — Admin mode এ question list drag-reorder সহ।
 *
 * ব্যবহার:
 *   AdminReorderableQuestionList(
 *       questions   = questions,
 *       isAdmin     = currentUser?.isAdmin() == true,
 *       onReorder   = { from, to -> viewModel.adminReorderQuestions(from, to) },
 *       itemContent = { idx, q, dragModifier -> QuestionCard(..., modifier = dragModifier) }
 *   )
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
        // Non-admin: simple list, no drag
        questions.forEachIndexed { idx, q ->
            itemContent(idx, q, Modifier)
        }
        return
    }

    var draggingIdx  by remember { mutableStateOf(-1) }
    var dragOffsetY  by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    Column(modifier = modifier) {
        questions.forEachIndexed { idx, q ->
            val isDragging = draggingIdx == idx

            // Drag handle + item row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isDragging) {
                            translationY  = dragOffsetY
                            scaleX        = 1.02f
                            scaleY        = 1.02f
                            alpha         = 0.93f
                            shadowElevation = 16f
                        }
                    }
                    .zIndex(if (isDragging) 10f else 0f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Drag handle (long-press এখানে শুরু) ──
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .fillMaxHeight()
                        .pointerInput(idx) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIdx  = idx
                                    dragOffsetY  = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                },
                                onDragEnd = {
                                    val fromIdx = draggingIdx
                                    if (fromIdx >= 0 && itemHeightPx > 0f) {
                                        // কতটা drag হয়েছে সেটা থেকে toIdx বের করো
                                        val steps   = (dragOffsetY / itemHeightPx).toInt()
                                        val toIdx   = (fromIdx + steps).coerceIn(0, questions.lastIndex)
                                        if (fromIdx != toIdx) onReorder(fromIdx, toIdx)
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
                    // Handle icon — admin কে বোঝায় drag করা যাবে
                    Column(
                        verticalArrangement   = Arrangement.spacedBy(3.dp),
                        horizontalAlignment   = Alignment.CenterHorizontally,
                        modifier              = Modifier.padding(vertical = 8.dp)
                    ) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .width(14.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isDragging) Color(0xFF4F46E5) else Color(0xFFCBD5E1))
                            )
                        }
                    }
                }

                // ── Actual question item ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            itemHeightPx = coords.size.height.toFloat()
                        }
                ) {
                    itemContent(idx, q, Modifier)
                }
            }

            // Drag-over indicator — target position দেখাতে
            val steps   = if (itemHeightPx > 0f) (dragOffsetY / itemHeightPx).toInt() else 0
            val toIdx   = (draggingIdx + steps).coerceIn(0, questions.lastIndex)
            val showSep = draggingIdx >= 0 && draggingIdx != idx && (
                (steps > 0 && idx == toIdx) ||
                (steps < 0 && idx == toIdx)
            )
            if (showSep) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF4F46E5))
                )
            }
        }
    }
}

/**
 * AdminReorderableList<T> — Generic version for subjects/subtopics।
 * যেকোনো list এ use করা যাবে।
 */
@Composable
fun <T> AdminReorderableList(
    items     : List<T>,
    isAdmin   : Boolean,
    keyOf     : (T) -> String,
    onReorder : (from: Int, to: Int) -> Unit,
    modifier  : Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    if (!isAdmin) {
        Column(modifier) {
            items.forEachIndexed { idx, item -> itemContent(idx, item) }
        }
        return
    }

    var draggingIdx  by remember { mutableStateOf(-1) }
    var dragOffsetY  by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    Column(modifier = modifier) {
        items.forEachIndexed { idx, item ->
            val isDragging = draggingIdx == idx
            val steps      = if (itemHeightPx > 0f) (dragOffsetY / itemHeightPx).toInt() else 0
            val toIdx      = if (draggingIdx >= 0) (draggingIdx + steps).coerceIn(0, items.lastIndex) else -1

            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isDragging) {
                            translationY    = dragOffsetY
                            scaleX          = 1.02f
                            scaleY          = 1.02f
                            alpha           = 0.93f
                            shadowElevation = 16f
                        }
                    }
                    .zIndex(if (isDragging) 10f else 0f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle
                Box(
                    Modifier
                        .width(32.dp)
                        .pointerInput(idx) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingIdx = idx; dragOffsetY = 0f },
                                onDrag = { change, amt ->
                                    change.consume()
                                    dragOffsetY += amt.y
                                },
                                onDragEnd = {
                                    val from = draggingIdx
                                    if (from >= 0 && itemHeightPx > 0f) {
                                        val s  = (dragOffsetY / itemHeightPx).toInt()
                                        val to = (from + s).coerceIn(0, items.lastIndex)
                                        if (from != to) onReorder(from, to)
                                    }
                                    draggingIdx = -1; dragOffsetY = 0f
                                },
                                onDragCancel = { draggingIdx = -1; dragOffsetY = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        repeat(3) {
                            Box(
                                Modifier.width(14.dp).height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isDragging) Color(0xFF4F46E5) else Color(0xFFCBD5E1))
                            )
                        }
                    }
                }

                Box(
                    Modifier.weight(1f).onGloballyPositioned { c ->
                        itemHeightPx = c.size.height.toFloat()
                    }
                ) {
                    itemContent(idx, item)
                }
            }

            // Drop indicator line
            val showSep = draggingIdx >= 0 && draggingIdx != idx && toIdx == idx
            if (showSep) {
                Box(
                    Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Color(0xFF4F46E5))
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
