---
name: android-expert
description: Android shim in `amethyst/`: MainActivity, AndroidManifest/intent filters, :napplet process, play/fdroid flavors, R8 + tools/r8-verify, permissions, Intent/Context ports. Use when editing amethyst/. NOT for screens/navigation (compose-expert).
---

# android-expert

`amethyst/` is shrinking to an Android shim: entry points, services, system integrations and
the actuals of shared ports. Screens, the nav host and the bar / rail / drawer are shared UI in
`commonsUI` (see `compose-expert`). A new screen goes there even when only Android shows it.

Source root: `amethyst/src/main/java/com/vitorpamplona/amethyst/` (an Android app module, not
KMP: there is no `androidMain` here). Flavor code is in `amethyst/src/play/` and
`amethyst/src/fdroid/`.

## Entry points

- `Amethyst.kt`: the `Application`. Builds `AppModules` (`Amethyst.instance`) in the main
  process only.
- `ui/MainActivity.kt`: the single main Activity (`launchMode="singleInstance"`).
  `onCreate` calls `enableEdgeToEdge()` and sets
  `AmethystTheme { ... AmethystApp(Amethyst.instance.sessionManager, AndroidAppRoot) }`.
  Everything from there down is shared UI.
- `ui/AndroidAppRoot.kt`: `object AndroidAppRoot : AppRoot`, Android's implementation of the
  shared shell port (`commonsUI/.../commons/ui/app/AppRoot.kt`). It registers the Android-only
  destinations (`androidDestinations` in `ui/navigation/AppNavigation.kt`) and runs
  `NavigateIfIntentRequested`.
- `ui/platform/AndroidAppPlatform.kt` (`AppPlatform`) and `ui/platform/AndroidAppServices.kt`
  (`AppServices`, the interface is in `commons/.../commons/service/AppServices.kt`): the other two
  ports the shared UI calls.
- Other activities have their own windows: `ui/call/CallActivity`,
  `ui/screen/loggedIn/nests/room/activity/NestActivity`, `service/playback/pip/PipVideoActivity`,
  the signer / napplet consent activities, and `HealthConnectRationaleActivity`.

## Two processes

The app runs in **main** and **`:napplet`**. Read `AGENTS.md` "Android Runtime Processes" before
touching `Amethyst.kt`, an Application callback, or anything under `napplet/`. The sandbox
activities and services (`NappletHostActivity`, `NappletBrowserActivity`, `NappletHostService`,
`NappletBrowserService`) live in the `:nappletHost` module and are declared with
`android:process=":napplet"`. The broker side (`napplet/NappletBrokerService.kt`,
`NappletLaunchRegistry`, consent) stays in `amethyst/`.

## Manifest and intents

`amethyst/src/main/AndroidManifest.xml`; each flavor merges its own `AndroidManifest.xml`.

- `MainActivity` takes `MAIN`, `VIEW` for `nostr:`, `amethyst:`, `nostrconnect:`, the
  wallet-connect schemes and several `https` hosts, and `SEND`.
- **Share targets are `<activity-alias>`es of MainActivity** (`.ui.ShareAsDMAlias`,
  `ShareAsPictureAlias`, `ShareAsShortVideoAlias`, `ShareAsVideoAlias`, `ShareAsHighlightAlias`,
  `ScanQrCodeAlias`). Which one the user picked is visible only in the launching intent's
  component class name, which `ShareIntentRouting.targetOf` in
  `commonsUI/.../commons/ui/navigation/ShareIntentRouting.kt` maps to a `ShareTarget`. Renaming an
  alias without updating its constant there silently routes the share to New Post. Adding a target
  means an alias, a constant, and a `ShareTarget` entry.
- `NavigateIfIntentRequested` (`ui/navigation/AppNavigation.kt`) turns `VIEW` / `SEND` /
  `SEND_MULTIPLE` / NFC intents into `nav.newStack(...)` calls.
- Foreground services (`CallForegroundService`, `NestForegroundService`, `PlaybackService`,
  `NotificationRelayService`, ...) read their notification strings synchronously, which is the one
  case where a string may stay in Android `res/`. See `AGENTS.md` "Strings" and
  `amethyst/src/main/res/AGENTS.md`.

## Intent / Context access from shared UI

Shared composables never touch `Context` directly. Use the existing shape: a small interface plus
an `expect fun rememberX()` in `commonsUI` `commonMain`, with the Android actual in
`commonsUI/src/androidMain`. Examples: `TextSharer` / `rememberTextSharer()` (the actual builds the
`ACTION_SEND` chooser from `LocalContext`), `FileSharer`, `PlatformBackHandler`,
`HideFromScreenshots`, `rememberCoarseLocationPermission(): PlatformPermission` (the actual wraps
Accompanist's `rememberPermissionState`). When the behavior needs app-only state (an Activity
class, a service, `Amethyst.instance`), put a member on `AppPlatform` / `AppServices` and implement
it in `AndroidAppPlatform` / `AndroidAppServices`, as `openNestRoom` does with `NestActivity.launch`.

## Flavors: play vs fdroid

`amethyst/build.gradle.kts` declares `flavorDimensions += "channel"` with `play` (default) and
`fdroid`. They differ in `BuildConfig.IS_CASTING_AVAILABLE` and in their dependencies:

- `play`: Firebase messaging, ML Kit (language id, translate, GenAI proofreading / rewriting /
  prompt / image description), Play Services Cast, androidx AppFunctions.
- `fdroid`: UnifiedPush and no Google services.

Classes that `main` calls exist in **both** `src/play/java/...` and `src/fdroid/java/...` with the
same name and signature (`PushNotificationUtils`, `SelectNotificationProvider`,
`LanguageTranslatorService`, `WritingAssistantFactory`, `ChromecastCaster`, `TermsGate`, ...). Add
or change one side and the other flavor stops compiling. Build both when you touch them. Play-only
code (`appfunctions/AmethystAppFunctions.kt`, `AmethystCastOptionsProvider`) needs no twin as long
as `main` never references it. Build types: `debug` (`.debug` suffix), `release`, and `benchmark`
(release config, debug-signed, profileable).

Gradle details beyond this (catalog, versions, KMP source sets) belong to `kotlin-multiplatform`.

## Images (Coil)

`service/images/ImageLoaderSetup.kt` installs the singleton Coil loader. It is called from
`AppModules.setImageLoader()` with a `callFactory` built from
`roleBasedHttpClientBuilder.okHttpClientForImage`, so Tor routing and the "image" traffic tag
apply. Change the client there, not inside the loader setup.
`BlossomFetcher` and `ProfilePictureFetcher` are Android-side. The shared fetchers
(`Base64Fetcher`, `BlurHashFetcher`, `ThumbHashFetcher`) are in
`commonsUI/.../commons/service/image/`, and the desktop counterpart is
`desktopApp/.../desktop/service/images/DesktopImageLoaderSetup.kt`. Never build an `ImageLoader`
inside a composable.

## R8 and release checks

The rules are `amethyst/proguard-rules.pro` plus `quartz/consumer-rules.pro`. The policy is to keep
only what is reached by name. Read `references/proguard-rules.md` before adding a keep.

R8 cannot see reflection, so a broken keep gives a green build and a crash on users' devices.
Before tagging a release, run the reflection-contract check on a minified build. A
`playBenchmark` build is enough, and `-PdisableAbiSplits=true` keeps it quick:

```bash
./gradlew :amethyst:assemblePlayBenchmark -PdisableAbiSplits=true
python3 tools/r8-verify/verify_reflection_contract.py amethyst/build/outputs/mapping/playBenchmark/
```

The release workflow (`.github/workflows/create-release.yml`) runs the same script on
`playRelease` and `fdroidRelease`. Adding reflection means adding a keep rule **and** a line in
`tools/r8-verify/reflection-contract.txt` (prefix it with `@play` / `@fdroid` for flavor-only
classes).

## Testing

Unit tests are in `amethyst/src/test` (plus `testPlay`), instrumented tests in
`amethyst/src/androidTest` (plus `androidTestPlay`). Before calling a UI change done, run it on a
device or emulator: `./gradlew :amethyst:installDebug`.
