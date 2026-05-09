# BamaChat AGENTS Leitfaden

## Große Architekturübersicht
- Einmodulige Android-App (`:app`) mit Kotlin + Compose + MVVM; App-Einstieg ist `MainActivity` + `BamaChatApplication`.
- Navigation ist zentralisiert in `ui/screen/BamaChatApp.kt`; Auth-Status steuert Routen (`WELCOME`/`AUTH`/`HOME_HUB`/`CHAT` etc.).
- `ChatViewModel` ist der Orchestrierungskern: AI-Provider-Routing, Streaming, Persona-Logik, Quotas/Paywall, Benachrichtigungen, multimodaler Import und Cloud-Persona-Sync.
- Lokale Persistenz verwendet Room (`data/local/*`) über `ChatRepository`; AI-Netzwerkaufrufe werden absichtlich in `ChatViewModel` durchgeführt (siehe Repo-Kommentar in `ChatRepository.kt`).
- Cloud-Status ist aufgeteilt:
  - Benutzereigene Dokumente unter `users/{uid}` (+ `persona_profiles`, `persona_prompts`, `persona_training_examples`).
  - Realtime-Kollaboration unter `collab_sessions/{sessionId}` mit `messages` und `presence` Unterkollektionen.
- Telemetrie läuft über `util/AppTelemetry.kt`; Crashlytics ist in debugfähigen Builds deaktiviert (`BamaChatApplication.kt`).

## Kritische Workflows
- Debug-Build: `.\gradlew.bat :app:assembleDebug`
- Vollständiges Smoke-Gate: `.\gradlew.bat :app:stabilityCheck` (assemble + Unit-Tests + Lint)
- Lint-Berichtspfad: `app/build/reports/lint-results-debug.html`
- Android UI Smoke-Test APK: `.\gradlew.bat :app:assembleDebugAndroidTest`; Geräteausführung: `:app:connectedDebugAndroidTest`
- Firebase IaC-Wrapper: `.\scripts\iac-firebase.ps1 -Environment dev -Action check|rules|indexes|all`
- Functions Deploy (Live-Web-Proxy): `npx firebase-tools deploy --only functions`
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
- Sprach-/Multimodal-Pipeline ist aufgeteilt: `SettingsViewModel` (`auto_language_detection_enabled`, `local_ocr_enabled`), Parsing/OCR/Language-ID in `MultimodalProcessor.kt`, Prompt-Integration und RAG-Chunking in `ChatViewModel`.
- Kollab-Berechtigungen erfordern duale Updates: `CollabViewModel.kt` Rollenlogik + `firestore.rules` (`canWriteMessages`, Teilnehmerprüfungen).
- Dockerisierter Ollama mappt `11435 -> 11434` (`docker-compose.yml`), während App-Standard `http://192.168.178.162:11434/` ist (`SettingsViewModel`/`ChatViewModel`).

## Agent-Guardrails
- Halte Dokumente ausgerichtet, wenn sich Verhalten ändert: `README.md`, `DEVELOPER_GUIDE.md` und diese Datei.
- Wenn neue Firestore/Storage-Pfade hinzugefügt werden, aktualisiere beide Regeldateien und validiere mit `iac-firebase.ps1`.
- Wenn Auth/Profil-Flow geändert wird, verifiziere `AuthViewModel`, `AuthScreen` und `ProfileScreen` zusammen.
- Wenn Quota/Credits-Verhalten berührt wird, verifiziere `MonetizationConfig`, `ChatViewModel.consumeQuota` und Billing-Callbacks in `SettingsViewModel`.
