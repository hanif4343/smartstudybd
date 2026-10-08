package com.hanif.smartstudy.ui.support

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.shared.Indigo600
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.SupportFaq
import com.hanif.smartstudy.viewmodel.SupportViewModel

// ─────────────────────────────────────────────────────────────
//  Support Center — Home-এর "AI Chat" টাইলের জায়গায়।
//  ১) App কীভাবে ব্যবহার করব (ধাপ + স্ক্রিনশট)  ২) Help centre (প্রশ্ন লিখলে AI অটো-উত্তর)
//  ৩) 💡 Feature Suggestion · 🔄 Sync সমস্যা · 📱 App/Account সমস্যা · 📖 FAQ · 📩 Contact Admin
// ─────────────────────────────────────────────────────────────

private data class GuideStep(
    val title: String,
    val body: String,
    /** স্ক্রিনশটের ছবির লিংক (CDN/jsDelivr)। ফাঁকা থাকলে ছবি দেখায় না — লিংক বসালেই দেখাবে। */
    val imageUrl: String = ""
)

private val GUIDE_STEPS = listOf(
    GuideStep("১. Quiz অনুশীলন", "নিচের Quiz ট্যাব → বিষয় → টপিক বাছুন। একটা অপশনে ট্যাপ করলে সঠিক উত্তর ও ব্যাখ্যা দেখা যাবে। ভুল প্রশ্ন নিজে থেকেই Wrong Review-তে জমে।"),
    GuideStep("২. QBank — আগের পরীক্ষার প্রশ্ন", "QBank ট্যাবের উপরের চিপ থেকে বিসিএস / প্রাথমিক / নিবন্ধন / ব্যাংক / ১৬-২০ গ্রেড বাছুন, তারপর কার্ডে ট্যাপ করুন। ১৬-২০ গ্রেডে পদবী, প্রতিষ্ঠান ও সাল দিয়ে ফিল্টার করা যায়।"),
    GuideStep("৩. Study — পড়া ও রিভিশন", "Study ট্যাবে বিষয় ও টপিক খুলে পড়ুন। পড়া শেষ হলে ✓ টিক দিন — প্রশ্ন নিচে চলে যাবে।"),
    GuideStep("৪. বুকমার্ক ও রিপোর্ট", "প্রশ্নের কার্ডে ⭐ ট্যাপ করলে সেভ হয়, 🚩 ট্যাপ করলে ভুল রিপোর্ট করা যায়। সেভ করা প্রশ্ন Menu-তে পাবেন।"),
    GuideStep("৫. AI ব্যাখ্যার জন্য Key", "Menu → সেটিংস-এ গিয়ে Groq / Mistral / Cerebras / Gemini-র যেকোনো একটা AI Key যোগ করুন। তারপর MCQ-তে অপশন বাছলেই AI ব্যাখ্যা আসবে।"),
    GuideStep("৬. Routine ও Focus Mode", "Home → Routine-এ দৈনিক পড়ার পরিকল্পনা করুন; Focus Mode চালু করলে মনোযোগ ধরে রাখতে সাহায্য করবে।")
)

private data class Category(val id: String, val emoji: String, val title: String, val hint: String)

private val CATEGORIES = listOf(
    Category("suggest", "💡", "Feature Suggestion", "নতুন ফিচারের আইডিয়া জানান"),
    Category("sync",    "🔄", "Sync সমস্যা",        "ডেটা/প্রশ্ন আসছে না বা আপডেট হচ্ছে না"),
    Category("account", "📱", "App/Account সমস্যা",  "লগইন, অ্যাপ হ্যাং, প্রোফাইল সমস্যা"),
    Category("faq",     "📖", "FAQ",                "সাধারণ প্রশ্নের উত্তর"),
    Category("contact", "📩", "Contact Admin",      "সরাসরি অ্যাডমিনকে মেসেজ")
)

private val QUICK_QUESTIONS = listOf(
    "AI ব্যাখ্যা আসছে না কেন?",
    "নতুন প্রশ্ন আপডেট হচ্ছে না",
    "ভুল প্রশ্ন কোথায় দেখব?",
    "QBank-এ ব্যাংকের প্রশ্ন কীভাবে পাব?"
)

private val SYNC_TIPS = listOf(
    "ইন্টারনেট চালু আছে কিনা দেখুন।",
    "Menu → সেটিংস-এ অফলাইন মোড বন্ধ আছে কিনা দেখুন।",
    "বিষয়ের তালিকা স্ক্রিনে টেনে নামিয়ে (pull-to-refresh) রিফ্রেশ করুন।",
    "অ্যাপ পুরো বন্ধ করে আবার খুলুন।",
    "অ্যাডমিন নতুন কনটেন্ট যোগ করলে সবার কাছে পৌঁছাতে কিছুটা সময় (কয়েক মিনিট) লাগতে পারে।"
)

private val ACCOUNT_TIPS = listOf(
    "ফোন নম্বর ও পাসওয়ার্ড ঠিক আছে কিনা আবার দেখুন।",
    "ইন্টারনেট চালু রেখে অ্যাপ বন্ধ করে আবার খুলুন।",
    "অ্যাপ আপডেট করা আছে কিনা দেখুন।"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportCenterScreen(
    onBack: () -> Unit,
    vm: SupportViewModel = viewModel()
) {
    var page by remember { mutableStateOf<String?>(null) }   // null = হোম
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = page != null) { page = null; vm.resetSend() }

    val title = when (page) {
        null      -> "🛟 Support"
        "guide"   -> "📘 App কীভাবে ব্যবহার করব"
        else      -> CATEGORIES.firstOrNull { it.id == page }?.let { "${it.emoji} ${it.title}" } ?: "Support"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) { page = null; vm.resetSend() } else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                null      -> SupportHome(vm, state, onOpen = { page = it })
                "guide"   -> GuidePage()
                "faq"     -> FaqPage()
                "suggest" -> MessagePage(
                    state, vm, "Feature Suggestion",
                    intro = "আপনার আইডিয়াটা লিখুন — কী ফিচার চান, কেন কাজে লাগবে।",
                    placeholder = "যেমন: QBank-এ সাল অনুযায়ী ফিল্টার চাই…"
                )
                "sync"    -> MessagePage(
                    state, vm, "Sync সমস্যা",
                    intro = "আগে এগুলো চেষ্টা করুন:", tips = SYNC_TIPS,
                    sendLabel = "তবু সমস্যা হলে অ্যাডমিনকে জানান",
                    placeholder = "কোন বিষয়/টপিকে কী আসছে না লিখুন…"
                )
                "account" -> MessagePage(
                    state, vm, "App/Account সমস্যা",
                    intro = "আগে এগুলো চেষ্টা করুন:", tips = ACCOUNT_TIPS,
                    sendLabel = "তবু সমস্যা হলে অ্যাডমিনকে জানান",
                    placeholder = "সমস্যাটা বিস্তারিত লিখুন…"
                )
                else      -> MessagePage(
                    state, vm, "Contact Admin",
                    intro = "অ্যাডমিনের জন্য আপনার মেসেজ লিখুন। আপনার নাম ও ফোন নম্বর সাথে যাবে।",
                    placeholder = "আপনার মেসেজ…"
                )
            }
        }
    }
}

// ─── হোম: গাইড কার্ড + Help centre + ৫টা ক্যাটাগরি ───
@Composable
private fun SupportHome(
    vm: SupportViewModel,
    state: com.hanif.smartstudy.viewmodel.SupportState,
    onOpen: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App কীভাবে ব্যবহার করব
        item {
            Card(
                Modifier.fillMaxWidth().clickable { onOpen("guide") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Indigo600.copy(alpha = 0.10f)),
                border = BorderStroke(1.dp, Indigo600.copy(alpha = 0.25f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📘", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("App কীভাবে ব্যবহার করব", fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text("ধাপে ধাপে গাইড ও স্ক্রিনশট", fontFamily = NotoSansBengali,
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", fontSize = 22.sp, color = Indigo600)
                }
            }
        }

        // Help centre
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("❓ Help centre", fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("আপনার প্রশ্ন লিখুন — AI সাথে সাথে উত্তর দেবে", fontFamily = NotoSansBengali,
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("যেমন: AI ব্যাখ্যা আসছে না কেন?", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.ask(query) },
                        enabled = query.isNotBlank() && !state.isAsking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                    ) {
                        if (state.isAsking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("উত্তর খোঁজা হচ্ছে…", fontFamily = NotoSansBengali, color = Color.White)
                        } else {
                            Text("উত্তর জানুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    QUICK_QUESTIONS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            row.forEach { q ->
                                AssistChip(
                                    onClick = { query = q; vm.ask(q) },
                                    label = { Text(q, fontFamily = NotoSansBengali, fontSize = 11.sp, maxLines = 1) }
                                )
                            }
                        }
                    }

                    if (state.answer != null || state.askError != null) {
                        Spacer(Modifier.height(6.dp))
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Indigo600.copy(alpha = 0.07f)).padding(12.dp)
                        ) {
                            state.answer?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = Indigo600, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (state.answerFromAi) "AI উত্তর" else "উত্তর", fontFamily = NotoSansBengali,
                                        fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Indigo600)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(it, fontFamily = NotoSansBengali, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                            state.askError?.let {
                                Text(it, fontFamily = NotoSansBengali, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row {
                                TextButton(onClick = { vm.clearAnswer() }) {
                                    Text("মুছুন", fontFamily = NotoSansBengali, fontSize = 12.sp)
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { onOpen("contact") }) {
                                    Text("উত্তরে হয়নি? অ্যাডমিনকে জানান", fontFamily = NotoSansBengali,
                                        fontSize = 12.sp, color = Indigo600, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("আরও সাহায্য", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(CATEGORIES) { c ->
            Card(
                Modifier.fillMaxWidth().clickable { onOpen(c.id) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(c.hint, fontFamily = NotoSansBengali, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", fontSize = 20.sp, color = Indigo600)
                }
            }
        }
    }
}

// ─── App ব্যবহার গাইড (ধাপ + স্ক্রিনশট) ───
@Composable
private fun GuidePage() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(GUIDE_STEPS) { s ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(s.title, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(s.body, fontFamily = NotoSansBengali, fontSize = 13.sp, lineHeight = 19.sp)
                    if (s.imageUrl.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        AsyncImage(
                            model = s.imageUrl,
                            contentDescription = s.title,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
        }
    }
}

// ─── FAQ ───
@Composable
private fun FaqPage() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(SupportFaq.items) { (q, a, _) ->
            var open by remember { mutableStateOf(false) }
            Card(
                Modifier.fillMaxWidth().clickable { open = !open },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Indigo600)
                    }
                    if (open) {
                        Spacer(Modifier.height(8.dp))
                        Text(a, fontFamily = NotoSansBengali, fontSize = 13.sp, lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─── মেসেজ পেজ (Suggestion / Sync / Account / Contact) ───
@Composable
private fun MessagePage(
    state: com.hanif.smartstudy.viewmodel.SupportState,
    vm: SupportViewModel,
    category: String,
    intro: String,
    tips: List<String> = emptyList(),
    sendLabel: String = "অ্যাডমিনকে পাঠান",
    placeholder: String
) {
    var text by remember(category) { mutableStateOf("") }
    LaunchedEffect(state.sentOk) { if (state.sentOk) text = "" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text(intro, fontFamily = NotoSansBengali, fontSize = 13.sp) }
        if (tips.isNotEmpty()) {
            items(tips) { t ->
                Row {
                    Text("•", fontSize = 14.sp, color = Indigo600)
                    Spacer(Modifier.width(8.dp))
                    Text(t, fontFamily = NotoSansBengali, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            item { HorizontalDivider() ; Spacer(Modifier.height(2.dp)); Text(sendLabel, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        item {
            OutlinedTextField(
                value = text, onValueChange = { text = it; if (state.sentOk) vm.resetSend() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text(placeholder, fontFamily = NotoSansBengali, fontSize = 13.sp) },
                shape = RoundedCornerShape(12.dp)
            )
        }
        item {
            Button(
                onClick = { vm.send(category, text) },
                enabled = text.isNotBlank() && !state.isSending,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                if (state.isSending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("পাঠান", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        item {
            if (state.sentOk) Text("✅ পাঠানো হয়েছে — ধন্যবাদ! অ্যাডমিন দেখবেন।",
                fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
            state.sendError?.let {
                Text(it, fontFamily = NotoSansBengali, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
