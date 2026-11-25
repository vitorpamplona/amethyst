# iosApp

The iOS shim over the shared app: the same `AmethystApp` (from `commonsUI`) that Android and the
desktop run. Plan and status: [`commons/plans/2026-10-09-ios-app.md`](../commons/plans/2026-10-09-ios-app.md).
This is step B0, the walking skeleton: a read-only `npub` login and the feeds.

| Where | What |
|---|---|
| `src/iosMain/…/ios/` | The Kotlin shim: `MainViewController()` (the entry Swift calls), the service graph (`IosAppModules`), and the ports the shared UI asks for (`IosAppServices`, `IosAccountViewModelHost`, `IosAppRoot`) |
| `xcode/project.yml` | XcodeGen spec for the app target; the Xcode project is generated from it, not committed |
| `xcode/Amethyst/AmethystApp.swift` | The Swift `@main`, which only hosts `MainViewController()` |

## Build

Compiling the Kotlin works on any host, Linux included:

```bash
./gradlew :iosApp:compileKotlinIosSimulatorArm64
```

Linking the framework and running the app need a Mac with Xcode:

```bash
brew install xcodegen
cd iosApp/xcode && xcodegen && open Amethyst.xcodeproj
```

Xcode's pre-build step runs `./gradlew :iosApp:embedAndSignAppleFrameworkForXcode`, so pick a
simulator and press Run. CI's `build-ios-app` job links the framework and builds the project
unsigned on every pull request.

## What is not wired yet

Each of these throws or stays off, and names the plan step that fills it: Blossom and Cordn blobs,
OpenTimestamps and namecoin lookups (A2), media pickers, playback and uploads (B4, B5),
notifications (B6), Tor (B12). Marmot groups keep their state in memory until their stores move to
okio (A3).
