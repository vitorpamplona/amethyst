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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** How many EVENTs [publishStream] keeps unanswered at once when the caller does not say. */
const val DEFAULT_STREAM_IN_FLIGHT = 512

/**
 * How many bytes of EVENT frames [publishStream] keeps unanswered at once when the caller does
 * not say: half of OkHttp's 16 MiB websocket send queue, past which OkHttp closes the socket
 * rather than queue the frame, leaving room for the socket's other traffic.
 */
const val DEFAULT_STREAM_IN_FLIGHT_BYTES = 8L * 1024 * 1024

/**
 * The tally of a [publishStream] call. [sent] counts the events handed to the relay;
 * every one of them got exactly one [publishStream] `onResult`, so [sent] equals
 * [answered] + [unanswered]. [stalled] is true when the relay went silent for the idle
 * window with events still outstanding: those were reported as
 * [PublishResult.NO_RESPONSE], and the rest of the input was never read.
 */
class StreamPublishResult(
    val sent: Int,
    val answered: Int,
    val unanswered: Int,
    val stalled: Boolean,
)

/**
 * Publishes every event of [events] to one [relay] with up to [maxInFlight] of them
 * awaiting their `OK` at once, and reports each verdict to [onResult] as it lands, in
 * arrival order of the OKs rather than of the events.
 *
 * This is the bulk counterpart of [publishAndConfirm]: one listener for the whole
 * stream instead of one per event, so a backfill of millions costs a map lookup per
 * OK. The window is the backpressure: reading [events] suspends while [maxInFlight]
 * events, or [maxInFlightBytes] of their frames, are unanswered, so a slow relay slows the
 * reader instead of growing the outbox. The byte bound is what keeps a window of large
 * events (a follow list runs to hundreds of KB) inside the socket's send queue; an event
 * larger than the whole budget is sent alone.
 *
 * Unless the socket is already open, the first event goes out alone and the window opens
 * on its verdict: until the socket is up, the pool both queues an EVENT and flushes its
 * outbox on connect, so a full window sent into a connecting socket would reach the relay
 * twice. On an open socket (a caller that holds it with [INostrClient.keepConnected], say)
 * there is no such flush to race, and waiting would only cost a round trip per call.
 *
 * Delivery leans on the pool: an event whose socket dropped stays in the outbox and is
 * re-sent on reconnect, and a `rate-limited:` refusal that states its back-off is
 * re-sent by the pool after it, so such an OK is held as provisional (up to
 * [RATE_LIMIT_WAITS] times per event) and the retry's verdict is the one reported. An
 * event the outbox gives up on is reported as [PublishResult.DISCONNECTED].
 *
 * [idleTimeoutMs] is an idle window over the relay's answers: it only runs while
 * something is outstanding, any OK resets it, and when it runs out the call reports
 * everything outstanding as [PublishResult.NO_RESPONSE] and returns with
 * [StreamPublishResult.stalled] set. Copies of one id in [events] share the relay's
 * single verdict and are each reported.
 */
suspend fun INostrClient.publishStream(
    relay: NormalizedRelayUrl,
    events: Flow<Event>,
    maxInFlight: Int = DEFAULT_STREAM_IN_FLIGHT,
    idleTimeoutMs: Long = 30_000,
    maxInFlightBytes: Long = DEFAULT_STREAM_IN_FLIGHT_BYTES,
    onResult: (Event, PublishResult) -> Unit,
): StreamPublishResult = publishOutgoing(relay, events.map { Outgoing(it, null, 0) }, maxInFlight, idleTimeoutMs, maxInFlightBytes, onResult)

/**
 * An event and its own JSON object, as a bulk publisher read it: [json] is what goes on the
 * wire, [event] what the bookkeeping keys on. See [EventCmd.eventJson] for what [json] must be.
 * [jsonBytes] is [json]'s UTF-8 length, counted where the item is made: a publisher parsing on
 * several threads counts there, off [publishRawStream]'s single reader (3% of a resend's CPU).
 */
class EventWithJson(
    val event: Event,
    val json: String,
    val jsonBytes: Long = utf8Bytes(json),
)

/**
 * [publishStream] for events already held as JSON: each goes out as its [EventWithJson.json],
 * verbatim, through [INostrClient.publishRaw], and the byte window counts that text exactly.
 * For a publisher reading an export, where serializing the parsed event again was the largest
 * cost after the socket's own.
 */
suspend fun INostrClient.publishRawStream(
    relay: NormalizedRelayUrl,
    events: Flow<EventWithJson>,
    maxInFlight: Int = DEFAULT_STREAM_IN_FLIGHT,
    idleTimeoutMs: Long = 30_000,
    maxInFlightBytes: Long = DEFAULT_STREAM_IN_FLIGHT_BYTES,
    onResult: (Event, PublishResult) -> Unit,
): StreamPublishResult = publishOutgoing(relay, events.map { Outgoing(it.event, it.json, it.jsonBytes) }, maxInFlight, idleTimeoutMs, maxInFlightBytes, onResult)

/** One event of a stream, with its JSON when the publisher had it. */
private class Outgoing(
    val event: Event,
    val json: String?,
    val jsonBytes: Long,
) {
    fun frameBytes(): Long = if (json != null) jsonBytes + EVENT_FRAME_WRAPPER else event.estimatedFrameBytes()
}

private suspend fun INostrClient.publishOutgoing(
    relay: NormalizedRelayUrl,
    events: Flow<Outgoing>,
    maxInFlight: Int,
    idleTimeoutMs: Long,
    maxInFlightBytes: Long,
    onResult: (Event, PublishResult) -> Unit,
): StreamPublishResult {
    require(maxInFlight > 0) { "maxInFlight must be positive, was $maxInFlight" }
    val target = relay
    val relaySet = setOf(target)
    val window = Semaphore(maxInFlight)
    val replies = Channel<StreamReply>(Channel.UNLIMITED)
    val lock = Mutex()
    val inFlight = LinkedHashMap<HexKey, StreamInFlight>()
    val clock = IdleClock()
    // Already open: no connect-time flush can send the window a second time. (A stream that
    // starts in the instant between the socket opening and its replay ending can still send an
    // event twice, as a first event alone could; the second OK finds nothing outstanding.)
    val firstVerdict = CompletableDeferred<Unit>().apply { if (target in connectedRelaysFlow().value) complete(Unit) }
    // Signalled on every settle; the reader waits on it while the window is full by bytes.
    val roomFreed = Channel<Unit>(Channel.CONFLATED)
    var bytesInFlight = 0L
    var sent = 0
    var answered = 0
    var unanswered = 0

    val listener =
        object : RelayConnectionListener {
            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is OkMessage && relay.url == target) {
                    replies.trySend(StreamReply.Ok(msg.eventId, msg.success, msg.message, msg.retryAfterMs))
                }
            }

            override fun onEventGaveUp(
                relay: IRelayClient,
                event: Event,
            ) {
                if (relay.url == target) replies.trySend(StreamReply.GaveUp(event.id))
            }
        }

    /** Takes [id] out of the window and reports each of its copies; false when it was not outstanding. */
    suspend fun settle(
        id: HexKey,
        verdict: (StreamInFlight) -> PublishResult,
    ): Boolean {
        val entry =
            lock.withLock {
                inFlight.remove(id)?.also { bytesInFlight -= it.bytes }
            } ?: return false
        roomFreed.trySend(Unit)
        val result = verdict(entry)
        repeat(entry.copies) {
            window.release()
            if (result.message == PublishResult.NO_RESPONSE || result.message == PublishResult.DISCONNECTED) unanswered++ else answered++
            onResult(entry.event, result)
        }
        return true
    }

    addConnectionListener(listener)
    try {
        var stalled = false
        coroutineScope {
            val reader =
                launch {
                    try {
                        events.collect { outgoing ->
                            val event = outgoing.event
                            val bytes = outgoing.frameBytes()
                            // Only this reader adds bytes, so room seen here can only grow until it is taken.
                            while (lock.withLock { bytesInFlight > 0 && bytesInFlight + bytes > maxInFlightBytes }) roomFreed.receive()
                            window.acquire()
                            val first =
                                lock.withLock {
                                    val held = inFlight[event.id]
                                    if (held != null) {
                                        held.copies++
                                        false
                                    } else {
                                        // The window was empty, so silence until now was ours, not the relay's.
                                        if (inFlight.isEmpty()) clock.bump()
                                        inFlight[event.id] = StreamInFlight(event, bytes, TimeSource.Monotonic.markNow())
                                        bytesInFlight += bytes
                                        true
                                    }
                                }
                            sent++
                            if (first) {
                                val json = outgoing.json
                                if (json != null) publishRaw(event, json, relaySet) else publish(event, relaySet)
                            }
                            if (sent == 1) firstVerdict.await()
                        }
                    } finally {
                        replies.trySend(StreamReply.InputDone)
                    }
                }

            var inputDone = false
            while (!inputDone || lock.withLock { inFlight.isNotEmpty() }) {
                val reply = replies.receiveWithinIdle(clock, idleTimeoutMs)
                if (reply == null) {
                    // Nothing outstanding is the input being slow, not the relay: the first
                    // event, when it comes, still goes out alone.
                    if (lock.withLock { inFlight.isEmpty() }) {
                        clock.bump()
                        continue
                    }
                    stalled = true
                    break
                }
                if (reply !is StreamReply.InputDone) firstVerdict.complete(Unit)
                when (reply) {
                    StreamReply.InputDone -> {
                        inputDone = true
                    }

                    is StreamReply.GaveUp -> {
                        if (settle(reply.id) { PublishResult(false, PublishResult.DISCONNECTED) }) clock.bump()
                    }

                    is StreamReply.Ok -> {
                        val held =
                            lock.withLock {
                                val entry = inFlight[reply.id] ?: return@withLock null
                                // The pool re-sends a back-off it was told about; wait for that verdict instead.
                                if (!reply.success && reply.retryAfterMs != null && entry.rateLimitWaits < RATE_LIMIT_WAITS) {
                                    entry.rateLimitWaits++
                                    true
                                } else {
                                    false
                                }
                            }
                        when (held) {
                            null -> {}

                            true -> {
                                clock.bump()
                            }

                            false -> {
                                settle(reply.id) {
                                    PublishResult(reply.success, reply.message, it.sentAt.elapsedNow().inWholeMilliseconds, reply.retryAfterMs)
                                }
                                clock.bump()
                            }
                        }
                    }
                }
            }
            if (stalled) {
                // Joined before the snapshot: a reader caught between taking a slot and
                // sending would otherwise add an event no verdict is ever reported for.
                reader.cancel(CancellationException("relay went silent for ${idleTimeoutMs}ms"))
                reader.join()
                val left = lock.withLock { inFlight.keys.toList() }
                for (id in left) settle(id) { PublishResult(false, PublishResult.NO_RESPONSE) }
            }
        }
        return StreamPublishResult(sent, answered, unanswered, stalled)
    } finally {
        removeConnectionListener(listener)
        replies.close()
    }
}

private class StreamInFlight(
    val event: Event,
    val bytes: Long,
    val sentAt: TimeSource.Monotonic.ValueTimeMark,
) {
    var copies = 1
    var rateLimitWaits = 0
}

/**
 * About what the event's EVENT frame takes on the wire, without serializing it: the fixed
 * fields, then the content and every tag value as UTF-8 with their JSON escapes and quoting.
 * Counting chars instead reads a third of the bytes on CJK text, and the byte budget exists
 * to stay under a socket limit counted in bytes.
 */
private fun Event.estimatedFrameBytes(): Long {
    var size = FRAME_OVERHEAD + jsonStringBytes(content)
    for (tag in tags) {
        size += 2
        for (value in tag) size += jsonStringBytes(value) + 1
    }
    return size
}

/** [value] as a quoted JSON string in UTF-8: escapes add a byte (`\"`) or five (`\u0001`). */
private fun jsonStringBytes(value: String): Long {
    var bytes = 2L
    for (c in value) {
        bytes +=
            when {
                c == '"' || c == '\\' -> 2
                c < ' ' -> 6
                c < '\u0080' -> 1
                c < '\u0800' -> 2
                // Each half of a surrogate pair: 4 bytes for the pair.
                c.isSurrogate() -> 2
                else -> 3
            }
    }
    return bytes
}

/** [value]'s length in UTF-8. */
internal fun utf8Bytes(value: String): Long {
    var bytes = 0L
    for (c in value) {
        bytes +=
            when {
                c < '\u0080' -> 1
                c < '\u0800' -> 2
                // Each half of a surrogate pair: 4 bytes for the pair.
                c.isSurrogate() -> 2
                else -> 3
            }
    }
    return bytes
}

/** `["EVENT",` and `]` around an event's JSON object. */
private const val EVENT_FRAME_WRAPPER = 10L

/** `["EVENT",{…}]`, the keys, id, pubkey, sig, kind and created_at. */
private const val FRAME_OVERHEAD = 400L

private sealed interface StreamReply {
    class Ok(
        val id: HexKey,
        val success: Boolean,
        val message: String,
        val retryAfterMs: Long?,
    ) : StreamReply

    class GaveUp(
        val id: HexKey,
    ) : StreamReply

    object InputDone : StreamReply
}
