---
name: compose-expert
description: Advanced Compose Multiplatform UI patterns for shared composables. Use when working with visual UI components, state management patterns (remember, derivedStateOf, produceState), recomposition optimization (@Stable/@Immutable visual usage), Material3 theming, custom ImageVector icons, or determining whether to share UI in commonMain vs keep platform-specific. Delegates navigation to android-expert/desktop-expert. Complements kotlin-expert (handles Kotlin language aspects of state/annotations).
---

# Compose Multiplatform Expert

Visual UI patterns for sharing composables across Android and Desktop.

## When to Use This Skill

- Creating or refactoring shared UI components
- Deciding whether to share UI in `commonMain` or keep platform-specific
- Building custom ImageVector icons (robohash pattern)
- State management: remember, derivedStateOf, produceState
- Recomposition optimization: visual usage of @Stable/@Immutable
- Material3 theming and styling
- Performance: lazy lists, image loading

**Delegate to other skills:**
- Navigation structure → `android-expert`, `desktop-expert`
- Kotlin state patterns (StateFlow, sealed classes) → `kotlin-expert`
- Build configuration → `gradle-expert`

## Philosophy: Share by Default

**Default to `commons/commonMain`** unless platform experts indicate otherwise.

### Always Share

- **UI components**: Buttons, cards, lists, dialogs, inputs
- **State visualization**: Loading, empty, error states
- **Custom icons**: ImageVector assets (robohash, custom paths)
- **Theme utilities**: Color calculations, style helpers
- **Material3 components**: Any UI using Material primitives

### Keep Platform-Specific

- **Navigation structure**: Bottom nav (Android) vs Sidebar (Desktop)
- **Screen layouts**: Platform-specific scaffolding
- **System integrations**: File pickers, notifications, share sheets
- **Platform UX**: Gestures, keyboard shortcuts, window management

### Decision Framework

1. **Uses only Material3 primitives?** → Share in `commonMain`
2. **Requires platform system APIs?** → Platform-specific
3. **Pure visual component without navigation?** → Share in `commonMain`
4. **Needs platform UX patterns?** → Ask `android-expert` or `desktop-expert`

If uncertain, **default to sharing** - easier to split later than merge.

## Shared Composable Anatomy

### Structure

```kotlin
@Composable
fun SharedComponent(
    // State parameters (read-only)
    data: DataClass,
    isLoading: Boolean,
    // Event parameters (write-only)
    onAction: () -> Unit,
    // Visual parameters
    modifier: Modifier = Modifier,
    // Optional customization
    colors: ComponentColors = ComponentDefaults.colors()
) {
    // Implementation
}
```

**Pattern**: State down, events up
- Parameters above modifier = required state/events
- `modifier` parameter = layout control
- Parameters below modifier = optional customization

### Example: AddButton

```kotlin
@Composable
fun AddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Add",
    enabled: Boolean = true
) {
    OutlinedButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        shape = ActionButtonShape,
        contentPadding = ActionButtonPadding
    ) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

// Shared constants for consistency
val ActionButtonShape = RoundedCornerShape(20.dp)
val ActionButtonPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp)
```

**Why this works on all platforms:**
- Material3 primitives (OutlinedButton, Text)
- No platform APIs
- Configurable through parameters
- Consistent styling via shared constants

## State Management Patterns

### remember - Cache Across Recompositions

```kotlin
@Composable
fun ExpandableCard() {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        IconButton(onClick = { isExpanded = !isExpanded }) {
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        if (isExpanded) {
            Text("Expanded content...")
        }
    }
}
```

**Visual pattern**: Toggle button → state changes → UI expands/collapses
**Use for**: Simple UI state (toggles, counters, text input)

### derivedStateOf - Optimize Frequent Changes

```kotlin
@Composable
fun ScrollToTopButton(listState: LazyListState) {
    // Only recomposes when showButton changes, not every scroll pixel
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

**Visual pattern**: Scroll position (0, 1, 2...) → boolean (show/hide) → Button visibility
**Use for**: Input changes frequently, derived result changes rarely
**Performance**: Prevents recomposition on every scroll event

### produceState - Async to Compose State

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

**Visual pattern**: Async operation → state updates → UI reflects changes
**Use for**: Convert Flow, LiveData, callbacks into Compose state
**Lifecycle**: Coroutine cancelled when composable leaves composition

For Kotlin-specific state patterns (StateFlow, sealed classes), see `kotlin-expert`.

## State Hoisting

Move state up to make composables reusable:

```kotlin
// ❌ Stateful - hard to test, can't control externally
@Composable
fun BadSearchBar() {
    var query by remember { mutableStateOf("") }
    TextField(value = query, onValueChange = { query = it })
}

// ✅ Stateless - reusable, testable
@Composable
fun GoodSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
    )
}

@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }

    Column {
        GoodSearchBar(query = query, onQueryChange = { query = it })
        SearchResults(query = query)
    }
}
```

**Principle**: State up, events down
- State: `query: String` (read-only parameter)
- Events: `onQueryChange: (String) -> Unit` (callback parameter)

## Recomposition Optimization

### Visual Usage of @Immutable

Use @Immutable on data classes passed to composables:

```kotlin
@Immutable
data class UserProfile(val name: String, val avatar: String)

@Composable
fun ProfileCard(profile: UserProfile) {
    // Only recomposes when profile instance changes
    Row {
        RobohashImage(robot = profile.avatar)
        Text(profile.name, style = MaterialTheme.typography.titleMedium)
    }
}
```

**Visual effect**: Prevents recomposition when parent recomposes with same data
**Pattern**: Mark parameter data classes as @Immutable
**Note**: For Kotlin language details on @Immutable, see `kotlin-expert`

### Stable Parameters

```kotlin
// ✅ Stable - won't trigger recomposition unless colors instance changes
@Composable
fun ThemedCard(
    content: String,
    colors: CardColors = CardDefaults.colors(),
    modifier: Modifier = Modifier
) {
    Card(colors = colors, modifier = modifier) {
        Text(content)
    }
}
```

For @Stable annotation details, see `kotlin-expert`.

## Material3 Theming

All shared composables use Material3 for consistency:

```kotlin
@Composable
fun ThemedComponent() {
    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.background(bg)
    ) {
        Text(
            "Title",
            style = MaterialTheme.typography.headlineMedium,
            color = fg
        )
        Button(
            onClick = { /* ... */ },
            colors = ButtonDefaults.buttonColors(containerColor = primary)
        ) {
            Text("Action")
        }
    }
}
```

**Principles:**
- Colors: `MaterialTheme.colorScheme.*`
- Typography: `MaterialTheme.typography.*`
- Shapes: `MaterialTheme.shapes.*`

### Theme Detection

```kotlin
@Composable
private fun isLightTheme(): Boolean {
    val background = MaterialTheme.colorScheme.background
    return (background.red + background.green + background.blue) / 3 > 0.5f
}

@Composable
fun ThemedIcon() {
    val isDark = !isLightTheme()
    val tint = if (isDark) Color.White else Color.Black
    Icon(Icons.Default.Face, null, tint = tint)
}
```

## Custom Icons: ImageVector Pattern

Amethyst uses ImageVector for multiplatform icons.

### roboBuilder DSL

```kotlin
fun roboBuilder(block: Builder.() -> Unit): ImageVector {
    return ImageVector.Builder(
        name = "Robohash",
        defaultWidth = 300.dp,
        defaultHeight = 300.dp,
        viewportWidth = 300f,
        viewportHeight = 300f
    ).apply(block).build()
}
```

### Building Icons

```kotlin
fun customIcon(fgColor: SolidColor, builder: Builder) {
    builder.addPath(pathData1, fill = fgColor, stroke = Black, strokeLineWidth = 1.5f)
    builder.addPath(pathData2, fill = Black, fillAlpha = 0.4f)
    builder.addPath(pathData3, fill = Black, fillAlpha = 0.2f)
}

private val pathData1 = PathData {
    moveTo(144.5f, 87.5f)
    reflectiveCurveToRelative(-51.0f, 3.0f, -53.0f, 55.0f)
    lineToRelative(16.0f, 16.0f)
    close()
}

@Composable
fun CustomIcon() {
    Image(
        painter = rememberVectorPainter(
            roboBuilder {
                customIcon(SolidColor(Color.Blue), this)
            }
        ),
        contentDescription = "Custom icon"
    )
}
```

**Why ImageVector?**
- Pure Kotlin, no XML
- Works on Android, Desktop, iOS
- GPU-accelerated
- Type-safe

### Caching Pattern

```kotlin
object CustomIcons {
    private val cache = mutableMapOf<String, ImageVector>()

    fun get(key: String): ImageVector {
        return cache.getOrPut(key) {
            buildIcon(key)
        }
    }
}

@Composable
fun CachedIcon(key: String) {
    Image(imageVector = CustomIcons.get(key), contentDescription = null)
}
```

For detailed icon patterns, see `references/icon-assets.md`.

## Common Visual Patterns

### State Visualization

```kotlin
@Composable
fun DataScreen(uiState: UiState) {
    when (uiState) {
        is UiState.Loading -> LoadingState("Loading...")
        is UiState.Empty -> EmptyState(
            title = "No data",
            onRefresh = { /* refresh */ }
        )
        is UiState.Error -> ErrorState(
            message = uiState.message,
            onRetry = { /* retry */ }
        )
        is UiState.Success -> ContentList(uiState.items)
    }
}
```

**Components** (all in `commons/commonMain`):
- `LoadingState` - Progress indicator + message
- `EmptyState` - Empty message + optional refresh button
- `ErrorState` - Error message + optional retry button

### Relay Status (Amethyst Pattern)

```kotlin
@Composable
fun RelayStatusIndicator(connectedCount: Int) {
    val statusColor = when {
        connectedCount == 0 -> RelayStatusColors.Disconnected
        connectedCount < 3 -> RelayStatusColors.Connecting
        else -> RelayStatusColors.Connected
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = if (connectedCount > 0) Icons.Default.Check else Icons.Default.Close,
            tint = statusColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            "$connectedCount relay${if (connectedCount != 1) "s" else ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

**Visual mapping**:
- 0 relays → Red + X icon
- 1-2 relays → Yellow + Check icon
- 3+ relays → Green + Check icon

### Placeholder Pattern

```kotlin
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Specific implementations
@Composable
fun SearchPlaceholder() = PlaceholderScreen(
    title = "Search",
    description = "Search for users, notes, and hashtags."
)
```

**Pattern**: Generic composable + specific wrappers with preset text

## Performance

### Avoid Unnecessary Recomposition

```kotlin
// ❌ Bad - recomposes on every scroll
@Composable
fun BadButton(scrollState: ScrollState) {
    if (scrollState.value > 100) {
        Button(onClick = {}) { Text("Top") }
    }
}

// ✅ Good - only recomposes when visibility changes
@Composable
fun GoodButton(scrollState: ScrollState) {
    val show by remember { derivedStateOf { scrollState.value > 100 } }
    if (show) {
        Button(onClick = {}) { Text("Top") }
    }
}
```

### Lazy Lists

```kotlin
@Composable
fun FeedList(items: List<Item>) {
    LazyColumn {
        items(items, key = { it.id }) { item ->
            FeedItem(item)
        }
    }
}
```

**Key principle**: Use `key` parameter for stable item identity

## Bundled Resources

- **references/shared-composables-catalog.md** - Complete catalog of shared UI components
- **references/state-patterns.md** - State management patterns with visual examples
- **references/icon-assets.md** - Custom ImageVector icon patterns
- **scripts/find-composables.sh** - Find all @Composable functions in codebase

## Quick Reference

| Task | Pattern | Location |
|------|---------|----------|
| Reusable UI | State hoisting | commons/commonMain |
| Simple state | remember { mutableStateOf() } | Composable scope |
| Derived state | derivedStateOf { } | remember block |
| Async → state | produceState { } | Composable function |
| Custom icons | roboBuilder + PathData | commons/icons |
| Loading/Error | LoadingState, ErrorState | commons/ui/components |
| Theme colors | MaterialTheme.colorScheme | Any @Composable |
| Navigation | Delegate to platform expert | amethyst/, desktopApp/ |

## Common Workflows

### Creating a Shared Component

1. Start in `commons/src/commonMain/kotlin/.../ui/components/`
2. Use Material3 primitives only
3. Hoist state (parameters for data, callbacks for events)
4. Add modifier parameter
5. Use MaterialTheme for colors/typography
6. Test on both Android and Desktop

### Converting Existing Component

1. Read current implementation in `amethyst/` or `desktopApp/`
2. Identify pure visual logic (no platform APIs)
3. Create in `commons/commonMain` with hoisted state
4. Replace platform implementations with shared component
5. Keep platform-specific wrappers if needed

### Custom Icon

1. Export SVG from design tool
2. Convert to PathData using Android Studio
3. Create icon function with roboBuilder
4. Add caching if generated dynamically
5. Wrap in @Composable for easy use

### Navigation (Delegate)

For navigation patterns:
- Android bottom nav → `android-expert`
- Desktop sidebar → `desktop-expert`
- Multi-window → `desktop-expert`

## Related Skills

- **kotlin-expert** - Kotlin language aspects (@Immutable details, StateFlow, sealed classes)
- **android-expert** - Android navigation, platform APIs
- **desktop-expert** - Desktop navigation, window management, OS specifics
- **kotlin-coroutines** - Async patterns, Flow integration
