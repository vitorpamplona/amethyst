---
name: desktop-expert
description: Desktop JVM shim in desktopApp/: AmethystDesktop window, DesktopMenuBar, platform ports, OS keyring; :desktopApp:run, packaging. Use when touching desktopApp, or to run/screenshot the shared UI headless (tools/desktop-run, Xvfb) to verify a UI change. NOT for writing shared screens.
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
| `platform/WaylandDisplay.kt`, `platform/AppWindow.kt` | Native Wayland: `WLToolkit` picked in `main()` on a Wayland session (`AMETHYST_X11=1` opts out); `AppWindow` is Compose's `Window` elsewhere and a Swing-drawn frame there. Open new top-level windows with `AppWindow`, never `Window`/`ComposeDialog`. |
| `platform/DesktopUiScale.kt` | X11 at a fractional scale (`Xft.dpi` 120 = 125%, `AMETHYST_SCALE` by hand): AWT draws whole scales only, so `AppWindow` raises Compose's density by the rest. `LocalDensity` is then not AWT's scale: convert AWT window/event coordinates with `awtPixelScale(component)`, and size AWT windows in AWT units. |
| `account/`, `tor/`, `network/`, `service/`, `ui/media/` | Account storage, Tor, HTTP, scheduled posts, video/audio players. |
| `update/` | Self-update: `InstallChannel` (how this copy was installed: DMG, Homebrew, MSI, zip, AppImage, tarball, .deb/.rpm, Flatpak), `DesktopUpdateInstaller` + `UpdateHelperScripts` (stage, then swap after quit), `UpdateDownloader` (SHA-256 checked). The checker is commons `appupdate`; dev runs need `-Damethyst.update.devCheck=true`. |

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
(`commons/src/jvmMain/.../commons/keystorage/SecureKeyStorage.kt`, `java-keyring`). Without one they
go to a NIP-49 password file (`PasswordKeyFile`), and a login can opt out of keeping its key. The
status, prompts and Settings › Key storage are shared (`KeyStorageController`, provided by
`DesktopAppRoot.keyStorage`); see `commons/plans/2026-10-12-desktop-key-storage.md`.

The keyring breaks in release builds only, so test the packaged app: `Amethyst --keyring-self-test`
(CI runs it through `keyring-self-test.sh` in the repo's top-level scripts folder). ProGuard must keep jkeychain's native library and
dbus-java's ServiceLoader transport, and drop the signatures of jars it rewrites (hkdf):
`desktopApp/compose-rules.pro`, `verifyJkeychainNativeSurvivesProguard`,
`verifyDbusTransportSurvivesProguard`.

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
- Build fails? Check `java -version` (21+), then that `./gradlew :commons:compileKotlinJvm` resolves quartz and compiles.

## Verify a UI change by running it (headless too)

The window renders the shared UI, so this is how an agent checks a `commonsUI`/`commons` change for
real (AGENTS.md "Run the app before calling a UI change done"). `tools/desktop-run/` does it without
a display or a window manager: `run-headless.sh start` runs Xvfb + `:desktopApp:run` on a scratch
profile (`/tmp/amethyst-desktop-run/profile`), waits for the first paint and saves `shot.png`;
`Drive.java` (pure JDK `java.awt.Robot`) clicks, types, presses keys, scrolls and takes screenshots.
Read the screenshots with the image-reading tool. Details: `tools/desktop-run/README.md`.

- Log in with a public `npub` for a read-only session. A throwaway Sign Up account is only for flows
  that must sign, and only when asked, because it publishes to real relays.
- The screen size picks the shell: `AMETHYST_SCREEN=480x900x24` gives the phone layout (bottom bar),
  the default 1600x1000 gives the permanent drawer + docked notifications. Use a new
  `AMETHYST_DISPLAY` per size.
- Linux packages bundle the JetBrains Runtime (build on `jbrsdk` 21, see BUILDING.md "Wayland and X11
  on Linux"); `scripts/wayland-smoke-test.sh <launcher>` checks one on a sway with no Xwayland.
- Under Xvfb: no GL (the script sets `SKIKO_RENDER_API=SOFTWARE`), no keyring (password-file
  fallback), no native video player ("Can't play this video"). Those are the environment, not your
  change. `run-headless.sh logs` lists exceptions first.
- Never `pkill -f AmethystDesktopKt` from a tool shell: it matches the shell itself. Use `stop`.

For a check that must keep passing, write a Compose UI test (`createComposeRule()`, see
`desktopApp/src/jvmTest/.../desktop/ui/BlockInteractionsUiTest.kt`); CI runs them under
`xvfb-run --auto-servernum ./gradlew :desktopApp:test`.

Tests: `./gradlew :desktopApp:test` (a plain JVM module: sources sit in `src/jvmTest` but the task
is `test`); `desktopApp/src/jvmTest/.../desktop/testrelay/` has an in-process relay fixture
(`LaunchFixtureRelay`).
