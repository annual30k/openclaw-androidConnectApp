package com.rethinkingstudio.clawlink.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rethinkingstudio.clawlink.AppContainer
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.screens.auth.LoginScreen
import com.rethinkingstudio.clawlink.ui.screens.auth.PairingScreen
import com.rethinkingstudio.clawlink.ui.screens.gateway.GatewayListScreen
import com.rethinkingstudio.clawlink.ui.screens.main.MainScreen
import com.rethinkingstudio.clawlink.ui.screens.model.ModelCatalogScreen
import com.rethinkingstudio.clawlink.ui.screens.settings.SettingsScreen
import com.rethinkingstudio.clawlink.ui.screens.settings.AdvancedScreen
import com.rethinkingstudio.clawlink.ui.screens.settings.HelpScreen
import com.rethinkingstudio.clawlink.ui.screens.skills.SkillsScreen
import com.rethinkingstudio.clawlink.ui.screens.tasks.TasksScreen
import com.rethinkingstudio.clawlink.ui.screens.welcome.WelcomeCarouselScreen

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val PAIRING = "pairing"
    const val MAIN = "main"
    const val GATEWAYS = "gateways"
    const val CHAT = "chat"
    const val MODELS = "models"
    const val SKILLS = "skills"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val ADVANCED = "advanced"
    const val HELP = "help"
    const val OFFICE = "office"
    const val SESSIONS = "sessions"
    const val VOICE_SETUP = "voice_setup"
    const val LANGUAGE = "language"
    const val LOGS = "logs"
    const val BACKUPS = "backups"
    const val DOCTOR_FIX = "doctor_fix"
}

@Composable
fun AppNavigation(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val welcomePrefs = remember { context.getSharedPreferences("clawlink_welcome", 0) }
    var hasSeenWelcome by remember { mutableStateOf(welcomePrefs.getBoolean("seen", false)) }
    var hasRestoredSession by remember { mutableStateOf(false) }
    val authState by container.authStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        container.authStore.tryRestoreSession()
        if (container.authStore.isLoggedIn) {
            if (!hasSeenWelcome) {
                welcomePrefs.edit().putBoolean("seen", true).apply()
                hasSeenWelcome = true
            }
            container.gatewayStore.loadGateways()
        }
        hasRestoredSession = true
    }

    if (!hasRestoredSession) {
        ClawLinkScaffold {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val startDestination = when {
        !hasSeenWelcome -> Routes.WELCOME
        authState.isLoggedIn -> Routes.MAIN
        else -> Routes.LOGIN
    }

    LaunchedEffect(authState.isLoggedIn, hasSeenWelcome) {
        if (!hasSeenWelcome) {
            navController.navigate(Routes.WELCOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else if (authState.isLoggedIn) {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.LOGIN) { inclusive = true }
                launchSingleTop = true
            }
        } else if (navController.currentBackStackEntry?.destination?.route != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeCarouselScreen(
                onFinish = {
                    welcomePrefs.edit().putBoolean("seen", true).apply()
                    hasSeenWelcome = true
                    navController.navigate(if (authState.isLoggedIn) Routes.MAIN else Routes.LOGIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                authStore = container.authStore,
                onLoginSuccess = {
                    scope.launch {
                        welcomePrefs.edit().putBoolean("seen", true).apply()
                        hasSeenWelcome = true
                        container.gatewayStore.loadGateways()
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.PAIRING) {
            PairingScreen(
                authStore = container.authStore,
                onPairSuccess = {
                    scope.launch {
                        welcomePrefs.edit().putBoolean("seen", true).apply()
                        hasSeenWelcome = true
                        container.gatewayStore.loadGateways()
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.PAIRING) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                container = container,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHelp = { navController.navigate(Routes.HELP) },
                hasSeenUsageGuide = hasSeenWelcome
            )
        }

        composable(Routes.GATEWAYS) {
            GatewayListScreen(
                gatewayStore = container.gatewayStore,
                onNavigateToPairing = { navController.navigate(Routes.PAIRING) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT) {
            com.rethinkingstudio.clawlink.ui.screens.chat.ChatScreen(
                chatStore = container.chatStore,
                gatewayStore = container.gatewayStore,
                modelStore = container.modelStore,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenUsageGuide = { navController.navigate(Routes.HELP) }
            )
        }

        composable(Routes.MODELS) {
            ModelCatalogScreen(
                modelStore = container.modelStore,
                gatewayStore = container.gatewayStore,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SKILLS) {
            SkillsScreen(
                skillStore = container.skillStore,
                gatewayStore = container.gatewayStore,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TASKS) {
            TasksScreen(
                taskStore = container.taskStore,
                gatewayStore = container.gatewayStore,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                authStore = container.authStore,
                gatewayStore = container.gatewayStore,
                wsClient = container.wsClient,
                notificationPort = container.notificationPort,
                onBack = { navController.popBackStack() },
                onNavigateToGateways = { navController.navigate(Routes.GATEWAYS) },
                onNavigateToPairing = { navController.navigate(Routes.PAIRING) },
                onNavigateToModels = { navController.navigate(Routes.MODELS) },
                onNavigateToSkills = { navController.navigate(Routes.SKILLS) },
                onNavigateToTasks = { navController.navigate(Routes.TASKS) },
                onNavigateToAdvanced = { navController.navigate(Routes.ADVANCED) },
                onNavigateToHelp = { navController.navigate(Routes.HELP) },
                onNavigateToOffice = { navController.navigate(Routes.OFFICE) },
                onNavigateToSessions = { navController.navigate(Routes.SESSIONS) },
                onNavigateToVoiceSetup = { navController.navigate(Routes.VOICE_SETUP) },
                onNavigateToLanguage = { navController.navigate(Routes.LANGUAGE) },
                onLogout = {
                    scope.launch { container.authStore.logout() }
                },
                onDeleteAccount = {
                    // Handle account deletion if needed
                }
            )
        }

        composable(Routes.ADVANCED) {
            AdvancedScreen(
                gatewayStore = container.gatewayStore,
                prefsStore = container.userPreferencesStore,
                onBack = { navController.popBackStack() },
                onNavigateToBackups = { navController.navigate(Routes.BACKUPS) },
                onNavigateToLogs = { navController.navigate(Routes.LOGS) },
                onNavigateToDoctorFix = { navController.navigate(Routes.DOCTOR_FIX) }
            )
        }

        composable(Routes.OFFICE) {
            com.rethinkingstudio.clawlink.ui.screens.settings.OfficeScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SESSIONS) {
            com.rethinkingstudio.clawlink.ui.screens.settings.SessionsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VOICE_SETUP) {
            com.rethinkingstudio.clawlink.ui.screens.settings.VoiceSetupScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LANGUAGE) {
            com.rethinkingstudio.clawlink.ui.screens.settings.LanguageScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BACKUPS) {
            com.rethinkingstudio.clawlink.ui.screens.settings.BackupScreen(
                backupStore = container.backupStore,
                gatewayStore = container.gatewayStore,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LOGS) {
            com.rethinkingstudio.clawlink.ui.screens.settings.LogScreen(
                gatewayStore = container.gatewayStore,
                apiClient = container.apiClient,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DOCTOR_FIX) {
            com.rethinkingstudio.clawlink.ui.screens.settings.DoctorFixScreen(
                gatewayStore = container.gatewayStore,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HELP) {
            HelpScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
