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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.gallery

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.isVideoUrl
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent

@Composable
fun GalleryThumbnail(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(baseNote, accountViewModel)
    val noteEvent = noteState.note.event ?: return

    val content =
        if (noteEvent is ProfileGalleryEntryEvent) {
            noteEvent.urls().map { url ->
                if (isVideoUrl(url)) {
                    MediaUrlVideo(
                        url = url,
                        description = noteEvent.content,
                        hash = null,
                        blurhash = noteEvent.blurhash(),
                        dim = noteEvent.dimensions(),
                        uri = null,
                        mimeType = noteEvent.mimeType(),
                    )
                } else {
                    MediaUrlImage(
                        url = url,
                        description = noteEvent.content,
                        // We don't want to show the hash banner here
                        hash = null,
                        blurhash = noteEvent.blurhash(),
                        dim = noteEvent.dimensions(),
                        uri = null,
                        mimeType = noteEvent.mimeType(),
                    )
                }
            }
        } else if (noteEvent is PictureEvent) {
            noteEvent.imetaTags().map { imeta ->
                MediaUrlImage(
                    url = imeta.url,
                    description = noteEvent.content,
                    // We don't want to show the hash banner here
                    hash = null,
                    blurhash = imeta.blurhash,
                    dim = imeta.dimension,
                    uri = null,
                    mimeType = imeta.mimeType,
                )
            }
        } else if (noteEvent is VideoEvent) {
            noteEvent.imetaTags().map { imeta ->
                MediaUrlVideo(
                    url = imeta.url,
                    description = noteEvent.content,
                    // We don't want to show the hash banner here
                    hash = null,
                    blurhash = imeta.blurhash,
                    dim = imeta.dimension,
                    uri = null,
                    mimeType = imeta.mimeType,
                )
            }
        } else {
            emptyList()
        }

    InnerRenderGalleryThumb(content, baseNote, accountViewModel)
}

@Composable
fun InnerRenderGalleryThumb(
    content: List<MediaUrlContent>,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    if (content.isNotEmpty()) {
        GalleryContentView(content, accountViewModel)
    } else {
        DisplayGalleryAuthorBanner(note, accountViewModel)
    }
}

@Composable
fun DisplayGalleryAuthorBanner(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    WatchAuthor(note, accountViewModel) { author ->
        BannerImage(
            author,
            Modifier
                .fillMaxSize()
                .clip(QuoteBorder),
            accountViewModel,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun GalleryContentView(
    contentList: List<MediaUrlContent>,
    accountViewModel: AccountViewModel,
) {
    AutoNonlazyGrid(contentList.size, modifier = Modifier.fillMaxSize()) { contentIndex ->
        when (val content = contentList[contentIndex]) {
            is MediaUrlContent -> {
                val hasSensitiveContent =
                    (content is MediaUrlVideo && content.contentWarning != null) ||
                        (content is MediaUrlImage && content.contentWarning != null)
                SensitivityWarning(hasSensitiveContent, accountViewModel) {
                    UrlImageView(content, accountViewModel)
                }
            }
        }
    }
}

@Composable
fun UrlImageView(
    content: MediaUrlContent,
    accountViewModel: AccountViewModel,
) {
    val defaultModifier = Modifier.fillMaxSize()

    val showImage =
        remember {
            mutableStateOf(
                accountViewModel.settings.showImages(),
            )
        }

    CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center, accountViewModel = accountViewModel) {
        if (it) {
            SubcomposeAsyncImage(
                model = content.url,
                contentDescription = content.description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Loading,
                    -> {
                        if (content.blurhash != null) {
                            DisplayBlurHash(
                                content.blurhash,
                                content.description,
                                ContentScale.Crop,
                                defaultModifier,
                            )
                        } else {
                            Box(defaultModifier, contentAlignment = Alignment.Center) {
                                LoadingAnimation()
                            }
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        Box(defaultModifier, contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = stringRes(id = R.string.play),
                                modifier = Size50Modifier,
                                tint = Color.White,
                            )
                        }
                    }

                    is AsyncImagePainter.State.Success -> {
                        SubcomposeAsyncImageContent(defaultModifier)
                    }

                    else -> {}
                }
            }
        } else {
            if (content.blurhash != null) {
                DisplayBlurHash(
                    content.blurhash,
                    content.description,
                    ContentScale.Crop,
                    defaultModifier.clickable { showImage.value = true },
                )
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = stringRes(id = R.string.play),
                    modifier = Size50Modifier,
                    tint = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = stringRes(id = R.string.play),
                    modifier = Size50Modifier,
                    tint = Color.White,
                )
            }
        }
    }
}
