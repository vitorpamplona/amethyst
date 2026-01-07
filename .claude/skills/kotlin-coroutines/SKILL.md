---
name: kotlin-coroutines
description: Advanced Kotlin coroutines patterns for AmethystMultiplatform. Use when working with: (1) Structured concurrency (supervisorScope, coroutineScope), (2) Advanced Flow operators (flatMapLatest, combine, merge, shareIn, stateIn), (3) Channels and callbackFlow, (4) Dispatcher management and context switching, (5) Exception handling (CoroutineExceptionHandler, SupervisorJob), (6) Testing async code (runTest, Turbine), (7) Nostr relay connection pools and subscriptions, (8) Backpressure handling in event streams. Delegates to kotlin-expert for basic StateFlow/SharedFlow patterns. Complements nostr-expert for relay communication.
---

# Kotlin Coroutines - Advanced Async Patterns

Expert guidance for complex async operations in Amethyst: relay pools, event streams, structured concurrency, and testing.

## Mental Model

```
Async Architecture in Amethyst:

Relay Pool (supervisorScope)
    ├── Relay 1 (launch) → callbackFlow → Events
    ├── Relay 2 (launch) → callbackFlow → Events
    └── Relay 3 (launch) → callbackFlow → Events
            ↓
    merge() → distinctBy(id) → shareIn
            ↓
    Multiple Collectors (ViewModels, Services)
```

**Key principles:**
- **supervisorScope** - Children fail independently
- **callbackFlow** - Bridge callbacks to Flow
- **shareIn/stateIn** - Hot flows from cold
- **Backpressure** - buffer(), conflate(), DROP_OLDEST

## When to Use This Skill

Use for **advanced** async patterns:
- Multi-relay subscriptions with supervisorScope
- Complex Flow operators (flatMapLatest, combine, merge)
- callbackFlow for Android callbacks (connectivity, location)
- Backpressure handling in high-frequency streams
- Exception handling with CoroutineExceptionHandler
- Testing coroutines with runTest and Turbine

**Delegate to kotlin-expert for:**
- Basic StateFlow/SharedFlow patterns
- Simple viewModelScope.launch
- MutableStateFlow → asStateFlow()

## Core Patterns

### Pattern: callbackFlow for Relay Subscriptions

```kotlin
// Real pattern from NostrClientStaticReqAsStateFlow.kt
fun INostrClient.reqAsFlow(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
): Flow<List<Event>> = callbackFlow {
    val subId = RandomInstance.randomChars(10)
    var hasBeenLive = false
    val eventIds = mutableSetOf<HexKey>()
    var currentEvents = listOf<Event>()

    val listener = object : IRequestListener {
        override fun onEvent(event: Event, ...) {
            if (event.id !in eventIds) {
                currentEvents = if (hasBeenLive) {
                    // After EOSE: prepend
                    listOf(event) + currentEvents
                } else {
                    // Before EOSE: append
                    currentEvents + event
                }
                eventIds.add(event.id)
                trySend(currentEvents)
            }
        }

        override fun onEose(...) {
            hasBeenLive = true
        }
    }

    openReqSubscription(subId, mapOf(relay to filters), listener)

    awaitClose { close(subId) }
}
```

**Key techniques:**
1. Deduplication with Set
2. EOSE handling (append → prepend strategy)
3. trySend (non-blocking from callback)
4. awaitClose for cleanup

### Pattern: Structured Concurrency for Relays

```kotlin
suspend fun connectToRelays(relays: List<Relay>) = supervisorScope {
    relays.forEach { relay ->
        launch {
            try {
                relay.connect()
                relay.subscribe(filters).collect { event ->
                    eventChannel.send(event)
                }
            } catch (e: IOException) {
                Log.e("Relay", "Connection failed: ${relay.url}", e)
                // Other relays continue
            }
        }
    }
}
```

**Why supervisorScope:**
- One relay failure doesn't cancel others
- All cancelled together when scope cancelled
- Proper cleanup guaranteed

### Pattern: Exception Handling for Services

```kotlin
// Real pattern from PushNotificationReceiverService.kt
class MyService : Service() {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("Service", "Caught: ${throwable.message}", throwable)
    }

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + exceptionHandler
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

**Pattern benefits:**
- SupervisorJob: children fail independently
- ExceptionHandler: log instead of crash
- Scoped lifecycle: cancel all on destroy

### Pattern: Network Connectivity as Flow

```kotlin
// Real pattern from ConnectivityFlow.kt
val status = callbackFlow {
    val networkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(ConnectivityStatus.Active(...))
        }
        override fun onLost(network: Network) {
            trySend(ConnectivityStatus.Off)
        }
    }

    connectivityManager.registerCallback(networkCallback)

    // Initial state
    activeNetwork?.let { trySend(ConnectivityStatus.Active(...)) }

    awaitClose {
        connectivityManager.unregisterCallback(networkCallback)
    }
}
    .distinctUntilChanged()
    .debounce(200)  // Stabilize flapping
    .flowOn(Dispatchers.IO)
```

**Key patterns:**
1. Emit initial state immediately
2. Register callback in flow body
3. Cleanup in awaitClose
4. Stabilize with debounce + distinctUntilChanged

### Pattern: Merge Events from Multiple Relays

```kotlin
fun observeFromRelays(
    relays: List<NormalizedRelayUrl>,
    filters: List<Filter>
): Flow<Event> =
    relays.map { relay ->
        client.reqAsFlow(relay, filters)
            .flatMapConcat { it.asFlow() }
    }.merge()
    .distinctBy { it.id }
```

**Flow:**
- Each relay: `Flow<List<Event>>`
- flatMapConcat: flatten to `Flow<Event>`
- merge(): combine all relays
- distinctBy: deduplicate across relays

## Advanced Operators

For comprehensive coverage of Flow operators:
- **flatMapLatest, combine, zip, merge** → See [advanced-flow-operators.md](references/advanced-flow-operators.md)
- **shareIn, stateIn** → Conversion to hot flows
- **buffer, conflate** → Backpressure strategies
- **debounce, sample** → Rate limiting

### Quick Reference

| Operator | Use Case | Example |
|----------|----------|---------|
| **flatMapLatest** | Cancel previous, switch to new | Search (cancel old query) |
| **combine** | Latest from ALL flows | combine(account, settings, connectivity) |
| **merge** | Single stream from multiple | merge(relay1, relay2, relay3) |
| **shareIn** | Multiple collectors, single upstream | Share expensive computation |
| **stateIn** | StateFlow from Flow | ViewModel state |
| **buffer(DROP_OLDEST)** | High-frequency streams | Real-time event feed |
| **conflate** | Latest only | UI updates |
| **debounce** | Wait for quiet period | Search input |

## Nostr Relay Patterns

For complete relay-specific patterns:
→ See [relay-patterns.md](references/relay-patterns.md)

Covers:
- Multi-relay subscription management
- Connection lifecycle and reconnection
- Event deduplication strategies
- Backpressure for high-frequency events
- EOSE handling patterns

## Testing

For comprehensive testing patterns:
→ See [testing-coroutines.md](references/testing-coroutines.md)

**Quick testing pattern:**

```kotlin
@Test
fun `relay subscription receives events`() = runTest {
    val client = FakeNostrClient()

    client.reqAsFlow(relay, filters).test {
        assertEquals(emptyList(), awaitItem())

        client.sendEvent(event1)
        assertEquals(listOf(event1), awaitItem())

        cancelAndIgnoreRemainingEvents()
    }
}
```

**Testing tools:**
- `runTest` - Virtual time, auto cleanup
- Turbine `.test {}` - Flow assertions
- `advanceTimeBy()` - Control time
- Fake implementations over mocks

## Common Scenarios

### Scenario: Implement New Relay Feature

**Steps:**
1. callbackFlow for subscription
2. Deduplication (Set of event IDs)
3. awaitClose for cleanup
4. Test with FakeNostrClient

**Example:** Add subscription for specific event kind

```kotlin
fun observeKind(kind: Int): Flow<Event> = callbackFlow {
    val listener = object : IRequestListener {
        override fun onEvent(event: Event, ...) {
            if (event.kind == kind) {
                trySend(event)
            }
        }
    }
    client.subscribe(listener)
    awaitClose { client.unsubscribe(listener) }
}
```

### Scenario: Handle Network Connectivity Changes

**Steps:**
1. callbackFlow for connectivity
2. flatMapLatest to reconnect
3. debounce to stabilize
4. Exception handling for failures

**Example:** Reconnect relays on connectivity

```kotlin
connectivityFlow
    .flatMapLatest { status ->
        when (status) {
            Active -> relayPool.observeEvents()
            else -> emptyFlow()
        }
    }
    .catch { e -> Log.e("Error", e) }
    .collect { event -> handleEvent(event) }
```

### Scenario: Optimize Multi-Collector Performance

**Steps:**
1. Use shareIn for expensive upstream
2. Configure SharingStarted strategy
3. Set replay buffer size
4. Test with multiple collectors

**Example:** Share relay subscription

```kotlin
val events: SharedFlow<Event> = client
    .reqAsFlow(relay, filters)
    .flatMapConcat { it.asFlow() }
    .shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        replay = 0
    )
```

## Anti-Patterns

❌ **Using GlobalScope**
```kotlin
GlobalScope.launch { /* Leaks, no structured concurrency */ }
```

✅ **Use scoped coroutines**
```kotlin
viewModelScope.launch { /* Cancelled with ViewModel */ }
```

---

❌ **Forgetting awaitClose**
```kotlin
callbackFlow {
    registerCallback()
    // Missing cleanup!
}
```

✅ **Always cleanup**
```kotlin
callbackFlow {
    registerCallback()
    awaitClose { unregisterCallback() }
}
```

---

❌ **Blocking in Flow**
```kotlin
flow.map { Thread.sleep(1000); process(it) }
```

✅ **Suspend, don't block**
```kotlin
flow.map { delay(1000); process(it) }.flowOn(Dispatchers.Default)
```

---

❌ **Ignoring backpressure**
```kotlin
fastProducer.collect { slowConsumer(it) }  // Blocks producer!
```

✅ **Handle backpressure**
```kotlin
fastProducer
    .buffer(64, BufferOverflow.DROP_OLDEST)
    .collect { slowConsumer(it) }
```

## Delegation

**Use kotlin-expert for:**
- Basic StateFlow/SharedFlow patterns
- viewModelScope.launch usage
- Simple MutableStateFlow → asStateFlow()

**Use nostr-expert for:**
- Nostr protocol details (NIPs, event structure)
- Event creation and signing
- Cryptographic operations

**This skill provides:**
- Advanced async patterns
- Structured concurrency
- Complex Flow operators
- Testing strategies
- Relay-specific async patterns

## Resources

- **references/advanced-flow-operators.md** - All Flow operators with examples
- **references/relay-patterns.md** - Nostr relay async patterns from codebase
- **references/testing-coroutines.md** - Complete testing guide

## Quick Decision Tree

```
Need async operation?
    ├─ Simple ViewModel state update → kotlin-expert (StateFlow)
    ├─ Android callback → This skill (callbackFlow)
    ├─ Multiple concurrent operations → This skill (supervisorScope)
    ├─ Complex Flow transformation → This skill (references/advanced-flow-operators.md)
    ├─ Relay subscription → This skill (references/relay-patterns.md)
    └─ Testing async code → This skill (references/testing-coroutines.md)
```
