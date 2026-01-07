# DSL Builder Examples

Type-safe fluent APIs and DSL patterns from the codebase.

## Table of Contents
- [TagArrayBuilder Pattern](#tagarraybuilder-pattern)
- [Builder Variations](#builder-variations)
- [DSL Principles](#dsl-principles)
- [Creating Custom DSLs](#creating-custom-dsls)

---

## TagArrayBuilder Pattern

### Core Implementation

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/core/TagArrayBuilder.kt:23-91`

```kotlin
class TagArrayBuilder<T : IEvent> {
    private val tagList = mutableMapOf<String, MutableList<Tag>>()

    fun remove(tagName: String): TagArrayBuilder<T> {
        tagList.remove(tagName)
        return this  // Method chaining
    }

    fun remove(tagName: String, tagValue: String): TagArrayBuilder<T> {
        tagList[tagName]?.removeAll { it.valueOrNull() == tagValue }
        if (tagList[tagName]?.isEmpty() == true) {
            tagList.remove(tagName)
        }
        return this
    }

    fun removeIf(
        predicate: (Tag, Tag) -> Boolean,
        toCompare: Tag
    ): TagArrayBuilder<T> {
        val tagName = toCompare.nameOrNull() ?: return this
        tagList[tagName]?.removeAll { predicate(it, toCompare) }
        if (tagList[tagName]?.isEmpty() == true) {
            tagList.remove(tagName)
        }
        return this
    }

    fun add(tag: Array<String>): TagArrayBuilder<T> {
        if (tag.isEmpty() || tag[0].isEmpty()) return this
        tagList.getOrPut(tag[0], ::mutableListOf).add(tag)
        return this
    }

    fun addFirst(tag: Array<String>): TagArrayBuilder<T> {
        if (tag.isEmpty() || tag[0].isEmpty()) return this
        tagList.getOrPut(tag[0], ::mutableListOf).add(0, tag)
        return this
    }

    fun addUnique(tag: Array<String>): TagArrayBuilder<T> {
        if (tag.isEmpty() || tag[0].isEmpty()) return this
        tagList[tag[0]] = mutableListOf(tag)  // Replace existing
        return this
    }

    fun addAll(tag: List<Array<String>>): TagArrayBuilder<T> {
        tag.forEach(::add)
        return this
    }

    fun toTypedArray() = tagList.flatMap { it.value }.toTypedArray()

    fun build() = toTypedArray()
}

// Inline DSL function with lambda receiver
inline fun <T : Event> tagArray(
    initializer: TagArrayBuilder<T>.() -> Unit = {}
): TagArray = TagArrayBuilder<T>().apply(initializer).build()
```

### Usage Examples

**Basic usage:**

```kotlin
val tags = tagArray<TextNoteEvent> {
    add(arrayOf("e", eventId, relay, "reply"))
    add(arrayOf("p", pubkey))
    add(arrayOf("t", "bitcoin"))
}
```

**Advanced patterns:**

```kotlin
// Remove and add
val tags = tagArray<TextNoteEvent> {
    addAll(existingTags)
    remove("a")  // Remove all address tags
    addUnique(arrayOf("client", "Amethyst"))  // Replace client tag
}

// Conditional building
val tags = tagArray<TextNoteEvent> {
    add(arrayOf("e", rootId, "", "root"))

    if (replyToId != null) {
        add(arrayOf("e", replyToId, "", "reply"))
    }

    mentionedPubkeys.forEach { pubkey ->
        add(arrayOf("p", pubkey))
    }

    hashtags.forEach { tag ->
        add(arrayOf("t", tag.lowercase()))
    }
}

// Custom predicate removal
val tags = tagArray<TextNoteEvent> {
    addAll(originalTags)
    removeIf(
        predicate = { tag, compare -> tag[1] == compare[1] },
        toCompare = arrayOf("e", eventIdToRemove)
    )
}
```

---

## Builder Variations

### PrivateTagArrayBuilder

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip51Lists/PrivateTagArrayBuilder.kt`

```kotlin
class PrivateTagArrayBuilder {
    private val builder = TagArrayBuilder<Event>()

    fun add(tag: PrivateTag): PrivateTagArrayBuilder {
        builder.add(tag.toArray())
        return this
    }

    fun addAll(tags: List<PrivateTag>): PrivateTagArrayBuilder {
        tags.forEach { add(it) }
        return this
    }

    fun build(): Array<Array<String>> = builder.build()
}

// DSL function
inline fun privateTagArray(
    initializer: PrivateTagArrayBuilder.() -> Unit
): Array<Array<String>> = PrivateTagArrayBuilder().apply(initializer).build()
```

**Usage:**

```kotlin
val privateTags = privateTagArray {
    add(PrivateTag.Event(eventId, marker = "bookmark"))
    add(PrivateTag.Profile(pubkey))
    addAll(existingPrivateTags)
}
```

### TlvBuilder

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip19Bech32/tlv/TlvBuilder.kt`

```kotlin
class TlvBuilder {
    private val entries = mutableListOf<TlvEntry>()

    fun add(type: TlvType, value: ByteArray): TlvBuilder {
        entries.add(TlvEntry(type, value))
        return this
    }

    fun addRelay(relay: String): TlvBuilder {
        add(TlvType.Relay, relay.encodeToByteArray())
        return this
    }

    fun addAuthor(pubkey: ByteArray): TlvBuilder {
        add(TlvType.Author, pubkey)
        return this
    }

    fun addKind(kind: Int): TlvBuilder {
        add(TlvType.Kind, kind.toByteArray())
        return this
    }

    fun build(): ByteArray {
        return entries.flatMap { it.encode() }.toByteArray()
    }
}

fun tlv(init: TlvBuilder.() -> Unit): ByteArray =
    TlvBuilder().apply(init).build()
```

**Usage:**

```kotlin
val tlvData = tlv {
    addAuthor(pubkeyBytes)
    addRelay("wss://relay.damus.io")
    addKind(1)
}
```

### MapOfSetBuilder

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/MapOfSetBuilder.kt`

```kotlin
class MapOfSetBuilder<K, V> {
    private val map = mutableMapOf<K, MutableSet<V>>()

    fun add(key: K, value: V): MapOfSetBuilder<K, V> {
        map.getOrPut(key) { mutableSetOf() }.add(value)
        return this
    }

    fun addAll(key: K, values: Collection<V>): MapOfSetBuilder<K, V> {
        map.getOrPut(key) { mutableSetOf() }.addAll(values)
        return this
    }

    fun remove(key: K, value: V): MapOfSetBuilder<K, V> {
        map[key]?.remove(value)
        if (map[key]?.isEmpty() == true) {
            map.remove(key)
        }
        return this
    }

    fun build(): Map<K, Set<V>> = map.mapValues { it.value.toSet() }
}

inline fun <K, V> mapOfSets(
    init: MapOfSetBuilder<K, V>.() -> Unit
): Map<K, Set<V>> = MapOfSetBuilder<K, V>().apply(init).build()
```

**Usage:**

```kotlin
val relayMap = mapOfSets<String, EventId> {
    add("wss://relay1.com", eventId1)
    add("wss://relay1.com", eventId2)
    add("wss://relay2.com", eventId3)
}
// Result: {"wss://relay1.com": [eventId1, eventId2], "wss://relay2.com": [eventId3]}
```

---

## DSL Principles

### 1. Lambda with Receiver

**Mental model:** Lambda receiver makes `this` refer to builder instance inside lambda.

```kotlin
// Without receiver
fun buildTags(config: (TagArrayBuilder<Event>) -> Unit) {
    val builder = TagArrayBuilder<Event>()
    config(builder)  // Must pass builder explicitly
    builder.build()
}

buildTags { builder ->
    builder.add(...)  // Verbose
}

// With receiver
inline fun buildTags(config: TagArrayBuilder<Event>.() -> Unit) {
    TagArrayBuilder<Event>().apply(config).build()
}

buildTags {
    add(...)  // Clean - 'this' is builder
}
```

### 2. Method Chaining

**Pattern:** Return `this` from mutator methods.

```kotlin
class Builder {
    private var value: String = ""

    fun setValue(v: String): Builder {
        value = v
        return this  // Enable chaining
    }

    fun append(s: String): Builder {
        value += s
        return this
    }

    fun build(): String = value
}

// Usage
val result = Builder()
    .setValue("Hello")
    .append(" ")
    .append("World")
    .build()
```

### 3. Inline for Performance

**Why inline:**
- Eliminates lambda allocation
- Allows `reified` type parameters
- Better for hot paths (frequently called)

```kotlin
// NOT inline - lambda object created each call
fun <T> myDsl(init: Builder<T>.() -> Unit): Result<T> {
    return Builder<T>().apply(init).build()
}

// Inline - lambda code inlined at call site
inline fun <T> myDsl(init: Builder<T>.() -> Unit): Result<T> {
    return Builder<T>().apply(init).build()
}
```

### 4. Type Safety

**Use generics for compile-time safety:**

```kotlin
// Type-safe builder
class EventBuilder<T : Event> {
    fun addTag(tag: Tag<T>): EventBuilder<T> {  // Only accepts tags for this event type
        tags.add(tag)
        return this
    }
}

// Usage
val textNote = EventBuilder<TextNoteEvent>()
    .addTag(TextNoteTag.Subject("Hello"))  // OK
    // .addTag(ChannelTag.Name("test"))     // Compile error!
    .build()
```

---

## Creating Custom DSLs

### Pattern: Simple Builder DSL

```kotlin
class QueryBuilder {
    private val filters = mutableListOf<String>()
    private var limit: Int? = null
    private var offset: Int? = null

    fun filter(field: String, value: String): QueryBuilder {
        filters.add("$field:$value")
        return this
    }

    fun limit(n: Int): QueryBuilder {
        limit = n
        return this
    }

    fun offset(n: Int): QueryBuilder {
        offset = n
        return this
    }

    fun build(): String {
        val parts = mutableListOf<String>()
        if (filters.isNotEmpty()) {
            parts.add(filters.joinToString(" AND "))
        }
        if (limit != null) {
            parts.add("LIMIT $limit")
        }
        if (offset != null) {
            parts.add("OFFSET $offset")
        }
        return parts.joinToString(" ")
    }
}

inline fun query(init: QueryBuilder.() -> Unit): String =
    QueryBuilder().apply(init).build()

// Usage
val sql = query {
    filter("status", "active")
    filter("age", ">18")
    limit(10)
    offset(20)
}
// Result: "status:active AND age:>18 LIMIT 10 OFFSET 20"
```

### Pattern: Nested Builders

```kotlin
class FilterBuilder {
    private val conditions = mutableListOf<String>()

    fun equals(field: String, value: String) {
        conditions.add("$field = '$value'")
    }

    fun greaterThan(field: String, value: Int) {
        conditions.add("$field > $value")
    }

    fun build(): String = conditions.joinToString(" AND ")
}

class QueryBuilder {
    private var filterClause: String = ""
    private var selectClause: String = "*"

    fun select(vararg fields: String): QueryBuilder {
        selectClause = fields.joinToString(", ")
        return this
    }

    fun where(init: FilterBuilder.() -> Unit): QueryBuilder {
        filterClause = FilterBuilder().apply(init).build()
        return this
    }

    fun build(): String {
        return "SELECT $selectClause WHERE $filterClause"
    }
}

inline fun query(init: QueryBuilder.() -> Unit): String =
    QueryBuilder().apply(init).build()

// Usage
val sql = query {
    select("id", "name", "age")
    where {
        equals("status", "active")
        greaterThan("age", 18)
    }
}
// Result: "SELECT id, name, age WHERE status = 'active' AND age > 18"
```

### Pattern: Type-Safe HTML DSL

```kotlin
abstract class Tag(val name: String) {
    private val children = mutableListOf<Tag>()
    private val attributes = mutableMapOf<String, String>()

    fun <T : Tag> tag(tag: T, init: T.() -> Unit): T {
        tag.init()
        children.add(tag)
        return tag
    }

    fun attr(name: String, value: String) {
        attributes[name] = value
    }

    fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent<$name")
        attributes.forEach { (k, v) -> builder.append(" $k=\"$v\"") }
        if (children.isEmpty()) {
            builder.append("/>\n")
        } else {
            builder.append(">\n")
            children.forEach { it.render(builder, "$indent  ") }
            builder.append("$indent</$name>\n")
        }
    }
}

class HTML : Tag("html")
class Head : Tag("head")
class Body : Tag("body")
class Div : Tag("div")
class P : Tag("p")
class A : Tag("a")

fun HTML.head(init: Head.() -> Unit) = tag(Head(), init)
fun HTML.body(init: Body.() -> Unit) = tag(Body(), init)
fun Body.div(init: Div.() -> Unit) = tag(Div(), init)
fun Div.p(init: P.() -> Unit) = tag(P(), init)
fun Div.a(init: A.() -> Unit) = tag(A(), init)

fun html(init: HTML.() -> Unit): HTML = HTML().apply(init)

// Usage
val page = html {
    head {
        // ...
    }
    body {
        div {
            attr("class", "container")
            p {
                attr("id", "intro")
            }
            a {
                attr("href", "https://example.com")
            }
        }
    }
}
```

---

## Best Practices

### ✅ DO

1. **Return `this` for chaining:**
   ```kotlin
   fun add(item: Item): Builder {
       items.add(item)
       return this
   }
   ```

2. **Use `inline` for DSL functions:**
   ```kotlin
   inline fun myDsl(init: Builder.() -> Unit) = Builder().apply(init).build()
   ```

3. **Provide sensible defaults:**
   ```kotlin
   inline fun query(
       init: QueryBuilder.() -> Unit = {}  // Empty lambda as default
   ) = QueryBuilder().apply(init).build()
   ```

4. **Validate in `build()`:**
   ```kotlin
   fun build(): Result {
       require(fields.isNotEmpty()) { "Must specify at least one field" }
       return Result(fields)
   }
   ```

### ❌ DON'T

1. **Forget to return `this`:**
   ```kotlin
   fun add(item: Item) {  // BAD: Can't chain
       items.add(item)
   }
   ```

2. **Mutate after build:**
   ```kotlin
   val builder = Builder()
   builder.add("foo")
   val result = builder.build()
   builder.add("bar")  // BAD: Confusing state
   ```

3. **Expose mutable state:**
   ```kotlin
   class Builder {
       val items = mutableListOf<Item>()  // BAD: Can be mutated externally
   }
   ```

4. **Make DSL functions non-inline unnecessarily:**
   ```kotlin
   fun myDsl(init: Builder.() -> Unit) = ...  // BAD: Lambda allocation overhead
   ```

---

## References

- TagArrayBuilder.kt:23-91
- PrivateTagArrayBuilder.kt
- TlvBuilder.kt
- [Type-Safe Builders | Kotlin Docs](https://kotlinlang.org/docs/type-safe-builders.html)
- [DSLs with Kotlin](https://kt.academy/article/dsl-intro)
