package com.hanif.smartstudy.data.local

import android.content.Context
import java.util.Calendar

// ── Smart Routine: লক্ষ্য (পরীক্ষা, তারিখ, দৈনিক সময়, সাপ্তাহিক ছুটি) ──
// শুধু ফোনে (SharedPreferences) থাকে; কনটেন্ট বা Firebase-এর সাথে কোনো সম্পর্ক নেই।
data class RoutineGoal(
    val exam        : String = "",        // যেমন "BCS (প্রিলি + লিখিত)"
    val examDate    : String = "",        // yyyy-MM-dd, খালি = সেট করা হয়নি
    val dailyMinutes: Int    = 120,       // দৈনিক পড়ার লক্ষ্য (মিনিট)
    val offDay      : Int    = 0          // Calendar.DAY_OF_WEEK (1=রবি … 7=শনি); 0 = ছুটি নেই
) {
    val isSet: Boolean get() = exam.isNotBlank()

    /** পরীক্ষা পর্যন্ত বাকি দিন; তারিখ না থাকলে বা পেরিয়ে গেলে null */
    fun daysLeft(now: Calendar = Calendar.getInstance()): Int? {
        val p = examDate.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        val exam = Calendar.getInstance().apply {
            set(y, m - 1, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diff = ((exam.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
        return if (diff >= 0) diff else null
    }

    fun isOffToday(now: Calendar = Calendar.getInstance()): Boolean =
        offDay in 1..7 && now.get(Calendar.DAY_OF_WEEK) == offDay
}

class RoutineGoalStore(context: Context) {
    private val prefs = context.getSharedPreferences("routine_goal_prefs", Context.MODE_PRIVATE)

    fun load(): RoutineGoal = RoutineGoal(
        exam         = prefs.getString("exam", "") ?: "",
        examDate     = prefs.getString("date", "") ?: "",
        dailyMinutes = prefs.getInt("minutes", 120).coerceIn(30, 720),
        offDay       = prefs.getInt("off", 0).coerceIn(0, 7)
    )

    fun save(goal: RoutineGoal) {
        prefs.edit()
            .putString("exam", goal.exam.trim())
            .putString("date", goal.examDate)
            .putInt("minutes", goal.dailyMinutes.coerceIn(30, 720))
            .putInt("off", goal.offDay.coerceIn(0, 7))
            .apply()
    }
}
