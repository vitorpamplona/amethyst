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

import com.vitorpamplona.quic.crypto.Aes128Gcm
import com.vitorpamplona.quic.crypto.AesEcbHeaderProtection
import com.vitorpamplona.quic.crypto.InitialSecrets
import com.vitorpamplona.quic.crypto.PlatformAesOneBlock
import com.vitorpamplona.quic.stream.QuicStream
import com.vitorpamplona.quic.stream.StreamId
import com.vitorpamplona.quic.tls.TlsClient
import com.vitorpamplona.quic.tls.TlsConstants
import com.vitorpamplona.quic.tls.TlsSecretsListener
import kotlinx.coroutines.CompletableDeferred
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
     * MUST be non-null for any network-facing connection. Pass an explicit
     * `null` only when the caller has audited the threat model and accepts
     * unauthenticated TLS (e.g. an in-process test loopback). There is no
     * silent default — production callers either pass a system-trust-store
     * validator or get a misconfiguration that's obvious in code review.
     */
    val tlsCertificateValidator: com.vitorpamplona.quic.tls.CertificateValidator?,
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
    private var nextLocalBidiIndex: Long = 0L
    private var nextLocalUniIndex: Long = 0L
    private val pendingDatagrams = ArrayDeque<ByteArray>()
    private val incomingDatagrams = ArrayDeque<ByteArray>()
    private var sendConnectionFlowCredit: Long = 0L
    private var receiveConnectionFlowLimit: Long = config.initialMaxData

    /** Streams the peer has opened that we haven't surfaced yet. */
    private val newPeerStreams = ArrayDeque<QuicStream>()

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
        )

    init {
        // Install Initial keys based on the random destination CID we just generated.
        val proto = InitialSecrets.derive(destinationConnectionId.bytes)
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)
        initial.sendProtection = PacketProtection(Aes128Gcm, proto.clientKey, proto.clientIv, hp, proto.clientHp)
        initial.receiveProtection = PacketProtection(Aes128Gcm, proto.serverKey, proto.serverIv, hp, proto.serverHp)
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
        peerTransportParameters = tp
        sendConnectionFlowCredit = tp.initialMaxData ?: 0L
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

    /** Allocate a new client-initiated bidirectional stream. Locked. */
    suspend fun openBidiStream(): QuicStream =
        lock.withLock {
            val id = StreamId.build(StreamId.Kind.CLIENT_BIDI, nextLocalBidiIndex++)
            val stream = QuicStream(id, QuicStream.Direction.BIDIRECTIONAL)
            stream.sendCredit = peerTransportParameters?.initialMaxStreamDataBidiRemote ?: config.initialMaxStreamDataBidiRemote
            stream.receiveLimit = config.initialMaxStreamDataBidiLocal
            streams[id] = stream
            stream
        }

    /** Allocate a new client-initiated unidirectional (write-only) stream. Locked. */
    suspend fun openUniStream(): QuicStream =
        lock.withLock {
            val id = StreamId.build(StreamId.Kind.CLIENT_UNI, nextLocalUniIndex++)
            val stream = QuicStream(id, QuicStream.Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE)
            stream.sendCredit = peerTransportParameters?.initialMaxStreamDataUni ?: config.initialMaxStreamDataUni
            stream.receiveLimit = 0L // can't receive
            streams[id] = stream
            stream
        }

    suspend fun pollIncomingPeerStream(): QuicStream? = lock.withLock { newPeerStreams.removeFirstOrNull() }

    suspend fun streamById(id: Long): QuicStream? = lock.withLock { streams[id] }

    suspend fun queueDatagram(payload: ByteArray) = lock.withLock { pendingDatagrams.addLast(payload) }

    suspend fun pollIncomingDatagram(): ByteArray? = lock.withLock { incomingDatagrams.removeFirstOrNull() }

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
    }

    /** Called by the parser on inbound CONNECTION_CLOSE or by the driver on read-loop death. */
    internal fun markClosedExternally(reason: String) {
        if (status != Status.CLOSED) status = Status.CLOSED
        if (!handshakeComplete) {
            signalHandshakeFailed(QuicConnectionClosedException("connection closed externally: $reason"))
        }
    }

    /**
     * Caller must hold [lock]. Used by [QuicConnectionParser] inside the
     * driver's read loop, which already holds the connection lock.
     */
    internal fun getOrCreatePeerStreamLocked(id: Long): QuicStream {
        streams[id]?.let { return it }
        val direction =
            when (StreamId.kindOf(id)) {
                StreamId.Kind.CLIENT_BIDI, StreamId.Kind.SERVER_BIDI -> QuicStream.Direction.BIDIRECTIONAL
                StreamId.Kind.SERVER_UNI -> QuicStream.Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL
                StreamId.Kind.CLIENT_UNI -> QuicStream.Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE
            }
        val stream = QuicStream(id, direction)
        stream.sendCredit = 0L
        stream.receiveLimit = config.initialMaxStreamDataBidiRemote
        streams[id] = stream
        newPeerStreams.addLast(stream)
        return stream
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

    /** Caller must hold [lock]. Pending datagram queue for the driver's send loop. */
    internal fun pendingDatagramsLocked(): ArrayDeque<ByteArray> = pendingDatagrams

    /** Caller must hold [lock]. Inbound datagram queue, written by the read loop. */
    internal fun incomingDatagramsLocked(): ArrayDeque<ByteArray> = incomingDatagrams

    /** Caller must hold [lock]. */
    internal fun streamByIdLocked(id: Long): QuicStream? = streams[id]
}

/** Connection was closed (locally or by peer) before reaching CONNECTED. */
class QuicConnectionClosedException(
    message: String,
) : RuntimeException(message)
