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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.contentMentions
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.links.valueTags
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * An edit of an existing Buzz stream message, `kind:40003`.
 *
 * Mirrors `build_edit` in Buzz's `buzz-sdk/src/builders.rs`: an `h` channel tag plus
 * an `e` tag pointing at the message being edited; [content] is the replacement text.
 */
@Immutable
class StreamMessageEditEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    fun channel() = tags.channel()

    fun editedMessage() = tags.targetMessage()

    override fun links(): List<Link<*>> =
        links {
            valueTags(Relation.GROUP, tags, GroupIdTag.TAG_NAME)
            event(Relation.EDITED, editedMessage(), ETag.TAG_NAME)
            contentMentions(content)
        }

    companion object {
        const val KIND = 40003

        fun build(
            channelId: String,
            targetEventId: HexKey,
            newContent: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<StreamMessageEditEvent>.() -> Unit = {},
        ) = eventTemplate<StreamMessageEditEvent>(KIND, newContent, createdAt) {
            channel(channelId)
            targetMessage(targetEventId)
            initializer()
        }
    }
}
