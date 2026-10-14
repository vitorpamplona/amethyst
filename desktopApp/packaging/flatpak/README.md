# Flatpak packaging for Amethyst Desktop

App ID: **`com.vitorpamplona.amethyst.Amethyst`**. Earlier bundles used two
other IDs:

- Up to v1.17.0: `com.vitorpamplona.amethyst.Desktop`. Flathub forbids IDs that
  end in generic terms like `.desktop`, `.app` or `.linux` ("it's fine to repeat
  the application name in such cases", hence `.Amethyst`).
- v1.18.0: `com.vitorpamplona.amethyst`, the Android package name. Nostr app
  stores (NIP-82) key an app's listing on its native ID: Zapstore lists the
  Android app under `com.vitorpamplona.amethyst`, and Orchard, a Flatpak store,
  would list the Flatpak under its app ID. Both listings are signed by the same
  key, so one shared ID would make each store's publish overwrite the other's
  listing.

`BUILDING.md` § Uninstall + state paths explains how users move their data to
the new ID.

Release CI (`.github/workflows/create-release.yml`, `linux-portable` leg)
builds a single-file bundle from this directory on every tag and attaches it to
the GitHub Release as `amethyst-desktop-<version>-linux-<x64|arm64>.flatpak`.

**Amethyst is not on Flathub.** The manifest here repackages a prebuilt tree,
which Flathub does not accept; [`flathub/`](flathub/) has the source build. See
[Flathub](#flathub) below.

## Files

- `com.vitorpamplona.amethyst.Amethyst.yml`: Flatpak manifest for the GitHub
  Release bundle. It packages the **prebuilt** jpackage tree from
  `./gradlew :desktopApp:createReleaseDistributable`, which bundles its own
  trimmed JRE (hence no openjdk extension). Build that tree on the JetBrains
  Runtime (see [Sandbox notes](#sandbox-notes)), or the bundle can't draw on
  Wayland.
- `com.vitorpamplona.amethyst.Amethyst.metainfo.xml`: AppStream metadata. It
  keeps the release history: add an entry when tagging (`RELEASE_OPS.md` § Pre-tag
  checklist). CI injects a bare entry for the version it builds if one is
  missing.
- `com.vitorpamplona.amethyst.Amethyst.desktop`: XDG desktop entry.
- `icons/512/com.vitorpamplona.amethyst.Amethyst.png`: 512x512 icon (copy of
  `desktopApp/src/jvmMain/resources/icon.png`).

Validate after editing:

```bash
appstreamcli validate --pedantic com.vitorpamplona.amethyst.Amethyst.metainfo.xml
desktop-file-validate com.vitorpamplona.amethyst.Amethyst.desktop
```

Both pass. `--pedantic` prints one hint, `cid-contains-uppercase-letter`: the
`.Amethyst` component is capitalised, which Flathub's ID rules allow. Flathub's
own linter (`flatpak-builder-lint` from the `org.flatpak.Builder` app) passed
on the manifest under the previous ID and has not been re-run since the rename;
on the built repo it reported only the missing screenshot:

```bash
flatpak install --user flathub org.flatpak.Builder
flatpak run --command=flatpak-builder-lint org.flatpak.Builder manifest com.vitorpamplona.amethyst.Amethyst.yml
flatpak run --command=flatpak-builder-lint org.flatpak.Builder repo <path-to>/repo
```

## Local build

```bash
# 1. Build the app tree the manifest packages, on the JetBrains Runtime 21 (jbrsdk)
JAVA_HOME=/path/to/jbrsdk-21 ./gradlew :desktopApp:createReleaseDistributable

# 2. Tooling + Flathub remote (one-time)
sudo apt-get install -y flatpak flatpak-builder   # or distro equivalent
flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo

# 3. Build + install locally
cd desktopApp/packaging/flatpak
flatpak-builder --user --install --install-deps-from=flathub --force-clean \
  build-dir com.vitorpamplona.amethyst.Amethyst.yml
flatpak run com.vitorpamplona.amethyst.Amethyst
```

To produce the distributable single-file bundle instead (what CI ships):

```bash
flatpak-builder --user --install-deps-from=flathub --force-clean \
  --repo=repo build-dir com.vitorpamplona.amethyst.Amethyst.yml
flatpak build-bundle repo amethyst.flatpak com.vitorpamplona.amethyst.Amethyst \
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

- `--socket=wayland` + `--socket=fallback-x11`: the app draws natively on
  Wayland and gets X11 only on X11 sessions. This needs the bundled runtime to
  be the JetBrains Runtime (its AWT has a Wayland toolkit, `WLToolkit`; stock
  OpenJDK's AWT is X11-only), which release CI builds the Linux legs on. Skiko's
  window surface needs X11, so on Wayland the app draws through Swing with
  Skiko's CPU renderer (`desktopApp/.../platform/WaylandDisplay.kt`,
  `AppWindow.kt`). `flatpak run --env=AMETHYST_X11=1 --socket=x11
  com.vitorpamplona.amethyst.Amethyst` puts it back on XWayland.
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
  under `~/.var/app/com.vitorpamplona.amethyst.Amethyst/`. Tor state and the
  image cache already follow `XDG_DATA_HOME`/`XDG_CACHE_HOME`.
- Scheduled posts only publish while the app is open. `OsScheduler` registers
  a systemd user timer (or crontab entry) to publish while the app is closed,
  and neither can be reached from the sandbox.
- The "now playing" reader (`MprisNowPlayingReader`) queries other players
  over the session bus with `dbus-send`. The sandbox grants no
  `org.mpris.MediaPlayer2.*` access, so it finds nothing in the Flatpak.

## Flathub

Amethyst has not been submitted yet. [`flathub/`](flathub/) holds a manifest
that builds from source as Flathub requires, and the tools that keep it current.
Status against Flathub's
[requirements](https://docs.flathub.org/docs/for-app-authors/requirements):

1. **Build from source: done.** `flathub/com.vitorpamplona.amethyst.Amethyst.yml`
   compiles the release tag offline with `:desktopApp:createReleaseDistributable`.
   Its pinned sources are the tag's tarball, the JetBrains Runtime SDK 21 (it runs
   Gradle and jpackage, so the bundled runtime keeps JBR's Wayland toolkit), the
   Gradle distribution, and `gradle-sources.json`: every Maven file the build
   resolves, for x86_64 and aarch64. `flathub-offline.init.gradle` replaces every
   repository with that local copy. Flathub already ships a prebuilt JBR and
   Skiko's natives in `com.prof18.feedflow`, a Compose Desktop app built the same
   way.

   Checked on aarch64 with `flatpak-builder` (network-less build, installs,
   `flatpak run` reaches the login screen, bundled runtime has
   `libawt_wlawt.so`); x86_64's dependency list was captured but not built
   through `flatpak-builder`. `flatpak-builder-lint` passes the manifest and the
   metainfo. On the build it reports only `appstream-external-screenshot-url`
   and `appstream-remote-icon-not-mirrored`: Debian's AppStream 1.0.5 writes
   media paths relative to `media_baseurl`, while the linter expects the full
   `https://dl.flathub.org/media` URLs Flathub's own builder writes.
   Flathub's builder (`org.flatpak.Builder`) could not run nested in Docker, so
   that was not checked end to end.

   Every release: `flathub/tools/regenerate-gradle-sources.sh vX.Y.Z` (about
   35 minutes, both arches in Docker) rewrites `gradle-sources.json` from a real
   online build and prints the tarball's sha256 for the manifest. Flathub's
   update bot can't regenerate that list, so each release is a manual PR to the
   Flathub app repo.

   Some Maven dependencies ship prebuilt Linux binaries inside their jars (JNA,
   secp256k1, SQLite, the media player, notifications, Skiko, and a `tor`
   executable in kmp-tor). FeedFlow's acceptance covers the Skiko kind; a
   reviewer may still ask about the `tor` executable.
2. **Generative AI policy: not met.** Flathub manifests "must not contain
   AI-generated or AI-assisted content", AI tools "must not open or automate
   Flathub submission pull requests, or generate their commit messages,
   descriptions, review comments, or replies", and all other AI-generated
   code, documentation or packaging must be disclosed. The `flathub/` files
   were written with Claude Code (the manifest says so), so the submission
   must disclose it and reviewers may decline; a person opens the submission
   PR and answers the review.
3. **Screenshot: done.** The metainfo lists `docs/screenshots/desktop-home.png`,
   the 1.19.0 Linux build's home feed, from a commit-pinned URL. It landed
   after `v1.19.0`, so submit with the next tag: the `v1.19.0` metainfo has none.
4. **Domain verification.** `com.vitorpamplona.amethyst.Amethyst` is verified by
   serving the token Flathub provides at
   `https://vitorpamplona.com/.well-known/org.flathub.VerifiedApps.txt`.
5. **Latest runtime: done.** 26.08 is the newest freedesktop runtime.

Once accepted, Flathub creates `flathub/com.vitorpamplona.amethyst.Amethyst`;
copy the manifest, the init script and `gradle-sources.json` there, and each
update is a PR to it.

## License metadata

The metainfo declares
`MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0 AND GPL-2.0-only WITH Classpath-exception-2.0`:
the expression from `rpmLicenseType` in `desktopApp/build.gradle.kts` plus the
bundled OpenJDK runtime. The manifest installs the repository `LICENSE` to
`/app/share/licenses/com.vitorpamplona.amethyst.Amethyst/`. The JRE's own notices ship
inside the jpackage tree under `lib/runtime/legal/`.
