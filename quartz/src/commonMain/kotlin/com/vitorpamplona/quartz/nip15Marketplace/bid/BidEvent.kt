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
    PubKeyHintProvider {
    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    // NIP-15: a bid carries a single `e`, the auction.
    override fun linkedEventIds() = listOfNotNull(auctionId())

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    // The builder notifies a single `p`, the auction's author.
    override fun linkedPubKeys() = listOfNotNull(auctionAuthor())

    fun amount() = content.toDoubleOrNull()

    fun auctionId() = tags.firstNotNullOfOrNull(ETag::parseId)

    /** The author of the auction being bid on (`p`, written by [notifyAuthor]). */
    fun auctionAuthor(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)

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
