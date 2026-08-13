# Key Backup & nsec Exposure — Manual Testing Sheet

Branch: `feat/key-backup-nsec-exposure` (worktree `.claude/worktrees/feat-key-backup`)
Date: 2026-08-11
Build: Desktop `./gradlew :desktopApp:run` · Android `./gradlew :amethyst:installPlayDebug`

Legend: ✅ pass · ❌ fail (note what happened) · ⏭️ skipped

Design invariants to keep verifying throughout:
- **npub** = shareable → plain, copyable, QR OK.
- **nsec** = unrecoverable password → masked by default, gated reveal + gated copy,
  encrypted (NIP-49) option, **NEVER shown as a QR code**.

---

## A. Desktop — New-account warning card (`NewKeyWarningCard`)

Precondition: launch Desktop, log out / add account, choose **Generate New Account**.

- [ ] A1. Card shows title + strengthened warning wording ("can never be reset/recovered").
- [ ] A2. **npub** shown; its **Copy** button copies → paste elsewhere matches the npub.
- [ ] A3. **nsec** shown; its **Copy** button copies → paste matches the nsec (`nsec1…`).
- [ ] A4. **Copy encrypted (recommended)**: type a password → button copies an `ncryptsec1…`
      string (paste to verify prefix). Empty password → button disabled / no-op.
- [ ] A5. **"I have saved my keys"** checkbox: Continue is disabled until checked (soft gate);
      checking it enables Continue.
- [ ] A6. Continue proceeds into the app with the generated account logged in.
- [ ] A7. **No QR code** anywhere on this card.

## B. Desktop — Backup Keys card in Settings/Profile (`BackupKeysCard`)

Precondition: logged in with an **internal-key** account (has nsec). Open Settings → Profile.

- [ ] B1. "Backup Keys" card is visible in the profile/settings screen (discoverable — the
      original complaint was "couldn't find it in settings").
- [ ] B2. **npub** row: monospace value + **Copy** works; **Show QR** renders a QR; **Hide QR** hides it.
- [ ] B3. QR scans/points to the npub (optional: scan with a phone).
- [ ] B4. **nsec** section: warning banner shown; key is **masked** ("hidden" placeholder),
      not revealed on load.

### B-lock. Reveal gating via PrivacyLock

Case 1 — PrivacyLock **NOT** set up (no master password configured):
- [ ] B5. Click **Reveal secret key** → nsec reveals immediately (baseline: masked + explicit
      toggle, no password). Acceptable per design.

Case 2 — PrivacyLock **enabled** (set a master password in Messages/Wallet privacy-lock settings first):
- [ ] B6. Click **Reveal secret key** → a **modal Dialog** appears asking to unlock (does NOT
      take over / expand the whole settings pane).
- [ ] B7. Wrong password → stays locked; **Cancel/dismiss** the dialog → nsec stays masked.
- [ ] B8. Correct password → dialog closes, nsec reveals.

### B-copy. Revealed secret-key actions
- [ ] B9. **Copy secret key** (plaintext) copies the `nsec1…`; a red plaintext warning is visible.
- [ ] B10. **Clipboard auto-clear**: after copying plaintext nsec, wait ~60s without copying
      anything else → paste → clipboard is **empty**. If you copy something else within 60s,
      that value is **preserved** (auto-clear only wipes if clipboard still holds the nsec).
- [ ] B11. **Copy encrypted (recommended)**: enter password → copies `ncryptsec1…`; toggle the
      password visibility eye works; wrong/blank handled (button disabled while blank; failure
      shows the error supporting text). Encrypted copy is **not** auto-cleared (it's password-safe).
- [ ] B12. **Hide** returns the section to masked state; leaving the screen and returning re-hides.
- [ ] B13. **No QR** is ever offered for the nsec.

## C. Desktop — External-signer / read-only account

Precondition: log in with an **external signer / bunker (NIP-46)** or a **read-only npub**.

- [ ] C1. Backup Keys card shows the npub section normally.
- [ ] C2. nsec section is replaced by the "This account uses an external signer — no secret key
      is stored here" note. No reveal/copy controls, no masked field.

---

## D. Android — Post-signup backup nudge

Precondition: fresh install or logged out. **Create a NEW account** (generate).

- [ ] D1. After signup lands on the home feed, a dismissible **"Back up your keys"** nudge/banner
      appears (top of feed, above the algo-feed status banner; does not block navigation).
- [ ] D2. **Back up now** → opens the existing Account Backup screen; returning home, the nudge
      is gone (flag flipped).
- [ ] D3. Re-create another new account → **I saved them** (or the X) dismisses the nudge.
- [ ] D4. Kill & relaunch the app → the dismissed nudge does **not** reappear for that account
      (per-account `hasBackedUpKeys` persisted in encrypted prefs).

## E. Android — Which accounts get nudged

- [ ] E1. Log in with an **existing nsec** (paste key) → **no** nudge (treated as already backed up).
- [ ] E2. Log in with **bunker / external signer** → **no** nudge.
- [ ] E3. Read-only **npub** login → **no** nudge (no private key).
- [ ] E4. Multiple accounts: a freshly-generated account is nudged; switching to a
      pre-existing account shows no nudge (flag is per-account).

## F. Android — Backup screen hardening (`AccountBackupScreen`)

- [ ] F1. **FLAG_SECURE**: on the Account Backup screen, attempt a screenshot → blocked by the OS
      ("can't take screenshots due to security policy") and the app-switcher/recents preview shows
      a blank/black thumbnail for this screen.
- [ ] F2. Navigating away from the backup screen → screenshots work again elsewhere (flag cleared,
      no leak to other screens).
- [ ] F3. Existing **biometric gate** on copy/QR still prompts and works.
- [ ] F4. **Copy secret key** (plaintext) → toast shown; **clipboard auto-clear** after ~60s
      empties the clipboard if unchanged; a value copied in the meantime is preserved.
- [ ] F5. **Encrypted (ncryptsec1) copy** and the **plaintext / encrypted QR codes** still work
      as before (regression check — these are pre-existing).

---

## G. Cross-cutting — NIP-49 round trip (correctness)

- [ ] G1. Desktop: encrypted-copy the nsec with password `P` → you have an `ncryptsec1…`.
- [ ] G2. Log in (Desktop or Android) using that `ncryptsec1…` + password `P` → succeeds and
      resolves to the **same** account (same npub). Confirms the nsec→hex decode + Nip49 encrypt
      are correct end-to-end.
- [ ] G3. Wrong password on login with the ncryptsec → rejected (no crash).

## H. Regression / smoke

- [ ] H1. Desktop **Developer Settings** key rows still copy (shared `copyToClipboard` refactor
      didn't break them).
- [ ] H2. Privacy lock still gates **Messages** and **Wallet** as before (adding `KeyBackup`
      scope didn't disturb existing scopes).
- [ ] H3. Normal posting on Android still works (paste-guard was **deferred** — a note containing
      an `nsec1…` currently posts without a warning; confirm posting itself is unaffected).

---

## Known limitations / deferred (expected, not bugs)
- **Compose paste-guard** (warn before posting a note that contains an `nsec1…`) is **deferred** —
  the send path is reimplemented across ~17 `*PostViewModel`s with no shared choke point.
- **Desktop reveal without PrivacyLock** is protected only by masked + explicit toggle (no
  password), by design — the master-password gate only engages if the user set one up.
- **Clipboard auto-clear** is best-effort (equality-guarded, 60s); the OS may surface its own
  sensitive-clipboard UI on Android 13+.

## Sign-off
- Tester: __________  Date: __________
- Desktop OS: __________  Android version/device: __________
- Overall: ☐ ready for PR  ☐ needs fixes (list): __________
