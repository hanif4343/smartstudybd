package com.hanif.smartstudy

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.firebase.database.FirebaseDatabase
import com.hanif.smartstudy.data.remote.FirebaseTokenProvider
import com.hanif.smartstudy.util.FcmHelper
import com.hanif.smartstudy.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // ── Firebase Offline Persistence — প্রথমবার data load হলে device এ cache হবে।
        // পরের বার internet ছাড়াও instant দেখাবে, শুধু নতুন/পরিবর্তিত data download হবে।
        // NOTE: setPersistenceEnabled অবশ্যই FirebaseDatabase এর যেকোনো call এর আগে call করতে হবে।
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            Log.d("SmartStudyApp", "Firebase offline persistence enabled ✅")
        } catch (e: Exception) {
            Log.w("SmartStudyApp", "Persistence already enabled or failed: ${e.message}")
        }

        // ── Remote Logger — পুরো অ্যাপের log/crash Firebase এ জমা হবে ──
        val savedPhone = runCatching {
            com.hanif.smartstudy.util.SessionManager(this).getCurrentUser()?.phone
        }.getOrNull()
        com.hanif.smartstudy.util.RemoteLogger.init(this, savedPhone)

        // Firebase Anonymous Auth — app start এ sign in নিশ্চিত করো
        FirebaseTokenProvider.ensureSignedIn()

        // Phase 3: Periodic content sync + offline queue flush
        SyncWorker.schedulePeriodic(this)

        // FCM token collect করে Firebase RTDB এ save করো
        FcmHelper.collectAndSave(this)

        // ── AdMob initialize — background thread (UI block হয় না) ──
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MobileAds.initialize(this@SmartStudyApp) {
                    Log.d("AdMob", "SDK initialized")
                    com.hanif.smartstudy.ui.ads.AdInitTracker.isReady = true
                }
            } catch (e: Exception) {
                Log.e("AdMob", "Init failed: ${e.message}")
            }
        }
    }
}
