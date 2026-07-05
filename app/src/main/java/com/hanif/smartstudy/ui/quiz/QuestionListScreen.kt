package com.hanif.smartstudy.ui.quiz

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.hanif.smartstudy.ui.ads.QuizBannerEvery10
import com.hanif.smartstudy.ui.ads.StickyBottomBannerView
import com.hanif.smartstudy.ui.shared.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.AdManager
import com.hanif.smartstudy.viewmodel.QuizViewModel

// ── Vibration helper (API 26+ VibrationEffect, পুরনো device এ fallback) ──
private fun vibrate(ctx: Context, pattern: LongArray, repeat: Int) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, repeat)
            }
        }
    } catch (_: Exception) { }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionListScreen(
    viewModel   : QuizViewModel,
    mode        : StudyMode,
    subject     : String,
    subTopic    : String,
    questions   : List<QuestionItem>,
    timerSec    : Int,
    totalTime   : Int,
    answered    : Int,
    currentPage : Int,
    totalQuestions: Int,   // Room থেকে মোট প্রশ্ন সংখ্যা (questions.size নয়)
    onBack      : () -> Unit,
    onSubmit    : () -> Unit,
    currentUser : com.hanif.smartstudy.data.model.User? = null,
    highlightQuestionId : String? = null,
    onHighlightConsumed : () -> Unit = {},
    onAdminEdit : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null
) {
    val pageSize = QuizViewModel.PAGE_SIZE
    // totalQuestions Room থেকে — questions.size শুধু current page এর count
    val effectiveTotal = if (totalQuestions > 0) totalQuestions else questions.size
    val totalPages = (effectiveTotal + pageSize - 1) / pageSize
    val safeCurrentPage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val pageOffset = safeCurrentPage * pageSize
    // questions এখন শুধু current page এর data (Room থেকে loaded)
    val pagedQuestions = questions   // already paged from Room
    val isLastPage = safeCurrentPage >= totalPages - 1
    val listState = rememberLazyListState()
    var reportIdx by remember { mutableStateOf(-1) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var activeHighlightId by remember { mutableStateOf<String?>(null) }

    // ── Sound + Vibration feedback ──
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val feedbackEvent by viewModel.feedbackEvent.collectAsState()
    LaunchedEffect(feedbackEvent) {
        val isCorrect = feedbackEvent ?: return@LaunchedEffect
        if (isCorrect) {
            com.hanif.smartstudy.util.SoundManager.playCorrect()
            // সঠিক উত্তর: একটা ছোট confirmation vibration
            vibrate(ctx, longArrayOf(0, 60), -1)
        } else {
            com.hanif.smartstudy.util.SoundManager.playWrong()
            // ভুল উত্তর: দুটো ছোট jolt
            vibrate(ctx, longArrayOf(0, 80, 60, 80), -1)
        }
        viewModel.clearFeedback()
    }

    LaunchedEffect(highlightQuestionId, questions) {
        val targetId = highlightQuestionId ?: return@LaunchedEffect
        val globalIdx = questions.indexOfFirst { it.id == targetId }
        if (globalIdx >= 0) {
            // সঠিক page-এ যাও প্রথমে
            val targetPage = globalIdx / pageSize
            if (targetPage != safeCurrentPage) viewModel.goToPage(targetPage)
            val localIdx = globalIdx % pageSize
            listState.animateScrollToItem(localIdx)
            activeHighlightId = targetId
            kotlinx.coroutines.delay(2500)
            activeHighlightId = null
            onHighlightConsumed()
        }
    }

    // Page পরিবর্তন হলে list এর উপরে scroll করো
    LaunchedEffect(safeCurrentPage) {
        listState.scrollToItem(0)
    }

    val readingIdx by remember { derivedStateOf { pageOffset + listState.firstVisibleItemIndex } }
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

                // ── Quiz: প্রতি ১০ প্রশ্নে sticky top banner ──
                if (mode != StudyMode.STUDY) {
                    QuizBannerEvery10(
                        currentQuestionIndex = readingIdx,
                        adUnitId = AdManager.BANNER_QUIZ_LIST
                    )
                }

                // ── Study mode: bottom sticky banner ──
                // (Box layout এর নিচে আলাদাভাবে দেখানো হবে)

                // Reading progress bar
                ReadingProgressBar(current = readingIdx + 1, total = effectiveTotal)

                // Question list
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(pagedQuestions, key = { _, q -> q.id }) { localIdx, q ->
                        val globalIdx = pageOffset + localIdx
                        val isHighlighted = q.id == activeHighlightId
                        // ── Study mode-এ "পড়া হয়েছে" টিক দিলে আইটেম লিস্টের নিচে চলে যায় —
                        //    animateItemPlacement() দিয়ে এই পজিশন-চেঞ্জটা হালকা স্মূথ এনিমেশনে
                        //    হয়, পুরো স্ক্রিন/স্ক্রল জাম্প করে না, বাকি আইটেমগুলো নিজ জায়গায়
                        //    স্মূথভাবে সরে আসে আর পরের প্রশ্নটা এমনিতেই ভেসে ওঠে ──
                        Box(
                            modifier = Modifier.animateItemPlacement(
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                            )
                        ) {
                        if (isHighlighted) {
                            // ── Report-resolved glow highlight ─────────────────
                            val hlTransition = rememberInfiniteTransition(label = "reportResolvedHL")
                            val hlAlpha by hlTransition.animateFloat(
                                initialValue = 0.08f, targetValue = 0.32f,
                                animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "hlAlpha"
                            )
                            val hlBorder by hlTransition.animateFloat(
                                initialValue = 0.4f, targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "hlBorder"
                            )
                            val hlScale by hlTransition.animateFloat(
                                initialValue = 1.0f, targetValue = 1.012f,
                                animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "hlScale"
                            )
                            val resolvedGreen = Color(0xFF10B981)
                            Box(
                                modifier = Modifier
                                    .graphicsLayer(scaleX = hlScale, scaleY = hlScale)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(resolvedGreen.copy(alpha = hlAlpha))
                                    .border(2.dp, resolvedGreen.copy(alpha = hlBorder), RoundedCornerShape(16.dp))
                            ) {
                                QuestionCard(
                                    index       = globalIdx,
                                    item        = q,
                                    mode        = mode,
                                    totalCount  = questions.size,
                                    onMcqAnswer = { opt -> viewModel.answerMcq(globalIdx, opt) },
                                    onWritten   = { text -> viewModel.answerWritten(globalIdx, text) },
                                    onWrittenDraft = { text -> viewModel.updateWrittenDraft(q.sourceKey(), text) },
                                    onBookmark  = { viewModel.toggleBookmark(q.id) },
                                    onStudyDone = { viewModel.toggleStudyDone(q.id) },
                                    onReport    = { reportIdx = globalIdx },
                                    currentUser = currentUser,
                                    onAdminRefresh = { viewModel.adminRefreshContent() },
                                    onAdminEdit = onAdminEdit
                                )
                            }
                        } else {
                        QuestionCard(
                            index       = globalIdx,
                            item        = q,
                            mode        = mode,
                            totalCount  = questions.size,
                            onMcqAnswer = { opt -> viewModel.answerMcq(globalIdx, opt) },
                            onWritten   = { text -> viewModel.answerWritten(globalIdx, text) },
                            onWrittenDraft = { text -> viewModel.updateWrittenDraft(q.sourceKey(), text) },
                            onBookmark  = { viewModel.toggleBookmark(q.id) },
                            onStudyDone = { viewModel.toggleStudyDone(q.id) },
                            onReport    = { reportIdx = globalIdx },
                            currentUser = currentUser,
                            onAdminRefresh = { viewModel.adminRefreshContent() },
                            onAdminEdit = onAdminEdit
                        )
                        }
                        }
                    }
                }
            }
        }

        // ── Study mode: bottom sticky banner ──
        if (mode == StudyMode.STUDY) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                StickyBottomBannerView(adUnitId = AdManager.BANNER_STUDY)
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
                            "${readingIdx + 1}/$effectiveTotal",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = NotoSansBengali
                        )
                        Text(
                            if (totalPages > 1) "পৃষ্ঠা ${safeCurrentPage + 1}/$totalPages" else "প্রশ্ন নম্বর",
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

                        // Next page or Submit button
                        if (!isLastPage) {
                            // পরবর্তী পৃষ্ঠা বাটন
                            Button(
                                onClick = {
                                    viewModel.goToPage(safeCurrentPage + 1)
                                },
                                shape   = RoundedCornerShape(20.dp),
                                colors  = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4F46E5)
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "পরবর্তী ➜",
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White,
                                    fontFamily = NotoSansBengali
                                )
                            }
                        } else {
                            // Submit button (শেষ পৃষ্ঠায়)
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
    }

    // ── Submit confirmation dialog ──
    if (showSubmitDialog) {
        val unanswered = effectiveTotal - answered
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
        Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, maxLines = 1)
                if (mode != StudyMode.STUDY) {
                    Text("$subject  ·  ${answered}/${total} উত্তর", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                } else {
                    Text(subject, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
