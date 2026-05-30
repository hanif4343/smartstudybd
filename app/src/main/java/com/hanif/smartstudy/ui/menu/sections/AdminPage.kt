package com.hanif.smartstudy.ui.menu.sections

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
            TabRow(
                selectedTabIndex = tab,
                containerColor   = DeepIndigo,
                contentColor     = Color.White
            ) {
                listOf("👥 ইউজার", "📣 Notify", "🔑 FCM").forEachIndexed { i, label ->
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
