# Flathub packaging for Amethyst Desktop

This directory contains the Flatpak manifest and associated metadata for
publishing Amethyst Desktop on Flathub.

## Files

- `com.vitorpamplona.amethyst.Desktop.yml` — Flatpak manifest
- `com.vitorpamplona.amethyst.Desktop.metainfo.xml` — AppStream metadata
  (categories, license, screenshots — needs screenshots added before
  submission)
- `com.vitorpamplona.amethyst.Desktop.desktop` — XDG desktop entry
- `icons/256/com.vitorpamplona.amethyst.Desktop.png` — TODO: copy a 256x256
  PNG icon from `desktopApp/src/jvmMain/resources/icon.png` before
  submission

## Build prerequisites

- `./gradlew :desktopApp:createReleaseDistributable` — produces
  `desktopApp/build/compose/binaries/main-release/app/Amethyst/`
- `flatpak install org.freedesktop.Platform//24.08 org.freedesktop.Sdk//24.08 org.freedesktop.Sdk.Extension.openjdk21//24.08`

## Local build

```bash
cd desktopApp/packaging/flatpak
flatpak-builder --user --install --force-clean build-dir com.vitorpamplona.amethyst.Desktop.yml
flatpak run com.vitorpamplona.amethyst.Desktop
```

## Submission to Flathub

Follow https://docs.flathub.org/docs/for-app-authors/submission

1. Fork `flathub/flathub` on GitHub.
2. Branch from `new-pr` (NOT `master`).
3. Copy this manifest + AppStream + desktop into a new directory matching
   the app id.
4. Open a PR titled "Add com.vitorpamplona.amethyst.Desktop".
5. After merge, a per-app repo is created with write access for ongoing
   updates.

## Codec coverage

- HEVC / VP9 / AV1: covered via `org.freedesktop.Platform.ffmpeg-full`
  add-extension declared in the manifest. Flatpak downloads it on install.
- HLS, H.264, AAC, MP3, Opus: covered by the GStreamer plugin set in
  `org.freedesktop.Platform 24.08` itself.

## License metadata

The manifest declares the binary as
`MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0`. This SPDX
expression validates via `appstreamcli validate`.
