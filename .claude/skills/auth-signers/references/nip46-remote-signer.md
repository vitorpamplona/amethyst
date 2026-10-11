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

`fromBunkerUri` takes optional `permissions` and `clientMetadata`; the constructor also takes
`onAuthUrl` for NIP-46 `auth_url` challenges (surface the URL; the request keeps waiting on
the same id).

## Identity

The constructor's `signer` is the transport key, **not** the user. `NostrSignerRemote.pubKey`
returns the identity resolved by `getPublicKey()` or set with `bindUserPubkey(...)` (persisted
accounts, the CLI's stored identity), and falls back to the transport key until then. Anything
that encrypts to self or checks authorship must use `pubKey` after binding. Transport plumbing
(response `p` filter, request addressing) uses `signer.pubKey`/`remotePubkey` on purpose.

## Request lifecycle (`RemoteSignerManager.launchWaitAndParse`)

Each call creates a request with a random id, publishes it to the bunker relays
(`client.publish(event, relayList)`), parks a channel in a `LargeCache` keyed by id, and waits
up to `timeout` (65 s). No answer → one retry with a fresh id after 2 s → then
`SignerResult.RequestAddressed.TimedOut`, which `NostrSignerRemote` throws as
`SignerExceptions.TimedOutException`. Concurrent requests never interleave because responses
route by id (`newResponse`).

## Gotchas

- Show progress for long waits; approval can take tens of seconds.
- Only Nostr relays carry the traffic — a feature that can sign offline cannot with a bunker.
- `signPsbt` is not implemented for remote bunkers (`UnsupportedMethodException`).
