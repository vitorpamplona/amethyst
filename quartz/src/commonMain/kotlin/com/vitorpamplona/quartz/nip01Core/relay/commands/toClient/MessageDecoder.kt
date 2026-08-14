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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper

/**
 * Turns one raw relay frame into a [Message]. The strategy the relay client
 * uses for its per-frame decode step — swap it to change how (or whether) a
 * frame is parsed without touching the connection machinery.
 *
 * Implementations may throw on malformed frames (the caller logs and drops
 * the frame, matching [OptimizedJsonMapper.fromJsonToMessage] behavior).
 */
fun interface MessageDecoder {
    fun decode(text: String): Message

    /**
     * Ages out whatever the decoder is holding. Call periodically: an entry must
     * survive at most two calls, so a 30s tick keeps nothing older than ~60s.
     *
     * A caching decoder's value decays with time — a duplicate that has not arrived
     * within seconds is not going to — while its memory does not decay at all. Aging
     * on a clock rather than on idleness is deliberate: relays keep pushing a trickle
     * of events down open subscriptions forever, so a decoder is never idle in the
     * sense of "saw no frames", and an idle-triggered release would never fire.
     *
     * Never affects correctness: dropping a cached id can only cost a re-parse.
     */
    fun ageOutCache() = Unit

    /** Releases everything cached, e.g. when the host is shutting the client down. */
    fun clearCache() = Unit

    companion object {
        /** Plain full-JSON parse of every frame. */
        val Default: MessageDecoder = MessageDecoder { OptimizedJsonMapper.fromJsonToMessage(it) }
    }
}
