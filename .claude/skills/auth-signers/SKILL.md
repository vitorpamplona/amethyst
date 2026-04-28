---
name: auth-signers
description: Signer abstraction patterns in Amethyst. Use when working with event signing, choosing between a local keypair (`NostrSignerInternal`), a remote NIP-46 bunker signer (`NostrSignerRemote`), or a NIP-55 Android external-app signer (`NostrSignerExternal`). Covers the abstract `NostrSigner` base class, `SignerResult` contract, how to wire a new flow that needs to sign events, and the security/UX trade-offs between signer kinds.
---

# Auth & Signers

Any time Amethyst produces a signed Nostr event, it goes through a `NostrSigner`. There are three kinds; all three implement the same abstract contract so feature code doesn't care which one the user has configured.

## When to Use This Skill

- Adding a new flow that publishes an event (follow, post, react, zap, profile edit).
- Reviewing whether a feature works when the user has a remote bunker signer or an external Android signer.
- Debugging "Sign request approved but nothing happens" / timeouts on sign operations.
- Onboarding a new signer kind (hardware signer, browser extension, etc.).
- Understanding the NIP-46 bunker request/response taxonomy.

## The Abstract Contract

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/signers/NostrSigner.kt`:

```kotlin
abstract class NostrSigner(val pubKey: HexKey) {
    abstract fun <T : Event> sign(
        template: EventTemplate<T>,
        onReady: (T) -> Unit,
    )
    abstract fun nip04Encrypt(plaintext: String, toPubKey: HexKey, onReady: (String) -> Unit)
    abstract fun nip04Decrypt(ciphertext: String, fromPubKey: HexKey, onReady: (String) -> Unit)
    abstract fun nip44Encrypt(...)
    abstract fun nip44Decrypt(...)
    abstract fun decryptZapEvent(event: LnZapRequestEvent, onReady: (LnZapRequestEvent) -> Unit)
}
```

Sibling files in the same folder:

- **`NostrSignerInternal.kt`** — in-process signer with the user's seckey in memory. Fastest; used for locally-stored accounts.
- **`NostrSignerSync.kt`** — blocking wrapper for scripts / migrations / tests where callbacks are inconvenient.
- **`EventTemplate.kt`** — the unsigned holder passed to `sign()`.
- **`SignerExceptions.kt`** — the error taxonomy (user denied, timeout, unsupported method, etc.).
- **`caches/`** — request cache so duplicate sign/encrypt requests coalesce.

### Concrete implementations

- **Local (in-process)**: `NostrSignerInternal` — direct `Secp256k1Instance.signSchnorr` + NIP-44 inline. Used by accounts created/imported into Amethyst.
- **Remote (NIP-46 bunker)**: `quartz/.../nip46RemoteSigner/signer/NostrSignerRemote.kt`. Talks to a bunker service over Nostr DMs using the `BunkerRequest*` / `BunkerResponse*` event taxonomy (`BunkerRequestConnect`, `BunkerRequestSign`, `BunkerRequestNip44Encrypt`, …).
- **Android external (NIP-55)**: `quartz/src/androidMain/.../nip55AndroidSigner/client/NostrSignerExternal.kt`. Uses Android intents + content provider to delegate to another app on the same device. Launcher: `ExternalSignerLogin.kt`, `IActivityLauncher.kt`. Install-check: `IsExternalSignerInstalled.kt`.

## The `SignerResult` Contract

Signers return via callback (and internally track via `SignerResult` sealed types in `nip46RemoteSigner/signer/SignerResult.kt` and `nip55AndroidSigner/api/SignerResult.kt`). Result variants cover success, user-denied, timeout, remote-disconnected, unsupported. Feature code should:

1. Pass a callback that handles success.
2. Trust the cache/timeout behavior — don't roll your own retry.
3. Surface `SignerExceptions` to the user with actionable messaging (e.g. "Bunker disconnected — reconnect?").

## Typical Flow (Feature Code)

```kotlin
// High-level: Account methods already do this internally.
val signer: NostrSigner = account.signer     // whichever kind the user configured

val template = reactionEventTemplate(noteId, authorPubKey, "+")

signer.sign(template) { signed ->
    account.sendToRelays(signed)             // or similar pipeline
}
```

Most feature code should go through `Account`'s mutation methods (`account.sendReaction`, `account.follow`) rather than touching the signer directly — the account layer handles signing + publishing + local state update atomically. Reach for the signer directly only when `Account` doesn't have a helper.

## Choosing a Signer at Sign-Up

Entry points:

- **Existing private key** (`nsec`, 32-byte hex, file) → `NostrSignerInternal`.
- **Bunker URL** (`bunker://...`) → `RemoteSignerManager.connect(url)` in `nip46RemoteSigner/signer/RemoteSignerManager.kt` returns a `NostrSignerRemote`.
- **Installed external signer app** (Amber, nos2x, etc. on Android) → `ExternalSignerLogin.launch(...)` opens the signer app; approval yields a `NostrSignerExternal`.

The UI hosts both flows via `amethyst/.../ui/screen/loggedOff/login/` — look there for `ExternalSignerButton.kt` and the bunker-URL paste screen.

## Trade-offs

| Signer | Latency | Offline OK? | Security | UX |
|--------|---------|-------------|----------|-----|
| Internal | µs | Yes | Key in app memory | No confirmation prompts |
| Remote (NIP-46) | 100ms–seconds | No (needs bunker reachable) | Key never touches Amethyst | Occasional approval prompts |
| External (NIP-55) | 100–500ms | Yes | Key in separate app | Prompt on every sign by default (configurable) |

## Gotchas

- **Callbacks may never fire.** External signers can be dismissed without result; remote signers can time out. Use `SignerExceptions` / timeout handling at every call site or rely on the `Account` layer's wrapping.
- **`nip04Encrypt` is legacy** for NIP-04 DMs. New DM code should use NIP-17 gift-wrap → `nip44Encrypt` path.
- **Don't cache signer output** beyond the `caches/` that quartz already maintains. Stale cache entries lead to duplicate publishes.
- **Remote signer disconnects** need explicit reconnection UX — `RemoteSignerManager` exposes state; hook into it for an account-switching warning.
- **External signer launch requires an Activity context** — it can't happen from a background service. Structure flows so signing is on the main dispatcher through an activity-scoped launcher.
- **`NostrSignerSync`** is rare. If you reach for it, you're probably in a test or migration — production code uses the async API.

## References

- `references/nip46-remote-signer.md` — the NIP-46 bunker message taxonomy and connection lifecycle.
- `references/nip55-android-signer.md` — Android intent-based external signer flow.
- Complements: `nostr-expert/references/crypto-and-encryption.md` (the crypto under all signers), `account-state` (which wraps signer calls), `android-expert` (intent launcher patterns).
