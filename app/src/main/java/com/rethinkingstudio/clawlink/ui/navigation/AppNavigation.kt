package com.rethinkingstudio.clawlink.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rethinkingstudio.clawlink.AppContainer
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

object Routes {
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
}

@Composable
fun AppNavigation(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val authState by container.authStore.state.collectAsState()

    LaunchedEffect(Unit) {
        container.authStore.tryRestoreSession()
    }

    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
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

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                authStore = container.authStore,
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { /* TODO: Register flow */ }
            )
        }

        composable(Routes.PAIRING) {
            PairingScreen(
                authStore = container.authStore,
                onPairSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                container = container,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.GATEWAYS) {
            GatewayListScreen(
                gatewayStore = container.gatewayStore,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT) {
            com.rethinkingstudio.clawlink.ui.screens.chat.ChatScreen(
                chatStore = container.chatStore,
                gatewayStore = container.gatewayStore,
                modelStore = container.modelStore,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
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
                onNavigateToAdvanced = { navController.navigate(Routes.ADVANCED) },
                onNavigateToHelp = { navController.navigate(Routes.HELP) },
                onLogout = { }
            )
        }

        composable(Routes.ADVANCED) {
            AdvancedScreen(
                backupStore = container.backupStore,
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
