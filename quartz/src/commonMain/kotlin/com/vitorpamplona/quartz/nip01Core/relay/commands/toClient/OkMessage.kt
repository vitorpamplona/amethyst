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

import com.vitorpamplona.quartz.nip01Core.core.HexKey

class OkMessage(
    val eventId: HexKey,
    val success: Boolean,
    val message: String,
    /**
     * The optional fifth element: a string whose meaning depends on [message]'s
     * machine-readable prefix. For `rate-limited:` it is the back-off in
     * milliseconds — read it with [retryAfterMs]. Null when the relay sent the
     * four-element form.
     */
    val params: String? = null,
) : Message {
    override fun label() = LABEL

    /**
     * How long the relay asked this client to wait before sending it another
     * `EVENT`, when it refused this one as `rate-limited:` with a back-off. See
     * [RetryAfter].
     */
    val retryAfterMs: Long? get() = RetryAfter.parse(message, params)

    /**
     * Wire form is `["OK","<eventId>",<true|false>,"<message>"]`, plus a
     * trailing `"<params>"` when there are any — sent once per published
     * EVENT. [eventId] is validated hex (always escape-free); splice directly
     * when [message] and [params] also need no escaping, which covers the
     * empty-string success ack, the plain-ASCII rejection reasons and a
     * numeric back-off. Byte-identical output; a reason with quotes or
     * non-ASCII falls back to the generic serializer.
     */
    override fun toJson(): String {
        if (!isEscapeFreeAscii(message)) return super.toJson()
        if (params != null && !isEscapeFreeAscii(params)) return super.toJson()
        return buildString(eventId.length + message.length + (params?.length ?: 0) + 24) {
            append("[\"OK\",\"")
            append(eventId)
            append(if (success) "\",true,\"" else "\",false,\"")
            append(message)
            if (params != null) {
                append("\",\"")
                append(params)
            }
            append("\"]")
        }
    }

    companion object {
        const val LABEL = "OK"

        /** A successful `OK true` for [eventId], with an optional human-readable note. */
        fun accepted(
            eventId: HexKey,
            message: String = "",
        ) = OkMessage(eventId, true, message)

        /**
         * A rejecting `OK false` for [eventId] whose reason carries a
         * standardized [MachineReadablePrefix] (e.g. `auth-required: ...`).
         */
        fun rejected(
            eventId: HexKey,
            prefix: MachineReadablePrefix,
            message: String,
            params: String? = null,
        ) = OkMessage(eventId, false, prefix.format(message), params)

        /**
         * A `rate-limited:` `OK false` for [eventId] telling the client to wait
         * [retryAfterMs] before its next `EVENT`; null leaves the back-off out.
         */
        fun rateLimited(
            eventId: HexKey,
            message: String,
            retryAfterMs: Long? = null,
        ) = rejected(eventId, MachineReadablePrefix.RATE_LIMITED, message, retryAfterMs?.let(RetryAfter::format))
    }
}
