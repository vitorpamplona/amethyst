# Flow Patterns in Amethyst

StateFlow and SharedFlow usage patterns from the codebase.

## Table of Contents
- [StateFlow for State Management](#stateflow-for-state-management)
- [Flow Composition](#flow-composition)
- [Common Patterns](#common-patterns)
- [Anti-Patterns](#anti-patterns)

---

## StateFlow for State Management

### AccountManager Pattern

**File:** `commons/src/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/account/AccountManager.kt:36-115`

```kotlin
sealed class AccountState {
    data object LoggedOut : AccountState()

    data class LoggedIn(
        val signer: NostrSigner,
        val pubKeyHex: String,
        val npub: String,
        val nsec: String?,
        val isReadOnly: Boolean,
    ) : AccountState()
}

class AccountManager {
    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    fun generateNewAccount(): AccountState.LoggedIn {
        val keyPair = KeyPair()
        val signer = NostrSignerInternal(keyPair)

        val state = AccountState.LoggedIn(
            signer = signer,
            pubKeyHex = keyPair.pubKey.toHexKey(),
            npub = keyPair.pubKey.toNpub(),
            nsec = keyPair.privKey?.toNsec(),
            isReadOnly = false
        )
        _accountState.value = state  // Update state
        return state
    }

    fun loginWithKey(keyInput: String): Result<AccountState.LoggedIn> {
        // ... validation ...

        val state = AccountState.LoggedIn(...)
        _accountState.value = state
        return Result.success(state)
    }

    fun logout() {
        _accountState.value = AccountState.LoggedOut
    }
}
```

**Pattern highlights:**
- Private `MutableStateFlow` for internal mutations
- Public `StateFlow` via `.asStateFlow()` for read-only access
- Sealed class for type-safe state variants
- Initial value required (`AccountState.LoggedOut`)

### RelayConnectionManager Pattern

**File:** `commons/src/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/network/RelayConnectionManager.kt:44-80`

```kotlin
data class RelayStatus(
    val url: NormalizedRelayUrl,
    val connected: Boolean,
    val error: String? = null,
    val messageCount: Int = 0
)

open class RelayConnectionManager(
    websocketBuilder: WebsocketBuilder
) : IRelayClientListener {
    private val client = NostrClient(websocketBuilder)

    // Map of relay URLs to their status
    private val _relayStatuses = MutableStateFlow<Map<NormalizedRelayUrl, RelayStatus>>(emptyMap())
    val relayStatuses: StateFlow<Map<NormalizedRelayUrl, RelayStatus>> = _relayStatuses.asStateFlow()

    // Delegated StateFlows from client
    val connectedRelays: StateFlow<Set<NormalizedRelayUrl>> = client.connectedRelaysFlow()
    val availableRelays: StateFlow<Set<NormalizedRelayUrl>> = client.availableRelaysFlow()

    fun addRelay(url: String): NormalizedRelayUrl? {
        val normalized = RelayUrlNormalizer.normalizeOrNull(url) ?: return null
        updateRelayStatus(normalized) { it.copy(connected = false, error = null) }
        return normalized
    }

    fun removeRelay(url: NormalizedRelayUrl) {
        _relayStatuses.value = _relayStatuses.value - url  // Immutable update (remove from map)
    }

    private fun updateRelayStatus(
        relay: NormalizedRelayUrl,
        update: (RelayStatus) -> RelayStatus
    ) {
        _relayStatuses.value = _relayStatuses.value.toMutableMap().apply {
            val current = get(relay) ?: RelayStatus(relay, false)
            put(relay, update(current))
        }
    }

    // IRelayClientListener implementation
    override fun onConnect(relay: NormalizedRelayUrl) {
        updateRelayStatus(relay) { it.copy(connected = true, error = null) }
    }

    override fun onError(relay: NormalizedRelayUrl, error: String) {
        updateRelayStatus(relay) { it.copy(connected = false, error = error) }
    }
}
```

**Pattern highlights:**
- `Map` as state value for collection tracking
- Immutable map updates (copy with modifications)
- Helper function `updateRelayStatus` for consistent updates
- Delegation pattern (client exposes its own StateFlows)

---

## Flow Composition

### Multiple StateFlows in UI

**Pattern:**

```kotlin
@Composable
fun LoginScreen(accountManager: AccountManager) {
    val accountState by accountManager.accountState.collectAsState()

    when (accountState) {
        is AccountState.LoggedOut -> {
            LoginForm(onLogin = { key -> accountManager.loginWithKey(key) })
        }
        is AccountState.LoggedIn -> {
            MainApp(account = accountState as AccountState.LoggedIn)
        }
    }
}
```

### Observing Multiple Flows

**Pattern:**

```kotlin
@Composable
fun RelayStatusCard(relayManager: RelayConnectionManager) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()

    Column {
        Text("${connectedRelays.size} of ${relayStatuses.size} relays connected")

        relayStatuses.forEach { (url, status) ->
            RelayRow(
                url = url,
                connected = status.connected,
                error = status.error
            )
        }
    }
}
```

---

## Common Patterns

### Pattern: Immutable State Updates

```kotlin
// Map updates
_relayStatuses.value = _relayStatuses.value + (url to newStatus)  // Add
_relayStatuses.value = _relayStatuses.value - url  // Remove
_relayStatuses.value = _relayStatuses.value.mapValues { (key, value) ->
    if (key == targetUrl) value.copy(connected = true) else value
}

// List updates
_items.value = _items.value + newItem  // Append
_items.value = _items.value.filter { it.id != removedId }  // Remove
_items.value = _items.value.map { if (it.id == id) it.copy(name = newName) else it }  // Update

// Object updates
_user.value = _user.value.copy(name = newName)
```

### Pattern: Conditional State Transitions

```kotlin
fun attemptLogin(credentials: Credentials) {
    if (_loginState.value is LoginState.LoggingIn) {
        return  // Already logging in, ignore
    }

    _loginState.value = LoginState.LoggingIn
    viewModelScope.launch {
        try {
            val user = repository.login(credentials)
            _loginState.value = LoginState.Success(user)
        } catch (e: Exception) {
            _loginState.value = LoginState.Error(e.message ?: "Login failed")
        }
    }
}
```

### Pattern: Derived State

```kotlin
class MyViewModel {
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    // Derived state (computed from items)
    val itemCount: StateFlow<Int> = items.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val hasItems: StateFlow<Boolean> = items.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
}

// Usage in Compose
@Composable
fun ItemList(viewModel: MyViewModel) {
    val itemCount by viewModel.itemCount.collectAsState()
    val hasItems by viewModel.hasItems.collectAsState()

    if (hasItems) {
        Text("$itemCount items")
    } else {
        Text("No items")
    }
}
```

### Pattern: State with Loading/Error

```kotlin
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class FeedViewModel {
    private val _feedState = MutableStateFlow<UiState<List<Event>>>(UiState.Loading)
    val feedState: StateFlow<UiState<List<Event>>> = _feedState.asStateFlow()

    fun loadFeed() {
        viewModelScope.launch {
            _feedState.value = UiState.Loading
            try {
                val events = repository.getEvents()
                _feedState.value = UiState.Success(events)
            } catch (e: Exception) {
                _feedState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

// UI
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val state by viewModel.feedState.collectAsState()

    when (state) {
        is UiState.Loading -> LoadingSpinner()
        is UiState.Success -> EventList((state as UiState.Success).data)
        is UiState.Error -> ErrorMessage((state as UiState.Error).message)
    }
}
```

---

## Anti-Patterns

### ❌ Exposing Mutable State

```kotlin
// BAD: External code can mutate
class BadViewModel {
    val state: MutableStateFlow<State> = MutableStateFlow(State.Initial)
}

// Caller can do:
viewModel.state.value = State.Hacked  // Bypass internal logic!
```

### ✅ Expose Immutable

```kotlin
// GOOD: Only ViewModel can mutate
class GoodViewModel {
    private val _state = MutableStateFlow(State.Initial)
    val state: StateFlow<State> = _state.asStateFlow()

    fun updateState(newState: State) {
        // Controlled mutation with validation
        _state.value = newState
    }
}
```

---

### ❌ Not Using Immutable Updates

```kotlin
// BAD: Mutating collection doesn't trigger StateFlow update
val list = mutableListOf<Item>()
list.add(newItem)
_items.value = list  // Same reference, no update emitted!
```

### ✅ Create New Instance

```kotlin
// GOOD: New list instance
_items.value = _items.value + newItem  // New list created, update emitted
```

---

### ❌ StateFlow for Events

```kotlin
// BAD: Events get lost if no collector
class BadViewModel {
    val navigationEvent: StateFlow<NavEvent?> = MutableStateFlow(null)

    fun navigate(event: NavEvent) {
        _navigationEvent.value = event  // Lost if UI not observing!
    }
}
```

### ✅ SharedFlow for Events

```kotlin
// GOOD: Events queued
class GoodViewModel {
    private val _navigationEvent = MutableSharedFlow<NavEvent>(replay = 0)
    val navigationEvent: SharedFlow<NavEvent> = _navigationEvent.asSharedFlow()

    fun navigate(event: NavEvent) {
        viewModelScope.launch {
            _navigationEvent.emit(event)  // Queued for collector
        }
    }
}
```

---

### ❌ Blocking Operations in State Update

```kotlin
// BAD: Blocking main thread
fun loadData() {
    _state.value = fetchDataFromNetwork()  // Blocks!
}
```

### ✅ Async Updates

```kotlin
// GOOD: Use coroutines
fun loadData() {
    viewModelScope.launch {
        _state.value = UiState.Loading
        val data = withContext(Dispatchers.IO) {
            fetchDataFromNetwork()
        }
        _state.value = UiState.Success(data)
    }
}
```

---

## References

- AccountManager.kt:36-115
- RelayConnectionManager.kt:44-80
- [StateFlow and SharedFlow | Android Developers](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Hot vs Cold Flows](https://carrion.dev/en/posts/kotlin-flows-hot-cold/)
