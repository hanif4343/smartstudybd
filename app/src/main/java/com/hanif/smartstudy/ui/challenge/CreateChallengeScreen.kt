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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier.fillMaxSize().padding(padding)
                .background(Color(0xFFF8FAFC)).verticalScroll(rememberScrollState())
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

            // Error
            state.error?.let { err ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF1F2)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠️", fontSize = 14.sp)
                    Text(err, fontSize = 12.sp, color = Color(0xFF991B1B), fontFamily = NotoSansBengali)
                }
            }

            // Create button
            Button(
                onClick  = vm::createChallenge,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                enabled  = !state.isLoading && state.invitedUsers.isNotEmpty() && state.selectedSubject.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("🚀 চ্যালেঞ্জ পাঠাও!", fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
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

            // Search result
            state.searchResult?.let { user ->
                SearchResultCard(user = user, onAdd = vm::addInvitee)
            }
            state.searchError?.let { err ->
                Text("⚠️ $err", fontSize = 11.sp, color = Color(0xFFDC2626), fontFamily = NotoSansBengali)
            }

            // Invited list
            if (state.invitedUsers.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFF1F5F9))
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
private fun SearchResultCard(user: User, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0FDF4)).border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center) {
            Text(user.displayName().first().toString(), fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(user.displayName(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF065F46), fontFamily = NotoSansBengali)
            Text(user.phone ?: "", fontSize = 10.sp, color = Color(0xFF047857))
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF10B981))) {
            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun InvitedUserChip(user: User, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC)).padding(horizontal = 10.dp, vertical = 8.dp),
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
                color = Color(0xFF1E293B), fontFamily = NotoSansBengali)
            Text(user.phone ?: "", fontSize = 10.sp, color = Color(0xFF64748B))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SubjectSection(state: ChallengeUiState, vm: ChallengeViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Subject chips
            Text("বিষয়:", fontSize = 11.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items = state.subjects) { subject ->
                    FilterChip(
                        selected = state.selectedSubject == subject,
                        onClick  = { vm.onSubjectSelect(subject) },
                        label    = { Text(subject, fontFamily = NotoSansBengali, fontSize = 11.sp) }
                    )
                }
            }

            // SubTopic chips (optional)
            if (state.subTopics.isNotEmpty()) {
                Text("টপিক (ঐচ্ছিক):", fontSize = 11.sp, color = Color(0xFF64748B), fontFamily = NotoSansBengali)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = state.selectedSubTopic.isEmpty(),
                            onClick = { vm.onSubTopicSelect("") },
                            label   = { Text("সব টপিক", fontFamily = NotoSansBengali, fontSize = 11.sp) })
                    }
                    items(items = state.subTopics) { sub ->
                        FilterChip(selected = state.selectedSubTopic == sub,
                            onClick = { vm.onSubTopicSelect(sub) },
                            label   = { Text(sub, fontFamily = NotoSansBengali, fontSize = 11.sp) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(state: ChallengeUiState, vm: ChallengeViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Question count
            ConfigRow(
                label = "প্রশ্ন সংখ্যা",
                value = "${state.questionCount}টি",
                options = listOf(5, 10, 15, 20, 25, 30).map { "$it" },
                selected = "${state.questionCount}",
                onSelect = { vm.onQuestionCountChange(it.toInt()) }
            )
            // Time limit
            ConfigRow(
                label    = "সময়সীমা",
                value    = "${state.timeLimitSec / 60} মিনিট",
                options  = listOf("5 মি" to 300, "10 মি" to 600, "15 মি" to 900, "20 মি" to 1200).map { it.first },
                selected = "${state.timeLimitSec / 60} মিনিট",
                onSelect = { label ->
                    val sec = when (label) {
                        "5 মি" -> 300; "10 মি" -> 600; "15 মি" -> 900; else -> 1200
                    }
                    vm.onTimeLimitChange(sec)
                }
            )
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B), fontFamily = NotoSansBengali)
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEF2FF))
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
private fun SectionHeader(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF1E293B), fontFamily = NotoSansBengali)
}
