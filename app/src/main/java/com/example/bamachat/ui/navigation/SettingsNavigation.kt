package com.example.bamachat.ui.navigation

internal object SettingsNavigationRoutes {
    const val OVERVIEW = "settings"
    const val VOICE_AUDIO = "settings/voice-audio"
    const val LEGACY_SECTION_PATTERN = "settings?section={section}"

    fun legacySection(section: String): String = "$OVERVIEW?section=$section"
}

internal enum class SettingsOverviewNavigationAction {
    Navigate,
    PopToOverview,
    None
}

internal object SettingsNavigationPolicy {
    fun isSettingsFlowRoute(route: String?): Boolean =
        BottomNavigationRoutePolicy.normalize(route) == SettingsNavigationRoutes.OVERVIEW

    fun shouldExitSettingsFlow(currentRoute: String?, targetRoute: String?): Boolean =
        isSettingsFlowRoute(currentRoute) && !isSettingsFlowRoute(targetRoute)

    fun overviewAction(currentRoute: String?): SettingsOverviewNavigationAction {
        val path = currentRoute?.substringBefore("?")
        return when {
            path?.startsWith("${SettingsNavigationRoutes.OVERVIEW}/") == true ->
                SettingsOverviewNavigationAction.PopToOverview
            BottomNavigationRoutePolicy.normalize(currentRoute) == SettingsNavigationRoutes.OVERVIEW ->
                SettingsOverviewNavigationAction.None
            else -> SettingsOverviewNavigationAction.Navigate
        }
    }
}
