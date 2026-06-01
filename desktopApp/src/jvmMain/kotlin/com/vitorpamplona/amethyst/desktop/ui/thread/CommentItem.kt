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
package com.vitorpamplona.amethyst.desktop.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar

@Composable
fun CommentItem(
    authorName: String,
    authorHandle: String,
    authorAvatarUrl: String?,
    authorPubKeyHex: String,
    content: String,
    timeAgo: String,
    reactionCount: Int,
    zapAmount: Long,
    isLiked: Boolean = false,
    isZapped: Boolean = false,
    onReply: () -> Unit = {},
    onLike: () -> Unit = {},
    onZap: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        UserAvatar(
            userHex = authorPubKeyHex,
            pictureUrl = authorAvatarUrl,
            size = 36.dp,
            modifier = Modifier.clickable(onClick = onAuthorClick),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onAuthorClick),
                )
                Text(
                    text = " @$authorHandle · $timeAgo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReply) {
                    Icon(
                        symbol = MaterialSymbols.Chat,
                        contentDescription = "Reply",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onLike),
                ) {
                    val likeColor =
                        if (isLiked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    val likeSymbol =
                        if (isLiked) {
                            MaterialSymbols.Favorite
                        } else {
                            MaterialSymbols.FavoriteBorder
                        }
                    Icon(
                        symbol = likeSymbol,
                        contentDescription = "Like",
                        modifier = Modifier.size(16.dp),
                        tint = likeColor,
                    )
                    if (reactionCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = reactionCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = likeColor,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onZap),
                ) {
                    val zapColor =
                        if (isZapped) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Icon(
                        symbol = MaterialSymbols.Bolt,
                        contentDescription = "Zap",
                        modifier = Modifier.size(16.dp),
                        tint = zapColor,
                    )
                    if (zapAmount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatZapAmount(zapAmount),
                            style = MaterialTheme.typography.labelSmall,
                            color = zapColor,
                        )
                    }
                }
            }
        }
    }
}

private fun formatZapAmount(sats: Long): String =
    when {
        sats >= 1_000_000 -> "${sats / 1_000_000}M"
        sats >= 1_000 -> "${sats / 1_000}k"
        else -> sats.toString()
    }
