# Embedded local relay (Desktop, then Android)

_2026-10-24. Proposed, not started. Nothing here has been prototyped or measured on a device;
the code references were read from this repo and from a shallow clone of vitorpamplona/quartz
on this date._

## Goal

Every Amethyst install carries its own Nostr relay that starts with the app and stores every
event the app receives:

- **Desktop** keeps all of the user's data and more, with no size cap by default, and can expose
  the relay to other apps on the machine (`amy`, other clients, a second profile).
- **Android** keeps a bounded cache that prunes itself, and can optionally expose the relay to
  other apps while the always-on notification service runs.
- **One screen on both** shows what the relay holds and lets the user browse, delete, import,
  export, set the budget and control access.

Both use the same code: quartz's relay engine and SQLite store, one host in `commons`, one
retention policy whose budget differs per platform, and one shared screen in `commonsUI`.

## Decisions

| Question | Decision |
| --- | --- |
| What goes into the store | **Option B: every event the app receives from a relay and verifies** (section 1). Option A, only what the relay lists already route there (as with Citrine), was rejected: it can't give Desktop the full offline copy. |
| How other Android apps reach it | A loopback WebSocket server, not a content provider (section 4). |
| Who can read the exposed socket | Only connections authenticated with NIP-42 as a logged-in account (section 5). |
| Managing the contents | A new shared screen, reusing the relay, devtools and NIP-86 screens (section 7). |

## What already exists

| Piece | Where | Use |
| --- | --- | --- |
| Local relay list | `settings.localRelayServers` → `Account.localRelayList` (`commons/.../model/localRelays/LocalRelayListState.kt`) | Already merged into the home, outbox, mine, DM-inbox, notification and default global relay sets (`Account.kt`, the `*RelayState` wiring). Built for Citrine. A URL in this list is written to and read from with no feed changes. |
| Kind-0 forwarding | `ForwardKind0ToLocalRelayState` | Already copies profiles to local relays. |
| Local Relays screen | `commonsUI/.../relays/local/` | Where the built-in relay shows up as a row, and the entry point to the new screen. |
| Relay engine + store | quartz: `nip01Core/relay/server/NostrServer`, `nip01Core/store/sqlite/EventStore` | KMP, runs on Android and JVM. NIP-09, NIP-40, NIP-62, NIP-77 and NIP-86 are all here, not in geode. |
| In-process transport | quartz: `relay/server/inprocess/InProcessWebSocket` | Plugs a `NostrServer` into `NostrClient` through a `WebsocketBuilder`, with no socket. Used by geode's `InProcessRelays` and the desktop test fixture `LaunchFixtureRelay`. |
| Trusted ingest | quartz: `NostrServer.ingest(event, skipVerify)` | Writes an event straight into the store and the live fan-out through the group-commit `IngestQueue`, skipping the connection's policy chain. Lets the app store events it already verified without paying Schnorr verification again. |
| NDJSON import/export | quartz: `nip01Core/store/NdjsonImportExport.kt` | Backs the screen's import and export. |
| In-process NIP-86 | quartz: `Nip86Server.dispatch(...)` | Lets the existing NIP-86 screen manage the local relay without HTTP. |
| History download | `commons/.../relays/eventsync/EventSync.kt` + `EventSyncScreen` | Already downloads everything the user wrote, every event that tags them and their DMs from every known relay, and publishes them to the outbox, inbox and DM relays. Those sets include local relays, so this already fills the store. It replaces the `MirrorWorker` backfill an earlier draft proposed. |
| NIP-86 management screen | `commonsUI/.../relays/nip86/RelayManagementScreen.kt` + `commons/.../relayManagement/Nip86Executor.kt` | Pubkey, event, kind and IP bans. Talks HTTP today; gets an in-process executor for the local URL. |
| Kind names and chips | `commonsUI/.../relays/KindDisplayName.kt`, `KindChip.kt` | For the breakdown by kind. |
| Devtools | `commonsUI/.../devtools/` (`EventInspectorScreen`, `RelayConsoleScreen`, `KindBrowserScreen`) | Event inspector for a stored event, and the raw relay console pointed at the local relay. |
| geode | `geode/` (about 1,800 lines of main code) | Adds on top of quartz: the Ktor CIO socket server (`KtorRelay`), TOML config, `MirrorWorker`, NIP-11/NIP-86 HTTP routes and the public-relay index strategy (`RelayIndexingStrategy`). |
| Embedding example | `cli/.../commands/ServeCommand.kt` (`amy serve`) | `RelayEngine(url, store = EventStore(...), adminPubkeys = ...)` + `KtorRelay(...).start()` + a shutdown hook. |
| Tor bypass for loopback | `commons/.../tor/TorRelayEvaluation.kt` | `isLocalHost()` relays are always dialled directly. |
| Always-on Android service | `amethyst/.../service/notifications/NotificationRelayService.kt` | `specialUse` foreground service with START_STICKY, a watchdog, boot and WorkManager restarts. |
| In-memory pruning rules | `commons/.../model/cache/CachePruner.kt` | Which events matter to the account in `LocalCache`. A starting point for the disk policy (it works on `Note`s, so follow its logic rather than share code). |

## Design

### 1. Shared host and option B ingest (`commons`)

A `LocalRelayHost` in `commons` (`jvmAndroid`, or `commonMain` if nothing JVM-only is needed):

- Owns one `EventStore` + `NostrServer` for the app, at a fixed loopback URL so the relay's
  identity is stable across restarts.
- Provides a routing `WebsocketBuilder`: the local URL goes to `InProcessWebSocket(server, out)`,
  everything else to the platform's OkHttp builder. Amethyst itself never uses a socket to reach
  its own relay, so a taken port can't break it.
- Provides `platformLocalRelays: StateFlow<Set<NormalizedRelayUrl>>`, which
  `LocalRelayListState.flow` merges with the user's settings. The built-in relay is not written
  into `settings.localRelayServers`: turning it off removes it cleanly, and the user's own Citrine
  or strfry entries are untouched.
- Runs maintenance (NIP-40 expiry, pruning, `optimize`) on a timer the platform drives.
- Admin pubkeys = the logged-in accounts, so NIP-86 and `amy admin` work against it.
- One store for the whole app, like `LocalCache`, not one per account: accounts share most of
  what they see (follows, profiles, threads).

**The ingest hook.** In `LocalCache.justConsumeAndUpdateIndexes`
(`commons/.../model/cache/EventCache.kt`), when `wasNew` is true, hand the event to the host,
which calls `server.ingest(event, skipVerify = true)`. Rules:

- **Only events that came from a relay:** `relay != null` and `relay` is not the local URL. The
  `wasVerified` flag can't be the test: decrypted DM contents (the unsigned events inside gift
  wraps) and Marmot messages are consumed with `relay = null, wasVerified = true`
  (`EventCache.kt`, `DecryptAndIndexProcessor.kt`, `AccountMarmotActions.kt`). Storing those
  would put decrypted messages in plain text in a database other apps can read. The gift wrap
  itself, as it came from the relay, is stored; its decrypted contents never are.
- **Not events read back from the local relay:** they are already there, and re-ingesting them
  would loop.
- **The user's own events need no hook:** the local relay is in the outbox set, so the existing
  publish path already sends them there, signed, through the in-process socket.
- **Skip ephemeral kinds** (20000–29999); the store rejects them anyway (`EphemeralModule`).
- **Deletions take care of themselves:** a kind 5 that arrives is stored, and the store's
  `reject_deleted_events` trigger and `DeletionRequestModule` remove what it deletes.
- **Never block the relay threads.** `justConsume` runs on the relay client's read path, so the
  hook puts the event on a channel drained by one coroutine into `ingest` (which group-commits).
  If the channel backs up past a limit, drop and count the drops; the screen shows the count.
  `LocalCache.live.newEventBundles` is not suitable as the source: it is a `MutableSharedFlow`
  with a 100-item `DROP_OLDEST` buffer and drops silently under load.

**Accounts.** Removing an account from the app deletes the gift wraps and DMs addressed to it
from the store (`#p` = that account, kinds 4 and 1059), so its private messages don't stay
behind for the next account on the machine.

### 2. Index strategy

- Desktop: `relayIndexingStrategy(fullTextSearch = true, liveNegentropyIndex = false)` from geode.
  The live negentropy index costs about 140 B of heap per event and only pays off for a public
  relay serving full-corpus NEG-OPENs. Keep full-text search: offline search of your own history
  is a main reason to have this relay.
- Android: quartz's client-tuned `DefaultIndexingStrategy` (no created-at-only, author-only or
  tag+kind+author indexes). Decide on full-text search after measuring its size.
- Measure bytes per event under both strategies before choosing Android's default budget.
  Option B on Desktop stores everything the global and discovery feeds pull in; measure how fast
  a day of normal use grows the store.

### 3. Desktop

- `desktopApp` depends on `:geode` for `KtorRelay` (as `cli` does).
- `DesktopAppModules` builds the host before `websocketBuilder` (`DesktopAppModules.kt`, the
  `BasicOkHttpWebSocket.Builder`) and wraps that builder in the router. The store lives at
  `filesDir/local-relay/events.db`, locked to the owner like `concord-images`. It starts in
  `initiate()`, off the UI thread, since opening the database can build indexes.
- Shutdown: stop the Ktor server, then close the engine, in the existing shutdown hook in
  `AmethystDesktop.kt`, before the settings flush.
- **"Expose to other apps"** (setting): `KtorRelay` on `127.0.0.1` only, never `0.0.0.0`, under
  the policy in section 5. Pick a fixed default port that clashes with neither Citrine's default
  nor `amy serve`'s 7447. If the port is taken, keep the in-process path and show a notice; don't
  move to another port silently, because other apps are configured with that URL.
- **History:** the existing `EventSync` ("download my history") fills the store, because local
  relays are in its outbox, inbox and DM target sets. The new screen links to it.
- Packaging: the jlink list in `desktopApp/build.gradle.kts` has `java.sql` and
  `java.management`. Boot the packaged build once to catch a `NoClassDefFound` from Ktor.
- Licensing: geode brings Ktor (Apache-2.0) and 4koma. Confirm 4koma's license from its
  `LICENSE` file before it ships in the desktop binary.

### 4. Android

**In-process store (step 1 on Android).** The shared host and the same ingest hook, built in the
**main** process only. `:napplet` never touches it (it has no `Amethyst.instance`). No service
and no socket are needed. `:geode` does not go into the APK: it pulls in the JVM-native SQLite
driver, OkHttp mirror workers and 4koma, and its value is the TCP server.

**Exposing it to other apps: a loopback server, not a content provider.**

| | Loopback WebSocket | Content provider |
| --- | --- | --- |
| Who can use it today | Every Nostr client and library: other Android clients, web clients in the phone's browser, `amy`/`nak` in Termux. Citrine has shown this works. | Nobody. A new protocol needing a NIP-55-style spec and a custom integration per app. |
| Live subscriptions | Native (REQ stream). | One-shot cursors. Live updates need `ContentObserver` + re-query or a bound AIDL/Messenger service. Large results hit the Binder (about 1 MB) and `CursorWindow` limits. |
| Knows the caller | No. Any app can connect, and every Android app has `INTERNET`. | Yes, `Binder.getCallingUid()`, so per-app grants are possible. |
| Needs the app running | Yes. | No: Android starts the process for a query, but that cold-starts `Amethyst.onCreate` and the full `AppModules`. |

Decision: loopback server, available only while `NotificationRelayService` runs. Users who want
the relay exposed already keep that service on, so an idle listening socket costs almost nothing
on top; the real cost is the traffic other apps send. Start the server in the same place the
service starts and stop it where the service stops. A content provider can come later, only as a
small read-only API if Android apps ask for per-app permissions or access while the service is
off.

Android-specific work:

- Update the manifest's `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` text, which today describes only the
  notification connections, and the Play Console justification. Review may push back.
- Either move `KtorRelay` and the NIP-11 route out of geode into a small module both platforms
  can use, or write a thin Ktor wrapper in `amethyst`. Check whether Ktor's CIO server engine
  officially supports Android (or what Citrine uses), and measure the APK size after R8.
- Check on a device whether Doze restricts loopback connections for an app running a foreground
  service.

### 5. Policy for the exposed relay

With option B the store holds more than DMs: messages from closed NIP-29 groups, Concord
communities, Buzz workspaces and paid or auth-gated relays, all of which the user could read only
because they authenticated there. A loopback socket can't tell apps apart, and the store keeps no
record of where an event came from. So the rule is simple:

- **Every REQ, COUNT and EVENT on the exposed socket needs NIP-42 auth as a logged-in account.**
  The local relay is the user's personal relay; an app reading it acts for the user and signs the
  challenge through the user's signer (Amber on Android, or a bunker). geode already has the
  auth machinery (see `geode/src/test/.../Nip42AuthDmDeliveryTest.kt`).
- Gift wraps and DMs are served only to the recipient's authenticated pubkey, even among the
  logged-in accounts.
- Unauthenticated public reads could come later as an opt-in, once the store can mark which
  events came from auth-gated sources.
- The in-process path (Amethyst itself) is not subject to this.
- NIP-11 `retention` (quartz has `Nip11RelayInformation.retention`) says honestly that the
  Android relay is a cache, not an archive.

### 6. Retention: one framework, different budgets

**What quartz's store already has:** `delete(filter)`, `delete(filters)`, `delete(id)`,
`deleteExpiredEvents()`, `count(filter)`, `vacuum()` and `optimize()`. Deletes cascade to the tag
and index rows (`PRAGMA foreign_keys = ON`) and invalidate the live negentropy index.
`delete(Filter())` deletes nothing by design.

**Gaps to close before pruning is safe on a phone:**

1. **Deleting doesn't shrink the file.** No `auto_vacuum` is set, so freed pages stay inside the
   file. Later inserts reuse them, so a store kept under a budget plateaus but never shrinks.
   `vacuum()` is a full `VACUUM`, which rewrites the database and can temporarily need about
   twice its size in free space, exactly what a nearly full phone lacks.
   *Fix in quartz:* `PRAGMA auto_vacuum = INCREMENTAL` at creation, `PRAGMA
   incremental_vacuum(N)` after each prune, one full `VACUUM` to convert an existing database,
   and a method that reports the bytes in use (`page_count` × `page_size` minus the free list).
2. **Delete-by-filter removes what matches, not what's least valuable.** A NIP-01 `Filter` has no
   negation, so "notes older than 30 days except from the user and their follows" can't be
   written as one. *Fix in quartz:* a `prune(...)` method that takes a protected set and an
   eviction order and runs SQL directly.
3. **Pruning tombstones brings deleted events back.** NIP-09 is enforced by the
   `reject_deleted_events` trigger (`DeletionRequestModule`), which looks for kind-5 rows in
   `event_tags`. Delete the kind 5 and the event it deleted is accepted again on the next sync or
   when another app writes it back. Replaceables and addressables have the same problem: prune
   the newest kind 0, 3 or 10002 and an older copy becomes acceptable again. NIP-62 vanish
   requests are safe: they live in their own `event_vanish` table.
   *Rule:* `prune` never removes kind 5, nor the newest replaceable/addressable version for any
   author whose events are kept. Both are small.
4. **Sync downloads pruned events again.** An `EventSync` run or negentropy reconcile whose
   window reaches past the retention period re-fetches the pruned events every time. *Rule:* on a
   budgeted store, sync windows use `since = now - retention`.

**The policy (`commons`, because it needs the account and its follows):**

- Never pruned:
  - the account's own events;
  - DMs and gift wraps to or from the account;
  - kind 5;
  - the newest replaceable/addressable versions for the user and their follows (profile,
    contacts, relay lists, other lists);
  - bookmarked events.
- Pruned first, oldest first:
  1. reactions, zaps and reposts on other people's notes;
  2. replies and notes from people the user doesn't follow;
  3. follows' notes older than N days.
- Budget: a setting. Android gets a cap (a few hundred MB, to be set from measurements). Desktop
  defaults to no cap but still applies NIP-40 expiry.
- When it runs: on Android from a WorkManager job or the always-on service's tick; on Desktop
  from an idle timer. Delete in small batches, each in its own transaction: the store has a
  single writer lock, and one large delete would hold up the inserts the UI is waiting on.
- Amethyst needs nothing new when an old event is gone locally: it fetches it from remote relays
  as it does today.

### 7. Management screen (`commonsUI`)

One shared screen, `LocalRelayScreen`, opened from the built-in row on the Local Relays screen
and from Settings. Route `Route.LocalRelay` in `commons/.../model/navigation/Routes.kt`; a
headless `LocalRelayViewModel` in `commons` (it only needs the host and the account); strings in
`commonsUI` Compose resources.

**Layout.** Adapts through `ScreenLayoutSpec` / `LocalScreenLayout`, not per platform:

- Compact (phone): a top bar with the relay's status, then three tabs: **Overview**, **Browse**,
  **Settings**.
- Wide (desktop, tablet, laptop): two panes. Left, the Overview breakdown as a list; right, Browse
  showing whatever row is selected on the left. Settings opens from the top bar.

**Overview.**

- Header card: space used, against the budget as a bar on Android (or alone on Desktop); event
  count; oldest stored event; space that can be reclaimed (free pages), with a **Compact** button.
- Status line: in-process; exposed or not, with the port and how many apps are connected; events
  stored in the last hour; ingest drops, if any (section 1).
- Breakdown by **kind** (`KindDisplayName`, `KindChip`): count and approximate size per kind,
  largest first. Tapping a kind opens Browse filtered to it.
- Breakdown by **author**: the top authors by event count, with avatars and names from
  `LocalCache`. Tapping one opens Browse filtered to them.
- Breakdown by **month**: a small bar chart of events per month, which shows at a glance what
  history is held and what pruning removed.

**Browse.**

- A filter bar: kinds (chips), authors (user search), date range (the date picker already used by
  `EventSyncScreen`), and text search when full-text search is on.
- Results as the normal note cards, paged with `until`, read straight from the store (not through
  `LocalCache`, so browsing doesn't fill memory).
- Each card's menu: **Inspect** (devtools `EventInspectorScreen`), **Delete from this relay**.
- Multi-select, and **Delete all matching** with the count shown before confirming ("Deletes
  12,345 events from this device's relay. Nothing is deleted from other relays."). Deleting here
  is local only and never publishes a NIP-09 deletion.
- Guarded rows: kind 5 tombstones and the newest replaceable versions are not deleted by a bulk
  delete (section 6); deleting the user's own events asks a second time.

**Settings.**

- On/off for the built-in relay.
- Budget (Android) and retention days per tier, **Prune now**, and the last prune's result
  (events removed, space freed).
- **Download my history:** opens the existing `EventSyncScreen`.
- **Import** and **Export** as NDJSON (quartz `NdjsonImportExport`) through the platform file
  picker ports.
- **Access:** "Expose to other apps", the port, and the connected sessions with the pubkey each
  authenticated as, with a button to disconnect one.
- **Bans:** the existing `RelayManagementScreen` (NIP-86), given an in-process `Nip86Executor`
  that calls `Nip86Server.dispatch` for the local URL instead of HTTP.
- **Advanced:** open the devtools `RelayConsoleScreen` pointed at the local relay; rebuild the
  search index; clear everything (confirm twice).

**What quartz needs for this screen.** `IEventStore` has `count(filter)` but no grouped
statistics, and one count per kind would be dozens of queries. Add a stats call that returns, in
one pass each: counts per kind, the top N authors by count, counts per month, and the
approximate size per kind (from stored row lengths; check whether the bundled SQLite has the
`dbstat` table for exact sizes). The header numbers come from the bytes-in-use method in section
6, item 1.

## Work order

1. **quartz:**
   - incremental vacuum and bytes-in-use reporting;
   - `prune(...)` with the tombstone and newest-version guarantees, with a test for each (delete a
     kind 5's target, prune, and check the target is still rejected);
   - the grouped stats call for the screen.
2. **commons:**
   - `LocalRelayHost`, the routing `WebsocketBuilder`, and `platformLocalRelays` merged into
     `LocalRelayListState`;
   - the option B ingest hook in `justConsumeAndUpdateIndexes`, with tests that a decrypted rumor
     is never stored, that events read from the local relay are not re-ingested, and that a kind
     5 removes its targets;
   - account removal deleting that account's DMs from the store;
   - the retention policy and its scheduling;
   - `LocalRelayViewModel` and the in-process `Nip86Executor`.
3. **Desktop, in-process with no cap:** wire the host into `DesktopAppModules`; run the app
   headless (`tools/desktop-run/`), browse for a while, restart with the network off and check
   the feeds come back from the local relay; measure how much a day of use grows the store.
4. **commonsUI, the management screen:** Overview and Browse first, then Settings. Check it on
   the desktop app at phone width (tabs) and wide (two panes), with screenshots.
5. **Desktop, exposure:** "Expose to other apps" with the NIP-42 policy; connected sessions in the
   screen; the jlink check on the packaged build.
6. **Android, in-process:** the host behind a setting, after measuring disk use per event and the
   cold-start time with a full store; the budget and pruning on.
7. **Android, exposure:** the loopback server tied to `NotificationRelayService`, with the FGS
   wording and Play review.
8. **Later, if asked for:** a read-only content provider with per-app grants; unauthenticated
   public reads.

## Open questions

- Android's default budget and whether full-text search is on there.
- Whether `KtorRelay` moves to a module both platforms share, or Android gets its own wrapper.
- The default port.
- The channel limit before the ingest hook starts dropping, once Desktop growth is measured.
