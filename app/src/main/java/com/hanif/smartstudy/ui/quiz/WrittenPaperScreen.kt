package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.ui.theme.NotoSansBengali

private val BN_LABELS = listOf(
    "ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ",
    "ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"
)
private val EN_LABELS = ('a'..'t').map { it.toString() }
private fun hasBangla(s: String) = s.any { it in '\u0980'..'\u09FF' }

private data class PaperSection(
    val heading: String,
    val fmt: String,
    val items: List<QuestionItem>,
    val isSingle: Boolean
)

private fun buildPaperSections(questions: List<QuestionItem>): List<PaperSection> {
    val written = questions.filter { it.isWritten() }
    val order = mutableListOf<PaperSection>()
    val byGroup = LinkedHashMap<String, MutableList<QuestionItem>>()
    written.forEach { q ->
        if (q.groupId.isBlank()) {
            order.add(PaperSection(heading = "", fmt = q.formatStyle.lowercase().trim(), items = listOf(q), isSingle = true))
        } else {
            if (!byGroup.containsKey(q.groupId)) byGroup[q.groupId] = mutableListOf()
            byGroup[q.groupId]!!.add(q)
        }
    }
    byGroup.values.forEach { items ->
        val sorted = items.sortedWith(
            compareBy<QuestionItem> { if (it.subIndex == 0) Int.MAX_VALUE else it.subIndex }
                .thenBy { it.id }
        )
        val first = sorted.first()
        order.add(
            PaperSection(
                heading = first.groupHeading,
                fmt = first.formatStyle.lowercase().trim(),
                items = sorted,
                isSingle = false
            )
        )
    }
    return order
}

private fun tableHeads(heading: String): Pair<String, String> {
    val h = heading
    return when {
        h.contains("সন্ধি") -> "প্রদত্ত শব্দ" to "সন্ধি বিচ্ছেদ"
        h.contains("বানান") || h.contains("শুদ্ধ") -> "অশুদ্ধ" to "শুদ্ধ"
        h.contains("বিপরীত") -> "শব্দ" to "বিপরীত শব্দ"
        h.contains("Gender", ignoreCase = true) -> "Word" to "Changed Gender"
        h.contains("Spelling", ignoreCase = true) -> "Given Word" to "Correct Spelling"
        h.contains("plural", ignoreCase = true) || h.contains("বহুবচন") -> "Singular" to "Plural"
        h.contains("Meaning", ignoreCase = true) || h.contains("অর্থ") ||
            h.contains("শব্দার্থ") || h.contains("পারিভাষিক") -> "Word" to "Meaning"
        h.contains("উৎস") -> "শব্দ" to "উৎস"
        else -> "প্রশ্ন" to "উত্তর"
    }
}

/**
 * Sheet markers, rendered exactly as stored:
 *  - <b>...</b> -> bold, tags never shown
 *  - *...* -> bold + underline (highlight)
 *  - __...__ -> bold + underline, except in fillblank where it is the blank
 *  - _..._ -> a blank in fillblank, underline otherwise
 */
private fun styledText(text: String, fmt: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        var bold = false
        while (i < text.length) {
            when {
                text.startsWith("<b>", i) -> { bold = true; i += 3 }
                text.startsWith("</b>", i) -> { bold = false; i += 4 }
                text.startsWith("<br>", i) -> { append("\n"); i += 4 }
                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end == -1) { append("__"); i += 2 } else {
                        if (fmt == "fillblank") {
                            append("________")
                        } else {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                                append(text.substring(i + 2, end))
                            }
                        }
                        i = end + 2
                    }
                }
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end == -1) { append("*"); i++ } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                text[i] == '_' -> {
                    val end = text.indexOf('_', i + 1)
                    if (end == -1) { append("_"); i++ } else {
                        if (fmt == "fillblank") {
                            append("________")
                        } else {
                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                append(text.substring(i + 1, end))
                            }
                        }
                        i = end + 1
                    }
                }
                else -> {
                    val ch = text[i]
                    if (bold) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(ch.toString()) }
                    } else {
                        append(ch.toString())
                    }
                    i++
                }
            }
        }
    }
}

@Composable
private fun PaperItemText(text: String, fmt: String, fontSize: Int = 14) {
    Text(
        text = styledText(text, fmt),
        fontFamily = NotoSansBengali,
        fontSize = fontSize.sp,
        color = Color.Black
    )
}

@Composable
private fun AnswerText(answer: String, prefix: String = "উ. ") {
    if (answer.isBlank()) return
    Text(
        text = prefix + answer,
        fontFamily = NotoSansBengali,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1B5E20)
    )
}

@Composable
private fun TableSection(section: PaperSection, showAns: Boolean) {
    val (headQ, headA) = tableHeads(section.heading)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.weight(1f).border(1.dp, Color.Black).padding(6.dp)
            ) { Text(headQ, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                Modifier.weight(1f).border(1.dp, Color.Black).padding(6.dp)
            ) { Text(headA, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        section.items.forEach { item ->
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.weight(1f).border(1.dp, Color.Black).padding(6.dp)
                ) { PaperItemText(item.question, section.fmt, 13) }
                Box(
                    Modifier.weight(1f).border(1.dp, Color.Black).padding(6.dp)
                ) {
                    if (showAns) PaperItemText(item.answer, section.fmt, 13)
                    else Text("……", fontFamily = NotoSansBengali, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ListSection(section: PaperSection, sectionNum: Int, showAns: Boolean) {
    val labels = if (hasBangla(section.heading) ||
        section.items.firstOrNull()?.let { hasBangla(it.question) } == true
    ) BN_LABELS else EN_LABELS
    if (section.heading.isNotBlank()) {
        Text(
            text = "$sectionNum। ${section.heading}",
            fontFamily = NotoSansBengali,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(4.dp))
    }
    section.items.forEachIndexed { idx, item ->
        val label = if (section.isSingle) "$sectionNum।" else "${labels.getOrNull(idx) ?: (idx + 1).toString()})"
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Column(Modifier.weight(1f)) {
                PaperItemText(item.question, section.fmt)
                if (showAns) {
                    if (section.fmt == "fillblank") {
                        Text(
                            text = "উত্তর: ${item.answer}",
                            fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1B5E20)
                        )
                    } else {
                        AnswerText(item.answer)
                    }
                    if (item.explanation.isNotBlank()) {
                        Text(
                            text = "সমাধান: ${item.explanation}",
                            fontFamily = NotoSansBengali,
                            fontSize = 13.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

/**
 * QBank written paper view for smartstudybd (the real app).
 * Default is questions only; the button shows answers.
 * It only renders group_id / group_heading / sub_index / formatStyle
 * exactly as stored — it never edits data.
 */
@Composable
fun WrittenPaperScreen(questions: List<QuestionItem>, modifier: Modifier = Modifier) {
    var showAns by remember { mutableStateOf(false) }
    val sections = remember(questions) { buildPaperSections(questions) }
    Column(modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "পেপার ভিউ",
                fontFamily = NotoSansBengali,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Button(onClick = { showAns = !showAns }) {
                Text(
                    text = if (showAns) "উত্তর লুকাও" else "উত্তর দেখাও",
                    fontFamily = NotoSansBengali,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (sections.isEmpty()) {
            Text(
                text = "এখানে কোনো Written প্রশ্ন নেই।",
                fontFamily = NotoSansBengali,
                fontSize = 14.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sections.size) { sIdx ->
                    val section = sections[sIdx]
                    val num = sIdx + 1
                    if (section.fmt == "table" && !section.isSingle) {
                        if (section.heading.isNotBlank()) {
                            Text(
                                text = "$num। ${section.heading}",
                                fontFamily = NotoSansBengali,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        TableSection(section, showAns)
                    } else {
                        ListSection(section, num, showAns)
                    }
                }
            }
        }
    }
}
