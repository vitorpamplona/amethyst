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
package com.vitorpamplona.quartz.contextvm.cep22OversizedTransfer

import com.vitorpamplona.quartz.contextvm.transfer.ProgressToken
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Splits an oversized serialized JSON-RPC message into CEP-22 frames.
 *
 * Two subtleties the chunking has to respect:
 *
 *  - **Relay limits apply to the whole serialized event**, not just `content`,
 *    and roughly 64 KiB is the practical ceiling. [DEFAULT_CHUNK_CHARS] leaves
 *    generous room for the JSON-RPC envelope, the event's tags and signature,
 *    and any gift wrap around it.
 *  - **A chunk boundary must not split a surrogate pair.** Fragments are
 *    concatenated as text and only then encoded to UTF-8 for the digest, so
 *    cutting a pair in half would corrupt the reassembled bytes even though each
 *    fragment still looks like a valid JSON string.
 */
class OversizedTransferSender(
    private val chunkChars: Int = DEFAULT_CHUNK_CHARS,
) {
    init {
        require(chunkChars > 1) { "chunkChars must leave room for a surrogate pair" }
    }

    /**
     * Frames [serialized] for [token], starting at `progress` 1.
     *
     * The returned list is `start`, then the chunks, then `end`. When the peer's
     * support is not yet known the caller must withhold everything after `start`
     * until the peer's `accept` arrives; the frames themselves are unchanged.
     */
    fun frame(
        token: ProgressToken,
        serialized: String,
    ): List<OversizedFrame> {
        val pieces = split(serialized)
        val bytes = serialized.encodeToByteArray()
        val digest = OversizedFrame.DIGEST_PREFIX_SHA256 + sha256(bytes).toHexKey()

        var progress = 1.0
        val frames = mutableListOf<OversizedFrame>()
        frames +=
            OversizedFrame.start(
                token = token,
                progress = progress,
                digest = digest,
                totalBytes = bytes.size.toLong(),
                totalChunks = pieces.size.toLong(),
            )

        pieces.forEach { piece ->
            progress += 1.0
            frames += OversizedFrame.chunk(token, progress, piece)
        }

        frames += OversizedFrame.end(token, progress + 1.0)
        return frames
    }

    /** True when [serialized] is large enough to be worth fragmenting. */
    fun shouldFragment(serialized: String) = serialized.length > chunkChars

    private fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        val pieces = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            var end = minOf(index + chunkChars, text.length)
            // Never cut between a high and low surrogate: the two halves would
            // each survive JSON encoding but rejoin into a different codepoint.
            if (end < text.length && text[end - 1].isHighSurrogate()) end -= 1
            pieces += text.substring(index, end)
            index = end
        }
        return pieces
    }

    companion object {
        /**
         * Conservative fragment size in UTF-16 characters.
         *
         * Well below the ~64 KiB practical relay ceiling because the fragment is
         * only part of what ships: it is wrapped in a progress notification, then
         * an event with tags and a signature, then possibly a gift wrap.
         */
        const val DEFAULT_CHUNK_CHARS = 16 * 1024
    }
}
