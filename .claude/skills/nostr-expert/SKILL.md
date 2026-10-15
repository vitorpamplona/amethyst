---
name: nostr-expert
description: Quartz Nostr code: event classes, kinds, tags, TagArrayBuilder, EventFactory, NIP-19 bech32, NIP-44, hex/time/hash utils, resolving npub/nprofile/NIP-05 to a pubkey, looking up a NIP spec. Use before writing protocol code in quartz/.
---

# Nostr in Quartz

Quartz ([the Quartz repository](https://github.com/vitorpamplona/quartz)) has one package per
NIP — about 95 `nipXX<Name>/` directories (hex ids too: `nip5aStaticWebsites`,
`nipB7Blossom`, `nipXX…` for unnumbered drafts) — plus `experimental/` and the non-NIP
protocol families (`marmot/`, `concord/`, `cordn/`, `contextvm/`, `buzz/`, `mls/`, …)
listed in AGENTS.md › Project Overview. `references/nip-catalog.md` maps every package
to its event classes and kinds.

## Look up a NIP

1. The spec is the source of truth: `https://github.com/nostr-protocol/nips/blob/master/NN.md`
   (zero-pad: `01.md`; hex ids as named: `B7.md`).
2. `.claude/skills/nostr-expert/scripts/nip-lookup.sh NN` lists the package's files
   (`NN` decimal or two-char hex; a word does a text search across `nip*` packages).
3. `grep -rln "NIP-NN\|nipNN" quartz/src/` for code outside the package.
4. Keep what the NIP requires apart from what Quartz already does and from your advice.

## Building events

Every event class has `const val KIND` and a companion `build(...)` returning an unsigned
`EventTemplate<T>`; a `NostrSigner` signs it (see `auth-signers`). Copy the shape of a
sibling class in the same NIP; `references/event-hierarchy.md` covers the base classes.

```kotlin
// nip01Core/signers/EventTemplate.kt
inline fun <T : Event> eventTemplate(kind: Int, description: String, createdAt: Long = TimeUtils.now(),
    initializer: TagArrayBuilder<T>.() -> Unit = {}) = EventTemplate<T>(createdAt, kind, tagArray(initializer), description)

// usage — nip10Notes/TextNoteEvent.kt
val template = TextNoteEvent.build("gm", replyingTo = parentHintBundle) { hashtag("nostr") }
val signed: TextNoteEvent = signer.sign(template)
```

- Edit an existing event's tags with `eventUpdate(base) { … }` / `event.update { … }`.
- Tags: each tag is a `XxxTag` codec + `TagArrayBuilderExt.kt` writer + `TagArrayExt.kt`
  reader in its NIP package. Grep for the builder before writing `add(arrayOf(...))`.
  See `references/tag-patterns.md`.
- Kind ranges (`nip01Core/core/Kind.kt`): `isRegular()` is `> 0 && < 10_000 && != 3`
  (kind 0 and 3 are replaceable), `isReplaceable()` is 0, 3 and 10000–19999,
  `isEphemeral()` 20000–29999, `isAddressable()` 30000–39999.
- A new kind needs a class, a registration in its Quartz module's `EventKindProvider` **and**
  a `LocalCache` dispatch branch, or it is silently dropped: `references/event-factory.md`.

## Hex (`nip01Core/core/HexKey.kt`, `utils/Hex.kt`)

Pubkeys, ids and signatures are lower-case hex `HexKey` (`= String`). Never hand-roll a
byte loop or pull in a hex library.

```kotlin
bytes.toHexKey()               // ByteArray -> hex
hex.hexToByteArray()           // throws on bad input
input.hexToByteArrayOrNull()   // null on invalid hex
Hex.isHex(input); Hex.isHex64(input)   // isHex64: fast path for a 32-byte key/id
hex.isValid()                  // 64 chars + valid hex
Hex.isEqual(hex, bytes)        // compare without decoding
```

`PUBKEY_LENGTH` / `EVENT_ID_LENGTH` (both 64) live in `nip01Core.core`.

## Time, randomness, hashing

```kotlin
TimeUtils.now()             // utils/TimeUtils.kt — Unix SECONDS for created_at
TimeUtils.oneHourAgo()      // relative bounds (…Ago / …FromNow), also seconds
RandomInstance.bytes(32)    // utils/RandomInstance.kt — secure random, not kotlin.random
RandomInstance.randomChars()     // 16-char subscription id
sha256(bytes)               // utils/sha256/Sha256.kt
EventHasher.hashId(pubKey, createdAt, kind, tags, content)          // nip01Core/crypto/
EventHasher.hashIdCheck(id, pubKey, createdAt, kind, tags, content) // verify untrusted ids
```

Use `EventHasher`, not `sha256` over your own JSON — the NIP-01 serialization is exact.
Verification: `event.verify()` / `checkSignature()` in `nip01Core/crypto/EventExt.kt`.

## Encryption

App code encrypts through the signer — `signer.nip44Encrypt(plaintext, toPublicKey)`,
`signer.nip44Decrypt(ciphertext, fromPublicKey)`, or `signer.decrypt(...)` (picks NIP-04
vs NIP-44) — so remote and external signers work. `Nip44.encrypt/decrypt`
(`nip44Encryption/Nip44.kt`) is for code that holds the raw key. NIP-04 is legacy.
See `references/crypto-and-encryption.md`.

## NIP-19 bech32

```kotlin
pubkeyBytes.toNpub(); privKeyBytes.toNsec(); idBytes.toNote()     // nip19Bech32/ByteArrayExt.kt
NEvent.create(idHex, authorHex, kind, relays); NProfile.create(pubkeyHex, relays)
event.toNIP19(relayHint)                                          // naddr or nevent
when (val e = Nip19Parser.uriToRoute(input)?.entity) { is NPub -> e.hex; is NAddress -> e.aTag(); … }
```

There is no `Nip19.npubEncode` and entities have no `toBech32()`. See
`references/nip19-bech32.md`.

## Resolving user input to a pubkey

**Before writing `if (isHex) … else if (npub) … else if ("@" in s) fetchWellKnown()`,
stop — it exists.**

```kotlin
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull

// hex | npub1… | nprofile1… | nsec1… | name@domain.tld  ->  HexKey? (null if unrecognized or lookup fails)
val pubkey = resolveUserHexOrNull(userInput, nip05Client)
```

- Tries the synchronous hex/bech32 path first (`decodePublicKeyAsHexOrNull`); only
  NIP-05-shaped input hits the network. `suspend`; re-throws only
  `CancellationException`. Pass `nip05Client = null` offline.
- Client: `Nip05Client(fetcher = OkHttpNip05Fetcher { _ -> okHttp })` (as in
  `cli/.../commands/LoginCommand.kt`). The fetcher runs on IO and refuses redirects per
  NIP-05 — don't re-implement `.well-known/nostr.json`.
- Verify a claimed identifier: `nip05Client.verify(Nip05Id.parse(id)!!, pubkey)`.

Full API (`Nip05Id`, `Nip05Parser`, `KeyInfoSet`, Namecoin `.bit`) and the anti-pattern:
`references/nip05-identifiers.md`.

## Relays

Relay operations (fetch, page, publish-and-confirm, count, negentropy) are
`INostrClient` extensions — see AGENTS.md › Feature Workflow. Compose-scoped
subscriptions are the `relay-client` skill.

## References

- `references/nip-catalog.md` — every package → event classes and kinds (generated by
  `scripts/nip-catalog.py`; regenerate, don't hand-edit)
- `references/event-hierarchy.md` — `Event` / `BaseReplaceableEvent` / `BaseAddressableEvent`, kind ranges, hint interfaces
- `references/tag-patterns.md` — tag codec layout, `TagArrayBuilder`, core tags, readers
- `references/event-factory.md` — `EventFactory` and the three registrations a new kind needs
- `references/nip19-bech32.md` — `Nip19Parser`, entities, encoders
- `references/nip05-identifiers.md` — `resolveUserHexOrNull`, `Nip05Client`, Namecoin
- `references/crypto-and-encryption.md` — `EventHasher`, `Nip01Crypto`, NIP-44
- `references/large-cache.md` — `LargeCache`, the store under `LocalCache`
- `scripts/nip-lookup.sh` — files of one NIP package, or a text search

Related skills: `auth-signers` (signing), `event-store-semantics` (stores, NIP-50
search), `nip85-trusted-assertions`, `relay-client`, `kotlin-multiplatform`
(expect/actual in Quartz).
