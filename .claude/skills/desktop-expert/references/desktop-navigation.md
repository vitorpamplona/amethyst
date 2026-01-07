# Desktop Navigation Patterns

Comparison of mobile vs desktop navigation patterns in AmethystMultiplatform.

## Core Difference

| Platform | Pattern | Location | Rationale |
|----------|---------|----------|-----------|
| **Android** | Bottom Navigation Bar | Horizontal, bottom | Thumb reach on mobile |
| **Desktop** | Navigation Rail | Vertical, left sidebar | Horizontal screen space |

---

## Desktop: NavigationRail

### Current Implementation

**File:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt:191-264`

```kotlin
@Composable
fun MainContent(
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    // ...
) {
    Row(Modifier.fillMaxSize()) {
        // LEFT: Vertical Sidebar (NavigationRail)
        NavigationRail(
            modifier = Modifier.width(80.dp).fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Spacer(Modifier.height(16.dp))

            // Top navigation items
            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, "Feed") },
                label = { Text("Feed") },
                selected = currentScreen == AppScreen.Feed,
                onClick = { onScreenChange(AppScreen.Feed) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Search, "Search") },
                label = { Text("Search") },
                selected = currentScreen == AppScreen.Search,
                onClick = { onScreenChange(AppScreen.Search) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Email, "Messages") },
                label = { Text("DMs") },
                selected = currentScreen == AppScreen.Messages,
                onClick = { onScreenChange(AppScreen.Messages) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Notifications, "Notifications") },
                label = { Text("Alerts") },
                selected = currentScreen == AppScreen.Notifications,
                onClick = { onScreenChange(AppScreen.Notifications) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Person, "Profile") },
                label = { Text("Profile") },
                selected = currentScreen == AppScreen.Profile,
                onClick = { onScreenChange(AppScreen.Profile) }
            )

            // Push Settings to bottom
            Spacer(Modifier.weight(1f))

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            NavigationRailItem(
                icon = { Icon(Icons.Default.Settings, "Settings") },
                label = { Text("Settings") },
                selected = currentScreen == AppScreen.Settings,
                onClick = { onScreenChange(AppScreen.Settings) }
            )

            Spacer(Modifier.height(16.dp))
        }

        VerticalDivider()

        // RIGHT: Main Content Area
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            when (currentScreen) {
                AppScreen.Feed -> FeedScreen(relayManager)
                AppScreen.Search -> SearchPlaceholder()
                AppScreen.Messages -> MessagesPlaceholder()
                AppScreen.Notifications -> NotificationsPlaceholder()
                AppScreen.Profile -> ProfileScreen(account, accountManager)
                AppScreen.Settings -> RelaySettingsScreen(relayManager)
            }
        }
    }
}
```

### Layout Structure

```
┌────────────────────────────────────────┐
│  [Menu Bar: File, Edit, View, Help]   │  ← MenuBar (OS-native)
├──────┬─────────────────────────────────┤
│      │                                 │
│ [🏠] │                                 │
│ Feed │                                 │
│      │                                 │
│ [🔍] │      Main Content Area          │
│Search│      (Feed, Messages, etc.)     │
│      │                                 │
│ [✉️] │                                 │
│ DMs  │                                 │
│      │                                 │
│ [🔔] │                                 │
│Alerts│                                 │
│      │                                 │
│ [👤] │                                 │
│Profile                                 │
│      │                                 │
│  ─   │                                 │
│ [⚙️] │                                 │
│Settings                                │
│      │                                 │
└──────┴─────────────────────────────────┘
  80dp          Remaining width (weight=1f)
```

### Key Features

1. **Always visible:** All nav items visible at once
2. **Icon + Label:** Both shown (not just icons)
3. **Vertical list:** Natural reading order
4. **Settings at bottom:** Separated by divider + Spacer.weight(1f)
5. **80dp width:** Standard NavigationRail width

---

## Android: BottomNavigationBar (Future)

### Expected Implementation

**Location:** `amethyst/src/androidMain/kotlin/...` (not yet implemented)

```kotlin
@Composable
fun MainScreen(
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Feed") },
                    label = { Text("Feed") },
                    selected = currentScreen == AppScreen.Feed,
                    onClick = { onScreenChange(AppScreen.Feed) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, "Search") },
                    label = { Text("Search") },
                    selected = currentScreen == AppScreen.Search,
                    onClick = { onScreenChange(AppScreen.Search) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Email, "Messages") },
                    label = { Text("Messages") },
                    selected = currentScreen == AppScreen.Messages,
                    onClick = { onScreenChange(AppScreen.Messages) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") },
                    selected = currentScreen == AppScreen.Profile,
                    onClick = { onScreenChange(AppScreen.Profile) }
                )
            }
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            when (currentScreen) {
                AppScreen.Feed -> FeedScreen()
                AppScreen.Search -> SearchScreen()
                AppScreen.Messages -> MessagesScreen()
                AppScreen.Profile -> ProfileScreen()
                // Settings accessed via Profile or overflow menu
            }
        }
    }
}
```

### Layout Structure

```
┌─────────────────────────────────────┐
│                                     │
│                                     │
│        Main Content Area            │
│        (Feed, Messages, etc.)       │
│                                     │
│                                     │
│                                     │
├─────────────────────────────────────┤
│ [🏠]   [🔍]   [✉️]   [👤]          │  ← NavigationBar
│ Feed  Search  DMs  Profile          │
└─────────────────────────────────────┘
```

### Key Differences from Desktop

1. **Bottom placement:** Thumb reach
2. **Horizontal layout:** Limited vertical space
3. **Fewer items:** 3-5 primary destinations
4. **Label optional:** Can hide on small screens
5. **Settings hidden:** In profile or overflow

---

## Shared Navigation State

Both platforms use the same `AppScreen` enum from `commons`.

**File:** `commons/src/commonMain/kotlin/.../navigation/AppScreen.kt` (expected)

```kotlin
// Shared navigation destinations
enum class AppScreen {
    Feed,
    Search,
    Messages,
    Notifications,
    Profile,
    Settings
}
```

**State management (shared):**

```kotlin
// commons/src/jvmAndroid/kotlin/.../navigation/NavigationViewModel.kt
class NavigationViewModel : ViewModel() {
    private val _currentScreen = MutableStateFlow(AppScreen.Feed)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }
}
```

---

## Multi-Pane Desktop Layout (Advanced)

Desktop can utilize horizontal space for multi-pane layouts.

### Two-Pane Layout

```kotlin
Row(Modifier.fillMaxSize()) {
    // Left: NavigationRail (fixed 80dp)
    NavigationRail { /* ... */ }

    VerticalDivider()

    // Center: Main content (60% width)
    Box(Modifier.weight(0.6f)) {
        FeedScreen()
    }

    // Right: Detail pane (40% width, conditional)
    if (selectedNote != null) {
        VerticalDivider()
        Box(Modifier.weight(0.4f)) {
            NoteDetailPane(selectedNote)
        }
    }
}
```

### Layout:

```
┌──────┬───────────────────┬─────────────┐
│      │                   │             │
│ Nav  │   Feed List       │   Detail    │
│ Rail │   (60%)           │   Pane      │
│      │                   │   (40%)     │
│      │                   │             │
└──────┴───────────────────┴─────────────┘
 80dp      weight(0.6f)      weight(0.4f)
```

**Use cases:**
- Email: List + message detail
- Notes: List + editor
- Settings: Categories + options

---

## Keyboard Navigation

Desktop should support keyboard navigation.

### Tab Navigation

```kotlin
NavigationRail(
    modifier = Modifier.focusable()
) {
    NavigationRailItem(
        icon = { Icon(Icons.Default.Home, "Feed") },
        label = { Text("Feed") },
        selected = currentScreen == AppScreen.Feed,
        onClick = { onScreenChange(AppScreen.Feed) },
        modifier = Modifier.focusable()
    )
    // More items...
}
```

### Keyboard Shortcuts

```kotlin
Window(
    onPreviewKeyEvent = { event ->
        when {
            event.key == Key.One && event.isCtrlPressed ->
                onScreenChange(AppScreen.Feed).also { true }
            event.key == Key.Two && event.isCtrlPressed ->
                onScreenChange(AppScreen.Search).also { true }
            event.key == Key.Three && event.isCtrlPressed ->
                onScreenChange(AppScreen.Messages).also { true }
            else -> false
        }
    }
) {
    // Content
}
```

**Standard:**
- Ctrl+1: First nav item (Feed)
- Ctrl+2: Second nav item (Search)
- Ctrl+3: Third nav item (Messages)
- Ctrl+Comma: Settings

---

## Navigation Transitions

### Desktop (Instant)

No fancy animations. Instant switch.

```kotlin
Box {
    when (currentScreen) {
        AppScreen.Feed -> FeedScreen()
        AppScreen.Search -> SearchScreen()
    }
}
```

### Android (Animated, Future)

Can use Navigation Compose for transitions.

```kotlin
NavHost(navController, startDestination = "feed") {
    composable("feed") { FeedScreen() }
    composable("search") { SearchScreen() }
}
```

---

## Best Practices

### Desktop NavigationRail

✅ **DO:**
- Keep width 72-80dp
- Show both icon and label
- Use Spacer.weight(1f) for bottom items
- Separate sections with HorizontalDivider
- Limit to 5-7 primary items

❌ **DON'T:**
- Use bottom navigation on desktop
- Hide labels (plenty of space)
- Make it collapsible (not standard)
- Use hamburger menu (not desktop pattern)

### Android NavigationBar

✅ **DO:**
- Limit to 3-5 items
- Use bottom placement
- Consider label visibility on small screens
- Use standard icons

❌ **DON'T:**
- Put more than 5 items
- Use top placement (deprecated)
- Put critical actions only in nav bar

---

## Migration Strategy

When adding Android support:

1. **Extract shared state:** Move `AppScreen` to `commons/commonMain`
2. **Platform layouts:** Keep `NavigationRail` in `desktopApp/jvmMain`, `NavigationBar` in `amethyst/androidMain`
3. **Shared screens:** Composables in `commons/commonMain` (FeedScreen content)
4. **Platform chrome:** Navigation containers in platform modules

**Example:**

```kotlin
// commons/commonMain - Shared screen content
@Composable
fun FeedContent(notes: List<Note>) {
    LazyColumn {
        items(notes) { note ->
            NoteCard(note)
        }
    }
}

// desktopApp/jvmMain - Desktop wrapper
@Composable
fun FeedScreen() {
    Column {
        FeedHeader()  // Desktop-specific header
        FeedContent(notes)  // Shared content
    }
}

// amethyst/androidMain - Android wrapper
@Composable
fun FeedScreen() {
    Scaffold(
        topBar = { TopAppBar { Text("Feed") } }
    ) {
        FeedContent(notes)  // Same shared content
    }
}
```

---

## References

- **Current Desktop:** Main.kt:191-264
- **Material3 NavigationRail:** [Material Design Docs](https://m3.material.io/components/navigation-rail)
- **Material3 NavigationBar:** [Material Design Docs](https://m3.material.io/components/navigation-bar)
