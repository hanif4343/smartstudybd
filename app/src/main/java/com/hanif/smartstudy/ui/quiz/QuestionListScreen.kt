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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.hanif.smartstudy.ui.theme.AppTheme
import com.hanif.smartstudy.ui.theme.LocalAppTheme
import com.hanif.smartstudy.ui.theme.NordicSage
import com.hanif.smartstudy.ui.theme.NordicSageDeep
import com.hanif.smartstudy.util.AdManager
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.viewmodel.QuizViewModel
import kotlinx.coroutines.launch

// ── Study রিকল-টাইপিং মোডে নিচের ফ্লোটিং "সাবমিট" বাটন চাপলে — বর্তমান
// পৃষ্ঠায় যে যে প্রশ্নে টাইপ-বক্সে খসড়া উত্তর লেখা আছে (Enter চাপা হোক
// বা না হোক) সবগুলো একসাথে AI দিয়ে চেক হয়, প্রতিটার ফলাফল এখানে জমা হয় ──
private data class StudyBulkItem(
    val questionId    : String,
    val questionText  : String,
    val correctAnswer : String,
    val userAnswer    : String,
    val verdict       : Boolean?   // true=ঠিক, false=ভুল, null=AI যাচাই করতে পারেনি
)

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
    onAdminEdit : ((sheet: String, rowKey: String, fields: Map<String, String>, preview: String) -> Unit)? = null,
    onAdminDelete : ((sheet: String, rowKey: String, preview: String) -> Unit)? = null
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
    val scrollScope = rememberCoroutineScope()
    var reportIdx by remember { mutableStateOf(-1) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var activeHighlightId by remember { mutableStateOf<String?>(null) }

    // ── Study: "শুধু প্রশ্ন দেখ" মোড — এই টগলটা শুধু Study screen-এর
    //    টপবারেই থাকে (Settings/Menu-তে না), Quiz/QBank-এ এফেক্ট নেই।
    //    পছন্দটা DataStore-এ সেভ থাকে যাতে পরের বার Study খুললেও মনে থাকে। ──
    val ctxForPrefs = androidx.compose.ui.platform.LocalContext.current
    var studyRevealMode by remember { mutableStateOf(SessionManager(ctxForPrefs).isStudyRevealMode()) }
    val prefsScope = rememberCoroutineScope()
    val onToggleStudyRevealMode: () -> Unit = {
        val newValue = !studyRevealMode
        studyRevealMode = newValue
        prefsScope.launch {
            SessionManager(ctxForPrefs).setStudyRevealMode(newValue)
        }
    }

    // ── Study: ⌨️ রিকল-টাইপিং মোড — টগল করলে টাইপ-করে-উত্তর-মেলানো ফ্লো চালু/বন্ধ হয় ──
    var studyRecallMode by remember { mutableStateOf(SessionManager(ctxForPrefs).isStudyRecallMode()) }
    val onToggleStudyRecallMode: () -> Unit = {
        val newValue = !studyRecallMode
        studyRecallMode = newValue
        prefsScope.launch {
            SessionManager(ctxForPrefs).setStudyRecallMode(newValue)
        }
    }
    // প্রতিটা প্রশ্নের রিকল-টাইপিং টেক্সট-বক্সের FocusRequester — id অনুযায়ী ক্যাশ করা,
    // যাতে একটা প্রশ্ন গ্রেড করার পর পরেরটার বক্সে সরাসরি কার্সর ফোকাস করা যায়
    val recallFocusRequesters = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
    fun recallFocusRequesterFor(id: String) =
        recallFocusRequesters.getOrPut(id) { androidx.compose.ui.focus.FocusRequester() }

    // ── ⌨️ রিকল-টাইপিং মোডে Enter না চেপে টাইপ করার সময়ও প্রতি কি-স্ট্রোকে
    // এখানে (প্রশ্ন id অনুযায়ী) খসড়া উত্তর জমা থাকে — নিচের ফ্লোটিং "সাবমিট"
    // বাটন Enter চাপার অপেক্ষা না করেই এই খসড়া নিয়ে AI দিয়ে ম্যাচ করাতে পারে। ──
    val recallLiveDrafts = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }

    // ── নিচের ফ্লোটিং সাবমিট বাটনের ফলাফল — চেক চলছে কিনা (কতটার মধ্যে
    // কতটা হয়ে গেছে), আর সবগুলোর ফলাফল একসাথে (কয়টা সঠিক/কয়টা ভুল) ──
    var studySubmitChecking by remember { mutableStateOf(false) }
    var studySubmitProgress by remember { mutableStateOf(0 to 0) } // (checked, total)
    var studySubmitResults by remember { mutableStateOf<List<StudyBulkItem>?>(null) }
    var studySubmitShowDetails by remember { mutableStateOf(false) }

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

    // ── Model Test চলাকালীন Written প্রশ্ন হলে টাইপ-করে-মেলানোর বদলে
    //    "উত্তর দেখুন" + ঠিক/ভুল সেলফ-গ্রেড UI দেখাতে হবে ──
    val vmState by viewModel.state.collectAsState()
    val isModelTest = vmState.activeModelTest != null

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
                    onSubmit = if (mode != StudyMode.STUDY) {{ showSubmitDialog = true }} else null,
                    studyRevealMode = studyRevealMode,
                    onToggleStudyRevealMode = onToggleStudyRevealMode,
                    studyRecallMode = studyRecallMode,
                    onToggleStudyRecallMode = onToggleStudyRecallMode
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
                        // ── চেকমার্ক দিলে (study mode) পরের প্রশ্ন কার্ডটা স্মুথলি
                        //    স্ক্রল হয়ে স্ক্রিনে উঠে আসবে — এটা শুধুই স্ক্রল অ্যানিমেশন,
                        //    ডেটার আসল অর্ডার এখনই পাল্টাচ্ছে না (সেটা সাবটপিক পরের বার
                        //    খুললে হবে, ViewModel-এর loadQuestions-এই sort হয়) ──
                        val onStudyDoneWithScroll: () -> Unit = {
                            viewModel.toggleStudyDone(q.id)
                            scrollScope.launch {
                                val nextLocalIdx = (localIdx + 1).coerceAtMost(pagedQuestions.lastIndex)
                                listState.animateScrollToItem(nextLocalIdx)
                            }
                        }
                        // ── ⌨️ রিকল-টাইপিং মোড: ঠিক/ভুল বেছে Enter/ট্যাপ করলে এই প্রশ্নটা
                        //    "পড়া হয়েছে" মার্ক হবে, তারপর পরের প্রশ্নে স্ক্রল করে সেই
                        //    প্রশ্নের টাইপ-বক্সে সরাসরি কার্সর ফোকাস করে দেওয়া হয় — পুরোটা
                        //    কীবোর্ড দিয়েই এক প্রশ্ন থেকে পরের প্রশ্নে চলে যাওয়া যায় ──
                        val onRecallGradedAdvance: (Boolean) -> Unit = {
                            viewModel.toggleStudyDone(q.id)
                            scrollScope.launch {
                                val nextLocalIdx = (localIdx + 1).coerceAtMost(pagedQuestions.lastIndex)
                                listState.animateScrollToItem(nextLocalIdx)
                                if (nextLocalIdx != localIdx) {
                                    val nextQ = pagedQuestions.getOrNull(nextLocalIdx)
                                    if (nextQ != null) {
                                        kotlinx.coroutines.delay(120)
                                        runCatching { recallFocusRequesterFor(nextQ.id).requestFocus() }
                                    }
                                }
                            }
                        }
                        // ── QBank: Written প্রশ্নে ঠিক/ভুল গ্রেড হয়ে গেলে (AI দিয়ে অটো-চেক
                        // বা ম্যানুয়াল বাটনে) স্বয়ংক্রিয়ভাবে পরের প্রশ্নে স্ক্রল হয়ে যায় —
                        // Study-র রিকল-টাইপিং মোডের মতোই একই আচরণ। অন্য মোডে (Quiz/Model
                        // Test) আগের মতোই শুধু ফলাফল সেভ হয়, স্ক্রল হয় না। ──
                        val onWrittenSelfGradeAdvance: (Boolean) -> Unit = { correct ->
                            viewModel.answerWrittenSelfGrade(globalIdx, correct)
                            if (mode == StudyMode.QBANK) {
                                scrollScope.launch {
                                    val nextLocalIdx = (localIdx + 1).coerceAtMost(pagedQuestions.lastIndex)
                                    listState.animateScrollToItem(nextLocalIdx)
                                }
                            }
                        }
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
                                    isModelTest = isModelTest,
                                    onWrittenSelfGrade = onWrittenSelfGradeAdvance,
                                    onBookmark  = { viewModel.toggleBookmark(q.id) },
                                    onStudyDone = onStudyDoneWithScroll,
                                    onReport    = { reportIdx = globalIdx },
                                    currentUser = currentUser,
                                    onAdminRefresh = { viewModel.adminRefreshContent() },
                                    onAdminEdit = onAdminEdit,
                                    onAdminDelete = onAdminDelete,
                                    studyRevealMode = studyRevealMode,
                                    studyRecallMode = studyRecallMode,
                                    answerFocusRequester = recallFocusRequesterFor(q.id),
                                    onRecallGraded = onRecallGradedAdvance,
                                    onRecallDraftChange = { text -> recallLiveDrafts[q.id] = text },
                                    onAiGradeWritten = { question, correctAnswer, userAnswer ->
                                        viewModel.gradeWrittenWithAi(question, correctAnswer, userAnswer)
                                    }
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
                            isModelTest = isModelTest,
                            onWrittenSelfGrade = onWrittenSelfGradeAdvance,
                            onBookmark  = { viewModel.toggleBookmark(q.id) },
                            onStudyDone = onStudyDoneWithScroll,
                            onReport    = { reportIdx = globalIdx },
                            currentUser = currentUser,
                            onAdminRefresh = { viewModel.adminRefreshContent() },
                            onAdminEdit = onAdminEdit,
                            onAdminDelete = onAdminDelete,
                            studyRevealMode = studyRevealMode,
                            studyRecallMode = studyRecallMode,
                            answerFocusRequester = recallFocusRequesterFor(q.id),
                            onRecallGraded = onRecallGradedAdvance,
                            onRecallDraftChange = { text -> recallLiveDrafts[q.id] = text },
                            onAiGradeWritten = { question, correctAnswer, userAnswer ->
                                viewModel.gradeWrittenWithAi(question, correctAnswer, userAnswer)
                            }
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
            // Nordic Pastel থিমে গাঢ় ইন্ডিগো গ্র্যাডিয়েন্টের বদলে সফট ডাবল-সেজ
            // গ্র্যাডিয়েন্ট — বাকি ইন্টারফেসের সাথে টোন মিলিয়ে রাখা হয়।
            val isNordic = LocalAppTheme.current.value == AppTheme.NORDIC
            val barBrush = if (isNordic)
                Brush.horizontalGradient(listOf(NordicSageDeep, NordicSage))
            else
                Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4F46E5)))
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
                        .background(barBrush)
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

                    // ── Study: ⌨️ রিকল-টাইপিং মোড চালু থাকলে এখানে একটা "সাবমিট"
                    // বাটন — Enter না চেপে একটা একটা করে অনেকগুলো প্রশ্নে টাইপ
                    // করে গেলেও, এই পাতায় যেসব প্রশ্নে খসড়া উত্তর লেখা আছে সেই
                    // সবগুলো একসাথে ব্যাকগ্রাউন্ডে AI দিয়ে চেক হয়ে "কয়টা সঠিক /
                    // কয়টা ভুল" এর মোট হিসাব দেখায় (শুধু সর্বশেষ প্রশ্নটার না) —
                    // "বিস্তারিত" চাপলে কোন কোনগুলো ভুল হয়েছে তার তালিকা দেখা যায়। ──
                    if (mode == StudyMode.STUDY && studyRecallMode) {
                        Button(
                            onClick = {
                                // ── ⚠️ এখানে item.isWritten() দিয়ে ফিল্টার করা যাবে না —
                                // Study-র ⌨️ রিকল-টাইপিং বক্স MCQ/Study যেকোনো টাইপের
                                // প্রশ্নেই দেখা যায় (SharedComponents.kt-এর studyRecallActive
                                // এর শর্তের সাথে মিলিয়ে রাখা হলো), শুধু "Written" টাইপে না —
                                // আগে ভুলবশত isWritten() চেক থাকায় অন্য টাইপের প্রশ্নে টাইপ
                                // করা উত্তরগুলো সাবমিটে ধরা পড়ছিল না ──
                                val candidates = pagedQuestions.filter { q ->
                                    q.answer.isNotBlank() &&
                                        recallLiveDrafts[q.id]?.isNotBlank() == true
                                }
                                studySubmitShowDetails = false
                                if (candidates.isEmpty()) {
                                    studySubmitResults = emptyList()
                                } else {
                                    studySubmitResults = null
                                    studySubmitChecking = true
                                    studySubmitProgress = 0 to candidates.size
                                    scrollScope.launch {
                                        val collected = mutableListOf<StudyBulkItem>()
                                        candidates.forEachIndexed { i, q ->
                                            val draft = recallLiveDrafts[q.id].orEmpty()
                                            val qText = q.question.ifBlank { q.explanation.ifBlank { q.answer } }
                                            val verdict = viewModel.gradeWrittenWithAi(qText, q.answer, draft)
                                            collected.add(StudyBulkItem(q.id, qText, q.answer, draft, verdict))
                                            studySubmitProgress = (i + 1) to candidates.size
                                        }
                                        studySubmitChecking = false
                                        studySubmitResults = collected
                                    }
                                }
                            },
                            shape   = RoundedCornerShape(20.dp),
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
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

    // ── Study: ⌨️ রিকল-টাইপিং মোডের নিচের "সাবমিট" বাটনের ফলাফল ডায়ালগ —
    // চেক চলাকালীন কতগুলার মধ্যে কতগুলো চেক হয়ে গেছে তা দেখায়, তারপর মোট
    // "কয়টা সঠিক / কয়টা ভুল" এর হিসাব — "বিস্তারিত" চাপলে কোন কোনগুলো ভুল
    // হয়েছে (সঠিক উত্তর সহ) তার তালিকা দেখা যাবে। ──
    if (mode == StudyMode.STUDY && studyRecallMode && (studySubmitChecking || studySubmitResults != null)) {
        AlertDialog(
            onDismissRequest = {
                if (!studySubmitChecking) {
                    studySubmitResults = null
                    studySubmitShowDetails = false
                }
            },
            title = {
                Text("সাবমিট ফলাফল", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            },
            text = {
                val results = studySubmitResults
                when {
                    studySubmitChecking -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF4F46E5))
                                Spacer(Modifier.width(10.dp))
                                Text("🤖 AI দিয়ে যাচাই করা হচ্ছে…", fontFamily = NotoSansBengali, fontSize = 13.sp)
                            }
                            Text(
                                "${studySubmitProgress.first}/${studySubmitProgress.second} প্রশ্ন চেক হয়েছে",
                                fontFamily = NotoSansBengali, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    results != null && results.isEmpty() -> {
                        Text("কোনো উত্তর টাইপ করা হয়নি — আগে টাইপ-বক্সে উত্তর লিখুন, তারপর সাবমিট করুন।",
                            fontFamily = NotoSansBengali, fontSize = 13.sp)
                    }
                    results != null -> {
                        val checkedResults = results.filter { it.verdict != null }
                        val unknownCount   = results.size - checkedResults.size
                        val correctCount   = checkedResults.count { it.verdict == true }
                        val wrongCount     = checkedResults.count { it.verdict == false }
                        val wrongItems     = results.filter { it.verdict == false }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SubmitStatChip("মোট", "${results.size}", Color(0xFF4F46E5), Modifier.weight(1f))
                                SubmitStatChip("✅ সঠিক", "$correctCount", Color(0xFF10B981), Modifier.weight(1f))
                                SubmitStatChip("❌ ভুল", "$wrongCount", Color(0xFFEF4444), Modifier.weight(1f))
                            }
                            if (unknownCount > 0) {
                                Text(
                                    "⚠️ $unknownCount টা AI দিয়ে যাচাই করা যায়নি (Settings-এ API key নেই/সব ব্যর্থ হয়েছে)।",
                                    fontFamily = NotoSansBengali, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (wrongItems.isNotEmpty()) {
                                TextButton(onClick = { studySubmitShowDetails = !studySubmitShowDetails }) {
                                    Text(
                                        if (studySubmitShowDetails) "বিস্তারিত লুকান" else "🔍 বিস্তারিত (ভুলগুলো দেখুন)",
                                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5)
                                    )
                                }
                                if (studySubmitShowDetails) {
                                    Column(
                                        Modifier
                                            .heightIn(max = 280.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        wrongItems.forEach { item ->
                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(item.questionText, fontFamily = NotoSansBengali,
                                                    fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
                                                Text("সঠিক উত্তর: ${item.correctAnswer}", fontFamily = NotoSansBengali,
                                                    fontSize = 11.sp, color = Color(0xFF10B981))
                                                Text("তোমার উত্তর: ${item.userAnswer}", fontFamily = NotoSansBengali,
                                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !studySubmitChecking,
                    onClick = {
                        studySubmitResults = null
                        studySubmitShowDetails = false
                    }
                ) {
                    Text("বন্ধ করুন", fontFamily = NotoSansBengali, color = Color(0xFF4F46E5))
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
private fun SubmitStatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = NotoSansBengali, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontFamily = NotoSansBengali, fontSize = 10.sp, color = color)
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
    onSubmit : (() -> Unit)?,
    studyRevealMode         : Boolean = false,
    onToggleStudyRevealMode : (() -> Unit)? = null,
    studyRecallMode         : Boolean = false,
    onToggleStudyRecallMode : (() -> Unit)? = null
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
            // ── Study: "শুধু প্রশ্ন দেখ" টগল — Quiz/QBank-এ এই বাটন দেখা যায় না,
            //    যেহেতু ওখানে উত্তর এমনিতেই MCQ সিলেক্ট/লেখার আগ পর্যন্ত হাইড থাকে ──
            if (mode == StudyMode.STUDY && onToggleStudyRevealMode != null) {
                IconButton(onClick = onToggleStudyRevealMode) {
                    Icon(
                        if (studyRevealMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (studyRevealMode) "শুধু প্রশ্ন মোড: চালু" else "শুধু প্রশ্ন মোড: বন্ধ",
                        tint = if (studyRevealMode) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // ── ⌨️ রিকল-টাইপিং মোড টগল — চালু করলে উত্তর দেখার আগে টাইপ-বক্সে
            //    নিজে লিখে Enter চাপতে হয়, তারপর ঠিক/ভুল বেছে Enter চাপলেই
            //    পরের প্রশ্নে চলে যায়। বন্ধ থাকলে সবকিছু আগের মতোই। ──
            if (mode == StudyMode.STUDY && onToggleStudyRecallMode != null) {
                IconButton(onClick = onToggleStudyRecallMode) {
                    Icon(
                        Icons.Default.Keyboard,
                        contentDescription = if (studyRecallMode) "টাইপ করে উত্তর মেলানো মোড: চালু" else "টাইপ করে উত্তর মেলানো মোড: বন্ধ",
                        tint = if (studyRecallMode) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
