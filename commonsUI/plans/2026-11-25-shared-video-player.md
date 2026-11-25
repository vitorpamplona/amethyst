# One video player UI for Android and iOS

Status: in progress (2026-11-25). Implements iOS plan step B4 (`commons/plans/2026-10-09-ios-app.md`)
by sharing Android's player UI instead of writing a third one.

## Why

Android's player is ~9.1k lines in `amethyst/service/playback`, ~6k of them composables that talk
to a media3 `Player`. Desktop has its own ComposeMediaPlayer player; iOS has none (videos render as
links). The composables use about 30 `Player` members, so the UI can move to `commonsUI` behind a
small interface while each platform keeps its engine.

## Shape

```
commonsUI/commonMain  ui/playback/
  player/AmethystPlayer.kt      interface the UI talks to (subset of media3 Player)
  player/PlaybackPlatform.kt    LocalPlaybackPlatform port: controller lookup, surface, platform buttons
  composable/…                  VideoView, VideoViewInner, RenderVideoPlayer, mutex, errors
  controls/…                    top/center/bottom buttons, speed, quality, captions, skip, progress
  wavefront/…                   waveform + playing animation
amethyst (Android)              Media3Player : AmethystPlayer (wraps media3 Player/MediaController),
                                AndroidPlaybackPlatform (PlaybackService pool, ContentFrame, PiP,
                                Cast, brightness/volume swipe, HDR, keep-screen-on)
iosApp                          AvPlayer : AmethystPlayer (AVPlayer + KVO/periodic observer),
                                IosPlaybackPlatform (AVPlayerLayer in UIKitView, player pool,
                                AVAudioSession, MPNowPlayingInfoCenter/MPRemoteCommandCenter,
                                AVPictureInPictureController, AVRoutePickerView for AirPlay)
```

### `AmethystPlayer`

State: `mediaId`, `volume` (var), `isPlaying`, `playWhenReady` (var), `playbackState`
(IDLE/BUFFERING/READY/ENDED), `currentPosition`, `duration`, `bufferedPosition`, `videoSize`
(w, h, pixel ratio), `error`, `playbackSpeed` (var), `repeatOne` (var).
Actions: `play`, `pause`, `prepare`, `seekTo`, `seekToDefaultPosition`.
Tracks: `videoTracks` / `selectVideoTrack` / `clearVideoTrackOverride` / `hasVideoTrackOverride`,
`constrainToViewport(w, h)`, `textTracks` / `selectTextTrack(null = off)`, `isAudioOnly`.
Listener with default no-op callbacks mirroring the media3 ones the UI uses: `onIsPlayingChanged`,
`onPlaybackStateChanged`, `onVolumeChanged`, `onVideoSizeChanged`, `onTracksChanged`,
`onPlayerErrorChanged`, `onPositionDiscontinuity`, `onMediaItemTransition`, `onCues(text)`.

The Android adapter forwards each member to the media3 `Player`, so moved code keeps its behaviour.

### What stays per platform (port members)

- obtaining a controller for a media item (Android: `PlaybackServiceClient` + pool + 30s background
  release; iOS: pool of `AVPlayer`s keyed by URL);
- drawing the picture (`ContentFrame` / `AVPlayerLayer`), HDR;
- PiP, Cast/AirPlay, saving a file, fullscreen brightness/volume swipe (system APIs);
- keep-screen-on; background/"keep playing" media.

## Feature checklist (Android parity on iOS)

- [ ] autoplay rules (always / Wi-Fi / never, tap-to-load card with size), share-as-image still
- [ ] one video plays at a time (nearest the centre), pause when hidden / app backgrounded
- [ ] muted by default, global mute carried to the next video
- [ ] blurhash/thumbhash placeholder, aspect ratio from `dim` then measured
- [ ] controls: tap to show, double-tap skip ±10s, progress + time, center play/pause, paused overlay
- [ ] top buttons from the shared button-layout settings, overflow menu
- [ ] fullscreen (+ volume/brightness swipe), speed, quality (HLS renditions), captions
- [ ] loop with the 5-replay cap, resume position
- [ ] errors with "open in browser"
- [ ] audio: waveform voice notes, audio visualizer, music / podcast / episode players
- [ ] background audio + lock-screen controls and artwork
- [ ] PiP, AirPlay (Cast on Android), share, download
- [ ] disk cache + prefetch, player pool limits
- [ ] voice recording

## Order

1. Interface + port in `commonsUI`; Android adapter. Move the composables, Android compiles and
   behaves the same (checked on a device before merge).
2. iOS engine and platform; inline video, then audio, background, PiP, AirPlay, cache.
3. Desktop can move onto the same UI later (its engine would implement `AmethystPlayer`).
