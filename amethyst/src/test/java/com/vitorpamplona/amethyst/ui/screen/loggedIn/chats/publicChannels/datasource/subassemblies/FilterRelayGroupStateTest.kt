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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.subassemblies

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The relay-group branch of the open-channel filter (`ChannelPublic`). After the state/content split this
 * must load **only** the group's relay-signed state (39000-39005, `#d`-scoped) plus an id-only backfill of
 * pinned message bodies — and crucially **no** kind-9/poll message window (that job moved to the chat tail +
 * history pager). This pins that contract so a regression can't quietly re-add a message-window REQ here.
 */
class FilterRelayGroupStateTest {
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://relay-a.example/")!!
    private val groupId = GroupId("g1", relayA)
    private val relaySignKey = "b".repeat(64)
    private val sig = "0".repeat(128)

    private val stateKinds =
        listOf(
            GroupMetadataEvent.KIND,
            GroupAdminsEvent.KIND,
            GroupMembersEvent.KIND,
            SupportedRolesEvent.KIND,
            GroupPinnedEvent.KIND,
        )

    @Test
    fun `with no pins it is a single host d-scoped state filter and no message window`() {
        val channel = RelayGroupChannel(groupId)

        val filters = filterRelayGroupState(channel, since = null)

        val f = filters.single()
        assertEquals(relayA, f.relay)
        assertEquals(stateKinds, f.filter.kinds)
        assertEquals(listOf("g1"), f.filter.tags!!["d"])
        assertNull("state is #d-scoped, never #h — the message window is the tail/pager's job", f.filter.tags!!["h"])
        assertNull("state carries no message-window kinds (9/poll), so no limit either", f.filter.limit)
        assertNull(f.filter.until)
    }

    @Test
    fun `pins add an id-only backfill filter alongside the state filter`() {
        val channel = RelayGroupChannel(groupId)
        val pinnedIds = listOf("a".repeat(64), "c".repeat(64))
        channel.updatePinned(
            GroupPinnedEvent(
                id = "d".repeat(64),
                pubKey = relaySignKey,
                createdAt = 100L,
                tags = arrayOf(arrayOf("d", "g1"), arrayOf("e", pinnedIds[0]), arrayOf("e", pinnedIds[1])),
                content = "",
                sig = sig,
            ),
        )

        val filters = filterRelayGroupState(channel, since = null)
        assertEquals(2, filters.size)

        val pinFilter = filters.first { it.filter.ids != null }
        assertEquals(relayA, pinFilter.relay)
        assertEquals(pinnedIds, pinFilter.filter.ids)
        assertNull("pinned bodies are fetched by id, so no kinds", pinFilter.filter.kinds)
        assertNull("pinned events are immutable, so no since either", pinFilter.filter.since)

        // The state filter is still present and still carries no #h message window.
        val stateFilter = filters.first { it.filter.ids == null }
        assertEquals(stateKinds, stateFilter.filter.kinds)
        assertTrue(stateFilter.filter.tags!!.containsKey("d"))
        assertNull(stateFilter.filter.tags!!["h"])
    }
}
