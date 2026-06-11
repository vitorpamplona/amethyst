# Windows bundled FFmpeg

Drop a single LGPL Windows x64 FFmpeg binary here as `ffmpeg.exe`.

## Recommended source

Crigges/Prebuilt-LGPL-2.1-FFmpeg-with-OpenH264:
- Repo: https://github.com/Crigges/Prebuilt-LGPL-2.1-FFmpeg-with-OpenH264
- Confirm: SPDX LGPL-2.1-or-later, OpenH264 (BSD-2 with patent grant from Cisco).
- Verify: `ffmpeg.exe -version` from a `cmd.exe` prompt.

Used as fallback by `VideoThumbnailCache` for non-H.264 / HLS thumbnails.

## Size

~20-30 MB. Acceptable given the ~95 MB VLC plugin tree this migration retires.
