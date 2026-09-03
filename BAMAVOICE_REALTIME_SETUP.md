# BamaVoice Realtime: sichere Einrichtung

## Architektur

```text
Android (Firebase ID-Token)
  -> Firebase Function voiceRealtimeSession
     -> Firebase Admin verifyIdToken(..., checkRevoked = true)
     -> serverseitiges Rate-/Parallel-Limit
     -> OpenAI POST /v1/realtime/client_secrets
  <- kurzlebige Clientberechtigung, Modell, Stimme, Ablauf und Lease-ID

Android (kurzlebige Berechtigung)
  -> native WebRTC-Verbindung zu OpenAI Realtime
     -> Mikrofon- und Antwortaudio direkt per PeerConnection
     -> Transkripte und Zustände über den Data Channel oai-events
```

Der dauerhafte OpenAI-Schlüssel bleibt ausschließlich im Firebase Secret Manager. Android enthält nur die HTTPS-URLs der beiden Functions. Die App speichert die kurzlebige Clientberechtigung weder in `SharedPreferences` noch in Room und schreibt sie nicht in Logs.

## Backend-Schutz

- Nur `POST`; maximal 4 KiB Request-Body; unbekannte Felder werden abgelehnt.
- Firebase ID-Token wird serverseitig geprüft; eine vom Client gelieferte UID wird nie akzeptiert.
- Pro UID maximal eine aktive Sitzung und vier Starts in zehn Minuten.
- Global maximal 60 Starts pro Minute; maximal zehn Function-Instanzen.
- OpenAI-Aufruf mit acht Sekunden Timeout und höchstens zwei begrenzten Versuchen.
- Modell-Allowlist: `gpt-realtime`; Stimmen-Allowlist: `marin`, `cedar`.
- Clientberechtigung: 30 Sekunden Startfenster; Live-Sitzung: maximal 15 Minuten.
- Android beendet eine stille Sitzung zusätzlich nach drei Minuten ohne Aktivität.
- Keine Tools, kein Webzugriff und kein vollständiger Chatverlauf in der Realtime-Session.
- Logs enthalten nur Korrelations-ID, Fehlerkategorie, Modell und Stimme; keine Tokens, Transkripte oder E-Mail-Adressen.

App Check ist derzeit nicht vollständig im Projekt eingerichtet und wird deshalb nicht unvollständig erzwungen. Es bleibt ein zusätzlicher Hardening-Schritt nach einer echten App-Check-Rollout- und Monitoring-Phase.

## Manuelle Aktivierung

Die folgenden Schritte sind absichtlich nicht automatisiert.

1. Backend-Tests ausführen:

   ```powershell
   Set-Location functions
   npm test
   Set-Location ..
   ```

2. Den dauerhaften OpenAI-Schlüssel interaktiv als Firebase-Secret setzen. Den Schlüssel nie in einen Befehl, eine Datei oder den Chat schreiben:

   ```powershell
   npx firebase-tools functions:secrets:set OPENAI_API_KEY --project bamachat-d07fb
   ```

3. Erst nach ausdrücklicher Freigabe nur die beiden Voice-Functions deployen:

   ```powershell
   npx firebase-tools deploy --only functions:voiceRealtimeSession,functions:voiceRealtimeSessionEnd --project bamachat-d07fb
   ```

4. Die vom Deploy ausgegebenen HTTPS-URLs in der benutzerspezifischen Datei `%USERPROFILE%\.gradle\gradle.properties` hinterlegen:

   ```properties
   bamaVoiceRealtimeSessionUrl=https://europe-west1-bamachat-d07fb.cloudfunctions.net/voiceRealtimeSession
   bamaVoiceRealtimeSessionEndUrl=https://europe-west1-bamachat-d07fb.cloudfunctions.net/voiceRealtimeSessionEnd
   ```

   Falls Firebase andere Function-URLs ausgibt, müssen exakt diese ausgegebenen HTTPS-URLs verwendet werden.

5. Android neu bauen und installieren:

   ```powershell
   .\gradlew.bat :app:assembleDebug :app:installDebug --no-daemon
   ```

Ohne beide gültigen HTTPS-URLs bleibt `Live-Unterhaltung` sicher deaktiviert und zeigt den Einrichtungs-Hinweis. Universal- und Lokalmodus funktionieren weiterhin.

## Betrieb

- Backend-Secret-Rotation erfordert keinen Android-Release.
- Ein abgelaufenes Client-Credential wird nie wiederverwendet; ein Reconnect fordert eine neue kurzlebige Berechtigung an.
- Reconnect ist auf zwei Versuche begrenzt. Danach bleibt Textchat verfügbar und die App zeigt einen recoverable Fehler.
- Finale Nutzer- und Assistententranskripte verwenden stabile Realtime-IDs und werden jeweils höchstens einmal lokal gespeichert. Erst danach greift die bestehende optionale Android-Cloud-Synchronisierung.
- Partielle oder unterbrochene Assistentenantworten werden nicht als abgeschlossene Chatnachricht gespeichert.
