package com.hanif.smartstudy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hanif.smartstudy.data.model.Achievement
import com.hanif.smartstudy.ui.auth.AuthScreen
import com.hanif.smartstudy.ui.main.MainScreen
import com.hanif.smartstudy.ui.splash.SplashScreen
import com.hanif.smartstudy.util.DeepLinkAction

object Routes {
    const val SPLASH = "splash"
    const val AUTH   = "auth"
    const val MAIN   = "main"
}

@Composable
fun SmartStudyNavGraph(
    navController           : NavHostController = rememberNavController(),
    deepLink                : DeepLinkAction    = DeepLinkAction(DeepLinkAction.Type.NONE),
    onAchievementUnlocked   : (Achievement) -> Unit = {},
    onStreakUpdated          : (Int) -> Unit = {}
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(Routes.AUTH) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
                onNavigateToMain = {
                    navController.navigate(Routes.MAIN)  { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) { popUpTo(Routes.AUTH) { inclusive = true } }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                deepLink              = deepLink,
                onLogout              = {
                    navController.navigate(Routes.AUTH) { popUpTo(Routes.MAIN) { inclusive = true } }
                },
                onAchievementUnlocked = onAchievementUnlocked,
                onStreakUpdated       = onStreakUpdated
            )
        }
    }
}
