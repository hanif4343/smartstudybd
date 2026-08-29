package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.BijoyCurriculum
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.CurriculumProvider
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  প্লেসমেন্ট-টেস্ট — Keybr/TypingClub-স্টাইল স্কিল-চেক
 * ═══════════════════════════════════════════════════════════════════════
 * নতুন ইউজার Smart Typing-এ প্রথমবার ঢুকলে এটা দেখানো হয় (একবারই — দেখো
 * SessionManager.hasCompletedTypingPlacement)। যে ইউজার আগে থেকেই টাইপ করতে
 * জানে, তাকে জোর করে স্টেজ ১ (মাত্র "ক া র ন ত ঁ") থেকে শুরু করানো অপমানজনক
 * ও বোরিং — এই টেস্ট তার আসল WPM/Accuracy মেপে সরাসরি উপযুক্ত স্টেজে বসিয়ে দেয়।
 *
 * ⚠️ ডিজাইন-সিদ্ধান্ত: এটা মূল TypingSessionViewModel ব্যবহার করে না, ইচ্ছাকৃতভাবে
 * সম্পূর্ণ self-contained ও হালকা রাখা হয়েছে (এখানে backspace-lock, rhythm-score,
 * curriculum-unlock — এসব কিছুই দরকার নেই, শুধু raw WPM/accuracy চাই)।
 */
@Composable
fun TypingPlacementTestScreen(onComplete: () -> Unit) {
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }
    val scope = rememberCoroutineScope()

    val targetText = remember {
        "আমি প্রতিদিন সকালে ঘুম থেকে উঠে বই পড়ি এবং তারপর স্কুলে যাওয়ার জন্য প্রস্তুত হই।"
    }
    var typed by remember { mutableStateOf("") }
    var startMs by remember { mutableStateOf(0L) }
    var elapsedSec by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var placedStage by remember { mutableStateOf(1) }
    var placedLabel by remember { mutableStateOf("") }

    LaunchedEffect(startMs, finished) {
        if (startMs == 0L || finished) return@LaunchedEffect
        while (!finished) {
            elapsedSec = ((System.currentTimeMillis() - startMs) / 1000).toInt()
            delay(500)
        }
    }

    fun finishTest() {
        val correctChars = typed.indices.count { i -> i < targetText.length && typed[i] == targetText[i] }
        val secs = elapsedSec.coerceAtLeast(1)
        val wpm = (correctChars / 5.0 / (secs / 60.0)).toInt()
        val accuracy = if (typed.isNotEmpty()) (correctChars * 100 / typed.length) else 0
        val totalStages = BijoyCurriculum.totalStages("bn")

        val (stage, label) = when {
            wpm >= 35 && accuracy >= 90 -> (totalStages * 0.55).toInt().coerceIn(1, totalStages) to "উন্নত"
            wpm >= 20 && accuracy >= 85 -> (totalStages * 0.25).toInt().coerceIn(1, totalStages) to "মধ্যম"
            wpm >= 10 && accuracy >= 70 -> 3 to "শুরুয়াতি (কিছুটা অভিজ্ঞ)"
            else -> 1 to "একদম নতুন"
        }
        placedStage = stage
        placedLabel = label
        finished = true
        scope.launch {
            CurriculumProvider.setStage(ctx, "bn", stage)
            session.setTypingPlacementCompleted()
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            if (!finished) {
                Text("⌨️", fontSize = 34.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "চলো দেখি আপনার টাইপিং কেমন", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali, textAlign = TextAlign.Center
                )
                Text(
                    "নিচের লাইনটা যতটা পারেন স্বাভাবিক গতিতে টাইপ করুন — এর ভিত্তিতে আপনার জন্য সঠিক জায়গা থেকে শুরু করানো হবে",
                    fontSize = 12.5.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        targetText,
                        fontSize = 17.sp, fontFamily = NotoSansBengali, lineHeight = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(18.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("%02d:%02d".format(elapsedSec / 60, elapsedSec % 60), fontSize = 13.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = typed,
                    onValueChange = { new ->
                        if (startMs == 0L && new.isNotEmpty()) startMs = System.currentTimeMillis()
                        typed = new
                        if (new.length >= targetText.length) finishTest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("এখানে টাইপ শুরু করুন...", fontFamily = NotoSansBengali) },
                    keyboardOptions = KeyboardOptions.Default,
                    minLines = 3
                )

                Spacer(Modifier.height(18.dp))
                TextButton(onClick = {
                    scope.launch { session.setTypingPlacementCompleted() }
                    onComplete()
                }) {
                    Text("এড়িয়ে যান, স্টেজ ১ থেকেই শুরু করব", fontSize = 12.sp, fontFamily = NotoSansBengali,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(40.dp))
                Text("🎯", fontSize = 40.sp)
                Spacer(Modifier.height(14.dp))
                Text("বুঝেছি!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                Text(
                    "আপনার লেভেল: $placedLabel", fontSize = 14.sp, fontFamily = NotoSansBengali,
                    color = Color(0xFF6366F1), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "স্টেজ $placedStage থেকে শুরু করাচ্ছি — যাতে সময় নষ্ট না হয়",
                    fontSize = 12.5.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onComplete, shape = RoundedCornerShape(14.dp)) {
                    Text("▶ শুরু করি", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
