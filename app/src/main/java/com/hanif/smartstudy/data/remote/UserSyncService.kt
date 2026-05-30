package com.hanif.smartstudy.data.remote

import com.google.firebase.database.FirebaseDatabase
import com.hanif.smartstudy.viewmodel.ActiveUser
import kotlinx.coroutines.tasks.await

object UserSyncService {

    /** Fetch presence data from Firebase RTDB — returns list of ActiveUser */
    suspend fun fetchActiveUsers(): List<ActiveUser> {
        return try {
            val snap = FirebaseDatabase.getInstance()
                .getReference("presence")
                .get().await()
            snap.children.mapNotNull { child ->
                val phone    = child.key ?: return@mapNotNull null
                val name     = child.child("name").getValue(String::class.java) ?: ""
                val isOnline = child.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                val fcmToken = child.child("fcmToken").getValue(String::class.java) ?: ""
                ActiveUser(phone = phone, name = name, isOnline = isOnline,
                           lastSeen = lastSeen, fcmToken = fcmToken)
            }.sortedByDescending { if (it.isOnline) Long.MAX_VALUE else it.lastSeen }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
