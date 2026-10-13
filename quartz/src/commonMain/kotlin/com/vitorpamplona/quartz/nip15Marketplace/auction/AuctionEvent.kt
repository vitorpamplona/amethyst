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
package com.vitorpamplona.quartz.nip15Marketplace.auction

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip15Marketplace.Nip15ParseFailed
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class AuctionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun isContentEncoded() = true

    // isWellFormed() runs for every copy the cache consumes and forEachIndexableField() on every
    // search keystroke, so the auction is read once per instance, a failure included. Events are
    // immutable; a race only decodes twice.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var auctionDataCache: Any? = null // AuctionData, or Nip15ParseFailed

    /**
     * The auction, from the JSON body with the tag form filling its gaps (see [AuctionData.read]):
     * null only when neither describes one.
     */
    fun auctionData(): AuctionData? {
        auctionDataCache?.let { return it as? AuctionData }
        val parsed = AuctionData.read(content, tags, dTag())
        auctionDataCache = parsed ?: Nip15ParseFailed
        return parsed
    }

    /** An auction has a name. Other apps' records on kind 30020 (encrypted blobs, scores) do not. */
    fun isWellFormed() = auctionData()?.name != null

    fun categories() = tags.hashtags()

    /**
     * The stall this is sold from: NIP-15 makes a stall's `d` tag equal its id, so `stall_id` on
     * the same merchant names its kind-30017 address.
     */
    fun stallAddress(): Address? = auctionData()?.stallId?.let { Address(StallEvent.KIND, pubKey, it) }

    override fun indexableContent() = auctionData()?.let { (listOfNotNull(it.name, it.description) + tags.hashtags()).joinToString("\n") } ?: ""

    // The read path. The parse happens once and its fields are handed over one by
    // one; a scan that stops on the first hit never pays for the rest of the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        val data = auctionData() ?: return
        if (!visitor.visit(data.name)) return
        if (!visitor.visit(data.description)) return
        tags.hashtags().forEach { if (!visitor.visit(it)) return }
    }

    companion object {
        const val KIND = 30020

        fun build(
            auction: AuctionData,
            categories: List<String>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AuctionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, JsonMapper.toJson(auction), createdAt) {
            dTag(auction.id)
            categories?.let { hashtags(it) }
            initializer()
        }
    }
}
