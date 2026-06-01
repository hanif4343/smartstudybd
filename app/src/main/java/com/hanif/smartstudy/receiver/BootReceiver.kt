package com.hanif.smartstudy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hanif.smartstudy.util.SessionManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val session = SessionManager(context)

        // Morning reminder reschedule
        if (session.isMorningReminderOn()) {
            ReminderReceiver.scheduleMorning(context, session.getMorningHour(), session.getMorningMinute())
        }
        // Night reminder reschedule
        if (session.isNightReminderOn()) {
            ReminderReceiver.scheduleNight(context, session.getNightHour(), session.getNightMinute())
        }
    }
}
