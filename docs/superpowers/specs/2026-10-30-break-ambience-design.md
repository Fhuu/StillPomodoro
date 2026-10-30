# Layered break ambience

Approved in chat on 2026-10-30: replace the short break recording with both selected CC0 ocean recordings, playing simultaneously from random positions with staggered fade-ins. Independently vary their volumes gently with slow fractal noise, keeping a quiet background and bounded combined volume. Prepare seamless loop boundaries and bundle the audio for offline playback. Stop both layers on pause, stop, phase change, or headphone disconnection.

Approaches considered: choose one recording per break (less variety); mix both with fixed volumes (repetitive balance); mix both with staggered starts and smooth multiscale modulation (selected). Use the existing Media3 dependency for two looping players; keep focus playback and UI unchanged. No new runtime dependency or audio synthesis is needed. Implementation choices: 5–15 second stagger, 5 second fade-ins, 2.5 second fade-outs, smooth volume changes over tens of seconds to minutes, and total layer gain below 0.7.
