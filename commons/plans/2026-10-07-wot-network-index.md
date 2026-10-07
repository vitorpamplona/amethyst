# Web of Trust network index (NIP-85 kind 30382, ~300k users)

Status: **design — decisions under review, nothing implemented.**

## 1. Goal

Know, in under a microsecond and without a network call, whether a pubkey is inside the
logged-in user's extended network and how much the user's trust provider trusts them.
The answer gates:

- DMs: the **Known** vs **New Requests** split.
- Notifications: the existing **Curated** filter (`TopFilter.Selected`).
- Replies, and possibly public chat messages (later phases).
- The rank / follower-count badges, which today come from a per-profile relay subscription.

The source is the user's NIP-85 trust provider: one kind 30382 card per member of the
network, all signed by the provider key named in the user's kind 10040. The first provider
we onboard is Brainstorm (GrapeRank), from a new **Web of Trust** settings screen. That
screen also sets the minimum accepted score, default **5**.

## 2. What we measured (2026-10-07)

All numbers come from the live `wss://scores.brainstorm.world`. That is the relay real
Brainstorm 10040s point at, and the one Brainstorm's web app uses
(`VITE_NIP85_RELAY_URL`). Two real per-user provider keys were tested.

| Fact | Value |
|---|---|
| Relay | strfry, `max_limit` **100,000**, NIP-45 COUNT and NIP-77 negentropy supported |
| Older relay `nip85.brainstorm.world` | `max_limit` **500**, negentropy supported. Some 10040s in the wild (fiatjaf's fixture) still point here. |
| Cards per user | **154,755** and **149,609** |
| Card size | ~520 B of JSON. Tags: `d`, `rank`, `followers`, `reporters`, `muters`, `client`, `hops` |
| Cold download | **93 MB / 34 s** (155k cards, 2 REQs of 100k, from a datacenter). gzip shrinks the stream ~3.3×. |
| Shared timestamps | **80,577** cards of one user carry the *same* `created_at`, because the provider publishes in batches. |
| Churn | Provider A: 9,704 cards re-signed in 3 days, 25,779 in 30 days. Provider B: all 155k re-signed today. |
| Deletions | Provider A has **169,317** kind-5 events, each carrying many `a` tags (`30382:<P>:<target>`). |
| Rank (0–100) | ≥ 5: **77,270 / 154,755** and **75,283 / 149,609**, so about half the network passes the default. Provider B also has 749 rank-0 cards. |
| Event-object heap | 300k card-shaped events ≈ **254 MB** (~850 B each), before `Note`/`User`/flows |
| Lookup, 310k keys (JVM) | `HashSet<String>` 0.1–0.25 µs but ~14 MB plus the strings. Sorted `LongArray` with hex parse ~0.4–0.5 µs at 2.5 MB. |
| Index file load (JVM) | 6.3 MB file, read and decoded in **14–40 ms** |
| Signature check | ~75 µs per event on JVM, 1–2k events/s parse+verify on a phone (`quartz/plans/2026-07-02-nostrclient-receiver-perf.md`). That is minutes for 300k. |

How Brainstorm publishes (read from `NosFabrica/brainstorm_server`,
`app/message_queue_tasks/upload_nostr_events.py`):

- **Score runs.** Scores are recomputed on a schedule (default `schedule_interval_seconds = 604800`, 7 days) or on demand. In steady state only changed scores are published; an occasional full sync re-asserts every card above the cutoff.
- **Removal.** When someone falls out of the network, Brainstorm first publishes a **rank-0 card with no `hops`**, then a kind-5 deletion by `a` tag. The zero card is there "so that even if the relay rejects kind 5, the score is effectively zeroed out".
- **Provider keys are per user.** `authors=[P]` returns exactly that user's network and nothing else.

## 3. What exists today (survey)

| Piece | Where | Reuse? |
|---|---|---|
| 10040 parsing, provider slots | `commons/.../model/trustedAssertions/TrustProviderListState.kt` (`liveUserRankProvider`, `liveUserFollowerCount`), with local backup in `AccountSettings.backupTrustProviderList` | Reuse as is |
| Per-profile rank/followers | `UserCardsCache.rankFlow` / `followerCountStrFlow`, read at `commonsUI/.../AccountUserObservers.kt:470,485`. Cards are fetched per profile by `UserCardsSubAssembler` and stored as `AddressableNote`s in `LocalCache`. | Migrate (D16) |
| Relay ops | `fetchAllPages` (streams, pages by `until`, reports `UNPAGEABLE`), `negentropyReconcile` + `NegentropyLocalIndex`, `count`, `ParallelEventVerifier`, `AdaptiveRelayLimiter` in `quartz/.../relay/client/accessories/` | Reuse |
| Compact hex keys | `Hex.readLong(hex, offset)`, `Hex.toLong128` in `quartz/utils/Hex.kt`. `SeenIds` uses the same 128-bit-prefix idea for event ids. | Reuse |
| Per-account files | `AccountCacheState.createAccount` makes `filesDir/accounts/<pubkey>/` (`:275`); `deleteAccountFiles` removes it on logoff; okio `platformFileSystem`; `atomicWrite` pattern in `FileCordnStores.kt` | Reuse |
| Synced settings | `AccountSyncedSettings` (NIP-78 kind 30078 `AmethystSettings`) | Add `minTrustScore` |
| Foreground signal | `ForegroundTracker.isForeground` (Android) | Trigger for small updates |
| Background work | WorkManager (`NotificationCatchUpWorker`, `ScheduledPostWorker`, which resolves the account from `accountsCache`) | Weekly full check |
| DM split | `ChatroomListKnownFeedFilter.kt:70-78` / `ChatroomListNewFeedFilter.kt:47-56` (incremental path `:167-173`). Known = a sender is followed, or I sent a message. | Extend |
| Notifications | `NotificationFeedFilter.kt:712` author gate; Curated = `TopFilter.Selected` | Extend |
| Push notifications | Renderers apply only `account.isAcceptable(note)` and never the notification TopFilter | Decide (D18) |
| Replies | `ThreadLevelCalculator.kt:109-114` sorts into tiers (thread author, me, follows, rest) | Extend later |
| Friends-of-friends | `commons/wot/WoTService.kt`: Desktop-only badge, in memory, not persisted | Independent, no change |
| 10040 editing UI | None in the app; only `amy graperank register` | New screen |

There is **no Android event store**. `LocalCache` lives only in memory, and every event received
on the shared client is filed into it by `CacheClientConnector`
(`AppModules.kt:873`: `EventCollector(client) { cache.justConsume(...) }`).

## 4. Decisions

Each has a recommendation (**Rec.**). The ones marked **❓** need a call from you before
implementation starts.

### D1. Which provider feeds the index
Use the 10040 entry whose service is `30382:rank` (`liveUserRankProvider`): its pubkey `P`
and relay hint `R`. That is one provider, with no multi-provider merge (NIP-85 doesn't
specify one). Brainstorm is only the onboarding path; any NIP-85 provider listed under
`30382:rank` works the same way.

**Rec.** A change of `P` or `R` (new 10040, re-registration) throws the index away and
starts a fresh cold sync. The file header records `(P, R)` so a mismatch is detected on load.

### D2. Membership rule
`accepted(pk) = pk == me || pk ∈ follows || (indexReady && rank(pk) >= minScore)`

- Follows always pass, so a stale provider never demotes someone you follow.
- **No card means outside the network.** Brainstorm only publishes above its cutoff.
- **A rank-0 card counts as removed** (Brainstorm's removal marker), as does a card
  deleted by kind 5.
- Mutes, blocks and spam flags still win over everything (`isHidden` is checked first, as today).
- `hops` is stored but **not** used to gate (D7).

### D3. Before the index is ready
When the index is missing, still downloading, or WoT is off, every surface behaves
**exactly as today**. Otherwise the first sync would empty the DM list and notifications for
minutes. "Ready" means a complete snapshot for the current `(P, R)` is loaded.

### D4. Use LocalCache? **No.**
- **Size.** 300k cards ≈ 254 MB for the event objects alone, plus 300k `AddressableNote`s and the `User` objects `about.cards()` creates, against ~6 MB for the index.
- **Nothing persists.** LocalCache is in memory only on Android, so it would have to re-download every start.
- **The shared client files everything.** `CacheClientConnector` puts every event on the shared client into LocalCache, so the sync **must use its own client** (D8).

LocalCache keeps what it has today: the account's **own** contact cards (encrypted
nicknames, signed by the account, `UserAssertionsState`) and any 30382 that arrives
incidentally.

### D5. Key width in the index
| Option | Bytes / entry | Risk |
|---|---|---|
| 64-bit prefix | 8 | Grinding a key whose first 64 bits match *some* member is roughly 2^64 / 300k ≈ 2^46 key generations. Our estimate: about a day of GPU vanity grinding, which buys a spammer a place in Known. |
| **128-bit prefix** | 16 | Not feasible to grind; random collisions are effectively zero |
| Full 256 bits | 32 | No gain over 128 for this purpose |

**Rec.** 128-bit: two longs per entry, sorted, via `Hex.readLong(pk, 0)` / `Hex.readLong(pk, 16)`
(no allocation).

### D6. In-memory structure
An immutable `NetworkSnapshot`:

```
keys      LongArray(2N)   sorted by (hi, lo) — binary search, ~19 steps at 300k
rank      ByteArray(N)    0..100 (clamped; unsigned read)
hops      ByteArray(N)    0..127, -1 = unknown
followers IntArray(N)
provider, relay, syncCursor, lastFullCheck, version
```

- Published as `StateFlow<NetworkSnapshot?>`. Each update builds a new snapshot and swaps
  it in atomically; readers never lock.
- About 6.3 MB at 300k. Alternatives considered:
  - Open-addressing hash: same size, O(1), but needs a rehash on load and gives no sorted merge.
  - `HashSet<String>`: 7–8× the memory.
- **❓ D6a.** Also memoize the two prefix longs on `User` so hot paths skip the hex read?
  **Rec.** Not in v1: `Hex.readLong` is cheap and allocation-free. Revisit if profiling says otherwise.

### D7. Which tag values to keep
Keep `rank` (the gate), `followers` (the badge), and `hops` (cheap, useful for UI such as
"3 hops away").

**❓** Brainstorm also publishes `reporters` and `muters`: counts of people in *your*
network who reported or muted this person. That is a strong spam signal ("muted by 12 people
you trust"), at 2 × 4 B × N ≈ 2.4 MB more. **Rec.** Leave them out of v1, but version the
file format so they can be added without a migration.

### D8. Relay client for the sync
**A second `NostrClient`** with the same `websocketBuilder`, so Tor routing and the relay
blocklist still apply. It is **not** wrapped by `CacheClientConnector`, is created for a sync
and disconnected afterwards. This also keeps the bulk sync from using up the shared
client's subscription slots. Desktop already does this for its kind-10050 indexer client
(`Main.kt:1039`).

### D9. Cold download
1. Read NIP-11 for `R`: `max_limit`, `max_subscriptions`, negentropy support.
2. **Path A (preferred when `max_limit` ≥ ~100k):** `fetchAllPages(R, {kinds:[30382], authors:[P]})`, streaming into a growing set of arrays. Events are never held.
3. **Path B (when A ends `UNPAGEABLE`):** if one second holds more cards than `max_limit` (likely on `nip85.brainstorm.world`), use `negentropyReconcile` with an empty local side to list the ids, then fetch them in batches of 500 by id. The perf plan measured fetch-by-id at up to 30–40k/s on a 33k-event corpus.
4. Sort, deduplicate by d-tag (newest `created_at` wins), drop rank 0, write the file (D11), swap the snapshot in.
5. Every event must have `kind == 30382`, `pubkey == P`, and a 64-hex d-tag. Anything else is dropped.

**❓ D9a. Signature checks.** A relay that inserts forged cards could make strangers "known".
- **Rec.** Check every signature with `ParallelEventVerifier`, on low-priority threads. This is roughly 2.5–5 min of CPU on a phone, once; updates are small.
- The cheaper option, checking a random 1–2% sample, only catches a small injection with modest probability, so we don't recommend it.

**❓ D9b. Resuming an interrupted cold download.** A 70–180 MB download on mobile can be killed.
**Rec.** v1 restarts from scratch but writes a "pages done" checkpoint (the `until` cursor
plus partial arrays to a `.partial` file), so Path A can resume. That is cheap to add because
`fetchAllPages` reports each page boundary (`onNewPage`).

**❓ D9c. Network policy.** The cold sync is 50–180 MB.
**Rec.** Run it on any network when the user starts it from the settings screen (they are
looking at a progress bar). Background cold syncs (e.g. after a provider change on another
device) wait for an unmetered network.

### D10. Small updates
The trigger is the app coming to the foreground (`ForegroundTracker`), plus right after the
user asks Brainstorm to recompute. It runs only if the last update was more than **12 h** ago,
since Brainstorm recomputes weekly by default.

1. `cursor = snapshot.syncCursor - 3600` (one hour of overlap absorbs batches sharing a timestamp and late relay arrivals).
2. `COUNT {kinds:[30382], authors:[P], since: cursor}`. If it exceeds about 20k and the network is metered, postpone; it is effectively a full re-publish.
3. Fetch `{kinds:[30382], authors:[P], since: cursor}` and `{kinds:[5], authors:[P], since: cursor}`.
4. Merge: upsert cards (newest per d-tag); rank 0 or a kind-5 `a` tag `30382:P:<target>` removes the entry.
5. Write the file, swap the snapshot in, and advance the cursor to the newest `created_at` seen.

### D11. On disk
A single file per account: `filesDir/accounts/<pubkey>/wot/network-v1.bin`. It lives inside the
account directory, so the existing `deleteAccountFiles` cleans it up on logoff.

```
magic "AWOT" | u16 version | 32B provider | relay (u16 len + utf8)
| i64 syncCursor | i64 lastFullCheck | i32 N
| keys  N×16 | rank N×1 | hops N×1 | followers N×4
```

- Written with okio to a temp file and then moved into place (`atomicWrite`), so a crash
  leaves the old file or the new one, never half.
- Read with one `readByteArray` and decoded on `Dispatchers.IO` when the `Account` is
  created: 14–40 ms on JVM, a small multiple of that on a phone.
- A bad magic, version, or `(P, R)` mismatch means the file is ignored and a cold sync is scheduled.

A second, optional file `network-ids-v1.bin` holds `(eventId 32B, createdAt i64)` per entry,
about 12 MB. It is read **only** by the weekly full check (D12) and never at startup.

### D12. Weekly full check
Purpose: catch deletions or rank-0 cards we missed (a relay outage, an update window
overflow).

- **Cheap check first.** `COUNT {kinds:[30382], authors:[P]}` against `N + removedSinceLastFull`. If they match, stop.
- **Otherwise** run `negentropyReconcile` against `network-ids-v1.bin` through a
  `NegentropyLocalIndex` adapter: fetch the ids we need, drop the ids only we have.
- On Android this is a WorkManager periodic job (unmetered, battery not low) that resolves
  the account from `accountsCache` like `ScheduledPostWorker`.

**❓** If you would rather not store the ids file, the fallback is a full re-download once a
month on Wi-Fi. That is simpler, but 50–180 MB a month.

### D13. Multiple accounts
Several `Account`s can be live at once: background notifications load every writable
account.
- **Rec.** Every live account with WoT enabled **loads** its index (~6 MB each), so
  notification filtering works for all of them.
- Only the **active** account runs the foreground update.
- The weekly worker goes through every WoT-enabled account.

### D14. Settings model
| Setting | Where | Why |
|---|---|---|
| Provider (P, R) | the 10040 itself (already on relays, backed up locally) | One source of truth, interoperable |
| `minTrustScore` (default 5) | `AccountSyncedSettings` (NIP-78) | A preference that should follow the user across devices |
| WoT filtering on/off | `AccountSyncedSettings` | Same |
| Brainstorm session token | memory only | Expires after 60 min (`AUTH_ACCESS_TOKEN_EXPIRE_MINUTES`); re-login just asks for one signature |
| Sync state | the index header | Local by nature |

**❓ D14a.** Is "WoT filtering on" implied by having a `30382:rank` provider, or a separate
switch? **Rec.** A separate switch, default **on** once onboarding finishes. Users who
already have a 10040 from elsewhere then get the feature only after opting in, instead of
having their DM list change silently on update.

### D15. Brainstorm onboarding (from `brainstorm_server` and `Brainstorm-UI` sources)
Base URL `https://api.brainstorm.world`; the NIP-85 relay is `wss://scores.brainstorm.world`.

1. `GET /authChallenge/{pubkey}` returns `{data:{challenge}}`.
2. Sign a **kind 22242** with tags `["t","brainstorm_login"]` and `["challenge", c]`, then
   `POST /authChallenge/{pubkey}/verify` with `{signed_event}`, which returns `{data:{token}}`.
   Login also creates the user's per-user service key on the server.
3. Authenticated calls send the header `access_token: <token>`.
4. `POST /user/graperank` triggers a score run. It needs at least one follow, is throttled to
   once per 30 min, and can return 429 when a tier's quota is used up.
5. `GET /user/history` returns `ta_pubkey` (the service key `P`) and
   `last_time_calculated_graperank`.
6. Sign a kind 10040 that **merges** into the existing list (keep other entries, public and
   private) the entries `["30382:rank", P, R]` and `["30382:followers", P, R]`, using
   `TrustProviderListEvent.add`. Publish to the outbox relays.
7. Once `last_time_calculated_graperank` is set (poll `/user/history`, or poll `COUNT` until it
   is above 0), start the cold sync (D9).

**❓ Open questions for this step:**
- **D15a. Public or private 10040 entries.** Brainstorm's own web app only recognises *public* entries (`declaresTrustProvider`), and other clients can only use public ones. Private entries hide which provider you use. **Rec.** Public; that is what Brainstorm's flow publishes.
- **D15b. An existing 10040 points at another rank provider.** **Rec.** Warn and ask before replacing it, as Brainstorm's UI does. Never replace silently.
- **D15c. Signers.** Two signatures (22242, 10040) go through the normal `NostrSigner`, so NIP-55 and NIP-46 signers work. Each call is a prompt for users who approve manually.
- **D15d. Free vs paid tiers.** `brainstorm_server` has billing tiers and manual-run quotas (`enforce_manual_quota`). The UI should surface 403/429 as "recomputed recently / quota reached", not as a failure.
- **D15e. Generic provider.** Should the settings screen also accept any NIP-85 provider (paste a pubkey and relay), or Brainstorm only? **Rec.** Show the current provider whatever it is (from the 10040) and offer Brainstorm as the one-tap setup.

### D16. Rank and follower count move to the index
- **Rec.** `rankFlow` and `followerCountStrFlow` read the snapshot (`snapshotFlow.map { it.rank(user) }`) when the index is ready **and** `P` is the provider for that slot. Brainstorm registers the same key for `rank` and `followers`.
- Otherwise they fall back to today's per-profile path, so users without WoT see no change.
- When the index serves both slots, `UserCardsSubAssembler` stops asking `R` for provider cards for each profile on screen, and keeps fetching only the account's own cards (nicknames).
- Someone outside the network shows no rank. That matches today, since the provider has no card for them.

### D17. Refreshing feeds when the snapshot or threshold changes
DM Known/New and the notification feed are additive filters; they won't re-check old items on their own.
- **Rec.** Expose `snapshotVersion: StateFlow<Long>` combined with `minTrustScore`.
- Their view models force a full refresh when either changes, debounced about 500 ms.
- The notification filter already rebuilds when `feedKey()` changes (it includes the TopFilter code), so adding `minTrustScore` and a WoT-ready flag to that key may be enough there.

### D18. Consumers, one by one
| Surface | Change | Phase |
|---|---|---|
| DM Known | `senderIntersects(follows) \|\| hasSentMessagesTo \|\| (wotOn && activeSenders.any { accepted })`. New stays its exact opposite, including the incremental path at `:167-173`. Group DMs: **any** accepted sender makes the room Known, matching how follows work today. | 4 |
| Notifications, Curated | Gate the author (`NotificationFeedFilter.kt:712`): `isAuthorInFollows \|\| accepted(author)` when `followList()` is `Selected` and WoT is on. Zaps are already handled: `notifAuthor` resolves a `ZapReceiptEvent` to the zap *request* sender, including private zaps (`:615-621`). | 4 |
| Push notifications | ❓ Today push ignores the TopFilter entirely. **Rec.** Apply the same WoT check when the account's notification filter is Curated. Otherwise a stranger's reply buzzes the phone but is hidden in the app. | 4 |
| Replies | ❓ Options: (a) add a "network" sorting tier between follows and the rest, (b) also collapse replies from outside the network behind "N more replies from outside your network", (c) hide them. **Rec.** (a) and (b). | 5 |
| Public chats / live chat | ❓ An opt-in "hide messages from outside my network" per chat type. **Rec.** Phase 5, off by default. | 5 |

### D19. Where code lives
| Piece | Module / package |
|---|---|
| `NetworkSnapshot` (arrays, lookup, merge, binary codec) | `quartz/.../nip85TrustedAssertions/users/index/`. Pure NIP-85 utility, no UI, KMP. |
| Sync (cold, update, full check) as `INostrClient` extensions | `quartz/.../nip85TrustedAssertions/users/index/` beside it |
| `WebOfTrustNetworkState` (load, save, schedule, `StateFlow`, accepted()) | `commons/.../wot/`, owned by `Account` |
| Brainstorm HTTP client + onboarding flow | `commons/.../wot/brainstorm/` |
| Settings screen, strings | `commonsUI` (strings in `composeResources`, per CLAUDE.md) |
| WorkManager job, foreground trigger | `amethyst/` shim |
| `amy wot sync \| check <npub> \| stats \| brainstorm login\|register` | `cli/`, a thin layer over the above |
| Desktop | wired through `DesktopIAccount` once the one-UI move reaches it; the core is already shared |

## 5. Phases

0. **Measure first.** `amy wot sync` against a real ~300k network: wall time, bytes, signature-check time, peak heap. Repeat on a mid-range phone through a debug button. This decides D9a/D9b/D9c.
1. **Index + storage + sync** (quartz/commons) with unit tests:
   - file format round trip;
   - merge, rank-0 and kind-5 removal;
   - the shared-timestamp `UNPAGEABLE` → negentropy fallback;
   - `(P, R)` mismatch.
2. **Account wiring + D16**: rank and follower badges served from the index; drop the per-profile provider subscriptions.
3. **Web of Trust settings + Brainstorm onboarding** (D14, D15), including the min-score slider with a live "N of M people pass" count.
4. **Consumers**: DM Known/New, Curated notifications, push (D18).
5. **Replies and chats** (D18).

## 6. Open questions (summary)

D6a (memoize prefixes on `User`), D7 (keep `reporters`/`muters`), D9a (check all signatures),
D9b (resume), D9c (network policy), D12 (store the ids file vs monthly re-download), D14a
(separate switch), D15a–e (Brainstorm flow details), D18 (zaps, push, replies, chats).
