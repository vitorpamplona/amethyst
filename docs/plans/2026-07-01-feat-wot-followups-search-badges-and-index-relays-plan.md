---
title: WoT follow-ups — search-result badges + shared index relays
type: feat
status: active
date: 2026-07-01
origin: docs/plans/2026-07-01-feat-desktop-wot-score-plan.md
deepened: 2026-07-01
---

# WoT follow-ups — search-result badges + shared index relays

## Enhancement Summary

**Deepened on:** 2026-07-01 (same day as plan write).

**Agents used:** code-simplicity-reviewer, targeted repo verification sweep.

### Key corrections vs first draft

1. **Split into two PRs.** Item 1 (search badges) is mechanical and has
   zero coupling to Items 2+3. Ship it alone. Items 2 + 3 stay bundled
   because the UI (Item 3) is the write path for the persistence
   (Item 2) — reviewing them separately means reviewing dead code or a
   headless feature.
2. **App-global (not per-account) index-relay override.** First draft
   made this per-account to match `searchRelays` / `dmRelays`. But
   `searchRelays` / `dmRelays` are per-account because they're NIP-51 /
   NIP-17 identity-scoped semantics; index relays are a user preference
   about where profile-metadata lookups go, and users have a single
   preferred set regardless of which account they're logged into.
   App-global halves the API surface and matches user mental model.
3. **`PreferencesIndexRelays` in `commons/jvmMain/`, not extending
   `DesktopAccountRelays`.** Verification found `DesktopAccountRelays`
   uses `Preferences.userNodeForPackage(DesktopAccountRelays::class.java)`,
   which is a per-class node — **not visible to amy** running from a
   different classpath. To achieve the "one truth for Desktop and amy"
   goal, the shared node must be an explicit
   `Preferences.userRoot().node("com/vitorpamplona/amethyst/relays/index")`,
   which is exactly the pattern `PreferencesHashtagSpamSettings` uses.
   New small class mirrors that shape.
4. **Drop `WoTBadgedSearchCard`.** Only two call sites; the 6-line
   score computation inlines cleanly. New wrapper composable earns its
   keep at 3+ call sites, not 2.
5. **Drop `DefaultIndexRelays.kt` in commons.** Speculative — no
   Android caller. amy can duplicate the 4 URLs (they change ~never)
   or read a single constant from a shared location. Extracting to
   commons is architectural neatness without a consumer.
6. **`DesktopRelayCategories.indexRelays` uses `override ?: default`
   only.** Not the full combine used by `searchRelays` (which
   intersects with NIP-65 discovery). Index relays are a curated user
   choice, not a "what's actually reachable right now" derived set. No
   `debounce` / `stateIn` combine needed — a straight-through StateFlow
   from the Preferences read is enough.
7. **Drop "Reset to defaults" button in Item 3.** Removing all relays
   from the UI already falls back to `DefaultRelays.RELAYS`. Delete-all
   IS the reset.
8. **Drop integration scenarios 2 and 6.** #2 (badge respects
   exemptions) is covered by existing `WoTBadgedAvatar` tests — same
   code path. #6 (empty override falls back) is a single unit test on
   `PreferencesIndexRelays`, not a manual scenario.
9. **`RelaySettingsScreen` current content** was mischaracterised — it
   already has 6 sections (Wallet Connect, Media Server, Image
   Compression, Tor, Namecoin, Local Relay, Content Filters). Index
   Relays fits between Local Relay and Content Filters (both have
   dividers).
10. **Adopt-not-in-this-PR discovery: `commons/AmethystDefaults.kt`
    already has `DefaultIndexerRelayList`** (Purple Pages, Coracle,
    etc). Desktop today uses the wrong list (`DefaultRelays.RELAYS` =
    general-purpose relays) for its index REQs. That's a real
    behavioural bug worth a separate ticket — not this one — because
    changing default index relays is a user-visible behaviour shift and
    deserves its own review.

---

## Overview

Three small follow-ups to the just-shipped Web-of-Trust score feature
(branch `feat/desktop-wot-score`, closed for manual testing):

1. **Badges on search-result person cards.** The main NoteCard header
   already renders `WoTBadgedAvatar`, but the Search screen's person
   picker uses a different composable (`UserSearchCard`) that doesn't
   currently accept a badge.
2. **Unify amy `wot sync` with Desktop on the same relay set.** Desktop
   currently uses a hard-coded `DefaultRelays.RELAYS` list as its
   `indexRelays`; amy uses whatever the user's NIP-65 outbox/inbox lists
   contain. When the two disagree, `amy wot get` after `amy wot sync`
   returns a different score than the Desktop UI would compute.
3. **Add an Index Relays section to the Relays settings screen** so
   users can customise which relays back both surfaces from one place.

**Shipping plan:** two PRs.

- **PR A — Search badges (Item 1).** ~40 LOC, one commons param
  addition, two Desktop call-site inline changes. Independent of the
  other work. Ships first.
- **PR B — Shared index relays (Items 2 + 3).** Introduces a small
  Preferences-backed class in `commons/jvmMain/`, wires the coordinator
  to read from it, adds a settings-screen section, and updates
  `amy wot sync` to read the same node. ~300 LOC. Ships second.

## Problem Statement

Three concrete regressions/gaps from the manual-testing pass of the WoT
PR:

- **Item 1.** When searching for a person in the Desktop search screen,
  their result card is a stranger 90% of the time (that's the point of
  searching), but there's no trust cue on the card. Users who find WoT
  badges useful on feed avatars want the same signal here.
- **Item 2.** amy's `wot sync` uses `ctx.outboxRelays()` (NIP-65 write
  list) with a fall-back to inbox. Those are legitimate relays for
  publishing / receiving events, but they are *not* what Desktop uses
  to fetch profile metadata and follow lists — Desktop hits a
  hard-coded `indexRelays` set (nos.lol, nostr.wine,
  relay.noswhere.com, relay.primal.net today). Result: `amy wot get`
  after a fresh `amy wot sync` can produce a score that lags or
  diverges from the Desktop UI for the same account.
- **Item 3.** The Relays settings screen already contains six
  sections; there's no UI to inspect or change which relays are
  considered "index relays" — the values live only in the hard-coded
  default list in `RelayStatus.kt`.

## Proposed Solution

### PR A — Item 1: Badge slot on `UserSearchCard`

`UserSearchCard` in
`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/components/UserSearchCard.kt`
gets an optional `badge` slot that forwards to its embedded
`UserAvatar` (which already has the slot from the WoT PR):

```kotlin
@Composable
fun UserSearchCard(
    user: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable (BoxScope.() -> Unit)? = null,
) {
    // Existing layout, unchanged, except:
    UserAvatar(
        userHex = user.pubkeyHex,
        pictureUrl = user.profilePicture(),
        size = 40.dp,
        contentDescription = stringResource(Res.string.accessibility_user_avatar),
        badge = badge,
    )
    // …rest of the Row unchanged
}
```

Backward compatible — default `null` means no visual change for callers
that don't opt in. The layout impact is zero: `UserAvatar` handles the
badge's `Box` overlay itself; the badge lives on the avatar's bottom-
right corner, and the `ArrowForward` icon at the row's trailing edge
doesn't collide with it.

Two Desktop call sites in
`desktopApp/.../ui/search/SearchResultsList.kt:116,126` inline the score
computation directly at the call:

```kotlin
val service = LocalWoTService.current
val ready = LocalWoTReady.current
val exempt = LocalSpamExemptKeys.current
val score = if (service != null && ready && user.pubkeyHex !in exempt) {
    service.scores[user.pubkeyHex] ?: 0
} else 0

UserSearchCard(
    user = user,
    onClick = { … },
    badge = if (score > 0) {
        { WoTBadge(count = score, modifier = Modifier.align(Alignment.BottomEnd)) }
    } else null,
)
```

The two sites are 4 lines apart; a small local `remember` block above
them can factor the read if we want (optional micro-cleanup — not
required).

**Not migrated in PR A:**

- `desktopApp/.../ui/chats/NewDmDialog.kt` (three sites) — the DM
  recipient picker. Same rationale as before: when picking a DM
  recipient you're already committing to messaging that person; a
  trust badge is more noise than signal. Add later if testing calls
  for it.

### PR B — Item 2: Shared `indexRelays` between Desktop and amy

#### Persist via `java.util.prefs`, node shared with amy

`Preferences.userRoot().node("com/vitorpamplona/amethyst/relays/index")`.
Same JVM-user-wide `java.util.prefs` trick the hashtag-spam PR (#3431)
uses — Desktop and amy running as the same OS user see the same node.

**Not stored per-account.** Users have a single preferred set of index
relays regardless of which account is logged in. Halves the API
surface and matches user intuition. If a user with two accounts
genuinely needs separate index relays per account, we add per-account
overlay later on demand — YAGNI now.

**Not persisted via `DesktopAccountRelays`.** That class uses
`Preferences.userNodeForPackage(DesktopAccountRelays::class.java)`,
which resolves to a per-class node that `cli/` running from a different
classpath **would not see**. Extending it would give us Desktop-local
config with no amy visibility — the opposite of what we want.

#### New shared class

`commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/relays/index/PreferencesIndexRelays.kt`
(new file, mirrors `PreferencesHashtagSpamSettings` shape):

```kotlin
class PreferencesIndexRelays(
    private val prefs: Preferences =
        Preferences.userRoot().node(NODE_NAME),
) {
    private val _relays =
        MutableStateFlow(parse(prefs.get(KEY_URLS, "")))
    val relays: StateFlow<Set<NormalizedRelayUrl>> = _relays.asStateFlow()

    fun setRelays(new: Set<NormalizedRelayUrl>) {
        _relays.value = new
        prefs.put(KEY_URLS, new.joinToString(",") { it.url })
    }

    /** Resolves the effective set — user override if non-empty, else defaults. */
    fun effective(): Set<NormalizedRelayUrl> =
        _relays.value.ifEmpty { DEFAULT_INDEX_RELAYS }

    companion object {
        const val NODE_NAME = "com/vitorpamplona/amethyst/relays/index"
        const val KEY_URLS = "urls"

        /**
         * Byte-for-byte identical to `DefaultRelays.RELAYS` at
         * `desktopApp/.../network/RelayStatus.kt`. Preserves current
         * behaviour for users who never open the settings UI.
         *
         * Note: `commons/AmethystDefaults.kt` also has
         * `DefaultIndexerRelayList` (Purple Pages, Coracle, …) which
         * is more purpose-built. Adopting it is a separate ticket —
         * see Out of Scope.
         */
        val DEFAULT_INDEX_RELAYS: Set<NormalizedRelayUrl> = setOf(
            "wss://nos.lol",
            "wss://nostr.wine",
            "wss://relay.noswhere.com",
            "wss://relay.primal.net",
        ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

        private fun parse(csv: String): Set<NormalizedRelayUrl> =
            csv.split(",")
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()
    }
}
```

CSV serialisation matches what `DesktopAccountRelays` uses for its
categories (`prefs.put(key, relays.joinToString(",") { it.url })`) — no
JSON, no `Serializable`, no dependencies beyond `RelayUrlNormalizer`.

#### Desktop wiring

Add `indexRelays: StateFlow<Set<NormalizedRelayUrl>>` to
`DesktopRelayCategories`, backed by the new class. Simple straight-
through, no combine:

```kotlin
class DesktopRelayCategories(
    // existing params
    private val indexRelaysStore: PreferencesIndexRelays,
) {
    // existing categories…

    val indexRelays: StateFlow<Set<NormalizedRelayUrl>> =
        indexRelaysStore.relays
            .map { it.ifEmpty { PreferencesIndexRelays.DEFAULT_INDEX_RELAYS } }
            .stateIn(scope, SharingStarted.Eagerly, indexRelaysStore.effective())

    fun setIndexRelays(new: Set<NormalizedRelayUrl>) = indexRelaysStore.setRelays(new)
}
```

`Main.kt` — the constructor at
`desktopApp/.../Main.kt:847-859` swaps the hard-coded literal for the
current effective set:

```kotlin
// before:
indexRelays = DefaultRelays.RELAYS.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet(),

// after:
indexRelays = indexRelaysStore.effective(),
```

`indexRelaysStore` is instantiated once at App() root (before the
coordinator) and provided into `DesktopRelayCategories`. UI reads from
`LocalRelayCategories.current.indexRelays`.

**Changes take effect on next relaunch.** Documented in the settings
section's help text. The existing coordinator has no re-target API for
`indexRelays`; teaching it one is out of scope. Rationale: index-relay
churn is expected to be rare, and users who edit the list generally
expect to restart anyway.

#### amy wiring

New helper on `cli/.../Context.kt`:

```kotlin
fun indexRelays(): Set<NormalizedRelayUrl> {
    val prefs = Preferences.userRoot().node("com/vitorpamplona/amethyst/relays/index")
    val csv = prefs.get("urls", "")
    val user = csv.split(",")
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
        .toSet()
    return user.ifEmpty {
        // Same defaults as PreferencesIndexRelays.DEFAULT_INDEX_RELAYS
        // Duplicated here (4 URLs) — they change ~never.
        setOf(
            "wss://nos.lol", "wss://nostr.wine",
            "wss://relay.noswhere.com", "wss://relay.primal.net",
        ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
    }
}
```

`WotCommand.sync` swaps:

```kotlin
// before:
val relays = ctx.outboxRelays().ifEmpty { ctx.inboxRelays() }
// after:
val relays = ctx.indexRelays()
```

The 4-URL duplication is fine per the simplicity review — the list
changes ~never; a single shared commons constant would be architectural
neatness with no material win. Adding a whole
`commons/defaults/DefaultIndexRelays.kt` for a 4-line constant fails
YAGNI on a plan we're specifically told to keep small.

#### Optional: `amy relay index …` verbs — deferred

v1 configuration lives in the Desktop settings section. If someone
running amy headless wants to seed the Preferences node, they can do
so with a five-line JVM one-liner:

```
java -cp … -e 'Preferences.userRoot().node("com/vitorpamplona/amethyst/relays/index").put("urls","wss://foo,wss://bar")'
```

CLI verbs are a follow-up ticket if demand appears.

### PR B — Item 3: Index Relays section in `RelaySettingsScreen`

Insert a new section in `RelaySettingsScreen`
(`desktopApp/.../Main.kt` line 1797 onward). Current sections in order:

1. Wallet Connect (NWC)
2. Media Server Settings
3. Image Compression Settings
4. Tor Settings
5. Namecoin Settings
6. Local Relay (conditional)
7. Content Filters (hashtag-spam)

Insert **between Local Relay and Content Filters** — both already have
a `HorizontalDivider` around them.

Section renders:

- Title: "Index Relays"
- One-line explainer: "Used to fetch profile metadata and follow lists
  (Web-of-Trust). Changes take effect on next relaunch."
- `LazyColumn` of `Text(relay.url) + IconButton(Icons.Default.Close, onClick = onRemove)` — 30 LOC ballpark.
- Add-row: `OutlinedTextField + Button("Add")`. Normalises input via
  `RelayUrlNormalizer.normalizeOrNull`; ignores nulls silently (or
  surfaces "invalid relay URL" if trivial).
- No "Reset to defaults" button — removing all entries falls back to
  defaults automatically (delete-all is the reset).

Reads:

```kotlin
val indexRelays by LocalRelayCategories.current.indexRelays.collectAsState()
val categories = LocalRelayCategories.current
// then in add/remove handlers:
categories.setIndexRelays(indexRelays + newUrl)
categories.setIndexRelays(indexRelays - existingUrl)
```

## Technical Considerations

### Recomposition + reactivity (Item 1)

Inlining the score computation at each `UserSearchCard` call still gets
per-key snapshot tracking — `service.scores[pubkey]` is a
`SnapshotStateMap` read that Compose tracks per-key. Only the row for
the changed pubkey recomposes when its score updates. Identical
behaviour to what we shipped in `WoTBadgedAvatar`; the wrapper
composable would have added a subscriber node with no gain.

### Live re-targeting of the coordinator (Item 2)

Existing `DesktopRelaySubscriptionsCoordinator` reads `indexRelays`
once at construction and holds it. Teaching it to swap
`indexRelays` mid-flight is a real refactor (in-flight subscription
state, cross-EOSE semantics). Ship "changes take effect on next
relaunch" for v1; add live re-targeting in a follow-up if users notice.

### Preferences node identity across Desktop and amy (Item 2)

Both processes use
`Preferences.userRoot().node("com/vitorpamplona/amethyst/relays/index")`.
Because `java.util.prefs.Preferences` is JVM-user-scoped
(per OS user, per prefs backend — plist on macOS, dconf on Linux,
registry on Windows), both processes end up looking at the same
physical store. `PreferencesHashtagSpamSettings` already relies on this
guarantee in shipped code.

### CSV vs JSON serialisation (Item 2)

CSV (`joinToString(",") { it.url }`) matches what `DesktopAccountRelays`
does for its categories. No dependency on Jackson or Serialisation at
the storage boundary. Trade-off: URLs cannot contain commas (they
can't per RFC anyway — commas are reserved). We normalise through
`RelayUrlNormalizer.normalizeOrNull` at both write time (in
`setRelays`) and read time (in `parse` / `Context.indexRelays()`), so
persisted CSV never contains an invalid URL.

### Default list ergonomics (deferred)

Verification surfaced a real bug: `commons/AmethystDefaults.kt`
already contains `DefaultIndexerRelayList` (Purple Pages, Coracle,
etc.) — a purpose-built index-relay set — but Desktop currently uses
`DefaultRelays.RELAYS` (nos.lol, nostr.wine, relay.noswhere.com,
relay.primal.net), which are general-purpose. That default mismatch is
a real behaviour improvement to be made, but it's a user-visible
behavioural change that deserves its own PR + review. **This plan
preserves byte-parity with today's default** and flags the improvement
in Out of Scope.

## System-Wide Impact

### Interaction graph

```
PR A (Item 1):
  User opens Search column → types query
    → SearchResultsList renders LazyColumn of user results
    → Each result inlines: read LocalWoTService.scores, gate on ready/exempt
      → pass a WoTBadge lambda to UserSearchCard(badge=...)
        → UserSearchCard forwards to UserAvatar(badge=...)
          → UserAvatar renders Box overlay with WoTBadge chip

PR B (Items 2+3):
  User opens Relays settings → Index Relays section
    → List rendered from LocalRelayCategories.indexRelays
    → User adds / removes a relay
      → categories.setIndexRelays(newSet)
        → indexRelaysStore.setRelays(newSet)
          → prefs.put("urls", csv) at
            com/vitorpamplona/amethyst/relays/index
          → indexRelays StateFlow emits new value

  amy wot sync (later):
    → ctx.indexRelays() reads the same prefs node
    → identical relay set — Desktop and amy compute the same score

  Desktop app next launch:
    → indexRelaysStore.effective() returns user set (or defaults)
    → coordinator constructed with that set
```

### Error & failure propagation

- **Empty override set** (user removed all entries): fall back to
  defaults at both `indexRelaysStore.effective()` and
  `ctx.indexRelays()`. Never allow an empty batch REQ — WoT would
  silently break.
- **Malformed URL entry** (e.g. old persisted CSV with a URL that no
  longer normalises): filter through
  `RelayUrlNormalizer.normalizeOrNull` at read time, drop nulls.
- **Preferences read failure** (`BackingStoreException`): treat as
  "unset → use defaults". Log at debug, do not surface to the user.

### State lifecycle risks

- **Cross-account leak:** App-global preference, no per-account
  identity in the key — by design.
- **Coordinator using stale set after user changes indexRelays:** Yes,
  in v1 the coordinator keeps its constructor-time set until relaunch.
  Documented in the UI. Not a data-integrity risk — just a UX quirk.

### API surface parity

- **PR A:** `UserSearchCard` badge slot — commonMain, backward-
  compatible. Android call sites (if any exist post-merge) unchanged;
  badge slot stays null.
- **PR B, new:** `PreferencesIndexRelays` class in `commons/jvmMain/`.
- **PR B, modified:** `DesktopRelayCategories` gains an `indexRelays`
  StateFlow + `setIndexRelays(...)`. `Main.kt` coordinator
  construction. `Context.kt` gains `indexRelays()`.
  `WotCommand.sync` swaps its relay source. `RelaySettingsScreen`
  gains an "Index Relays" section.
- **Nothing** in `amethyst/` (Android) is touched — this is Desktop +
  amy only.

### Integration test scenarios

1. **Search badge shows.** Load a search result for a stranger scored
   ≥ 1 in the WoT map — the badge renders bottom-right of the avatar.
2. **Index Relays default state.** Fresh install → open Relays
   settings → Index Relays section lists the four
   `DEFAULT_INDEX_RELAYS` entries as read-only (or marked "(default)").
3. **Index Relays override persists.** Add a new relay → close the
   app → relaunch → new relay still present. Remove one → close →
   relaunch → still gone.
4. **amy sees the same override.** After the Desktop override above,
   `amy wot sync` uses the new relay set. Verify by observing which
   relays receive the kind-3 REQ (packet capture or a debug print
   inside `WotCommand.sync`).
5. **Bad URL doesn't crash.** Manually plant an invalid entry in the
   Preferences node → app restart → invalid entries filtered out, UI
   shows only valid entries.

## Acceptance Criteria

### PR A — Functional (Item 1)

- [ ] `UserSearchCard` in commons accepts optional
      `badge: @Composable (BoxScope.() -> Unit)? = null` and forwards
      it to its `UserAvatar` call.
- [ ] Both call sites in
      `desktopApp/.../ui/search/SearchResultsList.kt` (currently at
      lines ~116 and ~126) inline the WoT-score computation and pass a
      `WoTBadge` lambda when score > 0 and pubkey not in
      `LocalSpamExemptKeys`.
- [ ] `NewDmDialog` call sites remain unchanged.

### PR B — Functional (Items 2+3)

- [ ] `PreferencesIndexRelays` created at
      `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/relays/index/PreferencesIndexRelays.kt`.
      Persists to `Preferences.userRoot().node("com/vitorpamplona/amethyst/relays/index")`
      key `urls` as CSV. Exposes
      `relays: StateFlow<Set<NormalizedRelayUrl>>`,
      `setRelays(new)`, `effective(): Set<NormalizedRelayUrl>`, and
      `DEFAULT_INDEX_RELAYS` constant that matches `DefaultRelays.RELAYS`
      byte-for-byte.
- [ ] `DesktopRelayCategories.indexRelays: StateFlow<Set<NormalizedRelayUrl>>`
      exposed — straight-through from `PreferencesIndexRelays.relays`,
      empty falls back to defaults. `setIndexRelays(new)` delegates.
- [ ] `Main.kt:847-859` constructs
      `DesktopRelaySubscriptionsCoordinator` with
      `indexRelays = indexRelaysStore.effective()` instead of the
      hard-coded `DefaultRelays.RELAYS.mapNotNull { … }.toSet()`.
      Behaviour on fresh install identical to today.
- [ ] `cli/.../Context.kt` gains `indexRelays(): Set<NormalizedRelayUrl>`
      reading the same Preferences node, falling back to the same 4
      defaults inline.
- [ ] `WotCommand.sync` uses `ctx.indexRelays()` instead of
      `outboxRelays()/inboxRelays()`.
- [ ] `RelaySettingsScreen` has an "Index Relays" section between
      Local Relay and Content Filters, with:
      - Title + one-line explainer including "Changes take effect on
        next relaunch."
      - List of current relays with per-row remove button.
      - Add-row: URL input + Add button, normalises via
        `RelayUrlNormalizer.normalizeOrNull`, silently drops nulls.
      - No "Reset to defaults" button (remove-all is the reset).

### Non-functional (both PRs)

- [ ] `./gradlew spotlessApply` — no diff.
- [ ] `./gradlew :commons:compileKotlinJvm :desktopApp:compileKotlin
      :cli:compileKotlin` — clean.
- [ ] `./gradlew test` — full suite passes.
- [ ] No new `Preferences` writes on any render path — only on
      settings-screen mutations.

### Quality gates

- [ ] **PR A:** manual smoke — open Search, type a query, verify
      badges appear on results scored ≥ 1 in the WoT map; none on
      follows/self.
- [ ] **PR B:** unit test for `PreferencesIndexRelays` round-trip
      (write set → new instance → same set out).
- [ ] **PR B:** unit test for `PreferencesIndexRelays.effective()`
      fallback when Preferences is unset.
- [ ] **PR B:** unit test for `ctx.indexRelays()` fallback behaviour
      when Preferences is unset.
- [ ] **PR B:** three new manual scenarios added to the WoT testing
      sheet — search-badge visibility, Preferences override
      persistence across restart, amy sync uses override (packet
      capture or debug log).

## Success Metrics

- Search results have the same at-a-glance trust cue as feed cards.
- `amy wot get <target>` after `amy wot sync` returns a score identical
  to the Desktop UI within ~2 s of the same relay set having been
  configured.
- Users who add / remove index relays in the settings UI see their
  change reflected on next relaunch, verified via a debug log line.

## Dependencies & Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Coordinator snapshot of `indexRelays` leaks stale set until relaunch | high (accepted v1) | Document as "changes on relaunch" in the UI; follow-up ticket for live re-targeting. |
| Empty override silently kills WoT | medium | Fallback-to-defaults guard at *both* `indexRelaysStore.effective()` and `ctx.indexRelays()`. Unit-tested. |
| CSV serialisation confuses a user who hand-edits the prefs file | low | Documented as internal; users are expected to use the UI. Hand-edit path stays functional as long as URLs don't contain commas (they can't per RFC). |
| Two Desktop and cli defaults drift out of sync (4 URLs duplicated in two places) | low | Comment in both files pointing to each other. If the list ever needs to change, both places must update. Realistically the list changes ~never. |
| `commons/AmethystDefaults.DefaultIndexerRelayList` continues to be the "correct" index-relay set while we're shipping the "general-purpose" defaults | ok (deferred) | Out-of-scope. Separate ticket to adopt as default. |

## Out of Scope (deferred)

- **`amy relay index add / remove / list` verbs.** Defer until there's
  demand from headless workflows.
- **Live re-targeting of index-relay subscriptions** without app
  relaunch. Separate coordinator refactor.
- **NIP-51 kind 30002 based index-relay list** for cross-Nostr-client
  portability.
- **DM-recipient-picker badges** in `NewDmDialog`. Ship only if manual
  testing complains.
- **Adopting `commons/AmethystDefaults.DefaultIndexerRelayList` as the
  Desktop / amy default.** Real improvement, but a user-visible
  behavioural change. Standalone ticket + review.
- **Per-account index-relay overrides.** YAGNI now — single-user
  preference dominates. Add later if demand shows up.

## Sources & References

### Origin

- **WoT PR plan:** `docs/plans/2026-07-01-feat-desktop-wot-score-plan.md`
- **Manual testing sheet:**
  `desktopApp/plans/2026-07-01-wot-score-manual-testing-sheet.md`

### Internal references

- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/components/UserSearchCard.kt:51-108`
  — target for badge slot (Item 1).
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/components/UserAvatar.kt:82`
  — badge slot already exists here (from the WoT PR).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/search/SearchResultsList.kt:116,126`
  — the two person-result call sites to migrate.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt:847-859`
  — where the hard-coded `indexRelays` is passed to the coordinator.
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/network/RelayStatus.kt:40-47`
  — `DefaultRelays.RELAYS` (byte-parity target for
  `DEFAULT_INDEX_RELAYS`).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/model/DesktopRelayCategories.kt:80-89`
  — `searchRelays` pattern (reference; index relays uses a *simpler*
  shape).
- `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/model/DesktopAccountRelays.kt`
  — per-class Preferences node pattern **we're deliberately not
  reusing** (would not be visible to amy).
- `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/moderation/PreferencesHashtagSpamSettings.kt`
  — pattern for shared Preferences node used across Desktop + amy;
  `PreferencesIndexRelays` mirrors this shape.
- `cli/src/main/kotlin/com/vitorpamplona/amethyst/cli/Context.kt:329-362`
  — where `outboxRelays()` / `inboxRelays()` live.
- `cli/src/main/kotlin/com/vitorpamplona/amethyst/cli/commands/WotCommand.kt`
  — swap `sync` to `ctx.indexRelays()`.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/AmethystDefaults.kt:62-63`
  — `DefaultIndexerRelayList` (Purple Pages, Coracle …) — flagged as
  future default adoption, **not touched** in this plan.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/assemblers/FeedMetadataCoordinator.kt`
  — `indexRelays` constructor param (already exists, no change needed).

### Skill references

- `relay-client` — DesktopRelayCategories composition + StateFlow
  patterns.
- `compose-expert` — CompositionLocal readers + badge slot forwarding.
- `amy-expert` — Context helper pattern (`outboxRelays()` etc.), CLI
  verb shape, shared JVM `Preferences` node semantics.
- `kotlin-flow-state-event-modeling` — StateFlow<Set<…>> straight-
  through vs combine semantics.

### Related work

- WoT PR branch: `feat/desktop-wot-score` (closed for manual testing).
- Hashtag-spam PR: https://github.com/vitorpamplona/amethyst/pull/3431
  (merged) — the `java.util.prefs` shared-node pattern
  `PreferencesIndexRelays` mirrors.
- Feature backlog:
  `desktopApp/plans/_desktop-feature-backlog.md` item #2 (parent WoT
  feature).
