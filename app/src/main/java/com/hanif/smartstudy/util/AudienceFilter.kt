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
    fun List<QuizItem>.filterForUser(user: User?)  = filterNotNull().filter { userCanSee(it.audienceTags, user) }

    @JvmName("filterQBankForUser")
    fun List<QBankItem>.filterForUser(user: User?) = filterNotNull().filter { userCanSee(it.audienceTags, user) }

    @JvmName("filterStudyForUser")
    fun List<StudyItem>.filterForUser(user: User?) = filterNotNull().filter { userCanSee(it.audienceTags, user) }

    // ── AppContent filtered view ─────────────────────────────

    fun AppContent.forUser(user: User?, adminOverrideTag: String = "") = copy(
        quiz   = quiz.filterNotNull()   .filter { userCanSee(it.audienceTags, user, adminOverrideTag) },
        qbank  = qbank.filterNotNull()  .filter { userCanSee(it.audienceTags, user, adminOverrideTag) },
        study  = study.filterNotNull()  .filter { userCanSee(it.audienceTags, user, adminOverrideTag) }
    )

    /**
     * ⚠️ নতুন: Subject-লেভেল audience ফিল্টার — Subjects রেফারেন্স-শিটের নতুন
     * "tag_id" কলাম (যেমন "TAG01") ব্যবহার করে। এটা প্রশ্ন-লেভেল AudienceTags
     * ফিল্টারের (userCanSee, উপরে) থেকে আলাদা মেকানিজম: content-এ ফ্রি-টেক্সট
     * classLevel/Job লেখা থাকে, কিন্তু Subject-এ শুধু একটা Tags-শিট আইডি
     * (tag_id) থাকে যেটা lookup করে আসল নাম বের করতে হয়।
     *
     * subjectTagId ফাঁকা হলে → সবাই দেখবে (unrestricted subject, ব্যাকওয়ার্ড
     * কম্প্যাটিবল — পুরনো subject-গুলোয় এখনো tag_id সেট করা নেই)।
     * subjectTagId থাকলে → tagsById দিয়ে আসল tag_name (যেমন "Job") বের করে,
     * সেটাকে ইউজারের effective audience group-এর (adminOverrideTag বিবেচনা
     * করে, admin App এ যেমন হয়) সাথে মিলিয়ে দেখে।
     */
    fun subjectVisibleForUser(
        subjectTagId    : String?,
        tagsById        : Map<String, String>,
        user            : User?,
        adminOverrideTag: String = ""
    ): Boolean {
        val tid = subjectTagId?.trim().orEmpty()
        if (tid.isBlank()) return true   // tag_id সেট করা নেই — সব audience-এর জন্য visible

        val tagName = tagsById[tid]?.trim().orEmpty()
        if (tagName.isBlank()) return true   // অজানা/মুছে-ফেলা tag_id — নিরাপদে দেখিয়ে দাও, লুকিয়ে ফেলো না

        val effectiveGroup = audienceGroupOf(user)
            .let { if (user?.isAdmin() == true && adminOverrideTag.isNotBlank()) adminOverrideTag else it }

        return tagName.equals(effectiveGroup, ignoreCase = true)
    }

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
