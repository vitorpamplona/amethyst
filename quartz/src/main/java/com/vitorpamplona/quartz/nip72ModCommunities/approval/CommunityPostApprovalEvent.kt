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
package com.vitorpamplona.quartz.nip72ModCommunities.approval

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedATags
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedAddresses
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kind
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class CommunityPostApprovalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun containedPost(): Event? =
        try {
            content.ifBlank { null }?.let { fromJson(it) }
        } catch (e: Exception) {
            Log.w(
                "CommunityPostEvent",
                "Failed to Parse Community Approval Contained Post of $id with $content",
            )
            null
        }

    fun communities() = taggedATags().filter { it.kind == CommunityDefinitionEvent.KIND }

    fun communityAddresses() = taggedAddresses().filter { it.kind == CommunityDefinitionEvent.KIND }

    fun approvedEvents() = taggedEvents()

    fun approvedATags() = taggedATags().filter { it.kind != CommunityDefinitionEvent.KIND }

    fun approvedAddresses() = taggedAddresses().filter { it.kind != CommunityDefinitionEvent.KIND }

    companion object {
        const val KIND = 4550
        const val ALT_DESCRIPTION = "Community post approval"

        fun build(
            approvedPost: EventHintBundle<Event>,
            community: EventHintBundle<CommunityDefinitionEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommunityPostApprovalEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)

            community(community)
            approved(approvedPost)
            notifyAuthor(approvedPost)
            kind(approvedPost.event.kind)

            initializer()
        }
    }
}
