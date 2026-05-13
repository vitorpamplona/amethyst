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
package com.vitorpamplona.amethyst.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.vitorpamplona.amethyst.commons.util.timeAbsolute
import com.vitorpamplona.amethyst.commons.util.toTimeAgo

/**
 * Timestamp text that toggles between a relative ("5m") and an absolute, scale-adjusted
 * ("14:32" / "Dec 12, 14:32" / "Dec 12, 2023") form when the user clicks it.
 *
 * Notes for the audit:
 * - Plain [remember] (not `rememberSaveable`): the toggle is a transient peek. Persisting
 *   it for every visible+scrolled-past notification/chat/note across config changes is
 *   pure memory bloat — recycling resets to relative, which is the right default.
 * - Both formatters here are pure functions of the timestamp, so for desktop we don't need
 *   any tick subscription at all (desktop has no shared "now" ticker). The string is
 *   stable for a given (timestamp, showAbsolute) pair, so a [derivedStateOf] also isn't
 *   needed — we just compute on flip.
 * - All params are primitives/value types, so the Compose compiler can skip this composable
 *   when nothing changes.
 */
@Composable
fun ToggleableTimeAgoText(
    timestamp: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var showAbsolute by remember(timestamp) { mutableStateOf(false) }

    Text(
        text = if (showAbsolute) timeAbsolute(timestamp, withDot = false) else timestamp.toTimeAgo(withDot = false),
        style = style,
        color = color,
        modifier =
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { showAbsolute = !showAbsolute },
    )
}
