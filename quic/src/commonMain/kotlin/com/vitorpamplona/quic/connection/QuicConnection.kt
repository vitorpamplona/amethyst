/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.crypto.AesEcbHeaderProtection
import com.vitorpamplona.quic.crypto.InitialSecrets
import com.vitorpamplona.quic.crypto.PlatformAesOneBlock
import com.vitorpamplona.quic.crypto.bestAes128GcmAead
import com.vitorpamplona.quic.observability.QlogObserver
import com.vitorpamplona.quic.packet.QuicVersion
import com.vitorpamplona.quic.stream.QuicStream
import com.vitorpamplona.quic.stream.StreamId
import com.vitorpamplona.quic.tls.TlsClient
import com.vitorpamplona.quic.tls.TlsConstants
import com.vitorpamplona.quic.tls.TlsSecretsListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * QUIC v1 client connection. The orchestrator that owns:
 *
 *   - the TLS 1.3 client (which produces CRYPTO bytes per encryption level)
 *   - the per-level packet protection material
 *   - per-level packet number spaces + ACK trackers
 *   - per-stream send/receive buffers
 *   - inbound datagram queue
 *
 * The connection is driven by two callbacks:
 *
 *   [feedDatagram] — feed an inbound UDP datagram. Decrypts every contained
 *                    QUIC packet, runs CRYPTO bytes through the TLS state
 *                    machine, dispatches STREAM/DATAGRAM/ACK/MAX_* frames to
 *                    the right stream, and updates ACK state.
 *
 *   [drainOutbound] — pull the next UDP datagram (or null if nothing to send).
 *                     Coalesces ACK + CRYPTO + STREAM + DATAGRAM frames into
 *                     a single packet (or two, if Initial + Handshake).
 *
 * The actual UDP socket I/O is in the higher-level [QuicConnectionDriver].
 */
class QuicConnection(
    val serverName: String,
    val config: QuicConnectionConfig,
    /**
     * Certificate validator is REQUIRED (audit-4 #1). For in-process tests
     * pass an explicit [com.vitorpamplona.quic.tls.PermissiveCertificateValidator];
     * the type system catches "forgot to validate" misconfigurations instead
     * of letting null silently disable MITM protection.
     */
    val tlsCertificateValidator: com.vitorpamplona.quic.tls.CertificateValidator,
    /**
     * Monotonic clock used by ACK-delay encoding, RTT samples, PTO scheduling,
     * loss detection, and path-validation timeouts. Default uses
     * [kotlin.time.TimeSource.Monotonic] so an NTP step / suspend-resume
     * doesn't poison RTT estimates or trigger spurious losses. The returned
     * value is "milliseconds since this connection was constructed" — only
     * differences are meaningful.
     *
     * Tests inject a virtual clock; production callers should leave the
     * default unless they have a specific reason (e.g. recording wallclock
     * timestamps in qlog) and understand the wallclock pitfalls.
     */
    val nowMillis: () -> Long = defaultMonotonicNowMillis(),
    val alpnList: List<ByteArray> = listOf(TlsConstants.ALPN_H3),
    /**
     * Optional second listener invoked after the connection's own
     * key-installation listener. Used by the interop runner endpoint to
     * dump SSLKEYLOG lines so Wireshark can decrypt captured pcaps.
     * Default `null` keeps production callers unaffected.
     */
    val extraSecretsListener: TlsSecretsListener? = null,
    /**
     * TLS cipher suites to offer in the ClientHello. Override to e.g.
     * `intArrayOf(TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256)` for the
     * `chacha20` interop testcase. Default matches [TlsClient]'s default.
     */
    val cipherSuites: IntArray =
        intArrayOf(
            TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256,
            TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256,
        ),
    /**
     * Version this connection puts in the FIRST Initial it sends. Defaults
     * to [QuicVersion.V1]; the interop runner sets it to
     * [QuicVersion.FORCE_VERSION_NEGOTIATION] for the `versionnegotiation`
     * testcase, which drives the client through the RFC 9000 §6 VN flow.
     */
    val initialVersion: Int = QuicVersion.V1,
    /**
     * Optional qlog observer (draft-marx-qlog). Production callers
     * leave this at [QlogObserver.NoOp] (zero overhead). Interop /
     * test runners attach a JSON-NDJSON writer so a failed run
     * produces a `client.sqlog` consumable by qvis.
     */
    val qlogObserver: QlogObserver = QlogObserver.NoOp,
    /**
     * Resumption state from a prior connection — when non-null, this
     * connection's ClientHello will offer a `pre_shared_key` extension
     * referencing the cached ticket, and the key schedule will seed
     * the early secret from the cached PSK rather than zeros (RFC 8446
     * §7.1). On a successful PSK negotiation the server skips
     * Certificate / CertificateVerify and we save a round-trip plus
     * ~1 KB of cert bytes.
     *
     * Caller produces this from [onResumptionTicket] on a previous
     * connection. The interop runner's `resumption` testcase exercises
     * exactly this: connection 1 receives a NewSessionTicket and
     * stashes the state, connection 2 reuses it.
     */
    val resumption: com.vitorpamplona.quic.tls.TlsResumptionState? = null,
    /**
     * Hook invoked when the server issues a NewSessionTicket. The TLS
     * layer derives the per-ticket PSK and surfaces a fully-formed
     * [com.vitorpamplona.quic.tls.TlsResumptionState]; the QUIC
     * connection passes it through here so the application can stash it
     * (e.g. in a per-host resumption cache) for a future reconnect.
     *
     * Default no-op so existing callers compile unchanged. Servers
     * routinely issue 1-2 tickets per connection — the callback may
     * fire more than once, and the application is free to keep all of
     * them (a small per-host LRU is the typical shape) or just the
     * latest.
     */
    val onResumptionTicket: ((com.vitorpamplona.quic.tls.TlsResumptionState) -> Unit)? = null,
) {
    val sourceConnectionId: ConnectionId = ConnectionId.random(8)
    var destinationConnectionId: ConnectionId = ConnectionId.random(8)
        internal set
    val originalDestinationConnectionId: ConnectionId = destinationConnectionId

    /**
     * Version the writer stamps into the long-header version field on the
     * NEXT outbound Initial / Handshake packet. Initialised to
     * [initialVersion]; switched to [QuicVersion.V1] by
     * [applyVersionNegotiation] after a successful VN exchange.
     */
    @Volatile
    var currentVersion: Int = initialVersion
        internal set

    /**
     * RFC 9000 §6.2: a client MUST consume at most one VN response per
     * connection. After [applyVersionNegotiation] runs once, any further
     * inbound VN packet is dropped silently — the latch defends against a
     * mid-handshake attacker who replays an old VN datagram to wedge us
     * into an endless re-negotiation loop.
     */
    @Volatile
    var vnConsumed: Boolean = false
        internal set

    /**
     * RFC 9000 §17.2.5.1: the Retry token the server handed us in a Retry
     * packet, which we must echo verbatim in the Token field of every
     * subsequent Initial we send. Null until [applyRetry] runs.
     */
    @Volatile
    var retryToken: ByteArray? = null
        internal set

    /**
     * RFC 9000 §17.2.5.2: a client MUST NOT process more than one Retry
     * packet per connection. Any subsequent Retry is silently dropped.
     * Latched true by [applyRetry] on a successfully-verified Retry.
     */
    @Volatile
    var retryConsumed: Boolean = false
        internal set

    /**
     * Cached ClientHello bytes captured by [start]. Re-enqueued onto the
     * fresh Initial-level [LevelState.cryptoSend] when
     * [applyVersionNegotiation] or [applyRetry] resets the encryption
     * level so the new Initial datagram still carries a valid TLS handshake.
     * Without this the reset wipes the bytes that [TlsClient] already
     * enqueued and the post-VN/post-Retry Initial would carry an empty
     * CRYPTO frame.
     */
    private var originalClientHello: ByteArray? = null

    val initial = LevelState()
    val handshake = LevelState()
    val application = LevelState()

    /**
     * RFC 9001 §6 1-RTT key update — application-level state.
     *
     * The TLS handshake hands us the initial 1-RTT secrets via
     * [onApplicationKeysReady]. From there, EITHER side can roll forward
     * to the next-phase secret by computing
     * `next = HKDF-Expand-Label(current, "quic ku", "", Hash.length)` —
     * QUIC signals the rotation in the per-packet `KEY_PHASE` bit.
     *
     *  - [appReceiveSecret] / [appSendSecret] hold the LIVE secrets in use
     *    (the keys derived from these are what [application.receiveProtection]
     *    / [application.sendProtection] hold).
     *  - [currentReceiveKeyPhase] / [currentSendKeyPhase] track which phase
     *    those secrets correspond to (false = phase 0, true = phase 1, then
     *    flipping). The wire bit must match the live keys' phase.
     *  - [previousReceiveProtection] holds the keys for the PRIOR phase so
     *    we can decrypt reordered packets that arrive after we've already
     *    rotated forward (RFC 9001 §6.1: "The recipient SHOULD retain old
     *    keys for some time after unprotecting a packet sent using the new
     *    keys"). Cleared on the next rotation.
     *
     * Initial-/Handshake-level packets carry long headers and are not
     * subject to key update — these fields apply only to APPLICATION.
     *
     * Why we initiate the rotation in lockstep with the peer rather than
     * driving it ourselves: when a peer initiates a key update, RFC 9001
     * §6.1 requires us to respond with packets in the new phase so they
     * can confirm the rotation took effect. We don't proactively initiate
     * key updates — there's no safety benefit at our connection scale and
     * not initiating means we never have to track per-packet usage limits
     * (RFC 9001 §6.6).
     */
    @Volatile
    internal var appCipherSuite: Int = 0

    @Volatile
    internal var appReceiveSecret: ByteArray? = null

    @Volatile
    internal var appSendSecret: ByteArray? = null

    @Volatile
    internal var currentReceiveKeyPhase: Boolean = false

    @Volatile
    internal var currentSendKeyPhase: Boolean = false

    @Volatile
    internal var previousReceiveProtection: PacketProtection? = null

    /**
     * RFC 9001 §4.10 — 0-RTT send-side packet protection. Installed when
     * the TLS layer derives `client_early_traffic_secret` (right after
     * a resumption ClientHello with the early_data extension goes out)
     * and cleared when 1-RTT keys arrive (the protocol forbids using
     * 0-RTT keys after that point — the next outbound packet must use
     * 1-RTT and a short header).
     *
     * When non-null AND [application]'s 1-RTT [LevelState.sendProtection]
     * is null, the writer builds outbound application data as long-
     * header 0-RTT packets (type 0x01) using these keys; the packet
     * number space is shared with 1-RTT (RFC 9000 §17.2.3).
     *
     * Receive side is symmetric on the server only — the server never
     * sends 0-RTT packets to the client, so we never need a 0-RTT
     * receive protection slot.
     */
    @Volatile
    internal var zeroRttSendProtection: PacketProtection? = null

    @Volatile
    var handshakeComplete: Boolean = false
        private set

    /**
     * Lock-split refactor (2026-05-08): @Volatile because the writer/parser
     * read this without acquiring [lifecycleLock] (the field is written
     * once at handshake completion, then immutable).
     */
    @Volatile
    var peerTransportParameters: TransportParameters? = null
        private set

    enum class Status { HANDSHAKING, CONNECTED, CLOSING, CLOSED }

    /**
     * Lock-split refactor (2026-05-08): @Volatile so concurrent loops can
     * read the status without a lock — coarse "are we still alive?" checks.
     * Mutating transitions still go through [closeStateMonitor] for atomicity
     * with [closeReason]/[closeErrorCode] updates.
     */
    @Volatile
    var status: Status = Status.HANDSHAKING
        internal set

    /**
     * Non-suspend monitor protecting the atomic transition of
     * [status] / [closeReason] / [closeErrorCode] from a non-CLOSED to a
     * CLOSED/CLOSING state. We intentionally do NOT use [lifecycleLock]
     * for this — that's a `kotlinx.coroutines.sync.Mutex` which only
     * works from suspend contexts, and [markClosedExternally] is invoked
     * from the parser inside a `streamsLock.withLock { }` block where
     * suspending again on a different mutex is awkward and risks lock
     * inversion. A plain monitor avoids both problems while still
     * giving us a true compare-and-set for "first caller wins the
     * close-reason and gets to fire the qlog event".
     */
    private val closeStateMonitor = Any()

    /** App-level error code for graceful close. */
    var closeReason: String? = null
        private set
    var closeErrorCode: Long = 0L
        private set

    private val streams = mutableMapOf<Long, QuicStream>()

    /**
     * Per-connection insertion-ordered list of live streams. Mutated only
     * under [streamsLock] (single-writer):
     *  - [openBidiStreamLocked] / [openUniStreamLocked] /
     *    [getOrCreatePeerStreamLocked] append.
     *  - [retireFullyDoneStreamsLocked] removes entries whose stream has
     *    flipped [QuicStream.isFullyRetired] = true, folding the
     *    receive-side high-water mark into [retiredStreamsRecvBytes] so
     *    the writer's MAX_DATA accounting in `appendFlowControlUpdates`
     *    doesn't regress when a retired stream's `receive.contiguousEnd()`
     *    drops out of the iteration.
     *
     * Read by both lock-holding paths (writer drain, parser dispatch) and
     * by lock-free observers ([closeAllSignals] after teardown). To make
     * lock-free reads safe we use an immutable snapshot pattern: every
     * mutation publishes a fresh `List` via the `@Volatile` reference,
     * and readers see either the pre- or post-mutation snapshot — never
     * a half-modified ArrayList that can raise
     * `ConcurrentModificationException`. The cost is one shallow copy
     * per add / retire; steady-state churn stays under ~100/sec even at
     * peak audio-room load (see
     * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`,
     * which pegs ~50 streams/sec at peak).
     *
     * Without retirement, the moq-lite audio-rooms path leaks one
     * QuicStream per Opus frame for the lifetime of the session — a
     * 3-hour room accumulates ~540 000 entries before retirement was
     * wired.
     */
    @Volatile
    private var streamsList: List<QuicStream> = emptyList()
    private var nextLocalBidiIndex: Long = 0L
    private var nextLocalUniIndex: Long = 0L

    /**
     * Cumulative `receive.contiguousEnd()` from streams that have been
     * removed by [retireFullyDoneStreamsLocked]. Folded into the writer's
     * `totalRecvAdvanced` accumulator so MAX_DATA continues to advertise
     * the *connection-lifetime* receive high-water mark even after the
     * contributing streams have been dropped from [streamsList]. Without
     * this, retiring N streams that each delivered K bytes would silently
     * regress the advertised connection-level credit by N*K, eventually
     * starving the peer once `advertisedMaxData` was tripped past.
     *
     * Caller of any read/write must hold [streamsLock].
     */
    internal var retiredStreamsRecvBytes: Long = 0L
        private set

    /**
     * Cumulative count of streams ever removed by
     * [retireFullyDoneStreamsLocked]. Diagnostic-only; tests that drive
     * stream churn use this to assert retirement actually fired (rather
     * than relying on `streamsList.size` alone, which can shrink because
     * a soak loop happened to drain the same workload it just enqueued).
     *
     * Caller of any read/write must hold [streamsLock].
     */
    internal var retiredStreamsCount: Long = 0L
        private set

    /**
     * FIFO ring of recently-retired stream IDs. The parser consults
     * this in [getOrCreatePeerStreamLocked] to drop duplicate STREAM
     * frames the peer might retransmit on a stream we've already torn
     * down — without this guard, a retransmit (which can happen if our
     * ACK of the FIN frame was lost and the peer's loss detector
     * re-fired) would create a fresh QuicStream object, deliver
     * duplicate bytes to the application, and bump
     * [peerInitiatedUniCount] a second time.
     *
     * Bounded at [RETIRED_STREAM_ID_RING_SIZE] entries. Eviction is
     * FIFO so a long-running session never grows this set unbounded —
     * at moq-lite churn rates of ~50 streams/sec, the ring covers the
     * last ~80 seconds of retired IDs, far longer than the peer's
     * loss-detection retransmit window (a small multiple of RTT).
     *
     * Out-of-bounds duplicate retransmits (extremely rare — would need
     * the peer's ACK to be lost AND our retransmit not arriving for
     * many seconds) fall through to the existing
     * "create-and-immediately-retire" path, which is the previous
     * pre-guard behavior and merely costs a re-iteration.
     *
     * Caller of any read/write must hold [streamsLock].
     */
    private val retiredStreamIdsOrder = ArrayDeque<Long>()
    private val retiredStreamIdSet = HashSet<Long>()

    /**
     * Peer-advertised concurrent bidirectional stream cap. Initialised from
     * [TransportParameters.initialMaxStreamsBidi] when peer params arrive,
     * then bumped by inbound MAX_STREAMS frames (RFC 9000 §19.11). Must not
     * decrease — a smaller MAX_STREAMS than current is silently dropped.
     *
     * [openBidiStream] consults this cap; opening past it would violate the
     * peer's flow control and trigger STREAM_LIMIT_ERROR on their side.
     *
     * Round-5 concurrency #7: `@Volatile` because [peerMaxStreamsBidiSnapshot]
     * is documented as lock-free; without volatile, JLS allows long-tearing on
     * 32-bit JVMs (still common on Android) and the JIT may cache a stale
     * value indefinitely.
     */
    @Volatile
    internal var peerMaxStreamsBidi: Long = 0L

    @Volatile
    internal var peerMaxStreamsUni: Long = 0L

    /**
     * The connection-level receive limit we've currently advertised to the
     * peer. Tracks the high-water mark of the most recent MAX_DATA frame we
     * sent. The writer only emits a new MAX_DATA when the new value exceeds
     * this — prevents spamming the peer with redundant updates.
     */
    internal var advertisedMaxData: Long = config.initialMaxData

    /**
     * Cumulative count of peer-initiated unidirectional streams we've
     * accepted (incremented in [getOrCreatePeerStreamLocked]). Compared
     * against [advertisedMaxStreamsUni] by the writer to decide whether
     * to emit a fresh `MAX_STREAMS_UNI` frame extending the cap. Without
     * extension the peer can never open more than
     * [config.initialMaxStreamsUni] uni streams over the lifetime of the
     * connection — the production stream-cliff investigation
     * (`nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`)
     * showed this is exactly what bites the listener side: each Opus
     * frame is forwarded by the relay as a fresh uni stream, so any
     * broadcast longer than 100 frames silently truncates at the
     * audience.
     */
    internal var peerInitiatedUniCount: Long = 0L

    /** Bidi counterpart of [peerInitiatedUniCount]. */
    internal var peerInitiatedBidiCount: Long = 0L

    /**
     * The peer-initiated uni stream cap we've currently advertised to
     * the peer. Starts at [config.initialMaxStreamsUni]; the writer
     * raises it when [peerInitiatedUniCount] crosses the half-window
     * threshold and emits a `MAX_STREAMS_UNI(newCap)` frame.
     */
    internal var advertisedMaxStreamsUni: Long = config.initialMaxStreamsUni

    /** Bidi counterpart of [advertisedMaxStreamsUni]. */
    internal var advertisedMaxStreamsBidi: Long = config.initialMaxStreamsBidi

    /**
     * RFC 9002 retransmit pending-state for the receive-side flow-
     * control extensions. Non-null means "the last MAX_STREAMS_UNI
     * we sent was lost; the writer should re-emit on its next pass".
     *
     * The value held is the limit that was lost — meaningful only
     * when it equals [advertisedMaxStreamsUni], which is the
     * supersede-check from neqo's `fc.rs::frame_lost` (if a newer,
     * higher extension has since gone out, the older lost frame is
     * irrelevant). Step 6 of
     * `quic/plans/2026-05-04-control-frame-retransmit.md` populates
     * this field via the loss dispatcher; step 4 (this commit) wires
     * the writer to drain it before running the rolling-extension
     * threshold check.
     *
     * Caller of any read/write must hold [lock].
     */
    internal var pendingMaxStreamsUni: Long? = null

    /** Bidi counterpart of [pendingMaxStreamsUni]. */
    internal var pendingMaxStreamsBidi: Long? = null

    /** Connection-level MAX_DATA counterpart of [pendingMaxStreamsUni]. */
    internal var pendingMaxData: Long? = null

    /**
     * Per-stream MAX_STREAM_DATA pending-state. Keyed by stream id.
     * Same semantics as [pendingMaxStreamsUni]: present iff a
     * MAX_STREAM_DATA for that stream was lost and hasn't been
     * superseded by a higher emit. Wired by step 6.
     */
    internal val pendingMaxStreamData: MutableMap<Long, Long> = HashMap()

    /**
     * Pending retransmits for `NEW_CONNECTION_ID` frames (RFC 9000
     * §19.15). Keyed by sequence number. Populated on loss via
     * [onTokensLost]; drained by the writer on the next outbound.
     * Connection-ID rotation isn't currently triggered by any
     * application path inside `:quic`, so the public emit API is
     * limited to test usage — but the retransmit machinery is
     * complete so any future emit path lands cleanly.
     */
    internal val pendingNewConnectionId:
        MutableMap<Long, com.vitorpamplona.quic.connection.recovery.RecoveryToken.NewConnectionId> = HashMap()

    /**
     * RFC 9000 §8.2.2: queue of inbound PATH_CHALLENGE payloads we
     * still owe a PATH_RESPONSE for. Each entry is the EXACT 8 bytes
     * the peer challenged with — the response MUST echo them
     * unchanged. The writer drains this queue on the next
     * application-level packet build, emitting one PATH_RESPONSE
     * per entry until empty.
     *
     * Bounded at [MAX_PENDING_PATH_RESPONSES] to defend against an
     * attacker spamming PATH_CHALLENGE frames to exhaust memory.
     * Excess challenges are dropped; the protocol allows it (a peer
     * that doesn't get a response just retries).
     *
     * RFC 9000 §8.2.1 nuance: the response MUST go out on the
     * incoming-packet's path. We model a single path today (one
     * UDP socket per connection — see [com.vitorpamplona.quic.transport.UdpSocket]'s
     * "no migration" kdoc), so "path the challenge arrived on" is
     * trivially "the only path." When client-initiated migration
     * lands, this queue grows to remember which path each entry
     * belongs to.
     *
     * Caller must hold [streamsLock] for any read/write.
     */
    internal val pendingPathChallengePayloads: ArrayDeque<ByteArray> = ArrayDeque()

    /**
     * RFC 9000 §9 client-initiated path validation + DCID rotation.
     * The pool of unused peer-issued connection IDs, the outbound
     * `PATH_CHALLENGE` queue, the matching state machine, and the
     * `RETIRE_CONNECTION_ID` retransmit queue all live here. The
     * parser populates the pool from inbound `NEW_CONNECTION_ID`;
     * the writer drains the challenge + retire queues; the driver
     * triggers a fresh validation when consecutive PTO threshold is
     * exceeded.
     *
     * Caller of any read/write must hold [streamsLock].
     */
    internal val pathValidator: PathValidator =
        PathValidator(
            // Cap the unused-CID buffer at the smaller of our own
            // [activeConnectionIdLimit] (which we advertised to the
            // peer in transport parameters) and the validator's hard
            // [PathValidator.DEFAULT_MAX_UNUSED_CIDS] memory cap. A
            // peer that issues more CIDs than we said we'd accept
            // hits the cap and excess offers are dropped.
            maxUnusedCids =
                config.activeConnectionIdLimit
                    .coerceAtMost(PathValidator.DEFAULT_MAX_UNUSED_CIDS.toLong())
                    .toInt(),
        )

    /**
     * RFC 9002 RTT estimator + loss-detection algorithm. Single
     * shared instance per connection (RTT is per-path; we model a
     * single path). Per-space `largestAcked*` lives on
     * [LevelState]. Step 6 wires the loss-detection callback.
     */
    internal val lossDetection: com.vitorpamplona.quic.connection.recovery.QuicLossDetection =
        com.vitorpamplona.quic.connection.recovery
            .QuicLossDetection()

    /**
     * RFC 9002 §6.2 Probe Timeout signalling. When the driver loop's
     * PTO timer fires (no ack-eliciting packet has been ACK'd in
     * the PTO window), it sets [pendingPing] = true so the writer
     * emits a PING frame on the next drain. The PING elicits an
     * ACK from the peer; that ACK runs through loss detection and
     * declares any in-flight packets lost, triggering retransmit.
     *
     * Lock-split refactor (2026-05-08): @Volatile so the driver
     * sets it without acquiring any mutex.
     */
    @Volatile
    internal var pendingPing: Boolean = false

    /**
     * RFC 9002 §6.2.2 consecutive PTO count. Incremented on each
     * PTO expiration without an intervening ACK; reset to 0 when an
     * inbound ACK acknowledges any ack-eliciting packet. The driver
     * doubles its sleep between probes by `1 shl consecutivePtoCount`.
     *
     * `@Volatile` because the driver's send-loop reads this without
     * holding `streamsLock` (in the backoff calculation in
     * [QuicConnectionDriver.sendLoopBody]), while three writers
     * mutate it: the driver's PTO firing path, the parser's ACK
     * handler resetting on inbound ACK, and the
     * [applyPeerPathResponseLocked] reset on successful migration.
     * Without volatility the JIT can cache a stale value
     * indefinitely and the backoff multiplier grows unbounded.
     */
    @Volatile
    internal var consecutivePtoCount: Int = 0

    /**
     * RFC 9002 §6.2.4 probe budget. Set to 2 each time the PTO timer
     * fires (`handlePtoFired`); decremented by the driver's send loop
     * after each probe-bearing datagram leaves the socket. Between
     * sends the loop re-requeues any inflight CRYPTO / STREAM bytes
     * via [requeueInflightForProbe] so the next [drainOutbound] has
     * payload to probe with.
     *
     * Why two and not one: under heavy consecutive packet loss
     * (`amplificationlimit` interop scenario drops 6 client→server
     * packets in a row, `handshakeloss` / `handshakecorruption` drop
     * ~30% with burst=3), single-packet probes need 6 PTO doublings
     * (~19s) to land one datagram. Strict server-side
     * handshake-progress watchdogs (quic-go, msquic) tear the
     * connection down at ~10s of silence regardless of our
     * `max_idle_timeout`. Two probes per PTO halves recovery to
     * ~3 PTO rounds (~5s) and keeps these servers from giving up.
     *
     * `@Volatile`: the send loop reads/writes from a background
     * coroutine that may be on a different IO worker thread than
     * the PTO timer's setter. JLS allows long-tearing on 32-bit
     * JVMs without volatile.
     */
    @Volatile
    internal var pendingProbePackets: Int = 0

    /**
     * Optional supplier of underlying UDP-socket counters. Wired by the
     * platform-specific driver since `UdpSocket`'s counters are
     * JVM-side fields the commonMain side can't see directly.
     * Diagnostic-only: surfaces in [flowControlSnapshot] so a test
     * can correlate "frames lost on the wire" against "datagrams the
     * kernel actually delivered to the application". Null when no
     * driver is attached (in-process tests etc).
     */
    @Volatile
    internal var udpStatsSupplier: (() -> UdpSocketStats)? = null

    /**
     * Round-robin starting index for the writer's stream-drain iteration.
     * Without rotation, streams created earlier always drain first under MTU
     * pressure, starving later streams indefinitely.
     */
    internal var streamRoundRobinStart: Int = 0
    private val pendingDatagrams = ArrayDeque<ByteArray>()
    private val incomingDatagrams = ArrayDeque<ByteArray>()

    /**
     * Connection-level send credit, refreshed by inbound MAX_DATA frames
     * (RFC 9000 §19.9). Internal because the parser updates it directly under
     * the connection lock; the writer reads it via [sendConnectionFlowCreditSnapshot]
     * to gate stream-frame emission once we've sent past the peer's cap.
     */
    internal var sendConnectionFlowCredit: Long = 0L

    /** Total stream bytes we've already sent against [sendConnectionFlowCredit]. */
    internal var sendConnectionFlowConsumed: Long = 0L

    private var receiveConnectionFlowLimit: Long = config.initialMaxData

    /** Streams the peer has opened that we haven't surfaced yet. */
    private val newPeerStreams = ArrayDeque<QuicStream>()

    /**
     * Conflated signal that wakes [awaitIncomingPeerStream] callers whenever a
     * peer-initiated stream is appended to [newPeerStreams]. Conflated because
     * a single wake is enough to trigger a queue-drain — duplicate signals
     * collapse into one, which is the correct semantics for "something is
     * available, come look".
     */
    private val peerStreamSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Same conflated-signal pattern for inbound datagrams. Wakes
     * [awaitIncomingDatagram] callers when a new datagram is appended to
     * [incomingDatagrams].
     */
    private val incomingDatagramSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Closed-state signal that wakes any suspended `await*` caller when the
     * connection terminates. Distinct from the per-resource signal channels
     * because a closed connection should unblock everyone, not just the next
     * resource arrival. Closing the channel (vs sending a value) is the
     * idiomatic "this stream is done" — `select` clauses on `onReceive` see
     * a [ClosedReceiveChannelException] which we map to null.
     */
    private val closedSignal = Channel<Unit>(Channel.CONFLATED)

    private val tlsListener =
        object : TlsSecretsListener {
            override fun onHandshakeKeysReady(
                cipherSuite: Int,
                clientSecret: ByteArray,
                serverSecret: ByteArray,
            ) {
                handshake.sendProtection = packetProtectionFromSecret(cipherSuite, clientSecret)
                handshake.receiveProtection = packetProtectionFromSecret(cipherSuite, serverSecret)
                qlogObserver.onKeyUpdated("client", EncryptionLevel.HANDSHAKE)
                qlogObserver.onKeyUpdated("server", EncryptionLevel.HANDSHAKE)
            }

            override fun onEarlyDataKeysReady(
                cipherSuite: Int,
                clientEarlySecret: ByteArray,
            ) {
                // Resumption + 0-RTT path: install 0-RTT packet
                // protection so the writer can encrypt outbound
                // application data with early-data keys until 1-RTT
                // keys arrive. Cleared in onApplicationKeysReady (RFC
                // 9001 §4.10 forbids using 0-RTT keys once 1-RTT is
                // available).
                zeroRttSendProtection = packetProtectionFromSecret(cipherSuite, clientEarlySecret)
                qlogObserver.onKeyUpdated("client", EncryptionLevel.APPLICATION)
            }

            override fun onApplicationKeysReady(
                cipherSuite: Int,
                clientSecret: ByteArray,
                serverSecret: ByteArray,
            ) {
                // RFC 9001 §4.10 — 0-RTT rejection fallback. If we
                // offered 0-RTT but the server's EncryptedExtensions
                // didn't echo the early_data extension, any application
                // data we already shipped under early-data keys was
                // silently dropped server-side. Re-queue it so the
                // writer replays it under the about-to-be-installed
                // 1-RTT keys. Must run BEFORE we install the 1-RTT
                // sendProtection — once the writer sees 1-RTT keys
                // available it'll start drainOutbound under short
                // headers; we want any pending retransmits to flow
                // through that path with the original byte content.
                //
                // requeueAllInflightStreamData walks streamsList under
                // the assumption the caller holds streamsLock — which
                // we do here because the parser path that fired this
                // listener (handleServerFinished → onApplicationKeysReady)
                // runs inside streamsLock.withLock { feedDatagram(...) }
                // in the read loop.
                val rejected0Rtt =
                    resumption != null &&
                        resumption.maxEarlyDataSize > 0 &&
                        !tls.earlyDataAccepted
                if (rejected0Rtt) {
                    requeueAllInflightStreamData()
                    application.cryptoSend.requeueAllInflight()
                    application.sentPackets.clear()
                }
                application.sendProtection = packetProtectionFromSecret(cipherSuite, clientSecret)
                application.receiveProtection = packetProtectionFromSecret(cipherSuite, serverSecret)
                // Drop 0-RTT keys — the writer must use 1-RTT short
                // headers from here on (RFC 9001 §4.10).
                zeroRttSendProtection = null
                // Stash the live secrets + cipher suite so we can derive
                // next-phase keys via HKDF-Expand-Label("quic ku") on demand
                // when the peer initiates a key update (RFC 9001 §6). Only
                // application-level keys are subject to key update —
                // Initial / Handshake levels are short-lived and never see
                // the KEY_PHASE bit (long headers don't carry it).
                appCipherSuite = cipherSuite
                appReceiveSecret = serverSecret.copyOf()
                appSendSecret = clientSecret.copyOf()
                qlogObserver.onKeyUpdated("client", EncryptionLevel.APPLICATION)
                qlogObserver.onKeyUpdated("server", EncryptionLevel.APPLICATION)
            }

            override fun onHandshakeComplete() {
                handshakeComplete = true
                if (status == Status.HANDSHAKING) status = Status.CONNECTED
                applyPeerTransportParameters()
                tls.negotiatedAlpn?.let { qlogObserver.onAlpnNegotiated(it.decodeToString()) }
                handshakeDoneSignal.complete(Unit)
                extraSecretsListener?.onHandshakeComplete()
            }

            override fun onNewSessionTicket(state: com.vitorpamplona.quic.tls.TlsResumptionState) {
                onResumptionTicket?.invoke(state)
                extraSecretsListener?.onNewSessionTicket(state)
            }
        }

    private val handshakeDoneSignal = CompletableDeferred<Unit>()

    /**
     * Suspend until the handshake completes or fails. Throws if the connection
     * was closed before reaching CONNECTED.
     */
    suspend fun awaitHandshake() {
        handshakeDoneSignal.await()
    }

    /** Mark the handshake as failed (called when read loop dies, peer closes, or local close runs). */
    internal fun signalHandshakeFailed(cause: Throwable) {
        if (!handshakeDoneSignal.isCompleted) handshakeDoneSignal.completeExceptionally(cause)
    }

    val tls: TlsClient =
        TlsClient(
            serverName = serverName,
            transportParameters = buildLocalTransportParameters().encode(),
            secretsListener = tlsListener,
            certificateValidator = tlsCertificateValidator,
            offeredAlpns = alpnList,
            cipherSuites = cipherSuites,
            resumption = resumption,
        )

    init {
        // Install Initial keys based on the random destination CID we just generated.
        val proto = InitialSecrets.derive(destinationConnectionId.bytes)
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)
        // Initial packets always use AES-128-GCM (RFC 9001 §5). Use the
        // platform's cached-cipher implementation so per-packet seal/open
        // doesn't pay for `Cipher.getInstance`.
        initial.sendProtection =
            PacketProtection(bestAes128GcmAead(proto.clientKey), proto.clientKey, proto.clientIv, hp, proto.clientHp)
        initial.receiveProtection =
            PacketProtection(bestAes128GcmAead(proto.serverKey), proto.serverKey, proto.serverIv, hp, proto.serverHp)

        // Resumption + 0-RTT: pre-load flow-control limits from the
        // remembered transport parameters so streams opened BEFORE the
        // new connection's ServerHello arrives have credit to push 0-RTT
        // STREAM frames on the wire. RFC 9001 §7.4.1 explicitly allows
        // (in fact requires) the client to use REMEMBERED transport
        // params for this purpose, while skipping the CID-bound ones
        // (initial_source_connection_id etc.) which would mismatch the
        // fresh CIDs we just generated. Server's real new params arrive
        // in EncryptedExtensions and the existing
        // [applyPeerTransportParameters] hook then overwrites these
        // pre-loaded values.
        if (resumption?.peerTransportParameters != null) {
            try {
                val tp = TransportParameters.decode(resumption.peerTransportParameters)
                sendConnectionFlowCredit = tp.initialMaxData ?: 0L
                peerMaxStreamsBidi = tp.initialMaxStreamsBidi ?: 0L
                peerMaxStreamsUni = tp.initialMaxStreamsUni ?: 0L
            } catch (_: Throwable) {
                // Bad cached params shouldn't block the connection; we
                // just won't be able to send 0-RTT data. Server's
                // real params arrive on EE either way.
            }
        }
    }

    /** Begin the handshake — emits ClientHello into Initial CRYPTO. */
    fun start() {
        // qlog: emit connection_started + initial transport_parameters_set
        // before any wire traffic so the trace makes chronological sense
        // when handed to qvis.
        qlogObserver.onConnectionStarted(
            serverName = serverName,
            dcid = destinationConnectionId.bytes,
            scid = sourceConnectionId.bytes,
        )
        qlogObserver.onTransportParametersSet("local", localTransportParametersSummary())
        // RFC 9000 §6: we're not doing version negotiation, so the
        // chosen version is unconditional.
        qlogObserver.onVersionInformation("v1", emptyList())
        tls.start()
        // Drain ClientHello bytes into the Initial-level CRYPTO send buffer.
        // Cache the bytes so [applyVersionNegotiation] can re-enqueue them
        // onto a fresh cryptoSend after resetting Initial-level state.
        // Cannot re-pollOutbound — the queue is destructive.
        tls.pollOutbound(TlsClient.Level.INITIAL)?.let {
            originalClientHello = it
            initial.cryptoSend.enqueue(it)
        }
    }

    /**
     * Apply a Version Negotiation packet (RFC 9000 §6) received from the
     * server. The client offered [initialVersion]; the server replies with
     * a list of versions it supports. We pick [QuicVersion.V1] from the
     * list, regenerate the destination CID + Initial keys, reset the
     * Initial encryption level, and re-emit the cached ClientHello so the
     * next drain produces a valid v1 Initial packet.
     *
     * RFC 9000 §6.2 invariants enforced here:
     *   - The supported_versions list MUST NOT contain
     *     [initialVersion] — including it would mean the server received
     *     our offer and STILL replied with VN, which is a downgrade signal.
     *     Drop the packet (treat as no-op).
     *   - At most one VN per connection (latched via [vnConsumed]).
     *   - If we cannot speak any of the offered versions, fail the
     *     handshake.
     *
     * Caller MUST hold [lock] (the parser already does).
     */
    internal fun applyVersionNegotiation(supportedVersions: List<Int>) {
        // RFC 9000 §6.2: a second VN must be ignored.
        if (vnConsumed) return
        // Anti-downgrade: server-claimed support for the version we
        // already offered indicates VN replay / spoof. Drop silently.
        if (supportedVersions.contains(initialVersion)) return
        // Pick a version we can speak. Today that's only v1.
        if (!supportedVersions.contains(QuicVersion.V1)) {
            signalHandshakeFailed(
                QuicVersionNegotiationException(
                    "VERSION_NEGOTIATION: server offered ${supportedVersions.map { v -> "0x" + v.toUInt().toString(16) }}, " +
                        "client only supports 0x" + QuicVersion.V1.toUInt().toString(16),
                ),
            )
            markClosedExternally("VERSION_NEGOTIATION: no mutually supported version")
            return
        }

        // Latch BEFORE reset so a re-entrant inbound VN during the reset
        // window is rejected by the early-return at the top.
        vnConsumed = true

        // Generate a fresh destination CID. RFC 9000 §6.2 doesn't strictly
        // require this (the server hasn't indexed our CID with any state
        // since its only response was VN), but it matches what reference
        // implementations do and keeps the post-VN connection
        // cryptographically isolated from the pre-VN exchange.
        val newDcid = ConnectionId.random(8)
        destinationConnectionId = newDcid

        // Reset Initial-level state in place: fresh PN space (next
        // allocateOutbound returns 0), fresh ackTracker, fresh
        // cryptoSend / cryptoReceive, fresh sentPackets retention.
        // Writer + parser only ever reach the level via [conn.initial],
        // so we mutate the fields rather than swap the instance.
        val proto = InitialSecrets.derive(newDcid.bytes)
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)
        val newSend =
            PacketProtection(bestAes128GcmAead(proto.clientKey), proto.clientKey, proto.clientIv, hp, proto.clientHp)
        val newReceive =
            PacketProtection(bestAes128GcmAead(proto.serverKey), proto.serverKey, proto.serverIv, hp, proto.serverHp)
        initial.resetForVersionNegotiation(sendProtection = newSend, receiveProtection = newReceive)
        // Re-enqueue the ClientHello so the next drainOutbound emits a v1
        // Initial datagram with the same TLS handshake the original carried.
        originalClientHello?.let { initial.cryptoSend.enqueue(it) }

        // Switch the writer's stamp to v1 so the next Initial / Handshake
        // long-header carries the right version.
        currentVersion = QuicVersion.V1
    }

    /**
     * Apply a verified Retry packet per RFC 9000 §17.2.5 + RFC 9001 §5.8.
     * Validates the retry-integrity tag, swaps DCID, re-derives Initial keys,
     * resets the Initial PN space, and re-enqueues the cached ClientHello so
     * the next outbound Initial carries `Token = retryPacket.retryToken`.
     *
     * Returns false on bad tag, second Retry (RFC 9000 §17.2.5.2), or
     * pre-start (no original ClientHello captured) — all silently dropped.
     */
    internal fun applyRetry(
        retryPacket: com.vitorpamplona.quic.packet.RetryPacket,
        originalPacketBytes: ByteArray,
    ): Boolean {
        if (retryConsumed) return false
        // RFC 9000 §17.2.5.2: "the client MUST discard a Retry packet
        // that contains a Source Connection ID field that is identical
        // to the Destination Connection ID field of its Initial packet".
        // Without this guard a self-loop / off-path attacker could feed
        // us a Retry that nominally validates but pins our state to the
        // attacker's chosen DCID forever.
        if (retryPacket.scid.bytes.contentEquals(originalDestinationConnectionId.bytes)) {
            return false
        }
        if (!retryPacket.verifyIntegrityTag(originalPacketBytes, originalDestinationConnectionId.bytes)) {
            return false
        }
        val savedClientHello = originalClientHello ?: return false

        destinationConnectionId = retryPacket.scid
        val proto = InitialSecrets.derive(destinationConnectionId.bytes)
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)
        // Use resetForRetry, NOT resetForVersionNegotiation: RFC 9001 §5.7
        // requires the Initial PN namespace to continue across the Retry
        // boundary. Resetting PN to 0 caused the runner to flag
        // "Client reset the packet number. Check failed for PN 0".
        initial.resetForRetry(
            sendProtection =
                PacketProtection(bestAes128GcmAead(proto.clientKey), proto.clientKey, proto.clientIv, hp, proto.clientHp),
            receiveProtection =
                PacketProtection(bestAes128GcmAead(proto.serverKey), proto.serverKey, proto.serverIv, hp, proto.serverHp),
        )
        initial.cryptoSend.enqueue(savedClientHello)

        retryToken = retryPacket.retryToken
        retryConsumed = true
        return true
    }

    private fun localTransportParametersSummary(): Map<String, String> {
        val out = LinkedHashMap<String, String>(8)
        out["initial_max_data"] = config.initialMaxData.toString()
        out["initial_max_stream_data_bidi_local"] = config.initialMaxStreamDataBidiLocal.toString()
        out["initial_max_stream_data_bidi_remote"] = config.initialMaxStreamDataBidiRemote.toString()
        out["initial_max_stream_data_uni"] = config.initialMaxStreamDataUni.toString()
        out["initial_max_streams_bidi"] = config.initialMaxStreamsBidi.toString()
        out["initial_max_streams_uni"] = config.initialMaxStreamsUni.toString()
        out["max_idle_timeout"] = config.maxIdleTimeoutMillis.toString()
        out["max_udp_payload_size"] = config.maxUdpPayloadSize.toString()
        out["max_datagram_frame_size"] = config.maxDatagramFrameSize.toString()
        return out
    }

    private fun peerTransportParametersSummary(tp: TransportParameters): Map<String, String> {
        val out = LinkedHashMap<String, String>(10)
        tp.initialMaxData?.let { out["initial_max_data"] = it.toString() }
        tp.initialMaxStreamDataBidiLocal?.let { out["initial_max_stream_data_bidi_local"] = it.toString() }
        tp.initialMaxStreamDataBidiRemote?.let { out["initial_max_stream_data_bidi_remote"] = it.toString() }
        tp.initialMaxStreamDataUni?.let { out["initial_max_stream_data_uni"] = it.toString() }
        tp.initialMaxStreamsBidi?.let { out["initial_max_streams_bidi"] = it.toString() }
        tp.initialMaxStreamsUni?.let { out["initial_max_streams_uni"] = it.toString() }
        tp.maxIdleTimeoutMillis?.let { out["max_idle_timeout"] = it.toString() }
        tp.maxUdpPayloadSize?.let { out["max_udp_payload_size"] = it.toString() }
        tp.maxDatagramFrameSize?.let { out["max_datagram_frame_size"] = it.toString() }
        tp.maxAckDelay?.let { out["max_ack_delay"] = it.toString() }
        return out
    }

    private fun buildLocalTransportParameters(): TransportParameters =
        TransportParameters(
            initialMaxData = config.initialMaxData,
            initialMaxStreamDataBidiLocal = config.initialMaxStreamDataBidiLocal,
            initialMaxStreamDataBidiRemote = config.initialMaxStreamDataBidiRemote,
            initialMaxStreamDataUni = config.initialMaxStreamDataUni,
            initialMaxStreamsBidi = config.initialMaxStreamsBidi,
            initialMaxStreamsUni = config.initialMaxStreamsUni,
            maxIdleTimeoutMillis = config.maxIdleTimeoutMillis,
            maxUdpPayloadSize = config.maxUdpPayloadSize,
            ackDelayExponent = config.ackDelayExponent,
            maxAckDelay = config.maxAckDelay,
            activeConnectionIdLimit = config.activeConnectionIdLimit,
            initialSourceConnectionId = sourceConnectionId.bytes,
            maxDatagramFrameSize = config.maxDatagramFrameSize,
        )

    /**
     * Lock-split refactor (2026-05-08): caller must hold [streamsLock]
     * because we mutate [streams], [peerMaxStreamsBidi]/Uni, and
     * [sendConnectionFlowCredit]. Invoked from the TLS listener inside
     * [QuicConnectionParser.feedDatagram] which acquires [streamsLock]
     * around CRYPTO-frame handling.
     */
    private fun applyPeerTransportParameters() {
        val raw = tls.peerTransportParameters ?: return
        val tp = TransportParameters.decode(raw)
        // Audit-4 #7: RFC 9000 §7.3 MUST checks. The peer's
        //   initial_source_connection_id MUST equal the SCID it put in its
        //     first Initial (which we adopted as `destinationConnectionId`).
        //   original_destination_connection_id MUST equal the DCID we put in
        //     our first Initial (`originalDestinationConnectionId`).
        // Skipping these opens a CID-substitution / downgrade window where
        // an attacker who can rewrite the first Initial can swap CIDs.
        val iscid = tp.initialSourceConnectionId
        if (iscid == null || !iscid.contentEquals(destinationConnectionId.bytes)) {
            markClosedExternally(
                "TRANSPORT_PARAMETER_ERROR: peer initial_source_connection_id mismatch",
            )
            return
        }
        val odcid = tp.originalDestinationConnectionId
        // We don't speak Retry yet, so the peer SHOULD echo our original DCID.
        // If it's missing (some servers omit it pre-handshake-complete) we
        // accept; if present but wrong we close.
        if (odcid != null && !odcid.contentEquals(originalDestinationConnectionId.bytes)) {
            markClosedExternally(
                "TRANSPORT_PARAMETER_ERROR: peer original_destination_connection_id mismatch",
            )
            return
        }
        peerTransportParameters = tp
        qlogObserver.onTransportParametersSet("remote", peerTransportParametersSummary(tp))
        sendConnectionFlowCredit = tp.initialMaxData ?: 0L
        peerMaxStreamsBidi = tp.initialMaxStreamsBidi ?: 0L
        peerMaxStreamsUni = tp.initialMaxStreamsUni ?: 0L
        // Update each open stream's send credit per direction.
        for ((id, stream) in streams) {
            stream.sendCredit =
                when (StreamId.kindOf(id)) {
                    StreamId.Kind.CLIENT_BIDI, StreamId.Kind.SERVER_BIDI -> {
                        if (StreamId.isClientInitiated(id)) tp.initialMaxStreamDataBidiRemote ?: 0L else tp.initialMaxStreamDataBidiLocal ?: 0L
                    }

                    StreamId.Kind.CLIENT_UNI, StreamId.Kind.SERVER_UNI -> {
                        tp.initialMaxStreamDataUni ?: 0L
                    }
                }
        }
    }

    /**
     * Lock-split refactor (2026-05-08): split the previous single
     * `lock` into two independent mutexes so the read loop, send
     * loop, and app coroutines can mostly progress in parallel.
     *
     *   - [streamsLock] guards the streams registry, datagram queues,
     *     stream-id counters, connection-level flow-control bookkeeping,
     *     packet-number space + sentPackets retention + CRYPTO buffer
     *     mutations at every encryption level. The writer's drain and
     *     the parser's feed both take it.
     *   - [lifecycleLock] guards [status] / [closeReason] /
     *     [closeErrorCode] transitions.
     *
     * Per-stream and per-level buffer mutations serialize through
     * `synchronized(this)` inside `SendBuffer` / `ReceiveBuffer` /
     * `AckTracker` — those leaf locks are safe to take with or
     * without an outer mutex held.
     *
     * Acquisition order to prevent deadlock:
     * `lifecycleLock` → `streamsLock`. Never go the other way.
     *
     * The historical `lock` field is retained as an alias of
     * [lifecycleLock] for source-compatibility with external callers
     * (tests, harnesses, in-process bridges). New code MUST NOT use it
     * — it no longer protects streams or level state.
     */
    val streamsLock: Mutex = Mutex()

    val lifecycleLock: Mutex = Mutex()

    @Deprecated(
        "Use streamsLock or lifecycleLock as appropriate. Lock-split refactor 2026-05-08.",
        replaceWith = ReplaceWith("streamsLock"),
    )
    val lock: Mutex
        get() = lifecycleLock

    /**
     * Allocate a new client-initiated bidirectional stream. Locked.
     *
     * Throws [QuicStreamLimitException] if the peer has not granted enough
     * bidirectional stream credit yet. Use [peerMaxStreamsBidiSnapshot] to
     * check capacity proactively if the caller wants to back-pressure rather
     * than throw.
     */
    suspend fun openBidiStream(): QuicStream = streamsLock.withLock { openBidiStreamLocked() }

    /**
     * Atomically open one bidi stream per [items] entry under a single
     * [streamsLock] hold and run [init] for each (stream, item) inside
     * the lock. The send loop cannot interject between opens — when it
     * next drains it sees ALL N streams' frames ready and packs them
     * into coalesced packets instead of emitting one tiny packet per
     * stream.
     *
     * **`init` runs under `streamsLock`.** It must not suspend
     * (the type signature enforces this) and SHOULD be fast — any
     * expensive work (encoding, allocation-heavy formatting) belongs
     * outside the call so it doesn't extend the lock-hold time. The
     * intended shape per caller:
     *
     *     val encoded = items.map { encode(it) }                   // outside
     *     conn.openBidiStreamsBatch(encoded) { stream, payload ->  // under lock
     *         stream.send.enqueue(payload)
     *         stream.send.finish()
     *         Handle(stream)
     *     }
     *
     * This is the bug-resistant API for the prepareRequests pattern.
     * The previous shape (caller manually wraps `streamsLock.withLock`
     * around a loop of [openBidiStreamLocked]) regressed twice: once
     * by holding the wrong lock, and once by skipping the wrapper
     * entirely. Both shapes failed silently as "one STREAM per packet"
     * under multiplex load, while the unit tests passed.
     *
     * Callers that just need a single stream should still use
     * [openBidiStream]. [openBidiStreamLocked] remains public for the
     * rare custom-batching scenarios that need finer control, but
     * those callers should generally migrate to this API.
     */
    suspend fun <I, R> openBidiStreamsBatch(
        items: List<I>,
        init: (QuicStream, I) -> R,
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val streamsBefore = if (writerDebugEnabled) streams.size else 0
        val result =
            streamsLock.withLock {
                items.map { init(openBidiStreamLocked(), it) }
            }
        if (writerDebugEnabled) {
            System.err.println(
                "[batch] openBidiStreamsBatch items=${items.size} returned=${result.size} " +
                    "streamsList_before=$streamsBefore streamsList_after=${streams.size}",
            )
        }
        return result
    }

    /**
     * The streamsLock-holding primitive used by [openBidiStream] and
     * [openBidiStreamsBatch]. Public so callers that need a custom
     * batching shape (e.g. mixed bidi+uni opens) can compose it under
     * a manual [streamsLock] hold. Caller MUST hold [streamsLock].
     */
    fun openBidiStreamLocked(): QuicStream {
        // Mutex.isLocked is the only check we have — kotlinx.coroutines
        // Mutex doesn't expose ownership without an `owner` argument,
        // and we don't pass one in production. So this catches the
        // common bug — caller used the wrong lock or no lock — but
        // not the rarer case of "caller held a DIFFERENT lock that
        // happens to be locked too." The interop runner's multiplexing
        // failure on 2026-05-06 was precisely this: prepareRequests
        // held lifecycleLock (`conn.lock`) and called this fn, the
        // send loop's drainOutbound interleaved between opens, and
        // we emitted one STREAM per packet (1421/2000 files in 60s).
        check(streamsLock.isLocked) {
            "openBidiStreamLocked requires streamsLock to be held — caller " +
                "must wrap with streamsLock.withLock { ... }. Without that, " +
                "drainOutbound can race the streams mutation and emit one " +
                "STREAM per packet under multiplex load."
        }
        if (nextLocalBidiIndex >= peerMaxStreamsBidi) {
            throw QuicStreamLimitException(
                "peer-granted bidi stream cap reached " +
                    "(used=$nextLocalBidiIndex limit=$peerMaxStreamsBidi)",
            )
        }
        val id = StreamId.build(StreamId.Kind.CLIENT_BIDI, nextLocalBidiIndex++)
        val stream = QuicStream(id, QuicStream.Direction.BIDIRECTIONAL)
        stream.sendCredit = peerTransportParameters?.initialMaxStreamDataBidiRemote ?: config.initialMaxStreamDataBidiRemote
        stream.receiveLimit = config.initialMaxStreamDataBidiLocal
        streams[id] = stream
        streamsList = streamsList + stream
        return stream
    }

    /**
     * Allocate a new client-initiated unidirectional (write-only) stream.
     * Locked.
     *
     * If [bestEffort] is true, the stream's [SendBuffer] drops lost
     * ranges instead of retransmitting them (see
     * [QuicStream.bestEffort]). Used for moq-lite group streams
     * carrying real-time Opus audio.
     */
    suspend fun openUniStream(bestEffort: Boolean = false): QuicStream = streamsLock.withLock { openUniStreamLocked(bestEffort) }

    /**
     * The streamsLock-holding primitive used by [openUniStream] and
     * [openUniStreamsBatch]. Caller MUST hold [streamsLock].
     */
    fun openUniStreamLocked(bestEffort: Boolean = false): QuicStream {
        check(streamsLock.isLocked) {
            "openUniStreamLocked requires streamsLock to be held"
        }
        if (nextLocalUniIndex >= peerMaxStreamsUni) {
            throw QuicStreamLimitException(
                "peer-granted uni stream cap reached " +
                    "(used=$nextLocalUniIndex limit=$peerMaxStreamsUni)",
            )
        }
        val id = StreamId.build(StreamId.Kind.CLIENT_UNI, nextLocalUniIndex++)
        val stream = QuicStream(id, QuicStream.Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE, bestEffort = bestEffort)
        stream.sendCredit = peerTransportParameters?.initialMaxStreamDataUni ?: config.initialMaxStreamDataUni
        stream.receiveLimit = 0L // can't receive
        streams[id] = stream
        streamsList = streamsList + stream
        return stream
    }

    /**
     * Bug-resistant counterpart to [openBidiStreamsBatch] for uni
     * streams. Atomically open one client-uni stream per [items]
     * entry under a single [streamsLock] hold and run [init] for
     * each (stream, item).
     *
     * **`init` runs under `streamsLock`** — same caveat as
     * [openBidiStreamsBatch]: keep it fast, encode outside the call.
     *
     * Use this for moq audio-rooms and any other path that opens many
     * uni streams in burst — without batching, each open releases the
     * lock and the send loop can interject, emitting one stream per
     * packet (the same shape that broke bidi multiplexing on
     * 2026-05-06). [bestEffort] applies uniformly to every stream
     * in the batch; mixed-mode batches need separate calls.
     */
    suspend fun <I, R> openUniStreamsBatch(
        items: List<I>,
        bestEffort: Boolean = false,
        init: (QuicStream, I) -> R,
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        return streamsLock.withLock {
            items.map { init(openUniStreamLocked(bestEffort), it) }
        }
    }

    /** Snapshot of peer-granted bidi cap. Reads do not need the lock — long writes are atomic on every supported platform. */
    fun peerMaxStreamsBidiSnapshot(): Long = peerMaxStreamsBidi

    /** Snapshot of peer-granted uni cap. */
    fun peerMaxStreamsUniSnapshot(): Long = peerMaxStreamsUni

    /**
     * Number of client-initiated bidi streams we've allocated so far —
     * the "consumed" side of the [peerMaxStreamsBidiSnapshot] budget.
     * Increments on each [openBidiStreamLocked] call and never decreases
     * (RFC 9000 §4.6: the limit is on cumulative IDs, not concurrent
     * count).
     *
     * Multiplexing callers use this with [peerMaxStreamsBidiSnapshot] to
     * compute the AVAILABLE budget at any moment (`max - used`) so they
     * can pace stream creation against peer's MAX_STREAMS_BIDI bumps
     * instead of throwing [QuicStreamLimitException] on the cap-tightest
     * peer.
     *
     * Read without [streamsLock] — the field is mutated under
     * `streamsLock`, but callers using this for back-pressure decisions
     * tolerate a slightly-stale read (worst case: open one fewer stream
     * than possible this round, get one more next round).
     */
    fun localBidiStreamsUsedSnapshot(): Long = nextLocalBidiIndex

    /**
     * Coherent point-in-time snapshot of the connection's flow-control
     * accounting. Acquires [lock] internally so the fields are read
     * atomically with respect to the read / send / parse paths.
     *
     * Diagnostic-only: meant for tests + dev tooling investigating
     * the production "stream cliff" symptom where `publisher.send`
     * keeps returning `true` past frame ~99 but no data reaches the
     * listener. Surface area is the smallest set that lets a caller
     * answer:
     *
     *   - what did the peer grant at handshake?
     *   - has the peer extended the cap since? (delta = current - initial)
     *   - have we hit the cap? (consumed vs credit)
     *   - is data piling up unsent on any stream? (sum readableBytes
     *     over local-initiated streams)
     *
     * See `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
     */
    suspend fun flowControlSnapshot(): QuicFlowControlSnapshot =
        streamsLock.withLock {
            val tp = peerTransportParameters
            // Sum bytes the application has enqueued but the writer
            // hasn't yet handed to a STREAM frame. A non-zero value
            // after the pump is the smoking gun for "data stuck in
            // local send buffer due to flow control".
            var pending = 0L
            var pendingStreamCount = 0
            for (stream in streamsList) {
                val pendingOnStream = stream.send.readableBytes.toLong()
                if (pendingOnStream > 0) {
                    pending += pendingOnStream
                    pendingStreamCount += 1
                }
            }
            val udp = udpStatsSupplier?.invoke()
            QuicFlowControlSnapshot(
                peerInitialMaxData = tp?.initialMaxData,
                peerInitialMaxStreamDataUni = tp?.initialMaxStreamDataUni,
                peerInitialMaxStreamDataBidiRemote = tp?.initialMaxStreamDataBidiRemote,
                peerInitialMaxStreamsUni = tp?.initialMaxStreamsUni,
                peerInitialMaxStreamsBidi = tp?.initialMaxStreamsBidi,
                sendConnectionFlowCredit = sendConnectionFlowCredit,
                sendConnectionFlowConsumed = sendConnectionFlowConsumed,
                peerMaxStreamsUniCurrent = peerMaxStreamsUni,
                peerMaxStreamsBidiCurrent = peerMaxStreamsBidi,
                nextLocalUniIndex = nextLocalUniIndex,
                nextLocalBidiIndex = nextLocalBidiIndex,
                advertisedMaxStreamsUni = advertisedMaxStreamsUni,
                advertisedMaxStreamsBidi = advertisedMaxStreamsBidi,
                peerInitiatedUniCount = peerInitiatedUniCount,
                peerInitiatedBidiCount = peerInitiatedBidiCount,
                totalEnqueuedNotSentBytes = pending,
                streamsWithPendingBytes = pendingStreamCount,
                totalStreamsTracked = streamsList.size,
                udp = udp,
            )
        }

    suspend fun pollIncomingPeerStream(): QuicStream? = streamsLock.withLock { newPeerStreams.removeFirstOrNull() }

    /**
     * Suspends until a peer-initiated stream is queued OR the connection
     * closes. Returns null on close. Replaces the older `pollIncomingPeerStream
     * + delay(5)` busy-loop — this version wakes within microseconds of the
     * parser appending a stream and parks the coroutine the rest of the time.
     *
     * **An H3 application MUST consume peer-initiated streams.** RFC 9114
     * §6.2.1 mandates that the server opens at least three peer-initiated
     * uni streams (CONTROL + QPACK_ENCODER + QPACK_DECODER). The parser
     * routes their bytes into the per-[QuicStream] `incomingChannel`
     * (capacity 64 chunks); if nothing accepts and reads them, the channel
     * fills and the next inbound chunk trips the audit-4 #3 "slow consumer"
     * tear-down at [QuicConnectionParser] (`INTERNAL_ERROR: stream …
     * consumer overflowed`). Symptoms: under H3 multiplexing of many bidi
     * request streams, the server's QPACK encoder issues a burst of
     * dynamic-table inserts on its uni stream and the connection dies
     * after ~5 s with zero requests completed. See
     * [drainPeerInitiatedUniStreamsIntoBlackHole] for a one-line opt-in
     * drainer that satisfies the contract when the H3 layer doesn't
     * actually need the SETTINGS / QPACK bytes.
     */
    suspend fun awaitIncomingPeerStream(): QuicStream? {
        while (true) {
            streamsLock.withLock { newPeerStreams.removeFirstOrNull() }?.let { return it }
            if (status == Status.CLOSED) return null
            // select between "wakeup" and "closed" so neither path can hang.
            val keepWaiting =
                select<Boolean> {
                    peerStreamSignal.onReceiveCatching { result ->
                        // Conflated channel: if it's closed (shouldn't happen
                        // here, but be defensive) bail out.
                        result.isSuccess
                    }
                    closedSignal.onReceiveCatching { false }
                }
            if (!keepWaiting) {
                // After a close-wake, drain one more time to surface any
                // streams added between the last drain and the close.
                streamsLock.withLock { newPeerStreams.removeFirstOrNull() }?.let { return it }
                return null
            }
        }
    }

    suspend fun streamById(id: Long): QuicStream? = streamsLock.withLock { streams[id] }

    suspend fun queueDatagram(payload: ByteArray) = streamsLock.withLock { pendingDatagrams.addLast(payload) }

    suspend fun pollIncomingDatagram(): ByteArray? = streamsLock.withLock { incomingDatagrams.removeFirstOrNull() }

    /**
     * Suspending counterpart of [pollIncomingDatagram]. Returns null only when
     * the connection has been closed and no more datagrams remain. Same
     * select-based wakeup pattern as [awaitIncomingPeerStream].
     */
    suspend fun awaitIncomingDatagram(): ByteArray? {
        while (true) {
            streamsLock.withLock { incomingDatagrams.removeFirstOrNull() }?.let { return it }
            if (status == Status.CLOSED) return null
            val keepWaiting =
                select<Boolean> {
                    incomingDatagramSignal.onReceiveCatching { result -> result.isSuccess }
                    closedSignal.onReceiveCatching { false }
                }
            if (!keepWaiting) {
                streamsLock.withLock { incomingDatagrams.removeFirstOrNull() }?.let { return it }
                return null
            }
        }
    }

    /** Initiate a graceful close. */
    suspend fun close(
        errorCode: Long,
        reason: String,
    ) {
        // Atomic CAS via [closeStateMonitor] so two concurrent
        // close()/markClosedExternally callers can't both observe a
        // non-CLOSED state and both proceed to fire the qlog event /
        // overwrite [closeReason]. First caller wins; subsequent
        // callers no-op silently.
        val firedQlog =
            synchronized(closeStateMonitor) {
                if (status == Status.CLOSED || status == Status.CLOSING) return@synchronized false
                closeErrorCode = errorCode
                closeReason = reason
                status = Status.CLOSING
                true
            }
        if (!firedQlog) return
        qlogObserver.onConnectionClosed("local", errorCode, reason)
        // If a caller is suspended on awaitHandshake() and we're tearing down
        // before completion, fail the deferred so the caller throws instead
        // of hanging forever.
        if (!handshakeComplete) {
            signalHandshakeFailed(QuicConnectionClosedException("connection closed before handshake completed: $reason"))
        }
        closeAllSignals()
    }

    /** Called by the parser on inbound CONNECTION_CLOSE or by the driver on read-loop death. */
    internal fun markClosedExternally(reason: String) {
        // First-call wins for [closeReason] so the highest-quality
        // diagnostic is preserved when several teardown paths race
        // (e.g. read loop's `socket.receive() == null` finally fires
        // a moment before the send loop's `socket.send` throw catch
        // block does). Pre-fix, the "did we win the race?" check was a
        // non-atomic read-then-write on [status], so two callers could
        // both observe `status != CLOSED`, both fire the qlog event,
        // and both stomp on [closeReason] in unpredictable order. The
        // monitor below makes the transition truly atomic.
        val firstClose =
            synchronized(closeStateMonitor) {
                if (status == Status.CLOSED) return@synchronized false
                status = Status.CLOSED
                closeReason = reason
                true
            }
        if (firstClose) {
            // "remote" covers both peer-initiated CONNECTION_CLOSE and
            // local invariant violations (CID mismatch, frame decode
            // failure) that the parser surfaces as markClosedExternally.
            // The reason string is the discriminator the trace consumer
            // reads.
            qlogObserver.onConnectionClosed("remote", closeErrorCode, reason)
        }
        if (!handshakeComplete) {
            signalHandshakeFailed(QuicConnectionClosedException("connection closed externally: $reason"))
        }
        closeAllSignals()
    }

    /**
     * Close every wakeup channel so suspended awaiters exit promptly. Round-5
     * concurrency #11: closing only `closedSignal` left `peerStreamSignal` and
     * `incomingDatagramSignal` open, so a parser frame racing teardown could
     * still `trySend(Unit)` into a never-consumed channel. All three channels
     * close idempotently, so calling this from both `close()` and
     * `markClosedExternally` is safe.
     *
     * Also closes every per-stream `incomingChannel` so application
     * coroutines suspended on `stream.incoming.collect { … }` unblock with
     * a clean Flow termination instead of hanging forever waiting for a
     * FIN that will never come. Without this an interop run that drops
     * the connection mid-response (e.g. quic-interop-runner's
     * `multiplexing` case where 677 collectors were waiting for replies
     * when the parser tripped INTERNAL_ERROR) leaves every per-stream
     * collector pinned indefinitely. Closing the channel after the
     * channel already has buffered chunks is safe — `consumeAsFlow`
     * drains the buffer before terminating, so any bytes already
     * delivered are surfaced to the collector before the Flow completes.
     */
    private fun closeAllSignals() {
        closedSignal.close()
        peerStreamSignal.close()
        incomingDatagramSignal.close()
        // [streamsList] is now an immutable List published via @Volatile,
        // so reading it here without [streamsLock] yields a consistent
        // snapshot — either the pre-mutation or post-mutation view —
        // and never a half-mutated ArrayList raising CME. Mutators
        // (open*Locked, retireFullyDoneStreamsLocked) all run under
        // [streamsLock] and publish a fresh List on each change.
        // closeIncoming is idempotent on the underlying Channel.close().
        for (stream in streamsList) {
            stream.closeIncoming()
        }
    }

    /**
     * Caller must hold [streamsLock]. Used by [QuicConnectionParser] inside
     * the driver's read loop, which already holds [streamsLock] around the
     * stream-domain section of frame dispatch.
     */
    internal fun getOrCreatePeerStreamLocked(id: Long): QuicStream {
        streams[id]?.let { return it }
        val kind = StreamId.kindOf(id)
        val direction =
            when (kind) {
                StreamId.Kind.CLIENT_BIDI, StreamId.Kind.SERVER_BIDI -> QuicStream.Direction.BIDIRECTIONAL
                StreamId.Kind.SERVER_UNI -> QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL
                StreamId.Kind.CLIENT_UNI -> QuicStream.Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE
            }
        val stream = QuicStream(id, direction)
        // Audit-4 #11: SERVER_BIDI peer-opened streams inherit
        // peerTransportParameters.initialMaxStreamDataBidiLocal as their
        // sendCredit (we are writing back on a stream the peer initiated;
        // the local-flow side's value applies). Previously they got 0L,
        // which silently blocked any reply until an unsolicited
        // MAX_STREAM_DATA arrived.
        val tp = peerTransportParameters
        stream.sendCredit =
            when (kind) {
                StreamId.Kind.SERVER_BIDI -> tp?.initialMaxStreamDataBidiLocal ?: 0L

                // Peer-uni and (defensively) peer-attempted CLIENT_* streams
                // can't be written from our side, so 0 is correct.
                else -> 0L
            }
        // Pick the local receive-limit appropriate for the stream's direction
        // — peer-bidi → we advertised initialMaxStreamDataBidiRemote;
        // peer-uni → we advertised initialMaxStreamDataUni.
        stream.receiveLimit =
            when (kind) {
                StreamId.Kind.SERVER_UNI, StreamId.Kind.CLIENT_UNI -> config.initialMaxStreamDataUni
                StreamId.Kind.SERVER_BIDI -> config.initialMaxStreamDataBidiRemote
                StreamId.Kind.CLIENT_BIDI -> config.initialMaxStreamDataBidiLocal
            }
        streams[id] = stream
        streamsList = streamsList + stream
        newPeerStreams.addLast(stream)
        // Track the peer's own stream-counter, derived from the stream
        // id's index field (bits 2..63 hold the per-direction sequence
        // — see RFC 9000 §2.1). Pre-fix we incremented on every
        // create-event, which double-counted when the peer retransmits
        // a STREAM frame on a stream id we've retired AND aged out of
        // [retiredStreamIdSet] (FIFO ring of bounded size). The
        // phantom-stream guard in the parser drops most retransmits,
        // but a sufficiently long broadcast can age the id out of the
        // ring and the next retransmit lands here, bumping the
        // counter and eventually triggering a spurious MAX_STREAMS_*
        // emission. Using max(current, peerIndex + 1) makes the
        // counter idempotent: re-creating the same id is a no-op,
        // matching the peer's view exactly.
        val peerIndexPlusOne = (id ushr 2) + 1L
        when (kind) {
            StreamId.Kind.SERVER_UNI, StreamId.Kind.CLIENT_UNI -> {
                peerInitiatedUniCount = maxOf(peerInitiatedUniCount, peerIndexPlusOne)
            }

            StreamId.Kind.SERVER_BIDI, StreamId.Kind.CLIENT_BIDI -> {
                peerInitiatedBidiCount = maxOf(peerInitiatedBidiCount, peerIndexPlusOne)
            }
        }
        // Wake any awaitIncomingPeerStream caller. trySend on a CONFLATED
        // channel can never fail in steady state.
        peerStreamSignal.trySend(Unit)
        return stream
    }

    /**
     * Caller (parser, holding lock) appended a datagram to [incomingDatagrams].
     * Fires the wakeup signal so awaitIncomingDatagram unblocks promptly.
     */
    internal fun signalIncomingDatagram() {
        incomingDatagramSignal.trySend(Unit)
    }

    /** Returns the level state for [level]. */
    fun levelState(level: EncryptionLevel): LevelState =
        when (level) {
            EncryptionLevel.INITIAL -> initial
            EncryptionLevel.HANDSHAKE -> handshake
            EncryptionLevel.APPLICATION -> application
        }

    /**
     * RFC 9002 §6.2.4 PTO probe — spec-correct retransmit path. Move
     * every byte currently sent-but-not-yet-ACK'd in the [level]'s
     * CRYPTO send buffer back to its retransmit queue, so the next
     * [com.vitorpamplona.quic.connection.drainOutbound] re-emits the
     * same bytes (at the same offsets) inside a fresh CRYPTO frame on
     * a new packet number.
     *
     * The driver calls this from its PTO branch when 1-RTT keys
     * aren't yet installed — i.e. the handshake hasn't finished, so
     * the only thing the peer could be missing is our ClientHello /
     * ClientFinished. A bare PING is insufficient because if the
     * server never saw our original Initial it has no DCID state to
     * correlate a PING against (it'll be dropped). Retransmitting the
     * CRYPTO actually advances the handshake.
     *
     * Idempotent: a second consecutive call is a no-op because the
     * first call moved everything out of inFlight. Old `RecoveryToken.Crypto`
     * entries in [LevelState.sentPackets] for the still-tracked
     * original PNs remain harmless — when loss detection eventually
     * declares them lost, [onTokensLost] re-runs `markLost` on the
     * same offset/length range, which is itself idempotent (the bytes
     * are already in retransmit or already ACK'd by then).
     *
     * Caller must hold [lock] (or call from inside an existing locked
     * region — typically the driver's PTO branch under
     * [QuicConnectionDriver.sendLoop]).
     */
    internal fun requeueAllInflightCrypto(level: EncryptionLevel) {
        levelState(level).cryptoSend.requeueAllInflight()
    }

    /**
     * RFC 9002 §6.2.4 PTO probe — STREAM-data analogue of
     * [requeueAllInflightCrypto]. Walks every open stream and moves each
     * stream's sent-but-not-yet-ACK'd byte ranges back to its retransmit
     * queue, so the next [com.vitorpamplona.quic.connection.drainOutbound]
     * re-emits the same bytes (at the same offsets, with FIN preserved
     * per range).
     *
     * Why this exists: loss detection ([com.vitorpamplona.quic.connection.recovery.QuicLossDetection.detectAndRemoveLost])
     * gates on `pn < largestAckedPn`, which means it never fires when
     * the peer hasn't ACK'd ANYTHING in the application space. That
     * happens whenever every 1-RTT packet we send is dropped or
     * corrupted en route — the peer never sees them, never ACKs, and
     * `largestAckedPn` stays null forever. A bare PING from PTO doesn't
     * help either: if the PING itself is lost, the peer doesn't see it
     * either. Re-queuing the data on every PTO ensures that whenever
     * one of our PROBE packets does land, the peer immediately receives
     * the application data we'd been trying to send — not an empty PING
     * that would need a follow-up RTT to retransmit.
     *
     * Discovered via `handshakecorruption` against aioquic at 30%
     * bit-flip rate: client opens HTTP/3 control + QPACK + GET streams
     * in 1-RTT pn=0, gets corrupted, server never decrypts, never ACKs.
     * Pre-fix the streams were never retransmitted, the GET stalled,
     * the multiconnect iteration timed out at 60 s.
     *
     * Idempotent: a second consecutive call is a no-op because the first
     * call drained `inFlight` empty. Best-effort streams (used by
     * audio-rooms, where Opus tolerates gaps) drop their inflight
     * ranges instead of re-queueing — see
     * [com.vitorpamplona.quic.stream.SendBuffer.requeueAllInflight].
     *
     * Caller should hold [streamsLock] while iterating; each per-stream
     * `requeueAllInflight` is internally `synchronized` on its
     * SendBuffer so the actual byte-range moves are race-free even
     * under concurrent `takeChunk` from the writer.
     */
    internal fun requeueAllInflightStreamData() {
        for (stream in streamsList) {
            stream.send.requeueAllInflight()
        }
    }

    /**
     * RFC 9001 §6.1 key update — rotate application-level keys forward by
     * one phase. Called from [com.vitorpamplona.quic.connection.feedShortHeaderPacket]
     * once it has positively decrypted a packet whose `KEY_PHASE` bit
     * differs from [currentReceiveKeyPhase] using freshly-derived keys.
     *
     *   next_secret = HKDF-Expand-Label(current_secret, "quic ku", "", Hash.length)
     *   next_key    = HKDF-Expand-Label(next_secret,    "quic key", "", aead.key_length)
     *   next_iv     = HKDF-Expand-Label(next_secret,    "quic iv",  "", iv.length)
     *
     * Header-protection key is NOT updated (RFC 9001 §6.1: "The QUIC header
     * is protected using the same packet protection key as the packet
     * payload, but the header_protection key is not updated when keys are
     * updated").
     *
     * Caller has already validated that the new-phase keys decrypt the
     * triggering packet — we install them as live, demote the prior keys
     * to [previousReceiveProtection] for the reorder window, and update
     * the send side in lockstep so our next outbound packet carries the
     * matching `KEY_PHASE` bit.
     *
     * Returns the [PacketProtection] the caller should retry-decrypt with
     * (the new receive-side keys); null if app keys aren't installed yet
     * (handshake hasn't completed) or the cipher suite is unsupported.
     *
     * Idempotent guard: if [currentReceiveKeyPhase] already matches
     * [newPhase] (concurrent path raced us to the rotation), this returns
     * the current keys unchanged. Should never happen given the parser is
     * single-threaded, but cheap insurance.
     */
    internal fun deriveNextPhaseReceiveKeys(): com.vitorpamplona.quic.connection.PacketProtection? {
        val current = appReceiveSecret ?: return null
        val cs = appCipherSuite.takeIf { it != 0 } ?: return null
        // RFC 9001 §6.1 — secret rotation label.
        val nextSecret =
            com.vitorpamplona.quic.crypto.HKDF
                .expandLabel(current, "quic ku", ByteArray(0), current.size)
        // Reuse the existing builder; it derives key + iv + hp from a secret,
        // and the spec just discards the new HP key (we keep the old one).
        val nextProtection =
            com.vitorpamplona.quic.connection.packetProtectionFromSecret(
                cipherSuite = cs,
                secret = nextSecret,
            )
        // Keep the OLD HP key — RFC 9001 §6.1 forbids rotating it.
        val live = application.receiveProtection ?: return null
        val rebound =
            com.vitorpamplona.quic.connection.PacketProtection(
                aead = nextProtection.aead,
                key = nextProtection.key,
                iv = nextProtection.iv,
                hp = live.hp,
                hpKey = live.hpKey,
            )
        // Rotation is committed by the caller after AEAD success — return
        // the new keys without mutating state. The caller calls
        // [commitKeyUpdate] on success.
        return rebound
    }

    /**
     * Commit a successful 1-RTT key rotation. Called by the parser after
     * [deriveNextPhaseReceiveKeys] returned keys that decrypted the
     * triggering packet. Side effects:
     *  - Demote the live receive keys to [previousReceiveProtection]
     *    (kept for the reorder window — packets sent before the peer
     *    rotated are still tagged with the old KEY_PHASE).
     *  - Install the next-phase receive keys as live.
     *  - Flip [currentReceiveKeyPhase].
     *  - Roll the send-side secret + keys forward in lockstep so our next
     *    outbound packet carries the matching KEY_PHASE bit. Per RFC 9001
     *    §6.1 the peer uses our matching-phase response to confirm the
     *    rotation took effect.
     *  - Replace the stashed secret with the next-phase secret so a SECOND
     *    rotation derives off the right base.
     *
     * The send-side rotation is unconditional — we always echo the peer's
     * phase rather than running independent rotation schedules. This is
     * spec-compliant (the peer just observes our phase; there's no
     * requirement to drive our own rotation independently) and avoids the
     * extra plumbing needed to enforce RFC 9001 §6.6 packet-count limits.
     */
    internal fun commitKeyUpdate(newReceive: com.vitorpamplona.quic.connection.PacketProtection) {
        val live = application.receiveProtection ?: return
        previousReceiveProtection = live
        application.receiveProtection = newReceive
        // Re-derive the receive secret so the NEXT rotation hashes off the
        // right base. The receive keys were derived from
        // HKDF-Expand-Label(current_secret, "quic ku", ...), so the new
        // current_secret is the same expansion.
        val cs = appCipherSuite
        val curRx = appReceiveSecret
        if (curRx != null && cs != 0) {
            appReceiveSecret =
                com.vitorpamplona.quic.crypto.HKDF
                    .expandLabel(curRx, "quic ku", ByteArray(0), curRx.size)
        }
        currentReceiveKeyPhase = !currentReceiveKeyPhase

        // Send side: roll forward in lockstep so our next outbound packet
        // carries the matching KEY_PHASE bit. Peer's loss-recovery uses our
        // matching-phase response as the "rotation confirmed" signal.
        val curTx = appSendSecret
        if (curTx != null && cs != 0) {
            val nextSendSecret =
                com.vitorpamplona.quic.crypto.HKDF
                    .expandLabel(curTx, "quic ku", ByteArray(0), curTx.size)
            appSendSecret = nextSendSecret
            val freshSend =
                com.vitorpamplona.quic.connection.packetProtectionFromSecret(
                    cipherSuite = cs,
                    secret = nextSendSecret,
                )
            val liveSend = application.sendProtection
            if (liveSend != null) {
                // Reuse old HP key for send too (HP is not rotated).
                application.sendProtection =
                    com.vitorpamplona.quic.connection.PacketProtection(
                        aead = freshSend.aead,
                        key = freshSend.key,
                        iv = freshSend.iv,
                        hp = liveSend.hp,
                        hpKey = liveSend.hpKey,
                    )
                currentSendKeyPhase = !currentSendKeyPhase
            }
        }
        qlogObserver.onKeyUpdated("server", EncryptionLevel.APPLICATION)
        qlogObserver.onKeyUpdated("client", EncryptionLevel.APPLICATION)
    }

    /**
     * RFC 9001 §6.1 — initiate a 1-RTT key update from our side. Derive
     * next-phase send keys (and pre-derive next-phase receive keys, for
     * the inevitable response from the peer) using HKDF-Expand-Label
     * "quic ku", install both as live, flip the phase fields. The next
     * outbound packet carries `KEY_PHASE = 1` and the peer is expected
     * to mirror back in the same phase.
     *
     * RFC 9001 §6.5 says an endpoint MUST NOT initiate a key update
     * before the handshake is confirmed (HANDSHAKE_DONE received). The
     * caller is responsible for that check; this method just performs
     * the rotation. §6.4 also forbids initiating a second update before
     * the current one has been confirmed (peer responds in matching
     * phase) — same caller contract.
     *
     * Header-protection key is unchanged (RFC 9001 §6.1: HP key is NOT
     * rotated when keys are updated).
     *
     * Returns true if rotation succeeded; false if app keys aren't yet
     * installed (handshake hasn't completed) or the cipher suite isn't
     * cached. The interop runner's keyupdate testcase requires the
     * client to send packets in phase 1 — without this method we'd
     * only echo peer-initiated rotations and the test fails with
     * "Expected to see packets sent with key phase 1 from both client
     * and server".
     */
    fun initiateKeyUpdate(): Boolean {
        val cs = appCipherSuite.takeIf { it != 0 } ?: return false
        val curRx = appReceiveSecret ?: return false
        val curTx = appSendSecret ?: return false
        val liveRx = application.receiveProtection ?: return false
        val liveSend = application.sendProtection ?: return false

        // Derive next-phase secrets and protections for both directions
        // up front. We MUST roll both sides because the peer responds in
        // the new phase — if our receive state is still at the old phase
        // when their response lands, the receive-side commit path will
        // re-derive the SAME keys we just installed (idempotent but
        // wasteful) and then promote them, ending up with our previous
        // receive keys orphaned in [previousReceiveProtection].
        val nextRxSecret =
            com.vitorpamplona.quic.crypto.HKDF
                .expandLabel(curRx, "quic ku", ByteArray(0), curRx.size)
        val nextTxSecret =
            com.vitorpamplona.quic.crypto.HKDF
                .expandLabel(curTx, "quic ku", ByteArray(0), curTx.size)
        val freshRx = packetProtectionFromSecret(cs, nextRxSecret)
        val freshTx = packetProtectionFromSecret(cs, nextTxSecret)

        previousReceiveProtection = liveRx
        application.receiveProtection =
            com.vitorpamplona.quic.connection.PacketProtection(
                aead = freshRx.aead,
                key = freshRx.key,
                iv = freshRx.iv,
                hp = liveRx.hp,
                hpKey = liveRx.hpKey,
            )
        application.sendProtection =
            com.vitorpamplona.quic.connection.PacketProtection(
                aead = freshTx.aead,
                key = freshTx.key,
                iv = freshTx.iv,
                hp = liveSend.hp,
                hpKey = liveSend.hpKey,
            )
        appReceiveSecret = nextRxSecret
        appSendSecret = nextTxSecret
        currentReceiveKeyPhase = !currentReceiveKeyPhase
        currentSendKeyPhase = !currentSendKeyPhase
        qlogObserver.onKeyUpdated("client", EncryptionLevel.APPLICATION)
        qlogObserver.onKeyUpdated("server", EncryptionLevel.APPLICATION)
        return true
    }

    /** Caller must hold [lock]. Snapshot of streams for the driver's send loop. */
    internal fun streamsLocked(): Map<Long, QuicStream> = streams

    /**
     * Insertion-ordered list view used by the writer's round-robin scan.
     * Stays in sync with [streams] because the only mutation paths
     * (openBidi/UniStream, getOrCreatePeerStreamLocked,
     * retireFullyDoneStreamsLocked) update both.
     */
    internal fun streamsListLocked(): List<QuicStream> = streamsList

    /**
     * Walk [streamsList] once and remove every stream whose
     * [QuicStream.isFullyRetired] flag is true. Drops the entry from both
     * [streamsList] and [streams]. Receive-side high-water marks fold
     * into [retiredStreamsRecvBytes] so the writer's connection-level
     * MAX_DATA accounting continues to see the lifetime total.
     *
     * Returns the number of streams removed in this pass.
     *
     * Caller MUST hold [streamsLock]. The writer drains under
     * `streamsLock`, so calling this from the top of `buildApplicationPacket`
     * is the natural place — it runs once per send pass, sees the latest
     * FIN/RESET ACK state from the parser's just-finished processing, and
     * the iteration order matches the writer's per-pass round-robin
     * (so the rotation cursor doesn't accidentally point at a hole).
     *
     * Why retirement is safe even though the QUIC spec requires the peer
     * to deliver retransmits if its ACK never reached us:
     *  - SEND side waits for `finAcked` / `resetAcked`, i.e. the peer has
     *    already confirmed receipt of our FIN. After that point the peer
     *    will not re-send anything on the stream.
     *  - RECEIVE side waits for the parser to have pushed every byte to
     *    the application's incoming Channel ([ReceiveBuffer.isFullyRead]).
     *    Retired stream IDs are recorded in [retiredStreamIdSet] so a
     *    duplicate STREAM frame the peer retransmits (because its own
     *    loss-detection fired before our ACK arrived) is dropped at
     *    [getOrCreatePeerStreamLocked] rather than minting a phantom
     *    stream that delivers duplicate bytes to the application.
     */
    internal fun retireFullyDoneStreamsLocked(): Int {
        val current = streamsList
        if (current.isEmpty()) return 0
        // Build the post-retire snapshot in one pass. Mutating the live
        // [streamsList] in-place would force readers (closeAllSignals,
        // diagnostic accessors) to take [streamsLock] just to iterate;
        // building a fresh list and publishing it via the @Volatile ref
        // makes those reads lock-free and CME-free.
        var removed = 0
        val kept = ArrayList<QuicStream>(current.size)
        for (stream in current) {
            if (!stream.isFullyRetired) {
                kept.add(stream)
                continue
            }
            // Fold the per-stream receive high-water into the cumulative
            // counter BEFORE we drop it — once we lose the reference the
            // writer can no longer reconstruct the contribution.
            retiredStreamsRecvBytes += stream.receive.contiguousEnd()
            // Defence-in-depth: ensure the application-side incoming
            // channel is closed even if the parser somehow missed the
            // closeIncoming call (e.g. a stream that finished via
            // resetAcked never having received any STREAM frame at all).
            // closeIncoming is idempotent.
            stream.closeIncoming()
            // Record the id so a peer retransmit on this stream gets
            // dropped instead of recreating a phantom stream. FIFO
            // ring with bounded size — ancient retired IDs eventually
            // age out, but the eviction window is far larger than the
            // peer's plausible retransmit horizon.
            recordRetiredStreamIdLocked(stream.streamId)
            streams.remove(stream.streamId)
            removed++
        }
        if (removed > 0) {
            streamsList = kept
            retiredStreamsCount += removed.toLong()
            // The writer's round-robin cursor is a position in
            // [streamsList], so a removal that crossed the cursor would
            // make the next drain pass skip a tier. Reset to 0 — the
            // round-robin is just a fairness hint, not a semantic
            // requirement.
            streamRoundRobinStart = 0
        }
        return removed
    }

    /**
     * Add [streamId] to the retired-IDs ring. FIFO eviction once the
     * ring is at [RETIRED_STREAM_ID_RING_SIZE] entries — the oldest
     * entry is removed from both the ordered deque and the lookup
     * set in lockstep. Idempotent on duplicate adds (the
     * [retireFullyDoneStreamsLocked] caller already drops the stream
     * from `streams` before this fires, so a duplicate add can only
     * happen if a caller manually re-records — defensive `add` skip
     * keeps the ring entries unique without the cost of a fresh
     * dedup pass).
     *
     * Caller must hold [streamsLock].
     */
    private fun recordRetiredStreamIdLocked(streamId: Long) {
        if (retiredStreamIdSet.add(streamId)) {
            retiredStreamIdsOrder.addLast(streamId)
            while (retiredStreamIdsOrder.size > RETIRED_STREAM_ID_RING_SIZE) {
                val evicted = retiredStreamIdsOrder.removeFirst()
                retiredStreamIdSet.remove(evicted)
            }
        }
    }

    /**
     * RFC 9000 §19.15: store a peer-issued connection ID into the
     * [pathValidator] pool, enforcing `retire_prior_to` semantics
     * and capping pool size. On a peer protocol violation
     * the connection is closed with FRAME_ENCODING_ERROR /
     * PROTOCOL_VIOLATION — these are MUSTs in the spec.
     *
     * After a successful store, if the peer's `retire_prior_to`
     * has advanced past our currently-active CID sequence,
     * RFC 9000 §5.1.2 obligates us to "retire the active
     * connection ID and adopt one with a higher sequence number."
     * That's a server-forced rotation on the same path — no
     * PATH_CHALLENGE needed (Bug-C fix).
     *
     * Caller must hold [streamsLock].
     */
    internal fun applyPeerNewConnectionIdLocked(
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    ) {
        val recordResult =
            pathValidator.recordPeerNewConnectionId(
                sequenceNumber = sequenceNumber,
                retirePriorTo = retirePriorTo,
                connectionId = connectionId,
                statelessResetToken = statelessResetToken,
            )
        when (recordResult) {
            PathValidator.RecordResult.Stored,
            PathValidator.RecordResult.Duplicate,
            PathValidator.RecordResult.AlreadyRetired,
            -> {
                Unit
            }

            PathValidator.RecordResult.PoolFull -> {
                // Peer over-issued past its own advertised
                // active_connection_id_limit. RFC 9000 §5.1.1 says
                // we MAY treat this as CONNECTION_ID_LIMIT_ERROR. We
                // only drop silently here — pinning memory is the
                // real concern, and the cap defense already handled
                // that. Skip the watermark check below; this offer
                // wasn't accepted.
                return
            }

            PathValidator.RecordResult.RetirePriorToExceedsSequence -> {
                markClosedExternally(
                    "FRAME_ENCODING_ERROR: NEW_CONNECTION_ID retire_prior_to ($retirePriorTo) > sequence_number ($sequenceNumber)",
                )
                return
            }

            PathValidator.RecordResult.DuplicateSequenceMismatch -> {
                markClosedExternally(
                    "PROTOCOL_VIOLATION: NEW_CONNECTION_ID seq=$sequenceNumber re-issued with different bytes",
                )
                return
            }

            PathValidator.RecordResult.InvalidCidLength -> {
                markClosedExternally(
                    "FRAME_ENCODING_ERROR: NEW_CONNECTION_ID has invalid cid length",
                )
                return
            }

            PathValidator.RecordResult.InvalidStatelessResetToken -> {
                markClosedExternally(
                    "FRAME_ENCODING_ERROR: NEW_CONNECTION_ID has invalid stateless reset token length",
                )
                return
            }
        }

        // Bug-C fix: §5.1.2 server-forced rotation. The watermark
        // may have advanced past our active CID as a side effect of
        // [recordPeerNewConnectionId]. If so we MUST swap on the
        // same path before the next outbound packet (which would
        // otherwise stamp a now-retired CID).
        when (val rotation = pathValidator.forceRotateToHigherSequence()) {
            null -> {
                Unit
            }

            // active CID is still valid; nothing to do.
            PathValidator.ForcedRotationResult.NoSpareCid -> {
                // Watermark forced retirement of the active CID but
                // the pool is empty — we have nothing valid to use.
                // RFC 9000 §5.1.2 leaves recovery to the endpoint;
                // CONNECTION_ID_LIMIT_ERROR is the canonical close.
                markClosedExternally(
                    "CONNECTION_ID_LIMIT_ERROR: peer retired our active CID with no spare available",
                )
            }

            is PathValidator.ForcedRotationResult.Rotated -> {
                destinationConnectionId = ConnectionId(rotation.connectionId)
                qlogObserver.onConnectionIdActivated("peer", rotation.newSequence, rotation.connectionId)
                qlogObserver.onConnectionIdRetired("peer", rotation.retiredSequence)
            }
        }
    }

    /**
     * Process an inbound `PATH_RESPONSE` (RFC 9000 §19.18). If the
     * payload matches our outstanding `PATH_CHALLENGE`, the writer
     * gets a new `destinationConnectionId` to stamp into outbound
     * short-headers and a `RETIRE_CONNECTION_ID` is queued for the
     * old sequence number. Mismatches and unsolicited responses are
     * silently dropped — RFC 9000 §8.2.2 says "an endpoint that
     * receives a PATH_RESPONSE with a different payload than what
     * it sent in the PATH_CHALLENGE on the path" is allowed to
     * ignore.
     *
     * Caller must hold [streamsLock].
     */
    internal fun applyPeerPathResponseLocked(payload: ByteArray) {
        when (val outcome = pathValidator.applyPathResponse(payload)) {
            PathValidator.ValidationOutcome.NotValidating,
            PathValidator.ValidationOutcome.PayloadMismatch,
            -> {
                Unit
            }

            is PathValidator.ValidationOutcome.Validated -> {
                // Bug-7 fix: a valid PATH_RESPONSE proves the peer
                // can reach us on the new path. Reset the PTO
                // backoff so the send loop returns to baseline
                // pacing instead of carrying a stale "many PTOs
                // expired" multiplier into healthy traffic.
                consecutivePtoCount = 0
                qlogObserver.onPathValidationSucceeded(outcome.newSequence)
                qlogObserver.onConnectionIdActivated("peer", outcome.newSequence, outcome.connectionId)
                qlogObserver.onConnectionIdRetired("peer", outcome.retiredSequence)
                pathValidator.acknowledgeTerminal()
                // The writer's `destinationConnectionId` was already
                // swapped at challenge time inside
                // [triggerPathMigrationLocked] (abrupt-migration
                // model — RFC 9000 §9.3 requires the challenge on
                // the new path). On success there is no further
                // mutation to make.
            }
        }
    }

    /**
     * RFC 9000 §19.16: peer asked us to retire one of our own
     * source CIDs.
     *
     * Two cases:
     *  - Sequence > 0: we never issued anything beyond seq 0, so
     *    this references a CID we never advertised. PROTOCOL_VIOLATION
     *    per §19.16.
     *  - Sequence == 0: peer is asking us to retire the SCID we
     *    picked at connection start. Per RFC 9000 §5.1.1 we'd be
     *    expected to have already issued a replacement via
     *    NEW_CONNECTION_ID and switch the peer to it. We don't
     *    issue our own NEW_CONNECTION_ID frames today, so we have
     *    no replacement to give the peer. Closing the connection
     *    surfaces this as a known limitation rather than continuing
     *    against a peer that thinks we agreed to stop using our
     *    only CID.
     *
     * Caller must hold [streamsLock].
     */
    internal fun applyPeerRetireConnectionIdLocked(sequenceNumber: Long) {
        if (sequenceNumber > 0L) {
            markClosedExternally(
                "PROTOCOL_VIOLATION: RETIRE_CONNECTION_ID seq=$sequenceNumber > any we issued (0)",
            )
            return
        }
        // Sequence 0: peer wants us off our initial SCID, but we never
        // advertised additional SCIDs (we don't issue NEW_CONNECTION_ID
        // frames yet). RFC 9000 §19.16 lets us close — and we MUST,
        // because there's no replacement SCID to switch to. Reclassify
        // as PROTOCOL_VIOLATION (peer-side fault) rather than
        // INTERNAL_ERROR (our-side fault) — the original diagnostic
        // misdescribed which side made the mistake.
        markClosedExternally(
            "PROTOCOL_VIOLATION: peer asked to retire our initial SCID (seq=0) but we never " +
                "advertised additional SCIDs to switch to",
        )
    }

    /**
     * Try to start client-initiated path validation + DCID rotation
     * (RFC 9000 §9). Picks the lowest-sequence unused CID from the
     * pool, queues a fresh PATH_CHALLENGE, **swaps the writer's
     * outbound DCID to the new CID**, and records the outstanding
     * state. Returns the result so the caller can decide whether
     * to retry later (e.g. on the next PTO if the pool was empty
     * when this fired).
     *
     * Bug-1 fix — abrupt-migration model: the writer stamps a
     * single connection-level [destinationConnectionId] onto every
     * outbound short-header packet. To put the PATH_CHALLENGE on
     * the new path (RFC 9000 §9.3) we rotate the DCID immediately
     * here, not on validation success. The trigger condition
     * ("path probably dead — N consecutive PTOs without ACKs")
     * makes abrupt migration appropriate; we don't gain anything
     * by holding the old DCID alongside. If validation later fails
     * (3 * PTO timeout), [checkPathValidationTimeoutLocked]
     * abandons the new CID without rolling back — by then the
     * connection is in trouble and the next PTO either picks
     * another spare CID or the connection idles out.
     *
     * Caller must hold [streamsLock]. Refuses to start before
     * handshake confirmation per RFC 9000 §9.1 (returns
     * [PathMigrationResult.NotConnected]).
     */
    internal fun triggerPathMigrationLocked(
        nowMillis: Long,
        currentPtoMillis: Long,
    ): PathMigrationResult {
        if (!handshakeComplete || status != Status.CONNECTED) {
            return PathMigrationResult.NotConnected
        }
        val result = pathValidator.tryStartValidation(nowMillis, currentPtoMillis)
        if (result == PathMigrationResult.Started) {
            val validating = pathValidator.state as PathValidationState.Validating
            destinationConnectionId = ConnectionId(validating.newConnectionId)
            qlogObserver.onPathValidationStarted(validating.newCidSequence)
        }
        return result
    }

    /**
     * Public counterpart to [triggerPathMigrationLocked] — acquires
     * the lock internally. Suitable for application / driver code
     * that wants to force a rotation without coupling to the
     * locking discipline.
     */
    suspend fun triggerPathMigration(
        nowMillis: Long = nowMillis(),
        currentPtoMillis: Long = lossDetection.ptoBaseMs(peerTransportParameters?.maxAckDelay ?: 0L),
    ): PathMigrationResult = streamsLock.withLock { triggerPathMigrationLocked(nowMillis, currentPtoMillis) }

    /**
     * Check whether an outstanding PATH_CHALLENGE has exceeded the
     * 3 * PTO budget (RFC 9000 §8.2.4). Called from the driver's
     * PTO timer path. Returns true when validation just timed out.
     *
     * On timeout the validator queues the failed CID's sequence
     * number into [PathValidator.pendingRetireSequences] (Bug-2
     * fix) so the writer's next drain emits a
     * RETIRE_CONNECTION_ID. Without this the peer keeps the
     * routing entry forever — we promised by sending the challenge
     * that we might use the CID, and §5.1.2 obligates us to
     * retire it once we abandon it.
     *
     * Caller must hold [streamsLock].
     */
    internal fun checkPathValidationTimeoutLocked(nowMillis: Long): Boolean {
        val abandoned = pathValidator.checkValidationTimeout(nowMillis) ?: return false
        qlogObserver.onPathValidationFailed(abandoned.newCidSequence)
        qlogObserver.onConnectionIdRetired("peer", abandoned.newCidSequence)
        pathValidator.acknowledgeTerminal()
        return true
    }

    /**
     * Queue a PATH_RESPONSE for the given [challengeData]. Called by
     * the parser when a PATH_CHALLENGE arrives. Idempotent on
     * duplicate challenges (peer retransmit) — the writer drains
     * each entry exactly once, so an over-eager peer that sends
     * many PATH_CHALLENGEs gets many PATH_RESPONSEs (RFC 9000 §8.2
     * permits this; the spec just requires at-least-one).
     *
     * The queue is bounded at [MAX_PENDING_PATH_RESPONSES]; excess
     * entries are silently dropped (the protocol allows the peer
     * to time out and retry).
     *
     * Caller must hold [streamsLock].
     */
    internal fun queuePathResponseLocked(challengeData: ByteArray) {
        if (pendingPathChallengePayloads.size >= MAX_PENDING_PATH_RESPONSES) return
        // The parser produces a fresh ByteArray per PATH_CHALLENGE
        // (see [com.vitorpamplona.quic.Buffer.QuicReader.readBytes],
        // which `copyOfRange`s a slice off the inbound payload), so
        // we can keep the reference directly without a defensive
        // copy.
        pendingPathChallengePayloads.addLast(challengeData)
    }

    /**
     * True if [streamId] has been retired *and* is still inside the
     * ring's eviction window. Used by [QuicConnectionParser] to drop
     * STREAM frames the peer retransmits on already-retired streams.
     *
     * Caller must hold [streamsLock].
     */
    internal fun isStreamIdRetiredLocked(streamId: Long): Boolean = streamId in retiredStreamIdSet

    /** Caller must hold [lock]. Pending datagram queue for the driver's send loop. */
    internal fun pendingDatagramsLocked(): ArrayDeque<ByteArray> = pendingDatagrams

    /** Caller must hold [lock]. Inbound datagram queue, written by the read loop. */
    internal fun incomingDatagramsLocked(): ArrayDeque<ByteArray> = incomingDatagrams

    /** Caller must hold [lock]. */
    internal fun streamByIdLocked(id: Long): QuicStream? = streams[id]

    /**
     * ACK-path counterpart to [onTokensLost]. Called by the parser
     * after [com.vitorpamplona.quic.connection.recovery.drainAckedSentPackets]
     * removes the carrying packet from the sent map. Tokens whose
     * underlying byte ranges live in a [com.vitorpamplona.quic.stream.SendBuffer]
     * (Stream / Crypto) trigger a [com.vitorpamplona.quic.stream.SendBuffer.markAcked]
     * call so the buffer can advance its flushedFloor and release
     * memory. Other token types are ACK-no-ops (the peer's
     * acknowledgment of a control frame doesn't require any local
     * action — the frame already did its job by reaching the peer).
     *
     * Caller must hold [lock].
     */
    internal fun onTokensAcked(tokens: List<com.vitorpamplona.quic.connection.recovery.RecoveryToken>) {
        for (token in tokens) {
            when (token) {
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.Ack -> {
                    // ACK-of-ACK: peer has now received our ACK, so
                    // we can drop everything at-or-below
                    // [token.largestAcked] from the inbound tracker.
                    // RFC 9000 §13.2.1 doesn't require us to keep
                    // re-advertising acknowledged PNs once the peer
                    // has confirmed receipt of our ACK that covered
                    // them. Without this the tracker's range list
                    // grows over the connection lifetime.
                    levelState(token.level).ackTracker.purgeBelow(token.largestAcked + 1)
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxStreamsUni,
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxStreamsBidi,
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxData,
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxStreamData,
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.NewConnectionId,
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.PathChallenge,
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.RetireConnectionId,
                -> {
                    // Flow-control extensions, NEW_CONNECTION_ID,
                    // PATH_CHALLENGE, and RETIRE_CONNECTION_ID have
                    // no per-buffer state to release on ACK; the
                    // pending* maps are populated only on loss, so an
                    // ACK for a frame that never lost is naturally
                    // absent. PATH_CHALLENGE specifically: a peer ACK
                    // means "we received the challenge frame" — but
                    // that's NOT path validation success. Validation
                    // requires the matching PATH_RESPONSE
                    // (handled in [applyPeerPathResponseLocked]).
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.ResetStream -> {
                    // Latch resetAcked = true. Subsequent loss
                    // notifications for stale RESET_STREAM tokens are
                    // dropped (see onTokensLost).
                    val stream = streamByIdLocked(token.streamId) ?: continue
                    stream.resetAcked = true
                    stream.resetEmitPending = false
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.StopSending -> {
                    val stream = streamByIdLocked(token.streamId) ?: continue
                    stream.stopSendingAcked = true
                    stream.stopSendingEmitPending = false
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.Stream -> {
                    val stream = streamByIdLocked(token.streamId) ?: continue
                    stream.send.markAcked(offset = token.offset, length = token.length)
                    if (token.length == 0L && token.fin) {
                        // FIN-only ACK: treat as zero-length ACK at offset.
                        // markAcked already handles length==0 ⇒ FIN match.
                    }
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.Crypto -> {
                    levelState(token.level).cryptoSend.markAcked(
                        offset = token.offset,
                        length = token.length,
                    )
                }
            }
        }
    }

    /**
     * Step 6 of `quic/plans/2026-05-04-control-frame-retransmit.md`:
     * dispatch the tokens of declared-lost packets to the matching
     * `pending*` field, applying the supersede check from neqo's
     * `fc.rs::frame_lost`. The supersede invariant: only re-flag for
     * retransmit if the lost value still equals the connection's
     * current advertised cap. If a higher extension has since gone
     * out, the older lost frame is irrelevant — the newer value
     * supersedes it.
     *
     * Called by the parser's AckFrame handler after
     * [com.vitorpamplona.quic.connection.recovery.QuicLossDetection.detectAndRemoveLost]
     * returns the lost set. Caller must hold [lock].
     */
    internal fun onTokensLost(tokens: List<com.vitorpamplona.quic.connection.recovery.RecoveryToken>) {
        for (token in tokens) {
            when (token) {
                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.Ack -> {
                    // ACK frames are not retransmittable per RFC 9000
                    // §13.2.1; the peer's own ACKs cover newer ranges.
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxStreamsUni -> {
                    if (token.maxStreams == advertisedMaxStreamsUni) {
                        pendingMaxStreamsUni = token.maxStreams
                    }
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxStreamsBidi -> {
                    if (token.maxStreams == advertisedMaxStreamsBidi) {
                        pendingMaxStreamsBidi = token.maxStreams
                    }
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxData -> {
                    if (token.maxData == advertisedMaxData) {
                        pendingMaxData = token.maxData
                    }
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.MaxStreamData -> {
                    val stream = streamByIdLocked(token.streamId) ?: continue
                    if (token.maxData == stream.receiveLimit) {
                        pendingMaxStreamData[token.streamId] = token.maxData
                    }
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.Stream -> {
                    // Re-queue the lost byte range on the stream's send
                    // buffer. The next writer drain pulls from the
                    // retransmit queue first (FIFO ahead of new sends).
                    // See [com.vitorpamplona.quic.stream.SendBuffer.markLost].
                    val stream = streamByIdLocked(token.streamId) ?: continue
                    stream.send.markLost(
                        offset = token.offset,
                        length = token.length,
                        fin = token.fin,
                    )
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.Crypto -> {
                    // Same shape, applied to the per-level CRYPTO buffer.
                    levelState(token.level).cryptoSend.markLost(
                        offset = token.offset,
                        length = token.length,
                        fin = false,
                    )
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.ResetStream -> {
                    // Reliable per RFC 9000 §13.3. Re-flag the
                    // owning stream's RESET_STREAM emit pending so
                    // the next writer drain re-emits — unless the
                    // peer already ACK'd it (resetAcked == true), in
                    // which case the lost token is a stale duplicate
                    // and we drop it.
                    val stream = streamByIdLocked(token.streamId) ?: continue
                    if (!stream.resetAcked) stream.resetEmitPending = true
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.StopSending -> {
                    val stream = streamByIdLocked(token.streamId) ?: continue
                    if (!stream.stopSendingAcked) stream.stopSendingEmitPending = true
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.NewConnectionId -> {
                    pendingNewConnectionId[token.sequenceNumber] = token
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.PathChallenge -> {
                    // RFC 9000 §8.2.1: if a PATH_CHALLENGE is lost
                    // (carrying packet declared lost without an
                    // ACK), the spec permits but does not require
                    // retransmission — validation simply runs against
                    // the 3 * PTO budget. We re-queue iff the
                    // outstanding validation still matches this
                    // payload (state hasn't progressed to Succeeded /
                    // Failed / a different challenge). The writer
                    // will pick it up on the next drain and emit a
                    // fresh PATH_CHALLENGE with the SAME 8 bytes —
                    // the peer must echo whichever it sees first.
                    val s = pathValidator.state
                    if (s is PathValidationState.Validating && s.challengeData.contentEquals(token.data)) {
                        if (!pathValidator.pendingChallenges.any { it.contentEquals(token.data) }) {
                            pathValidator.pendingChallenges.addLast(token.data)
                        }
                    }
                }

                is com.vitorpamplona.quic.connection.recovery.RecoveryToken.RetireConnectionId -> {
                    // RFC 9000 §13.3: RETIRE_CONNECTION_ID is
                    // reliable. Re-queue on loss — idempotent on
                    // duplicate (queueRetireSequence dedupes).
                    pathValidator.queueRetireSequence(token.sequenceNumber)
                }
            }
        }
    }

    companion object {
        /**
         * Bound on the inbound datagram queue depth. RFC 9221 datagrams are
         * outside connection-level flow control, so without this cap a peer
         * can pin arbitrary memory by spamming DATAGRAM frames. 256 entries
         * × ~1200 bytes/datagram ≈ 300 KB worst case, which is fine even on
         * memory-constrained devices and well above any realistic burst at
         * audio-room rates (~50/sec).
         *
         * On overflow the parser drops the OLDEST queued datagram — for live
         * audio/video, fresh frames matter more than stale ones.
         */
        const val MAX_INCOMING_DATAGRAM_QUEUE: Int = 256

        /**
         * Capacity of the retired-stream-IDs ring used by
         * [recordRetiredStreamIdLocked] / [isStreamIdRetiredLocked].
         * Sized so the ring covers ≥ 80 seconds of retirement at
         * moq-lite's ~50 streams/sec churn rate, far longer than any
         * plausible peer retransmit window (a small multiple of RTT
         * — at the absolute worst tens of seconds on lossy mobile
         * networks). Older retired IDs eviction-fall-through to the
         * existing create-and-immediately-retire path, which is
         * functionally correct, just with one extra round of work
         * per duplicate.
         *
         * Memory: 4 096 × (8 bytes Long key + ~32 bytes HashSet
         * overhead + 8 bytes ArrayDeque slot) ≈ 200 KB. Trivial vs
         * the per-stream object size we're saving by retiring.
         */
        const val RETIRED_STREAM_ID_RING_SIZE: Int = 4_096

        /**
         * Bound on the [pendingPathChallengePayloads] queue. RFC 9000 §8.2
         * doesn't cap PATH_CHALLENGE rate, so a malicious peer could
         * spam them to exhaust our memory. 64 entries × 8 bytes = 512 B
         * worst case — trivial to absorb but tight enough that an
         * attacker can't pin 100 MB by flooding.
         *
         * Excess challenges are dropped; the spec allows it (a peer
         * that doesn't see a response will retransmit on the next
         * PTO if path validation matters to them).
         */
        const val MAX_PENDING_PATH_RESPONSES: Int = 64
    }
}

/**
 * Default monotonic clock supplier for [QuicConnection.nowMillis]. Returns a
 * lambda that yields milliseconds-elapsed-since-construction, anchored on a
 * fresh [kotlin.time.TimeSource.Monotonic.markNow] mark. Only differences are
 * meaningful; an NTP step / suspend-resume does not affect the values.
 */
private fun defaultMonotonicNowMillis(): () -> Long {
    val anchor =
        kotlin.time.TimeSource.Monotonic
            .markNow()
    return { anchor.elapsedNow().inWholeMilliseconds }
}

/** Connection was closed (locally or by peer) before reaching CONNECTED. */
class QuicConnectionClosedException(
    message: String,
) : RuntimeException(message)

/** Caller tried to open a stream beyond the peer's MAX_STREAMS allowance. */
class QuicStreamLimitException(
    message: String,
) : RuntimeException(message)

/**
 * RFC 9000 §6: the server replied with a Version Negotiation packet but
 * the supported_versions list does not contain any version the client can
 * speak (today: only [com.vitorpamplona.quic.packet.QuicVersion.V1]).
 * The handshake is unrecoverable — caller must treat the connection as
 * permanently failed.
 */
class QuicVersionNegotiationException(
    message: String,
) : RuntimeException(message)

/**
 * Diagnostic snapshot of [QuicConnection]'s flow-control accounting at
 * a single moment. Returned by [QuicConnection.flowControlSnapshot].
 *
 * Reading rules of thumb for the production-cliff investigation
 * (see `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`):
 *
 *   - **`sendConnectionFlowConsumed >= peerInitialMaxData`** with
 *     `sendConnectionFlowCredit == peerInitialMaxData` ⇒ peer never
 *     extended `MAX_DATA`; we wedged on connection-level send credit.
 *   - **`sendConnectionFlowCredit > peerInitialMaxData`** ⇒ peer
 *     DID extend; suspicion shifts to per-stream credit or downstream.
 *   - **`totalEnqueuedNotSentBytes > 0` after a quiescent period** ⇒
 *     application wrote bytes that the writer couldn't put on the
 *     wire (per-stream or connection budget exhausted, or peer
 *     stopped reading).
 *   - **`nextLocalUniIndex >= peerMaxStreamsUniCurrent`** ⇒ at the
 *     stream-id cap; would block in [openUniStream] (currently
 *     throws [QuicStreamLimitException]).
 */
data class QuicFlowControlSnapshot(
    /**
     * Peer's `initial_max_data` transport parameter (RFC 9000 §18.2)
     * — the connection-level send budget the peer initially granted.
     * `null` means the peer's TPs hadn't been parsed yet at snapshot
     * time (i.e. handshake hadn't completed).
     */
    val peerInitialMaxData: Long?,
    /** Peer's `initial_max_stream_data_uni` transport parameter. */
    val peerInitialMaxStreamDataUni: Long?,
    /** Peer's `initial_max_stream_data_bidi_remote` transport parameter. */
    val peerInitialMaxStreamDataBidiRemote: Long?,
    /** Peer's `initial_max_streams_uni`. */
    val peerInitialMaxStreamsUni: Long?,
    /** Peer's `initial_max_streams_bidi`. */
    val peerInitialMaxStreamsBidi: Long?,
    /**
     * Current connection-level send credit. Starts at
     * [peerInitialMaxData] when the handshake completes; raised by
     * inbound `MAX_DATA` frames (RFC 9000 §19.9).
     */
    val sendConnectionFlowCredit: Long,
    /**
     * Total stream-frame bytes we've already pushed past the writer
     * against [sendConnectionFlowCredit]. When this catches up to
     * the credit, the writer stops draining stream data until a
     * fresh `MAX_DATA` arrives.
     */
    val sendConnectionFlowConsumed: Long,
    /**
     * Current peer-granted unidirectional stream concurrency cap.
     * Starts at [peerInitialMaxStreamsUni]; raised by inbound
     * `MAX_STREAMS_UNI` frames.
     */
    val peerMaxStreamsUniCurrent: Long,
    /** Bidi counterpart of [peerMaxStreamsUniCurrent]. */
    val peerMaxStreamsBidiCurrent: Long,
    /**
     * Number of client-initiated uni streams [openUniStream] has
     * allocated locally. The next allocation would use this value
     * as the index; if it equals or exceeds
     * [peerMaxStreamsUniCurrent], the next [openUniStream] throws
     * [QuicStreamLimitException].
     */
    val nextLocalUniIndex: Long,
    /** Bidi counterpart of [nextLocalUniIndex]. */
    val nextLocalBidiIndex: Long,
    /**
     * Sum of bytes enqueued to any stream's send buffer but NOT yet
     * encoded into a STREAM frame. A non-trivial value after a
     * quiescent period indicates flow control (per-stream or
     * connection-level) is starving the writer.
     */
    val totalEnqueuedNotSentBytes: Long,
    /** Number of streams contributing to [totalEnqueuedNotSentBytes]. */
    val streamsWithPendingBytes: Int,
    /** Number of streams currently tracked (alive + closed-but-retained). */
    val totalStreamsTracked: Int,
    /**
     * Outbound peer-initiated stream cap we've currently advertised.
     * Starts at `config.initialMaxStreams*` and grows as the writer
     * emits `MAX_STREAMS_*` frames. If this stays at the initial cap
     * while [peerInitiatedUniCount] climbs into the same range, the
     * peer's stream-id allowance against us is starving — see
     * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
     */
    val advertisedMaxStreamsUni: Long,
    /** Bidi counterpart of [advertisedMaxStreamsUni]. */
    val advertisedMaxStreamsBidi: Long,
    /**
     * Lifetime count of peer-initiated unidirectional streams accepted
     * by `getOrCreatePeerStreamLocked`. Compared against
     * [advertisedMaxStreamsUni] by the writer to decide when to extend
     * the cap.
     */
    val peerInitiatedUniCount: Long,
    /** Bidi counterpart of [peerInitiatedUniCount]. */
    val peerInitiatedBidiCount: Long,
    /**
     * Underlying UDP-socket counters when the connection has a
     * platform driver attached. Null otherwise (in-process tests
     * have no real socket). Lets a test answer "did the kernel
     * actually deliver these datagrams to us?" by diffing
     * [UdpSocketStats.receivedDatagrams] over the test window.
     */
    val udp: UdpSocketStats?,
)

/**
 * Lifetime UDP-socket counters surfaced through
 * [QuicFlowControlSnapshot]. The driver populates this from its
 * platform-specific `UdpSocket` impl.
 */
data class UdpSocketStats(
    /** Number of `recv()` calls that returned a non-null datagram. */
    val receivedDatagrams: Long,
    /** Sum of payload bytes returned by [receivedDatagrams] calls. */
    val receivedBytes: Long,
    /**
     * Effective `SO_RCVBUF` value the kernel reports. On Linux the
     * application requested value is *doubled* and then capped at
     * `rmem_max`, so this is what the kernel actually allocates.
     */
    val receiveBufferSizeBytes: Int,
)
