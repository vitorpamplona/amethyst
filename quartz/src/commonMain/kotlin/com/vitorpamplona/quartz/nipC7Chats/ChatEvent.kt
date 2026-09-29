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
package com.vitorpamplona.quartz.nipC7Chats

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quote
import com.vitorpamplona.quartz.nip19Bech32.contentMentions
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChatEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    EventHintProvider,
    PubKeyHintProvider,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    fun quotedEvents() = tags.mapNotNull(QEventTag::parse)

    fun replyingTo() = tags.lastOrNull { it.size > 1 && it[0] == QTag.TAG_NAME }?.get(1)

    override fun eventHints(): List<EventIdHint> {
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()
        return qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val qHints = tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = citedNIP19().eventIds()
        return qHints + nip19Hints
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

    /**
     * NIP-C7: a reply quotes its parent in a `q` tag whose fourth slot is the parent's author, so the
     * last `q` is the parent and any other a NIP-18 quote. A chat inside a NIP-29 group names it in `h`.
     */
    override fun links(): List<Link<*>> =
        links {
            val quotes = tags.mapNotNull(QTag::parse)
            val parent = quotes.lastOrNull()
            quotes.forEach {
                val relation = if (it === parent) Relation.PARENT else Relation.QUOTE
                when (it) {
                    is QEventTag -> event(relation, it, QTag.TAG_NAME)
                    is QAddressableTag -> address(relation, it, QTag.TAG_NAME)
                }
            }
            val parentAuthor =
                when (parent) {
                    is QEventTag -> parent.author
                    is QAddressableTag -> parent.address.pubKeyHex
                    else -> null
                }
            user(Relation.PARENT_AUTHOR, parentAuthor, QTag.TAG_NAME)
            each(tags, PTag::parse) { user(Relation.MENTION, it, PTag.TAG_NAME) }
            each(tags, GroupIdTag::parse) { tag(Relation.GROUP, GroupIdTag.TAG_NAME, it) }
            contentMentions(citedNIP19())
        }

    companion object {
        const val KIND = 9

        fun build(
            message: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChatEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            initializer()
        }

        fun reply(
            message: String,
            replyTo: EventHintBundle<ChatEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChatEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            quote(QEventTag(replyTo.event.id, replyTo.relay, replyTo.event.pubKey))
            initializer()
        }
    }
}
