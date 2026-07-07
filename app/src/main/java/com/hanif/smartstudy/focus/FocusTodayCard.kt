package com.hanif.smartstudy.focus

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
//  FocusTodayCard — Study ট্যাবের উপরে "🎯 আজ ফোকাস" কার্ড
// ───────────────────────────────────────────────────────────────────
//  সম্পূর্ণ self-contained: নিজের DataStore state নিজে collect করে,
//  তাই SubjectListScreen.kt-এ শুধু একটা ২-৩ লাইনের hook বসালেই চলে।
//  আপাতত শুধু Admin-দের জন্য (FocusModeConfig.ADMIN_ONLY অনুযায়ী কলার
//  নিজেই চেক করে এই কার্ড রেন্ডার করবে কিনা)।
// ═══════════════════════════════════════════════════════════════════

private val FocusPrimary = Color(0xFF4F46E5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTodayCard(subjects: List<String>) {
    val context = LocalContext.current
    val store   = remember { FocusModeStore(context) }
    val scope   = rememberCoroutineScope()

    val focusState by store.stateFlow.collectAsState(initial = FocusModeState())
    var showPicker by remember { mutableStateOf(false) }

    val active = focusState.isEffectivelyActive()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = if (active)
                       CardDefaults.cardColors(containerColor = FocusPrimary.copy(alpha = 0.10f))
                   else
                       CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(1.2.dp, FocusPrimary.copy(alpha = if (active) 0.5f else 0.25f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎯", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "আজ ফোকাস",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = NotoSansBengali,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                val subLabel = if (active) {
                    val d = focusState.daysUntilExam()
                    val dayTxt = when {
                        d <= 0 -> "আজই পরীক্ষা"
                        d == 1 -> "আগামীকাল পরীক্ষা"
                        else   -> "$d দিন পরে পরীক্ষা"
                    }
                    "${focusState.subject} • $dayTxt"
                } else {
                    "একটা সাবজেক্ট বেছে নিয়ে ফোকাস মোড চালু করুন"
                }
                Text(
                    subLabel,
                    fontSize   = 11.sp,
                    fontFamily = NotoSansBengali,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(if (active) "চালু ✓" else "চালু করুন ›", fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = FocusPrimary, fontFamily = NotoSansBengali)
        }
    }

    if (showPicker) {
        FocusSubjectPickerSheet(
            subjects   = subjects,
            focusState = focusState,
            onActivate = { subject, examDateMillis ->
                scope.launch { store.activate(subject, examDateMillis) }
                showPicker = false
            },
            onDeactivate = {
                scope.launch { store.deactivate() }
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusSubjectPickerSheet(
    subjects     : List<String>,
    focusState   : FocusModeState,
    onActivate   : (subject: String, examDateMillis: Long) -> Unit,
    onDeactivate : () -> Unit,
    onDismiss    : () -> Unit
) {
    val context     = LocalContext.current
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedSubject by remember {
        mutableStateOf(focusState.subject.ifBlank { subjects.firstOrNull() ?: "" })
    }
    var selectedDateMillis by remember {
        mutableStateOf(
            if (focusState.examDateMillis > 0L) focusState.examDateMillis
            else FocusModeStore.todayStartMillis()
        )
    }
    var customPicked by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    fun openCustomDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                selectedDateMillis = picked.timeInMillis
                customPicked = true
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "🎯 আজ ফোকাস — সাবজেক্ট ও পরীক্ষার তারিখ",
                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali
            )
            Text(
                "পরীক্ষা কাছে থাকা অবস্থায় শুধু এই সাবজেক্টে মনোযোগ রাখতে অ্যাপ আপনাকে মনে করিয়ে দেবে।",
                fontSize = 12.sp, fontFamily = NotoSansBengali,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("সাবজেক্ট বেছে নিন", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(subjects) { subj ->
                    val isSel = subj == selectedSubject
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSubject = subj }
                            .background(
                                if (isSel) FocusPrimary.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isSel) "✅ " else "▫️ ", fontSize = 14.sp)
                        Text(subj, fontSize = 13.sp, fontFamily = NotoSansBengali,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Text("পরীক্ষার তারিখ", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val todayMs    = FocusModeStore.todayStartMillis()
                val tomorrowMs = FocusModeStore.tomorrowStartMillis()

                OutlinedButton(
                    onClick  = { selectedDateMillis = todayMs; customPicked = false },
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (!customPicked && selectedDateMillis == todayMs) FocusPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border   = BorderStroke(
                        1.dp,
                        if (!customPicked && selectedDateMillis == todayMs) FocusPrimary else Color.Gray.copy(alpha = 0.4f)
                    )
                ) { Text("আজ", fontFamily = NotoSansBengali) }

                OutlinedButton(
                    onClick  = { selectedDateMillis = tomorrowMs; customPicked = false },
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (!customPicked && selectedDateMillis == tomorrowMs) FocusPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border   = BorderStroke(
                        1.dp,
                        if (!customPicked && selectedDateMillis == tomorrowMs) FocusPrimary else Color.Gray.copy(alpha = 0.4f)
                    )
                ) { Text("আগামীকাল", fontFamily = NotoSansBengali) }

                OutlinedButton(
                    onClick = { openCustomDatePicker() },
                    colors  = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (customPicked) FocusPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border  = BorderStroke(1.dp, if (customPicked) FocusPrimary else Color.Gray.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (customPicked) dateFmt.format(selectedDateMillis) else "কাস্টম",
                        fontFamily = NotoSansBengali)
                }
            }

            Spacer(Modifier.height(2.dp))

            Button(
                onClick  = { if (selectedSubject.isNotBlank()) onActivate(selectedSubject, selectedDateMillis) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = FocusPrimary),
                enabled  = selectedSubject.isNotBlank()
            ) {
                Text("🎯 ফোকাস মোড চালু করুন", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontFamily = NotoSansBengali)
            }

            if (focusState.enabled) {
                Button(
                    onClick  = onDeactivate,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("🔴 ফোকাস মোড বন্ধ করো", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, fontFamily = NotoSansBengali)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
