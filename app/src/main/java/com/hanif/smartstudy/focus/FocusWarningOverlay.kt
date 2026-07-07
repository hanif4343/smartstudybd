package com.hanif.smartstudy.focus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// ═══════════════════════════════════════════════════════════════════
//  FocusWarningOverlay
// ───────────────────────────────────────────────────────────────────
//  অ্যাপ খোলার সাথে সাথে (ফোকাস মোড চালু থাকলেই শুধু) দেখা যাওয়া
//  ফুল-স্ক্রিন ওভারলে। WrongReviewSection.kt-এর CongratsOverlay এর একই
//  bounce/pulse প্যাটার্নে বানানো — অ্যাপের বাকি অংশের সাথে ভিজ্যুয়াল
//  ধারাবাহিকতা রাখার জন্য।
//
//  এখানে কোনো কিছু জোর করে আটকানো হয় না — "এখন না" বাটন সবসময় থাকে।
// ═══════════════════════════════════════════════════════════════════
@Composable
fun FocusWarningOverlay(
    subject   : String,
    daysLeft  : Int,
    onStart   : () -> Unit,
    onDismiss : () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "focus_warning_pulse")
    val scale by pulse.animateFloat(
        initialValue  = 0.96f,
        targetValue   = 1.04f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "focus_warning_scale"
    )

    val dayLabel = when {
        daysLeft <= 0 -> "আজই"
        daysLeft == 1 -> "আগামীকাল"
        else          -> "$daysLeft দিন পরে"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(Color(0xCC0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth(0.88f)
                .scale(scale)
                .padding(horizontal = 8.dp),
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier             = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.spacedBy(14.dp)
            ) {
                Text("⏰", fontSize = 56.sp)

                Text(
                    "পরীক্ষা $dayLabel!",
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color(0xFFFBBF24),
                    fontFamily = NotoSansBengali,
                    textAlign  = TextAlign.Center
                )

                Text(
                    "$subject পড়ায় মনোযোগ দিন",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    fontFamily = NotoSansBengali,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(2.dp))

                Button(
                    onClick  = onStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24))
                ) {
                    Text(
                        "📖 এখনই পড়া শুরু করি",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color(0xFF1E1B4B),
                        fontFamily = NotoSansBengali
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        "এখন না",
                        fontSize   = 13.sp,
                        color      = Color.White.copy(alpha = 0.7f),
                        fontFamily = NotoSansBengali
                    )
                }
            }
        }
    }
}
