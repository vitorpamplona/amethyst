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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost

/**
 * How big a composer's dialog is on a wide window. A phone shows every composer full screen.
 *
 * [QUICK] is a message: a note, a reply, a poll, a DM, a find log. [FORM] fills in a standalone
 * piece or a place: a product, an event, a group, a geocache, a profile. [DOCUMENT] is long-form
 * writing that wants most of the window.
 */
enum class ComposerSize(
    val maxWidth: Dp,
    val maxHeight: Dp,
    val heightFraction: Float,
) {
    QUICK(maxWidth = 600.dp, maxHeight = 720.dp, heightFraction = 0.85f),
    FORM(maxWidth = 820.dp, maxHeight = 960.dp, heightFraction = 0.85f),
    DOCUMENT(maxWidth = 1100.dp, maxHeight = 1400.dp, heightFraction = 0.92f),
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
 * The one container every composer opens in. On a wide window it is a [size]d dialog over the
 * screen that opened it, which stays in view around it; on a phone it fills the screen, as a
 * pushed screen does. Esc or back calls [onDismissRequest]; a click outside does nothing, so a
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
    if (LocalScreenLayout.current.isLargeScreen) {
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

/** [content] in a card of [size], centered in the window with a margin around it. */
@Composable
fun ComposerCard(
    size: ComposerSize,
    popOut: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val frame = remember(popOut) { ComposerFrame(popOut) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(DialogMargin), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, size.maxWidth)
        val height = minOf(maxHeight * size.heightFraction, size.maxHeight)
        Surface(
            modifier =
                Modifier
                    .width(width)
                    .height(height)
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
    }
}

private val DialogMargin = 24.dp
