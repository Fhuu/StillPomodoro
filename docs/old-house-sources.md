# Old house audio

Old house is an independently selectable built-in sound. It contains sparse wooden frame creaks and wooden window/door rattles, with no rain, wind bed, or thunder baked in. Select it together with Roof storm for the sheltered-house atmosphere. The sources are not claimed to be recordings of Japanese or Indonesian houses.

All sources are [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/), downloaded 2026-11-01 as full-duration HQ MP3 previews.

| Source | Creator | Download |
| --- | --- | --- |
| [House_Creak_2.wav](https://freesound.org/people/SholeColtis/sounds/683416/) | SholeColtis | [HQ MP3](https://cdn.freesound.org/previews/683/683416_14670263-hq.mp3) |
| [Creaking in Room 8.wav](https://freesound.org/people/Greg_Surr/sounds/590171/) | Greg_Surr | [HQ MP3](https://cdn.freesound.org/previews/590/590171_8198141-hq.mp3) |
| [FX SaSc Wood Window Door Rattle Small Impact Hit Wind Close 01.wav](https://freesound.org/people/Profispiesser/sounds/583057/) | Profispiesser | [HQ MP3](https://cdn.freesound.org/previews/583/583057_6667441-hq.mp3) |

## Preparation

Decode each source to stereo 48 kHz floats, high-pass at 100 Hz and low-pass at 5 kHz. For frame and cabin use the full recordings. Extract three window fragments at 2–6, 12–16, and 22–26 seconds. Scale each clip to sample peak 0.40, then apply linear 50 ms fades at both edges. Quiet gaps are intentional.

`old_house_frames.ogg`: 97-second silent bed with frame at 7 s (gain 1.0), cabin at 29 s (0.85), frame at 51 s (0.75), and cabin at 79 s (0.90).

`old_house_windows.ogg`: 113-second silent bed with first window fragment at 11 s (0.80), second at 43 s (0.65), third at 76 s (0.90), and first again at 101 s (0.60).

Encode stereo 48 kHz Ogg Opus at 64 kb/s. Both tracks begin and end in silence, so the repeat join has zero sample step. Different loop lengths and independent random seek positions keep the two event patterns moving relative to each other. The quiet gaps also prevent short creaks from turning into a continuous repeating squeak.

## Verification and playback

Both delivered tracks fully decode. Frame duration: 97.000 s, decoded peak 0.41351; window duration: 113.000 s, decoded peak 0.33565. Both wrap steps are zero. About 87.5% and 86.3% of their frames respectively stay below amplitude 0.0001.

OldHouseEnvelope uses independent existing fractal noise per layer, smooth five-second entrance, and maximum gains 0.24 (frames) and 0.18 (windows). Levels vary from 55–100% of each maximum. The gain sum never exceeds 0.42 even for full-scale recordings; measured aligned sample peaks bound this mix below 0.160 before the shared output effect.

Old house shares normal built-in lifecycle and the existing +13 dB output effect with platform peak compression. When combined with other built-ins, every group targets 1 / active group count; outgoing fades remain counted. Pause, disconnect, deselection, immediate stop, fade restart, and automatic ocean breaks are covered by lifecycle tests. Subjective balance and native device playback remain unverified.
