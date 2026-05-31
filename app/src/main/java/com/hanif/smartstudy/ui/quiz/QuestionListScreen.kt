package com.hanif.smartstudy.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.shared.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.QuizViewModel

@Composable
fun QuestionListScreen(
    viewModel : QuizViewModel,
    mode      : StudyMode,
    subject   : String,
    subTopic  : String,
    questions : List<QuestionItem>,
    timerSec  : Int,
    totalTime : Int,
    answered  : Int,
    onBack    : () -> Unit,
    onSubmit  : () -> Unit
) {
    val listState = rememberLazyListState()
    var reportIdx by remember { mutableStateOf(-1) }
    var showSubmitDialog by remember { mutableStateOf(false) }

    val readingIdx by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(readingIdx) { viewModel.updateReadingIndex(readingIdx) }

    // ── Back button = Android system back ──
    BackHandler { onBack() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                QuestionTopBar(
                    mode     = mode,
                    subject  = subject,
                    subTopic = subTopic,
                    answered = answered,
                    total    = questions.size,
                    onBack   = onBack,
                    onSubmit = if (mode != StudyMode.STUDY) {{ showSubmitDialog = true }} else null
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {

                // Timer bar
                if (mode != StudyMode.STUDY && totalTime > 0) {
                    TimerBar(timerSec = timerSec, totalSec = totalTime)
                }

                // Reading progress bar
                ReadingProgressBar(current = readingIdx + 1, total = questions.size)

                // Question list
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                        QuestionCard(
                            index       = idx,
                            item        = q,
                            mode        = mode,
                            totalCount  = questions.size,
                            onMcqAnswer = { opt -> viewModel.answerMcq(idx, opt) },
                            onWritten   = { text -> viewModel.answerWritten(idx, text) },
                            onBookmark  = { viewModel.toggleBookmark(q.id) },
                            onReport    = { reportIdx = idx }
                        )
                    }
                }
            }
        }

        // ── Floating bottom bar — question counter + submit ──
        if (questions.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4F46E5)))
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // প্রশ্ন নম্বর counter
                    Column {
                        Text(
                            "${readingIdx + 1}/${questions.size}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = NotoSansBengali
                        )
                        Text(
                            "প্রশ্ন নম্বর",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = NotoSansBengali
                        )
                    }

                    // Answered count
                    if (mode != StudyMode.STUDY) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$answered উত্তর",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF86EFAC),
                                fontFamily = NotoSansBengali
                            )
                            Text(
                                "${questions.size - answered} বাকি",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontFamily = NotoSansBengali
                            )
                        }

                        // Submit button
                        Button(
                            onClick = { showSubmitDialog = true },
                            shape   = RoundedCornerShape(20.dp),
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "✅ সাবমিট",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White,
                                fontFamily = NotoSansBengali
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Submit confirmation dialog ──
    if (showSubmitDialog) {
        val unanswered = questions.count { it.answerState is AnswerState.Unanswered }
        AlertDialog(
            onDismissRequest = { showSubmitDialog = false },
            title = {
                Text("সাবমিট করবেন?", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResultStat("মোট প্রশ্ন", "${questions.size} টি", Color(0xFF4F46E5))
                    ResultStat("উত্তর দিয়েছেন", "$answered টি", Color(0xFF10B981))
                    if (unanswered > 0) {
                        ResultStat("উত্তর দেননি", "$unanswered টি", Color(0xFFEF4444))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSubmitDialog = false; onSubmit() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape   = RoundedCornerShape(12.dp)
                ) {
                    Text("সাবমিট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitDialog = false }) {
                    Text("আরো দেখি", fontFamily = NotoSansBengali, color = Color(0xFF4F46E5))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Report dialog ──
    if (reportIdx >= 0) {
        val q = questions.getOrNull(reportIdx)
        ReportDialog(
            questionId   = q?.id ?: "",
            questionText = q?.question ?: "",
            onReport     = { viewModel.reportQuestion(reportIdx, it); reportIdx = -1 },
            onDismiss    = { reportIdx = -1 }
        )
    }
}

@Composable
private fun ResultStat(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp, color = Color(0xFF64748B))
        Box(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(value, fontFamily = NotoSansBengali, fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionTopBar(
    mode     : StudyMode,
    subject  : String,
    subTopic : String,
    answered : Int,
    total    : Int,
    onBack   : () -> Unit,
    onSubmit : (() -> Unit)?
) {
    TopAppBar(
        title = {
            Column {
                Text(subTopic.ifBlank { subject }, fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B), fontFamily = NotoSansBengali, maxLines = 1)
                if (mode != StudyMode.STUDY) {
                    Text("$subject  ·  ${answered}/${total} উত্তর", fontSize = 10.sp,
                        color = Color(0xFF64748B), fontFamily = NotoSansBengali)
                } else {
                    Text(subject, fontSize = 10.sp,
                        color = Color(0xFF64748B), fontFamily = NotoSansBengali)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null)
            }
        },
        actions = {
            if (onSubmit != null) {
                TextButton(onClick = onSubmit) {
                    Text("সাবমিট", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = Indigo600, fontFamily = NotoSansBengali)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
    )
}
