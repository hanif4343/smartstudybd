package com.hanif.smartstudy.util

import com.hanif.smartstudy.data.model.AppContent
import com.hanif.smartstudy.data.model.QBankItem
import com.hanif.smartstudy.data.model.QuizItem
import com.hanif.smartstudy.data.model.StudyItem
import com.hanif.smartstudy.data.model.User

/**
 * AudienceFilter — কেন্দ্রীয় audience tag ফিল্টার লজিক
 *
 * নিয়ম:
 *   ┌──────────────────────────────┬────────────────────────────────────────────────┐
 *   │ AudienceTags (content-এ)     │ কে দেখতে পারবে                                │
 *   ├──────────────────────────────┼────────────────────────────────────────────────┤
 *   │ ফাঁকা / null                 │ শুধু Job seeker                                │
 *   │                              │ (userType="Job" অথবা classLevel খালি)          │
 *   ├──────────────────────────────┼────────────────────────────────────────────────┤
 *   │ "Masters 1" / "Class 12" ইত্যাদি │ যার classLevel ওই মানের সাথে মিলে        │
 *   ├──────────────────────────────┼────────────────────────────────────────────────┤
 *   │ "Job"                        │ যার userType = "Job"                           │
 *   └──────────────────────────────┴────────────────────────────────────────────────┘
 *
 * Challenge-এ opponent matching:
 *   দুজন ইউজার একে অপরকে challenge করতে পারবে কেবল যদি তারা
 *   একই "audience group"-এ থাকে।
 */
object AudienceFilter {

    // ── Single item check ────────────────────────────────────

    /**
     * নিয়ম:
     *  content tag ফাঁকা   → শুধু Job Seeker দেখবে (userType="Job", classLevel ফাঁকা)
     *  content tag = "Job"  → শুধু Job Seeker দেখবে
     *  content tag = "Masters 1" ইত্যাদি → ওই classLevel এর student দেখবে
     *
     *  Job Seeker সংজ্ঞা: userType="Job" OR (Student কিন্তু classLevel ফাঁকা — unusual)
     */
    fun userCanSee(audienceTags: String?, user: User?, adminOverrideTag: String = "Job"): Boolean {
        // Admin override mode
        if (user?.isAdmin() == true) {
            val effectiveTag = adminOverrideTag.ifBlank { "Job" }
            val tag = audienceTags?.trim() ?: ""
            return if (tag.isBlank() || tag.equals("Job", ignoreCase = true))
                       effectiveTag.equals("Job", ignoreCase = true)
                   else tag.equals(effectiveTag.trim(), ignoreCase = true)
        }

        val tag = audienceTags?.trim() ?: ""
        val cl  = user?.classLevel?.trim() ?: ""
        val ut  = user?.userType?.trim()   ?: ""

        val isJobSeeker = ut.equals("Job", ignoreCase = true) || cl.isBlank()

        return if (tag.isBlank() || tag.equals("Job", ignoreCase = true)) {
            // ফাঁকা বা "Job" tag → শুধু Job Seeker দেখবে
            isJobSeeker
        } else {
            // নির্দিষ্ট tag (Masters 1, Class 10, Honours 2 etc.) → ঠিক সেই classLevel এর student
            tag.equals(cl, ignoreCase = true)
        }
    }

    // ── List filters — @JvmName দিয়ে JVM clash ঠেকানো ──────
    // Kotlin-এ generic List<T> JVM bytecode-এ একই signature হয়,
    // তাই প্রতিটাকে আলাদা @JvmName দিতে হবে।

    @JvmName("filterQuizForUser")
    fun List<QuizItem>.filterForUser(user: User?)  = filter { userCanSee(it.audienceTags, user) }

    @JvmName("filterQBankForUser")
    fun List<QBankItem>.filterForUser(user: User?) = filter { userCanSee(it.audienceTags, user) }

    @JvmName("filterStudyForUser")
    fun List<StudyItem>.filterForUser(user: User?) = filter { userCanSee(it.audienceTags, user) }

    // ── AppContent filtered view ─────────────────────────────

    fun AppContent.forUser(user: User?, adminOverrideTag: String = "") = copy(
        quiz   = quiz.filter   { userCanSee(it.audienceTags, user, adminOverrideTag) },
        qbank  = qbank.filter  { userCanSee(it.audienceTags, user, adminOverrideTag) },
        study  = study.filter  { userCanSee(it.audienceTags, user, adminOverrideTag) }
    )

    // ── Challenge opponent compatibility ─────────────────────
    /**
     * দুটো ইউজার একই audience group-এ কিনা চেক করো।
     *
     * Group নির্ধারণ:
     *   - classLevel খালি না হলে → classLevel-ই group key
     *   - classLevel খালি হলে → userType বা "Job" default
     */
    fun audienceGroupOf(user: User?): String {
        val cl = user?.classLevel?.trim() ?: ""
        val ut = user?.userType?.trim()   ?: ""
        return when {
            cl.isNotBlank()                     -> cl   // Student — classLevel = group key
            ut.equals("Job", ignoreCase = true) -> "Job"
            else                                -> "Job" // default = Job group
        }
    }

    /**
     * Returns true যদি দুজন ইউজার একে অপরকে challenge করতে পারে।
     * একই audience group হতে হবে।
     */
    fun canChallenge(me: User?, opponent: User?): Boolean {
        return audienceGroupOf(me).equals(audienceGroupOf(opponent), ignoreCase = true)
    }

    /**
     * Human-readable group label — UI-তে দেখানোর জন্য
     */
    fun audienceGroupLabel(user: User?): String {
        val group = audienceGroupOf(user)
        return when {
            group.equals("Job", ignoreCase = true)      -> "চাকরি (Job)"
            group.startsWith("Class",   ignoreCase = true) ->
                "${group.removePrefix("Class").trim()} শ্রেণি"
            group.startsWith("Masters", ignoreCase = true) ->
                "মাস্টার্স ${group.removePrefix("Masters").trim()} বর্ষ"
            group.startsWith("Honours", ignoreCase = true) ->
                "অনার্স ${group.removePrefix("Honours").trim()} বর্ষ"
            else -> group
        }
    }
}
