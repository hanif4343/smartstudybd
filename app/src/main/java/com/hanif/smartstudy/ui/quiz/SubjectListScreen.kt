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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
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

// ── একটা অধ্যায়ে (topic) আসলেই কোনো প্রশ্ন আছে কিনা — টপিক লিস্টে দেখানো এবং
// সাবজেক্ট কার্ডে "X টি অধ্যায়" গোনা, দুই জায়গাতেই এই একই শর্ত ব্যবহার করতে হবে,
// নাহলে সাবজেক্টে বলা টপিক-সংখ্যা আর আসল টপিক-লিস্টে দেখানো সংখ্যা বেমিল হয়ে যায়।
// Model Test এন্ট্রি totalQ দিয়ে গোনা হয় না (ওটার প্রশ্ন modelTestCount দিয়ে গোনা হয়),
// তাই সেটাকে সবসময় "content আছে" ধরা হয়। ──
private fun SubTopicEntry.hasQuestions(): Boolean = isModelTest || totalQ > 0

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
    onDeleteSubject: (name: String) -> Unit = {},
    // ── Review System (Admin-only) — subjectId ধরে {total, reviewed} % — খালি map হলে
    // কোনো badge দেখাবে না (non-admin/non-lazy স্ক্রিনে এটা পাস করা হয় না) ──
    reviewProgress: Map<String, com.hanif.smartstudy.data.remote.GasContentService.ReviewCount> = emptyMap(),
    // ── QBank-only ফিল্টার বার: পদবী/প্রতিষ্ঠান/সাল চিপ + নাম-সার্চ ──
    // Quiz/Study মোডে showQBankFilterBar=false থাকে বলে কিছুই render হয় না (আগের আচরণ অপরিবর্তিত)।
    showQBankFilterBar     : Boolean               = false,
    qbankFilterMode        : QBankFilterMode        = QBankFilterMode.DESIGNATION,
    onQBankFilterModeChange: (QBankFilterMode) -> Unit = {},
    qbankSearchQuery       : String                 = "",
    onQBankSearchQueryChange: (String) -> Unit      = {},
    // ── App feature request ৪: এডমিন ইমুজি পরিবর্তন — refType নির্ধারণ করে কোন
    // reference-টেবিলে সেভ হবে ("subjects" | "posts" | "institutions"), emojiOverrides
    // key = "$refType:${subject.subjectId}"। isAdmin true হলে আইকনে ট্যাপ করলে
    // ছোট ইমুজি-এডিট ডায়ালগ খোলে (দেখো নিচে AdminEmojiEditDialog)। ──
    emojiOverrides : Map<String, String> = emptyMap(),
    refType        : String              = "subjects",
    onEmojiChange  : (id: String, emoji: String) -> Unit = { _, _ -> },
    // ── Pull-to-refresh (সব ইউজার) + Admin "Force Full Resync" ──
    isRefreshing     : Boolean    = false,
    onRefresh        : () -> Unit = {},
    onForceFullResync: () -> Unit = {},
    forceResyncMsg   : String?    = null,
    onDismissForceResyncMsg: () -> Unit = {}
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

    // ── QBank-only সার্চ: শুধু নাম-লিস্ট (Designation/Institution/Year) ক্লায়েন্ট-সাইড
    // ফিল্টার করে — Rename/Delete ডায়ালগ পুরো (আন-ফিল্টার্ড) subjects লিস্টই ব্যবহার করে,
    // যাতে সার্চ করা অবস্থায়ও Admin অন্য আইটেম rename/delete করতে পারে ──
    val displaySubjects = if (showQBankFilterBar && qbankSearchQuery.isNotBlank()) {
        subjects.filter { it.name.contains(qbankSearchQuery, ignoreCase = true) }
    } else subjects

    // ── Admin মেনু (ক্রম ঠিক করুন / Rename / Delete) — সবগুলোই বর্তমান sheet
    // (mode অনুযায়ী Quiz/QBank/Study) এর subject-এর ওপরই কাজ করে, অন্য sheet ছোঁয় না ──
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // ── App feature request ৪: কোন subjectId-এর ইমুজি এডিট হচ্ছে (null মানে বন্ধ) ──
    var emojiEditTargetId by remember { mutableStateOf<String?>(null) }
    var emojiEditCurrentEmoji by remember { mutableStateOf("") }

    // ── Pull-to-refresh (সব ইউজার): নিচের দিকে টেনে ধরলে reference-ডেটা
    // (subjects/topics/tags/posts/institutions + QBank হলে exam_appearances) টাটকা
    // হয়ে যায় — দেখো QuizViewModel.refreshCurrentMode()। PullToRefreshBox material3
    // 1.3.0+ থেকে স্ট্যাবল (compose-bom 2024.09.00-এ আছে)। ──
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh    = onRefresh,
        modifier     = Modifier.fillMaxSize()
    ) {
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

        // ── QBank-only ফিল্টার বার: পদবী/প্রতিষ্ঠান/সাল চিপ + সার্চ ──
        if (showQBankFilterBar) {
            item {
                QBankFilterBar(
                    filterMode        = qbankFilterMode,
                    onFilterModeChange = onQBankFilterModeChange,
                    searchQuery       = qbankSearchQuery,
                    onSearchQueryChange = onQBankSearchQueryChange
                )
            }
        }

        // 🔬 DIAG ব্লক (পদবী/প্রতিষ্ঠান/সাল mode-count-sample) সরানো হলো — root cause
        // কনফার্ম হয়ে গেছে (পুরনো/আন-রিবিল্ট APK ছিল, কোড ঠিকই ছিল)। বিস্তারিত ইতিহাস
        // দেখো dev-notes/DIAGNOSTIC_NOTES.md। ──

        // ── ফোকাস মোড: "🎯 আজ ফোকাস" কার্ড — শুধু Study ট্যাবে, এখন সবার জন্য উন্মুক্ত ──
        if (mode == StudyMode.STUDY && com.hanif.smartstudy.focus.FocusModeConfig.ENABLED) {
            item {
                com.hanif.smartstudy.focus.FocusTodayCard(
                    subjects = listOf(com.hanif.smartstudy.focus.FocusModeConfig.TYPING_FOCUS_SUBJECT) + subjects.map { it.name }
                )
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
        if (!isLoading && displaySubjects.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (showQBankFilterBar && qbankSearchQuery.isNotBlank())
                                   "🔍 কিছু পাওয়া যায়নি" else "⚠️ ডেটা আসেনি",
                        fontSize = 15.sp,
                        fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            fontSize = 11.sp,
                            fontFamily = NotoSansBengali,
                            color = Color.Gray,
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
                    itemsIndexed(displaySubjects) { idx, subject ->
                        QBankSubjectCard(
                            subject = subject,
                            onClick = { onSubject(subject.name) },
                            reorderEnabled = isAdmin && isReorderMode,
                            isFirst = idx == 0,
                            isLast  = idx == displaySubjects.lastIndex,
                            onMoveUp   = { onMoveSubject(idx, idx - 1) },
                            onMoveDown = { onMoveSubject(idx, idx + 1) },
                            reviewPct = if (isAdmin) reviewProgress[subject.subjectId]?.pct else null,
                            emojiOverride = emojiOverrides["$refType:${subject.subjectId}"],
                            isAdmin = isAdmin && !isReorderMode,
                            onEmojiClick = {
                                emojiEditTargetId = subject.subjectId
                                emojiEditCurrentEmoji = emojiOverrides["$refType:${subject.subjectId}"] ?: ""
                            },
                            subLabelOverride = when (qbankFilterMode) {
                                QBankFilterMode.INSTITUTION -> "${subject.subTopics.size} টি পদবী"
                                QBankFilterMode.YEAR        -> "${subject.totalQ} টি প্রশ্ন"
                                QBankFilterMode.POST        -> "${subject.subTopics.size} টি প্রতিষ্ঠান"
                                QBankFilterMode.DESIGNATION -> "${subject.subTopics.size} টি প্রতিষ্ঠান"
                            }
                        )
                    }
                }
            }
        } else {
            itemsIndexed(displaySubjects) { idx, subject ->
                SubjectCard(
                    subject = subject,
                    onClick = { onSubject(subject.name) },
                    reorderEnabled = isAdmin && isReorderMode,
                    isFirst = idx == 0,
                    isLast  = idx == displaySubjects.lastIndex,
                    onMoveUp   = { onMoveSubject(idx, idx - 1) },
                    onMoveDown = { onMoveSubject(idx, idx + 1) },
                    reviewPct = if (isAdmin) reviewProgress[subject.subjectId]?.pct else null,
                    emojiOverride = emojiOverrides["$refType:${subject.subjectId}"],
                    isAdmin = isAdmin && !isReorderMode,
                    onEmojiClick = {
                        emojiEditTargetId = subject.subjectId
                        emojiEditCurrentEmoji = emojiOverrides["$refType:${subject.subjectId}"] ?: ""
                    }
                )
            }
        }

        // ── QBank subject list — banner ad ──
        if (mode == StudyMode.QBANK && displaySubjects.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                AdBannerView(adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_QBANK_SUBJECT)
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── Model Test — শুধু QBank মোডে ──
        if (mode == StudyMode.QBANK && displaySubjects.isNotEmpty()) {
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
    } // PullToRefreshBox বন্ধ

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
    // ── App feature request ৪: এডমিন ইমুজি এডিট ডায়ালগ — আইকনে ট্যাপ করলে খোলে ──
    emojiEditTargetId?.let { targetId ->
        AdminEmojiEditDialog(
            currentEmoji = emojiEditCurrentEmoji,
            onConfirm = { newEmoji ->
                onEmojiChange(targetId, newEmoji)
                emojiEditTargetId = null
            },
            onDismiss = { emojiEditTargetId = null }
        )
    }
}

@Composable
private fun AdminMenuButton(
    isReorderMode  : Boolean,
    onToggleReorder: () -> Unit,
    onRenameClick  : () -> Unit,
    onDeleteClick  : () -> Unit,
    onMoveClick    : (() -> Unit)? = null,
    // ── "🔄 Force Full Resync" — শুধু Admin-এর ড্রপডাউনে, দেখো
    // QuizViewModel.forceFullResync()/ContentRepository.forceFullResync() এর কমেন্ট ──
    onForceResyncClick: (() -> Unit)? = null
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
            if (onMoveClick != null) {
                DropdownMenuItem(
                    text = { Text("📦 Move to Subject", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = Color(0xFF0EA5E9)) },
                    onClick = { expanded = false; onMoveClick() }
                )
            }
            if (onForceResyncClick != null) {
                DropdownMenuItem(
                    text = { Text("🔄 Force Full Resync (Cache Clear)", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED)) },
                    onClick = { expanded = false; onForceResyncClick() }
                )
            }
            DropdownMenuItem(
                text = { Text("🗑️ Delete", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)) },
                onClick = { expanded = false; onDeleteClick() }
            )
        }
    }
}

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

/**
 * App feature request ৪: এডমিন সাবজেক্ট/টপিক/পদবী/প্রতিষ্ঠানের ইমুজি বদলানোর ছোট
 * ডায়ালগ — কার্ডের আইকনে ট্যাপ করলে খোলে। AlertDialog নিজেই ছোট (একটাই টেক্সট
 * ফিল্ড), তাই কিবোর্ড-ওভারল্যাপের ঝুঁকি নেই (দেখো AdminFieldEditDialog-এর #৬ ফিক্স,
 * সেটা অনেক বড় ডায়ালগের জন্য দরকার ছিল, এখানে না)।
 */
@Composable
private fun AdminEmojiEditDialog(
    currentEmoji: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentEmoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎨 ইমুজি বদলান", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("একটা ইমুজি টাইপ/পেস্ট করুন (খালি রাখলে ডিফল্ট আইকন ফিরে আসবে)",
                    fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text("ইমুজি", fontFamily = NotoSansBengali) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()); onDismiss() }) {
                Text("সেভ করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
    )
}

@Composable
private fun AdminDeletePickerDialog(
    title    : String,
    items    : List<String>,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected      by remember { mutableStateOf(items.firstOrNull() ?: "") }
    var confirmStep   by remember { mutableStateOf(false) }

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
private fun AdminMoveTopicPickerDialog(
    title           : String,
    items           : List<String>,
    otherSubjects   : List<String>,
    onConfirm       : (oldTopic: String, newSubject: String, newTopicName: String) -> Unit,
    onDismiss       : () -> Unit
) {
    var selectedTopic   by remember { mutableStateOf(items.firstOrNull() ?: "") }
    var selectedSubject by remember { mutableStateOf(otherSubjects.firstOrNull() ?: "") }
    var newTopicName    by remember { mutableStateOf(selectedTopic) }

    LaunchedEffect(selectedTopic) { newTopicName = selectedTopic }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0EA5E9)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("📦 কোন অধ্যায় (Topic) move করবেন?", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (items.isEmpty()) {
                    Text("⚠️ কোনো Topic পাওয়া যায়নি", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFFEF4444))
                }
                items.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedTopic = name }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedTopic == name, onClick = { selectedTopic = name },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0EA5E9)))
                        Text(name, fontFamily = NotoSansBengali, fontSize = 13.sp)
                    }
                }

                Divider(Modifier.padding(vertical = 4.dp))
                Text("➡️ কোন বিষয়ে (Subject) নিয়ে যাবেন?", fontFamily = NotoSansBengali, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (otherSubjects.isEmpty()) {
                    Text("⚠️ move করার মতো অন্য কোনো Subject নেই", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFFEF4444))
                }
                otherSubjects.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedSubject = name }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedSubject == name, onClick = { selectedSubject = name },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0EA5E9)))
                        Text(name, fontFamily = NotoSansBengali, fontSize = 13.sp)
                    }
                }

                Divider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = newTopicName, onValueChange = { newTopicName = it },
                    label = { Text("Destination-এ Topic-এর নাম", fontFamily = NotoSansBengali) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "ℹ️ ওই বিষয়ে আগে থেকেই এই নামে কোনো অধ্যায় থাকলে, দুটো এক হয়ে যাবে (merge) — নাহলে নতুন অধ্যায় হিসেবে যোগ হবে।",
                    fontFamily = NotoSansBengali, fontSize = 10.5.sp, color = Color(0xFF6B7280)
                )
            }
        },
        confirmButton = {
            val canConfirm = selectedTopic.isNotBlank() && selectedSubject.isNotBlank() && newTopicName.isNotBlank()
            TextButton(
                onClick = { if (canConfirm) { onConfirm(selectedTopic, selectedSubject, newTopicName); onDismiss() } },
                enabled = canConfirm
            ) { Text("📦 Move করুন", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold, color = Color(0xFF0EA5E9)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", fontFamily = NotoSansBengali) } }
    )
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
private fun QBankFilterBar(
    filterMode         : QBankFilterMode,
    onFilterModeChange  : (QBankFilterMode) -> Unit,
    searchQuery         : String,
    onSearchQueryChange : (String) -> Unit
) {
    val qbankAccent = Color(0xFF0891B2)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            data class ChipDef(val mode: QBankFilterMode, val label: String, val emoji: String)
            listOf(
                ChipDef(QBankFilterMode.DESIGNATION, "পদবী", "🧑‍💼"),
                ChipDef(QBankFilterMode.INSTITUTION, "প্রতিষ্ঠান", "🏢"),
                ChipDef(QBankFilterMode.YEAR, "সাল", "📅")
            ).forEach { chip ->
                val selected = filterMode == chip.mode
                Surface(
                    onClick  = { onFilterModeChange(chip.mode) },
                    shape    = RoundedCornerShape(20.dp),
                    color    = if (selected) qbankAccent else qbankAccent.copy(alpha = 0.08f),
                    border   = if (selected) null else BorderStroke(1.dp, qbankAccent.copy(alpha = 0.3f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(chip.emoji, fontSize = 12.sp)
                        Text(
                            chip.label, fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else qbankAccent
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    when (filterMode) {
                        QBankFilterMode.DESIGNATION -> "পদবী খুঁজুন..."
                        QBankFilterMode.INSTITUTION -> "প্রতিষ্ঠান খুঁজুন..."
                        QBankFilterMode.YEAR        -> "সাল খুঁজুন..."
                        QBankFilterMode.POST        -> "পদ খুঁজুন..."
                    },
                    fontFamily = NotoSansBengali, fontSize = 12.sp
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "সার্চ", tint = qbankAccent, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "মুছুন", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = NotoSansBengali, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = qbankAccent,
                unfocusedBorderColor = qbankAccent.copy(alpha = 0.3f)
            )
        )
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
    onMoveDown : () -> Unit = {},
    reviewPct : Int? = null,
    // ── App feature request ৪: এডমিন ইমুজি পরিবর্তন ──
    emojiOverride : String? = null,
    isAdmin       : Boolean = false,
    onEmojiClick  : () -> Unit = {}
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
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .then(
                        if (isAdmin && subject.subjectId.isNotBlank())
                            Modifier.clickable(onClick = onEmojiClick)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) { Text(emojiOverride ?: subjectIcon(subject.name), fontSize = 22.sp) }

            Column(Modifier.weight(1f)) {
                Text(subject.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = textColor, fontFamily = NotoSansBengali)
                Text("${subject.totalQ} প্রশ্ন", fontSize = 11.sp, color = mutedColor,
                    fontFamily = NotoSansBengali)
                Spacer(Modifier.height(6.dp))
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
                // ── FIX ("রিভিউ প্রগ্রেস বার নয়, % সংখ্যা দেখানোই ভাল — কনফিউশন থাকবে না"):
                // আগে এখানে student progress bar-এর মতোই আরেকটা রঙিন বার আঁকা হতো, যেটা
                // সহজেই ওপরের আসল progress bar-এর সাথে গুলিয়ে ফেলত। এখন শুধু % টেক্সট। ──
                if (reviewPct != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("✓ রিভিউ: $reviewPct%", fontSize = 9.sp, color = Color(0xFFB45309),
                        fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                }
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
private fun QBankSubjectCard(
    subject : SubjectEntry,
    onClick : () -> Unit,
    reorderEnabled : Boolean = false,
    isFirst : Boolean = false,
    isLast  : Boolean = false,
    onMoveUp   : () -> Unit = {},
    onMoveDown : () -> Unit = {},
    subLabelOverride: String? = null,
    reviewPct : Int? = null,
    // ── App feature request ৪: এডমিন ইমুজি পরিবর্তন ──
    emojiOverride : String? = null,
    isAdmin       : Boolean = false,
    onEmojiClick  : () -> Unit = {}
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
                        .background(Brush.linearGradient(listOf(qbankAccent.copy(0.16f), qbankAccent.copy(0.06f))))
                        .then(
                            if (isAdmin && subject.subjectId.isNotBlank())
                                Modifier.clickable(onClick = onEmojiClick)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) { Text(emojiOverride ?: subjectIcon(subject.name), fontSize = 20.sp) }
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
                Text(subLabelOverride ?: "${subject.subTopics.count { it.hasQuestions() }} টি অধ্যায়", fontSize = 10.sp, color = mutedColor,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium)
            }

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

            // ── FIX ("রিভিউ প্রগ্রেস বার নয়, % সংখ্যা দেখানোই ভাল") — বার সরিয়ে শুধু % টেক্সট ──
            if (reviewPct != null) {
                Text("✓ রিভিউ: $reviewPct%", fontSize = 9.sp, color = Color(0xFFB45309),
                    fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            }

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
    onModelTest : (String) -> Unit = {},
    isAdmin       : Boolean        = false,
    isReorderMode : Boolean        = false,
    isSavingOrder : Boolean        = false,
    orderSavedMsg : String?        = null,
    onToggleReorder : () -> Unit   = {},
    onMoveSubTopic  : (Int, Int) -> Unit = { _, _ -> },
    onRenameSubTopic: (old: String, new: String) -> Unit = { _, _ -> },
    onDeleteSubTopic: (name: String) -> Unit = {},
    otherSubjectsForMove : List<String> = emptyList(),
    onMoveSubTopicToSubject: (old: String, newSubject: String, newTopicName: String) -> Unit = { _, _, _ -> },
    reviewProgress: Map<String, com.hanif.smartstudy.data.remote.GasContentService.ReviewCount> = emptyMap()
) {
    val isQBank = mode == StudyMode.QBANK
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog   by remember { mutableStateOf(false) }

    // ── প্রশ্ন-শূন্য অধ্যায় সাধারণ ব্রাউজিং-এ কখনোই দেখানো হবে না (student ভুল করে
    // ফাঁকা টপিকে ঢুকবে না, আর হেডারের "X টি অধ্যায়" কাউন্টও লিস্টে যা দেখা যাচ্ছে
    // তার সাথে মিলবে)। তবে Admin যখন reorder মোডে আছে, তখন পুরো (ফাঁকাসহ) লিস্ট
    // দেখানো হয় — নাহলে খালি হয়ে যাওয়া টপিক আর সাজানো/দেখা যাবে না। Rename/Delete/
    // Move ডায়ালগও ইচ্ছাকৃতভাবে নিচে পুরো subTopics লিস্ট ব্যবহার করে, যাতে খালি
    // টপিক অ্যাডমিন ঠিক করতে/মুছতে পারে। ──
    val visibleSubTopics = if (isAdmin && isReorderMode) subTopics
                            else subTopics.filter { it.hasQuestions() }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
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
                        Text("${visibleSubTopics.size} টি অধ্যায়", fontSize = 11.sp, color = Color.White.copy(0.65f),
                            fontFamily = NotoSansBengali)
                    }
                    if (isAdmin) {
                        AdminMenuButton(
                            isReorderMode   = isReorderMode,
                            onToggleReorder = onToggleReorder,
                            onRenameClick   = { showRenameDialog = true },
                            onDeleteClick   = { showDeleteDialog = true },
                            onMoveClick     = if (otherSubjectsForMove.isNotEmpty()) { { showMoveDialog = true } } else null
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (isAdmin && isReorderMode) {
                OrderHintBar(isSaving = isSavingOrder, msg = orderSavedMsg)
                Spacer(Modifier.height(6.dp))
            }
            AdBannerView(adUnitId = com.hanif.smartstudy.util.AdManager.BANNER_QUIZ_LIST)
            Spacer(Modifier.height(6.dp))
        }

        val reorderEnabled = isAdmin && isReorderMode

        if (isQBank) {
            item {
                LazyVerticalGrid(
                    columns            = GridCells.Fixed(2),
                    modifier           = Modifier.heightIn(max = 4000.dp).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(visibleSubTopics) { idx, st ->
                        QBankTopicCard(
                            st = st,
                            onClick = { if (st.isModelTest) onModelTest(st.subject) else onSubTopic(st.name) },
                            reorderEnabled = reorderEnabled && !st.isModelTest,
                            isFirst = idx == 0,
                            isLast  = idx == visibleSubTopics.lastIndex,
                            onMoveUp   = { onMoveSubTopic(idx, idx - 1) },
                            onMoveDown = { onMoveSubTopic(idx, idx + 1) },
                            reviewPct = if (isAdmin) reviewProgress[st.topicId]?.pct else null
                        )
                    }
                }
            }
        } else {
            itemsIndexed(visibleSubTopics) { idx, st ->
                SubTopicCard(
                    st = st,
                    onClick = { if (st.isModelTest) onModelTest(st.subject) else onSubTopic(st.name) },
                    reorderEnabled = reorderEnabled && !st.isModelTest,
                    isFirst = idx == 0,
                    isLast  = idx == visibleSubTopics.lastIndex,
                    onMoveUp   = { onMoveSubTopic(idx, idx - 1) },
                    onMoveDown = { onMoveSubTopic(idx, idx + 1) },
                    reviewPct = if (isAdmin) reviewProgress[st.topicId]?.pct else null
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
    if (isAdmin && showMoveDialog) {
        AdminMoveTopicPickerDialog(
            title         = "$subject — অধ্যায় Move to Subject",
            items         = subTopics.filterNot { it.isModelTest }.map { it.name },
            otherSubjects = otherSubjectsForMove,
            onConfirm     = { oldTopic, newSubject, newTopicName -> onMoveSubTopicToSubject(oldTopic, newSubject, newTopicName) },
            onDismiss     = { showMoveDialog = false }
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
    onMoveDown : () -> Unit = {},
    reviewPct : Int? = null
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
                // ── FIX ("রিভিউ প্রগ্রেস বার নয়, % সংখ্যা দেখানোই ভাল") — বার সরিয়ে শুধু % টেক্সট ──
                if (reviewPct != null) {
                    Spacer(Modifier.height(3.dp))
                    Text("✓ রিভিউ: $reviewPct%", fontSize = 9.sp, color = Color(0xFFB45309),
                        fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
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
    onMoveDown : () -> Unit = {},
    reviewPct : Int? = null
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
        "written" -> Color(0xFF7C3AED)
        "mixed"   -> Color(0xFFEA580C)
        else      -> Color(0xFF0891B2)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${st.totalQ} প্রশ্ন", fontSize = 10.sp, color = mutedColor, fontFamily = NotoSansBengali)
                Text("·", fontSize = 10.sp, color = mutedColor)
                Text(typeIcon, fontSize = 10.sp)
                Text(typeLabel, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    color = accent, fontFamily = NotoSansBengali)
            }
            if (reviewPct != null) {
                Text("✓ রিভিউ: $reviewPct%", fontSize = 9.sp, color = Color(0xFFB45309),
                    fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
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
