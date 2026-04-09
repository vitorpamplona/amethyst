# Amethyst iOS App

Compose Multiplatform iOS client for Nostr, sharing code with the Android and Desktop apps.

## Architecture

- **iosApp** — iOS-specific entry point and UI
- **commons** — Shared KMP UI components (now with iOS targets)
- **quartz** — Nostr protocol library (already had iOS targets)

## Build

### Prerequisites

- Xcode 15+ with iOS Simulator
- `brew install libsodium`
- Java 21+

### Build Framework (Gradle)

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :iosApp:linkDebugFrameworkIosSimulatorArm64
```

### Run in Xcode

1. Open `iosApp/iosApp.xcodeproj`
2. Select "iosApp" scheme and iPhone simulator
3. ⌘R to build and run

The Xcode build phase calls `embedAndSignAppleFrameworkForXcode` automatically.

## Key Learnings (KMP iOS)

### Kotlin/Native Obj-C Header Export

- Functions/classes returning `UIViewController` (or other UIKit types) are **NOT exported** in the generated Obj-C umbrella header
- Workaround: return `Any` instead of `UIViewController`, then cast in Swift: `as! UIViewController`
- Only `commonMain` symbols are exported; `iosMain`-only symbols are compiled but invisible to Swift
- Top-level functions in packages get mangled names; prefer classes for cleaner Swift interop

### Framework Bundle ID

- The framework's `binaryOption("bundleId", ...)` must differ from the app's `PRODUCT_BUNDLE_IDENTIFIER`
- Otherwise iOS refuses to install: "parent bundle has same identifier as sub-bundle"

### Dependencies Without iOS Variants

These JVM-only dependencies must NOT be in `commonMain` when iOS targets are present:
- `lifecycle-viewmodel-compose` (use `lifecycle-viewmodel` instead — it has iOS)
- `coil-network-okhttp` (use `coil-network-ktor3` for iOS)
- `richtext-commonmark`, `richtext-ui`, `richtext-ui-material3` (JitPack, JVM-only)

### Java APIs Requiring KMP Migration

| Java API | KMP Replacement |
|----------|-----------------|
| `synchronized(lock) {}` | `expect/actual platformSynchronized()` with NSRecursiveLock on iOS |
| `ConcurrentHashMap` | `HashMap` with synchronized access |
| `AtomicBoolean/AtomicLong` | `@Volatile var` |
| `String(CharArray)` | `charArray.concatToString()` |
| `Math.round/pow/etc` | `kotlin.math.*` |
| `Character.charCount()` | Custom helpers with `Char.isHighSurrogate()` |
| `String.format()` | String interpolation or `.replace()` |
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` |
| `java.net.URL` | Move to jvmAndroid or use Ktor |
| `BigDecimal` | Use quartz's KMP version |
| `WeakReference` | Nullable fields |

### iOS Native WebSocket
OkHttp doesn't work on iOS. Use `NSURLSessionWebSocketTask` for WebSocket connections:
- Create via `NSURLSession.sharedSession.webSocketTaskWithURL()`
- Send: `task.sendMessage(NSURLSessionWebSocketMessage(string))` with completion handler
- Receive: recursive `task.receiveMessageWithCompletionHandler()` loop
- Close: `task.cancelWithCloseCode()`

### Bottom Tab Navigation
Use Compose `NavigationBar` with `NavigationBarItem`. Keep state with `mutableStateOf<Screen>()` enum.

### Compose Multiplatform iOS Entry Point

```kotlin
// In commonMain (NOT iosMain!) — only commonMain symbols are exported
class IosEntryPoint {
    // Return Any, not UIViewController — UIViewController gets filtered from Obj-C header
    fun createViewController(): Any = ComposeUIViewController { App() }
}
```

```swift
// In Swift
let entryPoint = IosEntryPoint()
let vc = entryPoint.createViewController() as! UIViewController
window?.rootViewController = vc
```
