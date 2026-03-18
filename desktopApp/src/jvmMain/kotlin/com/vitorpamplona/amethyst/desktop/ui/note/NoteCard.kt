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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.richtext.Urls
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.ui.media.AnimatedGifImage
import com.vitorpamplona.amethyst.desktop.ui.media.AudioPlayer
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopVideoPlayer
import com.vitorpamplona.amethyst.desktop.ui.media.LocalWindowState
import com.vitorpamplona.amethyst.desktop.ui.media.isAnimatedGifUrl
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "wav", "flac", "aac", "opus", "m4a")

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
    localCache: DesktopLocalCache? = null,
    onClick: (() -> Unit)? = null,
    onAuthorClick: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onMediaClick: ((List<String>, Int, Float) -> Unit)? = null,
) {
    val urls = remember(note.content) { UrlParser().parseValidUrls(note.content) }
    val imageUrls =
        remember(urls) {
            urls.withScheme.filter { RichTextParser.isImageUrl(it) }
        }
    val videoAndAudioUrls =
        remember(urls) {
            urls.withScheme.filter { RichTextParser.isVideoUrl(it) }
        }
    val audioUrls =
        remember(videoAndAudioUrls) {
            videoAndAudioUrls.filter { url ->
                val ext =
                    url
                        .substringAfterLast('.', "")
                        .substringBefore('?')
                        .lowercase()
                ext in AUDIO_EXTENSIONS
            }
        }
    val videoUrls =
        remember(videoAndAudioUrls, audioUrls) {
            videoAndAudioUrls - audioUrls.toSet()
        }
    val mediaUrls = remember(imageUrls, videoAndAudioUrls) { (imageUrls + videoAndAudioUrls).toSet() }
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

    // Cap media height to half the window so text is never pushed off-screen
    val windowState = LocalWindowState.current
    val maxMediaHeight =
        if (windowState != null) {
            (windowState.size.height * 0.5f).coerceAtLeast(200.dp)
        } else {
            400.dp
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header + text area — clickable to navigate to thread
            Column(
                modifier =
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    },
            ) {
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
                        localCache = localCache,
                        onMentionClick = onMentionClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } // end clickable header+text column

            // Inline images
            if (imageUrls.isNotEmpty()) {
                if (strippedContent.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                }
                for ((index, url) in imageUrls.withIndex()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxMediaHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (onImageClick != null) {
                                        Modifier.clickable { onImageClick(imageUrls, index) }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        if (isAnimatedGifUrl(url)) {
                            AnimatedGifImage(
                                url = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
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
                for ((index, url) in videoUrls.withIndex()) {
                    DesktopVideoPlayer(
                        url = url,
                        modifier = Modifier.fillMaxWidth().heightIn(max = maxMediaHeight),
                        onFullscreen =
                            if (onMediaClick != null) {
                                { seekPos -> onMediaClick(videoUrls, index, seekPos) }
                            } else {
                                null
                            },
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
 * Resolved bech32 mention with display text and optional pubkey for click navigation.
 */
private data class ResolvedMention(
    val displayText: String,
    val pubKeyHex: String? = null,
)

/**
 * Resolves a nostr: bech32 reference to a display string and optional pubkey.
 * For npub/nprofile → @displayName + pubkey hex for navigation.
 * For note/nevent → truncated note ID.
 */
private fun resolveBech32(
    bech32: String,
    localCache: DesktopLocalCache?,
): ResolvedMention {
    val parsed = Nip19Parser.uriToRoute(bech32) ?: return ResolvedMention(bech32)
    return when (val entity = parsed.entity) {
        is NPub -> {
            val user = localCache?.getUserIfExists(entity.hex)
            ResolvedMention(
                displayText = "@${user?.toBestDisplayName() ?: entity.hex.take(8) + "..."}",
                pubKeyHex = entity.hex,
            )
        }

        is NProfile -> {
            val user = localCache?.getUserIfExists(entity.hex)
            ResolvedMention(
                displayText = "@${user?.toBestDisplayName() ?: entity.hex.take(8) + "..."}",
                pubKeyHex = entity.hex,
            )
        }

        is NNote -> {
            ResolvedMention("note:${entity.hex.take(8)}...")
        }

        is NEvent -> {
            ResolvedMention("note:${entity.hex.take(8)}...")
        }

        else -> {
            ResolvedMention(bech32.take(24) + "...")
        }
    }
}

/**
 * Extracts pubkey hex strings from all npub/nprofile bech32 references in a set.
 * Used to trigger metadata loading for mentioned users.
 */
fun extractMentionedPubkeys(bech32s: Set<String>): List<String> =
    bech32s.mapNotNull { bech32 ->
        val parsed = Nip19Parser.uriToRoute(bech32) ?: return@mapNotNull null
        when (val entity = parsed.entity) {
            is NPub -> entity.hex
            is NProfile -> entity.hex
            else -> null
        }
    }

/**
 * Renders text content with highlighted URLs and clickable nostr: bech32 mentions.
 * URLs are underlined in primary color; bech32 mentions show as @displayName in primary color
 * and navigate to profile on click.
 */
@Composable
fun RichTextContent(
    content: String,
    urls: Urls,
    localCache: DesktopLocalCache? = null,
    onMentionClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    if (urls.withScheme.isEmpty() && urls.bech32s.isEmpty()) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = defaultColor,
            modifier = modifier,
        )
    } else {
        data class Segment(
            val start: Int,
            val raw: String,
            val isUrl: Boolean,
        )

        val segments = mutableListOf<Segment>()
        for (url in urls.withScheme) {
            val idx = content.indexOf(url)
            if (idx != -1) segments.add(Segment(idx, url, true))
        }
        for (bech32 in urls.bech32s) {
            val idx = content.indexOf(bech32)
            if (idx != -1) segments.add(Segment(idx, bech32, false))
        }
        segments.sortBy { it.start }

        val annotatedText =
            buildAnnotatedString {
                var lastIndex = 0

                for (segment in segments) {
                    if (segment.start < lastIndex) continue

                    // Add text before segment
                    if (segment.start > lastIndex) {
                        append(content.substring(lastIndex, segment.start))
                    }

                    if (segment.isUrl) {
                        withStyle(
                            SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) {
                            append(segment.raw)
                        }
                    } else {
                        val resolved = resolveBech32(segment.raw, localCache)
                        if (resolved.pubKeyHex != null && onMentionClick != null) {
                            val pubKey = resolved.pubKeyHex
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "mention",
                                    styles = TextLinkStyles(SpanStyle(color = primaryColor)),
                                ) {
                                    onMentionClick(pubKey)
                                },
                            ) {
                                append(resolved.displayText)
                            }
                        } else {
                            withStyle(SpanStyle(color = primaryColor)) {
                                append(resolved.displayText)
                            }
                        }
                    }

                    lastIndex = segment.start + segment.raw.length
                }

                // Add remaining text
                if (lastIndex < content.length) {
                    append(content.substring(lastIndex))
                }
            }

        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            color = defaultColor,
            modifier = modifier,
        )
    }
}
