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
package com.vitorpamplona.amethyst.commons.service.broadcast

import androidx.compose.runtime.Immutable

/**
 * Result of a relay's response to an event publish.
 */
@Immutable
sealed class RelayResult {
    /** Relay accepted the event (OK message with success=true) */
    data object Success : RelayResult()

    /** Relay rejected the event (OK message with success=false) */
    data class Error(
        val message: String,
    ) : RelayResult()

    /** Relay did not respond within timeout */
    data object Timeout : RelayResult()

    /** Waiting for relay response */
    data object Pending : RelayResult()

    /** Retry in progress for this relay */
    data object Retrying : RelayResult()
}

/**
 * Overall status of a broadcast operation.
 */
enum class BroadcastStatus {
    /** Currently waiting for relay responses */
    IN_PROGRESS,

    /** All relays accepted the event */
    SUCCESS,

    /** Some relays accepted, some failed */
    PARTIAL,

    /** No relays accepted the event */
    FAILED,
}
