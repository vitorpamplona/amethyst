# macrobenchmark

Macrobenchmark tests for Amethyst's Android app. Measures cold-start performance
including the long tail after first frame — relay connection storm and
`LocalCache` event flood — that dominates a real-user boot experience.

## Boot markers

`StartupBenchmark` collects six `Boot:*` trace sections emitted from
`com.vitorpamplona.amethyst.debug.BootTrace`:

| Marker | Kind | Fired by | Meaning |
|---|---|---|---|
| `Boot:AppModulesCtor` | sync section | `Amethyst.onCreate` | Wraps `AppModules(this)` — captures eager construction cost (incl. `runBlocking` on `torPrefs` at AppModules.kt:201,255) |
| `Boot:Initiate` | sync section | `Amethyst.onCreate` | Wraps `instance.initiate(this)` — captures pre-frame work like the documented `uiPrefs` `runBlocking` at AppModules.kt:718 |
| `Boot:FirstAccountLoaded` | instant | observer in `Amethyst.kt` | First transition of `sessionManager.accountContent` to `AccountState.LoggedIn` |
| `Boot:RelaysConnected` | instant | observer in `Amethyst.kt` | First non-empty `client.connectedRelaysFlow()` |
| `Boot:FirstHomeFeedFrame` | instant | observer in `Amethyst.kt` | First emission of `LocalCache.live.newEventBundles` (proxy for "feed has data"; the true first-frame signal would require instrumenting `FeedContentState` in `:commons`) |
| `Boot:HomeFeedSteady` | instant | observer in `Amethyst.kt` | No new bundle for 3 s — signals end of cold-start event flood |

All markers use `androidx.tracing.Trace`. Sync sections use
`beginSection`/`endSection`; instants use zero-duration async sections.
`Trace.beginSection` is a JNI no-op when tracing isn't recording.

The 4 async observers wait on `applicationIOScope` (app-lifetime) and don't
self-cancel if their condition never fires (e.g., logged-out forever, no
relays configured). This is intentional — the held references are all
app-lifetime singletons, and suspended continuations cost bytes. If the
markers ever ship in release builds, revisit.

## Running

Connect a physical device (macrobenchmark refuses emulators by default):

```bash
./gradlew :macrobenchmark:connectedPlayBenchmarkAndroidTest
```

The `play` variant targets `com.vitorpamplona.amethyst.benchmark` (the
`benchmark` build type's `applicationIdSuffix`). For F-Droid:

```bash
./gradlew :macrobenchmark:connectedFdroidBenchmarkAndroidTest
```

Build only, no device:

```bash
./gradlew :macrobenchmark:assembleBenchmark
```

## Test account setup (TODO)

The 4 async markers only fire on a logged-in device. Currently the benchmark
runs against whatever account state is already installed on the device — if
the app is fresh-installed, those markers report as missing in the results.

To make the benchmark deterministic, either:

1. **Manual setup** — log into the test account once on the device before running.
   The `benchmark` variant has its own `applicationIdSuffix` so it doesn't
   share state with debug/release installs.
2. **Automated nsec injection** (not yet wired) — accept a test nsec via
   `androidTest` `instrumentationArgs`, write it to encrypted storage in the
   benchmark's `setupBlock`, then launch. Requires extracting login wiring
   from `AccountSessionManager` into something callable from instrumentation.

For now: prefer (1) and treat missing async markers in CI as a fresh-install
signal, not a regression.

## Reading the output

After the run, `./gradlew :macrobenchmark:connectedPlayBenchmarkAndroidTest`
prints a per-iteration summary plus a perfetto trace path. Open the trace at
https://ui.perfetto.dev to inspect the `Boot:*` markers in context with the
main-thread profile.

To compare runs:

```bash
adb pull /storage/emulated/0/Android/media/com.vitorpamplona.amethyst.macrobenchmark/
```
