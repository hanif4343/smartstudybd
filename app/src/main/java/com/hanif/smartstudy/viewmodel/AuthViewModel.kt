package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.data.remote.AuthResult
import com.hanif.smartstudy.data.remote.FirebaseAuthService
import com.hanif.smartstudy.data.remote.GoogleAuthResult
import com.hanif.smartstudy.data.remote.ImgBBService
import com.hanif.smartstudy.data.remote.UserSyncService
import com.hanif.smartstudy.data.remote.awaitTask
import com.hanif.smartstudy.util.FcmHelper
import com.hanif.smartstudy.util.PhoneValidator
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URL

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
    data class GoogleNewUser(
        val email: String,
        val name: String,
        val photoUrl: String
    ) : AuthState()
}

/**
 * Login/Signup এখন real Firebase Auth দিয়ে হয় (কোনো master secret/custom hashing লাগে না):
 * - Phone + Password ইউজার → Firebase "Email/Password" provider, ফোন নম্বর থেকে বানানো
 *   একটা ভেতরের synthetic email দিয়ে (ইউজার এটা কখনো দেখে না, ও শুধু ফোন+পাসওয়ার্ড দেখে)।
 *   এই পদ্ধতি Firebase এর ফ্রি Spark প্ল্যানেই কাজ করে — Phone OTP এর মতো SMS cost/Blaze
 *   plan লাগে না।
 * - Google ইউজার → real Google credential দিয়ে সরাসরি Firebase Auth এ sign in।
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionManager(app)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val firebaseUrl: String get() = try { BuildConfig.FIREBASE_URL.trimEnd('/') + "/" } catch (e: Exception) { "" }

    // ── Phone + Password Login ──
    fun login(phone: String, password: String) {
        val cleanPhone = PhoneValidator.sanitize(phone)
        val pw = password.trim()
        if (cleanPhone == null) { _authState.value = AuthState.Error("সঠিক ১১ সংখ্যার ফোন নম্বর দিন (01XXXXXXXXX)"); return }
        if (pw.isBlank()) { _authState.value = AuthState.Error("পাসওয়ার্ড দিন"); return }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val email = PhoneValidator.toSyntheticEmail(cleanPhone)
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pw).awaitTask()

                val profile = FirebaseAuthService.findUserByPhone(cleanPhone, firebaseUrl)
                if (profile == null) {
                    _authState.value = AuthState.Error("একাউন্ট পাওয়া যায়নি, Admin এর সাথে যোগাযোগ করুন")
                    return@launch
                }
                val status = (profile["Status"] ?: profile["status"] ?: "Active").toString().lowercase()
                if (status == "inactive") {
                    _authState.value = AuthState.Error("অ্যাকাউন্ট নিষ্ক্রিয়। Admin এর সাথে যোগাযোগ করুন।")
                    return@launch
                }

                val baseUser = User.fromFirebaseMap(profile).copy(phone = cleanPhone)
                val fullUser = try {
                    UserSyncService.fetchUser(cleanPhone)?.copy(phone = cleanPhone) ?: baseUser
                } catch (e: Exception) {
                    baseUser
                }
                // Local session এ already XP থাকলে সেটাই রাখো — Firebase XP দিয়ে overwrite করো না।
                // (Quiz করার পর Firebase XP update হতে সময় লাগতে পারে, তাই local টাই বেশি নির্ভরযোগ্য)
                val localXp = session.getCurrentUser()?.xp ?: 0
                val mergedUser = fullUser.copy(xp = maxOf(fullUser.xp, localXp))
                session.saveUser(mergedUser)
                FcmHelper.collectAndSaveForPhone(getApplication(), cleanPhone)
                Log.d("Auth", "Login success: ${fullUser.name}")
                _authState.value = AuthState.Success(fullUser)
            } catch (e: Exception) {
                Log.e("Auth", "Login failed: ${e.message}")
                val msg = when {
                    e.message?.contains("no user record", ignoreCase = true) == true ||
                        e.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true ||
                        e.message?.contains("password is invalid", ignoreCase = true) == true ->
                        "ফোন নম্বর বা পাসওয়ার্ড ভুল"
                    else -> "লগইন করতে সমস্যা হয়েছে: ${e.message}"
                }
                _authState.value = AuthState.Error(msg)
            }
        }
    }

    // ── Phone + Password Signup ──
    fun signup(
        name: String,
        phone: String,
        password: String,
        confirmPass: String,
        userType: String,
        classLevel: String
    ) {
        val n = name.trim()
        val cleanPhone = PhoneValidator.sanitize(phone)
        val pw = password.trim()
        val cp = confirmPass.trim()

        when {
            n.isBlank() -> { _authState.value = AuthState.Error("নাম লিখুন"); return }
            cleanPhone == null -> { _authState.value = AuthState.Error("সঠিক ১১ সংখ্যার ফোন নম্বর দিন (01XXXXXXXXX)"); return }
            pw.length < 6 -> { _authState.value = AuthState.Error("পাসওয়ার্ড কমপক্ষে ৬ অক্ষর"); return }
            pw != cp -> { _authState.value = AuthState.Error("পাসওয়ার্ড দুটো মিলছে না"); return }
        }
        val safePhone = cleanPhone!!

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val email = PhoneValidator.toSyntheticEmail(safePhone)
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pw).awaitTask()

                when (val r = FirebaseAuthService.createProfile(n, safePhone, userType, classLevel, firebaseUrl)) {
                    is AuthResult.Success -> {
                        val user = User.fromFirebaseMap(r.userData).copy(phone = safePhone, name = n)
                        session.saveUser(user)
                        FcmHelper.collectAndSaveForPhone(getApplication(), safePhone)
                        _authState.value = AuthState.Success(user)
                    }
                    is AuthResult.Error -> {
                        // Firebase Auth account তৈরি হয়ে গেছে কিন্তু DB profile তৈরি ব্যর্থ —
                        // সাইন আপ করা Auth account টা মুছে দাও, না হলে এই নম্বর দিয়ে আর কখনো signup করা যাবে না
                        try { FirebaseAuth.getInstance().currentUser?.delete()?.awaitTask() } catch (ignored: Exception) {}
                        _authState.value = AuthState.Error(r.message)
                    }
                }
            } catch (e: Exception) {
                Log.e("Auth", "Signup failed: ${e.message}")
                val msg = when {
                    e.message?.contains("already in use", ignoreCase = true) == true ||
                        e.message?.contains("EMAIL_EXISTS", ignoreCase = true) == true ->
                        "এই ফোন নম্বর আগে থেকেই নিবন্ধিত"
                    else -> "সাইন আপ করতে সমস্যা হয়েছে: ${e.message}"
                }
                _authState.value = AuthState.Error(msg)
            }
        }
    }

    // ── Google Sign-In ──
    fun googleSignIn(email: String, name: String, photoUrl: String, idToken: String) {
        if (email.isBlank()) { _authState.value = AuthState.Error("Google থেকে email পাওয়া যায়নি"); return }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                if (idToken.isNotBlank()) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential).awaitTask()
                } else {
                    Log.w("Auth", "Google idToken blank — real Firebase Auth bridge skip হলো")
                }
            } catch (e: Exception) {
                Log.e("Auth", "Google Firebase bridge failed: ${e.message}")
                _authState.value = AuthState.Error("Google sign-in সমস্যা: ${e.message}")
                return@launch
            }

            when (val r = FirebaseAuthService.googleSignIn(email, name, photoUrl, firebaseUrl)) {
                is GoogleAuthResult.ExistingUser -> {
                    val user = User.fromFirebaseMap(r.userData).let { u ->
                        u.copy(picture = u.picture ?: photoUrl, name = u.name ?: name)
                    }
                    session.saveUser(user)
                    user.phone?.let { FcmHelper.collectAndSaveForPhone(getApplication(), it) }
                    _authState.value = AuthState.Success(user)
                }
                is GoogleAuthResult.NewUser -> {
                    _authState.value = AuthState.GoogleNewUser(r.email, r.name, r.photoUrl)
                }
                is GoogleAuthResult.Error -> _authState.value = AuthState.Error(r.message)
            }
        }
    }

    // ── Google Signup ──
    fun googleSignup(
        name: String,
        email: String,
        phone: String,
        photoUrl: String,
        userType: String,
        classLevel: String,
        localPhotoUri: Uri? = null
    ) {
        val n = name.trim()
        val ph = phone.trim()
        if (n.isBlank()) { _authState.value = AuthState.Error("নাম লিখুন"); return }
        if (ph.length < 11) { _authState.value = AuthState.Error("সঠিক ১১ সংখ্যার ফোন নম্বর দিন"); return }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val finalPhotoUrl = try {
                when {
                    localPhotoUri != null -> {
                        val bytes = getApplication<Application>().contentResolver
                            .openInputStream(localPhotoUri)?.readBytes()
                        if (bytes != null) ImgBBService.upload(bytes) ?: photoUrl else photoUrl
                    }
                    photoUrl.isNotBlank() -> {
                        val bytes = URL(photoUrl).readBytes()
                        ImgBBService.upload(bytes) ?: photoUrl
                    }
                    else -> ""
                }
            } catch (e: Exception) {
                Log.e("GoogleSignup", "Photo upload failed: ${e.message}")
                photoUrl
            }

            Log.d("GoogleSignup", "Final photo URL: $finalPhotoUrl")

            when (val r = FirebaseAuthService.createProfile(
                name = n, localPhone = ph, userType = userType, classLevel = classLevel,
                firebaseUrl = firebaseUrl, email = email, picture = finalPhotoUrl
            )) {
                is AuthResult.Success -> {
                    val user = User.fromFirebaseMap(r.userData).copy(
                        phone = ph,
                        name = n,
                        picture = finalPhotoUrl
                    )
                    session.saveUser(user)
                    FcmHelper.collectAndSaveForPhone(getApplication(), ph)
                    _authState.value = AuthState.Success(user)
                }
                is AuthResult.Error -> _authState.value = AuthState.Error(r.message)
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun setError(msg: String) {
        _authState.value = AuthState.Error(msg)
    }
}
