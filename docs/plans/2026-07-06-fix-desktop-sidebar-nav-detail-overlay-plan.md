---
title: Desktop sidebar nav does not replace detail overlay
type: fix
status: active
date: 2026-07-06
---

# Desktop sidebar nav does not replace detail overlay

## Overview

On Amethyst Desktop, when the user is viewing a detail screen (Profile, Thread,
Article, Editor) opened from feed content, clicking a sidebar navigation item
(Home, Notifications, Messages, etc.) appears to do nothing until the user
presses the back button. Only then does the sidebar destination become visible.
The expected behavior is that the sidebar tap **replaces** the detail screen —
i.e., clears the detail overlay and shows the tapped sidebar destination.

## Problem Statement

Desktop uses two independent navigation states that render on top of each other:

- **Sidebar destination** — `SinglePaneState` (single-pane) or per-column
  `DeckColumn.type` inside `DeckState` (deck).
- **Detail overlay stack** — `ColumnNavigationState` (a homegrown
  push/pop stack of `DesktopScreen`), one instance per layout / per deck column,
  rendered as an `AnimatedContent(targetState = navState.current)` overlay on
  top of the sidebar destination's `RootContent`.

The sidebar's `onNavigate` handler in `Main.kt:1484-1498` mutates **only** the
sidebar destination. The overlay `navState` is untouched, so its opaque
`Surface(fillMaxSize)` keeps covering the (already-swapped) `RootContent`.
Pressing back pops the overlay stack, which finally reveals the destination
that swapped moments earlier — creating the "click did nothing" illusion.

**Buggy click flow (SINGLE_PANE, user on Thread detail, taps Home sidebar):**

1. User previously called `navState.push(DesktopScreen.Thread(...))` from feed
   (`SinglePaneLayout.kt:127`). Overlay `_stack = [Thread]`.
2. User clicks Home in sidebar → `Main.kt:1495` calls
   `singlePaneState.navigate(HomeFeed)`.
3. `RootContent`'s `columnType` recomposes to `HomeFeed` — but it's drawn
   *behind* the overlay `Surface` at `SinglePaneLayout.kt:148`.
4. `navState._stack` is unchanged, `currentOverlay = Thread`, `AnimatedContent`
   keeps the Thread overlay on top. User sees no change.
5. User presses back → `navState.pop()` → overlay dismisses → Home is revealed.

DECK mode has the same shape: each `DeckColumnContainer` at `:145` has its own
`navState = remember(column.id) { ColumnNavigationState() }`, and the sidebar
handler calls `deckState.focusExistingColumn(type)` or `deckState.addColumn(type)`
(`Main.kt:1487-1492`) without touching the target column's overlay.

## Proposed Solution

Every sidebar destination change should also clear the overlay stack of the
target column (SINGLE_PANE: the only column; DECK: the column being focused).
This produces the "replace" behavior the user expects and mirrors most
sidebar-nav apps (Discord, Slack, Chrome tabs on sidebar click).

Two candidate designs were considered — see **Alternative Approaches** below.
**Recommended:** signal-based clear (Option B), because it keeps overlay-state
ownership local to the layout that renders it, avoids restructuring
`ColumnNavigationState` ownership, and produces a small, surgical diff.

### Signal-based clear (Option B) — Recommended

1. **`SinglePaneState`** — add
   `val clearOverlaySignal: SharedFlow<Unit>` fed by a private
   `MutableSharedFlow<Unit>(extraBufferCapacity = 1)`. In `navigate(type)`,
   `tryEmit(Unit)` after the destination update.
   `SinglePaneLayout` collects the signal in a `LaunchedEffect(Unit)` and calls
   `navState.clear()` on emission.

2. **`DeckState`** — add
   `val clearOverlaySignal: SharedFlow<String>` (column ID payload) fed by
   `MutableSharedFlow<String>(extraBufferCapacity = 8)`. In
   `focusExistingColumn(type)`, look up the matched column's ID and
   `tryEmit(id)` after the focus mutation.
   `DeckColumnContainer` collects the signal in
   `LaunchedEffect(column.id) { deckState.clearOverlaySignal.collect { if (it == column.id) navState.clear() } }`.

3. **No changes to `ColumnNavigationState`** — its existing `clear()` method
   (`DeckColumnContainer.kt:117`) is used as-is.

4. **`addColumn(type)` is untouched** — a newly-created column starts with an
   empty overlay stack, so no clear is needed.

### Same-item tap

Tapping the sidebar item for the currently-focused destination (e.g., on
`Home > Thread` detail, tapping Home again) must also clear the overlay. This
falls out naturally from the signal-based design because:

- `SinglePaneState.navigate(HomeFeed)` sets the value on a `MutableStateFlow`
  which won't emit a new state (same value), **but** the signal is emitted
  independently regardless of the state comparison. ✅
- `DeckState.focusExistingColumn(HomeFeed)` runs unconditionally when the
  sidebar item is tapped, and the signal is emitted unconditionally. ✅

### DECK-mode scope (per brainstorm answer)

The user selected **"only focused/active column"**. In DECK, sidebar tap always
targets a specific column (either the focused one already exists and we focus
it, or a new column is added and it starts empty). No other columns' overlays
are ever cleared. The signal payload `column.id` makes this explicit — each
column filters on its own ID.

## Technical Considerations

**File touch list (estimated diff <150 LOC):**

| File | Change |
|------|--------|
| `desktopApp/.../ui/deck/SinglePaneState.kt` | Add `clearOverlaySignal: SharedFlow<Unit>`; emit in `navigate(type)` |
| `desktopApp/.../ui/deck/DeckState.kt` | Add `clearOverlaySignal: SharedFlow<String>`; emit in `focusExistingColumn(type)` |
| `desktopApp/.../ui/deck/SinglePaneLayout.kt` | `LaunchedEffect(Unit) { singlePaneState.clearOverlaySignal.collect { navState.clear() } }` |
| `desktopApp/.../ui/deck/DeckColumnContainer.kt` | `LaunchedEffect(column.id) { deckState.clearOverlaySignal.collect { if (it == column.id) navState.clear() } }` — needs a `deckState` param or a `CompositionLocal`; check if `DeckColumnContainer` already sees `deckState` |

**`DeckColumnContainer` and `deckState` reachability:** need to verify whether
`deckState` is currently threaded into `DeckColumnContainer` as a param. If not,
the smallest change is adding a `clearOverlaySignal: SharedFlow<String>` param
(rather than dragging the whole `DeckState` through). This keeps the container
decoupled from `DeckState`'s wider API.

**Editor screen** (`DesktopScreen.Editor`) sits on the same overlay stack. If a
user has unsaved composer content and taps a sidebar item, this fix will
dismiss the composer. **Accepted for v1** (matches "replace" semantics). A
"discard unsaved draft?" dialog is out of scope — flagged in Open Questions.

**Animation:** `AnimatedContent` observes `navState.current`. Setting
`_stack.clear()` transitions `current` from `DesktopScreen` to `null`, which
triggers the existing exit animation (slide-out + fade). No new animation code
needed.

**Threading:** `SharedFlow.tryEmit` on `MutableSharedFlow(extraBufferCapacity = 1)`
is non-suspending and safe from any thread; sidebar clicks run on Main. The
collector runs inside a `LaunchedEffect` which is Main-bound. No concurrency
concerns.

**Deck column removal:** the signal buffer holds at most 8 entries, so a stale
emit targeting a since-removed column simply matches no collector and is
discarded. No cleanup needed on column removal.

**Related sidebar path — `onOpenSettings`** (`Main.kt:1470-1483`) uses the same
`focusExistingColumn / addColumn` pattern for the Settings destination and the
same `singlePaneState.navigate(Settings)` for SINGLE_PANE. Because the clear
logic lives inside `SinglePaneState.navigate` and `DeckState.focusExistingColumn`,
this path is automatically fixed with no additional wiring. ✅

## System-Wide Impact

- **Interaction graph:**
  - `MainSidebar.onNavigate(type)` → (DECK) `deckState.focusExistingColumn` or
    `.addColumn`; (SINGLE_PANE) `singlePaneState.navigate`.
  - New: state mutation *also* emits `clearOverlaySignal`, which fires
    `navState.clear()` inside the appropriate layout composable.
- **Error propagation:** `tryEmit` returns a Boolean; a full buffer drops the
  emit. Buffer is sized (1 for single-pane, 8 for deck) to make drops
  effectively impossible during normal use. No exceptions thrown either way.
- **State lifecycle risks:** `ColumnNavigationState._stack.clear()` unwinds
  overlays without invoking their disposers explicitly — but Compose's
  `DisposableEffect`s inside overlay screens run when the composable leaves
  composition, so per-screen cleanup (subscription cancellation, etc.) runs
  correctly.
- **API surface parity:** the fix touches only sidebar-driven navigation. Feed
  content clicks (`navState.push(...)`) and back-button (`navState.pop()`) are
  unchanged.
- **Integration test scenarios** — see Acceptance Criteria manual matrix.

## Acceptance Criteria

**Functional:**

- [ ] SINGLE_PANE: on any detail screen (Profile / Thread / Article / Editor),
      tapping any sidebar item clears the overlay and shows the tapped
      destination in a single click.
- [ ] SINGLE_PANE: multi-level overlay (e.g., Thread → Article) is fully
      cleared on sidebar tap (not just popped one level).
- [ ] SINGLE_PANE: tapping the sidebar item for the currently-focused
      destination also clears the overlay.
- [ ] DECK: tapping a sidebar item that focuses an existing column clears
      **only that column's** overlay; other columns' overlays are preserved.
- [ ] DECK: tapping a sidebar item that adds a new column starts that new
      column with an empty overlay (unchanged behavior).
- [ ] DECK: tapping the sidebar item for the currently-focused column type
      also clears that column's overlay.
- [ ] `onOpenSettings` (which shares the sidebar handler shape) exhibits the
      same clear-on-tap behavior in both modes.
- [ ] Back button behavior unchanged: pops one overlay level; on empty stack it
      remains a no-op (per existing `pop()` guard at
      `DeckColumnContainer.kt:110`).
- [ ] Feed-driven `navState.push(...)` unchanged: pushing overlays from feed
      content still works.

**Non-functional:**

- [ ] Overlay exit animation still plays (slide-out + fade over ~200ms).
- [ ] No new lint / spotless violations.
- [ ] No regression in `./gradlew :desktopApp:compileKotlin`.
- [ ] Feed scroll position on the underlying destination is preserved when
      returning to it via a sidebar-tap-clear.

## Alternative Approaches Considered

### Option A: Hoist `ColumnNavigationState` to `Main.kt`

Move the `navState` declarations out of `SinglePaneLayout` and
`DeckColumnContainer` into `Main.kt`, co-located with `singlePaneState` /
`deckState`. Sidebar `onNavigate` mutates both states directly.

- **Pros:** No signaling; state changes are all synchronous imperative code
  right where the sidebar is wired.
- **Cons:** For DECK, `Main.kt` needs a `Map<String, ColumnNavigationState>`
  keyed by column ID with lifecycle management on `addColumn` /
  `removeColumn`. Larger refactor; changes DeckState API surface. **Rejected**
  for the size of the refactor.

### Option B: Signal-based clear (chosen)

See "Proposed Solution" above.

### Option C: Reactive `LaunchedEffect` on destination change

`LaunchedEffect(currentColumnType) { navState.clear() }` inside the layouts.

- **Pros:** Zero API surface change.
- **Cons:** `LaunchedEffect` key equality doesn't fire when the value is
  unchanged, so **same-item tap does not clear the overlay** — violating the
  Q3 answer. **Rejected.**

## Dependencies & Risks

- **No new dependencies.**
- **Risk: touching sidebar wire-up in `Main.kt`** — a large, load-bearing
  composable. Mitigation: minimal edit, no restructuring, only add
  `LaunchedEffect` calls inside the two layouts.
- **Risk: DECK column disposal races with signal emit** — `MutableSharedFlow`
  with a bounded buffer will drop rather than block; a late emit for a
  disposed column simply matches no collector. No hazard.
- **Risk: user with unsaved composer content taps sidebar** — draft loss.
  See Open Questions.

## Testing Strategy

**Unit:** minimal — `ColumnNavigationState.clear()` already exists; no new
public methods added on that class. `SinglePaneState.navigate` and
`DeckState.focusExistingColumn` gain a signal emission, verifiable with a
turbine collector in a small test if time permits.

**Manual test matrix** (both modes, run in worktree after build):

| # | Scenario | Expected |
|---|---------|----------|
| 1 | SINGLE_PANE: Home → open Thread from feed → tap Notifications sidebar | Notifications shows; Thread overlay gone |
| 2 | SINGLE_PANE: Home → open Profile → tap Home sidebar | Home root shows; Profile overlay gone |
| 3 | SINGLE_PANE: Home → open Thread → open Article inside Thread → tap Home | Home shows; both overlay levels cleared |
| 4 | SINGLE_PANE: Home → open Editor (composer) → tap Home sidebar | Editor closes; Home shows (accepted draft loss) |
| 5 | SINGLE_PANE: Home root (no overlay) → tap Notifications | Notifications shows (unchanged) |
| 6 | SINGLE_PANE: Home → open Profile → tap Home (same item) | Home root shows; Profile overlay gone |
| 7 | DECK: 2 columns (Home, Notifications). In Home, open Thread. Tap Home sidebar | Home column's Thread overlay cleared; Notifications column unchanged |
| 8 | DECK: same setup. In Home, open Thread. Tap Notifications sidebar | Focus moves to Notifications column; Home column's Thread overlay **preserved** (correct per Q2) |
| 9 | DECK: same setup, Notifications column has previously-opened Profile overlay in its own stack. Tap Notifications sidebar | Focus moves to Notifications column, its Profile overlay cleared |
| 10 | DECK: no Messages column. Tap Messages sidebar | New Messages column added, empty overlay (unchanged) |
| 11 | Back-button behavior post-fix, both modes | Unchanged: pops one level; no-op on empty stack |

## Open Questions

- Unsaved composer draft: on sidebar tap that clears an active `Editor`
  overlay, should we prompt "discard draft?" — deferred to v2.
- DECK Q8 (cross-column sidebar tap preserving other columns' overlays):
  confirm this is the intended UX. Q2 answer was "focused column only" which
  supports preservation, but if the user later reports feeling this is
  stale/confusing we may want a global-clear toggle.

## Sources & References

- Root-cause research: agent report from `repo-research-analyst` on
  2026-07-06 in this session (pinned lines below).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt:1484-1505`
  — sidebar `onNavigate` wire-up.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/deck/SinglePaneState.kt:34`
  — `SinglePaneState.navigate`.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/deck/SinglePaneLayout.kt:83,133`
  — layout-local `navState` + `AnimatedContent` overlay.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/deck/DeckColumnContainer.kt:88-119,145,228`
  — `ColumnNavigationState` definition + per-column `navState` +
  `AnimatedContent`.
