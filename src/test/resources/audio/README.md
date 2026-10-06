Synthetic 1-second, 440 Hz mono tones generated for decoder and playback tests.
No user recordings or third-party audio are included. Fixtures are covered by the repository license.

Generated with FFmpeg from `sine=frequency=440:sample_rate=48000:duration=1`.
The WAV uses signed 16-bit PCM; the other files are encodings of that synthetic WAV.
`tone.24.flac` uses FFmpeg's `-sample_fmt s32` FLAC encoding (24-bit), and `tone.float.wav` uses `pcm_f32le`.
FFmpeg is only a fixture-generation tool and is not needed to build or run the application or tests.

`tone.flac.ogg` contains the same synthetic WAV encoded with `-c:a flac -f ogg`; it exercises FLAC inside Ogg
separately from Vorbis and the native FLAC container.

Opus waveform fixtures also include 1.013-second stereo tones (440/660 Hz at levels 0.2/0.8), a 1.013-second
six-channel tone, and a three-second mono tone with one-second silence/0.1/0.8 level segments. The non-integral
packet duration exercises final padding removal; fixtures were generated with FFmpeg's `libopus` encoder.
