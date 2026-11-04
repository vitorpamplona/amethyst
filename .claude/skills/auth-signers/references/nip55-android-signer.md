# NIP-55 Android external signer

Another Android app (Amber, …) holds the key; Amethyst asks it through `Intent`s
(foreground, with UI) or its `ContentProvider` (background, pre-approved). Android only.

## Layout (`quartz/src/androidMain/kotlin/com/vitorpamplona/quartz/nip55AndroidSigner/`)

- `api/CommandType.kt` — `sign_event`, `nip04_encrypt`/`decrypt`, `nip44_encrypt`/`decrypt`,
  `get_public_key`, `decrypt_zap_event`, `derive_key`, `sign_psbt`.
- `api/foreground/` — `IntentRequestManager` (id → channel, launches the intent, waits
  `foregroundApprovalTimeout` = 30 s) and `intents/requests|responses|results/` per command.
- `api/background/queries/` — one `ContentResolver` query per command (`SignQuery`,
  `Nip44EncryptQuery`, `LoginQuery`, …).
- `api/permission/` — `Permission` + its serializers (the permissions requested at login).
- `client/` — `NostrSignerExternal(pubKey, packageName, contentResolver)` (the `NostrSigner`),
  `ExternalSignerLogin` (`createIntent(permissions, packageName)`, `parseResult(intent)`),
  `IActivityLauncher` (`registerForegroundLauncher`, `unregisterForegroundLauncher`,
  `newResponse`, `hasForegroundActivity`), `IsExternalSignerInstalled.kt`
  (`isExternalSignerInstalled(context)`, `getExternalSignersInstalled(context)`),
  `handlers/BackgroundRequestHandler` + `ForegroundRequestHandler`.
- `SignString.kt`, `JsonMapperNip55.kt`.

## Flow

1. Login: `ExternalSignerLogin.createIntent(...)` → the signer app returns pubkey + package →
   `parseResult(data)` → `NostrSignerExternal(pubKey, packageName, contentResolver)`.
2. Each call tries the background query first; if the signer has not pre-approved that
   command, it falls back to a foreground intent through the registered launcher.
3. No registered Activity → `SignerExceptions.RunningOnBackgroundWithoutAutomaticPermissionException`;
   no answer in 30 s → `TimedOutException`.

The Android entry point is
`amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedOff/login/ExternalSignerButton.kt`.

## Gotchas

- Without "always allow", every reaction or zap is an Activity round-trip — bad UX; ask for
  the permissions at login.
- Desktop has no NIP-55; it uses `NostrSignerInternal` or `NostrSignerRemote`.
- Tests: `quartz/src/androidHostTest/.../nip55AndroidSigner/` (JVM host) and
  `quartz/src/androidDeviceTest/.../nip55AndroidSigner/` (device/emulator only).
