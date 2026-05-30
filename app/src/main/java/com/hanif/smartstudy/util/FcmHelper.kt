package com.hanif.smartstudy.util

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.hanif.smartstudy.data.remote.UserSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM Token সংগ্রহ করে Firebase এ save করে।
 * App start এবং token refresh এ call হয়।
 */
object FcmHelper {

    private val TAG = "FcmHelper"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun collectAndSave(app: Application) {
        val session = SessionManager(app)
        val phone   = session.getCurrentUser()?.phone ?: return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "FCM token: $token")
                scope.launch {
                    UserSyncService.saveFcmToken(phone, token)
                    // Also save locally
                    val user = session.getCurrentUser() ?: return@launch
                    session.saveUser(user.copy(fcmToken = token))
                }
            }
            .addOnFailureListener { Log.e(TAG, "FCM token error: ${it.message}") }
    }
}

/**
 * App active/inactive এর রিপোর্ট Firebase এ পাঠায়।
 * MainActivity onResume/onPause থেকে call হয়।
 */
object ActivityReporter {

    private val TAG   = "ActivityReporter"
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
