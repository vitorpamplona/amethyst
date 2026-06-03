---
title: "feat: New posts chip on desktop feed"
type: feat
status: active
date: 2026-06-02
origin: docs/brainstorms/2026-06-02-stale-feed-on-launch-new-posts-chip-brainstorm.md
---

# feat: New posts chip on desktop feed

## Overview

Add a Twitter/Mastodon-style "New posts" floating pill chip to the Amethyst Desktop home feed. The chip slides down from the top — anchored just below the `FeedTabsHeader` (the search bar / header card) — whenever new events arrive while the user is scrolled away from the top of the list. Tapping the chip smooth-scrolls to position 0 and slides the chip back up off-screen. Scrolling to the top manually dismisses it the same way.

This addresses the user-reported "stale feed on launch" perception bug (see brainstorm: `docs/brainstorms/2026-06-02-stale-feed-on-launch-new-posts-chip-brainstorm.md`). The bug is **perceptual**, not architectural — the live `updateFeedWith()` reactive path already works; new events silently prepend. Users don't notice because the auto-stick (`StickToTopOnPrepend`) only fires when already at position 0.

## Problem Statement / Motivation

On cold launch, three factors combine to make the feed feel "stuck on 4-5 day old items":

1. `LocalRelayStore` hydrates events up to **7 days old** (`LocalRelayStore.kt:143-153`)
2. Feed subscription filters have **no `since` parameter** — relay returns its last 200 events regardless of age (`FeedSubscription.kt:41-74`)
3. **No perceptual signal** when fresh events finally prepend silently (`FeedContentState.kt:63-68` + `WatchScrollToTop.kt`)

User confirmation: _"it loads eventually I think. One nice UX would be to show a quick chip or tooltip that animates and shows 'New items - Scroll to top' and tapping on it triggers the scroll."_

Per brainstorm: subscription `since` tuning and hydration window changes are explicitly **out of scope**. This plan addresses only the perceptual fix.

## Proposed Solution

A reusable Compose Multiplatform composable + state holder in `commons/`, integrated into Desktop `FeedScreen`. Three components:

1. **`NewPostsChip`** — a stateless visual pill (`Surface(shape = RoundedCornerShape(999.dp))`) wrapped in `AnimatedVisibility` with vertical slide-in/slide-out + fade.
2. **`rememberNewPostsChipState(feedContentState, listState)`** — derives chip visibility by observing the feed's top-item id and the `LazyListState`. Exposes `visible: State<Boolean>` and `dismiss()`.
3. **Integration** — `FeedScreen.kt` mounts the chip inside the existing outer `Box`, aligned top-center, with `offset(y = headerSpacerHeight + 8.dp)` so it sits just below the `FeedTabsHeader` (which expands to 300.dp when search is active and collapses to 60.dp otherwise — chip follows via the animated DP).

### Animation spec (per user request: "nice slide from top and slide out to top")

```kotlin
AnimatedVisibility(
    visible = chipState.visible.value,
    enter = slideInVertically(
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        initialOffsetY = { fullHeight -> -fullHeight - 16 },  // start above the chip's resting position
    ) + fadeIn(tween(220)),
    exit = slideOutVertically(
        animationSpec = tween(220, easing = FastOutLinearInEasing),
        targetOffsetY = { fullHeight -> -fullHeight - 16 },   // exit back upward off-screen
    ) + fadeOut(tween(180)),
)
```

The `-fullHeight - 16` initial/target offset guarantees the chip is fully off-screen above its anchor point at the start of enter / end of exit, so it never half-appears clipped against the header card.

### Visibility predicate

Chip is `visible` when **all three** hold:
- New events have arrived since the user last saw the top (`lastSeenTopId != currentTopId`)
- The user is NOT at the top: `firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0`
- The feed is in `FeedState.Loaded` with a non-empty list

Predicate matches the inverse of `StickToTopOnPrepend`'s "at top" check (`WatchScrollToTop.kt:141,145`), so the two systems are mutually exclusive — auto-snap when at top, chip when not.

## Technical Considerations

### Architecture impacts

- **Reuse, don't reinvent.** The chip subscribes to `FeedContentState.feedContent` (existing) and `LazyListState` (existing). No new reactive infrastructure.
- **No changes to `FeedContentState`.** Top-item tracking lives in the state holder, not on ContentState — keeps ContentState focused on data.
- **Common module placement.** Composable lives in `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/NewPostsChip.kt`. Android can adopt it later — out of scope today.
- **Each deck column gets its own independent chip** because each column has its own `viewModel = remember(feedMode, activeFeedId)` and its own `lazyListState` (`FeedScreen.kt:433, 654`).

### Performance implications

- `snapshotFlow { listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset }` is cheap — already used elsewhere in `FeedScreen.kt:664`.
- One additional `StateFlow<Boolean>` observation per visible feed. Negligible.
- No new subscriptions to relays.

### Security considerations

None. This is presentational.

### Accessibility

- Chip is a `Surface(onClick = ...)` — natural focus + click target.
- `semantics { contentDescription = "New posts available, tap to scroll to top" }` on the Surface.
- Slide animation respects `LocalDensity`; no fixed-pixel hacks.
- Defer keyboard shortcut (e.g. Home key) to a follow-up — out of scope.

## System-Wide Impact

- **Interaction graph:** New events from relays → `DesktopRelaySubscriptionsCoordinator.consumeEvent` → 250ms bundler → `cacheEventStream.emitNewNotes` → `FeedViewModel` collector → `FeedContentState.updateFeedWith` → `feedContent` StateFlow emits new `LoadedFeedState`. **Chip state holder observes the StateFlow** and re-evaluates visibility predicate. Existing `StickToTopOnPrepend` continues to observe `scrollToTop` counter unchanged.
- **Error propagation:** None — chip only renders when `FeedState.Loaded`. `FeedState.FeedError` / `Loading` / `Empty` → chip hidden.
- **State lifecycle:** Chip state is `remember`ed inside `FeedScreen`. When `feedMode` or `activeFeedId` changes, the parent composable's `remember(feedMode, activeFeedId)` causes ViewModel recreation, which resets ContentState, which resets chip state. Per-column scope verified — no cross-column leakage.
- **API surface parity:** Composable is in `commons/` so Android could adopt later. Currently only Desktop wires it. Android continues to use existing `StickToTopOnPrepend` + bottom-nav dot pattern.
- **Integration test scenarios:**
  1. Cold launch with stale cache: chip should appear when fresh events arrive after subscription EOSE, only if user has scrolled below position 0.
  2. User scrolls down → events arrive → chip appears → user scrolls back to top manually → chip disappears.
  3. User taps chip → list animates to top → chip exits upward → top-most note now matches `lastSeenTopId`.
  4. User switches Following → Global mid-chip → chip dismisses immediately (state holder resets with new ContentState).
  5. Deck mode: two feed columns side-by-side, only the column with new arrivals shows its chip.

## Acceptance Criteria

### Functional

- [ ] When `FeedContentState.feedContent` emits a new top-most note id AND `lazyListState.firstVisibleItemIndex > 0` (or `firstVisibleItemScrollOffset > 0`), the chip slides down from above the search header and becomes visible.
- [ ] Chip text reads exactly **"New posts"** (no count — per brainstorm resolved decision).
- [ ] Chip contains a `MaterialSymbols.ArrowUpward` icon to the left of the text.
- [ ] Chip is anchored at `Alignment.TopCenter` of the outer `FeedScreen` Box, with `Modifier.offset(y = headerSpacerHeight + 8.dp)` so it tracks the animated header height (60.dp normal, 300.dp when search is expanded).
- [ ] Slide-in animation: `slideInVertically(tween(280, FastOutSlowInEasing), initialOffsetY = { -it - 16 })` + `fadeIn(tween(220))`.
- [ ] Slide-out animation: `slideOutVertically(tween(220, FastOutLinearInEasing), targetOffsetY = { -it - 16 })` + `fadeOut(tween(180))`.
- [ ] Tapping the chip launches `lazyListState.animateScrollToItem(0)` and triggers exit animation. Chip dismisses and `lastSeenTopId` updates to the current top.
- [ ] When the user reaches position 0 by any means (manual scroll, tap, `StickToTopOnPrepend` auto-snap), chip auto-dismisses with slide-out animation and `lastSeenTopId` updates.
- [ ] Switching `feedMode` (Following ↔ Global ↔ Custom) resets chip state — chip is hidden on mount of the new mode.
- [ ] Existing `StickToTopOnPrepend` behavior is unchanged: when user IS at position 0 and new events prepend, list still auto-snaps to top.
- [ ] In desktop deck view, each column shows its own independent chip — no cross-column leakage.
- [ ] Chip is hidden in `Loading`, `Empty`, and `FeedError` states.
- [ ] Search-expanded state: chip remains visible if conditions hold but its `offset.y` follows the animated 300.dp header height so it stays just below the expanded search card.

### Non-functional

- [ ] No regression in feed scroll FPS — animations run at ≥ 60 fps in desktop.
- [ ] Accessibility: chip has `contentDescription = "New posts available, tap to scroll to top"`.
- [ ] Theme: chip uses `MaterialTheme.colorScheme.surfaceContainerHigh` for background, `colorScheme.onSurface` for text/icon. Respects dark/light theme automatically.
- [ ] Elevation: `Surface(tonalElevation = 4.dp, shadowElevation = 6.dp)` — provides separation from feed content.

### Quality gates

- [x] `./gradlew :commons:compileKotlinJvm` passes.
- [x] `./gradlew :desktopApp:compileKotlin` passes.
- [x] `./gradlew spotlessApply` applied before commit.
- [x] Unit test for `NewPostsChipState` visibility predicate (pure function over list-state inputs) — 5 cases pass.
- [ ] Manual reproduction: cold launch, leave home feed open, scroll down a few items, wait for relay subscription to deliver fresh events → chip appears with slide-down animation. Tap → smooth scroll to top + slide-up. Verify with screen capture.

## Implementation Plan

### File-level changes

```
commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/
├── NewPostsChip.kt               # NEW — stateless visual composable
└── NewPostsChipState.kt          # NEW — state holder + rememberNewPostsChipState()

commons/src/jvmTest/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/
└── NewPostsChipStateTest.kt      # NEW — unit tests for visibility predicate

desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/
└── FeedScreen.kt                 # MODIFY — wire chip into outer Box
```

### Step 1 — `NewPostsChipState.kt`

```kotlin
// commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/NewPostsChipState.kt

class NewPostsChipState internal constructor(
    private val lastSeenTopId: MutableState<String?>,
    val visible: State<Boolean>,
    val onTap: suspend () -> Unit,
) {
    fun acknowledgeTop(newTopId: String?) {
        lastSeenTopId.value = newTopId
    }
}

@Composable
fun rememberNewPostsChipState(
    feedContentState: FeedContentState,
    listState: LazyListState,
): NewPostsChipState {
    val lastSeenTopId = remember(feedContentState) { mutableStateOf<String?>(null) }
    val feedState by feedContentState.feedContent.collectAsState()

    val currentTopId by remember(feedState) {
        derivedStateOf {
            (feedState as? FeedState.Loaded)?.feed?.value?.list?.firstOrNull()?.idHex
        }
    }

    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    // Initialize lastSeenTopId on first non-null top
    LaunchedEffect(currentTopId) {
        if (lastSeenTopId.value == null && currentTopId != null) {
            lastSeenTopId.value = currentTopId
        }
    }

    // Acknowledge top when user reaches it
    LaunchedEffect(isAtTop, currentTopId) {
        if (isAtTop) lastSeenTopId.value = currentTopId
    }

    val visible = remember {
        derivedStateOf {
            !isAtTop &&
                currentTopId != null &&
                lastSeenTopId.value != null &&
                lastSeenTopId.value != currentTopId
        }
    }

    val scope = rememberCoroutineScope()
    return remember(feedContentState, listState) {
        NewPostsChipState(
            lastSeenTopId = lastSeenTopId,
            visible = visible,
            onTap = {
                listState.animateScrollToItem(0)
                lastSeenTopId.value = currentTopId
            },
        )
    }
}
```

### Step 2 — `NewPostsChip.kt`

```kotlin
// commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/NewPostsChip.kt

@Composable
fun NewPostsChip(
    state: NewPostsChipState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = state.visible.value,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            initialOffsetY = { -it - 16 },
        ) + fadeIn(tween(220)),
        exit = slideOutVertically(
            animationSpec = tween(220, easing = FastOutLinearInEasing),
            targetOffsetY = { -it - 16 },
        ) + fadeOut(tween(180)),
    ) {
        Surface(
            onClick = { scope.launch { state.onTap() } },
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(36.dp)
                .semantics {
                    contentDescription = "New posts available, tap to scroll to top"
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(symbol = MaterialSymbols.ArrowUpward, size = 16.dp)
                Text(
                    text = "New posts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
```

### Step 3 — `FeedScreen.kt` integration

Inside `FeedScreen`'s outer `Box(Modifier.fillMaxSize())` (line 604):

- Move `lazyListState` creation up one level so it's accessible to both the LazyColumn (inside `ReadingColumn`) and the chip overlay.
- Capture the `headerSpacerHeight` animated DP so the chip can follow it.
- Add the chip as a new layer between Layer 1 (feed content) and Layer 2 (scrim) — or after Layer 3 (header) so it visually sits above the feed but below the header. Z-order matters: chip should NOT cover the header when search is expanded; placing it BELOW the header in the Box's child order, but with offset that puts it under the header, is correct.

```kotlin
// Inside FeedScreen, before `Box(modifier = Modifier.fillMaxSize())` at line 604
val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
val headerSpacerHeight by animateDpAsState(
    targetValue = if (searchActive) 300.dp else 60.dp,
    animationSpec = tween(200),
    label = "headerSpacer",
)

Box(modifier = Modifier.fillMaxSize()) {
    // Layer 1: Feed content
    ReadingColumn {
        Spacer(Modifier.height(headerSpacerHeight))
        when (val state = feedState) {
            // ... existing branches ...
            is FeedState.Loaded -> {
                val loadedState by state.feed.collectAsState()
                // ... use lazyListState from outer scope ...
                LazyColumn(state = lazyListState, ...) { /* unchanged */ }
            }
        }
    }

    // Layer 1.5: New-posts chip (NEW) — anchored just below the header card
    val loadedFeedState = (feedState as? FeedState.Loaded)?.feed?.collectAsState()?.value
    if (loadedFeedState != null && loadedFeedState.list.isNotEmpty()) {
        val chipState = rememberNewPostsChipState(
            feedContentState = viewModel.feedState,
            listState = lazyListState,
        )
        NewPostsChip(
            state = chipState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = headerSpacerHeight + 8.dp)
                .padding(top = 4.dp),
        )
    }

    // Layer 2: Search scrim (unchanged)
    // Layer 3: FeedTabsHeader (unchanged)
    // ...
}
```

### Step 4 — Test plan

**Unit test** (`commons/src/jvmTest/kotlin/.../NewPostsChipStateTest.kt`):

Extract the pure predicate into a testable helper:

```kotlin
internal fun shouldShowNewPostsChip(
    isAtTop: Boolean,
    currentTopId: String?,
    lastSeenTopId: String?,
): Boolean = !isAtTop && currentTopId != null && lastSeenTopId != null && currentTopId != lastSeenTopId
```

Cases to cover:
- `isAtTop = true` → false
- `currentTopId = null` (empty feed) → false
- `lastSeenTopId = null` (first paint, no acknowledgement yet) → false
- `currentTopId == lastSeenTopId` (no new events) → false
- All three valid + non-matching ids → true

**Manual repro**:

1. `./gradlew :desktopApp:run`
2. Open home feed, wait for it to load.
3. Scroll down 5–10 items.
4. Wait ~5s for relay subscription to deliver fresh events (or trigger via posting from another client).
5. Observe chip slides down from above the search header.
6. Tap chip → list smooth-scrolls to top, chip slides up off-screen.
7. Switch to Global → chip immediately hidden.
8. Repeat with search expanded — chip appears below the expanded 300.dp header.

## Success Metrics

- Subjective: user (you) confirms feed no longer "feels stuck" on launch.
- Functional: chip appears reliably within 250ms (one bundler cycle) of a fresh event arriving while scrolled.
- No regressions: existing `StickToTopOnPrepend` auto-snap still fires when user is at position 0.

## Dependencies & Risks

| Item | Risk | Mitigation |
|------|------|------------|
| `lazyListState` hoisting from inside `FeedState.Loaded` branch up to outer scope | Existing viewport-aware metadata loading (`LaunchedEffect` at line 659) and side-padding logic must continue to work | Keep `lazyListState` reference identical; only its declaration point moves. Verify the `LaunchedEffect(lazyListState, loadedState)` block still composes correctly. |
| Chip overlaps a future floating action button or overlay | Layout collision | Use `Modifier.zIndex(1f)` if z-order issues arise; defer to PR review. |
| Recomposition thrash from `firstVisibleItemScrollOffset` changing on every pixel | Performance | Wrap in `derivedStateOf` (already in plan); only `isAtTop: Boolean` flips trigger downstream recomposition. |
| `MaterialSymbols.ArrowUpward` codepoint not in the subset font | Tofu glyph | `ArrowUpward = MaterialSymbol("")` is already declared in `MaterialSymbols.kt:36` — no font regeneration needed. Verified per research. |
| Animation feels too fast / too slow | Subjective | 280ms in, 220ms out are conservative defaults; tune during manual test. |

## Sources & References

### Origin

- **Brainstorm document:** [`docs/brainstorms/2026-06-02-stale-feed-on-launch-new-posts-chip-brainstorm.md`](../brainstorms/2026-06-02-stale-feed-on-launch-new-posts-chip-brainstorm.md)
- Key decisions carried forward:
  - Floating overlay placement (not sticky list item)
  - No count — text always reads "New posts"
  - Reset on feed mode change
  - Scope limited to Desktop; commons composable available for future Android adoption
  - Subscription `since` parameter and hydration window changes explicitly out of scope

### Internal references

- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/FeedContentState.kt:44-231` — feed state holder, `scrollToTop` counter, `updateFeedWith` entry point
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/feeds/WatchScrollToTop.kt:45-52,141,145` — existing scroll-to-top pattern and "at top" predicate to mirror
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/FeedScreen.kt:604,610-614,654,683,770` — outer `Box`, animated `headerSpacerHeight`, `lazyListState` creation, `LazyColumn`, `FeedTabsHeader`
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/deck/DeckColumnContainer.kt:117-202,234-318` — per-column FeedScreen instances confirm per-column chip scope
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/search/SearchPill.kt:48-78` — existing pill pattern (`Surface(shape = RoundedCornerShape(999.dp), color = surfaceContainerHigh, height = 36.dp)`) — reuse the visual conventions
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/icons/symbols/MaterialSymbols.kt:36` — `ArrowUpward = MaterialSymbol("")` (already in subset font)

### External references

None — pattern is standard Compose `AnimatedVisibility` + `slideInVertically` / `slideOutVertically`. Material 3 components in use.

---

## Unanswered Questions

- chip click target hit-area (36.dp tall pill — minimum touch target on touch displays?)
- exact `surfaceContainerHigh` vs `primaryContainer` color choice — subjective, decide during manual test
- whether to add a thin border/outline for contrast in light theme
- should chip auto-dismiss after N seconds of no interaction, or persist until user acts? (current plan: persist)
- accessibility: should chip announce on appearance via live region, or only on focus?
- should Android adopt the chip in a follow-up plan, or stick with the existing dot + auto-stick pattern?
