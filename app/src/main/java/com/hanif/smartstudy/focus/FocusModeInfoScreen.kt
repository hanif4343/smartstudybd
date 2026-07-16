package com.hanif.smartstudy.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
//  FocusModeInfoScreen — Home > "Focus Mode" ট্যাপ করলে যে পেজ খোলে
// ───────────────────────────────────────────────────────────────────
//  ফিচারটা এখন সবার জন্য উন্মুক্ত (আগে Admin-only ছিল)। যেহেতু এটা এখন
//  সাধারণ ইউজাররাও দেখবে, তাই চালু করার আগে ঠিক কী হবে সেটা বিস্তারিত
//  বুঝিয়ে + একটা ওয়ার্নিং দেখিয়ে তারপর "চালু করুন" বাটন দেওয়া হয়েছে,
//  যাতে কেউ না বুঝে ট্যাপ করে বিরক্ত না হয়।
// ═══════════════════════════════════════════════════════════════════

private val FocusPrimary = Color(0xFF4F46E5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeInfoScreen(subjects: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val store   = remember { FocusModeStore(context) }
    val scope   = rememberCoroutineScope()

    val focusState by store.stateFlow.collectAsState(initial = FocusModeState())
    val active = focusState.isEffectivelyActive()

    var showPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 ফোকাস মোড", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── বর্তমান অবস্থা ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = if (active) FocusPrimary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
                ),
                border   = BorderStroke(1.2.dp, FocusPrimary.copy(alpha = if (active) 0.5f else 0.25f))
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("বর্তমান অবস্থা", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                        Text(
                            if (active) "চালু ✓" else "বন্ধ",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                            color = if (active) FocusPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (active) {
                        Text("সাবজেক্ট: ${focusState.subject}", fontSize = 13.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        focusState.autoOffDateMillis()?.let { millis ->
                            Text("অটো বন্ধ হবে: ${dateFmt.format(millis)}", fontSize = 12.sp, fontFamily = NotoSansBengali,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("এখন কোনো সাবজেক্টে ফোকাস মোড চালু নেই।", fontSize = 13.sp, fontFamily = NotoSansBengali,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── এটা কী ──
            Text("এটা কী?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            Text(
                "পরীক্ষা কাছে চলে এলে অনেক সময় কোন সাবজেক্ট পড়বেন বুঝে উঠতে দেরি হয়ে যায়। " +
                "ফোকাস মোড চালু করলে আপনি একটা নির্দিষ্ট সাবজেক্ট আর পরীক্ষার তারিখ বেছে নেন — " +
                "অ্যাপ তখন আপনাকে বারবার মনে করিয়ে দেয় যেন আপনি ওই সাবজেক্টেই মনোযোগ দিয়ে পড়েন।",
                fontSize = 13.sp, fontFamily = NotoSansBengali, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── এটা কীভাবে কাজ করে ──
            Text("এটা কীভাবে কাজ করে?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            FocusStep("1️⃣", "সাবজেক্ট ও তারিখ বেছে নিন", "নিচের \"চালু করুন\" বাটনে ট্যাপ করে একটা সাবজেক্ট আর পরীক্ষার তারিখ বেছে নেবেন।")
            FocusStep("2️⃣", "Study ট্যাবে হাইলাইট থাকবে", "Study ট্যাবের উপরে একটা কার্ডে সবসময় দেখাবে কোন সাবজেক্টে আছেন আর পরীক্ষার কত দিন বাকি।")
            FocusStep("3️⃣", "মনে করিয়ে দেওয়া নোটিফিকেশন", "নির্দিষ্ট বিরতিতে (প্রতি ${FocusModeConfig.REMINDER_INTERVAL_MINUTES} মিনিট পরপর সেট করা হলে) একটা রিমাইন্ডার নোটিফিকেশন পাঠানো হতে পারে যাতে আপনি পড়ায় ফিরে আসেন।")
            FocusStep("4️⃣", "অন্য জায়গায় গেলে মনে করিয়ে দেবে", "ফোকাস মোড চালু অবস্থায় অন্য ট্যাব/মেনুতে গেলে একটা ছোট নাজ (nudge) শীট দেখাতে পারে, যা মনে করিয়ে দেয় ফোকাস সাবজেক্টে ফিরে যেতে।")
            FocusStep("5️⃣", "নিজে থেকেই বন্ধ হয়ে যায়", "পরীক্ষার তারিখ পার হয়ে গেলে, অথবা ভুলে বন্ধ না করলেও সর্বোচ্চ ${FocusModeConfig.MAX_ACTIVE_DAYS} দিন পর, ফোকাস মোড নিজে থেকেই বন্ধ হয়ে যাবে।")
            FocusStep("6️⃣", "যেকোনো সময় বন্ধ করা যায়", "এই পেজে ফিরে এসে বা Study কার্ড থেকে এক ট্যাপেই ফোকাস মোড বন্ধ করে দেওয়া যাবে — কোনো কিছু মুছে যাবে না।")

            // ── ওয়ার্নিং ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                border   = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("চালু করার আগে জেনে নিন", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali, color = Color(0xFF92400E))
                    }
                    Text(
                        "• ফোকাস মোড অ্যাপ ব্লক করে না — আপনি সবসময়ই Quiz/QBank/অন্য সাবজেক্টে যেতে পারবেন, শুধু মনে করিয়ে দেওয়ার নোটিফিকেশন/নাজ দেখাবে।\n" +
                        "• একবারে শুধু একটা সাবজেক্টের জন্যই চালু রাখা যাবে; নতুন সাবজেক্ট বেছে নিলে আগেরটা বদলে যাবে।\n" +
                        "• রিমাইন্ডার নোটিফিকেশন পেতে হলে ফোনের নোটিফিকেশন পারমিশন চালু রাখতে হবে।",
                        fontSize = 12.sp, fontFamily = NotoSansBengali, lineHeight = 18.sp, color = Color(0xFF92400E)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // ── অ্যাকশন বাটন ──
            Button(
                onClick  = { showPicker = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = FocusPrimary)
            ) {
                Text(
                    if (active) "🎯 সাবজেক্ট বদলান / আবার সেট করুন" else "🎯 ফোকাস মোড চালু করুন",
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = NotoSansBengali
                )
            }

            if (focusState.enabled) {
                Button(
                    onClick  = { scope.launch { store.deactivate() } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("🔴 এখনই বন্ধ করো", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, fontFamily = NotoSansBengali)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showPicker) {
        FocusSubjectPickerSheet(
            subjects   = subjects,
            focusState = focusState,
            onActivate = { subject, examDateMillis ->
                scope.launch { store.activate(subject, examDateMillis) }
                showPicker = false
            },
            onDeactivate = {
                scope.launch { store.deactivate() }
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun FocusStep(emoji: String, title: String, desc: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 12.sp, fontFamily = NotoSansBengali, lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
