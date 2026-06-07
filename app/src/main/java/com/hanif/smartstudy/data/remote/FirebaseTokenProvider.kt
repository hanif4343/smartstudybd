package com.hanif.smartstudy.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Firebase Anonymous Auth দিয়ে ID Token নিয়ে আসে।
 * এই token দিয়ে REST API call করলে Firebase Rules সুরক্ষিত থাকে।
 *
 * Rules:
 *   ".read":  "auth != null"
 *   ".write": "auth != null"
 *
 * Token 1 ঘণ্টায় expire হয় — Firebase SDK নিজেই refresh করে।
 */
object FirebaseTokenProvider {

    private const val TAG = "FBToken"
    private val auth = FirebaseAuth.getInstance()

    /**
     * Valid ID token return করে।
     * Anonymous user না থাকলে sign in করে, তারপর token নেয়।
     * Token expire হলে Firebase SDK নিজেই নতুন নেয়।
     */
    suspend fun getToken(): String {
        return try {
            // ইতিমধ্যে signed in থাকলে শুধু token নাও
            val user = auth.currentUser ?: run {
                Log.d(TAG, "No current user — signing in anonymously")
                auth.signInAnonymously().await().user
            }
            if (user == null) {
                Log.e(TAG, "Anonymous sign-in failed — returning empty token")
                return ""
            }
            val token = user.getIdToken(false).await().token ?: ""
            Log.d(TAG, "Token obtained (${token.length} chars)")
            token
        } catch (e: Exception) {
            Log.e(TAG, "getToken error: ${e.message}")
            ""
        }
    }

    /**
     * Force refresh — token সমস্যা হলে ব্যবহার করো
     */
    suspend fun refreshToken(): String {
        return try {
            val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: return ""
            val token = user.getIdToken(true).await().token ?: ""
            Log.d(TAG, "Token refreshed (${token.length} chars)")
            token
        } catch (e: Exception) {
            Log.e(TAG, "refreshToken error: ${e.message}")
            ""
        }
    }

    /** App start এ একবার call করো — background এ sign in নিশ্চিত করে */
    fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { Log.d(TAG, "Anonymous sign-in success: ${it.user?.uid}") }
                .addOnFailureListener { Log.e(TAG, "Anonymous sign-in failed: ${it.message}") }
        } else {
            Log.d(TAG, "Already signed in: ${auth.currentUser?.uid}")
        }
    }
}
