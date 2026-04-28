---
title: "feat(cli): native distribution across macOS / Windows / Linux"
type: feat
status: proposed
date: 2026-04-21
owner: cli
---

# feat(cli): native distribution

## Overview

Today `amy` ships only as `./gradlew :cli:run` or a raw `installDist`
tree. Fine for dogfooding, not fine for the interop-test audience,
who need a single-binary install on every OS.

Goal: publish `amy-<version>-<os>-<arch>` artefacts on every GitHub
release, wrapped in the native package managers users already have.

## Target matrix

| OS / channel | Strategy | Notes |
|---|---|---|
| macOS (arm64 + x64) | `brew install amy-nostr` | Homebrew formula pointing at a tarball of `installDist` + a jlink'd JRE. Mirrors the existing `amethyst-nostr` cask. |
| Windows | `winget install VitorPamplona.Amy` + `scoop install amy` | `.zip` with `amy.bat` launcher + jlink JRE. |
| Debian / Ubuntu | `.deb` via `jpackage --type deb` | Depends on libc only; jlink JRE bundled. |
| Fedora / RHEL / openSUSE | `.rpm` via `jpackage --type rpm` | Same. |
| Arch | AUR `amy-nostr-bin` | Wrap the `.tar.gz`. |
| Any Linux | `.tar.gz` + AppImage | AppImage for users without a package manager. |
| Nix / NixOS | `nixpkgs` entry | Wrapper around `installDist`. |
| Zapstore | Extend `zapstore.yaml` | Signed by the same Nostr key as the Android app. |
| GitHub Release | All of the above attached as assets | Use `scripts/asset-name.sh` for consistent naming. |

## Shortest path to first release

1. Extend the existing `desktopApp` `jpackage` flow to also produce an
   `amy-<version>-<os>-<arch>` artefact. Reuse the signing and
   notarisation already configured for the desktop build.
2. CI matrix: macos-14 (arm64), macos-13 (x64), windows-latest,
   ubuntu-latest. Each runner produces one tarball + one native
   installer (`.dmg` / `.msi` / `.deb`).
3. Publish as release assets; no package-manager submission yet.
4. Only after the artefacts are stable (no path churn, no JRE
   incompatibilities): submit Homebrew formula, winget manifest,
   Scoop bucket, AUR PKGBUILD.

## Why not GraalVM native-image

Tempting for startup time but loses FFI to `secp256k1-kmp-jni-*`,
which is how Quartz signs today. Revisit when Quartz ships a
pure-Kotlin fallback signer.

## Auto-update

Out of scope for v1. Package managers handle it (`brew upgrade`,
`winget upgrade`, etc). Revisit if manual-install users complain.

## Size budget

Target: **< 80 MB installed** with a jlink'd runtime. If we cross
that, audit transitive deps — Amy should not pull in Compose or
Android libs. Verify with `./gradlew :cli:installDist && du -sh
cli/build/install/amy/` on CI and fail the build over a threshold.

## Risks

- **Code signing on macOS and Windows** costs real money and requires
  secrets rotation. Reuse the desktop app's existing signing setup
  rather than standing up a separate keychain.
- **Package-manager review latency.** Homebrew / winget reviews can
  take days. Ship tarballs first; submissions later.
- **JRE size.** A jlink'd JDK 21 image is ~40 MB; adding OkHttp +
  Jackson + Quartz should land well under the 80 MB budget, but a
  bad transitive dep can double it overnight.

## Open questions

- Single brew cask `amethyst-nostr` with an `amy` formula alongside,
  or a separate `amy-nostr` tap? Probably separate — different update
  cadence, different audience.
- Should Amy ship in the same release as the desktop app, or on its
  own cadence? Leaning same release — shared CI, shared version —
  until the surfaces diverge.

## Out of scope

- In-app auto-update UX (no CLI auto-update).
- macOS app notarization of the CLI (only signing). Notarization is
  for bundled `.app` GUIs.
