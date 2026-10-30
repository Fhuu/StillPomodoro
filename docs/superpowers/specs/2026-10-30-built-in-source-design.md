# Built-in ambient source

Add Built-in as the third audio source beside On device and YouTube. Ocean waves is the single initial catalog item and uses the existing five-recording, fractal-modulated ocean mix at its current louder default gain. Use checkbox selection and persist a set of built-in sound identifiers so selection supports layering within Built-in. Do not mix built-in sounds with local files or YouTube. Preserve default ocean playback during breaks.

The user chose built-in-only layering rather than overlays across sources. No additional sounds, downloads, per-layer controls, or new runtime dependencies are part of this change. A single enum catalog and stored set are sufficient; do not introduce a generalized mixing framework for future sounds.

Source changes apply during active, unpaused focus sessions without resetting the timer. Pause, stop, disconnect, source switching, metadata, and skip controls must respect the new source. An empty built-in selection disables focus audio. Switching tabs restores the previous nonempty built-in selection or defaults to Ocean waves.
