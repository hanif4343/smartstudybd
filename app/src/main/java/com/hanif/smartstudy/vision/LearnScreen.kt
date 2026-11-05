package com.hanif.smartstudy.vision

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun LearnScreen(nav: NavController, vm: VisionViewModel) {
    val grouped = VisionData.topics.groupBy { it.group }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "সিলেবাস ধরে পড়ুন",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "টপিক ধরে পড়ুন — সালের পেপার নয়, বিষয়ই আসল",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))

        for ((group, topics) in grouped) {
            Text(
                text = group,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (topic in topics) {
                val masteryPercent = (vm.mastery(topic.id) * 100).toInt()
                Card(
                    onClick = { nav.navigate("practice/${topic.id}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = topic.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = topic.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            VisionProgressBar(
                                progress = vm.mastery(topic.id),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(text = "$masteryPercent%", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (vm.attempted(topic.id)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "চেষ্টা করা হয়েছে",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { nav.navigate("papers") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "আসল প্রশ্নপত্র দেখুন")
        }
    }
}
