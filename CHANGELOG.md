# Changelog

All notable changes to BamaChat will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **Compatibility:** `compileSdk` upgraded from 34 → 35 (Android 15) for full Galaxy S25 Ultra support and Play Store compliance.
- **UX:** Material You / Dynamic Color is now **enabled by default** on Android 12+ devices. Galaxy S25 Ultra with One UI 7 will use Samsung's system color palette automatically. Users who prefer the custom neon theme can disable it in settings.
- **Security:** Removed `usesCleartextTraffic="true"` from AndroidManifest. Added `network_security_config.xml` — cleartext (HTTP) only allowed for localhost/loopback (Ollama, local LLMs). All external traffic requires HTTPS.
- **Security:** Encrypted SharedPreferences now consistently used for sensitive keys via `SecureSettingsStore`.
- **Architecture:** Added `.editorconfig` for consistent code formatting across IDEs and contributors.
- **Performance:** Added size limit to `ApiManager.researchCache` (max 50 entries) to prevent unbounded memory growth.

### Fixed
- **Security:** API keys are no longer at risk from cleartext HTTP interception.
- **Bug:** `visionViaGemini()` now returns a clear German user-facing message instead of silently failing.
- **Bug:** `allMessagesBuffer` accesses in `ChatViewModel` are now thread-safe via `ReentrantLock`.
- **Compat:** `compileSdk` now matches `targetSdk` (both 35) — eliminates build inconsistency that could cause API-level issues on Android 15 devices.

### Added
- Android 14+ foreground service permissions (`FOREGROUND_SERVICE_MICROPHONE`) for audio features on S25 Ultra.
- `LICENSE` (MIT) added to repository.
- `CHANGELOG.md` for tracking releases.
- `.editorconfig` for consistent formatting.
- `network_security_config.xml` for granular HTTP/HTTPS control.
- CI GitHub Actions workflow (lint, unit tests, build).
