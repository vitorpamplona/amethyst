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

class ClosedMessage(
    val subId: String,
    val message: String,
    /**
     * The optional fourth element, in the same form as [OkMessage.params]: a
     * string whose meaning depends on [message]'s machine-readable prefix. For
     * `rate-limited:` it is the back-off in milliseconds — read it with
     * [retryAfterMs]. Null when the relay sent the three-element form.
     */
    val params: String? = null,
) : Message {
    override fun label() = LABEL

    /**
     * How long the relay asked this client to wait before sending it another
     * `REQ` (or `COUNT`), when it closed this one as `rate-limited:` with a
     * back-off. See [RetryAfter].
     */
    val retryAfterMs: Long? get() = RetryAfter.parse(message, params)

    companion object {
        const val LABEL = "CLOSED"

        /**
         * A `CLOSED` for [subId] whose reason carries a standardized
         * [MachineReadablePrefix] (e.g. `auth-required: ...`).
         */
        fun of(
            subId: String,
            prefix: MachineReadablePrefix,
            message: String,
            params: String? = null,
        ) = ClosedMessage(subId, prefix.format(message), params)

        /**
         * A `rate-limited:` `CLOSED` for [subId] telling the client to wait
         * [retryAfterMs] before its next `REQ`; null leaves the back-off out.
         */
        fun rateLimited(
            subId: String,
            message: String,
            retryAfterMs: Long? = null,
        ) = of(subId, MachineReadablePrefix.RATE_LIMITED, message, retryAfterMs?.let(RetryAfter::format))
    }
}
