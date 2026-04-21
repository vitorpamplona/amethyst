# NIP-19: Bech32 Encoding & Parsing

Quartz implementation for `npub`, `nsec`, `note`, `nevent`, `nprofile`, `naddr`, `nrelay`, `nembed` — the user-facing encoded forms of Nostr identifiers.

## Layout

All under `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip19Bech32/`:

- `Nip19Parser.kt` — the entry point. Parses any Bech32 or `nostr:` URI into a typed `Entity`.
- `bech32/Bech32Util.kt` — raw Bech32 encode/decode (bits ↔ 5-bit groups).
- `tlv/Tlv.kt` / `tlv/TlvBuilder.kt` — Type-Length-Value codec for composite entities (`nevent`, `nprofile`, `naddr`).
- `TlvTypes.kt` — TLV type constants (0 = special payload, 1 = relay, 2 = author, 3 = kind).
- `entities/` — one class per entity type (see below).
- `ATagExt.kt`, `ByteArrayExt.kt`, `EventExt.kt`, `ListEntityExt.kt`, `TlvBuilderExt.kt` — convenience extensions for encoding domain objects directly.

## Entity Types

Each is a `sealed class Entity` subclass under `entities/`:

| Class       | Prefix     | Payload                                           | Purpose |
|-------------|------------|---------------------------------------------------|---------|
| `NPub`      | `npub1...` | 32-byte pubkey                                    | Public key |
| `NSec`      | `nsec1...` | 32-byte private key                               | Private key (never log/share) |
| `NNote`     | `note1...` | 32-byte event id                                  | Bare note reference (no hints) |
| `NEvent`    | `nevent1…` | TLV: event id + relays + author + kind            | Rich note reference |
| `NProfile`  | `nprofile…`| TLV: pubkey + relays                              | User reference with relay hints |
| `NAddress`  | `naddr1…`  | TLV: d-tag + relays + author + kind (addressable) | Parameterized replaceable event |
| `NRelay`    | `nrelay1…` | TLV: relay URL                                    | Relay pointer |
| `NEmbed`    | `nembed1…` | Compressed event JSON                             | Full event embedded inline |

## Parsing

```kotlin
// From anywhere (URI, Bech32, nostr: prefix, "nostr:" + data):
val entity: Entity? = Nip19Parser.uriToRoute(input)?.entity

// More forgiving — strips scheme, whitespace, surrounding chars:
val parsed = Nip19Parser.tryParseAndClean(dirtyInput)

when (entity) {
    is NPub      -> entity.hex           // 32-byte pubkey hex
    is NEvent    -> entity.hex + entity.relay + entity.author + entity.kind
    is NAddress  -> entity.atag          // kind:pubkey:d-tag
    is NProfile  -> entity.hex + entity.relay
    // …
}
```

## Encoding

The cleanest path is the entity's `toNostrUri()` / `toBech32()` methods (each entity class defines them). For composite entities (NEvent, NProfile, NAddress), internally the code builds a TLV buffer via `TlvBuilder`:

```kotlin
// TlvBuilder DSL (tlv/TlvBuilder.kt)
val bytes = TlvBuilder().apply {
    addHex(TlvTypes.SPECIAL, eventIdHex)
    addString(TlvTypes.RELAY, relayUrl)
    addHex(TlvTypes.AUTHOR, authorHex)
    addInt(TlvTypes.KIND, kind)
}.build()

Bech32Util.encode("nevent", bytes)
```

Kotlin-idiomatic extension helpers live in `TlvBuilderExt.kt`, `EventExt.kt`, and `ATagExt.kt` — prefer those over hand-building TLV.

## When to Use

- **Pasted input from users** → `Nip19Parser.tryParseAndClean` (handles prefixes, whitespace, leftover `nostr:`)
- **Internal routing / deep links** → `Nip19Parser.uriToRoute`
- **Outbound share links** → call the entity's `toNostrUri()` / `toBech32()` directly
- **Building a custom TLV entity** → `TlvBuilder` DSL + `Bech32Util.encode`

## Gotchas

- `NSec` should never be logged or propagated. Parse and discard the string buffer.
- Relay hints in `NEvent`/`NProfile`/`NAddress` are hints, not guarantees. The Outbox model (NIP-65) overrides them.
- TLV types are fixed (see `TlvTypes.kt`); do not reorder or invent new types without NIP-19 support.
- `NEmbed` is an Amethyst-specific compressed-event extension, not part of NIP-19 proper.

## Tests

See `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip19Bech32/` for round-trip tests covering every entity and `Nip19Parser` input cleaning.
