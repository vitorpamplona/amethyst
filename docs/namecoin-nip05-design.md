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
│    ├── NamecoinLookupCache         │                        │
│    └── Nip05NamecoinAdapter        │                        │
│                                    │                        │
│  UI: NamecoinVerificationDisplay   │                        │
│       NamecoinSearchResult         │                        │
├────────────────────────────────────┼────────────────────────┤
│                        Quartz Library                       │
├────────────────────────────────────┼────────────────────────┤
│  NamecoinNameResolver              │                        │
│    ├── parseIdentifier()           │                        │
│    ├── extractFromDomainValue()    │ (d/ namespace)         │
│    └── extractFromIdentityValue()  │ (id/ namespace)        │
│                                    │                        │
│  ElectrumxClient                   │                        │
│    ├── buildNameIndexScript()      │                        │
│    ├── electrumScriptHash()        │                        │
│    └── parseNameScript()           │                        │
│                                    ▼                        │
│                          ┌──────────────────┐               │
│                          │  ElectrumX Server │               │
│                          │  (Namecoin node)  │               │
│                          └──────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

### Layer Separation

- **`quartz/` (library)** — Protocol-level logic. No Android dependencies.
  - `ElectrumxClient` — TCP/TLS connection to ElectrumX, JSON-RPC, script parsing
  - `NamecoinNameResolver` — Identifier parsing, value extraction, NIP-05 mapping
  - `NamecoinLookupCache` — LRU cache with TTL
  - `NamecoinNameResolverTest` — Unit tests for parsing and value extraction

- **`amethyst/` (app)** — Android integration and UI.
  - `NamecoinNameService` — Application singleton, lifecycle management
  - `Nip05NamecoinAdapter` — Static bridge for NIP-05 verification hooks
  - `NamecoinVerificationDisplay` — Compose UI for verified badge + search results

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
4. **`AppModules`** wires up the resolver at construction time

## Default ElectrumX Server

```
electrumx.testls.space:50002  (TLS, self-signed certificate)
```

- ElectrumX 1.16.0, Namecoin chain, protocol 1.4–1.4.3
- Also available via Tor: `i665jpwsq46zlsdbnj4axgzd3s56uzey5uhotsnxzsknzbn36jaddsid.onion:50002`
- Fallback servers: `ulrichard.ch:50006`, `nmc2.lelux.fi:50006` (currently offline)

The `trustAllCerts` flag is set for servers with self-signed certificates. Users can configure custom servers via `NamecoinNameService.setCustomServers()`.

## Caching

- **LRU cache** with configurable max entries (default 500) and TTL (default 1 hour)
- Cache key is the normalized (lowercased, trimmed) identifier
- Both positive and negative results are cached
- Cache is invalidated on TTL expiry; manual `invalidate()` and `clear()` are available

## UI Components

### NamecoinVerificationDisplay
Shows a ⛓ chain-link badge next to profiles verified via Namecoin. Distinct from the standard NIP-05 checkmark — uses Namecoin blue (#4A90D9) and sea green (#2E8B57).

### NamecoinSearchResult
Search bar integration. When a user types a `.bit` identifier, shows a loading state during resolution, then the resolved pubkey with a clickable profile link.

## Security Considerations

- **Self-signed certificates**: The primary ElectrumX server uses a self-signed TLS cert. The `trustAllCerts` option accepts any certificate for that server. This is acceptable because the Namecoin blockchain itself provides the trust anchor — we verify names against on-chain data, not the transport layer. A MITM could return stale data but cannot forge name registrations.
- **Name expiry**: Namecoin names expire after ~36,000 blocks (~250 days) if not renewed. The current implementation does not check expiry. Future work should compare the name's `height` + `expiresIn` against the current block height.
- **Server trust**: The client trusts that the ElectrumX server returns accurate transaction data. For higher assurance, SPV proof verification could be added in the future.

## Files Changed

### New files (quartz/)
- `quartz/.../nip05/namecoin/ElectrumxClient.kt` — ElectrumX TCP/TLS client, scripthash-based name resolution
- `quartz/.../nip05/namecoin/NamecoinNameResolver.kt` — Identifier parsing, value extraction
- `quartz/.../nip05/namecoin/NamecoinLookupCache.kt` — LRU cache with TTL
- `quartz/src/jvmTest/.../NamecoinNameResolverTest.kt` — Unit tests

### New files (amethyst/)
- `amethyst/.../service/namecoin/NamecoinNameService.kt` — App singleton, coroutine scope
- `amethyst/.../service/namecoin/Nip05NamecoinAdapter.kt` — Static bridge for NIP-05 hooks
- `amethyst/.../ui/note/namecoin/NamecoinVerificationDisplay.kt` — Compose UI components

### Modified files
- `amethyst/.../AppModules.kt` — Wire up `NamecoinNameResolver` into `Nip05Client`
- `quartz/.../nip05DnsIdentifiers/Nip05Client.kt` — Route `.bit` identifiers to Namecoin resolver
- `amethyst/.../relays/RelayInformationScreen.kt` — Import reordering (spotless)

## Testing

### Unit tests
```bash
./gradlew :quartz:jvmTest --tests "*NamecoinNameResolverTest*"
```

### Manual testing (emulator)
1. Build and install: `./gradlew :amethyst:installFdroidDebug`
2. Search for `m@testls.bit` — should resolve to pubkey `6cdebcca...18667d`
3. Search for `testls.bit` — root domain lookup
4. Search for `d/testls` — direct namespace format

### Live verification
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
