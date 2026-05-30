package com.hanif.smartstudy.data.model

import com.google.gson.annotations.SerializedName

// ── Firebase RTDB থেকে আসা Study sheet এর একটি row ──
data class StudyItem(
    @SerializedName("id")          val id         : String? = null,
    @SerializedName("Subject")     val subject    : String? = null,
    @SerializedName("Sub_Topic")   val subTopic   : String? = null,
    @SerializedName("Question")    val question   : String? = null,
    @SerializedName("Answer")      val answer     : String? = null,
    @SerializedName("AudienceTags")val audienceTags: String? = null
)

// ── Quiz sheet ──
data class QuizItem(
    @SerializedName("id")           val id           : String? = null,
    @SerializedName("Subject")      val subject      : String? = null,
    @SerializedName("Sub_Topic")    val subTopic     : String? = null,
    @SerializedName("Question")     val question     : String? = null,
    @SerializedName("Option_A")     val optionA      : String? = null,
    @SerializedName("Option_B")     val optionB      : String? = null,
    @SerializedName("Option_C")     val optionC      : String? = null,
    @SerializedName("Option_D")     val optionD      : String? = null,
    @SerializedName("Answer")       val answer       : String? = null,
    @SerializedName("Explanation")  val explanation  : String? = null,
    @SerializedName("Question Type")val questionType : String? = null,
    @SerializedName("AudienceTags") val audienceTags : String? = null
)

// ── QBank sheet ──
data class QBankItem(
    @SerializedName("id")           val id           : String? = null,
    @SerializedName("Subject")      val subject      : String? = null,
    @SerializedName("Sub_Topic")    val subTopic     : String? = null,
    @SerializedName("Question")     val question     : String? = null,
    @SerializedName("Option_A")     val optionA      : String? = null,
    @SerializedName("Option_B")     val optionB      : String? = null,
    @SerializedName("Option_C")     val optionC      : String? = null,
    @SerializedName("Option_D")     val optionD      : String? = null,
    @SerializedName("Answer")       val answer       : String? = null,
    @SerializedName("Explanation")  val explanation  : String? = null,
    @SerializedName("Question Type")val questionType : String? = null,
    @SerializedName("AudienceTags") val audienceTags : String? = null,
    @SerializedName("Year")         val year         : String? = null,
    @SerializedName("Exam_Name")    val examName     : String? = null
)

// ── সব sheet একসাথে ──
data class AppContent(
    val study  : List<StudyItem>  = emptyList(),
    val quiz   : List<QuizItem>   = emptyList(),
    val qbank  : List<QBankItem>  = emptyList(),
    val fetchedAt: Long = 0L
) {
    fun isEmpty() = study.isEmpty() && quiz.isEmpty() && qbank.isEmpty()
    fun isStale(ttlMillis: Long = 6 * 60 * 60 * 1000L) =  // 6 ঘণ্টা পরে stale
        fetchedAt == 0L || (System.currentTimeMillis() - fetchedAt) > ttlMillis
}
