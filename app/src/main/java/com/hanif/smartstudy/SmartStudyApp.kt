package com.hanif.smartstudy

import android.app.Application
import com.hanif.smartstudy.data.remote.FirebaseTokenProvider
import com.hanif.smartstudy.util.FcmHelper
import com.hanif.smartstudy.worker.SyncWorker

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
    }
}
