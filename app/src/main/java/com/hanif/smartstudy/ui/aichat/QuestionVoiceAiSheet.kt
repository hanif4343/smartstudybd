package com.hanif.smartstudy.ui.aichat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeOff
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.AiChatMessage
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.model.StudyMode
import com.hanif.smartstudy.ui.shared.Indigo600
import com.hanif.smartstudy.ui.shared.RedWrong
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.TtsManager
import com.hanif.smartstudy.viewmodel.QuestionVoiceAiViewModel
import java.util.Locale

/**
 * ══════════════════════════════════════════════════════════════════
 *  QuestionVoiceAiSheet — প্রতিটা প্রশ্নের 🤖 বাটন থেকে খোলা ভয়েস AI চ্যাট।
 *  Quiz/QBank/Study — তিন জায়গাতেই ব্যবহৃত হয় (QuestionListScreen থেকে)।
 *
 *  ── ভয়েস ইনপুট নিয়ে গুরুত্বপূর্ণ নোট (Play Store নিরাপত্তা) ──
 *  এখানে Android-এর নিজস্ব RecognizerIntent (ACTION_RECOGNIZE_SPEECH) ব্যবহার
 *  করা হয়েছে — এটা Google-এর সিস্টেম "কথা বলুন" ডায়ালগ আলাদা করে খোলে, যেটা
 *  নিজেই মাইক্রোফোন হ্যান্ডেল করে। আমাদের অ্যাপ কখনো raw মাইক অ্যাক্সেস করে না,
 *  তাই:
 *    • Manifest-এ RECORD_AUDIO পারমিশন যোগ করার দরকার নেই
 *    • Play Console-এর Data Safety ফর্মে মাইক্রোফোন ডিক্লেয়ার করার দরকার নেই
 *    • অ্যাপ রিভিউ/পলিসি-রিজেকশনের নতুন কোনো ঝুঁকি তৈরি হয় না
 *  (custom in-app মাইক UI — SpeechRecognizer সরাসরি — চাইলে পরে করা যাবে,
 *  কিন্তু তাতে RECORD_AUDIO পারমিশন + Data Safety আপডেট লাগবে।)
 * ══════════════════════════════════════════════════════════════════
 */
@Composable
fun QuestionVoiceAiSheet(
    item    : QuestionItem,
    mode    : StudyMode,
    hasNext : Boolean,
    onNext  : () -> Unit,
    onClose : () -> Unit,
    vm      : QuestionVoiceAiViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var autoSpeak by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(item.id) { vm.setQuestion(item, mode, speakIntro = autoSpeak) }

    DisposableEffect(Unit) {
        onDispose { TtsManager.stop() }
    }

    LaunchedEffect(state.messages.size, state.isSending) {
        val lastIdx = state.messages.size - 1 + if (state.isSending) 1 else 0
        if (lastIdx >= 0) listState.animateScrollToItem(lastIdx)
    }

    // ── ভয়েস ইনপুট রেজাল্ট হ্যান্ডলার — "next"/"পরের প্রশ্ন" বললে সরাসরি পরের
    // প্রশ্নে চলে যায়, নাহলে AI-কে পাঠানো হয় ──
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                val normalized = spoken.lowercase(Locale.getDefault())
                val isNextCommand = normalized == "next" || normalized == "নেক্সট" ||
                    normalized.contains("পরের প্রশ্ন") || normalized.contains("পরবর্তী প্রশ্ন")
                if (isNextCommand && hasNext) onNext() else vm.sendMessage(spoken)
            }
        }
    }
    fun launchMic() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "প্রশ্নটা নিয়ে কিছু জিজ্ঞেস করো...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "ভয়েস ইনপুট চালু করা যায়নি — ফোনে Google অ্যাপ/ভয়েস সার্ভিস আছে কিনা দেখো",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                // ── Header ──
                Column(
                    Modifier.fillMaxWidth()
                        .background(Indigo600)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🤖 প্রশ্ন নিয়ে কথা বলো", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { autoSpeak = !autoSpeak; if (!autoSpeak) TtsManager.stop() }) {
                            Icon(
                                if (autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "অটো-স্পিক", tint = Color.White
                            )
                        }
                        IconButton(onClick = { TtsManager.stop(); onClose() }) {
                            Icon(Icons.Default.Close, contentDescription = "বন্ধ করো", tint = Color.White)
                        }
                    }
                    Text(
                        item.question.ifBlank { item.explanation }.take(90),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f),
                        fontFamily = NotoSansBengali, maxLines = 2
                    )
                }

                if (!state.hasAnyKey) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.12f)).padding(12.dp)
                    ) {
                        Text(
                            "⚠️ ভয়েসে কথা বলতে হলে Menu → Settings-এ গিয়ে অন্তত একটা AI API key " +
                                "(Groq/Mistral/Cerebras/Gemini) সেভ করে নাও — ফ্রি-তেই পাওয়া যায়।",
                            fontSize = 12.sp, fontFamily = NotoSansBengali, lineHeight = 17.sp, color = Color(0xFF92400E)
                        )
                    }
                }

                if (state.messages.isEmpty() && state.hasAnyKey) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎤", fontSize = 34.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "মাইক চেপে প্রশ্নটা নিয়ে কিছু জিজ্ঞেস করো, অথবা টাইপ করেও লিখতে পারো",
                            fontSize = 13.sp, fontFamily = NotoSansBengali, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "\"পরের প্রশ্ন\" বললেই পরের প্রশ্নে চলে যাবে",
                            fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    state               = listState,
                    modifier            = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages) { msg -> VoiceChatBubble(msg) }
                    if (state.isSending) {
                        item { VoiceTypingIndicator() }
                    }
                }

                state.error?.let { err ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp)).background(RedWrong.copy(alpha = 0.10f)).padding(10.dp)
                    ) {
                        Text(err, fontSize = 12.sp, fontFamily = NotoSansBengali, color = RedWrong, modifier = Modifier.weight(1f))
                        Text(
                            "✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RedWrong,
                            modifier = Modifier.clickable { vm.clearError() }
                        )
                    }
                }

                // ── নিচের বার: মাইক + টেক্সট ইনপুট (fallback) + পাঠাও ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { launchMic() },
                        modifier = Modifier.clip(CircleShape).background(RedWrong)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "মাইক", tint = Color.White)
                    }
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = { inputText = it },
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text("অথবা টাইপ করো...", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        minLines = 1, maxLines = 3, shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Indigo600, unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )
                    val canSend = inputText.isNotBlank() && !state.isSending
                    IconButton(
                        onClick = { if (canSend) { vm.sendMessage(inputText); inputText = "" } },
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (canSend) Indigo600 else Color(0xFFCBD5E1))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "পাঠাও", tint = Color.White)
                    }
                }
                if (hasNext) {
                    Button(
                        onClick  = { TtsManager.stop(); onNext() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 10.dp).height(46.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2))
                    ) {
                        Text("পরের প্রশ্ন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceChatBubble(msg: AiChatMessage) {
    val isUser = msg.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart    = 14.dp,
                        topEnd      = 14.dp,
                        bottomStart = if (isUser) 14.dp else 2.dp,
                        bottomEnd   = if (isUser) 2.dp else 14.dp
                    )
                )
                .background(if (isUser) Indigo600 else Color(0xFFF1F5F9))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                msg.content, fontSize = 13.sp, fontFamily = NotoSansBengali, lineHeight = 18.sp,
                color = if (isUser) Color.White else Color(0xFF1E293B)
            )
        }
    }
}

@Composable
private fun VoiceTypingIndicator() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 2.dp, bottomEnd = 14.dp))
                .background(Color(0xFFF1F5F9))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Indigo600)
            Spacer(Modifier.width(8.dp))
            Text("AI ভাবছে…", fontSize = 12.sp, fontFamily = NotoSansBengali, color = Color(0xFF64748B))
        }
    }
}
