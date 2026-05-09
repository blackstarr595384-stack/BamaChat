package com.example.bamachat.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.example.bamachat.ui.component.BamaChatBottomNav
import com.example.bamachat.ui.viewmodel.AuthViewModel
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.CollabViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel

private object Routes {
    const val WELCOME = "welcome"
    const val AUTH = "auth"
    const val HOME_HUB = "home_hub"
    const val CHAT = "chat"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val SETTINGS_WITH_SECTION = "settings?section={section}"
    const val HELP = "help"
    const val REALTIME_COLLAB = "realtime_collab"
    const val MINI_APPS = "mini_apps"
    const val AGENT_HUB = "agent_hub"
    const val COMPOSE_LAB = "compose_lab"
    const val COMPOSE_PLAYGROUND = "compose_playground"
    const val COMPOSE_ARG_DEMO = "compose_arg_demo/{demoId}"

    fun settingsRoute(section: String?): String =
        if (section.isNullOrBlank()) SETTINGS else "$SETTINGS?section=$section"
}

private val topLevelRoutes = listOf(
    Routes.HOME_HUB,
    Routes.CHAT,
    Routes.PROFILE,
    Routes.SETTINGS
)

private fun normalizeRoute(route: String?): String? = route?.substringBefore("?")?.substringBefore("/")

private fun topLevelRank(route: String?): Int = topLevelRoutes.indexOf(normalizeRoute(route))

@Composable
fun BamaChatApp(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    collabViewModel: CollabViewModel
) {
    val navController = rememberNavController()
    val primaryColorInt by settingsViewModel.primaryColorInt.collectAsState()
    val aiProvider by settingsViewModel.aiProvider.collectAsState()
    val designPreset by settingsViewModel.uiDesignPreset.collectAsState()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val normalizedRoute = normalizeRoute(currentRoute)
    val startDestination = Routes.WELCOME

    LaunchedEffect(isAuthenticated, normalizedRoute) {
        if (isAuthenticated && normalizedRoute == Routes.AUTH) {
            navController.navigate(Routes.HOME_HUB) {
                popUpTo(Routes.AUTH) { inclusive = true }
                launchSingleTop = true
            }
        } else if (!isAuthenticated && normalizedRoute == Routes.REALTIME_COLLAB) {
            navController.navigate(Routes.AUTH) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    val bottomNavRoutes = topLevelRoutes.toSet()
    val shouldRenderBottomNav = normalizedRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = shouldRenderBottomNav,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                BamaChatBottomNav(
                    currentRoute = normalizedRoute,
                    designPreset = designPreset,
                    onNavigate = { route ->
                        if (route != normalizedRoute) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                val from = normalizeRoute(initialState.destination.route)
                val to = normalizeRoute(targetState.destination.route)
                val fromRank = topLevelRank(from)
                val toRank = topLevelRank(to)
                if (fromRank >= 0 && toRank >= 0) {
                    fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(220))
                } else {
                    val forward = when {
                        fromRank >= 0 && toRank < 0 -> true
                        fromRank < 0 && toRank >= 0 -> false
                        else -> true
                    }
                    slideInHorizontally(
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth ->
                            if (forward) (fullWidth * 0.12f).toInt() else -(fullWidth * 0.12f).toInt()
                        }
                    ) + fadeIn(animationSpec = tween(210))
                }
            },
            exitTransition = {
                val from = normalizeRoute(initialState.destination.route)
                val to = normalizeRoute(targetState.destination.route)
                val fromRank = topLevelRank(from)
                val toRank = topLevelRank(to)
                if (fromRank >= 0 && toRank >= 0) {
                    fadeOut(animationSpec = tween(160)) +
                        scaleOut(targetScale = 1.01f, animationSpec = tween(180))
                } else {
                    val forward = when {
                        fromRank >= 0 && toRank < 0 -> true
                        fromRank < 0 && toRank >= 0 -> false
                        else -> true
                    }
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
                        targetOffsetX = { fullWidth ->
                            if (forward) -(fullWidth * 0.05f).toInt() else (fullWidth * 0.05f).toInt()
                        }
                    ) + fadeOut(animationSpec = tween(180))
                }
            },
            popEnterTransition = {
                val from = normalizeRoute(initialState.destination.route)
                val to = normalizeRoute(targetState.destination.route)
                val fromRank = topLevelRank(from)
                val toRank = topLevelRank(to)
                if (fromRank >= 0 && toRank >= 0) {
                    fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing)) +
                        scaleIn(initialScale = 0.985f, animationSpec = tween(200))
                } else {
                    val forward = when {
                        fromRank >= 0 && toRank < 0 -> false
                        fromRank < 0 && toRank >= 0 -> true
                        else -> false
                    }
                    slideInHorizontally(
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth ->
                            if (forward) -(fullWidth * 0.1f).toInt() else (fullWidth * 0.1f).toInt()
                        }
                    ) + fadeIn(animationSpec = tween(210))
                }
            },
            popExitTransition = {
                val from = normalizeRoute(initialState.destination.route)
                val to = normalizeRoute(targetState.destination.route)
                val fromRank = topLevelRank(from)
                val toRank = topLevelRank(to)
                if (fromRank >= 0 && toRank >= 0) {
                    fadeOut(animationSpec = tween(150)) +
                        scaleOut(targetScale = 1.01f, animationSpec = tween(170))
                } else {
                    val forward = when {
                        fromRank >= 0 && toRank < 0 -> false
                        fromRank < 0 && toRank >= 0 -> true
                        else -> false
                    }
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
                        targetOffsetX = { fullWidth ->
                            if (forward) (fullWidth * 0.05f).toInt() else -(fullWidth * 0.05f).toInt()
                        }
                    ) + fadeOut(animationSpec = tween(180))
                }
            }
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    isAuthenticated = isAuthenticated,
                    onOpenChat = { navController.navigate(Routes.HOME_HUB) },
                    onOpenAuth = { navController.navigate(Routes.AUTH) },
                    onContinueAsGuest = {
                        authViewModel.continueAsGuest()
                        navController.navigate(Routes.HOME_HUB)
                    },
                    onOpenHelp = { navController.navigate(Routes.HELP) }
                )
            }
            composable(Routes.AUTH) {
                AuthScreen(
                    authViewModel = authViewModel,
                    onAuthenticated = {
                        navController.navigate(Routes.HOME_HUB) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME_HUB) {
                HomeHubScreen(
                    providerName = aiProvider,
                    designPreset = designPreset,
                    onOpenMenu = { navController.navigate(Routes.CHAT) },
                    onOpenChat = { navController.navigate(Routes.CHAT) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenProviderSettings = { navController.navigate(Routes.settingsRoute("ai")) },
                    onOpenDesignSettings = { navController.navigate(Routes.settingsRoute("chat")) },
                    onOpenMiniApps = { navController.navigate(Routes.MINI_APPS) },
                    onOpenAgentHub = { navController.navigate(Routes.AGENT_HUB) },
                    onOpenRealtimeCollab = { navController.navigate(Routes.REALTIME_COLLAB) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenHelp = { navController.navigate(Routes.HELP) }
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(
                    viewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    onOpenMiniApps = { navController.navigate(Routes.MINI_APPS) },
                    onOpenAgentHub = { navController.navigate(Routes.AGENT_HUB) },
                    onOpenComposeLab = { navController.navigate(Routes.COMPOSE_LAB) }
                )
            }
            composable(
                route = Routes.SETTINGS_WITH_SECTION,
                arguments = listOf(
                    navArgument("section") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val initialSection = backStackEntry.arguments
                    ?.getString("section")
                    ?.takeIf { it.isNotBlank() }
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    initialSection = initialSection
                )
            }
            composable(Routes.HELP) {
                HelpCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REALTIME_COLLAB) {
                RealtimeCollabScreen(
                    collabViewModel = collabViewModel,
                    chatViewModel = chatViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    authViewModel = authViewModel,
                    designPreset = designPreset,
                    onBack = { navController.popBackStack() },
                    onRequireLogin = {
                        navController.navigate(Routes.AUTH) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
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
}
