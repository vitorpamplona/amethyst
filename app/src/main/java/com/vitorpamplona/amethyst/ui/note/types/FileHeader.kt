/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.events.FileHeaderEvent

@Composable
fun FileHeaderDisplay(
    note: Note,
    roundedCorner: Boolean,
    accountViewModel: AccountViewModel,
) {
    val event = (note.event as? FileHeaderEvent) ?: return
    val fullUrl = event.url() ?: return

    val content by
        remember(note) {
            val blurHash = event.blurhash()
            val hash = event.hash()
            val dimensions = event.dimensions()
            val description = event.content.ifEmpty { null } ?: event.alt()
            val isImage = RichTextParser.isImageUrl(fullUrl)
            val uri = note.toNostrUri()

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    MediaUrlImage(
                        url = fullUrl,
                        description = description,
                        hash = hash,
                        blurhash = blurHash,
                        dim = dimensions,
                        uri = uri,
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
                    )
                },
            )
        }

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        ZoomableContentView(
            content = content,
            roundedCorner = roundedCorner,
            accountViewModel = accountViewModel,
        )
    }
}
