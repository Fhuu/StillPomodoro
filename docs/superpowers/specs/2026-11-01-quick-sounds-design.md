# Quick Settings sounds

One Ambient sounds tile toggles the saved built-in mix. Long press opens a
full-screen selector with five track toggle buttons. Selecting sounds starts
playback immediately; turning all sounds off stops it. No Done or Play button.
Back closes the selector using normal Android navigation.

Playback and saved selection are independent of the session timer and settings.
Use Android TileService and a separate media-playback foreground service reusing
existing sound players and envelopes. Android controls the long-press transition;
this does not embed a custom Samsung panel or reopen Quick Settings on Back.

Settings independently toggle cyberpunk styling (default on) and blurred
background (default off). Both light and dark designs are supported. Dark uses
midnight black, electric blue, and cantaloupe accents; light uses a pale background
with blue and orange accents. Disabled cyberpunk styling uses the system palette.
Blur uses Android cross-window support with an opaque fallback when unavailable.

The saved mix defaults to ocean waves. Opening the selector while playback is off
shows no active sounds. Closing the selector leaves playback running. Stop and
headphone disconnect release playback; process recreation never resumes audio.

Alternatives: separate tiles were rejected in favor of one tile; sharing the timer
service would couple timer lifecycle and standalone playback.
