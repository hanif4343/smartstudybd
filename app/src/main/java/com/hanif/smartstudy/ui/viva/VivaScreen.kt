package com.hanif.smartstudy.ui.viva

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.ui.shared.Indigo600
import com.hanif.smartstudy.ui.shared.RedWrong
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.VivaLogEntry
import com.hanif.smartstudy.viewmodel.VivaStage
import com.hanif.smartstudy.viewmodel.VivaUiState
import com.hanif.smartstudy.viewmodel.VivaViewModel

private val GreenCorrect = Color(0xFF16A34A)
private val OrangePartial = Color(0xFFF59E0B)

/**
 * ══════════════════════════════════════════════════════════════════
 *  VivaScreen — Home থেকে "🎙️ Viva Mode" চাপলে খোলে। AI ছাত্রের নাম ধরে
 *  জিজ্ঞেস করে বিষয়/টপিক, তারপর Quiz থেকে random প্রশ্ন নিয়ে ভয়েসে
 *  কথোপকথন — QuestionVoiceAiSheet-এ যেসব বাগ ফিক্স করা হয়েছিল (TTS key,
 *  race condition) সেই একই প্যাটার্ন ViewModel-এ বিল্ট-ইন।
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun VivaScreen(
    onBack: () -> Unit,
    vm: VivaViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.start() }
    BackHandler { vm.close(); onBack() }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) vm.onVoiceInput(spoken)
        }
    }
    fun launchMic() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "ভয়েস ইনপুট চালু করা যায়নি — ফোনে Google অ্যাপ/ভয়েস সার্ভিস আছে কিনা দেখো", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // ── Header ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Indigo600, Color(0xFF7C3AED))))
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.close(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যাও", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("🎙️ Viva Mode", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali)
                    if (state.subject.isNotBlank()) {
                        Text(
                            if (state.subTopic.isNotBlank()) "${state.subject} — ${state.subTopic}" else state.subject,
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontFamily = NotoSansBengali
                        )
                    }
                }
            }

            when (state.stage) {
                VivaStage.ENDED -> VivaSummaryContent(state, onRestart = { vm.start() }, onHome = { vm.close(); onBack() })
                else -> VivaSessionContent(
                    state = state,
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onMic = { launchMic() },
                    onSend = { vm.onVoiceInput(inputText); inputText = "" },
                    onChangeSubject = vm::changeSubject,
                    onChangeTopic = vm::changeTopic,
                    onSkip = vm::skipQuestion,
                    onEnd = vm::endSession,
                    onClearError = vm::clearError
                )
            }
        }
    }
}

@Composable
private fun VivaSessionContent(
    state           : VivaUiState,
    inputText       : String,
    onInputChange   : (String) -> Unit,
    onMic           : () -> Unit,
    onSend          : () -> Unit,
    onChangeSubject : () -> Unit,
    onChangeTopic   : () -> Unit,
    onSkip          : () -> Unit,
    onEnd           : () -> Unit,
    onClearError    : () -> Unit
) {
    Column(Modifier.fillMaxSize()) {

        if (!state.hasAnyKey) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp))
                    .background(OrangePartial.copy(alpha = 0.12f)).padding(12.dp)
            ) {
                Text(
                    "⚠️ Viva চালাতে Menu → Settings-এ গিয়ে অন্তত একটা AI API key (Groq/Mistral/Cerebras/Gemini) সেভ করে নাও।",
                    fontSize = 12.sp, fontFamily = NotoSansBengali, lineHeight = 17.sp, color = Color(0xFF92400E)
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.stage) {
                VivaStage.ASK_SUBJECT, VivaStage.ASK_SUBTOPIC -> {
                    Spacer(Modifier.height(30.dp))
                    Text("🎙️", fontSize = 44.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.promptText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        fontFamily = NotoSansBengali, textAlign = TextAlign.Center, lineHeight = 22.sp
                    )
                }

                VivaStage.ASKING, VivaStage.GRADING -> {
                    state.currentQuestion?.let { q ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Indigo600.copy(alpha = 0.08f)).padding(14.dp)
                        ) {
                            Text(
                                q.question.ifBlank { q.explanation }, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                fontFamily = NotoSansBengali, lineHeight = 20.sp
                            )
                            if (q.isMcq()) {
                                Spacer(Modifier.height(6.dp))
                                listOf("ক" to q.optionA, "খ" to q.optionB, "গ" to q.optionC, "ঘ" to q.optionD).forEach { (label, opt) ->
                                    if (opt.isNotBlank()) {
                                        Text("$label) $opt", fontSize = 13.sp, fontFamily = NotoSansBengali,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ── ফিডব্যাক ব্যাজ (verdict দেওয়ার পর, পরের প্রশ্নে যাওয়ার আগে) ──
                    state.lastVerdict?.let { verdict ->
                        val (badgeColor, badgeLabel) = when (verdict) {
                            "CORRECT" -> GreenCorrect to "✅ সঠিক!"
                            "PARTIAL" -> OrangePartial to "🟡 আংশিক সঠিক"
                            else      -> RedWrong to "❌ ভুল হয়েছে"
                        }
                        Spacer(Modifier.height(14.dp))
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(badgeColor.copy(alpha = 0.10f)).padding(12.dp)
                        ) {
                            Text(badgeLabel, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                fontFamily = NotoSansBengali, color = badgeColor)
                            state.lastFeedback?.let {
                                Text(it, fontSize = 13.sp, fontFamily = NotoSansBengali,
                                    color = badgeColor, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    if (state.isBusy && state.lastVerdict == null) {
                        Spacer(Modifier.height(20.dp))
                        CircularProgressIndicator(color = Indigo600, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.stage == VivaStage.GRADING) "রায় দেওয়া হচ্ছে…" else "প্রশ্ন আনা হচ্ছে…",
                            fontSize = 12.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {}
            }
        }

        state.error?.let { err ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(RedWrong.copy(alpha = 0.10f)).padding(10.dp)
            ) {
                Text(err, fontSize = 12.sp, fontFamily = NotoSansBengali, color = RedWrong, modifier = Modifier.weight(1f))
                Text("✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RedWrong,
                    modifier = Modifier.clickable { onClearError() })
            }
        }

        // ── বিষয়/টপিক পরিবর্তন + পাস + সমাপ্ত — শুধু প্রশ্ন-পর্বে দেখা যাবে ──
        if (state.stage == VivaStage.ASKING || state.stage == VivaStage.GRADING) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onChangeSubject, shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("বিষয় পরিবর্তন করুন", fontSize = 11.sp, fontFamily = NotoSansBengali)
                }
                OutlinedButton(onClick = onChangeTopic, shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("টপিক পরিবর্তন করুন", fontSize = 11.sp, fontFamily = NotoSansBengali)
                }
                OutlinedButton(onClick = onSkip, shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("⏭️ পাস", fontSize = 11.sp, fontFamily = NotoSansBengali)
                }
                OutlinedButton(
                    onClick = onEnd, shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedWrong),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("🏁 সমাপ্ত করো", fontSize = 11.sp, fontFamily = NotoSansBengali)
                }
            }
        }

        // ── মাইক + টাইপ ফলব্যাক ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onMic,
                modifier = Modifier.clip(CircleShape).background(RedWrong),
                enabled = !state.isBusy
            ) {
                Icon(Icons.Default.Mic, contentDescription = "মাইক", tint = Color.White)
            }
            OutlinedTextField(
                value = inputText, onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("অথবা টাইপ করো...", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                minLines = 1, maxLines = 3, shape = RoundedCornerShape(20.dp),
                enabled = !state.isBusy,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600, unfocusedBorderColor = Color(0xFFE2E8F0))
            )
            val canSend = inputText.isNotBlank() && !state.isBusy
            IconButton(
                onClick = onSend,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (canSend) Indigo600 else Color(0xFFCBD5E1))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "পাঠাও", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VivaSummaryContent(
    state    : VivaUiState,
    onRestart: () -> Unit,
    onHome   : () -> Unit
) {
    val total = state.correctCount + state.wrongCount + state.skippedCount
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Indigo600, Color(0xFF7C3AED))))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉 সেশন শেষ!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = NotoSansBengali)
            Spacer(Modifier.height(10.dp))
            Text("${state.correctCount} / $total সঠিক", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, fontFamily = NotoSansBengali)
            if (state.xpEarned > 0) {
                Spacer(Modifier.height(6.dp))
                Text("⭐ +${state.xpEarned} XP অর্জিত হয়েছে", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, fontFamily = NotoSansBengali)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("প্রশ্নভিত্তিক ফলাফল:", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
        Spacer(Modifier.height(8.dp))

        state.log.forEachIndexed { idx, entry -> VivaLogRow(idx + 1, entry) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRestart, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
        ) {
            Text("🔁 আবার শুরু করি", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
            Text("🏠 Home এ ফিরে যাও", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun VivaLogRow(number: Int, entry: VivaLogEntry) {
    val (badgeColor, badgeText) = when (entry.verdict) {
        "CORRECT" -> GreenCorrect to "✅"
        "PARTIAL" -> OrangePartial to "🟡"
        "SKIPPED" -> Color(0xFF64748B) to "⏭️"
        else      -> RedWrong to "❌"
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC)).padding(12.dp)
    ) {
        Row {
            Text("$badgeText $number. ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = badgeColor)
            Text(entry.question, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali, lineHeight = 18.sp)
        }
        if (entry.studentAnswer.isNotBlank()) {
            Text("তোমার উত্তর: ${entry.studentAnswer}", fontSize = 12.sp, fontFamily = NotoSansBengali,
                color = Color(0xFF475569), modifier = Modifier.padding(top = 4.dp))
        }
        if (entry.verdict != "CORRECT") {
            Text("সঠিক উত্তর: ${entry.correctAnswer}", fontSize = 12.sp, fontFamily = NotoSansBengali,
                color = GreenCorrect, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
