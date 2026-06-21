# BamaChat Play Store Upload Guide

## 1. Marketing-Material vorbereiten

Textquelle:

- `APPSTORE_DESCRIPTION.md`

Screenshot-Quelle:

- `app/src/main/java/com/example/bamachat/ui/screen/PlayStoreScreenshots.kt`

Empfohlene Screenshot-Dateien:

1. `1-hero.png`
2. `2-workspace-features.png`
3. `3-tools-and-multimodal.png`
4. `4-get-started.png`

Die Screenshots können direkt aus der Compose-Preview in Android Studio gespeichert werden.

## 2. Release vorab verifizieren

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:stabilityCheck
```

Ohne grünen `stabilityCheck` kein Store-Upload.

## 3. Signiertes AAB bauen

Voraussetzungen:

- `keystore.properties` ist vollständig
- Release-Signing ist in `app/build.gradle.kts` korrekt verdrahtet

Build:

```powershell
.\gradlew.bat :app:bundleRelease
```

Ausgabe:

- `app/build/outputs/bundle/release/app-release.aab`

## 4. In die Play Console hochladen

1. App in der Play Console öffnen
2. Interne oder geschlossene Testspur auswählen
3. Neues Release erstellen
4. `app-release.aab` hochladen
5. Titel, Kurzbeschreibung und Vollbeschreibung aus `APPSTORE_DESCRIPTION.md` übernehmen
6. Screenshots und Icon hochladen
7. Rechtslinks und Support-Daten eintragen

Aktuelle Rechtslinks:

- Datenschutz: `https://bamachat-d07fb.web.app/privacy-policy/`
- Terms: `https://bamachat-d07fb.web.app/terms/`
- Konto-Löschung: `https://bamachat-d07fb.web.app/delete-account/`
- Support: `https://bamachat-d07fb.web.app/support/`
- Support-E-Mail: `support@bamachat.app`

## 5. Sinnvolle Releasenotes

Beispiel für den aktuellen Stand:

```text
Neuer Consent- und Legal-Flow, öffentliche Rechtstexte, verbesserte Kontolöschung,
überarbeiteter Hilfe-Bereich und deutlich stabilerer Chat-Komponist für den Alltag.
```

## 6. Finaler Upload-Check

- Der Build basiert auf dem aktuellen Produktstand
- Der Store-Text widerspricht nicht den Tarifen oder Quoten
- Alle öffentlichen Links funktionieren
- Die Screenshots zeigen die aktuelle App und keine alten UI-Zustände
- Die Testspur enthält mindestens ein reales Gerät und einen kompletten Smoke-Test
