package com.hanif.smartstudy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hanif.smartstudy.util.SessionManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Re-schedule reminder alarm after reboot / app update
            val session = SessionManager(context)
            if (session.isReminderOn()) {
                ReminderReceiver.schedule(
                    context = context,
                    hour    = session.getReminderHour(),
                    minute  = session.getReminderMinute()
                )
            }
        }
    }
}
