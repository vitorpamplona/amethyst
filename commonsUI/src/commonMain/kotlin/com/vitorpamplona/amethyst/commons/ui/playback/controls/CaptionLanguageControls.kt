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
package com.vitorpamplona.amethyst.commons.ui.playback.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.captions_off
import com.vitorpamplona.amethyst.commons.resources.captions_unnamed_track
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.TextTrack
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.languageNameOnly
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The text tracks a player has, in the order the media declared them.
 *
 * A NIP-71 video can carry a `text-track` per language, so "captions" is not one thing to switch
 * on — it is a list to choose from. Tracks the player reports as unsupported are already dropped
 * by [AmethystPlayer.textTracks]: offering a language the decoder will refuse is worse than not
 * offering it.
 */
internal fun getTextTrackChoices(player: AmethystPlayer): ImmutableList<CaptionChoice> =
    player.textTracks
        .map { track ->
            CaptionChoice(
                track = track,
                language = track.language,
                label = track.label,
                isSelected = track.selected,
            )
        }.toImmutableList()

internal data class CaptionChoice(
    val track: TextTrack,
    val language: String?,
    val label: String?,
    val isSelected: Boolean,
) {
    /**
     * What to print for this track.
     *
     * The publisher's own label wins when there is one. Otherwise the language code is expanded
     * into the reader's locale — "en" is not a word to most people, "English" is — and only a
     * track that names neither falls back to a generic term.
     */
    fun displayName(fallback: String): String =
        label?.takeIf { it.isNotBlank() }
            ?: language?.takeIf { it.isNotBlank() }?.let { languageNameOnly(it).ifBlank { it } }
            ?: fallback
}

internal fun selectTextTrack(
    player: AmethystPlayer,
    choice: CaptionChoice,
) {
    // Also turns captions on: picking a language is a request to see captions, so it has to undo
    // an earlier "off" rather than silently select a track nothing draws.
    player.selectTextTrack(choice.track)
}

/**
 * The language menu, shown in place of a plain on/off toggle when a video offers more than one
 * caption track.
 */
@Composable
internal fun CaptionLanguagePopup(
    choices: ImmutableList<CaptionChoice>,
    captionsEnabled: Boolean,
    onSelectOff: () -> Unit,
    onSelectTrack: (CaptionChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val baseColors = ButtonDefaults.textButtonColors()
        val contentColor = MaterialTheme.colorScheme.onBackground
        val colors = remember(baseColors, contentColor) { baseColors.copy(contentColor = contentColor) }
        val unnamed = stringRes(Res.string.captions_unnamed_track)

        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(colors = colors, onClick = onSelectOff) {
                Text(
                    stringRes(Res.string.captions_off),
                    fontWeight = if (!captionsEnabled) FontWeight(1000) else FontWeight(400),
                )
            }

            choices.forEach { choice ->
                TextButton(colors = colors, onClick = { onSelectTrack(choice) }) {
                    Text(
                        choice.displayName(unnamed),
                        fontWeight = if (captionsEnabled && choice.isSelected) FontWeight(1000) else FontWeight(400),
                    )
                }
            }
        }
    }
}
