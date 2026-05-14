# Mini-Apps & Photo-AI Release Checklist

Diese Checkliste fokussiert die Punkte 2-7 (Qualität, Stabilität, Monitoring, UX-Konsistenz) für `MiniAppsScreen` und `photoEdit`.

## 1. Build + Tests
1. `.\gradlew.bat :app:assembleDebug`
2. `.\gradlew.bat :app:testDebugUnitTest`
3. `.\gradlew.bat :app:assembleDebugAndroidTest`
4. Optional auf Gerät: `.\gradlew.bat :app:connectedDebugAndroidTest`
5. Gesamtgate: `.\gradlew.bat :app:stabilityCheck`

## 2. Photo-AI Cloud Readiness
1. Function deployen: `npx firebase-tools deploy --only functions`
2. Optional Secret für bessere Background-Removal-Qualität setzen:
   - `firebase functions:secrets:set REMOVE_BG_API_KEY`
3. In App-Einstellungen prüfen:
   - `photo_ai_cloud_endpoint` gesetzt
   - optional `photo_ai_cloud_api_token` gesetzt
4. Manuell testen:
   - `Background Remove`
   - `Upscale HD`
   - `Export HD`

## 3. Telemetrie-Validierung
Prüfe in Logcat/Telemetry-Pipeline die Events:
1. `photo_cloud_request`
2. `photo_cloud_success` / `photo_cloud_error`
3. `photo_action_start`
4. `photo_action_timing`
5. `photo_action_success` / `photo_action_error`

## 4. UX-Konsistenz Mini-Apps
1. `Photo Studio`: Status-Banner + Ladebalken sichtbar bei laufender Aktion.
2. `Voice Notes AI`: Status-Banner zeigt Info/Fehler/Erfolg korrekt.
3. `Smart Workspace`: Status-Banner zeigt Validierungs- und Ergebnisstatus.
4. Während laufender Photo-Aktion sind kritische Controls deaktiviert.

## 5. Regression-Fokus
1. Export darf keine Doppelaktionen auslösen.
2. Bei Fehlern darf kein Dauer-Ladezustand hängen bleiben.
3. Mini-Apps Navigation:
   - `Mini-Apps` öffnen
   - `Photo Studio` öffnen
   - `Voice Notes AI` öffnen

