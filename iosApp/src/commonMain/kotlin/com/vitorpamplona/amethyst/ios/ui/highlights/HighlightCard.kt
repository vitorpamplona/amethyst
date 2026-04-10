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
package com.vitorpamplona.amethyst.ios.ui.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.ios.util.toTimeAgo

private val QuoteShape = RoundedCornerShape(4.dp)

/**
 * Card composable for displaying a highlight (NIP-84, kind 9802) in the feed.
 */
@Composable
fun HighlightCard(
    highlight: HighlightDisplayData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAuthorClick: ((String) -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(12.dp)
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        ) {
            // Author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.weight(1f).then(
                            if (onAuthorClick != null) {
                                Modifier.clickable { onAuthorClick(highlight.pubKeyHex) }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    UserAvatar(
                        userHex = highlight.pubKeyHex,
                        pictureUrl = highlight.profilePictureUrl,
                        size = 32.dp,
                        contentDescription = "Profile picture",
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = highlight.pubKeyDisplay.take(20) + if (highlight.pubKeyDisplay.length > 20) "..." else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = Icons.Default.FormatQuote,
                    contentDescription = "Highlight",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = highlight.createdAt.toTimeAgo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Comment (if any)
            highlight.comment?.ifBlank { null }?.let { comment ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Highlighted quote with left border
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(60.dp)
                            .clip(QuoteShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = highlight.quote,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // Source attribution
            val source = highlight.sourceAuthorDisplay ?: highlight.sourceUrl
            if (source != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "— $source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
