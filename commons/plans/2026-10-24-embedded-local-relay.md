# Embedded local relay (Desktop, then Android)

_2026-10-24. Proposed, not started. Nothing here has been prototyped or measured on a device;
the code references were read from this repo and from a shallow clone of vitorpamplona/quartz
on this date._

## Goal

Every Amethyst install carries its own Nostr relay that starts with the app:

- **Desktop** keeps all of the user's data and more, with no size cap by default, and can expose
  the relay to other apps on the machine (`amy`, other clients, a second profile).
- **Android** keeps a bounded cache that prunes itself, and can optionally expose the relay to
  other apps while the always-on notification service runs.

Both use the same code: quartz's relay engine and SQLite store, one host in `commons`, and one
retention policy whose budget differs per platform.

## What already exists

| Piece | Where | Use |
| --- | --- | --- |
| Local relay list | `settings.localRelayServers` → `Account.localRelayList` (`commons/.../model/localRelays/LocalRelayListState.kt`) | Already merged into the home, outbox, mine, DM-inbox, notification and default global relay sets (`Account.kt`, the `*RelayState` wiring). Built for Citrine. A URL in this list is written to and read from with no feed changes. |
| Kind-0 forwarding | `ForwardKind0ToLocalRelayState` | Already copies profiles to local relays. |
| Local Relays screen | `commonsUI/.../relays/local/` | Where the built-in relay shows up as a row. |
| Relay engine + store | quartz: `nip01Core/relay/server/NostrServer`, `nip01Core/store/sqlite/EventStore` | KMP, runs on Android and JVM. NIP-09, NIP-40, NIP-62, NIP-77 and NIP-86 are all here, not in geode. |
| In-process transport | quartz: `relay/server/inprocess/InProcessWebSocket` | Plugs a `NostrServer` into `NostrClient` through a `WebsocketBuilder`, with no socket. Used by geode's `InProcessRelays` and the desktop test fixture `LaunchFixtureRelay`. |
| Trusted ingest | quartz: `NostrServer.ingest(event, skipVerify)` | Writes an event straight into the store and the live fan-out, skipping the connection's policy chain. Lets the app store events it already verified without paying Schnorr verification again. |
| geode | `geode/` (about 1,800 lines of main code) | Adds on top of quartz: the Ktor CIO socket server (`KtorRelay`), TOML config, `MirrorWorker` (negentropy backfill), NIP-11/NIP-86 HTTP routes and the public-relay index strategy (`RelayIndexingStrategy`). |
| Embedding example | `cli/.../commands/ServeCommand.kt` (`amy serve`) | `RelayEngine(url, store = EventStore(...), adminPubkeys = ...)` + `KtorRelay(...).start()` + a shutdown hook. |
| Tor bypass for loopback | `commons/.../tor/TorRelayEvaluation.kt` | `isLocalHost()` relays are always dialled directly. |
| Always-on Android service | `amethyst/.../service/notifications/NotificationRelayService.kt` | `specialUse` foreground service with START_STICKY, a watchdog, boot and WorkManager restarts. |
| In-memory pruning rules | `commons/.../model/cache/CachePruner.kt` | Which events matter to the account in `LocalCache`. A starting point for the disk policy (it works on `Note`s, so follow its logic rather than share code). |

## Design

### 1. Shared host (`commons`)

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

**Decision needed: what goes in the store.**

- *Option A, only what the relay lists already route:* the user's own events, DMs and kind-0
  forwarding. Same as Citrine today; no new hook.
- *Option B, everything the app verifies:* every event `LocalCache` accepts, written with
  `server.ingest(event, skipVerify = true)`. This gives a real offline cache and is what "Desktop
  keeps all of the user's data and more" needs.

Recommended: B, bounded by the retention policy on Android. It needs a **lossless** hook at
consume time. `LocalCache.live.newEventBundles` is not one: it is a `MutableSharedFlow` with a
100-item `DROP_OLDEST` buffer, so it drops events under load.

### 2. Index strategy

- Desktop: `relayIndexingStrategy(fullTextSearch = true, liveNegentropyIndex = false)` from geode.
  The live negentropy index costs about 140 B of heap per event and only pays off for a public
  relay serving full-corpus NEG-OPENs. Keep full-text search: offline search of your own history
  is a main reason to have this relay.
- Android: quartz's client-tuned `DefaultIndexingStrategy` (no created-at-only, author-only or
  tag+kind+author indexes). Decide on full-text search after measuring its size.
- Measure bytes per event under both strategies before choosing Android's default budget.

### 3. Desktop

- `desktopApp` depends on `:geode` for `KtorRelay` and `MirrorWorker` (as `cli` does).
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
- **Backfill** (optional): `MirrorWorker` with negentropy pulls the user's history from their
  NIP-65 write relays once ("download my data"), the same code geode runs like `strfry sync`.
- Packaging: the jlink list in `desktopApp/build.gradle.kts` has `java.sql` and
  `java.management`. Compared with geode's own list it lacks `java.naming`, `java.net.http`,
  `java.security.jgss` and `jdk.crypto.ec`, mostly for `MirrorWorker`'s TLS upstreams. Boot the
  packaged build once to catch a `NoClassDefFound`.
- Licensing: geode brings Ktor (Apache-2.0) and 4koma. Confirm 4koma's license from its
  `LICENSE` file before it ships in the desktop binary.

### 4. Android

**In-process store (step 1 on Android).** The shared host, built in the **main** process only.
`:napplet` never touches it (it has no `Amethyst.instance`). No service and no socket are
needed. `:geode` does not go into the APK: it pulls in the JVM-native SQLite driver, OkHttp
mirror workers and 4koma, and its value is the TCP server.

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

The store holds gift wraps and DMs, and the loopback port is reachable by any process on the
device. The in-process path doesn't have this problem; the socket does.

- Private kinds (gift wraps, DMs) are only served to a connection authenticated with NIP-42 as
  the recipient. Other apps authenticate through their own signer, such as Amber. geode already
  does auth-gated DM delivery (`geode/src/test/.../Nip42AuthDmDeliveryTest.kt`).
- Writes: limited to the logged-in pubkeys, or rate-limited.
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
4. **Sync downloads pruned events again.** A negentropy backfill or `MirrorWorker` window that
   reaches past the retention period re-fetches the pruned events every time. *Rule:* sync and
   backfill windows use `since = now - retention`.

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
  defaults to no cap but still applies NIP-40 expiry and drops strangers' events.
- When it runs: on Android from a WorkManager job or the always-on service's tick; on Desktop
  from an idle timer. Delete in small batches, each in its own transaction: the store has a
  single writer lock, and one large delete would hold up the inserts the UI is waiting on.
- Amethyst needs nothing new when an old event is gone locally: it fetches it from remote relays
  as it does today.

## Work order

1. **quartz:** incremental vacuum, bytes-in-use reporting, and `prune(...)` with the tombstone and
   newest-version guarantees, with tests for each guarantee (delete a kind 5's target, prune, and
   check the target is still rejected).
2. **commons:** `LocalRelayHost`, the routing `WebsocketBuilder`, `platformLocalRelays` merged
   into `LocalRelayListState`, the lossless ingest hook, the retention policy and its scheduling.
3. **Desktop:** wire the host into `DesktopAppModules` with no cap; the Local Relays screen row and
   toggle; run the app headless (`tools/desktop-run/`) and check that feeds come back from the
   local relay after a restart with the network off.
4. **Desktop:** "Expose to other apps" with the NIP-42 policy; optional `MirrorWorker` backfill;
   jlink module check on the packaged build.
5. **Android:** the in-process host behind a setting, after measuring disk use per event and the
   cold-start time with a full store.
6. **Android:** the loopback server tied to `NotificationRelayService`, with the FGS wording and
   Play review.
7. **Later, if asked for:** a read-only content provider with per-app grants.

## Open questions

- Option A or B for what goes into the store (recommended: B).
- Android's default budget and whether full-text search is on there.
- Whether `KtorRelay` moves to a module both platforms share, or Android gets its own wrapper.
- The default port.
