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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.vitorpamplona.amethyst.desktop.ui.toNoteDisplayData
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

    // Whole-card hover/ripple: pass onClick to M3 Card so the click-surface is
    // the entire card (M3 handles shape clipping for us). Action buttons inside
    // the NoteActionsRow have their own clickables that consume the click before
    // it reaches the Card's handler, so tapping an action still fires only that
    // action.
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val cardBody: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(12.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Author with avatar — stadium-shaped hover to match the
                    // avatar+name chip's visual shape.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            if (onAuthorClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .clickable { onAuthorClick(note.pubKeyHex) }
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
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = cardColors,
            elevation = cardElevation,
            content = cardBody,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = cardColors,
            elevation = cardElevation,
            content = cardBody,
        )
    }
}

/**
 * Resolved bech32 mention with display text and optional pubkey for click navigation.
 */
private data class ResolvedMention(
    val displayText: String,
    val pubKeyHex: String? = null,
    val noteIdHex: String? = null,
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
            ResolvedMention(displayText = "note:${entity.hex.take(8)}...", noteIdHex = entity.hex)
        }

        is NEvent -> {
            ResolvedMention(displayText = "note:${entity.hex.take(8)}...", noteIdHex = entity.hex)
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
private data class ContentSegment(
    val start: Int,
    val raw: String,
    val isUrl: Boolean,
)

private fun buildSegments(
    content: String,
    schemeUrls: Collection<String>,
    bech32s: Collection<String>,
): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    for (url in schemeUrls) {
        val idx = content.indexOf(url)
        if (idx != -1) segments.add(ContentSegment(idx, url, true))
    }
    for (bech32 in bech32s) {
        val idx = content.indexOf(bech32)
        if (idx != -1) segments.add(ContentSegment(idx, bech32, false))
    }
    segments.sortBy { it.start }
    return segments
}

@Composable
fun RichTextContent(
    content: String,
    urls: Urls,
    localCache: DesktopLocalCache? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onNavigateToThread: ((String) -> Unit)? = null,
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
        return
    }

    // Resolve bech32s to find quoted notes vs inline mentions
    val resolvedBech32s =
        remember(urls.bech32s, localCache) {
            urls.bech32s.associateWith { resolveBech32(it, localCache) }
        }

    // Collect quoted note IDs (nevent/note references)
    val quotedBech32s = remember(resolvedBech32s) { resolvedBech32s.filter { it.value.noteIdHex != null }.keys }
    val quotedNoteIds = remember(resolvedBech32s) { resolvedBech32s.values.mapNotNull { it.noteIdHex }.toSet() }

    if (quotedNoteIds.isEmpty()) {
        // No quoted notes — render everything as annotated text
        val segments = remember(content, urls) { buildSegments(content, urls.withScheme, urls.bech32s) }
        RichAnnotatedText(content, segments, resolvedBech32s, defaultColor, primaryColor, onMentionClick, modifier)
    } else {
        // Has quoted notes — render text + embedded note cards
        Column(modifier = modifier) {
            // Strip quoted note bech32 references from text
            val strippedText =
                remember(content, quotedBech32s) {
                    var text = content
                    for (bech32 in quotedBech32s) {
                        text = text.replace(bech32, "").trim()
                    }
                    text
                }

            if (strippedText.isNotBlank()) {
                val inlineBech32s = urls.bech32s - quotedBech32s
                val segments = remember(strippedText, urls, inlineBech32s) { buildSegments(strippedText, urls.withScheme, inlineBech32s) }
                RichAnnotatedText(strippedText, segments, resolvedBech32s, defaultColor, primaryColor, onMentionClick)
            }

            // Render quoted notes as embedded cards
            for (noteId in quotedNoteIds) {
                Spacer(Modifier.height(8.dp))
                QuotedNoteEmbed(noteId, localCache, onMentionClick, onNavigateToThread)
            }
        }
    }
}

/**
 * Renders a quoted note by ID. Observes the note's metadata flow so it
 * recomposes when the event arrives asynchronously from a relay fetch.
 * Also observes the author's metadata for display name / avatar updates.
 */
@Composable
fun QuotedNoteEmbed(
    noteId: String,
    localCache: DesktopLocalCache?,
    onMentionClick: ((String) -> Unit)? = null,
    onNavigateToThread: ((String) -> Unit)? = null,
) {
    if (localCache == null) return

    // getOrCreateNote ensures a placeholder exists so subscriptions can find it
    val note = remember(noteId) { localCache.getOrCreateNote(noteId) }

    // Observe note metadata flow — recomposes when loadEvent() is called
    val flowSet = remember(note) { note.flow() }
    val metadataState by flowSet.metadata.stateFlow.collectAsState()

    DisposableEffect(note) {
        onDispose { note.clearFlow() }
    }

    val event = note.event
    if (event != null) {
        // Recompute on every recomposition — picks up user metadata changes
        val displayData = event.toNoteDisplayData(localCache)

        NoteCard(
            note = displayData,
            localCache = localCache,
            onClick = onNavigateToThread?.let { nav -> { nav(event.id) } },
            onAuthorClick = onMentionClick,
            onMentionClick = onMentionClick,
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
        ) {
            Text(
                "Loading quoted note...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun RichAnnotatedText(
    content: String,
    segments: List<ContentSegment>,
    resolvedBech32s: Map<String, ResolvedMention>,
    defaultColor: androidx.compose.ui.graphics.Color,
    primaryColor: androidx.compose.ui.graphics.Color,
    onMentionClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val annotatedText =
        buildAnnotatedString {
            var lastIndex = 0
            for (seg in segments) {
                if (seg.start < lastIndex) continue
                if (seg.start > lastIndex) {
                    append(content.substring(lastIndex, seg.start))
                }
                if (seg.isUrl) {
                    withStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)) {
                        append(seg.raw)
                    }
                } else {
                    val resolved = resolvedBech32s[seg.raw] ?: ResolvedMention(seg.raw)
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
                lastIndex = seg.start + seg.raw.length
            }
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
