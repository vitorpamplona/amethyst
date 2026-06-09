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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.tlv.Tlv
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder

/**
 * CLINK Offers pointer (`noffer1…`, kind 21001): a static payment code that lets a
 * payer request a fresh BOLT-11 invoice over Nostr — the Nostr-native analogue of
 * LNURL-Pay. See https://github.com/shocknet/clink/blob/master/specs/clink-offers.md
 */
@Immutable
data class NOffer(
    override val pubKey: HexKey,
    override val relays: List<NormalizedRelayUrl>,
    override val pointer: String?,
    /** TLV 3 — how the offer is priced. Absent means [OfferPriceType.SPONTANEOUS]. */
    val priceType: OfferPriceType?,
    /** TLV 4 — price in sats (display/fixed offers), 4-byte big-endian per the SDK. */
    val price: Int?,
) : ClinkPointer {
    override fun encode(): String =
        TlvBuilder()
            .apply {
                addHex(ClinkTlv.PUBKEY, pubKey)
                relays.forEach { addStringIfNotNull(ClinkTlv.RELAY, it.url) }
                addStringIfNotNull(ClinkTlv.POINTER, pointer)
                priceType?.let { addHex(ClinkTlv.PRICE_TYPE, it.code.toSingleByteHex()) }
                price?.let { addInt(ClinkTlv.PRICE, it) }
            }.build()
            .let { Bech32.encodeBytes(HRP, it, Bech32.Encoding.Bech32) }

    companion object {
        const val HRP = "noffer"

        fun parse(bytes: ByteArray): NOffer? {
            if (bytes.isEmpty()) return null

            val tlv = Tlv.parse(bytes)

            val pubKey = tlv.firstAsHex(ClinkTlv.PUBKEY) ?: return null
            if (pubKey.isBlank()) return null

            val relays = tlv.asString(ClinkTlv.RELAY)?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } ?: emptyList()
            val pointer = tlv.firstAsString(ClinkTlv.POINTER)
            val priceType =
                tlv.data[ClinkTlv.PRICE_TYPE]?.firstOrNull()?.firstOrNull()?.let {
                    OfferPriceType.fromCode(it.toInt() and 0xFF)
                }
            val price = tlv.firstAsInt(ClinkTlv.PRICE)

            return NOffer(pubKey, relays, pointer, priceType, price)
        }
    }
}
