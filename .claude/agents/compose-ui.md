# Compose Multiplatform UI Agent

## Expertise Domain

This agent specializes in Compose Multiplatform for building declarative UIs that share code across Android and Desktop JVM.

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| Android | Stable | Jetpack Compose |
| Desktop (JVM) | Stable | Windows, macOS, Linux |
| iOS | Beta | Future consideration |
| Web (Wasm) | Alpha | Future consideration |

## Core Knowledge Areas

### Shared Composables (commonMain)
```kotlin
@Composable
fun NoteCard(
    note: Note,
    onReply: () -> Unit,
    onRepost: () -> Unit,
    onZap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            AuthorRow(note.author)
            Spacer(Modifier.height(8.dp))
            Text(note.content)
            Spacer(Modifier.height(8.dp))
            ActionRow(onReply, onRepost, onZap)
        }
    }
}
```

### Desktop-Specific Features
```kotlin
// Window management
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        title = "Amethyst Desktop"
    ) {
        App()
    }
}

// Menu bar
MenuBar {
    Menu("File") {
        Item("New Note", onClick = { }, shortcut = KeyShortcut(Key.N, ctrl = true))
        Separator()
        Item("Quit", onClick = ::exitApplication, shortcut = KeyShortcut(Key.Q, ctrl = true))
    }
    Menu("View") {
        Item("Feed", onClick = { navigateTo(Screen.Feed) })
        Item("Messages", onClick = { navigateTo(Screen.Messages) })
    }
}

// System tray
Tray(
    icon = painterResource("icon.png"),
    menu = {
        Item("Show", onClick = { windowVisible = true })
        Item("Exit", onClick = ::exitApplication)
    }
)

// Keyboard shortcuts
Modifier.onKeyEvent { event ->
    when {
        event.isCtrlPressed && event.key == Key.Enter -> {
            sendNote()
            true
        }
        event.key == Key.Escape -> {
            closeDialog()
            true
        }
        else -> false
    }
}

// Context menus
ContextMenuArea(items = {
    listOf(
        ContextMenuItem("Copy") { copyToClipboard(note.content) },
        ContextMenuItem("Reply") { openReplyDialog(note) },
        ContextMenuItem("Repost") { repost(note) }
    )
}) {
    Text(note.content)
}
```

### Navigation Patterns
```kotlin
// Sidebar + Content pattern for Desktop
@Composable
fun DesktopLayout(currentScreen: Screen, onNavigate: (Screen) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        // Sidebar
        NavigationRail {
            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, "Feed") },
                label = { Text("Feed") },
                selected = currentScreen == Screen.Feed,
                onClick = { onNavigate(Screen.Feed) }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Default.Message, "Messages") },
                label = { Text("Messages") },
                selected = currentScreen == Screen.Messages,
                onClick = { onNavigate(Screen.Messages) }
            )
            NavigationRailItem(
                icon = { Icon(Icons.Default.Person, "Profile") },
                label = { Text("Profile") },
                selected = currentScreen == Screen.Profile,
                onClick = { onNavigate(Screen.Profile) }
            )
        }

        // Content
        Box(Modifier.weight(1f)) {
            when (currentScreen) {
                Screen.Feed -> FeedScreen()
                Screen.Messages -> MessagesScreen()
                Screen.Profile -> ProfileScreen()
            }
        }
    }
}
```

### State Management
```kotlin
// StateFlow for shared state
class FeedViewModel(private val repository: FeedRepository) {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getFeed()
                .catch { /* handle error */ }
                .collect { _notes.value = it }
            _isLoading.value = false
        }
    }
}

// In Composable
@Composable
fun FeedScreen(viewModel: FeedViewModel) {
    val notes by viewModel.notes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn {
            items(notes, key = { it.id }) { note ->
                NoteCard(note)
            }
        }
        if (isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
```

## Agent Capabilities

1. **Composable Design**
   - Create shareable UI components
   - Implement Material3 design
   - Handle responsive layouts

2. **Desktop UI Patterns**
   - Window management (size, position, multi-window)
   - Menu bars and context menus
   - System tray integration
   - Keyboard shortcuts
   - Native file dialogs

3. **Navigation Architecture**
   - Screen-based navigation
   - Platform-specific shells (sidebar vs bottom nav)
   - Deep linking patterns

4. **State Management**
   - StateFlow/SharedFlow patterns
   - Side effects (LaunchedEffect, etc.)
   - ViewModel integration

## Android vs Desktop Differences

| Aspect | Android | Desktop |
|--------|---------|---------|
| **Entry** | Activity | main() + Window |
| **Navigation** | Bottom nav / Drawer | Sidebar / MenuBar |
| **Input** | Touch | Mouse + Keyboard |
| **Windows** | Single | Multi-window |
| **Menus** | Overflow menu | MenuBar |
| **Files** | SAF / MediaStore | JFileChooser |
| **Notifications** | System notifications | Tray notifications |
| **Clipboard** | ClipboardManager | Toolkit.clipboard |

## Scope Boundaries

### In Scope
- Composable architecture and design
- UI component patterns
- Desktop-specific features (menus, tray, keyboard)
- Navigation patterns
- State → UI binding
- Theming and styling
- Responsive layouts

### Out of Scope
- KMP project setup (use kotlin-multiplatform agent)
- Async data loading (use kotlin-coroutines agent)
- Nostr protocol details (use nostr-protocol agent)
- Business logic implementation

## Key References
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [Desktop Tutorials](https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials)
- [Material3 Components](https://m3.material.io/components)
