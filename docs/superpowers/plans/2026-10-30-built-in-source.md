# Built-in Ambient Source Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement inline; preserve the current uncommitted checkout without commits or branch changes.

**Goal:** Select the existing ocean mix as a third focus-audio source with built-in-only set selection.
**Architecture:** One enum catalog, persisted selection set, checkbox section, and reuse of the ocean player in AmbientPlayer. Update exhaustive source handling and add a guarded refresh action for active focus sessions.
**Tech Stack:** Existing Kotlin, Compose, SharedPreferences, Media3, JUnit, Robolectric.
**Spec:** `docs/superpowers/specs/2026-10-30-built-in-source-design.md`

## Global Constraints
- Built-in audio never layers with device or YouTube sources.
- Only Ocean waves is available initially; selection is a set.
- Preserve all five ocean layers, modulation, gain, and normal break behavior.
- Preserve existing uncommitted changes; no new runtime dependencies.

## Review Focus
- Leaving Built-in stops its focus playback; breaks still use waves independently.
- Pause/resume, stop, and earphone disconnect affect built-in playback.
- Saved source selections survive switching sources without playing concurrently.
- Empty/unknown built-in selections cannot start unwanted audio.
- Live selection changes never restart the timer or resume a paused session.

### Task 1: Selection state and third-source UI
- [x] Add failing store tests for saved sets, default selection, deselection, unknown IDs, and exclusive source switching.
- [x] Add BuiltInAmbientSound enum and BUILT_IN mode; implement filtered persistent selection and built-in activation.
- [x] Add Built-in tab and checkbox list; update label/artwork/skip handling.

### Task 2: Playback and verification
- [x] Add failing lifecycle tests for built-in focus start, pause/resume, stop, disconnect, and switching to OFF/device.
- [x] Reuse the ocean mixer for built-in focus; retain break behavior and add guarded live source refresh.
- [x] Run all tests, lint, release build, and independent review; export the next signed dist APK using the established convention.

## Verification

- Added five persistent-selection tests, three focus lifecycle tests, and two live-service regression tests. Existing ocean modulation and lifecycle tests remain passing: 21 tests in total.
- Service regressions first reproduced stale skip controls and resumption of a paused local track after selecting Built-in; both now pass after deferred refresh and media-session synchronization fixes.
- Debug lint/build pass with zero lint errors. Independent review found no remaining issues and all fixed review comments were removed.
- No new runtime dependencies or audio assets. The existing five-layer ocean mix and its +3 dB default boost are reused.
- No physical device listening or UI verification was performed in this task.
