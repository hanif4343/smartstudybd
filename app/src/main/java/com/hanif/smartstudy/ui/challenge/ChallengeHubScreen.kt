package com.hanif.smartstudy.ui.challenge

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.*

// ─────────────────────────────────────────────────────────
// ChallengeHubScreen — Challenge Zone main entry point
// Shows: pending invites + create button + info
// ─────────────────────────────────────────────────────────

@Composable
fun ChallengeZone(vm: ChallengeViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.dismissToast()
        }
    }

    val isInsideChallenge = state.screen !is ChallengeScreen.Home
    if (isInsideChallenge) {
        androidx.activity.compose.BackHandler(
            enabled = state.screen is ChallengeScreen.CreateSetup ||
                      state.screen is ChallengeScreen.Result
        ) { vm.goHome() }
    }

    var activeTab by remember { mutableStateOf(0) }

    when (state.screen) {
        is ChallengeScreen.Home -> {
            Column(Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor   = Color(0xFF1E1B4B),
                    contentColor     = Color.White,
                    indicator        = { tabPositions ->
                        if (activeTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                color = Color(0xFF818CF8)
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick  = { activeTab = 0 },
                        text = {
                            Text("⚔️ চ্যালেঞ্জ", fontSize = 12.sp,
                                fontWeight = if (activeTab == 0) FontWeight.ExtraBold else FontWeight.Normal,
                                fontFamily = NotoSansBengali, color = Color.White)
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick  = { activeTab = 1 },
                        text = {
                            Text("🏆 চ্যাম্পিয়নশিপ", fontSize = 12.sp,
                                fontWeight = if (activeTab == 1) FontWeight.ExtraBold else FontWeight.Normal,
                                fontFamily = NotoSansBengali, color = Color.White)
                        }
                    )
                }

                when (activeTab) {
                    0 -> ChallengeHubScreen(state, vm)
                    // battleVm এখানে — শুধু tab=1 হলেই তৈরি হয়, আগে নয়
                    1 -> {
                        val battleVm: WeekendBattleViewModel = viewModel()
                        WeekendBattleScreen(vm = battleVm)
                    }
                }
            }
        }
        is ChallengeScreen.CreateSetup -> CreateChallengeScreen(state, vm)
        is ChallengeScreen.Lobby       -> LobbyScreen(state, vm)
        is ChallengeScreen.Exam        -> ChallengeExamScreen(state, vm)
        is ChallengeScreen.Result      -> ChallengeResultScreen(state, vm)
    }
}

@Composable
private fun ChallengeHubScreen(state: ChallengeUiState, vm: ChallengeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // ── Header ──
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4F46E5))))
                .padding(16.dp)
        ) {
            Column {
                Text("⚔️ চ্যালেঞ্জ জোন", fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White,
                    fontFamily = NotoSansBengali)
                Text("বন্ধুদের সাথে লড়াই করো, জ্ঞানে এগিয়ে যাও",
                    fontSize = 12.sp, color = Color.White.copy(0.7f), fontFamily = NotoSansBengali)
            }
        }

        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))

            // ── Pending Invites ──
            if (state.pendingInvites.isNotEmpty()) {
                Text("📨 আমন্ত্রণ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B), fontFamily = NotoSansBengali)
                state.pendingInvites.forEach { invite ->
                    InviteCard(invite = invite, onAccept = { vm.respondToInvite(invite.challengeId, true) },
                        onDecline = { vm.respondToInvite(invite.challengeId, false) })
                }
                HorizontalDivider(color = Color(0xFFE2E8F0))
            }

            // ── Create button ──
            Button(
                onClick  = vm::openCreateSetup,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("নতুন চ্যালেঞ্জ তৈরি করো", fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            }

            // ── How it works ──
            HowItWorksCard()
        }
    }
}

@Composable
private fun InviteCard(invite: ChallengeInvite, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(Color.White),
        border    = BorderStroke(1.5.dp, Color(0xFF4F46E5).copy(0.3f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))),
                    contentAlignment = Alignment.Center) {
                    Text("⚔️", fontSize = 20.sp)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (invite.isGhostMode) "${invite.creatorName} Ghost চ্যালেঞ্জ পাঠিয়েছে!"
                            else "${invite.creatorName} চ্যালেঞ্জ পাঠিয়েছে!",
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E293B), fontFamily = NotoSansBengali
                        )
                        if (invite.isGhostMode) Text("👻", fontSize = 12.sp)
                    }
                    Text("${invite.subject} • ${invite.questionCount}টি প্রশ্ন • ${invite.timeLimitSec/60} মিনিট",
                        fontSize = 11.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali)
                    if (invite.isGhostMode) Text("সে আগেই পরীক্ষা দিয়েছে — তুমি দিলে রেজাল্ট দেখবে!",
                        fontSize = 10.sp, color = Color(0xFF7C3AED), fontFamily = NotoSansBengali)
                    if (invite.wagerXp > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💰", fontSize = 11.sp)
                            Text("বাজি: ±${invite.wagerXp} XP",
                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706), fontFamily = NotoSansBengali)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonColors(Color.White, Color(0xFFEF4444), Color(0xFFEF4444).copy(0.3f), Color(0xFF64748B))) {
                    Text("❌ না", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onAccept, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                    Text("✅ Accept", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color(0xFFEEF2FF)),
        border = BorderStroke(1.dp, Color(0xFFC7D2FE))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("কীভাবে কাজ করে?", fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold, color = Color(0xFF3730A3), fontFamily = NotoSansBengali)
            listOf(
                "1️⃣" to "বন্ধুর phone number দিয়ে invite করো",
                "2️⃣" to "বিষয়, প্রশ্ন সংখ্যা ও সময় ঠিক করো",
                "3️⃣" to "বন্ধু accept করলে একসাথে পরীক্ষা শুরু",
                "4️⃣" to "পরীক্ষা শেষে বিস্তারিত তুলনা দেখো"
            ).forEach { (icon, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(icon, fontSize = 14.sp)
                    Text(text, fontSize = 12.sp, color = Color(0xFF4338CA),
                        fontFamily = NotoSansBengali, lineHeight = 16.sp)
                }
            }
        }
    }
}
