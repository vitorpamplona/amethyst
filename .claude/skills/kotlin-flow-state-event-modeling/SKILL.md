---
name: kotlin-flow-state-event-modeling
description: Use when writing or reviewing Kotlin StateFlow/SharedFlow/Channel choices, sentinel default values, stateIn placement, WhileSubscribed staleness, or MutableStateFlow update patterns. Technique-layer skill — complements the codebase-specific kotlin-expert.
---

# Kotlin Flow: state and event modeling

## Core principle

**Pick the primitive that matches replay, fan-out, and synchronous-read requirements.** `StateFlow`, `SharedFlow`, `Channel`-backed flows, and cold `Flow` differ in buffering, who sees each emission, and whether `.value` exists. Wrong choices drop events, leak sharing coroutines, or force fake domain sentinels into state.

## When to use this skill

You're writing or reviewing Kotlin code involving:

- `MutableStateFlow<T>(SomeSentinel)` — `NoUser`, `Empty`, `Loading`, etc. — because the real value is async
- `.stateIn(...)` called inside a function rather than assigned to a property
- `SharingStarted.WhileSubscribed(...)` on a flow whose `.value` is read synchronously and must stay fresh
- `MutableSharedFlow` for navigation events, snackbars, or other one-shot emissions where loss would be a bug
- `.map { }` on a `StateFlow` when consumers still need synchronous `.value`
- `MutableStateFlow.value = _state.value.copy(...)` or update code that builds expensive objects inside `update { ... }`

## SharedFlow for single-consumer fire-once events

`SharedFlow` defaults have no replay buffer. If nothing is collecting at the exact instant of emission, the event is gone. For a **single UI consumer** handling exactly-once events such as navigation or snackbars, a buffered `Channel` exposed as a `Flow` often matches the semantics better:

```kotlin
// ❌ BAD
private val _navEvents = MutableSharedFlow<NavigationEvent>()
val navEvents: SharedFlow<NavigationEvent> = _navEvents.asSharedFlow()

// ✅ GOOD
private val _navEvents = Channel<NavigationEvent>(Channel.BUFFERED)
val navEvents: Flow<NavigationEvent> = _navEvents.receiveAsFlow()
```

`Channel.receiveAsFlow()` is **fan-out, not broadcast**: with multiple collectors, each event is delivered to **one** collector. `Channel.BUFFERED` is bounded, so sends can suspend and `trySend` can fail. If multiple observers must all see the same event, use explicit state, durable storage, or a deliberately configured `SharedFlow` instead.

## StateFlow polluted with invalid sentinel defaults

`StateFlow` forces an initial value. When the real value is async, developers sometimes invent fake domain values — `NoUser`, `EmptyUser`, placeholder IDs — and every consumer is forced to treat that sentinel as real data.

```kotlin
// ❌ BAD — sentinel leaks into the type
class UserSession(private val db: Db) {
    private val _user = MutableStateFlow<User>(NoUser)
    val user: StateFlow<User> = _user.asStateFlow()
    init { scope.launch { _user.value = db.load() } }
}
```

One fix is **phasing**: don't expose the `StateFlow` until the real value exists.

```kotlin
// ✅ GOOD — bootstrap suspends; observers only see real users
class UserSession(private val db: Db) {
    private var _user: MutableStateFlow<User>? = null
    val user: StateFlow<User>
        get() = checkNotNull(_user) { "Call login() first" }

    suspend fun login() {
        _user = MutableStateFlow(db.load())
    }
}
```

If absence, loading, or error is a real state, model it explicitly (`User?`, `sealed interface UserUiState`, `Result`, etc.). The bug is a fake domain value masquerading as real data, not every initial value.

## Mutate MutableStateFlow with `update { ... }`

Prefer `MutableStateFlow.update { current -> ... }` over reading `.value` and writing it back. `update` applies the transform atomically against the latest state, which avoids lost updates when multiple coroutines mutate the same state.

```kotlin
// BAD — read/modify/write can lose concurrent updates.
_state.value = _state.value.copy(
    selectedId = id,
    details = details,
)

// GOOD — transform starts from the latest state.
_state.update { current ->
    current.copy(
        selectedId = id,
        details = details,
    )
}
```

Keep object creation outside the `update` block unless it needs the current state. The update lambda can be retried, so expensive work or side effects inside it may run more than once:

```kotlin
// GOOD — details does not depend on current state, so build it once.
val details = Details.from(response)
_state.update { current ->
    current.copy(details = details)
}

// GOOD — derived value depends on current state, so compute it inside.
_state.update { current ->
    val nextItems = current.items.replaceById(updatedItem)
    current.copy(items = nextItems)
}
```

The block should be a pure, fast state transformation: no network calls, database writes, logging side effects, random IDs, or time reads unless those values were captured before the block.

## `stateIn()` inside a function

```kotlin
// ❌ BAD — new sharing coroutine every call
fun getPreferences(): StateFlow<Prefs> =
    repo.prefsFlow.stateIn(scope, SharingStarted.Eagerly, Prefs.Default)
```

Every call to `getPreferences()` launches a fresh coroutine on `scope` that never completes. Performance dies fast under repeated reads.

```kotlin
// ✅ GOOD — one shared instance, computed once
val preferences: StateFlow<Prefs> =
    repo.prefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs.Default)
```

## `WhileSubscribed` with synchronous `.value`

`SharingStarted.WhileSubscribed(timeout)` disconnects the upstream when there are no active collectors. While disconnected, `.value` returns the last cached value, which may be stale or still the initial value.

**Rule:** if `.value` must be fresh or initialized without an active collector, use `SharingStarted.Eagerly` or explicit initialization. `WhileSubscribed` is fine when stale/cached values are acceptable and consumers primarily collect asynchronously.

## `.map` on `StateFlow` loses `.value`

```kotlin
// ❌ BAD — `name.value` won't compile; it's now a plain Flow
val name: Flow<String> = userState.map { it.name }
```

If you need synchronous `.value`, terminate the chain with `.stateIn(...)`:

```kotlin
// ✅ GOOD
val name: StateFlow<String> = userState
    .map { it.name }
    .stateIn(viewModelScope, SharingStarted.Eagerly, userState.value.name)
```

Community "derived state flow" utilities run the transform on every `.value` read — only acceptable for fast, idempotent transforms. Default to `.stateIn(...)`.

## Decision: which Flow type?

| Need | Primitive |
|------|-----------|
| State that always has a value, read by both async collectors **and** synchronous code | `StateFlow`, often with `SharingStarted.Eagerly` when `.value` matters |
| Hot stream, multiple subscribers, **no** requirement for synchronous `.value` | `SharedFlow` |
| Discrete events for **one** consumer, exactly-once handoff | Consider `Channel(BUFFERED).receiveAsFlow()` |
| Cold stream, one consumer per collection | Plain `Flow` |

If you're tempted to reach for `SharedFlow`, ask: would dropping an emission be a bug, and how many consumers must see it? If one consumer must handle it exactly once, a `Channel` may fit. If every observer must see it, model durable state or configure a broadcast stream deliberately.

## Quick reference

| Symptom | Problem | Fix |
|---------|---------|-----|
| `MutableStateFlow<X>(FakeDomainValue)` | Invalid placeholder default | Model absence explicitly or use phase initialization |
| `MutableSharedFlow<Event>` for single-consumer nav/snackbar | Lossy default event stream | Consider `Channel(BUFFERED).receiveAsFlow()` |
| `fun foo() = flow.stateIn(...)` | Per-call sharing coroutine | Make it a `val` / shared instance |
| `WhileSubscribed` + `.value` must be fresh/initialized | Stale or initial data | `SharingStarted.Eagerly` or explicit initialization |
| `stateFlow.map { ... }` consumed as state | Lost `.value` | Terminate with `.stateIn(...)` |
| `_state.value = _state.value.copy(...)` | Non-atomic read/modify/write | `_state.update { it.copy(...) }` |
| Expensive object creation inside `update { ... }` that doesn't use current state | Work can repeat if update retries | Build before `update`; keep only current-state transforms inside |

## Red flags during review

| Thought | Reality |
|---------|---------|
| "We need `SharedFlow` because there are multiple subscribers" | Multiple subscribers change the semantics. `Channel.receiveAsFlow()` is not broadcast; choose the event model deliberately. |
| "We'll use `WhileSubscribed` to save resources" | Only if stale/initial `.value` reads are acceptable. Verify before applying. |
| "I'll use a sentinel until real data loads" | Consumers treat it as real domain; prefer explicit UI/state modeling or phasing. |
| "I'll construct the new object inside `update` because it's convenient" | The lambda may retry. Construct outside unless it depends on the current state. |

## Related

- [`kotlin-coroutines-structured-concurrency`](../kotlin-coroutines-structured-concurrency/SKILL.md) — scope ownership, init launches, fire-and-forget boundaries, cancellation, `runBlocking`
- [`compose-side-effects`](../compose-side-effects/SKILL.md) — collecting event flows and wiring side effects in Compose
- [`compose-state-holder-ui-split`](../compose-state-holder-ui-split/SKILL.md) — where state holders expose flows to UI
