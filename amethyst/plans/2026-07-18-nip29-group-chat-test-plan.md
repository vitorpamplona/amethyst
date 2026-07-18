# NIP-29 group-chat loading — test plan (per screen × per assembler)

**For:** an AI validating branch `claude/nip29-group-load-perf-wz4yca` before trusting the
state-vs-content refactor (see `2026-07-18-nip29-group-chat-subscriptions.md`).
**Question this answers:** *does the correct data load on every screen, and can we ever miss a message?*

## What is / isn't verifiable headless

| Layer | Harness | Covers |
|---|---|---|
| Filter **shapes** each assembler builds | amethyst JVM unit tests (new) | the REQ is correct for the screen's job |
| Relay **serves** those filters; **ingest** into `LocalCache`→`RelayGroupChannel.notes`; paging framework | **`amy` + `geode`/`amy serve`** (drives the same quartz+commons client) | the reused machinery + filter shapes work against a real relay |
| **Screen loading** (mount → subscribe → feed renders); UI marker/sentinel→`advance`; always-on mounting; backfill-to-window loop | **Android emulator/device** | the amethyst wiring end-to-end |

The amethyst *assemblers* are Android-module, so `amy` cannot invoke them directly — it validates the
**framework + filter shapes + ingest** they depend on. The **screen** rows below therefore have a
headless part (unit + amy) and a device part; do both, and mark any device row you couldn't run.

## The assemblers under test (all of them)

| # | Assembler | Mounts on | Must load |
|---|---|---|---|
| 1 | `RelayGroupJoinedState` (always-on) | LoggedInPage → every screen | joined groups' 39000/1/2/3/5 → name, roster, roles, pins, my membership |
| 2 | `RelayGroupJoinedChatTail` (always-on) | LoggedInPage → Messages | joined groups' recent chat (`#h` since=window) → true newest-message previews |
| 3 | `RelayGroupOpenChatTail` | open group chat screen | the open group's recent chat + live (incl. **non-joined**) |
| 4 | `RelayGroupOpenChatHistory` | open group chat screen | older chat on demand (`#h` until+limit), gap-proof |
| 5 | `RelayGroupOpenThreads` | Threads tab | kind-11/1111 threads |
| 6 | `RelayGroupCardWarmup` | discovery cards, relay channel-list, members/metadata/parent screens | a **non-joined** card's metadata + preview; **skips joined** groups |
| 7 | `RelayGroupsOnRelay` | relay channel-list, subgroups bar, parent picker | a host relay's whole group directory |
| 8 | `RelayGroupsDiscovery` | Discovery screen | cross-relay discovery feed (by follows / global) |
| 9 | `ChannelPublicFilter` (relay-group branch) | open group chat screen | open group metadata + **pinned-id backfill** (incl. non-joined; pins older than window) |
| 10 | `filterGroupNotificationsToPubkey` (always-on notifications) | account-level | group content that **p-tags me**, even if I never opened the group |

## Harness setup (headless)

```bash
./gradlew :cli:installDist            # build amy
RELAY=ws://127.0.0.1:7447
amy serve --port 7447 &               # embedded relay (geode); or run :geode directly
# Identities: one "relay/operator" key (signs 39xxx), a few member keys, and "me".
# Seed a group G on the relay:
amy relaygroup create --relay $RELAY --gid G --name "Test" ...   # 39000/39001/39002
# Seed chat spanning the live-tail boundary (7d): messages older AND newer than now-7d.
for t in <timestamps old→new>; do amy publish --relay $RELAY --kind 9 --tag h=G --created-at $t "msg $t"; done
# Seed: kind-11 thread + kind-1111 reply (h=G); a pinned kind-9 older than 7d + 39005 pin list;
#       one kind-9 that p-tags "me"; a SECOND group G2 on the same relay (batching); a group on a
#       second relay R2 (multi-relay); a group with <LIMIT total messages (small-group path).
```
Discover exact flags with `amy <verb> --help` (`fetch`/`subscribe`/`publish`/`relaygroup`).
`amy fetch --json` gives machine-checkable output for assertions.

---

## Tier A — baseline (must stay green)
```bash
./gradlew :amethyst:compilePlayDebugKotlin
./gradlew :quartz:jvmTest :commons:jvmTest :amethyst:testPlayDebugUnitTest :cli:test
./gradlew spotlessCheck
```

## Tier B — new amethyst unit tests (filter shapes per assembler)

Construct a minimal `Account` with `relayGroupList.liveRelayGroupList` = {G@R, G2@R} (+ a mock
`INostrClient`). If wiring a full `Account` is too heavy, **first refactor the filter construction out
of each `updateFilter` into a pure function** (`buildJoinedChatTailFilters(joinedTags, since)`,
`buildOpenChatHistoryFilters(groupId, armed, until, limit)`, …) and test those — this is itself a
worthwhile testability change. Assert, per assembler:

- **1 State:** one `#d` filter per host relay; kinds = 39000/39001/39002/39003/39005; `d` = all joined ids on that relay; `since` = shared per-relay EOSE. Disabled when NIP-29 toggle off / joined empty.
- **2 JoinedChatTail:** one `#h` filter per host relay; kinds = [9,poll]; `h` = all joined ids on that relay; `since = recentBoundary()`; **no per-group `limit`**. Two groups on one relay ⇒ **one** filter.
- **3 OpenChatTail:** one `#h` filter, host relay, kinds [9,poll], `since = recentBoundary()`, the single open group id.
- **4 OpenChatHistory:** with no relay armed ⇒ empty; after `advance(relay)` ⇒ one `#h` filter at `requestedUntilFor(relay)`, `limit = pageLimit`, **all authors** (no `authors`).
- **5 OpenThreads:** `#h`, kinds [11,1111], host relay.
- **6 CardWarmup:** a **joined** group ⇒ `emptyList()`; a **non-joined** group ⇒ metadata (unless contentOnly) + `#h` content (9,poll,11,1111) `limit`.
- **7 OnRelay:** directory `#`-less filter, kinds 39000-39003, `limit 500`, that relay.
- **8 Discovery:** by-follows + host-relay `#p` roster augmentation (see `RelayGroupsDiscoverySubAssembler`); global variant.
- **9 ChannelPublic relay-group branch:** returns **only** `filterRelayGroupState` (metadata + pin ids) — **no** message-window filter.
- **Reconnect stability:** re-run each `updateFilter` after a simulated EOSE; assert `FiltersChanged.needsToResendRequest(old,new)` is **false** for the tails/state (a `since`-only bump) — i.e. no full replay.

## Tier C — `amy` + relay integration (framework, ingest, can't-miss)

Issue the **exact filter shapes** from Tier B against the seeded relay and assert results:

- **C1 State load:** `amy fetch --kind 39000,39001,39002,39003,39005 --tag d=G --json` returns the seeded state. (screen-1)
- **C2 Preview/tail:** `amy fetch --kind 9 --tag h=G --since <now-7d> --json` returns only in-window messages; the newest equals the true newest. Batched: `--tag h=G --tag h=G2` returns both groups' recent in one query. (screens 2,3)
- **C3 History paging (CAN'T-MISS — the crown jewel):** seed **N=120** messages (older than 7d, spread over months). Starting `until=now`, repeatedly `amy fetch --kind 9 --tag h=G --until <cursor> --limit 50 --json`, setting the next `until = oldest.created_at - 1`, until an empty page. Assert the **union of all pages = all 120 ids, no gaps, no infinite loop** (mirror `RelayLoadingCursors.advance/onEose`). Then confirm a relay that returns the same newest events on a repeat page terminates (the `onEose` "not strictly older ⇒ done" guard). (screen-4)
- **C4 Ingest:** drive `amy subscribe`/`fetch` so events flow through the real client, then assert they land in a `RelayGroupChannel` keyed by `GroupId(G, R)` and surface via the `ChannelFeedFilter` predicate (kind 9/poll in, 1111 out). (screens 2,3)
- **C5 Threads:** `--kind 11,1111 --tag h=G` returns thread + reply; confirm 1111 attaches to threads, not the chat timeline. (screen-5)
- **C6 Pins:** a pinned kind-9 older than the window is **not** returned by C2 but **is** by `amy fetch --ids <pinnedId>` — proving the pinned-id backfill path still reaches it. (screen-9)
- **C7 Notifications:** `--kind 9 --tag h=G --tag p=<me>` returns the me-tagged message. (screen-10)
- **C8 Directory / discovery:** `--kind 39000 --limit 500` on R lists G+G2; a `#p=<follow>` roster query on the host relay surfaces a follow's group (the discovery augmentation). (screens 7,8)
- **C9 Multi-relay + reconnect:** repeat C2 against R and R2; drop and re-issue the subscription and confirm (via `--json` counts / relay logs) that a `since`-carrying re-REQ returns only the tail, not a full replay.

## Tier D — Android app, per screen (emulator/device; flag if unrunnable)

Boot `:amethyst:installDebug` against the seeded relay (point the account's relay list at `$RELAY`).
Watch logcat: `adb logcat | grep -E "DMPagination|relayGroup"`.

For **each screen**, the pass criteria:

- **D1 Messages list (1,2):** cold start with app already having joined G → the G row shows its **true newest** message (not a stale/scattered one), and its name/avatar (state). Join **G2 mid-session** (don't restart) → within seconds G2 appears with a real preview — *this is the original bug; it must now pass.*
- **D2 Open joined group (3,4,9 + backfill):** tap G → lands on a populated first screen (~50, the backfill-to-window), name/pins present. **Scroll up** past the window → older pages load, the reach marker advances, the "loading older" card shows then flips to "all caught up" at the bottom. No duplicate rows.
- **D3 Open non-joined group by link (3,4,9):** open a `naddr`/link to a group you have **not** joined → recent chat + live updates load (OpenChatTail) and scroll-up pages (OpenChatHistory), even though it's absent from the joined tail.
- **D4 Threads tab (5):** open Threads → kind-11 threads list; open one → its 1111 replies.
- **D5 Discovery (8,6):** open Discovery → groups list; a card fills name+activity (CardWarmup for non-joined). A **joined** group shown in "My Groups" still renders (from cache) though CardWarmup emits nothing for it.
- **D6 Relay channel-list / browse (7,6):** browse a relay → its directory lists groups; tapping one warms + opens.
- **D7 Members / Metadata screens (6/1):** roster + roles render.
- **D8 Reconnect (2,3,4):** toggle airplane mode on the open group and Messages → on reconnect, logcat shows incremental `since`/`until` REQs, **not** a full page replay; no missing or duplicated messages.
- **D9 Notifications (10):** with G *not* open, have another key post a message p-tagging me → it appears in notifications / unread.
- **D10 Quiet group:** a group whose newest message is older than 7d → Messages row falls back to cached/placeholder (documented limitation), and opening it backfills via the pager.

## Cross-cutting invariants (assert throughout)

- **No missed messages:** the union of tail + history + pins + notifications = the full timeline; C3 is the decisive test. Also exercise `RelayLoadingCursors.rewindTo` — trim the cache below the window, page again, confirm the pruned band re-loads.
- **No double-download on reconnect** (C9/D8).
- **Retirement left no gap:** with `RelayGroupMyJoinedGroups` deleted and `ChannelPublic`/`ChannelFromUser` relay-group content removed, D1/D2/D3 still load — proving the tail+pager replaced them.
- **CardWarmup joined-skip** (Tier B #6 / D5): a joined card issues no warmup REQ.
- **Serving-relay hazard (known open item):** quote a group message fetched from a **non-host** relay (`filterMissingEvents`) and confirm whether it appears in the group (`GroupId(G, hostR)`) or is filed under `GroupId(G, otherR)` and lost. Documents the plan's §2 follow-up; expected to **fail** until that fix lands.

## Exit criteria
- Tier A green; Tier B all assertions pass; Tier C1–C9 pass (esp. **C3**).
- Tier D1–D9 pass on device, or each unrun row is explicitly flagged for a human.
- Known-failing by design until follow-ups: the serving-relay hazard row and (if unadopted) a threads pager.
