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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.service.uploads.MediaMarkers
import com.vitorpamplona.amethyst.commons.ui.spellcheck.TextReplacement
import com.vitorpamplona.amethyst.commons.ui.spellcheck.VisualOffsetMapping
import com.vitorpamplona.amethyst.commons.ui.text.MENTION_REGEX
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector
import kotlin.coroutines.cancellation.CancellationException

/**
 * The field's [UrlUserTagOutputTransformation], kept across recompositions. BasicTextField keys its
 * transformed state on the transformation's equality: a new instance per recomposition restarts a
 * focused field's keyboard session and drops the mention cache.
 */
@Composable
fun rememberUrlUserTagOutputTransformation(
    color: Color = MaterialTheme.colorScheme.primary,
    mediaMarkers: MediaMarkerRendering? = null,
): UrlUserTagOutputTransformation = remember(color, mediaMarkers) { UrlUserTagOutputTransformation(color, mediaMarkers) }

/** How a composer that holds media markers ([MediaMarkers]) draws them in its text. */
sealed interface MediaMarkerRendering

/**
 * Each marker as an inline chip: [label] names the marker with [id] that is the [ordinal]-th
 * (1-based) in the text, e.g. "Photo 2", in [chip] / [onChip] colors. For fields too small to
 * show the media itself.
 */
class MediaMarkerChips(
    val chip: Color,
    val onChip: Color,
    val label: (id: Int, ordinal: Int) -> String,
) : MediaMarkerRendering

/**
 * Each marker as an empty paragraph [lineHeight] tall (by the marker's id: each media keeps its
 * own shape): a gap in the text where the field's owner draws the media itself (see
 * `ThinPaddingTextField.textOverlay`), so the composer reads like the note will. The marker becomes
 * a single invisible character, so the cursor steps over it in one move and Backspace removes the
 * whole marker.
 */
class MediaMarkerBlocks(
    val lineHeight: (id: Int) -> TextUnit,
) : MediaMarkerRendering

class UrlUserTagOutputTransformation(
    val color: Color,
    val mediaMarkers: MediaMarkerRendering? = null,
) : OutputTransformation,
    VisualOffsetMapping {
    override fun TextFieldBuffer.transformOutput() {
        val rawText = asCharSequence().toString()
        val replacements = replacements(rawText)

        // Phase 1: replace mentions back to front, so earlier indices stay valid after each replace.
        // Styles come after: addStyle must be called after all text mutations.
        for (index in replacements.indices.reversed()) {
            val replacement = replacements[index]
            replace(replacement.rawStart, replacement.rawEnd, replacement.visual)
        }

        // Phase 2: style the display names, front to back, tracking the shift of earlier replacements.
        val style = SpanStyle(color = color, textDecoration = TextDecoration.None)
        val markers = mediaMarkers
        val chipStyle = (markers as? MediaMarkerChips)?.let { SpanStyle(color = it.onChip, background = it.chip, fontWeight = FontWeight.Medium) }
        val blocks = markers as? MediaMarkerBlocks
        var cumulativeShift = 0
        for (replacement in replacements) {
            val adjustedStart = replacement.rawStart + cumulativeShift
            val adjustedEnd = adjustedStart + replacement.visual.length
            when {
                !replacement.isMediaMarker -> addStyle(style, adjustedStart, adjustedEnd)
                chipStyle != null -> addStyle(chipStyle, adjustedStart, adjustedEnd)
                blocks != null -> {
                    val id = MediaMarkers.idAt(rawText, replacement.rawStart)
                    if (id != null) addStyle(ParagraphStyle(lineHeight = blocks.lineHeight(id), lineHeightStyle = BlockLineHeightStyle), adjustedStart, adjustedEnd)
                    addStyle(HiddenStyle, adjustedStart, adjustedEnd)
                }
            }
            cumulativeShift += replacement.visual.length - (replacement.rawEnd - replacement.rawStart)
        }

        // Highlight URLs in remaining text
        highlightUrls(color)
    }

    // The last text and its replacements. transformOutput and the spell checker both ask for the
    // same text right after each edit, and must agree: a display name that loads in between would
    // otherwise shift the checker's offsets away from what the field drew.
    private var lastText: String? = null
    private var lastReplacements: List<TextReplacement> = emptyList()

    /**
     * Each `@npub…`/`nostr:nprofile…` mention and the `@Name` the field draws instead, and, when
     * [mediaMarkers] is set, each media marker and what it draws as, in order.
     */
    override fun replacements(rawText: String): List<TextReplacement> {
        if (rawText == lastText) return lastReplacements
        return findReplacements(rawText).also {
            lastText = rawText
            lastReplacements = it
        }
    }

    private fun findReplacements(rawText: String): List<TextReplacement> {
        val mentions = findMentions(rawText)
        val rendering = mediaMarkers ?: return mentions
        if (!rawText.contains(MediaMarkers.PREFIX)) return mentions
        val markers =
            MediaMarkers.REGEX.findAll(rawText).mapIndexedTo(ArrayList()) { index, match ->
                val visual =
                    when (rendering) {
                        // Non-breaking spaces pad the chip's background on both sides.
                        is MediaMarkerChips -> "\u00A0${rendering.label(match.groupValues[1].toInt(), index + 1)}\u00A0"
                        is MediaMarkerBlocks -> "\u00A0"
                    }
                TextReplacement(match.range.first, match.range.last + 1, visual, isMediaMarker = true)
            }
        return (mentions + markers).sortedBy { it.rawStart }
    }

    private fun findMentions(rawText: String): List<TextReplacement> =
        MENTION_REGEX.findAll(rawText).mapNotNullTo(ArrayList()) { match ->
            try {
                val bech32 =
                    match.value
                        .removePrefix("@")
                        .removePrefix("nostr:")
                val hex = decodePublicKeyAsHexOrNull(bech32) ?: return@mapNotNullTo null
                val user = LocalCache.getOrCreateUser(hex)
                TextReplacement(match.range.first, match.range.last + 1, "@${user.toBestDisplayName()}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
}

// The gap's character sits at the top of its tall line, invisible: the media is drawn over the gap.
private val BlockLineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None)
private val HiddenStyle = SpanStyle(color = Color.Transparent)

// The same detector the note renderer links with, so the editor highlights what the posted
// note will make clickable.
private fun TextFieldBuffer.highlightUrls(color: Color) {
    val text = asCharSequence().toString()
    var searchFrom = 0
    UrlDetector(text).detect().forEach { url ->
        val start = text.indexOf(url.originalUrl, searchFrom)
        if (start >= 0) {
            val end = start + url.originalUrl.length
            addStyle(SpanStyle(color = color, textDecoration = TextDecoration.None), start, end)
            searchFrom = end
        }
    }
}
