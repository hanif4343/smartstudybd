package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.firebaseKey
import com.hanif.smartstudy.data.repository.LeaderboardEntry
import com.hanif.smartstudy.data.repository.TypingLeaderboardRepository
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.SessionManager

/**
 * 🏆 লিডারবোর্ড — Firebase RTDB-এ (TypingRace/Challenge-এর একই instance, নতুন
 * কোনো ব্যাকএন্ড সেটআপ লাগেনি) টপ-WPM র‍্যাঙ্কিং — one-shot fetch (RTDB quota
 * বাঁচাতে persistent listener ব্যবহার করা হয়নি, দেখো TypingLeaderboardRepository)।
 * পুরো ফিচারটা SettingsScreen-এর "🏆 Typing Leaderboard" হোল্ড/আনহোল্ড টগলের
 * আওতায়, ডিফল্ট বন্ধ।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingLeaderboardScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }
    val repo = remember { TypingLeaderboardRepository() }
    val myPhone = remember { session.getCurrentUser()?.phone?.takeIf { it.isNotBlank() } ?: "" }
    val myKey = remember { myPhone.firebaseKey() }

    var entries by remember { mutableStateOf<List<LeaderboardEntry>?>(null) }
    var language by remember { mutableStateOf("bn") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(language, refreshKey) {
        entries = null
        entries = repo.fetchTop(language, limit = 50)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏆 লিডারবোর্ড", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // ── one-shot fetch (RTDB quota বাঁচাতে persistent listener বাদ
                    // দেওয়া হয়েছে) — তাই ম্যানুয়াল রিফ্রেশ বাটন দরকার ──
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "রিফ্রেশ")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── ভাষা টগল ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LangChip("বাংলা", language == "bn") { language = "bn" }
                LangChip("English", language == "en") { language = "en" }
            }

            when {
                entries == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                entries!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "এখনো কেউ এই ভাষায় স্কোর জমা দেয়নি — প্রথম হও! 🏁",
                        fontSize = 13.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val myRank = entries!!.indexOfFirst { it.phoneKey == myKey }
                    LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (myRank >= 0) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF6366F1).copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "তোমার র‍্যাঙ্ক: #${myRank + 1} — ${entries!![myRank].bestWpm} WPM",
                                        fontSize = 12.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6366F1), modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        itemsIndexed(entries!!) { idx, entry ->
                            LeaderboardRow(rank = idx + 1, entry = entry, isMe = entry.phoneKey == myKey)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF6366F1) else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Text(
            label, fontSize = 12.5.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isMe: Boolean) {
    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isMe) Color(0xFF6366F1).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isMe) Color(0xFF6366F1).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(
                    if (rank <= 3) Color(0xFFFFB648).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(medal ?: "$rank", fontSize = if (medal != null) 15.sp else 12.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name + if (isMe) " (তুমি)" else "", fontSize = 13.5.sp, fontFamily = NotoSansBengali,
                    fontWeight = if (isMe) FontWeight.ExtraBold else FontWeight.Medium
                )
                Text("Accuracy ${entry.accuracy}%", fontSize = 10.5.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${entry.bestWpm}", fontSize = 17.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
            Text("WPM", fontSize = 9.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
