package com.hanif.smartstudy.data.model

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────
// Firebase RTDB actual field names (from old HTML app getVal analysis):
//   subject, sub_topic, question, option1/opt1, option2/opt2, ...
//   correct (NOT Answer!), explanation, technique, Question Type, AudienceTags
// CaseInsensitiveAdapterFactory handles casing variants
// ─────────────────────────────────────────────────────────

data class StudyItem(
    @SerializedName("id")            val id           : String? = null,
    @SerializedName("subject")       val subject      : String? = null,
    @SerializedName("sub_topic")     val subTopic     : String? = null,
    @SerializedName("question")      val question     : String? = null,
    @SerializedName("answer")        val answer       : String? = null,
    // Firebase তে correct field — index.html getVal(i,'correct') এর মতো
    @SerializedName("correct")       val correct      : String? = null,
    @SerializedName("explanation")   val explanation  : String? = null,
    @SerializedName("technique")     val technique    : String? = null,
    @SerializedName("Question Type") val questionType : String? = null,
    @SerializedName("AudienceTags")  val audienceTags : String? = null,
    @SerializedName("VisualURL")     val visualUrl    : String? = null
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
    @SerializedName("Image")         val imageUrl     : String? = null,
    @SerializedName("VisualURL")     val visualUrl    : String? = null
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
    @SerializedName("Image")         val imageUrl     : String? = null,
    @SerializedName("VisualURL")     val visualUrl    : String? = null
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
            // visual url
            k == "visualurl" || k == "visual_url"
                || k == "visualurl"                 -> "VisualURL"
            // id
            k == "id"                               -> "id"
            else                                    -> key
        }
    }
}

data class AppContent(
    val study       : List<StudyItem>   = emptyList(),
    val quiz        : List<QuizItem>    = emptyList(),
    val qbank       : List<QBankItem>   = emptyList(),
    // Admin যে সিরিয়াল নাম্বার সেট করেছে — এখন mode-ভিত্তিক (Quiz/QBank/Study আলাদা)।
    // key: mode name ("QUIZ"/"QBANK"/"STUDY") → (key: subject name → serial)।
    // যে সাবজেক্টের serial নেই, সেটা সবসময় তালিকার শেষে (নাম অনুযায়ী) দেখানো হবে।
    // আগে এটা সব mode মিলিয়ে একটাই global Map<String,Int> ছিল — তাতে Quiz/QBank/Study
    // এর সাবজেক্ট একসাথে মিশে যেত admin editor এ। এখন প্রতিটা mode এর নিজস্ব ক্রম।
    val subjectOrder: Map<String, Map<String, Int>> = emptyMap(),
    // subTopic এর সিরিয়াল — mode + subject দুটো দিয়েই আলাদা আলাদা ক্রম থাকে।
    // key: mode name → (key: subject name → (key: subTopic name → serial))। not-set হলে শেষে যায়।
    val subTopicOrder: Map<String, Map<String, Map<String, Int>>> = emptyMap(),
    val fetchedAt   : Long              = 0L
) {
    fun isEmpty()  = study.isEmpty() && quiz.isEmpty() && qbank.isEmpty()
    // FIX: TTL আগে ৬ ঘণ্টা ছিল — admin কোনো প্রশ্ন এডিট করলে অন্য ইউজারদের ডিভাইসে
    // ৬ ঘণ্টা পর্যন্ত পুরনো (stale) cache-ই দেখানো হতো। ১ ঘণ্টায় নামানো হলো যাতে
    // আপডেট অনেক দ্রুত সবার কাছে পৌঁছায়, কিন্তু Firebase read খরচও বেড়ে না যায়।
    fun isStale(ttlMillis: Long = 60 * 60 * 1000L) =
        fetchedAt == 0L || (System.currentTimeMillis() - fetchedAt) > ttlMillis
}
