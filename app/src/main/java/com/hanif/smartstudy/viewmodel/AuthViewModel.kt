package com.hanif.smartstudy.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
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
import java.util.concurrent.TimeUnit

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    /** OTP পাঠানো হয়েছে — e164Phone শুধু UI তে "XXXX নম্বরে কোড পাঠানো হয়েছে" দেখানোর জন্য */
    data class OtpSent(val e164Phone: String) : AuthState()
    /** OTP verify হয়েছে কিন্তু এই নম্বরে এখনো কোনো profile নেই — signup form দেখাও */
    data class NeedsProfile(val localPhone: String) : AuthState()
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

    private val firebaseUrl: String get() = try { BuildConfig.FIREBASE_URL.trimEnd('/') + "/" } catch (e: Exception) { "" }

    // ── OTP flow এর জন্য সাময়িক state (UI state নয়, শুধু ViewModel এর ভেতরে) ──
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var pendingE164: String? = null

    // ── ধাপ ১: ফোন নম্বরে OTP পাঠাও ──
    fun sendOtp(rawPhone: String, activity: Activity) {
        val e164 = PhoneValidator.toE164BD(rawPhone)
        if (e164 == null) {
            _authState.value = AuthState.Error("সঠিক ১১ সংখ্যার ফোন নম্বর দিন (01XXXXXXXXX)")
            return
        }
        pendingE164 = e164
        startVerification(e164, activity, null)
    }

    // ── কোড আবার পাঠাও (একই নম্বরে) ──
    fun resendOtp(activity: Activity) {
        val e164 = pendingE164
        if (e164 == null) { _authState.value = AuthState.Error("আগে ফোন নম্বর দিন"); return }
        startVerification(e164, activity, resendToken)
    }

    private fun startVerification(
        e164: String,
        activity: Activity,
        forceResend: PhoneAuthProvider.ForceResendingToken?
    ) {
        _authState.value = AuthState.Loading
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // ফোন স্বয়ংক্রিয়ভাবে verify হয়ে গেছে (SMS auto-detect) — কোড লেখা লাগবে না
                Log.d("Auth", "Auto-verification completed")
                viewModelScope.launch { completeSignIn(credential) }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("Auth", "OTP verification failed: ${e.message}")
                _authState.value = AuthState.Error("OTP পাঠানো যায়নি: ${e.message}")
            }
            override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = vId
                resendToken = token
                Log.d("Auth", "OTP sent to $e164")
                _authState.value = AuthState.OtpSent(e164)
            }
        }
        val optionsBuilder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (forceResend != null) optionsBuilder.setForceResendingToken(forceResend)
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    // ── ধাপ ২: ইউজার যে কোড লিখলো সেটা verify করো ──
    fun verifyOtp(code: String) {
        val vId = verificationId
        if (vId == null) { _authState.value = AuthState.Error("আগে OTP পাঠান"); return }
        val trimmed = code.trim()
        if (trimmed.length < 6) { _authState.value = AuthState.Error("৬ সংখ্যার কোড দিন"); return }
        viewModelScope.launch {
            completeSignIn(PhoneAuthProvider.getCredential(vId, trimmed))
        }
    }

    private suspend fun completeSignIn(credential: PhoneAuthCredential) {
        _authState.value = AuthState.Loading
        try {
            val result = FirebaseAuth.getInstance().signInWithCredential(credential).awaitTask()
            val localPhone = PhoneValidator.fromE164BD(result.user?.phoneNumber)
            if (localPhone == null) {
                _authState.value = AuthState.Error("ফোন নম্বর শনাক্ত করতে সমস্যা হয়েছে")
                return
            }

            val existing = FirebaseAuthService.findUserByPhone(localPhone, firebaseUrl)
            if (existing != null) {
                val baseUser = User.fromFirebaseMap(existing).copy(phone = localPhone)
                val fullUser = try {
                    UserSyncService.fetchUser(localPhone)?.copy(phone = localPhone) ?: baseUser
                } catch (e: Exception) {
                    baseUser
                }
                session.saveUser(fullUser)
                FcmHelper.collectAndSaveForPhone(getApplication(), localPhone)
                Log.d("Auth", "OTP login success: ${fullUser.name}")
                _authState.value = AuthState.Success(fullUser)
            } else {
                _authState.value = AuthState.NeedsProfile(localPhone)
            }
        } catch (e: Exception) {
            Log.e("Auth", "completeSignIn failed: ${e.message}")
            val msg = when {
                e.message?.contains("invalid", ignoreCase = true) == true -> "ভুল OTP কোড, আবার চেষ্টা করুন"
                e.message?.contains("expired", ignoreCase = true) == true -> "OTP এর মেয়াদ শেষ হয়ে গেছে, আবার পাঠান"
                else -> "যাচাই করতে সমস্যা হয়েছে: ${e.message}"
            }
            _authState.value = AuthState.Error(msg)
        }
    }

    // ── ধাপ ৩ (নতুন ইউজার হলে): প্রোফাইল তথ্য দিয়ে অ্যাকাউন্ট তৈরি করো ──
    fun completeProfile(localPhone: String, name: String, userType: String, classLevel: String) {
        val n = name.trim()
        if (n.isBlank()) { _authState.value = AuthState.Error("নাম লিখুন"); return }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = FirebaseAuthService.createProfile(n, localPhone, userType, classLevel, firebaseUrl)) {
                is AuthResult.Success -> {
                    val user = User.fromFirebaseMap(r.userData).copy(phone = localPhone, name = n)
                    session.saveUser(user)
                    FcmHelper.collectAndSaveForPhone(getApplication(), localPhone)
                    _authState.value = AuthState.Success(user)
                }
                is AuthResult.Error -> _authState.value = AuthState.Error(r.message)
            }
        }
    }

    // ── Google Sign-In (TRANSITIONAL — পরের ধাপে real Firebase Auth এ migrate হবে) ──
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

            when (val r = FirebaseAuthService.signupWithEmail(
                n, ph, email, finalPhotoUrl, userType, classLevel, firebaseUrl
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
        verificationId = null
        resendToken = null
        pendingE164 = null
        _authState.value = AuthState.Idle
    }

    fun setError(msg: String) {
        _authState.value = AuthState.Error(msg)
    }
}
