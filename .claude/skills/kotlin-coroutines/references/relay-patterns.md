# Nostr Relay Async Patterns

Proven coroutine patterns for Nostr relay connections, subscriptions, and event streaming in Amethyst.

## Core Pattern: callbackFlow for Relay Subscriptions

### Pattern: Subscription as Flow

**Real implementation from NostrClientStaticReqAsStateFlow.kt:**

```kotlin
fun INostrClient.reqAsFlow(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
): Flow<List<Event>> =
    callbackFlow {
        val subId = RandomInstance.randomChars(10)
        var hasBeenLive = false
        val eventIds = mutableSetOf<HexKey>()
        var currentEvents = listOf<Event>()

        val listener = object : IRequestListener {
            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (event.id !in eventIds) {
                    if (hasBeenLive) {
                        // After EOSE: prepend new events
                        val list = ArrayList<Event>(1 + currentEvents.size)
                        list.add(event)
                        list.addAll(currentEvents)
                        currentEvents = list
                    } else {
                        // Before EOSE: append events
                        currentEvents = currentEvents + event
                    }
                    eventIds.add(event.id)
                    trySend(currentEvents)
                }
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                hasBeenLive = true
            }
        }

        openReqSubscription(subId, mapOf(relay to filters), listener)

        awaitClose {
            close(subId)
        }
    }
```

**Key techniques:**
1. **callbackFlow** - Bridge callback API to Flow
2. **Deduplication** - `eventIds` set prevents duplicates
3. **EOSE handling** - Changes insertion strategy (append → prepend)
4. **awaitClose** - Cleanup when flow cancelled
5. **trySend** - Non-blocking emission from callback

## Multi-Relay Patterns

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

**Explanation:**
- Each relay produces `Flow<List<Event>>`
- `flatMapConcat` flattens to `Flow<Event>`
- `merge()` combines all relay flows
- `distinctBy` deduplicates across relays

### Pattern: Concurrent Relay Operations with supervisorScope

```kotlin
suspend fun subscribeToRelays(
    relays: List<Relay>,
    filters: List<Filter>
) = supervisorScope {
    relays.forEach { relay ->
        launch {
            relay.subscribe(filters).collect { event ->
                eventChannel.send(event)
            }
        }
    }
}
```

**Why supervisorScope:**
- If one relay fails, others continue
- All children cancelled when scope cancelled
- Structured concurrency maintained

## Backpressure Handling

### Pattern: Buffer with Drop Strategy

**For high-frequency event streams:**

```kotlin
relayFlow
    .buffer(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    .collect { event -> processEvent(event) }
```

**Strategy selection:**
- `DROP_OLDEST` - For real-time feeds (lose old events OK)
- `DROP_LATEST` - For priority queues (lose new events OK)
- `SUSPEND` - For critical events (slow down producer)

### Pattern: Conflate for UI Updates

```kotlin
val uiEvents: Flow<UiEvent> = relayEvents
    .map { event -> toUiEvent(event) }
    .conflate()  // Skip intermediate, show latest
    .flowOn(Dispatchers.Default)
```

## Connection Management

### Pattern: Network Connectivity as Flow

**Real implementation from ConnectivityFlow.kt:**

```kotlin
@OptIn(FlowPreview::class)
val status = callbackFlow {
    trySend(ConnectivityStatus.StartingService)

    val connectivityManager = context.getConnectivityManager()

    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            connectivityManager.getNetworkCapabilities(network)?.let {
                trySend(ConnectivityStatus.Active(
                    network.networkHandle,
                    it.isMeteredOrMobileData()
                ))
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val isMobile = networkCapabilities.isMeteredOrMobileData()
            trySend(ConnectivityStatus.Active(
                network.networkHandle,
                isMobile
            ))
        }

        override fun onLost(network: Network) {
            trySend(ConnectivityStatus.Off)
        }
    }

    connectivityManager.registerDefaultNetworkCallback(networkCallback)

    // Send initial state
    connectivityManager.activeNetwork?.let { network ->
        connectivityManager.getNetworkCapabilities(network)?.let {
            trySend(ConnectivityStatus.Active(
                network.networkHandle,
                it.isMeteredOrMobileData()
            ))
        }
    }

    awaitClose {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        trySend(ConnectivityStatus.Off)
    }
}
    .distinctUntilChanged()
    .debounce(200)  // Stabilize rapid changes
    .flowOn(Dispatchers.IO)
```

**Key patterns:**
1. **Initial state** - Emit current connectivity immediately
2. **Callback registration** - Register listener in flow body
3. **Cleanup** - Unregister in `awaitClose`
4. **Stabilization** - `debounce(200)` prevents flapping
5. **Deduplication** - `distinctUntilChanged()` skips redundant updates

### Pattern: Reconnect on Connectivity Change

```kotlin
connectivityFlow
    .flatMapLatest { status ->
        when (status) {
            is ConnectivityStatus.Active -> {
                relayPool.connectAll()
                relayPool.observeEvents()
            }
            else -> emptyFlow()
        }
    }
    .collect { event -> handleEvent(event) }
```

## Exception Handling in Async Operations

### Pattern: CoroutineExceptionHandler + SupervisorJob

**Real implementation from PushNotificationReceiverService.kt:**

```kotlin
class PushNotificationReceiverService : FirebaseMessagingService() {
    // Catch all uncaught exceptions
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AmethystCoroutine", "Caught exception: ${throwable.message}", throwable)
    }

    // Children fail independently, handler catches all
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + exceptionHandler
    )

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        scope.launch(Dispatchers.IO) {
            parseMessage(remoteMessage.data)?.let { receiveIfNew(it) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

**Why this pattern:**
- **SupervisorJob** - One failure doesn't cancel others
- **ExceptionHandler** - Log exceptions, don't crash
- **Scoped lifecycle** - Cancel all on destroy

### Pattern: Retry with Backoff for Relay Connections

```kotlin
fun connectWithRetry(relay: Relay): Flow<ConnectionStatus> = flow {
    var attempt = 0
    val maxRetries = 5
    val baseDelay = 1000L

    while (attempt < maxRetries) {
        try {
            emit(ConnectionStatus.Connecting)
            relay.connect()
            emit(ConnectionStatus.Connected)
            return@flow
        } catch (e: Exception) {
            attempt++
            emit(ConnectionStatus.Error(e, attempt))

            if (attempt < maxRetries) {
                val delay = baseDelay * (1L shl attempt)  // Exponential backoff
                delay(delay)
            }
        }
    }
    emit(ConnectionStatus.Failed)
}
```

## Subscription Lifecycle

### Pattern: Auto-Cleanup Subscription

```kotlin
@Composable
fun ObserveRelayEvents(
    filters: List<Filter>,
    onEvent: (Event) -> Unit
) {
    val scope = rememberCoroutineScope()

    DisposableEffect(filters) {
        val job = scope.launch {
            relayClient.reqAsFlow(filters).collect { events ->
                events.forEach { onEvent(it) }
            }
        }

        onDispose {
            job.cancel()  // Cancels flow, triggers awaitClose
        }
    }
}
```

**Lifecycle:**
1. Composable enters → subscribe
2. filters change → cancel + re-subscribe
3. Composable leaves → cancel + cleanup

### Pattern: Multiple Concurrent Subscriptions

```kotlin
fun observeMultipleFeeds(
    account: Account
): Flow<Event> = channelFlow {
    supervisorScope {
        // Home feed
        launch {
            client.reqAsFlow(filters = homeFeedFilters)
                .collect { events -> events.forEach { send(it) } }
        }

        // Notifications
        launch {
            client.reqAsFlow(filters = notificationFilters)
                .collect { events -> events.forEach { send(it) } }
        }

        // DMs
        launch {
            client.reqAsFlow(filters = dmFilters)
                .collect { events -> events.forEach { send(it) } }
        }
    }
}
```

**Benefits:**
- All subscriptions run concurrently
- One failure doesn't affect others (supervisorScope)
- Single output channel for all events

## Performance Optimization

### Pattern: Shared Upstream for Multiple Collectors

```kotlin
class RelayViewModel(private val client: INostrClient) : ViewModel() {
    val events: SharedFlow<Event> = client
        .reqAsFlow(relay, filters)
        .flatMapConcat { it.asFlow() }
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 0
        )
}

// Multiple collectors share single relay subscription
events.collect { /* UI 1 */ }
events.collect { /* UI 2 */ }
```

### Pattern: Event Deduplication Cache

```kotlin
class EventCache {
    private val seen = mutableSetOf<HexKey>()

    fun filterNew(events: List<Event>): List<Event> =
        events.filter { event ->
            if (event.id in seen) {
                false
            } else {
                seen.add(event.id)
                true
            }
        }
}

val deduplicatedEvents = relayEvents
    .map { events -> cache.filterNew(events) }
    .filter { it.isNotEmpty() }
```

## Testing Relay Flows

### Pattern: Test with Fake Relay

```kotlin
@Test
fun `subscription receives events`() = runTest {
    val fakeRelay = FakeRelay()
    val client = NostrClient(fakeRelay)

    val events = mutableListOf<Event>()
    val job = launch {
        client.reqAsFlow(relay, filters).collect { list ->
            events.addAll(list)
        }
    }

    // Simulate relay responses
    fakeRelay.sendEvent(testEvent1)
    advanceTimeBy(100)
    fakeRelay.sendEvent(testEvent2)
    advanceTimeBy(100)

    assertEquals(2, events.size)
    job.cancel()
}
```

## Common Pitfalls

### ❌ Forgetting awaitClose

```kotlin
// BAD: Subscription never cleaned up
callbackFlow {
    relay.subscribe(listener)
    // Missing awaitClose!
}
```

```kotlin
// GOOD: Proper cleanup
callbackFlow {
    relay.subscribe(listener)
    awaitClose {
        relay.unsubscribe(listener)
    }
}
```

### ❌ Using GlobalScope

```kotlin
// BAD: Unstructured, leaks
GlobalScope.launch {
    relay.connect()
}
```

```kotlin
// GOOD: Scoped to lifecycle
viewModelScope.launch {
    relay.connect()
}
```

### ❌ Blocking in Flow Operators

```kotlin
// BAD: Blocks collector
flow.map { event ->
    Thread.sleep(1000)  // Blocks!
    process(event)
}
```

```kotlin
// GOOD: Use flowOn to offload
flow
    .map { event ->
        delay(1000)  // Suspends, doesn't block
        process(event)
    }
    .flowOn(Dispatchers.Default)
```
