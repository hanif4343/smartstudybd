package com.hanif.smartstudy.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * টাইপিং করার সময় প্রতিটা কী-প্রেসে ছোট্ট একটা ক্লিক-সাউন্ড — Neonlipi-এর "সাউন্ড প্রিসেট"
 * ফিচারের সরলীকৃত সংস্করণ। কোনো audio asset ফাইল লাগে না — Android-এর বিল্ট-ইন
 * ToneGenerator দিয়েই ছোট টোন বাজানো হয়, তাই APK সাইজ/এসেট ম্যানেজমেন্ট নিয়ে ভাবতে হয় না।
 *
 * Settings-এ (SessionManager.getTypingSoundPreset()) তিনটা অপশন:
 *   "off"        → সাউন্ড বন্ধ
 *   "soft"       → হালকা, নিচু-ভলিউমের ক্লিক (ডিফল্ট)
 *   "mechanical" → একটু জোরালো/খসখসে ক্লিক (মেকানিক্যাল কীবোর্ডের অনুভূতি)
 *
 * গ্লোবাল SessionManager.isSoundOff() (Settings-এর সাধারণ সাউন্ড টগল) অন থাকলে এটা
 * প্রিসেট যাই থাকুক না কেন বাজবে না — একটাই জায়গা থেকে পুরো অ্যাপের সাউন্ড নিয়ন্ত্রণ করা যায়।
 */
object TypingKeySound {

    @Volatile private var toneGen: ToneGenerator? = null

    private fun getOrCreate(): ToneGenerator? {
        toneGen?.let { return it }
        return try {
            ToneGenerator(AudioManager.STREAM_SYSTEM, 40).also { toneGen = it }
        } catch (e: Exception) {
            null // কিছু ডিভাইসে audio focus/hardware সমস্যায় তৈরি না-ও হতে পারে — নীরবে স্কিপ
        }
    }

    /** প্রতিটা কী-প্রেসে TypingPracticeScreen থেকে কল হবে — নিজে থেকেই প্রিসেট/গ্লোবাল
     *  টগল চেক করে নেয়, caller-কে আলাদা করে চেক করতে হয় না। */
    fun playForCurrentPreset(context: Context) {
        val session = SessionManager(context)
        if (session.isSoundOff()) return
        val preset = session.getTypingSoundPreset()
        val (tone, durationMs) = when (preset) {
            "off"        -> return
            "mechanical" -> ToneGenerator.TONE_PROP_BEEP2 to 25
            else         -> ToneGenerator.TONE_PROP_BEEP  to 15   // "soft" ডিফল্ট
        }
        try {
            getOrCreate()?.startTone(tone, durationMs)
        } catch (e: Exception) {
            // সাউন্ড ব্যর্থ হলেও টাইপিং যেন কখনো ব্লক না হয়
        }
    }

    /** স্ক্রিন বন্ধ হওয়ার সময় resource ছেড়ে দিতে — না ডাকলেও লিক হয় না (singleton),
     *  তবে ভালো practice হিসেবে TypingPracticeScreen-এর DisposableEffect থেকে কল করা যায়। */
    fun release() {
        toneGen?.release()
        toneGen = null
    }
}
