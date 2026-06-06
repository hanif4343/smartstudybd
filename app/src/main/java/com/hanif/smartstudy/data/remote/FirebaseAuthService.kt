package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object FirebaseAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun hashPassword(pass: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(pass.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── LOGIN (Direct Firebase Phone + Password) ──
    suspend fun verifyLogin(phone: String, password: String, firebaseUrl: String, secretKey: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "${firebaseUrl}Users/$phone.json?auth=$secretKey"
            Log.d("Login", "Fetching user from Firebase: $url")
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d("Login", "Firebase response: $body")

            if (body.isBlank() || body == "null") {
                return@withContext AuthResult.Error("এই ফোন নম্বর দিয়ে কোনো অ্যাকাউন্ট নেই")
            }

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val userMap: Map<String, Any> = gson.fromJson(body, type)

            val savedPassword = (userMap["Password"] ?: userMap["password"] ?: "").toString()
            val currentHashed = hashPassword(password)

            if (savedPassword == currentHashed) {
                val status = (userMap["Status"] ?: userMap["status"] ?: "Active").toString().lowercase()
                if (status == "inactive") {
                    AuthResult.Error("অ্যাকাউন্ট নিষ্ক্রিয়। Admin এর সাথে যোগাযোগ করুন।")
                } else {
                    AuthResult.Success(userMap)
                }
            } else {
                AuthResult.Error("পাসওয়ার্ড ভুল হয়েছে")
            }
        } catch (e: Exception) {
            Log.e("Login", "Error: ${e.message}")
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    // ── GOOGLE SIGN-IN (Direct Firebase Email Check) ──
    suspend fun googleSignIn(
        email: String,
        name: String,
        photoUrl: String,
        firebaseUrl: String,
        secretKey: String
    ): GoogleAuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "${firebaseUrl}Users.json?auth=$secretKey"
            Log.d("GoogleAuth", "Fetching all users from: $url")
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (body.isBlank() || body == "null") {
                return@withContext GoogleAuthResult.NewUser(email, name, photoUrl)
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
                Log.e("GoogleAuth", "Parse error: ${e.message}")
            }

            if (matchedUser != null) {
                val status = (matchedUser["Status"] ?: matchedUser["status"] ?: "Active").toString().lowercase()
                if (status == "inactive") {
                    return@withContext GoogleAuthResult.Error("অ্যাকাউন্ট নিষ্ক্রিয়। Admin-এর সাথে যোগাযোগ করুন।")
                }
                GoogleAuthResult.ExistingUser(matchedUser)
            } else {
                GoogleAuthResult.NewUser(email = email, name = name, photoUrl = photoUrl)
            }
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Error: ${e.message}")
            GoogleAuthResult.Error("সংযোগ সমস্যা: ${e.message}")
        }
    }

    // ── SIGNUP WITH EMAIL (Direct Firebase Put) ──
    suspend fun signupWithEmail(
        name: String,
        phone: String,
        email: String,
        password: String,
        picture: String,
        userType: String,
        classLevel: String,
        firebaseUrl: String,
        secretKey: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            // প্রথমে চেক করি এই ফোন নম্বর ইতিমধ্যে আছে কিনা
            val checkUrl = "${firebaseUrl}Users/$phone.json?auth=$secretKey"
            val checkReq = Request.Builder().url(checkUrl).get().build()
            val checkResp = client.newCall(checkReq).execute().body?.string() ?: ""
            if (checkResp.isNotBlank() && checkResp != "null") {
                return@withContext AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
            }

            val userData = mapOf(
                "Name" to name,
                "Phone" to phone,
                "Email" to email,
                "Password" to hashPassword(password),
                "Picture" to picture,
                "UserType" to userType,
                "ClassLevel" to classLevel,
                "Status" to "Active",
                "Role" to "User"
            )

            val jsonBody = gson.toJson(userData)
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val url = "${firebaseUrl}Users/$phone.json?auth=$secretKey"

            val req = Request.Builder().url(url).put(requestBody).build()
            val resp = client.newCall(req).execute()

            if (resp.isSuccessful) {
                AuthResult.Success(userData)
            } else {
                AuthResult.Error("Firebase-এ অ্যাকাউন্ট তৈরি করতে ব্যর্থ হয়েছে")
            }
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    // ── SIGNUP (Direct Firebase Put for Phone + Password) ──
    suspend fun signup(
        name: String,
        phone: String,
        password: String,
        userType: String,
        classLevel: String,
        firebaseUrl: String,
        secretKey: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val checkUrl = "${firebaseUrl}Users/$phone.json?auth=$secretKey"
            val checkReq = Request.Builder().url(checkUrl).get().build()
            val checkResp = client.newCall(checkReq).execute().body?.string() ?: ""
            if (checkResp.isNotBlank() && checkResp != "null") {
                return@withContext AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
            }

            val userData = mapOf(
                "Name" to name,
                "Phone" to phone,
                "Email" to "",
                "Password" to hashPassword(password),
                "Picture" to "",
                "UserType" to userType,
                "ClassLevel" to classLevel,
                "Status" to "Active",
                "Role" to "User"
            )

            val jsonBody = gson.toJson(userData)
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val url = "${firebaseUrl}Users/$phone.json?auth=$secretKey"

            val req = Request.Builder().url(url).put(requestBody).build()
            val resp = client.newCall(req).execute()

            if (resp.isSuccessful) {
                AuthResult.Success(userData)
            } else {
                AuthResult.Error("Firebase-এ অ্যাকাউন্ট তৈরি করতে ব্যর্থ হয়েছে")
            }
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }
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
