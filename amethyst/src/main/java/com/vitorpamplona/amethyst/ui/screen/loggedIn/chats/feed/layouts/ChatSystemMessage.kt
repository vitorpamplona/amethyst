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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size18dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.grayText

/**
 * A centered, muted system line for events that narrate the room rather than talk
 * in it (channel created, profile updated, ...). Visually distinct from user
 * bubbles: no author row, no tail, one small pill in the middle of the feed.
 *
 * [leading] is an optional slot rendered inside the pill, before the text — used to
 * put the avatar of whoever the line is about ("Alice joined") next to the sentence,
 * so a membership change is recognizable without reading the name.
 */
@Composable
fun ChatSystemMessage(
    text: String,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = ButtonBorder,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
                if (onClick != null) {
                    Modifier.clip(ButtonBorder).clickable(onClick = onClick)
                } else {
                    Modifier
                },
        ) {
            if (leading == null) {
                SystemMessageText(text)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    leading()
                    SystemMessageText(text, startPadding = 0.dp)
                }
            }
        }
    }
}

@Composable
private fun SystemMessageText(
    text: String,
    startPadding: Dp = 12.dp,
) {
    Text(
        text = text,
        fontSize = Font12SP,
        color = MaterialTheme.colorScheme.grayText,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(start = startPadding, end = 12.dp, top = 5.dp, bottom = 5.dp),
    )
}

@Preview
@Composable
private fun ChatSystemMessagePreview() {
    ThemeComparisonColumn {
        ChatSystemMessage("Alice created the channel Amethyst Users", onClick = {})
        ChatSystemMessage("Alice updated the channel profile")
        ChatSystemMessage(
            "Bob was added by Alice",
            leading = { Box(Modifier.size(Size18dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) },
        )
    }
}
