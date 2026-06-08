package com.hanif.smartstudy

import android.app.Application
import com.hanif.smartstudy.data.remote.FirebaseTokenProvider
import com.hanif.smartstudy.util.FcmHelper
import com.hanif.smartstudy.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Firebase Anonymous Auth — app start এ sign in নিশ্চিত করো
        // এটা না করলে REST call এ token পাওয়া যাবে না
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
                }
                // Test device list — physical device হলে logcat থেকে ID নাও
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED.toString()))
                        .build()
                )
            } catch (e: Exception) {
                Log.e("AdMob", "Init failed: ${e.message}")
            }
        }
    }
}
