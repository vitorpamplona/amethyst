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
package com.vitorpamplona.amethyst.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.gif
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedMediaUrl
import com.vitorpamplona.amethyst.commons.service.MediaLoadFailure
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.DisplayUrlWithLoadingSymbol
import com.vitorpamplona.amethyst.commons.ui.components.MediaFailureCard
import com.vitorpamplona.amethyst.commons.ui.components.WaitAndDisplay
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.animated.AnimatedGifImage
import com.vitorpamplona.amethyst.commons.ui.components.animated.AnimatedImageProbe
import com.vitorpamplona.amethyst.commons.ui.components.mediaLoadFailureOf
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.note.platform.GifVideoView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font10SP
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.coroutines.launch

/**
 * Whether [image] is played by the GIF player: declared or named as a GIF/AVIF, as Android's
 * `isAnimatedMedia` reads it, or a file a probe found frames in (an animated WebP, a GIF behind an
 * extensionless Blossom URL).
 */
private fun isAnimated(image: MediaUrlImage): Boolean =
    image.mimeType == "image/gif" ||
        image.mimeType == "image/avif" ||
        isAnimatedMediaUrl(image.url) ||
        AnimatedImageProbe.isAnimated(image.url)

/**
 * A note's picture inline. A still draws through Coil; an animated one, which Coil here decodes to its
 * first frame only, plays through the shared Skia player ([IosGifVideoView]). A still whose file
 * turns out to hold frames switches to the player once Coil has it on disk. Either way a tap opens
 * the viewer ([onOpen]), which grows out of [source].
 */
@Composable
internal fun IosInlineImage(
    content: MediaUrlImage,
    contentScale: ContentScale,
    roundedCorner: Boolean,
    cornerRadius: Dp,
    maxHeight: Dp,
    source: ZoomSourceAnchor,
    onOpen: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val url = content.url
    var animated by remember(url) { mutableStateOf(isAnimated(content)) }
    if (animated) {
        // The anchor lets the viewer grow out of, and shrink back into, the GIF.
        Box(Modifier.heightIn(max = maxHeight).zoomSourceAnchor(source)) {
            GifVideoView(
                videoUri = url,
                contentDescription = content.description,
                dimensions = content.dim,
                blurhash = content.blurhash,
                roundedCorner = roundedCorner,
                contentScale = contentScale,
                onDialog = onOpen,
                accountViewModel = accountViewModel,
                thumbhash = content.thumbhash,
            )
        }
    } else {
        val scope = rememberCoroutineScope()
        AsyncImage(
            model = url,
            contentDescription = content.description,
            contentScale = contentScale,
            onSuccess = { state ->
                if (AnimatedImageProbe.needsProbe(url)) {
                    val diskCacheKey = state.result.diskCacheKey
                    scope.launch { if (AnimatedImageProbe.probe(url, diskCacheKey)) animated = true }
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .zoomSourceAnchor(source)
                    .clip(RoundedCornerShape(cornerRadius))
                    .clickable(onClick = onOpen),
        )
    }
}

/**
 * A GIF (or animated WebP) in an image slot, drawn as an image: looping, silent, no player controls,
 * through the shared Skia player. With autoplay off (the video setting, as on Android) it holds its
 * first frame and wears a "GIF" label. Sized like a still ([mediaSizingModifier]): Crop fills the
 * slot, a known shape reserves it, anything else wraps the image.
 */
@Composable
internal fun IosGifVideoView(
    videoUri: String,
    contentDescription: String?,
    dimensions: DimensionTag?,
    blurhash: String?,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    onDialog: (() -> Unit)?,
    accountViewModel: AccountViewModel,
    thumbhash: String?,
) {
    val ratio = dimensions?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(videoUri)
    // Unbounded height wraps the image, which is zero pixels tall until its first frame is in:
    // the URL with a loading symbol holds the place then, as on Android.
    val heightIsBounded = contentScale == ContentScale.Crop || ratio != null
    val autoPlay by accountViewModel.settings.autoPlayVideosFlow.collectAsState()
    val borderModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier
    val containerModifier =
        borderModifier
            .then(mediaSizingModifier(ratio, contentScale))
            .let { if (onDialog != null) it.clickable { onDialog() } else it }

    var shown by remember(videoUri) { mutableStateOf(false) }
    // Bumped by Retry: a new key starts the load over.
    var attempt by remember(videoUri) { mutableIntStateOf(0) }
    var failure by remember(videoUri) { mutableStateOf<MediaLoadFailure?>(null) }

    Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
        if (!shown && failure == null) {
            if (blurhash != null || thumbhash != null) {
                DisplayBlurHash(blurhash, contentDescription, contentScale, Modifier.matchParentSize(), thumbhash = thumbhash)
            } else if (!heightIsBounded) {
                WaitAndDisplay { DisplayUrlWithLoadingSymbol(videoUri) }
            }
        }

        key(attempt) {
            AnimatedGifImage(
                url = videoUri,
                contentDescription = contentDescription,
                modifier = if (heightIsBounded) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                contentScale = contentScale,
                onFailure = { failure = mediaLoadFailureOf(it) },
                onShown = { shown = it },
                autoPlay = autoPlay,
            )
        }

        failure?.let {
            MediaFailureCard(
                url = videoUri,
                failure = it,
                modifier = Modifier.fillMaxSize(),
                animated = true,
                blurhash = blurhash,
                thumbhash = thumbhash,
                onRetry = {
                    failure = null
                    attempt++
                },
            )
        }

        if (!autoPlay && failure == null) {
            Text(
                text = stringRes(Res.string.gif),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = Font10SP,
                lineHeight = Font10SP,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(SmallBorder)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** An animated image filling [modifier], looping (a banner, the drawer header, the Studio's picked GIF). */
@Composable
internal fun IosAnimatedImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier,
    onError: () -> Unit,
    local: Boolean,
) = AnimatedGifImage(url = url, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale, onError = onError, local = local)
