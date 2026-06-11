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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cross-implementation interop vectors. The bech32 strings below were produced by the
 * reference TypeScript implementation @shocknet/clink-sdk@1.5.5 (`nofferEncode`,
 * `ndebitEncode`, `nmanageEncode`) for a fixed pubkey/relay.
 *
 * TLV is order-independent on decode, and the two implementations emit their fields in
 * different orders (we ascend 0→4, the SDK descends), so we assert *functional* interop
 * rather than byte-identical strings:
 *
 *   1. Decode: our parser reads the SDK's bytes into the expected fields.
 *   2. Round-trip: re-encoding then re-parsing our output preserves every field.
 *
 * The reverse direction — that the SDK decodes our (ascending-order) output back to the
 * same fields — was verified out-of-band by feeding our encoding to `decodeBech32`.
 */
class ClinkInteropTest {
    private val pubKey = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val relay = "wss://relay.shocknet.dev"

    // --- noffer ---

    private val offerFixed =
        "noffer1qszqqqzjpqpszqqzpphkven9wgkkjeqprpmhxue69uhhyetvv9ujuumgda3kkmn9wshxgetkqqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsx7zr9c"
    private val offerSpontaneous =
        "noffer1qvqsyqs9wd5x7up3qyv8wumn8ghj7un9d3shjtnndphkx6mwv46zuer9wcqzqln7n3p2jxl77x06j209lksmwtswhsdycy2pvulz09prfkr2mh6wexeyu2"
    private val offerVariable =
        "noffer1qszqqqqp7spszqgzq9mqzxrhwden5te0wfjkccte9eeksmmrddhx2apwv3jhvqpq0elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qhv7h2j"

    @Test
    fun decodesAndRoundTripsOfferFixed() {
        val offer = ClinkPointerParser.parse(offerFixed) as NOffer
        assertEquals(pubKey, offer.pubKey)
        assertEquals(RelayUrlNormalizer.normalizeOrNull(relay), offer.relays.single())
        assertEquals("offer-id", offer.pointer)
        assertEquals(OfferPriceType.FIXED, offer.priceType)
        assertEquals(21000L, offer.price)
        assertEquals(offer, ClinkPointerParser.parse(offer.encode()))
    }

    @Test
    fun decodesAndRoundTripsOfferSpontaneous() {
        val offer = ClinkPointerParser.parse(offerSpontaneous) as NOffer
        assertEquals("shop1", offer.pointer)
        assertEquals(OfferPriceType.SPONTANEOUS, offer.priceType)
        assertNull(offer.price)
        assertEquals(offer, ClinkPointerParser.parse(offer.encode()))
    }

    // The canonical example offer shipped by @shocknet/clink-sdk: it is both the README usage
    // example (MIT) and clink-demo / clinkme.dev's `DEFAULT_NOFFER` (public domain). A
    // real-world spontaneous, relay-bearing, no-price offer whose offer-id is a 64-char hex string.
    private val clinkDemoDefaultOffer =
        "noffer1qvqsyqjqxuurvwpcxc6rvvrxxsurqep5vfjk2wf4v33nsenrxumnyvesxfnrswfkvycrwdp3x93xydf5xg6rzce4vv6xgdfh8quxgct9x5erxvspremhxue69uhhgetnwskhyetvv9ujumrfva58gmnfdenjuur4vgqzpccxc30wpf78wf2q78wg3vq008fd8ygtl4qy06gstpye3h5unc47xmee6z"

    @Test
    fun decodesClinkDemoDefaultOffer() {
        val offer = ClinkPointerParser.parse(clinkDemoDefaultOffer) as NOffer
        assertEquals("e306c45ee0a7c772540f1dc88b00f79d2d3910bfd4047e910584998de9c9e2be", offer.pubKey)
        assertEquals(RelayUrlNormalizer.normalizeOrNull("wss://test-relay.lightning.pub"), offer.relays.single())
        assertEquals("786886460f480d4bee95dc8fc772302f896a07411bb54241c5c4d5788dae5232", offer.pointer)
        assertEquals(OfferPriceType.SPONTANEOUS, offer.priceType)
        assertNull(offer.price)
        assertEquals(offer, ClinkPointerParser.parse(offer.encode()))
    }

    @Test
    fun decodesAndRoundTripsOfferVariable() {
        val offer = ClinkPointerParser.parse(offerVariable) as NOffer
        assertEquals("v", offer.pointer)
        assertEquals(OfferPriceType.VARIABLE, offer.priceType)
        assertEquals(500L, offer.price)
        assertEquals(offer, ClinkPointerParser.parse(offer.encode()))
    }

    // --- ndebit ---

    private val debitWithPointer =
        "ndebit1qgyhqmmfde6x2u3dxuq3samnwvaz7tmjv4kxz7fwwd5x7cmtdejhgtnyv4mqqgr706wy92gmlmcel2ffuh76rdewp67p5nq3g9nnufu5ydxcdtwlfcg94z44"
    private val debitWithoutPointer =
        "ndebit1qyv8wumn8ghj7un9d3shjtnndphkx6mwv46zuer9wcqzqln7n3p2jxl77x06j209lksmwtswhsdycy2pvulz09prfkr2mh6wcrvl9u"

    @Test
    fun decodesAndRoundTripsDebitWithPointer() {
        val debit = ClinkPointerParser.parse(debitWithPointer) as NDebit
        assertEquals(pubKey, debit.pubKey)
        assertEquals(RelayUrlNormalizer.normalizeOrNull(relay), debit.relays.single())
        assertEquals("pointer-7", debit.pointer)
        assertNull(debit.k1)
        assertEquals(debit, ClinkPointerParser.parse(debit.encode()))
    }

    @Test
    fun decodesAndRoundTripsDebitWithoutPointer() {
        val debit = ClinkPointerParser.parse(debitWithoutPointer) as NDebit
        assertNull(debit.pointer)
        assertEquals(pubKey, debit.pubKey)
        assertEquals(debit, ClinkPointerParser.parse(debit.encode()))
    }

    // --- nmanage ---

    private val manageWithPointer =
        "nmanage1qgrxzurs956ryqgcwaehxw309aex2mrp0yh8x6r0vd4kuet59ejx2asqypl8a8zz4ydlauvl4y57tldpkuhqa0q6fsg5zee7y72zxnvx4h05ufx6vef"
    private val manageWithoutPointer =
        "nmanage1qyv8wumn8ghj7un9d3shjtnndphkx6mwv46zuer9wcqzqln7n3p2jxl77x06j209lksmwtswhsdycy2pvulz09prfkr2mh6wr57t3u"

    @Test
    fun decodesAndRoundTripsManageWithPointer() {
        val manage = ClinkPointerParser.parse(manageWithPointer) as NManage
        assertEquals(pubKey, manage.pubKey)
        assertEquals(RelayUrlNormalizer.normalizeOrNull(relay), manage.relays.single())
        assertEquals("app-42", manage.pointer)
        assertEquals(manage, ClinkPointerParser.parse(manage.encode()))
    }

    @Test
    fun decodesAndRoundTripsManageWithoutPointer() {
        val manage = ClinkPointerParser.parse(manageWithoutPointer) as NManage
        assertNull(manage.pointer)
        assertEquals(pubKey, manage.pubKey)
        assertEquals(manage, ClinkPointerParser.parse(manage.encode()))
    }
}
