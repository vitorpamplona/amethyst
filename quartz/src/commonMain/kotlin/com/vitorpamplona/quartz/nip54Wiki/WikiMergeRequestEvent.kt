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
package com.vitorpamplona.quartz.nip54Wiki

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A request to merge a forked wiki article back into the original (kind 818) — NIP-54, Appendix 1.
 *
 * - `a` — the article to modify, i.e. the merge target.
 * - `p` — the pubkey being asked to merge.
 * - `e` **marked** — the version to merge.
 * - `e` unmarked — the version the change was based on (optional).
 * - `content` — an optional explanation.
 *
 * **The marker is where the spec and the implementations disagree.** NIP-54 writes the
 * merge-source marker as `source`; the client publishing these in the wild
 * (`silberengel/jumble`) writes `fork`. Reading only one of them silently turns half the merge
 * requests on the network into "a request with nothing to merge", so [mergeSource] accepts both.
 */
@Immutable
class WikiMergeRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    /** The article this asks to change. */
    fun targetArticle(): Address? = tags.firstNotNullOfOrNull(ATag::parseAddress)

    /** The pubkey being asked to merge. */
    fun destinationAuthor(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)

    /**
     * The id of the version to be merged — the fork. Accepts either marker; see the class doc.
     *
     * The marker slot is read directly rather than through [MarkedETag]'s enum: `source` is
     * NIP-54 vocabulary, and adding it to the NIP-10 threading markers would imply it takes part
     * in threading, which it does not.
     */
    fun mergeSource(): HexKey? =
        tags.firstNotNullOfOrNull { tag ->
            if (tag.size > MARKER_SLOT && tag[0] == ETag.TAG_NAME && tag[1].isNotEmpty() && tag[MARKER_SLOT] in MERGE_SOURCE_MARKERS) {
                tag[1]
            } else {
                null
            }
        }

    /**
     * The version the change was based on: the unmarked `e`. Falls back to the first `e` that is
     * not the merge source, because publishers are inconsistent about marking this one at all.
     */
    fun baseVersion(): HexKey? {
        val source = mergeSource()
        return tags.firstNotNullOfOrNull { tag ->
            if (tag.size > 1 && tag[0] == ETag.TAG_NAME && tag[1].isNotEmpty() && tag[1] != source &&
                (tag.size <= MARKER_SLOT || tag[MARKER_SLOT].isEmpty())
            ) {
                tag[1]
            } else {
                null
            }
        }
    }

    /** A merge request with no source has nothing to merge and cannot be acted on. */
    fun hasMergeSource() = mergeSource() != null

    companion object {
        const val KIND = 818

        /** `source` is NIP-54's word for it; `fork` is what the publishing clients emit. */
        const val SPEC_MERGE_SOURCE_MARKER = "source"
        val MERGE_SOURCE_MARKERS = setOf(SPEC_MERGE_SOURCE_MARKER, "fork")

        /** NIP-10 puts the marker in the fourth slot: `["e", <id>, <relay>, <marker>]`. */
        const val MARKER_SLOT = 3

        fun build(
            targetArticle: Address,
            destinationAuthor: HexKey,
            mergeSourceId: HexKey,
            explanation: String = "",
            baseVersionId: HexKey? = null,
            relay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WikiMergeRequestEvent>.() -> Unit = {},
        ): EventTemplate<WikiMergeRequestEvent> =
            eventTemplate(KIND, explanation, createdAt) {
                add(ATag.assemble(targetArticle, relay))
                add(PTag.assemble(destinationAuthor, relay))
                baseVersionId?.let { add(ETag.assemble(it, relay, null)) }
                // Written with the spec's marker; our reader accepts the other one too.
                add(arrayOf(ETag.TAG_NAME, mergeSourceId, relay?.url ?: "", SPEC_MERGE_SOURCE_MARKER))

                initializer()
            }
    }
}
