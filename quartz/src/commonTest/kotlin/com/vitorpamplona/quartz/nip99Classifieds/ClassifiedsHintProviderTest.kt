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
package com.vitorpamplona.quartz.nip99Classifieds

import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class ClassifiedsHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val splitee = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val quotedId = "c3d3a1a5d6d4f7c9f0e3b1a2c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f7"
    private val citedId = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!
    private val relay2 = RelayUrlNormalizer.normalizeOrNull("wss://nos.lol/")!!

    @Test
    fun quotesZapSplitsAndContentCitationsAreReferences() {
        val nprofile = NProfile.create(other, relay2)
        val nevent = NEvent.create(citedId, null, null, relay2)
        val naddr = NAddress.create(30023, other, "review", relay2)
        val content = "Selling my bike. Seller: nostr:$nprofile, see nostr:$nevent and nostr:$naddr"
        val tags =
            arrayOf(
                arrayOf("d", "bike"),
                arrayOf("p", other, relay.url),
                arrayOf("e", eventId, relay.url),
                arrayOf("a", "30402:$other:old", relay.url),
                arrayOf("q", quotedId, relay.url),
                arrayOf("q", "30023:$other:post"),
                arrayOf("zap", splitee, relay.url, "1"),
            )
        val event = ClassifiedsEvent("00".repeat(32), author, 1700000000, tags, content, "00".repeat(64))

        assertEquals(listOf(other, splitee, other), event.linkedPubKeys())
        assertEquals(listOf(PubKeyHint(other, relay), PubKeyHint(splitee, relay), PubKeyHint(other, relay2)), event.pubKeyHints())

        assertEquals(listOf(eventId, quotedId, citedId), event.linkedEventIds())
        assertEquals(listOf(EventIdHint(eventId, relay), EventIdHint(quotedId, relay), EventIdHint(citedId, relay2)), event.eventHints())

        assertEquals(listOf("30402:$other:old", "30023:$other:post", "30023:$other:review"), event.linkedAddressIds())
        assertEquals(listOf(AddressHint("30402:$other:old", relay), AddressHint("30023:$other:review", relay2)), event.addressHints())
    }
}
