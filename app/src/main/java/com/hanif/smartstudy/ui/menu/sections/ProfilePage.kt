package com.hanif.smartstudy.ui.menu.sections

import android.graphics.BitmapFactory
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.viewmodel.MenuUiState
import com.hanif.smartstudy.viewmodel.MenuViewModel
import java.io.ByteArrayOutputStream

private val Indigo600 = Color(0xFF4F46E5)
private val SlateText = Color(0xFF1E293B)
private val MutedText = Color(0xFF64748B)
private val CardBg    = Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    state   : MenuUiState,
    vm      : MenuViewModel,
    onBack  : () -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf(state.user?.name ?: "") }
    var editingName by remember { mutableStateOf(false) }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
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

            // Upload hint
            Text("ছবিতে ট্যাপ করে নতুন ছবি বেছে নিন",
                fontSize = 11.sp, color = MutedText, fontFamily = NotoSansBengali)

            // ── Name edit ──
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(CardBg), elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ব্যক্তিগত তথ্য", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = SlateText, fontFamily = NotoSansBengali)

                    // Name
                    OutlinedTextField(
                        value         = nameInput,
                        onValueChange = { nameInput = it },
                        label         = { Text("নাম", fontFamily = NotoSansBengali) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        trailingIcon  = {
                            if (nameInput != state.user?.name) {
                                IconButton(onClick = {
                                    vm.updateName(nameInput.trim())
                                }) {
                                    Icon(Icons.Default.Check, null, tint = Indigo600)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600)
                    )

                    // Read-only fields
                    ReadOnlyField("📱 ফোন", state.user?.phone ?: "—")
                    ReadOnlyField("👤 ধরন", state.user?.userType ?: "—")
                    ReadOnlyField("🎓 শ্রেণি", state.user?.classLevel?.ifBlank { "—" } ?: "—")
                    ReadOnlyField("⭐ XP", "${state.user?.xp ?: 0}")
                    ReadOnlyField("🛡️ রোল", state.user?.role ?: "User")
                }
            }

            // Success / error messages
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
        Text(label, fontSize = 12.sp, color = MutedText, fontFamily = NotoSansBengali)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateText,
            fontFamily = NotoSansBengali)
    }
}
