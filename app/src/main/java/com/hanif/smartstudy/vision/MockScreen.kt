package com.hanif.smartstudy.vision

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun MockScreen(nav: NavController, vm: VisionViewModel) {
    val questions = remember { vm.startMock() }

    if (questions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "এই মুহূর্তে কোনো মক প্রশ্ন নেই",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { nav.popBackStack() }) {
                Text(text = "ফিরে যান")
            }
        }
        return
    }

    val totalSeconds = questions.size * 60
    var remaining by remember { mutableStateOf(totalSeconds) }
    var index by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var guessed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var timeSpent by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var questionStartMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var submitted by remember { mutableStateOf(false) }

    fun flushCurrentTime(): Map<String, Int> {
        val elapsed = ((System.currentTimeMillis() - questionStartMillis) / 1000)
            .toInt()
            .coerceAtLeast(0)
        val currentId = questions[index].id
        val updated = timeSpent.toMutableMap()
        updated[currentId] = (updated[currentId] ?: 0) + elapsed
        timeSpent = updated
        return updated
    }

    fun doSubmit() {
        if (submitted) {
            return
        }
        submitted = true
        val finalTimes = flushCurrentTime()
        val secondsPerQ = questions.associate { question ->
            question.id to (finalTimes[question.id] ?: 0)
        }
        vm.submitMock(questions, answers, guessed, secondsPerQ)
        vm.markDayActive()
        nav.navigate("mockResult")
    }

    LaunchedEffect(Unit) {
        while (!submitted && remaining > 0) {
            delay(1000)
            if (!submitted) {
                remaining -= 1
            }
        }
        if (!submitted && remaining == 0) {
            doSubmit()
        }
    }

    val q = questions[index]
    val minutes = remaining / 60
    val seconds = remaining % 60
    val timeText = "%02d:%02d".format(minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = { doSubmit() }) {
                Text(text = "জমা দিন")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            questions.forEachIndexed { questionIndex, question ->
                val isAnswered = answers.containsKey(question.id)
                Button(
                    onClick = {
                        flushCurrentTime()
                        index = questionIndex
                        questionStartMillis = System.currentTimeMillis()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAnswered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (isAnswered) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                ) {
                    Text(text = "${questionIndex + 1}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${index + 1} / ${questions.size}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = q.question,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        q.options.forEachIndexed { optionIndex, optionText ->
            val isSelected = answers[q.id] == optionIndex
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = {
                    val updated = answers.toMutableMap()
                    updated[q.id] = optionIndex
                    answers = updated
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                Text(text = optionText)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        val isGuessed = guessed.contains(q.id)
        Button(
            onClick = {
                guessed = if (isGuessed) {
                    guessed - q.id
                } else {
                    guessed + q.id
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGuessed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isGuessed) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        ) {
            Text(text = if (isGuessed) "✓ আন্দাজে দিয়েছি" else "আন্দাজে দিয়েছি")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = index > 0,
                onClick = {
                    flushCurrentTime()
                    index -= 1
                    questionStartMillis = System.currentTimeMillis()
                }
            ) {
                Text(text = "আগেরটি")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = index < questions.size - 1,
                onClick = {
                    flushCurrentTime()
                    index += 1
                    questionStartMillis = System.currentTimeMillis()
                }
            ) {
                Text(text = "পরেরটি")
            }
        }
    }
}

@Composable
fun MockResultScreen(nav: NavController, vm: VisionViewModel) {
    val attempt = vm.lastMock

    if (attempt == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "কোনো মক ফলাফল পাওয়া যায়নি",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { nav.popBackStack() }) {
                Text(text = "ফিরে যান")
            }
        }
        return
    }

    val percent = if (attempt.total > 0) attempt.correct * 100 / attempt.total else 0
    val message = when {
        percent < 40 -> "ধীরে ধীরে এগোন — এই টপিকগুলো আগে ঠিক করুন"
        percent <= 69 -> "ভালো অগ্রগতি — দুর্বল জায়গাগুলোতে আরো একটু কাজ করুন"
        else -> "অভিনন্দন — এই ধারাবাহিকতা ধরে রাখুন"
    }
    val weak = vm.weakTopics().take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "মক ফলাফল",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "স্কোর: ${attempt.correct} / ${attempt.total} ($percent%)",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
        if (percent < 40) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "দুর্বল টপিকগুলো নিচে দেওয়া আছে — সেগুলো থেকে অনুশীলন শুরু করুন।",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "আন্দাজে দেওয়া উত্তর: ${attempt.guessedCount}টি",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "গড় সময়: ${attempt.avgSeconds} সেকেন্ড",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "টপিক অনুযায়ী ফলাফল",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        attempt.perTopic.forEach { score ->
            val topicName = VisionData.topics.firstOrNull { it.id == score.topicId }?.name
                ?: score.topicId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = topicName)
                Text(text = "${score.correct} / ${score.total}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "পরের ৩টি দুর্বল টপিক",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (weak.isEmpty()) {
            Text(text = "কোনো দুর্বল টপিক পাওয়া যায়নি")
        } else {
            weak.forEach { topic ->
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = { nav.navigate("practice/${topic.id}") }
                ) {
                    Text(text = topic.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { nav.navigate("today") }
        ) {
            Text(text = "আজকের পাতা")
        }
    }
}
