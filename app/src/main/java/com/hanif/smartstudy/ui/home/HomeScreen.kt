package com.hanif.smartstudy.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.vector.ImageVector
import com.hanif.smartstudy.ui.menu.StudyBuddyQuickButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.shared.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.HomeUiState
import com.hanif.smartstudy.viewmodel.HomeViewModel
import com.hanif.smartstudy.viewmodel.QuizViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hanif.smartstudy.receiver.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

private val PrimaryIndigo = Color(0xFF4F46E5)
private val DeepIndigo    = Color(0xFF1E1B4B)
private val Amber         = Color(0xFFF59E0B)
private val GreenMint     = Color(0xFF10B981)

// ═══════════════════════════════════════════════════════════
// Exam Eve Notification — পরীক্ষার আগের রাত ১০টায় রিমাইন্ডার
// ═══════════════════════════════════════════════════════════
object ExamNotificationHelper {

    private const val EXAM_NOTIF_REQ = 2025

    fun scheduleExamEveReminder(ctx: Context, dateStr: String, examName: String) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val examDate = sdf.parse(dateStr) ?: return

            val cal = Calendar.getInstance().apply {
                time = examDate
                add(Calendar.DAY_OF_YEAR, -1)   // আগের দিন
                set(Calendar.HOUR_OF_DAY, 22)    // রাত ১০টা
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            if (cal.timeInMillis <= System.currentTimeMillis()) return

            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_TYPE, ReminderReceiver.TYPE_EXAM_EVE)
                putExtra("title", "📅 আগামীকাল পরীক্ষা!")
                putExtra("body", "\"$examName\" কাল। শেষবারের মতো রিভিশন করে নাও!")
                putExtra("notif_id", EXAM_NOTIF_REQ)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, EXAM_NOTIF_REQ, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelExamEveReminder(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, EXAM_NOTIF_REQ,
            Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}

@Composable
fun HomeScreen(
    viewModel              : HomeViewModel = viewModel(),
    quizViewModel          : QuizViewModel? = null,
    highlightRoutineItemId : String? = null,
    onRoutineItemHighlighted : () -> Unit = {},
    onSearchClick : () -> Unit = {},
    onTypingClick : () -> Unit = {},
    onOpenStudy       : (subject: String, subTopic: String) -> Unit = { _, _ -> },
    onOpenInstantTest : (subject: String, subTopic: String) -> Unit = { _, _ -> },
    onOpenWeeklyTest  : () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val ctx   = LocalContext.current

    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var wrongItems by remember { mutableStateOf(quizViewModel?.getWrongQuestions() ?: emptyList<Pair<QuestionItem, Int>>()) }
    // state পরিবর্তনে reload করি, কিন্তু practice চলাকালে নয়
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            wrongItems = quizViewModel?.getWrongQuestions() ?: emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp)
    ) {
        HomeHero(state = state, onNameEdit = { viewModel.updateUserName(it) }, onRefresh = { viewModel.refresh() })

        if (state.isOffline) OfflineBanner()

        Column(
            modifier            = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            AdBannerPlaceholder()

            QuickStatsRow(stats = state.studyStats)

            AdNativePlaceholder()

            // Daily Goal feature removed

            DailyRoutineCard(
                highlightRoutineItemId   = highlightRoutineItemId,
                onRoutineItemHighlighted = onRoutineItemHighlighted,
                onOpenStudy       = onOpenStudy,
                onOpenInstantTest = onOpenInstantTest,
                onOpenWeeklyTest  = onOpenWeeklyTest
            )

            WeeklyStreakCard(streak = state.streakInfo)

            // ── Wrong Question Review Section (QuizViewModel এর আসল মেথড দিয়ে ফিক্সড) ──
            if (wrongItems.isNotEmpty() || (quizViewModel?.getWrongQuestions()?.isNotEmpty() == true)) {
                WrongReviewSection(
                    wrongItems      = wrongItems,
                    onAnswerMcq     = { qId, opt -> 
                        val idx = wrongItems.indexOfFirst { it.first.id == qId }
                        if (idx != -1) {
                            quizViewModel?.answerMcq(idx, opt)
                        }
                    },
                    onAnswerWritten = { qId, text -> 
                        val idx = wrongItems.indexOfFirst { it.first.id == qId }
                        if (idx != -1) {
                            quizViewModel?.answerWritten(idx, text) ?: 0
                        } else { 0 }
                    },
                    onRemoveCorrect = { qId -> 
                        quizViewModel?.removeWrongQId(qId)
                        // local state থেকেও সাথে সাথে সরিয়ে দাও
                        // তাহলে পরবর্তী recomposition এ আর দেখাবে না
                        wrongItems = wrongItems.filter { it.first.id != qId }
                    }
                )
            }

            val examExpired = state.examCountdown.isSet &&
                state.examCountdown.days   == 0L &&
                state.examCountdown.hours  == 0L &&
                state.examCountdown.minutes == 0L &&
                state.examCountdown.seconds == 0L
            when {
                state.examCountdown.isSet && !examExpired ->
                    ExamCountdownCard(
                        countdown = state.examCountdown,
                        onClear   = { viewModel.clearExamDate() }
                    )
                !state.examCountdown.isSet ->
                    SetExamCard(onSet = { d, n ->
                        viewModel.setExamDate(d, n)
                        ExamNotificationHelper.scheduleExamEveReminder(ctx, d, n)
                    })
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryIndigo, modifier = Modifier.size(32.dp))
                }
            }
            state.error?.let { ErrorBanner(it) }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// REAL ADMOB ADS
// ═══════════════════════════════════════════════════════════

@Composable
fun AdBannerPlaceholder() {
    com.hanif.smartstudy.ui.ads.AdBannerView(
        adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_HOME
    )
}

@Composable
fun AdNativePlaceholder() {
    com.hanif.smartstudy.ui.ads.AdBannerView(
        adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_HOME
    )
}

// ═══════════════════════════════════════════════════════════
// EXISTING COMPONENTS
// ═══════════════════════════════════════════════════════════

@Composable
private fun HomeHero(state: HomeUiState, onNameEdit: (String) -> Unit, onRefresh: () -> Unit) {
    var editingName by remember { mutableStateOf(false) }
    var nameInput   by remember { mutableStateOf(state.user?.name ?: "") }
    LaunchedEffect(state.user?.name) { nameInput = state.user?.name ?: "" }

    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF312E81), Color(0xFF4F46E5), Color(0xFF6366F1))))
            .padding(top = 16.dp, bottom = 20.dp, start = 14.dp, end = 14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(56.dp).clip(CircleShape)
                    .background(Color.White.copy(0.2f))
                    .border(2.dp, Color.White.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    val pic = state.user?.picture
                    if (!pic.isNullOrEmpty()) {
                        AsyncImage(model = pic, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else { Text("👤", fontSize = 26.sp) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("স্বাগতম! 👋", fontSize = 10.sp, color = Color.White.copy(0.6f),
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    if (editingName) {
                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it },
                            singleLine = true, modifier = Modifier.fillMaxWidth().height(40.dp),
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(0.6f), unfocusedBorderColor = Color.White.copy(0.3f), cursorColor = Color.White),
                            trailingIcon = { IconButton(onClick = { onNameEdit(nameInput.trim()); editingName = false }) { Text("✓", color = Color.White, fontSize = 16.sp) } })
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.user?.displayName() ?: "নাম লিখুন", fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold, color = Color.White,
                                fontFamily = NotoSansBengali, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { editingName = true }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Edit, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(14.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                            state.user?.role?.let {
                                val isAdmin = it.lowercase() == "admin"
                                Badge(containerColor = if (isAdmin) Amber else Color.White.copy(0.15f),
                                    contentColor = if (isAdmin) Color(0xFF78350F) else Color.White.copy(0.85f)) {
                                    Text(it, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⭐", fontSize = 14.sp)
                        Text(state.xpInfo.xp.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = NotoSansBengali) }
                    Text("XP", fontSize = 8.sp, color = Color.White.copy(0.8f), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            val xp = state.xpInfo
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${xp.currentLevel.emoji} Lv.${xp.currentLevel.level} · ${xp.currentLevel.name}",
                    color = Color.White.copy(0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                xp.nextLevel?.let { Text("${it.minXP - xp.xp} XP → ${it.name}", color = Color.White.copy(0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressBar(pct = xp.progressPct / 100f)
        }
    }
}

@Composable
private fun LinearProgressBar(pct: Float) {
    val animPct by animateFloatAsState(pct, tween(800, easing = FastOutSlowInEasing), label = "xpBar")
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.2f))) {
        Box(Modifier.fillMaxWidth(animPct).fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Amber, Color(0xFFD97706)))))
    }
}

@Composable
fun QuickStatsRow(stats: StudyStats) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DeepIndigo)) {
        Column(Modifier.padding(12.dp)) {
            Text("📊 আজকের পরিসংখ্যান", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, fontFamily = NotoSansBengali, modifier = Modifier.padding(bottom = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatMini("✅", stats.correctCount.toString(), "সঠিক", Color(0xFF4ADE80), Modifier.weight(1f))
                StatMini("❌", stats.wrongCount.toString(), "ভুল", Color(0xFFF87171), Modifier.weight(1f))
                StatMini("🎯", "${stats.accuracyPct}%", "নির্ভুল", Color(0xFFA78BFA), Modifier.weight(1f))
                StatMini("⏱️", "${stats.todayMinutes}মি", "আজ", Color(0xFFFBBF24), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatMini(icon: String, value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White.copy(0.08f), RoundedCornerShape(10.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 16.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, fontFamily = NotoSansBengali, lineHeight = 16.sp)
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.5f), fontFamily = NotoSansBengali)
    }
}

@Composable
fun DailyRoutineCard(
    vm: com.hanif.smartstudy.viewmodel.RoutineViewModel = viewModel(),
    highlightRoutineItemId   : String? = null,
    onRoutineItemHighlighted : () -> Unit = {},
    onOpenStudy       : (subject: String, subTopic: String) -> Unit = { _, _ -> },
    onOpenInstantTest : (subject: String, subTopic: String) -> Unit = { _, _ -> },
    onOpenWeeklyTest  : () -> Unit = {}
) {
    val routine by vm.state.collectAsState()
    val subjectOptions by vm.subjectOptions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var focusItem by remember { mutableStateOf<com.hanif.smartstudy.data.model.RoutineItem?>(null) }
    var reminderEditItem by remember { mutableStateOf<com.hanif.smartstudy.data.model.RoutineItem?>(null) }
    // highlight state — set from deeplink, cleared after animation
    var activeHighlightId by remember { mutableStateOf<String?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Notification tap → scroll to item + pulse highlight
    LaunchedEffect(highlightRoutineItemId, routine.items) {
        val targetId = highlightRoutineItemId ?: return@LaunchedEffect
        if (routine.items.isEmpty()) return@LaunchedEffect
        val idx = routine.items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            kotlinx.coroutines.delay(150)
            activeHighlightId = targetId
            kotlinx.coroutines.delay(3500)
            activeHighlightId = null
            onRoutineItemHighlighted()
        }
    }

    if (showAddDialog) {
        AddRoutineItemDialog(
            subjectOptions = subjectOptions,
            onAdd = { title, subject, subTopic, minutes, reminderEnabled, rHour, rMinute ->
                vm.addItem(title, subject, subTopic, minutes, reminderEnabled, rHour, rMinute)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    reminderEditItem?.let { item ->
        RoutineReminderDialog(
            item = item,
            onSave = { enabled, hour, minute ->
                vm.setItemReminder(item.id, enabled, hour, minute)
                reminderEditItem = null
            },
            onDismiss = { reminderEditItem = null }
        )
    }

    focusItem?.let { item ->
        RoutineFocusSheet(
            item = item,
            onDismiss = { focusItem = null },
            routineVm = vm
        )
    }

    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surface), CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("✅ আজকের রুটিন", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                    Text(
                        if (routine.items.isEmpty()) "এখনো কোনো রুটিন যুক্ত করা হয়নি"
                        else "${routine.doneCount}/${routine.totalCount} সম্পন্ন",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudyBuddyQuickButton()
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, null, tint = PrimaryIndigo)
                    }
                }
            }

            if (routine.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress   = { routine.progressPct / 100f },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.3.dp)),
                    color      = if (routine.isComplete) GreenMint else PrimaryIndigo,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (routine.isComplete) {
                    Text(
                        "🎉 আজকের রুটিন সম্পন্ন হয়েছে! দারুণ কাজ করেছো!",
                        fontSize = 11.sp, color = GreenMint, fontWeight = FontWeight.ExtraBold,
                        fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(routine.items, key = { it.id }) { item ->
                        val isHighlighted = item.id == activeHighlightId
                        RoutineItemRow(
                            item          = item,
                            isHighlighted = isHighlighted,
                            onToggle      = { vm.toggleItem(item.id) },
                            onRemove      = { vm.removeItem(item.id) },
                            onOpenFocus       = { focusItem = item },
                            onEditReminder    = { reminderEditItem = item },
                            onOpenStudy       = onOpenStudy,
                            onOpenInstantTest = onOpenInstantTest,
                            onOpenWeeklyTest  = onOpenWeeklyTest
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineItemRow(
    item          : com.hanif.smartstudy.data.model.RoutineItem,
    isHighlighted : Boolean = false,
    onToggle      : () -> Unit,
    onRemove      : () -> Unit,
    onOpenFocus       : () -> Unit = {},
    onEditReminder    : () -> Unit = {},
    onOpenStudy       : (subject: String, subTopic: String) -> Unit = { _, _ -> },
    onOpenInstantTest : (subject: String, subTopic: String) -> Unit = { _, _ -> },
    onOpenWeeklyTest  : () -> Unit = {}
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hasSubject = item.subject.isNotBlank()

    // ── Highlight pulse animation ─────────────────────────────────
    val highlightTransition = rememberInfiniteTransition(label = "routineHighlight")
    val highlightAlpha by highlightTransition.animateFloat(
        initialValue = 0.0f,
        targetValue  = if (isHighlighted) 0.28f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hlAlpha"
    )
    val highlightScale by highlightTransition.animateFloat(
        initialValue = 1.0f,
        targetValue  = if (isHighlighted) 1.015f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hlScale"
    )
    val hlBorderAlpha by highlightTransition.animateFloat(
        initialValue = 0.0f,
        targetValue  = if (isHighlighted) 0.9f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hlBorderAlpha"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = highlightScale, scaleY = highlightScale)
            .clip(RoundedCornerShape(10.dp))
            .background(primaryColor.copy(alpha = highlightAlpha))
            .then(
                if (isHighlighted)
                    Modifier.border(1.5.dp, primaryColor.copy(alpha = hlBorderAlpha), RoundedCornerShape(10.dp))
                else Modifier
            )
            .padding(vertical = 6.dp, horizontal = if (isHighlighted) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = GreenMint)
        )
        Column(
            Modifier
                .weight(1f)
                .then(if (hasSubject) Modifier.clickable { onOpenFocus() } else Modifier)
        ) {
            Text(
                item.title,
                fontSize = 13.sp,
                fontFamily = NotoSansBengali,
                fontWeight = if (item.done) FontWeight.Normal else FontWeight.Medium,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val metaParts = mutableListOf<String>()
            if (hasSubject) {
                metaParts.add(if (item.subTopic.isNotBlank()) "${item.subject} • ${item.subTopic}" else item.subject)
            }
            metaParts.add("${item.minutes} মিনিট")
            if (item.hasReminder) metaParts.add("⏰ ${item.reminderTimeLabel()}")
            Text(
                metaParts.joinToString(" • "),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotoSansBengali
            )
        }

        // ── এই আইটেমের নিজস্ব রিমাইন্ডার সেট/এডিট করার বাটন ──
        IconButton(onClick = onEditReminder, modifier = Modifier.size(28.dp)) {
            Icon(
                if (item.hasReminder) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                contentDescription = "রিমাইন্ডার",
                tint = if (item.hasReminder) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        if (hasSubject) {
            Box {
                IconButton(onClick = { showActionMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("📖 এখানেই পড়ো", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        onClick = {
                            showActionMenu = false
                            onOpenFocus()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📚 পুরো অধ্যায় দেখো", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        onClick = {
                            showActionMenu = false
                            onOpenStudy(item.subject, item.subTopic)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📝 এখন টেস্ট দাও (Instant)", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        onClick = {
                            showActionMenu = false
                            onOpenInstantTest(item.subject, item.subTopic)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("⚔️ সাপ্তাহিক চ্যালেঞ্জে যাও", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        onClick = {
                            showActionMenu = false
                            onOpenWeeklyTest()
                        }
                    )
                }
            }
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Routine Dialog — shared visual primitives
//  কাস্টম Dialog + Card স্টাইল ব্যবহার করা হয়েছে (AlertDialog নয়),
//  যাতে header badge, sectioned card, এবং বড় filled/outlined বাটন
//  রাখা যায় — অ্যাপের বাকি অংশের (AddTechniqueDialog) মতোই প্যাটার্ন,
//  এবং সব রঙ MaterialTheme.colorScheme থেকে আসে বলে ৪টা থিম + ডার্ক
//  মোড — সবকিছুতেই সঠিকভাবে মানানসই হয়।
// ═══════════════════════════════════════════════════════════

@Composable
private fun RoutineDialogShell(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {

                // ── হেডার: রঙিন আইকন ব্যাজ + শিরোনাম ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)

                Spacer(Modifier.height(20.dp))

                footer()
            }
        }
    }
}

@Composable
private fun RoutineSectionLabel(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
        letterSpacing = 0.2.sp
    )
}

// ── টাইম-চিপ — selected হলে primary রঙে ভরাট, নাহলে হালকা surface outline ──
@Composable
private fun MinuteChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (!selected) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg, fontFamily = NotoSansBengali)
    }
}

// ── রিমাইন্ডার অন/অফ + সময় বাছাই — গ্রুপ করা tinted surface row ──
@Composable
private fun ReminderToggleSection(
    reminderOn: Boolean,
    onToggle: (Boolean) -> Unit,
    timeLabel: String,
    onPickTime: () -> Unit,
    helperText: String? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (reminderOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (reminderOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (reminderOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (reminderOn) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                            null,
                            tint = if (reminderOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "আলাদা রিমাইন্ডার", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali
                        )
                        Text(
                            "এই টপিকের জন্য নির্দিষ্ট সময়ে নোটিফিকেশন",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali
                        )
                    }
                }
                Switch(
                    checked = reminderOn,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }

            AnimatedVisibility(visible = reminderOn) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(11.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onPickTime() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(timeLabel, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("পরিবর্তন →", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                    if (helperText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(helperText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    }
                }
            }
        }
    }
}

// ── ফুটার: outlined বাতিল + filled প্রধান একশন, দুটোই সমান প্রস্থ ──
@Composable
private fun RoutineDialogFooter(
    confirmLabel: String,
    confirmEnabled: Boolean = true,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).height(46.dp),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Text("বাতিল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f).height(46.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(confirmLabel, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun routineFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor    = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor  = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    focusedTextColor      = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor    = MaterialTheme.colorScheme.onSurface,
    cursorColor           = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRoutineItemDialog(
    subjectOptions: List<com.hanif.smartstudy.data.model.RoutineSubjectOption>,
    onAdd: (title: String, subject: String, subTopic: String, minutes: Int, reminderEnabled: Boolean, reminderHour: Int, reminderMinute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title    by remember { mutableStateOf("") }
    var subject  by remember { mutableStateOf("") }
    var subTopic by remember { mutableStateOf("") }
    var minutes  by remember { mutableStateOf(20) }
    var subjectMenuExpanded  by remember { mutableStateOf(false) }
    var subTopicMenuExpanded by remember { mutableStateOf(false) }
    val minuteOptions = listOf(10, 15, 20, 30, 45, 60)

    // ── এই আইটেমের নিজস্ব রিমাইন্ডার (alarm_create_v0 কনসেপ্ট) ──
    var reminderOn by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = 20, initialMinute = 0, is24Hour = false)
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        TimePickerDialog(
            state = timePickerState,
            onConfirm = { showTimePicker = false },
            onDismiss = { showTimePicker = false }
        )
    }

    val subTopicsForSubject = subjectOptions.firstOrNull { it.subject == subject }?.subTopics ?: emptyList()

    RoutineDialogShell(
        icon = Icons.Default.Add,
        title = "নতুন টপিক যুক্ত করো",
        subtitle = "আজকের রুটিনে যোগ হবে",
        onDismiss = onDismiss,
        footer = {
            RoutineDialogFooter(
                confirmLabel = "যুক্ত করো",
                confirmEnabled = title.isNotBlank(),
                onCancel = onDismiss,
                onConfirm = {
                    if (title.isNotBlank()) {
                        onAdd(
                            title, subject, subTopic, minutes,
                            reminderOn,
                            if (reminderOn) timePickerState.hour else -1,
                            if (reminderOn) timePickerState.minute else -1
                        )
                    }
                }
            )
        }
    ) {
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("টপিকের নাম", fontFamily = NotoSansBengali, fontSize = 12.sp) },
            placeholder = { Text("যেমন: বাংলাদেশের সংবিধান", fontFamily = NotoSansBengali, fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = routineFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = subjectMenuExpanded,
            onExpandedChange = { if (subjectOptions.isNotEmpty()) subjectMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = subject,
                onValueChange = {},
                readOnly = true,
                enabled = subjectOptions.isNotEmpty(),
                label = { Text("বিষয় (ঐচ্ছিক)", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                placeholder = {
                    Text(
                        if (subjectOptions.isEmpty()) "কোনো বিষয় পাওয়া যায়নি" else "নির্বাচন করুন",
                        fontFamily = NotoSansBengali, fontSize = 12.sp
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectMenuExpanded) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                colors = routineFieldColors(),
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = subjectMenuExpanded,
                onDismissRequest = { subjectMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("— কোনো বিষয় না —", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                    onClick = {
                        subject = ""
                        subTopic = ""
                        subjectMenuExpanded = false
                    }
                )
                subjectOptions.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.subject, fontFamily = NotoSansBengali, fontSize = 12.sp) },
                        onClick = {
                            subject = opt.subject
                            subTopic = ""
                            subjectMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (subject.isNotBlank() && subTopicsForSubject.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = subTopicMenuExpanded,
                onExpandedChange = { subTopicMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = subTopic,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("SubTopic (ঐচ্ছিক)", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                    placeholder = { Text("নির্বাচন করুন", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subTopicMenuExpanded) },
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    colors = routineFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = subTopicMenuExpanded,
                    onDismissRequest = { subTopicMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("— কোনো SubTopic না —", fontFamily = NotoSansBengali, fontSize = 12.sp) },
                        onClick = {
                            subTopic = ""
                            subTopicMenuExpanded = false
                        }
                    )
                    subTopicsForSubject.forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st, fontFamily = NotoSansBengali, fontSize = 12.sp) },
                            onClick = {
                                subTopic = st
                                subTopicMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutineSectionLabel("আনুমানিক সময়")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                minuteOptions.take(3).forEach { m ->
                    MinuteChip("${m}মি", minutes == m, { minutes = m }, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                minuteOptions.drop(3).forEach { m ->
                    MinuteChip("${m}মি", minutes == m, { minutes = m }, Modifier.weight(1f))
                }
            }
        }

        ReminderToggleSection(
            reminderOn = reminderOn,
            onToggle = { reminderOn = it },
            timeLabel = formatHourMinute(timePickerState.hour, timePickerState.minute),
            onPickTime = { showTimePicker = true }
        )
    }
}

// ── hour(0-23)/minute কে 12-ঘণ্টার AM/PM ফরম্যাটে দেখানোর হেল্পার ──
private fun formatHourMinute(hour: Int, minute: Int): String {
    val h12 = when {
        hour == 0  -> 12
        hour > 12  -> hour - 12
        else       -> hour
    }
    val ampm = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(h12, minute, ampm)
}

// ── Material3 TimePicker কে dialog আকারে দেখানোর জন্য — material3 এ বিল্ট-ইন
//    TimePickerDialog এখনো experimental/অনুপস্থিত, তাই হালকা wrapper ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    state: TimePickerState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "⏰ রিমাইন্ডারের সময় বেছে নাও",
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("বাতিল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("ঠিক আছে", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
    }
}

// ── বিদ্যমান routine আইটেমের রিমাইন্ডার (on/off + সময়) এডিট করার ডায়ালগ ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineReminderDialog(
    item: com.hanif.smartstudy.data.model.RoutineItem,
    onSave: (enabled: Boolean, hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var reminderOn by remember { mutableStateOf(item.hasReminder) }
    val initialHour   = if (item.reminderHour in 0..23) item.reminderHour else 20
    val initialMinute = if (item.reminderMinute in 0..59) item.reminderMinute else 0
    val timePickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        TimePickerDialog(
            state = timePickerState,
            onConfirm = { showTimePicker = false },
            onDismiss = { showTimePicker = false }
        )
    }

    RoutineDialogShell(
        icon = Icons.Default.Notifications,
        title = "রিমাইন্ডার",
        subtitle = item.title,
        onDismiss = onDismiss,
        footer = {
            RoutineDialogFooter(
                confirmLabel = "সংরক্ষণ করো",
                onCancel = onDismiss,
                onConfirm = {
                    onSave(reminderOn, if (reminderOn) timePickerState.hour else -1, if (reminderOn) timePickerState.minute else -1)
                }
            )
        }
    ) {
        ReminderToggleSection(
            reminderOn = reminderOn,
            onToggle = { reminderOn = it },
            timeLabel = formatHourMinute(timePickerState.hour, timePickerState.minute),
            onPickTime = { showTimePicker = true },
            helperText = "প্রতিদিন এই সময়ে এই টপিকের জন্য আলাদা নোটিফিকেশন আসবে।"
        )
    }
}


@Composable
fun DailyGoalCard(goal: GoalProgress, onSetGoal: (Int) -> Unit) {
    var showGoalDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surface), CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { goal.progressPct / 100f }, modifier = Modifier.size(60.dp),
                    color = PrimaryIndigo, strokeWidth = 6.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${goal.doneMinutes}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                    Text("মি", fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("📚 দৈনিক লক্ষ্য", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                Text("${goal.doneMinutes} / ${goal.goalMinutes} minute", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 3.dp))
                if (goal.progressPct >= 100f) Text("🎉 লক্ষ্য পূরণ হয়েছে!", fontSize = 10.sp, color = GreenMint, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = { showGoalDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
        }
    }
    if (showGoalDialog) GoalSetDialog(goal.goalMinutes, onSet = { onSetGoal(it); showGoalDialog = false }, onDismiss = { showGoalDialog = false })
}

@Composable
private fun GoalSetDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(10, 20, 30, 45, 60, 90)
    var selected by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("দৈনিক লক্ষ্য নির্ধারণ", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) },
        text = {
            Column {
                Text("পড়ার লক্ষ্য বেছে নিন:", fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.chunked(3).forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { opt -> FilterChip(selected == opt, { selected = opt }, { Text("${opt}মি", fontFamily = NotoSansBengali, fontSize = 11.sp, fontWeight = FontWeight.Bold) }) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSet(selected) }) { Text("সেট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = PrimaryIndigo) } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant) } })
}

@Composable
fun WeeklyStreakCard(streak: StreakInfo) {
    val isDark = isSystemInDarkTheme()
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(MaterialTheme.colorScheme.surface), CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("সাপ্তাহিক Streak", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                Badge(
                    containerColor = if (isDark) Color(0xFF1C1400) else Color(0xFFFFFBEB),
                    contentColor = Amber
                ) {
                    Text("🔥 ${streak.streakDays} দিন", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                streak.weekDays.forEach { day -> StreakDot(day, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StreakDot(day: StreakDay, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val doneBgStart = if (isDark) Color(0xFF052E16) else Color(0xFFDCFCE7)
    val doneBgEnd   = if (isDark) Color(0xFF166534) else Color(0xFFBBF7D0)
    val bg = when {
        day.isToday -> Brush.linearGradient(listOf(PrimaryIndigo, Color(0xFF4338CA)))
        day.isDone  -> Brush.linearGradient(listOf(doneBgStart, doneBgEnd))
        else        -> Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF94A3B8)))
    }
    val textColor = when {
        day.isToday -> Color.White
        day.isDone  -> if (isDark) Color(0xFF4ADE80) else Color(0xFF166534)
        else        -> Color(0xFFCBD5E1)
    }
    val icon = when { day.isToday -> if (day.isDone) "🔥" else "📍"; day.isDone -> "✓"; else -> "" }
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 12.sp, lineHeight = 14.sp)
        Text(day.labelBn, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = textColor, fontFamily = NotoSansBengali)
    }
}

@Composable
fun ExamCountdownCard(countdown: ExamCountdown, onClear: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(DeepIndigo)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("পরীক্ষার কাউন্টডাউন ⏳", fontSize = 10.sp, color = Color.White.copy(0.55f), fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                    Text(countdown.examName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = NotoSansBengali)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📅", fontSize = 24.sp)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(0.12f))
                            .clickable { onClear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("✕", fontSize = 12.sp, color = Color.White.copy(0.7f), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    Column(modifier.background(Color.White.copy(0.1f), RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
    }
}

@Composable
private fun SetExamCard(onSet: (String, String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { showDialog = true }, RoundedCornerShape(14.dp),
        CardDefaults.cardColors(Color(0xFFF0F4FF)), border = BorderStroke(1.dp, Color(0xFFBFDBFE))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📅", fontSize = 28.sp); Spacer(Modifier.width(10.dp))
            Column {
                Text("পরীক্ষার তারিখ সেট করুন", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryIndigo, fontFamily = NotoSansBengali)
                Text("কাউন্টডাউন চালু করতে ট্যাপ করুন", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
            }
        }
    }
    if (showDialog) ExamDateDialog(onSet = { d, n -> onSet(d, n); showDialog = false }, onDismiss = { showDialog = false })
}

@Composable
private fun ExamDateDialog(onSet: (String, String) -> Unit, onDismiss: () -> Unit) {
    var examName by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("পরীক্ষার তথ্য", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(examName, { examName = it }, label = { Text("পরীক্ষার নাম", fontFamily = NotoSansBengali) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(examDate, { examDate = it }, label = { Text("তারিখ (YYYY-MM-DD)", fontFamily = NotoSansBengali) }, singleLine = true, placeholder = { Text("2025-12-31") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ if (examName.isNotBlank() && examDate.isNotBlank()) onSet(examDate, examName) }, enabled = examName.isNotBlank() && examDate.isNotBlank()) { Text("সেট করুন", fontFamily = NotoSansBengali, color = PrimaryIndigo, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } })
}

@Composable
private fun OfflineBanner() {
    Row(Modifier.fillMaxWidth().background(Color(0xFFFEF3C7)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📶", fontSize = 14.sp)
        Text("অফলাইন মোড — cached data দেখাচ্ছে", fontSize = 11.sp, color = Color(0xFF92400E),
            fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(Color(0xFFFFF1F2))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            Text(message, fontSize = 11.sp, color = Color(0xFF9F1239), fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold)
        }
    }
}
