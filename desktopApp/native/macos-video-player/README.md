# macOS video player without the audio tap

ComposeMediaPlayer (`io.github.kdroidfilter:composemediaplayer`, MIT) plays desktop videos on
macOS through a small Swift library, `libNativeVideoPlayer.dylib`, bundled in its jar. That
library attaches an audio processing tap to every video. The tap passes the sound through
unchanged, a leftover of an audio-level display upstream removed in 7b8cc87, and macOS's audio
thread crashes in its callback (`AudioQueueProcessingTapGetSourceAudio` under `tapProcess`, a null
read), often within minutes of scrolling a video feed.

This directory is that library's native source at the version in `VERSION`, with the tap
removed (see the header of `NativeVideoPlayer.swift`). `:desktopApp:buildMacVideoPlayer` compiles
it on macOS, the same `clang` + `swiftc` steps as upstream's `build.sh`, and the result ships in
the app's resources, signed with the app. The library loads its native player from
`java.library.path` before it unpacks the one in its jar, and the macOS app sets that path to its
resources, so ours is the one that runs. The log says which at the first video
(`GlobalMediaPlayer`: "macOS video player: …").

Measured on an M2 Max, same app code: the shipped player crashed within 3.5 minutes; this one
played about 175 videos over five sessions without a crash.

## Updating ComposeMediaPlayer

The JNI functions are registered by name from `jni_bridge.c`, so this build must match the jar's
Kotlin side. The build fails when `composemediaplayer` in `gradle/libs.versions.toml` differs from
`VERSION`. On a bump: if the new release still has the tap, copy its `NativeVideoPlayer.swift` and
`jni_bridge.c` here, remove the tap the same way, and update `VERSION`; if it no longer has the tap,
delete this directory, the build task and the `java.library.path` arguments.
