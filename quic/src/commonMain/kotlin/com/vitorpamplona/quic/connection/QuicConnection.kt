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

    /**
     * Notifier completed every time the peer extends our stream cap (either
     * direction) via a MAX_STREAMS frame, OR when the connection closes.
     * Suspended [openBidiStream] / [openUniStream] callers await the current
     * notifier; once it fires, they re-acquire the lock and re-check whether
     * they have credit. The notifier is replaced after each fire so a fresh
     * await reads the next cycle.
     *
     * Only mutated under [lock]. Reading the reference is safe without the
     * lock IF the caller subsequently takes the lock and re-checks the cap —
     * the read might be stale, the await might be stale, but the loop
     * guarantees forward progress because the parser also signals after each
     * cap raise.
     */
    private var streamCapNotifier: kotlinx.coroutines.CompletableDeferred<Unit> =
        kotlinx.coroutines.CompletableDeferred()

    /**
     * Stream-limit values we owe the peer in STREAMS_BLOCKED frames. Set
     * when [openBidiStream] / [openUniStream] discover the cap is exhausted.
     * Cleared by [QuicConnectionWriter] after the frame is emitted. Per RFC
     * 9000 §19.14 we should send STREAMS_BLOCKED at most once per cap value
     * — null means "nothing to send". Reset to a new value if the cap
     * advances and we hit it again later.
     */
    internal var pendingStreamsBlockedBidi: Long? = null
    internal var pendingStreamsBlockedUni: Long? = null

    /**
     * Hook invoked when the connection wants to nudge the writer (e.g. a
     * suspended opener queued STREAMS_BLOCKED and needs the writer to
     * flush). Wired by [QuicConnectionDriver.start] and reset on close.
     * Null while the driver is not running (in-process tests etc) — the
     * connection still works, just without an external send loop.
     */
    @Volatile
    internal var sendWakeupHook: (() -> Unit)? = null

    /**
     * Wake any suspended [openBidiStream] / [openUniStream] callers. Called
     * by the parser after each MAX_STREAMS frame raises a cap, and by the
     * close path so blocked openers can throw [QuicConnectionClosedException]
     * instead of suspending forever.
     *
     * Caller must hold [lock] (the notifier ref is mutated here).
     */
    internal fun signalStreamCapLocked() {
        val old = streamCapNotifier
        streamCapNotifier = kotlinx.coroutines.CompletableDeferred()
        old.complete(Unit)
    }

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
     * Allocate a new client-initiated bidirectional stream.
     *
     * If the peer has not granted enough bidi stream credit yet, this
     * method **suspends** (not throws) until either:
     *   - an inbound MAX_STREAMS frame raises the cap (RFC 9000 §19.11),
     *     or
     *   - the connection closes — in which case
     *     [QuicConnectionClosedException] is thrown.
     *
     * Before suspending, queues a STREAMS_BLOCKED_BIDI frame (RFC 9000
     * §19.14) so the peer knows we want more credit. Without this, a
     * conservative peer that only extends MAX_STREAMS in response to
     * STREAMS_BLOCKED would never grant new credit, deadlocking the
     * client.
     */
    suspend fun openBidiStream(): QuicStream = openClientStream(uni = false)

    /**
     * Allocate a new client-initiated unidirectional (write-only) stream.
     * Suspends if the peer has exhausted our uni stream cap; see
     * [openBidiStream] for the semantics.
     */
    suspend fun openUniStream(): QuicStream = openClientStream(uni = true)

    /**
     * Shared body for [openBidiStream] / [openUniStream]. The two paths
     * are byte-for-byte symmetric except for the StreamId kind, the per-
     * direction caps + indices, the per-direction `sendCredit` source,
     * and the receive limit (uni-out streams cannot receive).
     *
     * Re-acquires the lock on every retry — required to read the current
     * `peerMaxStreams*` (mutated by the parser under the lock) and to
     * mutate `nextLocal*Index` atomically with the streams map.
     */
    private suspend fun openClientStream(uni: Boolean): QuicStream {
        while (true) {
            // Capture under lock: either we have credit and allocate, or
            // we record our blocked-at limit and grab a notifier ref to
            // await *outside* the lock (otherwise the parser couldn't
            // acquire the lock to raise the cap, deadlocking).
            val notifier: kotlinx.coroutines.CompletableDeferred<Unit>
            lock.withLock {
                if (status == Status.CLOSED) {
                    throw QuicConnectionClosedException(
                        "connection closed before stream could be allocated",
                    )
                }
                val nextIndex = if (uni) nextLocalUniIndex else nextLocalBidiIndex
                val cap = if (uni) peerMaxStreamsUni else peerMaxStreamsBidi
                if (nextIndex < cap) {
                    val id =
                        StreamId.build(
                            if (uni) StreamId.Kind.CLIENT_UNI else StreamId.Kind.CLIENT_BIDI,
                            nextIndex,
                        )
                    if (uni) nextLocalUniIndex++ else nextLocalBidiIndex++
                    val stream =
                        QuicStream(
                            id,
                            if (uni) QuicStream.Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE else QuicStream.Direction.BIDIRECTIONAL,
                        )
                    stream.sendCredit =
                        if (uni) {
                            peerTransportParameters?.initialMaxStreamDataUni ?: config.initialMaxStreamDataUni
                        } else {
                            peerTransportParameters?.initialMaxStreamDataBidiRemote ?: config.initialMaxStreamDataBidiRemote
                        }
                    stream.receiveLimit = if (uni) 0L else config.initialMaxStreamDataBidiLocal
                    streams[id] = stream
                    streamsList += stream
                    return stream
                }
                // Out of credit. Queue a STREAMS_BLOCKED frame at the
                // current cap (per RFC 9000 §19.14, "stream limit" =
                // count from MAX_STREAMS) so the peer knows we want more.
                // Replace any earlier pending entry — we only ever owe
                // the peer one STREAMS_BLOCKED per cap value.
                if (uni) {
                    pendingStreamsBlockedUni = cap
                } else {
                    pendingStreamsBlockedBidi = cap
                }
                notifier = streamCapNotifier
            }
            // Nudge the writer so STREAMS_BLOCKED actually leaves the
            // host. Without this, a quiescent connection (no other
            // writes pending) would hold the frame in the writer's
            // queue until the next unrelated wakeup.
            sendWakeupHook?.invoke()
            notifier.await()
        }
    }

    /** Snapshot of peer-granted bidi cap. Reads do not need the lock — long writes are atomic on every supported platform. */
    fun peerMaxStreamsBidiSnapshot(): Long = peerMaxStreamsBidi

    /** Snapshot of peer-granted uni cap. */
    fun peerMaxStreamsUniSnapshot(): Long = peerMaxStreamsUni

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
        // Wake any openBidiStream / openUniStream callers blocked on the
        // stream-cap notifier so they re-acquire the lock, observe
        // status == CLOSED, and throw QuicConnectionClosedException
        // instead of hanging forever.
        if (!streamCapNotifier.isCompleted) streamCapNotifier.complete(Unit)
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
