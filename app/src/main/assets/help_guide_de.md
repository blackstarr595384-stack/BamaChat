# BamaFlow Anleitung

BamaFlow ist dein KI-Arbeitsbereich für Chat, Projektkontext, Dateien, Sprache, Bildfunktionen und Zusammenarbeit. Diese Anleitung ist bewusst in der App verfügbar, damit du keine Webseite brauchst.

## 1. Start in 30 Sekunden

1. Öffne den Chat.
2. Wähle bei Bedarf eine Persona oder lasse den aktuellen Stil aktiv.
3. Schreibe dein Ziel in einem Satz.
4. Ergänze Format, Länge oder nächsten Schritt.
5. Verfeinere das Ergebnis mit Rückfragen.

Beispiel:
"Erstelle mir einen Plan in 5 Schritten und gib am Ende eine kurze Checkliste."

## 2. Chat, Personas und Schnellaktionen

Im Chat ist BamaFlow dein direkter Arbeitsbereich.

- **Personas** bestimmen Stil und Perspektive der Antwort.
- **Nachricht** ist dein normaler Prompt.
- **Mikrofon** startet Spracheingabe.
- **Plus-Menü** öffnet Upload, Kamera und Bildgenerierung.
- **Schnellaktionen** wie Auto, Research, Code Review oder Plan geben der Antwort einen Arbeitsmodus.

Typische Personas:

- **Assistent**: schnell, klar, alltagstauglich
- **Entwickler**: Code, Fehlersuche, Architektur
- **Lehrer**: didaktisch, ruhig, Schritt für Schritt
- **Reflexion**: gesprächsorientiert und empathisch

Bessere Ergebnisse bekommst du, wenn du nennst:

- dein Ziel
- das gewünschte Format
- die Tiefe oder Länge
- wichtige Einschränkungen

## 3. Bildfunktionen im Chat

BamaFlow unterscheidet zwei Bildfunktionen:

### Bild hochladen / analysieren

Über das Plus-Menü kannst du ein Bild hochladen oder ein Foto aufnehmen. BamaFlow analysiert dann den Inhalt, kann OCR-Text auslesen und deine Frage dazu beantworten.

Beispiele:

- "Beschreibe dieses Bild."
- "Lies den Text im Screenshot aus."
- "Was ist auf dem Diagramm wichtig?"

### Bild generieren

Über **Plus-Menü > Bild generieren** kann BamaFlow aus deinem Prompt ein neues Bild erzeugen.

So funktioniert es:

1. Schreibe zuerst eine klare Bildbeschreibung in das Eingabefeld.
2. Tippe auf **Plus > Bild generieren**.
3. Warte, bis der externe Bilddienst erreichbar ist und ein Bild liefert.

Gute Prompts enthalten:

- Motiv: "eine Katze auf einem Schreibtisch"
- Stil: "realistisch", "Anime", "Logo", "Produktfoto"
- Format: "Portrait", "Wallpaper", "Banner"
- Stimmung/Farben: "warm", "dunkel", "minimalistisch"

Wichtig:

- Bildgenerierung braucht einen erreichbaren Bilddienst.
- Wenn der externe Dienst Zahlung/Auth verlangt oder nicht erreichbar ist, zeigt BamaFlow eine Fehlermeldung statt einer kaputten Bildkarte.
- Wenn du noch keinen eigenen Dienst hast, kannst du die Funktion in **Einstellungen > KI & Modelle** deaktivieren und später wieder aktivieren.

## 4. Workspaces erstellen und nutzen

Workspaces helfen dir, Chats und Projektkontext sauber zu trennen.

So erstellst du einen Workspace:

1. Öffne **Einstellungen**.
2. Gehe zu **Workspaces & Automationen**.
3. Trage unter **Neuer Workspace** einen Namen ein.
4. Tippe auf **Workspace erstellen**.
5. Aktiviere den Workspace über **Aktivieren**.

Was der Workspace macht:

- Der aktive Workspace ist dein aktueller Projektkontext.
- Chats können auf den aktiven Workspace gefiltert werden.
- Schnellaktionen und Tool-Bestätigungen können diesen Kontext nutzen.
- In der Live-Zusammenarbeit gibt es zusätzlich einen gemeinsamen Workspace-Text für das Team.

Praxis-Tipp:
Lege pro Kunde, Projekt oder Thema einen eigenen Workspace an, statt alles in einem Chat zu sammeln.

## 5. MCP-Tools verbinden und nutzen

MCP steht für externe Tools, die über die App angesprochen werden können.

Wichtig auf Android:

- Lokale `npx`-basierte MCP-Server laufen auf Android nicht direkt.
- Auf Android ist dafür die **Remote MCP Bridge** gedacht.
- Desktop-Setups können zusätzlich lokale Server haben.

So richtest du MCP auf Android ein:

1. Öffne **Einstellungen**.
2. Gehe zu **KI & Modelle**.
3. Trage unter **Remote MCP Bridge** eine **Remote MCP URL** ein.
4. Falls nötig, hinterlege einen **Bridge Token**.
5. Aktiviere im Bereich **MCP Server** den gewünschten Server.
6. Prüfe die Liste **Verfügbare MCP-Tools**.

Woran du erkennst, dass MCP bereit ist:

- Der Server steht auf **Verbunden**.
- Unter **Verfügbare MCP-Tools** werden echte Tool-Namen angezeigt.

## 6. Live-Zusammenarbeit starten

Live-Zusammenarbeit ist für gemeinsame Sessions mit mehreren Teilnehmern gedacht.

So startest du eine neue Session:

1. Öffne **Live-Zusammenarbeit**.
2. Trage einen **Session-Namen** ein.
3. Tippe auf **Neue Session erstellen**.
4. Kopiere danach Link, Session-ID oder Invite-Code.
5. Teile die Daten nur mit Personen, denen du vertraust.

So tritt jemand einer Session bei:

1. Öffne **Live-Zusammenarbeit**.
2. Trage **Session-ID oder Invite-Link** ein.
3. Optional: Invite-Code eintragen.
4. Tippe auf **Session beitreten**.

## 7. Rollen, Rechte und eingeladene Teilnehmer

In gemeinsamen Sessions gibt es Rollen und Regeln.

- **Owner**: verwaltet Session, Invite-Code und Rechte
- **Editor**: kann je nach Regel schreiben, KI starten und Workspace bearbeiten
- **Viewer**: kann lesen, aber nicht aktiv schreiben

Wichtige Klarstellung:

- **Gastmodus** ist lokal zum Testen auf diesem Gerät.
- **Eingeladene Teilnehmer** in einer Session sind nicht dasselbe wie lokaler Gastmodus.
- Für echte geräteübergreifende Zusammenarbeit solltest du ein Konto verwenden.
- Der lokale Dev-Modus ist nur für Tests gedacht.

Wenn du unsicher bist, gib neue Personen zuerst nur als Viewer frei.

## 8. Gemeinsam mit KI-Agenten arbeiten

Im Bereich Live-Zusammenarbeit kannst du mit mehreren KI-Agenten für das Team arbeiten.

So funktioniert es:

1. Öffne eine laufende Session.
2. Wähle unter **Agenten für KI-Hilfe wählen** einen oder mehrere Agenten aus.
3. Schreibe eine Nachricht oder nutze den aktuellen Workspace-Text als Kontext.
4. Tippe auf **KI-Team-Antwort**.
5. Die Antwort erscheint als KI-Nachricht in der gemeinsamen Session.

## 9. Gemeinsamer Workspace und Konflikte

Der Bereich **Gemeinsamer Workspace** ist eine synchronisierte Live-Notiz für die Session.

Du kannst dort:

- Anforderungen sammeln
- Aufgaben strukturieren
- Zwischenstände dokumentieren
- Prompt-Kontext für das KI-Team vorbereiten

Wenn zwei Personen gleichzeitig ändern, zeigt BamaFlow Konflikt-Hinweise an.
Dann stehen je nach Situation Aktionen wie diese bereit:

- **Remote laden**
- **Smart Merge**
- **Merge speichern**
- **Lokal erzwingen**

Nutze Merge, wenn beide Seiten wichtige Teile beigetragen haben.

## 10. Dateien, Bilder und multimodaler Kontext

Unterstützte Inhalte:

- **Dokumente**: TXT, MD, CSV, JSON, DOCX, XLSX, PDF
- **Bilder/Screenshots**: Analyse, OCR, Kontext
- **Audio/Video**: je nach aktivierter Pipeline und Einstellungen

Praxis-Tipps:

- Große Inhalte in Abschnitte teilen.
- Dateikontext immer mit einer klaren Aufgabe kombinieren.
- Bei Screenshots genau sagen, was geprüft werden soll.
- Keine privaten API-Keys, Passwörter oder sensiblen Kundendaten unnötig teilen.

## 11. Sprache und Voice

Für Spracheingabe braucht BamaFlow Mikrofonzugriff.

Wenn Sprache nicht reagiert:

1. Prüfe die Android-Mikrofonberechtigung.
2. Prüfe, ob ein anderer Recorder aktiv ist.
3. Starte die App neu.
4. Teste alternativ normale Texteingabe.

Cloud-Voice und TTS stellst du in den Einstellungen ein.

## 12. So bekommst du bessere Antworten

Guter Prompt:

"Ich arbeite an einer Android-App. Prüfe diesen Fehler, nenne die wahrscheinlichste Ursache und gib mir drei sichere nächste Schritte. Antworte kurz und auf Deutsch."

Noch besser:

- Kontext nennen
- Ziel nennen
- gewünschtes Format nennen
- Einschränkungen nennen
- Beispiel oder Datei anhängen

## 13. Sicherheit und Datenschutz

Achte besonders auf:

- API-Keys nur in Einstellungen hinterlegen, nicht in Prompts schreiben.
- Invite-Codes nur an vertrauenswürdige Personen geben.
- Sensible Inhalte vor Upload prüfen.
- Gastdaten nicht mit echten Kontodaten vermischen.
- Konto löschen nur ausführen, wenn du sicher bist.

## 14. Keine Homepage oder Social-Links?

Das ist kein Problem. Diese Hilfe ist lokal in der App verfügbar und kann über **Teilen** oder **Speichern** exportiert werden.

Empfehlung bis öffentliche Seiten vorhanden sind:

- Keine Platzhalter-Links als echte Social-Links anzeigen.
- Support-Hinweise in der App halten.
- Öffentliche Webseite, Impressum, Datenschutz und Community später ergänzen.
- In der App nur Links anzeigen, die wirklich existieren.

## 15. Troubleshooting

### Chat antwortet nicht

- Prüfe Internetverbindung.
- Prüfe API-Key und Provider in **Einstellungen > KI & Modelle**.
- Wechsle testweise auf einen anderen Provider.
- Prüfe, ob Quota/Credits erschöpft sind.

### Bildgenerierung funktioniert nicht

- Prüfe, ob du zuerst einen Bildprompt eingegeben hast.
- Prüfe **Einstellungen > KI & Modelle > Bildgenerierung im Chat**.
- Wenn der externe Bilddienst Zahlung/Auth verlangt, kann BamaFlow kein Bild erzeugen.
- In diesem Fall zeigt die App eine Fehlermeldung und speichert keine kaputte Bildkarte.
- Später kann ein eigener Bildproxy oder ein anderer Bildanbieter ergänzt werden.

### Bildanalyse funktioniert nicht

- Prüfe, ob du wirklich ein Bild hochgeladen oder aufgenommen hast.
- Prüfe Bildgröße und Format.
- Prüfe Provider/API-Key für multimodale Analyse.
- Teste ein kleineres Bild.

### Live-Zusammenarbeit verbindet nicht

- Prüfe Anmeldung oder Dev-Testmodus.
- Prüfe Session-ID, Invite-Link und Invite-Code.
- Prüfe Internetverbindung.
- Erstelle testweise eine neue Session.

### MCP-Tools erscheinen nicht

- Prüfe Remote MCP URL.
- Prüfe Bridge Token.
- Prüfe, ob die Bridge online ist.
- Aktualisiere die Tool-Liste.

### Spracheingabe reagiert nicht

- Prüfe Mikrofonberechtigung.
- Prüfe, ob ein anderes Mikrofon-Tool aktiv ist.
- Nutze testweise Texteingabe.

## 16. FAQ

### Brauche ich ein Konto?

Für lokales Testen reicht der Gastmodus. Für Sync, Profil, Konto-Funktionen und echte Zusammenarbeit mit anderen Geräten ist ein Konto sinnvoll.

### Was ist der Unterschied zwischen Bildanalyse und Bildgenerierung?

Bildanalyse erklärt ein vorhandenes Bild. Bildgenerierung erstellt ein neues Bild aus Text.

### Warum funktioniert Bildgenerierung manchmal nicht?

Die aktuelle Chat-Bildgenerierung hängt von einem externen Bilddienst ab. Wenn dieser Dienst Zahlung/Auth verlangt, nicht erreichbar ist oder blockiert, kann kein Bild erzeugt werden. Die App zeigt dann eine verständliche Fehlermeldung.

### Wo finde ich Hilfe, wenn es noch keine Webseite gibt?

Direkt hier in der App. Du kannst diese Anleitung über **Teilen** verschicken oder über **Speichern** als Markdown-Datei exportieren.

### Wie funktionieren Billing und Credits?

In **Einstellungen > KI & Modelle** siehst du Plan, Billing-Status und Credits. Je nach Plan sind bestimmte Aktionen begrenzt.

### Wie lösche ich mein Konto?

Die Kontolöschung startest du im Profil. Prüfe vorher, ob du wichtige Daten exportieren möchtest.

## 17. Anleitung teilen oder speichern

Im Hilfe-Bildschirm kannst du diese Anleitung:

- über **Teilen** an andere Apps senden
- über **Speichern** als Markdown-Datei sichern

So hast du die wichtigsten Schritte auch ohne öffentliche Webseite griffbereit.
