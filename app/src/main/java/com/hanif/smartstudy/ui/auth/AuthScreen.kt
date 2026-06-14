package com.hanif.smartstudy.ui.auth

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hanif.smartstudy.MainActivity
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.AuthState
import com.hanif.smartstudy.viewmodel.AuthViewModel

// ── শ্রেণী গ্রুপ ──
data class ClassGroup(val label: String, val emoji: String, val items: List<String>)

val CLASS_GROUPS = listOf(
    ClassGroup("মাধ্যমিক", "📚", listOf(
        "Class 1","Class 2","Class 3","Class 4","Class 5",
        "Class 6","Class 7","Class 8","Class 9","Class 10"
    )),
    ClassGroup("উচ্চ মাধ্যমিক", "🎓", listOf(
        "Class 11","Class 12"
    )),
    ClassGroup("অনার্স", "🏛️", listOf(
        "Honours 1","Honours 2","Honours 3","Honours 4"
    )),
    ClassGroup("মাস্টার্স", "🔬", listOf(
        "Masters 1","Masters 2"
    ))
)

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit) {
    var showLogin by remember { mutableStateOf(true) }
    var googleEmail by remember { mutableStateOf("") }
    var googleName by remember { mutableStateOf("") }
    var googlePhotoUrl by remember { mutableStateOf("") }
    var isGoogleSignup by remember { mutableStateOf(false) }
    val vm: AuthViewModel = viewModel()
    val state by vm.authState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is AuthState.GoogleNewUser) {
            val s = state as AuthState.GoogleNewUser
            googleEmail = s.email
            googleName = s.name
            googlePhotoUrl = s.photoUrl
            isGoogleSignup = true
            showLogin = false
            vm.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Indigo700, Color(0xFF6D28D9))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(90.dp).clip(CircleShape)
                    .background(White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("📚", fontSize = 42.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Smart Study", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            Text("পড়ো, শেখো, এগিয়ে যাও", color = White.copy(alpha = 0.75f), fontSize = 13.sp, fontFamily = NotoSansBengali)
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(White.copy(alpha = 0.15f)).padding(4.dp)
            ) {
                TabBtn("লগইন", showLogin) { showLogin = true; isGoogleSignup = false }
                TabBtn("সাইনআপ", !showLogin) { showLogin = false }
            }
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                if (showLogin) LoginForm(onLoginSuccess, vm)
                else SignupForm(
                    onLoginSuccess = onLoginSuccess,
                    vm = vm,
                    prefillName = googleName,
                    prefillEmail = googleEmail,
                    prefillPhotoUrl = googlePhotoUrl,
                    isGoogleSignup = isGoogleSignup
                )
            }
        }
    }
}

@Composable
private fun TabBtn(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) White else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 36.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) Indigo600 else White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = NotoSansBengali)
    }
}

// ─────────── LOGIN ───────────
@Composable
fun LoginForm(onLoginSuccess: () -> Unit, vm: AuthViewModel = viewModel()) {
    val state by vm.authState.collectAsStateWithLifecycle()
    val fm = LocalFocusManager.current
    val ctx = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthState.Success) {
            vm.resetState(); onLoginSuccess()
        }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("স্বাগতম! 👋", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, color = Slate800)
        Text("অ্যাকাউন্টে লগইন করুন", fontSize = 13.sp, color = Color.Gray, fontFamily = NotoSansBengali)
        SSField(phone, { phone = it }, "ফোন নম্বর (01XXXXXXXXX)", Icons.Default.Phone, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next, onIme = { fm.moveFocus(FocusDirection.Down) })
        SSField(password, { password = it }, "পাসওয়ার্ড", Icons.Default.Lock, isPass = true, showPass = showPw, onToggle = { showPw = !showPw }, imeAction = ImeAction.Done, onIme = { fm.clearFocus(); vm.login(phone, password) })

        if (state is AuthState.Error) ErrBanner((state as AuthState.Error).message)

        Button(
            onClick = { fm.clearFocus(); vm.login(phone, password) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
            enabled = state !is AuthState.Loading
        ) {
            if (state is AuthState.Loading) CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("লগইন করুন", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = White, fontFamily = NotoSansBengali)
        }
        GoogleSignInButton(isLoading = state is AuthState.Loading) {
            val activity = ctx as? MainActivity ?: return@GoogleSignInButton
            activity.startGoogleSignIn(
                onSuccess = { email, name, photoUrl -> vm.googleSignIn(email, name, photoUrl) },
                onError = { msg -> vm.setError(msg) }
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ─────────── SIGNUP ───────────
@Composable
fun SignupForm(
    onLoginSuccess : () -> Unit,
    vm : AuthViewModel = viewModel(),
    prefillName : String = "",
    prefillEmail : String = "",
    prefillPhotoUrl : String = "",
    isGoogleSignup : Boolean = false
) {
    val state by vm.authState.collectAsStateWithLifecycle()
    val fm = LocalFocusManager.current
    val ctx = LocalContext.current
    var name by remember(prefillName) { mutableStateOf(prefillName) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var userType by remember { mutableStateOf("Student") }
    var classLevel by remember { mutableStateOf("Class 10") }
    var showClassPicker by remember { mutableStateOf(false) }

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedPhotoUri = uri }

    val userTypes = listOf("Student" to "Student", "Job" to "Job Seeker", "General" to "General")

    LaunchedEffect(state) {
        if (state is AuthState.Success) {
            vm.resetState(); onLoginSuccess()
        }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (isGoogleSignup) "Google দিয়ে সাইনআপ ✨" else "নতুন অ্যাকাউন্ট ✨",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = NotoSansBengali,
            color = Slate800
        )
        if (isGoogleSignup && prefillEmail.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE8F5E9)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✅", fontSize = 16.sp)
                Column {
                    Text("Google থেকে তথ্য আনা হয়েছে", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32), fontFamily = NotoSansBengali)
                    Text(prefillEmail, fontSize = 11.sp, color = Color(0xFF388E3C), fontFamily = NotoSansBengali)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("প্রোফাইল ছবি", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800, fontFamily = NotoSansBengali)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8EAF6))
                        .border(2.dp, Indigo600, CircleShape)
                        .clickable { photoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val photoToShow = selectedPhotoUri?.toString() ?: prefillPhotoUrl
                    if (photoToShow.isNotBlank()) {
                        AsyncImage(
                            model = photoToShow,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, null, tint = Indigo600, modifier = Modifier.size(40.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(Indigo600, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, null, tint = White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("ছবিতে tap করে পরিবর্তন করো", fontSize = 11.sp, color = Color.Gray, fontFamily = NotoSansBengali)
            }
        }
        SSField(name, { name = it }, "পুরো নাম", Icons.Default.Person, imeAction = ImeAction.Next, onIme = { fm.moveFocus(FocusDirection.Down) })
        SSField(phone, { phone = it }, "ফোন নম্বর (01XXXXXXXXX)", Icons.Default.Phone, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next, onIme = { fm.moveFocus(FocusDirection.Down) })

        if (!isGoogleSignup) {
            SSField(password, { password = it }, "পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)", Icons.Default.Lock, isPass = true, showPass = showPw, onToggle = { showPw = !showPw }, imeAction = ImeAction.Next, onIme = { fm.moveFocus(FocusDirection.Down) })
            SSField(confirmPass, { confirmPass = it }, "পাসওয়ার্ড নিশ্চিত করুন", Icons.Default.Lock, isPass = true, showPass = showPw, onToggle = { showPw = !showPw }, imeAction = ImeAction.Done, onIme = { fm.clearFocus() })
        }

        // আপনি কে?
        Text("আপনি কে?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800, fontFamily = NotoSansBengali)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            userTypes.forEach { (value, label) ->
                FilterChip(value == userType, {
                    userType = value
                    if (value == "Job") classLevel = ""
                    else if (classLevel.isBlank()) classLevel = "Class 10"
                }, label = { Text(label, fontSize = 12.sp, fontFamily = NotoSansBengali) })
            }
        }

        // শ্রেণী / স্তর picker — Job Seeker দের জন্য প্রয়োজন নেই
        if (userType != "Job") {
            Text("শ্রেণী / স্তর", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800, fontFamily = NotoSansBengali)

            // Selected class display button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, Indigo600, RoundedCornerShape(14.dp))
                    .clickable { showClassPicker = !showClassPicker }
                    .background(Indigo600.copy(alpha = 0.06f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("নির্বাচিত শ্রেণী", fontSize = 11.sp, color = Indigo600, fontFamily = NotoSansBengali, fontWeight = FontWeight.SemiBold)
                        Text(classLevel.ifBlank { "Class 10" }, fontSize = 15.sp, color = Slate800, fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        if (showClassPicker) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Indigo600
                    )
                }
            }

            // Class picker panel
            if (showClassPicker) {
                ClassLevelPicker(
                    selected = classLevel,
                    onSelect = { classLevel = it; showClassPicker = false }
                )
            }
        }

        if (state is AuthState.Error) ErrBanner((state as AuthState.Error).message)

        if (state is AuthState.Loading && isGoogleSignup) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Indigo600.copy(alpha = 0.08f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Indigo600)
                Text("ছবি আপলোড ও অ্যাকাউন্ট তৈরি হচ্ছে...", fontSize = 12.sp, color = Indigo600, fontFamily = NotoSansBengali)
            }
        }

        Button(
            onClick = {
                fm.clearFocus()
                if (isGoogleSignup) {
                    vm.googleSignup(name, prefillEmail, phone, prefillPhotoUrl, userType, classLevel, selectedPhotoUri)
                } else {
                    vm.signup(name, phone, password, confirmPass, userType, classLevel)
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green500),
            enabled = state !is AuthState.Loading
        ) {
            if (state is AuthState.Loading) CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("অ্যাকাউন্ট তৈরি করুন", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = White, fontFamily = NotoSansBengali)
        }

        if (!isGoogleSignup) {
            GoogleSignInButton(isLoading = state is AuthState.Loading) {
                val activity = ctx as? MainActivity ?: return@GoogleSignInButton
                activity.startGoogleSignIn(
                    onSuccess = { email, name2, photoUrl -> vm.googleSignIn(email, name2, photoUrl) },
                    onError = { msg -> vm.setError(msg) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ─────────── CLASS LEVEL PICKER ───────────
@Composable
fun ClassLevelPicker(selected: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF5F3FF))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CLASS_GROUPS.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Group header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(group.emoji, fontSize = 14.sp)
                    Text(
                        group.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Indigo700,
                        fontFamily = NotoSansBengali
                    )
                    Divider(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        color = Indigo600.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }

                // Grid of class items
                val chunked = group.items.chunked(3)
                chunked.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { item ->
                            val isSelected = item == selected
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Indigo600 else White
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) Indigo600 else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelect(item) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) White else Slate800,
                                    textAlign = TextAlign.Center,
                                    fontFamily = NotoSansBengali,
                                    maxLines = 2
                                )
                            }
                        }
                        // Fill empty slots in last row
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSignInButton(isLoading: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0)),
        enabled = !isLoading
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(22.dp).background(Color(0xFF4285F4), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("G", color = White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("Google দিয়ে লগইন/সাইনআপ", color = Color(0xFF3C4043), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {},
    isPass: Boolean = false,
    showPass: Boolean = false,
    onToggle: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp) },
        leadingIcon = { Icon(icon, null, tint = Indigo600) },
        trailingIcon = if (isPass) {{
            IconButton(onClick = onToggle) {
                Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Color.Gray)
            }
        }} else null,
        visualTransformation = if (isPass && !showPass) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Indigo600,
            unfocusedBorderColor = Color(0xFFE2E8F0),
            focusedLabelColor = Indigo600
        )
    )
}

@Composable
fun ErrBanner(msg: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Red500.copy(alpha = 0.1f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Warning, null, tint = Red500, modifier = Modifier.size(18.dp))
        Text(msg, color = Red500, fontSize = 13.sp, fontFamily = NotoSansBengali)
    }
}
