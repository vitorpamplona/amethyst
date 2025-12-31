# Advanced Flow Operators

Comprehensive guide to Flow operators for complex async patterns in Amethyst.

## Transformation Operators

### flatMapLatest - Cancel Previous, Switch to New

**Use when:** Latest value matters, previous operations should cancel

```kotlin
// User types in search box → cancel previous search
searchQuery
    .flatMapLatest { query ->
        repository.search(query) // Cancels previous search
    }
    .collect { results -> updateUI(results) }
```

**Amethyst pattern:**
```kotlin
// Switch relays based on latest account
accountFlow
    .flatMapLatest { account ->
        relayPool.observeEvents(account.relays)
    }
```

### flatMapConcat - Sequential Processing

**Use when:** Order matters, process one at a time

```kotlin
eventIds
    .flatMapConcat { id ->
        repository.fetchEvent(id)
    }
    .collect { event -> process(event) }
```

### flatMapMerge - Concurrent Processing

**Use when:** Process multiple simultaneously, order doesn't matter

```kotlin
relays
    .flatMapMerge(concurrency = 10) { relay ->
        relay.subscribe(filters)
    }
    .collect { event -> handleEvent(event) }
```

## Combination Operators

### combine - Latest from Multiple Flows

**Use when:** Need latest value from ALL flows

```kotlin
combine(
    accountFlow,
    settingsFlow,
    connectivityFlow
) { account, settings, connectivity ->
    AppState(account, settings, connectivity)
}.collect { state -> render(state) }
```

**Pattern:** Re-emits whenever ANY source emits

### zip - Pair Values in Order

**Use when:** Need corresponding values from flows

```kotlin
zip(requestFlow, responseFlow) { req, res ->
    Pair(req, res)
}
```

**Pattern:** Waits for BOTH to emit before pairing

### merge - Combine Multiple Flows

**Use when:** Treat multiple flows as single stream

```kotlin
merge(
    relay1.events,
    relay2.events,
    relay3.events
).collect { event -> handleEvent(event) }
```

## Backpressure & Buffering

### shareIn - Hot Flow from Cold

**Use when:** Multiple collectors should share single upstream

```kotlin
val sharedEvents = repository.observeEvents()
    .shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        replay = 0
    )

// Multiple collectors share same upstream
sharedEvents.collect { /* collector 1 */ }
sharedEvents.collect { /* collector 2 */ }
```

**SharingStarted strategies:**
- `Eagerly` - Start immediately, never stop
- `Lazily` - Start on first subscriber, never stop
- `WhileSubscribed(stopTimeout)` - Stop after last unsubscribe + timeout

### stateIn - StateFlow from Cold Flow

**Use when:** Convert Flow to StateFlow (always has value)

```kotlin
val uiState: StateFlow<UiState> = repository.observeData()
    .map { data -> UiState.Success(data) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )
```

**Amethyst pattern:**
```kotlin
// Connectivity status as StateFlow
val connectivity: StateFlow<ConnectivityStatus> =
    connectivityFlow.status
        .stateIn(
            scope = serviceScope,
            started = SharingStarted.Eagerly,
            initialValue = ConnectivityStatus.Off
        )
```

### buffer - Control Backpressure

**Use when:** Producer faster than consumer

```kotlin
eventFlow
    .buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    .collect { event -> slowProcessor(event) }
```

**Strategies:**
- `SUSPEND` - Slow down producer (default)
- `DROP_OLDEST` - Drop oldest in buffer
- `DROP_LATEST` - Drop newest emission

### conflate - Keep Only Latest

**Use when:** Only latest value matters, skip intermediate

```kotlin
locationFlow
    .conflate() // Skip intermediate locations
    .collect { location -> updateMap(location) }
```

## Debouncing & Throttling

### debounce - Wait for Quiet Period

**Use when:** Wait for user to stop typing

```kotlin
searchQuery
    .debounce(300) // Wait 300ms after last emission
    .flatMapLatest { query -> search(query) }
```

**Amethyst pattern:**
```kotlin
// ConnectivityFlow.kt:87
connectivityFlow
    .distinctUntilChanged()
    .debounce(200)  // Wait 200ms for network to stabilize
    .flowOn(Dispatchers.IO)
```

### sample - Periodic Sampling

**Use when:** Rate-limit high-frequency emissions

```kotlin
sensorData
    .sample(1000) // Sample every 1 second
    .collect { data -> process(data) }
```

## Error Handling

### catch - Handle Upstream Errors

**Use when:** Graceful degradation needed

```kotlin
repository.fetchData()
    .catch { e ->
        Log.e("Error", e)
        emit(emptyList()) // Fallback value
    }
    .collect { data -> updateUI(data) }
```

**Pattern:** Only catches UPSTREAM errors, not in collect block

### retry/retryWhen - Automatic Retry

```kotlin
relayConnection
    .retry(3) { cause ->
        cause is IOException // Only retry on network errors
    }
```

## Context Switching

### flowOn - Change Upstream Dispatcher

**Use when:** Offload work from current context

```kotlin
repository.fetchData()
    .map { heavyProcessing(it) }
    .flowOn(Dispatchers.Default) // Heavy work on Default
    .collect { updateUI(it) }     // Collect on Main
```

**Critical:** Only affects UPSTREAM operators

**Amethyst pattern:**
```kotlin
// ConnectivityFlow.kt:87
callbackFlow { /* ... */ }
    .distinctUntilChanged()
    .debounce(200)
    .flowOn(Dispatchers.IO)  // All upstream on IO
```

## Common Patterns

### Pattern: Multi-Relay Subscription

```kotlin
fun observeFromMultipleRelays(relays: List<Relay>, filters: List<Filter>): Flow<Event> =
    relays.map { relay ->
        relay.subscribe(filters)
    }.merge()
    .distinctBy { it.id }
```

### Pattern: Load + Cache + Observe

```kotlin
fun observeWithCache(id: String): Flow<Data> = flow {
    // Emit cached value immediately
    cache[id]?.let { emit(it) }

    // Then observe updates
    emitAll(repository.observe(id))
}.distinctUntilChanged()
```

### Pattern: Retry with Exponential Backoff

```kotlin
fun <T> Flow<T>.retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt >= maxRetries || cause !is IOException) {
        false
    } else {
        delay(initialDelay * (1L shl attempt.toInt()))
        true
    }
}
```

## Performance Tips

1. **Use shareIn for expensive operations**
   - Compute once, share with multiple collectors

2. **Choose right backpressure strategy**
   - UI updates: `conflate()` or `DROP_OLDEST`
   - Events: `buffer()` with appropriate size

3. **flowOn placement matters**
   - Place after expensive operators to offload them

4. **Avoid unnecessary emissions**
   - Use `distinctUntilChanged()` when appropriate
   - Consider `debounce()` for high-frequency sources

5. **StateFlow vs SharedFlow**
   - StateFlow: Always has value, conflates
   - SharedFlow: Optional replay, configurable buffering
