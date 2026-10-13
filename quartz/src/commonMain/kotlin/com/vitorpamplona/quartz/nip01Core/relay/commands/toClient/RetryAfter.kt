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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

/**
 * The back-off a relay attaches to a `rate-limited:` refusal (NIP-01, nostr-protocol/nips#2498).
 *
 * `OK` and `CLOSED` carry an optional trailing `params` string whose meaning depends on the
 * reason's machine-readable prefix:
 *
 * ```
 * ["OK", "b1a649ebe8...", false, "rate-limited: slow down there chief", "1000"]
 * ["CLOSED", "sub1", "rate-limited: slow down there chief", "2500"]
 * ```
 *
 * For `rate-limited` it is a positive integer of milliseconds the client waits before sending the
 * relay another message of the type that was refused (another `EVENT` after an `OK`, another `REQ`
 * after a `CLOSED`). The value may be approximate. A `rate-limited:` refusal WITHOUT it is not an
 * invitation to retry at once: the client keeps backing off by its own rules.
 */
object RetryAfter {
    /**
     * The milliseconds to back off that [params] states for a refusal whose reason is [message],
     * or null when the reason is not `rate-limited:` or [params] is absent or not a positive
     * integer. Lenient on what it accepts (leading zeros, a value the relay sent as a JSON number),
     * since a wrong guess here only costs a wait the relay asked for anyway.
     */
    fun parse(
        message: String,
        params: String?,
    ): Long? {
        if (params == null) return null
        if (MachineReadablePrefix.parse(message) != MachineReadablePrefix.RATE_LIMITED) return null
        return parseMillis(params)
    }

    /** [params] as a positive millisecond count, or null when it is not one. */
    fun parseMillis(params: String): Long? {
        if (params.isEmpty() || params.length > 18) return null
        for (c in params) if (c !in '0'..'9') return null
        val ms = params.toLong()
        return if (ms > 0) ms else null
    }

    /**
     * [millis] as the `params` a `rate-limited:` refusal carries: the decimal string of a positive
     * integer with no leading zeros. Values below one millisecond round up to `"1"`, because the
     * spec has no way to say "no wait" other than leaving the field out.
     */
    fun format(millis: Long): String = millis.coerceAtLeast(1).toString()
}
