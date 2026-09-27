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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message

/**
 * Where a [RelaySession]'s frames go. The engine hands over a [Message]
 * wherever it has one, so a transport that must react to a frame (end an
 * HTTP answer at its EOSE, map a CLOSED onto a status) reads the type
 * instead of re-parsing wire JSON; stored and live EVENT frames, which the
 * zero-decode REQ path builds as text, arrive through [raw].
 *
 * Both are called from the engine's coroutines, on any thread, and must not
 * block. Deliberately not a `fun interface`: a lambda would be ambiguous with
 * the `(String) -> Unit` overloads that predate it.
 */
interface SessionSink {
    /** A frame the engine built as a [Message]. */
    fun message(message: Message)

    /** A frame already in wire JSON: an `EVENT` spliced from stored or live text. */
    fun raw(json: String)

    companion object {
        /** Every frame to [send] as wire JSON, which is all a socket wants. */
        fun of(send: (String) -> Unit): SessionSink =
            object : SessionSink {
                // NegMsgMessage overrides toJson with a direct-build wire path
                // (identical output, ~2× faster on big reconcile frames).
                override fun message(message: Message) = send(message.toJson())

                override fun raw(json: String) = send(json)
            }
    }
}
