# Launch animation

The local preview uses the user-supplied CherryApp Family video as an in-app launch intro, after Android's system splash. The original app icon is preserved.

- Asset: `app/src/main/res/raw/cherry_launch.mp4`.
- Source selection: seconds 3.8–6.6 of the supplied 10-second video, at the original speed (2.8 seconds).
- Portrait 720 × 1280; audio track removed. Passthrough export retains the original compressed video frames without re-encoding.
- Fits the complete original frame without center-crop zoom; the cherry background fills the remaining screen area. The source is 720p, so higher-resolution displays cannot gain detail beyond that source.
- Uses Android MediaPlayer and a single TextureView; no video-player library or bitmap frame cache.
- Shows once per process on a normal launcher start. External document intents, rotation, and warm returns skip it.
- Skip/Back dismiss it; errors and a five-second watchdog continue into the app. Playback pauses with the activity and the decoder/surface are released when the overlay is removed.

Physical-device coverage is in `LaunchIntroInstrumentedTest`: actual frame rendering, muted source, automatic completion and decoder release, direct file opening, recreation, and warm relaunch.
