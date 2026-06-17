package com.hanif.smartstudy.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────────────────
// BuddyRepository — Study Buddy ফিচারের জন্য Firebase RTDB wrapper
//
// Firebase paths:
//   /Buddies/{myKey}                 -> { buddyPhone, buddyName, since, active }
//   /BuddyRequests/{toKey}/{reqId}   -> BuddyRequest
//   /BuddyProgress/{myKey}           -> BuddyProgress (আজকের progress, দুজনেই read করতে পারবে)
// ─────────────────────────────────────────────────────────

class BuddyRepository {

    companion object {
        private const val TAG = "BuddyRepo"
    }

    private val db: FirebaseDatabase by lazy {
        try {
            val url = BuildConfig.FIREBASE_URL
            if (url.isNullOrBlank() || url.contains("%%") || !url.startsWith("https://")) {
                FirebaseDatabase.getInstance()
            } else {
                FirebaseDatabase.getInstance(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseDatabase init: ${e.message}")
            FirebaseDatabase.getInstance()
        }
    }

    private val buddiesRef         get() = db.getReference("Buddies")
    private val buddyRequestsRef   get() = db.getReference("BuddyRequests")
    private val buddyProgressRef   get() = db.getReference("BuddyProgress")

    // ── বন্ধু খুঁজো (ফোন নম্বর দিয়ে) ──────────────────────

    suspend fun findUserByPhone(phone: String): User? {
        return try {
            com.hanif.smartstudy.data.remote.UserSyncService.fetchUser(phone)
        } catch (e: Exception) {
            Log.e(TAG, "findUserByPhone: ${e.message}")
            null
        }
    }

    // ── বন্ধুর কাছে রিকোয়েস্ট পাঠাও ───────────────────────

    suspend fun sendBuddyRequest(me: User, toPhone: String): Boolean {
        return try {
            val myPhone = me.phone ?: return false
            if (toPhone == myPhone) return false

            val toKey = toPhone.firebaseKey()
            val id    = buddyRequestsRef.child(toKey).push().key ?: return false
            val request = BuddyRequest(
                id        = id,
                fromPhone = myPhone,
                fromName  = me.displayName(),
                toPhone   = toPhone,
                createdAt = System.currentTimeMillis(),
                status    = "PENDING"
            )
            buddyRequestsRef.child(toKey).child(id).setValue(requestToMap(request)).await()
            sendBuddyPush(
                toPhone = toPhone,
                title   = "🤝 নতুন Study Buddy রিকোয়েস্ট!",
                body    = "${me.displayName()} আপনাকে Study Buddy হওয়ার আমন্ত্রণ জানিয়েছে।",
                notifType = "buddy_request"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendBuddyRequest: ${e.message}")
            false
        }
    }

    // ── আমার পেন্ডিং রিকোয়েস্ট observe করো ────────────────

    fun observeMyRequests(myPhone: String): Flow<List<BuddyRequest>> = callbackFlow {
        val ref = buddyRequestsRef.child(myPhone.firebaseKey())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<BuddyRequest>()
                for (child in snapshot.children) {
                    @Suppress("UNCHECKED_CAST")
                    val map = child.value as? Map<String, Any> ?: continue
                    list.add(requestFromMap(child.key ?: "", map))
                }
                trySend(list.filter { it.status == "PENDING" })
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeMyRequests: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── রিকোয়েস্ট accept করো — দুজনের জন্য Buddy link তৈরি হবে ──

    suspend fun acceptBuddyRequest(me: User, request: BuddyRequest): Boolean {
        return try {
            val myPhone = me.phone ?: return false
            val now     = System.currentTimeMillis()

            val myLink = BuddyLink(
                buddyPhone = request.fromPhone,
                buddyName  = request.fromName,
                since      = now,
                active     = true
            )
            val theirLink = BuddyLink(
                buddyPhone = myPhone,
                buddyName  = me.displayName(),
                since      = now,
                active     = true
            )

            buddiesRef.child(myPhone.firebaseKey()).setValue(linkToMap(myLink)).await()
            buddiesRef.child(request.fromPhone.firebaseKey()).setValue(linkToMap(theirLink)).await()

            // রিকোয়েস্ট status আপডেট করো এবং পরিষ্কার করো
            buddyRequestsRef.child(myPhone.firebaseKey()).child(request.id).removeValue().await()

            sendBuddyPush(
                toPhone   = request.fromPhone,
                title     = "🎉 Study Buddy রিকোয়েস্ট গৃহীত হয়েছে!",
                body      = "${me.displayName()} আপনার Study Buddy হয়েছে। এখন একসাথে পড়াশোনা শুরু করো!",
                notifType = "buddy_accept"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "acceptBuddyRequest: ${e.message}")
            false
        }
    }

    // ── রিকোয়েস্ট decline করো ──────────────────────────────

    suspend fun declineBuddyRequest(myPhone: String, request: BuddyRequest): Boolean {
        return try {
            buddyRequestsRef.child(myPhone.firebaseKey()).child(request.id).removeValue().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "declineBuddyRequest: ${e.message}")
            false
        }
    }

    // ── বর্তমান Buddy link দেখো ────────────────────────────

    suspend fun getMyBuddy(myPhone: String): BuddyLink? {
        return try {
            val snap = buddiesRef.child(myPhone.firebaseKey()).get().await()
            @Suppress("UNCHECKED_CAST")
            val map = snap.value as? Map<String, Any> ?: return null
            linkFromMap(map).takeIf { it.active && it.buddyPhone.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "getMyBuddy: ${e.message}")
            null
        }
    }

    fun observeMyBuddy(myPhone: String): Flow<BuddyLink?> = callbackFlow {
        val ref = buddiesRef.child(myPhone.firebaseKey())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.value as? Map<String, Any>
                val link = map?.let { linkFromMap(it) }
                trySend(link?.takeIf { it.active && it.buddyPhone.isNotBlank() })
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeMyBuddy: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Buddy ভেঙে দাও (unfriend) ──────────────────────────

    suspend fun removeBuddy(myPhone: String, buddyPhone: String): Boolean {
        return try {
            buddiesRef.child(myPhone.firebaseKey()).removeValue().await()
            buddiesRef.child(buddyPhone.firebaseKey()).removeValue().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "removeBuddy: ${e.message}")
            false
        }
    }

    // ── আজকের progress আপলোড করো (দুজনেই দেখতে পারবে) ─────

    suspend fun updateMyProgress(progress: BuddyProgress): Boolean {
        return try {
            buddyProgressRef.child(progress.phone.firebaseKey()).setValue(progressToMap(progress)).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateMyProgress: ${e.message}")
            false
        }
    }

    // ── বন্ধুর আজকের progress observe করো ─────────────────

    fun observeBuddyProgress(buddyPhone: String): Flow<BuddyProgress?> = callbackFlow {
        val ref = buddyProgressRef.child(buddyPhone.firebaseKey())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.value as? Map<String, Any>
                trySend(map?.let { progressFromMap(buddyPhone, it) })
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeBuddyProgress: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── পার্টনারকে নাজ (Nudge) পাঠাও ───────────────────────

    suspend fun sendNudge(fromName: String, toPhone: String): Boolean {
        return try {
            sendBuddyPush(
                toPhone   = toPhone,
                title     = "👀 আপনার Study Buddy আপনাকে মনে করিয়ে দিচ্ছে!",
                body      = "$fromName আজকের পড়া শেষ করে ফেলেছে। আপনি কি পিছিয়ে থাকবেন? এখনই শুরু করো!",
                notifType = "buddy_nudge"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendNudge: ${e.message}")
            false
        }
    }

    // ── Helpers ───────────────────────────────────────────

    private suspend fun sendBuddyPush(toPhone: String, title: String, body: String, notifType: String) {
        try {
            val phoneEncoded = toPhone.firebaseKey()

            // Notifications fallback (poll worker picks this up)
            val notifKey = "notif_${System.currentTimeMillis()}"
            val notifMap = mapOf(
                "title" to title,
                "body"  to body,
                "type"  to notifType,
                "url"   to "menu/studybuddy",
                "read"  to false,
                "time"  to System.currentTimeMillis()
            )
            db.getReference("Notifications/$phoneEncoded/$notifKey").setValue(notifMap).await()

            // FCM push — সরাসরি token lookup + FCM v1 send (GAS নেই)
            val fcmToken = com.hanif.smartstudy.data.remote.FcmAdminService.fetchTokenForPhone(toPhone)
            if (!fcmToken.isNullOrBlank()) {
                com.hanif.smartstudy.data.remote.FcmAdminService.sendToToken(
                    token = fcmToken,
                    title = title,
                    body  = body,
                    data  = mapOf("type" to notifType, "url" to "menu/studybuddy")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendBuddyPush failed: ${e.message}")
        }
    }

    private fun requestToMap(r: BuddyRequest) = mapOf(
        "id"        to r.id,
        "fromPhone" to r.fromPhone,
        "fromName"  to r.fromName,
        "toPhone"   to r.toPhone,
        "createdAt" to r.createdAt,
        "status"    to r.status
    )

    @Suppress("UNCHECKED_CAST")
    private fun requestFromMap(id: String, map: Map<String, Any>): BuddyRequest = BuddyRequest(
        id        = (map["id"] as? String) ?: id,
        fromPhone = map["fromPhone"] as? String ?: "",
        fromName  = map["fromName"] as? String ?: "",
        toPhone   = map["toPhone"] as? String ?: "",
        createdAt = (map["createdAt"] as? Long) ?: (map["createdAt"] as? Number)?.toLong() ?: 0L,
        status    = map["status"] as? String ?: "PENDING"
    )

    private fun linkToMap(l: BuddyLink) = mapOf(
        "buddyPhone" to l.buddyPhone,
        "buddyName"  to l.buddyName,
        "since"      to l.since,
        "active"     to l.active
    )

    @Suppress("UNCHECKED_CAST")
    private fun linkFromMap(map: Map<String, Any>): BuddyLink = BuddyLink(
        buddyPhone = map["buddyPhone"] as? String ?: "",
        buddyName  = map["buddyName"] as? String ?: "",
        since      = (map["since"] as? Long) ?: (map["since"] as? Number)?.toLong() ?: 0L,
        active     = map["active"] as? Boolean ?: false
    )

    private fun progressToMap(p: BuddyProgress) = mapOf(
        "phone"       to p.phone,
        "name"        to p.name,
        "date"        to p.date,
        "doneMinutes" to p.doneMinutes,
        "goalMinutes" to p.goalMinutes,
        "progressPct" to p.progressPct,
        "lastNudgeAt" to p.lastNudgeAt
    )

    @Suppress("UNCHECKED_CAST")
    private fun progressFromMap(phone: String, map: Map<String, Any>): BuddyProgress = BuddyProgress(
        phone       = map["phone"] as? String ?: phone,
        name        = map["name"] as? String ?: "",
        date        = map["date"] as? String ?: "",
        doneMinutes = (map["doneMinutes"] as? Long)?.toInt() ?: (map["doneMinutes"] as? Number)?.toInt() ?: 0,
        goalMinutes = (map["goalMinutes"] as? Long)?.toInt() ?: (map["goalMinutes"] as? Number)?.toInt() ?: 20,
        progressPct = (map["progressPct"] as? Long)?.toInt() ?: (map["progressPct"] as? Number)?.toInt() ?: 0,
        lastNudgeAt = (map["lastNudgeAt"] as? Long) ?: (map["lastNudgeAt"] as? Number)?.toLong() ?: 0L
    )
}
