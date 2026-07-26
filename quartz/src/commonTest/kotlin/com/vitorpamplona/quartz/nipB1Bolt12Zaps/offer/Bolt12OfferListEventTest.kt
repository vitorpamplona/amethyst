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
package com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Bech32
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Bolt12OfferListEventTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val offerA = Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, byteArrayOf(1, 2, 3, 4))
    private val offerB = Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, byteArrayOf(5, 6, 7, 8))

    @Test
    fun holdsOffersAndResolvesTheFactoryType() =
        runTest {
            val event = Bolt12OfferListEvent.create(listOf(offerA, offerB), signer)

            assertEquals(Bolt12OfferListEvent.KIND, event.kind)
            assertEquals("", event.content)
            assertEquals(listOf(offerA, offerB), event.offers())
            assertEquals(offerA, event.firstOffer())
            assertTrue(Event.fromJson(event.toJson()) is Bolt12OfferListEvent)
        }

    @Test
    fun ignoresMalformedOfferTags() =
        runTest {
            // Manually build a list with one good and one junk offer tag.
            val event =
                Bolt12OfferListEvent(
                    id = "a".repeat(64),
                    pubKey = "b".repeat(64),
                    createdAt = 1_700_000_000L,
                    tags = arrayOf(arrayOf("offer", offerA), arrayOf("offer", "not-an-offer")),
                    content = "",
                    sig = "c".repeat(128),
                )
            assertEquals(listOf(offerA), event.offers())
        }

    @Test
    fun updateOffersReplacesTheOfferSetKeepingOtherTags() =
        runTest {
            val original =
                signer.sign(
                    1_700_000_000L,
                    Bolt12OfferListEvent.KIND,
                    arrayOf(arrayOf("offer", offerA), arrayOf("alt", "my offers")),
                    "",
                ) as Bolt12OfferListEvent

            val updated = Bolt12OfferListEvent.updateOffers(original, listOf(offerB), signer)

            assertEquals(listOf(offerB), updated.offers())
            // The non-offer tag survives the update.
            assertTrue(updated.tags.any { it.isNotEmpty() && it[0] == "alt" && it[1] == "my offers" })
        }
}
