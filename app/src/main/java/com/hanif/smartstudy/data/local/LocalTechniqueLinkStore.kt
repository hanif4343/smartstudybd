package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first

/**
 * প্রাইভেট টেকনিকের "সম্পর্কিত প্রশ্ন" লিংক — শুধু এই ফোনেই থাকে (অন্য কেউ কখনো দেখে না)।
 *
 * একটা প্রাইভেট টেকনিক (techId) কোন কোন টার্গেট প্রশ্নে দেখাবে (targets), আর কোন কোন প্রশ্ন
 * ইউজার "বাদ" দিয়েছে (rejected — আর প্রস্তাবে আসবে না)। টেকনিকের লেখা কপি করা হয় না —
 * দেখানোর সময় LocalTechniqueStore থেকে সরাসরি পড়া হয়, তাই আসল টেকনিক এডিট করলে
 * সব জায়গায় নিজে থেকেই বদলে যায়।
 */
object LocalTechniqueLinkStore {

    private data class Entry(
        val targets : MutableSet<String> = mutableSetOf(),
        val rejected: MutableSet<String> = mutableSetOf()
    )

    private val KEY = stringPreferencesKey("private_technique_links_json")
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, Entry>>() {}.type

    private suspend fun load(context: Context): MutableMap<String, Entry> = try {
        val json = context.dataStore.data.first()[KEY]
        if (json.isNullOrBlank()) mutableMapOf() else (gson.fromJson<MutableMap<String, Entry>>(json, mapType) ?: mutableMapOf())
    } catch (e: Exception) { mutableMapOf() }

    private suspend fun save(context: Context, m: Map<String, Entry>) {
        context.dataStore.edit { it[KEY] = gson.toJson(m) }
    }

    /** (সংযুক্ত টার্গেট, বাদ দেওয়া টার্গেট) */
    suspend fun getState(context: Context, techId: String): Pair<Set<String>, Set<String>> {
        val e = load(context)[techId]
        return (e?.targets?.toSet() ?: emptySet()) to (e?.rejected?.toSet() ?: emptySet())
    }

    /** এই টার্গেট প্রশ্নে কোন কোন প্রাইভেট টেকনিক (id) সংযুক্ত */
    suspend fun getTechIdsForTarget(context: Context, targetQuestionId: String): List<String> =
        load(context).filter { targetQuestionId in it.value.targets }.keys.toList()

    suspend fun link(context: Context, techId: String, targetQuestionId: String) {
        val m = load(context); val e = m.getOrPut(techId) { Entry() }
        e.targets.add(targetQuestionId); e.rejected.remove(targetQuestionId); save(context, m)
    }

    suspend fun reject(context: Context, techId: String, targetQuestionId: String) {
        val m = load(context); val e = m.getOrPut(techId) { Entry() }
        e.rejected.add(targetQuestionId); e.targets.remove(targetQuestionId); save(context, m)
    }

    /** সংযুক্ত কপি সরানো — "বাদ" হিসেবেও মনে রাখা হয় */
    suspend fun unlink(context: Context, techId: String, targetQuestionId: String) = reject(context, techId, targetQuestionId)

    /** টেকনিক মুছলে/পাবলিক করলে তার সব লিংক মুছে দাও */
    suspend fun removeAll(context: Context, techId: String) {
        val m = load(context)
        if (m.remove(techId) != null) save(context, m)
    }
}
