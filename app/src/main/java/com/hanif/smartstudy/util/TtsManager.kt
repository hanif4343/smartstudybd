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
 * ── Mixed Bangla + English pronunciation ──
 * টেক্সটে ইংরেজি শব্দ (Computer, Photosynthesis ইত্যাদি) থাকলে বাংলা ভয়েস
 * দিয়ে পড়লে উচ্চারণ বিকৃত শোনায়। তাই টেক্সটটাকে ভাষা অনুযায়ী ছোট ছোট
 * টুকরায় (segment) ভাগ করা হয় — বাংলা অংশ বাংলা ভয়েসে, ইংরেজি অংশ
 * ইংরেজি (US English) ভয়েসে, প্রতিটা নিজের ভাষায় শুদ্ধ উচ্চারণে পড়ে।
 * প্রতিটা segment আলাদা utterance হিসেবে queue (QUEUE_ADD) করা হয়, তাই
 * একটার পর একটা স্বাভাবিকভাবে চলতে থাকে, মাঝে বিরতি পড়ে না।
 *
 * ── Word highlighting + pause/resume ──
 * Android TTS এর `onRangeStart(utteranceId, start, end, frame)` callback
 * ব্যবহার করে ঠিক কোন word বলা হচ্ছে সেটা জানা যায়। প্রতিটা segment এর
 * নিজস্ব start-offset পুরো টেক্সটের সাপেক্ষে যোগ করে `currentWordRange`
 * আপডেট হয়, তাই highlight পুরো টেক্সট জুড়ে (বাংলা+ইংরেজি মিলিয়ে) সঠিক
 * জায়গায় হয়।
 *
 * Android TTS এ native pause/resume নেই, তাই pause করলে এখন পর্যন্ত কোন
 * segment-এর কতটুকু বলা হয়েছে (segment index + সেই segment-এর ভেতরের
 * charIndex) মনে রাখা হয়, resume করলে ঠিক সেখান থেকে বাকি segment +
 * পরের সব segment আবার queue করা হয়।
 */
object TtsManager {

    private const val TAG = "TtsManager"
    private const val UTTERANCE_PREFIX = "smartstudy_tts_"

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var isBanglaAvailable = false
    private var isEnglishAvailable = false
    private var initAttempted = false

    // বর্তমানে কোন key (card/button) বলছে — null মানে কিছুই বলছে না
    private val _speakingKey = MutableStateFlow<String?>(null)
    val speakingKey: StateFlow<String?> = _speakingKey

    // pause অবস্থায় থাকলে এই key টা সেট থাকে
    private val _pausedKey = MutableStateFlow<String?>(null)
    val pausedKey: StateFlow<String?> = _pausedKey

    // বর্তমান utterance এ এখন যেই word বলা হচ্ছে তার (start,end) — পুরো original text এর সাপেক্ষে
    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange

    // বর্তমানে যেই key এর জন্য speak চলছে/পজ আছে
    private val _activeKeyFlow = MutableStateFlow<String?>(null)
    val activeKeyFlow: StateFlow<String?> = _activeKeyFlow

    // ── ভাষা অনুযায়ী টেক্সট-টুকরা (segment) ──
    private data class Segment(val text: String, val isEnglish: Boolean, val startOffset: Int)

    private var fullText: String = ""               // মূল (clean করা) সম্পূর্ণ টেক্সট
    private var segments: List<Segment> = emptyList() // ভাষা অনুযায়ী ভাগ করা অংশ
    private var currentSegmentIdx: Int = 0            // এখন কোন segment বলা হচ্ছে
    private var resumeFromChar: Int = 0                // পুরো text এর সাপেক্ষে — এখান থেকে resume
    private var activeKey: String? = null

    fun isSpeaking(key: String): Boolean = _speakingKey.value == key
    fun isPaused(key: String): Boolean = _pausedKey.value == key

    /** প্রথমবার ব্যবহারের আগে init করে নাও (একাধিকবার কল করলেও সমস্যা নেই) */
    fun init(context: Context) {
        if (initAttempted) return
        initAttempted = true
        val appCtx = context.applicationContext
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val bnResult = tts?.setLanguage(Locale("bn", "BD"))
                isBanglaAvailable = bnResult != TextToSpeech.LANG_MISSING_DATA &&
                    bnResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isBanglaAvailable) {
                    val fallback = tts?.setLanguage(Locale("bn"))
                    isBanglaAvailable = fallback != TextToSpeech.LANG_MISSING_DATA &&
                        fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
                // ইংরেজি voice আলাদাভাবে available কিনা যাচাই (একই engine এ থাকে সাধারণত)
                val enResult = tts?.isLanguageAvailable(Locale.US)
                isEnglishAvailable = enResult == TextToSpeech.LANG_AVAILABLE ||
                    enResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    enResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                // ডিফল্ট বাংলায় সেট রাখি, প্রতি segment এ প্রয়োজনমতো বদলানো হবে
                tts?.setLanguage(if (isBanglaAvailable) Locale("bn", "BD") else Locale.US)
                isReady = true
                tts?.setSpeechRate(0.95f)

                // বাংলা voice এর gender বের করে same gender এর English voice খুঁজি,
                // যাতে বাংলা→English switch এ voice gender না বদলায়
                findMatchingEnglishVoice()

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        val idx = utteranceIdToSegmentIdx(utteranceId)
                        if (idx == null || idx >= segments.size - 1) {
                            // সব segment শেষ — সম্পূর্ণ utterance শেষ
                            finishAll()
                        } else {
                            // এই segment শেষ — পরের segment নিজে থেকেই engine এর queue তে চলবে,
                            // কিন্তু আমরা currentSegmentIdx আপডেট রাখি যাতে pause ঠিক জায়গায় হয়
                            currentSegmentIdx = idx + 1
                            val nextSeg = segments.getOrNull(currentSegmentIdx)
                            if (nextSeg != null) resumeFromChar = nextSeg.startOffset
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { finishAll() }
                    override fun onError(utteranceId: String?, errorCode: Int) { finishAll() }

                    // API 26+ — কোন word/range বলা হচ্ছে এখন (current segment এর ভেতরের index)
                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        val idx = utteranceIdToSegmentIdx(utteranceId) ?: return
                        val seg = segments.getOrNull(idx) ?: return
                        val globalStart = seg.startOffset + start
                        val globalEnd = seg.startOffset + end
                        _currentWordRange.value = globalStart..globalEnd
                        resumeFromChar = globalStart
                    }
                })
                Log.d(TAG, "TTS ready, Bangla=$isBanglaAvailable, English=$isEnglishAvailable")
            } else {
                Log.e(TAG, "TTS init failed, status=$status")
            }
        }
    }

    private fun finishAll() {
        _speakingKey.value = null
        _pausedKey.value = null
        _currentWordRange.value = null
        _activeKeyFlow.value = null
        fullText = ""
        segments = emptyList()
        currentSegmentIdx = 0
        resumeFromChar = 0
        activeKey = null
    }

    private fun utteranceIdToSegmentIdx(utteranceId: String?): Int? {
        if (utteranceId == null || !utteranceId.startsWith(UTTERANCE_PREFIX)) return null
        return utteranceId.removePrefix(UTTERANCE_PREFIX).toIntOrNull()
    }

    fun isBanglaSupported(): Boolean = isBanglaAvailable
    fun isReadyNow(): Boolean = isReady

    // বাংলা voice এর gender এর সাথে মেলে এমন English voice — voice switch এ gender অপরিবর্তিত রাখে
    private var matchingEnglishVoice: android.speech.tts.Voice? = null

    private fun findMatchingEnglishVoice() {
        val engine = tts ?: return
        try {
            val currentVoice = engine.voice ?: return
            val currentGender = currentVoice.features
                ?.firstOrNull { it.startsWith("gender") }
                ?: "gender:male" // default assume male (বাংলা সাধারণত male)
            val isMale = !currentGender.contains("female")

            val englishVoices = engine.voices
                ?.filter { v ->
                    (v.locale.language == "en") &&
                    !v.isNetworkConnectionRequired &&
                    v.quality >= android.speech.tts.Voice.QUALITY_NORMAL
                } ?: emptyList()

            // same gender এর voice খোঁজো, না পেলে যেকোনো English voice নাও
            matchingEnglishVoice = englishVoices.firstOrNull { v ->
                val genderFeature = v.features?.firstOrNull { it.startsWith("gender") } ?: ""
                if (isMale) !genderFeature.contains("female")
                else genderFeature.contains("female")
            } ?: englishVoices.firstOrNull()

            Log.d(TAG, "Bangla voice: ${currentVoice.name}, English voice matched: ${matchingEnglishVoice?.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Voice matching failed: ${e.message}")
        }
    }

    /**
     * টেক্সটকে বাংলা ও ইংরেজি অংশে ভাগ করে — যেন প্রতিটা অংশ তার নিজের
     * ভাষার voice দিয়ে শুদ্ধভাবে পড়া যায়। সংখ্যা/যতিচিহ্ন বর্তমান ভাষার
     * সাথেই থাকে (আলাদা ভাঙা হয় না, যাতে অপ্রয়োজনীয় বিরতি না পড়ে)।
     */
    private fun splitByLanguage(text: String): List<Pair<String, Boolean>> {
        // ইংরেজি অক্ষর-ভিত্তিক শব্দ খুঁজে বের করা (a-z, A-Z, এবং সংখ্যা/চিহ্নসহ পাশাপাশি থাকলে একসাথে রাখা)
        val regex = Regex("[A-Za-z][A-Za-z0-9.\\-]*")
        val result = mutableListOf<Pair<String, Boolean>>()
        var lastEnd = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > lastEnd) {
                val banglaPart = text.substring(lastEnd, match.range.first)
                if (banglaPart.isNotEmpty()) result.add(banglaPart to false)
            }
            result.add(match.value to true)
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            result.add(text.substring(lastEnd) to false)
        }
        // ছোট ছোট আলাদা অংশ যেগুলো ফাঁকা/শুধু স্পেস সেগুলো বাদ
        return result.filter { it.first.isNotEmpty() }
    }

    /** পুরো text কে Segment list এ রূপান্তর করে — প্রতিটার global startOffset সহ */
    private fun buildSegments(text: String): List<Segment> {
        val parts = splitByLanguage(text)
        val segs = mutableListOf<Segment>()
        var offset = 0
        for ((part, isEnglish) in parts) {
            segs.add(Segment(part, isEnglish, offset))
            offset += part.length
        }
        return segs
    }

    /** segmentIdx থেকে শুরু করে বাকি সব segment engine এর queue তে পাঠায় */
    private fun queueFrom(segmentIdx: Int, charOffsetWithinSegment: Int = 0) {
        val engine = tts ?: return
        if (!isBanglaAvailable && !isEnglishAvailable) return

        var first = true
        for (i in segmentIdx until segments.size) {
            val seg = segments[i]
            val textToSpeak = if (i == segmentIdx && charOffsetWithinSegment > 0) {
                seg.text.substring(charOffsetWithinSegment.coerceIn(0, seg.text.length))
            } else seg.text
            if (textToSpeak.isBlank()) continue

            if (seg.isEnglish && isEnglishAvailable) {
                // matched English voice (same gender) ব্যবহার করো — না পেলে locale fallback
                if (matchingEnglishVoice != null) {
                    engine.voice = matchingEnglishVoice
                } else {
                    engine.language = Locale.US
                }
            } else {
                engine.language = if (isBanglaAvailable) Locale("bn", "BD") else Locale.US
            }

            val mode = if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(textToSpeak, mode, null, "$UTTERANCE_PREFIX$i")
            first = false
        }
    }

    /**
     * Text পড়ে শোনায় (মিশ্র বাংলা+ইংরেজি ভাষা সঠিক উচ্চারণে)। key দিয়ে বোঝা যায়
     * কোন button/card বর্তমানে বলছে। আগে থেকে অন্য কিছু বলছে থাকলে সেটা থেমে
     * নতুনটা শুরু হয়। একই key তে আবার চাপলে — pause হয় (position মনে থাকে)।
     * pause অবস্থায় একই key তে চাপলে — যেখানে থেমেছিল সেখান থেকেই resume হয়।
     */
    fun speak(text: String, key: String) {
        val engine = tts
        if (engine == null || !isReady) {
            Log.w(TAG, "TTS not ready yet")
            return
        }
        if (text.isBlank()) return

        // চলমান অবস্থায় একই key তে চাপ → pause
        if (_speakingKey.value == key) {
            pause()
            return
        }

        // pause অবস্থায় একই key তে চাপ → resume
        if (_pausedKey.value == key && fullText.isNotEmpty()) {
            resume()
            return
        }

        // নতুন key, বা ভিন্ন কিছু আগে চলছিল — একদম নতুন করে শুরু
        engine.stop()
        val clean = cleanForSpeech(text)
        fullText = clean
        segments = buildSegments(clean)
        currentSegmentIdx = 0
        resumeFromChar = 0
        activeKey = key
        _pausedKey.value = null
        _speakingKey.value = key
        _activeKeyFlow.value = key
        _currentWordRange.value = 0..0
        queueFrom(0)
    }

    /** চলমান speech থামিয়ে দাও কিন্তু কতটুকু বলা হয়েছে সেটা মনে রাখো — পরে resume করা যাবে */
    fun pause() {
        tts?.stop()
        _pausedKey.value = activeKey
        _speakingKey.value = null
    }

    /** যেখানে pause হয়েছিল ঠিক সেখান থেকে আবার বলা শুরু করো */
    fun resume() {
        val key = activeKey ?: return
        if (fullText.isEmpty() || segments.isEmpty()) return

        // resumeFromChar অনুযায়ী কোন segment-এ আছি ও সেই segment-এর কোন charIndex থেকে শুরু করতে হবে বের করি
        var segIdx = 0
        var charInSeg = 0
        for ((i, seg) in segments.withIndex()) {
            val segEnd = seg.startOffset + seg.text.length
            if (resumeFromChar < segEnd) {
                segIdx = i
                charInSeg = (resumeFromChar - seg.startOffset).coerceAtLeast(0)
                break
            }
            segIdx = i + 1
        }
        if (segIdx >= segments.size) {
            // আর কিছু বলার নেই
            _pausedKey.value = null
            _speakingKey.value = null
            _activeKeyFlow.value = null
            return
        }

        _pausedKey.value = null
        _speakingKey.value = key
        _activeKeyFlow.value = key
        queueFrom(segIdx, charInSeg)
    }

    /** সম্পূর্ণভাবে থামিয়ে state রিসেট করো (pause না, পুরোপুরি বন্ধ) */
    fun stop() {
        tts?.stop()
        finishAll()
    }

    /**
     * একটা একক শব্দ/বাক্যাংশ তাৎক্ষণিকভাবে সঠিক ভাষায় উচ্চারণ করে শোনায় —
     * যেমন কোনো ইংরেজি শব্দে ট্যাপ করলে তার শুদ্ধ ইংরেজি উচ্চারণ শোনানোর জন্য।
     * এটা বর্তমান চলমান/পজ থাকা TTS state কে প্রভাবিত করে না (আলাদা one-shot speak)।
     */
    fun speakWord(word: String) {
        val engine = tts ?: return
        if (!isReady || word.isBlank()) return
        val isEnglish = Regex("^[A-Za-z0-9.\\-]+$").matches(word.trim())
        if (isEnglish && isEnglishAvailable) {
            if (matchingEnglishVoice != null) engine.voice = matchingEnglishVoice
            else engine.language = Locale.US
        } else {
            engine.language = if (isBanglaAvailable) Locale("bn", "BD") else Locale.US
        }
        engine.speak(word.trim(), TextToSpeech.QUEUE_FLUSH, null, "smartstudy_word_tap")
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
        finishAll()
    }
}
