package com.hanif.smartstudy.ui.menu.sections

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
    var notifyTitle    by remember { mutableStateOf("") }
    var notifyBody     by remember { mutableStateOf("") }
    var targetPhone    by remember { mutableStateOf("") }
    var showViewAsDialog by remember { mutableStateOf(false) }
    var viewAsPhone    by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🛡️ Admin Panel", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                        Text("${state.activeUsers.count { it.isOnline }} জন এখন অনলাইন",
                            fontSize = 10.sp, color = GreenOk, fontFamily = NotoSansBengali)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepIndigo,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor   = DeepIndigo,
                contentColor     = Color.White,
                edgePadding      = 0.dp
            ) {
                listOf("👥 ইউজার", "📣 Notify", "🔑 FCM", "🚩 Reports", "➕ নতুন প্রশ্ন", "⚡ Bulk Upload", "🌐 Bulk Tag", "✏️ Rename", "📋 Logs", "⏳ Sync", "🧪 Model Test", "✅ চেকলিস্ট")
                    .forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i },
                            text = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold) })
                    }
            }

            // Auto-load users when admin panel first opens
            LaunchedEffect(Unit) { vm.loadActiveUsers(); vm.loadPendingEdits() }

            // Auto-load reports when tab 3 opens
            LaunchedEffect(tab) { if (tab == 3) vm.loadPendingReports() }
            // Auto-load debug log phone list when Logs tab (8) opens
            LaunchedEffect(tab) { if (tab == 8) vm.loadDebugLogPhones() }
            // Sync ট্যাব (⏳ Sync) খোলার সময় প্রতিবার fresh করে নাও — যাতে
            // এইমাত্র করা offline edit-ও সাথে সাথে দেখা যায়
            LaunchedEffect(tab) { if (tab == 9) vm.loadPendingEdits() }

            when (tab) {
                0 -> ActiveUsersTab(state, vm, onViewAs = { showViewAsDialog = true; viewAsPhone = it })
                1 -> NotifyTab(notifyTitle, notifyBody, targetPhone,
                    onTitle  = { notifyTitle = it },
                    onBody   = { notifyBody  = it },
                    onTarget = { targetPhone = it },
                    onSend   = {
                        vm.adminSendNotification(notifyTitle, notifyBody, targetPhone.ifBlank { null })
                        notifyTitle = ""; notifyBody = ""; targetPhone = ""
                    })
                2 -> FcmTab(state)
                3 -> ReportQueueTab(state, vm)
                4 -> AddQuestionTab(state, vm)
                5 -> BulkUploaderTab(state, vm)
                6 -> BulkAudienceTab(state, vm)
                7 -> RenameTab(state, vm)
                8 -> LogsTab(state, vm)
                9 -> PendingSyncTab(state, vm)
                10 -> ModelTestGenerateTab(state, vm)
                11 -> ProductionChecklistTab()
            }
        }
    }

    // View-as dialog
    if (showViewAsDialog) {
        AlertDialog(
            onDismissRequest = { showViewAsDialog = false },
            title = { Text("ইউজার হিসেবে দেখুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
            text  = {
                OutlinedTextField(
                    value         = viewAsPhone,
                    onValueChange = { viewAsPhone = it },
                    label         = { Text("ফোন নম্বর", fontFamily = NotoSansBengali) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewAsPhone.isNotBlank()) {
                        vm.adminViewAs(viewAsPhone.trim())
                        showViewAsDialog = false
                    }
                }) { Text("দেখুন", fontFamily = NotoSansBengali, color = Indigo600, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton(onClick = { showViewAsDialog = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
        )
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

// ── Active users list ──
@Composable
private fun ActiveUsersTab(
    state    : MenuUiState,
    vm       : MenuViewModel,
    onViewAs : (String) -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    if (state.activeUsers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👥", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text("কোনো ইউজার নেই", fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.loadActiveUsers() }) {
                    Text("Refresh", fontFamily = NotoSansBengali)
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Summary bar
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xFFF0FDF4), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val online  = state.activeUsers.count { it.isOnline }
                val offline = state.activeUsers.size - online
                Text("🟢 $online অনলাইন", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = GreenOk, fontFamily = NotoSansBengali)
                Text("⚫ $offline অফলাইন", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                Text("👥 ${state.activeUsers.size} মোট", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = Indigo600, fontFamily = NotoSansBengali)
            }
        }

        items(state.activeUsers) { user ->
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Online indicator
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (user.isOnline) GreenOk else Color(0xFFCBD5E1))
                    )

                    Column(Modifier.weight(1f)) {
                        Text(user.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                        Text(user.phone, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali)
                        Text(
                            if (user.isOnline) "🟢 এখন সক্রিয়"
                            else "শেষ সক্রিয়: ${if (user.lastSeen > 0) sdf.format(Date(user.lastSeen)) else "অজানা"}",
                            fontSize = 9.sp,
                            color     = if (user.isOnline) GreenOk else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali
                        )
                    }

                    // View-as button
                    OutlinedButton(
                        onClick = { onViewAs(user.phone) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape   = RoundedCornerShape(8.dp)
                    ) {
                        Text("👁 দেখুন", fontSize = 9.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ── Notify tab ──
@Composable
private fun NotifyTab(
    title    : String, body: String, target: String,
    onTitle  : (String) -> Unit, onBody: (String) -> Unit, onTarget: (String) -> Unit,
    onSend   : () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📣 Notification পাঠান", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)

                OutlinedTextField(
                    value         = title,
                    onValueChange = onTitle,
                    label         = { Text("শিরোনাম", fontFamily = NotoSansBengali) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600)
                )
                OutlinedTextField(
                    value         = body,
                    onValueChange = onBody,
                    label         = { Text("বার্তা", fontFamily = NotoSansBengali) },
                    minLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600)
                )
                OutlinedTextField(
                    value         = target,
                    onValueChange = onTarget,
                    label         = { Text("ফোন নম্বর (ফাঁকা = সবাই)", fontFamily = NotoSansBengali) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    placeholder   = { Text("ফাঁকা রাখলে সবাইকে পাঠাবে", fontFamily = NotoSansBengali,
                        color = Color(0xFFCBD5E1)) }
                )

                Button(
                    onClick  = onSend,
                    enabled  = title.isNotBlank() && body.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Text("📣 পাঠান", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// ── FCM tokens tab ──
@Composable
private fun FcmTab(state: MenuUiState) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("🔑 FCM Token সমূহ", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        val usersWithToken = state.activeUsers.filter { it.fcmToken.isNotBlank() }
        if (usersWithToken.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("কোনো FCM token নেই", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(usersWithToken) { user ->
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(user.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                            Text(user.phone, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            user.fcmToken.take(40) + "...",
                            fontSize  = 9.sp,
                            color     = Indigo600,
                            fontFamily = NotoSansBengali,
                            modifier  = Modifier
                                .background(Color(0xFFF5F3FF), RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  FEATURE 1 — Report Queue Tab
// ══════════════════════════════════════════════════════════════

@Composable
private fun ReportQueueTab(state: MenuUiState, vm: MenuViewModel) {
    val reports = state.reportedQuestions
    val loading = state.isLoadingReports
    val sdf     = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    var editTarget by remember { mutableStateOf<com.hanif.smartstudy.data.remote.ReportedQuestion?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFFF7ED))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Flag, null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("${reports.size}টি pending report", fontFamily = NotoSansBengali,
                fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                color = Color(0xFF9A3412), modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadPendingReports() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF4F46E5))
                    Spacer(Modifier.height(10.dp))
                    Text("লোড হচ্ছে...", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("কোনো pending report নেই!", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 16.sp)
                }
            }
        } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(reports, key = { it.reportKey }) { report ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF4F46E5).copy(0.1f)) {
                                Text(report.tab.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4F46E5), fontFamily = NotoSansBengali)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(sdf.format(Date(report.timestamp)), fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(report.userName.ifBlank { report.userPhone }, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        }
                        Text("❓ " + report.question.take(100) + if (report.question.length > 100) "…" else "",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Medium)
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF1F2)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReportProblem, null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(report.issue, fontSize = 12.sp, color = Color(0xFF9F1239),
                                    fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { editTarget = report },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit প্রশ্ন", fontFamily = NotoSansBengali, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = {
                                vm.resolveReport(report.reportKey, "resolved", report.userPhone, report.question, report.userName, report.questionId, report.tab)
                            }, modifier = Modifier.weight(1f).height(36.dp), shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resolve", fontFamily = NotoSansBengali, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                            IconButton(onClick = {
                                vm.resolveReport(report.reportKey, "dismissed", report.userPhone, report.question, report.userName, report.questionId, report.tab)
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // Edit dialog for reported question
    editTarget?.let { r ->
        var editQ   by remember { mutableStateOf("") }
        var editAns by remember { mutableStateOf("") }
        var editExp by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Report থেকে Edit", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF8FAFC)) {
                        Text("মূল: " + r.question.take(80), Modifier.padding(10.dp),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    }
                    Text("🚩 ${r.issue}", fontSize = 12.sp, color = Color(0xFFEF4444),
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = editQ, onValueChange = { editQ = it },
                        label = { Text("নতুন প্রশ্ন (ফাঁকা = অপরিবর্তিত)", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(value = editAns, onValueChange = { editAns = it },
                        label = { Text("নতুন উত্তর (ফাঁকা = অপরিবর্তিত)", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(value = editExp, onValueChange = { editExp = it },
                        label = { Text("নতুন ব্যাখ্যা (ফাঁকা = অপরিবর্তিত)", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(10.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fields = mutableMapOf<String, String>()
                    if (editQ.isNotBlank())   fields["question"]    = editQ
                    if (editAns.isNotBlank()) fields["correct"]     = editAns
                    if (editExp.isNotBlank()) fields["explanation"] = editExp
                    if (fields.isNotEmpty()) vm.adminEditQuestion(r.sheetName(), r.questionId, fields, r.question)
                    vm.resolveReport(r.reportKey, "resolved", r.userPhone, r.question, r.userName, r.questionId, r.tab)
                    editTarget = null
                }, shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("সংরক্ষণ করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("বাতিল", fontFamily = NotoSansBengali) }
            }
        )
    }
}

// ── New Question Tab ──
private val SHEETS_LIST = listOf("Quiz", "QBank", "Study")
private val AUDIENCE_LIST = listOf(
    "" to "Job Seeker (default)", "Job" to "Job",
    "Honours 1" to "Honours 1st Year", "Honours 2" to "Honours 2nd Year",
    "Honours 3" to "Honours 3rd Year", "Honours 4" to "Honours 4th Year",
    "Masters 1" to "Masters 1st Year", "Masters 2" to "Masters 2nd Year",
    "Class 9" to "Class 9", "Class 10" to "Class 10",
    "Class 11" to "Class 11", "Class 12" to "Class 12"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddQuestionTab(state: MenuUiState, vm: MenuViewModel) {
    var sheet       by remember { mutableStateOf("Quiz") }
    var subject     by remember { mutableStateOf("") }
    var subTopic    by remember { mutableStateOf("") }
    var question    by remember { mutableStateOf("") }
    var optA        by remember { mutableStateOf("") }
    var optB        by remember { mutableStateOf("") }
    var optC        by remember { mutableStateOf("") }
    var optD        by remember { mutableStateOf("") }
    var answer      by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    // ব্যাখ্যা ডিফল্ট Public (সবাই দেখবে) — চাইলে Private (শুধু Admin) করা যাবে
    var explanationPublic by remember { mutableStateOf(true) }
    var technique   by remember { mutableStateOf("") }
    var audience    by remember { mutableStateOf("") }
    var isMcq       by remember { mutableStateOf(true) }
    var audExp      by remember { mutableStateOf(false) }

    val msg    = state.addQuestionMsg
    val saving = state.isAddingQuestion
    val isOk   = msg?.startsWith("✅") == true

    LaunchedEffect(isOk) {
        if (isOk) {
            kotlinx.coroutines.delay(2000)
            question = ""; optA = ""; optB = ""; optC = ""; optD = ""
            answer = ""; explanation = ""; technique = ""; explanationPublic = true
            vm.clearAddQuestionMsg()
        }
    }
    LaunchedEffect(Unit) { vm.loadAdminTaxonomy() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFEEF2FF), RoundedCornerShape(12.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AddCircle, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("নতুন প্রশ্ন যোগ করুন", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF4F46E5))
                Text("Firebase এ সরাসরি push — সব ডিভাইসে দেখাবে",
                    fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Sheet chips
        Text("📂 Sheet", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHEETS_LIST.forEach { s ->
                FilterChip(selected = sheet == s, onClick = { sheet = s; if (s == "Study") isMcq = false },
                    label = { Text(s, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4F46E5), selectedLabelColor = Color.White))
            }
        }

        if (sheet != "Study") {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("প্রশ্নের ধরন:", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = isMcq, onCheckedChange = { isMcq = it })
                Spacer(Modifier.width(8.dp))
                Text(if (isMcq) "MCQ" else "Written", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, color = Color(0xFF4F46E5))
            }
        }

        HorizontalDivider()
        val subjectOptions  = state.adminSubjectsBySheet[sheet].orEmpty()
        val subTopicOptions = state.adminSubTopicsByKey["$sheet|$subject"].orEmpty()
        AdminSubjectField("বিষয় (Subject) *", subject, subjectOptions, { subject = it })
        AdminSubjectField("অধ্যায় (SubTopic)", subTopic, subTopicOptions, { subTopic = it })
        AdminTabField("প্রশ্ন (Question) *", question, { question = it }, 3)

        if (isMcq) {
            Text("📝 Options", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, color = Color(0xFF4F46E5))
            AdminTabField("Option A *", optA, { optA = it })
            AdminTabField("Option B *", optB, { optB = it })
            AdminTabField("Option C", optC, { optC = it })
            AdminTabField("Option D", optD, { optD = it })
            AdminTabField("✅ সঠিক উত্তর (Option এর exact text) *", answer, { answer = it })
        } else {
            AdminTabField("✅ উত্তর *", answer, { answer = it }, 2)
        }
        AdminTabField("💡 ব্যাখ্যা", explanation, { explanation = it }, 2)

        // ── ব্যাখ্যা Public/Private টগল — ডিফল্ট Public ──
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (explanationPublic) Icons.Default.Public else Icons.Default.Lock,
                null,
                tint = if (explanationPublic) Color(0xFF10B981) else Color(0xFFF59E0B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("ব্যাখ্যার ভিজিবিলিটি", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    if (explanationPublic) "Public — সবাই দেখতে পাবে" else "Private — শুধু Admin দেখতে পাবে",
                    fontFamily = NotoSansBengali, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = explanationPublic, onCheckedChange = { explanationPublic = it })
        }

        AdminTabField("🧠 টেকনিক", technique, { technique = it }, 2)

        // Audience dropdown
        ExposedDropdownMenuBox(expanded = audExp, onExpandedChange = { audExp = it }) {
            OutlinedTextField(
                value = AUDIENCE_LIST.find { it.first == audience }?.second ?: "Job Seeker (default)",
                onValueChange = {}, readOnly = true,
                label = { Text("🎯 Audience Tag", fontFamily = NotoSansBengali) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(audExp) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4F46E5))
            )
            ExposedDropdownMenu(expanded = audExp, onDismissRequest = { audExp = false }) {
                AUDIENCE_LIST.forEach { (tag, label) ->
                    DropdownMenuItem(text = { Text(label, fontFamily = NotoSansBengali) },
                        onClick = { audience = tag; audExp = false })
                }
            }
        }

        msg?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold, color = if (isOk) Color(0xFF166534) else Color(0xFF991B1B))
            }
        }

        val isValid = subject.isNotBlank() && question.isNotBlank() && answer.isNotBlank() &&
                (!isMcq || (optA.isNotBlank() && optB.isNotBlank()))

        Button(
            onClick = {
                val fields = mutableMapOf("subject" to subject, "sub_topic" to subTopic,
                    "question" to question, "correct" to answer, "explanation" to explanation,
                    "explanationVisibility" to if (explanationPublic) "public" else "private",
                    "technique" to technique, "AudienceTags" to audience,
                    "type" to if (isMcq) "mcq" else "written")
                if (isMcq) {
                    if (optA.isNotBlank()) fields["option1"] = optA
                    if (optB.isNotBlank()) fields["option2"] = optB
                    if (optC.isNotBlank()) fields["option3"] = optC
                    if (optD.isNotBlank()) fields["option4"] = optD
                }
                vm.adminAddQuestion(sheet, fields)
            },
            enabled = isValid && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(20.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("যোগ হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Firebase এ যোগ করুন ($sheet)", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── Model Test Bulk-Generate Tab ──
// এডমিন শুধু Subject সিলেক্ট করে + কতগুলো Model Test + প্রতিটায় কতগুলো প্রশ্ন
// (পূর্ণমান) দিলে Quiz+QBank pool থেকে অ্যালগরিদম দিয়ে অটো-সিলেক্ট করে
// Firebase-এর "ModelTests/{subject}" নোডে সেভ করে দেয়। SubTopic সিলেক্ট করা লাগে না।
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelTestGenerateTab(state: MenuUiState, vm: MenuViewModel) {
    var subject   by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("5") }
    var perTestText by remember { mutableStateOf("50") }
    var type      by remember { mutableStateOf("both") }

    val msg    = state.modelTestGenMsg
    val saving = state.isGeneratingModelTest
    val isOk   = msg?.startsWith("✅") == true

    LaunchedEffect(Unit) { vm.loadAdminTaxonomy() }

    // Quiz + QBank দুই sheet-এর subject মিলিয়ে suggestion — Model Test দুই সোর্স থেকেই প্রশ্ন নেয়
    val subjectOptions = remember(state.adminSubjectsBySheet) {
        (state.adminSubjectsBySheet["Quiz"].orEmpty() + state.adminSubjectsBySheet["QBank"].orEmpty())
            .distinct().sorted()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF0FDF4), RoundedCornerShape(12.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Quiz, null, tint = Color(0xFF059669), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Model Test বাল্ক-জেনারেট", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF059669))
                Text("Subject সিলেক্ট করো — অটোমেটিক ভালো প্রশ্ন বাছাই করে টেস্ট বানাবে",
                    fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()
        AdminSubjectField("বিষয় (Subject) *", subject, subjectOptions, { subject = it })

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = countText, onValueChange = { countText = it.filter(Char::isDigit) },
                label = { Text("কতগুলো Model Test *", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
            )
            OutlinedTextField(
                value = perTestText, onValueChange = { perTestText = it.filter(Char::isDigit) },
                label = { Text("প্রতিটায় প্রশ্ন (পূর্ণমান) *", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
            )
        }

        Text("📝 প্রশ্নের ধরন (Audience অনুযায়ী প্রি-সেট)", fontFamily = NotoSansBengali,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mcq" to "MCQ", "written" to "Written", "both" to "উভয়").forEach { (v, label) ->
                FilterChip(selected = type == v, onClick = { type = v },
                    label = { Text(label, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF059669), selectedLabelColor = Color.White))
            }
        }
        if (type == "both") {
            Text("ইউজার শুরুতে MCQ / Written বেছে নেওয়ার অপশন দেখবে", fontFamily = NotoSansBengali,
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFFBEB), modifier = Modifier.fillMaxWidth()) {
            Text(
                "অ্যালগরিদম: পুরো subject-এর সব প্রশ্ন (Quiz+QBank) pool করে, প্রতিটা টেস্টে ~৩০-৪০% " +
                "গুরুত্বপূর্ণ (একাধিক Year-এ repeat হওয়া / ম্যানুয়াল ফ্ল্যাগ) প্রশ্ন + বাকিটা কম-ব্যবহৃত normal " +
                "প্রশ্ন দিয়ে ভরে — ফলে টেস্টগুলোয় কিছু কমন, কিছু নতুন প্রশ্ন থাকবে। প্রশ্ন সংখ্যা কম হলে টেস্টের " +
                "মধ্যে ইউনিক প্রশ্নই থাকবে, কিন্তু আলাদা টেস্টের মধ্যে repeat হতে পারে।",
                Modifier.padding(12.dp), fontFamily = NotoSansBengali, fontSize = 11.sp,
                color = Color(0xFF92400E), lineHeight = 16.sp
            )
        }

        msg?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = if (isOk) Color(0xFF166534) else Color(0xFF991B1B))
            }
        }

        val count   = countText.toIntOrNull() ?: 0
        val perTest = perTestText.toIntOrNull() ?: 0
        val isValid = subject.isNotBlank() && count in 1..50 && perTest in 1..300

        Button(
            onClick = { vm.adminGenerateModelTests(subject, count, perTest, type) },
            enabled = isValid && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(20.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("বানানো হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Model Test বানাও", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        Text("⚠️ আগে এই subject-এ Model Test বানানো থাকলে এই নতুন সেট সেগুলো replace করবে (testNumber 1..N)।",
            fontFamily = NotoSansBengali, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
    }
}

// ── Logs tab (Admin) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsTab(state: MenuUiState, vm: MenuViewModel) {
    val sdf      = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    val dateSdf  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeSdf  = SimpleDateFormat("HH:mm", Locale.getDefault())

    var selectedPhone  by remember { mutableStateOf("") }
    var phoneExp       by remember { mutableStateOf(false) }
    var levelFilter    by remember { mutableStateOf("সব") }  // E, W, I, D, সব
    var dateFilter     by remember { mutableStateOf("") }    // "dd/MM/yyyy" or ""
    var showDatePicker by remember { mutableStateOf(false) }

    val levelOptions = listOf("সব", "E", "W", "I", "D")

    // Apply filters
    val filteredLogs = remember(state.debugLogs, levelFilter, dateFilter) {
        state.debugLogs.filter { entry ->
            val levelOk = levelFilter == "সব" || entry.level == levelFilter
            val dateOk  = if (dateFilter.isBlank()) true else {
                dateSdf.format(Date(entry.ts)) == dateFilter
            }
            levelOk && dateOk
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        Text("📋 অ্যাপ লগ (Remote Logcat)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, modifier = Modifier.padding(bottom = 8.dp))

        // Phone filter + refresh
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            ExposedDropdownMenuBox(expanded = phoneExp, onExpandedChange = { phoneExp = it },
                modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = selectedPhone.ifBlank { "নিজের ফোন (default)" },
                    onValueChange = {}, readOnly = true,
                    label = { Text("ফোন নম্বর বাছাই করো", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(phoneExp) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                ExposedDropdownMenu(expanded = phoneExp, onDismissRequest = { phoneExp = false }) {
                    DropdownMenuItem(text = { Text("নিজের ফোন (default)", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                        onClick = { selectedPhone = ""; phoneExp = false; vm.loadDebugLogs("") })
                    state.debugLogPhones.forEach { p ->
                        DropdownMenuItem(text = { Text(p, fontSize = 12.sp) },
                            onClick = { selectedPhone = p; phoneExp = false; vm.loadDebugLogs(p) })
                    }
                }
            }

            OutlinedButton(onClick = { vm.loadDebugLogPhones(); vm.loadDebugLogs(selectedPhone) },
                modifier = Modifier.height(56.dp)) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Filter row: Level chips + Date picker ──
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Level filter chips
            LazyRow(
                modifier              = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(levelOptions.size) { i ->
                    val opt = levelOptions[i]
                    val isActive = levelFilter == opt
                    val chipColor = when (opt) {
                        "E"   -> RedWrong
                        "W"   -> Color(0xFFD97706)
                        "I"   -> Indigo600
                        "D"   -> MaterialTheme.colorScheme.onSurfaceVariant
                        else  -> Indigo600
                    }
                    Surface(
                        onClick  = { levelFilter = opt },
                        shape    = RoundedCornerShape(20.dp),
                        color    = if (isActive) chipColor.copy(0.15f) else Color(0xFFF1F5F9),
                        border   = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, chipColor) else null,
                        modifier = Modifier.height(30.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(opt, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                color = if (isActive) chipColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali)
                        }
                    }
                }
            }

            // Date filter button
            Surface(
                onClick  = { showDatePicker = true },
                shape    = RoundedCornerShape(8.dp),
                color    = if (dateFilter.isNotBlank()) Indigo600.copy(0.12f) else Color(0xFFF1F5F9),
                border   = if (dateFilter.isNotBlank()) androidx.compose.foundation.BorderStroke(1.dp, Indigo600) else null,
                modifier = Modifier.height(30.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(13.dp),
                        tint = if (dateFilter.isNotBlank()) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (dateFilter.isNotBlank()) dateFilter else "তারিখ",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (dateFilter.isNotBlank()) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali
                    )
                    if (dateFilter.isNotBlank()) {
                        Text("✕", fontSize = 10.sp, color = Indigo600,
                            modifier = Modifier.clickable { dateFilter = "" })
                    }
                }
            }
        }

        // Active filter summary
        if (levelFilter != "সব" || dateFilter.isNotBlank()) {
            Text(
                "দেখাচ্ছে: ${filteredLogs.size} / ${state.debugLogs.size} লগ",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        if (state.isLoadingLogs) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.debugLogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("কোনো লগ পাওয়া যায়নি। উপরে \"নিজের ফোন\" বা একটা নম্বর সিলেক্ট করে Refresh দাও।",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        } else if (filteredLogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("এই ফিল্টারে কোনো লগ নেই", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filteredLogs) { entry ->
                    val color = when (entry.level) {
                        "E" -> RedWrong
                        "W" -> Color(0xFFD97706)
                        "I" -> Indigo600
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${entry.level} • ${entry.tag}", fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold, color = color)
                                Text(if (entry.ts > 0) sdf.format(Date(entry.ts)) else "",
                                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(2.dp))
                            SelectionContainer {
                                Text(entry.msg, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialog (manual input — simple approach)
    if (showDatePicker) {
        var inputDate by remember { mutableStateOf(dateFilter) }
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = {
                Text("তারিখ ফিল্টার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("dd/MM/yyyy ফরম্যাটে লিখুন (যেমন: 21/06/2026)",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    OutlinedTextField(
                        value         = inputDate,
                        onValueChange = { inputDate = it },
                        label         = { Text("তারিখ", fontFamily = NotoSansBengali) },
                        singleLine    = true,
                        shape         = RoundedCornerShape(10.dp),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    // Quick date shortcuts from available logs
                    val availableDates = remember(state.debugLogs) {
                        state.debugLogs.map { dateSdf.format(Date(it.ts)) }.distinct().sorted().reversed()
                    }
                    if (availableDates.isNotEmpty()) {
                        Text("দ্রুত বাছাই:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableDates.size) { i ->
                                val d = availableDates[i]
                                Surface(
                                    onClick = { inputDate = d },
                                    shape   = RoundedCornerShape(16.dp),
                                    color   = if (inputDate == d) Indigo600.copy(0.15f) else Color(0xFFF1F5F9),
                                    border  = if (inputDate == d) androidx.compose.foundation.BorderStroke(1.dp, Indigo600) else null
                                ) {
                                    Text(d, fontSize = 11.sp, color = if (inputDate == d) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dateFilter = inputDate.trim(); showDatePicker = false }) {
                    Text("প্রয়োগ করুন", fontFamily = NotoSansBengali, color = Indigo600, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
        )
    }
}

@Composable
private fun AdminTabField(label: String, value: String, onChange: (String) -> Unit, minLines: Int = 1) {
    OutlinedTextField(value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(), minLines = minLines, shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF4F46E5), unfocusedBorderColor = Color(0xFFE2E8F0)),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = NotoSansBengali, fontSize = 13.sp))
}

// ── Subject/SubTopic এর জন্য editable dropdown ──
// সাধারণ OutlinedTextField এর মতোই admin যেকোনো নতুন নাম টাইপ করতে পারবে,
// কিন্তু একইসাথে আগে থেকে database এ থাকা subject/subtopic গুলো dropdown এ
// suggestion হিসেবে দেখাবে — যাতে বানান ভুলে duplicate subject তৈরি না হয়
// (যেমন "গণিত" আর "গনিত" আলাদা subject হয়ে না যায়)।
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminSubjectField(
    label    : String,
    value    : String,
    options  : List<String>,
    onChange : (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, options) {
        if (value.isBlank()) options
        else options.filter { it.contains(value, ignoreCase = true) }
    }
    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp) },
            placeholder = if (options.isEmpty()) null else ({
                Text("টাইপ করুন বা নিচ থেকে বেছে নিন", fontFamily = NotoSansBengali, fontSize = 11.sp)
            }),
            trailingIcon = {
                if (options.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4F46E5), unfocusedBorderColor = Color(0xFFE2E8F0)),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = NotoSansBengali, fontSize = 13.sp)
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
                filtered.take(50).forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        onClick = { onChange(opt); expanded = false }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// ── Bulk Question Uploader ── admin-app এর BulkUploaderPage এর 1:1 পোর্ট।
// একই { } ব্লক / লাইন-বাই-লাইন পার্সিং, একই সেমিকোলন ফরম্যাট (Study/Written/MCQ)।
// পার্থক্য শুধু ডেস্টিনেশন: এখানে সরাসরি Firebase-এ পুশ করার বদলে vm.adminBulkAddQuestions()
// ব্যবহার করা হয়, যেটা AddQuestionTab-এর মতোই লোকাল-ফার্স্ট (in-memory + disk cache এ সাথে
// সাথে দেখায়) এবং অফলাইন/quota-fail হলে PendingQueue-তে জমা রাখে — নেট/quota ঠিক হলে
// SyncWorker অটো সিঙ্ক করে দেয়।
// ══════════════════════════════════════════════════════════════════════════

private data class BulkParseResult(
    val ok: Boolean = false,
    val skip: Boolean = false,
    val err: Boolean = false,
    val reason: String = "",
    val q: String = "",
    val opt1: String = "", val opt2: String = "", val opt3: String = "", val opt4: String = "",
    val correct: String = "",
    val explanation: String = ""
)

// { ... } ব্লক থাকলে সেগুলোই entry, নাহলে প্রতি নন-ফাঁকা লাইন একটা entry
private fun getBulkEntries(raw: String): List<String> {
    val entries = mutableListOf<String>()
    Regex("\\{([\\s\\S]+?)\\}").findAll(raw).forEach { m ->
        val e = m.groupValues[1].trim()
        if (e.isNotEmpty()) entries.add(e)
    }
    if (entries.isNotEmpty()) return entries
    return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}

// mode==="Study" হলে সবসময় Study ফরম্যাট, নাহলে qtype (MCQ/Written) অনুযায়ী
private fun getBulkEffectiveType(mode: String, qtype: String) = if (mode == "Study") "Study" else qtype

private fun parseBulkEntry(entry: String, effectiveType: String): BulkParseResult {
    val tr = entry.trim()
    if (tr.isEmpty() || tr.startsWith("#")) return BulkParseResult(skip = true)

    return when (effectiveType) {
        "Study" -> {
            val si = tr.indexOf(";")
            if (si == -1) return BulkParseResult(err = true, reason = "Study: প্রথম ';' দিয়ে প্রশ্ন ও উত্তর আলাদা করুন")
            val q   = tr.substring(0, si).trim()
            val ans = tr.substring(si + 1).trim()
            if (q.isEmpty())   return BulkParseResult(err = true, reason = "Study: প্রশ্ন খালি")
            if (ans.isEmpty()) return BulkParseResult(err = true, reason = "Study: উত্তর খালি")
            BulkParseResult(ok = true, q = q, correct = ans)
        }
        "Written" -> {
            val si = tr.indexOf(";")
            if (si == -1) return BulkParseResult(err = true, reason = "Written: ';' দিয়ে প্রশ্ন ও উত্তর আলাদা করুন")
            val q    = tr.substring(0, si).trim()
            val rest = tr.substring(si + 1)
            val lastSemi = rest.lastIndexOf(";")
            val ans: String
            val exp: String
            if (lastSemi > 0) {
                ans = rest.substring(0, lastSemi).trim()
                exp = rest.substring(lastSemi + 1).trim()
            } else {
                ans = rest.trim(); exp = ""
            }
            if (q.isEmpty())   return BulkParseResult(err = true, reason = "Written: প্রশ্ন খালি")
            if (ans.isEmpty()) return BulkParseResult(err = true, reason = "Written: উত্তর খালি")
            BulkParseResult(ok = true, q = q, correct = ans, explanation = exp)
        }
        else -> { // MCQ
            val flat  = tr.replace(Regex("\\r?\\n"), " ").replace(Regex("\\s+"), " ")
            val parts = flat.split(";").map { it.trim() }
            if (parts.size < 6) return BulkParseResult(err = true,
                reason = "MCQ: ${parts.size}টি কলাম পেয়েছি, দরকার কমপক্ষে ৬টি (প্রশ্ন;অপ১;অপ২;অপ৩;অপ৪;উত্তর)")
            if (parts[0].isEmpty()) return BulkParseResult(err = true, reason = "MCQ: প্রশ্ন খালি")
            if (parts[5].isEmpty()) return BulkParseResult(err = true, reason = "MCQ: সঠিক উত্তর খালি")
            BulkParseResult(ok = true, q = parts[0], opt1 = parts[1], opt2 = parts[2], opt3 = parts[3], opt4 = parts[4],
                correct = parts[5], explanation = parts.getOrElse(6) { "" })
        }
    }
}

// AddQuestionTab-এর সাবমিট করা fields map এর সাথে হুবহু একই shape
private fun buildBulkFields(
    item: BulkParseResult, subject: String, subTopic: String, mode: String, qtype: String,
    audience: String, explanationPublic: Boolean
): Map<String, String> {
    val effType = getBulkEffectiveType(mode, qtype)
    val isMcq   = effType == "MCQ"
    val fields  = mutableMapOf(
        "subject" to subject, "sub_topic" to subTopic.ifBlank { subject },
        "question" to item.q, "correct" to item.correct,
        "explanation" to item.explanation,
        "explanationVisibility" to if (explanationPublic) "public" else "private",
        "technique" to "", "AudienceTags" to audience,
        "type" to if (mode == "Study") "study" else if (isMcq) "mcq" else "written"
    )
    if (isMcq) {
        if (item.opt1.isNotBlank()) fields["option1"] = item.opt1
        if (item.opt2.isNotBlank()) fields["option2"] = item.opt2
        if (item.opt3.isNotBlank()) fields["option3"] = item.opt3
        if (item.opt4.isNotBlank()) fields["option4"] = item.opt4
    }
    return fields
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkUploaderTab(state: MenuUiState, vm: MenuViewModel) {
    var mode        by remember { mutableStateOf("Quiz") }
    var qtype       by remember { mutableStateOf("MCQ") }
    var subject     by remember { mutableStateOf("") }
    var subTopic    by remember { mutableStateOf("") }
    var audience    by remember { mutableStateOf("") }
    var bulkText    by remember { mutableStateOf("") }
    var explanationPublic by remember { mutableStateOf(true) }
    var audExp      by remember { mutableStateOf(false) }

    val effType = getBulkEffectiveType(mode, qtype)
    val parsedRows = remember(bulkText, effType) {
        if (bulkText.isBlank()) emptyList() else getBulkEntries(bulkText).map { parseBulkEntry(it, effType) }
    }
    val okCount   = parsedRows.count { it.ok }
    val errCount  = parsedRows.count { it.err }
    val skipCount = parsedRows.count { it.skip }

    val running = state.isBulkUploading
    val pct = if (state.bulkUploadTotal > 0) state.bulkUploadDone.toFloat() / state.bulkUploadTotal else 0f

    LaunchedEffect(Unit) { vm.loadAdminTaxonomy() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text("⚡ বাল্ক প্রশ্ন আপলোড", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text("একসাথে অনেক প্রশ্ন যোগ করুন — নেট/Firebase কোটা না থাকলেও সাথে সাথে লোকালি সেভ ও দেখা যাবে, ঠিক হলে অটো sync হবে",
                fontFamily = NotoSansBengali, fontSize = 11.sp, color = Color.White.copy(alpha = .9f))
        }

        // Sheet chips
        Text("📂 Sheet", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHEETS_LIST.forEach { s ->
                FilterChip(selected = mode == s, onClick = { mode = s }, enabled = !running,
                    label = { Text(s, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4F46E5), selectedLabelColor = Color.White))
            }
        }

        if (mode != "Study") {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("প্রশ্নের ধরন:", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = qtype == "MCQ", onCheckedChange = { qtype = if (it) "MCQ" else "Written" }, enabled = !running)
                Spacer(Modifier.width(8.dp))
                Text(qtype, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4F46E5))
            }
        }

        HorizontalDivider()
        val subjectOptions  = state.adminSubjectsBySheet[mode].orEmpty()
        val subTopicOptions = state.adminSubTopicsByKey["$mode|$subject"].orEmpty()
        AdminSubjectField("বিষয় (Subject) *", subject, subjectOptions, { subject = it })
        AdminSubjectField("অধ্যায় (SubTopic)", subTopic, subTopicOptions, { subTopic = it })

        // Audience dropdown
        ExposedDropdownMenuBox(expanded = audExp, onExpandedChange = { audExp = it }) {
            OutlinedTextField(
                value = AUDIENCE_LIST.find { it.first == audience }?.second ?: "Job Seeker (default)",
                onValueChange = {}, readOnly = true,
                label = { Text("🎯 Audience Tag", fontFamily = NotoSansBengali) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(audExp) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4F46E5))
            )
            ExposedDropdownMenu(expanded = audExp, onDismissRequest = { audExp = false }) {
                AUDIENCE_LIST.forEach { (tag, label) ->
                    DropdownMenuItem(text = { Text(label, fontFamily = NotoSansBengali) },
                        onClick = { audience = tag; audExp = false })
                }
            }
        }

        // ব্যাখ্যা Public/Private টগল
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (explanationPublic) Icons.Default.Public else Icons.Default.Lock, null,
                tint = if (explanationPublic) Color(0xFF10B981) else Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("ব্যাখ্যার ভিজিবিলিটি (সবগুলোর জন্য)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(if (explanationPublic) "Public — সবাই দেখতে পাবে" else "Private — শুধু Admin দেখতে পাবে",
                    fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = explanationPublic, onCheckedChange = { explanationPublic = it })
        }

        // ফরম্যাট হিন্ট
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFEEF2FF), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("📋 প্রতিটি প্রশ্নের ফরম্যাট", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = Color(0xFF4F46E5))
                val hint = when (effType) {
                    "Study"   -> "প্রশ্ন ; উত্তর"
                    "Written" -> "প্রশ্ন ; উত্তর ; ব্যাখ্যা (ঐচ্ছিক)"
                    else      -> "প্রশ্ন ; অপশন১ ; অপশন২ ; অপশন৩ ; অপশন৪ ; সঠিক উত্তর ; ব্যাখ্যা (ঐচ্ছিক)"
                }
                Text(hint, fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("প্রতিটি প্রশ্ন { } দিয়ে ঘিরে দিন, অথবা প্রতি লাইনে একটা করে লিখুন। # দিয়ে শুরু হওয়া লাইন বাদ যাবে।",
                    fontFamily = NotoSansBengali, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("📝 প্রশ্নগুলো পেস্ট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = bulkText, onValueChange = { bulkText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4F46E5)),
            enabled = !running,
            placeholder = { Text("{ প্রশ্ন ; অপশন১ ; অপশন২ ; অপশন৩ ; অপশন৪ ; উত্তর }", fontFamily = NotoSansBengali, fontSize = 11.sp) }
        )

        // Validation chips
        if (parsedRows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("মোট ${parsedRows.size}", fontFamily = NotoSansBengali, fontSize = 11.sp) })
                AssistChip(onClick = {}, label = { Text("✅ $okCount", fontFamily = NotoSansBengali, fontSize = 11.sp, color = GreenOk) })
                if (errCount > 0) AssistChip(onClick = {},
                    label = { Text("❌ $errCount", fontFamily = NotoSansBengali, fontSize = 11.sp, color = RedWrong) })
                if (skipCount > 0) AssistChip(onClick = {},
                    label = { Text("⏭ $skipCount", fontFamily = NotoSansBengali, fontSize = 11.sp) })
            }
            val errorRows = parsedRows.filter { it.err }.take(6)
            if (errorRows.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF1F2), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        errorRows.forEach {
                            Text("• ${it.reason}", fontFamily = NotoSansBengali, fontSize = 11.sp, color = Color(0xFF991B1B))
                        }
                        if (errCount > errorRows.size) {
                            Text("...আরও ${errCount - errorRows.size}টি ভুল আছে", fontFamily = NotoSansBengali,
                                fontSize = 10.sp, color = Color(0xFF991B1B))
                        }
                    }
                }
            }
        }

        // Progress
        if (running || state.bulkUploadTotal > 0) {
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color(0xFF4F46E5))
                Spacer(Modifier.height(4.dp))
                Text("${state.bulkUploadDone}/${state.bulkUploadTotal}  •  ✅ সফল ${state.bulkUploadSent}  •  ⚠️ pending/offline ${state.bulkUploadFailed}",
                    fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        state.bulkUploadResultMsg?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF0FDF4), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
            }
        }

        // Live log
        if (state.bulkUploadLog.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF0F172A), modifier = Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.padding(8.dp).heightIn(max = 200.dp)) {
                    items(state.bulkUploadLog.takeLast(40).reversed()) { line ->
                        Text(line, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 10.sp, color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }

        val isValid = subject.isNotBlank() && okCount > 0

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val entries = parsedRows.filter { it.ok }
                        .map { row -> buildBulkFields(row, subject, subTopic, mode, qtype, audience, explanationPublic) }
                    vm.adminBulkAddQuestions(mode, entries)
                },
                enabled = isValid && !running,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(20.dp), Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("আপলোড হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Submit ($okCount টি প্রশ্ন)", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
            if (running) {
                OutlinedButton(onClick = { vm.adminStopBulkUpload() },
                    modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("⛔ বন্ধ", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!running && (state.bulkUploadTotal > 0 || bulkText.isNotBlank())) {
            TextButton(onClick = { bulkText = ""; vm.adminClearBulkUploadResult() }) {
                Text("🔄 রিসেট করুন (নতুন ব্যাচ)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── Bulk Audience Tag Tab ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkAudienceTab(state: MenuUiState, vm: MenuViewModel) {
    var sheet     by remember { mutableStateOf("Quiz") }
    var subject   by remember { mutableStateOf("") }
    var subTopic  by remember { mutableStateOf("") }
    var newTag    by remember { mutableStateOf("") }
    var tagExp    by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }

    val msg      = state.bulkUpdateMsg
    val updating = state.isBulkUpdating
    val isOk     = msg?.startsWith("✅") == true

    LaunchedEffect(isOk) {
        if (isOk) { kotlinx.coroutines.delay(3000); vm.clearBulkMsg(); subject = ""; subTopic = ""; confirmed = false }
    }
    LaunchedEffect(Unit) { vm.loadAdminTaxonomy() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFFBEB),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("সাবধান! Bulk Operation", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF92400E))
                    Text("একসাথে অনেক প্রশ্নের AudienceTag পরিবর্তন হবে।",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF92400E))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHEETS_LIST.forEach { s ->
                FilterChip(selected = sheet == s, onClick = { sheet = s },
                    label = { Text(s, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4F46E5), selectedLabelColor = Color.White))
            }
        }

        val subjectOptions  = state.adminSubjectsBySheet[sheet].orEmpty()
        val subTopicOptions = state.adminSubTopicsByKey["$sheet|$subject"].orEmpty()

        AdminSubjectField("বিষয় (Subject) *", subject, subjectOptions, { subject = it })
        AdminSubjectField("অধ্যায় (SubTopic) — ফাঁকা = সব অধ্যায়", subTopic, subTopicOptions, { subTopic = it })

        ExposedDropdownMenuBox(expanded = tagExp, onExpandedChange = { tagExp = it }) {
            OutlinedTextField(
                value = AUDIENCE_LIST.find { it.first == newTag }?.second ?: "বেছে নিন",
                onValueChange = {}, readOnly = true,
                label = { Text("নতুন Audience Tag *", fontFamily = NotoSansBengali) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tagExp) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4F46E5))
            )
            ExposedDropdownMenu(expanded = tagExp, onDismissRequest = { tagExp = false }) {
                AUDIENCE_LIST.forEach { (tag, label) ->
                    DropdownMenuItem(text = { Text(label, fontFamily = NotoSansBengali) },
                        onClick = { newTag = tag; tagExp = false })
                }
            }
        }

        if (subject.isNotBlank()) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFEEF2FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4F46E5).copy(0.3f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📋 Summary:", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4F46E5))
                    Text("Sheet: $sheet | Subject: $subject", fontFamily = NotoSansBengali, fontSize = 12.sp)
                    Text("SubTopic: ${subTopic.ifBlank { "সব অধ্যায়" }}", fontFamily = NotoSansBengali, fontSize = 12.sp)
                    Text("নতুন Tag: \"${newTag.ifBlank { "Job Seeker (খালি)" }}\"",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFFF1F2), RoundedCornerShape(10.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it },
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFEF4444)))
            Spacer(Modifier.width(8.dp))
            Text("আমি নিশ্চিত যে এই Bulk আপডেট করতে চাই।",
                fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = Color(0xFF9F1239), fontWeight = FontWeight.Bold)
        }

        msg?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold, color = if (isOk) Color(0xFF166534) else Color(0xFF991B1B))
            }
        }

        Button(
            onClick = { vm.adminBulkAudienceUpdate(sheet, subject, subTopic, newTag) },
            enabled = subject.isNotBlank() && newTag.isNotEmpty() && confirmed && !updating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
            if (updating) {
                CircularProgressIndicator(Modifier.size(20.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("আপডেট হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Bolt, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bulk Update চালান 🚀", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── Rename Subject / SubTopic Tab ──
@Composable
private fun RenameTab(state: MenuUiState, vm: MenuViewModel) {
    // কোন sheet(গুলো) এ rename হবে — multi-select, ডিফল্ট সবগুলো বেছে নেওয়া
    val selectedSheets = remember { mutableStateListOf("Quiz", "QBank", "Study") }

    var renameSubTopic by remember { mutableStateOf(false) }  // false = Subject rename, true = SubTopic rename
    var oldSubject  by remember { mutableStateOf("") }
    var oldSubTopic by remember { mutableStateOf("") }
    var newName     by remember { mutableStateOf("") }
    var confirmed   by remember { mutableStateOf(false) }

    val msg      = state.renameMsg
    val updating = state.isRenaming
    val isOk     = msg?.startsWith("✅") == true

    LaunchedEffect(isOk) {
        if (isOk) {
            kotlinx.coroutines.delay(3000)
            vm.clearRenameMsg()
            oldSubject = ""; oldSubTopic = ""; newName = ""; confirmed = false
        }
    }
    LaunchedEffect(Unit) { vm.loadAdminTaxonomy() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFEEF2FF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4F46E5).copy(0.3f))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Edit, null, tint = Indigo600, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("বিষয় / অধ্যায় Rename করুন", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Indigo600)
                    Text("সব প্রশ্নের subject/sub_topic একসাথে বদলে যাবে — প্রশ্ন/উত্তর অপরিবর্তিত থাকবে।",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF4338CA))
                }
            }
        }

        // কী rename হবে — Subject নাকি SubTopic
        Text("কী Rename করবেন?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !renameSubTopic, onClick = { renameSubTopic = false },
                label = { Text("📚 Subject", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Indigo600, selectedLabelColor = Color.White))
            FilterChip(selected = renameSubTopic, onClick = { renameSubTopic = true },
                label = { Text("📖 SubTopic (অধ্যায়)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Indigo600, selectedLabelColor = Color.White))
        }

        // Sheet বেছে নেওয়া — multi-select chips, "সব" সহ
        Text("কোন Sheet(গুলো) এ?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHEETS_LIST.forEach { s ->
                val isSel = selectedSheets.contains(s)
                FilterChip(
                    selected = isSel,
                    onClick = {
                        if (isSel) selectedSheets.remove(s) else selectedSheets.add(s)
                    },
                    label = { Text(s, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0891B2), selectedLabelColor = Color.White)
                )
            }
        }
        Text(
            if (selectedSheets.isEmpty()) "⚠️ অন্তত একটি sheet বেছে নিন"
            else "নির্বাচিত: ${selectedSheets.joinToString(", ")}",
            fontFamily = NotoSansBengali, fontSize = 11.sp,
            color = if (selectedSheets.isEmpty()) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // নির্বাচিত sheet গুলোর সব subject মিলিয়ে suggestion দেখাবে
        val subjectOptions = remember(selectedSheets.toList(), state.adminSubjectsBySheet) {
            selectedSheets.flatMap { state.adminSubjectsBySheet[it].orEmpty() }.distinct().sorted()
        }
        val subTopicOptions = remember(selectedSheets.toList(), oldSubject, state.adminSubTopicsByKey) {
            selectedSheets.flatMap { state.adminSubTopicsByKey["$it|$oldSubject"].orEmpty() }.distinct().sorted()
        }

        AdminSubjectField("বর্তমান বিষয় (Subject) *", oldSubject, subjectOptions, { oldSubject = it })

        if (renameSubTopic) {
            AdminSubjectField("বর্তমান অধ্যায় (SubTopic) *", oldSubTopic, subTopicOptions, { oldSubTopic = it })
        } else {
            Text(
                "ℹ️ SubTopic ফাঁকা রাখলে এই বিষয়ের সব অধ্যায়সহ পুরো Subject rename হবে",
                fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AdminTabField(
            if (renameSubTopic) "নতুন অধ্যায়ের নাম *" else "নতুন বিষয়ের নাম *",
            newName, { newName = it }
        )

        val canSubmit = selectedSheets.isNotEmpty() && oldSubject.isNotBlank() &&
            newName.isNotBlank() && (!renameSubTopic || oldSubTopic.isNotBlank())

        if (canSubmit) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF0FDF4),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenOk.copy(0.3f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📋 Summary:", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = GreenOk)
                    Text("Sheet: ${selectedSheets.joinToString(", ")}", fontFamily = NotoSansBengali, fontSize = 12.sp)
                    if (renameSubTopic) {
                        Text("Subject: $oldSubject", fontFamily = NotoSansBengali, fontSize = 12.sp)
                        Text("\"$oldSubTopic\" → \"$newName\"", fontFamily = NotoSansBengali,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenOk)
                    } else {
                        Text("\"$oldSubject\" → \"$newName\"" + if (oldSubTopic.isNotBlank()) " (শুধু \"$oldSubTopic\" অধ্যায়ে)" else " (সব অধ্যায়সহ)",
                            fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenOk)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFFF1F2), RoundedCornerShape(10.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it },
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFEF4444)))
            Spacer(Modifier.width(8.dp))
            Text("আমি নিশ্চিত যে এই Rename করতে চাই।",
                fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = Color(0xFF9F1239), fontWeight = FontWeight.Bold)
        }

        msg?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold, color = if (isOk) Color(0xFF166534) else Color(0xFF991B1B))
            }
        }

        Button(
            onClick = {
                vm.adminRenameSubjectOrTopic(
                    sheets         = selectedSheets.toList(),
                    oldSubject     = oldSubject,
                    oldSubTopic    = if (renameSubTopic) oldSubTopic else "",
                    newName        = newName,
                    renameSubTopic = renameSubTopic
                )
            },
            enabled = canSubmit && confirmed && !updating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
        ) {
            if (updating) {
                CircularProgressIndicator(Modifier.size(20.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Rename হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rename করুন ✏️", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── Pending Sync Tab ──
@Composable
private fun PendingSyncTab(state: MenuUiState, vm: MenuViewModel) {
    val pending  = state.pendingEdits
    val syncing  = state.isSyncingEdits
    val syncMsg  = state.syncEditsMsg
    val gson     = remember { com.google.gson.Gson() }
    val sdf      = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()) }

    LaunchedEffect(syncMsg) {
        if (syncMsg != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearSyncEditsMsg()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

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
                Text("⏳", fontSize = 28.sp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pending.isEmpty()) "কোনো pending edit নেই" else "${pending.size}টি edit sync হয়নি",
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp, color = Color(0xFF1E1B4B)
                    )
                    Text(
                        "Offline এ করা edit গুলো এখানে জমা থাকে",
                        fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Badge
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
            enabled  = !syncing,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (pending.isEmpty()) Color(0xFF94A3B8) else Color(0xFF4F46E5)
            )
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

        // Pending list
        if (pending.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("সব edit sync হয়ে গেছে!", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = GreenOk, fontSize = 15.sp)
                }
            }
        } else {
            Text(
                "pending edits:",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pending, key = { it.id }) { action ->
                    val payload = try {
                        gson.fromJson(action.payload, Map::class.java)
                    } catch (e: Exception) { emptyMap<String, Any>() }

                    val sheet    = payload["sheet"]?.toString() ?: "?"
                    val preview  = payload["questionPreview"]?.toString() ?: ""
                    val retry    = action.retryCount

                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Status icon
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (retry > 0) Color(0xFFFFF7ED) else Color(0xFFEEF2FF)
                            ) {
                                Text(
                                    if (retry > 0) "⚠️" else "📴",
                                    Modifier.padding(6.dp), fontSize = 16.sp
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF4F46E5).copy(0.1f)) {
                                        Text(sheet, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF4F46E5), fontFamily = NotoSansBengali)
                                    }
                                    Text(sdf.format(java.util.Date(action.createdAt)),
                                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                                    if (retry > 0) {
                                        Text("retry: $retry", fontSize = 9.sp,
                                            color = Color(0xFFEA580C), fontFamily = NotoSansBengali,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    preview.ifBlank { "প্রশ্ন preview নেই" },
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = NotoSansBengali,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
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
        CheckItem(false, true,
            "Firebase DB Secret → User-auth-only migration",
            "FirebaseTokenProvider.kt-এ এখন legacy fallback আছে:\n" +
            "কোনো signed-in user না থাকলে FIREBASE_DB_SECRET সরাসরি REST call-এ ব্যবহার হয়।\n" +
            "DB Secret মানে সম্পূর্ণ database access — এটা app-এ থাকা বিপজ্জনক।\n" +
            "→ Google Sign-In কে Firebase Auth-এর সাথে properly link করো\n" +
            "   যাতে সবসময় Firebase ID token ব্যবহার হয়, DB secret নয়।",
            "app/.../data/remote/FirebaseTokenProvider.kt — legacyFallback()"
        ),
        CheckItem(false, true,
            "Firebase Rules — Users node সবাই পড়তে পারছে",
            "firebase-database-rules.json-এ:\n" +
            "\"Users\": { \".read\": \"auth != null\" }\n" +
            "→ যেকোনো authenticated user সব user-এর data পড়তে পারছে!\n" +
            "   Phone number, name, XP সব expose।\n" +
            "→ Fix: প্রতিটা user শুধু নিজেরটা পড়তে পারবে:\n" +
            "   \"\$userId\": { \".read\": \"auth.uid === \$userId\" }",
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
        CheckItem(false, false,
            "RemoteLogger production-এ disable করো",
            "RemoteLogger.kt Firebase-এ debug log লিখছে — DB Secret দিয়ে।\n" +
            "→ Production-এ এটা বন্ধ রাখো বা REALTIME_DATA check দিয়ে guard করো।",
            "app/.../util/RemoteLogger.kt"
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
