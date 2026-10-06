package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.AiChatMessage
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.remote.AiChatService
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * ── Study Nav "Phase 7 — AI" ──
 *
 *  🤖 AI বাটন (Topic toolbar) → দুটি ট্যাব:
 *   • জিজ্ঞেস করো — এই লেসনের কনটেন্ট context হিসেবে দিয়ে চ্যাট
 *     (Ask about this lesson) + "সহজ করে বোঝাও" (Explain simply) কুইক বাটন
 *   • Practice — AI লেসন থেকে MCQ বানায় (Generate practice)।
 *     এগুলো শুধু এই শিটেই থাকে — Master QBank/Sheet-এ কিছুই যোগ হয় না।
 *
 *  Study Home → "🤖 AI Study Plan" (Personal recommendation) — Weak/Revision topic দেখে
 *  AI ছোট্ট একটা আজকের পরিকল্পনা দেয় ([StudyPlanSheet])।
 *
 *  কোনো নতুন key/স্কিমা লাগে না — Settings-এ সেভ করা আগের AI key (AiChatService) ব্যবহার হয়।
 */

private val AiAccent = Color(0xFF7C3AED)
private const val NO_KEY_MSG =
    "AI ব্যবহার করতে Settings → AI Key সেকশনে অন্তত একটা key (Groq/Mistral/Cerebras/Gemini) যোগ করো।"
private const val FAIL_MSG = "AI থেকে উত্তর পাওয়া যায়নি — একটু পর আবার চেষ্টা করো, বা Settings-এ API key চেক করো।"

private val aiTagRegex = Regex("<[^>]*>")

private fun aiPlain(s: String): String =
    s.replace(aiTagRegex, "").replace("**", "").replace("__", "").replace("++", "")
        .replace(Regex("^[#>\\-•!?]+\\s*", RegexOption.MULTILINE), "").trim()

/** লেসনের কনটেন্ট থেকে AI-এর জন্য context (সর্বোচ্চ ~৬০০০ অক্ষর) */
private fun buildLessonContext(subject: String, topic: String, qs: List<QuestionItem>): String {
    val sb = StringBuilder()
    sb.append("ছাত্র এখন Study মোডে এই লেসনটি পড়ছে। বিষয়: $subject — $topic\n")
    sb.append("শুধু নিচের লেসন-কনটেন্টের ভিত্তিতে উত্তর দাও; কনটেন্টে না থাকলে সেটা স্পষ্ট বলো, ")
    sb.append("তারপর তোমার সাধারণ জ্ঞান থেকে সাহায্য করো (সেটা যে কনটেন্টের বাইরে তা উল্লেখ করে)।\n\n--- লেসন ---\n")
    for (q in qs) {
        if (sb.length > 6000) break
        val title = aiPlain(q.question)
        if (title.isNotBlank()) sb.append("• ").append(title.take(300)).append('\n')
        if (q.answer.isNotBlank()) sb.append("  উত্তর: ").append(aiPlain(q.answer).take(500)).append('\n')
        if (q.explanation.isNotBlank()) sb.append("  ব্যাখ্যা: ").append(aiPlain(q.explanation).take(700)).append('\n')
    }
    return sb.toString()
}

private data class GenQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String
)

private fun parseGenerated(raw: String): List<GenQuestion> {
    val start = raw.indexOf('[')
    val end = raw.lastIndexOf(']')
    if (start < 0 || end <= start) return emptyList()
    return runCatching {
        val arr = JSONArray(raw.substring(start, end + 1))
        val out = ArrayList<GenQuestion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val opts = o.optJSONArray("options") ?: continue
            val list = (0 until opts.length()).map { opts.optString(it) }.filter { it.isNotBlank() }
            val ans = o.optInt("answer", -1)
            val q = o.optString("question")
            if (q.isBlank() || list.size < 2 || ans !in list.indices) continue
            out.add(GenQuestion(q, list, ans, o.optString("explanation")))
        }
        out
    }.getOrDefault(emptyList())
}

@Composable
private fun AiTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
        color = if (selected) Color.White else AiAccent,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) AiAccent else AiAccent.copy(alpha = 0.10f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun AiQuickChip(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali,
        color = AiAccent,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
            .background(AiAccent.copy(alpha = if (enabled) 0.10f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Topic-এর AI শিট (Ask + Practice)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyAiSheet(subject: String, topic: String, questions: List<QuestionItem>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lessonContext = remember(subject, topic, questions) { buildLessonContext(subject, topic, questions) }

    var tab by remember { mutableStateOf(0) }

    // ── Ask ──
    val messages = remember { mutableStateListOf<AiChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // ── Practice ──
    var generated by remember { mutableStateOf<List<GenQuestion>>(emptyList()) }
    var generating by remember { mutableStateOf(false) }
    var genError by remember { mutableStateOf<String?>(null) }
    var genCount by remember { mutableStateOf(5) }

    fun ask(apiText: String, shown: String = apiText) {
        val text = apiText.trim()
        if (text.isBlank() || sending) return
        val history = messages.toList() + AiChatMessage("user", text)
        messages.add(AiChatMessage("user", shown))
        sending = true; error = null
        scope.launch {
            val keys = withContext(Dispatchers.IO) { SessionManager(ctx).getAiApiKeys() }
            if (!keys.hasAnyKey()) { error = NO_KEY_MSG; sending = false; return@launch }
            val reply = AiChatService.sendMessage(history, keys, lessonContext)
            if (reply != null) messages.add(AiChatMessage("assistant", reply)) else error = FAIL_MSG
            sending = false
        }
    }

    fun generate() {
        if (generating) return
        generating = true; genError = null; generated = emptyList()
        scope.launch {
            val keys = withContext(Dispatchers.IO) { SessionManager(ctx).getAiApiKeys() }
            if (!keys.hasAnyKey()) { genError = NO_KEY_MSG; generating = false; return@launch }
            val prompt = "উপরের লেসন-কনটেন্ট থেকে ঠিক $genCount টি বহুনির্বাচনী (MCQ) প্রশ্ন বানাও, বাংলায়। " +
                "শুধু একটা JSON array ফেরত দাও, আর কিছু লিখবে না (কোনো markdown/কোড-ব্লক নয়)। ফরম্যাট: " +
                "[{\"question\":\"...\",\"options\":[\"...\",\"...\",\"...\",\"...\"],\"answer\":0,\"explanation\":\"...\"}] " +
                "এখানে answer হলো সঠিক অপশনের ০-ভিত্তিক index (০ থেকে ৩)। অপশন ঠিক ৪টি হবে, কনটেন্টে থাকা তথ্যের ওপর ভিত্তি করে, ব্যাখ্যা এক বাক্যে।"
            val reply = AiChatService.sendMessage(
                listOf(AiChatMessage("user", prompt)), keys, lessonContext, maxTokens = 2200
            )
            val parsed = if (reply != null) parseGenerated(reply) else emptyList()
            if (parsed.isEmpty()) genError = if (reply == null) FAIL_MSG else "প্রশ্ন তৈরি হয়নি — আবার চেষ্টা করো।"
            else generated = parsed
            generating = false
        }
    }

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            Text("🤖 AI সহায়ক", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            Text(topic, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotoSansBengali, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiTab("💬 জিজ্ঞেস করো", tab == 0) { tab = 0 }
                AiTab("📝 AI Practice", tab == 1) { tab = 1 }
            }
            Spacer(Modifier.height(10.dp))

            if (tab == 0) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiQuickChip("🧒 সহজ করে বোঝাও", !sending) {
                        ask("এই লেসনটা একদম সহজ ভাষায়, ছোট উদাহরণসহ বুঝিয়ে দাও — যেন ছাত্র প্রথমবার শুনছে।", "🧒 সহজ করে বোঝাও")
                    }
                    AiQuickChip("📌 মূল পয়েন্ট", !sending) {
                        ask("এই লেসনের পরীক্ষায় আসার মতো ৫টি মূল পয়েন্ট ছোট বুলেটে দাও।", "📌 মূল পয়েন্ট")
                    }
                    AiQuickChip("🧠 মনে রাখার কৌশল", !sending) {
                        ask("এই লেসনের মূল বিষয়গুলো মনে রাখার একটা সহজ কৌশল/mnemonic দাও।", "🧠 মনে রাখার কৌশল")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (messages.isEmpty() && !sending) {
                        item {
                            Text("এই লেসন নিয়ে যেকোনো প্রশ্ন করো, বা ওপরের বাটন চাপো।",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = NotoSansBengali, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                    items(messages.toList()) { m ->
                        val mine = m.role == "user"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (mine) AiAccent else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Text(m.content, fontSize = 14.sp, fontFamily = NotoSansBengali,
                                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                    if (sending) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AiAccent)
                                Text("  ভাবছি…", fontSize = 12.sp, fontFamily = NotoSansBengali,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    error?.let { e ->
                        item { Text(e, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontFamily = NotoSansBengali) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        placeholder = { Text("এই লেসন নিয়ে প্রশ্ন করো…", fontFamily = NotoSansBengali, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f), maxLines = 3
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { val t = input; input = ""; ask(t) },
                        enabled = input.isNotBlank() && !sending,
                        colors = ButtonDefaults.buttonColors(containerColor = AiAccent)
                    ) { Text("পাঠাও", fontFamily = NotoSansBengali) }
                }
            } else {
                AiPracticePane(
                    generated = generated, generating = generating, error = genError,
                    count = genCount, onCount = { genCount = it }, onGenerate = { generate() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AI Practice — উত্তর বেছে সাথে সাথে ফিডব্যাক; কোথাও সেভ হয় না
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AiPracticePane(
    generated: List<GenQuestion>, generating: Boolean, error: String?,
    count: Int, onCount: (Int) -> Unit, onGenerate: () -> Unit, modifier: Modifier
) {
    // প্রতিটা প্রশ্নের বাছাই করা অপশন (-1 = এখনো না) — নতুন সেট এলে রিসেট
    val picks = remember(generated) { mutableStateMapOf<Int, Int>() }

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("এই লেসন থেকে AI নতুন MCQ বানাবে। এগুলো শুধু প্র্যাকটিসের জন্য — Question Bank-এ যোগ হয় না।",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(3, 5, 10).forEach { n ->
                        FilterChip(selected = count == n, onClick = { onCount(n) },
                            label = { Text("${n}টি", fontFamily = NotoSansBengali) })
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onGenerate, enabled = !generating,
                        colors = ButtonDefaults.buttonColors(containerColor = AiAccent)) {
                        Text(if (generated.isEmpty()) "✨ তৈরি করো" else "🔁 নতুন সেট", fontFamily = NotoSansBengali)
                    }
                }
                if (generating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AiAccent)
                        Text("  প্রশ্ন বানাচ্ছি…", fontSize = 12.sp, fontFamily = NotoSansBengali)
                    }
                }
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontFamily = NotoSansBengali) }
            }
        }

        itemsIndexedCompat(generated) { idx, q ->
            val picked = picks[idx] ?: -1
            Surface(
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${idx + 1}. ${q.question}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali)
                    q.options.forEachIndexed { oi, opt ->
                        val show = picked >= 0
                        val col = when {
                            show && oi == q.answerIndex -> Color(0xFF059669)
                            show && oi == picked -> Color(0xFFDC2626)
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp), color = col.copy(alpha = if (show && col != MaterialTheme.colorScheme.outline) 0.14f else 0f),
                            border = BorderStroke(1.dp, col.copy(alpha = if (show && col != MaterialTheme.colorScheme.outline) 0.9f else 0.4f)),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !show) { picks[idx] = oi }
                        ) {
                            Text("${"কখগঘ".getOrElse(oi) { '•' }}) $opt", fontSize = 13.sp, fontFamily = NotoSansBengali,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                    if (picked >= 0 && q.explanation.isNotBlank()) {
                        Text("💡 ${q.explanation}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali)
                    }
                }
            }
        }

        if (generated.isNotEmpty() && picks.size == generated.size) {
            item {
                val score = generated.indices.count { picks[it] == generated[it].answerIndex }
                Text("🏁 স্কোর: $score / ${generated.size}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = AiAccent, fontFamily = NotoSansBengali, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

/** items(indexed) এর ছোট wrapper — LazyListScope-এর itemsIndexed import এড়াতে */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(
    list: List<GenQuestion>, content: @Composable (Int, GenQuestion) -> Unit
) {
    items(list.size) { i -> content(i, list[i]) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Study Home — AI Study Plan (ব্যক্তিগত সাজেশন)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanSheet(rows: List<RevisionRow>, onOpenTopic: (String, String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val keys = withContext(Dispatchers.IO) { SessionManager(ctx).getAiApiKeys() }
        if (!keys.hasAnyKey()) { error = NO_KEY_MSG; return@LaunchedEffect }
        val list = rows.take(6).joinToString("\n") { "- ${it.subject} → ${it.topic} (${it.status.label}: ${it.reason})" }
        val prompt = "ছাত্রের Study ডেটা অনুযায়ী নিচের Topic-গুলোতে সমস্যা/রিভিশন দরকার:\n$list\n\n" +
            "আজকের জন্য একটা ছোট (৩–৪ ধাপের) পড়ার পরিকল্পনা দাও — কোনটা আগে, কত মিনিট, কীভাবে রিভিশন করবে। " +
            "বাংলায়, বন্ধুত্বপূর্ণ ও সংক্ষিপ্ত। মোট ৮০ শব্দের মধ্যে।"
        val reply = AiChatService.sendMessage(listOf(AiChatMessage("user", prompt)), keys)
        if (reply != null) text = reply else error = FAIL_MSG
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🤖 আজকের AI Study Plan", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            when {
                text != null -> Text(text!!, fontSize = 14.sp, fontFamily = NotoSansBengali)
                error != null -> Text(error!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontFamily = NotoSansBengali)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AiAccent)
                    Text("  পরিকল্পনা বানাচ্ছি…", fontSize = 13.sp, fontFamily = NotoSansBengali)
                }
            }
            Text("📍 সাজেস্টেড Topic", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            rows.take(6).forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss(); onOpenTopic(r.subject, r.topic) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(r.status.emoji, modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.topic, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali, maxLines = 1)
                        Text("${r.subject} · ${r.reason}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
