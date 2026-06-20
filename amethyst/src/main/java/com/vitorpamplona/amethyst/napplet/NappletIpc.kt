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
package com.vitorpamplona.amethyst.napplet

/**
 * The Messenger wire contract between the untrusted `:napplet` process (the WebView host) and
 * the main-process [NappletBrokerService]. Kept tiny and string-only on purpose: nothing the
 * applet controls is ever interpreted as a Binder object, and the only payloads are JSON
 * strings ([NappletProtocolJson]) plus the applet's identity coordinate.
 */
object NappletIpc {
    /** Host → broker: a capability request. Carries [KEY_REQUEST_ID], the identity keys, and [KEY_PAYLOAD]. */
    const val MSG_REQUEST = 1

    /** Broker → host: the matching reply. Carries [KEY_REQUEST_ID] and [KEY_PAYLOAD]. */
    const val MSG_RESPONSE = 2

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_PAYLOAD = "payload"

    // Applet identity (the ledger coordinate) travels with every request — the host cannot be
    // trusted to have applied any policy, so the broker re-derives everything from these.
    const val KEY_AUTHOR = "author"
    const val KEY_IDENTIFIER = "identifier"
    const val KEY_AGGREGATE_HASH = "aggregateHash"

    /** Capability names (comma-separated) the manifest's `requires` resolved to. The broker
     *  refuses any request outside this set, regardless of consent state. */
    const val KEY_DECLARED = "declared"
}
