# Desktop startup: time to a settled home feed

Status: in progress. Nine of the changes below are merged, in two PRs: the desktop ones, and the
drawer and label ones (shared with Android). Class preloading and the measuring tools stay on the
measurement branch, `ccr-391ad394-xc470s`. The largest win (AppCDS) is on for the dev `run` only
and still needs a packaging story.

## What is measured

From the JVM starting to the moment the **top 5 posts of the home feed (New Threads) stop
changing**, leaving out posts created after the start or up to 60 s before it (they are live posts
arriving, not startup lag). Each run's reference is its own final top.

The tools live on the measurement branch (`ccr-391ad394-xc470s`), not in `main`:

- `StartupTimeline` (`desktopApp/.../service/startup/`, off by default) writes milestones and every
  change of the home feed's top 10 to a TSV, stamped in ms since JVM start, plus optional stack
  samplers of the main and UI threads.
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

A/B on one build with `-Damethyst.startupWarmUp=false`, 8 alternating boots each. This warm-up
was the first version, with class preloading (item 3) and secp256k1:

| Variant | account loaded | logged-in window up | follows known | first posts | **settled** |
|---|---|---|---|---|---|
| warm-up off | 1751 | 3508 | 4040 | 5090 | **5750** |
| `StartupWarmUp` on | 1418 | 2952 | 3384 | 4407 | **5062** |

The ranges don't overlap: account loaded 1663–1836 ms vs 1357–1459 ms, window up 3374–3594 ms vs
2912–3059 ms. "Logged-in window up" is the shell's first composition applied, when its
subscriptions start.

### The logged-in window on screen, without AppCDS

Polling the Xvfb screen from outside the app for when the drawer's labels first show, 8 alternating
boots of the build before this round and after it (window gate, class-compiled lambdas, warm-up
v2):

| Variant | account loaded | logged-in shell visible | follows known | first posts |
|---|---|---|---|---|
| before | 1642 | **8837** (7857–9394) | 3993 | 5094 |
| after | 1616 | **4483** (4037–4856, two boots at 7318 and 8433) | 3574 | 4506 |

In the two slow "after" boots the app drew its shell at 4.4 and 5.9 s (the first-draw mark) but
the poller saw the drawer 2.5–3 s later; not explained yet.

## Where a cold start's CPU goes

The start is CPU-bound: from `main()` the process runs ~3.3 of the container's 4 cores, ~10
CPU-seconds by the time the logged-in window is up. Per thread at 3.1 s (`/proc/<pid>/task`):
C2 compiler ~50%, C1 ~11%, UI thread ~13%, coroutine workers ~11%, main ~8%. JFR's compilation
events put ~7 s of C2 time in the first 4 s, nearly all JDK code: `java.lang.invoke` and its ASM
(spinning a class for each Kotlin lambda and string template on first use: ~7.9k such classes in
4 s, 6.2k of them ours), class loading, and the TLS handshakes' EC maths.

Reordering work across threads stops paying here: warming the drawer's labels in the background
made the shell compose ~0.2 s faster and delayed the login by as much (dropped). Removing work, or
JIT settings, are what move it:

| JIT flag (8+8 boots) | view model | window up | settled |
|---|---|---|---|
| none | 1735 | 3217 | 5528 |
| `-XX:CICompilerCount=2` | 1514 | 2776 | 5124 |
| `-XX:TieredStopAtLevel=1` (C1 only: an upper bound, not shippable) | 1297 | 2458 | 4548 |

Not shipped: the compiler-thread default scales with cores, and only this 4-core container was
measured.

## Changes

1. **AppCDS on `:desktopApp:run`** (`desktopApp/build.gradle.kts`): `-XX:+AutoCreateSharedArchive`
   with the archive in `build/appcds/`. Class loading was most of the time before the first frame
   (3.5 s → 0.95 s) and of the account start. Dev and agent runs only; see "Next".
2. **`AccountRelayPreconnect`** (commons, called by `DesktopAppModules` on login): holds the
   account's own-data relays and its follows' current outbox relays with `keepConnected` for 30 s,
   so the TLS handshakes overlap the first composition of the logged-in shell (~1.0–1.2 s, which
   every screen subscription waits for) instead of following it.

3. **Class preloading** (measurement branch only, not merged): two daemon threads load, without
   initializing, the ~15k classes a start reads from the app's jars (a generated list, in load
   order). All-thread samples of a cold start showed the main thread and the login running one
   step after another on one core, mostly in class loading: most of `Account.<init>`'s ~650 ms,
   about half of the logged-in window's ~1.7 s first composition. Not merged: the list is
   generated by the measuring tools (`startup-bench.sh classes`), which stay off `main`, so it
   would go stale there, and AppCDS in the packages would make it redundant.
4. **`setDockIcon`** asks whether the taskbar takes an icon image (macOS only) before decoding
   it: ~85 ms less on the main thread on Linux and Windows.
5. **The window composes the app only once it has a size** (`AmethystDesktop.kt`). Its size
   reads 0 x 0 until its first layout; composed before then, the shell picked the phone layout
   (bottom bar, modal drawer), composed all of it (~1.2 s of UI thread) and threw it away for the
   real one. In-app first draw 5244 → 4071 ms (A/B on one build).
6. **Lambdas, SAM conversions and string templates compiled as classes** on the JVM targets of
   `commons`, `commonsUI` and `desktopApp` (`-Xlambdas=class -Xsam-conversions=class
   -Xstring-concat=inline`; Android's D8 already desugars them). Runtime-spun classes in the first
   4 s: 7925 → 1375. Logged-in window up 3255 → 3076 ms, view model 2008 → 1730 ms (with item 7).
7. **`StartupWarmUp`** (desktop `main()`): on two daemon threads, the secp256k1 native library
   ahead of the login (~250 ms of it), the first TLS setup (trust store, `SSLContext`, ~250 ms of
   main thread inside `OkHttpClientFactory`) and, on Linux and Windows, the window icon. The icon
   now decodes through Skia: ImageIO plus a per-pixel copy cost ~200 ms of window creation on the
   UI thread. `-Damethyst.startupWarmUp=false` turns the warm-up off.
8. **Nav catalog labels resolved when read** (shared with Android, own PR) (`NavBarItemDef.label`, `labelRes` now a getter):
   each generated `Res.string` accessor initializes its whole file of 100 lazy accessors, and the
   54 labels sat in 32 files, ~3,200 accessors built on the UI thread before the drawer drew a row
   (`NavBarItemKt.<clinit>`, 24% of the shell's composition → 0.5%). `BottomBarCategories` is
   `by lazy` for the same reason.
9. **The drawer is a `LazyColumn`** (`DrawerContent.kt`; shared with Android, own PR): header, each section's heading, fixed
   rows and catalog rows as keyed items, then Accounts and the footer, so a start composes only the
   rows on screen. Same order and look; the weight spacers it replaces did nothing inside a
   scrolling column. Folding a section animates its rows with `animateItem`.
10. **The desktop Settings catalog is built when first read**: only the macOS menu bar reads it,
   and building it read all 53 Settings labels (~9% of the first frame's UI thread on Linux).

### The drawer (8–10 above), measured on a slower replacement container

The session moved to another machine (~10–15% slower), so these compare against the previous
commit on the same machine, 12 alternating boots each:

| | view model → composed | first draw | follows known |
|---|---|---|---|
| before | 1224–1456 ms (median ~1360) | median 4468 (4227–5824) | 3906 |
| after | 852–1101 ms (median ~1060) | median 4777 (3951–5623) | 3435 |

The composition is ~0.3 s shorter with no overlap, and UI-thread samples from the view model to
the first draw fell ~12% (the drawer's share halved: 61–71 → 31–37 samples). The first draw did not
move: with the window composed sooner, the subscriptions start sooner and the incoming events'
processing takes the CPU the first layout and draw needed (the sampler itself got fewer turns in
the "after" boots). On this saturated 4-core container less UI work does not show as an earlier
frame; on a machine with spare cores it should.

## Tried and dropped

- `sample()` → a leading-edge throttle on the home REQ's outbox-set trigger and on the follows'
  relay-list loader: no change in A/B (4201 vs 4212 ms settled). The sampling exists so relays don't
  see REQs replaced in quick succession, which many handle badly; not worth an extra re-issue.
- `LocalCache.live` bundler at 250 ms instead of 1000 ms: tighter spread (3.36–3.84 s) but no better
  median after AppCDS, and four times the feed-filter passes on every platform. Not adopted.

## Where the rest goes (AppCDS + preconnect)

- **Logged-in shell's first composition, ~1.1 s on the UI thread** (account view model created →
  its effects run). UI-thread samples (the measurement branch's sampler): the permanent
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
2. Compose less in the first frame of the logged-in shell. Items 8–10 took the drawer from half
   of it to about a quarter; what remains is the notification side panel and the home screen, and
   generated string files touched one label at a time (each still builds 100 accessors). Android's
   modal drawer still composes its content while closed.
3. Its first layout and draw (~1 s here, software rendering under Xvfb; likely far less on a GPU).
4. Keep the follows' relay lists across restarts so the home REQ can leave at login.
5. Measure the saved-account path (follow list from the account's backup instead of relays) and a
   real network with Tor on.
