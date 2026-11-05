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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun TodayScreen(nav: NavController, vm: VisionViewModel) {
    LaunchedEffect(Unit) {
        vm.markDayActive()
    }

    val profile = vm.profile
    if (profile == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "প্রোফাইল পাওয়া যায়নি",
                style = MaterialTheme.typography.titleLarge
            )
        }
        return
    }

    val daysLeft = ((profile.examDateMs - System.currentTimeMillis()) / 86400000L)
        .toInt()
        .coerceAtLeast(0)
    val streak = vm.streakDays()
    val readiness = vm.readiness()
    val readinessPercent = (readiness * 100).toInt()
    val mission = vm.todayMission()
    val dueCount = vm.revisionQueue().size
    val weak = vm.weakTopics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "আসসালামু আলাইকুম",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = profile.exam,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "পরীক্ষার বাকি $daysLeft দিন", style = MaterialTheme.typography.bodyLarge)
        Text(text = "টানা $streak দিন", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "প্রস্তুতি $readinessPercent%", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        VisionProgressBar(progress = readiness)
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "আজকের মিশন", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "নতুন প্রশ্ন", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "${mission.first} / 10", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "রিভিশন", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "${mission.second} / $dueCount", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "রিভিশন বাকি: $dueCountটি",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "মক টেস্ট", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (mission.third > 0) "সম্পন্ন" else "বাকি আছে",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { nav.navigate("practice/all") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "নতুন প্রশ্ন শুরু")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { nav.navigate("practice/revision") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "রিভিশন শুরু")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { nav.navigate("mock") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "মক টেস্ট")
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "দুর্বল টপিক", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        if (weak.isEmpty()) {
            Text(
                text = "সব টপিকেই ভালো অগ্রগতি!",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        for (topic in weak) {
            val masteryPercent = (vm.mastery(topic.id) * 100).toInt()
            Card(
                onClick = { nav.navigate("practice/${topic.id}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Text(text = topic.name, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "$masteryPercent%", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (mission.first + mission.second + mission.third > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "চালিয়ে যান", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "চালিয়ে যান — আজকের অগ্রগতি ধরে রাখুন, অল্প অল্প করে এগিয়ে যান।",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
