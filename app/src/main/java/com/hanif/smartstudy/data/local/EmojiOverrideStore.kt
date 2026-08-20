package com.hanif.smartstudy.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * App feature request ৪ ("এডমিন হিসেবে সাবজেক্ট বা পদবীর ইমুজি পরিবর্তন করার পাওয়ার
 * দরকার"): Subject/Topic/QBank Post/Institution-এর আইকন আগে শুধু নাম-ম্যাচ করা একটা
 * হার্ডকোড করা keyword→emoji ম্যাপ থেকে আসতো (দেখো SubjectListScreen.kt এর
 * subjectIcons ম্যাপ) — এডমিনের কাস্টমাইজ করার কোনো উপায় ছিল না।
 *
 * এই স্টোরটা admin-সেট emoji override গুলো লোকালি (SharedPreferences, একটা JSON
 * ম্যাপ হিসেবে) রাখে, key = "$refType:$id" (যেমন "subjects:QZ_S01",
 * "posts:PST_003")। adminSetEmoji() একইসাথে GAS-এর updateReferenceField action
 * দিয়ে Sheet-এও লিখে রাখে (দেখো QuizViewModel.adminSetEmoji ও
 * GasContentService.updateReferenceField) — তাই Sheet খুললেও ইমুজিটা দেখা যাবে।
 * তবে GAS-এর getReferenceData action এখনো এই "emoji" কলাম ফেরত দেয় না, তাই
 * অন্য ডিভাইসে/অ্যাপ রিইনস্টলের পর emoji auto-sync হবে না — শুধু যে ডিভাইস থেকে
 * সেট করা হয়েছে সেখানেই সাথে সাথে দেখা যাবে। ফুল ক্রস-ডিভাইস sync একটা ফলো-আপ
 * item (getReferenceData রেসপন্সে emoji কলাম যোগ করা লাগবে)।
 */
class EmojiOverrideStore(context: Context) {
    private val prefs = context.getSharedPreferences("emoji_overrides", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY = "map"

    private fun readAll(): MutableMap<String, String> {
        val json = prefs.getString(KEY, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun getAll(): Map<String, String> = readAll()

    fun set(refType: String, id: String, emoji: String) {
        val all = readAll()
        val key = "$refType:$id"
        if (emoji.isBlank()) all.remove(key) else all[key] = emoji
        prefs.edit().putString(KEY, gson.toJson(all)).apply()
    }
}
