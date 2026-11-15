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
package com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle

/**
 * Whether a composer's tools wrap under the text rather than scroll in a strip at the bottom:
 * on the docked tier, a desktop-sized window. Not on the rail tier, which landscape phones reach,
 * where the keyboard leaves no height for a second row of tools.
 */
@Composable
fun composerToolsWrap(): Boolean = LocalScreenLayout.current.navigationStyle == NavigationStyle.PERMANENT_DRAWER

/** Lines the wrapped tools up with a composer's text: past the 10dp side padding and the 35dp avatar. */
val ComposerToolsStartPadding = 45.dp

/** A composer field's start without an avatar beside it: just the 10dp side padding. */
val ComposerToolsFormStartPadding = 10.dp

/**
 * A composer's tool buttons (gallery, files, drawing, emoji, zap split, content warning, …).
 *
 * On a phone they scroll sideways in a 50dp strip at the bottom, above the keyboard. On the docked
 * tier they wrap under the text instead, lined up with it from [startPadding]: a mouse cannot drag
 * that strip, so its last tools were out of reach, and on a tall window the strip sat far below a
 * short text. The content above should then take only the height it needs, not fill the window:
 * give it `weight(1f, fill = !composerToolsWrap())` rather than `weight(1f)`.
 *
 * [tools] is told whether it is [wrapped], for a tool that needs its own bounds there (the voice
 * recorder fills its parent). It is movable, so a window crossing tiers moves the tools' state (a
 * recording, an open picker) instead of disposing it.
 */
@Composable
fun ComposerToolbar(
    modifier: Modifier = Modifier,
    startPadding: Dp = ComposerToolsStartPadding,
    wrapped: Boolean = composerToolsWrap(),
    tools: @Composable (wrapped: Boolean) -> Unit,
) {
    val movableTools =
        remember {
            movableContentOf { content: @Composable (Boolean) -> Unit, isWrapped: Boolean ->
                content(isWrapped)
            }
        }

    if (wrapped) {
        FlowRow(
            modifier = modifier.fillMaxWidth().padding(start = startPadding, end = 10.dp),
            itemVerticalAlignment = CenterVertically,
        ) {
            movableTools(tools, true)
        }
    } else {
        val scrollState = rememberScrollState()
        Row(
            modifier =
                modifier
                    .horizontalScroll(scrollState)
                    .fillMaxWidth()
                    .height(50.dp),
            verticalAlignment = CenterVertically,
        ) {
            movableTools(tools, false)
        }
    }
}
