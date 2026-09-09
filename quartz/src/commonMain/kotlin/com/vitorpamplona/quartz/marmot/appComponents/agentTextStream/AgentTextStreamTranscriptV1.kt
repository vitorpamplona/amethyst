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
package com.vitorpamplona.quartz.marmot.appComponents.agentTextStream

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider

/**
 * The rolling hash a stream's final kind-9 chat publishes as `stream-hash`,
 * alongside the `stream-chunks` count.
 *
 * ```
 * h0   = SHA-256("marmot agent text stream transcript v1"
 *                || len(stream_id) || stream_id
 *                || len(start_event_id) || start_event_id)
 * h_n  = SHA-256(h_{n-1} || seq || record_type || plaintext_frame)
 * ```
 *
 * Every receiver folds the same records in the same order, so a receiver whose
 * hash disagrees with the publisher's saw a different stream — a dropped,
 * reordered or injected record — even though each individual record opened.
 */
class AgentTextStreamTranscriptV1 private constructor(
    val streamId: ByteArray,
    val startEventId: ByteArray,
    private var rollingHash: ByteArray,
    private var chunks: Long,
) {
    val hash: ByteArray get() = rollingHash.copyOf()
    val chunkCount: Long get() = chunks

    fun append(
        seq: Long,
        recordType: Int,
        plaintextFrame: ByteArray,
    ) {
        val input = ArrayList<Byte>(rollingHash.size + 9 + plaintextFrame.size)
        for (b in rollingHash) input.add(b)
        for (i in 7 downTo 0) input.add((seq shr (8 * i)).toByte())
        input.add(recordType.toByte())
        for (b in plaintextFrame) input.add(b)
        rollingHash = MlsCryptoProvider.hash(input.toByteArray())
        chunks += 1
    }

    fun append(record: AgentTextStreamRecordV1) = append(record.seq, record.recordType, record.frame)

    companion object {
        val CONTEXT = "marmot agent text stream transcript v1".encodeToByteArray()

        fun start(
            streamId: ByteArray,
            startEventId: ByteArray,
        ): AgentTextStreamTranscriptV1 {
            val input = ArrayList<Byte>()
            for (b in CONTEXT) input.add(b)
            input.addLengthPrefixed(streamId)
            input.addLengthPrefixed(startEventId)
            return AgentTextStreamTranscriptV1(
                streamId = streamId,
                startEventId = startEventId,
                rollingHash = MlsCryptoProvider.hash(input.toByteArray()),
                chunks = 0,
            )
        }

        /**
         * Resume from durable state. The caller must have bound [hash] and
         * [chunkCount] to this same stream and start event — nothing here can
         * check that, and resuming against a different stream silently forks
         * the transcript.
         */
        fun resume(
            streamId: ByteArray,
            startEventId: ByteArray,
            hash: ByteArray,
            chunkCount: Long,
        ): AgentTextStreamTranscriptV1 {
            require(hash.size == 32) { "agent text stream transcript hash must be 32 bytes" }
            require(chunkCount >= 0) { "agent text stream chunk count cannot be negative" }
            return AgentTextStreamTranscriptV1(streamId, startEventId, hash.copyOf(), chunkCount)
        }
    }
}
