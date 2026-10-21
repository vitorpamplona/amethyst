---
name: auth-signers
description: NostrSigner in Amethyst: NostrSignerInternal (local key), NostrSignerRemote (NIP-46 bunker), NostrSignerExternal (NIP-55 Android app). Use when signing/encrypting, adding a signer kind, login flows, or debugging sign timeouts.
---

# Signers

Every signed event and every NIP-04/NIP-44 operation goes through a `NostrSigner`. Feature
code never knows which of the three kinds the user has.

## The contract (all `suspend`)

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/signers/NostrSigner.kt`:

```kotlin
abstract class NostrSigner(pubKey: HexKey) {
    open val pubKey: HexKey                       // open: NIP-46 resolves it from the bunker
    abstract fun isWriteable(): Boolean
    suspend fun <T : Event> sign(ev: EventTemplate<T>): T
    abstract suspend fun <T : Event> sign(createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): T
    abstract suspend fun nip04Encrypt(plaintext: String, toPublicKey: HexKey): String
    abstract suspend fun nip04Decrypt(ciphertext: String, fromPublicKey: HexKey): String
    abstract suspend fun nip44Encrypt(plaintext: String, toPublicKey: HexKey): String
    abstract suspend fun nip44Decrypt(ciphertext: String, fromPublicKey: HexKey): String
    abstract suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent
    abstract suspend fun deriveKey(nonce: HexKey): HexKey
    abstract suspend fun signPsbt(psbtHex: String): String   // NIP-BC; may throw UnsupportedMethodException
    abstract fun hasForegroundSupport(): Boolean
    suspend fun decrypt(encryptedContent: String, fromPublicKey: HexKey): String   // picks NIP-04 vs NIP-44
}
```

Failures are thrown as `SignerExceptions` (`nip01Core/signers/SignerExceptions.kt`):
`ReadOnlyException`, `UnauthorizedDecryptionException`, `NothingToDecrypt`,
`AutomaticallyUnauthorizedException`, `ManuallyUnauthorizedException`, `TimedOutException`,
`CouldNotPerformException`, `RunningOnBackgroundWithoutAutomaticPermissionException`,
`SignerNotFoundException`, `UnsupportedMethodException`. Same folder: `NostrSignerSync`
(blocking, for tests/scripts), `EventTemplate.kt`, `caches/` (`DecryptCache`).

## The three signers

| Signer | File | Notes |
|---|---|---|
| `NostrSignerInternal` | `quartz/.../nip01Core/signers/NostrSignerInternal.kt` | key in process memory; created/imported accounts |
| `NostrSignerRemote` | `quartz/.../nip46RemoteSigner/signer/NostrSignerRemote.kt` | NIP-46 bunker over kind 24133; `references/nip46-remote-signer.md` |
| `NostrSignerExternal` | `quartz/src/androidMain/.../nip55AndroidSigner/client/NostrSignerExternal.kt` | NIP-55 app (Amber…) via intents + content provider; Android only; `references/nip55-android-signer.md` |

## Calling it from feature code

Prefer an `Account` method (`commons/.../commons/model/Account.kt`) — e.g. `reactTo(...)`,
`follow(user)` — which builds the template, signs with `account.signer`, updates local
state and broadcasts (`sendMyPublicAndPrivateOutbox`, `sendAutomatic`). If none fits, build
the template from the event class's `build(...)` and `account.signer.sign(template)`.

From a ViewModel, run signer work through `BaseAccountViewModel.launchSigner { … }`
(`commons/.../commons/viewmodels/BaseAccountViewModel.kt`): it runs on IO and turns
`SignerExceptions` into user notices, so don't add your own catch-and-toast. Never retry a
sign yourself: a timeout usually means the user didn't answer in time.

## Login / choosing a signer

- **nsec / hex / ncryptsec** → `NostrSignerInternal`.
- **`bunker://…`** → `NostrSignerRemote.fromBunkerUri(bunkerUri, signer, client)` (`signer`
  is a fresh `NostrSignerInternal` transport key), then `suspend fun connect()`.
- **NIP-55 app** → `ExternalSignerLogin.createIntent(...)`, launch it, then
  `ExternalSignerLogin.parseResult(data)` for the pubkey and package; build
  `NostrSignerExternal(pubKey, packageName, contentResolver)`.

The login form is shared: `commonsUI/.../commons/account/ui/login/` (`LoginScreen`,
`LoginViewModel`, `RemoteSignerLogin`, `KeyTextField`, …) driving
`commons/.../commons/account/AccountSessionManager.kt`. The Android signer-app button,
`amethyst/.../ui/screen/loggedOff/login/ExternalSignerButton.kt`, reaches the form through
`AppPlatform.ExternalSignerLoginButton` (`commonsUI/.../commons/ui/platform/AppPlatform.kt`,
implemented in `amethyst/.../ui/platform/AndroidAppPlatform.kt`).

## Trade-offs

| Signer | Latency | Offline | Key location | UX |
|---|---|---|---|---|
| Internal | µs | yes | app memory | no prompts |
| Remote (NIP-46) | 100 ms – seconds | no (bunker relay must be reachable) | bunker | occasional approvals |
| External (NIP-55) | 100–500 ms | yes | other app | prompt per sign unless pre-approved |

## Gotchas

- **Timeouts are real and long**: `RemoteSignerManager` waits 65 s per request (one retry);
  the NIP-55 foreground path waits 30 s (`IntentRequestManager.foregroundApprovalTimeout`).
  Anything that stamps a time before asking to sign (NIP-98, NIP-42) can expire during the
  prompt — sign right before use.
- **Bunker identity ≠ transport key.** `NostrSignerRemote.pubKey` is the bunker's
  `get_public_key` answer (or `bindUserPubkey(...)` from a saved account); until bound it is the
  transport key. Self-encryption keyed off the wrong one breaks private lists and drafts.
- **External signer needs a foreground Activity** (`IActivityLauncher`); a background caller
  without pre-approved permissions gets `RunningOnBackgroundWithoutAutomaticPermissionException`.
- **NIP-04 is legacy** — new DM code is NIP-17 → `nip44Encrypt`.
- `signPsbt` is unsupported on remote bunkers and older NIP-55 apps; catch
  `UnsupportedMethodException`.

## References

- `references/nip46-remote-signer.md` — bunker request/response taxonomy, connection flow.
- `references/nip55-android-signer.md` — foreground/background transports, command types.
- Related skills: quartz's `nostr-expert` (crypto under the signers), `account-state` (`Account`),
  `android-expert` (activity/intent plumbing).
