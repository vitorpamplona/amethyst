---
title: "feat(polls): extended poll results page (counts + who voted for what)"
type: feature
status: proposed
date: 2026-08-03
owner: commons
consumers: amethyst, desktopApp
---

# feat(polls): extended poll results page

> **Status:** proposal. Nothing implemented. This document surveys what exists,
> names four correctness bugs the current tally has, and specifies a shared
> results surface for Android and Desktop. _Authored 2026-08-03._

## 1. Motivation

Today a NIP-88 poll (kind 1068) renders its results **inline in the feed**, and
the results are deliberately tiny: a progress bar, a percentage, and up to four
voter avatars per option (`Poll.kt:571 UserGallery` shows `take(4)` then `+N`).
There is no way to answer the two questions people actually ask about a poll:

1. **How many votes did each answer get?** — the card shows only a percentage,
   never a count, and the percentage denominator is wrong for multi-choice
   polls (§3.1).
2. **Who voted for what?** — Android gives you at most four avatars and no way
   to see the fifth. Desktop already went further (`DesktopPollCard.kt:576
   VoterListPopup`), which proves the demand but leaves the two platforms
   divergent and the logic unshared.

A dedicated results page also unlocks the things a feed card can never host:
filtering the voter list to people you follow, searching it, sorting it, and
being honest about *how complete the tally is* — which today it silently is not
(§3.4).

## 2. What exists today

| Layer | Component | File |
|---|---|---|
| Protocol | `PollEvent` (1068): `options()`, `relays()`, `pollType()`, `endsAt()`, `hasEnded()` | `quartz/…/nip88Polls/poll/PollEvent.kt:63` |
| Protocol | `PollResponseEvent` (1018): `responses()`, `poll()` | `quartz/…/nip88Polls/response/PollResponseEvent.kt:62` |
| Tally | `PollResponsesCache` + `ResponseTally` + `TallyResults` — the single source of truth, hung off `Note.pollState()` | `commons/…/model/nip88Polls/PollResponsesCache.kt` |
| Attach | `Note.poll` / `pollState()` | `commons/…/model/Note.kt:178` |
| Ingest | `consume(PollResponseEvent)` → `getOrCreateNote(pollId).pollState().addResponse(...)` | `amethyst/…/model/LocalCache.kt:2936` |
| Write | `Account.pollRespond(event, responses)` | `amethyst/…/model/Account.kt:2018` |
| UI (Android) | `RenderPoll` / `RenderPollCard` / `RenderResults` / `RenderClosedItem` / `UserGallery`, view-state `PollCard` + `PollItemCard` | `amethyst/…/ui/note/types/Poll.kt` |
| UI (Desktop) | `DesktopPollCard`, `PollResultRow`, `VoterGallery`, `VoterListPopup` | `desktopApp/…/ui/note/DesktopPollCard.kt:95` |
| Feed | `Route.Polls` tab + `PollsFeedFilter` / `OpenPollsFeedFilter` / `ClosedPollsFeedFilter` | `amethyst/…/ui/screen/loggedIn/polls/` |
| Zap polls | `ZapPollEvent` (6969) + `PollNoteViewModel` (amount-weighted, zap-receipt tally) | `quartz/…/experimental/zapPolls/`, `amethyst/…/ui/note/ZapPollNoteViewModel.kt` |

**Reuse verdict:** `PollResponsesCache` is the right foundation and should not
be replaced — but it must become *poll-aware* (§3). The results page needs **no
new event kind, no new cache, and no new feed filter**; it needs a poll-aware
tally, one shared subscription, one shared ViewModel, and two thin platform
shells.

## 3. Correctness gaps to fix first

These are not cosmetic. Three of them make the numbers on the current card
wrong, and the results page would inherit all of them. Each is cheap to fix and
the fix improves the inline card too.

### 3.1 Multi-choice percentages use the wrong denominator

`ResponseTally.totalVotes()` (`PollResponsesCache.kt:52`) sums the per-option
voter sets, so a voter who picks 3 options is counted 3 times. `filterTo`
divides by that (`:88-95`). On a multi-choice poll the percentages therefore sum
to 100% and read as "share of selections" — but users read a poll bar as "share
of people". Desktop already diverges here, using `tallyState.votes.size`
("distinct voters", `DesktopPollCard.kt:196`) for its footer while its bars use
the selections denominator.

**Proposal:** make the basis explicit and platform-consistent.

```kotlin
fun totalVoters(): Int = votes.size          // distinct pubkeys
fun totalSelections(): Int = tally.entries.sumOf { it.value.size }
```

- single-choice → identical, no visible change;
- multi-choice → percent = `optionVoters / totalVoters`, bars may sum past 100%,
  and each row is captioned **"N of M voters"** so the reading is unambiguous.

### 3.2 Single-choice polls count every `response` tag

NIP-88: *"for singlechoice polls, the first response tag is to be considered the
actual response."* `votesByOption()` (`PollResponsesCache.kt:129-143`) iterates
**all** response tags regardless of poll type, so one malformed (or hostile)
1018 with a `response` tag per option gets counted in every bucket.
`ResponseTally` cannot fix this today because it never sees the `PollEvent`.

### 3.3 Votes outside the poll timeframe still count

NIP-88 keeps the latest response *within the poll timeframe*. `latestByAuthor()`
(`Note.kt:1685`) only compares `createdAt` — a vote stamped after `endsAt` wins
over a legitimate earlier one, and a closed poll's totals keep moving.

### 3.4 Unknown option codes inflate the tally

A `response` tag whose code isn't in `PollEvent.options()` creates a phantom
bucket that still contributes to `totalVotes()`, dragging every real
percentage down. It should be dropped (and counted separately, so the results
page can show "3 responses ignored (invalid)").

### 3.5 The fix: a poll-aware tally

All four collapse into one change — give the tally the poll it belongs to:

```kotlin
// commons/…/model/nip88Polls/PollTallyPolicy.kt  (new)
@Immutable
class PollTallyPolicy(
    val validCodes: Set<String>,
    val type: PollType,
    val createdAt: Long,
    val endsAt: Long?,
) {
    fun isInWindow(createdAt: Long) = createdAt >= this.createdAt && (endsAt == null || createdAt <= endsAt)
    fun accept(codes: List<String>): Set<String> =
        when (type) {
            PollType.SINGLE_CHOICE -> setOfNotNull(codes.firstOrNull { it in validCodes })
            PollType.MULTI_CHOICE -> codes.filterTo(mutableSetOf()) { it in validCodes }
        }
}
```

`PollResponsesCache` gains `var policy: PollTallyPolicy?`, set by
`LocalCache.consume(PollEvent)` (and by `DesktopLocalCache.consume`) when the
poll event itself arrives — responses routinely arrive **before** the poll they
reference, so `ResponseTally` must recompute when the policy lands, and must
degrade to today's permissive behaviour while `policy == null`.
`ResponseTally` then also exposes `rejected: Int` for the "ignored" caption.

Note the ordering hazard is real and already present: `consume(PollResponseEvent)`
calls `getOrCreateNote(pollId).pollState().addResponse(...)`
(`LocalCache.kt:2936`) on a note that may still be an empty placeholder.

## 4. Data completeness (the second reason to fix this now)

**Android never queries a poll's own relays for votes.** Kind 1018 rides along
in the generic engagement filter (`FilterRepliesAndReactionsToNotes.kt:75`),
whose relay set is `Note.relayUrlsForReactions()` = *author inbox relays + relays
the note was seen on* (`Note.kt:339`). NIP-88 votes are published to the relays
listed in the poll's own `relay` tags (`EventBroadcaster.kt:233` does this on the
write side), which the viewer usually isn't subscribed to. Desktop diagnosed and
fixed this per-card (`DesktopPollCard.kt:132-157`); Android tallies are
systematically short.

That same filter also carries `limit = 100` and never pages, so any poll with
more than 100 responses truncates silently.

**Proposal — one shared assembler, used by both platforms and by the results page:**

- `commons/…/relayClient/…/polls/PollResponsesFilterAssembler.kt` — a
  compose-scoped `Subscribable` keyed by poll id, filtering
  `kinds = [1018], tags = {"e": [pollId]}` over `PollEvent.relays() + the
  viewer's read relays` (this is desktop's fix, lifted out of the card).
- The results page additionally does a one-shot backfill with **`fetchAllPages`**
  from `quartz/…/nip01Core/relay/client/accessories/` — do **not** hand-roll a
  paging loop; that package already has it.
- Optional but cheap: a NIP-45 **`count`** call (same package) per relay gives
  "relay reports 412 responses, 380 loaded" — the page can then be honest about
  incompleteness instead of presenting a partial tally as final.

## 5. Screen design

### 5.1 Anatomy

```
┌─────────────────────────────────────────────┐
│ ←  Poll results                          ⟳  │   TopBarWithBackButton
├─────────────────────────────────────────────┤
│ [avatar] Alice · 2d ago                     │   author row (UserCompose-style)
│ "Which relay should we default to?"         │   poll question (content)
│ ⬤ Open · closes in 4h   ⬡ Single choice     │   status + type chips
│ 412 voters · 412 selections                 │   totals (§3.1)
├─────────────────────────────────────────────┤
│ ▸ nos.lol                    189  46%  ✓    │   option rows, winner marked,
│   [▓▓▓▓▓▓▓▓▓░░░░░░░░░░]  ◍◍◍◍ +185          │   animated bar + avatar stack
│ ▸ relay.damus.io             142  34%       │
│   [▓▓▓▓▓▓░░░░░░░░░░░░░]  ◍◍◍◍ +138          │
│ ▸ purplepag.es                81  20%       │
├─────────────────────────────────────────────┤
│ You voted: nos.lol            [Change vote] │   only when signed in + voted
├─────────────────────────────────────────────┤
│ [All] [nos.lol] [damus] [purplepag.es]      │   option filter chips
│ [Everyone ▾] [Recent ▾]      🔍 search      │   audience + sort + search
├─────────────────────────────────────────────┤
│ [avatar] you            nos.lol      2d ago │   voter rows
│ [avatar] Bob (follows)  damus        2d ago │
│ [avatar] Carol          nos.lol      1d ago │
│ …                                            │
├─────────────────────────────────────────────┤
│ 380 of ~412 loaded from 6 relays · 3 ignored│   completeness footer (§4)
└─────────────────────────────────────────────┘
```

### 5.2 Behaviour decisions

| Question | Decision | Why |
|---|---|---|
| Percent basis | single-choice: % of voters; multi-choice: % of voters, bars may exceed 100% in sum, each row captioned "N of M voters" | §3.1 — matches how people read a poll |
| Voter list default | flat list of **all** voters, each row showing which option(s) they chose | answers "who voted for what" in one screen without drilling |
| Option chips | filter the voter list to one option; the summary block never filters | keeps totals stable while browsing |
| Audience filter | `Everyone` (default) / `Following` / `Following + WoT` | reuses `account.allFollows.flow`, already the tally's priority set |
| Muted users | excluded by default via `account.isHidden`, with a footer line "2 hidden by your mute list" and a toggle | consistent with every other user list in the app |
| Sort | `Relevance` (you → follows → pubkey — today's `filterTo` comparator), `Newest`, `Oldest` | relevance is already implemented; the other two are one comparator each |
| Ordering of you | your own row always pinned first in Relevance, and your vote echoed in the "You voted" strip | already the `filterTo` behaviour |
| Live updates | the page is a live view of `pollState().responses`; new votes animate in | it's a StateFlow already; no refresh button needed except for the relay backfill |
| Closed polls | status chip flips to "Ended · 2d ago", "Change vote" disappears, late votes excluded (§3.3) with an "N late votes excluded" footnote | spec compliance, and it explains a number that would otherwise look wrong |
| Empty state | "No votes yet" + the option rows at 0 | |

### 5.3 Privacy call-out

NIP-88 responses are **public, unencrypted events** — this page doesn't disclose
anything new, but it makes a fact legible that many users have not internalised.
Two small additions belong with this work:

- an ⓘ affordance on the results header: *"Poll votes are public. Anyone can see
  who voted for what."*
- the same one-liner next to the vote controls in `RenderPollCard` **before** the
  first vote — the honest place to say it is where the choice is made, not after.

There is no "anonymous vote" option to offer; saying so plainly is the whole fix.

## 6. Code structure

Following `commons/ARCHITECTURE.md` (state + ViewModels + shared Compose in
`commons`, screens/nav platform-native):

**New in `commons` (shared):**

| File | Contents |
|---|---|
| `model/nip88Polls/PollTallyPolicy.kt` | §3.5 |
| `model/nip88Polls/PollResponsesCache.kt` *(edit)* | poll-aware `ResponseTally`, `totalVoters()`, `totalSelections()`, `rejected`, `lateVotes` |
| `viewmodels/nip88Polls/PollResultsViewModel.kt` | holds the poll `Note`; exposes `StateFlow<PollResultsState>` (header + option rows + filtered/sorted voter rows + completeness meta); owns filter/sort/search `MutableStateFlow`s; drives the backfill. Sibling in style to `viewmodels/LiveStreamTopZappersViewModel.kt`, with the usual nested `Factory` |
| `relayClient/…/polls/PollResponsesFilterAssembler.kt` | §4 |
| `ui/note/polls/PollResultsHeader.kt`, `PollOptionBreakdown.kt`, `PollVoterRow.kt` | slot-based shared composables — the user avatar/name cell is a `@Composable` slot supplied by each platform (`UserCompose` on Android, desktop's own row), per `compose-slot-api-pattern` |

**New in `amethyst` (Android):**

- `ui/screen/loggedIn/polls/results/PollResultsScreen.kt` — `LoadNote(noteId) { … }`
  + `Scaffold(topBar = TopBarWithBackButton(...))` + `LazyColumn`, modelled on
  `ContactListUsersScreen.kt:51`, which is the established "users related to note
  X" screen in this codebase.
- `Route.PollResults(val noteId: String)` in `ui/navigation/routes/Routes.kt`,
  registered with `composableFromEndArgs<Route.PollResults>` in `AppNavigation.kt`
  (the slide-in detail-screen builder).

**New in `desktopApp`:**

- `DeckColumnType.PollResults(noteId)` in `ui/deck/DeckColumnType.kt` (plus the
  `title()` and `typeKey()` branches — those `when`s are exhaustive) and a render
  branch in `DeckColumnContainer.kt`. Keep `VoterListPopup` as the quick peek and
  add a **"See all voters"** footer that opens the column.

**Edited (entry points):**

- `Poll.kt` — make the totals line and the `UserGallery` `+N` chip clickable →
  `nav.nav(Route.PollResults(noteId))`; add a "N votes" count next to the
  percentage (currently absent).
- Note dropdown menu — a "Poll results" item when the note is a `PollEvent`.

**Deliberately not built:** no new feed filter, no new event kind, no second
cache, no per-platform tally logic.

## 7. Zap polls (kind 6969) — phase 4

`ZapPollEvent` results are **amount-weighted** (`PollNoteViewModel.refreshTallies()`,
`ZapPollNoteViewModel.kt:102`), not one-person-one-vote, so they share the page
*shell* but not the tally:

- option rows show **sats per option** + zapper count, plus the poll's
  `consensusThreshold` drawn as a line on the bars;
- voter rows are ordered by amount and show the amount;
- private zaps are visible only where the viewer can decrypt them
  (`cachedIsPollOptionZappedBy`, `:230`) — the page must say "some zaps are
  private and not shown" rather than quietly under-count.

Sequencing it last keeps the NIP-88 work from being blocked on reconciling two
very different tally models.

## 8. Testing

- Extend `commons/src/commonTest/…/model/nip88Polls/PollResponsesCacheTest.kt`:
  single-choice response carrying 3 tags counts once (§3.2); vote after `endsAt`
  excluded and counted as late (§3.3); unknown code rejected and excluded from
  the denominator (§3.4); multi-choice `totalVoters` vs `totalSelections` (§3.1);
  policy arriving *after* responses recomputes the tally (§3.5); duplicate
  pubkey latest-wins (existing behaviour, lock it in).
- New `PollResultsViewModelTest` with Turbine: filter/sort/search transitions,
  mute exclusion, live insertion of a new vote.
- Manual: a >100-response poll (paging), a poll whose `relay` tags the viewer
  isn't connected to (completeness), a multi-choice poll (denominators).

## 9. Phasing

| Phase | Scope | Ships value alone? |
|---|---|---|
| 0 | Poll-aware tally (§3) — commons + tests | yes — fixes the inline card's numbers on both platforms |
| 1 | Shared subscription + backfill (§4) | yes — Android tallies stop being short |
| 2 | `PollResultsViewModel` + shared composables + Android screen + route + entry points | yes — the feature |
| 3 | Filters, sort, search, completeness footer, privacy call-out (§5.2–5.3) | polish |
| 4 | Desktop column; then zap polls (§7) | parity, then breadth |

Phases 0 and 1 are worth landing even if the page itself is deferred: they are
bug fixes wearing a feature's clothes.

## 10. Open questions

1. **Multi-choice bars summing past 100%** — the honest rendering, but visually
   unusual. Alternative: keep selections as the basis and caption "% of
   selections". Needs a call before §3.1 lands.
2. **Should the results page be reachable for polls you haven't voted on yet?**
   The inline card deliberately hides results until you vote / opt in
   (`Poll.kt:298-336`, `hasViewedPollResults`). Proposal: the page respects the
   same gate — reaching it counts as opting in and calls `markPollResultsViewed`,
   exactly like today's "View results" link.
3. **WoT tier in the audience filter** — worth it, or do `Everyone` / `Following`
   cover the need?
4. **Export/share results** (copy as text, or a rendered image) — desirable, but
   listed as out of scope until the page exists.
