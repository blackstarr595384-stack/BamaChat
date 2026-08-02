# Phase 7.6b: Agent Draft PR

## Ziel und Freigabegrenze

Phase 7.6b ergänzt GitHub Intelligence um einen lokal erzeugten, strukturierten Umsetzungsplan und eine explizite Freigabeansicht. Die Android-App verändert weder lokale Repositorydateien noch GitHub-Inhalte. Ein echter Draft-PR-Auftrag ist in diesem Stand deaktiviert.

`EXTERNAL_SERVER_IMPLEMENTATION_REQUIRED`

Im erreichbaren Repository existieren weder ein authentifizierter BamaWorker-Client noch eine serverseitige GitHub-App-Implementierung für Agentenaufträge. Deshalb verwendet Android ausschließlich `DisabledAgentDraftPrGateway`. Der Gateway besitzt keine URL, keinen HTTP-Client, keinen Redirectpfad und keine versteckte Direktverbindung zu GitHub.

## Architekturgrenze

Der vorgesehene spätere Datenfluss lautet:

1. Der Android-Client liest das öffentliche Allowlist-Repository wie in Phase 7.6a ausschließlich lesend.
2. Der Nutzer wählt einen validierten Verbesserungsvorschlag aus.
3. `AgentImplementationPlanFactory` erzeugt lokal einen deterministischen Plan.
4. `AgentDraftPrPlanPolicy` prüft Repository, Ref, Basis-SHA, Branch, Pfade, Größen und Validierungs-IDs erneut.
5. Der Nutzer bestätigt den unveränderten Verbund aus `planId`, `baseCommitSha` und `branchName` und betätigt zusätzlich die finale Freigabeschaltfläche.
6. Erst eine spätere authentifizierte BamaWorker-Implementierung darf den gebundenen Auftrag annehmen.
7. Nur der BamaWorker darf über eine serverseitige GitHub App einen isolierten Agenten-Branch, einen Commit und einen Draft Pull Request erzeugen.

GitHub-App-Schlüssel, Installationstokens und Authorization-Header bleiben ausschließlich serverseitig. Sie sind weder Bestandteil der SharedCore-Modelle noch des Android-UI-State.

## Lokale Planregeln

- Repository: ausschließlich `blackstarr595384-stack/BamaChat`
- Ref: ausschließlich die vorhandene Ref-Allowlist
- Basis-Commit: exakt 40 kleingeschriebene SHA-1-Hexzeichen
- Plan-ID: exakt `plan-` plus 20 kleingeschriebene Hexzeichen; sie wird zentral über eine versionierte, feldmarkierte und UTF-8-längengebundene Kodierung aus Vorschlag, Repository, Ref, Basis-SHA, kanonischen Pfaden, geordneten Schritten und Validierungen, Risiko und Einschränkungen berechnet und vor jeder Freigabe erneut gegen den unveränderten Planinhalt geprüft
- Vorschlags-ID: unveränderte Parser-ID `proposal-` plus 64 kleingeschriebene Hexzeichen; nur ein begrenzter numerischer Kollisionssuffix ab `-2` ist zulässig
- Branch: `bamachat-agent/<kurze-plan-id>-<slug>`, maximal 80 Zeichen
- Pfade: ausschließlich bereits im validierten Snapshot und Vorschlag enthalten
- Dateianzahl: maximal 12
- Schritte: ausschließlich sicherer einzeiliger deklarativer Text ohne Steuer- oder Unicode-Formatzeichen; keine Git-, Gradle-, Maven-, Shell-, CMD-, Netzwerk- oder Skriptinterpreter-Befehle
- Tests: exakt die zentral aus den betroffenen Modulpfaden abgeleitete, geordnete `AgentValidationId`-Liste
- Planlebensdauer: maximal 30 Minuten
- Zustimmung: bindet exakt Plan-ID, Basis-SHA und Branchname

Zusätzlich zu `GitHubPathPolicy` sind GitHub-Workflows, Secret- und Release-Key-Verzeichnisse, Schlüssel- und Zertifikatsdateien sowie lokale Credential-Konfigurationen geschützt. Symlinks, Submodule, Binär- und Artefaktpfade bleiben bereits an der GitHub-Lesegrenze blockiert.

## Feste Validierungs-Allowlist

Eine spätere Serverimplementierung darf ausschließlich folgende IDs auf fest codierte Kommandos abbilden:

| ID | Serverfester Task |
| --- | --- |
| `SHARED_CORE_TEST` | `:sharedCore:test` |
| `ANDROID_UNIT_TEST` | `:app:testDebugUnitTest` |
| `ANDROID_COMPILE` | `:app:compileDebugKotlin` |
| `ANDROID_ASSEMBLE` | `:app:assembleDebug` |
| `DESKTOP_COMPILE` | `:desktopApp:compileKotlin` |
| `DESKTOP_TEST` | `:desktopApp:test` |
| `DIFF_CHECK` | Git-Diff-Whitespace-Prüfung |

Modelltext darf niemals einen auszuführenden Befehl bestimmen.

## Dokumentierter BamaWorker-Vertrag

Die folgenden Routen sind ausschließlich ein Vertrag für eine spätere Serverphase und in Phase 7.6b nicht erreichbar:

- Auftrag anlegen: `POST /v1/github/draft-pr/jobs`
- Status lesen: `GET /v1/github/draft-pr/jobs/{requestId}`
- Auftrag vor Annahme abbrechen: `POST /v1/github/draft-pr/jobs/{requestId}/cancel`

Der Server muss einen authentifizierten Benutzer, eine kurze Nonce beziehungsweise einen signierten Zeitstempel nach seinem vorhandenen Authentifizierungsmodell und den `idempotencyKey` verlangen. Der serverseitige Idempotenzschlüssel bindet den authentifizierten Benutzer zusätzlich an Repository, Basis-SHA und Plan-ID. Gleiche parallele Anfragen liefern denselben Job.

Antworten dürfen nur typisierte Statusdaten, sichere Meldungen, Branch, Commit, Draft-PR-Nummer, erlaubte Draft-PR-URL, Checks, Warnungen und Zeitstempel enthalten. Tokens, Rohprompts, Patches, Quelltexte, interne Exceptions und Providerantworten sind verboten.

## Erforderliche Serverdurchsetzung

Eine spätere BamaWorker-Implementierung muss:

- ausschließlich eine serverseitig konfigurierte GitHub App verwenden;
- den Branch exakt von `baseCommitSha` erstellen und bestehende Branches ablehnen;
- `main`, Force-Push, Merge, Auto-Merge, Approve, Workflow-Dispatch, Issues und Kommentare verbieten;
- ausschließlich einen Draft Pull Request erzeugen;
- Änderungen auf `approvedPaths` und maximal 12 Textdateien begrenzen;
- Workflow-, Secret-, Symlink-, Submodule-, Binär- und LFS-Pfade blockieren;
- Patchgröße auf 256 KiB begrenzen;
- strukturierten Modelloutput lokal parsen und niemals als Befehl ausführen;
- einen isolierten temporären Workspace verwenden;
- den resultierenden Diff vor Commit erneut prüfen;
- ausschließlich die feste Validierungs-Allowlist ausführen;
- Statusübergänge monoton halten;
- Wiederholungen nach unklarer Netzwerkantwort zuerst per Statusabfrage auflösen;
- Cancellation ausschließlich vor Serverannahme wirksam machen und ab `SERVER_ACCEPTED` vollständig ablehnen.

## UI- und Persistenzgrenzen

Vorschlagsdetails sind einzeln aufklappbar. Der Plan zeigt Repository, Basis-Ref, Basis-SHA, vorgegebenen Branch, Pfade, deklarative Änderungen, Validierungen, Risiko und Einschränkungen. Ohne Server bleibt die finale Schaltfläche deaktiviert und der neutrale Hinweis `Sicherer Agent-Service noch nicht verbunden` sichtbar.

Weder Repositoryquelltexte noch Patches, Rohantworten, Tokens oder vollständige Agentenresultate werden im UI-State, in SharedPreferences, in Dateien oder in Logs gespeichert. Ein Plan oder eine Zustimmung startet keinen Auftrag automatisch. Rotation und Navigation lösen keine Freigabe aus.

## Spätere Live-E2E-Prozedur

Erst nach separater Bereitstellung und Prüfung des BamaWorkers sind folgende Schritte zulässig:

1. Serverauthentifizierung, Host-Allowlist, TLS, Timeouts, Redirectblockade und Request-/Response-Limits prüfen.
2. GitHub-App-Secrets ausschließlich in der Serverumgebung konfigurieren.
3. Server-Contract-, Idempotenz-, Race-, Patch-, Pfad- und Test-Allowlist-Tests ausführen.
4. Einen explizit freigegebenen, ungefährlichen Dokumentationsplan verwenden.
5. Basis-SHA und Agenten-Branch serverseitig verifizieren.
6. Ausschließlich einen Draft Pull Request erstellen und anschließend menschlich prüfen.
7. Weder automatisch mergen noch `main` direkt verändern.

Phase 7.6b selbst erstellt keinen echten Draft Pull Request.
