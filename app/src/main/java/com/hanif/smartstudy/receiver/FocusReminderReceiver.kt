package com.hanif.smartstudy.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hanif.smartstudy.MainActivity
import com.hanif.smartstudy.R
import com.hanif.smartstudy.focus.FocusModeConfig
import com.hanif.smartstudy.focus.FocusModeStore
import kotlinx.coroutines.runBlocking

// ═══════════════════════════════════════════════════════════════════
//  FocusReminderReceiver — ফোকাস মোড Part ৪
// ───────────────────────────────────────────────────────────────────
//  অ্যাপ ব্যাকগ্রাউন্ডে গেলে (SmartStudyApp.kt এর Activity lifecycle
//  ট্র্যাকার থেকে ডাকা হয়) প্রতি REMINDER_INTERVAL_MINUTES মিনিট পরপর
//  একটা নোটিফিকেশন — RoutineItemReminderReceiver.kt এর মতোই "নিজেই
//  পরের বারের alarm সেট করে" প্যাটার্নে, তাই AlarmManager এর
//  setRepeating() এর চেয়ে বেশি নির্ভরযোগ্য (Doze/App-standby তেও)।
//
//  সেফটি: প্রতিবার ফায়ার হওয়ার আগে/পরে ফোকাস মোড এখনও effectively
//  active কিনা চেক করা হয় — মাস্টার সুইচ বন্ধ, ইউজার নিজে বন্ধ করলে,
//  পরীক্ষার তারিখ পার হলে, বা ৭ দিনের hard-cap পার হলে — এই চেইন
//  নিজে থেকেই থেমে যায় (আর reschedule হয় না)।
// ═══════════════════════════════════════════════════════════════════
class FocusReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "focus_mode_reminder"

        // একটাই চলমান focus reminder থাকে — তাই fixed request/notification code
        private const val REQUEST_CODE = 934_611_207

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel(CHANNEL_ID, "ফোকাস মোড রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH)
                        .apply {
                            description = "পরীক্ষা কাছে থাকা অবস্থায় অ্যাপের বাইরে গেলে পড়ায় ফিরিয়ে আনার রিমাইন্ডার"
                            enableVibration(true)
                        }
                        .also { nm.createNotificationChannel(it) }
                }
            }
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, FocusReminderReceiver::class.java)
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** এই মুহূর্ত থেকে REMINDER_INTERVAL_MINUTES পরে পরের alarm-টা সেট করো */
        private fun scheduleNext(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + FocusModeConfig.REMINDER_INTERVAL_MINUTES * 60_000L
            val pi = pendingIntent(context)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        /**
         * অ্যাপ ব্যাকগ্রাউন্ডে গেলে ডাকা হয় (SmartStudyApp.kt থেকে)। ফোকাস মোড
         * এই মুহূর্তে effectively active থাকলেই শুধু রিপিটিং চেইন শুরু হয়।
         */
        fun startIfActive(context: Context) {
            if (!FocusModeConfig.ENABLED) return
            try {
                val state = runBlocking { FocusModeStore(context).getState() }
                if (state.isEffectivelyActive()) {
                    createChannel(context)
                    scheduleNext(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /** অ্যাপ ফোরগ্রাউন্ডে ফিরলে, বা ফোকাস মোড বন্ধ/রিসেট হলে — pending alarm বাতিল করো */
        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FocusReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { am.cancel(it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val state = runBlocking { FocusModeStore(context).getState() }
            // এখনও effectively active না থাকলে — চুপচাপ থেমে যাও, আর reschedule না।
            if (!state.isEffectivelyActive()) return

            showNotification(context, state.subject, state.daysUntilExam())
            scheduleNext(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotification(context: Context, subject: String, daysLeft: Int) {
        createChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val dayTxt = when {
            daysLeft <= 0 -> "আজই পরীক্ষা"
            daysLeft == 1 -> "আগামীকাল পরীক্ষা"
            else          -> "$daysLeft দিন পরে পরীক্ষা"
        }
        val body = "$subject পড়ায় ফিরে যাও — $dayTxt ⏰"

        val tapIntent = PendingIntent.getActivity(
            context, REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("type", "focus_reminder")
                putExtra("subject", subject)
                putExtra("url", "focus")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🎯 ফোকাস মোড")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        nm.notify(REQUEST_CODE, notif)
    }
}
