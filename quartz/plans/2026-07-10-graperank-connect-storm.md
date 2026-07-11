# GrapeRank crawl: connect once, wait once (connection storm)

## Problem

A from-scratch `amy graperank crawl` spends most of its wall clock **waiting to
connect** to relays, not downloading. Three compounding causes, all on our side:

1. **Sockets are torn down between drains.** `NostrClient` reconciles the relay
   pool against the union of *active* subscriptions (`RelayPool.updatePool`,
   sampled every 300ms). A Phase-B drain unit unsubscribes when it finishes, so
   an outbox relay with no other in-flight drain is **disconnected ~300ms
   later** — and the next round re-pays DNS + TCP + TLS + WS-upgrade for the
   same relay. Only the top-20 "warm pool" relays kept a do-nothing
   subscription open. A crawl touches ~10–15k distinct relay URLs; most get
   dialed many times.
2. **Handshake concurrency was capped at 256** (OkHttp `Dispatcher.maxRequests`;
   the OkHttp default is 64). Every WS upgrade is an async call holding a
   dispatcher slot for its whole DNS + TCP + TLS + upgrade; a dead relay holds
   a slot for the full 7s `connectTimeout`. At 256 slots, dialing 5k relays
   where half are dead is ~20 serialized waves — minutes of pure waiting that
   could be one parallel wait.
3. **DNS is blocking, uncached and duplicated.** OkHttp's `Dns.SYSTEM` calls
   `InetAddress.getAllByName` (glibc `getaddrinfo`) on the dispatcher thread.
   The JVM's own cache is ~30s positive / ~10s negative — useless across a
   30-minute crawl that re-dials the same hosts every round. Worse, the outbox
   model mints hundreds of per-user *URLs* on one *host*
   (`filter.nostr.wine/npubA`, `/npubB`, …) and each URL is a fresh lookup. A
   dead domain is the worst case: resolver timeouts (5s × 2 attempts per
   nameserver) can hold a thread 10–30s, and the crawl re-resolves it from
   every straggler's outbox, every round.

## Single-server limits (what bounds "1000s of connections at once")

Measured/derived for a JVM CLI on one Linux box:

| Resource | Limit | Consequence |
|---|---|---|
| File descriptors | `ulimit -n` soft (often 1024–4096; systemd default 1024 soft / 512k+ hard) | 1 FD per socket. The JVM cannot raise its own rlimit — detect via `UnixOperatingSystemMXBean` and size the storm to fit; advise `ulimit -n 16384` for big crawls. |
| Ephemeral ports | ~28k (`ip_local_port_range`) **per destination tuple** | Non-issue: connections go to thousands of *distinct* relays, each gets its own tuple space. |
| Concurrent TCP connects | Kernel: effectively unbounded (a pending SYN is just a socket in `SYN_SENT`) | Dead-IP dials cost nothing in CPU but hold their FD + dispatcher slot for the connect timeout. Parallelism is the fix; refused/reset fails in 1 RTT. |
| DNS | glibc `getaddrinfo` is blocking, ~1 thread per in-flight lookup; dead domains 10–30s | Cache positives AND negatives in-process; dedupe concurrent lookups for the same host (path-URL explosion). |
| Threads | 1 platform thread per in-flight handshake (OkHttp) | 1–2k concurrent handshakes ≈ 1–2k transient threads — fine on a 64-bit JVM (~stack is virtual), but don't go to 10k. |
| TLS | CPU per handshake (~1ms) | Negligible next to network RTTs; OkHttp shares an SSL session cache per client. |
| Middleboxes | Home-router NAT/conntrack tables (~4–16k entries) | Operational caveat for residential operators; server/VPS operators unaffected (`nf_conntrack_max` default ~256k). |

Conclusion: with FDs raised and DNS cached, a single server comfortably opens
**a few thousand concurrent connections**; the binding constraints are the FD
soft limit and the dispatcher cap, both of which we now size/raise explicitly.

## Design

Three changes, all keeping completeness identical (same relays asked the same
filters — only *when* sockets open changes):

1. **Crawl-wide warm pool = mass pre-connect.** The existing warm-pool trick
   (a never-matching `ids` filter that EOSEs instantly and just holds the
   socket) is extended from the top-20 relays to **every candidate relay we
   know** — seeded at crawl start from the NIP-66 reachability cache's live
   set, and refreshed every round with the relays learned from kind:10002s —
   capped by `preconnectCap` (FD-budget-aware, busiest relays first, dead
   relays excluded). Connections open in one parallel storm at start (and in
   the background as new relays are learned), stay up for the whole crawl, and
   every drain hits an already-open socket. This also implements the "crawl
   once with --max-hops to save the relay list" flow: the first (even shallow)
   crawl populates the store with 10002s and the reachability cache with
   live/dead verdicts; the next crawl pre-connects that whole universe and
   waits once.
2. **Transport ceilings raised (CLI).** `Dispatcher.maxRequests` 256 → 1024
   (per-host stays 16 — politeness to path-multiplexed hosts is a *server*
   property), and the app's `SurgeDns` (moved to quartz; stale-while-revalidate,
   single-flight, 10-min negative TTL, persisted under `~/.amy/shared/`)
   replaces `Dns.SYSTEM`. FD budget is detected at startup and sizes both the
   dispatcher and the default `preconnectCap`. Caveat: in a proxied environment
   (HTTP CONNECT), OkHttp sends the hostname to the proxy and never consults
   the client-side resolver — so the DNS layer only pays off on direct-connect
   deployments, and none of the A/B numbers below depend on it.
3. **`amy graperank probe` — the relay census.** Reads the full known relay
   universe (every relay in stored kind:10002s + everything in the
   reachability cache), mass-connects it in waves with a never-matching REQ,
   and records per-relay verdicts with **real** `rtt-open` into the NIP-66
   store: connected → live (with measured RTT, however slow), cannot-connect /
   never-completed → dead (TTL'd, re-probed after expiry). Separates
   "working but slow" (kept, given the crawler's patient park path) from
   "not working" (skipped entirely) without burning crawl time on the
   distinction.

## Measured A/B results (cold hop-3 crawl, fresh store per leg, same observer)

Sequential single-variable legs on the same 4-core / 4096-FD box. Completeness
gate: `contact_lists_fed` and `users_discovered` must not drop (±2% network
noise between runs is normal — relays come and go).

| leg | wall | lists fed | users found | verdict |
|---|---|---|---|---|
| baseline (all connect-storm work, 24 workers) | 638s | 18,519 | 165k | reference |
| + background parks don't gate convergence | 579s | 18,829 | 167k | **keep** |
| + `--drain-concurrency 48` | 559s | 18,817 | 169k | keep (confirmed below) |
| + `--timeout 5` (shorter fast window) | 735s | 18,524 | 164k | **reject** — more parks/retries |
| + sharded-sweep dedup | 675s* | 18,801 | 168k | keep — round wall ↓ (301s vs 336s); total noise |
| + overlap tail (no convergence park-wait, agg ∥ deletions) | 474s | 18,816 | 167k | **keep** |
| + `--drain-concurrency 48` on top | **396s** | 18,816 | 167k | **keep → new default** |
| + per-relay sub cap 16→32 (`AMY_RELAY_SUB_CAP`) | 602s | 18,478 | 164k | **reject** — 27 relays demoted us + 7 rate-limited (vs 5+1 at 16); the Phase-B plateau is relay-side service rate, not client permits |

### Reproducibility + the recovery fixpoint

A cold hop-11 rerun matched hops 1–4 within ~3% but lost the entire hop-6/7
cluster (394k vs 621k discovered at −7.5% lists): one weak backbone hour at
round 5 shifted ~30k lists from in-round arrival to the terminal aggregator
pass, whose discoveries were never crawled. Fixed by iterating rounds +
recovery to a fixpoint (recovery reveals in-budget pending → resume rounds →
recover again). Validation, cold hop-8 on the fixpoint build: **796,459
discovered / 253,213 lists** — the deepest crawl of the series (hop 5–8
populations 107k/167k/131k/72k vs 67k/110k/127k/2k before); the fixpoint fired
live (+25 recovered lists → 1 pending → extra round → +0 → converge). Known
follow-ups: each fixpoint iteration re-asks aggregators for the FULL straggler
set (~4 min per pass at 470k stragglers — dedupe to newly-added stragglers),
and the retry rounds' backbone re-fan remains the dominant tail cost (rounds
8–10 ≈ 80 min for +6.6k lists).

Final validation — **cold hop-5, fresh store, winning stack** (the workload that
never completed pre-fixes): **85.4 min, 203,903 contact lists, 391,549 users
discovered, 626,599 events, 5,794 relays**. Per-hop list completeness: 90%
(hop 2), 71% (hop 3), 56% (hop 4). Remaining known lever for a future pass:
attempt>0 retries re-fan every user to the whole backbone inside per-batch
units, so the two retry rounds cost nearly as much as first passes at a
fraction of the yield — route retry backbone re-asks as round-wide chunked
per-relay units instead.

### Retry routing: the round-wide communal sweep (Phase C)

That "remaining lever" landed as a restructure of Phase B's shared-relay
coverage. Before: every 256-user batch with retry (attempt>0) or no-outbox
users fanned the SAME ~40 backbone+fallback relays, queueing thousands of
small REQs against their 16-permit gates — measured as rounds 8–10 of a hop-8
crawl burning ~80 min for +6.6k lists, nearly all hot-relay head-of-line
blocking. After: `routeByOutbox` routes only a user's OWN-signal relays
(write relays; harvested hints when there's no outbox) and flags
communal-coverage users into a per-round set; a new **Phase C**
(`communalSweep`) asks the shared relays ONCE per round for all flagged users
in author-chunked units — the same (relay, user) pairs, ~8x fewer REQs — and
records answered-empty pairs so each subsequent round shrinks. Attempt
accounting moves to Phase C (a user burns an attempt only after both its own
relays and the shared set missed that round), `shardedSweep` now records
answered-empty pairs too, and each recovery-fixpoint iteration only asks the
aggregators for stragglers ADDED since the previous pass (a repeat full-set
pass cost ~4 min at 470k stragglers for zero new answers).

Validation, cold hop-8, same observer (reference = the fixpoint build's
232.6 min / 253,213 lists / 796,459 users): **160.7 min, 292,768 lists,
1,169,194 users discovered, 7,240 relays** — 31% less wall clock with +15.6%
lists and +47% users. The depth cliff moved out two hops: hop-7/8 populations
319,878/292,268 vs 131,051/72,389, hop-7 lists 29,445 vs 6,117. Round-level
profile: the retry rounds that defined the old tail now run in minutes
(round 8: 247s total, of which Phase C fed 5,695 of its 6,080 lists), and
Phase C itself costs 10–170s per round, folded out of the per-batch hot path.

\* total inflated by a slow round-3 network sample + the then-serial tail; the
change's own effect (straggler-round Phase A 20s → 20ms) is visible directly.

Not measurable in these legs but fixed on the way (validated separately):
- the whole crawl ran on `runBlocking`'s single-threaded event loop — thousands
  of Phase-B unit coroutines starved one core while three idled, inflating
  batch walls ~5x (`crawl()` now hops to `Dispatchers.IO`); this also
  invalidated the old "64 workers is 2x slower" A/B;
- per-user store point queries (~8ms each on a multi-GB store) in
  harvestFromStore / sweep / consumer / aggregator paths — now one chunked
  author query per 300 users (hop-3 replay Phase A: 176s → 36s at identical
  fed counts).

## Non-goals

- No change to REQ concurrency per relay (`AdaptiveRelayLimiter`) or the
  drain worker count — the 64-worker A/B showed the *REQ* fan-out re-floods
  busy hubs; this work only parallelizes and amortizes *connection setup*.
- No async-DNS library dependency; cached blocking lookups on OkHttp's
  existing threads are sufficient once deduped and negative-cached.
