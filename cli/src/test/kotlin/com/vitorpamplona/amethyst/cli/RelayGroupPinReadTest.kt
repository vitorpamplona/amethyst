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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.commands.RelayGroupModerationCommands
import com.vitorpamplona.amethyst.cli.commands.RelayGroupModerationCommands.PinListRead
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DONE_REASON_EOSE
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.FetchAllResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `relaygroup pin|unpin` re-submits the whole 39005 list, so a failed read must abort instead of
 * being read as "no pins" (which would publish an empty replacement and wipe them).
 */
class RelayGroupPinReadTest {
    private val relay = RelayUrlNormalizer.normalize("wss://groups.example.com")
    private val relayKey = "aa".repeat(32)
    private val stranger = "bb".repeat(32)
    private val pinnedId = "cc".repeat(32)

    private fun pinned(
        author: String,
        createdAt: Long,
    ) = GroupPinnedEvent(
        id = "dd".repeat(32),
        pubKey = author,
        createdAt = createdAt,
        tags = arrayOf(arrayOf("d", "gid"), arrayOf("e", pinnedId)),
        content = "",
        sig = "ee".repeat(64),
    )

    @Test
    fun eoseWithoutAListMeansNoPins() {
        val read = RelayGroupModerationCommands.readPinList(FetchAllResult(emptyList(), mapOf(relay to DONE_REASON_EOSE), emptySet()), relay, relayKey)
        assertEquals(emptyList(), assertIs<PinListRead.Found>(read).pins)
    }

    @Test
    fun aTimedOutReadAbortsWithTimeout() {
        val read = RelayGroupModerationCommands.readPinList(FetchAllResult(emptyList(), emptyMap(), setOf(relay)), relay, relayKey)
        assertEquals("timeout", assertIs<PinListRead.Failed>(read).code)
    }

    @Test
    fun aClosedOrUnreachableReadAborts() {
        val read = RelayGroupModerationCommands.readPinList(FetchAllResult(emptyList(), mapOf(relay to "cannot:refused"), emptySet()), relay, relayKey)
        assertEquals("fetch_failed", assertIs<PinListRead.Failed>(read).code)
    }

    @Test
    fun onlyTheRelaySignedListCounts() {
        val events = listOf(relay to pinned(stranger, 200), relay to pinned(relayKey, 100))
        val read = RelayGroupModerationCommands.readPinList(FetchAllResult(events, mapOf(relay to DONE_REASON_EOSE), emptySet()), relay, relayKey)
        assertEquals(listOf(pinnedId), assertIs<PinListRead.Found>(read).pins.map { it.ref })
    }

    @Test
    fun aForgedListAloneIsNotAnAnswerWithoutEose() {
        val read = RelayGroupModerationCommands.readPinList(FetchAllResult(listOf(relay to pinned(stranger, 200)), emptyMap(), setOf(relay)), relay, relayKey)
        assertEquals("timeout", assertIs<PinListRead.Failed>(read).code)
    }
}
