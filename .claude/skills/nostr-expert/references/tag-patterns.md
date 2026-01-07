# Tag Patterns in Quartz

Tags are the primary way events reference other events, users, and metadata in Nostr.

## Tag Structure

```kotlin
typealias Tag = Array<String>  // ["tag_name", "value", "optional_param", ...]
typealias TagArray = Array<Tag>
```

**Pattern**: `[name, value, ...optionalParams]`

## TagArrayBuilder DSL

```kotlin
fun tagArray(initializer: TagArrayBuilder<T>.() -> Unit): TagArray
```

**Methods**:
- `add(tag)` - Append tag
- `addFirst(tag)` - Prepend tag
- `addUnique(tag)` - Replace all tags with this name
- `remove(tagName)` - Remove all tags with name
- `removeIf(predicate, toCompare)` - Conditional removal

**Example**:
```kotlin
val tags = tagArray<TextNoteEvent> {
    add(arrayOf("e", eventId, relayHint, "reply"))
    add(arrayOf("p", pubkey))
    addUnique(arrayOf("subject", "Hello"))
}
```

## Core Tag Types (NIP-01)

### e-tag (Event Reference)
```kotlin
// ["e", <event-id>, <relay-hint>, <marker>]
arrayOf("e", eventId, "wss://relay.damus.io", "reply")
```

**Markers** (NIP-10):
- `root` - Root of thread
- `reply` - Direct reply target
- `mention` - Mentioned event (not reply)

**Extensions**:
```kotlin
// nip01Core/tags/
fun TagArrayBuilder.eTag(eventId: HexKey, relay: String? = null, marker: String? = null)
```

### p-tag (Pubkey Reference)
```kotlin
// ["p", <pubkey>, <relay-hint>]
arrayOf("p", pubkey, "wss://relay.damus.io")
```

**Usage**: Tag users, indicate recipients

**Extensions**:
```kotlin
fun TagArrayBuilder.pTag(pubkey: HexKey, relay: String? = null)
```

### a-tag (Addressable Event Reference)
```kotlin
// ["a", <kind>:<pubkey>:<d-tag>, <relay-hint>]
arrayOf("a", "30023:${authorPubkey}:${dtag}", "wss://relay.damus.io")
```

**Usage**: Reference replaceable/addressable events (kinds 10000-20000, 30000-40000)

**Extensions**:
```kotlin
fun TagArrayBuilder.aTag(kind: Int, pubkey: HexKey, dTag: String, relay: String? = null)
```

### d-tag (Identifier)
```kotlin
// ["d", <identifier>]
arrayOf("d", "my-article-slug")
```

**Usage**: Unique identifier for addressable events

## Common Tag Extensions

### Subject (NIP-14)
```kotlin
// nip14Subject/
fun Event.subject(): String?
fun TagArrayBuilder.subject(text: String)
```

### Content Warning (NIP-36)
```kotlin
// nip36SensitiveContent/
fun Event.contentWarning(): String?
fun TagArrayBuilder.contentWarning(reason: String = "")
```

### Expiration (NIP-40)
```kotlin
// nip40Expiration/
fun Event.expiration(): Long?
fun TagArrayBuilder.expiration(unixTimestamp: Long)
```

### Alt Description (NIP-31)
```kotlin
// nip31Alts/
fun Event.alt(): String?
fun TagArrayBuilder.alt(description: String)
```

## Specialized Tags

### Zap Tags (NIP-57)
```kotlin
// nip57Zaps/tags/
class BoltTag(val bolt11: String, val preimage: String?)
class DescriptionTag(val zapRequestJson: String)
```

### Imeta Tags (NIP-92)
```kotlin
// nip92IMeta/
class IMetaTag(val url: String, val metadata: Map<String, String>)

// Usage: Image metadata
IMetaTag("https://example.com/image.jpg", mapOf(
    "m" to "image/jpeg",
    "dim" to "1920x1080",
    "blurhash" to "..."
))
```

### Relay Tags (NIP-65)
```kotlin
// nip65RelayList/
class RelayTag(val url: String, val type: RelayType)
enum class RelayType { READ, WRITE, BOTH }
```

## Tag Query Patterns

### Finding Tags
```kotlin
// Extension functions on TagArray
fun TagArray.firstTag(name: String): Tag?
fun TagArray.allTags(name: String): List<Tag>
fun TagArray.tagValue(name: String): String?
fun TagArray.tagValues(name: String): List<String>
```

**Example**:
```kotlin
val event: TextNoteEvent = ...
val subject = event.tags.tagValue("subject")
val mentions = event.tags.allTags("p").mapNotNull { it.getOrNull(1) }
```

### Parsing Tags
```kotlin
// Pattern: Companion object with parse methods
object ETag {
    fun parse(tag: Tag): ETag? {
        if (tag.getOrNull(0) != "e") return null
        return ETag(
            eventId = tag.getOrNull(1) ?: return null,
            relay = tag.getOrNull(2),
            marker = tag.getOrNull(3)
        )
    }
}

// Usage
val eTags = event.tags.mapNotNull(ETag::parse)
```

## Event Builder Pattern

Combining TagArrayBuilder with event creation:

```kotlin
fun createTextNote(content: String, replyTo: Event?): EventTemplate {
    return eventTemplate(
        kind = 1,
        content = content,
        tags = tagArray {
            replyTo?.let {
                eTag(it.id, marker = "reply")
                pTag(it.pubKey)
                it.rootEvent()?.let { root ->
                    eTag(root.id, marker = "root")
                }
            }
        }
    )
}
```

## Hint System

Tags can provide "hints" - optional relay URLs for fetching referenced content:

```kotlin
// Event references
["e", eventId, "wss://relay.example.com"]  // relay hint

// Pubkey references
["p", pubkey, "wss://relay.example.com"]  // relay hint

// Addressable references
["a", "30023:pubkey:dtag", "wss://relay.example.com"]  // relay hint
```

**Pattern**: Third parameter (index 2) is always the relay hint

## Tag Validation

```kotlin
// Common validations
fun validateETag(tag: Tag): Boolean {
    return tag.getOrNull(0) == "e" && tag.getOrNull(1)?.isValidHex() == true
}

fun validatePTag(tag: Tag): Boolean {
    return tag.getOrNull(0) == "p" && tag.getOrNull(1)?.isValidHex() == true
}
```

## Performance Patterns

### Tag Indexing
```kotlin
// TagArrayBuilder keeps an index by tag name
private val tagList = mutableMapOf<String, MutableList<Tag>>()

// Fast lookup by name
fun remove(tagName: String) {
    tagList.remove(tagName)
}
```

### Lazy Parsing
```kotlin
// Don't parse all tags upfront
class TextNoteEvent(...) {
    private val _mentions by lazy {
        tags.mapNotNull(PTag::parse)
    }

    fun mentions() = _mentions
}
```

## Common Workflows

### Creating a Reply
```kotlin
fun replyTo(original: TextNoteEvent, content: String): EventTemplate {
    return eventTemplate(
        kind = 1,
        content = content,
        tags = tagArray {
            // Reply to this event
            eTag(original.id, marker = "reply")

            // Copy root marker if exists, or mark original as root
            original.rootEvent()?.let {
                eTag(it.id, marker = "root")
            } ?: eTag(original.id, marker = "root")

            // Tag author
            pTag(original.pubKey)

            // Tag all mentioned users
            original.mentions().forEach { pTag(it) }
        }
    )
}
```

### Creating a Reaction
```kotlin
fun createReaction(targetEvent: Event, emoji: String): EventTemplate {
    return eventTemplate(
        kind = 7,
        content = emoji,
        tags = tagArray {
            eTag(targetEvent.id)
            pTag(targetEvent.pubKey)
        }
    )
}
```

### Creating an Addressable Event
```kotlin
fun createArticle(title: String, content: String, slug: String): EventTemplate {
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

## Quick Reference

| Tag | NIP | Usage | Example |
|-----|-----|-------|---------|
| e | 01 | Event reference | `["e", eventId, relay, marker]` |
| p | 01 | Pubkey reference | `["p", pubkey, relay]` |
| a | 01 | Addressable event | `["a", "kind:pubkey:d"]` |
| d | 01 | Identifier | `["d", "unique-id"]` |
| subject | 14 | Subject line | `["subject", "Hello"]` |
| content-warning | 36 | Content warning | `["content-warning", "nsfw"]` |
| expiration | 40 | Expiration time | `["expiration", "1234567890"]` |
| bolt11 | 57 | Lightning invoice | `["bolt11", "lnbc..."]` |
| imeta | 92 | Media metadata | `["imeta", "url", "m", "image/jpeg"]` |
| relay | 65 | User relays | `["relay", "wss://...", "read"]` |

## Resources

- Tag builders: `quartz/src/commonMain/.../nip01Core/tags/`
- Tag extensions: Look for `TagArrayExt.kt`, `TagArrayBuilderExt.kt` in each NIP package
- Event parsing: Each event class has tag parsing methods
