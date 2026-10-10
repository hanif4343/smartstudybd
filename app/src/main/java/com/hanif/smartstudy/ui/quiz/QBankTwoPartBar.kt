package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * QBank প্রতিষ্ঠান/পদবীতে MCQ আর লিখিত দুটোই থাকলে — "দুই-ভাগ" সুইচার।
 * part: ০ = MCQ (ইন্টারেক্টিভ কুইজ), ১ = লিখিত (এক্সাম-পেপার ভিউ)।
 */
@Composable
fun QBankTwoPartBar(part: Int, mcqCount: Int, writtenCount: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple(0, "\uD83D\uDD18 MCQ ($mcqCount)", Color(0xFF0891B2)),
            Triple(1, "\u270D\uFE0F লিখিত ($writtenCount)", Color(0xFF7C3AED))
        ).forEach { (idx, label, color) ->
            val selected = part == idx
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) color else color.copy(alpha = 0.10f))
                    .border(1.dp, color.copy(alpha = if (selected) 1f else 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onSelect(idx) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (selected) Color.White else color)
            }
        }
    }
}
