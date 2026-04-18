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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.shorts

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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.UserCardHeader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import kotlin.text.ifEmpty

@Composable
fun VideoCardCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (baseNote.event as? VideoEvent) ?: return
    val backgroundColor = remember { mutableStateOf(Color.Transparent) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Author header row
        UserCardHeader(baseNote, accountViewModel, nav)

        // Image content
        VideoCardImage(baseNote, event, backgroundColor, accountViewModel)

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
        VideoCardCaption(event)
    }
}

@Composable
private fun VideoCardImage(
    note: Note,
    event: VideoEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val videoEvent = (note.event as? VideoEvent) ?: return
    val event = (event as? Event) ?: return

    val imeta = videoEvent.imetaTags().getOrNull(0) ?: return

    val content by
        remember(note) {
            val description = event.content.ifEmpty { null } ?: imeta.alt ?: event.alt()
            val isImage = imeta.mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(imeta.url)
            val isSensitive = event.isSensitiveOrNSFW()
            val contentWarning = event.contentWarningReason()

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    MediaUrlImage(
                        url = imeta.url,
                        description = description,
                        hash = imeta.hash,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = note.toNostrUri(),
                        contentWarning = contentWarning,
                        isSensitive = isSensitive,
                        mimeType = imeta.mimeType,
                    )
                } else {
                    MediaUrlVideo(
                        url = imeta.url,
                        description = description,
                        hash = imeta.hash,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = note.toNostrUri(),
                        authorName = note.author?.toBestDisplayName(),
                        contentWarning = contentWarning,
                        isSensitive = isSensitive,
                        mimeType = imeta.mimeType,
                    )
                },
            )
        }

    ZoomableContentView(
        content = content,
        roundedCorner = false,
        contentScale = ContentScale.FillWidth,
        accountViewModel = accountViewModel,
    )
}

@Composable
internal fun VideoCardCaption(videoEvent: VideoEvent) {
    val event = (videoEvent as? Event) ?: return

    val title = videoEvent.title()
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
