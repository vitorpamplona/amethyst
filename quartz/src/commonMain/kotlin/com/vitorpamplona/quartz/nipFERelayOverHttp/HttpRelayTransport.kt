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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * How [HttpRelayClient] reaches a relay over HTTP: one POST, its response read line by line. The
 * NIP-FE side of the exchange (which URL, what body, signing, reading the answer) stays in the
 * client; an implementation only moves bytes, the way a
 * [com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder] does for [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient].
 */
interface HttpRelayTransport {
    /**
     * POSTs [body] to [url], [relay]'s HTTP URL, with an `Authorization` header when [authorization]
     * is not null. Calls [onStatus] once with the status and any `Retry-After`, then [onLine] for each
     * body line as it arrives, and returns when the body ends. A connection that drops mid-body
     * returns normally: the answer reader tells a cut-off answer from a whole one. Cancelling the
     * caller cancels the request.
     */
    suspend fun post(
        relay: NormalizedRelayUrl,
        url: String,
        body: ByteArray,
        authorization: String?,
        onStatus: (status: Int, retryAfter: String?) -> Unit,
        onLine: (String) -> Unit,
    )
}
