package com.hanif.smartstudy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.AnswerState
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.model.RoutineItem
import com.hanif.smartstudy.data.model.StudyItem
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.util.SoundManager
import com.hanif.smartstudy.viewmodel.QuizViewModel
import com.hanif.smartstudy.viewmodel.RoutineViewModel

// HomeScreen.kt তে এই রঙগুলো private val হিসেবে আছে (file-scoped, import করা যায় না),
// তাই এখানে একই value দিয়ে local copy রাখা হলো যাতে UI consistent থাকে।
private val PrimaryIndigo = Color(0xFF4F46E5)
private val GreenMint     = Color(0xFF10B981)

// মিনি-কুইজে সর্বোচ্চ কতগুলো প্রশ্ন দেখানো হবে — বেশি হলে ক্লান্তিকর, ছোট/দ্রুত
// "comprehension check" হিসেবে রাখা হলো।
private const val MAX_MINI_QUIZ_QUESTIONS = 8
private const val PASS_THRESHOLD_PCT = 70

/**
 * ════════════════════════════════════════════════════════════════
 *  RoutineFocusSheet
 * ════════════════════════════════════════════════════════════════
 * Routine এর একটা item tap করলে এই bottom sheet খোলে। দুটো অংশ:
 * - Study content — সেই subject/sub_topic এর পড়া এখানেই দেখা যায়।
 * - Quiz (যদি থাকে) — সরাসরি এই sheet এর ভেতরেই ছোট quiz নেওয়া যায়;
 *    ৭০%+ স্কোর করলে routine item স্বয়ংক্রিয়ভাবে "done" হয়ে যায়, আলাদা
 *    করে "পড়া শেষ করেছি" বাটনে চাপতে হয় না।
 *
 * এই mini-quiz সম্পূর্ণ self-contained — শেয়ার্ড QuizViewModel এর মূল
 * state (_state, mock test ইত্যাদি) স্পর্শ করে না, তাই মূল Quiz/QBank ট্যাবে
 * চলমান কোনো সেশনের সাথে এর কোনো সংঘর্ষ হয় না।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFocusSheet(
    item: RoutineItem,
    onDismiss: () -> Unit,
    routineVm: RoutineViewModel = viewModel(),
    quizVm: QuizViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ctx = LocalContext.current

    // ── Routine state থেকে live item — markDone হওয়ার পর sheet নিজে থেকেই reflect করবে ──
    val routineState by routineVm.state.collectAsState()
    val liveItem = routineState.items.find { it.id == item.id } ?: item

    // ── এই routine item এর subject/sub_topic এর সাথে মিলে যাওয়া study content ──
    val matchedStudy = remember(item.id) {
        val all = quizVm.getStudyContentSnapshot()
        all.filter { s ->
            val subjMatch = s.subject?.trim()?.equals(item.subject.trim(), ignoreCase = true) == true
            val topicMatch = item.subTopic.isBlank() ||
                s.subTopic?.trim()?.equals(item.subTopic.trim(), ignoreCase = true) == true
            subjMatch && topicMatch
        }
    }

    // ── একই বিষয়/উপবিষয়ের সাথে মিলে যাওয়া quiz প্রশ্ন (থাকলে) ──
    val matchedQuiz = remember(item.id) {
        val all = quizVm.getQuizContentSnapshot()
        all.filter { q ->
            val subjMatch = q.subject?.trim()?.equals(item.subject.trim(), ignoreCase = true) == true
            val topicMatch = item.subTopic.isBlank() ||
                q.subTopic?.trim()?.equals(item.subTopic.trim(), ignoreCase = true) == true
            subjMatch && topicMatch
        }.shuffled().take(MAX_MINI_QUIZ_QUESTIONS).map { QuestionItem.fromQuizItem(it) }
    }

    // sheet এর mode: "study" (default), "quiz" (প্রশ্ন চলছে), "result" (স্কোর দেখানো)
    var sheetMode by remember(item.id) { mutableStateOf("study") }
    var quizQuestions by remember(item.id) { mutableStateOf(matchedQuiz) }
    var qIndex by remember(item.id) { mutableStateOf(0) }
    var correctCount by remember(item.id) { mutableStateOf(0) }

    fun startQuiz() {
        quizQuestions = matchedQuiz.map { it.copy(answerState = AnswerState.Unanswered) }
        qIndex = 0
        correctCount = 0
        sheetMode = "quiz"
    }

    fun finishQuiz() {
        val pct = if (quizQuestions.isEmpty()) 0 else (correctCount * 100) / quizQuestions.size
        if (pct >= PASS_THRESHOLD_PCT) {
            routineVm.markDone(item.id)
        }
        sheetMode = "result"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp).width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE2E8F0))
            )
        }
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 640.dp)) {

            // ── হেডার ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.subject, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, color = Slate800)
                    if (item.subTopic.isNotBlank()) {
                        Text(item.subTopic, fontSize = 13.sp, fontFamily = NotoSansBengali, color = Color.Gray)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }

            when (sheetMode) {
                "quiz" -> MiniQuizSection(
                    questions = quizQuestions,
                    qIndex = qIndex,
                    onAnswer = { optionIdx, isCorrect ->
                        val updated = quizQuestions.toMutableList()
                        updated[qIndex] = updated[qIndex].copy(answerState = AnswerState.McqSelected(optionIdx, isCorrect))
                        quizQuestions = updated
                        if (isCorrect) {
                            correctCount++
                            SoundManager.playCorrect()
                            SoundManager.vibrateCorrect(ctx)
                        } else {
                            SoundManager.playWrong()
                            SoundManager.vibrateWrong(ctx)
                        }
                        quizQuestions[qIndex].id.takeIf { it.isNotBlank() }?.let {
                            quizVm.logRoutineQuizAnswer(it, isCorrect)
                        }
                    },
                    onNext = {
                        if (qIndex < quizQuestions.size - 1) qIndex++ else finishQuiz()
                    }
                )
                "result" -> MiniQuizResult(
                    correct = correctCount,
                    total = quizQuestions.size,
                    passed = quizQuestions.isNotEmpty() && (correctCount * 100 / quizQuestions.size) >= PASS_THRESHOLD_PCT,
                    onRetry = { startQuiz() },
                    onClose = onDismiss,
                    onBackToStudy = { sheetMode = "study" }
                )
                else -> StudyModeContent(
                    item = liveItem,
                    matchedStudy = matchedStudy,
                    hasQuiz = matchedQuiz.isNotEmpty(),
                    onStartQuiz = { startQuiz() },
                    routineVm = routineVm,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.StudyModeContent(
    item: RoutineItem,
    matchedStudy: List<StudyItem>,
    hasQuiz: Boolean,
    onStartQuiz: () -> Unit,
    routineVm: RoutineViewModel,
    onDismiss: () -> Unit
) {
    if (matchedStudy.isEmpty() && !hasQuiz) {
        // ── কোনো ম্যাচিং কন্টেন্ট পাওয়া যায়নি ──
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📭", fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "এই বিষয়ে কোনো পড়া এখনো যুক্ত হয়নি",
                fontSize = 14.sp, fontFamily = NotoSansBengali, color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "নিজে পড়াশোনা শেষ করে নিচের বাটনে ট্যাপ করো",
                fontSize = 12.sp, fontFamily = NotoSansBengali, color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            ManualDoneButton(item, routineVm, onDismiss)
        }
        return
    }

    // ── Info row ──
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.MenuBook, null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
        Text(
            if (matchedStudy.isNotEmpty()) "${matchedStudy.size}টি পড়া পাওয়া গেছে" else "এই বিষয়ে পড়া নেই, শুধু কুইজ আছে",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray, fontFamily = NotoSansBengali
        )
    }

    if (matchedStudy.isNotEmpty()) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(matchedStudy, key = { it.id ?: it.hashCode().toString() }) { study ->
                StudyContentCard(study)
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    } else {
        Spacer(Modifier.weight(1f, fill = false).heightIn(min = 20.dp))
    }

    Divider(color = Color(0xFFF1F5F9))

    // ── নিচে: quiz বাটন (থাকলে) + done বাটন ──
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.done) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(GreenMint.copy(alpha = 0.12f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = GreenMint)
                Text("আজকের জন্য সম্পন্ন ✅", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenMint, fontFamily = NotoSansBengali)
            }
        } else {
            if (hasQuiz) {
                Button(
                    onClick = onStartQuiz,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) {
                    Icon(Icons.Default.Quiz, null, tint = White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("কুইজ দাও — পাশ করলে স্বয়ংক্রিয় সম্পন্ন", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = White, fontFamily = NotoSansBengali)
                }
            }
            ManualDoneButton(item, routineVm, onDismiss, label = if (hasQuiz) "নিজে নিশ্চিত করে সম্পন্ন করো" else "পড়া শেষ করেছি")
        }
    }
}

@Composable
private fun MiniQuizSection(
    questions: List<QuestionItem>,
    qIndex: Int,
    onAnswer: (optionIdx: Int, isCorrect: Boolean) -> Unit,
    onNext: () -> Unit
) {
    if (questions.isEmpty()) return
    val q = questions[qIndex]
    val answered = q.answerState as? AnswerState.McqSelected

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        // প্রগ্রেস
        LinearProgressIndicator(
            progress = { (qIndex + 1).toFloat() / questions.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = PrimaryIndigo, trackColor = Color(0xFFF1F5F9)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "প্রশ্ন ${qIndex + 1}/${questions.size}",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, fontFamily = NotoSansBengali
        )
        Spacer(Modifier.height(10.dp))
        Text(
            q.question, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Slate800, fontFamily = NotoSansBengali
        )
        Spacer(Modifier.height(14.dp))

        val options = listOf(1 to q.optionA, 2 to q.optionB, 3 to q.optionC, 4 to q.optionD)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (idx, text) ->
                if (text.isBlank()) return@forEach
                val isSelected = answered?.option == idx
                val isCorrectOption = text.trim().equals(q.answer.trim(), ignoreCase = true)
                val bg = when {
                    answered == null -> Color(0xFFF8FAFC)
                    isCorrectOption -> GreenMint.copy(alpha = 0.15f)
                    isSelected && !isCorrectOption -> Red500.copy(alpha = 0.12f)
                    else -> Color(0xFFF8FAFC)
                }
                val borderColor = when {
                    answered == null -> Color(0xFFE2E8F0)
                    isCorrectOption -> GreenMint
                    isSelected && !isCorrectOption -> Red500
                    else -> Color(0xFFE2E8F0)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            enabled = answered == null
                        ) { onAnswer(idx, isCorrectOption) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ('A' + (idx - 1)).toString(),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = borderColor
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(text, fontSize = 14.sp, fontFamily = NotoSansBengali, color = Slate800, modifier = Modifier.weight(1f))
                }
            }
        }

        if (answered != null) {
            Spacer(Modifier.height(8.dp))
            if (!q.explanation.isBlank() && q.explanation != q.answer) {
                Text(
                    "💡 " + q.explanation,
                    fontSize = 12.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali
                )
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Text(
                    if (qIndex < questions.size - 1) "পরের প্রশ্ন →" else "ফলাফল দেখো",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White, fontFamily = NotoSansBengali
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MiniQuizResult(
    correct: Int,
    total: Int,
    passed: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onBackToStudy: () -> Unit
) {
    val pct = if (total == 0) 0 else (correct * 100) / total
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (passed) "🎉" else "📚", fontSize = 48.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (passed) "চমৎকার! সম্পন্ন হয়েছে" else "আরেকটু চেষ্টা দরকার",
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
            color = if (passed) GreenMint else Slate800
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "$correct/$total সঠিক ($pct%)",
            fontSize = 14.sp, color = Color.Gray, fontFamily = NotoSansBengali
        )
        if (passed) {
            Spacer(Modifier.height(4.dp))
            Text(
                "রুটিন আইটেম স্বয়ংক্রিয়ভাবে সম্পন্ন হিসেবে চিহ্নিত হয়েছে ✅",
                fontSize = 12.sp, color = GreenMint, fontFamily = NotoSansBengali, textAlign = TextAlign.Center
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                "কমপক্ষে ${PASS_THRESHOLD_PCT}% পেতে হবে — আবার পড়ে চেষ্টা করো",
                fontSize = 12.sp, color = Color.Gray, fontFamily = NotoSansBengali, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))

        if (passed) {
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenMint)
            ) {
                Text("ঠিক আছে", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = White, fontFamily = NotoSansBengali)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onBackToStudy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("আবার পড়ো", fontSize = 13.sp, fontFamily = NotoSansBengali)
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) {
                    Text("আবার দাও", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = White, fontFamily = NotoSansBengali)
                }
            }
        }
    }
}

@Composable
private fun ManualDoneButton(
    item: RoutineItem,
    routineVm: RoutineViewModel,
    onDismiss: () -> Unit,
    label: String = "পড়া শেষ করেছি"
) {
    Button(
        onClick = {
            routineVm.markDone(item.id)
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GreenMint)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = White, fontFamily = NotoSansBengali)
    }
}

@Composable
private fun StudyContentCard(study: StudyItem) {
    var expanded by remember { mutableStateOf(false) }
    val cardKey = study.id ?: study.hashCode().toString()
    val speakingKey by com.hanif.smartstudy.util.TtsManager.speakingKey.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FAFC))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.MenuBook, null, tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
            Text(
                study.question ?: "",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate800,
                fontFamily = NotoSansBengali,
                modifier = Modifier.weight(1f)
            )
            // 🔊 প্রশ্ন শুনো
            val questionText = study.question
            if (!questionText.isNullOrBlank()) {
                val qKey = "${cardKey}_q"
                val isSpeaking = speakingKey == qKey
                IconButton(
                    onClick = { com.hanif.smartstudy.util.TtsManager.speak(questionText, qKey) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = if (isSpeaking) "থামাও" else "প্রশ্ন শুনো",
                        tint = if (isSpeaking) PrimaryIndigo else Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        val answerText = study.answer ?: study.correct
        if (!answerText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    answerText,
                    fontSize = 13.sp, color = Color(0xFF475569), fontFamily = NotoSansBengali,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    modifier = Modifier.weight(1f)
                )
                // 🔊 উত্তর শুনো
                val aKey = "${cardKey}_a"
                val isAnswerSpeaking = speakingKey == aKey
                IconButton(
                    onClick = { com.hanif.smartstudy.util.TtsManager.speak(answerText, aKey) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (isAnswerSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = if (isAnswerSpeaking) "থামাও" else "উত্তর শুনো",
                        tint = if (isAnswerSpeaking) PrimaryIndigo else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (!study.explanation.isNullOrBlank() && expanded) {
            Spacer(Modifier.height(8.dp))
            Divider(color = Color(0xFFE2E8F0))
            Spacer(Modifier.height(8.dp))
            Text(
                "💡 " + study.explanation,
                fontSize = 12.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali
            )
        }
        val canExpand = (study.explanation?.isNotBlank() == true) || (answerText?.length ?: 0) > 140
        if (canExpand) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (expanded) "সংক্ষেপে দেখাও ▲" else "বিস্তারিত দেখাও ▼",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo, fontFamily = NotoSansBengali,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = !expanded }
            )
        }
    }
}
