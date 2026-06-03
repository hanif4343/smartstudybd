package com.hanif.smartstudy.data.repository

import android.util.Log
import com.google.firebase.database.*
import com.hanif.smartstudy.BuildConfig
import com.hanif.smartstudy.data.model.*
import com.hanif.smartstudy.data.model.ChallengeStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

// ─────────────────────────────────────────────────────────
// ChallengeRepository — Firebase RTDB wrapper
// ─────────────────────────────────────────────────────────

class ChallengeRepository {

    companion object {
        private const val TAG = "ChallengeRepo"
    }

    private val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(BuildConfig.FIREBASE_URL)
    }

    private val challengesRef get() = db.getReference("Challenges")
    private val usersRef      get() = db.getReference("Users")

    // ── User search by phone ──────────────────────────────

    suspend fun findUserByPhone(phone: String): User? {
        return try {
            val snap = withTimeout(10_000L) {
                usersRef.orderByChild("Phone").equalTo(phone).get().await()
            }
            snap.children.firstOrNull()?.let { child ->
                @Suppress("UNCHECKED_CAST")
                val map = child.value as? Map<String, Any> ?: return@let null
                User.fromFirebaseMap(map)
            }
        } catch (e: Exception) {
            Log.e(TAG, "findUserByPhone: ${e.message}")
            null
        }
    }

    // ── Create challenge ──────────────────────────────────

    suspend fun createChallenge(
        creator       : User,
        invitees      : List<User>,
        subject       : String,
        subTopic      : String,
        questionCount : Int,
        timeLimitSec  : Int,
        questionIds   : List<String>
    ): String? {
        return try {
            val id  = challengesRef.push().key ?: return null
            val now = System.currentTimeMillis()

            // সব participants map করো
            val participants = mutableMapOf<String, ChallengeParticipant>()
            // Creator auto-ACCEPTED
            participants[creator.phone!!.firebaseKey()] = ChallengeParticipant(
                phone  = creator.phone,
                name   = creator.displayName(),
                status = "ACCEPTED"
            )
            // Invitees INVITED
            invitees.forEach { u ->
                participants[u.phone!!.firebaseKey()] = ChallengeParticipant(
                    phone  = u.phone,
                    name   = u.displayName(),
                    status = "INVITED"
                )
            }

            val challenge = Challenge(
                id            = id,
                creatorPhone  = creator.phone,
                creatorName   = creator.displayName(),
                subject       = subject,
                subTopic      = subTopic,
                questionCount = questionCount,
                timeLimitSec  = timeLimitSec,
                status        = ChallengeStatus.PENDING.name,
                createdAt     = now,
                questionIds   = questionIds,
                participants  = participants
            )

            challengesRef.child(id).setValue(challengeToMap(challenge)).await()

            // প্রতিটা invitee-র inbox এ notification লিখো
            invitees.forEach { u ->
                writeInviteNotification(u.phone!!, ChallengeInvite(
                    challengeId   = id,
                    creatorName   = creator.displayName(),
                    creatorPhone  = creator.phone,
                    subject       = subject,
                    questionCount = questionCount,
                    timeLimitSec  = timeLimitSec,
                    createdAt     = now
                ))
            }

            Log.d(TAG, "Challenge created: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "createChallenge: ${e.message}")
            null
        }
    }

    // ── Accept / Decline ─────────────────────────────────

    suspend fun respondToChallenge(
        challengeId : String,
        myPhone     : String,
        accept      : Boolean
    ): Boolean {
        return try {
            val key    = myPhone.firebaseKey()
            val status = if (accept) "ACCEPTED" else "DECLINED"
            val updates = mapOf<String, Any>(
                "participants/$key/status" to status
            )
            challengesRef.child(challengeId).updateChildren(updates).await()

            if (!accept) {
                // decline হলে challenge cancel
                challengesRef.child(challengeId)
                    .child("status").setValue(ChallengeStatus.CANCELLED.name).await()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "respond: ${e.message}")
            false
        }
    }

    // ── Start challenge (creator triggers when all accepted) ──

    suspend fun startChallenge(challengeId: String): Boolean {
        return try {
            val updates = mapOf<String, Any>(
                "status"    to ChallengeStatus.ACTIVE.name,
                "startedAt" to System.currentTimeMillis()
            )
            challengesRef.child(challengeId).updateChildren(updates).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "startChallenge: ${e.message}")
            false
        }
    }

    // ── Live progress update (current question index) ──

    suspend fun updateProgress(challengeId: String, myPhone: String, questionIndex: Int) {
        try {
            challengesRef.child(challengeId)
                .child("participants/${myPhone.firebaseKey()}/currentQ")
                .setValue(questionIndex).await()
        } catch (e: Exception) {
            Log.e(TAG, "updateProgress: ${e.message}")
        }
    }

    // ── Submit answers ────────────────────────────────────

    suspend fun submitChallenge(
        challengeId : String,
        myPhone     : String,
        score       : Int,
        correctIds  : List<String>
    ): Boolean {
        return try {
            val key = myPhone.firebaseKey()
            val updates = mapOf<String, Any>(
                "participants/$key/status"      to "SUBMITTED",
                "participants/$key/score"       to score,
                "participants/$key/correctIds"  to correctIds,
                "participants/$key/submittedAt" to System.currentTimeMillis()
            )
            challengesRef.child(challengeId).updateChildren(updates).await()
            Log.d(TAG, "Submitted: $challengeId score=$score")
            true
        } catch (e: Exception) {
            Log.e(TAG, "submitChallenge: ${e.message}")
            false
        }
    }

    // ── Listen to a challenge (realtime) ─────────────────

    fun observeChallenge(challengeId: String): Flow<Challenge?> = callbackFlow {
        val ref      = challengesRef.child(challengeId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val map = snap.value as? Map<String, Any>
                    trySend(map?.let { challengeFromMap(challengeId, it) })
                } catch (e: Exception) { trySend(null) }
            }
            override fun onCancelled(err: DatabaseError) { trySend(null) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Listen to my pending invites ──────────────────────

    fun observeMyInvites(myPhone: String): Flow<List<ChallengeInvite>> = callbackFlow {
        val ref = db.getReference("Invites/${myPhone.firebaseKey()}")
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val list = snap.children.mapNotNull { child ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val m = child.value as? Map<String, Any> ?: return@mapNotNull null
                        ChallengeInvite(
                            challengeId   = m["challengeId"]?.toString() ?: "",
                            creatorName   = m["creatorName"]?.toString()  ?: "",
                            creatorPhone  = m["creatorPhone"]?.toString() ?: "",
                            subject       = m["subject"]?.toString()      ?: "",
                            questionCount = m["questionCount"]?.toString()?.toIntOrNull() ?: 10,
                            timeLimitSec  = m["timeLimitSec"]?.toString()?.toIntOrNull()  ?: 600,
                            createdAt     = m["createdAt"]?.toString()?.toLongOrNull()    ?: 0L
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

    // ── Delete invite after responding ───────────────────

    suspend fun deleteInvite(myPhone: String, challengeId: String) {
        try {
            db.getReference("Invites/${myPhone.firebaseKey()}/$challengeId").removeValue().await()
        } catch (_: Exception) {}
    }

    // ── Get challenge by id (one time) ───────────────────

    suspend fun getChallenge(challengeId: String): Challenge? {
        return try {
            val snap = withTimeout(10_000L) {
                challengesRef.child(challengeId).get().await()
            }
            @Suppress("UNCHECKED_CAST")
            val map = snap.value as? Map<String, Any> ?: return null
            challengeFromMap(challengeId, map)
        } catch (e: Exception) {
            Log.e(TAG, "getChallenge: ${e.message}")
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────

    private fun writeInviteNotification(toPhone: String, invite: ChallengeInvite) {
        db.getReference("Invites/${toPhone.firebaseKey()}/${invite.challengeId}")
            .setValue(mapOf(
                "challengeId"   to invite.challengeId,
                "creatorName"   to invite.creatorName,
                "creatorPhone"  to invite.creatorPhone,
                "subject"       to invite.subject,
                "questionCount" to invite.questionCount,
                "timeLimitSec"  to invite.timeLimitSec,
                "createdAt"     to invite.createdAt
            ))
    }

    @Suppress("UNCHECKED_CAST")
    private fun challengeFromMap(id: String, map: Map<String, Any>): Challenge {
        val participantsRaw = map["participants"] as? Map<String, Any> ?: emptyMap()
        val participants    = participantsRaw.mapValues { (_, v) ->
            val p = v as? Map<String, Any> ?: emptyMap()
            ChallengeParticipant(
                phone       = p["phone"]?.toString() ?: "",
                name        = p["name"]?.toString()  ?: "",
                status      = p["status"]?.toString() ?: "INVITED",
                currentQ    = p["currentQ"]?.toString()?.toIntOrNull() ?: 0,
                submittedAt = p["submittedAt"]?.toString()?.toLongOrNull() ?: 0L,
                score       = p["score"]?.toString()?.toIntOrNull() ?: -1,
                correctIds  = (p["correctIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            )
        }
        return Challenge(
            id            = id,
            creatorPhone  = map["creatorPhone"]?.toString()  ?: "",
            creatorName   = map["creatorName"]?.toString()   ?: "",
            subject       = map["subject"]?.toString()       ?: "",
            subTopic      = map["subTopic"]?.toString()      ?: "",
            questionCount = map["questionCount"]?.toString()?.toIntOrNull() ?: 10,
            timeLimitSec  = map["timeLimitSec"]?.toString()?.toIntOrNull()  ?: 600,
            status        = map["status"]?.toString()        ?: ChallengeStatus.PENDING.name,
            createdAt     = map["createdAt"]?.toString()?.toLongOrNull()    ?: 0L,
            startedAt     = map["startedAt"]?.toString()?.toLongOrNull()    ?: 0L,
            questionIds   = (map["questionIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            participants  = participants
        )
    }

    private fun challengeToMap(c: Challenge): Map<String, Any> = mapOf(
        "creatorPhone"  to c.creatorPhone,
        "creatorName"   to c.creatorName,
        "subject"       to c.subject,
        "subTopic"      to c.subTopic,
        "questionCount" to c.questionCount,
        "timeLimitSec"  to c.timeLimitSec,
        "status"        to c.status,
        "createdAt"     to c.createdAt,
        "startedAt"     to c.startedAt,
        "questionIds"   to c.questionIds,
        "participants"  to c.participants.mapValues { (_, p) -> mapOf(
            "phone"       to p.phone,
            "name"        to p.name,
            "status"      to p.status,
            "currentQ"    to p.currentQ,
            "submittedAt" to p.submittedAt,
            "score"       to p.score,
            "correctIds"  to p.correctIds
        )}
    )
}
