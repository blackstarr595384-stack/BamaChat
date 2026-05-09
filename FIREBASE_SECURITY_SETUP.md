# Firebase Security Setup (BamaChat)

## Ziel
Diese Regeln sichern Auth, Profilbild, Memory/RAG-Daten und Realtime-Collab-Sessions (inkl. Presence + Moderation + Rollen-Schreibschutz) so ab, dass nur berechtigte Nutzer lesen/schreiben können.

## Dateien
- `firestore.rules`
- `storage.rules`

## In Firebase Console anwenden
1. Firebase Console öffnen.
2. Projekt `BamaChat` auswählen.
3. **Firestore Database** -> **Rules** -> Inhalt von `firestore.rules` einfügen -> Publish.
4. **Storage** -> **Rules** -> Inhalt von `storage.rules` einfügen -> Publish.

## Kurzprüfung
1. User A anmelden -> Profilbild hochladen -> sollte funktionieren.
2. User B anmelden -> direkten Zugriff auf A-Bild versuchen -> muss verweigert werden.
3. Firestore `users/{uid}` mit fremder UID lesen/schreiben -> muss verweigert werden.
4. Collab-Session erstellen und mit zweitem Account beitreten -> Nachrichten in Echtzeit sichtbar.
5. Presence prüfen: beide Teilnehmer sehen Online-Status unter `collab_sessions/{id}/presence`.
6. Owner entfernt Teilnehmer B -> B darf Session/Nachrichten/Presence nicht mehr lesen.
7. Rolle `VIEWER` vergeben -> User darf lesen, aber keine neue Nachricht schreiben.
8. Dritter, nicht teilnehmender Account darf Session/Nachrichten/Presence nicht lesen.

## Hinweis
- Nach jeder Rule-Änderung immer neu `Publish`, sonst laufen App und Console-Stand auseinander.
- Wenn du später neue Collections oder Storage-Pfade einführst, Regeln explizit erweitern.
