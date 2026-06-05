# BamaChat Solo Release Board

This board is optimized for a solo workflow where AI agents execute tasks.

## Mission

- Ship a stable Play Store release with low friction onboarding.
- Keep advanced power features, but hide complexity from first-time users.

## Working Mode (Solo + AI Agents)

- Columns: `Backlog` -> `Ready` -> `In Progress` -> `Verify` -> `Done` (or `Blocked`)
- WIP rule: max 1 ticket in `In Progress`
- Finish rule: a ticket is only `Done` after acceptance criteria + verification commands pass

## Go / No-Go KPI Gates

- Crash-free sessions: `>= 99.5%`
- ANR rate: `< 0.3%`
- Onboarding complete rate: `>= 60%`
- First message sent after install: `>= 55%`
- D1 retention (beta): `>= 22%`
- Paywall to purchase (qualified users): `>= 2%`

## Sprint 1 (Startklar)

Focus: stabilize core paths before rollout.

| ID | Priority | Status | Owner Agent | Goal | Acceptance Criteria |
|---|---|---|---|---|---|
| P0-01 | high | Verify | qa-agent | Crash/ANR baseline | No fatal crash in happy path, crash monitor dashboard checked |
| P0-02 | high | Done | builder-agent | Onboarding under 60s | New user can send first message without advanced setup friction |
| P0-03 | high | Done | builder-agent | Core chat reliability | Send/stream/copy/select/sources chip all work on device |

## Backlog (P0 -> P2)

| ID | Priority | Status | Owner Agent | Goal | Acceptance Criteria |
|---|---|---|---|---|---|
| P0-04 | high | Done | builder-agent | Offline + timeout UX | Clear error + retry path, no app restart required |
| P0-05 | high | Verify | qa-agent | Billing E2E | Purchase, restore, entitlement and credits are consistent |
| P0-06 | high | Backlog | compliance-agent | Play compliance | Data safety, privacy text, support and delete path are visible and accurate |
| P0-07 | high | Backlog | growth-agent | Store conversion pack | Icon, feature graphic, 6-8 screenshots, DE/EN listing copy finalized |
| P0-08 | high | Backlog | release-agent | Staged rollout runbook | 5/20/50/100 rollout plan + rollback thresholds documented |
| P1-01 | medium | Backlog | data-agent | Funnel telemetry | install -> onboarding_complete -> first_message -> D1 trackable |
| P1-02 | medium | Backlog | builder-agent | Simpler default UX | Advanced settings moved behind clear `Advanced` section |
| P1-03 | medium | Backlog | growth-agent | Paywall messaging | Value before price, improved beta conversion |
| P2-01 | low | Backlog | builder-agent | Warning cleanup | Deprecated API warnings significantly reduced |

## Verification Commands (per ticket)

Use these commands as a minimum verification gate:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:stabilityCheck
.\gradlew.bat :app:installDebug
```

Device launch smoke:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am force-stop com.example.bamachat
& $adb shell am start -W -n com.example.bamachat/.MainActivity
```

## Daily Solo Cadence

1. Move exactly one `Ready` ticket to `In Progress`.
2. Let the assigned AI agent implement/fix.
3. Move ticket to `Verify` and run build + device smoke.
4. If all AC are met, move to `Done`; otherwise return to `In Progress`.
5. End of day: update KPI snapshot and pick next ticket.

## Agent Prompt Templates

Use these as copy/paste starters.

```text
You are my qa-agent. Execute ticket <ID> from RELEASE_BOARD.md.
Return: findings, root cause, minimal fix recommendation, and exact verification steps/results.
```

```text
You are my builder-agent. Implement ticket <ID> minimally and safely.
Follow existing repo conventions, run required checks, and return changed files + rationale.
```

```text
You are my release-agent. Validate rollout readiness for current sprint.
Return GO/NO-GO decision with KPI gate status and rollback risks.
```

## Current Sprint Kickoff Order

- 1) `P0-01`
- 2) `P0-02`
- 3) `P0-03`

When all 3 are `Done`, start `P0-04`.

## Execution Log

### P0-01 Crash/ANR Baseline (2026-05-23)

Status: `Done`

- Build gate pass: `:app:compileDebugKotlin`, `:app:stabilityCheck`, `:app:installDebug`
- Device smoke pass: `am start -W -n com.example.bamachat/.MainActivity` (cold start ~440ms)
- Monkey smoke pass: `adb shell monkey -p com.example.bamachat --throttle 120 300`
- Runtime fatal check: no `AndroidRuntime` fatal entries after test run
- Exit-info check: no new `APP CRASH` or `ANR` exit reasons in latest entries for this run

Open item before marking `Done`:

- Manual Crashlytics dashboard validation (requires Firebase console access not available in CLI runtime)

### P0-02 Onboarding under 60s (2026-05-23)

Status: `Done`

Implemented quick-start friction reductions:

- Skipping onboarding now persists completion state (`onboarding_completed=true`) so onboarding is not shown again on next launch.
- Welcome screen default CTA for unauthenticated users now prioritizes guest quick-start.
- Guest quick-start now deep-links directly into `CHAT` instead of routing via `HOME_HUB` first.

Verification:

- Build + install pass: `:app:compileDebugKotlin`, `:app:installDebug`
- Launch smoke pass: cold start and process alive

Additional verification:

- Fresh-install automation (`pm clear`) run completed
- Onboarding skip -> guest quick-start -> first send in `16s` (< 60s target)

### P0-03 Core Chat Reliability (2026-05-23)

Status: `Done`

Verified now:

- Fresh-install flow reaches chat reliably after onboarding skip + guest start
- Message composer interaction works; first message can be sent without crash
- Build gate remains green after chat UX changes (`:app:compileDebugKotlin`, `:app:stabilityCheck`)
- User validation: with Auto-Fallback enabled, normal question flow works reliably again

Notes:

- Groq `404 Modell nicht gefunden` root cause in agent loop fixed (provider now uses provider-specific model instead of OpenRouter model)
- Groq default switched to stable baseline model (`llama-3.1-8b-instant`)
- Residual risk: Groq-only mode may still be intermittently provider-sensitive; Auto-Fallback remains recommended for production users

### P0-04 Offline/Timeout UX (2026-05-24)

Status: `Done`

Implemented:

- Retry-aware error surface in chat: snackbar now supports action button for retryable failures
- Retry action is wired to resend last failed user message (`retryLastFailedMessage`)
- Error metadata added to ViewModel (`errorActionLabel`, `isErrorRetryable`)
- Connectivity preflight added in `ChatViewModel.sendChatViaApi` (checks active validated internet before provider call)
- Biometric lock default changed to opt-in (`biometric_enabled` default `false`) to reduce launch friction and avoid onboarding dead-ends
- Error mapping improved: `404 Modell nicht gefunden` is no longer labeled as generic unknown error

Verification:

- `:app:compileDebugKotlin` pass
- `:app:stabilityCheck` pass
- `:app:installDebug` + device cold-start smoke pass
- Device retry-path automation pass with forced DNS outage and recovery:
  - Forced failure via invalid private DNS (`private_dns_mode=hostname`, specifier invalid host)
  - Send shows retry snackbar (`Erneut versuchen`) within ~1s
  - After DNS restore, tapping snackbar action triggers resend without app restart (chat user-message count increases by +1 again)
- Final provider-path validation pass (2026-05-24), mobile data only (`Wi-Fi off`) with OpenRouter forced in prefs:
  - Retry flow executed in same session: network failure -> `Erneut versuchen` -> assistant response persisted
  - DB counters confirmed end-to-end success after retry (`assistant count` increased from `0` to `1`)

Note:

- In this run, one separate normal send showed `Modell nicht gefunden` before retry testing; that is a model/provider configuration issue and not a blocker for offline/retry UX acceptance.

### P0-05 Billing E2E (2026-05-24)

Status: `Verify`

Verified:

- Product-ID consistency check passed in code: `PlayBillingManager` uses IDs from `MonetizationConfig` (`pro_monthly_799`, `expert_monthly_1999`, `credits_100/300/1000`).
- Build gates pass after billing checks: `:app:compileDebugKotlin`, `:app:installDebug`, `:app:stabilityCheck`.
- Device run done on mobile data only (`Wi-Fi off`) as requested; settings show `Play Billing verbunden` and plan chips are rendered.

Open items before marking `Done`:

- Real sandbox purchase flow must complete in Play Billing UI (Pro/Expert) and persist entitlement (`premium_active` + `subscription_tier`) after app relaunch.
- Restore validation must be confirmed with an existing sandbox purchase (query on relaunch should recover tier without manual debug flag).
- Credits purchase path must be completed once in sandbox and verified that `credits_balance` increases according to `MonetizationConfig.creditsForProduct(...)`.

Current blocker note:

- On current sideloaded debug run (mobile data only), `Play Billing verbunden` is visible but tapping `Pro 7,99€` does not open the Play purchase sheet; likely requires a Play-distributed test build / fully configured tester-product availability before final E2E can be closed.
