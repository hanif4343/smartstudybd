package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.hanif.smartstudy.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ছোট নিজস্ব Task<T> awaiter — kotlinx-coroutines-play-services dependency
// ছাড়াই কাজ করার জন্য (এই প্রজেক্টে আগে থেকে সেটা নেই)। FirebaseAuth এর
// যেকোনো Task<T> রিটার্নকারী কলে ব্যবহার করা যায় (suspend ফাংশন থেকে)।
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
}

/**
 * Real Firebase Auth (Phone OTP) দিয়ে sign in করা থাকলে তার ID token রিটার্ন
 * করে — এটাই এখন থেকে প্রতিটা REST call এ ব্যবহৃত হয়, কোনো master secret না।
 *
 * ⚠️ TRANSITIONAL: Google Sign-In এখনো real Firebase Auth এর মধ্য দিয়ে যায়
 * না (সেটা পরের ধাপে migrate হবে), তাই সেই একটা ছোট windows-এ (Google
 * sign-in/signup চলাকালীন, phone OTP দিয়ে sign-in করার আগে) কোনো currentUser
 * থাকে না — তখন সাময়িকভাবে legacy secret এ fallback করে যাতে সেই flow ভেঙে
 * না যায়। Phone OTP দিয়ে sign-in করা থাকলে এই fallback কখনো ব্যবহৃত হয় না।
 */
object FirebaseTokenProvider {

    private const val TAG = "FBToken"

    suspend fun getToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            return try {
                val result = user.getIdToken(false).awaitTask()
                result.token ?: legacyFallback("ID token null")
            } catch (e: Exception) {
                Log.e(TAG, "getIdToken failed: ${e.message}")
                legacyFallback("getIdToken exception")
            }
        }
        return legacyFallback("no signed-in user yet")
    }

    private fun legacyFallback(reason: String): String {
        Log.w(TAG, "Falling back to legacy DB secret ($reason) — এই path টা migrate করা বাকি আছে")
        return BuildConfig.FIREBASE_DB_SECRET
    }

    suspend fun refreshToken(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: return getToken()
        return try {
            user.getIdToken(true).awaitTask().token ?: getToken()
        } catch (e: Exception) {
            getToken()
        }
    }

    fun ensureSignedIn() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, if (uid != null) "Signed in as $uid" else "No signed-in user (legacy fallback active)")
    }
}
