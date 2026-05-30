package com.hanif.smartstudy.ui.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel

// ─────────────────────────────────────────────────────────────
//  BookmarksMenuScreen, LeaderboardScreen, AdminScreen
// ─────────────────────────────────────────────────────────────

// ── Bookmarks screen (placeholder — real data from QuizViewModel) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksMenuScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⭐ সংরক্ষিত প্রশ্ন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⭐", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Quiz/QBank/Study ট্যাব থেকে\nপ্রশ্নে ⭐ দিয়ে সংরক্ষণ করুন",
                    fontFamily = NotoSansBengali, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Leaderboard ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    state  : MenuUiState,
    onBack : () -> Unit
) {
    // Mock leaderboard data — real data would come from Firebase RTDB
    val mockData = remember {
        listOf(
            Triple("🥇", "রাফি হোসেন", 4820),
            Triple("🥈", "তানিয়া আক্তার", 3950),
            Triple("🥉", "সিফাত মাহমুদ", 3200),
            Triple("4️⃣", "নাফিস আহমেদ", 2870),
            Triple("5️⃣", "মিম রহমান", 2550),
            Triple("6️⃣", "রিয়া চৌধুরী", 2340),
            Triple("7️⃣", "সাকিব হাসান", 2100),
            Triple("8️⃣", "প্রিয়া দাস", 1900),
            Triple("9️⃣", "রাহেলা বেগম", 1750),
            Triple("🔟", "আলিম হোসেন", 1600)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏆 লিডারবোর্ড", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // Current user rank
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👤", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.user?.displayName() ?: "আপনি",
                                fontFamily = NotoSansBengali, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                "আপনার XP: ${state.user?.xp ?: 0}",
                                fontFamily = NotoSansBengali, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(0.75f)
                            )
                        }
                        Text(
                            "#--",
                            fontFamily = NotoSansBengali, fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            itemsIndexed(mockData) { i, (medal, name, xp) ->
                LeaderboardRow(rank = i + 1, medal = medal, name = name, xp = xp)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, medal: String, name: String, xp: Int) {
    val bgColor = when (rank) {
        1 -> Color(0xFFFEF9C3)  // gold tint
        2 -> Color(0xFFF1F5F9)  // silver
        3 -> Color(0xFFFFF7ED)  // bronze
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(medal, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                fontFamily = NotoSansBengali, color = Slate800,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Indigo600.copy(0.12f)
            ) {
                Text(
                    "$xp XP",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Indigo600,
                    fontFamily = NotoSansBengali,
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ── Admin Screen ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    state  : MenuUiState,
    vm     : MenuViewModel,
    onBack : () -> Unit
) {
    var notifTitle by remember { mutableStateOf("") }
    var notifBody  by remember { mutableStateOf("") }
    var showNotifDialog by remember { mutableStateOf(false) }
    var showUserSearch  by remember { mutableStateOf(false) }
    var searchPhone     by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadAllUsers() }

    if (showNotifDialog) {
        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            title = { Text("📢 নোটিফিকেশন পাঠান", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value         = notifTitle,
                        onValueChange = { notifTitle = it },
                        label         = { Text("শিরোনাম", fontFamily = NotoSansBengali) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value         = notifBody,
                        onValueChange = { notifBody = it },
                        label         = { Text("বার্তা", fontFamily = NotoSansBengali) },
                        minLines      = 2,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick  = { vm.adminSendNotification(notifTitle, notifBody, null); showNotifDialog = false },
                    enabled  = notifTitle.isNotBlank() && notifBody.isNotBlank()
                ) {
                    Text("পাঠান", fontFamily = NotoSansBengali)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifDialog = false }) { Text("বাতিল", fontFamily = NotoSansBengali) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔑 Admin Panel", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── FCM Token ──
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("📱 আমার FCM Token", fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            state.fcmToken.ifEmpty { "লোড হচ্ছে..." },
                            fontFamily = NotoSansBengali, fontSize = 10.sp,
                            modifier   = Modifier.padding(10.dp),
                            color      = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                }
            }

            // ── Send notification ──
            AdminActionCard(
                emoji   = "📢",
                title   = "সকলকে নোটিফিকেশন পাঠান",
                subtitle= "Firebase Broadcast নোড এ সংরক্ষণ হয়",
                onClick = { showNotifDialog = true }
            )

            // ── User list ──
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("👥 সব ইউজার (${state.allUsers.size})", fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { vm.loadAllUsers() }) {
                            Text("রিফ্রেশ", fontFamily = NotoSansBengali, fontSize = 12.sp)
                        }
                    }

                    // Search
                    OutlinedTextField(
                        value         = searchPhone,
                        onValueChange = { searchPhone = it },
                        placeholder   = { Text("ফোন নম্বর খুঁজুন...", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    val filtered = state.allUsers.filter {
                        searchPhone.isEmpty() || (it["Phone"] ?: "").contains(searchPhone) || (it["Name"] ?: "").contains(searchPhone)
                    }.take(30)

                    filtered.forEach { user ->
                        AdminUserRow(user = user, onViewAs = { vm.adminViewAs(it) })
                    }
                }
            }

            // ── ViewAs banner ──
            if (state.viewingAsUser != null) {
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Amber500.copy(0.15f)),
                    border = BorderStroke(1.dp, Amber500.copy(0.5f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👁 দেখছেন: ${state.viewingAsUser.name} (${state.viewingAsUser.phone})", fontFamily = NotoSansBengali, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.adminExitViewAs() }) {
                            Text("বন্ধ", fontFamily = NotoSansBengali, color = Red500)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AdminActionCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape   = RoundedCornerShape(14.dp),
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    }
}

@Composable
private fun AdminUserRow(user: Map<String, String>, onViewAs: (String) -> Unit) {
    val phone  = user["Phone"] ?: user["phone"] ?: "-"
    val name   = user["Name"]  ?: user["name"]  ?: "-"
    val xp     = user["XP"]    ?: user["xp"]    ?: "0"
    val active = user["lastActive"]?.toLongOrNull()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onViewAs(phone) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(Indigo100, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text((name.firstOrNull()?.toString() ?: "?").uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Indigo600)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name,  fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(phone, fontFamily = NotoSansBengali, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$xp XP", fontFamily = NotoSansBengali, fontSize = 11.sp, color = Indigo600, fontWeight = FontWeight.Bold)
            if (active != null) {
                val diff = (System.currentTimeMillis() - active) / 60000
                val label = when {
                    diff < 5    -> "🟢 এখন"
                    diff < 60   -> "🕐 ${diff}m"
                    diff < 1440 -> "🕐 ${diff/60}h"
                    else        -> "⬜ ${diff/1440}d"
                }
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontFamily = NotoSansBengali)
            }
        }
    }
    HorizontalDivider(thickness = 0.4.dp, color = MaterialTheme.colorScheme.outline.copy(0.1f))
}
