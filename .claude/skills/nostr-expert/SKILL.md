---
name: nostr-expert
description: Nostr protocol implementation patterns in Quartz (AmethystMultiplatform's KMP Nostr library). Use when working with: (1) Nostr events (creating, parsing, signing), (2) Event kinds and tags, (3) NIP implementations (57 NIPs in quartz/), (4) Event builders and TagArrayBuilder DSL, (5) Nostr cryptography (secp256k1, NIP-44 encryption), (6) Relay communication patterns, (7) Bech32 encoding (npub, nsec, note, nevent). Complements nostr-protocol agent (NIP specs) - this skill provides Quartz codebase patterns and implementation details.
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
// Modern encryption (ChaCha20-Poly1305)
object Nip44v2 {
    fun encrypt(plaintext: String, privateKey: ByteArray, pubKey: HexKey): String
    fun decrypt(ciphertext: String, privateKey: ByteArray, pubKey: HexKey): String
}

// Usage
val encrypted = Nip44v2.encrypt(
    plaintext = "Secret message",
    privateKey = myPrivateKey,
    pubKey = recipientPubKey
)

val decrypted = Nip44v2.decrypt(
    ciphertext = encrypted,
    privateKey = myPrivateKey,
    pubKey = senderPubKey
)
```

**Pattern**: Elliptic curve Diffie-Hellman + ChaCha20-Poly1305 AEAD.

### NIP-04 (Deprecated)

```kotlin
// Legacy encryption (NIP-04, deprecated for NIP-44)
object Nip04 {
    fun encrypt(msg: String, privateKey: ByteArray, pubKey: HexKey): String
    fun decrypt(msg: String, privateKey: ByteArray, pubKey: HexKey): String
}
```

**Note**: Use NIP-44 (Nip44v2) for new implementations. NIP-04 has security issues.

## Bech32 Encoding (NIP-19)

```kotlin
object Nip19 {
    // Encode
    fun npubEncode(pubkey: HexKey): String  // npub1...
    fun nsecEncode(privateKey: ByteArray): String  // nsec1...
    fun noteEncode(eventId: HexKey): String  // note1...
    fun neventEncode(eventId: HexKey, relays: List<String> = emptyList()): String
    fun nprofileEncode(pubkey: HexKey, relays: List<String> = emptyList()): String
    fun naddrEncode(kind: Int, pubkey: HexKey, dTag: String, relays: List<String> = emptyList()): String

    // Decode
    fun decode(bech32: String): Nip19Result
}

sealed class Nip19Result {
    data class NPub(val hex: HexKey) : Nip19Result()
    data class NSec(val hex: HexKey) : Nip19Result()
    data class Note(val hex: HexKey) : Nip19Result()
    data class NEvent(val hex: HexKey, val relays: List<String>) : Nip19Result()
    data class NProfile(val hex: HexKey, val relays: List<String>) : Nip19Result()
    data class NAddr(val kind: Int, val pubkey: HexKey, val dTag: String, val relays: List<String>) : Nip19Result()
}
```

**Usage**:
```kotlin
// Encode
val npub = Nip19.npubEncode(pubkeyHex)
// Output: "npub1..."

// Decode
when (val result = Nip19.decode(npub)) {
    is Nip19Result.NPub -> println("Pubkey: ${result.hex}")
    is Nip19Result.NEvent -> println("Event: ${result.hex}, relays: ${result.relays}")
    else -> println("Other type")
}
```

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
