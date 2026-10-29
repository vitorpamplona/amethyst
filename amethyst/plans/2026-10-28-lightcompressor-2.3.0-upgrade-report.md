# Report: LightCompressor-enhanced 2.3.0 upgrade and HDR hint

Implements [2026-10-28-lightcompressor-2.3.0-upgrade.md](2026-10-28-lightcompressor-2.3.0-upgrade.md)
on branch `feat/lightcompressor-2.3.0-hdr`, created from `main` at `72fbcdb6ad`. Written 2026-10-29.

## Outcome

- Amethyst builds against LightCompressor-enhanced `2.3.0-SNAPSHOT` from the local Maven repo.
  Compressing an HLG or PQ video with H.265 on a device whose HEVC encoder can keep HDR now
  produces 10-bit HDR; every other case tone maps to BT.709 SDR (library default `hdrMode`).
- The post composer and the media post show a line under the H.265 switch when at least one
  picked video is HDR, saying whether it stays HDR or is converted and why. The chat file dialog,
  which always compresses as H.264, shows a codec-less "converted" line.
- On Android the quality slider labels the stop with the picked video's own standard ("4K video →
  2160p, 8 Mbps") instead of the 1080p example.
- A compression that finishes after the 60 s timeout, or after the caller was cancelled, now
  deletes its output instead of leaving it in `filesDir`.
- `builddef.lst` is gone from the APK packaging block: no mp4parser or isoparser in the tree.

Commits (oldest first):

| Hash | Message |
|---|---|
| `f4ce410df9` | feat(upload): LightCompressor-enhanced 2.3.0, HDR hint and real slider labels on Android |
| `23797a2a7f` | refactor(upload): share the orchestrator's video list and the dialogs' hint line |
| `bc5915f0f5` | fix(upload): close the cancel race on the compressed file and guard the HDR probe |

Not done, and why: the device check (brief step 4 and 5) needs the debug build signed in to
your account and uploads to a Blossom server, so it waits for you (see "Tests"). The version
stays `2.3.0-SNAPSHOT` until the tag exists (step 6).

## Decisions

| Decision | Taken as | Notes |
|---|---|---|
| A. `hdrMode` | **Option 1, keep the default** (recommended) | No code; the hint tells the user which outcome they get. The Pixel 9a bitrate overshoot on keep-HDR encodes (library bead 6k1) is accepted for now. |
| C. `cancel()` on timeout | **Not adopted** | `MultiOrchestrator.upload` and `uploadEncrypted` `launch` one job per attachment, so two videos compress at the same time and `cancel()` would stop the sibling. The orphaned output is deleted in `onSuccess` instead, and the library request (a per-`start()` handle) is recorded in the brief's section C. |
| F. Chat dialog | **Shows the line, without "Turn on H.265"** | Fourth string `video_hdr_converted`. The chat uploaders pass no `useH265`, so `MultiOrchestrator.upload`'s default `false` applies; the hint is called with `useH265 = false, offersH265 = false`. |
| F. Where the probe runs | In the composable, not at each call site | `VideoHdrHint(media, uploader, useH265)` probes inside, the way `MediaQualitySlider(media, uploader)` already does, instead of copying the `produceState` block into three dialogs (divergence 1). |
| Step 7 cleanup | Done | `./gradlew :amethyst:dependencies` has no `mp4parser`/`isoparser` line; the `builddef.lst` pick-first and its TODO are removed, the two `META-INF/LICENSE*` pick-firsts stay. |
| Step 8 release notes | New `docs/changelog/v1.21.00.md` | v1.20.0 is tagged and released (2026-10-24), so the next version gets a new file, linked from the changelog README. |

## Changes

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | `lightcompressor-enhanced = "2.3.0-SNAPSHOT"`. |
| `amethyst/build.gradle.kts` | Drops `pickFirsts.add("builddef.lst")` and the TODO above the packaging block. |
| `commons/.../service/uploads/video/VideoHdrInfo.kt` | New `data class VideoHdrInfo(isHdr, canKeepHdr)`. |
| `commons/.../service/uploads/HdrUploadOutcome.kt` | New `enum HdrUploadOutcome { KEPT, CONVERTED_BY_CODEC, CONVERTED_BY_DEVICE }` and `hdrUploadOutcome(probes, useH265)`. |
| `commons/.../service/uploads/MediaUploader.kt` | `suspend fun probeHdr(uri): VideoHdrInfo? = null` beside `probeVideo`. `UnavailableMediaUploader` and the test fakes inherit it. |
| `commons/.../service/uploads/MultiOrchestrator.kt` | `videos(): List<SelectedMedia>`; `firstVideo()` derives from it. |
| `commons/src/jvmTest/.../service/uploads/HdrUploadOutcomeTest.kt` | Six tests: nothing probed, all SDR, HDR + H.264, HDR + H.265 keepable, HDR + H.265 not keepable, SDR then HDR. |
| `commonsUI/.../ui/note/creators/uploads/MediaQualitySlider.kt` | `VideoHdrHint(media, uploader, useH265, offersH265)` and a private `UploadHintLine` shared with `VideoCompressionNeedsFfmpegHint`. |
| `commonsUI/.../ui/note/creators/uploads/ImageVideoDescription.kt` | Hint under the H.265 switch. |
| `commonsUI/.../ui/actions/NewMediaView.kt` | Hint under the H.265 switch, state from `postViewModel.useH265Codec`. |
| `commonsUI/.../chats/utils/ChatFileUploadDialog.kt` | Codec-less hint when `controls.videoWillBeCompressed`. |
| `commonsUI/.../composeResources/values/strings.xml` | `video_hdr_kept`, `video_hdr_converted_codec`, `video_hdr_converted`, `video_hdr_converted_device` (English only; Crowdin translates). |
| `amethyst/.../service/uploads/VideoCompressionHelper.kt` | `probe(context, uri): VideoInfo?` and `probeHdr(context, uri): VideoHdrInfo`; `onSuccess` deletes the output when the continuation is no longer active, resumes the success value with an `onCancellation` that deletes the file, and shares one `deleteOutput()`. |
| `amethyst/.../service/uploads/AndroidMediaUploader.kt` | Overrides `probeVideo` and `probeHdr` on `Dispatchers.IO`; `probeHdr` returns null if the library probe throws. |
| `docs/changelog/v1.21.00.md`, `docs/changelog/README.md` | Release notes for the next version. |
| `amethyst/plans/2026-10-28-lightcompressor-2.3.0-upgrade.md`, `amethyst/plans/README.md` | Status line, decision C record, queued-table entry. |

## Divergences from the brief

1. **The probe lives inside `VideoHdrHint`.** The brief drew `VideoHdrHint(outcome)` with the
   `produceState` block at each call site. Three call sites would have carried the same block, so
   the composable takes the orchestrator and the uploader and probes itself (as the slider does).
2. **`VideoCompressor.probeHdr` is called from `VideoCompressionHelper`, not `AndroidMediaUploader`.**
   Keeps every LightCompressor import in the helper; the uploader only maps the call to the
   interface. The uploader also wraps the call in a catch and returns null on failure, because the
   library's "never throws" covers `ColorInfoReader` but not the codec enumeration behind it
   (review finding M2).
3. **`MultiOrchestrator.videos()`** was added instead of a UI-side index walk. The walk's video
   test (`SelectedMedia.isVideo()`, case-sensitive) also differed from the orchestrator's own
   (`ignoreCase = true`), so the slider label and the hint could have disagreed on a `VIDEO/mp4`.
4. **Release notes** went into a new `v1.21.00.md` rather than "under the next version" of an
   existing file: `v1.20.00.md` is the released version.

## Tests

### Unit (JVM), all after the final commit

| Suite | Tests | Skipped | Failures |
|---|---|---|---|
| `:commons:jvmTest` (incl. the new `HdrUploadOutcomeTest`, 6) | 4112 | 0 | 0 |
| `:commonsUI:jvmTest` | 414 | 0 | 0 |
| `:amethyst:testPlayDebugUnitTest` (incl. `MediaCompressorTest` 7/1 skipped, `SquarePixelsResizerTest` 4, `HlsVideoEventBuilderTest` 12, `HlsPublishOrchestratorTest` 11) | 1550 | 10 | 0 |
| `:desktopApp:test --tests '*MediaQualitySliderUiTest'` | 1 | 0 | 0 |

The skipped `MediaCompressorTest` case and the ten skipped Android tests are pre-existing skips,
not touched here. `:amethyst:assemblePlayDebug` builds; `spotlessCheck` is clean. The first
step-1 build raced my edits (the interface gained `probeHdr` after `commons` had compiled) and
failed with `'probeHdr' overrides nothing`; the rerun on the final tree passed.

### Device (brief steps 4 and 5): **not run yet**

The Pixel 9a is reachable over adb Wi-Fi (`10.0.0.201:45439`). The checks need the debug build
(`com.vitorpamplona.amethyst.debug`) signed in to an account with a Blossom server, the two test
clips (`orig2.mp4` HLG, `jf_sdr_hevc10_1080p_3M.mp4` 10-bit SDR) on the phone, and uploads under
that account, so they wait for you. The list to run:

- slider label names the clip's own standard (a 4K or portrait phone recording is the clearest);
- SDR clip: no HDR line; output `filesDir/<uuid>.mp4`; uploads and plays;
- HLG clip, H.265 off: "converted … Turn on H.265"; on: "stays HDR"; line disappears when the
  HLG clip is removed from a mixed pick;
- HLG + H.265 upload: `ffprobe -show_streams` shows `hevc`, `Main 10`, `yuv420p10le`,
  `color_transfer=arib-std-b67`; HLG + H.264: `h264`, `yuv420p`, `bt709`, no cyan;
- posts play in Amethyst and a desktop browser;
- `run-as com.vitorpamplona.amethyst.debug ls files/` shows no `_temp` or `transcode_*` leftovers.

## Findings

- **Uploads compress concurrently.** `MultiOrchestrator` launches every attachment at once, which
  is what rules out `VideoCompressor.cancel()`; a per-`start()` handle in the library would let
  Amethyst cancel the encode instead of deleting its output afterwards.
- **`SelectedMedia` has identity equality.** `produceState(…, videos)` only re-probes when the
  orchestrator's list really changes because the orchestrator keeps the same instances; `videos()`
  says so in its KDoc.
- **Desktop already knows whether a source is HDR.** `FfprobeOutputParser` reads `color_transfer`
  into `ProbeResult.isHdr`, which `FfmpegVideoCompressor` uses for the post-upload
  `UploadNotice.HdrSource`. A `DesktopMediaUploader.probeHdr` override returning
  `VideoHdrInfo(isHdr, canKeepHdr = false)` would show the same pre-upload hint there and make
  the post-hoc notice redundant. Out of scope per the brief; a small follow-up.
- **Pre-existing:** the first video is opened three times in the dialog on Android (`MediaExtractor`
  for the pixel aspect ratio, `MediaMetadataRetriever` for the size, another `MediaExtractor` for the
  colour info) and again at compression time. All on `Dispatchers.IO`, once per picked set; not
  worth merging.
- **`desktopApp` is a plain JVM module**: its test task is `test`, not `jvmTest`.

## Review findings not acted on

Simplify pass (four reviewers; fixed: the `onSuccess` restructure and shared `deleteOutput`,
`MultiOrchestrator.videos()`, the shared `UploadHintLine`, the decision-C record):

- *Tri-state `useH265`/`offersH265` or a three-value `VideoHdrInfo` enum.* Would move UI wording
  ("turn on H.265") into a `commons` enum, or stop mirroring the library's `HdrProbe` one to one.
  Two booleans with one meaningless combination is the cheaper trade.
- *Self-gating `VideoHdrHint(controls, …)` with an `offersCodecSwitch` on `VideoUploadControls`.*
  The two post dialogs must place the line under the switch inside that block anyway, so
  self-gating saves only the chat dialog's one-line `if`, at the cost of `VideoUploadControls`
  keeping `media` and describing the chat layout.
- *Desktop `probeHdr` from ffprobe.* Out of the brief's scope ("Probing HDR on desktop"); listed
  above as a follow-up.
- *Parallel probes, a `derivedStateOf` for the video list, reusing the dialog's probe at
  compression time.* The efficiency reviewer judged each negligible for one-shot IO in an upload
  dialog; agreed.

Kotlin review (WARNING: 0 critical, 0 high, 4 medium, 3 low; fixed: M1 `onCancellation` on the
success resume, M2 catch around the library probe, L1 KDoc on `videos()`):

- *M3, `2.3.0-SNAPSHOT` in the catalog.* Known; brief step 6 pins `2.3.0` once JitPack serves the
  tag. Must happen before merge.
- *M4, a stale hint for the moment between a pick change and the new probes.* `produceState`
  keeps the old value until the restarted producer finishes; tens of milliseconds, and resetting
  the value first would flicker the line when a video is added.
- *L2, `videos()` allocates two lists and `firstVideo()` builds the whole list.* Lists of one to
  a handful of items in a dialog recomposition.
- *L3, a hypothetical second `onSuccess`.* Ruled out by the library's one-terminal-callback
  guarantee, now stated in the comment at the check.

## Release notes, tracker updates and status

- Release notes: `docs/changelog/v1.21.00.md` (new) and its link in `docs/changelog/README.md`.
- Plan status: the brief's status line points here; `amethyst/plans/README.md` keeps it under
  Queued until it ships.
- Next steps, in order:
  1. Run the device check above on the Pixel 9a and record the ffprobe output here.
  2. Tag LightCompressor-enhanced 2.3.0; when
     `https://jitpack.io/com/github/davotoula/LightCompressor-enhanced/2.3.0/LightCompressor-enhanced-2.3.0.pom`
     returns 200, set `lightcompressor-enhanced = "2.3.0"` and rebuild.
  3. `PREPUSH_KMP_METADATA=1 bash .git-hooks/pre-push` by hand, push the branch to the fork,
     open the PR against `vitorpamplona:main` when you ask for it.
