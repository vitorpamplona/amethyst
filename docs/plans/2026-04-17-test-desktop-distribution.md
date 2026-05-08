# Test Plan: Desktop Multi-Platform Distribution

**PR branch:** `feat/desktop-multiplatform-distribution`
**Test date:** 2026-04-17

## Pre-PR Test Matrix

### Phase 0: Version wiring (local, all platforms)

| # | Test | Command | Expected | Status |
|---|---|---|---|---|
| 0.1 | Desktop version reads from catalog | `./gradlew :desktopApp:properties \| grep "^version:"` | `version: 1.08.0` | |
| 0.2 | Android versionName reads from catalog | `./gradlew :amethyst:assembleDebug` then check `amethyst/build/outputs/apk/debug/*.apk` manifest | `versionName=1.08.0-feat-desktop-multipl` (branch suffix) | |
| 0.3 | Quartz publish version unaffected | `grep 'version =' quartz/build.gradle.kts` | `version = "1.08.0"` in `coordinates()` block (explicit, not inherited) | |
| 0.4 | RPM version strips dashes correctly | `echo '1.08.0-rc1' \| sed 's/-/~/g'` | `1.08.0~rc1` | |

### Phase 1: Local build per format (run on native platform)

| # | Test | Platform | Command | Pass criteria |
|---|---|---|---|---|
| 1.1 | macOS ARM DMG | macOS ARM (M-series) | `./gradlew :desktopApp:packageReleaseDmg` | DMG produced; `file ...Amethyst.app/Contents/MacOS/Amethyst` shows `arm64` |
| 1.2 | macOS Intel DMG | macOS Intel (or macos-13 CI) | same command | DMG produced; `file` shows `x86_64` |
| 1.3 | Windows MSI | Windows 11 | `./gradlew :desktopApp:packageReleaseMsi` | MSI produced; double-click installs to Start Menu |
| 1.4 | Linux DEB | Ubuntu 22.04+ | `./gradlew :desktopApp:packageReleaseDeb` | `.deb` produced; `dpkg-deb -I *.deb` shows `Version: 1.08.0` |
| 1.5 | Linux RPM | Ubuntu (with `apt install rpm`) | `./gradlew :desktopApp:packageReleaseRpm` | `.rpm` produced; `rpm -qpi *.rpm` shows `Version: 1.08.0`, `License: MIT` |
| 1.6 | Linux AppImage | Ubuntu 22.04+ | `./gradlew :desktopApp:createReleaseAppImage` | `.AppImage` produced; `chmod +x && ./Amethyst-*.AppImage` launches |
| 1.7 | Portable tar.gz | Linux | `./gradlew :desktopApp:createReleaseDistributable && cd .../app && tar czf amethyst.tar.gz Amethyst/` | Extract + `./Amethyst/bin/Amethyst` runs |
| 1.8 | Portable zip | Windows | `./gradlew :desktopApp:createReleaseDistributable` + zip | Extract + `Amethyst.exe` runs without JRE |

### Phase 2: VLC video playback verification

| # | Test | Platform | Steps | Pass criteria |
|---|---|---|---|---|
| 2.1 | VLC on ARM macOS | M-series Mac | Open DMG → launch → navigate to note with video → play | Video plays without crash; Console.app shows no `libvlc` errors |
| 2.2 | VLC on Intel macOS | Intel Mac | Same | Same |
| 2.3 | VLC in AppImage | Linux (no system VLC) | `./Amethyst-*.AppImage` → find video note → play | Video plays (proves `LD_LIBRARY_PATH` + `VLC_PLUGIN_PATH` correct) |
| 2.4 | VLC dylib arch check | macOS ARM build | `file desktopApp/src/jvmMain/appResources/macos/vlc/libvlc.dylib` | `Mach-O universal binary with 2 architectures: [x86_64] [arm64]` |

### Phase 3: CI workflow dry-run

| # | Test | Trigger | Expected |
|---|---|---|---|
| 3.1 | Dry-run builds all 5 desktop legs | `gh workflow run create-release.yml -f dry_run=true -f test_tag=v0.0.0-dryrun --ref feat/desktop-multiplatform-distribution` | All 5 matrix legs green; "Dry-run summary" in step output shows assets + sizes |
| 3.2 | Dry-run skips Android | Check workflow run | `deploy-android` job shows "skipped" |
| 3.3 | Dry-run skips upload | Check step logs | "Upload to GH Release" step shows "skipped" |
| 3.4 | Dry-run does NOT trigger bumps | `gh run list --workflow=bump-homebrew.yml --limit 1` | No new runs |
| 3.5 | Asset size ≤ 1 GB per asset | "Enforce asset size budget" step log | All assets show "OK: ... MB" |
| 3.6 | Tag-vs-catalog assertion skipped on dry-run | Step log | No assertion error (dry-run uses synthetic tag) |

### Phase 4: First real tag push (staging)

**Pre-req**: Push a test prerelease tag (e.g. `v1.08.0-test`) to the fork.

| # | Test | Expected |
|---|---|---|
| 4.1 | Tag-vs-catalog assertion passes | Desktop matrix starts building (tag `v1.08.0-test`, TOML `1.08.0` — assertion skips `-test` suffix mismatch? **NO** — assertion requires exact match. Must temporarily set TOML to `1.08.0-test` or use a matching tag like `v1.08.0`.) |
| 4.2 | All 5 desktop legs produce assets | GH Release has 8 desktop assets (dmg×2, msi, zip, deb, rpm, AppImage, tar.gz) |
| 4.3 | Android produces 12 assets | Same 12 APKs + 2 AABs as before |
| 4.4 | Asset naming correct | Names match `amethyst-desktop-<ver>-<family>-<arch>.<ext>` |
| 4.5 | Prerelease classified correctly | Tag `v1.08.0-test` → release marked as prerelease |
| 4.6 | Bump workflows NOT triggered | Prerelease → `release.released` event doesn't fire for bump workflows |
| 4.7 | Android asset names identical to old scheme | `amethyst-googleplay-universal-v1.08.0-test.apk` etc. — compare with previous release |
| 4.8 | Quartz publish succeeds (if secrets available) | Maven Central shows artifact OR step skipped (no secrets on fork) |

### Phase 5: Stable release simulation

**Pre-req**: Tag matching TOML exactly (e.g. `v1.08.0`).

| # | Test | Expected |
|---|---|---|
| 5.1 | Tag-vs-catalog passes | `1.08.0 == 1.08.0` |
| 5.2 | Release marked stable | `prerelease: false` |
| 5.3 | Bump-homebrew fires | Workflow starts (will fail without `HOMEBREW_TOKEN` — expected) |
| 5.4 | Bump-winget fires | Workflow starts (will fail without `WINGET_TOKEN` — expected) |
| 5.5 | Assert-stable-release passes | Composite action log shows `tag=v1.08.0 prerelease=false draft=false` |

### Phase 6: Negative tests

| # | Test | Expected |
|---|---|---|
| 6.1 | Tag mismatch (tag `v9.9.9`, TOML `1.08.0`) | Matrix jobs fail fast: `::error::gradle/libs.versions.toml app=1.08.0 but tag is v9.9.9` |
| 6.2 | Manual bump dispatch with RC tag | `gh workflow run bump-homebrew.yml -f tag=v1.08.0-rc1` → composite action rejects: `does not match stable vMAJOR.MINOR.PATCH format` |
| 6.3 | Manual bump dispatch with stable tag | `gh workflow run bump-homebrew.yml -f tag=v1.08.0` → composite action passes (bump still fails without token — expected) |

### Phase 7: Android regression

| # | Test | Comparison | Pass criteria |
|---|---|---|---|
| 7.1 | APK/AAB count | Old release vs new | Same 12 files |
| 7.2 | APK naming | Old: `amethyst-googleplay-arm64-v8a-v1.07.5.apk` | New: identical scheme (just different version number) |
| 7.3 | APK signing | `jarsigner -verify dist/amethyst-googleplay-universal-v*.apk` | "jar verified" (requires signing secrets — skip on fork) |
| 7.4 | Gradle `allprojects.version` doesn't break Android build | `./gradlew :amethyst:assembleDebug` | Successful build; no version conflicts |

---

## Test Execution Order (recommended)

1. **Phase 0** — takes 2 min, validates version wiring locally
2. **Phase 2.4** — takes 10 sec, validates VLC arch (non-blocking sanity check)
3. **Phase 1.1 or 1.4** — build one format on your native platform to validate Gradle changes
4. **Phase 3** — CI dry-run (takes ~15 min; validates entire matrix without side effects)
5. **Phase 7.4** — Android regression (local `assembleDebug` — takes ~5 min)
6. **Phase 4** — first real tag on fork (requires fork push; takes ~25 min)
7. **Phases 5, 6** — stable release + negative tests (requires fork tag pushes)
8. **Phases 1.2-1.8, 2.1-2.3** — platform-specific tests (delegate to testers with hardware)

## What Can't Be Tested Without Maintainer

| Item | Blocked on |
|---|---|
| Homebrew cask initial PR + auto-bump | `HOMEBREW_TOKEN` secret |
| Winget initial submission + auto-bump | `WINGET_TOKEN` secret |
| Quartz Maven Central publish | `SONATYPE_*` + `SIGNING_*` secrets |
| Android APK signing | `SIGNING_KEY` + `KEY_*` secrets |
| VLC on Intel Mac | Access to Intel Mac hardware |
| AppImage on Alpine/NixOS | Access to minimal Linux distro |

These require running on the upstream repo or providing secrets to the fork.
