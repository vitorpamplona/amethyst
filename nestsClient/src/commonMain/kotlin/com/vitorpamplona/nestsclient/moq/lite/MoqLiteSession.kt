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
import com.vitorpamplona.nestsclient.moq.MoqWriter
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
 * moq-lite (Lite-03) session for both listener and publisher roles.
 * Wraps a connected [WebTransportSession] and exposes:
 *
 * Listener side:
 *   - [announce] — open an Announce bidi with a prefix, observe live
 *     `Active` / `Ended` updates from the relay.
 *   - [subscribe] — open a Subscribe bidi for a `(broadcast, track)`
 *     pair, await SubscribeOk, return a [MoqLiteSubscribeHandle] whose
 *     [MoqLiteSubscribeHandle.frames] yields each frame the publisher
 *     pushes.
 *
 * Publisher side:
 *   - [publish] — claim a broadcast suffix; the session then services
 *     every relay-opened Announce / Subscribe bidi automatically and
 *     returns a [MoqLitePublisherHandle] the application can push
 *     Opus frames into. Group rollover is the application's call —
 *     [MoqLitePublisherHandle.send] auto-starts a group on first call,
 *     [MoqLitePublisherHandle.endGroup] FINs and starts a new one on
 *     the next send.
 *
 * Wire-protocol scope: Lite-03 — no SETUP, no datagrams, one fresh
 * bidi per request (subscriber → publisher OR publisher accepts from
 * relay), group data on uni streams. See
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md` for the byte-level
 * layout.
 */
class MoqLiteSession internal constructor(
    private val transport: WebTransportSession,
    private val scope: CoroutineScope,
) {
    private val state = Mutex()
    private val subscriptionsBySubscribeId: MutableMap<Long, ListenerSubscription> = HashMap()
    private var nextSubscribeId: Long = 0L
    private var groupPump: Job? = null

    /** Lazily-launched relay→us inbound bidi pump; only runs while a publisher is active. */
    private var bidiPump: Job? = null

    /** Single active publisher per session (moq-lite doesn't model multi-broadcast publishers). */
    private var activePublisher: PublisherStateImpl? = null

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

    // ====================================================================
    // Publisher side
    // ====================================================================

    /**
     * Begin publishing under [broadcastSuffix]. Returns a
     * [MoqLitePublisherHandle] that:
     *   - announces `Active` to every relay-opened Announce bidi whose
     *     `AnnouncePlease.prefix` matches our suffix
     *   - opens a fresh group uni stream when a relay-opened Subscribe
     *     bidi for our broadcast arrives, then forwards [MoqLitePublisherHandle.send]
     *     bytes as `varint(size) + payload` frames until [MoqLitePublisherHandle.endGroup]
     *     or [MoqLitePublisherHandle.close]
     *
     * Wire-flow per `kixelated/moq-rs/rs/moq-lite/src/lite/publisher.rs:40+`:
     *   - the relay opens Announce + Subscribe bidi streams *to* us
     *     (`Stream::accept(session)`), so the session's
     *     [WebTransportSession.incomingBidiStreams] pump is the entry
     *     point
     *   - we open uni streams ourselves to push group data
     *     (`session.open_uni()`)
     *
     * Only one [publish] is supported per session for now. Calling
     * [publish] twice on the same session is rejected with [IllegalStateException].
     */
    suspend fun publish(broadcastSuffix: String): MoqLitePublisherHandle {
        ensureOpen()
        val normalised = MoqLitePath.normalize(broadcastSuffix)
        val publisher: PublisherStateImpl
        state.withLock {
            check(!closed) { "session is closed" }
            check(activePublisher == null) {
                "MoqLiteSession.publish called twice — only one broadcast per session is supported"
            }
            publisher = PublisherStateImpl(suffix = normalised)
            activePublisher = publisher
            // Lazy launch — the inbound-bidi pump needs to keep running
            // for the lifetime of any active publisher.
            if (bidiPump == null) bidiPump = scope.launch { pumpInboundBidis() }
        }
        return publisher
    }

    /**
     * Drain inbound bidi streams (relay → us) and dispatch each by
     * its leading [MoqLiteControlType] varint. The relay opens
     * Announce / Subscribe bidis on its own initiative; we read the
     * control type, the request body, and reply on the same bidi.
     *
     * One pump per session — started lazily on the first [publish].
     */
    private suspend fun pumpInboundBidis() {
        try {
            transport.incomingBidiStreams().collect { bidi ->
                scope.launch { handleInboundBidi(bidi) }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Transport closed.
        }
    }

    private suspend fun handleInboundBidi(bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream) {
        val buffer = MoqLiteFrameBuffer()
        val publisher = state.withLock { activePublisher } ?: return
        try {
            // Read the leading ControlType varint from the first chunk.
            val first =
                bidi.incoming().firstOrNull() ?: return
            buffer.push(first)
            val controlCode = buffer.readVarint() ?: return
            val controlType = MoqLiteControlType.fromCode(controlCode) ?: return
            when (controlType) {
                MoqLiteControlType.Announce -> {
                    handleAnnounceRequest(bidi, buffer, publisher)
                }

                MoqLiteControlType.Subscribe -> {
                    handleSubscribeRequest(bidi, buffer, publisher)
                }

                else -> {
                    // Lite-03 treats Session/Fetch/Probe as separate flows;
                    // we don't implement them here. Drop the bidi.
                    runCatching { bidi.finish() }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            runCatching { bidi.finish() }
        }
    }

    private suspend fun handleAnnounceRequest(
        bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        seedBuffer: MoqLiteFrameBuffer,
        publisher: PublisherStateImpl,
    ) {
        val pleasePayload = readSizePrefixedFromBidiInto(bidi.incoming(), seedBuffer)
        val please = MoqLiteCodec.decodeAnnouncePlease(pleasePayload)
        // The relay sets the prefix to the namespace it expects us to
        // publish under (typically `claims.root`). Our broadcast path
        // (after stripping the prefix) is `publisher.suffix`. moq-lite
        // requires the suffix on the wire to be the *remaining* part
        // after `please.prefix` — so strip it.
        val emittedSuffix = MoqLitePath.stripPrefix(please.prefix, publisher.suffix) ?: publisher.suffix
        bidi.write(
            MoqLiteCodec.encodeAnnounce(
                MoqLiteAnnounce(
                    status = MoqLiteAnnounceStatus.Active,
                    suffix = emittedSuffix,
                    hops = 0L,
                ),
            ),
        )
        // Hold the bidi open until the publisher closes; if/when the
        // application stops broadcasting, send `Ended`.
        publisher.registerAnnounceBidi(bidi, emittedSuffix)
    }

    private suspend fun handleSubscribeRequest(
        bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        seedBuffer: MoqLiteFrameBuffer,
        publisher: PublisherStateImpl,
    ) {
        val subPayload = readSizePrefixedFromBidiInto(bidi.incoming(), seedBuffer)
        val sub = MoqLiteCodec.decodeSubscribe(subPayload)
        // Reply Ok right away — moq-lite is permissive on the publisher
        // side; the relay decides whether the subscriber is allowed to
        // see this broadcast.
        bidi.write(
            MoqLiteCodec.encodeSubscribeOk(
                MoqLiteSubscribeOk(
                    priority = sub.priority,
                    ordered = sub.ordered,
                    maxLatencyMillis = sub.maxLatencyMillis,
                    startGroup = null,
                    endGroup = null,
                ),
            ),
        )
        publisher.registerInboundSubscription(sub)
    }

    /**
     * Open a new uni stream for one group's frames and push the
     * `DataType=Group` byte + size-prefixed [MoqLiteGroupHeader].
     * Returns a write stream the caller frames each Opus packet onto.
     */
    internal suspend fun openGroupStream(
        subscribeId: Long,
        sequence: Long,
    ): com.vitorpamplona.nestsclient.transport.WebTransportWriteStream {
        val uni = transport.openUniStream()
        uni.write(Varint.encode(MoqLiteDataType.Group.code))
        uni.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId, sequence)))
        return uni
    }

    /**
     * Read a size-prefixed payload from a bidi, seeded with whatever's
     * already in [buffer] (the ControlType byte may have arrived with
     * extra bytes). Used internally by the publisher inbound dispatch.
     */
    private suspend fun readSizePrefixedFromBidiInto(
        incoming: kotlinx.coroutines.flow.Flow<ByteArray>,
        buffer: MoqLiteFrameBuffer,
    ): ByteArray {
        // Try the buffer first — first chunk often contains the whole
        // body since moq-lite messages are small and arrive as single
        // QUIC sends.
        buffer.readSizePrefixed()?.let { return it }
        var done: ByteArray? = null
        try {
            incoming.collect { chunk ->
                buffer.push(chunk)
                buffer.readSizePrefixed()?.let {
                    done = it
                    throw EarlyExit
                }
            }
        } catch (_: EarlyExit) {
            // expected
        } catch (ce: CancellationException) {
            throw ce
        }
        return done
            ?: throw MoqCodecException("incoming bidi closed before a complete size-prefixed body arrived")
    }

    private object EarlyExit : RuntimeException() {
        private fun readResolve(): Any = EarlyExit

        override fun fillInStackTrace(): Throwable = this
    }

    suspend fun close() {
        if (closed) return
        closed = true
        val toClose: List<ListenerSubscription>
        val publisherToClose: PublisherStateImpl?
        state.withLock {
            toClose = subscriptionsBySubscribeId.values.toList()
            subscriptionsBySubscribeId.clear()
            publisherToClose = activePublisher
            activePublisher = null
        }
        for (sub in toClose) {
            runCatching { sub.bidi.finish() }
            sub.frames.close()
        }
        runCatching { publisherToClose?.close() }
        groupPump?.cancelAndJoin()
        bidiPump?.cancelAndJoin()
        runCatching { transport.close() }
    }

    private fun ensureOpen() {
        check(!closed) { "MoqLiteSession is closed" }
    }

    /**
     * Read a moq-lite-03 SubscribeResponse off the bidi response side.
     * The wire format is `[type_varint][body_size_varint][body_bytes]`
     * — type lives OUTSIDE the size prefix (see
     * `rs/moq-lite/src/lite/subscribe.rs::SubscribeResponse::encode`).
     *
     * Walks chunks into [buffer] until both the type discriminator and
     * the size-prefixed body have arrived, then returns the contiguous
     * `type+size+body` byte slab so [MoqLiteCodec.decodeSubscribeResponse]
     * can parse it self-contained.
     *
     * Throws [MoqLiteSubscribeException] if the bidi closes before a
     * full message arrives — that's the relay rejecting the subscribe
     * with FIN instead of a SubscribeDrop reply.
     */
    private suspend fun readSubscribeResponseFromBidi(
        incoming: kotlinx.coroutines.flow.Flow<ByteArray>,
        buffer: MoqLiteFrameBuffer,
        id: Long,
    ): ByteArray {
        // Snapshot the buffer's pos before each varint so we can roll
        // back if not enough bytes have arrived yet — without this, a
        // partial varint advances `pos` and the next chunk's bytes
        // can't reconstitute it.
        var typeCode: Long? = null
        var body: ByteArray? = null
        try {
            // Some bytes may already be buffered (extra arrived with a
            // prior message); try first without waiting for new chunks.
            typeCode = buffer.readVarint()
            if (typeCode != null) body = buffer.readSizePrefixed()
            if (body != null) throw EarlyExit
            incoming.collect { chunk ->
                buffer.push(chunk)
                if (typeCode == null) typeCode = buffer.readVarint()
                if (typeCode != null && body == null) body = buffer.readSizePrefixed()
                if (body != null) throw EarlyExit
            }
        } catch (_: EarlyExit) {
            // expected
        } catch (ce: CancellationException) {
            throw ce
        }
        if (typeCode == null) {
            throw MoqLiteSubscribeException("subscribe stream FIN before reply for id=$id")
        }
        if (body == null) {
            throw MoqLiteSubscribeException(
                "subscribe stream FIN mid-body for id=$id (type=$typeCode)",
            )
        }
        // Re-emit the contiguous `[type][size][body]` slab so
        // [MoqLiteCodec.decodeSubscribeResponse] can parse it
        // self-contained.
        val out = MoqWriter()
        out.writeVarint(typeCode!!)
        out.writeVarint(body!!.size.toLong())
        out.writeBytes(body!!)
        return out.toByteArray()
    }

    private class ListenerSubscription(
        val id: Long,
        val request: MoqLiteSubscribe,
        val ok: MoqLiteSubscribeOk,
        val bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        val frames: Channel<MoqLiteFrame>,
    )

    /**
     * Publisher state. Tracks the announce bidis the relay opened to us
     * + the inbound subscriptions a relay (or peer) opened against our
     * broadcast, and owns the current group's uni stream.
     *
     * `gate` serialises access to per-group state so concurrent
     * `send` / `startGroup` / `endGroup` / `close` can't race.
     */
    private inner class PublisherStateImpl(
        override val suffix: String,
    ) : MoqLitePublisherHandle {
        private val gate = Mutex()
        private val announceBidis = mutableListOf<AnnounceBidiEntry>()
        private val inboundSubs = mutableListOf<MoqLiteSubscribe>()
        private var currentGroup: GroupOutbound? = null
        private var nextSequence: Long = 0L

        @Volatile private var publisherClosed = false

        suspend fun registerAnnounceBidi(
            bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
            emittedSuffix: String,
        ) {
            gate.withLock {
                if (publisherClosed) {
                    runCatching { bidi.finish() }
                    return
                }
                announceBidis += AnnounceBidiEntry(bidi, emittedSuffix)
            }
        }

        suspend fun registerInboundSubscription(sub: MoqLiteSubscribe) {
            gate.withLock {
                if (publisherClosed) return
                inboundSubs += sub
            }
        }

        override suspend fun startGroup() {
            gate.withLock {
                if (publisherClosed) return
                runCatching { currentGroup?.uni?.finish() }
                currentGroup = openNextGroupLocked()
            }
        }

        override suspend fun send(payload: ByteArray): Boolean {
            gate.withLock {
                if (publisherClosed) return false
                if (inboundSubs.isEmpty()) return false
                val group = currentGroup ?: openNextGroupLocked().also { currentGroup = it }
                val framed = Varint.encode(payload.size.toLong()) + payload
                runCatching { group.uni.write(framed) }
            }
            return true
        }

        override suspend fun endGroup() {
            gate.withLock {
                if (publisherClosed) return
                val group = currentGroup ?: return
                currentGroup = null
                runCatching { group.uni.finish() }
            }
        }

        override suspend fun close() {
            val toFinalise: List<AnnounceBidiEntry>
            val groupToFinish: GroupOutbound?
            gate.withLock {
                if (publisherClosed) return
                publisherClosed = true
                toFinalise = announceBidis.toList()
                announceBidis.clear()
                inboundSubs.clear()
                groupToFinish = currentGroup
                currentGroup = null
            }
            for (entry in toFinalise) {
                runCatching {
                    entry.bidi.write(
                        MoqLiteCodec.encodeAnnounce(
                            MoqLiteAnnounce(
                                status = MoqLiteAnnounceStatus.Ended,
                                suffix = entry.emittedSuffix,
                                hops = 0L,
                            ),
                        ),
                    )
                }
                runCatching { entry.bidi.finish() }
            }
            runCatching { groupToFinish?.uni?.finish() }
            // Detach from the session so a subsequent `publish` can run.
            state.withLock {
                if (activePublisher === this) activePublisher = null
            }
        }

        /** Caller holds [gate]. */
        private suspend fun openNextGroupLocked(): GroupOutbound {
            // moq-lite groups are addressed by `subscribeId` on the wire —
            // each inbound subscription gets its own group stream. For
            // simplicity we open one stream per group keyed off the
            // *first* subscription's id; relay-side multi-subscriber
            // fan-out happens above us. Inbound subscription set is
            // expected to be small (1 in nests's listener-per-room
            // model), so this is fine.
            val sub = inboundSubs.first()
            val sequence = nextSequence++
            val uni = openGroupStream(subscribeId = sub.id, sequence = sequence)
            return GroupOutbound(sequence = sequence, uni = uni)
        }
    }

    private data class AnnounceBidiEntry(
        val bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        val emittedSuffix: String,
    )

    private data class GroupOutbound(
        val sequence: Long,
        val uni: com.vitorpamplona.nestsclient.transport.WebTransportWriteStream,
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

/**
 * Active publisher handle returned by [MoqLiteSession.publish].
 *
 * Lifecycle:
 *   1. Call [startGroup] (or [send] which auto-starts a fresh group on
 *      first call) to begin pushing frames for one Opus group.
 *   2. Call [send] for each frame (one Opus packet = one frame).
 *   3. Call [endGroup] to FIN the current group's uni stream and start
 *      a fresh group on the next [send]. Group rollover is the
 *      publisher's call — typically every N seconds or every keyframe.
 *   4. Call [close] when the broadcast ends — sends `Announce(Ended)`
 *      on every active announce bidi and FINs every group stream.
 */
interface MoqLitePublisherHandle {
    /**
     * The broadcast suffix this publisher claimed at [MoqLiteSession.publish].
     * Always normalised per [MoqLitePath].
     */
    val suffix: String

    /**
     * Start a new group. Allocates a fresh sequence id and opens a new
     * uni stream pre-loaded with `DataType=Group + GroupHeader`. Idempotent
     * — calling [startGroup] when the previous group hasn't been ended
     * is treated as an implicit [endGroup] then a new start.
     */
    suspend fun startGroup()

    /**
     * Push one [payload] (one Opus packet) as a `varint(size) + payload`
     * frame on the current group's uni stream. Auto-starts a group if
     * none is active.
     *
     * Returns false if no inbound subscriber is currently attached.
     * Subscriber-less sends silently drop on the wire — the relay keeps
     * the publisher's announce active either way, so unmute is
     * sample-accurate.
     */
    suspend fun send(payload: ByteArray): Boolean

    /** FIN the current group's uni stream. The next [send] starts a fresh group. */
    suspend fun endGroup()

    /**
     * Stop publishing. Sends `Announce(Ended)` on every active announce
     * bidi, FINs the current group, and releases all per-publisher
     * resources. Idempotent.
     */
    suspend fun close()
}
