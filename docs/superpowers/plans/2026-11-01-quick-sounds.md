# Quick Sounds Implementation Plan

> Execute inline with test-first implementation and verification.

**Goal:** Independent ambient playback through one Android Quick Settings tile.
**Architecture:** Separate saved mix, foreground service, and built-in player group;
TileService toggles playback, preferences activity presents sound toggle buttons.
**Tech Stack:** Kotlin, Compose, Android TileService, existing ExoPlayer wrappers.
**Spec:** docs/superpowers/specs/2026-11-01-quick-sounds-design.md

## Constraints
No timer settings or lifecycle changes; no new dependencies; five existing sounds.

## Review focus
Empty mix; stale tile state after stop; headphone disconnect; service destruction;
long-press intent resolves to the sound panel.

## Tasks
- [x] Add failing Robolectric tests for default/persisted mix, timer independence,
  service stop cleanup, and long-press activity resolution. Run targeted tests.
- [x] Implement QuickSoundsStore, QuickSoundsPlayer and QuickSoundsService under sound/.
  Save only selected tracks; keep playing state in process. Use distinct notification ID.
- [x] Implement AmbientTileService and SoundPanelActivity; declare manifest services,
  preferences intent, dialog activity and vector tile icon.
- [x] Run targeted tests, full testDebugUnitTest, lintDebug, assembleDebug offline.
  Inspect diff and manifest wiring. Leave changes uncommitted for review.

## Verification

79 unit tests passed; lintDebug and assembleDebug passed offline.
Focused review found stale tile state: fixed by updating the listening tile
directly and refreshing on each shade opening; regression test covers start/stop.
Existing lint warnings remain; no new feature warnings. Installed successfully on the connected Samsung phone; visual UI not device-tested.
