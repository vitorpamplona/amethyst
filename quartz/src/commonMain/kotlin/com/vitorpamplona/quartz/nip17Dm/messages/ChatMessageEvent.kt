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
package com.vitorpamplona.quartz.nip17Dm.messages

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.contentMentions
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseDMGroupEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    SearchableEvent,
    LinkProvider {
    /** NIP-17: `p` are the receivers, `e` "the direct parent message this post is replying to", `q` a NIP-18 quote. */
    override fun links(): List<Link<*>> =
        links {
            tags.fastForEach {
                if (it.size < 2) return@fastForEach
                when (it[0]) {
                    "p" -> user(Relation.RECIPIENT, it[1], "p")
                    "e" -> event(Relation.PARENT, it[1], "e")
                    "q" -> eventOrAddress(Relation.QUOTE, it[1], "q")
                }
            }
            contentMentions(content)
        }

    // content is the decrypted (plaintext) direct-message body.
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    fun replyTo() = tags.mapNotNull(ETag::parseId)

    companion object {
        const val KIND = 14

        fun build(
            msg: String,
            to: List<PTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChatMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            group(to)
            initializer()
        }

        fun reply(
            msg: String,
            reply: EventHintBundle<BaseDMGroupEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChatMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            reply(reply)
            group((reply.event.recipients() + reply.toPTag()).distinctBy { it.pubKey })
            initializer()
        }
    }
}
