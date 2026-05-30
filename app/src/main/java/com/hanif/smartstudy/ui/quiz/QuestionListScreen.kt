package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val listState  = rememberLazyListState()
    var reportIdx  by remember { mutableStateOf(-1) }

    // Scroll → reading progress
    val readingIdx by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(readingIdx) {
        viewModel.updateReadingIndex(readingIdx)
    }

    Scaffold(
        topBar = {
            QuestionTopBar(
                mode      = mode,
                subject   = subject,
                subTopic  = subTopic,
                answered  = answered,
                total     = questions.size,
                onBack    = onBack,
                onSubmit  = if (mode != StudyMode.STUDY) onSubmit else null
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Timer bar (quiz / qbank mode)
            if (mode != StudyMode.STUDY && totalTime > 0) {
                TimerBar(timerSec = timerSec, totalSec = totalTime)
            }

            // Reading progress
            ReadingProgressBar(
                current = readingIdx + 1,
                total   = questions.size
            )

            // Question counter badge
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    "${readingIdx + 1}/${questions.size}",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color(0xFF94A3B8),
                    fontFamily = NotoSansBengali
                )
            }

            // Question list
            LazyColumn(
                state               = listState,
                modifier            = Modifier.weight(1f),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                    QuestionCard(
                        index      = idx,
                        item       = q,
                        mode       = mode,
                        onMcqAnswer = { opt -> viewModel.answerMcq(idx, opt) },
                        onWritten  = { text -> viewModel.answerWritten(idx, text) },
                        onBookmark = { viewModel.toggleBookmark(q.id) },
                        onReport   = { reportIdx = idx }
                    )
                }

                // Bottom submit button
                if (mode != StudyMode.STUDY && questions.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick  = onSubmit,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(16.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
                        ) {
                            Text("✅ সাবমিট করুন", fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = NotoSansBengali,
                                modifier   = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }

    // Report dialog
    if (reportIdx >= 0) {
        ReportDialog(
            onReport  = { /* TODO: GAS report endpoint */ reportIdx = -1 },
            onDismiss = { reportIdx = -1 }
        )
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
                Text(subTopic, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B), fontFamily = NotoSansBengali, maxLines = 1)
                Text("$subject  ·  ${answered}/${total} উত্তর", fontSize = 10.sp,
                    color = Color(0xFF64748B), fontFamily = NotoSansBengali)
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
