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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a publisher must remember about one agent text stream to be allowed to
 * keep publishing it.
 *
 * @param nextSeq the lowest sequence value that has NEVER been handed out.
 *   Reserved ahead of use, so it may sit above the last record actually sent.
 * @param closed true once the stream was finished or aborted. A closed stream
 *   is closed for good — a later preview needs a fresh `stream_id`, which
 *   produces a new start payload and therefore a new key context.
 */
class AgentTextStreamSequenceState(
    val nextSeq: Long,
    val closed: Boolean,
)

/**
 * Durable sequence bookkeeping for agent text streams we publish.
 *
 * This is not caching. `features/agent-text-streams-quic.md` requires that a
 * publisher never restart or reuse a `seq` for one
 * [AgentTextStreamKeyContextV1] — "including after reconnect, retry, process
 * restart, or daemon resume" — and that a publisher which cannot prove which
 * value is next stop publishing. The reason is cryptographic rather than
 * clerical: `seq` is XORed into the ChaCha20-Poly1305 record nonce, and the
 * key is fixed for the stream, so a repeated `seq` repeats a (key, nonce) pair.
 * That leaks the XOR of the two plaintexts and forfeits authentication for
 * every record under that key.
 *
 * Keys are [AgentTextStreamKeyContextV1.encode] bytes, so a different stream
 * id, epoch, sender or start event is a different entry with its own sequence.
 *
 * Implementations SHOULD encrypt at rest for the same reason the message store
 * does: the key context contains the group id and the stream anchor.
 */
interface AgentTextStreamSequenceStore {
    suspend fun load(keyContext: ByteArray): AgentTextStreamSequenceState?

    suspend fun save(
        keyContext: ByteArray,
        state: AgentTextStreamSequenceState,
    )
}

/** Process-lifetime store. Suitable for tests and for a publisher that never restarts. */
class InMemoryAgentTextStreamSequenceStore : AgentTextStreamSequenceStore {
    private val states = mutableMapOf<String, AgentTextStreamSequenceState>()

    override suspend fun load(keyContext: ByteArray): AgentTextStreamSequenceState? = states[keyContext.toHexKey()]

    override suspend fun save(
        keyContext: ByteArray,
        state: AgentTextStreamSequenceState,
    ) {
        states[keyContext.toHexKey()] = state
    }
}

/**
 * Publishes the QUIC record side of one agent text stream.
 *
 * The publisher owns exactly one cryptographic session, the one the stream's
 * kind-1200 start payload authorized, and it owns the sequence discipline that
 * makes that session safe. Sequence values are reserved in the durable store
 * BEFORE a record is handed out, in windows of [RESERVATION_WINDOW] so the hot
 * path is not a write per record. A crash therefore skips the unused tail of a
 * window rather than replaying it: a gap is something the transport binding
 * already has to handle, and a repeat is a nonce collision.
 *
 * This produces records. It does not move them — the QUIC/WebTransport data
 * plane that carries them to a broker is a separate layer, and until that
 * exists a group's `send` role (`0xF2D2`) stays unadvertised, because
 * advertising a role we cannot serve is worse for the group than not
 * advertising it.
 */
class AgentTextStreamPublisher private constructor(
    private val crypto: AgentTextStreamCrypto,
    private val store: AgentTextStreamSequenceStore,
    private val maxPlaintextFrameLen: Long,
    val transcript: AgentTextStreamTranscriptV1,
    private var nextSeq: Long,
    private var reservedThrough: Long,
) {
    private val mutex = Mutex()
    private var closed = false

    private val keyContext = crypto.context.encode()

    /** The next sequence value this publisher would use. Exposed for diagnostics. */
    val peekNextSeq: Long get() = nextSeq

    val isClosed: Boolean get() = closed

    /**
     * Seal [plaintextFrame] as the next record in the stream.
     *
     * The frame is validated against the group's `max_plaintext_frame_len`
     * before a sequence value is claimed, so a frame the policy refuses does
     * not burn one — a burnt value would show up at every receiver as a
     * permanent gap in a stream that never had a record there.
     */
    suspend fun publish(
        recordType: Int,
        plaintextFrame: ByteArray,
    ): AgentTextStreamRecordV1 {
        require(plaintextFrame.size <= maxPlaintextFrameLen) {
            "agent text stream plaintext frame is larger than the group's limit"
        }
        return mutex.withLock {
            check(!closed) { "agent text stream ${crypto.context.streamId.toHexKey()} is closed" }
            val seq = claimNextSeqUnlocked()
            val sealed =
                crypto.seal(
                    AgentTextStreamRecordV1(
                        streamId = crypto.context.streamId,
                        seq = seq,
                        recordType = recordType,
                        frame = plaintextFrame,
                    ),
                    maxPlaintextFrameLen,
                )
            transcript.append(seq, recordType, plaintextFrame)
            sealed
        }
    }

    /** Publish a `FinalNotice` and close the stream. */
    suspend fun finish(notice: ByteArray = ByteArray(0)): AgentTextStreamRecordV1 {
        val record = publish(AgentTextStreamRecordV1.TYPE_FINAL_NOTICE, notice)
        close()
        return record
    }

    /** Publish an `Abort` and close the stream without producing durable text. */
    suspend fun abort(reason: ByteArray = ByteArray(0)): AgentTextStreamRecordV1 {
        val record = publish(AgentTextStreamRecordV1.TYPE_ABORT, reason)
        close()
        return record
    }

    /**
     * Close the stream permanently. Idempotent, and safe to call without ever
     * having published — a start payload whose preview never materialised is
     * still a spent key context.
     */
    suspend fun close() =
        mutex.withLock {
            if (closed) return@withLock
            closed = true
            store.save(keyContext, AgentTextStreamSequenceState(nextSeq = nextSeq, closed = true))
        }

    /** Caller holds [mutex]. Persists the window before returning a value from it. */
    private suspend fun claimNextSeqUnlocked(): Long {
        if (nextSeq > reservedThrough) {
            reservedThrough = nextSeq + RESERVATION_WINDOW - 1
            store.save(keyContext, AgentTextStreamSequenceState(nextSeq = reservedThrough + 1, closed = false))
        }
        return nextSeq++
    }

    companion object {
        /**
         * How many sequence values one durable write reserves. Larger means
         * fewer writes on a chatty stream and a longer gap after a crash;
         * neither is a correctness question, only a cost one.
         */
        const val RESERVATION_WINDOW = 64L

        /** The first sequence value of a stream, per the record spec. */
        const val FIRST_SEQ = 1L

        /**
         * Start publishing a stream whose kind-1200 start payload was just
         * emitted.
         *
         * Refuses when the store already knows this key context: that means
         * either a live publisher or a spent one, and starting over would
         * re-issue sequence values under a key that has already used them.
         */
        suspend fun open(
            crypto: AgentTextStreamCrypto,
            store: AgentTextStreamSequenceStore,
            maxPlaintextFrameLen: Long = AgentTextStreamQuicPolicyV1.MAX_PLAINTEXT_FRAME_LEN,
        ): AgentTextStreamPublisher {
            val keyContext = crypto.context.encode()
            val existing = store.load(keyContext)
            check(existing == null) {
                "agent text stream ${crypto.context.streamId.toHexKey()} already has publisher state — " +
                    "a new preview needs a fresh stream id and start payload"
            }
            return AgentTextStreamPublisher(
                crypto = crypto,
                store = store,
                maxPlaintextFrameLen = maxPlaintextFrameLen,
                transcript = AgentTextStreamTranscriptV1.start(crypto.context.streamId, crypto.context.startEventId),
                nextSeq = FIRST_SEQ,
                reservedThrough = FIRST_SEQ - 1,
            )
        }

        /**
         * Resume publishing after a restart, or null when this publisher may
         * not publish for this start payload any more.
         *
         * Null is the spec's required outcome in both cases it covers: nothing
         * retained (we cannot prove which value is next) and a closed stream.
         * The caller falls back to the authoritative final kind-9 message, and
         * a later preview attempt starts a new stream.
         *
         * The transcript resumes empty because the records it would have
         * covered were already sent; a resumed publisher's `stream-hash` is
         * therefore only meaningful when [transcriptHash] and [chunkCount] are
         * carried across the restart alongside the sequence state.
         */
        suspend fun resume(
            crypto: AgentTextStreamCrypto,
            store: AgentTextStreamSequenceStore,
            maxPlaintextFrameLen: Long = AgentTextStreamQuicPolicyV1.MAX_PLAINTEXT_FRAME_LEN,
            transcriptHash: ByteArray? = null,
            chunkCount: Long = 0,
        ): AgentTextStreamPublisher? {
            val keyContext = crypto.context.encode()
            val state = store.load(keyContext) ?: return null
            if (state.closed) return null
            return AgentTextStreamPublisher(
                crypto = crypto,
                store = store,
                maxPlaintextFrameLen = maxPlaintextFrameLen,
                transcript =
                    if (transcriptHash != null) {
                        AgentTextStreamTranscriptV1.resume(
                            crypto.context.streamId,
                            crypto.context.startEventId,
                            transcriptHash,
                            chunkCount,
                        )
                    } else {
                        AgentTextStreamTranscriptV1.start(crypto.context.streamId, crypto.context.startEventId)
                    },
                nextSeq = state.nextSeq,
                // Everything the previous process reserved is spent as far as
                // this one is concerned: it cannot tell which of those values
                // actually reached the wire, so it uses none of them.
                reservedThrough = state.nextSeq - 1,
            )
        }
    }
}
