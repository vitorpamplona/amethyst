# Fix: Relax libicu dependency in .deb packages

**Date**: 2026-04-27
**Issue**: [#2579](https://github.com/vitorpamplona/amethyst/issues/2579)
**Branch**: `fix/deb-libicu-dependency`
**Reference**: upstream branch `fix-libicu74-dependency`

## Problem

jpackage runs `dpkg-shlibdeps` against the bundled JDK's native libs (libfontmanager.so links libicu), pinning `Depends:` to the build host's libicu version (libicu74 on ubuntu-24.04). Users on Debian 12 (libicu72), Debian 13 (libicu76), Ubuntu 22.04 (libicu70) etc. cannot install.

Neither jpackage nor Compose Desktop DSL expose a way to override auto-generated Depends.

## Solution

Post-process the .deb after jpackage: extract, rewrite the libicu dependency to accept any version from libicu66–libicu77, repack.

## Changes

### 1. Add `scripts/relax-deb-libicu.sh`

Shell script that:
- Takes one or more .deb paths as arguments
- Extracts with `dpkg-deb -R`
- Uses sed to replace `libicuNN` (or alternation) with `libicu66 | libicu67 | libicu70 | libicu72 | libicu74 | libicu76 | libicu77`
- Repacks with `dpkg-deb -b`
- Idempotent — no-op when no libicu dep present

### 2. Update `.github/workflows/build.yml`

Add step after "Build Desktop Distribution" for the `packageDeb` matrix leg:

```yaml
- name: Relax libicu dependency in .deb
  if: matrix.task == 'packageDeb'
  run: |
    set -euo pipefail
    chmod +x scripts/relax-deb-libicu.sh
    scripts/relax-deb-libicu.sh desktopApp/build/compose/binaries/main/deb/*.deb
```

Insert between "Build Desktop Distribution" (line 160) and "Stop Gradle Daemon" (line 162).

### 3. Update `.github/workflows/create-release.yml`

Add step after "Build desktop artifacts" for the `linux` family leg:

```yaml
- name: Relax libicu dependency in .deb
  if: matrix.family == 'linux'
  run: |
    set -euo pipefail
    chmod +x scripts/relax-deb-libicu.sh
    scripts/relax-deb-libicu.sh desktopApp/build/compose/binaries/main-release/deb/*.deb
```

Insert between "Build desktop artifacts" (line 99-104) and "Build portable archives" (line 106).

Note: `linux-portable` leg doesn't build .deb so no action needed there.

## Testing

- Script is idempotent — safe to run on any .deb
- CI will validate on next PR build (ubuntu-latest produces .deb artifact)
- Manual: install resulting .deb on Debian 13 (libicu76) to verify

## Unanswered Questions

- Should we also handle RPM libicu deps? (RPM has different dep mechanism, likely not affected since RPM uses `Requires:` from bundled libs differently)
- Should the libicu version list be externalized to a config file for easier updates? (Probably YAGNI — adding a new version is a one-line sed edit)
