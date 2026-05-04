# NIP-55 Android External Signer

NIP-55 lets the user delegate signing to another Android app (Amber, nos2x-fox, etc.) that holds the private key. Communication is via `Intent`s and a `ContentProvider`, not Nostr itself.

## Layout

Android-only, under `quartz/src/androidMain/kotlin/com/vitorpamplona/quartz/nip55AndroidSigner/`:

```
nip55AndroidSigner/
├── JsonMapperNip55.kt     ── JSON layer for intent extras
├── SignString.kt           ── canonical string to sign for login challenges
├── api/
│   ├── CommandType.kt      ── sign_event / nip04_encrypt / nip44_encrypt / …
│   ├── SignerResult.kt     ── sealed result sent back by launcher callback
│   ├── background/         ── "background" signer path via ContentProvider (no UI)
│   ├── foreground/         ── "foreground" signer path via Activity + Intent
│   └── permission/         ── permission grant / revoke helpers
└── client/
    ├── ExternalSignerLogin.kt       ── one-shot login / bootstrap intent
    ├── IActivityLauncher.kt         ── abstraction over Activity + ActivityResultLauncher
    ├── IsExternalSignerInstalled.kt ── query PM for compatible signers
    ├── NostrSignerExternal.kt       ── the NostrSigner impl
    └── handlers/                    ── per-command result handlers
```

Amethyst's Android app uses `ExternalSignerButton` (`amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedOff/login/ExternalSignerButton.kt`) as the sign-up entry point.

## Two Transport Modes

### Foreground (Activity + Intent)

- Launches an `Intent` with `ACTION_VIEW` and data URI `nostrsigner:<payload>`.
- The signer app opens, shows a UI prompt, returns via `onActivityResult`.
- **Always works**, but requires user interaction each call unless the user has "always allow" granted.
- Lives in `api/foreground/` and `client/handlers/`.

### Background (ContentProvider)

- Queries the signer's `ContentProvider` with `content://<signer-auth>/sign_event?...`.
- No UI interaction — signer either silently approves (if pre-authorized) or denies.
- Requires the user to have granted "always allow" permission beforehand via the foreground flow.
- Lives in `api/background/` and `api/permission/`.
- Falls back to foreground when background denies.

`NostrSignerExternal` picks the path automatically: try background if pre-authorized, else foreground. See `client/handlers/` for the per-command dispatch.

## Command Types

`api/CommandType.kt` enumerates what the external signer supports:

- `sign_event` — sign a Nostr event.
- `nip04_encrypt` / `nip04_decrypt` — legacy DMs.
- `nip44_encrypt` / `nip44_decrypt` — NIP-44 payloads (gift-wrap).
- `get_public_key` — identity check.
- `decrypt_zap_event` — LN zap request decoding.
- `connect` — bootstrap / permissions.

Commands map 1:1 to `NostrSigner` abstract methods.

## Installation Check

Before showing the "Use external signer" button, `IsExternalSignerInstalled.kt` queries the Android PackageManager for intent filters matching `nostrsigner:` URIs. If no compatible app is installed, hide the button (the UI already does this).

## Permission Flow

1. User taps "Use external signer" → `ExternalSignerLogin.launch(activityLauncher)`.
2. Amethyst fires an intent asking the signer for the user's pubkey.
3. Signer app opens, user approves, returns via `onActivityResult`.
4. `NostrSignerExternal` is created with that pubkey and the package name of the approved signer.
5. On subsequent sign requests, Amethyst tries background (via `ContentProvider`); if not granted, falls back to foreground intent.

`api/permission/` has helpers to pre-grant / revoke permissions through the signer's dedicated permission URI.

## Gotchas

- **Activity context required.** All launch paths need an `Activity`, not just a `Context`. Design flows so the launcher is available when sign is called — if a background service needs to sign, it must defer until the app is foregrounded, or show a notification.
- **Foreground loop.** Without "always allow", every single event sign = one Activity round-trip. That's bad UX for reactions / zaps. Push users toward granting always-allow.
- **Multiple signer apps installed.** `IActivityLauncher` honors the one the user first approved, persisted in `AccountSyncedSettings`. Changing signers requires explicit re-login.
- **KMP boundary.** `NostrSignerExternal` is strictly Android; Desktop uses `NostrSignerInternal` or `NostrSignerRemote` (NIP-46 bunker). Don't pretend there's a portable external-signer layer.
- **Test coverage.** Signer Android flows have instrumented tests in `quartz/src/androidDeviceTest/kotlin/.../nip55AndroidSigner/` — device or emulator only.

## Related

- `nip46-remote-signer.md` — the other delegated-signing path (works on all platforms).
- `android-expert/references/android-permissions.md` — Android permission mechanics.
- `android-expert/SKILL.md` — intent / activity-result launcher patterns.
