# Ocean ambience audio

All five recordings are bundled offline under **CC0 1.0**:
https://creativecommons.org/publicdomain/zero/1.0/ .

| Asset | Creator and source | Download used | Original duration |
| --- | --- | --- | --- |
| `break_ocean_calm.ogg` | [Calm ocean waves — SamsterBirdies](https://freesound.org/people/SamsterBirdies/sounds/578524/) | [Full-duration HQ MP3 preview](https://cdn.freesound.org/previews/578/578524_5487341-hq.mp3) | 2:59.710 |
| `break_ocean_gentle.ogg` | [Gentle Ocean Waves Mix (2018) — esh9419](https://freesound.org/people/esh9419/sounds/417797/) | [Full-duration HQ MP3 preview](https://cdn.freesound.org/previews/417/417797_3285887-hq.mp3) | 11:59.769 |
| `break_ocean_lapping.ogg` | [Gentle small waves lapping on shore — Alex_hears_things](https://freesound.org/people/Alex_hears_things/sounds/352356/) | [Full-duration HQ MP3 preview](https://cdn.freesound.org/previews/352/352356_2309965-hq.mp3) | 2:44.627 |
| `break_ocean_shingle.ogg` | [Waves on a Shingle Beach — Mr_Lamppost](https://freesound.org/people/Mr_Lamppost/sounds/800900/) | [Full-duration HQ MP3 preview](https://cdn.freesound.org/previews/800/800900_15570225-hq.mp3) | 2:14.000 |
| `break_ocean_distant.ogg` | [Distant waves from a cliff — chris_dagorne](https://freesound.org/people/chris_dagorne/sounds/426074/) | [Full-duration HQ MP3 preview](https://cdn.freesound.org/previews/426/426074_3380363-hq.mp3) | 1:02.375 |

The second recording incorporates CC0 recordings by subtyrant ("Xerokambos beach 01-Sep-11 60m 24bit.flac") and indieground ("Marée basse / Low tide Sables D'Olonne France"), credited by its creator.

## Preparation

Downloaded 2026-10-30. These are processed HQ previews, not lossless originals. Each source has its first eight seconds moved to the end with an eight-second linear overlap crossfade. The wrap then continues from that intro into its original next sample, rather than jumping between unrelated endpoints. The original two tracks receive fixed gains derived from measured loudness (−2.60 dB for calm, +15.15 dB for gentle, targeting approximately −23 LUFS), followed by gentle memoryless soft limiting for peak headroom, and are encoded as stereo 48 kHz Ogg Opus at 128 kb/s. Resulting loop durations are about 2:51.71 and 11:51.77.

Reproduce for each downloaded source (`source.mp3`) and destination (`loop.ogg`), setting `gain_db` to `-2.6` (calm) or `15.15` (gentle):

```sh
ffmpeg -i source.mp3 -filter_complex \
  "[0:a]asplit=2[body][head];[body]atrim=start=8,asetpts=PTS-STARTPTS[tail];[head]atrim=end=8,asetpts=PTS-STARTPTS[intro];[tail][intro]acrossfade=d=8:c1=tri:c2=tri,volume=${gain_db}dB,asoftclip=type=tanh:threshold=0.65:oversample=1[a]" \
  -map '[a]' -ar 48000 -c:a libopus -b:a 128k loop.ogg
```

Playback uses Media3 repeat-one for all five simultaneous layers. Each break chooses independent random seek positions and a random starting order. Each successive recording fades in 5–15 seconds after the preceding one, over five seconds; all five are present within 65 seconds. Smooth value noise at 23, 61, and 173 second scales controls each layer's strength. Base gains in source order are 0.16, 0.16, 0.14, 0.14, and 0.08, multiplied by 1.99733 (about +6 dB total; an additional +3 dB over v0.12) for louder default playback without changing system volume. The boosted gains sum to at most 1.3581844 before fades. The bundled recordings remain below full scale: decoded maximum levels are −5.5, −3.1, −2.3, −6.9, and −10.0 dBFS in source order. Including 0.1 dB measurement rounding, their worst-case aligned mix is below amplitude 0.794 (about 2 dB of headroom). Tests also check conservative per-recording peak bounds, rather than assuming the gain sum alone determines clipping. Distant surf is intentionally a quieter background layer. A local playback wake lock allows the modulation to continue with the screen off.

Fixed gain is intentional: dynamic loudness normalization changed gain between the beginning and end, making the loop seam less continuous. Decode checks on the delivered files found the wrap sample steps within ordinary neighboring sample differences (about 2.2× and 1.7× local sample-difference RMS).

Final measured integrated loudness: calm −23.21 LUFS; gentle −23.45 LUFS. Decoded true peaks: −5.52 dBTP and −3.08 dBTP, before runtime mix attenuation.

## Additional recording preparation

The three added recordings use the same fixed loudness matching and memoryless soft limiting. Overlaps for shingle and distant surf were shifted slightly to a join with a smaller neighboring stereo sample step (shingle) or near a stereo zero crossing (distant).

| Track | Fixed gain | Overlap at 48 kHz | Processed duration |
| --- | --- | --- | --- |
| Lapping | +7.64 dB | 384000 samples (8 s) | 2:36.628 |
| Shingle | −4.02 dB | 387715 samples (8.077396 s) | 2:05.923 |
| Distant | +5.04 dB | 399599 samples (8.324979 s) | 0:54.051 |

For exact reproduction set `overlap_samples` and `gain_db` from this table:

```sh
ffmpeg -i source.mp3 -filter_complex \
  "[0:a]asplit=2[body][head];[body]atrim=start_sample=${overlap_samples},asetpts=PTS-STARTPTS[tail];[head]atrim=end_sample=${overlap_samples},asetpts=PTS-STARTPTS[intro];[tail][intro]acrossfade=ns=${overlap_samples}:c1=tri:c2=tri,volume=${gain_db}dB,asoftclip=type=tanh:threshold=0.65:oversample=1[a]" \
  -map '[a]' -ar 48000 -ac 2 -c:a libopus -b:a 128k loop.ogg
```

The additional delivered loops decode fully. Wrap sample steps relative to local sample-difference RMS are approximately 0.82× (lapping), 0.20× (shingle), and 1.33× (distant); all absolute steps are below 0.01 before runtime attenuation.

Ocean waves is also available as a selectable item in the Built-in focus-audio source. Built-in selections are saved as a set and shown as toggle buttons; built-in audio is exclusive of device and YouTube audio. The automatic ocean mix during breaks remains available independently of the focus selection.

A second built-in sound, [Boat hum](boat-hum-sources.md), sequences real engine recordings with smooth crossfades rather than continuously layering its samples. Selecting both built-ins applies a smooth 0.50 gain multiplier to each group; selecting either alone restores its standalone gain.

## Built-in output gain (v0.18)

All built-in players share a dedicated nonzero Android audio session with a LoudnessEnhancer targeting +13 dB after mixing (another +3 dB over v0.17), with platform peak compression. This raises ocean output without changing system media volume, source files, fractal envelopes or loop joins. Boat hum receives compensating pre-effect attenuation because its existing v0.16 gain already supplies +10 dB; it also receives the additional +3 dB. If the effect is unavailable, playback retains the previous gain settings. Built-ins do not request audio focus, allowing another music app to play alongside them. Device and YouTube playback retain their separate audio handling.

Automated tests verify effect configuration, session isolation, unchanged system volume and cleanup. Actual device effect support and listening levels still require a phone check.
