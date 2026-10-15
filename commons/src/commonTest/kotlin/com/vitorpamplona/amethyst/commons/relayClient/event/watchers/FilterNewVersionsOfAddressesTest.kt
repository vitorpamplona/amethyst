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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilterNewVersionsOfAddressesTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://one.example")!!
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private fun header(
        author: String,
        d: String,
        createdAt: Long,
    ) = AddressableNote(Address(AddressableListHeaderEvent.KIND, author, d)).apply {
        addRelaySync(relay)
        event = AddressableListHeaderEvent("c".repeat(64), author, createdAt, arrayOf(arrayOf("d", d)), "", "d".repeat(128))
    }

    @Test
    fun asksForNewerVersionsOfWhatIsHeld() {
        val filters = filterNewVersionsOfAddresses(listOf(header(alice, "github-accounts", 200), header(bob, "books", 100)), null)!!

        val filter = filters.single()
        assertEquals(relay, filter.relay)
        assertEquals(listOf(AddressableListHeaderEvent.KIND), filter.filter.kinds)
        assertEquals(listOf(alice, bob), filter.filter.authors)
        assertEquals(mapOf("d" to listOf("books", "github-accounts")), filter.filter.tags)
        // No answer yet from the relay: from the oldest version held, not from zero.
        assertEquals(100L, filter.filter.since)
    }

    @Test
    fun onceTheRelayAnsweredOnlyWhatIsNewer() {
        val since = mutableMapOf(relay to MutableTime(500))

        val filters = filterNewVersionsOfAddresses(listOf(header(alice, "github-accounts", 200)), since)!!

        assertEquals(500L, filters.single().filter.since)
    }

    @Test
    fun oneFilterPerKind() {
        val article =
            AddressableNote(Address(LongFormContentEvent.KIND, alice, "post")).apply {
                addRelaySync(relay)
                event = LongFormContentEvent("e".repeat(64), alice, 300, arrayOf(arrayOf("d", "post")), "", "f".repeat(128))
            }

        val filters = filterNewVersionsOfAddresses(listOf(header(alice, "github-accounts", 200), article), null)!!

        assertEquals(listOf(listOf(LongFormContentEvent.KIND), listOf(AddressableListHeaderEvent.KIND)), filters.map { it.filter.kinds })
    }

    /** An address with no version yet is the loader's to find. */
    @Test
    fun skipsAddressesNotYetLoaded() {
        val missing = AddressableNote(Address(AddressableListHeaderEvent.KIND, alice, "x")).apply { addRelaySync(relay) }

        assertNull(filterNewVersionsOfAddresses(listOf(missing), null))
    }
}
