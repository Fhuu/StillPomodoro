# Boat hum

Approved in chat: add a Boat hum built-in toggle using three CC0 real recordings: marine diesel by AugustSandberg (264864), ship engine by monotraum (253606), and cabin engine by timsc (274490). Play samples sequentially, with overlap only during roughly eight-second transitions. Slow fractal noise varies volume gently and varies transition duration. Soften harsh frequencies and match sample loudness. Extract the cabin sample before its engine shutdown around one minute.

Approaches: continuously layer all engine samples (rejected by user); play one at a time with crossfades (selected). Use two Media3 players: one audible current sample and one preloaded next sample, audible only during transitions. Keep current ocean layering unchanged. Built-in toggles may select Ocean waves and Boat hum together; device and YouTube remain exclusive of Built-in. Breaks continue to play Ocean waves only.

Implementation choices: three 48 kHz stereo Ogg Opus assets, high-pass 30 Hz and low-pass 500 Hz, fixed level matching with peak headroom; 6–10 second equal-power transitions, chosen using slow noise; shuffled next sample excluding the current one. Use a five-second entrance fade and existing 2.5-second exit fade. Combined built-ins receive a smooth 0.65 group multiplier, while either sound alone retains full gain. No system-volume change or new runtime dependency.

Pause/stop/disconnect/source changes must cancel pending transitions and release both decks; source changes while paused stay deferred until resume. Handle a late next deck without cutting off the current recording. No physical listening claim without a device check.
