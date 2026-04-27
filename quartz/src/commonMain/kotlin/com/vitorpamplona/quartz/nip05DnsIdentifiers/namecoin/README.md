# Namecoin (`.bit`) Resolution

This package provides Nostr-aware resolution of Namecoin domains
(`.bit`), used both for NIP-05-style identity verification and for
adding `.bit` Nostr relays directly to the relay pool.

## Components

| File | Role |
|---|---|
| `IElectrumXClient.kt` | Abstract `name_show` lookup (real impl in `jvmAndroid`/`ios` source sets). |
| `NamecoinNameResolver.kt` | NIP-05-style identity resolution: `alice@example.bit` → pubkey + per-pubkey relays. Hosts the shared low-level helpers reused by every Namecoin path: `lookupNameDetailed` (timeout + exception → outcome translation around `name_show`), `parseRelayUrls` (value-JSON → relay URLs), `parseTlsaRecords` (value-JSON → TLSA records, RFC 6698), and `toNamecoinName` (identifier → `d/<name>` mapping). |
| `NamecoinLookupCache.kt` | TTL'd in-memory cache for identity resolution. |
| **`BitRelayResolver.kt`** | **Resolves `.bit` Nostr relay URLs to their underlying real `wss://` endpoint.** Thin policy layer that delegates URL parsing to `UriParser`, identifier mapping + ElectrumX dispatch + relay-URL/TLSA parsing to `NamecoinNameResolver`, and only adds an in-memory cache and a small "first usable URL" picking policy. The cache also exposes `cachedTlsaFor(host)` so the TLS path can pin without a second ElectrumX call. |
| **`TlsaVerifier.kt`** | **Spec-compliant matching policy for the Namecoin `tls` field (RFC 6698 / `ifa-0001`).** Pure Kotlin in `commonMain`; takes pre-extracted DER + SPKI from the platform's TLS layer and decides whether the chain matches the published TLSA records. |
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

## TLS Pinning via the Namecoin Blockchain (RFC 6698 / `ifa-0001`)

Resolving `wss://example.bit` to `wss://relay.example.com` is only half
of the story — by itself, the rewritten handshake is no safer than a
plain DNS lookup. To anchor the TLS handshake in the Namecoin blockchain
itself, the Namecoin `d/example` record can carry a `tls` field with one
or more TLSA records, exactly mirroring DNS TLSA RRs (RFC 6698):

```jsonc
{
  "relay": "wss://relay.example.com/",
  "tls": [
    // [usage, selector, matchingType, base64-association-data]
    [3, 1, 1, "<base64 SHA-256 of leaf SubjectPublicKeyInfo>"],
    [2, 1, 1, "<base64 SHA-256 of trust-anchor SubjectPublicKeyInfo>"]
  ]
}
```

Field semantics (per RFC 6698 §2.1):

- **usage**: `0` (PKIX-TA), `1` (PKIX-EE), `2` (DANE-TA), `3` (DANE-EE).
  PKIX-* records add constraints on top of normal CA validation; DANE-*
  records replace the CA trust anchor entirely.
- **selector**: `0` (full cert) or `1` (`SubjectPublicKeyInfo`). SPKI is
  preferred because it survives certificate rotation as long as the key
  stays the same.
- **matchingType**: `0` (exact bytes), `1` (SHA-256), `2` (SHA-512).
  Note: Namecoin uses **base64**, not the hex form used by DNS textual
  TLSA records.

### How Amethyst enforces this

1. `BitRelayResolver` runs `parseTlsaRecords` on the same `name_show`
   value JSON it already used to extract relay URLs, and stashes the
   records in its TTL'd cache alongside the rewritten URL. **No extra
   ElectrumX round-trip.**
2. The Amethyst-side `TlsaConnectionPolicy` looks up the cached
   records by `.bit` host, builds a `TlsaTrustManager`, and overlays it
   on the per-relay `OkHttpClient` via `sslSocketFactory(...)`.
3. `TlsaTrustManager` delegates the spec policy to the platform-agnostic
   `TlsaVerifier`. For DANE-* matches it accepts without consulting the
   system trust store; for PKIX-* matches it ALSO requires the platform
   default `X509TrustManager` to validate the chain.
4. **No fallback to plain PKIX on a no-match.** A relay running on a
   `.bit` domain that publishes TLSA records is treated as opting in to
   mandatory TLSA validation. Allowing a public-CA cert from a MITM to
   pass would defeat the whole feature.
5. Records with unknown selector / matching type are skipped (forward
   compat). If *every* record is unusable, the trust manager falls back
   to the platform default — a defensive policy, not a security one,
   so a publisher who upgrades to a future record type doesn't lock
   their users out at 3 a.m.

### Records without `tls`

The Namecoin Nostr ecosystem is mostly TLSA-free today. If the resolved
record has no `tls` field, the connection uses the platform default
trust store, exactly as it would for any non-`.bit` relay. The TLSA
path only kicks in when the publisher opts in by writing the records.

The ElectrumX servers themselves still use Amethyst's existing pinned
self-signed cert store (see `ElectrumXClient.PINNED_ELECTRUMX_CERTS`)
for their own TLS, independent of any TLSA published in `tls`.

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
