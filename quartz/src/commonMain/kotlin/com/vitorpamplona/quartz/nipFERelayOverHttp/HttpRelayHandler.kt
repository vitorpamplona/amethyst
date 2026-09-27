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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayServerBase
import com.vitorpamplona.quartz.nip01Core.relay.server.SessionSink
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
import com.vitorpamplona.quartz.nip98HttpAuth.tags.UrlTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** One NIP-FE request as the handler needs it: a POST to the relay's URL that is not a NIP-86 call. */
class HttpRelayRequest(
    /** The `Authorization` header as sent, or null. */
    val authorization: String?,
    /**
     * The body: one client frame. Hosts bound the read at [HttpRelayHandler.maxBodyBytes]: the
     * engine measures a frame in characters, and a UTF-8 character takes up to three bytes.
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
 * NIP-FE: one relay command per HTTP request. The body is the frame a client would send on the
 * websocket; it runs on its own [RelayServerBase] session, fed as socket text, so every limit and
 * policy the socket applies applies here; and the answer is the session's frames as the socket
 * would carry them, up to the one that ends the command's answer. Nothing outlives the request.
 *
 * Admission (how many requests a client may run) is the host's: gate before calling [handle], so a
 * refused request does not spend a NIP-98 token the handler would have verified.
 */
class HttpRelayHandler(
    private val server: RelayServerBase,
    /** The URLs a NIP-98 `u` may name (the relay's http URL, its .onion), asked per request; never from the request. */
    private val origins: () -> List<String>,
    /** How long one answer may run, first byte to last. [Duration.INFINITE] turns the deadline off. */
    private val deadline: Duration = DEFAULT_DEADLINE,
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
        val max = server.limits?.maxMessageLength
        val tooLarge = "invalid: the command exceeds $max characters"
        maxBodyBytes?.let { cap ->
            if (request.body.size > cap) return response.single(HttpRelayStatus.PAYLOAD_TOO_LARGE, notice(tooLarge))
        }
        val text = request.body.decodeToString()
        // Characters, as the engine's own limit counts them.
        if (max != null && text.length > max) return response.single(HttpRelayStatus.PAYLOAD_TOO_LARGE, notice(tooLarge))

        // Read here as well as in the engine, to refuse the commands HTTP does not carry and to
        // know what ends the answer and how to refuse it. The engine still parses the text itself,
        // as socket text, so the policies that judge raw frames run.
        val cmd =
            try {
                OptimizedJsonMapper.fromJsonToCommand(text)
            } catch (_: Exception) {
                null
            }
        val command =
            cmd?.let { HttpRelayCommand.of(it) }
                ?: return response.single(HttpRelayStatus.BAD_REQUEST, notice("invalid: the body is not one REQ, COUNT or EVENT frame"))

        val signedIn =
            when (val proof = proofOf(request)) {
                is Proof.Anonymous -> null
                is Proof.Signed -> proof.pubkey
                is Proof.Refused -> return response.single(HttpRelayStatus.forReason(proof.reason), HttpRelayCommand.refusal(cmd, proof.reason).toJson())
            }
        exchange(text, cmd, command, signedIn, response)
    }

    /** A frame as queued: its wire text, its type when the engine built one, and whether it ends the answer. */
    private class Frame(
        val json: String,
        val message: Message?,
        val last: Boolean,
    )

    private suspend fun exchange(
        text: String,
        cmd: Command,
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

        fun refusal(reason: String) = HttpRelayCommand.refusal(cmd, reason)

        fun fail(reason: String) = refusal(reason).let { offer(Frame(it.toJson(), it, last = true)) }
        val sink =
            object : SessionSink {
                override fun message(message: Message) {
                    // The challenge every connection opens with; this one proves its key by NIP-98 instead.
                    if (message is AuthMessage) return
                    offer(Frame(message.toJson(), message, command.ends(message)))
                }

                override fun raw(json: String) = offer(Frame(json, null, last = false))
            }
        val session =
            launch {
                try {
                    server.serve(sink) { session ->
                        val refused = signedIn?.let { session.authenticateByTransport(it) }
                        if (refused != null) fail(refused) else session.receive(text)
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
                    single(HttpRelayStatus.UNAVAILABLE, notice("error: no answer within $deadline"))
                }

                first.last || status != HttpRelayStatus.OK -> {
                    single(status, first.json)
                }

                else -> {
                    response.stream {
                        bounded(due) {
                            when (drain(first, frames, due)) {
                                Ending.ANSWERED -> {}
                                Ending.DEADLINE -> line(refusal("error: the answer ran past $deadline").toJson())
                                Ending.CUT -> line(refusal("error: slow reader, over $maxQueuedFrames frames waiting").toJson())
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
     * A NIP-98 header. Its `u` may name any address in [origins], with or without the trailing
     * slash, so a token signed at the .onion verifies there; the token is checked once, against the
     * address it names. It must bind the body's hash and be within 60 seconds of now; within that
     * window it may come again for the same body. Another scheme (a proxy's Basic, a client's
     * Bearer) is not addressed to the relay and is ignored.
     */
    private suspend fun proofOf(request: HttpRelayRequest): Proof {
        val header = request.authorization?.trim().orEmpty()
        val scheme = Nip98AuthVerifier.SCHEME
        if (!header.regionMatches(0, scheme, 0, scheme.length, ignoreCase = true)) return Proof.Anonymous
        val token = scheme + header.substring(scheme.length).trim()
        val addresses = origins()
        if (addresses.isEmpty()) return Proof.Refused(MachineReadablePrefix.AUTH_REQUIRED.format("this relay names no url to sign"))
        // The address the token names, when it is one of ours; otherwise the first, and the
        // verifier refuses the mismatch (or whatever else is wrong with the token) itself.
        val signed = claimedUrl(token)
        val url = signed?.takeIf { u -> addresses.any { it.trimEnd('/') == u.trimEnd('/') } } ?: addresses.first()
        // A fresh verifier each time: it remembers the tokens it accepts, and a NIP-FE token is not
        // single-use. Every command it can sign is idempotent, so a repeat only repeats a read or
        // re-sends an event the relay has, and a client may retry without signing again.
        return when (val r = Nip98AuthVerifier(toleranceSeconds = TOKEN_WINDOW_SECONDS).verify(token, "POST", url, request.body)) {
            is Nip98AuthVerifier.Result.Verified -> Proof.Signed(r.pubkey)
            is Nip98AuthVerifier.Result.Missing -> Proof.Anonymous
            is Nip98AuthVerifier.Result.Malformed -> Proof.Refused(MachineReadablePrefix.AUTH_REQUIRED.format("NIP-98 ${r.reason}"))
        }
    }

    /** The `u` a NIP-98 [token] names, or null when it does not decode; the verifier then says why. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun claimedUrl(token: String): String? =
        try {
            val event = OptimizedJsonMapper.fromJson(Base64.decode(token.substring(Nip98AuthVerifier.SCHEME.length)).decodeToString())
            event.tags.firstNotNullOfOrNull(UrlTag::parse)
        } catch (_: Exception) {
            null
        }

    companion object {
        /** A `NOTICE` line: how a request is refused before its command runs (400, 413, 429, 503), by the handler or its host. */
        fun notice(reason: String) = NoticeMessage(reason).toJson()

        val DEFAULT_DEADLINE = 30_000.milliseconds

        /** The websocket's slow-consumer bound in the reference relays. */
        const val DEFAULT_MAX_QUEUED_FRAMES = 8192

        val DEFAULT_TAIL_GRACE = 5_000.milliseconds

        /** NIP-FE: a token is good for 60 seconds either side of its `created_at`. */
        const val TOKEN_WINDOW_SECONDS = 60L
    }
}
