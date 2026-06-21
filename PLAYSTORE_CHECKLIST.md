# BamaChat Play Store Submission Checklist

## Source of truth

- Store-Texte: `APPSTORE_DESCRIPTION.md`
- Screenshot-Layouts: `app/src/main/java/com/example/bamachat/ui/screen/PlayStoreScreenshots.kt`
- Legal-Links: `app/src/main/java/com/example/bamachat/util/LegalPolicy.kt`
- Monetarisierung: `app/src/main/java/com/example/bamachat/util/MonetizationConfig.kt`

## Store-Metadaten

- [ ] Titel: `BamaChat - KI-Workspace mit Agenten`
- [ ] Kurzbeschreibung: `Chat, Agenten, MCP-Tools und Teamarbeit mit deinen KI-Modellen in einer App.`
- [ ] Vollbeschreibung direkt aus `APPSTORE_DESCRIPTION.md` übernehmen
- [ ] Kategorie: `Produktivität`
- [ ] Support-E-Mail: `support@bamachat.app`
- [ ] Keine veralteten Preise, Quoten oder Datenschutz-Claims eintragen

## Screenshots und Assets

- [ ] Screenshot 1: KI-Workspace statt nur Chat
- [ ] Screenshot 2: Mehrere Modelle, Agenten und Kollaboration
- [ ] Screenshot 3: Workspaces, MCP, Voice, Bilder und Dateien
- [ ] Screenshot 4: Gastmodus und schneller Einstieg
- [ ] App-Icon in Play-Store-Größe exportiert
- [ ] Feature Graphic erstellt oder bewusst ausgelassen

## Compliance und Legal

- [ ] Datenschutz: `https://bamachat-d07fb.web.app/privacy-policy/`
- [ ] Nutzungsbedingungen: `https://bamachat-d07fb.web.app/terms/`
- [ ] Konto-Löschung: `https://bamachat-d07fb.web.app/delete-account/`
- [ ] Support-Seite: `https://bamachat-d07fb.web.app/support/`
- [ ] Kontolöschung im App-Flow getestet
- [ ] Zielgruppe und Content-Rating in der Play Console anhand der echten Funktionen ausgefüllt

## Berechtigungen sauber erklären

- [ ] `INTERNET`: KI-Provider, Cloud-Sync, Hosting-Inhalte
- [ ] `RECORD_AUDIO`: Spracheingabe und Voice-Features
- [ ] `CAMERA`: Bildaufnahme für multimodale Eingaben
- [ ] `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: optionale lokale Kontext- und Smart-Features
- [ ] `USE_BIOMETRIC`: optionaler Geräteschutz
- [ ] `POST_NOTIFICATIONS`: Status- und Antworthinweise

## Produkt- und Pricing-Konsistenz

- [ ] In-App-Käufe und Abos in der Play Console stimmen mit der App überein
- [ ] Produkt-IDs bleiben konsistent zu `MonetizationConfig.kt`
- [ ] Store-Texte versprechen nicht mehr als der aktuelle Tarif- oder Quotenstand hergibt

## Technische QA vor Upload

- [ ] `.\gradlew.bat :app:assembleDebug`
- [ ] `.\gradlew.bat :app:stabilityCheck`
- [ ] `.\gradlew.bat :app:bundleRelease`
- [ ] Onboarding führt über Consent, dann Welcome und Home-Hub
- [ ] Legal-, Support- und Löschlinks öffnen korrekt
- [ ] Chat-Komponist und Navigation sind auf echten Geräten geprüft
- [ ] Gastmodus, Login, Profil und Rücknavigation funktionieren

## Play-Console-Ablauf

- [ ] App-Eintrag erstellt oder vorhandenen Eintrag aktualisiert
- [ ] Testspur angelegt
- [ ] `app-release.aab` hochgeladen
- [ ] Releasenotes eingetragen
- [ ] Tester oder Testgruppe hinzugefügt
- [ ] Store-Eintrag vollständig ohne Warnungen

## Nach dem Upload

- [ ] Interne oder geschlossene Tests auswerten
- [ ] Crashs und kritische UX-Probleme priorisiert abarbeiten
- [ ] Erst danach gestaffelten Produktions-Rollout starten
