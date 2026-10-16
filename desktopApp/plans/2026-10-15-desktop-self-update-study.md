# Desktop self-update: study

Status: **built (phases 1 to 5), not yet tried on a real macOS or Windows machine.** Written
2026-10-15; see [Implementation](#implementation) for what was built, how it was checked and what is
left.

Question: when someone installs Amethyst Desktop from a file on the GitHub
Release page (not from Homebrew, winget, Flathub or Orchard), how can the app
update itself on macOS, Windows and Linux?

## TL;DR

1. **Don't build one updater. Build one update check and one installer per
   artifact.** We ship nine desktop artifacts, and each one updates differently.
   A shared headless checker in `commons` finds the new version. A small
   per-format installer in `desktopApp` applies it.
2. **Never fight a package manager.** If Homebrew, winget, apt/dnf or Flatpak
   owns the install, the app only tells the user an update exists and how to
   get it. Self-replacement is only for installs nobody else manages: the DMG,
   the MSI, the Windows zip, the AppImage and the Linux tarball.
3. **The trust anchor should be a Nostr event, not GitHub.** Amethyst already
   parses NIP-82 software releases (`quartz/…/experimental/nip82SoftwareApps/`).
   A kind 3063 asset event signed by Amethyst's key carries the asset's URL,
   SHA-256, size, version and platform (`f` tag). The app pins that pubkey,
   verifies the signature, downloads from GitHub and checks the hash. A
   compromised GitHub release then can't ship a binary the app will install.
4. **Do it in-house. No framework fits well.** Conveyor would replace our whole
   packaging pipeline. Sparkle and WinSparkle are native libraries a JVM app
   would have to bind. Velopack has no JVM SDK we could confirm. The in-house
   installers are each about 100–200 lines (download, verify, swap, relaunch).
5. **Suggested order:** notify-only for everyone → AppImage + Linux tarball +
   Windows zip (rename-swap, no privileges) → MSI (UAC) → DMG → signed apt/dnf
   repos for the .deb/.rpm.

## What ships today

From `.github/workflows/create-release.yml`, `scripts/asset-name.sh` and
`desktopApp/build.gradle.kts`:

| Artifact | Archs | Installs to | Signed? | Who updates it today |
|---|---|---|---|---|
| `.dmg` | macOS arm64 only | `/Applications/Amethyst.app` (user drags) | Developer ID + notarized + stapled | Homebrew cask `amethyst-nostr`, or nobody |
| `.msi` | Windows x64 only | `C:\Program Files\Amethyst` (per-machine; winget manifest says `Scope: machine`) | **No Authenticode** | `winget upgrade`, or nobody |
| `.zip` | Windows x64 + arm64 | wherever the user unzips it | No | nobody (**the only Windows arm64 option**) |
| `.deb` | Linux x64 + arm64 | `/opt/amethyst` (root) | No repo signature | nobody (no apt repo) |
| `.rpm` | Linux x64 + arm64 | `/opt/amethyst` (root) | No repo signature | nobody (no dnf repo) |
| `.AppImage` | x86_64 + aarch64 | one file, anywhere | No | nobody (no update info embedded: `createReleaseAppImage` calls `appimagetool` without `-u`) |
| `.tar.gz` | Linux x64 + arm64 | wherever the user extracts it | No | nobody |
| `.flatpak` bundle | Linux x64 + arm64 | Flatpak user/system install | No | nobody (built with `--runtime-repo` only, no `--repo-url`) |
| Orchard Flatpak | Linux x64 + arm64 | Flatpak | NIP-82 listing signed with Amethyst's nsec | Orchard |

Other facts that matter:

- **User data lives outside the install.** Accounts and keys are in
  `~/.amethyst`, caches and Tor state in the OS cache and app-support folders
  (see the `zap` list in `packaging/homebrew/amethyst-nostr.rb`). Replacing the
  install tree loses nothing.
- **The app knows where its launcher is.** jpackage sets the
  `jpackage.app-path` system property to the absolute launcher path.
  `OsScheduler.resolveAppLaunchCommand()` already uses it. It is null under
  `./gradlew run`, which is how a dev build stays out of the updater.
- **The app knows its version.** `-Damethyst.version=$appVersion` is passed to
  the JVM. `Nip82VersionComparator` already compares NIP-82 versions.
- **The MSI upgrade path already works.** `upgradeUuid` is fixed in
  `build.gradle.kts`, and winget's `UpgradeBehavior: install` runs the new MSI
  over the old one. An in-app MSI update takes the same path.
- **Every package is large.** Each one ships a jlink'd JRE, Skiko, FFmpeg and
  Tor. Only zsync (AppImage) gives cheap delta updates. Everything else
  downloads the full package.

## Rule zero: detect the install channel

Run this at startup, from `desktopApp`. It returns a sealed `InstallChannel`.

| Signal | Channel | What the app does |
|---|---|---|
| `jpackage.app-path` is null | Dev build | No update checks |
| `FLATPAK_ID` set, or `/.flatpak-info` exists | Flatpak (bundle or Orchard) | Portal update (see Linux) or notify only |
| `APPIMAGE` set (set by the AppImage runtime) | AppImage | Self-replace |
| macOS, and a link under `/opt/homebrew/Caskroom/amethyst-nostr/<version>/` (or `/usr/local/Caskroom/…`) leads to this bundle | Homebrew cask | Notify: "run `brew upgrade --cask amethyst-nostr`" |
| macOS, bundle path contains `/AppTranslocation/` or starts with `/Volumes/` | Translocated / running from the DMG | Tell the user to move the app to `/Applications`. Updating here can't work: the path is read-only and random. |
| macOS, anything else | DMG install | Self-replace |
| Windows, launcher under `%ProgramFiles%` and the MSI UpgradeCode is registered | MSI (direct or winget: same MSI, same ARP entry) | Run the new MSI |
| Windows, anything else | Portable zip | Self-replace |
| Linux, launcher under `/opt/amethyst/` and `dpkg-query -S` / `rpm -qf` owns it | .deb / .rpm | Notify, or hand off to PackageKit |
| Linux, anything else | Tarball | Self-replace |

On Windows, a winget install and a direct MSI install can't be told apart, and
it doesn't matter: winget reads the installed version from the same ARP
(Add/Remove Programs) entry, so it stays in sync whoever ran the new MSI.

Homebrew is the exception. If the app replaces itself, brew's record goes
stale. Many casks do this and declare `auto_updates true`, but our cask is
deliberately bare (upstream maintainers removed extras). So: notify only.

Every channel gets a user setting: "Check for updates" (on/off), plus "Install
automatically" wherever self-replace exists. Respect it before any network call.

## Where the "new version" signal comes from

### Option A: GitHub Releases API

`GET https://api.github.com/repos/vitorpamplona/amethyst/releases/latest`.
That gives the tag and the asset URLs. The asset name is predictable from
`scripts/asset-name.sh`. Unauthenticated requests allow 60 per hour per IP,
which is plenty.

- Pros: zero release-process change.
- Cons: trust rests entirely on TLS + the GitHub account. A leaked token or a
  hijacked workflow could swap an asset. Every check also leaks the user's IP
  to GitHub.

### Option B: NIP-82 events signed by Amethyst's key (recommended)

The release process publishes, per desktop asset, a kind 3063
`SoftwareAssetEvent` with:

- `i` (the desktop app id)
- `url` (the GitHub download URL)
- `x` (SHA-256)
- `size`
- `version`
- `f` platform (`darwin-arm64`, `windows-x86_64`, `windows-aarch64`,
  `linux-x86_64`, `linux-aarch64`)
- `m` mime type (to tell the MSI from the zip, and the deb from the AppImage)

A kind 30063 release references the assets, and the kind 32267 application
ties them together. Quartz already models all three:
`SoftwareApplicationEvent`, `ReleaseArtifactSetEvent` (kind 30063;
`SoftwareReleaseEvent` is a deprecated alias for it) and `SoftwareAssetEvent`.
`PlatformTag` has exactly these platform constants. See the next sections for
how many events that is and how existing stores read them.

The app:

1. Fetches the newest assets for its own platform from the relays it already
   talks to, filtered on the relay with `#i` (app id) + `#f` (platform). It
   uses `fetchFirst`/`fetchAll` from `nip01Core/relay/client/accessories`. Tor
   routing then comes for free when the user has Tor enabled.
2. Picks the asset whose `m` (or `variant`) matches how it was installed (MSI,
   zip, AppImage…).
3. Checks that the author equals a **pubkey pinned in the build**.
4. Verifies the Schnorr signature (Quartz already does this for every event).
5. Downloads from `url` and refuses anything whose SHA-256 or size differs.

Pros:

- End-to-end integrity from the maintainer's key, not GitHub's.
- No new crypto, and no second signing key (Sparkle would need an extra EdDSA
  key, for example).
- Privacy: no GitHub call until the user actually updates.
- The same events make the desktop builds visible in the in-app Software Apps
  screen and in Zapstore-style stores.

Cons / open points:

- **Signing in CI vs by hand.** The nsec currently signs by hand (`zsp
  publish`, `orchard-publish`; see `RELEASE_OPS.md`). Options:
  - a maintainer step after the release (`amy` could gain a `publish-release`
    verb that hashes the published assets and signs);
  - a NIP-46 bunker that CI asks to sign;
  - a separate "release signer" key that the app pins in addition to the main
    key, so the main nsec never touches CI.
- **App-id collisions.** Zapstore owns `com.vitorpamplona.amethyst` and Orchard
  owns `com.vitorpamplona.amethyst.Amethyst`, both signed by the same key. The
  desktop listing needs a third id; see [Event budget](#event-budget-per-release).
- **Downgrade and freeze protection.** The app refuses a version lower than its
  own, and refuses an event older than the newest one it has accepted. Relays
  can withhold events (a freeze), but they can't forge one.

**Recommended:** B is the source of truth for automatic installs. A is the
fallback for a "new version available" banner only, never for installing
unattended.

### Event budget per release

Recommended app id: `com.vitorpamplona.amethyst.desktop` (the macOS
`bundleID`).

| Event | How many | When |
|---|---|---|
| 32267 listing | 1 | Once; re-sign only when the listing text or icon changes |
| 30063 release `com.vitorpamplona.amethyst.desktop@X.Y.Z` | 1 | Every release |
| 3063 assets | 8 (12 with deb/rpm) | Every release |

The 8 assets are the files the self-updater can install:

| Platform (`f`) | Files |
|---|---|
| `darwin-arm64` | DMG |
| `windows-x86_64` | MSI, zip |
| `windows-aarch64` | zip |
| `linux-x86_64` | AppImage, tar.gz |
| `linux-aarch64` | AppImage, tar.gz |

The .deb and .rpm for both architectures add 4 more. They're only worth
publishing for the PackageKit hand-off, which needs a verified hash; if those
installs stay notify-only, they can be left out. The GitHub Flatpak bundles stay
notify-only too, and Orchard already publishes signed events for Flatpak users.

So a release is 9 events (13 with deb/rpm), against Orchard's 3. All the hashes
come from the `dist/` folder the release workflow already builds, so one
command (e.g. an `amy` command that reads `dist/` and the tag) can build and
sign every event at once.

### Why not fewer events

**Not one release event with the hashes inline, without asset events.** The
release already works as a single signed manifest: it points to each asset by
event id (`AssetTag` is an `e` tag holding the 64-character id), and an event id
is the hash of the event's content. One signature on the release therefore fixes
every asset event, including each file's SHA-256; swapping an asset changes its
id and breaks the release signature. Inlining would save one relay round trip
and take the release off NIP-82, so Zapstore-style clients and our own Software
Apps screen could no longer read it.

**Not one app id for the APK, the Flatpak and desktop.** NIP-82 allows one
release to carry assets for every platform, but:

- `zsp` and `orchard-publish` each sign a 30063 with only their own files.
  Under a shared id, whichever publishes last replaces the others' file list
  (Orchard's per-arch address, below, avoids that only for its own two
  architectures). Sharing means retiring both tools for our own publisher.
- The Flatpak id was moved off `com.vitorpamplona.amethyst` in v1.18.0 for
  exactly this reason (`desktopApp/packaging/flatpak/README.md`).
- Zapstore's detail screen would pick up non-Android assets (below).

### How the existing stores filter

NIP-82 has no "type of release". Stores filter by the platform tag `f`, which
all three kinds carry: the listing (every platform the app supports), the
release (the union of its assets' platforms, which NIP-82 requires and
`buildSoftwareRelease` computes), and each asset (its own platform). Relays
index single-letter tags, so `#f` can be filtered on the relay. Where one
platform has several formats, the asset's `m` tag (MIME type) or `variant` tells
them apart.

Checked against the source on 2026-10-15: Zapstore's default branch (last
commit 2026-08-27) with the `purplebase/models` library it uses, and Orchard's
(last commit 2026-10-15).

**Zapstore filters on the relay and reads assets directly.**

- The platform is hard-coded: every package manager returns
  `'android-arm64-v8a'` (`android_package_manager.dart:89`).
- Catalog and update queries send it to the relay: listings with
  `{'#d': ids, '#f': {platform}}`, assets with
  `{'#i': {appId}, '#f': {platform}}, limit: 1` (`catalog_fetcher.dart`).
- It works asset-first. For current-format apps it skips the 30063 release and
  takes the newest 3063 for the app id and platform. The release chain is only
  used for old apps still on kind 1063.
- It never reads `m` or `variant`.
- **Gap:** the app detail screen loads `latestAsset` with only the author and
  `#i`, no platform, limit 1 (`models/lib/src/models/app.dart:40`). A
  non-Android asset under the APK's app id could show there as the latest
  build. This is why desktop must not share the APK's id.

**Orchard downloads everything and filters on the device.**

- It subscribes to all 32267, 30063 and 3063 events with no tag filter
  (`orchard-nostr/src/relay.rs:141`).
- It drops listings whose platform tags include no `linux-*`, an explicit guard
  against Zapstore's Android listings.
- It already expects mixed releases. From `orchard-nostr/src/lib.rs:185`: "A
  release references every format the publisher ships (apk, exe, AppImage), all
  sharing the linux platform tags, so the kind, not just the platform, decides
  installability." An asset is installable only with an `oci:`/`docker://`
  location, `m = application/vnd.flatpak`, or a `.flatpak` filename, and then
  only on an exact host-platform match (falling back to assets with no `f`).
- It accepts stable channels only: no channel tag, `stable`, or `main`
  (Zapstore's default name).
- `orchard-publish` addresses releases as `<app-id>@<version>@<platform>`, one
  per architecture, so its two runs don't overwrite each other. NIP-82 says
  `<app-id>@<version>`; Orchard's reader accepts both.

**What our events must carry, as a result:**

- `i` and `f` on every asset (what Zapstore filters on), and `m` (what Orchard
  keys on and what our updater needs). `SoftwareAssetEvent.build` already
  requires the app id and MIME type; the platform tag is the one to remember.
- A `c` channel tag of `stable` on stable releases.
- One release holding all assets, or one per platform like Orchard: both are
  valid. One release keeps everything under one signature and is simpler.

Our updater should read the same way Zapstore does (`#i` + `#f` on the relay,
then `m` on the device). The release event stays useful for release notes and
the single signature over all assets.

## Per-OS mechanics

All the self-replace paths share one shape:

1. **Check:** at startup + every ~12 h, with a jittered delay. Never on the
   first launch.
2. **Ask:** banner or notification, "Amethyst X.Y.Z is ready", with "Update
   now" / "Later" / release notes.
3. **Download to staging:** a sibling of the install
   (`<install>.new` / `<install>.download`) so the final rename stays on one
   filesystem and is atomic.
4. **Verify:** hash + size from the signed manifest, plus the platform
   signature where one exists.
5. **Apply on restart:** spawn a detached helper that waits for our PID to
   exit, swaps, relaunches `jpackage.app-path`, and cleans up. Keep the old
   copy until the new one starts (rollback).

The helper is a generated shell script on macOS/Linux and a PowerShell script
on Windows, written to the cache dir. That avoids shipping a second binary.

### macOS: DMG install (arm64)

**In-house, recommended:**

1. Download the DMG, check the hash.
2. Mount it: `hdiutil attach -nobrowse -readonly -mountpoint <tmp> <dmg>`.
3. Run `codesign --verify --deep --strict <tmp>/Amethyst.app`. Compare its
   `TeamIdentifier` (from `codesign -dv`) with the running bundle's.
   `spctl --assess --type execute` confirms notarization.
4. Run `ditto <tmp>/Amethyst.app <parent>/Amethyst.app.new`, then detach.
5. Helper: wait for the PID, then
   `mv Amethyst.app Amethyst.app.old && mv Amethyst.app.new Amethyst.app`,
   then `open Amethyst.app`, then `rm -rf Amethyst.app.old`.

Traps:

- **Write access.** `/Applications` is writable by admin-group users, not by
  standard users. If `<parent>` isn't writable, fall back: download, verify,
  and `open` the DMG for the user to drag across. An elevated copy through
  `osascript … with administrator privileges` is possible but heavy-handed.
- **Translocation.** An app launched from `~/Downloads` with the quarantine
  attribute gets moved to a random read-only path, and the original location
  can't be found
  ([Eclectic Light](https://eclecticlight.co/2023/05/09/what-causes-app-translocation)).
  Detect it and ask the user to move the app; don't try to update.
- **Never patch inside the bundle.** Replacing a jar in
  `Contents/app/` breaks the code-signature seal and Gatekeeper will refuse
  the app. That rules out jar-only updates (update4j-style) on macOS. Replace
  the whole `.app`.
- **Quarantine.** A file written by the JVM's HTTP client carries no
  `com.apple.quarantine` attribute. Only LSFileQuarantine-aware apps add it, so
  the copied bundle isn't translocated on its next launch. *To verify in
  phase 0.*
- **Intel Macs.** The release matrix builds arm64 only, so there is nothing to
  offer them.

**Sparkle 2 (alternative).** MIT license (checked the `LICENSE` on the `2.x`
branch). It is the standard macOS updater: EdDSA-signed appcast, DMG/zip
installs, an installer helper that handles authorization and translocation,
and delta updates. Cost for us:

- Embed `Sparkle.framework` (with its XPC services) in
  `Contents/Frameworks`. jpackage/Compose don't do that. It needs a step
  between `createDistributable` and signing, and every nested binary must be
  signed for notarization.
- Drive `SPUStandardUpdaterController` from the JVM through JNA / the ObjC
  runtime.
- Keep a second signing key (EdDSA) and an RSS appcast next to the NIP-82
  events.

Worth it only if the in-house path hits permission walls we can't cross.

### Windows: MSI install (x64)

1. Download the MSI, check the hash.
2. Helper (PowerShell): wait for the PID, then
   `Start-Process msiexec -Verb RunAs -ArgumentList '/i', '<msi>', '/passive', '/norestart' -Wait`,
   then relaunch `C:\Program Files\Amethyst\Amethyst.exe`. The `RunAs` verb
   triggers UAC because the MSI is per-machine.
3. The fixed `upgradeUuid` makes it a major upgrade: Windows Installer removes
   the old version and installs the new one. winget already runs exactly this
   step.

Traps and recommendations:

- **Unsigned MSI.** The UAC prompt says "Unknown publisher". Integrity is still
  covered by the signed manifest, but users see a scary dialog on every
  update. **Recommend Authenticode signing in the release workflow.** Azure
  Trusted Signing and SignPath's free OSS tier are the usual options. With a
  signature, the app can also check `Get-AuthenticodeSignature` before running
  the MSI.
- **SmartScreen.** A file the app writes itself has no Mark-of-the-Web
  (`Zone.Identifier` stream), so SmartScreen shouldn't intervene. *To verify in
  phase 0.*
- **Per-user install.** jpackage's `--win-per-user-install` would make the MSI
  install to `%LOCALAPPDATA%` and update with no UAC. But Windows Installer
  can't major-upgrade across per-machine and per-user contexts, so existing
  users would end up with two installs, and the winget manifest's `Scope`
  would change. Not worth it alone. Maybe revisit with signing.
- **Toasts.** The Start Menu shortcut carries the AUMID that toasts need, and
  the MSI recreates it on upgrade. Nothing to do.

**WinSparkle (alternative).** MIT (checked `COPYING`). It downloads and runs
the installer with its own native UI. It would replace step 1–2 with a DLL
bound through JNA, but its UI isn't our Compose UI. Little gain.

### Windows: portable zip (x64 + arm64)

This is the only Windows arm64 artifact, so it has to work.

1. Download the zip, check the hash, extract to `<parent>\Amethyst.new`.
2. Helper: wait for the PID. Windows locks the running `.exe`, loaded DLLs and
   jars the JVM has open, so nothing can be swapped before we exit. Then rename
   `Amethyst` → `Amethyst.old` and `Amethyst.new` → `Amethyst`, launch, delete
   `.old`. Retry the rename a few times: antivirus scanners briefly hold
   freshly extracted files.
3. If the parent dir isn't writable (e.g. unzipped into `Program Files` by
   hand), fall back to notify + open the download page.

### Linux: AppImage (x86_64 + aarch64)

**In-app (recommended):**

1. Download the new AppImage to `$APPIMAGE.new` (same dir), check the hash,
   `chmod +x`.
2. `rename($APPIMAGE.new, $APPIMAGE)`. This is safe while running: the running
   instance's squashfs mount holds the old inode, and the rename is atomic.
3. Relaunch via `$APPIMAGE` and exit. No helper process needed.

Keeping the old filename means it shows a stale version string, but it keeps
the user's desktop shortcuts (and AppImageLauncher's integration) pointing at
the right file.

**Also add zsync update info**, which is nearly free:

- Pass
  `-u "gh-releases-zsync|vitorpamplona|amethyst|latest|amethyst-desktop-*-x86_64.AppImage.zsync"`
  (and the aarch64 equivalent) to `appimagetool` in `createReleaseAppImage`.
  It writes the `.zsync` file next to the AppImage; upload that too.
- External updaters can then do delta updates without the app's help:
  `appimageupdatetool`, AppImageLauncher, Gear Lever. AppImageUpdate is MIT
  (checked `LICENSE.txt`).
  [AppImage docs](https://docs.appimage.org/packaging-guide/optional/updates.html).
- Caveat: `latest` means GitHub's latest *non-prerelease*, which is what we
  want.

### Linux: tarball (x64 + arm64)

Same as the Windows zip: extract to a sibling dir, swap, relaunch. Linux
lets us rename the directory while running (open jars stay valid through their
file descriptors), but restart right after the swap: classes that load later
come from the new jars.

### Linux: .deb / .rpm (x64 + arm64)

The app must not rewrite root-owned `/opt/amethyst` itself. Two honest paths:

1. **Host signed apt + dnf repositories (recommended long-term).** The .deb's
   postinst drops `/etc/apt/sources.list.d/amethyst.list` and a keyring in
   `/usr/share/keyrings/`; the .rpm drops a `/etc/yum.repos.d/amethyst.repo`.
   Chrome, VS Code and Signal do this. From then on unattended-upgrades, GNOME
   Software, Discover or `dnf upgrade` keep the app current, with zero updater
   code in the app. Costs:
   - a repo host: GitHub Pages works for a small apt/dnf repo; Cloudsmith and
     packagecloud have OSS tiers;
   - a GPG key in CI;
   - more postinst editing in `scripts/fix-deb-*.sh` and in `rpmSpec()` in
     `build.gradle.kts`.
2. **Short-term.** Notify, then either open the download, or call PackageKit's
   D-Bus `InstallFiles` on the verified `.deb`/`.rpm`. PackageKit shows the
   polkit password prompt and works on both apt and dnf systems. If
   PackageKit is missing, fall back to `xdg-open` on the file (the software
   center installs it).

### Linux: Flatpak bundle

- An installed bundle has no update source today. Rebuilding it with
  `flatpak build-bundle --repo-url=<ostree repo> --gpg-keys=<key>` makes
  installing it "automatically configure a remote for this URL"
  ([flatpak command reference](https://docs.flatpak.org/en/latest/flatpak-command-reference.html)).
  But that needs a hosted, GPG-signed OSTree repo, which is close to running
  our own Flathub.
- Once a remote exists (Orchard, Flathub, or our own), the app can update
  itself through the portal: `org.freedesktop.portal.Flatpak.CreateUpdateMonitor`
  emits `UpdateAvailable`, and `Update(parent_window, …)` installs it.
  Available since flatpak 1.5.0. It fails when the new version asks for more
  permissions than the installed one.
- **Recommendation:** steer Flatpak users to Orchard (and Flathub once the
  source build lands). For the GitHub bundle, notify only.

## Frameworks considered

| Tool | License (checked) | Fit | Verdict |
|---|---|---|---|
| **Conveyor** (Hydraulic) | Tool is proprietary; free for OSS apps. The public `hydraulic-software/conveyor` repo is Apache-2.0 docs/samples. | Replaces jpackage entirely: MSIX + `.appinstaller` on Windows, Sparkle zips (no DMG) on macOS, its own apt repo for .deb, tarball with no updates. Has Compose Desktop support. | Most complete, but it throws away our signed DMG, MSI/winget/Homebrew pipeline, rpm, AppImage and Flatpak work, and puts releases on a closed tool. No. |
| **Sparkle 2** | MIT | macOS only; native framework + JNA bindings. | Fallback for macOS if the in-house path fails. |
| **WinSparkle** | MIT | Windows only; runs our MSI. | Little gain over a PowerShell helper. |
| **Velopack** | MIT | Lists C#, C++, JS, Rust SDKs; no JVM SDK we could confirm; ships its own installer/package formats. | No. |
| **update4j** | Apache-2.0, repo archived 2023 | Swaps jars at JVM level. | Wrong level: breaks the macOS signature seal and can't update the JRE or natives. No. |
| **AppImageUpdate / zsync** | MIT | AppImage only. | Yes, as the `-u` update info (external tools), alongside the in-app swap. |

## Proposed shape in the codebase

- **`commons` (headless).** These go under a new `commons/…/update/` package.
  - `UpdateSource` interface, with `NostrReleaseSource` (NIP-82) and
    `GitHubReleaseSource` (notify-only) implementations.
  - `AvailableUpdate(version, notesUrl, asset: url/sha256/size/mime/platform, trusted: Boolean)`.
  - `UpdateChecker`: schedules checks, compares versions with
    `Nip82VersionComparator`, applies downgrade/freeze rules, exposes a
    `StateFlow<UpdateState>`.
  - All of it is CLI-safe, so `amy` could reuse it later (`amy update`, since
    `amy` ships a tarball too).
- **`desktopApp` (platform shim).**
  - `InstallChannelDetector`.
  - One `UpdateInstaller` per channel: `MacDmgInstaller`, `WindowsMsiInstaller`,
    `PortableDirInstaller` (Windows zip + Linux tarball), `AppImageInstaller`,
    `NotifyOnlyInstaller` (brew, deb/rpm, Flatpak), `FlatpakPortalInstaller`.
  - The helper-script writer.
  - All of it goes behind a port the shared UI calls, so Android (which updates
    through stores) gets a no-op.
- **`commonsUI`.** An update banner and a Settings › App › Updates section
  (toggle, "Check now", the current channel shown as text, so users understand
  why it's "notify only"). Strings go in `commonsUI` compose resources.
  Separately, the shared Software Apps screen filters by hashtag and follows
  but not by platform (`commons/…/relayClient/softwareapps/`), so on desktop it
  lists Android-only apps. Worth adding a `#f` filter once desktop builds are
  published.
- **Release workflow.** `-u` + `.zsync` for the AppImage, Authenticode for the
  MSI/zip exe, publishing of the NIP-82 asset events (hashes come from `dist/*`
  in the same job).

## Phases

0. **Verify assumptions** on real machines:
   - an in-app-downloaded DMG/MSI carries no quarantine attribute / MOTW;
   - `msiexec /i` over the installed MSI upgrades in place and keeps the
     shortcut/AUMID;
   - renaming over a running AppImage is safe;
   - the Windows zip swap works with Defender scanning.
1. **Notify only, everywhere:** channel detection + `GitHubReleaseSource` +
   banner. Small and low-risk; it already fixes "users never learn there's an
   update".
2. **Signed manifest:** publish NIP-82 asset events per release, add
   `NostrReleaseSource`, pin the key.
3. **Unprivileged self-replace:** AppImage, Linux tarball, Windows zip. Add
   `-u`/zsync to the AppImage build.
4. **MSI** with the UAC helper. Add Authenticode signing alongside.
5. **DMG** in-house installer (Sparkle only if this stalls).
6. **apt/dnf repositories** for the .deb/.rpm. This is release infrastructure,
   not app code.

## Open questions for the maintainer

- Signing the per-release NIP-82 asset events: by hand after each release, via
  a bunker from CI, or with a dedicated release key that the app pins?
- Desktop NIP-82 app id: is `com.vitorpamplona.amethyst.desktop` fine (see
  [Event budget](#event-budget-per-release))? Publish the .deb/.rpm assets
  too (13 events per release instead of 9)?
- Is Authenticode signing for Windows in budget (Azure Trusted Signing is paid
  monthly; SignPath has an OSS program)?
- Should we host apt/dnf repos, or keep .deb/.rpm as one-shot downloads with
  notify-only updates?
- Should updates install automatically by default, or only notify by default?
  Nostr users tend to want control. Suggest: notify by default, auto-install
  opt-in.

## Implementation

Built on 2026-10-15, following the recommendations above. Decisions taken where the study left a
question open:

- **App id:** `com.vitorpamplona.amethyst.desktop`.
- **Signing:** by hand, with Amethyst's nsec, through `amy releases publish` (RELEASE_OPS.md
  § Nostr releases). No key in CI. The same run also publishes the APK's events for Zapstore, tag
  for tag as `zsp` did, and the Flatpak's for Orchard, so one command covers all three NIP-82
  readers.
- **Orchard:** the events name the GitHub Release's `.flatpak` bundles by URL and SHA-256, which
  Orchard installs since 0.2.0 (2026-10-15). Orchard 0.1.x needed an OCI image on a registry, which
  1.19.0 had (`orchard-publish`, ghcr); with bundles there is no registry, no image build and no
  third-party tool in CI.
- **.deb and .rpm assets:** published too (12 assets per release), for the PackageKit hand-off.
- **Default:** the app checks on its own (at start and twice a day) and asks before it installs
  anything. Settings › App › Updates turns the checks off.

### Where the code is

| Piece | Where |
|---|---|
| Release lookup, trust rules, state machine, settings | `commons/…/appupdate/` (`Nip82UpdateSource`, `Nip82UpdateFinder`, `AppUpdateManager`, `AppUpdateSettingsStore`) |
| Release events and asset names (shared with amy) | `commons/…/appupdate/DesktopReleaseEvents.kt`, `DesktopRelease.kt` |
| Prompt and Settings › App › Updates | `commonsUI/…/appupdate/ui/` (`AppUpdateHost`, `AppUpdateSettingsSection`), wired through `AppRoot.appUpdates` |
| Install channel, downloader, installers, helper scripts, GitHub fallback | `desktopApp/…/desktop/update/` |
| Publishing | `cli/…/commands/ReleasesCommands.kt` (`amy releases publish`) |
| Zapstore and Orchard events, APK and bundle checks | `commons/…/appupdate/` (`AndroidReleaseEvents`, `FlatpakReleaseEvents`, `FlatpakRelease`, `ReleaseBundles`, `ApkInspector`) |
| AppImage zsync | `createReleaseAppImage` (`-u gh-releases-zsync|…`), `scripts/asset-name.sh`, `zsync` in `create-release.yml` |

How each part behaves:

- **Trust.** The app installs only from a kind 30063 release signed by `DesktopRelease.PUBLISHER`,
  for the desktop app id, on the `main` (or `stable`) channel, with the host's platform. Its file
  must be one of the release's own `e`-tagged assets, signed by the same key, with an https URL and
  a SHA-256. The download is checked against that hash and size before anything moves. The GitHub
  API only produces a "new version, open the download page" notice, never an install.
- **Prereleases.** `amy` publishes a prerelease version on the `beta` channel, which the app
  ignores.
- **Applying.**
  - AppImage: renamed over the running file, then relaunched.
  - Windows zip and Linux tarball: unpacked next to the app folder; after the app quits, a helper
    script swaps the folders and puts the old one back if the move fails.
  - DMG: `codesign --verify --deep --strict` and the same Team ID as the running app, then the
    same swap.
  - MSI: `msiexec` with UAC after the app quits.
  - .deb/.rpm: `pkcon install-local` after the app quits, or the desktop's software installer
    when PackageKit is missing.

  Every helper relaunches the app whether or not the update went in.
- **Notify only.** Homebrew (with the `brew upgrade` command), Flatpak (with `flatpak update`), a
  translocated macOS app (move it to Applications), and any install the user cannot write to (the
  download page).

### How it was checked

- **Unit tests (46 tests).**
  - `commons` (24): release selection, including forged, foreign, unnamed and http assets,
    channels, platforms and prereleases; the state machine; asset names.
  - `desktopApp` (22): channel detection per OS and installer, the downloader against a local HTTP
    server (hash, size and network failures), zip-slip, and the Unix helper run for real (it waits
    for a process, swaps the folders and relaunches, and rolls back when the new folder is missing).
- **End to end** (`UpdateEndToEndTest`, 1 more test): signed events on an in-process relay, the updater finding
  them, downloading, verifying, staging and swapping an AppImage.
- **amy** against a local `amy serve` relay: 13 events published (12 assets and the release); a
  `#i` + `#f` query returns the release and the right asset.
- **The desktop app, headless:** with `-Damethyst.update.devCheck=true -Damethyst.update.pretendVersion=1.0.0`,
  the app queried the relays (no signed release exists yet), fell back to GitHub, showed the
  "Amethyst 1.19.0 is available" prompt and the Settings › App › Updates section, and kept the
  auto-check switch.
- **appimagetool 1.9.0** writes the `.zsync` only when `zsyncmake` is installed, and points it at
  the AppImage's file name. That is why the build now names the AppImage as the release asset, and
  why CI installs `zsync`.

### Left to do

- **Try it on real installs:** the DMG (Gatekeeper, writing to `/Applications`, quarantine), the
  MSI (UAC, the upgrade keeping the Start menu shortcut), the Windows zip under Defender, and a .deb
  through PackageKit. The phase 0 assumptions above are still unverified on those systems.
- **Publish the first release events** for the next stable tag (`--listing` the first time).
- Authenticode signing for the MSI and the Windows launcher.
- Signed apt/dnf repositories, which would make the .deb/.rpm updates the system's job.
- A hosted Flatpak repo (`--repo-url`) for the GitHub bundle, or pointing those users at Orchard.

