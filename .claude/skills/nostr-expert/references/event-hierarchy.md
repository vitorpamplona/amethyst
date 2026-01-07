# Event Hierarchy & Structure

## Core Hierarchy

```
IEvent (empty interface)
  └── Event (@Immutable base class)
      ├── BaseAddressableEvent (replaceable + addressable, has d-tag)
      │   ├── BaseReplaceableEvent (kinds 10000-20000, FIXED_D_TAG = "")
      │   └── [Specific addressable events - 30000-40000]
      └── [Specific event implementations - all other kinds]
```

## Event Base Class

**Location**: `/quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/core/Event.kt`

```kotlin
@Immutable
open class Event(
    val id: HexKey,              // SHA-256 hash of serialized event
    val pubKey: HexKey,          // Author's public key (32 bytes hex)
    val createdAt: Long,         // Unix timestamp
    val kind: Kind,              // Event kind (Int typealias)
    val tags: TagArray,          // Array of tag arrays
    val content: String,         // Event content
    val sig: HexKey,             // schnorr signature (64 bytes hex)
) : IEvent, OptimizedSerializable
```

## Kind Classification

```kotlin
typealias Kind = Int

fun Kind.isEphemeral() = this in 20000..29999
fun Kind.isReplaceable() = this == 0 || this == 3 || this in 10000..19999
fun Kind.isAddressable() = this in 30000..39999
fun Kind.isRegular() = this in 1000..9999
```

## Common Event Types

### Text Note (kind 1)
```kotlin
class TextNoteEvent(...) : BaseThreadedEvent(...),
    EventHintProvider, AddressHintProvider, PubKeyHintProvider, SearchableEvent

// Threading support via markers: reply, root, mention
fun replyTo(): List<Note>  // Direct reply targets
fun root(): Note?          // Root of thread
```

### Metadata (kind 0)
```kotlin
class MetadataEvent(...) : BaseAddressableEvent(...)

// Replaceable: newest version overwrites old
// d-tag automatically set to "" for kind 0
fun name(): String?
fun displayName(): String?
fun picture(): String?
fun about(): String?
fun lnAddress(): String?
```

### Reaction (kind 7)
```kotlin
class ReactionEvent(...) : Event(...)

companion object {
    const val LIKE = "+"
    const val DISLIKE = "-"

    fun like(reactedTo: EventHintBundle<Event>, ...)
    fun dislike(reactedTo: EventHintBundle<Event>, ...)
}
```

### Zap Request/Receipt (kinds 9734, 9735)
```kotlin
class LnZapRequestEvent(...) : Event(...)
    // Created by client, sent to Lightning Address

class LnZapEvent(...) : Event(...)
    // Receipt from LSP, contains bolt11 + embedded zap request
    val zapRequest: LnZapRequestEvent? by lazy { containedPost() }
    val amount: BigDecimal? by lazy { /* parse from bolt11 */ }
```

### Long-Form Content (kind 30023)
```kotlin
class LongTextNoteEvent(...) : BaseAddressableEvent(...)
    // Blog posts, articles
    // Addressable via kind:pubkey:d-tag
```

### Lists (kinds 10000-30004)
```kotlin
sealed class PeopleListEvent : BaseAddressableEvent {
    object MuteList : PeopleListEvent(10000)
    object PinList : PeopleListEvent(10001)
    object BookmarkList : PeopleListEvent(10003)
    // ... 18 list types total
}
```

## Event Interfaces

### Hint Providers
Events can implement interfaces to optimize relay queries:

```kotlin
interface EventHintProvider {
    fun taggedEventIds(): Set<HexKey>
    fun taggedEventRelays(): Map<HexKey, Set<NormalizedRelayUrl>>
}

interface PubKeyHintProvider {
    fun taggedPubKeys(): Set<HexKey>
    fun taggedPubKeyRelays(): Map<HexKey, Set<NormalizedRelayUrl>>
}

interface AddressHintProvider {
    fun taggedAddresses(): Set<Address>
    fun taggedAddressRelays(): Map<Address, Set<NormalizedRelayUrl>>
}

interface SearchableEvent {
    fun subject(): String?
    fun isContentEncoded(): Boolean
}
```

## Event Building Pattern

### DSL Builder
```kotlin
TextNoteEvent.build(
    note = "Hello Nostr",
    replyingTo = eventBundle,
    createdAt = TimeUtils.now()
) {
    pTag(pubKey, relayHint)              // Tag person
    eTag(eventId, relayHint, "reply")    // Tag event with marker
    hashtag("nostr")                     // Add hashtag
    alt("A short note")                  // Alt text
}
```

### Event Template (Low-level)
```kotlin
suspend fun eventTemplate(
    kind: Kind,
    content: String,
    createdAt: Long,
    initializer: TagArrayBuilder.() -> Unit
): EventTemplate {
    val tags = TagArrayBuilder().apply(initializer).build()
    return EventTemplate(kind, tags, content, createdAt)
}

// Sign with signer
val template = eventTemplate(1, "Hello", now()) { pTag(pubkey) }
val signedEvent = signer.sign(template)
```

## Addressable vs Regular Events

| Feature | Regular Event | Addressable Event |
|---------|---------------|-------------------|
| **Identifier** | Event ID (SHA-256 hash) | Address (kind:pubkey:d-tag) |
| **Replaceability** | Immutable | Newest replaces old |
| **d-tag** | Optional | Required |
| **Lookup** | By event ID | By address |
| **Example** | Text note (kind 1) | Metadata (kind 0), Long-form (kind 30023) |

```kotlin
// Regular event address
note = LocalCache.getNoteIfExists(eventId)

// Addressable event address
address = Address(kind = 30023, pubkey = authorHex, dTag = "my-article")
note = LocalCache.getAddressableNoteIfExists(address)
```

## Event Validation

```kotlin
// Verify event ID matches computed hash
fun Event.verifyId(): Boolean =
    EventHasher.hashIdCheck(id, pubKey, createdAt, kind, tags, content)

// Verify signature
fun Event.verifySignature(): Boolean =
    Nip01.verify(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))

// Complete verification
fun Event.checkSignature() {
    if (!verifyId()) throw Exception("ID mismatch")
    if (!verifySignature()) throw Exception("Bad signature!")
}
```

## Event Serialization

```kotlin
// To JSON (for transmission/signing)
fun Event.toJson(): String = OptimizedJsonMapper.toJson(this)

// From JSON
fun Event.fromJson(json: String): Event = OptimizedJsonMapper.fromJson(json)

// Event ID generation (SHA-256 of canonical JSON)
fun EventHasher.hashId(
    pubKey: HexKey,
    createdAt: Long,
    kind: Kind,
    tags: TagArray,
    content: String
): HexKey {
    val serialized = """[0,"$pubKey",$createdAt,$kind,${tags.toJson()},"$content"]"""
    return sha256(serialized.encodeToByteArray()).toHexKey()
}
```

## Event Lifecycle in LocalCache

```
Event received from relay
    ↓
LocalCache.consume(event, relay, wasVerified)
    ↓
getOrCreateNote(event.id) or getOrCreateAddressableNote(address)
    ↓
justVerify(event) → checkSignature()
    ↓
note.loadEvent(event, author, replyTo)
    ↓
Update indices (replies, reactions, boosts)
    ↓
refreshNewNoteObservers(note) → emit to SharedFlow
    ↓
UI updates
```

## Common Event Patterns

### Reply Threading
```kotlin
// Root event (top of thread)
val rootEvent = TextNoteEvent.build("Thread root") { }

// Reply to root
val reply1 = TextNoteEvent.build("First reply", replyingTo = rootEvent) {
    // Automatically adds:
    // ["e", <root_id>, <relay>, "root"]
    // ["e", <root_id>, <relay>, "reply"]
}

// Reply to reply (nested)
val reply2 = TextNoteEvent.build("Nested reply", replyingTo = reply1) {
    // Automatically adds:
    // ["e", <root_id>, <relay>, "root"]
    // ["e", <reply1_id>, <relay>, "reply"]
}
```

### Replaceable Events
```kotlin
// Metadata update (kind 0) - newest wins
val metadata1 = MetadataEvent.createNew(name = "Alice", picture = "url1")
Thread.sleep(1000)
val metadata2 = MetadataEvent.createNew(name = "Alice Updated", picture = "url2")

// LocalCache keeps only metadata2 (higher createdAt)
```

### Event Deletion
```kotlin
// Delete events
val deletion = DeletionEvent.create(
    deleteEvents = listOf(eventId1, eventId2),
    reason = "Spam",
    signer = signer
)

// LocalCache marks events as deleted, but doesn't remove (for verification)
```

## 63+ Event Classes

Full list at `/quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip*/` - one class per event type across 60+ NIP implementations.
