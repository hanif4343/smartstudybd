package com.hanif.smartstudy.ui.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════
// TOAST SYSTEM
// ═══════════════════════════════════════════════════════════════

enum class ToastType { SUCCESS, ERROR, INFO, WARNING }

data class ToastData(
    val message : String,
    val type    : ToastType = ToastType.INFO,
    val id      : Long = System.currentTimeMillis()
)

class ToastState {
    var current by mutableStateOf<ToastData?>(null)
        private set
    fun show(message: String, type: ToastType = ToastType.INFO) {
        current = ToastData(message, type)
    }
    fun dismiss() { current = null }
}

@Composable
fun rememberToastState() = remember { ToastState() }

@Composable
fun ToastHost(state: ToastState) {
    val toast = state.current ?: return
    LaunchedEffect(toast.id) { delay(3000); state.dismiss() }

    Box(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 60.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = state.current != null,
            enter   = slideInVertically { -it } + fadeIn(tween(200)),
            exit    = slideOutVertically { -it } + fadeOut(tween(200))
        ) {
            val (bg, icon) = when (toast.type) {
                ToastType.SUCCESS -> Color(0xFF10B981) to Icons.Default.CheckCircle
                ToastType.ERROR   -> Color(0xFFEF4444) to Icons.Default.Cancel
                ToastType.WARNING -> Color(0xFFF59E0B) to Icons.Default.Warning
                ToastType.INFO    -> Color(0xFF4F46E5) to Icons.Default.Info
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(toast.message, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SHIMMER / SKELETON
// ═══════════════════════════════════════════════════════════════

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue  = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label         = "shimmerX"
    )
    return Brush.linearGradient(
        colors = listOf(Color(0xFFE2E8F0), Color(0xFFF8FAFC), Color(0xFFE2E8F0)),
        start  = Offset(x - 200f, 0f),
        end    = Offset(x, 0f)
    )
}

@Composable
fun SubjectListSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(
        modifier            = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(brush))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                }
                Box(Modifier.width(38.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            }
        }
    }
}

@Composable
fun QuestionCardSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(
        modifier            = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(2) {
            Column(
                modifier            = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(4.dp))
                repeat(4) { Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(brush)) }
            }
        }
    }
}

@Composable
fun HomeCardSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(20.dp)).background(brush))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { Box(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(14.dp)).background(brush)) }
        }
        repeat(3) { Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(brush)) }
    }
}

// ═══════════════════════════════════════════════════════════════
// ERROR STATE
// ═══════════════════════════════════════════════════════════════

@Composable
fun ErrorState(
    message : String,
    onRetry : (() -> Unit)? = null,
    modifier: Modifier      = Modifier
) {
    Column(
        modifier                = modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment     = Alignment.CenterHorizontally,
        verticalArrangement     = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFFFF1F2)),
            contentAlignment = Alignment.Center
        ) { Text("⚠️", fontSize = 32.sp) }
        Text("সমস্যা হয়েছে", fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
            color = Color(0xFF1E293B))
        Text(message, fontSize = 12.sp, fontFamily = NotoSansBengali,
            color = Color(0xFF64748B), textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (onRetry != null) {
            Button(
                onClick = onRetry, shape = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("আবার চেষ্টা করো", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════

@Composable
fun EmptyState(
    emoji   : String    = "📭",
    title   : String    = "কিছু নেই",
    subtitle: String    = "",
    modifier: Modifier  = Modifier
) {
    Column(
        modifier            = modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(emoji, fontSize = 48.sp)
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = NotoSansBengali, color = Color(0xFF1E293B))
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = 12.sp, fontFamily = NotoSansBengali,
                color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// OFFLINE BANNER
// ═══════════════════════════════════════════════════════════════

@Composable
fun OfflineBanner(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter   = expandVertically() + fadeIn(),
        exit    = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFFEF2F2))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WifiOff, null,
                tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
            Text("ইন্টারনেট নেই — পুরনো data দেখাচ্ছে",
                fontSize = 11.sp, fontFamily = NotoSansBengali,
                color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HAPTIC FEEDBACK UTILITY
// ═══════════════════════════════════════════════════════════════

object HapticUtil {
    fun light(ctx: android.content.Context)   = vibrate(ctx, 40)
    fun medium(ctx: android.content.Context)  = vibrate(ctx, 80)
    fun success(ctx: android.content.Context) = vibrate(ctx, 60)
    fun error(ctx: android.content.Context)   = vibrate(ctx, 180)
    fun tick(ctx: android.content.Context)    = vibrate(ctx, 25)

    private fun vibrate(ctx: android.content.Context, ms: Long) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
                    ?.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(ms)
            }
        } catch (_: Exception) {}
    }
}
