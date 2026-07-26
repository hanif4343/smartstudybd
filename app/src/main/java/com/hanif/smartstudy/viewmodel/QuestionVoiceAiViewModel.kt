package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.AiChatMessage
import com.hanif.smartstudy.data.model.AiChatState
import com.hanif.smartstudy.data.model.QuestionItem
import com.hanif.smartstudy.data.model.StudyMode
import com.hanif.smartstudy.data.remote.AiChatService
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ══════════════════════════════════════════════════════════════════
 *  QuestionVoiceAiViewModel — প্রতিটা প্রশ্নের পাশে 🤖 বাটন থেকে খোলা
 *  ভয়েস AI চ্যাট। AiChatViewModel (সাধারণ ডাউট-সলভার)-এরই মতো একই
 *  key-সেট (Groq→Mistral→Cerebras→Gemini) ব্যবহার করে, পার্থক্য শুধু:
 *
 *  ১) setQuestion() কল করলে সেই নির্দিষ্ট প্রশ্ন/অপশন/উত্তর/ব্যাখ্যা/
 *     টেকনিক system prompt-এর সাথে জুড়ে পাঠানো হয় (AiChatService-এর
 *     নতুন contextPrefix প্যারামিটার দিয়ে) — AI তখন সাধারণভাবে না
 *     বলে ঠিক ওই প্রশ্ন নিয়েই কথা বলে।
 *  ২) AI-এর প্রতিটা উত্তর অটোমেটিক্যালি TtsManager দিয়ে পড়ে শোনানো
 *     হয় — এটা ভয়েস কথোপকথনের অংশ, তাই টাইপ করা উত্তরের মতো শুধু
 *     স্ক্রিনে দেখালেই চলবে না।
 *  ৩) "পরের প্রশ্ন"-এ গেলে setQuestion() আবার কল হয়ে চ্যাট হিস্ট্রি
 *     রিসেট হয়ে যায় — প্রতিটা প্রশ্নের কথোপকথন আলাদা থাকে, আগের
 *     প্রশ্নের প্রসঙ্গ পরের প্রশ্নে বহন হয়ে গিয়ে AI-কে বিভ্রান্ত করে না।
 *
 *  ভয়েস ইনপুট নিজে এই ViewModel-এ নেই — সেটা Android-এর নিজস্ব
 *  RecognizerIntent (system "কথা বলুন" ডায়ালগ) দিয়ে UI লেয়ারেই হয়
 *  (QuestionVoiceAiSheet.kt), তাই RECORD_AUDIO পারমিশনের দরকার হয় না।
 * ══════════════════════════════════════════════════════════════════
 */
class QuestionVoiceAiViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)

    private val _state = MutableStateFlow(AiChatState(hasAnyKey = true))
    val state: StateFlow<AiChatState> = _state.asStateFlow()

    private var contextPrefix: String = ""
    private var currentQuestionId: String = ""

    init {
        _state.update { it.copy(hasAnyKey = session.getAiApiKeys().hasAnyKey()) }
    }

    /** নতুন প্রশ্নে "সুইচ" করে — চ্যাট রিসেট, নতুন context বসে, এবং সাথে সাথেই AI
     * প্রশ্ন+উত্তরের ব্যাখ্যা শুরু করে দেয় (autoExplain) — ইউজারকে আলাদা করে মাইক
     * চেপে জিজ্ঞেস করতে হয় না, 🤖 বাটন চাপাটাই যথেষ্ট ইঙ্গিত। */
    fun setQuestion(item: QuestionItem, mode: StudyMode) {
        if (item.id == currentQuestionId) return   // একই প্রশ্নে বারবার রিসেট না হয়
        currentQuestionId = item.id

        val modeLabel = when (mode) {
            StudyMode.QUIZ  -> "Quiz"
            StudyMode.QBANK -> "Question Bank"
            StudyMode.STUDY -> "Study"
        }
        val questionText = item.question.ifBlank { item.explanation }
        contextPrefix = buildString {
            append("এখন ছাত্র একটা নির্দিষ্ট প্রশ্ন সামনে রেখে তোমার সাথে ভয়েসে কথা বলছে। ")
            append("নিচের প্রশ্ন/উত্তর/ব্যাখ্যার প্রসঙ্গেই কথা বলবে — প্রয়োজনে আরও সহজ করে বুঝিয়ে দাও, ")
            append("ছোট উদাহরণ দাও, বা ছাত্রের নির্দিষ্ট জিজ্ঞাসার উত্তর দাও। এটা জোরে পড়ে শোনানো হবে, ")
            append("তাই উত্তর কথা-বলার-মতো সংক্ষিপ্ত রাখবে (২-৪ বাক্য), তালিকা/মার্কডাউন ফরম্যাট ব্যবহার করবে না।\n\n")
            append("বিষয়: ${item.subject}")
            if (item.subTopic.isNotBlank()) append(" — ${item.subTopic}")
            append(" ($modeLabel)\n")
            append("প্রশ্ন: $questionText\n")
            if (item.isMcq()) {
                append("অপশন: ক) ${item.optionA}  খ) ${item.optionB}  গ) ${item.optionC}  ঘ) ${item.optionD}\n")
            }
            if (item.answer.isNotBlank()) append("সঠিক উত্তর: ${item.answer}\n")
            if (item.explanation.isNotBlank()) append("ব্যাখ্যা: ${item.explanation}\n")
            if (item.technique.isNotBlank()) append("মনে রাখার কৌশল: ${item.technique}\n")
        }
        _state.update { it.copy(messages = emptyList(), error = null, isSending = false) }
        autoExplain()
    }

    /** 🤖 বাটন চাপলেই — ইউজারকে কিছু জিজ্ঞেস করতে হয় না, AI নিজে থেকেই প্রশ্ন+উত্তরের
     * সহজ ব্যাখ্যা দিয়ে শুরু করে। এই "hidden" instruction টা চ্যাটে user-bubble হিসেবে
     * দেখানো হয় না (state.messages-এ যোগ হয় না) — শুধু AI-এর জবাবটাই প্রথম বাবল হিসেবে
     * দেখা যায়, ঠিক যেন AI নিজে থেকেই কথা শুরু করলো। */
    private fun autoExplain() {
        val keys = session.getAiApiKeys()
        if (!keys.hasAnyKey()) return   // key না থাকলে UI-তে আলাদা warning আছেই, এখানে চুপ থাকি
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val hiddenPrompt = AiChatMessage(
                role = "user",
                content = "প্রশ্ন আর সঠিক উত্তরটা সংক্ষেপে, সহজ ভাষায় বুঝিয়ে দাও — যেন ছাত্র এই প্রথম শুনছে। " +
                    "কথা-বলার-মতো ২-৪ বাক্যে বলো, তালিকা/হেডিং ব্যবহার কোরো না।"
            )
            val reply = AiChatService.sendMessage(listOf(hiddenPrompt), keys, contextPrefix)
            if (reply != null) {
                _state.update { it.copy(messages = listOf(AiChatMessage(role = "assistant", content = reply)), isSending = false) }
                TtsManager.speak(reply, "voice_ai_reply")
            } else {
                _state.update {
                    it.copy(isSending = false, error = "AI থেকে উত্তর পাওয়া যায়নি — একটু পর আবার চেষ্টা করো, বা Settings-এ API key চেক করো।")
                }
            }
        }
    }

    /** "এই টপিকের মূল বিষয়বস্তু জানুন" কুইক-অ্যাকশন বাটন — পুরো চ্যাপ্টার/টপিক না, শুধু
     * এই নির্দিষ্ট প্রশ্নে যে ধারণা/নিয়ম পরীক্ষা করা হচ্ছে সেটুকুই সহজ ভাষায় বুঝিয়ে দিতে বলে।
     * (যেমন: প্রশ্নটা কোনো নির্দিষ্ট কারক নিয়ে হলে, পুরো "কারক" অধ্যায় না — শুধু ওই
     * নির্দিষ্ট কারকটাই ব্যাখ্যা করবে, কারণ AI-কে ইতিমধ্যে contextPrefix-এ পুরো প্রশ্নটা
     * দেওয়া আছে, তাই ঠিক কোন অংশটুকু প্রাসঙ্গিক সেটা AI নিজেই বুঝে নিতে পারবে।) */
    fun explainCoreConcept() {
        sendMessage(
            "এই প্রশ্নে যে নির্দিষ্ট ধারণা/নিয়মটা পরীক্ষা করা হচ্ছে (পুরো অধ্যায়/টপিক না, শুধু এইটুকু " +
                "নির্দিষ্ট অংশ), সেটা সহজ ভাষায়, একটা ছোট উদাহরণসহ বুঝিয়ে দাও।",
            displayText = "📘 এই টপিকের মূল বিষয়বস্তু জানুন"
        )
    }

    /**
     * @param displayText ঐচ্ছিক — দেওয়া থাকলে চ্যাটে এই ছোট টেক্সটটা user-bubble হিসেবে
     * দেখানো হয় (যেমন quick-action বাটনের লেবেল), কিন্তু AI-কে পাঠানো হয় `text`-এর পুরো
     * instruction-টাই। না দিলে দুটোই একই (স্বাভাবিক মাইক/টাইপ করা মেসেজের জন্য)।
     */
    fun sendMessage(text: String, displayText: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _state.value.isSending) return

        val keys = session.getAiApiKeys()
        if (!keys.hasAnyKey()) {
            _state.update {
                it.copy(
                    hasAnyKey = false,
                    error     = "AI দিয়ে কথা বলতে Settings → AI Key সেকশনে অন্তত একটা key (Groq/Mistral/Cerebras/Gemini) যোগ করো।"
                )
            }
            return
        }

        val apiHistory = _state.value.messages + AiChatMessage(role = "user", content = trimmed)
        val visibleHistory = _state.value.messages + AiChatMessage(role = "user", content = displayText ?: trimmed)
        _state.update {
            it.copy(messages = visibleHistory, isSending = true, error = null, hasAnyKey = true)
        }

        viewModelScope.launch {
            val reply = AiChatService.sendMessage(apiHistory, keys, contextPrefix)
            if (reply != null) {
                _state.update {
                    it.copy(
                        messages  = it.messages + AiChatMessage(role = "assistant", content = reply),
                        isSending = false
                    )
                }
                TtsManager.speak(reply, "voice_ai_reply")
            } else {
                _state.update {
                    it.copy(
                        isSending = false,
                        error     = "AI থেকে উত্তর পাওয়া যায়নি — একটু পর আবার চেষ্টা করো, বা Settings-এ API key চেক করো।"
                    )
                }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        TtsManager.stop()
    }
}
