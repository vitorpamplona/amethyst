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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

private const val ASPECT_RATIO = 4f / 3f
private val IMAGE_SPACING: Dp = Size5dp

@Composable
private fun GalleryImage(
    image: MediaUrlImage,
    allImages: ImmutableList<MediaUrlImage>,
    modifier: Modifier = Modifier,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = modifier) {
        ZoomableContentView(
            content = image,
            images = allImages,
            roundedCorner = roundedCorner,
            contentScale = contentScale,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun ImageGallery(
    images: ImageGalleryParagraph,
    state: RichTextViewerState,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    roundedCorner: Boolean = true,
) {
    if (images.words.isEmpty()) return

    val resolvedImages =
        images.words
            .mapNotNull { segment ->
                val imageUrl = segment.segmentText
                state.imagesForPager[imageUrl] as? MediaUrlImage
            }.toImmutableList()

    Column(modifier = modifier.padding(vertical = Size10dp)) {
        when (resolvedImages.size) {
            1 -> SingleImageGallery(resolvedImages, accountViewModel, roundedCorner)
            2 -> TwoImageGallery(resolvedImages, accountViewModel, roundedCorner)
            3 -> ThreeImageGallery(resolvedImages, accountViewModel, roundedCorner)
            4 -> FourImageGallery(resolvedImages, accountViewModel, roundedCorner)
            else -> ManyImageGallery(resolvedImages, accountViewModel, roundedCorner)
        }
    }
}

@Composable
private fun SingleImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    GalleryImage(
        image = images.first(),
        allImages = images,
        modifier = Modifier.fillMaxWidth(),
        roundedCorner = roundedCorner,
        contentScale = ContentScale.FillWidth,
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun TwoImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    Row(
        modifier = Modifier.aspectRatio(ASPECT_RATIO),
        horizontalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
    ) {
        images.take(2).forEach { image ->
            GalleryImage(
                image = image,
                allImages = images,
                modifier = Modifier.weight(1f).fillMaxSize(),
                roundedCorner = roundedCorner,
                contentScale = ContentScale.Crop,
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
private fun ThreeImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    Row(
        modifier = Modifier.aspectRatio(ASPECT_RATIO),
        horizontalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
    ) {
        GalleryImage(
            image = images[0],
            allImages = images,
            modifier = Modifier.weight(2f).fillMaxSize(),
            roundedCorner = roundedCorner,
            contentScale = ContentScale.Crop,
            accountViewModel = accountViewModel,
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
        ) {
            images.drop(1).forEach { image ->
                GalleryImage(
                    image = image,
                    allImages = images,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun FourImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    Column(
        modifier = Modifier.aspectRatio(ASPECT_RATIO),
        verticalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
    ) {
        images.chunked(2).forEach { rowImages ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
            ) {
                rowImages.forEach { image ->
                    GalleryImage(
                        image = image,
                        allImages = images,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManyImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    val columns =
        when {
            images.size <= 9 -> 3
            else -> 4
        }

    Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
        images.chunked(columns).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                rowImages.forEach { image ->
                    GalleryImage(
                        image = image,
                        allImages = images,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                }
                repeat(columns - rowImages.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
