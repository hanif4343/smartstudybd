package com.hanif.smartstudy.util

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * ════════════════════════════════════════════════════════════════
 *  TtsManager
 * ════════════════════════════════════════════════════════════════
 * App-wide একটা মাত্র TextToSpeech instance — Android এর built-in
 * engine ব্যবহার করে (কোনো paid API লাগে না, সম্পূর্ণ ফ্রি)। বাংলা
 * পড়ার জন্য ব্যবহৃত হয় (Study content এর প্রশ্ন/উত্তর শোনার বাটনে)।
 *
 * একই সময়ে একটাই utterance চলে — নতুন কিছু play করতে বললে আগেরটা
 * নিজে থেকেই থেমে যায়, তাই দুটো card থেকে একসাথে আওয়াজ আসার সমস্যা হয় না।
 * speakingKey StateFlow হওয়ায় একই সাথে স্ক্রিনে থাকা একাধিক card প্রত্যেকেই
 * স্বাধীনভাবে জানতে পারে এখন কে বলছে।
 */
object TtsManager {

    private const val TAG = "TtsManager"
    private const val UTTERANCE_ID = "smartstudy_tts"

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var isBanglaAvailable = false
    private var initAttempted = false

    // বর্তমানে কোন key (card/button) বলছে — null মানে কিছুই বলছে না
    private val _speakingKey = MutableStateFlow<String?>(null)
    val speakingKey: StateFlow<String?> = _speakingKey

    fun isSpeaking(key: String): Boolean = _speakingKey.value == key

    /** প্রথমবার ব্যবহারের আগে init করে নাও (একাধিকবার কল করলেও সমস্যা নেই) */
    fun init(context: Context) {
        if (initAttempted) return
        initAttempted = true
        val appCtx = context.applicationContext
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("bn", "BD"))
                isBanglaAvailable = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isBanglaAvailable) {
                    val fallback = tts?.setLanguage(Locale("bn"))
                    isBanglaAvailable = fallback != TextToSpeech.LANG_MISSING_DATA &&
                        fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
                isReady = true
                tts?.setSpeechRate(0.95f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { _speakingKey.value = null }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { _speakingKey.value = null }
                    override fun onError(utteranceId: String?, errorCode: Int) { _speakingKey.value = null }
                })
                Log.d(TAG, "TTS ready, Bangla available: $isBanglaAvailable")
            } else {
                Log.e(TAG, "TTS init failed, status=$status")
            }
        }
    }

    fun isBanglaSupported(): Boolean = isBanglaAvailable
    fun isReadyNow(): Boolean = isReady

    /**
     * Text পড়ে শোনায়। key দিয়ে বোঝা যায় কোন button/card বর্তমানে বলছে (UI তে
     * play/stop আইকন বদলানোর জন্য)। আগে থেকে কিছু বলছে থাকলে সেটা থেমে নতুনটা শুরু হয়।
     * একই key তে আবার চাপলে (toggle) — থেমে যায়।
     */
    fun speak(text: String, key: String) {
        val engine = tts
        if (engine == null || !isReady) {
            Log.w(TAG, "TTS not ready yet")
            return
        }
        if (text.isBlank()) return

        if (_speakingKey.value == key) {
            stop()
            return
        }

        val clean = cleanForSpeech(text)
        _speakingKey.value = key
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        tts?.stop()
        _speakingKey.value = null
    }

    private fun cleanForSpeech(raw: String): String {
        return raw
            .replace(Regex("\\$+"), "")
            .replace(Regex("[*_#`]"), "")
            .replace(Regex("https?://\\S+"), "")
            .trim()
    }

    /** বাংলা ভাষার voice data না থাকলে Android settings এ ইনস্টল করার ইন্টেন্ট */
    fun installLanguageIntent(): Intent {
        return Intent().apply { action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        initAttempted = false
        _speakingKey.value = null
    }
}
