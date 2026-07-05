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
package com.vitorpamplona.amethyst.ui.note.types

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveIcon

/** How many hex characters of the block to preview (the full block is ~16K chars). */
private const val HEX_PREVIEW_CHARS = 192

/** Floppy-disk emoji fallback for blocks without a decodable save icon. */
private const val FLOPPY_PREFIX = "💾 "

/** Milliseconds per animation frame, roughly the BIOS memory-card screen cadence. */
private const val ICON_FRAME_MILLIS = 250L

/**
 * Minimal, fixed-size card for a PS1 memory-card save block (kind 38192).
 *
 * The content is a full 8 KiB memory-card block, hex-encoded (~16K characters),
 * so the card never renders it whole: it shows the save's own 16×16 icon from
 * the title frame (animated exactly like the PS1 BIOS memory-card screen; a
 * floppy emoji when the block has none), the save title, a metadata line
 * (game product code, region, block number), and a short monospace hex preview
 * clamped to a few lines — no expansion, no network calls. The card is
 * identical in the feed and the opened view, so it takes no makeItShort flag.
 */
@Composable
fun RenderPs1Save(baseNote: Note) {
    val noteEvent = baseNote.event as? Ps1SaveEvent ?: return

    val save =
        remember(noteEvent) {
            Ps1SaveInfo(
                title = noteEvent.saveTitle(),
                filename = noteEvent.filename(),
                region = noteEvent.region(),
                blockNumber = noteEvent.blockNumber(),
                icon = noteEvent.icon(),
                isBlank = noteEvent.isBlankBlock(),
                hexPreview =
                    noteEvent.content
                        .take(HEX_PREVIEW_CHARS)
                        .chunked(4)
                        .joinToString(" ")
                        .ifBlank { null },
            )
        }

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (save.icon != null) {
                Ps1SaveIconImage(save.icon)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = (if (save.icon == null) FLOPPY_PREFIX else "") + (save.title ?: stringRes(R.string.ps1_save_title)),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val details =
            listOfNotNull(
                save.filename,
                save.region,
                save.blockNumber?.let { stringRes(R.string.ps1_save_block, it) },
            ).joinToString(" · ")

        if (details.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (save.isBlank) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringRes(R.string.ps1_save_empty_slot),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        } else if (save.hexPreview != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = save.hexPreview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The save's 16×16 icon, cycling its animation frames like the PS1 BIOS
 * memory-card screen. Scaled with nearest-neighbor so the pixels stay crisp.
 */
@Composable
private fun Ps1SaveIconImage(icon: Ps1SaveIcon) {
    val frames =
        remember(icon) {
            icon.frames.map { pixels ->
                Bitmap
                    .createBitmap(Ps1SaveIcon.SIZE, Ps1SaveIcon.SIZE, Bitmap.Config.ARGB_8888)
                    .apply { setPixels(pixels, 0, Ps1SaveIcon.SIZE, 0, 0, Ps1SaveIcon.SIZE, Ps1SaveIcon.SIZE) }
                    .asImageBitmap()
            }
        }

    var frameIndex by remember(icon) { mutableIntStateOf(0) }

    if (frames.size > 1) {
        // Driven by the frame clock (not delay) so the ticker suspends whenever
        // the composition stops drawing (backgrounded, behind another screen).
        LaunchedEffect(icon) {
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    frameIndex = (((now - start) / ICON_FRAME_MILLIS) % frames.size).toInt()
                }
            }
        }
    }

    Image(
        bitmap = frames[frameIndex],
        contentDescription = null,
        modifier = Modifier.size(Size24dp),
        filterQuality = FilterQuality.None,
    )
}

/** Tag values parsed once per event for the save card. */
private class Ps1SaveInfo(
    val title: String?,
    val filename: String?,
    val region: String?,
    val blockNumber: Int?,
    val icon: Ps1SaveIcon?,
    val isBlank: Boolean,
    val hexPreview: String?,
)
