package com.hanif.smartstudy.util

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.hanif.smartstudy.data.remote.UserSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM Token সংগ্রহ করে Firebase এ save করে।
 * App start এবং login success এর পরেও call হয়।
 */
object FcmHelper {

    private const val TAG = "FcmHelper"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * App start এ call করো — login থাকলে token save হবে
     */
    fun collectAndSave(app: Application) {
        val session = SessionManager(app)
        val phone   = session.getCurrentUser()?.phone ?: run {
            Log.d(TAG, "No logged-in user, skip FCM token save")
            return
        }
        collectAndSaveForPhone(app, phone)
    }

    /**
     * Login success এর পরে call করো — phone number দিয়ে
     */
    fun collectAndSaveForPhone(context: Context, phone: String) {
        if (phone.isBlank()) return
        val session = SessionManager(context)

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "FCM token: $token")
                scope.launch {
                    UserSyncService.saveFcmToken(phone, token)
                    // Local session এও save করো
                    val user = session.getCurrentUser() ?: return@launch
                    session.saveUser(user.copy(fcmToken = token))
                    Log.d(TAG, "FCM token saved for $phone")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "FCM token error: ${e.message}")
            }
    }
}

/**
 * App active/inactive এর রিপোর্ট Firebase এ পাঠায়।
 * MainActivity onResume/onPause থেকে call হয়।
 */
object ActivityReporter {

    private const val TAG = "ActivityReporter"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun reportActive(app: Application) {
        val phone = SessionManager(app).getCurrentUser()?.phone ?: return
        scope.launch {
            UserSyncService.reportActivity(phone, isActive = true)
            Log.d(TAG, "$phone → active")
        }
    }

    fun reportInactive(app: Application) {
        val phone = SessionManager(app).getCurrentUser()?.phone ?: return
        scope.launch {
            UserSyncService.reportActivity(phone, isActive = false)
            Log.d(TAG, "$phone → inactive")
        }
    }
}
