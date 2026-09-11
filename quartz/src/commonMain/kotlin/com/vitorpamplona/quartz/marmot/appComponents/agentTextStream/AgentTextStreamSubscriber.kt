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

/** What the subscriber did with one inbound record. */
enum class RecordOutcome {
    /** Folded into the transcript and applied to the preview. */
    Accepted,

    /** At or below the high-water mark. Discarded silently; not stream-fatal. */
    Replay,

    /** Ahead of the high-water mark: records are missing and were not folded. */
    Gap,

    /** Failed its AEAD. Not ours, or altered in flight. */
    Undecryptable,

    /** Belongs to a different stream than the one being rendered. */
    WrongStream,
}

/** How much a renderer may claim about the preview it is showing. */
enum class PreviewStatus {
    /** Every record so far arrived in order and opened. */
    LIVE,

    /** A gap could not be backfilled, so the transcript hash can never complete. */
    UNVERIFIABLE,

    /** The publisher aborted; there is no durable text coming from this preview. */
    ABORTED,

    /** The publisher signalled that the final MLS message is on its way. */
    FINISHED,
}

/**
 * The receiving half of one agent text stream preview.
 *
 * Everything here is provisional. The authority is the final kind-9 MLS
 * message, and [matchesFinal] is the only thing that turns "we rendered
 * something" into "we rendered what the publisher sent" — every record can
 * open individually and the stream still be wrong, if one was dropped,
 * reordered or injected.
 *
 * A renderer must show this text as visibly distinct from confirmed content
 * until that check passes.
 */
class AgentTextStreamSubscriber(
    private val crypto: AgentTextStreamCrypto,
) {
    private val builder = StringBuilder()

    /** The stream's rolling transcript over every accepted record. */
    val transcript: AgentTextStreamTranscriptV1 =
        AgentTextStreamTranscriptV1.start(crypto.context.streamId, crypto.context.startEventId)

    /** Highest `seq` folded so far; the next accepted record is this plus one. */
    var highWaterMark: Long = 0
        private set

    var status: PreviewStatus = PreviewStatus.LIVE
        private set

    /** Provisional answer text: `TextDelta` appended, `Checkpoint` replacing. */
    val previewText: String get() = builder.toString()

    /** Latest `Status` label, for local UI chrome only. Never part of the answer. */
    var latestStatus: String? = null
        private set

    /** Latest `ProgressDelta`, for live non-chat progress chrome only. */
    var latestProgress: String? = null
        private set

    /**
     * True once a gap was seen and not yet backfilled. The transcript is
     * incomplete, so it can never be compared against the final message.
     */
    private var sawUnbackfilledGap = false

    /**
     * Fold one record in, judged against the high-water mark.
     *
     * Order is not negotiable: `seq` is XORed into the record nonce, so an
     * out-of-order fold would also produce a transcript nobody else computes.
     * Hence a record ahead of the mark is reported rather than applied — the
     * caller backfills it from a replay source, or lives with an unverifiable
     * preview and waits for the final message.
     */
    fun accept(record: AgentTextStreamRecordV1): RecordOutcome {
        if (!record.streamId.contentEquals(crypto.context.streamId)) return RecordOutcome.WrongStream

        // "A record whose seq is at or below the high-water mark — for example,
        // a record a broker replays on reconnect — MUST be discarded silently
        // without affecting the stream."
        if (record.seq <= highWaterMark) return RecordOutcome.Replay

        if (record.seq > highWaterMark + 1) {
            sawUnbackfilledGap = true
            if (status == PreviewStatus.LIVE) status = PreviewStatus.UNVERIFIABLE
            return RecordOutcome.Gap
        }

        // Opening before advancing anything: a record that fails its AEAD must
        // leave the stream exactly as it was, or a single injected frame could
        // burn the sequence value the real record needs.
        val opened = crypto.openOrNull(record) ?: return RecordOutcome.Undecryptable

        highWaterMark = record.seq
        transcript.append(opened)

        when (opened.recordType) {
            AgentTextStreamRecordV1.TYPE_TEXT_DELTA -> builder.append(opened.frame.decodeToString())

            // "Receivers that support checkpoints replace the provisional
            // preview text with the checkpoint plaintext, then continue
            // applying later TextDelta records."
            AgentTextStreamRecordV1.TYPE_CHECKPOINT -> {
                builder.setLength(0)
                builder.append(opened.frame.decodeToString())
            }

            AgentTextStreamRecordV1.TYPE_STATUS -> latestStatus = opened.frame.decodeToString()

            AgentTextStreamRecordV1.TYPE_PROGRESS_DELTA -> latestProgress = opened.frame.decodeToString()

            AgentTextStreamRecordV1.TYPE_ABORT -> {
                // "Receivers remove or mark the preview as cancelled and wait
                // for later durable events." Nothing here becomes chat text.
                builder.setLength(0)
                status = PreviewStatus.ABORTED
            }

            AgentTextStreamRecordV1.TYPE_FINAL_NOTICE ->
                if (status == PreviewStatus.LIVE) status = PreviewStatus.FINISHED

            // An unknown record type still counts toward the transcript — the
            // hash covers the stream the publisher sent, not the subset we
            // happen to understand — but contributes nothing to the preview.
            else -> Unit
        }

        // A backfill that closes the last gap makes the preview whole again.
        if (sawUnbackfilledGap && status == PreviewStatus.UNVERIFIABLE) {
            sawUnbackfilledGap = false
            status = PreviewStatus.LIVE
        }

        return RecordOutcome.Accepted
    }

    /**
     * Does what we folded match what the final kind-9 says the publisher sent?
     *
     * A disagreement means records were dropped, reordered or injected even
     * though each one opened, so the preview must be discarded in favour of
     * the final message. A preview already known to be incomplete answers
     * false without comparing: it cannot have folded the stream, whatever its
     * running hash happens to be.
     */
    fun matchesFinal(
        transcriptHash: ByteArray,
        chunkCount: Long,
    ): Boolean {
        if (sawUnbackfilledGap) return false
        if (status == PreviewStatus.ABORTED) return false
        return transcript.chunkCount == chunkCount && transcript.hash.contentEquals(transcriptHash)
    }
}
