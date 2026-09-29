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
package com.vitorpamplona.quartz.experimental.zapPolls

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.zapPolls.tags.ClosedAtTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.ConsensusThresholdTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.MaximumTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.MinimumTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.contentMentions
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ZapPollEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider,
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    /**
     * Kind 1's reading (NIP-10): a zap poll is a threaded note plus `poll_option` tags. Marked
     * `e` tags name the root and the parent (a lone `root` is also the parent); without markers
     * the deprecated positional scheme applies (first `e` the root, last the parent, the ones
     * between mentions). A `p` is the parent's author when it matches the author slot of the
     * parent's `e`, else a mention. Votes are zaps, not links of the poll.
     */
    override fun links(): List<Link> {
        val thread = arrayOfNulls<MarkedETag>(tags.size)
        var markedRoot = -1
        var markedReply = -1
        var firstUnmarked = -1
        var lastUnmarked = -1
        tags.forEachIndexed { i, tag ->
            val e = MarkedETag.parseAllThreadTags(tag) ?: return@forEachIndexed
            thread[i] = e
            when (e.marker) {
                MarkedETag.MARKER.ROOT -> if (markedRoot < 0) markedRoot = i
                MarkedETag.MARKER.REPLY -> markedReply = i
                null -> {
                    if (firstUnmarked < 0) firstUnmarked = i
                    lastUnmarked = i
                }
                else -> Unit
            }
        }
        val marked = markedRoot >= 0 || markedReply >= 0
        val root = if (marked) markedRoot else firstUnmarked
        val parent =
            when {
                !marked -> lastUnmarked
                markedReply >= 0 -> markedReply
                else -> markedRoot
            }
        val parentAuthor = if (parent >= 0) thread[parent]?.author else null

        return links {
            tags.forEachIndexed { i, tag ->
                if (tag.size < 2) return@forEachIndexed
                when (tag[0]) {
                    "e" -> {
                        if (i == root) event(Relation.ROOT, tag[1], "e")
                        if (i == parent) event(Relation.PARENT, tag[1], "e")
                        if (i != root && i != parent) {
                            event(if (thread[i]?.marker == MarkedETag.MARKER.FORK) Relation.FORK else Relation.MENTION, tag[1], "e")
                        }
                    }
                    "a" -> address(Relation.MENTION, tag[1], "a")
                    "q" -> eventOrAddress(Relation.QUOTE, tag[1], "q")
                    "p" -> user(if (tag[1] == parentAuthor) Relation.PARENT_AUTHOR else Relation.MENTION, tag[1], "p")
                }
            }
            contentMentions(citedNIP19())
        }
    }

    override fun indexableContent() =
        buildString {
            append(content)
            pollOptionsArray().forEach { append('\n').append(it.descriptor) }
        }

    // The read path. `content` is visited even when empty: the joined form appends it
    // unconditionally, so the separator it produces is part of what the store indexed.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(content)) return
        pollOptionsArray().forEach { if (!visitor.visit(it.descriptor)) return }
    }

    override fun eventHints(): List<EventIdHint> {
        val eHints = tags.mapNotNull(MarkedETag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val eHints = tags.mapNotNull(MarkedETag::parseId)
        val qHints = tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = citedNIP19().eventIds()

        return eHints + qHints + nip19Hints
    }

    override fun addressHints(): List<AddressHint> {
        val aHints = tags.mapNotNull(ATag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = citedNIP19().addressHints()

        return aHints + qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val aHints = tags.mapNotNull(ATag::parseAddressId)
        val qHints = tags.mapNotNull(QTag::parseAddressId)
        val nip19Hints = citedNIP19().addressIds()

        return aHints + qHints + nip19Hints
    }

    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints = tags.mapNotNull(PTag::parseAsHint)
        val nip19Hints = citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val pHints = tags.mapNotNull(PTag::parseKey)
        val nip19Hints = citedNIP19().pubKeys()

        return pHints + nip19Hints
    }

    fun pollOptionsArray() = tags.mapNotNull(PollOptionTag::parse)

    fun pollOptions() = pollOptionsArray().associate { it.index to it.descriptor }

    fun minAmount() = tags.firstNotNullOfOrNull(MinimumTag::parse)

    fun maxAmount() = tags.firstNotNullOfOrNull(MaximumTag::parse)

    fun closedAt() = tags.firstNotNullOfOrNull(ClosedAtTag::parse)

    fun consensusThreshold() = tags.firstNotNullOfOrNull(ConsensusThresholdTag::parse)

    companion object {
        const val KIND = 6969

        fun build(
            post: String,
            options: List<PollOptionTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ZapPollEvent>.() -> Unit = {},
        ): EventTemplate<ZapPollEvent> {
            val tags = TagArrayBuilder<ZapPollEvent>()
            tags.pollOptions(options)
            tags.apply(initializer)
            return EventTemplate(createdAt, KIND, tags.build(), post)
        }
    }
}
