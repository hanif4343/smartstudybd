package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object FirebaseAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun hashPassword(pass: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(pass.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── LOGIN (Phone + Password) ──
    suspend fun verifyLogin(phone: String, password: String, gasUrl: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$gasUrl?action=verifyLogin" + "&phone=${encode(phone)}" + "&password=${encode(password)}"
            Log.d("Login", "GAS URL: $url")
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d("Login", "GAS response: $body")
            parseGasLoginResponse(body, phone, password)
        } catch (e: Exception) {
            Log.e("Login", "Error: ${e.message}")
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    // ── GOOGLE SIGN-IN ──
    suspend fun googleSignIn(
        email: String,
        name: String,
        photoUrl: String,
        gasUrl: String,
        firebaseUrl: String,
        secretKey: String
    ): GoogleAuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "${firebaseUrl}Users.json?auth=$secretKey"
            Log.d("GoogleAuth", "Fetching users from: $url")
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (body.isBlank() || body == "null") {
                return@withContext GoogleAuthResult.Error("ডেটা লোড হয়নি")
            }

            val normEmail = email.trim().lowercase()
            var matchedUser: Map<String, Any>? = null
            try {
                val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                val usersMap: Map<String, Map<String, Any>> = gson.fromJson(body, type)
                matchedUser = usersMap.values.find { u ->
                    val uEmail = (u["Email"] ?: u["email"])?.toString()?.trim()?.lowercase() ?: ""
                    uEmail == normEmail
                }
            } catch (e: Exception) {
                try {
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val usersList: List<Map<String, Any>> = gson.fromJson(body, type)
                    matchedUser = usersList.filterNotNull().find { u ->
                        val uEmail = (u["Email"] ?: u["email"])?.toString()?.trim()?.lowercase() ?: ""
                        uEmail == normEmail
                    }
                } catch (e2: Exception) {
                    Log.e("GoogleAuth", "Parse error: ${e2.message}")
                }
            }

            if (matchedUser != null) {
                val status = (matchedUser["Status"] ?: matchedUser["status"] ?: "Active")
                    .toString().lowercase()
                if (status == "inactive") {
                    return@withContext GoogleAuthResult.Error("অ্যাকাউন্ট নিষ্ক্রিয়। Admin-এর সাথে যোগাযোগ করুন।")
                }
                GoogleAuthResult.ExistingUser(matchedUser)
            } else {
                GoogleAuthResult.NewUser(
                    email = email,
                    name = name,
                    photoUrl = photoUrl
                )
            }
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Error: ${e.message}")
            GoogleAuthResult.Error("সংযোগ সমস্যা: ${e.message}")
        }
    }

    // ── GOOGLE SIGNUP ──
    suspend fun googleSignup(
        name: String,
        email: String,
        phone: String,
        photoUrl: String,
        userType: String,
        classLevel: String,
        gasUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("action", "signup")
                .add("name", name)
                .add("phone", phone)
                .add("email", email)
                .add("password", hashPassword(email + "_google"))
                .add("userType", userType)
                .add("classLevel", classLevel)
                .add("picture", photoUrl)
                .add("status", "Active")
                .add("role", "User")
                .build()
            val req = Request.Builder().url(gasUrl).post(formBody).build()
            val resp = client.newCall(req).execute().body?.string() ?: ""
            Log.d("GoogleSignup", "GAS response: $resp")
            try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(resp, type)
                val s = (map["status"] ?: map["result"] ?: "").toString()
                if (s == "ok" || s == "success") {
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone, "Email" to email, "Picture" to photoUrl,
                        "UserType" to userType, "ClassLevel" to classLevel, "Status" to "Active", "Role" to "User"
                    ))
                } else {
                    val msg = (map["message"] ?: map["error"] ?: "Signup ব্যর্থ").toString()
                    if (msg.contains("already", true) || msg.contains("duplicate", true) || msg.contains("exists", true))
                        AuthResult.Error("এই ইমেইল আগে থেকেই নিবন্ধিত")
                    else AuthResult.Error(msg)
                }
            } catch (e: Exception) {
                if (resp.contains("ok", true) || resp.contains("success", true)) {
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone, "Email" to email, "Picture" to photoUrl,
                        "UserType" to userType, "ClassLevel" to classLevel, "Status" to "Active", "Role" to "User"
                    ))
                } else AuthResult.Error("Signup ব্যর্থ হয়েছে")
            }
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    private fun parseGasLoginResponse(body: String, phone: String, password: String): AuthResult {
        if (body.isBlank()) return AuthResult.Error("GAS থেকে কোনো response পাওয়া যায়নি")
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(body, type)
            Log.d("Login", "Parsed map: $map")
            val result = map["result"]?.toString() ?: ""
            val status = map["status"]?.toString() ?: ""
            when {
                result == "success" -> {
                    val userMap = (map["user"] as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }
                        ?.mapValues { it.value ?: "" } ?: map
                    @Suppress("UNCHECKED_CAST")
                    AuthResult.Success(userMap as Map<String, Any>)
                }
                status == "ok" -> AuthResult.Success(map)
                result == "error" || status == "error" -> {
                    val msg = (map["error"] ?: map["message"] ?: "লগইন ব্যর্থ").toString()
                    AuthResult.Error(translateError(msg))
                }
                else -> AuthResult.Error("অপ্রত্যাশিত response: $body")
            }
        } catch (e: Exception) {
            Log.e("Login", "Parse error: ${e.message}, body: $body")
            AuthResult.Error("Response parse করা যায়নি")
        }
    }

    private fun translateError(msg: String): String = when {
        msg.contains("wrong password", true) -> "পাসওয়ার্ড ভুল হয়েছে"
        msg.contains("not found", true) -> "এই ফোন নম্বর দিয়ে কোনো অ্যাকাউন্ট নেই"
        msg.contains("missing", true) -> "ফোন নম্বর ও পাসওয়ার্ড দিন"
        msg.contains("inactive", true) -> "অ্যাকাউন্ট নিষ্ক্রিয়। Admin এর সাথে যোগাযোগ করুন।"
        else -> msg
    }

    // ── SIGNUP (Phone + Password) ──
    suspend fun signup(
        name: String,
        phone: String,
        password: String,
        userType: String,
        classLevel: String,
        gasUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("action", "signup")
                .add("name", name)
                .add("phone", phone)
                .add("password", hashPassword(password))
                .add("userType", userType)
                .add("classLevel", classLevel)
                .add("status", "Active")
                .add("role", "User")
                .build()
            val req = Request.Builder().url(gasUrl).post(formBody).build()
            val resp = client.newCall(req).execute().body?.string() ?: ""
            Log.d("Signup", "GAS response: $resp")
            try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(resp, type)
                val s = (map["status"] ?: map["result"] ?: "").toString()
                if (s == "ok" || s == "success") {
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone, "UserType" to userType, "ClassLevel" to classLevel, "Status" to "Active", "Role" to "User"
                    ))
                } else {
                    val msg = (map["message"] ?: map["error"] ?: "Signup ব্যর্থ").toString()
                    if (msg.contains("already", true) || msg.contains("duplicate", true) || msg.contains("exists", true))
                        AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
                    else AuthResult.Error(msg)
                }
            } catch (e: Exception) {
                if (resp.contains("ok", true) || resp.contains("success", true)) {
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone, "UserType" to userType, "ClassLevel" to classLevel, "Status" to "Active", "Role" to "User"
                    ))
                } else AuthResult.Error("Signup ব্যর্থ হয়েছে")
            }
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    // ── SIGNUP WITH EMAIL (Google user — email + picture সহ) ──
    suspend fun signupWithEmail(
        name: String,
        phone: String,
        email: String,
        password: String,
        picture: String,
        userType: String,
        classLevel: String,
        gasUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("action", "signup")
                .add("name", name)
                .add("phone", phone)
                .add("email", email)
                .add("password", hashPassword(password))
                .add("picture", picture)
                .add("userType", userType)
                .add("classLevel", classLevel)
                .add("status", "Active")
                .add("role", "User")
                .build()
            val req = Request.Builder().url(gasUrl).post(formBody).build()
            val resp = client.newCall(req).execute().body?.string() ?: ""
            Log.d("GoogleSignup", "GAS response: $resp")
            try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(resp, type)
                val s = (map["status"] ?: map["result"] ?: "").toString()
                if (s == "ok" || s == "success") {
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone, "Email" to email, "Picture" to picture,
                        "UserType" to userType, "ClassLevel" to classLevel, "Status" to "Active", "Role" to "User"
                    ))
                } else {
                    val msg = (map["message"] ?: map["error"] ?: "Signup ব্যর্থ").toString()
                    if (msg.contains("already", true) || msg.contains("duplicate", true) || msg.contains("exists", true))
                        AuthResult.Error("এই ফোন নম্বর বা Email আগে থেকেই নিবন্ধিত")
                    else AuthResult.Error(msg)
                }
            } catch (e: Exception) {
                if (resp.contains("ok", true) || resp.contains("success", true)) {
                    AuthResult.Success(mapOf(
                        "Name" to name, "Phone" to phone, "Email" to email, "Picture" to picture,
                        "UserType" to userType, "ClassLevel" to classLevel, "Status" to "Active", "Role" to "User"
                    ))
                } else AuthResult.Error("Signup ব্যর্থ হয়েছে: $resp")
            }
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

sealed class AuthResult {
    data class Success(val userData: Map<String, Any>) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class GoogleAuthResult {
    data class ExistingUser(val userData: Map<String, Any>) : GoogleAuthResult()
    data class NewUser(val email: String, val name: String, val photoUrl: String) : GoogleAuthResult()
    data class Error(val message: String) : GoogleAuthResult()
}
