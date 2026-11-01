# GitHub updates

Settings → Check for updates reads published releases from Fhuu/StillPomodoro, including alpha prereleases. Checks and downloads run only after a button press. The app chooses the highest newer version with one APK asset (or one clearly named universal APK), displays download size/progress, and offers installation after validation.

Android may first require Allow app updates for this app. Return to Settings and tap Install update; permission approval alone never installs anything. Android's installer asks for final confirmation. Downloads/check state survive closing Settings and rotation while the app process stays alive.

## Publishing an update

- Publish a GitHub Release in `Fhuu/StillPomodoro`; a tag alone is insufficient.
- Use a higher version such as `v1.1.2-alpha`, with matching APK versionName.
- Increment Android versionCode above the installed build and keep the current signing key.
- Attach a single `.apk` asset, or identify one universal `.apk` if supplying several variants.

The updater requires HTTPS GitHub asset URLs, checks asset size and SHA-256 when supplied by GitHub, and verifies package ID, current certificate, higher versionCode and versionName before opening the installer. Each APK has a unique private-cache filename and is shared read-only through a narrow FileProvider URI. No broad storage permission is used.

This feature needs an initial manual installation. The currently published v1.1.1-alpha predates the updater; later releases can then be downloaded through the app. Native installer confirmation remains required.
