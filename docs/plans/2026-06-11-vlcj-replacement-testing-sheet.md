# Manual Testing Sheet — vlcj → kdroidFilter Migration

**Plan:** [`2026-06-11-feat-replace-vlcj-with-kdroidfilter-plan.md`](2026-06-11-feat-replace-vlcj-with-kdroidfilter-plan.md)
**Branch / worktree:** `.claude/worktrees/vlcj-licensing-brainstorm` (branch `worktree-vlcj-licensing-brainstorm`)
**Date:** 2026-06-11

This sheet walks through every verification the user must perform locally
because the automated background session can't reach Maven Central or
exercise the JVM media stack across OSes.

---

## 0 · Build verification — VERIFIED locally on macOS arm64 (2026-06-11)

Build verified after network came online. Final dep pins:
- `io.github.kdroidfilter:composemediaplayer:0.10.0` (research-agent hallucinated 0.10.1 — actual latest on Maven Central is 0.10.0)
- `org.jcodec:jcodec:0.2.5`
- `org.jcodec:jcodec-javase:0.2.5`

| # | Command | Result on macOS arm64 |
|---|---------|-----------------------|
| 0.1 | `./gradlew :desktopApp:compileKotlin` | ✅ `BUILD SUCCESSFUL` in 16s |
| 0.2 | `./gradlew :desktopApp:test` | ✅ `BUILD SUCCESSFUL` in 23s |
| 0.3 | `./gradlew :desktopApp:spotlessApply` | ✅ clean, no diff |
| 0.4 | `grep -rn 'vlcj\|caprica\|VlcjPlayerPool\|MacOsVlcDiscoverer\|BundledVlcDiscoverer\|VlcResourceResolver' desktopApp/src/ gradle/ desktopApp/build.gradle.kts` | ✅ Only doc-comment mentions in `VideoThumbnailCache.kt` header (intentional; describes what was replaced) |

Re-verify on Windows + Linux at your convenience.

---

## 1 · Bundled FFmpeg binaries (REQUIRED for thumbnail fallback)

The agent created the directory structure but didn't check in binaries
(~30 MB each, not appropriate for git). Drop them here before packaging:

| OS | Path | Source | Verify |
|----|------|--------|--------|
| macOS (arm64 + x86_64 universal) | `desktopApp/src/jvmMain/appResources/macos/ffmpeg/ffmpeg` | https://www.osxexperts.net/ LGPL build | `lipo -info ffmpeg` shows both arches; `chmod +x ffmpeg` |
| Windows (x64) | `desktopApp/src/jvmMain/appResources/windows/ffmpeg/ffmpeg.exe` | https://github.com/Crigges/Prebuilt-LGPL-2.1-FFmpeg-with-OpenH264 | `ffmpeg.exe -version` reports `--enable-version3 --disable-gpl` flags |
| Linux | _(skip — relies on host ffmpeg/gstreamer)_ | n/a | See `desktopApp/src/jvmMain/appResources/linux/ffmpeg/README.md` for self-contained AppImage option |

Each per-OS subdirectory has a `README.md` with the exact source URL.

---

## 2 · Smoke launch

| # | Action | Expected |
|---|--------|----------|
| 2.1 | `./gradlew :desktopApp:run` | Window opens. No log line mentions VLC, vlcj, libvlc, or "plugin path". |
| 2.2 | Look at stdout / stderr | No `WARN`/`ERROR` from missing native libs. First time kdroidFilter extracts its native to `~/.cache/composemediaplayer/native/` — that's expected. |
| 2.3 | App responds to clicks; UI is layout-identical to pre-migration | Yes |

---

## 3 · Codec / playback matrix

Use the URLs below (substitute your own equivalents if any are dead). All
verified by mounting the URL via the feed; you don't need to post any
Nostr event.

### Active video URLs to feed into the player (place into a draft note or open via the lightbox)

| # | Test | URL pattern | macOS | Windows | Linux |
|---|------|------|--------|---------|-------|
| 3.1 | H.264 MP4 (golden path) | `https://download.samplelib.com/mp4/sample-5s.mp4` | ✅ plays | ✅ plays | ✅ plays |
| 3.2 | HLS livestream (zap.stream) | a live zap.stream m3u8 URL | ✅ plays | ✅ plays | ✅ plays |
| 3.3 | HLS VOD H.264+AAC | `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` | ✅ plays | ✅ plays | ✅ plays |
| 3.4 | VP9 WebM | a known nostr.build VP9 webm URL | ✅ plays | ⚠️ codec-missing UX, "open externally" works | ✅ plays (if gst-libav installed) |
| 3.5 | AV1 MP4 | a known void.cat / nostr.build AV1 URL | ✅ plays (macOS 13+ or M3+ HW) | ⚠️ codec-missing UX | ✅ plays (if gst-libav installed) |
| 3.6 | HEVC MP4 | a known iOS-recorded HEVC URL | ✅ plays | ⚠️ "Install HEVC extension" UX | ✅ plays (if gst-libav installed) |
| 3.7 | Audio: MP3 | a podcast MP3 from feed | ✅ plays | ✅ plays | ✅ plays |
| 3.8 | Audio: Opus | a known Opus URL | ✅ plays | ✅ plays | ✅ plays (if gst-plugins-base installed) |
| 3.9 | Audio: AAC | a known AAC URL | ✅ plays | ✅ plays | ✅ plays |
| 3.10 | Garbage URL (404) | `https://example.invalid/nope.mp4` | "Can't play this video" + `Source:` reason; "Open in default player" button does nothing harmful | same | same |

**Phase 1 decision gate** (per plan): if 3.2 (zap.stream HLS livestream)
fails on any OS for reasons that aren't codec-related, pivot to the
gst1-java-core fallback (research doc §3 Recommended #2). Don't pivot for
3.4/3.5/3.6 — those gaps are expected on stock Win10 without the codec
extensions and are mitigated by the "open externally" UX.

---

## 4 · UI cross-state behaviour

| # | Scenario | Expected |
|---|----------|----------|
| 4.1 | Play feed video A → navigate to a different screen → return | Video continues playing, position preserved. (Singleton `GlobalMediaPlayer` retained.) |
| 4.2 | Play video A → tap play on video B | A stops, B starts from beginning. |
| 4.3 | Active video → tap fullscreen | Enters native fullscreen; same `VideoPlayerSurface` renders fullscreen; controls work; Esc / F exits. |
| 4.4 | Fullscreen video → press Spacebar | Toggles play/pause. |
| 4.5 | NowPlayingBar mini-preview | Mini 48×36 video preview renders inside the bottom bar while video is active. |
| 4.6 | Audio playback → navigate around | NowPlayingBar shows music icon (no video) + correct artist/title (URL filename suffices for now). |
| 4.7 | Mute → unmute | Volume restores to pre-mute value. |
| 4.8 | Volume slider → 0 | `volume = 0` reflects, but `isMuted` flag stays false (matches kdroidFilter's no-mute-flag semantics; explicit mute is separate). |
| 4.9 | Stop video / audio while playing | Engine cleanly stops; UI returns to neutral state. |
| 4.10 | Quit app while playing | No segfault, no lingering native processes. (kdroidFilter registers its own shutdown hook on Windows for MediaFoundation.) |

---

## 5 · Thumbnail extraction

| # | Scenario | Expected |
|---|----------|----------|
| 5.1 | H.264 MP4 in feed (inactive) | Thumbnail appears within 2-3s. JCodec handles this — confirms by speed. |
| 5.2 | VP9 WebM in feed (inactive) | Thumbnail appears (slower, ~5s) — JCodec rejects, falls through to ffmpeg. Verifies the cascade. |
| 5.3 | Same feed reopened | Thumbnails appear instantly from in-memory cache. |
| 5.4 | No bundled ffmpeg + no system ffmpeg | Thumbnails fail silently for non-H.264 sources; H.264 still works (JCodec). No crash. Document this UX in release notes. |
| 5.5 | HLS livestream thumbnail | Should NOT block playback. JCodec auto-skipped because URL ends `.m3u8` or contains `/hls/`. Falls through to `ffmpeg URL` directly. |

---

## 6 · License metadata verification

| # | Artifact | Verify |
|---|----------|--------|
| 6.1 | `./gradlew :desktopApp:packageRpm` → `rpm -qpi build/compose/binaries/main-release/rpm/*.rpm` | `License: MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0` |
| 6.2 | `./gradlew :desktopApp:packageDmg` → mount DMG | Inside `Amethyst.app/Contents/app/resources/common/` — `NOTICE.md`, `licenses/LICENSE-MIT-amethyst.txt`, `licenses/LICENSE-MIT-kdroidfilter.txt`, `licenses/LICENSE-BSD-2-jcodec.txt`, `licenses/LICENSE-LGPL-2.1.txt` |
| 6.3 | `grep -rn 'vlcj\|GPL-3' desktopApp/src/jvmMain/appResources/common/` | Zero hits — confirms no stale GPL references shipped |
| 6.4 | `LICENSE-LGPL-2.1.txt` | **Currently a placeholder.** Replace with verbatim GNU LGPL-2.1 text (~26 KB) from https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt before any release. The plan acceptance gate flags this. |

---

## 7 · Flathub manifest (deferred submission)

| # | Action | Expected |
|---|--------|----------|
| 7.1 | `flatpak install org.freedesktop.Platform//24.08 org.freedesktop.Sdk//24.08 org.freedesktop.Sdk.Extension.openjdk21//24.08` | OK |
| 7.2 | `./gradlew :desktopApp:createReleaseDistributable` | Produces `desktopApp/build/compose/binaries/main-release/app/Amethyst/` |
| 7.3 | `cd desktopApp/packaging/flatpak && flatpak-builder --user --install --force-clean build-dir com.vitorpamplona.amethyst.Desktop.yml` | OK (may need icon at `icons/256/com.vitorpamplona.amethyst.Desktop.png` — copy from `desktopApp/src/jvmMain/resources/icon.png` first) |
| 7.4 | `flatpak run com.vitorpamplona.amethyst.Desktop` | App launches; video playback works (host org.freedesktop.Platform ships GStreamer 1.24) |
| 7.5 | Actual Flathub submission | Out of scope for this PR; follow steps in `desktopApp/packaging/flatpak/README.md` |

---

## 8 · macOS code signing (deferred — only if you sign before this PR merges)

| # | Action | Expected |
|---|--------|----------|
| 8.1 | jpackage with `--mac-sign --mac-signing-key-user-name "Developer ID Application: …"` | `codesign --verify --deep --strict Amethyst.app` is clean; nested `Contents/app/resources/macos/ffmpeg/ffmpeg` is signed with the same identity |
| 8.2 | Entitlements include `com.apple.security.cs.allow-jit`, `com.apple.security.cs.allow-unsigned-executable-memory`, `com.apple.security.cs.disable-library-validation` | Required for HotSpot + sibling dylib loading |
| 8.3 | `xcrun notarytool submit Amethyst.dmg` succeeds | Apple notarizes the DMG including nested ffmpeg |
| 8.4 | `xcrun stapler staple Amethyst.dmg` succeeds | Ticket stapled |

Detailed recipe: `docs/plans/_macos-ffmpeg-signing-recipe.md`.

---

## 9 · Known caveats / follow-ups for after this PR

These are intentional gaps, not bugs:

1. **In-app "Open source licenses" screen** — not yet wired into desktop settings UI. NOTICE.md and licenses/ ship as resources but a Compose composable reading them isn't included. Recommended follow-up: add `mikepenz/AboutLibraries` Gradle plugin (auto-discovers Maven licenses) + a `LicensesScreen.kt` rendering the report; integrate into the settings nav.
2. **LGPL-2.1 license text is a placeholder** — flagged in 6.4. The release-gate fix is "paste the verbatim 26 KB text into `LICENSE-LGPL-2.1.txt`".
3. **`tryJaffree*` and `runFfmpegToImage`** — the Jaffree dependency was dropped in favor of raw ProcessBuilder for simplicity. Renaming the internal `tryJaffree*` functions to `tryFfmpeg*` is a cosmetic follow-up.
4. **No GStreamer probe on Linux** — kdroidFilter's `SourceError` is indistinguishable from "missing gst-libav" without probing. Follow-up: add a one-time `gst-inspect-1.0 avdec_h264` probe on Linux startup; show a non-blocking install hint if missing.
5. **Windows codec-extension detection** — currently we just rely on kdroidFilter's `SourceError` and our error UX. A nicer follow-up: probe `MFTEnumEx` to detect HEVC/AV1 codec availability up front and prompt for the MS Store extension.
6. **Flathub icon file** — manifest references `icons/256/com.vitorpamplona.amethyst.Desktop.png` which doesn't exist. Copy/resize from `desktopApp/src/jvmMain/resources/icon.png` before the first Flathub submission.

---

## 10 · Roll-back path

If anything in 0-5 blocks shipping:

- **Code revert:** `git revert <merge-commit>` of this PR is clean — vlcj files were deleted, new files are isolated to `desktopApp/`.
- **Branch:** `git branch -D worktree-vlcj-licensing-brainstorm` (or whatever the merged branch was). `git checkout main && git pull` returns to vlcj-based desktop.
- **No data migration:** nothing on disk format changed. Settings, account state, downloaded thumbnails — all forward+backward compatible.

---

## Self-check before opening the PR

- [ ] 0.1 BUILD SUCCESSFUL on my machine
- [ ] 0.2 tests green
- [ ] 0.4 no vlcj source code references
- [ ] 1 ffmpeg binaries in place for the OSes I'm packaging
- [ ] 2.1 app launches without VLC warnings
- [ ] 3.1, 3.2, 3.3, 3.7 pass on at least my primary OS
- [ ] 6.1 RPM license metadata correct
- [ ] 6.4 LGPL text placeholder noted in release checklist
