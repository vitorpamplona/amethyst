---
title: Messaging Privacy Lock — Desktop Manual Testing Sheet
type: test
status: active
date: 2026-06-30
---

# Messaging Privacy Lock — Desktop Manual Testing Sheet

Companion to `2026-06-30-feat-messaging-privacy-lock-plan.md` and
`2026-06-30-feat-messaging-privacy-lock-brainstorm.md`.

## What ships on the `worktree-brainstorm-messaging-privacy-lock` branch

- ✅ **Commons foundation** — headless cross-platform state machine,
  preferences with password-hashed field, gate composable primitives,
  idle-timer modifier, 8 unit tests all green
  (`./gradlew :commons:jvmTest --tests "*MessagesLockStateTest*"`).
- ✅ **Desktop wiring** — `DesktopMessagesLockGate` wrapping the
  Messages deck column; PBKDF2 password unlock (100k iterations,
  16-byte salt); `PrivacyLockSettingsScreen` embedded in the Settings
  pane; app-global CompositionLocals provided in `Main.kt`.

## What is NOT in this branch

- **Android app module changes** — reverted. Foundation code in
  `commons/` still compiles for Android; wiring is a separate future
  workstream.
- **macOS Touch ID / Windows Hello / Linux biometrics** — Desktop v1
  uses a PBKDF2 password gate. Native biometric shims (Swift
  `LAContext`, Windows Hello, polkit) remain in Future Considerations
  per the plan.
- **Notification redaction call site** — the `DmRedactionLevel` policy
  exists in `commons/.../privacylock/`; the actual write into
  Android's `NotificationUtils.kt` or a desktop notification path is
  deferred (no desktop notifications wired today).
- **Screen-capture window blocks** — `NSWindowSharingNone` /
  `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` deferred. macOS 15+
  broke `sharingType` anyway; Windows can revisit as a v1.1 patch.
- **Blur-on-unfocus** — Desktop deferred to v1.1; the gate's
  onLeaveRoute-fires-on-column-navigation already covers the "left
  Messages" scenario.
- **Marmot group chats** — Marmot UI is Android-only in this repo.
  Desktop only gates the primary Messages column.
- **Security hardening H2–H6** — crash-report scrubber, DM-content
  log audit, MessagingStyle history flush, NotificationListener
  redaction of the main builder, bunker decrypt queue drain. Each is
  independent scope; see the plan's §Security Hardening Additions.

## Prerequisites

- macOS host recommended (the primary Amethyst Desktop dev target).
  Also runs on Windows and Linux (untested for this feature).
- Java 17+ (Compose Desktop bundles its own JBR).
- A Nostr account with at least one DM conversation for meaningful
  gating.

## Automated verification

```bash
# Unit tests for the state machine + settings interface
./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.privacylock.MessagesLockStateTest"

# Full commons + desktop compile
./gradlew :commons:compileKotlinJvm
./gradlew :desktopApp:compileKotlin

# Formatter
./gradlew spotlessApply
```

Expected: green.

## Manual testing paths

### Launch

```bash
./gradlew :desktopApp:run
```

Log in with an account that has one or more DM conversations.
Add a Messages column to your deck via the "+" button → Messages, if
not already present.

### Path A — Enable the lock

1. Click **Settings** in the sidebar.
2. Scroll to the **"Lock the Messages tab"** card.
   - ✅ Card shows a description and a **Switch**.
   - ✅ Switch is OFF by default.
3. Click the Switch to ON.
   - ✅ A **Set a password** dialog appears (because no password is
     set yet).
   - ✅ Dialog has: "New password" field, "Confirm new password"
     field, Cancel + Save buttons.
4. Enter a password shorter than 4 characters, click Save.
   - ✅ Error: "New password must be at least 4 characters".
5. Enter mismatched passwords, click Save.
   - ✅ Error: "Passwords don't match".
6. Enter matching passwords ≥ 4 chars, click Save.
   - ✅ Dialog closes.
   - ✅ Switch is now ON.
   - ✅ Below the toggle: **"Change password"** button appears.
   - ✅ Two new cards appear: **"Auto-lock after"** (default: 5 min)
     and **"DM notification preview"** (default: Full).

### Path B — Lock behavior

1. Lock enabled with a password set. Navigate to the Messages deck
   column.
   - ✅ **Lock screen** renders with a padlock icon, "Messages
     locked" title, "Enter your privacy-lock password" subtitle, a
     password field, and an Unlock button.
   - ✅ The chat list is NOT visible behind the lock screen.
2. Click Unlock without typing.
   - ✅ Button is disabled.
3. Type a wrong password, press Enter (or click Unlock).
   - ✅ "Wrong password" error under the field.
   - ✅ Chat still not visible.
4. Type the correct password, press Enter.
   - ✅ Lock screen disappears.
   - ✅ Full Messages column visible with conversations + chat pane.

### Path C — Auto re-lock triggers

1. Unlocked. Set inactivity timer to **1 min** via Settings →
   Privacy lock → Auto-lock after.
2. Return to Messages, don't interact for 60 seconds.
   - ✅ Column re-locks; password field reappears.
3. Unlock. Scroll a chat back and forth for 30 seconds.
   - ✅ Column stays unlocked (user interaction resets timer).
4. Unlock. Navigate to Feed or Discover column.
   - ✅ Return to Messages → **re-locked immediately** (leave-route
     trigger via `DisposableEffect(onDispose)`).
5. Set timer to **Never**.
   - ✅ Wait 2 minutes without input → column stays unlocked.
   - ✅ Navigating away still re-locks (leave-route independent).

### Path D — Deep-link race (security H1)

Not directly testable in this branch — Desktop deck columns navigate
via the sidebar, so there's no true "cold-start-to-chatroom deep link"
flow. However, the invariant still applies: the gate reads
`MessagesLockState.state` synchronously in composition (no
`LaunchedEffect` guard). Verify by:

1. Enable lock, quit the app.
2. Cold-start (`./gradlew :desktopApp:run`).
3. Add the Messages column immediately.
   - ✅ Lock screen appears **from the first frame** — never see chat
     content flash before the lock overlay paints.

### Path E — Change / clear password

1. Lock enabled. Settings → Privacy lock → **Change password**.
2. Dialog opens with: **Current password** field, New password,
   Confirm.
3. Enter wrong current password.
   - ✅ "Current password is wrong" error.
4. Enter correct current, new, confirm → Save.
   - ✅ Old password no longer works on the lock screen.
   - ✅ New password unlocks.
5. Toggle lock OFF, then back ON.
   - ✅ Password persists — you're NOT re-prompted to set one, since
     the hash is still stored (`passwordHashed` is not cleared on
     disable).

### Path F — Fallback (no password set)

Rare: user manually clears `passwordHashed` from
`~/.java/.userPrefs/com/vitorpamplona/amethyst/privacylock/` while
`lockEnabled = true`. To simulate:

1. Quit the app.
2. Open the prefs file for the node and remove
   `password_hashed=<value>` while keeping `lock_enabled=true`.
3. Restart.
4. Navigate to Messages.
   - ✅ Lock screen shows: "No password is set yet. Open Settings →
     Privacy lock to set one." + a **Disable lock** button.
5. Click **Disable lock**.
   - ✅ Lock is disabled globally; Messages column visible.

### Path G — Multi-account switch

1. Lock enabled, unlocked. In Messages, viewing a conversation.
2. Switch account via the sidebar / account switcher.
   - ✅ Return to Messages → column re-locked (account switch counts
     as leave-route because the deck column composable is disposed
     and re-composed).

### Path H — Regression sweep

1. Feed / Discover / Wallet / Videos columns still function normally,
   no gate anywhere else.
2. Sending a DM (after unlock) works — the compose pane is inside the
   gated content, so unlock allows send.
3. Settings pane still shows all other sections (Media servers, Local
   Relay, Namecoin, Logout).
4. Long-press-and-drag column reordering still works.

### Path J — First-run discovery banner

Added by `docs/plans/2026-07-01-feat-messages-first-run-lock-banner-plan.md`.

1. Fresh state — quit app, delete
   `~/.java/.userPrefs/com/vitorpamplona/amethyst/privacylock/prefs.xml`
   (or edit to clear `first_run_card_seen` + `lock_enabled`).
2. Launch app, open Messages column.
   - ✅ Inline banner at top of Messages column: padlock icon +
     **"Lock the Messages tab?"** + description + **Not now** +
     **Enable** buttons.
   - ✅ Conversation list + chat pane still visible below (banner is
     ~50dp, doesn't dominate).
3. Click **Not now**.
   - ✅ Banner animates out (shrinkVertically + fadeOut).
   - ✅ Navigate away and back → banner does NOT return.
   - ✅ Quit + relaunch → banner still does not return.
4. Reset state again. Click **Enable** on the banner.
   - ✅ **Set a password** dialog opens (same dialog as Settings).
   - ✅ Enter matching password ≥ 4 chars → Save.
   - ✅ Banner animates out.
   - ✅ Column STAYS interactive — **no lock-screen flash** after
     save (verifies the `onUnlockSuccess()` leniency).
5. Continue browsing chats without unlock prompt.
6. Navigate to Feed → back to Messages.
   - ✅ Lock screen appears (leave-route trigger fired after enable).
   - ✅ Unlock with the password you just set.
7. Reset state. In Settings, enable the lock first (via the toggle
   card). Then open Messages.
   - ✅ Banner does NOT appear (`lockEnabled == true` suppresses it).
8. Reset state. Show the banner. Dismiss with **Not now**. Now
   enable the lock via Settings. Later disable it via Settings.
   Return to Messages.
   - ✅ Banner does NOT reappear (dismissal is sticky — the
     `firstRunCardSeen` flag persists across enable/disable cycles).

### Path I — Cross-run persistence

1. Set a password, enable lock, set timer to 15 min, redaction to
   Hidden.
2. Quit the app.
3. Restart via `./gradlew :desktopApp:run`.
   - ✅ Lock is still enabled; navigating to Messages requires
     password.
   - ✅ Timer setting persists.
   - ✅ Redaction persists.

## Known-honest limitations

Copy in the Settings pane's **Limitations** card matches the actual
threat model:

- Filesystem-level attacker can read the `java.util.prefs` node and
  see the *hash* — cannot recover the password without brute-force,
  but 4-char passwords are weak. Recommend ≥ 8 chars, but v1 min is 4.
- Memory dumps expose the plaintext password briefly during
  verification. Not defended.
- The Nostr private key (`nsec`) is stored via `SecureKeyStorage` as
  it is today — the lock does not touch it.

## Sign-off checklist

- [ ] Paths A–I executed on macOS
- [ ] `./gradlew :commons:jvmTest` green
- [ ] `./gradlew :desktopApp:compileKotlin` green
- [ ] `./gradlew spotlessApply` clean
- [ ] Follow-up issues filed for:
  - Windows / Linux manual QA
  - Screen-capture window blocks (macOS 15+ caveat)
  - Notification redaction call site (desktop notifications don't
    exist yet — deferred to when they do)
  - Native biometrics (Touch ID / Windows Hello / polkit)
  - Security hardening H2–H6 (crash scrub, log audit, MessagingStyle
    history, NotificationListener redaction, bunker queue drain)
  - Marmot group chat gating (once Marmot desktop UI lands)
