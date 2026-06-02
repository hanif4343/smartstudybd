package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.unit.*
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.ui.shared.*
import com.hanif.smartstudy.ui.shared.SubjectListSkeleton
import com.hanif.smartstudy.ui.shared.ErrorState
import com.hanif.smartstudy.ui.shared.EmptyState
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// subject icon map
private val subjectIcons = mapOf(
    "বাংলা"              to "📝",
    "ইংরেজি"             to "🔤",
    "গণিত"               to "🔢",
    "বিজ্ঞান"            to "🔬",
    "সাধারণ জ্ঞান"       to "🌍",
    "তথ্য ও যোগাযোগ"    to "💻",
    "ইতিহাস"             to "📜",
    "ভূগোল"              to "🗺",
    "পদার্থবিজ্ঞান"      to "⚛️",
    "রসায়ন"              to "🧪",
    "জীববিজ্ঞান"         to "🧬",
    "অর্থনীতি"           to "💰",
    "ধর্ম"               to "☪️"
)

private fun subjectIcon(name: String): String =
    subjectIcons.entries.firstOrNull { name.contains(it.key) }?.value ?: "📚"

// ─────────────────────────────────────────────────────────
// Subject List Screen
// ─────────────────────────────────────────────────────────
@Composable
fun SubjectListScreen(
    mode       : StudyMode,
    subjects   : List<SubjectEntry>,
    weakTopics : List<WeakTopic>,
    isLoading  : Boolean,
    error      : String?   = null,
    onSubject  : (String) -> Unit,
    onMockZone : () -> Unit
) {
    val modeLabel = when (mode) {
        StudyMode.QUIZ  -> "Quiz"
        StudyMode.QBANK -> "Question Bank"
        StudyMode.STUDY -> "Study"
    }
    val modeColor = when (mode) {
        StudyMode.QUIZ  -> Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))
        StudyMode.QBANK -> Brush.linearGradient(listOf(Color(0xFF0891B2), Color(0xFF0E7490)))
        StudyMode.STUDY -> Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857)))
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Box(
                Modifier.fillMaxWidth().background(modeColor)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column {
                    Text(modeLabel, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali)
                    Text("বিষয় বেছে নিন", fontSize = 12.sp, color = Color.White.copy(0.65f),
                        fontFamily = NotoSansBengali)
                }
            }
        }

        // Weak topics bar
        if (weakTopics.isNotEmpty()) {
            item {
                WeakTopicsBar(weakTopics)
            }
        }

        // Loading
        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo600)
                }
            }
        }

        // Empty state
        if (!isLoading && subjects.isEmpty()) {
            item {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Text(
                        text = "⚠️ ডেটা আসেনি",
                        fontSize = 15.sp,
                        fontFamily = NotoSansBengali,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFE53935)
                    )
                    if (error != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.Text(
                            text = error,
                            fontSize = 11.sp,
                            fontFamily = NotoSansBengali,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Subject cards
        itemsIndexed(subjects) { _, subject ->
            SubjectCard(subject = subject, onClick = { onSubject(subject.name) })
        }

        // Mock Zone button (quiz + qbank)
        if (mode != StudyMode.STUDY) {
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick  = onMockZone,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("🏆 বিষয় ভিত্তিক মক টেস্ট", fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(subject: SubjectEntry, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon circle
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEEF2FF)),
                contentAlignment = Alignment.Center
            ) { Text(subjectIcon(subject.name), fontSize = 22.sp) }

            Column(Modifier.weight(1f)) {
                Text(subject.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = SlateText, fontFamily = NotoSansBengali)
                Text("${subject.totalQ} প্রশ্ন", fontSize = 11.sp, color = MutedText,
                    fontFamily = NotoSansBengali)
                Spacer(Modifier.height(6.dp))
                // Progress bar
                Box(
                    Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xFFE2E8F0))
                ) {
                    Box(
                        Modifier.fillMaxWidth(subject.progressPct / 100f).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Indigo600, Color(0xFF818CF8))))
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text("${subject.progressPct}% সম্পন্ন", fontSize = 9.sp, color = MutedText,
                    fontFamily = NotoSansBengali)
            }

            Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun WeakTopicsBar(weakTopics: List<WeakTopic>) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
        border = BorderStroke(1.dp, Color(0xFFFECACA))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("🔁 দুর্বল টপিক", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF9F1239), fontFamily = NotoSansBengali,
                modifier = Modifier.padding(bottom = 8.dp))
            weakTopics.take(5).forEach { w ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(w.subTopic, fontSize = 12.sp, color = Color(0xFF7F1D1D),
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    Text("${w.wrongCount}× ভুল", fontSize = 10.sp, color = Color(0xFFEF4444),
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// SubTopic List Screen
// ─────────────────────────────────────────────────────────
@Composable
fun SubTopicListScreen(
    subject    : String,
    mode       : StudyMode,
    subTopics  : List<SubTopicEntry>,
    onSubTopic : (String) -> Unit,
    onBack     : () -> Unit
) {
    val isQBank = mode == StudyMode.QBANK

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            // Subject header
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        if (isQBank) Brush.linearGradient(listOf(Color(0xFF0891B2), Color(0xFF0E7490)))
                        else Brush.linearGradient(listOf(Indigo600, Color(0xFF7C3AED)))
                    )
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Column {
                    Text(subject, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontFamily = NotoSansBengali)
                    Text("${subTopics.size} টি অধ্যায়", fontSize = 11.sp, color = Color.White.copy(0.65f),
                        fontFamily = NotoSansBengali)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (isQBank) {
            // QBank Grid layout
            item {
                LazyVerticalGrid(
                    columns            = GridCells.Fixed(2),
                    modifier           = Modifier.heightIn(max = 2000.dp).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    items(subTopics) { st ->
                        QBankTopicCard(st = st, onClick = { onSubTopic(st.name) })
                    }
                }
            }
        } else {
            itemsIndexed(subTopics) { _, st ->
                SubTopicCard(st = st, onClick = { onSubTopic(st.name) })
            }
        }
    }
}

@Composable
private fun SubTopicCard(st: SubTopicEntry, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(st.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = SlateText, fontFamily = NotoSansBengali)
                    if (st.isWeak) {
                        Text("🔁", fontSize = 12.sp)
                    }
                }
                Text("${st.totalQ} প্রশ্ন  ·  ${st.progressPct}% সম্পন্ন", fontSize = 10.sp,
                    color = MutedText, fontFamily = NotoSansBengali)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xFFE2E8F0))
                ) {
                    Box(
                        Modifier.fillMaxWidth(st.progressPct / 100f).fillMaxHeight()
                            .background(Indigo600)
                    )
                }
            }
            Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun QBankTopicCard(st: SubTopicEntry, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("📋", fontSize = 20.sp)
            Text(st.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = SlateText, fontFamily = NotoSansBengali, maxLines = 2)
            Text("${st.totalQ} প্রশ্ন", fontSize = 10.sp, color = MutedText, fontFamily = NotoSansBengali)
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xFFE2E8F0))
            ) {
                Box(
                    Modifier.fillMaxWidth(st.progressPct / 100f).fillMaxHeight()
                        .background(Color(0xFF0891B2))
                )
            }
        }
    }
}
