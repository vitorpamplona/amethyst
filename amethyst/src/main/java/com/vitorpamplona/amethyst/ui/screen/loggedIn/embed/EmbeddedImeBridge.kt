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

import org.json.JSONObject

/** Parses the `geom` object of an `ime.pagesel` payload into a [SelectionGeometry], or null if absent. */
fun parseSelectionGeometry(o: JSONObject?): SelectionGeometry? {
    if (o == null) return null
    return SelectionGeometry(
        left = o.optDouble("l", 0.0).toFloat(),
        top = o.optDouble("t", 0.0).toFloat(),
        right = o.optDouble("r", 0.0).toFloat(),
        bottom = o.optDouble("b", 0.0).toFloat(),
        startX = o.optDouble("sx", 0.0).toFloat(),
        startBottom = o.optDouble("sb", 0.0).toFloat(),
        endX = o.optDouble("ex", 0.0).toFloat(),
        endBottom = o.optDouble("eb", 0.0).toFloat(),
        viewportWidth = o.optDouble("vw", 0.0).toFloat(),
        caretX = if (o.has("cx")) o.optDouble("cx").toFloat() else null,
        caretTop = if (o.has("ct")) o.optDouble("ct").toFloat() else null,
        caretBottom = if (o.has("cb")) o.optDouble("cb").toFloat() else null,
    )
}

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
        val geometry: SelectionGeometry? = null,
    ) : ImeEvent

    /** The field lost focus — dismiss the keyboard. */
    data object Blur : ImeEvent

    /** The page changed the field's text/selection itself; resync the keyboard's buffer. */
    data class State(
        val text: String,
        val selStart: Int,
        val selEnd: Int,
        val geometry: SelectionGeometry? = null,
    ) : ImeEvent

    /**
     * Ordinary page text (not an input) gained or lost a selection. The embedded WebView can't present
     * Chrome's copy toolbar/handles in its cross-process surface, so the host draws its own over the page;
     * [text] is the selected text to put on the clipboard, [geometry] places the toolbar + drag handles.
     */
    data class PageSelection(
        val active: Boolean,
        val text: String,
        val geometry: SelectionGeometry?,
    ) : ImeEvent
}

/**
 * Selection geometry in the page's CSS px (viewport coords). The host scales by surface-width / [viewportWidth]
 * to screen px. [left]/[top]/[right]/[bottom] is the bounding box (toolbar anchors above it); ([startX],
 * [startBottom]) and ([endX], [endBottom]) are the caret feet where the two drag handles sit.
 */
data class SelectionGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val startX: Float,
    val startBottom: Float,
    val endX: Float,
    val endBottom: Float,
    val viewportWidth: Float,
    // Present only when the field's selection is a bare caret (no range): the caret rect, so the host can
    // show a draggable insertion handle below it. Null for a range or page-text selection.
    val caretX: Float? = null,
    val caretTop: Float? = null,
    val caretBottom: Float? = null,
)
