/**
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
package com.vitorpamplona.quartz.nip01Core.relay.client.stats

import androidx.collection.LruCache
import com.vitorpamplona.quartz.utils.TimeUtils

class RelayStat(
    var receivedBytes: Int = 0,
    var sentBytes: Int = 0,
    var spamCounter: Int = 0,
    var errorCounter: Int = 0,
    var pingInMs: Int = 0,
    var compression: Boolean = false,
) {
    val messages = LruCache<RelayDebugMessage, RelayDebugMessage>(100)

    fun newNotice(notice: String?) {
        val debugMessage =
            RelayDebugMessage(
                type = RelayDebugMessageType.NOTICE,
                message = notice ?: "No error message provided",
            )

        messages.put(debugMessage, debugMessage)
    }

    fun newError(error: String?) {
        errorCounter++

        val debugMessage =
            RelayDebugMessage(
                type = RelayDebugMessageType.ERROR,
                message = error ?: "No error message provided",
            )

        messages.put(debugMessage, debugMessage)
    }

    fun addBytesReceived(bytesUsedInMemory: Int) {
        receivedBytes += bytesUsedInMemory
    }

    fun addBytesSent(bytesUsedInMemory: Int) {
        sentBytes += bytesUsedInMemory
    }

    fun newSpam(spamDescriptor: String) {
        spamCounter++

        val debugMessage =
            RelayDebugMessage(
                type = RelayDebugMessageType.SPAM,
                message = spamDescriptor,
            )

        messages.put(debugMessage, debugMessage)
    }
}

class RelayDebugMessage(
    val type: RelayDebugMessageType,
    val message: String,
    val time: Long = TimeUtils.now(),
)

enum class RelayDebugMessageType {
    SPAM,
    NOTICE,
    ERROR,
}
