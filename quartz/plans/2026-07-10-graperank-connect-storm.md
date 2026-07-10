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

Final validation — **cold hop-5, fresh store, winning stack** (the workload that
never completed pre-fixes): **85.4 min, 203,903 contact lists, 391,549 users
discovered, 626,599 events, 5,794 relays**. Per-hop list completeness: 90%
(hop 2), 71% (hop 3), 56% (hop 4). Remaining known lever for a future pass:
attempt>0 retries re-fan every user to the whole backbone inside per-batch
units, so the two retry rounds cost nearly as much as first passes at a
fraction of the yield — route retry backbone re-asks as round-wide chunked
per-relay units instead.

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
