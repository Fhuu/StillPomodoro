# Layered Break Ambience Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Natural, varied, offline break ambience from both approved recordings.
**Architecture:** A break-only Media3 mixer plays two prepared seamless loops; a small pure Kotlin envelope supplies seeded, smooth multiscale volume modulation. AmbientPlayer owns session and routing integration.
**Tech Stack:** Kotlin, Android Media3 1.4.1, FFmpeg for asset preparation, JUnit 4.
**Spec:** `docs/superpowers/specs/2026-10-30-break-ambience-design.md`

## Global Constraints

- Preserve existing uncommitted work; no commits, branch changes, or worktree creation.
- Bundle both approved CC0 recordings, with source/license and reproducible processing notes.
- Keep focus playback and UI unchanged; no new runtime dependencies.
- Stop all layers and scheduled starts on pause, stop, phase change, or headphone disconnect.

## Review Focus

- Delayed second-layer start cannot outlive cancellation or a break.
- Repeated start and stop during a fade do not leak players or callbacks.
- Loops remain continuous after seeking to random positions.
- Routing checks include break playback even when the local focus player is absent.
- The combined gains stay bounded and change gently across noise cell boundaries.

### Task 1: Prepare both offline loops

**Files:** `app/src/main/res/raw/break_ocean_calm.ogg`, `break_ocean_gentle.ogg`; `docs/audio-sources.md`.
- [x] Download full-duration HQ previews of Freesound 578524 and 417797.
- [x] Prepare overlap-crossfaded Ogg loops and level-match them; document sources, license, commands, and durations.
- [x] Decode and inspect endpoints, loudness, and duration; remove the superseded 17-second asset after integration.

### Task 2: Implement and verify the mixer

**Files:** `BreakAmbienceEnvelope.kt`, `BreakAmbiencePlayer.kt`, `AmbientPlayer.kt`, `app/src/test/java/com/pomodoro/timer/BreakAmbienceEnvelopeTest.kt`, `app/build.gradle.kts`.
**Interfaces:** Envelope exposes `volumes(elapsedSeconds: Double): Pair<Float, Float>`; mixer exposes `start()`, `stop(immediate: Boolean = false)`, `isActive`.
- [x] Write and run failing tests for staggered fade-ins, quiet nonzero background, bounded combined gain, smooth noise boundaries, time-dependent variation, and seeded reproducibility.
- [x] Implement the envelope as three smooth value-noise octaves per layer.
- [x] Implement the mixer with independent Media3 players, random offsets, monotonic elapsed time, repeat-one, bounded volume, cancellable ticks, and fade-out/release.
- [x] Integrate break start/stop and routing in AmbientPlayer; cover pending-start cancellation and repeated stop/start in lifecycle verification.
- [x] Run unit tests, assembleDebug, lintDebug, asset checks, and independent code review. Record whether phone verification was available.

## Verification and decisions

- Baseline unit-test task passed with no tests before this feature.
- Tests first failed against a silent envelope; all five modulation tests now pass.
- Robolectric tests first reproduced the pause, route-monitoring, and YouTube-focus handoff regressions. All five lifecycle tests now pass, including pending-start cancellation and restart during fade.
- `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded; 10 tests passed. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:lintDebug` reports five existing errors: WrongConstant in AmbientPickers; ProtectedPermissions for the existing manifest permission; three UnsafeOptInUsageError uses in YoutubeAudioPlayer. None are in the new mixer or tests. These unrelated issues were left unchanged.
- Full audio decoding and seam checks passed; measured loudness is −23.21 LUFS (calm) and −23.45 LUFS (gentle). Both source files were full-duration HQ previews.
- Dynamic loudness normalization was replaced with fixed gain and memoryless soft limiting after decode checks detected a boundary mismatch.
- Final independent review found no remaining issues after lifecycle fixes; stale review comments were removed.
- No phone was connected. Device listening, screen-off playback, and physical Bluetooth routing remain unverified; no installation was attempted.
- Work stayed in the existing checkout to preserve the user's ongoing uncommitted feature work and their instruction that implementation go through. No commits or branch changes were made.
