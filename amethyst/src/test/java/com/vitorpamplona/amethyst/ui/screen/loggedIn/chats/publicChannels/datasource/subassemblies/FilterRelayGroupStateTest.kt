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
import org.junit.Assert.assertFalse
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

    private val metadataKinds =
        listOf(
            GroupMetadataEvent.KIND,
            GroupAdminsEvent.KIND,
            GroupMembersEvent.KIND,
            SupportedRolesEvent.KIND,
        )
    private val pinKinds = listOf(GroupPinnedEvent.KIND)

    @Test
    fun `with no pins it is host d-scoped state filters and no message window`() {
        val channel = RelayGroupChannel(groupId)

        val filters = filterRelayGroupState(channel, since = null)

        assertEquals(2, filters.size)
        filters.forEach { f ->
            assertEquals(relayA, f.relay)
            assertEquals(listOf("g1"), f.filter.tags!!["d"])
            assertNull("state is #d-scoped, never #h — the message window is the tail/pager's job", f.filter.tags!!["h"])
            assertNull("state carries no message-window kinds (9/poll), so no limit either", f.filter.limit)
            assertNull(f.filter.until)
        }
        assertEquals(metadataKinds, filters[0].filter.kinds)
        assertEquals(pinKinds, filters[1].filter.kinds)
    }

    /**
     * Regression: relay29-family relays (0xchat's `groups.0xchat.com`) answer a filter that mixes the
     * 39000-39003 metadata kinds with any other kind — 39005 pins included — with
     * `CLOSED … "blocked: it's not allowed to mix metadata kinds with others"`, dropping the WHOLE REQ.
     * The group then never learns its name, roster or the user's own membership, so it renders as a raw
     * id and offers "Join" to somebody the relay already lists as an admin. Keep the two apart.
     */
    @Test
    fun `pins are never mixed into the metadata filter`() {
        val channel = RelayGroupChannel(groupId)

        filterRelayGroupState(channel, since = null).forEach { f ->
            val kinds = f.filter.kinds ?: return@forEach
            assertFalse(
                "39005 must not share a filter with the 39000-39003 metadata block: $kinds",
                kinds.contains(GroupPinnedEvent.KIND) && kinds.any { it in metadataKinds },
            )
        }
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
        assertEquals(3, filters.size)

        val pinFilter = filters.first { it.filter.ids != null }
        assertEquals(relayA, pinFilter.relay)
        assertEquals(pinnedIds, pinFilter.filter.ids)
        assertNull("pinned bodies are fetched by id, so no kinds", pinFilter.filter.kinds)
        assertNull("pinned events are immutable, so no since either", pinFilter.filter.since)

        // The state filters are still present and still carry no #h message window.
        val stateFilters = filters.filter { it.filter.ids == null }
        assertEquals(listOf(metadataKinds, pinKinds), stateFilters.map { it.filter.kinds })
        stateFilters.forEach {
            assertTrue(it.filter.tags!!.containsKey("d"))
            assertNull(it.filter.tags!!["h"])
        }
    }
}
