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
package com.vitorpamplona.quartz.nip15Marketplace

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionData
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationData
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent
import com.vitorpamplona.quartz.nip15Marketplace.marketplace.MarketplaceEvent
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductData
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallData
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * NIP-15 bodies as relays actually hold them (shapes taken from wss://search.brainstorm.world,
 * 2026-10-13): numbers as strings, `quantity: null`, the auction tag form, other apps' JSON on the
 * same kinds. Every read is lenient: a bad field is null, a bad body is null, nothing throws.
 */
class Nip15LenientParsingTest {
    private val merchant = "0143e1efdc02b1f91062e65c55a21d2150382f272789544c2fbcbc68c24cbb0b"
    private val sig = "00".repeat(64)

    private fun <T : Event> event(
        kind: Int,
        content: String,
        vararg tags: Array<String>,
    ): T = EventFactory.create("11".repeat(32), merchant, 1_700_000_000L, kind, arrayOf(*tags), content, sig)

    // ---- 30017 stall ----

    @Test
    fun stallReadsNameCurrencyAndShippingZones() {
        val stall =
            event<StallEvent>(
                30017,
                """{"id":"212au4Pi","name":"The Hardware Store","description":"Your neighborhood hardware store, now available online.","currency":"Sats","shipping":[{"id":"64be11rM","name":"North America","cost":10000.0,"regions":["Canada","Mexico","USA"]},{"id":"d041HK7s","name":"Rest of the World","cost":20000.0,"regions":["All other countries"]}]}""",
                arrayOf("d", "212au4Pi"),
            )
        assertIs<StallEvent>(stall)
        val data = assertNotNull(stall.stallData())
        assertEquals("212au4Pi", data.id)
        assertEquals("The Hardware Store", data.name)
        assertEquals("Sats", data.currency)
        assertEquals(2, data.shipping.size)
        assertEquals("North America", data.shipping[0].name)
        assertEquals(10000.0, data.shipping[0].cost)
        assertEquals(listOf("Canada", "Mexico", "USA"), data.shipping[0].destinations())
        assertTrue(stall.isWellFormed())
        assertSame(data, stall.stallData())
    }

    @Test
    fun stallToleratesBadFieldsAndFallsBackToTheDTag() {
        val data =
            assertNotNull(
                StallData.parse(
                    """{"name":"  Shop  ","currency":7,"shipping":{"local":{"cost":5}},"description":null}""",
                    fallbackId = "the-d",
                ),
            )
        assertEquals("the-d", data.id)
        assertEquals("Shop", data.name)
        assertNull(data.currency)
        assertNull(data.description)
        assertTrue(data.shipping.isEmpty())

        val zone = assertNotNull(StallData.parse("""{"shipping":[{"id":"z","cost":"12.5","countries":["BR"]},"junk"]}"""))
        assertEquals(12.5, zone.shipping.single().cost)
        assertEquals(listOf("BR"), zone.shipping.single().destinations())
    }

    @Test
    fun otherAppsOnKind30017AreNotStalls() {
        val typingGame = event<StallEvent>(30017, """{"app":"typer","wpm":82,"accuracy":97,"levelId":3}""", arrayOf("d", "score"))
        assertNotNull(typingGame.stallData())
        assertFalse(typingGame.isWellFormed())
        assertEquals("", typingGame.indexableContent())

        listOf("", "not json", "[1,2]", "{broken", "\"str\"", "Ao8V99Qf/HvXebiOBPXsSn").forEach {
            val junk = event<StallEvent>(30017, it, arrayOf("d", "x"))
            assertNull(junk.stallData(), it)
            assertFalse(junk.isWellFormed(), it)
        }
    }

    // ---- 30018 product ----

    @Test
    fun productReadsEveryField() {
        val product =
            event<ProductEvent>(
                30018,
                """{"id":"hexbox-btc-printables","stall_id":"hexbox-tools-stall","name":"Bitcoin Printables 1.0.0 (9 STLs)","description":"9 CC0 STLs for people who run a node.","images":["https://blossom.primal.net/ab41.png","not a url"],"currency":"sat","price":2100,"quantity":null,"specs":[["type","digital"],["catalog_id","btc-printables"],["license","CC0-1.0"]],"shipping":[{"id":"digital","cost":0}]}""",
                arrayOf("d", "hexbox-btc-printables"),
                arrayOf("t", "digital"),
                arrayOf("t", "bitcoin"),
            )
        val data = assertNotNull(product.productData())
        assertEquals("hexbox-tools-stall", data.stallId)
        assertEquals("Bitcoin Printables 1.0.0 (9 STLs)", data.name)
        assertEquals(listOf("https://blossom.primal.net/ab41.png"), data.images)
        assertEquals("sat", data.currency)
        assertEquals(2100.0, data.price)
        assertNull(data.quantity)
        assertFalse(data.isSoldOut())
        assertEquals(listOf(listOf("type", "digital"), listOf("catalog_id", "btc-printables"), listOf("license", "CC0-1.0")), data.specs)
        assertEquals("digital", data.shipping.single().id)
        assertEquals(0.0, data.shipping.single().cost)
        assertEquals(listOf("digital", "bitcoin"), product.categories())
        assertTrue(product.isWellFormed())
    }

    @Test
    fun productReadsPricesWrittenAsStringsAndStockOfZero() {
        val data =
            assertNotNull(
                ProductData.parse(
                    """{"id": "nostrkit-v1.1", "stall_id": "fundingradar-stall", "name": "nostr_lib v1.1", "images": [], "price": "9", "currency": "USD", "shipping": [{"id": "default", "cost": 0, "name": "Digital delivery"}], "quantity": 0}""",
                ),
            )
        assertEquals(9.0, data.price)
        assertEquals(0L, data.quantity)
        assertTrue(data.isSoldOut())
    }

    @Test
    fun productDropsMalformedFieldsOnly() {
        val data =
            assertNotNull(
                ProductData.parse(
                    """{"name":"Thing","price":"free","quantity":"lots","specs":"none","images":"https://a.b/c.png","shipping":[{"cost":"x"}]}""",
                    fallbackId = "d-tag",
                ),
            )
        assertEquals("d-tag", data.id)
        assertEquals("Thing", data.name)
        assertNull(data.price)
        assertNull(data.quantity)
        assertTrue(data.specs.isEmpty())
        assertTrue(data.images.isEmpty())
        assertNull(data.shipping.single().cost)

        // Spec pairs: short, blank-keyed or nested entries are skipped, numbers kept as text.
        val specs = assertNotNull(ProductData.parse("""{"specs":[["size",42],["only-key"],["",""],[["x"],"y"],["color","red","extra"]]}""")).specs
        assertEquals(listOf(listOf("size", "42"), listOf("color", "red")), specs)

        // A lone `image` some merchants write instead of `images`.
        assertEquals(listOf("https://a.b/c.png"), ProductData.parse("""{"image":"https://a.b/c.png"}""")?.images)
    }

    @Test
    fun productWithoutANameIsNotWellFormed() {
        val order = event<ProductEvent>(30018, """{"listingId":"x","priceSats":100,"status":"paid"}""", arrayOf("d", "o"))
        assertFalse(order.isWellFormed())
        assertFalse(event<ProductEvent>(30018, "nope", arrayOf("d", "o")).isWellFormed())
    }

    // ---- 30019 marketplace ----

    @Test
    fun marketplaceReadsUiAndMerchants() {
        val market =
            event<MarketplaceEvent>(
                30019,
                """{"name":"Nate","about":"","ui":{"picture":"https://p.png","banner":"","theme":"dark","darkMode":false},"merchants":["$merchant","nope"],"storefront":{"x":1}}""",
                arrayOf("d", merchant),
            )
        val data = assertNotNull(market.marketplaceData())
        assertEquals("Nate", data.name)
        assertNull(data.about)
        assertEquals("https://p.png", data.ui?.picture)
        assertNull(data.ui?.banner)
        assertEquals(false, data.ui?.darkMode)
        assertEquals(listOf(merchant), market.merchants())
        assertTrue(market.isWellFormed())
    }

    @Test
    fun otherAppsOnKind30019AreNotMarketplaces() {
        val offer = event<MarketplaceEvent>(30019, """{"type":"offer","title":"Bike","priceSats":1000,"merchants":"nope","ui":"x"}""", arrayOf("d", "o"))
        assertNull(offer.marketplaceData()?.ui)
        assertFalse(offer.isWellFormed())
    }

    // ---- 30020 auction ----

    @Test
    fun auctionReadsTheSpecBody() {
        val auction =
            event<AuctionEvent>(
                30020,
                """{"id": "d8b4", "stall_id": "41dd", "name": "TEST", "description": "TEST", "images": [], "starting_bid": 20, "start_date": 1689947208, "duration": 3600.0, "specs":[["k","v"]], "shipping":[{"id":"z","cost":2}]}""",
                arrayOf("d", "d8b4"),
            )
        val data = assertNotNull(auction.auctionData())
        assertEquals("41dd", data.stallId)
        assertEquals("TEST", data.name)
        assertEquals(20.0, data.startingBid)
        assertEquals(1689947208L, data.startDate)
        assertEquals(3600L, data.duration)
        assertEquals(1689947208L + 3600, data.endsAt())
        assertEquals(1689947208L + 3600 + 300, data.endsAt(extensionSeconds = 300))
        assertNull(data.currency)
        assertTrue(auction.isWellFormed())
    }

    @Test
    fun auctionBodyGapsAreFilledFromTags() {
        // mkauction: the body says `title` (not `name`) and has no currency; the tags carry it.
        val auction =
            event<AuctionEvent>(
                30020,
                """{"id":"b877","stall_id":"fcf8","title":"Hash 21","description":"Oriental hash","images":["https://blossom.primal.net/dd09.png"],"starting_bid":3000,"start_date":1775712859,"duration":86400,"auto_extend":true,"shipping":{"local":{"cost":5}}}""",
                arrayOf("d", "b877"),
                arrayOf("t", "art"),
                arrayOf("title", "Hash 21"),
                arrayOf("starting_bid", "3000"),
                arrayOf("currency", "sats"),
                arrayOf("start_date", "1775712859"),
                arrayOf("duration", "86400"),
            )
        val data = assertNotNull(auction.auctionData())
        assertEquals("Hash 21", data.name)
        assertEquals("sats", data.currency)
        assertEquals(3000.0, data.startingBid)
        assertEquals(listOf("https://blossom.primal.net/dd09.png"), data.images)
        assertTrue(data.shipping.isEmpty())
        assertEquals(1775712859L + 86400, data.endsAt())
    }

    @Test
    fun auctionTagFormWithPlainTextContent() {
        // hash21: no JSON at all; the end is an absolute `end_time`.
        val auction =
            event<AuctionEvent>(
                30020,
                "Digital Art",
                arrayOf("d", "roatan-2-1777236453"),
                arrayOf("t", "hash21"),
                arrayOf("title", "Roatan 2"),
                arrayOf("image", "https://image.nostr.build/0621.jpg"),
                arrayOf("start_price", "1000"),
                arrayOf("currency", "sats"),
                arrayOf("start_time", "1777236453"),
                arrayOf("end_time", "1777240053"),
            )
        val data = assertNotNull(auction.auctionData())
        assertEquals("roatan-2-1777236453", data.id)
        assertEquals("Roatan 2", data.name)
        assertEquals("Digital Art", data.description)
        assertEquals(listOf("https://image.nostr.build/0621.jpg"), data.images)
        assertEquals(1000.0, data.startingBid)
        assertEquals(1777236453L, data.startDate)
        assertEquals(3600L, data.duration)
        assertEquals(1777240053L, data.endsAt())
    }

    @Test
    fun auctionWithoutABodyOrTitleIsNothing() {
        listOf("100", "# im using markdown", "Ao8V99Qf/HvXebiOBPXsSnjKz7xqTzfu8Sj8h3N").forEach {
            val junk = event<AuctionEvent>(30020, it, arrayOf("d", "x"), arrayOf("p", merchant))
            assertNull(junk.auctionData(), it)
            assertFalse(junk.isWellFormed(), it)
        }
        val benchmark = event<AuctionEvent>(30020, """{"agent":"a","score":10,"max_score":100.0}""", arrayOf("t", "x"))
        assertNull(benchmark.auctionData()?.name)
        assertFalse(benchmark.isWellFormed())
        // No start: no end either.
        assertNull(AuctionData.read("""{"name":"x","duration":10}""", emptyArray())?.endsAt())
    }

    // ---- 1021 bid / 1022 confirmation ----

    @Test
    fun bidAmountIsLenient() {
        val auctionId = "56".repeat(32)
        assertEquals(5000.0, event<BidEvent>(1021, " 5000 ", arrayOf("e", auctionId)).amount())
        assertTrue(event<BidEvent>(1021, "5000", arrayOf("e", auctionId)).isWellFormed())
        assertNull(event<BidEvent>(1021, "five", arrayOf("e", auctionId)).amount())
        assertNull(event<BidEvent>(1021, "-3", arrayOf("e", auctionId)).amount())
        assertFalse(event<BidEvent>(1021, "5000").isWellFormed())
    }

    @Test
    fun confirmationReadsStatusMessageAndExtension() {
        val bid = "61".repeat(32)
        val auction = "56".repeat(32)
        val ok =
            event<BidConfirmationEvent>(
                1022,
                """{"status":"Accepted","message":"You are the highest bid! Congrats!","total_cost":222,"duration_extended":"300"}""",
                arrayOf("e", bid),
                arrayOf("e", auction),
            )
        assertEquals("accepted", ok.status())
        assertEquals("You are the highest bid! Congrats!", ok.confirmationData()?.message)
        assertEquals(300L, ok.confirmationData()?.durationExtended)
        assertEquals(bid, ok.bidId())
        assertEquals(auction, ok.auctionId())
        assertTrue(ok.isWellFormed())

        // The key Quartz used to write before following the spec's name.
        assertEquals(60L, BidConfirmationData.parse("""{"status":"accepted","duration_extension":60}""")?.durationExtended)
        // And it now writes the spec's.
        assertTrue(JsonMapper.toJson(BidConfirmationData("accepted", durationExtended = 60)).contains("\"duration_extended\":60"))

        assertFalse(event<BidConfirmationEvent>(1022, """{"message":"no status"}""", arrayOf("e", bid)).isWellFormed())
        assertFalse(event<BidConfirmationEvent>(1022, """{"status":"winner"}""").isWellFormed())
    }

    @Test
    fun buildersWriteWhatTheParsersRead() {
        val stall = StallEvent.build(StallData(id = "s1", name = "Shop", currency = "USD"))
        assertEquals("""{"id":"s1","name":"Shop","currency":"USD"}""", stall.content)
        assertEquals("Shop", StallData.parse(stall.content)?.name)

        val product = ProductEvent.build(ProductData(id = "p1", stallId = "s1", name = "Mug", currency = "USD", price = 12.5, quantity = 3))
        val read = assertNotNull(ProductData.parse(product.content))
        assertEquals(12.5, read.price)
        assertEquals(3L, read.quantity)
        assertEquals("s1", read.stallId)
    }
}
