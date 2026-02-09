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
package com.vitorpamplona.amethyst.commons.services.nwc

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks pending NIP-47 (Nostr Wallet Connect) payment requests awaiting responses.
 *
 * Shared between Android and Desktop to provide consistent payment tracking behavior.
 * Platform-specific caches (LocalCache, DesktopLocalCache) delegate to this tracker
 * for the core request/response matching logic.
 *
 * Flow:
 * 1. When sending payment request: [registerRequest] stores callback
 * 2. When response arrives: [onResponseReceived] retrieves and removes pending request
 * 3. Caller invokes callback and links notes via Note.addZapPayment()
 */
class NwcPaymentTracker {
    /**
     * Data for a pending payment request.
     *
     * @property zappedNote The note being zapped, if payment is for a zap
     * @property onResponse Callback to invoke when wallet responds
     */
    data class PendingRequest(
        val zappedNote: Note?,
        val onResponse: suspend (LnZapPaymentResponseEvent) -> Unit,
    )

    private val awaitingRequests = ConcurrentHashMap<HexKey, PendingRequest>(10)

    /**
     * Registers a pending payment request.
     *
     * @param requestId Event ID of the LnZapPaymentRequestEvent
     * @param zappedNote The note being zapped (null if not a zap payment)
     * @param onResponse Callback invoked when response arrives
     */
    fun registerRequest(
        requestId: HexKey,
        zappedNote: Note?,
        onResponse: suspend (LnZapPaymentResponseEvent) -> Unit,
    ) {
        awaitingRequests[requestId] = PendingRequest(zappedNote, onResponse)
    }

    /**
     * Called when a payment response event is received.
     * Retrieves and removes the pending request for the given request ID.
     *
     * @param requestId The 'e' tag from the response, pointing to original request
     * @return PendingRequest if found, null otherwise
     */
    fun onResponseReceived(requestId: HexKey?): PendingRequest? {
        if (requestId == null) return null
        return awaitingRequests.remove(requestId)
    }

    /**
     * Checks if there's a pending request for the given ID.
     */
    fun hasPendingRequest(requestId: HexKey): Boolean = awaitingRequests.containsKey(requestId)

    /**
     * Manually removes a pending request (e.g., on timeout).
     */
    fun cleanup(requestId: HexKey) {
        awaitingRequests.remove(requestId)
    }

    /**
     * Returns count of pending requests (for debugging/monitoring).
     */
    fun pendingCount(): Int = awaitingRequests.size
}
