package com.hanif.smartstudy.ui.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.model.Achievement
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────
// 1. Offline Banner (bottom, dismissible)
// ─────────────────────────────────────────────────────────

@Composable
fun OfflineBanner(
    isOffline       : Boolean,
    pendingSyncCount: Int = 0,
    modifier        : Modifier = Modifier
) {
    var dismissed by remember { mutableStateOf(false) }

    // Re-show if goes offline again
    LaunchedEffect(isOffline) { if (isOffline) dismissed = false }

    AnimatedVisibility(
        visible = isOffline && !dismissed,
        enter   = slideInVertically { it } + fadeIn(),
        exit    = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing wifi-off icon
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.85f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "scale"
                )
                Icon(
                    Icons.Default.WifiOff, null,
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(16.dp).scale(pulse)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "ইন্টারনেট সংযোগ নেই",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, fontFamily = NotoSansBengali
                    )
                    if (pendingSyncCount > 0) {
                        Text(
                            "$pendingSyncCount টি কার্যক্রম sync-এর অপেক্ষায়",
                            fontSize = 10.sp, color = Color(0xFF94A3B8),
                            fontFamily = NotoSansBengali
                        )
                    }
                }
                if (pendingSyncCount > 0) {
                    Badge(containerColor = Color(0xFFF59E0B)) {
                        Text("$pendingSyncCount", fontSize = 9.sp, color = Color.White)
                    }
                }
                // Close button
                IconButton(
                    onClick = { dismissed = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close, null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 2. Achievement Popup
// ─────────────────────────────────────────────────────────

@Composable
fun AchievementPopup(
    achievement : Achievement?,
    onDismiss   : () -> Unit
) {
    if (achievement == null) return

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(achievement) {
        visible = true
        delay(3500)
        visible = false
        delay(400)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.8f),
        exit    = slideOutVertically { -it } + fadeOut()
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(Color(0xFF1E1B4B)),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { visible = false }
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val sparkle by rememberInfiniteTransition(label = "sp").animateFloat(
                        0.9f, 1.1f,
                        infiniteRepeatable(tween(400), RepeatMode.Reverse),
                        label = "scale"
                    )
                    Text(achievement.emoji, fontSize = 32.sp, modifier = Modifier.scale(sparkle))
                    Column {
                        Text(
                            "🏆 Achievement Unlocked!",
                            fontSize = 10.sp, color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali
                        )
                        Text(
                            achievement.title,
                            fontSize = 14.sp, color = Color.White,
                            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali
                        )
                        Text(
                            achievement.description,
                            fontSize = 11.sp, color = Color(0xFF94A3B8),
                            fontFamily = NotoSansBengali
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+${achievement.xpReward}", fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color(0xFFFBBF24),
                            fontFamily = NotoSansBengali)
                        Text("XP", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 3. Streak Popup
// ─────────────────────────────────────────────────────────

@Composable
fun StreakPopup(
    streak    : Int,
    onDismiss : () -> Unit
) {
    if (streak <= 0) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape  = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(Color(0xFF1E1B4B)),
            elevation = CardDefaults.cardElevation(24.dp)
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Fire animation
                val fire by rememberInfiniteTransition(label = "fire").animateFloat(
                    0.95f, 1.05f,
                    infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "s"
                )
                Text("🔥", fontSize = 64.sp, modifier = Modifier.scale(fire))

                Text(
                    "$streak",
                    fontSize = 56.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFB923C), fontFamily = NotoSansBengali
                )
                Text(
                    "দিনের Streak!",
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontFamily = NotoSansBengali
                )

                val msg = when {
                    streak >= 30 -> "অবিশ্বাস্য! তুমি একজন Study Legend! 👑"
                    streak >= 7  -> "দারুণ! এক সপ্তাহ ধরে পড়ছ! ⚡"
                    streak >= 3  -> "চমৎকার! ধারাবাহিকতা বজায় রাখো! 💪"
                    else         -> "ভালো শুরু! প্রতিদিন পড়ার অভ্যাস গড়ো!"
                }
                Text(
                    msg, fontSize = 13.sp, color = Color(0xFF94A3B8),
                    fontFamily = NotoSansBengali, textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB923C))
                ) {
                    Text("চালিয়ে যাও! 🚀", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 4. In-app Reminder Banner (animated, shows inside app)
// ─────────────────────────────────────────────────────────

@Composable
fun ReminderBanner(
    message   : String,
    onDismiss : () -> Unit,
    modifier  : Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(5000)
        visible = false
        delay(400)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter   = expandVertically() + fadeIn(),
        exit    = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { visible = false }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val bell by rememberInfiniteTransition(label = "bell").animateFloat(
                    -8f, 8f,
                    infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
                    label = "rot"
                )
                Text("🔔", fontSize = 18.sp)
                Text(
                    message, fontSize = 13.sp, color = Color.White,
                    fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { visible = false; onDismiss() },
                    modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// 5. Image Zoom Overlay
// ─────────────────────────────────────────────────────────

@Composable
fun ImageZoomOverlay(
    imageUrl  : String,
    onDismiss : () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside   = true
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            var scale by remember { mutableStateOf(1f) }
            val animScale by animateFloatAsState(scale, tween(200), label = "zoom")

            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .scale(animScale)
            )

            // Close button top-right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.White.copy(0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }

            // Zoom hint
            Text(
                "ট্যাপ করে বন্ধ করুন",
                fontSize = 11.sp, color = Color.White.copy(0.5f),
                fontFamily = NotoSansBengali,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// 6. Pending Sync Badge (for BottomBar)
// ─────────────────────────────────────────────────────────

@Composable
fun PendingSyncBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .size(16.dp)
            .background(Color(0xFFF59E0B), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 9) "9+" else "$count",
            fontSize = 8.sp, color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
