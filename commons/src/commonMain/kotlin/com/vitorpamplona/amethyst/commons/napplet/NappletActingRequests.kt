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
package com.vitorpamplona.amethyst.commons.napplet

/**
 * The napplet requests that ACT for the user — publish, pay, upload, notify, broadcast to other
 * napplets — as opposed to reading. A host holds these while its napplet is off-screen: pausing the
 * WebView stops animations and media but not JavaScript, so without this an "allow always" napplet
 * parked in the bottom bar could keep publishing or paying while the user looks elsewhere. Reads keep
 * flowing, so a preloaded napplet still fills itself in.
 */
object NappletActingRequests {
    private val ACTING =
        setOf(
            "relay.publish",
            "relay.publishEncrypted",
            "value.payInvoice",
            "upload.upload",
            "notify.create",
            "inc.emit",
        )

    fun actsForUser(requestType: String?): Boolean = requestType in ACTING
}
