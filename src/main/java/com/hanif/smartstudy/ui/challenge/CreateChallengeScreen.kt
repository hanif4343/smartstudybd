package com.hanif.smartstudy.ui.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items // সঠিক items লুপের জন্য এই ইমপোর্টটি প্রয়োজন
import androidx.compose.ui.unit.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import coil.compose.rememberAsyncImagePainter
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.ChallengeUiState
import com.hanif.smartstudy.viewmodel.ChallengeViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChallengeScreen(state: ChallengeUiState, vm: ChallengeViewModel) {
    BackHandler { vm.goHome() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("নতুন চ্যালেঞ্জ", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = vm::goHome) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier.fillMaxSize().padding(padding)
                .background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Step 1: Invite friends ──
            SectionHeader("👥 Step 1: বন্ধু যোগ করো")
            InviteSection(state = state, vm = vm)

            // ── Step 2: Subject ──
            SectionHeader("📚 Step 2: বিষয় বেছে নাও")
            SubjectSection(state = state, vm = vm)

            // ── Step 3: Config ──
            SectionHeader("⚙️ Step 3: পরীক্ষার নিয়ম")
            ConfigSection(state = state, vm = vm)

            // ── Step 4: XP Wager (ঐচ্ছিক) ──
            SectionHeader("💰 Step 4: XP বাজি (ঐচ্ছিক)")
            WagerSection(state = state, vm = vm)

            // Error
            state.error?.let { err ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF1F2)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 14.sp)
                    Text(err, fontSize = 12.sp, color = Color(0xFF991B1B), fontFamily = NotoSansBengali)
                }
            }

            // ── Ghost Mode toggle ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = if (state.isGhostMode) Color(0xFF4C1D95).copy(0.3f)
                                     else Color(0xFF1E1B4B).copy(0.3f)
                ),
                border   = BorderStroke(1.dp,
                    if (state.isGhostMode) Color(0xFF7C3AED) else Color.White.copy(0.15f))
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleGhostMode() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("👻", fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Ghost Mode", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.White, fontFamily = NotoSansBengali)
                        Text(
                            if (state.isGhostMode)
                                "তুমি আগে পরীক্ষা দেবে, score lock হবে — পরে বন্ধু দেবে"
                            else
                                "বন্ধু অনলাইনে না থাকলে Ghost Mode চালু করো",
                            fontSize = 10.sp, color = Color.White.copy(0.6f),
                            fontFamily = NotoSansBengali, lineHeight = 13.sp
                        )
                    }
                    Switch(
                        checked = state.isGhostMode,
                        onCheckedChange = { vm.toggleGhostMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor       = Color.White,
                            checkedTrackColor       = Color(0xFF7C3AED),
                            uncheckedThumbColor     = Color.White.copy(0.5f),
                            uncheckedTrackColor     = Color.White.copy(0.15f)
                        )
                    )
                }
            }

            // Create button
            Button(
                onClick  = if (state.isGhostMode) vm::createGhostChallenge else vm::createChallenge,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (state.isGhostMode) Color(0xFF7C3AED) else Color(0xFF4F46E5)
                ),
                enabled  = !state.isLoading && state.invitedUsers.isNotEmpty()
                           && state.selectedSubject.isNotBlank()
                           && (state.wagerXp == 0 || state.myCurrentXp >= state.wagerXp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (state.isGhostMode) "👻 Ghost Challenge শুরু করো!" else "🚀 চ্যালেঞ্জ পাঠাও!",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InviteSection(state: ChallengeUiState, vm: ChallengeViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Search input
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = state.phoneInput,
                    onValueChange = vm::onPhoneInput,
                    modifier      = Modifier.weight(1f),
                    label         = { Text("বন্ধুর ফোন নম্বর", fontFamily = NotoSansBengali) },
                    placeholder   = { Text("01XXXXXXXXX", color = Color(0xFFCBD5E1)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4F46E5))
                )
                IconButton(
                    onClick  = vm::searchUser,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF4F46E5))
                ) {
                    if (state.isSearching)
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Search, null, tint = Color.White)
                }
            }

            // ── Select User Dropdown Button ──
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick  = vm::loadAllUsers,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, Color(0xFF4F46E5))
                ) {
                    Icon(Icons.Default.Person, null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("👥 Select User", fontFamily = NotoSansBengali, fontSize = 13.sp,
                        color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (state.isLoadingUsers)
                        CircularProgressIndicator(color = Color(0xFF4F46E5), modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF4F46E5))
                }

                // Dropdown Menu
                DropdownMenu(
                    expanded         = state.showUserDropdown,
                    onDismissRequest = vm::dismissUserDropdown,
                    modifier         = Modifier.fillMaxWidth(0.92f)
                        .heightIn(max = 320.dp)
                        .background(Color.White)
                ) {
                    if (state.isLoadingUsers) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF4F46E5), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (state.allUsers.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("কোনো ব্যবহারকারী পাওয়া যায়নি", fontFamily = NotoSansBengali, fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                            onClick = {}
                        )
                    } else {
                        state.allUsers.forEach { user ->
                            val isSelected = state.selectedDropUser?.phone == user.phone
                            val alreadyAdded = state.invitedUsers.any { it.phone == user.phone }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Profile picture or initial
                                        Box(
                                            Modifier.size(36.dp).clip(CircleShape)
                                                .background(if (isSelected) Color(0xFF4F46E5) else Color(0xFFE0E7FF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!user.picture.isNullOrBlank()) {
                                                androidx.compose.foundation.Image(
                                                    painter = rememberAsyncImagePainter(user.picture),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    user.displayName().first().toString(),
                                                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) Color.White else Color(0xFF4F46E5)
                                                )
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(user.displayName(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                val cl = user.classLevel?.trim() ?: ""
                                                val ut = user.userType?.trim() ?: ""
                                                val label = when { cl.isNotBlank() -> "🎓 $cl"; ut.isNotBlank() -> "💼 $ut"; else -> "💼 User" }
                                                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                                                Text("⭐ ${user.xp} XP", fontSize = 10.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                                            }
                                        }
                                        // Challenge button
                                        if (!alreadyAdded) {
                                            SmallButton(
                                                text    = if (isSelected) "✓ যোগ করো" else "Challenge",
                                                color   = if (isSelected) Color(0xFF10B981) else Color(0xFF4F46E5),
                                                onClick = {
                                                    if (isSelected) {
                                                        vm.addInviteeFromDropdown(user)
                                                    } else {
                                                        vm.selectDropUser(user)
                                                    }
                                                }
                                            )
                                        } else {
                                            Box(
                                                Modifier.clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFDCFCE7))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("✓ Added", fontSize = 10.sp, color = Color(0xFF166534), fontFamily = NotoSansBengali)
                                            }
                                        }
                                    }
                                },
                                onClick = { vm.selectDropUser(user) }
                            )
                            // XP info row when selected
                            if (isSelected) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .background(Color(0xFFF5F3FF))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("🏆 XP: ${user.xp}", fontSize = 11.sp, color = Color(0xFF7C3AED), fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                                        val level = when {
                                            user.xp >= 500 -> "🔥 Expert"
                                            user.xp >= 200 -> "⚡ Intermediate"
                                            user.xp >= 50  -> "📗 Beginner"
                                            else           -> "🌱 Newbie"
                                        }
                                        Text(level, fontSize = 11.sp, color = Color(0xFF4F46E5), fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }

            // Search result
            state.searchResult?.let { user ->
                SearchResultCard(
                    user   = user,
                    canAdd = state.canAddOpponent,
                    onAdd  = vm::addInvitee
                )
            }
            state.searchError?.let { err ->
                Text("⚠️ $err", fontSize = 11.sp, color = Color(0xFFDC2626), fontFamily = NotoSansBengali)
            }

            // Invited list
            if (state.invitedUsers.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("${state.invitedUsers.size} জন যোগ করা হয়েছে", fontSize = 11.sp,
                    color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                state.invitedUsers.forEach { user ->
                    InvitedUserChip(user = user, onRemove = { vm.removeInvitee(user.phone ?: "") })
                }
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 10.sp, color = Color.White, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchResultCard(user: User, canAdd: Boolean, onAdd: () -> Unit) {
    val btnColor = if (canAdd) Color(0xFF10B981) else Color(0xFF94A3B8)  // সবুজ বা ধূসর
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (canAdd) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp,
                if (canAdd) Color(0xFFA7F3D0) else Color(0xFFCBD5E1),
                RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(btnColor),
                contentAlignment = Alignment.Center) {
                Text(user.displayName().first().toString(), fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(user.displayName(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (canAdd) Color(0xFF065F46) else Color(0xFF475569),
                    fontFamily = NotoSansBengali)
                Text(user.phone ?: "", fontSize = 10.sp,
                    color = if (canAdd) Color(0xFF047857) else Color(0xFF94A3B8))
                // শ্রেণি/group দেখাও
                val cl = user.classLevel?.trim() ?: ""
                val ut = user.userType?.trim() ?: ""
                val groupLabel = when {
                    cl.isNotBlank() -> "🎓 $cl"
                    ut.isNotBlank() -> "💼 $ut"
                    else            -> "💼 Job"
                }
                Text(groupLabel, fontSize = 10.sp,
                    color = if (canAdd) Color(0xFF059669) else Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
            }
            // Add বাটন — group mismatch হলে disabled + ধূসর
            IconButton(
                onClick  = { if (canAdd) onAdd() },
                enabled  = canAdd,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(btnColor)
            ) {
                Icon(
                    if (canAdd) Icons.Default.Add else Icons.Default.Close,
                    contentDescription = if (canAdd) "যোগ করুন" else "এই শ্রেণির সাথে challenge করা যাবে না",
                    tint   = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // Group mismatch হলে ছোট warning দেখাও
        if (!canAdd) {
            Text(
                "⚠️ ভিন্ন শ্রেণির শিক্ষার্থীকে challenge করা যাবে না",
                fontSize = 10.sp,
                color    = Color(0xFFDC2626),
                fontFamily = NotoSansBengali,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun InvitedUserChip(user: User, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF4F46E5)),
            contentAlignment = Alignment.Center) {
            Text(user.displayName().first().toString(), fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(user.displayName(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
            Text(user.phone ?: "", fontSize = 10.sp, color = Color(0xFF64748B))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectSection(state: ChallengeUiState, vm: ChallengeViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Step 1: Quiz / QBank ──
            StepLabel(step = "১", label = "উৎস বেছে নাও")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Quiz", "QBank").forEach { source ->
                    val selected = state.selectedSource == source
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { vm.onSourceSelect(source) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            source,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = NotoSansBengali
                        )
                    }
                }
            }

            // ── Step 2: Subject (visible after source selected) ──
            if (state.selectedSource.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StepLabel(step = "২", label = "বিষয় বেছে নাও")
                if (state.subjects.isEmpty()) {
                    Text("কোনো বিষয় পাওয়া যায়নি", fontSize = 11.sp,
                        color = Color(0xFF94A3B8), fontFamily = NotoSansBengali)
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(6.dp)
                    ) {
                        state.subjects.forEach { subject ->
                            val sel = state.selectedSubject == subject
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) Color(0xFF4F46E5) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { vm.onSubjectSelect(subject) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(subject, fontSize = 12.sp, fontFamily = NotoSansBengali,
                                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (sel) Color.White else Color(0xFF4F46E5))
                            }
                        }
                    }
                }
            }

            // ── Step 3: SubTopic (visible after subject selected) ──
            if (state.selectedSubject.isNotEmpty() && state.subTopics.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StepLabel(step = "৩", label = "টপিক বেছে নাও (ঐচ্ছিক)")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp)
                ) {
                    // "সব টপিক" chip
                    val allSel = state.selectedSubTopic.isEmpty()
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (allSel) Color(0xFF10B981) else Color(0xFFEEF2FF))
                            .clickable { vm.onSubTopicSelect("") }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text("সব টপিক", fontSize = 12.sp, fontFamily = NotoSansBengali,
                            fontWeight = if (allSel) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (allSel) Color.White else Color(0xFF4F46E5))
                    }
                    state.subTopics.forEach { sub ->
                        val sel = state.selectedSubTopic == sub
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { vm.onSubTopicSelect(sub) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(sub, fontSize = 12.sp, fontFamily = NotoSansBengali,
                                fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (sel) Color.White else Color(0xFF4F46E5))
                        }
                    }
                }
            }

            // ── Selected summary ──
            if (state.selectedSubject.isNotEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF0FDF4)).padding(10.dp)
                ) {
                    val subtopicLabel = if (state.selectedSubTopic.isEmpty()) "সব টপিক" else state.selectedSubTopic
                    Text(
                        "✅ ${state.selectedSource} › ${state.selectedSubject} › $subtopicLabel",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534), fontFamily = NotoSansBengali
                    )
                }
            }
        }
    }
}

@Composable
private fun StepLabel(step: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF4F46E5)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF374151), fontFamily = NotoSansBengali)
    }
}

@Composable
private fun ConfigSection(state: ChallengeUiState, vm: ChallengeViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Question count
            ConfigRow(
                label   = "প্রশ্ন সংখ্যা",
                value   = "${state.questionCount}টি",
                options = listOf(5, 10, 15, 20, 25, 30).map { "$it" },
                selected = "${state.questionCount}",
                onSelect = { vm.onQuestionCountChange(it.toInt()) }
            )
            // Auto time info
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("⏱ সময়সীমা (স্বয়ংক্রিয়)", fontSize = 12.sp,
                    color = Color(0xFF4F46E5), fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                Text("${state.questionCount} মিনিট", fontSize = 13.sp,
                    color = Color(0xFF4F46E5), fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF4F46E5), fontFamily = NotoSansBengali)
            }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(count = options.size) { idx ->
                val opt = options[idx]
                FilterChip(
                    selected = opt == selected || value.startsWith(opt.replace(" মি", "")),
                    onClick  = { onSelect(opt) },
                    label    = { Text(opt, fontFamily = NotoSansBengali, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun WagerSection(state: ChallengeUiState, vm: ChallengeViewModel) {
    val options  = listOf(0, 10, 25, 50, 100)
    val hasEnough = state.myCurrentXp >= state.wagerXp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("জিতলে XP পাবে, হারলে কাটা যাবে",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
                    Text("তোমার XP: ${state.myCurrentXp}",
                        fontSize = 11.sp, color = Color(0xFF4F46E5),
                        fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                }
                if (state.wagerXp > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("±${state.wagerXp} XP",
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFD97706), fontFamily = NotoSansBengali)
                    }
                }
            }

            // Wager chips
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(count = options.size) { idx ->
                    val opt = options[idx]
                    val label = if (opt == 0) "বাজি নেই" else "$opt XP"
                    val isSelected = state.wagerXp == opt
                    FilterChip(
                        selected = isSelected,
                        onClick  = { vm.onWagerChange(opt) },
                        label    = { Text(label, fontFamily = NotoSansBengali, fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (opt == 0) Color(0xFFDCFCE7)
                                                     else Color(0xFFFEF3C7),
                            selectedLabelColor     = if (opt == 0) Color(0xFF166534)
                                                     else Color(0xFFD97706)
                        )
                    )
                }
            }

            // Warning if not enough XP
            if (state.wagerXp > 0 && !hasEnough) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF1F2))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 12.sp)
                    Text("তোমার যথেষ্ট XP নেই! আগে XP অর্জন করো।",
                        fontSize = 11.sp, color = Color(0xFF991B1B),
                        fontFamily = NotoSansBengali)
                }
            }

            // ── Rewarded Ad: Earn 20 XP ──
            RewardedAdEarnCard(vm = vm)
        }
    }
}

@Composable
private fun RewardedAdEarnCard(vm: ChallengeViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    var rewardedAd by remember { mutableStateOf<com.google.android.gms.ads.rewarded.RewardedAd?>(null) }
    var isLoading  by remember { mutableStateOf(false) }
    var earned     by remember { mutableStateOf(false) }

    // Load ad on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        com.hanif.smartstudy.util.AdManager.loadRewarded(
            context  = context,
            onLoaded = { rewardedAd = it; isLoading = false },
            onFailed = { rewardedAd = null; isLoading = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                if (earned) "✅ +20 XP পেয়েছ!" else "🎬 বিজ্ঞাপন দেখো",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = Color.White, fontFamily = NotoSansBengali
            )
            Text(
                if (earned) "ধন্যবাদ! XP যোগ হয়েছে।" else "একটি বিজ্ঞাপন দেখে 20 XP অর্জন করো",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f),
                fontFamily = NotoSansBengali
            )
        }

        if (!earned) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(enabled = rewardedAd != null && !isLoading) {
                        activity?.let { act ->
                            com.hanif.smartstudy.util.AdManager.showRewarded(
                                activity    = act,
                                ad          = rewardedAd,
                                onRewarded  = { _ ->
                                    earned = true
                                    vm.earnXpFromAd(20)
                                    rewardedAd = null
                                },
                                onDismissed = { rewardedAd = null }
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (rewardedAd != null) "▶ দেখো" else "লোড হচ্ছে...",
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF7C3AED), fontFamily = NotoSansBengali
                    )
                }
            }
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("+20 XP ✓", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontFamily = NotoSansBengali)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)
}
