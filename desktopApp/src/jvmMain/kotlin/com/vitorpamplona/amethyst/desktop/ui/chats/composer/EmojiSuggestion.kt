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
package com.vitorpamplona.amethyst.desktop.ui.chats.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.kodein.emoji.Emoji
import org.kodein.emoji.allGroups
import org.kodein.emoji.allOf

/**
 * One `:shortcode:` autocomplete entry — either a NIP-30 custom-pack emoji
 * (rendered from [previewUrl], inserts `:code:`) or a standard unicode emoji
 * (rendered as [previewGlyph], inserts the glyph itself).
 */
data class EmojiSuggestion(
    val label: String,
    val insertText: String,
    val previewUrl: String?,
    val previewGlyph: String?,
)

/**
 * The full flat list of standard unicode emojis, built once from the Kodein
 * emoji-kt static data (no network/service init needed). Shared by the emoji
 * picker and the `:` shortcode autocomplete.
 */
internal val standardEmojis: List<Emoji> by lazy {
    Emoji.allGroups().flatMap { group -> Emoji.allOf(group) }
}

/**
 * Horizontal `:shortcode:` autocomplete strip shown above the composer while the
 * word under the caret starts with `:`. Renders custom-pack emojis (image) and
 * standard emojis (glyph) side by side. [onPick] returns the chosen entry so the
 * caller can replace the current word with its [EmojiSuggestion.insertText].
 */
@Composable
fun EmojiSuggestionStrip(
    suggestions: List<EmojiSuggestion>,
    onPick: (EmojiSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(suggestions, key = { "${it.label}|${it.insertText}" }) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .padding(horizontal = 4.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onPick(suggestion) },
                ) {
                    if (suggestion.previewUrl != null) {
                        AsyncImage(
                            model = suggestion.previewUrl,
                            contentDescription = suggestion.label,
                            modifier = Modifier.size(28.dp).padding(end = 4.dp),
                        )
                    } else if (suggestion.previewGlyph != null) {
                        Text(
                            text = suggestion.previewGlyph,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        text = ":${suggestion.label}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }
}
