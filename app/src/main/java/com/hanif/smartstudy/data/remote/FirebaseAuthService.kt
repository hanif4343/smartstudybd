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

    /** Gson সব number কে Double করে — এই function সঠিক String দেয় */
    private fun anyToString(value: Any?): String {
        if (value == null) return ""
        return when (value) {
            is Double -> {
                if (value == Math.floor(value) && !value.isInfinite()) {
                    value.toLong().toString()
                } else value.toString()
            }
            is Long    -> value.toString()
            is Int     -> value.toString()
            is Boolean -> value.toString()
            else       -> value.toString()
        }
    }

    /** Firebase Auth ID Token দিয়ে auth query param বানাও */
    private suspend fun authQuery(): String {
        val token = FirebaseTokenProvider.getToken()
        return if (token.isNotBlank()) "?auth=$token" else ""
    }

    /**
     * Users node থেকে phone দিয়ে user খোঁজে।
     * key = phone number অথবা numeric index — দুটোই handle করে।
     */
    private suspend fun findUserByPhone(phone: String, firebaseUrl: String): Map<String, Any>? =
        withContext(Dispatchers.IO) {
            try {
                val normPhone = phone.trim()
                val auth = authQuery()
                val allUrl = "${firebaseUrl}Users.json$auth"
                Log.d("Login", "Scanning users for phone: $normPhone")
                val allReq  = Request.Builder().url(allUrl).get().build()
                val allResp = client.newCall(allReq).execute()
                val allBody = allResp.body?.string() ?: ""

                if (allBody.isBlank() || allBody == "null") return@withContext null

                val type = object : TypeToken<Map<String, Any>>() {}.type
                val rawMap: Map<String, Any> = gson.fromJson(allBody, type)

                for ((key, value) in rawMap) {
                    when {
                        key == normPhone -> {
                            if (value is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                return@withContext value as Map<String, Any>
                            }
                        }
                        value is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val userMap = value as Map<String, Any>
                            val storedPhone = anyToString(userMap["Phone"] ?: userMap["phone"]).trim()
                            if (storedPhone == normPhone) {
                                Log.d("Login", "Found: ${userMap["Name"]}")
                                return@withContext userMap
                            }
                        }
                    }
                }
                Log.d("Login", "User not found: $normPhone")
                null
            } catch (e: Exception) {
                Log.e("Login", "findUserByPhone error: ${e.message}")
                null
            }
        }

    // ── LOGIN ──
    suspend fun verifyLogin(phone: String, password: String, firebaseUrl: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val userMap = findUserByPhone(phone, firebaseUrl)
                    ?: return@withContext AuthResult.Error("এই ফোন নম্বর দিয়ে কোনো অ্যাকাউন্ট নেই")

                val savedPassword  = anyToString(userMap["Password"] ?: userMap["password"] ?: "")
                val inputPassword  = password.trim()
                val currentHashed  = hashPassword(inputPassword)

                val passwordMatches = savedPassword == currentHashed || savedPassword == inputPassword

                if (passwordMatches) {
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

    // ── GOOGLE SIGN-IN ──
    suspend fun googleSignIn(
        email: String,
        name: String,
        photoUrl: String,
        firebaseUrl: String
    ): GoogleAuthResult = withContext(Dispatchers.IO) {
        try {
            val auth = authQuery()
            val url  = "${firebaseUrl}Users.json$auth"
            Log.d("GoogleAuth", "Fetching users from Firebase")
            val req  = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (body.isBlank() || body == "null") {
                return@withContext GoogleAuthResult.NewUser(email, name, photoUrl)
            }

            val normEmail    = email.trim().lowercase()
            var matchedUser: Map<String, Any>? = null

            try {
                val type   = object : TypeToken<Map<String, Any>>() {}.type
                val rawMap: Map<String, Any> = gson.fromJson(body, type)
                for ((_, value) in rawMap) {
                    if (value is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        val userMap = value as Map<String, Any>
                        val uEmail  = (userMap["Email"] ?: userMap["email"])
                            ?.toString()?.trim()?.lowercase() ?: ""
                        if (uEmail == normEmail) { matchedUser = userMap; break }
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleAuth", "Parse error: ${e.message}")
            }

            if (matchedUser != null) {
                val status = (matchedUser["Status"] ?: matchedUser["status"] ?: "Active")
                    .toString().lowercase()
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

    // ── SIGNUP WITH EMAIL (Google) ──
    suspend fun signupWithEmail(
        name: String,
        phone: String,
        email: String,
        password: String,
        picture: String,
        userType: String,
        classLevel: String,
        firebaseUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val existing = findUserByPhone(phone, firebaseUrl)
            if (existing != null) {
                return@withContext AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
            }

            val userData = mapOf(
                "Name"       to name,
                "Phone"      to phone,
                "Email"      to email,
                "Password"   to hashPassword(password),
                "Picture"    to picture,
                "UserType"   to userType,
                "ClassLevel" to classLevel,
                "Status"     to "Active",
                "Role"       to "User"
            )

            val auth        = authQuery()
            val url         = "${firebaseUrl}Users/${phone}.json$auth"
            val requestBody = gson.toJson(userData).toRequestBody(JSON_MEDIA_TYPE)
            val req         = Request.Builder().url(url).put(requestBody).build()
            val resp        = client.newCall(req).execute()

            if (resp.isSuccessful) AuthResult.Success(userData)
            else AuthResult.Error("Firebase-এ অ্যাকাউন্ট তৈরি করতে ব্যর্থ হয়েছে (HTTP ${resp.code})")
        } catch (e: Exception) {
            AuthResult.Error("নেটওয়ার্ক সমস্যা: ${e.message}")
        }
    }

    // ── SIGNUP (Phone + Password) ──
    suspend fun signup(
        name: String,
        phone: String,
        password: String,
        userType: String,
        classLevel: String,
        firebaseUrl: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val existing = findUserByPhone(phone, firebaseUrl)
            if (existing != null) {
                return@withContext AuthResult.Error("এই ফোন নম্বর আগে থেকেই নিবন্ধিত")
            }

            val userData = mapOf(
                "Name"       to name,
                "Phone"      to phone,
                "Email"      to "",
                "Password"   to hashPassword(password),
                "Picture"    to "",
                "UserType"   to userType,
                "ClassLevel" to classLevel,
                "Status"     to "Active",
                "Role"       to "User"
            )

            val auth        = authQuery()
            val url         = "${firebaseUrl}Users/${phone}.json$auth"
            val requestBody = gson.toJson(userData).toRequestBody(JSON_MEDIA_TYPE)
            val req         = Request.Builder().url(url).put(requestBody).build()
            val resp        = client.newCall(req).execute()

            if (resp.isSuccessful) AuthResult.Success(userData)
            else AuthResult.Error("Firebase-এ অ্যাকাউন্ট তৈরি করতে ব্যর্থ হয়েছে (HTTP ${resp.code})")
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
