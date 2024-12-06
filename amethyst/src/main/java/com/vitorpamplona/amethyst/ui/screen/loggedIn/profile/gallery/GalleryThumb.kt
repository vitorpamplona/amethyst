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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.gallery

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.isVideoUrl
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.ui.components.DisplayUrlWithLoadingSymbol
import com.vitorpamplona.amethyst.ui.components.GetMediaItem
import com.vitorpamplona.amethyst.ui.components.GetVideoController
import com.vitorpamplona.amethyst.ui.components.ImageUrlWithDownloadButton
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.UrlImageView
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.ammolite.service.HttpClientManager
import com.vitorpamplona.quartz.events.PictureEvent
import com.vitorpamplona.quartz.events.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.events.VideoEvent

@Composable
fun GalleryThumbnail(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent = noteState?.note?.event ?: return

    val content =
        if (noteEvent is ProfileGalleryEntryEvent) {
            val url = noteEvent.url()
            if (url == null) {
                null
            } else if (isVideoUrl(url)) {
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
                    hash = null, // We don't want to show the hash banner here
                    blurhash = noteEvent.blurhash(),
                    dim = noteEvent.dimensions(),
                    uri = null,
                    mimeType = noteEvent.mimeType(),
                )
            }
        } else if (noteEvent is PictureEvent) {
            val imeta = noteEvent.imetaTags().firstOrNull()
            if (imeta?.url == null) {
                null
            } else {
                MediaUrlImage(
                    url = imeta.url,
                    description = noteEvent.content,
                    hash = null, // We don't want to show the hash banner here
                    blurhash = imeta.blurhash,
                    dim = imeta.dimension,
                    uri = null,
                    mimeType = imeta.mimeType,
                )
            }
        } else if (noteEvent is VideoEvent) {
            val imeta = noteEvent.imetaTags().firstOrNull()

            if (imeta?.url == null) {
                null
            } else {
                MediaUrlVideo(
                    url = imeta.url,
                    description = noteEvent.content,
                    hash = null, // We don't want to show the hash banner here
                    blurhash = imeta.blurhash,
                    dim = imeta.dimension,
                    uri = null,
                    mimeType = imeta.mimeType,
                )
            }
        } else {
            null
        }

    InnerRenderGalleryThumb(content, baseNote, accountViewModel)
}

@Composable
fun InnerRenderGalleryThumb(
    content: MediaUrlContent?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    if (content != null) {
        GalleryContentView(
            content = content,
            accountViewModel = accountViewModel,
        )
    } else {
        DisplayGalleryAuthorBanner(note)
    }
}

@Composable
fun DisplayGalleryAuthorBanner(note: Note) {
    WatchAuthor(note) { author ->
        BannerImage(author, Modifier.fillMaxSize().clip(QuoteBorder))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun GalleryContentView(
    content: MediaUrlContent,
    accountViewModel: AccountViewModel,
) {
    when (content) {
        is MediaUrlImage ->
            SensitivityWarning(content.contentWarning != null, accountViewModel) {
                UrlImageView(content, accountViewModel)
            }
        is MediaUrlVideo ->
            SensitivityWarning(content.contentWarning != null, accountViewModel) {
                UrlVideoView(content, accountViewModel)
            }
    }
}

@Composable
fun UrlImageView(
    content: MediaUrlImage,
    accountViewModel: AccountViewModel,
    alwayShowImage: Boolean = false,
) {
    val defaultModifier = Modifier.fillMaxSize().aspectRatio(1f)

    val showImage =
        remember {
            mutableStateOf(
                if (alwayShowImage) true else accountViewModel.settings.showImages.value,
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
                            DisplayUrlWithLoadingSymbol(content)
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        ClickableUrl(urlText = "${content.url} ", url = content.url)
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
                IconButton(
                    modifier = Modifier.size(Size75dp),
                    onClick = { showImage.value = true },
                ) {
                    DownloadForOfflineIcon(Size75dp, Color.White)
                }
            } else {
                ImageUrlWithDownloadButton(content.url, showImage)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun UrlVideoView(
    content: MediaUrlVideo,
    accountViewModel: AccountViewModel,
) {
    val defaultModifier = Modifier.fillMaxSize().aspectRatio(1f)

    val automaticallyStartPlayback =
        remember(content) {
            mutableStateOf<Boolean>(accountViewModel.settings.startVideoPlayback.value)
        }

    Box(defaultModifier, contentAlignment = Alignment.Center) {
        if (content.blurhash != null) {
            // Always displays Blurharh to avoid size flickering
            DisplayBlurHash(
                content.blurhash,
                null,
                ContentScale.Crop,
                defaultModifier,
            )
        }

        if (!automaticallyStartPlayback.value) {
            IconButton(
                modifier = Modifier.size(Size75dp),
                onClick = { automaticallyStartPlayback.value = true },
            ) {
                DownloadForOfflineIcon(Size75dp, Color.White)
            }
        } else {
            GetMediaItem(content.url, content.description, content.artworkUri, content.authorName) { mediaItem ->
                GetVideoController(
                    mediaItem = mediaItem,
                    videoUri = content.url,
                    defaultToStart = true,
                    nostrUriCallback = content.uri,
                    proxyPort = HttpClientManager.getCurrentProxyPort(accountViewModel.account.shouldUseTorForVideoDownload(content.url)),
                ) { controller, keepPlaying ->
                    AndroidView(
                        modifier = Modifier,
                        factory = { context: Context ->
                            PlayerView(context).apply {
                                clipToOutline = true
                                player = controller
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)

                                controllerAutoShow = false
                                useController = false

                                hideController()

                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

                                controller.playWhenReady = true
                            }
                        },
                    )
                }
            }
        }
    }
}
