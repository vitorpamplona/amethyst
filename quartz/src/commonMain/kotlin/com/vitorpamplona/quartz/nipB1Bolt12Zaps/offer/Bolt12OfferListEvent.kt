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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.tags.OfferTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-B1: BOLT12 Zaps — a user's **BOLT12 offer list** (kind 10058).
 *
 * A replaceable event, anchored to the author's pubkey, that publishes one or more
 * canonical raw BOLT12 offers (`["offer", "lno1..."]`). This is how a payer
 * discovers a recipient's offer before sending a BOLT12 zap — the on-Nostr
 * analogue of the `lud16` metadata field NIP-57 lightning zaps use, and the thing
 * that ties an offer to a Nostr identity (the author's signature).
 *
 * Fetch a recipient's latest 10058 and pick one of its offers; when more than one
 * is present, a payer MAY select any it supports. Content SHOULD be empty.
 */
@Immutable
class Bolt12OfferListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** All canonical raw BOLT12 offers (`lno1...`) this user publishes, in tag order. */
    fun offers(): List<String> = tags.mapNotNull(OfferTag::parse)

    /** The first valid offer, or null when the list carries none. */
    fun firstOffer(): String? = tags.firstNotNullOfOrNull(OfferTag::parse)

    companion object {
        const val KIND = 10058

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        fun createTagArray(offers: List<String>): Array<Array<String>> =
            offers
                .map { OfferTag.assemble(it) }
                .toTypedArray()

        /** Replace the offer set, preserving any non-offer tags an earlier version carried. */
        suspend fun updateOffers(
            earlierVersion: Bolt12OfferListEvent,
            offers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): Bolt12OfferListEvent {
            val tags =
                earlierVersion.tags
                    .filter { !OfferTag.isTag(it) }
                    .plus(offers.map { OfferTag.assemble(it) })
                    .toTypedArray()

            return signer.sign(createdAt, KIND, tags, earlierVersion.content)
        }

        suspend fun create(
            offers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): Bolt12OfferListEvent = signer.sign(createdAt, KIND, createTagArray(offers), "")

        fun create(
            offers: List<String>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): Bolt12OfferListEvent = signer.sign(createdAt, KIND, createTagArray(offers), "")
    }
}
