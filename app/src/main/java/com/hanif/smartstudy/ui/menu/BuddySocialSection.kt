package com.hanif.smartstudy.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.BuddyKnock
import com.hanif.smartstudy.data.model.BuddyState
import com.hanif.smartstudy.data.model.KnockKind
import com.hanif.smartstudy.data.model.GoalMetric
import com.hanif.smartstudy.data.model.GoalMode
import com.hanif.smartstudy.data.model.firebaseKey
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.BuddyViewModel

/**
 * ── Study Buddy Phase B1 ──
 *  🔥 Buddy Streak (দুজনেই পড়েছে এমন টানা দিন + "at risk" সতর্কতা)
 *  👋 Study Action Knock (Knock / ⏱ Focus করবি? / ❓ নির্দিষ্ট প্রশ্নের কৌশল জানতে চাই)
 *  আসা knock-এ [চল] [পরে] অথবা কৌশল লিখে উত্তর; বন্ধুর উত্তর নিচে দেখা যায়।
 */
@Composable
fun BuddySocialSection(vm: BuddyViewModel, state: BuddyState) {
    val buddyName = state.buddy?.buddyName ?: "Buddy"
    var showAsk by remember { mutableStateOf(false) }
    var askText by remember { mutableStateOf("") }
    val orange = Color(0xFFF97316)

    // ── আসা knock-গুলো ──
    state.knocks.forEach { k -> IncomingKnockCard(k, vm) }

    // ── Phase B2: দুজনের আজকের পড়া + Routine তুলনা ──
    LaunchedEffect(Unit) { vm.refreshMyProgress() }   // goal/streak ডেটা সতেজ করা
    BuddyFocusCard(state, buddyName, vm)
    BuddyCompareCard(state, buddyName)
    BuddyGoalCard(state, buddyName, vm)

    // ── Buddy Streak ──
    Card(shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, orange.copy(alpha = 0.35f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔥 Buddy Streak: ${state.buddyStreak} দিন", fontFamily = NotoSansBengali,
                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = orange)
            Text(
                if (state.buddyStreak == 0) "দুজনেই একই দিনে অন্তত কিছুটা পড়লে Streak শুরু হবে।"
                else "গত ${state.buddyStreak} দিন তোমরা দুজনেই পড়েছো — চালিয়ে যাও!",
                fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.65f)
            )
            state.streakRisk?.let { risk ->
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF3E0)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("⚠️ Streak at risk — $risk", fontFamily = NotoSansBengali, fontSize = 12.sp,
                            color = Color(0xFFE65100), modifier = Modifier.weight(1f))
                        if (risk.startsWith(buddyName)) {
                            TextButton(onClick = { vm.sendKnock(KnockKind.KNOCK, "আজকের Streak বাঁচাতে একটু পড়ি?") }) {
                                Text("Knock", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Knock পাঠাও ──
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("👋 $buddyName-কে Knock করো", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { vm.sendKnock(KnockKind.KNOCK, "চলো আজ একটু পড়ি!") },
                    label = { Text("👋 Knock", fontFamily = NotoSansBengali) })
                AssistChip(onClick = { vm.sendKnock(KnockKind.FOCUS, "আজ ২৫ মিনিট একসাথে Focus করবি?") },
                    label = { Text("⏱ Focus করবি?", fontFamily = NotoSansBengali) })
                AssistChip(onClick = { showAsk = true },
                    label = { Text("❓ প্রশ্নের কৌশল", fontFamily = NotoSansBengali) })
            }
            Text("এটা সাধারণ চ্যাট নয় — ছোট্ট Study Action। বন্ধু [চল]/[পরে] বা কৌশল লিখে উত্তর দিতে পারবে।",
                fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }

    // ── বন্ধুর উত্তর ──
    state.sentKnocks.take(3).forEach { k ->
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
            Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val head = when (k.status) {
                    "ACCEPTED" -> "✅ $buddyName রাজি: ${k.message}"
                    "LATER"    -> "🕒 $buddyName পরে করবে: ${k.message}"
                    else       -> "💡 $buddyName-এর কৌশল (\"${k.message.take(60)}\")"
                }
                Text(head, fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (k.status == "REPLIED" && k.reply.isNotBlank()) {
                    Text(k.reply, fontFamily = NotoSansBengali, fontSize = 13.sp)
                }
            }
        }
    }

    // ── Phase B2: বন্ধু কী দেখতে পাবে ──
    BuddyPrivacyCard(state, vm)

    if (showAsk) {
        AlertDialog(
            onDismissRequest = { showAsk = false },
            title = { Text("❓ কোন প্রশ্নের কৌশল জানতে চাও?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                OutlinedTextField(askText, { askText = it }, minLines = 2, maxLines = 5,
                    placeholder = { Text("প্রশ্নটা লেখো, যেমন: \"বিপরীত শব্দ মনে রাখার ট্রিক কী?\"", fontFamily = NotoSansBengali, fontSize = 12.sp) })
            },
            confirmButton = {
                TextButton(enabled = askText.isNotBlank(), onClick = {
                    vm.sendKnock(KnockKind.ASK, askText.trim()); askText = ""; showAsk = false
                }) { Text("পাঠাও", fontFamily = NotoSansBengali) }
            },
            dismissButton = { TextButton(onClick = { showAsk = false }) { Text("বাতিল", fontFamily = NotoSansBengali) } }
        )
    }
}

@Composable
private fun IncomingKnockCard(k: BuddyKnock, vm: BuddyViewModel) {
    var reply by remember(k.id) { mutableStateOf("") }
    val title = when (k.kind) {
        KnockKind.FOCUS -> "⏱ ${k.fromName}: একসাথে Focus?"
        KnockKind.ASK   -> "❓ ${k.fromName} একটা কৌশল জানতে চাইছে"
        else            -> "👋 ${k.fromName} knock করেছে!"
    }
    Card(shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(k.message, fontFamily = NotoSansBengali, fontSize = 13.sp)
            if (k.kind == KnockKind.ASK) {
                OutlinedTextField(reply, { reply = it }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("তোমার কৌশল/ব্যাখ্যা লেখো…", fontFamily = NotoSansBengali, fontSize = 12.sp) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.respondKnock(k, "LATER") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)) { Text("পরে", fontFamily = NotoSansBengali) }
                if (k.kind == KnockKind.ASK) {
                    Button(onClick = { vm.respondKnock(k, "REPLIED", reply.trim()) }, enabled = reply.isNotBlank(),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("উত্তর পাঠাও", fontFamily = NotoSansBengali)
                    }
                } else {
                    Button(onClick = {
                        vm.respondKnock(k, "ACCEPTED")
                        if (k.kind == KnockKind.FOCUS) vm.startOrJoinFocus()   // B4: shared session
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("চল", fontFamily = NotoSansBengali)
                    }
                }
            }
        }
    }
}

private fun fmtMin(m: Int): String = when {
    m < 0 -> "🔒"
    m >= 60 -> "${m / 60}h ${m % 60}m"
    else -> "${m}m"
}

@Composable
private fun CompareRow(label: String, value: String, frac: Float?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp)
            Text(value, fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        if (frac != null) {
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = color, trackColor = color.copy(0.15f)
            )
        }
    }
}

/** ⏱ আজকের পড়ার সময় ও 📚 Routine — দুজনের পাশাপাশি; "আর ২০ মিনিট! শেষ করি?" */
@Composable
private fun BuddyCompareCard(state: BuddyState, buddyName: String) {
    val me = state.myProgress
    val bd = state.buddyProgress
    val myColor = MaterialTheme.colorScheme.primary
    val bdColor = Color(0xFFFF9800)
    fun frac(done: Int, goal: Int): Float? = if (done < 0 || goal <= 0) null else done.toFloat() / goal
    fun routine(d: Int, t: Int): String = when {
        d < 0 || t < 0 -> "🔒"
        t == 0 -> "—"
        else -> "$d/$t"
    }
    fun routineFrac(d: Int, t: Int): Float? = if (d < 0 || t <= 0) null else d.toFloat() / t

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("⏱ আজকের পড়া", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            CompareRow("তুমি", "${fmtMin(me.doneMinutes)} / ${fmtMin(me.goalMinutes)}", frac(me.doneMinutes, me.goalMinutes), myColor)
            CompareRow(buddyName, "${fmtMin(bd.doneMinutes)} / ${fmtMin(bd.goalMinutes)}", frac(bd.doneMinutes, bd.goalMinutes), bdColor)
            val left = me.goalMinutes - me.doneMinutes
            if (left in 1..60) {
                Text("💪 আর $left মিনিট! শেষ করি?", fontFamily = NotoSansBengali, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, color = myColor)
            }
            if (me.routineTotal > 0 || bd.routineTotal != 0) {
                HorizontalDivider()
                Text("📚 আজকের Routine", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                CompareRow("তুমি", routine(me.routineDone, me.routineTotal), routineFrac(me.routineDone, me.routineTotal), myColor)
                CompareRow(buddyName, routine(bd.routineDone, bd.routineTotal), routineFrac(bd.routineDone, bd.routineTotal), bdColor)
            }
            if (me.quizAcc >= 0 || bd.quizAcc >= 0) {
                Text("🎯 Quiz সঠিক: তুমি ${if (me.quizAcc >= 0) "${me.quizAcc}%" else "—"} · $buddyName ${if (bd.quizAcc >= 0) "${bd.quizAcc}%" else "🔒"}",
                    fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
            }
            if (bd.streak >= 0) {
                Text("🔥 $buddyName-এর নিজের streak: ${bd.streak} দিন", fontFamily = NotoSansBengali, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

/** 🔒 বন্ধু কী দেখতে পাবে — ইউজার নিজেই নিয়ন্ত্রণ করে; বন্ধ করলে ডেটা Firebase-এ যায়ই না */
@Composable
private fun BuddyPrivacyCard(state: BuddyState, vm: BuddyViewModel) {
    val sh = state.share
    @Composable
    fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
    Card(shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🔒 বন্ধু কী দেখতে পাবে", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            SwitchRow("পড়ার সময়", sh.studyTime) { vm.setShare(sh.copy(studyTime = it)) }
            SwitchRow("প্রোগ্রেস %", sh.progress) { vm.setShare(sh.copy(progress = it)) }
            SwitchRow("নিজের Streak", sh.streak) { vm.setShare(sh.copy(streak = it)) }
            SwitchRow("Routine সম্পন্ন", sh.routine) { vm.setShare(sh.copy(routine = it)) }
            SwitchRow("Quiz স্কোর (সঠিক %)", sh.quizScore) { vm.setShare(sh.copy(quizScore = it)) }
            Text("কখনোই শেয়ার হয় না: ভুল প্রশ্ন, ব্যক্তিগত নোট, Study কনটেন্ট, ব্যক্তিগত তথ্য।",
                fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
        }
    }
}

/** 🏠 Home card — "Rahim আজ 42 মিনিট পড়েছে · 🔥 3 day streak" + [Knock]; বন্ধু না থাকলে কিছুই দেখায় না */
@Composable
fun BuddyHomeCard(onOpen: () -> Unit, vm: BuddyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state by vm.state.collectAsState()
    if (!state.hasBuddy) return
    val name = state.buddy?.buddyName ?: "Buddy"
    val bd = state.buddyProgress
    val teal = Color(0xFF0D9488)
    Surface(
        shape = RoundedCornerShape(16.dp), color = teal.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, teal.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🤝 Study Buddy", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = teal)
            Text(
                if (bd.doneMinutes >= 0) "$name আজ ${fmtMin(bd.doneMinutes)} পড়েছে" else "$name",
                fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            val extra = buildList {
                if (state.buddyStreak > 0) add("🔥 Buddy Streak ${state.buddyStreak} দিন")
                state.streakRisk?.let { add("⚠️ $it") }
                if (state.knocks.isNotEmpty()) add("👋 ${state.knocks.size}টি knock অপেক্ষায়")
            }
            if (extra.isNotEmpty()) {
                Text(extra.joinToString(" · "), fontFamily = NotoSansBengali, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.sendKnock(KnockKind.KNOCK, "চলো আজ একটু পড়ি!") },
                    colors = ButtonDefaults.buttonColors(containerColor = teal),
                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Text("👋 Knock পাঠান", fontFamily = NotoSansBengali, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onOpen, shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Text("খুলুন", fontFamily = NotoSansBengali, fontSize = 12.sp)
                }
            }
        }
    }
}

/** 🎯 Shared Goal — 🤝 Team (দুজনের যোগফল) অথবা ⚔️ Challenge (কে আগে); কোনো toxic leaderboard নয় */
@Composable
private fun BuddyGoalCard(state: BuddyState, buddyName: String, vm: BuddyViewModel) {
    val gs = state.goalState
    val g = gs.goal
    var showCreate by remember { mutableStateOf(false) }
    val purple = Color(0xFF7C3AED)
    val myColor = MaterialTheme.colorScheme.primary
    val bdColor = Color(0xFFFF9800)

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, purple.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (g == null) {
                Text("🎯 আমাদের লক্ষ্য", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = purple)
                Text("দুজন মিলে একটা লক্ষ্য ঠিক করো — যেমন ৩০ দিনে ১০০০ প্রশ্ন, অথবা \"কে আগে ২০ MCQ শেষ করে\"।",
                    fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.65f))
                Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(10.dp)) { Text("➕ লক্ষ্য তৈরি করো", fontFamily = NotoSansBengali) }
            } else {
                val race = g.mode == GoalMode.RACE
                Text((if (race) "⚔️ " else "🤝 ") + g.title, fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = purple)
                Text(
                    (if (race) "Challenge: প্রত্যেকে ${g.target} ${g.unit}" else "Team: দুজনে মিলে ${g.target} ${g.unit}") +
                        " · " + (if (g.expired()) "সময় শেষ" else "${g.daysLeft()} দিন বাকি"),
                    fontFamily = NotoSansBengali, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
                if (race) {
                    CompareRow("তুমি", "${gs.myValue}/${g.target}", gs.myValue.toFloat() / g.target, myColor)
                    CompareRow(buddyName, "${gs.buddyValue}/${g.target}", gs.buddyValue.toFloat() / g.target, bdColor)
                    val msg = when {
                        gs.myReachedAt > 0L && gs.buddyReachedAt > 0L -> "🎉 দুজনেই challenge complete করেছো!" +
                            (if (gs.myReachedAt <= gs.buddyReachedAt) " এই রাউন্ডে তুমি আগে ছিলে 🏁" else " এই রাউন্ডে $buddyName আগে ছিল 🏁")
                        gs.myReachedAt > 0L -> "🏁 তুমি আগে শেষ করেছো! $buddyName-কে একটা Knock দাও?"
                        gs.buddyReachedAt > 0L -> "💪 $buddyName শেষ করে ফেলেছে — তুমিও পারবে, আর ${g.target - gs.myValue} ${g.unit}!"
                        else -> null
                    }
                    msg?.let { Text(it, fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = purple) }
                } else {
                    val total = gs.myValue + gs.buddyValue
                    CompareRow("সম্মিলিত", "$total/${g.target}", total.toFloat() / g.target, purple)
                    Text("তুমি ${gs.myValue} · $buddyName ${gs.buddyValue} ${g.unit}", fontFamily = NotoSansBengali, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    if (total >= g.target) {
                        Text("🎉 লক্ষ্য পূরণ — দারুণ টিমওয়ার্ক!", fontFamily = NotoSansBengali, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = purple)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCreate = true }, shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("নতুন লক্ষ্য", fontFamily = NotoSansBengali, fontSize = 12.sp)
                    }
                    TextButton(onClick = { vm.clearGoal() }) {
                        Text("শেষ করো", fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (showCreate) CreateGoalDialog(g != null, onDismiss = { showCreate = false }) { t, m, n, mode, d ->
        vm.createGoal(t, m, n, mode, d); showCreate = false
    }
}

@Composable
private fun CreateGoalDialog(replacing: Boolean, onDismiss: () -> Unit, onCreate: (String, String, Int, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf(GoalMetric.QUESTIONS) }
    var target by remember { mutableStateOf("100") }
    var mode by remember { mutableStateOf(GoalMode.TEAM) }
    var days by remember { mutableStateOf(7) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎯 নতুন লক্ষ্য", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (replacing) Text("⚠️ আগের লক্ষ্য ও প্রোগ্রেস মুছে যাবে।", fontFamily = NotoSansBengali, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error)
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("নাম (যেমন: BCS Model Test)", fontFamily = NotoSansBengali, fontSize = 12.sp) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(mode == GoalMode.TEAM, { mode = GoalMode.TEAM }, label = { Text("🤝 Team", fontFamily = NotoSansBengali) })
                    FilterChip(mode == GoalMode.RACE, { mode = GoalMode.RACE }, label = { Text("⚔️ Challenge", fontFamily = NotoSansBengali) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(metric == GoalMetric.QUESTIONS, { metric = GoalMetric.QUESTIONS }, label = { Text("প্রশ্ন", fontFamily = NotoSansBengali) })
                    FilterChip(metric == GoalMetric.MINUTES, { metric = GoalMetric.MINUTES }, label = { Text("মিনিট", fontFamily = NotoSansBengali) })
                }
                OutlinedTextField(target, { target = it.filter { c -> c.isDigit() }.take(6) }, singleLine = true,
                    label = { Text(if (mode == GoalMode.TEAM) "মোট লক্ষ্য (দুজনের যোগফল)" else "প্রত্যেকের লক্ষ্য", fontFamily = NotoSansBengali, fontSize = 12.sp) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 14, 30).forEach { d ->
                        FilterChip(days == d, { days = d }, label = { Text("$d দিন", fontFamily = NotoSansBengali) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = (target.toIntOrNull() ?: 0) > 0, onClick = {
                onCreate(title.trim(), metric, target.toInt(), mode, days)
            }) { Text("তৈরি করো", fontFamily = NotoSansBengali) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
    )
}

/** ⏱ Study Together — একসাথে Focus: দুজনের স্ট্যাটাস + একই টাইমার; কেউ বেরোলে জানানো হয় */
@Composable
private fun BuddyFocusCard(state: BuddyState, buddyName: String, vm: BuddyViewModel) {
    val f = state.focus
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(f?.startMs) {
        while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }
    val active = f != null && f.active(now)
    val green = Color(0xFF059669)
    val buddyKey = state.buddy?.buddyPhone?.firebaseKey() ?: ""
    val buddyMember = f?.members?.firstOrNull { it.phoneKey == buddyKey }
    val me = f?.members?.firstOrNull { it.phoneKey != buddyKey }
    val iAmIn = active && me?.inSession == true
    val buddyIn = active && buddyMember?.inSession == true
    val buddyLeft = active && buddyMember != null && buddyMember.leftAt > 0L

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, green.copy(alpha = 0.35f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⏱ Study Together", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = green)
            when {
                iAmIn -> {
                    val remain = ((f!!.endMs() - now) / 1000).coerceAtLeast(0)
                    Text("%d:%02d".format(remain / 60, remain % 60), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = green)
                    Text("🟢 তুমি পড়ছো", fontFamily = NotoSansBengali, fontSize = 13.sp)
                    Text(
                        when {
                            buddyIn   -> "🟢 $buddyName পড়ছে"
                            buddyLeft -> "⚪ $buddyName left the session"
                            else      -> "⚪ $buddyName এখনো Join করেনি"
                        },
                        fontFamily = NotoSansBengali, fontSize = 13.sp
                    )
                    OutlinedButton(onClick = { vm.leaveFocus() }, shape = RoundedCornerShape(10.dp)) {
                        Text("সেশন থেকে বেরিয়ে যাও", fontFamily = NotoSansBengali, fontSize = 12.sp)
                    }
                }
                buddyIn -> {
                    Text("🟢 $buddyName এখন Focus করছে — তুমিও Join করো!", fontFamily = NotoSansBengali, fontSize = 13.sp)
                    Button(onClick = { vm.startOrJoinFocus() }, colors = ButtonDefaults.buttonColors(containerColor = green),
                        shape = RoundedCornerShape(10.dp)) { Text("Join করো", fontFamily = NotoSansBengali) }
                }
                else -> {
                    Text("দুজন একসাথে ২৫ মিনিটের Focus সেশন চালাও — একই টাইমার, একে অপরের স্ট্যাটাস দেখা যাবে।",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.65f))
                    Button(onClick = { vm.startOrJoinFocus() }, colors = ButtonDefaults.buttonColors(containerColor = green),
                        shape = RoundedCornerShape(10.dp)) { Text("🟢 একসাথে Focus শুরু", fontFamily = NotoSansBengali) }
                }
            }
        }
    }
}
