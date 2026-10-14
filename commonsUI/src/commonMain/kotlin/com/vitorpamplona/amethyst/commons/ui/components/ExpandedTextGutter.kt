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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.show_less
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlin.math.roundToInt

/**
 * The note's author column, under the picture, while the note's text is expanded: the "Show Less"
 * button lives there instead of over the text. [ExpandableRichTextViewer] registers how to collapse
 * itself while it shows its full text; the note renders [ExpandedTextGutter] in that column.
 */
@Stable
class ExpandedTextGutterState {
    var collapse by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun register(action: () -> Unit) {
        collapse = action
    }

    fun unregister(action: () -> Unit) {
        if (collapse === action) collapse = null
    }
}

/**
 * The gutter of the note being rendered, or null where there is none (quotes, chats, profiles):
 * [ExpandableRichTextViewer] then puts its "Show Less" under the text.
 */
val LocalExpandedTextGutter = staticCompositionLocalOf<ExpandedTextGutterState?> { null }

private val GutterButtonTopMargin = 8.dp

/**
 * Emits nothing while the text is collapsed, so the note lays out no gutter at all. While expanded,
 * a thin track spans the column and the button stays pinned to the top of what is visible of the
 * note, until the note's bottom pushes it up.
 */
@Composable
fun ExpandedTextGutter(state: ExpandedTextGutterState) {
    val collapse = state.collapse ?: return
    StickyShowLessButton(collapse)
}

@Composable
private fun StickyShowLessButton(collapse: () -> Unit) {
    val viewport = LocalZoomViewport.current
    val topBarHeight = rememberTopBarHeightPx()
    val tracker = remember { GutterTracker() }
    val dispatcher = remember { NestedScrollDispatcher() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(NoOpNestedScrollConnection, dispatcher)
                .onGloballyPositioned { tracker.onPositioned(it) },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = GutterButtonTopMargin)
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
        )

        Surface(
            onClick = {
                tracker.revealNoteTop(dispatcher, topBarHeight)
                collapse()
            },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier =
                Modifier
                    .width(48.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, tracker.stickyOffset(viewport, placeable.height, GutterButtonTopMargin.roundToPx()))
                        }
                    },
        ) {
            Column(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringRes(Res.string.show_less),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

internal object NoOpNestedScrollConnection : NestedScrollConnection

/** Where a layout was last placed, kept out of snapshot state: it is read on click, not composed. */
internal class PlacedCoordinates {
    var coordinates: LayoutCoordinates? = null
}

private class GutterTracker {
    private var coordinates: LayoutCoordinates? = null

    // Bumped every time the gutter moves on screen, so the button's placement runs again.
    private var moves by mutableIntStateOf(0)

    fun onPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        moves++
    }

    /** How far down the gutter the button sits: the visible top, kept inside the gutter. */
    fun stickyOffset(
        viewport: ZoomViewport?,
        buttonHeight: Int,
        margin: Int,
    ): Int {
        moves
        val gutter = coordinates?.takeIf { it.isAttached } ?: return 0
        val hidden = gutter.visibleTopInWindow(viewport) - gutter.positionInWindow().y
        val maxOffset = (gutter.size.height - buttonHeight).coerceAtLeast(0)
        return (hidden.roundToInt() + margin).coerceIn(0, maxOffset)
    }

    fun revealNoteTop(
        dispatcher: NestedScrollDispatcher,
        topBarHeight: Float,
    ) {
        val gutter = coordinates?.takeIf { it.isAttached } ?: return
        dispatcher.revealTopOf(gutter.parentLayoutCoordinates ?: gutter, topBarHeight)
    }
}

/**
 * Scrolls the list so [target]'s top is back on screen, before [target] shrinks. Without it, a text
 * collapsed from deep inside would leave the feed scrolled past its note, onto whatever came next.
 * The parent scrollable consumes what its children leave over, so a post-scroll dispatch moves the
 * list right away, before the collapse lays out. [dispatcher] must be attached with nestedScroll.
 *
 * The list runs under the top bar, and scrolling back up brings a hidden bar back, so the top lands
 * [topBarHeight] below the list's visible edge whether or not the bar shows right now.
 */
internal fun NestedScrollDispatcher.revealTopOf(
    target: LayoutCoordinates,
    topBarHeight: Float,
) {
    if (!target.isAttached) return
    val hidden = target.boundsInWindow().top + topBarHeight - target.positionInWindow().y
    if (hidden > 0f) {
        dispatchPostScroll(Offset.Zero, Offset(0f, hidden), NestedScrollSource.UserInput)
    }
}

/** The height of the bar the feed scrolls under, in pixels; zero outside the disappearing scaffold. */
@Composable
internal fun rememberTopBarHeightPx(): Float {
    val topPadding = LocalDisappearingScaffoldPadding.current.calculateTopPadding()
    val density = LocalDensity.current
    return remember(topPadding, density) { with(density) { topPadding.toPx() } }
}

/** The top of what shows of this layout: clipped by its scrolling parents and by bars drawn over them. */
private fun LayoutCoordinates.visibleTopInWindow(viewport: ZoomViewport?): Float {
    val clipped = boundsInWindow().top
    val uncovered = viewport?.boundsInWindow()?.top ?: return clipped
    return maxOf(clipped, uncovered)
}
