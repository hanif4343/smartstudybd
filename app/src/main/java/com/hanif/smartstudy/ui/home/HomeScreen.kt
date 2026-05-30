package com.hanif.smartstudy.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.HomeUiState
import com.hanif.smartstudy.viewmodel.HomeViewModel

// Brand colors
private val PrimaryIndigo   = Color(0xFF4F46E5)
private val DeepIndigo      = Color(0xFF1E1B4B)
private val Amber           = Color(0xFFF59E0B)
private val GreenMint       = Color(0xFF10B981)
private val CardBg          = Color(0xFFFFFFFF)
private val SlateBg         = Color(0xFFF8FAFC)
private val TextPrimary     = Color(0xFF1E293B)
private val TextMuted       = Color(0xFF64748B)

@Composable
fun HomeScreen(
    viewModel     : HomeViewModel = viewModel(),
    onSearchClick : () -> Unit    = {},
    onTypingClick : () -> Unit    = {}
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp)   // bottom nav এর জায়গা
    ) {
        // ── Hero Header ──
        HomeHero(
            state    = state,
            onNameEdit = { viewModel.updateUserName(it) },
            onRefresh  = { viewModel.refresh() }
        )

        // ── Offline banner ──
        if (state.isOffline) OfflineBanner()

        // Body content
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Quick Action row — Search + Typing
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionCard(
                    emoji    = "🔍",
                    label    = "Global Search",
                    subtitle = "সব প্রশ্ন খোঁজুন",
                    color    = Color(0xFF4F46E5),
                    modifier = Modifier.weight(1f),
                    onClick  = onSearchClick
                )
                QuickActionCard(
                    emoji    = "⌨️",
                    label    = "Typing Practice",
                    subtitle = "WPM বাড়ান",
                    color    = Color(0xFF059669),
                    modifier = Modifier.weight(1f),
                    onClick  = onTypingClick
                )
            }

            // Daily Quote
            QuoteCard(quote = state.dailyQuote)

            // Quick Stats row
            QuickStatsRow(stats = state.studyStats)

            // Daily Goal
            DailyGoalCard(
                goal     = state.goalProgress,
                onSetGoal = { viewModel.setDailyGoal(it) }
            )

            // Weekly Streak
            WeeklyStreakCard(streak = state.streakInfo)

            // Exam Countdown
            if (state.examCountdown.isSet) {
                ExamCountdownCard(countdown = state.examCountdown)
            } else {
                SetExamCard(onSet = { d, n -> viewModel.setExamDate(d, n) })
            }

            // Loading / Error
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryIndigo, modifier = Modifier.size(32.dp))
                }
            }
            state.error?.let { ErrorBanner(it) }

            // Today's topic (Study/QBank)
            TodayTopicCard(content = state.content)

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────
// Hero Header — avatar, name, XP bar
// ─────────────────────────────────────────
@Composable
private fun HomeHero(
    state     : HomeUiState,
    onNameEdit: (String) -> Unit,
    onRefresh : () -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var nameInput   by remember { mutableStateOf(state.user?.name ?: "") }
    LaunchedEffect(state.user?.name) { nameInput = state.user?.name ?: "" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF312E81), Color(0xFF4F46E5), Color(0xFF6366F1)))
            )
            .padding(top = 16.dp, bottom = 20.dp, start = 14.dp, end = 14.dp)
    ) {
        Column {
            // Top bar: name + refresh
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier             = Modifier.fillMaxWidth()
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val pic = state.user?.picture
                    if (!pic.isNullOrEmpty()) {
                        AsyncImage(
                            model             = pic,
                            contentDescription = "Avatar",
                            modifier          = Modifier.fillMaxSize(),
                            contentScale      = ContentScale.Crop
                        )
                    } else {
                        Text("👤", fontSize = 26.sp)
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Name + role
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "স্বাগতম! 👋",
                        fontSize   = 10.sp,
                        color      = Color.White.copy(alpha = 0.6f),
                        fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold
                    )
                    if (editingName) {
                        OutlinedTextField(
                            value         = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine    = true,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            textStyle     = LocalTextStyle.current.copy(
                                color      = Color.White,
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Color.White.copy(0.6f),
                                unfocusedBorderColor = Color.White.copy(0.3f),
                                cursorColor          = Color.White
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onNameEdit(nameInput.trim())
                                    editingName = false
                                }) { Text("✓", color = Color.White, fontSize = 16.sp) }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text       = state.user?.displayName() ?: "নাম লিখুন",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White,
                                fontFamily = NotoSansBengali,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick  = { editingName = true },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, tint = Color.White.copy(0.6f),
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                        // Role + Class badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            state.user?.role?.let {
                                val isAdmin = it.lowercase() == "admin"
                                Badge(
                                    containerColor = if (isAdmin) Amber else Color.White.copy(0.15f),
                                    contentColor   = if (isAdmin) Color(0xFF78350F) else Color.White.copy(0.85f)
                                ) {
                                    Text(it, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                            val classLabel = state.user?.classLevel?.ifBlank { null }
                                ?: state.user?.userType?.takeIf { it != "General" }
                            classLabel?.let {
                                Badge(
                                    containerColor = Color.White.copy(0.18f),
                                    contentColor   = Color.White.copy(0.9f)
                                ) {
                                    Text(it, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // XP badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⭐", fontSize = 14.sp)
                        Text(
                            text       = state.xpInfo.xp.toString(),
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White,
                            fontFamily = NotoSansBengali
                        )
                    }
                    Text("XP", fontSize = 8.sp, color = Color.White.copy(0.8f), fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(6.dp))

                // Refresh
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Level + XP progress bar
            val xp = state.xpInfo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${xp.currentLevel.emoji} Lv.${xp.currentLevel.level} · ${xp.currentLevel.name}",
                    color = Color.White.copy(0.9f), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali
                )
                xp.nextLevel?.let {
                    Text(
                        "${it.minXP - xp.xp} XP → ${it.name}",
                        color = Color.White.copy(0.55f), fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressBar(pct = xp.progressPct / 100f)
        }
    }
}

@Composable
private fun LinearProgressBar(pct: Float) {
    val animPct by animateFloatAsState(
        targetValue    = pct,
        animationSpec  = tween(800, easing = FastOutSlowInEasing),
        label          = "xpBar"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animPct)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Amber, Color(0xFFD97706))))
        )
    }
}

// ─────────────────────────────────────────
// Motivational Quote Card
// ─────────────────────────────────────────
@Composable
fun QuoteCard(quote: MotivationalQuote) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border    = BorderStroke(1.dp, Color(0xFFA7F3D0))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("💬", fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
            Column {
                Text(
                    text       = "\"${quote.text}\"",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF065F46),
                    fontFamily = NotoSansBengali,
                    lineHeight = 18.sp
                )
                Text(
                    text       = "— ${quote.author}",
                    fontSize   = 10.sp,
                    color      = Color(0xFF059669),
                    fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────
// Quick Stats Row — সঠিক/ভুল/নির্ভুলতা/সময়
// ─────────────────────────────────────────
@Composable
fun QuickStatsRow(stats: StudyStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = DeepIndigo)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "📊 আজকের পরিসংখ্যান",
                fontSize   = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White,
                fontFamily = NotoSansBengali,
                modifier   = Modifier.padding(bottom = 10.dp)
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatMini("✅", stats.correctCount.toString(),   "সঠিক",    Color(0xFF4ADE80), Modifier.weight(1f))
                StatMini("❌", stats.wrongCount.toString(),     "ভুল",     Color(0xFFF87171), Modifier.weight(1f))
                StatMini("🎯", "${stats.accuracyPct}%",         "নির্ভুল", Color(0xFFA78BFA), Modifier.weight(1f))
                StatMini("⏱️", "${stats.todayMinutes}মি",       "আজ",      Color(0xFFFBBF24), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatMini(icon: String, value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier           = modifier
            .background(Color.White.copy(0.08f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon,  fontSize = 16.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valueColor,
            fontFamily = NotoSansBengali, lineHeight = 16.sp)
        Text(label, fontSize = 8.sp,  fontWeight = FontWeight.Bold, color = Color.White.copy(0.5f),
            fontFamily = NotoSansBengali)
    }
}

// ─────────────────────────────────────────
// Daily Goal Card — circular ring progress
// ─────────────────────────────────────────
@Composable
fun DailyGoalCard(goal: GoalProgress, onSetGoal: (Int) -> Unit) {
    var showGoalDialog by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ring progress
            Box(
                modifier        = Modifier.size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress     = { goal.progressPct / 100f },
                    modifier     = Modifier.size(60.dp),
                    color        = PrimaryIndigo,
                    strokeWidth  = 6.dp,
                    trackColor   = Color(0xFFE2E8F0)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${goal.doneMinutes}",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = TextPrimary,
                        fontFamily = NotoSansBengali
                    )
                    Text("মি", fontSize = 7.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "📚 দৈনিক লক্ষ্য",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPrimary,
                    fontFamily = NotoSansBengali
                )
                Text(
                    "${goal.doneMinutes} / ${goal.goalMinutes} মিনিট",
                    fontSize   = 11.sp,
                    color      = TextMuted,
                    fontFamily = NotoSansBengali,
                    modifier   = Modifier.padding(top = 3.dp)
                )
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(goal.progressPct / 100f)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(PrimaryIndigo, Color(0xFF818CF8)))
                            )
                    )
                }
                if (goal.progressPct >= 100f) {
                    Text("🎉 লক্ষ্য পূরণ হয়েছে!", fontSize = 10.sp, color = GreenMint,
                        fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }

            Spacer(Modifier.width(8.dp))

            // Edit goal button
            IconButton(onClick = { showGoalDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }

    // Goal set dialog
    if (showGoalDialog) {
        GoalSetDialog(
            current  = goal.goalMinutes,
            onSet    = { onSetGoal(it); showGoalDialog = false },
            onDismiss = { showGoalDialog = false }
        )
    }
}

@Composable
private fun GoalSetDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(10, 20, 30, 45, 60, 90)
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("দৈনিক লক্ষ্য নির্ধারণ", fontFamily = NotoSansBengali,
                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        },
        text = {
            Column {
                Text("পড়ার লক্ষ্য বেছে নিন:", fontFamily = NotoSansBengali, fontSize = 12.sp,
                    color = TextMuted, modifier = Modifier.padding(bottom = 10.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.chunked(3).forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { opt ->
                                FilterChip(
                                    selected = selected == opt,
                                    onClick  = { selected = opt },
                                    label    = {
                                        Text("${opt}মি", fontFamily = NotoSansBengali,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(selected) }) {
                Text("সেট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                    color = PrimaryIndigo)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", fontFamily = NotoSansBengali, color = TextMuted)
            }
        }
    )
}

// ─────────────────────────────────────────
// Weekly Streak Card — 7 dot
// ─────────────────────────────────────────
@Composable
fun WeeklyStreakCard(streak: StreakInfo) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "সাপ্তাহিক Streak",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPrimary,
                    fontFamily = NotoSansBengali
                )
                Badge(
                    containerColor = Color(0xFFFFFBEB),
                    contentColor   = Amber
                ) {
                    Text(
                        "🔥 ${streak.streakDays} দিন",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = NotoSansBengali,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                streak.weekDays.forEach { day ->
                    StreakDot(day = day, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StreakDot(day: StreakDay, modifier: Modifier = Modifier) {
    val bg = when {
        day.isToday -> Brush.linearGradient(listOf(PrimaryIndigo, Color(0xFF4338CA)))
        day.isDone  -> Brush.linearGradient(listOf(Color(0xFFDCFCE7), Color(0xFFDCFCE7)))
        else        -> Brush.linearGradient(listOf(Color(0xFFF1F5F9), Color(0xFFF1F5F9)))
    }
    val textColor = when {
        day.isToday -> Color.White
        day.isDone  -> Color(0xFF166534)
        else        -> Color(0xFFCBD5E1)
    }
    val icon = when {
        day.isToday -> if (day.isDone) "🔥" else "📍"
        day.isDone  -> "✓"
        else        -> ""
    }

    Column(
        modifier           = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon,         fontSize = 12.sp, lineHeight = 14.sp)
        Text(day.labelBn, fontSize = 8.sp,  fontWeight = FontWeight.ExtraBold,
            color = textColor, fontFamily = NotoSansBengali)
    }
}

// ─────────────────────────────────────────
// Exam Countdown Card
// ─────────────────────────────────────────
@Composable
fun ExamCountdownCard(countdown: ExamCountdown) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = DeepIndigo)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("পরীক্ষার কাউন্টডাউন ⏳", fontSize = 10.sp,
                        color = Color.White.copy(0.55f), fontWeight = FontWeight.Bold,
                        fontFamily = NotoSansBengali)
                    Text(countdown.examName, fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.White,
                        fontFamily = NotoSansBengali)
                }
                Text("📅", fontSize = 24.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CountdownBox(countdown.days.toString(), "দিন", Modifier.weight(1f))
                CountdownBox(countdown.hours.toString().padStart(2,'0'), "ঘণ্টা", Modifier.weight(1f))
                CountdownBox(countdown.minutes.toString().padStart(2,'0'), "মিনিট", Modifier.weight(1f))
                CountdownBox(countdown.seconds.toString().padStart(2,'0'), "সেকেন্ড", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CountdownBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier           = modifier
            .background(Color.White.copy(0.1f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            color = Color.White, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f),
            fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
    }
}

@Composable
private fun SetExamCard(onSet: (String, String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)),
        border   = BorderStroke(1.dp, Color(0xFFBFDBFE))
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📅", fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("পরীক্ষার তারিখ সেট করুন", fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold, color = PrimaryIndigo,
                    fontFamily = NotoSansBengali)
                Text("কাউন্টডাউন চালু করতে ট্যাপ করুন", fontSize = 10.sp,
                    color = TextMuted, fontFamily = NotoSansBengali)
            }
        }
    }

    if (showDialog) {
        ExamDateDialog(
            onSet     = { d, n -> onSet(d, n); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ExamDateDialog(onSet: (String, String) -> Unit, onDismiss: () -> Unit) {
    var examName by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পরীক্ষার তথ্য", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = examName,
                    onValueChange = { examName = it },
                    label         = { Text("পরীক্ষার নাম", fontFamily = NotoSansBengali) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = examDate,
                    onValueChange = { examDate = it },
                    label         = { Text("তারিখ (YYYY-MM-DD)", fontFamily = NotoSansBengali) },
                    singleLine    = true,
                    placeholder   = { Text("2025-12-31") },
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (examName.isNotBlank() && examDate.isNotBlank()) onSet(examDate, examName) },
                enabled  = examName.isNotBlank() && examDate.isNotBlank()
            ) {
                Text("সেট করুন", fontFamily = NotoSansBengali, color = PrimaryIndigo,
                    fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) }
        }
    )
}

// ─────────────────────────────────────────
// Today's Topic Card
// ─────────────────────────────────────────
@Composable
fun TodayTopicCard(content: AppContent) {
    if (content.isEmpty()) return

    val dayNum    = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
    val useStudy  = dayNum % 2 == 0 && content.study.isNotEmpty()
    val items     = if (useStudy) content.study.map { it.subTopic to it.subject }
                    else content.qbank.map { it.subTopic to it.subject }
    val topics    = items.mapNotNull { it.first }.distinct()
    if (topics.isEmpty()) return

    val topic     = topics[dayNum % topics.size]
    val count     = items.count { it.first == topic }
    val subject   = items.firstOrNull { it.first == topic }?.second ?: ""
    val isStudy   = useStudy

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isStudy) Color(0xFFECFDF5) else Color(0xFFEFF6FF)
        ),
        border = BorderStroke(1.dp, if (isStudy) Color(0xFFA7F3D0) else Color(0xFFBFDBFE))
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isStudy) "📌 আজকের পাঠ — Study" else "📌 আজকের প্রশ্নপত্র — QBank",
                    fontSize = 10.sp,
                    color    = if (isStudy) Color(0xFF059669) else Color(0xFF2563EB),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali,
                    modifier   = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    topic ?: "আজকের টপিক",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isStudy) Color(0xFF065F46) else Color(0xFF1E40AF),
                    fontFamily = NotoSansBengali
                )
                Text(
                    "$subject · $count টি প্রশ্ন",
                    fontSize   = 11.sp,
                    color      = if (isStudy) Color(0xFF047857) else Color(0xFF3B82F6),
                    fontFamily = NotoSansBengali,
                    modifier   = Modifier.padding(top = 2.dp)
                )
            }
            Text(if (isStudy) "📖" else "📚", fontSize = 28.sp)
        }
    }
}

// ─────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────
// Quick Action Card (Search / Typing)
// ─────────────────────────────────────────
@Composable
private fun QuickActionCard(
    emoji    : String,
    label    : String,
    subtitle : String,
    color    : Color,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = modifier,
        shape     = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border    = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(emoji, fontSize = 24.sp)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = color, fontFamily = NotoSansBengali)
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF64748B),
                fontFamily = NotoSansBengali)
        }
    }
}

// ─────────────────────────────────────────
@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFEF3C7))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("📶", fontSize = 14.sp)
        Text(
            "অফলাইন মোড — cached data দেখাচ্ছে",
            fontSize   = 11.sp,
            color      = Color(0xFF92400E),
            fontWeight = FontWeight.Bold,
            fontFamily = NotoSansBengali
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠️", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            Text(message, fontSize = 11.sp, color = Color(0xFF9F1239),
                fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold)
        }
    }
}
