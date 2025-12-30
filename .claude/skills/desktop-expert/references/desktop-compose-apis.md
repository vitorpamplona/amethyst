# Desktop Compose APIs Catalog

Complete reference for Compose Multiplatform Desktop-only APIs.

## Window Management

### application

Root entry point for desktop apps.

```kotlin
fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        Text("Hello Desktop")
    }
}
```

### Window

Creates a window.

```kotlin
Window(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    undecorated: Boolean = false,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    content: @Composable FrameWindowScope.() -> Unit
)
```

**Example:**
```kotlin
val windowState = rememberWindowState(
    width = 1200.dp,
    height = 800.dp,
    position = WindowPosition.Aligned(Alignment.Center)
)

Window(
    onCloseRequest = ::exitApplication,
    state = windowState,
    title = "My App",
    resizable = true
) {
    // Content
}
```

### rememberWindowState

Manages window size and position.

```kotlin
@Composable
fun rememberWindowState(
    placement: WindowPlacement = WindowPlacement.Floating,
    isMinimized: Boolean = false,
    position: WindowPosition = WindowPosition.PlatformDefault,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified
): WindowState
```

**WindowPlacement:**
- `Floating` - Normal window
- `Maximized` - Fullscreen
- `Fullscreen` - Fullscreen without decorations

**WindowPosition:**
- `PlatformDefault` - OS decides
- `Aligned(alignment)` - Center, TopStart, etc.
- `Absolute(x, y)` - Fixed position in pixels

### DialogWindow

Modal dialog.

```kotlin
DialogWindow(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    visible: Boolean = true,
    title: String = "Dialog",
    icon: Painter? = null,
    undecorated: Boolean = false,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    content: @Composable DialogWindowScope.() -> Unit
)
```

**Example:**
```kotlin
var showDialog by remember { mutableStateOf(false) }

if (showDialog) {
    DialogWindow(
        onCloseRequest = { showDialog = false },
        title = "Confirm"
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Are you sure?")
            Row {
                Button(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
                Button(onClick = { /* confirm */ }) {
                    Text("OK")
                }
            }
        }
    }
}
```

---

## MenuBar

### MenuBar

Native menu bar for windows.

```kotlin
@Composable
fun FrameWindowScope.MenuBar(
    content: @Composable MenuBarScope.() -> Unit
)
```

**Example:**
```kotlin
Window(onCloseRequest = ::exitApplication) {
    MenuBar {
        Menu("File") {
            Item("New", onClick = { /* ... */ })
            Item("Open", onClick = { /* ... */ })
            Separator()
            Item("Quit", onClick = ::exitApplication)
        }
        Menu("Edit") {
            Item("Copy", onClick = { /* ... */ })
            Item("Paste", onClick = { /* ... */ })
        }
    }
}
```

### Menu

Top-level menu.

```kotlin
@Composable
fun MenuBarScope.Menu(
    text: String,
    mnemonic: Char? = null,
    enabled: Boolean = true,
    content: @Composable MenuScope.() -> Unit
)
```

### Item

Menu item.

```kotlin
@Composable
fun MenuScope.Item(
    text: String,
    onClick: () -> Unit,
    shortcut: KeyShortcut? = null,
    mnemonic: Char? = null,
    enabled: Boolean = true,
    icon: Painter? = null
)
```

**With keyboard shortcut:**
```kotlin
Item(
    text = "Save",
    onClick = { save() },
    shortcut = KeyShortcut(Key.S, ctrl = true),
    icon = painterResource("save.png")
)
```

### Separator

Menu separator line.

```kotlin
@Composable
fun MenuScope.Separator()
```

### CheckboxItem

Toggleable menu item.

```kotlin
@Composable
fun MenuScope.CheckboxItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shortcut: KeyShortcut? = null,
    mnemonic: Char? = null,
    enabled: Boolean = true
)
```

**Example:**
```kotlin
var darkMode by remember { mutableStateOf(false) }

Menu("View") {
    CheckboxItem(
        text = "Dark Mode",
        checked = darkMode,
        onCheckedChange = { darkMode = it },
        shortcut = KeyShortcut(Key.D, ctrl = true)
    )
}
```

### RadioButtonItem

Radio button menu item.

```kotlin
@Composable
fun MenuScope.RadioButtonItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    shortcut: KeyShortcut? = null,
    mnemonic: Char? = null,
    enabled: Boolean = true
)
```

---

## System Tray

### Tray

System tray icon with menu.

```kotlin
@Composable
fun ApplicationScope.Tray(
    icon: Painter,
    state: TrayState = rememberTrayState(),
    tooltip: String? = null,
    onAction: () -> Unit = {},
    menu: @Composable MenuScope.() -> Unit = {}
)
```

**Example:**
```kotlin
application {
    var isVisible by remember { mutableStateOf(true) }

    Tray(
        icon = painterResource("tray-icon.png"),
        tooltip = "My App",
        onAction = { isVisible = true },
        menu = {
            Item("Show Window", onClick = { isVisible = true })
            Separator()
            Item("Quit", onClick = ::exitApplication)
        }
    )

    if (isVisible) {
        Window(
            onCloseRequest = { isVisible = false },
            title = "App"
        ) {
            // Content
        }
    }
}
```

### rememberTrayState

Manages tray state.

```kotlin
@Composable
fun rememberTrayState(): TrayState
```

---

## Notifications

### Notification (via Tray)

Show desktop notifications through tray.

```kotlin
val trayState = rememberTrayState()

Tray(
    icon = painterResource("icon.png"),
    state = trayState
)

// Send notification
LaunchedEffect(Unit) {
    trayState.sendNotification(
        Notification(
            title = "Message",
            message = "You have a new message",
            type = Notification.Type.Info
        )
    )
}
```

**Notification types:**
- `Info` - Information
- `Warning` - Warning
- `Error` - Error

---

## Keyboard

### KeyShortcut

Keyboard shortcut definition.

```kotlin
data class KeyShortcut(
    val key: Key,
    val ctrl: Boolean = false,
    val meta: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false
)
```

**Examples:**
```kotlin
// Ctrl+S (Windows/Linux)
KeyShortcut(Key.S, ctrl = true)

// Cmd+S (macOS)
KeyShortcut(Key.S, meta = true)

// Ctrl+Shift+N
KeyShortcut(Key.N, ctrl = true, shift = true)

// Alt+F4
KeyShortcut(Key.F4, alt = true)
```

### onPreviewKeyEvent / onKeyEvent

Window-level keyboard handlers.

```kotlin
Window(
    onCloseRequest = ::exitApplication,
    onPreviewKeyEvent = { event ->
        if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
            // Handle Escape
            true  // Consume event
        } else {
            false  // Propagate
        }
    }
) {
    // Content
}
```

---

## Mouse

### PointerMoveFilter (Deprecated, use Modifier.pointerInput)

```kotlin
Box(
    modifier = Modifier
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    // Handle mouse events
                }
            }
        }
)
```

### Mouse cursor

```kotlin
Box(
    modifier = Modifier.pointerHoverIcon(
        icon = PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    )
) {
    Text("Hover me")
}
```

**Cursor types:**
- `DEFAULT_CURSOR`
- `HAND_CURSOR`
- `TEXT_CURSOR`
- `CROSSHAIR_CURSOR`
- `WAIT_CURSOR`
- `MOVE_CURSOR`
- `E_RESIZE_CURSOR`, `W_RESIZE_CURSOR`, etc.

---

## Drag & Drop (Experimental)

### onExternalDrag

Handle drag-and-drop from external sources.

```kotlin
Box(
    modifier = Modifier
        .size(200.dp)
        .background(Color.LightGray)
        .onExternalDrag(
            onDragStart = { externalDragValue ->
                println("Drag started")
            },
            onDrag = { externalDragValue ->
                println("Dragging: ${externalDragValue.dragData}")
            },
            onDragExit = {
                println("Drag exited")
            },
            onDrop = { externalDragValue ->
                val dragData = externalDragValue.dragData
                when (dragData) {
                    is DragData.FilesList -> {
                        println("Files dropped: ${dragData.readFiles()}")
                    }
                    is DragData.Text -> {
                        println("Text dropped: ${dragData.readText()}")
                    }
                    else -> {}
                }
            }
        )
) {
    Text("Drop files here", Modifier.align(Alignment.Center))
}
```

---

## Resources

### painterResource

Load images from resources.

```kotlin
val icon = painterResource("icon.png")

Icon(
    painter = icon,
    contentDescription = "App icon"
)
```

**Resource location:** `src/jvmMain/resources/`

---

## Platform Integration

### Desktop.getDesktop() (AWT)

Access system desktop features (not Compose API, but commonly used).

```kotlin
import java.awt.Desktop
import java.net.URI

// Open URL in browser
if (Desktop.isDesktopSupported()) {
    Desktop.getDesktop().browse(URI("https://example.com"))
}

// Open file with default app
Desktop.getDesktop().open(File("/path/to/file.pdf"))

// Open email client
Desktop.getDesktop().mail(URI("mailto:user@example.com"))
```

### FileDialog (AWT)

File picker dialogs.

```kotlin
import java.awt.FileDialog
import java.awt.Frame

// Open file
val fileDialog = FileDialog(Frame(), "Select file", FileDialog.LOAD)
fileDialog.isVisible = true
val selectedFile = fileDialog.file
val directory = fileDialog.directory

// Save file
val saveDialog = FileDialog(Frame(), "Save file", FileDialog.SAVE)
saveDialog.file = "document.txt"
saveDialog.isVisible = true
```

---

## SwingPanel (Interop)

Embed Swing components in Compose.

```kotlin
import androidx.compose.ui.awt.SwingPanel
import javax.swing.JButton

SwingPanel(
    factory = {
        JButton("Swing Button").apply {
            addActionListener {
                println("Swing button clicked")
            }
        }
    },
    modifier = Modifier.size(200.dp, 50.dp)
)
```

---

## ComposePanel (Reverse Interop)

Embed Compose in Swing.

```kotlin
import androidx.compose.ui.awt.ComposePanel
import javax.swing.JFrame

val frame = JFrame("Swing Frame")
val composePanel = ComposePanel()

composePanel.setContent {
    Text("Compose in Swing")
}

frame.contentPane.add(composePanel)
frame.setSize(400, 300)
frame.isVisible = true
```

---

## Version Requirements

- **Kotlin:** 2.0+
- **Compose Multiplatform:** 1.7.0+
- **JVM Target:** 11+ (recommend 21)

**See also:**
- [Official Desktop API docs](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-components.html)
- [Compose Multiplatform repo](https://github.com/JetBrains/compose-multiplatform)
