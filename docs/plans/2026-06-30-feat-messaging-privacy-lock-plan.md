---
title: Messaging Privacy Lock
type: feat
status: active
date: 2026-06-30
origin: docs/brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md
---

# Messaging Privacy Lock

Route-scoped lock on the Messages destination + companion defenses
(notification redaction, screen-capture block, blur-on-unfocus) that prevent
an attentive snooper from reading DMs on an unattended-but-unlocked device.
Industry baseline = Signal screen-lock + WhatsApp Chat Lock, collapsed to
*Messages route only*, OS-credential-only (no Amethyst PIN), device-global,
off-by-default with a one-time prompt.

> All decisions traced back to the brainstorm
> (see brainstorm: `docs/brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md`).

## Enhancement Summary

**Deepened on:** 2026-06-30
**Reviewers:** security-sentinel, architecture-strategist,
code-simplicity-reviewer, performance-oracle, spec-flow-analyzer,
pattern-recognition-specialist

### Key changes incorporated

1. **Security hardening** — six previously-missed content-leak paths
   added to scope: crash-report scrubbing, DM-content log scrub,
   MessagingStyle history flush on first enable, NotificationListener
   payload redaction (main builder, not just `setPublicVersion`),
   bunker decrypt queue drained on re-lock, deep-link race fixed by
   synchronous initial-state read.
2. **Architecture corrections** — `CompositionLocal` instead of
   `commons/jvmMain → desktopApp` import (broke dependency arrow);
   `PrivacyLockPreferences` moved to `commons/jvmAndroid` source set
   (was breaking iOS); native binaries moved to canonical
   `appResources/{macos,linux,windows}/`; `SecureScreenEffect`
   relegated to Android-only (no JVM equivalent).
3. **Performance** — gate uses
   `state.map { it is Locked }.distinctUntilChanged()` to keep chat
   subtree off the recomposition path; `SharingStarted.Eagerly`;
   Activity-level FLAG_SECURE so cold-resume thumbnail is blanked
   from frame 0; blur radius 16dp on overlay layer (not LazyColumn).
4. **Scope cuts** — **Windows Hello native helper deferred** (v1 uses
   `CredUIPromptForCredentials` = OS password, biometric in v2);
   **Linux entirely deferred** (lock toggle shows "Not available on
   Linux yet" banner in v1); 2-level notification (Generic / Full),
   no chooser dialog; first-run card buttons collapsed to Enable / Not
   now (no "Don't ask" persistence).
5. **Edge cases formalized as ACs** — empty-Messages first-run card,
   account-switch = leave-route, BiometricPrompt-up + background
   cancellation, draft persistence via overlay (not unmount), media
   playback does NOT reset idle timer, in-app DM banner suppressed on
   Messages route while locked.
6. **Naming aligned** — `PreferencesPrivacyLockSettings` (mirrors
   `PreferencesHashtagSpamSettings`); JNA under
   `desktopApp/.../service/security/` (mirrors `service/media/`).

## Overview

Five user-visible deliverables, gated behind a single device-global toggle:

1. **Messages-route gate** — biometric / OS-credential prompt before any
   DM UI renders. Re-locks on inactivity (default 5 min, configurable
   Immediate–Never) and on leaving the route.
2. **Notification preview redaction** — 3-level (Full / Sender-only /
   Generic). Auto-bumps to *Generic* on first lock-enable with a chooser.
3. **Screen-capture block** — Android `FLAG_SECURE`, macOS
   `NSWindow.sharingType = .none`, Windows
   `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)`. Linux: not
   available; documented limitation.
4. **Desktop blur-on-unfocus** — Messages content renders blurred while
   the window is unfocused; no PIN re-entry on focus regain.
5. **First-run inline card** — top-of-Messages prompt offering Enable /
   Not now / Don't ask.

## Problem Statement

Amethyst currently has zero protection against snooping on an unattended
device. Anyone who walks past a logged-in install can read DM history,
including NIP-04 + NIP-17 private chats + Marmot group chats. The
existing `SecurityFiltersScreen` is a misnomer — it gates spam/content
filters, not privacy. Notification banners on Android show the sender's
name in the title even when the message body is generic. Recents
thumbnails and screen-sharing tools capture chat content unrestricted.

The intended user is a person who:
- Leaves their device unlocked for short periods (coffee shop, family
  living room, shared workstation).
- Wants reasonable, inconvenient-but-tolerable friction guarding the
  Messages tab specifically.
- Does *not* expect protection against a fully hostile device-possessor
  (rooted Android, attached debugger, filesystem-level access).

## Proposed Solution

A device-global toggle (`PrivacyLockPreferences.lockEnabled`) drives a
`MessagesLockState` StateFlow consumed by a shared `MessagesLockGate`
composable. The gate wraps every Messages-route entry composable on both
Android and Desktop. When state is Locked, the gate intercepts and shows
a lock screen with a single "Unlock" affordance. Tapping the affordance
invokes the platform-specific `CredentialPrompter`, which routes to:

| Platform | Primary | Fallback |
|---|---|---|
| Android | `BiometricPrompt(STRONG \| DEVICE_CREDENTIAL)` | Device PIN/Pattern (built into the prompt) |
| macOS | `LAContext.evaluatePolicy(.deviceOwnerAuthentication)` via ~80-line Swift `.dylib` shim + JNA | OS password (built into LAPolicy) |
| Windows | `CredUIPromptForCredentials` via JNA (User32) → OS password only in v1 | Same |
| Linux | **Lock disabled in v1** — settings shows "Not available on Linux yet" banner | — |

**Why Windows Hello + Linux were deferred** (per deepen-plan
simplicity review): no maintained JVM WinRT projection (would require
a native `.exe` helper, code-signing, MSI bundling — heavy v1 cost
for biometric vs. OS password); polkit Linux flow needs root-installed
policy that breaks Flatpak distribution. Both tracked under Future
Considerations.

On success the gate transitions to Unlocked; an idle timer drives the
re-lock; navigation away from the route flips state to Locked
immediately. Companion defenses (FLAG_SECURE, sharing block,
blur-on-unfocus, notification redaction) are all conditioned on
`lockEnabled` and applied automatically — no extra UI knobs in v1.

## Technical Approach

### Architecture

```
                  ┌─────────────────────────────────┐
                  │   PrivacyLockPreferences        │  java.util.prefs (jvm)
                  │   ─ lockEnabled: Boolean        │   + SharedPreferences (Android)
                  │   ─ inactivityMillis: Long      │
                  │   ─ notificationLevel: …        │
                  │   ─ firstRunPromptDismissed     │
                  │   ─ disabledByMissingCredential │
                  └────────────┬────────────────────┘
                               │
                  ┌────────────▼────────────────────┐
                  │   MessagesLockState              │  StateFlow<LockState>
                  │   ─ Disabled / Locked / Unlocked │
                  │   ─ onUserInteraction()          │
                  │   ─ onLeaveRoute()               │
                  │   ─ onUnlockSuccess()            │
                  │   ─ onCredentialUnavailable()    │
                  └────────────┬────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            │                                     │
   ┌────────▼─────────┐                ┌──────────▼────────┐
   │  Android UI       │                │   Desktop UI       │
   │   (amethyst/)     │                │   (desktopApp/)    │
   ├───────────────────┤                ├────────────────────┤
   │  MessagesLockGate │                │  MessagesLockGate  │
   │  (commons/common) │                │  (commons/common)  │
   │  ↓                │                │  ↓                 │
   │  AndroidCredential│                │  Mac / Win / Linux │
   │  Prompter         │                │  CredentialPrompter│
   │  (expect/actual)  │                │  (expect/actual)   │
   │                   │                │                    │
   │  FLAG_SECURE      │                │  NSWindowSharingNone│
   │  on chat screens  │                │  / WDA_EXCLUDE…    │
   │                   │                │                    │
   │  Notification     │                │  WindowFocus → blur│
   │  redaction        │                │                    │
   └───────────────────┘                └────────────────────┘
```

Single state holder, two UIs subscribed. Mirrors the
`LocalRelayStore` / `LocalRelayMaintenance` pattern already established in
the embedded-relay work
(see `desktopApp/plans/2026-05-09-embedded-local-relay-plan.md`).

### Module map

| Concern | Module / Source set | Path |
|---|---|---|
| `PreferencesPrivacyLockSettings` (mirrors `PreferencesHashtagSpamSettings`; node `com/vitorpamplona/amethyst/privacylock`) | `commons/jvmAndroid` (shared by Android + Desktop, NOT iOS — per `commons/ARCHITECTURE.md` §3) | `commons/.../privacylock/PreferencesPrivacyLockSettings.kt` |
| `MessagesLockState` (StateFlow + idle timer) | `commons/commonMain` | `commons/.../privacylock/MessagesLockState.kt` |
| `DmRedactionLevel` enum (`Generic` / `Full`) + redaction policy (CLI-safe data) | `commons/commonMain` | `commons/.../privacylock/DmRedactionPolicy.kt` |
| `MessagesLockGate` composable + lock-screen UI | `commons/commonMain` | `commons/.../ui/privacylock/MessagesLockGate.kt` |
| `interface CredentialPrompter` + `LocalCredentialPrompter` CompositionLocal (no expect/actual — supplied per platform at App root) | `commons/commonMain` | `commons/.../ui/privacylock/CredentialPrompter.kt` |
| Android `BiometricCredentialPrompter` | `amethyst/.../security/` | `BiometricCredentialPrompter.kt` |
| macOS Touch ID Swift shim source | `desktopApp/src/jvmMain/native/macos/` | `TouchIDShim.swift` |
| macOS Touch ID compiled binary | `desktopApp/src/jvmMain/appResources/macos/` (Compose canonical native-resource root, picked up automatically by `nativeDistributions`) | `libAmethystTouchID.dylib` |
| Windows OS password prompter (v1 — no Hello) | `desktopApp/.../service/security/` | `WindowsPasswordPrompter.kt` (JNA → `User32.CredUIPromptForCredentials`) |
| macOS Touch ID prompter (Kotlin side) | `desktopApp/.../service/security/` | `MacTouchIdPrompter.kt` (JNA → `libAmethystTouchID.dylib`) |
| Desktop `CredentialPrompter` provider (selects Mac/Win/no-op) | `desktopApp/.../service/security/` | `DesktopCredentialPrompterFactory.kt` (lives in `desktopApp`, NOT `commons/jvmMain` — `PlatformInfo` ownership stays where it is) |
| `FrameWindowScope.applyWindowCaptureBlock(enabled)` | `desktopApp/.../platform/` | `WindowCaptureBlock.kt` (alongside `applyNativeWindowChrome` in `PlatformTheme.kt`) |
| Desktop window-focus StateFlow + `LocalWindowFocus` | `desktopApp/.../platform/` | `WindowFocusOwner.kt` |
| `LocalMessagesLockState` CompositionLocal (provided once at App root on both platforms) | `commons/commonMain` | `commons/.../privacylock/MessagesLockState.kt` (companion) |
| Android settings UI + first-run card | `amethyst/.../settings/` + `amethyst/.../chats/` | `PrivacyLockSettingsScreen.kt`, `MessagesFirstRunCard.kt` |
| Desktop settings UI (Column + Card pattern from `LocalRelaySettingsScreen`, NO Scaffold) + first-run card | `desktopApp/.../settings/` + `desktopApp/.../chats/` | `PrivacyLockSettingsScreen.kt`, `MessagesFirstRunCard.kt` |
| Notification redaction call site (Android-only — `NotificationUtils.kt` doesn't exist on Desktop) | `amethyst/.../service/notifications/` | edit `NotificationUtils.kt:370-510` to consume `DmRedactionPolicy` from commons |
| Idle-timer modifier (`Modifier.resetIdleOnInteraction()` — single root attach, not per-composable) | `commons/commonMain` | `commons/.../ui/privacylock/IdleTimerModifier.kt` |
| FLAG_SECURE wrapper (**Android-only** — no expect/actual; desktop uses window-level `WindowCaptureBlock`) | `amethyst/.../security/` | `SecureScreenEffect.kt` |
| Crash-report DM scrubber (NEW — security hardening) | `amethyst/.../service/crashreports/` | edit `ReportAssembler.kt` to strip `Throwable.message` when origin package is `nip04Dm` / `nip17Dm` / `marmot` |
| DM-content log audit (NEW — security hardening) | `amethyst/.../service/notifications/` + `commons/.../bunker/` | grep + edit: `EventNotificationConsumer.kt:349,392,435,491,549,695`, `RemoteSignerManager.kt:53` — strip body fields from `Log.d` |

`commons/ARCHITECTURE.md` governs package taxonomy — new package
`commons/.../privacylock/` for state + prefs + policy,
`commons/.../ui/privacylock/` for Compose surfaces.

**Pattern note (new):** `LocalMessagesLockState` + `LocalCredentialPrompter`
introduce app-global CompositionLocals provided once at the App root.
Mirrors the existing precedent `LocalRelayStore`-style provision in
`desktopApp/Main.kt` (single instantiation site, app-global, not
per-Window — required so multi-window desktop in the future doesn't
bypass the lock).

### Implementation Phases

#### Phase 1: Foundation (state + preferences + tests)

Lay the cross-platform spine before touching either UI. Output: a
testable, headless state machine with unit-test coverage.

**Critical requirement (per deepen perf finding):** initial `LockState`
value MUST be derived synchronously from `prefs.lockEnabled` BEFORE
the first composition runs. `setContent {}` must see `Locked` as the
seed value the first time it composes — never `Unlocked` followed by
an async update. This is the foundation of the deep-link race fix
(see Security Hardening below).

- New files:
  - `commons/src/commonMain/.../privacylock/MessagesLockState.kt`
    - `sealed class LockState { Disabled; Locked; Unlocked }`
    - `class MessagesLockState(prefs, clock, scope)`
      - `val state: StateFlow<LockState>` — backed by
        `MutableStateFlow(seedFromPrefsSynchronously())`
      - Uses `SharingStarted.Eagerly` (NOT `WhileSubscribed` — needed
        because the notification path reads `lockEnabled` outside any
        UI collector, per deepen perf finding #4)
    - `fun onUserInteraction()` — idempotent
    - `fun onLeaveRoute()` — idempotent; transitions Unlocked→Locked
    - `fun onUnlockSuccess()` — transitions Locked→Unlocked, restarts idle timer
    - `fun onCredentialUnavailable()` — single-arg (no reason variants
      per simplicity review); transitions to Disabled, persists flag,
      no-op if already Disabled
    - `companion object { val LocalMessagesLockState: ProvidableCompositionLocal<MessagesLockState> }`
  - `commons/src/commonMain/.../privacylock/InactivityTimer.kt`
    - `enum class InactivityTimer(val millis: Long?)`:
      `OneMin`, `FiveMin`, `FifteenMin`, `OneHour`, `Never`.
      Default `FiveMin`. **`Immediate` dropped** (per simplicity review
      — redundant with leave-route trigger.)
  - `commons/src/commonMain/.../privacylock/DmRedactionPolicy.kt`
    - `enum class DmRedactionLevel { Generic; Full }` (2 levels, not 3
      — per simplicity review; "Sender only" was niche middle ground)
    - `fun resolveLevel(lockEnabled: Boolean, userChoice: DmRedactionLevel?): DmRedactionLevel`
      — when `lockEnabled` and user hasn't explicitly chosen, returns
      `Generic`. When `!lockEnabled`, returns `Full`.
  - `commons/src/jvmAndroid/.../privacylock/PreferencesPrivacyLockSettings.kt`
    - Mirrors `PreferencesHashtagSpamSettings` shape (class taking
      `prefs`, exposes `StateFlow` mutators).
    - Backing storage: java.util.prefs on Desktop /
      SharedPreferences on Android (each platform sets up its own
      Preferences instance and hands it in — `jvmAndroid` source set
      doesn't pick the storage backend).
    - Keys: `lockEnabled`, `inactivityTimerOrdinal`,
      `redactionLevelOrdinal`, `firstRunCardSeen`. **Removed:**
      `disabledByMissingCredential` (per simplicity review — re-check
      via `BiometricManager.canAuthenticate()` on settings render).
  - `commons/src/commonTest/.../privacylock/MessagesLockStateTest.kt`
    - 5 unit tests (down from 12 per simplicity review — start small):
      1. Idle expiry fires lock
      2. Leave-route locks immediately
      3. Cold-start with `lockEnabled=true` seeds to Locked
      4. Settings cascade — toggle off transitions Locked→Disabled
      5. Never timer doesn't fire

- Acceptance:
  - [ ] `./gradlew :commons:jvmTest --tests "*MessagesLockStateTest*"` green
  - [ ] State machine is idempotent on duplicate triggers
  - [ ] No new deps required; uses kotlinx.coroutines.flow only
  - [ ] Initial value seeded synchronously (no flash on cold start)

#### Phase 2: Android lock + UI + notification redaction + leak audit

Wire the gate, settings, and notification redaction on Android. This
phase incorporates **the security-hardening leak fixes** surfaced
during deepen review (crash, logs, MessagingStyle history,
NotificationListener payload).

- New files:
  - `commons/.../ui/privacylock/MessagesLockGate.kt` (commonMain) —
    gate hosts the lock overlay ONLY; content lambda passed in
    unchanged. State read uses
    `state.map { it is Locked }.distinctUntilChanged().collectAsStateWithLifecycle(initialValue = state.value is Locked)`
    so the chat subtree never recomposes on idle-tick non-flips.
    Gate selects branch SYNCHRONOUSLY in composition
    (`when { locked -> LockOverlay(); else -> content() }`) — no
    `LaunchedEffect` guard (per security finding #1).
  - `commons/.../ui/privacylock/CredentialPrompter.kt` (commonMain) —
    `interface CredentialPrompter { suspend fun prompt(reason: String): PromptResult }`,
    `LocalCredentialPrompter` CompositionLocal.
  - `amethyst/.../security/BiometricCredentialPrompter.kt` — wraps
    `BiometricPrompt(STRONG | DEVICE_CREDENTIAL)`. Reuse pattern from
    `UpdateZapAmountDialog.kt:428-479`. Provided into
    `LocalCredentialPrompter` at the `AmethystApp` composition root.
    Error mapping (single-reason variant per simplicity review):
    - `ERROR_USER_CANCELED` → stay locked, no toast
    - `ERROR_LOCKOUT` → "Try again in 30 sec" toast, stay locked
    - `ERROR_LOCKOUT_PERMANENT` / `ERROR_NO_HARDWARE` /
      `ERROR_NONE_ENROLLED` → `state.onCredentialUnavailable()`
  - `amethyst/.../security/SecureScreenEffect.kt` (Android-only — no
    expect/actual per architecture review). Toggles
    `WindowManager.LayoutParams.FLAG_SECURE`.
  - `commons/.../ui/privacylock/IdleTimerModifier.kt` (commonMain) —
    single `Modifier.resetIdleOnInteraction(state)` using
    `Modifier.pointerInput(Unit) { awaitPointerEventScope { while(true){ awaitPointerEvent(PointerEventPass.Initial); state.onUserInteraction() } } }`.
    Initial pass = observe without consuming. Per-keypress TextField
    reset wired via a separate `onValueChange` adapter at the compose
    sites that have text fields.

- Edited files:
  - **`amethyst/.../MainActivity.kt`** — set
    `Window.addFlags(FLAG_SECURE)` early in `onCreate` (window-level,
    not per-screen DisposableEffect) when `prefs.lockEnabled == true`.
    This closes the recents cold-reattach thumbnail leak window
    (security finding #6 + perf finding #6). Per-screen
    `SecureScreenEffect` stays as belt-and-suspenders for when
    chat is visible but lock toggle was just flipped on.
  - `amethyst/.../ui/screen/loggedIn/chats/rooms/MessagesScreen.kt`
    — wrap top-level composable in `MessagesLockGate`; attach
    `Modifier.resetIdleOnInteraction(state)` once at the route root
    (per architecture review's "single root attach" recommendation).
  - `amethyst/.../ui/screen/loggedIn/chats/privateDM/ChatroomScreen.kt`
    — wrap content in `SecureScreenEffect(enabled = lockEnabled)`;
    treated as inside-gate so deep-link from notification still
    transits the parent-route gate.
  - `amethyst/.../ui/screen/loggedIn/chats/marmotGroup/MarmotGroupScreen.kt`
    — same. Verify gate wraps at route level (not deep inside the
    composable tree), per architecture review.
  - `amethyst/.../service/notifications/NotificationUtils.kt` —
    consume `DmRedactionPolicy` from commons. **Redact the MAIN
    builder, not just `setPublicVersion`** (security finding #5 —
    NotificationListener apps see the main `Notification.extras`):
    - When `level == Generic`: `setContentTitle("Amethyst")`,
      `setContentText("New message")`, do NOT add
      `MessagingStyle.addMessage(body, …)` (which persists across
      updates and leaks pre-enable history).
    - Disable inline-reply `RemoteInput` when level=Generic (would
      otherwise let the system quote the original message — security
      finding #13).
    - On first lock-enable, call
      `NotificationManagerCompat.cancel(DM_GROUP_KEY)` to flush
      pre-enable MessagingStyle history (security finding #4).
    - Bypass `Log.d("$content $title …")` style logging in DM paths
      (security finding #3) — survey lines 349, 392, 435, 491, 549,
      695 in `EventNotificationConsumer.kt` and strip body fields.
  - **`amethyst/.../service/crashreports/ReportAssembler.kt`** — NEW
    security hardening (finding #2). Strip `Throwable.message` to
    class-name-only when origin package is `nip04Dm`, `nip17Dm`, or
    `marmot`. Always-on (not gated on `lockEnabled` — DM plaintext in
    crash reports is never a feature).
  - **`commons/.../bunker/RemoteSignerManager.kt`** — on
    `MessagesLockState` transition Unlocked→Locked, drain the bunker
    decrypt response queue (security finding #7). Cancel in-flight
    `Channel<Response>` subscriptions for DM decrypt requests.
- New settings:
  - `amethyst/.../ui/screen/loggedIn/settings/PrivacyLockSettingsScreen.kt`
    - Toggle: Lock Messages (calls `BiometricManager.canAuthenticate()`
      on render — if unavailable, toggle disabled + banner)
    - Spinner: Lock after `[1 min / 5 min / 15 min / 1 hour / Never]`
      (5 values, default 5 min — per simplicity review, `Immediate`
      dropped as redundant with leave-route)
    - Toggle: Hide notification preview (2-level: On = Generic, Off =
      Full — chooser dialog dropped per simplicity review)
    - Inline hint: "Notifications hide content while lock is on."
    - Banner (when canAuthenticate returns NO_HARDWARE / NONE_ENROLLED):
      "Lock disabled — no biometric or device credential available."
    - Copy: "Lock applies to Messages only. Other parts of the app
      stay open. Does NOT protect against rooted devices, debuggers,
      filesystem access, apps with notification-listener permission,
      or screen-recording apps you've granted access — your nsec is
      still stored as it is today." (Honest threat-model copy per
      security finding #15.)
  - Add link from existing `SecurityFiltersScreen.kt` to
    `PrivacyLockSettingsScreen`. Keep `SecurityFiltersScreen` name to
    minimize churn; add a "Privacy" sub-link clearly labeled.
- First-run card:
  - `amethyst/.../ui/screen/loggedIn/chats/rooms/MessagesFirstRunCard.kt`
    — inline `Card` at top of `MessagesScreen`, structurally modeled
    on `OfflineBanner.kt` from the embedded-relay work (per pattern
    review). Two buttons (per simplicity review): **Enable** /
    **Not now**. Auto-suppress after `firstRunCardSeen` is set OR after
    `lockEnabled` flips to true. (No "Don't ask" plumbing.) Renders
    independently of DM list state — visible even on empty Messages
    (per spec-flow gap #1).

- Acceptance:
  - [ ] `./gradlew :amethyst:assembleDebug` succeeds
  - [ ] Manual: gate appears, biometric unlocks, idle timer locks,
        navigation away locks immediately
  - [ ] Recents-screen thumbnail blank when in Messages (verify on
        BOTH cold cold-start and warm resume — security finding #6)
  - [ ] Notification preview hidden by default after enable
  - [ ] **First-enable flushes MessagingStyle history** (security AC)
  - [ ] **NotificationListenerService apps see redacted main builder**
        when level=Generic (verify with a side-loaded NL app)
  - [ ] **Crash report from an injected DM-decode error contains
        no plaintext** (security AC)
  - [ ] **`logcat | grep -i 'dm\|chat'` produces no plaintext** while
        DMs flow (security AC)
  - [ ] First-run card displays on empty Messages tab
  - [ ] **Deep-link from notification: chat content NEVER paints
        before gate** (security AC — write Compose-test screenshot
        diff on the transition frame)

#### Phase 3: Desktop lock + UI + macOS Touch ID + Windows OS password

**Scope cut per deepen simplicity review**: Linux is fully deferred
to v2 (toggle disabled with "Not available on Linux yet" banner);
Windows ships with OS password only in v1 (Windows Hello deferred —
no JVM-native WinRT projection). macOS gets Touch ID via the Swift
shim — small enough to justify (~80 LOC).

- New files:
  - `desktopApp/.../service/security/DesktopCredentialPrompterFactory.kt`
    — selects `MacTouchIdPrompter` on macOS,
    `WindowsPasswordPrompter` on Windows, returns a no-op disabled
    prompter on Linux. Provided into `LocalCredentialPrompter` at the
    `App()` root in `Main.kt`.
  - `desktopApp/.../service/security/MacTouchIdPrompter.kt` —
    JNA into `libAmethystTouchID.dylib.amethyst_touchid_authenticate`.
  - `desktopApp/.../service/security/WindowsPasswordPrompter.kt` —
    JNA into `User32.CredUIPromptForCredentials`. **No Windows Hello
    in v1** — drops native helper + signing + MSI bundling cost.
    Documented in copy: "Windows uses your account password until we
    add Windows Hello support."
  - `desktopApp/src/jvmMain/native/macos/TouchIDShim.swift` — source,
    ~80 lines:
    ```swift
    import LocalAuthentication

    @_cdecl("amethyst_touchid_authenticate")
    public func authenticate(reason: UnsafePointer<CChar>) -> Int32 {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err)
        else { return -1 }
        let sem = DispatchSemaphore(value: 0)
        var ok = false
        ctx.evaluatePolicy(.deviceOwnerAuthentication,
            localizedReason: String(cString: reason)) { success, _ in
            ok = success
            sem.signal()
        }
        sem.wait()
        return ok ? 0 : 1
    }
    ```
    Hardcode `reason` string on the Kotlin side (security finding #14
    — no user-controlled strings to the OS prompt). Compiled with
    `swiftc -emit-library -o libAmethystTouchID.dylib` on macOS hosts.
  - `desktopApp/src/jvmMain/appResources/macos/libAmethystTouchID.dylib`
    — committed pre-built artifact. Compose Desktop's
    `appResourcesRootDir` is the canonical location (already used by
    VLC bundles per `desktopApp/build.gradle.kts:107,173-175`), so
    `nativeDistributions` picks it up automatically. **No new Gradle
    tasks required** (per architecture review #8). Recompile recipe
    documented separately at
    `desktopApp/plans/2026-06-30-privacy-lock-native-shims-recipe.md`.
  - `desktopApp/.../platform/WindowFocusOwner.kt` —
    `LocalWindowFocus: ProvidableCompositionLocal<StateFlow<Boolean>>`.
    Sourced from `window.addWindowFocusListener` events emitted into
    a `MutableStateFlow`.
  - `desktopApp/.../platform/WindowCaptureBlock.kt` —
    `fun FrameWindowScope.applyWindowCaptureBlock(enabled: StateFlow<Boolean>)`:
    - macOS: JNA into `NSWindow.setSharingType(0)` — extract JNA
      pattern from existing `applyNativeWindowChrome()` in
      `PlatformTheme.kt:87`. Apply via
      `LaunchedEffect(enabled) { ... }` so JNA call fires only on flip,
      not per recomposition (perf finding #5). Use `DisposableEffect`
      to restore `sharingType = normal` on disable.
    - Windows: JNA into
      `User32.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE)`.
      Same effect plumbing.
    - Linux: no-op.
- Edited files:
  - `desktopApp/.../Main.kt` — inside `Window { ... }` block (line 316),
    invoke `applyWindowCaptureBlock(lockEnabledFlow)` next to existing
    `applyNativeWindowChrome()`. Construct the app-global
    `MessagesLockState` ONCE here and provide both
    `LocalMessagesLockState` and `LocalCredentialPrompter` at the App
    root. Multi-window correctness (architecture review #5).
  - `desktopApp/.../ui/chats/DesktopMessagesScreen.kt`,
    `ChatPane.kt`, `ConversationListPane.kt` — wrap in
    `MessagesLockGate`. Apply `Modifier.resetIdleOnInteraction(state)`
    at the route root only.
  - `desktopApp/.../ui/chats/ChatPane.kt` — apply
    `Modifier.blur(16.dp, BlurredEdgeTreatment.Unbounded)` on an
    OVERLAY layer (not on the LazyColumn), driven by
    `LocalWindowFocus`. Cap at 16dp (perf finding #2). On macOS 15+
    and Linux, default blur radius even when focused (because the
    capture block is broken/absent — partial compensation per
    security findings #9 and #10).
  - `desktopApp/build.gradle.kts` — only addition is the macOS
    `infoPlist` block to add `NSFaceIDUsageDescription`. Native
    binaries auto-bundled via `appResources/macos/`.
  - `desktopApp/.../ui/settings/PrivacyLockSettingsScreen.kt` —
    follows `LocalRelaySettingsScreen` shape (Column + Card, NO
    Scaffold — pattern review). Same toggles/spinners as Android.
    On Linux, the whole settings group is replaced by a banner: "Not
    available on Linux yet." On macOS 15+, an info row: "Screen
    capture protection is limited on macOS 15+." On Windows, info row:
    "Windows uses your account password — Hello support coming later."

- Acceptance:
  - [ ] `./gradlew :desktopApp:run` on macOS: Touch ID prompt appears,
        success unlocks, cancel keeps locked
  - [ ] `./gradlew :desktopApp:run` on Windows: OS-password prompt
        appears (CredUI dialog), success unlocks
  - [ ] `./gradlew :desktopApp:run` on Linux: lock toggle in settings
        is disabled with banner; Messages route shows no gate
  - [ ] DMG / MSI builds succeed (no new MSI signing required since
        Hello helper is deferred)

#### Phase 4: Desktop companion defenses + blur + first-run

- Edited files:
  - `desktopApp/.../ui/chats/DesktopMessagesScreen.kt` — apply
    `Modifier.blur(...)` driven by `LocalWindowFocus`.
  - `desktopApp/.../ui/chats/MessagesFirstRunCard.kt` — new inline card
    composable mirroring Android.
- Window-focus wiring:
  - `Main.kt` Window block: emit window-focus events into
    `LocalWindowFocus` StateFlow via
    `window.addWindowFocusListener { ... }`.
- macOS Sequoia/Tahoe caveat:
  - `PrivacyLockSettingsScreen` shows an info row when running on macOS
    15+: "Screen sharing protection is limited on macOS 15+ due to a
    system-level change — relying on this for hostile environments is
    not advised."
- Linux caveat:
  - On Linux, the same row reads: "Screen capture protection is not
    available on Linux. Lock + blur + notification redaction still
    apply."

- Acceptance:
  - [ ] Alt-tab away from window → Messages content blurs
  - [ ] Alt-tab back → blur clears, no re-prompt
  - [ ] macOS: screenshot via ⌘+Shift+3 produces blank on the Messages
        window (best-effort on 15+ — verify and document)
  - [ ] Windows: PrtScn + clipboard paste shows blank Messages window
  - [ ] Linux: row says "not available"

#### Phase 5: Polish, l10n, docs, manual testing

- Translation strings — add ~20 strings to Android
  `amethyst/src/main/res/values/strings.xml` and desktop
  `desktopApp/.../resources/messages.properties`:
  - lock_messages_title, lock_messages_subtitle, lock_after,
    immediate/1min/5min/15min/1hour/never, notification_preview,
    full/sender_only/hidden, lock_screen_title, lock_screen_unlock_button,
    first_run_card_title, first_run_card_enable, first_run_card_not_now,
    first_run_card_dont_ask, lock_disabled_no_credential_banner,
    capture_protection_linux_unavailable,
    capture_protection_macos15_caveat
- Crowdin auto-sync handles propagation (PR #3142 just merged
  translations — verify pipeline)
- Final `./gradlew spotlessApply`
- Update `commons/ARCHITECTURE.md` with new `privacylock/` package
- Update `MEMORY.md` index entry for this work
- Hand-written manual testing sheet (post-implementation deliverable)

## Security Hardening Additions (from deepen review)

Six previously-missed leak paths now in scope. Each maps to a concrete
edit elsewhere in the plan; this section is the punch list.

| # | Leak path | Severity | Mitigation | Status in plan |
|---|---|---|---|---|
| H1 | Deep-link race — `LaunchedEffect` guard insufficient; chat content can flash 1 frame before gate paints | HIGH | Gate selects branch SYNCHRONOUSLY in composition; `MessagesLockState.state` seeded synchronously from prefs before `setContent`; Compose-test screenshot diff asserts no-flash invariant | Phase 1 (seed) + Phase 2 (gate composable + AC) |
| H2 | Crash reports serialize `Throwable.message` containing decrypted DM plaintext (Jackson parse errors echo input; NIP-44 errors echo bytes) | HIGH | `ReportAssembler.kt` strips `Throwable.message` to class-name-only when origin package is `nip04Dm`, `nip17Dm`, or `marmot`. Always-on. | Phase 2 (new edit) |
| H3 | `Log.d` calls in DM notification path echo decrypted content; Proguard does not reliably strip in release; visible via `adb logcat` and NotificationListener apps with logging plugins | HIGH | Audit lines 349, 392, 435, 491, 549, 695 in `EventNotificationConsumer.kt` and `RemoteSignerManager.kt:53`; strip body fields, keep only event ID + length | Phase 2 (new edit) |
| H4 | `NotificationCompat.MessagingStyle.addMessage(...)` persists pre-enable history in system NotificationManager; lockscreen redaction does not affect it | HIGH | On first lock-enable, call `NotificationManagerCompat.cancel(DM_GROUP_KEY)` to flush; do not call `addMessage(body, …)` when level=Generic | Phase 2 (edit `NotificationUtils.kt`) |
| H5 | NotificationListenerService apps see the full `Notification` payload (`extras.text`, MessagingStyle); `setPublicVersion` only affects lockscreen | HIGH | Redact the MAIN builder when level=Generic — `setContentText("New message")`, omit `MessagingStyle.addMessage`, disable inline-reply `RemoteInput` (which would otherwise let the system quote the original message — finding #13) | Phase 2 (edit `NotificationUtils.kt`) |
| H6 | NIP-46 bunker decrypt responses queued on a channel; on re-lock, in-flight responses can deliver plaintext to a recomposed (now-locked) chat scope | MEDIUM | On `MessagesLockState` Unlocked→Locked transition, cancel in-flight bunker decrypt request subscriptions and clear the response channel | Phase 2 (edit `RemoteSignerManager.kt`) |

These are NOT optional polish — without them the v1 lock fails to
deliver even its narrow "cosmetic shoulder-surf" promise honestly.

## Alternative Approaches Considered

1. **App-wide lock** (not just Messages route). Rejected during brainstorm
   — user explicitly chose Messages-only. Higher friction for low gain.
2. **In-app Amethyst PIN**. Rejected — OS device credentials are better
   UX and better security (no PIN reuse, no recovery to build).
3. **Per-conversation lock** (WhatsApp Chat Lock-style). Rejected for
   v1 as scope creep. Could layer on later as a per-`Chatroom` flag.
4. **nsec encryption-at-rest with PIN-derived key**. Recognized as the
   cryptographic upgrade that would close the "device-possessor"
   threat. Explicitly deferred to Phase 2 (separate brainstorm) until
   v1 ships and we have user feedback.
5. **Background / system-lock as trigger**. User chose to skip — the
   inactivity timer (default 5 min) subsumes it within the same window.
6. **Rococoa for macOS Touch ID** (vs Swift `.dylib` shim). Rejected —
   shim is smaller (~80 LOC) and avoids pulling in a 5MB framework
   dependency. JNA + a single `.dylib` matches the existing
   `MacOsVlcDiscoverer.kt` JNA pattern.

## System-Wide Impact

### Interaction Graph

User taps Messages tab →
`NavController.navigate("messages")` →
`MessagesScreen` composes →
`MessagesLockGate` consumes `MessagesLockState.state` →
- If `Disabled` or `Unlocked` → render content + apply
  `SecureScreenEffect` + (desktop) `Modifier.blur(focusState)`
- If `Locked` → render lock screen with "Unlock" button →
  user taps → `CredentialPrompter.prompt(...)` →
  (Android) `BiometricPrompt.authenticate(...)` →
  (macOS) JNA → `libAmethystTouchID.dylib.amethyst_touchid_authenticate(...)` →
  `LAContext.evaluatePolicy(...)` →
  callback success → `MessagesLockState.onUnlockSuccess()` →
  state flips to Unlocked → recomposition → content renders.

User receives DM while app foregrounded but on a different route →
`EventNotificationConsumer.consume(event)` →
- If `lockEnabled` → `sendDMNotification(level = current)` →
  `NotificationCompat.Builder.setPublicVersion(...)` →
  user sees redacted preview on lock screen.

User taps DM notification →
`MainActivity.onNewIntent(intent)` →
NavController deep-link to chatroom →
`MessagesScreen` route entered → gate intercepts (because state was Locked
from a prior leave-route event) → unlock prompt → on success → chatroom
shows.

### Error Propagation

| Origin | Error | Handled at | Result |
|---|---|---|---|
| BiometricPrompt | `ERROR_USER_CANCELED` | `CredentialPrompter.android.kt` | Swallowed; stay locked |
| BiometricPrompt | `ERROR_LOCKOUT` | same | Toast "Try again in 30 sec"; stay locked |
| BiometricPrompt | `ERROR_LOCKOUT_PERMANENT` | same | `onCredentialUnavailable(LockoutPermanent)` → state → Disabled + banner |
| BiometricPrompt | `ERROR_NO_HARDWARE` | same | `onCredentialUnavailable(NoCredential)` → same |
| TouchIDShim | `canEvaluatePolicy = false` | `MacTouchIdPrompter` | `onCredentialUnavailable(NoCredential)` |
| TouchIDShim | `evaluate → false` (user cancel) | same | Stay locked, no toast |
| WindowsHello helper | `ProcessBuilder` non-zero exit | `WindowsHelloPrompter` | If exit code = 2 (WinRT unavailable) → fall back to `CredUIPromptForCredentials` |
| Linux password | wrong password | `LinuxPasswordPrompter` | Toast "Wrong password"; stay locked. Throttle: 5s lockout after 3 attempts. |
| `MessagesLockState` | preferences write fails | `PrivacyLockPreferences` | Log + propagate (caller treats as best-effort) |
| Notification redaction | level mismatched on platform change | `NotificationUtils` | Falls back to Generic — safe default |

### State Lifecycle Risks

| Risk | Mitigation |
|---|---|
| App killed mid-unlock leaves state inconsistent | State is in-memory; cold start re-reads `lockEnabled` from prefs → if enabled, starts in `Locked`. Fail-safe by default. |
| Idle timer fires while user is typing a message | Timer resets on `keyboardCharType` interaction events; types via `Modifier.pointerInput` + composing-text interaction. |
| Switching Nostr accounts mid-session | Setting is device-global → state persists. Users expect this. |
| Settings change "disable lock" while screen is Locked | State holder receives prefs flow update → transitions Locked → Disabled. Gate transparently shows content. |
| Deep-link from notification while locked | Notification intent → MainActivity → NavController.handleDeepLink → MessagesScreen route → gate intercepts. Critical: ensure no chat content is rendered before gate paints (use `LaunchedEffect` initial composition guard). |
| Restoring app from recents (Android) while Locked | Lock state persists in `MessagesLockState`. Recents thumbnail is FLAG_SECURE-blanked. |
| Window-focus oscillation (rapid alt-tab) | Debounce blur transition by 100ms to avoid visual flicker. |

### API Surface Parity

| Surface | Affected? | Notes |
|---|---|---|
| `amy` CLI | No | CLI has no Compose UI. `PrivacyLockPreferences` is exposed but CLI commands don't gate by it. Future: `amy lock status` for debugging. |
| Android Quick-Tile widgets | No widgets today |
| Wear OS companion | None today |
| Search (global) | Yes — verify | Audit `MessagesSearchScreen` / global search results: when locked, must NOT surface DM hits. Add filter `isMessagesLocked` to search result aggregation. |
| Notifications service | Yes | `EventNotificationConsumer` reads `lockEnabled` + redaction level. |
| Marmot group chats | Yes | Same gate via commons composable. |

### Integration Test Scenarios

1. **Lock + deep link**: Tap DM notification while app cold, lock
   enabled. Expected: Messages route opens, gate intercepts BEFORE
   chatroom content visible, unlock → chatroom shows. Failure mode:
   chatroom flashes content during navigation transition.
2. **Lock + spec drift**: User toggles lock → toggles inactivity to
   Never → leaves Messages → returns. Expected: Locked (because
   leave-route is a separate trigger). Failure: stays Unlocked because
   timer = Never.
3. **Lock + credential revocation**: User removes fingerprint in
   Android settings while app open. Next gate entry expected: prompt
   appears, fails with NONE_ENROLLED → state → Disabled + banner
   shows on settings.
4. **Lock + multi-window (Android tablet)**: Lock active, Messages in
   left pane, Feed in right. Expected: only left pane shows lock.
5. **Lock + spam tab**: Locked + user navigates Known→New tab inside
   Messages. Expected: no re-prompt within same Messages route session.
6. **Lock + send-receive parity**: Lock active, user unlocks, sends
   a DM, recipient sees it normally. Outbound path unaffected.
7. **Notification preview level after lock disable**: User enables
   lock → Generic notifications. Disables lock → notifications
   revert to Full (no sticky Generic).

## Acceptance Criteria

### Functional

- [ ] Messages route gated by `MessagesLockGate` on Android + Desktop
- [ ] Idle timer (default 5 min) auto-locks while in Messages
- [ ] Navigation away from Messages triggers immediate re-lock
- [ ] Account switch counts as leave-route (force re-lock — spec-flow #2)
- [ ] Inactivity timer ignores incoming DMs (per resolved Q)
- [ ] Inactivity timer resets on user input (scroll/tap/keypress) but
      NOT on media playback (spec-flow #13)
- [ ] Marmot group chats inherit the gate (per resolved Q)
- [ ] Android: `BiometricPrompt(STRONG | DEVICE_CREDENTIAL)` succeeds
- [ ] macOS: Touch ID via Swift shim succeeds; password fallback works
- [ ] Windows: `CredUIPromptForCredentials` (OS password) succeeds
      — **no Windows Hello in v1**
- [ ] Linux: lock toggle disabled with "Not available on Linux yet"
      banner — **lock entirely deferred in v1**
- [ ] Activity-level FLAG_SECURE applied at MainActivity.onCreate when
      `lockEnabled = true` (closes cold-resume recents window)
- [ ] Per-screen FLAG_SECURE belt-and-suspenders via SecureScreenEffect
- [ ] macOS NSWindow sharingType set when lock enabled (with macOS 15+
      caveat documented in-app — both ScreenCaptureKit and
      `screencapture(1)` ignore it on Sequoia/Tahoe)
- [ ] Windows SetWindowDisplayAffinity set when lock enabled
- [ ] Desktop blur-on-unfocus active while Messages visible (16dp,
      overlay layer not LazyColumn)
- [ ] macOS 15+ and Linux: blur applied even while focused as partial
      capture-block compensation
- [ ] Notification preview redaction respects level (Generic / Full —
      2 levels per simplicity review)
- [ ] **Notification main builder redacted when level=Generic** (not
      just `setPublicVersion`) — security H5
- [ ] **Inline-reply `RemoteInput` disabled when level=Generic** — security H13
- [ ] **MessagingStyle history flushed on first lock-enable** — security H4
- [ ] **Crash reports strip `Throwable.message` for DM-origin packages** — security H2
- [ ] **No `Log.d` calls echo decrypted DM content** (verified by
      `logcat | grep` audit during testing) — security H3
- [ ] **Bunker decrypt response queue drained on re-lock** — security H6
- [ ] First-run inline card appears once; auto-suppresses after Enable
      or after the card is shown (2 buttons: Enable / Not now — no
      "Don't ask" persistence per simplicity review)
- [ ] First-run card renders on empty Messages tab (spec-flow #1)
- [ ] Fallback: credential-unavailable → toggle disabled + banner
      (no persisted flag — re-checked via `BiometricManager.canAuthenticate()`)
- [ ] Settings UI on both platforms (Android Scaffold, Desktop Column
      + Card per pattern review)
- [ ] Global search does not surface DM content while locked
      (`SearchBarViewModel` + `AdvancedSearchBarState` + Desktop
      search panel — filter aggregation on kinds 4 / 14 / 1059 / 443)
- [ ] In-app DM banner suppressed on Messages route while Locked
      (spec-flow #7)
- [ ] Draft persistence — text TextFields keep their value across a
      lock cycle (gate overlays content, never disposes it — spec-flow #8)
- [ ] BiometricPrompt dismissed + state reset on
      `Lifecycle.STOP` while prompt is up (spec-flow #4)
- [ ] Disable-while-locked: toggle off in settings → state
      Locked → Disabled within one frame (spec-flow #9)
- [ ] Concurrent triggers (leave-route + idle-timer same tick) are
      idempotent (spec-flow #5)
- [ ] **Deep-link from notification: chat content NEVER paints
      before gate** — security H1; Compose-test asserts this

### Non-Functional

- [ ] No measurable startup-time regression (≤ +20ms cold start)
- [ ] Gate composition adds ≤1 recomposition per state change
- [ ] No new ANRs / dropped frames during lock transitions (verified
      with macrobenchmark on Android, frame-rate inspection on desktop)
- [ ] FLAG_SECURE applied via DisposableEffect, removed on screen exit
      (no permanent secure-flag leak)
- [ ] No credentials cached in memory beyond the unlock callback
- [ ] All new code paths spotless-clean and covered where applicable

### Quality Gates

- [ ] `./gradlew :commons:jvmTest --tests "*privacylock*"` green
- [ ] `./gradlew :amethyst:assembleDebug` green
- [ ] `./gradlew :desktopApp:compileKotlin` green
- [ ] `./gradlew :desktopApp:packageDmg` green on macOS host
- [ ] `./gradlew :desktopApp:packageMsi` green on Windows host
- [ ] `./gradlew :desktopApp:packageDeb` green on Linux host
- [ ] `./gradlew spotlessApply` clean
- [ ] Manual testing sheet executed and signed off

## Success Metrics

- **Adoption proxy**: prefs node read counts (if telemetry exists; if
  not, GitHub issue volume tagged `privacy-lock`)
- **Stability proxy**: zero "stuck-locked" support reports during the
  first 30 days post-release
- **Discoverability proxy**: < 10% of users dismiss the first-run card
  with "Don't ask" without first trying Enable (intent: card is clear
  enough that 'Don't ask' is informed, not annoyance-driven)

## Dependencies & Prerequisites

- `androidx.biometric.ktx 1.2.0-alpha05` (already bundled)
- `com.sun.jna:jna` (already a transitive dep via existing JNA usage
  in `MacOsVlcDiscoverer.kt`)
- Compose Multiplatform 1.11.0 — `Modifier.blur` supported
- macOS dev host with `swiftc` (only when changing Touch ID shim source)
- Windows dev host with `dotnet` SDK (only when changing Hello helper)
- Pre-built `.dylib` / `.exe` committed to repo so non-mac/non-win devs
  can still build

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| macOS 15+ NSWindowSharingNone is ignored by ScreenCaptureKit | Verified | Medium | In-app caveat row; document in settings + release notes |
| Touch ID Swift shim breaks under future macOS update | Low | Medium | Shim is tiny; fix is < 30 LOC. Fallback: password via `LAPolicy.deviceOwnerAuthentication` works without biometric |
| Windows Hello helper.exe gets flagged by AV | Medium | Medium | Sign the helper with same code-sign cert as the main MSI; ship as `AmethystHello.exe` inside the app bundle (not standalone) |
| Linux distro permutations (Wayland vs X11) — no portable capture block | Verified | Low | Documented limitation; lock + blur + notif redaction still work |
| Notification deep-link races the gate (chat flash) | Medium | High (privacy leak) | LaunchedEffect initial-composition guard; integration test |
| BiometricPrompt + Compose lifecycle bug under config-change | Low | Medium | Use existing pattern from `UpdateZapAmountDialog.kt:428-479`; add lifecycle-aware launch |
| User disables OS device credentials → permanently locked out | Verified | Low | `onCredentialUnavailable` → disable + warn banner — no permanent lockout |
| Spotless config doesn't know about Swift / C# files | Low | Low | Add file globs to `.editorconfig` only — spotless is Kotlin-scoped |
| ProGuard strips `BiometricPrompt` reflection | Medium | Medium | Existing `compose-rules.pro` already has broad keepnames; add explicit `-keep class androidx.biometric.**` if needed |
| Marmot UI extraction blocked / delayed | Low | Low | Gate wraps Marmot Android screen today; Desktop inherits when Marmot extracts. No coupling. |

## Future Considerations

- **Windows Hello in v2.** Native helper `.exe` (C++/WinRT or C#) ~60
  LOC over stdio, parented to the JVM `HWND` via
  `IUserConsentVerifierInterop`. Requires the helper to be signed with
  the same cert as the main MSI to avoid AV flags. Add once the v1
  flow has user feedback. (See deepen-plan external research output.)
- **Linux lock in v2.** Bitwarden's polkit pattern is the proven path
  but requires a root-installed policy file at
  `/usr/share/polkit-1/actions/com.amethyst.privacy-lock.policy`,
  which breaks Flatpak distribution. Alternative: in-app password
  prompt stored in libsecret. Decide at v2.
- **Phase 2 — nsec encryption-at-rest with PIN-derived key**.
  Separate brainstorm. Closes the cosmetic-vs-cryptographic gap. Would
  re-encrypt `accounts.json.enc` with `PRF(nsec, PIN-derived key)` on
  lock; decrypt only at unlock. Compatible with NIP-46 bunker (signer
  never holds nsec). Compatible with NIP-55 (external signer).
- **Per-conversation lock** (WhatsApp Chat Lock parity). Add
  `Chatroom.locked: Boolean` flag and a second gate over individual
  ChatroomScreen entries.
- **Wallet (NWC) gate** — reuse the same `MessagesLockGate` plumbing
  to gate the Wallet deck column. Already on the feature backlog.
- **amy CLI integration** — `amy messages status` could refuse to
  print DMs while `lockEnabled = true` to enforce parity even from the
  headless side. Useful when running amy on a shared machine.
- **Hide app from app-switcher entirely (Android)** — instead of
  FLAG_SECURE blanking, fully omit the app from the recents list while
  Messages is foregrounded. More aggressive UX; opt-in.

## Documentation Plan

- `commons/ARCHITECTURE.md` — add `privacylock/` package entry
- `desktopApp/CLAUDE.md` (if exists) — mention Touch ID shim build step
- New `desktopApp/plans/2026-06-30-privacy-lock-native-shims-recipe.md`
  capturing the macOS `.dylib` / Windows `.exe` build & signing
  procedure (so future contributors don't rediscover it)
- Release notes section "Privacy & Security" highlighting the new
  toggle + caveats (macOS 15+, Linux capture)
- `docs/manual-testing-privacy-lock.md` — the testing sheet (delivered
  as part of the final step)

## Sources & References

### Origin

- **Brainstorm document**:
  [`docs/brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md`](../brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md)
  — Key decisions carried forward: (a) Messages-only route gate (not
  app-wide), (b) OS device credentials only (no Amethyst PIN), (c)
  device-global setting, (d) off by default + inline first-run card,
  (e) inactivity-timer + leave-route as the two re-lock triggers,
  (f) auto-bump notification preview to Generic on first enable.

### Internal References

- Existing biometric pattern:
  `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/UpdateZapAmountDialog.kt:428-479`
- DM notification path:
  `amethyst/src/main/java/com/vitorpamplona/amethyst/service/notifications/NotificationUtils.kt:370-510`
- Existing JNA usage:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/media/MacOsVlcDiscoverer.kt`
- macOS window-chrome extension pattern:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/platform/PlatformTheme.kt:87`
- Desktop window creation:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt:316`
- Settings storage on desktop (java.util.prefs precedent):
  `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/keystorage/SecureKeyStorage.kt`
- DM messaging UI (commons + Android + Desktop) — surveyed in brainstorm research.

### External References

- macOS Touch ID via Swift shim: [classycodeoss/java-touchid](https://github.com/classycodeoss/java-touchid)
- Rococoa (Java↔Cocoa bridge, used by Cyberduck): [iterate-ch/rococoa](https://github.com/iterate-ch/rococoa)
- Apple `LAContext.evaluatePolicy`: [developer.apple.com](https://developer.apple.com/documentation/LocalAuthentication/LAContext/evaluatePolicy(_:localizedReason:reply:))
- Microsoft `UserConsentVerifier`: [learn.microsoft.com](https://learn.microsoft.com/en-us/uwp/api/windows.security.credentials.ui.userconsentverifier)
- `IUserConsentVerifierInterop` (HWND attach): [Microsoft sdk-api](https://github.com/MicrosoftDocs/sdk-api/blob/docs/sdk-api-src/content/userconsentverifierinterop/nn-userconsentverifierinterop-iuserconsentverifierinterop.md)
- Bitwarden Linux polkit pattern (rejected for v1): [bitwarden/clients PR #4586](https://github.com/bitwarden/clients/pull/4586)
- macOS 15+ `sharingType` regression: [Tauri #14200](https://github.com/tauri-apps/tauri/issues/14200)
- Windows `SetWindowDisplayAffinity`: [learn.microsoft.com](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowdisplayaffinity)
- NIST 800-63B session timeouts: [pages.nist.gov](https://pages.nist.gov/800-63-4/sp800-63b.html)
- WhatsApp Chat Lock: [about.fb.com](https://about.fb.com/news/2023/05/whatsapp-chat-lock/)
- Signal Screen Lock: [support.signal.org](https://support.signal.org/hc/en-us/articles/360007059572-Screen-Lock)

### Related Work

- Embedded local relay plan (precedent for `commons/jvmMain` state
  holder + StateFlow + `LocalXyzStore` pattern):
  `desktopApp/plans/2026-05-09-embedded-local-relay-plan.md`
- Account security hardening (concurrent, deals with nsec storage and
  forced logout): `docs/plans/2026-05-14-fix-account-security-hardening-plan.md`
- Hashtag spam filter (precedent for global preferences node):
  `docs/plans/2026-06-29-feat-desktop-hashtag-spam-filter-plan.md`

## Open Questions (post-brainstorm + post-deepen)

All brainstorm open questions are resolved. Original planning
questions reordered + deepen-review additions integrated:

1. **macOS code-signing for Touch ID shim.** Current desktop build's
   `nativeDistributions { macOS { ... } }` block has only `bundleID`
   and `iconFile`. No `signing` block, no notarization. Touch ID
   *works* on unsigned dev builds (research confirms), but for the
   release DMG we need a Developer ID + notarization. Verify whether
   the existing DMG release is signed elsewhere; if not, that's a
   separate workstream (out of this plan's scope but a release-day
   blocker).
2. **ProGuard rules.** Compose Desktop release uses ProGuard. Verify
   `compose-rules.pro` keeps the new JNA classes, biometric reflection
   surfaces, and Compose composables. Add
   `-keep class androidx.biometric.** { *; }` if not already covered.
3. **Search audit — concrete files** (refined from spec-flow review):
   `SearchBarViewModel.kt` (Android), `AdvancedSearchBarState.kt`
   (commons), `AdvancedSearchPanel.kt` (desktop). Filter aggregation
   on kinds 4 / 14 / 1059 / 443 when `MessagesLockState.state == Locked`.
   Phase 5 deliverable, gated by an acceptance criterion below.
4. **`commons/jvmAndroid` source set name** — verify the exact name
   `commons/ARCHITECTURE.md` uses (could be `jvmAndroidMain` or
   `desktopAndroidMain`). Source set must exist already (used by
   shared StateFlow / persistence patterns); confirm during Phase 1.
5. **`CredentialPrompter` interface vs `expect class`** — per pattern
   review the codebase has no precedent for `@Composable expect fun`,
   so the plan uses a plain interface + CompositionLocal. If a wider
   refactor ever wants `expect class CredentialPrompter` (matching
   `SecureKeyStorage`), follow that idiom — out of scope now.
6. **`firstRunCardSeen` scope** — device-global (single flag, not
   per-npub) per spec-flow gap #2. Acceptable for v1.
7. **`MessagesLockState.LocalMessagesLockState` instantiation site on
   Android** — needs a single owner equivalent to the Desktop `Main.kt`
   instantiation. Likely `AmethystApp` / Application class. Confirm
   during Phase 2 wiring.
8. **Telemetry?** Amethyst has no telemetry today. Success metrics
   above are inferred from issue volume — keep as-is, no telemetry
   shipped.
