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
package com.vitorpamplona.quartz.nip10Notes

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.forks.parseForkedAddress
import com.vitorpamplona.quartz.experimental.forks.parseForkedEventId
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.contentMentions
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.hashtags
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.graph.quotes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.ReferenceTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedATag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.prepareETagsAsReplyTo
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    IForkableEvent,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = listOfNotNull(subject(), content).joinToString("\n")

    // The read path: hands over the same fields indexableContent() joins, without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(subject())) return
        visitor.visit(content)
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

    fun isNewThread() = tags.none(ETag::isTagged)

    override fun isAFork() = tags.any { it.size > 3 && (it[0] == "a" || it[0] == "e") && it[3] == "fork" }

    override fun forkFromAddress() = tags.firstNotNullOfOrNull(::parseForkedAddress)

    override fun forkFromVersion() = tags.firstNotNullOfOrNull(MarkedETag::parseForkedEventId)

    /**
     * NIP-10, read the way Amethyst threads a note ([root], [replyingTo], [threadRootIdOrSelf]):
     * - `ROOT` is the `root`-marked `e`, else the first positional one; a lone `reply` marker is
     *   a direct reply, so its event is the root too.
     * - `PARENT` is the `reply`-marked `e`, else the root (a reply to the root), else the last
     *   positional one. Every other `e` is a `MENTION` (the legacy `mention` marker, or the
     *   positional ones in between), except a `fork`-marked one.
     * - `a` tags follow the same markers ([MarkedATag], [markedRootAddress]). An `a` to a NIP-72
     *   community is the `COMMUNITY` the note is posted in, never a thread root; any other unmarked
     *   `a` is a `MENTION`.
     * - A `p` is the `PARENT_AUTHOR` only when it is the author the parent tag itself names (the
     *   `e`'s pubkey slot, or an `a`'s coordinate): NIP-10 adds the replied-to author to the `p`s,
     *   but every thread member rides there too, and nothing else tells them apart.
     */
    override fun links(): List<Link<*>> =
        links {
            val parentTag = markedReply() ?: markedRoot() ?: unmarkedReply()
            val rootTag = root() ?: markedReply()
            val rootAddress = markedRootAddress() ?: markedReplyAddress().takeIf { rootTag == null }
            val parentAddress = markedReplyAddress() ?: markedRootAddress().takeIf { parentTag == null }
            val parentAuthor = parentTag?.author ?: parentAddress?.address?.pubKeyHex

            event(Relation.ROOT, rootTag, MarkedETag.TAG_NAME)
            address(Relation.ROOT, rootAddress, MarkedATag.TAG_NAME)
            event(Relation.PARENT, parentTag, MarkedETag.TAG_NAME)
            address(Relation.PARENT, parentAddress, MarkedATag.TAG_NAME)

            each(tags, MarkedETag::parseAllThreadTags) {
                when {
                    it.marker == MarkedETag.MARKER.FORK -> event(Relation.FORK, it, MarkedETag.TAG_NAME)
                    it.eventId != rootTag?.eventId && it.eventId != parentTag?.eventId -> event(Relation.MENTION, it, MarkedETag.TAG_NAME)
                }
            }
            each(tags, PTag::parse) { user(if (it.pubKey == parentAuthor) Relation.PARENT_AUTHOR else Relation.MENTION, it, PTag.TAG_NAME) }
            quotes(tags)
            each(tags, MarkedATag::parse) {
                when {
                    it.address.kind == CommunityDefinitionEvent.KIND -> address(Relation.COMMUNITY, it, MarkedATag.TAG_NAME)
                    it.marker == MarkedETag.MARKER.FORK -> address(Relation.FORK, it, MarkedATag.TAG_NAME)
                    it.address != rootAddress?.address && it.address != parentAddress?.address -> address(Relation.MENTION, it, MarkedATag.TAG_NAME)
                }
            }
            hashtags(tags)
            each(tags, ReferenceTag::parse) { tag(Relation.TAG, ReferenceTag.TAG_NAME, it) }
            each(tags, GeoHashTag::parse) { tag(Relation.TAG, GeoHashTag.TAG_NAME, it) }

            contentMentions(citedNIP19())
        }

    companion object {
        const val KIND = 1

        fun build(
            note: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TextNoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, note, createdAt) {
            initializer()
        }

        fun build(
            note: String,
            replyingTo: EventHintBundle<TextNoteEvent>? = null,
            forkingFrom: EventHintBundle<TextNoteEvent>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TextNoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, note, createdAt) {
            if (replyingTo != null || forkingFrom != null) {
                markedETags(prepareETagsAsReplyTo(replyingTo, forkingFrom))
            }

            initializer()
        }
    }
}
