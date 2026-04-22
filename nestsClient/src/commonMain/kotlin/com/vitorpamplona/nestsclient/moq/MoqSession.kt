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
package com.vitorpamplona.nestsclient.moq

import com.vitorpamplona.nestsclient.transport.WebTransportBidiStream
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * MoQ-transport draft version constants. Value shape: `0xff00000000 | draft`
 * is how some draft tests label versions; draft-ietf-moq-transport-17 is
 * commonly represented as 0xff000011 but implementations vary. The actual
 * version used on the wire is negotiated in [MoqSession.setup].
 *
 * Listed as `Long` to survive the MSB-set encoding on JVM.
 */
object MoqVersion {
    /** draft-ietf-moq-transport-11 (a popular stable draft). */
    const val DRAFT_11: Long = 0xff00000bL

    /** draft-ietf-moq-transport-17 (newer draft). */
    const val DRAFT_17: Long = 0xff000011L
}

/**
 * Session wrapper over a [WebTransportSession] that speaks MoQ-transport.
 *
 * Lifecycle:
 *   1. [client] / [server] attaches to a transport. No traffic yet.
 *   2. [setup] runs the SETUP handshake synchronously. After it returns
 *      successfully, two background pumps start in [pumpScope]:
 *       * a control-stream pump that buffers chunks, decodes complete frames,
 *         and dispatches each (currently SUBSCRIBE_OK / SUBSCRIBE_ERROR);
 *       * a datagram pump that decodes OBJECT_DATAGRAMs and routes them to
 *         the matching per-track flow.
 *   3. [subscribe] sends a SUBSCRIBE, awaits the OK, returns a
 *      [SubscribeHandle] whose [SubscribeHandle.objects] flow yields every
 *      OBJECT_DATAGRAM tagged with that subscription's track alias.
 *   4. [close] cancels the pumps and closes the transport.
 */
class MoqSession private constructor(
    private val transport: WebTransportSession,
    /** The single bidi stream every MoQ exchange uses for control. */
    private val controlStream: WebTransportBidiStream,
    private val role: Role,
    private val pumpScope: CoroutineScope,
) {
    enum class Role { Client, Server }

    /** Server's selected version; null until [setup] returns. */
    var selectedVersion: Long? = null
        private set

    /** Parameters the server sent back in SERVER_SETUP. Empty until [setup] returns. */
    var serverParameters: List<SetupParameter> = emptyList()
        private set

    private val stateMutex = Mutex()
    private val writeMutex = Mutex()
    private var nextSubscribeId = 0L

    /** subscribe_id → CompletableDeferred awaiting SUBSCRIBE_OK / SUBSCRIBE_ERROR. */
    private val pendingSubscribes = HashMap<Long, CompletableDeferred<SubscribeOk>>()

    /** track_alias → bounded channel that fans matching OBJECT_DATAGRAMs to the subscriber.
     *
     *  We use a [Channel] with [BufferOverflow.DROP_OLDEST] (rather than a
     *  [MutableSharedFlow]) because objects must be buffered between
     *  [subscribe] returning and the caller attaching to [SubscribeHandle.objects],
     *  even when no collector exists yet. SharedFlow with `replay=0` drops
     *  pre-subscription emissions, which races real-time audio delivery.
     */
    private val sinks = HashMap<Long, Channel<MoqObject>>()

    /** subscribe_id → track_alias, so [unsubscribe] can clean up the matching sink. */
    private val aliasBySubscribeId = HashMap<Long, Long>()

    private var controlPumpJob: Job? = null
    private var datagramPumpJob: Job? = null
    private var closed = false

    /**
     * Run the SETUP handshake.
     *
     * Client side: writes CLIENT_SETUP with [supportedVersions] + [clientParameters],
     *   then reads exactly one SERVER_SETUP, stores the result, and starts the
     *   background pumps.
     * Server side: reads exactly one CLIENT_SETUP, selects the first mutually
     *   supported version from [supportedVersions], writes SERVER_SETUP, and
     *   starts the background pumps.
     *
     * @throws MoqProtocolException if the peer sent an unexpected message, or
     *   if no version overlap exists (server side).
     */
    suspend fun setup(
        supportedVersions: List<Long>,
        clientParameters: List<SetupParameter> = emptyList(),
        handshakeTimeoutMs: Long = 10_000,
    ) {
        withTimeout(handshakeTimeoutMs) {
            when (role) {
                Role.Client -> runClientSetup(supportedVersions, clientParameters)
                Role.Server -> runServerSetup(supportedVersions, clientParameters)
            }
        }
        startPumps()
    }

    private suspend fun runClientSetup(
        supportedVersions: List<Long>,
        clientParameters: List<SetupParameter>,
    ) {
        controlStream.write(MoqCodec.encode(ClientSetup(supportedVersions, clientParameters)))
        val reply = readOneSetupMessage()
        val server =
            reply as? ServerSetup
                ?: throw MoqProtocolException("expected SERVER_SETUP, got ${reply.type.name}")
        if (server.selectedVersion !in supportedVersions) {
            throw MoqProtocolException(
                "server picked version 0x${server.selectedVersion.toString(16)} which we did not offer",
            )
        }
        selectedVersion = server.selectedVersion
        serverParameters = server.parameters
    }

    private suspend fun runServerSetup(
        acceptedVersions: List<Long>,
        serverParameters: List<SetupParameter>,
    ) {
        val incoming = readOneSetupMessage()
        val client =
            incoming as? ClientSetup
                ?: throw MoqProtocolException("expected CLIENT_SETUP, got ${incoming.type.name}")
        val overlap =
            client.supportedVersions.firstOrNull { it in acceptedVersions }
                ?: throw MoqProtocolException(
                    "no mutually-supported MoQ version (client offered ${client.supportedVersions})",
                )
        controlStream.write(MoqCodec.encode(ServerSetup(overlap, serverParameters)))
        this.selectedVersion = overlap
        this.serverParameters = serverParameters
    }

    /**
     * Bootstrap helper: read exactly one SETUP-phase message from the control
     * stream. Used only during the synchronous handshake (before the pump
     * starts). SETUP frames always fit in a single transport write, so we can
     * safely take the first chunk and decode.
     */
    private suspend fun readOneSetupMessage(): MoqMessage {
        val chunk = controlStream.incoming().first()
        val decoded =
            MoqCodec.decode(chunk)
                ?: throw MoqProtocolException(
                    "control-stream chunk did not contain a complete MoQ frame (size=${chunk.size})",
                )
        return decoded.message
    }

    // ---------------------------------------------------------------- Subscribe

    /**
     * Send SUBSCRIBE and suspend until SUBSCRIBE_OK arrives. Returns a
     * [SubscribeHandle] whose [SubscribeHandle.objects] flow emits every
     * OBJECT_DATAGRAM tagged with this subscription's track alias.
     *
     * @throws MoqProtocolException if the publisher rejects the subscribe with
     *   SUBSCRIBE_ERROR, or if the session is closed mid-flight.
     */
    suspend fun subscribe(
        namespace: TrackNamespace,
        trackName: ByteArray,
        priority: Int = 0x80,
        filter: SubscribeFilter = SubscribeFilter.LatestGroup,
        objectBufferCapacity: Int = DEFAULT_OBJECT_BUFFER,
    ): SubscribeHandle {
        check(!closed) { "session is closed" }

        val subscribeId: Long
        val trackAlias: Long
        val deferred = CompletableDeferred<SubscribeOk>()
        val sink =
            Channel<MoqObject>(
                capacity = objectBufferCapacity,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        stateMutex.withLock {
            check(!closed) { "session is closed" }
            subscribeId = nextSubscribeId++
            trackAlias = subscribeId // 1:1 alias = id keeps things simple
            pendingSubscribes[subscribeId] = deferred
            sinks[trackAlias] = sink
            aliasBySubscribeId[subscribeId] = trackAlias
        }

        val frame =
            MoqCodec.encode(
                Subscribe(
                    subscribeId = subscribeId,
                    trackAlias = trackAlias,
                    namespace = namespace,
                    trackName = trackName,
                    subscriberPriority = priority,
                    filter = filter,
                ),
            )
        try {
            writeMutex.withLock { controlStream.write(frame) }
            val ok = deferred.await()
            return SubscribeHandle(
                subscribeId = subscribeId,
                trackAlias = trackAlias,
                ok = ok,
                objects = sink.receiveAsFlow(),
                unsubscribeAction = { unsubscribe(subscribeId) },
            )
        } catch (t: Throwable) {
            // Roll back state on any failure (write error, deferred cancellation,
            // SUBSCRIBE_ERROR, session close).
            stateMutex.withLock {
                pendingSubscribes.remove(subscribeId)
                sinks.remove(trackAlias)?.close()
                aliasBySubscribeId.remove(subscribeId)
            }
            throw t
        }
    }

    /**
     * Send UNSUBSCRIBE and tear down all local state for [subscribeId]. Safe
     * to call multiple times — second call is a no-op.
     */
    suspend fun unsubscribe(subscribeId: Long) {
        val alias =
            stateMutex.withLock {
                pendingSubscribes.remove(subscribeId)?.cancel()
                aliasBySubscribeId.remove(subscribeId)?.also { sinks.remove(it)?.close() }
            } ?: return

        if (closed) return
        runCatching {
            writeMutex.withLock { controlStream.write(MoqCodec.encode(Unsubscribe(subscribeId))) }
        }
        // Suppress write errors on unsubscribe — the session is being torn down
        // and the alias is already gone from local state. `alias` is intentionally
        // referenced so the compiler keeps the cleanup expression's side effects.
        @Suppress("UNUSED_VARIABLE")
        val ignored = alias
    }

    // ---------------------------------------------------------------- Pumps

    private fun startPumps() {
        controlPumpJob =
            pumpScope.launch {
                runControlPump()
            }
        datagramPumpJob =
            pumpScope.launch {
                runDatagramPump()
            }
    }

    private suspend fun runControlPump() {
        var buffer = ByteArray(0)
        controlStream.incoming().collect { chunk ->
            buffer =
                if (buffer.isEmpty()) {
                    chunk
                } else {
                    val merged = ByteArray(buffer.size + chunk.size)
                    buffer.copyInto(merged, 0)
                    chunk.copyInto(merged, buffer.size)
                    merged
                }
            while (true) {
                val decoded =
                    try {
                        MoqCodec.decode(buffer) ?: break
                    } catch (e: MoqCodecException) {
                        // Drop the corrupted buffer; keep the pump alive so the
                        // next valid frame can recover the session.
                        buffer = ByteArray(0)
                        break
                    }
                buffer = buffer.copyOfRange(decoded.bytesConsumed, buffer.size)
                dispatchControlMessage(decoded.message)
            }
        }
    }

    private suspend fun dispatchControlMessage(msg: MoqMessage) {
        when (msg) {
            is SubscribeOk -> {
                val deferred =
                    stateMutex.withLock { pendingSubscribes.remove(msg.subscribeId) }
                deferred?.complete(msg)
            }

            is SubscribeError -> {
                val deferred =
                    stateMutex.withLock {
                        val d = pendingSubscribes.remove(msg.subscribeId)
                        aliasBySubscribeId.remove(msg.subscribeId)?.also { sinks.remove(it)?.close() }
                        d
                    }
                deferred?.completeExceptionally(
                    MoqProtocolException(
                        "SUBSCRIBE rejected: code=${msg.errorCode} reason=${msg.reasonPhrase}",
                    ),
                )
            }

            else -> {
                // Other control messages (SETUP echoes, future ANNOUNCE/etc.)
                // are silently dropped at this layer; Phase 3c-3 only needs the
                // subscribe lifecycle.
            }
        }
    }

    private suspend fun runDatagramPump() {
        transport.incomingDatagrams().collect { datagram ->
            val obj =
                try {
                    MoqObjectDatagram.decode(datagram)
                } catch (e: MoqCodecException) {
                    // Drop malformed datagrams — UDP-style transport, so partial
                    // / corrupted packets are normal.
                    return@collect
                }
            val sink = stateMutex.withLock { sinks[obj.trackAlias] }
            // trySend on a DROP_OLDEST channel always succeeds (drops oldest on
            // overflow). Returning value is intentionally ignored.
            sink?.trySend(obj)
        }
    }

    /** Cancel the pumps and close the underlying transport. Idempotent. */
    suspend fun close(
        code: Int = 0,
        reason: String = "",
    ) {
        stateMutex.withLock {
            if (closed) return
            closed = true
            // Fail any in-flight subscribe waiters cleanly.
            for ((_, deferred) in pendingSubscribes) {
                deferred.cancel()
            }
            pendingSubscribes.clear()
            for ((_, ch) in sinks) ch.close()
            sinks.clear()
            aliasBySubscribeId.clear()
        }
        controlPumpJob?.cancel()
        datagramPumpJob?.cancel()
        runCatching { writeMutex.withLock { controlStream.finish() } }
        runCatching { transport.close(code, reason) }
    }

    companion object {
        /**
         * Default per-subscription buffer for unread objects. 64 frames at
         * Opus 20 ms = ~1.3 s of audio backlog before DROP_OLDEST kicks in,
         * which matches a typical real-time listener's tolerance.
         */
        const val DEFAULT_OBJECT_BUFFER: Int = 64

        /**
         * Attach to a [WebTransportSession] in the client role.
         *
         * @param pumpScope where the post-handshake pumps live. Tests typically
         *   pass `backgroundScope` from `runTest`; production code passes the
         *   owning ViewModel scope so pumps are cancelled on screen exit.
         */
        suspend fun client(
            transport: WebTransportSession,
            pumpScope: CoroutineScope,
        ): MoqSession {
            val control = transport.openBidiStream()
            return MoqSession(transport, control, Role.Client, pumpScope)
        }

        /**
         * Attach to a [WebTransportSession] in the server role over an
         * already-accepted control stream. Used in tests — a real server
         * accepts the client's first bidi.
         */
        fun server(
            transport: WebTransportSession,
            control: WebTransportBidiStream,
            pumpScope: CoroutineScope,
        ): MoqSession = MoqSession(transport, control, Role.Server, pumpScope)
    }
}

/**
 * Handle to an active subscription. [objects] emits every OBJECT_DATAGRAM the
 * publisher delivers for this subscription's track. Call [unsubscribe] when
 * done — equivalent to [MoqSession.unsubscribe] with this handle's id.
 */
class SubscribeHandle internal constructor(
    val subscribeId: Long,
    val trackAlias: Long,
    val ok: SubscribeOk,
    val objects: Flow<MoqObject>,
    private val unsubscribeAction: suspend () -> Unit,
) {
    suspend fun unsubscribe() = unsubscribeAction()
}

/** Thrown when the peer violates the MoQ-transport state machine. */
class MoqProtocolException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
