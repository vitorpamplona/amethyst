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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip71Video.VideoEvent

@Composable
fun JustVideoDisplay(
    note: Note,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    val videoEvent = (note.event as? VideoEvent) ?: return
    val event = (videoEvent as? Event) ?: return

    val imeta = videoEvent.imetaTags().getOrNull(0) ?: return
    val isSensitive = event.isSensitiveOrNSFW()
    val reasons = collectContentWarningReasons(event)
    val isImage = imeta.mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(imeta.url)

    val content by
        remember(note) {
            val description = event.content.ifEmpty { null } ?: imeta.alt ?: event.alt()

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    MediaUrlImage(
                        url = imeta.url,
                        description = description,
                        hash = imeta.hash,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = note.toNostrUri(),
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
                        mimeType = imeta.mimeType,
                    )
                },
            )
        }

    val ratio = imeta.dimension?.aspectRatio() ?: MediaAspectRatioCache.get(imeta.url)

    ContentWarningGate(
        isSensitive = isSensitive,
        reasons = reasons,
        preloadUrls = if (isImage) listOf(imeta.url) else emptyList(),
        accountViewModel = accountViewModel,
        modifier = mediaSizingModifier(ratio, contentScale),
        backdrop = imeta.blurhash?.let { blurhash -> { BlurhashBackdrop(blurhash, content.description) } },
    ) {
        ZoomableContentView(
            content = content,
            roundedCorner = roundedCorner,
            contentScale = contentScale,
            accountViewModel = accountViewModel,
        )
    }
}
