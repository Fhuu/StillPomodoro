# Feature Packages Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Preserve all existing uncommitted changes.

**Goal:** Group sound, island and settings code into the requested packages.
**Architecture:** Direct Kotlin file/package moves plus explicit imports. Android application ID, namespace and component names stay unchanged. Keep shared TimerStore and Phase in timer.
**Tech Stack:** Existing Kotlin/Android module and test suite.
**Spec:** `docs/superpowers/specs/2026-11-01-feature-packages-design.md`

## Global Constraints
- All folder/device audio selection and YouTube code belongs to sound.
- Keep public names and behavior; avoid unrelated extraction or refactoring.
- Preserve pending Old house/Roof storm work; no commits or installation.

## Review Focus
- Imported Android R references still resolve to the unchanged app namespace.
- Cross-package references resolve, including widget/service/activity callers.
- Test package moves and reflection continue exercising real players.
- No Android manifest component identity changes.
- No old fully-qualified audio/island/settings references remain in executable files.

### Task 1: Package moves
- [x] Move the audio classes/source pickers/YouTube helpers and focused tests to sound, five island UI files to island, and SettingsDialog to setting.
- [x] Add explicit cross-feature imports and preserve existing declarations and visibility.
- [x] Run `./gradlew testDebugUnitTest lintDebug assembleDebug --offline`; require success and zero lint errors.
- [x] Inspect diff for behavior-only edits beyond the already authorized sound changes, check obsolete package references, and review independently.

## Execution record

Pre-flight: sound and island consume TimerStore/Phase/PomodoroService and each other's public/internal symbols; explicit imports preserve those module-level interfaces. Kotlin `internal` visibility remains module-wide.


Verification: `testDebugUnitTest lintDebug assembleDebug --offline` succeeded after all package moves: 51 tests, zero failures/errors/skips; lint zero errors and 32 warnings. Debug APK built at `app/build/outputs/apk/debug/app-debug.apk`. Independent review found no important or critical bugs and no stale class references. No manifest, application ID, preference key, branch, commit, installation, or dist artifact changes.

Moved 19 audio files, five island UI files, SettingsDialog, and eight audio-focused tests; shared timer/session state, app entry points and Android namespace remain in timer. Build/test compilation verifies cross-package visibility and imports.
