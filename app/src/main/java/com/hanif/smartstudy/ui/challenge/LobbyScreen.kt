package com.hanif.smartstudy.ui.challenge

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.ChallengeUiState
import com.hanif.smartstudy.viewmodel.ChallengeViewModel

@Composable
fun LobbyScreen(state: ChallengeUiState, vm: ChallengeViewModel) {
    val challenge = state.challenge
    val status    = challenge?.getStatus() ?: ChallengeStatus.PENDING

    // Pulse animation for waiting
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.08f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "scale")

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Text("⚔️ লবি", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, fontFamily = NotoSansBengali)
            Text(challenge?.subject ?: "", fontSize = 14.sp,
                color = Color.White.copy(0.7f), fontFamily = NotoSansBengali)

            // Status
            AnimatedContent(targetState = status, label = "status") { s ->
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(
                    when (s) {
                        ChallengeStatus.PENDING  -> Color(0xFFF59E0B)
                        ChallengeStatus.WAITING  -> Color(0xFF10B981)
                        ChallengeStatus.ACTIVE   -> Color(0xFF4F46E5)
                        else                     -> Color(0xFF64748B)
                    }
                ).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        when (s) {
                            ChallengeStatus.PENDING -> "⏳ সবার accept এর অপেক্ষা..."
                            ChallengeStatus.WAITING -> "✅ সবাই রেডি! শুরু হচ্ছে..."
                            ChallengeStatus.ACTIVE  -> "🚀 পরীক্ষা শুরু হয়েছে!"
                            else                    -> "❌ Challenge বাতিল"
                        },
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali
                    )
                }
            }

            // Participants
            challenge?.participants?.values?.forEach { p ->
                ParticipantRow(participant = p)
            }

            // Challenge info pills
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("❓ ${challenge?.questionCount}টি প্রশ্ন")
                InfoPill("⏱ ${(challenge?.timeLimitSec ?: 0) / 60} মিনিট")
            }

            // Waiting pulse indicator
            if (status == ChallengeStatus.PENDING || status == ChallengeStatus.WAITING) {
                Box(Modifier.size(80.dp).scale(scale).clip(CircleShape)
                    .background(Color.White.copy(0.08f)),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White.copy(0.7f), modifier = Modifier.size(40.dp))
                }
                Text("পরীক্ষা শুরু হলে স্বয়ংক্রিয়ভাবে চলে যাবে",
                    fontSize = 11.sp, color = Color.White.copy(0.5f),
                    fontFamily = NotoSansBengali)
            }

            // Cancel button
            if (status == ChallengeStatus.PENDING) {
                TextButton(onClick = vm::goHome) {
                    Text("বাতিল করুন", color = Color.White.copy(0.5f),
                        fontFamily = NotoSansBengali, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(participant: ChallengeParticipant) {
    val (bg, icon, statusText) = when (participant.status) {
        "ACCEPTED"  -> Triple(Color(0xFF10B981), "✅", "রেডি!")
        "INVITED"   -> Triple(Color(0xFFF59E0B), "⏳", "অপেক্ষায়...")
        "DECLINED"  -> Triple(Color(0xFFEF4444), "❌", "Decline করেছে")
        "SUBMITTED" -> Triple(Color(0xFF4F46E5), "🏁", "শেষ করেছে")
        else        -> Triple(Color(0xFF64748B), "❓", participant.status)
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(bg.copy(0.2f)),
            contentAlignment = Alignment.Center) {
            Text(participant.name.firstOrNull()?.toString() ?: "?",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(participant.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color.White, fontFamily = NotoSansBengali)
            Text(participant.phone, fontSize = 10.sp, color = Color.White.copy(0.5f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(icon, fontSize = 14.sp)
            Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = bg, fontFamily = NotoSansBengali)
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(20.dp))
        .background(Color.White.copy(0.1f))
        .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Color.White, fontFamily = NotoSansBengali)
    }
}
