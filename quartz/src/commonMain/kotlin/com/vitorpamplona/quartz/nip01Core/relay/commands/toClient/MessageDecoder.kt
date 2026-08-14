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
import com.vitorpamplona.quartz.utils.TimeUtils

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
     * Releases whatever the decoder is holding if it has seen no frames for
     * [idleMillis]. Returns true when something was released.
     *
     * A caching decoder's value decays with time — a duplicate that has not
     * arrived in a minute is not going to — but its memory does not, so the
     * owner ticks this to let a quiet decoder drop what it cached. Stateless
     * decoders keep the no-op default.
     *
     * Never affects correctness: releasing a cache can only cost a re-parse.
     */
    fun trimIfIdle(
        idleMillis: Long,
        nowMillis: Long = TimeUtils.nowMillis(),
    ): Boolean = false

    companion object {
        /** Plain full-JSON parse of every frame. */
        val Default: MessageDecoder = MessageDecoder { OptimizedJsonMapper.fromJsonToMessage(it) }
    }
}
