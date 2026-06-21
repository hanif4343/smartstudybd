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
private val SlateText = Color(0xFF1E293B)
private val MutedText = Color(0xFF64748B)
private val CardBg    = Color(0xFFFFFFFF)
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
                listOf("👥 ইউজার", "📣 Notify", "🔑 FCM", "🚩 Reports", "➕ নতুন প্রশ্ন", "🌐 Bulk Tag", "✏️ Rename", "📋 Logs", "⏳ Sync")
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
            // Auto-load debug log phone list when tab 7 opens
            LaunchedEffect(tab) { if (tab == 7) vm.loadDebugLogPhones() }

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
                5 -> BulkAudienceTab(state, vm)
                6 -> RenameTab(state, vm)
                7 -> LogsTab(state, vm)
                8 -> PendingSyncTab(state, vm)
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
                    color = MutedText, fontWeight = FontWeight.Bold)
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
                    color = MutedText, fontFamily = NotoSansBengali)
                Text("👥 ${state.activeUsers.size} মোট", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = Indigo600, fontFamily = NotoSansBengali)
            }
        }

        items(state.activeUsers) { user ->
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(1.dp)
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
                            color = SlateText, fontFamily = NotoSansBengali)
                        Text(user.phone, fontSize = 10.sp, color = MutedText,
                            fontFamily = NotoSansBengali)
                        Text(
                            if (user.isOnline) "🟢 এখন সক্রিয়"
                            else "শেষ সক্রিয়: ${if (user.lastSeen > 0) sdf.format(Date(user.lastSeen)) else "অজানা"}",
                            fontSize = 9.sp,
                            color     = if (user.isOnline) GreenOk else MutedText,
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
            colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📣 Notification পাঠান", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = SlateText, fontFamily = NotoSansBengali)

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
                color = SlateText, fontFamily = NotoSansBengali,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        val usersWithToken = state.activeUsers.filter { it.fcmToken.isNotBlank() }
        if (usersWithToken.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("কোনো FCM token নেই", fontFamily = NotoSansBengali, color = MutedText)
                }
            }
        } else {
            items(usersWithToken) { user ->
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(CardBg)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(user.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = SlateText, fontFamily = NotoSansBengali)
                            Text(user.phone, fontSize = 10.sp, color = MutedText,
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
                    Text("লোড হচ্ছে...", fontFamily = NotoSansBengali, color = Color(0xFF64748B))
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
                    colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                color = Color(0xFF64748B), fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Person, null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(report.userName.ifBlank { report.userPhone }, fontSize = 10.sp,
                                color = Color(0xFF64748B), fontFamily = NotoSansBengali)
                        }
                        Text("❓ " + report.question.take(100) + if (report.question.length > 100) "…" else "",
                            fontSize = 12.sp, color = Color(0xFF1E293B), fontFamily = NotoSansBengali,
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
                                Icon(Icons.Default.Close, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
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
                            fontSize = 11.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali)
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
            answer = ""; explanation = ""; technique = ""
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
                    fontFamily = NotoSansBengali, fontSize = 11.sp, color = Color(0xFF64748B))
            }
        }

        // Sheet chips
        Text("📂 Sheet", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
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

// ── Logs tab (Admin) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsTab(state: MenuUiState, vm: MenuViewModel) {
    val sdf = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    var selectedPhone by remember { mutableStateOf("") }
    var phoneExp by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        Text("📋 অ্যাপ লগ (Remote Logcat)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = SlateText, fontFamily = NotoSansBengali, modifier = Modifier.padding(bottom = 8.dp))

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
                        fontFamily = NotoSansBengali, fontSize = 12.sp, color = MutedText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.debugLogs) { entry ->
                    val color = when (entry.level) {
                        "E" -> RedWrong
                        "W" -> Color(0xFFD97706)
                        "I" -> Indigo600
                        else -> MutedText
                    }
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(CardBg)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${entry.level} • ${entry.tag}", fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold, color = color)
                                Text(if (entry.ts > 0) sdf.format(Date(entry.ts)) else "",
                                    fontSize = 9.sp, color = MutedText)
                            }
                            Spacer(Modifier.height(2.dp))
                            SelectionContainer {
                                Text(entry.msg, fontSize = 10.sp, color = SlateText,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
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
            color = if (selectedSheets.isEmpty()) Color(0xFFEF4444) else MutedText
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
                fontFamily = NotoSansBengali, fontSize = 11.sp, color = MutedText
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
                        fontFamily = NotoSansBengali, fontSize = 11.sp, color = MutedText
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
                fontSize = 12.sp, color = MutedText
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
                        colors = CardDefaults.cardColors(CardBg),
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
                                        fontSize = 9.sp, color = MutedText, fontFamily = NotoSansBengali)
                                    if (retry > 0) {
                                        Text("retry: $retry", fontSize = 9.sp,
                                            color = Color(0xFFEA580C), fontFamily = NotoSansBengali,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    preview.ifBlank { "প্রশ্ন preview নেই" },
                                    fontSize = 11.sp, color = SlateText,
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
