package com.example.bamachat.shared.core.github

object GitHubAnalysisScopeSelector {
    private val baselinePaths = listOf(
        "AGENTS.md",
        "README.md",
        "DEVELOPER_GUIDE.md",
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle/libs.versions.toml",
        "app/build.gradle.kts",
        "desktopApp/build.gradle.kts",
        "sharedCore/build.gradle.kts"
    )

    fun select(
        treeEntries: List<GitHubTreeEntry>,
        analysisArea: GitHubAnalysisArea
    ): List<String> {
        val available = treeEntries.asSequence()
            .filter { it.type == GitHubTreeEntryType.FILE }
            .map { it.path }
            .filter(GitHubPathPolicy::isAllowed)
            .distinct()
            .sorted()
            .toList()
        val availableSet = available.toSet()
        val selected = linkedSetOf<String>()
        baselinePaths.filterTo(selected, availableSet::contains)

        val rule = ruleFor(analysisArea)
        rule.preferredPaths.filterTo(selected, availableSet::contains)

        val matchingTests = available.filter { path ->
            isTestPath(path) && rule.matches(path)
        }
        matchingTests.take(MAX_AREA_TESTS).forEach(selected::add)

        available.asSequence()
            .filterNot(::isTestPath)
            .filter(rule::matches)
            .forEach { path ->
                if (selected.size < GitHubReadLimits.MAX_FILES) {
                    selected += path
                }
            }

        matchingTests.forEach { path ->
            if (selected.size < GitHubReadLimits.MAX_FILES) {
                selected += path
            }
        }

        return selected.take(GitHubReadLimits.MAX_FILES)
    }

    private fun isTestPath(path: String): Boolean {
        return "/test/" in path || "/androidTest/" in path
    }

    private fun ruleFor(area: GitHubAnalysisArea): ScopeRule = when (area) {
        GitHubAnalysisArea.ARCHITECTURE -> ScopeRule(
            preferredPaths = listOf(
                "app/src/main/java/com/example/bamachat/ui/screen/BamaChatApp.kt",
                "app/src/main/java/com/example/bamachat/di/AppModule.kt"
            ),
            tokens = listOf("/di/", "BamaChatApp", "ChatViewModel", "Extension", "shared/core")
        )
        GitHubAnalysisArea.SECURITY -> ScopeRule(
            preferredPaths = listOf(
                "app/src/main/java/com/example/bamachat/data/provider/ProviderUrlPolicy.kt",
                "app/src/main/java/com/example/bamachat/util/SecureSettingsStore.kt"
            ),
            tokens = listOf("security", "secure", "policy", "auth", "ProviderHttp", "Secret")
        )
        GitHubAnalysisArea.ANDROID_UI_UX -> ScopeRule(
            preferredPaths = listOf(
                "app/src/main/java/com/example/bamachat/ui/screen/BamaChatApp.kt",
                "app/src/main/java/com/example/bamachat/ui/screen/HomeHubScreen.kt"
            ),
            tokens = listOf("app/src/main/java/com/example/bamachat/ui/", "app/src/androidTest/")
        )
        GitHubAnalysisArea.DESKTOP -> ScopeRule(
            preferredPaths = listOf(
                "desktopApp/src/main/kotlin/com/example/bamachat/desktop/DesktopMain.kt",
                "desktopApp/src/main/kotlin/com/example/bamachat/desktop/DesktopChatGateway.kt"
            ),
            tokens = listOf("desktopApp/")
        )
        GitHubAnalysisArea.SHARED_CORE -> ScopeRule(
            preferredPaths = emptyList(),
            tokens = listOf("sharedCore/")
        )
        GitHubAnalysisArea.TESTS -> ScopeRule(
            preferredPaths = emptyList(),
            tokens = listOf("/test/", "/androidTest/", "Test.kt", "Test.java")
        )
        GitHubAnalysisArea.PERFORMANCE -> ScopeRule(
            preferredPaths = emptyList(),
            tokens = listOf("performance", "benchmark", "cache", "paging", "pagination")
        )
        GitHubAnalysisArea.ACCESSIBILITY -> ScopeRule(
            preferredPaths = emptyList(),
            tokens = listOf("accessibility", "semantics", "contentdescription", "/ui/screen/", "/ui/component/")
        )
        GitHubAnalysisArea.DOCUMENTATION -> ScopeRule(
            preferredPaths = emptyList(),
            tokens = listOf("docs/", ".md")
        )
        GitHubAnalysisArea.PROVIDER_SYSTEM -> ScopeRule(
            preferredPaths = listOf(
                "app/src/main/java/com/example/bamachat/data/ApiClient.kt",
                "app/src/main/java/com/example/bamachat/data/provider/chat/ProviderChatExecutionEngine.kt"
            ),
            tokens = listOf("/provider/", "ApiClient", "ProviderChat")
        )
        GitHubAnalysisArea.AGENTS_EXTENSIONS -> ScopeRule(
            preferredPaths = listOf(
                "app/src/main/java/com/example/bamachat/util/WorkspaceExtensions.kt",
                "sharedCore/src/main/kotlin/com/example/bamachat/shared/core/ExtensionRuntimeOrchestrator.kt"
            ),
            tokens = listOf("extension", "agent", "Mcp")
        )
    }

    private data class ScopeRule(
        val preferredPaths: List<String>,
        val tokens: List<String>
    ) {
        fun matches(path: String): Boolean {
            val normalized = path.lowercase()
            return tokens.any { normalized.contains(it.lowercase()) }
        }
    }

    private const val MAX_AREA_TESTS = 8
}
