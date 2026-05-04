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
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols

@Composable
fun ColumnHeader(
    column: DeckColumn,
    canClose: Boolean,
    hasBackStack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onDoubleClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleClick() },
                    )
                }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasBackStack) {
            IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                Icon(
                    MaterialSymbols.AutoMirrored.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        Icon(
            symbol = column.type.icon(),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = column.type.title(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (canClose) {
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = "Close column",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun DeckColumnType.icon(): MaterialSymbol =
    when (this) {
        DeckColumnType.HomeFeed -> MaterialSymbols.Home
        DeckColumnType.Notifications -> MaterialSymbols.Notifications
        DeckColumnType.Messages -> MaterialSymbols.Email
        DeckColumnType.Search -> MaterialSymbols.Search
        DeckColumnType.Reads -> MaterialSymbols.AutoMirrored.Article
        DeckColumnType.Bookmarks -> MaterialSymbols.Bookmark
        DeckColumnType.GlobalFeed -> MaterialSymbols.Public
        DeckColumnType.MyProfile -> MaterialSymbols.Person
        DeckColumnType.Chess -> MaterialSymbols.Extension
        DeckColumnType.Settings -> MaterialSymbols.Settings
        DeckColumnType.Relays -> MaterialSymbols.Dns
        is DeckColumnType.Article -> MaterialSymbols.AutoMirrored.Article
        is DeckColumnType.Editor -> MaterialSymbols.AutoMirrored.Article
        DeckColumnType.Drafts -> MaterialSymbols.AutoMirrored.Article
        DeckColumnType.MyHighlights -> MaterialSymbols.AutoMirrored.Article
        is DeckColumnType.Profile -> MaterialSymbols.Person
        is DeckColumnType.Thread -> MaterialSymbols.AutoMirrored.Article
        is DeckColumnType.Hashtag -> MaterialSymbols.Tag
    }
