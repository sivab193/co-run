package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.RoomCodeGenerator
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveRunScreen
import com.example.ui.screens.OnboardingNameScreen
import com.example.ui.screens.RunSummaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RunTogetherTheme
import com.example.ui.theme.RunnerOrange
import com.example.ui.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val LIVE = "live/{roomCode}"
    const val SUMMARY = "summary/{runId}/{autoSave}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun live(roomCode: String) = "live/$roomCode"
    fun summary(runId: String, autoSave: Boolean) = "summary/$runId/$autoSave"
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleIncomingIntent(intent)

        setContent {
            RunTogetherTheme {
                RunTogetherRoot(mainViewModel = mainViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val code = RoomCodeGenerator.extractRoomCodeFromUri(intent?.data)
        if (code != null) {
            mainViewModel.onDeepLinkRoomCode(code)
        }
    }
}

@Composable
private fun RunTogetherRoot(mainViewModel: MainViewModel) {
    val profile by mainViewModel.userProfile.collectAsStateWithLifecycle()
    val homeState by mainViewModel.homeState.collectAsStateWithLifecycle()
    val history by mainViewModel.history.collectAsStateWithLifecycle()
    val isLoadingHistory by mainViewModel.isLoadingHistory.collectAsStateWithLifecycle()
    val pendingDeepLinkCode by mainViewModel.pendingDeepLinkCode.collectAsStateWithLifecycle()

    if (profile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RunnerOrange)
        }
        return
    }

    val currentProfile = profile!!
    if (currentProfile.displayName.isBlank()) {
        OnboardingNameScreen(
            onSaveName = { mainViewModel.saveDisplayName(it) }
        )
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(pendingDeepLinkCode) {
        val code = pendingDeepLinkCode ?: return@LaunchedEffect
        mainViewModel.clearPendingDeepLink()
        mainViewModel.joinRoom(code) { joinedCode ->
            navController.navigate(Routes.live(joinedCode)) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                displayName = currentProfile.displayName,
                isFirebaseConfigured = mainViewModel.isFirebaseConfigured,
                actionState = homeState,
                onCreateRoom = {
                    mainViewModel.createRoom { roomCode ->
                        navController.navigate(Routes.live(roomCode)) {
                            launchSingleTop = true
                        }
                    }
                },
                onJoinRoom = { code ->
                    mainViewModel.joinRoom(code) { joinedCode ->
                        navController.navigate(Routes.live(joinedCode)) {
                            launchSingleTop = true
                        }
                    }
                },
                onClearError = { mainViewModel.clearHomeError() },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.LIVE,
            arguments = listOf(navArgument("roomCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode").orEmpty()
            LiveRunScreen(
                roomCode = roomCode,
                onRunFinished = { runId ->
                    navController.navigate(Routes.summary(runId, autoSave = true)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onLeaveRoom = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(
            route = Routes.SUMMARY,
            arguments = listOf(
                navArgument("runId") { type = NavType.StringType },
                navArgument("autoSave") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val runId = backStackEntry.arguments?.getString("runId").orEmpty()
            val autoSave = backStackEntry.arguments?.getBoolean("autoSave") ?: false
            RunSummaryScreen(
                runId = runId,
                autoSaveToGallery = autoSave,
                onDone = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                entries = history,
                unit = currentProfile.distanceUnit,
                isLoading = isLoadingHistory,
                onRefresh = { mainViewModel.refreshHistory() },
                onSelectRun = { runId ->
                    navController.navigate(Routes.summary(runId, autoSave = false))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                currentName = currentProfile.displayName,
                currentUnit = currentProfile.distanceUnit,
                isFirebaseConfigured = mainViewModel.isFirebaseConfigured,
                onSaveName = { mainViewModel.saveDisplayName(it) },
                onSelectUnit = { mainViewModel.setDistanceUnit(it) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
