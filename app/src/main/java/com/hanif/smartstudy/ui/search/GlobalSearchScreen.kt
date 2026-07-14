package com.hanif.smartstudy.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.local.AppDatabase
import com.hanif.smartstudy.data.local.toQuestionItem
import com.hanif.smartstudy.data.model.AnswerState
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.ui.shared.AnswerBox
import com.hanif.smartstudy.ui.shared.McqOptions
import com.hanif.smartstudy.ui.shared.OrangeTech
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Indigo600  = Color(0xFF4F46E5)

// ── এখন আর কোনো একটা ট্যাবের ViewModel-এর সীমিত (শুধু বর্তমানে ওপেন করা subject/subTopic-এর)
// প্রশ্ন লিস্টের উপর নির্ভর করে না — সরাসরি Room cache থেকে Quiz+QBank+Study তিনটাই একসাথে
// খোঁজে। Room-এ যা কিছু একবার sync হয়েছে (অফলাইনেও থাকে), তার সবকিছুতেই সার্চ কাজ করবে —
// Home/Menu যেখান থেকেই ওপেন করা হোক না কেন, বা user কোনো subject visit না করে থাকলেও।
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack       : () -> Unit,
    onSelect     : (QuestionItem) -> Unit = {}
) {
    val context     = LocalContext.current
    val dao         = remember { AppDatabase.getInstance(context).questionDao() }

    var query       by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("সব") }
    var results      by remember { mutableStateOf<List<QuestionItem>>(emptyList()) }
    val focusReq    = remember { FocusRequester() }
    val keyboard    = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.length >= 2) {
            isSearching = true
            delay(300)
            activeQuery = query
        } else {
            isSearching = false
            activeQuery = ""
        }
    }

    LaunchedEffect(Unit) { focusReq.requestFocus() }

    // Room cache (persistent, অফলাইনেও থাকে) থেকে খোঁজে — Quiz + QBank + Study
    LaunchedEffect(activeQuery) {
        if (activeQuery.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        val found = withContext(Dispatchers.IO) {
            val quiz  = dao.search("QUIZ",  activeQuery)
            val qbank = dao.search("QBANK", activeQuery)
            val study = dao.search("STUDY", activeQuery)
            (quiz + qbank + study).map { it.toQuestionItem() }
        }
        results = found.take(60)
        isSearching = false
    }

    val filtered = remember(results, selectedMode) {
        when (selectedMode) {
            "MCQ"     -> results.filter { it.isMcq() }
            "Written" -> results.filter { it.isWritten() }
            "Study"   -> results.filter { it.isStudy() }
            else      -> results
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("প্রশ্ন, বিষয়, উত্তর খোঁজুন...",
                            fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusReq),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Indigo600,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; activeQuery = "" }) {
                                Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (activeQuery.length >= 2) {
                // Filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("সব" to results.size, "MCQ" to results.count { it.isMcq() }, "Written" to results.count { it.isWritten() }, "Study" to results.count { it.isStudy() })
                    items(modes) { (label, count) ->
                        FilterChip(
                            selected = selectedMode == label, onClick = { selectedMode = label },
                            label = { Text("$label ($count)", fontFamily = NotoSansBengali, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Indigo600, selectedLabelColor = Color.White)
                        )
                    }
                }

                if (isSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Indigo600)

                Text("${filtered.size}টি ফলাফল", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filtered.isEmpty() && !isSearching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🔍", fontSize = 36.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("\"$activeQuery\" পাওয়া যায়নি", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { q ->
                            SearchResultCard(q, activeQuery, onSelect)
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍", fontSize = 48.sp)
                        Text("কমপক্ষে ২টি অক্ষর লিখুন", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("প্রশ্ন, উত্তর, বিষয়, SubTopic দিয়ে খুঁজতে পারবেন", fontFamily = NotoSansBengali, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ── এই কার্ডটা এখন McqOptions/AnswerBox — অ্যাপের বাকি জায়গায় (Quiz/QBank/Study কার্ডে)
// যেই কম্পোনেন্টগুলো দিয়ে MCQ/Written প্রশ্ন দেখানো হয় — সেগুলোই সরাসরি reuse করে।
// ফলে সার্চ রেজাল্ট দেখতে হুবহু আসল MCQ/Written কার্ডের মতোই লাগবে।
@Composable
private fun SearchResultCard(q: QuestionItem, query: String, onClick: (QuestionItem) -> Unit) {
    // MCQ হলে সঠিক অপশনটা আগে থেকেই "reveal" করা অবস্থায় দেখানো হয় (McqOptions একই রঙ/স্টাইলে
    // সঠিক উত্তর সবুজ করে দেখাবে, ঠিক Quiz/QBank কার্ডে উত্তর দেওয়ার পর যেমন দেখায়)
    val revealedItem = remember(q.id) {
        if (q.isMcq()) {
            val correctOpt = when (q.answer.trim().lowercase()) {
                q.optionA.trim().lowercase() -> 1
                q.optionB.trim().lowercase() -> 2
                q.optionC.trim().lowercase() -> 3
                q.optionD.trim().lowercase() -> 4
                else -> 0
            }
            if (correctOpt > 0) q.copy(answerState = AnswerState.McqSelected(correctOpt, true)) else q
        } else q
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick(q) },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (q.subject.isNotBlank()) Text(q.subject, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (q.subTopic.isNotBlank()) Text(q.subTopic, fontSize = 9.sp, color = Indigo600, fontWeight = FontWeight.Bold,
                    fontFamily = NotoSansBengali, modifier = Modifier.background(Indigo600.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                Spacer(Modifier.weight(1f))
                if (q.isWritten()) {
                    Text("Written", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = OrangeTech,
                        modifier = Modifier.background(OrangeTech.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                } else if (q.isStudy()) {
                    Text("Study", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2563EB),
                        modifier = Modifier.background(Color(0xFFDBEAFE), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            Text(highlightText(q.question, query), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali, maxLines = 3)

            when {
                // MCQ — বাকি অ্যাপের মতোই McqOptions (A/B/C/D), সঠিকটা সবুজ করে দেখানো
                q.isMcq() -> McqOptions(item = revealedItem, onAnswer = {})
                // Written/Study — বাকি অ্যাপের মতোই AnswerBox (উত্তর বক্স)
                q.answer.isNotBlank() -> AnswerBox(text = q.answer)
                q.explanation.isNotBlank() -> AnswerBox(text = q.explanation)
                else -> {}
            }
        }
    }
}

private fun highlightText(text: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) { append(text); return@buildAnnotatedString }
    val lower = text.lowercase(); val qLower = query.lowercase(); var start = 0
    while (start < text.length) {
        val idx = lower.indexOf(qLower, start)
        if (idx < 0) { append(text.substring(start)); break }
        append(text.substring(start, idx))
        withStyle(SpanStyle(background = Color(0xFFFEF9C3), fontWeight = FontWeight.ExtraBold)) { append(text.substring(idx, idx + query.length)) }
        start = idx + query.length
    }
}
