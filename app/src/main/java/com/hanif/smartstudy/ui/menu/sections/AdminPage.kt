package com.hanif.smartstudy.ui.menu.sections

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
                listOf("👥 ইউজার", "📣 Notify", "🔑 FCM", "🚩 Reports", "➕ নতুন প্রশ্ন", "🌐 Bulk Tag").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold) })
                }
            }

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
            }

            // Report queue auto-load যখন tab 3 খোলা হয়
            LaunchedEffect(tab) {
                if (tab == 3) vm.loadPendingReports()
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
                OutlinedButton(onClick = { vm.loadAll() }) {
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

// ═══════════════════════════════════════════════════════════════
//  FEATURE 1 ─ Report Queue Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ReportQueueTab(state: MenuUiState, vm: MenuViewModel) {
    val reports = state.reportedQuestions
    val loading = state.isLoadingReports
    val sdf     = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    // Edit dialog state
    var editTarget by remember { mutableStateOf<com.hanif.smartstudy.data.remote.ReportedQuestion?>(null) }

    Column(Modifier.fillMaxSize()) {

        // Header bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF7ED))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Flag, null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "মোট ${reports.size}টি pending report",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, color = Color(0xFF9A3412), modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.loadPendingReports() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Indigo600)
                    Spacer(Modifier.height(12.dp))
                    Text("Reports লোড হচ্ছে...", fontFamily = NotoSansBengali, color = MutedText)
                }
            }
            return@Column
        }

        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("কোনো pending report নেই!", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold, color = GreenOk, fontSize = 16.sp)
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(reports, key = { it.reportKey }) { report ->
                ReportCard(
                    report  = report,
                    sdf     = sdf,
                    onEdit  = { editTarget = report },
                    onResolve  = { vm.resolveReport(report.reportKey, "resolved") },
                    onDismiss  = { vm.resolveReport(report.reportKey, "dismissed") }
                )
            }
        }
    }

    // Inline edit dialog for the reported question
    editTarget?.let { r ->
        ReportEditDialog(
            report    = r,
            onDismiss = { editTarget = null },
            onSave    = { fields ->
                vm.adminEditQuestion(r.sheetName(), r.questionId, fields)
                vm.resolveReport(r.reportKey, "resolved")
                editTarget = null
            }
        )
    }
}

@Composable
private fun ReportCard(
    report   : com.hanif.smartstudy.data.remote.ReportedQuestion,
    sdf      : SimpleDateFormat,
    onEdit   : () -> Unit,
    onResolve: () -> Unit,
    onDismiss: () -> Unit
) {
    val tabColor = when (report.tab) {
        "qbank" -> Color(0xFF7C3AED)
        "study" -> Color(0xFF0891B2)
        else    -> Indigo600
    }
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Top row: tab badge + time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = tabColor.copy(0.1f)) {
                    Text(
                        report.tab.uppercase(),
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        color = tabColor, fontFamily = NotoSansBengali
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    sdf.format(Date(report.timestamp)),
                    fontSize = 10.sp, color = MutedText, fontFamily = NotoSansBengali,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.Person, null, tint = MutedText, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(report.userName.ifBlank { report.userPhone }, fontSize = 10.sp, color = MutedText,
                    fontFamily = NotoSansBengali)
            }

            // Question preview
            Text(
                "❓ " + report.question.take(120) + if (report.question.length > 120) "…" else "",
                fontSize = 12.sp, color = SlateText, fontFamily = NotoSansBengali,
                fontWeight = FontWeight.Medium
            )

            // Issue
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF1F2)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ReportProblem, null, tint = RedWrong, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        report.issue,
                        fontSize = 12.sp, color = Color(0xFF9F1239),
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Edit প্রশ্ন
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit প্রশ্ন", fontFamily = NotoSansBengali, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                }
                // Resolve
                OutlinedButton(
                    onClick = onResolve,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape  = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenOk)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Resolve", fontFamily = NotoSansBengali, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = GreenOk)
                }
                // Dismiss
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, null, tint = MutedText, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ReportEditDialog(
    report    : com.hanif.smartstudy.data.remote.ReportedQuestion,
    onDismiss : () -> Unit,
    onSave    : (Map<String, String>) -> Unit
) {
    var editQ   by remember { mutableStateOf("") }
    var editAns by remember { mutableStateOf("") }
    var editExp by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = Indigo600, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report থেকে সরাসরি Edit", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Original question (read-only)
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF8FAFC)) {
                    Text(
                        "মূল: " + report.question.take(100),
                        Modifier.padding(10.dp),
                        fontSize = 11.sp, color = MutedText, fontFamily = NotoSansBengali
                    )
                }
                Text(
                    "🚩 অভিযোগ: ${report.issue}",
                    fontSize = 12.sp, color = RedWrong,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value         = editQ,
                    onValueChange = { editQ = it },
                    label         = { Text("নতুন প্রশ্ন (ফাঁকা = অপরিবর্তিত)", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 2,
                    shape         = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value         = editAns,
                    onValueChange = { editAns = it },
                    label         = { Text("নতুন উত্তর (ফাঁকা = অপরিবর্তিত)", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value         = editExp,
                    onValueChange = { editExp = it },
                    label         = { Text("নতুন ব্যাখ্যা (ফাঁকা = অপরিবর্তিত)", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 2,
                    shape         = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fields = mutableMapOf<String, String>()
                    if (editQ.isNotBlank())   fields["question"]    = editQ
                    if (editAns.isNotBlank()) fields["correct"]     = editAns
                    if (editExp.isNotBlank()) fields["explanation"] = editExp
                    onSave(fields)
                },
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("সংরক্ষণ করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", fontFamily = NotoSansBengali)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════
//  FEATURE 2 ─ Add New Question Tab
// ═══════════════════════════════════════════════════════════════

private val SHEETS = listOf("Quiz", "QBank", "Study")
private val AUDIENCE_OPTIONS = listOf(
    "" to "Job Seeker (default)",
    "Job" to "Job",
    "Honours 1" to "Honours 1st Year",
    "Honours 2" to "Honours 2nd Year",
    "Honours 3" to "Honours 3rd Year",
    "Honours 4" to "Honours 4th Year",
    "Masters 1" to "Masters 1st Year",
    "Masters 2" to "Masters 2nd Year",
    "Class 9"   to "Class 9",
    "Class 10"  to "Class 10",
    "Class 11"  to "Class 11",
    "Class 12"  to "Class 12"
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

    var sheetExpanded by remember { mutableStateOf(false) }
    var audExpanded   by remember { mutableStateOf(false) }

    val msg     = state.addQuestionMsg
    val saving  = state.isAddingQuestion
    val isOk    = msg?.startsWith("✅") == true

    LaunchedEffect(isOk) {
        if (isOk) {
            kotlinx.coroutines.delay(2000)
            // Reset form
            question = ""; optA = ""; optB = ""; optC = ""; optD = ""
            answer = ""; explanation = ""; technique = ""
            vm.clearAddQuestionMsg()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFEEF2FF), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AddCircle, null, tint = Indigo600, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("নতুন প্রশ্ন যোগ করুন", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Indigo600)
                Text("Firebase এ সরাসরি push হবে — সব ডিভাইসে দেখাবে",
                    fontFamily = NotoSansBengali, fontSize = 11.sp, color = MutedText)
            }
        }

        // Sheet selector
        Text("📂 Sheet (কোথায় যোগ করবেন?)", fontFamily = NotoSansBengali,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SlateText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHEETS.forEach { s ->
                val selected = sheet == s
                FilterChip(
                    selected = selected,
                    onClick  = { sheet = s; if (s == "Study") isMcq = false },
                    label    = { Text(s, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Indigo600,
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // MCQ / Written toggle (only for Quiz/QBank)
        if (sheet != "Study") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("প্রশ্নের ধরন:", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked         = isMcq,
                    onCheckedChange = { isMcq = it },
                    thumbContent    = {
                        Text(if (isMcq) "MCQ" else "Written",
                            fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            fontFamily = NotoSansBengali)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isMcq) "MCQ" else "Written",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                    color = Indigo600, fontSize = 13.sp)
            }
        }

        HorizontalDivider()

        // Basic fields
        AddField("বিষয় (Subject) *", subject, { subject = it })
        AddField("অধ্যায় (SubTopic)", subTopic, { subTopic = it })
        AddField("প্রশ্ন (Question) *", question, { question = it }, minLines = 3)

        // MCQ options
        if (isMcq) {
            Text("📝 Options", fontFamily = NotoSansBengali,
                fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Indigo600)
            AddField("Option A *", optA, { optA = it })
            AddField("Option B *", optB, { optB = it })
            AddField("Option C", optC, { optC = it })
            AddField("Option D", optD, { optD = it })
            AddField("✅ সঠিক উত্তর (Option A/B/C/D এর exact text) *", answer, { answer = it })
        } else {
            AddField("✅ উত্তর *", answer, { answer = it }, minLines = 2)
        }

        AddField("💡 ব্যাখ্যা (Explanation)", explanation, { explanation = it }, minLines = 2)
        AddField("🧠 টেকনিক (Technique)", technique, { technique = it }, minLines = 2)

        // Audience tag dropdown
        Text("🎯 Audience Tag", fontFamily = NotoSansBengali,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SlateText)
        ExposedDropdownMenuBox(
            expanded         = audExpanded,
            onExpandedChange = { audExpanded = it }
        ) {
            OutlinedTextField(
                value         = AUDIENCE_OPTIONS.find { it.first == audience }?.second ?: audience,
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Audience Tag", fontFamily = NotoSansBengali) },
                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(audExpanded) },
                modifier      = Modifier.menuAnchor().fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp)
            )
            ExposedDropdownMenu(expanded = audExpanded, onDismissRequest = { audExpanded = false }) {
                AUDIENCE_OPTIONS.forEach { (tag, label) ->
                    DropdownMenuItem(
                        text    = { Text(label, fontFamily = NotoSansBengali) },
                        onClick = { audience = tag; audExpanded = false }
                    )
                }
            }
        }

        // Status message
        msg?.let {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (isOk) GreenOk else RedWrong)
            }
        }

        // Save button
        val isValid = subject.isNotBlank() && question.isNotBlank() && answer.isNotBlank() &&
                (!isMcq || (optA.isNotBlank() && optB.isNotBlank()))

        Button(
            onClick = {
                val fields = mutableMapOf(
                    "subject"      to subject,
                    "sub_topic"    to subTopic,
                    "question"     to question,
                    "correct"      to answer,
                    "explanation"  to explanation,
                    "technique"    to technique,
                    "AudienceTags" to audience,
                    "type"         to if (isMcq) "mcq" else "written"
                )
                if (isMcq) {
                    if (optA.isNotBlank()) fields["option1"] = optA
                    if (optB.isNotBlank()) fields["option2"] = optB
                    if (optC.isNotBlank()) fields["option3"] = optC
                    if (optD.isNotBlank()) fields["option4"] = optD
                }
                vm.adminAddQuestion(sheet, fields)
            },
            enabled  = isValid && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                    color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Firebase এ যোগ হচ্ছে...", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
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

@Composable
private fun AddField(
    label    : String,
    value    : String,
    onChange : (String) -> Unit,
    minLines : Int = 1
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp) },
        modifier      = Modifier.fillMaxWidth(),
        minLines      = minLines,
        shape         = RoundedCornerShape(10.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Indigo600,
            unfocusedBorderColor = Color(0xFFE2E8F0)
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = NotoSansBengali, fontSize = 13.sp
        )
    )
}

// ═══════════════════════════════════════════════════════════════
//  FEATURE 3 ─ Bulk Audience Tag Update Tab
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkAudienceTab(state: MenuUiState, vm: MenuViewModel) {
    var sheet       by remember { mutableStateOf("Quiz") }
    var subject     by remember { mutableStateOf("") }
    var subTopic    by remember { mutableStateOf("") }
    var newTag      by remember { mutableStateOf("") }
    var tagExpanded by remember { mutableStateOf(false) }
    var confirmed   by remember { mutableStateOf(false) }

    val msg      = state.bulkUpdateMsg
    val updating = state.isBulkUpdating
    val isOk     = msg?.startsWith("✅") == true

    LaunchedEffect(isOk) {
        if (isOk) {
            kotlinx.coroutines.delay(3000)
            vm.clearBulkMsg()
            subject  = ""
            subTopic = ""
            confirmed = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Warning header
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFBEB),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("সাবধান! Bulk Operation", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF92400E))
                    Text("এই অপারেশন একসাথে অনেক প্রশ্নের AudienceTag পরিবর্তন করবে। " +
                            "ভুল করলে পূর্বাবস্থায় ফেরা কঠিন।",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF92400E))
                }
            }
        }

        // Sheet selector
        Text("📂 Sheet", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, color = SlateText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHEETS.forEach { s ->
                FilterChip(
                    selected = sheet == s,
                    onClick  = { sheet = s },
                    label    = { Text(s, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Indigo600,
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        AddField("বিষয় (Subject) — যে subject এর প্রশ্ন বদলাবেন *", subject, { subject = it })
        AddField("অধ্যায় (SubTopic) — ফাঁকা রাখলে সব অধ্যায়", subTopic, { subTopic = it })

        // New tag dropdown
        Text("🎯 নতুন Audience Tag", fontFamily = NotoSansBengali,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SlateText)
        ExposedDropdownMenuBox(
            expanded         = tagExpanded,
            onExpandedChange = { tagExpanded = it }
        ) {
            OutlinedTextField(
                value         = AUDIENCE_OPTIONS.find { it.first == newTag }?.second ?: "বেছে নিন",
                onValueChange = {},
                readOnly      = true,
                label         = { Text("নতুন Tag *", fontFamily = NotoSansBengali) },
                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(tagExpanded) },
                modifier      = Modifier.menuAnchor().fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600)
            )
            ExposedDropdownMenu(expanded = tagExpanded, onDismissRequest = { tagExpanded = false }) {
                AUDIENCE_OPTIONS.forEach { (tag, label) ->
                    DropdownMenuItem(
                        text    = { Text(label, fontFamily = NotoSansBengali) },
                        onClick = { newTag = tag; tagExpanded = false }
                    )
                }
            }
        }

        // Preview box
        if (subject.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEEF2FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Indigo600.copy(0.3f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📋 অপারেশন summary:", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, color = Indigo600)
                    Text("Sheet: $sheet", fontFamily = NotoSansBengali, fontSize = 12.sp, color = SlateText)
                    Text("Subject: $subject", fontFamily = NotoSansBengali, fontSize = 12.sp, color = SlateText)
                    Text("SubTopic: ${subTopic.ifBlank { "সব অধ্যায়" }}", fontFamily = NotoSansBengali,
                        fontSize = 12.sp, color = SlateText)
                    Text("নতুন Tag: \"${newTag.ifBlank { "Job Seeker (খালি)" }}\"",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Indigo600)
                }
            }
        }

        // Confirmation checkbox
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF1F2), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked         = confirmed,
                onCheckedChange = { confirmed = it },
                colors          = CheckboxDefaults.colors(checkedColor = RedWrong)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "আমি নিশ্চিত যে এই Bulk আপডেট করতে চাই এবং পরিণতি সম্পর্কে সচেতন।",
                fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF9F1239),
                fontWeight = FontWeight.Bold
            )
        }

        // Status
        msg?.let {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isOk) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (isOk) GreenOk else RedWrong)
            }
        }

        // Execute button
        val isReady = subject.isNotBlank() && newTag.isNotEmpty() && confirmed

        Button(
            onClick = {
                vm.adminBulkAudienceUpdate(sheet, subject, subTopic, newTag)
            },
            enabled  = isReady && !updating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isReady) RedWrong else Color(0xFFCBD5E1)
            )
        ) {
            if (updating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                    color = Color.White, strokeWidth = 2.dp)
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
