# Changelog

All notable changes to BamaChat will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **Security:** Removed `usesCleartextTraffic="true"` from AndroidManifest. Added `network_security_config.xml` — cleartext (HTTP) only allowed for localhost/loopback (Ollama, local LLMs). All external traffic requires HTTPS.
- **Security:** Encrypted SharedPreferences now consistently used for sensitive keys via `SecureSettingsStore`.
- **Architecture:** Began migrating `ServiceLocator` usage toward Hilt dependency injection for testability.
- **Architecture:** Added `.editorconfig` for consistent code formatting across IDEs and contributors.
- **Performance:** Added size limit to `ApiManager.researchCache` to prevent unbounded memory growth.

### Fixed
- **Security:** API keys are no longer at risk from cleartext HTTP interception.
- **Bug:** `visionViaGemini()` now returns a clear user-facing message instead of silently failing.
- **Bug:** `allMessagesBuffer` accesses in `ChatViewModel` are now thread-safe.

### Added
- `LICENSE` (MIT) added to repository.
- `CHANGELOG.md` for tracking releases.
- `.editorconfig` for consistent formatting.
- `network_security_config.xml` for granular HTTP/HTTPS control.
