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
package com.vitorpamplona.quartz.nipBCOnchainZaps.zap

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.toATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.toETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-BC: Onchain Zaps.
 *
 * Kind 8333 event that attributes a Bitcoin onchain payment to a Nostr event or profile.
 * The recipient's Nostr pubkey is used directly as the internal key of a BIP-341 P2TR
 * output, so every Nostr pubkey has exactly one corresponding mainnet Taproot address.
 *
 * Mainnet only.
 */
@Immutable
class OnchainZapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    /** The Bitcoin transaction id (64-char lowercase hex) parsed from the `i` tag. */
    fun txid() = tags.txid()

    /** Sender-claimed amount in satoshis. Must be verified against the on-chain transaction. */
    fun claimedAmountInSats() = tags.amountInSats()

    /** The hex-encoded pubkey of the recipient (the author being paid). */
    fun recipient() = tags.firstNotNullOfOrNull(PTag::parseKey)

    /** The event being zapped, if any. */
    fun zappedEvent() = tags.firstNotNullOfOrNull(ETag::parseId)

    /** The addressable event being zapped, if any. */
    fun zappedAddress() = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    /** Optional block tag with hash + height enabling SPV verification. */
    fun block() = tags.block()

    /** Optional inline SPV proof (raw tx hex + merkle proof hex). */
    fun proof() = tags.proof()

    /** True when neither `e` nor `a` is present — the zap targets the recipient's profile. */
    fun isProfileZap() = zappedEvent() == null && zappedAddress() == null

    companion object {
        const val KIND = 8333

        /** NIP-31 human-readable fallback. Includes the amount, as in the NIP-BC example. */
        fun altDescription(amountInSats: Long) = "Onchain zap: $amountInSats sats"

        /**
         * Build an onchain zap that targets a specific event.
         */
        fun build(
            txid: String,
            recipientPubKey: HexKey,
            amountInSats: Long,
            zappedEvent: EventHintBundle<out Event>,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<OnchainZapEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            alt(altDescription(amountInSats))
            txid(txid)
            recipient(recipientPubKey)
            amountInSats(amountInSats)
            if (zappedEvent.event is AddressableEvent) {
                zappedAddress(zappedEvent.toATag())
            }
            zappedEvent(zappedEvent.toETag())
            zappedKind(zappedEvent.event.kind)
            initializer()
        }

        /**
         * Build an onchain zap that targets a recipient's profile (no event / address).
         */
        fun buildProfileZap(
            txid: String,
            recipientPubKey: HexKey,
            amountInSats: Long,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<OnchainZapEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            alt(altDescription(amountInSats))
            txid(txid)
            recipient(recipientPubKey)
            amountInSats(amountInSats)
            initializer()
        }
    }
}
