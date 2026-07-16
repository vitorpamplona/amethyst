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
package com.vitorpamplona.quartz.nip29RelayGroups.moderation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-29 `update-pin-list` moderation event (kind 9010). Carries the group `h`
 * tag plus the FULL list of pinned message ids as `e` tags — pinning, unpinning,
 * reordering and clearing pins are all done by submitting a new complete list.
 * The relay checks the sender's role, applies it, and republishes the group's
 * kind-39005 [com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent].
 */
@Immutable
class UpdatePinListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun groupId() = tags.groupId()

    fun pinnedEventIds() = tags.pinnedEventIds()

    companion object {
        const val KIND = 9010

        fun build(
            groupId: String,
            pinnedEventIds: List<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<UpdatePinListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            groupId(groupId)
            pinnedEventIds.forEach { add(arrayOf("e", it)) }
            initializer()
        }
    }
}
