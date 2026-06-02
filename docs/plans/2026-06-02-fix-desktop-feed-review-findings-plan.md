---
title: Fix 5 review findings on PR #3124 desktop feed UI refresh
type: fix
status: active
date: 2026-06-02
pr: https://github.com/vitorpamplona/amethyst/pull/3124
review_comment: https://github.com/vitorpamplona/amethyst/pull/3124#issuecomment-4599816576
worktree: ../AmethystMultiplatform-feed-review
branch: fix/desktop-feed-ui-review (tracks origin/feat/desktop-feed-ui-refresh)
deepened: 2026-06-02
---

# Fix 5 review findings on PR #3124

## Enhancement summary (2026-06-02 deepen-plan)

Eight parallel agents resolved all 5 open questions and surfaced 3 plan revisions:

**Open questions resolved:**
- **Q1 — kind-1 NIP-10 vs kind-1111 NIP-22:** kind-1 NIP-10 confirmed. Android's
  `NotificationReplyReceiver` already routes by parent type
  (`TextNoteEvent` → kind 1, others → kind 1111). Desktop feed loads kind 1
  only (`DesktopFeedFilters.kt:39`). Use kind 1 + `prepareETagsAsReplyTo`.
- **Q2 — extract consume+broadcast couplet:** YES. Five clean call sites (no
  inline complexity) + ordering inconsistency (`TextNoteEvent` does
  consume→broadcast at `ThreadScreen.kt:361` and `FeedScreen.kt:1564`,
  Reaction/Follow do broadcast→consume at `ThreadScreen.kt:424`,
  `FeedScreen.kt:426`/`:1617`). A `desktopApp` extension fixes both volume and
  the ordering drift. Canonical order: consume→broadcast (local-first).
- **Q3 — Phase 4 produceState vs ViewModel:** produceState. `LargeCache.notes`
  is a `ConcurrentSkipListMap` (`LargeCache.jvmAndroid.kt:27`) — weakly
  consistent iterator, safe on main composition coroutine, 50–150ms for ~30k
  notes. No debounce needed; candidate-filter pre-check blocks 80–90% of
  bundles. `FeedViewModel.kt:54-59` precedent collects same stream without
  debounce.
- **Q4 — NoteActions.kt:264 formatSats:** sats, safe to swap to
  `amount.toZapAmount()`. Inputs are hardcoded preset amounts (line 111
  `ZAP_AMOUNTS = listOf(21L, 100L, ...)`) and `LnZapEvent.amount` which is
  already sats (`LnZapEvent.kt:69`).
- **Q5 — WalletColumnScreen.kt:979 formatSats:** intentional. Wallet shows
  precise balance with locale-aware grouping (`1,000,000`). Leave + add
  `// intentional` comment to prevent future drift.

**Plan revisions:**
- **Phase 1 path flattening:** move `ReplyActions` from
  `commons/.../actions/nip10Notes/ReplyActions.kt` to flat
  `commons/.../actions/ReplyActions.kt`. Sister actions (`FollowActions`,
  `ZapActions`, `DmActions`, `SearchActions`) are all flat under `actions/`;
  no `nipNN/` subpackage convention. (Architecture review)
- **Phase 5 simplification:** drop the explicit `requestFocus()` in the Esc
  handler; the column never loses focus during pop (Esc was *received by* the
  focused column). Just `LaunchedEffect(Unit) { requestFocus() }` + the
  existing `.focusable()`. Add `key(column.id) { DeckColumnContainer(...) }`
  wrap in `DeckLayout.kt:111` so `LaunchedEffect(Unit)` survives column
  reordering. (Code-simplicity + focus-audit review)
- **Phase 2 promoted from "optional":** with 5 verified duplicates + order
  inconsistency, extract `Account.dispatch(signed: Event)` as a
  `desktopApp` extension. Canonical order: consume→broadcast. Not in
  `commons` (relay manager + cache are desktop types).

**Android follow-up (out of this PR):** 4 inlined `TextNoteEvent.build` sites
on Android (`ShortNotePostViewModel.kt:1037`, `VoiceReplyViewModel.kt:265`,
`NotificationReplyReceiver.kt:203`, `AmethystAppFunctions.kt:1051`) should
migrate to the new `ReplyActions.replyTo` in a follow-up PR. Tracked in
"Future work" below.

## Overview

Davotoula's review on PR #3124 (`feat/desktop-feed-ui-refresh`) flagged 5 issues
ranging from one **NIP-10 protocol bug** (inline reply emits a tag set other
clients can't thread) down to **consistency bugs** (zap totals bypass the shared
formatter). All confirmed by inspecting `origin/feat/desktop-feed-ui-refresh`.

This plan groups the fixes so dependent ones land in a sequence that compiles at
each step, and routes the protocol/architectural fixes through existing shared
helpers (`TextNoteEvent.build(replyingTo=…)`, `ReactionAction`, `FollowAction`,
`ZapFormatter.showAmount`) rather than introducing new abstractions.

## Findings (root-cause confirmed)

### #3 — Inline reply emits lower-fidelity NIP-10 tag set [PROTOCOL BUG]

**File:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/FeedScreen.kt:1554`

**Current code:**
```kotlin
val template = TextNoteEvent.build(content) {
    val etag = ETag(event.id)
    etag.relay = null
    etag.author = event.pubKey
    eTag(etag)
    pTag(PTag(event.pubKey, relayHint = null))
}
```

**Root cause:** the call uses the **single-arg** `TextNoteEvent.build(note, initializer)`
overload at `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip10Notes/TextNoteEvent.kt:133`
and hand-rolls a minimal reply tag set. It emits a single unmarked `e`-tag and a
single `p`-tag. **No NIP-10 root marker. No carry of the parent's root-e-tag.
No carry of the parent's p-tag chain.** Replying to a note deep in a thread
produces an event with no `root` reference; conformant clients (Damus, Primal,
Coracle…) can't reconstruct the thread.

**Fix:** switch to the **reply-aware overload** at `TextNoteEvent.kt:142`:

```kotlin
fun build(
    note: String,
    replyingTo: EventHintBundle<TextNoteEvent>? = null,
    forkingFrom: EventHintBundle<TextNoteEvent>? = null,
    …
) = eventTemplate(KIND, note, createdAt) {
    alt(shortedMessageForAlt(note))
    if (replyingTo != null || forkingFrom != null) {
        markedETags(prepareETagsAsReplyTo(replyingTo, forkingFrom))
    }
    initializer()
}
```

`prepareETagsAsReplyTo` (already in quartz) handles **root marker, reply marker,
parent root/p-tag carry** correctly. The inline path was bypassing it.

### #4 — Reaction/follow/reply business logic in desktop composables

**Files:**
- `FeedScreen.kt:1611` — reaction (likes from inline expansion)
- `FeedScreen.kt:423` — follow (follow pill from feed)
- `FeedScreen.kt:1554` — reply (inline reply input)

**Current state (verified):**
- **Follow** already uses `FollowAction.follow(pubKeyHex, signer, currentList)`
  (commons). The complaint is the surrounding `cache.consume → broadcast`
  couplet inlined in the composable.
- **Reaction** already uses `ReactionAction.reactTo(EventHintBundle, "+", signer)`
  (commons). Same couplet inlined.
- **Reply** does NOT use a shared builder (see #3).

**CLAUDE.md rule (`commons/ARCHITECTURE.md:73-88`):** "actions package (CLI-safe):
Event builders for user actions (follow, zap…). The canonical entry point for
non-UI callers."

**Fix:** introduce a new shared action that mirrors `FollowActions` for kind-1
replies. The consume+broadcast couplet stays inline (2 lines, platform-specific
relay/cache wiring), but the *protocol-touching* build moves out:

```kotlin
// commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/actions/nip10Notes/ReplyActions.kt
object ReplyActions {
    suspend fun replyTo(
        parent: EventHintBundle<TextNoteEvent>,
        content: String,
        signer: NostrSigner,
    ): TextNoteEvent {
        val template = TextNoteEvent.build(content, replyingTo = parent)
        return signer.sign(template)
    }
}
```

Desktop call site becomes:
```kotlin
val parentText = event as? TextNoteEvent ?: return@withContext
val signed = ReplyActions.replyTo(EventHintBundle(parentText, null), content, account.signer)
localCache.consume(signed, relay = null)
relayManager.broadcastToAll(signed)
```

This matches the shape already used for `FollowAction.follow` at `FeedScreen.kt:423`
and `ReactionAction.reactTo` at `NoteActions.kt:1393`. Drift between desktop and
Android paths is bounded to a 3-line couplet that won't grow.

### #1 — Related content stale after one scan

**File:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/thread/RelatedContentRow.kt:83`

**Current code:**
```kotlin
DisposableEffect(noteId) {
    val results = mutableListOf<Note>()
    // scan localCache.notes once
    localCache.notes.forEach { … }
    relatedItems = results.sortedByDescending { it.createdAt() }.take(6).map { … }
    onDispose { }
}
```

**Root cause:** `DisposableEffect(noteId)` re-runs only on `noteId` change. The
scan reads `localCache.notes` (a `LargeCache`) at composition time; nothing
re-runs the scan as the cache fills. Expanding a note on a cold cache leaves
the section empty/partial until collapse+re-expand. Also missing keys:
`noteHashtags` and `authorPubKey` (cosmetic — caller stabilises these per noteId).

**Fix:** observe the cache's change stream and re-scan on bundle arrivals.
`DesktopLocalCache` exposes `eventStream: DesktopCacheEventStream` with
`newEventBundles: SharedFlow<Set<Note>>` (`DesktopLocalCache.kt:719-743`).

Two options:

**Option A (simpler, matches inline-section scale):** `produceState` keyed by
`(noteId, hashtagsHash, authorPubKey)` that collects `newEventBundles` and
re-runs the scan when relevant events land:

```kotlin
val relatedItems by produceState<List<CompactNoteData>>(emptyList(), noteId, authorPubKey, noteHashtags) {
    fun rescan() { value = scanRelated(localCache, noteId, authorPubKey, noteHashtags) }
    rescan() // initial
    localCache.eventStream.newEventBundles.collect { bundle ->
        if (bundle.any { isCandidate(it, noteHashtags, authorPubKey) }) rescan()
    }
}
```

**Option B (matches FeedViewModel family):** new `RelatedContentViewModel` in
`commons/src/commonMain/.../viewmodels/related/`, taking a `FeedFilter` style
"by hashtag OR by author" predicate and exposing
`StateFlow<List<CompactNoteData>>`. Heavier but consistent with the rest of the
feed system.

**Recommendation:** **Option A** — the related-content row is a 6-item sidecar,
not a feed. A ViewModel adds wiring without solving an actual problem here.
Deepen-plan agent may overrule.

### #2 — Deck columns steal focus from each other

**File:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/deck/DeckColumnContainer.kt:150`

**Current code:**
```kotlin
val focusRequester = remember { FocusRequester() }
LaunchedEffect(currentOverlay) { focusRequester.requestFocus() }
```

**Root cause:** `LaunchedEffect(currentOverlay)` fires on the column's first
composition **and on every overlay change**. In a multi-column deck, when
column B opens an overlay, column B's effect grabs focus — yanking it out of
column A's inline reply input mid-typing.

**Fix:** decouple "request focus once on initial composition" from "Escape
handler needs focus to be live." The Escape key path works as long as the
column owns focus when the user presses Escape — which it does after the
initial composition. Match the existing pattern at
`EditProfileScreen.kt:380` (`LaunchedEffect(Unit) { focusRequester.requestFocus() }`)
**and** scope the effect so only the column the user is interacting with
re-grabs focus when a nested overlay closes (i.e. on `popOverlay()`).

Concretely:
1. Change `LaunchedEffect(currentOverlay)` → `LaunchedEffect(Unit)` for the
   initial focus request.
2. When the user presses Escape and `navState.pop()` succeeds, explicitly call
   `focusRequester.requestFocus()` in the key handler (intent-driven, not
   composition-driven).

This contains focus stealing to the column the user actually interacted with.

### #5 — Zap totals bypass shared formatter

**Files:**
- `RelatedContentRow.kt:137` — `zapCount = "${note.zapsAmount.toLong()}"` (raw, e.g. `"1500000"`)
- `CommentItem.kt:155` — `text = formatZapAmount(zapAmount)` calling local helper
- `CommentItem.kt:166-171` — private `formatZapAmount(sats: Long)` hand-rolled k/M

**Shared formatter (`commons/src/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/util/ZapFormatter.kt`):**
- `fun showAmount(amount: BigDecimal?): String` — G/M/k suffixes, `""` for null/<0.01
- `fun showAmountWithZero(amount: BigDecimal?): String` — same, `"0"` instead of `""`
- `fun Long.toZapAmount(): String`
- `fun Int.toZapAmount(): String`

`Note.zapsAmount` is `BigDecimal` (`commons/src/commonMain/.../model/Note.kt:183`),
so use `showAmount(note.zapsAmount)` directly in `RelatedContentRow`. `CommentItem`
takes a `Long`, so use `zapAmount.toZapAmount()` and delete the local helper.

**Also flagged (outside review but same root cause):**
- `NoteActions.kt:264` — local `formatSats(amount: Long)` with only `k` suffix.
- `WalletColumnScreen.kt:979` — local `formatSats` using `NumberFormat` (intentional?
  wallet flows may want full sats — leave but document).

Cover the two review-flagged sites + `NoteActions.kt:264`. Defer wallet.

## Phased implementation plan

Phases ordered so each compiles + tests cleanly without depending on later work.

### Phase 1 — Shared `ReplyActions` (fixes #3, completes #4 reply)

**Create:** `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/actions/ReplyActions.kt` *(flat — matches `FollowActions.kt`, `ZapActions.kt`, `DmActions.kt`; no `nip10Notes/` subpackage)*

```kotlin
object ReplyActions {
    suspend fun replyTo(
        parent: EventHintBundle<TextNoteEvent>,
        content: String,
        signer: NostrSigner,
    ): TextNoteEvent {
        val template = TextNoteEvent.build(content, replyingTo = parent)
        return signer.sign(template)
    }
}
```

Mirror `FollowActions.buildFollow` shape (`commons/.../actions/FollowActions.kt:69`).

**Test:** `commons/src/commonTest/kotlin/com/vitorpamplona/amethyst/commons/actions/ReplyActionsTest.kt`
— assert the signed event has:
- A marked e-tag with `marker=reply` pointing at the parent id.
- A marked e-tag with `marker=root` (pointing at parent's root if parent had one,
  else parent's id).
- All parent p-tags carried + parent's `pubKey` appended.

Use `runTest { … }` from `kotlinx-coroutines-test`, in-test signer is
`NostrSignerInternal(KeyPair(privHex.hexToByteArray()))` — match `FollowActionsTest.kt:25-37`.

**Edit:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/FeedScreen.kt` (~ line 1554) — replace inline build with `ReplyActions.replyTo(EventHintBundle(parentText, null), content, account.signer)`. Guard parent kind with `event as? TextNoteEvent`; if not a kind-1, skip / log (replies to non-kind-1 from the feed inline path were never well-defined and are out of scope; matches Android's `NotificationReplyReceiver.kt:136-156` routing).

**Acceptance:**
- [ ] `./gradlew :commons:jvmTest --tests "*ReplyActionsTest*"` green.
- [ ] Manual: reply to a deep-thread note from desktop, inspect the broadcast event in a relay log → has `e-tag root` + `e-tag reply` + carries all parent `p` tags.
- [ ] Reply renders in Damus/Primal under the correct thread.

### Phase 2 — Extract reaction/follow/reply consume+broadcast couplet (Option B confirmed)

Deepen-plan audit found **5 clean duplicate sites** with an ordering
inconsistency between them:

| File:line | Signer | Order today |
|---|---|---|
| `ThreadScreen.kt:361` | inline `TextNoteEvent.build` reply | consume → broadcast |
| `ThreadScreen.kt:424` | `ReactionAction.reactTo` | broadcast → consume |
| `FeedScreen.kt:426` | `FollowAction.follow` | broadcast → consume |
| `FeedScreen.kt:1564` | inline `TextNoteEvent.build` reply | consume → broadcast |
| `FeedScreen.kt:1617` | `ReactionAction.reactTo` | broadcast → consume |

No site has inline extra work (snackbars, retries) entangled with the couplet.

**Create:** `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/cache/EventDispatch.kt`

```kotlin
/**
 * Canonical local-first dispatch: write to the local cache before broadcasting
 * so the UI reflects the user's action immediately, even if relay round-trips fail.
 */
suspend fun dispatch(
    signed: Event,
    localCache: DesktopLocalCache,
    relayManager: RelayManager,
) {
    localCache.consume(signed, relay = null)
    relayManager.broadcastToAll(signed)
}
```

(Or, equivalent — as an extension on a small `DispatchContext` if the call
sites already have one. Keep in `desktopApp` because both `LocalCache` and
`RelayManager` are desktop-side types.)

**Migrate all 5 sites** to call `dispatch(signed, localCache, relayManager)`.
Fixes the ordering drift (everyone goes local-first) and shrinks call sites
to one line.

**Acceptance:**
- [ ] `grep -rn "broadcastToAll" desktopApp/` shows only the call inside `EventDispatch.kt` + any non-couplet uses.
- [ ] No remaining call sites do consume + broadcast inline (other than the helper).
- [ ] Reactions, follows, and replies all still round-trip correctly in a manual sanity test.

### Phase 3 — ZapFormatter swap (fixes #5)

**Edit:** `RelatedContentRow.kt:137` —
```kotlin
zapCount = if (note.zapsAmount > BigDecimal.ZERO) showAmount(note.zapsAmount) else "",
```

**Edit:** `CommentItem.kt:155, 166-171` — replace `formatZapAmount(zapAmount)` with
`zapAmount.toZapAmount()`. **Delete the private `formatZapAmount` fun at line 166-171.**

**Edit:** `NoteActions.kt:264` — swap to `amount.toZapAmount()`. Verified
sats (not msats): inputs are `ZAP_AMOUNTS = listOf(21L, 100L, 500L, 1000L, 5000L, 10000L)`
at line 111 + `LnZapEvent.amount` which is sats per `LnZapEvent.kt:69`.
**Delete the private `formatSats` fun if no remaining references** (run
`grep -n formatSats desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/NoteActions.kt` after swap).

**Skip:** `WalletColumnScreen.kt:979` `formatSats` is **intentional** — wallet
balance display uses locale-aware grouping (`NumberFormat.getNumberInstance().format`)
for full-precision sats. Out of review scope. Add a one-line `// intentional: wallet shows precise sats with locale grouping; not a ZapFormatter target` comment to prevent future drift.

**Acceptance:**
- [ ] `./gradlew :desktopApp:compileKotlin` green.
- [ ] Visual: a note with 1.5M sats renders `1.5M` (or `1M`, matching commons
      semantics), not `1500000`.

### Phase 4 — Cache-aware Related (fixes #1)

**Edit:** `RelatedContentRow.kt:83-145` — replace `DisposableEffect(noteId)` with
`produceState` (Option A) keyed on `(noteId, authorPubKey, noteHashtags)`:

```kotlin
val relatedItems by produceState<List<CompactNoteData>>(
    initialValue = emptyList(),
    key1 = noteId, key2 = authorPubKey, key3 = noteHashtags,
) {
    val lowercaseTags = noteHashtags.map { it.lowercase() }.toSet()
    fun rescan() {
        runCatching {
            value = scanRelated(localCache, noteId, authorPubKey, lowercaseTags)
        }.onFailure {
            // weakly-consistent iterator may rarely surface; skip this tick
        }
    }
    rescan()
    localCache.eventStream.newEventBundles.collect { bundle ->
        val matters = bundle.any { n ->
            n.event is TextNoteEvent &&
            n.idHex != noteId &&
            (n.event?.pubKey == authorPubKey ||
             n.event?.tags?.isTaggedHashes(lowercaseTags) == true)
        }
        if (matters) rescan()
    }
}
```

Extract the existing scan body into a private top-level `scanRelated(...)` so
both the initial call and the bundle-driven re-run share it.

**Safety notes (deepen-plan):**
- `LargeCache.notes` is `ConcurrentSkipListMap` (`LargeCache.jvmAndroid.kt:27`)
  — weakly-consistent iterator, safe on main composition coroutine.
- Scan is O(N): ~50–150ms for ~30k notes; fine on main.
- No debounce: candidate-filter blocks 80–90% of bundles. Matches
  `FeedViewModel.kt:54-59` precedent (collects same stream without debounce).
- If hot-loop observed in production, retroactively add `.debounce(150)`
  (precedent: `SearchBarState.kt:87`, `BookmarkListState.kt`).

**Acceptance:**
- [ ] Cold-cache repro: open a note in a fresh session, related section starts
      empty; as kind-1 events stream in matching the hashtag or author, related
      cards appear without collapse+re-expand.
- [ ] No re-render storm: rescan only fires when a bundle contains a candidate.

### Phase 5 — Focus gating (fixes #2)

**Edit:** `DeckColumnContainer.kt:147-152` —
```kotlin
LaunchedEffect(Unit) { focusRequester.requestFocus() }   // once on column creation
```

That's the only effect change. **No explicit `requestFocus()` in the Escape
handler**: deepen-plan focus-audit verified the column never loses focus during
back-nav (Escape was received by the focused column → it still has focus after
`navState.pop()`). Adding it would be cargo-cult.

**Also edit:** `DeckLayout.kt:111` — wrap the `forEachIndexed` body in a
`key(column.id) { DeckColumnContainer(...) }` so `LaunchedEffect(Unit)`
survives column reordering. Without `key()`, moving a column in the deck list
re-fires the effect for the wrong column instance.

**Acceptance:**
- [ ] Two-column repro: in column A's inline reply text field, type characters
      while column B opens/closes an overlay → column A keeps focus, no
      characters lost.
- [ ] Escape still pops nested overlays in the focused column.
- [ ] Reorder a column in the deck (drag if supported, or remove+re-add) →
      typing focus in unrelated columns is preserved.

## System-Wide Impact

### Interaction graph

- **Reply path:** `InlineReplyInput.onSend(content)` → `ReplyActions.replyTo(...)` (commons) → `signer.sign` → `localCache.consume` (DesktopLocalCache) → `relayManager.broadcastToAll` (NostrClient WS pool) → relay round-trip → cache update → `eventStream.newEventBundles` → Phase-4 `produceState` re-scan → related section refresh. Phase 4 is downstream of Phase 1 only by happy coincidence (a reply might match its parent's hashtags) — no hard coupling.
- **Follow path:** unchanged, already routed through `FollowAction.follow`.
- **Reaction path:** unchanged, already routed through `ReactionAction.reactTo`.

### Error propagation

- `ReplyActions.replyTo` is `suspend` and propagates `signer.sign` failures (cancellation, signer rejection). Desktop call site already runs in `withContext(Dispatchers.IO)` — wrap in `try/catch` to surface a snackbar on signing failure (Android path does this in `CommentPostViewModel.sendPostSync`; desktop currently swallows).
- Phase 4 `produceState` collect runs in the column's coroutine scope; cancelled when composable leaves composition. Exceptions in `scanRelated` (e.g. ConcurrentModificationException on `LargeCache.forEach`) would crash the collector — wrap `rescan()` body in `runCatching` to skip on transient cache mutations.

### State lifecycle risks

- Phase 1: signed reply written to `localCache` before broadcast succeeds. If
  the broadcast fails, the reply is visible locally but not on relays.
  This matches existing behaviour for reaction/follow paths; no new risk.
- Phase 4: `produceState` collects an unbounded `SharedFlow`. If `newEventBundles`
  emits at high rate (cold cache fill), `scanRelated` runs O(N) per bundle.
  `LargeCache.notes` size for an active user is ~10k–50k notes; a full scan is
  ~ms. Acceptable; if hot-loop observed, debounce via `collectLatest` +
  `delay(150)`.

### API surface parity

- `ReplyActions` is JVM-only consumer today (desktop), but lives in
  `commons/commonMain` so Android can adopt it (and should — `NewPostViewModel`
  on Android currently inlines a similar `TextNoteEvent.build` call). Tracked as
  follow-up, **not in this PR**.

### Integration test scenarios

1. **NIP-10 thread fidelity:** create note A → reply B to A → reply C to B from
   desktop. Inspect C's tags: must contain `["e", A.id, "", "root"]` and
   `["e", B.id, "", "reply"]` and `["p", A.pubKey]` + `["p", B.pubKey]`.
2. **Cold-cache related:** clear local DB, open a thread → Related row empty →
   simulate incoming kind-1 events matching parent's hashtag → row populates
   without user interaction.
3. **Multi-column focus:** open two columns side by side. Start typing in column
   A's inline reply. Open a profile overlay in column B. Verify typed characters
   stay in column A.
4. **Reply to non-kind-1:** open a thread whose root is a `LongFormContentEvent`
   (kind 30023). Inline reply must either disable (preferred) or route through
   `CommentEvent` (NIP-22) — open question below.
5. **Zap formatting:** seed a note with 1_500_000 sats zaps. Both `RelatedContentRow`
   and `CommentItem` render `1.5M` (or `1M` per commons rules).

## Acceptance criteria (rollup)

### Functional

- [ ] Inline-reply event from desktop, when broadcast, threads correctly in
      ≥1 non-Amethyst client (Damus or Primal verified).
- [ ] Related section refreshes from cold cache without user interaction.
- [ ] Typing in column A's reply box doesn't lose focus when column B opens an
      overlay.
- [ ] Zap totals render with k/M/G suffix in `RelatedContentRow` and
      `CommentItem`.
- [ ] Existing inline reaction/follow continue to work (no regression).

### Non-functional

- [ ] No new `--no-verify` commits.
- [ ] `./gradlew spotlessApply` clean.
- [ ] `./gradlew test` green for `:commons:jvmTest` and `:quartz:jvmTest`.
- [ ] No new Kotlin warnings introduced.

### Quality gates

- [ ] `ReplyActionsTest` covers root-marker, reply-marker, p-tag carry.
- [ ] Hand-rolled `formatZapAmount` deleted (grep returns 0 in `desktopApp/`).

## Dependencies & risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `EventHintBundle<TextNoteEvent>` cast fails when parent is `CommentEvent` / `LongFormContentEvent` | Med | Guard with `as? TextNoteEvent`; skip + log if null. Open question covers full support. |
| `produceState` re-runs scan storm on cold cache fill | Low | Filter bundle for candidate match before rescan; debounce if observed. |
| `LargeCache.forEach` concurrent modification during rescan | Low | Wrap rescan body in `runCatching`. |
| Focus fix breaks ESC → back-nav inside a column | Low | Explicit `requestFocus()` in pop handler covers it; manual repro before push. |
| `ZapFormatter.showAmount` returns `""` for amount < 0.01 — different from current `"0"`-on-empty | Low | Use `showAmountWithZero` if `"0"` desired, else gate with `if (note.zapsAmount > ZERO)`. |

## Resolved questions (deepen-plan)

All Q1–Q5 resolved — see "Enhancement summary" at top for verdicts + evidence.

## Future work (separate PR, not in this branch)

- **Android kind-1 reply migration.** Four Android sites inline
  `TextNoteEvent.build` and should migrate to the new `ReplyActions.replyTo`
  (single source of truth across platforms):
  - `amethyst/.../ShortNotePostViewModel.kt:1037`
  - `amethyst/.../VoiceReplyViewModel.kt:265`
  - `amethyst/.../NotificationReplyReceiver.kt:203`
  - `amethyst/.../AmethystAppFunctions.kt:1051`
- **CLI `amy reply` verb.** `ReplyActions` lives in `commons/commonMain` and
  is CLI-safe — a future Amy reply verb wires straight to it.
- **Wallet vs Zap formatter consolidation.** `WalletColumnScreen.kt:979`
  intentionally diverges (locale-aware full-precision). Revisit if/when a
  unified "amount display" component is built.

## Sources & references

### Internal references

- Review comment: https://github.com/vitorpamplona/amethyst/pull/3124#issuecomment-4599816576
- PR: https://github.com/vitorpamplona/amethyst/pull/3124
- `commons/ARCHITECTURE.md:73-88` — actions package boundary
- `quartz/.../nip10Notes/TextNoteEvent.kt:142` — reply-aware build overload
- `quartz/.../nip10Notes/tags/MarkedETag.kt:44-60` — NIP-10 marker enum + tag-array
- `quartz/.../nip10Notes/tags/prepareETagsAsReplyTo.kt` — root/reply tag-carry helper
- `commons/.../actions/FollowActions.kt:69` — pattern to mirror for `ReplyActions`
- `commons/.../model/nip25Reactions/ReactionAction.kt:50` — sister action
- `commons/.../util/ZapFormatter.kt` — shared zap-amount formatter
- `desktopApp/.../cache/DesktopLocalCache.kt:719-743` — `eventStream.newEventBundles`
- `desktopApp/.../ui/EditProfileScreen.kt:380` — `LaunchedEffect(Unit)` focus pattern to mirror
- `amethyst/.../ui/note/nip22Comments/CommentPostViewModel.kt:128, 447-571` — Android reply path (NIP-22 reference, not directly reused)

### CLAUDE.md conventions

- "Check existing implementations first — most logic already exists" — confirmed: shared helpers exist; this is reuse, not new abstraction.
- "Pre-commit hooks run spotless — always `./gradlew spotlessApply` before commit"
- "Never use `--no-verify`"
- "Verify, Don't Guess" — root causes verified by reading code at each line cited.
