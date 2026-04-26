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
package com.vitorpamplona.nestsclient.moq.lite

import com.vitorpamplona.nestsclient.moq.MoqCodecException
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quic.Varint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Listener-side moq-lite (Lite-03) session. Wraps a connected
 * [WebTransportSession] and exposes:
 *
 *   - [announce] — open an Announce bidi with a prefix, observe live
 *     `Active` / `Ended` updates from the relay.
 *   - [subscribe] — open a Subscribe bidi for a `(broadcast, track)`
 *     pair, await SubscribeOk, return a [MoqLiteSubscribeHandle] whose
 *     [MoqLiteSubscribeHandle.frames] yields each frame the publisher
 *     pushes.
 *
 * Speaker-side is **not yet implemented** — the publisher direction
 * needs server-initiated bidi acceptance (relay → us) which the
 * current `WebTransportSession` interface does not surface. Tracked in
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md` phase-5c-speaker.
 *
 * Wire-protocol scope: Lite-03 — no SETUP, no datagrams, one fresh
 * client-initiated bidi per request, group data on uni streams. See
 * the gap doc for the byte-level layout.
 */
class MoqLiteSession internal constructor(
    private val transport: WebTransportSession,
    private val scope: CoroutineScope,
) {
    private val state = Mutex()
    private val subscriptionsBySubscribeId: MutableMap<Long, ListenerSubscription> = HashMap()
    private var nextSubscribeId: Long = 0L
    private var groupPump: Job? = null

    @Volatile private var closed: Boolean = false

    val isClosed: Boolean get() = closed

    /**
     * Open an Announce bidi against the relay. Sends
     * `ControlType=Announce` + `AnnouncePlease(prefix)`, then surfaces
     * a flow of [MoqLiteAnnounce] updates the relay streams back.
     *
     * The returned [MoqLiteAnnouncesHandle] keeps the bidi open until
     * [MoqLiteAnnouncesHandle.close] is called. FINing the bidi is
     * how moq-lite signals "no longer interested" — there is no
     * ANNOUNCE_CANCEL message.
     */
    suspend fun announce(prefix: String): MoqLiteAnnouncesHandle {
        ensureOpen()
        val bidi = transport.openBidiStream()
        bidi.write(Varint.encode(MoqLiteControlType.Announce.code))
        bidi.write(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease(prefix)))

        val updates = MutableSharedFlow<MoqLiteAnnounce>(replay = 0, extraBufferCapacity = 64)
        val pump =
            scope.launch {
                val buffer = MoqLiteFrameBuffer()
                try {
                    bidi.incoming().collect { chunk ->
                        buffer.push(chunk)
                        while (true) {
                            val payload = buffer.readSizePrefixed() ?: break
                            updates.emit(MoqLiteCodec.decodeAnnounce(payload))
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Flow terminated (peer FIN or transport close).
                    // The Announce stream's emit-side just stops; consumers
                    // see an end-of-flow.
                }
            }
        return MoqLiteAnnouncesHandle(
            updates = updates,
            close = {
                runCatching { bidi.finish() }
                pump.cancelAndJoin()
            },
        )
    }

    /**
     * Open a Subscribe bidi for `(broadcast, track)`. Sends
     * `ControlType=Subscribe` + the [MoqLiteSubscribe] body, awaits
     * the [MoqLiteCodec.SubscribeResponse] reply, and on Ok returns a
     * [MoqLiteSubscribeHandle] whose `frames` flow yields each frame
     * the publisher pushes through the relay (one [MoqLiteFrame] per
     * payload, bundled into [MoqLiteFrame] structs that carry the
     * group sequence).
     *
     * Throws [MoqLiteSubscribeException] if the publisher / relay
     * replies with Drop, or if the response stream tears down before
     * any reply arrives.
     */
    suspend fun subscribe(
        broadcast: String,
        track: String,
        priority: Int = DEFAULT_PRIORITY,
        ordered: Boolean = true,
        maxLatencyMillis: Long = 0L,
        startGroup: Long? = null,
        endGroup: Long? = null,
    ): MoqLiteSubscribeHandle {
        ensureOpen()
        val id =
            state.withLock {
                check(!closed) { "session is closed" }
                val next = nextSubscribeId++
                next
            }
        val request =
            MoqLiteSubscribe(
                id = id,
                broadcast = broadcast,
                track = track,
                priority = priority,
                ordered = ordered,
                maxLatencyMillis = maxLatencyMillis,
                startGroup = startGroup,
                endGroup = endGroup,
            )
        val bidi = transport.openBidiStream()
        bidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
        bidi.write(MoqLiteCodec.encodeSubscribe(request))

        // moq-lite's subscribe-response is a single size-prefixed
        // message on the response side of the bidi. Read incoming
        // chunks into a buffer until the buffer holds a full payload,
        // then stop. We don't need a separate collector pump because
        // post-Ok the bidi is idle (group data flows on its own uni
        // streams).
        val responseBuffer = MoqLiteFrameBuffer()
        val responsePayload = readSubscribeResponseFromBidi(bidi.incoming(), responseBuffer, id)
        when (val resp = MoqLiteCodec.decodeSubscribeResponse(responsePayload)) {
            is MoqLiteCodec.SubscribeResponse.Dropped -> {
                throw MoqLiteSubscribeException(
                    "publisher rejected subscribe id=$id: errorCode=${resp.drop.errorCode} " +
                        "reason='${resp.drop.reasonPhrase}'",
                )
            }

            is MoqLiteCodec.SubscribeResponse.Ok -> {
                val frames = Channel<MoqLiteFrame>(capacity = DEFAULT_FRAME_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                val sub =
                    ListenerSubscription(
                        id = id,
                        request = request,
                        ok = resp.ok,
                        bidi = bidi,
                        frames = frames,
                    )
                state.withLock {
                    subscriptionsBySubscribeId[id] = sub
                    if (groupPump == null) groupPump = scope.launch { pumpUniStreams() }
                }
                return MoqLiteSubscribeHandle(
                    id = id,
                    ok = resp.ok,
                    frames = frames.consumeAsFlow(),
                    unsubscribeAction = { unsubscribe(id) },
                )
            }
        }
    }

    /**
     * Drain inbound uni streams and route each one's group frames to
     * the matching subscription. The relay opens a fresh uni stream
     * per (subscribe id, group sequence); the first byte is
     * [MoqLiteDataType] (0 = Group), followed by a size-prefixed
     * [MoqLiteGroupHeader], followed by `varint(size) + payload`
     * frames until QUIC FIN.
     *
     * One pump per session — started lazily on the first subscribe.
     */
    private suspend fun pumpUniStreams() {
        try {
            transport.incomingUniStreams().collect { stream ->
                scope.launch { drainOneGroup(stream) }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Transport closed — subscriptions will surface end-of-flow
            // via their own bidi pumps as well.
        }
    }

    private suspend fun drainOneGroup(stream: com.vitorpamplona.nestsclient.transport.WebTransportReadStream) {
        val buffer = MoqLiteFrameBuffer()
        var typeRead = false
        var subscribeId: Long = -1L
        var groupSequence: Long = -1L
        var headerRead = false
        try {
            stream.incoming().collect { chunk ->
                buffer.push(chunk)
                if (!typeRead) {
                    val type = buffer.readVarint() ?: return@collect
                    if (type != MoqLiteDataType.Group.code) {
                        throw MoqCodecException("unknown moq-lite uni-stream type code: $type")
                    }
                    typeRead = true
                }
                if (!headerRead) {
                    val payload = buffer.readSizePrefixed() ?: return@collect
                    val hdr = MoqLiteCodec.decodeGroupHeader(payload)
                    subscribeId = hdr.subscribeId
                    groupSequence = hdr.sequence
                    headerRead = true
                }
                while (true) {
                    val frame = buffer.readSizePrefixed() ?: break
                    val sub = state.withLock { subscriptionsBySubscribeId[subscribeId] }
                    if (sub != null) {
                        sub.frames.trySend(
                            MoqLiteFrame(
                                groupSequence = groupSequence,
                                payload = frame,
                            ),
                        )
                    }
                    // If the subscription has been closed already we
                    // silently drop the frame — the publisher hasn't
                    // observed the unsubscribe yet (its uni streams
                    // are independent of our bidi FIN).
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Stream errored / FIN'd. Nothing to do — the next group
            // arrives on a fresh uni stream.
        }
    }

    private suspend fun unsubscribe(id: Long) {
        val sub =
            state.withLock { subscriptionsBySubscribeId.remove(id) } ?: return
        runCatching { sub.bidi.finish() }
        sub.frames.close()
    }

    suspend fun close() {
        if (closed) return
        closed = true
        val toClose: List<ListenerSubscription>
        state.withLock {
            toClose = subscriptionsBySubscribeId.values.toList()
            subscriptionsBySubscribeId.clear()
        }
        for (sub in toClose) {
            runCatching { sub.bidi.finish() }
            sub.frames.close()
        }
        groupPump?.cancelAndJoin()
        runCatching { transport.close() }
    }

    private fun ensureOpen() {
        check(!closed) { "MoqLiteSession is closed" }
    }

    /**
     * Read the single size-prefixed subscribe response off a fresh
     * subscribe bidi. moq-lite's subscribe-response is one wire write,
     * so the first chunk we receive contains the entire payload (matches
     * what the IETF [com.vitorpamplona.nestsclient.moq.MoqSession] does
     * during its SETUP read).
     *
     * Throws [MoqLiteSubscribeException] if the bidi closes before any
     * chunk arrives.
     */
    private suspend fun readSubscribeResponseFromBidi(
        incoming: kotlinx.coroutines.flow.Flow<ByteArray>,
        buffer: MoqLiteFrameBuffer,
        id: Long,
    ): ByteArray {
        val chunk =
            incoming.firstOrNull()
                ?: throw MoqLiteSubscribeException("subscribe stream FIN before reply for id=$id")
        buffer.push(chunk)
        return buffer.readSizePrefixed()
            ?: throw MoqLiteSubscribeException(
                "first chunk on subscribe response did not carry a complete size-prefixed payload " +
                    "(id=$id, chunkSize=${chunk.size})",
            )
    }

    private class ListenerSubscription(
        val id: Long,
        val request: MoqLiteSubscribe,
        val ok: MoqLiteSubscribeOk,
        val bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        val frames: Channel<MoqLiteFrame>,
    )

    companion object {
        /** moq-lite priority byte midpoint — neutral default. */
        const val DEFAULT_PRIORITY: Int = 0x80

        /**
         * Per-subscription channel buffer for inbound frames. 128 audio
         * frames at Opus 20 ms ≈ 2.5 s of backlog before DROP_OLDEST,
         * matching a real-time listener's tolerance.
         */
        const val DEFAULT_FRAME_BUFFER: Int = 128

        /**
         * Attach to a [WebTransportSession] in the client role. Lite-03
         * has no SETUP — the WT handshake itself is the handshake — so
         * this returns a ready-to-use session immediately.
         *
         * @param pumpScope where the inbound-uni-stream + per-bidi pumps
         *   live. Production code passes the owning ViewModel scope.
         */
        fun client(
            transport: WebTransportSession,
            pumpScope: CoroutineScope,
        ): MoqLiteSession = MoqLiteSession(transport, pumpScope)
    }
}

/**
 * One frame received from a subscription. moq-lite's wire format
 * carries no per-frame envelope beyond the size; [groupSequence] is
 * pulled from the group header so consumers can detect group rollover
 * (e.g. for keyframe boundaries).
 */
data class MoqLiteFrame(
    val groupSequence: Long,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MoqLiteFrame) return false
        return groupSequence == other.groupSequence && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * groupSequence.hashCode() + payload.contentHashCode()
}

/**
 * Active subscription handle returned by [MoqLiteSession.subscribe].
 * [frames] emits every frame the publisher pushes; [unsubscribe]
 * FINs the bidi to signal "no longer interested" (moq-lite has no
 * UNSUBSCRIBE message — FIN is the protocol).
 */
class MoqLiteSubscribeHandle internal constructor(
    val id: Long,
    val ok: MoqLiteSubscribeOk,
    val frames: Flow<MoqLiteFrame>,
    private val unsubscribeAction: suspend () -> Unit,
) {
    suspend fun unsubscribe() = unsubscribeAction()
}

/**
 * Active announce-discovery handle returned by [MoqLiteSession.announce].
 * [updates] emits every [MoqLiteAnnounce] update the relay streams
 * back; [close] FINs the bidi to stop receiving updates.
 */
class MoqLiteAnnouncesHandle internal constructor(
    val updates: Flow<MoqLiteAnnounce>,
    private val close: suspend () -> Unit,
) {
    suspend fun close() = close.invoke()
}

/** Thrown when subscribe is rejected (Drop) or the response stream dies. */
class MoqLiteSubscribeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
