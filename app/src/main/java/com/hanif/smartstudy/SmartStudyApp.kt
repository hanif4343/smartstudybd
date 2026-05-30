package com.hanif.smartstudy

import android.app.Application
import com.hanif.smartstudy.worker.SyncWorker

class SmartStudyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Phase 3: Periodic content sync + offline queue flush
        SyncWorker.schedulePeriodic(this)
    }
}
