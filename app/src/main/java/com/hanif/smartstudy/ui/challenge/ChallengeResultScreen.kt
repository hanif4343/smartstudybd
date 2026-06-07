package com.hanif.smartstudy.ui.challenge

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.AdManager
import com.hanif.smartstudy.viewmodel.ChallengeUiState
import com.hanif.smartstudy.viewmodel.ChallengeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChallengeResultScreen(state: ChallengeUiState, vm: ChallengeViewModel) {
    val challenge = state.challenge ?: return
    val myPhone   = state.myPhone
    val total     = state.questions.size
    val context   = LocalContext.current

    val sorted = challenge.participants.values
        .filter { it.score >= 0 }
        .sortedByDescending { it.score }

    val myResult     = sorted.find { it.phone == myPhone }
    val myRank       = sorted.indexOfFirst { it.phone == myPhone } + 1
    val iWon         = myRank == 1 && sorted.size > 1
    val waitingCount = challenge.participants.values.count { it.status != "SUBMITTED" }

    var showComparison by remember { mutableStateOf(false) }

    // ── Rewarded Ad: background load ──
    var rewardedAd    by remember { mutableStateOf<RewardedAd?>(null) }
    var xpDoubled     by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        AdManager.loadRewarded(
            context  = context,
            onLoaded = { rewardedAd = it },
            onFailed = { rewardedAd = null }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81))))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // ── Hero ──
        Box(
            Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {

                if (waitingCount > 0) {
                    // Still waiting
                    CircularProgressIndicator(color = Color.White.copy(0.5f))
                    Text("$waitingCount জন এখনো পরীক্ষা দিচ্ছে...",
                        fontSize = 13.sp, color = Color.White.copy(0.7f),
                        fontFamily = NotoSansBengali)
                    Text("রেজাল্ট আসছে...", fontSize = 11.sp,
                        color = Color.White.copy(0.5f), fontFamily = NotoSansBengali)
                } else {
                    // All done
                    Text(if (iWon) "🏆" else when (myRank) { 2 -> "🥈"; 3 -> "🥉"; else -> "⭐" },
                        fontSize = 64.sp)
                    Text(if (iWon) "অভিনন্দন! তুমি জিতেছ!" else "পরীক্ষা শেষ!",
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali)

                    myResult?.let { me ->
                        Text("${me.score}/${total} সঠিক • ${(me.score * 100f / total.coerceAtLeast(1)).toInt()}%",
                            fontSize = 14.sp, color = Color.White.copy(0.8f),
                            fontFamily = NotoSansBengali)
                        if (myRank > 0) {
                            Box(Modifier.clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.15f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Text("${myRank} নম্বর অবস্থান", fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold, color = Color.White,
                                    fontFamily = NotoSansBengali)
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Accuracy Tip ──
            state.accuracyTip?.let { tip ->
                AccuracyTipCard(tip)
            }

            if (sorted.isNotEmpty()) {
                // ── Leaderboard ──
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                    CardDefaults.cardColors(Color.White.copy(0.1f)),
                    border = BorderStroke(1.dp, Color.White.copy(0.15f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏆 লিডারবোর্ড", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali)
                        sorted.forEachIndexed { idx, p ->
                            ResultRow(
                                rank     = idx + 1,
                                participant = p,
                                total    = total,
                                isMe     = p.phone == myPhone
                            )
                        }
                    }
                }

                // ── Comparison toggle ──
                OutlinedButton(
                    onClick  = { showComparison = !showComparison },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonColors(Color.White.copy(0.1f), Color.White,
                        Color.White.copy(0.3f), Color.White.copy(0.5f)),
                    border   = BorderStroke(1.dp, Color.White.copy(0.3f))
                ) {
                    Text(if (showComparison) "তুলনা লুকাও" else "📊 বিস্তারিত তুলনা দেখো",
                        fontFamily = NotoSansBengali, fontSize = 13.sp)
                }

                // ── Question comparison ──
                AnimatedVisibility(showComparison) {
                    QuestionComparisonCard(
                        questions    = state.questions,
                        participants = sorted,
                        myPhone      = myPhone
                    )
                }
            }

            // ── Action buttons ──
            if (sorted.size > 1 && waitingCount == 0) {
                // Rematch button
                Button(
                    onClick  = { vm.rematch() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("♻️ Rematch", fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                    }
                }
            }

            // Match History — 1v1 হলে দেখাও
            val opponent = sorted.find { it.phone != myPhone }
            if (opponent != null) {
                LaunchedEffect(opponent.phone) { vm.loadMatchHistory(opponent.phone) }
                if (state.matchHistory.isNotEmpty()) {
                    MatchHistoryCard(
                        history      = state.matchHistory,
                        myPhone      = myPhone,
                        opponentName = opponent.name
                    )
                }
            }

            // ── Rewarded Ad — XP Double বাটন ──
            if (!xpDoubled && waitingCount == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(Color(0xFFFEF3C7)),
                    border   = BorderStroke(1.dp, Color(0xFFFBBF24))
                ) {
                    Row(
                        modifier              = Modifier.padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("⭐", fontSize = 28.sp)
                        Column(Modifier.weight(1f)) {
                            Text("XP দ্বিগুণ করো!", fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold, color = Color(0xFF92400E),
                                fontFamily = NotoSansBengali)
                            Text("একটি ছোট ভিডিও দেখো, XP ×২ পাও",
                                fontSize = 11.sp, color = Color(0xFFB45309),
                                fontFamily = NotoSansBengali)
                        }
                        Button(
                            onClick = {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    AdManager.showRewarded(
                                        activity    = activity,
                                        ad          = rewardedAd,
                                        onRewarded  = { _ ->
                                            xpDoubled = true
                                            vm.doubleXP()   // ViewModel এ XP double করো
                                        },
                                        onDismissed = { rewardedAd = null }
                                    )
                                }
                            },
                            enabled = rewardedAd != null,
                            shape   = RoundedCornerShape(10.dp),
                            colors  = ButtonDefaults.buttonColors(
                                containerColor        = Color(0xFFF59E0B),
                                disabledContainerColor= Color(0xFFD1D5DB)
                            )
                        ) {
                            Text(if (rewardedAd != null) "▶ দেখো" else "লোড হচ্ছে...",
                                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }

            // XP double হলে success message
            if (xpDoubled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(Color(0xFFF0FDF4)),
                    border   = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Text("🎉 অভিনন্দন! XP দ্বিগুণ হয়েছে!",
                        modifier = Modifier.padding(14.dp),
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF166534), fontSize = 14.sp)
                }
            }

            Button(
                onClick  = vm::goHome,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("🏠 হোমে ফিরে যাও", fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            }
        }
    }
}

@Composable
private fun ResultRow(rank: Int, participant: ChallengeParticipant, total: Int, isMe: Boolean) {
    val rankColor = when (rank) { 1 -> Color(0xFFFBBF24); 2 -> Color(0xFF94A3B8); 3 -> Color(0xFFCD7C2A); else -> Color.White.copy(0.6f) }
    val rankEmoji = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank" }
    val pct = if (total > 0) (participant.score * 100f / total).toInt() else 0

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMe) Color.White.copy(0.18f) else Color.White.copy(0.06f))
            .then(if (isMe) Modifier.border(1.5.dp, Color(0xFFFBBF24), RoundedCornerShape(12.dp)) else Modifier)
            .padding(10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(rankEmoji, fontSize = 20.sp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(participant.name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontFamily = NotoSansBengali)
                if (isMe) Box(Modifier.clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFBBF24)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("তুমি", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF78350F))
                }
            }
            LinearProgressIndicator(
                progress   = { pct / 100f },
                modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).padding(top = 3.dp),
                color      = rankColor,
                trackColor = Color.White.copy(0.1f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${participant.score}/$total", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                color = rankColor, fontFamily = NotoSansBengali)
            Text("$pct%", fontSize = 10.sp, color = Color.White.copy(0.5f))
        }
    }
}

@Composable
private fun QuestionComparisonCard(
    questions    : List<QuestionItem>,
    participants : List<ChallengeParticipant>,
    myPhone      : String
) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color.White.copy(0.08f)),
        border = BorderStroke(1.dp, Color.White.copy(0.15f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📋 প্রশ্নভিত্তিক তুলনা", fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = NotoSansBengali)
            questions.forEachIndexed { idx, q ->
                val allCorrect   = participants.all { p -> p.correctIds.contains(q.id) }
                val allWrong     = participants.none { p -> p.correctIds.contains(q.id) }
                val bgColor      = when {
                    allCorrect -> Color(0xFF10B981).copy(0.15f)
                    allWrong   -> Color(0xFFEF4444).copy(0.15f)
                    else       -> Color.White.copy(0.05f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)).background(bgColor)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${idx + 1}.", fontSize = 11.sp, color = Color.White.copy(0.5f),
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Text(q.question.replace(Regex("<[^>]+>"), "").take(45) + "…",
                        fontSize = 11.sp, color = Color.White, fontFamily = NotoSansBengali,
                        modifier = Modifier.weight(1f), lineHeight = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        participants.forEach { p ->
                            val correct = p.correctIds.contains(q.id)
                            val isMe    = p.phone == myPhone
                            Box(Modifier.size(20.dp).clip(CircleShape)
                                .background(if (correct) Color(0xFF10B981) else Color(0xFFEF4444))
                                .then(if (isMe) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center) {
                                Text(if (correct) "✓" else "✗", fontSize = 9.sp,
                                    color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
                // Highlight: সবাই ভুল করলে special note
                if (allWrong) {
                    Text("  ⚡ সবাই এই প্রশ্নে ভুল করেছে!", fontSize = 9.sp,
                        color = Color(0xFFFBBF24), fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// ── Match History Card ────────────────────────────────────

@Composable
fun MatchHistoryCard(
    history      : List<MatchRecord>,
    myPhone      : String,
    opponentName : String
) {
    val wins   = history.count { it.iWon }
    val losses = history.size - wins

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(Color.White.copy(0.1f)),
        border   = BorderStroke(1.dp, Color.White.copy(0.15f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header: win/loss summary
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "⚔️ ${opponentName.split(" ").first()} এর সাথে ইতিহাস",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontFamily = NotoSansBengali
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WinLossBadge("${wins}W", Color(0xFF10B981))
                    WinLossBadge("${losses}L", Color(0xFFEF4444))
                }
            }

            // Win rate bar
            val winRate = if (history.isNotEmpty()) wins.toFloat() / history.size else 0f
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFEF4444).copy(0.4f))
            ) {
                Box(
                    Modifier.fillMaxWidth(winRate).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF10B981))
                )
            }

            // Last 5 matches
            history.take(5).forEach { record ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (record.iWon) Color(0xFF10B981).copy(0.12f)
                            else Color(0xFFEF4444).copy(0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(if (record.iWon) "🏆" else "💔", fontSize = 14.sp)
                        Column {
                            Text(
                                record.subject.take(18),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = Color.White, fontFamily = NotoSansBengali
                            )
                            Text(
                                SimpleDateFormat("dd MMM", Locale.getDefault())
                                    .format(Date(record.playedAt)),
                                fontSize = 9.sp, color = Color.White.copy(0.5f)
                            )
                        }
                        if (record.isGhostMode) {
                            Text("👻", fontSize = 10.sp)
                        }
                    }
                    Text(
                        "${record.myScore} - ${record.opponentScore}",
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (record.iWon) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontFamily = NotoSansBengali
                    )
                }
            }

            if (history.size > 5) {
                Text(
                    "... আরো ${history.size - 5}টি ম্যাচ",
                    fontSize = 10.sp, color = Color.White.copy(0.4f),
                    fontFamily = NotoSansBengali,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun WinLossBadge(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(0.2f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = color, fontFamily = NotoSansBengali)
    }
}

// ── Accuracy / Speed tip card ──
@Composable
private fun AccuracyTipCard(tip: String) {
    val isWarning = tip.startsWith("⚠️") || tip.startsWith("💡")
    val bg        = if (isWarning) Color(0xFFFFF7ED) else Color(0xFFF0FDF4)
    val border    = if (isWarning) Color(0xFFFED7AA) else Color(0xFFBBF7D0)
    val textColor = if (isWarning) Color(0xFF92400E) else Color(0xFF166534)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(bg),
        border   = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier  = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(if (isWarning) "📊" else "🌟", fontSize = 22.sp)
            Text(
                text       = tip,
                fontSize   = 12.sp,
                color      = textColor,
                fontFamily = NotoSansBengali,
                lineHeight = 18.sp,
                modifier   = Modifier.weight(1f)
            )
        }
    }
}
