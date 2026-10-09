# Flatpak packaging for Amethyst Desktop

App ID: **`com.vitorpamplona.amethyst`**. Up to v1.17.0 the bundle shipped as
`com.vitorpamplona.amethyst.Desktop`; Flathub forbids IDs that end in generic
terms like `.desktop`, so it was renamed. `BUILDING.md` § Uninstall + state
paths explains how users move their data to the new ID.

Release CI (`.github/workflows/create-release.yml`, `linux-portable` leg)
builds a single-file bundle from this directory on every tag and attaches it to
the GitHub Release as `amethyst-desktop-<version>-linux-<x64|arm64>.flatpak`.

**Amethyst is not on Flathub.** The manifest here repackages a prebuilt tree,
which Flathub does not accept. See [Flathub](#flathub) below.

## Files

- `com.vitorpamplona.amethyst.yml`: Flatpak manifest for the GitHub Release
  bundle. It packages the **prebuilt** jpackage tree from
  `./gradlew :desktopApp:createReleaseDistributable`, which bundles its own
  trimmed JRE (hence no openjdk extension).
- `com.vitorpamplona.amethyst.metainfo.xml`: AppStream metadata. It keeps the
  release history: add an entry when tagging (`RELEASE_OPS.md` § Pre-tag
  checklist). CI injects a bare entry for the version it builds if one is
  missing.
- `com.vitorpamplona.amethyst.desktop`: XDG desktop entry.
- `icons/512/com.vitorpamplona.amethyst.png`: 512x512 icon (copy of
  `desktopApp/src/jvmMain/resources/icon.png`).

Validate after editing:

```bash
appstreamcli validate --pedantic com.vitorpamplona.amethyst.metainfo.xml
desktop-file-validate com.vitorpamplona.amethyst.desktop
```

Both pass cleanly. Flathub's own linter (`flatpak-builder-lint` from the
`org.flatpak.Builder` app) also passes on the manifest; on the built repo it
reports only the missing screenshot:

```bash
flatpak install --user flathub org.flatpak.Builder
flatpak run --command=flatpak-builder-lint org.flatpak.Builder manifest com.vitorpamplona.amethyst.yml
flatpak run --command=flatpak-builder-lint org.flatpak.Builder repo <path-to>/repo
```

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
  build-dir com.vitorpamplona.amethyst.yml
flatpak run com.vitorpamplona.amethyst
```

To produce the distributable single-file bundle instead (what CI ships):

```bash
flatpak-builder --user --install-deps-from=flathub --force-clean \
  --repo=repo build-dir com.vitorpamplona.amethyst.yml
flatpak build-bundle repo amethyst.flatpak com.vitorpamplona.amethyst \
  --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo
```

Installing the bundle: `flatpak install --user ./amethyst.flatpak`. The
`--runtime-repo` baked in above lets flatpak fetch the freedesktop runtime
from Flathub automatically on the user's machine.

## Runtime

`org.freedesktop.Platform` **26.08**. Each freedesktop runtime gets about two
years of support (a new one ships every August), so bump `runtime-version`
yearly. CI reads the version from the manifest when it installs the runtime.

Codecs: since 25.08 the runtime declares `org.freedesktop.Platform.codecs-extra`
(the successor of `ffmpeg-full`: H.264/H.265 and friends) and flatpak installs
it with the runtime, so the manifest declares no codec extension. Don't set
`GST_PLUGIN_SYSTEM_PATH` in `finish-args`: the runtime's own value already
lists the codecs-extra plugin directory, and overriding it hides those plugins.

## Sandbox notes

- `--socket=x11` (not wayland/fallback-x11): Compose Desktop renders through
  AWT/skiko, which is X11-only on Linux and runs under XWayland on Wayland
  sessions.
- File access is limited to Downloads and Pictures. AWT's `FileDialog` calls
  the GTK file chooser directly rather than the file chooser portal, so it can
  only browse what the sandbox can see.
- `--talk-name=org.freedesktop.secrets`: `java-keyring` stores the account keys
  through the Secret Service API.
- `--talk-name=org.freedesktop.Notifications`: `NucleusNotificationDispatcher`
  sends desktop notifications over D-Bus through `nucleus.notification-linux`
  (AWT's tray is only its fallback).
- `--persist=.amethyst` and `--persist=.java`: the app writes accounts,
  drafts, scheduled posts and the local relay under `~/.amethyst`, and Java
  Preferences (relay lists, search history) under `~/.java`. Without home
  access, writes there are lost when the sandbox exits; `--persist` keeps them
  under `~/.var/app/com.vitorpamplona.amethyst/`. Tor state and the image
  cache already follow `XDG_DATA_HOME`/`XDG_CACHE_HOME`.
- Scheduled posts only publish while the app is open. `OsScheduler` registers
  a systemd user timer (or crontab entry) to publish while the app is closed,
  and neither can be reached from the sandbox.
- The "now playing" reader (`MprisNowPlayingReader`) queries other players
  over the session bus with `dbus-send`. The sandbox grants no
  `org.mpris.MediaPlayer2.*` access, so it finds nothing in the Flatpak.

## Flathub

Amethyst has not been submitted. These are the gaps against Flathub's
[requirements](https://docs.flathub.org/docs/for-app-authors/requirements),
checked 2026-10-09:

1. **Generative AI policy.** Flathub manifests "must not contain AI-generated
   or AI-assisted content", AI tools "must not open or automate Flathub
   submission pull requests, or generate their commit messages, descriptions,
   review comments, or replies", and all other AI-generated code,
   documentation or packaging must be disclosed. The files in this directory
   were AI-assisted, so a human must write the Flathub manifest, open the
   submission PR and answer the review. Reviewers may also reject a
   submission based on how much of the app itself is AI-generated. An earlier,
   AI-written `flathub/` submission directory was removed for this reason.
2. **Build from source.** "All source available submissions must be built
   entirely from source code", offline, with every dependency listed as a
   manifest source. Repackaging the GitHub Release tarball, as the manifest
   here does, is not accepted.

   The Gradle side already holds up. On 2026-10-09,
   `./gradlew :desktopApp:createReleaseDistributable` built from scratch
   (`--rerun-tasks --no-build-cache`) with no Android SDK (no `local.properties`,
   no `ANDROID_HOME`). It then ran again with `--offline` and an empty
   `KONAN_DATA_DIR`, and fetched no Kotlin/Native toolchain. The AGP
   multiplatform plugin and the iOS/Linux native targets don't get in the way
   of JVM-only tasks. What a source manifest still has to provide:
   - every Maven artifact the build resolves, plugins included, as manifest
     sources (generated with Flatpak's Gradle tooling, which Flathub allows as
     generated files), plus an init script that points the repositories in
     `settings.gradle.kts` at that local copy;
   - the Gradle distribution itself, since the wrapper downloads it;
   - a JDK from `org.freedesktop.Sdk.Extension.openjdk21` to run Gradle and
     jpackage. The jpackage tree then bundles a trimmed JRE as it does today.

   Some Maven dependencies ship prebuilt Linux binaries inside their jars,
   and a reviewer may ask for those to be built from source too. In the
   v1.17 desktop distributable: `resource-exec-tor` (a `tor` executable),
   `secp256k1-kmp-jni-jvm-linux`, `jna`, `sqlite-bundled-jvm`,
   `mediaplayer-jvm`, `nucleus.notification-linux` and Skiko's runtime.
3. **Screenshot.** The metainfo needs at least one screenshot of the Linux
   app (see the commented-out `<screenshots>` block).
4. **Domain verification.** `com.vitorpamplona.amethyst` is verified by
   serving the token Flathub provides at
   `https://vitorpamplona.com/.well-known/org.flathub.VerifiedApps.txt`.
5. **Latest runtime.** Flathub requires the newest runtime at submission time.

Once accepted, Flathub creates `flathub/com.vitorpamplona.amethyst`, and
updates are PRs there.

## License metadata

The metainfo declares
`MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0 AND GPL-2.0-only WITH Classpath-exception-2.0`:
the expression from `rpmLicenseType` in `desktopApp/build.gradle.kts` plus the
bundled OpenJDK runtime. The manifest installs the repository `LICENSE` to
`/app/share/licenses/com.vitorpamplona.amethyst/`. The JRE's own notices ship
inside the jpackage tree under `lib/runtime/legal/`.
