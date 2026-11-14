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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.lifecycle.ViewModel

/**
 * The front page's off-screen browser, held by its screen's back-stack entry rather than by the
 * composition: a tab the reader leaves drops out of the composition but stays on the back stack, so
 * the page keeps its scroll position (and its loaded art) until they come back. Closed when the
 * entry goes (a pop), when another edition or a Retry replaces it, or by the host when the account
 * leaves, after which the next [open] starts a new one.
 *
 * Touched only from composition and the ViewModel's own clearing, on the main thread.
 */
internal class HeldPaperSession(
    private val host: DesktopNappletHost,
) : ViewModel() {
    private class Held(
        val key: String,
        val session: CefNappletSession,
    )

    private var held: Held? = null

    /** The open session for this edition: the held one when it still shows the same page, else a new one. */
    fun open(
        instanceId: String,
        html: String,
        title: String,
    ): Result<CefNappletSession> {
        val key = "$instanceId|$title|${html.length}|${html.hashCode()}"
        held?.let {
            if (it.key == key && !it.session.isClosed) return Result.success(it.session)
            host.closeInline(it.session)
        }
        held = null
        return host.openInlinePaper(html, title, instanceId).onSuccess { held = Held(key, it) }
    }

    override fun onCleared() {
        held?.let { host.closeInline(it.session) }
        held = null
    }
}
