package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.DataSourceMode
import com.hanif.smartstudy.data.model.TypingSheetStageContent
import com.hanif.smartstudy.data.remote.ApiResult
import com.hanif.smartstudy.data.remote.ContentFetchService
import com.hanif.smartstudy.data.remote.GasContentService
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.CurriculumStageContentProvider
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  এডমিন-এডিটর — কারিকুলাম-স্টেজের প্র্যাকটিস-কনটেন্ট (Google Sheet ব্যাকড)
 * ═══════════════════════════════════════════════════════════════════════
 * শুধু এডমিনদের জন্য (কল-সাইটে session.getCurrentUser()?.isAdmin() চেক করে
 * তবেই এই স্ক্রিন খোলা উচিত — এই কম্পোজেবল নিজে সেটা re-verify করে না, কারণ
 * navigation-লেয়ারেই একবার গার্ড করাই যথেষ্ট, ঠিক AdminPage.kt-এর প্যাটার্নে)।
 *
 * এখানে এডিট করা কনটেন্ট সরাসরি Google Sheet-এর "CurriculumStages" ট্যাবে যায়
 * (GasContentService.addQuestion() — বিদ্যমান জেনেরিক row-upsert endpoint, নতুন
 * কোনো নেটওয়ার্কিং কোড লাগেনি), আর সেখান থেকে Firebase-এ সিঙ্ক হয়ে **সব
 * ইউজারের** অ্যাপে প্রযোজ্য হয় (দেখো CurriculumStageContentProvider.kt)।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumStageAdminScreen(onBack: () -> Unit, initialTrack: String = "bn", initialStage: Int = 1) {
    val ctx = LocalContext.current
    val session = remember { SessionManager(ctx) }
    val scope = rememberCoroutineScope()

    var track by remember { mutableStateOf(initialTrack) }
    var stageText by remember { mutableStateOf(initialStage.toString()) }
    var content by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<List<TypingSheetStageContent>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val stage = stageText.toIntOrNull()

    suspend fun reload() {
        loading = true
        val mode = session.getDataSourceMode()
        val all = try {
            if (mode == DataSourceMode.GOOGLE_SHEET) GasContentService.fetchCurriculumStageContent()
            else ContentFetchService.fetchCurriculumStageContent()
        } catch (e: Exception) { emptyList() }
        existing = all.filter { it.track == track && it.stageInt() == stage }
        loading = false
    }

    LaunchedEffect(track, stage) { if (stage != null) reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("✏️ স্টেজ-কনটেন্ট এডিটর", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "এখানে যা সাবমিট করবেন তা সরাসরি Google Sheet-এ যাবে এবং সব ইউজারের অ্যাপে প্রযোজ্য হবে — সাবধানে লিখুন।",
                fontSize = 11.5.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── ট্র্যাক + স্টেজ ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TrackChip("বাংলা", track == "bn") { track = "bn"; editId = null; content = "" }
                    TrackChip("English", track == "en") { track = "en"; editId = null; content = "" }
                }
                OutlinedTextField(
                    value = stageText,
                    onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) { stageText = v; editId = null; content = "" } },
                    modifier = Modifier.width(90.dp),
                    label = { Text("স্টেজ", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            // ── বিদ্যমান ভ্যারিয়েন্ট ──
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (existing.isNotEmpty()) {
                Text("বিদ্যমান ভ্যারিয়েন্ট (${existing.size}টা) — ট্যাপ করে এডিট করুন",
                    fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(existing) { row ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (editId == row.id) Color(0xFF6366F1).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = { editId = row.id; content = row.content }
                        ) {
                            Text(row.content, fontSize = 12.sp, fontFamily = NotoSansBengali, maxLines = 2,
                                modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            } else if (stage != null) {
                Text("এই স্টেজে এখনো কোনো এডমিন-কনটেন্ট নেই — অটো-জেনারেটেড টেক্সট চলছে",
                    fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── কনটেন্ট ইনপুট ──
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (editId != null) "এডিট করছেন" else "নতুন কনটেন্ট", fontFamily = NotoSansBengali) },
                placeholder = { Text("শব্দগুলো স্পেস দিয়ে আলাদা লিখুন...", fontFamily = NotoSansBengali) },
                minLines = 5
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editId != null) {
                    OutlinedButton(onClick = { editId = null; content = "" }, modifier = Modifier.weight(1f)) {
                        Text("নতুন হিসেবে যোগ করুন", fontSize = 12.sp, fontFamily = NotoSansBengali)
                    }
                }
                Button(
                    onClick = {
                        if (stage == null || content.isBlank()) return@Button
                        submitting = true
                        statusMsg = null
                        scope.launch {
                            val fields = buildMap {
                                put("track", track); put("stage", stage.toString()); put("content", content.trim())
                                editId?.let { put("editId", it) }
                            }
                            when (val res = GasContentService.addQuestion("CurriculumStages", fields)) {
                                is ApiResult.Success -> {
                                    statusIsError = false
                                    statusMsg = "✅ সেভ হয়েছে — সবার অ্যাপে প্রযোজ্য হবে"
                                    CurriculumStageContentProvider.forceRefreshNextTime()
                                    editId = null; content = ""
                                    reload()
                                }
                                is ApiResult.Error -> {
                                    statusIsError = true
                                    statusMsg = "❌ ব্যর্থ: ${res.message}"
                                }
                            }
                            submitting = false
                        }
                    },
                    enabled = !submitting && stage != null && content.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(if (editId != null) "আপডেট করুন" else "সেভ করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                }
            }

            statusMsg?.let {
                Text(it, fontSize = 12.sp, fontFamily = NotoSansBengali,
                    color = if (statusIsError) Color(0xFFEF4444) else Color(0xFF10B981))
            }
        }
    }
}

@Composable
private fun TrackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF6366F1) else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Text(
            label, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}
