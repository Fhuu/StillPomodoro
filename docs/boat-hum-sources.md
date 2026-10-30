# Boat hum audio

Boat hum is a built-in toggle. Its three real recordings play one at a time, with overlap only during equal-power crossfades. A silent next recording is prepared ahead of time. All sources are **CC0 1.0**, usable for modification and redistribution: https://creativecommons.org/publicdomain/zero/1.0/ .

| Recording | Creator/source | HQ download | Source excerpt | Fixed gain | Delivered duration |
| --- | --- | --- | --- | --- | --- |
| Marine diesel engine | [AugustSandberg, 264864](https://freesound.org/people/AugustSandberg/sounds/264864/) | [MP3](https://cdn.freesound.org/previews/264/264864_1934171-hq.mp3) | 3–70 s | −2.05 dB | 66 s |
| Ship engine | [monotraum, 253606](https://freesound.org/people/monotraum/sounds/253606/) | [MP3](https://cdn.freesound.org/previews/253/253606_1756543-hq.mp3) | 0–26.7 s | +0.17 dB | 25.7 s |
| Large ship engine recorded in cabin | [timsc, 274490](https://freesound.org/people/timsc/sounds/274490/) | [MP3](https://cdn.freesound.org/previews/274/274490_266678-hq.mp3) | 5–50 s, before engine shutdown | −11.14 dB | 44 s |

Downloaded 2026-10-30 from full-duration high-quality previews, not lossless originals. Each excerpt is high-pass filtered at 30 Hz and low-pass filtered at 500 Hz to emphasize a soft low hum. Base fixed gain targets approximately −23 LUFS; memoryless soft limiting shapes peaks. A further +6 dB is baked into each file after that limiter, and +4 dB is applied in the playback envelope, for +10 dB standalone Boat hum gain over v0.15 without exceeding Media3’s per-player volume limit. Output: 48 kHz stereo Ogg Opus, 128 kb/s.

The first second is overlap-crossfaded onto the end to give each recording a continuous wrap. Normally playback changes to another recording near the end. The prepared wrap lets the current sound continue if the next player is late. All three files decode fully; measured seam steps were 0.003672, 0.000550, and 0.001190 respectively, within ordinary neighboring waveform differences.

To reproduce, set `start_sec`, `end_sec`, and `gain_db` from the table:

```sh
ffmpeg -i source.mp3 -filter_complex \
  "[0:a]atrim=start=${start_sec}:end=${end_sec},asetpts=PTS-STARTPTS,highpass=f=30,lowpass=f=500,asplit=2[body][head];[body]atrim=start=1,asetpts=PTS-STARTPTS[tail];[head]atrim=end=1,asetpts=PTS-STARTPTS[intro];[tail][intro]acrossfade=d=1:c1=tri:c2=tri,volume=${gain_db}dB,asoftclip=type=tanh:threshold=0.65:oversample=1,volume=6dB[a]" \
  -map '[a]' -ar 48000 -ac 2 -c:a libopus -b:a 128k boat_hum.ogg
```

## Playback

- Random initial recording; each subsequent recording differs from the current one.
- Shared smooth fractal noise at 23, 61, and 173 second scales changes gain gently and chooses transition duration between 6 and 10 seconds, capped at one third of the shorter recording.
- Crossfade progress follows incoming playback position. Waiting or buffering does not advance it on wall-clock time.
- Boat hum enters over five seconds and exits over 2.5 seconds. Incoming preloads remain silent until a transition.
- Selecting both built-ins smoothly attenuates each group to 0.50; returning to one restores its full standalone gain. Conservative wave/boat peak bounds keep the combined mix below 0.95 amplitude even during a crossfade. System volume is never modified.
- Breaks retain Ocean waves only. Device and YouTube remain exclusive sources.

No physical listening, Bluetooth, or screen-off device verification was performed for this change.

Final measured integrated loudness after the +6 dB asset boost: diesel −17.18 LUFS, ship −17.20 LUFS, cabin −17.14 LUFS. True peaks are −4.73, −1.89, and −6.59 dBTP respectively, before playback gain. Conservative amplitude bounds of 0.60, 0.85, and 0.50 for the three distinct samples keep solo crossfades below 0.95 amplitude. Combined playback is attenuated more than v0.15 to prevent clipping; its resulting increase is lower than the full standalone +10 dB.
