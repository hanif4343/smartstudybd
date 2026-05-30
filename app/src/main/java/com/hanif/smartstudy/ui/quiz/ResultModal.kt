package com.hanif.smartstudy.ui.quiz

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.shared.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// ─────────────────────────────────────────────────────────
// Result Modal (Bottom Sheet style)
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultModal(
    result   : QuizResult,
    onRetry  : () -> Unit,
    onHome   : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest  = onHome,
        sheetState        = sheetState,
        containerColor    = CardBg,
        shape             = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier            = Modifier.fillMaxWidth(),
            contentPadding      = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Score ring
            item {
                Spacer(Modifier.height(8.dp))
                ScoreRing(pct = result.pct)
                Spacer(Modifier.height(12.dp))
                Text(result.emoji, fontSize = 36.sp)
                Text(result.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = SlateText, fontFamily = NotoSansBengali,
                    textAlign = TextAlign.Center)
                Text("মোট ${result.total} প্রশ্নের মধ্যে ${result.correct} সঠিক",
                    fontSize = 12.sp, color = MutedText, fontFamily = NotoSansBengali,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
            }

            // Stats row
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultStatBox("✅", result.correct.toString(), "সঠিক",
                        Color(0xFFF0FDF4), GreenOk, Modifier.weight(1f))
                    ResultStatBox("❌", result.wrong.toString(), "ভুল",
                        Color(0xFFFFF1F2), RedWrong, Modifier.weight(1f))
                    ResultStatBox("⏭", result.skipped.toString(), "স্কিপ",
                        Color(0xFFFFFBEB), AmberWarn, Modifier.weight(1f))
                    ResultStatBox("⭐", "+${result.xpEarned}", "XP",
                        Color(0xFFF5F3FF), Indigo600, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }

            // Subject breakdown
            if (result.subjectBreakdown.isNotEmpty()) {
                item {
                    Text("বিষয় ভিত্তিক ফলাফল", fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold, color = MutedText,
                        fontFamily = NotoSansBengali,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                }
                items(result.subjectBreakdown.values.toList()) { sub ->
                    SubjectResultRow(sub)
                    Spacer(Modifier.height(6.dp))
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Action buttons
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = onHome,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Text("🏠 হোম", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick  = onRetry,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Indigo600)
                    ) {
                        Text("🔄 আবার চেষ্টা", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreRing(pct: Int) {
    val animPct by animateFloatAsState(
        targetValue   = pct / 100f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label         = "scoreRing"
    )
    val color = when {
        pct >= 80 -> GreenOk
        pct >= 60 -> Indigo600
        pct >= 40 -> AmberWarn
        else      -> RedWrong
    }
    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 14f, cap = StrokeCap.Round)
            drawArc(Color(0xFFE2E8F0), -90f, 360f, false, style = stroke)
            drawArc(color, -90f, 360f * animPct, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = SlateText, fontFamily = NotoSansBengali)
            Text("স্কোর", fontSize = 10.sp, color = MutedText, fontFamily = NotoSansBengali)
        }
    }
}

@Composable
private fun ResultStatBox(
    icon     : String,
    value    : String,
    label    : String,
    bg       : Color,
    valueColor: Color,
    modifier : Modifier
) {
    Column(
        modifier            = modifier.clip(RoundedCornerShape(14.dp))
            .background(bg).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 18.sp)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            color = valueColor, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = MutedText, fontWeight = FontWeight.Bold,
            fontFamily = NotoSansBengali)
    }
}

@Composable
private fun SubjectResultRow(sub: SubjectScore) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(sub.subject, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = SlateText, fontFamily = NotoSansBengali,
            modifier = Modifier.width(90.dp))
        Box(
            Modifier.weight(1f).height(8.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color(0xFFE2E8F0))
        ) {
            Box(
                Modifier.fillMaxWidth(sub.pct / 100f).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Indigo600, Color(0xFF818CF8))))
            )
        }
        Text("${sub.correct}/${sub.total}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = Indigo600, fontFamily = NotoSansBengali, modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End)
    }
}

// ─────────────────────────────────────────────────────────
// Mock Test Selection Screen
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockSelectionScreen(
    subjects       : List<SubjectEntry>,
    mockConfig     : MockTestConfig,
    onToggleTopic  : (String) -> Unit,
    onSetLimit     : (Int) -> Unit,
    onStart        : () -> Unit,
    onBack         : () -> Unit
) {
    val selectedCount = mockConfig.selectedTopics.size
    val totalQ = subjects.flatMap { s ->
        s.subTopics.filter { st ->
            mockConfig.selectedTopics.contains("${s.name}||${st.name}")
        }
    }.sumOf { it.totalQ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("মক টেস্ট", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            fontFamily = NotoSansBengali)
                        Text("টপিক সিলেক্ট করো", fontSize = 10.sp, color = MutedText,
                            fontFamily = NotoSansBengali)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    Text("$selectedCount টি টপিক", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = Indigo600, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        bottomBar = {
            // Floating start bar
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Question limit input
                    OutlinedTextField(
                        value         = mockConfig.questionLimit.toString(),
                        onValueChange = { it.toIntOrNull()?.let { n -> onSetLimit(n.coerceIn(5, 200)) } },
                        label         = { Text("প্রশ্ন সংখ্যা", fontFamily = NotoSansBengali, fontSize = 11.sp) },
                        singleLine    = true,
                        modifier      = Modifier.width(100.dp),
                        shape         = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick  = onStart,
                        enabled  = selectedCount > 0 || true,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("মক টেস্ট শুরু করুন", fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
                            Text("মোট প্রশ্ন: $totalQ", fontSize = 9.sp,
                                color = Color.White.copy(0.7f), fontFamily = NotoSansBengali)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(subjects) { idx, subject ->
                MockSubjectAccordion(
                    subject      = subject,
                    mockConfig   = mockConfig,
                    onToggleTopic = onToggleTopic
                )
            }
        }
    }
}

@Composable
private fun MockSubjectAccordion(
    subject      : SubjectEntry,
    mockConfig   : MockTestConfig,
    onToggleTopic: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val allSelected = subject.subTopics.all { st ->
        mockConfig.selectedTopics.contains("${subject.name}||${st.name}")
    }

    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            // Subject header
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked         = allSelected,
                    onCheckedChange = {
                        subject.subTopics.forEach { st ->
                            val key = "${subject.name}||${st.name}"
                            val isSelected = mockConfig.selectedTopics.contains(key)
                            if (it && !isSelected) onToggleTopic(key)
                            if (!it && isSelected) onToggleTopic(key)
                        }
                    },
                    colors = CheckboxDefaults.colors(checkedColor = Indigo600)
                )
                Text(subject.name, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = SlateText, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                Text("${subject.subTopics.size} Topics", fontSize = 10.sp, color = MutedText)
                Text(if (expanded) "▲" else "▼", fontSize = 12.sp, color = MutedText)
            }

            // SubTopics
            if (expanded) {
                subject.subTopics.forEach { st ->
                    val key      = "${subject.name}||${st.name}"
                    val selected = mockConfig.selectedTopics.contains(key)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (selected) Color(0xFFF5F3FF) else Color(0xFFF8FAFC))
                            .clickable { onToggleTopic(key) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked         = selected,
                            onCheckedChange = { onToggleTopic(key) },
                            colors          = CheckboxDefaults.colors(checkedColor = Indigo600),
                            modifier        = Modifier.size(20.dp)
                        )
                        Text(st.name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (selected) Indigo600 else SlateText,
                            fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
                        Text("${st.totalQ}টি", fontSize = 10.sp, color = MutedText,
                            fontFamily = NotoSansBengali)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
