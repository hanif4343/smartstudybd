package com.hanif.smartstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hanif.smartstudy.service.SmartStudyFirebaseService
import com.hanif.smartstudy.ui.navigation.SmartStudyNavGraph
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.util.SessionManager

// ─────────────────────────────────────────────────────────────
//  MainActivity — theme-aware entry point
//  Presence tracking + session time recording
// ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var session: SessionManager
    private var sessionStartMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        session = SessionManager(this)

        setContent {
            // Read persisted theme preferences
            val darkFlow  = remember { session.darkModeFlow() }
            val themeFlow = remember { session.themeColorFlow() }
            val isDark    by darkFlow.collectAsState(initial = session.isDarkMode())
            val themeStr  by themeFlow.collectAsState(initial = session.getThemeColor())
            val appTheme  = themeFromString(themeStr)

            SmartStudyTheme(darkTheme = isDark, appTheme = appTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SmartStudyNavGraph()
                }
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
            kotlinx.coroutines.runBlocking {
                session.recordSessionMinutes(sessionMin)
            }
        }
        SmartStudyFirebaseService.updatePresence(this, false)
    }

    override fun onStop() {
        super.onStop()
        SmartStudyFirebaseService.updatePresence(this, false)
    }
}
