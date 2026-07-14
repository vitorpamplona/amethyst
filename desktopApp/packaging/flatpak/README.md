# Flatpak packaging for Amethyst Desktop

This directory contains the Flatpak manifest and associated metadata. It is
used two ways:

1. **Release CI** (`.github/workflows/create-release.yml`, `linux-portable`
   leg) builds a single-file bundle from it on every tag and attaches it to
   the GitHub Release as `amethyst-desktop-<version>-linux-x64.flatpak`.
2. **Flathub submission** (future) — the same manifest is the starting
   point, but Flathub requires building from downloadable sources instead of
   the local `type: dir` tree, so it will need a source rewrite at
   submission time.

## Files

- `com.vitorpamplona.amethyst.Desktop.yml` — Flatpak manifest. It packages
  the **prebuilt** jpackage tree from
  `./gradlew :desktopApp:createReleaseDistributable` (which bundles its own
  trimmed JRE — that's why there is no openjdk module/sdk-extension).
- `com.vitorpamplona.amethyst.Desktop.metainfo.xml` — AppStream metadata.
  Release CI injects the `<release>` entry for the version being built
  (the checked-in file deliberately carries none); screenshots still need
  to be added before any Flathub submission.
- `com.vitorpamplona.amethyst.Desktop.desktop` — XDG desktop entry
- `icons/512/com.vitorpamplona.amethyst.Desktop.png` — 512x512 icon (copy of
  `desktopApp/src/jvmMain/resources/icon.png`)

## Local build

```bash
# 1. Build the app tree the manifest packages
./gradlew :desktopApp:createReleaseDistributable

# 2. Tooling + Flathub remote (one-time)
sudo apt-get install -y flatpak flatpak-builder   # or distro equivalent
flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo

# 3. Build + install locally
cd desktopApp/packaging/flatpak
flatpak-builder --user --install --install-deps-from=flathub --force-clean \
  build-dir com.vitorpamplona.amethyst.Desktop.yml
flatpak run com.vitorpamplona.amethyst.Desktop
```

To produce the distributable single-file bundle instead (what CI ships):

```bash
flatpak-builder --user --install-deps-from=flathub --force-clean \
  --repo=repo build-dir com.vitorpamplona.amethyst.Desktop.yml
flatpak build-bundle repo amethyst.flatpak com.vitorpamplona.amethyst.Desktop \
  --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo
```

Installing the bundle: `flatpak install --user ./amethyst.flatpak`. The
`--runtime-repo` baked in above lets flatpak fetch the freedesktop runtime
from Flathub automatically on the user's machine.

## Sandbox notes

- `--socket=x11` (not wayland/fallback-x11): Compose Desktop renders through
  AWT/skiko, which is X11-only on Linux and runs under XWayland on Wayland
  sessions.

## Codec coverage

- HEVC / VP9 / AV1: covered via `org.freedesktop.Platform.ffmpeg-full`
  add-extension declared in the manifest. Flatpak downloads it on install.
- HLS, H.264, AAC, MP3, Opus: covered by the GStreamer plugin set in
  `org.freedesktop.Platform 24.08` itself.

## Submission to Flathub

Follow https://docs.flathub.org/docs/for-app-authors/submission

1. Fork `flathub/flathub` on GitHub.
2. Branch from `new-pr` (NOT `master`).
3. Copy this manifest + AppStream + desktop into a new directory matching
   the app id, and rework the `type: dir` source into a
   buildable-from-source or downloadable-artifact form per Flathub policy.
4. Open a PR titled "Add com.vitorpamplona.amethyst.Desktop".
5. After merge, a per-app repo is created with write access for ongoing
   updates.

## License metadata

The manifest declares the binary as
`MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0`. This SPDX
expression validates via `appstreamcli validate`.
