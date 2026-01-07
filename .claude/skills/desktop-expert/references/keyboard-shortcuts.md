# Keyboard Shortcuts Reference

Standard keyboard shortcuts for desktop applications across macOS, Windows, and Linux.

## Primary Modifier Keys

| Platform | Primary | Secondary | Tertiary |
|----------|---------|-----------|----------|
| **macOS** | Cmd (⌘) / `meta` | Option (⌥) / `alt` | Ctrl (⌃) / `ctrl` |
| **Windows** | Ctrl / `ctrl` | Alt / `alt` | Win / `meta` |
| **Linux** | Ctrl / `ctrl` | Alt / `alt` | Super / `meta` |

**In Compose Desktop:**

```kotlin
// macOS
KeyShortcut(Key.N, meta = true)   // Cmd+N

// Windows/Linux
KeyShortcut(Key.N, ctrl = true)    // Ctrl+N
```

---

## File Operations

| Action | macOS | Windows | Linux | Notes |
|--------|-------|---------|-------|-------|
| **New** | Cmd+N | Ctrl+N | Ctrl+N | Create new |
| **Open** | Cmd+O | Ctrl+O | Ctrl+O | Open file |
| **Save** | Cmd+S | Ctrl+S | Ctrl+S | Save current |
| **Save As** | Cmd+Shift+S | Ctrl+Shift+S | Ctrl+Shift+S | Save with new name |
| **Close** | Cmd+W | Ctrl+W | Ctrl+W | Close window/tab |
| **Quit** | Cmd+Q | Ctrl+Q | Ctrl+Q | Exit app |
| **Print** | Cmd+P | Ctrl+P | Ctrl+P | Print |

**Compose Implementation:**

```kotlin
val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

MenuBar {
    Menu("File") {
        Item(
            "New Note",
            shortcut = if (isMacOS) {
                KeyShortcut(Key.N, meta = true)
            } else {
                KeyShortcut(Key.N, ctrl = true)
            },
            onClick = { createNewNote() }
        )
        Item(
            "Save",
            shortcut = if (isMacOS) {
                KeyShortcut(Key.S, meta = true)
            } else {
                KeyShortcut(Key.S, ctrl = true)
            },
            onClick = { save() }
        )
        Separator()
        Item(
            "Quit",
            shortcut = if (isMacOS) {
                KeyShortcut(Key.Q, meta = true)
            } else {
                KeyShortcut(Key.Q, ctrl = true)
            },
            onClick = ::exitApplication
        )
    }
}
```

---

## Edit Operations

| Action | macOS | Windows | Linux | Notes |
|--------|-------|---------|-------|-------|
| **Undo** | Cmd+Z | Ctrl+Z | Ctrl+Z | Universal |
| **Redo** | Cmd+Shift+Z | Ctrl+Y | Ctrl+Y | Windows/Linux use Y |
| **Cut** | Cmd+X | Ctrl+X | Ctrl+X | Universal |
| **Copy** | Cmd+C | Ctrl+C | Ctrl+C | Universal |
| **Paste** | Cmd+V | Ctrl+V | Ctrl+V | Universal |
| **Select All** | Cmd+A | Ctrl+A | Ctrl+A | Universal |
| **Find** | Cmd+F | Ctrl+F | Ctrl+F | Search |
| **Find Next** | Cmd+G | F3 | F3 | Next result |
| **Replace** | Cmd+Option+F | Ctrl+H | Ctrl+H | Find & replace |

**Note:** Undo/Redo typically handled by text fields automatically.

---

## Navigation

| Action | macOS | Windows | Linux | Notes |
|--------|-------|---------|-------|-------|
| **Tab 1** | Cmd+1 | Ctrl+1 | Ctrl+1 | First tab/view |
| **Tab 2** | Cmd+2 | Ctrl+2 | Ctrl+2 | Second tab/view |
| **Tab 3** | Cmd+3 | Ctrl+3 | Ctrl+3 | Third tab/view |
| **Next Tab** | Cmd+Option+→ | Ctrl+Tab | Ctrl+Tab | Cycle forward |
| **Prev Tab** | Cmd+Option+← | Ctrl+Shift+Tab | Ctrl+Shift+Tab | Cycle back |
| **Go Back** | Cmd+[ | Alt+← | Alt+← | Browser-style |
| **Go Forward** | Cmd+] | Alt+→ | Alt+→ | Browser-style |

**Compose Implementation:**

```kotlin
Window(
    onPreviewKeyEvent = { event ->
        if (event.type == KeyEventType.KeyDown) {
            when {
                event.key == Key.One && event.isPrimaryPressed() -> {
                    navigateTo(AppScreen.Feed)
                    true
                }
                event.key == Key.Two && event.isPrimaryPressed() -> {
                    navigateTo(AppScreen.Search)
                    true
                }
                event.key == Key.Three && event.isPrimaryPressed() -> {
                    navigateTo(AppScreen.Messages)
                    true
                }
                else -> false
            }
        } else false
    }
) {
    // Content
}

// Helper extension
fun KeyEvent.isPrimaryPressed() = if (isMacOS) isMetaPressed else isCtrlPressed
```

---

## Window Management

| Action | macOS | Windows | Linux | Notes |
|--------|-------|---------|-------|-------|
| **New Window** | Cmd+N | Ctrl+N | Ctrl+N | New instance |
| **Close Window** | Cmd+W | Alt+F4 | Alt+F4 | Close current |
| **Minimize** | Cmd+M | Win+Down | Super+Down | Minimize to dock/taskbar |
| **Maximize** | Cmd+Ctrl+F | Win+Up | Super+Up | Fullscreen/maximize |
| **Hide App** | Cmd+H | - | - | macOS only |
| **Switch Window** | Cmd+` | Alt+Tab | Alt+Tab | Between app windows |

**Note:** Window management often handled by OS, not app shortcuts.

---

## App-Specific (Amethyst)

### Nostr Actions

| Action | macOS | Windows | Linux | Description |
|--------|-------|---------|-------|-------------|
| **New Note** | Cmd+N | Ctrl+N | Ctrl+N | Compose new post |
| **Refresh Feed** | Cmd+R | Ctrl+R | Ctrl+R | Reload timeline |
| **Search** | Cmd+K | Ctrl+K | Ctrl+K | Quick search |
| **DMs** | Cmd+Shift+M | Ctrl+Shift+M | Ctrl+Shift+M | Open messages |
| **Settings** | Cmd+, | Ctrl+, | Ctrl+, | Open preferences |
| **Notifications** | Cmd+Shift+N | Ctrl+Shift+N | Ctrl+Shift+N | View alerts |

**Implementation:**

```kotlin
MenuBar {
    Menu("File") {
        Item(
            "New Note",
            shortcut = DesktopShortcuts.primary(Key.N),
            onClick = { showComposeDialog() }
        )
        Item(
            "Settings",
            shortcut = DesktopShortcuts.primary(Key.Comma),
            onClick = { navigateTo(AppScreen.Settings) }
        )
    }
    Menu("View") {
        Item(
            "Refresh Feed",
            shortcut = DesktopShortcuts.primary(Key.R),
            onClick = { refreshFeed() }
        )
        Item(
            "Search",
            shortcut = DesktopShortcuts.primary(Key.K),
            onClick = { focusSearch() }
        )
    }
}
```

---

## Accessibility

| Action | macOS | Windows | Linux | Description |
|--------|-------|---------|-------|-------------|
| **Zoom In** | Cmd++ | Ctrl++ | Ctrl++ | Increase size |
| **Zoom Out** | Cmd+- | Ctrl+- | Ctrl+- | Decrease size |
| **Reset Zoom** | Cmd+0 | Ctrl+0 | Ctrl+0 | Default size |
| **Help** | Cmd+? | F1 | F1 | Show help |

---

## Best Practices

### 1. OS-Aware Helper

Create a utility for OS detection:

```kotlin
// commons/src/jvmMain/kotlin/utils/PlatformShortcuts.kt
object DesktopShortcuts {
    private val isMacOS = System.getProperty("os.name")
        .lowercase()
        .contains("mac")

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

    fun primaryAlt(key: Key) = if (isMacOS) {
        KeyShortcut(key, meta = true, alt = true)
    } else {
        KeyShortcut(key, ctrl = true, alt = true)
    }

    val modifierName = if (isMacOS) "Cmd" else "Ctrl"
    val secondaryName = if (isMacOS) "Option" else "Alt"
}
```

**Usage:**

```kotlin
Item(
    "Save",
    shortcut = DesktopShortcuts.primary(Key.S),
    onClick = { save() }
)
```

### 2. Show Shortcuts in Tooltips

```kotlin
IconButton(
    onClick = { refresh() },
    modifier = Modifier.tooltipArea {
        Text("Refresh (${DesktopShortcuts.modifierName}+R)")
    }
) {
    Icon(Icons.Default.Refresh, "Refresh")
}
```

### 3. Shortcuts Menu

Provide a "Keyboard Shortcuts" help menu:

```kotlin
Menu("Help") {
    Item("Keyboard Shortcuts", onClick = { showShortcutsDialog() })
}

// Dialog content
@Composable
fun ShortcutsDialog() {
    Dialog(onDismissRequest = { /* close */ }) {
        Surface {
            Column(Modifier.padding(16.dp)) {
                Text("Keyboard Shortcuts", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                ShortcutRow("New Note", "${DesktopShortcuts.modifierName}+N")
                ShortcutRow("Save", "${DesktopShortcuts.modifierName}+S")
                ShortcutRow("Search", "${DesktopShortcuts.modifierName}+K")
                ShortcutRow("Settings", "${DesktopShortcuts.modifierName}+,")
                // ...
            }
        }
    }
}

@Composable
fun ShortcutRow(action: String, shortcut: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(action, style = MaterialTheme.typography.bodyMedium)
        Text(
            shortcut,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### 4. Avoid Conflicts

**Check for OS-level shortcuts:**

| macOS Reserved | Description |
|----------------|-------------|
| Cmd+Tab | Switch apps |
| Cmd+Space | Spotlight |
| Cmd+H | Hide window |
| Cmd+M | Minimize |
| Cmd+Q | Quit |
| Cmd+W | Close window |

**Windows Reserved:**

| Windows Reserved | Description |
|-----------------|-------------|
| Win+D | Show desktop |
| Win+E | File Explorer |
| Win+L | Lock screen |
| Alt+Tab | Switch apps |
| Alt+F4 | Close window |

**Don't override these unless critical.**

---

## Testing Shortcuts

```kotlin
// Test OS detection
@Test
fun testOsDetection() {
    val osName = System.getProperty("os.name")
    println("OS: $osName")

    val isMacOS = osName.lowercase().contains("mac")
    println("Is macOS: $isMacOS")

    val shortcut = if (isMacOS) {
        KeyShortcut(Key.N, meta = true)
    } else {
        KeyShortcut(Key.N, ctrl = true)
    }

    println("Primary modifier for New: $shortcut")
}
```

---

## Current Issues in Amethyst

**Main.kt:105-123** hardcodes `ctrl = true`:

```kotlin
// ❌ WRONG: Hardcoded Ctrl (doesn't work on macOS)
Item(
    "New Note",
    shortcut = KeyShortcut(Key.N, ctrl = true),  // Should be Cmd on macOS
    onClick = { /* ... */ }
)
```

**Fix:**

```kotlin
// ✅ CORRECT: OS-aware
Item(
    "New Note",
    shortcut = DesktopShortcuts.primary(Key.N),
    onClick = { /* ... */ }
)
```

---

## References

- [macOS Keyboard Shortcuts](https://support.apple.com/en-us/102650)
- [Windows Keyboard Shortcuts](https://support.microsoft.com/en-us/windows/keyboard-shortcuts-in-windows-dcc61a57-8ff0-cffe-9796-cb9706c75eec)
- [GNOME Keyboard Shortcuts](https://help.gnome.org/users/gnome-help/stable/shell-keyboard-shortcuts.html)
- [Material Design: Keyboard Shortcuts](https://m3.material.io/foundations/interaction/keyboard)
- [Compose Desktop: Keyboard Events](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-keyboard.html)
