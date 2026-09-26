# Moving ViewModels from `amethyst` to `commons`

_Status: proposal / study. 2026-09-26._

Companion to `2026-05-30-amethyst-to-commons-migration.md` and
`2026-08-30-commons-migration-sweep.md`, which inventory *what* to move. This
doc answers *how* a ViewModel should move: as an `androidx.lifecycle.ViewModel`
subclass, or as a plain class in `commons` that a thin ViewModel holds.

## TL;DR

1. **KMP ViewModels already work — the base class is not the blocker.**
   `commons` depends on `androidx.lifecycle:lifecycle-viewmodel` 2.11.0 in
   `commonMain` (`commons/build.gradle.kts:81`), and 7 ViewModels already live
   there (`FeedViewModel`, `ListChangeFeedViewModel`, `ChatroomFeedViewModel`,
   `LiveStreamTopZappersViewModel`, `PollResultsViewModel`, `NestViewModel`,
   `GitRepositoryBrowserViewModel`). 105 of the 138 app-side ViewModels have
   **zero** `android.*` imports.
2. **What actually blocks them is their dependencies**: the concrete `Account`,
   `AccountViewModel`, the `LocalCache` object, the `Amethyst.instance` service
   locator, `R.string`, `Context`/`Uri`. Wrapping doesn't remove any of those —
   the inner class must be free of them too. So the wrapper is not a shortcut
   past the real work.
3. **Your instinct is still right**, for different reasons: put the logic in a
   plain, scope-injected class in `commons` and make the platform ViewModel a
   holder. It gives the CLI and tests a class without ViewModel semantics,
   fixes Desktop's broken ViewModel lifecycle, replaces the `init(accountViewModel)`
   setter-injection pattern with constructor injection, and deletes ~46
   hand-written `Factory` classes.
4. **But don't write pass-through methods.** The ViewModel exposes the holder
   rather than re-declaring its API. `FeedViewModel` already does this
   (`val feedState = FeedContentState(filter, viewModelScope, cache)`). Better
   still, one generic `StateHolderViewModel<T>` in `commonsUI` means most screens
   have **no per-screen ViewModel class at all**.

## Survey (2026-09-26, `main` @ 9fba8965)

138 files in `amethyst/` import `androidx.lifecycle.ViewModel`:

| Signal | Files | What it means for the move |
|---|---|---|
| No `android.*` import | 105 | Platform isn't the problem. |
| Use `viewModelScope` | 74 | Needs a scope → inject `CoroutineScope`. |
| `override fun onCleared` | 24 | Needs teardown → scope cancellation / `AutoCloseable`. |
| `SavedStateHandle` | **0** | Nothing tied to Android process-death restore; a plain class loses nothing. |
| Hand-written `ViewModelProvider.Factory` | 46 | Boilerplate that exists only to pass constructor args. |
| `lateinit var account` + `init(accountViewModel)` | 52 | Setter injection because `viewModel()` without a factory can't take args. Not portable. |
| Reference `AccountViewModel` | 66 | Must become narrow interfaces (`IAccount`, `ICacheProvider`, ports). |
| `Amethyst.instance` | 27 | Service locator — inject instead. |
| `import android.content.Context` | 33 | Mostly composers/uploads. |
| `stringRes`/`R.string`/toasts | 16 | `commons` has no `Res`; emit typed errors, map in UI. |
| `mutableStateOf`/`mutableStateListOf` | 59 | Allowed in `commons` (Compose *runtime*). |
| `TextFieldValue`/`TextFieldState` | 28 | Compose *foundation* → these holders go to `commonsUI`, per `ARCHITECTURE.md`. |
| Under `**/dal/` (thin feed VMs) | 39 | Already `FeedViewModel` subclasses; blocked only on their `FeedFilter`s. |

Rough families:

- **Thin feed VMs (~39, 37–60 lines each)** — e.g. `HashtagFeedViewModel` is a
  `Factory` plus `AndroidFeedViewModel(HashtagFeedFilter(…, account, LocalCache))`.
  The VM is trivial; the filter's dependency on `Account`/`LocalCache` is what
  blocks it (sweep plan: 106 of 109 feed filters block on `Account` alone).
- **Editors / settings (~40)** — 17 `*RelayListViewModel`s (Desktop hand-rolls 7
  of these editors in `desktopApp/ui/relay/`), 6 near-identical
  `*MetadataViewModel`s, wallet/Cashu, Blossom, NWC, badges, lists. Medium
  coupling: `AccountViewModel` + `Amethyst.instance` + a few strings.
- **Composers (~20)** — `ShortNotePostViewModel` (2,119 lines), `CommentPost`,
  `ChatNewMessage`, `ChannelNewMessage`, `LongFormPost`, `NewProduct`, music/
  podcast/HLS authoring. `Context` + uploads + location + `TextFieldValue`.
  Last wave.
- **Misplaced** — six "ViewModels" are declared inside screen files
  (`GitRepositoryScreen.kt`, `ZapCustomDialog.kt`, `UpdateReactionTypeDialog.kt`,
  `DisplayReward.kt`, `ImportFollowListSelectUserScreen.kt`,
  `LiveStreamTopZappers.kt`). Split out first.
- **`AccountViewModel` (3,302 lines)** — not a ViewModel to move; it is the
  god-object the sweep plan's Wave 4 decomposes. Its pieces become holders.

## Why not just subclass `ViewModel` in `commons`?

It works today and is a legitimate option. The reasons to prefer a plain holder:

1. **Desktop cannot manage a ViewModel's lifecycle.** `ViewModel.clear()` is
   `internal` (lifecycle 2.11 `commonMain/ViewModel.kt:168`); only
   `ViewModelStore.clear()` is public, and Desktop uses no `ViewModelStore`.
   It builds ViewModels with `remember { … }` — which the constructor's KDoc
   explicitly forbids ("never manually create a ViewModel outside of a
   `ViewModelProvider.Factory`") — and so:
   - `DesktopFeedViewModel.destroy()` hand-cancels `viewModelScope` from a
     `DisposableEffect`; `onCleared()` and `addCloseable` resources never run.
   - `DesktopMessagesScreen.kt:271` and `:368` `remember` a
     `ChatroomFeedViewModel` with no dispose at all. Its init-time collectors on
     the cache event stream (`ListChangeFeedViewModel` `init {}`) are never
     cancelled, so every room switch appears to leak one. (Found by reading;
     not yet reproduced in a test.)
2. **The CLI and services want the logic, not the lifecycle.** A holder taking
   `(scope, deps)` is directly usable from `amy` (`coroutineScope { Holder(this, …) }`),
   from another holder, or from a background service. A ViewModel is not
   supposed to be constructed outside a provider.
3. **Constructor injection.** `viewModel()` with no factory can't take args,
   which is why 52 VMs use `lateinit var account` + `init(accountViewModel)`.
   A holder always takes its deps in the constructor; the generic holder VM
   below captures them in a lambda, so no `Factory` class is needed.
4. **Tests.** `Holder(backgroundScope, fakes)` inside `runTest` in
   `commons/jvmTest` — no `Dispatchers.setMain`, no provider.
   (The `ViewModel(viewModelScope: CoroutineScope)` constructor in 2.11 would
   also make a ViewModel testable; it doesn't solve 1–3.)

Nothing is lost: no VM uses `SavedStateHandle`, and on Android the holder
still survives configuration changes because its owning ViewModel does.

## Proposed shape

### 1. The holder (in `commons`, or `commonsUI` if it holds a text-field type)

```kotlin
// commons/.../viewmodels/relays/Nip65RelayEditorState.kt
class Nip65RelayEditorState(
    private val scope: CoroutineScope,      // owned by the caller; never created or cancelled here
    private val account: IAccount,          // or narrower ports, never Account/AccountViewModel
    private val client: INostrClient,
    private val nip11: Nip11Fetcher,        // instead of Amethyst.instance.nip11Cache
) {
    val homeRelays: StateFlow<List<BasicRelaySetupInfo>>
    val errors: SharedFlow<RelayEditorError> // typed; UI maps to Res/R strings
    fun load() { … }
    fun addHomeRelay(url: NormalizedRelayUrl) { … }
    fun save() = scope.launch(Dispatchers.IO) { … }
}
```

Rules:

- **Constructor-inject everything.** No `Amethyst.instance`, no `LocalCache`
  object, no `AccountViewModel`, no `Account`; take `IAccount`/`ICacheProvider`
  or a smaller interface. Where `IAccount` lacks something, add it there (that
  *is* the Wave 4 work, done one consumer at a time).
- **Take a `CoroutineScope`; never create or cancel one.** Teardown beyond
  scope cancellation → implement `AutoCloseable` and let the owner close it
  (the VM passes it to `addCloseable`).
- **No strings, no `Context`, no `Uri`.** Emit sealed errors/events; accept
  platform ports (e.g. a media-source interface) for composers.
- **Expose state, don't hide it behind the VM.** `StateFlow` preferred for
  anything the CLI may read; snapshot state is allowed.
- **Naming:** keep the `*State` suffix already used in `commons/viewmodels`
  (`SearchBarState`, `RoomPresenceState`, `ChatroomListState`), qualified to
  avoid collisions with account-state classes in `commons/model`
  (`Nip65RelayListState` is the account's list → `Nip65RelayEditorState`).

### 2. One generic holder ViewModel (in `commonsUI`)

```kotlin
// commonsUI/.../viewmodels/StateHolderViewModel.kt
class StateHolderViewModel<T : Any>(create: (CoroutineScope) -> T) : ViewModel() {
    val holder: T = create(viewModelScope)
    init { (holder as? AutoCloseable)?.let { addCloseable(it) } }
}

@Composable
inline fun <reified T : Any> rememberStateHolder(
    key: String? = null,
    noinline create: (CoroutineScope) -> T,
): T =
    viewModel(key = "${T::class.qualifiedName}:${key.orEmpty()}") {
        StateHolderViewModel(create)
    }.holder
```

Call site, replacing a VM class + `Factory` + `init(accountViewModel)`:

```kotlin
val editor = rememberStateHolder { scope ->
    Nip65RelayEditorState(scope, account, client, nip11)
}
```

**Gotcha this handles:** the provider's default key is the ViewModel class's
canonical name (`ViewModelProviders.getDefaultKey`). Every holder shares the
erased class `StateHolderViewModel`, so without folding `T` into the key two
holders on one screen would collide and the second call would get the first
holder (→ `ClassCastException`). As with today's `Factory`s, `create` runs once
per key: if its arguments can change, put them in `key`.

Keep a dedicated `ViewModel` subclass only if something really needs
ViewModel-only API; the survey found nothing that does.

### 3. Desktop gets a real `ViewModelStore`

Give each Desktop navigation entry / deck column a `ViewModelStoreOwner`
(a `ViewModelStore` provided through `LocalViewModelStoreOwner`, cleared in
`onDispose`). Then `rememberStateHolder` behaves the same on both platforms,
shared screens in `commonsUI` can call it without `expect`/`actual`, and
`DesktopFeedViewModel.destroy()` plus the undisposed `remember { …ViewModel(…) }`
sites go away. `desktopApp` already depends on `lifecycle-viewmodel-compose`.

## Migration order

0. **Infrastructure + pilots.**
   - Add `StateHolderViewModel`/`rememberStateHolder` to `commonsUI`; add the
     Desktop per-entry `ViewModelStoreOwner`; fix the `ChatroomFeedViewModel`
     leak (write the failing test first).
   - Pilot 1: `ChessViewModelNew` — 0 `android.*` imports, 2 account refs, and
     Desktop's `DesktopChessViewModelNew` is a line-for-line copy, so the pilot
     deletes a duplicate.
   - Pilot 2: the `*RelayListViewModel` family (12 extend `BasicRelaySetupInfoModel`)
     — validates the rules on `init(accountViewModel)` + `Amethyst.instance` +
     strings, and replaces Desktop's seven hand-rolled relay editors.
1. **Editors/settings** — the 6 `*MetadataViewModel`s (one shared error enum),
   wallet/Cashu, Blossom, NWC, payment targets, `SearchBarViewModel` (route
   building → callback).
2. **Thin feed VMs** — move each `FeedFilter` as `IAccount` grows; the VM then
   collapses to `rememberStateHolder { FeedContentState(…) }` (or a
   `rememberFeed(filter)` helper) and its `Factory` is deleted. Optionally turn
   the commons `FeedViewModel` family into holders the same way.
3. **Composers** — split into draft state (`commonsUI`, text-field types) +
   send logic (`commons`) + an Android upload/`Context` adapter.
4. **`AccountViewModel`** — shrink by peeling off holders; it stays in
   `amethyst` as the Android glue that remains.

Each step: move the holder, point Android and Desktop at it, delete the old
class and `Factory`, add a `commons/jvmTest` test driving the holder with fakes.

## Open questions

- Does `IAccount` grow per consumer (incremental), or is the Wave 4
  `Account` extraction done first? This plan assumes incremental.
- Desktop owner granularity: per deck column, or per navigation entry within a
  column? Per entry matches Android's back-stack semantics.
- Should `amy` get a helper that runs a holder to completion and prints its
  state, or keep driving the lower layers directly (current `amy-expert`
  guidance)?
