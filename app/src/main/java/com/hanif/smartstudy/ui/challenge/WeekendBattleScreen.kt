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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.ads.AdBannerView
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.AdManager
import com.hanif.smartstudy.viewmodel.WeekendBattleViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────
// WeekendBattleScreen — সাপ্তাহিক মেগা চ্যাম্পিয়নশিপ
// ─────────────────────────────────────────────────────────

@Composable
fun WeekendBattleScreen(vm: WeekendBattleViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // Tab open হলে একবার load করো
    LaunchedEffect(Unit) {
        vm.loadIfNeeded()
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); vm.dismissToast() }
    }

    if (state.isExamMode) {
        var showExitDialog by remember { mutableStateOf(false) }
        BackHandler { showExitDialog = true }
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("পরীক্ষা ছেড়ে যাবে?", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold) },
                text  = { Text("বের হলে তোমার উত্তরগুলো হারিয়ে যাবে।",
                    fontFamily = NotoSansBengali) },
                confirmButton = {
                    TextButton(onClick = { vm.exitExam(); showExitDialog = false }) {
                        Text("হ্যাঁ, বের হও", color = Color(0xFFEF4444), fontFamily = NotoSansBengali)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("না, থাকো", fontFamily = NotoSansBengali)
                    }
                }
            )
        }
        BattleExamScreen(state = state, vm = vm)
        return
    }

    BattleHubScreen(state = state, vm = vm)
}

// ── Hub Screen ────────────────────────────────────────────

@Composable
private fun BattleHubScreen(state: WeekendBattleUiState, vm: WeekendBattleViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // Header
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777))))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🏆", fontSize = 28.sp)
                Column {
                    Text("মেগা চ্যাম্পিয়নশিপ", fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.White,
                        fontFamily = NotoSansBengali)
                    Text("প্রতি সপ্তাহে শুক্র-শনি • সবাই একসাথে লড়াই",
                        fontSize = 11.sp, color = Color.White.copy(0.8f),
                        fontFamily = NotoSansBengali)
                }
            }
        }

        // Banner Ad
        AdBannerView(adUnitId = AdManager.BANNER_WEEKEND)

        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF7C3AED))
                }
                return@Column
            }

            state.error?.let { err ->
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
                    CardDefaults.cardColors(Color(0xFFFFF1F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⚠️ $err", fontFamily = NotoSansBengali, color = Color(0xFF9F1239))
                        Button(
                            onClick = { vm.dismissError(); vm.loadBattleInfo() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFF7C3AED))
                        ) {
                            Text("🔄 পুনরায় চেষ্টা করুন", fontFamily = NotoSansBengali,
                                fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                return@Column
            }

            state.toast?.let { ToastBanner(it) }

            val battle = state.activeBattle ?: state.upcomingBattle

            if (battle == null) {
                NoBattleCard()
            } else {
                BattleInfoCard(battle = battle, state = state, onStart = { vm.startExam() })
                if (state.leaderboard.isNotEmpty()) {
                    BattleLeaderboard(ranked = state.leaderboard)
                }
                if (state.hasSubmitted && state.myEntry != null) {
                    MyResultCard(entry = state.myEntry!!, battle = battle)
                }
            }

            BattleHowItWorksCard()
        }
    }
}

// ── Battle Info Card ──────────────────────────────────────

@Composable
private fun BattleInfoCard(
    battle  : WeekendBattle,
    state   : WeekendBattleUiState,
    onStart : () -> Unit
) {
    val now       = System.currentTimeMillis()
    val isActive  = battle.startsAt > 0 && battle.endsAt > 0 &&
                    battle.startsAt <= now && now <= battle.endsAt
    val isUpcoming = battle.startsAt > 0 && battle.startsAt > now

    // Safe date format — crash করবে না
    val startStr = safeDateFormat(battle.startsAt)
    val endStr   = safeDateFormat(battle.endsAt)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(battle.title.ifBlank { "সাপ্তাহিক চ্যাম্পিয়নশিপ" },
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B), fontFamily = NotoSansBengali,
                    modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(when {
                            isActive   -> Color(0xFF10B981)
                            isUpcoming -> Color(0xFF3B82F6)
                            else       -> Color(0xFF94A3B8)
                        })
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        when { isActive -> "চলছে"; isUpcoming -> "আসছে"; else -> "শেষ" },
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali
                    )
                }
            }

            // Stats
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                if (battle.subject.isNotBlank()) {
                    BattleStatChip("📚 ${battle.subject}", Color(0xFF4F46E5))
                }
                BattleStatChip("❓ ${battle.questionCount}টি", Color(0xFF0891B2))
                BattleStatChip("⏱ ${battle.timeLimitSec / 60} মিনিট", Color(0xFF059669))
            }

            if (battle.startsAt > 0) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("শুরু", fontSize = 10.sp, color = Color(0xFF64748B),
                            fontFamily = NotoSansBengali)
                        Text(startStr, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B), fontFamily = NotoSansBengali)
                    }
                    if (battle.endsAt > 0) {
                        Column(Modifier.weight(1f)) {
                            Text("শেষ", fontSize = 10.sp, color = Color(0xFF64748B),
                                fontFamily = NotoSansBengali)
                            Text(endStr, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B), fontFamily = NotoSansBengali)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("🏅 ${battle.participantCount} জন অংশ নিয়েছে",
                    fontSize = 11.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali)
                Text("🎖 পুরস্কার: ${battle.xpReward} XP",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF7C3AED), fontFamily = NotoSansBengali)
            }

            if (isActive && !state.hasSubmitted) {
                Button(
                    onClick  = onStart,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777)))
                            .let { Color(0xFF7C3AED) }
                    )
                ) {
                    Text("🚀 পরীক্ষা শুরু করো", fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                }
            } else if (state.hasSubmitted) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0FDF4))
                        .padding(12.dp),
                    Alignment.Center
                ) {
                    Text("✅ তুমি ইতিমধ্যে অংশ নিয়েছ!", fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color(0xFF166534),
                        fontFamily = NotoSansBengali)
                }
            } else if (isUpcoming) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF6FF))
                        .padding(12.dp),
                    Alignment.Center
                ) {
                    Text("⏳ $startStr থেকে শুরু হবে", fontSize = 12.sp,
                        color = Color(0xFF1D4ED8), fontFamily = NotoSansBengali,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── Leaderboard ───────────────────────────────────────────

@Composable
private fun BattleLeaderboard(ranked: List<BattleRankEntry>) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🏆 লিডারবোর্ড", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E293B), fontFamily = NotoSansBengali)
            ranked.take(20).forEach { r -> BattleRankRow(r) }
        }
    }
}

@Composable
private fun BattleRankRow(r: BattleRankEntry) {
    val rankEmoji = when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${r.rank}" }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (r.isMe) Color(0xFFEEF2FF) else Color(0xFFF8FAFC))
            .border(1.dp, if (r.isMe) Color(0xFF4F46E5) else Color.Transparent,
                RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(rankEmoji, fontSize = if (r.rank <= 3) 18.sp else 13.sp,
            fontWeight = FontWeight.ExtraBold)
        Column(Modifier.weight(1f)) {
            Text(if (r.isMe) "${r.entry.name} (তুমি)" else r.entry.name,
                fontSize = 13.sp,
                fontWeight = if (r.isMe) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (r.isMe) Color(0xFF4F46E5) else Color(0xFF1E293B),
                fontFamily = NotoSansBengali)
            Text("${r.entry.score}/${r.entry.total} • ${r.entry.accuracy.toInt()}%",
                fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali)
        }
        Text("+${r.entry.xpEarned} XP", fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C3AED),
            fontFamily = NotoSansBengali)
    }
}

@Composable
private fun MyResultCard(entry: BattleEntry, battle: WeekendBattle) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color(0xFFEEF2FF)),
        border = BorderStroke(1.5.dp, Color(0xFF4F46E5).copy(0.4f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📊 আমার ফলাফল", fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold, color = Color(0xFF3730A3),
                fontFamily = NotoSansBengali)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                StatBox("সঠিক", "${entry.score}/${entry.total}", Color(0xFF10B981))
                StatBox("Accuracy", "${entry.accuracy.toInt()}%", Color(0xFF4F46E5))
                StatBox("সময়", "${entry.timeTakenSec}s", Color(0xFFF59E0B))
                StatBox("XP", "+${entry.xpEarned}", Color(0xFF7C3AED))
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali)
    }
}

@Composable
private fun NoBattleCard() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp),
        CardDefaults.cardColors(Color(0xFFF5F3FF))) {
        Column(Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🏆", fontSize = 40.sp)
            Text("এই মুহূর্তে কোনো চ্যাম্পিয়নশিপ নেই",
                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4C1D95), fontFamily = NotoSansBengali,
                textAlign = TextAlign.Center)
            Text("প্রতি শুক্রবার রাত ৮টায় নতুন চ্যাম্পিয়নশিপ শুরু হয়",
                fontSize = 12.sp, color = Color(0xFF6D28D9), fontFamily = NotoSansBengali,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BattleHowItWorksCard() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color(0xFFF5F3FF)),
        border = BorderStroke(1.dp, Color(0xFFDDD6FE))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("কীভাবে কাজ করে?", fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold, color = Color(0xFF4C1D95),
                fontFamily = NotoSansBengali)
            listOf(
                "📅" to "প্রতি শুক্র-শনি সবাই একই প্রশ্নে পরীক্ষা দেয়",
                "📊" to "Score + Speed মিলিয়ে rank নির্ধারণ হয়",
                "🥇" to "১ম: +500 XP, ২য়: +250 XP, ৩য়: +125 XP",
                "⚡" to "প্রতি সঠিক উত্তরে +5 XP পাবে সবাই"
            ).forEach { (icon, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(icon, fontSize = 14.sp)
                    Text(text, fontSize = 12.sp, color = Color(0xFF5B21B6),
                        fontFamily = NotoSansBengali, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ToastBanner(msg: String) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .padding(12.dp),
        Alignment.Center
    ) {
        Text(msg, color = Color.White, fontSize = 13.sp,
            fontFamily = NotoSansBengali, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BattleStatChip(label: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp))
        .background(color.copy(0.1f))
        .padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = color, fontFamily = NotoSansBengali)
    }
}

// ── Exam Screen ───────────────────────────────────────────

@Composable
private fun BattleExamScreen(state: WeekendBattleUiState, vm: WeekendBattleViewModel) {
    val battle   = state.activeBattle ?: return
    val questions = state.questions
    val currentQ = questions.getOrNull(state.currentQIndex) ?: return

    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777))))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("🏆 মেগা চ্যাম্পিয়নশিপ", fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color.White,
                            fontFamily = NotoSansBengali)
                        if (battle.subject.isNotBlank()) {
                            Text(battle.subject, fontSize = 10.sp,
                                color = Color.White.copy(0.7f), fontFamily = NotoSansBengali)
                        }
                    }
                    BattleExamTimer(timerSec = state.timerSec, totalSec = battle.timeLimitSec)
                }
            }
        },
        bottomBar = {
            Column {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(count = questions.size) { idx ->
                        val answered = state.answers.containsKey(questions[idx].id)
                        Box(
                            Modifier.size(if (idx == state.currentQIndex) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(when {
                                    idx == state.currentQIndex -> Color(0xFF7C3AED)
                                    answered -> Color(0xFF10B981)
                                    else -> Color(0xFFE2E8F0)
                                })
                                .clickable { vm.goToQuestion(idx) }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { vm.goToQuestion(state.currentQIndex - 1) },
                        enabled = state.currentQIndex > 0,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("আগে", fontFamily = NotoSansBengali, fontSize = 12.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.currentQIndex + 1}/${questions.size}", fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B),
                            fontFamily = NotoSansBengali)
                        Text("${state.answers.size} উত্তর", fontSize = 10.sp,
                            color = Color(0xFF10B981), fontWeight = FontWeight.Bold,
                            fontFamily = NotoSansBengali)
                    }

                    if (state.currentQIndex < questions.size - 1) {
                        Button(
                            onClick = { vm.goToQuestion(state.currentQIndex + 1) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFF7C3AED))
                        ) {
                            Text("পরে", fontFamily = NotoSansBengali, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                        }
                    } else {
                        var showDialog by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFF10B981))
                        ) {
                            Text("✅ জমা দাও", fontFamily = NotoSansBengali, fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold)
                        }
                        if (showDialog) {
                            val unanswered = questions.size - state.answers.size
                            AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text("জমা দেবে?", fontFamily = NotoSansBengali,
                                    fontWeight = FontWeight.ExtraBold) },
                                text  = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("উত্তর দিয়েছ: ${state.answers.size}/${questions.size}",
                                            fontFamily = NotoSansBengali)
                                        if (unanswered > 0)
                                            Text("⚠️ $unanswered টি উত্তর বাকি!",
                                                color = Color(0xFFEF4444), fontFamily = NotoSansBengali)
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = { showDialog = false; vm.submitExam() },
                                        colors = ButtonDefaults.buttonColors(Color(0xFF10B981))) {
                                        Text("হ্যাঁ, জমা দাও", fontFamily = NotoSansBengali)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDialog = false }) {
                                        Text("না, আরো দেখি", fontFamily = NotoSansBengali)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3E8FF))
                            .padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("#${state.currentQIndex + 1}", fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C3AED))
                        }
                        if (currentQ.subTopic.isNotBlank()) {
                            Text(currentQ.subTopic, fontSize = 10.sp,
                                color = Color(0xFF64748B), fontFamily = NotoSansBengali)
                        }
                    }
                    Text(currentQ.question.replace(Regex("<[^>]+>"), ""),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B), fontFamily = NotoSansBengali,
                        lineHeight = 20.sp)

                    listOf(currentQ.optionA, currentQ.optionB, currentQ.optionC, currentQ.optionD)
                        .filterIndexed { _, opt -> opt.isNotBlank() }
                        .forEachIndexed { i, opt ->
                            val optNum = i + 1
                            val isSelected = state.answers[currentQ.id] == optNum
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFF3E8FF) else Color(0xFFF8FAFC))
                                    .border(1.5.dp,
                                        if (isSelected) Color(0xFF7C3AED) else Color(0xFFE2E8F0),
                                        RoundedCornerShape(12.dp))
                                    .clickable { vm.answerQuestion(currentQ.id, optNum) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(Modifier.size(26.dp).clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF7C3AED) else Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center) {
                                    Text(listOf("ক","খ","গ","ঘ")[i], fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.White else Color(0xFF64748B))
                                }
                                Text(opt, fontSize = 13.sp, fontFamily = NotoSansBengali,
                                    color = if (isSelected) Color(0xFF4A1D96) else Color(0xFF1E293B),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                }
            }
        }
    }

    if (state.isSubmitting) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
            contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Color(0xFF7C3AED))
                    Text("জমা হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Timer ─────────────────────────────────────────────────

@Composable
private fun BattleExamTimer(timerSec: Int, totalSec: Int) {
    val mins = timerSec / 60
    val secs = timerSec % 60
    val pct  = if (totalSec > 0) timerSec.toFloat() / totalSec else 0f
    val color = when {
        pct > 0.5f -> Color(0xFF10B981)
        pct > 0.25f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(String.format("%02d:%02d", mins, secs), fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold, color = color)
    }
}

// ── Helpers ───────────────────────────────────────────────

private fun safeDateFormat(timestampMs: Long): String {
    if (timestampMs <= 0L) return "—"
    return try {
        // Locale.ENGLISH — Bengali locale SimpleDateFormat crash করে
        val fmt = SimpleDateFormat("EEE d MMM, hh:mm a", Locale.ENGLISH)
        fmt.format(Date(timestampMs))
    } catch (e: Exception) {
        "—"
    }
}
