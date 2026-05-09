# BamaChat Developer Guide

Diese Anleitung ist für dich als Entwickler gedacht: Setup, Architektur, täglicher Workflow, Tests, Firebase, Release-Vorbereitung und Troubleshooting.

## 1. Zielbild
BamaChat ist als modulare KI-App aufgebaut, in der Personas, Sprachfunktionen, Bildverarbeitung und Lern-/Memory-Funktionen zusammenarbeiten.  
Die aktuelle Basis ist stabil genug für iteratives Produkt-Building, noch vor finalem Play-Store-Rollout.

## 2. Tech Stack
- Sprache: Kotlin
- UI: Jetpack Compose + Navigation
- State: ViewModels + `StateFlow`
- DB lokal: Room
- Netzwerk: Retrofit + OkHttp
- KI-Provider: OpenRouter, Gemini, Ollama (+ Fallback-Strategie)
- Live-Web-Recherche: Firebase Function `webSearch` als sicherer Internet-Proxy für Agenten
- Firebase:
  - Auth
  - Firestore
  - Storage
  - Analytics
  - Crashlytics
- Billing: Google Play Billing

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
```

## 4. Lokales Setup
## Voraussetzungen
- Android Studio (aktuell, stable)
- JDK 11 oder höher
- Android SDK + Emulator/Device

## Initial Build
```powershell
.\gradlew.bat :app:assembleDebug
```

## Voller Stabilitätscheck
```powershell
.\gradlew.bat :app:stabilityCheck
```

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
```

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

## Voice
- Lokale TTS/STT in `ChatScreen.kt`
- Cloud-Voice in `CloudVoiceManager.kt`

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
  - Viewer ist read-only (kein Schreiben von Nachrichten)
- Multimodal Advanced (Feature 8, MVP):
  - `MultimodalProcessor.kt`
  - Bild/Screenshot, Text-Dokumente, DOCX/XLSX-Basis
  - Lokale OCR für Bildtexte (optional per Setting)
  - PDF-Textlayer (PDFBox Android)
  - Audio/Video-Transkription via `AudioTranscriptionManager.kt` (Groq Whisper API)
  - OCR-Fallback für PDF-Scans (ML Kit Text Recognition)
  - Video-Keyframe-Pipeline in `VideoKeyframeExtractor.kt`

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

Hinweis: DB-Version ist auf den aktuellen Stand angehoben, Migration aktuell destruktiv (`fallbackToDestructiveMigration`).

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
- ElevenLabs Key/Voice-ID prüfen.
- Bei Cloud-Ausfall fallbackt App auf lokale TTS.

## 13. Security-Hinweise
- Keine API-Keys hardcoden.
- Nutzer- und Dokumentdaten nur owner-basiert zugreifen lassen.
- Vor produktivem Rollout Rules mit echten Testkonten validieren.

## 14. Release-Vorbereitung (später)
1. Crashlytics-Dashboards beobachten.
2. Analytics-Events prüfen (Onboarding, Retention, Voice, Bildanalyse).
3. Proguard/Minify mit Testflight intern validieren.
4. Datenschutztexte + Consent-Flows finalisieren.
5. Erst danach Play-Store-Publishing.
