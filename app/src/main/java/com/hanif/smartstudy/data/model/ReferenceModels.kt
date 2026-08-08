package com.hanif.smartstudy.data.model

import com.google.gson.annotations.SerializedName

/* ─────────────────────────────────────────────────────────────────────────
   GAS action=getReferenceData রেসপন্সের data.{subjects,topics,subtopics,tags,
   posts,institutions} — ফিল্ড-নাম Admin App-এর সাথে হুবহু মিলিয়ে রাখা হলো
   (দেখো smart-study-admin-app: ReferenceManagerTab.jsx NAME_KEY/ID_KEY,
   ExamAppearancesTab.jsx)। এই ফাইলে plain Gson @SerializedName যথেষ্ট —
   Firebase Quiz/QBank/Study-এর মতো legacy কলাম-নাম ভ্যারিয়েন্ট এখানে নেই,
   কারণ এই টেবিলগুলো Phase 5-এই নতুন করে বানানো, পুরনো নাম-বিশৃঙ্খলা নেই।
   ───────────────────────────────────────────────────────────────────────── */

data class SubjectRef(
    @SerializedName("subject_id")   val subjectId : String? = null,
    @SerializedName("subject_name") val name      : String? = null,
    @SerializedName("sheet")        val sheet     : String? = null,
    // ⚠️ নতুন: Subjects শিটে যোগ করা "tag_id" কলাম (যেমন "TAG01") — Tags
    // রেফারেন্স-শিটের tag_id-কে পয়েন্ট করে (TAG01 → tag_name="Job")। খালি থাকলে
    // এই subject-এ audience-ফিল্টার প্রযোজ্য না (সবাই দেখবে) — দেখো
    // AudienceFilter.subjectVisibleForUser()।
    @SerializedName("tag_id")       val tagId     : String? = null
)

data class TopicRef(
    @SerializedName("topic_id")   val topicId   : String? = null,
    @SerializedName("subject_id") val subjectId : String? = null,
    @SerializedName("topic_name") val name      : String? = null,
    // ⚠️ FIX: আগে এই দুটো Int? ছিল — কিন্তু যেই topic-এ এখনো কোনো প্রশ্ন ট্যাগ হয়নি
    // (rebuildIndex-এর পরে নতুন যোগ হওয়া topic), GAS সেই সেলের জন্য খালি স্ট্রিং "" পাঠায়
    // (null না)। Gson "" কে Int-এ পার্স করতে গেলে crash করত (NumberFormatException) —
    // আর যেহেতু পুরো getReferenceData রেসপন্স একটাই fromJson() কলে পার্স হয়, এই একটা
    // topic-এর crash-ই পুরো Subjects+Topics sync ব্যর্থ করে দিত (দেখো GasContentService.
    // fetchReferenceData-এর catch ব্লক — silently null রিটার্ন করত)। এখন String? রাখা
    // হলো (Gson সংখ্যা/স্ট্রিং দুটোই String-এ নিরাপদে পড়তে পারে), আসল Int কনভার্শন
    // EntityExtensions.kt-এর toEntity()-তে toIntOrNull() দিয়ে ডিফেন্সিভলি হয় —
    // এই একই প্যাটার্ন এই ফাইলেরই অন্য জায়গায় (QuestionEntity.subIndex) আগে থেকেই আছে।
    @SerializedName("row_start")  val rowStart  : String? = null,
    @SerializedName("row_count")  val rowCount  : String? = null
)

data class SubTopicRef(
    @SerializedName("subtopic_id")   val subtopicId : String? = null,
    @SerializedName("topic_id")      val topicId    : String? = null,
    @SerializedName("subtopic_name") val name       : String? = null
)

data class TagRef(
    @SerializedName("tag_id")   val tagId : String? = null,
    @SerializedName("tag_name") val name  : String? = null
)

data class PostRef(
    @SerializedName("post_id")   val postId : String? = null,
    @SerializedName("post_name") val name   : String? = null
)

data class InstitutionRef(
    @SerializedName("institution_id")   val institutionId : String? = null,
    @SerializedName("institution_name") val name           : String? = null
)

data class ExamAppearanceRef(
    @SerializedName("appearance_id")  val appearanceId  : String? = null,
    @SerializedName("question_id")    val questionId    : String? = null,
    @SerializedName("post_id")        val postId        : String? = null,
    @SerializedName("institution_id") val institutionId : String? = null,
    @SerializedName("year")           val year          : String? = null
)

/** GAS action=getReferenceData → {"status":"success","data":{...}} এর "data" অংশ */
data class ReferenceData(
    val subjects     : List<SubjectRef>     = emptyList(),
    val topics       : List<TopicRef>       = emptyList(),
    val subtopics    : List<SubTopicRef>    = emptyList(),
    val tags         : List<TagRef>         = emptyList(),
    val posts        : List<PostRef>        = emptyList(),
    val institutions : List<InstitutionRef> = emptyList()
) {
    fun isEmpty() = subjects.isEmpty() && topics.isEmpty() && subtopics.isEmpty() &&
            tags.isEmpty() && posts.isEmpty() && institutions.isEmpty()
}
