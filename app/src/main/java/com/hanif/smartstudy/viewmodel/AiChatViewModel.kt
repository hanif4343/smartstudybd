package com.hanif.smartstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanif.smartstudy.data.model.AiChatMessage
import com.hanif.smartstudy.data.model.AiChatState
import com.hanif.smartstudy.data.remote.AiChatService
import com.hanif.smartstudy.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ── AI Chat (ডাউট সলভার) ViewModel ──
 * Settings-এ সেভ করা একই AI key গুলো ব্যবহার করে (Groq→Mistral→Cerebras→Gemini) —
 * WrittenAnswerAiService যেভাবে Study-র রিকল-টাইপিং মোডে ব্যবহার হয়, সেই একই
 * key-সেট এখানে সাধারণ প্রশ্ন-উত্তর চ্যাটের জন্য ব্যবহার হচ্ছে। চ্যাট হিস্ট্রি
 * শুধু এই স্ক্রিন খোলা থাকা অবস্থাতেই মেমোরিতে থাকে (in-memory) — স্ক্রিন বন্ধ
 * করে আবার ঢুকলে নতুন করে শুরু হয়।
 */
class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)

    private val _state = MutableStateFlow(AiChatState(hasAnyKey = true))
    val state: StateFlow<AiChatState> = _state.asStateFlow()

    init {
        _state.update { it.copy(hasAnyKey = session.getAiApiKeys().hasAnyKey()) }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _state.value.isSending) return

        val keys = session.getAiApiKeys()
        if (!keys.hasAnyKey()) {
            _state.update {
                it.copy(
                    hasAnyKey = false,
                    error     = "AI চ্যাট চালাতে Settings → AI Key সেকশনে অন্তত একটা key (Groq/Mistral/Cerebras/Gemini) যোগ করো।"
                )
            }
            return
        }

        val historyWithUserMsg = _state.value.messages + AiChatMessage(role = "user", content = trimmed)
        _state.update {
            it.copy(messages = historyWithUserMsg, isSending = true, error = null, hasAnyKey = true)
        }

        viewModelScope.launch {
            val reply = AiChatService.sendMessage(historyWithUserMsg, keys)
            if (reply != null) {
                _state.update {
                    it.copy(
                        messages  = it.messages + AiChatMessage(role = "assistant", content = reply),
                        isSending = false
                    )
                }
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

    fun clearChat() {
        _state.update { it.copy(messages = emptyList(), error = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
