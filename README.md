# BamaChat

BamaChat ist primär eine Android-Chat-App (Jetpack Compose) mit Persona-System, Multi-Provider-KI, Sprachfunktionen, Bildanalyse/-generierung, Auth/Profil und erweiterten AI-Features (Training, Multi-Agent, Realtime-Collab, Multimodal-Import). Zusätzlich gibt es einen Windows-Desktop-Client auf Compose Multiplatform sowie ein `sharedCore`-Modul fuer plattformneutrale Logik.

## Status
- Plattform: Android 13+ (Kotlin, Compose, Room, Firebase)
- Windows Desktop Client: Compose Multiplatform Modul `:desktopApp` (Stage 4)
- Shared Core: JVM-Modul `:sharedCore` fuer wiederverwendbare Business-Logik (Drafts, Workspace-Text-Tools, Quick-Action-Heuristiken, Send-Dedup, Workspace-Naming)
- Sprache in der App: primär Deutsch
- Build-Status: `stabilityCheck` erfolgreich (Assemble + Unit Tests + Lint)
- Tablet-Layout: adaptive UI (u. a. Mini-Apps V2)
- iPhone: erfordert separaten iOS-Client (nicht im Android-Projekt enthalten)

## Hauptfunktionen
- Persona-Chat mit editierbaren Prompts und Prompt-Versionierung (Rollback)
- Persona-Cloud-Sync pro Nutzerkonto:
  - Charakterprofil (Empathie/Kreativität/Direktheit + Autonomie)
  - Prompt-Overrides (inkl. Custom Persona Prompt)
  - Trainingsbeispiele pro Persona (Fine-Tuning-MVP)
  - Merge-Logik bei Login/Persona-Wechsel (lokal + cloud)
- Multi-Provider-Flow (OpenRouter, Gemini, Ollama + Fallback-Logik)
- Optionale Live-Web-Recherche für Agenten (über Firebase Function Proxy + Quellenanhang)
- Sprachfeatures:
  - STT (Diktieren)
  - TTS lokal
  - optionale Cloud-Voice (ElevenLabs)
  - Auto-Spracherkennung pro Nachricht (ML Kit Language ID, optional)
- Bildfunktionen:
  - Bild hochladen und analysieren
  - Bildgenerierung
- Auth & Profil:
  - Registrierung/Anmeldung
  - Google Sign-In über Credential Manager (Google ID Token)
  - Gastmodus
  - Profilname + Profilbild (Firebase Storage)
- Monetarisierung-Basis:
  - Free-Quotas
  - Paywall-Struktur
- Neue KI-Basis:
  - Persistent Memory (Nutzer-Fakten + Feedback-Learnings)
  - Emotion Detection
  - Lokales RAG (Dokumentimport + Retrieval)
  - Knowledge-Graph-Extraktion
- Feature 5 (MVP): Fine-Tuning-ähnliches Persona-Training
  - Manuelle Trainingsbeispiele pro Persona
  - Automatische Trainingsbeispiele aus hilfreichem Feedback
  - Few-shot-Einbindung in Systemprompt
- Feature 6 (MVP): Multi-Agent Collaboration
  - Mehrere Personas beantworten dieselbe Aufgabe
  - Automatische Synthese-Antwort
  - Nutzbar über Prompt-Muster mit mehreren Persona-Namen
- Feature 7 (MVP): Realtime Collaboration
  - Gemeinsame Sessions per Session-Code
  - Realtime-Nachrichten via Firestore
  - KI-Team-Antwort in Session (Agenten-Auswahl)
  - Presence (online/offline) pro Teilnehmer
  - Typing-Indicator inkl. Draft-Preview pro Teilnehmer
  - Offline-Queue mit Auto-Retry für fehlgeschlagene Collab-Nachrichten
  - Session-Policies pro Owner (KI an/aus, Editor-Rechte für KI/Nachrichten/Workspace)
  - Workspace-Revisionen mit Konflikthinweis (Remote übernehmen / Merge speichern)
  - Smart-Diff-Preview + Inline-Diff-Highlighter + lokales Erzwingen bei Workspace-Konflikten
  - Owner-Moderation (Teilnehmer entfernen, Session verlassen inkl. Owner-Transfer)
  - Rollenmodell (Owner/Editor/Viewer) inkl. Schreibschutz für Viewer
  - Invite-Code + kopierbarer Invite-Link
- Hinweis: nur für angemeldete Nutzer (nicht Gastmodus)
- Feature 8 (MVP): Multimodal Advanced
  - Bild/Screenshot-Analyse
  - Optional lokale OCR für Bild-Chat-Kontext (ML Kit Text Recognition)
  - Dokumentimport inkl. TXT/MD/CSV/JSON
  - DOCX/XLSX-Extraktion (Basis)
  - PDF-Textlayer-Extraktion (PDFBox Android)
  - Audio/Video-Transkription via Groq Whisper (Import als Wissenschunks)
  - OCR-Fallback für Scan-PDFs (erste Seiten)
  - Video-Keyframe-Textanalyse als zusätzliche Fallback-Pipeline
  - Falls kein Transkript möglich: klare Fehlermeldung/Guidance in der App
- Workspaces & Automationen (neu)
  - Projekt-Workspaces in Einstellungen (aktiv, anlegen, löschen)
  - Neue Chats übernehmen den aktiven Workspace im Titel
  - Optionaler Chatlisten-Filter auf den aktiven Workspace
  - AI-Extensions Manager: installierbare Workspace-Plugins mit Capability-Freigaben (Pflichtrechte-Guardrail vor Aktivierung)
  - Chat-Integration: aktive Extensions werden pro Turn in den Runtime-Kontext eingebunden (inkl. optionalem Live-Web-Trigger für Research-Radar)
  - Quick-Action-Leiste im Composer: `Auto`, `Research`, `Code Review`, `Plan` für schnellere manuelle Steuerung pro Nachricht
  - Automation-Templates (Tagesbriefing, Meeting->ToDos, Release-Check, Risiko-Scan)
  - Persona-Marketplace im Agent Hub (installierbare Preset-Pakete)
  - Mini-Apps V2: Discover-Hub, Favoriten, Ausblenden, Reihenfolge, Swipe-Management
  - Neue Mini-Apps: `Prompt Lab`, `Voice Notes AI`, `Smart Workspace`, `Photo Studio`
  - Photo Studio: Bildimport, Filter-Regler (Helligkeit/Kontrast/Sättigung/Wärme), Rotation/Spiegelung/Crop, Undo/Redo, Galerie-Export
  - Photo Studio Pro-Aktionen: zentrales Action-Backend mit AI-Rechten/Tool-Gating, Risiko-Bestätigung und Cloud-Workflow (`Background Remove`, `Upscale HD`) über `photoEdit` Function Endpoint
  - Bestehende Mini-Apps: `Automation Board` + `Knowledge Vault`
- MCP (Model Context Protocol):
  - stdio-basierte MCP-Client-Integration für lokale Tool-Server
  - Multi-Server-Management mit Start/Stop pro Server in den Einstellungen
  - Automatische Tool-Registry und Konvertierung in OpenAI-Function-Calling-Format
  - Agent Loop: KI kann MCP-Tools autonom aufrufen und Ergebnisse verarbeiten
  - Default-Server: Dateisystem, Web-Suche, Knowledge Graph, Code-Ausführung
- Workflow-Automation:
  - Mehrstufige Tool-Pipelines (z. B. Web-Recherche+Fetch, Code-Lint+Review)
  - Workflows werden als `workflow_<id>`-Tools im Agent-Kontext registriert
  - Step-für-Step-Ausführung mit Kontext-Weitergabe zwischen Steps
- Windows Desktop Client (Stage 2):
  - Produktiver Chat mit OpenRouter oder lokalem Ollama
  - Persistente Desktop-Einstellungen (`%USERPROFILE%/.bamachat-desktop/settings.properties`)
  - Shared-Core Quick-Actions + Extension-Runtime-Kontext im Chat
- Windows Desktop Client (Stage 3):
  - Firebase E-Mail/Passwort-Login im Desktop-Settings-Screen
  - Token-Refresh über Firebase Secure Token API
  - Cloud-Sync für Workspace-Notizen nach Firestore (`users/{uid}`)
- Windows Desktop Client (Stage 4):
  - Google-Login per Browser-OAuth (Loopback + PKCE) und Firebase `signInWithIdp`
  - Konfigurierbare Google OAuth Client-ID/Secret im Settings-Screen
- Windows Desktop Client (Stage 5):
  - Automatischer Background-Refresh der Firebase Session vor Token-Ablauf
  - Klarere Session-Expired-Behandlung (Auto-Logout bei nicht erneuerbarer Session)
  - Optionale lokale Verschluesselung von ID-/Refresh-Token in `settings.properties`
- Windows Desktop Client (Stage 6):
  - Windows-Paketierung mit expliziten JVM-Modulen (`java.net.http`, `jdk.httpserver`, `jdk.crypto.ec`, `jdk.unsupported`, `java.naming`)
  - Startmenue-Integration (`BamaChat` Gruppe) + Shortcut-Option
  - Per-User-Installation fuer stabile Install/Reinstall ohne Admin-Rechte

## MCP (Model Context Protocol) Integration

BamaChat unterstützt das stdio-basierte MCP-Protokoll zur Anbindung lokaler Tools und Datenquellen.

### Architektur
- `util/McpClient.kt` — JSON-RPC 2.0 Client über stdio-Prozesskommunikation
- `util/McpServerManager.kt` — Multi-Server-Management, Tool-Registry, Konvertierung in OpenAI-kompatibles Tool-Format (`getToolDefinitionsOpenAI()`)
- `util/McpTypes.kt` — Datenmodelle (`McpServerConfig`, `McpToolDefinition`, `McpToolCall`, `McpToolResult`)
- `util/McpWorkflowManager.kt` — Workflow-Engine für mehrstufige Tool-Pipelines

### Standard-MCP-Server
Vorkonfigurierte Default-Server in `McpTypes.kt`:
- **Dateisystem** (`@modelcontextprotocol/server-filesystem`)
- **Web-Suche** (`@anthropic-ai/mcp-server-web-search`)
- **Knowledge Graph** (`@modelcontextprotocol/server-memory`)
- **Code-Ausführung** (`@anthropic-ai/mcp-server-code-executor`)

Server können in den Einstellungen (KI & Modelle → MCP Server) aktiviert/deaktiviert werden.

### Agent Loop (Tool-Calling)
Wenn MCP- oder Workflow-Tools verfügbar sind, schaltet `sendChatViaApi` automatisch in den Agent-Modus:
1. Request mit `tools`-Array und `tool_choice: "auto"` an den Provider
2. Bei `tool_calls` in der Antwort: Ausführung via `McpServerManager.callTool()` oder `McpWorkflowManager.executeWorkflow()`
3. Tool-Ergebnisse werden als `role: "tool"`-Messages zurückgegeben
4. Wiederholung bis max. 5 Iterationen oder finale Text-Antwort

### Workflows
Definierte Abläufe in `McpWorkflowManager`:
- **Web-Recherche & Zusammenfassung**: `web_search` → `web_fetch`
- **Code-Review-Pipeline**: `read_file` → `execute_command`

Workflows werden als `workflow_<id>`-Tools im Agent-Kontext registriert und können vom KI-Modell wie normale Tools aufgerufen werden.

## Schnellstart
## Voraussetzungen
- Android Studio (neuere stabile Version)
- JDK 11+ (Projekt nutzt Toolchain 11)
- Android SDK/Build Tools installiert

## Projekt starten
```powershell
.\gradlew.bat :app:assembleDebug
```

APK liegt danach unter:
- `app/build/outputs/apk/debug/`

## Qualitätscheck
```powershell
.\gradlew.bat :app:stabilityCheck
```

Enthält:
- `assembleDebug`
- `testDebugUnitTest`
- `lintDebug`

## Desktop Client starten (Windows)
```powershell
.\gradlew.bat :desktopApp:run
```

Optionales Paket fuer Windows:
```powershell
.\gradlew.bat :desktopApp:packageMsi
```

Installierte App starten (bevorzugt per-user Install):
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-bamachat-desktop.ps1
```

Desktop-Start Smoke-Test:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\desktop-launch-smoke-test.ps1
```

Optional: Legacy-Machine-Installationen (Program Files) bereinigen:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\remove-legacy-machine-install.ps1
```

## Shared Core Tests
```powershell
.\gradlew.bat :sharedCore:test
```

## Firebase einrichten
1. Firebase-Projekt anlegen/verwenden.
2. Android-App `com.example.bamachat` registrieren.
3. `google-services.json` nach `app/google-services.json` legen.
4. Firestore- und Storage-Rules veröffentlichen:
   - `firestore.rules`
   - `storage.rules`

Details: [FIREBASE_SECURITY_SETUP.md](./FIREBASE_SECURITY_SETUP.md)

## Firebase IaC ohne Plugin
Damit du ohne Cloud-IaC-Plugin sauber arbeiten kannst, ist ein kleines IaC-Setup enthalten:
- `firebase.json` (zentraler Deploy-Plan)
- `firestore.rules`
- `storage.rules`
- `firestore.indexes.json`
- `infra/firebase/environments.json`
- `scripts/iac-firebase.ps1`

### Einmaliger Check
```powershell
.\scripts\iac-firebase.ps1 -Environment dev -Action check
```

### Nur Security Rules deployen
```powershell
.\scripts\iac-firebase.ps1 -Environment dev -Action rules
```

### Nur Firestore Indexes deployen
```powershell
.\scripts\iac-firebase.ps1 -Environment dev -Action indexes
```

### Alles deployen (Rules + Indexes)
```powershell
.\scripts\iac-firebase.ps1 -Environment dev -Action all
```

Hinweis:
- `infra/firebase/environments.json` steuert die Projekt-IDs (`dev`/`prod`).
- Vor `prod`-Deploy zuerst `dev` testen.

## Live-Web-Recherche aktivieren (Agenten mit aktuellen Quellen)
1. Firebase Functions Dependencies installieren:
```powershell
cd functions
npm install
cd ..
```
2. Optional Secrets setzen (empfohlen):
```powershell
firebase functions:secrets:set WEBSEARCH_TOKEN
firebase functions:secrets:set BRAVE_SEARCH_API_KEY
firebase functions:secrets:set GITHUB_TOKEN
```
3. Function deployen:
```powershell
npx firebase-tools deploy --only functions
```
4. In der App unter `Einstellungen -> KI & Modelle`:
- `Live-Web-Recherche` aktivieren
- `Web-Search Function URL` eintragen (z. B. `https://websearch-<hash>-ew.a.run.app` oder `https://europe-west1-<project>.cloudfunctions.net/webSearch`)
- Optional `Function Access Token` und `Domain-Allowlist` setzen
- Optional `GitHub bevorzugen` aktivieren (empfohlen für Coding/Repo/Issue-Fragen)
- Optional `Auto-Spracherkennung` aktivieren
- Optional `Lokale OCR für Bilder` aktivieren

Hinweise:
- Ohne `BRAVE_SEARCH_API_KEY` nutzt die Function DuckDuckGo als Fallback.
- Mit `GITHUB_TOKEN` werden GitHub-Recherchetreffer (Repos/Issues) stabiler und mit höherem Rate-Limit geliefert.
- Web-Recherche hat eigenes Tageslimit (`webSearchRequests`) + optionalen Credit-Fallback.

Wichtiger Hinweis zu Persona-Cloud-Sync:
- Firestore-Rules müssen die folgenden Subcollections unter `users/{uid}` erlauben:
  - `persona_profiles`
  - `persona_prompts`
  - `persona_training_examples`
- In diesem Projekt sind diese Regeln bereits in `firestore.rules` enthalten.
- Nach Änderungen immer deployen:
```powershell
npx firebase-tools deploy --only firestore:rules
```

## Multimodal v2 aktivieren
- In den Einstellungen einen `Groq API-Key` hinterlegen (für Audio/Video-Transkription).
- PDF-Extraktion ist durch `pdfbox-android` bereits im Build integriert.
- Ohne Groq-Key funktionieren Bild-/Text-/DOCX-/XLSX-/PDF-Importe weiterhin, Audio/Video dann nur mit Hinweis.

## Wichtige Dateien
- App-Einstieg:
  - `app/src/main/java/com/example/bamachat/MainActivity.kt`
  - `app/src/main/java/com/example/bamachat/BamaChatApplication.kt`
- Navigation:
  - `app/src/main/java/com/example/bamachat/ui/screen/BamaChatApp.kt`
- Chat:
  - `app/src/main/java/com/example/bamachat/ui/screen/ChatScreen.kt`
  - `app/src/main/java/com/example/bamachat/ui/viewmodel/ChatViewModel.kt`
  - `app/src/main/java/com/example/bamachat/ui/screen/ExtensionManagerScreen.kt`
  - `app/src/main/java/com/example/bamachat/ui/viewmodel/ExtensionManagerViewModel.kt`
  - `app/src/main/java/com/example/bamachat/ui/screen/RealtimeCollabScreen.kt`
  - `app/src/main/java/com/example/bamachat/ui/viewmodel/CollabViewModel.kt`
- Auth/Profil:
  - `app/src/main/java/com/example/bamachat/ui/viewmodel/AuthViewModel.kt`
  - `app/src/main/java/com/example/bamachat/ui/screen/AuthScreen.kt`
  - `app/src/main/java/com/example/bamachat/ui/screen/ProfileScreen.kt`
- Daten/DB:
  - `app/src/main/java/com/example/bamachat/data/local/`
  - `app/src/main/java/com/example/bamachat/data/repository/ChatRepository.kt`
- Telemetrie:
  - `app/src/main/java/com/example/bamachat/util/AppTelemetry.kt`
- AI-Utilities:
  - `app/src/main/java/com/example/bamachat/util/EmotionAnalyzer.kt`
  - `app/src/main/java/com/example/bamachat/util/MemoryFactExtractor.kt`
  - `app/src/main/java/com/example/bamachat/util/KnowledgeGraphExtractor.kt`
  - `app/src/main/java/com/example/bamachat/util/WorkspaceExtensions.kt`
  - `app/src/main/java/com/example/bamachat/util/MultimodalProcessor.kt`
  - `app/src/main/java/com/example/bamachat/util/McpClient.kt`
  - `app/src/main/java/com/example/bamachat/util/McpServerManager.kt`
  - `app/src/main/java/com/example/bamachat/util/McpTypes.kt`
  - `app/src/main/java/com/example/bamachat/util/McpWorkflowManager.kt`
  - `app/src/main/java/com/example/bamachat/util/McpWorkflowTypes.kt`

## E2E-Smoke-Test (Basis)
- Datei:
  - `app/src/androidTest/java/com/example/bamachat/AppE2ESmokeTest.kt`
- Build der AndroidTest-APK:
```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```

Hinweis: Für echte Ausführung ist ein verbundenes Gerät/Emulator + `adb` nötig.

## UI-Screenshot-Workflow (GitHub Actions)
- Workflow:
  - `.github/workflows/ui-screenshots.yml`
- Screenshot-Test:
  - `app/src/androidTest/java/com/example/bamachat/AppScreenshotCaptureTest.kt`
- Start:
  - GitHub → `Actions` → `UI Screenshots` → `Run workflow`
- Ergebnis:
  - Artifact `ui-screenshots` mit PNGs für Home/Chat/Profil/Einstellungen

## Dokumentation für Entwickler
Die komplette technische Entwickleranleitung ist hier:
- [DEVELOPER_GUIDE.md](./DEVELOPER_GUIDE.md)
- [MINIAPPS_RELEASE_CHECKLIST.md](./MINIAPPS_RELEASE_CHECKLIST.md)

## Play-Store Preflight (Stand: 02.05.2026)
### Technischer Status
- Debug-Build: erfolgreich
- Release-Build: erfolgreich
- Release-Bundle (`.aab`): erfolgreich erstellt
- Instrumentation Smoke-Test: erfolgreich
- Unit Tests: erfolgreich
- Lint: keine Errors, nur externe Library-Warnungen

### Aktuelle Release-Artefakte
- AAB: `app/build/outputs/bundle/release/app-release.aab` (~73.91 MB)
- APK: `app/build/outputs/apk/release/app-release-unsigned.apk` (~143.31 MB)

Wichtig:
- Für Play Console ist das `.aab` relevant.
- Das Release-APK ist aktuell `unsigned` und nicht für direkten Rollout gedacht.

### Versionierung vor jedem Upload
In `app/build.gradle.kts`:
- `versionCode` bei jedem Upload erhöhen (z. B. 2, 3, 4, ...)
- `versionName` sinnvoll mitziehen (z. B. `1.0.1`, `1.1.0`)

### Signierung (Upload Key) vorbereiten
1. Upload-Key erzeugen (einmalig):
```powershell
keytool -genkeypair -v -keystore bamachat-upload-key.jks -alias bamachat-upload -keyalg RSA -keysize 4096 -validity 10000
```
2. In der Play Console **Play App Signing** aktivieren.
3. Upload-Zertifikat (SHA-1/SHA-256) in Firebase/Google APIs hinterlegen.
4. Optional: `keystore.properties` lokal anlegen und Release-Signing automatisieren.

Automatisches Signing ist im Projekt bereits vorbereitet:
1. `keystore.properties.example` nach `keystore.properties` kopieren.
2. Werte ausfüllen (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
3. `storeFile` als relativen Pfad vom Projektroot setzen (z. B. `bamachat-upload-key.jks`).
4. Release bauen:
```powershell
.\gradlew.bat :app:bundleRelease
```

### Berechtigungen & Data-Safety-Abgleich
Manifest-Berechtigungen in der App:
- `INTERNET`, `ACCESS_NETWORK_STATE`: API/Cloud-Kommunikation
- `RECORD_AUDIO`: Voice/Diktierfunktionen
- `POST_NOTIFICATIONS`: optionale Benachrichtigungen
- `USE_BIOMETRIC`: optionaler App-Lock
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`: Standortfunktion (wenn genutzt)
- `VIBRATE`, `WAKE_LOCK`: System-/Background-Unterstützung

Vor Store-Upload:
- Im Play Console Data-Safety-Formular exakt diese Datenflüsse abbilden.
- Nicht genutzte Berechtigungen entfernen, um Review-Risiko zu senken.

### Go-Live Checkliste (Final)
1. `versionCode` erhöhen.
2. `versionName` aktualisieren.
3. `:app:assembleRelease` erfolgreich.
4. `:app:bundleRelease` erfolgreich.
5. `:app:lintRelease` ohne Errors.
6. `:app:testDebugUnitTest` erfolgreich.
7. `:app:connectedDebugAndroidTest` erfolgreich.
8. Firebase Rules veröffentlicht (`firestore.rules`, `storage.rules`).
9. Gast-/Login-/Profilbild-Flow manuell auf Gerät geprüft.
10. Voice (STT/TTS/Cloud-Voice) manuell geprüft.
11. Bild-/Dokument-/Multimodal-Import manuell geprüft.
12. Crashlytics aktiv und empfangsbereit geprüft.
13. Data-Safety-Formular in Play Console ausgefüllt.
14. Privacy Policy URL bereitgestellt.
15. App-Icon, Screenshots, Kurz-/Langbeschreibung final.
16. Internen Test-Track zuerst nutzen (nicht sofort Production).

### Aktuelle bekannte Restpunkte
- Lint-Warnungen stammen aus externer Dependency (`bouncycastle`) und nicht aus App-Code.
- `android:allowBackup="true"` ist aktiv. Für striktere Datenschutz-Policy ggf. auf `false` setzen.

## Monetarisierung (Live-Konfig)
- Zentrale Config: `app/src/main/java/com/example/bamachat/util/MonetizationConfig.kt`
- Subscription Produkt-ID: `pro_monthly_799`
- Subscription Produkt-ID: `expert_monthly_1999`
- Credit Produkt-ID: `credits_100`
- Credit Produkt-ID: `credits_300`
- Credit Produkt-ID: `credits_1000`
