package com.hanif.smartstudy.ui.menu.sections

import android.content.Context
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.remote.LeaderboardEntry
import com.hanif.smartstudy.data.remote.UserSyncService
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.ReminderHelper
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel
import kotlinx.coroutines.launch

private val Indigo600 = Color(0xFF4F46E5)
private val AmberWarn = Color(0xFFF59E0B)
private val SlateText = Color(0xFF1E293B)
private val MutedText = Color(0xFF64748B)
private val CardBg    = Color(0xFFFFFFFF)
private val GreenOk   = Color(0xFF10B981)

// ═══════════════════════════════════════════════════════════
// Bookmarks Page
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksPage(state: MenuUiState, onBack: () -> Unit) {
    val prefs    = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE)
    val bookmarkIds = state.bookmarkedIds
    var filter   by remember { mutableStateOf("সব") }

    // Load bookmarked questions from cache (simplified — show IDs + subjects)
    val filterOptions = listOf("সব", "MCQ", "Written", "QBank")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সংরক্ষিত প্রশ্ন (${bookmarkIds.size})",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            LazyRow(
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { opt ->
                    FilterChip(
                        selected = filter == opt,
                        onClick  = { filter = opt },
                        label    = { Text(opt, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Indigo600,
                            selectedLabelColor     = Color.White
                        )
                    )
                }
            }

            if (bookmarkIds.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("কোনো সংরক্ষিত প্রশ্ন নেই", fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = MutedText,
                            fontFamily = NotoSansBengali)
                        Text("Quiz পড়তে গিয়ে ⭐ বাটনে ট্যাপ করো",
                            fontSize = 12.sp, color = Color(0xFFCBD5E1),
                            fontFamily = NotoSansBengali)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarkIds.toList()) { id ->
                        BookmarkCard(id = id)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(id: String) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("⭐", fontSize = 20.sp)
            Column(Modifier.weight(1f)) {
                Text("প্রশ্ন ID: $id", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = SlateText, fontFamily = NotoSansBengali)
                Text("Quiz/QBank থেকে সংরক্ষিত", fontSize = 10.sp, color = MutedText,
                    fontFamily = NotoSansBengali)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Leaderboard Page
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardPage(vm: MenuViewModel, onBack: () -> Unit) {
    var entries  by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }
    val scope    = rememberCoroutineScope()
    val myPhone  = vm.state.collectAsState().value.user?.phone ?: ""

    LaunchedEffect(Unit) {
        scope.launch {
            entries = UserSyncService.fetchLeaderboard()
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏆 লিডারবোর্ড", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Indigo600)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Top 3 podium
                item {
                    if (entries.size >= 3) Podium(entries.take(3))
                    Spacer(Modifier.height(8.dp))
                }
                // Rest
                itemsIndexed(entries.drop(3)) { idx, entry ->
                    LeaderboardRow(
                        rank    = idx + 4,
                        entry   = entry,
                        isMe    = entry.phone == myPhone
                    )
                }
            }
        }
    }
}

@Composable
private fun Podium(top3: List<LeaderboardEntry>) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF312E81), Color(0xFF4F46E5))))
            .padding(vertical = 20.dp, horizontal = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.Bottom
        ) {
            // 2nd
            PodiumEntry(top3.getOrNull(1), 2, 80.dp, Color(0xFF94A3B8))
            // 1st
            PodiumEntry(top3.getOrNull(0), 1, 100.dp, AmberWarn)
            // 3rd
            PodiumEntry(top3.getOrNull(2), 3, 65.dp, Color(0xFFCD7F32))
        }
    }
}

@Composable
private fun PodiumEntry(entry: LeaderboardEntry?, rank: Int, height: Dp, medalColor: Color) {
    entry ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(when (rank) { 1 -> "👑" 2 -> "🥈" else -> "🥉" }, fontSize = 20.sp)
        Box(
            Modifier.size(50.dp).clip(CircleShape)
                .background(Color.White.copy(0.15f))
                .border(2.dp, medalColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (entry.pic.isNotBlank()) {
                AsyncImage(model = entry.pic, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else Text("👤", fontSize = 22.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.name.take(10), fontSize = 10.sp, color = Color.White,
            fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
        Text("${entry.xp} XP", fontSize = 11.sp, color = AmberWarn,
            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
        // Podium block
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(60.dp).height(height)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(medalColor.copy(0.25f))
            .border(1.dp, medalColor.copy(0.5f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("#$rank", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                color = medalColor)
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isMe: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (isMe) Color(0xFFF5F3FF) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("#$rank", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = MutedText, modifier = Modifier.width(28.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFEEF2FF)),
            contentAlignment = Alignment.Center
        ) {
            if (entry.pic.isNotBlank()) {
                AsyncImage(model = entry.pic, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else Text("👤", fontSize = 16.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (isMe) Indigo600 else SlateText, fontFamily = NotoSansBengali)
            if (isMe) Text("(তুমি)", fontSize = 9.sp, color = Indigo600, fontFamily = NotoSansBengali)
        }
        Text("${entry.xp} XP", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = AmberWarn, fontFamily = NotoSansBengali)
    }
    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
}

// ═══════════════════════════════════════════════════════════
// Reminder Page — time picker + toggle
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPage(context: Context, onBack: () -> Unit) {
    val (enabled, savedH, savedM) = ReminderHelper.getReminderState(context)
    var isEnabled by remember { mutableStateOf(enabled) }
    var hour      by remember { mutableStateOf(savedH) }
    var minute    by remember { mutableStateOf(savedM) }
    var showPicker by remember { mutableStateOf(false) }
    var saved      by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("পড়ার রিমাইন্ডার", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Banner
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(Color(0xFF1E1B4B))
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("⏰", fontSize = 36.sp)
                    Column {
                        Text("প্রতিদিনের রিমাইন্ডার", fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color.White,
                            fontFamily = NotoSansBengali)
                        Text("নির্ধারিত সময়ে notification পাবে",
                            fontSize = 11.sp, color = Color.White.copy(0.6f),
                            fontFamily = NotoSansBengali)
                    }
                }
            }

            // Enable switch
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("রিমাইন্ডার চালু", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = SlateText, fontFamily = NotoSansBengali)
                    Switch(
                        checked         = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            if (!it) ReminderHelper.cancelReminder(context)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Indigo600.copy(0.3f),
                            checkedThumbColor = Indigo600)
                    )
                }
            }

            // Time display
            if (isEnabled) {
                Card(
                    Modifier.fillMaxWidth().clickable { showPicker = true },
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(Color(0xFFF5F3FF)),
                    border = BorderStroke(1.5.dp, Indigo600)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("নির্ধারিত সময়", fontSize = 11.sp, color = MutedText,
                                fontFamily = NotoSansBengali)
                            Text("%02d:%02d".format(hour, minute), fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold, color = Indigo600,
                                fontFamily = NotoSansBengali)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (hour < 12) "সকাল" else if (hour < 17) "বিকাল" else "রাত",
                                fontSize = 11.sp, color = MutedText, fontFamily = NotoSansBengali)
                            Icon(Icons.Default.Edit, null, tint = Indigo600, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Save button
                Button(
                    onClick = {
                        ReminderHelper.setDailyReminder(context, hour, minute)
                        saved = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) {
                    Text("✅ রিমাইন্ডার সেট করুন", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(vertical = 6.dp))
                }

                if (saved) {
                    Text("✅ রিমাইন্ডার সেট হয়েছে!", fontSize = 12.sp, color = GreenOk,
                        fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                }
            }
        }
    }

    // Time picker dialog
    if (showPicker) {
        val tpState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("সময় বেছে নিন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
            text  = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = tpState.hour; minute = tpState.minute; showPicker = false
                }) { Text("ঠিক আছে", fontFamily = NotoSansBengali, color = Indigo600, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("বাতিল", fontFamily = NotoSansBengali) }
            }
        )
    }
}
