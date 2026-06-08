package com.hanif.smartstudy.data.remote

import android.util.Log
import com.hanif.smartstudy.BuildConfig

/**
 * Firebase Database Secret দিয়ে auth param দেয়।
 * Anonymous Auth এর dependency নেই — সব REST call এ কাজ করে।
 */
object FirebaseTokenProvider {

    private const val TAG = "FBToken"

    suspend fun getToken(): String {
        val secret = BuildConfig.FIREBASE_DB_SECRET
        if (secret.isNotBlank()) {
            Log.d(TAG, "Using DB secret (${secret.length} chars)")
        } else {
            Log.e(TAG, "FIREBASE_DB_SECRET is blank!")
        }
        return secret
    }

    suspend fun refreshToken(): String = getToken()

    fun ensureSignedIn() {
        Log.d(TAG, "ensureSignedIn: using DB secret, no Firebase Auth needed")
    }
}
