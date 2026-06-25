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
