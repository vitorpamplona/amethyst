# First-Run "Save Your Keys" Onboarding — Plan (Desktop)

Date: 2026-08-11
Branch: `feat/key-backup-nsec-exposure`
Supersedes: the single `NewKeyWarningCard` for freshly-generated accounts.

## Why

New-account key backup is the highest-stakes moment in a Nostr client: the
`nsec` is shown once and can never be reset. Current impl crams warning + npub +
nsec + encrypted-copy + checkbox + continue into one `NewKeyWarningCard`. Inside
the 480px add-account dialog it has no scroll, so it clips — labels above and the
Continue button below are cut off → reads as "no label / no way forward". Even
un-clipped, one dense card is confusing.

Decision (user): **guided multi-step full-screen flow**, **soft checkbox gate**.

## What already exists (reuse — don't rebuild)

- **Split generation** (just built): `AccountManager.buildNewAccount()` creates the
  keypair WITHOUT activating it; `activateAccount(state)` flips account state.
  This is what lets a backup step render before the account switch tears the
  screen down. Keep.
- **Entry points** that generate a key:
  - Cold start: `LoginScreen` (`onGenerateNew` → `buildNewAccount()`).
  - Logged-in: `AddAccountDialog` (`onGenerateNew` → `buildNewAccount()`), confirmed
    via `onNewAccountConfirmed` on the App scope.
- **Pieces to lift out of `NewKeyWarningCard`**: `CopyKeyButton`, `EncryptedCopyRow`
  (NIP-49 password → `ncryptsec1`), `SelectableKeyText`, the warning strings
  (`new_key_*`). The `nsec→hex` decode (`decodePrivateKeyAsHexOrNull`) + `Nip49`.
- **QR**: `QrCodeCanvas(data)` — npub only, never nsec.
- **Settings** backup card (`BackupKeysCard`) already covers "view my keys later".

## Design — `NewKeyOnboardingScreen` (stepper, 3 steps)

A full-window composable (NOT a dialog). Rendered while `buildNewAccount()` result
is held and before `activateAccount`. Fixed max content width (~560dp), centered,
each step vertically scrollable so nothing clips.

**Step 1 — Why this matters (education)**
- Headline "Save your keys" + plain-language explainer: npub = your public
  identity (shareable); nsec = a password that can never be reset or recovered —
  lose it and the account is gone. No inputs. [Next].

**Step 2 — Your keys (the actual backup)**
- **Public key (npub)**: monospace, Copy, optional Show QR.
- **Secret key (nsec)**: monospace (shown — this is the one moment we intentionally
  reveal it), **Copy** (primary, prominent) + best-effort clipboard auto-clear.
- **Copy encrypted (recommended)**: collapsible/secondary — password field →
  `ncryptsec1`. Keep out of the way so the primary Copy is obvious.
- Strong "never share the nsec" inline warning. No nsec QR. [Back] [Next].

**Step 3 — Confirm & continue (soft gate)**
- Recap one line ("Stored somewhere safe? A password manager is ideal.").
- **Soft checkbox** "I have saved my keys somewhere safe" → enables **Continue**.
  (Honor-system, matches the Android nudge decision. No copy/verify enforcement.)
- Continue → `activateAccount(state)` + persist + proceed into the app.

Progress indicator (e.g. "Step 2 of 3" or dots). [Back] on 2/3.

## Integration

- **New file**: `desktopApp/.../ui/auth/NewKeyOnboardingScreen.kt` (stepper +
  step composables; reuse the extracted Copy/Encrypt pieces).
- **Cold start**: in `LoginScreen`, when `generatedAccount != null` render the
  onboarding screen full-bleed instead of the inline card. `onFinish` =
  `activateAccount` + `onLoginSuccess`.
- **Add-account**: when a key is generated, DON'T keep it inside the small dialog.
  Either (a) dismiss the dialog and show the onboarding screen at the App level
  (preferred — full space, survives the later account switch), or (b) let the
  onboarding screen be the dialog's content at a larger, scrollable size. Pick (a)
  for consistency with the cold-start path: hoist a top-level
  `pendingNewAccount: AccountState.LoggedIn?` in the App composable; both entry
  points set it; one `NewKeyOnboardingScreen` renders when non-null; `onFinish`
  activates + persists on the App scope. This unifies both flows through one
  screen and removes the dialog-clipping problem entirely.
- **Retire** `NewKeyWarningCard` (and its Preview) once both paths use the stepper.

## Testing (add to the manual sheet)
- Cold start: log out → Generate → 3-step screen, no clipping, Copy works,
  encrypted copy → `ncryptsec1`, checkbox gates Continue, Continue lands in app.
- Add account (already logged in): Add → Generate → same screen at App level (not a
  cramped dialog), Continue switches to the new account + persists (survives
  restart).
- Back/Next preserve state; window resize keeps everything reachable (scroll).
- NIP-49 round trip (encrypted copy → login elsewhere → same npub).

## Non-goals / follow-ups
- Android first-run guided screen (Android already has `AccountBackupScreen` +
  post-signup nudge; a matching stepper is a separate follow-up).
- Hard copy/verify enforcement (explicitly declined — soft gate).
- Mnemonic (NIP-06) backup.

## Unanswered questions
- Add-account path: hoist to App-level full-screen (recommended) vs enlarge the
  dialog to host the stepper? (Plan assumes hoist.)
- Does generating from the logged-in state and cancelling mid-onboarding need an
  explicit "discard this new key" confirm, or is silent discard fine?
- Show the nsec revealed by default on Step 2 (it's first-run, user must save it) —
  or masked-with-reveal like the Settings card? (Plan assumes shown, since the
  whole point is to save it now.)
- Progress UI: numbered "Step X of 3" vs dots vs a top wizard bar?
- Keep the `NewKeyWarningCard` as a fallback anywhere, or fully delete?
