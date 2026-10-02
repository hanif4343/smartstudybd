package com.hanif.smartstudy.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * ফোনে বাকি অ্যাপে কতক্ষণ সময় গেল — এটা পড়তে Android-এর বিশেষ "Usage Access" পারমিশন লাগে,
 * যেটা সাধারণ রানটাইম পারমিশন ডায়ালগ দিয়ে দেওয়া যায় না — ইউজারকে নিজে থেকে Settings-এ গিয়ে
 * চালু করতে হয়। এই ফাইল শুধু অনুমতি থাকলে ডেটা পড়ে; না থাকলে Settings পেজে নিয়ে যাওয়ার
 * শর্টকাট দেয় — জোর করে চালু করানো সম্ভব না (এটা Android-এর ইচ্ছাকৃত প্রাইভেসি সুরক্ষা)।
 */
object DeviceUsageStats {

    data class AppUsage(val label: String, val packageName: String, val millis: Long)
    data class TodayUsage(val totalMillis: Long, val apps: List<AppUsage>)

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** ফোনের Settings-এর Usage Access পেজে নিয়ে যায় — ইউজার নিজে চালু করবে। */
    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try { data = Uri.fromParts("package", context.packageName, null) } catch (_: Exception) {}
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // কিছু ডিভাইসে package-নির্দিষ্ট URI সাপোর্ট করে না — প্লেইন সেটিংস পেজ fallback
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** আজকের (স্থানীয় মধ্যরাত থেকে এখন পর্যন্ত) প্রতিটা অ্যাপের ব্যবহারের সময়, বড় থেকে ছোট ক্রমে।
     *  SmartStudy নিজেকে বাদ দেওয়া হয় — সেটা AppUsageTracker দিয়েই দেখানো হয়, আরও নির্ভুলভাবে। */
    suspend fun getTodayOtherAppsUsage(context: Context, limit: Int = 10): TodayUsage = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext TodayUsage(0L, emptyList())
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val end   = System.currentTimeMillis()
            val statsMap = usm.queryAndAggregateUsageStats(start, end)
            val pm = context.packageManager
            val myPkg = context.packageName

            var total = 0L
            val apps = statsMap.values
                .filter { it.totalTimeInForeground > 0 && it.packageName != myPkg }
                .mapNotNull { st ->
                    total += st.totalTimeInForeground
                    try {
                        val ai: ApplicationInfo = pm.getApplicationInfo(st.packageName, 0)
                        // সিস্টেম লঞ্চার/ইনপুট-মেথড ইত্যাদি বাদ — ইউজার আসলে "ব্যবহার করেছে" এমন অ্যাপ
                        if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) return@mapNotNull null
                        AppUsage(
                            label       = pm.getApplicationLabel(ai).toString(),
                            packageName = st.packageName,
                            millis      = st.totalTimeInForeground
                        )
                    } catch (_: Exception) { null } // uninstall হয়ে যাওয়া অ্যাপ
                }
                .sortedByDescending { it.millis }
                .take(limit)
            TodayUsage(total, apps)
        } catch (_: Exception) {
            TodayUsage(0L, emptyList())
        }
    }
}
