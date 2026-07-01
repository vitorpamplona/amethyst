---
title: fix(desktop): macOS forced re-login on cold boot — ProGuard strips java-keyring backend
type: fix
status: shipped-pr1
date: 2026-06-18
origin: docs/brainstorms/2026-06-18-fix-macos-bunker-relogin-brainstorm.md
---

## Implementation Status (2026-06-18, updated)

**H1 (ProGuard strip) is REFUTED.** Binary PoW: `./gradlew :desktopApp:proguardReleaseJars` on both `main` and the fix branch produces `java-keyring-1.0.4-*.jar` outputs with byte-identical macOS keychain backend bytecode (`OsxKeychainBackend`, `ModernOsxKeychainBackend`, `pt/davidafsilva/apple/OSXKeychain` including all `_addGenericPassword` / `_findGenericPassword` / `_deleteGenericPassword` / `loadSharedObject` native methods). The original `pt.davidafsilva.apple.**` keep rule was correctly targeting the transitive JNI bridge (`ModernOsxKeychainBackend` holds a `private pt.davidafsilva.apple.OSXKeychain` field). See PR #3260 comment for the full keep-rule audit.

**Landed in PR 1 (defense-in-depth only):**
- ✅ `AccountManager._keychainUnavailable: StateFlow<Boolean>` mirroring the existing `_storageCorruption` / `_forceLogoutReason` diagnostic channels
- ✅ `loadInternalAccount` + `loadBunkerAccount` raise the signal when `accounts.json.enc` points at a key the keychain cannot return
- ✅ `LoginScreen` observes the signal, renders a single-line error banner above the LoginCard; `clearKeychainUnavailable()` invoked from all successful login paths
- ✅ Four new unit tests in `AccountManagerLoadAccountTest`

**Reverted in PR 1:**
- ❌ ProGuard keep-rule change — the hypothesis it was based on is refuted by binary PoW; the original `pt.davidafsilva.apple.**` keep is correct and stays in place.

**Open — actual root cause still unidentified after three refuted hypotheses.**

### PoW logs from 2026-06-18 / 06-19 investigation

| # | Hypothesis | Test | Result |
|---|------------|------|--------|
| H1 | ProGuard strips macOS keychain backend classes | `javap` on `OsxKeychainBackend.class` + `ModernOsxKeychainBackend.class` in proguarded vs unshrunk jar | **REFUTED** — bytecode identical, all native methods preserved |
| H1b | ProGuard strips `osxkeychain.so` native resource | `unzip -l desktopApp/build/compose/tmp/main-release/proguard/jkeychain-1.1.0-*.jar` | **REFUTED** — file present, 117016 bytes |
| H2 | Hardened runtime + Library Validation blocks unsigned dylib load | `codesign -d --entitlements -` on locally-built `Amethyst.app` | **REFUTED** — `com.apple.security.cs.disable-library-validation = true` is present in default entitlements |
| — | `Keyring.create()` + `setPassword` + `getPassword` round-trip on macOS Keychain | Java program run with `java -cp <proguarded jars> KeychainTest` | **PASS** — round-trip succeeds against proguarded classpath on this host |
| — | Locally-built release distributable launches | `./Amethyst.app/Contents/MacOS/Amethyst` foreground for 10s | **OK** — only unrelated VLC plugin-cache warnings on stderr |

**What this means:** the bug cannot be reproduced on a locally-built (unsigned, adhoc-signed) release distributable on this Apple Silicon Mac. Either:
- The bug requires the actual CI-built release DMG (something in CI's environment / jpackage runtime differs from local)
- Or the bug is environment-specific to the affected user (macOS version, prior Keychain state, quarantine xattrs, an upgrade path we haven't replicated)
- Or there's a different code path (not keychain) that breaks on cold boot for bunker accounts specifically

**Next investigation steps need a Mac + the affected user:**
- Affected user: `Console.app` filter "Amethyst", attempt bunker login, force-quit, relaunch — share log lines.
- Affected user: `security find-generic-password -s amethyst-desktop` immediately after first bunker login — does the entry exist?
- Affected user: `ls -la ~/.amethyst/` — does `accounts.json.enc` contain a bunker entry after first login?
- Mac reviewer: download the *signed/notarized* GitHub release DMG, install, repro on a clean macOS user account.

# 🐛 fix(desktop): macOS forced re-login on cold boot — ProGuard strips java-keyring backend

> **Status:** in-progress — PR 1 defense-in-depth (keychainUnavailable signal + login banner + tests) landed, but the actual cold-boot root cause is still open/unidentified.
> _Audited 2026-06-30._


## Overview

User on macOS using the recent Amethyst Desktop release (official GitHub DMG) is forced to re-log-in with their NIP-46 bunker on **every** cold boot — 100 % reproducible. Investigation determined the bug is **not bunker-specific**: every macOS release-DMG user whose account requires a key stored in the OS keychain loses their session on cold boot (bunker, nsec, NWC). Bunker is the loudest symptom because re-pasting a bunker URI is high-friction; nsec users likely re-paste quickly and never report.

Plan delivers a **reproduce-before-fix** workflow per `CLAUDE.md`'s "Verify, Don't Guess" instruction: a failing test on `main` first, a confirmed local-DMG reproduction second, then the fix, then signed/notarized verification.

## Problem Statement / Motivation

### Symptom (reported)
- Affected user: macOS, official DMG, "every restart, always."
- Cold boot → login screen instead of feed → user must repeat bunker login flow.

### Code path
```
loadSavedAccount() [AccountManager.kt:240]
  → loadBunkerAccount(bunkerUri, npub) [AccountManager.kt:259, 291]
  → secureStorage.getPrivateKey(bunkerEphemeralKeyAlias(npub)) [AccountManager.kt:296]
  → SecureKeyStorage.getPrivateKey() [SecureKeyStorage.kt:110]
  → Keyring.create() throws BackendNotSupportedException
  → catch → keyringAvailable = false (process-wide), getFromFallback()
  → SecureKeyStorage.kt:201: fallbackPassword ?: return null  // GUI cold boot, never prompted
  → null returned
  → loadBunkerAccount returns Result.failure(Exception("Ephemeral key not found")) [:300]
  → Main.kt translates failure → AccountState.LoggedOut, no diagnostic surfaced to user
```

The same path also serves:
- **nsec**: `loadInternalAccount()` → `secureStorage.getPrivateKey(npub)` (`AccountManager.kt:269`)
- **NWC**: `nwc_<npub>` keychain alias per memory note + recent hardening (`46caa4d79`).

So the same failure invalidates all three on macOS release builds.

### Root cause
`desktopApp/compose-rules.pro:94-96` declares ProGuard keep rules for `pt.davidafsilva.apple.**` — a library that is **not** in this project's dependency graph. The actual macOS keychain dependency is `com.github.javakeyring:java-keyring` (`libs.versions.toml:166`).

- ProGuard was newly wired in v1.09.1 (`compose-rules.pro:76-90` comment).
- `Keyring.create()` reflection-loads its OS backend (e.g. `com.github.javakeyring.internal.osx.OSXKeychainBackend`).
- The shrink pass (still on; only `-dontoptimize` is set per `compose-rules.pro:127`) removes classes with no static callers — and the macOS backend has none, by design.
- Result: `Keyring.create()` always throws `BackendNotSupportedException` in the release DMG. Dev `:desktopApp:run` skips ProGuard, which is why no-one caught it pre-release.

### Why it surfaced only after the May 15 hardening
- Pre-`46caa4d79`: bunker URI was read from `bunker_uri.txt`; the legacy load path tolerated missing keychain state (rebuilt via different fallback).
- Post-`46caa4d79`: cold boot routes strictly on `SignerType` from `accounts.json.enc` and requires the keychain ephemeral key. Same keychain failure now manifests as a hard "ephemeral key not found" → silent LoggedOut.

## Proposed Solution

Three-layer fix, deliberately small and bounded:

1. **Primary**: correct the ProGuard keep rules to actually keep `com.github.javakeyring.**` (and JNA-Structure-style native-method-bearing members in its `internal.**` backends). Delete the dead `pt.davidafsilva.apple.**` rules.
2. **Defense in depth**: `SecureKeyStorage.getPrivateKey()` should not silently return null when the keychain is configured-but-broken; route a distinct error so the caller can present a diagnosable state instead of an indistinguishable LoggedOut.
3. **Regression guard**: extend the existing release-DMG smoke test (already present for #2819 / `c0c055e77`) with a "bunker cold boot survives restart" assertion against a signed+notarized artifact.

## Technical Considerations

### Architecture impacts
- Single file change in `compose-rules.pro` for the primary fix. No public API change.
- `SecureKeyStorage` gains a new exception case (already declares `SecureStorageException`) — internal contract only.
- `AccountManager.loadBunkerAccount` / `loadInternalAccount` propagate a typed failure rather than a generic `Exception("Ephemeral key not found")`.
- Login screen reads a single new sentinel from the existing `AccountState` flow (no new global state container).

### Performance implications
- None. ProGuard keep rules add a handful of classes to the shipped jar (~few KB).

### Security considerations
- Keep rules are scoped to `com.github.javakeyring.**`; no broader reflection surface introduced.
- Fallback file (`~/.amethyst/keys.enc`) behavior unchanged — still password-gated for genuine fallback environments; the fix prevents the *unintended* fallback path on macOS release builds.

## System-Wide Impact

- **Interaction graph**: ProGuard config → `Keyring.create()` backend lookup → JNA call to macOS Security framework → `setPassword` / `getPassword` succeeds → `SecureKeyStorage` returns key → `loadBunkerAccount` / `loadInternalAccount` succeed → `AccountState.LoggedIn`. Same chain serves NWC secret.
- **Error propagation**: today `getFromFallback` returns null on a process-wide latch (`keyringAvailable = false`) once `BackendNotSupportedException` fires — every subsequent read in the same process also returns null. After the fix this latch is never tripped on macOS release builds; for genuine fallback scenarios (Linux without secret service) behavior is unchanged.
- **State lifecycle risks**: pre-fix Keychain entries written under one code-signing identity may not be readable after a re-signed update (macOS ACL); the user will need to log in **once** after upgrading. Document this; do NOT attempt automatic re-write.
- **API surface parity**: bunker, nsec, NWC all read via `SecureKeyStorage` — all benefit from one keep-rule fix and one defense-in-depth wrap. iOS uses a different `actual` and is unaffected.
- **Integration test scenarios**: covered in Phase 3 below.

## Phases

### Phase 0 — Diagnostics & Reproduction (gating, MUST complete before Phase 1)

Track A (cheap, CI): write deterministic failing test in `commons:jvmTest`.
- File: `commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/keystorage/SecureKeyStorageFallbackSilentFailureTest.kt`
  - Inject a `Keyring` factory that throws `BackendNotSupportedException`.
  - Save then read → assert that the API surface today returns `null` silently (the failing-on-fix predicate is "no diagnosable signal exists for cold-boot keychain unavailability").
  - Test stays after fix as a regression guard for the defense-in-depth path.

Track B (one-machine, manual recipe): local release-DMG reproduction.
- Build current `main`: `./gradlew :desktopApp:packageReleaseDmg` (or `createReleaseDistributable` + `packageDmg` — confirm exact task in Phase 0).
- Install DMG; log in with a fresh bunker URI; quit; relaunch → expect login screen.
- On the same Mac, `./gradlew :desktopApp:run` with the same `~/.amethyst/` directory → expect logged-in feed (proves dev vs release divergence).
- Run `security find-generic-password -s amethyst-desktop` → expect entry present (proves write-side works; isolates read-side).
- Inspect ProGuard mapping: confirm `com.github.javakeyring.internal.osx.OSXKeychainBackend` is missing/renamed in the release jar.

Track C (gated on B inconclusive): instrumented build to affected user.
- Add `println` around `Keyring.create()`, `getPassword`, and the fallback entry, with the exception class name and stack.
- Hand to the affected user; collect cold-boot log.

**Phase 0 done when:** Track A test is passing on `main` (i.e. silent-null IS the behaviour) AND Track B has visually reproduced "every restart, always" on a locally-built DMG. If A passes but B can't reproduce, escalate to H2 (signing/entitlements) per the brainstorm.

### Phase 1 — Primary fix: ProGuard keep rules

- File: `desktopApp/compose-rules.pro`
- Replace the dead `pt.davidafsilva.apple.**` block (lines 85-96) with:
  ```proguard
  # com.github.javakeyring:java-keyring — macOS Keychain / Linux Secret Service /
  # Windows Credential Manager backends are loaded by reflection from
  # Keyring.create(); the shrink pass strips them without these keeps.
  -keep class com.github.javakeyring.** { *; }
  -keepclassmembers class com.github.javakeyring.internal.** {
      native <methods>;
      <init>(...);
  }
  ```
- Update the JNI-keep comment block above to point to the real library.
- Audit: do any other reflection-loaded backends in our deps share this hazard? Quick grep for `Class.forName` / `ServiceLoader` in shipped libs; out-of-scope to fix but worth noting.

### Phase 2 — Defense in depth: diagnosable cold-boot state

Today `loadSavedAccount` collapses every failure into `Result.failure(Exception(...))` and the UI shows the login screen with no signal. Add **one** sentinel:

- `commons` (or `desktopApp` if commons would force cross-module churn): new typed failure reason, e.g. `SignerLoadError.KeychainUnavailable(npub: String)`.
- `SecureKeyStorage.getPrivateKey()`: distinguish "keychain says no such entry" (legitimate null) from "keychain backend not usable in this process" (latched state) and surface a `SecureStorageException` for the latter.
- `AccountManager.loadBunkerAccount` / `loadInternalAccount`: catch `SecureStorageException`, map to the typed failure reason, route through to `AccountState.LoggedOut(reason = ...)` (extend the existing LoggedOut, do **not** add a new state).
- `LoginScreen`: when `reason != null`, render a single-line banner: *"Your saved session couldn't be restored. Please log in again."* (no help-link, no telemetry — keep it minimal).
- This is the only UX change; **resist** adding error-recovery dialogs, retry buttons, or settings screens. Per `CLAUDE.md` "Don't add features beyond what the task requires."

### Phase 3 — Tests

| # | Scenario | Test file | Type |
|---|----------|-----------|------|
| 1 | `Keyring.create()` returns a working backend in release classpath (post-ProGuard) | `commons/src/jvmTest/.../SecureKeyStorageBackendLoadsTest.kt` | Unit, asserts `Keyring.create()` does not throw on the running JVM |
| 2 | Backend throws → `SecureKeyStorage.getPrivateKey()` surfaces `SecureStorageException`, not silent null | `commons/src/jvmTest/.../SecureKeyStorageFallbackSilentFailureTest.kt` | Unit (Track A above, evolves into this) |
| 3 | Cold boot with bunker account + simulated keychain failure → LoggedOut with `KeychainUnavailable` reason | `desktopApp/src/jvmTest/.../AccountManagerBunkerColdBootRecoveryTest.kt` | Unit |
| 4 | Cold boot with nsec account + simulated keychain failure → same diagnosable LoggedOut | `desktopApp/src/jvmTest/.../AccountManagerInternalColdBootRecoveryTest.kt` | Unit |
| 5 | Cold boot with NWC secret + simulated keychain failure → wallet section degrades gracefully (no crash) | `desktopApp/src/jvmTest/.../AccountManagerNwcColdBootRecoveryTest.kt` | Unit |
| 6 | Multi-account: 1 internal + 1 bunker, switching account does not corrupt the other's keychain entry | `desktopApp/src/jvmTest/.../AccountManagerMultiAccountSwitchTest.kt` (extend existing) | Unit |
| 7 | Release-DMG smoke: log in with bunker → kill app → relaunch → assert still logged in | `desktopApp/.../SmokeTestRelease*.kt` (extend pattern from `c0c055e77`) | Smoke (signed/notarized DMG) |

ProGuard mapping regression guard (optional, recommended): a tiny gradle check that fails the release build if `mapping.txt` contains a rename for `com.github.javakeyring.internal.osx.OSXKeychainBackend`.

### Phase 4 — Cross-platform verification (Linux / Windows release builds)

- Linux release DEB: built-in fallback is Secret Service / kwallet → if backend stripped, falls back to encrypted file but with the same silent-password failure mode in GUI sessions.
- Windows release MSI: WinCredential backend; same reflection-load pattern.
- Run smoke test (Phase 3 #7) on Linux DEB and Windows MSI release artifacts. Add to existing CI matrix.

### Phase 5 — Signed/notarized verification + release notes

- Verify on a **signed and notarized** macOS DMG (not just locally built) — the affected user reported via official release.
- Verify on Linux DEB and Windows MSI.
- Release notes line: "Fixed an issue where macOS users were forced to re-log-in on every app restart (since v1.09.1)."
- **Known caveat documented**: users who logged in under a previous (unfixed) build may have a Keychain entry whose ACL is bound to a stale signing identity. After upgrading they may have to log in one final time; subsequent restarts work. Do NOT auto-clear the entry — let macOS Security re-bind on the next write.

## Acceptance Criteria

### Functional
- [ ] Phase 0 Track A test passes (proves "silent null on backend failure" is the current behaviour).
- [ ] Phase 0 Track B reproduces "every restart, always" on a locally-built release DMG.
- [ ] After Phase 1 ships: same Track B recipe → bunker session persists across cold boot.
- [ ] nsec users also persist across cold boot on the release DMG.
- [ ] NWC connection persists across cold boot on the release DMG.
- [ ] Multi-account: switching does not cause either account's keychain entry to be lost.

### Non-functional
- [ ] No new dependency added.
- [ ] No password prompt ever appears in the GUI cold-boot path (only legitimate fallback environments may prompt).
- [ ] Release DMG size unchanged within ±100 KB (sanity check on keep-rule scope).
- [ ] No new dialog, settings page, or recovery flow added beyond the single banner line in Phase 2.

### Quality gates
- [ ] Tests 1-6 in Phase 3 all green in CI.
- [ ] Smoke test (#7) passes on macOS DMG, Linux DEB, Windows MSI release artifacts.
- [ ] `./gradlew spotlessApply` clean.
- [ ] Manual verification on a **signed + notarized** macOS DMG (Phase 5).

## Success Metrics

- Zero `BackendNotSupportedException` log lines from `SecureKeyStorage` on macOS release builds.
- Affected user confirms persistence after upgrading (single user-confirmed datapoint is enough — repro is deterministic).
- No new "forced re-login on macOS" reports in the next two releases.

## Dependencies & Risks

| Risk | Likelihood | Mitigation |
|------|------------|-----------|
| Keep rules miss a sub-package that gets reflection-loaded | Low | `-keep class com.github.javakeyring.** { *; }` covers all members under the root package; Phase 3 Test 1 asserts `Keyring.create()` works post-shrink |
| Keychain entries written by pre-fix builds unreadable post-fix due to signing-identity rebind | Medium (one-time, user-visible) | Documented as a one-time re-login; release notes call it out |
| `-dontoptimize` already in place — no interaction with keep rules | Low | Existing config + manual verification |
| Linux/Windows release builds need same fix; lab-testing matrix small | Medium | Extend Phase 4 verification to all three artifacts before merging |
| Merge conflict with active "Account Security Hardening" branch (`fix/account-security-hardening`) | Low | This plan touches `compose-rules.pro` + minor `SecureKeyStorage`/`AccountManager` deltas; coordinate with `docs/plans/2026-05-14-fix-account-security-hardening-plan.md` owner |
| Defense-in-depth scope creep | Medium | Hard-cap on Phase 2 design: one typed failure, one banner line, no recovery UI |

## Open Questions (carried from brainstorm)

- ProGuard mapping file from the affected user's release version — needed to confirm the macOS backend is renamed/stripped post-shrink.
- Are non-bunker (nsec) macOS users on the same release also affected? Expected yes per code path; need one confirmation.
- Is the `~/.amethyst/keys.enc` fallback file ever created on the affected user's system (`ls -la ~/.amethyst/`)? Confirms the fallback was reached.
- Does `security find-generic-password -s amethyst-desktop` show the entry after the first login? Confirms write-side works on the affected box.
- Linux DEB + Windows MSI smoke test: confirm same bug, same fix. (Phase 4 will answer.)
- DMG signing/notarization status of the affected release: hardened-runtime + entitlements file location — for ruling out H2.

## Sources & References

### Origin
- **Brainstorm:** [docs/brainstorms/2026-06-18-fix-macos-bunker-relogin-brainstorm.md](docs/brainstorms/2026-06-18-fix-macos-bunker-relogin-brainstorm.md) — carries forward: reproduce-before-fix policy; Tracks A/B/C; H1/H2/H3 hypothesis ranking; "open questions" list.

### Internal references
- `desktopApp/compose-rules.pro:85-96` — incorrect keep rule (`pt.davidafsilva.apple.**`)
- `desktopApp/compose-rules.pro:148-150` — JNA keep rules (correct, retain as-is)
- `gradle/libs.versions.toml:166` — `java-keyring` dependency
- `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/keystorage/SecureKeyStorage.kt:110-127, 200-218` — failure path (silent null on backend latch)
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/account/AccountManager.kt:240-289` — cold-boot routing
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/account/AccountManager.kt:291-307` — `loadBunkerAccount`
- Related commits: `46caa4d79` (security hardening), `325a1f6f6` (review fixes), `0e431d674` (loading screen), `c0c055e77` (release smoke test pattern for #2819)
- Related plan: [docs/plans/2026-05-14-fix-account-security-hardening-plan.md](docs/plans/2026-05-14-fix-account-security-hardening-plan.md) — overlapping `accounts.json.enc` cold-boot work; coordinate before merge.

### External references
- `java-keyring` README: https://github.com/javakeyring/java-keyring — backend selection mechanism
- Compose Multiplatform 1.11.0 ProGuard release notes — context for why ProGuard was added in v1.09.1

## Unanswered Questions

- Is the existing release-DMG smoke test infrastructure (`c0c055e77`) capable of preserving state between two app launches? Need to read it before extending — may require new harness work.
- Should Phase 2 reasonably be deferred to a follow-up plan? Argument for inlining: tiny, prevents future silent-keychain-failure regressions. Argument against: scope creep on what should be a one-line ProGuard fix.
- Backport policy: is a point-release expected, or does this ride the next minor? Affects urgency of Phase 5.
- Does the `pt.davidafsilva.apple.**` keep rule predate the dep swap, or did somebody copy-paste from another project's config? Git blame on `compose-rules.pro:94` will answer in seconds.
