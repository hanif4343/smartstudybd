package com.hanif.smartstudy.data.model

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────
// Firebase RTDB actual field names (from old HTML app getVal analysis):
//   subject, sub_topic, question, option1/opt1, option2/opt2, ...
//   correct (NOT Answer!), explanation, technique, Question Type, AudienceTags
// CaseInsensitiveAdapterFactory handles casing variants
// ─────────────────────────────────────────────────────────

data class StudyItem(
    @SerializedName("id")           val id          : String? = null,
    @SerializedName("subject")      val subject     : String? = null,
    @SerializedName("sub_topic")    val subTopic    : String? = null,
    @SerializedName("question")     val question    : String? = null,
    @SerializedName("answer")       val answer      : String? = null,
    @SerializedName("AudienceTags") val audienceTags: String? = null
)

data class QuizItem(
    @SerializedName("id")            val id           : String? = null,
    @SerializedName("subject")       val subject      : String? = null,
    @SerializedName("sub_topic")     val subTopic     : String? = null,
    @SerializedName("question")      val question     : String? = null,
    @SerializedName("option1")       val optionA      : String? = null,
    @SerializedName("option2")       val optionB      : String? = null,
    @SerializedName("option3")       val optionC      : String? = null,
    @SerializedName("option4")       val optionD      : String? = null,
    @SerializedName("correct")       val answer       : String? = null,
    @SerializedName("explanation")   val explanation  : String? = null,
    @SerializedName("Question Type") val questionType : String? = null,
    @SerializedName("technique")     val technique    : String? = null,
    @SerializedName("AudienceTags")  val audienceTags : String? = null,
    @SerializedName("Image")         val imageUrl     : String? = null
)

data class QBankItem(
    @SerializedName("id")            val id           : String? = null,
    @SerializedName("subject")       val subject      : String? = null,
    @SerializedName("sub_topic")     val subTopic     : String? = null,
    @SerializedName("question")      val question     : String? = null,
    @SerializedName("option1")       val optionA      : String? = null,
    @SerializedName("option2")       val optionB      : String? = null,
    @SerializedName("option3")       val optionC      : String? = null,
    @SerializedName("option4")       val optionD      : String? = null,
    @SerializedName("correct")       val answer       : String? = null,
    @SerializedName("explanation")   val explanation  : String? = null,
    @SerializedName("Question Type") val questionType : String? = null,
    @SerializedName("AudienceTags")  val audienceTags : String? = null,
    @SerializedName("Year")          val year         : String? = null,
    @SerializedName("Exam_Name")     val examName     : String? = null,
    @SerializedName("Image")         val imageUrl     : String? = null
)

// ── Gson case-insensitive + multi-alias adapter ──
object CaseInsensitiveGson {
    val instance: com.google.gson.Gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapterFactory(CaseInsensitiveAdapterFactory())
            .create()
    }
}

class CaseInsensitiveAdapterFactory : com.google.gson.TypeAdapterFactory {
    override fun <T> create(gson: com.google.gson.Gson, type: com.google.gson.reflect.TypeToken<T>): com.google.gson.TypeAdapter<T>? {
        val delegate = gson.getDelegateAdapter(this, type)
        val elementAdapter = gson.getAdapter(com.google.gson.JsonElement::class.java)
        return object : com.google.gson.TypeAdapter<T>() {
            override fun write(out: com.google.gson.stream.JsonWriter, value: T) = delegate.write(out, value)
            override fun read(`in`: com.google.gson.stream.JsonReader): T {
                val jsonElement = elementAdapter.read(`in`)
                return if (jsonElement.isJsonObject) {
                    val normalized = com.google.gson.JsonObject()
                    jsonElement.asJsonObject.entrySet().forEach { (k, v) ->
                        normalized.add(normalizeKey(k), v)
                    }
                    delegate.fromJsonTree(normalized)
                } else {
                    delegate.fromJsonTree(jsonElement)
                }
            }
        }
    }

    // Firebase field names → @SerializedName canonical keys
    private fun normalizeKey(key: String): String {
        val k = key.lowercase().trim()
        return when {
            // subject
            k == "subject"                          -> "subject"
            // sub_topic — many variants
            k == "sub_topic" || k == "subtopic"
                || k == "sub topic"                 -> "sub_topic"
            // question
            k == "question"                         -> "question"
            // options — option1/opt1 variants
            k == "option1" || k == "opt1"
                || k == "option_a" || k == "optiona" -> "option1"
            k == "option2" || k == "opt2"
                || k == "option_b" || k == "optionb" -> "option2"
            k == "option3" || k == "opt3"
                || k == "option_c" || k == "optionc" -> "option3"
            k == "option4" || k == "opt4"
                || k == "option_d" || k == "optiond" -> "option4"
            // correct answer — old app uses "correct", also "answer"
            k == "correct"                          -> "correct"
            k == "answer"                           -> "answer"
            // explanation
            k == "explanation"                      -> "explanation"
            // question type
            k == "question type" || k == "question_type"
                || k == "questiontype"              -> "Question Type"
            // audience tags
            k == "audiencetags" || k == "audience_tags" -> "AudienceTags"
            // technique
            k == "technique"                        -> "technique"
            // qbank specific
            k == "year"                             -> "Year"
            k == "exam_name" || k == "examname"
                || k == "exam name"                 -> "Exam_Name"
            // image
            k == "image" || k == "imageurl"
                || k == "image_url"                 -> "Image"
            // id
            k == "id"                               -> "id"
            else                                    -> key
        }
    }
}

data class AppContent(
    val study    : List<StudyItem>  = emptyList(),
    val quiz     : List<QuizItem>   = emptyList(),
    val qbank    : List<QBankItem>  = emptyList(),
    val fetchedAt: Long             = 0L
) {
    fun isEmpty()  = study.isEmpty() && quiz.isEmpty() && qbank.isEmpty()
    fun isStale(ttlMillis: Long = 6 * 60 * 60 * 1000L) =
        fetchedAt == 0L || (System.currentTimeMillis() - fetchedAt) > ttlMillis
}
