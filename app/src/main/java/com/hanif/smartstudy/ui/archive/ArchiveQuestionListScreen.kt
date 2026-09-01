package com.hanif.smartstudy.ui.archive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.ArchiveQuestion
import com.hanif.smartstudy.data.model.TopicRef
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.ArchiveViewModel

/* ─────────────────────────────────────────────────────────────────────────
   একটা Archive টপিকের প্রশ্ন-পেজ — A-Z Sort / প্রতি-প্রশ্ন Duplicate checkbox /
   Mark All / Move to Active। ডিলিট কোনো বাটনেই নেই (ইচ্ছাকৃত — প্ল্যান দ্রঃ)।
   ───────────────────────────────────────────────────────────────────────── */

@Composable
fun ArchiveQuestionListScreen(
    viewModel : ArchiveViewModel,
    onBack    : () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showMoveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.selectedTopic?.name ?: "", fontFamily = NotoSansBengali,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1
                            )
                            Text(
                                "${state.questions.size} টা লোড হয়েছে" + if (state.total > 0) " / মোট ~${state.total}" else "",
                                fontFamily = NotoSansBengali, fontSize = 11.sp, color = Color.Gray
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSort() }) {
                            Icon(
                                Icons.Default.SortByAlpha, contentDescription = "A-Z Sort",
                                tint = if (state.isSorted) Color(0xFFF59E0B) else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFFFBEB))
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.toggleMarkAll() }, enabled = state.questions.isNotEmpty()) {
                        Text(
                            if (state.duplicateIds.isNotEmpty() && state.duplicateIds.containsAll(state.questions.map { it.id }))
                                "☑️ সব আনসিলেক্ট" else "☐ Mark All (ডুপ্লিকেট)",
                            fontFamily = NotoSansBengali, fontSize = 12.sp
                        )
                    }
                    Text(
                        "${state.duplicateIds.size} টা duplicate মার্ক করা",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color.Gray
                    )
                }
                Divider()
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Button(
                    onClick  = { showMoveDialog = true },
                    enabled  = state.questions.isNotEmpty() && !state.isBusy,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        val moveCount = state.questions.size - state.duplicateIds.size
                        Text(
                            "Move to Active ($moveCount টা ভালো" +
                                    if (state.duplicateIds.isNotEmpty()) " + ${state.duplicateIds.size} টা duplicate মার্ক)" else ")",
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            state.error?.let { err ->
                Surface(color = Color(0xFFFEE2E2), modifier = Modifier.fillMaxWidth()) {
                    Text(err, color = Color(0xFFB91C1C), fontSize = 12.sp, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(10.dp))
                }
            }
            state.message?.let { msg ->
                Surface(color = Color(0xFFD1FAE5), modifier = Modifier.fillMaxWidth()) {
                    Text(msg, color = Color(0xFF065F46), fontSize = 12.sp, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(10.dp))
                }
            }

            if (state.isLoadingQuestions && state.questions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFF59E0B))
                }
                return@Scaffold
            }

            if (state.questions.isEmpty() && !state.isLoadingQuestions) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("এই টপিকে আর রিভিউ করার কিছু নেই 🎉", fontFamily = NotoSansBengali, color = Color.Gray)
                }
                return@Scaffold
            }

            LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                items(state.questions, key = { it.id }) { q ->
                    ArchiveQuestionCard(
                        q          = q,
                        isMarked   = q.id in state.duplicateIds,
                        onToggle   = { viewModel.toggleDuplicate(q.id) }
                    )
                }
                if (state.hasMore && !state.isSorted) {
                    item {
                        TextButton(
                            onClick = { viewModel.loadNextPage() },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            enabled = !state.isLoadingQuestions
                        ) {
                            Text(
                                if (state.isLoadingQuestions) "লোড হচ্ছে..." else "আরও ৫০টা লোড করো (একই পেজে যোগ হবে)",
                                fontFamily = NotoSansBengali
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showMoveDialog) {
        MoveToActiveDialog(
            sheetLabel  = state.sheet.label,
            subjects    = state.subjects,
            activeTopics= state.activeTopics,
            defaultSubjectId = state.selectedTopic?.subjectId,
            onDismiss   = { showMoveDialog = false },
            onConfirm   = { subject, topicName ->
                showMoveDialog = false
                viewModel.finishPage(subject, topicName)
            }
        )
    }
}

@Composable
private fun ArchiveQuestionCard(q: ArchiveQuestion, isMarked: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (isMarked) Color(0xFFFEE2E2) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = isMarked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                if (q.srl > 0) {
                    Text("#${q.srl}", fontFamily = NotoSansBengali, fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                }
                Text(q.question, fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                listOf(q.option1, q.option2, q.option3, q.option4).forEachIndexed { idx, opt ->
                    if (opt.isNotBlank()) {
                        val isCorrect = opt.trim() == q.correct.trim() || (idx + 1).toString() == q.correct.trim()
                        Text(
                            "${'A' + idx}. $opt",
                            fontFamily = NotoSansBengali, fontSize = 12.sp,
                            color = if (isCorrect) Color(0xFF059669) else Color.DarkGray,
                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                if (isMarked) {
                    Text("✕ Duplicate/সমস্যা হিসেবে মার্ক করা", fontFamily = NotoSansBengali, fontSize = 11.sp, color = Color(0xFFB91C1C))
                }
            }
        }
    }
}

@Composable
private fun MoveToActiveDialog(
    sheetLabel        : String,
    subjects          : List<com.hanif.smartstudy.data.model.SubjectRef>,
    activeTopics      : List<TopicRef>,
    defaultSubjectId  : String?,
    onDismiss         : () -> Unit,
    onConfirm         : (subject: String, topic: String) -> Unit
) {
    var selectedSubject by remember {
        mutableStateOf(subjects.firstOrNull { it.subjectId == defaultSubjectId } ?: subjects.firstOrNull())
    }
    var subjectExpanded by remember { mutableStateOf(false) }
    var topicExpanded   by remember { mutableStateOf(false) }
    var selectedTopicName by remember { mutableStateOf<String?>(null) }
    var isNewTopic by remember { mutableStateOf(false) }
    var newTopicText by remember { mutableStateOf("") }

    val topicsForSubject = remember(selectedSubject, activeTopics) {
        activeTopics.filter { it.subjectId == selectedSubject?.subjectId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Active $sheetLabel-এ Move করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Subject", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color.Gray)
                ExposedDropdownMenuBox(expanded = subjectExpanded, onExpandedChange = { subjectExpanded = it }) {
                    OutlinedTextField(
                        value = selectedSubject?.name ?: "নির্বাচন করুন",
                        onValueChange = {}, readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) }
                    )
                    ExposedDropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                        subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name ?: s.subjectId ?: "?", fontFamily = NotoSansBengali) },
                                onClick = { selectedSubject = s; selectedTopicName = null; subjectExpanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Topic", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color.Gray)

                if (!isNewTopic) {
                    ExposedDropdownMenuBox(expanded = topicExpanded, onExpandedChange = { topicExpanded = it }) {
                        OutlinedTextField(
                            value = selectedTopicName ?: "নির্বাচন করুন",
                            onValueChange = {}, readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicExpanded) }
                        )
                        ExposedDropdownMenu(expanded = topicExpanded, onDismissRequest = { topicExpanded = false }) {
                            topicsForSubject.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.name ?: t.topicId ?: "?", fontFamily = NotoSansBengali) },
                                    onClick = { selectedTopicName = t.name; topicExpanded = false }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("+ নতুন Topic লিখুন", fontFamily = NotoSansBengali, color = Color(0xFFF59E0B)) },
                                onClick = { isNewTopic = true; topicExpanded = false }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newTopicText, onValueChange = { newTopicText = it },
                        label = { Text("নতুন Topic নাম", fontFamily = NotoSansBengali) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { isNewTopic = false; newTopicText = "" }) {
                        Text("existing Topic থেকে বাছাই করুন", fontFamily = NotoSansBengali, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            val topicFinal = if (isNewTopic) newTopicText.trim() else (selectedTopicName ?: "")
            Button(
                onClick = { selectedSubject?.name?.let { onConfirm(it, topicFinal) } },
                enabled = selectedSubject != null && topicFinal.isNotBlank()
            ) { Text("Confirm", fontFamily = NotoSansBengali) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
    )
}
