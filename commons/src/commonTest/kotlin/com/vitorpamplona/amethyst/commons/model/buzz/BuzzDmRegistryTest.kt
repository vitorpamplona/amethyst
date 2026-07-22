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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuzzDmRegistryTest {
    private val relay = RelayUrlNormalizer.normalize("wss://buzz.example")
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun dm(
        id: String,
        createdAt: Long,
        participants: List<String> = listOf(alice, bob),
    ) = BuzzDmConversation(id, participants, createdAt, relay)

    @BeforeTest fun setup() = BuzzDmRegistry.clearForTesting()

    @AfterTest fun teardown() = BuzzDmRegistry.clearForTesting()

    @Test
    fun recordsAConversationAndFlagsItAsDm() {
        BuzzDmRegistry.record(dm("chan-1", createdAt = 100))
        assertTrue(BuzzDmRegistry.isDm("chan-1"))
        assertFalse(BuzzDmRegistry.isDm("chan-unknown"))
        assertEquals(listOf(alice, bob), BuzzDmRegistry.conversations.value["chan-1"]?.participants)
    }

    @Test
    fun keepsTheNewestConfirmationPerChannel() {
        BuzzDmRegistry.record(dm("chan-1", createdAt = 100, participants = listOf(alice, bob)))
        // A re-open re-materializes with a later created_at and an expanded participant set.
        BuzzDmRegistry.record(dm("chan-1", createdAt = 200, participants = listOf(alice, bob, carol)))
        assertEquals(200, BuzzDmRegistry.conversations.value["chan-1"]?.createdAt)
        assertEquals(listOf(alice, bob, carol), BuzzDmRegistry.conversations.value["chan-1"]?.participants)

        // An older confirmation arriving late never regresses the record.
        BuzzDmRegistry.record(dm("chan-1", createdAt = 50, participants = listOf(alice)))
        assertEquals(200, BuzzDmRegistry.conversations.value["chan-1"]?.createdAt)
    }

    @Test
    fun hiddenChannelsDropOutOfTheViewerList() {
        BuzzDmRegistry.record(dm("chan-1", createdAt = 100))
        BuzzDmRegistry.record(dm("chan-2", createdAt = 200))
        BuzzDmRegistry.recordHidden(alice, setOf("chan-1"))

        val visible = BuzzDmRegistry.visibleFor(alice)
        assertEquals(listOf("chan-2"), visible.map { it.channelId })
        assertEquals(setOf("chan-1"), BuzzDmRegistry.hiddenFor(alice))
    }

    @Test
    fun visibleListIsNewestFirst() {
        BuzzDmRegistry.record(dm("older", createdAt = 100))
        BuzzDmRegistry.record(dm("newer", createdAt = 300))
        BuzzDmRegistry.record(dm("middle", createdAt = 200))
        assertEquals(listOf("newer", "middle", "older"), BuzzDmRegistry.visibleFor(bob).map { it.channelId })
    }

    @Test
    fun hiddenSetIsPerViewer() {
        BuzzDmRegistry.record(dm("chan-1", createdAt = 100))
        BuzzDmRegistry.recordHidden(alice, setOf("chan-1"))
        // Bob has not hidden it, so it stays visible for him.
        assertTrue(BuzzDmRegistry.visibleFor(bob).any { it.channelId == "chan-1" })
        assertTrue(BuzzDmRegistry.visibleFor(alice).none { it.channelId == "chan-1" })

        // Clearing alice's hide (empty snapshot) brings it back.
        BuzzDmRegistry.recordHidden(alice, emptySet())
        assertTrue(BuzzDmRegistry.visibleFor(alice).any { it.channelId == "chan-1" })
    }
}
