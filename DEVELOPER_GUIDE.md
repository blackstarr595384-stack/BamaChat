# BamaChat Developer Guide

Diese Anleitung ist für dich als Entwickler gedacht: Setup, Architektur, täglicher Workflow, Tests, Firebase, Release-Vorbereitung und Troubleshooting.

## 1. Zielbild
BamaChat ist als modulare KI-App aufgebaut, in der Personas, Sprachfunktionen, Bildverarbeitung und Lern-/Memory-Funktionen zusammenarbeiten.  
Die aktuelle Basis ist stabil genug für iteratives Produkt-Building, noch vor finalem Play-Store-Rollout.

## 2. Tech Stack
- Sprache: Kotlin
- UI: Jetpack Compose + Navigation
- DI: Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`)
- Desktop UI (Windows Client): Compose Multiplatform Desktop (`:desktopApp`)
- State: ViewModels + `StateFlow`
- DB lokal: Room
- Netzwerk: Retrofit + OkHttp
- KI-Provider: OpenRouter, OpenCode, Gemini, Ollama (+ Fallback-Strategie)
  - OpenCode-Default: Zen-API `https://opencode.ai/zen/v1/` (`/messages`, Header `x-api-key`, Modell-Default `claude-sonnet-4-5`)
- Live-Web-Recherche: Firebase Function `webSearch` als sicherer Internet-Proxy für Agenten
- Firebase:
  - Auth
  - Firestore
  - Storage
  - Analytics
  - Crashlytics
- Billing: Google Play Billing
- Target Device Range: Android 13+ (minSdk 33)
- Android Build-Toolchain: AGP 8.7.3, Kotlin 2.1.21, Gradle 8.10.2, compile/targetSdk 35
- Hinweis zu iOS: benötigt separaten Client (z. B. KMP/Flutter oder native iOS-App)

## 3. Projektstruktur (relevant)
```text
app/src/main/java/com/example/bamachat
  MainActivity.kt
  BamaChatApplication.kt
  data/
    api/
    local/
    model/
    repository/
  ui/
    screen/
    viewmodel/
    theme/
  util/

desktopApp/
  src/main/kotlin/com/example/bamachat/desktop/DesktopMain.kt

sharedCore/
  src/main/kotlin/com/example/bamachat/shared/core/*
```

Aktuelle Shared-Core-Bausteine:
- `PromptDrafts` (Draft-Erstellung + Entduplizierung)
- `WorkspaceTextToolkit` (Summary + Action-Item-Extraktion)
- `QuickActionInterpreter` (AUTO/RESEARCH/CODE_REVIEW/PLAN Heuristik)
- `WorkspaceNaming` (Workspace-Tagging/Normalisierung)
- `ChatSendDeduplicator` (Send-Dedup-Fenster)
- `ExtensionRuntimeOrchestrator` (Quick-Action + Extension-Hinweise fuer Runtime-Prompts)
- `shared/core/github/*` (plattformneutrale Allowlist, Pfad-/Groessenlimits, Snapshot-Modelle und Untrusted-Context-Grenzen fuer GitHub Intelligence)

Desktop-Client relevante Klassen:
- `desktop/DesktopMain.kt` (Shell + Chat/Workspace/Settings Screens)
- `desktop/DesktopChatGateway.kt` (OpenRouter/Ollama HTTP-Calls)
- `desktop/DesktopSettingsStore.kt` (persistente Settings unter `%USERPROFILE%/.bamachat-desktop/settings.properties`)
- `desktop/DesktopCredentialCipher.kt` (optionale AES-GCM Verschluesselung von Session-Tokens)
- `desktop/DesktopExtensionCatalog.kt` (Desktop-seitige Extension-Auswahl fuer Runtime-Kontext)
- `desktop/DesktopFirebaseConfig.kt` (Default-Resolver aus `app/google-services.json`)
- `desktop/DesktopCloudSyncGateway.kt` (Firebase Auth REST + Firestore Workspace-Sync)
- `desktop/DesktopGoogleOAuthGateway.kt` (Google Browser-OAuth via Loopback + PKCE)

## 4. Lokales Setup
## Voraussetzungen
- Android Studio (aktuell, stable)
- JDK 17 oder höher
- Android SDK + Emulator/Device
- Plugin- und Dependency-Versionen werden zentral in `gradle/libs.versions.toml` gepflegt.
- Host-spezifische Gradle-/AGP-Overrides gehoeren in `%USERPROFILE%/.gradle/gradle.properties`; SDK- oder andere lokale Dateipfade bleiben in `local.properties`.

## Initial Build
```powershell
.\gradlew.bat :app:assembleDebug
```

## Voller Stabilitätscheck
```powershell
.\gradlew.bat :app:stabilityCheck
```

## Optional: Lokaler Secure Chat Proxy
Falls ein Client gegen einen sicheren lokalen Backend-Proxy (`/api/chat`) testen soll:

```powershell
npm install
Copy-Item .env.proxy.example .env
# HF_TOKEN in .env setzen
# optional: PROXY_AUTH_TOKEN setzen
npm run dev
```

Standard-Endpunkte:
- `http://<host>:5173/health`
- `http://<host>:5173/api/chat`

Production (Vercel):
- Serverless Handler: `api/chat.js`, `api/health.js`
- Rewrites: `vercel.json` mapped `/health -> /api/health`
- Pflicht-Env: `HF_TOKEN`, `ALLOWED_ORIGIN`
- Empfehlenswert: `ALLOW_MISSING_ORIGIN=true` fuer native Clients, optional `PROXY_AUTH_TOKEN`
- Optional Abuse-Schutz: `RATE_LIMIT_WINDOW_MS` (default 60000), `RATE_LIMIT_MAX_REQUESTS` (default 30)

## 5. Firebase Setup
## 5.1 `google-services.json`
- Datei nach `app/google-services.json` legen.

## 5.2 Regeln publishen
- Firestore-Regeln aus `firestore.rules`
- Storage-Regeln aus `storage.rules`

Details und Prüfablauf:
- [FIREBASE_SECURITY_SETUP.md](./FIREBASE_SECURITY_SETUP.md)

## 5.3 Crashlytics/Analytics
- Plugins in Root/App-Gradle sind eingebunden.
- Initialisierung läuft in `BamaChatApplication.kt` über `AppTelemetry`.
- Crashlytics ist in Debug standardmäßig deaktiviert, in nicht-debuggable Builds aktiv.

## 6. Build-/Test-Kommandos
## Kern
```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:stabilityCheck
.\gradlew.bat :desktopApp:build
.\gradlew.bat :desktopApp:run
.\gradlew.bat :desktopApp:packageMsi
.\gradlew.bat :sharedCore:test
powershell -ExecutionPolicy Bypass -File .\scripts\start-bamachat-desktop.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\desktop-launch-smoke-test.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\remove-legacy-machine-install.ps1
```

Lint-Hinweise:
- `app/lint.xml` ignoriert `AndroidGradlePluginVersion`, da AGP 8.7.3 als validierte Basis fuer compile/targetSdk 35 gehalten wird.
- `TrustAllX509TrustManager` wird nur fuer das externe `org.bouncycastle:bcpkix-jdk15to18`-Jar ignoriert; App-eigener Netzwerkcode bleibt von der Pruefung erfasst.

Desktop Cloud-Sync (Stage 3):
- Login/Registrierung läuft über Firebase Auth REST (`accounts:signInWithPassword`, `accounts:signUp`).
- Token-Refresh läuft über `securetoken.googleapis.com`.
- Workspace-Notizen werden in Firestore unter `users/{uid}` mit Feldern `desktop_workspace_note`, `desktop_workspace_updated_at`, `desktop_workspace_updated_by` gespeichert.

Desktop Google-Login (Stage 4):
- OAuth 2.0 Browser-Flow mit Loopback Redirect (`http://127.0.0.1:<port>/oauth2callback`) und PKCE.
- Google-ID-Token wird über Firebase Auth REST (`accounts:signInWithIdp`) in eine Firebase-Session überführt.
- Bei Nutzung eines Web-OAuth-Clients kann ein `client_secret` im Desktop-Settings-Screen erforderlich sein.

Desktop Session-Hardening (Stage 5):
- Optional verschluesselte lokale Session-Speicherung (`encrypt_cloud_session`) via `DesktopCredentialCipher` (AES-GCM, Salt unter `%USERPROFILE%/.bamachat-desktop/session_salt.bin`).
- Auto-Refresh der Firebase Session im Desktop-Root vor Ablauf.
- Einheitliche `CloudSessionExpiredException` fuer Refresh-/401-Faelle mit Auto-Logout-Pfad in der UI.

Desktop Packaging-Hardening (Stage 6):
- `desktopApp/build.gradle.kts` setzt explizite Runtime-Module (`java.net.http`, `jdk.httpserver`, `jdk.crypto.ec`, `jdk.unsupported`, `java.naming`), um NoClassDefFoundError in installierten Builds zu vermeiden.
- MSI ist auf `perUserInstall = true` + Startmenue-Gruppe (`BamaChat`) konfiguriert, damit Installation/Update ohne Admin-Rechte moeglich ist.
- `upgradeUuid` ist fixiert fuer konsistente Upgrades innerhalb der per-user Linie.

## AndroidTest APK bauen
```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```

## UI-Tests auf Gerät ausführen (wenn `adb` verfügbar)
```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 6.1 Live-Web-Proxy (Firebase Functions)
Function-Code liegt unter `functions/` (`index.js`, `package.json`).

Setup/Deploy:
```powershell
cd functions
npm install
cd ..
firebase functions:secrets:set WEBSEARCH_TOKEN
firebase functions:secrets:set PHOTO_AI_TOKEN
firebase functions:secrets:set BRAVE_SEARCH_API_KEY
firebase functions:secrets:set GITHUB_TOKEN
npx firebase-tools deploy --only functions
```

App-Konfiguration:
- `settings.live_web_enabled` = `true`
- `settings.live_web_endpoint` = `https://websearch-<hash>-ew.a.run.app` (oder Cloud Functions URL)
- optional: `settings.live_web_api_token` und `settings.live_web_allowed_domains` (CSV)
- optional: `settings.live_web_prefer_github` = `true` (empfohlen für technische Queries)
- optional: `settings.auto_language_detection_enabled` = `true` (ML Kit Language ID für User-Turn-Kontext)
- optional: `settings.local_ocr_enabled` = `true` (OCR-Text aus Bildern in Bildanalyse-Prompt)
- optional: `settings.image_generation_mode` = `Externer Bilddienst` oder `Deaktiviert` (Chat-Bildgenerierung; bei leerem Prompt, deaktiviertem Modus oder Providerfehler keine Bildkarte persistieren)
- optional für Photo AI Cloud:
  - `settings.photo_ai_cloud_endpoint` = `https://europe-west1-<project-id>.cloudfunctions.net/photoEdit`
  - `settings.photo_ai_cloud_api_token` = `<token>` (wenn gesetzt)

Verhalten:
- `ChatViewModel` ruft bei aktuellen/News-ähnlichen Queries den Proxy über `ApiManager` auf.
- Bei `live_web_prefer_github=true` (oder Coding-Query) sendet die App `preferGithub=true`; der Proxy priorisiert dann GitHub-Repos/Issues in den Ergebnissen.
- Treffer werden als Prompt-Kontext injiziert und als Quellenliste an die finale Antwort angehängt.
- Tageslimit läuft über `MonetizationViewModel.QuotaType.WEB_RESEARCH`.

## 7. Aktuelle Kernfeatures im Code
## Chat + Personas
- `ChatViewModel.kt`
- `ChatScreen.kt`

## Auth + Profil
- `AuthViewModel.kt`
- `AuthScreen.kt`
- `ProfileScreen.kt`
- Google-Login nutzt `CredentialManager` (`GetGoogleIdOption`); bei Logout wird `clearCredentialState()` aufgerufen.
- Die Consent-Route (`LegalDisclaimerScreen`) kommt vor `WelcomeScreen` und schaltet Telemetrie erst nach Zustimmung frei (`SettingsViewModel.acceptLegalPolicy()` + `BamaChatApplication`).
- Die finale Konto-Löschung läuft ueber `AuthViewModel.deleteAccount()` und entfernt Auth, Cloud-Daten und lokale Daten; öffentliche Rechtstexte liegen unter `/privacy-policy/`, `/terms/`, `/delete-account/` und `/support/`.

## Voice
- `BamaVoiceSessionController` ist die einzige Autorität für Listening, Transcribing, Thinking, Speaking, Interrupt und Error.
- `BamaVoiceViewModel` besitzt genau eine aktive STT-/TTS-Engine, reagiert auf Providerwechsel und gibt Recognizer/Player beim Verlassen des Chats frei.
- `AndroidSpeechRecognizerEngine` liefert Pegel, Teiltext und genau ein finales Ergebnis. Ein finaler Ergebnis-Timeout verhindert dauerhaftes Transcribing.
- `VoiceTextProcessor` entfernt Markdown, URLs, Zitationsmarker und Codeblöcke; `StreamingSpeechBuffer` gibt vollständige Sätze/Klauseln in stabiler Reihenfolge aus.
- Ausgabefallbacks sind `ElevenLabs` (standardmäßig Flash v2.5), `Piper` und Android TTS. Ein bereits gestarteter Cloud-Chunk wird bei Fehlern nicht erneut über Android vorgelesen.
- `AudioTranscriptionManager` bleibt ausschließlich für importierte Audio-/Videodateien in `MediaService`; die Live-Mikrofonsteuerung verwendet ihn nicht.
- BamaVoice Universal verwendet den bestehenden Textprovider. BamaVoice Local erzwingt On-Device-STT und erlaubt nur Android TTS oder einen privaten Piper-Endpunkt.
- Es gibt keinen Mikrofon-Foreground-Service: Aufnahme ist bewusst auf die sichtbare Chat-Oberfläche beschränkt.

```text
Chat UI -> BamaVoiceViewModel -> BamaVoiceSessionController
                               |-> SpeechToTextEngine (Android)
                               |-> bestehender ChatViewModel-Textstream
                               |-> SpeechOutputEngine (ElevenLabs/Piper/Android)
                               `-> VoiceAudioSession (Playback-Fokus + MODE_IN_COMMUNICATION)
```

Androids `SpeechRecognizer` besitzt den Aufnahmefokus selbst. BamaVoice fordert deshalb beim Listening keinen konkurrierenden App-Audiofokus an; eigener transienter Fokus wird nur für Sprachausgabe gehalten.

### Realtime-Grenze
- `FirebaseRealtimeSessionCredentialProvider` sendet einen aktuellen Firebase ID-Token an `voiceRealtimeSession`; die Function verifiziert die UID serverseitig und liefert nur eine kurzlebige OpenAI-Realtime-Clientberechtigung.
- `OpenAiRealtimeVoiceEngine` besitzt Credential, Reconnect-Grenze und Event-Mapping. `AndroidRealtimePeerConnectionController` stellt natives WebRTC-Audio und den Data Channel `oai-events` bereit; `ChatScreen` steuert WebRTC nie direkt.
- Der dauerhafte OpenAI-Key ist ausschließlich als Firebase-Secret `OPENAI_API_KEY` gebunden. Ohne beide konfigurierten Function-URLs bleibt Live-Unterhaltung deaktiviert; es gibt keinen unsicheren Fallback-Key im APK.
- Live-Transkripte gehen über `BamaVoiceSessionController` in `ChatViewModel.persistRealtimeVoiceTurn`: nur finale, stabile Realtime-IDs werden lokal gespeichert und anschließend an die bestehende opt-in Cloud-Synchronisierung übergeben. Der normale Textprovider und ElevenLabs/Android-TTS werden im Live-Modus nicht parallel gestartet.
- Sessions sind serverseitig auf 15 Minuten und clientseitig zusätzlich auf drei Minuten Inaktivität begrenzt; Reconnect endet nach zwei Versuchen.
- Einrichtung und manuelle Deploy-Schritte stehen in [`BAMAVOICE_REALTIME_SETUP.md`](./BAMAVOICE_REALTIME_SETUP.md).
- Gemini Live und xAI Voice benötigen separate, tatsächlich getestete Adapter und bleiben bis dahin ohne Produktionsbutton.

## Advanced-AI-Basis (Feature 1-8, aktueller MVP-Stand)
- Persistent Memory:
  - Room-Entities: `UserMemoryFactEntity`, `PersonaMemoryEntity`
- Emotion Detection:
  - `EmotionAnalyzer.kt`
- RAG (lokal):
  - `DocumentIngestor.kt`
  - `KnowledgeChunkEntity` + Retrieval im `ChatViewModel`
- Knowledge Graph:
  - `KnowledgeGraphExtractor.kt`
  - `KnowledgeEdgeEntity`
- Fine-Tuning-ähnliches Persona-Training (Feature 5):
  - `PersonaTrainingExampleEntity`
  - Auto-Learning aus Helpful-Feedback
  - Manuelle Trainingseinträge im Persona-Dialog
- Multi-Agent-Orchestrierung (Feature 6):
  - Persona-Erkennung im Prompt
  - Perspektiven + Synthese in `ChatViewModel`
- Realtime Collaboration (Feature 7):
  - `RealtimeCollabScreen.kt`
  - `CollabViewModel.kt`
  - Firestore Collection `collab_sessions/{id}/messages`
  - Presence-Subcollection `collab_sessions/{id}/presence`
  - Rollenmodell (Owner/Editor/Viewer), Owner-Moderation und Invite-Code-Rotation
  - Session-Policy-Felder in `CollabSession` (`aiEnabled`, `editorCanUseAi`, `editorCanSendMessages`, `editorCanEditWorkspace`)
  - Viewer ist read-only (kein Schreiben von Nachrichten)
  - Typing-Indicator (`CollabPresence.typing/draftPreview/cursorIndex`)
  - Offline-Queue + Auto-Retry in `CollabViewModel` (fehlgeschlagene Sends)
  - Workspace-Konfliktbehandlung über `CollabWorkspaceState.revision/baseRevision` inkl. Diff-Preview, Inline-Diff-Highlight und Smart-Merge
- Multimodal Advanced (Feature 8, MVP):
  - `MultimodalProcessor.kt`
  - Bild/Screenshot, Text-Dokumente, DOCX/XLSX-Basis
  - Lokale OCR für Bildtexte (optional per Setting)
  - PDF-Textlayer (PDFBox Android)
  - Audio/Video-Transkription via `AudioTranscriptionManager.kt` (Groq Whisper API)
  - OCR-Fallback für PDF-Scans (ML Kit Text Recognition)
  - Video-Keyframe-Pipeline in `VideoKeyframeExtractor.kt`
- Workspaces & Produktivität (neu):
  - Home Hub startet standardmäßig im einfachen Modus (`settings.simple_mode_enabled`) und kann auf Advanced umgeschaltet werden
  - Workspace-Status in `SettingsViewModel` (`project_workspaces_json`, `active_workspace_id`)
  - Workspace-Sektion in `SettingsDialog.kt`
  - Chat-Titel übernimmt aktiven Workspace (`ChatViewModel.newConversationTitle()`)
  - Optionaler Workspace-Chatfilter in `SettingsViewModel` (`workspace_chat_filter_enabled`) + `ChatScreen`
  - AI-Extensions Manager:
    - Route/Screen: `ExtensionManagerScreen.kt` (über Home Hub)
    - Orchestrierung: `ExtensionManagerViewModel.kt`
    - Katalog + Capability-Persistenz: `WorkspaceExtensions.kt`
    - Guardrail: Aktivierung nur, wenn alle Pflicht-Capabilities freigegeben sind
    - Repo Autopilot 1.1.0: `CHAT_READ` + `GITHUB_READ`, dedizierter unauthentifizierter GET-only-Gateway für das öffentliche Allowlist-Repository, nur reguläre Git-Blobs mit Tree-SHA-Prüfung, Credential-Redaction vor dem AI-Kontext und keine GitHub-Schreiboperationen
    - Runtime-Hook im Chat: `ChatViewModel` lädt aktive Extensions und injiziert turn-basierten Extension-Kontext in `buildOpenRouterHistory` (inkl. optionaler Web-Recherche-Erzwingung)
    - Quick-Action-Steuerung im Eingabefeld (`Auto`, `Research`, `Code Review`, `Plan`) mit Persistenz über SharedPreferences
  - Mini-Apps V2 in `MiniAppsScreen.kt`:
    - Discover-Hub (Suche, Filter, Empfehlungen, zuletzt genutzt)
    - Personalisierung (Favoriten, Ausblenden, Reihenfolge, Swipe-Management)
    - Neue Apps: `PromptLabApp`, `VoiceNotesAiApp`, `SmartWorkspaceApp`, `PhotoStudioApp`
    - `PhotoStudioApp`: Bildimport via Photo Picker, nicht-destruktive Filter-Vorschau, Rotation/Spiegeln/Crop, Undo/Redo, Export via MediaStore
    - Photo-Aktionsschicht: `PhotoAiActionExecutor.kt` kapselt lokale Bildaktionen, Permission-Gating, Risiko-Confirmation und Cloud-Aufrufe für `BackgroundRemove`/`UpscaleHd`
    - Cloud-Client: `PhotoAiCloudClient.kt` (Endpoint-Auflösung, Auth-Header, Base64 I/O, Fehler-Mapping)
    - Backend: Firebase Function `photoEdit` in `functions/index.js` (Cloud-Pipeline für Background Remove + Upscale)
    - Chat-Komposer: Bild hochladen, Kamera-Foto aufnehmen und Bildgenerierung direkt aus dem Mehr-Menü
    - Bildgenerierung läuft über `ChatViewModel.generateImage`; Quota wird erst nach erfolgreicher URL-Auflösung verbraucht, Dienstfehler werden als deutsche Fehlermeldung angezeigt.
    - Detailkarten nutzen kompakte Aktionszeilen statt vieler einzelner Buttons
  - Bestehende Apps weiterhin aktiv: `AutomationBoard` + `KnowledgeVault`
  - Persona-Marketplace in `AgentHubScreen.kt`

## 7b. MCP Integration

### Architektur
- `util/McpClient.kt` — JSON-RPC 2.0 über stdio (lokal) oder HTTP POST (remote)
- `util/McpServerManager.kt` — Multi-Server-Lifecycle, Tool-Registry, `getToolDefinitionsOpenAI()`, Remote-Config-Auflösung via `resolveStartConfig()`
- `util/McpTypes.kt` — Datenmodelle + `defaultMcpServers` Liste
- `util/McpWorkflowManager.kt` / `McpWorkflowTypes.kt` — mehrstufige Tool-Pipelines

### Remote MCP Bridge
Lokale `npx`-Prozesse funktionieren auf Android nicht. Die Remote Bridge ermöglicht die Verbindung zu einem HTTP JSON-RPC Server im Netzwerk oder in der Cloud.

**Wie es funktioniert:**
1. `McpTypes.kt` enthält einen `remote-bridge` Eintrag mit `command = "remote_http"` als Sentinel.
2. Beim Start ruft `McpServerManager.resolveStartConfig()` die echte URL aus `SharedPreferences` (`mcp_remote_url`) ab und den Token aus `SecureSettingsStore` (`mcp_remote_token`).
3. `McpClient.isRemoteTransport()` erkennt `http://`/`https://` und sendet alle JSON-RPC-Requests als OkHttp POST statt über eine Prozess-Pipe.
4. Optionaler Bearer-Token wird als `Authorization`-Header mitgeschickt.

**Einstellungen in der App:**
- `Einstellungen → KI & Modelle → Remote MCP Bridge`
  - **Remote MCP URL** — z. B. `http://192.168.178.162:3000/mcp`
  - **Bridge Token** — optional, für Absicherung

**Relevante Settings-Keys:**
- `settings.mcp_remote_url` — plain SharedPreferences
- `mcp_remote_token` — verschlüsselt in `SecureSettingsStore`

**All-in-One Starter-Server (lokal):**
Fertig vorbereitet unter `C:\Users\Black\mcp-server\`. Starten mit:
```powershell
C:\Users\Black\mcp-server\start.ps1
```
Tools: `read_file`, `write_file`, `list_directory`, `search_files`, `search_in_files`, `run_command`, `web_fetch`, `get_time`, `calculator`.

### Agent Loop
`ChatViewModel.sendChatViaApi` schaltet bei verfügbaren Tools automatisch in den Agent-Modus:
1. Request mit `tools`-Array + `tool_choice: "auto"` an Provider
2. Bei `tool_calls`: Ausführung via `McpServerManager.callTool()` oder `McpWorkflowManager.executeWorkflow()`
3. Ergebnisse als `role: "tool"` zurück an Modell
4. Max. 5 Iterationen bis finale Text-Antwort

## 8. Datenmodell (vereinfacht)
- `conversations`
- `chat_messages`
- `persona_memory`
- `persona_feedback`
- `persona_prompt_versions`
- `user_memory_facts`
- `knowledge_chunks`
- `knowledge_edges`
- `persona_training_examples`

Hinweis: DB-Migrationen laufen explizit über definierte Room-Migrationsschritte (fail-fast bei fehlender Migration, kein automatischer Daten-Reset).

Chat-Datenbesitz:
- Neue Conversations und Messages tragen immer denselben Owner-Scope: `account:<Firebase-UID>` oder `guest:<persistente Session-ID>`.
- Die nichtdestruktive Migration auf Schema 10 erhält vorhandene Chatzeilen als `legacy:unclassified`; diese Altzeilen werden weder automatisch einem Konto/Gast zugeordnet noch zur Cloud hochgeladen.
- Abfragen und Schreibzugriffe laufen ausschließlich im aktiven Scope. Cloud-Sync akzeptiert nur `account:<aktuelle UID>` und bleibt während eines Gast-zu-Konto-Übergangs gesperrt.
- Erst nach bestätigter Authentifizierung entfernt eine Room-Transaktion ausschließlich den aktuellen Guest-Scope. Danach wird der Account-Scope aktiviert und Cloud-Sync freigegeben; Abbruch und Fehler lassen Gastdaten unverändert.

## Release-/Feature-Kommunikation
- Wenn du ein neues User-Feature einbaust, aktualisiere immer direkt `README.md` und diesen Developer Guide.
- Für Release-Notizen nutze pro Feature immer dieselbe Kurzform: "Was ist neu?", "Warum ist es hilfreich?", "Wie nutze ich es?".
- Die wichtigste Produktstory ist aktuell: autonomer Chat-Arbeitsraum, Foto/Kamera im Chat, MCP-/Builtin-Tools, Extensions, Repo-Selbstchecks (Branch/Remote/TODO) und weniger unnötige Buttons.
- Neue autonome Bausteine, Tool-Packs oder Skills gehören zusätzlich in `BUILTIN_TOOLS_GUIDE.md`, damit die Release-Erklärung nicht hinter dem Code zurückbleibt.

## 9. Entwickler-Workflow (empfohlen)
1. Feature-Branch lokal.
2. Kleine, testbare Änderungen.
3. Nach jedem größeren Schritt:
   - `:app:assembleDebug`
   - `:app:testDebugUnitTest`
4. Vor Übergabe:
   - `:app:stabilityCheck`
5. Nur dann auf Device-Tests gehen.

## 10. Bekannte Grenzen aktuell
- Android UI/E2E-Tests sind vorbereitet, aber ohne `adb` auf dieser Shell nicht direkt ausführbar.
- Dokumentimport ist robust für Text/DOCX/XLSX/PDF-Textlayer.
- Audio/Video-Import benötigt gültigen Groq API-Key; ohne Key gibt es absichtliche Guidance statt Silent-Fail.
- OCR-Fallback ist integriert, aber aktuell auf wenige Seiten begrenzt (Performance-Schutz).
- Realtime-Collab benötigt Firebase-Login (Gastmodus ist absichtlich aus Sicherheits-/Regelgründen ausgeschlossen).
- Multi-Agent kostet mehr Tokens/Requests und sollte mit Quotas/Monitoring betrieben werden.

## 11. Nächste sinnvolle Ausbaustufen
1. Training v2: Bewertungs-UI für Trainingsbeispiele, Deaktivieren/Löschen einzelner Samples.
2. Multi-Agent v2: Rollen-Templates (Planner, Critic, Executor) mit Kosten-Limits.
3. Realtime-Collab v4: Session-Policies (z. B. nur Invite-Join), Audit-Log und Soft-Moderation.
4. Multimodal v4: OCR-Qualitätsstufen, Video-Szenen-Segmentierung, erweiterte Dokument-Parser.

## 12. Troubleshooting
## Build-Probleme
- Gradle-Daemon neu starten:
```powershell
.\gradlew.bat --stop
```
- Danach erneut:
```powershell
.\gradlew.bat :app:stabilityCheck
```

## Memory/Out-of-Memory
- `gradle.properties` enthält bereits konservative Limits.
- Bei wenig RAM:
  - Emulator schließen
  - Browser/andere IDEs schließen
  - dann Build erneut starten

## Firebase-Fehler
- Prüfen:
  - `google-services.json` korrekt?
  - App-ID `com.example.bamachat` passt?
  - Firestore/Storage Rules publiziert?

## Voice-Probleme
- ElevenLabs Key/Voice-ID/Model oder Piper Endpoint/Voice prüfen.
- Wenn Cloud Voice explizit aktiviert ist, zeigt die App Provider-Fehler direkt an statt still auf lokale TTS zurueckzufallen.

## 13. Security-Hinweise
- Keine API-Keys hardcoden.
- Nutzer- und Dokumentdaten nur owner-basiert zugreifen lassen.
- Vor produktivem Rollout Rules mit echten Testkonten validieren.

## 14. Release-Vorbereitung (später)
Zusatz für Mini-Apps und Photo-AI:
- [MINIAPPS_RELEASE_CHECKLIST.md](./MINIAPPS_RELEASE_CHECKLIST.md)

1. Crashlytics-Dashboards beobachten.
2. Analytics-Events prüfen (Onboarding, Retention, Voice, Bildanalyse).
3. Proguard/Minify mit Testflight intern validieren.
4. Datenschutztexte + Consent-Flows finalisieren.
5. Erst danach Play-Store-Publishing.
## Phase 7.6b: sichere Draft-PR-Vorbereitung

Die Draft-PR-Erweiterung von GitHub Intelligence liegt in `sharedCore/src/main/kotlin/com/example/bamachat/shared/core/github/AgentDraftPrModels.kt`, `app/src/main/java/com/example/bamachat/data/github/DisabledAgentDraftPrGateway.kt`, `GitHubIntelligenceViewModel` und `GitHubIntelligenceScreen`. Reale `proposal-<64 hex>`-Parser-IDs werden unverändert gebunden; die `plan-<20 hex>`-ID wird zentral über eine versionierte, UTF-8-längengebundene Kodierung aus dem vollständigen fachlichen Planinhalt berechnet und erneut geprüft. Validierungen müssen exakt der zentral aus den betroffenen Modulpfaden abgeleiteten Reihenfolge entsprechen, direkte Git-/Gradle-/Maven-/Shell-/Netzwerk-/Interpreter-Befehle werden abgelehnt und Cancellation ist nur vor `SERVER_ACCEPTED` erlaubt. Ohne nachweisbaren BamaWorker bleibt der Gateway strikt deaktiviert. Niemals GitHub-Tokens, direkte GitHub-Schreibrequests oder Client-seitige Git-Kommandos ergänzen. Der vollständige zukünftige Serververtrag steht in `docs/phase-7.6b-agent-draft-pr.md`.
