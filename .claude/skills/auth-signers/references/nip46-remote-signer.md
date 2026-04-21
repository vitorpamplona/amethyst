# NIP-46 Remote Signer (Bunker)

Remote signers are a different Nostr client (the "bunker") that holds the private key and signs on request over Nostr DMs. Amethyst connects to a bunker via a `bunker://` URL and proxies every signing / encryption operation as a request/response over kind 24133 events.

## Layout

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/`:

```
nip46RemoteSigner/
├── BunkerMessage.kt            ── common wrapper
├── BunkerRequest.kt            ── sealed base for requests
├── BunkerRequestConnect.kt
├── BunkerRequestGetPublicKey.kt
├── BunkerRequestGetRelays.kt
├── BunkerRequestNip04Decrypt.kt
├── BunkerRequestNip04Encrypt.kt
├── BunkerRequestNip44Decrypt.kt
├── BunkerRequestNip44Encrypt.kt
├── BunkerRequestPing.kt
├── BunkerRequestSign.kt
├── BunkerResponse.kt           ── sealed base for responses
├── BunkerResponseAck.kt
├── BunkerResponseDecrypt.kt
├── BunkerResponseEncrypt.kt
├── BunkerResponseError.kt
├── BunkerResponseEvent.kt
├── BunkerResponseGetRelays.kt
├── BunkerResponsePong.kt
├── BunkerResponsePublicKey.kt
├── NostrConnectEvent.kt        ── kind 24133 payload
├── kotlinSerialization/        ── JSON codecs for each request/response
└── signer/
    ├── NostrSignerRemote.kt       ── the NostrSigner impl
    ├── RemoteSignerManager.kt     ── connection lifecycle
    ├── ConnectResponse.kt
    ├── Nip04DecryptResponse.kt
    ├── Nip04EncryptResponse.kt
    ├── Nip44DecryptResponse.kt
    ├── Nip44EncryptResponse.kt
    ├── PingResponse.kt
    ├── PubKeyResponse.kt
    ├── SignerResult.kt
    └── SignResponse.kt
```

## Connection Flow

```
User pastes bunker://<bunker-pubkey>?relay=wss://…&secret=…
              │
              ▼
RemoteSignerManager.connect(url) generates a client keypair
              │
              ▼
Publish BunkerRequestConnect (kind 24133, encrypted) to relay
              │
              ▼
Wait for BunkerResponseAck or BunkerResponseError
              │
              ▼ on ack
Return a NostrSignerRemote(clientKeys, bunkerPubKey, relays)
```

The client keypair is **not** the user's Nostr identity — it's a session key used to correspond with the bunker. The bunker controls the real signing key. `RemoteSignerManager` persists session state so reconnecting skips the handshake.

## Request / Response Pattern

Every signing or encryption call is an async round-trip:

```
Amethyst                              Bunker
   │                                   │
   │── BunkerRequestSign(template) ──►│
   │                                   │  user approves (sometimes)
   │◄── BunkerResponseEvent(signed) ──│
   │                                   │
```

`NostrSignerRemote.sign(template, onReady)` serializes the `BunkerRequestSign`, encrypts it to the bunker's pubkey, publishes it, and waits (with a timeout) for a `BunkerResponseEvent` carrying the signed event. The response goes through a request-id correlation map so concurrent signs don't interleave.

## Supported Requests

- `BunkerRequestConnect` / `BunkerRequestPing` — lifecycle.
- `BunkerRequestGetPublicKey` — verify what pubkey this session controls.
- `BunkerRequestGetRelays` — discover the bunker's preferred inbox relays.
- `BunkerRequestSign` — the main path.
- `BunkerRequestNip04Encrypt`/`Decrypt` — legacy DMs.
- `BunkerRequestNip44Encrypt`/`Decrypt` — NIP-44 payloads (gift-wrap, modern DMs).

Each has a matching response with the same correlation id.

## Timeouts & Disconnects

- **Timeout**: default in `NostrSignerRemote`. Expired requests surface as `SignerExceptions.TimedOut` (or equivalent). Treat as "maybe the user will approve later but UI has given up" — don't auto-retry.
- **Relay disconnect**: `RemoteSignerManager` reconnects transparently when the bunker's relay reappears; in-flight requests may still time out.
- **Bunker revoke**: if the bunker closes the session, next sign attempt returns `BunkerResponseError`; prompt the user to reconnect.

## Gotchas

- **The session key is not the user key.** Logs and UI should never surface the session pubkey as "your pubkey".
- **Don't assume a sign is fast** — UX should show a spinner and allow cancellation for 10+ seconds.
- **Relay hints from `BunkerRequestGetRelays`** can change the relay list mid-session; the session manager handles it, but re-reads of `relays` in feature code may be stale.
- **NIP-46 over Nostr is the only transport here** — there's no HTTP/WS shortcut. Any feature gating on "can this sign" must check relay reachability.

## Related

- `nip55-android-signer.md` — the other remote-ish signer (but local to the device).
- `nostr-expert/references/crypto-and-encryption.md` — NIP-44 details (used by request/response encryption).
