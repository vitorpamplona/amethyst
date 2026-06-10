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
package com.vitorpamplona.quartz.experimental.clink.pointers

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClinkPointerTest {
    private val pubKey = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val k1 = "4caa9ee5f0f0a0b1c2d3e4f5061728394a5b6c7d8e9f00112233445566778899"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.shocknet.dev")!!

    @Test
    fun offerSpontaneousRoundTrip() {
        val offer = NOffer(pubKey, listOf(relay), "my-offer-id", null, null)
        val encoded = offer.encode()

        assertTrue(encoded.startsWith("noffer1"), "expected noffer1 prefix, got $encoded")
        assertEquals(offer, NOffer.parse(Bech32.decodeBytes(encoded).second))
        assertEquals(offer, ClinkPointerParser.parse(encoded))
    }

    @Test
    fun offerFixedPriceRoundTrip() {
        val offer = NOffer(pubKey, listOf(relay), null, OfferPriceType.FIXED, 21_000)
        val parsed = ClinkPointerParser.parse(offer.encode()) as NOffer

        assertEquals(OfferPriceType.FIXED, parsed.priceType)
        assertEquals(21_000, parsed.price)
        assertEquals(offer, parsed)
    }

    @Test
    fun offerLargePriceRoundTripIsUnsigned() {
        // A price with the high bit set (> Int.MAX_VALUE) must round-trip as a positive
        // Long — the price is an unsigned 4-byte big-endian integer, so reading it signed
        // would wrap it negative.
        val price = 3_000_000_000L
        val offer = NOffer(pubKey, listOf(relay), null, OfferPriceType.FIXED, price)
        val parsed = ClinkPointerParser.parse(offer.encode()) as NOffer

        assertEquals(price, parsed.price)
        assertEquals(offer, parsed)
    }

    @Test
    fun debitStaticRoundTrip() {
        val debit = NDebit(pubKey, listOf(relay), "pointer-7", null)
        val parsed = ClinkPointerParser.parse(debit.encode()) as NDebit

        assertEquals(debit, parsed)
        assertTrue(!parsed.isSession)
    }

    @Test
    fun debitSessionRoundTrip() {
        val debit = NDebit(pubKey, listOf(relay), null, k1)
        val parsed = ClinkPointerParser.parse(debit.encode()) as NDebit

        assertEquals(k1, parsed.k1)
        assertTrue(parsed.isSession)
        assertEquals(debit, parsed)
    }

    @Test
    fun manageRoundTrip() {
        val manage = NManage(pubKey, listOf(relay), null)
        val encoded = manage.encode()

        assertTrue(encoded.startsWith("nmanage1"))
        assertEquals(manage, ClinkPointerParser.parse(encoded))
    }

    @Test
    fun parserStripsSchemeAndWhitespace() {
        val offer = NOffer(pubKey, listOf(relay), null, null, null)
        val encoded = offer.encode()

        assertEquals(offer, ClinkPointerParser.parse("  nostr:$encoded  "))
        assertEquals(offer, ClinkPointerParser.parse("lightning:$encoded"))
    }

    @Test
    fun parserRejectsGarbageAndForeignPrefixes() {
        assertNull(ClinkPointerParser.parse("not-a-pointer"))
        assertNull(ClinkPointerParser.parse("npub1xxxxxxxxxxxxx"))
        assertNull(ClinkPointerParser.parse(""))
    }

    @Test
    fun parseAllFindsEmbeddedPointers() {
        val offer = NOffer(pubKey, listOf(relay), null, null, null).encode()
        val debit = NDebit(pubKey, listOf(relay), null, null).encode()
        val text = "Pay me at $offer or pull from $debit thanks"

        val found = ClinkPointerParser.parseAll(text)
        assertEquals(2, found.size)
        assertTrue(found[0] is NOffer)
        assertTrue(found[1] is NDebit)
    }
}
