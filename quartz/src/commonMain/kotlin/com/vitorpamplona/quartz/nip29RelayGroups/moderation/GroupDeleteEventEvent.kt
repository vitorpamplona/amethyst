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
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GroupDeleteEventEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    /** A moderator's delete-event: NIP-09's owner-only rule does not govern it, the source kind says so. */
    override fun links(): List<Link> =
        links {
            tags.fastForEach {
                if (it.size < 2) return@fastForEach
                when (it[0]) {
                    "h" -> tag(Relation.GROUP, "h", it[1])
                    "e" -> event(Relation.DELETED, it[1], "e")
                }
            }
        }

    fun groupId() = tags.groupId()

    fun deletedEventIds() = tags.deletedEventIds()

    fun previousEvents() = tags.previousEvents()

    companion object {
        const val KIND = 9005

        fun build(
            groupId: String,
            eventIds: List<HexKey>,
            previousEvents: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupDeleteEventEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            groupId(groupId)
            eventIds.forEach { add(arrayOf("e", it)) }
            previous(previousEvents)
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to GroupDeleteEventEvent. NIP-29 group events carry the Group prefix to keep them apart from NIP-43 relay membership events.",
    ReplaceWith("GroupDeleteEventEvent", "com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent"),
)
typealias DeleteEventEvent = GroupDeleteEventEvent
