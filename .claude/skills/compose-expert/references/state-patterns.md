# Compose State Management Patterns

Visual guide to state management in Compose Multiplatform. For Kotlin-specific patterns (StateFlow, sealed classes), see `kotlin-expert` skill.

## Core State Functions

### remember

Cache values across recompositions:

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }

    Button(onClick = { count++ }) {
        Text("Clicked $count times")
    }
}
```

**When to use**: Simple UI state (toggles, counters, text input)
**Visual pattern**: Button press → state changes → UI updates

### derivedStateOf

Compute state from other state, recompose only when result changes:

```kotlin
@Composable
fun ScrollToTopButton(listState: LazyListState) {
    // Only recomposes when showButton value changes (not every scroll pixel)
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    if (showButton) {
        FloatingActionButton(onClick = { /* scroll to top */ }) {
            Icon(Icons.Default.ArrowUpward, null)
        }
    }
}
```

**When to use**: Input state changes frequently, but derived result changes rarely
**Visual pattern**: Scroll position (0, 1, 2...) → boolean (show/hide) → FAB visibility
**Performance**: Prevents recomposition on every scroll event

### produceState

Convert non-Compose state into Compose state:

```kotlin
@Composable
fun LoadUserProfile(userId: String): State<User?> {
    return produceState<User?>(initialValue = null, userId) {
        value = repository.fetchUser(userId)
    }
}

@Composable
fun ProfileScreen(userId: String) {
    val user by LoadUserProfile(userId)

    when (user) {
        null -> LoadingState("Loading profile...")
        else -> ProfileCard(user!!)
    }
}
```

**When to use**: Convert Flow, LiveData, callbacks into Compose state
**Visual pattern**: Async operation → state updates → UI reflects changes
**Lifecycle**: Coroutine cancelled when composable leaves composition

## State Hoisting Pattern

Move state up to make composables reusable and testable:

### Before (Stateful)
```kotlin
@Composable
fun SearchBar() {
    var query by remember { mutableStateOf("") }

    TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search...") }
    )
}
```
❌ Hard to test, can't control state externally

### After (Stateless)
```kotlin
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search...") },
        modifier = modifier
    )
}

@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }

    Column {
        SearchBar(query = query, onQueryChange = { query = it })
        SearchResults(query = query)
    }
}
```
✅ Reusable, testable, state controlled by parent

**Hoisting principle**: State goes up, events go down
- State: `query: String` (read-only)
- Events: `onQueryChange: (String) -> Unit` (write-only)

## Amethyst State Patterns

### Theme-Aware State

```kotlin
@Composable
private fun isLightTheme(): Boolean {
    val background = MaterialTheme.colorScheme.background
    return (background.red + background.green + background.blue) / 3 > 0.5f
}

@Composable
fun ThemedContent() {
    val isDark = !isLightTheme()
    // Adjust visuals based on theme
    val iconTint = if (isDark) Color.White else Color.Black
}
```

**Pattern**: Derive state from MaterialTheme
**Visual**: Component adapts to light/dark theme automatically

### Relay Status State

```kotlin
@Composable
fun RelayStatusIndicator(
    connectedCount: Int,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        connectedCount == 0 -> RelayStatusColors.Disconnected
        connectedCount < 3 -> RelayStatusColors.Connecting
        else -> RelayStatusColors.Connected
    }

    Icon(
        imageVector = if (connectedCount > 0) Icons.Default.Check else Icons.Default.Close,
        tint = statusColor
    )
}
```

**Pattern**: Visual state derived from domain state
**Visual mapping**:
- 0 relays → Red + X icon
- 1-2 relays → Yellow + Check icon
- 3+ relays → Green + Check icon

### Loading/Empty/Error States

```kotlin
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState) {
        is UiState.Loading -> LoadingState("Loading feed...")
        is UiState.Empty -> FeedEmptyState(onRefresh = { viewModel.refresh() })
        is UiState.Error -> FeedErrorState(
            errorMessage = uiState.message,
            onRetry = { viewModel.retry() }
        )
        is UiState.Success -> LazyColumn {
            items(uiState.items) { FeedItem(it) }
        }
    }
}
```

**Pattern**: Sealed class → visual state component
**Components**:
- `LoadingState` - Progress indicator
- `EmptyState` - Empty message + refresh
- `ErrorState` - Error message + retry
- Success - Actual content

## Common Patterns

### Toggle State
```kotlin
var isExpanded by remember { mutableStateOf(false) }

IconButton(onClick = { isExpanded = !isExpanded }) {
    Icon(
        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = if (isExpanded) "Collapse" else "Expand"
    )
}

if (isExpanded) {
    Text("Expanded content...")
}
```

### List State with Actions
```kotlin
var items by remember { mutableStateOf(listOf("Item 1", "Item 2")) }

Column {
    AddButton(onClick = {
        items = items + "Item ${items.size + 1}"
    })

    items.forEachIndexed { index, item ->
        Row {
            Text(item)
            RemoveButton(onClick = {
                items = items.filterIndexed { i, _ -> i != index }
            })
        }
    }
}
```

### TextField State
```kotlin
var text by remember { mutableStateOf("") }

TextField(
    value = text,
    onValueChange = { text = it },
    label = { Text("Enter text") }
)
```

## Performance Patterns

### Avoid Unnecessary Recomposition
```kotlin
// ❌ Bad: Recomposes on every scroll position change
@Composable
fun BadScrollButton(scrollState: ScrollState) {
    if (scrollState.value > 100) {  // scrollState.value changes constantly
        Button(onClick = { /* ... */ }) { Text("Scroll to Top") }
    }
}

// ✅ Good: Only recomposes when visibility changes
@Composable
fun GoodScrollButton(scrollState: ScrollState) {
    val showButton by remember {
        derivedStateOf { scrollState.value > 100 }
    }

    if (showButton) {
        Button(onClick = { /* ... */ }) { Text("Scroll to Top") }
    }
}
```

### Stable Parameters
Use `@Immutable` data classes (see `kotlin-expert`) to prevent recomposition:

```kotlin
@Immutable
data class UserProfile(val name: String, val avatar: String)

@Composable
fun ProfileCard(profile: UserProfile) {
    // Only recomposes when profile instance changes
    Row {
        RobohashImage(robot = profile.avatar)
        Text(profile.name)
    }
}
```

## Integration with Kotlin State

For ViewModel state, Flow, StateFlow → See `kotlin-expert` skill

Common integration pattern:
```kotlin
// ViewModel (Kotlin state)
class FeedViewModel {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
}

// Composable (Compose state)
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    // Use uiState to render UI
}
```

## Quick Reference

| Function | Use Case | Recomposes When |
|----------|----------|----------------|
| `remember { mutableStateOf() }` | Local UI state | State value changes |
| `derivedStateOf { }` | Computed state | Derived result changes |
| `produceState { }` | Async/Flow → State | Async operation updates value |
| `collectAsState()` | Flow → State | Flow emits new value |
| State hoisting | Reusable components | Parent passes new state |

## Sources

State management patterns based on:
- [State and Jetpack Compose - Android Developers](https://developer.android.com/develop/ui/compose/state)
- [When should I use derivedStateOf?](https://medium.com/androiddevelopers/jetpack-compose-when-should-i-use-derivedstateof-63ce7954c11b)
- [Advanced State and Side Effects](https://developer.android.com/codelabs/jetpack-compose-advanced-state-side-effects)
- AmethystMultiplatform codebase patterns (2025)
