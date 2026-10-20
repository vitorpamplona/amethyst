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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The "add a coordinator" forms, which build a [CoordinatorConfig] from text
 * on every keystroke, inside composition.
 *
 * [CoordinatorConfig] throws on a pubkey that is not 32 bytes, so the parse
 * has to reject anything shorter: a half-typed or half-pasted hex key used to
 * reach the constructor and crash the app on the frame that rendered it.
 */
class CoordinatorConfigInputTest {
    private val coordinator: HexKey = "cc".repeat(32)
    private val relays = "wss://relay.example.com\n  \nnot a relay"

    @Test
    fun `a partly typed hex key is not a coordinator yet`() {
        // Even-length hex decodes cleanly, so this is the shape that crashed.
        listOf("a", "ab", "abcd", coordinator.dropLast(2), coordinator + "cc").forEach {
            assertNull(CoordinatorConfig.fromInput(it, relays), "accepted '$it'")
        }
    }

    @Test
    fun `a full hex key with a relay is a coordinator`() {
        val config = assertNotNull(CoordinatorConfig.fromInput("  $coordinator ", relays, label = "  Work "))
        assertEquals(coordinator, config.pubKey)
        assertEquals(listOf("wss://relay.example.com/"), config.relays.map { it.url })
        assertEquals(CoordinatorConfig.Origin.MANUAL, config.origin)
        assertEquals("Work", config.label)
    }

    @Test
    fun `an npub works as well as hex`() {
        val npub = NPub.create(coordinator)
        assertEquals(coordinator, CoordinatorConfig.fromInput(npub, relays)?.pubKey)
    }

    @Test
    fun `no relay, no blank label`() {
        assertNull(CoordinatorConfig.fromInput(coordinator, ""))
        assertNull(CoordinatorConfig.fromInput(coordinator, relays, label = "   ")?.label)
    }
}
