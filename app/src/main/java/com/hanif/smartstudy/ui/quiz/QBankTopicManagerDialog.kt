package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hanif.smartstudy.data.repository.ContentRepository
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.QBankMovedStore
import com.hanif.smartstudy.util.TopicOrdering
import com.hanif.smartstudy.viewmodel.QuizViewModel

/**
 * ── QBank টপিক ম্যানেজার (শুধু Admin, শুধু QBank) ──
 *
 * বাল্ক আপলোডে QBank-এ যে Subject › Topic গুলো এসেছে সেগুলোর তালিকা, দুটো কাজের জন্য:
 *
 * 1) **আনমুভ ফিল্টার** — বাল্ক আপলোডের পর যে টপিকগুলো থেকে এখনো কিছুই Quiz-এ পাঠানো হয়নি (বা
 *    বদলানো হয়নি) শুধু সেগুলো দেখায়। কোনো টপিকের যেকোনো প্রশ্ন Quiz-এ পাঠালেই (এখান থেকে বা
 *    প্রশ্ন-লিস্ট থেকে) টপিকটা সাথে সাথে "মুভ হয়েছে" মার্ক হয়ে আনমুভ তালিকা থেকে সরে যায়।
 * 2) **🔀 মার্জ টু** — টপিকের পাশের বাটনে ট্যাপ করে Quiz-এর যেকোনো Subject › Topic বাছলে ওই টপিকের
 *    সব MCQ একবারে সেখানে যোগ হয় (গন্তব্যে একই প্রশ্ন থাকলে স্কিপ — ডুপ্লিকেট হয় না)।
 *
 * ⚠️ QBank-এর কোনো প্রশ্ন কখনো সরে না, মুছে না, লুকায় না — "মুভ/মার্জ" মানে Quiz-এ কপি; প্রশ্ন
 * যেখানে ছিল সেখানেই থাকে। বানানের ভুলে আলাদা হয়ে থাকা Subject/Topic ঠিক করতে ✏️ Rename আছে;
 * গন্তব্য বাছার তালিকাতেও Rename/Delete আছে।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QBankTopicManagerDialog(
    viewModel        : QuizViewModel,
    onRenameSubject  : ((sheet: String, oldName: String, newName: String) -> Unit)?,
    onDeleteSubject  : ((sheet: String, name: String) -> Unit)?,
    onRenameSubTopic : ((sheet: String, subject: String, oldName: String, newName: String) -> Unit)?,
    onDeleteSubTopic : ((sheet: String, subject: String, name: String) -> Unit)?,
    onMergeToQuiz    : ((topicId: String, topicLabel: String, newSubject: String, newSubTopic: String) -> Unit)?,
    onDismiss        : () -> Unit
) {
    val ctx  = LocalContext.current
    val repo = remember { ContentRepository(ctx.applicationContext) }
    val accent = Color(0xFF0891B2)

    var rows by remember { mutableStateOf<List<ContentRepository.QBankTopicRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(1) }          // 0 = সব, 1 = আনমুভ (ডিফল্ট), 2 = মুভ হয়েছে
    var query by remember { mutableStateOf("") }
    var mergeTarget by remember { mutableStateOf<ContentRepository.QBankTopicRow?>(null) }
    var renameReq by remember { mutableStateOf<RenameReq?>(null) }
    var renameText by remember { mutableStateOf("") }

    // মুভ-মার্ক বদলালে (version state) এই তালিকা নিজে থেকেই রিকম্পোজ হয়
    val moved = QBankMovedStore.movedIds(ctx)

    LaunchedEffect(Unit) {
        rows = try { repo.getQBankTopicRows() } catch (e: Exception) { emptyList() }
        loading = false
    }

    val q = query.trim()
    val filtered = rows.filter { r ->
        val isMoved = r.topicId in moved
        val passFilter = when (filter) { 1 -> !isMoved; 2 -> isMoved; else -> true }
        passFilter && (q.isBlank() || r.topic.contains(q, ignoreCase = true) || r.subject.contains(q, ignoreCase = true))
    }
    val grouped = filtered.groupBy { it.subject }.toList()
        .sortedWith { a, b -> TopicOrdering.naturalCompare(a.first, b.first) }
    val totalCount = rows.size
    val unmovedCount = rows.count { it.topicId !in moved }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // ── হেডার ──
                Row(
                    Modifier.fillMaxWidth().background(accent).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("🧩 QBank টপিক ম্যানেজার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp, color = Color.White)
                        Text("আনমুভ $unmovedCount টি · মোট $totalCount টি টপিক", fontFamily = NotoSansBengali,
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("বন্ধ", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // ── ফিল্টার চিপ ──
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        1 to "⏳ আনমুভ ($unmovedCount)",
                        2 to "✅ মুভ হয়েছে (${totalCount - unmovedCount})",
                        0 to "সব ($totalCount)"
                    ).forEach { (mode, label) ->
                        val sel = filter == mode
                        Surface(
                            onClick = { filter = mode },
                            shape = RoundedCornerShape(20.dp),
                            color = if (sel) accent else accent.copy(alpha = 0.08f),
                            border = if (sel) null else BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                        ) {
                            Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else accent)
                        }
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Subject বা Topic খুঁজুন...", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                Text(
                    "ℹ️ QBank-এর প্রশ্ন সরে না — \"মার্জ টু\" মানে Quiz-এ কপি; প্রশ্ন যেখানে ছিল সেখানেই থাকবে।",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontFamily = NotoSansBengali, fontSize = 10.5.sp, color = Color(0xFF6B7280)
                )

                // ── তালিকা ──
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                    rows.isEmpty() -> Text(
                        "কোনো QBank টপিক পাওয়া যায়নি — একবার রিফ্রেশ করে আবার খুলুন।",
                        modifier = Modifier.padding(20.dp), fontFamily = NotoSansBengali, fontSize = 13.sp, color = Color(0xFF6B7280)
                    )
                    filtered.isEmpty() -> Text(
                        if (filter == 1 && q.isBlank()) "🎉 সব টপিকই মুভ করা হয়ে গেছে!" else "কিছু পাওয়া যায়নি",
                        modifier = Modifier.padding(20.dp), fontFamily = NotoSansBengali, fontSize = 13.sp, color = Color(0xFF6B7280)
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        grouped.forEach { (subject, list) ->
                            item(key = "S|$subject") {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📚 $subject", modifier = Modifier.weight(1f), fontFamily = NotoSansBengali,
                                        fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = accent)
                                    if (onRenameSubject != null) {
                                        TextButton(
                                            onClick = { renameReq = RenameReq(true, subject, ""); renameText = subject },
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) { Text("✏️", fontSize = 15.sp) }
                                    }
                                }
                            }
                            items(
                                list.sortedWith { a, b -> TopicOrdering.naturalCompare(a.topic, b.topic) },
                                key = { it.topicId }
                            ) { r ->
                                val isMoved = r.topicId in moved
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, (if (isMoved) Color(0xFF16A34A) else accent).copy(alpha = 0.25f)),
                                    tonalElevation = 1.dp
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(r.topic, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (r.count > 0) {
                                                    Text("${r.count} প্রশ্ন", fontFamily = NotoSansBengali, fontSize = 10.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (isMoved) {
                                                    Text("✅ মুভ হয়েছে", fontFamily = NotoSansBengali, fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold, color = Color(0xFF16A34A),
                                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFF16A34A).copy(alpha = 0.12f))
                                                            .clickable { QBankMovedStore.unmark(ctx, r.topicId) }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                        if (onRenameSubTopic != null) {
                                            TextButton(
                                                onClick = { renameReq = RenameReq(false, r.subject, r.topic); renameText = r.topic },
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) { Text("✏️", fontSize = 15.sp) }
                                        }
                                        if (onMergeToQuiz != null) {
                                            FilledTonalButton(
                                                onClick = { mergeTarget = r },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("🔀 মার্জ টু", fontFamily = NotoSansBengali, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 🔀 মার্জ টু: Quiz-এর গন্তব্য Subject › Topic বাছাই (Rename/Delete সহ) ──
    mergeTarget?.let { r ->
        AdminMoveQuestionsPickerDialog(
            subjects        = emptyList(),
            onLoadSubjects  = { viewModel.adminSubjectsForMove("Quiz") },
            selectedCount   = r.count,
            copyToQuizMode  = true,
            currentSubject  = "",
            currentSubTopic = "",
            openTopicFirst  = false,
            onLoadTopics    = { subj -> viewModel.adminTopicsForSubject("Quiz", subj) },
            onRenameSubject = onRenameSubject?.let { cb -> { old: String, new: String -> cb("Quiz", old, new) } },
            onDeleteSubject = onDeleteSubject?.let { cb -> { name: String -> cb("Quiz", name) } },
            onRenameTopic   = onRenameSubTopic?.let { cb -> { subj: String, old: String, new: String -> cb("Quiz", subj, old, new) } },
            onDeleteTopic   = onDeleteSubTopic?.let { cb -> { subj: String, name: String -> cb("Quiz", subj, name) } },
            onConfirm = { newSubject, newSubTopic ->
                // টপিক সাথে সাথে "মুভ হয়েছে" মার্ক হয় (ViewModel-ই মার্ক করে) — তালিকায় তখনই বদলে যায়
                onMergeToQuiz?.invoke(r.topicId, r.topic, newSubject, newSubTopic)
            },
            onDismiss = { mergeTarget = null }
        )
    }

    // ── ✏️ Rename (Subject বা Topic — বানানের ভুলে আলাদা হয়ে থাকা নাম ঠিক করতে) ──
    renameReq?.let { req ->
        val oldName = if (req.isSubject) req.subject else req.topic
        AlertDialog(
            onDismissRequest = { renameReq = null },
            title = {
                Text(if (req.isSubject) "✏️ Subject Rename" else "✏️ Topic Rename",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    label = { Text("নতুন নাম", fontFamily = NotoSansBengali) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val nn = renameText.trim()
                TextButton(
                    onClick = {
                        if (req.isSubject) {
                            rows = rows.map { if (it.subject == req.subject) it.copy(subject = nn) else it }
                            onRenameSubject?.invoke("QBank", req.subject, nn)
                        } else {
                            rows = rows.map {
                                if (it.subject == req.subject && it.topic == req.topic) it.copy(topic = nn) else it
                            }
                            onRenameSubTopic?.invoke("QBank", req.subject, req.topic, nn)
                        }
                        renameReq = null
                    },
                    enabled = nn.isNotBlank() && nn != oldName
                ) { Text("Rename করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { renameReq = null }) { Text("বাতিল", fontFamily = NotoSansBengali) } }
        )
    }
}

private data class RenameReq(val isSubject: Boolean, val subject: String, val topic: String)
