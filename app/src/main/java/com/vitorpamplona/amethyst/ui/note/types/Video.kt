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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.VideoEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.toImmutableList

@Composable
fun VideoDisplay(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = (note.event as? VideoEvent) ?: return
    val fullUrl = event.url() ?: return

    val title = event.title()
    val summary = event.content.ifBlank { null }?.takeIf { title != it }
    val image = event.thumb() ?: event.image()
    val isYouTube = fullUrl.contains("youtube.com") || fullUrl.contains("youtu.be")
    val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

    val content by
        remember(note) {
            val blurHash = event.blurhash()
            val hash = event.hash()
            val dimensions = event.dimensions()
            val description = event.content.ifBlank { null } ?: event.alt()
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
                        dim = dimensions,
                        uri = uri,
                        authorName = note.author?.toBestDisplayName(),
                        artworkUri = event.thumb() ?: event.image(),
                    )
                },
            )
        }

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isYouTube) {
                val uri = LocalUriHandler.current
                Row(
                    modifier = Modifier.clickable { runCatching { uri.openUri(fullUrl) } },
                ) {
                    image?.let {
                        AsyncImage(
                            model = it,
                            contentDescription =
                                stringResource(
                                    R.string.preview_card_image_for,
                                    it,
                                ),
                            contentScale = ContentScale.FillWidth,
                            modifier = MaterialTheme.colorScheme.imageModifier,
                        )
                    } ?: run {
                        DefaultImageHeader(note, accountViewModel)
                    }
                }
            } else {
                ZoomableContentView(
                    content = content,
                    roundedCorner = true,
                    accountViewModel = accountViewModel,
                )
            }

            title?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                )
            }

            summary?.let {
                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (event.hasHashtags()) {
                Row(
                    Modifier.fillMaxWidth(),
                ) {
                    DisplayUncitedHashtags(
                        remember(event) { event.hashtags().toImmutableList() },
                        summary ?: "",
                        nav,
                    )
                }
            }
        }
    }
}
