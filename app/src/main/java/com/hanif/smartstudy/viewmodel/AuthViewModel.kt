package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.User
import com.hanif.smartstudy.data.remote.GasApiService
import com.hanif.smartstudy.data.remote.GasResult
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

    fun login(phone: String, password: String) {
        if (phone.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("ফোন নম্বর ও পাসওয়ার্ড দিন")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = GasApiService.login(phone.trim(), password)) {
                is GasResult.Success -> {
                    val d = r.data
                    val user = User(
                        phone      = d["phone"]?.toString() ?: phone,
                        name       = d["name"]?.toString(),
                        role       = d["role"]?.toString() ?: "User",
                        picture    = d["picture"]?.toString(),
                        userType   = d["userType"]?.toString(),
                        classLevel = d["classLevel"]?.toString(),
                        xp         = (d["xp"] as? Double)?.toInt() ?: 0
                    )
                    session.saveUser(user)
                    _authState.value = AuthState.Success(user)
                }
                is GasResult.Error ->
                    _authState.value = AuthState.Error(r.message)
            }
        }
    }

    fun signup(
        name: String, phone: String, password: String,
        confirmPass: String, userType: String, classLevel: String
    ) {
        when {
            name.isBlank()        -> { _authState.value = AuthState.Error("নাম দিন"); return }
            phone.length < 11     -> { _authState.value = AuthState.Error("সঠিক ফোন নম্বর দিন"); return }
            password.length < 6   -> { _authState.value = AuthState.Error("পাসওয়ার্ড কমপক্ষে ৬ অক্ষর"); return }
            password != confirmPass -> { _authState.value = AuthState.Error("পাসওয়ার্ড মিলছে না"); return }
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val r = GasApiService.signup(name.trim(), phone.trim(), password, userType, classLevel)) {
                is GasResult.Success -> {
                    val user = User(
                        phone      = phone.trim(),
                        name       = name.trim(),
                        role       = "User",
                        userType   = userType,
                        classLevel = classLevel
                    )
                    session.saveUser(user)
                    _authState.value = AuthState.Success(user)
                }
                is GasResult.Error ->
                    _authState.value = AuthState.Error(r.message)
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
