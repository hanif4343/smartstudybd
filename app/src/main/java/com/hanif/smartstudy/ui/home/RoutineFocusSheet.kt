package com.hanif.smartstudy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.data.model.RoutineItem
import com.hanif.smartstudy.data.model.StudyItem
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.QuizViewModel
import com.hanif.smartstudy.viewmodel.RoutineViewModel

// HomeScreen.kt তে এই রঙগুলো private val হিসেবে আছে (file-scoped, import করা যায় না),
// তাই এখানে একই value দিয়ে local copy রাখা হলো যাতে UI consistent থাকে।
private val PrimaryIndigo = Color(0xFF4F46E5)
private val GreenMint     = Color(0xFF10B981)

/**
 * ════════════════════════════════════════════════════════════════
 *  RoutineFocusSheet
 * ════════════════════════════════════════════════════════════════
 * Routine এর একটা item tap করলে এই bottom sheet খোলে — সেই subject/
 * sub_topic এর সাথে মিলে যাওয়া study content এখানেই দেখানো হয়, যাতে
 * "পড়া শুরু করলাম" বলার পর আলাদা স্ক্রিনে গিয়ে খুঁজতে না হয়।
 *
 * এখন (ধাপ ১): শুধু study content দেখানো + "পড়া শেষ" বাটন (manual)।
 * পরের ধাপে (ব্যবহারকারীর ইচ্ছায়): এর সাথে quiz যুক্ত হবে, আর quiz
 * score ভিত্তিতে auto-complete হবে।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFocusSheet(
    item: RoutineItem,
    onDismiss: () -> Unit,
    routineVm: RoutineViewModel = viewModel(),
    quizVm: QuizViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── এই routine item এর subject/sub_topic এর সাথে মিলে যাওয়া study content খুঁজি ──
    val matchedStudy = remember(item.id) {
        val all = quizVm.getStudyContentSnapshot()
        all.filter { s ->
            val subjMatch = s.subject?.trim()?.equals(item.subject.trim(), ignoreCase = true) == true
            val topicMatch = item.subTopic.isBlank() ||
                s.subTopic?.trim()?.equals(item.subTopic.trim(), ignoreCase = true) == true
            subjMatch && topicMatch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp).width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE2E8F0))
            )
        }
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 640.dp)) {

            // ── হেডার ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.subject, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, color = Slate800)
                    if (item.subTopic.isNotBlank()) {
                        Text(item.subTopic, fontSize = 13.sp, fontFamily = NotoSansBengali, color = Color.Gray)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }

            if (matchedStudy.isEmpty()) {
                // ── কোনো ম্যাচিং কন্টেন্ট পাওয়া যায়নি ──
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📭", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "এই বিষয়ে কোনো পড়া এখনো যুক্ত হয়নি",
                        fontSize = 14.sp, fontFamily = NotoSansBengali, color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "নিজে পড়াশোনা শেষ করে নিচের বাটনে ট্যাপ করো",
                        fontSize = 12.sp, fontFamily = NotoSansBengali, color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    ManualDoneButton(item, routineVm, onDismiss)
                }
            } else {
                // ── Progress indicator (কতগুলো item পাওয়া গেছে) ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                    Text(
                        "${matchedStudy.size}টি পড়া পাওয়া গেছে",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray, fontFamily = NotoSansBengali
                    )
                }

                // ── Study content list — scroll করে পড়া যাবে ──
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(matchedStudy, key = { it.id ?: it.hashCode().toString() }) { study ->
                        StudyContentCard(study)
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                Divider(color = Color(0xFFF1F5F9))

                // ── নিচে: পড়া শেষ করার বাটন ──
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    if (item.done) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(GreenMint.copy(alpha = 0.12f)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = GreenMint)
                            Text("আজকের জন্য সম্পন্ন ✅", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenMint, fontFamily = NotoSansBengali)
                        }
                    } else {
                        ManualDoneButton(item, routineVm, onDismiss)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "পড়া শেষ হলে উপরের বাটনে ট্যাপ করো",
                            fontSize = 11.sp, color = Color.LightGray, fontFamily = NotoSansBengali,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualDoneButton(
    item: RoutineItem,
    routineVm: RoutineViewModel,
    onDismiss: () -> Unit
) {
    Button(
        onClick = {
            routineVm.markDone(item.id)
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GreenMint)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("পড়া শেষ করেছি", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = White, fontFamily = NotoSansBengali)
    }
}

@Composable
private fun StudyContentCard(study: StudyItem) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FAFC))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.MenuBook, null, tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
            Text(
                study.question ?: "",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate800,
                fontFamily = NotoSansBengali,
                modifier = Modifier.weight(1f)
            )
        }
        val answerText = study.answer ?: study.correct
        if (!answerText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                answerText,
                fontSize = 13.sp, color = Color(0xFF475569), fontFamily = NotoSansBengali,
                maxLines = if (expanded) Int.MAX_VALUE else 4
            )
        }
        if (!study.explanation.isNullOrBlank() && expanded) {
            Spacer(Modifier.height(8.dp))
            Divider(color = Color(0xFFE2E8F0))
            Spacer(Modifier.height(8.dp))
            Text(
                "💡 " + study.explanation,
                fontSize = 12.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali
            )
        }
        val canExpand = (study.explanation?.isNotBlank() == true) || (answerText?.length ?: 0) > 140
        if (canExpand) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (expanded) "সংক্ষেপে দেখাও ▲" else "বিস্তারিত দেখাও ▼",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo, fontFamily = NotoSansBengali,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = !expanded }
            )
        }
    }
}
