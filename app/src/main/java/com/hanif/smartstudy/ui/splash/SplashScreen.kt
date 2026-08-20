package com.hanif.smartstudy.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // ── FIX ("অ্যাপ ওপেন হতে ১৫-২০ সেকেন্ড লাগে"): আগে এখানে হার্ডকোড করা
    // delay(2500) ছিল — শুধু লোগো দেখানোর জন্য প্রতিবার অ্যাপ-ওপেনে আড়াই সেকেন্ড
    // জোর করে বসিয়ে রাখা হতো, session/login চেক ঢাকঢাক থেকেই ইনস্ট্যান্ট
    // (SharedPreferences read, নেটওয়ার্ক কল না)। এই ২.৫ সেকেন্ড + Android-এর নিজস্ব
    // system splash (installSplashScreen()) + MainActivity.onCreate()-এর কাজ
    // মিলিয়েই বেশিরভাগ "অ্যাপ খুলতে দেরি" অনুভূতির মূল কারণ ছিল। এখন মাত্র ৪৫০ms
    // রাখা হলো — লোগো/অ্যানিমেশন এক ঝলক দেখা যাওয়ার জন্য যথেষ্ট, কিন্তু আর কৃত্রিম
    // অপেক্ষা নেই। SessionManager(context).isLoggedIn() একটা লোকাল
    // SharedPreferences read মাত্র (কোনো নেটওয়ার্ক কল না) — তাই এরপর সাথে সাথেই
    // পরের স্ক্রিনে চলে যাওয়া নিরাপদ। ──
    LaunchedEffect(Unit) {
        delay(450)
        if (SessionManager(context).isLoggedIn()) onNavigateToMain()
        else onNavigateToAuth()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Indigo600, Indigo700))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo emoji as placeholder (actual mipmap shown via splash API)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("📚", fontSize = 52.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Smart Study",
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "পড়ো, শেখো, এগিয়ে যাও 🚀",
                color = White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = NotoSansBengali
            )

            Spacer(Modifier.height(60.dp))

            LoadingDots()
        }
    }
}

@Composable
fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue  = 1f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = dotAlpha))
            )
        }
    }
}
