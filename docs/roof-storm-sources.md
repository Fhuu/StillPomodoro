# Roof storm audio

Seven recordings are bundled offline under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). The earlier roof-rain sources are restored; the newer kkenny101 tin-shed recording (#851476) with the pronounced metallic tone stays excluded. The restored recordings use their original gentle processing without the newer 1.8 kHz boost.

| Asset (`roof_storm_*.ogg`) | Creator and source | Download used |
| --- | --- | --- |
| `roof` | [Rain on metal roof with distant thunder — DBlover](https://freesound.org/people/DBlover/sounds/404061/) | [HQ preview](https://cdn.freesound.org/previews/404/404061_7846219-hq.mp3) |
| `roof_texture` | [Gentle rain on metal roof — wlabarron](https://freesound.org/people/wlabarron/sounds/509113/) | [HQ preview](https://cdn.freesound.org/previews/509/509113_3782735-hq.mp3) |
| `roof_rumble` | [Rain on metal roof with background thunder — johnaudiotech](https://freesound.org/people/johnaudiotech/sounds/342100/) | [HQ preview](https://cdn.freesound.org/previews/342/342100_6120286-hq.mp3) |
| `downpour` | [Heavy Rain, Distant Thunder — Kinoton](https://freesound.org/people/Kinoton/sounds/393560/) | [HQ preview](https://cdn.freesound.org/previews/393/393560_2247456-hq.mp3) |
| `wind_gusts` | [Heavy gusts of wind — SamsterBirdies](https://freesound.org/people/SamsterBirdies/sounds/565140/) | [HQ preview](https://cdn.freesound.org/previews/565/565140_5487341-hq.mp3) |
| `wind_window` | [200509 Wind, window, cold gusts, moan, whistle, 1pm.wav — TRP](https://freesound.org/people/TRP/sounds/567356/) | [HQ preview](https://cdn.freesound.org/previews/567/567356_97550-hq.mp3) |
| `thunder` | [230823 Thunder, dry distant rolling, R-07 EM272s, Stratford 12pm — TRP](https://freesound.org/people/TRP/sounds/717890/) | [HQ preview](https://cdn.freesound.org/previews/717/717890_97550-hq.mp3) |


## Preparation and verification

High-pass at 40 Hz; low-pass at 10 kHz for rain, 5 kHz for wind, and 2.5 kHz for thunder. Match with fixed gain towards −24 LUFS, using the measured filtered input loudness, then apply memoryless tanh soft limiting for headroom. Resample to stereo 48 kHz before sample-based trimming. Move the intro to the end with a linear overlap crossfade, so the wrap continues from that intro into its original next sample. The short roof texture uses a two-second overlap; the other overlaps are eight seconds except wind-window and thunder, whose overlap points were moved near stereo zero crossings to reduce decoded wrap steps.

| Layer | Gain (dB) | Overlap (samples at 48 kHz) | Duration (s) | LUFS | True peak (dBTP) | Wrap step / local difference RMS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| roof | +7.31 | 384000 | 296.603 | -24.31 | -4.72 | 1.28× |
| roof_texture | +18.59 | 96000 | 8.400 | -24.60 | -13.25 | 0.03× |
| roof_rumble | -0.21 | 384000 | 213.271 | -24.15 | -4.86 | 1.34× |
| downpour | +5.52 | 384000 | 295.187 | -24.25 | -3.53 | 0.75× |
| wind_gusts | -4.27 | 384000 | 47.062 | -24.19 | -5.48 | 1.65× |
| wind_window | +9.81 | 388064 | 43.648 | -25.06 | -8.03 | 2.22× |
| thunder | +4.02 | 383742 | 239.278 | -24.64 | -3.88 | 2.56× |

Reproduce each downloaded `source.mp3`, setting `gain_db`, `overlap_samples`, and `cutoff_hz` from the values above:

```sh
ffmpeg -i source.mp3 -filter_complex \
  "[0:a]highpass=f=40,lowpass=f=${cutoff_hz},aresample=48000,asplit=2[body][head];[body]atrim=start_sample=${overlap_samples},asetpts=PTS-STARTPTS[tail];[head]atrim=end_sample=${overlap_samples},asetpts=PTS-STARTPTS[intro];[tail][intro]acrossfade=ns=${overlap_samples}:c1=tri:c2=tri,volume=${gain_db}dB,asoftclip=type=tanh:threshold=0.65:oversample=1[a]" \
  -map '[a]' -ar 48000 -ac 2 -c:a libopus -b:a 96k loop.ogg
```

All seven delivered assets fully decode; wrap steps are below 0.01 before runtime attenuation and below three times the local neighboring-sample difference RMS. This is a numeric continuity check; phone listening remains unverified.


## Playback

The earlier seven-layer mix is restored: roof rain, gentle roof texture, roof rain with distant thunder, downpour, two winds, and rolling thunder. Maximum gains in that order are 0.28, 0.07, 0.15, 0.12, 0.14, 0.10, and 0.08 (sum 0.94). The stronger metallic foreground mix is not used.

Roof rain enters first over five seconds; other layers enter in random order, spaced 2–6 seconds apart, each over five seconds. All seven are present within 41 seconds. Each layer has independent fractal volume modulation; wind samples noise at double speed. Shared output and built-in group attenuation remain unchanged.

Wooden creaks and window rattles remain independently selectable as [Old house](old-house-sources.md).
