package com.hanif.smartstudy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.model.RecentActivityItem
import com.hanif.smartstudy.ui.theme.LocalDarkMode
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.BuddyViewModel
import com.hanif.smartstudy.viewmodel.HomeUiState

// ═══════════════════════════════════════════════════════════
// নতুন Home ড্যাশবোর্ড (রেফারেন্স ডিজাইন অনুযায়ী) — সব ডেটা আসল:
//  • মোট অগ্রগতি  = Quiz মোডের সব উত্তরের গড় সঠিক % (HomeOverview.quizAccuracyPct)
//  • পড়াশোনা     = আজকের পড়ার সময় / দৈনিক লক্ষ্য (GoalProgress)
//  • কুইজ         = আজ উত্তর দেওয়া Quiz প্রশ্ন / আজকের লক্ষ্য
//  • মডেল টেস্ট   = আজ দেওয়া Model Test / লক্ষ্য
//  • রুটিন/লক্ষ্য = RoutineViewModel (আজকের রুটিন আইটেম)
//  • সাম্প্রতিক   = TestHistory-র সর্বশেষ কার্যক্রম
// যে ফিচারের ডেটা এখনো নেই, সেখানে ডিজাইন ঠিক রেখে "খালি অবস্থা" (empty state) দেখায়।
// ═══════════════════════════════════════════════════════════

private val HdIndigo = Color(0xFF4F46E5)
private val HdAmber  = Color(0xFFF59E0B)

// ── ছোট সহায়ক ফাংশন ──
internal fun hdFmtMin(m: Int): String {
    val h = m / 60; val mm = m % 60
    return when {
        h > 0 && mm > 0 -> "${h}h ${mm}m"
        h > 0           -> "${h}h"
        else            -> "${mm}m"
    }
}

private fun hdFmtHm(m: Int): String = "${m / 60}h ${m % 60}m"

private fun hdTimeAgo(ts: Long): String {
    if (ts <= 0L) return ""
    val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
    val min = diff / 60_000L
    return when {
        min < 1     -> "এইমাত্র"
        min < 60    -> "${min}m আগে"
        min < 1440  -> "${min / 60}h আগে"
        else        -> "${min / 1440}d আগে"
    }
}

private fun hdGreeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11  -> "শুভ সকাল,"
        in 12..15 -> "শুভ দুপুর,"
        in 16..17 -> "শুভ বিকাল,"
        in 18..19 -> "শুভ সন্ধ্যা,"
        else      -> "শুভ রাত্রি,"
    }
}

/** হেডারের নিচের লাইন — শুধু স্ট্রিক (আজকের লক্ষ্য/অগ্রগতি এখানে নয়) */
private fun hdStatusLine(state: HomeUiState): String {
    val streak = state.streakInfo.streakDays
    return when {
        streak >= 1 -> "🔥 $streak দিনের স্ট্রিক"
        else -> com.hanif.smartstudy.util.AudienceProfiles.current.welcome
    }
}

private val HdMottos = listOf(
    "লক্ষ্য বড় রাখি, চেষ্টা আরো বড়",
    "আজকের পরিশ্রম, কালকের সাফল্য",
    "ছোট ছোট ধাপেই বড় জয়",
    "থেমো না, এগিয়ে চলো",
    "প্রতিদিন একটু একটু করে"
)

@Composable
private fun HdCard(
    modifier: Modifier = Modifier,
    bg: Color = MaterialTheme.colorScheme.surface,
    radius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) { content() }
}

@Composable
private fun HdChevron(tint: Color = HdIndigo, size: Dp = 18.dp) {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun HdGradientIcon(icon: ImageVector, c1: Color, c2: Color, box: Dp = 40.dp, iconSize: Dp = 22.dp) {
    Box(
        Modifier.size(box).clip(RoundedCornerShape(box * 0.32f))
            .background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
private fun HdAvatar(picture: String?, initial: String, size: Dp, ring: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(ring.copy(alpha = 0.18f))
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!picture.isNullOrEmpty()) {
            AsyncImage(model = picture, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initial, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold,
                color = ring, fontFamily = NotoSansBengali)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ১) আকাশ-পাহাড়ের হেডার — অভিবাদন, নাম, ঘণ্টা/সার্চ/⋮ মেনু
// ═══════════════════════════════════════════════════════════
@Composable
internal fun HomeSkyHeader(
    state: HomeUiState,
    isAdmin: Boolean,
    onBellClick: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenBuddy: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDark: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onForceResync: () -> Unit
) {
    val dark = LocalDarkMode.current.value
    val textMain = if (dark) Color.White else Color(0xFF1E293B)
    val textSub  = if (dark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val skyTop   = if (dark) Color(0xFF0F172A) else Color(0xFF9CCBF0)
    val skyMid   = if (dark) Color(0xFF1E293B) else Color(0xFFCDE6F7)
    val skyBot   = if (dark) Color(0xFF312E81) else Color(0xFFFBE3C5)
    val hillFar  = if (dark) Color(0xFF334155) else Color(0xFF9DB9D8)
    val hillMid  = if (dark) Color(0xFF1F4D47) else Color(0xFF6FA5A0)
    val hillNear = if (dark) Color(0xFF14532D) else Color(0xFF3F8F6B)
    val sun      = if (dark) Color(0xFFFDE68A) else Color(0xFFFFE9A8)

    var menuOpen by remember { mutableStateOf(false) }
    // ── স্ট্রিক লাইন (শুধু স্ট্রিক; আজকের লক্ষ্য/অগ্রগতি নয়) ──
    val motto = hdStatusLine(state)
    val name = state.user?.displayName() ?: "বন্ধু"

    Box(Modifier.fillMaxWidth().height(128.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(listOf(skyTop, skyMid, skyBot)))
            drawCircle(sun.copy(alpha = 0.95f), radius = h * 0.20f, center = Offset(w * 0.74f, h * 0.74f))
            val far = Path().apply {
                moveTo(0f, h * 0.80f)
                quadraticBezierTo(w * 0.18f, h * 0.50f, w * 0.38f, h * 0.76f)
                quadraticBezierTo(w * 0.55f, h * 0.92f, w * 0.70f, h * 0.66f)
                quadraticBezierTo(w * 0.86f, h * 0.48f, w, h * 0.74f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(far, hillFar.copy(alpha = 0.85f))
            val mid = Path().apply {
                moveTo(0f, h * 0.90f)
                quadraticBezierTo(w * 0.22f, h * 0.70f, w * 0.45f, h * 0.90f)
                quadraticBezierTo(w * 0.70f, h * 1.05f, w, h * 0.82f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(mid, hillMid.copy(alpha = 0.9f))
            val near = Path().apply {
                moveTo(0f, h * 0.97f)
                quadraticBezierTo(w * 0.30f, h * 0.86f, w * 0.60f, h * 0.99f)
                lineTo(w * 0.60f, h); lineTo(0f, h); close()
            }
            drawPath(near, hillNear)
        }

        Row(
            Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // avatar + অনলাইন ডট
            Box(Modifier.size(68.dp)) {
                Box(
                    Modifier.size(66.dp).clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                ) {
                    HdAvatar(state.user?.picture, name.take(1), 60.dp, HdIndigo)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape)
                        .background(Color.White).padding(2.dp).clip(CircleShape)
                        .background(if (state.isOffline) Color(0xFF94A3B8) else Color(0xFF22C55E))   // আসল নেট-স্ট্যাটাস
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(hdGreeting(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = textSub, fontFamily = NotoSansBengali)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textMain,
                        fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(4.dp))
                    Text(state.xpInfo.currentLevel.emoji, fontSize = 18.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(motto, fontSize = 12.sp, color = textSub, fontFamily = NotoSansBengali,
                    maxLines = 2, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                // 🔔
                BadgedBox(badge = {
                    if (state.unreadNotifCount > 0) {
                        Badge(containerColor = Color(0xFFEF4444)) {
                            Text(if (state.unreadNotifCount > 9) "9+" else "${state.unreadNotifCount}",
                                fontSize = 9.sp, color = Color.White)
                        }
                    }
                }) {
                    HdRoundBtn(
                        if (state.unreadNotifCount > 0) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                        "নোটিফিকেশন", onBellClick
                    )
                }
                HdRoundBtn(Icons.Default.Search, "সার্চ", onSearchClick)
                // ⋮
                Box {
                    HdRoundBtn(Icons.Default.MoreVert, "মেনু") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        HdMenuItem(Icons.Default.Groups, "স্টাডি বাডি") { menuOpen = false; onOpenBuddy() }
                        HdMenuItem(Icons.Default.Settings, "Settings") { menuOpen = false; onOpenSettings() }
                        HdMenuItem(
                            if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            if (dark) "Light Mode" else "Dark Mode"
                        ) { menuOpen = false; onToggleDark() }
                        HdMenuItem(Icons.Default.Info, "About") { menuOpen = false; onAbout() }
                        HdMenuItem(Icons.Default.Help, "Help") { menuOpen = false; onHelp() }
                        if (isAdmin) {
                            HdMenuItem(Icons.Default.Refresh, "Force Full Resync") { menuOpen = false; onForceResync() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HdRoundBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).shadow(2.dp, CircleShape).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp)) }
}

@Composable
private fun HdMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp, fontFamily = NotoSansBengali) },
        leadingIcon = { Icon(icon, null, tint = Color(0xFF1E1B4B), modifier = Modifier.size(20.dp)) },
        onClick = onClick
    )
}

// ═══════════════════════════════════════════════════════════
// ২) Buddy কার্ড + Streak কার্ড
// ═══════════════════════════════════════════════════════════
@Composable
internal fun HomeBuddyStreakRow(
    state: HomeUiState,
    buddyEnabled: Boolean,
    onOpenBuddy: () -> Unit,
    onOpenStreak: () -> Unit
) {
    // কিছু দেখানোর না থাকলে (স্ট্রিক ০ ও বাডি বন্ধ) পুরো সারিই অদৃশ্য — খালি কার্ড জায়গা খায় না
    val streak = state.streakInfo.streakDays
    if (streak <= 0 && !buddyEnabled) return
    Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (buddyEnabled) {
            val buddyBg = Brush.horizontalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFF3E8FF)))
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .shadow(2.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .background(buddyBg)
                    .clickable(onClick = onOpenBuddy)
            ) { HdBuddyInner(state, onOpenBuddy) }
        }
        if (streak > 0) {
            HdCard(
                Modifier.weight(1f).fillMaxHeight(),
                bg = if (LocalDarkMode.current.value) Color(0xFF3B2A16) else Color(0xFFFFF1E3),
                onClick = onOpenStreak
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥", fontSize = 28.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$streak", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, lineHeight = 22.sp)
                        Text("দিনের স্ট্রিক", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali, maxLines = 1)
                    }
                    HdChevron(HdAmber)
                }
            }
        }
    }
}

@Composable
private fun HdBuddyEmpty(hint: String) {
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PersonAdd, null, tint = HdIndigo, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("স্টাডি বাডি", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF312E81), fontFamily = NotoSansBengali, maxLines = 1)
            Text(hint, fontSize = 11.sp, color = Color(0xFF4F46E5), fontFamily = NotoSansBengali, maxLines = 1)
        }
        HdChevron()
    }
}

@Composable
private fun HdBuddyInner(state: HomeUiState, onOpen: () -> Unit) {
    val vm: BuddyViewModel = viewModel()
    val b by vm.state.collectAsState()
    if (!b.hasBuddy) { HdBuddyEmpty("বন্ধু যোগ করো"); return }
    val buddyName = b.buddy?.buddyName ?: "বাডি"
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(78.dp).height(50.dp)) {
            HdAvatar(state.user?.picture, (state.user?.displayName() ?: "?").take(1), 46.dp, Color(0xFF10B981),
                Modifier.align(Alignment.CenterStart))
            HdAvatar(null, buddyName.take(1).uppercase(), 46.dp, Color(0xFF7C3AED),
                Modifier.align(Alignment.CenterEnd))
            if (b.knocks.isNotEmpty()) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) { Text("${b.knocks.size}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(buddyName, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF312E81),
                fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bd = b.buddyProgress
            Text(
                if (bd.doneMinutes >= 0) "আজ ${hdFmtMin(bd.doneMinutes)}" else "আজকের তথ্য নেই",
                fontSize = 11.sp, color = Color(0xFF4F46E5), fontFamily = NotoSansBengali, maxLines = 1
            )
        }
        HdChevron()
    }
}

@Composable
private fun HdSectionTitle(icon: ImageVector, title: String, tint: Color, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface,
            fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (action != null) {
            Row(Modifier.clickable(onClick = onAction), verticalAlignment = Alignment.CenterVertically) {
                Text(action, fontSize = 10.5.sp, color = HdIndigo, fontFamily = NotoSansBengali, maxLines = 1, softWrap = false)
                HdChevron(HdIndigo, 14.dp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ৪) সাম্প্রতিক কার্যক্রম (পুরো প্রস্থ, ২টা সারি)
// ═══════════════════════════════════════════════════════════
internal data class HdQuickEntry(val label: String, val icon: ImageVector, val c1: Color, val c2: Color, val onClick: () -> Unit)

@Composable
internal fun HomeRecentCard(recent: List<RecentActivityItem>, onOpenHistory: () -> Unit) {
    HdCard(Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HdSectionTitle(Icons.Default.Schedule, "সাম্প্রতিক কার্যক্রম", MaterialTheme.colorScheme.onSurface, "দেখুন সব", onOpenHistory)
            if (recent.isEmpty()) {
                Text("এখনো কোনো কার্যক্রম নেই — একটা কুইজ বা টেস্ট দিলেই এখানে দেখা যাবে",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, modifier = Modifier.clickable(onClick = onOpenHistory).padding(vertical = 6.dp))
            } else {
                recent.take(2).forEach { r ->
                    val (icon, c1, c2) = hdActivityStyle(r)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenHistory),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HdGradientIcon(icon, c1, c2, 36.dp, 19.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(hdTimeAgo(r.timestamp), fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
                            maxLines = 1, softWrap = false)
                        HdChevron(MaterialTheme.colorScheme.onSurfaceVariant, 16.dp)
                    }
                }
            }
        }
    }
}

private fun hdActivityStyle(r: RecentActivityItem): Triple<ImageVector, Color, Color> = when {
    r.isModelTest        -> Triple(Icons.Default.Description, Color(0xFFFBBF24), Color(0xFFF59E0B))
    r.mode == "STUDY"    -> Triple(Icons.AutoMirrored.Filled.MenuBook, Color(0xFF34D399), Color(0xFF059669))
    r.mode == "QBANK"    -> Triple(Icons.Default.Layers, Color(0xFF60A5FA), Color(0xFF2563EB))
    r.mode == "VIVA"     -> Triple(Icons.Default.Mic, Color(0xFFA78BFA), Color(0xFF7C3AED))
    else                 -> Triple(Icons.Default.TrackChanges, Color(0xFFC084FC), Color(0xFF7C3AED))
}

// ═══════════════════════════════════════════════════════════
// ৫) লিডারবোর্ড · AI সহকারী · আরও — ছোট একটা সারি (আগে তিনটা বড় গ্রেডিয়েন্ট টাইল)
// ═══════════════════════════════════════════════════════════
@Composable
internal fun HomeBottomTilesRow(onLeaderboard: () -> Unit, onSupport: () -> Unit, onTools: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HdSlimTile("লিডারবোর্ড", Icons.Default.EmojiEvents, Color(0xFFF97316), onLeaderboard, Modifier.weight(1f))
        HdSlimTile("AI সহকারী", Icons.Default.SupportAgent, Color(0xFF4F46E5), onSupport, Modifier.weight(1f))
        HdSlimTile("আরও", Icons.Default.Apps, Color(0xFF0D9488), onTools, Modifier.weight(1f))
    }
}

@Composable
private fun HdSlimTile(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            fontFamily = NotoSansBengali, maxLines = 1, softWrap = false)
    }
}

// ═══════════════════════════════════════════════════════════
// ৬) Tools শীট — আগের Home-এর সব এন্ট্রি (Typing, Focus, Wrong Review, Routine…) এখানে
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeToolsSheet(entries: List<HdQuickEntry>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("⋯ আরও", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { e ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(e.c2.copy(alpha = 0.10f))
                                .border(1.dp, e.c2.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                                .clickable { onDismiss(); e.onClick() }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HdGradientIcon(e.icon, e.c1, e.c2, 42.dp, 23.dp)
                            Text(e.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali,
                                maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
