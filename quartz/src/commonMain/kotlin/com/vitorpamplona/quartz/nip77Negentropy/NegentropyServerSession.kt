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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.Hex

/**
 * Server-side handler for NIP-77 negentropy reconciliation.
 *
 * Used when acting as a relay (or relay-relay sync) to respond to
 * incoming NEG-OPEN and NEG-MSG from a client.
 *
 * Usage:
 * 1. On NEG-OPEN: create a [NegentropyServerSession] with the matching local events
 * 2. Call [processMessage] with the initial hex message from NEG-OPEN
 * 3. Send back the resulting [NegMsgMessage]
 * 4. On subsequent NEG-MSG: call [processMessage] again and send the response
 */
class NegentropyServerSession(
    val subId: String,
    localEvents: List<Event>,
    frameSizeLimit: Long = 0,
) {
    private val storage = StorageVector()
    private val negentropy: Negentropy

    init {
        for (event in localEvents) {
            storage.insert(event.createdAt, event.id)
        }
        storage.seal()
        negentropy = Negentropy(storage, frameSizeLimit)
    }

    fun processMessage(hexMessage: String): NegMsgMessage? {
        val msgBytes = Hex.decode(hexMessage)
        val result = negentropy.reconcile(msgBytes)
        return if (result.msg != null) {
            NegMsgMessage(
                subId = subId,
                message = Hex.encode(result.msg!!),
            )
        } else {
            null
        }
    }
}
