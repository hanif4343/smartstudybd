package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
 * Real Firebase Auth (Google/Phone OTP sign-in, অথবা সেসবের আগে Anonymous)
 * দিয়ে যা-ই সাইন-ইন করা থাকুক, তার ID token রিটার্ন করে — প্রতিটা REST call
 * এ ব্যবহৃত হয়, কোনো master DB secret আর ব্যবহার হয় না।
 *
 * ✅ আগে এখানে "কোনো signed-in user না থাকলে" পুরো database access দেওয়া
 * BuildConfig.FIREBASE_DB_SECRET এ fallback করতো — যেটা APK decompile করে
 * বের করে ফেলা সম্ভব ছিল (minifyEnabled=false থাকায় আরও সহজ), মানে যে কেউ
 * পুরো ডেটাবেসে read/write করতে পারতো। এখন সেই fallback সম্পূর্ণ সরিয়ে,
 * বদলে Firebase Anonymous Auth ব্যবহার করা হয় — app চালু হওয়ার সাথে সাথেই
 * (SmartStudyApp.onCreate) signInAnonymously() কল হয়ে যায়, তাই বাস্তবে
 * "currentUser == null" অবস্থা প্রায় কখনোই ঘটে না। Google/Phone sign-in
 * হয়ে গেলে সেই real user-ই currentUser হয়ে যায়, ততক্ষণ anonymous user-ই
 * auth != null শর্ত পূরণ করে (কিন্তু কোনো master-secret-level অ্যাক্সেস দেয় না)।
 */
object FirebaseTokenProvider {

    private const val TAG = "FBToken"

    suspend fun getToken(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: signInAnonymously()
        if (user == null) {
            Log.e(TAG, "No token available — anonymous sign-in also failed. এই কলটা auth ছাড়াই যাবে, Firebase Rules এ প্রত্যাখ্যাত হবে।")
            return ""
        }
        return try {
            user.getIdToken(false).awaitTask().token ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "getIdToken failed: ${e.message}")
            ""
        }
    }

    private suspend fun signInAnonymously(): FirebaseUser? {
        return try {
            val result = FirebaseAuth.getInstance().signInAnonymously().awaitTask()
            Log.d(TAG, "Anonymous sign-in OK: ${result.user?.uid}")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed: ${e.message}")
            null
        }
    }

    suspend fun refreshToken(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: return getToken()
        return try {
            user.getIdToken(true).awaitTask().token ?: getToken()
        } catch (e: Exception) {
            getToken()
        }
    }

    /** App চালু হওয়ার সাথে সাথেই কল হয় (SmartStudyApp.onCreate) — currentUser
     *  না থাকলে সাথে সাথে anonymous sign-in করে ফেলে, যাতে পরের প্রতিটা
     *  Firebase কল-এর আগে "no signed-in user" অবস্থাই না থাকে। */
    suspend fun ensureSignedIn() {
        val existing = FirebaseAuth.getInstance().currentUser
        if (existing != null) {
            Log.d(TAG, "Already signed in as ${existing.uid}")
            return
        }
        signInAnonymously()
    }
}
