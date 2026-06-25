package com.hanif.smartstudy.ui.menu.sections

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel

private val Indigo600 = Color(0xFF4F46E5)
// SlateText replaced by MaterialTheme.colorScheme.onSurface
// MutedText replaced by MaterialTheme.colorScheme.onSurfaceVariant
// CardBg replaced by MaterialTheme.colorScheme.surface

// ── UserType এবং ClassLevel এর অপশন ──────────────────────
private val USER_TYPE_OPTIONS = listOf(
    "Student" to "শিক্ষার্থী",
    "Job"     to "চাকরিজীবী / Job Seeker"
)

// classLevel: ফাঁকা = Job seeker, নির্দিষ্ট মান = সেই শ্রেণির শিক্ষার্থী
private val CLASS_LEVEL_OPTIONS = listOf(
    ""          to "প্রযোজ্য নয় (Job Seeker)",
    "Class 1"   to "১ম শ্রেণি",
    "Class 2"   to "২য় শ্রেণি",
    "Class 3"   to "৩য় শ্রেণি",
    "Class 4"   to "৪র্থ শ্রেণি",
    "Class 5"   to "৫ম শ্রেণি",
    "Class 6"   to "৬ষ্ঠ শ্রেণি",
    "Class 7"   to "৭ম শ্রেণি",
    "Class 8"   to "৮ম শ্রেণি",
    "Class 9"   to "৯ম শ্রেণি",
    "Class 10"  to "১০ম শ্রেণি (SSC)",
    "Class 11"  to "একাদশ শ্রেণি",
    "Class 12"  to "দ্বাদশ শ্রেণি (HSC)",
    "Honours 1" to "অনার্স ১ম বর্ষ",
    "Honours 2" to "অনার্স ২য় বর্ষ",
    "Honours 3" to "অনার্স ৩য় বর্ষ",
    "Honours 4" to "অনার্স ৪র্থ বর্ষ",
    "Masters 1" to "মাস্টার্স ১ম বর্ষ",
    "Masters 2" to "মাস্টার্স ২য় বর্ষ"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    state   : MenuUiState,
    vm      : MenuViewModel,
    onBack  : () -> Unit
) {
    // ── Edit state ──
    var nameInput      by remember { mutableStateOf(state.user?.name      ?: "") }
    var userTypeInput  by remember { mutableStateOf(state.user?.userType  ?: "") }
    var classLevelInput by remember { mutableStateOf(state.user?.classLevel ?: "") }

    var showUserTypeMenu  by remember { mutableStateOf(false) }
    var showClassLevelMenu by remember { mutableStateOf(false) }

    // ── Track dirty state ──
    val isDirty = nameInput      != (state.user?.name      ?: "") ||
                  userTypeInput  != (state.user?.userType  ?: "") ||
                  classLevelInput != (state.user?.classLevel ?: "")

    // Image picker
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        vm.uploadPhoto(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("প্রোফাইল", fontFamily = NotoSansBengali, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Avatar ──
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(110.dp).clip(CircleShape)
                        .background(Color(0xFFEEF2FF))
                        .border(3.dp,
                            Brush.linearGradient(listOf(Indigo600, Color(0xFF7C3AED))),
                            CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val pic = state.user?.picture
                    if (!pic.isNullOrEmpty()) {
                        AsyncImage(model = pic, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else Text("👤", fontSize = 48.sp)

                    if (state.uploadProgress) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                // Camera button
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(Indigo600)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { imageLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }

            Text("ছবিতে ট্যাপ করে নতুন ছবি বেছে নিন",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)

            // ── Editable info card ──
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ব্যক্তিগত তথ্য", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface, fontFamily = NotoSansBengali)

                    // ── Name ──
                    OutlinedTextField(
                        value         = nameInput,
                        onValueChange = { nameInput = it },
                        label         = { Text("নাম", fontFamily = NotoSansBengali) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600)
                    )

                    // ── Read-only ──
                    ReadOnlyField("📱 ফোন", state.user?.phone ?: "—")
                    ReadOnlyField("⭐ XP", "${state.user?.xp ?: 0}")
                    ReadOnlyField("🛡️ রোল", state.user?.role ?: "User")

                    Divider(color = Color(0xFFE2E8F0))

                    Text("📚 শ্রেণি ও ধরন", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)

                    // ── UserType dropdown ──
                    ExposedDropdownMenuBox(
                        expanded  = showUserTypeMenu,
                        onExpandedChange = { showUserTypeMenu = it }
                    ) {
                        OutlinedTextField(
                            value    = USER_TYPE_OPTIONS.firstOrNull { it.first == userTypeInput }?.second
                                       ?: userTypeInput.ifBlank { "বেছে নিন" },
                            onValueChange = {},
                            readOnly = true,
                            label    = { Text("ধরন (UserType)", fontFamily = NotoSansBengali) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUserTypeMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600)
                        )
                        ExposedDropdownMenu(
                            expanded = showUserTypeMenu,
                            onDismissRequest = { showUserTypeMenu = false }
                        ) {
                            USER_TYPE_OPTIONS.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontFamily = NotoSansBengali) },
                                    onClick = {
                                        userTypeInput = value
                                        // Job seeker হলে classLevel খালি করে দাও
                                        if (value == "Job") classLevelInput = ""
                                        showUserTypeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // ── ClassLevel dropdown ──
                    ExposedDropdownMenuBox(
                        expanded = showClassLevelMenu,
                        onExpandedChange = { showClassLevelMenu = it }
                    ) {
                        OutlinedTextField(
                            value    = CLASS_LEVEL_OPTIONS.firstOrNull { it.first == classLevelInput }?.second
                                       ?: classLevelInput.ifBlank { "বেছে নিন" },
                            onValueChange = {},
                            readOnly = true,
                            label    = { Text("শ্রেণি (ClassLevel)", fontFamily = NotoSansBengali) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showClassLevelMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600)
                        )
                        ExposedDropdownMenu(
                            expanded = showClassLevelMenu,
                            onDismissRequest = { showClassLevelMenu = false }
                        ) {
                            CLASS_LEVEL_OPTIONS.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontFamily = NotoSansBengali,
                                                  fontWeight = if (value == classLevelInput) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        classLevelInput = value
                                        showClassLevelMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // ── Audience tag preview ──
                    val tagLabel = when {
                        classLevelInput.isNotBlank() ->
                            "এই শ্রেণির কনটেন্ট দেখবেন: $classLevelInput"
                        userTypeInput.equals("Job", ignoreCase = true) ->
                            "Job / চাকরি সম্পর্কিত কনটেন্ট দেখবেন"
                        else ->
                            "শুধু সাধারণ (ফাঁকা tag) কনটেন্ট দেখবেন"
                    }
                    Text("🔍 $tagLabel",
                        fontSize = 11.sp, color = Color(0xFF4F46E5),
                        fontFamily = NotoSansBengali,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEEF2FF), RoundedCornerShape(8.dp))
                            .padding(8.dp))
                }
            }

            // ── Update button ──
            Button(
                onClick = {
                    vm.updateProfile(nameInput, userTypeInput, classLevelInput)
                },
                enabled = isDirty,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Indigo600,
                    disabledContainerColor = Color(0xFFCBD5E1)
                )
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("প্রোফাইল আপডেট করুন", fontFamily = NotoSansBengali,
                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }

            // ── Success / error ──
            state.successMsg?.let {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Text("✅ $it", Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                        color = Color(0xFF166534), fontWeight = FontWeight.Bold)
                }
            }
            state.error?.let {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(Color(0xFFFFF1F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Text("⚠️ $it", Modifier.padding(12.dp), fontFamily = NotoSansBengali,
                        color = Color(0xFF9F1239), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = NotoSansBengali)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            fontFamily = NotoSansBengali)
    }
}
