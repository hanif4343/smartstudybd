package com.hanif.smartstudy.ui.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.BuddyRequest
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.BuddyViewModel

// ─────────────────────────────────────────────────────────────
//  StudyBuddyScreen — পড়াশোনার সঙ্গী (Study Buddy)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyBuddyScreen(
    onBack : () -> Unit,
    vm     : BuddyViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val phoneInput by vm.phoneInput.collectAsStateWithLifecycle()

    // Toast
    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.clearToast()
        }
    }

    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Study Buddy সরাবেন?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
            text  = { Text("এই সম্পর্ক ভাঙলে দুজনের কমন প্রোগ্রেস বার মুছে যাবে।", fontFamily = NotoSansBengali, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.removeBuddy(); showRemoveDialog = false }) {
                    Text("হ্যাঁ, সরাও", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤝 Study Buddy", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── পেন্ডিং রিকোয়েস্ট থাকলে দেখাও ──
            state.incomingRequest?.let { req ->
                IncomingRequestCard(
                    request  = req,
                    onAccept = { vm.acceptRequest(req) },
                    onDecline = { vm.declineRequest(req) }
                )
            }

            if (state.hasBuddy && state.buddy != null) {
                BuddyDashboard(
                    buddyName     = state.buddy!!.buddyName,
                    myProgress    = state.myProgress,
                    buddyProgress = state.buddyProgress,
                    onNudge       = { vm.sendNudge() },
                    onRemove      = { showRemoveDialog = true }
                )
            } else {
                NoBuddyCard(
                    phoneInput   = phoneInput,
                    onPhoneChange = vm::onPhoneInputChange,
                    isLoading    = state.isLoading,
                    error        = state.error,
                    onSend       = { vm.sendRequest(phoneInput) }
                )
            }

            // ── ফিচার ব্যাখ্যা ──
            InfoCard()

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── পেন্ডিং রিকোয়েস্ট কার্ড ──────────────────────────────

@Composable
private fun IncomingRequestCard(
    request   : BuddyRequest,
    onAccept  : () -> Unit,
    onDecline : () -> Unit
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🤝 নতুন Study Buddy রিকোয়েস্ট!", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "${request.fromName} (${request.fromPhone}) আপনাকে Study Buddy হওয়ার আমন্ত্রণ জানিয়েছে।",
                fontFamily = NotoSansBengali, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
                Button(onClick = onAccept, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("Accept", fontFamily = NotoSansBengali)
                }
            }
        }
    }
}

// ── বন্ধু নেই — খুঁজে নিয়ে রিকোয়েস্ট পাঠানোর কার্ড ──────────

@Composable
private fun NoBuddyCard(
    phoneInput    : String,
    onPhoneChange : (String) -> Unit,
    isLoading     : Boolean,
    error         : String?,
    onSend        : () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🧑‍🤝‍🧑", fontSize = 40.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                "এখনো কোনো Study Buddy নেই",
                fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Text(
                "একজন বন্ধুর ফোন নম্বর দিয়ে রিকোয়েস্ট পাঠাও, দুজনে একসাথে পড়াশোনার অগ্রগতি দেখতে পারবে এবং একে অপরকে তাগাদা দিতে পারবে। এটি সম্পূর্ণ ঐচ্ছিক — Study Buddy ছাড়াও তুমি স্বাভাবিকভাবে একা পড়তে পারবে, কোনো ফিচার লক হবে না।",
                fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value         = phoneInput,
                onValueChange = onPhoneChange,
                placeholder   = { Text("বন্ধুর ফোন নম্বর", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                leadingIcon   = { Icon(Icons.Default.Phone, null) }
            )

            if (error != null) {
                Text(error, fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick  = onSend,
                enabled  = !isLoading && phoneInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("রিকোয়েস্ট পাঠাও", fontFamily = NotoSansBengali)
                }
            }

            Text(
                "চাইলে এই ফিচারটি বাদ দিয়ে এখনই পড়াশোনা শুরু করতে পারো — Study Buddy পরেও যুক্ত করা যাবে।",
                fontFamily = NotoSansBengali, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
}

// ── বন্ধু আছে — ড্যাশবোর্ড ─────────────────────────────────

@Composable
private fun BuddyDashboard(
    buddyName     : String,
    myProgress    : com.hanif.smartstudy.data.model.BuddyProgress,
    buddyProgress : com.hanif.smartstudy.data.model.BuddyProgress,
    onNudge       : () -> Unit,
    onRemove      : () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤝", fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("তোমার Study Buddy", fontFamily = NotoSansBengali, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(0.7f))
                    Text(buddyName, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── Common progress bars ──
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("📊 আজকের প্রোগ্রেস", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            BuddyProgressRow(label = "তুমি", pct = myProgress.progressPct, color = MaterialTheme.colorScheme.primary)
            BuddyProgressRow(label = buddyName, pct = buddyProgress.progressPct, color = Color(0xFFFF9800))

            if (buddyProgress.progressPct >= 100 && myProgress.progressPct < 100) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF3E0)
                ) {
                    Text(
                        "👀 $buddyName আজকের পড়া শেষ করে ফেলেছে! তুমি কি পিছিয়ে থাকবে?",
                        fontFamily = NotoSansBengali, fontSize = 12.sp,
                        color = Color(0xFFE65100),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── Nudge / Remove buttons ──
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick  = onNudge,
            enabled  = myProgress.progressPct >= 100 && buddyProgress.progressPct < 100,
            modifier = Modifier.weight(1f),
            shape    = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("তাগাদা পাঠাও", fontFamily = NotoSansBengali, fontSize = 13.sp)
        }
        OutlinedButton(
            onClick  = onRemove,
            modifier = Modifier.weight(1f),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.PersonRemove, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("সরাও", fontFamily = NotoSansBengali, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BuddyProgressRow(label: String, pct: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("$pct%", fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color    = color,
            trackColor = color.copy(0.15f)
        )
    }
}

// ── তথ্য কার্ড ───────────────────────────────────────────

@Composable
private fun InfoCard() {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ℹ️ Study Buddy কীভাবে কাজ করে?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "• দুজনের আজকের পড়ার লক্ষ্যের প্রোগ্রেস বার একসাথে দেখা যাবে\n" +
                "• তোমার পড়া শেষ হলে কিন্তু বন্ধুর শেষ না হলে, তাকে এক ক্লিকে 'তাগাদা' পাঠাতে পারবে\n" +
                "• প্রতিদিন রাত ১২টায় প্রোগ্রেস রিসেট হয়ে যাবে",
                fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.65f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  StudyBuddyQuickButton — হোম স্ক্রিনের কোণায় ছোট "+ 👤" বাটন
//  ট্যাপ করলে bottom sheet এ Study Buddy স্ট্যাটাস/রিকোয়েস্ট দেখা যাবে
//  (সম্পূর্ণ ঐচ্ছিক — না চাইলে এড়িয়ে যাওয়া যায়)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyBuddyQuickButton(
    vm: BuddyViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val phoneInput by vm.phoneInput.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.clearToast()
        }
    }

    Box {
        IconButton(onClick = { showSheet = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                if (state.hasBuddy) Icons.Default.Diversity1 else Icons.Default.PersonAdd,
                contentDescription = "Study Buddy",
                tint = if (state.hasBuddy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        // বাডি আছে এবং তার progress 100% — ছোট badge
        if (state.hasBuddy && state.buddyProgress.progressPct >= 100) {
            Box(
                Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF22C55E))
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState       = sheetState,
            containerColor   = MaterialTheme.colorScheme.surface,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("🤝 Study Buddy", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                state.incomingRequest?.let { req ->
                    IncomingRequestCard(
                        request   = req,
                        onAccept  = { vm.acceptRequest(req) },
                        onDecline = { vm.declineRequest(req) }
                    )
                }

                if (state.hasBuddy && state.buddy != null) {
                    BuddyDashboard(
                        buddyName     = state.buddy!!.buddyName,
                        myProgress    = state.myProgress,
                        buddyProgress = state.buddyProgress,
                        onNudge       = { vm.sendNudge() },
                        onRemove      = { vm.removeBuddy() }
                    )
                } else {
                    NoBuddyCard(
                        phoneInput    = phoneInput,
                        onPhoneChange = vm::onPhoneInputChange,
                        isLoading     = state.isLoading,
                        error         = state.error,
                        onSend        = { vm.sendRequest(phoneInput) }
                    )
                }
            }
        }
    }
}

