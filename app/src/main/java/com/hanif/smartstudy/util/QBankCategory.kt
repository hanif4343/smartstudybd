package com.hanif.smartstudy.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * QBank পরীক্ষা-ক্যাটাগরি (বিসিএস / প্রাথমিক / নিবন্ধন / ব্যাংক / ১৬-২০ গ্রেড) — UI (চিপ-ফিল্টার)
 * ও ViewModel (সাল-মোডে প্রশ্ন ফিল্টার) দুই জায়গায় একই নিয়ম ব্যবহারের জন্য এক জায়গায়।
 * "১৬-২০ গ্রেড" catch-all: বাকি চারটার কোনোটাতে না মিললেই এখানে।
 */
object QBankCategory {
    const val GRADE = AudienceProfiles.CAT_GRADE

    // ── 🩺 নার্সিং ক্যাটাগরি (সংজ্ঞা AudienceProfiles-এ; এখানে সুবিধার জন্য নাম) ──
    const val MIDWIFE = AudienceProfiles.CAT_MIDWIFE
    const val BSC_NURSING = AudienceProfiles.CAT_BSC_NURSING
    const val DIPLOMA_NURSING = AudienceProfiles.CAT_DIPLOMA_NURSING
    val NURSING_ALL = AudienceProfiles.NURSING_QBANK

    /** বর্তমান অডিয়েন্সের (AudienceProfiles.current) চিপ-তালিকা — অডিয়েন্স বদলালে আপনা-আপনি বদলায় */
    val ALL: List<String> get() = AudienceProfiles.current.qbankCategories

    /** বর্তমানে সিলেক্টেড চিপ — UI সেট করে, ViewModel (সাল-মোড) পড়ে; back করলেও মনে থাকে */
    var selected by mutableStateOf(AudienceProfiles.GENERAL_QBANK.first())

    /** ইউজার (বা admin-এর audience override) অনুযায়ী অডিয়েন্স সেট করো; সিলেক্টেড চিপ অবৈধ হলে প্রথমটায় ফেরাও */
    fun applyAudience(classLevel: String?) {
        AudienceProfiles.apply(classLevel)
        if (selected !in ALL) selected = ALL.first()
    }

    private val NAMED = AudienceProfiles.NAMED_GROUPS.first()

    /** কার্ডের নামে ক্যাটাগরির কীওয়ার্ড আছে কিনা (কীওয়ার্ড-তালিকা AudienceProfiles.CATEGORY_KEYWORDS-এ) */
    fun nameMatches(name: String, category: String): Boolean {
        val keys = AudienceProfiles.CATEGORY_KEYWORDS[category] ?: return false
        val n = name.trim().lowercase()
        return keys.any { n.contains(it) }
    }

    /**
     * names-এর (কার্ডের নিজের নাম + নেস্টেড নাম) কোনোটা ক্যাটাগরিতে পড়ে কিনা।
     *
     * ⚠️ নিয়ম: names[0] = কার্ডের নিজের নাম। কার্ডের নিজের নামই যদি কোনো নির্দিষ্ট ক্যাটাগরিতে
     * (বিসিএস/প্রাথমিক/নিবন্ধন/ব্যাংক) মিলে যায়, তাহলে কার্ডটা শুধু সেই ক্যাটাগরিতেই যাবে।
     * আগে নেস্টেড নামে "ব্যাংক" থাকলেই বিসিএসের কার্ডও "ব্যাংক" চিপে চলে আসত।
     * নিজের নামে কিছু না মিললে (যেমন "সিনিয়র অফিসার") নেস্টেড নাম (প্রতিষ্ঠান) দেখে আগের মতোই মেলানো হয়।
     */
    fun matches(names: List<String>, category: String): Boolean {
        fun hit(cat: String) = names.any { nameMatches(it, cat) }
        // নামযুক্ত ক্যাটাগরি (বিসিএস/প্রাথমিক/নিবন্ধন/ব্যাংক বা মিডওয়াইফ/বিএসসি/ডিপ্লোমা):
        // কার্ডের নিজের নাম আগে; তাতে না মিললে নেস্টেড নাম (প্রতিষ্ঠান) দেখে মেলানো।
        val group = AudienceProfiles.NAMED_GROUPS.firstOrNull { category in it }
        if (group != null) {
            val ownName0 = names.firstOrNull().orEmpty()
            val ownCat = group.firstOrNull { nameMatches(ownName0, it) }
            return if (ownCat != null) ownCat == category else hit(category)
        }
        val ownName = names.firstOrNull().orEmpty()
        val ownCategory = NAMED.firstOrNull { nameMatches(ownName, it) }
        return when (category) {
            // ── FIX: আগে GRADE = "কোনো নামযুক্ত ক্যাটাগরিতেই মেলেনি"। ফলে একটা বড় পদবীর (যেমন "অফিস সহকারী কাম
            // কম্পিউটার অপারেটর", ১১ প্রতিষ্ঠান) শুধু একটা প্রতিষ্ঠানের নামে "প্রাথমিক ও গণশিক্ষা মন্ত্রণালয়"
            // থাকলেই পুরো পদবী "প্রাথমিক" চিপে চলে যেত, "১৬-২০ গ্রেড"-এ আর আসত না (৬২৩টা লিখিত প্রশ্নসহ গায়েব)।
            // এখন: কার্ডের নিজের নামে কোনো নামযুক্ত ক্যাটাগরি না মিললে এবং অন্তত একটা নেস্টেড নাম (প্রতিষ্ঠান)
            // কোনো নামযুক্ত ক্যাটাগরিতেই না পড়লে (বা নেস্টেড নামই না থাকলে) কার্ডটা GRADE-এও আসে।
            // বিসিএস/প্রাথমিক/নিবন্ধন/ব্যাংক চিপের নিয়ম আগের মতোই — তাই আগের কোনো কার্ড কোনো চিপ থেকে সরে না,
            // শুধু GRADE-এ আরও কিছু যোগ হতে পারে (GRADE-এর আগের সব কার্ড এখনও থাকে)। ──
            GRADE    -> ownCategory == null && names.drop(1).let { nested ->
                nested.isEmpty() || nested.any { n -> NAMED.none { nameMatches(n, it) } }
            }
            else     -> true
        }
    }
}
