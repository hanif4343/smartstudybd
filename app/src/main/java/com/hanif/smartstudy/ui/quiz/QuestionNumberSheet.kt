package com.hanif.smartstudy.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.data.model.AnswerState
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.ui.theme.NotoSansBengali

// ═══════════════════════════════════════════════════════════
// প্রশ্ন-নম্বর গ্রিড (Question Navigator)
// ৫০/১০০+ প্রশ্নের প্রশ্নপত্রে একটানা স্ক্রল না করে নম্বর ধরে সরাসরি লাফ দেওয়া যায়,
// আর এক নজরে দেখা যায় কোনটা সঠিক / ভুল / বাকি / সেভ করা।
// শুধু UI — ViewModel ও ডেটা অপরিবর্তিত; QuestionListScreen বর্তমান প্রশ্ন-লিস্ট থেকে cells বানিয়ে দেয়।
// ═══════════════════════════════════════════════════════════

internal enum class NumCellState { UNANSWERED, CORRECT, WRONG, ANSWERED }

internal data class NumCell(val state: NumCellState, val bookmarked: Boolean)

/** QuestionItem-এর বর্তমান উত্তর-অবস্থা থেকে গ্রিডের ঘরের অবস্থা */
internal fun QuestionItem.toNumCell(): NumCell {
    val st = when (val a = answerState) {
        is AnswerState.McqSelected       -> if (a.isCorrect) NumCellState.CORRECT else NumCellState.WRONG
        is AnswerState.WrittenSubmitted  -> if (a.isCorrect) NumCellState.CORRECT else NumCellState.WRONG
        is AnswerState.WrittenRecorded   -> NumCellState.ANSWERED
        else                             -> NumCellState.UNANSWERED
    }
    return NumCell(st, isBookmarked)
}

private val QnGreen = Color(0xFF16A34A)
private val QnRed   = Color(0xFFDC2626)
private val QnAmber = Color(0xFFF59E0B)

private fun qnBn(n: Int): String =
    n.toString().map { if (it.isDigit()) "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuestionNumberSheet(
    cells: List<NumCell>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val ok   = cells.count { it.state == NumCellState.CORRECT }
    val bad  = cells.count { it.state == NumCellState.WRONG }
    val rest = cells.count { it.state == NumCellState.UNANSWERED }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "সব প্রশ্ন (${qnBn(cells.size)}টি)", fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali
            )
            Text(
                "সঠিক ${qnBn(ok)} · ভুল ${qnBn(bad)} · বাকি ${qnBn(rest)}",
                fontSize = 12.sp, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cells.withIndex().chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (i, c) -> QnBox(i, c, i == current) { onPick(i) } }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(
                "সবুজ = সঠিক · লাল = ভুল · ধূসর = বাকি · ★ = সেভ করা",
                fontSize = 11.sp, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.QnBox(index: Int, cell: NumCell, isCurrent: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val (bg, fg) = when (cell.state) {
        NumCellState.CORRECT    -> QnGreen.copy(alpha = 0.18f) to QnGreen
        NumCellState.WRONG      -> QnRed.copy(alpha = 0.18f) to QnRed
        NumCellState.ANSWERED   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
        NumCellState.UNANSWERED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(shape)
            .background(bg)
            .then(if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            qnBn(index + 1), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            fontFamily = NotoSansBengali, color = fg, maxLines = 1, softWrap = false
        )
        if (cell.bookmarked) {
            Text(
                "★", fontSize = 9.sp, color = QnAmber,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 3.dp)
            )
        }
    }
}
