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
| 1 | **Insertion handle** (the teardrop "blob" under the caret) | tap in editable text; tap again to re-show | typing, scroll start, focus loss, ~4s inactivity timeout | ✅ `InsertionHandle` — shows on caret geometry, hides via `onEdited`. **Missing the inactivity timeout and the re-show-on-tap.** |
| 2 | **Selection handles** (asymmetric left/right teardrops) | long-press word, double-tap word, drag-extend | tap-collapse, typing, new selection | ✅ `SelectionHandle(isStart)` + drag-to-extend via `pageExtend`/caretmove |
| 3 | **Floating toolbar** (Cut/Copy/Paste/Select-All/Share/…) | selection made, or tap insertion handle (Paste/Select-All) | scroll/fling (hides, returns on settle), handle drag (hides), tap-collapse | ⚠️ `EmbeddedSelectionToolbar` has Cut/Copy/Paste/Select-All only. **No hide-during-drag, no hide-during-scroll, no insertion-handle Paste popup, no overflow, no Share/Web-Search/process-text.** |
| 4 | **Magnifier / loupe** (the zoom bubble above the finger while dragging a handle or the caret) | finger down + moving on a handle or the caret | finger up | ❌ **Not built.** This is the "zoom bubble" called out — highest-value missing piece. |
| 5 | **Word-granularity long-press** then char-extend | long-press | — | ⚠️ partial — selection works; granularity (word-snap while dragging, then char) not modelled |
| 6 | **Double-tap = word, long-press = word, (triple-tap/drag = paragraph)** | tap count | — | ❌ double-tap-to-select not wired (only long-press) |
| 7 | **Smart selection / entity expansion** (`TextClassifier`: phone, URL, address, date → entity actions in toolbar) | selection lands on an entity | — | ❌ not built (low priority) |
| 8 | **Drag selected text** (long-press a selection → drag-and-drop to move) | long-press on existing selection | drop | ❌ not built (low priority) |
| 9 | **Auto-scroll while dragging to a viewport edge** | handle dragged near top/bottom edge | finger leaves edge / up | ❌ not built — needed once fields can be off-screen/long |
| 10 | **Caret snapping to character boundaries** | always during caret/handle drag | — | ✅ via `offsetFromPoint` binary search + Y-clamp |
| 11 | **Themed handle/caret drawables + blink** | always | — | ⚠️ teardrop shape matches; color/blink not themed to `textSelectHandle`/`textCursorDrawable` |
| 12 | **Insertion-handle Paste/Select-All mini-popup** | tap the insertion handle | tap elsewhere | ❌ not built |

Legend: ✅ done · ⚠️ partial · ❌ missing.

### Activation/deactivation is the hard part

Most of the bugs we already fixed were activation-timing bugs (cursor-jumps-to-end,
collapse-on-tap, focus-transfer races). The remaining features each carry their
own state machine. Before adding more, we should **centralize selection-UI state**
in one place (today it's spread across `showInsertionHandle`, `showSelectionToolbar`,
page-vs-field flags in `EmbeddedTabLayer`). A single `SelectionUiState`
(none / insertion / range / dragging-handle / scrolling) makes the hide/show rules
(toolbar hides while dragging, magnifier shows only while dragging, handle
auto-times-out) expressible instead of emergent.

## Priority order for parity work

1. **Magnifier (#4).** Biggest perceived gap. We already report caret/handle
   geometry; the magnifier needs a *magnified pixel view of the surface* at the
   drag point. Options:
   - **A. Compose-side zoom of a surface snapshot.** The surface is a
     `SurfaceControlViewHost` — we can't trivially `Bitmap`-grab a remote
     surface from the main process. Would need `PixelCopy` against the
     `SurfaceView`/`SurfaceControl`, then draw a clipped, scaled bitmap in a
     popup that follows X and stays clamped to the dragged line's Y (native
     behavior). Verify `PixelCopy` works against the sandboxed surface before
     committing to this.
   - **B. Render the magnifier in `:napplet`.** Have `shim.js`/the host WebView
     produce the loupe content (a zoomed DOM render) and ship it. Heavier, but
     sidesteps cross-process pixel capture.
   - Start by spiking A with `PixelCopy.request(SurfaceView, …)`; if it returns
     a valid bitmap for our surface, A is far simpler.
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
