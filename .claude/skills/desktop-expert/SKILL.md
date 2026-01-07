# Desktop Expert

Expert in Compose Multiplatform Desktop development for AmethystMultiplatform. Covers Desktop-specific APIs, OS conventions, navigation patterns, and UX principles.

## When to Use This Skill

**Auto-invoke when:**
- Working with `desktopApp/` module files
- Using Desktop-only APIs: `Window`, `Tray`, `MenuBar`, `Dialog`
- Implementing keyboard shortcuts, menu systems
- Desktop navigation (NavigationRail, multi-window)
- File system operations (file pickers, drag-drop)
- OS-specific behavior (macOS, Windows, Linux)
- Desktop UX patterns (keyboard-first, tooltips)

**Delegate to:**
- **kotlin-multiplatform**: Shared code questions, `jvmMain` source set structure
- **gradle-expert**: All `build.gradle.kts` issues, dependency conflicts
- **compose-expert**: General Compose patterns, `@Composable` best practices, Material3

## Scope

**In scope:**
- Desktop-only Compose APIs
- Window management, positioning, state
- MenuBar + keyboard shortcuts (OS-specific)
- System Tray integration
- Desktop navigation patterns (NavigationRail)
- File dialogs, Desktop.getDesktop()
- OS conventions (macOS vs Windows vs Linux)
- Desktop UX principles

**Out of scope:**
- Build configuration → **gradle-expert**
- Shared composables → **compose-expert**
- KMP structure → **kotlin-multiplatform**

---

## 1. Desktop Entry Point

### application {} DSL

Desktop apps start with the `application {}` block:

```kotlin
// desktopApp/src/jvmMain/kotlin/Main.kt
fun main() = application {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Amethyst"
    ) {
        MenuBar { /* ... */ }
        App()
    }
}
```

**Key points:**
- `application {}` is the root composable (JVM-only)
- `Window()` creates the main window
- `rememberWindowState()` manages size/position
- `onCloseRequest` handles window close

**See:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt:87-138`

---

## 2. Window Management

### WindowState

```kotlin
val windowState = rememberWindowState(
    width = 1200.dp,
    height = 800.dp,
    position = WindowPosition.Aligned(Alignment.Center)
)

Window(
    state = windowState,
    title = "My App",
    resizable = true,
    onCloseRequest = ::exitApplication
) {
    // Content
}
```

### Multiple Windows

```kotlin
fun main() = application {
    var showSettings by remember { mutableStateOf(false) }

    Window(onCloseRequest = ::exitApplication, title = "Main") {
        Button(onClick = { showSettings = true }) {
            Text("Open Settings")
        }
    }

    if (showSettings) {
        Window(
            onCloseRequest = { showSettings = false },
            title = "Settings"
        ) {
            // Settings UI
        }
    }
}
```

**Pattern:** Use state to control window visibility conditionally.

---

## 3. MenuBar System

### Basic MenuBar

```kotlin
Window(onCloseRequest = ::exitApplication, title = "App") {
    MenuBar {
        Menu("File") {
            Item("New Note", onClick = { /* ... */ })
            Separator()
            Item("Quit", onClick = ::exitApplication)
        }
        Menu("Edit") {
            Item("Copy", onClick = { /* ... */ })
            Item("Paste", onClick = { /* ... */ })
        }
    }
    App()
}
```

### Keyboard Shortcuts (OS-Aware)

**Current issue:** Main.kt hardcodes `ctrl = true` (Main.kt:105, 111, 117, 122, 123).

**OS-specific shortcuts:**

```kotlin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut

// Detect OS
val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

MenuBar {
    Menu("File") {
        Item(
            "New Note",
            shortcut = if (isMacOS) {
                KeyShortcut(Key.N, meta = true)  // Cmd+N on macOS
            } else {
                KeyShortcut(Key.N, ctrl = true)   // Ctrl+N on Win/Linux
            },
            onClick = { /* ... */ }
        )
        Item(
            "Settings",
            shortcut = if (isMacOS) {
                KeyShortcut(Key.Comma, meta = true)  // Cmd+, on macOS
            } else {
                KeyShortcut(Key.Comma, ctrl = true)   // Ctrl+, on Win/Linux
            },
            onClick = { /* ... */ }
        )
        Separator()
        Item(
            "Quit",
            shortcut = if (isMacOS) {
                KeyShortcut(Key.Q, meta = true)  // Cmd+Q on macOS
            } else {
                KeyShortcut(Key.Q, ctrl = true)   // Ctrl+Q on Win/Linux
            },
            onClick = ::exitApplication
        )
    }
}
```

**Standard shortcuts:**

| Action | macOS | Windows/Linux |
|--------|-------|---------------|
| New | Cmd+N | Ctrl+N |
| Open | Cmd+O | Ctrl+O |
| Save | Cmd+S | Ctrl+S |
| Quit | Cmd+Q | Ctrl+Q (Alt+F4) |
| Settings | Cmd+, | Ctrl+, |
| Copy | Cmd+C | Ctrl+C |
| Paste | Cmd+V | Ctrl+V |
| Undo | Cmd+Z | Ctrl+Z |

**See:** `references/keyboard-shortcuts.md` for full list.

---

## 4. System Tray

### Basic Tray

```kotlin
application {
    var isVisible by remember { mutableStateOf(true) }

    Tray(
        icon = painterResource("icon.png"),
        onAction = { isVisible = true },
        menu = {
            Item("Show", onClick = { isVisible = true })
            Separator()
            Item("Quit", onClick = ::exitApplication)
        }
    )

    if (isVisible) {
        Window(
            onCloseRequest = { isVisible = false }, // Minimize to tray
            title = "App"
        ) {
            // Content
        }
    }
}
```

**Pattern:** Hide window to tray instead of closing.

**Current status:** Not implemented in Main.kt. Planned feature.

---

## 5. Desktop Navigation Patterns

### NavigationRail (Current Pattern)

Desktop uses **NavigationRail** (vertical sidebar) instead of Android's bottom navigation.

```kotlin
Row(Modifier.fillMaxSize()) {
    // Sidebar
    NavigationRail(
        modifier = Modifier.width(80.dp).fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        NavigationRailItem(
            icon = { Icon(Icons.Default.Home, "Feed") },
            label = { Text("Feed") },
            selected = currentScreen == AppScreen.Feed,
            onClick = { currentScreen = AppScreen.Feed }
        )
        // More items...
    }

    VerticalDivider()

    // Main content area
    Box(Modifier.weight(1f).fillMaxHeight()) {
        when (currentScreen) {
            AppScreen.Feed -> FeedScreen()
            // Other screens...
        }
    }
}
```

**See:** Main.kt:191-264

**Why NavigationRail?**
- Desktop has horizontal space (1200+ dp width)
- Vertical sidebar is standard desktop pattern
- Always visible (no tabs hidden)
- Icon + label both visible

**Android comparison:**
- Android: `BottomNavigationBar` (horizontal, bottom)
- Desktop: `NavigationRail` (vertical, left)

### Multi-Pane Layouts

Desktop can leverage wide screens:

```kotlin
Row {
    // Left: Navigation
    NavigationRail { /* ... */ }

    // Center: Main content
    Box(Modifier.weight(0.6f)) {
        FeedScreen()
    }

    // Right: Details pane (desktop only)
    if (selectedNote != null) {
        VerticalDivider()
        Box(Modifier.weight(0.4f)) {
            NoteDetailPane(selectedNote)
        }
    }
}
```

**See:** `references/desktop-navigation.md`

---

## 6. File System Integration

### File Dialogs

```kotlin
// File picker (load)
val fileDialog = FileDialog(Frame(), "Select file", FileDialog.LOAD)
fileDialog.isVisible = true
val filePath = fileDialog.file?.let { "${fileDialog.directory}$it" }

// File picker (save)
val saveDialog = FileDialog(Frame(), "Save file", FileDialog.SAVE)
saveDialog.isVisible = true
val savePath = saveDialog.file?.let { "${saveDialog.directory}$it" }
```

**Note:** Compose Desktop doesn't have native file picker composable yet. Use AWT `FileDialog`.

### Open External URLs

```kotlin
// jvmMain actual implementation
actual fun openExternalUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}
```

**Pattern:** Define `expect` in `commonMain`, implement `actual` in `jvmMain`.

### Drag & Drop (Future)

```kotlin
// Compose Desktop drag-drop (experimental)
Box(
    modifier = Modifier
        .onExternalDrag(
            onDragStart = { /* ... */ },
            onDrag = { /* ... */ },
            onDragExit = { /* ... */ },
            onDrop = { state ->
                val dragData = state.dragData
                // Handle dropped files
            }
        )
) {
    Text("Drop files here")
}
```

---

## 7. OS-Specific Conventions

### Platform Detection

```kotlin
val osName = System.getProperty("os.name").lowercase()

val isMacOS = osName.contains("mac")
val isWindows = osName.contains("win")
val isLinux = osName.contains("nux") || osName.contains("nix")
```

### Menu Bar Placement

| OS | Behavior |
|----|----------|
| **macOS** | System-wide menu bar at top of screen |
| **Windows** | In-window menu bar |
| **Linux** | Varies by desktop environment |

Compose Desktop `MenuBar` adapts automatically.

### Keyboard Modifier Keys

| Modifier | macOS | Windows/Linux |
|----------|-------|---------------|
| Primary | `meta = true` (Cmd) | `ctrl = true` |
| Secondary | `ctrl = true` | `alt = true` |
| Shift | `shift = true` | `shift = true` |

**Best practice:** Detect OS and use appropriate modifier.

### System Tray Behavior

| OS | Tray Location |
|----|---------------|
| **macOS** | Top-right menu bar |
| **Windows** | Bottom-right taskbar |
| **Linux** | Top panel (varies) |

---

## 8. Desktop UX Principles

### Keyboard-First Design

**Every action should have:**
1. Mouse/touch interaction
2. Keyboard shortcut (if frequent)
3. Tooltip showing shortcut

```kotlin
IconButton(
    onClick = { /* refresh */ },
    modifier = Modifier.tooltipArea(
        tooltip = {
            Text("Refresh (${if (isMacOS) "Cmd" else "Ctrl"}+R)")
        }
    )
) {
    Icon(Icons.Default.Refresh, "Refresh")
}
```

### Tooltip Best Practices

- Show keyboard shortcut in tooltip
- Use native modifier name (Cmd vs Ctrl)
- Brief description + shortcut

### Context Menus

Right-click should show context menu:

```kotlin
// Future: Compose Desktop context menu API
Box(
    modifier = Modifier.contextMenuArea(
        items = {
            listOf(
                ContextMenuItem("Copy") { /* ... */ },
                ContextMenuItem("Paste") { /* ... */ }
            )
        }
    )
) {
    // Content
}
```

**Current:** Use popup or custom implementation.

### Window State Persistence

Save/restore window size/position:

```kotlin
// Save on close
windowState.size // DpSize
windowState.position // WindowPosition

// Restore on launch
val savedWidth = preferences.getInt("window.width", 1200)
val savedHeight = preferences.getInt("window.height", 800)

val windowState = rememberWindowState(
    width = savedWidth.dp,
    height = savedHeight.dp
)
```

---

## 9. Desktop Module Structure

```
desktopApp/
├── build.gradle.kts                  # Desktop-only build config
└── src/
    └── jvmMain/
        ├── kotlin/
        │   └── com/vitorpamplona/amethyst/desktop/
        │       ├── Main.kt               # Entry point, Window, MenuBar
        │       ├── network/
        │       │   ├── DesktopHttpClient.kt
        │       │   └── DesktopRelayConnectionManager.kt
        │       └── ui/
        │           ├── FeedScreen.kt     # Desktop screen layouts
        │           └── LoginScreen.kt
        └── resources/
            ├── icon.icns                 # macOS icon
            ├── icon.ico                  # Windows icon
            └── icon.png                  # Linux icon
```

**Key files:**
- `Main.kt:87-138` - `application {}`, `Window`, `MenuBar`
- `Main.kt:183-264` - NavigationRail pattern
- `build.gradle.kts:45-73` - Desktop packaging config

---

## 10. Packaging & Distribution

### Build Configuration

```kotlin
// desktopApp/build.gradle.kts
compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            packageName = "Amethyst"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.vitorpamplona.amethyst.desktop"
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
            }

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                menuGroup = "Amethyst"
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
            }
        }
    }
}
```

**See:** desktopApp/build.gradle.kts:45-73

### Gradle Tasks

```bash
# Run desktop app
./gradlew :desktopApp:run

# Package for distribution
./gradlew :desktopApp:packageDmg       # macOS
./gradlew :desktopApp:packageMsi       # Windows
./gradlew :desktopApp:packageDeb       # Linux
```

**Delegate packaging issues to gradle-expert.**

---

## Common Patterns

### Pattern: OS-Aware Shortcuts Helper

```kotlin
// commons/src/jvmMain/kotlin/shortcuts/ShortcutUtils.kt
object DesktopShortcuts {
    private val isMacOS = System.getProperty("os.name")
        .lowercase().contains("mac")

    fun primary(key: Key) = if (isMacOS) {
        KeyShortcut(key, meta = true)
    } else {
        KeyShortcut(key, ctrl = true)
    }

    fun primaryShift(key: Key) = if (isMacOS) {
        KeyShortcut(key, meta = true, shift = true)
    } else {
        KeyShortcut(key, ctrl = true, shift = true)
    }

    val modifierName = if (isMacOS) "Cmd" else "Ctrl"
}

// Usage in MenuBar
Item(
    "New Note",
    shortcut = DesktopShortcuts.primary(Key.N),
    onClick = { /* ... */ }
)
```

### Pattern: Shared Composables, Platform Layouts

```kotlin
// commons/commonMain - Shared NoteCard
@Composable
fun NoteCard(note: NoteDisplayData) {
    // Business logic, UI component (shared)
}

// desktopApp/jvmMain - Desktop layout
@Composable
fun FeedScreen() {
    Column {
        FeedHeader(/* ... */)  // Shared from commons
        LazyColumn {
            items(notes) { note ->
                NoteCard(note)  // Shared composable
            }
        }
    }
}

// amethyst/androidMain - Android layout
@Composable
fun FeedScreen() {
    Scaffold(
        bottomBar = { BottomNavigationBar() }  // Android-specific
    ) {
        LazyColumn {
            items(notes) { note ->
                NoteCard(note)  // Same shared composable
            }
        }
    }
}
```

**Philosophy:** Share UI components (cards, buttons), keep navigation/layout platform-specific.

---

## Resources

### Official Documentation
- [Desktop-only API | Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-components.html)
- [Top-level windows management](https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html)
- [Tray/MenuBar Tutorial](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Tray_Notifications_MenuBar_new/README.md)

### Bundled References
- `references/desktop-compose-apis.md` - Complete Desktop API catalog
- `references/desktop-navigation.md` - NavigationRail vs BottomNav patterns
- `references/keyboard-shortcuts.md` - Standard shortcuts by OS
- `references/os-detection.md` - Platform detection patterns

### Codebase Examples
- Main.kt:87-138 - Window, MenuBar entry point
- Main.kt:183-264 - NavigationRail pattern
- FeedScreen.kt:49-136 - Desktop screen layout
- LoginScreen.kt:44-97 - Centered desktop login

---

## Questions to Ask

When working on desktop features:

1. **Should this be shared or desktop-only?**
   - Business logic → Share in `commonMain`
   - Navigation/layout → Keep in `desktopApp/jvmMain`

2. **Does this need OS-specific behavior?**
   - Keyboard shortcuts → Yes (Cmd vs Ctrl)
   - File paths → Yes (separators)
   - Icons → Yes (per-OS formats)

3. **Is there a desktop UX convention?**
   - Check MenuBar standards
   - Consider keyboard-first design
   - Tooltips for all actions

4. **Does this need gradle-expert?**
   - Any `build.gradle.kts` changes → Delegate
   - Packaging/distribution issues → Delegate

---

## Anti-Patterns

❌ **Hardcoding Ctrl everywhere**
```kotlin
// Main.kt:105 - Current issue
shortcut = KeyShortcut(Key.N, ctrl = true)  // Wrong on macOS
```

✅ **OS-aware shortcuts**
```kotlin
shortcut = DesktopShortcuts.primary(Key.N)
```

---

❌ **Using Android navigation on Desktop**
```kotlin
Scaffold(bottomBar = { BottomNavigationBar() })  // Wrong for desktop
```

✅ **NavigationRail for desktop**
```kotlin
Row {
    NavigationRail { /* ... */ }
    MainContent()
}
```

---

❌ **No keyboard shortcuts**
```kotlin
IconButton(onClick = { refresh() }) {
    Icon(Icons.Default.Refresh, "Refresh")
}
```

✅ **Shortcuts + tooltips**
```kotlin
IconButton(
    onClick = { refresh() },
    modifier = Modifier.tooltipArea("Refresh (Cmd+R)")
) {
    Icon(Icons.Default.Refresh, "Refresh")
}
```

---

## Next Steps

When implementing desktop features:

1. **Read** `references/desktop-compose-apis.md` for API catalog
2. **Check** `references/keyboard-shortcuts.md` for standard shortcuts
3. **Reference** Main.kt:87-264 for current patterns
4. **Test** on all 3 platforms (macOS, Windows, Linux) if possible
5. **Delegate** build issues to gradle-expert
6. **Share** UI components via compose-expert, not desktop-expert

---

**Version:** 1.0.0
**Last Updated:** 2025-12-30
**Codebase Reference:** AmethystMultiplatform commit 258c4e011
