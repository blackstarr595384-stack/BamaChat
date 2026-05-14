# BamaChat AGENTS Leitfaden

## Große Architekturübersicht
- Dreimodulige App-Basis: Android-Hauptapp (`:app`) mit Kotlin + Compose + MVVM, Desktop-Client (`:desktopApp`) auf Compose Multiplatform (Windows Stage 6) und Shared-Core (`:sharedCore`) fuer plattformneutrale Logik; Android-Einstieg ist `MainActivity` + `BamaChatApplication` (Android 13+, minSdk 33).
- Navigation ist zentralisiert in `ui/screen/BamaChatApp.kt`; Auth-Status steuert Routen (`WELCOME`/`AUTH`/`HOME_HUB`/`CHAT` etc.).
- `ChatViewModel` ist der Orchestrierungskern: AI-Provider-Routing, Streaming, Persona-Logik, Quotas/Paywall, Benachrichtigungen, multimodaler Import und Cloud-Persona-Sync.
- Für installierbare Workspace-Plugins gibt es einen separaten Flow aus `ExtensionManagerViewModel` + `ExtensionManagerScreen` + `util/WorkspaceExtensions.kt` (Katalog, Capabilities, Persistenz); aktive Extensions werden im `ChatViewModel` turn-basiert in den Runtime-Kontext injiziert. Zusätzlich steuert die Composer-Quick-Action (`Auto`/`Research`/`Code Review`/`Plan`) den Extension-Modus pro Nachricht.
- Lokale Persistenz verwendet Room (`data/local/*`) über `ChatRepository`; AI-Netzwerkaufrufe werden absichtlich in `ChatViewModel` durchgeführt (siehe Repo-Kommentar in `ChatRepository.kt`).
- Cloud-Status ist aufgeteilt:
  - Benutzereigene Dokumente unter `users/{uid}` (+ `persona_profiles`, `persona_prompts`, `persona_training_examples`).
  - Realtime-Kollaboration unter `collab_sessions/{sessionId}` mit `messages` und `presence` Unterkollektionen.
- Telemetrie läuft über `util/AppTelemetry.kt`; Crashlytics ist in debugfähigen Builds deaktiviert (`BamaChatApplication.kt`).

## Kritische Workflows
- Debug-Build: `.\gradlew.bat :app:assembleDebug`
- Vollständiges Smoke-Gate: `.\gradlew.bat :app:stabilityCheck` (assemble + Unit-Tests + Lint)
- Desktop-Build: `.\gradlew.bat :desktopApp:build`
- Desktop-Start lokal: `.\gradlew.bat :desktopApp:run`
- Shared-Core-Tests: `.\gradlew.bat :sharedCore:test`
- Lint-Berichtspfad: `app/build/reports/lint-results-debug.html`
- Android UI Smoke-Test APK: `.\gradlew.bat :app:assembleDebugAndroidTest`; Geräteausführung: `:app:connectedDebugAndroidTest`
- Firebase IaC-Wrapper: `.\scripts\iac-firebase.ps1 -Environment dev -Action check|rules|indexes|all`
- Functions Deploy (Live-Web-Proxy): `npx firebase-tools deploy --only functions`
- Mini-Apps/Photo-AI Release-Checkliste: `MINIAPPS_RELEASE_CHECKLIST.md`
- Release-Signierung ist bedingt: `app/build.gradle.kts` liest `keystore.properties` und schlägt schnell fehl, wenn Schlüssel unvollständig sind.

## Projekt-spezifische Konventionen
- Kein DI-Container: ViewModels werden in `MainActivity` über `ViewModelProvider` erstellt.
- UI-Status ist `MutableStateFlow` in ViewModels und wird mit `collectAsStateWithLifecycle` in Screens konsumiert.
- SharedPreferences (`"settings"`) ist ein wichtiger Konfigurationsbus (API-Schlüssel, Provider-Auswahl, Persona-Tuning, Billing-Flags).
- Benutzerseitige Fehler/Status werden über ViewModel-Status (`_errorMessage`, `_statusMessage`) angezeigt und meist deutscher Text.
- Room DB verwendet `fallbackToDestructiveMigration(dropAllTables = true)` in `ChatDatabase`; Schema-Änderungen können lokale Daten löschen.
- Gastdaten-Bereinigung ist explizit in `LocalDataSanitizer.clearGuestSessionData`; füge neue private Schlüssel dort hinzu, falls nötig.

## Integrations-Hotspots
- Provider-Integration lebt in `data/ApiClient.kt` (OpenRouter/Groq/Cerebras/Together/Gemini/Ollama).
- Billing-Produkt-IDs müssen zwischen `MonetizationConfig.kt` und `PlayBillingManager.kt` konsistent bleiben.
- Live-Web-Recherche ist verteilt über `SettingsViewModel` (`live_web_*`, inkl. `live_web_prefer_github`), `ApiManager.runLiveWebResearch`, `ChatViewModel.resolveLiveWebContext` und `functions/index.js`.
- Workspace-/Produktivitäts-Features liegen primär in `SettingsViewModel` (`project_workspaces_json`, `active_workspace_id`, `workspace_chat_filter_enabled`), `ChatViewModel` (Workspace-Bindings + Chatfilter) sowie `ui/screen/MiniAppsScreen.kt` (Mini-Apps V2 Discover + Personalisierung + `PromptLabApp`, `VoiceNotesAiApp`, `SmartWorkspaceApp`, `AutomationBoard`, `KnowledgeVault`).
- Photo-AI-Editing ist dreigeteilt: UI/Flow in `ui/screen/MiniAppsScreen.kt` (`PhotoStudioApp`, Rechtepanel, Undo/Redo), Aktionsausführung in `util/PhotoAiActionExecutor.kt` und Cloud-Transport in `util/PhotoAiCloudClient.kt` (Endpoint/Token aus `settings.photo_ai_cloud_*`, Fallback-Ableitung aus Live-Web-Endpoint).
- Erweiterungs-/Plugin-Basis liegt in `util/WorkspaceExtensions.kt` (Katalog + Capability-Mapping), `ui/viewmodel/ExtensionManagerViewModel.kt` (Install/Enable/Rechte) und `ui/screen/ExtensionManagerScreen.kt` (Management-UI).
- Sprach-/Multimodal-Pipeline ist aufgeteilt: `SettingsViewModel` (`auto_language_detection_enabled`, `local_ocr_enabled`), Parsing/OCR/Language-ID in `MultimodalProcessor.kt`, Prompt-Integration und RAG-Chunking in `ChatViewModel`.
- Kollab-Flow umfasst Rollen + Presence + Queue-Retry + Session-Policies + Workspace-Konflikterkennung (Diff/Smart-Merge) in `CollabViewModel.kt`; Firestore-Regeln bleiben dual zu prüfen (`canWriteMessages`, `canWriteAiMessages`, `canWriteWorkspace`, Teilnehmerprüfungen).
- Dockerisierter Ollama mappt `11435 -> 11434` (`docker-compose.yml`), während App-Standard `http://192.168.178.162:11434/` ist (`SettingsViewModel`/`ChatViewModel`).
- Google-Auth verwendet den Credential-Manager-Flow in `AuthScreen.kt` (`GetGoogleIdOption` + Fallback autorisierte/alle Konten); `AuthViewModel.signOut()` räumt Provider-Sessionzustand via `clearCredentialState()` auf.
- Shared-Core-Logik in `:sharedCore` kapselt plattformneutrale Heuristiken/Utilities (Quick-Action-Interpretation, Workspace-Naming, Send-Dedup, Draft/Workspace-Text-Tools) und wird in `ChatViewModel` + `desktopApp` wiederverwendet.
- Desktop-Produktivpfad: `desktop/DesktopChatGateway.kt` (OpenRouter/Ollama Calls), `desktop/DesktopSettingsStore.kt` (persistente Konfiguration), `desktop/DesktopExtensionCatalog.kt` + `ExtensionRuntimeOrchestrator` (Prompt-Kontext).
- Desktop-Cloudpfad: `desktop/DesktopCloudSyncGateway.kt` (Firebase Auth REST + Token-Refresh + Firestore Sync auf `users/{uid}`), Defaults aus `desktop/DesktopFirebaseConfig.kt`.
- Desktop-Google-Auth: `desktop/DesktopGoogleOAuthGateway.kt` (Loopback-OAuth + PKCE), Token-Bridge zu Firebase via `signInWithIdp`.
- Desktop-Session-Hardening: optional verschluesselte Token-Speicherung (`DesktopCredentialCipher`, `encrypt_cloud_session`), Background-Refresh im `DesktopRoot` und `CloudSessionExpiredException`-basierter Auto-Logout bei abgelaufener Session.
- Desktop-Packaging: `desktopApp/build.gradle.kts` definiert explizite JVM-Module fuer jpackage und nutzt per-user MSI mit Startmenue-Gruppe `BamaChat`; lokale Start-/Smoke-Skripte liegen unter `scripts/start-bamachat-desktop.ps1` und `scripts/desktop-launch-smoke-test.ps1`.

## Agent-Guardrails
- Halte Dokumente ausgerichtet, wenn sich Verhalten ändert: `README.md`, `DEVELOPER_GUIDE.md` und diese Datei.
- Wenn neue Firestore/Storage-Pfade hinzugefügt werden, aktualisiere beide Regeldateien und validiere mit `iac-firebase.ps1`.
- Wenn Auth/Profil-Flow geändert wird, verifiziere `AuthViewModel`, `AuthScreen` und `ProfileScreen` zusammen.
- Wenn Quota/Credits-Verhalten berührt wird, verifiziere `MonetizationConfig`, `ChatViewModel.consumeQuota` und Billing-Callbacks in `SettingsViewModel`.
