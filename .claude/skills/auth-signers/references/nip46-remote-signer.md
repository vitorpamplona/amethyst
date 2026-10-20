# NIP-46 remote signer (bunker)

A bunker holds the user's key and signs on request. Amethyst talks to it with kind-24133
`NostrConnectEvent`s, NIP-44-encrypted between a local **transport** keypair and the
bunker's pubkey.

## Layout (`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/`)

- `BunkerRequest*.kt` — `Connect`, `GetPublicKey`, `GetRelays`, `Ping`, `Sign`,
  `Nip04Encrypt/Decrypt`, `Nip44Encrypt/Decrypt`, `Invalid`; `BunkerRequestParser.kt`.
- `BunkerResponse*.kt` — `Ack`, `Event`, `Encrypt`, `Decrypt`, `PublicKey`, `GetRelays`,
  `Pong`, `Error`.
- `NostrConnectEvent.kt` (kind 24133), `NostrConnectURI.kt`, `BunkerMessage.kt`,
  `kotlinSerialization/`.
- `signer/` — the client side: `NostrSignerRemote` (the `NostrSigner`),
  `RemoteSignerManager` (request/response correlation), per-method `*Response` parsers,
  `SignerResult`.
- `server/` — the bunker side Quartz can also run: `NostrConnectSignerService`,
  `BunkerRequestProcessor`, `Nip46RequestAuthorizer`.

## Connection

```
bunker://<bunker-pubkey>?relay=wss://…&secret=…
  -> NostrSignerRemote.fromBunkerUri(uri, transportSigner: NostrSignerInternal, client)
  -> remote.connect()            // BunkerRequestConnect(remoteKey, permissions, secret, clientMetadata)
  -> remote.getPublicKey()       // binds the user identity
```

`fromBunkerUri` takes optional `permissions`, `clientMetadata` and `onAuthUrl`, the callback for
NIP-46 `auth_url` challenges (surface the URL; the request keeps waiting on the same id). The
apps pass `RemoteSignerAuthPrompts::report` (`commons/.../domain/nip46/`), from the login use
cases and `AccountCacheState` alike; `RemoteSignerAuthPrompt` in `AmethystApp` shows each page
with an Open button, and only `http(s)` pages get through. The CLI prints the URL instead.

`switchRelays()` sends NIP-46 `switch_relays`; a new list moves the requests and the response
subscription (`relays` is mutable, the subscription a `DynamicSubscription`) and fires
`onRelaysSwitched`. `AccountCacheState` asks it whenever it loads a remote-signer account and
saves the new relays into `remoteSignerBunkerUri`; `AccountsTorStateConnector` re-reads that on
every settings save. `logout()` sends NIP-46 `logout`: `AccountSessionManager.logOff` runs
`RemoteSignerLogoutUseCase` (its own short-lived signer on the saved transport key, bounded, never
throws) before the account's settings are deleted. The CLI sends neither.

On the signer side (`server/`), `BunkerRequestProcessor`'s `switchRelays` hook answers
`switch_relays` (paired clients only; strangers get `null`). `Nip46SignerState` uses it to move a
`nostrconnect://` app, whose session sits on relays the app picked, onto the account's NIP-65
inbox relays, keeping only those whose NIP-11 doesn't require auth, payment or restricted writes
(`acceptsSignerTraffic`). The app's own relays leave the listen set only once one of its requests
arrives on an inbox relay (`NostrConnectSignerService.onRequestSeenOn`), so an app that never got
the answer isn't stranded.

`name@domain` logins: `Nip05Client.loadRemoteSigner` reads the domain's `nip46` (the legacy
`nip46[pubkey]` relays first, then the spec's `name=_` announcement with `nostrconnect_url`), and
`Nip05SignerLoginUseCase` logs in through it — no-secret connect, or the signer's page carrying a
`nostrconnect://` code, shown through `RemoteSignerAuthPrompts` — and requires the account to be the
address's key. `AccountSessionManager` and `amy login` fall back to read-only without a signer.

## Identity

The constructor's `signer` is the transport key, **not** the user. `NostrSignerRemote.pubKey`
returns the identity resolved by `getPublicKey()` or set with `bindUserPubkey(...)` (persisted
accounts, the CLI's stored identity), and falls back to the transport key until then. Anything
that encrypts to self or checks authorship must use `pubKey` after binding. Transport plumbing
(response `p` filter, request addressing) uses `signer.pubKey`/`remotePubkey` on purpose.

## Request lifecycle (`RemoteSignerManager.launchWaitAndParse`)

Each call creates a request with a random id, publishes it to the bunker relays
(`client.publish(event, relayList)`), parks a channel in a `LargeCache` keyed by id, and waits
up to `timeout` (65 s). No answer → after 2 s, one retry that resends the same event under the same id → then
`SignerResult.RequestAddressed.TimedOut`, which `NostrSignerRemote` throws as
`SignerExceptions.TimedOutException`. Concurrent requests never interleave because responses
route by id (`newResponse`).

## Gotchas

- Show progress for long waits; approval can take tens of seconds.
- Only Nostr relays carry the traffic — a feature that can sign offline cannot with a bunker.
- `signPsbt` is not implemented for remote bunkers (`UnsupportedMethodException`).
