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
package com.vitorpamplona.quartz.buzz.stream

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.stream.tags.ExpectedRevisionTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz channel canvas (shared markdown document), `kind:40100`.
 *
 * Mirrors `build_set_canvas` in Buzz's `buzz-sdk/src/builders.rs`: a single `h`
 * channel tag with the markdown document in [content], plus an optional
 * `["expected-revision", …]` compare-and-swap precondition ([ExpectedRevisionTag]).
 *
 * The live canvas is the newest revision under `created_at DESC, id ASC` (a same-second tie goes
 * to the smallest id — see [isNewerHeadThan]). A write that edits a loaded head stamps
 * `created_at = max(now, head.created_at + 1)` ([writeCreatedAt]) so it always sorts strictly
 * ahead of the head it asserts.
 */
@Immutable
class CanvasEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent,
    EventHintProvider {
    // The `expected-revision` head id has no relay slot; the `none` sentinel is not a reference.
    override fun eventHints(): List<EventIdHint> = emptyList()

    override fun linkedEventIds(): List<HexKey> = listOfNotNull(expectedRevisionId())

    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    fun channel() = tags.channel()

    /** The `expected-revision` precondition: [ExpectedRevisionTag.NONE], a head id, or null for an unconditional write. */
    fun expectedRevision() = tags.firstNotNullOfOrNull(ExpectedRevisionTag::parse)

    /**
     * The canvas head id the `expected-revision` precondition names; null for the
     * [ExpectedRevisionTag.NONE] sentinel, an unconditional write, or a malformed tag.
     */
    fun expectedRevisionId(): HexKey? = tags.firstNotNullOfOrNull(ExpectedRevisionTag::parseEventId)

    /**
     * True when this revision displaces [other] as the channel's live canvas under the relay's
     * `created_at DESC, id ASC` read order: a newer `created_at` wins, and a same-second tie goes to
     * the lexicographically smallest id (not to whichever arrived last).
     */
    fun isNewerHeadThan(other: Event?): Boolean = isNewerHead(createdAt, id, other?.createdAt, other?.id)

    companion object {
        const val KIND = 40100

        /**
         * The furthest a canvas head may sit in the future before a writer refuses to ratchet past
         * it (seconds). Mirrors `CANVAS_MAX_FUTURE_SKEW_SECS` in `buzz-sdk/src/builders.rs`: stamping
         * `max(now, head + 1)` against a poisoned far-future head would drag every later write along.
         */
        const val MAX_HEAD_FUTURE_SKEW_SECS = 60L

        /**
         * How far in the future the relay accepts a canvas `created_at` (seconds) —
         * `CANVAS_MAX_INGEST_FUTURE_SECS` in `buzz-relay/src/handlers/ingest.rs`. A write stamped
         * by [writeCreatedAt] never exceeds `now + MAX_HEAD_FUTURE_SKEW_SECS + 1`, well inside it.
         */
        const val RELAY_MAX_FUTURE_SECS = 300L

        /** OK-message prefix the relay uses when an `expected-revision` precondition fails. */
        const val CONFLICT_PREFIX = "conflict:"

        fun build(
            channelId: String,
            markdown: String,
            createdAt: Long = TimeUtils.now(),
            expectedRevision: String? = null,
            initializer: TagArrayBuilder<CanvasEvent>.() -> Unit = {},
        ) = eventTemplate<CanvasEvent>(KIND, markdown, createdAt) {
            channel(channelId)
            expectedRevision?.let { addUnique(ExpectedRevisionTag.assemble(it)) }
            initializer()
        }

        /**
         * Writer discipline for a canvas write composed against a head stamped [headCreatedAt]:
         * `max(now, headCreatedAt + 1)`, so the write sorts strictly ahead of that head. Returns
         * null — refuse the write — when the head sits more than [MAX_HEAD_FUTURE_SKEW_SECS] in the
         * future. With no head ([headCreatedAt] null) it is just [now].
         *
         * Mirrors `canvas_write_created_at` in `buzz-sdk/src/builders.rs`.
         */
        fun writeCreatedAt(
            headCreatedAt: Long?,
            now: Long = TimeUtils.now(),
        ): Long? {
            if (headCreatedAt == null) return now
            if (headCreatedAt > now + MAX_HEAD_FUTURE_SKEW_SECS) return null
            return maxOf(now, headCreatedAt + 1)
        }

        /**
         * `created_at DESC, id ASC` head selection: true when revision ([createdAt], [id]) displaces
         * the current head ([headCreatedAt], [headId]). No head at all is always displaced; the same
         * revision never displaces itself.
         */
        fun isNewerHead(
            createdAt: Long,
            id: String,
            headCreatedAt: Long?,
            headId: String?,
        ): Boolean {
            if (headCreatedAt == null || headId == null) return true
            if (createdAt != headCreatedAt) return createdAt > headCreatedAt
            return id < headId
        }

        /** True when a relay OK message reports a failed `expected-revision` compare-and-swap. */
        fun isConflict(okMessage: String?): Boolean = okMessage?.startsWith(CONFLICT_PREFIX) == true
    }
}
