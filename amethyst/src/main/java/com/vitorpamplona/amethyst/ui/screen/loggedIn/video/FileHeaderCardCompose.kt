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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.BlurhashBackdrop
import com.vitorpamplona.amethyst.ui.components.ContentWarningGate
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.components.collectContentWarningReasons
import com.vitorpamplona.amethyst.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import kotlin.text.ifEmpty

@Composable
fun FileHeaderCardCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (baseNote.event as? FileHeaderEvent) ?: return
    val backgroundColor = remember { mutableStateOf(Color.Transparent) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Author header row
        UserCardHeader(baseNote, accountViewModel, nav)

        // Image content
        FileHeaderCardImage(baseNote, event, backgroundColor, accountViewModel)

        // Reactions row
        ReactionsRow(
            baseNote = baseNote,
            showReactionDetail = true,
            addPadding = true,
            editState = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        // Title and content
        FileHeaderCardCaption(event)
    }
}

@Composable
private fun FileHeaderCardImage(
    note: Note,
    event: FileHeaderEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val fullUrl = event.url() ?: return

    val isSensitive = remember(note) { event.isSensitiveOrNSFW() }
    val reasons = remember(note) { collectContentWarningReasons(event) }
    val isImage = remember(note) { event.mimeType()?.startsWith("image/") == true || RichTextParser.isImageUrl(fullUrl) }
    val blurHash = remember(note) { event.blurhash() }
    val thumbHash = remember(note) { event.thumbhash() }
    val dimensions = remember(note) { event.dimensions() }

    val content by remember(note) {
        val hash = event.hash()
        val description = event.content.ifEmpty { null } ?: event.alt()
        val uri = note.toNostrUri()
        val mimeType = event.mimeType()

        mutableStateOf<BaseMediaContent>(
            if (isImage) {
                MediaUrlImage(
                    url = fullUrl,
                    description = description,
                    hash = hash,
                    blurhash = blurHash,
                    dim = dimensions,
                    uri = uri,
                    mimeType = mimeType,
                    thumbhash = thumbHash,
                )
            } else {
                MediaUrlVideo(
                    url = fullUrl,
                    description = description,
                    hash = hash,
                    blurhash = blurHash,
                    dim = dimensions,
                    uri = uri,
                    authorName = note.author?.toBestDisplayName(),
                    mimeType = mimeType,
                    thumbhash = thumbHash,
                )
            },
        )
    }

    val ratio = dimensions?.aspectRatio() ?: MediaAspectRatioCache.get(fullUrl)

    ContentWarningGate(
        isSensitive = isSensitive,
        reasons = reasons,
        preloadUrls = if (isImage) listOf(fullUrl) else emptyList(),
        accountViewModel = accountViewModel,
        modifier = mediaSizingModifier(ratio, ContentScale.FillWidth),
        backdrop = (thumbHash ?: blurHash)?.let { { BlurhashBackdrop(blurHash, content.description, thumbHash) } },
    ) {
        ZoomableContentView(
            content = content,
            roundedCorner = false,
            contentScale = ContentScale.FillWidth,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
internal fun FileHeaderCardCaption(videoEvent: FileHeaderEvent) {
    val event = (videoEvent as? Event) ?: return

    val title = videoEvent.summary()
    val content = event.content

    if (title != null || content.isNotBlank()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
