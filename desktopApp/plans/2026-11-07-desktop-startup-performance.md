# Desktop startup: time to a settled home feed

Status: in progress. Measurement tooling and four changes landed; the largest win (AppCDS) is on
for the dev `run` only and still needs a packaging story.

## What is measured

From the JVM starting to the moment the **top 5 posts of the home feed (New Threads) stop
changing**, leaving out posts created after the start or up to 60 s before it (they are live posts
arriving, not startup lag). Each run's reference is its own final top.

- `StartupTimeline` (`desktopApp/.../service/startup/`, off by default) writes milestones and every
  change of the home feed's top 10 to a TSV, stamped in ms since JVM start.
- `tools/desktop-run/startup-bench.sh` boots the app's JVM directly (no Gradle in the timing) N
  times on the headless display and runs `startup_settle.py` over the timelines.

Setup: a new key ("Startup Bench", made with `run-headless.sh start --signup`) following the 260
accounts of Vitor's follow list (Import Follow List, select all). Logged in transiently with
`--login-file`, so every boot fetches the follow list from relays. Tor engine **Off**: Tor never
bootstraps in this container and every untrusted outbox relay would wait for it. 4 cores, Xvfb,
software rendering, all traffic through the sandbox's HTTPS proxy. Absolute times drift with relay
load, so comparisons are A/B runs with boots alternated.

## Results (medians, ms since JVM start)

| Variant | first frame | account loaded | first relay | follows known | first posts | **settled** |
|---|---|---|---|---|---|---|
| main (n=5) | 3474 | 1822 | 3999 | 4699 | 5389 | **5631** |
| + AppCDS (n=5) | 952 | 933 | 1988 | 2436 | 3336 | **3377** |

A/B on the same build with AppCDS, 8 alternating boots each:

| Variant | first relay | follows known | first posts | **settled** |
|---|---|---|---|---|
| no preconnect | 2044 | 2472 | 3433 | **4452** |
| `AccountRelayPreconnect` | 1517 | 2221 | 3238 | **4241** |

### Login and the logged-in window, without AppCDS (what installed apps run)

A/B on one build with `-Damethyst.startupWarmUp=false`, 8 alternating boots each:

| Variant | account loaded | logged-in window up | follows known | first posts | **settled** |
|---|---|---|---|---|---|
| warm-up off | 1751 | 3508 | 4040 | 5090 | **5750** |
| `StartupWarmUp` on | 1418 | 2952 | 3384 | 4407 | **5062** |

The ranges don't overlap: account loaded 1663–1836 ms vs 1357–1459 ms, window up 3374–3594 ms vs
2912–3059 ms. "Logged-in window up" is the shell's first composition applied, when its
subscriptions start.

## Changes

1. **AppCDS on `:desktopApp:run`** (`desktopApp/build.gradle.kts`): `-XX:+AutoCreateSharedArchive`
   with the archive in `build/appcds/`. Class loading was most of the time before the first frame
   (3.5 s → 0.95 s) and of the account start. Dev and agent runs only; see "Next".
2. **`AccountRelayPreconnect`** (commons, called by `DesktopAppModules` on login): holds the
   account's own-data relays and its follows' current outbox relays with `keepConnected` for 30 s,
   so the TLS handshakes overlap the first composition of the logged-in shell (~1.0–1.2 s, which
   every screen subscription waits for) instead of following it.

3. **`StartupWarmUp`** (desktop `main()`): two daemon threads load, without initializing, the ~9k
   classes a start reads from the app's jars (`resources/startup/preload-classes.txt`, in load
   order), and load the secp256k1 native library before the login needs it. All-thread samples
   of a cold start showed the main thread and the login running one step after another on one
   core, mostly in class loading: ~250 ms secp256k1 in the login, most of `Account.<init>`'s
   ~650 ms, about half of the logged-in window's ~1.7 s first composition. Release builds keep
   class names (`-dontobfuscate`), so the list applies there too; shrunk-away names are skipped.
   Regenerate the list with `startup-bench.sh classes` after large changes to the start or shell:
   a stale list only loses speed.
4. **`setDockIcon`** asks whether the taskbar takes an icon image (macOS only) before decoding
   it: ~85 ms less on the main thread on Linux and Windows.

## Tried and dropped

- `sample()` → a leading-edge throttle on the home REQ's outbox-set trigger and on the follows'
  relay-list loader: no change in A/B (4201 vs 4212 ms settled). The sampling exists so relays don't
  see REQs replaced in quick succession, which many handle badly; not worth an extra re-issue.
- `LocalCache.live` bundler at 250 ms instead of 1000 ms: tighter spread (3.36–3.84 s) but no better
  median after AppCDS, and four times the feed-filter passes on every platform. Not adopted.

## Where the rest goes (AppCDS + preconnect)

- **Logged-in shell's first composition, ~1.1 s on the UI thread** (account view model created →
  its effects run). UI-thread samples (`-Damethyst.startupTimeline.sampleUi=<file>`): the permanent
  drawer's full catalog ~25%, `NavBarItem.<clinit>` ~10%, Compose Resources' first `stringResource`
  reads (a `runBlocking` read per string) ~8%, Material Symbols drawing ~5%.
- **Follows known → first posts, ~1 s**: the follows' relay lists (kind 10002) have to arrive before
  the home REQ reaches their outboxes, then ~300 relays dial. The desktop keeps no event store, so
  every start downloads all of it again.
- **First posts → settled, ~1 s**: slower outbox relays answering, plus the 1 s `LocalCache` bundle.

## Next

1. **Ship AppCDS in the packages.** JDK 21 requires the archive's class path to match exactly
   (checked: a moved app directory gets "Required classpath entry does not exist"), so an archive
   made at build time breaks once installed elsewhere, and the JDK 21 jpackage launcher only expands
   `$APPDIR`/`$BINDIR`/`$ROOTDIR` in `java-options` (checked in its binary), so it cannot point
   `AutoCreateSharedArchive` at a per-user folder. Options: a newer JDK's launcher or AOT cache, a
   first-run training step that writes next to the user's data, or a small native launcher.
2. Open the window sooner: the account is loaded at ~1.4 s but the logged-in shell starts
   composing at ~1.9 s, after the rest of `main()` and the window's theme and icon fonts.
3. Compose less in the first frame of the logged-in shell: a lazy drawer catalog, cheaper
   `NavBarItem` init, string reads off the first frame.
4. Keep the follows' relay lists across restarts so the home REQ can leave at login.
5. Measure the saved-account path (follow list from the account's backup instead of relays) and a
   real network with Tor on.
