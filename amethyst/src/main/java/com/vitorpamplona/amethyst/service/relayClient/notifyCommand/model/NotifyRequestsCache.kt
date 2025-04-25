/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class NotifyRequestsCache {
    val transientPaymentRequestDismissals: MutableStateFlow<Set<NotifyRequest>> = MutableStateFlow(emptySet())
    val transientPaymentRequests: MutableStateFlow<Set<NotifyRequest>> = MutableStateFlow(emptySet())

    fun addPaymentRequestIfNew(
        description: String,
        relayUrl: String,
    ) {
        addPaymentRequestIfNew(NotifyRequest(description, relayUrl))
    }

    fun addPaymentRequestIfNew(paymentRequest: NotifyRequest) {
        if (
            !this.transientPaymentRequests.value.contains(paymentRequest) &&
            !this.transientPaymentRequestDismissals.value.contains(paymentRequest)
        ) {
            this.transientPaymentRequests.value += paymentRequest
        }
    }

    fun dismissPaymentRequest(request: NotifyRequest) {
        if (this.transientPaymentRequests.value.contains(request)) {
            this.transientPaymentRequests.update { it - request }
            this.transientPaymentRequestDismissals.update { it + request }
        }
    }
}
