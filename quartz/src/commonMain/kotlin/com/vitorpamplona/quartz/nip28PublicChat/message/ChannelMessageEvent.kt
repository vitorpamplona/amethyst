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
package com.vitorpamplona.quartz.nip28PublicChat.message

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.core.tagArray
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
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
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.markedETag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip28PublicChat.base.channel
import com.vitorpamplona.quartz.nip28PublicChat.base.reply
import com.vitorpamplona.quartz.nip37Drafts.ExposeInDraft
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChannelMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    IsInPublicChatChannel,
    ExposeInDraft,
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
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

    override fun channel() = markedRoot() ?: unmarkedRoot()

    override fun channelId() = channel()?.eventId

    override fun markedReplyTos() = super.markedReplyTos().filter { it != channelId() }

    override fun unmarkedReplyTos() = super.unmarkedReplyTos().filter { it != channelId() }

    override fun exposeInDraft() =
        tagArray<ChannelMessageEvent> {
            channel()?.let { markedETag(it) }
            reply()?.let { markedETag(it) }
        }

    /**
     * NIP-28: the channel is the `root`-marked `e`, so it is the `ROOT` ([channel]); the replied-to
     * message is the `reply`-marked one, the `PARENT` ([reply], never the channel itself). NIP-28
     * adds the replied-to author as a `p`: it is the `PARENT_AUTHOR` when the parent tag names that
     * same author (Quartz writes it in the `e`'s pubkey slot), else a `MENTION`, as on kind 1.
     */
    override fun links(): List<Link> =
        links {
            val channelId = channelId()
            val parentTag = reply()?.takeIf { it.eventId != channelId }
            val parentId = parentTag?.eventId
            val parentAuthor = parentTag?.author

            event(Relation.ROOT, channelId, "e")
            event(Relation.PARENT, parentId, "e")

            tags.fastForEach { tag ->
                if (tag.size < 2) return@fastForEach
                when (tag[0]) {
                    "e" -> if (tag[1] != channelId && tag[1] != parentId) event(Relation.MENTION, tag[1], "e")
                    "a" -> address(Relation.MENTION, tag[1], "a")
                    "p" -> user(if (tag[1] == parentAuthor) Relation.PARENT_AUTHOR else Relation.MENTION, tag[1], "p")
                    "q" -> eventOrAddress(Relation.QUOTE, tag[1], "q")
                }
            }

            contentMentions(citedNIP19())
        }

    companion object {
        const val KIND = 42
        const val ALT = "Public chat message"

        fun reply(
            post: String,
            replyingTo: EventHintBundle<ChannelMessageEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            replyingTo.event.channel()?.let { channel(it) }
            reply(replyingTo)
            initializer()
        }

        fun message(
            post: String,
            channel: EventHintBundle<ChannelCreateEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            channel(channel)
            initializer()
        }

        fun message(
            post: String,
            channel: ETag,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            channel(channel)
            initializer()
        }
    }
}
