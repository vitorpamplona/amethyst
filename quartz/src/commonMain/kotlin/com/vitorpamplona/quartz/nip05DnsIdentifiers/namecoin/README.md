# Namecoin (`.bit`) Resolution

This package provides Nostr-aware resolution of Namecoin domains
(`.bit`), used both for NIP-05-style identity verification and for
adding `.bit` Nostr relays directly to the relay pool.

## Components

| File | Role |
|---|---|
| `IElectrumXClient.kt` | Abstract `name_show` lookup (real impl in `jvmAndroid`/`ios` source sets). |
| `NamecoinNameResolver.kt` | NIP-05-style identity resolution: `alice@example.bit` → pubkey + per-pubkey relays. Hosts the shared low-level helpers reused by both the identity and relay paths: `lookupNameDetailed` (timeout + exception → outcome translation around `name_show`), `parseRelayUrls` (value-JSON → relay URLs), and `toNamecoinName` (identifier → `d/<name>` mapping). |
| `NamecoinLookupCache.kt` | TTL'd in-memory cache for identity resolution. |
| **`BitRelayResolver.kt`** | **Resolves `.bit` Nostr relay URLs to their underlying real `wss://` endpoint.** Thin policy layer that delegates URL parsing to `UriParser`, identifier mapping + ElectrumX dispatch + relay-URL parsing to `NamecoinNameResolver`, and only adds an in-memory cache and a small "first usable URL" picking policy. |
| `ElectrumXServer.kt` | ElectrumX server descriptor, default server lists (clearnet + Tor), `name_show` result, exception types. |

## `.bit` Relay Record Format

A user can add a relay like `wss://example.bit` to their relay list.
At connect time, [`BitRelayResolver`](./BitRelayResolver.kt) queries
Namecoin's `d/example` record and pulls a real `wss://` URL out of it.

The Namecoin value JSON should advertise the relay using one of these
shapes (checked in priority order):

```jsonc
// Simplest (recommended for relay-only records).
{ "relay": "wss://relay.example.com/" }

// Multiple endpoints (failover).
{ "relays": ["wss://relay-a.example.com/", "wss://relay-b.example.com/"] }

// Nested under "nostr" (compatible with existing identity records).
{ "nostr": { "relay":  "wss://relay.example.com/" } }
{ "nostr": { "relays": ["wss://relay-a.example.com/"] } }

// Pubkey-keyed shape used by NIP-05 identity records (last-resort fallback).
{ "nostr": { "relays": { "<hex-pubkey>": ["wss://..."] } } }
```

Both `wss://` and `ws://` schemes are accepted. Non-WebSocket schemes
are ignored.

### Combined identity + relay

A record can combine identity and relay advertisement:

```jsonc
{
  "nostr": {
    "names": { "_": "abc...64 hex chars..." }
  },
  "relay": "wss://relay.example.com/"
}
```

`NamecoinNameResolver` reads identity (`nostr.names`) and
`BitRelayResolver` reads the relay URL — they don't fight.

## Connection Flow

1. User adds `wss://example.bit` to their relay list.
2. The relay URL is normalised by `RelayUrlNormalizer` and stored in
   `NormalizedRelayUrl` form. **`.bit` is the canonical identifier** —
   it's what shows in the UI, in event tags, and in Amethyst's relay
   policy.
3. When `OkHttpWebSocket.connect()` runs, it calls a configured
   `urlRewriter`. Amethyst wires this to `BitRelayUrlRewriter`, which
   delegates to `BitRelayResolver`.
4. `BitRelayResolver` queries ElectrumX for `d/example`, parses the
   value, picks the first `wss://` URL it finds, and returns it.
5. The OkHttp WebSocket handshake is performed against the **resolved**
   URL, while the rest of Amethyst still treats the relay as
   `wss://example.bit`.

If the resolved URL has no path component, the original `.bit` URL's
path/query is preserved (so per-room scoping like
`wss://example.bit/room/foo` still works when the record only points
at the host).

## Caching

`BitRelayResolver` keeps a small in-memory cache:

- Positive results cached for 1 hour by default (`positiveTtlSecs`).
- Negative results cached for 1 minute (`negativeTtlSecs`) so newly
  published Namecoin records are picked up reasonably fast.
- The cache is keyed by the lowercased `.bit` host.

Call `BitRelayResolver.invalidate(host)` to drop a single entry, or
`clear()` to evict everything.

## Privacy

`BitRelayResolver` reuses the same `IElectrumXClient` and server
selection as `NamecoinNameResolver`, so:

- Tor routing follows the user's `useTorForNIP05` policy.
- Pinned ElectrumX certificates and TOFU work the same way.
- A custom user-supplied ElectrumX server overrides the defaults.

This means publishing a `.bit` relay does **not** leak the user's IP
to the relay's hosting infrastructure if Tor is enabled — Namecoin
resolution goes through the configured proxy, and the actual relay
connection follows Amethyst's normal Tor policy.
