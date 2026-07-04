package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.hanif.smartstudy.ui.ads.AdBannerView
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
    onMockZone : () -> Unit,
    // ── Admin: ইনলাইন ক্রম সাজানো ──
    isAdmin       : Boolean        = false,
    isReorderMode : Boolean        = false,
    isSavingOrder : Boolean        = false,
    orderSavedMsg : String?        = null,
    onToggleReorder: () -> Unit    = {},
    onMoveSubject  : (Int, Int) -> Unit = { _, _ -> }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(modeLabel, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali)
                        Text("বিষয় বেছে নিন", fontSize = 12.sp, color = Color.White.copy(0.65f),
                            fontFamily = NotoSansBengali)
                    }
                    if (isAdmin) {
                        ReorderToggleButton(isReorderMode = isReorderMode, onClick = onToggleReorder)
                    }
                }
            }
        }

        if (isAdmin && isReorderMode) {
            item { OrderHintBar(isSaving = isSavingOrder, msg = orderSavedMsg) }
        }

        // (দুর্বল টপিক শুধু Profile/Stats পেজে দেখাবে)

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
        itemsIndexed(subjects) { idx, subject ->
            SubjectCard(
                subject = subject,
                onClick = { onSubject(subject.name) },
                reorderEnabled = isAdmin && isReorderMode,
                isFirst = idx == 0,
                isLast  = idx == subjects.lastIndex,
                onMoveUp   = { onMoveSubject(idx, idx - 1) },
                onMoveDown = { onMoveSubject(idx, idx + 1) }
            )
        }

        // ── QBank subject list — banner ad (list এর শেষে, Mock button এর আগে) ──
        if (mode == StudyMode.QBANK && subjects.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                AdBannerView(adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_QBANK_SUBJECT)
                Spacer(Modifier.height(4.dp))
            }
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
private fun ReorderToggleButton(isReorderMode: Boolean, onClick: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = if (isReorderMode) Color.White else Color.White.copy(alpha = 0.18f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(if (isReorderMode) "✖️" else "🔢", fontSize = 13.sp)
            Text(
                if (isReorderMode) "শেষ" else "ক্রম সাজান",
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                color = if (isReorderMode) Color(0xFF4F46E5) else Color.White
            )
        }
    }
}

@Composable
private fun OrderHintBar(isSaving: Boolean, msg: String?) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape  = RoundedCornerShape(12.dp),
        color  = Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, Color(0xFFFDE68A))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    isSaving        -> "⏳ সংরক্ষণ হচ্ছে..."
                    msg != null     -> msg
                    else            -> "▲▼ বাটনে চেপে ক্রম সাজান — সাথে সাথেই সংরক্ষিত হবে"
                },
                fontSize = 11.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                color = when {
                    msg?.startsWith("❌") == true -> Color(0xFFB91C1C)
                    msg?.startsWith("✅") == true -> Color(0xFF166534)
                    else -> Color(0xFF92400E)
                },
                modifier = Modifier.weight(1f)
            )
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF92400E), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun SubjectCard(
    subject : SubjectEntry,
    onClick : () -> Unit,
    reorderEnabled : Boolean = false,
    isFirst : Boolean = false,
    isLast  : Boolean = false,
    onMoveUp   : () -> Unit = {},
    onMoveDown : () -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor    = MaterialTheme.colorScheme.onSurface
    val mutedColor   = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .then(if (!reorderEnabled) Modifier.clickable { onClick() } else Modifier),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = surfaceColor),
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Text(subjectIcon(subject.name), fontSize = 22.sp) }

            Column(Modifier.weight(1f)) {
                Text(subject.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = textColor, fontFamily = NotoSansBengali)
                Text("${subject.totalQ} প্রশ্ন", fontSize = 11.sp, color = mutedColor,
                    fontFamily = NotoSansBengali)
                Spacer(Modifier.height(6.dp))
                // Progress bar
                Box(
                    Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier.fillMaxWidth(subject.progressPct / 100f).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF22C55E), Color(0xFF4ADE80))))
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text("${subject.progressPct}% সম্পন্ন", fontSize = 9.sp, color = mutedColor,
                    fontFamily = NotoSansBengali)
            }

            if (reorderEnabled) {
                ReorderUpDownButtons(isFirst = isFirst, isLast = isLast, onMoveUp = onMoveUp, onMoveDown = onMoveDown)
            } else {
                Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ReorderUpDownButtons(
    isFirst : Boolean,
    isLast  : Boolean,
    onMoveUp   : () -> Unit,
    onMoveDown : () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick  = onMoveUp,
            enabled  = !isFirst,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp, null,
                tint = if (!isFirst) Indigo600 else Color(0xFFCBD5E1)
            )
        }
        IconButton(
            onClick  = onMoveDown,
            enabled  = !isLast,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                tint = if (!isLast) Indigo600 else Color(0xFFCBD5E1)
            )
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
    onBack     : () -> Unit,
    onModelTest : (String) -> Unit = {},   // "মডেল টেস্ট" ভার্চুয়াল কার্ডে ট্যাপ করলে — subject পাস হয়
    // ── Admin: ইনলাইন ক্রম সাজানো ──
    isAdmin       : Boolean        = false,
    isReorderMode : Boolean        = false,
    isSavingOrder : Boolean        = false,
    orderSavedMsg : String?        = null,
    onToggleReorder : () -> Unit   = {},
    onMoveSubTopic  : (Int, Int) -> Unit = { _, _ -> }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(subject, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali)
                        Text("${subTopics.size} টি অধ্যায়", fontSize = 11.sp, color = Color.White.copy(0.65f),
                            fontFamily = NotoSansBengali)
                    }
                    if (isAdmin) {
                        ReorderToggleButton(isReorderMode = isReorderMode, onClick = onToggleReorder)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (isAdmin && isReorderMode) {
                OrderHintBar(isSaving = isSavingOrder, msg = orderSavedMsg)
                Spacer(Modifier.height(6.dp))
            }
            // ── Banner Ad — subject header এর নিচে ──
            AdBannerView(adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_QUIZ_LIST)
            Spacer(Modifier.height(6.dp))
        }

        val reorderEnabled = isAdmin && isReorderMode

        if (isQBank) {
            // QBank Grid layout
            item {
                LazyVerticalGrid(
                    columns            = GridCells.Fixed(2),
                    modifier           = Modifier.heightIn(max = 4000.dp).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(subTopics) { idx, st ->
                        QBankTopicCard(
                            st = st,
                            onClick = { if (st.isModelTest) onModelTest(st.subject) else onSubTopic(st.name) },
                            reorderEnabled = reorderEnabled && !st.isModelTest,
                            isFirst = idx == 0,
                            isLast  = idx == subTopics.lastIndex,
                            onMoveUp   = { onMoveSubTopic(idx, idx - 1) },
                            onMoveDown = { onMoveSubTopic(idx, idx + 1) }
                        )
                    }
                }
            }
        } else {
            itemsIndexed(subTopics) { idx, st ->
                SubTopicCard(
                    st = st,
                    onClick = { if (st.isModelTest) onModelTest(st.subject) else onSubTopic(st.name) },
                    reorderEnabled = reorderEnabled && !st.isModelTest,
                    isFirst = idx == 0,
                    isLast  = idx == subTopics.lastIndex,
                    onMoveUp   = { onMoveSubTopic(idx, idx - 1) },
                    onMoveDown = { onMoveSubTopic(idx, idx + 1) }
                )
            }
        }
    }
}

@Composable
private fun SubTopicCard(
    st : SubTopicEntry,
    onClick : () -> Unit,
    reorderEnabled : Boolean = false,
    isFirst : Boolean = false,
    isLast  : Boolean = false,
    onMoveUp   : () -> Unit = {},
    onMoveDown : () -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor    = MaterialTheme.colorScheme.onSurface
    val mutedColor   = MaterialTheme.colorScheme.onSurfaceVariant

    if (st.isModelTest) {
        Card(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onClick() },
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF059669).copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🏆", fontSize = 20.sp)
                Column(Modifier.weight(1f)) {
                    Text(st.name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF059669), fontFamily = NotoSansBengali)
                    Text("${st.modelTestCount}টি টেস্ট · পূর্ণমান", fontSize = 10.sp,
                        color = mutedColor, fontFamily = NotoSansBengali)
                }
                Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFF059669),
                    modifier = Modifier.size(12.dp))
            }
        }
        return
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .then(if (!reorderEnabled) Modifier.clickable { onClick() } else Modifier),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = surfaceColor),
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
                        color = textColor, fontFamily = NotoSansBengali)
                    // review symbol সরানো হয়েছে
                }
                Text("${st.totalQ} প্রশ্ন  ·  ${st.progressPct}% সম্পন্ন", fontSize = 10.sp,
                    color = mutedColor, fontFamily = NotoSansBengali)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier.fillMaxWidth(st.progressPct / 100f).fillMaxHeight()
                            .background(Color(0xFF22C55E))
                    )
                }
            }
            if (reorderEnabled) {
                ReorderUpDownButtons(isFirst = isFirst, isLast = isLast, onMoveUp = onMoveUp, onMoveDown = onMoveDown)
            } else {
                Icon(Icons.Default.ArrowForwardIos, null, tint = mutedColor,
                    modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun QBankTopicCard(
    st : SubTopicEntry,
    onClick : () -> Unit,
    reorderEnabled : Boolean = false,
    isFirst : Boolean = false,
    isLast  : Boolean = false,
    onMoveUp   : () -> Unit = {},
    onMoveDown : () -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor    = MaterialTheme.colorScheme.onSurface
    val mutedColor   = MaterialTheme.colorScheme.onSurfaceVariant

    if (st.isModelTest) {
        Card(
            modifier  = Modifier.fillMaxWidth().clickable { onClick() },
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF059669).copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🏆", fontSize = 20.sp)
                Text(st.name, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF059669), fontFamily = NotoSansBengali, maxLines = 2)
                Text("${st.modelTestCount}টি টেস্ট · পূর্ণমান", fontSize = 10.sp,
                    color = mutedColor, fontFamily = NotoSansBengali)
            }
        }
        return
    }

    Card(
        modifier  = Modifier.fillMaxWidth()
            .then(if (!reorderEnabled) Modifier.clickable { onClick() } else Modifier),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📋", fontSize = 20.sp, modifier = Modifier.weight(1f))
                if (reorderEnabled) {
                    IconButton(
                        onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, null,
                            modifier = Modifier.size(18.dp), tint = if (!isFirst) Indigo600 else Color(0xFFCBD5E1))
                    }
                    IconButton(
                        onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, null,
                            modifier = Modifier.size(18.dp), tint = if (!isLast) Indigo600 else Color(0xFFCBD5E1))
                    }
                }
            }
            Text(st.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = textColor, fontFamily = NotoSansBengali, maxLines = 2)
            Text("${st.totalQ} প্রশ্ন", fontSize = 10.sp, color = mutedColor, fontFamily = NotoSansBengali)
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier.fillMaxWidth(st.progressPct / 100f).fillMaxHeight()
                        .background(Color(0xFF22C55E))
                )
            }
        }
    }
}
