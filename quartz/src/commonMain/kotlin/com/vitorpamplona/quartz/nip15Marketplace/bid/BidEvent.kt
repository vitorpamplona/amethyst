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
package com.vitorpamplona.quartz.nip15Marketplace.bid

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.graph.props.AuctionProps
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class BidEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    LinkProvider {
    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    fun amount() = content.toDoubleOrNull()

    fun auctionId() = tags.firstNotNullOfOrNull(ETag::parseId)

    /** NIP-15: the auction is named by its event id (a 30020 version) and the bid amount is the content. The `p` is Quartz's notification of the auction's merchant. */
    override fun links(): List<Link<*>> =
        links {
            val props = AuctionProps(amount())
            each(tags, ETag::parse) { event(Relation.AUCTION, it, ETag.TAG_NAME, props) }
            each(tags, PTag::parse) { user(Relation.AUCTION_AUTHOR, it, PTag.TAG_NAME) }
        }

    companion object {
        const val KIND = 1021

        fun build(
            auction: EventHintBundle<AuctionEvent>,
            amount: Double,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<BidEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, amount.toLong().toString(), createdAt) {
            auction(auction)
            notifyAuthor(auction)
            initializer()
        }
    }
}
