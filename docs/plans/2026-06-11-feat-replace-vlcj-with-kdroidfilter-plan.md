---
title: Replace vlcj with kdroidFilter ComposeMediaPlayer + JCodec/Jaffree
type: feat
status: active
date: 2026-06-11
deepened: 2026-06-11
origin: docs/brainstorms/2026-06-11-vlcj-replacement-migration-brainstorm.md
---

# Replace vlcj with kdroidFilter ComposeMediaPlayer + JCodec/Jaffree

## Enhancement Summary (Deepened 2026-06-11)

**Research artifacts (companion files):**
- [`_kdroidfilter-api-notes.md`](_kdroidfilter-api-notes.md) — verified kdroidFilter public API (note: pinned to **0.10.0** in `libs.versions.toml`; the research agent referenced 0.10.1 which does not exist on Maven Central as of 2026-06-11. API contract is the same across 0.10.x.)
- [`_jcodec-thumbnail-recipe.md`](_jcodec-thumbnail-recipe.md) — concrete JCodec → Skia ImageBitmap path
- [`_macos-ffmpeg-signing-recipe.md`](_macos-ffmpeg-signing-recipe.md) — jpackage nested-binary codesign recipe
- [`_flathub-manifest-recipe.md`](_flathub-manifest-recipe.md) — Gitnuro-style Compose Desktop manifest
- [`_notice-and-licenses-recipe.md`](_notice-and-licenses-recipe.md) — cashapp/licensee + AboutLibraries pattern
- [`_vlcj-migration-learnings.md`](_vlcj-migration-learnings.md) — institutional learnings from prior media work
- [`_vlcj-migration-codebase-shape.md`](_vlcj-migration-codebase-shape.md) — file inventory (if regenerated)

### Material corrections to original plan

These supersede the corresponding sections below — implementer should use the new specs:

1. **kdroidFilter API names** (Phase 1 was wrong; corrected inline):
   - `openMedia(url)` → **`openUri(url)`**
   - `release()` → **`dispose()`**
   - `setVolume()` → **`volume = X` (Float, 0..1)**
   - `setMuted()` / `isMuted` → **does not exist; emulate by stashing/restoring volume**
   - State exposed as Compose **`mutableStateOf`** (read with `snapshotFlow {}` from outside composition), **not** `StateFlow` — affects `stateSyncJob` design.
   - `VideoPlayerState` is an interface — instantiate via `rememberVideoPlayerState()` (composable) or `createVideoPlayerState()` (non-composable; manual `dispose()`).
   - JVM surface uses **Compose Canvas** (Skia ImageBitmap), **not** SwingPanel — overlays/z-order/AnimatedVisibility work normally (vs. vlcj/SwingPanel limitations).
   - No `surfaceType` parameter on JVM (Android-only).
   - Error states on JVM are only `SourceError` / `UnknownError` — **no codec-vs-network distinction**. Need a separate GStreamer-on-Linux probe.

2. **macOS app bundle path correction** (Phase 2 was wrong):
   - `appResourcesRootDir` lands at **`Contents/app/resources/`**, NOT `Contents/Resources/`. jpackage owns `Contents/Resources/`. Final ffmpeg path: `Amethyst.app/Contents/app/resources/ffmpeg/ffmpeg`.

3. **Linux GStreamer requirements**:
   - kdroidFilter on Linux requires system **GStreamer 1.16+** with `plugins-base`, `plugins-good`, `plugins-bad`, and `libav` (for actual codec decoding). HLS needs `plugins-bad`. Document as `Recommends:` line in DEB / `recommends:` in metainfo.

4. **JCodec adjustments** (Phase 2):
   - Need `org.jcodec:jcodec:0.2.5` **plus** `org.jcodec:jcodec-javase:0.2.5` (AWTUtil lives in `-javase`).
   - Frame color space is **`YUV420J`** — must convert with `ColorUtil.getTransform(native.color, ColorSpace.RGB)` before `AWTUtil.toBufferedImage` (AWTUtil does not auto-convert).
   - `ColorAlphaType.OPAQUE` (not `PREMUL` as I wrote).
   - Cleanest `BufferedImage` → `ImageBitmap` is **ImageIO → PNG bytes → `Image.makeFromEncoded(bytes).toComposeImageBitmap()`**. No stable direct extension.
   - Exception class: **`org.jcodec.api.UnsupportedFormatException extends JCodecException`** — also catch `IOException` + broad `RuntimeException` (decoder throws `AIOOBE` on malformed SPS/PPS).
   - Use `seekToSecondSloppy(1.0)` for thumbnails (precise decodes up to 500 frames per call).
   - Fast-reject path: `MP4Util.parseMovie()` + check `stsd` FourCC for `avc1` / `avc3` before opening FrameGrab.

5. **Tooling for licenses** (Phase 0):
   - Use **`app.cash.licensee`** Gradle plugin to enforce allow-list + generate JSON report at build time (compliance gate).
   - Use **`com.mikepenz.aboutlibraries`** Gradle plugin to produce the in-app "Open source licenses" screen (CMP-ready, replaces my hand-rolled `OpenSourceLicensesScreen.kt`).
   - Hand-author `NOTICE.md` only for native bundles outside Gradle's graph (FFmpeg, GStreamer runtime).

6. **Flathub manifest precedent** (Phase 4):
   - **Gitnuro** (`com.jetpackduba.Gitnuro`) is the existing Kotlin Compose Desktop precedent — use its structure verbatim where applicable.
   - Manifest sketch in plan replaced by Gitnuro-style pattern in [`_flathub-manifest-recipe.md`](_flathub-manifest-recipe.md).
   - Submission branch is **`new-pr`** (not `master`).
   - Patent-codec extension `org.freedesktop.Platform.ffmpeg-full` is the way to surface HEVC/AV1 on Flatpak.

7. **SPDX expression acceptance**:
   - Fedora `rpmLicenseType` accepts SPDX expressions (mandatory since F41 phase 4) → our compound is valid as-is.
   - Flathub `<project_license>` validates compound via `appstreamcli`.
   - **Debian DEP-5 does NOT support compound expressions** — must group per-file with separate `License:` stanzas.

### Key plan-level changes derived from research

- Drop the `OpenSourceLicensesScreen.kt` hand-rolled file (replaced by AboutLibraries plugin).
- Add `app.cash.licensee` + allow-list in `desktopApp/build.gradle.kts`.
- Add `gradle/spdx-allowlist.txt` (or `desktopApp/licensee.gradle`) configuration.
- macOS entitlements file overrides `entitlementsFile.set(...)` and `runtimeEntitlementsFile.set(...)` with `allow-jit`, `allow-unsigned-executable-memory`, `disable-library-validation`.
- `stateSyncJob` uses `snapshotFlow {}` not `combine(StateFlow, ...)`.
- `VideoPlayerSurface` JVM uses Compose Canvas → fullscreen handoff is **simpler** than expected (risk #7 in original risk table is downgraded from M-M to L-L).
- Linux codec-missing UX: probe GStreamer via `gst-inspect-1.0 playbin` at app launch; if missing, show one-time install nag.
- Phase 4 adds a `add-extensions: org.freedesktop.Platform.ffmpeg-full` block to the Flathub manifest for patent codecs.

## Overview

Drop `uk.co.caprica:vlcj 4.8.3` (GPL-3.0-or-later) from `desktopApp` and ship
an MIT-dominant desktop binary. Replace the three vlcj subsystems with:

| Subsystem | New library | License |
|-----------|-------------|---------|
| Video playback | `io.github.kdroidfilter:composemediaplayer:0.10.1` | MIT |
| Audio playback | same (audio-only mode of `VideoPlayerState`) | MIT |
| Thumbnail extraction | `org.jcodec:jcodec:0.2.5` + Jaffree fallback with LGPL FFmpeg | BSD-2 + Apache-2 (Java); LGPL-2.1 (FFmpeg native) |

Resulting binary SPDX: **`MIT AND LGPL-2.1-or-later AND BSD-2-Clause`** (down
from today's effective `GPL-3.0-or-later AND LGPL-2.1-or-later AND MIT` that
the current `rpmLicenseType = "MIT"` misrepresents).

Single migration PR. Hard cut — no feature flag. Phase 0 (NOTICE / SPDX
honesty patch) bundled in the same PR so the binary is never released
mislabeled.

See brainstorms for *why*:
- [Licensing issues catalog](../brainstorms/2026-06-11-vlcj-licensing-brainstorm.md)
- [Migration brainstorm](../brainstorms/2026-06-11-vlcj-replacement-migration-brainstorm.md)
- [Replacement-candidate research](../brainstorms/2026-06-11-vlcj-replacement-research.md)

## Problem Statement

Today's `desktopApp` integration:

1. Builds Amethyst Desktop's binary by linking GPL-3.0 vlcj into the JVM.
2. Bundles LGPL-2.1 libvlc + a mixed-license VLC 3.0.20 plugin tree (~95 MB on macOS, ~90 MB on Windows, ~70 MB on Linux uncompressed) via the `ir.mahozad.vlc-setup` Gradle plugin.
3. Declares `rpmLicenseType = "MIT"` (`desktopApp/build.gradle.kts:141`) — **wrong** for the produced binary.
4. Ships no NOTICE, no per-component LICENSE files, no written GPL source offer, and no About-screen license listing.

Consequences (full catalog in
[licensing brainstorm](../brainstorms/2026-06-11-vlcj-licensing-brainstorm.md)):

- The MIT badge on the repo + RPM is incorrect for distributed binaries.
- Forks of `desktopApp/` silently inherit GPL.
- Mac/MS App Store distribution is structurally blocked (anti-Tivoization conflict).
- Linux distros (Fedora, Debian) would fail license review with the current metadata.
- The bundled VLC plugin tree contains GPL-only plugins (`libdvdcss`, `x264`-built swscale) that drag GPL regardless of the Java binding.

User-resolved priorities (from brainstorm 2026-06-11):
- Keep **MIT branding on the binary** — non-negotiable.
- Mac/MS App Store: **not** on roadmap; not the migration driver.
- **Hard cut** (no feature flag), single PR, Flathub manifest in scope.
- Android (`amethyst/`) out of scope — already uses `media3-exoplayer`, no vlcj.

## Proposed Solution

### High-level

Swap the playback engine inside the existing `GlobalMediaPlayer` singleton
and the thumbnail engine inside `VideoThumbnailCache`, preserving the
public `StateFlow` surface that the rest of the UI consumes. Delete the
discoverer/pool layer (kdroidFilter handles its own native loading) and the
`ir.mahozad.vlc-setup` Gradle plugin (no more bundled VLC).

The UI composables (`DesktopVideoPlayer`, `AudioPlayer`, `VideoControls`,
`NowPlayingBar`, `GlobalFullscreenOverlay`, `LightboxOverlay`) keep their
shapes; `DesktopVideoPlayer` switches its rendering path from
`Image(bitmap = videoFrame)` to kdroidFilter's `VideoPlayerSurface`
composable when the active video is playing, and stays on a thumbnail
`Image` for inactive instances (the "show poster until I tap play" pattern).

### The cross-stack contract

```
+-----------------------------------------+
|  UI composables (kept)                  |
|  DesktopVideoPlayer | AudioPlayer       |
|  VideoControls | NowPlayingBar |        |
|  GlobalFullscreenOverlay | LightboxOverlay |
+----------------------+------------------+
                       |
                       v  reads StateFlow + invokes verbs
+-----------------------------------------+
|  GlobalMediaPlayer  (kept as singleton) |
|  - exposes StateFlow<MediaPlaybackState>|
|  - exposes activeVideoPlayerState       |
|  - verbs: playVideo/playAudio/pause/... |
+----------------------+------------------+
                       |
                       v  delegates to
+----------------+   +-------------------+   +---------------+
| kdroidFilter   |   | JCodec (primary)  |   | Jaffree (fallback) |
| VideoPlayerState |  | thumbnail H.264   |   | LGPL FFmpeg thumb  |
+----------------+   +-------------------+   +---------------+
```

### Why kdroidFilter

Full rationale in the [research doc](../brainstorms/2026-06-11-vlcj-replacement-research.md).
Headline:

- **MIT.** Binding + Maven coordinates `io.github.kdroidfilter:composemediaplayer:0.10.1`.
- **OS-native backends, no native bundle on Win/mac:** Media Foundation, AVFoundation, GStreamer (Linux system).
- **First-class Compose API:** `VideoPlayerSurface(playerState, contentScale, surfaceType, overlay)`.
- Bundle on macOS drops from ~95 MB → ~60-80 MB (we still bundle LGPL FFmpeg for the rare-codec thumbnail fallback; nothing for video).
- Active in 2026 (v0.10.1 May 2026, single maintainer Elie Gambache).

## Technical Approach

### Architecture

**Module boundary:** Migration stays inside `desktopApp/`. `quartz/`,
`commons/`, and `amethyst/` are untouched. No new shared abstractions in
`commons/` — the player layer is desktop-specific and has no Android
counterpart in scope (`amethyst/` runs `media3-exoplayer`).

**Concurrency:** kdroidFilter manages its own threading. We continue to
adapt its callbacks into the existing `MediaPlaybackState` StateFlow inside
`GlobalMediaPlayer` so UI consumers see the same shape. Thumbnail
extraction stays on `Dispatchers.IO`; we replace vlcj's `CountDownLatch`
gymnastics with a `suspendCancellableCoroutine` around JCodec's synchronous
API and `Jaffree.executeAsync().toCompletableFuture().asDeferred()` for the
fallback.

**Native loading:** kdroidFilter uses JNI (not JNA). On macOS this avoids
the `setenv$3b99ba0d` versioned-symbol class of bug that
`MacOsVlcDiscoverer` worked around in May 2026 (see
`docs/plans/2026-05-18-fix-macos-vlc-bundled-discovery-plan.md`). FFmpeg
binaries for Jaffree are spawned as separate processes — no JNI native lib
to load, so no signing/notarization complications beyond marking the
ffmpeg binary as executable in the jpackage app bundle and ensuring it
ships inside `Contents/MacOS/` so macOS hardened runtime permits it.

### Implementation Phases

#### Phase 0 — License bridge (in-PR, lands first commit) (~1 day)

Land alongside the migration code in the same PR so no released binary is
ever mislabeled.

**File changes:**

1. **`desktopApp/build.gradle.kts:141`**
   - `rpmLicenseType = "MIT"` → `rpmLicenseType = "MIT AND LGPL-2.1-or-later AND BSD-2-Clause"`
   - (jpackage forwards verbatim into the .rpm `License:` field. SPDX
     expression form is accepted by `rpm --query`; Fedora packagers parse it
     as a compound license.)

2. **New: `desktopApp/src/jvmMain/appResources/common/NOTICE.md`**
   - Lists every third-party component shipping in the binary with version + SPDX + upstream URL.
   - Includes FFmpeg LGPL build provenance + binary download URL.
   - References JCodec, kdroidFilter, GStreamer (Linux runtime dep).
   - Contains the GPLv3 source-availability written offer for the *interim* commit (the bridge commit lands while the migration is in flight; the same NOTICE is updated in Phase 3 to drop the vlcj/VLC lines once those are deleted).

3. **New: `desktopApp/src/jvmMain/appResources/common/licenses/`**
   - `LICENSE-MIT.txt` — Amethyst's MIT
   - `LICENSE-MIT-kdroidfilter.txt` — verbatim from upstream
   - `LICENSE-BSD-2-JCodec.txt` — verbatim
   - `LICENSE-Apache-2-Jaffree.txt` — verbatim
   - `LICENSE-LGPL-2.1.txt` — FFmpeg + libvlc (interim)
   - `LICENSE-GPL-3.0.txt` — vlcj (interim, deleted in Phase 3)
   - `WRITTEN-OFFER.txt` — GPL source-offer pointing to https://github.com/vitorpamplona/amethyst (valid for 3 years per GPLv3 §6c). Removed in Phase 3 once vlcj is gone.

4. **New: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/about/OpenSourceLicensesScreen.kt`**
   - Scrollable list of bundled components → name, version, license SPDX, "View license" expander.
   - Reachable from existing settings (look up + wire into nav during implementation; flagged as plan question in brainstorm).
   - Pure data-driven: a `LicenseEntry` data class list defined inline; no resource loader complexity.

5. **`README.md` (root)** — add a single bullet under any "Desktop" section:
   - "The desktop binary currently bundles GPL components (vlcj, parts of VLC plugins) during the in-flight migration to MIT-only dependencies. Source available at this repository."
   - Removed in Phase 3.

**Acceptance for Phase 0:**
- `./gradlew :desktopApp:packageDistributionForCurrentOS` produces a .rpm whose `rpm -qpi` shows the SPDX combined string.
- About-dialog "Open source licenses" screen renders and is reachable from the desktop settings.
- `NOTICE.md`, `licenses/`, and `WRITTEN-OFFER.txt` are visible inside the produced DMG/MSI/DEB/RPM payloads.

#### Phase 1 — kdroidFilter swap-in for video + audio (~1-2 weeks)

##### 1.1 Gradle wiring

**`gradle/libs.versions.toml`** — add:
```toml
[versions]
composemediaplayer = "0.10.1"

[libraries]
composemediaplayer = { group = "io.github.kdroidfilter", name = "composemediaplayer", version.ref = "composemediaplayer" }
```

**`desktopApp/build.gradle.kts`** — add `implementation(libs.composemediaplayer)`. Leave `implementation(libs.vlcj)` in place until Phase 3.

##### 1.2 GlobalMediaPlayer refactor

Replace the engine but **keep the public StateFlow surface** so UI code is
unaffected.

Current public surface (preserve):
```kotlin
val videoFrame: StateFlow<ImageBitmap?>          // delete — DesktopVideoPlayer reads VideoPlayerSurface directly now
val videoState: StateFlow<MediaPlaybackState>   // keep
val audioState: StateFlow<MediaPlaybackState>   // keep
val isFullscreen: StateFlow<Boolean>            // keep

fun playVideo(url: String, seekPosition: Float = 0f)
fun playAudio(url: String)
fun toggleVideoPlayPause()
fun toggleAudioPlayPause()
fun seekVideo(position: Float)
fun seekAudio(position: Float)
fun setVideoVolume(volume: Int)
fun setAudioVolume(volume: Int)
fun toggleVideoMute()
fun toggleAudioMute()
fun stopVideo()
fun stopAudio()
fun toggleFullscreen()
fun exitFullscreen()
fun shutdown()
```

New private fields:
```kotlin
private val videoPlayerState: VideoPlayerState = VideoPlayerState()
private val audioPlayerState: VideoPlayerState = VideoPlayerState().apply { /* audio-only config */ }
private var stateSyncJob: Job? = null
```

New public field — exposed so `DesktopVideoPlayer` can pass it to
`VideoPlayerSurface(...)`:

```kotlin
val activeVideoPlayerState: VideoPlayerState get() = videoPlayerState
val activeAudioPlayerState: VideoPlayerState get() = audioPlayerState
```

**State-sync coroutine.** Replace the vlcj `MediaPlayerEventAdapter` +
500ms polling loop with a single coroutine that mirrors
`videoPlayerState`'s `StateFlow`s into our `_videoState` mutable flow:

```kotlin
private fun startStateSync(state: VideoPlayerState, target: MutableStateFlow<MediaPlaybackState>) {
    stateSyncJob?.cancel()
    stateSyncJob = scope.launch {
        // kdroidFilter exposes: isPlaying, currentTime, duration, isLoading,
        // volume, isMuted, aspectRatio (as StateFlows or @Composable getters)
        combine(
            state.isPlayingFlow,        // verify exact name in v0.10.1 API
            state.currentTimeFlow,
            state.durationFlow,
            state.isLoadingFlow,
            state.aspectRatioFlow,
        ) { /* fold into MediaPlaybackState */ }
            .collect { target.value = it }
    }
}
```

(If kdroidFilter v0.10.1 exposes its state as `@Composable State<T>`
getters rather than `Flow<T>`, fall back to a `snapshotFlow { state.isPlaying }`
inside a `produceState`-equivalent coroutine. Confirm during Phase 1
implementation by reading kdroidFilter source on github.)

##### 1.3 DesktopVideoPlayer rewire

Replace the `Image(bitmap = displayBitmap)` rendering path with conditional
`VideoPlayerSurface` when this composable instance owns the active video URL:

```kotlin
val isActiveVideo = videoState.url == url

Box(...) {
    if (isActiveVideo) {
        VideoPlayerSurface(
            playerState = GlobalMediaPlayer.activeVideoPlayerState,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
        )
    } else {
        thumbnail?.let {
            Image(bitmap = it, contentDescription = "Video thumbnail", ...)
        }
    }

    VideoControls(...)  // unchanged
}
```

Drop the `VlcNotAvailableMessage` composable — kdroidFilter never fails on
"VLC not installed." Replace it with `VlcCodecUnsupportedMessage(url, codec)`
shown when kdroidFilter emits a codec-error event (Windows HEVC/AV1
without the MS Store extension). The message offers "Open in default
player" via `Desktop.getDesktop().browse(URI(url))`.

##### 1.4 AudioPlayer rewire

Drop the vlcj surface plumbing — `AudioPlayer.kt` currently doesn't touch
vlcj directly, it just reads `GlobalMediaPlayer.audioState`. No file
change beyond imports if the StateFlow shape is preserved.

##### 1.5 VideoControls / NowPlayingBar / GlobalFullscreenOverlay / LightboxOverlay

Review for vlcj-specific assumptions. Expected change: none (they consume
StateFlows). Plan question P6 — to be verified during implementation by
grepping for any `EmbeddedMediaPlayer`, `MediaPlayer`, or `videoFrame`
references in these files.

##### 1.6 Phase 1 decision gate

After 1.1–1.5 compile + launch, run the manual test set:

| Test URL | Expected outcome |
|----------|------------------|
| `https://download.samplelib.com/mp4/sample-5s.mp4` (H.264 MP4) | Plays on Win/mac/Linux. |
| `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` (HLS H.264) | Plays on Win/mac/Linux. |
| `https://nostr.build/i/<recent-VP9-webm-url>` (VP9 WebM) | Plays on mac+Linux. Win → codec-missing UX. |
| `https://void.cat/<recent-AV1-mp4>` (AV1 MP4) | Plays on macOS 13+ + Linux. Win → codec-missing UX. |
| `https://zap.stream/<known-live-stream>` (HLS livestream) | Plays on all 3 OSes. **Gate criterion: must not regress vs. vlcj.** |

If the zap.stream live stream fails on any of the 3 OSes for reasons not
explained by the codec gap (i.e. legitimate HLS protocol issues), invoke
the fallback plan — switch primary engine to `gst1-java-core` keeping the
wrapper API intact (research doc §3 Recommended #2). Document the pivot
in `docs/decisions/` and update this plan's "Status" frontmatter.

##### 1.7 Tests

Unit tests in `desktopApp/src/jvmTest/`:
- `GlobalMediaPlayerStateTest.kt` — drives `MediaPlaybackState` derivations from a fake `VideoPlayerState` (build via constructor / setters). Verifies pause/play/seek transitions emit correctly.
- `DesktopVideoPlayerActiveDispatchTest.kt` (compose test) — given two `DesktopVideoPlayer` instances mounted with different URLs, asserts that only the active URL renders `VideoPlayerSurface` (use semantic test tags).
- No instrumented test that actually decodes a network video (too flaky for CI; covered in manual test sheet).

#### Phase 2 — Thumbnail extraction (JCodec → Jaffree cascade) (~3-5 days)

##### 2.1 Gradle wiring

**`gradle/libs.versions.toml`** — add:
```toml
[versions]
jcodec = "0.2.5"
jaffree = "2024.08.29"

[libraries]
jcodec = { group = "org.jcodec", name = "jcodec", version.ref = "jcodec" }
jaffree = { group = "com.github.kokorin.jaffree", name = "jaffree", version.ref = "jaffree" }
```

**`desktopApp/build.gradle.kts`** — add both.

##### 2.2 LGPL FFmpeg per-OS bundling

We need FFmpeg only for the *fallback* thumbnail path. The video player
doesn't shell out to it. Bundle one binary per OS in
`appResources/<os>/ffmpeg/`:

| OS | Source | Path inside DMG/MSI/AppImage |
|----|--------|------------------------------|
| macOS arm64+x86_64 | https://www.osxexperts.net/ — LGPL build, code-signed | `Contents/Resources/ffmpeg/ffmpeg` |
| Windows x64 | https://github.com/Crigges/Prebuilt-LGPL-2.1-FFmpeg-with-OpenH264 | `app/resources/ffmpeg/ffmpeg.exe` |
| Linux x64 | https://johnvansickle.com/ffmpeg/ "release" tarball — LGPL config | `lib/resources/ffmpeg/ffmpeg` (DEB), AppImage equivalent |

(Each binary's per-OS LICENSE.txt also ships in `licenses/` from Phase 0.)

We do not invoke a per-build *download* step (no extra Gradle download
plugin). Binaries are checked into the repo under
`desktopApp/src/jvmMain/appResources/<os>/ffmpeg/` (~10 MB per OS,
acceptable for git LFS or direct check-in). The Phase 3 cleanup removes
the `ir.mahozad.vlc-setup` plugin, which was the big downloader.

**macOS hardened-runtime entitlement:** spawning a child process from a
hardened-runtime app needs the `com.apple.security.cs.allow-jit` or
`com.apple.security.cs.disable-library-validation` entitlement, OR the
ffmpeg binary needs to be co-signed with the app's identity. We extend
the existing `entitlements.plist` to add:

```xml
<key>com.apple.security.cs.allow-unsigned-executable-memory</key>
<false/>
<key>com.apple.security.cs.disable-library-validation</key>
<false/>
<key>com.apple.security.cs.allow-jit</key>
<true/>  <!-- only if needed by ffmpeg; verify on first sign -->
```

The cleanest path is to co-sign ffmpeg with the same identity used for the
.app — verify during implementation that jpackage's signing step covers
nested executables in `Contents/Resources/` (older jpackage did not — may
need a post-pkg `codesign --deep`).

##### 2.3 VideoThumbnailCache rewrite

Replace the vlcj-based `extractFirstFrame` with a cascade:

```kotlin
private suspend fun extractFirstFrame(url: String): ImageBitmap? {
    val downloadedBytes = downloadVideoForThumb(url) ?: return null
    return tryJCodec(downloadedBytes)
        ?: tryJaffree(downloadedBytes)
        ?: tryJaffreeFromUrl(url)  // for HLS where the URL points to a manifest, not bytes
}
```

- **`downloadVideoForThumb(url)`** — uses existing OkHttp instance (which is wired in `desktopApp` already, line 50 in build.gradle.kts). Range-fetch first 4 MB for fast thumbnail. Cache to `~/Library/Caches/com.vitorpamplona.amethyst.desktop/thumbs/<hash>.mp4` (per OS — use `java.util.prefs`-style discovery for cache dir).
- **`tryJCodec(bytes)`** — `FrameGrab.createFrameGrab(NIOUtils.readableChannel(File))` → `seekToSecondPrecise(1.0).getNativeFrame()`. Convert YUV → RGB → Skia `Bitmap` → `ImageBitmap`. Wrap with `runCatching` and discard on any exception (unsupported codec, malformed MP4, HLS manifest in the bytes, etc.).
- **`tryJaffree(bytes)`** — spawn `ffmpeg -ss 1 -i <tempfile> -frames:v 1 -f image2pipe -c:v png pipe:1`, read stdout into ByteArray, decode with `Image.makeFromEncoded(bytes).toComposeImageBitmap()`. 5-second timeout.
- **`tryJaffreeFromUrl(url)`** — same as tryJaffree but with the URL directly as `-i` argument. For HLS or any streamed source.

**Thread safety / dedup:** keep the existing `pending` `ConcurrentHashMap`
guard. Replace `CountDownLatch` with `suspendCancellableCoroutine` over
Jaffree's `executeAsync()` return type for clean cancellation on
recomposition.

##### 2.4 Tests

Unit tests:
- `VideoThumbnailCacheTest.kt` — given a known-good 5-second H.264 MP4 in `src/jvmTest/resources/sample.mp4`, asserts JCodec path produces a non-null `ImageBitmap` with expected dimensions.
- `JaffreeProbeTest.kt` — verifies the bundled ffmpeg binary is found in `appResources/<currentOs>/ffmpeg/` at runtime and prints `ffmpeg -version` successfully. Skip on platforms where we don't ship a binary.
- No HEVC/VP9/AV1 unit test (codec coverage depends on the runtime ffmpeg build; manual test).

#### Phase 3 — vlcj removal + build cleanup (~2-3 days)

##### 3.1 Code deletions

- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/VlcjPlayerPool.kt` — delete
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/BundledVlcDiscoverer.kt` — delete
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/MacOsVlcDiscoverer.kt` — delete
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/VlcResourceResolver.kt` — delete (referenced by both discoverers)
- Any `import uk.co.caprica.vlcj.*` remaining in `GlobalMediaPlayer.kt` or `VideoThumbnailCache.kt` — delete
- Tests that exercise vlcj integration (if any) — delete

##### 3.2 Gradle deletions

`desktopApp/build.gradle.kts`:
- Remove `id("ir.mahozad.vlc-setup") version "0.1.0"` from plugins block
- Remove `implementation(libs.vlcj)`
- Remove the entire `vlcSetup { ... }` block
- Remove `tasks.named("spotlessKotlin") { mustRunAfter("vlcSetup") }`
- Remove `tasks.withType<Download>().configureEach { ... }` (was for `vlcDownload`)
- Remove `jvmArgs += "-Dvlc.plugin.path=\$APPDIR/resources/vlc/plugins"`
- Remove `"jdk.unsupported"` from `modules(...)` if kdroidFilter doesn't need it (verify)
- Update the AppImage build doc comment that mentions VLC

`gradle/libs.versions.toml`:
- Remove `vlcj = "4.8.3"` from `[versions]`
- Remove the `vlcj` entry from `[libraries]`

##### 3.3 Bundled VLC tree deletion

```bash
git rm -r desktopApp/src/jvmMain/appResources/macos/vlc
git rm -r desktopApp/src/jvmMain/appResources/windows/vlc
git rm -r desktopApp/src/jvmMain/appResources/linux/vlc
```

(Equivalent to the `ir.mahozad.vlc-setup` copy targets. If the directories
were generated and not checked in, no `git rm` needed — just confirm the
plugin removal stops emitting them.)

##### 3.4 NOTICE.md / licenses/ trimming

- Drop `LICENSE-GPL-3.0.txt`, `WRITTEN-OFFER.txt`
- Drop the vlcj + libvlc entries from `NOTICE.md`
- Keep entries for: kdroidFilter (MIT), JCodec (BSD-2), Jaffree (Apache-2), FFmpeg (LGPL-2.1), GStreamer (Linux runtime, LGPL-2.1)

##### 3.5 README trim

- Remove the interim "binary bundles GPL" disclaimer.
- Replace with "Built on kdroidFilter ComposeMediaPlayer + native OS media frameworks."

##### 3.6 Acceptance for Phase 3

- `grep -r "vlcj\|caprica\|libvlc" desktopApp/` returns no source code matches (only doc/license mentions).
- `./gradlew :desktopApp:packageDistributionForCurrentOS` produces installers that contain no `vlc/`, `libvlc.dylib`, `libvlccore.dylib`, or `plugins/*.dylib`.
- Built DMG is < 80 MB compressed (down from ~140 MB today; the FFmpeg LGPL binary is ~12-20 MB compressed).
- App launches and plays a sample MP4 with no log line containing "VLC", "vlcj", or "libvlc".

#### Phase 4 — Flathub manifest + final SPDX (~2 days)

##### 4.1 Flathub manifest

New file: `desktopApp/packaging/flatpak/com.vitorpamplona.amethyst.Desktop.yml`

Sketch (planner to refine during implementation):

```yaml
app-id: com.vitorpamplona.amethyst.Desktop
runtime: org.freedesktop.Platform
runtime-version: '24.08'
sdk: org.freedesktop.Sdk
sdk-extensions:
  - org.freedesktop.Sdk.Extension.openjdk21
command: amethyst-desktop
finish-args:
  - --share=network
  - --share=ipc
  - --socket=fallback-x11
  - --socket=wayland
  - --socket=pulseaudio
  - --device=dri
  - --filesystem=xdg-download
  - --talk-name=org.freedesktop.Notifications
modules:
  - name: amethyst-desktop
    buildsystem: simple
    build-commands:
      - install -Dm755 amethyst-desktop /app/bin/amethyst-desktop
      # ... (jpackage Linux output copied in)
    sources:
      - type: file
        path: ../../build/compose/binaries/main-release/app/Amethyst/bin/Amethyst
```

Runtime `org.freedesktop.Platform 24.08` ships GStreamer 1.24 with the
plugins kdroidFilter needs (`gst-plugins-good`, `gst-plugins-bad`,
`gst-libav` with LGPL config). No bundled FFmpeg needed for Flathub since
GStreamer is available — the thumbnail path uses Jaffree → spawns the
system `ffmpeg` if present, else falls back to JCodec-only. Document this
clearly in the Flathub manifest comments.

##### 4.2 Final SPDX

After Phase 3 deletions, set `rpmLicenseType = "MIT AND LGPL-2.1-or-later
AND BSD-2-Clause"`. (LGPL stays because the bundled FFmpeg for thumbnails is LGPL native.)

Update `LICENSES.md` (root) to add a "Distributed binary" section listing
the SPDX expression + table of bundled components.

##### 4.3 CI updates

- Add a job that uploads the Flathub manifest to `flathub/com.vitorpamplona.amethyst.Desktop` repo (manual review by Flathub maintainers — not a one-button operation; document as a deferred follow-up).
- Add a job step that verifies the produced DMG/MSI/DEB contains no `vlc*`, `libvlc*` strings.

### Architecture diagrams

#### Old (today)

```
[User taps play on a feed video]
            │
            ▼
DesktopVideoPlayer composable
            │ GlobalMediaPlayer.playVideo(url)
            ▼
GlobalMediaPlayer (singleton)
            │ VlcjPlayerPool.init() + acquire()
            ▼
EmbeddedMediaPlayer  ◄── BufferFormatCallback / RenderCallback
            │                            │
            │ libvlc events              │ ByteBuffer of pixels
            ▼                            ▼
MediaPlayerEventAdapter       Skia Bitmap.installPixels
            │                            │
            ▼                            ▼
   _videoState (StateFlow)     _videoFrame (StateFlow<ImageBitmap?>)
            │                            │
            ▼                            ▼
   VideoControls reads state    Image(bitmap = videoFrame)
```

#### New

```
[User taps play on a feed video]
            │
            ▼
DesktopVideoPlayer composable
            │ GlobalMediaPlayer.playVideo(url)
            ▼
GlobalMediaPlayer (singleton)
            │ videoPlayerState.openMedia(url) + .play()
            ▼
VideoPlayerState (kdroidFilter)
            │ delegates to OS backend
            ▼
[Media Foundation / AVFoundation / GStreamer]
            │                            │
            │ state flows                │ frames piped to native surface
            ▼                            ▼
   stateSyncJob folds into       VideoPlayerSurface(state) renders directly
   _videoState (MediaPlaybackState)         (no Compose ImageBitmap relay)
            │
            ▼
   VideoControls reads state
```

## Alternative Approaches Considered

(Full evaluation in [research doc §1 and §3](../brainstorms/2026-06-11-vlcj-replacement-research.md))

| Alternative | Why not |
|-------------|---------|
| Accept GPL on binary + only fix the metadata (Approach A in licensing brainstorm) | User priority: keep MIT brand on the binary. |
| Buy Caprica commercial vlcj license (Approach C) | Burns budget every year; doesn't address bundled VLC plugin GPL surface. |
| `gst1-java-core` + bundled GStreamer (Recommended #2 in research) | Binary is LGPL-3.0-dominant, not MIT. Reserved as Phase 1 decision-gate fallback. |
| JavaFX MediaPlayer | No HEVC, VP9, AV1, Opus, FLAC. SwingPanel z-order incompatible with our overlays. |
| Write our own JNA binding to libvlc | 3-6 weeks of native callback / GC-pinning work for **no material license gain** over kdroidFilter. |
| open-ani/mediamp | Desktop backend (`mediamp-vlc`) is GPLv3 — same problem with one more layer. |
| libmpv via Java binding | No maintained JVM binding exists. |
| HumbleVideo | Abandoned 2018, AGPL. |
| External player handoff only | Breaks in-feed playback UX. Kept as codec-missing fallback only. |

## System-Wide Impact

### Interaction Graph

What fires when `playVideo(url)` is called in the new design:

1. `DesktopVideoPlayer` composable's "play" button onClick → `GlobalMediaPlayer.playVideo(url)`.
2. `GlobalMediaPlayer` updates `_videoState` to `(url, isBuffering=true, ...)`.
3. `GlobalMediaPlayer` calls `videoPlayerState.openMedia(url)` then `.play()` on `Dispatchers.IO`.
4. kdroidFilter's backend (Media Foundation / AVFoundation / GStreamer) opens the URL.
5. kdroidFilter emits state changes through its flows; `stateSyncJob` folds them into `_videoState`.
6. Compose recomposes `DesktopVideoPlayer` (active branch) → `VideoPlayerSurface(playerState)` swaps from "loading" to live frames.
7. `NowPlayingBar` (which reads `_videoState`) updates simultaneously.
8. `VideoControls` recomposes (consumes `_videoState`).

No callback marshalling between native and Compose threads on our side —
kdroidFilter handles that. No `Skia.Bitmap.installPixels` per frame in our
code path — `VideoPlayerSurface` writes directly to a native surface.

### Error & Failure Propagation

- **Codec unsupported (e.g. AV1 on stock Win10):** kdroidFilter emits an error state. `stateSyncJob` sets `_videoState.isBuffering = false` + a new `_videoState.errorReason: CodecError? = null` field. `DesktopVideoPlayer` checks this and renders `CodecUnsupportedMessage` instead of `VideoPlayerSurface`.
- **Network failure / 404:** Same path — kdroidFilter error state → `CodecError(reason=NETWORK)`. UI shows generic "Couldn't load" with retry button.
- **Thumbnail failure (JCodec then Jaffree both fail):** `VideoThumbnailCache.getThumbnail(url)` returns `null`. `DesktopVideoPlayer` already handles this (current code shows just the play-button overlay on null thumbnail).
- **kdroidFilter native loading failure on Linux (no system GStreamer):** caught at `GlobalMediaPlayer.init()`-equivalent. Show a one-time toast: "Install GStreamer to play videos: sudo apt install gstreamer1.0-plugins-good gstreamer1.0-libav".

### State Lifecycle Risks

- `videoPlayerState` is a singleton on `GlobalMediaPlayer`. Reusing it across URLs is supported by kdroidFilter (`openMedia(newUrl)` resets). No risk of leaked native players (vlcj's pool was a workaround for vlcj's expensive `EmbeddedMediaPlayer` construction; kdroidFilter doesn't have that cost).
- App shutdown: `GlobalMediaPlayer.shutdown()` calls `videoPlayerState.release()` + `audioPlayerState.release()`. No native handle survives JVM exit.
- Thumbnail cache: same `ConcurrentHashMap` + `pending` deduper. No native handles.
- FFmpeg child processes (Jaffree): always have a 5-second timeout. `try-finally` ensures `Process.destroyForcibly()` on cancellation or exception.

### API Surface Parity

- `GlobalMediaPlayer`'s public API (verbs + StateFlows) is preserved. Callers in `DesktopVideoPlayer`, `AudioPlayer`, `VideoControls`, `NowPlayingBar`, `GlobalFullscreenOverlay`, `LightboxOverlay` need no breaking changes other than removing the `videoFrame` StateFlow (which only `DesktopVideoPlayer` reads).
- Drop: `GlobalMediaPlayer.videoFrame: StateFlow<ImageBitmap?>` (replaced by `VideoPlayerSurface(activeVideoPlayerState)` reading the player state directly in the consumer composable).
- Add: `GlobalMediaPlayer.activeVideoPlayerState: VideoPlayerState` for the surface consumer.
- Add: `MediaPlaybackState.errorReason: CodecError? = null` for codec-unsupported UX.

### Integration Test Scenarios

Manual (also captured in the testing sheet handed to the user post-implementation):

1. **Resume across navigation.** Play a video in the feed, navigate to a different screen, return to the feed → video continues playing, position preserved. (Tests `GlobalMediaPlayer` singleton scope.)
2. **Switch URLs mid-play.** Tap play on video A, then tap play on video B → A stops, B starts from beginning. (Tests `openMedia` reset.)
3. **Now-playing bar sync.** Play audio from a feed item, scroll away → bar appears with playback state in sync. (Tests `_audioState` parity with the underlying engine.)
4. **Fullscreen ↔ feed handoff.** Enter fullscreen during playback, exit → playback continues uninterrupted, no reload. (Tests that `VideoPlayerSurface` instances can be re-attached / not destroyed across composable re-creation. **High-risk**: this may require a `key(...)` boundary to keep the surface stable.)
5. **Codec-missing Windows path.** Open an AV1 MP4 on stock Win10 → `CodecUnsupportedMessage` renders + "Open in default player" works. (Tests error propagation + handoff.)

## Acceptance Criteria

### Functional Requirements

- [ ] Feed videos (H.264 MP4) play on macOS / Windows / Linux desktop builds.
- [ ] Feed audio (MP3 / Opus / AAC) plays on all 3 OSes.
- [ ] HLS livestreams (zap.stream) play on all 3 OSes.
- [ ] VP9 WebM plays on macOS + Linux. Windows shows codec-missing UX with "open externally" handoff.
- [ ] AV1 plays on macOS 13+ + Linux. Windows shows codec-missing UX.
- [ ] HEVC plays on macOS + Linux. Windows shows codec-missing UX.
- [ ] Thumbnails are extracted and shown in the feed for H.264 MP4 (JCodec) and HEVC/VP9/AV1 (Jaffree LGPL FFmpeg) — same coverage as vlcj today.
- [ ] Existing in-feed playback UX (play/pause/seek/volume/mute/fullscreen) is preserved.
- [ ] Now-playing bar continues to work across navigation.
- [ ] Lightbox / fullscreen overlay continues to work.
- [ ] App startup: no "VLC not installed" path. Engine is always available.

### Non-Functional Requirements

- [ ] Binary SPDX in produced packages: `MIT AND LGPL-2.1-or-later AND BSD-2-Clause` (no `GPL-3.0` token).
- [ ] macOS DMG size ≤ today's size − 25 MB (target: ~70-80 MB compressed).
- [ ] No regression in feed-scroll FPS (kdroidFilter writes frames natively; should equal or exceed vlcj's `RenderCallback` path).
- [ ] App startup time ≤ today (vlcj init removed; kdroidFilter doesn't initialize until first playback).

### Quality Gates

- [ ] `./gradlew :desktopApp:compileKotlin` green.
- [ ] `./gradlew :desktopApp:test` green.
- [ ] `./gradlew spotlessApply` clean.
- [ ] `grep -r "vlcj\|caprica\|libvlc" desktopApp/src/` finds zero hits.
- [ ] `./gradlew :desktopApp:packageDistributionForCurrentOS` succeeds on macOS arm64 (local) and produces a DMG; smoke-launches.
- [ ] NOTICE / licenses / About-screen-licenses-listing accurate and accessible from the running app.
- [ ] Manual test sheet completed by user (provided post-implementation).

## Success Metrics

- **Binary SPDX correctness** (boolean): packaged installers carry the SPDX expression listed above. Verified by `rpm -qpi`, DMG metadata, MSI summary.
- **Bundle size delta** (MB): macOS DMG and Linux DEB shrink by ≥25 MB after Phase 3.
- **Manual codec coverage matrix pass rate** (%): aim for ≥95% pass on H.264, AAC, MP3, HLS-H.264; ≥75% on VP9, AV1, HEVC (Windows excepted per documented gap).
- **Crash-free playback sessions in first 30 days post-release** (telemetry, if available): ≥ today's baseline.
- **Issue-tracker mentions of "VLC not installed"** post-release: 0.

## Dependencies & Prerequisites

- Maven Central artifacts: `io.github.kdroidfilter:composemediaplayer:0.10.1`, `org.jcodec:jcodec:0.2.5`, `com.github.kokorin.jaffree:jaffree:2024.08.29`.
- LGPL FFmpeg binaries to commit into `desktopApp/src/jvmMain/appResources/<os>/ffmpeg/` (planner to fetch + verify checksums during Phase 2).
- Linux runtime: GStreamer 1.20+ with `gst-plugins-good` and `gst-libav`. Already installed by default on Fedora 39+, Ubuntu 22.04+, Debian 12+. Documented as a `recommends`/`depends` line in the .deb/.rpm control files.
- Flathub: `org.freedesktop.Platform 24.08` runtime (Phase 4).
- JDK 21 (already in use).
- No new tooling required (jpackage, ProGuard, spotless already configured).

## Risk Analysis & Mitigation

| # | Risk | Probability | Impact | Mitigation |
|---|------|-------------|--------|------------|
| 1 | kdroidFilter HLS livestream failures on AVPlayer (strict mode) | M | H | Phase 1 decision gate against zap.stream + 2-3 alt streams. Pivot to gst1-java-core if it bites. |
| 2 | Windows stock-codec gap (HEVC/VP9/AV1) | H | M | Codec-detection + "open externally" handoff. Document in release notes. |
| 3 | kdroidFilter project goes dormant (single maintainer) | L | M | API surface we depend on is tiny; we own the wrapper. Pivot to gst1-java-core later behind same wrapper API. |
| 4 | JCodec doesn't decode some H.264 high-profile MP4s | M | L | Jaffree fallback handles it. Cost: an extra ffmpeg spawn for those URLs. |
| 5 | macOS hardened-runtime rejects nested ffmpeg binary | L | H | Co-sign nested binaries with the app identity in the existing `codesign` step. If jpackage doesn't, add a post-step `codesign --deep`. |
| 6 | Flathub review delays / rejects manifest | L | L | Manifest authoring is in scope; submitting to Flathub is a separate operation tracked outside this PR. |
| 7 | `Image(bitmap)` → `VideoPlayerSurface` swap reveals new z-order interaction with `GlobalFullscreenOverlay` | M | M | Test scenario #4 in integration tests; fix at implementation time. |
| 8 | `_videoState.errorReason` field break binary-state compat in tests | L | L | All affected tests are co-edited in this PR. |
| 9 | LGPL FFmpeg binaries grow git history | M | L | Use git LFS for the per-OS binaries; or fetch on first build via a one-shot Gradle task with checksum verification. |
| 10 | jpackage doesn't sign nested executables on macOS | M | M | Phase 2 acceptance verifies; fallback is post-step `codesign --deep --force --sign "..." Amethyst.app`. |

## Resource Requirements

- **People:** 1 engineer (you/Claude in `/ce:work` mode).
- **Time:** 2-3 weeks elapsed for code work. Plus user time for manual testing pass.
- **Infra:** Local macOS arm64 build, Windows VM for codec-gap testing (existing CI handles cross-OS DMG/MSI/DEB), Linux VM for Flathub manifest verification.

## Future Considerations

- Once kdroidFilter / `gst1-java-core` matures, consider lifting the player abstraction into `commons/commonMain/` so iOS desktop (if ever) can share. Out of scope for this PR.
- If Compose Multiplatform 1.9+ ships an official `VideoPlayer` composable backed by `androidx.media3` on desktop, evaluate migrating to that — but only if the codec story is at least equivalent. Likely 12-18 months out.
- DASH support — kdroidFilter doesn't list it. If we want it later, GStreamer fallback is the path.

## Documentation Plan

- `NOTICE.md` (new, in app bundle).
- `LICENSES.md` (root) — add "Distributed binary" section.
- `README.md` (root) — interim disclaimer added in Phase 0, removed in Phase 3.
- `desktopApp/packaging/flatpak/README.md` — explains the Flathub manifest, build steps, deps.
- About-dialog "Open source licenses" screen (in-app).
- Update `docs/plans/2026-03-16-feat-desktop-media-full-parity-plan.md` to add "superseded by 2026-06-11 plan" front-matter note (if appropriate — verify during execution).

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-06-11-vlcj-replacement-migration-brainstorm.md](../brainstorms/2026-06-11-vlcj-replacement-migration-brainstorm.md). Decisions carried forward:
  - Stack choice (kdroidFilter primary, gst1-java-core fallback)
  - Single-PR bundling of license bridge + migration
  - Hard cut, no feature flag
  - Flathub manifest in scope
  - Android out of scope
- **Companion research:** [docs/brainstorms/2026-06-11-vlcj-replacement-research.md](../brainstorms/2026-06-11-vlcj-replacement-research.md) — full candidate comparison matrix.
- **Companion licensing analysis:** [docs/brainstorms/2026-06-11-vlcj-licensing-brainstorm.md](../brainstorms/2026-06-11-vlcj-licensing-brainstorm.md) — 10-issue catalog.

### Internal References

- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/VlcjPlayerPool.kt` — pool architecture being deleted
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/GlobalMediaPlayer.kt` — engine swap target
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/VideoThumbnailCache.kt` — engine swap target
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/media/DesktopVideoPlayer.kt:62-172` — UI rewire target
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/media/AudioPlayer.kt` — minor changes
- `desktopApp/build.gradle.kts:9, 60-61, 97-98, 117, 141, 166-176` — build wiring to swap
- `gradle/libs.versions.toml` — `vlcj = "4.8.3"` to remove
- `LICENSE` (root) — Amethyst MIT
- `docs/plans/2026-05-18-fix-macos-vlc-bundled-discovery-plan.md` — recent VLC native-loading work that gets superseded
- `docs/plans/2026-03-16-feat-desktop-media-full-parity-plan.md` — earlier desktop media plan
- `docs/plans/_vlcj-migration-learnings.md` — institutional learnings (compiled by research agent 2026-06-11)

### External References

- [kdroidFilter/ComposeMediaPlayer](https://github.com/kdroidFilter/ComposeMediaPlayer) — primary library
- [kdroidFilter/ComposeMediaPlayer LICENSE](https://github.com/kdroidFilter/ComposeMediaPlayer/blob/master/LICENSE)
- [`io.github.kdroidfilter:composemediaplayer` on Maven Central](https://central.sonatype.com/artifact/io.github.kdroidfilter/composemediaplayer)
- [jcodec/jcodec](https://github.com/jcodec/jcodec)
- [kokorin/Jaffree](https://github.com/kokorin/Jaffree)
- [Crigges/Prebuilt-LGPL-2.1-FFmpeg-with-OpenH264](https://github.com/Crigges/Prebuilt-LGPL-2.1-FFmpeg-with-OpenH264) — Windows LGPL ffmpeg
- [osxexperts.net LGPL FFmpeg](https://www.osxexperts.net/) — macOS LGPL ffmpeg
- [johnvansickle.com FFmpeg static builds](https://johnvansickle.com/ffmpeg/) — Linux LGPL ffmpeg
- [Flathub submission docs](https://docs.flathub.org/docs/for-app-authors/submission)
- [GStreamer licensing FAQ](https://gstreamer.freedesktop.org/documentation/frequently-asked-questions/licensing.html)
- [FFmpeg legal](https://www.ffmpeg.org/legal.html) — `--enable-gpl` semantics
- [GNU GPL FAQ §JavaJVM](https://www.gnu.org/licenses/gpl-faq.html#JavaJVM) — confirms JNI/JNA doesn't escape GPL
- [Apple anti-Tivoization vs GPLv3 (FSF/VLC 2011)](https://www.fsf.org/news/2010-05-app-store-compliance) — App Store incompatibility

### Related Work

- Brainstorms (this PR's ancestry):
  - [Licensing catalog](../brainstorms/2026-06-11-vlcj-licensing-brainstorm.md)
  - [Migration brainstorm](../brainstorms/2026-06-11-vlcj-replacement-migration-brainstorm.md)
  - [Replacement research](../brainstorms/2026-06-11-vlcj-replacement-research.md)
- Prior plans:
  - [Desktop media full parity plan](2026-03-16-feat-desktop-media-full-parity-plan.md)
  - [Desktop media manual testing plan](2026-03-16-desktop-media-manual-testing-plan.md)
  - [macOS VLC bundled discovery fix plan](2026-05-18-fix-macos-vlc-bundled-discovery-plan.md)
