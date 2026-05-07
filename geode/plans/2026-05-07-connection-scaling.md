# Connection scaling: pushing past 2 000

## Problem

Current measurement (`LoadBenchmark.connectionsHeldOpen`): **~2 000
concurrent connections** before file-descriptor pressure / Ktor CIO
event-loop saturation. Real-world relays (e.g. nostr.wine, nos.lol)
sustain 10–30k. Geode shouldn't be the bottleneck for an Amethyst-
adjacent operator who scales beyond a thousand-user community.

## What's spending memory per connection today

| Cost                    | Per connection                                           | At 5 000 conns |
| ----------------------- | -------------------------------------------------------- | -------------- |
| `outQueue` Channel      | 8 192 string slots × ~8 b ref                            | ~320 MB pinned |
| `RelaySession`          | `LargeCache<String, Job>` for subs (likely 1–10 entries) | ~negligible    |
| `NegSessionRegistry`    | `HashMap<String, NegentropyServerSession>` — usually 0   | ~negligible    |
| Ktor CIO buffers        | TCP read + write buffers                                 | ~10 MB         |
| Per-session writer Job  | one coroutine                                            | ~few KB        |

The `outQueue` reservation is the dominant cost. The 8 192 was sized
for a worst case "thousands of subscriptions, one event matches all" —
but at 5 000 connections we've over-provisioned by ~300 MB just on
the channel array, even though most connections never fan out.

## Sketch

### A — adaptive outQueue capacity

Start every connection with `INITIAL_OUTGOING_BUFFER = 64`. When the
producer side trySends and we observe queue depth crossing a high-water
mark (e.g. 75% full), grow the channel up to `MAX_OUTGOING_BUFFER =
8192`. This is not how `kotlinx.coroutines.channels.Channel` is
structured (capacity is fixed at construction), so the implementation
is "swap in a wider channel under a per-session lock when watermark
trips" — drains the old, then routes new sends through the new.

Expected: 90% of connections never fan out, so they stay at 64 slots
× ~512 B per ref ≈ 32 KB. At 5 000 conns that's ~160 MB → ~5 MB.
Hot-fanout connections still get the 2 MB cap.

### B — per-relay event-loop pool sizing

Ktor CIO defaults to one event-loop thread per available CPU.
Beyond a few thousand connections, this becomes the bottleneck — and
none of geode's per-connection work is CPU-bound (it's mostly waiting
on incoming frames). Tune CIO via:

```kotlin
embeddedServer(CIO, ...) {
    connectionGroupSize = max(2, Runtime.getRuntime().availableProcessors() / 2)
    workerGroupSize    = max(4, Runtime.getRuntime().availableProcessors())
    callGroupSize      = max(8, Runtime.getRuntime().availableProcessors() * 4)
}
```

Expose these through `RelayConfig.NetworkSection` so an operator on a
big VM can lift them.

### C — reduce per-message JSON allocations

`OptimizedJsonMapper.fromJsonToCommand` allocates a `JsonNode` tree per
incoming frame. At 10k connections with 1 msg/s each that's 10k tree
allocations/sec. Investigate streaming Jackson + reusing `ObjectMapper`
per session, or using kotlinx-serialization's lower-overhead path.

This is more of a quartz-level change than geode-specific, but
geode's load benchmark is the right place to measure it.

## How to verify

Add to `geode.perf.LoadBenchmark`:

- `connectionsHeldOpen10k` — opens 10 000 idle WebSocket connections;
  asserts no FD exhaustion + RSS stays under 1 GB.
- `connectionsHeldOpenWithFanout` — 5 000 idle subscribers,
  10 EPS published; measures p99 fanout latency at scale.

The current `connectionsHeldOpen` benchmark stays as the baseline
floor (~2 000 conns).

## Risks

- **Adaptive channel swap is fiddly**: drains under the producer's nose
  must preserve OK ordering. A simpler alternative: keep capacity fixed,
  but lazily allocate a small `ArrayDeque<String>` only when the first
  message is sent. Channels in kotlinx.coroutines do allocate up-front.
- **Bumping CIO group sizes can hurt**: more threads can mean worse
  L1/L2 locality. Always benchmark before/after, don't trust
  intuitive sizing.
- **OS-level FD limit**: per-process FD limit on Linux defaults to
  1024 in many environments. Document the `ulimit -n` requirement
  for operators targeting >1k connections.
