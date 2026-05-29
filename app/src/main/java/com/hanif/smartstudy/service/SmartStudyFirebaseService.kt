package com.hanif.smartstudy.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SmartStudyFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // Phase 6 এ GAS এ save হবে
    }
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Phase 6 এ notification দেখাবে
    }
}
