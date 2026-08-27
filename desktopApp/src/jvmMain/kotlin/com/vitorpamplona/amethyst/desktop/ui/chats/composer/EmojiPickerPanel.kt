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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A compact unicode emoji picker for the desktop DM composer. Renders emojis as
 * text via the platform's system emoji font (offline, no network) using the
 * Kodein Emoji.kt data API. [onPick] receives the UTF-16 emoji string to insert
 * at the caret.
 */
@Composable
fun EmojiPickerPanel(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    // Shared flat list of all emojis (built once, static emoji-kt data — no
    // network/service init needed).
    val filtered =
        remember(query) {
            if (query.isBlank()) {
                standardEmojis
            } else {
                val q = query.trim().lowercase()
                standardEmojis.filter {
                    it.details.description
                        .lowercase()
                        .contains(q)
                }
            }
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        modifier = modifier.size(width = 320.dp, height = 360.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search emoji…") },
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No emoji found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 36.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    items(filtered, key = { it.details.description }) { emoji ->
                        Text(
                            text = emoji.details.string,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onPick(emoji.details.string) },
                        )
                    }
                }
            }
        }
    }
}
