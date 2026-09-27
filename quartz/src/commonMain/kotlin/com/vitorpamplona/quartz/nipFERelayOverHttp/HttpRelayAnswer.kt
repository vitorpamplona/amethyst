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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message

/**
 * A NIP-FE answer as the client got it. [complete] is false when the body stopped before the frame
 * that ends the command's answer: what came is a prefix, and a REQ's prefix looks exactly like a
 * short result, so a caller MUST NOT read an incomplete answer as the whole one.
 */
class HttpRelayAnswer(
    val status: Int,
    /** The frame the answer ended on (EOSE, CLOSED, COUNT, OK, NOTICE), or the last one read when cut off. */
    val last: Message?,
    /** Whether the answer reached its end, rather than being cut off. */
    val complete: Boolean,
    /** The `Retry-After` the relay sent with a 429 or 503. */
    val retryAfter: String? = null,
)

/**
 * NIP-FE, client side: reads an answer one line at a time as it arrives and keeps track of whether
 * it ended where [command]'s answer ends. A 200 streams up to that frame; any other status is one
 * refusal line, which is the whole answer whatever frame it is.
 */
class HttpRelayAnswerReader(
    val command: HttpRelayCommand,
    val status: Int,
) {
    var last: Message? = null
        private set

    private var lines = 0
    private var ended = false
    private var broken = false

    /**
     * The frame on [line], or null for a blank line. A line that does not parse, or anything after
     * the answer ended, marks the answer incomplete: the body is not what this NIP says it is.
     */
    fun read(line: String): Message? {
        if (line.isBlank()) return null
        if (ended || broken) {
            broken = true
            return null
        }
        val message =
            try {
                OptimizedJsonMapper.fromJsonToMessage(line)
            } catch (_: Exception) {
                broken = true
                return null
            }
        lines++
        last = message
        ended = status != HttpRelayStatus.OK || command.ends(message)
        return message
    }

    /** Whether the body read so far is a whole answer. Asked once the body ends. */
    val complete: Boolean get() = ended && !broken && (status == HttpRelayStatus.OK || lines == 1)

    fun answer(retryAfter: String? = null) = HttpRelayAnswer(status, last, complete, retryAfter)
}
