package com.hanif.smartstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.hanif.smartstudy.data.model.Achievement
import com.hanif.smartstudy.service.SmartStudyFirebaseService
import com.hanif.smartstudy.ui.navigation.SmartStudyNavGraph
import com.hanif.smartstudy.ui.shared.AchievementPopup
import com.hanif.smartstudy.ui.shared.OfflineBanner
import com.hanif.smartstudy.ui.shared.StreakPopup
import com.hanif.smartstudy.ui.theme.*
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hanif.smartstudy.receiver.ReminderReceiver
import com.hanif.smartstudy.ui.shared.ToastHost
import com.hanif.smartstudy.ui.shared.rememberToastState
import com.hanif.smartstudy.worker.NotificationPollWorker
import com.hanif.smartstudy.util.ConnectivityObserver
import com.hanif.smartstudy.util.DeepLinkAction
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.parseDeepLink

// ─────────────────────────────────────────────────────────────
//  MainActivity — Google Sign-In + theme + offline banner
// ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var session: SessionManager
    private var sessionStartMs = 0L

    // Google Sign-In
    private lateinit var googleSignInClient: GoogleSignInClient
    private var googleSignInCallback: ((email: String, name: String, photoUrl: String, idToken: String) -> Unit)? = null
    private var googleSignInErrorCallback: ((msg: String) -> Unit)? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email    = account.email    ?: ""
            val name     = account.displayName ?: ""
            val photoUrl = account.photoUrl?.toString() ?: ""
            val idToken  = account.idToken ?: ""
            Log.d("GoogleSignIn", "Success: $email")
            googleSignInCallback?.invoke(email, name, photoUrl, idToken)
        } catch (e: ApiException) {
            Log.e("GoogleSignIn", "Failed statusCode=${e.statusCode} msg=${e.message}")
            val reason = when (e.statusCode) {
                10   -> "Developer error (${e.statusCode}): SHA-1 বা OAuth Client ID ঠিক নেই"
                12500-> "Google Play Services আপডেট করো"
                12501-> "Sign-in বাতিল করা হয়েছে"
                7    -> "নেটওয়ার্ক সমস্যা"
                else -> "Google Sign-in ব্যর্থ (কোড: ${e.statusCode})"
            }
            googleSignInErrorCallback?.invoke(reason)
        } finally {
            googleSignInCallback      = null
            googleSignInErrorCallback = null
        }
    }

    // AuthScreen থেকে call হবে
    fun startGoogleSignIn(
        onSuccess: (email: String, name: String, photoUrl: String, idToken: String) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        googleSignInCallback      = onSuccess
        googleSignInErrorCallback = onError
        // signOut করে fresh account picker দেখাও
        // addOnCompleteListener main thread এ run করে, তাই launch সরাসরি কাজ করবে
        googleSignInClient.signOut().addOnCompleteListener(this) {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        session = SessionManager(this)

        // Google Sign-In client configure
        val webClientId = getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(webClientId)
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Notification channel create করো
        ReminderReceiver.createChannel(this)

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        // রিমাইন্ডার নির্ভরযোগ্য করতে: exact-alarm permission নিশ্চিত করো (Android 12+)
        // এটা না থাকলে SCHEDULE_EXACT_ALARM declare করা সত্ত্বেও alarm silently দেরিতে/বাদ পড়ে যেতে পারে,
        // বিশেষ করে vivo/Xiaomi/Oppo-র মতো aggressive battery-management OEM-এ।
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    Log.e("ExactAlarm", "Could not open exact alarm settings", e)
                }
            }
        }

        // ব্যাটারি অপ্টিমাইজেশন থেকে app-কে বাদ রাখার অনুরোধ — vivo (FuntouchOS/OriginOS) সহ
        // অনেক OEM background-এ app বন্ধ থাকলে নিজে থেকেই scheduled alarm cancel করে দেয়।
        // এটা exempt না করলে রিমাইন্ডার মাঝে মাঝে বাজবে না, যদিও কোড সঠিকভাবে alarm set করেছে।
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (e: Exception) {
                Log.e("BatteryOpt", "Could not request battery optimization exemption", e)
            }
        }

        NotificationPollWorker.schedule(this)

        val deepLink = intent?.parseDeepLink() ?: DeepLinkAction(DeepLinkAction.Type.NONE)

        setContent {
            val darkFlow  = remember { session.darkModeFlow() }
            val themeFlow = remember { session.themeColorFlow() }
            val scaleFlow = remember { session.fontScaleFlow() }
            val isDark    by darkFlow.collectAsState(initial = session.isDarkMode())
            val themeStr  by themeFlow.collectAsState(initial = session.getThemeColor())
            val uiScale   by scaleFlow.collectAsState(initial = session.getFontScale())
            val appTheme  = themeFromString(themeStr)

            val isOnline by ConnectivityObserver.observe(this@MainActivity)
                .collectAsState(initial = true)
            val pendingSync = remember { session.getPendingSyncCount() }

            var pendingAchievement by remember { mutableStateOf<Achievement?>(null) }
            var showStreak         by remember { mutableStateOf(false) }
            var streakCount        by remember { mutableStateOf(0) }

            SmartStudyTheme(darkTheme = isDark, appTheme = appTheme, uiScale = uiScale) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {

                        val toastState = rememberToastState()

                        SmartStudyNavGraph(
                            deepLink              = deepLink,
                            onAchievementUnlocked = { ach -> pendingAchievement = ach },
                            onStreakUpdated        = { streak ->
                                if (streak > 0) { streakCount = streak; showStreak = true }
                            }
                        )
                        ToastHost(state = toastState)

                        OfflineBanner(
                            isOffline        = !isOnline,
                            pendingSyncCount = pendingSync,
                            modifier         = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        )

                        AchievementPopup(
                            achievement = pendingAchievement,
                            onDismiss   = { pendingAchievement = null }
                        )
                    }
                }
            }

            if (showStreak) {
                StreakPopup(streak = streakCount, onDismiss = { showStreak = false })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sessionStartMs = System.currentTimeMillis()
        SmartStudyFirebaseService.updatePresence(this, true)
    }

    override fun onPause() {
        super.onPause()
        val sessionMin = ((System.currentTimeMillis() - sessionStartMs) / 60000).toInt()
        if (sessionMin > 0) {
            SmartStudyFirebaseService.recordAppSession(this, sessionMin)
            kotlinx.coroutines.runBlocking { session.recordSessionMinutes(sessionMin) }
        }
        SmartStudyFirebaseService.updatePresence(this, false)
        com.hanif.smartstudy.util.RemoteLogger.flush()
    }

    override fun onStop() {
        super.onStop()
        SmartStudyFirebaseService.updatePresence(this, false)
    }
}
