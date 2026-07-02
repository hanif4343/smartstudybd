package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.data.model.UserTechnique
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * প্রাইভেট টেকনিক (isPublic = false) সরাসরি ইউজারের ফোনেই (DataStore) সেভ থাকে।
 * এর জন্য ইন্টারনেট লাগে না — অফলাইনেও অ্যাড/এডিট/ডিলিট করা যায়, এবং অ্যাপ বন্ধ
 * করে আবার খুললেও ডেটা থেকে যায়।
 *
 * key format: "local_<uuid>" — যাতে remote (Firebase push key) আইডির সাথে গুলিয়ে না যায়।
 */
object LocalTechniqueStore {

    private const val LOCAL_ID_PREFIX = "local_"
    private val KEY = stringPreferencesKey("local_private_techniques_json")
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<UserTechnique>>() {}.type

    fun isLocalId(id: String) = id.startsWith(LOCAL_ID_PREFIX)

    // ── প্রশ্ন অনুযায়ী এই ইউজারের প্রাইভেট টেকনিকগুলো পড়ো ──
    suspend fun getForQuestion(context: Context, questionId: String, userId: String): List<UserTechnique> {
        return getAll(context).filter { it.questionId == questionId && it.userId == userId }
    }

    // ── নতুন প্রাইভেট টেকনিক ফোনে সেভ করো ──
    suspend fun add(
        context   : Context,
        questionId: String,
        userId    : String,
        userName  : String,
        text      : String
    ): UserTechnique {
        val technique = UserTechnique(
            id         = LOCAL_ID_PREFIX + UUID.randomUUID().toString(),
            questionId = questionId,
            userId     = userId,
            userName   = userName,
            text       = text,
            isPublic   = false,
            status     = "approved", // প্রাইভেট টেকনিক নিজে থেকেই দেখা যায়, অনুমোদন লাগে না
            timestamp  = System.currentTimeMillis()
        )
        val all = getAll(context).toMutableList()
        all.add(technique)
        save(context, all)
        return technique
    }

    // ── লোকাল টেকনিক এডিট করো ──
    suspend fun update(context: Context, id: String, text: String) {
        val all = getAll(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(text = text, timestamp = System.currentTimeMillis())
            save(context, all)
        }
    }

    // ── লোকাল টেকনিক ডিলিট করো ──
    suspend fun delete(context: Context, id: String) {
        val all = getAll(context).toMutableList()
        all.removeAll { it.id == id }
        save(context, all)
    }

    private suspend fun getAll(context: Context): List<UserTechnique> {
        return try {
            val json = context.dataStore.data.first()[KEY] ?: return emptyList()
            gson.fromJson<MutableList<UserTechnique>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun save(context: Context, list: List<UserTechnique>) {
        context.dataStore.edit { it[KEY] = gson.toJson(list) }
    }
}
