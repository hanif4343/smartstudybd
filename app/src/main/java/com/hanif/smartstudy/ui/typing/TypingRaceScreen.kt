package com.hanif.smartstudy.ui.typing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.RaceInvite
import com.hanif.smartstudy.data.model.RaceStatus
import com.hanif.smartstudy.data.model.TypingRace
import com.hanif.smartstudy.data.repository.TypingRaceRepository
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.launch

/**
 * 🏁 Typing Race — বন্ধুর সাথে সরাসরি প্রতিযোগিতা।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingRaceScreen(onBack: () -> Unit) {
    val ctx     = LocalContext.current
    val scope   = rememberCoroutineScope()
    val session = remember { SessionManager(ctx) }
    val repo    = remember { TypingRaceRepository() }
    val me      = remember { session.getCurrentUser() }
    val myPhone = me?.phone ?: ""
    val myName  = me?.displayName() ?: "আমি"

    var raceId          by remember { mutableStateOf<String?>(null) }
    var race             by remember { mutableStateOf<TypingRace?>(null) }
    var pendingInvites   by remember { mutableStateOf<List<RaceInvite>>(emptyList()) }
    var inviteePhone     by remember { mutableStateOf("") }
    var raceLanguage     by remember { mutableStateOf("bn") }
    var creating         by remember { mutableStateOf(false) }
    var createError      by remember { mutableStateOf<String?>(null) }
    var userInput        by remember { mutableStateOf("") }
    var startedAtLocal   by remember { mutableStateOf(0L) }
    var lastProgressPush by remember { mutableStateOf(0L) }
    var hasFinished       by remember { mutableStateOf(false) }

    // ── আমার পেন্ডিং ইনভাইট শোনা ──
    LaunchedEffect(myPhone) {
        if (myPhone.isBlank()) return@LaunchedEffect
        repo.observeMyRaceInvites(myPhone).collect { pendingInvites = it }
    }

    // ── সিলেক্ট করা race-টা realtime শোনা ──
    LaunchedEffect(raceId) {
        val id = raceId
        userInput = ""; startedAtLocal = 0L; hasFinished = false
        if (id == null) { race = null; return@LaunchedEffect }
        repo.observeRace(id).collect { race = it }
    }

    // ── ACTIVE হওয়ার সাথে সাথে local timer শুরু ──
    LaunchedEffect(race?.status) {
        if (race?.getStatus() == RaceStatus.ACTIVE && startedAtLocal == 0L) {
            startedAtLocal = System.currentTimeMillis()
        }
    }

    // ── টাইপ করার সাথে সাথে throttled progress push + সম্পূর্ণ হলে finish ──
    LaunchedEffect(userInput) {
        val r = race ?: return@LaunchedEffect
        if (r.getStatus() != RaceStatus.ACTIVE || hasFinished || startedAtLocal == 0L) return@LaunchedEffect
        val passage = r.passage
        val correct = userInput.indices.count { it < passage.length && userInput[it] == passage[it] }
        val timeSec = ((System.currentTimeMillis() - startedAtLocal) / 1000).coerceAtLeast(1)
        val minutes = timeSec / 60.0
        val wpm     = if (minutes > 0) (correct / 5.0 / minutes).toInt().coerceAtLeast(0) else 0

        if (userInput.length >= passage.length) {
            hasFinished = true
            val acc = if (userInput.isNotEmpty()) (correct * 100 / userInput.length) else 100
            scope.launch { repo.finishRace(r.id, myPhone, wpm, acc) }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastProgressPush > 1200) {
                lastProgressPush = now
                scope.launch { repo.updateRaceProgress(r.id, myPhone, userInput.length, wpm) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏁 টাইপিং রেস", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (raceId != null) raceId = null else onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (myPhone.isBlank()) {
                Text("রেসে অংশ নিতে আগে লগইন করো।", fontFamily = NotoSansBengali)
                return@Column
            }

            val r = race
            when {
                raceId == null -> {
                    // ── পেন্ডিং ইনভাইট ──
                    if (pendingInvites.isNotEmpty()) {
                        Text("📩 তোমার আমন্ত্রণ", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                        pendingInvites.forEach { inv ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${inv.creatorName} তোমাকে রেসে আমন্ত্রণ জানিয়েছে", fontFamily = NotoSansBengali, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = {
                                            scope.launch {
                                                repo.respondToRace(inv.raceId, myPhone, true)
                                                repo.deleteInvite(myPhone, inv.raceId)
                                                raceId = inv.raceId
                                            }
                                        }) { Text("✅ Accept", fontFamily = NotoSansBengali) }
                                        OutlinedButton(onClick = {
                                            scope.launch {
                                                repo.respondToRace(inv.raceId, myPhone, false)
                                                repo.deleteInvite(myPhone, inv.raceId)
                                            }
                                        }) { Text("❌ Decline", fontFamily = NotoSansBengali) }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }

                    // ── নতুন রেস তৈরি ──
                    Text("🆚 নতুন রেস শুরু করো", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("bn" to "বাংলা", "en" to "English").forEach { (code, label) ->
                            OutlinedButton(
                                onClick = { raceLanguage = code },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (raceLanguage == code) Color(0xFFEEF2FF) else Color.Transparent
                                )
                            ) { Text(label, fontFamily = NotoSansBengali) }
                        }
                    }
                    OutlinedTextField(
                        value = inviteePhone, onValueChange = { inviteePhone = it; createError = null },
                        label = { Text("বন্ধুর ফোন নম্বর", fontFamily = NotoSansBengali) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    createError?.let { Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, fontFamily = NotoSansBengali) }
                    Button(
                        onClick = {
                            if (inviteePhone.isBlank()) { createError = "ফোন নম্বর দাও"; return@Button }
                            creating = true; createError = null
                            scope.launch {
                                val invitee = com.hanif.smartstudy.data.remote.UserSyncService.fetchUser(inviteePhone.trim())
                                if (invitee?.phone.isNullOrBlank()) {
                                    createError = "এই নম্বরে কোনো ইউজার পাওয়া যায়নি"
                                    creating = false
                                    return@launch
                                }
                                val passage = fallbackPassageFor(raceLanguage)
                                val id = repo.createRace(
                                    creatorPhone = myPhone, creatorName = myName,
                                    inviteePhone = invitee!!.phone!!, inviteeName = invitee.displayName(),
                                    passage = passage, language = raceLanguage
                                )
                                creating = false
                                if (id != null) raceId = id else createError = "রেস তৈরি করা যায়নি, আবার চেষ্টা করো"
                            }
                        },
                        enabled = !creating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (creating) "তৈরি হচ্ছে..." else "🏁 আমন্ত্রণ পাঠাও", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                }

                r == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                else -> when (r.getStatus()) {
                    RaceStatus.PENDING, RaceStatus.WAITING -> RaceWaitingRoom(r, myPhone, onStart = {
                        scope.launch { repo.startRace(r.id) }
                    })
                    RaceStatus.ACTIVE -> RaceLiveArea(r, myPhone, userInput) { userInput = it }
                    RaceStatus.FINISHED -> RaceResult(r, myPhone, onExit = { raceId = null })
                    RaceStatus.CANCELLED -> Text("এই রেসটা বাতিল হয়ে গেছে।", fontFamily = NotoSansBengali)
                }
            }
        }
    }
}

@Composable
private fun RaceWaitingRoom(r: TypingRace, myPhone: String, onStart: () -> Unit) {
    val isCreator = r.creatorPhone == myPhone
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("⏳ অপেক্ষা করা হচ্ছে...", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
        r.participants.values.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (p.status == "ACCEPTED") "✅" else "⌛", fontSize = 16.sp)
                Text(p.name, fontFamily = NotoSansBengali, fontSize = 14.sp)
                Text("(${p.status})", fontSize = 11.sp, color = Color.Gray)
            }
        }
        if (isCreator && r.allAccepted()) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("🏁 রেস শুরু করো", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        } else if (isCreator) {
            Text("বন্ধু accept করার অপেক্ষায়...", fontSize = 12.sp, fontFamily = NotoSansBengali, color = Color.Gray)
        } else {
            Text("Creator শুরু করলেই রেস চালু হয়ে যাবে।", fontSize = 12.sp, fontFamily = NotoSansBengali, color = Color.Gray)
        }
    }
}

@Composable
private fun RaceLiveArea(r: TypingRace, myPhone: String, userInput: String, onInputChange: (String) -> Unit) {
    val passage = r.passage
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        r.participants.values.forEach { p ->
            val isMe = p.phone == myPhone
            val frac = if (passage.isNotEmpty()) (p.progress.toFloat() / passage.length).coerceIn(0f, 1f) else 0f
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((if (isMe) "🧑 তুমি" else "👤 ${p.name}"), fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    Text("${p.liveWpm} WPM" + if (p.status == "FINISHED") " ✅" else "", fontSize = 12.sp, fontFamily = NotoSansBengali)
                }
                LinearProgressIndicator(
                    progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (isMe) Color(0xFF6366F1) else Color(0xFFF59E0B)
                )
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text(passage, Modifier.padding(16.dp), fontSize = 15.sp, fontFamily = NotoSansBengali, lineHeight = 24.sp)
        }

        OutlinedTextField(
            value = userInput, onValueChange = { onInputChange(it.take(passage.length)) },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            placeholder = { Text("এখানে টাইপ করো...", fontFamily = NotoSansBengali) }
        )
    }
}

@Composable
private fun RaceResult(r: TypingRace, myPhone: String, onExit: () -> Unit) {
    val winner = r.winner()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val iWon = winner?.phone == myPhone
        Text(if (iWon) "🏆 তুমি জিতেছ!" else "🥈 এবার হলো না", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
        r.participants.values.sortedByDescending { it.finalWpm }.forEach { p ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (p.phone == winner?.phone) Color(0xFFECFDF5) else Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((if (p.phone == winner?.phone) "🏆 " else "") + p.name, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    Text("${p.finalWpm} WPM  •  ${p.accuracy}%", fontFamily = NotoSansBengali)
                }
            }
        }
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("🔙 ফিরে যাও", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
        }
    }
}
