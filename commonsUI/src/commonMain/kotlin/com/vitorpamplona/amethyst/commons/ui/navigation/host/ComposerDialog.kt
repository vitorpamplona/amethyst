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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.cornerResizeCursor
import com.vitorpamplona.amethyst.commons.ui.layouts.horizontalResizeCursor
import com.vitorpamplona.amethyst.commons.ui.layouts.verticalResizeCursor
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost

/**
 * How big a composer's dialog is where composers open as dialogs ([composersOpenAsDialogs]). A
 * touch screen shows every composer full screen.
 *
 * [QUICK] is a message: a note, a reply, a poll, a DM, a find log. [FORM] fills in a standalone
 * piece or a place: a product, an event, a group, a geocache, a profile. [DOCUMENT] is long-form
 * writing that wants most of the window. [MEDIA] is the media Studio and the picture crop: a
 * stage for the picture or video, as large as the window comfortably allows.
 */
enum class ComposerSize(
    val maxWidth: Dp,
    val maxHeight: Dp,
    val heightFraction: Float,
) {
    QUICK(maxWidth = 600.dp, maxHeight = 720.dp, heightFraction = 0.85f),
    FORM(maxWidth = 820.dp, maxHeight = 960.dp, heightFraction = 0.85f),
    DOCUMENT(maxWidth = 1100.dp, maxHeight = 1400.dp, heightFraction = 0.92f),
    MEDIA(maxWidth = 1000.dp, maxHeight = 1100.dp, heightFraction = 0.9f),
}

/**
 * Where the composer being drawn sits, for the parts of it that change with that: [popOut] moves
 * it out to a window of its own, when the platform has windows. Null (the default) when the
 * composer fills its screen or window, as on a phone.
 */
@Immutable
class ComposerFrame(
    val popOut: (() -> Unit)?,
)

val LocalComposerFrame = staticCompositionLocalOf<ComposerFrame?> { null }

/** Whether the composer being drawn sits in a dialog over the screen that opened it. */
@Composable
fun isComposerDialog(): Boolean = LocalComposerFrame.current != null

/**
 * Whether composers open as dialogs here: a wide window driven by a mouse, the desktop. A touch
 * screen keeps them full screen even when wide: the rail tier is also a phone turned sideways,
 * and on a tablet the on-screen keyboard takes the bottom of a centered card while the screen
 * around it, which a modal dialog blocks anyway, adds nothing.
 */
@Composable
fun composersOpenAsDialogs(): Boolean = LocalScreenLayout.current.isLargeScreen && LocalAppPlatform.current.hasFinePointer

/**
 * The one container every composer opens in. Where [composersOpenAsDialogs] it is a [size]d
 * dialog over the screen that opened it, which stays in view around it; on a touch screen it
 * fills the screen, as a pushed screen does. Esc or back calls [onDismissRequest]; a click outside does nothing, so a
 * stray click never throws a draft away.
 *
 * The navigation host draws composer destinations in it; a composer that is not a destination
 * (the media and badge composers) wraps itself in it.
 */
@Composable
fun ComposerDialog(
    size: ComposerSize,
    onDismissRequest: () -> Unit,
    popOut: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (composersOpenAsDialogs()) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            ComposerCard(size, popOut, content)
        }
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = edgeToEdgeDialogProperties(dismissOnClickOutside = false),
        ) {
            SetDialogToEdgeToEdge()
            content()
        }
    }
}

/**
 * The dialog size the user picked for each [ComposerSize], kept between runs where the platform
 * can: every New Post opens at the size the last one was left at. Null means the size's default.
 */
interface ComposerDialogSizes {
    fun load(size: ComposerSize): DpSize?

    fun save(
        size: ComposerSize,
        picked: DpSize?,
    )

    companion object {
        /** For as long as the app runs: a platform that keeps nothing between runs. */
        val InMemory: ComposerDialogSizes =
            object : ComposerDialogSizes {
                private val picked = HashMap<ComposerSize, DpSize>()

                override fun load(size: ComposerSize) = picked[size]

                override fun save(
                    size: ComposerSize,
                    picked: DpSize?,
                ) {
                    if (picked == null) this.picked.remove(size) else this.picked[size] = picked
                }
            }
    }
}

/**
 * [content] in a card of [size], centered in the window with a margin around it. Its sides, its
 * bottom and its bottom-right corner drag to resize it, growing evenly around its center; the size
 * is kept for the next composer of that size. A double click on the corner goes back to the default.
 */
@Composable
fun ComposerCard(
    size: ComposerSize,
    popOut: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val frame = remember(popOut) { ComposerFrame(popOut) }
    val store = LocalAppPlatform.current.composerDialogSizes
    var picked by remember(size, store) { mutableStateOf(store.load(size)) }

    // The handles sit in a ring just outside the card, inside its margin, so they never cover
    // anything the composer draws.
    BoxWithConstraints(Modifier.fillMaxSize().padding(DialogMargin - HandleThickness), contentAlignment = Alignment.Center) {
        val room = DpSize(maxWidth - HandleThickness * 2, maxHeight - HandleThickness * 2)
        val default = DpSize(minOf(room.width, size.maxWidth), minOf(room.height * size.heightFraction, size.maxHeight))
        val shown = (picked ?: default).fitIn(room)

        val resize =
            remember(size, store) {
                ComposerResize(
                    onResize = { width, height -> picked = DpSize(width, height) },
                    onDone = { store.save(size, picked) },
                    onReset = {
                        picked = null
                        store.save(size, null)
                    },
                )
            }
        resize.current = shown
        resize.room = room

        Box(Modifier.size(shown.width + HandleThickness * 2, shown.height + HandleThickness * 2)) {
            Surface(
                modifier =
                    Modifier
                        .padding(HandleThickness)
                        .fillMaxSize()
                        // The card sits clear of the status bar and cutouts: the composer's own bars skip them.
                        .consumeWindowInsets(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
                shape = MaterialTheme.shapes.extraLarge,
                // A dark theme's top bar is as dark as the scrim: the outline keeps the card's edge in view.
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                CompositionLocalProvider(LocalComposerFrame provides frame) {
                    // The emoji panel docks inside the card, not under it in the window behind.
                    EmojiPanelHost { Box(Modifier.fillMaxSize()) { content() } }
                }
            }
            ResizeHandles(resize)
        }
    }
}

/** What a drag on a handle changes: the card's [current] size, within the [room] the window leaves. */
private class ComposerResize(
    val onResize: (Dp, Dp) -> Unit,
    val onDone: () -> Unit,
    val onReset: () -> Unit,
) {
    var current: DpSize = DpSize.Zero
    var room: DpSize = DpSize.Zero

    /** The card stays centered, so an edge moves by half of what the size changes: twice the drag keeps it under the pointer. */
    fun drag(
        dx: Dp,
        dy: Dp,
    ) {
        val next = DpSize(current.width + dx * 2, current.height + dy * 2).fitIn(room)
        current = next
        onResize(next.width, next.height)
    }
}

@Composable
private fun BoxScope.ResizeHandles(resize: ComposerResize) {
    // One gesture loop per handle: a drag resizes, and on the corner a second press soon after a
    // click resets. Separate drag and double-tap detectors on one box fought over the press.
    fun Modifier.dragging(
        horizontal: Float,
        vertical: Float,
        resetsOnDoubleClick: Boolean = false,
    ) = pointerInput(resize) {
        var lastClickUp = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (resetsOnDoubleClick && down.uptimeMillis - lastClickUp <= viewConfiguration.doubleTapTimeoutMillis) {
                lastClickUp = 0L
                down.consume()
                resize.onReset()
                waitForUpOrCancellation()?.consume()
                return@awaitEachGesture
            }

            fun move(amount: Offset) = resize.drag(amount.x.toDp() * horizontal, amount.y.toDp() * vertical)
            val start =
                awaitTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                    // The whole way from the press, slop included, so the edge stays under the pointer.
                    move(change.position - down.position)
                }
            if (start == null) {
                // A click: the first half of a double click.
                lastClickUp = currentEvent.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
                return@awaitEachGesture
            }
            drag(start.id) { change ->
                move(change.positionChange())
                change.consume()
            }
            resize.onDone()
        }
    }

    // The sides and the bottom: strips in the ring around the card.
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .width(HandleThickness)
            .fillMaxHeight(0.8f)
            .horizontalResizeCursor()
            .dragging(horizontal = -1f, vertical = 0f),
    )
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .width(HandleThickness)
            .fillMaxHeight(0.8f)
            .horizontalResizeCursor()
            .dragging(horizontal = 1f, vertical = 0f),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .height(HandleThickness)
            .fillMaxWidth(0.8f)
            .verticalResizeCursor()
            .dragging(horizontal = 0f, vertical = 1f),
    )

    // The corner: the ring's corner and the part of the card's own corner its rounding leaves
    // empty (within 8 dp of the corner, a 28 dp radius draws nothing), with the grip drawn there,
    // outside the card's curve.
    val grip = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .size(CornerSize)
            .cornerResizeCursor()
            .dragging(horizontal = 1f, vertical = 1f, resetsOnDoubleClick = true),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val inset = 3.dp.toPx()
            for (step in 0..2) {
                val reach = (3 + step * 3).dp.toPx()
                drawLine(
                    color = grip,
                    start = Offset(size.width - inset - reach, size.height - inset),
                    end = Offset(size.width - inset, size.height - inset - reach),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** This size, no smaller than [MinCardSize] (where [room] allows) and no larger than [room]. */
private fun DpSize.fitIn(room: DpSize) =
    DpSize(
        width.coerceIn(minOf(MinCardSize.width, room.width), room.width),
        height.coerceIn(minOf(MinCardSize.height, room.height), room.height),
    )

private val MinCardSize = DpSize(360.dp, 320.dp)
private val HandleThickness = 8.dp
private val CornerSize = HandleThickness + 8.dp

private val DialogMargin = 24.dp
