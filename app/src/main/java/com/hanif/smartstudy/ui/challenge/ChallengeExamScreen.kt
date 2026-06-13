package com.hanif.smartstudy.ui.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.ChallengeUiState
import com.hanif.smartstudy.viewmodel.ChallengeViewModel

@Composable
fun ChallengeExamScreen(state: ChallengeUiState, vm: ChallengeViewModel) {
    var showSubmitDialog by remember { mutableStateOf(false) }

    // Back = warn
    BackHandler { showSubmitDialog = true }

    val questions = state.questions
    val challenge = state.challenge
    val myPhone   = state.myPhone
    val currentQ  = questions.getOrNull(state.currentQIndex)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ──
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4F46E5))))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("⚔️ ${challenge?.subject ?: "চ্যালেঞ্জ"}",
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali)
                        // Live progress — BLIND score, শুধু কে কোথায়
                        val others = challenge?.otherParticipants(myPhone) ?: emptyMap()
                        if (others.isNotEmpty()) {
                            val progressText = others.values.joinToString("  ") { p ->
                                "${p.name.split(" ").first()}: ${p.currentQ + 1}/${questions.size}"
                            }
                            Text(progressText, fontSize = 9.sp,
                                color = Color.White.copy(0.6f), fontFamily = NotoSansBengali)
                        }
                    }
                    // Timer
                    ExamTimer(timerSec = state.timerSec, totalSec = challenge?.timeLimitSec ?: 600)
                }
            }

            // ── Question counter progress ──
            LinearProgressIndicator(
                progress = { (state.currentQIndex + 1).toFloat() / questions.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color    = Color(0xFF10B981),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // ── Question content ──
            if (currentQ != null) {
                LazyColumn(
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ChallengeQuestionCard(
                            index         = state.currentQIndex,
                            total         = questions.size,
                            question      = currentQ,
                            selected      = state.answers[currentQ.id],
                            hiddenOptions = state.hiddenOptions,
                            onSelect      = { opt -> vm.answerQuestion(currentQ.id, opt) }
                        )
                    }
                }
            }

            // ── Lifeline bar ──
            LifelineBar(state = state, vm = vm)

            // ── Bottom nav ──
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Prev
                OutlinedButton(
                    onClick  = { vm.goToQuestion(state.currentQIndex - 1) },
                    enabled  = state.currentQIndex > 0,
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("আগে", fontFamily = NotoSansBengali, fontSize = 12.sp)
                }

                // Question number counter
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.currentQIndex + 1}/${questions.size}",
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                    Text("${state.answers.size} উত্তর", fontSize = 10.sp,
                        color = Color(0xFF10B981), fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold)
                }

                // Next / Submit
                if (state.currentQIndex < questions.size - 1) {
                    Button(
                        onClick  = { vm.goToQuestion(state.currentQIndex + 1) },
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("পরে", fontFamily = NotoSansBengali, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                    }
                } else {
                    Button(
                        onClick  = { showSubmitDialog = true },
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("✅ সাবমিট", fontFamily = NotoSansBengali,
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Submitting overlay
        if (state.isSubmitting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Color(0xFF4F46E5))
                        Text("সাবমিট হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Submit confirmation dialog
    if (showSubmitDialog) {
        val unanswered = questions.size - state.answers.size
        AlertDialog(
            onDismissRequest = { showSubmitDialog = false },
            title = { Text("সাবমিট করবেন?", fontFamily = NotoSansBengali,
                fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubmitStatRow("মোট প্রশ্ন", "${questions.size}টি", Color(0xFF4F46E5))
                    SubmitStatRow("উত্তর দিয়েছেন", "${state.answers.size}টি", Color(0xFF10B981))
                    if (unanswered > 0) {
                        SubmitStatRow("⚠️ উত্তর বাকি", "${unanswered}টি", Color(0xFFEF4444))
                        Text("এখনো $unanswered টি প্রশ্নের উত্তর দেননি!",
                            fontSize = 11.sp, color = Color(0xFFEF4444),
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSubmitDialog = false; vm.submitExam() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape  = RoundedCornerShape(12.dp)) {
                    Text("নিশ্চিত সাবমিট", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
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
}

@Composable
private fun LifelineBar(state: ChallengeUiState, vm: ChallengeViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("🎯 লাইফলাইন:", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
            fontWeight = FontWeight.Bold)

        // 50-50
        LifelineChip(
            label    = "50/50",
            emoji    = "✂️",
            used     = state.usedFiftyFifty,
            onClick  = { vm.useFiftyFifty() }
        )

        // Time Freeze
        LifelineChip(
            label    = if (state.isFreezing) "ফ্রিজ!" else "⏸ ফ্রিজ",
            emoji    = "🧊",
            used     = state.usedTimeFreeze,
            active   = state.isFreezing,
            onClick  = { vm.useTimeFreeze() }
        )

        if (state.isFreezing) {
            Spacer(Modifier.weight(1f))
            Text("⏸ ৩০ সেকেন্ড freeze", fontSize = 9.sp,
                color = Color(0xFF3B82F6), fontFamily = NotoSansBengali,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LifelineChip(
    label  : String,
    emoji  : String,
    used   : Boolean,
    active : Boolean = false,
    onClick: () -> Unit
) {
    val bg    = when {
        active -> Color(0xFF3B82F6)
        used   -> MaterialTheme.colorScheme.surfaceVariant
        else   -> Color(0xFF4F46E5)
    }
    val text  = if (used && !active) Color(0xFF94A3B8) else Color.White
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(enabled = !used) { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text       = if (used && !active) "✓ $label" else "$emoji $label",
            fontSize   = 11.sp,
            color      = text,
            fontWeight = FontWeight.Bold,
            fontFamily = NotoSansBengali
        )
    }
}

@Composable
private fun ExamTimer(timerSec: Int, totalSec: Int) {
    val pct = timerSec.toFloat() / totalSec.coerceAtLeast(1)
    val color = when {
        pct > 0.5f -> Color(0xFF10B981)
        pct > 0.25f -> Color(0xFFF59E0B)
        else        -> Color(0xFFEF4444)
    }
    val min = timerSec / 60
    val sec = timerSec % 60
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%02d:%02d".format(min, sec), fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold, color = color, fontFamily = NotoSansBengali)
        Text("সময়", fontSize = 9.sp, color = Color.White.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

@Composable
private fun ChallengeQuestionCard(
    index         : Int,
    total         : Int,
    question      : QuestionItem,
    selected      : Int?,
    hiddenOptions : Set<Int> = emptySet(),
    onSelect      : (Int) -> Unit
) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White),
        CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("#${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4F46E5))
                }
                Text(question.subTopic, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
            }
            Text(question.question.replace(Regex("<[^>]+>"), ""),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, lineHeight = 20.sp)

            listOf(question.optionA, question.optionB, question.optionC, question.optionD)
                .filterIndexed { i, opt -> opt.isNotBlank() }
                .forEachIndexed { i, opt ->
                    val optNum     = i + 1
                    val isHidden   = optNum in hiddenOptions
                    val isSelected = selected == optNum
                    if (isHidden) {
                        // 50-50 এ ঘোলা করে দেখাও
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Text(listOf("ক","খ","গ","ঘ")[i], fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold, color = Color(0xFFCBD5E1))
                            }
                            Text("— — —", fontSize = 13.sp, color = Color(0xFFCBD5E1),
                                modifier = Modifier.weight(1f))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, if (isSelected) Color(0xFF4F46E5) else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { onSelect(optNum) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape)
                                .background(if (isSelected) Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Text(listOf("ক","খ","গ","ঘ")[i], fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(opt, fontSize = 13.sp, fontFamily = NotoSansBengali,
                                color = if (isSelected) Color(0xFF1E1B4B) else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
        }
    }
}

@Composable
private fun SubmitStatRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.1f))
            .padding(horizontal = 10.dp, vertical = 3.dp)) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = color, fontFamily = NotoSansBengali)
        }
    }
}
