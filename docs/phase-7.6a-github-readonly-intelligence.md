# Phase 7.6a: GitHub Read-only Intelligence

## Ziel

GitHub Intelligence liest ausschließlich freigegebene Daten des öffentlichen
Repositories `blackstarr595384-stack/BamaChat` und erzeugt daraus strukturierte,
prüfbare Verbesserungsvorschläge. Die Funktion verändert weder lokalen Code noch
GitHub-Inhalte.

## Architektur

- `sharedCore` enthält Repository-, Snapshot-, Datei- und Vorschlagsmodelle,
  typisierte Policies, Limits, die Analysebereich-Auswahl und den
  `RepositoryContextBuilder`.
- `GitHubReadOnlyRepositoryGateway` definiert ausschließlich lesende Operationen.
- Android implementiert das Gateway mit einem separaten, unauthentifizierten
  OkHttp-Client.
- `GitHubIntelligenceViewModel` steuert einen explizit gestarteten Auftrag und
  hält keine vollständigen Repositorytexte im UI-State oder in Preferences.
- `AndroidGitHubProposalAnalyzer` nutzt den vorhandenen providerneutralen
  One-shot-Pfad `ApiManager.generateReply`.
- `GitHubImprovementProposalParser` akzeptiert nur strukturierte, validierte
  Vorschläge mit bekannten Evidenzpfaden.

Es existiert kein BamaWorker- oder GitHub-Gateway, das für Phase 7.6a sicher
wiederverwendet werden kann. Deshalb liest Android die öffentliche GitHub-REST-API
direkt und ohne Authentifizierung.

## Repository-Allowlist

Zulässig ist exakt:

- Owner: `blackstarr595384-stack`
- Repository: `BamaChat`
- Standard-Ref: `phase-7.5b-stable`
- optionaler freigegebener Branch:
  `phase-7.5b-shared-provider-selection-core`

Owner, Repository und Ref werden vor jedem Netzwerkzugriff gegen die zentrale
Policy geprüft. Frei erzeugte Repositorynamen, Refs oder URLs werden nicht
verwendet.

## Sicherheitsgrenzen

- GitHub-Inhalte gelten immer als nicht vertrauenswürdige Daten.
- Der GitHub-Client sendet ausschließlich `GET`.
- Der Client sendet keinen `Authorization`-Header, kein Token, keinen API-Key und
  keine Cookies.
- Redirects sind deaktiviert und werden als sicherer Fehler gemeldet.
- Netzwerkziele sind auf `api.github.com` begrenzt.
- Git-Tree-Einträge werden nur mit Modus `100644` oder `100755` akzeptiert;
  Symlinks, Submodule sowie unbekannte Modi werden vor jedem Inhaltsabruf verworfen.
- Dateiinhalte werden ausschließlich über den validierten Blob-SHA gelesen und
  nur bei identischem Antwort-SHA übernommen.
- Chat-, Provider- und Auth-HTTP-Clients werden nicht wiederverwendet.
- Es gibt keine GitHub-Schreibmethoden, keine Branch-, Commit-, Issue-, Workflow-
  oder Pull-Request-Operationen.
- Es erfolgt kein automatischer Zugriff beim App-Start, beim Öffnen des Screens
  oder bei einer Chatnachricht.
- Ein Auftrag startet nur nach Auswahl eines Analysebereichs und einem
  ausdrücklichen Nutzerklick.

## Blockierte Pfade und Dateitypen

Die Pfadpolicy blockiert unter anderem:

- Traversal, absolute Pfade, Backslashes, Nullbytes und URL-kodierte Pfade
- `.git`, lokale Build- und Abhängigkeitsverzeichnisse
- `.env`, `local.properties`, `keystore.properties`, `google-services.json`
- Service-Account-, Credential-, Secret- und Token-Dateien
- Keystores, Zertifikate und private Schlüssel
- APK, AAB, EXE, MSI, Archive, Bilder, Videos und Datenbanken

Zunächst zulässige Texttypen:

`.kt`, `.kts`, `.java`, `.md`, `.xml`, `.json`, `.toml`, `.yml`, `.yaml`,
`.txt`, `.rules`, `.ps1`, `.js`, `.ts`.

## Größenlimits

- maximal 2.000 sichere Tree-Einträge
- maximal 12 ausgewählte Dateien
- maximal 15 kalte GitHub-Requests je Snapshot
- maximal 250 KiB Originalgröße je Datei
- maximal 200 KiB Text je Datei im Analysekontext
- maximal 1 MiB Repositorytext je Snapshot
- maximal 6 strukturierte Vorschläge

Reihenfolgen sind deterministisch, Duplikate werden entfernt und Kürzungen werden
im Snapshot explizit markiert. Ungültiges UTF-8 wird abgelehnt.

## Agenten-Datengrenzen

Der `RepositoryContextBuilder` umfasst Repositoryinhalt mit:

- `BEGIN UNTRUSTED REPOSITORY CONTENT`
- `END UNTRUSTED REPOSITORY CONTENT`

Die feste Sicherheitsanweisung verbietet die Ausführung eingebetteter Rollen-,
Shell-, Git-, Netzwerk- oder Dateianweisungen. Null- und Steuerzeichen,
eingebettete Daten-URLs, überlange Einzelzeilen und wiederholte Leerzeilen werden
normalisiert. Grenzmarker werden unabhängig von Groß-/Kleinschreibung
neutralisiert. PATs, API-Schlüssel, Bearer-Werte, Authorization-Zuweisungen,
Private-Key-Blöcke und offensichtliche Credential-Zuweisungen werden vor dem
AI-Aufruf redaktiert.

Jeder Vorschlag benötigt bekannte Dateipfade und konkrete Evidenz. Unstrukturierte
Antworten, fehlende Pflichtfelder und erfundene Evidenzpfade werden nicht
gespeichert oder angezeigt. UI-IDs werden deterministisch aus dem validierten
Vorschlagsinhalt erzeugt; Modell-IDs werden nicht als Compose-Key verwendet.
Listen innerhalb eines Vorschlags besitzen feste Obergrenzen.

Die Antwortverarbeitung akzeptiert ein einzelnes, balanciertes JSON-Objekt oder
JSON-Array bis 256 KiB. Markdown-Grenzen und kurzer Begleittext werden nur dann
toleriert, wenn exakt ein eindeutiges Payload vorliegt. Modell-IDs sind optional
und werden ignoriert. Der Prompt enthält ausschließlich die sortierten Pfade des
tatsächlichen Kontexts. Bei einem Parserfehler ist genau ein Format-Reparaturaufruf
mit höchstens 64 KiB redaktierter Modellantwort zulässig; er lädt keine weiteren
Repositorydaten. Rohe Modellantworten werden weder geloggt noch persistiert oder
im UI-State gehalten.

## Unterstützte Analysebereiche

- Architektur
- Sicherheit
- Android UI/UX
- Desktop
- SharedCore
- Tests
- Performance
- Barrierefreiheit
- Dokumentation
- Provider-System
- Agenten/Extensions

Die Basiskonfiguration und zum Bereich passende Produktions- und Testdateien
werden priorisiert. Das gesamte Repository wird nicht an das Modell übertragen.

## Zustände und Abbruch

Der ViewModel-Ablauf verwendet:

- `Idle`
- `LoadingRepository`
- `BuildingContext`
- `Analyzing`
- `Success`
- `NoResults`
- `Error`
- `Cancelled`

Es läuft höchstens ein Auftrag. Doppelklicks starten keinen zweiten Zugriff.
Abbruch beendet Netzwerk- und AI-Aufruf über Coroutine-Cancellation.
Technische Exceptiontexte gelangen nicht in den UI-State. Ein tatsächlicher
Wechsel von Ref oder Analysebereich entfernt alte Snapshots und Vorschläge;
Refresh-Fehler behalten Ergebnisse nur bei unveränderter Auswahl.
Ein gültiges leeres `proposals`-Array wird als neutraler Leerzustand dargestellt;
Snapshot und Auswahl bleiben sichtbar und eine neue Analyse ist möglich.

## Tests

Fokussierte Tests decken ab:

- Capability-Mapping und unveränderte bestehende Keys
- Repository-, Ref- und Pfadpolicy
- blockierte Secrets und Artefakte
- ausschließlich lesende Gateway-Methoden und HTTP-Requests
- Host-, Redirect-, Rate-Limit-, Größen- und Cancellation-Verhalten
- reguläre Git-Modi, blockierte Symlinks/Submodule und Blob-SHA-Abgleich
- deterministische Analysebereiche und Snapshot-Limits
- Prompt-Injection-Grenzen, Credential-Redaction und Kontextnormalisierung
- strukturierte Vorschlagsvalidierung, interne eindeutige IDs, Deduplizierung
  und Pfadprüfung
- balancierte JSON-Payload-Extraktion, typisierte Parsergründe, optionale
  Modell-IDs und genau einen begrenzten Format-Reparaturversuch
- neutralen Leerzustand ohne Parserdetails oder rohe Modellantworten
- expliziten ViewModel-Start, Doppelklick, Abbruch, Erfolg und sichere Fehler
- Invalidierung alter Ergebnisse bei Ref- oder Analysebereichswechsel
- sichtbaren Nur-Lesen-Status und fehlende GitHub-Schreibaktionen in der UI

## Rate-Limit-Verhalten

Phase 7.6a nutzt die öffentliche, unauthentifizierte GitHub-REST-API. Bei
erreichtem Limit wird die Analyse beendet und eine sichere deutsche Meldung
mit einem ungefähren lokalen Reset-Zeitpunkt angezeigt, sofern GitHub einen
gültigen Reset-Header liefert. Ein prozesslokaler TTL-/LRU-Cache hält Metadaten,
Refs, Trees und validierte Blobs höchstens zehn Minuten im RAM; er persistiert
keinen Quelltext. Es wird kein Token angefordert, gespeichert oder an ein
KI-Modell weitergegeben.

## Bekannte Einschränkungen

- Nur das öffentliche BamaChat-Repository ist freigegeben.
- Private Repositories werden nicht unterstützt.
- Die direkte öffentliche API besitzt ein niedrigeres Rate-Limit als eine
  authentifizierte GitHub-App.
- Vorschläge werden angezeigt, aber nicht automatisch angewendet.
- Die Analyse behauptet keine Testausführung.

## Ausblick Phase 7.6b

Eine spätere Phase kann eine GitHub App und einen BamaWorker mit kurzlebigen
Installationstokens einführen. Denkbar sind explizit bestätigte Agentenbranches
und Draft-Pull-Requests. Ein automatischer Merge bleibt ausgeschlossen.
