package com.example.bamachat.ui.screen

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.bamachat.ui.component.BamaChatBottomNav
import com.example.bamachat.ui.navigation.BottomNavigationRoutePolicy
import com.example.bamachat.ui.navigation.BottomNavigationUiState
import com.example.bamachat.ui.viewmodel.AuthViewModel
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.ui.viewmodel.CollabViewModel
import com.example.bamachat.ui.viewmodel.ExtensionManagerViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.LegalPolicy
import kotlinx.coroutines.launch

private object Routes {
    const val ONBOARDING = "onboarding"
    const val LEGAL = "legal"
    const val WELCOME = "welcome"
    const val AUTH = "auth"
    const val HOME_HUB = "home_hub"
    const val CHAT = "chat"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val SETTINGS_WITH_SECTION = "settings?section={section}"
    const val HELP = "help"
    const val HERMES_CODING_ASSISTANT = "hermes_coding_assistant"
    const val REALTIME_COLLAB = "realtime_collab"
    const val MINI_APPS = "mini_apps"
    const val AGENT_HUB = "agent_hub"
    const val EXTENSIONS = "extensions"
    const val COMPOSE_LAB = "compose_lab"
    const val COMPOSE_PLAYGROUND = "compose_playground"
    const val KNOWLEDGE_GRAPH = "knowledge_graph"
    const val WORKSPACES = "workspaces"
    const val CHAT_SEARCH = "chat_search"
    const val COMPOSE_ARG_DEMO = "compose_arg_demo/{demoId}"

    fun settingsRoute(section: String?): String =
        if (section.isNullOrBlank()) SETTINGS else "$SETTINGS?section=$section"
}

private val topLevelRoutes = listOf(
    Routes.MINI_APPS,
    Routes.HOME_HUB,
    Routes.CHAT,
    Routes.PROFILE,
    Routes.SETTINGS,
    Routes.WORKSPACES
)

private fun normalizeRoute(route: String?): String? = BottomNavigationRoutePolicy.normalize(route)

private fun topLevelRank(route: String?): Int = topLevelRoutes.indexOf(normalizeRoute(route))

@Composable
fun BamaChatApp() {
    val chatViewModel: ChatViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val collabViewModel: CollabViewModel = hiltViewModel()
    val extensionManagerViewModel: ExtensionManagerViewModel = hiltViewModel()
    val navController = rememberNavController()
    val primaryColorInt by settingsViewModel.primaryColorInt.collectAsState()
    val aiProvider by settingsViewModel.aiProvider.collectAsState()
    val designPreset by settingsViewModel.uiDesignPreset.collectAsState()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsState()
    val simpleModeEnabled by settingsViewModel.simpleModeEnabled.collectAsState()
    val legalAcknowledgedVersion by settingsViewModel.legalAcknowledgedVersion.collectAsState()
    val connectChatBottomBars by settingsViewModel.connectChatBottomBars.collectAsState()
    val uiCornerRoundnessScale by settingsViewModel.uiCornerRoundnessScale.collectAsState()
    val uiShadowIntensityScale by settingsViewModel.uiShadowIntensityScale.collectAsState()
    val uiSurfaceOpacity by settingsViewModel.uiSurfaceOpacity.collectAsState()
    val developerRealtimeCollabTesting by settingsViewModel.developerRealtimeCollabTesting.collectAsState()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val firebaseUser by authViewModel.firebaseUser.collectAsState()
    val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val normalizedRoute = normalizeRoute(currentRoute)
    val destinationHierarchyRoutes = navBackStackEntry
        ?.destination
        ?.hierarchy
        ?.map { it.route }
        ?.toList()
        .orEmpty()
    var previousBottomNavigationState by remember { mutableStateOf(BottomNavigationUiState()) }
    val bottomNavigationState = BottomNavigationRoutePolicy.resolve(
        previousState = previousBottomNavigationState,
        destinationHierarchyRoutes = destinationHierarchyRoutes
    )
    SideEffect {
        if (previousBottomNavigationState != bottomNavigationState) {
            previousBottomNavigationState = bottomNavigationState
        }
    }
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val startDestination = remember(onboardingCompleted, legalAcknowledgedVersion) {
        when {
            !onboardingCompleted -> Routes.ONBOARDING
            legalAcknowledgedVersion < LegalPolicy.CURRENT_ACK_VERSION -> Routes.LEGAL
            else -> Routes.WELCOME
        }
    }

    fun clearStartStackAndNavigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun navigateWelcomeThenAuth() {
        navController.navigate(Routes.WELCOME) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
            launchSingleTop = true
        }
        navController.navigate(Routes.AUTH) {
            launchSingleTop = true
        }
    }

    fun navigateHomeHub() {
        if (!navController.popBackStack(Routes.HOME_HUB, false)) {
            navController.navigate(Routes.HOME_HUB) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun navigateTopLevel(route: String) {
        val normalizedTarget = normalizeRoute(route) ?: route
        if (normalizedTarget == normalizedRoute && !route.contains("?")) return
        if (normalizedTarget == Routes.HOME_HUB) {
            navigateHomeHub()
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(isAuthenticated, normalizedRoute) {
        if (isAuthenticated && normalizedRoute == Routes.AUTH) {
            navController.navigate(Routes.HOME_HUB) {
                popUpTo(Routes.AUTH) { inclusive = true }
                launchSingleTop = true
            }
        } else if (!isAuthenticated && !developerRealtimeCollabTesting && normalizedRoute == Routes.REALTIME_COLLAB) {
            navController.navigate(Routes.AUTH) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (bottomNavigationState.visible) {
                BamaChatBottomNav(
                    currentRoute = bottomNavigationState.selectedRoute,
                    designPreset = designPreset,
                    attachedToComposer = bottomNavigationState.selectedRoute == Routes.CHAT && connectChatBottomBars,
                    cornerRoundnessScale = uiCornerRoundnessScale,
                    shadowIntensityScale = uiShadowIntensityScale,
                    surfaceOpacity = uiSurfaceOpacity,
                    onNavigate = { route -> navigateTopLevel(route) }
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        settingsViewModel.completeOnboarding()
                        val nextRoute = if (legalAcknowledgedVersion >= LegalPolicy.CURRENT_ACK_VERSION) {
                            Routes.WELCOME
                        } else {
                            Routes.LEGAL
                        }
                        clearStartStackAndNavigate(nextRoute)
                    },
                    onSkip = {
                        settingsViewModel.completeOnboarding()
                        val nextRoute = if (legalAcknowledgedVersion >= LegalPolicy.CURRENT_ACK_VERSION) {
                            Routes.WELCOME
                        } else {
                            Routes.LEGAL
                        }
                        clearStartStackAndNavigate(nextRoute)
                    }
                )
            }
            composable(Routes.LEGAL) {
                LegalDisclaimerScreen(
                    onAccept = {
                        settingsViewModel.acceptLegalPolicy()
                        clearStartStackAndNavigate(Routes.WELCOME)
                    },
                    onBack = {
                        if (!navController.popBackStack()) {
                            activity?.finish()
                        }
                    }
                )
            }
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    isAuthenticated = isAuthenticated,
                    onOpenChat = { navController.navigate(Routes.HOME_HUB) },
                    onOpenAuth = { navController.navigate(Routes.AUTH) },
                    onContinueAsGuest = {
                        authViewModel.continueAsGuest()
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenHelp = { navController.navigate(Routes.HELP) }
                )
            }
            composable(Routes.AUTH) {
                AuthScreen(
                    authViewModel = authViewModel,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.WELCOME) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenHelp = { navController.navigate(Routes.HELP) },
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
                    activeWorkspaceName = activeWorkspaceName,
                    onOpenMenu = {
                        chatViewModel.setChatWorkspaceContext(null)
                        settingsViewModel.setWorkspaceChatFilterEnabled(false)
                        navController.navigate(Routes.CHAT)
                    },
                    onOpenChat = {
                        chatViewModel.setChatWorkspaceContext(null)
                        settingsViewModel.setWorkspaceChatFilterEnabled(false)
                        scope.launch {
                            chatViewModel.openOrCreateNormalConversation()
                            navController.navigate(Routes.CHAT)
                        }
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenProviderSettings = { navController.navigate(Routes.settingsRoute("ai")) },
                    onOpenDesignSettings = { navController.navigate(Routes.settingsRoute("chat")) },
                    onOpenWorkspaceSettings = { navController.navigate(Routes.WORKSPACES) },
                    onOpenMiniApps = { navController.navigate(Routes.MINI_APPS) },
                    onOpenAgentHub = { navController.navigate(Routes.AGENT_HUB) },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) },
                    onOpenRealtimeCollab = { navController.navigate(Routes.REALTIME_COLLAB) },
                    onOpenHermesCodingAssistant = { navController.navigate(Routes.HERMES_CODING_ASSISTANT) },
                    onOpenKnowledgeGraph = { navController.navigate(Routes.KNOWLEDGE_GRAPH) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenHelp = { navController.navigate(Routes.HELP) },
                    simpleModeEnabled = simpleModeEnabled,
                    onToggleSimpleMode = { value -> settingsViewModel.setSimpleModeEnabled(value) }
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(
                    viewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    onBottomNavRoute = { route -> navigateTopLevel(route) },
                    onOpenMiniApps = { navController.navigate(Routes.MINI_APPS) },
                    onOpenAgentHub = { navController.navigate(Routes.AGENT_HUB) },
                    onOpenComposeLab = { navController.navigate(Routes.COMPOSE_LAB) },
                    onOpenWorkspace = { navController.navigate(Routes.WORKSPACES) },
                    onSearchClick = { navController.navigate(Routes.CHAT_SEARCH) }
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
                    cloudChatSyncUid = firebaseUser?.uid,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    initialSection = initialSection,
                    onOpenWorkspaceSettings = { navController.navigate(Routes.WORKSPACES) },
                    mcpServerManager = chatViewModel.mcpServerManager,
                    mcpWorkflowManager = chatViewModel.mcpWorkflowManager
                )
            }
            composable(Routes.HELP) {
                HelpCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HERMES_CODING_ASSISTANT) {
                HermesCodingAssistantScreen(onBack = { navController.popBackStack() })
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
                    onRequireLogin = { navigateWelcomeThenAuth() },
                    onOpenSettings = { navigateTopLevel(Routes.SETTINGS) }
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
            composable(Routes.EXTENSIONS) {
                ExtensionManagerScreen(
                    extensionManagerViewModel = extensionManagerViewModel,
                    designPreset = designPreset,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.KNOWLEDGE_GRAPH) {
                KnowledgeGraphScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.WORKSPACES) {
                WorkspaceScreen(
                    settingsViewModel = settingsViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { workspaceId ->
                        settingsViewModel.setActiveWorkspace(workspaceId)
                        settingsViewModel.setWorkspaceChatFilterEnabled(true)
                        chatViewModel.setChatWorkspaceContext(workspaceId)
                        scope.launch {
                            chatViewModel.openOrCreateWorkspaceConversation(workspaceId)
                            navController.navigate(Routes.CHAT) {
                                popUpTo(Routes.HOME_HUB)
                            }
                        }
                    }
                )
            }
            composable(Routes.COMPOSE_LAB) {
                ComposeLabScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlayground = { navController.navigate(Routes.COMPOSE_PLAYGROUND) }
                )
            }
            composable(Routes.CHAT_SEARCH) {
                ChatSearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { conversationId ->
                        chatViewModel.setChatWorkspaceContext(null)
                        settingsViewModel.setWorkspaceChatFilterEnabled(false)
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.HOME_HUB)
                        }
                        chatViewModel.switchConversation(conversationId)
                    }
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
