package com.hanif.smartstudy.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.ArchiveSheet
import com.hanif.smartstudy.data.model.ArchiveTopicRef
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.ArchiveViewModel

/* ─────────────────────────────────────────────────────────────────────────
   Archive সেকশন — নতুন, স্বতন্ত্র স্ক্রিন (existing Quiz/QBank স্ক্রিন থেকে
   সম্পূর্ণ আলাদা ফাইল, existing কোড অপরিবর্তিত)। Admin-only — MainScreen থেকে
   isAdmin গেট করে ঢোকানো হয়।
   ───────────────────────────────────────────────────────────────────────── */

private val ArchiveAccent = Color(0xFFF59E0B)      // Amber — "cleanup/admin টুল" বোঝাতে Quiz/QBank-এর ইন্ডিগো থেকে ইচ্ছাকৃতভাবে আলাদা রঙ
private val ArchiveAccentBg = Color(0xFFFFFBEB)

@Composable
fun ArchiveHomeScreen(
    onBack    : () -> Unit,
    viewModel : ArchiveViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadInitial(ArchiveSheet.QUIZ_ARCHIVE) }

    // ── টপিক সিলেক্ট করা থাকলে প্রশ্ন-লিস্ট স্ক্রিনে চলে যায় ──
    if (state.selectedTopic != null) {
        ArchiveQuestionListScreen(viewModel = viewModel, onBack = { viewModel.backToTopics() })
        return
    }

    val subjectNameById = remember(state.subjects) {
        state.subjects.associate { (it.subjectId ?: "") to (it.name ?: it.subjectId ?: "?") }
    }
    val groupedTopics = remember(state.archiveTopics, state.sheet) {
        state.archiveTopics
            .filter { it.rowCountFor(state.sheet) > 0 }
            .groupBy { it.subjectId ?: "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive (Admin)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ArchiveAccentBg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Quiz-Archive / QBank-Archive টগল ──
            TabRow(
                selectedTabIndex = ArchiveSheet.entries.indexOf(state.sheet),
                containerColor = Color.White,
                contentColor = ArchiveAccent
            ) {
                ArchiveSheet.entries.forEach { sh ->
                    Tab(
                        selected = state.sheet == sh,
                        onClick  = { viewModel.switchSheet(sh) },
                        text     = { Text(sh.label, fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            if (state.isLoadingTopics) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ArchiveAccent)
                }
                return@Scaffold
            }

            state.error?.let { err ->
                Surface(color = Color(0xFFFEE2E2), modifier = Modifier.fillMaxWidth()) {
                    Text(err, color = Color(0xFFB91C1C), fontSize = 13.sp, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(12.dp))
                }
            }

            if (groupedTopics.isEmpty() && !state.isLoadingTopics) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "এই মুহূর্তে ${state.sheet.label}-এ রিভিউ করার মতো কিছু নেই 🎉",
                        fontFamily = NotoSansBengali, color = Color.Gray, fontSize = 14.sp
                    )
                }
                return@Scaffold
            }

            LazyColumn(Modifier.fillMaxSize()) {
                groupedTopics.toSortedMap(compareBy { subjectNameById[it] ?: it }).forEach { (subjectId, topics) ->
                    item(key = "hdr_$subjectId") {
                        Text(
                            subjectNameById[subjectId] ?: subjectId,
                            fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = ArchiveAccent,
                            modifier = Modifier.fillMaxWidth().background(ArchiveAccentBg)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(topics.sortedByDescending { it.rowCountFor(state.sheet) }, key = { it.topicId ?: it.hashCode().toString() }) { topic ->
                        ArchiveTopicRow(topic = topic, count = topic.rowCountFor(state.sheet), onClick = { viewModel.selectTopic(topic) })
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ArchiveTopicRow(topic: ArchiveTopicRef, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(topic.name ?: topic.topicId ?: "?", fontFamily = NotoSansBengali, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("~$count টা প্রশ্ন (আনুমানিক)", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
    Divider(color = Color(0xFFF3F4F6))
}
