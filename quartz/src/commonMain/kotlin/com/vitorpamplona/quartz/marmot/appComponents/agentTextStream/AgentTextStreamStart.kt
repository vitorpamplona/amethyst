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

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The kind-1200 event that anchors one agent text stream, read off an inner
 * app payload's tags.
 *
 * A stream never carries its own key material: the anchoring event's id is
 * part of [AgentTextStreamKeyContextV1], so a receiver can only derive record
 * keys for a stream it has already seen announced inside the group. That is
 * also why the anchor is an ordinary in-group app payload rather than
 * something the broker hands out — the broker relays ciphertext and learns
 * nothing.
 *
 * ```
 * ["stream", <32-byte stream id, hex>]
 * ["stream-type", "text"]
 * ["final-kind", "9"]
 * ["route", "quic"]                       (optional; "quic" when absent)
 * ["parent", <prompt event id>]           (optional)
 * ["broker", <candidate URL>]             (repeatable)
 * ```
 *
 * `stream`, `stream-type` and `final-kind` are owned by the feature;
 * `route` and `broker` belong to the transport binding, and a client that
 * implements `receive` but not raw QUIC may ignore them entirely and wait for
 * the final message.
 *
 * The matching END of a stream is an ordinary kind-9 chat carrying
 * [STREAM_TAG], [STREAM_HASH_TAG] and [STREAM_CHUNKS_TAG]; see
 * [AgentTextStreamFinal].
 */
class AgentTextStreamStart(
    val streamId: HexKey,
    val route: String,
    val brokerCandidates: List<String>,
    val streamType: String = TYPE_TEXT,
    val finalKind: Int = FINAL_KIND_TEXT,
    val parentEventId: HexKey? = null,
) {
    val isQuicRoute: Boolean get() = route == ROUTE_QUIC

    /**
     * True when this start is one we know how to render live. A `stream-type`
     * we don't implement is not an error — the final message still arrives —
     * so callers skip the preview rather than rejecting the payload.
     */
    val isTextProfile: Boolean get() = streamType == TYPE_TEXT && finalKind == FINAL_KIND_TEXT

    /**
     * "Receivers MUST ignore a final payload whose kind does not match the
     * start payload's `final-kind`."
     */
    fun acceptsFinalKind(kind: Int): Boolean = kind == finalKind

    companion object {
        const val KIND = 1200

        const val STREAM_TAG = "stream"
        const val STREAM_TYPE_TAG = "stream-type"
        const val FINAL_KIND_TAG = "final-kind"
        const val ROUTE_TAG = "route"
        const val PARENT_TAG = "parent"
        const val BROKER_TAG = "broker"

        const val ROUTE_QUIC = "quic"

        /** The first — and so far only — stream type. */
        const val TYPE_TEXT = "text"

        /** For `stream-type=text`, `final-kind` MUST be 9. */
        const val FINAL_KIND_TEXT = 9

        fun tags(
            streamId: HexKey,
            brokerCandidates: List<String>,
            route: String = ROUTE_QUIC,
            streamType: String = TYPE_TEXT,
            finalKind: Int = FINAL_KIND_TEXT,
            parentEventId: HexKey? = null,
        ): Array<Array<String>> =
            buildList {
                add(arrayOf(STREAM_TAG, streamId))
                add(arrayOf(STREAM_TYPE_TAG, streamType))
                add(arrayOf(FINAL_KIND_TAG, finalKind.toString()))
                add(arrayOf(ROUTE_TAG, route))
                if (parentEventId != null) add(arrayOf(PARENT_TAG, parentEventId))
                brokerCandidates.forEach { add(arrayOf(BROKER_TAG, it)) }
            }.toTypedArray()

        /**
         * Read the start view from a kind-1200 payload's tags, or null when
         * this is not a stream start.
         *
         * A missing `stream` tag means the payload cannot anchor anything, so
         * it is rejected rather than defaulted. The rest default to the first
         * text profile: an older or terser sender that omits `stream-type` /
         * `final-kind` / `route` still describes exactly that profile, and
         * refusing it would drop a stream we can render.
         */
        fun fromTags(
            kind: Int,
            tags: Array<Array<String>>,
        ): AgentTextStreamStart? {
            if (kind != KIND) return null
            val streamId = tags.firstOrNull { it.size >= 2 && it[0] == STREAM_TAG }?.get(1) ?: return null
            val route = tags.firstOrNull { it.size >= 2 && it[0] == ROUTE_TAG }?.get(1) ?: ROUTE_QUIC
            val streamType = tags.firstOrNull { it.size >= 2 && it[0] == STREAM_TYPE_TAG }?.get(1) ?: TYPE_TEXT
            val finalKind =
                tags.firstOrNull { it.size >= 2 && it[0] == FINAL_KIND_TAG }?.get(1)?.toIntOrNull()
                    ?: FINAL_KIND_TEXT
            val parent = tags.firstOrNull { it.size >= 2 && it[0] == PARENT_TAG }?.get(1)
            val brokers = tags.filter { it.size >= 2 && it[0] == BROKER_TAG }.map { it[1] }
            return AgentTextStreamStart(streamId, route, brokers, streamType, finalKind, parent)
        }
    }
}

/**
 * The `stream` / `stream-hash` / `stream-chunks` tags a kind-9 chat carries to
 * close a stream out.
 *
 * The final chat is the durable message; the stream was a live preview of the
 * text it now contains. A receiver that folded every record compares its own
 * [AgentTextStreamTranscriptV1] against these two values: agreement means it
 * saw exactly the stream the publisher sent, and disagreement means records
 * were dropped, reordered, or injected — even though each one opened.
 */
class AgentTextStreamFinal(
    val streamId: HexKey,
    val transcriptHash: HexKey,
    val chunkCount: Long,
) {
    companion object {
        const val STREAM_HASH_TAG = "stream-hash"
        const val STREAM_CHUNKS_TAG = "stream-chunks"

        fun tags(
            streamId: HexKey,
            transcriptHash: HexKey,
            chunkCount: Long,
        ): Array<Array<String>> =
            arrayOf(
                arrayOf(AgentTextStreamStart.STREAM_TAG, streamId),
                arrayOf(STREAM_HASH_TAG, transcriptHash),
                arrayOf(STREAM_CHUNKS_TAG, chunkCount.toString()),
            )

        fun fromTags(tags: Array<Array<String>>): AgentTextStreamFinal? {
            val streamId = tags.firstOrNull { it.size >= 2 && it[0] == AgentTextStreamStart.STREAM_TAG }?.get(1) ?: return null
            val hash = tags.firstOrNull { it.size >= 2 && it[0] == STREAM_HASH_TAG }?.get(1) ?: return null
            val chunks = tags.firstOrNull { it.size >= 2 && it[0] == STREAM_CHUNKS_TAG }?.get(1)?.toLongOrNull() ?: return null
            return AgentTextStreamFinal(streamId, hash, chunks)
        }
    }
}
