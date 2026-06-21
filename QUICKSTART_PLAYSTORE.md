# BamaChat Play Store Schnellstart

## Ziel

In 30 bis 45 Minuten den aktuellen Build für eine interne oder geschlossene Testspur vorbereiten.

## 1. Produktstand verifizieren

Lokal ausführen:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:stabilityCheck
```

Danach auf einem echten Gerät prüfen:

- Onboarding führt zuerst auf den Legal-Screen und erst danach weiter.
- Datenschutz, Terms, Support und Konto-Löschung lassen sich aus der App öffnen.
- Kontolöschung funktioniert vollständig.
- Chat-Komponist, Grabber und untere Navigation verhalten sich wie vorgesehen.
- Gastmodus, Anmeldung und Rückweg zum Home-Hub funktionieren.

## 2. Store-Assets vorbereiten

Textquelle:

- `APPSTORE_DESCRIPTION.md`

Screenshot-Quelle:

- `app/src/main/java/com/example/bamachat/ui/screen/PlayStoreScreenshots.kt`

Empfohlene Screenshot-Dateien:

1. `1-hero.png`
2. `2-workspace-features.png`
3. `3-tools-and-multimodal.png`
4. `4-get-started.png`

Die Bilder können direkt aus der Android-Studio-Preview exportiert und unter `app/store_listings/de-DE/screenshots/` abgelegt werden.

## 3. Signiertes Release-Bundle bauen

Vorher prüfen:

- `keystore.properties` ist vollständig.
- Release-Signing in `app/build.gradle.kts` ist korrekt konfiguriert.

Build:

```powershell
.\gradlew.bat :app:bundleRelease
```

Ergebnis:

- `app/build/outputs/bundle/release/app-release.aab`

## 4. In die Play Console eintragen

In der Play Console übernehmen:

- Titel und Kurzbeschreibung aus `APPSTORE_DESCRIPTION.md`
- Vollbeschreibung aus `APPSTORE_DESCRIPTION.md`
- Kategorie: Produktivität
- Datenschutzerklärung: `https://bamachat-d07fb.web.app/privacy-policy/`
- Nutzungsbedingungen: `https://bamachat-d07fb.web.app/terms/`
- Konto-Löschung: `https://bamachat-d07fb.web.app/delete-account/`
- Support: `https://bamachat-d07fb.web.app/support/`
- Support-E-Mail: `support@bamachat.app`

Anschließend:

1. Interne oder geschlossene Testspur erstellen.
2. `app-release.aab` hochladen.
3. Releasenotes ergänzen.
4. Testerkreis hinzufügen.

## Häufige Fehler

`bundleRelease` schlägt fehl:
- `keystore.properties` prüfen.

Falsche Rechtslinks im Store:
- Nur die `web.app`-Links verwenden, solange keine funktionierende Custom Domain live ist.

Veraltete Store-Texte:
- Immer `APPSTORE_DESCRIPTION.md` als Quelle verwenden.

APK statt Bundle hochgeladen:
- Für Google Play das `.aab` verwenden.
