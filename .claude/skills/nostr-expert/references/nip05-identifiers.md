# NIP-05: Identifiers → Pubkey Resolution

How Quartz turns anything a human might type — a raw hex pubkey, an `npub`/`nprofile`/`nsec`, or a NIP-05 internet identifier (`alice@domain.tld`) — into a 64-hex Nostr pubkey. **Everything below already exists in `quartz/nip05DnsIdentifiers/`. Do not hand-roll it.**

## TL;DR — the one function you almost always want

```kotlin
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull

// hex | npub1… | nprofile1… | nsec1… | name@domain.tld  →  64-hex pubkey (or null)
val pubkey: HexKey? = resolveUserHexOrNull(userInput, nip05Client)
```

`resolveUserHexOrNull(input, nip05Client)` (in `UserHexResolver.kt`) is the canonical "accept any identifier form" resolver. It:

- trims input, tries the **synchronous** bech32/hex path first (`decodePublicKeyAsHexOrNull`), so hex/`npub`/`nprofile`/`nsec` never touch the network;
- only issues an HTTPS fetch for genuinely NIP-05-shaped input (`name@domain.tld`), gated by a cheap `looksLikeNip05()` precheck;
- returns `null` on anything unrecognizable or on a failed NIP-05 lookup (network error / no match);
- re-throws **only** `CancellationException`, so it's safe inside structured concurrency.

Pass `nip05Client = null` in pure-offline contexts — NIP-05-shaped inputs then fall through to `null` and no HTTP is attempted.

## ❌ Do not write this (the hand-rolled anti-pattern)

```kotlin
// DON'T. This re-implements resolveUserHexOrNull badly:
//  - no nsec support
//  - no input validation (accepts IP-literal / malformed domains → spurious fetches)
//  - hand-parses JSON instead of using Nip05Parser
//  - bespoke httpGet ignores the "MUST NOT follow redirects" rule
//  - swallows CancellationException, breaking structured concurrency
fun resolveObserver(input: String): String? {
    if (Hex.isHex64(input)) return input.lowercase()
    if (input.startsWith("npub1") || input.startsWith("nprofile1")) { /* … */ }
    if ("@" in input) return resolveNip05(input)  // bespoke well-known fetch
    return null
}
```

## ✅ Do this instead

```kotlin
// CLI already exposes it — commands call Context.requireUserHex(input):
val pubHex = resolveUserHexOrNull(input, nip05Client)
    ?: return Output.error("bad_args", "expected npub, nprofile, 64-hex, or name@domain.tld")
```

## Building an `Nip05Client`

`resolveUserHexOrNull` takes an `INip05Client`. On JVM/Android, wire the OkHttp fetcher (mirror what `cli/Context.kt` does):

```kotlin
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher

val nip05Client = Nip05Client(fetcher = OkHttpNip05Fetcher { _ -> okHttpClient })
```

`OkHttpNip05Fetcher` already runs on `Dispatchers.IO` and disables redirects per the NIP-05 spec ("Fetchers MUST ignore any HTTP redirects"). Don't re-implement the fetch.

For tests / offline code, `EmptyNip05Client` is a no-op stub.

## The pieces (all in `quartz/…/nip05DnsIdentifiers/`)

| Type | File | Purpose |
|------|------|---------|
| `resolveUserHexOrNull(input, client?)` | `UserHexResolver.kt` | **Start here.** Any identifier form → 64-hex pubkey, or null. `suspend`. |
| `Nip05Id` | `Nip05Id.kt` | Parsed `name@domain`. `Nip05Id.parse(str)` validates (RFC 5321 local-part + hostname rules, rejects IP literals) and lowercases. `toUserUrl()` / `toDomainUrl()` build the `.well-known/nostr.json` URLs. `toDisplayValue()` collapses the `_` wildcard to just the domain. |
| `INip05Client` / `Nip05Client` | `INip05Client.kt`, `Nip05Client.kt` | Async resolver. `get(id): Nip05KeyInfo?` (pubkey + relays), `verify(id, hex): Boolean`, `load(id): KeyInfoSet?`, `list(domain): KeyInfoSet`, `loadClinkOffer(id): String?`. Auto-routes `.bit` domains to Namecoin. `EmptyNip05Client` = offline no-op. |
| `Nip05Fetcher` / `OkHttpNip05Fetcher` | `Nip05Fetcher.kt`, `OkHttpNip05Fetcher.kt` (jvmAndroid) | Transport SAM. OkHttp actual disables redirects + runs on IO. |
| `Nip05Parser` | `Nip05Parser.kt` | JSON `.well-known/nostr.json` codec: `parseHexKey`, `parseHexKeyAndRelays`, `parse` → `KeyInfoSet`, `parseClinkOffer`. |
| `Nip05KeyInfo` / `KeyInfoSet` | `Nip05KeyInfo.kt`, `KeyInfoSet.kt` | `Nip05KeyInfo(pubkey, relays)`; `KeyInfoSet(names: Map, relays: Map)` = the full domain listing. |
| `NamecoinNameResolver` | `namecoin/NamecoinNameResolver.kt` | `.bit` / `d/…` / `id/…` blockchain identifiers. `isNamecoinIdentifier(str)`, `resolve(str)`. Invoked automatically by `Nip05Client` — you rarely call it directly. |

## When you only need the pure (synchronous, no-network) part

If the input can only be hex/bech32 (no NIP-05), skip the client entirely:

```kotlin
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

// hex | npub1… | nprofile1… | nsec1…  →  64-hex pubkey (or null). No suspend, no network.
val pubkey: HexKey? = decodePublicKeyAsHexOrNull(input)
```

See `references/nip19-bech32.md` for the full bech32 entity story. `resolveUserHexOrNull` is just this function plus the NIP-05 HTTP fallback.

## Verifying a claimed identifier

To confirm a profile's advertised `nip05` actually points back to its pubkey (NIP-05 verification), use `verify`, not `get`:

```kotlin
val ok: Boolean = nip05Client.verify(Nip05Id.parse("alice@domain.tld")!!, profilePubkeyHex)
```

## Tests

`quartz/src/commonTest/…/nip05DnsIdentifiers/Nip05Test.kt` covers parsing, URL construction, case-normalization, CLINK offers, and the validation rejects (IP literals, malformed domains).
