---
name: kotlin-coroutines-structured-concurrency
description: Use when writing or reviewing Kotlin code that stores CoroutineScope, launches from init/non-suspending APIs, calls runBlocking, or catches broad exceptions around suspend calls. Technique-layer skill — complements the codebase-specific kotlin-coroutines.
---

# Kotlin coroutines: structured concurrency

## Core principle

A well-structured coroutine is a self-contained unit of asynchronous work — single entry, single exit, scoped to a lifecycle known at the call site.

**Scopes should usually be tied to the caller's lifecycle, not stored as a property on the callee.** A stored `CoroutineScope` is a strong review signal: the class must prove it owns cancellation, error reporting, restart behavior, and lifecycle. Most repositories, managers, use cases, and data sources cannot prove that, so they should expose `suspend` APIs instead.

The fix is almost always the same: **make the API `suspend` and let the caller own the scope.**

## When to use this skill

You're writing or reviewing Kotlin code and you see any of these:

- A class with `private val scope: CoroutineScope` (constructor param stored as a property)
- An `init { scope.launch { ... } }` block
- A non-suspending public function whose body is `scope.launch { ... }`
- `runBlocking { ... }` in suspend-capable application code, or in tests where `runTest` should apply
- `runCatching { suspendCall() }` or a `catch` on `Exception` / `Throwable` around a `suspend` call without rethrowing `CancellationException`
- A `catch (e: CancellationException)` (or equivalent) around suspension that does not rethrow

## The silent-cancellation bug

The reason an unowned `CoroutineScope` property is so dangerous: "once a scope is cancelled, every future `launch` on it silently completes as cancelled — no exception, no log, nothing." The work just doesn't happen. This is one of the hardest coroutine bugs to diagnose, and it appears when a class holds a long-lived reference to a lifecycle it does not own.

If APIs are `suspend`, this can't happen: the caller's scope is either alive (work runs) or the call site cancels (the caller knows).

## Anti-patterns and fixes

### 1. CoroutineScope stored as a property

```kotlin
// ❌ BAD
@Inject
class UserRepository(
    private val scope: CoroutineScope,
    private val api: UserApi,
) {
    fun refresh() {
        scope.launch { _state.value = api.fetchUser() }
    }
}

// ✅ GOOD
@Inject
class UserRepository(
    private val api: UserApi,
) {
    suspend fun refresh(): User = api.fetchUser()
}
```

The repository no longer needs to know about coroutines at all. The caller (a ViewModel, a use case) decides on what scope, with what error handling, with what cancellation semantics.

### 2. init-block launches

```kotlin
// ❌ BAD: construction-time side effect, unbounded work
class UserSession(private val scope: CoroutineScope, private val api: Api) {
    init { scope.launch { _user.value = api.load() } }
}
```

The constructor returns immediately. The caller can't `await` the load, can't see errors, can't cancel. The class is "alive" but its state is undefined.

```kotlin
// ✅ GOOD: explicit bootstrap, caller owns the suspension
class UserSession(private val api: Api) {
    private var _user: User? = null
    val user: User get() = checkNotNull(_user) { "Call init() first" }

    suspend fun init() { _user = api.load() }
}
```

### 3. Fire-and-forget from non-UI classes

A non-suspending public function on a **non-UI class** (repository, manager, use case, data source) that launches into a class-owned scope. The caller gets no result, no error, no cancellation, and no guarantee the work ever ran.

```kotlin
// ❌ BAD — repository with stored scope and fire-and-forget public API
class AnalyticsClient(private val scope: CoroutineScope, private val api: Api) {
    fun track(event: Event) {
        scope.launch { api.send(event) }      // caller has no idea what happens
    }
    fun signOut() {
        scope.launch { api.signOut() }        // silent failure if scope cancelled
    }
}
```

```kotlin
// ✅ GOOD
class AnalyticsClient(private val api: Api) {
    suspend fun track(event: Event) = api.send(event)
    suspend fun signOut() = api.signOut()
}
```

#### Carve-out: the UI ↔ state-holder boundary

UI frameworks are non-suspending. A Composable's `onClick`, a Fragment's `onKeyEvent`, an Activity's `onNewIntent` — none can `suspend`. The state holder (ViewModel, Decompose Component, feature model, etc. — anything whose role is to absorb UI events and hold UI state) **is** the boundary that translates one-shot UI events into asynchronous work bound to the UI lifecycle. That's its job.

```kotlin
// ✅ GOOD — state holder absorbs a non-suspending UI event onto its scope
class FavouritesViewModel(private val repo: FavouritesRepository) : ViewModel() {
    fun onToggleFavourite(item: Item) {
        viewModelScope.launch { repo.toggleFavourite(item) }
    }
}

// in Compose:
ListItem(onClick = { viewModel.onToggleFavourite(item) })
```

This is **not** the fire-and-forget anti-pattern. All three conditions must hold:

1. **State holder for a UI surface** — a ViewModel, Decompose Component, feature model, or equivalent UI state holder. Not a repository, manager, use case, or data source.
2. **Lifecycle-bound scope** — `viewModelScope`, a Component's `coroutineScope` that's cancelled on destroy, a Composable's `rememberCoroutineScope()`. Not `AppScope`, not an injected long-lived scope, not an ad-hoc `CoroutineScope(...)`.
3. **Caller really is a UI event** — Composable callback, key handler, lifecycle hook. Not another business-logic class calling through the state holder.

The repository / use case / data source layers underneath still expose `suspend` APIs. The state holder is the *only* layer where the non-suspending → suspending translation belongs.

"It feels like a state holder" isn't enough. The question is "does the UI directly bind to this?" If no, the carve-out doesn't apply.

### 4. Stored scopes that aren't injected

The same anti-pattern, without an injected scope:

```kotlin
// ❌ BAD — same problem, scope is constructed in-class instead of injected
class FooManager {
    private val scope = MainScope()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
}
```

Lifecycle is now owned by nothing and lives forever. Replace with `suspend` APIs.

The same is true if the instantiation is nested inside a function body — `fun foo() { CoroutineScope(...).launch { … } }` is just a stored scope with extra steps. Each call leaks a new uncancellable scope; bundling it into a `by lazy` property doesn't fix the underlying issue (the scope shouldn't exist at all).

### 5. DI-bound singletons / initializers that launch

A specific pattern that is hard to spot: a DI-bound class (`@SingleIn(AppScope)`, `@Singleton`, an `Initializer.initialize()`) launches a coroutine from its constructor / `init` block / `initialize()`. The launched work then has:

- **A non-deterministic start time** — whenever the graph realizes the binding. Cold-start ordering is invisible.
- **No observable lifecycle.** Nothing else in the codebase can see whether it's running or has crashed.
- **No `stop()` / restart path.** If upstream enters a bad state, the loop is uncancellable.
- **No calling code to grep for.** Readers can't find "who starts this and when".

§1 says scopes should be tied to the caller's lifecycle. The DI-bound variant violates this indirectly: the *scope* may be injected, but the *launch* is hidden inside construction — same effect, harder to see.

```kotlin
// ❌ BAD — singleton boots work as a side effect of being constructed
@SingleIn(AppScope::class)
@Inject
class TokenRefresher(
    @ForScope(AppScope::class) private val scope: CoroutineScope,
    private val auth: AuthService,
) {
    init {
        scope.launch {
            while (isActive) {
                delay(5.minutes)
                auth.refreshIfNeeded()
            }
        }
    }
}

// ❌ ALSO BAD — Initializer.initialize() that *launches*, not just registers
class TokenInvalidatorInitializer @Inject constructor(
    @ForScope(AppScope::class) private val scope: CoroutineScope,
    private val store: AuthStore,
    private val invalidator: TokenInvalidator,
) : Initializer {
    override fun initialize() {
        scope.launch { store.tokenChanges.collect { invalidator.invalidate() } }
    }
}
```

Both look like "application-scoped singletons", but the **When NOT to apply** carve-out is *not* permission to launch from `init` / `initialize()`. It's permission for a singleton to own a scope when its API is suspending.

#### First ask: does this background-loop class need to exist at all?

Most background-loop classes exist only because no one inverted the observation. Three answers, in order of preference:

**Pattern 1 — invert into the consumer.** The class observes state forever to react when it changes. But *someone* mutates the state — sign-out flow, profile switch, flag-update handler. That mutation site is already in a coroutine context and is the natural place to do the work directly.

```kotlin
// ✅ GOOD — no background loop, no scope, no class. The mutation site does the work.
class Authenticator(
    private val authStore: AuthStore,
    private val tokenInvalidator: TokenInvalidator,
) {
    suspend fun signOut() {
        authStore.clearTokens()
        tokenInvalidator.invalidate()   // direct call at the mutation site
    }
}
```

The background-loop class is **deleted**. The work happens where the state changes.

When this applies: the consumer of the state has a clear lifecycle (a use case, an Authenticator, a service handler) and can perform the reaction inline.

**Pattern 2 — scheduled work.** Genuinely periodic or deferred. Use WorkManager / BGTaskScheduler. The enqueue is one-shot; make it suspending and call it once from an orchestrator that already runs at startup.

**Pattern 3 — explicit named launch site.** Sometimes the consumer is a synchronous API with no observable lifecycle (e.g., OpenTelemetry's `Sampler.shouldSample(...)`, an AIDL stub fanout, a broadcast receiver bridge). The observation has to live somewhere coroutine-aware, but it must live at an *explicit named call site* — not in the class's own `init`.

```kotlin
// ✅ GOOD — work is named; an explicit call site owns the launch
@SingleIn(AppScope::class)
class OtelConfigurableSampler(...) : Sampler {
    @Volatile private var delegate: Sampler = ...

    suspend fun observeRate(featureFlags: FeatureFlags) {
        featureFlags.observe(OTEL_SAMPLING_RATE).collect { rate ->
            delegate = Sampler.traceIdRatioBased(rate.coerceIn(0.0, 1.0))
        }
    }

    override fun shouldSample(...) = delegate.shouldSample(...)
}

// wired explicitly at the OTel SDK init module:
applicationScope.launch { otelSampler.observeRate(featureFlags) }
```

When this applies: the consumer is a synchronous API that calls *into* you with no observable lifecycle. The launch can't be invertible, but it must still be visible at a named call site.

#### Test for which pattern fits

"Is the consumer's lifecycle observable to me?"

- **Yes, and they're already in a coroutine context** → Pattern 1. Push the subscription into them; delete the background-loop class.
- **The work is periodic / deferred** → Pattern 2. Suspend enqueue called once.
- **No, they're a synchronous API with no observable lifecycle** → Pattern 3. Explicit launch site, not `init`.

If a fourth answer seems to fit — e.g., "I want a `Bootable` interface that launches everything for me" — that's the same anti-pattern with an extra layer of abstraction. The whole point is that launches be *visible*; auto-discovery by interface defeats it.

#### Initializers are still fine — *if they only register*

The `Initializer` pattern is correct when `initialize()` *registers* a listener or hook. The bug is when `initialize()` *launches* a coroutine.

```kotlin
// ✅ GOOD Initializer — registers a contributor, doesn't launch
class FavouritesContributorInitializer @Inject constructor(
    private val registry: ContributorRegistry,
    private val favouritesContributor: FavouritesContributor,
) : Initializer {
    override fun initialize() {
        registry.register(favouritesContributor)
    }
}
```

**`Initializer.initialize()` must not `launch` a coroutine.** If yours does, it's a Pattern 1/2/3 candidate.

#### Diagnostic for review

- Where is the start moment defined? If "wherever DI realizes me", bad.
- Who can observe whether the work is running? If "no one", bad.
- Who can stop or restart it? If "no one", bad.
- Can a reader grep for the launch site? If no, bad.

If the answers are "the consumer / the orchestrator / the named call site" — you're good.

### 6. Swallowing `CancellationException`

A `catch` clause around a `suspend` call that matches `CancellationException` — directly, or through `Exception` / `Throwable` — and doesn't rethrow usually turns cancellation into silent success. The parent coroutine thinks the child finished; the child keeps running (or its side effects do); the cancellation contract is broken.

Same failure shape as §1's stored-scope bug, viewed from the other end: §1 hides the work *from* the caller's lifecycle; this hides cancellation *from* the work.

```kotlin
// ❌ BAD — catches CancellationException, never rethrows
suspend fun fetch() {
    try {
        api.load()
    } catch (e: Exception) {           // matches CancellationException too
        logger.warn("load failed", e)
    }
}

// ❌ ALSO BAD — runCatching has the same problem
suspend fun fetch() {
    runCatching { api.load() }
        .onFailure { logger.warn("load failed", it) }
}
```

The acceptable shapes:

```kotlin
// ✅ Separate catch first
try { api.load() }
catch (e: CancellationException) { throw e }
catch (e: Exception) { logger.warn("load failed", e) }

// ✅ Conditional rethrow inside the broad catch
try { api.load() }
catch (e: Exception) {
    if (e is CancellationException) throw e
    logger.warn("load failed", e)
}

// ✅ ensureActive() — good when the catch handles ordinary failures and you only need
// to rethrow if the current coroutine is cancelled
try { api.load() }
catch (e: Exception) {
    currentCoroutineContext().ensureActive()
    logger.warn("load failed", e)
}

// ✅ runCatching with explicit guard
runCatching { api.load() }
    .onFailure {
        if (it is CancellationException) throw it
        logger.warn("load failed", it)
    }

// ✅ runCatching terminated with getOrThrow (cancellation flows back out)
runCatching { api.load() }.getOrThrow()
```

The trigger is "a suspend call inside the `try`", not "the enclosing function is declared `suspend`". This applies inside any suspending body — `suspend fun`, a `launch { … }` lambda, a Flow `collect { … }`, etc.

The common carve-out is an intentionally local timeout: catching `TimeoutCancellationException` from your own `withTimeout` and converting it to a domain result can be correct. Keep that catch narrow and close to the timeout. Do not use it as permission to swallow arbitrary cancellation.

Catching a non-cancellation subtype (`IOException`, your own exception types) is fine — they don't extend `CancellationException`.

### 7. `runBlocking`

`runBlocking` parks the current thread until the lambda finishes. Inside suspend-capable or lifecycle-scoped application paths it is wrong: a thread that meant to be async is now blocked, structured concurrency is broken, and any cancellation upstream has no effect. It is the "callee makes a structural decision for the caller" anti-pattern at its most direct.

```kotlin
// ❌ BAD — bridging to suspend by blocking the calling thread
fun saveUser(user: User) {
    runBlocking { repository.save(user) }
}
```

Three fixes, by context:

**Suspend-capable application code** — make the function `suspend`:

```kotlin
// ✅ GOOD
suspend fun saveUser(user: User) = repository.save(user)
```

If the immediate caller can't suspend either (a non-suspending UI callback, a `BroadcastReceiver` hook), use the existing lifecycle-bound scope at the boundary — see §3's UI ↔ state-holder carve-out. The fix is at the boundary, not inside `saveUser`.

Legitimate blocking boundaries exist: `main` in a CLI tool, Java interop APIs that must return synchronously, framework callbacks with no suspending alternative, and migration shims. Keep `runBlocking` at that outer boundary, keep the body small, and call suspending code immediately.

**Tests** — use `runTest`:

```kotlin
// ❌ BAD — real time, slow tests, no virtual delay
@Test fun loadsUser() = runBlocking {
    assertThat(repository.load().name).isEqualTo("Alice")
}

// ✅ GOOD
@Test fun loadsUser() = runTest {
    assertThat(repository.load().name).isEqualTo("Alice")
}
```

`runTest` gives you virtual time (`delay()` returns immediately), `TestDispatcher` integration, and proper coroutine cleanup. Real-time `runBlocking` in tests makes them slow and flaky.

**`ContentProvider` carve-out** — Android's `ContentProvider` methods (`query`, `insert`, `update`, `delete`, `onCreate`, `call`) are synchronous from outside the process. There is no way to suspend them. Inside *member functions* of a `ContentProvider` subclass (direct or indirect — not companion objects), `runBlocking` is the unavoidable bridge. Keep the body as short as possible and call into suspending code immediately:

```kotlin
// ✅ Acceptable in ContentProvider members only
class MyProvider : ContentProvider() {
    override fun query(...): Cursor? = runBlocking { dao.query(...) }
}
```

This carve-out is for `android.content.ContentProvider` subclasses *only*. "It's like a `ContentProvider`" doesn't apply, and a `runBlocking` in a `ContentProvider`'s companion object is still a regular violation — the helper isn't part of the framework's synchronous surface.

## Quick reference

| Symptom | Anti-pattern | Fix |
|---|---|---|
| Class has `private val scope: CoroutineScope` | Stored scope on the callee | Remove. Make public APIs `suspend`. |
| `init { scope.launch { ... } }` | Construction-time launch | Move to `suspend fun init()` / `login()` |
| `fun foo() { scope.launch { ... } }` on a repository/manager/use case | Fire-and-forget from non-UI class | `suspend fun foo()`, let UI state holder pick the scope |
| `fun onClick() { viewModelScope.launch { ... } }` on a state holder, called from UI | UI ↔ state-holder boundary — fine | Keep as-is (see §3 carve-out) |
| `private val scope = MainScope()` | Internally-constructed stored scope | Same — remove, make APIs `suspend` |
| `@SingleIn(AppScope) class X(scope) { init { scope.launch { … } } }` | DI-bound opaque launch (§5) | Expose `suspend fun run()`, launch from startup orchestrator |
| `class Y : Initializer { override fun initialize() { scope.launch { … } } }` | Initializer that launches, not registers (§5) | Same — `suspend fun run()`, orchestrator owns lifecycle |
| `try { suspendCall() } catch (e: Exception\|Throwable\|CancellationException) { … }` with no rethrow | Swallowed cancellation (§6) | Prefer `catch (e: CancellationException) { throw e }`; use `ensureActive()` only when that matches the intent |
| `runCatching { suspendCall() }.onFailure { … }` with no cancellation guard | Same shape as above (§6) | Add `if (it is CancellationException) throw it`, or terminate with `.getOrThrow()` |
| `runBlocking { … }` inside suspend-capable app code | Thread-blocking bridge (§7) | Make caller `suspend`; or use a lifecycle scope at the boundary |
| `runBlocking { … }` in a test | Same — real-time bridging (§7) | Use `runTest { … }` |
| `runBlocking { … }` inside a `ContentProvider.query`/`insert`/… member | Carve-out (§7) | Acceptable; keep the body minimal |

## Refactoring guidance

Removing an existing offender:

1. **Start at the leaf.** Pick the class farthest from any UI — usually a repository or data source. Its public surface should be the easiest to convert.
2. **Convert public functions to `suspend`** one at a time. The compiler will surface every caller.
3. **At each caller, choose the scope deliberately:** `viewModelScope`, `lifecycleScope`, `coroutineScope { }`, or an explicit job. This is the choice that was missing before.
4. **Delete the `CoroutineScope` constructor parameter** once nothing uses it. Remove the injection binding.

Don't try to fix every class in one MR. Removing an anti-pattern is incremental work.

## When NOT to apply

- **UI state holders absorbing UI events.** A ViewModel/Component/feature model with `fun onClick(...) { viewModelScope.launch { ... } }` is correct — that's the boundary the framework needs. See §3 carve-out.
- **Lifecycle owners with explicit cancellation and error policy.** Actors/services, app infrastructure, or application-scoped singletons may own a scope when they expose clear `close`/`cancel`/restart behavior or otherwise map directly to an application lifecycle. Inject `Application.applicationScope` explicitly rather than creating one ad-hoc. **This is not permission to launch from `init` / `initialize()`** — see §5.
- **Already-suspending APIs** don't need any of this work.
- **Tests** sometimes use `TestScope` as a deliberate ambient scope — that's a different pattern with explicit virtual-time control.

## Red flags during review

These thoughts mean the anti-pattern is back:

| Thought | Reality |
|---|---|
| "I'll just add a `CoroutineExceptionHandler` to the scope" | The problem isn't error handling. The problem is the scope shouldn't exist. |
| "I need to launch from `init` so the data's ready when consumers arrive" | Consumers reading state that isn't ready is the bug. Use phasing. |
| "The caller doesn't want to deal with `suspend`" | Then the caller chooses fire-and-forget at their scope. Don't decide for them. |
| "It's just a small fire-and-forget call" | Silent cancellation makes every fire-and-forget a potential silent failure. |
| "We caught and logged the exception, so we're fine" | Did the catch rethrow `CancellationException`? If no, the coroutine is silently un-cancelled. (§6) |
| "It's just one `runBlocking`, in a non-critical path" | Every `runBlocking` asserts the caller has no async option. If they do, it's the wrong primitive. (§7) |
| "Tests are simpler with `runBlocking`" | They run in real time, can't fast-forward `delay`, and lose `TestDispatcher` semantics. Use `runTest`. (§7) |

## Related

- [`kotlin-flow-state-event-modeling`](../kotlin-flow-state-event-modeling/SKILL.md) — `StateFlow`, `SharedFlow`, `Channel`, `stateIn`, one-shot events, and related modeling.
- [`kotlin-coroutines`](../kotlin-coroutines/SKILL.md) — Amethyst's relay-pool / callbackFlow / testing async patterns.
