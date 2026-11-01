# Coastal Wind Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; preserve pending changes and dist artifacts.

**Goal:** Add separately selectable Coastal wind with natural strong gusts.
**Architecture:** One offline coastal wind loop in existing BreakAmbiencePlayer, with CoastalWindEnvelope and existing FractalNoise. Extend AmbientPlayer ownership to five groups.
**Tech Stack:** Existing Kotlin, Media3, JUnit/Robolectric, FFmpeg.
**Spec:** `docs/superpowers/specs/2026-11-01-coastal-wind-design.md`

## Global Constraints
- Natural recording, no added surf or synthetic gale; keep other sounds unchanged.
- Offline CC0 asset, smooth fades and bounded peak gain.
- No installation, commits, or dependency changes.

## Review Focus
- Coastal-only selection keeps output and routing alive.
- Pause, disconnect, deselection and immediate stop release the new player.
- Five groups attenuate to 1/5 and restore solo gain when other groups exit.
- Fade restart cancels old callbacks; breaks retain ocean-only playback.
- Prepared loop decodes and has a continuous wrap.

### Task 1: Asset and envelope
- [x] Download/verify CC0 source, prepare fixed-gain smooth loop, document processing and measurements.
- [x] Add failing CoastalWindEnvelopeTest for silent fade, bounded audible gain <= 0.65, smooth seeded variation.
- [x] Implement CoastalWindEnvelope(seed).volumes(seconds) using a five-second fade and existing fractal noise.

### Task 2: Integration
- [x] Add failing lifecycle tests for source identity, independent persisted selection, pause/resume/disconnect, restart and break cleanup, and five-group gain restoration.
- [x] Add COASTAL_WIND and wire the player into every existing output/routing/lifecycle path.
- [x] Run `testDebugUnitTest lintDebug assembleDebug --offline`, review and record results.

## Execution record

Pre-flight: integration consumes CoastalWindEnvelope(seed).volumes(seconds) and R.raw.coastal_wind. Preserve existing four-group tests by specifying their four intended selections explicitly.


Verification: full `testDebugUnitTest lintDebug assembleDebug --offline` succeeded: 63 tests, zero failures/errors/skips, zero lint errors. Debug APK built at `app/build/outputs/apk/debug/app-debug.apk`. Independent review found no critical or important bugs. Existing shared output/selection behavior retained. No installation, dependency changes, commits, branch changes or edits to dist artifacts.

Envelope test first failed for missing CoastalWindEnvelope; three lifecycle tests first failed for missing COASTAL_WIND, then passed. Asset fully decoded; −24.04 LUFS, −9.42 dBTP, wrap step 0.007267 (~1.40× local sample-difference RMS). Native listening remains unverified.
