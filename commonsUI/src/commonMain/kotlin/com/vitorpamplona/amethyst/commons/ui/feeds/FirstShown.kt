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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.CompletableDeferred

/**
 * Completes when a lazy-list item is first placed on screen.
 *
 * A list can compose an item before showing it: Compose prefetches the next one, and a cache
 * window (`rememberForeverLazyListState(cacheAhead = true)`) composes a screen ahead. What should
 * happen only once the user sees the item (marking it read, dismissing its notification, starting
 * its "new" highlight) waits for this instead of running at composition.
 */
@Stable
class FirstShown {
    private val shown = CompletableDeferred<Unit>()

    val isShown: Boolean get() = shown.isCompleted

    suspend fun await() = shown.await()

    internal fun markShown() {
        shown.complete(Unit)
    }
}

/**
 * The [FirstShown] of the list item this composes in, or null outside one that tracks it: there
 * the item is shown when it is composed, as before.
 */
val LocalFirstShown = staticCompositionLocalOf<FirstShown?> { null }

/**
 * A list item that knows when it is first shown: [content] puts the modifier it gets on the item's
 * root, and everything inside reads [LocalFirstShown].
 */
@Composable
fun FirstShownItem(content: @Composable (Modifier) -> Unit) {
    val signal = remember { FirstShown() }
    CompositionLocalProvider(LocalFirstShown provides signal) {
        content(FirstShownElement(signal))
    }
}

private data class FirstShownElement(
    val signal: FirstShown,
) : ModifierNodeElement<FirstShownNode>() {
    override fun create() = FirstShownNode(signal)

    override fun update(node: FirstShownNode) {
        node.signal = signal
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "firstShown"
    }
}

/** Completes its signal on the first placement; a no-op on every one after (each scroll frame). */
private class FirstShownNode(
    var signal: FirstShown,
) : Modifier.Node(),
    LayoutAwareModifierNode {
    override fun onPlaced(coordinates: LayoutCoordinates) {
        if (!signal.isShown) signal.markShown()
    }
}
