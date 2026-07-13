package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first

/**
 * Offline Pending Queue:
 * Internet না থাকলে actions queue-এ রাখে।
 * Internet আসলে WorkManager দিয়ে sync হয়।
 *
 * Supported actions: quiz_answer, study_progress, xp_update, admin_edit_question
 */
data class PendingAction(
    val id         : String = java.util.UUID.randomUUID().toString(),
    val type       : String,      // "quiz_answer" | "study_progress" | "xp_update" | "admin_edit_question"
    val payload    : String,      // JSON string
    val createdAt  : Long   = System.currentTimeMillis(),
    val retryCount : Int    = 0
)

class PendingQueue(private val context: Context) {

    private val gson = Gson()
    private val KEY  = stringPreferencesKey("pending_queue_json")
    private val type = object : TypeToken<MutableList<PendingAction>>() {}.type

    // ── Queue-এ action যোগ করো ──
    suspend fun enqueue(action: PendingAction) {
        val queue = getAll().toMutableList()
        queue.add(action)
        save(queue)
    }

    // ── Quiz answer offline ──
    suspend fun enqueueQuizAnswer(questionId: String, isCorrect: Boolean, phone: String) {
        enqueue(PendingAction(
            type    = "quiz_answer",
            payload = gson.toJson(mapOf(
                "questionId" to questionId,
                "isCorrect"  to isCorrect,
                "phone"      to phone
            ))
        ))
    }

    // ── XP update offline ──
    suspend fun enqueueXpUpdate(phone: String, xpDelta: Int) {
        enqueue(PendingAction(
            type    = "xp_update",
            payload = gson.toJson(mapOf(
                "phone"   to phone,
                "xpDelta" to xpDelta
            ))
        ))
    }

    // ── Study session offline ──
    suspend fun enqueueStudyProgress(phone: String, minutes: Int, topic: String) {
        enqueue(PendingAction(
            type    = "study_progress",
            payload = gson.toJson(mapOf(
                "phone"   to phone,
                "minutes" to minutes,
                "topic"   to topic
            ))
        ))
    }

    // ── Admin: offline question edit ──
    suspend fun enqueueAdminEdit(
        sheet      : String,
        questionId : String,
        fields     : Map<String, String>,
        questionPreview: String = ""   // UI তে দেখানোর জন্য প্রশ্নের প্রথম কিছু অংশ
    ) {
        enqueue(PendingAction(
            type    = "admin_edit_question",
            payload = gson.toJson(mapOf(
                "sheet"           to sheet,
                "questionId"      to questionId,
                "fields"          to fields,
                "questionPreview" to questionPreview.take(80)
            ))
        ))
    }

    // ── Admin: offline নতুন প্রশ্ন যোগ ──
    // localId = লোকালি generate করা temp id (এই আইডি দিয়েই cache-এ item দেখানো হয়,
    // sync সফল হলে আসল Firebase push-key দিয়ে বদলে যায়)
    suspend fun enqueueAdminAdd(
        sheet      : String,
        localId    : String,
        fields     : Map<String, String>,
        questionPreview: String = ""
    ) {
        enqueue(PendingAction(
            type    = "admin_add_question",
            payload = gson.toJson(mapOf(
                "sheet"           to sheet,
                "localId"         to localId,
                "fields"          to fields,
                "questionPreview" to questionPreview.take(80)
            ))
        ))
    }

    // ── Admin: প্রশ্ন কার্ড ডিলিট (অফলাইন/fail হলে queue এ রাখা হয়, net আসলে
    //    Firebase থেকেও ডিলিট হয়ে যাবে) ──
    suspend fun enqueueAdminDelete(
        sheet      : String,
        questionId : String,
        questionPreview: String = ""
    ) {
        enqueue(PendingAction(
            type    = "admin_delete_question",
            payload = gson.toJson(mapOf(
                "sheet"           to sheet,
                "questionId"      to questionId,
                "questionPreview" to questionPreview.take(80)
            ))
        ))
    }

    // ── কোনো প্রশ্ন (rowKey/localId) ডিলিট হয়ে গেলে সেই প্রশ্নের জন্য আগে থেকে
    //    queue-তে থাকা pending edit/add action গুলো আর দরকার নেই — সরিয়ে ফেলো।
    //    বিশেষত: এখনো sync না হওয়া লোকাল-add প্রশ্ন ডিলিট করলে তো Firebase-এ
    //    কখনো পাঠানোরই দরকার নেই ──
    suspend fun removePendingForQuestion(questionId: String) {
        val queue = getAll().toMutableList()
        queue.removeAll { action ->
            if (action.type != "admin_edit_question" && action.type != "admin_add_question") return@removeAll false
            try {
                val map = gson.fromJson<Map<String, Any>>(action.payload, object : TypeToken<Map<String, Any>>() {}.type)
                map["questionId"]?.toString() == questionId || map["localId"]?.toString() == questionId
            } catch (e: Exception) { false }
        }
        save(queue)
    }

    // ── Pending admin edit + add + delete — সবগুলোই একসাথে (Pending Sync ট্যাবে দেখানোর জন্য) ──
    suspend fun getPendingAdminActions(): List<PendingAction> =
        getAll().filter { it.type == "admin_edit_question" || it.type == "admin_add_question" || it.type == "admin_delete_question" }

    // ── Pending admin edits আলাদা করে দেখাও ──
    suspend fun getPendingAdminEdits(): List<PendingAction> =
        getAll().filter { it.type == "admin_edit_question" }

    // ── সব pending action পড়ো ──
    suspend fun getAll(): List<PendingAction> {
        return try {
            val json = context.dataStore.data.first()[KEY] ?: return emptyList()
            gson.fromJson<MutableList<PendingAction>>(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // ── একটি action সফলভাবে sync হলে remove করো ──
    suspend fun remove(actionId: String) {
        val queue = getAll().toMutableList()
        queue.removeAll { it.id == actionId }
        save(queue)
    }

    // ── retry count বাড়াও ──
    suspend fun incrementRetry(actionId: String) {
        val queue = getAll().toMutableList()
        val idx   = queue.indexOfFirst { it.id == actionId }
        if (idx >= 0) {
            queue[idx] = queue[idx].copy(retryCount = queue[idx].retryCount + 1)
        }
        save(queue)
    }

    // ── 5+ বার fail হলে drop করো ──
    suspend fun dropFailed() {
        val queue = getAll().filter { it.retryCount < 5 }.toMutableList()
        save(queue)
    }

    suspend fun count(): Int = getAll().size

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY) }
    }

    private suspend fun save(queue: List<PendingAction>) {
        context.dataStore.edit { it[KEY] = gson.toJson(queue) }
    }
}
