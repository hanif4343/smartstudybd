package com.hanif.smartstudy.ui.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.ArchiveSheet
import com.hanif.smartstudy.data.model.ArchiveTopicRef
import com.hanif.smartstudy.data.model.SubjectRef
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.ArchiveViewModel

/* ─────────────────────────────────────────────────────────────────────────
   Archive সেকশন — এখন হুবহু original Quiz-এর SubjectListScreen/SubTopicListScreen-এর
   প্যাটার্ন অনুসরণ করে:
   - কোনো TopAppBar নেই — gradient হেডার ব্যানার LazyColumn-এর প্রথম item হিসেবে
     (ঠিক SubjectListScreen.kt-এর মতো), ব্যাক নেভিগেশন সিস্টেম BackHandler দিয়ে
     (app-এর বাকি সব স্ক্রিনের মতোই, কোনো on-screen ব্যাক-অ্যারো নেই)
   - Subject/Topic কার্ড হুবহু SubjectCard/SubTopicCard-এর লেআউট (icon box,
     bold title, count subtitle, chevron) — progress bar বাদ, কারণ Archive-এ
     % সম্পন্ন ধারণাটা প্রযোজ্য না
   - একই depth-based single-orchestrator ধাঁচ — CoreScreen.kt-এর navPath.depth()
     প্যাটার্নের মতোই এখানে selectedSubjectId/selectedTopic দিয়ে depth নির্ধারণ হয়
   এই ফাইল স্বতন্ত্র — existing SubjectListScreen.kt/CoreScreen.kt কোথাও স্পর্শ
   করা হয়নি, শুধু ভিজ্যুয়াল ভাষা কপি করা হয়েছে।
   ───────────────────────────────────────────────────────────────────────── */

private val ArchiveGradient = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))

@Composable
fun ArchiveHomeScreen(
    onBack    : () -> Unit,
    viewModel : ArchiveViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadInitial(ArchiveSheet.QUIZ_ARCHIVE) }

    // ── CoreScreen.kt-এর BackHandler প্যাটার্নের মতোই — depth অনুযায়ী এক ধাপ
    // পিছিয়ে যায়, depth 0-এ থাকলে Archive থেকে বেরিয়ে Home-এ ফেরত যায় ──
    BackHandler(enabled = true) {
        when {
            state.selectedTopic != null      -> viewModel.backToTopics()
            state.selectedSubjectId != null  -> viewModel.backToSubjects()
            else                              -> onBack()
        }
    }

    when {
        state.selectedTopic != null ->
            ArchiveQuestionListScreen(viewModel = viewModel, onBack = { viewModel.backToTopics() })

        state.selectedSubjectId != null ->
            ArchiveTopicListScreen(
                subjectName = state.subjects.firstOrNull { it.subjectId == state.selectedSubjectId }?.name
                    ?: state.selectedSubjectId ?: "",
                topics    = state.archiveTopics.filter { it.subjectId == state.selectedSubjectId && it.rowCountFor(state.sheet) > 0 }
                    .sortedByDescending { it.rowCountFor(state.sheet) },
                sheet     = state.sheet,
                isLoading = state.isLoadingTopics,
                onTopic   = { viewModel.selectTopic(it) }
            )

        else ->
            ArchiveSubjectListScreen(
                sheet         = state.sheet,
                subjects      = state.subjects,
                archiveTopics = state.archiveTopics,
                isLoading     = state.isLoadingTopics,
                error         = state.error,
                onSwitchSheet = { viewModel.switchSheet(it) },
                onSubject     = { viewModel.selectSubject(it) }
            )
    }
}

/** depth 0 — original SubjectListScreen()-এর হুবহু কাঠামো (gradient header + LazyColumn) */
@Composable
private fun ArchiveSubjectListScreen(
    sheet         : ArchiveSheet,
    subjects      : List<SubjectRef>,
    archiveTopics : List<ArchiveTopicRef>,
    isLoading     : Boolean,
    error         : String?,
    onSwitchSheet : (ArchiveSheet) -> Unit,
    onSubject     : (String) -> Unit
) {
    val countsBySubject = remember(archiveTopics, sheet) {
        archiveTopics.filter { it.rowCountFor(sheet) > 0 }
            .groupBy { it.subjectId ?: "" }
            .mapValues { (_, list) -> Pair(list.size, list.sumOf { it.rowCountFor(sheet) }) }
    }
    val visibleSubjects = remember(subjects, countsBySubject) {
        subjects.filter { (countsBySubject[it.subjectId ?: ""]?.first ?: 0) > 0 }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header — SubjectListScreen.kt-এর হেডার ব্লকের হুবহু কাঠামো, শুধু
        // রঙ Amber (যাতে Archive সহজে আলাদা বোঝা যায়) আর নিচে sheet-টগল ট্যাব ──
        item {
            Box(
                Modifier.fillMaxWidth().background(ArchiveGradient)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column {
                    Text("Archive", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali)
                    Text("বিষয় বেছে নিন (Admin — duplicate cleanup)", fontSize = 12.sp,
                        color = Color.White.copy(0.75f), fontFamily = NotoSansBengali)
                }
            }
            TabRow(
                selectedTabIndex = ArchiveSheet.entries.indexOf(sheet),
                containerColor   = Color.White
            ) {
                ArchiveSheet.entries.forEach { sh ->
                    Tab(
                        selected = sheet == sh,
                        onClick  = { onSwitchSheet(sh) },
                        text     = { Text(sh.label, fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFD97706))
                }
            }
        }

        if (error != null) {
            item {
                Text(error, color = Color(0xFFB91C1C), fontSize = 12.sp, fontFamily = NotoSansBengali,
                    modifier = Modifier.padding(16.dp))
            }
        }

        if (!isLoading && visibleSubjects.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("এই মুহূর্তে ${sheet.label}-এ রিভিউ করার মতো কিছু নেই 🎉",
                        fontSize = 13.sp, fontFamily = NotoSansBengali, color = Color.Gray)
                }
            }
        }

        items(visibleSubjects, key = { it.subjectId ?: it.hashCode().toString() }) { subj ->
            val (topicCount, qCount) = countsBySubject[subj.subjectId ?: ""] ?: (0 to 0)
            ArchiveSubjectCard(
                title    = subj.name ?: subj.subjectId ?: "?",
                subtitle = "$topicCount টি টপিক  ·  ~$qCount টা প্রশ্ন (আনুমানিক)",
                onClick  = { subj.subjectId?.let(onSubject) }
            )
        }
    }
}

/** depth 1 — original SubTopicListScreen()-এর হুবহু কাঠামো (gradient header + topic কার্ড লিস্ট) */
@Composable
private fun ArchiveTopicListScreen(
    subjectName : String,
    topics      : List<ArchiveTopicRef>,
    sheet       : ArchiveSheet,
    isLoading   : Boolean,
    onTopic     : (ArchiveTopicRef) -> Unit
) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().background(ArchiveGradient)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Column {
                    Text(subjectName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali)
                    Text("${topics.size} টি টপিক", fontSize = 11.sp, color = Color.White.copy(0.65f),
                        fontFamily = NotoSansBengali)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFD97706))
                }
            }
        }

        items(topics, key = { it.topicId ?: it.hashCode().toString() }) { topic ->
            ArchiveTopicCard(
                title    = topic.name ?: topic.topicId ?: "?",
                subtitle = "~${topic.rowCountFor(sheet)} টা প্রশ্ন (আনুমানিক, রিভিউ-না-হওয়া সহ)",
                onClick  = { onTopic(topic) }
            )
        }
    }
}

/** original SubjectCard()-এর হুবহু লেআউট — icon box + bold title + subtitle + chevron
 * (progress bar বাদ, Archive-এ প্রযোজ্য না) */
@Composable
private fun ArchiveSubjectCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Text("📚", fontSize = 22.sp) }

            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali)
            }
            Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
        }
    }
}

/** original SubTopicCard()-এর হুবহু লেআউট (ছোট কার্ড, icon ছাড়া) */
@Composable
private fun ArchiveTopicCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = NotoSansBengali)
            }
            Icon(Icons.Default.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp))
        }
    }
}
