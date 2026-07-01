# macOS bundled FFmpeg

Drop a single universal (arm64 + x86_64) LGPL FFmpeg binary here as `ffmpeg`
(no extension) with `+x` permission. Used by `VideoThumbnailCache` for
non-H.264 / non-faststart thumbnail extraction (HEVC, VP9, AV1, HLS).

## Recommended source

osxexperts.net "FFmpeg static (LGPL)" build:
- Page: https://www.osxexperts.net/
- Verify SPDX: LGPL-2.1-or-later (configure flags: `--disable-gpl --disable-nonfree`)
- After download: `lipo -info ffmpeg` should report `arm64 x86_64`. If
  separate arch binaries, fuse with:
    `lipo -create ffmpeg-arm64 ffmpeg-x86_64 -output ffmpeg`
- Make executable: `chmod +x ffmpeg`

## Codesigning

`jpackage --mac-sign` traverses `Contents/app/resources/` and signs nested
executables with the same Developer ID identity as the app bundle. The
process is launched from the JVM at runtime; hardened-runtime entitlements
(`allow-jit`, `disable-library-validation`) are documented in
`docs/plans/_macos-ffmpeg-signing-recipe.md`.

## Size

~30-40 MB universal binary. Acceptable trade-off given we shed ~95 MB of
bundled VLC by switching to kdroidFilter + this thin LGPL FFmpeg.
