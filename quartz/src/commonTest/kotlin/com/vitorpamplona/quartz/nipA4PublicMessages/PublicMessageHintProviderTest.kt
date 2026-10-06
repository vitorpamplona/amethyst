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
package com.vitorpamplona.quartz.nipA4PublicMessages

import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicMessageHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val receiver = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val splitee = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val addressId = "30023:$receiver:article"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!

    private fun message(vararg tags: Array<String>) = PublicMessageEvent("00".repeat(32), author, 1700000000, arrayOf(*tags), "hi", "00".repeat(64))

    @Test
    fun quoteTagsAndZapSplitsAreReferences() {
        val event =
            message(
                arrayOf("p", receiver, relay.url),
                arrayOf("q", eventId, relay.url, receiver),
                arrayOf("q", addressId),
                arrayOf("zap", splitee, relay.url, "1"),
                // a zero-weight split pays nobody, so it links nobody
                arrayOf("zap", author, relay.url, "0"),
            )

        assertEquals(listOf(receiver, splitee), event.linkedPubKeys())
        assertEquals(listOf(PubKeyHint(receiver, relay), PubKeyHint(splitee, relay)), event.pubKeyHints())

        assertEquals(listOf(eventId), event.linkedEventIds())
        assertEquals(listOf(EventIdHint(eventId, relay)), event.eventHints())

        assertEquals(listOf(addressId), event.linkedAddressIds())
        assertEquals(emptyList<AddressHint>(), event.addressHints())
    }
}
