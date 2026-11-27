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

Linking the framework and running the app need a Mac with Xcode and XcodeGen:

```bash
brew install xcodegen
cd iosApp/xcode && xcodegen && open Amethyst.xcodeproj
```

Xcode's pre-build step runs `./gradlew :iosApp:embedAndSignAppleFrameworkForXcode`, so pick a
simulator (Apple silicon) and press Run. The app links Arti, the Tor client, from the archives
committed in `libs/arti/<triple>/libarti.a`, the way Android ships `jniLibs/`: no Rust toolchain
is needed to build the app. Only a change to `tools/arti-build` needs rustup, to rebuild and
commit those archives (`tools/arti-build/README.md`, "iOS"). CI's `build-ios-app` job links the
framework and builds the project unsigned on every pull request.

## Tor (Arti)

The Rust Tor client from `tools/arti-build` is bound through a cinterop
(`src/nativeInterop/cinterop/arti.def`, Kotlin side in `src/iosArtiMain/…/ios/tor/ArtiNative.kt`),
set up on macOS hosts only; elsewhere `src/iosNoArtiMain` stands in and Tor reports unavailable.
`IosAppModules` runs the shared `TorService`/`TorManager` over it, and every connection picks its
route from the shared Tor settings: relays by `TorRelayState`, HTTP roles (images, video, previews,
money, NIP-05, uploads) by `RoleBasedKtorClients`, through Tor's SOCKS port on `localhost` or
direct. A request that must go through Tor while Tor is down fails rather than going direct.
Plan: [`commons/plans/2026-11-25-ios-tor.md`](../commons/plans/2026-11-25-ios-tor.md) (B12d). The
simulator tests link the same committed archives (`tools/arti-build/README.md`, "iOS"):

```bash
./gradlew :iosApp:iosSimulatorArm64Test                          # Arti lifecycle, no network
xcrun simctl boot <udid>                                         # HTTPS needs a booted simulator
./gradlew :iosApp:iosSimulatorArm64Test -Pamethyst.arti.network=true -Pamethyst.ios.testSimulator=<udid>
```

## What is not wired yet

Each of these throws or stays off, and names the plan step that fills it: Blossom and Cordn blobs,
OpenTimestamps and namecoin lookups (A2), media pickers, playback and uploads (B4, B5),
notifications (B6). A video that must go through Tor but would play off the video cache (HLS, a
live stream) is refused with a message (B12e). Marmot groups keep their state in memory until
their stores move to okio (A3).
