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

    fun userCanSee(audienceTags: String?, user: User?): Boolean {
        val tag = audienceTags?.trim() ?: ""
        val cl  = user?.classLevel?.trim() ?: ""
        val ut  = user?.userType?.trim()   ?: ""

        return if (tag.isBlank()) {
            // ফাঁকা tag → শুধু Job seeker
            ut.equals("Job", ignoreCase = true) || cl.isBlank()
        } else {
            // নির্দিষ্ট tag → classLevel বা userType যেকোনোটা মিললেই হবে
            tag.equals(cl, ignoreCase = true) || tag.equals(ut, ignoreCase = true)
        }
    }

    // ── List filters ─────────────────────────────────────────

    fun List<QuizItem>.filterForUser(user: User?)  = filter { userCanSee(it.audienceTags, user) }
    fun List<QBankItem>.filterForUser(user: User?) = filter { userCanSee(it.audienceTags, user) }
    fun List<StudyItem>.filterForUser(user: User?) = filter { userCanSee(it.audienceTags, user) }

    // ── AppContent filtered view ─────────────────────────────

    fun AppContent.forUser(user: User?) = copy(
        quiz   = quiz.filterForUser(user),
        qbank  = qbank.filterForUser(user),
        study  = study.filterForUser(user)
    )

    // ── Challenge opponent compatibility ─────────────────────
    /**
     * দুটো ইউজার একই audience group-এ কিনা চেক করো।
     *
     * Group নির্ধারণ:
     *   - classLevel খালি না হলে → classLevel-ই group key
     *   - classLevel খালি হলে → userType বা "Job" default
     *
     * উদাহরণ:
     *   HSC (Class 12) ছাত্র → group = "Class 12"
     *   Job seeker            → group = "Job"
     *   Masters 1             → group = "Masters 1"
     */
    fun audienceGroupOf(user: User?): String {
        val cl = user?.classLevel?.trim() ?: ""
        val ut = user?.userType?.trim()   ?: ""
        return when {
            cl.isNotBlank()                    -> cl
            ut.equals("Job", ignoreCase = true) -> "Job"
            else                               -> "Job"   // fallback: untagged = Job group
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
            group.equals("Job", ignoreCase = true) -> "চাকরি (Job)"
            group.startsWith("Class",  ignoreCase = true) -> "${group.removePrefix("Class").trim()} শ্রেণি"
            group.startsWith("Masters", ignoreCase = true) -> "মাস্টার্স ${group.removePrefix("Masters").trim()} বর্ষ"
            group.startsWith("Honours", ignoreCase = true) -> "অনার্স ${group.removePrefix("Honours").trim()} বর্ষ"
            else -> group
        }
    }
}
