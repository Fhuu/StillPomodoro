# Changelog

Versions use `MAJOR.MINOR.PATCH` with an `-alpha` suffix for preview releases.
Git tags identify releases; local APK iteration numbers such as `v0.18` are build labels.

## 1.2.0-alpha — 2026-11-01

### Added

- Android Quick Settings tile for ambient playback independent of the session timer.
- Long-press sound selector with five track toggles and immediate playback control.
- Separate Settings toggles for cyberpunk styling and a full-screen blurred background.
- System-following app dark mode with midnight black, electric blue, and cantaloupe accents.
- Tests for standalone playback, saved settings, headphone disconnect, cleanup, and tile updates.

### Changed

- Match the audio selector's light and dark designs to the updated app palette.
- Use an orange Begin focus button in dark mode and a transparent in-app logo background.

## 1.1.2-alpha — 2026-11-01

### Added

- Check for updates in Settings using published GitHub Releases, including alpha versions.
- In-app APK downloads with progress and Android installation confirmation.
- APK size, SHA-256, package, signing-certificate, and newer-build validation before installation.
- Coverage for release selection, download integrity, permission handling, and installer handoff.

## 1.1.1-alpha — 2026-11-01

### Added

- Roof storm with seven offline roof-rain, wind, and thunder recordings blended with independent fractal volume modulation.
- Separately selectable Old house ambience with subtle wooden creaks and window rattles.
- Separately selectable Coastal wind using a natural coastal-path recording with slow fractal variation.
- Lifecycle and mixing coverage for the five built-in sounds, including pause/resume, output routing, fade cancellation, and combined gain restoration.

### Changed

- Group audio playback, device-folder selection, and YouTube code under `com.pomodoro.sound`.
- Group island UI under `com.pomodoro.island` and settings UI under `com.pomodoro.setting`.
- Balance simultaneous built-in sound groups by their active count, including outgoing fades.
- Retain the earlier roof-rain recordings and exclude the newer strongly metallic tin-shed recording.
- Ignore saved Japanese and bamboo chime selections after removing those experimental sounds.

## 1.1.0-alpha — 2026-10-30

### Added

- Focus audio from an on-device file or folder, a YouTube playlist, or bundled built-in sounds.
- Five offline ocean recordings, layered with staggered starts and slow fractal volume modulation.
- Three offline boat-engine recordings, played sequentially with smooth, noise-varied crossfades.
- Toggle buttons to select and layer built-in sounds together.
- Next-track controls for device folders and YouTube playlists, including media-session support.
- Automated coverage for built-in selection, playback lifecycle, blending, and output gain.

### Changed

- Stronger built-in output without changing phone volume: a shared +13 dB output target, with compensation for the boat recordings' existing boost and platform peak compression.
- Built-in ambience allows other music apps to play alongside it without requesting audio focus.
- Source changes refresh playback, titles, artwork, and notifications; changes made while paused apply on resume.
- Ocean recordings use prepared loop joins and independent random starting positions. Breaks continue to use ocean ambience.

### Fixed

- YouTube extraction uses a desktop browser identity, updated extractor, and matching playback headers.
- YouTube playback prefetches stream URLs, refreshes failed URLs, and reports loading errors.
- Built-in audio stops and releases its players and effects when playback ends or headphones disconnect.
- Persisted file-access flags and scoped Android lint annotations.

## 1.0.2-alpha — 2026-10-29

- Replaced frequent service ticks with alarm-driven session scheduling.
- Reduced notification and island animation updates to lower background activity.
- Fixed countdown completion and reliable ambient stopping on pause and session stop.
- Updated duration controls, settings, and widget text.
- Historical Git tag: `V1.0.2-Alpha`.

## 1.0.1-alpha — 2026-10-28

- Fixed session notification controls and ambient pause/resume behavior.
- Improved Samsung Live Notification foreground behavior.
- Historical Git tag: `V1.0.1-alpha`.

## 1.0-alpha — 2026-10-28

- Initial alpha with focus/break timing, ambient crossfades, dynamic island styles, and Now Bar updates.
- Saved island position, size, and ring color for each compact style.
- Historical Git tag: `v1.0-alpha`.
