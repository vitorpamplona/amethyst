# Desktop video compression — design

**Status:** approved design, awaiting implementation plan
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
  UNCOMPRESSED, on the promise that compression strips metadata. On desktop nothing strips it.
- The `convertGifToMp4` switch is equally dead on desktop.

Android compresses video with `LightCompressor-enhanced` (MediaCodec) using a tuned table in
`amethyst/…/service/uploads/VideoCompressionHelper.kt`. Those settings are the reference; desktop
copies them.

## Goals

1. On desktop, the slider and the H.265 switch produce output comparable to Android's for the same
   level: a 1080p clip at HIGH → 1080p H.264 @ 4 Mbps; with H.265 → HEVC @ ×0.75.
2. The UI never claims a capability the platform lacks. Without `ffmpeg`, video controls are hidden
   and replaced by a one-line hint; the original uploads as today.
3. One source of truth for the resolution/bitrate table, shared by Android and desktop, so the
   platforms cannot drift.
4. No new third-party dependency and no change to Amethyst's MIT licensing position.
5. Phase 1 code must work unchanged with a phase-2 bundled LGPL binary that has no `libx264`.

## Non-goals (phase 1)

- Bundling FFmpeg (phase 2).
- HLS publishing on desktop.
- `amy` CLI flags for video quality.
- A VERY_HIGH / VERY_LOW row for video. Those levels exist for images only.
- Two separate sliders for images and videos (considered and rejected: a second quality value would
  have to be threaded through every composer ViewModel and `MultiOrchestrator.upload` on Android
  too, to serve the rare mixed photo+video post).

## Decisions taken during brainstorming

| Question | Decision |
| --- | --- |
| Encoder delivery | Phase 1: system `ffmpeg` on `$PATH` (or already-present `appResources/<os>/ffmpeg/`). Phase 2: bundled LGPL binary. |
| No `ffmpeg` installed | Degrade quietly: hide video controls, show hint, upload original. No dialog, no failure. |
| Slider model | One slider. When the selection contains a video, the displayed stops are the platform list filtered to levels that have a video rule (desktop: LOW/MEDIUM/HIGH/UNCOMPRESSED — the Android layout). |
| Licensing | A user-installed GPL `ffmpeg` is invoked as a separate process, not linked; no licensing impact. Phase-2 bundled binary must be LGPL (no x264/x265), hence the encoder preference list below. |

## Design

### 1. Shared data — the rules table moves to `commons`

`VideoStandard`, `VideoResolution` (+ `getStandard()`), `CompressionRule` (+ `getBitrateBps()`) and
the LOW/MEDIUM/HIGH table move **verbatim** from `amethyst/…/VideoCompressionHelper.kt` to

```
commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/video/VideoCompressionRules.kt
```

Android imports them from there and its copies are deleted. Unchanged semantics:

- the table (short side keyed by source standard, bitrate in Mbps);
- ×1.3 at ≥ 60 fps, ×0.75 for H.265;
- UNKNOWN standard → 2 Mbps, no resize (Android's fallback when probing fails).

`CompressorQuality` gains `val hasVideoRule: Boolean` — true for LOW, MEDIUM, HIGH and UNCOMPRESSED.
Nothing else in the enum changes: the Int slider position remains an index into the platform list
(`sliderSteps`), and every ViewModel keeps calling `fromSlider(Int)`. The 4-stop layout is a display
concern handled by the slider composable (§4).

### 2. Encoder — `FfmpegVideoCompressor` in `commons/jvmMain`

Lives next to the JVM upload pipeline (`commons/src/jvmMain/…/service/upload/video/`). CLI-safe, no
Compose, so `amy` can use it later. No dependency is added: FFmpeg is an external process.

**`FfmpegLocator`** — extracted from `VideoThumbnailCache`, same rules: probe `ffmpeg -version` on
`$PATH` (2 s cap, killed if it overruns); else `${compose.application.resources.dir}/ffmpeg/ffmpeg`
(`.exe` on Windows); never a working-directory-relative path. Resolves `ffprobe` by the same rules.
Exposes `available: Boolean`. Result cached for the process lifetime. Phase 2 only has to put
binaries in that folder.

**`FfmpegEncoders`** — runs `ffmpeg -hide_banner -encoders` once and picks by preference:

| Codec | Preference order |
| --- | --- |
| H.264 | `libx264` › `h264_videotoolbox` › `h264_nvenc` › `h264_qsv` › `h264_amf` › `h264_vaapi` › `h264_mf` › `libopenh264` |
| H.265 | `libx265` › `hevc_videotoolbox` › `hevc_nvenc` › `hevc_qsv` › `hevc_amf` › `hevc_vaapi` › `hevc_mf` |

A listed hardware encoder can still fail at run time (no GPU, no driver, headless session), so on a
non-zero exit the next candidate is tried before giving up. H.265 requested with no HEVC encoder
available → H.264 and a `UserNotice`.

**Probe** — `ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate
-show_entries format=duration -of json`. Width/height are pre-rotation, but the table keys on the
short side, so orientation does not matter. `ffprobe` missing or failing → UNKNOWN rule.

**Command** (one invocation; `in` is the picked file, `out` a temp `.mp4` under `AmethystTempDir`):

```
ffmpeg -hide_banner -loglevel error -nostdin -y
  -i <in>
  -c:v <encoder> -b:v <bps> -maxrate <bps> -bufsize <2·bps> -pix_fmt yuv420p
  -vf "scale=<expr>"            # short side ≤ rule.shortSide, never upscale, even dimensions
  -c:a aac -b:a 128k
  -map_metadata -1 -map_metadata:s -1
  -movflags +faststart
  [-tag:v hvc1]                 # HEVC only, so Apple players accept it
  -progress pipe:1
  <out>
```

Output is always `video/mp4`. `+faststart` mirrors Android's `isStreamable = true`. Metadata is
stripped on purpose: the composer hides the strip-metadata toggle on the promise that compression
strips, and this makes the promise true on desktop.

**Progress and cancellation** — the `out_time_us=` lines from `-progress pipe:1` divided by the
probed duration drive `UploadingState.Compressing` from 0.02 to 0.20 (the desktop upload already
starts at 0.20). Coroutine cancellation → `process.destroyForcibly()`, temp deleted under
`NonCancellable`. No fixed overall timeout (Android's 60 s is a known weakness for long clips);
instead a **stall watchdog**: no progress line for 60 s → kill and treat as failure.

**Result rules**

- Output size ≥ original → discard output, use original (Android's sanity check).
- `ffmpeg` found but compression fails after all candidates → upload the original and report a
  `UserNotice` ("Video compression failed; uploading the original").
- UNCOMPRESSED, or `ffmpeg` not available → not called at all.
- The caller owns the temp file and deletes it when the upload finishes or fails.

**GIF → MP4** — same runner, one extra branch: input is the GIF, no bitrate table (use the MEDIUM
rule for the GIF's dimensions), `-pix_fmt yuv420p` and an even-dimension scale. Included so no
second dead switch survives on desktop.

### 3. Wiring — `DesktopMediaUploader`

- `MediaUploader` gains `val supportsVideoCompression: Boolean`. Android: `true`. The commonMain
  default (`MediaUploader.kt`'s no-op implementation): `false`. Desktop: `FfmpegLocator.available`.
- A private `compressVideoIfNeeded(uri, mimeType, quality, useH265, convertGifToMp4, progress)` in
  `DesktopMediaUploader` returns either the original file or a compressed temp. It backs **both**
  `compressIfNeeded` (used by the Marmot group-icon and Cordn chat callers) and the four upload
  entry points (`upload`, `uploadEncrypted`, `nip95`, `nip96`). For a compressed temp the pipeline is
  called with `quality = null` and `stripExif = false` (ffmpeg already stripped); the temp is deleted
  in `finally`. Otherwise the call is exactly what it is today.
- `VideoThumbnailCache` switches to `FfmpegLocator`. No behaviour change.

### 4. Dialog — `commonsUI` (shared, so Android benefits)

`ImageVideoDescription.kt` and `NewMediaView.kt`:

- **Slider** (`MediaQualitySlider`): if the selection has a video, the displayed stops are
  `sliderSteps.filter { it.hasVideoRule }`. Desktop shows LOW/MEDIUM/HIGH/UNCOMPRESSED; Android is
  unchanged (its list already has only those). The composable maps displayed index ↔ platform
  position internally; the state stays a platform position. Adding a video while the position is on
  a level without a video rule snaps to the nearest lower level with one (VERY_HIGH → HIGH).
- **Label** is content-aware: `High · images up to 1920 px`, `High · 1080p video → 1080p, 4 Mbps`,
  or both for a mixed post. The video phrase describes the 1080p row of the table, the common case,
  because `SelectedMedia` carries no dimensions.
- **Desktop without `ffmpeg`** (`!uploader.supportsVideoCompression`): the H.265 and GIF→MP4
  switches are hidden; the slider is shown only when the selection has a compressible image; one hint
  line "Install ffmpeg to compress videos" (new string `video_compression_needs_ffmpeg`).
- **Bug fix:** `isVideoWithCompression = hasVideo && slider != UNCOMPRESSED &&
  supportsVideoCompression`. Today the strip-metadata toggle disappears on desktop for a video that
  is then uploaded with its metadata intact.
- **"Upload Image" → "Upload Media"**: new string `upload_media` for the composer and DM attach
  buttons (`SelectFromGallery.kt`, `NewGroupDMScreen.kt`), whose picker accepts photos and videos.
  `upload_image` stays where the picker really is images only (profile picture/banner,
  picture-only screens).

All new strings go in `commonsUI/src/commonMain/composeResources/values/strings.xml`.

### 5. Tests

`commons/jvmTest`

- Rules table: every (level, standard) row; `getStandard()` boundaries; fps and H.265 multipliers;
  UNKNOWN fallback. (No such tests exist on Android today.)
- Slider mapping: displayed index ↔ platform position for both platform lists; snap on adding a
  video; UNCOMPRESSED position stable.
- `FfmpegCommandBuilder`: pure arg-list builder — scale expression (no upscale, even dims), bitrate
  flags, HEVC tag, metadata flags, GIF branch. No ffmpeg needed.
- `-progress` parser: `out_time_us` → fraction; ignores unrelated keys; handles `progress=end`.
- Encoder selection from canned `-encoders` output: full GPL build, LGPL-no-x264 build, HEVC absent.
- Gated on `ffmpeg` present (`Assume`): synthesise a 2 s `testsrc` clip, compress at each level,
  verify output dims and bitrate via ffprobe, verify metadata is gone, verify larger-than-original
  returns the original.

`desktopApp/jvmTest`

- Uploader video path with a fake compressor: temp cleanup on success, failure and cancellation;
  passthrough when `supportsVideoCompression` is false; passthrough at UNCOMPRESSED.

Manual

- With and without `ffmpeg` on `$PATH`; each slider stop; H.265 on a machine with and without an
  HEVC encoder; GIF→MP4; the blossom hash check from the investigation (the uploaded blob's sha256
  must now differ from the source and `ffprobe` must show the rule's dimensions/bitrate).

### 6. Phase 2 (separate plan)

Gradle task fetching a verified LGPL FFmpeg build per OS into `appResources/<os>/ffmpeg/` at
packaging time, plus macOS codesigning (`docs/plans/_macos-ffmpeg-signing-recipe.md`). Phase 1
needs no change for it: the locator already looks there, and the encoder preference list already
copes with a build that has no `libx264`/`libx265`.
