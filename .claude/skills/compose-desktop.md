# Compose Desktop Skill

## Desktop Application Entry Point

```kotlin
// desktopApp/src/jvmMain/kotlin/Main.kt
package com.vitorpamplona.amethyst.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*

fun main() = application {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    // System tray
    Tray(
        icon = painterResource("icon.png"),
        tooltip = "Amethyst",
        menu = {
            Item("Show", onClick = { windowState.isMinimized = false })
            Separator()
            Item("Exit", onClick = ::exitApplication)
        }
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Amethyst",
        icon = painterResource("icon.png")
    ) {
        MenuBar {
            Menu("File") {
                Item("New Note", shortcut = KeyShortcut(Key.N, ctrl = true)) { }
                Separator()
                Item("Settings", shortcut = KeyShortcut(Key.Comma, ctrl = true)) { }
                Separator()
                Item("Quit", shortcut = KeyShortcut(Key.Q, ctrl = true), onClick = ::exitApplication)
            }
            Menu("Edit") {
                Item("Copy", shortcut = KeyShortcut(Key.C, ctrl = true)) { }
                Item("Paste", shortcut = KeyShortcut(Key.V, ctrl = true)) { }
            }
            Menu("View") {
                Item("Feed") { }
                Item("Messages") { }
                Item("Notifications") { }
            }
            Menu("Help") {
                Item("About Amethyst") { }
            }
        }

        App()
    }
}
```

## Desktop-Specific Components

### File Dialog
```kotlin
@Composable
fun rememberFileDialog(): FileDialogState {
    return remember { FileDialogState() }
}

class FileDialogState {
    var isOpen by mutableStateOf(false)
    var result by mutableStateOf<File?>(null)

    fun open() { isOpen = true }

    @Composable
    fun Dialog(
        title: String = "Select File",
        allowedExtensions: List<String> = emptyList()
    ) {
        if (isOpen) {
            DisposableEffect(Unit) {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, title)
                if (allowedExtensions.isNotEmpty()) {
                    dialog.setFilenameFilter { _, name ->
                        allowedExtensions.any { name.endsWith(it) }
                    }
                }
                dialog.isVisible = true
                result = dialog.file?.let { File(dialog.directory, it) }
                isOpen = false
                onDispose { }
            }
        }
    }
}
```

### Scroll Behavior
```kotlin
@Composable
fun DesktopScrollableColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()

    Box(modifier) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxSize()
        ) {
            content()
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}
```

### Keyboard Navigation
```kotlin
@Composable
fun KeyboardNavigableList(
    items: List<Note>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onActivate: (Note) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                when {
                    event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                        onSelect((selectedIndex + 1).coerceAtMost(items.lastIndex))
                        true
                    }
                    event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                        onSelect((selectedIndex - 1).coerceAtLeast(0))
                        true
                    }
                    event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                        items.getOrNull(selectedIndex)?.let { onActivate(it) }
                        true
                    }
                    else -> false
                }
            }
    ) {
        itemsIndexed(items) { index, note ->
            NoteCard(
                note = note,
                isSelected = index == selectedIndex,
                modifier = Modifier.clickable { onSelect(index) }
            )
        }
    }
}
```

### Multi-Window Support
```kotlin
@Composable
fun ApplicationScope.NoteDetailWindow(
    note: Note,
    onClose: () -> Unit
) {
    Window(
        onCloseRequest = onClose,
        title = "Note by ${note.author.name}",
        state = rememberWindowState(width = 600.dp, height = 400.dp)
    ) {
        NoteDetailScreen(note)
    }
}

// Usage in main application
var openNotes by remember { mutableStateOf<List<Note>>(emptyList()) }

openNotes.forEach { note ->
    key(note.id) {
        NoteDetailWindow(
            note = note,
            onClose = { openNotes = openNotes - note }
        )
    }
}
```

### Tooltips
```kotlin
@Composable
fun TooltipButton(
    tooltip: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    text = tooltip,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) {
        IconButton(onClick = onClick) {
            content()
        }
    }
}
```

## Desktop Layout Pattern

```kotlin
@Composable
fun DesktopAppLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    content: @Composable () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        // Sidebar navigation
        NavigationRail(
            modifier = Modifier.width(72.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Spacer(Modifier.height(16.dp))

            NavigationRailItem(
                icon = { Icon(Icons.Default.Home, "Feed") },
                label = { Text("Feed") },
                selected = currentScreen == Screen.Feed,
                onClick = { onNavigate(Screen.Feed) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Email, "Messages") },
                label = { Text("DMs") },
                selected = currentScreen == Screen.Messages,
                onClick = { onNavigate(Screen.Messages) }
            )

            NavigationRailItem(
                icon = { Icon(Icons.Default.Notifications, "Notifications") },
                label = { Text("Alerts") },
                selected = currentScreen == Screen.Notifications,
                onClick = { onNavigate(Screen.Notifications) }
            )

            Spacer(Modifier.weight(1f))

            NavigationRailItem(
                icon = { Icon(Icons.Default.Settings, "Settings") },
                label = { Text("Settings") },
                selected = currentScreen == Screen.Settings,
                onClick = { onNavigate(Screen.Settings) }
            )
        }

        // Divider
        VerticalDivider()

        // Main content
        Box(Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}
```

## Build Configuration

```kotlin
// desktopApp/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(project(":quartz"))
}

compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )

            packageName = "Amethyst"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.vitorpamplona.amethyst.desktop"
                iconFile.set(project.file("icons/icon.icns"))
            }

            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "Amethyst"
            }

            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
```
