package com.hanif.smartstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hanif.smartstudy.ui.navigation.SmartStudyNavGraph
import com.hanif.smartstudy.ui.theme.SmartStudyTheme
import com.hanif.smartstudy.util.ActivityReporter
import com.hanif.smartstudy.util.FcmHelper
import com.hanif.smartstudy.util.ReminderHelper
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.util.SoundManager
import com.hanif.smartstudy.viewmodel.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        // Phase 5: reminder channel
        ReminderHelper.createChannel(this)

        // Phase 5: sound init
        SoundManager.init(this)

        setContent {
            val context = LocalContext.current
            val session = remember { SessionManager(context) }

            // Dark mode reactive
            val isDark   = remember { mutableStateOf(session.isDarkMode()) }
            val themeKey = remember { mutableStateOf(
                context.getSharedPreferences("quiz_prefs", MODE_PRIVATE)
                    .getString("app_theme", "indigo") ?: "indigo"
            )}
            val appTheme = AppTheme.entries.firstOrNull { it.key == themeKey.value } ?: AppTheme.INDIGO

            SmartStudyTheme(darkTheme = isDark.value, appTheme = appTheme) {
                SmartStudyNavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Phase 5: report active + collect FCM token
        ActivityReporter.reportActive(application)
        FcmHelper.collectAndSave(application)
    }

    override fun onPause() {
        super.onPause()
        ActivityReporter.reportInactive(application)
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
