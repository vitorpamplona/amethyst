# QuicConnection Lock Split — Design Note

Date: 2026-05-08

## Problem

`QuicConnection.lock: Mutex` serialises every meaningful operation:

  - `drainOutbound` (send loop, holds lock during a full datagram build —
    iterates every stream, allocates packet numbers, encrypts).
  - `feedDatagram` (read loop, holds lock during decrypt + frame dispatch
    + per-stream insert).
  - `openBidiStream` / `openUniStream` (app code, holds lock for stream
    allocation + map insert).
  - `getOrCreatePeerStreamLocked` (parser path on the read loop's
    critical section, but app code can also call it from tests).

Multiplexing test against aioquic measures ~25 streams/sec — every
coroutine fights this single mutex.

## Goal

Split the mutex into per-domain mutexes so the read loop, send loop, and
app code can mostly progress concurrently. Per-stream `synchronized(this)`
inside `SendBuffer`/`ReceiveBuffer` already handles per-stream
serialisation; we don't touch those.

## Domain Map

### Domain A — `streamsLock: Mutex` (the streams registry)

Fields:

  - `streams: MutableMap<Long, QuicStream>`
  - `streamsList: MutableList<QuicStream>` (insertion-ordered list parallel
    to `streams`, used by writer round-robin)
  - `nextLocalBidiIndex`, `nextLocalUniIndex`
  - `streamRoundRobinStart` — read+written by writer; used in the
    same critical section it holds `streamsLock` for the iteration
  - `peerInitiatedUniCount`, `peerInitiatedBidiCount`
  - `advertisedMaxStreamsUni`, `advertisedMaxStreamsBidi`,
    `advertisedMaxData`
  - `pendingMaxStreamsUni`, `pendingMaxStreamsBidi`, `pendingMaxData`
  - `pendingMaxStreamData: MutableMap<Long, Long>`
  - `pendingNewConnectionId: MutableMap<Long, …>`
  - `newPeerStreams: ArrayDeque<QuicStream>`
  - `pendingDatagrams: ArrayDeque<ByteArray>` — outbound DATAGRAMs
  - `incomingDatagrams: ArrayDeque<ByteArray>` — inbound DATAGRAMs
  - `sendConnectionFlowCredit`, `sendConnectionFlowConsumed`
  - `receiveConnectionFlowLimit`

Rationale: the writer needs an atomic snapshot of "all streams + all
pending control-frame retransmits + datagram queues + flow-control
counters" in one critical section to assemble a packet. The parser needs
the same coverage when delivering a STREAM frame (look up or create
the stream + queue receive bytes + bump pending* fields). Splitting
these into multiple sub-locks would force the writer/parser to acquire
several locks per pass — same contention, more deadlock risk.

`peerMaxStreamsBidi`, `peerMaxStreamsUni` stay `@Volatile` (already are):
the writer reads them once at the top of a stream open; the parser
writes once on inbound MAX_STREAMS. Atomic long write is sufficient on
all supported platforms.

### Domain B — `LevelState.levelLock: Mutex` (one per level: initial / handshake / application)

Fields per `LevelState`:

  - `pnSpace: PacketNumberSpaceState`
  - `sentPackets: MutableMap<Long, SentPacket>`
  - `ackTracker`
  - `cryptoSend: SendBuffer`, `cryptoReceive: ReceiveBuffer`
  - `sendProtection: PacketProtection?`, `receiveProtection: PacketProtection?`
  - `keysDiscarded`
  - `largestAckedPn`, `largestAckedSentTimeMs`

The writer iterates through levels in order (initial → handshake →
application) when building a coalesced datagram. Each level's critical
section is independent, so the lock is held only for the duration of
build at that level (which doesn't touch the streams registry except
to read `streamsListLocked()` for stream frames inside the application
build — that read transitions through `streamsLock`).

### Domain C — `lifecycleLock: Mutex` (status + handshake metadata)

Fields:

  - `status: Status`
  - `closeReason: String?`, `closeErrorCode: Long`
  - `peerTransportParameters: TransportParameters?` — read-mostly after
    handshake; using `@Volatile` reference + write-once-after-handshake
    is sufficient here. Promoted to `@Volatile` so writer/parser can
    snapshot without a lock.
  - `handshakeComplete: Boolean`
  - `closeAllSignals` (the channels are themselves thread-safe; lock is
    only required to serialise the status transition)

### Domain D — Atomic / `@Volatile` (no lock)

Fields:

  - `pendingPing` — toggled by driver under PTO; observed by writer.
    Promote to `@Volatile`.
  - `consecutivePtoCount` — already `@Volatile`. Driver writes it under
    its own logic; no further protection needed because it's only read
    inside the same loop iteration that wrote it.
  - `destinationConnectionId` — already has volatile semantics
    (`internal set` on a `@Volatile var`). Stays as is.
  - `udpStatsSupplier` — already `@Volatile`.
  - `peerMaxStreamsBidi`, `peerMaxStreamsUni` — already `@Volatile`.
  - `handshakeDoneSignal: CompletableDeferred<Unit>` — coroutines
    primitive, thread-safe.

### Domain E — Per-stream (UNCHANGED)

`QuicStream` already protects its `SendBuffer` / `ReceiveBuffer` with
internal `synchronized(this)` blocks. Nothing changes here.

## Lock Acquisition Order

To prevent deadlock, document and enforce:

```
lifecycleLock < streamsLock < (any LevelState.levelLock)
```

Per-stream `synchronized(...)` blocks inside `SendBuffer`/`ReceiveBuffer`
remain at the leaf — never acquire any QuicConnection mutex while
holding a per-stream lock.

In practice the only nesting that happens is:

  - `drainOutbound` acquires `streamsLock` (for the streams loop +
    stream-frame creation) but the per-level builds happen *outside*
    that block — each level acquires its own `levelLock` separately.
    No nested `streamsLock` ⊃ `levelLock` chain.
  - Actually re-checking the design: the writer needs to allocate a
    PN at the chosen level *while* it has decided which streams to
    flush. Two options:
      (1) acquire streamsLock, snapshot streams + frames, release;
          acquire each levelLock to encode + record.
      (2) hold streamsLock during level encode for the application
          packet (because stream-frame retransmit tokens get recorded
          into level.sentPackets in the same operation).
    We take option (2) — encode under both locks, with strict order
    `streamsLock` → `levelLock`. The other levels (initial/handshake)
    don't touch streamsLock at all, so they only acquire `levelLock`.

## Public API Compatibility

`QuicConnection.lock: Mutex` is `val`-public. External callers exist
(tests + InMemoryQuicPipe-driven harnesses). To avoid breaking those:

  - Keep the `lock: Mutex` field as a deprecated forwarder. It now
    *also* exists, but it is an alias for `lifecycleLock`. New code
    must NOT use it. Existing tests that lock it before mutating
    state used to cover all domains; we update them in place to use
    the appropriate lock(s).

Actually simpler: keep `lock: Mutex` as a *no-op* lock (still a
`Mutex` so external code compiles), document that it no longer
guards anything, update the tests that lock it.

After review: tests use `conn.lock` to serialise their direct calls to
`onTokensAcked`/`onTokensLost`/`getOrCreatePeerStreamLocked`. We update
those tests to acquire `streamsLock` instead (since those routines
mutate stream-domain state). The `lock` field is kept as deprecated
for source compatibility but is functionally a leaf no-op.

## Migration Plan

1. Add `streamsLock`, `lifecycleLock` fields. Keep `lock` as alias of
   `lifecycleLock`.
2. Add `levelLock` to `LevelState`.
3. Convert `getOrCreatePeerStreamLocked` → `getOrCreatePeerStream` doing
   its own `streamsLock` acquisition. Keep the old name as a forwarder
   for backwards compat.
4. Update `openBidiStream`, `openUniStream`, `streamById`, `pollIncomingPeerStream`,
   `awaitIncomingPeerStream`, `pollIncomingDatagram`, `awaitIncomingDatagram`,
   `queueDatagram`, `flowControlSnapshot` to acquire `streamsLock`.
5. Update `close`, `markClosedExternally` to use `lifecycleLock`.
6. Update driver's `readLoop`/`sendLoop`:
     - `feedDatagram` no longer wraps in conn-wide lock. Instead the
       parser acquires `streamsLock` around stream-touching code,
       and `levelLock` around level-touching code.
     - `drainOutbound` is restructured similarly.
7. Update tests that hold `conn.lock` to use the relevant new lock.

## Risk + Mitigation

  - **Deadlock**: enforce order via code review + (where practical)
    inline comments at each acquisition site. Keep nesting shallow.
  - **Missed coverage**: enumerate every field in this doc; if a field
    can be mutated from two domains we either move it to a single domain
    or annotate it as @Volatile.
  - **Performance regression**: more mutex acquisitions overall; but
    the critical path (multiplexing test) sees parallel execution
    instead of serial, which more than compensates.

## Implementation Phases

This commit implements **phase 1** — separate domain locks but
`drainOutbound` and `feedDatagram` still hold `streamsLock` for the
entire pass. The wins from phase 1 alone:

  - App code (`openBidiStream`, `streamById`, `flowControlSnapshot`) no
    longer contends with `lifecycleLock`-only operations.
  - The PTO timer path stops touching any mutex (volatile fields).
  - `markClosedExternally` no longer needs a lock.
  - `close()` only takes lifecycleLock — opens the path for in-progress
    drain to finish without status-write contention.

Phase 2 (deferred follow-up): split `buildApplicationPacket` into a
"collect frames under streamsLock" stage and an "encrypt + record
under levelLock" stage so app coroutines can intersperse during the
encrypt window. That requires more invasive surgery on the writer's
internals; phase 1 ships first to lock in the safer subset.

## Verification

  - `:quic:jvmTest` — full suite must pass.
  - `MultiplexingThroughputTest` (new): 1000 streams in <500 ms on
    InMemoryQuicPipe.
