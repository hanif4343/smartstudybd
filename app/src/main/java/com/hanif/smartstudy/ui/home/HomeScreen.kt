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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import com.hanif.smartstudy.data.model.*
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

private val PrimaryIndigo = Color(0xFF4F46E5)
private val DeepIndigo    = Color(0xFF1E1B4B)
private val Amber         = Color(0xFFF59E0B)
private val GreenMint     = Color(0xFF10B981)
private val CardBg        = Color(0xFFFFFFFF)
private val SlateBg       = Color(0xFFF8FAFC)
private val TextPrimary   = Color(0xFF1E293B)
private val TextMuted     = Color(0xFF64748B)

// ═══════════════════════════════════════════════════════════
// Exam Eve Notification — পরীক্ষার আগের রাত ১০টায় রিমাইন্ডার
// ═══════════════════════════════════════════════════════════
object ExamNotificationHelper {

    private const val EXAM_NOTIF_REQ = 2025

    // পরীক্ষার তারিখের আগের রাত ২২:০০ তে alarm set করে
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

            // আগের সময় চলে গেলে set করার দরকার নেই
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
    viewModel     : HomeViewModel = viewModel(),
    quizViewModel : QuizViewModel? = null,
    onSearchClick : () -> Unit = {},
    onTypingClick : () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val ctx   = LocalContext.current

    // XP ও user data — যেকোনো screen থেকে ফিরলে auto-refresh
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Wrong questions থেকে review data — mutableStateOf দিয়ে refresh করা যাবে
    var wrongItems by remember { mutableStateOf(quizViewModel?.getWrongQuestions() ?: emptyList<Pair<QuestionItem, Int>>()) }
    LaunchedEffect(state) {
        wrongItems = quizViewModel?.getWrongQuestions() ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
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

            // ── AD SLOT 1: Banner — Hero 아래, 콘텐츠 시작 전 ──
            // AdMob policy: ✅ content와 content 사이, 자연스러운 위치
            AdBannerPlaceholder()

            // Quick Stats
            QuickStatsRow(stats = state.studyStats)

            // ── AD SLOT 2: Native In-Feed — stats 아래, streak 전 ──
            // AdMob policy: ✅ native/in-feed는 content와 구분되게
            AdNativePlaceholder()

            // Daily Goal
            DailyGoalCard(goal = state.goalProgress, onSetGoal = { viewModel.setDailyGoal(it) })

            // Weekly Streak
            WeeklyStreakCard(streak = state.streakInfo)

            // ── Wrong Question Review Section ──
            if (wrongItems.isNotEmpty()) {
                WrongReviewSection(
                    wrongItems      = wrongItems,
                    onAnswerMcq     = { qId, opt -> quizViewModel?.answerMcq(
                        wrongItems.indexOfFirst { it.first.id == qId }, opt) },
                    onAnswerWritten = { qId, text -> quizViewModel?.answerWritten(
                        wrongItems.indexOfFirst { it.first.id == qId }, text) ?: 0 },
                    onRemoveCorrect = { qId -> quizViewModel?.removeWrongQId(qId) }
                )
            }

            // Exam Countdown — শেষ হলে auto-hide, না থাকলে set বাটন
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
                        // পরীক্ষার আগের রাতে নোটিফিকেশন schedule করো
                        ExamNotificationHelper.scheduleExamEveReminder(ctx, d, n)
                    })
                // expired → কিছুই দেখাবে না (auto-hide ✅)
            }

            // Loading / Error
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
    // Native Ad — Banner দিয়ে replace করা হচ্ছে (Native ad XML layout ছাড়া কাজ করে না)
    // একটু বেশি জায়গায় banner দেখাও
    com.hanif.smartstudy.ui.ads.AdBannerView(
        adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_HOME
    )
}

// ═══════════════════════════════════════════════════════════
// WRONG QUESTION REVIEW CARD
// ═══════════════════════════════════════════════════════════

@Composable
fun WrongReviewCard(
    wrongItems : List<Pair<QuestionItem, Int>>,
    onStart    : () -> Unit
) {
    val wrongCount   = wrongItems.size
    val previewItems = wrongItems.take(2)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        border    = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFFF97316)))),
                        contentAlignment = Alignment.Center
                    ) { Text("🔁", fontSize = 18.sp) }
                    Column {
                        Text("ভুল প্রশ্ন Review", fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626),
                            fontFamily = NotoSansBengali)
                        Text("আবার চেষ্টা করো", fontSize = 10.sp,
                            color = TextMuted, fontFamily = NotoSansBengali)
                    }
                }
                // Stats pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatPill(wrongCount.toString(), "ভুল", Color(0xFFDC2626), Color(0xFFFEF2F2))
                }
            }

            // Preview questions (২টা)
            previewItems.forEach { (q, count) ->
                WrongQuestionPreview(q = q, wrongCount = count)
            }

            // CTA button
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Brush.horizontalGradient(
                        listOf(Color(0xFFDC2626), Color(0xFFEF4444))
                    ).let { Color(0xFFDC2626) }
                )
            ) {
                Text("🎯 ", fontSize = 14.sp)
                Text(
                    if (wrongCount > 2) "সব ${wrongCount}টি প্রশ্ন অনুশীলন করুন"
                    else "${wrongCount}টি ভুল প্রশ্ন অনুশীলন করুন",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.25f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("$wrongCount", fontSize = 11.sp,
                        color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun WrongQuestionPreview(q: QuestionItem, wrongCount: Int) {
    val badgeColor = when {
        wrongCount >= 3 -> Color(0xFFDC2626)
        wrongCount == 2 -> Color(0xFFF97316)
        else            -> Color(0xFFF59E0B)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFF1F2))
            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFEF2F2)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (q.optionA.isNotBlank()) "🔘" else "✍️", fontSize = 14.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                q.question.replace(Regex("<[^>]+>"), "").take(55) +
                    if (q.question.length > 55) "…" else "",
                fontSize   = 12.sp, fontWeight = FontWeight.Bold,
                color      = TextPrimary, fontFamily = NotoSansBengali, lineHeight = 16.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE0F2FE))
                    .padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text(q.subTopic.take(20), fontSize = 9.sp, color = Color(0xFF0284C7),
                        fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                }
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(badgeColor)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text("×$wrongCount", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun StatPill(value: String, label: String, textColor: Color, bgColor: Color) {
    Column(
        modifier            = Modifier.clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            color = textColor, fontFamily = NotoSansBengali, lineHeight = 15.sp)
        Text(label, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold,
            fontFamily = NotoSansBengali)
    }
}

// ═══════════════════════════════════════════════════════════
// EXISTING COMPONENTS (unchanged)
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
fun DailyGoalCard(goal: GoalProgress, onSetGoal: (Int) -> Unit) {
    var showGoalDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(CardBg), CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { goal.progressPct / 100f }, modifier = Modifier.size(60.dp),
                    color = PrimaryIndigo, strokeWidth = 6.dp, trackColor = Color(0xFFE2E8F0))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${goal.doneMinutes}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontFamily = NotoSansBengali)
                    Text("মি", fontSize = 7.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("📚 দৈনিক লক্ষ্য", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontFamily = NotoSansBengali)
                Text("${goal.doneMinutes} / ${goal.goalMinutes} মিনিট", fontSize = 11.sp, color = TextMuted, fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 3.dp))
                if (goal.progressPct >= 100f) Text("🎉 লক্ষ্য পূরণ হয়েছে!", fontSize = 10.sp, color = GreenMint, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 3.dp))
            }
            IconButton(onClick = { showGoalDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(16.dp)) }
        }
    }
    if (showGoalDialog) GoalSetDialog(goal.goalMinutes, { onSetGoal(it); showGoalDialog = false }, { showGoalDialog = false })
}

@Composable
private fun GoalSetDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(10, 20, 30, 45, 60, 90)
    var selected by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("দৈনিক লক্ষ্য নির্ধারণ", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) },
        text = {
            Column {
                Text("পড়ার লক্ষ্য বেছে নিন:", fontFamily = NotoSansBengali, fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(bottom = 10.dp))
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
        dismissButton = { TextButton(onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali, color = TextMuted) } })
}

@Composable
fun WeeklyStreakCard(streak: StreakInfo) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(CardBg), CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("সাপ্তাহিক Streak", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontFamily = NotoSansBengali)
                Badge(containerColor = Color(0xFFFFFBEB), contentColor = Amber) {
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
    val bg = when { day.isToday -> Brush.linearGradient(listOf(PrimaryIndigo, Color(0xFF4338CA))); day.isDone -> Brush.linearGradient(listOf(Color(0xFFDCFCE7), Color(0xFFDCFCE7))); else -> Brush.linearGradient(listOf(Color(0xFFF1F5F9), Color(0xFFF1F5F9))) }
    val textColor = when { day.isToday -> Color.White; day.isDone -> Color(0xFF166534); else -> Color(0xFFCBD5E1) }
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
                    // Clear বাটন
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
                Text("কাউন্টডাউন চালু করতে ট্যাপ করুন", fontSize = 10.sp, color = TextMuted, fontFamily = NotoSansBengali)
            }
        }
    }
    if (showDialog) ExamDateDialog({ d, n -> onSet(d, n); showDialog = false }, { showDialog = false })
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
