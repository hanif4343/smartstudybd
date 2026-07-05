package com.hanif.smartstudy.ui.menu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.TestHistoryEntry
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.TestHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
//  TestHistoryScreen — "এখন টেস্ট দাও" এর সব রেজাল্ট ইতিহাস
//  (Menu → Profile → 📝 টেস্ট হিস্ট্রি)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestHistoryScreen(
    onBack : () -> Unit,
    vm     : TestHistoryViewModel = viewModel()
) {
    val history by vm.history.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("সব হিস্ট্রি মুছবেন?", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
            text  = { Text("এই কাজটি ফিরিয়ে আনা যাবে না।", fontFamily = NotoSansBengali, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClearDialog = false }) {
                    Text("মুছে দাও", fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("বাতিল", fontFamily = NotoSansBengali)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 টেস্ট হিস্ট্রি", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, null, tint = Red500)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            EmptyHistoryState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SummaryCard(history)
                Spacer(Modifier.height(4.dp))
            }

            items(history, key = { it.id }) { entry ->
                TestHistoryCard(
                    entry      = entry,
                    isExpanded = expandedId == entry.id,
                    onToggle   = { expandedId = if (expandedId == entry.id) null else entry.id }
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── খালি অবস্থা ─────────────────────────────────────────────

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📝", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "এখনো কোনো টেস্ট দেওয়া হয়নি",
                fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Quiz/QBank/Study ট্যাবে গিয়ে \"এখন টেস্ট দাও\"\nবিভাগ থেকে যেকোনো সময় টেস্ট দিতে পারো",
                fontFamily = NotoSansBengali, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── সারাংশ কার্ড ─────────────────────────────────────────────

@Composable
private fun SummaryCard(history: List<TestHistoryEntry>) {
    val totalTests = history.size
    // ungraded (written Model Test) এন্ট্রির pct সবসময় ০ — গড়/সর্বোচ্চ স্কোর হিসাবে ওগুলো বাদ
    val gradedOnly = history.filter { !it.isUngraded }
    val avgPct     = if (gradedOnly.isNotEmpty()) gradedOnly.sumOf { it.pct } / gradedOnly.size else 0
    val bestPct    = gradedOnly.maxOfOrNull { it.pct } ?: 0
    val totalXp    = history.sumOf { it.xpEarned }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryStat("📝", "$totalTests", "মোট টেস্ট")
            SummaryStat("📊", "$avgPct%", "গড় স্কোর")
            SummaryStat("🏆", "$bestPct%", "সর্বোচ্চ")
            SummaryStat("⭐", "$totalXp", "মোট XP")
        }
    }
}

@Composable
private fun SummaryStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary, fontFamily = NotoSansBengali)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(0.7f), fontFamily = NotoSansBengali)
    }
}

// ── একটি টেস্টের কার্ড ────────────────────────────────────────

@Composable
private fun TestHistoryCard(
    entry      : TestHistoryEntry,
    isExpanded : Boolean,
    onToggle   : () -> Unit
) {
    val scoreColor = when {
        entry.pct >= 80 -> Green500
        entry.pct >= 60 -> Teal600
        entry.pct >= 40 -> Amber500
        else            -> Red500
    }
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale("bn")) }

    Card(
        modifier = Modifier.clickable(onClick = onToggle),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Score circle — Model Test-এর written (ungraded) হলে %-এর বদলে "✍️ জমা" দেখায়
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(scoreColor.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (entry.isUngraded) {
                            Text("✍️", fontSize = 16.sp)
                            Text("জমা", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = scoreColor, fontFamily = NotoSansBengali)
                        } else {
                            Text("${entry.pct}%", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = scoreColor, fontFamily = NotoSansBengali)
                            Text(entry.gradeEmoji, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.isModelTest) {
                            Text("🏆 ", fontSize = 12.sp)
                        }
                        Text(
                            entry.topics.joinToString(", "),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${entry.modeLabel} • ${dateFmt.format(Date(entry.timestamp))}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick stats row
            Spacer(Modifier.height(10.dp))
            if (entry.isUngraded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("📝", "${entry.recorded}", "জমা হয়েছে", Indigo600)
                    MiniStat("⏱", formatTime(entry.timeTakenSec), "সময়", Indigo600)
                    MiniStat("💬", "রিভিউ বাকি", "স্ট্যাটাস", Amber500)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("✅", "${entry.correct}", "সঠিক", Green500)
                    MiniStat("❌", "${entry.wrong}", "ভুল", Red500)
                    MiniStat("⏭", "${entry.skipped}", "বাদ", MaterialTheme.colorScheme.onSurfaceVariant)
                    MiniStat("⏱", formatTime(entry.timeTakenSec), "সময়", Indigo600)
                    MiniStat("⭐", "${entry.xpEarned}", "XP", Amber500)
                }
            }

            // Subject breakdown — expanded অবস্থায়
            if (isExpanded && entry.subjectBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                Spacer(Modifier.height(8.dp))
                Text("বিষয়ভিত্তিক ফলাফল", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                entry.subjectBreakdown.values.forEach { sb ->
                    SubjectBreakdownRow(sb.subject, sb.correct, sb.total)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(emoji: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = NotoSansBengali)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
    }
}

@Composable
private fun SubjectBreakdownRow(subject: String, correct: Int, total: Int) {
    val pct = if (total > 0) (correct * 100) / total else 0
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(subject, fontSize = 12.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium)
            Text("$correct/$total ($pct%)", fontSize = 11.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress   = { pct / 100f },
            modifier   = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color      = if (pct >= 60) Green500 else if (pct >= 40) Amber500 else Red500,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

private fun formatTime(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}মি ${s}সে" else "${s}সে"
}
