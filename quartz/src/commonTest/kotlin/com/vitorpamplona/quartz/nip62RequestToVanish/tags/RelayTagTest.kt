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
package com.vitorpamplona.quartz.nip62RequestToVanish.tags

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayTagTest {
    private val relay = RelayUrlNormalizer.normalize("wss://relay.example.com")

    private fun tag(url: String) = arrayOf(RelayTag.TAG_NAME, url)

    @Test
    fun matchesTheNormalizedForm() {
        assertTrue(RelayTag.shouldVanishFrom(tag(relay.url), relay))
    }

    @Test
    fun matchesTheFormAClientTypes() {
        // The form most clients write: no trailing slash. It used to be accepted and ignored.
        assertTrue(RelayTag.shouldVanishFrom(tag("wss://relay.example.com"), relay))
        assertTrue(RelayTag.shouldVanishFrom(tag("wss://Relay.Example.com/"), relay))
        assertTrue(RelayTag.shouldVanishFrom(tag("relay.example.com"), relay))
    }

    @Test
    fun matchesALocalRelay() {
        val local = RelayUrlNormalizer.normalize("ws://localhost:7777")
        assertTrue(RelayTag.shouldVanishFrom(tag("ws://localhost:7777"), local))
        assertTrue(RelayTag.shouldVanishFrom(tag("ws://localhost:7777/"), local))
    }

    @Test
    fun matchesEverywhere() {
        assertTrue(RelayTag.shouldVanishFrom(tag(RelayTag.EVERYWHERE), relay))
    }

    @Test
    fun ignoresOtherRelays() {
        assertFalse(RelayTag.shouldVanishFrom(tag("wss://other.example.com"), relay))
        assertFalse(RelayTag.shouldVanishFrom(tag("wss://relay.example.com/other"), relay))
        assertFalse(RelayTag.shouldVanishFrom(tag("not a url"), relay))
        assertFalse(RelayTag.shouldVanishFrom(arrayOf("r", relay.url), relay))
        assertFalse(RelayTag.shouldVanishFrom(arrayOf(RelayTag.TAG_NAME), relay))
    }

    @Test
    fun theEventLevelCheckUsesTheSameRule() {
        val tags = arrayOf(arrayOf("p", "a".repeat(64)), tag("wss://relay.example.com"))
        assertTrue(tags.shouldVanishFrom(relay))
        assertFalse(tags.shouldVanishFrom(RelayUrlNormalizer.normalize("wss://other.example.com")))
    }
}
