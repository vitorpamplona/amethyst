# Flatpak packaging for Amethyst Desktop

This directory contains the Flatpak manifest and associated metadata. It is
used two ways:

1. **Release CI** (`.github/workflows/create-release.yml`, `linux-portable`
   leg) builds a single-file bundle from it on every tag and attaches it to
   the GitHub Release as `amethyst-desktop-<version>-linux-x64.flatpak`.
2. **Flathub submission** — the `flathub/` subdirectory holds a
   submission-ready variant that builds from the published GitHub Release
   tarball (Flathub's build servers must fetch sources themselves; the
   local `type: dir` tree used by CI is not allowed there).

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
- `flathub/` — self-contained, copy-ready Flathub submission dir: its own
  manifest (archive source pinned to the release tarball URL + sha256, with
  `x-checker-data` so Flathub's update bot bumps it), its own metainfo
  (carries the permanent `<releases>` history Flathub requires), desktop
  entry, icon, and `flathub.json` (`only-arches: x86_64` — we publish no
  aarch64 tarball, and jpackage can't cross-compile one)

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

Follow https://docs.flathub.org/docs/for-app-authors/submission — no
separate Flathub account exists; everything runs through GitHub PRs.

**Remaining blocker before submitting:** at least one screenshot with a
publicly reachable URL in `flathub/….metainfo.xml` (the Flathub linter
rejects the current empty placeholder).

1. Fork `flathub/flathub` on GitHub.
2. Branch from `new-pr` (NOT `master`).
3. Copy the contents of `flathub/` verbatim into a new top-level directory
   named `com.vitorpamplona.amethyst.Desktop`.
4. Open a PR titled "Add com.vitorpamplona.amethyst.Desktop".
5. After merge, Flathub creates a per-app repo
   (`flathub/com.vitorpamplona.amethyst.Desktop`) with write access for
   ongoing updates. Its `x-checker-data` makes
   flatpak-external-data-checker open update PRs there automatically on
   each new GitHub Release (bumping url/sha256 and appending the metainfo
   `<release>` entry).

To test the Flathub variant locally, run the same flatpak-builder commands
as above from inside `flathub/` — it downloads the pinned release tarball
instead of using a local Gradle build.

## License metadata

The manifest declares the binary as
`MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0`. This SPDX
expression validates via `appstreamcli validate`.
