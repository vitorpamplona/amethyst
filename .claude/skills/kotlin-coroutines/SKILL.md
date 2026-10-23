---
name: kotlin-coroutines
description: Coroutines/Flow in Amethyst: picking IO vs Default, scopes and cancellation (CancellationException, withTimeout), StateFlow/SharedFlow/Channel and stateIn choices, runTest. Use when writing or reviewing async Kotlin code.
---

# Kotlin coroutines in Amethyst

Repo-specific rules only. Textbook coroutines/Flow knowledge is assumed.

## Dispatchers

**`Dispatchers.IO` is the default; `Default` only for provably lock-free CPU work.**
The full rule, with the reasons and examples (`ParallelEventVerifier`,
`PoWMiner.MiningDispatcher`, `BagSweep`, `NostrSignerExternal`), is
`commons/ARCHITECTURE.md` › "Choosing a dispatcher". Read it before writing
`flowOn`/`withContext`. In `commonMain`, `Dispatchers.IO` needs
`import kotlinx.coroutines.IO`.

## Relay work: reuse, don't hand-roll

Never write your own `callbackFlow { openReqSubscription(...); awaitClose { ... } }`
REQ loop. One-shot and paged fetches, publish-and-confirm, COUNT and negentropy sync
already exist as `INostrClient` extensions in
`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/client/accessories/`
(catalog: its `README.md`; flow helpers in `../reqs/`). Their timeouts are idle
windows (`idleTimeoutMs`), not deadlines. For loads tied to a composable's lifetime,
see the `relay-client` skill.

`callbackFlow` is still the right bridge for platform callbacks; copy
`amethyst/.../service/connectivity/ConnectivityFlow.kt` (initial emit, `awaitClose`
unregister, `distinctUntilChanged().debounce(200).flowOn(Dispatchers.IO)`).

## Scopes

- **Stored scopes are fine for lifecycle owners.** `Account` takes
  `scope: CoroutineScope`; Android services build
  `CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)` and
  `cancel()` it in `onDestroy` (`PushNotificationReceiverService`). Hundreds of
  such fields are legitimate; don't flag them on sight.
- **New non-owner classes** (helpers, repositories, parsers) expose `suspend` APIs
  and do not `launch` from `init`. Let the owner pick the scope.
- **No `runBlocking`** outside tests, previews and the CLI entry point. No
  `GlobalScope`.

## Cancellation and timeouts

- A broad `catch (e: Exception)` / `runCatching` around a suspend call must rethrow
  `CancellationException`.
- **`TimeoutCancellationException` IS a `CancellationException`.** A blanket
  rethrow turns one slow RPC into a dead loop: rethrowing it ended the cordn sync
  loop for the whole session. Either catch the timeout narrowly, right at the
  `withTimeout`, or rethrow only when the coroutine itself is cancelled
  (`if (!isActive) throw e`). See cordn-kmp's `cordn/client/CordnSyncLoop.kt` for both.

## State and events

- `MutableStateFlow.update { }` instead of `_s.value = _s.value.copy(...)`. Keep the
  block pure and cheap (it can retry); build expensive values before it.
- `stateIn`/`shareIn` belong on a `val`, never inside a function (each call starts a
  new sharing coroutine).
- `WhileSubscribed` stops upstream with no collectors, so `.value` goes stale or
  stays at the initial value. If code reads `.value` synchronously, use `Eagerly`.
- `stateFlow.map { }` is a plain `Flow`; terminate with `stateIn` if consumers
  need `.value`.
- One-shot events for a single consumer (navigation, a toast): a
  `Channel(Channel.BUFFERED).receiveAsFlow()`, not a no-replay `MutableSharedFlow`
  that drops emissions with no collector. A `Channel` delivers each item to ONE
  collector; if every observer must see it, model it as state.
- Don't invent fake domain sentinels as the initial value; use `null` or a sealed
  loading state.

## Tests

- `runTest` from `kotlinx-coroutines-test`, not `runBlocking`. Turbine is not a
  dependency; collect into a list in `backgroundScope.launch { flow.collect { } }`
  and drive time with `advanceUntilIdle()`, `runCurrent()`, `advanceTimeBy()`.
- Inject the dispatcher/scope under test (`StandardTestDispatcher(testScheduler)`
  or `UnconfinedTestDispatcher`) so delays run on virtual time.
- Never wait on wall-clock time (`delay(350)` racing a 200 ms timeout flaked on
  loaded CI). Wait on the test scheduler or a condition.
