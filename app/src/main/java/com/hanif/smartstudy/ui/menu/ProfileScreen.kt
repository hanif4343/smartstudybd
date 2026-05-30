package com.hanif.smartstudy.ui.menu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel

// ─────────────────────────────────────────────────────────────
//  ProfileScreen — edit name, upload photo, view info
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state  : MenuUiState,
    vm     : MenuViewModel,
    onBack : () -> Unit
) {
    val user = state.user
    var name by remember { mutableStateOf(user?.name ?: "") }
    var editingName by remember { mutableStateOf(false) }

    // Image picker
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { vm.uploadProfilePhoto(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("প্রোফাইল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Avatar + upload ──
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(0.15f))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isUploadingPhoto) {
                        CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                    } else if (!user?.picture.isNullOrBlank()) {
                        AsyncImage(
                            model        = user!!.picture,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier     = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            (user?.name?.firstOrNull()?.toString() ?: "?").uppercase(),
                            fontSize   = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Edit badge
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                }
            }

            if (state.photoUploadError != null) {
                Text(state.photoUploadError, color = Red500, fontSize = 12.sp, fontFamily = NotoSansBengali)
            }

            Text(
                "ছবিতে ক্লিক করে পরিবর্তন করুন",
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                fontFamily = NotoSansBengali
            )

            Spacer(Modifier.height(4.dp))

            // ── Name edit ──
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("নাম", fontFamily = NotoSansBengali, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        if (!editingName) {
                            TextButton(onClick = { editingName = true }) {
                                Text("পরিবর্তন", fontFamily = NotoSansBengali, fontSize = 12.sp)
                            }
                        }
                    }
                    if (editingName) {
                        OutlinedTextField(
                            value         = name,
                            onValueChange = { name = it },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true,
                            shape         = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editingName = false; name = user?.name ?: "" }) {
                                Text("বাতিল", fontFamily = NotoSansBengali)
                            }
                            Button(onClick = { vm.updateName(name); editingName = false }) {
                                Text("সংরক্ষণ", fontFamily = NotoSansBengali)
                            }
                        }
                    } else {
                        Text(user?.name ?: "নাম নেই", fontFamily = NotoSansBengali, fontSize = 16.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── User info ──
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(0.dp)) {
                    ProfileInfoRow("📱 ফোন",       user?.phone ?: "-")
                    ProfileInfoRow("🎓 ধরন",        user?.userType ?: "-")
                    ProfileInfoRow("📚 শ্রেণি",     user?.classLevel ?: "-")
                    ProfileInfoRow("⭐ XP",          "${user?.xp ?: 0}")
                    ProfileInfoRow("🔑 ভূমিকা",     user?.role ?: "User")
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, fontSize = 13.sp, fontFamily = NotoSansBengali, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(0.12f))
}
