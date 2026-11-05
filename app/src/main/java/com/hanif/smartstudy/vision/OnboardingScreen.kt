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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.util.Calendar
import java.util.Locale

private fun defaultExamDateMs(): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, 180)
    return calendar.timeInMillis
}

private fun parseExamDateMs(text: String): Long {
    if (text.isBlank()) {
        return defaultExamDateMs()
    }
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.isLenient = false
        format.parse(text.trim())?.time ?: defaultExamDateMs()
    } catch (e: Exception) {
        defaultExamDateMs()
    }
}

@Composable
fun OnboardingScreen(nav: NavController, vm: VisionViewModel) {
    var exam by remember { mutableStateOf("বিসিএস") }
    var dateText by remember { mutableStateOf("") }
    var dailyMinutes by remember { mutableStateOf(30) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "আপনার পরীক্ষার কোচ",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "প্রতিদিন অল্প অল্প করে, সিলেবাস ধরে এগিয়ে যান।",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "পরীক্ষার নাম", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = exam,
            onValueChange = { exam = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "পরীক্ষার তারিখ (YYYY-MM-DD)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(text = "YYYY-MM-DD") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "প্রতিদিন কত মিনিট পড়বেন?", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (minutes in listOf(15, 30, 45, 60)) {
                FilterChip(
                    selected = dailyMinutes == minutes,
                    onClick = { dailyMinutes = minutes },
                    label = { Text(text = "$minutes") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val examDateMs = parseExamDateMs(dateText)
                val profile = VisionProfile(
                    exam = exam.ifBlank { "বিসিএস" },
                    examDateMs = examDateMs,
                    dailyMinutes = dailyMinutes,
                    baselineDone = false,
                    baselineCorrect = 0,
                    baselineTotal = 0
                )
                vm.saveProfile(profile)
                nav.navigate("diagnostic")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "শুরু করুন")
        }
    }
}

@Composable
fun DiagnosticScreen(nav: NavController, vm: VisionViewModel) {
    val questions = remember {
        VisionData.diagnosticIds.mapNotNull { id ->
            VisionData.mcqs.find { it.id == id }
        }
    }
    var index by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        if (questions.isEmpty()) {
            Text(
                text = "ডায়াগনস্টিক প্রশ্ন পাওয়া যায়নি",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    nav.navigate("today") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "আজকের পাতায় যান")
            }
        } else {
        val question = questions[index]
        Text(
            text = "প্রশ্ন ${index + 1} / ${questions.size}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = question.question,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(20.dp))

        for ((optionIndex, option) in question.options.withIndex()) {
            Button(
                onClick = {
                    val updated = answers + (question.id to (optionIndex == question.answerIndex))
                    answers = updated
                    if (index < questions.size - 1) {
                        index += 1
                    } else {
                        val correct = updated.values.count { it }
                        val base = vm.profile ?: VisionProfile(
                            exam = "বিসিএস",
                            examDateMs = defaultExamDateMs(),
                            dailyMinutes = 30,
                            baselineDone = false,
                            baselineCorrect = 0,
                            baselineTotal = 0
                        )
                        vm.saveProfile(
                            base.copy(
                                baselineDone = true,
                                baselineCorrect = correct,
                                baselineTotal = questions.size
                            )
                        )
                        vm.markDayActive()
                        nav.navigate("today") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(text = option, style = MaterialTheme.typography.bodyLarge)
            }
        }
        }
    }
}
