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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(nav: NavController, vm: VisionViewModel) {
    val profile = vm.profile
    var selectedMinutes by remember { mutableStateOf(profile?.dailyMinutes ?: 30) }

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
    val readinessPercent = (vm.readiness() * 100).toInt()
    val streak = vm.streakDays()
    val mockHistory = vm.mockHistory()
    val writtenCount = vm.writtenChecks().size
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(text = profile.exam, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "পরীক্ষার বাকি $daysLeft দিন", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "প্রতিদিন ${profile.dailyMinutes} মিনিট",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "প্রস্তুতি $readinessPercent%", style = MaterialTheme.typography.bodyLarge)
        Text(text = "টানা $streak দিন", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "টপিকভিত্তিক দক্ষতা", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        for (topic in VisionData.topics) {
            val masteryPercent = (vm.mastery(topic.id) * 100).toInt()
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)) {
                Text(text = topic.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "$masteryPercent%", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "মক টেস্টের ইতিহাস", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        if (mockHistory.isEmpty()) {
            Text(text = "এখনো কোনো মক টেস্ট দেওয়া হয়নি", style = MaterialTheme.typography.bodyLarge)
        }
        for (attempt in mockHistory) {
            val percent = if (attempt.total > 0) attempt.correct * 100 / attempt.total else 0
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)) {
                Text(
                    text = dateFormat.format(Date(attempt.dateMs)),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${attempt.correct}/${attempt.total} • $percent%",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "লিখিত অনুশীলন: ${writtenCount}টি",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "প্রতিদিন কত মিনিট পড়বেন?", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (minutes in listOf(15, 30, 45, 60)) {
                FilterChip(
                    selected = selectedMinutes == minutes,
                    onClick = { selectedMinutes = minutes },
                    label = { Text(text = "$minutes") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { vm.saveProfile(profile.copy(dailyMinutes = selectedMinutes)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "সংরক্ষণ")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { nav.navigate("papers") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "আসল প্রশ্নপত্র")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { nav.navigate("written") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "লিখিত অনুশীলন")
        }
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Pro ভাবনা", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "এই ভিশন সংস্করণে সবকিছু খোলা আছে। পরে বিস্তারিত বিশ্লেষণ, লাইভ মক ও লিখিত রিভিউ পেইড হতে পারে — তবে এখানে কোনো পেমেন্ট কোড বা ভুয়া পেওয়াল নেই।",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                vm.resetAll()
                nav.navigate("onboarding") {
                    popUpTo("today") { inclusive = true }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "সব ডেটা মুছে ফেলুন")
        }
    }
}
