package com.hanif.smartstudy.ui.typing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.delay

private val Indigo600 = Color(0xFF4F46E5)
private val GreenOk   = Color(0xFF10B981)
private val RedWrong  = Color(0xFFEF4444)
private val SlateText = Color(0xFF1E293B)
private val MutedText = Color(0xFF64748B)
private val CardBg    = Color(0xFFFFFFFF)

private val PASSAGES = listOf(
    "বাংলাদেশ একটি সুন্দর দেশ। এখানে অনেক নদী আছে। পদ্মা, মেঘনা, যমুনা প্রধান নদী।",
    "বিজ্ঞান মানুষের জীবনকে সহজ করেছে। প্রযুক্তির উন্নয়নে পৃথিবী পরিবর্তন হচ্ছে দিন দিন।",
    "শিক্ষা জাতির মেরুদণ্ড। একটি শিক্ষিত জাতি সকল প্রতিকূলতা অতিক্রম করতে পারে।",
    "The quick brown fox jumps over the lazy dog. Practice typing every day to improve your speed.",
    "Science and technology have transformed human civilization in remarkable ways over centuries.",
    "Reading books regularly improves vocabulary, concentration, and overall mental development.",
)

data class TypingResult(
    val wpm         : Int,
    val accuracy    : Int,
    val timeSec     : Int,
    val correctChars: Int,
    val totalChars  : Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingPracticeScreen(
    bestWpm   : Int = 0,
    onBack    : () -> Unit,
    onResult  : (TypingResult) -> Unit = {}
) {
    var passageIndex by remember { mutableStateOf(0) }
    var passage      by remember { mutableStateOf(PASSAGES[0]) }
    var userInput    by remember { mutableStateOf("") }
    var isStarted    by remember { mutableStateOf(false) }
    var isFinished   by remember { mutableStateOf(false) }
    var elapsedSec   by remember { mutableStateOf(0) }
    var result       by remember { mutableStateOf<TypingResult?>(null) }

    // Timer
    LaunchedEffect(isStarted, isFinished) {
        if (isStarted && !isFinished) {
            while (true) {
                delay(1000)
                elapsedSec++
            }
        }
    }

    // Check completion
    LaunchedEffect(userInput) {
        if (isStarted && userInput.length >= passage.length && !isFinished) {
            isFinished = true
            val timeSec = elapsedSec.coerceAtLeast(1)
            val words   = passage.trim().split(" ").size
            val wpm     = (words * 60 / timeSec)
            val correct = userInput.zip(passage).count { (a, b) -> a == b }
            val acc     = if (passage.isNotEmpty()) correct * 100 / passage.length else 0
            val r = TypingResult(wpm, acc, timeSec, correct, passage.length)
            result = r
            onResult(r)
        }
    }

    // Start timer on first keypress
    fun onInputChange(new: String) {
        if (!isStarted && new.isNotEmpty()) isStarted = true
        if (!isFinished) userInput = new.take(passage.length)
    }

    fun reset(newPassageIdx: Int = passageIndex) {
        passage      = PASSAGES[newPassageIdx % PASSAGES.size]
        userInput    = ""
        isStarted    = false
        isFinished   = false
        elapsedSec   = 0
        result       = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⌨️ Typing Practice", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg),
                actions = {
                    // Best WPM badge
                    if (bestWpm > 0) {
                        Box(
                            Modifier.padding(end = 12.dp)
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("🏆 $bestWpm WPM", fontSize = 11.sp, color = GreenOk,
                                fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Stats row
            StatsRow(
                elapsedSec  = elapsedSec,
                userInput   = userInput,
                passage     = passage,
                isStarted   = isStarted
            )

            // Passage display
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(Color(0xFFFAFAFF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E7FF))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("📖 Passage", fontSize = 11.sp, color = MutedText,
                        fontFamily = NotoSansBengali, modifier = Modifier.padding(bottom = 8.dp))

                    // Colored passage showing correct/wrong chars
                    Text(
                        buildAnnotatedString {
                            passage.forEachIndexed { i, ch ->
                                val style = when {
                                    i >= userInput.length -> SpanStyle(color = SlateText)
                                    userInput[i] == ch   -> SpanStyle(color = GreenOk, background = Color(0xFFDCFCE7))
                                    else                 -> SpanStyle(color = RedWrong, background = Color(0xFFFEE2E2))
                                }
                                withStyle(style) { append(ch) }
                            }
                        },
                        fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        lineHeight = 26.sp, letterSpacing = 0.3.sp
                    )
                }
            }

            // Input field
            OutlinedTextField(
                value         = if (isFinished) userInput else userInput,
                onValueChange = { if (!isFinished) onInputChange(it) },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text(
                    if (!isStarted) "এখানে type করা শুরু করুন..." else "টাইপ চলছে...",
                    fontFamily = NotoSansBengali
                )},
                shape         = RoundedCornerShape(14.dp),
                enabled       = !isFinished,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo600,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.None
                ),
                minLines = 3
            )

            // Hint: timer starts on typing
            if (!isStarted) {
                Text("💡 Type করলেই Timer শুরু হবে",
                    fontSize = 11.sp, color = MutedText, fontFamily = NotoSansBengali,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            // Result card
            AnimatedVisibility(visible = isFinished && result != null) {
                result?.let { r ->
                    ResultCard(r, bestWpm,
                        onRetry = { reset() },
                        onNextPassage = {
                            passageIndex = (passageIndex + 1) % PASSAGES.size
                            reset(passageIndex)
                        }
                    )
                }
            }

            // Passage selector
            if (!isStarted) {
                Text("📝 Passage বেছে নিন:", fontSize = 12.sp, color = MutedText,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                PASSAGES.forEachIndexed { i, p ->
                    OutlinedButton(
                        onClick = { passageIndex = i; reset(i) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (passageIndex == i) Color(0xFFEEF2FF) else Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (passageIndex == i) Indigo600 else Color(0xFFE2E8F0)
                        )
                    ) {
                        Text(
                            "${i + 1}. ${p.take(40)}...",
                            fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = if (passageIndex == i) Indigo600 else SlateText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(elapsedSec: Int, userInput: String, passage: String, isStarted: Boolean) {
    val mins = elapsedSec / 60
    val secs = elapsedSec % 60
    val progress = if (passage.isNotEmpty()) userInput.length.toFloat() / passage.length else 0f
    val liveWpm = if (elapsedSec > 0 && isStarted) {
        val words = userInput.trim().split(" ").size
        words * 60 / elapsedSec
    } else 0

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color(0xFF1E1B4B))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox("⏱️", "%02d:%02d".format(mins, secs), "সময়", Color(0xFF60A5FA))
                StatBox("⌨️", "$liveWpm", "WPM", Color(0xFF4ADE80))
                StatBox("📊", "${(progress * 100).toInt()}%", "Progress", Color(0xFFA78BFA))
                StatBox("📝", "${userInput.length}/${passage.length}", "অক্ষর", Color(0xFFFBBF24))
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Indigo600, trackColor = Color.White.copy(0.2f)
            )
        }
    }
}

@Composable
private fun StatBox(icon: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 14.sp)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

@Composable
private fun ResultCard(
    result       : TypingResult,
    bestWpm      : Int,
    onRetry      : () -> Unit,
    onNextPassage: () -> Unit
) {
    val isNewBest = result.wpm > bestWpm
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            if (isNewBest) Color(0xFF1E1B4B) else CardBg
        ),
        border = if (isNewBest) androidx.compose.foundation.BorderStroke(
            2.dp, Brush.horizontalGradient(listOf(Color(0xFFFBBF24), Color(0xFFF59E0B)))
        ) else null,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            if (isNewBest) {
                Text("🏆 নতুন Record!", fontSize = 14.sp, color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            }

            Text("${result.wpm} WPM",
                fontSize = 42.sp, fontWeight = FontWeight.ExtraBold,
                color = if (isNewBest) Color.White else Indigo600, fontFamily = NotoSansBengali)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ResultStat("✅ Accuracy", "${result.accuracy}%",
                    if (result.accuracy >= 90) GreenOk else if (result.accuracy >= 70) Color(0xFFF59E0B) else RedWrong,
                    isNewBest)
                ResultStat("⏱️ সময়", "${result.timeSec}s", Color(0xFF60A5FA), isNewBest)
                ResultStat("📝 সঠিক", "${result.correctChars}/${result.totalChars}", GreenOk, isNewBest)
            }

            val grade = when {
                result.wpm >= 60 && result.accuracy >= 95 -> "S" to "⚡ Excellent!"
                result.wpm >= 40 && result.accuracy >= 90 -> "A" to "👍 Very Good!"
                result.wpm >= 25 && result.accuracy >= 80 -> "B" to "📈 Good!"
                result.wpm >= 15 -> "C" to "💪 Keep Practicing!"
                else -> "D" to "🔄 Try Again!"
            }
            Text("${grade.first} Grade — ${grade.second}", fontSize = 13.sp,
                color = if (isNewBest) Color(0xFF94A3B8) else MutedText,
                fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("🔄 আবার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
                Button(onClick = onNextPassage, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)) {
                    Text("➡️ পরের Passage", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, color: Color, dark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = if (dark) Color(0xFF94A3B8) else MutedText, fontFamily = NotoSansBengali)
    }
}
