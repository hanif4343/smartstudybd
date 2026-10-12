package com.hanif.smartstudy.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hanif.smartstudy.util.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Admin Sync ইতিহাস — কোন কোন action (Move / Delete / Rename / Edit ...) সফল হলো, কোনটা ব্যর্থ হলো
 * (এবং কেন), তার স্থায়ী লগ। Admin → Sync ট্যাবের "ইতিহাস" অংশে তারিখ-ফিল্টার সহ দেখানো হয়।
 *
 * - একই action (id) বারবার চেষ্টা হলে একটাই এন্ট্রি আপডেট হয় (attempts বাড়ে, শেষ status/কারণ থাকে)।
 * - সর্বোচ্চ [MAX_ENTRIES]টি এবং [MAX_AGE_DAYS] দিনের পুরনো এন্ট্রি আপনাআপনি মুছে যায়।
 */
data class SyncHistoryEntry(
    val id         : String = "",
    val actionType : String = "",
    val summary    : String = "",
    val sheet      : String = "",
    val status     : String = STATUS_FAILED,   // SUCCESS | FAILED
    val reason     : String? = "",
    val attempts   : Int    = 1,
    val createdAt  : Long   = 0L,              // action প্রথম যখন করা হয়েছিল
    val updatedAt  : Long   = 0L               // শেষ চেষ্টা/সফল হওয়ার সময়
) {
    companion object {
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED  = "FAILED"
    }
}

class SyncHistoryStore(private val context: Context) {

    private val gson = Gson()
    private val KEY  = stringPreferencesKey("admin_sync_history_json")
    private val type = object : TypeToken<MutableList<SyncHistoryEntry>>() {}.type

    companion object {
        private const val MAX_ENTRIES  = 500
        private const val MAX_AGE_DAYS = 90
        private val mutex = Mutex()

        /** action type → (লেবেল, ইমোজি) */
        fun typeLabel(type: String): String = when (type) {
            "admin_add_question"         -> "➕ নতুন প্রশ্ন"
            "admin_edit_question"        -> "✏️ এডিট"
            "admin_delete_question"      -> "🗑️ প্রশ্ন ডিলিট"
            "admin_delete_subject_topic" -> "🗑️ বিষয়/অধ্যায় ডিলিট"
            "admin_move_questions"       -> "📦 প্রশ্ন Move"
            "admin_move_topic"           -> "📦 অধ্যায় Move"
            "admin_rename"               -> "🏷️ Rename"
            "admin_qbank_move"           -> "📦 QBank Move/merge"
            "admin_qbank_delete"         -> "🗑️ QBank ডিলিট"
            "admin_reorder_subject", "admin_reorder_subtopic", "admin_set_topic_order",
            "admin_set_reference_order"  -> "↕️ ক্রম পরিবর্তন"
            else                         -> "🔄 অন্যান্য"
        }

        /** payload থেকে মানুষের পড়ার মতো সারাংশ */
        fun describe(action: PendingAction, gson: Gson = Gson()): Pair<String, String> {
            val payload: Map<*, *> = try { gson.fromJson(action.payload, Map::class.java) ?: emptyMap<String, Any>() }
                                     catch (e: Exception) { emptyMap<String, Any>() }
            val sheet = payload["sheet"]?.toString()
                ?: (payload["sheets"] as? List<*>)?.joinToString("+") ?: ""
            val preview = payload["questionPreview"]?.toString()?.ifBlank { null } ?: when (action.type) {
                "admin_move_questions" -> {
                    val ids = (payload["ids"] as? List<*>)?.size ?: 0
                    "${ids}টি প্রশ্ন → \"${payload["newSubject"] ?: ""}\" › \"${payload["newSubTopic"] ?: ""}\""
                }
                "admin_move_topic" -> {
                    val merge = payload["mergeTopicId"]?.toString()?.isNotBlank() == true
                    "\"${payload["newSubTopicName"] ?: ""}\" অধ্যায় → \"${payload["newSubjectName"] ?: ""}\"" + (if (merge) " (merge)" else "")
                }
                "admin_delete_subject_topic" -> {
                    val subj = payload["subject"]?.toString().orEmpty()
                    val subT = payload["subTopic"]?.toString().orEmpty()
                    val delSub = payload["deleteSubTopic"]?.toString()?.toBoolean() ?: false
                    if (delSub && subT.isNotBlank()) "\"$subj\" › \"$subT\" (পুরো অধ্যায়)" else "\"$subj\" (পুরো বিষয়)"
                }
                else -> ""
            }
            return sheet to preview
        }

        /** কাঁচা এরর বার্তা → সুন্দর বাংলা শিরোনাম (কাঁচা বার্তা আলাদা ছোট করে দেখানো হয়) */
        fun prettyReason(raw: String?): String {
            val r = (raw ?: "").trim()
            val l = r.lowercase()
            return when {
                r.isBlank() -> "কারণ জানা যায়নি — আবার চেষ্টা করা হবে"
                "unable to resolve host" in l || "no address associated" in l || "network is unreachable" in l ||
                    "failed to connect" in l || "ইন্টারনেট" in r || "offline" in l -> "📡 ইন্টারনেট সংযোগ নেই বা খুব দুর্বল ছিল"
                "timeout" in l || "timed out" in l -> "⏱️ সার্ভার সময়মতো সাড়া দেয়নি (টাইমআউট)"
                "401" in l || "403" in l || "unauthor" in l || "forbidden" in l || "permission" in l || "token" in l ->
                    "🔒 অনুমতি/টোকেন সমস্যা — আবার লগইন করে চেষ্টা করুন"
                "404" in l || "not found" in l || "পাওয়া যায়নি" in r -> "🔍 সার্ভারে আইটেমটি পাওয়া যায়নি (আগেই মুছে/বদলে গেছে হতে পারে)"
                "429" in l || "quota" in l || "rate" in l -> "🚦 অনেক বেশি রিকোয়েস্ট — একটু পরে আবার চেষ্টা হবে"
                "500" in l || "502" in l || "503" in l || "504" in l || "server" in l -> "🛠️ সার্ভারে সাময়িক সমস্যা"
                "কনফিগার" in r || "not configured" in l -> "⚙️ Google Sheet কনফিগার করা নেই"
                "তৈরি ব্যর্থ" in r -> "🆕 নতুন Subject/Topic তৈরি করা যায়নি"
                else -> "⚠️ সার্ভার কাজটি গ্রহণ করেনি"
            }
        }
    }

    private suspend fun load(): MutableList<SyncHistoryEntry> = try {
        val json = context.dataStore.data.first()[KEY]
        if (json.isNullOrBlank()) mutableListOf() else (gson.fromJson<MutableList<SyncHistoryEntry>>(json, type) ?: mutableListOf())
    } catch (e: Exception) { mutableListOf() }

    private suspend fun save(list: List<SyncHistoryEntry>) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_DAYS * 24L * 3600_000L
        val pruned = list.filter { it.updatedAt >= cutoff }.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
        context.dataStore.edit { it[KEY] = gson.toJson(pruned) }
    }

    suspend fun getAll(): List<SyncHistoryEntry> = mutex.withLock { load().sortedByDescending { it.updatedAt } }

    /** Pending action-এর ফলাফল (সফল/ব্যর্থ) — একই id থাকলে আপডেট */
    suspend fun recordFromAction(action: PendingAction, success: Boolean, reason: String? = null) {
        try {
            val (sheet, preview) = describe(action, gson)
            mutex.withLock {
                val list = load()
                val now = System.currentTimeMillis()
                val idx = list.indexOfFirst { it.id == action.id }
                val prev = list.getOrNull(idx)
                val entry = SyncHistoryEntry(
                    id = action.id, actionType = action.type, summary = preview, sheet = sheet,
                    status = if (success) SyncHistoryEntry.STATUS_SUCCESS else SyncHistoryEntry.STATUS_FAILED,
                    reason = if (success) "" else reason,
                    attempts = (prev?.attempts ?: 0) + 1,
                    createdAt = prev?.createdAt ?: action.createdAt,
                    updatedAt = now
                )
                if (idx >= 0) list[idx] = entry else list.add(entry)
                save(list)
            }
        } catch (_: Exception) { /* ইতিহাস লেখা ব্যর্থ হলে মূল sync যেন না থামে */ }
    }

    /** সরাসরি (queue ছাড়া) হওয়া action — যেমন অনলাইনে এক চোটেই সফল Move/Delete/Rename */
    suspend fun recordDirect(type: String, summary: String, sheet: String = "", success: Boolean, reason: String? = "") {
        try {
            mutex.withLock {
                val list = load()
                val now = System.currentTimeMillis()
                list.add(SyncHistoryEntry(
                    id = java.util.UUID.randomUUID().toString(), actionType = type, summary = summary, sheet = sheet,
                    status = if (success) SyncHistoryEntry.STATUS_SUCCESS else SyncHistoryEntry.STATUS_FAILED,
                    reason = if (success) "" else reason, attempts = 1, createdAt = now, updatedAt = now
                ))
                save(list)
            }
        } catch (_: Exception) { }
    }

    suspend fun clear() { mutex.withLock { context.dataStore.edit { it.remove(KEY) } } }
}
