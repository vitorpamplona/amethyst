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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import com.vitorpamplona.quartz.concord.cord02Community.ConcordChannel as FoldedChannel

class ConcordChannelOrderTest {
    private fun channels(vararg names: Pair<String, String>): Map<String, FoldedChannel> = linkedMapOf(*names.map { (id, name) -> id to FoldedChannel(id, ChannelEntity(name = name)) }.toTypedArray())

    @Test
    fun theFoldsMapOrderIsNotTheListOrder() {
        // The order the app showed while testing: two channels created after #general, ahead of it.
        val folded = channels("c1" to "announcements", "c2" to "staff-room", "c3" to "general")
        assertEquals(listOf("announcements", "general", "staff-room"), ConcordChannelOrder.sorted(folded.entries).map { it.value.definition.name })
    }

    @Test
    fun namesCompareAsUtf8BytesAndTheIdBreaksTies() {
        // CORD-03 §2: by name as UTF-8 bytes, so an upper-case name sorts before a lower-case one.
        val folded = channels("b" to "Zebra", "z" to "apple", "a" to "apple")
        assertEquals(listOf("b", "a", "z"), ConcordChannelOrder.sorted(folded.entries).map { it.key })
    }

    @Test
    fun positionsComeFirstThenArmadasLegacyOrder() {
        // The channel_order case of Quartz's Armada vectors: Armada lists positioned Channels first.
        val folded =
            linkedMapOf(
                "9a" to FoldedChannel("9a", ChannelEntity(name = "general")),
                "c3" to FoldedChannel("c3", ChannelEntity(name = "beta")),
                "c2" to FoldedChannel("c2", ChannelEntity(name = "alpha"), armadaOrder = 1),
                "c1" to FoldedChannel("c1", ChannelEntity(name = "zeta"), armadaOrder = 0),
                "s1" to FoldedChannel("s1", ChannelEntity(name = "yak"), position = "V"),
            )
        assertEquals(listOf("s1", "c1", "c2", "c3", "9a"), ConcordChannelOrder.sorted(folded.entries).map { it.key })
    }
}
