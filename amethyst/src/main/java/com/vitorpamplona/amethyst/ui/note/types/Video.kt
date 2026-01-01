/**
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip01Core.core.EmptyTagList
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip71Video.VideoEvent

@Composable
fun VideoDisplay(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val videoEvent = (note.event as? VideoEvent) ?: return
    val event = (videoEvent as? Event) ?: return

    val imeta = videoEvent.imetaTags().firstOrNull() ?: return

    val title = videoEvent.title()
    val summary = videoEvent.content.ifBlank { null }?.takeIf { title != it }
    val image = imeta.image.firstOrNull()
    val isYouTube = imeta.url.contains("youtube.com") || imeta.url.contains("youtu.be")
    val tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

    val content by
        remember(note) {
            val description = videoEvent.content.ifBlank { null } ?: event.alt()
            val isImage = imeta.mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(imeta.url)
            val uri = note.toNostrUri()

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    MediaUrlImage(
                        url = imeta.url,
                        description = description,
                        hash = imeta.hash,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = uri,
                        mimeType = imeta.mimeType,
                    )
                } else {
                    MediaUrlVideo(
                        url = imeta.url,
                        description = description,
                        hash = imeta.hash,
                        dim = imeta.dimension,
                        uri = uri,
                        authorName = note.author?.toBestDisplayName(),
                        artworkUri = imeta.image.firstOrNull(),
                        mimeType = imeta.mimeType,
                        blurhash = imeta.blurhash,
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
            verticalArrangement = spacedBy(Size5dp),
        ) {
            if (isYouTube) {
                val uri = LocalUriHandler.current
                Row(
                    modifier = Modifier.clickable { runCatching { uri.openUri(imeta.url) } },
                ) {
                    image?.let {
                        MyAsyncImage(
                            imageUrl = it,
                            contentDescription =
                                stringRes(
                                    R.string.preview_card_image_for,
                                    it,
                                ),
                            contentScale = ContentScale.FillWidth,
                            mainImageModifier = Modifier,
                            loadedImageModifier = MaterialTheme.colorScheme.imageModifier,
                            accountViewModel = accountViewModel,
                            onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                            onError = { DefaultImageHeader(note, accountViewModel) },
                        )
                    } ?: run {
                        DefaultImageHeader(note, accountViewModel)
                    }
                }
            } else {
                ZoomableContentView(
                    content = content,
                    roundedCorner = true,
                    contentScale = contentScale,
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
                            .fillMaxWidth(),
                )
            }

            summary?.let {
                val callbackUri = remember(note) { note.toNostrUri() }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                if (videoEvent.hasHashtags()) {
                    Row(
                        Modifier.fillMaxWidth(),
                    ) {
                        DisplayUncitedHashtags(videoEvent, summary, callbackUri, accountViewModel, nav)
                    }
                }
            }
        }
    }
}
