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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayServerBase
import com.vitorpamplona.quartz.nip01Core.relay.server.SessionSink
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** One NIP-FE request as the handler needs it. The host routes by [HttpRelayCommand.path]. */
class HttpRelayRequest(
    val command: HttpRelayCommand,
    /** The `Authorization` header as sent, or null. */
    val authorization: String?,
    /**
     * The body. Hosts bound the read at [HttpRelayHandler.maxBodyBytes]: the engine measures a
     * frame in characters, and a UTF-8 character takes up to three bytes.
     */
    val body: ByteArray,
)

/**
 * Where [HttpRelayHandler] writes an answer. The host owns the socket, the headers and any
 * compression; the handler decides the status and the lines. On 401 the host adds
 * `WWW-Authenticate: Nostr`, on 429 and 503 `Retry-After`. A [HttpRelayReaderStalled] thrown out of
 * either call means the client stopped reading, and the host drops the connection unfinished.
 */
interface HttpRelayResponse {
    /** An answer that is one frame, with its status: a refusal, or a command answered at once. */
    suspend fun single(
        status: Int,
        frame: String,
    )

    /** A 200 answer written line by line, as `application/x-ndjson`. Returns when [lines] does. */
    suspend fun stream(lines: suspend HttpRelayLines.() -> Unit)
}

/** The body of a streamed answer. */
interface HttpRelayLines {
    /** One frame and its newline. May suspend on the socket: that is the backpressure. */
    suspend fun line(frame: String)

    /** Sends what [line] has written so far; called whenever no frame is waiting. */
    suspend fun flush()
}

/** The client stopped reading an answer; the host drops the connection instead of finishing it. */
class HttpRelayReaderStalled : Exception("the client stopped reading the answer")

/**
 * NIP-FE: one relay command per HTTP request, run on its own [RelayServerBase] session, so every
 * limit and policy the socket applies applies here, and answered with the relay's own frames up to
 * the command's answer, without their subscription id. Nothing outlives the request.
 *
 * Admission (how many requests a client may run) is the host's: gate before calling [handle], so a
 * refused request does not spend a NIP-98 token the handler would have verified.
 */
class HttpRelayHandler(
    private val server: RelayServerBase,
    /** The prefixes a NIP-98 `u` may carry (the relay's http origin, its .onion), asked per request; never from the request. */
    private val origins: () -> List<String>,
    /** How long one answer may run, first byte to last. [Duration.INFINITE] turns the deadline off. */
    private val deadline: Duration = DEFAULT_DEADLINE,
    /**
     * Tokens are not single-use: a request can land on any instance, which a per-process memory of
     * spent tokens cannot follow, and the body's hash already limits a captured token to the one
     * command it signs, inside its window.
     */
    private val verifier: Nip98AuthVerifier = Nip98AuthVerifier(rejectReplays = false),
    /** Frames queued ahead of a slow reader before the answer is cut short. */
    private val maxQueuedFrames: Int = DEFAULT_MAX_QUEUED_FRAMES,
    /** How long past the deadline the last line may take before the reader counts as stalled. */
    private val tailGrace: Duration = DEFAULT_TAIL_GRACE,
) {
    /** The largest body that can still be a frame within the relay's message limit, or null for no limit. */
    val maxBodyBytes: Long? get() = server.limits?.maxMessageLength?.let { it.toLong() * 3 }

    suspend fun handle(
        request: HttpRelayRequest,
        response: HttpRelayResponse,
    ) {
        val command = request.command
        val max = server.limits?.maxMessageLength
        maxBodyBytes?.let { cap ->
            if (request.body.size > cap) {
                return response.single(HttpRelayStatus.PAYLOAD_TOO_LARGE, closed("invalid: the command exceeds $max characters"))
            }
        }
        val frame =
            command.frameOf(request.body.decodeToString())
                ?: return response.single(HttpRelayStatus.BAD_REQUEST, closed("invalid: the body is not ${command.name}'s arguments"))
        // Characters, as the engine's own limit counts them.
        if (max != null && frame.length > max) {
            return response.single(HttpRelayStatus.PAYLOAD_TOO_LARGE, closed("invalid: the command exceeds $max characters"))
        }
        val signedIn =
            when (val proof = proofOf(request)) {
                is Proof.Anonymous -> {
                    null
                }

                is Proof.Signed -> {
                    proof.pubkey
                }

                is Proof.Refused -> {
                    val reason = proof.reason
                    return response.single(HttpRelayStatus.forReason(reason), closed(reason))
                }
            }
        exchange(frame, command, signedIn, response)
    }

    /** A frame as queued: its wire text, its type when the engine built one, and whether it ends the answer. */
    private class Frame(
        val json: String,
        val message: Message?,
        val last: Boolean,
    )

    private suspend fun exchange(
        frame: String,
        command: HttpRelayCommand,
        signedIn: HexKey?,
        response: HttpRelayResponse,
    ) = coroutineScope {
        val frames = Channel<Frame>(maxQueuedFrames)
        val ended = CompletableDeferred<Unit>()

        // Called on the engine's coroutines and cannot suspend, so a frame that does not fit ends the answer.
        fun offer(frame: Frame) {
            val sent = frames.trySend(frame)
            if (sent.isClosed) return
            if (sent.isFailure || frame.last) {
                frames.close()
                ended.complete(Unit)
            }
        }

        fun fail(reason: String) = offer(Frame(closed(reason), ClosedMessage(HttpRelayCommand.SUB_ID, reason), last = true))
        val sink =
            object : SessionSink {
                override fun message(message: Message) {
                    // The challenge every connection opens with; this one proves its key by NIP-98 instead.
                    if (message is AuthMessage) return
                    offer(Frame(withoutSubId(message.toJson()), message, command.ends(message)))
                }

                override fun raw(json: String) = offer(Frame(withoutSubId(json), null, last = false))
            }
        val session =
            launch {
                try {
                    server.serve(sink) { session ->
                        val refused = signedIn?.let { session.authenticateByTransport(it) }
                        if (refused != null) fail(refused) else session.receive(frame)
                        ended.await()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A backend or policy that throws is the relay's failure, answered as one.
                    fail(MachineReadablePrefix.ERROR.format(e.message ?: "the relay failed this command"))
                }
            }
        val due = TimeSource.Monotonic.markNow() + deadline

        suspend fun single(
            status: Int,
            json: String,
        ) = bounded(due) { response.single(status, json) }
        try {
            val first = withTimeoutOrNull(deadline) { frames.receiveCatching().getOrNull() }
            val status = HttpRelayStatus.of(first?.message)
            when {
                first == null -> {
                    single(HttpRelayStatus.UNAVAILABLE, closed("error: no answer within $deadline"))
                }

                first.last || status != HttpRelayStatus.OK -> {
                    single(status, first.json)
                }

                else -> {
                    response.stream {
                        bounded(due) {
                            when (drain(first, frames, due)) {
                                Ending.ANSWERED -> {}
                                Ending.DEADLINE -> line(closed("error: the answer ran past $deadline"))
                                Ending.CUT -> line(closed("error: slow reader, over $maxQueuedFrames frames waiting"))
                            }
                            flush()
                        }
                    }
                }
            }
        } finally {
            session.cancel()
        }
    }

    /** Runs [block] until [due] plus the tail grace; a write still blocked then is a reader that stopped. */
    private suspend fun bounded(
        due: TimeSource.Monotonic.ValueTimeMark,
        block: suspend () -> Unit,
    ) {
        val left = (-due.elapsedNow()).coerceAtLeast(Duration.ZERO) + tailGrace
        try {
            withTimeout(left) { block() }
        } catch (_: TimeoutCancellationException) {
            throw HttpRelayReaderStalled()
        }
    }

    /** How a streamed answer stopped: at its answer frame, at the deadline, or cut because the reader fell behind. */
    private enum class Ending { ANSWERED, DEADLINE, CUT }

    /**
     * Writes [first] and what follows up to the command's answer, flushing whenever nothing is
     * waiting so a burst leaves as one write. Stops at the answer: a live event queued behind it is
     * not part of it. The deadline is read between frames and never interrupts a write, so every line
     * leaves whole, and the answer frame goes out even at the deadline: the answer is complete.
     */
    private suspend fun HttpRelayLines.drain(
        first: Frame,
        frames: Channel<Frame>,
        due: TimeSource.Monotonic.ValueTimeMark,
    ): Ending {
        var frame = first
        while (true) {
            line(frame.json)
            if (frame.last) return Ending.ANSWERED
            frame = frames.tryReceive().getOrNull() ?: run {
                flush()
                val left = -due.elapsedNow()
                if (!left.isPositive()) return Ending.DEADLINE
                val next = withTimeoutOrNull(left) { frames.receiveCatching() } ?: return Ending.DEADLINE
                // Closed with no answer frame in it: the send side gave up on this reader.
                next.getOrNull() ?: return Ending.CUT
            }
            if (!frame.last && due.hasPassedNow()) return Ending.DEADLINE
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
     * A NIP-98 header, checked against every address in [origins], so a token signed at the .onion
     * verifies there. It must bind the body's hash: it authorizes one command.
     * Another scheme (a proxy's Basic, a client's Bearer) is not addressed to the relay and is ignored.
     */
    private suspend fun proofOf(request: HttpRelayRequest): Proof {
        val header = request.authorization?.trim().orEmpty()
        val scheme = Nip98AuthVerifier.SCHEME
        if (!header.regionMatches(0, scheme, 0, scheme.length, ignoreCase = true)) return Proof.Anonymous
        val token = scheme + header.substring(scheme.length).trim()
        val accepted = origins().map { it.trimEnd('/') + request.command.path }
        if (accepted.isEmpty()) return Proof.Refused(MachineReadablePrefix.AUTH_REQUIRED.format("this relay names no url to sign"))
        return when (val r = verifier.verify(token, "POST", accepted, request.body)) {
            is Nip98AuthVerifier.Result.Verified -> {
                Proof.Signed(r.pubkey)
            }

            is Nip98AuthVerifier.Result.Missing -> {
                Proof.Anonymous
            }

            // A full replay cache, in a verifier that keeps one, is the relay's limit, not the token's fault.
            is Nip98AuthVerifier.Result.Malformed -> {
                if (MachineReadablePrefix.parse(r.reason) == MachineReadablePrefix.RATE_LIMITED) {
                    Proof.Refused(r.reason)
                } else {
                    Proof.Refused(MachineReadablePrefix.AUTH_REQUIRED.format("NIP-98 ${r.reason}"))
                }
            }
        }
    }

    private fun closed(reason: String) = withoutSubId(ClosedMessage(HttpRelayCommand.SUB_ID, reason).toJson())

    companion object {
        val DEFAULT_DEADLINE = 30_000.milliseconds

        /** The websocket's slow-consumer bound in the reference relays. */
        const val DEFAULT_MAX_QUEUED_FRAMES = 8192

        val DEFAULT_TAIL_GRACE = 5_000.milliseconds
    }
}

/** The frames that carry a subscription id in the engine; NIP-FE sends them without it. */
private val SUBSCRIPTION_FRAMES = setOf("EVENT", "EOSE", "CLOSED", "COUNT")

private const val SUB_ID_FIELD = ",\"" + HttpRelayCommand.SUB_ID + "\""

/**
 * [frame] as NIP-FE sends it: the engine's frame with its `"http"` subscription id taken out,
 * `["EVENT","http",{…}]` → `["EVENT",{…}]`, `["EOSE","http"]` → `["EOSE"]`. Other frames pass as they are.
 */
internal fun withoutSubId(frame: String): String {
    if (!frame.startsWith("[\"")) return frame
    val verbEnd = frame.indexOf('"', 2)
    if (verbEnd < 0 || frame.substring(2, verbEnd) !in SUBSCRIPTION_FRAMES) return frame
    if (!frame.startsWith(SUB_ID_FIELD, verbEnd + 1)) return frame
    return frame.substring(0, verbEnd + 1) + frame.substring(verbEnd + 1 + SUB_ID_FIELD.length)
}
