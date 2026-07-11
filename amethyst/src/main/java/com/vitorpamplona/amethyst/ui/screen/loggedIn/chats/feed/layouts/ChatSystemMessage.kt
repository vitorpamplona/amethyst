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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.grayText

/**
 * A centered, muted system line for events that narrate the room rather than talk
 * in it (channel created, profile updated, ...). Visually distinct from user
 * bubbles: no author row, no tail, one small pill in the middle of the feed.
 */
@Composable
fun ChatSystemMessage(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        if (onClick != null) {
            Surface(
                onClick = onClick,
                shape = ButtonBorder,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                SystemMessageText(text)
            }
        } else {
            Surface(
                shape = ButtonBorder,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                SystemMessageText(text)
            }
        }
    }
}

@Composable
private fun SystemMessageText(text: String) {
    Text(
        text = text,
        fontSize = Font12SP,
        color = MaterialTheme.colorScheme.grayText,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Preview
@Composable
private fun ChatSystemMessagePreview() {
    ThemeComparisonColumn {
        ChatSystemMessage("Alice created the channel Amethyst Users", onClick = {})
        ChatSystemMessage("Alice updated the channel profile")
    }
}
