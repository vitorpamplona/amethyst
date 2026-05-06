# Stream priority for moq-lite group uni streams (T11.3 follow-up)

**Status**: deferred — flagged in T11 (drop `bestEffort=true`), not landed.

## Why

`T11` removed `bestEffort=true` from `MoqLiteSession.openGroupStream`
because the QUIC contract it relied on (drop lost ranges silently
without `RESET_STREAM`) created undeliverable streams that wedge
peer reassembly buffers — the actual user-visible bug was a 30 s
silent dropout per lost packet on lossy networks.

`bestEffort=true` was incidentally providing a "newer-groups-skip-
queued-retransmits" effect: the writer never queued retransmits in
the first place, so under congestion the loss budget naturally
biased toward dropping older lost ranges. With `bestEffort=true`
gone, all retransmits are now queued, and under sustained
congestion the writer drains streams in `streamRoundRobinStart`
order — which can mean the listener catches up on a stale group
when a fresh one would be more useful.

The kixelated reference (`moq-rs`'s `Publisher::serve_group` in
`rs/moq-lite/src/lite/publisher.rs:347-406`) addresses this by
calling `stream.set_priority(priority.current())` on every group
stream and biasing the writer toward higher-priority (newer)
streams. We should do the same.

## Target shape

### `:quic` API

Add a stable priority hook to `QuicStream`:

```kotlin
class QuicStream(...) {
    @Volatile
    var priority: Int = 0
        // Higher drains first under contention. Default 0 = unchanged
        // round-robin behaviour.
}
```

Expose at the WebTransport layer:

```kotlin
interface WebTransportWriteStream {
    fun setPriority(priority: Int)
}
```

### `QuicConnectionWriter` — the load-bearing change

Today's send-frame loop (`QuicConnectionWriter.kt:411-416`):

```kotlin
val streamsView = conn.streamsListLocked()
val start = conn.streamRoundRobinStart % streamsView.size
for (i in streamsView.indices) {
    val stream = streamsView[(start + i) % streamsView.size]
    // ...
}
```

Replace with priority-then-round-robin:

```kotlin
val streamsView = conn.streamsListLocked()
val sorted = streamsView.sortedByDescending { it.priority }
val start = conn.streamRoundRobinStart % sorted.size
for (i in sorted.indices) {
    val stream = sorted[(start + i) % sorted.size]
    // ...
}
```

Sort is stable; same-priority streams retain insertion order, so the
existing round-robin behaviour holds within a priority tier. Higher-
priority streams always come first in the iteration.

Cost: O(N log N) per drain pass, where N is active local-initiated
streams. N is small (1–10 in the moq-lite audio path); the
allocation of `sorted` per pass is the only real cost. If that
shows up in profiling, switch to `kotlin.collections.IntArray`-
backed indirect sort or maintain a priority-sorted list incrementally
on `setPriority` calls.

### moq-lite wiring

`MoqLiteSession.openGroupStream:1022-1037`:

```kotlin
internal suspend fun openGroupStream(
    subscribeId: Long,
    sequence: Long,
): WebTransportWriteStream {
    val uni = transport.openUniStream()
    uni.setPriority(sequence.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    uni.write(Varint.encode(MoqLiteDataType.Group.code))
    uni.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId, sequence)))
    return uni
}
```

Newer groups have higher sequence → higher priority → drain first
under congestion. The saturation conversion handles broadcasts that
run long enough for `sequence` to exceed `Int.MAX_VALUE` (≈ 71
years at our 1 group/sec production cadence; defensive only).

## Test

Add a unit test in `:quic` that builds a `QuicConnection`, opens
two streams, sets `.priority = 0` on the first and `.priority = 1`
on the second, queues bytes on both, and verifies the higher-
priority stream's bytes hit the wire first. Doesn't need to fake
real flow-control backpressure — pinning the iteration order via
the writer's emitted-frames tape is sufficient to catch the
regression case ("a later refactor accidentally re-introduces
round-robin order").

A second test in `:nestsClient` that opens 3 group streams and
asserts later-sequence streams drain before earlier ones is
nice-to-have but secondary; the `:quic`-level test pins the load-
bearing invariant.

## Why deferred

The change is small in lines but touches the QUIC writer's hot
path. Rebasing a future `:quic` retransmit / pacing change onto a
priority-sorted iteration order is doable but the diff conflicts
get noisy. Better landed as a focused PR after the current
interop-work series stabilises.

Risk profile:
- **Bugs**: subtle starvation risk if a high-priority stream
  always has `streamRemaining > 0` — round-robin tiebreaker within
  a priority tier mitigates this for same-priority case, but the
  cross-tier case needs deliberate thought (do we want strict
  priority or weighted?).
- **Performance**: per-pass sort allocation. Negligible for N≤10
  but worth measuring if N grows.
- **Compat**: streams without an explicit priority default to 0,
  matching today's behaviour. Existing tests should pass unchanged.

## When to land

After the catalog interop series is fully verified (production
audio with no degradation under realistic conditions), and ideally
alongside a `:quic` perf review pass that touches the same code
path. Not blocking on any audio bug today — `T11`'s reliable-
delivery fix is the actual correctness change; this is the
spec-aligned hardening.
