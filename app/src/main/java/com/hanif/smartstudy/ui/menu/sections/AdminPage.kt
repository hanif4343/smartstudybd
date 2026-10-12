package com.hanif.smartstudy.ui.menu.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel
import java.text.SimpleDateFormat
import java.util.*

private val Indigo600 = Color(0xFF4F46E5)
private val GreenOk   = Color(0xFF10B981)
private val RedWrong  = Color(0xFFEF4444)
// Theme-aware colors accessed via MaterialTheme.colorScheme inside composables
// Legacy constants below are kept only for non-composable contexts
private val DeepIndigo= Color(0xFF1E1B4B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPage(
    state  : MenuUiState,
    vm     : MenuViewModel,
    onBack : () -> Unit
) {
    var tab            by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("🛡️ Admin Panel", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepIndigo,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Tab row — Phase 6 item 13: 👥ইউজার/📣Notify/🔑FCM/🚩Reports/🌐Bulk Tag/📋Logs
            // সম্পূর্ণ সরানো হয়েছে (Admin Web App-এ ডুপ্লিকেট ছিল) — এখন শুধু ৪টা যেগুলো
            // মোবাইল থেকে করা সত্যিই দরকারি (দ্রুত এক-প্রশ্ন যোগ, বাল্ক আপলোড, offline-edit
            // sync, রিলিজ-চেকলিস্ট)।
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor   = DeepIndigo,
                contentColor     = Color.White,
                edgePadding      = 0.dp
            ) {
                listOf("⏳ Sync", "✅ চেকলিস্ট")
                    .forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i },
                            text = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold) })
                    }
            }

            // প্যানেল খুললে pending edit তালিকা লোড (ইউজার তালিকা আর লাগে না — সরানো হয়েছে)
            LaunchedEffect(Unit) { vm.loadPendingEdits() }

            // Sync ট্যাব (⏳ Sync, index 0) খোলার সময় প্রতিবার fresh করে নাও — যাতে
            // এইমাত্র করা offline edit-ও সাথে সাথে দেখা যায়
            LaunchedEffect(tab) { if (tab == 0) vm.loadPendingEdits() }

            when (tab) {
                0 -> PendingSyncTab(state, vm)
                1 -> ProductionChecklistTab()
            }
        }
    }

    // Messages
    val msg = state.successMsg ?: state.error
    if (msg != null) {
        val isSuccess = state.successMsg != null
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.clearMsg()
        }
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier          = Modifier.padding(16.dp),
                containerColor    = if (isSuccess) GreenOk else RedWrong,
                contentColor      = Color.White
            ) {
                Text(msg, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// (➕ নতুন প্রশ্ন ট্যাব Phase 1b-তে সরানো হয়েছে — স্টুডেন্ট অ্যাপ থেকে প্রশ্ন যোগ করা যাবে না)
// (Bulk Upload ট্যাব Phase 1-এ সরানো হয়েছে — স্টুডেন্ট অ্যাপ থেকে মাস্টার-প্রশ্ন বাল্ক আপলোড নেই)


// ── Pending Sync Tab ──
// Sync Now-এর লাইভ প্রোগ্রেস বার (কোন action চলছে, কয়টা শেষ), পেন্ডিং তালিকা (ব্যর্থ হলে
// কারণসহ), আর "ইতিহাস" — সফল/ব্যর্থ সব action তারিখ-ফিল্টার সহ, ব্যর্থতার কারণ সুন্দর করে।
private enum class DateFilter(val label: String) { TODAY("আজ"), YESTERDAY("গতকাল"), D7("৭ দিন"), D30("৩০ দিন"), ALL("সব"), PICKED("📅") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingSyncTab(state: MenuUiState, vm: MenuViewModel) {
    val pending  = state.pendingEdits
    val syncing  = state.isSyncingEdits
    val syncMsg  = state.syncEditsMsg
    val gson     = remember { com.google.gson.Gson() }
    val sdfTime  = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val sdfDay   = remember { SimpleDateFormat("dd MMM yyyy, EEEE", Locale("bn")) }
    val sdfFull  = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    var section      by remember { mutableStateOf(0) }                 // 0 = পেন্ডিং, 1 = ইতিহাস
    var dateFilter   by remember { mutableStateOf(DateFilter.ALL) }
    var statusFilter by remember { mutableStateOf(0) }                 // 0 সব, 1 সফল, 2 ব্যর্থ
    var pickedDay    by remember { mutableStateOf<Long?>(null) }
    var showPicker   by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(syncMsg) {
        if (syncMsg != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearSyncEditsMsg()
        }
    }

    // ── তারিখ-ফিল্টার: দিনের শুরু (লোকাল টাইমজোন) বের করার হেল্পার ──
    fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val DAY = 24L * 3600_000L
    val todayStart = dayStart(System.currentTimeMillis())

    val history = state.syncHistory
    val filtered = remember(history, dateFilter, statusFilter, pickedDay, todayStart) {
        history.filter { e ->
            val okDate = when (dateFilter) {
                DateFilter.TODAY     -> e.updatedAt >= todayStart
                DateFilter.YESTERDAY -> e.updatedAt in (todayStart - DAY) until todayStart
                DateFilter.D7        -> e.updatedAt >= todayStart - 6 * DAY
                DateFilter.D30       -> e.updatedAt >= todayStart - 29 * DAY
                DateFilter.ALL       -> true
                DateFilter.PICKED    -> pickedDay?.let { d -> e.updatedAt in d until d + DAY } ?: true
            }
            val okStatus = when (statusFilter) {
                1 -> e.status == com.hanif.smartstudy.data.local.SyncHistoryEntry.STATUS_SUCCESS
                2 -> e.status == com.hanif.smartstudy.data.local.SyncHistoryEntry.STATUS_FAILED
                else -> true
            }
            okDate && okStatus
        }
    }
    val okCount   = filtered.count { it.status == com.hanif.smartstudy.data.local.SyncHistoryEntry.STATUS_SUCCESS }
    val failCount = filtered.size - okCount

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Header card
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (pending.isEmpty()) "✅" else "⏳", fontSize = 26.sp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pending.isEmpty()) "কোনো pending action নেই" else "${pending.size}টি action sync হয়নি",
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp, color = Color(0xFF1E1B4B)
                    )
                    Text(
                        "Move, Delete, Rename, Edit — অফলাইনে/ব্যর্থ হলে এখানে জমা থাকে",
                        fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (pending.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEF4444)) {
                        Text(
                            "${pending.size}",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Sync Now button
        Button(
            onClick  = { vm.syncPendingEditsNow() },
            enabled  = !syncing && pending.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
        ) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(20.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Sync হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pending.isEmpty()) "✅ সব Sync হয়ে গেছে" else "☁ Sync Now (${pending.size}টি)",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                )
            }
        }

        // ── লাইভ প্রোগ্রেস বার ──
        if (syncing && state.syncTotal > 0) {
            val frac = (state.syncDone.toFloat() / state.syncTotal).coerceIn(0f, 1f)
            val animated by animateFloatAsState(frac, label = "syncProgress")
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔄 Sync চলছে", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${state.syncDone}/${state.syncTotal} • ${(frac * 100).toInt()}%",
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Indigo600)
                    }
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = Indigo600, trackColor = Indigo600.copy(alpha = 0.15f)
                    )
                    if (state.syncCurrentLabel.isNotBlank()) {
                        Text("এখন: ${state.syncCurrentLabel}", fontFamily = NotoSansBengali, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Sync result message
        syncMsg?.let {
            val isOk = it.startsWith("✅")
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF7ED),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold,
                    color = if (isOk) Color(0xFF166534) else Color(0xFF92400E))
            }
        }

        // ── সেকশন সুইচ: পেন্ডিং | ইতিহাস ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = section == 0, onClick = { section = 0 },
                label = { Text("⏳ পেন্ডিং (${pending.size})", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 12.sp) })
            FilterChip(selected = section == 1, onClick = { section = 1; vm.loadSyncHistory() },
                label = { Text("📜 ইতিহাস (${history.size})", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 12.sp) })
        }

        if (section == 0) {
            // ════════ পেন্ডিং তালিকা ════════
            if (pending.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("সব action sync হয়ে গেছে!", fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold, color = GreenOk, fontSize = 15.sp)
                        Text("কী কী হয়েছে দেখতে \"ইতিহাস\" ট্যাব খুলুন", fontFamily = NotoSansBengali,
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(pending, key = { it.id }) { action ->
                        val (sheet0, preview0) = com.hanif.smartstudy.data.local.SyncHistoryStore.describe(action, gson)
                        val sheet = sheet0.ifBlank { "?" }
                        val retry = action.retryCount
                        val running = syncing && state.syncCurrentId == action.id
                        val idx = pending.indexOfFirst { it.id == action.id }
                        val waiting = syncing && !running && idx > state.syncDone
                        val alreadyDone = syncing && !running && idx < state.syncDone
                        val typeColor = when (action.type) {
                            "admin_add_question" -> Color(0xFF16A34A)
                            "admin_delete_question", "admin_delete_subject_topic" -> Color(0xFFDC2626)
                            "admin_move_questions", "admin_move_topic" -> Color(0xFF0EA5E9)
                            else -> Color(0xFF4F46E5)
                        }
                        Card(
                            Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (running) Indigo600.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(shape = RoundedCornerShape(8.dp),
                                        color = if (retry > 0) Color(0xFFFFF7ED) else Color(0xFFEEF2FF)) {
                                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                            if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Indigo600)
                                            else Text(if (retry > 0) "⚠️" else "📴", fontSize = 16.sp)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = Indigo600.copy(0.1f)) {
                                                Text(sheet, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold, color = Indigo600, fontFamily = NotoSansBengali)
                                            }
                                            Surface(shape = RoundedCornerShape(6.dp), color = typeColor.copy(0.1f)) {
                                                Text(com.hanif.smartstudy.data.local.SyncHistoryStore.typeLabel(action.type),
                                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold, color = typeColor, fontFamily = NotoSansBengali)
                                            }
                                            Text(sdfFull.format(Date(action.createdAt)), fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(preview0.ifBlank { "প্রশ্ন preview নেই" }, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        when { running -> "চলছে…"; waiting -> "অপেক্ষায়"; alreadyDone -> "শেষ"; retry > 0 -> "retry $retry"; else -> "" },
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                                        color = if (running) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // সর্বশেষ ব্যর্থতার কারণ
                                if (!action.lastError.isNullOrBlank() && !running) {
                                    FailureReasonBox(action.lastError, action.lastTriedAt.takeIf { it > 0 }?.let { sdfFull.format(Date(it)) },
                                        footer = "পরের Sync-এ আবার চেষ্টা হবে")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ════════ ইতিহাস ════════
            // তারিখ ফিল্টার
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateFilter.values().forEach { f ->
                    val label = if (f == DateFilter.PICKED)
                        (pickedDay?.let { "📅 " + SimpleDateFormat("dd MMM", Locale("bn")).format(Date(it)) } ?: "📅 তারিখ বাছুন")
                    else f.label
                    FilterChip(selected = dateFilter == f,
                        onClick = { if (f == DateFilter.PICKED) showPicker = true else dateFilter = f },
                        label = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                }
            }
            // স্ট্যাটাস ফিল্টার + সংখ্যা
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                listOf("সব (${filtered.size})", "✅ সফল ($okCount)", "❌ ব্যর্থ ($failCount)").forEachIndexed { i, t ->
                    FilterChip(selected = statusFilter == i, onClick = { statusFilter = i },
                        label = { Text(t, fontFamily = NotoSansBengali, fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                }
                if (history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("🧹 মুছুন", fontFamily = NotoSansBengali, fontSize = 11.sp, color = RedWrong)
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 40.sp)
                        Text(if (history.isEmpty()) "এখনো কোনো ইতিহাস নেই" else "এই ফিল্টারে কিছু পাওয়া যায়নি",
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val grouped = remember(filtered) { filtered.groupBy { dayStart(it.updatedAt) }.toList().sortedByDescending { it.first } }
                val stillPendingIds = remember(pending) { pending.map { it.id }.toSet() }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    grouped.forEach { (day, dayItems) ->
                        item(key = "h$day") {
                            val dayLabel = when (day) {
                                todayStart -> "আজ"
                                todayStart - DAY -> "গতকাল"
                                else -> sdfDay.format(Date(day))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(dayLabel, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("${dayItems.size}টি", fontFamily = NotoSansBengali, fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(dayItems, key = { it.id }) { e ->
                            val ok = e.status == com.hanif.smartstudy.data.local.SyncHistoryEntry.STATUS_SUCCESS
                            val c = if (ok) GreenOk else RedWrong
                            Card(
                                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Row(Modifier.height(IntrinsicSize.Min)) {
                                    Box(Modifier.width(4.dp).fillMaxHeight().background(c))
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(if (ok) "✅" else "❌", fontSize = 14.sp)
                                            Text(com.hanif.smartstudy.data.local.SyncHistoryStore.typeLabel(e.actionType),
                                                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = c)
                                            if (e.sheet.isNotBlank()) {
                                                Surface(shape = RoundedCornerShape(6.dp), color = Indigo600.copy(0.1f)) {
                                                    Text(e.sheet, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold, color = Indigo600, fontFamily = NotoSansBengali)
                                                }
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Text(sdfTime.format(Date(e.updatedAt)), fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                                        }
                                        if (e.summary.isNotBlank()) {
                                            Text(e.summary, fontFamily = NotoSansBengali, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface, maxLines = 3,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                        if (e.attempts > 1) {
                                            Text(if (ok) "${e.attempts}তম চেষ্টায় সফল" else "${e.attempts} বার চেষ্টা করা হয়েছে",
                                                fontFamily = NotoSansBengali, fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (!ok) {
                                            FailureReasonBox(e.reason, null,
                                                footer = if (e.id in stillPendingIds) "পেন্ডিং-এ আছে — পরের Sync-এ আবার চেষ্টা হবে" else null)
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

    // ── তারিখ বাছাই ──
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = pickedDay ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        // DatePicker UTC-মধ্যরাত দেয় — লোকাল দিনের শুরুতে বদলে নেওয়া
                        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        pickedDay = Calendar.getInstance().apply {
                            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
                        }.timeInMillis
                        dateFilter = DateFilter.PICKED
                    }
                    showPicker = false
                }) { Text("ঠিক আছে", fontFamily = NotoSansBengali) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("বাতিল", fontFamily = NotoSansBengali) } }
        ) { DatePicker(state = pickerState) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ইতিহাস মুছবেন?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
            text = { Text("শুধু ইতিহাসের তালিকা মুছবে — পেন্ডিং action আর আসল ডেটা অক্ষত থাকবে।", fontFamily = NotoSansBengali) },
            confirmButton = { TextButton(onClick = { vm.clearSyncHistory(); confirmClear = false }) { Text("মুছুন", color = RedWrong, fontFamily = NotoSansBengali) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("থাক", fontFamily = NotoSansBengali) } }
        )
    }
}

/** ব্যর্থতার কারণ — সুন্দর বাংলা শিরোনাম + নিচে কাঁচা বার্তা (ছোট, কপি করা যায়) */
@Composable
private fun FailureReasonBox(raw: String?, whenText: String?, footer: String?) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFEF2F2), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(com.hanif.smartstudy.data.local.SyncHistoryStore.prettyReason(raw),
                fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF991B1B))
            if (!raw.isNullOrBlank()) {
                SelectionContainer {
                    Text(raw, fontFamily = NotoSansBengali, fontSize = 10.sp, color = Color(0xFF7F1D1D).copy(alpha = 0.75f),
                        maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            val foot = listOfNotNull(whenText?.let { "সর্বশেষ চেষ্টা: $it" }, footer).joinToString(" • ")
            if (foot.isNotBlank()) {
                Text(foot, fontFamily = NotoSansBengali, fontSize = 10.sp, color = Color(0xFF92400E))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// ✅ Production Checklist Tab
// Play Store এ ছাড়ার আগে এই সব জিনিস ঠিক করতে হবে
// ══════════════════════════════════════════════════════════════════
private data class CheckItem(
    val done    : Boolean,
    val critical: Boolean,
    val title   : String,
    val detail  : String,
    val file    : String
)

@Composable
private fun ProductionChecklistTab() {

    val checklist = listOf(
        // ── 🔴 CRITICAL ──────────────────────────────────────────────────
        CheckItem(false, true,
            "AdMob Test ID সরাও — App ID (Manifest)",
            "AndroidManifest.xml এ এখন Google-এর test App ID আছে:\n" +
            "ca-app-pub-3940256099942544~3347511713\n" +
            "→ নিজের AdMob অ্যাকাউন্ট থেকে আসল App ID বসাও।",
            "app/src/main/AndroidManifest.xml (line ~79)"
        ),
        CheckItem(false, true,
            "AdMob Test Ad Unit ID সরাও — AdManager.kt",
            "AdManager.kt-এ সব BANNER / INTERSTITIAL / REWARDED এখন Google test ID দিয়ে চলছে:\n" +
            "ca-app-pub-3940256099942544/...\n" +
            "→ Production-এ প্রতিটা val এর জায়গায় নিজের আসল ad unit ID বসাও।\n" +
            "স্থান: BANNER_HOME, BANNER_QUIZ_LIST, BANNER_QBANK_SUBJECT, BANNER_STUDY,\n" +
            "BANNER_WEEKEND, INTERSTITIAL_RESULT, INTERSTITIAL_CHALLENGE,\n" +
            "REWARDED_XP_BONUS, REWARDED_DAILY_LOGIN, NATIVE_HOME",
            "app/src/main/java/com/hanif/smartstudy/util/AdManager.kt (line ~42-50)"
        ),
        CheckItem(false, true,
            "REALTIME_DATA = false করো — build.gradle",
            "এখন build.gradle-এ REALTIME_DATA = true আছে।\n" +
            "এর মানে: প্রতিবার এপ খুললে সরাসরি Firebase থেকে data টানে — cache নেই।\n" +
            "→ Production-এ false করো, নইলে:\n" +
            "   • Firebase read বিল বাড়বে\n" +
            "   • অনেক user হলে Firebase throttle করবে\n" +
            "   • এপ খুলতে বেশি সময় লাগবে (offline-first না)",
            "app/build.gradle — buildConfigField \"boolean\", \"REALTIME_DATA\", \"true\""
        ),
        CheckItem(false, true,
            "minifyEnabled true করো — build.gradle (release)",
            "এখন release build-এ minifyEnabled false আছে।\n" +
            "→ true করলে:\n" +
            "   • APK ছোট হবে (~30-50%)\n" +
            "   • Code obfuscate হবে (reverse engineering কঠিন)\n" +
            "   • BuildConfig secrets গুলো decompile করা কঠিন হবে\n" +
            "⚠️ true করার পর proguard-rules.pro চেক করো — crash হলে rules যোগ করতে হবে।",
            "app/build.gradle — buildTypes > release > minifyEnabled false"
        ),
        CheckItem(true, true,
            "Firebase DB Secret → User-auth-only migration ✅",
            "FIREBASE_DB_SECRET সম্পূর্ণ সরানো হয়েছে (build.gradle, CI workflow,\n" +
            "FirebaseTokenProvider.kt সবখান থেকে)। এখন app চালু হওয়ার সাথে সাথেই\n" +
            "Firebase Anonymous Auth দিয়ে sign-in হয় (SmartStudyApp.onCreate), তাই\n" +
            "currentUser কখনো null থাকে না — কোনো master secret আর দরকার নেই।\n" +
            "⚠️ পুরনো secret আগের সব APK build-এ এমবেড হয়ে গেছে — Firebase Console এ\n" +
            "গিয়ে Database Secret rotate করো, আর GitHub repo থেকে\n" +
            "FIREBASE_DB_SECRET secret-টাও ডিলিট করে দাও (আমি এটা করতে পারবো না)।",
            "app/.../data/remote/FirebaseTokenProvider.kt"
        ),
        CheckItem(true, true,
            "Firebase Rules — Users node সবাই পড়তে পারছে ✅",
            "গভীরে গিয়ে দেখা গেল সমস্যাটা আরও বড় ছিল — isAdmin চেক\n" +
            "root.child('Users').child(auth.uid) দিয়ে হতো, কিন্তু Users node\n" +
            "phone দিয়ে key করা, Firebase Auth UID দিয়ে না! মানে এই isAdmin\n" +
            "চেক প্রোডাকশনে কখনোই সত্যি হতো না (Reports/ModelTests/Routine\n" +
            "সব জায়গায়) — সম্ভবত DB-secret fallback এটা আড়াল করে রাখতো।\n" +
            "→ Fix: নতুন UidToPhone/{uid}→phone ম্যাপিং (sign-in এর সময় লেখা হয়),\n" +
            "   Users/\$userId এখন শুধু owner + admin পড়তে/লিখতে পারবে,\n" +
            "   users (lowercase, FCM token) node ও একইভাবে fix হয়েছে।\n" +
            "⚠️ Firebase Console/CLI দিয়ে নতুন rules ডিপ্লয় করতে হবে, আর Google\n" +
            "   sign-in + admin login ভালোভাবে টেস্ট করে দেখো।",
            "firebase-database-rules.json"
        ),

        // ── 🟡 RECOMMENDED ────────────────────────────────────────────────
        CheckItem(false, false,
            "210+ Log statement সরাও বা disable করো",
            "সারা কোডজুড়ে ২১০টি Log.d/e/w আছে।\n" +
            "→ Production-এ sensitive info (phone, token, Firebase URL) log-এ দেখা যায়।\n" +
            "→ সহজ fix: proguard-rules.pro-তে যোগ করো:\n" +
            "   -assumenosideeffects class android.util.Log { *; }\n" +
            "   (minifyEnabled true হলে কাজ করবে)",
            "সব .kt ফাইল — grep: Log.d / Log.e / Log.w"
        ),
        CheckItem(false, false,
            "SyncWorker-এ DB Secret সরাসরি URL-এ যাচ্ছে",
            "SyncWorker.kt-এ ?auth=\$secret দিয়ে Firebase REST call হচ্ছে।\n" +
            "Worker background-এ চলে, তখন কোনো signed-in user নাও থাকতে পারে।\n" +
            "→ Worker-এ Firebase Auth token refresh করে ব্যবহার করো।",
            "app/.../worker/SyncWorker.kt"
        ),
        CheckItem(true, false,
            "RemoteLogger সম্পূর্ণ সরানো হয়েছে ✅",
            "RemoteLogger.kt (Firebase এ DB Secret দিয়ে debug log পাঠানো) পুরোপুরি " +
            "বাদ দেওয়া হয়েছে — ফাইল ডিলিট, আর init/flush/setUserPhone/d/e/i সব কল " +
            "সরিয়ে ফেলা হয়েছে (যেখানে দরকার ছিল সেখানে সাধারণ android.util.Log " +
            "রাখা হয়েছে, যা শুধু ডিভাইসের logcat-এই থাকে, বাইরে পাঠায় না)।",
            "app/.../util/RemoteLogger.kt — ডিলিট করা হয়েছে"
        ),
        CheckItem(false, false,
            "versionName এবং versionCode ঠিক করো",
            "এখন build.gradle-এ versionCode টা github run number দিয়ে auto-set হয়।\n" +
            "versionName \"1.3\" — Play Store-এর জন্য meaningful version দাও।\n" +
            "→ Semantic versioning: major.minor.patch (যেমন: 1.0.0)",
            "app/build.gradle — versionName"
        ),
        CheckItem(false, false,
            "Play Store listing-এ আসল App Icon দাও",
            "এখন build.yml-এ Python দিয়ে নীল রঙের 'SS' লেখা placeholder icon তৈরি হচ্ছে।\n" +
            "→ Figma বা Adobe দিয়ে proper icon বানাও:\n" +
            "   512×512 PNG (Play Store)\n" +
            "   মিপম্যাপ folder-এ (mdpi থেকে xxxhdpi)\n" +
            "   Adaptive icon (foreground + background আলাদা)",
            "app/src/main/res/mipmap-*/ + build.yml icon generation step"
        ),
        CheckItem(false, false,
            "Privacy Policy URL যাচাই করো",
            "Play Store-এ Privacy Policy আবশ্যক।\n" +
            "PrivacyPolicyScreen.kt এ কোনো URL hardcode আছে কিনা চেক করো।",
            "app/.../ui/menu/PrivacyPolicyScreen.kt"
        ),
    )

    val criticalCount  = checklist.count { it.critical && !it.done }
    val recommendCount = checklist.count { !it.critical && !it.done }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4F46E5)))
                    )
                    .padding(16.dp)
            ) {
                Text(
                    "🚀 Production Checklist",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Play Store এ ছাড়ার আগে এই সব ঠিক করতে হবে",
                    fontFamily = NotoSansBengali, fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🔴 $criticalCount টা Critical বাকি",
                            fontFamily = NotoSansBengali, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🟡 $recommendCount টা Recommended বাকি",
                            fontFamily = NotoSansBengali, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    }
                }
            }
        }

        // Critical section header
        item {
            Text(
                "🔴 অবশ্যই করতে হবে (Critical)",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp, color = Color(0xFFEF4444),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Critical items
        items(checklist.filter { it.critical }) { item ->
            ChecklistCard(item.done, item.critical, item.title, item.detail, item.file)
        }

        // Recommended section header
        item {
            Text(
                "🟡 করা ভালো (Recommended)",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp, color = Color(0xFFF59E0B),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Recommended items
        items(checklist.filter { !it.critical }) { item ->
            ChecklistCard(item.done, item.critical, item.title, item.detail, item.file)
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ChecklistCard(
    done    : Boolean,
    critical: Boolean,
    title   : String,
    detail  : String,
    file    : String
) {
    val borderColor = if (critical) Color(0xFFEF4444) else Color(0xFFF59E0B)
    val bgColor     = if (critical) Color(0xFFEF4444).copy(alpha = 0.05f)
                      else          Color(0xFFF59E0B).copy(alpha = 0.05f)
    val icon        = if (done) "✅" else if (critical) "🔴" else "🟡"

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                title, fontFamily = NotoSansBengali,
                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = borderColor, modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                detail, fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Folder, null,
                    tint = borderColor, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(file, fontFamily = NotoSansBengali, fontSize = 10.sp,
                        color = borderColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
