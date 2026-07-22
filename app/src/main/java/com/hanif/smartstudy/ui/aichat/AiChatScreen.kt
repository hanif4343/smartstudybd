package com.hanif.smartstudy.ui.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.AiChatMessage
import com.hanif.smartstudy.ui.shared.Indigo600
import com.hanif.smartstudy.ui.shared.RedWrong
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.AiChatViewModel

// ─────────────────────────────────────────────────────────────
//  AiChatScreen — "AI Chat" (ডাউট সলভার)
//  Home স্ক্রিনের কার্ড থেকে খোলে (আগে যেটা "Statistics"-এ যেত, সেই জায়গায়)।
//  বিদ্যমান "Study Buddy" (মানুষ-বন্ধু) ফিচার থেকে এটা সম্পূর্ণ আলাদা —
//  এখানে সরাসরি AI-এর সাথে কথা বলে পড়াশোনার প্রশ্ন/ডাউট জিজ্ঞেস করা যায়।
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    vm    : AiChatViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // নতুন মেসেজ আসলে বা AI উত্তর দিলে তালিকা স্বয়ংক্রিয়ভাবে নিচে স্ক্রল হয়
    LaunchedEffect(state.messages.size, state.isSending) {
        val lastIdx = state.messages.size - 1 + if (state.isSending) 1 else 0
        if (lastIdx >= 0) listState.animateScrollToItem(lastIdx)
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("চ্যাট মুছে ফেলবে?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
            text  = { Text("এই কথোপকথনের পুরো ইতিহাস মুছে যাবে।", fontFamily = NotoSansBengali, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.clearChat(); showClearDialog = false }) {
                    Text("হ্যাঁ, মুছে ফেলো", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 AI Chat", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "চ্যাট মুছো")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (!state.hasAnyKey) {
                NoApiKeyNotice()
            }

            if (state.messages.isEmpty() && state.hasAnyKey) {
                EmptyChatHint()
            }

            LazyColumn(
                state               = listState,
                modifier            = Modifier.weight(1f).fillMaxWidth(),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages) { msg -> ChatBubble(msg) }
                if (state.isSending) {
                    item { TypingIndicatorBubble() }
                }
            }

            state.error?.let { err ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RedWrong.copy(alpha = 0.10f))
                        .padding(10.dp)
                ) {
                    Text(
                        err, fontSize = 12.sp, fontFamily = NotoSansBengali,
                        color = RedWrong, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RedWrong,
                        modifier = Modifier.clickable { vm.clearError() }
                    )
                }
            }

            // ── নিচের ইনপুট বার — টাইপ করে Send বাটনে পাঠাও ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { inputText = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = {
                        Text("তোমার প্রশ্ন/ডাউট লিখো...", fontFamily = NotoSansBengali, fontSize = 13.sp)
                    },
                    minLines = 1,
                    maxLines = 4,
                    shape    = RoundedCornerShape(20.dp),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Indigo600,
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )
                val canSend = inputText.isNotBlank() && !state.isSending
                IconButton(
                    onClick = {
                        if (canSend) {
                            vm.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (canSend) Indigo600 else Color(0xFFCBD5E1))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "পাঠাও", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun NoApiKeyNotice() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Text(
            "⚠️ AI Chat চালাতে হলে Menu → Settings-এ গিয়ে অন্তত একটা AI API key " +
                "(Groq / Mistral / Cerebras / Gemini) সেভ করে নাও — এটা ফ্রি-তেই পাওয়া যায়।",
            fontSize = 12.sp, fontFamily = NotoSansBengali, lineHeight = 17.sp,
            color = Color(0xFF92400E)
        )
    }
}

@Composable
private fun EmptyChatHint() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Indigo600, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            "যেকোনো পড়াশোনার প্রশ্ন বা ডাউট জিজ্ঞেস করো",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "যেমন: \"সালোকসংশ্লেষণ কী?\" বা \"এই অংকটা বুঝিয়ে দাও\"",
            fontSize = 12.sp, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatBubble(msg: AiChatMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
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
                msg.content,
                fontSize   = 13.sp,
                fontFamily = NotoSansBengali,
                lineHeight = 18.sp,
                color      = if (isUser) Color.White else Color(0xFF1E293B)
            )
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
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
            Text("AI লিখছে…", fontSize = 12.sp, fontFamily = NotoSansBengali, color = Color(0xFF64748B))
        }
    }
}
