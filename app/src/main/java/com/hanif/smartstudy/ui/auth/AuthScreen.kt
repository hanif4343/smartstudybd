package com.hanif.smartstudy.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.viewmodel.AuthState
import com.hanif.smartstudy.viewmodel.AuthViewModel

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit) {
    var showLogin by remember { mutableStateOf(true) }

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
            // Logo
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text("📚", fontSize = 42.sp) }

            Spacer(Modifier.height(12.dp))
            Text("Smart Study", color = White, fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali)
            Text("পড়ো, শেখো, এগিয়ে যাও", color = White.copy(alpha = 0.75f),
                fontSize = 13.sp, fontFamily = NotoSansBengali)

            Spacer(Modifier.height(28.dp))

            // Tab toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(White.copy(alpha = 0.15f))
                    .padding(4.dp)
            ) {
                TabBtn("লগইন",   showLogin)        { showLogin = true  }
                TabBtn("সাইনআপ", !showLogin)       { showLogin = false }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(24.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                if (showLogin) LoginForm(onLoginSuccess)
                else           SignupForm(onLoginSuccess)
            }
        }
    }
}

@Composable
private fun TabBtn(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) White else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 36.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label,
            color      = if (active) Indigo600 else White,
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            fontFamily = NotoSansBengali)
    }
}

// ─────────── LOGIN ───────────
@Composable
fun LoginForm(onLoginSuccess: () -> Unit, vm: AuthViewModel = viewModel()) {
    val state by vm.authState.collectAsStateWithLifecycle()
    val fm    = LocalFocusManager.current
    var phone    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthState.Success) { vm.resetState(); onLoginSuccess() }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("স্বাগতম! 👋", fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, color = Slate800)
        Text("অ্যাকাউন্টে লগইন করুন", fontSize = 13.sp,
            color = Color.Gray, fontFamily = NotoSansBengali)

        SSField(phone, { phone = it }, "ফোন নম্বর (01XXXXXXXXX)", Icons.Default.Phone,
            keyboardType = KeyboardType.Phone,
            imeAction    = ImeAction.Next,
            onIme        = { fm.moveFocus(FocusDirection.Down) })

        SSField(password, { password = it }, "পাসওয়ার্ড", Icons.Default.Lock,
            isPass       = true,
            showPass     = showPw,
            onToggle     = { showPw = !showPw },
            imeAction    = ImeAction.Done,
            onIme        = { fm.clearFocus(); vm.login(phone, password) })

        if (state is AuthState.Error) ErrBanner((state as AuthState.Error).message)

        Button(
            onClick  = { fm.clearFocus(); vm.login(phone, password) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Indigo600),
            enabled  = state !is AuthState.Loading
        ) {
            if (state is AuthState.Loading)
                CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else
                Text("লগইন করুন", fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp, color = White, fontFamily = NotoSansBengali)
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ─────────── SIGNUP ───────────
@Composable
fun SignupForm(onLoginSuccess: () -> Unit, vm: AuthViewModel = viewModel()) {
    val state by vm.authState.collectAsStateWithLifecycle()
    val fm    = LocalFocusManager.current
    var name        by remember { mutableStateOf("") }
    var phone       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showPw      by remember { mutableStateOf(false) }
    var userType    by remember { mutableStateOf("Student") }
    var classLevel  by remember { mutableStateOf("HSC") }

    val userTypes   = listOf("Student", "Job Seeker", "General")
    val classLevels = listOf("SSC", "HSC", "Degree", "Masters", "BCS", "Bank", "Other")

    LaunchedEffect(state) {
        if (state is AuthState.Success) { vm.resetState(); onLoginSuccess() }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("নতুন অ্যাকাউন্ট ✨", fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold, fontFamily = NotoSansBengali, color = Slate800)

        SSField(name,   { name = it },   "পুরো নাম", Icons.Default.Person,
            imeAction = ImeAction.Next, onIme = { fm.moveFocus(FocusDirection.Down) })
        SSField(phone,  { phone = it },  "ফোন নম্বর (01XXXXXXXXX)", Icons.Default.Phone,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next, onIme = { fm.moveFocus(FocusDirection.Down) })
        SSField(password, { password = it }, "পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)", Icons.Default.Lock,
            isPass = true, showPass = showPw, onToggle = { showPw = !showPw },
            imeAction = ImeAction.Next, onIme = { fm.moveFocus(FocusDirection.Down) })
        SSField(confirmPass, { confirmPass = it }, "পাসওয়ার্ড নিশ্চিত করুন", Icons.Default.Lock,
            isPass = true, showPass = showPw, onToggle = { showPw = !showPw },
            imeAction = ImeAction.Done, onIme = { fm.clearFocus() })

        // User type
        Text("আপনি কে?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = Slate800, fontFamily = NotoSansBengali)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            userTypes.forEach { t ->
                FilterChip(t == userType, { userType = t },
                    label = { Text(t, fontSize = 12.sp, fontFamily = NotoSansBengali) })
            }
        }

        // Class level
        Text("শ্রেণী / স্তর", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = Slate800, fontFamily = NotoSansBengali)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            classLevels.forEach { cl ->
                FilterChip(cl == classLevel, { classLevel = cl },
                    label = { Text(cl, fontSize = 12.sp, fontFamily = NotoSansBengali) })
            }
        }

        if (state is AuthState.Error) ErrBanner((state as AuthState.Error).message)

        Button(
            onClick  = { fm.clearFocus(); vm.signup(name, phone, password, confirmPass, userType, classLevel) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Green500),
            enabled  = state !is AuthState.Loading
        ) {
            if (state is AuthState.Loading)
                CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else
                Text("অ্যাকাউন্ট তৈরি করুন", fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp, color = White, fontFamily = NotoSansBengali)
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ─────────── Reusable components ───────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSField(
    value: String, onValueChange: (String) -> Unit,
    label: String, icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {},
    isPass: Boolean = false,
    showPass: Boolean = false,
    onToggle: () -> Unit = {}
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, fontFamily = NotoSansBengali, fontSize = 13.sp) },
        leadingIcon   = { Icon(icon, null, tint = Indigo600) },
        trailingIcon  = if (isPass) {{
            IconButton(onClick = onToggle) {
                Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    null, tint = Color.Gray)
            }
        }} else null,
        visualTransformation = if (isPass && !showPass) PasswordVisualTransformation()
                               else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier   = Modifier.fillMaxWidth(),
        shape      = RoundedCornerShape(14.dp),
        singleLine = true,
        colors     = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Indigo600,
            unfocusedBorderColor = Color(0xFFE2E8F0),
            focusedLabelColor    = Indigo600
        )
    )
}

@Composable
fun ErrBanner(msg: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Red500.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment         = Alignment.CenterVertically,
        horizontalArrangement     = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Warning, null, tint = Red500, modifier = Modifier.size(18.dp))
        Text(msg, color = Red500, fontSize = 13.sp, fontFamily = NotoSansBengali)
    }
}
