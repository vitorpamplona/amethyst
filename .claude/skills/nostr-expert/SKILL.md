---
name: nostr-expert
description: Nostr protocol implementation patterns in Quartz (AmethystMultiplatform's KMP Nostr library). Use when working with: (1) Nostr events (creating, parsing, signing), (2) Event kinds and tags, (3) NIP implementations (80+ NIP packages in quartz/), (4) Event builders and TagArrayBuilder DSL, (5) Nostr cryptography (secp256k1, NIP-44 encryption), (6) Relay communication patterns, (7) Bech32 encoding (npub, nsec, note, nevent), (8) Resolving user input (hex, npub, nprofile, or NIP-05 `name@domain` internet identifiers) to a pubkey. Complements nostr-protocol agent (NIP specs) - this skill provides Quartz codebase patterns and implementation details.
---

# Nostr Protocol Expert (Quartz Implementation)

Practical patterns for working with Nostr in Quartz, AmethystMultiplatform's KMP Nostr library.

## When to Use This Skill

- Implementing Nostr event types (TextNote, Reaction, Zap, etc.)
- Creating/parsing events with TagArrayBuilder DSL
- Working with event kinds and tags
- Finding NIP implementations in quartz/ codebase
- Nostr cryptography (secp256k1 signing, NIP-44 encryption)
- Bech32 encoding/decoding (npub, nsec, note formats)
- Resolving user input (hex / npub / nprofile / NIP-05 `name@domain`) to a pubkey
- Event validation and verification

**For NIP specifications** → Use `nostr-protocol` agent
**For Quartz implementation** → Use this skill

## Quartz Architecture

Quartz organizes code by NIP number:

```
quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/
├── nip01Core/           # Core protocol (Event, Kind, Tags)
├── nip04Dm/             # Legacy DMs (deprecated)
├── nip10Notes/          # Text notes with threading
├── nip17Dm/             # Private DMs (gift wrap)
├── nip19Bech32/         # Bech32 encoding
├── nip44Encryption/     # Modern encryption (ChaCha20)
├── nip57Zaps/           # Lightning zaps
├── ... (57 NIPs total)
└── experimental/        # Draft NIPs
```

**Pattern**: `nip##<Name>/` directories contain event classes, tags, and utilities for that NIP.

**Find implementations**: Use `scripts/nip-lookup.sh <nip-number>` or see `references/nip-catalog.md`.

## Event Anatomy

### Core Structure

```kotlin
@Immutable
open class Event(
    val id: HexKey,              // SHA-256 hash of serialized event
    val pubKey: HexKey,          // Author's public key (32 bytes hex)
    val createdAt: Long,         // Unix timestamp
    val kind: Kind,              // Event kind (Int typealias)
    val tags: TagArray,          // Array of tag arrays
    val content: String,         // Event content
    val sig: HexKey,             // Schnorr signature (64 bytes hex)
) : IEvent
```

**Key insight**: `Event` is the base class. Specific event types (TextNoteEvent, ReactionEvent) extend it and add parsing/helper methods.

### Kind Classification

```kotlin
typealias Kind = Int

fun Kind.isEphemeral() = this in 20000..29999      // Not stored
fun Kind.isReplaceable() = this == 0 || this == 3 || this in 10000..19999
fun Kind.isAddressable() = this in 30000..39999    // Replaceable + has d-tag
fun Kind.isRegular() = this in 1000..9999          // Stored, not replaced
```

**Pattern**: Kind determines event lifecycle and replaceability.

## Creating Events

### EventTemplate Pattern

```kotlin
fun eventTemplate(
    kind: Kind,
    content: String,
    tags: TagArray = emptyArray()
): EventTemplate
```

**Usage**:
```kotlin
val template = eventTemplate(
    kind = 1,  // Text note
    content = "Hello Nostr!",
    tags = tagArray {
        add(arrayOf("subject", "Greeting"))
    }
)

// Sign with a signer
val signedEvent = signer.sign(template)
```

**Why templates?** Separates event data from signing. Templates can be signed by different signers (local keys, remote signers, hardware wallets).

### TagArrayBuilder DSL

```kotlin
fun <T : Event> tagArray(
    initializer: TagArrayBuilder<T>.() -> Unit
): TagArray
```

**Methods**:
- `add(tag)` - Append tag
- `addFirst(tag)` - Prepend tag (for ordering)
- `addUnique(tag)` - Replace all tags with this name
- `remove(tagName)` - Remove by name
- `addAll(tags)` - Bulk add

**Example**:
```kotlin
val tags = tagArray<TextNoteEvent> {
    add(arrayOf("e", replyToEventId, "", "reply"))
    add(arrayOf("p", authorPubkey))
    addUnique(arrayOf("subject", "Re: Hello"))
    add(arrayOf("content-warning", "spoilers"))
}
```

**Pattern**: Fluent DSL for building tag arrays with validation and deduplication.

## Common Event Types

### TextNoteEvent (kind 1)

```kotlin
class TextNoteEvent : BaseThreadedEvent
```

**Creating**:
```kotlin
val note = eventTemplate(
    kind = 1,
    content = "Hello world!",
    tags = tagArray {
        add(arrayOf("subject", "First post"))
    }
)
```

**Parsing**:
```kotlin
val event: TextNoteEvent = ...
val subject = event.subject()  // Extension from nip14Subject
val mentions = event.mentions()  // List of p-tags
val quotedEvents = event.quotes()  // List of q-tags
```

### ReactionEvent (kind 7)

```kotlin
fun createReaction(
    targetEvent: Event,
    emoji: String = "+"
): EventTemplate {
    return eventTemplate(
        kind = 7,
        content = emoji,
        tags = tagArray {
            add(arrayOf("e", targetEvent.id))
            add(arrayOf("p", targetEvent.pubKey))
        }
    )
}
```

### MetadataEvent (kind 0)

```kotlin
data class UserMetadata(
    val name: String?,
    val displayName: String?,
    val picture: String?,
    val banner: String?,
    val about: String?,
    // ... more fields
)

fun createMetadata(metadata: UserMetadata): EventTemplate {
    return eventTemplate(
        kind = 0,
        content = metadata.toJson()  // Serialize to JSON
    )
}
```

### Addressable Events (kinds 30000-40000)

```kotlin
fun createArticle(
    slug: String,
    title: String,
    content: String
): EventTemplate {
    return eventTemplate(
        kind = 30023,
        content = content,
        tags = tagArray {
            addUnique(arrayOf("d", slug))  // Unique identifier
            add(arrayOf("title", title))
            add(arrayOf("published_at", "${TimeUtils.now()}"))
        }
    )
}
```

**Key**: `d-tag` makes it addressable. Events with same kind + pubkey + d-tag replace each other.

## Tag Patterns

Tags are `Array<String>` with pattern `[name, value, ...optionalParams]`.

### Core Tags

**e-tag** (event reference):
```kotlin
add(arrayOf("e", eventId, relayHint, marker))
// marker: "reply", "root", "mention"
```

**p-tag** (pubkey reference):
```kotlin
add(arrayOf("p", pubkey, relayHint))
```

**a-tag** (addressable event):
```kotlin
add(arrayOf("a", "$kind:$pubkey:$dtag", relayHint))
```

**d-tag** (identifier for addressable events):
```kotlin
addUnique(arrayOf("d", "unique-slug"))
```

### Tag Extensions

```kotlin
// Find tags
event.tags.tagValue("subject")  // First subject tag value
event.tags.allTags("p")  // All p-tags
event.tags.tagValues("e")  // All e-tag values

// Parse structured tags
event.tags.mapNotNull(ETag::parse)  // Parse as ETag objects
```

For comprehensive tag patterns, see `references/tag-patterns.md`.

## Threading (NIP-10)

```kotlin
fun createReply(
    original: TextNoteEvent,
    content: String
): EventTemplate {
    return eventTemplate(
        kind = 1,
        content = content,
        tags = tagArray {
            // Reply marker
            add(arrayOf("e", original.id, "", "reply"))

            // Root marker (original's root, or original itself)
            original.rootEvent()?.let {
                add(arrayOf("e", it.id, "", "root"))
            } ?: add(arrayOf("e", original.id, "", "root"))

            // Tag author
            add(arrayOf("p", original.pubKey))

            // Tag all mentioned users
            original.mentions().forEach {
                add(arrayOf("p", it))
            }
        }
    )
}
```

**Pattern**: `reply` and `root` markers establish thread hierarchy.

## Cryptography

### Signing (secp256k1)

```kotlin
interface ISigner {
    suspend fun sign(template: EventTemplate): Event
}

// Local key signing
class LocalSigner(private val privateKey: ByteArray) : ISigner {
    override suspend fun sign(template: EventTemplate): Event {
        val id = template.generateId()
        val sig = Secp256k1.sign(id, privateKey)
        return Event(id, pubKey, createdAt, kind, tags, content, sig)
    }
}
```

**Pattern**: Signers abstract key management. Can be local, remote (NIP-46), or hardware.

### Encryption (NIP-44)

```kotlin
// Modern encryption (ChaCha20-Poly1305) via the Nip44 facade
// (nip44Encryption/Nip44.kt — picks the current version, decrypts any)
object Nip44 {
    fun encrypt(msg: String, privateKey: ByteArray, pubKey: ByteArray): Nip44v2.EncryptedInfo
    fun decrypt(payload: String, privateKey: ByteArray, pubKey: ByteArray): String
}

// Usage
val encrypted = Nip44.encrypt("Secret message", myPrivateKey, recipientPubKey)
val payload = encrypted.encodePayload()  // base64 string for event content

val decrypted = Nip44.decrypt(payload, myPrivateKey, senderPubKey)
```

Most code should not call `Nip44` directly — go through
`signer.nip44Encrypt(plaintext, toPublicKey)` / `signer.nip44Decrypt(ciphertext, fromPublicKey)`
so remote/external signers keep working.

**Pattern**: Elliptic curve Diffie-Hellman + ChaCha20-Poly1305 AEAD.

### NIP-04 (Deprecated)

```kotlin
// Legacy encryption (NIP-04, deprecated for NIP-44)
object Nip04 {
    fun encrypt(msg: String, privateKey: ByteArray, pubKey: HexKey): String
    fun decrypt(msg: String, privateKey: ByteArray, pubKey: HexKey): String
}
```

**Note**: Use NIP-44 (`Nip44`) for new implementations. NIP-04 has security issues.

## Hex Encoding (HexKey ↔ ByteArray)

Pubkeys, event ids and signatures are lower-case hex. Quartz uses the `HexKey`
typealias (`= String`) plus extensions in `nip01Core/core/HexKey.kt`, backed by
the `Hex` object in `utils/Hex.kt`. **Use these — never hand-roll a byte loop or
import a third-party hex codec.**

```kotlin
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.utils.Hex

val hex: HexKey = bytes.toHexKey()            // ByteArray -> lower-case hex
val back: ByteArray = hex.hexToByteArray()    // hex -> ByteArray (throws on odd length)
val safe: ByteArray? = input.hexToByteArrayOrNull()  // null on invalid hex

Hex.isHex(input)     // valid hex, any length
Hex.isHex64(input)   // ~30% faster fast-path for a 32-byte key/id
hex.isValid()        // 64 chars + valid hex (pubkey / event-id shape)
Hex.isEqual(hex, bytes)  // compare hex to bytes without decoding
```

Constants `PUBKEY_LENGTH` / `EVENT_ID_LENGTH` (both 64) live in `nip01Core.core`.

## Core Utilities (time, random, event id)

Reuse these instead of hand-rolling — each avoids a common mistake:

```kotlin
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher

TimeUtils.now()            // Unix SECONDS for created_at — not currentTimeMillis()/1000
TimeUtils.oneHourAgo()     // relative filter bounds (…Ago / …FromNow); all in seconds
RandomInstance.bytes(32)   // secure random (SecureRandom) — for nonces/keys, not kotlin.random.Random
RandomInstance.randomChars()   // 16-char subscription id

sha256(bytes)              // raw hash primitive
EventHasher.hashId(pubKey, createdAt, kind, tags, content)   // canonical event id
EventHasher.hashIdCheck(id, pubKey, createdAt, kind, tags, content)  // verify untrusted events
```

`EventHasher` serializes `[0, pubkey, created_at, kind, tags, content]` in the
exact form NIP-01 requires — prefer it over calling `sha256` on your own JSON.

## Bech32 Encoding (NIP-19)

Encoding uses extension functions on `ByteArray` (`nip19Bech32/ByteArrayExt.kt`);
TLV entities carry relay hints via `create()` helpers on the entity classes in
`nip19Bech32/entities/`. Decoding goes through `Nip19Parser`, whose
`uriToRoute()` returns a `ParseReturn?` wrapping the parsed `Entity`.

```kotlin
// Encode simple entities: ByteArray extensions
val npub = pubkeyBytes.toNpub()   // "npub1..."
val nsec = privKeyBytes.toNsec()  // "nsec1..."
val note = eventIdBytes.toNote()  // "note1..."

// Encode TLV entities with relay hints (relays: List<NormalizedRelayUrl>)
val nevent = NEvent.create(eventIdHex, authorHex, kind, relays)
val nprofile = NProfile.create(pubkeyHex, relays)
```

**Usage**:
```kotlin
// Decode (also accepts nostr: URIs); entity types live in nip19Bech32.entities
when (val entity = Nip19Parser.uriToRoute(input)?.entity) {
    is NPub -> println("Pubkey: ${entity.hex}")
    is NEvent -> println("Event: ${entity.hex}, relays: ${entity.relay}")
    is NAddress -> println("Address: ${entity.aTag()}")
    null -> println("not a valid bech32 entity")
    else -> println("Other type")
}
```

## Resolving User Input to a Pubkey (NIP-05 + NIP-19)

**Before writing any `if (isHex) … else if (npub) … else if ("@" in s) fetchWellKnown()` logic, stop — it already exists.** `resolveUserHexOrNull` in `quartz/nip05DnsIdentifiers/` accepts every identifier form a user might type and returns a 64-hex pubkey.

```kotlin
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull

// hex | npub1… | nprofile1… | nsec1… | name@domain.tld  →  HexKey? (null if unrecognized/lookup fails)
val pubkey = resolveUserHexOrNull(userInput, nip05Client)
```

- Tries the **synchronous** hex/bech32 path first (`decodePublicKeyAsHexOrNull`) — only NIP-05-shaped input hits the network.
- `suspend`; re-throws only `CancellationException`. Pass `nip05Client = null` for offline contexts.
- Build the client with `Nip05Client(fetcher = OkHttpNip05Fetcher { _ -> okHttp })` (see `cli/Context.kt`). The OkHttp fetcher already runs on IO and disables redirects per the NIP-05 spec — don't re-implement the `.well-known/nostr.json` fetch or JSON parse.
- Need only hex/bech32 (no network)? Use `decodePublicKeyAsHexOrNull(input)` directly.
- Need to *verify* a claimed identifier maps back to a pubkey? `nip05Client.verify(Nip05Id.parse(id)!!, pubkey)`.

See `references/nip05-identifiers.md` for the full API surface (`Nip05Id`, `Nip05Client`, `Nip05Parser`, `KeyInfoSet`, Namecoin `.bit`) and the hand-rolled anti-pattern to avoid.

## Event Validation

```kotlin
fun Event.verify(): Boolean {
    // 1. Verify ID matches content hash
    val computedId = generateId()
    if (id != computedId) return false

    // 2. Verify signature
    return Secp256k1.verify(id, sig, pubKey)
}

fun Event.generateId(): HexKey {
    val serialized = serializeForId()  // JSON array format
    return sha256(serialized)
}
```

**Pattern**: Always verify events from untrusted sources (relays).

## Common Workflows

### Publishing an Event

```kotlin
suspend fun publishNote(content: String, signer: ISigner, relays: List<String>) {
    // 1. Create template
    val template = eventTemplate(kind = 1, content = content)

    // 2. Sign
    val event = signer.sign(template)

    // 3. Verify (optional but recommended)
    require(event.verify()) { "Signature verification failed" }

    // 4. Publish to relays
    relays.forEach { relay ->
        relayClient.send(relay, event)
    }
}
```

### Querying Events

```kotlin
// Subscription filter
data class Filter(
    val ids: List<HexKey>? = null,
    val authors: List<HexKey>? = null,
    val kinds: List<Kind>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val tags: Map<String, List<String>>? = null  // e.g., {"#e": [eventId], "#p": [pubkey]}
)

// Usage
val filter = Filter(
    authors = listOf(userPubkey),
    kinds = listOf(1),  // Text notes only
    limit = 50
)

relayClient.subscribe(relay, filter) { event ->
    // Handle incoming events
}
```

### Creating a Zap (NIP-57)

```kotlin
fun createZapRequest(
    targetEvent: Event,
    amountSats: Long,
    comment: String = ""
): EventTemplate {
    return eventTemplate(
        kind = 9734,  // Zap request
        content = comment,
        tags = tagArray {
            add(arrayOf("e", targetEvent.id))
            add(arrayOf("p", targetEvent.pubKey))
            add(arrayOf("amount", "${amountSats * 1000}"))  // millisats
            add(arrayOf("relays", "wss://relay1.com", "wss://relay2.com"))
        }
    )
}
```

### Gift-Wrapped DMs (NIP-17)

```kotlin
fun createGiftWrappedDM(
    recipientPubkey: HexKey,
    message: String,
    signer: ISigner
): Event {
    // 1. Create sealed gossip (kind 14)
    val sealedGossip = createSealedGossip(message, recipientPubkey, signer)

    // 2. Wrap in gift wrap (kind 1059)
    return createGiftWrap(sealedGossip, recipientPubkey, signer)
}
```

**Pattern**: Double encryption + random ephemeral keys for metadata protection.

## Finding NIPs

Use the bundled script:

```bash
# Find by NIP number
scripts/nip-lookup.sh 44

# Search by term
scripts/nip-lookup.sh encryption
scripts/nip-lookup.sh "gift wrap"
```

Or see `references/nip-catalog.md` for complete catalog.

## Bundled Resources

- **references/nip-catalog.md** - All 57 NIPs with package locations and key files
- **references/event-hierarchy.md** - Event class hierarchy, kind classifications, common types
- **references/tag-patterns.md** - Tag structure, TagArrayBuilder DSL, common tag types, parsing patterns
- **references/nip19-bech32.md** - `Nip19Parser`, `Bech32Util`, `TlvBuilder`, entity types (NPub, NSec, NEvent, NAddress, NProfile, NRelay, NEmbed)
- **references/nip05-identifiers.md** - Resolving any identifier (hex/npub/nprofile/nsec/`name@domain`) to a pubkey via `resolveUserHexOrNull`; `Nip05Client`, `Nip05Id`, `Nip05Parser`, Namecoin `.bit` — and the hand-rolled anti-pattern to avoid
- **references/event-factory.md** - `EventFactory` dispatch pattern and how to register a new kind
- **references/crypto-and-encryption.md** - Event signing/verification, secp256k1 abstraction, NIP-44 encryption, `SharedKeyCache`
- **references/large-cache.md** - `LargeCache<K,V>` expect/actual + `ICacheOperations` functional API
- **scripts/nip-lookup.sh** - Find NIP implementations by number or search term

## Quick Reference

| Task | Pattern | Location |
|------|---------|----------|
| Create event | `eventTemplate(kind, content, tags)` | nip01Core/signers/ |
| Build tags | `tagArray { add(...) }` | nip01Core/core/ |
| Sign event | `signer.sign(template)` | nip01Core/signers/ |
| Verify signature | `event.verify()` | nip01Core/core/ |
| Encrypt (NIP-44) | `Nip44v2.encrypt(...)` | nip44Encryption/ |
| Bech32 encode | `Nip19.npubEncode(...)` | nip19Bech32/ |
| Resolve input → pubkey | `resolveUserHexOrNull(input, nip05Client)` | nip05DnsIdentifiers/ |
| Decode bech32 → pubkey (no net) | `decodePublicKeyAsHexOrNull(input)` | nip19Bech32/ |
| Verify NIP-05 identifier | `nip05Client.verify(Nip05Id.parse(id)!!, hex)` | nip05DnsIdentifiers/ |
| Find NIP | `scripts/nip-lookup.sh <number>` | - |

## Common Event Kinds

| Kind | Type | NIP | Package |
|------|------|-----|---------|
| 0 | Metadata | 01 | nip01Core/ |
| 1 | Text note | 01, 10 | nip10Notes/ |
| 3 | Contact list | 02 | nip02FollowList/ |
| 5 | Deletion | 09 | nip09Deletions/ |
| 7 | Reaction | 25 | nip25Reactions/ |
| 1059 | Gift wrap | 59 | nip59Giftwrap/ |
| 9734 | Zap request | 57 | nip57Zaps/ |
| 9735 | Zap receipt | 57 | nip57Zaps/ |
| 10002 | Relay list | 65 | nip65RelayList/ |
| 30023 | Long-form content | 23 | nip23LongContent/ |

## Related Skills

- **nostr-protocol** - NIP specifications and protocol details
- **kotlin-expert** - Kotlin patterns (@Immutable, sealed classes, DSLs)
- **kotlin-coroutines** - Async patterns for relay communication
- **kotlin-multiplatform** - KMP patterns, expect/actual in Quartz
