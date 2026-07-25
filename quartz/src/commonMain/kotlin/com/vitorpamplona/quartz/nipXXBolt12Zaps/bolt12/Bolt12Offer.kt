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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12

/**
 * A parsed BOLT12 offer (`lno1...`). Exposes the fields NIP-XX cares about; the
 * full TLV stream is retained for callers that need more.
 *
 * See https://github.com/lightning/bolts/blob/master/12-offer-encoding.md#offer-fields
 */
class Bolt12Offer(
    val tlv: TlvStream,
) {
    /** `offer_issuer_id` (type 22): the 33-byte compressed node id that signs invoices for this offer, if present. */
    fun issuerId(): ByteArray? = tlv.value(TYPE_ISSUER_ID)

    /** `offer_amount` (type 8): the offer amount in the offer currency's minimal unit (msats when no currency). */
    fun amount(): Long? = tlv.tu64(TYPE_AMOUNT)

    /** `offer_currency` (type 6): ISO 4217 code; absent means bitcoin (msats). */
    fun currency(): String? = tlv.value(TYPE_CURRENCY)?.decodeToString()

    /** `offer_description` (type 10). */
    fun description(): String? = tlv.value(TYPE_DESCRIPTION)?.decodeToString()

    fun hasPaths(): Boolean = tlv.has(TYPE_PATHS)

    companion object {
        const val TYPE_CHAINS = 2L
        const val TYPE_METADATA = 4L
        const val TYPE_CURRENCY = 6L
        const val TYPE_AMOUNT = 8L
        const val TYPE_DESCRIPTION = 10L
        const val TYPE_FEATURES = 12L
        const val TYPE_ABSOLUTE_EXPIRY = 14L
        const val TYPE_PATHS = 16L
        const val TYPE_ISSUER = 18L
        const val TYPE_QUANTITY_MAX = 20L
        const val TYPE_ISSUER_ID = 22L

        fun parse(canonicalOffer: String): Bolt12Offer? {
            val bytes = Bolt12Bech32.decodeToBytesOrNull(canonicalOffer, Bolt12Bech32.OFFER_HRP) ?: return null
            val tlv = TlvStream.readOrNull(bytes) ?: return null
            return Bolt12Offer(tlv)
        }
    }
}
