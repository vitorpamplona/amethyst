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
package com.vitorpamplona.quartz.experimental.clink.debits

import com.vitorpamplona.quartz.experimental.clink.common.GfyDelta
import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable

/**
 * Decrypted request to a CLINK Debits service (kind 21002). Two shapes:
 *  - direct payment: [bolt11] (+ optional [amount_sats]); [k1] required for session pointers
 *  - budget approval: [amount_sats] + [frequency] (omit [frequency] for a one-time budget)
 */
class DebitRequest(
    var pointer: String? = null,
    var amount_sats: Long? = null,
    var bolt11: String? = null,
    var description: String? = null,
    var k1: String? = null,
    var frequency: DebitFrequency? = null,
) : OptimizedSerializable

/** Recurring-budget cadence; [unit] is one of `day`, `week`, `month`. */
class DebitFrequency(
    var number: Int? = null,
    var unit: String? = null,
) : OptimizedSerializable

/**
 * Decrypted response from a CLINK Debits service. [res] is `"ok"` on success (with
 * [preimage] for Lightning payouts, absent for internal settlements/budget approvals)
 * or `"GFY"` on failure, in which case [code] (see
 * [com.vitorpamplona.quartz.experimental.clink.common.GfyErrorCode]) and [error] are set.
 */
class DebitResponse(
    var res: String? = null,
    var preimage: String? = null,
    var code: Int? = null,
    var error: String? = null,
    var range: SatRange? = null,
    var retry_after: Long? = null,
    var delta: GfyDelta? = null,
) : OptimizedSerializable {
    fun isOk(): Boolean = res == OK

    companion object {
        const val OK = "ok"
        const val GFY = "GFY"
    }
}
