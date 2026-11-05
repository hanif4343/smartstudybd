package com.hanif.smartstudy.vision

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun PapersScreen(nav: NavController, vm: VisionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "মূল প্রশ্নপত্র",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        for (paper in VisionData.papers) {
            val totalQuestions = paper.sections.sumOf { it.items.size }
            Card(
                onClick = { nav.navigate("paperDetail/${paper.id}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = paper.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${paper.year}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "বিভাগ: ${paper.sections.size} • প্রশ্ন: $totalQuestions")
                }
            }
        }
    }
}

@Composable
fun PaperDetailScreen(nav: NavController, vm: VisionViewModel, paperId: String) {
    val paper = VisionData.papers.find { it.id == paperId }
    if (paper == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "এই প্রশ্নপত্রটি পাওয়া যায়নি।")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { nav.popBackStack() }) {
                Text(text = "ফিরে যান")
            }
        }
        return
    }

    var showAnswers by remember { mutableStateOf(false) }
    var questionNumber = 0
    val optionPrefixes = listOf("ক", "খ", "গ", "ঘ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Button(onClick = { nav.popBackStack() }) {
            Text(text = "ফিরে যান")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = paper.title,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${paper.year}")
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            if (showAnswers) {
                OutlinedButton(
                    onClick = { showAnswers = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "শুধু প্রশ্ন")
                }
            } else {
                Button(
                    onClick = { showAnswers = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "শুধু প্রশ্ন")
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (showAnswers) {
                Button(
                    onClick = { showAnswers = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "উত্তরসহ")
                }
            } else {
                OutlinedButton(
                    onClick = { showAnswers = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "উত্তরসহ")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        for (section in paper.sections) {
            Text(
                text = section.heading,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (item in section.items) {
                questionNumber += 1
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(text = "$questionNumber. ${item.question}")
                    Spacer(modifier = Modifier.height(4.dp))
                    for (optionIndex in item.options.indices) {
                        val prefix = if (optionIndex < optionPrefixes.size) {
                            optionPrefixes[optionIndex]
                        } else {
                            "${optionIndex + 1}"
                        }
                        Text(text = "$prefix. ${item.options[optionIndex]}")
                    }
                    if (showAnswers && item.answerIndex in item.options.indices) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "উ. ${item.options[item.answerIndex]}")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
