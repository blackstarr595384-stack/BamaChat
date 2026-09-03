package com.example.bamachat.ui.navigation

internal data class BottomNavigationUiState(
    val visible: Boolean = false,
    val selectedRoute: String? = null
)

internal object BottomNavigationRoutePolicy {
    private val visibleRoutes = setOf(
        "home_hub",
        "chat",
        "mini_apps",
        "profile",
        "settings",
        "workspaces"
    )

    private val selectableRoutes = setOf(
        "home_hub",
        "chat",
        "mini_apps",
        "profile",
        "settings"
    )

    private val explicitlyHiddenRoutes = setOf(
        "onboarding",
        "legal",
        "welcome",
        "auth",
        "help",
        "hermes_coding_assistant",
        "realtime_collab",
        "agent_hub",
        "extensions",
        "compose_lab",
        "compose_playground",
        "knowledge_graph",
        "chat_search",
        "compose_arg_demo"
    )

    fun normalize(route: String?): String? =
        route?.substringBefore("?")?.substringBefore("/")

    fun resolve(
        previousState: BottomNavigationUiState,
        destinationHierarchyRoutes: Iterable<String?>
    ): BottomNavigationUiState {
        val routes = destinationHierarchyRoutes.toList()
        if (routes.any(SettingsNavigationPolicy::isFullscreenSubpage)) {
            return BottomNavigationUiState(
                visible = false,
                selectedRoute = previousState.selectedRoute
            )
        }
        val normalizedRoutes = routes.mapNotNull(::normalize)
        val visibleRoute = normalizedRoutes.firstOrNull { it in visibleRoutes }
        if (visibleRoute != null) {
            return BottomNavigationUiState(
                visible = true,
                selectedRoute = visibleRoute.takeIf { it in selectableRoutes }
            )
        }

        val currentRoute = normalizedRoutes.firstOrNull() ?: return previousState
        return if (currentRoute in explicitlyHiddenRoutes) {
            BottomNavigationUiState(visible = false, selectedRoute = previousState.selectedRoute)
        } else {
            previousState
        }
    }
}
