package com.hanif.smartstudy.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.LocalTechniqueLinkStore
import com.hanif.smartstudy.data.model.UserTechnique
import com.hanif.smartstudy.data.remote.ApiResult
import com.hanif.smartstudy.data.remote.FirebaseDataService
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TechniqueMatch
import com.hanif.smartstudy.util.TechniqueSuggester
import com.hanif.smartstudy.util.TechniqueSuggestResult
import com.hanif.smartstudy.util.TechniqueSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Admin-এর জন্য: একটা টেকনিকের সাথে সম্পর্কিত প্রশ্নের প্রস্তাব-তালিকা।
 * কিছুই আগে থেকে টিক/যোগ করা থাকে না — Admin প্রতিটা প্রশ্ন নিজে যাচাই করে
 *   ✅ যোগ করো (টেকনিকের কপি ওই প্রশ্নে বসে)  অথবা  ❌ বাদ দাও (আর কখনো প্রস্তাব হবে না)।
 * কিবোর্ড: ↑/↓ = সারি, A = যোগ, R = বাদ, Esc = বন্ধ।
 */
@Composable
fun TechniqueLinkDialog(
    source   : UserTechnique,
    onDismiss: () -> Unit,
    privateMode: Boolean = false,        // true = প্রাইভেট টেকনিক: লিংক শুধু এই ফোনে (Firebase-এ কিছু যায় না)
    autoCloseIfEmpty: Boolean = false    // সেভের পর নিজে থেকে খুললে — প্রস্তাব না থাকলে চুপচাপ বন্ধ
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val cacheDao = remember { AppDatabase.getInstance(context).techniqueCacheDao() }

    var loading by remember(source.id) { mutableStateOf(true) }
    var loadError by remember(source.id) { mutableStateOf<String?>(null) }
    var result  by remember(source.id) { mutableStateOf<TechniqueSuggestResult?>(null) }
    // প্রশ্ন-id → "added" | "rejected" | "busy" | "error"
    val status = remember(source.id) { mutableStateMapOf<String, String>() }
    var selected by remember(source.id) { mutableStateOf(0) }
    var filter by remember(source.id) { mutableStateOf("") }   // "" = সব, নইলে TechniqueMatch.*
    // ✨ AI অনুসন্ধান — শুধু বাটন চাপলে চলে (নিজে থেকে কখনো না)
    val aiItems = remember(source.id) { mutableStateListOf<TechniqueSuggestion>() }
    var aiLoading by remember(source.id) { mutableStateOf(false) }
    var aiMsg by remember(source.id) { mutableStateOf<String?>(null) }
    var aiDone by remember(source.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(source.id) {
        loading = true
        val linkedKeys: Set<String>
        val rejected: Set<String>
        if (privateMode) {
            val (l, r) = LocalTechniqueLinkStore.getState(context, source.id)
            linkedKeys = l; rejected = r
        } else {
            val st = FirebaseDataService.fetchTechniqueLinkState(source.id)
            if (st is ApiResult.Success) { linkedKeys = st.data.first.keys; rejected = st.data.second }
            else {
                linkedKeys = emptySet(); rejected = emptySet()
                loadError = "লিংকের তথ্য আনা যায়নি — যোগ করা/বাদ দেওয়া প্রশ্ন আবার দেখা যেতে পারে"
            }
        }
        result = TechniqueSuggester.suggest(context, source, linkedKeys, rejected)
        loading = false
        if (autoCloseIfEmpty && result?.items.isNullOrEmpty()) { onDismiss(); return@LaunchedEffect }
        runCatching { focus.requestFocus() }
    }

    fun doAdd(s: TechniqueSuggestion) {
        if (status[s.questionId] != null) return
        status[s.questionId] = "busy"
        scope.launch {
            if (privateMode) {
                LocalTechniqueLinkStore.link(context, source.id, s.questionId)
                status[s.questionId] = "added"
            } else {
                val r = FirebaseDataService.createTechniqueLink(source, s.questionId)
                if (r is ApiResult.Success) {
                    runCatching { cacheDao.invalidate(s.questionId) }   // এই ফোনে সাথে সাথে দেখাবে
                    status[s.questionId] = "added"
                } else status[s.questionId] = "error"
            }
        }
    }
    fun doReject(s: TechniqueSuggestion) {
        if (status[s.questionId] != null) return
        status[s.questionId] = "busy"
        scope.launch {
            if (privateMode) {
                LocalTechniqueLinkStore.reject(context, source.id, s.questionId)
                status[s.questionId] = "rejected"
            } else {
                val r = FirebaseDataService.rejectTechniqueLink(source.id, s.questionId)
                status[s.questionId] = if (r is ApiResult.Success) "rejected" else "error"
            }
        }
    }

    val ruleItems = result?.items ?: emptyList()
    val allItems = ruleItems + aiItems.filter { a -> ruleItems.none { it.questionId == a.questionId } }
    // "সব"-এ 🔶 (আংশিক সম্পর্কিত) দেখানো হয় না — ওগুলো আলাদা 🔶 ফিল্টারে
    val items = if (filter.isEmpty()) allItems.filter { TechniqueMatch.AIR !in it.kinds } else allItems.filter { filter in it.kinds }

    fun runAi() {
        if (aiLoading) return
        aiLoading = true; aiMsg = null
        scope.launch {
            val keys = withContext(Dispatchers.IO) { SessionManager(context.applicationContext).getAiApiKeys() }
            val linkedKeys: Set<String>; val rej: Set<String>
            val st = if (privateMode) null else FirebaseDataService.fetchTechniqueLinkState(source.id)
            if (st is ApiResult.Success) { linkedKeys = st.data.first.keys; rej = st.data.second }
            else { linkedKeys = emptySet(); rej = emptySet() }
            val skip = (ruleItems.map { it.questionId } + aiItems.map { it.questionId }).toSet()
            val r = TechniqueSuggester.aiSuggest(context, source, skip, linkedKeys, rej, keys)
            aiItems.addAll(r.items)
            aiMsg = r.message
            aiDone = r.ok
            aiLoading = false
            if (r.items.any { TechniqueMatch.AI in it.kinds }) filter = TechniqueMatch.AI
        }
    }
    LaunchedEffect(filter) { selected = 0 }
    LaunchedEffect(selected) { if (items.isNotEmpty()) runCatching { listState.animateScrollToItem(selected.coerceIn(0, items.lastIndex)) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f).fillMaxHeight(0.92f)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown || items.isEmpty()) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionDown -> { selected = (selected + 1).coerceAtMost(items.lastIndex); true }
                        Key.DirectionUp   -> { selected = (selected - 1).coerceAtLeast(0); true }
                        Key.A -> { items.getOrNull(selected)?.let { doAdd(it); if (selected < items.lastIndex) selected++ }; true }
                        Key.R -> { items.getOrNull(selected)?.let { doReject(it); if (selected < items.lastIndex) selected++ }; true }
                        else -> false
                    }
                },
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Text(if (privateMode) "🔗 আপনার প্রাইভেট টেকনিক — সম্পর্কিত প্রশ্ন" else "🔗 সম্পর্কিত প্রশ্ন (প্রস্তাব)", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = AmberWarn.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        source.text.replace(Regex("<[^>]+>"), "").take(160).let { if (source.text.length > 160) "$it…" else it },
                        fontSize = 12.sp, fontFamily = NotoSansBengali, maxLines = 3,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    (if (privateMode) "এই টেকনিক শুধু আপনিই দেখবেন (অন্য কেউ না)। " else "") +
                    "প্রতিটা প্রশ্ন নিজে দেখে যাচাই করুন — উত্তরের সাথে টেকনিকটা সত্যিই মিললে ✅ যোগ করুন, না মিললে ❌ বাদ দিন। কিছুই নিজে থেকে যোগ হয় না।   ⌨ ↑↓ · A যোগ · R বাদ",
                    fontSize = 10.5.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                loadError?.let { Text(it, fontSize = 10.sp, color = RedWrong, fontFamily = NotoSansBengali) }
                if (source.tagList().isNotEmpty()) {
                    Text("🏷 " + source.tagList().joinToString(", "), fontSize = 11.sp, color = Indigo600,
                        fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                } else if (!loading) {
                    Text("ট্যাগ দেওয়া নেই — তাই ট্যাগ-মিল খোঁজা হয়নি (এডিট করে ট্যাগ দিলে পাওয়া যাবে)",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                }
                Spacer(Modifier.height(6.dp))

                // ── ✨ AI বাটন (Admin চাইলে) ──
                if (!privateMode) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { runAi() }, enabled = !aiLoading && !loading,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            if (aiLoading) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("AI খুঁজছে…", fontSize = 12.sp, fontFamily = NotoSansBengali)
                            } else {
                                Text(if (aiDone) "✨ আবার AI দিয়ে খুঁজুন" else "✨ AI দিয়ে আরও খুঁজুন", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("শুধু আপনি চাপলে চলে", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    }
                    aiMsg?.let { Text(it, fontSize = 10.5.sp, color = Indigo600, fontFamily = NotoSansBengali, modifier = Modifier.padding(top = 3.dp)) }
                    Spacer(Modifier.height(6.dp))
                }

                // ── মিলের ধরন অনুযায়ী ফিল্টার ──
                if (allItems.isNotEmpty()) {
                    val chips = listOf(
                        "" to "সব",
                        TechniqueMatch.TAG to "🏷 ট্যাগ",
                        TechniqueMatch.Q   to "❓ প্রশ্ন",
                        TechniqueMatch.OA  to "🔘 অপশন+উত্তর",
                        TechniqueMatch.ALL to "✅ সব মিল",
                        TechniqueMatch.AI  to "✨ AI",
                        TechniqueMatch.AIR to "🔶 সম্পর্কিত"
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { (k, label) ->
                            val n = if (k.isEmpty()) allItems.count { TechniqueMatch.AIR !in it.kinds } else allItems.count { k in it.kinds }
                            if (n > 0 || k.isEmpty() || (k == TechniqueMatch.AI && aiDone)) {
                                FilterChip(
                                    selected = filter == k, onClick = { filter = k },
                                    label = { Text("$label ($n)", fontSize = 11.sp, fontFamily = NotoSansBengali) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        result?.sourceFound == false -> Text(
                            "এই টেকনিকের মূল প্রশ্নটা এই ফোনে ডাউনলোড করা নেই, তাই সম্পর্কিত প্রশ্ন খোঁজা যায়নি। প্রশ্নটা একবার খুলে (ডাউনলোড করে) আবার 🔗 চাপুন।",
                            fontSize = 12.sp, fontFamily = NotoSansBengali, modifier = Modifier.align(Alignment.Center).padding(12.dp)
                        )
                        items.isEmpty() -> Text(
                            (if (allItems.isEmpty()) "কোনো প্রস্তাব পাওয়া যায়নি।" else "এই ধরনের কোনো প্রশ্ন নেই।") + "\n(এই ফোনে ডাউনলোড হওয়া সব বিষয়ের ${result?.poolSize ?: 0}টা প্রশ্নে খোঁজা হয়েছে)",
                            fontSize = 12.sp, fontFamily = NotoSansBengali, modifier = Modifier.align(Alignment.Center).padding(12.dp)
                        )
                        else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(items, key = { _, s -> s.sheet + s.questionId }) { idx, s ->
                                val st = status[s.questionId]
                                val isSel = idx == selected
                                Card(
                                    shape  = RoundedCornerShape(10.dp),
                                    border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) Indigo600 else MaterialTheme.colorScheme.outlineVariant),
                                    colors = CardDefaults.cardColors(containerColor = when (st) {
                                        "added" -> GreenOk.copy(alpha = 0.10f)
                                        "rejected" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = Indigo600.copy(alpha = 0.10f)) {
                                            Text(s.reason, fontSize = 10.sp, color = Indigo600, fontWeight = FontWeight.Bold,
                                                fontFamily = NotoSansBengali, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(s.question, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali)
                                        Spacer(Modifier.height(2.dp))
                                        Text("উত্তর: ${s.answer}", fontSize = 12.sp, color = GreenOk, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                                        Text("${s.subject} › ${s.subTopic}  ·  ${s.questionId}", fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                                        Spacer(Modifier.height(6.dp))
                                        when (st) {
                                            "added" -> Text("✅ যোগ হয়েছে", fontSize = 12.sp, color = GreenOk, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                                            "rejected" -> Text("❌ বাদ দেওয়া হয়েছে", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                                            "busy" -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = { selected = idx; doAdd(s) }, modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = GreenOk),
                                                    contentPadding = PaddingValues(vertical = 6.dp)) {
                                                    Text("✅ যোগ করো", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(onClick = { selected = idx; doReject(s) }, modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 6.dp)) {
                                                    Text("❌ বাদ দাও", fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = RedWrong)
                                                }
                                            }
                                        }
                                        if (st == "error") Text("⚠️ ব্যর্থ — নেট দেখে আবার চেষ্টা করুন", fontSize = 10.sp, color = RedWrong, fontFamily = NotoSansBengali)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                val added = status.values.count { it == "added" }
                val rej = status.values.count { it == "rejected" }
                val total = result?.totalFound ?: 0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$added যোগ · $rej বাদ" + if (total > items.size) " · আরও ${total - items.size}টা প্রস্তাব আছে (আবার 🔗 চাপলে দেখাবে)" else "",
                        fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (privateMode && items.any { status[it.questionId] == null }) {
                        // প্রাইভেটে ভুল মিলের ক্ষতি শুধু নিজের — তাই বাকি সবগুলো একবারে যোগ করার সুযোগ
                        OutlinedButton(onClick = { items.filter { status[it.questionId] == null }.forEach { doAdd(it) } }) {
                            Text("সব যোগ", fontFamily = NotoSansBengali, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Button(onClick = onDismiss) { Text("বন্ধ করুন", fontFamily = NotoSansBengali) }
                }
            }
        }
    }
}
