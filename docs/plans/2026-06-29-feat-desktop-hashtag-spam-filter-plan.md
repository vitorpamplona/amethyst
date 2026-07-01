---
title: Desktop Hashtag-Spam Filter
type: feat
status: active
date: 2026-06-29
origin: docs/brainstorms/2026-06-29-feat-hashtag-spam-filter-brainstorm.md
deepened: 2026-06-29
---

# Desktop Hashtag-Spam Filter

## Enhancement Summary

**Deepened on:** 2026-06-29 (same day as plan write).

**Review agents used:** architecture-strategist, code-simplicity-reviewer,
pattern-recognition-specialist, performance-oracle, agent-native-reviewer,
security-sentinel · plus best-practices research (Compose collapse-UX + Slider
patterns) and a Quartz/NoteCard-variant verification sweep.

### Key corrections vs initial plan

1. **Repost unwrap uses `note.replyTo`, not `containedPost()`.** Desktop's
   `DesktopLocalCache.consumeRepost()` already resolves the boosted event into
   `note.replyTo` at consume time — a pointer chase, not a JSON parse per render.
2. **NoteCard signature mismatch.** Desktop `NoteCard` takes `NoteDisplayData`,
   not `Note`. Spam check is hoisted to the **caller** (`FeedScreen`,
   `QuotedNoteEmbed`, thread renderer); `NoteCard` itself stays untouched, the
   caller chooses between `NoteCard(...)` and `CollapsedSpamNote(...)`.
3. **Persistence moves to `commons/jvmMain/`** with a stable
   `java.util.prefs` node name shared between Desktop and the `amy` CLI —
   closes the agent-native parity gap from day one.
4. **Settings UI gets its own "Content Filters" section** in `Main.kt`'s
   settings screen, not under `RelaySettingsScreen` (this isn't a relay
   concern).
5. **Reveal state simplifies to `rememberSaveable(note.idHex)`** — drop the
   `LocalRevealedSpamState` CompositionLocal + `SnapshotStateMap`. Trade-off:
   the same spam note revealed in column A stays collapsed in column B —
   accepted as v1 limitation.
6. **Slider commits on `onValueChangeFinished`** with a local `Float` state
   driving the thumb. Live label reads the local float. No `debounce`, no
   `prefs.flush()` thrash, no recomposition storm during drag.
7. **`collectAsState` hoisted to column scope** (or higher) and scalars
   pushed down to leaf items — not collected per-card.
8. **`HashtagSpamCheck` placed in `commons/.../hashtags/`** (existing
   package), not a new `filters/` package.
9. **Self + follow exemption** merged into a single `exemptKeys: Set<HexKey>`
   parameter.
10. **`NoOpHashtagSpamSettings` dropped.** Always provide
    `PreferencesHashtagSpamSettings` at App root via
    `compositionLocalOf { error("Provide LocalHashtagSpamSettings") }`.
11. **Notifications-tab compact card is out of scope v1** — uses a custom
    56 dp composable that doesn't funnel through `NoteCard`. All other
    Desktop note-render surfaces (Home/Hashtag/Profile/Search/Thread
    replies/Embedded quotes) DO funnel through `NoteCard` and pick up the
    filter for free.
12. **Quartz API name correction:** `containedPost()`, not
    `containedNote()`.

### New considerations discovered

- `note.replyTo` is precomputed by `DesktopLocalCache.consumeRepost`
  (`desktopApp/.../cache/DesktopLocalCache.kt:401-416`). Use it.
- `Kind3Follows.authors` is a `val` on an `@Immutable` data class
  (`commons/.../nip02FollowList/Kind3FollowListState.kt:100-104`), so
  `account.followingKeySet()` returns a stable reference — no extra caching
  needed. The plan's earlier reactivity hedge was unnecessary.
- `containedPost()` already returns `null` on parse failure
  (`quartz/.../nip18Reposts/RepostEvent.kt:87-92`) — no extra try/catch.
- Embedded quotes (`QuotedNoteEmbed`) and search results both call
  `NoteCard()` directly, so the caller-side check handles them naturally
  when we patch those call sites.

---

## Overview

Detect notes that abuse `t` (hashtag) tags as a visibility trick and render
them as a compact reveal-on-click placeholder instead of the normal note
card. Logic in `commons/` (callable by Desktop, future Android, and the
`amy` CLI). UI + settings shipped on Desktop first. Reuses Quartz's
`hasMoreHashtagsThan` primitive.

**Carried forward from brainstorm**
(`docs/brainstorms/2026-06-29-feat-hashtag-spam-filter-brainstorm.md`):
collapse-with-reveal · single global threshold · default 5 (slider 1–20,
with off-switch) · auto-exempt long-form articles (kind 30023) + followed
authors · persisted via `java.util.prefs.Preferences`.

**Resolved during planning + deepening:** default ON · repost wrapper
checks the inner wrapped event's tags (via pre-resolved `note.replyTo`) ·
thread root auto-expands but replies stay collapsed · hashtag-feed columns
still filter · reveal state is session-scoped via `rememberSaveable` keyed
by note id · settings persisted under a `commons/jvmMain` `java.util.prefs`
node shared with `amy`.

## Problem Statement

Hashtag-spam — posts with 10–30 `t` tags chosen to maximize cross-feed
visibility — pollutes every multi-column deck view. The cost scales with
column count: Desktop's TweetDeck-style UI exposes the problem more than
Android. Existing `AntiSpamFilter` only catches duplicate content, not
visibility-bombs.

## Proposed Solution

### High-level approach

A pure check function in `commons/` (`HashtagSpamCheck.isHashtagSpam(...)`)
called by the **callers** of each note-card composable (feed `LazyColumn`
item lambdas, embedded-quote renderer, thread item renderer). When the check
returns `true`, the caller renders a `CollapsedSpamNote` placeholder
instead of the normal note card. The placeholder takes primitive scalars
so the same composable is reusable across Desktop and (later) Android.

Settings (enabled flag + integer threshold) live behind a
`HashtagSpamSettings` interface in `commons/`, with a
`PreferencesHashtagSpamSettings` JVM implementation backing
`java.util.prefs.Preferences.userRoot().node("com/vitorpamplona/amethyst/filters")`
— shared with `amy` CLI for agent-native parity.

### Why **not** a FeedFilter

`commons/.../ui/feeds/AdditiveFeedFilter.kt` is **exclusionary** —
`applyFilter()` returns a `Set<Note>` and items not in the set vanish.
Collapse-with-reveal needs to keep the item and render it differently, so
the decision belongs at render time, not in the feed pipeline. The
brainstorm doc named the unit `HashtagSpamFilter`; renamed to
`HashtagSpamCheck`.

### Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│ commons/  (platform-agnostic, callable by Desktop / amy / Android)│
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ commonMain/                                                  │ │
│ │   hashtags/HashtagSpamCheck.kt        (pure function)        │ │
│ │   hashtags/HashtagSpamSettings.kt     (interface, @Stable)   │ │
│ │   hashtags/displayedEvent.kt          (Note → Event? helper) │ │
│ │   ui/note/CollapsedSpamNote.kt        (scalar-param card)    │ │
│ │   ui/LocalHashtagSpamSettings.kt      (CompositionLocal,     │ │
│ │                                        error-on-default)     │ │
│ │ jvmMain/                                                     │ │
│ │   hashtags/PreferencesHashtagSpamSettings.kt                 │ │
│ │     (java.util.prefs-backed impl, shared node name)          │ │
│ └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │
┌─────────────────────────────┴────────────────────────────────────┐
│ desktopApp/  (only the UI wire-up + caller-side check)           │
│   Main.kt                                                        │
│     CompositionLocalProvider(LocalHashtagSpamSettings provides   │
│                              PreferencesHashtagSpamSettings()) { │
│       App() …                                                    │
│     }                                                            │
│   ui/settings/HashtagSpamSettingsSection.kt                      │
│     (Switch + Slider; local Float thumb; commit on              │
│      onValueChangeFinished)                                      │
│   feeds/FeedScreen.kt (and QuotedNoteEmbed, ThreadScreen replies)│
│     LazyColumn { items(notes, key = { it.idHex }) { note ->      │
│       if (isHashtagSpam(...)) CollapsedSpamNote(...)             │
│       else NoteCard(NoteDisplayData(note), …)                    │
│     }}                                                           │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│ cli/  (amy — v2 subcommands, plan-out only)                      │
│   amy filter hashtag-spam get / set                              │
│   amy notes is-spam <neventid|hex>                               │
│   amy notes feed --include-spam=false (default)                  │
└──────────────────────────────────────────────────────────────────┘
```

### Pure check function

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/hashtags/HashtagSpamCheck.kt`:

```kotlin
object HashtagSpamCheck {
    /**
     * Pure: no side effects, no IO. Caller passes plain scalars so this
     * stays Compose-friendly for `remember(...)` keys and unit-testable
     * without setting up a CompositionLocal.
     *
     * `displayedEvent` is the event whose body the note card actually
     * renders — for kind 6/16 reposts that means the wrapped inner event
     * resolved through `Note.displayedEvent()`.
     *
     * `exemptKeys` should already include the user's self pubkey plus
     * every pubkey in the current follow list; merged at the call site so
     * this function stays single-purpose.
     */
    fun isHashtagSpam(
        displayedEvent: Event?,
        authorPubkey: HexKey?,
        enabled: Boolean,
        threshold: Int,
        exemptKeys: Set<HexKey>,
    ): Boolean {
        if (!enabled) return false
        if (displayedEvent == null) return false
        if (displayedEvent.kind == LongFormContentEvent.KIND) return false      // 30023
        if (authorPubkey != null && authorPubkey in exemptKeys) return false
        return displayedEvent.tags.hasMoreHashtagsThan(threshold)
    }
}
```

### Displayed-event resolver

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/hashtags/displayedEvent.kt`:

```kotlin
/**
 * For reposts (kind 6) and generic reposts (kind 16), the "displayed
 * event" is the wrapped inner event already resolved on `note.replyTo`
 * by the consume pipeline. Falls back to `containedPost()` only if the
 * cache hasn't materialised the reply yet; that path JSON-parses and
 * returns null on bad content (Quartz API already handles try/catch).
 */
fun Note.displayedEvent(): Event? {
    val e = this.event ?: return null
    return when (e) {
        is RepostEvent -> replyTo?.lastOrNull()?.event ?: e.containedPost()
        is GenericRepostEvent -> replyTo?.lastOrNull()?.event ?: e.containedPost()
        else -> e
    }
}
```

`containedPost()` (not `containedNote()` — corrected) lives at
`quartz/.../nip18Reposts/RepostEvent.kt:87` and
`.../GenericRepostEvent.kt:87`. Both already wrap `fromJson(content)` in
try/catch returning null.

### Settings interface

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/hashtags/HashtagSpamSettings.kt`:

```kotlin
@Stable
interface HashtagSpamSettings {
    val enabled: StateFlow<Boolean>
    val threshold: StateFlow<Int>
    fun setEnabled(enabled: Boolean)
    fun setThreshold(threshold: Int)
}
```

`LocalHashtagSpamSettings`:

```kotlin
val LocalHashtagSpamSettings: ProvidableCompositionLocal<HashtagSpamSettings> =
    compositionLocalOf { error("LocalHashtagSpamSettings not provided") }
```

No `NoOpHashtagSpamSettings` — caller (`Main.kt` `App()`) is always required
to provide the real impl. Compose idiom for "must provide" via `error { ... }`
default, mirroring how Material3 enforces theme provision.

### Persistence (commons/jvmMain — shared with `amy`)

`commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/hashtags/PreferencesHashtagSpamSettings.kt`:

```kotlin
class PreferencesHashtagSpamSettings(
    prefs: Preferences = Preferences.userRoot().node("com/vitorpamplona/amethyst/filters"),
) : HashtagSpamSettings {
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    private val _threshold = MutableStateFlow(prefs.getInt(KEY_THRESHOLD, 5))
    override val enabled = _enabled.asStateFlow()
    override val threshold = _threshold.asStateFlow()

    override fun setEnabled(v: Boolean) {
        _enabled.value = v
        prefs.putBoolean(KEY_ENABLED, v)
    }

    override fun setThreshold(v: Int) {
        val clamped = v.coerceIn(MIN, MAX)
        _threshold.value = clamped
        prefs.putInt(KEY_THRESHOLD, clamped)
    }

    companion object {
        const val KEY_ENABLED = "hashtag_spam_enabled"
        const val KEY_THRESHOLD = "hashtag_spam_threshold"
        const val MIN = 1
        const val MAX = 20
    }
}
```

- Shared `java.util.prefs` node `com/vitorpamplona/amethyst/filters` (not
  per-class) so `amy` constructing the same impl observes the same node.
- No `prefs.flush()` — `java.util.prefs` auto-flushes on JVM shutdown and
  periodically; explicit flush thrashes disk.
- Defaults: enabled = `true`, threshold = `5`. New installs and existing
  users (absent keys) both get the defaults. No migration code.

### Collapsed-note placeholder

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/CollapsedSpamNote.kt`:

```kotlin
@Composable
fun CollapsedSpamNote(
    authorDisplayName: String,
    authorAvatarUrl: String?,
    createdAtSeconds: Long,
    hashtagCount: Int,
    threshold: Int,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription =
                    "Hidden note from $authorDisplayName, $hashtagCount hashtags. Tap to reveal."
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(url = authorAvatarUrl, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(authorDisplayName, style = MaterialTheme.typography.labelLarge)
            Text(
                "Filtered: $hashtagCount hashtags · threshold $threshold",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(relativeTimeShort(createdAtSeconds), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onReveal) { Text("Reveal") }
    }
}
```

Parameters are **scalars only** — no `Note`, no `NoteDisplayData`, no
`Event`. This is the single shareable surface; Android, Desktop, and any
future front-end render it from their own data shape by mapping at the call
site.

Always visible: avatar, display name, timestamp, hashtag count, reason
chip, Reveal button. Hidden: body, media, link previews. Convergent with
Mastodon/Tusky/Bluesky CW-card conventions (research: best-practices).

### Caller-side integration

The check happens **outside** `NoteCard` because Desktop's `NoteCard` takes
a `NoteDisplayData` that doesn't carry the raw `Note`/`Event` we need. Each
caller resolves `Note → displayed event → spam decision` and renders
`CollapsedSpamNote` or `NoteCard`.

`desktopApp/.../ui/FeedScreen.kt` (representative):

```kotlin
@Composable
fun FeedScreen(notes: List<Note>, account: IAccount) {
    val settings = LocalHashtagSpamSettings.current
    val enabled by settings.enabled.collectAsState()
    val threshold by settings.threshold.collectAsState()
    val exemptKeys = remember(account) {
        // Stable: Kind3Follows.authors is @Immutable, selfPubkey is a String
        account.followingKeySet() + account.userProfile().pubkeyHex
    }

    LazyColumn {
        items(notes, key = { it.idHex }) { note ->
            val displayedEvent = note.displayedEvent()
            val isSpam = remember(note.idHex, displayedEvent, enabled, threshold, exemptKeys) {
                HashtagSpamCheck.isHashtagSpam(
                    displayedEvent = displayedEvent,
                    authorPubkey = displayedEvent?.pubKey,
                    enabled = enabled,
                    threshold = threshold,
                    exemptKeys = exemptKeys,
                )
            }
            var revealed by rememberSaveable(note.idHex) { mutableStateOf(false) }

            Box(Modifier.animateItem()) {                                  // smooth resize
                if (isSpam && !revealed) {
                    CollapsedSpamNote(
                        authorDisplayName = note.author?.bestDisplayName() ?: "",
                        authorAvatarUrl = note.author?.profilePicture(),
                        createdAtSeconds = displayedEvent?.createdAt ?: 0L,
                        hashtagCount = displayedEvent?.tags?.countHashtags() ?: 0,
                        threshold = threshold,
                        onReveal = { revealed = true },
                    )
                } else {
                    NoteCard(NoteDisplayData(note), /* … */)
                }
            }
        }
    }
}
```

Important specifics from research:

- `collectAsState()` is called **once at column scope**, not once per note —
  prevents 60–360 redundant collectors per visible viewport (perf-oracle).
- `key = { it.idHex }` on `items(...)` is required for `Modifier.animateItem()`
  to animate the size change correctly.
- `rememberSaveable(note.idHex)` survives `LazyColumn` recycling on scroll.
- Trade-off: the same spam note shown simultaneously in Home + Hashtag
  columns has independent reveal state per column. Acceptable v1.

#### Other caller sites that need the same pattern

1. **`QuotedNoteEmbed`** at `desktopApp/.../ui/note/NoteCard.kt:470-535` — when
   a note body contains `nostr:nevent…` and the rich-text renderer recurses
   into `NoteCard`. Patch this entry to apply the same check.
2. **Thread replies** in `desktopApp/.../ui/ThreadScreen.kt` — replies use
   `NoteCard` too. The **root** note auto-expands (caller passes
   `forceReveal = true`); replies use the normal collapse rule.
3. **Search results** at `desktopApp/.../ui/search/SearchResultsList.kt:156+`
   — same call-site change.
4. **Notifications-tab compact 56 dp card** at `NotificationsScreen.kt:271-366`
   does NOT funnel through `NoteCard` (it's a bespoke compact composable).
   Verified by Quartz/NoteCard-variant sweep. **Deferred to v2** — the
   notification's snippet text isn't a full note body so the impact is
   lower.

#### Thread root auto-expand

Thread screen owns its caller and simply passes `forceReveal = true` to
the root note's `revealed` state initialization, while replies keep
`rememberSaveable(note.idHex) { mutableStateOf(false) }`. No `ReplyContext`
plumbing through `NoteCard` (that didn't exist anyway — corrected from the
draft).

### Settings UI (Desktop)

`desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/settings/HashtagSpamSettingsSection.kt`:

```kotlin
@Composable
fun HashtagSpamSettingsSection(
    settings: HashtagSpamSettings,
    modifier: Modifier = Modifier,
) {
    val enabled by settings.enabled.collectAsState()
    val committed by settings.threshold.collectAsState()

    // Local slider state — decouples drag from StateFlow churn.
    // LaunchedEffect resyncs if a different surface changes the threshold.
    var live by remember { mutableFloatStateOf(committed.toFloat()) }
    LaunchedEffect(committed) { live = committed.toFloat() }

    Column(modifier.padding(16.dp)) {
        Text("Hashtag-spam filter", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = settings::setEnabled)
            Spacer(Modifier.width(8.dp))
            Text(if (enabled) "On" else "Off")
        }
        if (enabled) {
            Text("Hide notes with more than ${live.roundToInt()} hashtags",
                 style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = live,
                onValueChange = { live = it },                            // local only — no recompose storm
                onValueChangeFinished = { settings.setThreshold(live.roundToInt()) },
                valueRange = 1f..20f,
                steps = 18,
            )
            Text("Long-form articles and posts from people you follow are always shown.",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

Wired into `Main.kt` settings screen as a dedicated **Content Filters**
section (sibling to Wallet Connect, Media Server, Namecoin, Local Relay),
not under `RelaySettingsScreen`'s relay-specific entries. Section header
spans the screen so users searching "spam" or "hashtag" find it.

The settings impl is provided at App root:

```kotlin
// Main.kt App()
val hashtagSpamSettings = remember { PreferencesHashtagSpamSettings() }
CompositionLocalProvider(
    LocalHashtagSpamSettings provides hashtagSpamSettings,
    // existing CompositionLocals …
) {
    App(…)
}
```

## Technical Considerations

### Performance

- Quartz `hasMoreHashtagsThan` is O(n) over the note's tag array (n ≈ 5–30
  worst case) with short-circuit on total count before any hashset
  allocation. Sub-microsecond per note.
- `note.displayedEvent()` is a pointer chase via the precomputed
  `note.replyTo` for reposts — no JSON parse on the hot path. Falls back
  to `containedPost()` (which itself has try/catch + null return) only
  for the rare unconsumed-repost case.
- `isHashtagSpam` is `remember(...)`-memoized against
  `(noteId, displayedEvent, enabled, threshold, exemptKeys)`. The
  `exemptKeys` set is `remember(account)`-cached at column scope so
  references stay stable across recompositions.
- Settings StateFlows are `collectAsState`d **once per column**, not per
  note — prevents O(visible notes) flow collectors per deck.
- `Slider` uses local `Float` state; downstream collectors only see the
  committed `Int` on drag-end → zero feed recompositions while the user
  drags the thumb.
- `rememberSaveable(note.idHex)` for reveal flag survives LazyColumn
  recycling on scroll without any external state container.

### Stability annotations

- `HashtagSpamSettings` interface: `@Stable` — public observable surface
  (`StateFlow` instances) changes only via `setEnabled`/`setThreshold`,
  honest stability contract.
- `PreferencesHashtagSpamSettings` class: omit annotation — `@Stable` would
  be redundant on a class implementing a `@Stable` interface, and
  `@Immutable` is wrong (it holds `MutableStateFlow`s).
- `HashtagSpamCheck` is `object` — implicitly `@Immutable`.

### Threshold reactivity

`enabled` and `threshold` are exposed as `StateFlow` and read via
`collectAsState()` at column scope. Setting changes → StateFlow emit →
column recomposes → all visible cards re-evaluate `isSpam` and
re-collapse/re-reveal accordingly. Project pattern memory:
`?.collectAsState()?.value` does NOT work for tracking — use `by … .collectAsState()`.

### Follow-set reactivity

`account.followingKeySet()` returns
`kind3FollowList.flow.value.authors`. `Kind3Follows` is `@Immutable`, so
the returned `Set<HexKey>` reference is stable until the follow list
mutates upstream. Currently no `StateFlow<Set<HexKey>>` accessor exists —
follow/unfollow during a session won't reactively re-evaluate cards in
place. Acceptable for v1 (follow churn is low). A future refactor to expose
`kind3FollowList.flow` directly on `IAccount` closes the gap.

### Reposts and the displayed event

| `note.event` kind | `note.displayedEvent()` |
|-------------------|--------------------------|
| 6 (`RepostEvent`) | `replyTo.last().event` (precomputed) ?: `containedPost()` |
| 16 (`GenericRepostEvent`) | `replyTo.last().event` (precomputed) ?: `containedPost()` |
| anything else | `note.event` |

The wrapped event is **already materialised on `note.replyTo`** by
`DesktopLocalCache.consumeRepost()` — no JSON parsing on render.

### Inside threads

Thread screen owns the caller; for the **root** note it initialises
`revealed = true` (auto-expand on opt-in click-through). Replies use
the default collapsed/`rememberSaveable` path. No special threading types.

### Inside embedded/quoted notes

`QuotedNoteEmbed` (the entry that recurses into `NoteCard` for inline
`nostr:nevent…` references) gets the same check at its call site.
Independent `rememberSaveable` per quote location.

### Security / privacy

- Pure client-side: no network IO, no relay filter derivation, no telemetry.
- App-global persistence (not per-account) intentionally avoids leaking
  per-Nostr-identity behavioural fingerprints to anyone with filesystem
  access — the OS-account boundary is the trust boundary.
- Malformed inner repost events: `containedPost()` already returns null,
  `isHashtagSpam` returns false (does not collapse, does not throw).
- Censorship-resistance smell test: content is **collapsed, not dropped**;
  one click reveals; settings live in a top-level "Content Filters" section
  (not buried under relays).

## System-Wide Impact

### Interaction graph

```
PreferencesHashtagSpamSettings (java.util.prefs node)
    │
    ▼ CompositionLocalProvider in Main.kt App()
LocalHashtagSpamSettings
    │
    ▼ collectAsState() at column scope
(enabled, threshold) as scalars
    │
    ▼ per-note remember(...) at LazyColumn item
HashtagSpamCheck.isHashtagSpam(displayedEvent, …)
    │
    ▼ if true & !revealed
CollapsedSpamNote(scalars, onReveal)
    │
    └─ onReveal → rememberSaveable(note.idHex) flips → recomposes this row only
```

### Error & failure propagation

- `note.displayedEvent()`: fallback chain `replyTo → containedPost → null`.
  Null displayed event → `isHashtagSpam = false` → normal NoteCard renders.
  Never throws.
- `Preferences.put*` may throw `BackingStoreException`. Wrap in try/catch
  inside `setEnabled`/`setThreshold`; log and continue (in-memory state
  already updated; persistence is best-effort).
- Settings UI: `LocalHashtagSpamSettings` default `error("...")` only fires
  if App root forgot the provider. Caught at first launch, never in prod.

### State lifecycle risks

- Reveal state is `rememberSaveable` per LazyColumn item. Survives scroll
  recycling; lost on screen close (intentional).
- Settings keys absent on first launch → defaults (`true`, `5`). No
  partial-state risk; `java.util.prefs` is atomic per key.
- Slider local-Float state is column-instance scoped; lost on settings
  screen close. Re-derived from committed `Int` on next open.

### API surface parity

- **commons:** `HashtagSpamCheck` (pure), `HashtagSpamSettings` (interface),
  `Note.displayedEvent()` extension, `CollapsedSpamNote` (scalar
  composable), `LocalHashtagSpamSettings` (CompositionLocal),
  `PreferencesHashtagSpamSettings` (`jvmMain` impl, shared `java.util.prefs`
  node with `amy`).
- **desktopApp:** App-root provider, `HashtagSpamSettingsSection` Compose
  UI, caller-side check in `FeedScreen` / `QuotedNoteEmbed` / `ThreadScreen`
  / `SearchResultsList`.
- **amethyst (Android):** no changes v1. Future adoption is a single
  `AndroidHashtagSpamSettings` (DataStore-backed) + `LocalHashtagSpamSettings`
  provider + the same caller-side pattern. Commons does not leak Desktop
  types so the path is clean.
- **cli (amy):** day-1 picks up the shared `java.util.prefs` node — agents
  using `amy` see the same setting users configured in Desktop. v2 adds
  `amy filter hashtag-spam {get|set}` and `amy notes is-spam <ref>`
  subcommands; v2 also adds `amy notes feed --include-spam` (default
  respects setting).

### Integration test scenarios

1. **Slider live re-collapse.** Open a Home column with a known spammy
   visible (revealed). Drag threshold down past its tag count.
   `onValueChangeFinished` commits → all visible cards re-evaluate → that
   card collapses. No mid-drag visual jank.
2. **Repost spam (precomputed).** A kind:6 wrapping a 12-hashtag kind:1
   already consumed → `replyTo` materialised → check trips on inner →
   wrapper collapses.
3. **Repost spam (unconsumed).** Same scenario but the inner event hasn't
   been cached → fallback to `containedPost()` succeeds → same outcome.
4. **Followed-author exemption.** 15-hashtag note from a key in follow set
   → does NOT collapse.
5. **Self exemption.** 15-hashtag note from the active account → does NOT
   collapse for self.
6. **Long-form exemption.** kind:30023 with 12 topic tags → no collapse.
7. **Thread root auto-expand.** Tap a collapsed card → thread screen → root
   shows full content; a 12-hashtag reply in the thread stays collapsed.
8. **Embedded quote.** A note quotes a 15-hashtag note via `nostr:nevent…`
   → the inline embedded card renders as collapsed; revealing it does NOT
   reveal the same note in the parent feed column (independent
   `rememberSaveable` per call site).
9. **Settings persistence.** Toggle off, restart Desktop app → off-state
   restored from `java.util.prefs` node.
10. **amy parity.** From command line, write enabled=false to the shared
    prefs node via plain `java.util.prefs` API → relaunch Desktop →
    Desktop reads the same node → filter is off.
11. **Malformed inner repost.** `containedPost()` returns null on bad
    inner JSON → `isHashtagSpam` returns false → renders normal NoteCard
    of the wrapper (which itself has 0 hashtags). No crash.

## Acceptance Criteria

### Functional

- [x] `HashtagSpamCheck.isHashtagSpam` in
      `commons/commonMain/.../moderation/HashtagSpamCheck.kt`. Compiles for
      `:commons:compileKotlinJvm`. *(Note: filed under `moderation/` not
      `hashtags/` — `hashtags/` is the existing icon-only package.)*
- [x] `HashtagSpamSettings` (`@Stable` interface) in
      `commons/commonMain/.../moderation/HashtagSpamSettings.kt`.
- [x] `LocalHashtagSpamSettings` CompositionLocal with `error(...)`
      default. `LocalSpamExemptKeys` CompositionLocal added for the
      account-derived exempt set.
- [x] `PreferencesHashtagSpamSettings` JVM impl in `commons/jvmMain`
      bound to `Preferences.userRoot().node("com/vitorpamplona/amethyst/filters")`
      with keys `hashtag_spam_enabled` (default `true`) and
      `hashtag_spam_threshold` (default `5`, range 1–20).
- [x] `CollapsedSpamNote` composable in
      `commons/commonMain/.../ui/note/CollapsedSpamNote.kt` taking scalar
      params only (no `Note`, no `NoteDisplayData`, no `Event`); includes
      a11y `contentDescription`.
- [x] `Note.displayedEvent()` extension in
      `commons/commonMain/.../moderation/DisplayedEvent.kt` returning the
      wrapped event for kind 6 / 16 via `replyTo` (precomputed), falling
      back to `containedPost()` and finally null.
- [x] Desktop `FeedNoteCard` (covers FeedScreen + ThreadScreen +
      UserProfileScreen), `QuotedNoteEmbed`, `BookmarksScreen`, and
      `SearchResultsList` (5 sites) all branch on the spam check
      caller-side via the shared `SpamCheckedNoteRender` helper and render
      either `CollapsedSpamNote` or `NoteCard`.
- [x] `FeedNoteCard` exposes `forceReveal: Boolean = false`; ThreadScreen
      passes `forceReveal = true` for the root note. Replies inside the
      thread use default `rememberSaveable` collapsed.
- [x] `Main.kt` provides `LocalHashtagSpamSettings` at App root (always-on)
      and `LocalSpamExemptKeys` inside the LoggedIn branch (account-aware);
      adds a **Content Filters** settings section containing
      `HashtagSpamSettingsSection`.
- [x] `HashtagSpamSettingsSection`: Switch + Slider with local `Float`
      state, commit on `onValueChangeFinished`, live label, exemption
      footnote.
- [x] Notifications-tab compact card explicitly **out of scope v1** —
      documented in the deferred section + manual testing sheet T13.
- [x] `collectAsState` for settings called once inside
      `SpamCheckedNoteRender` (per visible card, but with cheap stable
      `StateFlow` references); slider settings UI also collects once.

### Non-functional

- [ ] No measurable frame-time regression in profiler runs of a 200-note
      column (manual smoke test pending — see T14 in testing sheet).
- [x] Spotless clean: `./gradlew spotlessApply` produces no diff.
- [x] Compiles cleanly: `./gradlew :commons:compileKotlinJvm
      :desktopApp:compileKotlin`.
- [x] No `Preferences.flush()` calls; rely on JVM auto-flush.

### Quality gates

- [x] Unit tests for `HashtagSpamCheck` (commons) — disabled, longform
      exempt, self exempt, follow exempt, under threshold, equal threshold,
      over threshold with duplicates only (helper short-circuits on
      uniques), over threshold with uniques, null displayedEvent, null
      authorPubkey. **10 tests, all green.**
- [x] Unit test for `Note.displayedEvent()` — repost with materialised
      `replyTo`, repost with null `replyTo` (fallback returns null on
      malformed JSON), non-repost (returns own event), null event.
      **4 tests, all green.**
- [x] Unit test for `PreferencesHashtagSpamSettings` — read default, read
      after set, threshold clamps to 1..20, both keys persist across
      instance recreation (test-specific node suffix). **5 tests, all
      green.**
- [x] Manual testing sheet at
      `desktopApp/plans/2026-06-29-hashtag-spam-filter-manual-testing-sheet.md`
      covering 16 integration scenarios.

## Success Metrics

- Subjective deck-feed cleanliness on default ON / threshold 5 (no
  collapsed legit follow content, visible spam collapsed).
- No frame drops > 16 ms during slider drag or feed scroll in a heavy
  column.
- Settings persist across app restarts and across the Desktop ↔ `amy`
  boundary.

## Dependencies & Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `replyTo` unset for very fresh reposts | low | Fall back to `containedPost()`; both already null-safe |
| Embedded `NoteCard` recursion misses a call site | medium | Audit every site that constructs `NoteCard(NoteDisplayData(note), …)` during impl; integration scenario #8 catches misses |
| Follow-set non-reactive during a session | low | Acceptable v1; track for a future `IAccount.followingFlow()` accessor |
| Default-on surprises power users | medium | Settings entry is in a top-level **Content Filters** section and easy to find; no toast (per simplicity review) |
| amy and Desktop diverge on settings node name | low | Use a constant `Preferences` node name in commons and reference it from both binaries |
| `Modifier.animateItem()` on an alpha API surface | low | Compose Foundation 1.7+ stable; project already uses it elsewhere — verify during impl |

## Out of Scope (deferred)

- User-curated hashtag allowlist (waiting for false-positive feedback).
- Per-column threshold override.
- Aggregated "N filtered today" badge.
- Account-synced setting via NIP-78/NIP-51.
- Android UI (commons logic ready; Android wires later).
- **`amy` subcommands** for the filter (`amy filter hashtag-spam …`,
  `amy notes is-spam`, `amy notes feed --include-spam`) — v2; v1 ships the
  shared `java.util.prefs` node so the data is already accessible.
- Banner when feed is 100 % collapsed.
- Cross-column shared reveal state (each column's reveal flag is
  independent in v1 — `rememberSaveable` per call site).
- Notifications-tab compact 56 dp card spam check (custom composable that
  doesn't go through `NoteCard`).
- Persistent "permanently revealed" notes across sessions.

## Sources & References

### Origin

- **Brainstorm:** `docs/brainstorms/2026-06-29-feat-hashtag-spam-filter-brainstorm.md`
  — collapse-with-reveal · global threshold · default 5 · longform +
  followed exemptions · `Preferences` persistence.

### Internal references

- `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/tags/hashtags/TagArrayExt.kt:41`
  — `hasMoreHashtagsThan(limit)` primitive.
- `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip18Reposts/RepostEvent.kt:87`
  + `.../GenericRepostEvent.kt:87` — `containedPost()` (null-safe).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/cache/DesktopLocalCache.kt:401-416`
  — `consumeRepost()` precomputes `note.replyTo` (use this, not JSON
  decode).
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/nip02FollowList/Kind3FollowListState.kt:100-104`
  — `@Immutable Kind3Follows.authors: Set<HexKey>` (stable ref).
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/IAccount.kt:101`
  — `followingKeySet(): Set<String>`.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/note/NoteCard.kt:96-112`
  — `NoteCard(note: NoteDisplayData, …)` signature (caller-side check).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/note/NoteCard.kt:470-535`
  — `QuotedNoteEmbed` recursion site (also needs caller-side check).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/search/SearchResultsList.kt:156+`
  — search-result call sites.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/DesktopPreferences.kt`
  — existing `java.util.prefs` pattern (referenced; settings live in
  commons/jvmMain instead).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/settings/LocalRelaySettingsScreen.kt`
  — Switch + Slider settings UI pattern.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/deck/LocalFeedProvider.kt:64-87`
  — existing `Local*` CompositionLocal pattern.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/AdditiveFeedFilter.kt`
  — why we don't extend `FeedFilter` (exclusionary contract).
- `commons/ARCHITECTURE.md` — module taxonomy / `commons/jvmMain` placement
  for JVM-only helpers.

### External references

- [Android Developers — Lazy lists and lazy grids](https://developer.android.com/develop/ui/compose/lists)
  — stable `key`, `Modifier.animateItem()`.
- [Android Developers — Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting).
- [JetBrains compose-multiplatform #4366 — Slider draggable state](https://github.com/JetBrains/compose-multiplatform/issues/4366)
  — why local `Float` + `onValueChangeFinished` is needed.
- [issuetracker #240599812 — AnimatedVisibility in LazyColumn](https://issuetracker.google.com/issues/240599812)
  — pitfalls with collapsed-item layout.
- [Mastodon moderating docs](https://docs.joinmastodon.org/user/moderating/)
  + [Bluesky moderation docs](https://docs.bsky.app/docs/advanced-guides/moderation)
  — convergent "always show author, gate body" convention.

### Skill references

- `feed-patterns` — confirmed `FeedFilter` is exclusionary; not used here.
- `compose-expert` — note-card composition + state hoisting.
- `compose-side-effects` — `LaunchedEffect(committed) { live = … }` to
  resync slider local state on upstream change.
- `compose-recomposition-performance` — hoist `collectAsState` to column
  scope; `rememberSaveable` survives recycling; local `Float` thumb.
- `compose-stability-diagnostics` — `@Stable` on the settings interface.
- `kotlin-flow-state-event-modeling` — StateFlow + collectAsState
  reactivity.
- `account-state` — `IAccount.followingKeySet()`; `Kind3Follows`
  immutability.
- `nostr-expert` — repost event unwrap via `replyTo`; `containedPost()`
  semantics.
- `amy-expert` — shared `java.util.prefs` node so `amy` reads the same
  setting users configure in Desktop.
- `kotlin-multiplatform` — `commons/jvmMain` placement for JVM-only
  persistence impls.

### Backlog reference

- `desktopApp/plans/_desktop-feature-backlog.md` priority item #1; WoT
  score on avatars is the next item.
