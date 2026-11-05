package com.hanif.smartstudy.vision

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun VisionApp() {
    val vm: VisionViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.init(context)
    }
    VisionTheme {
        if (!vm.loaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("লোড হচ্ছে...")
            }
        } else {
            val navController = rememberNavController()
            val start = if (vm.profile == null) "onboarding" else "today"
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            Scaffold(
                bottomBar = {
                    if (currentRoute == "today" || currentRoute == "learn" || currentRoute == "me") {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == "today",
                                onClick = {
                                    navController.navigate("today") {
                                        popUpTo(start) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                label = { Text("আজ") },
                                icon = { Text("🏠") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "learn",
                                onClick = {
                                    navController.navigate("learn") {
                                        popUpTo(start) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                label = { Text("পড়া") },
                                icon = { Text("📖") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "me",
                                onClick = {
                                    navController.navigate("me") {
                                        popUpTo(start) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                label = { Text("আমি") },
                                icon = { Text("👤") }
                            )
                        }
                    }
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = start,
                    modifier = Modifier.padding(padding)
                ) {
                    composable("onboarding") {
                        OnboardingScreen(navController, vm)
                    }
                    composable("diagnostic") {
                        DiagnosticScreen(navController, vm)
                    }
                    composable("today") {
                        TodayScreen(navController, vm)
                    }
                    composable("learn") {
                        LearnScreen(navController, vm)
                    }
                    composable("me") {
                        ProfileScreen(navController, vm)
                    }
                    composable(
                        route = "practice/{topicId}",
                        arguments = listOf(
                            navArgument("topicId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val topicId = backStackEntry.arguments?.getString("topicId") ?: ""
                        PracticeScreen(navController, vm, topicId)
                    }
                    composable("mock") {
                        MockScreen(navController, vm)
                    }
                    composable("mockResult") {
                        MockResultScreen(navController, vm)
                    }
                    composable("written") {
                        WrittenScreen(navController, vm)
                    }
                    composable(
                        route = "writtenDetail/{promptId}",
                        arguments = listOf(
                            navArgument("promptId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val promptId = backStackEntry.arguments?.getString("promptId") ?: ""
                        WrittenDetailScreen(navController, vm, promptId)
                    }
                    composable("papers") {
                        PapersScreen(navController, vm)
                    }
                    composable(
                        route = "paperDetail/{paperId}",
                        arguments = listOf(
                            navArgument("paperId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val paperId = backStackEntry.arguments?.getString("paperId") ?: ""
                        PaperDetailScreen(navController, vm, paperId)
                    }
                }
            }
        }
    }
}
