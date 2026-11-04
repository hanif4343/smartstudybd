package com.hanif.smartstudy.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hanif.smartstudy.data.model.User

/**
 * 🎯 AudienceProfile — "কোন ধরনের ইউজার কী দেখবে" এর একমাত্র কেন্দ্রীয় সংজ্ঞা।
 *
 * আগে এই সিদ্ধান্ত অ্যাপের অনেক জায়গায় আলাদা আলাদা ছড়ানো ছিল (QBank-এর নার্সিং সুইচ,
 * অ্যাডমিনের tag-তালিকা, গ্রুপ-লেবেল, "অ্যাডমিন হলে override নইলে নিজের group" এর ১৫+ কপি…)।
 * এখন সব এখান থেকে আসে। নতুন অডিয়েন্স যোগ করতে হলে শুধু [AudienceProfiles.all]-এ একটা সারি।
 *
 * ধাপ ১: কোনো দৃশ্যমান পরিবর্তন নেই — আচরণ আগের মতোই, শুধু কাঠামো এক জায়গায়।
 * পরের ধাপে এখানেই থিম/হোম-সেকশন/ফিচার-ফ্ল্যাগ যোগ হবে।
 */
data class AudienceProfile(
    /** স্থির আইডি (লগ/কনফিগের জন্য), যেমন "JOB", "NURSING" */
    val id: String,
    /** কনটেন্টে ও ইউজারের classLevel-এ যে নামে থাকে, যেমন "Job", "Nursing Admission", "Masters 1" */
    val tag: String,
    /** UI-তে গ্রুপের নাম, যেমন "চাকরি (Job)", "নার্সিং ভর্তি" */
    val groupLabel: String,
    /** অ্যাডমিনের "Switch Audience" চিপের লেখা */
    val adminLabel: String,
    /** QBank-এর ক্যাটাগরি-চিপ (ক্রম অনুযায়ী) */
    val qbankCategories: List<String>,
    /** true = চাকরি-প্রার্থী ধরনের (ক্লাস-লেভেল ছাড়া) */
    val isJob: Boolean = false,
    /** হোমের দ্রুত বাটন (সর্বোচ্চ ৪টা, ক্রম অনুযায়ী): wrong | model | saved | routine | typing | viva | focus */
    val quickActions: List<String> = DEFAULT_QUICK_ACTIONS,
    /** ইউজার নিজে থিম না বাছলে এই থিম চলবে: "indigo" | "teal" | "rose" | "amber" | "nordic" */
    val defaultTheme: String = "indigo",
    /** আজ এখনো পড়া শুরু না হলে হোম-হেডারে যে বার্তা দেখায় */
    val welcome: String = DEFAULT_WELCOME
)

val DEFAULT_QUICK_ACTIONS = listOf("wrong", "model", "saved", "routine")
const val DEFAULT_WELCOME = "আজ এখনো পড়া শুরু হয়নি — একটা কুইজ দিয়ে শুরু করো"

/** সাইনআপের একটা গ্রুপ (যেমন "মাধ্যমিক" → Class 1…10) */
data class SignupGroup(val label: String, val emoji: String, val tags: List<String>)

object AudienceProfiles {

    const val TAG_JOB     = "Job"
    const val TAG_NURSING = "Nursing Admission"

    // ── QBank ক্যাটাগরির নাম (QBankCategory-র const-ও এখান থেকেই নেয়) ──
    const val CAT_BCS      = "বিসিএস"
    const val CAT_PRIMARY  = "প্রাথমিক"
    const val CAT_NIBONDHON = "নিবন্ধন"
    const val CAT_BANK     = "ব্যাংক"
    const val CAT_GRADE    = "১৬-২০ গ্রেড"
    const val CAT_MIDWIFE  = "Midwife"
    const val CAT_BSC_NURSING     = "BSc Nursing"
    const val CAT_DIPLOMA_NURSING = "Diploma In Nursing"

    val GENERAL_QBANK = listOf(CAT_BCS, CAT_PRIMARY, CAT_NIBONDHON, CAT_BANK, CAT_GRADE)
    val NURSING_QBANK = listOf(CAT_MIDWIFE, CAT_BSC_NURSING, CAT_DIPLOMA_NURSING)

    /**
     * ক্যাটাগরি → কার্ডের নামে যে শব্দ থাকলে সেই ক্যাটাগরিতে পড়বে (ছোট হাতের ইংরেজি + বাংলা)।
     * আগে এগুলো QBankCategory-র ভেতরে কোডে বসানো ছিল; এখন এক জায়গায় ডাটা — পরে AudienceConfig শিট থেকেও
     * আনা যাবে (অ্যাপ আপডেট ছাড়া নতুন ক্যাটাগরি/কীওয়ার্ড)। "১৬-২০ গ্রেড" এখানে নেই — সেটা catch-all।
     */
    val CATEGORY_KEYWORDS: Map<String, List<String>> = mapOf(
        CAT_BCS      to listOf("bcs", "বিসিএস"),
        CAT_PRIMARY  to listOf("primary", "প্রাথমিক"),
        // শিক্ষক নিবন্ধন (NTRCA): কলেজ পর্যায় / স্কুল পর্যায় নামেও আসে
        CAT_NIBONDHON to listOf("ntrca", "registration", "নিবন্ধন", "কলেজ পর্যায়", "স্কুল পর্যায়"),
        CAT_BANK     to listOf("bank", "ব্যাংক"),
        CAT_MIDWIFE  to listOf("midwife", "midwifery", "মিডওয়াইফ"),
        CAT_BSC_NURSING to listOf("bsc", "b.sc", "b sc", "বিএসসি"),
        CAT_DIPLOMA_NURSING to listOf("diploma", "ডিপ্লোমা")
    )

    /** নামযুক্ত ক্যাটাগরির দল: একটা কার্ড নিজের নামে এক দলের একটা ক্যাটাগরিতেই পড়ে */
    val NAMED_GROUPS: List<List<String>> = listOf(
        listOf(CAT_BCS, CAT_PRIMARY, CAT_NIBONDHON, CAT_BANK),
        NURSING_QBANK
    )

    // ── প্রোফাইলগুলো ──
    val JOB = AudienceProfile(
        id = "JOB", tag = TAG_JOB, groupLabel = "চাকরি (Job)",
        adminLabel = "👔 Job Seeker (Default)", qbankCategories = GENERAL_QBANK, isJob = true
    )
    val NURSING = AudienceProfile(
        id = "NURSING", tag = TAG_NURSING, groupLabel = "নার্সিং ভর্তি",
        adminLabel = "🩺 Nursing Admission", qbankCategories = NURSING_QBANK,
        // ভর্তি পরীক্ষার প্রস্তুতিতে মডেল টেস্টই সবার আগে
        quickActions = listOf("model", "wrong", "saved", "routine"),
        defaultTheme = "teal",
        welcome = "নার্সিং ভর্তির প্রস্তুতি শুরু করো — আজ একটা মডেল টেস্ট দাও"
    )

    private fun student(tag: String, adminLabel: String): AudienceProfile {
        val n = tag.substringAfterLast(' ').toIntOrNull()
        val isSmall = tag.startsWith("Class") && n != null && n <= 5
        val isUni = tag.startsWith("Honours") || tag.startsWith("Masters")
        return AudienceProfile(
            id = tag.uppercase().replace(' ', '_'), tag = tag, groupLabel = groupLabelFor(tag),
            adminLabel = adminLabel, qbankCategories = GENERAL_QBANK,
            // ছোটদের উষ্ণ কমলা-হলুদ, বিশ্ববিদ্যালয়ে শান্ত প্যাস্টেল, মাঝেরটা আগের ইন্ডিগো
            defaultTheme = when { isSmall -> "amber"; isUni -> "nordic"; else -> "indigo" },
            welcome = when {
                isSmall -> "আজকের পড়াটা শুরু করি? একটা ছোট কুইজ দিয়ে শুরু করো"
                isUni   -> "আজকের পড়া শুরু হয়নি — একটা টপিক ধরে শুরু করো"
                else    -> "আজকের পড়া শুরু হয়নি — একটা অধ্যায় ধরে কুইজ দাও"
            }
        )
    }

    /** অ্যাডমিন-সুইচে যে ক্রমে দেখায় ঠিক সেই ক্রমে */
    val all: List<AudienceProfile> = listOf(
        JOB, NURSING,
        student("Masters Final", "🎓 Masters Final"),
        student("Masters 2", "🎓 Masters 2nd"),
        student("Masters 1", "🎓 Masters 1st"),
        student("Honours 4", "🎓 Honours 4th"),
        student("Honours 3", "🎓 Honours 3rd"),
        student("Honours 2", "🎓 Honours 2nd"),
        student("Honours 1", "🎓 Honours 1st")
    ) + (12 downTo 1).map { student("Class $it", "📚 Class $it") }

    /** সাইনআপের গ্রুপ ও তাদের ক্লাস-লেভেল — নতুন গ্রুপ যোগ হলে শুধু এখানে */
    val signupGroups: List<SignupGroup> = listOf(
        SignupGroup("মাধ্যমিক", "📚", (1..10).map { "Class $it" }),
        SignupGroup("উচ্চ মাধ্যমিক", "🎓", listOf("Class 11", "Class 12")),
        SignupGroup("অনার্স", "🏛️", (1..4).map { "Honours $it" }),
        SignupGroup("মাস্টার্স", "🔬", listOf("Masters 1", "Masters 2", "Masters Final")),
        SignupGroup("ভর্তি প্রস্তুতি", "🩺", listOf(TAG_NURSING))
    )

    private val ordinals = mapOf(1 to "১ম", 2 to "২য়", 3 to "৩য়", 4 to "৪র্থ", 5 to "৫ম",
        6 to "৬ষ্ঠ", 7 to "৭ম", 8 to "৮ম", 9 to "৯ম", 10 to "১০ম")

    /** প্রোফাইল-পাতার ক্লাস-লেভেল ড্রপডাউনে যে নামে দেখায় */
    fun displayLabel(tag: String): String {
        val n = tag.substringAfterLast(' ').toIntOrNull()
        return when {
            tag == "Class 10"             -> "১০ম শ্রেণি (SSC)"
            tag == "Class 11"             -> "একাদশ শ্রেণি"
            tag == "Class 12"             -> "দ্বাদশ শ্রেণি (HSC)"
            tag.startsWith("Class")       -> "${ordinals[n] ?: tag} শ্রেণি"
            tag.startsWith("Honours")     -> "অনার্স ${ordinals[n] ?: tag} বর্ষ"
            tag == "Masters Final"        -> "মাস্টার্স ফাইনাল"
            tag.startsWith("Masters")     -> "মাস্টার্স ${ordinals[n] ?: tag} বর্ষ"
            tag.equals(TAG_NURSING, true) -> "🩺 নার্সিং ভর্তি (Nursing Admission)"
            else                          -> tag
        }
    }

    /** অ্যাডমিনের Switch Audience-এর (tag, লেবেল) তালিকা */
    val adminSwitchOptions: List<Pair<String, String>> = all.map { it.tag to it.adminLabel }

    /** tag মিললে রেজিস্টার্ড প্রোফাইল, নইলে null */
    fun find(tag: String?): AudienceProfile? {
        val t = tag?.trim().orEmpty()
        if (t.isBlank()) return null
        return all.firstOrNull { it.tag.equals(t, ignoreCase = true) }
    }

    /** সবসময় একটা প্রোফাইল দেয়: ফাঁকা = Job; অজানা tag (যেমন "Masters Final") = সাধারণ শিক্ষার্থী-প্রোফাইল */
    fun resolve(tag: String?): AudienceProfile {
        val t = tag?.trim().orEmpty()
        if (t.isBlank()) return JOB
        return find(t) ?: AudienceProfile(
            id = t.uppercase().replace(' ', '_'), tag = t, groupLabel = groupLabelFor(t),
            adminLabel = t, qbankCategories = GENERAL_QBANK
        )
    }

    // ── ইউজার → audience group ──

    /** ইউজারের নিজের group: classLevel থাকলে সেটা, নইলে "Job" */
    fun groupOf(user: User?): String {
        val cl = user?.classLevel?.trim() ?: ""
        return if (cl.isNotBlank()) cl else TAG_JOB
    }

    /** অ্যাডমিন হলে তার বেছে নেওয়া tag (ফাঁকা না হলে), নইলে ইউজারের নিজের group */
    fun effectiveGroup(user: User?, adminTag: String): String {
        val own = groupOf(user)
        return if (user?.isAdmin() == true && adminTag.isNotBlank()) adminTag else own
    }

    /** group থেকে UI-র নাম ("Class 5" → "5 শ্রেণি", "Masters 1" → "মাস্টার্স 1 বর্ষ") */
    fun groupLabelFor(group: String): String = when {
        group.equals(TAG_JOB, ignoreCase = true)     -> "চাকরি (Job)"
        group.startsWith("Class", ignoreCase = true) -> "${group.removePrefix("Class").trim()} শ্রেণি"
        group.equals(TAG_NURSING, ignoreCase = true) -> "নার্সিং ভর্তি"
        group.startsWith("Masters", ignoreCase = true) -> "মাস্টার্স ${group.removePrefix("Masters").trim()} বর্ষ"
        group.startsWith("Honours", ignoreCase = true) -> "অনার্স ${group.removePrefix("Honours").trim()} বর্ষ"
        else -> group
    }

    // ── বর্তমান প্রোফাইল (Compose state — বদলালে UI আপনা-আপনি আপডেট হয়) ──

    /** এই মুহূর্তে অ্যাপ যে অডিয়েন্সের চোখে চলছে (অ্যাডমিনের ক্ষেত্রে তার Switch Audience পছন্দ) */
    var current by mutableStateOf(JOB)
        private set

    /** ইউজারের classLevel (অ্যাডমিনের জন্য: তার বেছে নেওয়া tag) দিয়ে বর্তমান প্রোফাইল ঠিক করো */
    fun apply(tag: String?) {
        current = resolve(tag)
    }
}
