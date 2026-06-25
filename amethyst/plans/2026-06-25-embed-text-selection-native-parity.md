# Embedded text selection — native-Android parity

**Status:** in progress. The working set landed in `fix(embed): IME typing +
host-drawn text selection for embedded surfaces` (commit `e0a2a9ab81`). This
plan tracks the remaining gap to a native-feeling experience and the one open
platform bug.

## Why we draw selection ourselves

Editable fields inside an embedded napplet/nsite/browser tab live in the keyless
`:napplet` process and render through `SurfaceControlViewHost` /
`SandboxedSdkView` (privacy-sandbox UI). A WebView rendered into an off-window
surface like this **cannot host the soft keyboard and cannot present Chrome's
own text-selection UI** (handles, the floating action-mode toolbar, the
magnifier). Chrome detects it has nowhere to put that UI and collapses the
selection to the focus endpoint.

So, exactly like Flutter's `TextInputPlugin` did for its virtual-display era, we
relay editing to the main process: an invisible `EditText` (`RemoteImeView`)
hosts the keyboard, `shim.js` mirrors DOM selection/caret geometry out over the
Messenger channel, and `EmbeddedTabLayer` draws the selection UI in Compose on
top of the surface. Everything we want for parity, we draw — the platform gives
us nothing here.

## What native Android gives a text field (the parity target)

This is the full feature inventory we are cloning, with activation/deactivation
rules, so we can check off coverage. Native impl lives in `android.widget.Editor`
(+ `SelectionActionModeHelper`, `android.widget.Magnifier`,
`PopupTouchHandleDrawable` on the Chrome side).

| # | Native feature | Activates | Deactivates | Our status |
|---|----------------|-----------|-------------|------------|
| 1 | **Insertion handle** (the teardrop "blob" under the caret) | tap in editable text; tap again to re-show | typing, scroll start, focus loss, ~4s inactivity timeout | ✅ `InsertionHandle`. **Native availability rule now matched (2026-06-25):** only shown when the field is NON-EMPTY (`Editor` gates the handle behind `text.length() > 0`, via `SelectionUiState.fieldHasText`) — fixes it popping up on focus of an empty box; hides on typing (`onEdited`), scroll (`scrolling`), focus loss, and ~4s inactivity (`hideCaret` timeout), re-showing on the next tap — via an explicit `ime.carettap` shim signal (DOM `click`), so a tap that doesn't move the caret still re-shows it. Device-verified. |
| 2 | **Selection handles** (asymmetric left/right teardrops) | long-press word, double-tap word, drag-extend | tap-collapse, typing, new selection | ✅ `SelectionHandle(isStart)` + drag-to-extend, for BOTH plain page text (`pageExtend`) AND in-field `<input>`/`<textarea>` selections (`fieldExtend`, 2026-06-25). The shim reports the selection's caret feet (`sx/sb`,`ex/eb`, flagged `rng`) via the same mirror-div as the caret; the host holds the range geometry separately so Chrome's transient collapse-to-caret (the re-assert fight) doesn't yank the handles to the field edges. **Tap-to-collapse (2026-06-25):** a single tap inside a selection dismisses it to a caret at the tapped offset + insertion handle — the shim's `click` handler collapses explicitly via `offsetFromPoint` (off-window Chrome doesn't do it itself). Device-verified. |
| 3 | **Floating toolbar** (Cut/Copy/Paste/Select-All/Share/…) | selection made, or tap insertion handle (Paste/Select-All) | scroll/fling (hides, returns on settle), handle drag (hides), tap-collapse | ⚠️ `EmbeddedSelectionToolbar` (Cut/Copy/Paste/Select-All). **Hide-during-handle-drag ✅ device-verified; hide-during-scroll ✅ implemented (2026-06-25, via `SelectionUiState.dragging`/`scrolling`; shim sends `ime.scroll` + re-reports geometry on settle) — same gate as drag-hide, but not yet exercised on device (the embedded WebView didn't scroll under injected swipes / cached short harness).** Still missing: insertion-handle Paste popup, overflow, Share/Web-Search/process-text. |
| 4 | **Magnifier / loupe** (the zoom bubble above the finger while dragging a handle or the caret) | finger down + moving on a handle or the caret | finger up | ✅ **Built (2026-06-25).** `Magnifier` bubble in [EmbeddedMagnifier.kt] follows the dragged caret/selection handle, showing live magnified page pixels captured in the `:napplet` provider and shipped over IPC ([EmbeddedMagnifierProbe], option B). Both embed paths wired (browser + napplet); verified on device for the browser path. Capture Y is locked to the caret/selection line (X follows the finger). Possible further polish: RGB_565 to cut encode, themed crosshair, clamp capture X to the line so a fast drag past EOL doesn't show blank. |
| 5 | **Word-granularity long-press** then char-extend | long-press | — | ✅ in-field handle-extend snaps to word boundaries (2026-06-25): `fieldExtend` snaps the dragged edge via `wordStartAt`/`wordEndAt` (end→word end, start→word start), so dragging a word selection's handle extends a word at a time. Device-verified (drag end handle slightly into "world" → whole "hello world"). Page-text extend stays char (no offset model); the "then char" fine mode within a word isn't modeled. |
| 6 | **Double-tap = word, long-press = word, (triple-tap/drag = paragraph)** | tap count | — | ✅ double-tap + long-press both select a word (2026-06-25). Chrome word-selects on the 2nd tap, then abandons it by collapsing to the end (off-window quirk); the host re-assert restores it. The shim `click` handler DEFERS its tap-to-collapse ~300ms and the real `dblclick` cancels that timer, so the word selection survives (a timing-only guard was flaky ~40%). Triple-tap/paragraph not done. |
| 7 | **Smart selection / entity expansion** (`TextClassifier`: phone, URL, address, date → entity actions in toolbar) | selection lands on an entity | — | ❌ not built (low priority) |
| 8 | **Drag selected text** (long-press a selection → drag-and-drop to move) | long-press on existing selection | drop | ❌ not built (low priority) |
| 9 | **Auto-scroll while dragging to a viewport edge** | handle dragged near top/bottom edge | finger leaves edge / up | ✅ works (2026-06-25, user-confirmed). The drag driver (`onMagnify`) detects the finger in the surface's top/bottom edge zone and sends `ime.autoscroll`; the shim scrolls the textarea (else the window) and re-reports geometry, flagged so the hide-on-scroll path doesn't fire. Scrolls per drag-move in the edge zone (not on a perfectly-held finger). **Fixed alongside:** the nav drawer's left-edge swipe was hijacking the edge drag — `EmbeddedSelectionDrag.dragging` (set by `onMagnify`) now suspends the drawer's `gesturesEnabled` while a handle is dragged. |
| 10 | **Caret snapping to character boundaries** | always during caret/handle drag | — | ✅ via `offsetFromPoint` binary search + Y-clamp |
| 11 | **Themed handle/caret drawables + blink** | always | — | ✅/⚠️ The host-drawn handles use `colorScheme.primary` — which IS the native `textSelectHandle`/accent color — so the handle drawables are themed (the actionable part). The caret bar + selection-highlight are drawn by Chrome inside the off-window surface: the caret already blinks natively, and theming its color/the highlight would mean injecting CSS into arbitrary third-party pages (intrusive; `::selection` was already ruled out as non-painting), so those are intentionally left to Chrome. |
| 12 | **Insertion-handle Paste/Select-All mini-popup** | tap the insertion handle | tap elsewhere | ✅ built (2026-06-25). Tapping the bare insertion handle toggles a Paste/Select-All bar above the caret (`SelectionUiState.insertionPopup`, toggled from the handle's unified tap/drag gesture); tap-elsewhere/typing/blur/selection/scroll dismiss it. Fixed a latent bug: toolbar items now consume the *down* (not just the up) so the tap doesn't bleed through to the surface and blur the field. Device-verified (Select-all selects all text, field stays focused). |

Legend: ✅ done · ⚠️ partial · ❌ missing.

### Activation/deactivation is the hard part

Most of the bugs we already fixed were activation-timing bugs (cursor-jumps-to-end,
collapse-on-tap, focus-transfer races). The remaining features each carry their
own state machine.

**Done (2026-06-25): `SelectionUiState`** (`SelectionUiState.kt`) now centralizes
what used to be scattered flags in `EmbeddedTabLayer` (`showInsertionHandle`,
`showSelectionToolbar`, `fieldGeometry`, `rangeFieldGeometry`, `pageSelection`).
It holds the three mutually-exclusive contexts (insertion caret / in-field range /
page-text range) plus the transient modifiers `dragging` and `scrolling`, and
exposes derived visibility (`insertionHandle`, `fieldHandles`, `fieldToolbar`,
`pageHandles`, `pageToolbar`) so the rules are expressed once:
- **toolbar hides while a handle is dragged** (`dragging`, set from the same
  `OnMagnify` lifecycle that drives the loupe) — the dragged handle also hides its
  own teardrop (the loupe stands in), like Android; the other handle stays.
- **all overlays hide while scrolling** (`scrolling`, from the shim's `ime.scroll`);
  the shim re-reports geometry just before `active=false` so they reappear
  repositioned.
Still emergent / TODO: insertion-handle auto-timeout, tap-to-re-show.

**Selection-blink fix (2026-06-25).** A field selection — especially in a `<textarea>` —
flickered: off-window Chrome abandons the selection by collapsing the caret to an
endpoint every ~25 ms, and the FIELD re-assert round-tripped through the host EditText
(`RemoteImeView.onPageState` → `ime.set` → page), leaving a visible collapsed frame each
cycle. Fix: re-assert field selections **synchronously in the shim's `selectionchange`
handler** (mirror of the page-text path that never blinked) — `lastFieldRange`/`lastFieldAt`
tracked via `noteSel()`, and a collapse-to-endpoint within 1500 ms is reverted with `setSel`
(guarded, not re-reported) so it reverts before paint. The host re-assert stays as a
fallback. Device-verified: textarea selection is stable; input select still works.

## Priority order for parity work

1. **Magnifier (#4).** Biggest perceived gap. We already report caret/handle
   geometry; the magnifier needs a *magnified pixel view of the surface* at the
   drag point. Options:
   - **A. Compose-side zoom of a surface snapshot. ❌ RULED OUT (spiked 2026-06-25).**
     The surface is a `SurfaceControlViewHost` — we can't trivially `Bitmap`-grab a
     remote surface from the main process. We spiked `PixelCopy.request(SurfaceView, …)`
     against the live embedded surface (`SurfaceMagnifierProbe`, wired into
     `EmbeddedTabLayer` behind `BuildConfig.DEBUG`, fired on field focus). The capture
     target is the privacysandbox `ContentView extends SurfaceView` — the only real
     child of `SandboxedSdkView` once the session opens. **Result on a clearly-painted
     surface (1080×2088): every capture returns `ERROR_SOURCE_NO_DATA`** (center
     region, repeated 3×). Cause: the WebView pixels live in a *child* `SurfaceControl`
     reparented under the SurfaceView via `ContentView.setChildSurfacePackage(...)`; the
     host SurfaceView's *own* buffer is never drawn into, so `PixelCopy` on the parent
     reads an empty buffer. Host-side pixel capture of the sandboxed content is not
     available. (A `PixelCopy.request(Window, …)` against the host window would also
     miss it — the sandbox layer is a *separate* SurfaceControl z-ordered below the
     window.)
   - **B. Capture in `:napplet` and ship the loupe content. ✅ SPIKED & VIABLE
     (2026-06-25).** Inside the keyless provider the WebView IS a real in-window view, so
     `WebView.draw(Canvas)` into a software bitmap renders real DOM pixels. Spike added
     `MSG_MAGNIFIER_REQUEST`/`MSG_MAGNIFIER_FRAME` to `NappletBrowserContract`:
     `NappletBrowserService.onMagnifierRequest` draws a zoomed slice
     (`canvas.scale(zoom); translate(-(cx-box/2), -(cy-box/2)); webView.draw(canvas)`),
     PNG-encodes it, and ships the bytes back; `EmbeddedBrowserController` (now also an
     `EmbeddedMagnifierProbe`) requests on focus and `EmbeddedTabLayer` logs the result.
     **10-frame burst, 160px source × 1.5× zoom → 240×240 PNG, center `#FF111111`
     (real opaque content):** provider draw 0.7–1.2 ms steady (≈5 ms cold), provider
     total draw+PNG 3–4 ms steady (≈12 ms cold), client round-trip 4–8 ms steady
     (occasional ~18 ms), payload 8–15 KB (far under the 1 MB Binder limit). Comfortably
     within a frame budget if throttled to ~30 fps.

     **✅ Real loupe shipped (2026-06-25).** `MagnifierUiState` + `Magnifier`
     (`EmbeddedMagnifier.kt`); the caret/selection handles call an `OnMagnify` callback
     on drag start/move/end; `EmbeddedTabLayer` tracks the drag point, throttles capture
     requests to one in flight (100 ms timeout), decodes each `MagnifierFrame` to an
     `ImageBitmap`, and floats the bubble above the finger (clamped, flips below near the
     top). Capture mirrored onto BOTH embed paths (browser:
     `NappletBrowserContract`/`NappletBrowserService`/`EmbeddedBrowserController`;
     napplet: `NappletEmbedContract`/`NappletHostService`/`EmbeddedNappletController`).
     Verified on device (browser): drag → bubble shows live magnified "ello world" with
     the caret, centered on the line → follows finger → hides on release. The dead
     Option-A probe (`SurfaceMagnifierProbe`) was removed. **Polish done (2026-06-25):**
     capture Y is locked to the authoritative caret/selection line (the handles pass a
     `lineHalfPx` so the box centers on the line, not the finger or the caret foot); X
     still follows the finger. Remaining nice-to-haves: RGB_565/raw to cut PNG encode,
     themed crosshair, clamp capture X to the line so a fast drag past EOL isn't blank,
     reuse one off-screen bitmap.
   - **Gesture-routing caveat (found during the spike).** The host-drawn handles'
     drag is fragile: the sandbox `ContentView.onTouchEvent` always returns `true`, so
     via Compose's `AndroidView` interop it consumes the drag-move pointer and cancels
     the overlay handle's `detectDragGestures` (synthetic `adb` drags on the handle
     never produced an `onDrag`). The magnifier trigger should ride the existing
     caret/selection-move path (which already round-trips through the shim), not a fresh
     Compose drag layered over the surface.
2. **Toolbar state rules (#3 hide-during-drag/scroll) + insertion-handle popup
   (#12).** Pure Compose/state work, no platform unknowns. Do alongside the
   `SelectionUiState` refactor.
3. **Handle inactivity timeout + tap-to-re-show (#1).** Small.
4. **Double-tap-to-select (#6)** and **word-granularity drag (#5).**
5. **Auto-scroll (#9), themed drawables/blink (#11).**
6. Defer: smart selection (#7), drag-to-move (#8).

## Open platform bug — full-screen round-trip kills highlight paint

**Symptom.** Open an embedded field's page in its own full-screen activity
(where the WebView renders in-window with a native keyboard), then `back` to the
embedded version. From then on, **every** embedded surface in the `:napplet`
process can no longer paint the native selection highlight. Critically:

- DOM selection is still correct (`window.getSelection()` returns the right range).
- Our host-drawn toolbar still appears and **Copy/Cut/Paste still work**.
- Only the **blue highlight rectangle render is dead** — and across *all* embeds,
  not just the page that was opened full-screen.

This points at **global/process WebView state in `:napplet`**, not per-page
state — likely Chrome's selection/popup controller (the same machinery behind
`PopupTouchHandleDrawable` / `SelectionPopupControllerImpl`) entering a state
where it has decided there is no valid window to render selection UI into, after
the surface's window/`ViewAndroidDelegate` changed under it during the
full-screen excursion.

**What we already ruled out (do not re-try blind):**

- `::selection` CSS override — produced *no* paint at all (not even pre-bug).
- Resizing the surface on return — no effect.
- WebView focus-cycle (blur/refocus the page) — produced **ghost carets** (a
  pink caret stuck at field start + a working blue one); still could not select.
- Surface "wake" nudge + lifecycle re-dispatch (`MSG_WAKE`) — nothing changed;
  reverted.

**Hypotheses to test next:**

1. **Recreate the WebView (or the whole `:napplet` session) on return from
   full-screen** instead of trying to revive it. Heaviest hammer, most likely to
   work, but loses page state — acceptable as a fallback if scoped to "a
   full-screen excursion happened."
2. **Confirm process-global vs per-WebView.** Open two embeds, send one
   full-screen, return — does the *other, untouched* embed also lose highlight?
   (Summary says yes; re-confirm deterministically.) If process-global, the fix
   must reset shared Chrome state, which realistically means #1.
3. **Inspect the surface/window handoff.** Log the `SurfaceControlViewHost` /
   `attachedSurfaceControl` identity before vs after; if the surface is
   re-parented but Chrome kept the stale one, re-attaching a fresh
   `SandboxedSdkView` may force Chrome to re-evaluate.
4. **Avoid the in-window full-screen path entirely.** If the full-screen
   "open in own window" reused a *different* WebView/rendering mode, the act of
   switching modes is what corrupts shared state — consider keeping full-screen
   on the same off-window surface (no native keyboard) so the mode never changes.

Until pinned, treat this as a **WebView/Chromium off-window-surface limitation**:
the app layer cannot currently restore the highlight without recreating the
surface (#1).

## How to test

The on-device harness lives at **`tools/ime-test/`** (`index.html` + `README.md`).
It's a single page with an `<input>`, a `<textarea>`, and an on-page log that
timestamps focus/selection/input/composition events, **paint latency**,
long-tasks, and main-thread blocks — the instrumentation that pinned the erase,
caret-jump, and first-letter-freeze bugs, and exactly what we'll want when
profiling the magnifier.

Run it (full details in `tools/ime-test/README.md`):

1. `cd tools/ime-test && python3 -m http.server 8765`
2. Reach it: emulator → `http://10.0.2.2:8765`; USB device →
   `adb reverse tcp:8765 tcp:8765` then `http://localhost:8765`.
3. Open that URL in the **in-app browser** (`BrowserScreen` address bar) to load
   it as an *embedded* tab (the relay path). Opening the same URL full-screen and
   pressing `back` is how you reproduce the highlight bug below.

Console log lines are tagged `[ImeDiag]` and surface in `adb logcat` (the
`:napplet` process owns the WebView console). This is a dev tool — nothing under
`tools/` ships, which is why those diagnostic strings are kept out of `src/`.

## Key files

- `commons/src/commonMain/composeResources/files/napplet/shim.js` — DOM bridge.
  Geometry sources: `caretCoords` (287), `offsetFromPoint` (318), `fieldGeom`
  (336), `reportState` (360), `pageGeom` (462), `sendPageSel` (470), `pageExtend`
  (494). A magnifier built via option B would add a loupe-render here.
- `amethyst/.../embed/EmbeddedTabLayer.kt` — the Compose overlay. `InsertionHandle`
  (557), `SelectionHandle` (496), `EmbeddedSelectionToolbar` (616),
  `PageSelectionOverlay` (460), the `showInsertionHandle`/`showSelectionToolbar`
  state to be folded into a `SelectionUiState`. Magnifier popup (option A) lands
  here.
- `amethyst/.../embed/RemoteImeView.kt` — invisible host `EditText`; selection
  re-assert + copy/cut/paste/select-all + edit callbacks.
- `amethyst/.../embed/EmbeddedImeBridge.kt` — `SelectionGeometry` (caret + handle
  feet + viewport), `ImeEvent.{Focus,State,PageSelection}`, `parseSelectionGeometry`.
- `amethyst/.../{browser/EmbeddedBrowserController,favorites/EmbeddedNappletController}.kt`
  — parse `ime.pagesel` + geometry off the Messenger channel.
- Context: `amethyst/plans/2026-06-19-napplet-sandbox-host.md`,
  `2026-06-24-napplet-embedded-tabs.md`.
