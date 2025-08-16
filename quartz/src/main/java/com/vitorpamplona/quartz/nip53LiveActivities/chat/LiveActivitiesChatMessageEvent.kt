/**
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
package com.vitorpamplona.quartz.nip53LiveActivities.chat

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.tagArray
import com.vitorpamplona.quartz.nip01Core.hints.ETagHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.aTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.markedETag
import com.vitorpamplona.quartz.nip37Drafts.ExposeInDraft
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class LiveActivitiesChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    ETagHintProvider,
    ExposeInDraft,
    SearchableEvent {
    override fun indexableContent() = content

    private fun activityHex() = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    fun activity() = tags.firstNotNullOfOrNull(ATag::parse)

    fun activityAddress() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    override fun markedReplyTos() = super.markedReplyTos().minus(activityHex() ?: "")

    override fun unmarkedReplyTos() = super.markedReplyTos().minus(activityHex() ?: "")

    override fun exposeInDraft() =
        tagArray<LiveActivitiesChatMessageEvent> {
            activity()?.let { aTag(it) }
            reply()?.let { markedETag(it) }
        }

    companion object {
        const val KIND = 1311
        const val ALT = "Live activity chat message"

        fun reply(
            post: String,
            replyingTo: EventHintBundle<LiveActivitiesChatMessageEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveActivitiesChatMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            replyingTo.event.activity()?.let { activity(it) }
            reply(replyingTo)
            initializer()
        }

        fun message(
            post: String,
            activity: EventHintBundle<LiveActivitiesEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveActivitiesChatMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            activity(activity)
            initializer()
        }

        fun message(
            post: String,
            activity: ATag,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveActivitiesChatMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            activity(activity)
            initializer()
        }
    }
}
