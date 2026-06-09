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
package com.vitorpamplona.quartz.experimental.clink.offers

import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable

/**
 * Decrypted request payload sent to a CLINK Offers service (kind 21001) to obtain a
 * fresh BOLT-11 invoice. `offer` echoes the pointer's offer-id; `amount_sats` is
 * required for spontaneous/variable offers. `description` is capped at 100 chars.
 */
class OfferRequest(
    var offer: String? = null,
    var amount_sats: Long? = null,
    var payer_data: Map<String, Any?>? = null,
    var zap: String? = null,
    var expires_in_seconds: Long? = null,
    var description: String? = null,
) : OptimizedSerializable

/**
 * Decrypted response from a CLINK Offers service. Either an invoice ([bolt11] set) or
 * an error ([code] set). On "Expired or Moved" (code 3) the service may include
 * [latest] with a replacement `noffer1…`; on "Invalid Amount" (code 5) it includes [range].
 */
class OfferResponse(
    var bolt11: String? = null,
    var error: String? = null,
    var code: Int? = null,
    var range: SatRange? = null,
    var latest: String? = null,
) : OptimizedSerializable {
    fun isSuccess(): Boolean = bolt11 != null
}

/** Optional post-settlement receipt (kind 21001). `preimage` is absent for internal settlements. */
class OfferReceipt(
    var res: String? = null,
    var preimage: String? = null,
) : OptimizedSerializable

/** Error codes returned by a CLINK Offers service in [OfferResponse.code]. */
object OfferErrorCode {
    const val INVALID_OFFER = 1
    const val TEMPORARY_FAILURE = 2
    const val EXPIRED_OR_MOVED = 3
    const val UNSUPPORTED_FEATURE = 4
    const val INVALID_AMOUNT = 5
}
