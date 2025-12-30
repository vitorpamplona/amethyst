# OS Detection & Platform-Specific Code

Patterns for detecting operating system and implementing platform-specific behavior in Compose Desktop.

## OS Detection

### Basic Detection

```kotlin
val osName = System.getProperty("os.name").lowercase()

val isMacOS = osName.contains("mac")
val isWindows = osName.contains("win")
val isLinux = osName.contains("nux") || osName.contains("nix")
```

### System Properties

```kotlin
// OS name
System.getProperty("os.name")
// Examples: "Mac OS X", "Windows 10", "Linux"

// OS version
System.getProperty("os.version")
// Examples: "14.2.1", "10.0", "6.5.0-14-generic"

// OS architecture
System.getProperty("os.arch")
// Examples: "aarch64", "x86_64", "amd64"

// User home directory
System.getProperty("user.home")
// Examples: "/Users/username", "C:\Users\username", "/home/username"

// File separator
System.getProperty("file.separator")
// Examples: "/" (Unix), "\" (Windows)

// Path separator
System.getProperty("path.separator")
// Examples: ":" (Unix), ";" (Windows)
```

---

## PlatformDetector Utility

Create a centralized utility for platform detection.

**File:** `commons/src/jvmMain/kotlin/utils/PlatformDetector.kt`

```kotlin
package com.vitorpamplona.amethyst.commons.utils

object PlatformDetector {
    private val osName = System.getProperty("os.name").lowercase()

    val isMacOS: Boolean = osName.contains("mac")
    val isWindows: Boolean = osName.contains("win")
    val isLinux: Boolean = osName.contains("nux") || osName.contains("nix")

    val platform: Platform = when {
        isMacOS -> Platform.MacOS
        isWindows -> Platform.Windows
        isLinux -> Platform.Linux
        else -> Platform.Unknown
    }

    enum class Platform {
        MacOS,
        Windows,
        Linux,
        Unknown
    }

    // File paths
    val fileSeparator: String = System.getProperty("file.separator")
    val pathSeparator: String = System.getProperty("path.separator")

    // User directories
    val userHome: String = System.getProperty("user.home")

    val appDataDir: String = when (platform) {
        Platform.MacOS -> "$userHome/Library/Application Support"
        Platform.Windows -> System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
        Platform.Linux -> System.getenv("XDG_CONFIG_HOME") ?: "$userHome/.config"
        Platform.Unknown -> userHome
    }

    // Modifier key names
    val primaryModifierName: String = if (isMacOS) "Cmd" else "Ctrl"
    val secondaryModifierName: String = if (isMacOS) "Option" else "Alt"

    fun platformSpecific(
        macOS: () -> Unit = {},
        windows: () -> Unit = {},
        linux: () -> Unit = {},
        fallback: () -> Unit = {}
    ) {
        when (platform) {
            Platform.MacOS -> macOS()
            Platform.Windows -> windows()
            Platform.Linux -> linux()
            Platform.Unknown -> fallback()
        }
    }
}
```

**Usage:**

```kotlin
// Simple check
if (PlatformDetector.isMacOS) {
    // macOS-specific code
}

// Pattern matching
when (PlatformDetector.platform) {
    Platform.MacOS -> setupMacDock()
    Platform.Windows -> setupWindowsTray()
    Platform.Linux -> setupLinuxTray()
    Platform.Unknown -> showWarning()
}

// Platform-specific execution
PlatformDetector.platformSpecific(
    macOS = { setupMacMenuBar() },
    windows = { setupWindowsMenu() },
    linux = { setupLinuxMenu() }
)

// File paths
val configPath = "${PlatformDetector.appDataDir}${PlatformDetector.fileSeparator}amethyst"
```

---

## Platform-Specific UI

### Keyboard Shortcuts Helper

```kotlin
package com.vitorpamplona.amethyst.commons.utils

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut

object DesktopShortcuts {
    private val isMacOS = PlatformDetector.isMacOS

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

    val modifierName = PlatformDetector.primaryModifierName
    val secondaryName = PlatformDetector.secondaryModifierName

    fun formatShortcut(key: String, withPrimary: Boolean = true): String {
        return if (withPrimary) "$modifierName+$key" else key
    }
}
```

### File Paths Helper

```kotlin
package com.vitorpamplona.amethyst.commons.utils

import java.io.File

object FilePaths {
    private val separator = PlatformDetector.fileSeparator

    fun join(vararg parts: String): String {
        return parts.joinToString(separator)
    }

    fun appConfig(appName: String): String {
        return join(PlatformDetector.appDataDir, appName)
    }

    fun appCache(appName: String): String {
        return when (PlatformDetector.platform) {
            PlatformDetector.Platform.MacOS ->
                join(PlatformDetector.userHome, "Library", "Caches", appName)
            PlatformDetector.Platform.Windows ->
                join(System.getenv("LOCALAPPDATA") ?: "${PlatformDetector.userHome}\\AppData\\Local", appName)
            PlatformDetector.Platform.Linux ->
                join(System.getenv("XDG_CACHE_HOME") ?: "${PlatformDetector.userHome}/.cache", appName)
            else -> join(PlatformDetector.userHome, ".cache", appName)
        }
    }

    fun ensureDirectory(path: String): File {
        return File(path).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
}

// Usage
val configDir = FilePaths.ensureDirectory(FilePaths.appConfig("amethyst"))
val cacheDir = FilePaths.ensureDirectory(FilePaths.appCache("amethyst"))
```

---

## Platform-Specific Features

### Open External URL

```kotlin
// commons/src/commonMain/kotlin/utils/ExternalUrl.kt
expect fun openExternalUrl(url: String)

// commons/src/jvmMain/kotlin/utils/ExternalUrl.jvm.kt
import java.awt.Desktop
import java.net.URI

actual fun openExternalUrl(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    }
}
```

### File Picker

```kotlin
// Platform-specific file picker
fun showFilePicker(
    title: String = "Select file",
    mode: FilePickerMode = FilePickerMode.Load
): String? {
    val fileDialog = java.awt.FileDialog(
        java.awt.Frame(),
        title,
        when (mode) {
            FilePickerMode.Load -> java.awt.FileDialog.LOAD
            FilePickerMode.Save -> java.awt.FileDialog.SAVE
        }
    )

    // macOS-specific: Enable file selection features
    if (PlatformDetector.isMacOS) {
        System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }

    fileDialog.isVisible = true

    return fileDialog.file?.let { "${fileDialog.directory}$it" }
}

enum class FilePickerMode {
    Load,
    Save
}
```

### Directory Picker (macOS)

```kotlin
fun showDirectoryPicker(title: String = "Select directory"): String? {
    if (PlatformDetector.isMacOS) {
        // macOS-specific directory picker
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
    }

    val fileDialog = java.awt.FileDialog(java.awt.Frame(), title, java.awt.FileDialog.LOAD)
    fileDialog.isVisible = true

    if (PlatformDetector.isMacOS) {
        System.setProperty("apple.awt.fileDialogForDirectories", "false")
    }

    return fileDialog.directory
}
```

---

## Window Decorations

### macOS-Specific

```kotlin
// Unified title bar (macOS Big Sur+)
if (PlatformDetector.isMacOS) {
    Window(
        undecorated = false,
        transparent = true,
        // ...
    ) {
        // Custom title bar
    }
}
```

### Windows-Specific

```kotlin
// Custom window chrome (Windows)
if (PlatformDetector.isWindows) {
    Window(
        undecorated = true,
        // Custom decorations
    ) {
        Column {
            // Custom title bar with min/max/close buttons
            WindowTitleBar()
            // Content
        }
    }
}
```

---

## System Tray Icons

Different icon formats per OS:

```kotlin
fun getTrayIcon(): Painter {
    return when (PlatformDetector.platform) {
        Platform.MacOS -> painterResource("tray-icon-mac.png")  // Template icon
        Platform.Windows -> painterResource("tray-icon-win.ico")
        Platform.Linux -> painterResource("tray-icon-linux.png")
        else -> painterResource("tray-icon.png")
    }
}

// macOS: Template icons (black/transparent)
// Windows: ICO format, 16x16
// Linux: PNG, typically 24x24
```

---

## Native Notifications

```kotlin
// Platform-specific notification implementation
fun sendNotification(title: String, message: String) {
    PlatformDetector.platformSpecific(
        macOS = {
            // macOS: Use NSUserNotification (via tray)
            trayState.sendNotification(
                Notification(title, message, Notification.Type.Info)
            )
        },
        windows = {
            // Windows: Use Windows toast notifications
            trayState.sendNotification(
                Notification(title, message, Notification.Type.Info)
            )
        },
        linux = {
            // Linux: Use libnotify (via tray)
            trayState.sendNotification(
                Notification(title, message, Notification.Type.Info)
            )
        }
    )
}
```

---

## Architecture Detection

```kotlin
object ArchDetector {
    private val arch = System.getProperty("os.arch").lowercase()

    val isArm: Boolean = arch.contains("aarch") || arch.contains("arm")
    val isX64: Boolean = arch.contains("x86_64") || arch.contains("amd64")
    val isX86: Boolean = arch.contains("x86") && !isX64

    val architecture: Architecture = when {
        isArm -> Architecture.ARM
        isX64 -> Architecture.X64
        isX86 -> Architecture.X86
        else -> Architecture.Unknown
    }

    enum class Architecture {
        ARM,
        X64,
        X86,
        Unknown
    }
}

// Usage: Load correct native library
fun loadNativeLib() {
    val libName = when {
        PlatformDetector.isMacOS && ArchDetector.isArm -> "libsecp256k1-macos-arm64"
        PlatformDetector.isMacOS && ArchDetector.isX64 -> "libsecp256k1-macos-x64"
        PlatformDetector.isWindows && ArchDetector.isX64 -> "libsecp256k1-win-x64"
        PlatformDetector.isLinux && ArchDetector.isX64 -> "libsecp256k1-linux-x64"
        else -> throw UnsupportedOperationException("Unsupported platform")
    }

    System.loadLibrary(libName)
}
```

---

## Testing Platform Detection

```kotlin
@Test
fun testPlatformDetection() {
    println("OS: ${System.getProperty("os.name")}")
    println("Version: ${System.getProperty("os.version")}")
    println("Arch: ${System.getProperty("os.arch")}")
    println()
    println("Is macOS: ${PlatformDetector.isMacOS}")
    println("Is Windows: ${PlatformDetector.isWindows}")
    println("Is Linux: ${PlatformDetector.isLinux}")
    println("Platform: ${PlatformDetector.platform}")
    println()
    println("User home: ${PlatformDetector.userHome}")
    println("App data: ${PlatformDetector.appDataDir}")
    println("File separator: ${PlatformDetector.fileSeparator}")
}

// Example output (macOS):
// OS: Mac OS X
// Version: 14.2.1
// Arch: aarch64
//
// Is macOS: true
// Is Windows: false
// Is Linux: false
// Platform: MacOS
//
// User home: /Users/username
// App data: /Users/username/Library/Application Support
// File separator: /
```

---

## Best Practices

### 1. Centralize Detection

✅ **DO:** Use PlatformDetector singleton
```kotlin
if (PlatformDetector.isMacOS) { /* ... */ }
```

❌ **DON'T:** Repeat detection everywhere
```kotlin
if (System.getProperty("os.name").lowercase().contains("mac")) { /* ... */ }
```

### 2. Use expect/actual for Platform APIs

```kotlin
// commonMain
expect fun openFile(path: String)

// jvmMain (Desktop)
actual fun openFile(path: String) {
    Desktop.getDesktop().open(File(path))
}

// androidMain
actual fun openFile(path: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(path)))
}
```

### 3. Graceful Degradation

```kotlin
fun openBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            // Fallback: Copy to clipboard
            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                StringSelection(url),
                null
            )
            showMessage("URL copied to clipboard: $url")
        }
    } catch (e: Exception) {
        showError("Failed to open browser: ${e.message}")
    }
}
```

### 4. Test on All Platforms

Always test platform-specific code on:
- macOS (Intel + Apple Silicon if possible)
- Windows (10/11)
- Linux (Ubuntu/Fedora)

---

## Common Patterns

### Pattern: Config File Location

```kotlin
fun getConfigFile(filename: String): File {
    val configDir = when (PlatformDetector.platform) {
        Platform.MacOS ->
            File("${PlatformDetector.userHome}/Library/Application Support/Amethyst")
        Platform.Windows ->
            File("${System.getenv("APPDATA")}\\Amethyst")
        Platform.Linux ->
            File("${PlatformDetector.userHome}/.config/amethyst")
        else ->
            File("${PlatformDetector.userHome}/.amethyst")
    }

    if (!configDir.exists()) {
        configDir.mkdirs()
    }

    return File(configDir, filename)
}

// Usage
val settingsFile = getConfigFile("settings.json")
```

### Pattern: Platform-Specific Resources

```kotlin
fun getPlatformIcon(name: String): Painter {
    val extension = when (PlatformDetector.platform) {
        Platform.MacOS -> "icns"
        Platform.Windows -> "ico"
        else -> "png"
    }

    return painterResource("$name.$extension")
}

// Resources:
// src/jvmMain/resources/app-icon.icns (macOS)
// src/jvmMain/resources/app-icon.ico (Windows)
// src/jvmMain/resources/app-icon.png (Linux)
```

---

## References

- [System Properties (Java)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#getProperties())
- [Desktop API (Java)](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/Desktop.html)
- [File System Standards (XDG)](https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html)
