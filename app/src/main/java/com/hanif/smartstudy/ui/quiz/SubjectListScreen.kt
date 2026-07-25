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
import com.hanif.smartstudy.ui.theme.AppTheme
import com.hanif.smartstudy.ui.theme.LocalAppTheme
import com.hanif.smartstudy.ui.theme.NordicSageTint
import com.hanif.smartstudy.ui.theme.NordicBlueTint
import com.hanif.smartstudy.ui.theme.NordicClayTint
import com.hanif.smartstudy.ui.theme.NordicInk
import com.hanif.smartstudy.ui.theme.NordicMuted

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
    onModelTestZone : () -> Unit = {},
    // ── Admin: ইনলাইন ক্রম সাজানো ──
    isAdmin       : Boolean        = false,
    isReorderMode : Boolean        = false,
    isSavingOrder : Boolean        = false,
    orderSavedMsg : String?        = null,
    onToggleReorder: () -> Unit    = {},
    onMoveSubject  : (Int, Int) -> Unit = { _, _ -> },
    onRenameSubject: (old: String, new: String) -> Unit = { _, _ -> },
    onDeleteSubject: (name: String) -> Unit = {}
) {
    val modeLabel = when (mode) {
        StudyMode.QUIZ  -> "Quiz"
        StudyMode.QBANK -> "Question Bank"
        StudyMode.STUDY -> "Study"
    }

    // ── Nordic Pastel থিম চালু থাকলে হেডার vivid gradient না হয়ে
    //    soft pastel wash + গাঢ় ইঙ্ক টেক্সট হয় (স্ক্রিনশটের "ক বিভাগ" বার-এর মতো) ──
    val isNordic  = LocalAppTheme.current.value == AppTheme.NORDIC
    val modeColor = if (isNordic) {
        val flat = when (mode) {
            StudyMode.QUIZ  -> NordicSageTint
            StudyMode.QBANK -> NordicBlueTint
            StudyMode.STUDY -> NordicClayTint
        }
        Brush.linearGradient(listOf(flat, flat))
    } else when (mode) {
        StudyMode.QUIZ  -> Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)))
        StudyMode.QBANK -> Brush.linearGradient(listOf(Color(0xFF0891B2), Color(0xFF0E7490)))
        StudyMode.STUDY -> Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857)))
    }
    val headerTextColor = if (isNordic) NordicInk else Color.White
    val headerSubTextColor = if (isNordic) NordicMuted else Color.White.copy(0.65f)

    // ── Admin মেনু (ক্রম ঠিক করুন / Rename / Delete) — সবগুলোই বর্তমান sheet
    // (mode অনুযায়ী Quiz/QBank/Study) এর subject-এর ওপরই কাজ করে, অন্য sheet ছোঁয় না ──
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                            color = headerTextColor, fontFamily = NotoSansBengali)
                        Text("বিষয় বেছে নিন", fontSize = 12.sp, color = headerSubTextColor,
                            fontFamily = NotoSansBengali)
                    }
                    if (isAdmin) {
                        AdminMenuButton(
                            isReorderMode   = isReorderMode,
                            onToggleReorder = onToggleReorder,
                            onRenameClick   = { showRenameDialog = true },
                            onDeleteClick   = { showDeleteDialog = true }
                        )
                    }
                }
            }
        }

        if (isAdmin && isReorderMode) {
            item { OrderHintBar(isSaving = isSavingOrder, msg = orderSavedMsg) }
        }

        // ── ফোকাস মোড: "🎯 আজ ফোকাস" কার্ড — শুধু Study ট্যাবে, এখন সবার জন্য উন্মুক্ত ──
        // (টাইপিং প্র্যাকটিসও এই একই সাবজেক্ট-তালিকায় একটা এন্ট্রি হিসেবে আছে, যাতে একই
        // ফোকাস-মোড সেকশন ও নোটিফিকেশন পাইপলাইন টাইপিং-এর জন্যও ব্যবহার করা যায়)
        if (mode == StudyMode.STUDY && com.hanif.smartstudy.focus.FocusModeConfig.ENABLED) {
            item {
                com.hanif.smartstudy.focus.FocusTodayCard(
                    subjects = listOf(com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT) + subjects.map { it.name }
                )
            }
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

        // Subject cards — QBank এ ২-কলাম গ্রিড (সাবটপিক কাউন্ট দেখায়), Quiz/Study এ আগের মতো সিঙ্গল-কলাম লিস্ট
        if (mode == StudyMode.QBANK) {
            item {
                LazyVerticalGrid(
                    columns                = GridCells.Fixed(2),
                    modifier               = Modifier.heightIn(max = 4000.dp).padding(horizontal = 12.dp),
                    horizontalArrangement  = Arrangement.spacedBy(8.dp),
                    verticalArrangement    = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(subjects) { idx, subject ->
                        QBankSubjectCard(
                            subject = subject,
                            onClick = { onSubject(subject.name) },
                            reorderEnabled = isAdmin && isReorderMode,
                            isFirst = idx == 0,
                            isLast  = idx == subjects.lastIndex,
                            onMoveUp   = { onMoveSubject(idx, idx - 1) },
                            onMoveDown = { onMoveSubject(idx, idx + 1) }
                        )
                    }
                }
            }
        } else {
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
        }

        // ── QBank subject list — banner ad (list এর শেষে, Mock button এর আগে) ──
        if (mode == StudyMode.QBANK && subjects.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                AdBannerView(adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_QBANK_SUBJECT)
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── Mock Test বাটন এখান থেকে সরানো হয়েছে — এটা অলরেডি Home স্ক্রিনে আছে,
        //    তাই Quiz/QBank নেভিগেশনে এটা রিডান্ড্যান্ট ছিল। onMockZone প্যারামিটার
        //    signature-এ রাখা হলো (backward-compat), শুধু এখানে আর ব্যবহার হচ্ছে না।

        // ── Model Test — শুধু QBank মোডে (এন্ট্রি পয়েন্ট এখানেই, প্রশ্ন আসে Quiz sheet থেকে) —
        // Job ইউজারের জন্য সরাসরি জেনারেট-ফর্ম, Student ইউজারের জন্য আগে subject picker ──
        if (mode == StudyMode.QBANK && subjects.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick  = onModelTestZone,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                    border   = BorderStroke(1.4.dp, Color(0xFF059669))
                ) {
                    Text("🏆 মডেল টেস্ট", fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }

    if (isAdmin && showRenameDialog) {
        AdminRenamePickerDialog(
            title   = "$modeLabel বিষয় Rename",
            items   = subjects.map { it.name },
            onConfirm = { old, new -> onRenameSubject(old, new) },
            onDismiss = { showRenameDialog = false }
        )
    }
    if (isAdmin && showDeleteDialog) {
        AdminDeletePickerDialog(
            title   = "$modeLabel বিষয় Delete",
            items   = subjects.map { it.name },
            onConfirm = { name -> onDeleteSubject(name) },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun AdminMenuButton(
    isReorderMode  : Boolean,
    onToggleReorder: () -> Unit,
    onRenameClick  : () -> Unit,
    onDeleteClick  : () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape    = RoundedCornerShape(12.dp),
            color    = if (isReorderMode) Color.White else Color.White.copy(alpha = 0.18f),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("🛡️", fontSize = 13.sp)
                Text(
                    "Admin", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali,
                    color = if (isReorderMode) Color(0xFF4F46E5) else Color.White
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(if (isReorderMode) "✖️ ক্রম সাজানো শেষ করুন" else "🔢 ক্রম ঠিক করুন",
                        fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                },
                onClick = { expanded = false; onToggleReorder() }
            )
            DropdownMenuItem(
                text = { Text("✏️ Rename", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                onClick = { expanded = false; onRenameClick() }
            )
            DropdownMenuItem(
                text = { Text("🗑️ Delete", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)) },
                onClick = { expanded = false; onDeleteClick() }
            )
        }
    }
}

// ── বর্তমান sheet (Quiz/QBank/Study — যেটাতে ইউজার এখন আছে)-এর subject বা
// subtopic-এর মধ্য থেকে একটা বেছে নিয়ে নতুন নাম দেওয়ার ডায়ালগ। items সবসময়
// এই screen-এই দেখানো list থেকে আসে, তাই অন্য sheet-এর ডেটা কখনো দেখায় না। ──
@Composable
private fun AdminRenamePickerDialog(
    title    : String,
    items    : List<String>,
    onConfirm: (old: String, new: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(items.firstOrNull() ?: "") }
    var newName  by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("কোনটা Rename করবেন?", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (items.isEmpty()) {
                    Text("⚠️ কোনো আইটেম পাওয়া যায়নি", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFFEF4444))
                }
                items.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = name }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == name, onClick = { selected = name })
                        Text(name, fontFamily = NotoSansBengali, fontSize = 13.sp)
                    }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("নতুন নাম", fontFamily = NotoSansBengali) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val canConfirm = selected.isNotBlank() && newName.isNotBlank()
            TextButton(
                onClick = { if (canConfirm) { onConfirm(selected, newName); onDismiss() } },
                enabled = canConfirm
            ) { Text("Rename করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
    )
}

// ── একই রকম, শুধু rename এর বদলে ডিলিট — ধ্বংসাত্মক কাজ, তাই ২-ধাপে কনফার্ম করা হয়
// (লিস্ট থেকে বাছাই → শেষে হ্যাঁ/না)। আগে নাম টাইপ করে কনফার্ম করা লাগত, কিন্তু emoji-সহ
// নাম কিবোর্ড দিয়ে হুবহু টাইপ করা কঠিন ছিল বলে সেটা বাদ দেওয়া হয়েছে — এখন শুধু ট্যাপ। ──
@Composable
private fun AdminDeletePickerDialog(
    title    : String,
    items    : List<String>,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected      by remember { mutableStateOf(items.firstOrNull() ?: "") }
    var confirmStep   by remember { mutableStateOf(false) }   // false = লিস্ট থেকে বাছাই, true = শেষ নিশ্চিতকরণ

    if (!confirmStep) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF4444)) },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("⚠️ কোনটা ডিলিট করবেন? এর সব প্রশ্ন Sheet + Firebase থেকে চিরতরে মুছে যাবে!",
                        fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    if (items.isEmpty()) {
                        Text("⚠️ কোনো আইটেম পাওয়া যায়নি", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFFEF4444))
                    }
                    items.forEach { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = name }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == name, onClick = { selected = name },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444)))
                            Text(name, fontFamily = NotoSansBengali, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (selected.isNotBlank()) confirmStep = true },
                    enabled = selected.isNotBlank()
                ) { Text("ডিলিট করুন 🗑️", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
        )
    } else {
        // ── শেষ নিশ্চিতকরণ — টাইপ করার ঝামেলা নেই, শুধু ২টা বাটনে চাপ। emoji-সহ
        // নাম হুবহু টাইপ করতে গিয়ে আটকে যাওয়ার সমস্যা এড়াতে টাইপ-কনফার্ম বাদ দেওয়া হলো। ──
        AlertDialog(
            onDismissRequest = { confirmStep = false },
            title = { Text("⚠️ একদম নিশ্চিত?", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF4444)) },
            text = {
                Text(
                    "\"$selected\" — এর সব প্রশ্ন চিরতরে মুছে যাবে। এই কাজ ফেরানো যাবে না!",
                    fontFamily = NotoSansBengali, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(selected); onDismiss() }) {
                    Text("হ্যাঁ, ডিলিট করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF4444))
                }
            },
            dismissButton = { TextButton(onClick = { confirmStep = false }) { Text("না, বাতিল", fontFamily = NotoSansBengali) } }
        )
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

// ── QBank Subject Card — গ্রিড (২-কলাম) এ দেখানো হয়, সাবজেক্টের ভিতরে কতগুলো
// অধ্যায় (সাবটপিক) আছে সেটা দেখায় — মোট প্রশ্ন সংখ্যা না ──
@Composable
private fun QBankSubjectCard(
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
    val qbankAccent  = Color(0xFF0891B2)

    Card(
        modifier  = Modifier.fillMaxWidth()
            .then(if (!reorderEnabled) Modifier.clickable { onClick() } else Modifier),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(2.dp),
        border    = BorderStroke(1.dp, qbankAccent.copy(alpha = 0.14f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                        .background(Brush.linearGradient(listOf(qbankAccent.copy(0.16f), qbankAccent.copy(0.06f)))),
                    contentAlignment = Alignment.Center
                ) { Text(subjectIcon(subject.name), fontSize = 20.sp) }
                Spacer(Modifier.weight(1f))
                if (!reorderEnabled) {
                    Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(12.dp))
                }
            }

            Text(subject.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                color = textColor, fontFamily = NotoSansBengali, maxLines = 2)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("📂", fontSize = 10.sp)
                Text("${subject.subTopics.size} টি অধ্যায়", fontSize = 10.sp, color = mutedColor,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium)
            }

            // Progress bar
            Box(
                Modifier.fillMaxWidth().height(5.dp)
                    .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier.fillMaxWidth(subject.progressPct / 100f).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(qbankAccent, Color(0xFF22D3EE))))
                )
            }
            Text("${subject.progressPct}% সম্পন্ন", fontSize = 9.sp, color = mutedColor,
                fontFamily = NotoSansBengali)

            if (reorderEnabled) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, null,
                            modifier = Modifier.size(18.dp), tint = if (!isFirst) qbankAccent else Color(0xFFCBD5E1))
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, null,
                            modifier = Modifier.size(18.dp), tint = if (!isLast) qbankAccent else Color(0xFFCBD5E1))
                    }
                }
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
    onMoveSubTopic  : (Int, Int) -> Unit = { _, _ -> },
    onRenameSubTopic: (old: String, new: String) -> Unit = { _, _ -> },
    onDeleteSubTopic: (name: String) -> Unit = {}
) {
    val isQBank = mode == StudyMode.QBANK
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                        AdminMenuButton(
                            isReorderMode   = isReorderMode,
                            onToggleReorder = onToggleReorder,
                            onRenameClick   = { showRenameDialog = true },
                            onDeleteClick   = { showDeleteDialog = true }
                        )
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

    if (isAdmin && showRenameDialog) {
        AdminRenamePickerDialog(
            title   = "$subject — অধ্যায় Rename",
            items   = subTopics.filterNot { it.isModelTest }.map { it.name },
            onConfirm = { old, new -> onRenameSubTopic(old, new) },
            onDismiss = { showRenameDialog = false }
        )
    }
    if (isAdmin && showDeleteDialog) {
        AdminDeletePickerDialog(
            title   = "$subject — অধ্যায় Delete",
            items   = subTopics.filterNot { it.isModelTest }.map { it.name },
            onConfirm = { name -> onDeleteSubTopic(name) },
            onDismiss = { showDeleteDialog = false }
        )
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

    val accent = when (st.questionTypeLabel) {
        "written" -> Color(0xFF7C3AED)   // বেগুনি — Written
        "mixed"   -> Color(0xFFEA580C)   // কমলা — Mixed (MCQ + Written দুটোই)
        else      -> Color(0xFF0891B2)   // সায়ান — MCQ (ডিফল্ট)
    }
    val typeIcon  = when (st.questionTypeLabel) { "written" -> "✍️"; "mixed" -> "🔀"; else -> "🔘" }
    val typeLabel = when (st.questionTypeLabel) { "written" -> "Written"; "mixed" -> "মিশ্র"; else -> "MCQ" }

    Card(
        modifier  = Modifier.fillMaxWidth()
            .then(if (!reorderEnabled) Modifier.clickable { onClick() } else Modifier),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (reorderEnabled) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(Icons.Default.ArrowForwardIos, null, tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(11.dp))
                }
            }
            Text(st.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = textColor, fontFamily = NotoSansBengali, maxLines = 2)
            // ── প্রশ্ন সংখ্যার পাশেই প্রশ্নের ধরন (MCQ / Written / মিশ্র) প্লেইন টেক্সট আকারে ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${st.totalQ} প্রশ্ন", fontSize = 10.sp, color = mutedColor, fontFamily = NotoSansBengali)
                Text("·", fontSize = 10.sp, color = mutedColor)
                Text(typeIcon, fontSize = 10.sp)
                Text(typeLabel, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    color = accent, fontFamily = NotoSansBengali)
            }
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
