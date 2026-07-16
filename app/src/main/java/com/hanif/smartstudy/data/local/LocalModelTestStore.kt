package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.data.model.ModelTestMeta
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first

// ─────────────────────────────────────────────────────────
// LocalModelTestStore — ইউজার নিজে জেনারেট করা Model Test (DataStore-এ JSON আকারে)।
// শুধু এই ডিভাইসেই থাকে — Firebase-এ আপলোড হয় না, রিইনস্টল করলে মুছে যায়।
//
// key = subject নাম। Job seeker-দের জন্য সব সাবজেক্ট মিশিয়ে একটাই ব্যাচ বানানো হয়,
// সেটা JOB_ALL_KEY সেন্টিনেল "subject" নামে সেভ হয় (আসল কোনো সাবজেক্টের নামের সাথে
// সংঘর্ষ এড়াতে অস্বাভাবিক প্রিফিক্স ব্যবহার করা হয়েছে)।
//
// একই subject-এ আবার জেনারেট করলে পুরনো ব্যাচ রিপ্লেস হয়ে যায় (saveTests সবসময় পুরো লিস্ট বদলে দেয়)।
// ─────────────────────────────────────────────────────────
class LocalModelTestStore(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_JSON = stringPreferencesKey("local_model_tests_json")
        const val JOB_ALL_KEY = "__JOB_ALL__"
        const val JOB_ALL_LABEL = "সকল বিষয় (মিশ্র)"
    }

    private suspend fun readAll(): MutableMap<String, List<ModelTestMeta>> {
        val json = context.dataStore.data.first()[KEY_JSON]
        if (json.isNullOrBlank()) return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<String, List<ModelTestMeta>>>() {}.type
            val parsed: Map<String, List<ModelTestMeta>>? = gson.fromJson(json, type)
            (parsed ?: emptyMap()).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    suspend fun getTests(subjectKey: String): List<ModelTestMeta> = readAll()[subjectKey].orEmpty()

    suspend fun countFor(subjectKey: String): Int = getTests(subjectKey).size

    // পুরো ব্যাচ রিপ্লেস করে সেভ করে (রিজেনারেট = পুরনোটা মুছে নতুনটা বসে)
    suspend fun saveTests(subjectKey: String, tests: List<ModelTestMeta>) {
        val all = readAll()
        all[subjectKey] = tests
        context.dataStore.edit { it[KEY_JSON] = gson.toJson(all) }
    }
}
