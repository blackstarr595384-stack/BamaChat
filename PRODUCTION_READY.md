# BamaChat Production Readiness

## Fazit

Der aktuelle Stand ist technisch bereit für eine interne oder geschlossene Play-Store-Testphase. Der verbleibende Block ist kein Produktumbau mehr, sondern Release-Ausführung: Assets exportieren, signiertes Bundle bauen, Play-Console-Eintrag befüllen und reale Gerätetests fahren.

## Bereits umgesetzt

- Consent-Route vor dem Welcome-Flow
- Versionierte Legal-Zustimmung als App-State
- Telemetrie-Gating nach Zustimmung
- Vollständige Kontolöschung mit Cloud Function und lokalem Cleanup
- Öffentliche Rechts- und Support-Seiten
- Hilfezentrum mit FAQ für Workspaces, MCP, Kollaboration, Voice, Billing und Datenschutz
- Überarbeiteter Welcome-Screen mit klarerer Positionierung als KI-Workspace
- Play-Store-Screenshot-Layouts mit aktueller Produktbotschaft
- Bereinigter Chat-Komponist für bessere Lesbarkeit und Navigation

## Noch manuell zu erledigen

1. Screenshots aus `PlayStoreScreenshots.kt` exportieren.
2. App-Icon und optional Feature Graphic finalisieren.
3. `.\gradlew.bat :app:bundleRelease` erfolgreich durchlaufen lassen.
4. Metadaten aus `APPSTORE_DESCRIPTION.md` in die Play Console übernehmen.
5. Interne oder geschlossene Testspur mit echten Geräten und Testkonten prüfen.

## Release-Gate

Vor jeder Store-Einreichung:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:stabilityCheck
.\gradlew.bat :app:bundleRelease
```

## Kritische Prüfpunkte

- Legal- und Support-Links öffnen die öffentlichen Hosting-Seiten.
- Kontolöschung ist aus dem Profil erreichbar und arbeitet Ende-zu-Ende.
- Der Chat funktioniert auf dem Zielgerät ohne überdeckte Inhalte oder verlorene Navigation.
- Monetarisierungs-Text im Store widerspricht nicht `MonetizationConfig.kt`.
- Keine veralteten Claims wie exakte Preise, feste Gratis-Limits oder absolute Datenschutz-Aussagen in Store-Texten verwenden.

## Risiken und Hinweise

- Die öffentliche Rechtsdokumentation läuft aktuell über `https://bamachat-d07fb.web.app/...`.
- Falls später `bamachat.app` produktiv genutzt werden soll, muss das Domain-Mapping sauber über Firebase Hosting und DNS erfolgen.
- Das Release-Signing schlägt bewusst früh fehl, wenn Schlüssel oder Properties unvollständig sind.

## Empfohlene Go-live-Reihenfolge

1. Technische Verifikation mit `stabilityCheck`
2. Screenshot-Export und Asset-Feinschliff
3. Signiertes AAB bauen
4. Interne oder geschlossene Testspur veröffentlichen
5. Feedback, Crashs und kritische UX-Probleme abarbeiten
6. Erst danach Produktions-Rollout starten
