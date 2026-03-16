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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.richtext.Urls
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.amethyst.desktop.ui.media.AudioPlayer
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopVideoPlayer

/**
 * Data class for displaying a note card.
 */
data class NoteDisplayData(
    val id: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val content: String,
    val createdAt: Long,
)

/**
 * Reusable note card composable that displays a Nostr note.
 * Can be used by both Desktop and Android apps.
 */
@Composable
fun NoteCard(
    note: NoteDisplayData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAuthorClick: ((String) -> Unit)? = null,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
) {
    val urls = remember(note.content) { UrlParser().parseValidUrls(note.content) }
    val imageUrls =
        remember(urls) {
            urls.withScheme.filter { RichTextParser.isImageUrl(it) }
        }
    val allMediaUrls =
        remember(urls) {
            urls.withScheme.filter { RichTextParser.isVideoUrl(it) }
        }
    val audioExtensions = setOf("mp3", "ogg", "wav", "flac", "aac", "opus", "m4a")
    val audioUrls =
        remember(allMediaUrls) {
            allMediaUrls.filter { url ->
                val ext =
                    url
                        .substringAfterLast('.', "")
                        .substringBefore('?')
                        .lowercase()
                ext in audioExtensions
            }
        }
    val videoUrls =
        remember(allMediaUrls, audioUrls) {
            allMediaUrls - audioUrls.toSet()
        }
    val mediaUrls = remember(imageUrls, allMediaUrls) { (imageUrls + allMediaUrls).toSet() }
    val strippedContent =
        remember(note.content, mediaUrls) {
            var text = note.content
            for (url in mediaUrls) {
                text = text.replace(url, "").trim()
            }
            text
        }
    val strippedUrls =
        remember(urls, mediaUrls) {
            Urls(
                withScheme = urls.withScheme - mediaUrls,
                withoutScheme = urls.withoutScheme,
                emails = urls.emails,
                bech32s = urls.bech32s,
                relayUrls = urls.relayUrls,
                blossomUris = urls.blossomUris,
            )
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        onClick = onClick ?: {},
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Author with avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        if (onAuthorClick != null) {
                            Modifier.clickable { onAuthorClick(note.pubKeyHex) }
                        } else {
                            Modifier
                        },
                ) {
                    UserAvatar(
                        userHex = note.pubKeyHex,
                        pictureUrl = note.profilePictureUrl,
                        size = 32.dp,
                        contentDescription = "Profile picture of ${note.pubKeyDisplay}",
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = note.pubKeyDisplay.take(20) + if (note.pubKeyDisplay.length > 20) "..." else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }

                // Timestamp
                Text(
                    text = note.createdAt.toTimeAgo(withDot = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (strippedContent.isNotBlank()) {
                RichTextContent(
                    content = strippedContent,
                    urls = strippedUrls,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Inline images
            if (imageUrls.isNotEmpty()) {
                if (strippedContent.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                }
                for ((index, url) in imageUrls.withIndex()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (onImageClick != null) {
                                        Modifier.clickable { onImageClick(imageUrls, index) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        contentScale = ContentScale.FillWidth,
                    )
                    if (url != imageUrls.last()) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Inline videos
            if (videoUrls.isNotEmpty()) {
                if (strippedContent.isNotBlank() || imageUrls.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                }
                for (url in videoUrls) {
                    DesktopVideoPlayer(
                        url = url,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    )
                    if (url != videoUrls.last()) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Inline audio
            if (audioUrls.isNotEmpty()) {
                if (strippedContent.isNotBlank() || imageUrls.isNotEmpty() || videoUrls.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                }
                for (url in audioUrls) {
                    AudioPlayer(
                        url = url,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (url != audioUrls.last()) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Spacer(Modifier.height(4.dp))

            // Event ID (truncated)
            Text(
                text = "ID: ${note.id.take(12)}...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Renders text content with highlighted URLs.
 * Uses RichTextParser from commons to detect and highlight links.
 */
@Composable
fun RichTextContent(
    content: String,
    urls: Urls,
    modifier: Modifier = Modifier,
    maxLines: Int = 10,
) {
    if (urls.withScheme.isEmpty()) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    } else {
        val annotatedText =
            buildAnnotatedString {
                var lastIndex = 0
                val sortedUrls = urls.withScheme.sortedBy { content.indexOf(it) }

                for (url in sortedUrls) {
                    val startIndex = content.indexOf(url, lastIndex)
                    if (startIndex == -1) continue

                    // Add text before URL
                    if (startIndex > lastIndex) {
                        append(content.substring(lastIndex, startIndex))
                    }

                    // Add URL with styling
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(url)
                    }

                    lastIndex = startIndex + url.length
                }

                // Add remaining text
                if (lastIndex < content.length) {
                    append(content.substring(lastIndex))
                }
            }

        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}
