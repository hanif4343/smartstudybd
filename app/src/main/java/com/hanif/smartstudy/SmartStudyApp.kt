package com.hanif.smartstudy

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.firebase.database.FirebaseDatabase
import com.hanif.smartstudy.data.remote.FirebaseTokenProvider
import com.hanif.smartstudy.receiver.FocusReminderReceiver
import com.hanif.smartstudy.util.FcmHelper
import com.hanif.smartstudy.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartStudyApp : Application() {

    // ── ফোকাস মোড Part ৪: অ্যাপ ফোরগ্রাউন্ডে ভিজিবল আছে কিনা এই কাউন্টার দিয়ে
    // ট্র্যাক করা হয় (started activity সংখ্যা)। কোনো নতুন lifecycle library লাগেনি —
    // core Android এর registerActivityLifecycleCallbacks দিয়েই "Recents-এ থাকলেও
    // not visible" নির্ভরযোগ্যভাবে ধরা যায় (onStop হয়ে যায় ততক্ষণে)।
    // অ্যাপে বর্তমানে একটাই Activity (MainActivity) থাকলেও এই কাউন্টার প্যাটার্ন
    // ভবিষ্যতে একাধিক Activity হলেও ঠিকভাবে কাজ করবে। ──
    private var startedActivityCount = 0

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

        // Firebase Anonymous/Real Auth — app start এ sign in নিশ্চিত করো (আগে এটা
        // শুধু log করতো, কোনো sign-in-ই করতো না — DB secret fallback-এর আসল কারণ
        // ছিল এটাই, এখন আসলেই anonymous sign-in করে)
        CoroutineScope(Dispatchers.IO).launch {
            FirebaseTokenProvider.ensureSignedIn()
        }

        // Phase 3: Periodic content sync + offline queue flush
        SyncWorker.schedulePeriodic(this)

        // FCM token collect করে Firebase RTDB এ save করো
        FcmHelper.collectAndSave(this)

        // Text-to-Speech — Study content এ "শুনো" বাটনের জন্য আগে থেকেই init করে রাখা
        com.hanif.smartstudy.util.TtsManager.init(this)

        // Admin broadcast notification এর জন্য "all_users" topic এ subscribe করো —
        // তাহলে broadcast পাঠানোর সময় প্রতিটা user এর token আলাদাভাবে লুকআপ
        // করে পাঠাতে হয় না, একবারেই topic এ push করা যায়।
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic("all_users")
        } catch (e: Exception) {
            Log.w("SmartStudyApp", "Topic subscribe failed: ${e.message}")
        }

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

        registerFocusModeLifecycleTracker()
    }

    // ── ফোকাস মোড Part ৪: ব্যাকগ্রাউন্ড রিমাইন্ডার চেইন শুরু/বন্ধ করার হুক ──
    private fun registerFocusModeLifecycleTracker() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount == 1) {
                    // অ্যাপ ফোরগ্রাউন্ডে ফিরল — pending reminder alarm বাতিল করো
                    FocusReminderReceiver.cancel(this@SmartStudyApp)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    // অ্যাপ ব্যাকগ্রাউন্ডে গেল (Recents-এ থাকলেও) — ফোকাস মোড active
                    // থাকলেই শুধু রিপিটিং রিমাইন্ডার চেইন শুরু হবে
                    CoroutineScope(Dispatchers.IO).launch {
                        FocusReminderReceiver.startIfActive(this@SmartStudyApp)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
