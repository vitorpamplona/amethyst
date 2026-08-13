# Key Backup & nsec Exposure — Plan

Date: 2026-08-11
Scope: Desktop (`desktopApp`) primary, Android (`amethyst`) secondary, shared strings/logic in `commons`/`quartz`.

## Problem

User report (Nostr): new account creation gives no discoverable way to
find/save the keypair. "Couldn't find it in settings, no option to save when
generated." Keys are crucial → must reinforce, not obfuscate.

Ask: make nsec discoverable + backupable; npub sharable (copy/QR); check both
Android and Desktop.

## Key asymmetry (design principle — non-negotiable)

Every surveyed client agrees:
- **npub** = public identity → plain by default, copy + QR, share freely.
- **nsec** = password that can NEVER be reset → masked by default, gated
  reveal + gated copy, **never rendered as a QR**, prefer encrypted (NIP-49).

So "show nsec with npub for sharing" splits: npub is for sharing; nsec is for
**private backup only**. The plan treats them differently.

## Current state

### Android (`amethyst`) — mostly done, one gap
- `ui/.../keyBackup/AccountBackupScreen.kt` — full backup screen: biometric-gated
  copy nsec, NIP-49 encrypted (ncryptsec1) copy, plaintext + encrypted QR,
  strong warnings (`account_backup_tips2_md`/`tips3_md`). Route `AccountBackup`.
- **Gap**: signup (`SignUpViewModel.signup` → `AccountSessionManager.createNewAccount`,
  `val keyPair = KeyPair()`) generates silently. **No post-signup nudge** to back
  up. Screen exists but discoverability relies on the user hunting the drawer.

### Desktop (`desktopApp`) — large gaps
- `ui/auth/NewKeyWarningCard.kt` — shows npub+nsec once at creation as plain
  `SelectableKeyText` (manual-select). No copy button, no QR, no encrypted
  option, weak warning.
- `ui/DevSettingsSection.kt` — full copy UI but **debug-mode only**; normal
  users can't re-reach nsec.
- `ui/profile/ProfileInfoCard.kt` — npub + hex only (no copy? verify), no QR.
- **No user-facing Backup Keys screen. No settings path to nsec. No NIP-49
  export. No QR for npub.**
- `AccountState.LoggedIn` already exposes `.npub`, `.nsec`, `.pubKeyHex` →
  wiring a backup screen is trivial.
- `commons/jvmMain/.../keystorage/SecureKeyStorage.kt` — OS keychain + encrypted
  fallback; can retrieve raw key.

### Shared / quartz (reuse — don't rebuild)
- `nip19Bech32/ByteArrayExt.kt`: `toNsec()`, `toNpub()`.
- `nip49PrivKeyEnc/Nip49.kt`: `encrypt()/decrypt()` ncryptsec1.
- Android QR: `ui/.../qrcode` (`QrCodeDrawer`). Desktop QR: `QrCodeCanvas.kt`
  (currently only NIP-46/NIP-47 URIs) — reuse for npub.

## Recommended design

Adopt the **hidden-password camp** (Amethyst Android's existing model) +
**persistent post-signup backup nudge** (Snort pattern). Rationale: matches
Android, keeps signup fast, avoids a hard gate, but nags until backed up.

Firm rules across both platforms:
1. nsec masked by default; reveal is an explicit gated action.
2. Copy nsec is itself gated (copy is the real leak vector) + shows warning.
3. Offer **NIP-49 encrypted (ncryptsec1)** copy/export alongside plaintext.
4. npub always plain, copyable, QR. **nsec never QR.**
5. Hide the entire private-key section for **external-signer / bunker / read-only**
   sessions (`nsec == null`) — show "This account uses an external signer" note.
6. Explicit **"cannot be recovered"** warning verbatim-strong (reuse Android's
   `account_backup_tips2_md`).

Platform reveal-gate:
- **Android**: biometric (already wired in `AccountBackupScreen`).
- **Desktop**: no biometric → gate reveal/copy behind a confirm dialog
  ("Show secret key?" YES/CANCEL) + `showKeys` toggle, matching DevSettings but
  user-facing and with the encrypted option. (Optional later: OS auth via
  existing PrivacyLock/master-password machinery if present — verify.)

## Implementation

### Phase 1 — Desktop Backup Keys screen (biggest gap)
- New `desktopApp/.../ui/settings/BackupKeysScreen.kt` (or `.../ui/keyBackup/`):
  - npub row: plain, Copy, "Show QR" (reuse `QrCodeCanvas`).
  - nsec section: masked → confirm-dialog reveal → Copy (plaintext) + "Copy
    encrypted" (password field → `Nip49().encrypt`). Strong warning banner.
  - Hidden when `account.nsec == null` (external/read-only) → info note.
- Add entry point in Settings sidebar/account area (near `ProfileInfoCard` in
  `Main.kt` ~2191). Label "Backup Keys" / "Account Keys".
- Reuse `AccountState.LoggedIn.{npub,nsec,pubKeyHex}`; clipboard via existing
  `copyToClipboard` (AWT) — factor out of `DevSettingsSection`.

### Phase 2 — Desktop NewKeyWarningCard upgrade
- Add Copy button per key (not just selection).
- Add "Copy encrypted (recommended)" + password field.
- Strengthen warning copy to match Android "no recovery" strength.
- Add "I've saved my keys" acknowledgement affordance before `onContinue`
  (soft; not a hard checkbox gate — see open Q).
- Do NOT add nsec QR.

### Phase 3 — Android post-signup backup nudge
- After `signup()`, route to / surface a dismissible "Back up your keys" prompt
  with "Back up now" (→ `AccountBackupScreen`) / "I already saved them".
- Persist "backed up" flag per account; keep nudging (home banner or settings
  badge) until acknowledged. No silent dismissal.

### Phase 4 — Shared polish (both platforms)
- Copy-warning string on every nsec copy ("like a password, cannot be reset").
- `FLAG_SECURE` (Android) on reveal to redact screenshots/recents. Desktop:
  no direct equivalent — skip.
- (Stretch) timed clipboard auto-clear after nsec copy — genuinely novel, no
  client does it. Verify feasibility (Android `ClipboardManager`, Desktop AWT).
- (Stretch) Coracle-style paste-guard on compose: warn if a note body starts
  with `nsec1`.

## Testing
- Desktop: generate account → reveal/copy plaintext + encrypted → decrypt
  round-trips (`Nip49`) → QR shows npub not nsec → external-signer account hides
  nsec section. Manual sheet.
- Android: signup → nudge appears → backup screen reachable → encrypted copy
  round-trips. Existing `AccountBackupScreen` unit coverage if any.
- Reuse quartz `Nip49` tests; add commons test for any extracted helper.

## Non-goals / deferred
- iOS (no mature target yet).
- BIP-39 / NIP-06 mnemonic backup (Snort) — separate feature.
- Full OS-biometric gate on Desktop (no primitive) — confirm dialog instead.

## Unanswered questions
- Design camp: confirm hidden-password + nudge (recommended) vs. keep Desktop's
  show-at-creation as the primary backup moment?
- Hard "I saved it" checkbox gate at signup, or soft dismissible nudge? (survey:
  soft wins; hard gate largely unclaimed.)
- Desktop reveal gate: confirm-dialog only, or wire existing PrivacyLock/master
  password if one exists? (verify what Desktop already has.)
- Extract a shared `commons` backup composable, or keep Android + Desktop
  screens separate (Android biometric vs Desktop dialog diverge)?
- Timed clipboard auto-clear: in scope now or stretch?
- Paste-guard on compose (nsec self-doxx prevention): this feature or separate?
- Does Desktop `ProfileInfoCard` already copy npub / need a QR button there too?
