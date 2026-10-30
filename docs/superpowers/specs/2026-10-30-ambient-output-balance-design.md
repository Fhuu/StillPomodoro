# Ambient output balance and coexistence

User clarification: built-in ambience is too quiet alongside music from another app; it must be louder without blocking that audio. Preserve the original recordings and the existing fractal layering/timing. A measured representative ocean mix is −29.7 LUFS before the existing combined-built-in attenuation, confirming low output gain.

Use one dedicated, nonzero Android audio session shared by the built-in wave and boat players. Attach a LoudnessEnhancer with a +10 dB target after their shared session mix; Android compresses samples that exceed the supported range. Keep device and YouTube sessions separate. Boat hum already has a +10 dB asset/envelope boost in v0.16, so compensate its pre-effect gain by 1/sqrt(10) while the new output effect is enabled to retain its established level rather than doubling the boost. Preserve the 0.5 combined-built-in gain rule.

Built-in ambience deliberately does not request audio focus. Leave device/YouTube focus behavior unchanged. Release the shared output effect when all built-ins stop; late audio-session callbacks must not recreate it. If the device cannot create/enable the effect, log the failure and retain previous playback levels. Never attach effects to session zero or alter system volume.

No UI change, new runtime library, or remastered audio asset. Physical listening and native DSP behavior need phone verification; unit tests verify API configuration, gain compensation, session isolation, focus behavior, and cleanup.
