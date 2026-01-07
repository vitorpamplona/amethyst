---
name: kotlin-expert
description: Advanced Kotlin patterns for AmethystMultiplatform. Flow state management (StateFlow/SharedFlow), sealed hierarchies (classes vs interfaces), immutability (@Immutable, data classes), DSL builders (type-safe fluent APIs), inline functions (reified generics, performance). Use when working with: (1) State management patterns (StateFlow/SharedFlow/MutableStateFlow), (2) Sealed classes or sealed interfaces, (3) @Immutable annotations for Compose, (4) DSL builders with lambda receivers, (5) inline/reified functions, (6) Kotlin performance optimization. Complements kotlin-coroutines agent (async patterns) - this skill focuses on Amethyst-specific Kotlin idioms.
---

# Kotlin Expert

Advanced Kotlin patterns for AmethystMultiplatform. Covers Flow state management, sealed hierarchies, immutability, DSL builders, and inline functions with real codebase examples.

## Mental Model

**Kotlin in Amethyst:**

```
State Management (Hot Flows)
    ├── StateFlow<T>           # Single value, always has value, replays to new subscribers
    ├── SharedFlow<T>          # Event stream, configurable replay, multiple subscribers
    └── MutableStateFlow<T>    # Private mutable, public via .asStateFlow()

Type Safety (Sealed Hierarchies)
    ├── sealed class           # State variants with data (AccountState.LoggedIn/LoggedOut)
    └── sealed interface       # Generic result types (SignerResult<T>)

Compose Performance (@Immutable)
    ├── @Immutable             # 173+ event classes - prevents recomposition
    └── data class             # Structural equality, copy(), immutable by convention

DSL Patterns
    ├── Builder classes        # Fluent APIs (TagArrayBuilder)
    ├── Lambda receivers       # inline fun tagArray { ... }
    └── Method chaining        # return this

Performance
    ├── inline fun             # Eliminate lambda overhead
    ├── reified type params    # Runtime type info (OptimizedJsonMapper)
    └── value class            # Zero-cost wrappers (NOT USED yet in Amethyst)
```

**Delegation:**
- **kotlin-coroutines agent**: Deep async (structured concurrency, channels, operators)
- **kotlin-multiplatform skill**: expect/actual, source sets
- **This skill**: Amethyst Kotlin idioms, state patterns, type safety

---

## 1. Flow State Management

### StateFlow: State that Changes

**Mental model:** StateFlow is a "hot" observable state holder. Always has a value, new collectors immediately get current state.

**Amethyst pattern:**

```kotlin
// AccountManager.kt:48-50
class AccountManager {
    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    fun login(key: String) {
        _accountState.value = AccountState.LoggedIn(...)
    }
}
```

**Key principles:**
1. **Private mutable, public immutable**: `_accountState` (MutableStateFlow) private, `accountState` (StateFlow) public
2. **Always has value**: Initial value required (`LoggedOut`)
3. **Single value**: Replays ONE most recent value to new subscribers
4. **Hot**: Stays in memory, all collectors share same instance

**See:** AccountManager.kt:48-50, RelayConnectionManager.kt:49-52

### SharedFlow: Event Streams

**Mental model:** SharedFlow is a "hot" broadcast stream for events. Configurable replay buffer, doesn't require initial value.

**Amethyst pattern:**

```kotlin
// RelayConnectionManager.kt:52-53
val connectedRelays: StateFlow<Set<NormalizedRelayUrl>> = client.connectedRelaysFlow()
val availableRelays: StateFlow<Set<NormalizedRelayUrl>> = client.availableRelaysFlow()
```

**When to use StateFlow vs SharedFlow:**

| Scenario | Use StateFlow | Use SharedFlow |
|----------|---------------|----------------|
| **UI state** | ✅ Current screen data, login status | ❌ |
| **One-time events** | ❌ | ✅ Navigation, snackbars, toasts |
| **Always has value** | ✅ | ❌ Optional |
| **Replay count** | 1 (latest only) | Configurable (0, 1, n) |
| **Backpressure** | Conflates (drops old) | Configurable buffer |

**Best practice:**
```kotlin
// State: Use StateFlow
private val _uiState = MutableStateFlow(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// Events: Use SharedFlow
private val _navigationEvents = MutableSharedFlow<NavEvent>(replay = 0)
val navigationEvents: SharedFlow<NavEvent> = _navigationEvents.asSharedFlow()
```

### Flow Anti-Patterns

❌ **Exposing mutable state:**
```kotlin
val accountState: MutableStateFlow<AccountState>  // BAD: Can be mutated externally
```

✅ **Expose immutable:**
```kotlin
val accountState: StateFlow<AccountState> = _accountState.asStateFlow()  // GOOD
```

---

❌ **SharedFlow for state:**
```kotlin
val loginState = MutableSharedFlow<LoginState>()  // BAD: State might get lost
```

✅ **StateFlow for state:**
```kotlin
val loginState = MutableStateFlow(LoginState.LoggedOut)  // GOOD: Always has value
```

**See:** `references/flow-patterns.md` for comprehensive examples.

---

## 2. Sealed Hierarchies

### Sealed Classes: State Variants

**Mental model:** Sealed classes represent a closed set of variants that share common data/behavior.

**Amethyst pattern:**

```kotlin
// AccountManager.kt:36-46
sealed class AccountState {
    data object LoggedOut : AccountState()

    data class LoggedIn(
        val signer: NostrSigner,
        val pubKeyHex: String,
        val npub: String,
        val nsec: String?,
        val isReadOnly: Boolean
    ) : AccountState()
}

// Usage
when (state) {
    is AccountState.LoggedOut -> showLogin()
    is AccountState.LoggedIn -> showFeed(state.pubKeyHex)
}  // Exhaustive - compiler enforces all cases
```

**Key principles:**
1. **Closed hierarchy**: All subclasses known at compile-time
2. **Exhaustive when**: Compiler ensures all cases handled
3. **Shared data**: Sealed class can hold common properties
4. **Single inheritance**: Subclass can't extend another class

**When to use:**
- Modeling UI states (Loading, Success, Error)
- Login states (LoggedOut, LoggedIn)
- Result types with different data per variant

### Sealed Interfaces: Generic Result Types

**Mental model:** Sealed interfaces for contracts with multiple implementations that need generics or multiple inheritance.

**Amethyst pattern:**

```kotlin
// SignerResult.kt:25-46
sealed interface SignerResult<T : IResult> {
    sealed interface RequestAddressed<T : IResult> : SignerResult<T> {
        class Successful<T : IResult>(val result: T) : RequestAddressed<T>
        class Rejected<T : IResult> : RequestAddressed<T>
        class TimedOut<T : IResult> : RequestAddressed<T>
        class ReceivedButCouldNotPerform<T : IResult>(
            val message: String?
        ) : RequestAddressed<T>
    }
}

// Usage with generics
fun handleResult(result: SignerResult<SignResult>) {
    when (result) {
        is SignerResult.RequestAddressed.Successful -> processEvent(result.result.event)
        is SignerResult.RequestAddressed.Rejected -> showRejected()
        is SignerResult.RequestAddressed.TimedOut -> showTimeout()
    }
}
```

**Key principles:**
1. **Multiple inheritance**: Subtype can implement other interfaces
2. **Variance**: Supports `out`/`in` modifiers for generics
3. **No constructor**: Can't hold state directly (subtypes can)
4. **Nested hierarchies**: Can create sub-sealed hierarchies

### Sealed Class vs Sealed Interface

| Feature | Sealed Class | Sealed Interface |
|---------|--------------|------------------|
| **Constructor** | ✅ Can hold common state | ❌ No constructor |
| **Inheritance** | ❌ Single parent only | ✅ Multiple interfaces |
| **Generics** | ❌ No variance | ✅ Covariance/contravariance |
| **Use case** | State variants | Result types, contracts |

**Decision tree:**

```
Need to hold common data in base?
    YES → sealed class
    NO → sealed interface

Need generics with variance (out/in)?
    YES → sealed interface
    NO → Either works

Subtypes need multiple inheritance?
    YES → sealed interface
    NO → Either works
```

**Amethyst examples:**
- `sealed class AccountState` - state variants with different data
- `sealed interface SignerResult<T>` - generic result types with variance

**See:** `references/sealed-class-catalog.md` for all sealed types in quartz.

---

## 3. Immutability & Compose Performance

### @Immutable Annotation

**Mental model:** @Immutable tells Compose "this value never changes after construction." Compose can skip recomposition if @Immutable object reference doesn't change.

**Amethyst pattern:**

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
    // All properties immutable (val), no mutable state
}
```

**Key principles:**
1. **All properties immutable**: Only `val`, never `var`
2. **No mutable collections**: Use `ImmutableList`, `Array`, not `MutableList`
3. **Deep immutability**: Nested objects also immutable
4. **Compose optimization**: Skips recomposition if reference equals

**Why it matters:**

```kotlin
// Without @Immutable
@Composable
fun NoteCard(note: TextNoteEvent) {  // Recomposes every time parent recomposes
    Text(note.content)
}

// With @Immutable
@Composable
fun NoteCard(note: TextNoteEvent) {  // Only recomposes if note reference changes
    Text(note.content)
}
```

**173+ @Immutable classes** in quartz - all events immutable for Compose performance.

### Data Classes & Immutability

**Pattern:**

```kotlin
@Immutable
data class RelayStatus(
    val url: NormalizedRelayUrl,
    val connected: Boolean,
    val error: String? = null
) {
    // Implicit: equals(), hashCode(), copy(), toString()
}

// Usage
val oldStatus = RelayStatus(url, connected = false)
val newStatus = oldStatus.copy(connected = true)  // Immutable update
```

**Key principles:**
1. **Structural equality**: `equals()` compares properties, not reference
2. **copy()**: Create modified copies without mutating
3. **All properties in constructor**: For proper `equals()`/`hashCode()`
4. **Prefer val**: Make properties immutable

### kotlinx.collections.immutable

**Pattern:**

```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// Instead of List (which could be mutable internally)
val relays: ImmutableList<String> = persistentListOf("wss://relay1.com", "wss://relay2.com")

// Add returns new instance
val updated = relays.add("wss://relay3.com")  // relays unchanged, updated has 3 items
```

**When to use:**
- Compose state that needs collection
- Publicly exposed collections
- Shared state across threads

**See:** `references/immutability-patterns.md`

---

## 4. DSL Builders

### Type-Safe Fluent APIs

**Mental model:** DSL (Domain-Specific Language) builders use lambda receivers and method chaining to create readable, type-safe APIs.

**Amethyst pattern:**

```kotlin
// TagArrayBuilder.kt:23-90
class TagArrayBuilder<T : IEvent> {
    private val tagList = mutableMapOf<String, MutableList<Tag>>()

    fun add(tag: Array<String>): TagArrayBuilder<T> {
        if (tag.isEmpty() || tag[0].isEmpty()) return this
        tagList.getOrPut(tag[0], ::mutableListOf).add(tag)
        return this  // Method chaining
    }

    fun remove(tagName: String): TagArrayBuilder<T> {
        tagList.remove(tagName)
        return this  // Method chaining
    }

    fun build() = tagList.flatMap { it.value }.toTypedArray()
}

// Inline function with lambda receiver (line 90)
inline fun <T : Event> tagArray(initializer: TagArrayBuilder<T>.() -> Unit = {}): TagArray =
    TagArrayBuilder<T>().apply(initializer).build()
```

**Usage:**

```kotlin
val tags = tagArray<TextNoteEvent> {
    add(arrayOf("e", eventId, relay, "reply"))
    add(arrayOf("p", pubkey))
    remove("a")  // Remove address tags
}
```

**Key patterns:**
1. **Method chaining**: Return `this` from mutator methods
2. **Lambda receiver**: `TagArrayBuilder<T>.() -> Unit` - lambda has `this: TagArrayBuilder<T>`
3. **inline function**: Eliminates lambda overhead
4. **apply()**: Executes lambda with receiver, returns receiver

### DSL Pattern Template

```kotlin
class MyBuilder {
    private val items = mutableListOf<Item>()

    fun add(item: Item): MyBuilder {
        items.add(item)
        return this
    }

    fun build(): Result = Result(items.toList())
}

inline fun myDsl(init: MyBuilder.() -> Unit): Result =
    MyBuilder().apply(init).build()

// Usage
val result = myDsl {
    add(Item("foo"))
    add(Item("bar"))
}
```

**Why inline?**
- Eliminates lambda object allocation
- Enables `reified` type parameters
- Better performance for frequently-called DSLs

**See:** `references/dsl-builder-examples.md` for more patterns.

---

## 5. Inline Functions & reified

### inline fun: Eliminate Overhead

**Mental model:** `inline` copies function body to call site. No lambda object created, direct code insertion.

**Pattern:**

```kotlin
// Without inline
fun <T> measureTime(block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()  // Lambda object allocated
    println("Time: ${System.currentTimeMillis() - start}ms")
    return result
}

// With inline
inline fun <T> measureTime(block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()  // No allocation, code inlined
    println("Time: ${System.currentTimeMillis() - start}ms")
    return result
}
```

**Benefits:**
1. **Zero overhead**: No lambda object allocation
2. **Non-local returns**: Can `return` from outer function inside lambda
3. **reified enabled**: Access to type parameter at runtime

### reified: Runtime Type Access

**Mental model:** `reified` makes generic type `T` available at runtime. Only works with `inline`.

**Amethyst pattern:**

```kotlin
// OptimizedJsonMapper.kt:48
expect object OptimizedJsonMapper {
    inline fun <reified T : OptimizedSerializable> fromJsonTo(json: String): T
}

// Usage
val event: TextNoteEvent = OptimizedJsonMapper.fromJsonTo(jsonString)
// Compiler inlines and passes TextNoteEvent::class info
```

**Without reified:**

```kotlin
// Would need to pass class explicitly
fun <T> fromJson(json: String, clazz: KClass<T>): T {
    return when (clazz) {
        TextNoteEvent::class -> parseTextNote(json) as T
        // ...
    }
}

val event = fromJson(json, TextNoteEvent::class)  // Verbose
```

**With reified:**

```kotlin
inline fun <reified T> fromJson(json: String): T {
    return when (T::class) {  // Can access T::class!
        TextNoteEvent::class -> parseTextNote(json) as T
        // ...
    }
}

val event = fromJson<TextNoteEvent>(json)  // Clean
```

### noinline & crossinline

**noinline**: Prevent specific lambda from being inlined

```kotlin
inline fun foo(
    inlined: () -> Unit,
    noinline notInlined: () -> Unit  // Can be stored, passed around
) {
    inlined()
    someFunction(notInlined)  // Can pass to non-inline function
}
```

**crossinline**: Lambda can't do non-local returns

```kotlin
inline fun foo(crossinline block: () -> Unit) {
    launch {
        block()  // OK: crossinline allows lambda in different context
    }
}
```

---

## 6. Value Classes (Opportunity)

**Mental model:** `value class` is a compile-time wrapper with zero runtime overhead. Single property, no boxing.

**Not currently used in Amethyst** - potential optimization.

**Pattern:**

```kotlin
@JvmInline
value class EventId(val hex: String)

@JvmInline
value class PubKey(val hex: String)

// Type safety without runtime cost
fun fetchEvent(eventId: EventId): Event {
    // eventId.hex accessed without wrapper object
}

val id = EventId("abc123")
fetchEvent(id)  // Type safe
// fetchEvent(PubKey("xyz"))  // Compile error!
```

**When to use:**
- Type safety for primitives (IDs, hex strings, timestamps)
- High-frequency allocations (event processing)
- Clear domain types without overhead

**Restrictions:**
- Single property only
- Must be `val`
- Can't have `init` block with logic
- Inline at compile-time, may box in some cases

**Amethyst opportunity:**

```kotlin
// Current (String everywhere, no type safety)
fun fetchEvent(id: String): Event  // Could pass wrong string

// With value class
@JvmInline value class EventId(val hex: String)
@JvmInline value class PubKeyHex(val hex: String)
@JvmInline value class Bech32(val encoded: String)

fun fetchEvent(id: EventId): Event  // Type safe, zero cost
```

---

## Common Patterns

### Pattern: StateFlow State Management

```kotlin
class MyViewModel {
    private val _state = MutableStateFlow(State.Initial)
    val state: StateFlow<State> = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.value = State.Loading
            val result = repository.getData()
            _state.value = when (result) {
                is Success -> State.Success(result.data)
                is Error -> State.Error(result.message)
            }
        }
    }
}

sealed class State {
    data object Initial : State()
    data object Loading : State()
    data class Success(val data: List<Item>) : State()
    data class Error(val message: String) : State()
}
```

### Pattern: Sealed Result with Generics

```kotlin
sealed interface Result<out T> {
    data class Success<T>(val value: T) : Result<T>
    data class Error(val exception: Exception) : Result<Nothing>
    data object Loading : Result<Nothing>
}

// Use with variance
fun <T> fetchData(): Result<T> = ...

val userResult: Result<User> = fetchData()
val itemResult: Result<List<Item>> = fetchData()
```

### Pattern: Immutable Event Builder

```kotlin
@Immutable
data class Event(
    val id: String,
    val kind: Int,
    val content: String,
    val tags: ImmutableList<Tag>
) {
    companion object {
        fun builder() = EventBuilder()
    }
}

class EventBuilder {
    private var id: String = ""
    private var kind: Int = 1
    private var content: String = ""
    private val tags = mutableListOf<Tag>()

    fun id(value: String) = apply { id = value }
    fun kind(value: Int) = apply { kind = value }
    fun content(value: String) = apply { content = value }
    fun tag(tag: Tag) = apply { tags.add(tag) }

    fun build() = Event(id, kind, content, tags.toImmutableList())
}

// Usage
val event = Event.builder()
    .id("abc")
    .kind(1)
    .content("Hello")
    .tag(Tag.P("pubkey"))
    .build()
```

---

## Delegation Guide

**When to delegate:**

| Topic | Delegate To | This Skill Covers |
|-------|-------------|-------------------|
| Structured concurrency, channels | kotlin-coroutines agent | Flow state patterns only |
| expect/actual, source sets | kotlin-multiplatform skill | Platform-agnostic Kotlin |
| General Compose patterns | compose-expert skill | @Immutable for performance |
| Build configuration | gradle-expert skill | - |

**Ask kotlin-coroutines agent for:**
- Advanced Flow operators (flatMapLatest, combine, zip)
- Channel patterns
- Structured concurrency (supervisorScope, coroutineScope)
- Error handling in coroutines

**This skill teaches:**
- StateFlow/SharedFlow state management
- Sealed hierarchies
- @Immutable for Compose
- DSL builders
- Inline/reified patterns

---

## Anti-Patterns

❌ **Mutable public state:**
```kotlin
val accountState: MutableStateFlow<AccountState>  // BAD
```

✅ **Immutable public interface:**
```kotlin
val accountState: StateFlow<AccountState> = _accountState.asStateFlow()
```

---

❌ **Sealed class for generic results:**
```kotlin
sealed class Result<T> {  // BAD: Can't use variance
    data class Success<T>(val value: T) : Result<T>()
}
```

✅ **Sealed interface for generics:**
```kotlin
sealed interface Result<out T> {  // GOOD: Covariance
    data class Success<T>(val value: T) : Result<T>
}
```

---

❌ **Mutable properties in @Immutable class:**
```kotlin
@Immutable
data class Event(
    var content: String  // BAD: var breaks immutability
)
```

✅ **All val:**
```kotlin
@Immutable
data class Event(
    val content: String
)
```

---

❌ **Passing class explicitly when reified available:**
```kotlin
inline fun <T> parse(json: String, clazz: KClass<T>): T  // BAD
```

✅ **Use reified:**
```kotlin
inline fun <reified T> parse(json: String): T  // GOOD
```

---

## Quick Reference

### Flow Decision Tree

```
Need to expose state?
    YES → StateFlow (always has value, single latest)
    NO → Need events? → SharedFlow (optional replay, broadcast)

Need to mutate?
    Internal only → MutableStateFlow (private)
    Expose publicly → StateFlow via .asStateFlow()
```

### Sealed Decision Tree

```
Need common data in base type?
    YES → sealed class
    NO → sealed interface

Need generics with variance?
    YES → sealed interface
    NO → Either works

Need multiple inheritance?
    YES → sealed interface
    NO → Either works
```

### Inline Decision Tree

```
Passing lambda to function?
    Called frequently? → inline (performance)
    Need reified? → inline (required)
    Need to store/pass lambda? → regular fun (can't inline)
```

---

## Resources

### Official Docs
- [StateFlow and SharedFlow | Android Developers](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Sealed Classes | Kotlin Docs](https://kotlinlang.org/docs/sealed-classes.html)
- [Inline Functions | Kotlin Docs](https://kotlinlang.org/docs/inline-functions.html)

### Bundled References
- `references/flow-patterns.md` - StateFlow/SharedFlow examples from AccountManager, RelayManager
- `references/sealed-class-catalog.md` - All sealed types in quartz
- `references/dsl-builder-examples.md` - TagArrayBuilder, other DSL patterns
- `references/immutability-patterns.md` - @Immutable usage, data classes, collections

### Codebase Examples
- AccountManager.kt:36-50 - sealed class AccountState, StateFlow pattern
- RelayConnectionManager.kt:44-52 - StateFlow state management
- SignerResult.kt:25-46 - sealed interface with generics
- TextNoteEvent.kt:51-63 - @Immutable event class
- TagArrayBuilder.kt:23-90 - DSL builder pattern, inline function
- OptimizedJsonMapper.kt:48 - inline fun with reified

---

**Version:** 1.0.0
**Last Updated:** 2025-12-30
**Codebase Reference:** AmethystMultiplatform commit 258c4e011
