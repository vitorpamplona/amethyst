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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.Hex

/**
 * Manages a single NIP-77 negentropy reconciliation session with a relay.
 *
 * Usage:
 * 1. Create a NegentropySession with local events and a filter
 * 2. Call [open] to get the NEG-OPEN command
 * 3. Feed relay NEG-MSG responses via [processMessage]
 * 4. If [processMessage] returns a non-null [NegMsgCmd], send it back
 * 5. Repeat until [processMessage] returns a result with a null command (reconciliation complete)
 * 6. Use [haveIds] and [needIds] from the result to send/request events
 * 7. Call [close] to produce a NEG-CLOSE command when done
 */
class NegentropySession(
    val subId: String,
    val filter: Filter,
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

    fun open(): NegOpenCmd {
        val initialMessage = negentropy.initiate()
        return NegOpenCmd(
            subId = subId,
            filter = filter,
            initialMessage = Hex.encode(initialMessage),
        )
    }

    fun processMessage(hexMessage: String): ReconcileResult {
        val msgBytes = Hex.decode(hexMessage)
        val result = negentropy.reconcile(msgBytes)

        val haveIds = result.sendIds.map { it.toHexString() }
        val needIds = result.needIds.map { it.toHexString() }

        val nextCmd =
            if (result.msg != null) {
                NegMsgCmd(
                    subId = subId,
                    message = Hex.encode(result.msg!!),
                )
            } else {
                null
            }

        return ReconcileResult(
            nextCmd = nextCmd,
            haveIds = haveIds,
            needIds = needIds,
        )
    }

    fun close(): NegCloseCmd = NegCloseCmd(subId)
}

class ReconcileResult(
    val nextCmd: NegMsgCmd?,
    val haveIds: List<String>,
    val needIds: List<String>,
) {
    fun isComplete() = nextCmd == null
}
