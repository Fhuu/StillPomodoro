# Roof Storm Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Preserve uncommitted work; no branch changes or commits.

**Goal:** Add Roof storm as an offline built-in sound with seven fractal-blended weather layers.
**Architecture:** Parameterize the existing layered player with recordings and an envelope factory, retaining ocean defaults. RoofStormEnvelope supplies independent rain/wind/thunder modulation; AmbientPlayer manages the third group with equal group attenuation.
**Tech Stack:** Existing Kotlin, Media3, JUnit/Robolectric, FFmpeg.
**Spec:** `docs/superpowers/specs/2026-10-30-roof-storm-design.md`

## Global Constraints
- Rain stays dominant; wind swells; thunder stays occasional within its recording.
- Seven offline CC0 loops, smooth independent fractal modulation, conservative headroom.
- Preserve existing ocean behavior and break defaults; no new dependencies or installation.

## Review Focus
- Storm-only playback must keep routing monitoring and the shared output alive.
- Pause, disconnect, deselection, and stop must release all seven layers.
- Three simultaneous groups must remain bounded and return to solo gain after exits.
- Restart during an outgoing fade must cancel the old fade.
- Loops must fully decode and avoid a discontinuity at their wrap.

### Task 1: Recordings and envelope
- [x] Download seven CC0 recordings; prepare fixed-gain, softened, seamless Opus loops.
- [x] Add failing RoofStormEnvelopeTest coverage for silent/staggered entrance, audible rain/wind, independent smooth seeded variation, and sum of gains <= 0.95.
- [x] Run `./gradlew testDebugUnitTest --tests '*RoofStormEnvelopeTest' --offline`; expect missing envelope failure.
- [x] Implement `RoofStormEnvelope(seed: Int).volumes(elapsedSeconds: Double): List<Float>` and rerun; expect PASS.
- [x] Document source URLs, processing, loudness, peaks, and loop measurements in `docs/roof-storm-sources.md`.

### Task 2: Playback and selection
- [x] Add failing lifecycle tests for persisted storm selection, seven distinct offline layers, storm-only pause/resume/disconnect, independent toggles, three-group gain restoration, and fade restart cancellation.
- [x] Run `./gradlew testDebugUnitTest --offline`; expect missing storm integration failures.
- [x] Add defaulted `recordings: List<Int>` and `envelopeFactory: (Int) -> (Double) -> List<Float>` to BreakAmbiencePlayer; retain ocean defaults.
- [x] Add ROOF_STORM to BuiltInAmbientSound and integrate storm ownership into AmbientPlayer lifecycle and group gain calculation.
- [x] Run `./gradlew testDebugUnitTest lintDebug assembleDebug --offline`; expect PASS, with any existing lint warnings documented.
- [x] Review changed code and audio measurements, then record verification below.

## Execution record

Pre-flight: Task 2 consumes Task 1's `RoofStormEnvelope(seed).volumes(seconds)` and seven `roof_storm_*` resources. Keep resource ordering identical to the envelope's gain ordering.

Ruling: execute in the provided workspace to keep the authorized change reviewable; preserve the existing untracked `dist/` directory. No branch or commit changes.


Task 1: complete. Envelope tests first failed to compile because RoofStormEnvelope was absent, then all three passed. Seven CC0 assets fully decoded, measured −24.15 to −25.06 LUFS, with wrap steps below 0.01 and 0.03–2.56× local sample-difference RMS. Maximum aligned source peak at maximum layer gains: 0.5088 before output enhancement.

Task 2: complete. Three integration tests first failed because ROOF_STORM was absent, then passed after integration. Final `testDebugUnitTest lintDebug assembleDebug --offline`: BUILD SUCCESSFUL, 47 tests, zero failures/errors/skips. Lint: zero errors, 45 warnings (same count as recorded before this change). Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Final review: independent reviewer found no critical or important code defects. Source licenses and final audio measurements were verified separately. Physical device playback and subjective balance remain unverified. No installation, commits, branch changes, or edits to existing dist artifacts.
