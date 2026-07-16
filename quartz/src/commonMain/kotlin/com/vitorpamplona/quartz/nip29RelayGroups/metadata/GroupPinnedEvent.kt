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
package com.vitorpamplona.quartz.nip29RelayGroups.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-29 relay-signed list of a group's pinned messages (kind 39005). The relay
 * regenerates it from the accepted [com.vitorpamplona.quartz.nip29RelayGroups.moderation.UpdatePinListEvent]
 * (kind 9010) moderation actions, so this is the read side clients render — the
 * source of truth for which messages are pinned and in what display order.
 *
 * Addressed by the group id (`d` tag). The pinned event ids are carried as `e`
 * tags in display order.
 */
@Immutable
class GroupPinnedEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun groupId() = dTag()

    /** Pinned message ids, in the relay's display order. */
    fun pinnedEventIds(): List<HexKey> = tags.mapValueTagged("e") { it }

    companion object {
        const val KIND = 39005

        fun build(
            groupId: String,
            pinnedEventIds: List<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupPinnedEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(groupId)
            pinnedEventIds.forEach { add(arrayOf("e", it)) }
            initializer()
        }
    }
}
