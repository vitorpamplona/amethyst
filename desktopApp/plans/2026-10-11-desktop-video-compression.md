# Desktop video compression — design

**Status:** approved design (revised after review 2026-10-11), awaiting implementation
**Branch:** `feat/desktop-video-compression`
**Scope:** phase 1 of two. Phase 1 makes the composer's video controls real on the JVM desktop when a
system `ffmpeg` is installed. Phase 2 (separate plan) bundles an LGPL FFmpeg per OS so nothing has to
be installed.

## Background — what the reports are about

Users reported "too aggressive" video compression from Amethyst Desktop. The investigation
(2026-10-11) showed the opposite: the JVM desktop has **never compressed video**.

- `DesktopMediaUploader.compressIfNeeded` returns its input unchanged; `useH265` is accepted and
  never read.
- The upload pipeline (`commons/jvmMain/…/upload/UploadOrchestrator.kt`) runs `ImageReencoder`,
  whose sniffer classifies a video as `NotAnImage` → `PassThrough` → original bytes.
- `jcodec` and the FFmpeg subprocess exist only for `VideoThumbnailCache`; no video encoder is
  wired into any upload path, on this branch or on `upstream/main`.
- A 1080p test clip uploaded from desktop came back byte-identical (same sha256, 2 247 200 bytes)
  from blossom.band, blossom.primal.net, nostr.download and cdn.nostrcheck.me.

Yet the shared composer shows a **media-quality slider** and a **"Use H.265/HEVC Codec"** switch for
any video, on every platform, and both are ignored on desktop. People upload a low-bitrate source
(phone export, downloaded clip, Android's own 720p @ 2 Mbps output), judge the result against the
controls they just set, and attribute the source's quality to the client. Two further desktop bugs
fall out of the same gap:

- `ImageVideoDescription` hides the strip-metadata toggle for a video whenever the slider is not
  UNCOMPRESSED, on the promise that compression strips metadata. On desktop nothing strips it
  (the JVM stripper, `MediaCompressor.stripExif`, handles JPEG only).
- The `convertGifToMp4` switch is equally dead on desktop.

Android compresses video with `LightCompressor-enhanced` (MediaCodec) using a tuned table in
`amethyst/…/service/uploads/VideoCompressionHelper.kt`. Those settings are the reference; desktop
copies them.

## Goals

1. On desktop, the slider and the H.265 switch produce output comparable to Android's for the same
   level: a 1080p clip at HIGH → 1080p H.264 @ 4 Mbps; with H.265 → HEVC @ ×0.75.
2. The UI never claims a capability the platform lacks. Without `ffmpeg`, video controls are hidden
   and replaced by a one-line hint; the original uploads as today. When the dialog hides the
   strip-metadata toggle because "compression strips", the bytes that leave the machine have no
   metadata — including when compression falls back to the original.
3. One source of truth for the resolution/bitrate table, shared by Android and desktop, so the
   platforms cannot drift.
4. No new third-party dependency and no change to Amethyst's MIT licensing position.
5. Phase 1 code must work unchanged with a phase-2 bundled LGPL binary that has no `libx264`.

## Non-goals (phase 1)

- Bundling FFmpeg (phase 2).
- HLS publishing on desktop.
- `amy` CLI flags for video quality.
- A VERY_HIGH / VERY_LOW row for video. Those levels exist for images only; for video they map to
  HIGH / LOW (see §1).
- Two separate sliders for images and videos (considered and rejected: a second quality value would
  have to be threaded through every composer ViewModel and `MultiOrchestrator.upload` on Android
  too, to serve the rare mixed photo+video post).
- HDR tone-mapping. Phase 1 detects HDR and warns (§2); converting HLG/PQ to SDR properly, or
  keeping 10-bit HEVC, is a follow-up.
- Reading the real frame rate on Android. Android reads `METADATA_KEY_CAPTURE_FRAMERATE`, which
  ordinary clips do not carry, so it almost always assumes 30 fps and never applies the ×1.3
  multiplier. Desktop reads the real average frame rate (§2), so a 1080p60 clip gets 5.2 Mbps on
  desktop and 4 Mbps on Android. Fixing Android to read the real rate is a follow-up; the table and
  multipliers are still identical.
- The Marmot group-icon and Cordn chat callers of `compressIfNeeded` pass *images* through untouched
  on desktop today (the icon uploads at UNCOMPRESSED). Videos through those callers are covered by
  this design; the image behaviour is pre-existing and out of scope.

## Decisions taken during brainstorming and review

| Question | Decision |
| --- | --- |
| Encoder delivery | Phase 1: system `ffmpeg` found on `$PATH` or in the usual package-manager locations, or already present in `appResources/<os>/ffmpeg/`. Phase 2: bundled LGPL binary. |
| No `ffmpeg` installed | Degrade quietly: hide video controls, show hint, upload original. No dialog, no failure. |
| Slider model | One slider. When the selection contains a video, the displayed stops are the platform list filtered to levels that have a video rule (desktop: LOW/MEDIUM/HIGH/UNCOMPRESSED — the Android layout). |
| Licensing | A user-installed GPL `ffmpeg` is invoked as a separate process over argv and pipes, not linked, and nothing is distributed: no licensing impact. Phase-2 bundled binary must be LGPL (no x264/x265), hence the encoder preference list below, plus the LGPL duties in §6. |
| VAAPI | Not in the preference list. `*_vaapi` needs `-vaapi_device` and a `hwupload` filter; a one-size command line would only add a guaranteed failed attempt. Linux falls through to `nvenc`/`qsv`/`amf` or `libopenh264`. |
| HDR | Detect and warn, encode as designed; tone-mapping deferred. |
| Label | Describes the picked file's real resolution (desktop probes it), not a fixed "1080p" example. |

## Design

### 1. Shared data — the rules table moves to `commons`

`VideoStandard`, `VideoResolution` (+ `getStandard()`), `VideoInfo`, `CompressionRule`
(+ `getBitrateBps()`) and the LOW/MEDIUM/HIGH table move **verbatim** from
`amethyst/…/VideoCompressionHelper.kt` to

```
commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRules.kt
```

Android imports them from there and its copies are deleted. Unchanged semantics:

- the table (short side keyed by source standard, bitrate in Mbps), **including its UNKNOWN rows**
  (LOW 480p/0.75, MEDIUM 720p/2, HIGH 1080p/4), which apply when the probe succeeds but the
  resolution is below 240p;
- ×1.3 at ≥ 59 fps (so broadcast 59.94 fps counts as 60), ×0.75 for H.265;
- the **probe-failure fallback**, which is not a table row: when the video cannot be probed at all,
  2 Mbps and no resize (`VideoCompressionRules.PROBE_FAILED_BITRATE_BPS = 2_000_000`).

Two additions in `CompressorQuality`:

- `hasVideoRule: Boolean` — true for LOW, MEDIUM, HIGH and UNCOMPRESSED.
- `videoLevel: CompressorQuality` — the level the video encoder uses: VERY_HIGH → HIGH,
  VERY_LOW → LOW, everything else itself. Both the compressor and the slider go through it, so a
  position kept across dialogs (`NewMediaModel`, `ChatFileUploadState` keep the slider position
  across `load()`) can never reach the encoder as a level without a rule.

Nothing else in the enum changes: the Int slider position remains an index into the platform list
(`sliderSteps`), and every ViewModel keeps calling `fromSlider(Int)`. The 4-stop layout is a display
concern handled by the slider composable (§4).

### 2. Encoder — `FfmpegVideoCompressor` in `commons/jvmMain`

Lives next to the JVM upload pipeline (`commons/src/jvmMain/…/service/upload/video/`). CLI-safe, no
Compose, so `amy` can use it later. No dependency is added: FFmpeg is an external process.

**`FfmpegLocator`** — extracted from `VideoThumbnailCache`. Resolution order for `ffmpeg` and
`ffprobe` alike: (1) the bare name on `$PATH`, probed with `-version` (2 s cap, killed on overrun);
(2) the package-manager directories a Finder- or Start-menu-launched app does not have on its PATH
(`launchctl getenv PATH` is empty on macOS, so a packaged app sees only `/usr/bin:/bin:/usr/sbin:/sbin`):
`/opt/homebrew/bin`, `/usr/local/bin`, `/opt/local/bin` on macOS/Linux; `%ProgramFiles%\ffmpeg\bin`,
`%LOCALAPPDATA%\Microsoft\WinGet\Links`, `%USERPROFILE%\scoop\shims`, `%ChocolateyInstall%\bin` on
Windows; (3) `${compose.application.resources.dir}/ffmpeg/<name>(.exe)`. Never a
working-directory-relative path. The result is cached for the process lifetime and **warmed on an
IO thread at app start**, so reading availability from composition never blocks on the probe.
The environment variable `AMETHYST_FFMPEG_DISABLED` (any value) makes the locator report nothing,
so the app and the gated tests can be run as a machine without ffmpeg — a bare `PATH` is not
enough, because the locator finds Homebrew by design. `VideoThumbnailCache` switches to it; phase
2 only has to put binaries in the resources folder.

**`FfmpegEncoders`** — runs `ffmpeg -hide_banner -encoders` once and picks by preference:

| Codec | Preference order |
| --- | --- |
| H.264 | `libx264` › `h264_videotoolbox` › `h264_nvenc` › `h264_qsv` › `h264_amf` › `h264_mf` › `libopenh264` |
| H.265 | `libx265` › `hevc_videotoolbox` › `hevc_nvenc` › `hevc_qsv` › `hevc_amf` › `hevc_mf` |

The candidate list for a request is the available HEVC encoders in order (when H.265 was asked for)
**followed by** the available H.264 encoders in order. A listed hardware encoder can still fail at run
time (no GPU, no driver, headless session, NVENC session limit), so on a non-zero exit the next
candidate is tried; the bitrate and the `hvc1` tag are recomputed per candidate because the codec
can change along the way. H.265 requested but the encode ends on an H.264 candidate → a
`UserNotice`. This is what keeps phase-1 code valid for a phase-2 LGPL binary that has no x264.

**Probe** — `ffprobe -v error -select_streams V:0 -show_entries
stream=width,height,avg_frame_rate,color_transfer -show_entries format=duration -of json`.
`V:0` skips attached cover art. `avg_frame_rate` rather than `r_frame_rate`: phone clips are
variable-frame-rate and `r_frame_rate` reports the timebase, not the rate. Width/height are
pre-rotation, but the table keys on the short side, so orientation does not matter. `ffprobe`
missing or failing → the probe-failure fallback of §1. `color_transfer` of `smpte2084` or
`arib-std-b67` marks the clip HDR → a `UserNotice` ("HDR video looks flat after compression; choose
Uncompressed to keep it") and the encode proceeds.

**Command** (one invocation; `in` is the picked file, `out` a temp `.mp4` under `AmethystTempDir`):

```
ffmpeg -hide_banner -loglevel error -nostdin -y
  -i <in>
  -map 0:v:0 -map 0:a:0? -sn -dn -map_chapters -1
  -c:v <encoder> -b:v <bps> -maxrate <bps> -bufsize <2·bps> -pix_fmt yuv420p
  -vf "scale=<expr>"            # short side ≤ rule.shortSide, never upscale, even dimensions
  -c:a aac -b:a 128k
  -map_metadata -1 -map_metadata:s -1
  -movflags +faststart
  [-tag:v hvc1]                 # HEVC only, so Apple players accept it
  -progress pipe:1
  <out>
```

Output is always `video/mp4`. `+faststart` mirrors Android's `isStreamable = true`. Metadata,
chapters, subtitle and data streams are dropped on purpose: the composer hides the strip-metadata
toggle on the promise that compression strips, and this makes the promise true on desktop.
(Verified on ffmpeg 9.0.2: both `-map_metadata` forms are needed; the scale expression turns a
rotated 1920×1080 clip into 720×1280 with no rotation matrix left; `-c:a aac` is fine with no audio,
Opus or PCM sources; `-progress pipe:1` works with `-loglevel error`.)

**The original must be stripped too.** Whenever ffmpeg is present and the pipeline ends up
uploading the original video — output ≥ original, every encoder failed, or the dialog's own
`stripMetadata` toggle for a video at UNCOMPRESSED — the bytes are first remuxed with

```
ffmpeg -hide_banner -loglevel error -nostdin -y -i <in>
  -map 0:v -map 0:a? -dn -sn -c copy
  -map_metadata -1 -map_metadata:s -1 -map_chapters -1 -movflags +faststart
  -progress pipe:1 <out>
```

(seconds, no re-encode). The stream maps are deliberate: `-map 0 -c copy` fails (exit 234,
"Could not find tag for codec none") on a MOV that carries a timecode or other data track —
which is what iPhones and cameras write — and that failure would upload the original with its
`location` tag while the toggle is hidden. Video and audio only, data and subtitle tracks dropped.
`-progress pipe:1` is on the remux too, so the stall watchdog below sees output and does not kill
a legitimate long remux. The same remux backs `MediaUploader.stripMetadata()` for `video/*` on
desktop, so the chat dialogs that keep the toggle visible strip too. Without ffmpeg nothing can
strip a video; the toggle then stays visible and its "could not strip" path applies, as today.
(Verified on ffmpeg 9.0.2 with a `testsrc` MOV built with `-timecode 00:00:00:00 -metadata
location=… -metadata title=secret`: the old form exits 234, the new one strips both tags.)

**Progress and cancellation** — the `out_time_us=` lines from `-progress pipe:1` (`N/A` tolerated)
divided by the probed duration drive `UploadingState.Compressing` from 0.02 up to the level where
the upload stage starts (0.20 for Blossom/NIP-96, 0.40 for NIP-95, so progress never jumps
backwards). Coroutine cancellation → `process.destroyForcibly()`, temp deleted under
`NonCancellable`. No fixed overall timeout (Android's 60 s is a known weakness for long clips);
instead a **stall watchdog**: no progress line for 60 s → kill and treat as failure. Uploads run in
`viewModelScope` and the JVM may exit mid-encode, so every live process is held in a registry that
a **JVM shutdown hook** kills — no orphaned `ffmpeg` after the window closes. Only the two pieces
that block on a child process (the process runner and the probe) switch to `Dispatchers.IO`; the
compressor itself stays on the caller's dispatcher, so its cancellation and temp-file behaviour is
testable under `runTest` virtual time. Temp files come from an injectable factory (default
`AmethystTempDir.createTempFile("amethyst_video_", ".mp4")`) so tests never touch the real
`~/.amethyst/tmp` of a running app.

**Concurrency** — `MultiOrchestrator` uploads every item in parallel; N videos would start N
`ffmpeg` processes each using every core, and hardware encoders have session limits. A
process-wide `Semaphore(1)` serialises encodes (the pattern `ImageReencoder` already uses).

**NIP-95** — the relay-event limit is 80 000 bytes. Before encoding, `(bitrate + 128 kbps) × duration`
is estimated from the probe; over the limit → `MEDIA_TOO_BIG_FOR_NIP95` at once, without a
multi-minute encode that would be refused afterwards.

**Result rules**

- Output size ≥ original → discard output, use the (remuxed, stripped) original.
- `ffmpeg` found but every candidate fails → upload the (remuxed, stripped) original and report a
  `UserNotice` ("Video compression failed; uploading the original").
- UNCOMPRESSED, or `ffmpeg` not available → the encoder is not called.
- The caller owns the temp file and deletes it when the upload finishes or fails.

**GIF → MP4** — same runner; the input is the GIF, no bitrate table: the source size is kept
(rounded down to even), exactly as Android's `GifToMp4Converter`, encoded with the H.264 candidate
list at the MEDIUM row's bitrate for the GIF's standard. Android converts even at UNCOMPRESSED when
the switch is on, so desktop does too. Included so no second dead switch survives on desktop. A
GIF conversion that fails uploads the GIF untouched (never remuxed — it is not a video) with the
"compression failed" notice.

### 3. Wiring — `DesktopMediaUploader`

- `MediaUploader` gains `val videoCompression: VideoCompressionSupport` with three values:
  `Supported` (Android; desktop with ffmpeg), `NeedsFfmpeg` (desktop without), `Unsupported`
  (the commonMain `UnavailableMediaUploader`, iOS). The dialog shows the install hint only for
  `NeedsFfmpeg`.
- `MediaUploader` gains `suspend fun probeVideo(uri: MediaUri): VideoInfo?` (default null;
  desktop delegates to the compressor's own `probe`, so the uploader has one ffmpeg dependency and
  nothing resolves `ffprobe` at construction time) so the slider label can describe the picked
  file (§4).
- A private `withVideoPrepared(progress, uri, mimeType, quality, useH265, stripMetadata,
  convertGifToMp4, progressCeiling, maxBytes) { file, prepared -> … }` in `DesktopMediaUploader`
  hands its block either the original file, a remuxed copy (when stripping is owed), or a
  compressed temp, and deletes what it created in `finally`. The same decision table backs
  `compressIfNeeded` (used by the Marmot group-icon and Cordn chat callers) and the four upload
  entry points (`upload`, `uploadEncrypted`, `nip95`, `nip96`). For a prepared file the pipeline is
  called with `quality = null` and `stripExif = false` (already stripped). Otherwise the call is
  exactly what it is today.
- Encrypted (DM) uploads compress the plaintext first, then encrypt, through the same wrapper.
- **Notices.** The uploader is one per process (`DesktopAccountViewModelHost`), while
  `UserNoticeSink` lives on each `AccountViewModel` (`BaseAccountViewModel.notices`). The host owns a
  `ForwardingNoticeSink` (a `UserNoticeSink` with a settable target); `DesktopAppRoot.createAccountViewModel`
  points it at the new view model's `ToastManager`. The uploader is built with that sink. New
  notice types are a `sealed interface UploadNotice : UserNotice` in `commons/notices`
  (`VideoCompressionFailed(reason)`, `HevcUnavailable`, `HdrSource`), worded in
  `commonsUI/notices/ui/UserNoticeText.kt`.
- `VideoThumbnailCache` switches to `FfmpegLocator`. No behaviour change.

### 4. Dialog — `commonsUI` (shared, so Android benefits)

`ImageVideoDescription.kt`, `NewMediaView.kt` and `ChatFileUploadDialog.kt`:

- **Slider** (`MediaQualitySlider`): the slider is in *video mode* when the selection has a video
  **and** `videoCompression == Supported` — on a platform that will not compress the video, the
  slider is only about the images, so it keeps the image stops and says nothing about video. In
  video mode the displayed stops are `sliderSteps.filter { it.hasVideoRule }`. Desktop shows
  LOW/MEDIUM/HIGH/UNCOMPRESSED; Android and iOS are unchanged (their lists already have only
  those). The composable maps displayed index ↔ platform position internally; the state stays a
  platform position. Entering video mode while the position is on a level without a video rule
  snaps to `videoLevel` (VERY_HIGH → HIGH). Known and accepted: in a mixed photo+video post this
  also lowers the photos from 2560 px to 1920 px, and removing the video again does not restore
  VERY_HIGH.
- **Label** is content-aware: `High · images up to 1920 px`, `High · 4K video → 2160p, 8 Mbps`, or
  both for a mixed post. Every word including the `p` and `Mbps` units comes from string resources
  with positional arguments (`%1$s video → %2$dp, %3$s Mbps`); the code supplies the source
  standard's label, the rule's short side and the bitrate (`2`, `0.75`, no trailing `.0`). The
  video phrase uses `probeVideo()` on the first video (desktop) and the rule for *its* standard;
  where the probe returns null (Android, iOS, a corrupt file) the phrase describes the 1080p row
  with a "1080p video →" prefix so it is never presented as the file's own.
- **Desktop without `ffmpeg`** (`videoCompression == NeedsFfmpeg`): the H.265 and GIF→MP4
  switches are hidden; the slider is shown only when the selection has a compressible *image*
  (`MultiOrchestrator.hasCompressible()` counts videos, so a new `hasCompressibleImage()` is used);
  one hint line "Install ffmpeg to compress videos" (new string `video_compression_needs_ffmpeg`).
  For `Unsupported` the same controls are hidden and no hint is shown.
- **Bug fix:** `isVideoWithCompression = hasVideo && videoCompression == Supported &&
  slider != UNCOMPRESSED`. Today the strip-metadata toggle disappears on desktop for a video that
  is then uploaded with its metadata intact.
- **The three dialogs differ** and keep their differences; only the conditions change:

  | Dialog | Slider | H.265 switch | GIF→MP4 switch | Strip-metadata toggle |
  | --- | --- | --- | --- | --- |
  | `ImageVideoDescription` (post composer) | compressible image, or video when `Supported`; hidden while GIF→MP4 is on | `isVideoWithCompression` | `hasGif() && Supported` | hidden when `isVideoWithCompression` |
  | `NewMediaView` (media post) | always shown today; stays | `isVideoWithCompression` | none (no state for it) | always shown |
  | `ChatFileUploadDialog` | compressible image, or video when `Supported` | none (`ChatFileUploadState` has no `useH265`) | none | always shown |

  The hint line appears in all three for `NeedsFfmpeg` when the selection has a video.
- **"Upload Image" → "Upload Media"**: new string `upload_media` for every attach button whose
  picker accepts photos and videos — `SelectFromGallery`, `NewGroupDMScreen.kt`,
  `NewVideoFeedButton.kt`, `NewShortVideoButton.kt`, `NewLongVideoButton.kt`. `upload_image` stays
  where the picker really is images only: `SelectSingleFromGallery` (profile picture, banner, group
  and channel pictures) shares the private `GallerySelectButton` with `SelectFromGallery`, so the
  button takes its label as a parameter instead of hard-coding one; `NewPictureButton` is unchanged.

All new strings go in `commonsUI/src/commonMain/composeResources/values/strings.xml`.

### 5. Tests

`commons/jvmTest`

- Rules table: every (level, standard) row including UNKNOWN; `getStandard()` boundaries; fps and
  H.265 multipliers (59.94 fps gets the ×1.3, 50 fps does not); `videoLevel` / `hasVideoRule`; the probe-failure
  constant. (No such tests exist on Android today.)
- Slider mapping: displayed index ↔ platform position, snap on adding a video, UNCOMPRESSED
  position stable — written against an explicit step list passed in as a parameter, because
  `platformSliderSteps` is an internal `expect` and the test must cover both the 5-stop and the
  4-stop lists.
- `FfmpegCommandBuilder`: pure arg-list builder — scale expression (no upscale, even dims, portrait),
  stream maps, bitrate flags, HEVC tag, metadata flags, the remux command, the GIF branch. No ffmpeg
  needed.
- `-progress` parser: `out_time_us` → fraction; `N/A`; unrelated keys; `progress=end`.
- Encoder selection from canned `-encoders` output: full GPL build, LGPL-no-x264 build, HEVC
  absent, HEVC listed but every HEVC candidate failing at run time → the H.264 tail is reached.
- Locator: PATH, package-manager directories, bundled, none, disabled by the environment variable.
- Runner with a fake process: candidate retry deletes the failed temp, all-fail notice, larger
  output discarded (and the original remuxed), remux failure → unstripped original, GIF failure →
  untouched GIF, HEVC-unavailable notice, HDR notice, probe-failure fallback, progress forwarding,
  NIP-95 early refusal, the semaphore (two concurrent calls run one process at a time),
  cancellation kills and deletes. Temp files are counted in a per-test directory handed to the
  compressor, never in the real `~/.amethyst/tmp`.
- The real process runner, with a real child process (`sleep`/`timeout.exe` — no ffmpeg needed):
  a child that prints nothing for longer than the stall timeout is killed, reports exit −1 and
  leaves the registry empty; a normal exit is forwarded with its stderr tail.
- Gated on `ffmpeg` **and `ffprobe` and `libx264`** present (a build without x264 encodes
  differently and the size assertions would not hold); the gate is `FfmpegLocator.default`, which
  reports nothing available when the environment variable `AMETHYST_FFMPEG_DISABLED` is set — the
  way to run the suite as a machine without ffmpeg, since the locator finds Homebrew even with a
  bare `PATH`. Fixtures, built with the real binary into a temp dir:
  - `noisy.mp4`: a high-entropy synthetic source (`-f lavfi -i
    "color=c=gray:s=1920x1080:r=30,noise=alls=100:allf=t+u"`, 2 s, `-crf 18 -preset ultrafast`,
    `-metadata title=secret` — `noise` is a filter, not a source, so it needs a `color` source in
    front) whose encodes are *smaller* than the source, so the size rule keeps them: HIGH →
    1920×1080, LOW → short side 480 (measured 854×480), no `title` tag (the container's
    brand/encoder tags remain; they carry no user data); bitrate asserted only as the **VBV upper
    bound** `≤ b × (1 + 2/duration)` — with `bufsize = 2b` a short clip legitimately bursts above
    `b` (measured 6.07 Mbps on 1 s at a 4 Mbps target), and x264 undershoots on easy content —
    plus LOW's bitrate < HIGH's; HEVC → codec `hevc` when no `HevcUnavailable` notice.
  - `easy.mp4`: a low-entropy source (`testsrc`, 55 KB, also with `-metadata title=secret`)
    proves the "larger than original → original, remuxed" rule (its HIGH encode measured 181 KB)
    and that the remuxed original has no `title`.
  - `rotated.mp4`: `-display_rotation 90 -i noisy.mp4 -c copy` (the old `-metadata:s:v rotate=90`
    writes nothing on ffmpeg 9). It must derive from `noisy`, not `easy`: `easy`'s encode is
    larger than the source, so the size rule would return the remux — which still carries the
    rotation side data — and the test would not exercise the encoder. Comes out 1080×1920 with no
    rotation side data (measured 215 KB from the 90 MB source).
  - `tc.mov`: `testsrc` written as MOV with `-timecode 00:00:00:00 -metadata location=… -metadata
    title=secret`, which adds a data track; `strip()` must succeed and the result has one video
    stream and neither tag. (This is the fixture that fails with `-map 0`.)

`desktopApp/jvmTest`

- Uploader video path with a fake compressor: temp cleanup on success, failure and cancellation;
  pass-through when not `Supported`; UNCOMPRESSED; GIF only when asked; notices forwarded;
  `Compressing` progress reaches the orchestrator up to the per-path ceiling. `ForwardingNoticeSink`.

Manual (see the sheet written at the end of implementation)

- With `ffmpeg` and with `AMETHYST_FFMPEG_DISABLED=1` in the environment; **the packaged app
  launched from Finder / the Start menu** (not `:desktopApp:run`, which inherits the shell PATH);
  each slider stop; H.265 on a machine with
  and without an HEVC encoder; GIF→MP4; a portrait phone video; an HDR iPhone clip (notice shown);
  three videos in one post (one ffmpeg at a time); quit mid-encode (no `ffmpeg` left running, temp
  dir clean); a corrupt video (uploads original + toast); the blossom hash check from the
  investigation (the uploaded blob's sha256 must now differ from the source and `ffprobe` must show
  the rule's dimensions and a bitrate at or under the target).

### 6. Phase 2 (separate plan)

Gradle task fetching a verified LGPL FFmpeg build per OS — **both `ffmpeg` and `ffprobe`**, or
every level lands on the probe-failure fallback — into `appResources/<os>/ffmpeg/` at packaging
time, plus macOS codesigning (the notes in `appResources/macos/ffmpeg/README.md`). Phase 1 needs no
change for it: the locator already looks there, and the encoder preference list already copes with
a build that has no `libx264`/`libx265`.

Licensing duties that come with bundling, to be called out in that PR per the repo's WARN rule:
LGPL requires the FFmpeg source (or a written offer) and its notices in `appResources/common/NOTICE.md`;
`libopenh264` is BSD but Cisco's H.264 patent grant covers only Cisco's own binary, not a build we
compile ourselves — prefer Cisco's binary or document the exposure.
