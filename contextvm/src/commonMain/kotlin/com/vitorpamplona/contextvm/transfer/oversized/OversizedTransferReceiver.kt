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
package com.vitorpamplona.contextvm.transfer.oversized

import com.vitorpamplona.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.contextvm.transfer.ProgressToken
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Admission-control limits for one receiver.
 *
 * CEP-22 requires evaluating the declared `totalBytes` and `totalChunks` against
 * local policy **before** committing reassembly state, so a peer cannot make us
 * allocate by announcing a huge transfer.
 */
data class OversizedLimits(
    val maxTotalBytes: Long = 8L * 1024 * 1024,
    val maxTotalChunks: Long = 4_096,
    /**
     * How many out-of-order chunks may be buffered while waiting for earlier
     * ones. Relay delivery can reorder, so some buffering is required, but it
     * has to be bounded or a peer can park unbounded memory by withholding one
     * low-`progress` chunk.
     */
    val maxPendingChunks: Int = 256,
)

/** Outcome of feeding one frame to [OversizedTransferReceiver]. */
sealed interface OversizedProgressResult {
    /** Frame accepted; the transfer continues. Nothing is surfaced upward yet. */
    data object Continue : OversizedProgressResult

    /** The receiver should send an `accept` frame before chunks may flow. */
    data object SendAccept : OversizedProgressResult

    /** The transfer completed and validated. [message] is the reassembled payload. */
    data class Completed(
        val message: JsonRpcMessage,
        val raw: String,
    ) : OversizedProgressResult

    /** The transfer ended without producing a payload. */
    data class Aborted(
        val reason: String?,
    ) : OversizedProgressResult
}

/** Thrown when a transfer violates CEP-22. The transfer is terminal once this is raised. */
class OversizedTransferException(
    message: String,
) : IllegalStateException(message)

/**
 * Receives one CEP-22 bounded transfer, keyed by its `progressToken`.
 *
 * The rules this enforces are §6.5 `CVM-22-*` in
 * `quartz/plans/2026-09-17-cordn-interop.md`. Two are worth stating here because
 * they shape the design:
 *
 *  - **Nothing is surfaced upward before validation succeeds.** The reassembled
 *    string only becomes a message after the chunk count, byte length and digest
 *    all agree, so a partially delivered payload can never be mistaken for a
 *    complete one.
 *  - **The digest covers the exact serialized string.** Fragments are
 *    concatenated and encoded to UTF-8 once; the payload is never parsed and
 *    re-serialized on the way, because that would reorder keys or change
 *    whitespace and break the hash.
 *
 * @param requireAccept true in stateless bootstrap, where the sender must wait
 *   for `accept` before the first chunk. When peer support is already known the
 *   sender may go straight from `start` to `chunk`, and this is false.
 */
class OversizedTransferReceiver(
    val token: ProgressToken,
    private val limits: OversizedLimits = OversizedLimits(),
    private val requireAccept: Boolean = true,
) {
    private var started: OversizedFrame.Start? = null
    private var accepted = false
    private var terminal = false

    /** Fragments keyed by `progress`, so out-of-order arrivals assemble correctly. */
    private val chunks = mutableMapOf<Double, String>()

    val isTerminal get() = terminal

    fun accept(frame: OversizedFrame): OversizedProgressResult {
        if (terminal) throw OversizedTransferException("frame received after the transfer ended")
        if (frame.token != token) throw OversizedTransferException("frame belongs to another transfer")

        if (frame is OversizedFrame.Abort) {
            terminal = true
            return OversizedProgressResult.Aborted(frame.reason)
        }

        return when (frame) {
            is OversizedFrame.Start -> onStart(frame)
            is OversizedFrame.Accept -> OversizedProgressResult.Continue
            is OversizedFrame.Chunk -> onChunk(frame)
            is OversizedFrame.End -> onEnd(frame)
            is OversizedFrame.Abort -> error("handled above")
        }
    }

    private fun onStart(frame: OversizedFrame.Start): OversizedProgressResult {
        if (started != null) fail("a second start frame arrived for this transfer")

        // Admission control runs before any state is committed.
        if (frame.totalBytes < 0) fail("totalBytes must not be negative")
        if (frame.totalChunks < 0) fail("totalChunks must not be negative")
        if (frame.totalBytes > limits.maxTotalBytes) {
            fail("declared totalBytes ${frame.totalBytes} exceeds the limit ${limits.maxTotalBytes}")
        }
        if (frame.totalChunks > limits.maxTotalChunks) {
            fail("declared totalChunks ${frame.totalChunks} exceeds the limit ${limits.maxTotalChunks}")
        }
        if (!frame.digest.startsWith(OversizedFrame.DIGEST_PREFIX_SHA256)) {
            fail("unsupported digest algorithm: ${frame.digest.substringBefore(':')}")
        }

        started = frame
        return if (requireAccept) OversizedProgressResult.SendAccept else OversizedProgressResult.Continue
    }

    private fun onChunk(frame: OversizedFrame.Chunk): OversizedProgressResult {
        val start = started ?: fail("chunk arrived before start")

        if (requireAccept && !accepted) {
            // In stateless bootstrap the sender must not transmit before the
            // receiver confirms. Seeing a chunk first means the peer skipped a
            // step the profile requires, so the transfer cannot be trusted.
            fail("chunk arrived before accept in a bootstrap transfer")
        }
        if (chunks.size >= limits.maxPendingChunks) {
            fail("buffered chunk count exceeds the limit ${limits.maxPendingChunks}")
        }
        if (chunks.size.toLong() >= start.totalChunks) {
            fail("more chunks arrived than the declared totalChunks ${start.totalChunks}")
        }
        // `progress` is the assembly index, so a chunk must sort after `start`.
        // Arrival order is NOT checked: relays reorder, and the CEP explicitly
        // says progress is not a guarantee of arrival order. Out-of-order
        // frames are buffered and assembled by progress at `end`.
        if (frame.progress <= start.progress) {
            fail("chunk progress ${frame.progress} does not follow start at ${start.progress}")
        }
        if (chunks.put(frame.progress, frame.data) != null) {
            fail("duplicate chunk at progress ${frame.progress}")
        }
        return OversizedProgressResult.Continue
    }

    private fun onEnd(frame: OversizedFrame.End): OversizedProgressResult {
        val start = started ?: fail("end arrived before start")

        val highestChunk = chunks.keys.maxOrNull()
        if (highestChunk != null && frame.progress <= highestChunk) {
            fail("end progress ${frame.progress} does not follow the last chunk at $highestChunk")
        }
        if (frame.progress <= start.progress) {
            fail("end progress ${frame.progress} does not follow start at ${start.progress}")
        }

        if (chunks.size.toLong() != start.totalChunks) {
            fail("expected ${start.totalChunks} chunks but assembled ${chunks.size}")
        }

        // `progress` is the canonical assembly index, not arrival order.
        val raw = chunks.entries.sortedBy { it.key }.joinToString("") { it.value }
        val bytes = raw.encodeToByteArray()

        if (bytes.size.toLong() != start.totalBytes) {
            fail("reassembled ${bytes.size} bytes but start declared ${start.totalBytes}")
        }

        val actual = OversizedFrame.DIGEST_PREFIX_SHA256 + sha256(bytes).toHexKey()
        if (!actual.equals(start.digest, ignoreCase = true)) {
            fail("digest mismatch: expected ${start.digest} but reassembled $actual")
        }

        terminal = true
        return OversizedProgressResult.Completed(JsonRpcCodec.decode(raw), raw)
    }

    /** Records that we sent `accept`, unblocking chunk frames. */
    fun markAccepted() {
        accepted = true
    }

    private fun fail(reason: String): Nothing {
        terminal = true
        throw OversizedTransferException(reason)
    }
}
