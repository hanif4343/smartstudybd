package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.AiChatMessage
import com.hanif.smartstudy.data.remote.AiChatService
import com.hanif.smartstudy.data.remote.FirebaseDataService
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupportState(
    val isAsking   : Boolean = false,
    val question   : String  = "",
    val answer     : String? = null,
    val answerFromAi: Boolean = false,
    val askError   : String? = null,
    val isSending  : Boolean = false,
    val sentOk     : Boolean = false,
    val sendError  : String? = null
)

/** Support Center — Help centre (AI অটো-উত্তর, না পারলে লোকাল FAQ) + অ্যাডমিনকে মেসেজ পাঠানো */
class SupportViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val _state = MutableStateFlow(SupportState())
    val state: StateFlow<SupportState> = _state.asStateFlow()

    /** Help centre: প্রশ্ন → AI উত্তর (Settings-এর AI key দিয়ে), key/নেট না থাকলে লোকাল FAQ-তে খোঁজা */
    fun ask(text: String) {
        val q = text.trim()
        if (q.isBlank() || _state.value.isAsking) return
        _state.update { it.copy(isAsking = true, question = q, answer = null, askError = null) }
        viewModelScope.launch {
            val keys = session.getAiApiKeys()
            val aiReply = if (keys.hasAnyKey()) {
                runCatching {
                    AiChatService.sendMessage(
                        history = listOf(AiChatMessage(role = "user", content = q)),
                        keys = keys,
                        contextPrefix = APP_KNOWLEDGE,
                        maxTokens = 450
                    )
                }.getOrNull()
            } else null

            if (!aiReply.isNullOrBlank()) {
                _state.update { it.copy(isAsking = false, answer = aiReply, answerFromAi = true) }
            } else {
                val local = SupportFaq.bestMatch(q)
                _state.update {
                    it.copy(
                        isAsking = false,
                        answer = local?.second,
                        answerFromAi = false,
                        askError = if (local == null)
                            "এই প্রশ্নের উত্তর এখন দিতে পারছি না — নিচের \"Contact Admin\" থেকে অ্যাডমিনকে জানান।" else null
                    )
                }
            }
        }
    }

    fun clearAnswer() { _state.update { it.copy(answer = null, askError = null, question = "") } }

    /** Feature Suggestion / Sync / Account / Contact Admin — মেসেজ অ্যাডমিনের কাছে (Firebase + নোটিফিকেশন) */
    fun send(category: String, message: String) {
        val msg = message.trim()
        if (msg.isBlank() || _state.value.isSending) return
        _state.update { it.copy(isSending = true, sentOk = false, sendError = null) }
        viewModelScope.launch {
            val user = session.getCurrentUser()
            val ok = FirebaseDataService.submitSupportMessage(
                category  = category,
                message   = msg,
                userName  = user?.name.orEmpty(),
                userPhone = user?.phone.orEmpty()
            )
            _state.update {
                it.copy(
                    isSending = false, sentOk = ok,
                    sendError = if (ok) null else "পাঠানো যায়নি — ইন্টারনেট চেক করে আবার চেষ্টা করুন।"
                )
            }
        }
    }

    fun resetSend() { _state.update { it.copy(sentOk = false, sendError = null) } }

    companion object {
        private const val APP_KNOWLEDGE = """
তুমি এখন "Smart Study" অ্যাপের সাপোর্ট সহকারী। শুধু এই অ্যাপ ব্যবহার সংক্রান্ত প্রশ্নের উত্তর দাও, ২-৫ লাইনে, সহজ বাংলায়।
অ্যাপ সম্পর্কে তথ্য:
- নিচে ৫টা ট্যাব: Home, Quiz, QBank, Study, Menu।
- Quiz: বিষয় → টপিক বেছে MCQ অনুশীলন; উত্তর দিলে ব্যাখ্যা দেখা যায়; ভুল প্রশ্ন Wrong Review-তে জমে।
- QBank: আগের পরীক্ষার প্রশ্ন। উপরের চিপে বিসিএস/প্রাথমিক/নিবন্ধন/ব্যাংক/১৬-২০ গ্রেড বাছা যায়; ১৬-২০ গ্রেডে পদবী/প্রতিষ্ঠান/সাল ফিল্টার আছে।
- Study: পড়ার কনটেন্ট; "পড়া হয়েছে" টিক দেওয়া যায়।
- প্রশ্নে ⭐ বুকমার্ক ও 🚩 রিপোর্ট বাটন আছে; সেভ করা প্রশ্ন Menu-তে পাওয়া যায়।
- Home-এ Routine, Focus Mode, Challenge, Study Buddy, Mock/Model Test আছে।
- Settings (Menu → সেটিংস): ডার্ক মোড, রিমাইন্ডার, সাউন্ড, অফলাইন মোড, AI Key (Groq/Mistral/Cerebras/Gemini) যোগ করা যায় — AI ব্যাখ্যার জন্য key লাগে।
- তালিকা স্ক্রিনে টেনে নামালে (pull-to-refresh) নতুন ডেটা আসে।
- অ্যাডমিনের সাথে যোগাযোগ: Support → Contact Admin।
নিশ্চিত না হলে অনুমান করবে না; বলবে "অ্যাডমিনকে Contact Admin থেকে জানান"। পড়াশোনার বিষয়ের প্রশ্ন (যেমন গণিত/ইতিহাস) এখানে নয় — সেগুলোর জন্য প্রশ্নের ভেতরের AI ব্যাখ্যা বা টেকনিক দেখতে বলো।
"""
    }
}

/** AI key/নেট না থাকলে ব্যবহারের জন্য লোকাল FAQ */
object SupportFaq {
    val items: List<Triple<String, String, List<String>>> = listOf(
        Triple("প্রশ্নের ব্যাখ্যা দেখা যাচ্ছে না কেন?",
            "MCQ-তে একটা অপশন বাছলে ব্যাখ্যা আসে। AI ব্যাখ্যার জন্য Menu → সেটিংস-এ অন্তত একটা AI Key (Groq/Mistral/Cerebras/Gemini) যোগ করা থাকতে হবে।",
            listOf("ব্যাখ্যা", "explanation", "ai", "key")),
        Triple("নতুন প্রশ্ন বা ডেটা আসছে না",
            "ইন্টারনেট চালু আছে কিনা দেখুন, অফলাইন মোড বন্ধ করুন, তারপর বিষয়ের তালিকায় টেনে নামিয়ে (pull-to-refresh) রিফ্রেশ করুন। নতুন যোগ হওয়া কনটেন্ট পৌঁছাতে কিছুটা সময় লাগতে পারে।",
            listOf("sync", "সিঙ্ক", "নতুন", "আসছে না", "ডেটা", "refresh")),
        Triple("ভুল প্রশ্ন কোথায় দেখব?",
            "Quiz-এ ভুল করা প্রশ্নগুলো Home-এর Wrong Review অংশে জমে। সেখান থেকে আবার অনুশীলন করা যায়।",
            listOf("ভুল", "wrong", "review")),
        Triple("প্রশ্ন সেভ বা বুকমার্ক করব কীভাবে?",
            "প্রশ্নের কার্ডের ⭐ আইকনে ট্যাপ করলে বুকমার্ক হয়। সেভ করা প্রশ্ন Menu-তে পাওয়া যায়।",
            listOf("বুকমার্ক", "bookmark", "সেভ", "save")),
        Triple("প্রশ্নে ভুল পেলে কী করব?",
            "প্রশ্নের কার্ডের 🚩 বাটনে ট্যাপ করে সমস্যাটা লিখে রিপোর্ট করুন। অ্যাডমিন দেখে ঠিক করবেন।",
            listOf("ভুল তথ্য", "রিপোর্ট", "report", "ভুল আছে")),
        Triple("QBank-এ বিসিএস/ব্যাংক প্রশ্ন কীভাবে পাব?",
            "QBank ট্যাবের উপরের চিপ থেকে ক্যাটাগরি (বিসিএস, প্রাথমিক, নিবন্ধন, ব্যাংক, ১৬-২০ গ্রেড) বেছে কার্ডে ট্যাপ করুন।",
            listOf("qbank", "বিসিএস", "ব্যাংক", "প্রাথমিক", "নিবন্ধন")),
        Triple("পাসওয়ার্ড/লগইন সমস্যা",
            "ফোন নম্বর ও পাসওয়ার্ড ঠিক আছে কিনা দেখুন; ইন্টারনেট চালু রাখুন। তবু না হলে Contact Admin-এ আপনার ফোন নম্বরসহ জানান।",
            listOf("লগইন", "login", "পাসওয়ার্ড", "password", "অ্যাকাউন্ট", "account"))
    )

    fun bestMatch(q: String): Pair<String, String>? {
        val lower = q.lowercase()
        val best = items.maxByOrNull { (_, _, keys) -> keys.count { lower.contains(it.lowercase()) } } ?: return null
        val hits = best.third.count { lower.contains(it.lowercase()) }
        return if (hits > 0) best.first to best.second else null
    }
}
