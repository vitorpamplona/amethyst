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

Linking the framework and running the app need a Mac with Xcode, XcodeGen and **rustup** (the
Rust toolchain manager), because the app links Arti, the Tor client, built from source:

```bash
brew install xcodegen rustup   # or rustup from https://rustup.rs; either is found
cd iosApp/xcode && xcodegen && open Amethyst.xcodeproj
```

Xcode's pre-build step runs `./gradlew :iosApp:embedAndSignAppleFrameworkForXcode`, which first
runs `:iosApp:buildArtiIos` (`tools/arti-build/build-arti-ios.sh`) for the SDK you build. The
first time, rustup installs the pinned Rust (`tools/arti-build/rust-toolchain.toml`, 1.98.1) and
the iOS targets, the script clones Arti into `/tmp/amethyst-arti-build`, and the Rust build takes
a minute or two; after that it is skipped until `tools/arti-build` changes. The archives are not
committed. So pick a simulator (Apple silicon) and press Run. CI's `build-ios-app` job installs
the same toolchain, links the framework and builds the project unsigned on every pull request.

## Tor (Arti)

The Rust Tor client from `tools/arti-build` is bound through a cinterop
(`src/nativeInterop/cinterop/arti.def`, Kotlin side in `src/iosArtiMain/…/ios/tor/ArtiNative.kt`),
set up on macOS hosts only; elsewhere `src/iosNoArtiMain` stands in and Tor reports unavailable.
`IosAppModules` runs the shared `TorService`/`TorManager` over it, and every connection picks its
route from the shared Tor settings: relays by `TorRelayState`, HTTP roles (images, video, previews,
money, NIP-05, uploads) by `RoleBasedKtorClients`, through Tor's SOCKS port on `localhost` or
direct. A request that must go through Tor while Tor is down fails rather than going direct.
Plan: [`commons/plans/2026-11-25-ios-tor.md`](../commons/plans/2026-11-25-ios-tor.md) (B12d). The
simulator tests link Arti too; `:iosApp:buildArtiIos` builds the static libraries first (see
`tools/arti-build/README.md`, "iOS"):

```bash
./gradlew :iosApp:iosSimulatorArm64Test                          # Arti lifecycle, no network
xcrun simctl boot <udid>                                         # HTTPS needs a booted simulator
./gradlew :iosApp:iosSimulatorArm64Test -Pamethyst.arti.network=true -Pamethyst.ios.testSimulator=<udid>
```

## What is not wired yet

Each of these stays off on iOS, and names the plan step that fills it:

- **Notifications** (B6): no APNs registration, Notification Service Extension or background
  catch-up. Needs the push server's APNs work, outside this repo.
- **Links and sharing** (B7): the `nostr:` URL scheme, a share extension, and the share sheet
  (`ImageSharer`/`FileSharer`; sharing text copies it).
- **Calls** (B10, WebRTC) and **audio rooms** (B11): need a WebRTC framework, and quic-kmp and
  `nestsClient` iOS builds.
- **Screens still in `jvmAndroid`** (A4): Git repositories, the in-app browser,
  scan QR from a shared image, My Fitness, Now Playing settings, Resource usage, the HLS composer.
- **On-device AI and media extras** (B13): translation, the writing assistant, alt-text
  suggestions, the voice anonymizer, the trim preview, Cast, the front page printer, Routstr.
- **Namecoin** (`.bit`) lookups: need an ElectrumX client over raw TLS sockets.
- **In-app wallets** (Cashu, on-chain) are off unless the build turns them on
  (`-Pamethyst.ios.inAppWallets=true`, App Store guideline 3.1.5(i) wants an organization account).
- **The NIP-82 software catalogue** ("App Store" in the menu) is left out on purpose: App Review
  rejects an app that offers a store of other apps (guideline 3.2.2(i)). Software app notes still
  render, and open as notes.
- A link to a screen iOS lacks opens the note it is about (a git repository, a software app), the
  system browser (a web page or web app that does not ask for Tor), or a notice that names no other
  platform (`UnavailableScreen`).
- A video that must go through Tor but would play off the video cache (HLS, a live stream) is
  refused with a message (B12e).

## App Store packaging

`xcode/Amethyst/PrivacyInfo.xcprivacy` is the privacy manifest (no tracking, no collected data,
the required-reason APIs with where each is called). `ITSAppUsesNonExemptEncryption` is true: the
app implements its own end-to-end encryption and Tor, so App Store Connect asks the export
questions, and the mass-market classification needs the yearly self-classification report. The
first login accepts the terms of use, as on the Play build.

Release builds go to TestFlight through `.github/workflows/ios-testflight.yml` (a release tag, or
by hand): it archives, signs with Xcode's cloud-managed signing and an App Store Connect API key,
and uploads. The listing, the review notes, the answers App Store Connect asks for and the
guideline-by-guideline checklist are in [`store/README.md`](store/README.md).
