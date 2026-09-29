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

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.AuctionProps
import com.vitorpamplona.quartz.nip01Core.links.props.BidProps
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip15MarketplaceLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val auction = "a".repeat(64)
    private val bid = "b".repeat(64)
    private val merchant = "c".repeat(64)
    private val bidder = "d".repeat(64)

    @Test
    fun bidNamesItsAuctionAndMerchantWithTheAmount() {
        val event = BidEvent(id, bidder, 1, arrayOf(arrayOf("e", auction, "wss://relay.example/"), arrayOf("p", merchant)), "150", sig)
        assertEquals(
            listOf(
                Link(Relation.AUCTION, LinkTarget.Event(auction), "e", AuctionProps(150.0)),
                Link(Relation.AUCTION_AUTHOR, LinkTarget.User(merchant), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun bidConfirmationReadsTheBidFirstAndTheAuctionSecond() {
        val event =
            BidConfirmationEvent(
                id,
                merchant,
                1,
                arrayOf(arrayOf("e", bid, "", bidder), arrayOf("e", auction, "", merchant), arrayOf("p", bidder)),
                "{\"status\":\"accepted\",\"duration_extension\":300}",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.BID, LinkTarget.Event(bid), "e", BidProps("accepted", 300L)),
                Link(Relation.AUCTION, LinkTarget.Event(auction), "e"),
                Link(Relation.BID_AUTHOR, LinkTarget.User(bidder), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun productsAndAuctionsLinkOnlyTheirCategories() {
        val tags = arrayOf(arrayOf("d", "item"), arrayOf("t", "Electronics"), arrayOf("t", "books"))
        val content = "{\"id\":\"item\",\"stall_id\":\"stall\",\"name\":\"Radio\"}"
        val expected =
            listOf(
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "electronics"), "t"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "books"), "t"),
            )
        assertEquals(expected, ProductEvent(id, merchant, 1, tags, content, sig).links())
        assertEquals(expected, AuctionEvent(id, merchant, 1, tags, content, sig).links())
    }
}
