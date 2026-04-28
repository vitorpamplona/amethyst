# Building Amethyst Desktop

This guide covers building Amethyst Desktop from source, the release pipeline,
and one-time bootstrap steps for distribution channels.

- [Prerequisites](#prerequisites)
- [Clone + first build](#clone--first-build)
- [Per-format build commands](#per-format-build-commands)
- [Asset naming contract](#asset-naming-contract)
- [Release runbook](#release-runbook)
- [Bootstrap runbook (one-time)](#bootstrap-runbook-one-time)
- [Troubleshooting installs](#troubleshooting-installs)
- [Uninstall + state paths](#uninstall--state-paths)
- [Incident response](#incident-response)
- [Fallback plans](#fallback-plans)

---

## Prerequisites

All platforms:

- **JDK 21** (Zulu or Temurin recommended)
- **Git**

Platform-specific:

- **macOS**: Xcode Command Line Tools (`xcode-select --install`)
- **Windows**: WiX Toolset 3.x on PATH (for MSI). `winget install WiXToolset.WiXToolset`
- **Linux (all)**: nothing extra for `.deb`; `rpm` + `fakeroot` for `.rpm`; `linuxdeploy` for AppImage

Install Linux RPM tooling:

```bash
# Debian/Ubuntu
sudo apt-get install -y rpm fakeroot

# Fedora
sudo dnf install -y rpm-build
```

Install linuxdeploy locally (CI fetches its own — SHA-verified):

```bash
curl -fsSL -o packaging/appimage/linuxdeploy-x86_64.AppImage \
  https://github.com/linuxdeploy/linuxdeploy/releases/download/1-alpha-20240109-1/linuxdeploy-x86_64.AppImage
chmod +x packaging/appimage/linuxdeploy-x86_64.AppImage
```

---

## Clone + first build

```bash
git clone https://github.com/vitorpamplona/amethyst.git
cd amethyst

# Dev loop (launches Amethyst Desktop)
./gradlew :desktopApp:run

# Package for current OS
./gradlew :desktopApp:packageDistributionForCurrentOS
```

---

## Per-format build commands

| Artifact | Command | Output |
|---|---|---|
| macOS DMG (host arch) | `./gradlew :desktopApp:packageReleaseDmg` | `desktopApp/build/compose/binaries/main-release/dmg/Amethyst-*.dmg` |
| Windows MSI | `./gradlew :desktopApp:packageReleaseMsi` | `desktopApp/build/compose/binaries/main-release/msi/Amethyst-*.msi` |
| Linux `.deb` | `./gradlew :desktopApp:packageReleaseDeb` | `desktopApp/build/compose/binaries/main-release/deb/amethyst_*.deb` |
| Linux `.rpm` | `./gradlew :desktopApp:packageReleaseRpm` | `desktopApp/build/compose/binaries/main-release/rpm/amethyst-*.rpm` |
| Linux AppImage | `./gradlew :desktopApp:createReleaseAppImage` | `desktopApp/build/appimage/Amethyst-*-x86_64.AppImage` |
| Windows `.zip` portable | See below (inline `7z`) | — |
| Linux `.tar.gz` portable | See below (inline `tar`) | — |

**Inline portable archives** (run after `createReleaseDistributable`):

```bash
./gradlew :desktopApp:createReleaseDistributable

# Linux tar.gz
VER=$(grep -E '^app\s*=' gradle/libs.versions.toml | head -1 | cut -d'"' -f2)
( cd desktopApp/build/compose/binaries/main-release/app \
  && tar czf "../../../../portable/amethyst-desktop-${VER}-linux-x64.tar.gz" Amethyst/ )

# Windows .zip (PowerShell)
Compress-Archive -Path desktopApp\build\compose\binaries\main-release\app\Amethyst `
  -DestinationPath "desktopApp\build\portable\amethyst-desktop-$env:VER-windows-x64.zip"
```

Cross-platform architecture note: **`jpackage` cannot cross-compile**. An Intel
DMG must be built on `macos-13` (x64); an ARM DMG must be built on `macos-14`
or later. CI runs both.

---

## Asset naming contract

All GH Release assets follow:

```
amethyst-desktop-<version>-<family>-<arch>.<ext>
```

Where:

| Field | Values |
|---|---|
| `<version>` | Tag stripped of leading `v` (e.g. `1.08.0`) |
| `<family>` | `macos`, `windows`, `linux` |
| `<arch>` | `x64`, `arm64` |
| `<ext>` | `dmg`, `msi`, `zip`, `deb`, `rpm`, `AppImage`, `tar.gz` |

Single source of truth: [`scripts/asset-name.sh`](scripts/asset-name.sh).
Package manager manifests (Homebrew cask, Winget) depend on this exact scheme —
any change is a breaking contract.

Examples:

- `amethyst-desktop-1.08.0-macos-x64.dmg`
- `amethyst-desktop-1.08.0-macos-arm64.dmg`
- `amethyst-desktop-1.08.0-windows-x64.msi`
- `amethyst-desktop-1.08.0-linux-x64.AppImage`

---

## Release runbook

The release flow is driven by a tag push. Every cut ships Android + Desktop +
Quartz library in one pipeline.

1. **Bump the app version** in `gradle/libs.versions.toml`:

   ```toml
   [versions]
   app = "1.08.1"  # new semver
   ```

2. **Bump Android `versionCode`** in `amethyst/build.gradle` (monotonic integer,
   must increment even for same `versionName`):

   ```groovy
   versionCode = 443
   versionName = generateVersionName(libs.versions.app.get())
   ```

3. **Commit + tag + push**:

   ```bash
   git commit -am "chore(release): 1.08.1"
   git tag -s v1.08.1 -m "Release 1.08.1"
   git push && git push --tags
   ```

4. **Wait** for the `Create Release Assets` workflow to finish (~25–30 min).

5. **Verify**:
   - GH Release contains 8 desktop assets + 12 Android assets
   - Asset sizes look sane (see §Enforce asset size budget — CI auto-fails at 1 GB/asset)
   - Intel + ARM DMGs both present
   - Android flow unchanged

6. **Stable vs prerelease** — a tag containing `-rc`, `-beta`, `-alpha`, `-dev`,
   or `-snapshot` is auto-classified as prerelease. Stable tags trigger the
   Homebrew + Winget bump workflows.

### Dry-run (no tag push)

Use `workflow_dispatch` to exercise the full matrix without publishing:

```bash
gh workflow run create-release.yml \
  -f dry_run=true \
  -f test_tag=v0.0.0-dryrun \
  --ref feat/my-branch
```

Assets are built and size-checked, but not uploaded; bump workflows do not
fire. Use for pre-merge validation of workflow changes.

### Version constraint: tag must match `libs.versions.toml`

The first step in each build-desktop matrix job asserts:

```
tag (stripped of 'v') == gradle/libs.versions.toml [versions] app
```

If they drift, the workflow fails fast. Always bump the TOML first, then tag.

### NEVER change Windows `upgradeUuid`

`desktopApp/build.gradle.kts:upgradeUuid` is the MSI product family GUID.
Changing it breaks in-place upgrades for existing Windows users — they must
uninstall before a new release. Leave it alone forever.

---

## Bootstrap runbook (one-time)

### Secrets to provision in GitHub repo settings

| Secret | Purpose | Scope |
|---|---|---|
| `HOMEBREW_TOKEN` | Bump Homebrew cask | Fine-grained PAT — `Homebrew/homebrew-cask` only — `Contents: write` + `Pull requests: write` — 90d expiry |
| `WINGET_TOKEN` | Submit Winget manifests | Classic PAT — `public_repo` — 90d expiry (dedicated bot account preferred; `vedantmgoyal9/winget-releaser` does not support fine-grained) |

All existing secrets (`SIGNING_KEY`, `SONATYPE_USERNAME`, etc.) remain
unchanged.

Rotate both on a 90-day cadence. Owner: assigned via `docs/RELEASE_OPS.md`
or equivalent issue tracker. On rotation, paste new token and run
`gh workflow run bump-homebrew.yml` on the most recent stable tag to verify.

### Homebrew cask (one-time initial PR)

```bash
brew bump-cask-pr amethyst-nostr \
  --version 1.08.0 \
  --url "https://github.com/vitorpamplona/amethyst/releases/download/v1.08.0/amethyst-desktop-1.08.0-macos-arm64.dmg"
```

The cask filename is `amethyst-nostr` (not `amethyst` — that's taken by a
tiling window manager). After the first PR is merged, `bump-homebrew.yml`
auto-submits new version bumps on each stable release.

### Winget (one-time initial submission)

```bash
wingetcreate new \
  https://github.com/vitorpamplona/amethyst/releases/download/v1.08.0/amethyst-desktop-1.08.0-windows-x64.msi
```

Set `PackageIdentifier = VitorPamplona.Amethyst`. After the first manifest is
merged into `microsoft/winget-pkgs`, `bump-winget.yml` auto-submits new
version manifests.

---

## Troubleshooting installs

### macOS — Gatekeeper "damaged and can't be opened"

Amethyst Desktop is currently unsigned. First-time launch requires:

1. **Right-click → Open** on the app (don't double-click) — then click **Open** on the Gatekeeper dialog
2. Or: `xattr -cr /Applications/Amethyst.app` to strip quarantine
3. Or: System Settings → Privacy & Security → "Open Anyway" after a blocked launch

Recommended path: install via Homebrew (`brew install --cask amethyst-nostr`)
— cask flow handles this seamlessly.

### Windows — SmartScreen "Windows protected your PC"

Amethyst Desktop is currently unsigned (no Authenticode). First-time launch:

1. Click **More info** on the SmartScreen dialog
2. Click **Run anyway**

Alternatively use `winget install VitorPamplona.Amethyst` — winget install
bypasses the UI dialog after accepting the installer's inherent trust.

### Linux AppImage won't execute

```bash
chmod +x Amethyst-*.AppImage
./Amethyst-*.AppImage
```

On Fedora Silverblue / very minimal distros, FUSE might be missing. Use
`--appimage-extract-and-run`:

```bash
./Amethyst-*.AppImage --appimage-extract-and-run
```

---

## Uninstall + state paths

State is shared across install channels (DMG, Homebrew, MSI, Winget, .deb,
.rpm, AppImage, tar.gz). Switching channels does not duplicate data but may
expose downgrade migration risks — **prefer a single install channel per
machine**.

| OS | App location | State directories |
|---|---|---|
| macOS | `/Applications/Amethyst.app` | `~/Library/Application Support/Amethyst`<br>`~/Library/Preferences/com.vitorpamplona.amethyst.desktop.plist`<br>`~/Library/Caches/Amethyst` |
| Windows | `%LOCALAPPDATA%\Amethyst` or `C:\Program Files\Amethyst` | `%APPDATA%\Amethyst`<br>`%LOCALAPPDATA%\Amethyst` |
| Linux (deb/rpm) | `/opt/amethyst` | `~/.config/amethyst`<br>`~/.local/share/amethyst`<br>`~/.cache/amethyst` |
| Linux (AppImage/tar.gz) | user-chosen | Same as above |

Uninstall:

- Homebrew: `brew uninstall --cask amethyst-nostr && brew zap amethyst-nostr`
- Winget: `winget uninstall VitorPamplona.Amethyst`
- .deb: `sudo apt remove amethyst`
- .rpm: `sudo dnf remove amethyst`
- AppImage / tar.gz: delete the file / extracted directory
- macOS `.dmg`: drag from `/Applications` to Trash, then delete state dirs manually

---

## Incident response

### Bad GH Release asset

1. Immediately mark release as prerelease (pauses bump workflows):
   ```bash
   gh release edit v1.08.1 --prerelease
   ```
2. Delete the bad asset:
   ```bash
   gh release delete-asset v1.08.1 amethyst-desktop-1.08.1-macos-arm64.dmg --yes
   ```
3. Rebuild locally or rerun the failing matrix job:
   ```bash
   gh run rerun <run-id> --failed
   ```
4. Flip back to stable once verified (re-fires bump workflows — confirm fix first):
   ```bash
   gh release edit v1.08.1 --prerelease=false
   ```

### Bad build reached Homebrew

**Preferred**: ship a point release (e.g. v1.08.2) — users on v1.08.1 get the
fix via `brew upgrade`.

**Alternative**: close the open PR in `Homebrew/homebrew-cask` before merge,
or file a revert PR if already merged. Typical Homebrew turn-around: 1–2 days.

### Bad build reached Winget

Winget manifests are append-only — no hard unpublish. Options:

1. Ship a point release (preferred — users upgrade via `winget upgrade`)
2. File a manifest-removal PR against `microsoft/winget-pkgs`. Moderator
   review: 24–72h.

### User-facing communication

On any incident:

1. Edit the release body on GitHub with a warning banner + workaround
2. Pin a GH Issue with downgrade instructions per channel
3. Announce via Nostr relay + project social channels

---

## Fallback plans

### macOS Intel runner retirement

GitHub's `macos-13` runner will eventually be deprecated. Monitor
<https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners>
for the deprecation date. When it hits:

1. Drop the `macos-13` matrix entry from `.github/workflows/create-release.yml`
2. Add a cross-arch build step on `macos-14` using a bundled x64 JDK + `jpackage --mac-signing-prefix` shenanigans, OR accept that only Apple Silicon DMGs ship and direct Intel users to `winget` on a Parallels VM or to rebuild from source.
3. Update README install matrix to reflect the change.

### Homebrew main-cask rejects unsigned app (post-Sept 1 2026)

Homebrew has committed to disabling unsigned casks in `Homebrew/homebrew-cask`
on 2026-09-01. Before that date:

**Option A**: Commit budget to Apple Developer Program ($99/yr), add
`signing { sign.set(true) }` + `notarization {}` blocks to
`desktopApp/build.gradle.kts`, wire Developer ID + notary creds into CI.

**Option B**: Pivot to a private Homebrew tap:

```bash
# Create repo: vitorpamplona/homebrew-amethyst
# Update bump-homebrew.yml:
#   tap: vitorpamplona/amethyst
#   cask: amethyst-nostr
# Users install: brew tap vitorpamplona/amethyst && brew install --cask amethyst-nostr
```

Note: a private tap does NOT bypass Gatekeeper itself (macOS OS-level) — users
still see the "unsigned developer" dialog. Tap only sidesteps Homebrew's
internal policy.

---

## Follow-up channels (separate PRs)

- **AUR** (`amethyst-desktop-bin`) — blocked on AUR account ownership decision
- **Scoop** (Windows) — blocked on bucket strategy (own vs Extras)
- **Flathub** — deferred (moderate ongoing maintenance)
