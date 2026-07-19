package com.hanif.smartstudy.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * TypingRaceRepository — Firebase RTDB wrapper, ChallengeRepository.kt-এর একই
 * প্যাটার্নে (একই Firebase instance-init, একই callbackFlow realtime listening
 * স্টাইল) — কিন্তু আলাদা path (/TypingRaces/) ব্যবহার করে, existing Challenge
 * সিস্টেমকে স্পর্শ না করে। দেখো SmartStudyBD-টাইপিং-অডিট-ও-রোডম্যাপ.md সেকশন ১০।
 */
class TypingRaceRepository {

    companion object {
        private const val TAG = "TypingRaceRepo"
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

    private val racesRef get() = db.getReference("TypingRaces")

    // ── নতুন রেস তৈরি + বন্ধুকে invite ─────────────────────

    suspend fun createRace(
        creatorPhone: String,
        creatorName : String,
        inviteePhone: String,
        inviteeName : String,
        passage     : String,
        language    : String
    ): String? {
        return try {
            if (creatorPhone.isBlank() || inviteePhone.isBlank()) {
                Log.e(TAG, "createRace: missing phone")
                return null
            }
            val id  = racesRef.push().key ?: return null
            val now = System.currentTimeMillis()

            val participants = mapOf(
                creatorPhone.firebaseKey() to RaceParticipant(phone = creatorPhone, name = creatorName, status = "ACCEPTED"),
                inviteePhone.firebaseKey() to RaceParticipant(phone = inviteePhone, name = inviteeName, status = "INVITED")
            )
            val race = TypingRace(
                id = id, creatorPhone = creatorPhone, creatorName = creatorName,
                passage = passage, language = language, status = RaceStatus.PENDING.name,
                createdAt = now, participants = participants
            )
            racesRef.child(id).setValue(raceToMap(race)).await()

            writeInviteNotification(inviteePhone, RaceInvite(
                raceId = id, creatorName = creatorName, creatorPhone = creatorPhone,
                language = language, createdAt = now
            ))

            Log.d(TAG, "Race created: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "createRace: ${e.message}")
            null
        }
    }

    // ── Accept / Decline ──────────────────────────────────

    suspend fun respondToRace(raceId: String, myPhone: String, accept: Boolean): Boolean {
        return try {
            val key = myPhone.firebaseKey()
            racesRef.child(raceId).child("participants/$key/status")
                .setValue(if (accept) "ACCEPTED" else "DECLINED").await()
            if (!accept) {
                racesRef.child(raceId).child("status").setValue(RaceStatus.CANCELLED.name).await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "respondToRace: ${e.message}")
            false
        }
    }

    // ── Start (creator ট্রিগার করে, দুজনেই ACCEPTED হলে) ──

    suspend fun startRace(raceId: String): Boolean {
        return try {
            racesRef.child(raceId).updateChildren(mapOf(
                "status"    to RaceStatus.ACTIVE.name,
                "startedAt" to System.currentTimeMillis()
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "startRace: ${e.message}")
            false
        }
    }

    // ── Live progress আপডেট — প্রতিটা কী-প্রেসে না, প্রতি ~১-২ সেকেন্ডে কল করা উচিত
    // (throttle করা caller-এর দায়িত্ব — নাহলে Firebase-এ অতিরিক্ত write হবে) ──

    suspend fun updateRaceProgress(raceId: String, myPhone: String, progress: Int, liveWpm: Int) {
        try {
            val key = myPhone.firebaseKey()
            racesRef.child(raceId).updateChildren(mapOf(
                "participants/$key/status"   to "RACING",
                "participants/$key/progress" to progress,
                "participants/$key/liveWpm"  to liveWpm
            )).await()
        } catch (e: Exception) {
            Log.e(TAG, "updateRaceProgress: ${e.message}")
        }
    }

    // ── রেস শেষ ────────────────────────────────────────────

    suspend fun finishRace(raceId: String, myPhone: String, finalWpm: Int, accuracy: Int): Boolean {
        return try {
            val key = myPhone.firebaseKey()
            racesRef.child(raceId).updateChildren(mapOf(
                "participants/$key/status"     to "FINISHED",
                "participants/$key/finalWpm"   to finalWpm,
                "participants/$key/accuracy"   to accuracy,
                "participants/$key/finishedAt" to System.currentTimeMillis()
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "finishRace: ${e.message}")
            false
        }
    }

    // ── Realtime observe ───────────────────────────────────

    fun observeRace(raceId: String): Flow<TypingRace?> = callbackFlow {
        val ref = racesRef.child(raceId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val map = snap.value as? Map<String, Any>
                    trySend(map?.let { raceFromMap(raceId, it) })
                } catch (e: Exception) { trySend(null) }
            }
            override fun onCancelled(err: DatabaseError) { trySend(null) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeMyRaceInvites(myPhone: String): Flow<List<RaceInvite>> = callbackFlow {
        val ref = db.getReference("RaceInvites/${myPhone.firebaseKey()}")
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = snap.children.mapNotNull { child ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val m = child.value as? Map<String, Any> ?: return@mapNotNull null
                        RaceInvite(
                            raceId       = m["raceId"]?.toString() ?: "",
                            creatorName  = m["creatorName"]?.toString() ?: "",
                            creatorPhone = m["creatorPhone"]?.toString() ?: "",
                            language     = m["language"]?.toString() ?: "bn",
                            createdAt    = m["createdAt"]?.toString()?.toLongOrNull() ?: 0L
                        )
                    } catch (_: Exception) { null }
                }
                trySend(list)
            }
            override fun onCancelled(err: DatabaseError) { trySend(emptyList()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun deleteInvite(myPhone: String, raceId: String) {
        try { db.getReference("RaceInvites/${myPhone.firebaseKey()}/$raceId").removeValue().await() } catch (_: Exception) {}
    }

    suspend fun getRace(raceId: String): TypingRace? {
        return try {
            val snap = withTimeout(10_000L) { racesRef.child(raceId).get().await() }
            @Suppress("UNCHECKED_CAST")
            val map = snap.value as? Map<String, Any> ?: return null
            raceFromMap(raceId, map)
        } catch (e: Exception) {
            Log.e(TAG, "getRace: ${e.message}")
            null
        }
    }

    // ── Helpers ────────────────────────────────────────────

    private suspend fun writeInviteNotification(toPhone: String, invite: RaceInvite) {
        try {
            db.getReference("RaceInvites/${toPhone.firebaseKey()}/${invite.raceId}")
                .setValue(mapOf(
                    "raceId"       to invite.raceId,
                    "creatorName"  to invite.creatorName,
                    "creatorPhone" to invite.creatorPhone,
                    "language"     to invite.language,
                    "createdAt"    to invite.createdAt
                )).await()

            val title = "🏁 টাইপিং রেসের আমন্ত্রণ!"
            val body  = "${invite.creatorName} তোমাকে একটা টাইপিং রেসে চ্যালেঞ্জ করেছে। Accept করো!"
            val notifKey = "notif_${System.currentTimeMillis()}"
            db.getReference("Notifications/${toPhone.firebaseKey()}/$notifKey").setValue(
                mapOf(
                    "title" to title, "body" to body, "type" to "typing_race_invite",
                    "url" to "typing_race", "raceId" to invite.raceId,
                    "read" to false, "time" to System.currentTimeMillis()
                )
            ).await()

            val fcmToken = com.hanif.smartstudy.data.remote.FcmAdminService.fetchTokenForPhone(toPhone)
            if (!fcmToken.isNullOrBlank()) {
                com.hanif.smartstudy.data.remote.FcmAdminService.sendToToken(
                    token = fcmToken, title = title, body = body,
                    data = mapOf("type" to "typing_race_invite", "url" to "typing_race", "raceId" to invite.raceId)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeInviteNotification: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun raceFromMap(id: String, map: Map<String, Any>): TypingRace {
        val participantsRaw = map["participants"] as? Map<String, Any> ?: emptyMap()
        val participants = participantsRaw.mapValues { (_, v) ->
            val p = v as? Map<String, Any> ?: emptyMap()
            RaceParticipant(
                phone      = p["phone"]?.toString() ?: "",
                name       = p["name"]?.toString() ?: "",
                status     = p["status"]?.toString() ?: "INVITED",
                progress   = p["progress"]?.toString()?.toIntOrNull() ?: 0,
                liveWpm    = p["liveWpm"]?.toString()?.toIntOrNull() ?: 0,
                finishedAt = p["finishedAt"]?.toString()?.toLongOrNull() ?: 0L,
                finalWpm   = p["finalWpm"]?.toString()?.toIntOrNull() ?: 0,
                accuracy   = p["accuracy"]?.toString()?.toIntOrNull() ?: 100
            )
        }
        return TypingRace(
            id = id,
            creatorPhone = map["creatorPhone"]?.toString() ?: "",
            creatorName  = map["creatorName"]?.toString() ?: "",
            passage      = map["passage"]?.toString() ?: "",
            language     = map["language"]?.toString() ?: "bn",
            status       = map["status"]?.toString() ?: RaceStatus.PENDING.name,
            createdAt    = map["createdAt"]?.toString()?.toLongOrNull() ?: 0L,
            startedAt    = map["startedAt"]?.toString()?.toLongOrNull() ?: 0L,
            participants = participants
        )
    }

    private fun raceToMap(r: TypingRace): Map<String, Any> = mapOf(
        "creatorPhone" to r.creatorPhone,
        "creatorName"  to r.creatorName,
        "passage"      to r.passage,
        "language"     to r.language,
        "status"       to r.status,
        "createdAt"    to r.createdAt,
        "startedAt"    to r.startedAt,
        "participants" to r.participants.mapValues { (_, p) -> mapOf(
            "phone" to p.phone, "name" to p.name, "status" to p.status,
            "progress" to p.progress, "liveWpm" to p.liveWpm,
            "finishedAt" to p.finishedAt, "finalWpm" to p.finalWpm, "accuracy" to p.accuracy
        )}
    )
}
