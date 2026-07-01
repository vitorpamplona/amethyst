# Linux bundled FFmpeg

The DEB/RPM/AppImage packages **do not bundle FFmpeg** — they declare a
runtime dependency on system FFmpeg/GStreamer instead, which keeps the
binary's SPDX cleaner and reduces package size.

The Flatpak manifest similarly relies on `org.freedesktop.Platform 24.08`
which ships an LGPL GStreamer + FFmpeg; thumbnail extraction falls through
to the host FFmpeg if installed.

If you want a self-contained Linux build (e.g. AppImage with no host deps),
drop a static LGPL `ffmpeg` binary here. johnvansickle.com publishes LGPL
"release" builds for x86_64 and aarch64. Source: https://johnvansickle.com/ffmpeg/

The `VideoThumbnailCache` will prefer system `ffmpeg` on `$PATH` first, then
fall through to this bundled binary.
