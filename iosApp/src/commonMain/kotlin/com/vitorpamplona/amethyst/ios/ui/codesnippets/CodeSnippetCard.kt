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
package com.vitorpamplona.amethyst.ios.ui.codesnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.ios.util.toTimeAgo

private val CodeBlockShape = RoundedCornerShape(8.dp)

/**
 * Card composable for displaying a code snippet (NIP-C0, kind 1337) in the feed.
 */
@Composable
fun CodeSnippetCard(
    snippet: CodeSnippetDisplayData,
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
                                Modifier.clickable { onAuthorClick(snippet.pubKeyHex) }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    UserAvatar(
                        userHex = snippet.pubKeyHex,
                        pictureUrl = snippet.profilePictureUrl,
                        size = 32.dp,
                        contentDescription = "Profile picture",
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = snippet.pubKeyDisplay.take(20) + if (snippet.pubKeyDisplay.length > 20) "..." else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = snippet.createdAt.toTimeAgo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Name + language badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = snippet.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                snippet.language?.let { lang ->
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(CodeBlockShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Description
            snippet.description?.ifBlank { null }?.let { desc ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Code preview
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(CodeBlockShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CodeBlockShape)
                        .padding(10.dp),
            ) {
                Text(
                    text = snippet.codePreview,
                    style =
                        TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
