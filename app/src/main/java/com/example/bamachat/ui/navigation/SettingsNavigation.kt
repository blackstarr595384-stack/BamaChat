package com.example.bamachat.ui.navigation

internal object SettingsNavigationRoutes {
    const val OVERVIEW = "settings"
    const val VOICE_AUDIO = "settings/voice-audio"
    const val AI_MODELS = "settings/ai-models"
    const val PROVIDERS = "settings/ai-models/providers"
    const val PROVIDER_ADD = "settings/ai-models/providers/add"
    const val PROVIDER_EDIT_PATTERN = "settings/ai-models/providers/{providerId}"
    const val LEGACY_SECTION_PATTERN = "settings?section={section}"

    fun legacySection(section: String): String = "$OVERVIEW?section=$section"
    fun providerEditor(providerId: String): String = "$PROVIDERS/${android.net.Uri.encode(providerId)}"
}

internal enum class SettingsOverviewNavigationAction {
    Navigate,
    PopToOverview,
    None
}

internal object SettingsNavigationPolicy {
    fun isFullscreenSubpage(route: String?): Boolean {
        val path = route?.substringBefore("?") ?: return false
        return path == SettingsNavigationRoutes.AI_MODELS ||
            path == SettingsNavigationRoutes.PROVIDERS ||
            path == SettingsNavigationRoutes.PROVIDER_ADD ||
            path == SettingsNavigationRoutes.PROVIDER_EDIT_PATTERN ||
            path.startsWith("${SettingsNavigationRoutes.PROVIDERS}/")
    }

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
