# Builtin-Tools & Agent-Mode – Kurzanleitung

Der Agent-Mode erlaubt BamaChat, Tools **automatisch** auszuführen – ohne dass du einen externen MCP-Server installieren musst.

## So aktivierst du den Agent-Mode

1. In einem Chat die **Quick-Action** (neben dem Senden-Button) auf **`Auto`** stellen
2. Einfach losschreiben, z.B.: *"Was ist das Wetter in Berlin?"* oder *"Erstelle eine Notizdatei"*
3. Der Agent entscheidet selbst, ob und welches Tool er nutzt

## Verfügbare Builtin-Tools (keine Installation nötig)

Diese Tools sind der Autonomie-Kern der App. Sie machen BamaChat eher zu einem kleinen Arbeitsagenten als zu einem reinen Chatfenster.

| Tool | Zweck | Beispiel-Prompt |
|------|-------|----------------|
| `web_fetch` | Webseiten-Inhalt abrufen | "Hol den Inhalt von https://example.com" |
| `web_search` | Websuche | "Suche nach aktuellen KI-News" |
| `now` | Aktuelle Uhrzeit/Datum | "Wie spät ist es?" |
| `read_file` | Datei aus der App-Sandbox lesen | "Lies meine Notizen" |
| `write_file` | Neue Datei erstellen | "Schreibe eine Einkaufsliste" |
| `edit_file` | Text in Datei ersetzen | "Ändere 'Milch' zu 'Hafermilch'" |
| `list_files` | Ordnerinhalt anzeigen | "Was ist im Ordner notes?" |
| `search_files` | Dateien nach Namen oder Inhalt durchsuchen | "Finde alle Dateien mit 'release' im Inhalt" |
| `copy_file` | Datei oder Ordner kopieren | "Kopiere notes/todo.txt nach archive/todo.txt" |
| `move_file` | Datei oder Ordner verschieben/umbenennen | "Verschiebe draft.txt nach done/draft.txt" |
| `delete_file` | Datei oder Ordner löschen | "Lösche die Datei test.txt" |
| `run_terminal` | Shell-Befehl ausführen | "Was ist im aktuellen Ordner?" |
| `git_status` | Git-Status prüfen | "Zeig mir den aktuellen Git-Status" |
| `git_diff` | Git-Diff anzeigen | "Welche Änderungen sind gerade offen?" |
| `git_log` | Letzte Commits anzeigen | "Welche letzten Commits gab es?" |
| `git_show` | Commit-Details anzeigen | "Zeig mir den letzten Commit im Detail" |
| `git_branch` | Aktuellen Branch und lokale Branches anzeigen | "Auf welchem Branch bin ich gerade?" |
| `git_remote` | Git-Remotes und URLs anzeigen | "Welche Remotes sind konfiguriert?" |
| `todo_scan` | TODO/FIXME/HACK-Hotspots im Repo finden | "Wo sind die dringendsten Wartungsstellen?" |
| `project_inventory` | Projekt-Inventar und Hotspots erstellen | "Was ist das Wichtigste in diesem Repo?" |
| `ui_action_audit` | UI auf doppelte Buttons/Hotspots prüfen | "Findet die App doppelte Aktionen in der Oberfläche?" |
| `config_audit` | Konfigurationen auf Inkonsistenzen prüfen | "Prüfe, ob die Settings doppelt oder widersprüchlich sind" |

### Warum das wichtig ist
- Mit `search_files`, `git_*`, `todo_scan` und den Audit-Tools kann die App sich selbst analysieren und vereinfachen.
- Genau diese Tools helfen dir später beim Release, die App als "autonom", "selbstprüfend" und "selbstoptimierend" zu erklären.
- Für eine opencode/openclaw-ähnliche Story sind diese internen Werkzeuge wichtiger als noch mehr lose UI-Buttons.

## Continuous Voice Mode (NEU)

Der **Sprachmodus** in den Einstellungen macht den Chat komplett **hands-free**:

1. **Aktivieren**: Einstellungen → Sprache & Stimme → **Sprachmodus** einschalten
2. **Sprechen**: Einmal Mikrofon antippen – dann läuft alles automatisch
3. **Loop**: Du sprichst → Nachricht wird gesendet → KI antwortet → Mikro öffnet sich wieder → Du sprichst erneut
4. **Stoppen**: Einfach Mikrofon antippen oder Sprachmodus ausschalten

> **Tipp:** Kombiniere mit Auto-TTS (lässt sich Antworten vorlesen) für komplett freihändige Nutzung.

## Datei-Tools (read_file / write_file / edit_file)

Diese arbeiten **nur im App-Sandbox** (`filesDir`). Pfade sind relativ:
- `notes/todo.txt` → speichert in `filesDir/notes/todo.txt`
- `test.txt` → speichert in `filesDir/test.txt`
- Verzeichnis-Pfade wie `../../etc/passwd` werden sicher blockiert

## Terminal (run_terminal)

Erlaubt Bash-Befehle im App-Sandbox. **Gesperrte Befehle:**
- `rm -rf /`, `rm -rf /*` – System löschen
- `dd if=`, `mkfs.` – Low-Level-Operationen
- `shutdown`, `reboot` – Geräte-Aktionen
- `sudo ` – Root-Zugriff
- `chmod 777`, `>:` – Unsichere Operationen

Alles andere ist erlaubt: `ls`, `pwd`, `git`, `node`, `python`, `echo`, `mkdir` etc.

## MCP-Server (extern, optional)

Für eigene MCP-Server (z.B. Filesystem, GitHub):
1. Gehe zu **Einstellungen → MCP-Server**
2. Tippe auf den Server, den du starten willst
3. Der Server muss **vorher installiert** sein (z.B. `npx @modelcontextprotocol/server-filesystem` via Termux)
4. Nach dem Start leuchtet der Schalter **lila**

> **Hinweis:** MCP-Server benötigen `npx` (Node.js) auf deinem Gerät. Ohne Node.js bleiben die Schalter grau – die Builtin-Tools funktionieren trotzdem!

## Troubleshooting

| Problem | Lösung |
|---------|--------|
| Quick-Action fehlt | In den Einstellungen prüfen, ob Agent-Features aktiviert sind |
| Agent nutzt kein Tool | Formuliere den Prompt klarer, z.B. "Suche im Web nach ..." |
| Schalter bleibt grau | Node.js/npx nicht installiert – Builtin-Tools reichen für die meisten Fälle |
| "Datei nicht gefunden" | Pfad ist relativ zu `filesDir`, nicht zum Stammverzeichnis |
