package com.hanif.smartstudy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SmartStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDER, "Study Reminder", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "পড়ার সময়ের reminder"; enableVibration(true) }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PUSH, "Smart Study Notifications", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "App notifications" }
            )
        }
    }

    companion object {
        const val CHANNEL_REMINDER = "smart_study_reminder"
        const val CHANNEL_PUSH     = "smart_study_channel"
    }
}
