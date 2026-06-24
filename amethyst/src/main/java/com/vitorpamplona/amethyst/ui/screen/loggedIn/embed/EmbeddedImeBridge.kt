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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

/**
 * A cross-process editable: the focused field lives in the embedded WebView (a different process/window
 * that can't host the soft keyboard), so the main app keeps the keyboard and relays editing across this
 * bridge. The host view ([RemoteImeView]) drives the keyboard from [ImeEvent]s and forwards every edit
 * op back via [sendImeOp]. Implemented by the controllers whose surface can take text input.
 */
interface EmbeddedImeBridge {
    /** Page → host: a field gained focus, the field blurred, or its text changed outside the keyboard. */
    var onImeEvent: ((ImeEvent) -> Unit)?

    /** Host → page: a single IME op as a JSON envelope (`{type:"ime.commit", ...}`). */
    fun sendImeOp(json: String)
}

/** What the focused page field reports up to the host keyboard. */
sealed interface ImeEvent {
    /** A field took focus; carries enough to configure the keyboard and seed the editing buffer. */
    data class Focus(
        val inputType: String,
        val enterKeyHint: String,
        val multiline: Boolean,
        val text: String,
        val selStart: Int,
        val selEnd: Int,
    ) : ImeEvent

    /** The field lost focus — dismiss the keyboard. */
    data object Blur : ImeEvent

    /** The page changed the field's text/selection itself; resync the keyboard's buffer. */
    data class State(
        val text: String,
        val selStart: Int,
        val selEnd: Int,
    ) : ImeEvent
}
