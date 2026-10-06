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
package com.vitorpamplona.quartz.nipC7Chats

import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val quotedAddress = "30023:$other:article"
    private val citedAddress = "30402:$other:listing"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!
    private val relay2 = RelayUrlNormalizer.normalizeOrNull("wss://nos.lol/")!!

    private fun chat(
        content: String,
        vararg tags: Array<String>,
    ) = ChatEvent("00".repeat(32), author, 1700000000, arrayOf(*tags), content, "00".repeat(64))

    @Test
    fun quotedAndCitedAddressesAreAddressReferences() {
        val naddr = NAddress.create(30402, other, "listing", relay2)
        val event =
            chat(
                "look at nostr:$naddr",
                arrayOf("q", eventId, relay.url, other),
                arrayOf("q", quotedAddress, relay.url),
                arrayOf("q", "30023:$other:no-relay"),
            )

        assertEquals(listOf(quotedAddress, "30023:$other:no-relay", citedAddress), event.linkedAddressIds())
        assertEquals(listOf(AddressHint(quotedAddress, relay), AddressHint(citedAddress, relay2)), event.addressHints())

        // q addresses are not event ids, and vice versa.
        assertEquals(listOf(eventId), event.linkedEventIds())
        assertEquals(listOf(EventIdHint(eventId, relay)), event.eventHints())
    }

    @Test
    fun aChatWithNoAddressesHasNone() {
        val event = chat("hello", arrayOf("q", eventId, relay.url))
        assertEquals(emptyList(), event.linkedAddressIds())
        assertEquals(emptyList(), event.addressHints())
    }
}
