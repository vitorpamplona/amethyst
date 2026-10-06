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
package com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

@Immutable
class BidConfirmationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    // The seller's optional free-text `message`; status is a machine enum and stays out.
    override fun indexableContent() = confirmationData()?.message.orEmpty()

    // The read path: the one field, handed over as held (null when absent) — no `orEmpty()`.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(confirmationData()?.message)
    }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    // NIP-15: exactly two `e` tags, the bid and then its auction.
    override fun linkedEventIds() = listOfNotNull(bidId(), auctionId())

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    // The builder notifies a single `p`, the bidder.
    override fun linkedPubKeys() = listOfNotNull(bidder())

    /** The bid being confirmed: the first `e` (NIP-15). */
    fun bidId(): HexKey? = tags.firstNotNullOfOrNull(ETag::parseId)

    /** The auction the confirmed bid was placed on: the second `e` (NIP-15). */
    fun auctionId(): HexKey? {
        var seenBid = false
        for (tag in tags) {
            val id = ETag.parseId(tag) ?: continue
            if (seenBid) return id
            seenBid = true
        }
        return null
    }

    /** The author of the confirmed bid (`p`, written by [notifyBidder]). */
    fun bidder(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)

    // forEachIndexableField() runs on every keystroke, so the body is decoded once per
    // instance — a failure included, which also keeps the warning to one per event. A failure
    // is cached as the [ParseFailed] marker, not a `Result.failure`: that would pin the
    // exception and its stack trace to every malformed event the cache holds. Events are
    // immutable; a race only decodes twice.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var confirmationDataCache: Any? = null // BidConfirmationData, or ParseFailed

    fun confirmationData(): BidConfirmationData? {
        confirmationDataCache?.let { return it as? BidConfirmationData }
        val parsed =
            try {
                JsonMapper.fromJson<BidConfirmationData>(content)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("BidConfirmationEvent") { "Content Parse Error: ${toNostrUri()} ${e.message}" }
                null
            }
        confirmationDataCache = parsed ?: ParseFailed
        return parsed
    }

    /** What [confirmationDataCache] holds once the body failed to decode. */
    private object ParseFailed

    fun status() = confirmationData()?.status

    companion object {
        const val KIND = 1022

        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_PENDING = "pending"
        const val STATUS_WINNER = "winner"

        fun build(
            bid: EventHintBundle<BidEvent>,
            auction: EventHintBundle<AuctionEvent>,
            confirmation: BidConfirmationData,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<BidConfirmationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, JsonMapper.toJson(confirmation), createdAt) {
            bid(bid)
            auction(auction)
            notifyBidder(bid)
            initializer()
        }
    }
}
