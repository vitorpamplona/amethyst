# Manual Testing Sheet — Desktop Video Compression (phase 1)

**Spec:** `desktopApp/plans/2026-10-11-desktop-video-compression.md`
**Plan:** `desktopApp/plans/2026-10-11-desktop-video-compression-plan.md`
**Branch:** `feat/desktop-video-compression`
**Run:** `./gradlew :desktopApp:run` — and, for the no-ffmpeg sections, `AMETHYST_FFMPEG_DISABLED=1 ./gradlew :desktopApp:run`
(the `run` task's JVM inherits the shell environment; a bare `PATH` is **not** enough, the locator finds Homebrew by design).

Everything below is 🟢 LIVE: built, unit-tested where the logic allows, and ready to click through.
The automated suite already covers the rules table, the slider mapping, the ffmpeg command lines,
encoder selection, the stall watchdog and a set of real encodes (`./gradlew :commons:jvmTest --tests "*Ffmpeg*"`).
This sheet covers what only a person with the app, a server and a few clips can check.

**Legend for results:** ✅ pass · ❌ fail (write what happened) · ⏭️ skipped/blocked

---

## Setup (do once)

- [ ] `ffmpeg -version` and `ffprobe -version` work in a terminal (Homebrew: `brew install ffmpeg`).
- [ ] `ffmpeg -hide_banner -encoders | grep -E "libx264|hevc_videotoolbox|libx265"` — note which HEVC encoders this machine has.
- [ ] Clips in `~/Downloads`:
  - **`big.mp4`**, a high-bitrate 1080p source: a real phone or camera clip of 10–30 s (phones write 1080p at 10–20 Mbps), or build one:
    `ffmpeg -f lavfi -i "color=c=gray:s=1920x1080:r=30,noise=alls=100:allf=t+u" -f lavfi -i sine -t 10 -shortest -pix_fmt yuv420p -c:v libx264 -crf 18 -preset ultrafast -metadata title=secret -metadata location=+48.2082+016.3738/ ~/Downloads/big.mp4`
    (check: `ffprobe` reports a video `bit_rate` well above 4 000 000). Note its `shasum -a 256`.
  - `file_example_MOV_1920_2_2MB.mov` (the investigation's clip; sha256 `ead8b50c…e40c62`). It is 1080p H.264 at only ~450 kbps, so every encode above Low comes out **larger** than the source and the pipeline uploads the remuxed original — it proves the size rule (row 2.7), not the encoder.
  - a **portrait phone video** (taller than wide, shot on a phone)
  - an **HDR iPhone clip** (Settings › Camera › Record Video › HDR on; the probe sees `color_transfer` `arib-std-b67`)
  - an animated **GIF**
  - a **corrupt video**: `head -c 300000 file_example_MOV_1920_2_2MB.mov > broken.mov`
  - a **long clip** (≥ 2 min) for the NIP-95 refusal
- [ ] A Blossom server you can upload to (blossom.band works) and, for §7, a NIP-95 relay-event upload target.
- [ ] A terminal open for `pgrep -l ffmpeg`, `shasum -a 256`, `curl` and `ffprobe`.

> `ffprobe` one-liner used throughout:
> `ffprobe -v error -select_streams V:0 -show_entries stream=width,height,codec_name,bit_rate:format_tags -of json <file>`

---

## 1. Composer with ffmpeg installed  🟢 LIVE

Open the post composer, attach media with the **Upload Media** button.

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1.1 | Hover the attach button | Tooltip reads **Upload Media** | |
| 1.2 | Pick `file_example_MOV_1920_2_2MB.mov` | The quality slider shows **4 stops** (Low / Medium / High / Uncompressed), the **H.265** switch is visible, the **strip metadata** toggle is hidden while the slider is below Uncompressed | |
| 1.3 | Read the label at High | `High · 1080p video → 1080p, 4 Mbps` (the file's real standard, not an example) | |
| 1.4 | Move to Medium, then Low | `Medium · 1080p video → 720p, 2 Mbps`, then `Low · 1080p video → 480p, 0.75 Mbps` | |
| 1.5 | Move to Uncompressed | No video phrase; the H.265 switch disappears; the strip-metadata toggle **appears** | |
| 1.6 | Add a **photo** to the same post, then move the slider through every stop | Still 4 stops; the label shows both parts, e.g. `High · images up to 1920 px · 1080p video → 1080p, 4 Mbps` | |
| 1.7 | New post: photo first, slider to **Very High**, then add a video | The slider snaps to **High** (Very High has no video rule) and keeps 4 stops | |
| 1.8 | Remove the video again | 5 stops return; the position stays on High (known and accepted: Very High is not restored) | |
| 1.9 | Pick the **GIF** | A **Convert GIF to MP4** switch appears; turning it on hides the slider | |
| 1.10 | Profile › edit › picture picker | Tooltip still reads **Upload Image** | |

---

## 2. Upload results — the hash check  🟢 LIVE

Upload **`big.mp4`** to **blossom.band** from the composer, once per row, and fetch the blob
(`curl -o out.mp4 <url>`; `shasum -a 256 out.mp4`; the `ffprobe` one-liner).

| # | Step | Expected | Result |
|---|------|----------|--------|
| 2.1 | **High**, H.265 off | sha256 **differs** from the source; the blob is much **smaller**; `.mp4`; 1920×1080; `codec_name` `h264`; video `bit_rate` ≤ 4 400 000; no `title`/`location` in `format_tags`; the progress bar shows **Compressing** before **Uploading** | |
| 2.2 | **Low** | short side **480** (854×480); bitrate well under 2.1's | |
| 2.3 | **High**, H.265 **on**, machine with an HEVC encoder | `codec_name` `hevc`; plays in QuickTime/Safari (the `hvc1` tag) | |
| 2.4 | **High**, H.265 **on**, machine **without** an HEVC encoder | A toast **"H.265 not available … encoded as H.264"**; the blob is `h264` | |
| 2.5 | **Uncompressed**, strip metadata **on** | sha256 differs from the source (remuxed); `codec_name` is the **source's**; bitrate is the source's; no `title`/`location` tags | |
| 2.6 | **Uncompressed**, strip metadata **off** | sha256 **equals** the source | |
| 2.7 | `file_example_MOV_1920_2_2MB.mov` at **High** | The upload is the **remuxed original**: sha256 differs from `ead8b50c…e40c62`, `codec_name` `h264`, bitrate ≈ 450 kbps (the source's, not 4 Mbps), size ≈ 2.2 MB, tags gone. A dialog **"Original video kept — Compressing did not make the video smaller…"** | |
| 2.8 | `file_example_MOV_1920_2_2MB.mov` at **Low** | Compressed to 854×480 (the only level whose encode is smaller than this source) | |

---

## 3. Special sources  🟢 LIVE

| # | Step | Expected | Result |
|---|------|----------|--------|
| 3.1 | Portrait phone video at High | Label names its real standard (short side = width); uploaded blob is **portrait** (e.g. 1080×1920), plays upright, no rotation metadata | |
| 3.2 | HDR iPhone clip at High | A toast **"HDR video — Compressed HDR video looks flat…"**; the upload still completes | |
| 3.3 | GIF with **Convert GIF to MP4** on | The post carries an `.mp4` at the GIF's own size; it loops/plays in the feed | |
| 3.4 | Corrupt `broken.mov` at High | A toast **"Video compression failed — The original file is being uploaded instead…"**; the upload completes with the original bytes | |
| 3.5 | A clip with no audio track | Encodes and uploads without error | |

---

## 4. Concurrency, cancellation, cleanup  🟢 LIVE

| # | Step | Expected | Result |
|---|------|----------|--------|
| 4.1 | Three videos in one post, post it, run `pgrep -l ffmpeg` repeatedly | **One** `ffmpeg` at a time; all three upload | |
| 4.2 | Start a long encode and **quit the app** mid-way | `pgrep -l ffmpeg` is empty within a second; `ls ~/.amethyst/tmp/amethyst_video_*` (or wherever `AmethystTempDir.rootDir()` points) shows nothing | |
| 4.3 | Start an encode and **cancel** the post | No `ffmpeg` left; no `amethyst_video_*` temp left | |
| 4.4 | After a successful upload | No `amethyst_video_*` temp left | |

---

## 5. NIP-95 (file inside the event)  🟢 LIVE

| # | Step | Expected | Result |
|---|------|----------|--------|
| 5.1 | Pick the **long clip** with a NIP-95 target at High | Refused **at once** with the "too big for NIP-95" error — no multi-minute encode first (`pgrep ffmpeg` stays empty) | |
| 5.2 | A clip that compresses to under 80 KB (a 1–2 s clip at Low) | Encodes, then publishes as an event | |

---

## 6. Chat file dialog  🟢 LIVE

Open a DM, attach a video through the chat file dialog.

| # | Step | Expected | Result |
|---|------|----------|--------|
| 6.1 | With ffmpeg | The slider shows 4 stops with the video label; **no** H.265 switch (the chat state has none); the strip-metadata toggle is **always** shown | |
| 6.2 | Send `big.mp4` at High | The received video is 1080p H.264 (same checks as 2.1) | |
| 6.3 | Encrypted DM attachment | Same result; the plaintext was compressed before encryption | |

---

## 7. Without ffmpeg  🟢 LIVE

Quit the app and relaunch with `AMETHYST_FFMPEG_DISABLED=1 ./gradlew :desktopApp:run`.

| # | Step | Expected | Result |
|---|------|----------|--------|
| 7.1 | Pick a video in the composer | **No** slider for the video alone, **no** H.265 switch, one grey hint line **"Install ffmpeg to compress videos. Until then videos are uploaded as they are."**, the strip-metadata toggle **stays visible** | |
| 7.2 | Photo + video | The slider shows **5 stops** (images only) and its label has **no** video phrase | |
| 7.3 | GIF | No Convert GIF to MP4 switch | |
| 7.4 | Upload `big.mp4` | sha256 **equals** the source (nothing touched it) | |
| 7.5 | Chat file dialog with a video | Same hint; the slider appears only when a photo is also attached | |
| 7.6 | Video thumbnails in the feed | Still render (the thumbnail cache now shares the locator; disabled means no ffmpeg thumbnails, posters from the event still show) | |

---

## 8. Packaged app from Finder  🟢 LIVE

`./gradlew :desktopApp:packageDmg`, install from `desktopApp/build/compose/binaries/main/dmg/`, launch **from Finder** (not from a terminal — Finder-launched apps have an empty launchd `PATH`).

| # | Step | Expected | Result |
|---|------|----------|--------|
| 8.1 | Pick a video in the composer with Homebrew ffmpeg installed | The video controls appear (found through `/opt/homebrew/bin`) | |
| 8.2 | Upload `big.mp4` at High | Same result as 2.1 | |
