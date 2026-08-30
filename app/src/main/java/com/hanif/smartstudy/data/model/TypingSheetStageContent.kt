package com.hanif.smartstudy.data.model

/**
 * Google Sheet-এর "CurriculumStages" ট্যাব থেকে (Firebase হয়ে) আসা admin-curated
 * প্র্যাকটিস-কনটেন্ট — Smart Typing-এর কারিকুলাম-স্টেজে (দেখো BijoyCurriculum.kt)
 * এতদিন যে এলোমেলো সিলেবল/শব্দ স্বয়ংক্রিয়ভাবে জেনারেট হতো (CurriculumProvider.
 * buildDrillPassage), সেটার বদলে/পাশাপাশি এডমিন এখন বাস্তব-অর্থবহ শব্দ/বাক্য
 * সরাসরি বসাতে পারবে — যেটা সব ইউজারের জন্য প্রযোজ্য হবে।
 *
 * কলাম: id, track, stage, content, updatedAt।
 *   - track  : "bn" | "en"
 *   - stage  : সংখ্যা (স্ট্রিং হিসেবে আসে, কারণ Sheet সবকিছুই টেক্সট/নাম্বার হিসেবে
 *              রিটার্ন করতে পারে — তাই stageInt() হেল্পার দিয়ে নিরাপদে parse করা হয়)
 *   - content: প্র্যাকটিস-টেক্সট (শব্দ স্পেস দিয়ে আলাদা করা — ঠিক আগের সিন্থেটিক
 *              জেনারেশনের ফরম্যাটের মতোই, যাতে বাকি টাইপিং-ইঞ্জিনে কোনো পরিবর্তন
 *              লাগে না)
 *
 * একই track+stage-এ একাধিক row থাকলে (variety-র জন্য এডমিন একাধিক ভ্যারিয়েন্ট
 * দিতে পারে), CurriculumStageContentProvider সবগুলো জমা করে, ব্যবহারের সময় একটা
 * এলোমেলোভাবে বেছে নেওয়া হয়।
 */
data class TypingSheetStageContent(
    val id        : String = "",
    val track     : String = "",
    val stage     : String = "",
    val content   : String = "",
    val updatedAt : Long   = 0L
) {
    fun stageInt(): Int? = stage.trim().toDoubleOrNull()?.toInt()
}
