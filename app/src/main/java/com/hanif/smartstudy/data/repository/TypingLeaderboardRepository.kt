package com.hanif.smartstudy.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.firebaseKey
import kotlinx.coroutines.tasks.await

/**
 * টাইপিং লিডারবোর্ড — TypingRaceRepository.kt-এর একই প্যাটার্নে (একই Firebase RTDB
 * instance-init স্টাইল), কিন্তু আলাদা path (/TypingLeaderboard/) ব্যবহার করে।
 *
 * ⚠️ প্রাইভেসি নোট: এখানে ফোন-নাম্বার-কী + নাম গ্লোবালি readable (ঠিক যেভাবে
 * TypingRace/Challenge সিস্টেমেও আগে থেকেই আছে — নতুন কোনো প্রাইভেসি-মডেল নয়,
 * বিদ্যমান কনভেনশনই অনুসরণ করা হয়েছে)। শুধু bestWpm/accuracy/name/language লেখা
 * হয় — ফোন-নাম্বারটা শুধু কী (key) হিসেবে, আলাদা ভ্যালু-ফিল্ড হিসেবে না।
 *
 * ⚠️ Firebase quota নোট (main-branch merge): প্রথম ভার্সনে এখানে persistent
 * ValueEventListener (real-time observeTop Flow) ছিল — কিন্তু main-branch-এ ততদিনে
 * "Firebase usage limit"-এর কারণে RTDB ব্যবহার সচেতনভাবে অনেক কমানো হয়েছে (দেখো
 * SessionManager-এর KEY_CHALLENGES_ENABLED/KEY_BUDDY_ENABLED/KEY_TYPING_RACE_ENABLED
 * হোল্ড/আনহোল্ড টগল)। তাই এখানেও একই নীতি: persistent listener বাদ দিয়ে one-shot
 * fetch (get().await()), আর পুরো ফিচারটাই KEY_TYPING_LEADERBOARD_ENABLED টগলের
 * আওতায় (ডিফল্ট বন্ধ) — দেখো TypingModeSelectScreen.kt/SmartTypingScreen.kt।
 */
data class LeaderboardEntry(
    val phoneKey  : String = "",
    val name      : String = "অজানা",
    val bestWpm   : Int = 0,
    val accuracy  : Int = 0,
    val updatedAt : Long = 0L
)

class TypingLeaderboardRepository {

    companion object {
        private const val TAG = "TypingLeaderboardRepo"
    }

    private val db: FirebaseDatabase by lazy {
        try {
            val url = BuildConfig.FIREBASE_URL
            if (url.isNullOrBlank() || url.contains("%%") || !url.startsWith("https://")) {
                Log.w(TAG, "FIREBASE_URL invalid, using default instance")
                FirebaseDatabase.getInstance()
            } else {
                FirebaseDatabase.getInstance(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseDatabase init: ${e.message}")
            FirebaseDatabase.getInstance()
        }
    }

    private fun boardRef(language: String) = db.getReference("TypingLeaderboard").child(language)

    /** সেশন শেষে নতুন personal-best হলেই কল করো (প্রতি সেশনে না — নাহলে অপ্রয়োজনীয়
     *  write বাড়বে; caller decide করে কখন নতুন best হয়েছে) */
    suspend fun submitScore(language: String, phone: String, name: String, wpm: Int, accuracy: Int): Boolean {
        if (phone.isBlank()) return false
        return try {
            val key = phone.firebaseKey()
            val existing = boardRef(language).child(key).get().await()
            @Suppress("UNCHECKED_CAST")
            val existingMap = existing.value as? Map<String, Any>
            val existingBest = (existingMap?.get("bestWpm") as? Long)?.toInt() ?: 0
            if (wpm <= existingBest) return false   // শুধু নতুন best হলেই লেখা হয়

            boardRef(language).child(key).setValue(
                mapOf(
                    "phoneKey" to key, "name" to name, "bestWpm" to wpm,
                    "accuracy" to accuracy, "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "submitScore: ${e.message}")
            false
        }
    }

    /** টপ N — bestWpm অনুযায়ী descending (Firebase Query শুধু ascending সাপোর্ট করে,
     *  তাই orderByChild + limitToLast + client-side reverse)। one-shot read (get()) —
     *  persistent listener না, তাই স্ক্রিন খোলা থাকা অবস্থায় RTDB কানেকশন ধরে রাখে না।
     *  ইউজার pull-to-refresh বা স্ক্রিনে re-entry করলে আবার fetch হবে। */
    suspend fun fetchTop(language: String, limit: Int = 50): List<LeaderboardEntry> {
        return try {
            val snap = boardRef(language).orderByChild("bestWpm").limitToLast(limit).get().await()
            snap.children.mapNotNull { child ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val m = child.value as? Map<String, Any> ?: return@mapNotNull null
                    LeaderboardEntry(
                        phoneKey  = m["phoneKey"]?.toString() ?: child.key ?: "",
                        name      = m["name"]?.toString()?.takeIf { it.isNotBlank() } ?: "অজানা",
                        bestWpm   = (m["bestWpm"] as? Long)?.toInt() ?: 0,
                        accuracy  = (m["accuracy"] as? Long)?.toInt() ?: 0,
                        updatedAt = (m["updatedAt"] as? Long) ?: 0L
                    )
                } catch (e: Exception) { null }
            }.sortedByDescending { it.bestWpm }
        } catch (e: Exception) {
            Log.e(TAG, "fetchTop: ${e.message}")
            emptyList()
        }
    }
}
