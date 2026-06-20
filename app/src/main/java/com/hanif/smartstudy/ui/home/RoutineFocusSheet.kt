package com.hanif.smartstudy.ui.home

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.R
import com.hanif.smartstudy.model.RoutineTask
import com.hanif.smartstudy.viewmodel.MenuViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFocusSheet(
    task: RoutineTask,
    viewModel: MenuViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Core Timer States
    val totalSeconds = remember(task.durationMinutes) { task.durationMinutes * 60 }
    var timeLeft by remember { mutableIntStateOf(totalSeconds) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(task.isCompleted) }
    
    // Analytical Stats Tracks
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var pauseCount by remember { mutableIntStateOf(0) }
    var showCelebration by remember { mutableStateOf(false) }

    // Haptic Feedback / Vibration Controller
    val triggerVibration = {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(200L)
            }
        } catch (_: Exception) {}
    }

    // Main Countdown Coroutine Loop
    LaunchedEffect(isTimerRunning, timeLeft) {
        if (isTimerRunning && timeLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            timeLeft--
            elapsedSeconds++
            
            if (timeLeft == 0) {
                isTimerRunning = false
                isCompleted = true
                showCelebration = true
                triggerVibration()
                viewModel.toggleRoutineTaskCompletion(task.id)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Section 1: Header Meta Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) { // FIXED: Modifier.weight(1f) scope error resolved[span_0](start_span)[span_0](end_span)
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = task.category,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "⏱️ ${task.startTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), alpha = 0.5f)

            // Section 2: Visual Audio-Style Timer Ring Architecture
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .padding(12.dp)
            ) {
                val sweepAngleProgress by animateFloatAsState(
                    targetValue = if (totalSeconds > 0) (timeLeft.toFloat() / totalSeconds) else 0f,
                    animationSpec = tween(durationMillis = 500),
                    label = "TimerProgress"
                )

                // Custom Canvas Drawing for Aesthetic Smooth Ring
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                val primaryColor = MaterialTheme.colorScheme.primary
                val completeColor = Color(0xFF4CAF50)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Background Track Ring
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Active Foreground Countdown Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = if (isCompleted) listOf(completeColor, completeColor)
                                     else listOf(primaryColor, primaryColor.copy(alpha = 0.6f))
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepAngleProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner Status Typography
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatTime(timeLeft),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-1).sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isCompleted) "চমৎকার! শেষ হয়েছে" 
                               else if (isTimerRunning) "মনোযোগ দিন..." 
                               else "সাময়িক বিরতি",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = if (isCompleted) completeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Metrics Tracker Analytics Dashboard Block
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "মোট লক্ষ্য", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${task.durationMinutes} মিনিট", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "ব্যয়িত সময়", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${elapsedSeconds / 60}মি : ${elapsedSeconds % 60}সে", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "বিরতি সংখ্যা", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "$pauseCount বার", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Section 4: Dynamic Call To Actions (CTA) Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Secondary Reset/Undo Action Modifier Scope
                OutlinedButton(
                    onClick = {
                        triggerVibration()
                        if (isCompleted) {
                            isCompleted = false
                            showCelebration = false
                            timeLeft = totalSeconds
                            elapsedSeconds = 0
                            pauseCount = 0
                            viewModel.toggleRoutineTaskCompletion(task.id)
                        } else {
                            isTimerRunning = false
                            timeLeft = totalSeconds
                            elapsedSeconds = 0
                            pauseCount = 0
                        }
                    },
                    modifier = Modifier.weight(1f), // FIXED: Modifier.weight(1f) layout constraints resolved[span_1](start_span)[span_1](end_span)
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isCompleted) "পুনরায় শুরু" else "রিসেট")
                }

                // Primary Start/Pause State Processing Trigger
                Button(
                    onClick = {
                        triggerVibration()
                        if (!isCompleted) {
                            if (isTimerRunning) {
                                pauseCount++
                            }
                            isTimerRunning = !isTimerRunning
                        }
                    },
                    enabled = !isCompleted,
                    modifier = Modifier.weight(1f), // FIXED: Modifier.weight(1f) layout constraints resolved[span_2](start_span)[span_2](end_span)
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.8f)
                    )
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else if (isTimerRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "StateAction",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCompleted) "সম্পন্ন হয়েছে" else if (isTimerRunning) "থামুন" else "ফোকাস মোড",
                        color = Color.White
                    )
                }
            }

            // Section 5: Experimental Aesthetic Celebration Notification Banner
            AnimatedVisibility(
                visible = showCelebration,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .border(1.dp, Color(0xFF81C784), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Reward",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "দারুণ অধ্যবসায়!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "সফলভাবে আপনার মেমরি রিভিশন টাইম ব্লক সম্পন্ন হয়েছে। শৃঙ্খলা লক্ষ্য ধরে রাখে!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF388E3C)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
}

private fun getCategoryIcon(category: String): Int {
    return when (category.lowercase(Locale.getDefault())) {
        "study" -> R.drawable.ic_routine
        "revision" -> R.drawable.ic_routine
        "break" -> R.drawable.ic_routine
        else -> R.drawable.ic_routine
    }
}
