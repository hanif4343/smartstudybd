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
            ReminderReceiver.scheduleMorning(context, session.getMorningHour(), session.getMorningMinute(), session.isMorningRepeatDaily())
        }
        // Night reminder reschedule
        if (session.isNightReminderOn()) {
            ReminderReceiver.scheduleNight(context, session.getNightHour(), session.getNightMinute(), session.isNightRepeatDaily())
        }
        // Midday progress check reschedule
        if (session.isMiddayReminderOn()) {
            ReminderReceiver.scheduleMidday(context, session.getMiddayHour(), session.getMiddayMinute(), session.isMiddayRepeatDaily())
        }
        // Evening urgency check reschedule
        if (session.isEveningReminderOn()) {
            ReminderReceiver.scheduleEvening(context, session.getEveningHour(), session.getEveningMinute(), session.isEveningRepeatDaily())
        }

        // Daily Routine — প্রতি আইটেমের নিজস্ব রিমাইন্ডার (reboot/আপডেটের পরও বহাল থাকুক)
        RoutineItemReminderReceiver.rescheduleAll(context)
    }
}
