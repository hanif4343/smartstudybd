package com.hanif.smartstudy.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
   Archive সেকশন — existing Quiz সেকশনের মতোই ৩-লেভেল ড্রিল-ডাউন:
   Subject কার্ড → Topic কার্ড → Question লিস্ট। ভিজ্যুয়ালি existing
   SubjectListScreen.kt-এর SubjectCard-এর স্টাইল (rounded card, icon box,
   bold name, subtitle, chevron) অনুসরণ করা হয়েছে — কিন্তু ফাইল আলাদা,
   existing স্ক্রিন কোথাও স্পর্শ করা হয়নি।
   ───────────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveHomeScreen(
    onBack    : () -> Unit,
    viewModel : ArchiveViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadInitial(ArchiveSheet.QUIZ_ARCHIVE) }

    when {
        state.selectedTopic != null ->
            ArchiveQuestionListScreen(viewModel = viewModel, onBack = { viewModel.backToTopics() })

        state.selectedSubjectId != null ->
            ArchiveTopicListScreen(
                subjectName = state.subjects.firstOrNull { it.subjectId == state.selectedSubjectId }?.name
                    ?: state.selectedSubjectId ?: "",
                topics   = state.archiveTopics.filter { it.subjectId == state.selectedSubjectId && it.rowCountFor(state.sheet) > 0 }
                    .sortedByDescending { it.rowCountFor(state.sheet) },
                sheet    = state.sheet,
                isLoading = state.isLoadingTopics,
                onBack   = { viewModel.backToSubjects() },
                onTopic  = { viewModel.selectTopic(it) }
            )

        else ->
            ArchiveSubjectListScreen(
                sheet        = state.sheet,
                subjects     = state.subjects,
                archiveTopics= state.archiveTopics,
                isLoading    = state.isLoadingTopics,
                error        = state.error,
                onBack       = onBack,
                onSwitchSheet= { viewModel.switchSheet(it) },
                onSubject    = { viewModel.selectSubject(it) }
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveSubjectListScreen(
    sheet         : ArchiveSheet,
    subjects      : List<SubjectRef>,
    archiveTopics : List<ArchiveTopicRef>,
    isLoading     : Boolean,
    error         : String?,
    onBack        : () -> Unit,
    onSwitchSheet : (ArchiveSheet) -> Unit,
    onSubject     : (String) -> Unit
) {
    // ── প্রতি subject-এ কতগুলো টপিক + মোট প্রশ্ন (আনুমানিক, index থেকে) ──
    val countsBySubject = remember(archiveTopics, sheet) {
        archiveTopics.filter { it.rowCountFor(sheet) > 0 }
            .groupBy { it.subjectId ?: "" }
            .mapValues { (_, list) -> Pair(list.size, list.sumOf { it.rowCountFor(sheet) }) }
    }
    val visibleSubjects = remember(subjects, countsBySubject) {
        subjects.filter { (countsBySubject[it.subjectId ?: ""]?.first ?: 0) > 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive (Admin)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = ArchiveSheet.entries.indexOf(sheet)) {
                ArchiveSheet.entries.forEach { sh ->
                    Tab(
                        selected = sheet == sh,
                        onClick  = { onSwitchSheet(sh) },
                        text     = { Text(sh.label, fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp,
                        fontFamily = NotoSansBengali, modifier = Modifier.padding(12.dp))
                }
            }
            if (visibleSubjects.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("এই মুহূর্তে ${sheet.label}-এ রিভিউ করার মতো কিছু নেই 🎉",
                        fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                return@Scaffold
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleSubjects, key = { it.subjectId ?: it.hashCode().toString() }) { subj ->
                    val (topicCount, qCount) = countsBySubject[subj.subjectId ?: ""] ?: (0 to 0)
                    ArchiveEntityCard(
                        emoji    = "📚",
                        title    = subj.name ?: subj.subjectId ?: "?",
                        subtitle = "$topicCount টি টপিক · ~$qCount টা প্রশ্ন (আনুমানিক)",
                        onClick  = { subj.subjectId?.let(onSubject) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveTopicListScreen(
    subjectName : String,
    topics      : List<ArchiveTopicRef>,
    sheet       : ArchiveSheet,
    isLoading   : Boolean,
    onBack      : () -> Unit,
    onTopic     : (ArchiveTopicRef) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subjectName, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(topics, key = { it.topicId ?: it.hashCode().toString() }) { topic ->
                ArchiveEntityCard(
                    emoji    = "📖",
                    title    = topic.name ?: topic.topicId ?: "?",
                    subtitle = "~${topic.rowCountFor(sheet)} টা প্রশ্ন (আনুমানিক, রিভিউ-না-হওয়া সহ)",
                    onClick  = { onTopic(topic) }
                )
            }
        }
    }
}

/** existing SubjectCard-এর ঠিক একই ভিজ্যুয়াল ভাষা — rounded card, ইমোজি
 * আইকন বক্স, বোল্ড টাইটেল, subtitle, ট্রেলিং chevron। Progress bar নেই যেহেতু
 * Archive-এ "progress %" ধারণাটা প্রযোজ্য না। */
@Composable
private fun ArchiveEntityCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 22.sp) }

            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
            }
            Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
        }
    }
}
