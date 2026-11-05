package com.hanif.smartstudy.vision

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun PracticeScreen(nav: NavController, vm: VisionViewModel, topicId: String) {
    val queue = remember(topicId) {
        if (topicId == "revision") vm.revisionQueue()
        else vm.newQueue(if (topicId == "all") null else topicId)
    }

    if (queue.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "এই মুহূর্তে কোনো প্রশ্ন নেই",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { nav.popBackStack() }) {
                Text(text = "ফিরে যান")
            }
        }
        return
    }

    var index by remember { mutableStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var confidenceAsked by remember { mutableStateOf(false) }
    var confidence by remember { mutableStateOf<Boolean?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var correctCount by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var missionMarked by remember { mutableStateOf(false) }

    if (finished) {
        val total = queue.size
        val percent = if (total > 0) correctCount * 100 / total else 0
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "অনুশীলন শেষ",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "স্কোর: $correctCount / $total ($percent%)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { nav.navigate("today") }
            ) {
                Text(text = "আজকের পাতা")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    index = 0
                    selectedOption = null
                    confidenceAsked = false
                    confidence = null
                    revealed = false
                    recorded = false
                    correctCount = 0
                    finished = false
                    missionMarked = false
                }
            ) {
                Text(text = "আবার")
            }
        }
        return
    }

    val q = queue[index]
    val isLast = index == queue.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "${index + 1} / ${queue.size}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = q.question,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        q.options.forEachIndexed { optionIndex, optionText ->
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                enabled = selectedOption == null,
                onClick = { selectedOption = optionIndex }
            ) {
                Text(text = optionText)
            }
        }

        if (selectedOption != null && !confidenceAsked && !revealed) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "আপনি কতটা নিশ্চিত?",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        confidence = true
                        confidenceAsked = true
                        revealed = true
                        if (!recorded) {
                            val correct = selectedOption == q.answerIndex
                            vm.recordPracticeAnswer(q, correct, true)
                            recorded = true
                            if (correct) {
                                correctCount += 1
                            }
                        }
                    }
                ) {
                    Text(text = "নিশ্চিত")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        confidence = false
                        confidenceAsked = true
                        revealed = true
                        if (!recorded) {
                            val correct = selectedOption == q.answerIndex
                            vm.recordPracticeAnswer(q, correct, false)
                            recorded = true
                            if (correct) {
                                correctCount += 1
                            }
                        }
                    }
                ) {
                    Text(text = "আন্দাজে")
                }
            }
        }

        if (revealed) {
            val correct = selectedOption == q.answerIndex
            Spacer(modifier = Modifier.height(16.dp))
            if (correct) {
                Text(
                    text = "সঠিক উত্তর",
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Text(
                    text = "ভুল উত্তর — সঠিক উত্তর: ${q.options.getOrNull(q.answerIndex) ?: ""}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = q.shortExplain)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = q.deepExplain)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = q.trapNote)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isLast) {
                        if (!missionMarked) {
                            if (topicId == "revision") {
                                vm.markMissionRevision()
                            } else {
                                vm.markMissionNew()
                            }
                            vm.markDayActive()
                            missionMarked = true
                        }
                        finished = true
                    } else {
                        index += 1
                        selectedOption = null
                        confidenceAsked = false
                        confidence = null
                        revealed = false
                        recorded = false
                    }
                }
            ) {
                Text(text = if (isLast) "শেষ করুন" else "পরের প্রশ্ন")
            }
        }
    }
}
