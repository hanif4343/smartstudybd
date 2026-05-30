package com.hanif.smartstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hanif.smartstudy.data.model.Achievement
import com.hanif.smartstudy.service.SmartStudyFirebaseService
import com.hanif.smartstudy.ui.navigation.SmartStudyNavGraph
import com.hanif.smartstudy.ui.shared.AchievementPopup
import com.hanif.smartstudy.ui.shared.OfflineBanner
import com.hanif.smartstudy.ui.shared.StreakPopup
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.util.ConnectivityObserver
import com.hanif.smartstudy.util.DeepLinkAction
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.parseDeepLink

// ─────────────────────────────────────────────────────────────
//  MainActivity — theme, offline banner, achievement/streak popup
// ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var session: SessionManager
    private var sessionStartMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        session = SessionManager(this)

        // Parse deep link if launched from URL
        val deepLink = intent?.parseDeepLink() ?: DeepLinkAction(DeepLinkAction.Type.NONE)

        setContent {
            val darkFlow  = remember { session.darkModeFlow() }
            val themeFlow = remember { session.themeColorFlow() }
            val isDark    by darkFlow.collectAsState(initial = session.isDarkMode())
            val themeStr  by themeFlow.collectAsState(initial = session.getThemeColor())
            val appTheme  = themeFromString(themeStr)

            // Offline state
            val isOnline by ConnectivityObserver.observe(this@MainActivity)
                .collectAsState(initial = true)
            val pendingSync = remember { session.getPendingSyncCount() }

            // Achievement / streak popup state
            var pendingAchievement by remember { mutableStateOf<Achievement?>(null) }
            var showStreak         by remember { mutableStateOf(false) }
            var streakCount        by remember { mutableStateOf(0) }

            SmartStudyTheme(darkTheme = isDark, appTheme = appTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {

                        SmartStudyNavGraph(
                            deepLink            = deepLink,
                            onAchievementUnlocked = { ach -> pendingAchievement = ach },
                            onStreakUpdated       = { streak ->
                                if (streak > 0) { streakCount = streak; showStreak = true }
                            }
                        )

                        // Offline banner (bottom)
                        OfflineBanner(
                            isOffline        = !isOnline,
                            pendingSyncCount = pendingSync,
                            modifier         = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        )

                        // Achievement popup (top)
                        AchievementPopup(
                            achievement = pendingAchievement,
                            onDismiss   = { pendingAchievement = null }
                        )
                    }
                }
            }

            // Streak popup (Dialog — overlays everything)
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
    }

    override fun onStop() {
        super.onStop()
        SmartStudyFirebaseService.updatePresence(this, false)
    }
}
