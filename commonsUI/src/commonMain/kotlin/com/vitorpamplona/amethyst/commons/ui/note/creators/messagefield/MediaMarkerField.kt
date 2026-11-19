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
package com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.uploads.MediaMarkers
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerBlocks
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerChips
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerRendering
import com.vitorpamplona.amethyst.commons.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.components.LaidOutText
import com.vitorpamplona.amethyst.commons.ui.components.TextOverlayState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The media a composer draws inside its text: one [card] per media marker, in the gap the marker
 * leaves where it sits in the text. Each card takes the field's width and its media's shape
 * ([aspectRatio], width over height, null until known: [defaultHeight]), between [minHeight] and
 * [maxHeight] so a panorama stays tappable and a tall portrait doesn't fill the screen. [card]
 * gets the marker's id and whether the cursor sits against it (Backspace would remove it).
 */
class MediaBlocks(
    val aspectRatio: (id: Int) -> Float?,
    val defaultHeight: Dp = 220.dp,
    val minHeight: Dp = 120.dp,
    val maxHeight: Dp = 420.dp,
    /** A height that doesn't follow the width: a sound file or a document is one row, not a picture. */
    val fixedHeight: (id: Int) -> Dp? = { null },
    val card: @Composable (id: Int, selected: Boolean, modifier: Modifier) -> Unit,
) {
    /** The card's height in a field [widthDp] wide. */
    fun heightOf(
        id: Int,
        widthDp: Float,
    ): Dp {
        fixedHeight(id)?.let { return it }
        val ratio = aspectRatio(id)?.takeIf { it > 0f }
        if (ratio == null || widthDp <= 0f) return defaultHeight
        return (widthDp / ratio).dp.coerceIn(minHeight, maxHeight)
    }
}

/** Space above and below a media card, inside its gap. */
private val MediaBlockGap = 8.dp

/**
 * What a text field needs to draw its media markers: the output transformation (mentions, links
 * and markers), the caret brush (hidden while the cursor sits against a card) and the overlay that
 * places the cards. Pass all three to `ThinPaddingTextField` or `OutlinedThinPaddingTextField`.
 */
class MediaMarkerField(
    val outputTransformation: UrlUserTagOutputTransformation,
    val cursorBrush: Brush?,
    val textOverlay: (@Composable BoxScope.(TextOverlayState) -> Unit)?,
)

/** Media cards ([mediaBlocks]) or chips ([mediaChips]) for the markers in [state]; neither draws only mentions and links. */
@Composable
fun rememberMediaMarkerField(
    state: TextFieldState,
    mediaBlocks: MediaBlocks? = null,
    mediaChips: MediaMarkerChips? = null,
): MediaMarkerField {
    val density = LocalDensity.current
    // The text's width, from the last layout: a card's height follows it and its media's shape.
    val textWidth = remember { mutableFloatStateOf(0f) }
    val rendering: MediaMarkerRendering? =
        if (mediaBlocks != null) {
            remember(mediaBlocks, density) {
                MediaMarkerBlocks { id ->
                    with(density) { (mediaBlocks.heightOf(id, textWidth.floatValue / density.density) + MediaBlockGap * 2).toSp() }
                }
            }
        } else {
            mediaChips
        }
    val transformation = rememberUrlUserTagOutputTransformation(mediaMarkers = rendering)

    // The cursor against a media card selects the card: its line is as tall as the card, so the
    // caret would be too.
    val selection = state.selection
    val selectedBlock = if (mediaBlocks != null && selection.collapsed) MediaMarkers.markerAt(state.text, selection.start) else null

    return MediaMarkerField(
        outputTransformation = transformation,
        cursorBrush = if (selectedBlock != null) SolidColor(Color.Transparent) else null,
        textOverlay =
            mediaBlocks?.let { blocks ->
                { overlay ->
                    // Recomposes only when the width changes, not on every layout.
                    val width by remember(overlay) {
                        derivedStateOf {
                            overlay.laidOut
                                ?.layout
                                ?.fieldWidth()
                                ?.toFloat()
                        }
                    }
                    width?.let { if (it != textWidth.floatValue) SideEffect { textWidth.floatValue = it } }
                    MediaBlockCards(state.text, transformation, overlay, blocks, selectedBlock, textWidth)
                }
            },
    )
}

/** Puts the focus back in a field each time [key] changes: after a dialog over it (the media studio) closes. */
@Composable
fun RefocusOn(
    key: Any?,
    focusRequester: FocusRequester,
) {
    if (key == null) return
    LaunchedEffect(key) {
        // After the dialog's window is gone, or it takes the focus back with it.
        delay(200)
        focusRequester.requestFocus()
    }
}

/**
 * One card per media marker, over the gap the marker leaves in the laid-out text.
 *
 * Which cards there are follows the text, in composition. Where each one goes follows the layout,
 * read from [overlay] as the card is measured: composition only has the last frame's layout, a key
 * behind the text, and a card placed by it jumped down and back with each key typed above it.
 */
@Composable
private fun MediaBlockCards(
    text: CharSequence,
    transformation: UrlUserTagOutputTransformation,
    overlay: TextOverlayState,
    blocks: MediaBlocks,
    selected: Int?,
    textWidth: FloatState,
) {
    val markers = remember(transformation) { MarkerOffsets(transformation) }
    val gap = with(LocalDensity.current) { MediaBlockGap.roundToPx() }
    // A marker pasted twice shows its file twice: the nth card goes in the nth gap.
    val seen = HashMap<Int, Int>()
    MediaMarkers.idsIn(text).forEach { id ->
        val nth = seen.getOrElse(id) { 0 }
        seen[id] = nth + 1
        key(id, nth) {
            val place =
                Modifier.layout { measurable, constraints ->
                    // Before the field knows its width, the gaps are a guess: the card waits a frame.
                    val laidOut = overlay.laidOut
                    val offset = laidOut?.let { markers.of(it)[id]?.getOrNull(nth) }
                    if (offset == null || textWidth.floatValue <= 0f) return@layout layout(0, 0) {}
                    val layout = laidOut.layout
                    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else layout.fieldWidth()
                    // Centred between the text above (the previous line's bottom, which can sit higher
                    // than this line's top) and the end of the gap, so the space above and below matches.
                    val line = layout.getLineForOffset(offset)
                    val above = if (line > 0) layout.getLineBottom(line - 1) else layout.getLineTop(line)
                    val bottom = layout.getLineBottom(line)
                    // Never taller than its gap: a gap sized before the card's media had its shape
                    // would otherwise let the card cover the text above it.
                    val height =
                        minOf(
                            blocks.heightOf(id, width / density).roundToPx(),
                            (bottom - above).roundToInt() - 2 * gap,
                        ).coerceAtLeast(0)
                    val top = ((above + bottom - height) / 2).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, top) }
                }
            blocks.card(id, id == selected, place)
        }
    }
}

/** Where each marker's id sits in a [LaidOutText]'s drawn text, in order; worked out once per layout. */
private class MarkerOffsets(
    private val transformation: UrlUserTagOutputTransformation,
) {
    private var last: LaidOutText? = null
    private var offsets: Map<Int, List<Int>> = emptyMap()

    fun of(laidOut: LaidOutText): Map<Int, List<Int>> {
        if (laidOut === last) return offsets
        val raw = laidOut.text.toString()
        val result = HashMap<Int, MutableList<Int>>()
        var shift = 0
        transformation.replacements(raw).forEach { replacement ->
            if (replacement.isMediaMarker) {
                MediaMarkers.idAt(raw, replacement.rawStart)?.let { result.getOrPut(it) { ArrayList(1) }.add(replacement.rawStart + shift) }
            }
            shift += replacement.visual.length - (replacement.rawEnd - replacement.rawStart)
        }
        last = laidOut
        offsets = result
        return result
    }
}

/** The width the text may take: the field's, not the laid-out text's own (one short line is narrow). */
private fun TextLayoutResult.fieldWidth(): Int = layoutInput.constraints.let { if (it.hasBoundedWidth) it.maxWidth else size.width }
