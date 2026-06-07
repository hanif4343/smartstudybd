package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.data.remote.AuthResult
import com.hanif.smartstudy.data.remote.FirebaseAuthService
import com.hanif.smartstudy.data.remote.GoogleAuthResult
import com.hanif.smartstudy.data.remote.ImgBBService
import com.hanif.smartstudy.data.remote.UserSyncService
import com.hanif.smartstudy.util.FcmHelper
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

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionManager(app)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val firebaseUrl: String get() = try { BuildConfig.FIREBASE_URL } catch (e: Exception) { "" }
    // ── Phone + Password Login ──
    fun login(phone: String, password: String) {
        val ph = phone.trim()
        val pw = password.trim()
        if (ph.isBlank()) { _authState.value = AuthState.Error("ফোন নম্বর দিন"); return }
        if (pw.isBlank()) { _authState.value = AuthState.Error("পাসওয়ার্ড দিন"); return }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = FirebaseAuthService.verifyLogin(ph, pw, firebaseUrl)) {
                is AuthResult.Success -> {
                    val user = User.fromFirebaseMap(r.userData).copy(phone = ph)
                    val fullUser = try {
                        UserSyncService.fetchUser(ph)?.copy(phone = ph) ?: user
                    } catch (e: Exception) {
                        user
                    }
                    session.saveUser(fullUser)
                    FcmHelper.collectAndSaveForPhone(getApplication(), ph)
                    Log.d("Auth", "Login success: ${fullUser.name}")
                    _authState.value = AuthState.Success(fullUser)
                }
                is AuthResult.Error -> _authState.value = AuthState.Error(r.message)
            }
        }
    }

    // ── Google Sign-In ──
    fun googleSignIn(email: String, name: String, photoUrl: String) {
        if (email.isBlank()) { _authState.value = AuthState.Error("Google থেকে email পাওয়া যায়নি"); return }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
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
            val dummyPassword = "GoogleUser_${ph.takeLast(4)}"

            when (val r = FirebaseAuthService.signupWithEmail(
                n, ph, email, dummyPassword, finalPhotoUrl, userType, classLevel, firebaseUrl
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
        val ph = phone.trim()
        val pw = password.trim()
        val cp = confirmPass.trim()

        when {
            n.isBlank() -> { _authState.value = AuthState.Error("নাম লিখুন"); return }
            ph.length < 11 -> { _authState.value = AuthState.Error("সঠিক ১১ সংখ্যার ফোন নম্বর দিন"); return }
            pw.length < 6 -> { _authState.value = AuthState.Error("পাসওয়ার্ড কমপক্ষে ৬ অক্ষর"); return }
            pw != cp -> { _authState.value = AuthState.Error("পাসওয়ার্ড দুটো মিলছে না"); return }
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = FirebaseAuthService.signup(n, ph, pw, userType, classLevel, firebaseUrl)) {
                is AuthResult.Success -> {
                    val user = User.fromFirebaseMap(r.userData).copy(phone = ph, name = n)
                    session.saveUser(user)
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
