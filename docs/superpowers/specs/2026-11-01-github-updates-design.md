# GitHub release updates

User accepts the proposed Check for updates button, downloading a newer signed APK from GitHub Releases and asking Android to install it. Add this to Settings for the fixed public repository Fhuu/StillPomodoro. Include published alpha/prereleases because all current releases use them. Only user-triggered checks/downloads/install; no silent or scheduled updates.

Use the public releases API (not latest, which omits prereleases), semantic version comparison, and an unambiguous APK asset. Download into app-private cache using existing OkHttp. Check expected size and GitHub SHA-256 when available, then verify Android package ID, higher versionCode, matching release version and current signing certificate. Hand off a read-only FileProvider URI to Android's installer. Request per-app unknown-source permission when necessary; returning to the app exposes Install again. Never install automatically.

Approaches: opening the GitHub page delegates download to a browser but adds manual steps; an in-app download with Android installer confirmation matches the requested flow. Use the latter, with a process-local controller so configuration changes/closing Settings do not duplicate downloads. Android still performs final APK validation and installation.

Keep update logic in com.pomodoro.update and its UI in com.pomodoro.setting. No new dependencies, version bump, commits, push or automatic device installation. Preserve all existing settings/audio/session behavior. Future releases must attach an APK signed with the same key and a greater versionCode.
