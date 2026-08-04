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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

/**
 * Whether a failure may be published as "this relay is unreachable".
 *
 * The distinction matters because the answer is PUBLISHED: a negative NIP-66
 * record is a signed, public statement about someone else's server. A relay
 * that completes a handshake and then hangs up mid-page is emphatically
 * reachable, and an exception thrown by the caller's own code says nothing
 * about the relay at all. So this asks only about the connection itself —
 * name resolution, routing, refusal, TLS.
 *
 * Unknown failures stay quiet: the cost of silence is one retry next cycle,
 * the cost of being wrong is a false record carrying the monitor's signature.
 */
object Unreachability {
    fun proves(e: Exception): Boolean =
        when (e) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException,
            is java.net.PortUnreachableException,
            is javax.net.ssl.SSLHandshakeException,
            -> true

            else -> false
        }
}
