# BamaChat Play-Store-Dokumente

## Empfohlene Reihenfolge

1. `QUICKSTART_PLAYSTORE.md`
2. `PLAYSTORE_CHECKLIST.md`
3. `PLAYSTORE_UPLOAD_GUIDE.md`
4. `APPSTORE_DESCRIPTION.md`
5. `PRODUCTION_READY.md`

## Source of truth

- Store-Texte: `APPSTORE_DESCRIPTION.md`
- Screenshot-Layouts: `app/src/main/java/com/example/bamachat/ui/screen/PlayStoreScreenshots.kt`
- Live-Rechtslinks: `app/src/main/java/com/example/bamachat/util/LegalPolicy.kt`
- Produkt- und Tariflogik: `app/src/main/java/com/example/bamachat/util/MonetizationConfig.kt`
- Android-Release-Konfiguration: `app/build.gradle.kts`

## Aktueller Stand

- Consent-, Legal- und Welcome-Flow sind in der App verdrahtet.
- Telemetrie wird erst nach Zustimmung aktiviert.
- Kontolöschung ist lokal und per Cloud Function umgesetzt.
- Öffentliche Datenschutz-, Support-, Terms- und Löschseiten sind live.
- Die Play-Store-Screenshot-Composables sind vorhanden und inhaltlich aktualisiert.
- Der Chat-Composer wurde für den Geräteeinsatz bereinigt.

## Manuelle Go-live-Aufgaben

1. Vier Screenshots aus `PlayStoreScreenshots.kt` exportieren.
2. App-Icon und optional eine Feature Graphic finalisieren.
3. Signiertes Release-Bundle mit `.\gradlew.bat :app:bundleRelease` bauen.
4. Store-Metadaten aus `APPSTORE_DESCRIPTION.md` in die Play Console übernehmen.
5. Interne oder geschlossene Testspur anlegen und auf echten Geräten testen.

## Wichtige Hinweise

- Für neue Releases die `web.app`-Rechtslinks verwenden, solange `bamachat.app` nicht sauber auf Firebase Hosting zeigt.
- Keine festen Preise, Quoten oder Datenschutz-Claims in Marketing-Texte schreiben, wenn sie nicht exakt dem aktuellen Produktstand entsprechen.
- Für Google Play das Release-Bundle (`.aab`) verwenden, nicht die Debug- oder Unsigned-APK.
