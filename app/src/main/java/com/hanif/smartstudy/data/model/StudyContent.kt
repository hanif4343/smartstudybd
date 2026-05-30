package com.hanif.smartstudy.data.model

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────
// Firebase RTDB field names — old app getVal() case-insensitive এর মতো
// দুটো করে @SerializedName দেওয়া যায় না, তাই Gson TypeAdapterFactory ছাড়া
// সবচেয়ে নিরাপদ উপায়: যেটা Firebase-এ actually আছে সেটা রাখো।
// পুরনো sheet-এ: Subject, Sub_Topic, Question, Option_A... Answer, AudienceTags
// ─────────────────────────────────────────────────────────

data class StudyItem(
    @SerializedName("id")           val id          : String? = null,
    @SerializedName("Subject")      val subject     : String? = null,
    @SerializedName("Sub_Topic")    val subTopic    : String? = null,
    @SerializedName("Question")     val question    : String? = null,
    @SerializedName("Answer")       val answer      : String? = null,
    @SerializedName("AudienceTags") val audienceTags: String? = null
)

data class QuizItem(
    @SerializedName("id")            val id           : String? = null,
    @SerializedName("Subject")       val subject      : String? = null,
    @SerializedName("Sub_Topic")     val subTopic     : String? = null,
    @SerializedName("Question")      val question     : String? = null,
    @SerializedName("Option_A")      val optionA      : String? = null,
    @SerializedName("Option_B")      val optionB      : String? = null,
    @SerializedName("Option_C")      val optionC      : String? = null,
    @SerializedName("Option_D")      val optionD      : String? = null,
    @SerializedName("Answer")        val answer       : String? = null,
    @SerializedName("Explanation")   val explanation  : String? = null,
    @SerializedName("Question Type") val questionType : String? = null,
    @SerializedName("Technique")     val technique    : String? = null,
    @SerializedName("AudienceTags")  val audienceTags : String? = null,
    @SerializedName("Image")         val imageUrl     : String? = null
)

data class QBankItem(
    @SerializedName("id")            val id           : String? = null,
    @SerializedName("Subject")       val subject      : String? = null,
    @SerializedName("Sub_Topic")     val subTopic     : String? = null,
    @SerializedName("Question")      val question     : String? = null,
    @SerializedName("Option_A")      val optionA      : String? = null,
    @SerializedName("Option_B")      val optionB      : String? = null,
    @SerializedName("Option_C")      val optionC      : String? = null,
    @SerializedName("Option_D")      val optionD      : String? = null,
    @SerializedName("Answer")        val answer       : String? = null,
    @SerializedName("Explanation")   val explanation  : String? = null,
    @SerializedName("Question Type") val questionType : String? = null,
    @SerializedName("AudienceTags")  val audienceTags : String? = null,
    @SerializedName("Year")          val year         : String? = null,
    @SerializedName("Exam_Name")     val examName     : String? = null,
    @SerializedName("Image")         val imageUrl     : String? = null
)

// ── Gson case-insensitive deserializer ──
// Gson normally is case-sensitive, but our Firebase may have inconsistent casing.
// We register a custom TypeAdapterFactory that handles it.
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
                    // Normalize keys to match @SerializedName exactly
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

    // Map common variants to canonical key names
    private fun normalizeKey(key: String): String = when (key.lowercase().trim().replace(" ", "_")) {
        "subject"       -> "Subject"
        "sub_topic"     -> "Sub_Topic"
        "subtopic"      -> "Sub_Topic"
        "question"      -> "Question"
        "option_a"      -> "Option_A"
        "option_b"      -> "Option_B"
        "option_c"      -> "Option_C"
        "option_d"      -> "Option_D"
        "answer"        -> "Answer"
        "explanation"   -> "Explanation"
        "question_type" -> "Question Type"
        "questiontype"  -> "Question Type"
        "audiencetags"  -> "AudienceTags"
        "audience_tags" -> "AudienceTags"
        "technique"     -> "Technique"
        "year"          -> "Year"
        "exam_name"     -> "Exam_Name"
        "image"         -> "Image"
        else            -> key  // keep original
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
