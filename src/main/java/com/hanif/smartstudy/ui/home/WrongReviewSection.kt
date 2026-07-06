package com.hanif.smartstudy.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.shared.*
import com.hanif.smartstudy.ui.theme.LocalDarkMode
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.delay

private val WR_RedMain   = Color(0xFFDC2626)
private val WR_RedLight  = Color(0xFFFFF1F2)   // original light pink (light mode)
private val WR_RedBorder = Color(0xFFFECACA)   // original light pink border (light mode)

@Composable
fun WrongReviewSection(
    wrongItems      : List<Pair<QuestionItem, Int>>,
    onAnswerMcq     : (questionId: String, selectedOption: Int) -> Unit,
    onAnswerWritten : (questionId: String, userText: String) -> Int,
    onRemoveCorrect : (questionId: String) -> Unit
) {
    var practiceMode  by remember { mutableStateOf(false) }
    var showCongrats  by remember { mutableStateOf(false) }

    // local answer states
    val localAnswers = remember { mutableStateMapOf<String, AnswerState>() }

    // practice শুরুর সময় যতগুলো ছিল সেটা মনে রাখি
    var totalAtStart by remember { mutableStateOf(0) }

    // সঠিক হয়ে যাওয়া ids (hide tracking)
    val correctIds = remember { mutableStateListOf<String>() }

    // wrongItems বাইরে থেকে আসে — practice চলাকালে snapshot নিই
    val practiceSnapshot = remember { mutableStateListOf<Pair<QuestionItem, Int>>() }

    // active = snapshot থেকে যেগুলো এখনো correct হয়নি
    val activeItems = practiceSnapshot.filter { it.first.id !in correctIds }

    // সব সঠিক check
    LaunchedEffect(correctIds.size, totalAtStart) {
        if (practiceMode && totalAtStart > 0 && correctIds.size >= totalAtStart) {
            showCongrats = true
            delay(3000)
            showCongrats = false
            practiceMode = false
            correctIds.clear()
            localAnswers.clear()
            practiceSnapshot.clear()
            totalAtStart = 0
        }
    }

    // congratulations দেখালে শুধু ওটাই
    if (showCongrats) {
        CongratsOverlay()
        return
    }

    // ভুল প্রশ্ন নেই — section দেখাবো না
    if (wrongItems.isEmpty() && !practiceMode) return

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border    = BorderStroke(1.5.dp, WR_RedMain.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            WRHeader(
                totalCount   = if (practiceMode) totalAtStart else wrongItems.size,
                activeCount  = activeItems.size,
                practiceMode = practiceMode
            )

            if (!practiceMode) {
                // Preview: সর্বশেষ ৩টা
                wrongItems.take(3).forEach { (q, count) ->
                    WRPreviewItem(q = q, wrongCount = count)
                }
                if (wrongItems.size > 3) {
                    Text(
                        "... আরো ${wrongItems.size - 3}টি ভুল প্রশ্ন আছে",
                        fontSize   = 11.sp,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali,
                        modifier   = Modifier.padding(start = 4.dp)
                    )
                }

                // অনুশীলন বাটন
                Button(
                    onClick = {
                        // snapshot নাও এবং practice শুরু করো
                        practiceSnapshot.clear()
                        practiceSnapshot.addAll(wrongItems)
                        correctIds.clear()
                        localAnswers.clear()
                        totalAtStart = wrongItems.size
                        practiceMode = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = WR_RedMain)
                ) {
                    Text("🎯 ", fontSize = 16.sp)
                    Text(
                        "সব ${wrongItems.size}টি প্রশ্ন অনুশীলন করুন",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White,
                        fontFamily = NotoSansBengali
                    )
                }

            } else {
                // Practice mode — active প্রশ্নগুলো দেখাও
                activeItems.forEach { (q, wrongCount) ->
                    val localState = localAnswers[q.id] ?: AnswerState.Unanswered
                    WRPracticeItem(
                        q               = q.copy(answerState = localState),
                        wrongCount      = wrongCount,
                        onMcqAnswer     = { selectedOpt ->
                            val optText = when (selectedOpt) {
                                1 -> q.optionA; 2 -> q.optionB
                                3 -> q.optionC; 4 -> q.optionD; else -> ""
                            }
                            val isCorrect = optText.trim().equals(q.answer.trim(), ignoreCase = true)
                            localAnswers[q.id] = AnswerState.McqSelected(selectedOpt, isCorrect)
                            onAnswerMcq(q.id, selectedOpt)
                            if (isCorrect) {
                                onRemoveCorrect(q.id)
                            }
                        },
                        onWrittenAnswer = { text ->
                            val pct = onAnswerWritten(q.id, text)
                            val isCorrect = pct >= 70
                            localAnswers[q.id] = AnswerState.WrittenSubmitted(text, pct, isCorrect)
                            if (isCorrect) onRemoveCorrect(q.id)
                            pct
                        },
                        onHide = {
                            if (q.id !in correctIds) {
                                correctIds.add(q.id)
                            }
                        }
                    )
                }

                // বন্ধ করুন বাটন
                OutlinedButton(
                    onClick = {
                        practiceMode = false
                        correctIds.clear()
                        localAnswers.clear()
                        practiceSnapshot.clear()
                        totalAtStart = 0
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        "✖ অনুশীলন বন্ধ করুন",
                        fontSize   = 13.sp,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali
                    )
                }
            }
        }
    }
}

// ─── Header ───
@Composable
private fun WRHeader(totalCount: Int, activeCount: Int, practiceMode: Boolean) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFFF97316)))
                    ),
                contentAlignment = Alignment.Center
            ) { Text("🔁", fontSize = 20.sp) }
            Column {
                Text(
                    "ভুল প্রশ্ন Review",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = WR_RedMain,
                    fontFamily = NotoSansBengali
                )
                Text(
                    if (practiceMode) "অনুশীলন চলছে..." else "সর্বশেষ ৩টি দেখানো হচ্ছে",
                    fontSize   = 10.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali
                )
            }
        }
        val isDark = LocalDarkMode.current.value
        val wrRedLight  = if (isDark) Color(0xFF3D1010) else WR_RedLight
        val wrRedBorder = if (isDark) Color(0xFF7F1D1D) else WR_RedBorder
        Box(
            modifier         = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(wrRedLight)
                .border(1.dp, wrRedBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (practiceMode) "$activeCount" else "$totalCount",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = WR_RedMain,
                    fontFamily = NotoSansBengali
                )
                Text(
                    "ভুল",
                    fontSize   = 9.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NotoSansBengali
                )
            }
        }
    }
}

// ─── Preview Item ───
@Composable
private fun WRPreviewItem(q: QuestionItem, wrongCount: Int) {
    val badgeColor = when {
        wrongCount >= 3 -> WR_RedMain
        wrongCount == 2 -> Color(0xFFF97316)
        else            -> Color(0xFFF59E0B)
    }
    val isDark = LocalDarkMode.current.value
    val wrRedLight  = if (isDark) Color(0xFF3D1010) else WR_RedLight
    val wrRedBorder = if (isDark) Color(0xFF7F1D1D) else WR_RedBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(wrRedLight)
            .border(1.dp, wrRedBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (q.isMcq()) "🔘" else "✍️", fontSize = 16.sp)
        Column(Modifier.weight(1f)) {
            val displayQ = q.question.replace(Regex("<[^>]+>"), "")
            Text(
                if (displayQ.length > 60) displayQ.take(60) + "…" else displayQ,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                fontFamily = NotoSansBengali,
                lineHeight = 16.sp
            )
            if (q.subTopic.isNotBlank()) {
                Text(
                    q.subTopic.take(25),
                    fontSize   = 9.sp,
                    color      = Color(0xFF0284C7),
                    fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(top = 2.dp)
                )
            }
        }
        Box(
            modifier         = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("×$wrongCount", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ─── Practice Item ───
@Composable
private fun WRPracticeItem(
    q               : QuestionItem,
    wrongCount      : Int,
    onMcqAnswer     : (Int) -> Unit,
    onWrittenAnswer : (String) -> Int,
    onHide          : () -> Unit
) {
    val isAnswered = q.answerState !is AnswerState.Unanswered
    val isCorrect  = when (val s = q.answerState) {
        is AnswerState.McqSelected      -> s.isCorrect
        is AnswerState.WrittenSubmitted -> s.isCorrect
        else                            -> false
    }

    var visible by remember { mutableStateOf(true) }

    // সঠিক হলে ১.২s পর original app এর মতো collapse animation দিয়ে hide
    LaunchedEffect(isCorrect) {
        if (isCorrect && visible) {
            delay(1200L)   // original: 1200ms
            visible = false
            delay(520L)    // original: 520ms transition সম্পন্ন হওয়ার পর
            onHide()
        }
    }

    // Original app: opacity + maxHeight collapse
    // Compose এ: AnimatedVisibility দিয়ে fadeOut + shrinkVertically
    AnimatedVisibility(
        visible = visible,
        exit    = fadeOut(animationSpec = tween(400, easing = LinearEasing)) +
                  shrinkVertically(
                      animationSpec = tween(520, easing = FastOutSlowInEasing),
                      shrinkTowards = Alignment.Top
                  )
    ) {
        val badgeColor = when {
            wrongCount >= 3 -> WR_RedMain
            wrongCount == 2 -> Color(0xFFF97316)
            else            -> Color(0xFFF59E0B)
        }

        val isDark = LocalDarkMode.current.value
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(
                containerColor = when {
                    isCorrect  -> if (isDark) Color(0xFF052E16) else Color(0xFFF0FDF4)
                    isAnswered -> if (isDark) Color(0xFF3D1010) else Color(0xFFFFF1F2)
                    else       -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border    = BorderStroke(
                1.5.dp,
                when {
                    isCorrect  -> Color(0xFF22C55E)
                    isAnswered -> WR_RedMain.copy(alpha = 0.4f)
                    else       -> Color(0xFF94A3B8)
                }
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier            = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top badges row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "×$wrongCount ভুল",
                                fontSize   = 9.sp,
                                color      = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = NotoSansBengali
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (q.isMcq()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                                .border(
                                    1.dp,
                                    if (q.isMcq()) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                if (q.isMcq()) "MCQ" else "Written",
                                fontSize   = 9.sp,
                                color      = if (q.isMcq()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (q.subTopic.isNotBlank()) {
                            Text(
                                q.subTopic.take(20),
                                fontSize   = 9.sp,
                                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali
                            )
                        }
                    }

                }

                QuestionText(text = q.question)

                if (q.imageUrl.isNotBlank()) {
                    val imgUrls = q.imageUrl.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    imgUrls.forEach { ZoomableImage(url = it) }
                }

                if (q.isMcq()) {
                    McqOptions(item = q, onAnswer = onMcqAnswer)
                } else {
                    WrittenInput(item = q, onSubmit = onWrittenAnswer)
                }

                if (isAnswered) {
                    if (q.answer.isNotBlank()) AnswerBox(text = q.answer)
                    if (q.explanation.isNotBlank()) {
                        ExplanationBox(text = q.explanation)
                    }
                    if (q.technique.isNotBlank()) TechniqueBox(text = q.technique)
                }

                // সঠিক banner
                if (isCorrect) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF052E16) else Color(0xFFDCFCE7))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✅", fontSize = 18.sp)
                        Text(
                            "সঠিক উত্তর দিয়েছেন!",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color(0xFF166534),
                            fontFamily = NotoSansBengali
                        )
                    }
                }
            }
        }
    }
}

// ─── Congratulations ───
@Composable
private fun CongratsOverlay() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue  = 0.97f,
        targetValue   = 1.03f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "scale"
    )
    val starAlpha by pulse.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label         = "alpha"
    )

    Card(
        modifier  = Modifier.fillMaxWidth().scale(scale),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("🏆", fontSize = 60.sp)
            Text(
                "অভিনন্দন!",
                fontSize   = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color(0xFFFBBF24),
                fontFamily = NotoSansBengali,
                textAlign  = TextAlign.Center
            )
            Text(
                "আপনি সব ভুল প্রশ্ন\nঅনুশীলনের মাধ্যমে সঠিক করেছেন! 🎉",
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White.copy(alpha = 0.9f),
                fontFamily = NotoSansBengali,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.alpha(starAlpha)
            ) {
                listOf("⭐", "🌟", "✨", "🌟", "⭐").forEach {
                    Text(it, fontSize = 22.sp)
                }
            }
            Text(
                "কিছুক্ষণ পর এই সেকশন বন্ধ হয়ে যাবে...",
                fontSize   = 10.sp,
                color      = Color.White.copy(alpha = 0.4f),
                fontFamily = NotoSansBengali,
                textAlign  = TextAlign.Center
            )
        }
    }
}
