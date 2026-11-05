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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun WrittenScreen(nav: NavController, vm: VisionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "লিখিত অনুশীলন",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        for (prompt in VisionData.writtenPrompts) {
            val topicName = VisionData.topics.find { it.id == prompt.topicId }?.name
                ?: prompt.topicId
            Card(
                onClick = { nav.navigate("writtenDetail/${prompt.id}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = topicName)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "নম্বর: ${prompt.marks}")
                }
            }
        }
    }
}

@Composable
fun WrittenDetailScreen(nav: NavController, vm: VisionViewModel, promptId: String) {
    val prompt = VisionData.writtenPrompts.find { it.id == promptId }
    if (prompt == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "এই লিখিত প্রম্পটটি পাওয়া যায়নি।")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { nav.popBackStack() }) {
                Text(text = "ফিরে যান")
            }
        }
        return
    }

    val totalSeconds = prompt.marks * 5 * 60
    var remainingSeconds by remember { mutableStateOf(totalSeconds) }
    var draft by remember { mutableStateOf("") }
    var checkedPoints by remember {
        mutableStateOf(List(prompt.modelPoints.size) { false })
    }
    var note by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(prompt.id) {
        var left = totalSeconds
        while (left > 0) {
            delay(1000L)
            left -= 1
            remainingSeconds = left
        }
    }

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
            text = prompt.title,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (remainingSeconds > 0) {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            val secondsText = if (seconds < 10) "0$seconds" else "$seconds"
            Text(text = "অনুশীলনের সময়: $minutes:$secondsText বাকি")
        } else {
            Text(text = "সময় শেষ — তবুও লিখতে পারেন")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = prompt.prompt,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "আপনার উত্তর লিখুন",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "মডেল উত্তর ও রুব্রিক (নিজে যাচাই করুন)",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "এটা গ্রেডিং নয়, নিজের যাচাই")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = prompt.modelAnswer)
        Spacer(modifier = Modifier.height(12.dp))
        for (index in prompt.modelPoints.indices) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checkedPoints[index],
                    onCheckedChange = { isChecked ->
                        val updated = checkedPoints.toMutableList()
                        updated[index] = isChecked
                        checkedPoints = updated
                    }
                )
                Text(text = prompt.modelPoints[index])
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "নিজের নোট",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val checkedIndices = checkedPoints.mapIndexedNotNull { index, isChecked ->
                    if (isChecked) index else null
                }
                vm.saveWrittenCheck(
                    VisionWrittenCheck(
                        promptId = prompt.id,
                        checkedPoints = checkedIndices,
                        note = note,
                        dateMs = System.currentTimeMillis()
                    )
                )
                vm.markDayActive()
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "সংরক্ষণ করুন")
        }
        if (saved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "সংরক্ষিত হয়েছে ✓")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { nav.popBackStack() }) {
            Text(text = "ফিরে যান")
        }
    }
}
