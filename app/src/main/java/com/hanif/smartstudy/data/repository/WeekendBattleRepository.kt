package com.hanif.smartstudy.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

// ─────────────────────────────────────────────────────────
// WeekendBattleRepository — Firebase RTDB wrapper
// Path: WeekendBattles/{battleId}/...
//       WeekendBattles/{battleId}/entries/{phoneKey}/...
// ─────────────────────────────────────────────────────────

class WeekendBattleRepository {

    companion object {
        private const val TAG  = "WeekendBattleRepo"
        private const val ROOT = "WeekendBattles"
    }

    private val db: FirebaseDatabase by lazy {
        try {
            val url = BuildConfig.FIREBASE_URL
            // placeholder মান বা invalid URL হলে default instance নাও
            if (url.isNullOrBlank() || url.contains("%%") || !url.startsWith("https://")) {
                android.util.Log.w(TAG, "FIREBASE_URL invalid ($url), using default instance")
                FirebaseDatabase.getInstance()
            } else {
                FirebaseDatabase.getInstance(url)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "FirebaseDatabase init error: ${e.message}")
            FirebaseDatabase.getInstance()
        }
    }

    private val ref get() = db.getReference(ROOT)

    // ── Active / Upcoming battle আনো ────────────────────

    suspend fun getActiveBattle(): WeekendBattle? {
        return try {
            val now  = System.currentTimeMillis()
            val snap = withTimeout(10_000L) {
                ref.orderByChild("endsAt").startAt(now.toDouble()).limitToFirst(1).get().await()
            }
            snap.children.firstOrNull()?.let { battleFromSnap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "getActiveBattle: ${e.message}")
            null
        }
    }

    suspend fun getUpcomingBattle(): WeekendBattle? {
        return try {
            val now  = System.currentTimeMillis()
            val snap = withTimeout(10_000L) {
                ref.orderByChild("startsAt").startAt(now.toDouble()).limitToFirst(2).get().await()
            }
            // প্রথমটা active হতে পারে, দ্বিতীয়টা upcoming
            snap.children.mapNotNull { battleFromSnap(it) }
                .firstOrNull { it.startsAt > now }
        } catch (e: Exception) {
            Log.e(TAG, "getUpcomingBattle: ${e.message}")
            null
        }
    }

    // ── Real-time leaderboard observe ───────────────────

    fun observeLeaderboard(battleId: String): Flow<List<BattleEntry>> = callbackFlow {
        val listener = ref.child("$battleId/entries")
            .orderByChild("score")
            .limitToLast(50)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val list = snap.children.mapNotNull { entryFromSnap(it) }
                        .sortedWith(compareByDescending<BattleEntry> { it.score }
                            .thenBy { it.timeTakenSec })
                    trySend(list)
                }
                override fun onCancelled(e: DatabaseError) {
                    Log.e(TAG, "observeLeaderboard: ${e.message}")
                }
            })
        awaitClose { ref.child("$battleId/entries").removeEventListener(listener) }
    }

    // ── আমার entry আছে কিনা দেখো ───────────────────────

    suspend fun getMyEntry(battleId: String, phone: String): BattleEntry? {
        return try {
            val snap = withTimeout(8_000L) {
                ref.child("$battleId/entries/${phone.firebaseKey()}").get().await()
            }
            if (snap.exists()) entryFromSnap(snap) else null
        } catch (e: Exception) {
            Log.e(TAG, "getMyEntry: ${e.message}")
            null
        }
    }

    // ── Battle questions আনো ────────────────────────────

    suspend fun getBattleQuestions(
        battleId : String,
        allQ     : List<QuestionItem>
    ): List<QuestionItem> {
        return try {
            val snap = withTimeout(8_000L) {
                ref.child("$battleId/questionIds").get().await()
            }
            val ids = (snap.value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            ids.mapNotNull { id -> allQ.find { it.id == id } }
        } catch (e: Exception) {
            Log.e(TAG, "getBattleQuestions: ${e.message}")
            emptyList()
        }
    }

    // ── Submit করো ──────────────────────────────────────

    suspend fun submitBattleEntry(
        battleId     : String,
        phone        : String,
        name         : String,
        score        : Int,
        total        : Int,
        timeTakenSec : Int,
        xpEarned     : Int
    ): Boolean {
        return try {
            val now      = System.currentTimeMillis()
            val accuracy = if (total > 0) score * 100f / total else 0f
            val entry    = mapOf(
                "phone"        to phone,
                "name"         to name,
                "score"        to score,
                "total"        to total,
                "accuracy"     to accuracy,
                "timeTakenSec" to timeTakenSec,
                "submittedAt"  to now,
                "xpEarned"     to xpEarned
            )
            ref.child("$battleId/entries/${phone.firebaseKey()}").setValue(entry).await()
            // participant count বাড়াও (best-effort)
            ref.child("$battleId/participantCount").runTransaction(object : Transaction.Handler {
                override fun doTransaction(d: MutableData): Transaction.Result {
                    d.value = (d.getValue(Int::class.java) ?: 0) + 1
                    return Transaction.success(d)
                }
                override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {}
            })
            Log.d(TAG, "Battle entry submitted: $battleId score=$score")
            true
        } catch (e: Exception) {
            Log.e(TAG, "submitBattleEntry: ${e.message}")
            false
        }
    }

    // ── XP award করো (top 3 পাবে) ────────────────────

    suspend fun awardBattleXp(
        phone    : String,
        xpDelta  : Int
    ) {
        try {
            val usersRef = db.getReference("Users")
            val snap     = usersRef.orderByChild("Phone").equalTo(phone).get().await()
            snap.children.firstOrNull()?.ref?.runTransaction(object : Transaction.Handler {
                override fun doTransaction(d: MutableData): Transaction.Result {
                    val cur = d.child("XP").getValue(Int::class.java) ?: 0
                    d.child("XP").value = cur + xpDelta
                    return Transaction.success(d)
                }
                override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "awardBattleXp: ${e.message}")
        }
    }

    // ── Helpers ─────────────────────────────────────────

    private fun battleFromSnap(snap: DataSnapshot): WeekendBattle? {
        return try {
            if (!snap.exists()) return null
            @Suppress("UNCHECKED_CAST")
            val m = snap.value as? Map<String, Any> ?: return null
            WeekendBattle(
                id               = snap.key ?: return null,
                title            = m["title"]?.toString()   ?: "সাপ্তাহিক চ্যাম্পিয়নশিপ",
                subject          = m["subject"]?.toString() ?: "",
                // Firebase questionIds List বা Map হিসেবে আসতে পারে
                questionIds      = when (val qi = m["questionIds"]) {
                                       is List<*>  -> qi.mapNotNull { it?.toString() }
                                       is Map<*, *> -> qi.values.mapNotNull { it?.toString() }
                                       else        -> emptyList()
                                   },
                questionCount    = m["questionCount"]?.toString()?.toIntOrNull()    ?: 20,
                timeLimitSec     = m["timeLimitSec"]?.toString()?.toIntOrNull()     ?: 1200,
                startsAt         = m["startsAt"]?.toString()?.toLongOrNull()        ?: 0L,
                endsAt           = m["endsAt"]?.toString()?.toLongOrNull()          ?: 0L,
                status           = m["status"]?.toString()                          ?: BattleStatus.UPCOMING.name,
                xpReward         = m["xpReward"]?.toString()?.toIntOrNull()         ?: 500,
                participantCount = m["participantCount"]?.toString()?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("WeekendBattleRepo", "battleFromSnap error: ${e.message}")
            null
        }
    }

    private fun entryFromSnap(snap: DataSnapshot): BattleEntry? {
        return try {
            if (!snap.exists()) return null
            @Suppress("UNCHECKED_CAST")
            val m = snap.value as? Map<String, Any> ?: return null
            BattleEntry(
                phone        = m["phone"]?.toString()        ?: snap.key?.replace("_", ".") ?: "",
                name         = m["name"]?.toString()         ?: "অজানা",
                score        = m["score"]?.toString()?.toIntOrNull()        ?: 0,
                total        = m["total"]?.toString()?.toIntOrNull()        ?: 0,
                accuracy     = m["accuracy"]?.toString()?.toFloatOrNull()   ?: 0f,
                timeTakenSec = m["timeTakenSec"]?.toString()?.toIntOrNull() ?: 0,
                submittedAt  = m["submittedAt"]?.toString()?.toLongOrNull() ?: 0L,
                xpEarned     = m["xpEarned"]?.toString()?.toIntOrNull()     ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("WeekendBattleRepo", "entryFromSnap error: ${e.message}")
            null
        }
    }
}
