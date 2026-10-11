# NIP-19 bech32 in Quartz

All under `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip19Bech32/`.

| File | Holds |
|---|---|
| `Nip19Parser.kt` | `object Nip19Parser`: `uriToRoute(uri): ParseReturn?` (`entity`, `nip19raw`, `additionalChars`), `tryParseAndClean(uri): String?` (bare `npub1…` out of dirty input), `parseAll(content)`, `parseAllStandalone`, `parseAllEvents`, `hasAny`. Top-level `decodePublicKey`, `decodePublicKeyAsHexOrNull`, `decodePrivateKeyAsHexOrNull`, `decodeEventIdAsHexOrNull`. |
| `ByteArrayExt.kt` | `toNpub()`, `toNsec()`, `toNote()`, `toNEvent()`, `toNProfile()`, `toNAddress()`, `toNEmbed()`, `toLnUrl()` on raw bytes |
| `entities/` | `Entity` (plain interface) and `NPub`, `NSec`, `NNote`, `NEvent`, `NProfile`, `NAddress`, `NRelay`, `NEmbed`; each has `parse(bytes)` and a companion `create(...)` that returns the bech32 string |
| `EventExt.kt` | `Event.toNIP19(relayHint)` — `naddr` for addressable events, `nevent` otherwise |
| `ATagExt.kt` | `ATag.toNAddr()`, `ATag.parseAny`, `ATag.parseNAddr`, `ATag.isATag` |
| `ListEntityExt.kt` | `List<Entity>.eventHints()/eventIds()/addressHints()/pubKeys()…` for parsed content |
| `tlv/`, `TlvTypes.kt` | `TlvBuilder` (`addHex`, `addString`, `addInt`, `…IfNotNull`), `TlvTypes { SPECIAL, RELAY, AUTHOR, KIND }` |
| `bech32/Bech32Util.kt` | `object Bech32` (`encodeBytes`, `decodeBytes`), `String.bechToBytes()` |

## Encoding

```kotlin
val npub = pubkeyBytes.toNpub()                        // or NPub.create(hex)
val nevent = NEvent.create(idHex, authorHex, kind, relays)   // relays: List<NormalizedRelayUrl> (or one, nullable)
val nprofile = NProfile.create(pubkeyHex, relays)
val link = event.toNIP19(relayHint)
```

Entities have no `toBech32()`/`toNostrUri()` methods; use `create(...)`, the `ByteArray`
extensions, or `toNIP19`.

## Decoding

```kotlin
when (val entity = Nip19Parser.uriToRoute(input)?.entity) {   // accepts nostr: URIs
    is NPub -> entity.hex
    is NEvent -> entity.hex to entity.relay                      // relay: List<NormalizedRelayUrl>
    is NAddress -> entity.aTag()                                  // or entity.address()
    null -> /* not bech32 */
}
```

Just need a pubkey from hex/npub/nprofile/nsec? `decodePublicKeyAsHexOrNull(input)`, or
`resolveUserHexOrNull` to also accept NIP-05 (see `nip05-identifiers.md`).

## Gotchas

- Relay hints in `nevent`/`nprofile`/`naddr` are hints; the outbox model decides where to
  fetch.
- `NEmbed` is a gzip-compressed full event (`NEmbed.create(event)`), an Amethyst extension
  beyond NIP-19.
- Never log an `NSec` or the input string it came from. `PubKeysNeverLeakNsecTest`
  (commonTest `nip19Bech32/`) guards the decode helpers.
