# Feature package organization

User requests sound code under `com.pomodoro.sound`, including folder/device selection and all YouTube selection/download/playback; island code under `com.pomodoro.island`; settings code under `com.pomodoro.setting`.

Move audio playback, source pickers, library, metadata artwork, YouTube helpers, built-in catalog, output and envelope classes to sound. Move IslandOverlay, IslandProgressBar, IslandRingView, PillBackgroundView and OverlayPrompt to island. Move SettingsDialog and its local controls to setting. Keep shared timer/session state, scheduling, notifications, activity entry points, theme and widgets in timer. Keep existing names and visibility; update imports and move audio-focused tests alongside sound classes. No behavior changes from the package moves, preference-key changes, manifest component changes, application ID changes, or class splitting.

Use direct moves and explicit imports; compatibility aliases would preserve obsolete package paths and are unnecessary for this single app module.
