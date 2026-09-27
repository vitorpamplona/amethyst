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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayServerBase
import com.vitorpamplona.quartz.nip01Core.relay.server.SessionSink
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.TimeSource

/** One NIP-FE request as the handler needs it. The host routes by [HttpRelayCommand.path] and bounds the body while reading it. */
class HttpRelayRequest(
    val command: HttpRelayCommand,
    /** The `Authorization` header as sent, or null. */
    val authorization: String?,
    val body: ByteArray,
)

/**
 * Where [HttpRelayHandler] writes an answer. The host owns the socket, the
 * headers and any compression; the handler decides the status and the lines.
 * On 401 the host adds `WWW-Authenticate: Nostr`, on 429 and 503 `Retry-After`.
 */
interface HttpRelayResponse {
    /** An answer that is one frame, with its status: a refusal, or a command answered at once. */
    suspend fun single(
        status: Int,
        frame: String,
    )

    /**
     * A 200 answer written line by line, as `application/x-ndjson`. Returns
     * when [lines] does; a [HttpRelayReaderStalled] thrown out of it means
     * the client stopped reading and the host drops the connection unfinished.
     */
    suspend fun stream(lines: suspend HttpRelayLines.() -> Unit)
}

/** The body of a streamed answer. */
interface HttpRelayLines {
    /** One frame and its newline. May suspend on the socket: that is the backpressure. */
    suspend fun line(frame: String)

    /** Sends what [line] has written so far; called whenever no frame is waiting. */
    suspend fun flush()
}

/** The client stopped reading a streamed answer; the host drops the connection instead of finishing it. */
class HttpRelayReaderStalled : Exception("the client stopped reading the answer")

/**
 * NIP-FE: one relay command per HTTP request, run on its own [RelayServerBase]
 * session, so every limit and policy the socket applies applies here, and
 * answered with the relay's own frames up to the command's answer. Nothing
 * outlives the request. Admission (how many requests a client may run) is the
 * host's: gate before calling [handle], so a refused request does not spend a
 * NIP-98 token that the handler would have verified.
 */
class HttpRelayHandler(
    private val server: RelayServerBase,
    /** The prefixes a NIP-98 `u` may carry (the relay's http origin, its .onion), asked per request; never from the request. */
    private val origins: () -> List<String>,
    /** How long one answer may run, first byte to last. */
    private val deadlineMs: Long = DEFAULT_DEADLINE_MS,
    /** Its own replay cache, so public commands cannot evict another endpoint's. */
    private val verifier: Nip98AuthVerifier = Nip98AuthVerifier(),
    /** Frames queued ahead of a slow reader before the answer is cut short. */
    private val maxQueuedFrames: Int = DEFAULT_MAX_QUEUED_FRAMES,
    /** How long past the deadline the last line may take before the reader counts as stalled. */
    private val tailGraceMs: Long = DEFAULT_TAIL_GRACE_MS,
) {
    suspend fun handle(
        request: HttpRelayRequest,
        response: HttpRelayResponse,
    ) {
        val command = request.command
        val max = server.limits?.maxMessageLength
        if (max != null && request.body.size > max) {
            return response.single(HttpRelayStatus.PAYLOAD_TOO_LARGE, closed("invalid: the body exceeds $max bytes"))
        }
        val parsed =
            command.parse(request.body.decodeToString())
                ?: return response.single(HttpRelayStatus.BAD_REQUEST, closed("invalid: the body is not ${command.name}'s arguments"))
        if (max != null && parsed.wireLength > max) {
            return response.single(HttpRelayStatus.PAYLOAD_TOO_LARGE, closed("invalid: the command exceeds $max characters"))
        }
        val signedIn =
            when (val proof = proofOf(request)) {
                is Proof.Anonymous -> emptySet()
                is Proof.Signed -> setOf(proof.pubkey)
                is Proof.Refused -> return response.single(HttpRelayStatus.UNAUTHORIZED, closed(MachineReadablePrefix.AUTH_REQUIRED.format(proof.reason)))
            }
        exchange(parsed, command, signedIn, response)
    }

    /** A frame as queued: its wire text, and its type when the engine built one. */
    private class Frame(
        val json: String,
        val message: Message?,
    )

    private fun HttpRelayCommand.ends(frame: Frame) = frame.message?.let(::ends) == true

    private suspend fun exchange(
        parsed: HttpRelayCommand.Parsed,
        command: HttpRelayCommand,
        signedIn: Set<HexKey>,
        response: HttpRelayResponse,
    ) = coroutineScope {
        val frames = Channel<Frame>(maxQueuedFrames)
        val ended = CompletableDeferred<Unit>()

        // Called on the engine's coroutines and cannot suspend, so a frame that does not fit ends the answer.
        fun offer(
            frame: Frame,
            last: Boolean,
        ) {
            val sent = frames.trySend(frame)
            if (sent.isClosed) return
            if (sent.isFailure || last) {
                frames.close()
                ended.complete(Unit)
            }
        }
        val sink =
            object : SessionSink {
                override fun message(message: Message) {
                    // The challenge every connection opens with; this one proved its key by NIP-98 instead.
                    if (message is AuthMessage) return
                    offer(Frame(message.toJson(), message), command.ends(message))
                }

                override fun raw(json: String) = offer(Frame(json, null), false)
            }
        val session =
            launch {
                server.serve(sink, signedIn) {
                    it.receive(parsed.command)
                    ended.await()
                }
            }
        val started = TimeSource.Monotonic.markNow()

        fun remainingMs() = (deadlineMs - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)
        try {
            val first = withTimeoutOrNull(deadlineMs) { frames.receiveCatching().getOrNull() }
            when {
                first == null -> {
                    response.single(HttpRelayStatus.UNAVAILABLE, closed("error: no answer within ${deadlineMs / 1000}s"))
                }

                command.ends(first) || HttpRelayStatus.of(first.message) != HttpRelayStatus.OK -> {
                    response.single(HttpRelayStatus.of(first.message), first.json)
                }

                else -> {
                    response.stream {
                        // The deadline is read between frames and never interrupts a write, so every line leaves
                        // whole; a reader that stops reading altogether is dropped at the hard stop.
                        try {
                            withTimeout(remainingMs() + tailGraceMs) {
                                when (drain(first, frames, command, started)) {
                                    Ending.ANSWERED -> {}
                                    Ending.DEADLINE -> line(closed("error: the answer ran past ${deadlineMs / 1000}s"))
                                    Ending.CUT -> line(closed("error: slow reader, over $maxQueuedFrames frames waiting"))
                                }
                                flush()
                            }
                        } catch (_: TimeoutCancellationException) {
                            throw HttpRelayReaderStalled()
                        }
                    }
                }
            }
        } finally {
            session.cancel()
        }
    }

    /** How a streamed answer stopped: at its answer frame, at the deadline, or cut because the reader fell behind. */
    private enum class Ending { ANSWERED, DEADLINE, CUT }

    /**
     * Writes [first] and what follows up to the command's answer, flushing whenever nothing is waiting so
     * a burst leaves as one write. Stops at the answer: a live event queued behind it is not part of it.
     */
    private suspend fun HttpRelayLines.drain(
        first: Frame,
        frames: Channel<Frame>,
        command: HttpRelayCommand,
        started: TimeSource.Monotonic.ValueTimeMark,
    ): Ending {
        var frame = first
        while (true) {
            line(frame.json)
            if (command.ends(frame)) return Ending.ANSWERED
            frame = frames.tryReceive().getOrNull() ?: run {
                flush()
                val leftMs = deadlineMs - started.elapsedNow().inWholeMilliseconds
                if (leftMs <= 0) return Ending.DEADLINE
                val next = withTimeoutOrNull(leftMs) { frames.receiveCatching() } ?: return Ending.DEADLINE
                // Closed with no answer frame in it: the send side gave up on this reader.
                next.getOrNull() ?: return Ending.CUT
            }
            if (started.elapsedNow().inWholeMilliseconds >= deadlineMs) return Ending.DEADLINE
        }
    }

    /** Who a request acts as. */
    private sealed interface Proof {
        data object Anonymous : Proof

        class Signed(
            val pubkey: HexKey,
        ) : Proof

        class Refused(
            val reason: String,
        ) : Proof
    }

    /**
     * A NIP-98 header, checked against the address it names when that is one of [origins], so a token
     * signed at the .onion verifies there. It must bind the body's hash: it authorizes one command, once.
     * Another scheme (a proxy's Basic, a client's Bearer) is not addressed to the relay and is ignored.
     */
    private suspend fun proofOf(request: HttpRelayRequest): Proof {
        val header = request.authorization?.trim().orEmpty()
        val scheme = Nip98AuthVerifier.SCHEME
        if (!header.regionMatches(0, scheme, 0, scheme.length, ignoreCase = true)) return Proof.Anonymous
        val token = scheme + header.substring(scheme.length).trim()
        val accepted = origins().map { it.trimEnd('/') + request.command.path }
        val url = claimedUrl(token)?.takeIf { it in accepted } ?: accepted.firstOrNull() ?: return Proof.Refused("this relay names no url to sign")
        return when (val r = verifier.verify(token, "POST", url, request.body)) {
            is Nip98AuthVerifier.Result.Verified -> Proof.Signed(r.pubkey)
            is Nip98AuthVerifier.Result.Malformed -> Proof.Refused("NIP-98 ${r.reason}")
            is Nip98AuthVerifier.Result.Missing -> Proof.Anonymous
        }
    }

    /** The `u` tag of a NIP-98 token, or null when it does not decode; the verifier then says why. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun claimedUrl(token: String): String? =
        runCatching {
            val json = Base64.decode(token.removePrefix(Nip98AuthVerifier.SCHEME).trim()).decodeToString()
            OptimizedJsonMapper
                .fromJson(json)
                .tags
                .firstOrNull { it.size > 1 && it[0] == "u" }
                ?.get(1)
        }.getOrNull()

    private fun closed(reason: String) = ClosedMessage(HttpRelayCommand.SUB_ID, reason).toJson()

    companion object {
        const val DEFAULT_DEADLINE_MS = 30_000L

        /** The websocket's slow-consumer bound in the reference relays. */
        const val DEFAULT_MAX_QUEUED_FRAMES = 8192

        const val DEFAULT_TAIL_GRACE_MS = 5_000L
    }
}
