package com.hanif.smartstudy.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.hanif.smartstudy.data.local.RoutineGoal
import com.hanif.smartstudy.data.model.DailyRoutine
import com.hanif.smartstudy.data.model.HomeOverview
import com.hanif.smartstudy.data.model.RecentActivityItem
import com.hanif.smartstudy.data.model.RoutineItem
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
private val HdGreen  = Color(0xFF10B981)
private val HdBlue   = Color(0xFF2563EB)
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

/** হেডারের নিচের লাইন — স্থির motto-র বদলে ইউজারের আসল অবস্থা (স্ট্রিক, আজকের পড়া/প্রশ্ন) */
private fun hdStatusLine(state: HomeUiState): String {
    val streak = state.streakInfo.streakDays
    val studyMin = state.goalProgress.doneMinutes
    val answered = state.overview.todayQuizAnswered
    return when {
        streak >= 2 && (studyMin > 0 || answered > 0) -> "🔥 $streak দিন টানা · আজ ${hdFmtMin(studyMin)} পড়া, $answered প্রশ্ন"
        studyMin > 0 || answered > 0 -> "আজ ${hdFmtMin(studyMin)} পড়েছো, $answered টি প্রশ্নের উত্তর দিয়েছো"
        streak >= 1 -> "🔥 $streak দিনের স্ট্রিক — আজ শুরু করলে বজায় থাকবে"
        else -> "আজ এখনো পড়া শুরু হয়নি — একটা কুইজ দিয়ে শুরু করো"
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
    // ── আসল তথ্য-ভিত্তিক লাইন (স্থির motto না): স্ট্রিক / আজকের পড়া / আজকের প্রশ্ন থেকে ──
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
                        HdMenuItem(Icons.Default.Groups, "Study Buddy") { menuOpen = false; onOpenBuddy() }
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
    Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val buddyBg = Brush.horizontalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFF3E8FF)))
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(22.dp), clip = false)
                .clip(RoundedCornerShape(22.dp))
                .background(buddyBg)
                .clickable(onClick = onOpenBuddy)
        ) {
            if (buddyEnabled) HdBuddyInner(state, onOpenBuddy) else HdBuddyEmpty("চালু করো")
        }
        HdCard(
            Modifier.weight(1f).fillMaxHeight(),
            bg = if (LocalDarkMode.current.value) Color(0xFF3B2A16) else Color(0xFFFFF1E3),
            onClick = onOpenStreak
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔥", fontSize = 34.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${state.streakInfo.streakDays}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, lineHeight = 26.sp)
                    Text("দিনের স্ট্রিক", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali, maxLines = 1)
                }
                HdChevron(HdAmber)
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
            Text("Study Buddy", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
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
    val buddyName = b.buddy?.buddyName ?: "Buddy"
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

// ═══════════════════════════════════════════════════════════
// ৩) মোট অগ্রগতি কার্ড — রিং + পড়াশোনা / কুইজ / মডেল টেস্ট
// ═══════════════════════════════════════════════════════════
@Composable
internal fun HomeProgressCard(
    overview: HomeOverview,
    studyDoneMin: Int,
    studyGoalMin: Int,
    onOpenStats: () -> Unit
) {
    HdCard(Modifier.fillMaxWidth(), radius = 24.dp, onClick = onOpenStats) {
      Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HdRing(
                fraction = overview.progressPct / 100f,
                size = 84.dp, stroke = 10.dp, color = HdGreen,
                track = HdGreen.copy(alpha = 0.16f)
            ) {
                Text("${overview.progressPct}%", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                Text("মোট অগ্রগতি", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HdMiniStat(
                    Icons.AutoMirrored.Filled.MenuBook, HdBlue, "পড়াশোনা",
                    "${hdFmtMin(studyDoneMin)} / ${hdFmtMin(studyGoalMin)}",
                    if (studyGoalMin > 0) studyDoneMin.toFloat() / studyGoalMin else 0f,
                    HdGreen, Modifier.weight(1f)
                )
                HdMiniStat(
                    Icons.Default.FactCheck, HdIndigo, "প্রশ্ন",
                    "${overview.todayQuizAnswered} / ${overview.quizDailyTarget}",
                    if (overview.quizDailyTarget > 0) overview.todayQuizAnswered.toFloat() / overview.quizDailyTarget else 0f,
                    HdBlue, Modifier.weight(1f)
                )
                HdMiniStat(
                    Icons.Default.Description, HdGreen, "মডেল টেস্ট",
                    "আজ ${overview.todayModelTests} · ৭ দিনে ${overview.weekModelTests}",
                    if (overview.modelTestTarget > 0) (overview.todayModelTests.toFloat() / overview.modelTestTarget) else 0f,
                    Color(0xFF94A3B8), Modifier.weight(1f)
                )
            }
            HdChevron(MaterialTheme.colorScheme.onSurface, 22.dp)
        }
        // ── আসল সংখ্যা: কতগুলো প্রশ্ন সমাধান, মোটের কতটা, আর সঠিক হার ──
        if (overview.totalQuestions > 0 || overview.quizAttempted > 0) {
            Text(
                "${overview.quizAttempted} / ${overview.totalQuestions} প্রশ্ন সমাধান · সঠিক হার ${overview.quizAccuracyPct}%",
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 12.dp, bottom = 8.dp)
            )
        }
      }
    }
}

@Composable
private fun HdRing(
    fraction: Float, size: Dp, stroke: Dp, color: Color, track: Color,
    center: @Composable ColumnScope.() -> Unit
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val tl = Offset(sw / 2f, sw / 2f)
            val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
            if (anim > 0f) {
                drawArc(color, -90f, 360f * anim, false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { center() }
    }
}

@Composable
private fun HdMiniStat(
    icon: ImageVector, tint: Color, label: String, value: String,
    fraction: Float, barColor: Color, modifier: Modifier
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "mini")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 7.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)) }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            fontFamily = NotoSansBengali, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = NotoSansBengali, maxLines = 1, softWrap = false)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(barColor.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(CircleShape).background(barColor))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ৫) আজকের রুটিন + আজকের লক্ষ্য (RoutineViewModel-এর আসল ডেটা)
// ═══════════════════════════════════════════════════════════
@Composable
internal fun HomeRoutineGoalRow(
    routine: DailyRoutine,
    goal: RoutineGoal,
    studyDoneMin: Int,
    onOpenRoutine: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HdRoutineCard(routine, onOpenRoutine, Modifier.weight(1f).fillMaxHeight())
        HdGoalCard(routine, goal, studyDoneMin, onOpenRoutine, Modifier.weight(1f).fillMaxHeight())
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

@Composable
private fun HdRoutineCard(routine: DailyRoutine, onOpen: () -> Unit, modifier: Modifier) {
    HdCard(modifier, radius = 24.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HdSectionTitle(Icons.Default.CalendarMonth, "আজকের রুটিন", MaterialTheme.colorScheme.onSurface, "দেখুন সব", onOpen)
            // সময় অনুযায়ী সাজানো: রিমাইন্ডার-সময় আছে এমন আগে, তারপর বাকি; সম্পন্নগুলো শেষে
            val shown = remember(routine) {
                routine.items.sortedWith(
                    compareBy<RoutineItem>({ it.done }, { if (it.hasReminder) it.reminderHour * 60 + it.reminderMinute else 24 * 60 + 1 })
                ).take(2)
            }
            if (shown.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(HdBlue.copy(alpha = 0.07f)).clickable(onClick = onOpen).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("আজকের রুটিন খালি", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                    Text("ট্যাপ করে পড়ার রুটিন যোগ করো", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                }
            } else {
                shown.forEachIndexed { i, item ->
                    val chipColor = if (i % 2 == 0) HdGreen else HdBlue
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(chipColor.copy(alpha = 0.07f))
                            .clickable(onClick = onOpen).padding(horizontal = 6.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(chipColor.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            Text(
                                item.reminderTimeLabel().ifBlank { "যেকোনো" },
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chipColor,
                                fontFamily = NotoSansBengali, maxLines = 1, softWrap = false
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            val head = listOf(item.subject, item.subTopic).filter { it.isNotBlank() }
                                .joinToString(" – ").ifBlank { item.title }
                            Text(head, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${hdFmtMin(item.minutes)}${if (item.done) " · সম্পন্ন" else ""}", fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, maxLines = 1)
                        }
                        if (item.done) Icon(Icons.Default.CheckCircle, null, tint = HdGreen, modifier = Modifier.size(16.dp))
                        else HdChevron(MaterialTheme.colorScheme.onSurfaceVariant, 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HdGoalCard(
    routine: DailyRoutine, goal: RoutineGoal, studyDoneMin: Int,
    onOpen: () -> Unit, modifier: Modifier
) {
    // মোট সময়: রুটিন থাকলে আইটেমগুলোর যোগফল, নইলে দৈনিক লক্ষ্য
    val totalMin = if (routine.items.isNotEmpty()) routine.items.sumOf { it.minutes } else goal.dailyMinutes
    val doneMin  = if (routine.items.isNotEmpty()) routine.items.filter { it.done }.sumOf { it.minutes } else studyDoneMin
    val fraction = if (totalMin > 0) doneMin.toFloat() / totalMin else 0f
    // বিষয় অনুযায়ী গ্রুপ — সব আইটেম শেষ হলে টিক
    val groups = remember(routine) {
        routine.items.groupBy { it.subject.ifBlank { it.title } }
            .map { (k, v) -> k to v.all { it.done } }
            .take(3)
    }
    HdCard(modifier, radius = 24.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HdSectionTitle(Icons.Default.TrackChanges, "আজকের লক্ষ্য", MaterialTheme.colorScheme.onSurface, "দেখুন সব", onOpen)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(HdGreen.copy(alpha = 0.06f)).clickable(onClick = onOpen).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HdRing(fraction, 66.dp, 7.dp, HdGreen, HdGreen.copy(alpha = 0.18f)) {
                    // আগে এখানে লক্ষ্যের মোট সময় (ডিফল্ট 2h 0m) দেখাত বলে সবসময় একই থাকত —
                    // এখন আজ কতক্ষণ পড়েছো সেটা দেখায়, নিচে ছোট করে লক্ষ্য
                    Text(hdFmtMin(doneMin), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, maxLines = 1, softWrap = false)
                    Text("লক্ষ্য ${hdFmtMin(totalMin)}", fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (groups.isEmpty()) {
                        Text("আজ ${hdFmtMin(doneMin)} পড়েছো", fontSize = 11.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("রুটিন যোগ করলে এখানে বিষয়ের তালিকা আসবে", fontSize = 9.5.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp)
                    } else {
                        groups.forEach { (label, done) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (done) Icon(Icons.Default.CheckCircle, null, tint = HdGreen, modifier = Modifier.size(15.dp))
                                else Icon(Icons.Default.RadioButtonUnchecked, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(label, fontSize = 11.sp, fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
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
// ৫) Leaderboard · Support · Tools — তিনটা গ্রেডিয়েন্ট কার্ড
// ═══════════════════════════════════════════════════════════
@Composable
internal fun HomeBottomTilesRow(onLeaderboard: () -> Unit, onSupport: () -> Unit, onTools: () -> Unit, leaderboardSub: String = "শীর্ষ শিক্ষার্থী") {
    Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HdBigTile("Leaderboard", leaderboardSub, Icons.Default.EmojiEvents, Color(0xFFFBBF24), Color(0xFFF97316), onLeaderboard, Modifier.weight(1f))
        HdBigTile("Support", "AI সাহায্য", Icons.Default.SupportAgent, Color(0xFF818CF8), Color(0xFF4F46E5), onSupport, Modifier.weight(1f))
        HdBigTile("More", "আরও সব", Icons.Default.Build, Color(0xFF2DD4BF), Color(0xFF0D9488), onTools, Modifier.weight(1f))
    }
}

@Composable
private fun HdBigTile(title: String, sub: String, icon: ImageVector, c1: Color, c2: Color, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.fillMaxHeight().shadow(3.dp, shape, clip = false).clip(shape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .clickable(onClick = onClick)
    ) {
        // হালকা সাজসজ্জার বৃত্ত
        Box(Modifier.size(64.dp).align(Alignment.TopEnd).offset(x = 20.dp, y = (-20).dp)
            .clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    fontFamily = NotoSansBengali, maxLines = 1, softWrap = false)
                Text(sub, fontSize = 10.sp, color = Color.White.copy(alpha = 0.88f), fontFamily = NotoSansBengali, maxLines = 1)
            }
        }
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
            Text("⋯ More", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
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
