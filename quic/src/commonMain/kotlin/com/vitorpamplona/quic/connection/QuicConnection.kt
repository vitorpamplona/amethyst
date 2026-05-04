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
    val nowMillis: () -> Long = {
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
    },
    val alpnList: List<ByteArray> = listOf(TlsConstants.ALPN_H3),
) {
    val sourceConnectionId: ConnectionId = ConnectionId.random(8)
    var destinationConnectionId: ConnectionId = ConnectionId.random(8)
        internal set
    val originalDestinationConnectionId: ConnectionId = destinationConnectionId

    val initial = LevelState()
    val handshake = LevelState()
    val application = LevelState()

    var handshakeComplete: Boolean = false
        private set

    var peerTransportParameters: TransportParameters? = null
        private set

    enum class Status { HANDSHAKING, CONNECTED, CLOSING, CLOSED }

    var status: Status = Status.HANDSHAKING
        internal set

    /** App-level error code for graceful close. */
    var closeReason: String? = null
        private set
    var closeErrorCode: Long = 0L
        private set

    private val streams = mutableMapOf<Long, QuicStream>()

    /**
     * Round-4 perf #10: parallel insertion-ordered list of streams so the
     * writer's round-robin scan can index by position without
     * `streams.entries.toList()` allocating per drain. Streams are only ever
     * added (no removal in the current model), so the two stay in sync as
     * long as `getOrCreatePeerStreamLocked` and `openBidi/UniStream` append
     * to both.
     */
    private val streamsList = mutableListOf<QuicStream>()
    private var nextLocalBidiIndex: Long = 0L
    private var nextLocalUniIndex: Long = 0L

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
     * RFC 9002 RTT estimator + loss-detection algorithm. Single
     * shared instance per connection (RTT is per-path; we model a
     * single path). Per-space `largestAcked*` lives on
     * [LevelState]. Step 6 wires the loss-detection callback.
     */
    internal val lossDetection: com.vitorpamplona.quic.connection.recovery.QuicLossDetection =
        com.vitorpamplona.quic.connection.recovery
            .QuicLossDetection()

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
            }

            override fun onApplicationKeysReady(
                cipherSuite: Int,
                clientSecret: ByteArray,
                serverSecret: ByteArray,
            ) {
                application.sendProtection = packetProtectionFromSecret(cipherSuite, clientSecret)
                application.receiveProtection = packetProtectionFromSecret(cipherSuite, serverSecret)
            }

            override fun onHandshakeComplete() {
                handshakeComplete = true
                if (status == Status.HANDSHAKING) status = Status.CONNECTED
                applyPeerTransportParameters()
                handshakeDoneSignal.complete(Unit)
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
    }

    /** Begin the handshake — emits ClientHello into Initial CRYPTO. */
    fun start() {
        tls.start()
        // Drain ClientHello bytes into the Initial-level CRYPTO send buffer.
        tls.pollOutbound(TlsClient.Level.INITIAL)?.let { initial.cryptoSend.enqueue(it) }
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
     * Single mutex protecting connection-wide mutable state: streams map,
     * datagram queues, stream-id counters, status. The driver acquires this
     * around its read/send loops; public API methods listed below acquire it
     * before mutating. Internal-only methods (used only from inside the
     * driver loops) do NOT lock — caller must hold the lock.
     */
    val lock: Mutex = Mutex()

    /**
     * Allocate a new client-initiated bidirectional stream. Locked.
     *
     * Throws [QuicStreamLimitException] if the peer has not granted enough
     * bidirectional stream credit yet. Use [peerMaxStreamsBidiSnapshot] to
     * check capacity proactively if the caller wants to back-pressure rather
     * than throw.
     */
    suspend fun openBidiStream(): QuicStream =
        lock.withLock {
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
            streamsList += stream
            stream
        }

    /** Allocate a new client-initiated unidirectional (write-only) stream. Locked. */
    suspend fun openUniStream(): QuicStream =
        lock.withLock {
            if (nextLocalUniIndex >= peerMaxStreamsUni) {
                throw QuicStreamLimitException(
                    "peer-granted uni stream cap reached " +
                        "(used=$nextLocalUniIndex limit=$peerMaxStreamsUni)",
                )
            }
            val id = StreamId.build(StreamId.Kind.CLIENT_UNI, nextLocalUniIndex++)
            val stream = QuicStream(id, QuicStream.Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE)
            stream.sendCredit = peerTransportParameters?.initialMaxStreamDataUni ?: config.initialMaxStreamDataUni
            stream.receiveLimit = 0L // can't receive
            streams[id] = stream
            streamsList += stream
            stream
        }

    /** Snapshot of peer-granted bidi cap. Reads do not need the lock — long writes are atomic on every supported platform. */
    fun peerMaxStreamsBidiSnapshot(): Long = peerMaxStreamsBidi

    /** Snapshot of peer-granted uni cap. */
    fun peerMaxStreamsUniSnapshot(): Long = peerMaxStreamsUni

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
        lock.withLock {
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

    suspend fun pollIncomingPeerStream(): QuicStream? = lock.withLock { newPeerStreams.removeFirstOrNull() }

    /**
     * Suspends until a peer-initiated stream is queued OR the connection
     * closes. Returns null on close. Replaces the older `pollIncomingPeerStream
     * + delay(5)` busy-loop — this version wakes within microseconds of the
     * parser appending a stream and parks the coroutine the rest of the time.
     */
    suspend fun awaitIncomingPeerStream(): QuicStream? {
        while (true) {
            lock.withLock { newPeerStreams.removeFirstOrNull() }?.let { return it }
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
                lock.withLock { newPeerStreams.removeFirstOrNull() }?.let { return it }
                return null
            }
        }
    }

    suspend fun streamById(id: Long): QuicStream? = lock.withLock { streams[id] }

    suspend fun queueDatagram(payload: ByteArray) = lock.withLock { pendingDatagrams.addLast(payload) }

    suspend fun pollIncomingDatagram(): ByteArray? = lock.withLock { incomingDatagrams.removeFirstOrNull() }

    /**
     * Suspending counterpart of [pollIncomingDatagram]. Returns null only when
     * the connection has been closed and no more datagrams remain. Same
     * select-based wakeup pattern as [awaitIncomingPeerStream].
     */
    suspend fun awaitIncomingDatagram(): ByteArray? {
        while (true) {
            lock.withLock { incomingDatagrams.removeFirstOrNull() }?.let { return it }
            if (status == Status.CLOSED) return null
            val keepWaiting =
                select<Boolean> {
                    incomingDatagramSignal.onReceiveCatching { result -> result.isSuccess }
                    closedSignal.onReceiveCatching { false }
                }
            if (!keepWaiting) {
                lock.withLock { incomingDatagrams.removeFirstOrNull() }?.let { return it }
                return null
            }
        }
    }

    /** Initiate a graceful close. */
    suspend fun close(
        errorCode: Long,
        reason: String,
    ) {
        lock.withLock {
            if (status == Status.CLOSED || status == Status.CLOSING) return@withLock
            closeErrorCode = errorCode
            closeReason = reason
            status = Status.CLOSING
        }
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
        if (status != Status.CLOSED) status = Status.CLOSED
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
     */
    private fun closeAllSignals() {
        closedSignal.close()
        peerStreamSignal.close()
        incomingDatagramSignal.close()
    }

    /**
     * Caller must hold [lock]. Used by [QuicConnectionParser] inside the
     * driver's read loop, which already holds the connection lock.
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
        streamsList += stream
        newPeerStreams.addLast(stream)
        // Track lifetime peer-stream counts so the writer can emit a
        // refreshed MAX_STREAMS_* once the peer's usage approaches the
        // currently-advertised cap. Without this, our initial cap of
        // [config.initialMaxStreams*] is the lifetime maximum the peer
        // can open and any longer broadcast silently truncates.
        when (kind) {
            StreamId.Kind.SERVER_UNI, StreamId.Kind.CLIENT_UNI -> peerInitiatedUniCount += 1
            StreamId.Kind.SERVER_BIDI, StreamId.Kind.CLIENT_BIDI -> peerInitiatedBidiCount += 1
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

    /** Caller must hold [lock]. Snapshot of streams for the driver's send loop. */
    internal fun streamsLocked(): Map<Long, QuicStream> = streams

    /**
     * Insertion-ordered list view used by the writer's round-robin scan.
     * Stays in sync with [streams] because the only mutation paths
     * (openBidi/UniStream, getOrCreatePeerStreamLocked) append to both. No
     * remove path exists today; if/when one is added it MUST update both.
     */
    internal fun streamsListLocked(): List<QuicStream> = streamsList

    /** Caller must hold [lock]. Pending datagram queue for the driver's send loop. */
    internal fun pendingDatagramsLocked(): ArrayDeque<ByteArray> = pendingDatagrams

    /** Caller must hold [lock]. Inbound datagram queue, written by the read loop. */
    internal fun incomingDatagramsLocked(): ArrayDeque<ByteArray> = incomingDatagrams

    /** Caller must hold [lock]. */
    internal fun streamByIdLocked(id: Long): QuicStream? = streams[id]

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
                com.vitorpamplona.quic.connection.recovery.RecoveryToken.Ack -> {
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
    }
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
