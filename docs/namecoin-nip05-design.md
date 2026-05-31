# Namecoin NIP-05 Resolution — Design Document

## Overview

This patch adds Namecoin blockchain-based NIP-05 identity verification to Amethyst. Users can set their `nip05` field to a `.bit` domain (e.g. `alice@example.bit`) or a direct Namecoin name (`d/example`, `id/alice`), and Amethyst will resolve it via the Namecoin blockchain instead of HTTP.

This is censorship-resistant identity verification: no web server to seize, no DNS to hijack, no TLS certificate to revoke. The name-to-pubkey mapping lives in Namecoin UTXOs.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Amethyst App                         │
├─────────────────────────────────────────────────────────────┤
│  Nip05Client                                                │
│    ├── HTTP path (existing) ── Nip05Fetcher                 │
│    └── Namecoin path (new)  ── NamecoinNameResolver         │
│                                    │                        │
│  NamecoinNameService (singleton)   │                        │
│    └── NamecoinLookupCache         │                        │
│                                    │                        │
│  RoleBasedHttpClientBuilder        │                        │
│    └── socketFactoryForNip05()  ───┤ (Tor-aware sockets)    │
│                                    │                        │
│  ProxiedSocketFactory              │                        │
│    └── SOCKS5 proxy routing     ───┘                        │
├────────────────────────────────────┼────────────────────────┤
│                       Quartz Library                        │
├────────────────────────────────────┼────────────────────────┤
│  NamecoinNameResolver              │                        │
│    ├── parseIdentifier()           │                        │
│    ├── extractFromDomainValue()    │ (d/ namespace)         │
│    ├── extractFromIdentityValue()  │ (id/ namespace)        │
│    ├── NamecoinImportResolver      │ (ifa-0001 imports)     │
│    └── serverListProvider()        │ (Tor/clearnet routing) │
│                                    │                        │
│  CompositeNamecoinBackend          │                        │
│    ├── primary  ─── ElectrumxClient or NamecoinCoreRpcClient│
│    ├── custom ElectrumX fallback                            │
│    └── default ElectrumX fallback                           │
│                                    │                        │
│  ElectrumXClient                   │                        │
│    ├── buildNameIndexScript()      │                        │
│    ├── electrumScriptHash()        │                        │
│    ├── parseNameScript()           │ (NAME_UPDATE + FIRSTUPDATE)│
│    └── socketFactory()          ───┤ (injected, proxy-aware)│
│                                    │                        │
│  NamecoinCoreRpcClient             │                        │
│    └── HTTP(S) JSON-RPC ──── name_show ── full node         │
│                                    ▼                        │
│       ┌────────────────────────────────────────────┐        │
│       │ ElectrumX server  OR  Namecoin Core node   │        │
│       │ (clearnet, .onion, LAN, StartOS, umbrel)   │        │
│       └────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### Layer Separation

- **`quartz/` (library)** — Protocol-level logic. No Android dependencies in `commonMain`; TCP / TLS / HTTP clients live in `jvmAndroid`.
  - `ElectrumXClient` — TCP/TLS connection to ElectrumX, JSON-RPC, script parsing. Accepts an injected `SocketFactory` lambda for proxy/Tor support. Path: `quartz/.../nip05DnsIdentifiers/namecoin/ElectrumXClient.kt` (`jvmAndroid`).
  - `NamecoinCoreRpcClient` — HTTP(S) JSON-RPC client for a Namecoin Core full node. Lives in `jvmAndroid`. Same TOFU-pinning model as `ElectrumXClient`.
  - `NamecoinNameResolver` — Identifier parsing, value extraction, NIP-05 mapping, ifa-0001 `import` expansion. Accepts a `serverListProvider` lambda for dynamic server selection and exposes `resolveDetailed()` returning a `NamecoinResolveOutcome` sealed type (Success / NameNotFound / NameExpired / NoNostrField / MalformedRecord / ServersUnreachable / InvalidIdentifier / Timeout).
  - `CompositeNamecoinBackend` — Chains a primary backend (Core RPC or custom ElectrumX) with optional custom-ElectrumX and default-ElectrumX fallbacks, per `NamecoinFallbackPolicy`.
  - `NamecoinImportResolver` — Resolves the [ifa-0001](https://github.com/namecoin/proposals/blob/master/ifa-0001.md) `import` item (string / array / array-of-arrays forms) before record extraction.
  - `NamecoinLookupCache` — LRU cache with TTL.
  - `NamecoinNameResolverTest`, `CompositeNamecoinBackendTest`, `NamecoinImportTest`, `NamecoinCoreRpcClientTest` — JVM unit tests.

- **`commons/` (Kotlin multiplatform)** — Settings schema shared by Android and Desktop.
  - `NamecoinSettings` — Serializable config (backend choice, custom servers, Core RPC URL/creds, fallback toggles) used by both platforms' persistence layers.
  - `NamecoinResolveState` — UI state model surfaced by search / on-chain zap rows.

- **`amethyst/` (Android app)** — Android integration and Tor-aware wiring.
  - `NamecoinNameService` — Application singleton, initialized with a proxy-aware `ElectrumXClient` and (optionally) a `NamecoinCoreRpcClient` via `CompositeNamecoinBackend`.
  - `NamecoinSharedPreferences` — DataStore-backed persistence for `NamecoinSettings`, including TOFU-pinned PEM certs.
  - `ProxiedSocketFactory` — `SocketFactory` implementation that routes through a SOCKS5 proxy (Tor).
  - `RoleBasedHttpClientBuilder.socketFactoryForNip05()` — Returns a proxy-aware or default `SocketFactory` based on current Tor settings.
  - `NamecoinSettingsScreen` / `NamecoinSettingsSection` — Backend picker, custom-server editor, Test Connection diagnostics, TOFU pin prompts.
  - `NamecoinResolutionRow` — Inline indicator that surfaces a `NamecoinResolveState` in search results, on-chain zap dialogs, etc.

- **`desktopApp/` (JVM desktop)** — Mirrors the Android wiring for Compose Desktop.
  - `DesktopNamecoinNameService`, `DesktopNamecoinPreferences`, `LocalNamecoin` — lazy-initialized service stack so Namecoin code is not loaded until the user resolves a `.bit` name.
  - `desktop/ui/settings/NamecoinSettingsSection.kt` — desktop equivalent of the Android settings UI.

## Tor & Proxy Integration

The ElectrumX connection respects the user's Tor settings to prevent IP leaks:

### Problem

The original `ElectrumXClient` used raw `java.net.Socket` / `SSLSocket` directly, bypassing OkHttp entirely. This meant Namecoin lookups would leak the user's real IP even when they had configured Tor for NIP-05 verification traffic.

### Solution

1. **`ElectrumXClient`** accepts a `socketFactory: () -> SocketFactory` lambda (evaluated at each connection, not captured at construction)
2. **`ProxiedSocketFactory`** creates sockets routed through a `java.net.Proxy` (SOCKS5)
3. **`RoleBasedHttpClientBuilder.socketFactoryForNip05()`** checks the user's NIP-05 Tor settings and returns either `SocketFactory.getDefault()` or a `ProxiedSocketFactory` with the active Tor SOCKS proxy
4. SSL is layered on top of the (possibly proxied) base socket via `SSLSocketFactory.createSocket(socket, host, port, autoClose)`, preserving the proxy tunnel
5. The Namecoin Core RPC path reuses the same `socketFactoryForNip05()` plumbing, so a user-supplied node URL (onion or LAN) inherits the same Tor / proxy rules with no extra wiring

### Server Selection

The `serverListProvider` lambda in `NamecoinNameResolver` is evaluated at resolution time, so toggling Tor settings takes effect immediately without restarting the app. The current defaults are listed under [Default ElectrumX Servers](#default-electrumx-servers) below — when Tor is enabled for NIP-05 traffic, the resolver switches to `TOR_ELECTRUMX_SERVERS`, which prepends `.onion` endpoints to the clearnet set.

### Dynamic Evaluation

Both the socket factory and server list are provided as lambdas, not captured values. This means:
- Toggling Tor on/off in settings takes effect on the next Namecoin lookup
- The proxy port is read from `DualHttpClientManager`'s live `StateFlow`
- No app restart or singleton reconstruction needed

## ElectrumX Protocol — How Name Resolution Works

Namecoin names are stored as UTXOs with `NAME_UPDATE` scripts. The ElectrumX server indexes these by a canonical "name index script hash", allowing lookup via standard Electrum protocol methods.

### Resolution Steps

```
1. Build canonical name index script
   OP_NAME_UPDATE(0x53) + push(name_bytes) + push(empty) + OP_2DROP(0x6d) + OP_DROP(0x75) + OP_RETURN(0x6a)

2. Compute Electrum-style scripthash
   SHA-256(script) → reverse bytes → hex encode

3. Query transaction history
   → blockchain.scripthash.get_history(scripthash)
   ← [{tx_hash, height}, ...]

4. Fetch latest transaction (last entry = most recent name update)
   → blockchain.transaction.get(tx_hash, verbose=true)
   ← {vout: [{scriptPubKey: {hex: "53..."}}]}

5. Parse NAME_UPDATE script from transaction output
   Script: OP_NAME_UPDATE <push(name)> <push(value_json)> OP_2DROP OP_DROP <address_script>
   Extract: name string + JSON value

6. Extract Nostr pubkey from the JSON value
```

### Why Not `blockchain.name.get_value_proof`?

The Electrum-NMC fork of ElectrumX advertises a `blockchain.name.get_value_proof` method (protocol v1.4.3), but in practice this method expects a scripthash parameter, not a name string. The scripthash-based approach described above works with both the Namecoin ElectrumX fork and stock ElectrumX pointed at a Namecoin node, as long as the server has a name index.

## Namecoin Value Formats

### Domain namespace (`d/`)

Namecoin `d/` names store domain configuration as JSON. Two Nostr formats are supported:

**Simple form** — single pubkey for the root domain:
```json
{
  "nostr": "b0635d6a9851d3aed0cd6c495b282167acf761729078d975fc341b22650b07b9"
}
```

**Extended form** — multiple users with relay hints (mirrors NIP-05 JSON structure):
```json
{
  "nostr": {
    "names": {
      "_": "aaaa...0001",
      "alice": "bbbb...0002"
    },
    "relays": {
      "bbbb...0002": ["wss://relay.example.com"]
    }
  }
}
```

### Identity namespace (`id/`)

Namecoin `id/` names store personal identity data:

```json
{
  "nostr": "cccc...0003"
}
```

Or with relay hints:
```json
{
  "nostr": {
    "pubkey": "dddd...0004",
    "relays": ["wss://relay.example.com"]
  }
}
```

## Identifier Formats

| User input | Namecoin name | Local part | Namespace |
|---|---|---|---|
| `alice@example.bit` | `d/example` | `alice` | DOMAIN |
| `_@example.bit` | `d/example` | `_` | DOMAIN |
| `example.bit` | `d/example` | `_` | DOMAIN |
| `d/example` | `d/example` | `_` | DOMAIN |
| `id/alice` | `id/alice` | `_` | IDENTITY |

## NIP-05 Integration

The integration is minimal and non-invasive:

1. **`Nip05Client`** gains an optional `namecoinResolver` parameter
2. On `verify()` and `get()`, if the identifier matches `.bit` / `d/` / `id/`, it routes to `NamecoinNameResolver` instead of the HTTP fetcher
3. Non-Namecoin identifiers are completely unaffected
4. **`AppModules`** wires up the resolver with Tor-aware socket factory and dynamic server selection

## Search Integration

The search bar resolves Namecoin identifiers in real-time via `SearchBarViewModel`:

1. A `namecoinResolvedUser` flow watches the search input with a 400ms debounce
2. If the input matches any Namecoin format (`d/*`, `id/*`, `*.bit`, `*@*.bit`), it resolves via `NamecoinNameService` → `CompositeNamecoinBackend` (`ElectrumXClient` and/or `NamecoinCoreRpcClient`) → blockchain. `NamecoinResolutionRow` shows the live state (Resolving / Found / NameNotFound / NameExpired / MalformedRecord / NoNostrField / ServersUnreachable).
3. The resolved pubkey is used to get/create a `User` in `LocalCache`
4. The Namecoin-resolved user is prepended to the standard local search results (deduplicated)

This means typing `alice@example.bit`, `example.bit`, `d/example`, or `id/alice` into the search bar will query the Namecoin blockchain and show the resolved user profile at the top of results.

## Default ElectrumX Servers

Defined in `quartz/.../nip05DnsIdentifiers/namecoin/ElectrumXServer.kt` as `DEFAULT_ELECTRUMX_SERVERS` (clearnet) and `TOR_ELECTRUMX_SERVERS` (Tor-preferred).

### Clearnet (`DEFAULT_ELECTRUMX_SERVERS`)
| Server | Port | TLS | Trust path | Notes |
|---|---|---|---|---|
| `electrumx.testls.space` | 50002 | Yes (self-signed) | Pinned | Primary |
| `nmc2.bitcoins.sk` | 57002 | Yes (self-signed) | Pinned | Fallback |
| `46.229.238.187` | 57002 | Yes (self-signed) | Pinned | Bare-IP peer of `nmc2.bitcoins.sk` (same operator/cert/box) |
| `relay.testls.bit` | 50002 | Yes (self-signed) | Pinned | Second public Namecoin ElectrumX, co-located with the `wss://relay.testls.bit/` Nostr relay |
| `23.158.233.10` | 50002 | Yes (self-signed) | Pinned | Bare-IP peer of `relay.testls.bit` |
| `electrum.nmc.ethicnology.com` | 50002 | Yes (Let's Encrypt) | System CAs | Third public deployment ([ethicnology/namecoin-compose](https://github.com/ethicnology/namecoin-compose)); first entry that does NOT require a pinned cert |

### Tor (`TOR_ELECTRUMX_SERVERS`, used when "NIP-05 verifications via Tor" is on)
| Server | Port | TLS | Trust path | Notes |
|---|---|---|---|---|
| `i665jpwsq46zlsdbnj4axgzd3s56uzey5uhotsnxzsknzbn36jaddsid.onion` | 50002 | Yes (self-signed) | Pinned | Onion service for `electrumx.testls.space` |
| `6cbn4rskfdr647otej7gpqlmpqcmj723vg2eoeuu7ljbwu6cpdebozyd.onion` | 50001 | No (plaintext) | n/a | Hidden service shared with the `relay.testls.bit` Nostr onion; onion key authenticates the endpoint |
| `electrumx.testls.space` | 50002 | Yes (self-signed) | Pinned | Clearnet fallback (via Tor SOCKS) |
| `nmc2.bitcoins.sk` | 57002 | Yes (self-signed) | Pinned | Clearnet fallback (via Tor SOCKS) |
| `relay.testls.bit` | 50002 | Yes (self-signed) | Pinned | Clearnet fallback (via Tor SOCKS) |
| `23.158.233.10` | 50002 | Yes (self-signed) | Pinned | Clearnet fallback (via Tor SOCKS) |
| `electrum.nmc.ethicnology.com` | 50002 | Yes (Let's Encrypt) | System CAs | Clearnet fallback (via Tor SOCKS) |

Servers with `usePinnedTrustStore = true` are validated against the hardcoded `PINNED_ELECTRUMX_CERTS` plus the user's TOFU-pinned cert store; `false` means the system trust store is sufficient (Let's Encrypt path).

Users can add custom servers via the Namecoin settings screen — `NamecoinSettings.customServers` accepts `host:port` (TLS) or `host:port:tcp` (plaintext, useful for local `.onion`). When at least one custom server is configured it is used **exclusively**; the public defaults are skipped unless `fallbackToDefaultElectrumx` is enabled. Custom-server TLS certs are TOFU-pinned at first connection (see [Cert Pinning & TOFU](#cert-pinning--tofu)).

## Backends, Composition, and Fallback Policy

For a given resolution request, `NamecoinNameService` (or its desktop equivalent) builds a `CompositeNamecoinBackend` driven by the user's `NamecoinSettings`:

```
                primary                fallback 1                  fallback 2
┌───────────────────────────┐  ┌───────────────────────────┐  ┌──────────────────────────┐
│ backend = ELECTRUMX:       │  │ (skipped — custom servers │  │ default ElectrumX        │
│   custom servers, else     │─▶│  already are the primary) │─▶│ (`DEFAULT_ELECTRUMX_…`)  │
│   default servers          │  │                           │  │ if fallbackToDefault…    │
├───────────────────────────┤  ├───────────────────────────┤  ├──────────────────────────┤
│ backend = NAMECOIN_CORE_RPC│  │ custom ElectrumX servers  │  │ default ElectrumX        │
│   user-supplied node URL   │─▶│ if fallbackToCustom…      │─▶│ if fallbackToDefault…    │
└───────────────────────────┘  └───────────────────────────┘  └──────────────────────────┘
```

Key rules implemented in `CompositeNamecoinBackend`:

- A definitive **"name not found"** answer short-circuits the chain (preserves privacy intent — the lookup already happened on the chosen backend).
- **`ServersUnreachable`** (transport failures, all endpoints in a tier dead) cascades to the next configured tier.
- `CancellationException` propagates immediately.
- Both fallback toggles default to `false`; users must opt in explicitly. This matches the historical behaviour where custom ElectrumX servers were exclusive.

### Namecoin Core RPC backend

Set `backend = NAMECOIN_CORE_RPC` in settings and provide:

- `url` — Full URL including scheme (`http://`, `https://`). Examples:
  - StartOS: `https://<lan-host>/` (LAN cert auto-issued by StartOS — pin via TOFU)
  - umbrel: `http://<umbrel-host>:8336/` or the "Connect From Outside" URL
  - Raw host: `http://<ip>:8336/`
  - Onion: `http://<onion>.onion:8336/` (routed through Tor SOCKS via `socketFactoryForNip05()`)
- `username`, `password` — StartOS "RPC Credentials" or umbrel "Connect From Outside → RPC User / Password". Cookie-auth is not supported because users typically aren't on the node host.
- `timeoutMs` — Per-call timeout, default 15 s, deliberately under the 20 s `NamecoinNameResolver` outer budget.
- `usePinnedTrustStore` — Set true to route the HTTPS request through the pinned-cert socket factory (StartOS / umbrel LAN endpoints with self-signed root).

The RPC client issues a single JSON-RPC `name_show` call, parses `expires_in`, and throws `NamecoinLookupException.NameExpired` when the name is expired. The Settings screen offers a **Test RPC** action that prompts the user to confirm the leaf cert fingerprint and pins it (TOFU) before the first real query.

## ifa-0001 `import` resolution

Namecoin's Domain Name Object spec allows a record to import items from another name via the `import` field ([ifa-0001](https://github.com/namecoin/proposals/blob/master/ifa-0001.md)). `NamecoinImportResolver` (in `quartz`) expands these before Quartz extracts NIP-05 fields, so a `.bit` name can centralise its Nostr config in a shared record.

Behaviour:

- Accepts canonical array-of-arrays form **and** the three short-hand forms (`"d/foo"`, `["d/foo"]`, `["d/foo", "sub"]`) that appear in real-world records.
- Recurses up to depth 4 (the minimum the spec mandates); cycles are broken by a visited-set keyed on `name|selector`.
- The importing object's items take precedence; a `null` item still suppresses the imported value.
- Subdomain Selectors are resolved via the imported value's `map` tree before merging.
- Failed imports (not found / malformed / network error) degrade to an empty `{}` rather than failing the whole resolution — keeps Quartz's existing best-effort namecoin behaviour intact.
- Only items that Quartz actually consumes (e.g. `nostr`) are read after the merge; we do not recursively merge nested objects.

## Cert Pinning & TOFU

Namecoin's ElectrumX ecosystem predominantly uses self-signed TLS certs, so a plain system trust store would reject everything. Amethyst's model:

- **`PINNED_ELECTRUMX_CERTS`** — hardcoded PEM bundle inside `ElectrumXClient.kt` covering the self-signed defaults (testls, nmc2.bitcoins.sk, relay.testls.bit, onion services). The bare-IP entries (`46.229.238.187`, `23.158.233.10`) work without SNI because the pin is on the DER SHA-256, not hostname.
- **User-supplied PEM store** — TOFU-pinned certs captured the first time a custom ElectrumX server (or Namecoin Core RPC endpoint) is tested via the Settings UI. Persisted by `NamecoinSharedPreferences` (Android) / `DesktopNamecoinPreferences` (desktop).
- **Let's Encrypt path** — `electrum.nmc.ethicnology.com` chains to a publicly-trusted cert, so its `ElectrumxServer` entry has `usePinnedTrustStore = false`. The system trust manager handles it; if the cert rotates we don't need a release to keep working.
- **`.onion` cert handling** — Onion-routed connections skip cert pinning entirely (the onion key already authenticates the endpoint). Verified for the testls onion in 2025 and documented inline in `ElectrumXClient.kt`.
- **Test Connection diagnostics** — Settings exposes a per-server test that returns `ServerTestResult { success, responseTimeMs, tlsVersion, serverCertPem, certFingerprint, error }`. The UI uses this to render success/failure plus a fingerprint-confirmation dialog for TOFU.

## Name expiry

Namecoin names expire after `NAME_EXPIRE_DEPTH = 36000` blocks (~250 days) if not renewed. Both backends now enforce this:

- `ElectrumXClient` cross-references `current_height - height` against `NAME_EXPIRE_DEPTH` and throws `NamecoinLookupException.NameExpired(name)` for expired records; live `NameShowResult.expiresIn` is populated when the current height is known.
- `NamecoinCoreRpcClient` reads the node's `expired` and `expires_in` fields from the JSON-RPC response and throws the same exception.
- `NamecoinResolveOutcome.NameExpired` is surfaced to the search and on-chain zap UIs so users see why a `.bit` name failed to resolve.

The resolver also distinguishes `MalformedRecord` (the on-chain value parsed but didn't conform to the expected shape) from `NoNostrField` (record is valid, just has no `nostr` item) — both are rendered with their own copy in `NamecoinResolutionRow`.

## Caching

- **LRU cache** with configurable max entries (default 500) and TTL (default 1 hour)
- Cache key is the normalized (lowercased, trimmed) identifier
- Both positive and negative results are cached
- Cache is invalidated on TTL expiry; manual `invalidate()` and `clear()` are available

## Security Considerations

- **Tor integration**: ElectrumX and Namecoin Core RPC connections are routed through the user's Tor SOCKS proxy when NIP-05 Tor settings are enabled. This prevents IP leaks. The onion endpoints are preferred when Tor is active, providing end-to-end onion routing for both transports.
- **Self-signed certificates**: Most public Namecoin ElectrumX servers use self-signed TLS certs. The `usePinnedTrustStore` flag (renamed from the original `trustAllCerts`) routes those connections through a SHA-256 pin set rather than blindly accepting any cert. This protects against arbitrary MITM while preserving the operator's ability to roll their own CA. The Namecoin blockchain itself remains the trust anchor for the name data; transport authentication only stops on-path tampering.
- **TOFU pinning for custom endpoints**: Custom ElectrumX servers and the Namecoin Core RPC endpoint are TOFU-pinned via the Test Connection diagnostic. Subsequent requests refuse certs that don't match the pinned fingerprint.
- **Name expiry**: Enforced — see [Name expiry](#name-expiry) above. Expired names surface as `NamecoinResolveOutcome.NameExpired` rather than a stale pubkey.
- **Server trust**: The client still trusts that the ElectrumX server or Core RPC node returns accurate transaction data. For higher assurance, SPV proof verification could be added in the future; users who want to remove that trust today can run their own Namecoin Core node and point the Core RPC backend at it.
- **Dynamic proxy evaluation**: Socket factory and server list are evaluated per-request (via lambdas), ensuring Tor setting changes take effect immediately without stale socket reuse.
- **Fail-closed routing**: Namecoin lookups go through `RoleBasedHttpClientBuilder`'s `PrivacyRouter`, so a misconfigured route raises a typed `BlockedRouteException` instead of silently falling back to clearnet.

## Files

Layout reflects the current `main` branch. Paths under `quartz` moved from the original `nip05/namecoin/` location to `nip05DnsIdentifiers/namecoin/` when Quartz's DNS-identifier package was reorganised.

### Quartz (`quartz/`)

`commonMain`:
- `nip05DnsIdentifiers/namecoin/ElectrumXServer.kt` — `ElectrumxServer`, `NameShowResult`, `NamecoinLookupException`, `ServerTestResult`, `DEFAULT_ELECTRUMX_SERVERS`, `TOR_ELECTRUMX_SERVERS`
- `nip05DnsIdentifiers/namecoin/IElectrumXClient.kt` — common-source client interface
- `nip05DnsIdentifiers/namecoin/NamecoinBackend.kt` — `NamecoinBackend` enum, `NamecoinCoreRpcConfig`, `NamecoinFallbackPolicy`
- `nip05DnsIdentifiers/namecoin/NamecoinNameResolver.kt` — parser, `NamecoinResolveOutcome`, `resolveDetailed()`, server-list provider
- `nip05DnsIdentifiers/namecoin/NamecoinImportResolver.kt` — ifa-0001 `import` expansion
- `nip05DnsIdentifiers/namecoin/NamecoinLookupCache.kt` — LRU + TTL cache
- `nip05DnsIdentifiers/namecoin/CompositeNamecoinBackend.kt` — primary + fallback chain

`jvmAndroid` (Android + desktop JVM):
- `nip05DnsIdentifiers/namecoin/ElectrumXClient.kt` — TCP/TLS ElectrumX client; parses NAME_UPDATE and NAME_FIRSTUPDATE outputs; checks expiry; TOFU pin store
- `nip05DnsIdentifiers/namecoin/NamecoinCoreRpcClient.kt` — HTTP(S) JSON-RPC client for Namecoin Core (`name_show`)

Tests under `quartz/src/jvmTest/.../nip05/namecoin/`:
- `NamecoinNameResolverTest`, `CompositeNamecoinBackendTest`, `NamecoinImportTest`, `NamecoinCoreRpcClientTest`

### Commons (`commons/`)
- `commonMain/.../nip05DnsIdentifiers/namecoin/NamecoinSettings.kt` — serializable config shared by Android + desktop
- `commonMain/.../nip05DnsIdentifiers/namecoin/NamecoinResolveState.kt` — UI state model used by search and on-chain zap rows
- Companion `commonTest/.../NamecoinSettingsTest.kt`

### Amethyst Android app (`amethyst/`)
- `service/namecoin/NamecoinNameService.kt` — singleton tying together `CompositeNamecoinBackend`, the proxy-aware socket factory, server list provider, and the cache
- `model/preferences/NamecoinSharedPreferences.kt` — DataStore persistence for `NamecoinSettings` + TOFU PEM store
- `model/privacyOptions/ProxiedSocketFactory.kt` — `SocketFactory` routed through SOCKS5
- `model/privacyOptions/RoleBasedHttpClientBuilder.kt` — `socketFactoryForNip05()` + `PrivacyRouter` integration
- `ui/screen/loggedIn/settings/NamecoinSettingsScreen.kt`, `NamecoinSettingsSection.kt` — backend picker, custom-server editor, Test Connection / TOFU prompts, fallback toggles
- `ui/components/namecoin/NamecoinResolutionRow.kt` — inline `.bit` resolution indicator (search bar, on-chain zap recipient picker)
- `ui/screen/loggedIn/search/SearchBarViewModel.kt` — `.bit` / `d/` / `id/` routing into `NamecoinNameService`
- `AppModules.kt` — wires resolver, cache, settings flow, and lazy backend construction
- Quartz integration point: `quartz/.../nip05DnsIdentifiers/Nip05Client.kt` — routes `.bit` identifiers to the Namecoin resolver

### Desktop app (`desktopApp/`)
- `service/namecoin/DesktopNamecoinNameService.kt`, `DesktopNamecoinPreferences.kt`, `LocalNamecoin.kt` — lazy-initialised desktop equivalent (Namecoin code is not loaded until needed)
- `ui/settings/NamecoinSettingsSection.kt` — desktop settings UI mirroring the Android section
- Unit test: `service/namecoin/DesktopNamecoinPreferencesTest.kt`

## Testing

### Unit tests
```bash
./gradlew :quartz:jvmTest --tests "*NamecoinNameResolverTest*"
```

### Manual testing (emulator or device)

Build and install the debug APK:
```bash
./gradlew assemblePlayDebug
adb install -r amethyst/build/outputs/apk/play/debug/amethyst-play-universal-debug.apk
```

**Search bar tests** — open the search bar and enter each of these:

| Search query | Expected result | What it tests |
|---|---|---|
| `m@testls.bit` | Resolves to Vitor Pamplona's profile (Namecoin row above the local results) | NIP-05 style `user@domain.bit` |
| `testls.bit` | Resolves to the root `_` / first available entry | Bare domain `.bit` lookup |
| `d/testls` | Resolves to Vitor Pamplona's profile | Direct `d/` namespace via `NamecoinResolutionRow` |
| `id/someuser` | Resolves if registered on-chain | Direct `id/` namespace via `NamecoinResolutionRow` |
| `nonexistent.bit` | `NameNotFound` indicator in the row | Negative-cached failure path |

**On-chain zap tests** — open the on-chain zap send dialog:
1. Type a `.bit` recipient → `NamecoinResolutionRow` should resolve and enable Send when a pubkey is found
2. Type a user-search query → result chip should wrap and be selectable

**Backend picker tests** — Settings → Namecoin:
1. Default (ElectrumX, no custom servers) — search uses `DEFAULT_ELECTRUMX_SERVERS`
2. Add a custom server, hit Test Connection → confirm fingerprint → TOFU pin persists across restarts
3. Switch backend to **Namecoin Core RPC**, point at a local node (StartOS / umbrel / raw), hit Test RPC → confirm cert and pin, then search
4. Toggle `fallbackToCustomElectrumx` / `fallbackToDefaultElectrumx` and verify cascade by killing the primary

**Tor tests** — enable Tor and set "NIP-05 verifications via Tor" to on:
1. Search for `m@testls.bit` — should resolve via the onion endpoint
2. Verify no direct clearnet connections to ElectrumX servers (use `tcpdump`)
3. Toggle Tor off — next search should use clearnet servers
4. Switch backend to Core RPC with an onion URL — same Tor routing applies

**Verification test** — if a profile has a `.bit` address in its `nip05` field, the NIP-05 badge should verify via the blockchain instead of HTTP.

**Network verification** — to confirm Namecoin calls are being made:
```bash
adb root
adb shell tcpdump -i any -nn port 50001 or port 50002 or port 57002 or port 8336
```
With Tor off, you should see TCP connections to one of the entries in `DEFAULT_ELECTRUMX_SERVERS` (or the Core RPC port when that backend is selected).
With Tor on, you should see connections to the local Tor SOCKS port only.

### Live test data
The name `d/testls` is registered on the Namecoin blockchain (block 551519+, last updated block 814278) with value:
```json
{
  "nostr": {
    "names": {
      "m": "6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d"
    }
  }
}
```

This means:
- `m@testls.bit` → resolves `m` entry → pubkey `6cdebcca...18667d`
- `testls.bit` → resolves root `_` entry → falls back to first available entry
- `d/testls` → same as `testls.bit` (root lookup)
