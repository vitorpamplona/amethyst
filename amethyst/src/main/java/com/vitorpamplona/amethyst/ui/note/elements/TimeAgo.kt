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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun TimeAgo(note: Note) {
    val time = note.createdAt() ?: return
    TimeAgo(time)
}

@Composable
fun TimeAgo(time: Long) {
    val context = LocalContext.current
    // Subscribe to the shared coarse ticker; `derivedStateOf` ensures the Text only
    // recomposes when the formatted string actually flips (e.g. 1m → 2m), not on every tick.
    val nowState = LocalNowSeconds.current
    var showAbsolute by rememberSaveable(time) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val timeStr by
        remember(time, context, nowState) {
            derivedStateOf {
                nowState.value
                if (showAbsolute) timeAbsolute(time, context) else timeAgo(time, context = context)
            }
        }

    Text(
        text = timeStr,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        modifier =
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { showAbsolute = !showAbsolute },
    )
}

@Composable
fun NormalTimeAgo(
    baseNote: Note,
    modifier: Modifier,
) {
    val nowStr = stringRes(id = R.string.now)
    val nowState = LocalNowSeconds.current
    val context = LocalContext.current
    var showAbsolute by rememberSaveable(baseNote.idHex) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val time by
        remember(baseNote, nowStr, nowState) {
            derivedStateOf {
                nowState.value
                val createdAt = baseNote.createdAt() ?: 0L
                if (showAbsolute) timeAbsoluteNoDot(createdAt, context) else timeAgoShort(createdAt, nowStr)
            }
        }

    Text(
        text = time,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { showAbsolute = !showAbsolute },
    )
}
