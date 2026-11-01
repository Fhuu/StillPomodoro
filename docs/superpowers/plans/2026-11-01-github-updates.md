# GitHub Updates Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; preserve existing dist artifacts.

**Goal:** Add user-triggered GitHub release checking, APK download and Android installation confirmation.
**Architecture:** ReleaseVersion/GitHubReleaseClient handle metadata and downloads; ApkUpdateInstaller validates package/signature/version and builds installer intents. AppUpdater retains process-local UI state and serial background work. UpdateSection exposes actions in Settings.
**Tech Stack:** Existing Kotlin, Compose, OkHttp, Android PackageManager/FileProvider, JUnit/Robolectric.
**Spec:** `docs/superpowers/specs/2026-11-01-github-updates-design.md`

## Global Constraints
- Fixed Fhuu/StillPomodoro public releases, published prereleases included, drafts excluded.
- Manual actions only; Android confirms installation.
- App-private update cache; no broad storage permission or new dependencies.
- Package/signature/newer-build checks before installation; no version bump/commit/install.

## Review Focus
- Older/stable/prerelease tags compare numerically; legacy V prefixes do not break checks.
- Drafts, missing/ambiguous APK assets, foreign URLs and same versions are ignored.
- Interrupted/corrupt downloads cannot become installable files.
- Wrong package/signing key, stale APK version and non-newer versionCode are rejected.
- Unknown-source permission return, Settings recreation and closing do not duplicate work or auto-install.

### Task 1: Release metadata and download
- [x] Add failing semantic-version and release/download tests.
- [x] Implement ReleaseVersion and GitHubReleaseClient with bounded downloads and optional digest verification.
- [x] Verify prerelease discovery against the actual public release metadata.

### Task 2: Android installation and Settings
- [x] Add failing installer validation/intent tests.
- [x] Add narrow FileProvider cache mapping and REQUEST_INSTALL_PACKAGES permission.
- [x] Implement APK identity/signature/version validation and confirmation intents.
- [x] Implement process-local AppUpdater and UpdateSection with background check/download, progress, retry and permission return handling; integrate into SettingsDialog.
- [x] Run all tests, lint and debug/release builds; inspect rendered Settings on a device if available without installing unless requested.
- [x] Independent review and record verified limitations.

## Execution record

Pre-flight: release candidates expose version/tag/asset URL/size/digest; installer consumes a verified downloaded file and the candidate. UI consumes process-local state; background work retains only application context.


Verification: `testDebugUnitTest lintDebug assembleDebug assembleRelease --offline` succeeded: 68 tests, zero failures/errors/skips; lint zero errors (32 warnings). Debug and signed release APKs built. No new dependencies, version bump, commits, push, or device installation.

Independent review found two installer-handoff/current-signer issues, both fixed with regression tests. API 28+ uses GET_SIGNING_CERTIFICATES and current APK signers; API 26–27 uses legacy signature checks. Every download reserves a unique APK filename, including byte-identical downloads, so failure/deletion cannot affect a file already shared with an installer. Narrow UpdateFileProvider subclass uses the manifest's non-exported/read-grant configuration. Fixed review comments removed.

Additional tests cover duplicate checks/downloads, permission denial/grant with no auto-install, explicit installer handoff, old Android compatibility, package/version/key mismatches, short API responses, digest/size failures and immutable installer files. Test providers are initialized from real manifest metadata to isolate AndroidX's process-global path cache across Robolectric application instances.

Native phone installer/permission UI and visual Settings layout were not exercised; doing so requires installing this build. Settings integration was compiled and installer/permission intents were exercised through Robolectric. Actual GitHub metadata was read successfully, including v1.1.1-alpha and its APK size/digest. Future publication requires a greater versionCode and matching signing key. One manual installation is needed to bootstrap this updater.
