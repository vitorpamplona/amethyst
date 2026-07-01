---
title: Messages First-Run Privacy Lock Banner (Desktop)
type: feat
status: completed
date: 2026-07-01
---

# Messages First-Run Privacy Lock Banner (Desktop)

## Overview

Inline discovery banner at the top of the Desktop Messages deck column
that appears when:

1. The privacy lock is **not enabled**, AND
2. The user has **not dismissed** it before (`firstRunCardSeen == false`).

Two actions: **Enable** (opens the existing password-set dialog inline)
or **Not now** (sets `firstRunCardSeen = true` and hides forever). One
composable, no new state, no new settings.

Small follow-up to the shipped privacy-lock feature at
[`docs/plans/2026-06-30-feat-messaging-privacy-lock-plan.md`](2026-06-30-feat-messaging-privacy-lock-plan.md).

## Problem Statement / Motivation

The privacy lock exists and works (Desktop v1 password gate + auto
re-lock + settings pane), but it has **zero in-app discovery**. A user
who never opens Settings will never find it. Yet the Messages column
is exactly the surface where the feature's value is felt — anyone who
opens it is by definition a DM user.

Existing users are the sharper case: they can install this update and
never learn the feature exists unless they read the release notes.
A one-time banner solves that with negligible UX cost.

## Proposed Solution

Add a `MessagesFirstRunBanner` composable rendered at the top of
`DesktopMessagesScreen`, above the two-pane / single-pane layout. It
sits inside the `DesktopMessagesLockGate` content lambda, so the
banner is only ever visible in Unlocked / Disabled states — never
over the lock screen.

Uses `AnimatedVisibility(expandVertically + fadeIn / shrinkVertically
+ fadeOut)` for the show/hide transition — same pattern as
`desktopApp/.../ui/components/OfflineBanner.kt:49-56` (the embedded
local-relay work). Same visual treatment (Surface + Row + Icon + Text
+ TextButton).

The dialog behind the **Enable** action is the existing
`SetPasswordDialog` from `PrivacyLockSettingsScreen.kt:270`. That dialog
is currently `private`. Extract it to a new shared file so both the
Settings pane and the banner point at the same composable.

On successful password save from the banner:

1. `settings.setPasswordHashed(hash)`
2. `settings.setLockEnabled(true)`
3. `settings.setFirstRunCardSeen(true)` (dismiss banner)
4. `messagesLockState.onUnlockSuccess()` — keep the user Unlocked so
   they don't immediately hit a lock screen after enabling.

The last call requires a tiny leniency change to `onUnlockSuccess()`
so it works from `LockState.Disabled` as well as `Locked` (see
Technical Considerations below).

## Technical Considerations

### Architecture

- **New file**: `desktopApp/.../security/MessagesFirstRunBanner.kt`
  — the banner composable + interaction logic.
- **New file**: `desktopApp/.../security/SetPasswordDialog.kt` —
  extracted from `PrivacyLockSettingsScreen.kt`. Public visibility so
  the banner can reuse it.
- **Edit**: `PrivacyLockSettingsScreen.kt` — delete the private
  `SetPasswordDialog` body, add an import for the new shared version.
- **Edit**: `DesktopMessagesScreen.kt` — insert the banner as the
  first child inside the existing root `Column` (or wrap the current
  content in a Column if the top-level isn't already one). Passes
  through in single-pane and compact modes identically.
- **Edit**: `MessagesLockState.kt` — relax `onUnlockSuccess()` to
  accept `Disabled` as a valid previous state (transitions to
  `Unlocked`).

### Visibility logic

```kotlin
// MessagesFirstRunBanner.kt
@Composable
fun MessagesFirstRunBanner() {
    val settings = LocalPrivacyLockSettings.current
    val lockState = LocalMessagesLockState.current
    val enabled by settings.lockEnabled.collectAsState()
    val seen by settings.firstRunCardSeen.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !enabled && !seen,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lock the Messages tab?",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Require a password before Messages shows. Feed and profile stay open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { settings.setFirstRunCardSeen(true) }) {
                    Text("Not now")
                }
                Button(onClick = { showDialog = true }) {
                    Text("Enable")
                }
            }
        }
    }

    if (showDialog) {
        SetPasswordDialog(
            existingHash = null,
            onDismiss = { showDialog = false },
            onConfirm = { newHash ->
                settings.setPasswordHashed(newHash)
                settings.setLockEnabled(true)
                settings.setFirstRunCardSeen(true)
                lockState.onUnlockSuccess() // stay Unlocked; don't force user through the gate immediately
                showDialog = false
            },
        )
    }
}
```

### Placement inside DesktopMessagesScreen

The banner goes at the top of the composable's root layout. In
`DesktopMessagesScreen.kt` the current `@Composable fun
DesktopMessagesScreen(...)` returns either a two-pane or single-pane
layout. Wrap in a Column:

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    MessagesFirstRunBanner()
    // existing two-pane / single-pane content, weight(1f)
}
```

Both pane modes need `Modifier.weight(1f)` on the pane container so
they consume the remaining space.

### State transitions

Enabling from the banner drives this sequence:

| Step | Action | LockState |
|---|---|---|
| Before click | User on Messages, lock off | `Disabled` |
| `setPasswordHashed(hash)` | Persist | `Disabled` |
| `setLockEnabled(true)` | Persist + StateFlow emit | `Disabled` → eventually `Locked` via init collector |
| `setFirstRunCardSeen(true)` | Persist | (no state change) |
| `onUnlockSuccess()` | Force state = `Unlocked` | `Unlocked` |

Race: the `settings.lockEnabled` collector on `windowScope` runs
whenever it's next scheduled. If it fires between step 3 and step 4,
state briefly is `Locked`. If it fires after step 4, state is
`Unlocked` and the collector's `else if (state is Disabled)` branch
skips (state isn't Disabled anymore). Either ordering ends at
`Unlocked`. The gate re-reads `state` each frame, so no lock-screen
flash — `Unlocked` reaches the UI within the same recomposition batch.

### `onUnlockSuccess()` leniency

Current signature only transitions from `Locked`:

```kotlin
fun onUnlockSuccess() {
    if (mutableState.value is LockState.Locked) {
        mutableState.value = LockState.Unlocked
        restartIdleTimer()
    }
}
```

Change to:

```kotlin
fun onUnlockSuccess() {
    if (mutableState.value !is LockState.Unlocked) {
        mutableState.value = LockState.Unlocked
        restartIdleTimer()
    }
}
```

New semantics: "the caller has authenticated the user; go to Unlocked
regardless of previous state." No behavior change for any existing
call site (they only ever call this from `Locked`).

### Performance

- Banner is inside a `Column` that's already the root layout — one
  extra `AnimatedVisibility` composable.
- Reads two `StateFlow<Boolean>` via `collectAsState`. Both are hot
  (backed by `MutableStateFlow`) so no cost when not changing.
- When `firstRunCardSeen` flips true or `lockEnabled` flips true,
  `AnimatedVisibility` runs its exit animation (~300ms) and unmounts
  the banner. One-time cost per session.
- No new coroutine, no new dispatcher.

### Accessibility

- Icon has `contentDescription = null` (decorative — title conveys
  the meaning).
- Text uses semantic `titleSmall` / `bodySmall` styles for screen
  readers.
- Buttons have explicit text labels.

## System-Wide Impact

- **Interaction graph**: `MessagesFirstRunBanner` → reads
  `LocalPrivacyLockSettings` + `LocalMessagesLockState` → on Enable
  action → `SetPasswordDialog` → onConfirm → 4 sequential settings
  writes + `onUnlockSuccess()` → `MessagesLockState.state` transitions
  → gate recomposes → user sees banner disappear + column stays
  interactive.
- **Error propagation**: none new. Password validation stays inside
  `SetPasswordDialog` (existing errors: length, mismatch, wrong
  current). Password hash write to `java.util.prefs` is best-effort
  (the underlying `prefs.put(...)` swallows IO errors — matches the
  existing settings pane).
- **State lifecycle risks**: none. The banner only writes; it never
  reads then re-writes. Dismissal is idempotent (`setFirstRunCardSeen(true)`
  is safe to call twice). Enabling from banner is atomic-ish (see
  race analysis above — end state is deterministic).
- **API surface parity**: `SetPasswordDialog` becomes a shared
  composable. Verify both call sites (banner + settings pane) render
  the same behavior after extraction.
- **Integration test scenarios** (manual — no test infra for this):
  1. Fresh install → open Messages → banner visible; enable → password
     dialog → set + save → banner disappears, column stays interactive,
     lock is on for next session.
  2. Fresh install → open Messages → banner visible; **Not now** →
     banner disappears, does not reappear on subsequent Messages entries
     even after restart.
  3. Existing user with lock already enabled from Settings pane →
     `!enabled` is false → banner never renders.
  4. User dismisses banner, later disables the lock from Settings →
     banner does NOT reappear (dismissal is sticky by design).
  5. Cold-start with lock enabled → gate takes over immediately →
     banner never renders.

## Acceptance Criteria

- [x] `MessagesFirstRunBanner` composable exists at
      `desktopApp/.../security/MessagesFirstRunBanner.kt`
- [x] `SetPasswordDialog` extracted to
      `desktopApp/.../security/SetPasswordDialog.kt` and reused by
      both `PrivacyLockSettingsScreen` and the new banner (single
      source of truth)
- [x] `PrivacyLockSettingsScreen` still opens the same dialog after
      the extraction (visual + behavior identical)
- [x] Banner appears at the top of the Desktop Messages column when
      `!lockEnabled && !firstRunCardSeen`
- [x] Banner never appears when the gate is Locked (implicit — the
      gate replaces content)
- [x] "Enable" button opens the password set dialog inline
- [x] After successful password save from banner: lock is enabled,
      hash stored, banner dismissed, column stays Unlocked (no
      lock-screen flash)
- [x] "Not now" dismisses the banner permanently across restarts
- [x] Banner does NOT reappear after the user later disables the lock
      from Settings (dismissal is sticky per `firstRunCardSeen`
      semantics)
- [x] `MessagesLockState.onUnlockSuccess()` accepts `Disabled` as a
      valid previous state (Unit-tested)
- [x] `./gradlew :commons:jvmTest` green (all 8 existing tests +
      any new coverage for the leniency change)
- [x] `./gradlew :desktopApp:compileKotlin` green
- [x] `./gradlew spotlessApply` clean
- [x] Manual testing sheet updated with a new Path (banner discovery)

## Alternative Approaches Considered

1. **Modal dialog on first Messages entry** — rejected in the original
   brainstorm as too intrusive. Same rejection applies here.
2. **Sidebar/menu badge on the Messages icon** — subtle but obscure;
   users don't associate a lock-glyph badge with the concept "you can
   lock this." Also requires editing the sidebar composable, more
   surface area.
3. **Toast/snackbar on Messages open** — dismissive; easily missed;
   auto-fades. Terrible for a discovery affordance.
4. **Onboarding flow** — Amethyst has no first-run onboarding today
   and adding one for this feature is disproportionate.
5. **Inline chip inside the empty-state view** — misses users who
   already have chats (skips the empty-state).
6. **No banner, rely on release notes** — status quo. Fails the
   existing-user discovery case.

Chosen: **top-of-column banner** — visible to all Messages users,
non-blocking, dismissable.

## Success Metrics

Amethyst has no telemetry. Qualitative signals only:

- Zero GitHub issues about "banner won't go away" within 30 days.
- One or more community reports of users discovering + enabling the
  lock via the banner (Nostr threads, Reddit, Discord).
- No reports of a lock-screen flash after enable (validates the
  `onUnlockSuccess()` leniency + state-transition ordering).

## Dependencies & Risks

- **Depends on** the shipped privacy-lock feature (Phase 1 + Phase 5
  Desktop wiring already committed on this branch). Nothing new to
  import.
- **Depends on** `firstRunCardSeen` field already in
  `PrivacyLockSettings` — no new persistence needed.
- **Risk (low)**: extraction of `SetPasswordDialog` accidentally
  changes behavior in the Settings pane. Mitigation: extract as
  literal copy, replace call site, no logic change.
- **Risk (low)**: `onUnlockSuccess()` leniency affects existing
  callers. Mitigation: only one existing caller
  (`DesktopLockScreen`) and it calls this from `Locked`, so the new
  semantics are backward-compatible.
- **Risk (very low)**: state race between the `setLockEnabled` init
  collector and `onUnlockSuccess()`. Analysis above shows the end
  state is deterministic; no lock-screen flash reaches the user
  because `Unlocked` is set within the same UI recomposition batch.

## Sources & References

### Origin

- **Parent plan**:
  [`docs/plans/2026-06-30-feat-messaging-privacy-lock-plan.md`](2026-06-30-feat-messaging-privacy-lock-plan.md)
  — the shipped feature this banner promotes.
- **Parent brainstorm**:
  [`docs/brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md`](../brainstorms/2026-06-30-feat-messaging-privacy-lock-brainstorm.md)
  — the resolved-Q "First-run prompt = inline dismissable card at top
  of Messages on first entry" that this banner implements.

### Internal References

- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/components/OfflineBanner.kt`
  — structural template (AnimatedVisibility + Surface + Row).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/settings/PrivacyLockSettingsScreen.kt:270`
  — the `SetPasswordDialog` to extract.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/chats/DesktopMessagesScreen.kt:82`
  — the insertion point.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/privacylock/MessagesLockState.kt`
  — file for the `onUnlockSuccess()` leniency edit.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/privacylock/PrivacyLockSettings.kt:41,57`
  — `firstRunCardSeen` + setter already exist.

### Related Work

- Manual testing sheet at
  `docs/plans/2026-06-30-privacy-lock-manual-testing.md` — will need
  a Path J (banner discovery) added post-implementation.
