---
title: Wallet Privacy Lock — Manual Testing Sheet
type: test
status: active
date: 2026-07-07
plan: docs/plans/2026-07-07-feat-wallet-privacy-lock-reuse-plan.md
---

# Wallet Privacy Lock — Manual Testing Sheet

Companion to the messaging-privacy-lock testing sheet — assumes the Messages
gate has already been validated by that document. Focus here is on the
Wallet gate and cross-scope behaviour introduced by the single master lock.

## Setup

- Fresh Amethyst Desktop install on a supported OS (macOS 14+, Windows 11,
  Ubuntu 22.04+).
- Log in with an account that has an NWC-connected wallet (Alby or a
  self-hosted LNDHUB will do).
- Confirm messaging-privacy-lock testing sheet has been executed and green.
- Start with `lockEnabled = false` (default).

## T1 — First-run banner (Wallet)

**Steps.** Open the Wallet column with the master lock disabled and never
seen the first-run banner before.

**Expected.** Banner *"Lock the Wallet and Messages?"* appears at the top
of the Wallet column with Enable + Not now buttons. Dismissing with **Not
now** hides the banner permanently; opening Messages afterwards shows no
banner either (single `firstRunCardSeen` flag).

**Failure.** Banner reappears after Not now, or Messages banner shows
independently.

## T2 — Enable via Wallet banner

**Steps.** Fresh install. Open Wallet. Tap **Enable** on the banner. Set a
password.

**Expected.** After the password dialog closes, the Wallet column is
Unlocked and immediately usable (no lock screen flash). Navigating to
Messages shows the Messages lock screen — because the Messages instance is
freshly Locked. Password unlocks it.

**Failure.** Wallet flashes lock screen after enabling; Messages does not
lock.

## T3 — Cross-scope lockout (brute force)

**Steps.** Lock enabled. On the Wallet lock screen, enter 5 wrong
passwords in a row. Then navigate to Messages.

**Expected.** Both Wallet AND Messages show *"Too many attempts. Try
again in 30s."* Password field disabled on both. Countdown updates
every ~0.5s.

**Failure.** Only Wallet locks out; Messages accepts input.

## T4 — Balance and invoice blur on window unfocus

**Steps.** Lock enabled, Wallet Unlocked, wallet connected. Note the
balance amount. Now click a browser or other app to defocus the Amethyst
window.

**Expected.** The balance amount text ("N sats") blurs; the "Balance"
label, "Refresh" button, and card outline stay crisp. Refocus Amethyst
→ blur clears immediately.

**Also.** Open Receive dialog, generate an invoice. Defocus the window.
Amount text + QR code blur. Refocus → clear. Note that Send-dialog input
fields are NOT blurred (users need to type into them).

**Failure.** Whole card blurs, or blur persists after refocus, or blur
never fires.

## T5 — Leave-route re-lock

**Steps.** Lock enabled, Wallet Unlocked. Navigate away from the Wallet
column (Home Feed or Messages).

**Expected.** Returning to Wallet shows the lock screen. Messages state
is not affected (if it was Unlocked, it stays Unlocked).

**Failure.** Wallet stays Unlocked, or Messages is force-locked too.

## T6 — Idle timer re-lock (Wallet in foreground)

**Steps.** Lock enabled. Set inactivity timer to 1 minute. Unlock Wallet.
Do not interact with the app for 60+ seconds.

**Expected.** After ~1 minute, Wallet column transitions to Locked; the
lock screen shows. Password unlocks it.

**Failure.** Wallet stays Unlocked past the timer; timer only applies to
Messages.

## T7 — Password change → shared re-verify

**Steps.** Enable lock. Set password `A`. Change password to `B` from
Settings. Unlock Wallet — should accept `B`, reject `A`.

**Expected.** Only the new password unlocks. Both scopes accept `B`.

**Failure.** Wallet still accepts old password.

## T8 — Password clear → cascade

**Steps.** Enable lock. Set password. Navigate to Settings → Privacy
lock. Click *"Remove password"* and confirm with the current password.

**Expected.** Master toggle turns off automatically. Both Messages and
Wallet transition to Disabled (no lock screen). Navigation into either
route shows content without a prompt.

**Failure.** Master toggle stays on with no password (invalid state).

## T9 — Deep-link to Settings from Wallet "No password set" branch

**Steps.** Contrived-state edge case: enable lock with password. Then
manually delete the `password_hashed` java.util.prefs key while the app is
running (via a debugger or a second Settings tab). Navigate to Wallet.

**Expected.** Lock screen renders *"No password is set yet."* with an
**Open Settings** button. Tapping it navigates to the Settings tab
(via `onNavigateToRelays`). User can re-set the password there.

**Failure.** Wallet shows *"Disable lock"* fallback instead of the deep-link
(that's the Messages behaviour, but per plan Q5 Wallet should deep-link).

## T10 — First-time enable via Settings (Wallet-only user)

**Steps.** User who never opens Messages. Enable the lock via Settings
directly (not via a banner). Set password. Navigate to Wallet.

**Expected.** Wallet gate fires normally. Password unlocks. Master toggle
now enables both scopes but Messages is never visited so no visible
difference.

**Failure.** Wallet not gated.

## T11 — Settings copy sanity

**Steps.** Navigate to Settings → Privacy lock section.

**Expected.**
- Card header reads *"Enable privacy lock"* (not *"Lock the Messages
  tab"*).
- Body reads *"Require your password before the Messages and Wallet
  columns show. Feed, profile, and search stay open."*
- Auto-lock card says *"Re-lock Messages and Wallet after this much
  inactivity."*
- DM notification card mentions *"Wallet has no notifications yet."*
- Caveats card mentions *"the Messages and Wallet columns"*.

**Failure.** Any card still says *"Messages"* only.

## T12 — Rapid navigation between locked scopes

**Steps.** Lock enabled. Both scopes Locked. Rapidly click Messages then
Wallet then Messages in the sidebar (< 200 ms between clicks).

**Expected.** Each route shows its own lock screen with correct scope-
specific title. No flicker to content. No state cross-talk (unlocking
one scope's screen halfway shouldn't unlock the other).

**Failure.** Wrong title on the wrong scope, or content flashes during
navigation.

## Sign-off

- [ ] All 12 tests passed
- Tester: _________
- OS + Amethyst build: _________
- Date: _________
- Notes: _________
