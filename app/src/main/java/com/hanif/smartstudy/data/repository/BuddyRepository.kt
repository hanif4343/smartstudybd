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

    // ── Study Nav Phase 6: Study Together ────────────────────

    private val buddyStudyingRef get() = db.getReference("BuddyStudying")

    suspend fun setStudying(myPhone: String, myName: String, subject: String, topic: String): Boolean {
        return try {
            buddyStudyingRef.child(myPhone.firebaseKey()).setValue(mapOf(
                "name" to myName, "subject" to subject, "topic" to topic,
                "at" to System.currentTimeMillis()
            )).await()
            true
        } catch (e: Exception) { Log.e(TAG, "setStudying: ${e.message}"); false }
    }

    suspend fun clearStudying(myPhone: String) {
        try { buddyStudyingRef.child(myPhone.firebaseKey()).removeValue().await() }
        catch (e: Exception) { Log.e(TAG, "clearStudying: ${e.message}") }
    }

    fun observeBuddyStudying(buddyPhone: String): Flow<BuddyStudying?> = callbackFlow {
        val ref = buddyStudyingRef.child(buddyPhone.firebaseKey())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.value as? Map<String, Any>
                trySend(map?.let {
                    BuddyStudying(
                        phone   = buddyPhone,
                        name    = it["name"] as? String ?: "",
                        subject = it["subject"] as? String ?: "",
                        topic   = it["topic"] as? String ?: "",
                        at      = (it["at"] as? Long) ?: (it["at"] as? Number)?.toLong() ?: 0L
                    )
                })
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeBuddyStudying: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** "এই lessonটা একসাথে পড়বি?" — বন্ধুর কাছে push + inbox; ট্যাপ করলে সরাসরি ওই Topic-এ খোলে */
    suspend fun sendStudyInvite(fromName: String, toPhone: String, subject: String, topic: String): Boolean {
        return try {
            val title = "🤝 একসাথে পড়বি?"
            val body  = "$fromName \"$topic\" ($subject) পড়ছে — তুইও জয়েন কর!"
            val phoneEncoded = toPhone.firebaseKey()
            val notifKey = "notif_${System.currentTimeMillis()}"
            db.getReference("Notifications/$phoneEncoded/$notifKey").setValue(mapOf(
                "title" to title, "body" to body, "type" to "study_together",
                "url" to "study", "subject" to subject, "topic" to topic,
                "read" to false, "time" to System.currentTimeMillis()
            )).await()
            val fcmToken = com.hanif.smartstudy.data.remote.FcmAdminService.fetchTokenForPhone(toPhone)
            if (!fcmToken.isNullOrBlank()) {
                com.hanif.smartstudy.data.remote.FcmAdminService.sendToToken(
                    token = fcmToken, title = title, body = body,
                    data  = mapOf("type" to "study_together", "url" to "study", "subject" to subject, "topic" to topic)
                )
            }
            true
        } catch (e: Exception) { Log.e(TAG, "sendStudyInvite: ${e.message}"); false }
    }

    // ── Study Buddy Phase B1: Knock + Buddy Streak ───────────

    private val knocksRef get() = db.getReference("BuddyKnocks")
    private val daysRef   get() = db.getReference("BuddyDays")

    suspend fun sendKnock(me: User, toPhone: String, kind: String, message: String): Boolean {
        return try {
            val myPhone = me.phone ?: return false
            val toKey = toPhone.firebaseKey()
            val id = knocksRef.child(toKey).push().key ?: return false
            knocksRef.child(toKey).child(id).setValue(mapOf(
                "id" to id, "fromPhone" to myPhone, "fromName" to me.displayName(),
                "kind" to kind, "message" to message, "at" to System.currentTimeMillis(),
                "status" to "PENDING", "reply" to "", "replyAt" to 0L
            )).await()
            val (title, body) = when (kind) {
                KnockKind.FOCUS -> "⏱ ${me.displayName()} একসাথে Focus করতে চাইছে" to message
                KnockKind.ASK   -> "❓ ${me.displayName()} একটা প্রশ্নের কৌশল জানতে চাইছে" to message
                else            -> "👋 ${me.displayName()} knock করেছে!" to message.ifBlank { "চলো আজ একটু পড়ি!" }
            }
            sendBuddyPush(toPhone, title, body, "buddy_knock")
            true
        } catch (e: Exception) { Log.e(TAG, "sendKnock: ${e.message}"); false }
    }

    /** কারো inbox observe করো (নিজেরটা → আসা knock; বন্ধুরটা → আমার পাঠানোগুলোর উত্তর) */
    fun observeKnocks(ownerPhone: String): Flow<List<BuddyKnock>> = callbackFlow {
        val ref = knocksRef.child(ownerPhone.firebaseKey()).limitToLast(30)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<BuddyKnock>()
                for (c in snapshot.children) {
                    @Suppress("UNCHECKED_CAST")
                    val m = c.value as? Map<String, Any> ?: continue
                    list.add(BuddyKnock(
                        id        = (m["id"] as? String) ?: (c.key ?: ""),
                        fromPhone = m["fromPhone"] as? String ?: "",
                        fromName  = m["fromName"] as? String ?: "",
                        kind      = m["kind"] as? String ?: KnockKind.KNOCK,
                        message   = m["message"] as? String ?: "",
                        at        = (m["at"] as? Number)?.toLong() ?: 0L,
                        status    = m["status"] as? String ?: "PENDING",
                        reply     = m["reply"] as? String ?: "",
                        replyAt   = (m["replyAt"] as? Number)?.toLong() ?: 0L
                    ))
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeKnocks: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun respondKnock(me: User, knock: BuddyKnock, status: String, reply: String): Boolean {
        return try {
            val myPhone = me.phone ?: return false
            knocksRef.child(myPhone.firebaseKey()).child(knock.id).updateChildren(mapOf(
                "status" to status, "reply" to reply, "replyAt" to System.currentTimeMillis()
            )).await()
            val (title, body) = when (status) {
                "ACCEPTED" -> "✅ ${me.displayName()} রাজি!" to "তোমার knock-এ ${me.displayName()} \"চল\" বলেছে।"
                "LATER"    -> "🕒 ${me.displayName()} পরে করবে" to "তোমার knock-এ ${me.displayName()} \"পরে\" বলেছে।"
                else       -> "💡 ${me.displayName()} কৌশল জানিয়েছে" to reply.take(120)
            }
            sendBuddyPush(knock.fromPhone, title, body, "buddy_knock_reply")
            true
        } catch (e: Exception) { Log.e(TAG, "respondKnock: ${e.message}"); false }
    }

    /** আজ কত মিনিট পড়েছি — Buddy Streak হিসাবের জন্য দিনভিত্তিক রেকর্ড (/BuddyDays/{key}/{yyyy-MM-dd}) */
    suspend fun recordDay(myPhone: String, day: String, minutes: Int) {
        try { daysRef.child(myPhone.firebaseKey()).child(day).setValue(minutes).await() }
        catch (e: Exception) { Log.e(TAG, "recordDay: ${e.message}") }
    }

    fun observeDays(phone: String): Flow<Map<String, Int>> = callbackFlow {
        val ref = daysRef.child(phone.firebaseKey()).limitToLast(35)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val out = HashMap<String, Int>()
                for (c in snapshot.children) {
                    val k = c.key ?: continue
                    out[k] = (c.value as? Number)?.toInt() ?: 0
                }
                trySend(out)
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeDays: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Study Buddy Phase B3: Shared Goal ─────────────────────

    private val goalsRef get() = db.getReference("BuddyGoals")

    fun pairKey(a: String, b: String): String = listOf(a.firebaseKey(), b.firebaseKey()).sorted().joinToString("__")

    suspend fun saveGoal(pk: String, g: BuddyGoal, toPhone: String, fromName: String): Boolean {
        return try {
            goalsRef.child(pk).removeValue().await()   // আগের goal + progress মুছে নতুন শুরু
            goalsRef.child(pk).child("goal").setValue(mapOf(
                "id" to g.id, "title" to g.title, "metric" to g.metric, "target" to g.target,
                "mode" to g.mode, "days" to g.days, "startMs" to g.startMs,
                "createdBy" to g.createdBy, "createdByName" to g.createdByName
            )).await()
            val kind = if (g.mode == GoalMode.RACE) "⚔️ Challenge" else "🤝 Team Goal"
            sendBuddyPush(toPhone, "$kind: ${g.title}",
                "$fromName নতুন লক্ষ্য দিয়েছে — ${g.target} ${g.unit}, ${g.days} দিনে। দেখো!", "buddy_goal")
            true
        } catch (e: Exception) { Log.e(TAG, "saveGoal: ${e.message}"); false }
    }

    suspend fun clearGoal(pk: String) {
        try { goalsRef.child(pk).removeValue().await() } catch (e: Exception) { Log.e(TAG, "clearGoal: ${e.message}") }
    }

    suspend fun updateGoalProgress(pk: String, myPhone: String, value: Int, reachedAt: Long) {
        try {
            val m = mutableMapOf<String, Any>("value" to value, "updatedAt" to System.currentTimeMillis())
            if (reachedAt > 0L) m["reachedAt"] = reachedAt
            goalsRef.child(pk).child("progress").child(myPhone.firebaseKey()).updateChildren(m).await()
        } catch (e: Exception) { Log.e(TAG, "updateGoalProgress: ${e.message}") }
    }

    /** goal + দুজনের progress — { phoneKey → (value, reachedAt) } */
    data class GoalNode(val goal: BuddyGoal?, val progress: Map<String, Pair<Int, Long>>)

    fun observeGoal(pk: String): Flow<GoalNode> = callbackFlow {
        val ref = goalsRef.child(pk)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val gm = snapshot.child("goal").value as? Map<String, Any>
                val goal = gm?.let {
                    BuddyGoal(
                        id = it["id"] as? String ?: "", title = it["title"] as? String ?: "",
                        metric = it["metric"] as? String ?: GoalMetric.QUESTIONS,
                        target = (it["target"] as? Number)?.toInt() ?: 100,
                        mode = it["mode"] as? String ?: GoalMode.TEAM,
                        days = (it["days"] as? Number)?.toInt() ?: 7,
                        startMs = (it["startMs"] as? Number)?.toLong() ?: 0L,
                        createdBy = it["createdBy"] as? String ?: "",
                        createdByName = it["createdByName"] as? String ?: ""
                    )
                }
                val prog = HashMap<String, Pair<Int, Long>>()
                for (c in snapshot.child("progress").children) {
                    val k = c.key ?: continue
                    prog[k] = ((c.child("value").value as? Number)?.toInt() ?: 0) to
                        ((c.child("reachedAt").value as? Number)?.toLong() ?: 0L)
                }
                trySend(GoalNode(goal, prog))
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeGoal: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Study Buddy Phase B4: Study Together (shared Focus) ───

    private val focusRef get() = db.getReference("BuddyFocus")

    suspend fun startFocus(pk: String, me: User, toPhone: String, minutes: Int): Long? {
        return try {
            val myPhone = me.phone ?: return null
            val now = System.currentTimeMillis()
            focusRef.child(pk).setValue(mapOf(
                "startMs" to now, "minutes" to minutes, "startedBy" to myPhone,
                "members" to mapOf(myPhone.firebaseKey() to mapOf("name" to me.displayName(), "joinedAt" to now, "leftAt" to 0L))
            )).await()
            sendBuddyPush(toPhone, "🟢 ${me.displayName()} Focus শুরু করেছে",
                "একসাথে $minutes মিনিট পড়বি? Study Buddy খুলে Join করো!", "buddy_focus")
            now
        } catch (e: Exception) { Log.e(TAG, "startFocus: ${e.message}"); null }
    }

    suspend fun joinFocus(pk: String, me: User): Boolean {
        return try {
            val k = (me.phone ?: return false).firebaseKey()
            focusRef.child(pk).child("members").child(k).setValue(
                mapOf("name" to me.displayName(), "joinedAt" to System.currentTimeMillis(), "leftAt" to 0L)
            ).await()
            true
        } catch (e: Exception) { Log.e(TAG, "joinFocus: ${e.message}"); false }
    }

    suspend fun leaveFocus(pk: String, myPhone: String) {
        try { focusRef.child(pk).child("members").child(myPhone.firebaseKey()).child("leftAt")
            .setValue(System.currentTimeMillis()).await() }
        catch (e: Exception) { Log.e(TAG, "leaveFocus: ${e.message}") }
    }

    fun observeFocus(pk: String): Flow<BuddyFocusSession?> = callbackFlow {
        val ref = focusRef.child(pk)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) { trySend(null); return }
                val members = snapshot.child("members").children.mapNotNull { c ->
                    val k = c.key ?: return@mapNotNull null
                    FocusMember(
                        phoneKey = k,
                        name     = c.child("name").value as? String ?: "",
                        joinedAt = (c.child("joinedAt").value as? Number)?.toLong() ?: 0L,
                        leftAt   = (c.child("leftAt").value as? Number)?.toLong() ?: 0L
                    )
                }
                trySend(BuddyFocusSession(
                    startMs   = (snapshot.child("startMs").value as? Number)?.toLong() ?: 0L,
                    minutes   = (snapshot.child("minutes").value as? Number)?.toInt() ?: 25,
                    startedBy = snapshot.child("startedBy").value as? String ?: "",
                    members   = members
                ))
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "observeFocus: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
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
        "lastNudgeAt" to p.lastNudgeAt,
        "streak"       to p.streak,
        "routineDone"  to p.routineDone,
        "routineTotal" to p.routineTotal,
        "quizAcc"      to p.quizAcc
    )

    @Suppress("UNCHECKED_CAST")
    private fun progressFromMap(phone: String, map: Map<String, Any>): BuddyProgress = BuddyProgress(
        phone       = map["phone"] as? String ?: phone,
        name        = map["name"] as? String ?: "",
        date        = map["date"] as? String ?: "",
        doneMinutes = (map["doneMinutes"] as? Long)?.toInt() ?: (map["doneMinutes"] as? Number)?.toInt() ?: 0,
        goalMinutes = (map["goalMinutes"] as? Long)?.toInt() ?: (map["goalMinutes"] as? Number)?.toInt() ?: 20,
        progressPct = (map["progressPct"] as? Long)?.toInt() ?: (map["progressPct"] as? Number)?.toInt() ?: 0,
        lastNudgeAt = (map["lastNudgeAt"] as? Long) ?: (map["lastNudgeAt"] as? Number)?.toLong() ?: 0L,
        streak       = (map["streak"] as? Number)?.toInt() ?: -1,
        routineDone  = (map["routineDone"] as? Number)?.toInt() ?: -1,
        routineTotal = (map["routineTotal"] as? Number)?.toInt() ?: -1,
        quizAcc      = (map["quizAcc"] as? Number)?.toInt() ?: -1
    )
}
