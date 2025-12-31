# Testing Coroutines

Comprehensive guide for testing async code with runTest, Turbine, and best practices.

## runTest - Standard Testing

### Basic Pattern

```kotlin
@Test
fun `test suspend function`() = runTest {
    val result = repository.fetchData()
    assertEquals(expected, result)
}
```

**What runTest does:**
- Skips delays automatically
- Provides TestScope
- Advances virtual time
- Waits for all coroutines to complete

### Testing StateFlow

```kotlin
@Test
fun `stateflow updates correctly`() = runTest {
    val viewModel = MyViewModel()

    // Initial state
    assertEquals(UiState.Loading, viewModel.state.value)

    // Trigger action
    viewModel.loadData()
    advanceUntilIdle()  // Run all pending coroutines

    // Verify final state
    assertEquals(UiState.Success(data), viewModel.state.value)
}
```

### Testing with Time Control

```kotlin
@Test
fun `debounce works correctly`() = runTest {
    val viewModel = SearchViewModel()

    viewModel.search("a")
    advanceTimeBy(100)  // 100ms passed

    viewModel.search("ab")
    advanceTimeBy(100)

    viewModel.search("abc")
    advanceTimeBy(300)  // Debounce completes

    // Only "abc" should have triggered search
    assertEquals(listOf("abc"), viewModel.searchQueries)
}
```

**Time control functions:**
- `advanceTimeBy(millis)` - Move virtual time forward
- `advanceUntilIdle()` - Run all pending work
- `runCurrent()` - Run currently scheduled tasks only

## Turbine - Flow Testing Library

### Basic Collection Testing

```kotlin
@Test
fun `flow emits expected values`() = runTest {
    repository.observeData().test {
        assertEquals(Item1, awaitItem())
        assertEquals(Item2, awaitItem())
        assertEquals(Item3, awaitItem())
        awaitComplete()
    }
}
```

### Testing Flow Transformations

```kotlin
@Test
fun `map transforms correctly`() = runTest {
    val source = flowOf(1, 2, 3)

    source
        .map { it * 2 }
        .test {
            assertEquals(2, awaitItem())
            assertEquals(4, awaitItem())
            assertEquals(6, awaitItem())
            awaitComplete()
        }
}
```

### Testing Relay Subscriptions

```kotlin
@Test
fun `relay subscription receives events`() = runTest {
    val fakeClient = FakeNostrClient()

    fakeClient.reqAsFlow(relay, filters).test {
        // Initially empty
        assertEquals(emptyList(), awaitItem())

        // Send event
        fakeClient.sendEvent(event1)
        assertEquals(listOf(event1), awaitItem())

        // Send another
        fakeClient.sendEvent(event2)
        assertEquals(listOf(event1, event2), awaitItem())

        cancelAndIgnoreRemainingEvents()
    }
}
```

### Testing Error Handling

```kotlin
@Test
fun `catch handles errors gracefully`() = runTest {
    val errorFlow = flow {
        emit(1)
        throw IOException("Network error")
    }.catch { emit(-1) }  // Fallback value

    errorFlow.test {
        assertEquals(1, awaitItem())
        assertEquals(-1, awaitItem())
        awaitComplete()
    }
}
```

### Testing StateFlow with Turbine

```kotlin
@Test
fun `stateflow emits updates`() = runTest {
    val viewModel = MyViewModel()

    viewModel.state.test {
        // Skip initial value
        assertEquals(UiState.Loading, awaitItem())

        // Trigger update
        viewModel.loadData()
        assertEquals(UiState.Success(data), awaitItem())

        cancelAndIgnoreRemainingEvents()
    }
}
```

**Turbine assertions:**
- `awaitItem()` - Get next emission or fail
- `awaitComplete()` - Verify flow completed
- `awaitError()` - Verify flow threw exception
- `expectNoEvents()` - Assert no emissions in timeframe
- `cancelAndIgnoreRemainingEvents()` - Stop test

## Testing Patterns for Amethyst

### Pattern: Test Relay Connection Flow

```kotlin
@Test
fun `reconnects on connectivity change`() = runTest {
    val connectivityFlow = MutableStateFlow(ConnectivityStatus.Off)
    val relayPool = FakeRelayPool()

    connectivityFlow
        .flatMapLatest { status ->
            when (status) {
                is ConnectivityStatus.Active -> relayPool.connectAll()
                else -> emptyFlow()
            }
        }
        .test {
            // Initially offline
            expectNoEvents()

            // Go online
            connectivityFlow.value = ConnectivityStatus.Active(1L, false)
            assertTrue(relayPool.connected)

            cancelAndIgnoreRemainingEvents()
        }
}
```

### Pattern: Test Event Deduplication

```kotlin
@Test
fun `deduplicates events across relays`() = runTest {
    val relay1 = FakeRelay()
    val relay2 = FakeRelay()

    merge(relay1.events, relay2.events)
        .distinctBy { it.id }
        .test {
            // Both relays send same event
            relay1.send(event1)
            relay2.send(event1)

            // Only one emission
            assertEquals(event1, awaitItem())
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
}
```

### Pattern: Test Backpressure Handling

```kotlin
@Test
fun `drops oldest events when buffer full`() = runTest {
    val fastProducer = flow {
        repeat(100) { emit(it) }
    }

    fastProducer
        .buffer(capacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .test {
            // Slow consumer
            delay(100)

            // Should have dropped oldest, kept newest
            val items = mutableListOf<Int>()
            repeat(10) {
                items.add(awaitItem())
            }

            // Newest items present
            assertTrue(90 in items)
            assertTrue(99 in items)

            awaitComplete()
        }
}
```

### Pattern: Test Concurrent Subscriptions

```kotlin
@Test
fun `multiple subscriptions run concurrently`() = runTest {
    val client = FakeNostrClient()

    val feed1 = async { client.reqAsFlow(relay1, filters1).first() }
    val feed2 = async { client.reqAsFlow(relay2, filters2).first() }

    client.sendTo(relay1, event1)
    client.sendTo(relay2, event2)

    assertEquals(listOf(event1), feed1.await())
    assertEquals(listOf(event2), feed2.await())
}
```

## Fakes and Mocks

### Fake NostrClient

```kotlin
class FakeNostrClient : INostrClient {
    private val subscriptions = mutableMapOf<String, MutableSharedFlow<Event>>()

    override fun reqAsFlow(
        relay: NormalizedRelayUrl,
        filters: List<Filter>
    ): Flow<List<Event>> = callbackFlow {
        val subId = RandomInstance.randomChars(10)
        val flow = MutableSharedFlow<Event>()
        subscriptions[subId] = flow

        val events = mutableListOf<Event>()
        flow.collect { event ->
            events.add(event)
            send(events.toList())
        }

        awaitClose {
            subscriptions.remove(subId)
        }
    }

    fun sendEvent(event: Event) {
        subscriptions.values.forEach { it.tryEmit(event) }
    }

    fun sendTo(relay: NormalizedRelayUrl, event: Event) {
        subscriptions[relay.url]?.tryEmit(event)
    }
}
```

### Fake Relay Pool

```kotlin
class FakeRelayPool {
    var connected = false
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun connectAll(): Flow<Unit> = flow {
        connected = true
        emit(Unit)
    }

    fun disconnect() {
        connected = false
    }

    suspend fun sendEvent(event: Event) {
        _events.emit(event)
    }
}
```

## Testing Exception Handling

### Test CoroutineExceptionHandler

```kotlin
@Test
fun `exception handler catches errors`() = runTest {
    val errors = mutableListOf<Throwable>()

    val handler = CoroutineExceptionHandler { _, throwable ->
        errors.add(throwable)
    }

    val scope = CoroutineScope(
        Dispatchers.Unconfined + SupervisorJob() + handler
    )

    scope.launch {
        throw IOException("Test error")
    }

    advanceUntilIdle()

    assertEquals(1, errors.size)
    assertTrue(errors[0] is IOException)
}
```

### Test Retry Logic

```kotlin
@Test
fun `retries failed connections`() = runTest {
    var attempts = 0
    val maxRetries = 3

    flow {
        attempts++
        if (attempts < maxRetries) {
            throw IOException("Connection failed")
        }
        emit("Success")
    }
        .retry(maxRetries)
        .test {
            assertEquals("Success", awaitItem())
            awaitComplete()
            assertEquals(3, attempts)
        }
}
```

## Common Testing Patterns

### Pattern: Verify No Emissions After Cancellation

```kotlin
@Test
fun `no emissions after cancellation`() = runTest {
    val flow = flow {
        emit(1)
        delay(1000)
        emit(2)  // Should not emit
    }

    flow.test {
        assertEquals(1, awaitItem())
        cancel()

        // Verify no more emissions
        expectNoEvents()
    }
}
```

### Pattern: Test Time-Based Operations

```kotlin
@Test
fun `periodic emission works`() = runTest {
    flow {
        repeat(3) {
            emit(it)
            delay(1000)
        }
    }.test {
        assertEquals(0, awaitItem())

        advanceTimeBy(1000)
        assertEquals(1, awaitItem())

        advanceTimeBy(1000)
        assertEquals(2, awaitItem())

        awaitComplete()
    }
}
```

### Pattern: Test Hot Flow Conversion

```kotlin
@Test
fun `shareIn creates hot flow`() = runTest {
    var emissions = 0
    val source = flow {
        repeat(3) {
            emissions++
            emit(it)
        }
    }

    val shared = source.shareIn(
        scope = this,
        started = SharingStarted.Eagerly,
        replay = 1
    )

    // First collector
    shared.take(2).collect()
    assertEquals(2, emissions)  // Emitted 0, 1

    // Second collector - shares upstream
    shared.take(1).collect()
    assertEquals(3, emissions)  // Only emitted 2, not restarted

    cancel()
}
```

## Best Practices

1. **Use runTest for all coroutine tests**
   - Provides virtual time
   - Automatic cleanup

2. **Use Turbine for Flow testing**
   - Clearer assertions
   - Better error messages

3. **Test both success and error paths**
   - Normal flow
   - Exception handling
   - Edge cases

4. **Control virtual time explicitly**
   - Don't rely on real delays
   - Use `advanceTimeBy()` and `advanceUntilIdle()`

5. **Create fakes, not mocks**
   - Simpler to maintain
   - More realistic behavior
   - Easier to debug

6. **Test cancellation behavior**
   - Verify cleanup happens
   - Check no emissions after cancel

7. **Test concurrent operations**
   - Use `async` to spawn concurrent work
   - Verify independence with SupervisorJob
