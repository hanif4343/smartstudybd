package com.hanif.smartstudy.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.hanif.smartstudy.R

/**
 * ══════════════════════════════════════════════════════════════════════════
 * Speed Plan সিদ্ধান্ত: "CDN Fail hole admin notify hobe and app cache a colbe"
 *
 * CDN read (manifest/reference/topic JSON) ব্যর্থ হলে (network/timeout/৪xx/৫xx)
 * app কোনো GAS-এ fallback করে না — সরাসরি Room cache থেকে দেখায়, আর এখানে
 * একটা **লোকাল** (device-only, কোনো network/GAS/Firebase কল ছাড়াই) notification
 * দেখায়। সিঙ্গেল-ইউজার অ্যাপ বলে এই ডিভাইসের ইউজারই admin — তাই এটাই
 * সবচেয়ে দ্রুত/নির্ভরযোগ্য alert channel (নেটওয়ার্কই যখন সমস্যা, তখন আরেকটা
 * নেটওয়ার্ক কল দিয়ে alert পাঠানো অর্থহীন)।
 *
 * পুরো অ্যাপ-সেশনে (process lifetime) বারবার একই স্প্যামি notification এড়াতে
 * সর্বনিম্ন গ্যাপ (NOTIFY_MIN_GAP_MS) মানা হয়।
 * ══════════════════════════════════════════════════════════════════════════
 */
object CdnFailureNotifier {

    private const val CHANNEL_ID = "cdn_failure_alerts"
    private const val NOTIFICATION_ID = 9001
    private const val NOTIFY_MIN_GAP_MS = 5 * 60_000L // একই সেশনে বারবার spam না করার জন্য

    @Volatile private var lastNotifiedAt: Long = 0L

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel(CHANNEL_ID, "CDN/সিঙ্ক সমস্যা", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "CDN থেকে কনটেন্ট আনতে না পারলে (cache থেকে দেখানো হচ্ছে) সতর্কতা" }
                    .also { nm.createNotificationChannel(it) }
            }
        }
    }

    /**
     * @param reason ছোট, নির্দিষ্ট বার্তা — যেমন "manifest.json আনা যায়নি" বা
     *   "QZ_S00_T03 আনা যায়নি" — যাতে কোন অংশে সমস্যা হয়েছে বোঝা যায়।
     */
    fun notify(context: Context, reason: String) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastNotifiedAt < NOTIFY_MIN_GAP_MS) return
            lastNotifiedAt = now

            createChannel(context)

            // Android 13+ এ runtime notification permission লাগে — না থাকলে
            // NotificationManagerCompat.notify() নিজে থেকেই silently কিছু করবে
            // না, এখানে আগেই চেক করে নিরাপদে বেরিয়ে যাচ্ছি (crash এড়াতে)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚠️ CDN থেকে ডেটা আনা যায়নি")
                .setContentText("$reason — পুরনো/cache করা ডেটা দেখানো হচ্ছে")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$reason — পুরনো/cache করা ডেটা দেখানো হচ্ছে"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, notif)
        } catch (e: Exception) {
            // notification দেখানো ব্যর্থ হলেও app flow-কে কোনোভাবেই ব্লক করা যাবে না
            android.util.Log.w("CdnFailureNotifier", "notify failed: ${e.message}")
        }
    }
}
