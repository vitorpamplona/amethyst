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

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.UnrecognizedKind30405Event
import com.vitorpamplona.quartz.nip99Classifieds.review.ProductReviewEvent
import com.vitorpamplona.quartz.nip99Classifieds.review.tags.ReviewRatingTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.UnrecognizedKind30406Event
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ShippingService
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.VisibilityTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Open Markets kinds (30402 product tags, 30405 collection, 30406 shipping option, 31555
 * review) in the shapes relays hold them: Conduit's listings as wss://search.brainstorm.world held
 * them on 2026-10-16, and Shopstr's unrelated carts and escrow records on 30405 and 30406.
 */
class OpenMarketsParsingTest {
    private val merchant = "2e9130621de48a1b544a2c2b2b4a2e5b9f08afe473f773a1594ead071df2bffa"
    private val reviewer = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val sig = "00".repeat(64)

    private fun <T : Event> event(
        kind: Int,
        content: String,
        vararg tags: Array<String>,
        pubKey: String = merchant,
    ): T = EventFactory.create("11".repeat(32), pubKey, 1_791_459_352L, kind, arrayOf(*tags), content, sig)

    private val shippingAddress = "30406:$merchant:no-rulers-orange-ca"

    // ---- 30402 product ----

    @Test
    fun conduitVariationReadsItsOpenMarketsTags() {
        val product =
            event<ClassifiedsEvent>(
                30402,
                "No rulers, no rules.",
                arrayOf("d", "no-rulers-orange-camo-sneakers-10-5"),
                arrayOf("title", "No Rulers Orange Camo Sneakers - 10.5 Men/12.5 Women"),
                arrayOf("price", "53", "USD"),
                arrayOf("type", "variation", "physical"),
                arrayOf("visibility", "hidden"),
                arrayOf("stock", "4"),
                arrayOf("a", "30402:$merchant:no-rulers-orange-ca"),
                arrayOf("a", "30405:$merchant:sneakers"),
                arrayOf("spec", "US Mens/US Womens", "10.5 Men/12.5 Women"),
                arrayOf("shipping_option", shippingAddress, "2.5"),
                arrayOf("g", "u0y"),
                arrayOf("t", "sneakers"),
            )

        val type = product.productType()!!
        assertTrue(type.isVariation())
        assertTrue(type.isPhysical())
        assertTrue(product.isHidden())
        assertEquals(VisibilityTag.VISIBILITY.HIDDEN, product.visibility())
        assertEquals(4L, product.stock())
        assertEquals("u0y", product.geohash())
        assertEquals("10.5 Men/12.5 Women", product.specs().single().value)
        assertEquals(Address(30402, merchant, "no-rulers-orange-ca"), product.parentProduct())
        assertEquals(listOf(Address(30405, merchant, "sneakers")), product.collections())

        val shipping = product.shippingOptions().single()
        assertEquals(Address(30406, merchant, "no-rulers-orange-ca"), shipping.address)
        assertEquals("2.5", shipping.extraCost)
    }

    @Test
    fun shopstrShippingTagsAreNotReadAsAddresses() {
        val product =
            event<ClassifiedsEvent>(
                30402,
                "",
                arrayOf("d", "p"),
                arrayOf("title", "Mug"),
                arrayOf("shipping_option", "Italia", "500", "sats"),
            )
        assertTrue(product.shippingOptions().isEmpty())
    }

    @Test
    fun aListingTaggingItsOwnAddressIsNotItsOwnParent() {
        val plain = event<ClassifiedsEvent>(30402, "", arrayOf("d", "1ff9"), arrayOf("a", "30402:$merchant:1ff9"))
        assertNull(plain.parentProduct())

        val variation =
            event<ClassifiedsEvent>(
                30402,
                "",
                arrayOf("d", "v1"),
                arrayOf("type", "variation", "physical"),
                arrayOf("a", "30402:$merchant:v1"),
                arrayOf("a", "30402:$merchant:parent"),
            )
        assertEquals(Address(30402, merchant, "parent"), variation.parentProduct())
    }

    @Test
    fun aFormatWrittenInTheTypeSlotIsReadAsTheFormat() {
        val product = event<ClassifiedsEvent>(30402, "", arrayOf("d", "p"), arrayOf("type", "physical"))
        val type = product.productType()!!
        assertNull(type.type)
        assertTrue(type.isPhysical())
    }

    @Test
    fun aWholeDecimalStockIsACount() {
        assertEquals(0L, event<ClassifiedsEvent>(30402, "", arrayOf("d", "p"), arrayOf("stock", "0.0")).stock())
        assertNull(event<ClassifiedsEvent>(30402, "", arrayOf("d", "p"), arrayOf("stock", "1.5")).stock())
        assertNull(event<ClassifiedsEvent>(30402, "", arrayOf("d", "p"), arrayOf("stock", "em_estoque")).stock())
    }

    @Test
    fun absentOrUnknownVisibilityIsNotHidden() {
        val plain = event<ClassifiedsEvent>(30402, "", arrayOf("d", "p"), arrayOf("title", "Mug"))
        assertFalse(plain.isHidden())
        assertNull(plain.visibility())
        assertNull(plain.productType())
        assertNull(plain.stock())

        val odd = event<ClassifiedsEvent>(30402, "", arrayOf("d", "p"), arrayOf("visibility", "<hidden"), arrayOf("stock", "-2"))
        assertFalse(odd.isHidden())
        assertNull(odd.visibility())
        assertNull(odd.stock())
    }

    // ---- 30405 collection ----

    @Test
    fun collectionListsItsProductsAndIsSearchedByTitle() {
        val collection =
            event<ProductCollectionEvent>(
                30405,
                "Hand-made in Portugal.",
                arrayOf("d", "sneakers"),
                arrayOf("title", "Sneakers"),
                arrayOf("summary", "Every pair we make"),
                arrayOf("a", "30402:$merchant:a"),
                arrayOf("a", "30402:$merchant:b"),
                arrayOf("a", "30406:$merchant:standard"),
                arrayOf("shipping_option", "30406:$merchant:standard"),
                arrayOf("location", "Porto"),
                arrayOf("t", "footwear"),
            )

        assertEquals(listOf(Address(30402, merchant, "a"), Address(30402, merchant, "b")), collection.products())
        assertEquals(Address(30406, merchant, "standard"), collection.shippingOptions().single().address)
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Sneakers"),
                secondary = listOf("Every pair we make"),
                text = "Hand-made in Portugal.",
                hashtags = listOf("footwear"),
                locations = listOf("Porto"),
            ),
            SearchFieldExtractor.extract(collection),
        )
    }

    @Test
    fun aCollectionWithOnlyProductsStillParses() {
        assertIs<ProductCollectionEvent>(event(30405, "", arrayOf("d", "c"), arrayOf("a", "30402:$merchant:a")))
    }

    @Test
    fun shopstrCartOnTheSameKindIsUnrecognizedAndNotSearchable() {
        // Shopstr's real carts carry no tags: the `d` and items are inside the ciphertext.
        val cart = event<Event>(30405, "AgZ1c2VyY2FydA==?iv=abc")
        assertFalse(cart is SearchableEvent)
        assertIs<UnrecognizedKind30405Event>(cart)
        assertEquals(IndexableFields.None, SearchFieldExtractor.extract(cart))
    }

    @Test
    fun probeAnswersAsTheOpenMarketsClasses() {
        assertIs<ProductCollectionEvent>(EventFactory.probe(30405))
        assertIs<ShippingOptionEvent>(EventFactory.probe(30406))
        assertIs<ProductReviewEvent>(EventFactory.probe(31555))
    }

    // ---- 30406 shipping option ----

    @Test
    fun shippingOptionReadsTheSpecExample() {
        val option =
            event<ShippingOptionEvent>(
                30406,
                "Standard Regional Shipping",
                arrayOf("d", "standard-regional"),
                arrayOf("title", "Standard Shipping"),
                arrayOf("price", "5.99", "USD"),
                arrayOf("country", "US", "CA"),
                arrayOf("region", "US-FL"),
                arrayOf("service", "standard"),
                arrayOf("carrier", "UPS"),
                arrayOf("duration", "24", "72", "H"),
            )

        assertEquals("5.99", option.price()?.amount)
        assertEquals(listOf("US", "CA"), option.countries())
        assertEquals(listOf("US-FL"), option.regions())
        assertTrue(option.shipsTo("ca"))
        assertFalse(option.shipsTo("PT"))
        assertEquals(ShippingService.STANDARD, option.serviceType())
        assertFalse(option.isPickup())
        assertEquals("72", option.duration()?.max)
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Standard Shipping"),
                secondary = listOf("UPS"),
                text = "Standard Regional Shipping",
            ),
            SearchFieldExtractor.extract(option),
        )
    }

    @Test
    fun shopstrEscrowOnTheSameKindIsUnrecognized() {
        val escrow = event<Event>(30406, "AgZlc2Nyb3c=?iv=abc", arrayOf("d", "shopstr:p2pk-escrow:1"), arrayOf("type", "p2pk-escrow"))
        assertFalse(escrow is SearchableEvent)
        assertIs<UnrecognizedKind30406Event>(escrow)
    }

    @Test
    fun aServiceOrACountryAloneIsEnoughForAShippingOption() {
        assertIs<ShippingOptionEvent>(event(30406, "", arrayOf("d", "s"), arrayOf("service", "pickup")))
        assertIs<ShippingOptionEvent>(event(30406, "", arrayOf("d", "c"), arrayOf("country", "PT")))
    }

    @Test
    fun countriesAreCollectedAcrossRepeatedTags() {
        val option =
            event<ShippingOptionEvent>(
                30406,
                "",
                arrayOf("d", "eu"),
                arrayOf("title", "EU"),
                arrayOf("country", "AT"),
                arrayOf("country", "BE"),
                arrayOf("country", "CA", "DE"),
            )
        assertEquals(listOf("AT", "BE", "CA", "DE"), option.countries())
        assertTrue(option.shipsTo("ca"))
        assertFalse(option.shipsTo("US"))
    }

    @Test
    fun anOptionThatNamesNoCountryRestrictsNone() {
        val worldwide = event<ShippingOptionEvent>(30406, "", arrayOf("d", "w"), arrayOf("title", "Worldwide Standard"))
        assertTrue(worldwide.countries().isEmpty())
        assertTrue(worldwide.shipsTo("US"))
    }

    // ---- 31555 review ----

    @Test
    fun reviewNamesItsProductAndScoresTheSpecWay() {
        val review =
            event<ProductReviewEvent>(
                31555,
                "Fits well, slow shipping.",
                arrayOf("d", "a:30402:$merchant:no-rulers-orange-ca"),
                arrayOf("rating", "1", "thumb"),
                arrayOf("rating", "0.8", "value"),
                arrayOf("rating", "1.0", "quality"),
                arrayOf("rating", "0.6", "delivery"),
                arrayOf("rating", "0.9", "communication"),
                pubKey = reviewer,
            )

        assertEquals(Address(30402, merchant, "no-rulers-orange-ca"), review.product())
        assertEquals(merchant, review.merchant())
        assertEquals(listOf("30402:$merchant:no-rulers-orange-ca"), review.linkedAddressIds())
        assertEquals(1.0, review.thumb())
        // 1 × 0.5 + 0.5 × (0.8 + 1.0 + 0.6 + 0.9) / 4
        assertEquals(0.9125, review.score()!!, 1e-9)
        assertEquals("Fits well, slow shipping.", review.indexableContent())
    }

    @Test
    fun aThumbAloneIsTheScoreAndBadRatingsAreDropped() {
        val review =
            event<ProductReviewEvent>(
                31555,
                "",
                arrayOf("d", "a:30402:$merchant:p"),
                arrayOf("rating", "0", "thumb"),
                arrayOf("rating", "5", "quality"),
                arrayOf("rating", "x", "value"),
                pubKey = reviewer,
            )
        assertEquals(0.0, review.score())
        assertEquals(1, review.ratings().size)
    }

    @Test
    fun reviewOfSomethingOtherThanAProductHasNoProduct() {
        val review = event<ProductReviewEvent>(31555, "", arrayOf("d", "30402:$merchant:p"), arrayOf("rating", "1", "thumb"))
        assertNull(review.product())
        assertEquals(1.0, review.thumb())
    }

    // ---- builders ----

    @Test
    fun buildersWriteWhatTheParsersRead() {
        val product = Address(30402, merchant, "mug")

        val collection = ProductCollectionEvent.build("Mugs", listOf(product), dTag = "mugs")
        val readCollection = event<ProductCollectionEvent>(30405, collection.content, *collection.tags)
        assertEquals(listOf(product), readCollection.products())
        assertEquals("Mugs", readCollection.title())

        val shipping = ShippingOptionEvent.build("Pickup", PriceTag("0", "USD", null), listOf("US"), ShippingService.PICKUP, dTag = "pickup")
        val readShipping = event<ShippingOptionEvent>(30406, shipping.content, *shipping.tags)
        assertTrue(readShipping.isPickup())
        assertEquals(listOf("US"), readShipping.countries())

        val review = ProductReviewEvent.build(product, 1.0, "Great", mapOf(ReviewRatingTag.QUALITY to 0.5))
        val readReview = event<ProductReviewEvent>(31555, review.content, *review.tags, pubKey = reviewer)
        assertEquals(product, readReview.product())
        assertEquals(0.75, readReview.score())
    }
}
