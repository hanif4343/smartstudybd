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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.delay

private val Indigo600  = Color(0xFF4F46E5)
private val SlateText  = Color(0xFF1E293B)
private val MutedText  = Color(0xFF64748B)
private val CardBg     = Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    allQuestions : List<QuestionItem>,
    onBack       : () -> Unit,
    onSelect     : (QuestionItem) -> Unit = {}
) {
    var query       by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("সব") }
    val focusReq    = remember { FocusRequester() }
    val keyboard    = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.length >= 2) {
            isSearching = true
            delay(300)
            activeQuery = query
            isSearching = false
        } else {
            activeQuery = ""
        }
    }

    LaunchedEffect(Unit) { focusReq.requestFocus() }

    val results = remember(activeQuery, allQuestions) {
        if (activeQuery.length < 2) emptyList()
        else allQuestions.filter { q ->
            val q2 = activeQuery.lowercase()
            q.question.lowercase().contains(q2) || q.answer.lowercase().contains(q2) ||
            q.subject.lowercase().contains(q2)  || q.subTopic.lowercase().contains(q2) ||
            q.optionA.lowercase().contains(q2)  || q.optionB.lowercase().contains(q2)
        }.take(50)
    }

    val filtered = remember(results, selectedMode) {
        when (selectedMode) {
            "MCQ"     -> results.filter { it.isMcq() }
            "Written" -> results.filter { it.isWritten() }
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
                            fontFamily = NotoSansBengali, color = MutedText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusReq),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Indigo600,
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedContainerColor   = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; activeQuery = "" }) {
                                Icon(Icons.Default.Clear, null, tint = MutedText)
                            }
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
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
                    val modes = listOf("সব" to results.size, "MCQ" to results.count { it.isMcq() }, "Written" to results.count { it.isWritten() })
                    items(modes) { (label, count) ->
                        FilterChip(
                            selected = selectedMode == label, onClick = { selectedMode = label },
                            label = { Text("$label ($count)", fontFamily = NotoSansBengali, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Indigo600, selectedLabelColor = Color.White)
                        )
                    }
                }

                if (isSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Indigo600)

                Text("${filtered.size}টি ফলাফল", fontSize = 11.sp, color = MutedText,
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
                                    Text("\"$activeQuery\" পাওয়া যায়নি", fontFamily = NotoSansBengali, color = MutedText, fontWeight = FontWeight.Bold)
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
                        Text("কমপক্ষে ২টি অক্ষর লিখুন", fontFamily = NotoSansBengali, color = MutedText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("প্রশ্ন, উত্তর, বিষয়, SubTopic দিয়ে খুঁজতে পারবেন", fontFamily = NotoSansBengali, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(q: QuestionItem, query: String, onClick: (QuestionItem) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(q) },
        shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(CardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(q.subject, fontSize = 9.sp, color = MutedText, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                    modifier = Modifier.background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (q.subTopic.isNotBlank()) Text(q.subTopic, fontSize = 9.sp, color = Indigo600, fontWeight = FontWeight.Bold,
                    fontFamily = NotoSansBengali, modifier = Modifier.background(Color(0xFFEEF2FF), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                Spacer(Modifier.weight(1f))
                val (typeColor, typeBg) = if (q.isMcq()) Color(0xFF059669) to Color(0xFFD1FAE5) else Color(0xFF7C3AED) to Color(0xFFEDE9FE)
                Text(if (q.isMcq()) "MCQ" else "Written", fontSize = 9.sp, color = typeColor, fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali, modifier = Modifier.background(typeBg, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Text(highlightText(q.question, query), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateText, fontFamily = NotoSansBengali, maxLines = 2)
            if (q.answer.isNotBlank()) {
                Text(highlightText("✅ " + q.answer.take(80) + if (q.answer.length > 80) "..." else "", query),
                    fontSize = 11.sp, color = Color(0xFF059669), fontFamily = NotoSansBengali, maxLines = 1)
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
