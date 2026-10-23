package com.hanif.smartstudy.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.RecentActivityItem
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// ═══════════════════════════════════════════════════════════
// Home — "এখন কী করব?" অংশ (নতুন)
//   ১) চালিয়ে যান কার্ড   — সর্বশেষ কার্যক্রম (HomeUiState.recentActivity) থেকে, এক ট্যাপে ঠিক ট্যাবে
//   ২) দ্রুত অ্যাকশন      — ভুল রিভিউ · মডেল টেস্ট · সেভ করা · রুটিন (Tools শিটে লুকানো ছিল)
//   ৩) প্রশ্নব্যাংক সারি   — ক্যাটাগরি ট্যাপ করলে সরাসরি সেই ক্যাটাগরির প্রশ্নপত্র-তালিকা
// ViewModel/ডেটা মডেলে কোনো পরিবর্তন নেই — শুধু বিদ্যমান ডেটা আর কলব্যাক ব্যবহার হয়েছে।
// ═══════════════════════════════════════════════════════════

/** সর্বশেষ কার্যক্রম থেকে "চালিয়ে যান"। কার্যক্রম না থাকলে "শুরু করুন" (প্রশ্নব্যাংকে নিয়ে যায়)। */
@Composable
internal fun HomeContinueCard(
    recent: List<RecentActivityItem>,
    onContinue: (RecentActivityItem) -> Unit,
    onStartFresh: () -> Unit
) {
    val last = recent.firstOrNull()
    val shape = RoundedCornerShape(22.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .clickable { if (last != null) onContinue(last) else onStartFresh() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            if (last != null) "সর্বশেষ যেখানে ছিলেন" else "শুরু করুন",
            fontSize = 11.sp, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            last?.title ?: "প্রথম প্রশ্নপত্র খুলুন",
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            last?.subtitle ?: "প্রশ্নব্যাংকে ক্যাটাগরি বেছে একটা প্রশ্নপত্র শুরু করুন",
            fontSize = 12.sp, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { if (last != null) onContinue(last) else onStartFresh() },
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                if (last != null) "চালিয়ে যান" else "শুরু করুন",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali
            )
        }
    }
}

/** চারটা হালকা-রঙের টাইল। রঙ alpha দিয়ে বানানো, তাই ডার্ক মোডেও মানিয়ে যায়। */
@Composable
internal fun HomeQuickActionsRow(
    onWrongReview: () -> Unit,
    onModelTest: () -> Unit,
    onSaved: () -> Unit,
    onRoutine: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QsTile("ভুল রিভিউ", Icons.Default.Cancel, Color(0xFFF59E0B), onWrongReview, Modifier.weight(1f))
        QsTile("মডেল টেস্ট", Icons.Default.Description, Color(0xFFEF4444), onModelTest, Modifier.weight(1f))
        QsTile("সেভ করা", Icons.Default.Bookmark, Color(0xFF8B5CF6), onSaved, Modifier.weight(1f))
        QsTile("রুটিন", Icons.Default.CalendarMonth, Color(0xFF22C55E), onRoutine, Modifier.weight(1f))
    }
}

@Composable
private fun QsTile(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center
        )
    }
}

/** প্রশ্নব্যাংকের ক্যাটাগরি (বিসিএস/প্রাথমিক/…) + Study — আড়াআড়ি স্ক্রলযোগ্য চিপ সারি। */
@Composable
internal fun HomeQBankCategoriesRow(
    categories: List<String>,
    onPick: (String) -> Unit,
    onOpenAll: () -> Unit,
    onOpenStudy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "প্রশ্নব্যাংক", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "সব দেখুন ›", fontSize = 12.sp, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenAll).padding(4.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { c -> QsChip(c, filled = false) { onPick(c) } }
            QsChip("Study", filled = true, onClick = onOpenStudy)
        }
    }
}

@Composable
private fun QsChip(text: String, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = NotoSansBengali,
        color = fg, maxLines = 1, softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
