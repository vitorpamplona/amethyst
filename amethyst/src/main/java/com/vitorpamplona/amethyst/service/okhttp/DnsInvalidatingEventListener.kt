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
package com.vitorpamplona.amethyst.service.okhttp

import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException

/**
 * Drops a host's [SurgeDns] entry whenever an OkHttp call to it fails outright. We hook
 * `callFailed` (final-stage signal after OkHttp has tried every address from the DNS lookup)
 * rather than per-attempt `connectFailed`: a multi-A-record host with one bad IP fires the
 * latter while OkHttp recovers via the next address, and we don't want to invalidate the cache
 * just because the first IP was unreachable.
 *
 * Used by the relay client. The media path uses [MediaCallEventListener], which folds the same
 * invalidation into its `finish` method alongside its existing timing logging.
 */
class DnsInvalidatingEventListener(
    private val dns: SurgeDns,
) : EventListener() {
    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) {
        dns.invalidate(call.request().url.host)
    }

    /** Per-client factory. The listener is stateless, so the same instance serves every call. */
    class Factory(
        dns: SurgeDns,
    ) : EventListener.Factory {
        private val listener = DnsInvalidatingEventListener(dns)

        override fun create(call: Call): EventListener = listener
    }
}
