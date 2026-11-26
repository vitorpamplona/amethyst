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
package com.vitorpamplona.amethyst.commons.ui.content

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where a document's footnotes and the text that calls them are, so a call (`¹`) scrolls to its
 * note and the note's `↩` back to the text. Each side registers a [BringIntoViewRequester], which
 * scrolls whatever holds the document (an article's page, a feed's lazy list, the chat) without
 * the document knowing it.
 *
 * A call is a link to `#fn:<number>` ([linkTo]), so it goes through the document's own link
 * handler like any other link.
 */
@Stable
class FootnoteTargets(
    private val scope: CoroutineScope,
    /** Room kept around a target, so a top bar drawn over the list doesn't cover it. */
    private val marginPx: Float,
) {
    private val notes = HashMap<Int, FootnoteAnchor>()
    private val calls = HashMap<Int, FootnoteAnchor>()

    fun registerNote(
        number: Int,
        anchor: FootnoteAnchor,
    ) {
        notes[number] = anchor
    }

    fun unregisterNote(
        number: Int,
        anchor: FootnoteAnchor,
    ) {
        if (notes[number] === anchor) notes.remove(number)
    }

    /** The first text to call a note is where its `↩` returns to; later calls don't move it. */
    fun registerCall(
        number: Int,
        anchor: FootnoteAnchor,
    ) {
        if (number !in calls) calls[number] = anchor
    }

    fun unregisterCall(
        number: Int,
        anchor: FootnoteAnchor,
    ) {
        if (calls[number] === anchor) calls.remove(number)
    }

    /** Scrolls to note [number]; false when this document has no such note on screen. */
    fun jumpToNote(number: Int): Boolean = jump(notes[number])

    /** Scrolls back to the text that first called note [number]. */
    fun jumpToCall(number: Int): Boolean = jump(calls[number])

    private fun jump(anchor: FootnoteAnchor?): Boolean {
        if (anchor == null) return false
        val size = anchor.size
        // The target and a margin on each side: scrolling up leaves room under a top bar,
        // scrolling down shows the whole note instead of stopping at its first line.
        val area = Rect(0f, -marginPx, size.width.toFloat(), size.height + marginPx)
        scope.launch { anchor.requester.bringIntoView(area) }
        return true
    }

    companion object {
        private const val PREFIX = "#fn:"

        fun linkTo(number: Int): String = PREFIX + number

        /** The note a link calls, or null when it is not a footnote call. */
        fun numberOf(uri: String): Int? = if (uri.startsWith(PREFIX)) uri.substring(PREFIX.length).toIntOrNull() else null
    }
}

/** Where a note or a call is: what scrolls to it, and its size, to keep a margin around it. */
class FootnoteAnchor {
    val requester = BringIntoViewRequester()
    var size: IntSize = IntSize.Zero
}

/** Marks this element as where [anchor] is. */
internal fun Modifier.footnoteAnchor(anchor: FootnoteAnchor): Modifier = bringIntoViewRequester(anchor.requester).onSizeChanged { anchor.size = it }

/** The footnotes of the document being drawn; each document provides its own. */
val LocalFootnoteTargets = staticCompositionLocalOf<FootnoteTargets?> { null }

@Composable
fun rememberFootnoteTargets(key: Any?): FootnoteTargets {
    val scope = rememberCoroutineScope()
    val margin = with(LocalDensity.current) { JumpMargin.toPx() }
    return remember(key, scope, margin) { FootnoteTargets(scope, margin) }
}

private val JumpMargin = 80.dp

/** Registers [anchor] as where the calls in [numbers] are, while it is composed. */
@Composable
internal fun RegisterFootnoteCalls(
    targets: FootnoteTargets,
    numbers: List<Int>,
    anchor: FootnoteAnchor,
) {
    DisposableEffect(targets, numbers, anchor) {
        numbers.forEach { targets.registerCall(it, anchor) }
        onDispose { numbers.forEach { targets.unregisterCall(it, anchor) } }
    }
}

/** Registers [anchor] as where note [number] is, while it is composed. */
@Composable
internal fun RegisterFootnote(
    targets: FootnoteTargets,
    number: Int,
    anchor: FootnoteAnchor,
) {
    DisposableEffect(targets, number, anchor) {
        targets.registerNote(number, anchor)
        onDispose { targets.unregisterNote(number, anchor) }
    }
}
