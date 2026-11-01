# Coastal wind audio

Source: [strong wind on coastal path.wav — bruno.auzet](https://freesound.org/people/bruno.auzet/sounds/706471/), [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). Downloaded 2026-11-01 as the [full-duration HQ MP3 preview](https://cdn.freesound.org/previews/706/706471_11519060-hq.mp3); the original recording is 267.5 seconds. This is real coastal-path wind, with its natural gusts retained. No separate surf, thunder or roof recording is mixed in.

Preparation: high-pass at 50 Hz, low-pass at 6.5 kHz, resample to stereo 48 kHz. Use fixed gain +7.40 dB towards −24 LUFS followed by memoryless tanh soft limiting (threshold 0.65). Move the first 393367 samples to the end using a linear overlap crossfade of that length. The overlap point was moved near a stereo zero crossing to reduce wrap discontinuity. Encode Ogg Opus at 96 kb/s as `coastal_wind.ogg`.

Final duration 259.305 s; loudness -24.04 LUFS; true peak -9.42 dBTP. Full decode passes; wrap step 0.007267, 1.40× local sample-difference RMS.

CoastalWindEnvelope applies a smooth five-second entrance and gentle existing fractal modulation at 70–100% of maximum gain 0.65. Natural gusts come from the recording; no synthesized wind is added. The decoded peak of 0.336247 bounds the solo pre-effect output below 0.218561. Playback uses the standard shared output, group attenuation, and stop/routing behavior. Combine with Ocean waves or Roof storm using their separate toggles. Native listening remains unverified.
