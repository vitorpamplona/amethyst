# desktop-run: launch and drive the desktop app headless

The desktop app renders the same shared UI (`AmethystApp`, `commonsUI`) as Android: the same screens,
navigation shell and feeds. A UI change that works on desktop has passed the shared code path, so
running the JVM build is the cheapest way to check a change for real when no Android device is
attached, for example in a cloud agent container. Compiling and unit tests do not catch a screen
that crashes on open, a missing string, or a layout that collapses.

Two pieces, both pure JDK plus Xvfb. No xdotool and no window manager needed:

- `run-headless.sh` starts Xvfb on `:99`, builds and runs `./gradlew :desktopApp:run` on a separate
  profile, waits for the first paint and takes a screenshot. It fails with the log's errors when
  the build fails, the JVM dies or nothing paints.
- `Drive.java` is a `java.awt.Robot` driver that takes screenshots, clicks, types, presses keys and
  scrolls. Run it as a single-file program (`java Drive.java ...`) against the same `DISPLAY`.

## Loop

```bash
tools/desktop-run/run-headless.sh start --fresh   # first build: many minutes; warm restart ~20 s
# look at /tmp/amethyst-desktop-run/shot.png (an agent: open it with its image-reading tool)

D="java tools/desktop-run/Drive.java"
export DISPLAY=:99
$D click 800 422 ";" type npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m   # see below
$D click 800 595 ";" sleep 8000 ";" shot /tmp/amethyst-desktop-run/feed.png

tools/desktop-run/run-headless.sh logs   # running?, exceptions first, then the tail
tools/desktop-run/run-headless.sh stop   # ALWAYS before rebuilding (see Traps)
```

`start` without `--fresh` keeps the profile in `/tmp/amethyst-desktop-run/profile`, including the
logged-in account, so a restart after a code change lands directly on the feed.

## Drive.java commands

Join steps with a quoted `";"` to run them in one JVM (each JVM start costs about 1 s):

| Command | Does |
|---|---|
| `shot <file.png> [x y w h]` | Screenshot of the whole screen, or a region (zoom in on a detail) |
| `click x y`, `dclick x y`, `rclick x y`, `move x y` | Mouse, in screen pixels (= window pixels: no WM, so the window is undecorated) |
| `type <text...>` | Pastes the text through the clipboard + Ctrl+V. Works for any character and layout |
| `key <chord...>` | `enter`, `tab`, `escape`, `ctrl+k`, `ctrl+shift+n`, `f5`, `down`, `pagedown`, ... |
| `scroll x y n` | Mouse wheel at (x, y); positive `n` scrolls down |
| `sleep ms`, `waitpaint [ms]` | Wait; wait until the screen shows something drawn |
| `size` | Prints the screen size |

Read coordinates off the last screenshot. The screen is 1600x1000 by default. The window fills up
to 90% of it (at most 1380x920 dp), centred and undecorated, so screenshot pixels are the click
pixels. The screen size picks the `ScreenLayoutSpec` tier, so one run per size shows each shell. Use
a new display number, because a running Xvfb keeps its size:

```bash
tools/desktop-run/run-headless.sh start                                          # wide: drawer + docked notifications
AMETHYST_DISPLAY=:98 AMETHYST_SCREEN=480x900x24 tools/desktop-run/run-headless.sh start   # phone: bottom bar, as on Android
```

`start` drops the saved window bounds each time, so the window always fits the current screen.

## Accounts

- **Read-only, preferred:** log in with any public `npub` (the one above is jack's). That loads real
  feeds, profiles and notifications and cannot sign or publish anything.
- **Flows that sign** (posting, reacting, settings that publish lists): use a throwaway account from
  the app's Sign Up, never a real key. They publish to real public relays, so do it only when the
  human asked for that flow to be tested.
- The container has no OS keyring, so a key that is remembered goes to the NIP-49 password file.
  The login screen says so, and that is expected here.

## Expected noise (not your bug)

- `Can't play this video ... Failed to create native player`: no native media engine in the
  container.
- `[MediaHttp] ... error=...` / relay `stalled` lines: the network, not the UI.
- `No OS keyring; keys go to the password file`: see above.
- A screenshot taken while the UI is moving (a feed filling, a bar sliding in) can show overlapping
  text for one frame. Take another before calling it a rendering bug.

## Traps

- **Stop before rebuilding shared modules.** A Gradle build rewrites the jars the running app
  loaded, and it dies with `NoClassDefFoundError`. `stop`, build or edit, `start`.
- **Never `pkill -f AmethystDesktopKt` from an agent shell.** The pattern also matches the shell
  whose own command line contains it, and kills your tool call (exit 144). `run-headless.sh stop`
  anchors the match on the java binary.
- **Tor never starts in a sandboxed container, and by default DM and untrusted relays wait for
  it.** Tor needs direct TCP to its guard relays; a container that only lets traffic out through
  an HTTPS proxy never gets a SOCKS port, so every Tor-routed relay is held back with nothing in
  the log. For an account whose outbox is also its DM relay, its own lists (follows, emoji, mutes)
  then never load and its publishes never leave, while clearnet relays work and hide the problem.
  The tell is the drawer's `Relays 1/11`. Before testing a signing account, open the onion icon
  next to the version at the bottom of the drawer and set the Tor engine to **Off**; the choice is
  saved in the run's profile, so it survives `stop`/`start`.
- A dependency resolution `429 Too Many Requests` from Maven Central is a rate limit. Re-run
  `start`.
- `start` gives up after `AMETHYST_START_TIMEOUT` seconds (default 900). Raise it for a first cold
  build on a slow machine.
- `AMETHYST_RUN_ARGS` passes extra Gradle arguments to the run, e.g. the updater's development
  switches: `AMETHYST_RUN_ARGS="-Damethyst.update.devCheck=true -Damethyst.update.pretendVersion=1.0.0"`
  checks for updates five seconds after start, as if version 1.0.0 were running.

## Automated alternative

For a check that should keep passing, write a Compose UI test instead
(`createComposeRule()`, see `desktopApp/src/jvmTest/.../desktop/ui/BlockInteractionsUiTest.kt`) and
run `xvfb-run --auto-servernum ./gradlew :desktopApp:test`, which is what
`.github/workflows/smoke-test-desktop.yml` does in CI.
