package com.hanif.smartstudy.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.data.remote.AuthResult
import com.hanif.smartstudy.data.remote.FirebaseAuthService
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val gasUrl: String get() = try {
        BuildConfig.GAS_URL
    } catch (e: Exception) { "" }

    fun login(phone: String, password: String) {
        val ph = phone.trim()
        val pw = password.trim()
        if (ph.isBlank()) { _authState.value = AuthState.Error("ফোন নম্বর দিন"); return }
        if (pw.isBlank()) { _authState.value = AuthState.Error("পাসওয়ার্ড দিন"); return }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            Log.d("Auth", "Login: $ph")

            // GAS verifyLogin — plain text ও hash দুটোই handle করে
            when (val r = FirebaseAuthService.verifyLogin(ph, pw, gasUrl)) {
                is AuthResult.Success -> {
                    val user = User.fromFirebaseMap(r.userData)
                        .copy(phone = ph)
                    session.saveUser(user)
                    Log.d("Auth", "Login success: ${user.name}")
                    _authState.value = AuthState.Success(user)
                }
                is AuthResult.Error -> {
                    Log.d("Auth", "Login failed: ${r.message}")
                    _authState.value = AuthState.Error(r.message)
                }
            }
        }
    }

    fun signup(
        name: String, phone: String, password: String,
        confirmPass: String, userType: String, classLevel: String
    ) {
        val n  = name.trim()
        val ph = phone.trim()
        val pw = password.trim()
        val cp = confirmPass.trim()

        when {
            n.isBlank()    -> { _authState.value = AuthState.Error("নাম লিখুন"); return }
            ph.length < 11 -> { _authState.value = AuthState.Error("সঠিক ১১ সংখ্যার ফোন নম্বর দিন"); return }
            pw.length < 6  -> { _authState.value = AuthState.Error("পাসওয়ার্ড কমপক্ষে ৬ অক্ষর"); return }
            pw != cp       -> { _authState.value = AuthState.Error("পাসওয়ার্ড দুটো মিলছে না"); return }
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = FirebaseAuthService.signup(n, ph, pw, userType, classLevel, gasUrl)) {
                is AuthResult.Success -> {
                    val user = User.fromFirebaseMap(r.userData)
                        .copy(phone = ph, name = n)
                    session.saveUser(user)
                    _authState.value = AuthState.Success(user)
                }
                is AuthResult.Error ->
                    _authState.value = AuthState.Error(r.message)
            }
        }
    }

    fun resetState() { _authState.value = AuthState.Idle }
}
