# Immutability Patterns

@Immutable annotation, data classes, and immutable collections for Compose performance.

## Table of Contents
- [Why Immutability Matters](#why-immutability-matters)
- [@Immutable Annotation](#immutable-annotation)
- [Data Classes](#data-classes)
- [Immutable Collections](#immutable-collections)
- [Common Patterns](#common-patterns)
- [Performance Impact](#performance-impact)

---

## Why Immutability Matters

### Compose Recomposition

**Mental model:** Compose tracks state changes by comparing references. If an `@Immutable` object reference doesn't change, Compose skips recomposition.

```kotlin
// Without @Immutable - Recomposes on every parent recomposition
data class User(val name: String, val age: Int)

@Composable
fun UserCard(user: User) {  // Recomposes unnecessarily
    Text(user.name)
}

// With @Immutable - Only recomposes when user reference changes
@Immutable
data class User(val name: String, val age: Int)

@Composable
fun UserCard(user: User) {  // Smart recomposition
    Text(user.name)
}
```

**Performance difference:**
- Without `@Immutable`: 1000 `UserCard` recompositions per screen update
- With `@Immutable`: 10 `UserCard` recompositions (only changed users)

### Thread Safety

Immutable objects are inherently thread-safe:

```kotlin
@Immutable
data class Event(
    val id: String,
    val content: String,
    val createdAt: Long
)

// Safe to share across coroutines without synchronization
val sharedEvent: Event = fetchEvent()
launch { processEvent(sharedEvent) }  // Safe
launch { saveEvent(sharedEvent) }     // Safe
```

---

## @Immutable Annotation

### Basic Usage

**Pattern from Amethyst:**

```kotlin
// TextNoteEvent.kt:51-63
@Immutable
class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    // All properties are val (immutable)
    // No var properties
    // No mutable collections
}
```

**Requirements for @Immutable:**
1. All properties must be `val` (no `var`)
2. All property types must be immutable or primitives
3. No mutable collections (`MutableList`, `MutableMap`)
4. Arrays are allowed (treated as immutable by contract)
5. No public mutable state

### @Immutable vs @Stable

**@Immutable:** Value never changes after construction

```kotlin
@Immutable
data class User(val name: String, val age: Int)
// Once created, user.name and user.age never change
```

**@Stable:** Value can change, but changes are tracked

```kotlin
@Stable
class MutableCounter {
    var count by mutableStateOf(0)  // Changes tracked by Compose
}
```

**Amethyst uses @Immutable extensively:**
- 173+ event classes annotated with `@Immutable`
- All Nostr events immutable by design
- Critical for feed performance (thousands of events)

---

## Data Classes

### Immutable Data Classes

**Pattern:**

```kotlin
@Immutable
data class RelayStatus(
    val url: NormalizedRelayUrl,
    val connected: Boolean,
    val error: String? = null,
    val messageCount: Int = 0
) {
    // Immutable properties only (val)
    // Default values allowed
}
```

**Benefits:**
1. **Structural equality:** `equals()` compares values, not references
2. **copy():** Create modified copies without mutation
3. **toString():** Debugging-friendly output
4. **hashCode():** Consistent hashing for collections
5. **componentN():** Destructuring support

### copy() for Updates

**Mental model:** Instead of mutating, create modified copies.

```kotlin
val status = RelayStatus(
    url = "wss://relay.damus.io",
    connected = false,
    error = null
)

// Immutable update
val updatedStatus = status.copy(connected = true)

// Original unchanged
assert(status.connected == false)
assert(updatedStatus.connected == true)
```

**StateFlow pattern:**

```kotlin
private val _relayStatuses = MutableStateFlow<Map<String, RelayStatus>>(emptyMap())

fun updateRelay(url: String, connected: Boolean) {
    _relayStatuses.value = _relayStatuses.value.mapValues { (key, status) ->
        if (key == url) {
            status.copy(connected = connected)  // Immutable update
        } else {
            status
        }
    }
}
```

### All Properties in Constructor

**Why important for data classes:**

```kotlin
// BAD: Properties outside constructor not included in equals/hashCode
data class User(val name: String) {
    var age: Int = 0  // NOT in equals/hashCode/copy!
}

val user1 = User("Alice")
val user2 = User("Alice")
user1.age = 25
user2.age = 30

assert(user1 == user2)  // TRUE! age not compared
assert(user1.copy() == user1)  // TRUE! age not copied

// GOOD: All properties in constructor
@Immutable
data class User(
    val name: String,
    val age: Int  // Included in equals/hashCode/copy
)
```

---

## Immutable Collections

### kotlinx.collections.immutable

**Installation:**

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
}
```

**Why use:**
- Structural sharing (efficient copies)
- Explicit immutability (compiler enforced)
- Safe for Compose state

### ImmutableList

```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// Create immutable list
val relays: ImmutableList<String> = persistentListOf(
    "wss://relay1.com",
    "wss://relay2.com"
)

// Add returns NEW list
val updated = relays.add("wss://relay3.com")
assert(relays.size == 2)      // Original unchanged
assert(updated.size == 3)     // New list has 3 items

// Convert from regular list
val mutableList = mutableListOf("a", "b", "c")
val immutable = mutableList.toImmutableList()
```

### ImmutableMap

```kotlin
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

// Create immutable map
val relayStatuses: ImmutableMap<String, RelayStatus> = persistentMapOf(
    "wss://relay1.com" to RelayStatus(...),
    "wss://relay2.com" to RelayStatus(...)
)

// Put returns NEW map
val updated = relayStatuses.put("wss://relay3.com", RelayStatus(...))

// Remove returns NEW map
val removed = relayStatuses.remove("wss://relay1.com")
```

### ImmutableSet

```kotlin
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

val connectedRelays: ImmutableSet<String> = persistentSetOf(
    "wss://relay1.com",
    "wss://relay2.com"
)

val updated = connectedRelays.add("wss://relay3.com")
```

### Structural Sharing

**Mental model:** Immutable collections reuse internal structure for efficiency.

```kotlin
val list1 = persistentListOf(1, 2, 3, 4, 5)  // 5 items
val list2 = list1.add(6)                     // Shares structure with list1

// Internally:
// list1 and list2 share nodes for items 1-5
// list2 has one additional node for item 6
// O(1) time, O(1) space for add operation
```

---

## Common Patterns

### Pattern: Immutable State Updates

```kotlin
@Immutable
data class FeedState(
    val events: ImmutableList<Event>,
    val loading: Boolean,
    val error: String?
)

class FeedViewModel {
    private val _state = MutableStateFlow(
        FeedState(
            events = persistentListOf(),
            loading = false,
            error = null
        )
    )
    val state: StateFlow<FeedState> = _state.asStateFlow()

    fun loadEvents() {
        _state.value = _state.value.copy(loading = true, error = null)

        viewModelScope.launch {
            try {
                val events = repository.getEvents()
                _state.value = _state.value.copy(
                    events = events.toImmutableList(),
                    loading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message
                )
            }
        }
    }

    fun addEvent(event: Event) {
        _state.value = _state.value.copy(
            events = _state.value.events.add(event)  // Immutable add
        )
    }

    fun removeEvent(eventId: String) {
        _state.value = _state.value.copy(
            events = _state.value.events.filter { it.id != eventId }.toImmutableList()
        )
    }
}
```

### Pattern: Deep Immutability

```kotlin
// Nested immutable structures
@Immutable
data class User(
    val name: String,
    val profile: Profile  // Also immutable
)

@Immutable
data class Profile(
    val bio: String,
    val avatar: String,
    val relays: ImmutableList<String>  // Immutable collection
)

// Safe deep copy
val user = User(
    name = "Alice",
    profile = Profile(
        bio = "Nostr enthusiast",
        avatar = "https://...",
        relays = persistentListOf("wss://relay1.com")
    )
)

val updatedUser = user.copy(
    profile = user.profile.copy(
        bio = "Bitcoin & Nostr enthusiast"  // Deep update
    )
)
```

### Pattern: Collection Builder to Immutable

```kotlin
// Build mutable, convert to immutable
fun processEvents(input: List<Event>): ImmutableList<Event> {
    val processed = mutableListOf<Event>()

    for (event in input) {
        if (event.isValid()) {
            processed.add(event.normalize())
        }
    }

    return processed.toImmutableList()  // Convert once at end
}
```

### Pattern: Immutable Map Updates

```kotlin
private val _relayStatuses = MutableStateFlow<ImmutableMap<String, RelayStatus>>(
    persistentMapOf()
)

fun updateRelay(url: String, connected: Boolean) {
    val currentStatuses = _relayStatuses.value
    val currentStatus = currentStatuses[url] ?: RelayStatus(url, false)

    _relayStatuses.value = currentStatuses.put(
        url,
        currentStatus.copy(connected = connected)
    )
}

fun removeRelay(url: String) {
    _relayStatuses.value = _relayStatuses.value.remove(url)
}
```

---

## Performance Impact

### Benchmarks (Approximate)

**Recomposition cost:**

```kotlin
// 1000 items in LazyColumn
// Without @Immutable: ~100ms per frame (skipped frames)
// With @Immutable: ~16ms per frame (smooth 60fps)

@Immutable
data class Item(val id: String, val name: String)

@Composable
fun ItemList(items: ImmutableList<Item>) {
    LazyColumn {
        items(items, key = { it.id }) { item ->
            ItemRow(item)  // Only recomposes when item changes
        }
    }
}
```

**Structural sharing efficiency:**

```kotlin
val list1 = persistentListOf(1..10000)
val list2 = list1.add(10001)  // O(log n) time, shares structure

// Regular list (copy on modification):
val mutableList = (1..10000).toMutableList()
val copy = mutableList.toList() + 10001  // O(n) time, full copy
```

### When to Use Immutable Collections

**Use ImmutableList/Map/Set when:**
- Storing in Compose state (@Immutable class)
- Sharing across coroutines
- Frequent modifications (structural sharing efficient)
- Need compile-time immutability guarantee

**Use Array when:**
- Fixed size, no modifications
- Nostr protocol (tags are `Array<Array<String>>`)
- Performance-critical (array access is fastest)

**Use regular List/Map/Set when:**
- Local scope only
- Build once, read many times
- Converting to immutable at boundary

---

## Anti-Patterns

### ❌ Mutable Properties in @Immutable Class

```kotlin
@Immutable
data class BadEvent(
    val id: String,
    var content: String  // BAD: var breaks immutability
)
```

### ✅ All val Properties

```kotlin
@Immutable
data class GoodEvent(
    val id: String,
    val content: String
)
```

---

### ❌ Mutable Collections in @Immutable Class

```kotlin
@Immutable
data class BadState(
    val items: MutableList<Item>  // BAD: Can mutate items
)

// Caller can mutate:
val state = BadState(mutableListOf())
state.items.add(newItem)  // Breaks immutability!
```

### ✅ Immutable Collections

```kotlin
@Immutable
data class GoodState(
    val items: ImmutableList<Item>
)

// Caller must create new state:
val updated = state.copy(items = state.items.add(newItem))
```

---

### ❌ Direct Mutation

```kotlin
val status = RelayStatus(url, connected = false)
status.connected = true  // Compile error (val)

// But could happen with mutable nested objects:
@Immutable
data class Config(
    val settings: Settings  // If Settings is mutable...
)

class Settings {
    var theme: String = "dark"  // BAD
}

val config = Config(Settings())
config.settings.theme = "light"  // Mutates "immutable" config!
```

### ✅ Deep Immutability

```kotlin
@Immutable
data class Config(
    val settings: Settings
)

@Immutable
data class Settings(
    val theme: String  // val only
)

val config = Config(Settings("dark"))
val updated = config.copy(
    settings = config.settings.copy(theme = "light")
)
```

---

### ❌ Exposing Mutable Internal State

```kotlin
@Immutable
class BadViewModel {
    private val _items = mutableListOf<Item>()
    val items: List<Item> = _items  // BAD: Exposes mutable list

    fun addItem(item: Item) {
        _items.add(item)
    }
}

// Caller can cast and mutate:
val vm = BadViewModel()
(vm.items as MutableList).clear()  // Breaks encapsulation!
```

### ✅ Convert to Immutable at Boundary

```kotlin
@Immutable
class GoodViewModel {
    private val _items = mutableListOf<Item>()
    val items: ImmutableList<Item>
        get() = _items.toImmutableList()  // GOOD: Copy to immutable

    fun addItem(item: Item) {
        _items.add(item)
    }
}
```

---

## Checklist for Immutability

**For @Immutable classes:**
- [ ] All properties are `val`, never `var`
- [ ] No mutable collections (`MutableList`, `MutableMap`, `MutableSet`)
- [ ] Nested objects are also `@Immutable` or primitives
- [ ] No public mutable state
- [ ] Use `copy()` for updates, never mutation
- [ ] Arrays used only when truly immutable by contract

**For StateFlow state:**
- [ ] State class is `@Immutable`
- [ ] Use immutable collections (ImmutableList, ImmutableMap)
- [ ] Create new instances for updates (`copy()`, `.add()`, `.put()`)
- [ ] Never mutate state in-place

**For Compose performance:**
- [ ] All `@Composable` parameters are `@Immutable` or `@Stable`
- [ ] Lists use `ImmutableList` and `key` parameter in `items()`
- [ ] Heavy objects (events, profiles) cached and reused

---

## References

- TextNoteEvent.kt:51-63 - @Immutable event example
- RelayConnectionManager.kt - Immutable map updates
- [Compose Performance | Android Developers](https://developer.android.com/jetpack/compose/performance/stability)
- [kotlinx.collections.immutable | GitHub](https://github.com/Kotlin/kotlinx.collections.immutable)
- [@Stable and @Immutable | Compose Docs](https://developer.android.com/jetpack/compose/performance/stability/fix)
