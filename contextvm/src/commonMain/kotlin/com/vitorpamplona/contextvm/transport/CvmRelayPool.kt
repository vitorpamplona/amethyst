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
package com.vitorpamplona.contextvm.transport

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** A live subscription. Closing it stops delivery. */
interface CvmSubscription {
    fun close()
}

/**
 * The relay surface ContextVM needs.
 *
 * Deliberately tiny and transport-agnostic: everything above it is pure
 * protocol, and the Tier C fixture implements this same interface to play a
 * misbehaving peer without any network. The production binding wraps quartz's
 * relay client.
 */
interface CvmRelayPool {
    /**
     * Subscribes to events addressed to [pubKey] (`#p`) of the given [kinds].
     *
     * Delivery starts when this returns. Because kind 25910 is ephemeral, a
     * subscription opened after a peer published has missed the event
     * permanently — [CvmTransport] is built so callers cannot make that mistake.
     */
    fun subscribe(
        pubKey: HexKey,
        kinds: IntArray,
        onEvent: (Event) -> Unit,
    ): CvmSubscription

    /** Publishes [event] to the configured relays. */
    suspend fun publish(event: Event)
}
