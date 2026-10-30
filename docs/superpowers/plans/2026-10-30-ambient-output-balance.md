# Ambient Output Balance Implementation Plan

> **For agentic workers:** Execute inline, preserving existing uncommitted changes. No branch changes or commits.

**Goal:** Audible built-in ambience that coexists with another music app.
**Architecture:** Dedicated shared built-in audio session with Android LoudnessEnhancer; compensate existing boat gain before the effect, preserve all source envelopes, and opt built-ins out of audio-focus acquisition.
**Tech Stack:** Existing Kotlin/Media3/Android audio effects/Robolectric.
**Spec:** `docs/superpowers/specs/2026-10-30-ambient-output-balance-design.md`

## Constraints and review focus
- Never use session zero or change system volume; device/YouTube sessions remain separate.
- Preserve source audio bytes, fractal modulation and transition timing.
- Retain boat level when enabling the shared boost; fallback retains previous levels.
- All-player cleanup must release the effect; late callbacks cannot resurrect it.
- Built-in start must not request focus or fade/mute another app.

### Task 1: Shared output effect
- [x] Add failing tests for gain/session configuration, system-volume preservation, compensation and release/late callbacks.
- [x] Implement BuiltInAudioOutput and configure only built-in Media3 players with its dedicated session.

### Task 2: Integrate and verify
- [x] Add failing tests that built-in playback does not request audio focus.
- [x] Activate/release the effect with built-in playback; apply boat compensation immediately when effect state changes.
- [x] Run full tests, lint, independent review and release build; export the next signed dist APK.

Validation: 41 unit tests pass; lint has zero errors and 45 existing warnings; release build succeeds. Independent review found no important issues. Source recordings match their pre-change snapshots. Native DSP/listening requires a phone check. Signed release exported as `dist/just-pomodoro-v0.17-media-player.apk`.
