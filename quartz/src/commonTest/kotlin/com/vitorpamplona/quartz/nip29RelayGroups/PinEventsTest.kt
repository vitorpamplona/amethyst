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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.UpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NIP-29 message-pinning wire format (PR #2379): the kind-9010 `update-pin-list`
 * moderation write and the relay-signed kind-39005 pinned-list read side. Verifies
 * both are dispatched to the right class by [EventFactory] and that the `h`/`d` group
 * scope plus the ordered `e` id list round-trip through build → parse.
 */
class PinEventsTest {
    private val relaySelf = "aa".repeat(32)
    private val gid = "0123456789abcdef"
    private val id1 = "11".repeat(32)
    private val id2 = "22".repeat(32)
    private val id3 = "33".repeat(32)

    @Test
    fun groupPinnedEventParsesGroupAndOrderedIds() {
        val tags = arrayOf(arrayOf("d", gid), arrayOf("e", id1), arrayOf("e", id2), arrayOf("e", id3))
        val event: Event = EventFactory.create("00".repeat(32), relaySelf, 100, GroupPinnedEvent.KIND, tags, "", "22".repeat(64))

        assertEquals(true, event is GroupPinnedEvent)
        event as GroupPinnedEvent
        assertEquals(gid, event.groupId())
        assertEquals(listOf(id1, id2, id3), event.pinnedEventIds())
    }

    @Test
    fun updatePinListEventParsesGroupAndIds() {
        val tags = arrayOf(arrayOf("h", gid), arrayOf("e", id1), arrayOf("e", id2))
        val event: Event = EventFactory.create("00".repeat(32), relaySelf, 100, UpdatePinListEvent.KIND, tags, "", "22".repeat(64))

        assertEquals(true, event is UpdatePinListEvent)
        event as UpdatePinListEvent
        assertEquals(gid, event.groupId())
        assertEquals(listOf(id1, id2), event.pinnedEventIds())
    }

    @Test
    fun updatePinListBuildCarriesHTagAndFullList() {
        val template = UpdatePinListEvent.build(gid, listOf(id1, id2))

        assertEquals(UpdatePinListEvent.KIND, template.kind)
        assertEquals(gid, template.tags.firstOrNull { it[0] == GroupIdTag.TAG_NAME }?.getOrNull(1))
        assertEquals(listOf(id1, id2), template.tags.filter { it[0] == "e" }.map { it[1] })
    }

    @Test
    fun groupPinnedBuildCarriesDTagAndFullList() {
        val template = GroupPinnedEvent.build(gid, listOf(id1, id2, id3))

        assertEquals(GroupPinnedEvent.KIND, template.kind)
        assertEquals(gid, template.tags.firstOrNull { it[0] == "d" }?.getOrNull(1))
        assertEquals(listOf(id1, id2, id3), template.tags.filter { it[0] == "e" }.map { it[1] })
    }
}
