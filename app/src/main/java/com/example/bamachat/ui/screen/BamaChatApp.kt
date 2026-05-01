package com.example.bamachat.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel

private object Routes {
    const val CHAT = "chat"
    const val MINI_APPS = "mini_apps"
    const val AGENT_HUB = "agent_hub"
    const val COMPOSE_LAB = "compose_lab"
    const val COMPOSE_PLAYGROUND = "compose_playground"
    const val COMPOSE_ARG_DEMO = "compose_arg_demo/{demoId}"
}

@Composable
fun BamaChatApp(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val primaryColorInt by settingsViewModel.primaryColorInt.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT
    ) {
        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = chatViewModel,
                settingsViewModel = settingsViewModel,
                onOpenMiniApps = { navController.navigate(Routes.MINI_APPS) },
                onOpenAgentHub = { navController.navigate(Routes.AGENT_HUB) },
                onOpenComposeLab = { navController.navigate(Routes.COMPOSE_LAB) }
            )
        }
        composable(Routes.MINI_APPS) {
            MiniAppsScreen(
                themeColor = Color(primaryColorInt),
                onClose = { navController.popBackStack() }
            )
        }
        composable(Routes.AGENT_HUB) {
            AgentHubScreen(
                settingsViewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.COMPOSE_LAB) {
            ComposeLabScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayground = { navController.navigate(Routes.COMPOSE_PLAYGROUND) }
            )
        }
        composable(Routes.COMPOSE_PLAYGROUND) {
            ComposePlaygroundScreen(
                onBack = { navController.popBackStack() },
                onOpenArgumentDemo = { demoId ->
                    navController.navigate("compose_arg_demo/$demoId")
                }
            )
        }
        composable(
            route = Routes.COMPOSE_ARG_DEMO,
            arguments = listOf(
                navArgument("demoId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val demoId = backStackEntry.arguments?.getInt("demoId") ?: 0
            ComposeArgumentDemoScreen(
                demoId = demoId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
