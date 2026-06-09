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

/**
 * How a CLINK Offer is priced, encoded in TLV field 3 of a `noffer` pointer.
 *
 * When the field is absent the pointer defaults to [SPONTANEOUS] (payer chooses
 * the amount), per the CLINK Offers spec.
 */
enum class OfferPriceType(
    val code: Int,
) {
    /** A fixed amount the payer must match; the amount travels in TLV 4. */
    FIXED(0),

    /** A fiat-denominated amount that the service converts to sats at request time. */
    VARIABLE(1),

    /** No preset amount; the payer specifies `amount_sats` in the request. */
    SPONTANEOUS(2),
    ;

    companion object {
        fun fromCode(code: Int): OfferPriceType? = entries.firstOrNull { it.code == code }
    }
}
