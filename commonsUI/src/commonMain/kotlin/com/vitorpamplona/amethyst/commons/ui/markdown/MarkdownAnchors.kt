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
package com.vitorpamplona.amethyst.commons.ui.markdown

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lets a reader jump to a section of a Markdown document: the Markdown renderer splits the
 * document at its headings while one of these is provided ([LocalMarkdownAnchors]) and registers
 * where each section starts; a table of contents or a `[link](#section)` then scrolls [scrollable]
 * to it. [viewport] is the scrolling container, and [topInsetPx] the part of it a top bar covers.
 *
 * Positions are read when a jump is asked for, so they are right however far the reader scrolled.
 * A section whose layout left the screen (a lazy list recycled it) can't be reached until it is
 * composed again; for a document's own page that never happens.
 */
@Stable
class MarkdownAnchors(
    private val scrollable: ScrollableState,
    private val scope: CoroutineScope,
    private val topInsetPx: () -> Float,
) {
    private val targets = HashMap<String, LayoutCoordinates>()
    private var viewport: LayoutCoordinates? = null

    fun registerViewport(coordinates: LayoutCoordinates) {
        viewport = coordinates
    }

    fun register(
        anchor: String,
        coordinates: LayoutCoordinates,
    ) {
        targets[anchor] = coordinates
    }

    /**
     * The section a link points at, when it is one of this document's: `#Encryption`, or the
     * hashtag form the Markdown library turns a `#…` link into (`nostr:hashtag?id=Encryption`).
     * Null for anything else, so a real hashtag still opens its feed.
     */
    fun anchorOfLink(uri: String): String? {
        val raw =
            when {
                uri.startsWith("#") -> uri.removePrefix("#")
                uri.startsWith(HASHTAG_URI) -> uri.removePrefix(HASHTAG_URI)
                uri.startsWith(NOSTR_HASHTAG_URI) -> uri.removePrefix(NOSTR_HASHTAG_URI)
                else -> return null
            }
        val key = MarkdownChunks.anchorOf(raw)
        return key.takeIf { it in targets }
    }

    /** Scrolls to [anchor] (`encryption`, `#Encryption`); false when the section isn't laid out. */
    fun jumpTo(anchor: String): Boolean {
        val key = MarkdownChunks.anchorOf(anchor.removePrefix("#"))
        val target = targets[key] ?: return false
        val view = viewport ?: return false
        if (!target.isAttached || !view.isAttached) return false
        val delta = target.positionInRoot().y - view.positionInRoot().y - topInsetPx()
        scope.launch { scrollable.animateScrollBy(delta) }
        return true
    }
}

private const val HASHTAG_URI = "hashtag?id="
private const val NOSTR_HASHTAG_URI = "nostr:hashtag?id="

/** The anchors of the document being read; null where headings needn't be reachable (feeds). */
val LocalMarkdownAnchors = staticCompositionLocalOf<MarkdownAnchors?> { null }

@Composable
fun rememberMarkdownAnchors(
    scrollable: ScrollableState,
    topInsetPx: () -> Float = { 0f },
): MarkdownAnchors {
    val scope = rememberCoroutineScope()
    return remember(scrollable, scope) { MarkdownAnchors(scrollable, scope, topInsetPx) }
}

/** Marks the scrolling container the anchors measure against. */
fun Modifier.markdownAnchorViewport(anchors: MarkdownAnchors?): Modifier = if (anchors == null) this else onGloballyPositioned { anchors.registerViewport(it) }

/** Marks where the section [anchor] starts. */
fun Modifier.markdownAnchor(
    anchors: MarkdownAnchors?,
    anchor: String?,
): Modifier = if (anchors == null || anchor == null) this else onGloballyPositioned { anchors.register(anchor, it) }
