---
name: desktop-expert
description: Desktop JVM shim in desktopApp/.../desktop/app/: AmethystDesktop window, DesktopMenuBar shortcuts, platform ports, OS keyring; run/package (:desktopApp:run, packageDmg/Msi/Deb/Rpm). Use when touching desktopApp. NOT for shared screens/navigation.
---

# Desktop (JVM shim)

`desktopApp` is a **shim**: it opens a window and renders the same `AmethystApp` Android renders.
Screens, the navigation shell (bottom bar / rail / drawer, chosen by `ScreenLayoutSpec`) and the
deck are shared UI in `commonsUI` — see AGENTS.md "Navigation Shell" and "Feature Workflow", and
`compose-expert`. Never add a desktop-only screen or a desktop-specific navigation layout.
Background: `commons/plans/2026-09-27-one-ui-android-desktop.md`,
`commons/plans/2026-10-09-legacy-desktop-retirement.md` (the legacy desktop app is gone).

## Where things live (`desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/`)

| File | Role |
|---|---|
| `app/AmethystDesktop.kt` | `main()`: macOS AWT properties, data dir, `DesktopAppModules`, the `Window`, the CompositionLocals (`LocalAppPlatform`, `LocalNotePlatform`, …) around `AmethystApp`. `--publish-scheduled` runs headless (OS timer publishing scheduled posts). |
| `app/DesktopAppModules.kt` | The desktop graph (Android's `AppModules` minus phone-only parts). |
| `app/DesktopAppRoot.kt` | `AppRoot` impl; `jvmDestinations`. |
| `app/DesktopAppPlatform.kt` | `AppPlatform` (commonsUI `ui/platform/`); unoverridden members keep the default, which hides the feature. |
| `app/DesktopNotePlatform.kt`, `DesktopRichTextPlatform.kt` | `NotePlatform`, `RichTextPlatform` (commonsUI). |
| `app/DesktopAccountViewModelHost.kt`, `DesktopLocalCacheHost.kt`, `DesktopAppServices.kt` | `AccountViewModelHost`, `LocalCacheHost`, `AppServices` (commons). |
| `app/DesktopMenuBar.kt` | `DesktopNavigator`, the `Command` enum, `DesktopMenuBar`, `handleShortcut`. |
| `app/Legacy*Import.kt` | One-time import of the retired legacy app's accounts, deck, feeds, privacy lock. |
| `platform/PlatformInfo.kt` | `PlatformInfo.current` / `host`, `isMacOS`, `isWindows`, `isLinux`, `isGnome`, `isKde`. |
| `platform/SavedWindowBounds.kt` | Window position/size persisted to `window-bounds.properties`. |
| `account/`, `tor/`, `network/`, `service/`, `ui/media/` | Account storage, Tor, HTTP, scheduled posts, video/audio players. |

A new shared feature that needs a desktop behaviour = a member on one of the ports above
(default in the interface, override here), not a desktop branch in the shared screen.

## Shortcuts and menu bar

- One `Command` enum in `DesktopMenuBar.kt` drives both. `Command.shortcut` is Cmd+key on macOS,
  Ctrl+key elsewhere (`isMac = PlatformInfo.host == Platform.MACOS`).
- macOS: the system `MenuBar` carries the shortcuts. Linux/Windows get **no** menu bar (Swing draws
  it inside the window); shortcuts go through `handleShortcut` from the window's `onPreviewKeyEvent`.
- Deck commands are ignored unless `DeckCommandBus.active`; held chords are de-duplicated
  (`AUTO_REPEAT_WINDOW_MS`).
- Menu labels are `Res.string.*` (commonsUI strings, per AGENTS.md "Strings").

## OS detection

`PlatformInfo.current` honours an override (`-Damethyst.platform=GNOME` or `AMETHYST_PLATFORM`) to
preview another OS's theming; `PlatformInfo.host` is the real OS — use it for anything that touches
the actual system (menu bar, Cmd key, shell-outs).

## Keys

Keys go to the OS keyring through `SecureKeyStorage`
(`commons/src/jvmMain/.../commons/keystorage/SecureKeyStorage.kt`, `java-keyring`; falls back to a
password-encrypted file). On macOS the release ProGuard pass must keep jkeychain's native library:
`desktopApp/compose-rules.pro` and the `verifyJkeychainNativeSurvivesProguard` task guard that.

## Run and package

```bash
./gradlew :desktopApp:run                                   # JDK 21 toolchain (jvmToolchain(21))
./gradlew :desktopApp:run -Damethyst.dataDir=/tmp/amy-test  # separate profile; -Damethyst.debug=true for DEBUG logs
./gradlew :desktopApp:packageDmg    # macOS; packageMsi (Windows), packageDeb / packageRpm (Linux)
```

- `mainClass = "com.vitorpamplona.amethyst.desktop.app.AmethystDesktopKt"`. Data lives in
  `~/.amethyst/app` unless `amethyst.dataDir` is set.
- Packages land in `desktopApp/build/compose/binaries/main/` (release variants in `main-release/`).
- **Stop a running app before rebuilding shared modules.** A build rewrites the jars the running
  `:desktopApp:run` JVM loaded and it crashes with `NoClassDefFoundError`. Relaunch it afterwards.
- Build fails? Check `java -version` (21+), then that `./gradlew :quartz:build` compiles.

Tests: `./gradlew :desktopApp:jvmTest`; `desktopApp/src/jvmTest/.../desktop/testrelay/` has an
in-process relay fixture (`LaunchFixtureRelay`).
