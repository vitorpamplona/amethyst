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
package com.vitorpamplona.quartz.nip01Core.core

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.DiffEntries
import com.vitorpamplona.quartz.nip01Core.diff.DiffEntry
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.EventKSerializer
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.Serializable

@Immutable
@Serializable(with = EventKSerializer::class)
open class Event(
    val id: HexKey,
    val pubKey: HexKey,
    val createdAt: Long,
    val kind: Kind,
    val tags: TagArray,
    val content: String,
    val sig: HexKey,
) : IEvent,
    OptimizedSerializable {
    /**
     * Set this to true if the .content is encrypted or encoded in a
     * way that it should not be indexed for local search.
     */
    open fun isContentEncoded() = false

    /**
     * Returns true when this event is intended to notify [userHex].
     *
     * The default delegates to
     * [com.vitorpamplona.quartz.nip01Core.tags.people.PTag.isNotifying],
     * i.e. "any lowercase `p` tag addresses the user" — the convention for
     * most kinds that address a single recipient or a set of mentions
     * (NIP-01 mentions, NIP-04/17 DMs, NIP-25 reactions, NIP-28 chat
     * messages, NIP-34 git issues/patches, NIP-57 zap receipts, NIP-68
     * pictures, NIP-71 videos, NIP-84 highlights, NIP-AC calls, chess,
     * wiki/long-form/poll mentions).
     *
     * Subclasses override when the NIP defines additional notification tags
     * — e.g. NIP-22 comments use uppercase `P` for the root author in
     * addition to lowercase `p` for the direct-reply author.
     */
    open fun notifies(userHex: HexKey): Boolean = PTag.isNotifying(tags, userHex)

    /**
     * Turns one of this event's tags into a [DiffEntry] for [diffFrom], or null to leave the
     * tag out of diffs. Subclasses override it for tags whose meaning is specific to their
     * kind and fall back to `super` for the common ones.
     */
    open fun diffEntry(tag: Array<String>): DiffEntry? = DiffEntries.fromTag(tag)

    /**
     * Everything this event holds, as [DiffEntry]s. Defaults to its tags; subclasses whose
     * content is structured (kind:0 profiles) add the content's fields here too.
     */
    open fun diffEntries(): List<DiffEntry> = tags.mapNotNull { diffEntry(it) }

    /**
     * How the content changed since [older], beyond what [diffEntries] already covers.
     * Subclasses whose content is already expressed as entries, or is meaningless, return
     * [ContentChange.NONE].
     */
    open fun diffContent(older: Event): ContentChange = ContentChange.between(older.content, content)

    /**
     * Compares this event with an [older] version of itself (same kind and author) and
     * reports what was removed, added and changed, in the typed [DiffEntry] vocabulary so
     * it can be shown to the user. Null when [older] isn't a version of the same event kind
     * and author. Callers comparing addressables must pass versions of the same address.
     */
    fun diffFrom(older: Event): EventDiff? {
        if (older.kind != kind || older.pubKey != pubKey) return null
        return EventDiff.compute(kind, older.diffEntries(), diffEntries(), diffContent(older), isContentEncoded())
    }

    fun toJson(): String = OptimizedJsonMapper.toJson(this)

    companion object {
        fun fromJson(json: String): Event = OptimizedJsonMapper.fromJson(json)

        fun fromJsonOrNull(json: String) =
            try {
                fromJson(json)
            } catch (e: Exception) {
                Log.w("Event", "Unable to parse event JSON: $json", e)
                null
            }

        fun build(
            kind: Int,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<Event>.() -> Unit = {},
        ) = eventTemplate(kind, content, createdAt, initializer)
    }
}
