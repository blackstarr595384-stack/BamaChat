# BamaChat AGENTS Leitfaden

- GitHub Intelligence Phase 7.6b darf lokal nur deterministische Umsetzungspläne für unveränderte `proposal-<64 hex>`-Parser-IDs vorbereiten. Die `plan-<20 hex>`-ID bindet den vollständigen freigegebenen Planinhalt über eine versionierte, UTF-8-längengebundene Kodierung, Validierungen werden exakt aus den Modulpfaden abgeleitet, und direkte Git-/Gradle-/Maven-/Shell-/Netzwerk-/Interpreter-Befehle sind in Änderungsschritten verboten; Cancellation endet vor `SERVER_ACCEPTED`. Ein echter Draft-PR-Auftrag benötigt einen separat geprüften BamaWorker mit serverseitiger GitHub App; der aktuelle Android-Gateway bleibt ohne Server deaktiviert und enthält weder GitHub-Token noch direkten Schreibzugriff.

## Große Architekturübersicht
- Dreimodulige App-Basis: Android-Hauptapp (`:app`) mit Kotlin + Compose + MVVM, Desktop-Client (`:desktopApp`) auf Compose Multiplatform (Windows Stage 6) und Shared-Core (`:sharedCore`) fuer plattformneutrale Logik; Android-Einstieg ist `MainActivity` + `BamaChatApplication` (Android 13+, minSdk 33).
- Navigation ist zentralisiert in `ui/screen/BamaChatApp.kt`; Auth-Status steuert Routen (`WELCOME`/`AUTH`/`HOME_HUB`/`CHAT` etc.).
- Home-Hub Verständlichkeitsmodus: `SettingsViewModel.simpleModeEnabled` (`settings.simple_mode_enabled`) steuert die reduzierte Einstiegskachel-Auswahl im `HomeHubScreen`.
- `ChatViewModel` ist der Orchestrierungskern: AI-Provider-Routing, Streaming, Persona-Logik, Quotas/Paywall, Benachrichtigungen, multimodaler Import und Cloud-Persona-Sync.
- Für installierbare Workspace-Plugins gibt es einen separaten Flow aus `ExtensionManagerViewModel` + `ExtensionManagerScreen` + `util/WorkspaceExtensions.kt` (Katalog, Capabilities, Persistenz); aktive Extensions werden im `ChatViewModel` turn-basiert in den Runtime-Kontext injiziert. Zusätzlich steuert die Composer-Quick-Action (`Auto`/`Research`/`Code Review`/`Plan`) den Extension-Modus pro Nachricht.
- GitHub Intelligence (`data/github/*`, `ui/viewmodel/GitHubIntelligenceViewModel.kt`, `ui/screen/GitHubIntelligenceScreen.kt`, `shared/core/github/*`) ist strikt read-only: nur das öffentliche Allowlist-Repository `blackstarr595384-stack/BamaChat`, nur GET ohne Auth-Header, ausschließlich reguläre Git-Blobs mit SHA-Abgleich, Credential-Redaction, begrenzte Textsnapshots und feste Untrusted-Content-Grenzen.
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
- Lokaler Secure-Proxy (`/api/chat`): `npm install && npm run dev` (Root, Token via `.env`)
- Lint-Berichtspfad: `app/build/reports/lint-results-debug.html`
- Android UI Smoke-Test APK: `.\gradlew.bat :app:assembleDebugAndroidTest`; Geräteausführung: `:app:connectedDebugAndroidTest`
- Firebase IaC-Wrapper: `.\scripts\iac-firebase.ps1 -Environment dev -Action check|rules|indexes|all`
- Functions Deploy (Live-Web-Proxy): `npx firebase-tools deploy --only functions`
- Optional Secure-Proxy Deploy (Vercel): Serverless Handler unter `api/chat.js` + `api/health.js`
- Mini-Apps/Photo-AI Release-Checkliste: `MINIAPPS_RELEASE_CHECKLIST.md`
- Release-Signierung ist bedingt: `app/build.gradle.kts` liest `keystore.properties` und schlägt schnell fehl, wenn Schlüssel unvollständig sind.

## Projekt-spezifische Konventionen
- Android verwendet Hilt fuer `Application`, `Activity` und `ViewModel` Wiring; Screens beziehen ViewModels ueber `hiltViewModel()`.
- UI-Status ist `MutableStateFlow` in ViewModels und wird mit `collectAsStateWithLifecycle` in Screens konsumiert.
- SharedPreferences (`"settings"`) ist ein wichtiger Konfigurationsbus (API-Schlüssel, Provider-Auswahl, Persona-Tuning, Billing-Flags).
- Benutzerseitige Fehler/Status werden über ViewModel-Status (`_errorMessage`, `_statusMessage`) angezeigt und meist deutscher Text.
- Room DB nutzt explizite Migrationen in `ChatDatabase` (kein `fallbackToDestructiveMigration`); fehlende Migrationen schlagen bewusst fehl statt lokale Daten zu loeschen.
- Gastdaten-Bereinigung ist explizit in `LocalDataSanitizer.clearGuestSessionData`; füge neue private Schlüssel dort hinzu, falls nötig.
- Plugin- und Dependency-Versionen liegen zentral in `gradle/libs.versions.toml`; maschinenbezogene Gradle-/AGP-Overrides bleiben in `%USERPROFILE%/.gradle/gradle.properties` oder `local.properties`.
- Lint-Ausnahmen liegen bewusst in `app/lint.xml`: `AndroidGradlePluginVersion` ist wegen Toolchain-Pin auf AGP 8.7.3 ignoriert; `TrustAllX509TrustManager` nur fuer externes `bcpkix`-Jar (pdfbox-Transitiv).

## Integrations-Hotspots
- Provider-Integration lebt in `data/ApiClient.kt` (OpenRouter/OpenCode/Groq/Cerebras/Together/Gemini/Ollama).
- OpenCode nutzt standardmäßig die Zen-API (`https://opencode.ai/zen/v1/`, `/messages`, `x-api-key`); Legacy-Endpoint `https://api.opencode.ai/v1/` gilt als veraltet.
- Billing-Produkt-IDs müssen zwischen `MonetizationConfig.kt` und `PlayBillingManager.kt` konsistent bleiben.
- Cloud-Voice läuft zentral über `util/CloudVoiceManager.kt`; die Android-Settings halten den Provider kompakt (`ElevenLabs` oder `Piper`) und zeigen nur provider-spezifische Felder.
- Live-Web-Recherche ist verteilt über `SettingsViewModel` (`live_web_*`, inkl. `live_web_prefer_github`), `ApiManager.runLiveWebResearch`, `ChatViewModel.resolveLiveWebContext` und `functions/index.js`.
- Chat-Bildgenerierung ist getrennt von Bildanalyse: Modus `settings.image_generation_mode` (`Externer Bilddienst`/`Deaktiviert`) in `SettingsViewModel` + `SettingsDialog`, Ausführung in `ChatViewModel.generateImage`; bei leerem Prompt/deaktiviertem Modus/Providerfehler keine kaputte Bildkarte speichern und Quota erst nach erfolgreicher Bild-URL-Auflösung verbrauchen.
- Optionaler lokaler Chat-Proxy fuer API-Key-Abschirmung liegt in `server.js` (Root, Endpoint `/api/chat`).
- Vercel-Proxy-Variante fuer `/api/chat` liegt in `api/chat.js` (CORS/Origin-Checks + optional `x-proxy-token`).
- Workspace-/Produktivitäts-Features liegen primär in `SettingsViewModel` (`project_workspaces_json`, `active_workspace_id`, `workspace_chat_filter_enabled`), `ChatViewModel` (Workspace-Bindings + Chatfilter) sowie `ui/screen/MiniAppsScreen.kt` (Mini-Apps V2 Discover + Personalisierung + `PromptLabApp`, `VoiceNotesAiApp`, `SmartWorkspaceApp`, `AutomationBoard`, `KnowledgeVault`).
- Build-Toolchain ist derzeit auf AGP 8.7.3 + Kotlin 2.1.21 + Gradle 8.10.2 + compile/targetSdk 35 ausgerichtet (`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`).
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
