package com.hanif.smartstudy.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.local.ContentCache
import com.hanif.smartstudy.data.local.PendingQueue
import com.hanif.smartstudy.data.remote.ContentFetchService
import com.hanif.smartstudy.data.remote.ContentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SyncWorker:
 * 1. Pending offline queue sync করে —
 *    - quiz_answer / xp_update / study_progress: সরাসরি Firebase-এ (এই তিনটা
 *      এখনো Firebase-native node, GAS/Sheet-এ নেই)
 *    - admin_edit_question / admin_add_question / admin_delete_question /
 *      admin_reorder_* / admin_delete_subject_topic / admin_move_*: সব
 *      Google Sheet (GAS)-এ, Firebase-এ কখনো না — কারণ Quiz/QBank/Study
 *      কনটেন্টের একমাত্র সোর্স-অফ-ট্রুথ এখন Sheet (FIX: আগে edit/add/delete
 *      সরাসরি Firebase-এ যেত, যেটা GAS/CDN থেকে disconnected ছিল এবং ডিলিট করা
 *      পুরনো Firebase node আবার "পুনরুজ্জীবিত" করে ফেলত)
 * 2. Content (Study/Quiz/QBank) refresh করে cache-এ
 */
class SyncWorker(
    context: Context,
    params : WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG   = "SyncWorker"
    private val queue = PendingQueue(context)
    private val cache = ContentCache(context)
    private val gson  = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // ইউজার ম্যানুয়ালি "অফলাইন মোড" অন করে রাখলে — WorkManager-এর নিজের
        // network constraint পাশ কাটিয়ে হলেও এই worker কোনো Firebase কল করবে না।
        // Pending queue অক্ষত থাকবে, offline mode বন্ধ হলে পরের sync-এ সব চলে যাবে।
        if (com.hanif.smartstudy.util.SessionManager(applicationContext).isOfflineMode()) {
            Log.d(TAG, "SyncWorker skipped — offline mode is manually ON")
            return@withContext Result.success()
        }

        Log.d(TAG, "SyncWorker started")
        var allSuccess = true

        // ── FIX (Speed Plan Task 1, one-time): fix-এর আগে জমে থাকা পুরনো
        // admin_add/edit/delete pending action (যেগুলো আগে সরাসরি Firebase-এ
        // লিখত) একবার purge করে দাও — এই flag ছাড়া প্রতিবার worker রান হলে
        // আবার purge চেষ্টা করতো, তাই legacyAdminQueuePurged দিয়ে একবারই। ──
        val prefs = applicationContext.getSharedPreferences("sync_worker_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("legacy_admin_queue_purged_v1", false)) {
            queue.purgeLegacyDirectFirebaseAdminActions()
            prefs.edit().putBoolean("legacy_admin_queue_purged_v1", true).apply()
            Log.i(TAG, "One-time purge: cleared legacy direct-Firebase admin actions from pending queue")
        }

        // ── 1. Pending queue sync ──
        val pending = queue.getAll()
        Log.d(TAG, "Pending actions: ${pending.size}")

        for (action in pending) {
            val ok = syncAction(action)
            if (ok) {
                queue.remove(action.id)
                Log.d(TAG, "Synced: ${action.type} id=${action.id}")
            } else {
                queue.incrementRetry(action.id)
                allSuccess = false
                Log.w(TAG, "Failed to sync: ${action.type} retry=${action.retryCount+1}")
            }
        }

        // 5+ বার fail হলে drop করো
        queue.dropFailed()

        // ── 2. Content refresh (শুধু সার্ভারে আসলেই নতুন কিছু থাকলে) ──
        // আগে শুধু TTL (1 ঘণ্টা) দেখেই পুরো Quiz+QBank+Study রিফেচ হতো — কনটেন্ট বদলাক
        // বা না বদলাক। এখন প্রথমে ছোট "/meta/updatedAt" চেক করা হয়; সেটা লাস্ট সেভ করা
        // remoteUpdatedAt এর চেয়ে নতুন হলে তবেই পুরো ডেটা টানা হয়। meta node না থাকলে
        // (পুরনো/আনসাপোর্টেড ডেটাবেস) TTL fallback ব্যবহার হয়, যাতে ডেটা কখনো একদম আটকে না থাকে।
        val cached = cache.loadContent()
        val remoteUpdatedAt = ContentFetchService.fetchMetaUpdatedAt(applicationContext)
        val needsRefresh = when {
            cached == null -> true
            remoteUpdatedAt > 0L -> remoteUpdatedAt > cached.remoteUpdatedAt
            else -> cached.isStale(FALLBACK_TTL_MILLIS)
        }

        if (needsRefresh) {
            if (cached == null) {
                // কখনো fetch হয়নি — এই একবারই পুরো ডাউনলোড লাগবে
                Log.d(TAG, "No cache yet — full fetch")
                when (val result = ContentFetchService.fetchAllContent(applicationContext)) {
                    is ContentResult.Success -> {
                        val toSave = result.data.copy(
                            remoteUpdatedAt = if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis()
                        )
                        cache.saveContent(toSave)
                        cache.markFullSyncDone(toSave.fetchedAt)
                        Log.d(TAG, "Content refreshed (full): Study=${toSave.study.size}")
                    }
                    is ContentResult.Error -> {
                        Log.w(TAG, "Content refresh failed: ${result.message}")
                        allSuccess = false
                    }
                }
            } else {
                // ── DELTA SYNC — শুধু "updatedAt > lastSync" এমন row গুলো আনো, পুরো ১০,০০০
                // প্রশ্ন না — এটাই ৬-ঘণ্টার periodic run এ bandwidth বাঁচানোর মূল অংশ ──
                val lastFullSync = cache.getLastFullSync()
                val now = System.currentTimeMillis()
                val needsFullResync = lastFullSync == 0L ||
                    (now - lastFullSync) > ContentCache.FULL_RESYNC_INTERVAL_MS

                if (needsFullResync) {
                    Log.d(TAG, "Periodic full resync due (deletion/edge-case reconcile)")
                    when (val result = ContentFetchService.fetchAllContent(applicationContext)) {
                        is ContentResult.Success -> {
                            val toSave = result.data.copy(
                                remoteUpdatedAt = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now
                            )
                            cache.saveContent(toSave)
                            cache.markFullSyncDone(toSave.fetchedAt)
                            Log.d(TAG, "Content refreshed (full resync): Study=${toSave.study.size}")
                        }
                        is ContentResult.Error -> {
                            Log.w(TAG, "Full resync failed: ${result.message}")
                            allSuccess = false
                        }
                    }
                } else {
                    val sinceQuiz  = (cache.getQuizLastSync()  - ContentCache.CLOCK_SKEW_BUFFER_MS).coerceAtLeast(1L)
                    val sinceQBank = (cache.getQBankLastSync() - ContentCache.CLOCK_SKEW_BUFFER_MS).coerceAtLeast(1L)
                    val sinceStudy = (cache.getStudyLastSync() - ContentCache.CLOCK_SKEW_BUFFER_MS).coerceAtLeast(1L)

                    when (val delta = ContentFetchService.fetchIncrementalContent(applicationContext, sinceQuiz, sinceQBank, sinceStudy)) {
                        is ContentResult.Success -> {
                            val d = delta.data
                            Log.d(TAG, "Delta sync: quiz+${d.quiz.size} qbank+${d.qbank.size} study+${d.study.size}")
                            val merged = cached.copy(
                                quiz          = mergeById(cached.quiz,  d.quiz)  { it.id },
                                qbank         = mergeById(cached.qbank, d.qbank) { it.id },
                                study         = mergeById(cached.study, d.study) { it.id },
                                subjectOrder  = d.subjectOrder,
                                subTopicOrder = d.subTopicOrder,
                                modelTests    = d.modelTests,
                                fetchedAt     = now,
                                remoteUpdatedAt = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now
                            )
                            cache.saveContent(merged)
                            cache.setSyncCheckpoints(now, now, now)
                        }
                        is ContentResult.Error -> {
                            Log.w(TAG, "Delta sync failed: ${delta.message}")
                            allSuccess = false
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Content unchanged on server, skipping refetch")
        }

        if (allSuccess) Result.success() else Result.retry()
    }

    /** existing list-এ changed/new item গুলো id দিয়ে merge করে — id মিললে replace, না মিললে যোগ */
    private fun <T> mergeById(existing: List<T>, changed: List<T>, idOf: (T) -> String?): List<T> {
        if (changed.isEmpty()) return existing
        val map = LinkedHashMap<String, T>()
        existing.forEach { item -> idOf(item)?.let { if (it.isNotBlank()) map[it] = item } }
        changed.forEach  { item -> idOf(item)?.let { if (it.isNotBlank()) map[it] = item } }
        return map.values.toList()
    }

    private suspend fun syncAction(action: com.hanif.smartstudy.data.local.PendingAction): Boolean {
        val payload = try {
            gson.fromJson(action.payload, Map::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "syncAction payload parse error: ${e.message}")
            return false
        }
        return when (action.type) {
            "quiz_answer"        -> syncQuizAnswer(payload)
            "xp_update"          -> syncXpUpdate(payload)
            "study_progress"     -> syncStudyProgress(payload)
            "admin_edit_question" -> syncAdminEdit(payload)
            "admin_add_question"  -> syncAdminAdd(payload)
            "admin_delete_question" -> syncAdminDelete(payload)
            "admin_reorder_subject" -> syncAdminReorderSubject(payload)
            "admin_reorder_subtopic" -> syncAdminReorderSubTopic(payload)
            "admin_delete_subject_topic" -> syncAdminDeleteSubjectTopic(payload)
            "admin_move_questions" -> syncAdminMoveQuestions(payload)
            "admin_move_topic" -> syncAdminMoveTopic(payload)
            else -> false
        }
    }

    // ── Quiz answer log — সরাসরি Firebase এ (GAS এর quizAnswer action এর বদলে) ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncQuizAnswer(payload: Map<*, *>): Boolean {
        return try {
            val phone      = payload["phone"]?.toString() ?: return false
            val questionId = payload["questionId"]?.toString() ?: ""
            val isCorrect  = payload["isCorrect"]?.toString() ?: "false"
            val safePhone  = phone.replace("+", "").trim()
            if (safePhone.isBlank()) return false

            val secret = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            val base   = BuildConfig.FIREBASE_URL.trimEnd('/')
            val url    = "$base/QuizAnswers/$safePhone.json?auth=$secret"

            val obj = com.google.gson.JsonObject().apply {
                addProperty("questionId", questionId)
                addProperty("isCorrect", isCorrect)
                addProperty("timestamp", System.currentTimeMillis())
            }
            val resp = client.newCall(
                Request.Builder().url(url)
                    .post(obj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            val ok = resp.isSuccessful
            resp.close()
            Log.d(TAG, "syncQuizAnswer $safePhone/$questionId → $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "syncQuizAnswer error: ${e.message}")
            false
        }
    }

    // ── XP update — সরাসরি Firebase Users/{phone}/XP পড়ে+লিখে (GAS এর updateXP এর বদলে) ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncXpUpdate(payload: Map<*, *>): Boolean {
        return try {
            val phone = payload["phone"]?.toString() ?: return false
            val delta = payload["xpDelta"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            val safePhone = phone.replace("+", "").trim()
            if (safePhone.isBlank() || delta == 0) return true

            val secret = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            val base   = BuildConfig.FIREBASE_URL.trimEnd('/')
            val getUrl = "$base/Users/$safePhone.json?auth=$secret"

            val curJson = client.newCall(Request.Builder().url(getUrl).get().build())
                .execute().body?.string()
            val currentXp = if (!curJson.isNullOrBlank() && curJson != "null") {
                try { org.json.JSONObject(curJson).optInt("XP", 0) } catch (e: Exception) { 0 }
            } else 0
            val newXp = maxOf(0, currentXp + delta)

            val patchObj = com.google.gson.JsonObject().apply { addProperty("XP", newXp) }
            val resp = client.newCall(
                Request.Builder().url(getUrl)
                    .patch(patchObj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            val ok = resp.isSuccessful
            resp.close()
            Log.d(TAG, "syncXpUpdate $safePhone: $currentXp + $delta = $newXp → $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "syncXpUpdate error: ${e.message}")
            false
        }
    }

    // ── Study progress log — সরাসরি Firebase এ (GAS এর studyProgress action এর বদলে) ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncStudyProgress(payload: Map<*, *>): Boolean {
        return try {
            val phone   = payload["phone"]?.toString() ?: return false
            val minutes = payload["minutes"]?.toString() ?: "0"
            val topic   = payload["topic"]?.toString() ?: ""
            val safePhone = phone.replace("+", "").trim()
            if (safePhone.isBlank()) return false

            val secret = com.hanif.smartstudy.data.remote.FirebaseTokenProvider.getToken()
            val base   = BuildConfig.FIREBASE_URL.trimEnd('/')
            val url    = "$base/StudyLog/$safePhone.json?auth=$secret"

            val obj = com.google.gson.JsonObject().apply {
                addProperty("minutes", minutes)
                addProperty("topic", topic)
                addProperty("timestamp", System.currentTimeMillis())
            }
            val resp = client.newCall(
                Request.Builder().url(url)
                    .post(obj.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            val ok = resp.isSuccessful
            resp.close()
            Log.d(TAG, "syncStudyProgress $safePhone: ${minutes}min/$topic → $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "syncStudyProgress error: ${e.message}")
            false
        }
    }

    // ── FIX (Speed Plan Task 1): আগে এটা সরাসরি Firebase-এ PATCH করত — কিন্তু
    //    Quiz/QBank/Study কনটেন্টের আসল সোর্স এখন Google Sheet (GAS), Firebase
    //    না। এর ফলে অফলাইনে/GAS-fail হলে করা edit পরে replay হওয়ার সময় সরাসরি
    //    Firebase-এ লেখা হতো — যেটা কখনো CDN/GAS-এ প্রতিফলিত হতো না, উল্টো ডিলিট
    //    করা পুরনো Firebase node আবার জ্যান্ত হয়ে যেত। এখন MenuViewModel-এর
    //    adminUpdateField()-এর মতোই GasContentService.updateFields() ব্যবহার
    //    করে — success হলে GAS নিজেই dirty-topic মার্ক করে CDN publish
    //    pipeline-এ পাঠিয়ে দেয়, তাই আলাদা করে touchMeta()/Firebase কল লাগে না। ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminEdit(payload: Map<*, *>): Boolean {
        return try {
            val sheet      = payload["sheet"]?.toString() ?: return false
            val questionId = payload["questionId"]?.toString() ?: return false
            val fields     = payload["fields"] as? Map<String, String> ?: return false
            if (questionId.isBlank() || fields.isEmpty()) return false

            when (val r = com.hanif.smartstudy.data.remote.GasContentService
                .updateFields(sheet, questionId, fields)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    Log.d(TAG, "syncAdminEdit (GAS) $sheet/$questionId → success")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminEdit (GAS) failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminEdit error: ${e.message}")
            false
        }
    }

    // ── FIX (Speed Plan Task 1): আগে এটা সরাসরি Firebase-এ POST করে নতুন row
    //    বানাত, যেটা GAS/CDN-এর সাথে সম্পূর্ণ disconnected — GAS-side dirty-topic
    //    ট্র্যাকিং কখনো জানতোই না নতুন প্রশ্ন যোগ হয়েছে, ফলে CDN-এ কখনো publish-ও
    //    হতো না, শুধু "ভুতুড়ে" Firebase row থেকে যেত। এখন MenuViewModel-এর মতোই
    //    GasContentService.addQuestion() ব্যবহার করে — GAS নিজেই sheet-এ row
    //    বানায়, dirty মার্ক করে, আর রিটার্ন করা আসল id দিয়ে লোকাল temp id
    //    (localId) রিপ্লেস হয়। ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminAdd(payload: Map<*, *>): Boolean {
        return try {
            val sheet   = payload["sheet"]?.toString() ?: return false
            val localId = payload["localId"]?.toString() ?: return false
            val fields  = payload["fields"] as? Map<String, String> ?: return false
            if (fields.isEmpty()) return false

            when (val r = com.hanif.smartstudy.data.remote.GasContentService
                .addQuestion(sheet, fields)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    val newId = r.data
                    if (newId.isNotBlank()) {
                        com.hanif.smartstudy.data.repository.ContentRepository(applicationContext)
                            .replaceLocalIdAndPersist(sheet, localId, newId)
                    }
                    Log.d(TAG, "syncAdminAdd (GAS) $sheet localId=$localId → newId=$newId")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminAdd (GAS) failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminAdd error: ${e.message}")
            false
        }
    }

    // ── FIX (Speed Plan Task 1): আগে এটা সরাসরি Firebase-এ DELETE করত, যেটা
    //    এখন-অব্যবহৃত Firebase Quiz/QBank/Study node-কে "পুনরুজ্জীবিত" করতে পারত
    //    (আসল সোর্স Google Sheet-এ কখনো ডিলিট হতোই না)। localId (কখনো GAS-এ
    //    সিঙ্কই হয়নি) হলে এখানে আসার আগেই PendingQueue.removePendingForQuestion()
    //    দিয়ে বাদ পড়ে যায়, তাই এই ফাংশন শুধু আসল sheet-row-এর জন্যই কল হয় —
    //    এখন MenuViewModel-এর adminDeleteRow()-এর মতোই GasContentService
    //    .deleteQuestion() ব্যবহার করে। ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminDelete(payload: Map<*, *>): Boolean {
        return try {
            val sheet      = payload["sheet"]?.toString() ?: return false
            val questionId = payload["questionId"]?.toString() ?: return false
            if (questionId.isBlank()) return false

            when (val r = com.hanif.smartstudy.data.remote.GasContentService
                .deleteQuestion(sheet, questionId)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    Log.d(TAG, "syncAdminDelete (GAS) $sheet/$questionId → success")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminDelete (GAS) failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminDelete error: ${e.message}")
            false
        }
    }

    // ── অফলাইনে/ব্যর্থ হওয়া Subject/SubTopic bulk delete — net আসলে ব্যাকগ্রাউন্ডে
    //    Google Sheet-এ সেই subject/subTopic-এর সব প্রশ্ন (দেখো
    //    GasContentService.deleteBySubjectOrTopic — deleteByIds-ভিত্তিক, তাই stale
    //    row-index এর ঝুঁকি নেই) এবং সম্ভব হলে (referenceIds রিজলভ করা থাকলে) Subjects/
    //    Topics reference ট্যাব থেকেও (deleteReferenceItem, id-ম্যাচ, নিরাপদ) মুছে দেয়।
    //    এই action-টা MenuViewModel.adminDeleteSubjectOrTopic-এর মতোই সরাসরি Sheet/GAS-এ
    //    লেখে (Firebase-এ না — এই অ্যাপ এখন Quiz/QBank/Study-এর জন্য Sheet-primary,
    //    দেখো MenuViewModel-এর "Phase 6 পূর্ণ কাটওভার" নোট) — অন্য syncAdmin*() ফাংশনগুলো
    //    (edit/add/delete single question) এখনো পুরনো Firebase পাথ ব্যবহার করে, এটা
    //    ইচ্ছাকৃতভাবে আলাদা রাখা হলো যাতে আসল লাইভ (অনলাইন) পাথের সাথে সামঞ্জস্যপূর্ণ থাকে। ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminDeleteSubjectTopic(payload: Map<*, *>): Boolean {
        return try {
            val sheets = (payload["sheets"] as? List<*>)?.map { it.toString() } ?: return false
            val subject = payload["subject"]?.toString() ?: return false
            val subTopic = payload["subTopic"]?.toString() ?: ""
            val deleteSubTopic = payload["deleteSubTopic"]?.toString()?.toBoolean() ?: false
            val referenceIds = (payload["referenceIds"] as? Map<*, *>)
                ?.entries?.associate { (k, v) -> k.toString() to v.toString() } ?: emptyMap()
            if (sheets.isEmpty() || subject.isBlank() || (deleteSubTopic && subTopic.isBlank())) return false

            when (val r = com.hanif.smartstudy.data.remote.GasContentService
                .deleteBySubjectOrTopic(sheets, subject, subTopic, deleteSubTopic)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    val refType = if (deleteSubTopic) "topics" else "subjects"
                    referenceIds.values.toSet().forEach { rid ->
                        if (rid.isNotBlank()) {
                            com.hanif.smartstudy.data.remote.GasContentService.deleteReferenceItem(refType, rid)
                        }
                    }
                    Log.i(TAG, "syncAdminDeleteSubjectTopic $sheets/$subject/$subTopic → success")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminDeleteSubjectTopic failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminDeleteSubjectTopic error: ${e.message}")
            false
        }
    }

    // ── অফলাইনে/ব্যর্থ হওয়া Question(s) Move ("Move to...", ফাইল ম্যানেজারের মতো) —
    //    net আসলে ব্যাকগ্রাউন্ডে Google Sheet-এ ওই id-গুলোর subject/sub_topic/
    //    subject_id/topic_id আপডেট করে দেয় (GAS action=moveQuestions, id/fbKey
    //    অপরিবর্তিত থাকে বলে bookmark/quiz-history/Exam_Appearances ভাঙে না) ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminMoveQuestions(payload: Map<*, *>): Boolean {
        return try {
            val sheet = payload["sheet"]?.toString() ?: return false
            val ids = (payload["ids"] as? List<*>)?.map { it.toString() } ?: return false
            val newSubject = payload["newSubject"]?.toString() ?: return false
            val newSubjectId = payload["newSubjectId"]?.toString() ?: return false
            val newSubTopic = payload["newSubTopic"]?.toString() ?: return false
            var newTopicId = payload["newTopicId"]?.toString() ?: ""
            val createIfMissing = payload["createIfMissing"]?.toString()?.toBoolean() ?: false
            if (ids.isEmpty()) return false

            // ── অফলাইনে "নতুন Topic যোগ করে Move" queue হয়ে থাকলে — retry-এর সময়
            // প্রথমে GAS addReferenceItem দিয়ে আসল topicId বানিয়ে নিতে হবে (offline
            // অবস্থায় শুধু অস্থায়ী লোকাল id ছিল, blank/local id দিয়ে move করা যাবে না) ──
            if (createIfMissing || newTopicId.isBlank() || newTopicId.startsWith("-local")) {
                when (val cr = com.hanif.smartstudy.data.remote.GasContentService
                    .addReferenceItem("topics", newSubTopic, newSubjectId)) {
                    is com.hanif.smartstudy.data.remote.ApiResult.Success -> newTopicId = cr.data
                    is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                        Log.w(TAG, "syncAdminMoveQuestions: addReferenceItem failed: ${cr.message}")
                        return false
                    }
                }
            }

            when (val r = com.hanif.smartstudy.data.remote.GasContentService
                .moveQuestions(sheet, ids, newSubject, newSubjectId, newSubTopic, newTopicId)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    Log.i(TAG, "syncAdminMoveQuestions $sheet/${ids.size}টি → $newSubject/$newSubTopic success")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminMoveQuestions failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminMoveQuestions error: ${e.message}")
            false
        }
    }

    // ── অফলাইনে/ব্যর্থ হওয়া Topic Move — net আসলে ব্যাকগ্রাউন্ডে Google Sheet-এ
    //    (GAS action=moveTopic) Topic-এর reference-রো + তার আন্ডারের সব প্রশ্ন অন্য
    //    Subject-এ move করে দেয় (mergeTopicId থাকলে destination-এর existing Topic-এর
    //    সাথে merge) ──
    private suspend fun syncAdminMoveTopic(payload: Map<*, *>): Boolean {
        return try {
            val topicId = payload["topicId"]?.toString() ?: return false
            val newSubjectId = payload["newSubjectId"]?.toString() ?: return false
            val newSubjectName = payload["newSubjectName"]?.toString() ?: return false
            val newSubTopicName = payload["newSubTopicName"]?.toString() ?: return false
            val mergeTopicId = payload["mergeTopicId"]?.toString()?.ifBlank { null }

            when (val r = com.hanif.smartstudy.data.remote.GasContentService
                .moveTopic(topicId, newSubjectId, newSubjectName, newSubTopicName, mergeTopicId)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    Log.i(TAG, "syncAdminMoveTopic $topicId → $newSubjectName success (${r.data}টি প্রশ্ন)")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminMoveTopic failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminMoveTopic error: ${e.message}")
            false
        }
    }

    // ── অফলাইনে/quota-fail এ করা Subject reorder — net আসলে ব্যাকগ্রাউন্ডে
    //    FirebaseDataService.adminSetSubjectOrderBulk দিয়েই পাঠায় (URL/encoding
    //    লজিক এখানে আলাদা করে লেখা হয়নি, একই ফাংশন আবার ব্যবহার হয়েছে যাতে দুই
    //    জায়গায় path/encoding আলাদা হয়ে অসামঞ্জস্য না হয়) — সফল হলে সেই
    //    ফাংশনের ভেতরেই touchMetaUpdatedAt() কল হয়ে যায়, তাই বাকি সব ডিভাইসও
    //    পরের sync-এ নতুন ক্রম পেয়ে যাবে। শেষে লোকাল cache-ও patch করে রাখি। ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminReorderSubject(payload: Map<*, *>): Boolean {
        return try {
            val mode     = payload["mode"]?.toString() ?: return false
            val tag      = payload["tag"]?.toString() ?: ""
            val orderRaw = payload["order"] as? Map<*, *> ?: return false
            val order = orderRaw.entries.associate { (k, v) ->
                k.toString() to (v?.toString()?.toDoubleOrNull()?.toInt() ?: 0)
            }
            if (order.isEmpty()) return true

            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService
                .adminSetSubjectOrderBulk(mode, tag, order)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(tag)
                    com.hanif.smartstudy.data.repository.ContentRepository(applicationContext)
                        .patchSubjectOrderAndPersist(mode, encodedTag, order)
                    Log.d(TAG, "syncAdminReorderSubject $mode/$tag → success")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminReorderSubject failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminReorderSubject error: ${e.message}")
            false
        }
    }

    // ── একই প্যাটার্নে SubTopic reorder — mode+tag+subject ভিত্তিক ──
    @Suppress("UNCHECKED_CAST")
    private suspend fun syncAdminReorderSubTopic(payload: Map<*, *>): Boolean {
        return try {
            val mode     = payload["mode"]?.toString() ?: return false
            val tag      = payload["tag"]?.toString() ?: ""
            val subject  = payload["subject"]?.toString() ?: return false
            val orderRaw = payload["order"] as? Map<*, *> ?: return false
            val order = orderRaw.entries.associate { (k, v) ->
                k.toString() to (v?.toString()?.toDoubleOrNull()?.toInt() ?: 0)
            }
            if (order.isEmpty()) return true

            when (val r = com.hanif.smartstudy.data.remote.FirebaseDataService
                .adminSetSubTopicOrderBulk(mode, tag, subject, order)) {
                is com.hanif.smartstudy.data.remote.ApiResult.Success -> {
                    val encodedTag = com.hanif.smartstudy.data.model.AppContent.normalizedTagForPath(tag)
                    com.hanif.smartstudy.data.repository.ContentRepository(applicationContext)
                        .patchSubTopicOrderAndPersist(mode, encodedTag, subject, order)
                    Log.d(TAG, "syncAdminReorderSubTopic $mode/$tag/$subject → success")
                    true
                }
                is com.hanif.smartstudy.data.remote.ApiResult.Error -> {
                    Log.w(TAG, "syncAdminReorderSubTopic failed: ${r.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncAdminReorderSubTopic error: ${e.message}")
            false
        }
    }

    companion object {
        const val WORK_NAME = "SmartStudySyncWork"

        // meta node না থাকলে (পুরনো ডেটাবেস) safety-net TTL — 12 ঘণ্টা।
        // আগে 1 ঘণ্টা ছিল, যেটা meta-check না থাকা অবস্থায় প্রতি ঘণ্টায় পুরো
        // Quiz+QBank+Study রিফেচ করাতো, কনটেন্ট বদলাক বা না বদলাক।
        private const val FALLBACK_TTL_MILLIS = 12 * 60 * 60 * 1000L

        // ── Internet আসলে একবার run ──
        fun scheduleOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        // ── প্রতি ৬ ঘণ্টায় periodic content refresh ──
        // আগে ১ ঘণ্টা ছিল, TTL-ও ১ ঘণ্টা — ফলে অ্যাপ চালু/ব্যাকগ্রাউন্ডে থাকলেই প্রতি ঘণ্টায়
        // পুরো Quiz+QBank+Study রিফেচ হতো, কনটেন্ট বদলাক বা না বদলাক। এখন meta/updatedAt
        // চেক থাকায় বেশিরভাগ রান-এ আসলে কিছুই ডাউনলোড হবে না (শুধু ছোট meta node চেক হবে),
        // তাই ৬ ঘণ্টায় নামিয়ে আনলেও bandwidth নষ্ট হবে না, বরং কমবে।
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            // FIX: আগে KEEP পলিসি ছিল — মানে যাদের ফোনে আগেই ১ ঘণ্টার periodic work
            // enqueue হয়ে গেছে, তাদের জন্য নতুন ৬-ঘণ্টার শিডিউল কখনো কার্যকর হতো না
            // (app update করলেও আগের schedule-ই থেকে যেত)। UPDATE পলিসি দিলে বিদ্যমান
            // request-এর constraints/interval নতুন করে বসে যায়।
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "${WORK_NAME}_periodic",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }
    }
}
