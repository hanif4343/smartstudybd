package com.hanif.smartstudy

import android.app.Application
import com.hanif.smartstudy.util.FcmHelper
import com.hanif.smartstudy.worker.SyncWorker

class SmartStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Phase 3: Periodic content sync + offline queue flush
        SyncWorker.schedulePeriodic(this)

        // FCM token collect করে Firebase RTDB এ save করো
        // User login থাকলেই token save হবে, না থাকলে login এর পরে save হবে
        FcmHelper.collectAndSave(this)
    }
}
