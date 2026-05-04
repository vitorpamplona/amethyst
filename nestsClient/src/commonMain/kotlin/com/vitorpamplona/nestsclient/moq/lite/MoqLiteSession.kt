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
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quic.Varint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
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
    /**
     * `internal` (not `private`) so test code in the same module can
     * downcast to a platform [WebTransportSession] (typically
     * `QuicWebTransportSession`) and read diagnostic flow-control
     * counters from the underlying QUIC connection. Production code
     * paths inside this file continue to use `transport` as before.
     */
    internal val transport: WebTransportSession,
    private val scope: CoroutineScope,
) {
    private val state = Mutex()
    private val subscriptionsBySubscribeId: MutableMap<Long, ListenerSubscription> = HashMap()
    private var nextSubscribeId: Long = 0L
    private var groupPump: Job? = null

    /** Lazily-launched relay→us inbound bidi pump; only runs while a publisher is active. */
    private var bidiPump: Job? = null

    /**
     * Single shared announce-watch pump that runs while we have any
     * listener-side subscription. Closes the frames channel of any
     * subscription whose broadcast suffix goes Ended on the relay's
     * announce stream — see [pumpAnnounceWatch] for why this is the
     * only reliable signal of publisher disconnect under moq-lite
     * Lite-03. Lazily launched on first subscribe; lives until the
     * session scope is cancelled.
     */
    private var announceWatchJob: Job? = null

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
        Log.d("NestRx") { "announce(prefix='$prefix'): bidi opened, writing AnnouncePlease" }
        bidi.write(Varint.encode(MoqLiteControlType.Announce.code))
        bidi.write(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease(prefix)))
        Log.d("NestRx") { "announce(prefix='$prefix'): AnnouncePlease flushed, awaiting Active updates" }

        val updates = MutableSharedFlow<MoqLiteAnnounce>(replay = 0, extraBufferCapacity = 64)
        val pump =
            scope.launch {
                val buffer = MoqLiteFrameBuffer()
                var chunksSeen = 0
                try {
                    bidi.incoming().collect { chunk ->
                        chunksSeen += 1
                        Log.d("NestRx") { "announce(prefix='$prefix'): bidi chunk #$chunksSeen size=${chunk.size}" }
                        buffer.push(chunk)
                        while (true) {
                            val payload = buffer.readSizePrefixed() ?: break
                            updates.emit(MoqLiteCodec.decodeAnnounce(payload))
                        }
                    }
                    Log.d("NestRx") { "announce(prefix='$prefix'): bidi.incoming() ended naturally after $chunksSeen chunks" }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w("NestRx") { "announce(prefix='$prefix'): bidi.incoming() threw ${t::class.simpleName}: ${t.message} (chunks=$chunksSeen)" }
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
        // Open the announce-watch bidi BEFORE the subscribe goes
        // out. moq-rs uses the announce stream to propagate
        // broadcast availability into the subscriber session — a
        // subscribe that arrives before our session has any
        // announce bidi open is rejected with "not found", even
        // when the publisher's session is alive on the relay. The
        // bidi must be on the wire before subscribe sends; lazy-
        // launching after subscribe (the obvious-but-wrong shape)
        // races the relay's discovery and produces flaky misses,
        // especially for fresh listener sessions opened after a
        // wrapper-driven reconnect.
        ensureAnnounceWatchStarted()
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
        Log.d("NestRx") { "subscribe id=$id broadcast='$broadcast' track='$track': bidi opened, writing SUBSCRIBE bytes" }
        bidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
        bidi.write(MoqLiteCodec.encodeSubscribe(request))
        Log.d("NestRx") { "subscribe id=$id: SUBSCRIBE bytes flushed, awaiting response" }

        // Single long-running collector for the bidi's whole lifetime.
        // The collector parses the SubscribeResponse inline, signals it
        // via [responseDeferred], then continues draining the bidi
        // until the peer FINs (publisher disconnect under moq-lite
        // Lite-03) or the transport tears down. On collector exit,
        // the frames channel is closed so the consumer's
        // `frames.consumeAsFlow()` ends naturally.
        //
        // Why one collector (vs separate response read + lifetime
        // watch): the underlying QUIC stream's `incoming` is backed
        // by `Channel<ByteArray>.consumeAsFlow()` which CANCELS the
        // channel when the first collect ends. A second collect on a
        // fresh `bidi.incoming()` Flow would see the already-cancelled
        // channel and fire prematurely.
        //
        // Why we need a lifetime watch at all: pumpAnnounceWatch only
        // closes frames when the relay actively sends Announce(Ended).
        // A silent transport black-hole never reaches that path; without
        // a per-subscription bidi watch, consumers would hang forever
        // on dead UDP paths.
        val frames = Channel<MoqLiteFrame>(capacity = DEFAULT_FRAME_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val responseDeferred = CompletableDeferred<MoqLiteCodec.SubscribeResponse>()

        // Pre-register the subscription BEFORE launching the collector.
        // Otherwise: if the publisher FINs the bidi immediately after
        // sending Ok, the collector's exit-cleanup races subscribe()'s
        // post-await registration — collector exits, runs `remove(id)`
        // against an empty map, no-ops; subscribe() then inserts; the
        // frames channel is now in the map with no live collector to
        // close it on transport tear-down. Consumer hangs forever.
        // Pre-registering means the collector's idempotent
        // remove+close cleanup always finds and closes the frames
        // channel, regardless of timing.
        val sub =
            ListenerSubscription(
                id = id,
                request = request,
                bidi = bidi,
                frames = frames,
            )
        state.withLock {
            subscriptionsBySubscribeId[id] = sub
            if (groupPump == null) groupPump = scope.launch { pumpUniStreams() }
        }

        scope.launch {
            val responseBuffer = MoqLiteFrameBuffer()
            var responseParsed = false
            var chunksSeen = 0
            try {
                bidi.incoming().collect { chunk ->
                    chunksSeen += 1
                    if (!responseParsed) {
                        Log.d("NestRx") {
                            "subscribe id=$id: bidi chunk #$chunksSeen size=${chunk.size} (response not yet parsed)"
                        }
                        responseBuffer.push(chunk)
                        val typeCode = responseBuffer.readVarint() ?: return@collect
                        val body = responseBuffer.readSizePrefixed() ?: return@collect
                        val out = MoqWriter()
                        out.writeVarint(typeCode)
                        out.writeVarint(body.size.toLong())
                        out.writeBytes(body)
                        val resp = MoqLiteCodec.decodeSubscribeResponse(out.toByteArray())
                        responseDeferred.complete(resp)
                        responseParsed = true
                    }
                    // Post-response chunks are silently discarded —
                    // moq-lite leaves the bidi idle post-Ok. The signal
                    // we care about is the flow's natural completion.
                }
                Log.d("NestRx") { "subscribe id=$id: bidi.incoming() flow ended naturally after $chunksSeen chunks (responseParsed=$responseParsed)" }
            } catch (ce: CancellationException) {
                if (!responseDeferred.isCompleted) responseDeferred.completeExceptionally(ce)
                throw ce
            } catch (t: Throwable) {
                Log.w("NestRx") { "subscribe id=$id: bidi.incoming() threw ${t::class.simpleName}: ${t.message} (chunks=$chunksSeen)" }
                if (!responseDeferred.isCompleted) responseDeferred.completeExceptionally(t)
            }
            if (!responseDeferred.isCompleted) {
                Log.w("NestRx") { "subscribe id=$id: bidi closed BEFORE any response parsed (chunks=$chunksSeen)" }
                responseDeferred.completeExceptionally(
                    MoqLiteSubscribeException("subscribe stream FIN before reply for id=$id"),
                )
            }
            // Idempotent: if subscribe() unwound on a Dropped response
            // (or any throw from await), it already removed the
            // subscription before throwing. Either way: remove + close.
            val removed = state.withLock { subscriptionsBySubscribeId.remove(id) }
            removed?.frames?.close()
        }

        val resp =
            try {
                responseDeferred.await()
            } catch (t: Throwable) {
                state.withLock { subscriptionsBySubscribeId.remove(id) }
                frames.close()
                runCatching { bidi.finish() }
                throw t
            }
        when (resp) {
            is MoqLiteCodec.SubscribeResponse.Dropped -> {
                Log.w("NestRx") {
                    "SUBSCRIBE_DROP id=$id broadcast='$broadcast' track='$track' " +
                        "errCode=${resp.drop.errorCode} reason='${resp.drop.reasonPhrase}'"
                }
                state.withLock { subscriptionsBySubscribeId.remove(id) }
                frames.close()
                runCatching { bidi.finish() }
                throw MoqLiteSubscribeException(
                    "publisher rejected subscribe id=$id: errorCode=${resp.drop.errorCode} " +
                        "reason='${resp.drop.reasonPhrase}'",
                )
            }

            is MoqLiteCodec.SubscribeResponse.Ok -> {
                Log.d("NestRx") { "SUBSCRIBE_OK id=$id broadcast='$broadcast' track='$track'" }
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
     * Lock that serializes lazy-launch of the announce-watch.
     * Distinct from [state] so the synchronous `announce(prefix="")`
     * inside [ensureAnnounceWatchStarted] can suspend without
     * blocking other state-mutating operations.
     */
    private val announceWatchLock = Mutex()

    /**
     * Open the shared announce-watch bidi *synchronously* (and
     * launch its collector coroutine) if it isn't already running.
     * Idempotent. Called from [subscribe] before the subscribe
     * message goes on the wire so moq-rs has a chance to propagate
     * broadcast availability into our session before the subscribe
     * arrives — see the comment in [subscribe].
     */
    private suspend fun ensureAnnounceWatchStarted() {
        announceWatchLock.withLock {
            if (announceWatchJob != null) return
            val handle =
                try {
                    announce(prefix = "")
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Couldn't open the announce bidi — best effort,
                    // bail. Subscriptions still work; we just lose
                    // automatic cycle detection (the wrapper still
                    // re-issues on listener swap / explicit failure).
                    return
                }
            announceWatchJob =
                scope.launch {
                    try {
                        pumpAnnounceWatch(handle)
                    } finally {
                        announceWatchLock.withLock { announceWatchJob = null }
                    }
                }
        }
    }

    /**
     * Single shared announce-watch pump for ALL subscriptions on
     * this session. Driven by the bidi opened in
     * [ensureAnnounceWatchStarted]. For each
     * [MoqLiteAnnounceStatus.Ended] update, iterates the
     * subscription map and closes the frames channel of any
     * subscription whose `broadcast` matches the announce suffix.
     * The closed channel ends the consumer-facing
     * `frames.consumeAsFlow()` flow naturally — same shape as a
     * user-driven `handle.unsubscribe()` from the consumer's POV —
     * which lets the wrapper's re-issuance pump drive a fresh
     * subscribe against the same broadcast path. moq-lite supports
     * subscribe-before-announce, so a subscribe issued during the
     * gap (between Ended and the next Active under the same suffix)
     * attaches cleanly when the new publisher comes up.
     *
     * This pump survives announce-bidi errors via best-effort
     * silence — the session itself recovers via its own reconnect
     * path. Cancelled when [scope] is cancelled (session close).
     */
    private suspend fun pumpAnnounceWatch(handle: MoqLiteAnnouncesHandle) {
        try {
            handle.updates.collect { update ->
                Log.d("NestRx") {
                    "announce update status=${update.status} suffix='${update.suffix}' hops=${update.hops}"
                }
                if (update.status != MoqLiteAnnounceStatus.Ended) return@collect
                val targets =
                    state.withLock {
                        subscriptionsBySubscribeId.values
                            .filter { it.request.broadcast == update.suffix }
                            .toList()
                    }
                for (sub in targets) {
                    Log.w("NestRx") {
                        "announce ENDED closes sub id=${sub.id} broadcast='${sub.request.broadcast}'"
                    }
                    // Just close the frames channel — the
                    // wrapper-level collect of `frames.consumeAsFlow()`
                    // ends naturally and the wrapper pump re-issues.
                    // Don't fire `unsubscribe(id)` here: that'd FIN
                    // OUR side of the (still-alive) subscribe bidi,
                    // and the wrapper's re-issue would have to open
                    // a fresh bidi anyway. Keeping the subscribe
                    // bidi open lets a future subscribe-before-
                    // announce land cleanly.
                    sub.frames.close()
                    state.withLock { subscriptionsBySubscribeId.remove(sub.id) }
                    runCatching { sub.bidi.finish() }
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Announce bidi died — same best-effort fallback.
        } finally {
            runCatching { handle.close() }
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
            // coroutineScope binds each per-stream drain to this pump's
            // job — when the pump is cancelled (session close), every
            // drain is cancelled with it instead of leaking as a
            // sibling on the outer [scope] until the transport's flow
            // independently errors out.
            kotlinx.coroutines.coroutineScope {
                var seen = 0L
                transport.incomingUniStreams().collect { stream ->
                    val n = ++seen
                    Log.d("NestRx") { "transport delivered uni stream #$n (QUIC→moq seam)" }
                    launch { drainOneGroup(stream) }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("NestRx") { "pumpUniStreams ended with ${t::class.simpleName}: ${t.message}" }
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
                    Log.d("NestRx") { "uni grpHdr id=$subscribeId seq=$groupSequence" }
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
                    } else {
                        Log.w("NestRx") {
                            "uni frame drop: no live sub for id=$subscribeId seq=$groupSequence size=${frame.size}"
                        }
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
        Log.d("NestTx") { "publish suffix='$normalised'" }
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
            // Bind each per-bidi handler to this pump's job (see
            // [pumpUniStreams]'s identical comment) so they don't outlive
            // bidiPump.cancelAndJoin() in [close].
            kotlinx.coroutines.coroutineScope {
                var seen = 0L
                transport.incomingBidiStreams().collect { bidi ->
                    val n = ++seen
                    Log.d("NestTx") { "transport delivered inbound bidi #$n (QUIC→moq seam)" }
                    launch { handleInboundBidi(bidi) }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("NestTx") { "pumpInboundBidis ended with ${t::class.simpleName}: ${t.message}" }
            // Transport closed.
        }
    }

    private suspend fun handleInboundBidi(bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream) {
        val publisher = state.withLock { activePublisher } ?: return

        // Single long-running collector for the bidi's full lifetime.
        // Pre-fix this dispatch was split into a `firstOrNull()` to
        // peek the control byte + a `readSizePrefixedFromBidiInto`
        // to read the body — but `bidi.incoming()` is backed by
        // `Channel<ByteArray>.consumeAsFlow(consume=true)`, which
        // CANCELS the channel when the first collect ends. Any
        // attempt to re-collect from the bidi (e.g. to watch for
        // subscriber-disconnect FIN) saw an immediately-empty
        // closed flow, firing the cleanup right after registration
        // and starving the publisher's send path. With one collector
        // for the bidi's whole life, the dispatch reads the message,
        // the collector continues silently until peer FIN, and the
        // post-collect cleanup runs exactly once — same shape the
        // moq-lite session's [announce] pump already uses.
        val buffer = MoqLiteFrameBuffer()
        // typeCode is hoisted outside the collect lambda so it
        // survives across invocations — `buffer.readVarint()`
        // advances `pos`, so calling it again on the next collect
        // tick would read body bytes as if they were the control
        // varint and tear the dispatch state apart.
        var typeCode: Long? = null
        var dispatched = false
        var inboundSub: MoqLiteSubscribe? = null
        try {
            bidi.incoming().collect { chunk ->
                buffer.push(chunk)
                if (!dispatched) {
                    if (typeCode == null) typeCode = buffer.readVarint()
                    val tc = typeCode ?: return@collect
                    val controlType =
                        MoqLiteControlType.fromCode(tc) ?: run {
                            dispatched = true
                            runCatching { bidi.finish() }
                            return@collect
                        }
                    when (controlType) {
                        MoqLiteControlType.Announce -> {
                            val pleasePayload = buffer.readSizePrefixed() ?: return@collect
                            val please = MoqLiteCodec.decodeAnnouncePlease(pleasePayload)
                            val emittedSuffix =
                                MoqLitePath.stripPrefix(please.prefix, publisher.suffix) ?: publisher.suffix
                            Log.d("NestTx") {
                                "inbound AnnouncePlease prefix='${please.prefix}' → reply Active suffix='$emittedSuffix'"
                            }
                            bidi.write(
                                MoqLiteCodec.encodeAnnounce(
                                    MoqLiteAnnounce(
                                        status = MoqLiteAnnounceStatus.Active,
                                        suffix = emittedSuffix,
                                        hops = 0L,
                                    ),
                                ),
                            )
                            publisher.registerAnnounceBidi(bidi, emittedSuffix)
                            dispatched = true
                        }

                        MoqLiteControlType.Subscribe -> {
                            val subPayload = buffer.readSizePrefixed() ?: return@collect
                            val sub = MoqLiteCodec.decodeSubscribe(subPayload)
                            Log.d("NestTx") {
                                "inbound SUBSCRIBE id=${sub.id} broadcast='${sub.broadcast}' track='${sub.track}' " +
                                    "priority=${sub.priority} maxLatencyMs=${sub.maxLatencyMillis}"
                            }
                            // Register the subscription BEFORE sending Ok so the
                            // peer's observation of Ok is a happens-after of
                            // `inboundSubs += sub`. Otherwise on dispatchers that
                            // resume the peer's `bidi.incoming().first()`
                            // continuation before this coroutine's continuation
                            // (notably Windows under Dispatchers.Default), the
                            // peer's first `publisher.send` after Ok races the
                            // registration and observes an empty subscriber set.
                            publisher.registerInboundSubscription(sub)
                            inboundSub = sub
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
                            dispatched = true
                        }

                        else -> {
                            // Lite-03 treats Session/Fetch/Probe as
                            // separate flows; we don't implement them.
                            runCatching { bidi.finish() }
                            dispatched = true
                        }
                    }
                }
                // Post-dispatch chunks are silently discarded —
                // Lite-03's announce / subscribe bidis are idle
                // after the response. The signal we care about is
                // the flow's natural completion (peer FIN =
                // subscriber-disconnect, or transport drop).
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Bidi errored — fall through to the same cleanup.
        }
        // Flow ended (peer FIN or error). Remove the inbound
        // subscribe so the publisher's send path stops keying new
        // groups off this dead subscriber. Announce bidis are
        // owned by the publisher state for sending Ended on
        // publisher-close — we don't remove them here.
        inboundSub?.let {
            Log.d("NestTx") {
                "inbound SUBSCRIBE FIN'd: removing id=${it.id} broadcast='${it.broadcast}' track='${it.track}'"
            }
            publisher.removeInboundSubscription(it)
        }
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

    private class ListenerSubscription(
        val id: Long,
        val request: MoqLiteSubscribe,
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
                val wasEmpty = inboundSubs.isEmpty()
                inboundSubs += sub
                if (wasEmpty) {
                    Log.d("NestTx") {
                        "first inbound subscriber attached id=${sub.id} broadcast='${sub.broadcast}' track='${sub.track}'"
                    }
                }
            }
        }

        /**
         * Remove an inbound subscription whose bidi was FIN'd by the
         * relay (subscriber disconnected). FINs the current group
         * defensively because [openNextGroupLocked] keys each uni
         * stream off `inboundSubs.first()`'s id; if the dropped sub
         * was first, the current uni stream is dead-routed and the
         * next send must open a fresh group keyed off whatever
         * live sub is now first.
         */
        suspend fun removeInboundSubscription(sub: MoqLiteSubscribe) {
            gate.withLock {
                if (publisherClosed) return
                if (!inboundSubs.remove(sub)) return
                runCatching { currentGroup?.uni?.finish() }
                currentGroup = null
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
                // Single-allocation framing: write the varint length
                // directly into a buffer sized for `varint + payload`,
                // then copy the payload after it. The previous shape
                // (`Varint.encode(...) + payload`) allocated twice per
                // Opus frame — once for the varint, once for the `+`
                // concatenation — at 50 fps × N speakers that's measurable
                // young-gen pressure on the audio hot path.
                val payloadSize = payload.size
                val varintLen = Varint.size(payloadSize.toLong())
                val framed = ByteArray(varintLen + payloadSize)
                Varint.writeTo(payloadSize.toLong(), framed, 0)
                payload.copyInto(framed, varintLen)
                val writeResult = runCatching { group.uni.write(framed) }
                if (writeResult.isFailure) {
                    // The uni stream errored (peer reset, transport closed,
                    // FIN'd by removeInboundSubscription). Drop the dead
                    // stream so the next send opens a fresh group instead of
                    // re-trying on a corpse, and surface the failure to the
                    // caller — the prior contract of "always true on
                    // not-muted" silently masked publisher disconnects.
                    runCatching { group.uni.finish() }
                    currentGroup = null
                    return false
                }
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
            Log.d("NestTx") {
                "openGroup seq=$sequence keyedOnSubId=${sub.id} broadcast='${sub.broadcast}' track='${sub.track}' " +
                    "inboundSubsCount=${inboundSubs.size}"
            }
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
