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
        try {
            val url = BuildConfig.FIREBASE_URL
            if (url.isNullOrBlank() || url.contains("%%") || !url.startsWith("https://")) {
                android.util.Log.w(TAG, "FIREBASE_URL invalid, using default instance")
                FirebaseDatabase.getInstance()
            } else {
                FirebaseDatabase.getInstance(url)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "FirebaseDatabase init: ${e.message}")
            FirebaseDatabase.getInstance()
        }
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
        questionIds   : List<String>,
        wagerXp       : Int = 0
    ): String? {
        return try {
            val id  = challengesRef.push().key ?: return null
            val now = System.currentTimeMillis()

            val participants = mutableMapOf<String, ChallengeParticipant>()
            participants[creator.phone!!.firebaseKey()] = ChallengeParticipant(
                phone  = creator.phone,
                name   = creator.displayName(),
                status = "ACCEPTED"
            )
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
                participants  = participants,
                wagerXp       = wagerXp
            )

            challengesRef.child(id).setValue(challengeToMap(challenge)).await()

            invitees.forEach { u ->
                writeInviteNotification(u.phone!!, ChallengeInvite(
                    challengeId   = id,
                    creatorName   = creator.displayName(),
                    creatorPhone  = creator.phone,
                    subject       = subject,
                    questionCount = questionCount,
                    timeLimitSec  = timeLimitSec,
                    createdAt     = now,
                    wagerXp       = wagerXp
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
                            createdAt     = m["createdAt"]?.toString()?.toLongOrNull()    ?: 0L,
                            isGhostMode   = m["isGhostMode"] as? Boolean ?: false,
                            wagerXp       = m["wagerXp"]?.toString()?.toIntOrNull() ?: 0
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
                "createdAt"     to invite.createdAt,
                "wagerXp"       to invite.wagerXp
            ))
    }

    @Suppress("UNCHECKED_CAST")
    private fun challengeFromMap(id: String, map: Map<String, Any>): Challenge {
        val participantsRaw = map["participants"] as? Map<String, Any> ?: emptyMap()
        val participants    = participantsRaw.mapValues { (_, v) ->
            val p = v as? Map<String, Any> ?: emptyMap()
            ChallengeParticipant(
                phone           = p["phone"]?.toString() ?: "",
                name            = p["name"]?.toString()  ?: "",
                status          = p["status"]?.toString() ?: "INVITED",
                currentQ        = p["currentQ"]?.toString()?.toIntOrNull() ?: 0,
                submittedAt     = p["submittedAt"]?.toString()?.toLongOrNull() ?: 0L,
                score           = p["score"]?.toString()?.toIntOrNull() ?: -1,
                correctIds      = (p["correctIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                usedFiftyFifty  = p["usedFiftyFifty"] as? Boolean ?: false,
                usedTimeFreeze  = p["usedTimeFreeze"] as? Boolean ?: false
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
            participants  = participants,
            isGhostMode   = map["isGhostMode"] as? Boolean ?: false,
            ghostLockedAt = map["ghostLockedAt"]?.toString()?.toLongOrNull() ?: 0L,
            wagerXp       = map["wagerXp"]?.toString()?.toIntOrNull() ?: 0
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
            "phone"          to p.phone,
            "name"           to p.name,
            "status"         to p.status,
            "currentQ"       to p.currentQ,
            "submittedAt"    to p.submittedAt,
            "score"          to p.score,
            "correctIds"     to p.correctIds,
            "usedFiftyFifty" to p.usedFiftyFifty,
            "usedTimeFreeze" to p.usedTimeFreeze
        )},
        "isGhostMode"   to c.isGhostMode,
        "ghostLockedAt" to c.ghostLockedAt,
        "wagerXp"       to c.wagerXp
    )

    // ────────────────────────────────────────────────────────
    // GHOST MODE
    // ────────────────────────────────────────────────────────

    // Ghost: creator একা পরীক্ষা দিয়ে score lock করে, তারপর invite পাঠায়
    suspend fun lockGhostChallenge(
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
                "participants/$key/submittedAt" to System.currentTimeMillis(),
                "status"                        to ChallengeStatus.GHOST_ACTIVE.name,
                "ghostLockedAt"                 to System.currentTimeMillis()
            )
            challengesRef.child(challengeId).updateChildren(updates).await()
            Log.d(TAG, "Ghost locked: $challengeId score=$score")
            true
        } catch (e: Exception) {
            Log.e(TAG, "lockGhostChallenge: ${e.message}")
            false
        }
    }

    // Ghost invite পাঠাও (creator score lock করার পরে)
    fun sendGhostInvite(toPhone: String, invite: ChallengeInvite) {
        db.getReference("Invites/${toPhone.firebaseKey()}/${invite.challengeId}")
            .setValue(mapOf(
                "challengeId"   to invite.challengeId,
                "creatorName"   to invite.creatorName,
                "creatorPhone"  to invite.creatorPhone,
                "subject"       to invite.subject,
                "questionCount" to invite.questionCount,
                "timeLimitSec"  to invite.timeLimitSec,
                "createdAt"     to invite.createdAt,
                "isGhostMode"   to true
            ))
    }

    // ────────────────────────────────────────────────────────
    // MATCH HISTORY
    // ────────────────────────────────────────────────────────

    // Challenge শেষে দুজনের ইতিহাসে record save করো
    suspend fun saveMatchHistory(
        myPhone      : String,
        myName       : String,
        opponent     : ChallengeParticipant,
        challengeId  : String,
        subject      : String,
        myScore      : Int,
        total        : Int,
        isGhostMode  : Boolean = false,
        wagerXp      : Int     = 0
    ) {
        try {
            val iWon     = myScore > opponent.score
            val xpChange = if (wagerXp > 0) if (iWon) wagerXp else -wagerXp else 0
            val now      = System.currentTimeMillis()
            val record   = mapOf(
                "challengeId"   to challengeId,
                "subject"       to subject,
                "myScore"       to myScore,
                "opponentScore" to opponent.score,
                "opponentName"  to opponent.name,
                "opponentPhone" to opponent.phone,
                "iWon"          to iWon,
                "total"         to total,
                "playedAt"      to now,
                "isGhostMode"   to isGhostMode,
                "wagerXp"       to wagerXp,
                "xpChange"      to xpChange
            )
            db.getReference("MatchHistory/${myPhone.firebaseKey()}/${opponent.phone.firebaseKey()}/$challengeId")
                .setValue(record).await()
        } catch (e: Exception) {
            Log.e(TAG, "saveMatchHistory: ${e.message}")
        }
    }

    // XP Wager — জিতলে XP যোগ, হারলে বাদ (Firebase RTDB transaction)
    suspend fun applyWagerXp(phone: String, delta: Int): Boolean {
        return try {
            val ref = usersRef.orderByChild("Phone").equalTo(phone).get().await()
                .children.firstOrNull()?.ref ?: return false
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(data: MutableData): Transaction.Result {
                    val current = data.child("XP").getValue(Int::class.java) ?: 0
                    val newXp   = maxOf(0, current + delta)   // XP কখনো negative হবে না
                    data.child("XP").value = newXp
                    return Transaction.success(data)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) Log.e(TAG, "XP transaction error: ${error.message}")
                }
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "applyWagerXp: ${e.message}")
            false
        }
    }

    // Lifeline ব্যবহার — Firebase তে mark করো
    suspend fun markLifelineUsed(
        challengeId : String,
        phone       : String,
        lifeline    : com.hanif.smartstudy.data.model.Lifeline
    ) {
        try {
            val key   = phone.firebaseKey()
            val field = when (lifeline) {
                com.hanif.smartstudy.data.model.Lifeline.FIFTY_FIFTY  -> "usedFiftyFifty"
                com.hanif.smartstudy.data.model.Lifeline.TIME_FREEZE  -> "usedTimeFreeze"
            }
            challengesRef.child(challengeId).child("participants/$key/$field")
                .setValue(true).await()
        } catch (e: Exception) {
            Log.e(TAG, "markLifelineUsed: ${e.message}")
        }
    }

    // নির্দিষ্ট opponent এর সাথে match history আনো
    suspend fun getMatchHistory(myPhone: String, opponentPhone: String): List<MatchRecord> {
        return try {
            val snap = withTimeout(10_000L) {
                db.getReference("MatchHistory/${myPhone.firebaseKey()}/${opponentPhone.firebaseKey()}")
                    .orderByChild("playedAt").limitToLast(20).get().await()
            }
            snap.children.mapNotNull { child ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val m = child.value as? Map<String, Any> ?: return@mapNotNull null
                    MatchRecord(
                        challengeId   = m["challengeId"]?.toString()   ?: "",
                        subject       = m["subject"]?.toString()        ?: "",
                        myScore       = m["myScore"]?.toString()?.toIntOrNull() ?: 0,
                        opponentScore = m["opponentScore"]?.toString()?.toIntOrNull() ?: 0,
                        opponentName  = m["opponentName"]?.toString()   ?: "",
                        opponentPhone = m["opponentPhone"]?.toString()  ?: "",
                        iWon          = m["iWon"] as? Boolean ?: false,
                        total         = m["total"]?.toString()?.toIntOrNull() ?: 0,
                        playedAt      = m["playedAt"]?.toString()?.toLongOrNull() ?: 0L,
                        isGhostMode   = m["isGhostMode"] as? Boolean ?: false,
                        wagerXp       = m["wagerXp"]?.toString()?.toIntOrNull() ?: 0,
                        xpChange      = m["xpChange"]?.toString()?.toIntOrNull() ?: 0
                    )
                } catch (_: Exception) { null }
            }.sortedByDescending { it.playedAt }
        } catch (e: Exception) {
            Log.e(TAG, "getMatchHistory: ${e.message}")
            emptyList()
        }
    }

    // সব opponent এর সাথে overall win/loss summary
    suspend fun getWinLossSummary(myPhone: String): Map<String, Pair<Int, Int>> {
        // return: opponentPhone -> Pair(wins, losses)
        return try {
            val snap = withTimeout(10_000L) {
                db.getReference("MatchHistory/${myPhone.firebaseKey()}").get().await()
            }
            val result = mutableMapOf<String, Pair<Int, Int>>()
            snap.children.forEach { opponentSnap ->
                var wins = 0; var losses = 0
                opponentSnap.children.forEach { matchSnap ->
                    @Suppress("UNCHECKED_CAST")
                    val m = matchSnap.value as? Map<String, Any> ?: return@forEach
                    if (m["iWon"] as? Boolean == true) wins++ else losses++
                }
                if (wins + losses > 0) {
                    val oppPhone = opponentSnap.key?.fromFirebaseKey() ?: return@forEach
                    result[oppPhone] = Pair(wins, losses)
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getWinLossSummary: ${e.message}")
            emptyMap()
        }
    }

    // Ghost Challenge তৈরি (isGhostMode=true দিয়ে)
    suspend fun createGhostChallenge(
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

            val participants = mutableMapOf<String, ChallengeParticipant>()
            participants[creator.phone!!.firebaseKey()] = ChallengeParticipant(
                phone  = creator.phone,
                name   = creator.displayName(),
                status = "ACCEPTED"
            )
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
                status        = ChallengeStatus.ACTIVE.name, // সরাসরি ACTIVE — creator একাই পরীক্ষা দেবে
                createdAt     = now,
                startedAt     = now,
                questionIds   = questionIds,
                participants  = participants,
                isGhostMode   = true
            )

            challengesRef.child(id).setValue(challengeToMap(challenge)).await()
            Log.d(TAG, "Ghost challenge created: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "createGhostChallenge: ${e.message}")
            null
        }
    }
}
