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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage

/**
 * NIP-FE status codes. The status of an answer is decided by its first
 * frame: an accepting one is 200 and the answer may stream; a refusal's
 * NIP-01 machine-readable prefix picks the code. After the first frame is
 * out the status cannot change, so later failures are frames.
 */
object HttpRelayStatus {
    const val OK = 200
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val PAYLOAD_TOO_LARGE = 413
    const val TOO_MANY_REQUESTS = 429
    const val INTERNAL_ERROR = 500
    const val UNAVAILABLE = 503

    /** The status an answer opening with [message] carries. A raw EVENT frame opens a 200. */
    fun of(message: Message?): Int =
        when (message) {
            is ClosedMessage -> forReason(message.message)
            is NegErrMessage -> forReason(message.reason)
            // A duplicate is already stored, which is what the caller asked for, whichever flag the store set.
            is OkMessage -> if (message.success || message.message.startsWith("duplicate:")) OK else forReason(message.message)
            is NoticeMessage -> BAD_REQUEST
            else -> OK
        }

    /** The status a NIP-01 machine-readable prefix stands for. */
    fun forReason(reason: String): Int =
        when (reason.substringBefore(':', "")) {
            "auth-required" -> UNAUTHORIZED
            "restricted", "blocked" -> FORBIDDEN
            "rate-limited" -> TOO_MANY_REQUESTS
            "error" -> INTERNAL_ERROR
            else -> BAD_REQUEST
        }
}
