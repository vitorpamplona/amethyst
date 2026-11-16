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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.uploads.MediaMarkers
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerBlocks
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerChips
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerRendering
import com.vitorpamplona.amethyst.commons.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.spellcheck.toVisual
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

/**
 * The media a composer draws inside its text: one [card] per media marker, [height] tall, in the
 * gap the marker leaves where it sits in the text. [card] gets the marker's id and whether the
 * cursor sits against it (Backspace would remove it).
 */
class MediaBlocks(
    val height: Dp,
    val card: @Composable (id: Int, selected: Boolean, modifier: Modifier) -> Unit,
)

/** Space above and below a media card, inside its gap. */
private val MediaBlockGap = 8.dp

@Composable
fun MessageField(
    placeholder: StringResource,
    viewModel: IMessageField,
    requestFocus: Boolean = true,
    onContentReceived: ((MediaUri, String?) -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    mediaChips: MediaMarkerChips? = null,
    mediaBlocks: MediaBlocks? = null,
    /** Each new value puts the focus back in the field: after a dialog over it (the media studio) closes. */
    refocusKey: Any? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (refocusKey != null) {
        LaunchedEffect(refocusKey) {
            // After the dialog's window is gone, or it takes the focus back with it.
            delay(200)
            focusRequester.requestFocus()
        }
    }

    if (requestFocus) {
        LaunchedEffect(Unit) {
            launch {
                delay(200)
                focusRequester.requestFocus()
            }
        }
    }

    val density = LocalDensity.current
    val rendering: MediaMarkerRendering? =
        if (mediaBlocks != null) {
            remember(mediaBlocks.height, density) { MediaMarkerBlocks(with(density) { (mediaBlocks.height + MediaBlockGap * 2).toSp() }) }
        } else {
            mediaChips
        }
    val transformation = rememberUrlUserTagOutputTransformation(mediaMarkers = rendering)

    // The cursor against a media card selects the card: its line is as tall as the card, so the
    // caret would be too.
    val selection = viewModel.message.selection
    val selectedBlock = if (mediaBlocks != null && selection.collapsed) MediaMarkers.markerAt(viewModel.message.text, selection.start) else null

    ThinPaddingTextField(
        state = viewModel.message,
        onTextChanged = viewModel::onMessageChanged,
        onContentReceived = onContentReceived,
        inputTransformation = MentionPreservingInputTransformation,
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        keyboardController?.show()
                        onFocused?.invoke()
                    }
                },
        placeholder = {
            Text(
                text = stringRes(placeholder),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        spellCheck = true,
        outputTransformation = transformation,
        cursorBrush = if (selectedBlock != null) SolidColor(Color.Transparent) else null,
        textOverlay =
            mediaBlocks?.let { blocks ->
                { layout -> MediaBlockCards(viewModel.message.text, transformation, layout, blocks, selectedBlock) }
            },
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        contentPadding =
            TextFieldDefaults.contentPaddingWithoutLabel(
                start = 10.dp,
                top = 5.dp,
                end = 10.dp,
                bottom = 5.dp,
            ),
    )
}

/** One card per media marker, over the gap the marker leaves in the laid-out text. */
@Composable
private fun MediaBlockCards(
    raw: CharSequence,
    transformation: UrlUserTagOutputTransformation,
    layout: TextLayoutResult,
    blocks: MediaBlocks,
    selected: Int?,
) {
    val rawText = raw.toString()
    val replacements = transformation.replacements(rawText)
    val cardHeight = with(LocalDensity.current) { blocks.height.toPx() }
    val drawn = layout.layoutInput.text.length
    replacements.forEach { replacement ->
        if (!replacement.isMediaMarker) return@forEach
        val id = MediaMarkers.idAt(rawText, replacement.rawStart) ?: return@forEach
        val offset = replacements.toVisual(replacement.rawStart) ?: return@forEach
        // The layout can trail the text by a frame: skip a marker it hasn't laid out yet.
        if (offset >= drawn) return@forEach
        // Centred between the text above (the previous line's bottom, which can sit higher than this
        // line's top) and the end of the gap, so the space above and below the card matches.
        val line = layout.getLineForOffset(offset)
        val above = if (line > 0) layout.getLineBottom(line - 1) else layout.getLineTop(line)
        val top = ((above + layout.getLineBottom(line) - cardHeight) / 2).roundToInt()
        key(id) {
            blocks.card(id, id == selected, Modifier.offset { IntOffset(0, top) }.fillMaxWidth().height(blocks.height))
        }
    }
}
