# IME / text-selection test harness

A single-file web page (`index.html`) for exercising and profiling the embedded
WebView IME + text-selection relay (see
`amethyst/plans/2026-06-25-embed-text-selection-native-parity.md`). It has a
plain `<input>` and a `<textarea>` plus an on-page green log that records, with
millisecond timestamps:

- focus/blur, `selectionchange`, `keydown`/`beforeinput`/`input`, composition
  events, and the resulting `value`/selection — to catch erase, caret-jump, and
  focus-transfer regressions;
- **paint latency** (`requestAnimationFrame` after each DOM change) — the metric
  that exposed the first-letter freeze;
- **long-task** + **main-thread-block** detectors and a focus/selection
  **heartbeat** — to catch anything stalling the WebView main thread or
  spontaneously moving focus/selection.

The log lines are tagged `[ImeDiag]` and also go to `console.log`, so they show
up in `adb logcat` (the `:napplet` process owns the WebView console). Nothing
here ships in the app — it's a dev tool, which is why the `[ImeDiag]` strings
live only under `tools/`.

## Run it

1. Serve this directory over HTTP from your dev machine:

   ```bash
   cd tools/ime-test && python3 -m http.server 8765
   ```

2. Reach it from the device/emulator:
   - **Emulator:** the page is at `http://10.0.2.2:8765` (`10.0.2.2` is the
     emulator's alias for the host loopback).
   - **Physical device (USB):** `adb reverse tcp:8765 tcp:8765`, then the page is
     at `http://localhost:8765`.

3. Open that URL as an **embedded** tab (this is the path that uses the relay —
   *not* a full-screen activity):
   - Open the in-app browser (`BrowserScreen`) and type the URL into its address
     bar. The embedded browser handles `http`/`https`, so it loads into the
     `:napplet` SurfaceControlViewHost surface.

   To compare against native behavior, open the same URL in a full-screen
   activity (where the WebView renders in-window with the native keyboard) — that
   is also how you reproduce the **full-screen round-trip highlight bug** (open
   full-screen, `back`, then selection highlight is dead across all embeds).

## Reading the log

- `INPUT … val=… sel=…` right after a keystroke with the right value = no erase.
- `PAINT-LATENCY Nms` spiking to ~1000ms = the first-letter freeze (should stay
  low now that the surface no longer resizes on IME show).
- `MAINTHREAD BLOCKED` / `LONGTASK` = something is stalling the WebView thread.
- `HEARTBEAT` lines changing while idle = spontaneous focus/selection drift.

## `perf.html` — why does the embed feel slower than the full-screen browser?

`index.html` profiles the IME relay. `perf.html` answers a different question:
the embedded tab and the full-screen browser are the **same WebView in the same
`:napplet` process** with byte-identical `WebSettings`, so when a site's JS feels
slower in the embed, the cause is host-induced — and this page measures which
host effect it is.

Serve the directory (above) and open **the same URL in both hosts**, then compare
the summary line at the bottom of the page:

- **`vis=hidden`** — decisive. Chromium considers the embedded page hidden, so it
  clamps timers to ~1Hz and suspends `requestAnimationFrame`. Everything the site
  schedules lands late; it reads as "the JS got slow". Confirmed by
  `timer50` (a 50ms interval firing at 500-1000ms) and `raf` (0 fps).
- **`vis=visible` but `cpu` is 2-4× the full-screen number** — the process is
  running on the little cores. The site's JS runs in the WebView *renderer*
  process, whose scheduling class is inherited from its host: `:napplet` is
  `top-app` when it fronts the full-screen activity, but only a bound service
  (`BIND_AUTO_CREATE`, no `BIND_IMPORTANT`) when it serves the embed. Cross-check
  off-device with:

  ```bash
  adb shell dumpsys activity processes | grep -E 'napplet|sandboxed'
  adb shell "cat /proc/$(adb shell pidof com.vitorpamplona.amethyst:napplet)/cgroup"
  ```

  Expect `/top-app` with the full-screen browser open and `/foreground` (or lower)
  with an embed tab open.
- **`cpu` matches but `inputDelivery` is much higher** — the gap is input routing
  into the embedded window, not compute. `inputDelivery` is the time between the
  platform stamping the touch and JS receiving it.
- **`layout` much higher in the embed** — layout/paint is the bottleneck (check
  logcat for WebView software-rendering warnings; a non-hardware-accelerated
  `SurfaceControlViewHost` window would put Chromium on the software path).
- **`focus=false` in the embed is expected** and is not itself a throttle: the
  host window owns the keyboard, which is the whole reason `RemoteImeView` exists.

`longtasks` counts main-thread blocks over 50ms while the page was measuring —
high counts in the embed with a matching `cpu` number point at something else in
the process competing (e.g. parked warm tabs that are never paused).
