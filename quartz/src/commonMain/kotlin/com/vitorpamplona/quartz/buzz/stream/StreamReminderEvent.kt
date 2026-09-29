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
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.links.userTags
import com.vitorpamplona.quartz.nip01Core.links.valueTags
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A reminder attached to a Buzz stream message or time, `kind:40007`.
 *
 * Channel-scoped via the `h` tag and `p`-tagged with the user the reminder is for
 * (Buzz's `buzz-db/src/feed.rs` describes it as "reminder, tagged with user pubkey").
 * `KIND_STREAM_REMINDER` has no dedicated SDK builder — the target-message `e` tag and
 * any due-time tag are not defined in the Buzz source; see report uncertainties.
 */
@Immutable
class StreamReminderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    fun channel() = tags.channel()

    fun recipients() = tags.mentions()

    fun targetMessage() = tags.targetMessage()

    override fun links(): List<Link> =
        links {
            valueTags(Relation.GROUP, tags, GroupIdTag.TAG_NAME)
            userTags(Relation.RECIPIENT, tags)
            event(Relation.REMINDED, targetMessage(), ETag.TAG_NAME)
        }

    companion object {
        const val KIND = 40007

        fun build(
            channelId: String,
            recipientPubKey: HexKey,
            content: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<StreamReminderEvent>.() -> Unit = {},
        ) = eventTemplate<StreamReminderEvent>(KIND, content, createdAt) {
            channel(channelId)
            mention(recipientPubKey)
            initializer()
        }
    }
}
