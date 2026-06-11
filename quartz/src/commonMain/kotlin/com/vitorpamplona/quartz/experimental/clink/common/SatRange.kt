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
package com.vitorpamplona.quartz.experimental.clink.common

import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable

/** Inclusive sats range returned with "Invalid Amount" errors so the payer can correct and retry. */
class SatRange(
    var min: Long? = null,
    var max: Long? = null,
) : OptimizedSerializable

/** Timing detail attached to GFY "Expired Request" (code 3) errors. */
class GfyDelta(
    var max_delta_ms: Long? = null,
    var actual_delta_ms: Long? = null,
) : OptimizedSerializable

/**
 * Shared "GFY" failure codes used by Debits (21002) and Manage (21003).
 * The accompanying payload may carry [SatRange] (5), `retry_after` (4), or [GfyDelta] (3).
 */
object GfyErrorCode {
    const val REQUEST_DENIED = 1
    const val TEMPORARY_FAILURE = 2
    const val EXPIRED_REQUEST = 3
    const val RATE_LIMITED = 4
    const val INVALID_AMOUNT = 5
    const val INVALID_REQUEST = 6
}
